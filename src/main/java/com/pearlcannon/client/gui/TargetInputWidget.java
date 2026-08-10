package com.pearlcannon.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class TargetInputWidget {

    private final EditBox fieldX, fieldY, fieldZ;
    private final String label;
    private final int x, y;

    public TargetInputWidget(int x, int y, String label, Font font,
                             String defaultX, String defaultY, String defaultZ) {
        this.x = x;
        this.y = y;
        this.label = label;
        int fieldWidth = 70, fieldHeight = 18, spacing = 10;

        this.fieldX = new EditBox(font, x, y, fieldWidth, fieldHeight, Component.literal("X"));
        this.fieldY = new EditBox(font, x + fieldWidth + spacing, y, fieldWidth, fieldHeight, Component.literal("Y"));
        this.fieldZ = new EditBox(font, x + (fieldWidth + spacing) * 2, y, fieldWidth, fieldHeight, Component.literal("Z"));

        this.fieldX.setValue(defaultX);
        this.fieldY.setValue(defaultY);
        this.fieldZ.setValue(defaultZ);
        this.fieldX.setMaxLength(12);
        this.fieldY.setMaxLength(12);
        this.fieldZ.setMaxLength(12);

        // 每个输入框悬停提示用途
        this.fieldX.setTooltip(Tooltip.create(Component.literal(label + " X 坐标")));
        this.fieldY.setTooltip(Tooltip.create(Component.literal(label + " Y 坐标")));
        this.fieldZ.setTooltip(Tooltip.create(Component.literal(label + " Z 坐标")));
    }

    public void render(GuiGraphicsExtractor guiGraphics, Font font) {
        // 每个输入框左侧绘制 X/Y/Z 小标识（ tooltip 已说明完整用途）
        guiGraphics.text(font, "X", fieldX.getX() - 10, fieldX.getY() + 4, 0xAAAAAA);
        guiGraphics.text(font, "Y", fieldY.getX() - 10, fieldY.getY() + 4, 0xAAAAAA);
        guiGraphics.text(font, "Z", fieldZ.getX() - 10, fieldZ.getY() + 4, 0xAAAAAA);
    }

    public double getX() { return parseDouble(fieldX.getValue(), 0); }
    public double getY() { return parseDouble(fieldY.getValue(), 0); }
    public double getZ() { return parseDouble(fieldZ.getValue(), 0); }

    public void setValues(double x, double y, double z) {
        fieldX.setValue(String.format("%.1f", x));
        fieldY.setValue(String.format("%.1f", y));
        fieldZ.setValue(String.format("%.1f", z));
    }

    public void setVisible(boolean visible) {
        fieldX.visible = visible;
        fieldY.visible = visible;
        fieldZ.visible = visible;
    }

    public void setYVisible(boolean visible) {
        fieldY.visible = visible;
    }

    public EditBox getFieldX() { return fieldX; }
    public EditBox getFieldY() { return fieldY; }
    public EditBox getFieldZ() { return fieldZ; }

    private static double parseDouble(String text, double def) {
        try { return Double.parseDouble(text); } catch (Exception e) { return def; }
    }
}
