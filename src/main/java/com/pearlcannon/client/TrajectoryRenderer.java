package com.pearlcannon.client;

import com.pearlcannon.common.CannonCalculator;
import net.minecraft.client.Minecraft;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 轨迹预览渲染器
 * 使用粒子系统在游戏世界中渲染珍珠飞行轨迹。
 */
public final class TrajectoryRenderer {

    private static final AtomicReference<double[][]> pendingTrajectory = new AtomicReference<>();
    private static final AtomicReference<CannonCalculator.CalculationResult> pendingResult = new AtomicReference<>();

    private static double[][] currentTrajectory = null;
    private static CannonCalculator.CalculationResult currentResult = null;
    private static boolean rendering = false;
    private static int renderTicksRemaining = 0;

    public static void requestPreview(double[][] trajectory) {
        pendingTrajectory.set(trajectory);
        rendering = true;
        renderTicksRemaining = 300;
    }

    public static void requestPreview(CannonCalculator.CalculationResult result) {
        pendingResult.set(result);
        pendingTrajectory.set(result.predictedTrajectory());
        rendering = true;
        renderTicksRemaining = 300;
    }

    public static void stopRendering() {
        rendering = false;
        currentTrajectory = null;
        currentResult = null;
    }

    public static void tick() {
        if (!rendering) return;

        double[][] newTraj = pendingTrajectory.getAndSet(null);
        if (newTraj != null) currentTrajectory = newTraj;

        CannonCalculator.CalculationResult newResult = pendingResult.getAndSet(null);
        if (newResult != null) currentResult = newResult;

        renderTicksRemaining--;
        if (renderTicksRemaining <= 0) {
            stopRendering();
        }

        if (currentTrajectory != null && Minecraft.getInstance().level != null) {
            renderTrajectoryParticles();
        }
    }

    private static void renderTrajectoryParticles() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || currentTrajectory == null) return;

        int step = 5;
        for (int i = 0; i < currentTrajectory.length; i += step) {
            client.level.addParticle(
                net.minecraft.core.particles.ParticleTypes.END_ROD,
                currentTrajectory[i][0], currentTrajectory[i][1], currentTrajectory[i][2],
                0, 0.01, 0);
        }

        if (currentTrajectory.length > 0) {
            int lastIdx = currentTrajectory.length - 1;
            for (int j = 0; j < 3; j++) {
                client.level.addParticle(
                    net.minecraft.core.particles.ParticleTypes.CRIT,
                    currentTrajectory[lastIdx][0], currentTrajectory[lastIdx][1] + j * 0.1, currentTrajectory[lastIdx][2],
                    0, 0, 0);
            }
        }

        if (currentResult != null) {
            for (int j = 0; j < 5; j++) {
                client.level.addParticle(
                    net.minecraft.core.particles.ParticleTypes.NOTE,
                    currentResult.targetX(), currentResult.targetY() + j * 0.2, currentResult.targetZ(),
                    0.8, 0, 0);
            }
        }
    }

    public static boolean isRendering() { return rendering; }
    public static double[][] getCurrentTrajectory() { return currentTrajectory; }
    public static CannonCalculator.CalculationResult getCurrentResult() { return currentResult; }
}
