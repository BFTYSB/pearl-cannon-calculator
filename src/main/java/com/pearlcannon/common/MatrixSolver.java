package com.pearlcannon.common;

import java.util.List;

/**
 * 发射速度求解引擎（零 Minecraft 依赖）
 *
 * 核心职责：根据 N 次爆炸助推数据，求解最优发射速度。
 *
 * 采用位移平衡模型（见技术备忘录 3.x）：
 *   珍珠最终位移 = start + v × dragSum(t) + Σ(Δv_i × remainingSum_i)
 * 反解：
 *   v = (target - start - Σ(Δv_i × remainingSum_i)) / dragSum(t)
 *
 * 2D 模式：仅解水平 vx/vz，vy 由重力补偿；3D 模式：解全分量（含重力补偿）。
 *
 * 数据流：ExplosionDataCollector → MatrixSolver → LaunchVelocity
 */
public final class MatrixSolver {

    private MatrixSolver() {}

    /**
     * 爆炸助推数据记录
     *
     * 每次爆炸对珍珠产生的速度增量：
     *   Δv_x = (explosion_x - pearl_x) / distance × power × factor
     *
     * 助推前预存速度（preBoostVx/Y/Z）：
     *   珍珠在本次 TNT 助推发生前的当前速度。
     *   珍珠在炮口内可能已有非零初速度（例如上一次小助推、重力累积、
     *   炮口内弹跳等），仅记录 Δv 会丢失这部分贡献，导致 y 方向
     *   计算出现系统性偏差（见 FTL 炮测试日志 20260805-145830）。
     *   真实助推后速度 = preBoost + Δv。
     */
    public record ExplosionRecord(
            double deltaVx, double deltaVy, double deltaVz,
            double explosionX, double explosionY, double explosionZ,
            double pearlX, double pearlY, double pearlZ,
            double power, int tick,
            double preBoostVx, double preBoostVy, double preBoostVz) {

        /** 向后兼容：无 preBoost 信息时默认为 0（旧调用点 / 旧预设文件） */
        public ExplosionRecord(
                double deltaVx, double deltaVy, double deltaVz,
                double explosionX, double explosionY, double explosionZ,
                double pearlX, double pearlY, double pearlZ,
                double power, int tick) {
            this(deltaVx, deltaVy, deltaVz,
                    explosionX, explosionY, explosionZ,
                    pearlX, pearlY, pearlZ,
                    power, tick,
                    0, 0, 0);
        }

        /** 向后兼容：无 tick 且无 preBoost 信息 */
        public ExplosionRecord(
                double deltaVx, double deltaVy, double deltaVz,
                double explosionX, double explosionY, double explosionZ,
                double pearlX, double pearlY, double pearlZ,
                double power) {
            this(deltaVx, deltaVy, deltaVz,
                    explosionX, explosionY, explosionZ,
                    pearlX, pearlY, pearlZ,
                    power, -1);
        }
    }

