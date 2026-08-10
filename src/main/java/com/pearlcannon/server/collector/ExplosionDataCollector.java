package com.pearlcannon.server.collector;

import com.pearlcannon.common.CannonMode;
import com.pearlcannon.common.DebugLog;
import com.pearlcannon.common.MatrixSolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * 爆炸数据采集器（单例）
 *
 * 负责采集 TNT 爆炸对末影珍珠产生的速度增量（Δv），并存储供计算器使用。
 *
 * 修复说明（v1.0.1）：
 *  - 改用 Mixin 传入的「真实速度差」(爆炸后 - 爆炸前) 作为 Δv，
 *    不再使用基于几何方向的近似反推，消除布局不对称导致的系统性误差。
 *  - 增加全局爆炸中心去重：同一爆炸中心只处理一次，避免连锁爆炸/重复事件刷屏。
 *  - 仅当 |Δv| 超过阈值（默认 0.01）时才记录为有效助推，过滤无关爆炸噪声。
 *  - 限制日志打印频率，消除「范围内=0」刷屏。
 */
public final class ExplosionDataCollector {

    private static final ExplosionDataCollector INSTANCE = new ExplosionDataCollector();

    /** 已采集的爆炸助推记录（按采集顺序，作为主炮 TNT 助推序列） */
    private final List<MatrixSolver.ExplosionRecord> records = new ArrayList<>();

    /** 本次开炮周期内，珍珠爆炸前速度快照：pearlUUID -> 速度 */
    private final Map<UUID, Vec3> pearlVelocitySnapshots = new HashMap<>();

    /** 已命中的珍珠 UUID 集合（避免重复匹配同一颗珍珠造成的重复记录） */
    private final Set<UUID> matchedPearls = new HashSet<>();

    /** 首次速度突变时的游戏 tick（飞行开始，用于 relTick 计算） */
    private long flightStartGameTick = -1;

    /** 采集开关：玩家点击「采集」后开启，直到手动停止或服务器关闭 */
    private volatile boolean collecting = false;

    /** 当前炮模式 */
    private CannonMode currentMode = CannonMode.REGULAR;

    // ==================== 珍珠位置追踪（起点/落点自动填充） ====================

    /** 最后一次有效爆炸时珍珠的位置（炮口起点，跨轮次保留） */
    private double lastPearlStartX, lastPearlStartY, lastPearlStartZ;
    private boolean hasLastPearlStart = false;

    /** 本轮珍珠落点 */
    private double landX, landY, landZ;
    private boolean hasLand = false;

    /** 珍珠最后已知位置（tick 追踪，供落点兜底） */
    private double lastKnownX, lastKnownY, lastKnownZ;
    private boolean hasLastKnown = false;

    // ==================== 珍珠飞行时长（用于自动填充 ticks 字段） ====================

    /** 首次有效爆炸时的毫秒时间戳（飞行开始） */
    private long flightStartMillis = -1;

    /** 珍珠到达目的地时的毫秒时间戳（飞行结束） */
    private long flightEndMillis = -1;

    public static ExplosionDataCollector getInstance() {
        return INSTANCE;
    }

    private ExplosionDataCollector() {}

    /** 锁定追踪的珍珠 UUID（避免传送后漂移到另一颗珍珠） */
    private UUID trackedPearlUuid = null;

    /**
     * 在 level 中查找末影珍珠（主炮发射的珍珠，不限制 owner）。
     * 若已锁定珍珠 UUID，则只返回该颗；锁定珍珠消失后才重新寻找。
     *
     * <p>注：不加 getOwner()==null 限制——实测主炮发射的珍珠也可能带有 owner，
     * 且原始逻辑仅按类型 + isAlive 匹配即可稳定采集。
     */
    private ThrownEnderpearl findTrackedPearl(ServerLevel level) {
        if (level == null) return null;
        // 若已锁定，优先返回锁定的珍珠
        if (trackedPearlUuid != null) {
            for (Entity e : level.getAllEntities()) {
                if (e instanceof ThrownEnderpearl pearl && pearl.isAlive()
                        && pearl.getUUID().equals(trackedPearlUuid)) {
                    return pearl;
                }
            }
            // 锁定的珍珠已消失：解锁
            trackedPearlUuid = null;
        }
        for (Entity e : level.getAllEntities()) {
            if (e instanceof ThrownEnderpearl pearl && pearl.isAlive()) {
                trackedPearlUuid = pearl.getUUID();
                return pearl;
            }
        }
        return null;
    }

    /**
     * HEAD 阶段：已废弃。采集已改为 tick() 轮询珍珠速度突变检测，
     * 保留为空实现以避免修改 Mixin 注入点。
     */
    public void beforeExplosion(ServerLevel level) {
        // 采集逻辑已迁移至 tick()
    }

