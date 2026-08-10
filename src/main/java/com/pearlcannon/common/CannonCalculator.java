package com.pearlcannon.common;

import java.util.List;

/**
 * 珍珠炮计算器 - 统一计算入口（零 MC 依赖）
 * 
 * 根据炮模式选择对应的求解策略：
 * - REGULAR → solve2D (伪逆)
 * - WEAK_LOADING → solve2D (伪逆 + WeakLoadingCorrector λ 校正)
 * - VECTOR_3D → solve3D (直接求逆)
 * 
 * 输出结果包含最优发射速度、预测轨迹、精度评估、整数解优化。
 */
public final class CannonCalculator {

    private CannonCalculator() {}

    /**
     * 计算结果数据类
     */
    public record CalculationResult(
            CannonMode mode,
            double launchVx, double launchVy, double launchVz,
            double targetX, double targetY, double targetZ,
            double startX, double startY, double startZ,
            int ticks,
            double airDragModifier,
            double[][] predictedTrajectory,
            double accuracyError,
            boolean solvable,
            int[] integerSolution,
            double integerError,
            int estimatedTNTCount) {}

    /**
     * 执行珍珠炮计算（完整版）
     * 
     * @param mode 炮模式
     * @param explosions 爆炸数据列表
     * @param targetX/Y/Z 目标坐标
     * @param startX/Y/Z 起点坐标
     * @param ticks 飞行 tick 数
     * @param airDragModifier 空气阻力修正值
     * @param weakLoadingDelay 弱加载延迟 tick 数（仅 WEAK_LOADING 使用）
     * @return 完整计算结果
     */
    public static CalculationResult calculate(
            CannonMode mode,
            List<MatrixSolver.ExplosionRecord> explosions,
            double targetX, double targetY, double targetZ,
            double startX, double startY, double startZ,
            int ticks, double airDragModifier,
            int weakLoadingDelay) {

        double[] launchVelocity;
        boolean solvable = true;

        // 真实助推前速度（取第一次爆炸的 preBoost；无爆炸记录则 0）
        double preBoostVx = 0, preBoostVy = 0, preBoostVz = 0;
        if (!explosions.isEmpty()) {
            MatrixSolver.ExplosionRecord first = explosions.get(0);
            preBoostVx = first.preBoostVx();
            preBoostVy = first.preBoostVy();
            preBoostVz = first.preBoostVz();
        }

        switch (mode) {
            case REGULAR -> {
                launchVelocity = MatrixSolver.solve2D(
                    explosions, targetX, targetZ,
                    startX, startZ, ticks, airDragModifier);
                // solve2D 返回 boostedVx/Vz（含 preBoost + ΣΔv + v_extra），
                // 但 vy 未算（2D 模式）。这里用 InversionEngine 算 y 方向 boostedVy：
                //   boostedVy = (targetY - startY - gravitySum) / dragSum
                // 注意：此 y 求解隐含"y 方向无爆炸 Δv"假设。
                // 若 y 也有爆炸贡献（3D 矢量炮），应使用 VECTOR_3D 模式。
                launchVelocity[1] = InversionEngine.computeGravityCompensationVy(
                    targetY, startY, ticks, airDragModifier);
            }
            case WEAK_LOADING -> {
                // 应用弱加载 λ 校正
                int effectiveTicks = WeakLoadingCorrector.effectiveTicks(ticks, weakLoadingDelay);
                if (effectiveTicks <= 0) {
                    solvable = false;
                    launchVelocity = new double[]{0, 0, 0};
                } else {
                    List<MatrixSolver.ExplosionRecord> correctedExplosions =
                        WeakLoadingCorrector.applyCorrection(explosions,
                            new int[]{weakLoadingDelay},
                            new int[]{WeakLoadingCorrector.estimateUnloadedChunks(
                                startX, startZ, targetX, targetZ)});
                    launchVelocity = MatrixSolver.solve2D(
                        correctedExplosions, targetX, targetZ,
                        startX, startZ, effectiveTicks, airDragModifier);
                    launchVelocity[1] = InversionEngine.computeGravityCompensationVy(
                        targetY, startY, effectiveTicks, airDragModifier);
                }
            }
            case VECTOR_3D -> {
                if (explosions.size() < 3) {
                    solvable = false;
                    launchVelocity = new double[]{0, 0, 0};
                } else {
                    launchVelocity = MatrixSolver.solve3D(
                        explosions, targetX, targetY, targetZ,
                        startX, startY, startZ, ticks, airDragModifier);
                }
            }
            default -> {
                launchVelocity = new double[]{0, 0, 0};
                solvable = false;
            }
        }

        // 模拟预测轨迹（v1.0.2 修正：用 simulateTrajectoryWithExplosions
        // 消费 preBoost + 爆炸 Δv 序列，而非把 launchVelocity 当作单一初速度）
        // v1.0.4: 传入 targetY 作为 landingY，做 y 方向落地截断，
        //         解决珍珠最后 1 tick 撞地后 y 继续下落的 0.274 格偏差
        double[][] trajectory = MotionEngine.simulateTrajectoryWithExplosions(
            preBoostVx, preBoostVy, preBoostVz,
            explosions,
            startX, startY, startZ,
            ticks, airDragModifier,
            targetY);

        // 计算精度误差
        double error = computeAccuracyError(trajectory, ticks, targetX, targetY, targetZ);

        // 整数解优化
        var intSol = MatrixSolver.findBestIntegerSolution(
            launchVelocity, startX, startY, startZ,
            targetX, targetY, targetZ, ticks, airDragModifier,
            Constants.INTEGER_SEARCH_RANGE);

        // 估算 TNT 数量（简化：每次爆炸 = 1 TNT）
        int tntCount = Math.max(explosions.size(),
            mode == CannonMode.VECTOR_3D ? 3 : explosions.size());

        return new CalculationResult(
            mode,
            launchVelocity[0], launchVelocity[1], launchVelocity[2],
            targetX, targetY, targetZ,
            startX, startY, startZ,
            ticks, airDragModifier,
            trajectory, error, solvable,
            intSol.velocity(), intSol.error(),
            tntCount);
    }

