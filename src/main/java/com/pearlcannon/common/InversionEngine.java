package com.pearlcannon.common;

/**
 * 位移反推初速度引擎（零 Minecraft 依赖）
 * 
 * 根据目标落点和飞行 tick 数反推所需发射速度。
 * 
 * 数学原理：
 *   x(t) = x0 + vx × Σ(d^k, k=1..t)
 *   其中 Σ(d^k) 是阻力累积系数
 * 
 * 反解公式：
 *   vx = (targetX - x0) / dragSum
 *   vy = (targetY - y0 - gravitySum) / dragSum
 *   vz = (targetZ - z0) / dragSum
 */
public final class InversionEngine {

    private InversionEngine() {}

    /**
     * 根据目标落点和飞行 tick 数反推发射速度
     * 
     * @param targetX/Y/Z 目标坐标
     * @param startX/Y/Z 起点坐标
     * @param ticks 飞行 tick 数
     * @param airDragModifier 空气阻力修正值
     * @return 所需发射速度 [vx, vy, vz]
     */
    public static double[] solveLaunchVelocity(
            double targetX, double targetY, double targetZ,
            double startX, double startY, double startZ,
            int ticks, double airDragModifier) {

        double effectiveDrag = Constants.effectiveDrag(airDragModifier);
        double dragSum = MotionEngine.computeDragSum(ticks, effectiveDrag);
        double gravitySum = MotionEngine.computeGravitySum(ticks, effectiveDrag);

        double vx = (targetX - startX) / dragSum;
        double vy = (targetY - startY - gravitySum) / dragSum;
        double vz = (targetZ - startZ) / dragSum;

        return new double[]{vx, vy, vz};
    }

    /**
     * 计算 y 方向重力补偿速度
     * 
     * 目标：珍珠在 ticks 后精确到达 targetY
     * 
     * @param targetY 目标 y 坐标
     * @param startY 起点 y 坐标
     * @param ticks 飞行 tick 数
     * @param airDragModifier 空气阻力修正值
     * @return 所需 vy 值
     */
    public static double computeGravityCompensationVy(
            double targetY, double startY, int ticks, double airDragModifier) {

        double effectiveDrag = Constants.effectiveDrag(airDragModifier);
        double gravityDisp = MotionEngine.computeGravitySum(ticks, effectiveDrag);
        double dragSum = MotionEngine.computeDragSum(ticks, effectiveDrag);
        return (targetY - startY - gravityDisp) / dragSum;
    }

    /**
     * 快速估算最小飞行 ticks
     * 
     * 给定距离和最大初速度，估算需要的最少飞行时间。
     * 
     * @param distance 平面距离 sqrt(dx² + dz²)
     * @param maxInitialSpeed 最大初始速度（通常由发射机制决定）
     * @param airDragModifier 空气阻力修正值
     * @return 估算的最小 ticks
     */
    public static int estimateMinTicks(double distance, double maxInitialSpeed, double airDragModifier) {
        double effectiveDrag = Constants.effectiveDrag(airDragModifier);
        
        // 近似：distance ≈ maxSpeed × dragSum(t)
        // 二分查找最小 t
        int low = 1, high = Constants.MAX_PEARL_TICKS;
        while (low < high) {
            int mid = (low + high) / 2;
            double dragSum = MotionEngine.computeDragSum(mid, effectiveDrag);
            double maxDistance = maxInitialSpeed * dragSum;
            
            if (maxDistance >= distance) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return low;
    }
}
