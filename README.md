# Block & Parry（格挡与招架）

一个 Minecraft **NeoForge 1.21.1** 格挡 mod：用体力管理取代原版"举盾免伤"——格挡消耗体力、
体力枯竭时防御完全失效且解除减速，掐准时机招架可以零消耗反制。

> 当前版本：MVP（v0.1）。数值与机制详见 `docs/BlockParry-软件规格说明书-v2.0.md`。

## 安装

1. 安装 [NeoForge 21.1.248](https://neoforged.net/)（或对应安装器版本 ≥ 21.1.x）；
2. 将 `build/libs/blockmod-1.0.0.jar` 放入 `mods/` 目录；
3. 启动游戏，创造模式物品栏「格挡与招架」分页可获取全部 11 件盾牌装备。

## 玩法速览

| 装备 | 操作 | 效果 |
|:---|:---|:---|
| 任意剑 | 长按右键 | 格挡（gb 0.20，无移速惩罚），**按下后 5 刻内**受击触发招架 |
| 小圆盾 | 长按右键 | 格挡（移速 -40%），**10 刻内**受击触发招架 |
| 中盾（含原版盾牌） | 长按右键 | 格挡（移速 -70%），不可招架；**格挡中左键=盾击**（8 伤害+击退，1s 冷却） |
| 大盾 | 长按右键 | 格挡（移速 -90%），不可招架；**按住 Left Alt=强力防御**（体力 2/s，跳不起来，gb 提升） |

- 格挡消耗体力 `⌊(dmg×9.4)^PFIX⌋×(1−gb)`，PvE 0.7 / PvP 0.9；
- 体力 ≤ 0 进入**枯竭**：无法格挡/招架，但移速惩罚自动解除，8/s 快速恢复；
- 满饥饿进食回复营养值等量体力；
- 招架：剑 5 刻 / 小圆盾 10 刻窗口内免伤+零消耗，近战反制眩晕 1 秒，箭矢弹开；
- Boss（Warden/凋灵/末影龙）需累计 3 次招架才被眩晕。

## 指令（op 2）

```
/blockparry stamina get [玩家]     查询体力
/blockparry stamina set <值> [玩家] 设置体力（可为负）
/blockparry stamina fill [玩家]    回满
/blockparry deplete [玩家]         清零（调试枯竭）
/blockparry debug [玩家]           输出完整状态快照
```

## 配置

`config/blockmod-server.toml`（服务端），全部分节数值可调，支持热重载：
`[stamina]`/`[guard]`/`[parry]`/`[shield_bash]`/`[power_guard]`/`[durability]`/
`[boss_detection]`/`[network]`/`[compat]`/`[debug]`。非法值自动回退默认并记录 ERROR。

## 已知问题

- 盾牌图标为程序生成的占位纹理（原版盾牌图标走实体渲染器，无法直接复用），正式美术资源待 v0.2；
- 眩晕粒子使用原版效果漩涡（自定义 `stun_star` 为 v0.2）；
- 招架/格挡音效复用原版盾牌音效（自定义音效为 v0.2）；
- 枯竭期间作弊客户端仍可发移动包（原版移动本为客户端权威，防作弊超出 MVP 范围）；
- 第三方无 `guard_profile` 组件的盾牌暂不可格挡（FR-27 兼容层为 v0.2）。

## 开发

```bash
./gradlew build        # 构建（build/libs/*.jar）
./gradlew test         # 66 项纯函数单元测试
./gradlew runClient    # 客户端开发运行
./gradlew runServer    # 专用服开发运行（服务端权威验证）
```

架构规则与任务流程见 `AGENTS.md`。
