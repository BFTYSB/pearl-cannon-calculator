package com.pearlcannon.client.gui;

import com.pearlcannon.common.CannonCalculator;
import com.pearlcannon.common.Constants;
import com.pearlcannon.common.EnvironmentDetector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class ResultDisplayWidget {

    private CannonCalculator.CalculationResult result;
    private EnvironmentDetector.GameEnvironment environment;
    private boolean waitingForServer = false;
    private final int x, y;

    public ResultDisplayWidget(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void render(GuiGraphicsExtractor guiGraphics, Font font) {
        // 26.2：text(Font, String, int, int, int) 5 参形式（无 shadow 参数）才会被收集渲染；
        // 旧 6 参 String 形式（text(Font, String, x, y, color, shadow)）在 extractRenderState 中不再输出。
        if (waitingForServer) {
            guiGraphics.text(font,
                Component.translatable("display.pearlcalc.waiting_server").getString(),
                x, y, 0xFFFF00);
            return;
        }
        if (result == null) {
            guiGraphics.text(font,
                Component.translatable("display.pearlcalc.no_result").getString(),
                x, y, 0x888888);
            return;
        }

        int lineY = y;
        int lh = 14;
        guiGraphics.text(font,
            Component.translatable("label.pearl_cannon_calculator.result").getString(),
            x, lineY, 0xFFD700); lineY += lh;

        guiGraphics.text(font,
            "Vx: " + String.format("%.4f", result.launchVx())
            + "  Vy: " + String.format("%.4f", result.launchVy())
            + "  Vz: " + String.format("%.4f", result.launchVz()),
            x, lineY, 0x00FF00); lineY += lh;

        int errColor = result.accuracyError() < Constants.ACCEPTABLE_ERROR ? 0x00FF00 : 0xFF0000;
        guiGraphics.text(font,
            Component.translatable("label.pearl_cannon_calculator.error").getString()
            + String.format("%.4f", result.accuracyError()) + " blocks",
            x, lineY, errColor); lineY += lh;

        if (!result.solvable()) {
            guiGraphics.text(font,
                Component.translatable("label.pearl_cannon_calculator.unsolvable").getString(),
                x, lineY, 0xFF0000); lineY += lh;
        }
        if (result.integerSolution() != null) {
            int[] is = result.integerSolution();
            guiGraphics.text(font,
                "Int: [" + is[0] + ", " + is[1] + ", " + is[2] + "] err="
                + String.format("%.4f", result.integerError()),
                x, lineY, 0x00CCFF); lineY += lh;
        }
        guiGraphics.text(font, "TNT: " + result.estimatedTNTCount(),
            x, lineY, 0xFF9900); lineY += lh;

        String envStr = switch (environment) {
            case SINGLEPLAYER -> "[SP] Local";
            case MULTIPLAYER -> "[MP] Server";
            case UNKNOWN -> "[??] Unknown";
        };
        guiGraphics.text(font, envStr, x, lineY, 0x666666);
    }

    public void setResult(CannonCalculator.CalculationResult r) { this.result = r; }
    public void setEnvironment(EnvironmentDetector.GameEnvironment env) { this.environment = env; }
    public void setWaiting(boolean waiting) { this.waitingForServer = waiting; }
    public CannonCalculator.CalculationResult getResult() { return result; }
}
