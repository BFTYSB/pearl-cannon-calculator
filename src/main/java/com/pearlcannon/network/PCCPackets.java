package com.pearlcannon.network;

import net.minecraft.resources.Identifier;

/**
 * 珍珠炮计算器网络包 ID 枚举
 * 
 * 定义所有自定义网络包的标识符。
 * 使用 Fabric Networking API 的 FriendlyByteBuf 序列化。
 */
public final class PCCPackets {

    private PCCPackets() {}

    /** 包通道 ID */
    public static final String CHANNEL_ID = "pearl-cannon-calculator:main";

    /**
     * 包类型枚举
     */
    public enum PacketType {
        /** C→S：计算请求（目标坐标 + 模式 + tick） */
        CALCULATION_REQUEST((byte) 0x01),
        /** S→C：计算结果（TNT数量 + 误差 + 轨迹点） */
        CALCULATION_RESULT((byte) 0x02),
        /** S→C：爆炸向量配置同步 */
        EXPLOSION_VECTOR_SYNC((byte) 0x03),
        /** C→S：开始采集请求 */
        START_COLLECTING((byte) 0x04),
        /** C→S：停止采集请求 */
        STOP_COLLECTING((byte) 0x05);

        private final byte id;

        PacketType(byte id) { this.id = id; }

        public byte getId() { return id; }

        public static PacketType fromId(byte id) {
            for (PacketType type : values()) {
                if (type.id == id) return type;
            }
            return null;
        }
    }

    // ==================== 包注册常量 ====================

    public static final String CALCULATION_REQUEST_ID = "calculation_request";
    public static final String CALCULATION_RESULT_ID = "calculation_result";
    public static final String EXPLOSION_VECTOR_SYNC_ID = "explosion_vector_sync";

    // ==================== 辅助方法 ====================

    /**
     * 根据子路径创建 Identifier（使用固定命名空间）
     */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("pearlcannon", path);
    }
}