    /**
     * TAIL 阶段：已废弃。爆炸数据采集已改为 tick() 轮询珍珠速度突变检测，
     * 此方法保留为空实现以避免修改 Mixin 注入点。
     */
    public void afterExplosion(ServerLevel level, Explosion explosion) {
        // 采集逻辑已迁移至 tick()
    }

    /**
     * 获取已采集的记录（供计算器使用）
     */
    public List<MatrixSolver.ExplosionRecord> getRecords() {
        return new ArrayList<>(records);
    }

    /**
     * 清空所有采集数据（开始新一轮开炮前调用）
     */
    public void clearRecords() {
        clear();
    }

    public void clear() {
        records.clear();
        pearlVelocitySnapshots.clear();
        matchedPearls.clear();
        collecting = false;
        // 起点（最后一次爆炸珍珠位置）跨轮次保留；落点每次清空
        hasLand = false;
        hasLastKnown = false;
        flightStartMillis = -1;
        flightEndMillis = -1;
        flightStartGameTick = -1;
        prevPearlVelocity = null;
        prevPearlPos = null;
        trackedPearlUuid = null;
        // 不在此打印日志：startCollecting() 会统一打印「开始采集」
    }

    // ==================== 采集开关与模式 ====================

    public boolean isCollecting() {
        return collecting;
    }

    public void startCollecting() {
        // 开始采集前先清空旧数据，确保本轮数据干净
        clear();
        collecting = true;
        // 每次采集单独创建日志文件
        DebugLog.startSessionFile();
        DebugLog.info("开始采集爆炸数据");
    }

    public void stopCollecting() {
        collecting = false;
        DebugLog.info("停止采集，共采集 " + records.size() + " 条有效记录");
        // 采集结束，恢复写入主日志
        DebugLog.stopSessionFile();
    }

    public CannonMode getMode() {
        return currentMode;
    }

    public CannonMode getCurrentMode() {
        return currentMode;
    }

    public void setCurrentMode(CannonMode mode) {
        this.currentMode = mode;
        DebugLog.info("设置炮模式=" + mode.getLocalizedName(true));
    }

    public void setMode(CannonMode mode) {
        setCurrentMode(mode);
    }

    /** 水平速度突变检测阈值：珍珠水平速度相邻 tick 增量超过此值视为 TNT 助推（m/s） */
    private static final double VELOCITY_JUMP_THRESHOLD = 2.0;

    /** 珍珠位移异常阈值：相邻 tick 位移超过此值视为传送/命中瞬移（非 TNT 助推） */
    private static final double PEARL_TELEPORT_DISTANCE = 2.0;

    /** 上一 tick 采样的珍珠速度（用于突变检测） */
    private Vec3 prevPearlVelocity = null;

    /** 上一 tick 采样的珍珠位置（用于判断珍珠是否已起飞/位移） */
    private Vec3 prevPearlPos = null;

    // ==================== Pearl.tick HEAD/TAIL 双采样（v1.0.3 修正） ====================
    //
    // 修复目的：消除"prevPearlVelocity 在 pearl.tick() 后采样"导致的 Δv 混入
    //          重力+阻力差的问题（FTL 炮测试 y 方向 0.288 格偏差）。
    //
    // 采样时机（参考 Projectile Wiki / Explosion Wiki 26.2 运动模型）：
    //   pearl.tick() 内部顺序：
    //     1. TNT 爆炸处理：Δv 叠加到 pearl.deltaMovement
    //     2. 投掷物运动：Acc(gravity) → Drag(×drag) → Pos(+=v)
    //   HEAD 采样（tick() 进入时）：v_head = 上一 tick 结束后的最终速度
    //   TAIL 采样（tick() 返回时）：v_tail = 本 tick 结束后的最终速度
    //
    // 反推纯爆炸增量 Δv（不含重力/阻力差）：
    //   按 Acc→Drag 顺序：v_tail = (v_head + Δv + grav) × drag
    //   解出：Δv = v_tail / drag - grav - v_head
    //
    // 验证（用 20260805-150909 日志数据）：
    //   v_head = (0, -0.260, 0),  v_tail 期望 = (262.49, -0.287, -405.91)
    //   Δv = (262.49/0.99 - 0 - 0,  -0.287/0.99 + 0.03 + 0.260,  -405.91/0.99 - 0 - 0)
    //      = (265.14, 0, -410.01)  ← 与实际 TNT 助推方向一致，y 分量归零

    /** 当前 tick HEAD 采样的珍珠速度（pearl.tick() 进入时） */
    private Vec3 headPearlVelocity = null;
    /** 当前 tick HEAD 采样的珍珠位置 */
    private Vec3 headPearlPos = null;
    /** 当前 tick 是否已采集 HEAD（防止重复采样） */
    private boolean headSampledThisTick = false;
    /** 当前 tick 跟踪的珍珠 UUID（HEAD 与 TAIL 必须匹配同一颗珍珠） */
    private UUID headPearlUuid = null;

