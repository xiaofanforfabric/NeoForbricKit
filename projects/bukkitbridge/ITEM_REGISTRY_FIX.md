# 物品注册表修复：插件的"给自定义物品"命令报 `PAPER isn't an item`

## 症状

服务端上插件的给物品命令（`/osc give ...`）抛错，物品给不出来。`latest.log` 里只有外壳：

```
[Forbric/Bukkit] Orbital_Strike_Cannon_Comapt threw while running /osc [give, stab, xiaofan]:
org.bukkit.command.CommandException: Unhandled exception executing command 'osc' in plugin ...
```

真因没进日志，因为桥用 `server.getLogger()`（`java.util.logging`，ConsoleHandler 写 `System.err`）报告的，而运维看的是 log4j 的 `latest.log`。

用一个探针插件（真 `JavaPlugin`，在 `onEnable` 里逐项调用造物品的 API）在本地服务端复现，结果：

```
OK   Material.PAPER.asItemType()        -> null          ← 应该是 ItemType
OK   Material.PAPER.isItem()            -> false         ← 应该是 true
FAIL new ItemStack(Material.PAPER)        java.lang.IllegalArgumentException: PAPER isn't an item
FAIL ItemStack.of(Material.PAPER)         （同一个异常）
OK   Registry.ITEM.get(minecraft:paper) -> null          ← 应该是 PAPER
```

## 根因：一次跨类加载器的调用，失败被吞成了"物品不存在"

桥在运行时存在**两份**（游戏层 / FML Plugins 层）：

- Paper 用 `ServiceLoader` 实例化 `io.papermc.paper.registry.RegistryAccess` 的 provider（`ForbricRegistryAccess`），用的是**接口自己的加载器** = FML Plugins 层；
- 那一层**看不到 `net.minecraft.*`**；而 `ForbricItemType` 的签名里就写着 `BuiltInRegistries`，只能由游戏层定义。

所以旧代码在 API 层调 `ForbricItemType.of(id)` 必然失败，而：

```java
} catch (Exception e) {
    return null;          // 所有失败都变成"这个 id 不存在"
}
```

于是 `Registry.ITEM.get()` 对每个 id 都回答 null → `Material.asItemType()` 为 null → `ItemStack.of` 抛 `IllegalArgumentException: PAPER isn't an item` → 插件的命令炸，而堆栈指向插件自己的代码。

## 改动

| 文件 | 改动 |
|---|---|
| `bukkit/ForbricItemLookup.java`（新增） | 物品查询在**游戏层**的公开半身：`keys()` / `of(namespace, path)` / `size()`，全部直连 `BuiltInRegistries.ITEM`。公开且静态，正是为了让另一层按名字调用 |
| `bukkit/ForbricRegistryAccess.java` | API 层的 `Registry.ITEM` 改为委托 `ForbricItemLookup`（`gameLayer` 取自服务端发布的 `RegistryAccess` 对象的加载器）；`stream()` 里直接引用 `net.minecraft.resources.Identifier` 的写法去掉；所有失败改为 `ForbricLog` 报告（每种问题只报一次），不再吞成 null |
| `bukkit/ForbricDeclaredCommands.java` | 命令执行失败的**原因**改走 `ForbricLog`（进 `latest.log`，带堆栈），不再走 JUL/`System.err` |
| `bukkit/BridgeBootstrap.java` | "某个 jar 加载失败"与"bridge ready"改走 `ForbricLog`，同样是为了让 `latest.log` 能自证 |
| `bridge/boot/BukkitBridgeLauncher.java` | 关服时的 `InvocationTargetException` 拆包后再报，不再只打一个类名 |

## 验证（本地实机）

```
信息: installed the game's registries for the Bukkit item registry (1537 items)
OK   Material.PAPER.asItemType()        -> ForbricItemType ForbricItemType[minecraft:paper]
OK   Material.PAPER.isItem()            -> Boolean true
OK   new ItemStack(Material.PAPER)      -> ItemStack ItemStack{PAPER x 1}
OK   new ItemStack(Material.PAPER, 3)   -> ItemStack ItemStack{PAPER x 3}
OK   ItemStack.of(Material.PAPER, 5)    -> ForbricItemStack ForbricItemStack[PAPER x5]
OK   Registry.ITEM.get(minecraft:paper) -> ForbricItemType ForbricItemType[minecraft:paper]
OK   stack.setItemMeta(meta) / asOne() / clone() / getMaxStackSize() / isSimilar()
```

## 仍未实现（这次没动）

| 缺失 | 位置 | 影响 |
|---|---|---|
| `ItemMeta.set/getCustomModelData` | `ForbricItemMeta`（**生成文件**） | 自定义贴图物品的标准做法，很可能是下一条会撞上的 |
| `ItemMeta.setUnbreakable` | 同上 | 不可破坏物品 |
| `Server.getItemFactory()` | `ServerImpl.notYet` | `Bukkit.getItemFactory().createItemStack("...")` 这条路 |
| `Server.createInventory()` | `ServerImpl.notYet` | 打开 GUI 菜单 |
| `ItemStack.serialize()/deserializeBytes()` | `ForbricItemCodec` | 跨层 `NoClassDefFoundError: net/minecraft/core/RegistryAccess` —— 与本次同一个病根，需要同样的"游戏层半身"处理 |

**关于 `ForbricItemMeta`**：文件头写着 `*** GENERATED FILE — DO NOT EDIT ***`，由 `tools/gen_stub.py` + `tools/stubs/ForbricItemMeta.table` 生成，而这两个文件都不在仓库里（`.gitignore` 忽略 `*.py` 与 `/tools/*`）。所以 `setCustomModelData` 这类新增应当加进那张表再重新生成，否则下次生成会把就地修改冲掉。
