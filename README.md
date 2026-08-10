# Pearl Cannon Calculator 🎯

Minecraft 26.2 (Chaos Cubed) Fabric 模组 — 游戏内末影珍珠炮弹道求解器。

支持三种炮模式：**常规炮** / **弱加载炮** / **三维矢量炮**，双端自适应架构（单人直算、多人走网络）。

> ⚠️ **开发阶段声明**
> 
> 本模组目前处于 **v1.0.0-WIP 测试初版**，尚未发布正式版。
> 功能骨架已完成，物理模型与计算逻辑正在验证中，存在已知问题（详见 [DEVELOPMENT.md](DEVELOPMENT.md)）。
> 不建议在生产环境或正式服务器中使用。欢迎测试反馈与贡献。

---

## 文件说明

### 构建配置

| 文件 | 用途 |
|---|---|
| [build.gradle](build.gradle) | Gradle 构建脚本：Fabric Loom 1.17、EJML 0.44、JDK 25、双端入口 |
| [gradle.properties](gradle.properties) | 全局属性：MC 26.2、Fabric Loader 0.19.3、Fabric API 0.156.0 |
| [settings.gradle](settings.gradle) | Gradle 项目设置：Fabric Maven 仓库、Foojay 工具链解析器 |
| [gradle/wrapper/gradle-wrapper.properties](gradle/wrapper/gradle-wrapper.properties) | Gradle Wrapper 配置：9.6.1 |

### 项目文档

| 文件 | 用途 |
|---|---|
| [DEVELOPMENT.md](DEVELOPMENT.md) | 开发规格书：物理模型推导、模块架构、数据流、已知问题与修复建议 |
| [memo.txt](memo.txt) | 技术备忘录：26.2 版本差异、运动模型、设计决策记录 |

### 模组资源

| 文件 | 用途 |
|---|---|
| [fabric.mod.json](src/main/resources/fabric.mod.json) | Fabric 模组元数据：Mod ID、双端入口（main + client）、Mixin 声明、依赖约束 |
| [pearl-cannon-calculator.mixins.json](src/main/resources/pearl-cannon-calculator.mixins.json) | Mixin 配置文件：注入目标类列表 |
| [zh_cn.json](src/main/resources/assets/pearl-cannon-calculator/lang/zh_cn.json) | 简体中文语言文件 |
| [en_us.json](src/main/resources/assets/pearl-cannon-calculator/lang/en_us.json) | 英文语言文件 |

### 源码 → `common/` — 纯计算层（零 Minecraft 依赖）

| 文件 | 用途 |
|---|---|
| [Constants.java](src/main/java/com/pearlcannon/common/Constants.java) | 物理常数：基础阻力、重力加速度、air_drag_modifier 范围、精度阈值 |
| [CannonMode.java](src/main/java/com/pearlcannon/common/CannonMode.java) | 炮型枚举：REGULAR / WEAK_LOADING / VECTOR_3D，含本地化名与网络序列化 |
| [MotionEngine.java](src/main/java/com/pearlcannon/common/MotionEngine.java) | 26.2 运动引擎：单 tick Acc→Drag→Pos 更新、完整轨迹模拟、带爆炸序列轨迹（含 landingY 截断） |
| [InversionEngine.java](src/main/java/com/pearlcannon/common/InversionEngine.java) | 位移反推初速度：根据目标落点反算发射速度、y 轴重力补偿、最小 tick 二分估算 |
| [MatrixSolver.java](src/main/java/com/pearlcannon/common/MatrixSolver.java) | 位移平衡求解器：2D/3D 反解（闭式代数法）、整数解遍历优化、ExplosionRecord 数据结构 |
| [WeakLoadingCorrector.java](src/main/java/com/pearlcannon/common/WeakLoadingCorrector.java) | 弱加载 λ 校正：时间衰减 × 空间衰减模型、爆炸记录批量校正 |
| [CannonCalculator.java](src/main/java/com/pearlcannon/common/CannonCalculator.java) | 统一计算门面：模式分发 → 求解 → 轨迹预测 → 精度评估 → 整数优化 |
| [EnvironmentDetector.java](src/main/java/com/pearlcannon/common/EnvironmentDetector.java) | 单人/多人/未知环境判定 |
| [DebugLog.java](src/main/java/com/pearlcannon/common/DebugLog.java) | 调试日志（仅开发阶段）：多级别日志、自动轮转、采集会话隔离 |