    /**
     * 2D 珍珠炮求解（伪逆方法）
     *
     * 用于常规炮和弱加载炮模式（忽略 y 分量，简化为 x-z 平面）
     * 构造超定系统 A·v = b，使用 SVD 伪逆求解：v = pinv(A) × b
     *
     * 返回值语义（v1.0.2 修正）：
     *   返回"助推后珍珠真实总速度" = preBoost + ΣΔv + 额外所需速度。
     *   旧版本只返回"额外所需速度"（剥离了 preBoost 与 Δv），
     *   导致 {@link MotionEngine#simulateTrajectory} 把它当作完整初速度，
     *   预测轨迹严重偏离实际（FTL 炮测试 x/z 偏差 262/405 格）。
     *
     * @param explosions 爆炸数据列表（含 preBoost 字段）
     * @param targetX 目标 x 坐标
     * @param targetZ 目标 z 坐标
     * @param startX 起点 x
     * @param startZ 起点 z
     * @param ticks 飞行 tick 数
     * @param airDragModifier 空气阻力修正值
     * @return 求解结果：[boostedVx, boostedVy, boostedVz]（助推后真实总速度）
     */
    public static double[] solve2D(
            List<ExplosionRecord> explosions,
            double targetX, double targetZ,
            double startX, double startZ,
            int ticks, double airDragModifier) {

        double effectiveDrag = Constants.effectiveDrag(airDragModifier);
        double dragSum = MotionEngine.computeDragSum(ticks, effectiveDrag);
        if (dragSum == 0) {
            return new double[]{0, 0, 0};
        }

        // 位移平衡模型（参考 Projectile Wiki 与 Explosion Wiki）：
        //   珍珠最终位移 = start + boostedV × dragSum(t)
        //   其中 boostedV = preBoost + ΣΔv + 额外所需速度
        //
        // 爆炸 Δv 在 pearl.tick() 内叠加到珍珠速度（Explosion Wiki:
        //   "added to its current velocity"），之后按 Acc→Drag→Pos 推进。
        //   闭式解中 Δv 的位移贡献 = Σ(Δv_i × remainingSum_i)，
        //   remainingSum_i = dragSum(t - k_i)，k_i 为第 i 次爆炸发生的 tick。
        //
        // preBoost 是助推前已有速度，整个飞行都参与位移贡献：
        //   preBoost 位移贡献 = preBoost × dragSum(t)
        //
        // 反解额外所需速度 v_extra：
        //   target = start + (preBoost + v_extra) × dragSum + Σ(Δv_i × remainingSum_i)
        //   v_extra = (target - start - preBoost×dragSum - Σ(Δv_i×remainingSum_i)) / dragSum
        //
        // 最终返回 boostedV = preBoost + ΣΔv + v_extra（助推后真实总速度）
        double preBoostVx = 0, preBoostVz = 0;
        double sumDvx = 0, sumDvz = 0, sumDeltaVx = 0, sumDeltaVz = 0;
        for (int i = 0; i < explosions.size(); i++) {
            ExplosionRecord exp = explosions.get(i);
            if (i == 0) {
                // 约定：所有爆炸共享同一 preBoost（炮口内珍珠已有速度）
                preBoostVx = exp.preBoostVx();
                preBoostVz = exp.preBoostVz();
            }
            int k = exp.tick() >= 0 ? Math.min(exp.tick(), ticks - 1) : Math.min(i + 1, ticks - 1);
            double remainingSum = MotionEngine.computeDragSum(ticks - k, effectiveDrag);
            sumDvx += exp.deltaVx() * remainingSum;
            sumDvz += exp.deltaVz() * remainingSum;
            sumDeltaVx += exp.deltaVx();
            sumDeltaVz += exp.deltaVz();
        }

        double vExtraX = (targetX - startX - preBoostVx * dragSum - sumDvx) / dragSum;
        double vExtraZ = (targetZ - startZ - preBoostVz * dragSum - sumDvz) / dragSum;

        // 助推后真实总速度（传给 simulateTrajectoryWithExplosions 作为初速度）
        double boostedVx = preBoostVx + sumDeltaVx + vExtraX;
        double boostedVz = preBoostVz + sumDeltaVz + vExtraZ;
        // 2D 模式：y 由重力补偿（caller 会用 InversionEngine 覆盖此值）
        double boostedVy = 0;

        return new double[]{boostedVx, boostedVy, boostedVz};
    }

