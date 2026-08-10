package com.pearlcannon;

import com.pearlcannon.common.Constants;
import com.pearlcannon.common.DebugLog;
import com.pearlcannon.network.PCCNetworkHandler;
import com.pearlcannon.server.collector.ExplosionDataCollector;
import com.pearlcannon.server.command.PearlCalcCommand;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * 模组主入口 - Pearl Cannon Calculator v2.0
 * 
 * 职责：
 * - 注册网络包通道
 * - 初始化全局状态
 * - 检测运行环境（CLIENT/SERVER/BOTH）
 * - 作为 common 层的统一初始化点
 */
public class PearlCannonCalculator implements ModInitializer {

    @Override
    public void onInitialize() {
        System.out.println("[PearlCannon] Initializing v" + Constants.MOD_VERSION 
            + " for Minecraft " + Constants.MC_VERSION);

        // 初始化调试日志（记录玩家操作与 mod 数据）
        DebugLog.init(null);
        DebugLog.sessionStart("Mod Initializer (v" + Constants.MOD_VERSION + " for " + Constants.MC_VERSION + ")");
        DebugLog.info("初始化网络包系统...");

        // 初始化网络包系统
        PCCNetworkHandler.initialize();

        // === 服务端功能注册在 main entrypoint ===
        // 注意：不能放在 "server" entrypoint，否则单机（integrated server）模式下不会执行，
        // 导致 tick 采集、指令等全部失效。
        // 注册 /pearlcalc 指令
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            PearlCalcCommand.register(dispatcher);
        });
        DebugLog.info("已注册 /pearlcalc 指令");

        // 服务端 tick：驱动爆炸数据采集器（单机集成服与独立服都会触发）
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ExplosionDataCollector.getInstance().tick(server);
        });
        DebugLog.info("已注册服务端 tick（爆炸采集）");

        // 服务器生命周期
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            System.out.println("[PearlCannon] Server started - Calculator ready");
            DebugLog.info("服务器已启动");
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ExplosionDataCollector.getInstance().stopCollecting();
            ExplosionDataCollector.getInstance().clearRecords();
            DebugLog.info("服务器停止，已清理采集数据");
        });

        System.out.println("[PearlCannon] Ready! Press O to open calculator.");
        DebugLog.info("主入口初始化完成，按 O 打开计算器。");
    }
}
