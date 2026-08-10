package com.pearlcannon.server.calculator;

import com.pearlcannon.common.CannonCalculator;
import com.pearlcannon.common.CannonMode;
import com.pearlcannon.common.DebugLog;
import com.pearlcannon.common.MatrixSolver;
import com.pearlcannon.network.CalculationRequestPacket;
import com.pearlcannon.network.CalculationResultPacket;
import com.pearlcannon.server.collector.ExplosionDataCollector;
import net.minecraft.server.MinecraftServer;

/**
 * 服务端计算处理器
 * 
 * 多人模式下，接收客户端 CalculationRequestPacket，
 * 调用 common 引擎计算，封装 CalculationResultPacket 返回。
 * 
 * 处理流程：
 * 1. 接收请求包（含模式、目标坐标、ticks、airDragModifier）
 * 2. 从 ExplosionDataCollector 获取爆炸数据
 * 3. 调用 CannonCalculator.calculate()
 * 4. 封装结果为 CalculationResultPacket 返回
 */
public final class ServerCalculationHandler {

    private ServerCalculationHandler() {}

    /**
     * 处理客户端计算请求
     * 
     * @param request 客户端发送的计算请求
     * @return 计算结果包（待发送回客户端）
     */
    public static CalculationResultPacket handleRequest(CalculationRequestPacket request) {
        try {
            // 从采集器获取爆炸数据
            var explosions = ExplosionDataCollector.getInstance().getRecords();

            // 如果没有采集数据，使用空列表（会返回不可解结果）
            DebugLog.data("服务端开始计算",
                "mode=" + request.mode()
                + " 爆炸记录数=" + explosions.size()
                + " ticks=" + request.ticks() + " drag=" + DebugLog.fmt(request.airDragModifier()));
            if (explosions.isEmpty()) {
                DebugLog.warn("计算返回不可解: 无爆炸采集数据");
                return createEmptyResult(request.mode());
            }

            // 调用 common 引擎计算
            var result = CannonCalculator.calculate(
                request.mode(), explosions,
                request.targetX(), request.targetY(), request.targetZ(),
                request.startX(), request.startY(), request.startZ(),
                request.ticks(), request.airDragModifier(),
                request.weakLoadingDelay());

            // 封装为网络包返回
            DebugLog.data("服务端计算完成",
                "solvable=" + result.solvable()
                + " 发射速度=(" + DebugLog.fmt(result.launchVx()) + "," + DebugLog.fmt(result.launchVy()) + "," + DebugLog.fmt(result.launchVz()) + ")"
                + " 误差=" + DebugLog.fmt(result.accuracyError())
                + " TNT=" + result.estimatedTNTCount());
            return new CalculationResultPacket(
                result.launchVx(), result.launchVy(), result.launchVz(),
                result.accuracyError(),
                result.solvable(),
                result.predictedTrajectory(),
                result.estimatedTNTCount());

        } catch (Exception e) {
            // 计算异常时返回错误结果
            DebugLog.error("服务端计算异常", e);
            return createErrorResult(e.getMessage());
        }
    }

    /**
     * 创建空数据结果（无爆炸记录时）
     */
    private static CalculationResultPacket createEmptyResult(CannonMode mode) {
        return new CalculationResultPacket(
            0, 0, 0,
            Double.MAX_VALUE,
            false,
            new double[0][],
            0);
    }

    /**
     * 创建错误结果
     */
    private static CalculationResultPacket createErrorResult(String errorMessage) {
        return new CalculationResultPacket(
            0, 0, 0,
            Double.MAX_VALUE,
            false,
            new double[0][],
            0);
    }

    /**
     * 同步爆炸向量到所有在线玩家
     * 
     * 在采集完成或玩家加入时调用。
     */
    public static void syncExplosionVectors(MinecraftServer server) {
        var records = ExplosionDataCollector.getInstance().getRecords();
        if (records.isEmpty()) return;

        // 提取最多 3 个向量用于同步
        double[] vecA = {0, 0, 0}, vecB = {0, 0, 0}, vecC = {0, 0, 0};
        for (int i = 0; i < Math.min(3, records.size()); i++) {
            var rec = records.get(i);
            switch (i) {
                case 0 -> vecA = new double[]{rec.deltaVx(), rec.deltaVy(), rec.deltaVz()};
                case 1 -> vecB = new double[]{rec.deltaVx(), rec.deltaVy(), rec.deltaVz()};
                case 2 -> vecC = new double[]{rec.deltaVx(), rec.deltaVy(), rec.deltaVz()};
            }
        }

        var syncPacket = new com.pearlcannon.network.ExplosionVectorSyncPacket(
            vecA, vecB, vecC, records.size());
        com.pearlcannon.network.PCCNetworkHandler.syncExplosionVectors(server, syncPacket);
    }
}
