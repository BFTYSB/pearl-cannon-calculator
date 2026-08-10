package com.pearlcannon.common;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 调试日志工具 - 记录玩家操作与 mod 内部数据，便于问题排查。
 *
 * <p>所有日志写入 Minecraft 日志目录下的 {@code pearl-cannon-debug.log}
 * （与 latest.log 同目录），并额外输出到控制台（latest.log）方便直接查看。
 *
 * <p>时间戳格式：{@code HH:mm:ss.SSS}。
 */
public final class DebugLog {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 日志根目录（自动探测），默认 null 表示未初始化 */
    private static volatile Path logDir = null;

    /** 日志文件大小上限：超过后自动轮转（保留最近一部分），避免无限增长 */
    private static final long MAX_LOG_BYTES = 512 * 1024; // 512KB

    /** 轮转历史文件保留份数 */
    private static final int MAX_ROTATED_FILES = 1;

    /** 采集会话专属日志文件名（时间戳命名），null 表示写入主日志文件 */
    private static volatile String sessionFile = null;

    /** 时间戳格式（用于采集会话文件名） */
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private DebugLog() {}

    /**
     * 显式初始化日志目录。通常无需手动调用，会自动从系统属性探测。
     *
     * @param dir 日志目录（null 时自动探测）
     */
    public static void init(Path dir) {
        if (dir != null) {
            logDir = dir;
        } else {
            logDir = detectLogDir();
        }
        info("=== 调试日志已初始化 ===");
    }

    /**
     * 自动探测日志目录：优先使用 Fabric 的游戏目录（与 latest.log 同目录），
     * 其次尝试系统属性，最后退回系统临时目录。
     */
    private static Path detectLogDir() {
        // FabricLoader 在模组初始化及游戏运行期间可用，能准确得到游戏根目录
        try {
            net.fabricmc.loader.api.FabricLoader fl = net.fabricmc.loader.api.FabricLoader.getInstance();
            Path p = fl.getGameDir().resolve("logs");
            Files.createDirectories(p);
            return p;
        } catch (Throwable ignored) {}
        String mcDir = System.getProperty("minecraft.game.dir");
        if (mcDir != null && !mcDir.isBlank()) {
            Path p = Paths.get(mcDir, "logs");
            try { Files.createDirectories(p); return p; } catch (IOException ignored) {}
        }
        // 兼容部分启动器未设置该属性：从 logs 系统属性再试
        String logsDir = System.getProperty("log_dir");
        if (logsDir != null && !logsDir.isBlank()) {
            Path p = Paths.get(logsDir);
            try { Files.createDirectories(p); return p; } catch (IOException ignored) {}
        }
        Path tmp = Paths.get(System.getProperty("java.io.tmpdir", "."));
        try { Files.createDirectories(tmp); return tmp; } catch (IOException ignored) {}
        return tmp;
    }

    /**
     * 记录 INFO 级别日志。
     */
    public static void info(String msg) {
        write("INFO ", msg);
    }

    /**
     * 记录 WARN 级别日志。
     */
    public static void warn(String msg) {
        write("WARN ", msg);
    }

    /**
     * 记录 ERROR 级别日志（含异常堆栈）。
     */
    public static void error(String msg, Throwable t) {
        StringBuilder sb = new StringBuilder(msg);
        if (t != null) {
            sb.append("\n\t").append(t);
            for (StackTraceElement e : t.getStackTrace()) {
                sb.append("\n\t\tat ").append(e);
            }
            Throwable cause = t.getCause();
            while (cause != null) {
                sb.append("\n\tCaused by: ").append(cause);
                for (StackTraceElement e : cause.getStackTrace()) {
                    sb.append("\n\t\tat ").append(e);
                }
                cause = cause.getCause();
            }
        }
        write("ERROR", sb.toString());
    }

    /**
     * 记录玩家操作。
     *
     * @param action 动作描述（如 "点击计算"）
     * @param detail 附加数据（可空）
     */
    public static void player(String action, String detail) {
        write("PLAYER", action + (detail == null ? "" : " | " + detail));
    }

