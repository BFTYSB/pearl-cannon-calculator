package com.pearlcannon.config;

import com.pearlcannon.common.CannonMode;
import com.pearlcannon.common.MatrixSolver;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置序列化器 - NBT / JSON 互转
 */
public final class ConfigSerializer {

    private static final Path CONFIG_DIR = Path.of("pearl_cannon_configs");

    public record CannonPreset(
            String name,
            CannonMode mode,
            double startX, double startY, double startZ,
            double targetX, double targetY, double targetZ,
            int ticks,
            double airDragModifier,
            int weakLoadingDelay,
            List<MatrixSolver.ExplosionRecord> explosions) {}

    // ==================== NBT 序列化 ====================

    public static CompoundTag presetToNbt(CannonPreset preset) {
        CompoundTag root = new CompoundTag();
        root.putString("name", preset.name());
        root.putString("mode", preset.mode().name());
        root.putDouble("startX", preset.startX());
        root.putDouble("startY", preset.startY());
        root.putDouble("startZ", preset.startZ());
        root.putDouble("targetX", preset.targetX());
        root.putDouble("targetY", preset.targetY());
        root.putDouble("targetZ", preset.targetZ());
        root.putInt("ticks", preset.ticks());
        root.putDouble("airDragModifier", preset.airDragModifier());
        root.putInt("weakLoadingDelay", preset.weakLoadingDelay());

        ListTag explosionList = new ListTag();
        for (MatrixSolver.ExplosionRecord exp : preset.explosions()) {
            CompoundTag expNbt = new CompoundTag();
            expNbt.putDouble("deltaVx", exp.deltaVx());
            expNbt.putDouble("deltaVy", exp.deltaVy());
            expNbt.putDouble("deltaVz", exp.deltaVz());
            expNbt.putDouble("explosionX", exp.explosionX());
            expNbt.putDouble("explosionY", exp.explosionY());
            expNbt.putDouble("explosionZ", exp.explosionZ());
            expNbt.putDouble("pearlX", exp.pearlX());
            expNbt.putDouble("pearlY", exp.pearlY());
            expNbt.putDouble("pearlZ", exp.pearlZ());
            expNbt.putDouble("power", exp.power());
            expNbt.putInt("tick", exp.tick());
            // 助推前预存速度：旧文件缺失时默认 0（向后兼容）
            expNbt.putDouble("preBoostVx", exp.preBoostVx());
            expNbt.putDouble("preBoostVy", exp.preBoostVy());
            expNbt.putDouble("preBoostVz", exp.preBoostVz());
            explosionList.add(expNbt);
        }
        root.put("explosions", explosionList);
        return root;
    }

    public static CannonPreset nbtToPreset(CompoundTag root) {
        List<MatrixSolver.ExplosionRecord> explosions = new ArrayList<>();
        ListTag expList = root.getList("explosions").orElse(new ListTag());
        for (int i = 0; i < expList.size(); i++) {
            CompoundTag e = expList.getCompound(i).orElse(new CompoundTag());
            explosions.add(new MatrixSolver.ExplosionRecord(
                e.getDouble("deltaVx").orElse(0.0), e.getDouble("deltaVy").orElse(0.0), e.getDouble("deltaVz").orElse(0.0),
                e.getDouble("explosionX").orElse(0.0), e.getDouble("explosionY").orElse(0.0), e.getDouble("explosionZ").orElse(0.0),
                e.getDouble("pearlX").orElse(0.0), e.getDouble("pearlY").orElse(0.0), e.getDouble("pearlZ").orElse(0.0),
                e.getDouble("power").orElse(0.0), e.getInt("tick").orElse(-1),
                e.getDouble("preBoostVx").orElse(0.0),
                e.getDouble("preBoostVy").orElse(0.0),
                e.getDouble("preBoostVz").orElse(0.0)));
        }
        return new CannonPreset(
            root.getString("name").orElse(""), CannonMode.valueOf(root.getString("mode").orElse("REGULAR")),
            root.getDouble("startX").orElse(0.0), root.getDouble("startY").orElse(0.0), root.getDouble("startZ").orElse(0.0),
            root.getDouble("targetX").orElse(0.0), root.getDouble("targetY").orElse(0.0), root.getDouble("targetZ").orElse(0.0),
            root.getInt("ticks").orElse(200), root.getDouble("airDragModifier").orElse(1.0),
            root.getInt("weakLoadingDelay").orElse(0), explosions);
    }

    // ==================== JSON 序列化 ====================

    public static String exportToJson(CannonPreset p) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(escapeJson(p.name())).append("\",\n");
        sb.append("  \"mode\": \"").append(p.mode().name()).append("\",\n");
        sb.append("  \"start\": [").append(p.startX()).append(", ").append(p.startY())
          .append(", ").append(p.startZ()).append("],\n");
        sb.append("  \"target\": [").append(p.targetX()).append(", ").append(p.targetY())
          .append(", ").append(p.targetZ()).append("],\n");
        sb.append("  \"ticks\": ").append(p.ticks()).append(",\n");
        sb.append("  \"airDragModifier\": ").append(p.airDragModifier()).append(",\n");
        sb.append("  \"weakLoadingDelay\": ").append(p.weakLoadingDelay()).append(",\n");
        sb.append("  \"explosions\": [\n");
        for (int i = 0; i < p.explosions().size(); i++) {
            var e = p.explosions().get(i);
            sb.append("    {\"dv\":[").append(e.deltaVx()).append(",").append(e.deltaVy())
              .append(",").append(e.deltaVz()).append("],\"ep\":[")
              .append(e.explosionX()).append(",").append(e.explosionY()).append(",")
              .append(e.explosionZ()).append("],\"p\":").append(e.power())
              .append(",\"pre\":[").append(e.preBoostVx()).append(",").append(e.preBoostVy())
              .append(",").append(e.preBoostVz()).append("]}");
            if (i < p.explosions().size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}");
        return sb.toString();
    }

    private static String escapeJson(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    // ==================== 文件操作 ====================

    public static void savePreset(CannonPreset preset) throws IOException {
        ensureDir();
        Files.write(CONFIG_DIR.resolve(preset.name() + ".nbt"), nbtToBytes(presetToNbt(preset)));
    }

    public static CannonPreset loadPreset(String name) throws IOException {
        byte[] data = Files.readAllBytes(CONFIG_DIR.resolve(name + ".nbt"));
        return nbtToPreset(bytesToNbt(data));
    }

    public static List<String> listPresets() {
        try { ensureDir(); } catch (IOException ignored) {}
        List<String> names = new ArrayList<>();
        File[] files = CONFIG_DIR.toFile().listFiles((d, f) -> f.endsWith(".nbt"));
        if (files != null) for (File f : files) names.add(f.getName().replace(".nbt", ""));
        return names;
    }

    private static void ensureDir() throws IOException { Files.createDirectories(CONFIG_DIR); }
    private static byte[] nbtToBytes(CompoundTag nbt) throws IOException {
        var baos = new java.io.ByteArrayOutputStream();
        NbtIo.write(nbt, new java.io.DataOutputStream(baos));
        return baos.toByteArray();
    }
    private static CompoundTag bytesToNbt(byte[] b) throws IOException {
        return NbtIo.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(b)));
    }
}
