package com.pearlcannon.common;

import java.util.List;

/**
 * 运动引擎 - 核心物理计算（零 Minecraft 依赖）
 *
 * Minecraft 26.2 运动模型（Chaos Cubed）：
 * 运动顺序：加速度 → 阻力 → 位置
 * effective_drag = base_drag × air_drag_modifier
 *
 * 坐标约定：
 *   x = 东(E), y = 上(U), z = 南(S)
 *   发射方向：北(N) = -z
 *
 * 注意：此类不引用任何 Minecraft 类，
 * air_drag_modifier 由调用方传入。
 */
public final class MotionEngine {

    private MotionEngine() {}

    /**
     * 单 tick 运动更新（顺序：加速度→阻力→位置）
     * 
     * @param vx 当前 x 方向速度
     * @param vy 当前 y 方向速度  
     * @param vz 当前 z 方向速度
     * @param airDragModifier air_drag_modifier 属性值（默认1.0）
     * @return 运动后的新速度分量 [vx', vy', vz']
     */
    public static double[] applyMotion(double vx, double vy, double vz, double airDragModifier) {
        double effectiveDrag = Constants.effectiveDrag(airDragModifier);

        // 步骤1: 加速度（重力仅作用于 y）
        vy += Constants.GRAVITY;

        // 步骤2: 阻力衰减
        vx *= effectiveDrag;
        vy *= effectiveDrag;
        vz *= effectiveDrag;

        return new double[]{vx, vy, vz};
    }

    /**
     * 模拟完整珍珠飞行轨迹（200 tick）
     * 
     * @param launchVx 发射 x 速度
     * @param launchVy 发射 y 速度
     * @param launchVz 发射 z 速度
     * @param startX 起点 x 坐标
     * @param startY 起点 y 坐标
     * @param startZ 起点 z 坐标
     * @param airDragModifier 空气阻力修正值
     * @return 飞行轨迹点数组 [tick][0=x, 1=y, 2=z]
     */
    public static double[][] simulateTrajectory(
            double launchVx, double launchVy, double launchVz,
            double startX, double startY, double startZ,
            double airDragModifier) {

        double[][] trajectory = new double[Constants.MAX_PEARL_TICKS + 1][3];
        trajectory[0] = new double[]{startX, startY, startZ};

        double vx = launchVx, vy = launchVy, vz = launchVz;
        double x = startX, y = startY, z = startZ;

        double effectiveDrag = Constants.effectiveDrag(airDragModifier);

        for (int tick = 1; tick <= Constants.MAX_PEARL_TICKS; tick++) {
            // 加速度
            vy += Constants.GRAVITY;
            // 阻力
            vx *= effectiveDrag;
            vy *= effectiveDrag;
            vz *= effectiveDrag;
            // 位置
            x += vx;
            y += vy;
            z += vz;

            trajectory[tick] = new double[]{x, y, z};

            // 检测落地（y <= 目标高度时提前终止）
            if (y <= -64) break; // 世界底部边界
        }

        return trajectory;
    }

    /**
     * 计算阻力累积位移系数 Σ(d^k, k=1..t)
     * 
     * 公式：Σ(d^k) = d × (1 - d^t) / (1 - d)，当 d≠1
     *       Σ(d^k) = t，当 d=1
     */
    public static double computeDragSum(int ticks, double drag) {
        if (ticks <= 0) return 0;
        if (drag == 1.0) return ticks;
        double dragPow = Math.pow(drag, ticks);
        return drag * (1.0 - dragPow) / (1.0 - drag);
    }

    /**
     * 计算重力累积位移项
     * 
     * 每个tick的重力增量被阻力衰减后累加：
     * gravitySum = Σ_{k=1}^{t} g × d^(t-k+1)
     */
    public static double computeGravitySum(int ticks, double drag) {
        if (ticks <= 0) return 0;
        double sum = 0;
        double vyGrav = 0;
        for (int t = 1; t <= ticks; t++) {
            vyGrav += Constants.GRAVITY;
            vyGrav *= drag;
            sum += vyGrav;
        }
        return sum;
    }

    /**
     * 计算指定 tick 时的精确位置
     */
    public static double[] computePositionAtTick(
            double launchVx, double launchVy, double launchVz,
            double startX, double startY, double startZ,
            int tick, double airDragModifier) {

        double[][] traj = simulateTrajectory(
                launchVx, launchVy, launchVz,
                startX, startY, startZ, airDragModifier);

        int idx = Math.min(tick, traj.length - 1);
        return traj[idx];
    }

