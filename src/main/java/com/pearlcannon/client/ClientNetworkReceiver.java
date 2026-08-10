package com.pearlcannon.client;

import com.pearlcannon.network.CalculationResultPacket;
import com.pearlcannon.network.ExplosionVectorSyncPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 客户端网络包接收器
 * 
 * 在 client 源集中注册 S→C 包的接收逻辑，
 * 避免 main 源集引用 client 类。
 */
public final class ClientNetworkReceiver {

    private ClientNetworkReceiver() {}

    /**
     * 注册客户端接收器（由 PearlCannonClient 调用）
     */
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(CalculationResultPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                CannonCalculatorScreen.onReceiveResult(payload);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(ExplosionVectorSyncPacket.ID, (payload, context) -> {
            context.client().execute(() -> {
                CannonCalculatorScreen.onExplosionVectorsSynced(payload);
            });
        });
    }
}
