package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.util.LocationKey;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 自訂 Android 程式：將 MachineService 的 Android Station 從預設程序（HARVEST/LOGGING/…）
 * 擴展為可由玩家用簡單 DSL 編寫的自訂程式。
 *
 * <p>DSL 指令（每行一條，大小寫不敏感）：
 * <ul>
 *   <li>{@code MOVE <N|E|S|W|U|D>} — 將相對位移向指定方向 +1</li>
 *   <li>{@code HARVEST} — 在當前位移位置嘗試收成作物</li>
 *   <li>{@code CHOP} — 在當前位移位置嘗試砍樹</li>
 *   <li>{@code ATTACK} — 在當前位移位置周圍 2.0 範圍攻擊第一個生物</li>
 *   <li>{@code SALVAGE} — 在當前位移位置撿拾附近物品實體</li>
 *   <li>{@code WAIT <n>} — 等待 n 週期（不消耗燃料）</li>
 *   <li>{@code RESET} — 將位移重置為 (0,1,0)</li>
 *   <li>{@code JUMP <line>} — 跳至第 line 行（0-index）</li>
 * </ul>
 *
 * <p>程式以 YAML 持久化到 {@code android-programs.yml}；程式計數器（PC）、
 * 位移（dx,dy,dz）、WAIT 計時器等執行期狀態僅存於記憶體，重啟後重置。
 */
public final class AndroidProgrammingService {

    public static final int MAX_LINES = 64;
    private static final int[] DEFAULT_OFFSET = {0, 1, 0};

    private final TechMCPlugin plugin;
    private final File file;
    private final Map<LocationKey, List<String>> programs = new ConcurrentHashMap<>();
    private final Map<LocationKey, Integer> counters = new ConcurrentHashMap<>();
    private final Map<LocationKey, int[]> offsets = new ConcurrentHashMap<>();
    private final Map<LocationKey, Integer> waits = new ConcurrentHashMap<>();