    /**
     * 服务端每 tick 调用：
     * <ol>
     *   <li>每 tick 采样珍珠速度，若与上一 tick 相比发生突变（&gt; 阈值），
     *       说明珍珠被 TNT 助推，记录该 Δv（真实速度增量）。</li>
     *   <li>珍珠炮的所有 TNT 在起飞瞬间（1~2 tick）内集中爆炸，
     *       珍珠速度从 0 突变为真实初速 → 只记录 1~2 条大 Δv，
     *       不会把同一爆炸的重复 tick 累加。</li>
     *   <li>飞行中阻力每 tick 衰减约 1%（远小于阈值），不触发记录。</li>
     *   <li>珍珠消失/落地时自动停止采集，记录落点。</li>
     * </ol>
     */
    public void tick(net.minecraft.server.MinecraftServer server) {
        if (server == null || !collecting) return;
        try {
            ServerLevel level = server.overworld();
            ThrownEnderpearl pearl = findTrackedPearl(level);
            if (pearl == null) {
                // 珍珠已消失：若本轮采集到过助推，则视为到达目的地
                if (!records.isEmpty() && hasLastKnown) {
                    landX = lastKnownX;
                    landY = lastKnownY;
                    landZ = lastKnownZ;
                    hasLand = true;
                    collecting = false;
                    // 记录飞行结束毫秒并计算飞行时长（毫秒 → tick）
                    flightEndMillis = System.currentTimeMillis();
                    long flightTicks = flightStartMillis >= 0
                            ? Math.max(1, (flightEndMillis - flightStartMillis) / 50L)
                            : 0;
                    DebugLog.info(String.format(
                            "珍珠到达目的地，自动停止采集 落点=(%.3f,%.3f,%.3f) 共采集 %d 条 飞行tick=%d",
                            landX, landY, landZ, records.size(), flightTicks));
                    // 采集自动结束，恢复写入主日志
                    DebugLog.stopSessionFile();
                }
                // 重置 HEAD/TAIL 采样状态
                headSampledThisTick = false;
                headPearlVelocity = null;
                headPearlPos = null;
                headPearlUuid = null;
                return;
            }

            // 持续更新珍珠最后已知位置
            lastKnownX = pearl.getX();
            lastKnownY = pearl.getY();
            lastKnownZ = pearl.getZ();
            hasLastKnown = true;

            // —— 旧逻辑（基于 server tick 边界采样）已废弃 ——
            // 改用 PearlEntityTickMixin 在 pearl.tick() HEAD/TAIL 双采样，
            // 由 onPearlTickHead / onPearlTickTail 触发助推检测，
            // 消除"prevPearlVelocity 混入重力+阻力差"的问题（v1.0.3）。
        } catch (Throwable t) {
            DebugLog.warn("珍珠速度追踪异常: " + t.getMessage());
        }
    }

    // ==================== Pearl.tick HEAD/TAIL 采样回调（v1.0.3） ====================

    /**
     * 由 PearlEntityTickMixin 在 pearl.tick() HEAD 调用。
     *
     * 采样珍珠助推前的速度（即上一 tick 结束后的最终速度）。
     * 此时本 tick 内的 TNT 爆炸处理尚未发生，珍珠速度尚未叠加 Δv。
     */
    public void onPearlTickHead(ThrownEnderpearl pearl) {
        if (!collecting) return;
        try {
            // 仅跟踪当前锁定的珍珠（与 findTrackedPearl 保持一致）
            if (trackedPearlUuid != null && !pearl.getUUID().equals(trackedPearlUuid)) return;

            headPearlVelocity = pearl.getDeltaMovement();
            headPearlPos = new Vec3(pearl.getX(), pearl.getY(), pearl.getZ());
            headPearlUuid = pearl.getUUID();
            headSampledThisTick = true;
        } catch (Throwable t) {
            DebugLog.warn("onPearlTickHead 异常: " + t.getMessage());
        }
    }

