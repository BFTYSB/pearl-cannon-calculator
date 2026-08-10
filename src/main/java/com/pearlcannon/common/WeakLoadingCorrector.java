package com.pearlcannon.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 弱加载炮校正器（λ 加载衰减系数）
 * 
 * 弱加载炮的特殊性：
 * - 珍珠飞越未加载 chunk 边界时，该区域内的实体/tick 不被处理
 * - 导致爆炸助推效果被"延迟"，等效于速度增量乘以一个衰减因子 λ
 * 
 * λ 的取值取决于：
 * - chunk 加载延迟 tick 数（通常 1-3 ticks）
 * - 珍珠经过未加载区域时的速度
 * - 未加载区域的长度
 * 
 * 公式：
 *   effective_Δv = raw_Δv × λ(delayTicks, distanceInUnloaded)
 *   λ = e^(-α × delayTicks) × (1 - β × unloadedChunks)
 * 
 * 其中 α 和 β 是经验校准参数。
 */
public final class WeakLoadingCorrector {

    private WeakLoadingCorrector() {}

    // ==================== 经验校准参数 ====================
    
    /** 时间衰减系数 α（越大表示延迟影响越强） */
    public static final double ALPHA = 0.15;
    
    /** 空间衰减系数 β（越大表示未加载区域影响越强） */
    public static final double BETA = 0.08;
    
    /** 单个 chunk 的 block 数量 */
    public static final int CHUNK_SIZE_BLOCKS = 16;

    /**
     * 计算弱加载衰减因子 λ
     * 
     * @param delayTicks chunk 加载延迟 tick 数
     * @param unloadedChunks 经过未加载 chunk 的数量
     * @return 衰减因子 λ ∈ [0, 1]
     */
    public static double computeLambda(int delayTicks, int unloadedChunks) {
        double timeDecay = Math.exp(-ALPHA * delayTicks);
        double spaceDecay = 1.0 - BETA * unloadedChunks;
        return Math.max(0, Math.min(1, timeDecay * spaceDecay));
    }

    /**
     * 应用弱加载校正到爆炸记录列表
     * 
     * 对每个爆炸记录的速度增量乘以对应的 λ 值。
     * 
     * @param originalRecords 原始爆炸记录
     * @param delayTicksArray 每次爆炸对应的延迟 tick 数组
     * @param unloadedChunksArray 每次爆炸对应的未加载 chunk 数组
     * @return 校正后的爆炸记录列表
     */
    public static List<MatrixSolver.ExplosionRecord> applyCorrection(
            List<MatrixSolver.ExplosionRecord> originalRecords,
            int[] delayTicksArray,
            int[] unloadedChunksArray) {

        List<MatrixSolver.ExplosionRecord> corrected = new ArrayList<>();

        for (int i = 0; i < originalRecords.size(); i++) {
            MatrixSolver.ExplosionRecord orig = originalRecords.get(i);
            int delay = (i < delayTicksArray.length) ? delayTicksArray[i] : 0;
            int chunks = (i < unloadedChunksArray.length) ? unloadedChunksArray[i] : 0;
            double lambda = computeLambda(delay, chunks);

            // 仅对 TNT 助推增量 Δv 施加 λ 衰减；
            // preBoost（助推前预存速度）是珍珠本身已有的速度，不受弱加载影响，原样保留
            corrected.add(new MatrixSolver.ExplosionRecord(
                orig.deltaVx() * lambda,
                orig.deltaVy() * lambda,
                orig.deltaVz() * lambda,
                orig.explosionX(), orig.explosionY(), orig.explosionZ(),
                orig.pearlX(), orig.pearlY(), orig.pearlZ(),
                orig.power(), orig.tick(),
                orig.preBoostVx(), orig.preBoostVy(), orig.preBoostVz()));
        }

        return corrected;
    }

    /**
     * 估算未加载 chunk 数量
     * 
     * 根据珍珠起点到爆炸点的直线距离估算穿越的 chunk 数量。
     * 
     * @param startX 起点 x
     * @param startZ 起点 z
     * @param explosionX 爆炸点 x
     * @param explosionZ 爆炸点 z
     * @return 估算的未加载 chunk 数量
     */
    public static int estimateUnloadedChunks(
            double startX, double startZ,
            double explosionX, double explosionZ) {

        double dx = Math.abs(explosionX - startX);
        double dz = Math.abs(explosionZ - startZ);
        double distance = Math.sqrt(dx * dx + dz * dz);
        return (int) Math.ceil(distance / CHUNK_SIZE_BLOCKS);
    }

    /**
     * 校正后的有效飞行 ticks
     * 
     * 弱加载模式下，由于 chunk 加载延迟，实际有效飞行时间减少。
     * 
     * @param nominalTicks 标称飞行 ticks
     * @param totalDelay 总延迟 ticks
     * @return 有效飞行 ticks
     */
    public static int effectiveTicks(int nominalTicks, int totalDelay) {
        return Math.max(1, nominalTicks - totalDelay);
    }
}