    public AndroidProgrammingService(final TechMCPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "android-programs.yml");
        this.load();
    }

    /* ---------------- 持久化 ---------------- */

    private void load() {
        if (!this.file.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(this.file);
        final List<?> raw = yaml.getList("programs");
        if (raw == null) {
            return;
        }
        for (final Object entry : raw) {
            if (!(entry instanceof Map<?, ?> map)) continue;
            final Object keyObj = map.get("key");
            final Object linesObj = map.get("lines");
            if (!(keyObj instanceof String keyStr) || !(linesObj instanceof List<?> linesList)) continue;
            final LocationKey key = parseKey(keyStr);
            if (key == null) continue;
            final List<String> lines = new ArrayList<>();
            for (final Object o : linesList) {
                if (o instanceof String s && !s.isBlank()) lines.add(s);
            }
            if (!lines.isEmpty()) {
                this.programs.put(key, Collections.synchronizedList(lines));
            }
        }
    }

    public synchronized void save() {
        final YamlConfiguration yaml = new YamlConfiguration();
        final List<Map<String, Object>> list = new ArrayList<>();
        for (final Map.Entry<LocationKey, List<String>> e : this.programs.entrySet()) {
            final Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("key", formatKey(e.getKey()));
            map.put("lines", new ArrayList<>(e.getValue()));
            list.add(map);
        }
        yaml.set("programs", list);
        try {
            final File parent = this.file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            yaml.save(this.file);
        } catch (final IOException ex) {
            this.plugin.getLogger().warning("無法儲存 android-programs.yml: " + ex.getMessage());
        }
    }

    private static LocationKey parseKey(final String raw) {
        final String[] parts = raw.split(":");
        if (parts.length != 4) return null;
        try {
            return new LocationKey(parts[0],
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        } catch (final NumberFormatException ex) {
            return null;
        }
    }

    private static String formatKey(final LocationKey key) {
        return key.worldName() + ":" + key.x() + ":" + key.y() + ":" + key.z();
    }

    /* ---------------- 程式 CRUD ---------------- */

    public List<String> getProgram(final LocationKey key) {
        final List<String> list = this.programs.get(key);
        return list == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(list));
    }

    public int size(final LocationKey key) {
        final List<String> list = this.programs.get(key);
        return list == null ? 0 : list.size();
    }

    public boolean addLine(final LocationKey key, final String line) {
        final String normalized = normalizeLine(line);
        if (normalized == null) return false;
        final List<String> list = this.programs.computeIfAbsent(key,
                k -> Collections.synchronizedList(new ArrayList<>()));
        if (list.size() >= MAX_LINES) return false;
        list.add(normalized);
        this.save();
        return true;
    }

    public boolean setLine(final LocationKey key, final int index, final String line) {
        final String normalized = normalizeLine(line);
        if (normalized == null) return false;
        final List<String> list = this.programs.get(key);
        if (list == null || index < 0 || index >= list.size()) return false;
        list.set(index, normalized);
        this.save();
        return true;
    }

    public boolean removeLine(final LocationKey key, final int index) {
        final List<String> list = this.programs.get(key);
        if (list == null || index < 0 || index >= list.size()) return false;
        list.remove(index);
        if (list.isEmpty()) {
            this.programs.remove(key);
        }
        this.resetRuntime(key);
        this.save();
        return true;
    }

    public void clear(final LocationKey key) {
        this.programs.remove(key);
        this.resetRuntime(key);
        this.save();
    }

    public void resetRuntime(final LocationKey key) {
        this.counters.remove(key);
        this.offsets.remove(key);
        this.waits.remove(key);
    }

    /* ---------------- 執行期狀態 ---------------- */

    /**
     * 取得下一條要執行的指令，並自動前進程式計數器。
     * 如果當前處於 WAIT 倒數，會傳回 null 並扣一次 wait。
     */
    public String nextInstruction(final LocationKey key) {
        final List<String> list = this.programs.get(key);
        if (list == null || list.isEmpty()) return null;
        final Integer w = this.waits.get(key);
        if (w != null && w > 0) {
            if (w - 1 <= 0) this.waits.remove(key);
            else this.waits.put(key, w - 1);
            return null;
        }
        int pc = this.counters.getOrDefault(key, 0);
        if (pc < 0 || pc >= list.size()) pc = 0;
        final String instr = list.get(pc);
        final int next = (pc + 1) % list.size();
        this.counters.put(key, next);
        return instr;
    }

    /** 以正則處理 JUMP：設定 PC 指向指定行；若無效則不動作。 */
    public void jumpTo(final LocationKey key, final int target) {
        final List<String> list = this.programs.get(key);
        if (list == null || list.isEmpty()) return;
        if (target < 0 || target >= list.size()) return;
        this.counters.put(key, target);
    }

    public void startWait(final LocationKey key, final int ticks) {
        if (ticks <= 0) return;
        this.waits.put(key, ticks);
    }

    public int[] offset(final LocationKey key) {
        return this.offsets.computeIfAbsent(key, k -> DEFAULT_OFFSET.clone());
    }

    public void addOffset(final LocationKey key, final int dx, final int dy, final int dz) {
        this.addOffset(key, dx, dy, dz, 8);
    }

    public void addOffset(final LocationKey key, final int dx, final int dy, final int dz, final int maxRadius) {
        final int[] cur = this.offset(key);
        cur[0] = Math.max(-maxRadius, Math.min(maxRadius, cur[0] + dx));
        cur[1] = Math.max(-4, Math.min(16, cur[1] + dy));
        cur[2] = Math.max(-maxRadius, Math.min(maxRadius, cur[2] + dz));
    }

    /** 直接設定偏移量到指定相對座標（由 MOVE x y z 使用）。 */
    public void setOffset(final LocationKey key, final int x, final int y, final int z, final int maxRadius) {
        final int[] cur = this.offset(key);
        cur[0] = Math.max(-maxRadius, Math.min(maxRadius, x));
        cur[1] = Math.max(-4, Math.min(16, y));
        cur[2] = Math.max(-maxRadius, Math.min(maxRadius, z));
    }

    public void resetOffset(final LocationKey key) {
        this.offsets.put(key, DEFAULT_OFFSET.clone());
    }

    /* ---------------- DSL 驗證 ---------------- */

    /** 回傳標準化後的指令字串（大寫、修整空白），若無效則回傳 null。 */
    public static String normalizeLine(final String raw) {
        if (raw == null) return null;
        final String trimmed = raw.trim();
        if (trimmed.isEmpty()) return null;
        final String[] parts = trimmed.split("\\s+");
        final String op = parts[0].toUpperCase(Locale.ROOT);
        switch (op) {
            case "HARVEST":
            case "CHOP":
            case "ATTACK":
            case "SALVAGE":
            case "RESET": {
                return op;
            }
            case "MOVE": {
                if (parts.length < 2) return null;
                final String dir = parts[1].toUpperCase(Locale.ROOT);
                if (dir.equals("N") || dir.equals("E") || dir.equals("S")
                        || dir.equals("W") || dir.equals("U") || dir.equals("D")) {
                    return op + " " + dir;
                }
                // MOVE x y z — 相對座標移動
                if (parts.length >= 4) {
                    try {
                        final int mx = Integer.parseInt(parts[1]);
                        final int my = Integer.parseInt(parts[2]);
                        final int mz = Integer.parseInt(parts[3]);
                        return op + " " + mx + " " + my + " " + mz;
                    } catch (final NumberFormatException ignored) {
                        return null;
                    }
                }
                return null;
            }
            case "WAIT":
            case "JUMP": {
                if (parts.length < 2) return null;
                try {
                    final int n = Integer.parseInt(parts[1]);
                    if (op.equals("WAIT") && (n < 1 || n > 200)) return null;
                    if (op.equals("JUMP") && (n < 0 || n >= MAX_LINES)) return null;
                    return op + " " + n;
                } catch (final NumberFormatException ex) {
                    return null;
                }
            }
            default:
                return null;
        }
    }

    public static String helpText() {
        return "指令: MOVE <N|E|S|W|U|D>, MOVE <x y z>, HARVEST, CHOP, ATTACK, SALVAGE, WAIT <1-200>, RESET, JUMP <行>";
    }
}
