package com.pearlcannon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

/**
 * 爆炸向量同步包 (Server → Client)
 * 
 * 当玩家加入服务器或采集完成时，服务端将爆炸向量配置同步到客户端。
 * Payload: vectorA/B/C 的 x/y/z (double[9])
 */
public record ExplosionVectorSyncPacket(
        double[] vectorA, // [ax, ay, az]
        double[] vectorB, // [bx, by, bz]
        double[] vectorC, // [cx, cy, cz]
        int explosionCount
) implements CustomPacketPayload {

    public static final Type<ExplosionVectorSyncPacket> ID =
            new Type<>(PCCPackets.id(PCCPackets.EXPLOSION_VECTOR_SYNC_ID));

    public static final StreamCodec<FriendlyByteBuf, ExplosionVectorSyncPacket> CODEC =
            new StreamCodec<>() {
                @Override
                public @NotNull ExplosionVectorSyncPacket decode(@NotNull FriendlyByteBuf buf) {
                    double[] vecA = new double[3], vecB = new double[3], vecC = new double[3];
                    for (int i = 0; i < 3; i++) vecA[i] = buf.readDouble();
                    for (int i = 0; i < 3; i++) vecB[i] = buf.readDouble();
                    for (int i = 0; i < 3; i++) vecC[i] = buf.readDouble();
                    int count = buf.readInt();
                    return new ExplosionVectorSyncPacket(vecA, vecB, vecC, count);
                }

                @Override
                public void encode(@NotNull FriendlyByteBuf buf, @NotNull ExplosionVectorSyncPacket pkt) {
                    for (double v : pkt.vectorA()) buf.writeDouble(v);
                    for (double v : pkt.vectorB()) buf.writeDouble(v);
                    for (double v : pkt.vectorC()) buf.writeDouble(v);
                    buf.writeInt(pkt.explosionCount());
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return ID; }
}