    /**
     * 由 PearlEntityTickMixin 在 pearl.tick() TAIL 调用。
     *
     * 此时本 tick 内的爆炸 Δv 已叠加，且已应用 Acc→Drag→Pos。
     * 用反推公式还原纯爆炸增量：
     *   v_tail = (v_head + Δv + grav) × drag
     *   Δv = v_tail / drag - grav - v_head
     *
     * 检测到 |Δv.xz| 超阈值时记录为 TNT 助推。
     */
    public void onPearlTickTail(ThrownEnderpearl pearl) {
        if (!collecting || !headSampledThisTick) return;
        try {
            // 必须与 HEAD 是同一颗珍珠
            if (headPearlUuid == null || !pearl.getUUID().equals(headPearlUuid)) {
                headSampledThisTick = false;
                return;
            }

            Vec3 tailVel = pearl.getDeltaMovement();
            Vec3 tailPos = new Vec3(pearl.getX(), pearl.getY(), pearl.getZ());

            // 反推纯爆炸增量 Δv（参考 Projectile Wiki 26.2 Acc→Drag→Pos 顺序）：
            //   v_tail = (v_head + Δv + grav) × drag
            //   Δv = v_tail / drag - grav - v_head
            double drag = com.pearlcannon.common.Constants.BASE_DRAG; // 默认 airDragModifier=1.0
            double grav = com.pearlcannon.common.Constants.GRAVITY;
            double dvx = tailVel.x / drag - 0 - headPearlVelocity.x;
            double dvy = tailVel.y / drag - grav - headPearlVelocity.y;
            double dvz = tailVel.z / drag - 0 - headPearlVelocity.z;
            double dvXZ = Math.sqrt(dvx * dvx + dvz * dvz);

            // 珍珠位移（判断是否已起飞/传送）
            double dispX = tailPos.x - headPearlPos.x;
            double dispY = tailPos.y - headPearlPos.y;
            double dispZ = tailPos.z - headPearlPos.z;
            double dispDist = Math.sqrt(dispX * dispX + dispY * dispY + dispZ * dispZ);

            // TNT 助推判定：水平突变 + 位移在炮口范围内
            if (dvXZ > VELOCITY_JUMP_THRESHOLD && dispDist <= PEARL_TELEPORT_DISTANCE) {
                ServerLevel level = pearl.level() instanceof ServerLevel sl ? sl : null;
                long nowGameTick = level != null ? level.getGameTime() : 0L;
                if (flightStartGameTick < 0) {
                    flightStartGameTick = nowGameTick;
                }
                int relTick = (int) Math.max(0, nowGameTick - flightStartGameTick);

                // preBoost = v_head（助推前珍珠已有速度，由 HEAD 采样得到）
                // 真实助推后速度 = v_head + Δv
                MatrixSolver.ExplosionRecord rec = new MatrixSolver.ExplosionRecord(
                        dvx, dvy, dvz,
                        tailPos.x, tailPos.y, tailPos.z,
                        headPearlPos.x, headPearlPos.y, headPearlPos.z,
                        4.0f, relTick,
                        headPearlVelocity.x, headPearlVelocity.y, headPearlVelocity.z);

                records.add(rec);
                lastPearlStartX = headPearlPos.x;
                lastPearlStartY = headPearlPos.y;
                lastPearlStartZ = headPearlPos.z;
                hasLastPearlStart = true;
                if (flightStartMillis < 0) {
                    flightStartMillis = System.currentTimeMillis();
                }
                DebugLog.info(String.format(
                        "采集到珍珠助推 #%d {Δv=(%.3f,%.3f,%.3f) |dv.xz|=%.3f tick=%d 位移=(%.3f,%.3f,%.3f) 助推前v=(%.3f,%.3f,%.3f) 助推后v=(%.3f,%.3f,%.3f)}",
                        records.size(), dvx, dvy, dvz, dvXZ, relTick,
                        dispX, dispY, dispZ,
                        headPearlVelocity.x, headPearlVelocity.y, headPearlVelocity.z,
                        tailVel.x, tailVel.y, tailVel.z));
            }

            // 同步更新 prevPearlVelocity/Pos（保留旧字段兼容性，但不再用于 Δv 计算）
            prevPearlVelocity = tailVel;
            prevPearlPos = tailPos;

            // 重置本 tick HEAD/TAIL 采样状态
            headSampledThisTick = false;
            headPearlVelocity = null;
            headPearlPos = null;
            headPearlUuid = null;
        } catch (Throwable t) {
            DebugLog.warn("onPearlTickTail 异常: " + t.getMessage());
        }
    }

    // ==================== 起点 / 落点 getter ====================

    /** 是否已有炮口起点（最后一次爆炸珍珠位置） */
    public boolean hasStartPos() {
        return hasLastPearlStart;
    }

    /** 获取炮口起点坐标 [x, y, z] */
    public double[] getStartPos() {
        return new double[]{lastPearlStartX, lastPearlStartY, lastPearlStartZ};
    }

    /** 是否已检测到落点 */
    public boolean hasLandPos() {
        return hasLand;
    }

    /** 获取落点坐标 [x, y, z] */
    public double[] getLandPos() {
        return new double[]{landX, landY, landZ};
    }

    /**
     * 获取珍珠实际飞行 tick 数（首次有效爆炸 → 珍珠到达目的地）。
     * 若尚未结束返回 -1。
     */
    public int getFlightTicks() {
        if (flightStartMillis < 0 || flightEndMillis < 0) {
            return -1;
        }
        return (int) Math.max(1, (flightEndMillis - flightStartMillis) / 50L);
    }

}