    /**
     * 快捷方法：常规炮计算
     */
    public static CalculationResult calculateRegular(
            List<MatrixSolver.ExplosionRecord> explosions,
            double targetX, double targetZ,
            double startX, double startZ,
            int ticks, double airDragModifier) {
        return calculate(CannonMode.REGULAR, explosions,
            targetX, 0, targetZ, startX, 0, startZ,
            ticks, airDragModifier, 0);
    }

    /**
     * 快捷方法：弱加载炮计算
     */
    public static CalculationResult calculateWeakLoading(
            List<MatrixSolver.ExplosionRecord> explosions,
            double targetX, double targetZ,
            double startX, double startZ,
            int ticks, double airDragModifier,
            int weakLoadingDelay) {
        return calculate(CannonMode.WEAK_LOADING, explosions,
            targetX, 0, targetZ, startX, 0, startZ,
            ticks, airDragModifier, weakLoadingDelay);
    }

    /**
     * 快捷方法：三维矢量炮计算
     */
    public static CalculationResult calculate3D(
            List<MatrixSolver.ExplosionRecord> explosions,
            double targetX, double targetY, double targetZ,
            double startX, double startY, double startZ,
            int ticks, double airDragModifier) {
        return calculate(CannonMode.VECTOR_3D, explosions,
            targetX, targetY, targetZ, startX, startY, startZ,
            ticks, airDragModifier, 0);
    }

    /**
     * 计算预测落点与目标之间的误差
     */
    private static double computeAccuracyError(
            double[][] trajectory, int ticks,
            double targetX, double targetY, double targetZ) {

        int idx = Math.min(ticks, trajectory.length - 1);
        double dx = trajectory[idx][0] - targetX;
        double dy = trajectory[idx][1] - targetY;
        double dz = trajectory[idx][2] - targetZ;
        return Math.sqrt(dx*dx + dy*dy + dz*dz);
    }
}
