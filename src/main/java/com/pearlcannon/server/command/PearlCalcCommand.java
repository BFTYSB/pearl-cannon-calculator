package com.pearlcannon.server.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.pearlcannon.common.CannonMode;
import com.pearlcannon.server.collector.ExplosionDataCollector;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/**
 * /pearlcalc 指令族 (Mojmap 版本)
 * 
 * 子命令：
 * - /pearlcalc status                  查看采集状态和数据量
 * - /pearlcalc mode <regular|weak|3d>  设置当前炮模式
 */
public class PearlCalcCommand {

    /**
     * 注册指令到 CommandDispatcher
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            LiteralArgumentBuilder.<CommandSourceStack>literal("pearlcalc")
            // === 状态查询 ===
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("status")
                .executes(ctx -> showStatus(ctx.getSource())))

            // === 模式切换 ===
            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("mode")
                .then(RequiredArgumentBuilder.<CommandSourceStack, String>argument("mode", StringArgumentType.word())
                    .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                        new String[]{"regular", "weak", "3d"}, builder))
                    .executes(ctx -> setMode(ctx.getSource(),
                        StringArgumentType.getString(ctx, "mode")))))
        );
    }

    private static int showStatus(CommandSourceStack source) {
        var collector = ExplosionDataCollector.getInstance();
        boolean collecting = collector.isCollecting();
        int count = collector.getRecords().size();

        source.sendSuccess(() -> Component.literal("--- Pearl Cannon Calculator Status ---")
            .withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.translatable(
            "command.pearlcalc.status.collecting",
            collecting ? "Yes" : "No"
        ).withStyle(collecting ? ChatFormatting.GREEN : ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable(
            "command.pearlcalc.status.records", count
        ).withStyle(ChatFormatting.WHITE), false);
        source.sendSuccess(() -> Component.translatable(
            "command.pearlcalc.status.mode",
            collector.getCurrentMode().getLocalizedName(true)
        ).withStyle(ChatFormatting.AQUA), false);

        return 1;
    }

    private static int setMode(CommandSourceStack source, String modeStr) {
        CannonMode mode = switch (modeStr.toLowerCase()) {
            case "regular" -> CannonMode.REGULAR;
            case "weak" -> CannonMode.WEAK_LOADING;
            case "3d", "vector" -> CannonMode.VECTOR_3D;
            default -> null;
        };

        if (mode == null) {
            source.sendFailure(Component.translatable(
                "command.pearlcalc.mode.invalid", modeStr
            ).withStyle(ChatFormatting.RED));
            return 0;
        }

        ExplosionDataCollector.getInstance().setCurrentMode(mode);
        source.sendSuccess(() -> Component.translatable(
            "command.pearlcalc.mode.set", mode.getLocalizedName(true)
        ).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