### 源码 → `network/` — 网络层

| 文件 | 用途 |
|---|---|
| [PCCPackets.java](src/main/java/com/pearlcannon/network/PCCPackets.java) | 包类型枚举 + 注册 |
| [PCCNetworkHandler.java](src/main/java/com/pearlcannon/network/PCCNetworkHandler.java) | 网络包注册与路由 |
| [CalculationRequestPacket.java](src/main/java/com/pearlcannon/network/CalculationRequestPacket.java) | C→S：客户端发送计算请求 |
| [CalculationResultPacket.java](src/main/java/com/pearlcannon/network/CalculationResultPacket.java) | S→C：服务端返回计算结果 |
| [ExplosionVectorSyncPacket.java](src/main/java/com/pearlcannon/network/ExplosionVectorSyncPacket.java) | S→C：爆炸向量同步 |

### 源码 → `client/` — 客户端

| 文件 | 用途 |
|---|---|
| [PearlCannonClient.java](src/main/java/com/pearlcannon/client/PearlCannonClient.java) | 客户端入口：按键注册、生命周期 |
| [CannonCalculatorScreen.java](src/main/java/com/pearlcannon/client/CannonCalculatorScreen.java) | 主 GUI 屏幕：模式切换、参数输入 |
| [TrajectoryRenderer.java](src/main/java/com/pearlcannon/client/TrajectoryRenderer.java) | 轨迹粒子渲染 |
| [ClientNetworkReceiver.java](src/main/java/com/pearlcannon/client/ClientNetworkReceiver.java) | 客户端网络回调处理 |
| [TargetInputWidget.java](src/main/java/com/pearlcannon/client/gui/TargetInputWidget.java) | 目标坐标输入控件 |
| [ResultDisplayWidget.java](src/main/java/com/pearlcannon/client/gui/ResultDisplayWidget.java) | 计算结果展示控件 |
| [TrajectoryPreviewWidget.java](src/main/java/com/pearlcannon/client/gui/TrajectoryPreviewWidget.java) | 轨迹预览控件 |

### 源码 → `server/` — 服务端

| 文件 | 用途 |
|---|---|
| [ServerCalculationHandler.java](src/main/java/com/pearlcannon/server/calculator/ServerCalculationHandler.java) | 服务端计算调度 |
| [ExplosionDataCollector.java](src/main/java/com/pearlcannon/server/collector/ExplosionDataCollector.java) | 爆炸数据采集（Mixin Hook 入口） |
| [TickFreezer.java](src/main/java/com/pearlcannon/server/collector/TickFreezer.java) | Tick 冻结/步进控制 |
| [PearlCalcCommand.java](src/main/java/com/pearlcannon/server/command/PearlCalcCommand.java) | `/pearlcalc` 指令处理 |

### 源码 → `config/` — 配置持久化

| 文件 | 用途 |
|---|---|
| [ConfigSerializer.java](src/main/java/com/pearlcannon/config/ConfigSerializer.java) | 炮预设序列化：保存/加载/导入/导出 |

### 源码 → `mixin/` — Mixin 注入

| 文件 | 用途 |
|---|---|
| [ServerExplosionMixin.java](src/main/java/com/pearlcannon/mixin/ServerExplosionMixin.java) | Hook `Level.explode()`：采集爆炸速度向量 |
| [PearlEntityTickMixin.java](src/main/java/com/pearlcannon/mixin/PearlEntityTickMixin.java) | Hook 珍珠实体 tick：读取 air_drag_modifier |

### 模组入口

| 文件 | 用途 |
|---|---|
| [PearlCannonCalculator.java](src/main/java/com/pearlcannon/PearlCannonCalculator.java) | 模组主入口：注册网络包、指令、事件监听 |

---

## 物理模型

```
每 tick 运动顺序：加速度 → 阻力衰减 → 位置更新

  vy  ←  vy - 0.03
  v   ←  v × (0.9900000095367432 × air_drag_modifier)
  pos ←  pos + v

最大飞行 200 tick ｜ 基础阻力 0.9900000095367432 ｜ air_drag_modifier 默认 1.0
```

---

## 构建

```bash
# 前提：JDK 25
gradlew build
# 输出：build/libs/pearl-cannon-calculator-1.0.0.jar
```

---

## 许可

MIT License — 详见 [LICENSE](src/main/resources/LICENSE)