    /**
     * 带爆炸助推序列的轨迹模拟（消费 preBoost + Δv 序列）
     *
     * 物理模型（参考 Minecraft Wiki Projectile / Explosion 页面）：
     * <ol>
     *   <li>珍珠以 preBoost 速度进入飞行（炮口内已有速度，如重力累积、
     *       上一次小助推、炮口内弹跳等）。</li>
     *   <li>每个爆炸在其 tick 内将 Δv 叠加到珍珠当前速度：
     *       v = v + Δv（Explosion Wiki: "added to its current velocity"）。</li>
     *   <li>每 tick 按 Acc→Drag→Pos 顺序推进（1.21.2+ 投掷物顺序，
     *       26.2 沿用，见 Projectile Wiki note 4）。</li>
     * </ol>
     *
     * 与 {@link #simulateTrajectory} 的区别：
     *   后者把传入速度当作"完整初速度"，无爆炸序列；
     *   本方法接受 preBoost + 爆炸 Δv 序列，能还原珍珠炮真实飞行过程，
     *   消除"采集到 preBoost/Δv 但计算层丢弃"导致的系统性偏差。
     *
     * @param preBoostVx/Vy/Vz 助推前珍珠已有速度（采集字段）
     * @param explosions 爆炸序列（按 tick 排序；Δv 叠加到珍珠速度）
     * @param startX/Y/Z 起点（助推 tick 0 的珍珠位置）
     * @param ticks 总飞行 tick 数
     * @param airDragModifier 空气阻力修正值
     * @return 轨迹 [tick][0=x, 1=y, 2=z]，trajectory[0] = 起点
     */
    public static double[][] simulateTrajectoryWithExplosions(
            double preBoostVx, double preBoostVy, double preBoostVz,
            List<MatrixSolver.ExplosionRecord> explosions,
            double startX, double startY, double startZ,
            int ticks, double airDragModifier) {
        return simulateTrajectoryWithExplosions(
                preBoostVx, preBoostVy, preBoostVz, explosions,
                startX, startY, startZ, ticks, airDragModifier, null);
    }

    /**
     * 带爆炸助推序列的轨迹模拟（消费 preBoost + Δv 序列，含 y 方向落地截断）
     *
     * landingY 参数：落地 y 坐标（目标方块顶面）。
     * 当珍珠 y 方向位移会导致 y ≤ landingY 时，截断 y 到 landingY 并停止模拟。
     *
     * 这解决了珍珠在最后 1 tick 撞地后 y 方向继续下落但实际已停止的问题
     * （实测 FTL 炮最后 0.274 格 y 偏差：珍珠在 tick t+1 撞墙/撞地，
     *  x/z 停止，y 下落 0.274 格到方块表面，但无完整 tick 飞行）。
     *
     * 注意：本方法仅在 y 方向做截断，x/z 方向不截断。
     * 如果珍珠在 x/z 方向也撞墙，x/z 预测仍会偏大。
     *
     * @param landingY 落地 y 坐标（null 表示不截断）
     */
    public static double[][] simulateTrajectoryWithExplosions(
            double preBoostVx, double preBoostVy, double preBoostVz,
            List<MatrixSolver.ExplosionRecord> explosions,
            double startX, double startY, double startZ,
            int ticks, double airDragModifier,
            Double landingY) {

        int totalTicks = Math.max(ticks, 1);
        double[][] trajectory = new double[totalTicks + 1][3];
        trajectory[0] = new double[]{startX, startY, startZ};

        double vx = preBoostVx, vy = preBoostVy, vz = preBoostVz;
        double x = startX, y = startY, z = startZ;
        double effectiveDrag = Constants.effectiveDrag(airDragModifier);

        // 跟踪每个爆炸是否已叠加（避免重复叠加）
        boolean[] applied = new boolean[explosions.size()];

        for (int tick = 1; tick <= totalTicks; tick++) {
            // 步骤0: 在本 tick 开头叠加本 tick 内发生的爆炸 Δv
            // （珍珠炮的 TNT 在 pearl.tick() 内引爆，先叠加 Δv 再走 Acc→Drag→Pos）
            // 约定：expTick=0 表示助推发生在第一个飞行 tick（tick=1）内
            for (int i = 0; i < explosions.size(); i++) {
                if (applied[i]) continue;
                MatrixSolver.ExplosionRecord exp = explosions.get(i);
                int expTick = exp.tick() >= 0 ? exp.tick() : 0;
                if (expTick == tick - 1) {
                    vx += exp.deltaVx();
                    vy += exp.deltaVy();
                    vz += exp.deltaVz();
                    applied[i] = true;
                }
            }

            // 步骤1: 加速度（重力仅作用于 y）
            vy += Constants.GRAVITY;

            // 步骤2: 阻力衰减
            vx *= effectiveDrag;
            vy *= effectiveDrag;
            vz *= effectiveDrag;

            // 步骤3: 位置更新（含 y 方向落地截断）
            x += vx;
            double newY = y + vy;
            z += vz;

            if (landingY != null && newY <= landingY) {
                // y 撞地截断：y 停在 landingY，停止模拟
                // （珍珠撞到方块顶面，后续 tick 不再飞行）
                y = landingY;
                trajectory[tick] = new double[]{x, y, z};
                // 填充剩余 tick 为最终位置（保持数组长度一致）
                for (int i = tick + 1; i <= totalTicks; i++) {
                    trajectory[i] = new double[]{x, y, z};
                }
                break;
            } else {
                y = newY;
                trajectory[tick] = new double[]{x, y, z};
            }

            // 检测落地（世界底部边界）
            if (y <= -64) break;
        }

        return trajectory;
    }
}
