# 小圆盾专用第三人称举盾动画（Guard Raise Animation）

## Context

用户要求为小圆盾设计专用第三人称举盾动画，硬性要求：
1. 盾牌精准握持在角色长方形手臂**前端**，最终**完全举正**；
2. 预备姿势→举盾完成的自然过渡（符合人体运动规律）；
3. 层次感关键帧：**手臂抬升 → 盾牌旋转调整 → 最终稳定姿态**；
4. 时长 0.8–1.2 秒（16–24 ticks）；
5. 与行走/站立基础动作自然衔接（叠加而非覆盖）；
6. 多角度验证各视角下盾位正确。

现状（2026-09-13 回退裁定）：小圆盾格挡时第三人称保持待机姿势渲染（无 blocking 谓词、无变体）；中盾/大盾走原版 `blocking=1` 静态模型覆盖，均不变更。本任务是**新专用特性**，取代该回退对小圆盾的适用范围。

## 方案总览 — 双层动画（无 mixin、纯客户端、不动服务端窗口）

**手臂层（逐帧平滑）**：新增 NeoForge ArmPose 枚举扩展（沿用 `SwordGuardArmPose` 的 `EnumProxy` + `IArmPoseTransformer` 模式——已确认 NeoForge 给 `HumanoidModel.poseRightArm/poseLeftArm` 的 switch 打了 `default:` 分支调用扩展）。在原版 `setupAnim` 已写入走路摆臂角度之后运行，因此**增量叠加**于行走/站立动画（满足要求 5）。

**盾模型层（原版惯例阶梯插值）**：第三人称物品 display 无法逐帧驱动（无事件、1.21.1 无 per-frame 钩子），采用原版弓拉箭同款机制——新客户端物品属性 `blockmod:guard_raise`（0→1 浮点，按 tick 阈值选模型变体）。脚本生成 12 个过渡变体，变体间由平滑移动的手臂承载，整体观感流畅。

## 关键设计

### 1. 触发（服务端确认链，绝无心跳误触发）
`GuardPoseRenderer.acceptSync` 中 `GUARD_POSES` map 的 **UUID 缺失→新增**转移即格挡入场：仅当参战快照含小圆盾（`GuardEquipmentResolver.typeOf == ShieldType.BUCKLER`）时启动 per-player envelope；条目移除（格挡退出/强制放下）即停止。复用既有确认门 `shouldPose` / `isConfirmedGuardShield`（含本地玩家 ClientGuardState 门、眩晕/消耗检查）。

### 2. 手臂关键帧（`BucklerGuardRaiseArmPose`）
在 transformer 内逐帧计算（partialTick 取自 Minecraft 单例）：
- **预备 p∈[0,0.15]**：预备姿态——手臂轻微后收（xRot 正向小幅度外-回包络）；
- **抬升 p∈[0.12,0.72]**：xRot 从当前走路摆臂值 easeOutCubic 插值到目标；
- **旋转 p∈[0.40,0.90]**：yRot 从 0 easeInOutSine 插值到 ±30° 内收 + 头部偏航钳制（层次感：先抬后转）；
- **落位**：目标值精确复刻原版 `poseBlockingArm` 公式 `xRot = base*0.5 - 0.9424779 + clamp(head.xRot, -4π/9, 0.4363)`、`yRot = ±30° + clamp(head.yRot, ±π/6)` → envelope 结束切回稳态 `ArmPose.BLOCK` **无缝无跳变**（同一公式）。
- 蹲伏 +0.4、bob 摆动等原版后续叠加自动保持。

