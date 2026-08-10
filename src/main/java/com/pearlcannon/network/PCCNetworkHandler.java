package com.pearlcannon.network;

import com.pearlcannon.common.DebugLog;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 网络包处理器 - 服务端注册与发送工具
 * 
 * 注意：客户端接收逻辑在 client 源集的 ClientNetworkReceiver 中，
 * 因为 splitEnvironmentSourceSets 模式下 main 不能引用 client 类。
 */
public final class PCCNetworkHandler {

    private static boolean initialized = false;

    private PCCNetworkHandler() {}

    /**
     * 初始化网络通道（在模组主入口调用）
     * 仅注册服务端接收器，客户端接收器由 PearlCannonClient 注册
     */
    public static void initialize() {
        // 幂等保护：main / client / server 入口都会调用本方法，只需初始化一次
        if (initialized) return;
        initialized = true;
        // 必须先注册 payload 类型，才能注册接收器（Fabric 1.20.5+ 新网络 API）
        registerPayloadTypes();
        registerServerReceivers();
        DebugLog.info("网络包系统已初始化（payload 类型 + 服务端接收器）");
    }

    /**
     * 注册所有自定义网络包的 payload 类型（C2S / S2C）
     * 必须在 registerGlobalReceiver / send 之前调用，客户端与服务端都会执行
     */
    private static void registerPayloadTypes() {
        // C2S：客户端 → 服务端
        PayloadTypeRegistry.serverboundPlay().register(CalculationRequestPacket.ID, CalculationRequestPacket.CODEC);
        // S2C：服务端 → 客户端
        PayloadTypeRegistry.clientboundPlay().register(CalculationResultPacket.ID, CalculationResultPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ExplosionVectorSyncPacket.ID, ExplosionVectorSyncPacket.CODEC);
    }

    /**
     * 注册服务端接收器（处理 C→S 包）
     */
    private static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(CalculationRequestPacket.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            MinecraftServer server = player.level().getServer();

            server.execute(() -> {
                DebugLog.data("服务端收到计算请求(C→S)",
                    "玩家=" + player.getScoreboardName()
                    + " mode=" + payload.mode()
                    + " 目标=(" + DebugLog.fmt(payload.targetX()) + "," + DebugLog.fmt(payload.targetY()) + "," + DebugLog.fmt(payload.targetZ()) + ")");
                var result = com.pearlcannon.server.calculator.ServerCalculationHandler.handleRequest(payload);
                ServerPlayNetworking.send(player, result);
                DebugLog.data("服务端发送结果(S→C)",
                    "solvable=" + result.solvable()
                    + " 误差=" + DebugLog.fmt(result.error())
                    + " TNT=" + result.tntCount());
            });
        });
    }

    /**
     * 服务端发送结果到指定玩家
     */
    public static void sendCalculationResult(ServerPlayer player, CalculationResultPacket result) {
        ServerPlayNetworking.send(player, result);
    }

    /**
     * 服务端同步爆炸向量到所有玩家
     */
    public static void syncExplosionVectors(MinecraftServer server, ExplosionVectorSyncPacket payload) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }
}
