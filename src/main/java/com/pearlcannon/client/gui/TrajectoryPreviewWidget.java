package com.pearlcannon.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class TrajectoryPreviewWidget {

    private double[][] trajectory;
    private final int x, y, width, height;
    private boolean worldPreviewActive = false;

    public TrajectoryPreviewWidget(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.width = w; this.height = h;
    }

    public void render(GuiGraphicsExtractor guiGraphics, Font font) {
        guiGraphics.fill(x, y, x + width, y + height, 0x80000000);
        guiGraphics.outline(x, y, width, height, 0xFF444444);
        guiGraphics.text(font,
            Component.translatable("label.pearlcalc.trajectory_preview").getString(),
            x + 5, y + 3, 0xFFFFFF);

        if (trajectory == null || trajectory.length == 0) {
            guiGraphics.text(font,
                Component.translatable("display.pearlcalc.no_trajectory").getString(),
                x + 10, y + height / 2, 0x666666);
            return;
        }

        double minX = Double.MAX_VALUE, maxX = Double.MIN_VALUE;
        double minZ = Double.MAX_VALUE, maxZ = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE, maxY = Double.MIN_VALUE;
        for (double[] pt : trajectory) {
            minX = Math.min(minX, pt[0]); maxX = Math.max(maxX, pt[0]);
            minZ = Math.min(minZ, pt[2]); maxZ = Math.max(maxZ, pt[2]);
            minY = Math.min(minY, pt[1]); maxY = Math.max(maxY, pt[1]);
        }
        double rangeX = Math.max(maxX - minX, 1);
        double rangeZ = Math.max(maxZ - minZ, 1);
        double rangeY = Math.max(maxY - minY, 1);

        int margin = 15;
        int plotW = width - margin * 2;
        int plotH = (height - margin * 2) / 2;
        int baseY_top = y + margin;

        for (int i = 1; i < trajectory.length; i++) {
            double[] prev = trajectory[i - 1];
            double[] curr = trajectory[i];
            int px = (int) (margin + x + (curr[0] - minX) / rangeX * plotW);
            int pz = (int) (baseY_top + (curr[2] - minZ) / rangeZ * plotH);
            float progress = (float) i / trajectory.length;
            int color = interpolateColor(progress, 0x00FF00, 0xFF0000);
            guiGraphics.fill(px - 1, pz - 1, px + 1, pz + 1, color);
        }

        int baseY_bottom = baseY_top + plotH + 5;
        for (int i = 1; i < trajectory.length; i++) {
            double[] prev = trajectory[i - 1];
            double[] curr = trajectory[i];
            int px = (int) (margin + x + (curr[0] - minX) / rangeX * plotW);
            int py = (int) (baseY_bottom + plotH - (curr[1] - minY) / rangeY * plotH);
            float progress = (float) i / trajectory.length;
            int color = interpolateColor(progress, 0x00FF00, 0xFF0000);
            guiGraphics.fill(px - 1, py - 1, px + 1, py + 1, color);
        }

        if (trajectory.length > 0) {
            double[] last = trajectory[trajectory.length - 1];
            int lx = (int) (margin + x + (last[0] - minX) / rangeX * plotW);
            int lz = (int) (baseY_top + (last[2] - minZ) / rangeZ * plotH);
            guiGraphics.fill(lx - 2, lz - 2, lx + 2, lz + 2, 0xFFFF0000);
        }
    }

    private static int interpolateColor(float t, int c1, int c2) {
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    public void setTrajectory(double[][] traj) { this.trajectory = traj; }
    public double[][] getTrajectory() { return trajectory; }
    public boolean isWorldPreviewActive() { return worldPreviewActive; }
    public void setWorldPreviewActive(boolean active) { this.worldPreviewActive = active; }
}
