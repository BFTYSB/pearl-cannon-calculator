# Pearl Cannon Calculator — 开发规格书

## 1. 项目元信息

| 字段 | 值 |
|---|---|
| Mod ID | `pearl-cannon-calculator` |
| 包根 | `com.pearlcannon` |
| 目标 MC 版本 | 26.2 (Chaos Cubed) |
| 核心运动模型 | 1.21.2+ Acc→Drag→Pos |
| 语言 | Java 25 |
| 构建系统 | Gradle 9.6.1 + Fabric Loom 1.17-SNAPSHOT |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.156.0+26.2 |
| 外部依赖 | EJML 0.44.0（ejml-core + ejml-ddense，当前声明但未实际调用） |
| 许可证 | MIT |
| 开发阶段 | v1.0.0 WIP（初版，功能骨架完成，待验证与修复） |

---

## 2. 功能摘要

在 Minecraft Java Edition 26.2 内集成的**终端珍珠炮计算器模组**，提供三种炮模式的统一求解接口：

| 模式 | CannonMode 枚举 | 求解维度 | 求解策略 | 特化参数 |
|---|---|---|---|---|
| 常规炮 | `REGULAR` | x-z 平面 (2D) | 位移平衡模型反解（注释标注为 SVD 伪逆路线，当前为纯代数闭式解） | 无 |
| 弱加载炮 | `WEAK_LOADING` | x-z 平面 (2D) | 同常规炮 + λ 加载衰减校正 | 加载衰减系数 λ（经验公式） |
| 三维矢量炮 | `VECTOR_3D` | x-y-z 空间 (3D) | 位移平衡全分量反解（注释标注为直接求逆路线） | 3 组爆炸向量需不共面 |

**双端自适应**：单人模式（IntegratedServer）在客户端直算，零网络开销；多人模式（远程服务端）通过 `PCCNetworkHandler` 发包到服务端计算并回传结果。

---

## 3. 物理模型（26.2 特化）

### 3.1 单 tick 运动更新

```
顺序：Acceleration → Drag → Position

  v_y  ← v_y + g            (g = -0.03)
  v    ← v × effectiveDrag
  effectiveDrag = 0.9900000095367432 × air_drag_modifier

  pos ← pos + v
```

- `air_drag_modifier` 默认 1.0（范围 0.0–2048.0），由调用方从 `EntityAttributes.AIR_DRAG_MODIFIER` 动态读取传入
- 基础阻力值 `0.9900000095367432` 为 `float(0.99f)` 的 IEEE 754 double 精确表示
- 最大飞行 200 tick

### 3.2 位移累积闭式解

```
dragSum(t)      = d × (1 - d^t) / (1 - d)        （d ≠ 1）
gravitySum(t)   = Σ_{k=1}^{t} [ g × d^(t−k+1) ]   （当前逐 tick 计算）

最终位移:
  pos_t = pos_0 + v_0 × dragSum(t) + gravity_y(t)
```

当存在爆炸助推序列时（含 preBoost + Δv）：

```
pos_t = pos_0 + preBoost × dragSum(t) + Σ(Δv_i × dragSum(t − tick_i)) + gravity_y(t)
```

### 3.3 反解公式

```
v_extra = (target − start − preBoost × dragSum − Σ(Δv_i × remainingSum_i)) / dragSum
boostedV = preBoost + ΣΔv + v_extra
```

2D 模式 y 分量由 `InversionEngine.computeGravityCompensationVy` 独立补偿。

---

## 4. 模块架构

