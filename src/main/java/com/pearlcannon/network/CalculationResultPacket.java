package com.pearlcannon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

/**
 * 计算结果包 (Server → Client)
 * 
 * 服务端计算完成后发送回客户端。
 * Payload: launchVx/Vy/Vz, error, solvable, trajectoryPoints[], tntCount
 */
public record CalculationResultPacket(
        double launchVx, double launchVy, double launchVz,
        double error,
        boolean solvable,
        double[][] trajectory,
        int tntCount
) implements CustomPacketPayload {

    public static final Type<CalculationResultPacket> ID =
            new Type<>(PCCPackets.id(PCCPackets.CALCULATION_RESULT_ID));

    public static final StreamCodec<FriendlyByteBuf, CalculationResultPacket> CODEC =
            new StreamCodec<>() {
                @Override
                public @NotNull CalculationResultPacket decode(@NotNull FriendlyByteBuf buf) {
                    double vx = buf.readDouble(), vy = buf.readDouble(), vz = buf.readDouble();
                    double err = buf.readDouble();
                    boolean solvable = buf.readBoolean();
                    
                    // 轨迹点数组
                    int trajLen = buf.readInt();
                    double[][] trajectory = new double[trajLen][3];
                    for (int i = 0; i < trajLen; i++) {
                        trajectory[i][0] = buf.readDouble();
                        trajectory[i][1] = buf.readDouble();
                        trajectory[i][2] = buf.readDouble();
                    }
                    
                    int tntCount = buf.readInt();
                    return new CalculationResultPacket(vx, vy, vz, err, solvable, trajectory, tntCount);
                }

                @Override
                public void encode(@NotNull FriendlyByteBuf buf, @NotNull CalculationResultPacket pkt) {
                    buf.writeDouble(pkt.launchVx()); buf.writeDouble(pkt.launchVy()); buf.writeDouble(pkt.launchVz());
                    buf.writeDouble(pkt.error());
                    buf.writeBoolean(pkt.solvable());
                    
                    // 轨迹点数组
                    buf.writeInt(pkt.trajectory().length);
                    for (double[] point : pkt.trajectory()) {
                        buf.writeDouble(point[0]); buf.writeDouble(point[1]); buf.writeDouble(point[2]);
                    }
                    
                    buf.writeInt(pkt.tntCount());
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return ID; }
}
