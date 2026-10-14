# 插件命令修复：TAB 补全不对，整条命令也不执行

报告的现象（Kits 插件 `kits-plugin-1.0.1.jar`）：`/kit` 在客户端 TAB 里没有子命令，输入完整命令（`/kit list`、`/kit give ...`）也没有任何反应。

## 症状为什么长这样

这个插件是现代 Paper 写法：它**不用** plugin.yml 的 executor，而是在 `onEnable` 里注册
`LifecycleEvents.COMMANDS` 处理器，在自己的 Brigadier 树里建 `kit create|give|delete|list`，并用
`requires(source -> source.getSender().hasPermission("kits.kit"))` 给整棵树加门禁。

- 树没注册 → 客户端看到的只有 plugin.yml 那个旧 `/kit` 节点（一个 greedy `args` 子节点，没有 executor）
  → 子命令没有来源，**TAB 是空的**
- 输入完整命令 → 走旧节点 → `PluginCommand` 没有 executor → **什么都不发生**（`/osc` 之前那条
  `CommandException: Unhandled exception executing command` 是同一个形状）

桥的日志把这一点说得很清楚：修复前只有

```
[Forbric/Bukkit] installed /kit for Kits — children=[args] runnable=true
```

（旧路径的节点）而**从来没有** `ForbricCommands.install()` 里那句 `command tree registered: ...`。

## 根因：两处"跨类加载器层的静态状态"（与物品注册表是同一个病根）

桥在运行时存在**两份**（游戏层 + FML Plugins 层），而 Paper 用 `ServiceLoader`（接口自己的加载器
= **Plugins 层**）实例化的那些类，正是插件直接接触到的：

### 1. 生命周期管理器存在另一层的静态字段里 → `COMMANDS` 事件从未被触发

`ForbricApiBridge.createPluginLifecycleEventManager()`（Plugins 层）把 manager 存进**静态** `MANAGERS`
表，而 `PluginHandle.enable()`（游戏层）用 `lifecycleManagerOf(plugin)` 读**另一份**静态表 → 恒为 null
→ 插件的 `COMMANDS` 处理器**一次都没跑**。

修法：不再存 manager，而是把它发布成 `manager::fire`（`BiConsumer<String, LifecycleEvent>`）放进程级的
System property 里。值刻意用 **JDK + paper-api 类型**，因为两份 `ForbricLifecycleEventManager` 是**不同的
Class**，把 manager 本体递过去会在另一层 ClassCastException。

### 2. 参数类型工厂同样存在另一层 → `ArgumentTypes.players()` 抛异常

修好第 1 条后，插件的注册**开始执行**了，但立刻抛：

```
IllegalStateException: a command argument type was asked for before the plugin bridge started
    at ForbricVanillaArgumentProvider.factory(...)
    at ArgumentTypes.players(ArgumentTypes.java:89)
    at com.blueleaff.kits.command.KitCommand.register(KitCommand.java:44)
```

`ForbricArgumentTypes` 的实例存在**静态**字段里（游戏层设的），Plugins 层那份读到 null。
而且不能简单地把实例强转过去：`ForbricArgumentTypes` 的方法构造的是
`net.minecraft.commands.arguments.*`，Plugins 层**根本链接不到 Minecraft**。

修法：`ForbricArgumentTypes.install()` 把实例发布进 System property，新增
`sharedInstance()`（返回 `Object`，**不强转**）；`ForbricVanillaArgumentProvider` 的 37 处
`factory().xxx(...)` 改为 `ask("xxx", ...)` —— 按方法名+参数个数**反射**调用，方法体在游戏层执行，
`ask` 用泛型返回保持各方法自己的声明类型。

## 顺带修的（这次验证路上撞到的缺口）

| 位置 | 改动 |
|---|---|
| `ServerImpl.getConsoleSender()` | 原来直接抛"not implemented"。现在返回 `ForbricConsoleSender`（继承 `ForbricCommandSender`，实现 Bukkit 的 `ConsoleCommandSender`；`Conversable` 的 7 个方法里 raw message 转发给 `sendMessage`，会话相关的按名拒绝） |
| `ForbricDeclaredCommands.run` / `BridgeBootstrap` / `BukkitBridgeLauncher.stop` | 失败原因改走 `ForbricLog`（进 `latest.log` 带堆栈），不再被 JUL 写到 `System.err` 而丢失 |

## 验证（本地实机，真插件）

```
[Forbric/Bukkit] command tree registered: LiteralCommandNode name=kit executable=false redirect=none
[Forbric/Bukkit] command tree   LiteralCommandNode name=create
[Forbric/Bukkit] command tree     ArgumentCommandNode name=name executable=true type=StringArgumentType
[Forbric/Bukkit] command tree   LiteralCommandNode name=give
[Forbric/Bukkit] command tree     ArgumentCommandNode name=name suggestions=set
[Forbric/Bukkit] command tree       ArgumentCommandNode name=player type=net.minecraft.commands.arguments.EntityArgument
[Forbric/Bukkit] command tree   LiteralCommandNode name=delete
[Forbric/Bukkit] command tree   LiteralCommandNode name=list executable=true
[Forbric/Bukkit] Kits declares /kit in plugin.yml, but a Brigadier tree already defines it — the tree stays
OK   consoleSender.hasPermission("kits.kit") -> Boolean true
```

即：`COMMANDS` 事件被触发、整棵树进了 dispatcher、`ArgumentTypes.players()` 成功（`player` 参数的
类型是 `EntityArgument`）、kit 名的补全提供者挂在 `name` 参数上（`suggestions=set`）、
plugin.yml 的旧节点正确让位给树。TAB 的子命令/kit 名来源和命令派发都因此恢复。

## 仍未实现（与本问题无关，下次可继续）

| 缺失 | 影响 |
|---|---|
| `Server.dispatchCommand()` | 插件用 `Bukkit.dispatchCommand(...)` 转发命令（本次测试探针也卡在这里） |
| `Server.getItemFactory()`、`Server.createInventory()` | `createItemStack(String)`、GUI |
| `ItemStack.serialize()/deserializeBytes()` | `ForbricItemCodec` 整类在 Plugins 层而方法体要用 MC 的 `RegistryFriendlyByteBuf`——同样的跨层处理 |

## 这个仓库反复出现的教训

桥的任何**静态状态**，只要另一层要用，就必须走 System property 这类进程级通道；而且**发布的值不能是
桥自己的类**（两份是不同的 Class），要么用 JDK/paper-api 类型（如 `BiConsumer`、`LifecycleEvent`），
要么像参数类型工厂这样按名字反射调用。已经这么做的：`ForbricRegistries`（注册表）、本次的
生命周期 firer 与参数类型工厂。