### 3. 盾模型变体（脚本生成，禁止手改）
扩展 `tools/models/calc_shield_block_variants.py` 新增小圆盾段：
- 终态 display = 原版持盾→举盾矩阵增量作用到各小圆盾自身 idle 第三人称显示 + **X 校准 0.8514**（沿用此前实测的对齐值，满足要求 1"手臂前端"）；
- 12 个中间变体 `*_raise_N.json`：rendered 空间对 rot（欧拉分量单调无绕环）/translation 线性插值，scale 保持 0.5；
- 每个变体 **firstperson_righthand/lefthand、gui、fixed、ground 显示从 idle 原样拷贝** → 第一人称与 GUI 渲染逐像素不变（第一人称管线零回归）；
- 4 个小圆盾 idle 模型追加 `overrides`：谓词 `blockmod:guard_raise` 阈值 k/12；脚本幂等（重跑清理旧变体）。

### 4. 属性（`BucklerGuardRaiseProperty`）
注册于 4 个小圆盾物品（`BlockModClient` enqueueWork，同 `ShieldBlockPoseProperty.register()` 时机——模型烘焙前）。getter：
- 非 Player / 旁观 / 未确认格挡 → 0；
- `isConfirmedGuardShield(player, stack)` 且 type==BUCKLER → 盾阶段进度值；
- 盾阶段窗口：进度 p∈[0,0.30] 保持 0（手臂先抬、盾保持待机朝向），p∈[0.30,0.85] smoothstep 扫过 12 变体（"盾牌旋转调整"），p∈[0.85,1] 恒 1.0（最终稳定姿态）；envelope 结束后持续 1.0 → 稳态即终态变体。

### 5. 配置（blockmod-client.toml，`config/Config.java`）
新增客户端段 `[buckler_guard_raise]`：`enabled`（默认 true）、`duration_ticks`（默认 20，范围 16–24 = 要求 4 的 0.8–1.2s）。绝不改服务端数值（小盾 10-tick 弹反窗口不受影响）。

### 6. 状态卫生
`BucklerGuardRaiseAnimation`：`Map<UUID, Integer> remaining`（上限=玩家数，§7.1）；ClientTickEvent.Post 递减；登录/登出/Clone/实体离开清理；格挡退出立即清零（与既有姿势行为一致）。

## 文件改动

| 文件 | 操作 |
|:---|:---|
| `client/BucklerGuardRaiseAnimation.java` | 新增：envelope 状态 + 生命周期 + 进度 API |
| `client/BucklerGuardRaiseArmPose.java` | 新增：EnumProxy + IArmPoseTransformer 关键帧 |
| `client/BucklerGuardRaiseProperty.java` | 新增：guard_raise 属性 + register() |
| `client/GuardPoseRenderer.java` | 修改：acceptSync 钩子（start/stop）、poseArm 选 pose |
| `BlockModClient.java` | 修改：注册属性 |
| `config/Config.java` | 修改：客户端配置段 |
| `tools/models/calc_shield_block_variants.py` | 修改：小圆盾变体段 + sanity check |
| `assets/.../models/item/*buckler*.json` | 脚本重新生成（12×4 变体 + 4 个 overrides） |
| `AGENTS.md` / project_memory | 记录 2026-09-13 新裁定（取代小盾回退对小盾的适用，中盾/大盾与第一人称不变） |

## 验证

1. `python tools/models/calc_shield_block_variants.py` — sanity check 通过，仅小圆盾相关文件变化；
2. `./gradlew build` — 零新警告；
3. `./gradlew runServer` — M2Verify 全探针 ok（客户端改动不应影响服务端自检）；
4. 用户 `runClient` 验收清单：
   - F5 第三人称：小圆盾入场播放 ~1s 三阶段动画，终态盾面举正、贴合手臂前端；
   - 行走/站立/蹲伏中举盾均自然衔接，无跳变；动画结束稳态无 pop；
   - 多角度环绕（正面/侧/后/俯仰）盾位正确；左手持盾镜像正确；
   - 第一人称小圆盾（突刺花式）与中盾/大盾第三人称完全不变；
   - 主手剑+副手小盾、双小盾、小盾+大盾双持组合均正常；
   - 格挡被击退/耗尽/眩晕时姿势立即解除，无残留。