    /**
     * 3D 珍珠炮求解（直接求逆方法）
     *
     * 用于三维矢量炮模式（完整 x-y-z 三分量）
     * 构造 3×3 系统 A·v = b，使用直接矩阵求逆：v = inv(A) × b
     *
     * 返回值语义（v1.0.2 修正）：
     *   返回"助推后珍珠真实总速度" = preBoost + ΣΔv + 额外所需速度
     *   （与 solve2D 同步修正，详见 solve2D 注释）
     *
     * @param explosions 爆炸数据列表（含 preBoost 字段，至少3次）
     * @param targetX/Y/Z 目标坐标
     * @param startX/Y/Z 起点坐标
     * @param ticks 飞行 tick 数
     * @param airDragModifier 空气阻力修正值
     * @return 求解结果：[boostedVx, boostedVy, boostedVz]（助推后真实总速度）
     */
    public static double[] solve3D(
            List<ExplosionRecord> explosions,
            double targetX, double targetY, double targetZ,
            double startX, double startY, double startZ,
            int ticks, double airDragModifier) {

        double effectiveDrag = Constants.effectiveDrag(airDragModifier);
        double dragSum = MotionEngine.computeDragSum(ticks, effectiveDrag);
        if (dragSum == 0) {
            return new double[]{0, 0, 0};
        }
        // y 方向除爆炸贡献外还需补偿重力累积位移 gravitySum
        double gravitySum = MotionEngine.computeGravitySum(ticks, effectiveDrag);

        // 位移平衡模型（3D 全分量，含 preBoost 与 Δv 序列）：
        //   target = start + (preBoost + v_extra)×dragSum
        //          + gravitySum(y) + Σ(Δv_i × remainingSum_i)
        // 反解 v_extra，返回 boostedV = preBoost + ΣΔv + v_extra
        double preBoostVx = 0, preBoostVy = 0, preBoostVz = 0;
        double sumDvx = 0, sumDvy = 0, sumDvz = 0;
        double sumDeltaVx = 0, sumDeltaVy = 0, sumDeltaVz = 0;
        for (int i = 0; i < explosions.size(); i++) {
            ExplosionRecord exp = explosions.get(i);
            if (i == 0) {
                preBoostVx = exp.preBoostVx();
                preBoostVy = exp.preBoostVy();
                preBoostVz = exp.preBoostVz();
            }
            int k = exp.tick() >= 0 ? Math.min(exp.tick(), ticks - 1) : Math.min(i + 1, ticks - 1);
            double remainingSum = MotionEngine.computeDragSum(ticks - k, effectiveDrag);
            sumDvx += exp.deltaVx() * remainingSum;
            sumDvy += exp.deltaVy() * remainingSum;
            sumDvz += exp.deltaVz() * remainingSum;
            sumDeltaVx += exp.deltaVx();
            sumDeltaVy += exp.deltaVy();
            sumDeltaVz += exp.deltaVz();
        }

        double vExtraX = (targetX - startX - preBoostVx * dragSum - sumDvx) / dragSum;
        double vExtraY = (targetY - startY - preBoostVy * dragSum - gravitySum - sumDvy) / dragSum;
        double vExtraZ = (targetZ - startZ - preBoostVz * dragSum - sumDvz) / dragSum;

        double boostedVx = preBoostVx + sumDeltaVx + vExtraX;
        double boostedVy = preBoostVy + sumDeltaVy + vExtraY;
        double boostedVz = preBoostVz + sumDeltaVz + vExtraZ;

        return new double[]{boostedVx, boostedVy, boostedVz};
    }

    /**
     * 整数解遍历优化
     * 
     * 对浮点解进行 ±N 范围的整数遍历，找误差最小的整数组合。
     * 用于实际炮搭建时需要整数格数的情况。
     * 
     * @param floatingSolution 浮点解 [vx, vy, vz]
     * @param trajectory 预测轨迹
     * @param targetX/Y/Z 目标坐标
     * @param ticks 飞行 ticks
     * @param airDragModifier 空气阻力修正值
     * @param searchRange 搜索范围（±N）
     * @return 最优整数解及误差
     */
    public static IntegerSolution findBestIntegerSolution(
            double[] floatingSolution,
            double startX, double startY, double startZ,
            double targetX, double targetY, double targetZ,
            int ticks, double airDragModifier,
            int searchRange) {

        double bestError = Double.MAX_VALUE;
        int[] bestInt = new int[3];

        for (int ix = -searchRange; ix <= searchRange; ix++) {
            for (int iy = -searchRange; iy <= searchRange; iy++) {
                for (int iz = -searchRange; iz <= searchRange; iz++) {
                    double testVx = Math.round(floatingSolution[0]) + ix;
                    double testVy = Math.round(floatingSolution[1]) + iy;
                    double testVz = Math.round(floatingSolution[2]) + iz;

                    double[][] traj = MotionEngine.simulateTrajectory(
                            testVx, testVy, testVz,
                            startX, startY, startZ, airDragModifier);

                    int idx = Math.min(ticks, traj.length - 1);
                    double dx = traj[idx][0] - targetX;
                    double dy = traj[idx][1] - targetY;
                    double dz = traj[idx][2] - targetZ;
                    double error = Math.sqrt(dx*dx + dy*dy + dz*dz);

                    if (error < bestError) {
                        bestError = error;
                        bestInt = new int[]{
                            (int) Math.round(testVx),
                            (int) Math.round(testVy),
                            (int) Math.round(testVz)};
                    }
                }
            }
        }

        return new IntegerSolution(bestInt, bestError);
    }

    /**
     * 整数解结果
     */
    public record IntegerSolution(int[] velocity, double error) {}

}
