package com.pearlcannon.network;

import com.pearlcannon.common.CannonMode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

/**
 * 计算请求包 (Client → Server)
 * 
 * 玩家点击 GUI 的"计算"按钮时发送。
 * Payload: mode, targetX/Y/Z, startX/Y/Z, ticks, airDragModifier, weakLoadingDelay
 */
public record CalculationRequestPacket(
        CannonMode mode,
        double targetX, double targetY, double targetZ,
        double startX, double startY, double startZ,
        int ticks,
        double airDragModifier,
        int weakLoadingDelay
) implements CustomPacketPayload {

    /**
     * Payload ID
     */
    public static final Type<CalculationRequestPacket> ID =
            new Type<>(PCCPackets.id(PCCPackets.CALCULATION_REQUEST_ID));

    /**
     * 编解码器
     */
    public static final StreamCodec<FriendlyByteBuf, CalculationRequestPacket> CODEC =
            new StreamCodec<>() {
                @Override
                public @NotNull CalculationRequestPacket decode(@NotNull FriendlyByteBuf buf) {
                    CannonMode mode = CannonMode.fromByte(buf.readByte());
                    double tx = buf.readDouble(), ty = buf.readDouble(), tz = buf.readDouble();
                    double sx = buf.readDouble(), sy = buf.readDouble(), sz = buf.readDouble();
                    int ticks = buf.readInt();
                    double drag = buf.readDouble();
                    int weakDelay = buf.readInt();
                    return new CalculationRequestPacket(mode, tx, ty, tz, sx, sy, sz, ticks, drag, weakDelay);
                }

                @Override
                public void encode(@NotNull FriendlyByteBuf buf, @NotNull CalculationRequestPacket packet) {
                    buf.writeByte(packet.mode().toByte());
                    buf.writeDouble(packet.targetX()); buf.writeDouble(packet.targetY()); buf.writeDouble(packet.targetZ());
                    buf.writeDouble(packet.startX()); buf.writeDouble(packet.startY()); buf.writeDouble(packet.startZ());
                    buf.writeInt(packet.ticks());
                    buf.writeDouble(packet.airDragModifier());
                    buf.writeInt(packet.weakLoadingDelay());
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() { return ID; }
}
