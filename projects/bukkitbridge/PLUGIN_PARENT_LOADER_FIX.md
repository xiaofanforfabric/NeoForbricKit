# 插件父加载器修复：两层父加载器没有生效

## 症状

服务端启动后 Bukkit 插件一个都起不来，`latest.log` 里是：

```
[NeoFobric/Bukkit] the plugin bridge could not start; Bukkit plugins stay unloaded
java.lang.NoClassDefFoundError: io/papermc/paper/plugin/lifecycle/event/types/LifecycleEventType
    at net.neoforbrickit.bukkit.PluginHandle.load(PluginHandle.java:64)
    at net.neoforbrickit.bukkit.BridgeBootstrap.loadAll(BridgeBootstrap.java:172)
    ...
Caused by: java.lang.ClassNotFoundException: io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType
    at java.net.URLClassLoader.findClass
```

同一份日志里桥自己打印的诊断已经指出了问题所在：

```
ServerBuildInfo defined by java.net.URLClassLoader@66eb985d      ← 纸 API 在这一层
bridge defined by ...TransformingClassLoader@6ce90bc5            ← 桥（和游戏）在这一层
```

## 根因

### 1. `apiLayer` 取成了「本类的加载器」，也就是游戏层（主因）

`BukkitBridgeHooks.pluginParentLoader()` 原本写的是：

```java
ClassLoader apiLayer = BukkitBridgeHooks.class.getClassLoader();   // 其实是游戏层
...
if (loader instanceof ClassLoader gameLayer && gameLayer != apiLayer) {
    return newTwoLayerLoader(apiLayer, gameLayer);                 // 因此永远不执行
}
return apiLayer;                                                    // 插件只拿到游戏层
```

而 `gameLayer`（FML 当前加载器）和它**是同一个加载器**，于是条件恒假，`TwoLayerClassLoader` 一次都没被构造。插件被父接到游戏层，而 `io.papermc.paper.*` 只在纸 API 层 —— 这正是 `TwoLayerClassLoader` 自己的注释预写过的失败方式：

> parented to the game layer, a plugin that touches `LifecycleEventType` throws ClassNotFoundException out of `URLClassLoader.findClass`

### 2. 构造两层加载器失败时被静默吞掉

原 `pluginParentLoader()` 的 `catch (Throwable unreachable)` 既不分情况也不打日志，所以「两层加载器构造失败」和「本来就不需要两层」在日志里长得一模一样，掩盖了上面那个判断错误。

### 3. 单个插件的 `Error` 会把整个桥带崩

`BridgeBootstrap.loadAll()` 原来只 catch `IOException | InvalidDescriptionException | ReflectiveOperationException | RuntimeException`，而 `NoClassDefFoundError` 是 `Error`。它穿出循环后：

- 桥整体报告「the plugin bridge could not start」；
- 在它之前已经 enable 的插件被丢弃：既没有 `onDisable`，命令也留在了 dispatcher 里。

## 改动

| 文件 | 改动 |
|---|---|
| `bridge/boot/BukkitBridgeHooks.java` | API 层改为**探针实际定义纸 API 的加载器**（`io.papermc.paper.ServerBuildInfo` / `org.bukkit.Bukkit` / `net.kyori.adventure.text.Component` 的 `getClassLoader()`），不再拿本类加载器顶替；两层加载器的查找在多个层上依次尝试；构造失败改为 warn 并说明回退到哪一层；启动时无条件打印 `plugin parent: bridge=… api=… game=…` |
| `bridge/boot/BukkitBridgeLauncher.java` | `start(...)` 拆成 `start(ClassLoader bridgeLoader, ClassLoader pluginParent, Path gameDir, Object server)`：桥自身的类仍从**原来的加载器**载入（它的静态成员和注入到 MC 里的调用点必须同源），只有插件拿到新的父加载器 |
| `bukkit/TwoLayerClassLoader.java` | 第一层失败按 `Throwable` 兜底，不再只 catch `ClassNotFoundException`（FML 的 `ModuleClassLoader` 占了包却没有类时抛的是 `NoClassDefFoundError`）；两层都失败时把两个原因都挂上；`toString` 带上两层 |
| `bukkit/BridgeBootstrap.java` | 每个插件的 catch 加上 `| Error`，一个坏 jar 只影响它自己 |

