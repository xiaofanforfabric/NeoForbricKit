# Bukkit / Paper 插件桥

在 NeoForge（本仓库 = NeoFobric）里运行 **Bukkit / Paper 插件**。

代码来源：**Forbric 项目**（`github.com/Ray-T-r/Minecraft-Forbric-mod-loader`，Apache-2.0）的
`forbric-kernel/src/runtime/java/{org/cn/xiaofan/bukkit,io/papermc}` 与内核接缝。
移植原则：**能 COPY 就 COPY，不能才改**。

## ⚠️ 这不是一个 Gradle 子项目

`projects/bukkitbridge/src/main/{java,resources}` 是**源目录**，由 `:neoforge` 的 main sourceSet
直接编译（见 `projects/neoforge/build.gradle` 的 `srcDirs`）。这里**没有** `build.gradle`。

**原因（一个解不开的编译环）**：

```
:bukkitbridge:compileJava → :neoforge:compileJava    桥要 net.minecraft.*（只有 :neoforge 有）
:neoforge:compileJava     → :bukkitbridge:jar        :neoforge 运行时要桥的类，且它的 jar 要进 launcher profile
```

两条边都是真需求。试过的弯路：

| 尝试 | 结果 |
|---|---|
| `compileOnly(project(':neoforge'))` | 环 |
| 依赖 MDG 的 `clean/joined.jar` | 能编，但 `ArgumentTypeInfos.registerByClass` 是 **NeoForge patch 加的**，clean 版没有 → 失败 |
| `DownloaderJar` 打包 `:neoforge:compileJava` 输出 | 编译通过，但 `libraries(project(...))` 仍成环 |
| 纯文件 artifact（无 producer task） | 环断了，但**没有 Maven 坐标** → `Don't know how to break bukkitbridge-….jar into Maven components` |
| 文件 artifact + `mustRunAfter` | 同上的坐标问题 |
| **源码并入 `:neoforge`（最终采用）** | ✅ 零环；连反射调用也不需要了 |

这与 Forbric 一致 —— 它的桥也编在内核的 runtime source set 里
（*"a kernel that boots at all has its bridge"*）。

## COPY / ADAPT 一览

| 类别 | 文件 | 处理 |
|---|---|---|
| **桥接层（38 文件，约 18.2k 行）** | `org/cn/xiaofan/bukkit/*.java` | ✅ **原样 COPY，零改动** |
| **Paper provider（5 文件）** | `io/papermc/paper/**` | ✅ **原样 COPY，零改动**（接口包私有，实现必须同包） |
| ServiceLoader 注册 | `resources/META-INF/services/*` | ✅ **原样 COPY，零改动** |
| 日志 | `net/forbric/kernel/util/ForbricLog.java` | 🔧 COPY + 改 3 处（日志名、debug 开关、stderr 前缀） |
| Paper API 下载器 | `net/forbric/kernel/boot/PaperApiFetcher.java` | 🔧 COPY + 改缓存路径；**NeoFobric 不调用**（改声明式，见下） |
| 启动入口 | `net/forbric/kernel/boot/BukkitBridgeLauncher.java` | 🔧 COPY + 改属性名与日志前缀 |
| 运行时门面 | `net/forbric/kernel/boot/BukkitBridgeHooks.java` | ➕ **新增**（NeoForge 无 kernel，用它承接生命周期调用） |
| 交互注入器 | `net/forbric/kernel/transform/BukkitInteractInjector.java` | 🔧 **ASM 核心整段 COPY**；外壳从 Forbric `ClassTransformer` 换成 NeoForge `ClassProcessor` |
| `pack.mcmeta` | — | ❌ **删除**（NeoForge 自带；那份是 Forbric 品牌资源包声明） |

**包名保留原样**（`org.cn.xiaofan.bukkit`、`net.forbric.kernel.*`）。

## 为什么几乎没有改动就能跑

- **桥接层对平台的依赖只有一个类**：`ForbricLog`。对 `net.neoforged` / `net.fabricmc` /
  `net.minecraftforge` 的 import 是**零**。
- **桥不订阅加载器事件总线**：它用 ASM 把 `PlayerInteractEvent` 注入到 **vanilla 方法**
  （`ServerPlayerGameMode.useItemOn` / `handleBlockBreakAction`、
  `ServerGamePacketListenerImpl.handleUseItem` / `handleAnimate`）。原注释：
  *"No NeoForge type appears anywhere in this package."*
- **唯一涉及 NeoForge 的地方是 2 行 import**（`BukkitInteractInjector` 的
  `ClassProcessor` / `ProcessorName`）。

## 接线点

| 位置 | 做什么 |
|---|---|
| `META-INF/services/net.neoforged.neoforgespi.transformation.ClassProcessor` | FML 靠 ServiceLoader 发现 `BukkitInteractInjector` |
| `ServerLifecycleHooks.handleServerStarted` | `BukkitBridgeHooks.startBukkitBridge(server)` |
| `ServerLifecycleHooks.handleServerStopped` | `BukkitBridgeHooks.stopBukkitBridge()` |
| `CommonModLoader.begin` | `BukkitBridgeLauncher.setGameDirectory(FMLPaths.GAMEDIR.get())` |

## Paper API 从哪来（**踩过一个坑**）

`paper-api` / `bungeecord-chat` 在 **`repo.papermc.io`**。但 NeoForge 的 `LibraryCollector`
（`buildSrc`）有一个 **`HOST_WHITELIST`**：

```java
private static final List<String> HOST_WHITELIST = List.of(
        "minecraft.net", "neoforged.net", "mojang.com");
```

**`repo.papermc.io` 被过滤掉** → 安装器把 URL 写成 `maven.neoforged.net/...`（404）→ 安装失败。

**解法**：把 Paper API 及其闭包**嵌进安装器** maven 目录（`bukkitApiEmbedded` 配置 +
`installerJar` 的 `from(...)`）。安装器先解压嵌入的 maven，启动器检查 SHA-1 命中即跳过下载。

嵌入清单**显式列出**（`transitive = false`），因为 `paper-api` 的传递闭包会拖进
maven-resolver 全家桶和 MC 已自带的 gson/guava/brigadier/fastutil/log4j/joml ——
在同一个 loader 上放两份同类库正是原版 `PaperApiFetcher` 列出清单要避免的。

## 已知边界：NMS 插件

桥**没有 CraftBukkit 的 `getHandle()` 路径**。强依赖 NMS 的插件（如 TAB）跑不了 ——
与 Forbric 的规则一致。纯 `org.bukkit.*` API 的插件是目标。

## 构建

```bash
export JAVA_HOME=<java 25>
# 代理环境: 必须同时给 JAVA_TOOL_OPTIONS 和 gradle -D（Gradle 自己的 HTTP client 不吃前者）
./gradlew :neoforge:createCleanArtifacts   # 首次: 下载 MC
./gradlew :neoforge:setup                  # 首次: 解压 patched 源码
./gradlew :neoforge:installerJar
```

产物: `projects/neoforge/build/libs/neoforge-<version>-installer.jar`

## 尚未验证

编译、打包、嵌入全部验证过，但**没有实机跑过**（`runServer` + 放一个插件进 `plugins/`）。
