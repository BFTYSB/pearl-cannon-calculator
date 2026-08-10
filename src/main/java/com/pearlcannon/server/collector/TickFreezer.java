package com.pearlcannon.server.collector;

import net.minecraft.server.MinecraftServer;

/**
 * Tick 冻结器封装 - 纯反射实现
 * 
 * Minecraft 26.2 服务端 tick 管理器 API 可能因版本变化，
 * 使用反射避免编译时方法名依赖。
 */
public final class TickFreezer {

    private TickFreezer() {}

    /**
     * 冻结服务端 tick
     */
    public static void freeze(MinecraftServer server) {
        invokeTickManager(server, "setFrozen", true);
    }

    /**
     * 解冻服务端 tick
     */
    public static void unfreeze(MinecraftServer server) {
        invokeTickManager(server, "setFrozen", false);
    }

    /**
     * 步进指定数量的 tick
     */
    public static void step(MinecraftServer server, int ticks) {
        invokeTickManager(server, "setStepTicks", ticks, int.class);
    }

    /**
     * 检查是否处于冻结状态
     */
    public static boolean isFrozen(MinecraftServer server) {
        if (server == null) return false;
        try {
            Object tm = getTickManager(server);
            if (tm == null) return false;
            return (Boolean) tm.getClass().getMethod("isFrozen").invoke(tm);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 通过反射获取 tick 管理器
     */
    private static Object getTickManager(MinecraftServer server) throws Exception {
        // 尝试多种可能的方法名
        for (String methodName : new String[]{"getTickManager", "tickManager", "getTickManager"}) {
            try {
                return server.getClass().getMethod(methodName).invoke(server);
            } catch (NoSuchMethodException ignored) {}
        }
        // 尝试字段访问
        try {
            var field = server.getClass().getDeclaredField("tickManager");
            field.setAccessible(true);
            return field.get(server);
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 通过反射调用 tick 管理器方法
     */
    private static void invokeTickManager(MinecraftServer server, String methodName, Object value, Class<?> paramType) {
        if (server == null) return;
        try {
            Object tm = getTickManager(server);
            if (tm == null) return;
            tm.getClass().getMethod(methodName, paramType).invoke(tm, value);
        } catch (Exception e) {
            System.err.println("[PearlCannon] Failed to invoke " + methodName + ": " + e.getMessage());
        }
    }

    private static void invokeTickManager(MinecraftServer server, String methodName, boolean value) {
        invokeTickManager(server, methodName, value, boolean.class);
    }

    private static void invokeTickManager(MinecraftServer server, String methodName, int value) {
        invokeTickManager(server, methodName, value, int.class);
    }
}
