# 小圆盾举盾动画闪退 — 问题分析与修复报告

- 日期：2026-09-13
- 严重级别：Critical（第三人称小圆盾格挡必现闪退）
- 状态：已修复，等待 runClient 复测验收

## 1. 复现路径与触发条件

| 项 | 内容 |
|:---|:---|
| 操作路径 | 第三人称视角（F5）下，玩家持小圆盾格挡入场（服务端确认格挡 → 举盾动画信封激活约 1 秒内） |
| 必要条件 | ① 第三人称渲染玩家模型；② `GuardPoseRenderer.poseArm` 判定该手为小圆盾；③ `BucklerGuardRaiseAnimation.isActive(uuid)` 为真（入场动画信封内） |
| 频率 | 必现。小圆盾在第三人称格挡入场时 100% 崩溃（两份崩溃报告 22:01:07 / 22:03:52 同一签名，间隔 2 分钟连续复现） |
| 不触发场景 | 第一人称（不渲染玩家模型）；中盾/大盾（走 `ArmPose.BLOCK` 分支）；动画信封过期后的稳态小圆盾格挡（回退 `BLOCK` 分支） |
| 日志位置 | `run/crash-reports/crash-2026-09-13_22.01.07-client.txt`、`crash-2026-09-13_22.03.52-client.txt` |

用户描述的"切换操作"（换装/切手/换视角）只是恰好发生在入场窗口内——真正的判据是**格挡入场确认后的动画信封窗口**，与具体切换动作无关。

## 2. 崩溃签名

```
Description: Rendering entity in world
java.lang.NullPointerException: Enum not initialized. Did you forget to configure
the field holding this proxy as a parameter in the enum extension config file?
    at net.neoforged.fml.common.asm.enumextension.EnumProxy.getValue(EnumProxy.java:46)
    at com.example.blockmod.client.GuardPoseRenderer.poseArm(GuardPoseRenderer.java:138)
    at com.example.blockmod.client.GuardPoseRenderer.onRenderPlayer(GuardPoseRenderer.java:107)
    ...  PlayerRenderer.render → EntityRenderDispatcher.render → LevelRenderer.renderLevel
```

## 3. 根本原因

`BucklerGuardRaiseArmPose` 通过 NeoForge 的枚举扩展机制向 `HumanoidModel.ArmPose` 注入新常量
`BUCKLER_RAISE`（`EnumProxy` 字段）。该机制的注册链是：

1. `src/main/templates/META-INF/neoforge.mods.toml` 声明 `enumExtensions="META-INF/enumextensions.json"`；
2. `META-INF/enumextensions.json` 列出每个枚举扩展的常量名、构造器签名、以及**持有 `EnumProxy` 实例的字段**；
3. FML 在枚举类加载时按配置反射读取这些字段，调用 `getValue()` 取真实枚举常量并回填 proxy 内部值。

本次实现漏掉了第 2 步：`enumextensions.json` 只注册了旧的 `SwordGuardArmPose.SWORD_BLOCK`，
`BucklerGuardRaiseArmPose.BUCKLER_RAISE` 未注册。FML 不会为未注册的 proxy 注入常量，
其内部值保持 null——首次调用 `getValue()` 即抛出该 NPE。

- 调用点只有 `GuardPoseRenderer.poseArm` 第 138 行，且仅在"小圆盾 + 动画信封激活"分支才触达，
  与复现条件完全吻合。
- 编译期无法发现：`EnumProxy` 字段本身合法存在，类型检查全部通过；只有运行期首次取值才炸。
- `runServer` 自检无法覆盖：`HumanoidModel.ArmPose` 是纯客户端类，dedicated server 不加载。

## 4. 修复方案

单行注册，改动文件：`src/main/resources/META-INF/enumextensions.json`

```json
{
  "enum": "net/minecraft/client/model/HumanoidModel$ArmPose",
  "name": "BLOCKMOD_BUCKLER_RAISE",
  "constructor": "(ZLnet/neoforged/neoforge/client/IArmPoseTransformer;)V",
  "parameters": {
    "class": "com/example/blockmod/client/BucklerGuardRaiseArmPose",
    "field": "BUCKLER_RAISE"
  }
}
```

与既有 `BLOCKMOD_SWORD_BLOCK` 条目同构：常量名唯一、构造器签名 `(boolean, IArmPoseTransformer)`
与 `EnumProxy` 构造参数逐位对应。动画类本体、渲染逻辑、模型变体均无需改动。

## 5. 验证

| 项 | 结果 |
|:---|:---|
| `./gradlew build` | 通过，零新警告 |
| 打包产物 | `build/libs/blockmod-1.0.0.jar` 内 `META-INF/enumextensions.json` 已含两条注册（解包核对） |
| 客户端启动冒烟测试 | 启动无枚举扩展加载错误 |
| 游戏内复测 | 待用户执行（见 §7） |

## 6. 预防措施

1. **约定**：今后任何 `EnumProxy` 枚举扩展常量，必须在同一改动中把字段登记进
   `META-INF/enumextensions.json`——新增 ArmPose/枚举常量时这是与 `@SubscribeEvent` 同级的必做步骤。
   已记入项目记忆（Lessons Learned）。
2. **审查清单**：实现枚举扩展时 diff 必须成对出现：`xxx.java`（proxy 字段）+ `enumextensions.json`（注册）。
   只见其一即为缺陷。
3. **验证口径**：枚举扩展只影响客户端渲染，`runServer` 自检不覆盖；凡新增 ArmPose 扩展，
   验收必须含 runClient 第三人称实机渲染路径，不能只以 build + runServer 通过为完成标准。

## 7. 复测清单

1. 第三人称持小圆盾格挡入场：播放约 1 秒三阶段动画，**不再闪退**，终态盾面举正贴合手臂前端；
2. 反复进出格挡（入场动画期间多次重入）与动画中被打断（耗尽/眩晕/换装）均无崩溃、无残留姿势；
3. 第一人称小圆盾、中盾/大盾第三人称、主手剑+副手小盾双持均与修复前一致（无回归）；
4. 行走/站立/蹲伏衔接与多角度视角下盾位正确。
