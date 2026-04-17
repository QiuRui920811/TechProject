package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GEO Resources：每個 chunk 擁有一種隱藏地質資源（礦脈 / 原油 / 鹽 等），
 * 需要用 GEO Scanner 勘探才會得知；GEO Extractor 機器會持續抽取。
 *
 * <p>設計：
 * <ul>
 *   <li>所有 chunk 都有確定性資源（由 world seed + chunkX + chunkZ 雜湊得出）。
 *       這代表世界初始時就可以安全地用 peek() 查看，而不需要先寫入 yml。</li>
 *   <li>只有「已被抽取過」的 chunk 會進 remainingAmounts map 和 yml：
 *       剩餘 = 初始 - 已抽取。沒進 map 代表還沒動過它，回去看初始值。</li>
 *   <li>Scanner 掃描後不改變存量，只會短暫通知玩家。</li>
 * </ul>
 */
public final class GeoResourceService {

    /** 資源類型。對應的 output-id 必須是 tech 物品 id。 */
    public enum GeoType {
        OIL("crude_oil", 60, 320, 0.22),
        SALT("rock_salt", 80, 280, 0.18),
        URANIUM("uranium_ore_raw", 30, 140, 0.10),
        COPPER_VEIN("copper_dust", 80, 360, 0.22),
        SULFUR("sulfur_dust", 60, 240, 0.18),
        NONE("", 0, 0, 0.10);

        public final String outputId;
        public final int minAmount;
        public final int maxAmount;
        public final double weight;

        GeoType(final String outputId, final int minAmount, final int maxAmount, final double weight) {
            this.outputId = outputId;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.weight = weight;
        }

        public String displayName() {
            return switch (this) {
                case OIL -> "原油礦脈";
                case SALT -> "鹽礦";
                case URANIUM -> "鈾礦";
                case COPPER_VEIN -> "銅礦脈";
                case SULFUR -> "硫磺礦";
                case NONE -> "貧瘠地層";
            };
        }

        public int[] rgb() {
            return switch (this) {
                case OIL -> new int[] {0x1F, 0x29, 0x37};
                case SALT -> new int[] {0xF3, 0xF4, 0xF6};
                case URANIUM -> new int[] {0x65, 0xA3, 0x0D};
                case COPPER_VEIN -> new int[] {0xC2, 0x41, 0x0C};
                case SULFUR -> new int[] {0xFA, 0xCC, 0x15};
                case NONE -> new int[] {0x6B, 0x72, 0x80};
            };
        }
    }

    public record GeoSnapshot(GeoType type, int remaining, int initial) {
        public boolean isDepleted() {
            return this.type == GeoType.NONE || this.remaining <= 0;
        }
    }

    private final TechMCPlugin plugin;
    private final File dataFile;

    /** 每個 chunk 的「已抽取數量」。未在 map 中的 chunk 表示還沒動過。 */
    private final Map<String, Integer> extracted = new ConcurrentHashMap<>();

    /** 被玩家 Scanner 勘探過的 chunk（僅用於顯示圖示/提示）。 */
    private final Map<String, Long> scannedAt = new ConcurrentHashMap<>();