    /**
     * 记录一次数值型事件（如计算请求/结果），带键值对格式化。
     */
    public static void data(String label, String keyValues) {
        write("DATA  ", label + " {" + keyValues + "}");
    }

    /**
     * 实际写入日志（文件 + 控制台）。
     * 写入前检查文件大小，超过上限则轮转（保留最近的日志）。
     * 若存在采集会话文件，则写入会话文件，否则写入主日志文件。
     */
    private static void write(String level, String msg) {
        String ts = LocalDateTime.now().format(TIME);
        String line = "[" + ts + "] [" + level + "] " + msg;
        System.out.println("[PearlCannon-Debug] " + line); // 输出到 latest.log
        Path p = logDir != null ? logDir : detectLogDir();
        if (p == null) return;
        String fileName = sessionFile != null ? sessionFile : "pearl-cannon-debug.log";
        rotateIfNeeded(p, fileName);
        try (BufferedWriter w = Files.newBufferedWriter(
                p.resolve(fileName),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(line);
            w.newLine();
        } catch (IOException e) {
            // 写文件失败不影响主流程
            System.err.println("[PearlCannon-Debug] 无法写入调试日志: " + e);
        }
    }

    /**
     * 开启采集会话日志：后续日志写入独立的时间戳文件
     * {@code pearl-cannon-collect-<yyyyMMdd-HHmmss>.log}，便于每次采集单独留存。
     * 采集结束后调用 {@link #stopSessionFile()} 恢复写入主日志。
     */
    public static void startSessionFile() {
        String ts = LocalDateTime.now().format(FILE_TS);
        sessionFile = "pearl-cannon-collect-" + ts + ".log";
        write("INFO ", "=== 采集会话开始 ===");
    }

    /**
     * 结束采集会话日志：恢复写入主日志文件 {@code pearl-cannon-debug.log}。
     */
    public static void stopSessionFile() {
        if (sessionFile == null) return;
        write("INFO ", "=== 采集会话结束 ===");
        sessionFile = null;
    }

    /**
     * 若指定日志文件超过大小上限，则轮转：
     * 将旧文件重命名为 .1.log（覆盖之前的轮转文件），主文件从头开始。
     */
    private static void rotateIfNeeded(Path dir, String fileName) {
        try {
            Path log = dir.resolve(fileName);
            if (!Files.exists(log)) return;
            long size = Files.size(log);
            if (size < MAX_LOG_BYTES) return;
            String base = fileName.endsWith(".log") ? fileName.substring(0, fileName.length() - 4) : fileName;
            for (int i = MAX_ROTATED_FILES; i >= 1; i--) {
                Path target = dir.resolve(base + "." + i + ".log");
                Path from = dir.resolve(i == 1 ? fileName : (base + "." + (i - 1) + ".log"));
                if (Files.exists(from)) {
                    Files.move(from, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.createFile(dir.resolve(fileName));
        } catch (IOException ignored) {
            // 轮转失败不影响主流程
        }
    }

    /**
     * 在日志文件开头写入一段会话头（含版本信息）。启动时调用。
     */
    public static void sessionStart(String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("=======================================================\n");
        sb.append("  Pearl Cannon Calculator 调试会话\n");
        sb.append("  开始时间: ").append(LocalDateTime.now().format(DATE)).append("\n");
        sb.append("  上下文:   ").append(context).append("\n");
        sb.append("=======================================================\n");
        System.out.println("[PearlCannon-Debug] " + sb);
        appendRaw(sb.toString());
    }

    private static void appendRaw(String content) {
        Path p = logDir != null ? logDir : detectLogDir();
        if (p == null) return;
        try (BufferedWriter w = Files.newBufferedWriter(
                p.resolve("pearl-cannon-debug.log"),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(content);
        } catch (IOException ignored) {}
    }

    /**
     * 将浮点数格式化为保留 3 位小数，便于阅读日志。
     */
    public static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.3f", d);
    }
}
