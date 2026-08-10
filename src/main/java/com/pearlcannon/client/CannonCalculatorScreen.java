package com.pearlcannon.client;

import com.pearlcannon.common.CannonCalculator;
import com.pearlcannon.common.CannonMode;
import com.pearlcannon.common.Constants;
import com.pearlcannon.common.MotionEngine;
import com.pearlcannon.common.DebugLog;
import com.pearlcannon.common.EnvironmentDetector;
import com.pearlcannon.common.MatrixSolver;
import com.pearlcannon.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import com.pearlcannon.server.collector.ExplosionDataCollector;
import com.pearlcannon.client.gui.TargetInputWidget;
import com.pearlcannon.client.gui.ResultDisplayWidget;
import com.pearlcannon.client.gui.TrajectoryPreviewWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class CannonCalculatorScreen extends Screen {

    private EnvironmentDetector.GameEnvironment currentEnvironment = EnvironmentDetector.GameEnvironment.UNKNOWN;
    private CannonMode currentMode = CannonMode.REGULAR;
    private TargetInputWidget startInput, targetInput;
    private ResultDisplayWidget resultDisplay;
    private TrajectoryPreviewWidget trajectoryPreview;
    private EditBox ticksField, airDragField, weakLoadingDelayField;
    private Button regularTabBtn, weakLoadingTabBtn, vector3DTabBtn;
    private Button calculateBtn, collectBtn, clearDataBtn, previewBtn;
    private List<MatrixSolver.ExplosionRecord> explosionRecords = new ArrayList<>();
    private boolean waitingForResult = false;
    private StringWidget startLabel, targetLabel, ticksLabel, airDragLabel, weakDelayLabel, statusLabel, countLabel;

    private static final int TAB_WIDTH = 100, TAB_HEIGHT = 22, MARGIN = 8;

    public CannonCalculatorScreen() {
        super(Minecraft.getInstance(), Minecraft.getInstance().font,
            Component.translatable("screen.pearl_cannon_calculator.title"));
        DebugLog.init(null);
        DebugLog.player("打开计算器界面", "title=pearl_cannon_calculator");
    }

    @Override
    protected void init() {
        super.init();
        detectEnvironment();

        int centerX = this.width / 2;
        int startY = 25;

        regularTabBtn = Button.builder(
            Component.literal(CannonMode.REGULAR.getLocalizedName(true)),
            btn -> switchMode(CannonMode.REGULAR)
        ).bounds(centerX - TAB_WIDTH * 3 / 2 - MARGIN, startY, TAB_WIDTH, TAB_HEIGHT).build();

        weakLoadingTabBtn = Button.builder(
            Component.literal(CannonMode.WEAK_LOADING.getLocalizedName(true)),
            btn -> switchMode(CannonMode.WEAK_LOADING)
        ).bounds(centerX - TAB_WIDTH / 2, startY, TAB_WIDTH, TAB_HEIGHT).build();

        vector3DTabBtn = Button.builder(
            Component.literal("矢量炮"),
            btn -> switchMode(CannonMode.VECTOR_3D)
        ).bounds(centerX + TAB_WIDTH / 2 + MARGIN, startY, TAB_WIDTH, TAB_HEIGHT).build();

        this.addRenderableWidget(regularTabBtn);
        this.addRenderableWidget(weakLoadingTabBtn);
        this.addRenderableWidget(vector3DTabBtn);

        int inputY = startY + TAB_HEIGHT + 18;
        startInput = new TargetInputWidget(centerX - 180, inputY,
            Component.translatable("label.pearl_cannon_calculator.start").getString(),
            this.font, "0", "0", "0");
        targetInput = new TargetInputWidget(centerX - 180, inputY + 55,
            Component.translatable("label.pearl_cannon_calculator.target").getString(),
            this.font, "100", "0", "0");

        int paramY = inputY + 110;
        ticksField = createField(centerX - 160, paramY, "200");
        airDragField = createField(centerX - 40, paramY, "1.0");
        weakLoadingDelayField = createField(centerX + 80, paramY, "0");
        ticksField.setTooltip(Tooltip.create(Component.literal(
            "飞行刻数 (ticks)：末影珍珠飞行的总 tick 数，默认 200")));
        weakLoadingDelayField.setTooltip(Tooltip.create(Component.literal(
            "弱加载延迟 (weak_delay)：仅弱加载炮模式使用，默认 0")));
        updateAirDragTooltip();
        this.addRenderableWidget(ticksField);
        this.addRenderableWidget(airDragField);
        this.addRenderableWidget(weakLoadingDelayField);

        int btnY = paramY + 30;
        int btnW = 80, btnGap = 5;
        int totalBtnWidth = btnW * 4 + btnGap * 3;
        int btnStartX = centerX - totalBtnWidth / 2;
        calculateBtn = Button.builder(
            Component.translatable("button.pearl_cannon_calculator.calculate"), this::doCalculate
        ).bounds(btnStartX, btnY, btnW, TAB_HEIGHT).build();
        collectBtn = Button.builder(
            Component.translatable("button.pearl_cannon_calculator.collect"), this::toggleCollecting
        ).bounds(btnStartX + btnW + btnGap, btnY, btnW, TAB_HEIGHT).build();
        clearDataBtn = Button.builder(
            Component.translatable("button.pearl_cannon_calculator.clear"), this::clearData
        ).bounds(btnStartX + (btnW + btnGap) * 2, btnY, btnW, TAB_HEIGHT).build();
        previewBtn = Button.builder(
            Component.translatable("button.pearl_cannon_calculator.preview"), this::doPreview
        ).bounds(btnStartX + (btnW + btnGap) * 3, btnY, btnW, TAB_HEIGHT).build();

        this.addRenderableWidget(calculateBtn);
        this.addRenderableWidget(collectBtn);
        this.addRenderableWidget(clearDataBtn);
        this.addRenderableWidget(previewBtn);

        resultDisplay = new ResultDisplayWidget(centerX - 160, btnY + 38);
        resultDisplay.setEnvironment(currentEnvironment);
        trajectoryPreview = new TrajectoryPreviewWidget(centerX + 110, btnY + 38, 130, 90);

        updateTabHighlight();

        // === 标签：使用 StringWidget widget，由 super.extractRenderState 渲染 ===
        // 26.2 中 guiGraphics.text(String,...) 在 extractRenderState 流程不再可靠地输出文本
        startLabel = new StringWidget(centerX - 180, inputY - 12, 80, 10,
            Component.translatable("label.pearl_cannon_calculator.start"), this.font);
        targetLabel = new StringWidget(centerX - 180, inputY + 55 - 12, 80, 10,
            Component.translatable("label.pearl_cannon_calculator.target"), this.font);
        ticksLabel = new StringWidget(ticksField.getX(), ticksField.getY() - 12, 80, 10,
            Component.literal("刻数 (ticks)"), this.font);
        airDragLabel = new StringWidget(airDragField.getX(), airDragField.getY() - 12, 80, 10,
            Component.literal("空气阻力 (air_drag)"), this.font);
        weakDelayLabel = new StringWidget(weakLoadingDelayField.getX(), weakLoadingDelayField.getY() - 12, 80, 10,
            Component.literal("弱加载延迟 (weak_delay)"), this.font);
        this.addRenderableWidget(startLabel);
        this.addRenderableWidget(targetLabel);
        this.addRenderableWidget(ticksLabel);
        this.addRenderableWidget(airDragLabel);
        this.addRenderableWidget(weakDelayLabel);

        // 状态与计数标签移到右上角，避免和左侧输入区重叠
        statusLabel = new StringWidget(this.width - 130, 10, 120, 12,
            Component.literal(""), this.font);
        countLabel = new StringWidget(this.width - 130, 25, 120, 12,
            Component.literal("爆炸数量: 0"), this.font);
        this.addRenderableWidget(statusLabel);
        this.addRenderableWidget(countLabel);

        // 输入框必须作为 widget 加入屏幕才能接收鼠标/键盘事件
        this.addRenderableWidget(startInput.getFieldX());
        this.addRenderableWidget(startInput.getFieldY());
        this.addRenderableWidget(startInput.getFieldZ());
        this.addRenderableWidget(targetInput.getFieldX());
        this.addRenderableWidget(targetInput.getFieldY());
        this.addRenderableWidget(targetInput.getFieldZ());

        // 空气阻力输入变化时更新悬停提示
        airDragField.setResponder(value -> updateAirDragTooltip());

        // 必须在 weakDelayLabel 等所有相关 widget 创建完成后调用，否则 NPE
        updateFieldVisibility();
        updateStatusLabel();
        updateCountLabel();
    }

    private EditBox createField(int x, int y, String defaultText) {
        var f = new EditBox(this.font, x, y, 65, 18, Component.empty());
        f.setValue(defaultText);
        f.setMaxLength(12);
        return f;
    }

    private void detectEnvironment() {
        var mc = Minecraft.getInstance();
        boolean hasIntegratedServer = (mc.getSingleplayerServer() != null);
        boolean isConnectedToRemote = (mc.getConnection() != null && mc.getConnection().getServerData() != null);
        currentEnvironment = EnvironmentDetector.detect(hasIntegratedServer, isConnectedToRemote);
    }

    private void switchMode(CannonMode mode) {
        this.currentMode = mode;
        DebugLog.player("切换模式", "mode=" + mode);
        updateTabHighlight();
        updateFieldVisibility();
        resultDisplay.setResult(null);
        trajectoryPreview.setTrajectory(null);
    }

    private void updateTabHighlight() {
        regularTabBtn.active = (currentMode != CannonMode.REGULAR);
        weakLoadingTabBtn.active = (currentMode != CannonMode.WEAK_LOADING);
        vector3DTabBtn.active = (currentMode != CannonMode.VECTOR_3D);
    }

    private void updateFieldVisibility() {
        boolean showWeak = (currentMode == CannonMode.WEAK_LOADING);
        weakLoadingDelayField.visible = showWeak;
        weakDelayLabel.visible = showWeak;
    }

    private void doCalculate(Button btn) {
        DebugLog.player("点击计算",
            "mode=" + currentMode
            + " 目标=(" + DebugLog.fmt(targetInput.getX()) + "," + DebugLog.fmt(targetInput.getY()) + "," + DebugLog.fmt(targetInput.getZ()) + ")"
            + " 起点=(" + DebugLog.fmt(startInput.getX()) + "," + DebugLog.fmt(startInput.getY()) + "," + DebugLog.fmt(startInput.getZ()) + ")"
            + " ticks=" + parseInt(ticksField)
            + " drag=" + parseDouble(airDragField.getValue())
            + " weakDelay=" + parseInt(weakLoadingDelayField)
            + " 环境=" + currentEnvironment);
        if (EnvironmentDetector.canCalculateLocally(currentEnvironment)) {
            performLocalCalculation();
        } else if (EnvironmentDetector.requiresNetwork(currentEnvironment)) {
            sendCalculationRequest();
        }
    }

    private void performLocalCalculation() {
        try {
            var records = getExplosionRecords();
            DebugLog.data("计算输入诊断",
                "模式=" + currentMode
                + " 记录数=" + records.size()
                + " ticks=" + parseInt(ticksField)
                + " airDragModifier=" + DebugLog.fmt(parseDouble(airDragField.getValue()))
                + " effDrag=" + DebugLog.fmt(Constants.effectiveDrag(parseDouble(airDragField.getValue())))
                + " dragSum=" + DebugLog.fmt(MotionEngine.computeDragSum(parseInt(ticksField), Constants.effectiveDrag(parseDouble(airDragField.getValue()))))
                + " 起点=(" + DebugLog.fmt(startInput.getX()) + "," + DebugLog.fmt(startInput.getY()) + "," + DebugLog.fmt(startInput.getZ()) + ")"
                + " 目标=(" + DebugLog.fmt(targetInput.getX()) + "," + DebugLog.fmt(targetInput.getY()) + "," + DebugLog.fmt(targetInput.getZ()) + ")");
            if (!records.isEmpty()) {
                var r0 = records.get(0);
                DebugLog.data("首条记录",
                    "珍珠位置=(" + DebugLog.fmt(r0.pearlX()) + "," + DebugLog.fmt(r0.pearlY()) + "," + DebugLog.fmt(r0.pearlZ()) + ")"
                    + " 爆炸中心=(" + DebugLog.fmt(r0.explosionX()) + "," + DebugLog.fmt(r0.explosionY()) + "," + DebugLog.fmt(r0.explosionZ()) + ")"
                    + " 速度增量Δv=(" + DebugLog.fmt(r0.deltaVx()) + "," + DebugLog.fmt(r0.deltaVy()) + "," + DebugLog.fmt(r0.deltaVz()) + ")"
                    + " power=" + DebugLog.fmt(r0.power()));
            }
            var result = CannonCalculator.calculate(
                currentMode, records,
                targetInput.getX(), targetInput.getY(), targetInput.getZ(),
                startInput.getX(), startInput.getY(), startInput.getZ(),
                parseInt(ticksField), parseDouble(airDragField.getValue()),
                parseInt(weakLoadingDelayField));
            resultDisplay.setResult(result);
            resultDisplay.setWaiting(false);
            trajectoryPreview.setTrajectory(result.predictedTrajectory());
            DebugLog.data("本地计算完成",
                "solvable=" + result.solvable()
                + " 发射速度=(" + DebugLog.fmt(result.launchVx()) + "," + DebugLog.fmt(result.launchVy()) + "," + DebugLog.fmt(result.launchVz()) + ")"
                + " 误差=" + DebugLog.fmt(result.accuracyError())
                + " TNT=" + result.estimatedTNTCount()
                + " 轨迹点=" + (result.predictedTrajectory() == null ? 0 : result.predictedTrajectory().length));
        } catch (NumberFormatException e) {
            resultDisplay.setResult(null);
            DebugLog.warn("本地计算失败: 输入数字格式错误 (" + e.getMessage() + ")");
        }
    }

    private void sendCalculationRequest() {
        var request = new CalculationRequestPacket(
            currentMode,
            targetInput.getX(), targetInput.getY(), targetInput.getZ(),
            startInput.getX(), startInput.getY(), startInput.getZ(),
            parseInt(ticksField), parseDouble(airDragField.getValue()),
            parseInt(weakLoadingDelayField));
        ClientPlayNetworking.send(request);
        resultDisplay.setWaiting(true);
        waitingForResult = true;
        DebugLog.data("发送计算请求(C→S)",
            "mode=" + request.mode()
            + " 目标=(" + DebugLog.fmt(request.targetX()) + "," + DebugLog.fmt(request.targetY()) + "," + DebugLog.fmt(request.targetZ()) + ")"
            + " 起点=(" + DebugLog.fmt(request.startX()) + "," + DebugLog.fmt(request.startY()) + "," + DebugLog.fmt(request.startZ()) + ")"
            + " ticks=" + request.ticks() + " drag=" + DebugLog.fmt(request.airDragModifier())
            + " weakDelay=" + request.weakLoadingDelay());
    }

    public static void onReceiveResult(CalculationResultPacket packet) {
        DebugLog.data("收到计算结果(S→C)",
            "solvable=" + packet.solvable()
            + " 发射速度=(" + DebugLog.fmt(packet.launchVx()) + "," + DebugLog.fmt(packet.launchVy()) + "," + DebugLog.fmt(packet.launchVz()) + ")"
            + " 误差=" + DebugLog.fmt(packet.error())
            + " TNT=" + packet.tntCount()
            + " 轨迹点=" + (packet.trajectory() == null ? 0 : packet.trajectory().length));
        var mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof CannonCalculatorScreen screen) {
            var result = new CannonCalculator.CalculationResult(
                screen.currentMode,
                packet.launchVx(), packet.launchVy(), packet.launchVz(),
                0, 0, 0, 0, 0, 0,
                packet.tntCount(), 1.0,
                packet.trajectory(), packet.error(), packet.solvable(),
                null, 0, packet.tntCount());
            screen.resultDisplay.setResult(result);
            screen.resultDisplay.setWaiting(false);
            screen.waitingForResult = false;
            screen.trajectoryPreview.setTrajectory(packet.trajectory());
        }
    }

    public static void onExplosionVectorsSynced(ExplosionVectorSyncPacket packet) {
        DebugLog.data("收到爆炸向量同步(S→C)",
            "vectorA=(" + DebugLog.fmt(packet.vectorA()[0]) + "," + DebugLog.fmt(packet.vectorA()[1]) + "," + DebugLog.fmt(packet.vectorA()[2]) + ")"
            + " 爆炸数=" + packet.explosionCount());
    }

    private void toggleCollecting(Button btn) {
        var collector = ExplosionDataCollector.getInstance();
        if (collector.isCollecting()) {
            collector.stopCollecting();
            collectBtn.setMessage(Component.translatable("button.pearl_cannon_calculator.collect"));
            DebugLog.player("停止采集", "已采记录=" + collector.getRecords().size());
        } else {
            collector.startCollecting();
            landFilled = false;
            startFilled = false;
            collectBtn.setMessage(Component.translatable("button.pearl_cannon_calculator.collecting"));
            DebugLog.player("开始采集", "模式=" + collector.getMode());
            // 自动填充起点：最后一次爆炸时珍珠位置（炮口）
            if (collector.hasStartPos()) {
                double[] p = collector.getStartPos();
                startInput.setValues(p[0], p[1], p[2]);
                DebugLog.player("自动填充起点(最后一次爆炸珍珠位置)",
                    "(" + DebugLog.fmt(p[0]) + "," + DebugLog.fmt(p[1]) + "," + DebugLog.fmt(p[2]) + ")");
            } else {
                DebugLog.player("自动填充起点被跳过", "暂无最后一次爆炸数据");
            }
        }
        updateStatusLabel();
    }

    private void clearData(Button btn) {
        int before = ExplosionDataCollector.getInstance().getRecords().size();
        ExplosionDataCollector.getInstance().clearRecords();
        explosionRecords.clear();
        landFilled = false;
        startFilled = false;
        resultDisplay.setResult(null);
        trajectoryPreview.setTrajectory(null);
        DebugLog.player("清空数据", "清除前记录数=" + before);
    }

    private void doPreview(Button btn) {
        var result = resultDisplay.getResult();
        if (result != null && result.predictedTrajectory() != null) {
            TrajectoryRenderer.requestPreview(result.predictedTrajectory());
            DebugLog.player("预览轨迹", "轨迹点=" + result.predictedTrajectory().length);
        } else {
            DebugLog.warn("预览轨迹被忽略: 无可用结果");
        }
    }

    /** 更新底部状态标签（环境 + 采集中/空闲） */
    private void updateStatusLabel() {
        if (statusLabel == null) return;
        String envLabel = switch (currentEnvironment) {
            case SINGLEPLAYER -> "[SP] ";
            case MULTIPLAYER -> "[MP] ";
            case UNKNOWN -> "[?] ";
        };
        String collectStatus = ExplosionDataCollector.getInstance().isCollecting()
            ? Component.translatable("status.pearl_cannon_calculator.collecting").getString()
            : Component.translatable("status.pearl_cannon_calculator.idle").getString();
        statusLabel.setMessage(Component.literal(envLabel + collectStatus));
    }

    /** 更新底部爆炸数量标签 */
    private void updateCountLabel() {
        if (countLabel == null) return;
        int dataCount = ExplosionDataCollector.getInstance().getRecords().size();
        countLabel.setMessage(Component.literal(
            Component.translatable("label.pearl_cannon_calculator.explosion_count").getString() + dataCount));
    }

    private String lastAirDragTip = null;

    /**
     * 更新空气阻力输入框的悬停提示：显示精确的基础阻力 / 修正系数 / 有效阻力数值。
     * 输入框本身保持显示修正系数（默认 1.0），悬停才展示精确值。
     */
    private void updateAirDragTooltip() {
        if (airDragField == null) return;
        double modifier = parseDouble(airDragField.getValue());
        double effective = Constants.effectiveDrag(modifier);
        String tip = "空气阻力修正系数 (air_drag_modifier) = " + modifier + "\n"
            + "基础阻力 (base_drag) = " + Constants.BASE_DRAG + "\n"
            + "有效阻力 (effective_drag) = " + effective;
        if (!tip.equals(lastAirDragTip)) {
            lastAirDragTip = tip;
            airDragField.setTooltip(Tooltip.create(Component.literal(tip)));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 26.2: 先让 super 渲染背景、按钮、StringWidget 标签以及已加入的 EditBox，
        // 否则背景层会覆盖我们在其之后手动绘制的内容。
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        // 动态更新状态/计数标签（开销很小，只在需要时 setMessage）
        updateStatusLabel();
        updateCountLabel();
        // 自动采集落点：检测到珍珠到达目的地时，自动填入目标点并同步按钮状态
        checkAutoLandFill();

        // 手动渲染自定义标签、结果和轨迹预览（在 widget 层之上）
        startInput.render(guiGraphics, this.font);
        targetInput.render(guiGraphics, this.font);
        resultDisplay.render(guiGraphics, this.font);
        trajectoryPreview.render(guiGraphics, this.font);
    }

    /** 落点自动填充去重标志：每轮采集只填一次 */
    private boolean landFilled = false;

    /** 起点自动填充去重标志：已有最后一次爆炸数据后只填一次 */
    private boolean startFilled = false;

    /**
     * 检测珍珠落点：若服务端采集器已记录落点，自动填入目标点，
     * 并把「采集中」按钮恢复为「采集」。
     * 同时：若采集器已有炮口起点数据且起点框为空，则一并自动填入起点。
     */
    private void checkAutoLandFill() {
        var collector = ExplosionDataCollector.getInstance();
        if (landFilled) {
            return;
        }
        if (collector.hasLandPos()) {
            double[] p = collector.getLandPos();
            targetInput.setValues(p[0], p[1], p[2]);
            landFilled = true;
            collectBtn.setMessage(Component.translatable("button.pearl_cannon_calculator.collect"));
            DebugLog.player("自动填充目标点(珍珠落点)",
                "(" + DebugLog.fmt(p[0]) + "," + DebugLog.fmt(p[1]) + "," + DebugLog.fmt(p[2]) + ")");
            updateStatusLabel();
        }
        // 起点自动填充：开炮采集到爆炸记录后，自动填入炮口位置（使用独立标志避免重复）
        if (!startFilled && collector.hasStartPos()) {
            double[] p = collector.getStartPos();
            startInput.setValues(p[0], p[1], p[2]);
            startFilled = true;
            DebugLog.player("自动填充起点(炮口位置)",
                "(" + DebugLog.fmt(p[0]) + "," + DebugLog.fmt(p[1]) + "," + DebugLog.fmt(p[2]) + ")");
        }
        // 飞行 tick 自动填充：用珍珠实际飞行时长覆盖 ticks 字段
        int flightTicks = collector.getFlightTicks();
        if (flightTicks > 0 && ticksField != null) {
            ticksField.setValue(String.valueOf(flightTicks));
            DebugLog.player("自动填充飞行tick", "ticks=" + flightTicks);
        }
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    private List<MatrixSolver.ExplosionRecord> getExplosionRecords() {
        var records = ExplosionDataCollector.getInstance().getRecords();
        return records.isEmpty() ? explosionRecords : records;
    }

    private double parseDouble(String t, double d) { try { return Double.parseDouble(t); } catch (Exception e) { return d; } }
    private double parseDouble(String t) { return parseDouble(t, 0); }
    private int parseInt(EditBox f) { try { return Integer.parseInt(f.getValue()); } catch (Exception e) { return 200; } }
}