    public GeoResourceService(final TechMCPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "geo-resources.yml");
    }

    public void start() {
        this.load();
        // 每 5 分鐘自動存檔
        this.plugin.getSafeScheduler().runGlobalTimer(task -> this.save(), 20L * 60L * 5L, 20L * 60L * 5L);
    }

    public void shutdown() {
        this.save();
    }

    // ═══════════════════════════════════════════
    //  核心 API
    // ═══════════════════════════════════════════

    /** 取得某 chunk 的 geo 資料。始終返回非 null。 */
    public GeoSnapshot peek(final Chunk chunk) {
        return this.peek(chunk.getWorld(), chunk.getX(), chunk.getZ());
    }

    public GeoSnapshot peek(final World world, final int chunkX, final int chunkZ) {
        final GeoType type = this.deterministicType(world, chunkX, chunkZ);
        final int initial = this.deterministicAmount(world, chunkX, chunkZ, type);
        final int used = this.extracted.getOrDefault(key(world, chunkX, chunkZ), 0);
        final int remaining = Math.max(0, initial - used);
        return new GeoSnapshot(type, remaining, initial);
    }

    /** 從 chunk 抽取 amount 單位。回傳實際抽取數量（可能小於 amount）。 */
    public int extract(final Chunk chunk, final int amount) {
        if (amount <= 0) return 0;
        final GeoSnapshot snap = this.peek(chunk);
        if (snap.isDepleted()) return 0;
        final int take = Math.min(amount, snap.remaining);
        final String k = key(chunk.getWorld(), chunk.getX(), chunk.getZ());
        this.extracted.merge(k, take, Integer::sum);
        return take;
    }

    public void markScanned(final Chunk chunk) {
        this.scannedAt.put(key(chunk.getWorld(), chunk.getX(), chunk.getZ()), System.currentTimeMillis());
    }

    public boolean isScanned(final Chunk chunk) {
        return this.scannedAt.containsKey(key(chunk.getWorld(), chunk.getX(), chunk.getZ()));
    }

    // ═══════════════════════════════════════════
    //  確定性生成
    // ═══════════════════════════════════════════

    /** 根據世界 seed + chunk 座標雜湊出一個穩定的資源類型。 */
    private GeoType deterministicType(final World world, final int chunkX, final int chunkZ) {
        final long seed = world.getSeed() ^ (((long) chunkX) << 32) ^ (chunkZ * 0x9E3779B97F4A7C15L);
        final Random rng = new Random(seed);
        final double pick = rng.nextDouble();
        double sum = 0.0;
        for (final GeoType t : GeoType.values()) {
            sum += t.weight;
            if (pick < sum) return t;
        }
        return GeoType.NONE;
    }

    private int deterministicAmount(final World world, final int chunkX, final int chunkZ, final GeoType type) {
        if (type == GeoType.NONE) return 0;
        final long seed = world.getSeed() ^ (((long) chunkZ) << 32) ^ (chunkX * 0x9E3779B97F4A7C15L) ^ type.ordinal();
        final Random rng = new Random(seed);
        return type.minAmount + rng.nextInt(type.maxAmount - type.minAmount + 1);
    }

    private static String key(final World world, final int chunkX, final int chunkZ) {
        return world.getName() + ":" + chunkX + ":" + chunkZ;
    }

    // ═══════════════════════════════════════════
    //  持久化
    // ═══════════════════════════════════════════

    private void save() {
        final YamlConfiguration cfg = new YamlConfiguration();
        final java.util.List<String> extractedList = new java.util.ArrayList<>(this.extracted.size());
        for (final Map.Entry<String, Integer> e : this.extracted.entrySet()) {
            extractedList.add(e.getKey() + "|" + e.getValue());
        }
        cfg.set("extracted", extractedList);
        final java.util.List<String> scannedList = new java.util.ArrayList<>(this.scannedAt.size());
        for (final Map.Entry<String, Long> e : this.scannedAt.entrySet()) {
            scannedList.add(e.getKey() + "|" + e.getValue());
        }
        cfg.set("scanned", scannedList);
        this.plugin.getSafeScheduler().runAsync(() -> {
            try {
                if (!this.dataFile.getParentFile().exists()) {
                    this.dataFile.getParentFile().mkdirs();
                }
                cfg.save(this.dataFile);
            } catch (final IOException ex) {
                this.plugin.getLogger().warning("Geo 存檔失敗: " + ex.getMessage());
            }
        });
    }

    private void load() {
        if (!this.dataFile.exists()) return;
        final YamlConfiguration cfg = YamlConfiguration.loadConfiguration(this.dataFile);
        for (final String entry : cfg.getStringList("extracted")) {
            final int split = entry.lastIndexOf('|');
            if (split <= 0) continue;
            try {
                this.extracted.put(entry.substring(0, split), Integer.parseInt(entry.substring(split + 1)));
            } catch (final NumberFormatException ignored) { }
        }
        for (final String entry : cfg.getStringList("scanned")) {
            final int split = entry.lastIndexOf('|');
            if (split <= 0) continue;
            try {
                this.scannedAt.put(entry.substring(0, split), Long.parseLong(entry.substring(split + 1)));
            } catch (final NumberFormatException ignored) { }
        }
    }
}
