# 实体生成修复：`World.spawn(Location, Class)` 对半数实体类抛异常

## 症状

OSCC（轨道打击炮）插件的核弹模式生成不出 TNT，日志：

```
严重: [核弹] 生成失败: java.lang.IllegalArgumentException:
  [Forbric/Bukkit] World.spawn() has no game entity type for org.bukkit.entity.TNTPrimed;
  this bridge maps a class to a type by its EntityType name
        at net.neoforbrickit.bukkit.ForbricWorld.spawnAt(ForbricWorld.java:787)
        at org.bukkit.RegionAccessor.spawn(RegionAccessor.java:424)
        at com.xiaofan.orbital_Strike_Cannon_Comapt.strike.StrikeService.launchOriginalNuke(StrikeService.java:141)
```

插件的调用（`World.spawn(loc, TNTPrimed.class, tnt -> tnt.setFuseTicks(80))`）是标准写法，
问题在桥这一侧。

## 根因

`ForbricWorld.entityTypeOf(Class)` 把 **Bukkit 实体接口的简单类名小写**当成游戏注册表 id：

```java
String name = bukkitClass.getSimpleName().toLowerCase(Locale.ROOT);  // TNTPrimed -> "tntprimed"
Identifier id = Identifier.parse(name);
return BuiltInRegistries.ENTITY_TYPE.get(id)...orElse(null);          // 没有 tntprimed -> null -> 抛异常
```

但 `org.bukkit.entity.TNTPrimed` 对应的注册表 id 是 **`tnt`**，不是 `tntprimed`。
「类名 = 注册表 id」这个前提只对一部分实体成立。

按 paper-api `26.2.build.133-stable` 的 `EntityType` 统计：**133 个实体接口里，56 个的类名不等于任何注册表 id**，
即 `World.spawn(Location, Class)` 对这 56 个全部直接抛异常，包括：

- `TNTPrimed` → `tnt`
- `PigZombie` → `zombified_piglin`
- `FallingBlock` → `falling_block`
- `AreaEffectCloud` → `area_effect_cloud`
- `EnderCrystal` → `end_crystal`
- `ItemFrame` / `GlowItemFrame` → `item_frame` / `glow_item_frame`
- `ExperienceOrb` → `experience_orb`
- `Snowman` → `snow_golem`、`IronGolem` → `iron_golem`、`MagmaCube` → `magma_cube` …

## 修复

不再猜名字，改用 API 自己发布的映射：`EntityType.getEntityClass()`（实体接口）与
`EntityType.getKey()`（命名空间 id）。后者得到的 id 与反向的 `ForbricEntity.getType()`
（`BuiltInRegistries.ENTITY_TYPE.getKey(...)` 再 `valueOf(path.toUpperCase())`）是同一条命名空间，收发对称。

| 位置 | 改动 |
|---|---|
| `ForbricWorld.entityTypeOf` | 由「类名小写 → parse」改为「查 `ENTITY_TYPES_BY_CLASS` 得 `EntityType` → `getKey()` → `Identifier.fromNamespaceAndPath`」 |
| `ForbricWorld`（新增静态字段/方法） | `ENTITY_TYPES_BY_CLASS`：遍历 `EntityType.values()`，以 `getEntityClass()` 为键建立 `Class -> EntityType` 表，`Map.copyOf` 只读。建一次而非每次 spawn 遍历 133 项；`getEntityClass()` 抛异常或返回 null 的常量跳过（`UNKNOWN`、legacy 别名），不拖累其余 |

保留原有的「找不到就返回 null、由调用方抛 `IllegalArgumentException`」契约不变，
只是现在找不到的原因是真的没有对应类型，而不是名字拼错。

## 影响面

- 修的是**所有** 56 个错配实体的 `spawn(Location, Class, Consumer)` 路径，不只 TNT。
- `spawnEntity(Location, EntityType, boolean)`（另一条入口）目前仍是 `notYet`，不在本次范围。
- `ForbricEntity.getType()`（游戏 → Bukkit 反查）本来就是对的，未改动。

## 未实机验证

本环境无 JDK，未能编译/实机跑一轮。改动仅用 `paper-api 26.2.build.133-stable` 的
字节码核对过所调用的 API（`EntityType.getEntityClass()`/`getKey()`、`NamespacedKey.getNamespace()`/`getKey()`
均存在且为具体方法），逻辑上与既有 `ForbricBlock.keyOf` 走的是同一条「用 API 的 key」思路。
建议实机复验：`world.spawn(loc, TNTPrimed.class, t -> t.setFuseTicks(80))` 应生成带引信的 TNT，
不抛异常。