## 验证

编译与打包（本机 Gradle 9.7 / JDK 25）：

```
:neoforge:setup              BUILD SUCCESSFUL in 1m 39s
:neoforge:compileBridgeJava  BUILD SUCCESSFUL in 2m 10s
:neoforge:installerJar       BUILD SUCCESSFUL in 2m 19s
```

实机：用安装器装一个干净服务端，`plugins/` 里放两个探针 jar，启动后日志：

```
[NeoFobric/Bukkit] plugin parent: bridge=TransformingClassLoader@4cc76301 api=java.net.URLClassLoader@3fc9dfc5 game=TransformingClassLoader@4cc76301
```

`api` 与 `game` 是两个不同的加载器 —— 修复前这里不可能出现（两者相等）。两个探针的结果：

```
# 对照：引用 LifecycleEventType 但故意不继承 JavaPlugin 的类
could not load loader-probe.jar: java.lang.IllegalStateException: probe.LinkingProbe does not extend JavaPlugin
    at net.neoforbrickit.bukkit.PluginHandle.load(PluginHandle.java:66)     ← 第 64 行的 Class.forName 已通过

# 正向：真 JavaPlugin
probe.RealProbe onEnable; lifecycle API = io.papermc.paper.plugin.lifecycle.event.types.ForbricPrioritizableEventType@54e9147e
enabled RealProbe v1.0 from loader-real.jar
bridge ready: 1 plugin(s) enabled
```

即：`LifecycleEventType` 这类纸 API 内部包的类现在能被插件解析；真插件走完 plugin.yml → 主类链接 → `JavaPlugin` 的 `ConfiguredPluginClassLoader` 守卫 → `init` → `onLoad`/`onEnable`；坏 jar 单独报错，桥继续运行。

## 本地构建的前置条件（否则会撞不相关的错）

1. **代理**：本机 `maven.neoforged.net` 只能过 Clash（`127.0.0.1:7890`）。JVM 不走 Windows 系统代理，所以 Gradle 与派生的 JVM 都要显式给：
   ```properties
   # ~/.gradle/gradle.properties（用户级，不进仓库）
   systemProp.http.proxyHost=127.0.0.1
   systemProp.http.proxyPort=7890
   systemProp.https.proxyHost=127.0.0.1
   systemProp.https.proxyPort=7890
   ```
   启动服务端 / 跑安装器时还要 `JAVA_TOOL_OPTIONS=-Dhttps.proxyHost=... -Dhttps.proxyPort=...`（安装器要从 neoforged maven 下库）。
2. **磁盘**：`neoform` 打补丁的产物要搬进 Gradle 用户目录，C 盘只剩 5 GB 会失败并报 `磁盘空间不足`。把 `GRADLE_USER_HOME` 指到空间大的盘（本机用的是 `I:\gradle-home`）。
3. **顺序**：MC 的反编译源码不入库（`projects/neoforge/.gitignore` 忽略 `src`），首次必须
   ```
   gradlew :neoforge:createCleanArtifacts     # 下载 MC
   gradlew :neoforge:setup                    # 解压 patched 源码，否则 compileJava 报「程序包 net.minecraft 不存在」
   ```
   之后才谈得上 `compileBridgeJava` / `installerJar`。

## 遗留

- 真实插件（`Orbital_Strike_Cannon_Comapt`、`TEST`）未在本机验证（没有那两个 jar）。
- 未修：PR #1 评审提到的 `ServerLifecycleHooks.invokeBukkitHook` 会选中 `startBukkitBridge(Object)` 这个 1 参重载的问题；以及 `projects/neoforge/build.gradle` 中服务声明来源与注释不一致的问题。
- 桥的类目前同时存在于游戏层的 joinedJar 与 FML Plugins 层的 `bridge.jar`，本次改动把「桥自身留在游戏层、插件给两层」固定下来，但没有消除这份重复。
