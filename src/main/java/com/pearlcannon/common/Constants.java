package com.pearlcannon.common;

/**
 * 物理常数与版本检测工具
 * 
 * Minecraft 26.2 (Chaos Cubed) 特化常量。
 * 此类零 Minecraft 依赖，仅包含纯数学常量和工具方法。
 */
public final class Constants {

    private Constants() {}

    // ==================== 版本信息 ====================
    public static final String MC_VERSION = "26.2";
    public static final String MOD_VERSION = "1.0.0";
    public static final String MOD_ID = "pearl-cannon-calculator";

    // ==================== 物理常数（26.2） ====================
    /**
     * 基础空气阻力值（每 tick 速度衰减系数）。
     * 精确值 0.9900000095367432 来自 Minecraft 26.2 的 float 属性默认值
     * （BASE_DRAG=0.99f 在 Java 中 double 化的精确表示），见技术备忘录 3.3。
     */
    public static final double BASE_DRAG = 0.9900000095367432;
    /** 重力加速度 (blocks/tick²) */
    public static final double GRAVITY = -0.03;
    /** 单 tick 时间步长（秒） */
    public static final double TICK_DURATION = 1.0 / 20.0;
    /** 末影珍珠最大飞行 tick 数 */
    public static final int MAX_PEARL_TICKS = 200;

    // ==================== air_drag_modifier 范围 ====================
    /** 默认空气阻力修正值（无属性时） */
    public static final double DEFAULT_AIR_DRAG_MODIFIER = 1.0;
    /** 最小有效值 */
    public static final double AIR_DRAG_MIN = 0.0;
    /** 最大有效值（Minecraft 26.2 属性上限） */
    public static final double AIR_DRAG_MAX = 2048.0;

    // ==================== 爆炸参数 ====================
    /** TNT 默认爆炸威力 */
    public static final float DEFAULT_TNT_POWER = 4.0f;
    /** 爆炸影响半径倍数 */
    public static final double EXPLOSION_RADIUS_MULTIPLIER = 2.0;

    // ==================== 计算精度 ====================
    /** SVD 伪逆截断阈值 */
    public static final double SVD_THRESHOLD = 1e-10;
    /** 可接受误差阈值（blocks） */
    public static final double ACCEPTABLE_ERROR = 0.5;
    /** 整数解遍历范围（±N） */
    public static final int INTEGER_SEARCH_RANGE = 5;

    // ==================== 工具方法 ====================

    /**
     * 计算有效阻力：base_drag × modifier
     */
    public static double effectiveDrag(double airDragModifier) {
        return BASE_DRAG * clamp(airDragModifier, AIR_DRAG_MIN, AIR_DRAG_MAX);
    }

    /**
     * 将值限制在 [min, max] 范围内
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 验证 air_drag_modifier 是否在有效范围内
     */
    public static boolean isValidAirDragModifier(double value) {
        return value >= AIR_DRAG_MIN && value <= AIR_DRAG_MAX;
    }
}