```
src/main/java/com/pearlcannon/
├── PearlCannonCalculator.java          # 模组主入口：注册网络、指令、生命周期事件
│
├── common/                             # 纯计算层（零 Minecraft 类引用，可独立单元测试）
│   ├── Constants.java                  # 物理常数、版本号、浮点精度阈值
│   ├── CannonMode.java                 # 炮型枚举 + 本地化名 + 网络序列化
│   ├── MotionEngine.java              # 26.2 运动引擎：单 tick 更新 / 纯轨迹 / 带爆炸序列轨迹（含 landingY 截断）
│   ├── InversionEngine.java           # 位移反推初速度 / y 重力补偿 / 最小 tick 二分估算
│   ├── MatrixSolver.java              # 位移平衡求解器：solve2D / solve3D / 整数解遍历
│   ├── WeakLoadingCorrector.java      # λ 衰减：时间衰减 × 空间衰减 / 爆炸记录校正
│   ├── CannonCalculator.java          # 统一计算门面：模式分发 → 求解 → 精度评估 → 整数优化
│   ├── EnvironmentDetector.java       # 单人/多人/未知环境判定
│   └── DebugLog.java                  # 调试日志：多级别 / 自动轮转 / 采集会话隔离
│
├── network/                            # 网络层（共享序列化逻辑）
│   ├── PCCPackets.java                # 包类型枚举 + 注册
│   ├── PCCNetworkHandler.java         # 网络包注册与路由
│   ├── CalculationRequestPacket.java  # 客户端 → 服务端：计算请求
│   ├── CalculationResultPacket.java   # 服务端 → 客户端：计算结果
│   └── ExplosionVectorSyncPacket.java # 爆炸向量同步包
│
├── client/                             # 客户端专用
│   ├── PearlCannonClient.java         # 客户端入口 + 按键注册
│   ├── CannonCalculatorScreen.java    # 主 GUI 屏幕
│   ├── ClientNetworkReceiver.java     # 客户端网络回调
│   ├── TrajectoryRenderer.java        # 轨迹粒子渲染
│   └── gui/
│       ├── TargetInputWidget.java      # 目标输入控件
│       ├── ResultDisplayWidget.java    # 结果显示控件
│       └── TrajectoryPreviewWidget.java # 轨迹预览控件
│
├── server/                             # 服务端专用
│   ├── calculator/
│   │   └── ServerCalculationHandler.java # 服务端计算调度
│   ├── collector/
│   │   ├── ExplosionDataCollector.java    # 爆炸数据采集（Mixin Hook）
│   │   └── TickFreezer.java              # Tick 冻结/步进控制
│   └── command/
│       └── PearlCalcCommand.java         # /pearlcalc 指令
│
├── config/
│   └── ConfigSerializer.java          # 炮预设保存/加载/导入/导出
│
└── mixin/
    ├── ServerExplosionMixin.java       # Hook Level.explode() 采集爆炸速度向量
    └── PearlEntityTickMixin.java       # Hook 珍珠 tick 获取 air_drag_modifier
```

### 4.1 数据流

```
客户端 GUI 输入目标                     服务端环境
       │                                    │
       ▼                                    ▼
EnvironmentDetector.detect()          Mixin Hook 采集
       │                             ExplosionDataCollector
  ┌────┴────┐                              │
  ▼         ▼                              ▼
单人直算   多人发包              ExplosionRecord 列表
  │         │                              │
  └────┬────┘                              │
       ▼                                   ▼
  CannonCalculator.calculate(mode, explosions, ...)
       │
       ▼
  MatrixSolver.solve2D/solve3D  ──→  boostedV (含 preBoost + ΣΔv + v_extra)
       │
       ▼
  MotionEngine.simulateTrajectoryWithExplosions(landingY)
       │
       ▼
  CalculationResult → GUI 显示
```

---

## 5. 网络协议

| 方向 | 包类型 | 内容 |
|---|---|---|
| C→S | `CalculationRequestPacket` | 目标坐标、起点坐标、ticks、airDragModifier、爆炸记录列表 |
| S→C | `CalculationResultPacket` | 完整 `CalculationResult`（含轨迹、误差、整数解） |
| S→C | `ExplosionVectorSyncPacket` | 采集到的爆炸向量数据同步 |

包注册通过 `PCCPackets` 枚举集中管理。

---

## 6. 构建与运行

```bash
# 环境前提
JDK 25（gradle.properties 中配置 toolchain → languageVersion = 25）

# 构建
gradlew build

# 运行客户端（IDE Run Config 已预置）
# .idea/runConfigurations/Minecraft_Client.xml

# 输出
build/libs/pearl-cannon-calculator-1.0.0.jar
```

`build.gradle` 中定义的关键配置：
- `java.toolchain.languageVersion = JavaLanguageVersion.of(25)`
- `tasks.withType(JavaCompile).options.release = 25`
- `loom.mods` 注册了 sourceSet main
- `processResources` 将 `mod_version` 注入 `fabric.mod.json` 中的 `${version}`

---

## 7. 待修复问题（v1.0.0 审计记录）

### 🔴 高优先级

#### 7.1 轨迹预测与发射速度不同步
**文件**: `CannonCalculator.java` calculate() 方法
**问题**: `solve2D/solve3D` 返回的 `launchVelocity` 是 `preBoost + ΣΔv + v_extra`（到达目标所需的完整初速度），但轨迹模拟调用的是 `simulateTrajectoryWithExplosions(preBoostVx,...,explosions,...)`，只模拟了 `preBoost + ΣΔv`，**未包含 v_extra**。
**影响**: 用户看到的 `predictedTrajectory` 落点短于目标，但结果中的 `launchVx/Vz` 数值正确，两者矛盾。
**建议**: 求解完成后，直接用 `launchVelocity` 作为单一初速度调用 `simulateTrajectory()`，不再重复消费 explosions。或者在 simulateTrajectoryWithExplosions 参数中增加 v_extra 注入机制。

#### 7.2 EJML 依赖声明但未使用
**文件**: `build.gradle` + `MatrixSolver.java`
**问题**: build.gradle 声明并 `include` 了 ejml-core 和 ejml-ddense（4 行配置），但全源码中不存在任何 `org.ejml` 引用。MatrixSolver 注释标注"SVD 伪逆 / 直接求逆"，实际为纯代数位移平衡模型。`Constants.SVD_THRESHOLD`（1e-10）为孤立常量。
**影响**: 约 1MB 无用的外部 jar 打包进最终模组文件。
**建议**: 二选一 —— (a) 删除 ejml 依赖和 SVD_THRESHOLD，MatrixSolver 重命名为更准确的名字；(b) 真正引入 SVD 伪逆处理病态矩阵场景（3D 炮条件数差时）。

