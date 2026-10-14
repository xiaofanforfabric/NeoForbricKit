# `:bukkitbridge` — Bukkit / Paper 插件桥

在 NeoForge（本仓库 = NeoFobric）里运行 **Bukkit / Paper 插件** 的子项目。

代码来源：**Forbric 项目**（`github.com/Ray-T-r/Minecraft-Forbric-mod-loader`，Apache-2.0）的
`forbric-kernel/src/runtime/java/{org/cn/xiaofan/bukkit,io/papermc}` 与内核接缝。
移植原则：**能 COPY 就 COPY，不能才改**（见下表）。

## COPY / ADAPT 一览

| 类别 | 文件 | 处理 |
|---|---|---|
| **桥接层（38 文件，约 18.2k 行）** | `org/cn/xiaofan/bukkit/*.java` | ✅ **原样 COPY，零改动** |
| **Paper provider（5 文件）** | `io/papermc/paper/**` | ✅ **原样 COPY，零改动**（接口是包私有，实现必须同包） |
| ServiceLoader 注册 + `pack.mcmeta` | `resources/META-INF/services/*` | ✅ **原样 COPY，零改动** |
| 日志 | `net/forbric/kernel/util/ForbricLog.java` | 🔧 COPY + 改 3 处（日志名 `NeoFobric/Bukkit`、开关 `-Dneofobric.debug`、stderr 前缀） |
| Paper API 下载器 | `net/forbric/kernel/boot/PaperApiFetcher.java` | 🔧 COPY + 改 2 处（缓存目录 `.neofobric/bukkit-libs`、日志前缀） |
| 启动入口 | `net/forbric/kernel/boot/BukkitBridgeLauncher.java` | 🔧 COPY + 改属性名（`neofobric.bukkitBridge`）与日志前缀 |
| 交互注入器 | `net/forbric/kernel/transform/BukkitInteractInjector.java` | 🔧 **ASM 核心整段 COPY**；外壳从 Forbric `ClassTransformer` 换成 NeoForge `ClassProcessor` |

**包名保留原样**（`org.cn.xiaofan.bukkit`、`net.forbric.kernel.*`）—— 这是刻意的：改动面最小，
唯一的新增 shim 是 `ForbricLog`（同包名同签名，让 3 个文件的 14 处调用一行不改）。

## 为什么几乎没有改动就能跑

- **桥接层对平台的依赖只有一个类**：`ForbricLog`。对 `net.neoforged` / `net.fabricmc` /
  `net.minecraftforge` 的 import 是**零**。
- **桥不订阅加载器事件总线**：它用 ASM 把 `PlayerInteractEvent` 注入到 **vanilla 方法**
  （`ServerPlayerGameMode.useItemOn` / `handleBlockBreakAction`、
  `ServerGamePacketListenerImpl.handleUseItem` / `handleAnimate`）。原注释：
  *"No NeoForge type appears anywhere in this package."*
  这个设计正好让它在换宿主时免疫。

## 交互注入器的宿主适配（唯一有实质改动的部分）

| | Forbric | NeoForge（本仓库） |
|---|---|---|
| SPI | `implements ClassTransformer` | `implements ClassProcessor` |
| 入口 | `byte[] transform(String, byte[], TransformContext)` | `ComputeFlags processClass(TransformationContext)` |
| 输入 | raw `byte[]`，要自己 `ClassReader` | `context.node()` 直接给 `ClassNode` |
| 帧计算 | 手写 `FrameComputingWriter(COMPUTE_FRAMES)` | `return ComputeFlags.COMPUTE_FRAMES` |
| 报告 | `AnchorSet` / `claims()` 台账 | `context.audit(...)` |

**`inject` / `injectOnStartDestroy` / `injectUseItemPacket` / `method` / `hasPlayerField` /
`alreadyInjected` / `headIsBranchTarget` / `frameLocals` / `withPlayer` / `arguments` /
`declined` / `simpleName` 全部逐字保留**（含它们的长注释 —— 那些注释记录了两次失败尝试）。

## 构建

```bash
# 代理必须同时给 JAVA_TOOL_OPTIONS 和 gradle -D（Gradle 自己的 HTTP client 不吃前者）
export JAVA_HOME=<java 25>
./gradlew :neoforge:createCleanArtifacts   # 下载 MC（首次）
./gradlew :neoforge:setup                  # 解压 patched 源码（首次）
./gradlew :bukkitbridge:jar
```

产物：`projects/bukkitbridge/build/libs/bukkitbridge-<version>.jar`

## 尚未接线（下一步）

- **启动挂钩**：`BukkitBridgeLauncher.setGameDirectory(...)` / `ownedJars(...)` /
  `start(loader, gameDir, server)` 还没有被 NeoForge 侧调用。
  计划挂到 `CommonModLoader.begin`（那里已有 `// NeoFobric:` 的 Fabric 入口点调用）。
- **部署**：jar 还没进安装器/游戏层。
- **注入器注册**：`BukkitInteractInjector` 还没注册进 FML 的 `ClassProcessor` 列表。
- **插件类加载**：NeoForbric 没有"每插件一个 ClassLoader"的概念，`PluginClassLoader` 的父加载器语义待接。
