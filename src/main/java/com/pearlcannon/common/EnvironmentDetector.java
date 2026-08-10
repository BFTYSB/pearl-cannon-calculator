package com.pearlcannon.common;

/**
 * 单/多人游戏环境检测器
 * 
 * 检测当前运行环境：
 * - SINGLEPLAYER: 单人游戏（IntegratedServer），客户端直接计算，零网络开销
 * - MULTIPLAYER: 多人游戏（远程服务器），需要发送网络包到服务端
 * - UNKNOWN: 无法确定的环境
 * 
 * 使用方式：
 *   GameEnvironment env = EnvironmentDetector.detect(client);
 *   if (env == SINGLEPLAYER) { 直接计算 } else { 发送网络包 }
 */
public final class EnvironmentDetector {

    private EnvironmentDetector() {}

    /**
     * 游戏环境枚举
     */
    public enum GameEnvironment {
        /** 单人游戏：客户端直接调用 common 引擎，不走网络 */
        SINGLEPLAYER,
        /** 多人游戏：发送请求包到服务端，等待返回 */
        MULTIPLAYER,
        /** 未知环境 */
        UNKNOWN
    }

    /**
     * 检测当前运行环境
     * 
     * 此方法需要在客户端上下文中调用。
     * 通过检查 MinecraftClient.getInstance().getServer() 是否非 null 来判断。
     * 
     * 注意：此方法本身不引用 Minecraft 类，
     * 实际检测由 Minecraft 特定的适配层完成。
     * 
     * @param hasIntegratedServer 是否存在集成服务器（单人模式标志）
     * @param isConnectedToRemote 是否连接到远程服务器
     * @return 检测到的游戏环境
     */
    public static GameEnvironment detect(boolean hasIntegratedServer, boolean isConnectedToRemote) {
        if (hasIntegratedServer) {
            return GameEnvironment.SINGLEPLAYER;
        }
        if (isConnectedToRemote) {
            return GameEnvironment.MULTIPLAYER;
        }
        return GameEnvironment.UNKNOWN;
    }

    /**
     * 检查是否可以在客户端本地执行计算
     * 
     * 单人模式或降级模式下允许本地计算。
     */
    public static boolean canCalculateLocally(GameEnvironment env) {
        return env == GameEnvironment.SINGLEPLAYER || env == GameEnvironment.UNKNOWN;
    }

    /**
     * 检查是否需要通过网络发送计算请求
     */
    public static boolean requiresNetwork(GameEnvironment env) {
        return env == GameEnvironment.MULTIPLAYER;
    }
}
