package com.pearlcannon.common;

/**
 * 珍珠炮模式枚举
 * 
 * 三种炮模式对应不同的计算策略：
 * 1. REGULAR - 常规炮：顺序爆炸助推，2D伪逆求解
 * 2. WEAK_LOADING - 弱加载炮：延迟爆炸助推，含 λ 加载衰减系数
 * 3. VECTOR_3D - 三维矢量炮：3D直接求逆求解
 * 
 * 零 Minecraft 依赖，纯数据定义。
 */
public enum CannonMode {

    /**
     * 常规珍珠炮
     * - 顺序爆炸助推（t1, t2, ...）
     * - 爆炸在同一 tick 按序施加速度增量
     * - 2D 模式（x-z 平面），y 由重力补偿
     */
    REGULAR("常规炮", "Regular Cannon"),

    /**
     * 弱加载珍珠炮
     * - 爆炸助推有延迟（跨越 chunk 加载边界）
     * - 需要考虑 chunk 加载 tick 偏移和 λ 加载衰减系数
     * - 2D 模式，但时间参数有偏移
     */
    WEAK_LOADING("弱加载炮", "Weak-Loading Cannon"),

    /**
     * 三维矢量炮
     * - 三方向独立爆炸助推
     * - 3×3 矩阵直接求逆
     * - 完整 x-y-z 三分量求解
     */
    VECTOR_3D("三维矢量炮", "3D Vector Cannon");

    private final String chineseName;
    private final String englishName;

    CannonMode(String chineseName, String englishName) {
        this.chineseName = chineseName;
        this.englishName = englishName;
    }

    public String getChineseName() { return chineseName; }
    public String getEnglishName() { return englishName; }

    /**
     * 根据语言键获取本地化名称
     */
    public String getLocalizedName(boolean isChinese) {
        return isChinese ? chineseName : englishName;
    }

    /**
     * 序列化为网络传输用的字节码
     */
    public byte toByte() {
        return (byte) ordinal();
    }

    /**
     * 从字节码反序列化
     */
    public static CannonMode fromByte(byte b) {
        int idx = b & 0xFF;
        if (idx >= values().length) return REGULAR;
        return values()[idx];
    }
}