### 🟡 中优先级

#### 7.3 DebugLog 引用 FabricLoader，违反 common 层零 MC 依赖约束
**文件**: `DebugLog.java` detectLogDir() 方法
**问题**: 直接 import `net.fabricmc.loader.api.FabricLoader`，而 DebugLog 位于 `common/` 包内。common 包的设计目标是纯计算、可独立于 MC 测试。
**注意**: DebugLog 仅为开发阶段调试工具，正式发布前将整体移除，因此该问题在 v1.0.0 开发期无需修复。

#### 7.4 EnvironmentDetector 设计空壳
**文件**: `EnvironmentDetector.java`
**问题**: 类本身无 MC 引用，但 `detect(boolean, boolean)` 签名要求调用方传入 MC 上下文布尔值。该类的价值在于"封装判定逻辑"，但当前实现仅做了两次 `if` 分支，封装价值为零——调用方仍需自己获取 `hasIntegratedServer`。
**建议**: 要么在类内部直接引用 `MinecraftClient.getInstance()`，把它移出 common 包；要么降级为一个工具枚举，不提供 detect 方法。

#### 7.5 弱加载炮多次爆炸时 λ 校正不完整
**文件**: `CannonCalculator.java` calculate() WEAK_LOADING 分支 + `WeakLoadingCorrector.java`
**问题**: 传给 `applyCorrection` 的 `delayTicksArray` 和 `unloadedChunksArray` 均只有 1 个元素。applyCorrection 内部用 `(i < length) ? arr[i] : 0` 保护不越界，但第 2 次及之后的爆炸均使用默认值 0。如果弱加载炮有 2+ 次爆炸且中间跨越未加载区块，计算会偏。
**建议**: 传入与 explosions 等长的数组，或改为在 applyCorrection 内部生成（延迟和未加载 chunk 数按爆炸序列均匀分布/根据位置递增）。

### 🟢 低优先级 / 优化建议

#### 7.6 类名不准确
- `MatrixSolver` → `LaunchSolver` 或 `VelocitySolver`（类内无矩阵操作）
- `InversionEngine` → `GravityCompensator` 或 `LaunchVelocityEstimator`（角色很小，仅用于 y 重力补偿）

#### 7.7 computeGravitySum 使用逐 tick 迭代而非闭式解
**影响**: 每次调用 O(n)（n ≤ 200），结合 `findBestIntegerSolution` 的 1331 次遍历时放大至 ~26 万次 FP 运算。实际性能影响微小，但作为代码质量建议用闭式解代替。

#### 7.8 findBestIntegerSolution 全量暴力搜索
**影响**: 11³ = 1331 个候选全遍历，可考虑剪枝优化（例如 y 方向独立优化后联合 x/z）。

---

## 8. 关键设计决策

| 决策 | 理由 |
|---|---|
| 独立 Fabirc Mod 而非 Carpet 扩展 | 覆盖更广玩家群体；Carpet 存在时可选增强 |
| 双端自适应（客户端/服务端两套计算路径） | 单人不走网络，降低延迟；多人保证权威计算 |
| 位移平衡模型（代数反解）而非矩阵求解 | 避免引入重型依赖；2D 炮问题超定但性态良好，闭式解同等精度且零外部依赖 |
| PreBoost 机制 | 实测发现炮口内珍珠已有非零速度（重力累积、小助推），忽略会导致 y 方向系统性偏差 |
| landingY 截断 | 珍珠最后 1 tick 撞地后 y 继续下落的 ~0.274 格偏差需要特殊处理 |
| explode() Hook（Mixin 注入）而非 Carpet 采集 | 保持独立模组定位，不依赖第三方 |

---

## 9. 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0.0 | 2026-08 | 初版：三种炮型框架、双端架构、MotionEngine/MatrixSolver/PreBoost/landingY 截断 |

---

## 10. 参考

- 技术备忘录: [memo.txt](file:///D:/珍珠计算器/memo.txt)
- MC 26.2 Release: `https://www.minecraft.net/en-us/article/minecraft-java-edition-26-2`
- MC Wiki 26.2: `https://minecraft.wiki/w/Java_Edition_26.2`
- 珍珠炮教程: `https://minecraft.wiki/w/Tutorials/Pearl_cannon`
- 投掷物运动学: `https://minecraft.wiki/w/Projectile`
- PearlCalculatorRS (Rust 参考实现): `https://github.com/MliroLirrorsIngenuity/PearlCalculatorRS`
- Fabric 开发文档: `https://docs.fabricmc.net/develop/getting-started/setting-up`
