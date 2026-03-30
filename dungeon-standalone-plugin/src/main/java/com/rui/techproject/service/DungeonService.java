package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.model.dungeon.*;
import com.rui.techproject.model.dungeon.DungeonDefinition.*;
import com.rui.techproject.model.dungeon.DungeonInstance.State;
import com.rui.techproject.util.RichText;
import com.rui.techproject.util.SafeScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * TechProject 副本系統核心服務。
 * <p>
 * 負責：
 * <ul>
 *   <li>從 YAML 載入副本定義</li>
 *   <li>世界模板複製 / 清理</li>
 *   <li>隊伍管理</li>
 *   <li>副本實例生命週期（建立→倒數→波次→Boss→通關/失敗→清理）</li>
 *   <li>腳本事件系統</li>
 *   <li>獎勵發放 + 冷卻 + 計時排行</li>
 * </ul>
 */
public final class DungeonService {

    // ── 常數 ──
    private static final int COUNTDOWN_SECONDS = 5;
    private static final int CLOSE_DELAY_TICKS = 200; // 10 秒後關閉世界
    private static final String INSTANCE_PREFIX = "dungeon_instance_";
    private static final String EDIT_PREFIX = "dungeon_edit_";
    private static final String TEMPLATE_DIR = "dungeon_templates";

    // ── 注入 ──
    private final TechProjectPlugin plugin;
    private final SafeScheduler scheduler;

    // ── 資料 ──
    private final Map<String, DungeonDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, DungeonInstance> instances = new ConcurrentHashMap<>();
    private final Map<UUID, DungeonParty> parties = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerPartyMap = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerInstanceMap = new ConcurrentHashMap<>();
    private final Map<UUID, DungeonPlayerData> playerData = new ConcurrentHashMap<>();
    private final Map<String, BossBar> bossBars = new ConcurrentHashMap<>();
    /** instanceId -> (playerUuid -> entered region script ids) */
    private final Map<String, Map<UUID, Set<String>>> playerRegionStates = new ConcurrentHashMap<>();
    private final AtomicInteger instanceCounter = new AtomicInteger(0);
    private final Map<String, List<LeaderboardEntry>> leaderboards = new ConcurrentHashMap<>();

    // ── 編輯模式 ──
    /** 玩家 UUID → 正在編輯的副本 ID */
    private final Map<UUID, String> editingSessions = new ConcurrentHashMap<>();
    /** 玩家 UUID → 進入編輯模式前的位置 */
    private final Map<UUID, Location> editReturnLocations = new ConcurrentHashMap<>();
    /** MythicDungeons 式功能編輯器管理器 */
    private DungeonEditorManager editorManager;
    /** 副本功能運行引擎（執行 Function 觸發 / 條件 / 串連） */
    private DungeonFunctionEngine functionEngine;

    // ── 編輯器 GUI / 聊天輸入 ──
    private static final String EDITOR_GUI_PREFIX = "§1§l副本編輯 §8» §r";
    /** 編輯器 GUI 頁面列舉 */
    public enum EditorPage { MAIN, SETTINGS, WAVES, WAVE_DETAIL, MOB_SELECT, BOSSES, BOSS_DETAIL, BOSS_SKILL, REWARDS, SCRIPTS }
    /** 編輯器 GUI 狀態 */
    public record EditorGuiState(String dungeonId, EditorPage page, int subIndex, int subIndex2) {}
    /** 等待聊天輸入的類型 */
    public record PendingEditorInput(String dungeonId, String field, EditorGuiState returnState) {}
    /** 玩家 UUID → 目前 GUI 狀態 */
    private final Map<UUID, EditorGuiState> editorGuiStates = new ConcurrentHashMap<>();
    /** 玩家 UUID → 等待的聊天輸入 */
    private final Map<UUID, PendingEditorInput> pendingEditorInput = new ConcurrentHashMap<>();

    public record LeaderboardEntry(UUID uuid, String name, int seconds) implements Comparable<LeaderboardEntry> {
        @Override public int compareTo(final LeaderboardEntry o) { return Integer.compare(this.seconds, o.seconds); }
    }

    // ──────────────────────────────────────────────
    //  建構 / 生命週期
    // ──────────────────────────────────────────────

    public DungeonService(final TechProjectPlugin plugin,
                          final SafeScheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.editorManager = new DungeonEditorManager(plugin, scheduler, this);
        this.functionEngine = new DungeonFunctionEngine(plugin, scheduler, this);
        this.loadDefinitions();
    }

    public void start() {
        // 清理上次伺服器關閉殘留的副本 / 編輯世界資料夾
        this.cleanupStaleWorldFolders();
        // 載入所有副本的功能定義（供運行引擎使用）
        for (final String dungeonId : this.definitions.keySet()) {
            this.editorManager.loadFunctions(dungeonId);
        }
        // 主 tick loop：每 20 tick (1 秒) 處理所有活躍副本
        this.scheduler.runGlobalTimer(task -> this.tickAllInstances(), 20L, 20L);
        // 腳本 timer 檢查：每 20 tick
        this.scheduler.runGlobalTimer(task -> this.tickScriptTimers(), 20L, 20L);
        // 啟動功能編輯器 (粒子特效等)
        this.editorManager.start();
        this.plugin.getLogger().info("[副本] 系統啟動，已載入 " + this.definitions.size() + " 個副本定義。");
    }

    /**
     * 清理伺服器根目錄中殘留的 dungeon_instance_* 與 dungeon_edit_* 資料夾。
     * Folia 不支援 unloadWorld，導致這些世界資料夾在上次關閉後可能殘留。
     */
    private void cleanupStaleWorldFolders() {
        final File container = Bukkit.getWorldContainer();
        final File[] children = container.listFiles();
        if (children == null) return;
        int cleaned = 0;
        for (final File dir : children) {
            if (!dir.isDirectory()) continue;
            final String name = dir.getName();
            if (name.startsWith(INSTANCE_PREFIX) || name.startsWith(EDIT_PREFIX)) {
                // 如果世界仍在記憶體中（Folia 殘留），嘗試卸載（可能失敗，不影響刪除）
                final World w = Bukkit.getWorld(name);
                if (w != null) {
                    try { Bukkit.unloadWorld(w, false); } catch (final Exception ignored) {}
                }
                this.deleteWorldFolder(dir);
                cleaned++;
                this.plugin.getLogger().info("[副本] 已清理殘留世界資料夾：" + name);
            }
        }
        if (cleaned > 0) {
            this.plugin.getLogger().info("[副本] 啟動清理完成，共清理 " + cleaned + " 個殘留世界資料夾。");
        }
    }

    public void shutdown() {
        // 關閉功能編輯器
        if (this.editorManager != null) this.editorManager.shutdown();
        // 關閉所有活躍副本
        for (final DungeonInstance instance : new ArrayList<>(this.instances.values())) {
            this.forceCloseInstance(instance, "伺服器關閉");
        }
        this.bossBars.values().forEach(bar -> {
            final List<Object> snap = new ArrayList<>();
            bar.viewers().forEach(snap::add);
            for (final var viewer : snap) {
                if (viewer instanceof net.kyori.adventure.audience.Audience a) bar.removeViewer(a);
            }
        });
        this.bossBars.clear();
    }

    public void saveAll() {
        // 玩家副本資料存到 dungeon-data.yml
        final File file = new File(this.plugin.getDataFolder(), "dungeon-data.yml");
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<UUID, DungeonPlayerData> entry : this.playerData.entrySet()) {
            final ConfigurationSection section = yaml.createSection(entry.getKey().toString());
            for (final Map.Entry<String, Object> e : entry.getValue().serialize().entrySet()) {
                section.set(e.getKey(), e.getValue());
            }
        }
        // 排行榜
        final ConfigurationSection lbSection = yaml.createSection("leaderboards");
        for (final Map.Entry<String, List<LeaderboardEntry>> e : this.leaderboards.entrySet()) {
            final List<Map<String, Object>> list = new ArrayList<>();
            for (final LeaderboardEntry entry : e.getValue()) {
                final Map<String, Object> m = new LinkedHashMap<>();
                m.put("uuid", entry.uuid().toString());
                m.put("name", entry.name());
                m.put("seconds", entry.seconds());
                list.add(m);
            }
            lbSection.set(e.getKey(), list);
        }
        try {
            yaml.save(file);
        } catch (final IOException e) {
            this.plugin.getLogger().warning("[副本] 儲存副本資料失敗：" + e.getMessage());
        }
    }

    public void loadPlayerData() {
        final File file = new File(this.plugin.getDataFolder(), "dungeon-data.yml");
        if (!file.exists()) return;
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (final String key : yaml.getKeys(false)) {
            if (key.equals("leaderboards")) continue;
            try {
                final UUID uuid = UUID.fromString(key);
                final DungeonPlayerData data = new DungeonPlayerData(uuid);
                final ConfigurationSection section = yaml.getConfigurationSection(key);
                if (section != null) {
                    final Map<String, Object> map = new LinkedHashMap<>();
                    for (final String k : section.getKeys(false)) {
                        map.put(k, section.get(k));
                    }
                    data.deserialize(map);
                }
                this.playerData.put(uuid, data);
            } catch (final IllegalArgumentException ignored) {}
        }
        // 排行榜
        final ConfigurationSection lbSection = yaml.getConfigurationSection("leaderboards");
        if (lbSection != null) {
            for (final String dungeonId : lbSection.getKeys(false)) {
                final List<?> list = lbSection.getList(dungeonId);
                if (list == null) continue;
                final List<LeaderboardEntry> entries = new ArrayList<>();
                for (final Object obj : list) {
                    if (obj instanceof Map<?, ?> m) {
                        try {
                            entries.add(new LeaderboardEntry(
                                UUID.fromString(String.valueOf(m.get("uuid"))),
                                String.valueOf(m.get("name")),
                                ((Number) m.get("seconds")).intValue()
                            ));
                        } catch (final Exception ignored) {}
                    }
                }
                entries.sort(LeaderboardEntry::compareTo);
                this.leaderboards.put(dungeonId, entries);
            }
        }
    }

    // ──────────────────────────────────────────────
    //  定義載入
    // ──────────────────────────────────────────────

    private void loadDefinitions() {
        this.definitions.clear();
        final File externalFile = new File(this.plugin.getDataFolder(), "tech-dungeons.yml");
        YamlConfiguration yaml;
        if (externalFile.isFile()) {
            yaml = YamlConfiguration.loadConfiguration(externalFile);
        } else {
            // 首次安裝：從 jar 內建資源複製出來
            this.plugin.saveResource("tech-dungeons.yml", false);
            if (externalFile.isFile()) {
                yaml = YamlConfiguration.loadConfiguration(externalFile);
            } else {
                final var resource = this.plugin.getResource("tech-dungeons.yml");
                if (resource == null) return;
                yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
            }
        }
        for (final String key : yaml.getKeys(false)) {
            final ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) continue;
            try {
                final DungeonDefinition def = this.parseDungeonDefinition(key, section);
                this.definitions.put(key, def);
            } catch (final Exception e) {
                this.plugin.getLogger().warning("[副本] 載入定義 '" + key + "' 失敗：" + e.getMessage());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private DungeonDefinition parseDungeonDefinition(final String id, final ConfigurationSection section) {
        return new DungeonDefinition(
            id,
            section.getString("display-name", id),
            section.getString("description", ""),
            section.getString("template-world", "dungeon_" + id),
            section.getInt("min-players", 1),
            section.getInt("max-players", 4),
            section.getInt("time-limit", 0),
            section.getInt("cooldown", 0),
            section.getInt("daily-limit", 0),
            this.parseDoubleArray(section, "spawn-point", new double[]{0.5, 65, 0.5, 0, 0}),
            section.contains("exit-point") ? this.parseDoubleArray(section, "exit-point", null) : null,
            this.parseWaves(section.getMapList("waves")),
            this.parseBosses(section.getMapList("bosses")),
            this.parseRewards(section.getMapList("rewards")),
            this.parseScripts(section.getMapList("scripts")),
            section.getString("required-permission"),
            section.getBoolean("tech-themed", false),
            section.getString("category", "冒險"),
            this.parseDungeonConfig(section.getConfigurationSection("config"))
        );
    }

    /** 解析完整副本組態（MythicDungeons 式設定）。 */
    @SuppressWarnings("unchecked")
    private DungeonConfig parseDungeonConfig(final ConfigurationSection cs) {
        final DungeonConfig cfg = DungeonConfig.defaults();
        if (cs == null) return cfg;

        // 基本
        cfg.dungeonType(cs.getString("dungeon-type", "INSTANCED"));
        cfg.showTitleOnStart(cs.getBoolean("show-title-on-start", true));

        // 大廳 / 出口
        if (cs.contains("lobby-point")) cfg.lobbyPoint(this.parseDoubleArray(cs, "lobby-point", null));
        if (cs.contains("exit-location")) cfg.exitLocation(this.parseDoubleArray(cs, "exit-location", null));
        cfg.alwaysUseExit(cs.getBoolean("always-use-exit", false));

        // 遊戲模式
        cfg.gamemode(cs.getString("gamemode", "SURVIVAL"));

        // 生命 & 死亡
        cfg.playerLives(cs.getInt("player-lives", -1));
        cfg.instantRespawn(cs.getBoolean("instant-respawn", false));
        cfg.deadPlayersSpectate(cs.getBoolean("dead-players-spectate", true));
        cfg.closeDungeonWhenAllSpectating(cs.getBoolean("close-when-all-spectating", true));
        if (cs.contains("respawn-point")) cfg.respawnPoint(this.parseDoubleArray(cs, "respawn-point", null));

        // 進入時保留
        cfg.keepInventoryOnEnter(cs.getBoolean("keep-inventory-on-enter", true));
        cfg.keepHealthOnEnter(cs.getBoolean("keep-health-on-enter", true));
        cfg.keepFoodOnEnter(cs.getBoolean("keep-food-on-enter", true));
        cfg.keepPotionEffectsOnEnter(cs.getBoolean("keep-potion-effects-on-enter", false));
        cfg.keepExpOnEnter(cs.getBoolean("keep-exp-on-enter", true));

        // 斷線
        cfg.kickOfflinePlayers(cs.getBoolean("kick-offline-players", true));
        cfg.kickOfflinePlayersDelay(cs.getInt("kick-offline-delay", 60));

        // 實例管理
        cfg.maxBackups(cs.getInt("max-backups", 3));
        cfg.maxInstances(cs.getInt("max-instances", 5));
        cfg.cleanupDelay(cs.getInt("cleanup-delay", 10));

        // 獎勵冷卻
        final ConfigurationSection lootCd = cs.getConfigurationSection("loot-cooldown");
        if (lootCd != null) {
            cfg.lootCooldownEnabled(lootCd.getBoolean("enabled", false));
            cfg.lootCooldownPerReward(lootCd.getBoolean("per-reward", false));
            cfg.lootCooldownType(lootCd.getString("type", "PLAYER"));
            cfg.lootCooldownTime(lootCd.getInt("time", 86400));
            cfg.lootCooldownResetDay(lootCd.getString("reset-day", "MONDAY"));
        }

        // 進入冷卻
        final ConfigurationSection accessCd = cs.getConfigurationSection("access-cooldown");
        if (accessCd != null) {
            cfg.accessCooldownEnabled(accessCd.getBoolean("enabled", false));
            cfg.accessCooldownOnFinish(accessCd.getBoolean("on-finish", true));
            cfg.accessCooldownOnLeave(accessCd.getBoolean("on-leave", false));
            cfg.accessCooldownOnLoseLives(accessCd.getBoolean("on-lose-lives", false));
            cfg.accessCooldownOnStart(accessCd.getBoolean("on-start", false));
            cfg.accessCooldownType(accessCd.getString("type", "PLAYER"));
            cfg.accessCooldownTime(accessCd.getInt("time", 3600));
            cfg.accessCooldownResetDay(accessCd.getString("reset-day", "MONDAY"));
        }

        // 進入要求
        final ConfigurationSection req = cs.getConfigurationSection("requirements");
        if (req != null) {
            cfg.requiredPermissions(req.getStringList("permissions"));
            cfg.minPartySize(req.getInt("min-party-size", 1));
            cfg.maxPartySize(req.getInt("max-party-size", 4));
            cfg.requiredDungeons(req.getStringList("dungeons-complete"));
            cfg.cost(req.getDouble("cost", 0));
            cfg.leaderOnlyCost(req.getBoolean("leader-only-cost", true));
        }

        // 鑰匙
        final ConfigurationSection keys = cs.getConfigurationSection("access-keys");
        if (keys != null) {
            cfg.accessKeysConsume(keys.getBoolean("consume", true));
            cfg.accessKeysLeaderOnly(keys.getBoolean("leader-only", true));
            cfg.accessKeyItems(keys.getStringList("items"));
        }

        // 難度
        final ConfigurationSection diff = cs.getConfigurationSection("difficulty");
        if (diff != null) {
            cfg.enableDifficultyLevels(diff.getBoolean("enable-levels", false));
            cfg.enableDifficultyMenu(diff.getBoolean("enable-menu", false));
            final ConfigurationSection levels = diff.getConfigurationSection("levels");
            if (levels != null) {
                final List<DungeonConfig.DifficultyLevel> diffList = new ArrayList<>();
                for (final String key : levels.getKeys(false)) {
                    final ConfigurationSection lvl = levels.getConfigurationSection(key);
                    if (lvl == null) continue;
                    diffList.add(new DungeonConfig.DifficultyLevel(key, lvl.getString("display-name", key))
                        .mobHealth(lvl.getDouble("mob-health", 1.0))
                        .mobAmounts(lvl.getDouble("mob-amounts", 1.0))
                        .mobDamage(lvl.getDouble("mob-damage", 1.0))
                        .bonusMythicLevels(lvl.getInt("bonus-mythic-levels", 0))
                        .bonusLoot(lvl.getDouble("bonus-loot", 1.0)));
                }
                cfg.difficultyLevels(diffList);
            }
        }

        // 地圖
        final ConfigurationSection map = cs.getConfigurationSection("map");
        if (map != null) {
            cfg.mapGiveOnJoin(map.getBoolean("give-on-join", false));
            cfg.mapMaxRenderDepth(map.getInt("max-render-depth", 5));
            cfg.mapDisplayFloors(map.getBoolean("display-floors", true));
            cfg.mapFloorDepth(map.getInt("floor-depth", 10));
            cfg.mapShowAllPlayers(map.getBoolean("show-all-players", true));
        }

        // 規則
        final ConfigurationSection rules = cs.getConfigurationSection("rules");
        if (rules != null) {
            final DungeonConfig.DungeonRules r = cfg.rules();
            r.spawnMobs(rules.getBoolean("spawn-mobs", false));
            r.spawnAnimals(rules.getBoolean("spawn-animals", false));
            r.spawnMonsters(rules.getBoolean("spawn-monsters", false));
            r.allowBreakBlocks(rules.getBoolean("allow-break-blocks", false));
            r.allowPlaceBlocks(rules.getBoolean("allow-place-blocks", false));
            r.allowEnderpearl(rules.getBoolean("allow-enderpearl", false));
            r.allowChorusFruit(rules.getBoolean("allow-chorus-fruit", false));
            r.allowBucket(rules.getBoolean("allow-bucket", false));
            r.allowPlaceEntities(rules.getBoolean("allow-place-entities", false));
            r.disableRandomTick(rules.getBoolean("disable-random-tick", true));
            r.preventPlantGrowth(rules.getBoolean("prevent-plant-growth", true));
            r.preventExplosionBlockDamage(rules.getBoolean("prevent-explosion-block-damage", true));
            r.pvp(rules.getBoolean("pvp", false));
            r.hideDeathMessages(rules.getBoolean("hide-death-messages", true));
            r.preventTeleportIn(rules.getBoolean("prevent-teleport-in", true));
            r.allowCommands(rules.getBoolean("allow-commands", false));
            r.allowedCommands(rules.getStringList("allowed-commands"));
            r.disallowedCommands(rules.getStringList("disallowed-commands"));
            r.bannedItems(rules.getStringList("banned-items"));
            r.customBannedItems(rules.getStringList("custom-banned-items"));
            r.blockPlaceWhitelist(rules.getStringList("block-place-whitelist"));
            r.blockPlaceBlacklist(rules.getStringList("block-place-blacklist"));
            r.blockBreakWhitelist(rules.getStringList("block-break-whitelist"));
            r.blockBreakBlacklist(rules.getStringList("block-break-blacklist"));
            r.allowBreakPlacedBlocks(rules.getBoolean("allow-break-placed-blocks", false));
            r.damageProtectedEntities(rules.getStringList("damage-protected-entities"));
            r.interactProtectedEntities(rules.getStringList("interact-protected-entities"));
            r.preventDurabilityLoss(rules.getBoolean("prevent-durability-loss", false));
        }

        return cfg;
    }

    private double[] parseDoubleArray(final ConfigurationSection section, final String key, final double[] defaults) {
        final List<?> list = section.getList(key);
        if (list == null || list.isEmpty()) return defaults;
        return list.stream().mapToDouble(v -> v instanceof Number n ? n.doubleValue() : 0).toArray();
    }

    @SuppressWarnings("unchecked")
    private List<WaveDefinition> parseWaves(final List<Map<?, ?>> list) {
        if (list == null) return List.of();
        final List<WaveDefinition> waves = new ArrayList<>();
        for (final Map<?, ?> map : list) {
            // 支援兩種 key 格式：範本用 "wave" 、編輯器存檔用 "wave-index"
            final Object waveIdx = map.containsKey("wave-index") ? map.get("wave-index") : map.get("wave");
            // 讀取自訂生成位置
            double[] spawnLoc = null;
            if (map.containsKey("spawn-location")) {
                final List<?> locList = (List<?>) map.get("spawn-location");
                if (locList != null && locList.size() >= 3) {
                    spawnLoc = new double[]{toDouble(locList.get(0), 0), toDouble(locList.get(1), 65), toDouble(locList.get(2), 0)};
                }
            }
            waves.add(new WaveDefinition(
                toInt(waveIdx, waves.size()),
                this.parseMobEntries((List<Map<?, ?>>) map.get("mobs")),
                toInt(map.get("spawn-delay"), 5),
                toString(map.get("message"), ""),
                toBool(map.get("require-clear"), true),
                spawnLoc
            ));
        }
        return waves;
    }

    @SuppressWarnings("unchecked")
    private List<MobEntry> parseMobEntries(final List<Map<?, ?>> list) {
        if (list == null) return List.of();
        final List<MobEntry> entries = new ArrayList<>();
        for (final Map<?, ?> map : list) {
            // 支援兩種 key 格式：範本用 "entity-type"、編輯器存檔用 "type"
            final Object entityTypeVal = map.containsKey("type") ? map.get("type") : map.get("entity-type");
            entries.add(new MobEntry(
                toString(entityTypeVal, "ZOMBIE"),
                toString(map.get("mythic-mob-id"), null),
                toInt(map.get("count"), 1),
                toInt(map.get("level"), 1),
                toDoubleArray(map.get("spawn-offset"), new double[]{0, 1, 0}),
                toString(map.get("custom-name"), null),
                map.get("equipment") instanceof Map<?, ?> eq ? (Map<String, String>) eq : Map.of()
            ));
        }
        return entries;
    }

    @SuppressWarnings("unchecked")
    private List<BossDefinition> parseBosses(final List<Map<?, ?>> list) {
        if (list == null) return List.of();
        final List<BossDefinition> bosses = new ArrayList<>();
        for (final Map<?, ?> map : list) {
            bosses.add(new BossDefinition(
                toString(map.get("id"), "boss"),
                toString(map.get("display-name"), "Boss"),
                toString(map.get("entity-type"), "WITHER_SKELETON"),
                toString(map.get("mythic-mob-id"), null),
                toDouble(map.get("health"), 200),
                toDouble(map.get("damage"), 10),
                toDoubleArray(map.get("spawn-point"), new double[]{0, 65, 0}),
                this.parseBossSkills((List<Map<?, ?>>) map.get("skills")),
                this.parseBossPhases((List<Map<?, ?>>) map.get("phases")),
                toInt(map.get("after-wave"), -1),
                toBool(map.get("boss-bar"), true),
                map.get("loot-table") instanceof List<?> lt ? lt.stream().map(String::valueOf).toList() : List.of()
            ));
        }
        return bosses;
    }

    @SuppressWarnings("unchecked")
    private List<BossSkill> parseBossSkills(final List<Map<?, ?>> list) {
        if (list == null) return List.of();
        final List<BossSkill> skills = new ArrayList<>();
        for (final Map<?, ?> map : list) {
            skills.add(new BossSkill(
                toString(map.get("id"), "skill"),
                toString(map.get("type"), "aoe_damage"),
                toInt(map.get("cooldown"), 200),
                toDouble(map.get("radius"), 5),
                toDouble(map.get("damage"), 5),
                toString(map.get("message"), null),
                map.get("params") instanceof Map<?, ?> p ? (Map<String, Object>) p : Map.of()
            ));
        }
        return skills;
    }

    @SuppressWarnings("unchecked")
    private List<BossPhase> parseBossPhases(final List<Map<?, ?>> list) {
        if (list == null) return List.of();
        final List<BossPhase> phases = new ArrayList<>();
        for (final Map<?, ?> map : list) {
            phases.add(new BossPhase(
                toDouble(map.get("health-threshold"), 0.5),
                toString(map.get("message"), null),
                toDouble(map.get("damage-multiplier"), 1.0),
                toDouble(map.get("speed-multiplier"), 1.0),
                this.parseMobEntries((List<Map<?, ?>>) map.get("reinforcements")),
                map.get("activate-skills") instanceof List<?> as ? as.stream().map(String::valueOf).toList() : List.of()
            ));
        }
        return phases;
    }

    @SuppressWarnings("unchecked")
    private List<RewardDefinition> parseRewards(final List<Map<?, ?>> list) {
        if (list == null) return List.of();
        final List<RewardDefinition> rewards = new ArrayList<>();
        for (final Map<?, ?> map : list) {
            rewards.add(new RewardDefinition(
                toString(map.get("type"), "item"),
                toString(map.get("value"), "DIAMOND"),
                toInt(map.get("amount"), 1),
                toDouble(map.get("chance"), 1.0),
                toBool(map.get("first-clear-only"), false)
            ));
        }
        return rewards;
    }

    @SuppressWarnings("unchecked")
    private List<ScriptDefinition> parseScripts(final List<Map<?, ?>> list) {
        if (list == null) return List.of();
        final List<ScriptDefinition> scripts = new ArrayList<>();
        for (final Map<?, ?> map : list) {
            scripts.add(new ScriptDefinition(
                toString(map.get("id"), "script_" + scripts.size()),
                toString(map.get("trigger"), "timer"),
                this.parseScriptConditions((List<Map<?, ?>>) map.get("conditions")),
                this.parseScriptActions((List<Map<?, ?>>) map.get("actions")),
                toBool(map.get("one-shot"), false),
                toInt(map.get("delay-ticks"), 0)
            ));
        }
        return scripts;
    }

    @SuppressWarnings("unchecked")
    private List<ScriptCondition> parseScriptConditions(final List<Map<?, ?>> list) {
        if (list == null) return List.of();
        return list.stream().map(m -> new ScriptCondition(
            toString(m.get("type"), ""),
            m.get("params") instanceof Map<?, ?> p ? (Map<String, Object>) p : Map.of()
        )).toList();
    }

    @SuppressWarnings("unchecked")
    private List<ScriptAction> parseScriptActions(final List<Map<?, ?>> list) {
        if (list == null) return List.of();
        return list.stream().map(m -> new ScriptAction(
            toString(m.get("type"), ""),
            m.get("params") instanceof Map<?, ?> p ? (Map<String, Object>) p : Map.of()
        )).toList();
    }

    // 解析輔助方法
    private static int toInt(final Object obj, final int def) {
        return obj instanceof Number n ? n.intValue() : def;
    }
    private static double toDouble(final Object obj, final double def) {
        return obj instanceof Number n ? n.doubleValue() : def;
    }
    private static boolean toBool(final Object obj, final boolean def) {
        return obj instanceof Boolean b ? b : def;
    }
    private static String toString(final Object obj, final String def) {
        return obj instanceof String s ? s : def;
    }
    private static double[] toDoubleArray(final Object obj, final double[] def) {
        if (obj instanceof List<?> list) {
            return list.stream().mapToDouble(v -> v instanceof Number n ? n.doubleValue() : 0).toArray();
        }
        return def;
    }

    // ──────────────────────────────────────────────
    //  公開 API — 隊伍
    // ──────────────────────────────────────────────

    public Map<String, DungeonDefinition> definitions() { return Collections.unmodifiableMap(this.definitions); }

    public DungeonParty createParty(final Player leader) {
        final UUID uuid = leader.getUniqueId();
        if (this.playerPartyMap.containsKey(uuid)) {
            leader.sendMessage(this.msg("§c你已經在一個隊伍中了。"));
            return null;
        }
        final String partyId = "party_" + uuid.toString().substring(0, 8);
        final DungeonParty party = new DungeonParty(partyId, uuid);
        this.parties.put(uuid, party);
        this.playerPartyMap.put(uuid, partyId);
        leader.sendMessage(this.msg("§a隊伍已建立！使用 §f/tech dungeon invite <玩家> §a來邀請隊友。"));
        return party;
    }

    public void inviteToParty(final Player leader, final Player target) {
        final DungeonParty party = this.getPartyByLeader(leader.getUniqueId());
        if (party == null) {
            leader.sendMessage(this.msg("§c你沒有隊伍，先用 §f/tech dungeon party §c建立。"));
            return;
        }
        if (!party.leader().equals(leader.getUniqueId())) {
            leader.sendMessage(this.msg("§c只有隊長可以邀請玩家。"));
            return;
        }
        if (this.playerPartyMap.containsKey(target.getUniqueId())) {
            leader.sendMessage(this.msg("§c該玩家已在隊伍中。"));
            return;
        }
        party.invite(target.getUniqueId());
        target.sendMessage(this.msg("§a" + leader.getName() + " §f邀請你加入副本隊伍！使用 §a/tech dungeon accept §f來接受。"));
        leader.sendMessage(this.msg("§a已邀請 " + target.getName() + "。"));
    }

    public void acceptInvite(final Player player) {
        final UUID uuid = player.getUniqueId();
        for (final DungeonParty party : this.parties.values()) {
            if (party.isInvited(uuid)) {
                if (this.playerPartyMap.containsKey(uuid)) {
                    this.leaveParty(player);
                }
                party.addMember(uuid);
                this.playerPartyMap.put(uuid, party.partyId());
                this.broadcastToParty(party, "§a" + player.getName() + " 加入了隊伍！");
                return;
            }
        }
        player.sendMessage(this.msg("§c你沒有待接受的邀請。"));
    }

    public void leaveParty(final Player player) {
        final UUID uuid = player.getUniqueId();
        final String partyId = this.playerPartyMap.remove(uuid);
        if (partyId == null) {
            player.sendMessage(this.msg("§c你不在任何隊伍中。"));
            return;
        }
        final DungeonParty party = this.parties.get(uuid);
        if (party != null) {
            party.removeMember(uuid);
            if (party.members().isEmpty()) {
                this.parties.remove(party.leader());
            } else {
                this.broadcastToParty(party, "§e" + player.getName() + " 離開了隊伍。");
            }
        }
        player.sendMessage(this.msg("§e你已離開隊伍。"));
    }

    public DungeonParty getPartyByLeader(final UUID leader) {
        return this.parties.get(leader);
    }

    public DungeonParty getPlayerParty(final UUID uuid) {
        final String partyId = this.playerPartyMap.get(uuid);
        if (partyId == null) return null;
        for (final DungeonParty party : this.parties.values()) {
            if (party.partyId().equals(partyId)) return party;
        }
        return null;
    }

    // ──────────────────────────────────────────────
    //  公開 API — 副本實例
    // ──────────────────────────────────────────────

    public String getPlayerInstanceId(final UUID uuid) { return this.playerInstanceMap.get(uuid); }
    public DungeonInstance getInstance(final String instanceId) { return this.instances.get(instanceId); }
    public Map<String, DungeonInstance> activeInstances() { return Collections.unmodifiableMap(this.instances); }

    /**
     * 開始一個新的副本實例。
     */
    public void startDungeon(final Player leader, final String dungeonId) {
        final DungeonDefinition def = this.definitions.get(dungeonId);
        if (def == null) {
            leader.sendMessage(this.msg("§c找不到副本定義：" + dungeonId));
            return;
        }
        final DungeonConfig cfg = def.config();
        // 權限檢查（傳統 + config）
        if (def.requiredPermission() != null && !leader.hasPermission(def.requiredPermission())) {
            leader.sendMessage(this.msg("§c你沒有進入此副本的權限。"));
            return;
        }
        for (final String perm : cfg.requiredPermissions()) {
            if (!perm.isBlank() && !leader.hasPermission(perm)) {
                leader.sendMessage(this.msg("§c你缺少權限：" + perm));
                return;
            }
        }
        // 是否已在副本中
        if (this.playerInstanceMap.containsKey(leader.getUniqueId())) {
            leader.sendMessage(this.msg("§c你已經在一個副本中了。"));
            return;
        }
        // 最大實例數檢查
        final long activeCount = this.instances.values().stream()
                .filter(i -> i.definition().id().equals(dungeonId) && i.isActive()).count();
        if (cfg.maxInstances() > 0 && activeCount >= cfg.maxInstances()) {
            leader.sendMessage(this.msg("§c此副本已達最大實例數量（" + cfg.maxInstances() + "）。"));
            return;
        }

        // 收集隊員
        DungeonParty party = this.getPlayerParty(leader.getUniqueId());
        final Set<UUID> members;
        if (party != null) {
            members = new LinkedHashSet<>(party.members());
        } else {
            members = new LinkedHashSet<>();
            members.add(leader.getUniqueId());
        }

        // 人數檢查（config 優先，定義次之）
        final int minP = cfg.minPartySize() > 0 ? cfg.minPartySize() : def.minPlayers();
        final int maxP = cfg.maxPartySize() > 0 ? cfg.maxPartySize() : def.maxPlayers();
        if (members.size() < minP) {
            leader.sendMessage(this.msg("§c人數不足！需要至少 " + minP + " 人。"));
            return;
        }
        if (members.size() > maxP) {
            leader.sendMessage(this.msg("§c人數超出！最多 " + maxP + " 人。"));
            return;
        }

        // 前置副本通關檢查
        if (!cfg.requiredDungeons().isEmpty()) {
            for (final UUID uuid : members) {
                final DungeonPlayerData pd = this.getOrCreatePlayerData(uuid);
                for (final String reqDungeon : cfg.requiredDungeons()) {
                    if (!pd.hasCleared(reqDungeon)) {
                        final Player p = Bukkit.getPlayer(uuid);
                        final String name = p != null ? p.getName() : uuid.toString();
                        leader.sendMessage(this.msg("§c" + name + " 尚未通關：" + reqDungeon));
                        return;
                    }
                }
            }
        }

        // 進入冷卻檢查（config 優先）
        final String todayKey = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        for (final UUID uuid : members) {
            final DungeonPlayerData pd = this.getOrCreatePlayerData(uuid);
            pd.resetDailyIfNeeded(todayKey);
            // 進入冷卻（Config 版）
            if (cfg.accessCooldownEnabled() && pd.isAccessOnCooldown(dungeonId, cfg.accessCooldownTime())) {
                final int remaining = pd.remainingAccessCooldownSeconds(dungeonId, cfg.accessCooldownTime());
                leader.sendMessage(this.msg("§c隊員進入冷卻中，剩餘 " + this.formatTime(remaining) + "。"));
                return;
            }
            // 傳統冷卻
            if (pd.isOnCooldown(dungeonId, def.cooldownSeconds())) {
                final int remaining = pd.remainingCooldownSeconds(dungeonId, def.cooldownSeconds());
                leader.sendMessage(this.msg("§c隊員冷卻中，剩餘 " + this.formatTime(remaining) + "。"));
                return;
            }
            if (pd.hasReachedDailyLimit(dungeonId, def.dailyLimit())) {
                leader.sendMessage(this.msg("§c隊員已達今日進入上限（" + def.dailyLimit() + " 次）。"));
                return;
            }
        }

        // 鑰匙檢查
        if (!cfg.accessKeyItems().isEmpty()) {
            final Set<UUID> toCheck = cfg.accessKeysLeaderOnly() ? Set.of(leader.getUniqueId()) : members;
            for (final UUID uuid : toCheck) {
                final Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                for (final String keyItem : cfg.accessKeyItems()) {
                    if (!this.playerHasKeyItem(p, keyItem)) {
                        leader.sendMessage(this.msg("§c缺少鑰匙物品：" + keyItem));
                        return;
                    }
                }
            }
            // 消耗鑰匙
            if (cfg.accessKeysConsume()) {
                for (final UUID uuid : toCheck) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    for (final String keyItem : cfg.accessKeyItems()) {
                        this.consumeKeyItem(p, keyItem);
                    }
                }
            }
        }

        // 費用檢查（簡化—須整合經濟系統）
        // cost > 0 的檢查會在此處完成如果有 Vault 整合

        // 建立實例
        final String instanceId = INSTANCE_PREFIX + this.instanceCounter.incrementAndGet();
        final DungeonInstance instance = new DungeonInstance(instanceId, def, leader.getUniqueId());
        for (final UUID uuid : members) {
            instance.addMember(uuid);
            this.playerInstanceMap.put(uuid, instanceId);
        }
        this.instances.put(instanceId, instance);

        // 設定進入冷卻（on-start 模式）
        if (cfg.accessCooldownEnabled() && cfg.accessCooldownOnStart()) {
            for (final UUID uuid : members) {
                this.getOrCreatePlayerData(uuid).setAccessCooldown(dungeonId, System.currentTimeMillis());
            }
        }

        // 非同步複製世界
        leader.sendMessage(this.msg("§e正在準備副本世界..."));
        this.scheduler.runAsync(() -> {
            try {
                this.copyTemplateWorld(def.templateWorld(), instanceId);
                // 切回主線程載入世界
                this.scheduler.runGlobal(task -> this.onWorldCopied(instance));
            } catch (final Exception e) {
                this.plugin.getLogger().log(Level.WARNING, "[副本] 複製世界失敗", e);
                this.scheduler.runGlobal(task -> {
                    this.broadcastToInstance(instance, "§c副本世界準備失敗：" + e.getMessage());
                    this.cleanupInstance(instance);
                });
            }
        });
    }

    /** 檢查玩家是否持有指定鑰匙物品。 */
    private boolean playerHasKeyItem(final Player player, final String keyItem) {
        // 嘗試依 Material 名稱匹配
        try {
            final Material mat = Material.valueOf(keyItem.toUpperCase());
            return player.getInventory().contains(mat);
        } catch (final Exception ignored) {}
        // 嘗試依科技材料 ID 匹配
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            if (meta.getPersistentDataContainer().has(
                    new NamespacedKey(this.plugin, "tech_item_id"), PersistentDataType.STRING)) {
                final String id = meta.getPersistentDataContainer().get(
                        new NamespacedKey(this.plugin, "tech_item_id"), PersistentDataType.STRING);
                if (keyItem.equals(id)) return true;
            }
        }
        return false;
    }

    /** 消耗玩家身上一個指定鑰匙物品。 */
    private void consumeKeyItem(final Player player, final String keyItem) {
        try {
            final Material mat = Material.valueOf(keyItem.toUpperCase());
            player.getInventory().removeItem(new ItemStack(mat, 1));
            return;
        } catch (final Exception ignored) {}
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) continue;
            if (meta.getPersistentDataContainer().has(
                    new NamespacedKey(this.plugin, "tech_item_id"), PersistentDataType.STRING)) {
                final String id = meta.getPersistentDataContainer().get(
                        new NamespacedKey(this.plugin, "tech_item_id"), PersistentDataType.STRING);
                if (keyItem.equals(id)) {
                    item.setAmount(item.getAmount() - 1);
                    return;
                }
            }
        }
    }

    private void onWorldCopied(final DungeonInstance instance) {
        final DungeonDefinition def = instance.definition();
        final DungeonConfig cfg = def.config();
        final String worldName = instance.instanceId();

        // 載入世界 — 使用 NMS 繞過 Folia 限制
        World world = null;
        try {
            world = com.rui.techproject.util.NmsWorldUtil.loadWorld(worldName, this.plugin.getLogger());
        } catch (final Exception e) {
            this.plugin.getLogger().warning("[副本] NMS 世界載入失敗：" + e.getMessage());
        }
        if (world == null) {
            this.broadcastToInstance(instance, "§c載入副本世界失敗！此伺服器可能不支援動態世界建立。");
            this.cleanupInstance(instance);
            return;
        }

        world.setAutoSave(false);
        world.setDifficulty(Difficulty.HARD);

        // 依照 Config 規則設定 GameRule
        final DungeonConfig.DungeonRules rules = cfg.rules();
        world.setGameRule(GameRule.DO_MOB_SPAWNING, rules.spawnMobs());
        world.setGameRule(GameRule.MOB_GRIEFING, !rules.preventExplosionBlockDamage());
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true); // 我們自己處理物品邏輯
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, !rules.hideDeathMessages());
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, rules.disableRandomTick() ? 0 : 3);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, true);
        world.setTime(6000L); // 正午

        // 設定世界出生點為副本生成點，確保重生時的 fallback 位置正確
        final double[] defSpawn = def.spawnPoint();
        world.setSpawnLocation((int) defSpawn[0], (int) defSpawn[1], (int) defSpawn[2]);

        instance.setInstanceWorld(world);

        // 初始化生命系統
        final int lives = cfg.playerLives();
        if (lives > 0) {
            for (final UUID uuid : instance.members()) {
                instance.initPlayerLives(uuid, lives);
            }
        }

        // 開始倒數或大廳等待
        if (cfg.lobbyPoint() != null) {
            instance.setState(State.PREPARING);
            instance.setInLobby(true);
        } else {
            instance.setState(State.COUNTDOWN);
            instance.setCountdownSeconds(COUNTDOWN_SECONDS);
        }

        // 記錄進入時間 + 每日次數
        final String todayKey = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        for (final UUID uuid : instance.members()) {
            final DungeonPlayerData pd = this.getOrCreatePlayerData(uuid);
            pd.setLastEntryTime(def.id(), System.currentTimeMillis());
            pd.resetDailyIfNeeded(todayKey);
            pd.incrementDailyEntry(def.id());
        }

        // 傳送玩家
        final World instanceWorld = world;
        final double[] sp;
        if (instance.isInLobby() && cfg.lobbyPoint() != null) {
            sp = cfg.lobbyPoint();
        } else {
            sp = def.spawnPoint();
        }
        final Location spawn = new Location(instanceWorld, sp[0], sp[1], sp[2],
                sp.length > 3 ? (float) sp[3] : 0f, sp.length > 4 ? (float) sp[4] : 0f);

        for (final UUID uuid : instance.members()) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                instance.setReturnLocation(uuid, player.getLocation());
                // 保存 / 清除物品欄
                if (!cfg.keepInventoryOnEnter()) {
                    player.getInventory().clear();
                }
                if (!cfg.keepHealthOnEnter()) {
                    final var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                    if (maxHealth != null) player.setHealth(maxHealth.getValue());
                }
                if (!cfg.keepFoodOnEnter()) {
                    player.setFoodLevel(20);
                    player.setSaturation(20f);
                }
                if (!cfg.keepPotionEffectsOnEnter()) {
                    for (final PotionEffect effect : player.getActivePotionEffects()) {
                        player.removePotionEffect(effect.getType());
                    }
                }
                if (!cfg.keepExpOnEnter()) {
                    player.setLevel(0);
                    player.setExp(0f);
                }
                // 設定遊戲模式的目標值（傳送後在正確的 region thread 上設定）
                GameMode gm;
                try {
                    gm = GameMode.valueOf(cfg.gamemode().toUpperCase());
                } catch (final Exception ignored) {
                    gm = GameMode.SURVIVAL;
                }
                final GameMode finalGameMode = gm;
                this.teleportPlayerSafely(player, spawn, finalGameMode);
            }
        }

        // 大廳提示 or 倒數提示
        if (instance.isInLobby()) {
            this.broadcastToInstance(instance, "§a副本已準備完成！請在大廳準備就緒。使用 §f/tech dungeon ready §a確認準備。");
        } else {
            this.broadcastToInstance(instance, "§a副本已準備完成！倒數開始...");
        }
        // 開始 title
        if (cfg.showTitleOnStart()) {
            this.broadcastTitle(instance,
                RichText.parse(def.displayName()),
                RichText.parse(def.description()),
                10, 40, 10);
        }
    }

    /**
     * 離開副本。
     */
    public void leaveDungeon(final Player player) {
        final String instanceId = this.playerInstanceMap.get(player.getUniqueId());
        if (instanceId == null) {
            player.sendMessage(this.msg("§c你不在任何副本中。"));
            return;
        }
        final DungeonInstance instance = this.instances.get(instanceId);
        if (instance == null) {
            this.playerInstanceMap.remove(player.getUniqueId());
            this.clearRegionState(instanceId, player.getUniqueId());
            return;
        }
        // 設定進入冷卻（on-leave 模式）
        final DungeonConfig cfg = instance.definition().config();
        if (cfg.accessCooldownEnabled() && cfg.accessCooldownOnLeave()) {
            this.getOrCreatePlayerData(player.getUniqueId()).setAccessCooldown(
                    instance.definition().id(), System.currentTimeMillis());
        }
        this.removePlayerFromInstance(player, instance);
        player.sendMessage(this.msg("§e你已離開副本。"));
        // 功能引擎：LEAVE_DUNGEON_LISTENER 觸發
        this.functionEngine.onPlayerLeave(instance, player);
        // 觸發腳本
        this.triggerScripts(instance, "leave_dungeon", Map.of("player", player.getUniqueId()));
        if (instance.members().isEmpty()) {
            this.forceCloseInstance(instance, "所有玩家離開");
        }
    }

    private void removePlayerFromInstance(final Player player, final DungeonInstance instance) {
        final UUID uuid = player.getUniqueId();
        instance.removeMember(uuid);
        this.playerInstanceMap.remove(uuid);
        this.clearRegionState(instance.instanceId(), uuid);
        // 移除 BossBar
        final BossBar bar = this.bossBars.get(instance.instanceId());
        if (bar != null) bar.removeViewer(player);
        // 清除副本世界的重生點（床/錨），避免 Folia 跨世界檢查床方塊導致錯誤重生
        this.clearDungeonRespawnPoint(player);
        // 傳回原位
        final Location returnLoc = instance.getReturnLocation(uuid);
        if (returnLoc != null && returnLoc.getWorld() != null) {
            player.teleportAsync(returnLoc);
        } else {
            player.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    /**
     * 清除玩家的重生點（如果指向副本世界）。
     * Folia 下玩家的 respawn point 如果指向 dungeon_instance_* 世界，
     * 會導致 findRespawnAndUseSpawnBlock 跨世界存取方塊失敗。
     */
    private void clearDungeonRespawnPoint(final Player player) {
        try {
            final Location respawn = player.getRespawnLocation();
            if (respawn != null && respawn.getWorld() != null) {
                final String wn = respawn.getWorld().getName();
                if (wn.startsWith(INSTANCE_PREFIX) || wn.startsWith(EDIT_PREFIX)) {
                    player.setRespawnLocation(null, true);
                    this.plugin.getLogger().info("[副本] 已清除 " + player.getName() + " 指向副本/編輯世界的重生點 (" + wn + ")");
                }
            }
        } catch (final Exception ignored) {
            try {
                player.setRespawnLocation(null, true);
            } catch (final Exception ignored2) { /* 安靠忽略 */ }
        }
    }

    /**
     * 檢查重生位置是否指向副本世界（供 TechListener 呼叫）。
     * 如果是，回傳主世界出生點；否則回傳 null。
     */
    public Location sanitizeRespawnLocation(final Location loc) {
        if (loc != null && loc.getWorld() != null) {
            final String wn = loc.getWorld().getName();
            if (wn.startsWith(INSTANCE_PREFIX) || wn.startsWith(EDIT_PREFIX)) {
                return Bukkit.getWorlds().get(0).getSpawnLocation();
            }
        }
        return null;
    }

    /** 是否顯示波次文字/音效提示（預設關閉，交給腳本自行提示）。 */
    private boolean shouldShowWaveAnnouncements() {
        return this.plugin.getConfig().getBoolean("dungeon.show-wave-announcements", false);
    }

    /** 是否顯示副本進度 ActionBar（預設關閉，避免 timer/wave 提示干擾）。 */
    private boolean shouldShowRuntimeActionBar() {
        return this.plugin.getConfig().getBoolean("dungeon.show-runtime-actionbar", false);
    }

    /** 是否由腳本控制波次推進（存在 start_wave 動作即視為腳本驅動）。 */
    private boolean usesScriptDrivenWaveProgression(final DungeonInstance instance) {
        for (final ScriptDefinition script : instance.definition().scripts()) {
            for (final ScriptAction action : script.actions()) {
                if ("start_wave".equalsIgnoreCase(action.type())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 統一追蹤副本實體，後續清理與偵測都依賴此清單。 */
    private void trackEntityForInstance(final DungeonInstance instance, final Entity entity) {
        if (entity == null) {
            return;
        }
        instance.trackEntity(entity);
    }

    /** 安全傳送：先找安全落點，再傳送，最後補一次反卡牆校正。 */
    private void teleportPlayerSafely(final Player player, final Location preferred) {
        this.teleportPlayerSafely(player, preferred, null);
    }

    /** 安全傳送 + 可選遊戲模式設定。 */
    private void teleportPlayerSafely(final Player player, final Location preferred, final GameMode targetGameMode) {
        if (player == null || preferred == null || preferred.getWorld() == null) {
            return;
        }
        final World world = preferred.getWorld();
        if (world == null) {
            return;
        }

        world.getChunkAtAsync(preferred).thenAccept(chunk -> {
            this.scheduler.runRegion(preferred, task -> {
                final Location safeTarget = this.findNearestSafeLocation(preferred);
                this.scheduler.runEntity(player, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    player.teleportAsync(safeTarget).thenAccept(success -> {
                        this.scheduler.runEntity(player, () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            if (!success) {
                                player.sendMessage(this.msg("§c傳送到副本失敗，正在重試..."));
                                player.teleportAsync(safeTarget).thenAccept(retry -> {
                                    this.scheduler.runEntity(player, () -> {
                                        if (!retry || !player.isOnline()) {
                                            return;
                                        }
                                        if (targetGameMode != null) {
                                            player.setGameMode(targetGameMode);
                                        }
                                        this.scheduleTeleportSafetyCheck(player);
                                    });
                                });
                                return;
                            }

                            if (targetGameMode != null) {
                                player.setGameMode(targetGameMode);
                            }
                            this.scheduleTeleportSafetyCheck(player);
                        });
                    });
                });
            });
        });
    }

    private void scheduleTeleportSafetyCheck(final Player player) {
        this.scheduler.runEntityDelayed(player, () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            final Location current = player.getLocation();
            if (this.isLocationUnsafe(current)) {
                final Location safe = this.findNearestSafeLocation(current);
                player.teleportAsync(safe);
            }
        }, 2L);
    }

    /** 判斷一個位置是否可能讓玩家卡牆/窒息。 */
    private boolean isLocationUnsafe(final Location location) {
        if (location == null || location.getWorld() == null) {
            return true;
        }
        final World world = location.getWorld();
        final int x = location.getBlockX();
        final int y = location.getBlockY();
        final int z = location.getBlockZ();
        return !this.isSafeStandBlock(world, x, y, z);
    }

    /** 從指定點附近找最近可站立位置。 */
    private Location findNearestSafeLocation(final Location preferred) {
        if (preferred == null || preferred.getWorld() == null) {
            return preferred;
        }
        final World world = preferred.getWorld();
        final float yaw = preferred.getYaw();
        final float pitch = preferred.getPitch();

        final int baseX = preferred.getBlockX();
        final int baseZ = preferred.getBlockZ();
        final int minY = world.getMinHeight() + 1;
        final int maxY = world.getMaxHeight() - 2;
        final int baseY = Math.max(minY, Math.min(maxY, preferred.getBlockY()));

        for (int radius = 0; radius <= 4; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    final int x = baseX + dx;
                    final int z = baseZ + dz;
                    for (int y = baseY; y <= Math.min(baseY + 8, maxY); y++) {
                        if (this.isSafeStandBlock(world, x, y, z)) {
                            return new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
                        }
                    }
                    for (int y = baseY - 1; y >= Math.max(baseY - 6, minY); y--) {
                        if (this.isSafeStandBlock(world, x, y, z)) {
                            return new Location(world, x + 0.5, y, z + 0.5, yaw, pitch);
                        }
                    }
                }
            }
        }

        int fallbackY = Math.max(minY, Math.min(maxY, world.getHighestBlockYAt(baseX, baseZ) + 1));
        if (!this.isSafeStandBlock(world, baseX, fallbackY, baseZ)) {
            for (int y = maxY; y >= minY; y--) {
                if (this.isSafeStandBlock(world, baseX, y, baseZ)) {
                    fallbackY = y;
                    break;
                }
            }
        }
        return new Location(world, baseX + 0.5, fallbackY, baseZ + 0.5, yaw, pitch);
    }

    private boolean isSafeStandBlock(final World world, final int x, final int y, final int z) {
        if (world == null) {
            return false;
        }
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 1) {
            return false;
        }
        final Block feet = world.getBlockAt(x, y, z);
        final Block head = world.getBlockAt(x, y + 1, z);
        final Block floor = world.getBlockAt(x, y - 1, z);
        if (!feet.isPassable() || !head.isPassable()) {
            return false;
        }
        if (feet.isLiquid() || head.isLiquid()) {
            return false;
        }
        return !floor.isPassable() && !floor.isLiquid();
    }

    /** 清理怪物死亡後可能殘留的模型附屬實體（BetterModel/Display 類）。 */
    private void cleanupModelArtifactsNearDeath(final DungeonInstance instance, final Entity deadEntity) {
        if (deadEntity == null || deadEntity.getWorld() == null) {
            return;
        }
        this.removePassengersRecursive(deadEntity);

        final Location deathLoc = deadEntity.getLocation().clone();
        final Set<String> sourceTags = new HashSet<>(deadEntity.getScoreboardTags());
        final Runnable cleanupTask = () -> this.scheduler.runRegion(deathLoc,
                task -> this.removeDetachedModelArtifacts(instance, deathLoc, sourceTags));
        cleanupTask.run();
        this.scheduler.runGlobalDelayed(task -> cleanupTask.run(), 10L);
    }

    private void removePassengersRecursive(final Entity root) {
        if (root == null) {
            return;
        }
        for (final Entity passenger : new ArrayList<>(root.getPassengers())) {
            this.removePassengersRecursive(passenger);
            if (passenger.isValid()) {
                passenger.remove();
            }
        }
    }

    private void removeDetachedModelArtifacts(final DungeonInstance instance,
                                              final Location deathLoc,
                                              final Set<String> sourceTags) {
        final World world = instance.instanceWorld();
        if (world == null || deathLoc.getWorld() == null || !deathLoc.getWorld().equals(world)) {
            return;
        }
        for (final Entity nearby : world.getNearbyEntities(deathLoc, 4.0, 3.0, 4.0)) {
            if (nearby == null || nearby instanceof Player || !nearby.isValid()) {
                continue;
            }
            if (instance.spawnedEntities().contains(nearby)) {
                continue;
            }
            if (!this.isLikelyModelArtifact(nearby, sourceTags)) {
                continue;
            }
            nearby.remove();
        }
    }

    private boolean isLikelyModelArtifact(final Entity entity, final Set<String> sourceTags) {
        final Set<String> tags = entity.getScoreboardTags();
        final boolean sharesRootTag = !sourceTags.isEmpty() && tags.stream().anyMatch(sourceTags::contains);
        final boolean hasModelKeyword = tags.stream()
                .map(tag -> tag.toLowerCase(Locale.ROOT))
                .anyMatch(tag -> tag.contains("model") || tag.contains("mythic") || tag.contains("better"));

        final String type = entity.getType().name();
        final boolean helperType = "ARMOR_STAND".equals(type)
                || "INTERACTION".equals(type)
                || type.endsWith("_DISPLAY");
        if (helperType) {
            return sharesRootTag || hasModelKeyword;
        }

        if (entity instanceof LivingEntity living) {
            return (sharesRootTag || hasModelKeyword)
                    && !living.hasAI()
                    && (living.isInvisible() || entity.isInvulnerable());
        }
        return false;
    }

    // ──────────────────────────────────────────────
    //  Tick 循環
    // ──────────────────────────────────────────────

    private void tickAllInstances() {
        for (final DungeonInstance instance : new ArrayList<>(this.instances.values())) {
            try {
                this.tickInstance(instance);
            } catch (final Exception e) {
                this.plugin.getLogger().log(Level.WARNING, "[副本] tick 異常 " + instance.instanceId(), e);
            }
        }
    }

    private void tickInstance(final DungeonInstance instance) {
        switch (instance.state()) {
            case PREPARING -> this.tickLobby(instance);
            case COUNTDOWN -> this.tickCountdown(instance);
            case RUNNING, BOSS_FIGHT -> this.tickRunning(instance);
            case COMPLETED -> this.tickCompleted(instance);
            case FAILED -> this.tickFailed(instance);
            case CLOSING -> {} // 等待排程清理
            default -> {}
        }
    }

    /** Tick 大廳等待（PREPARING + inLobby）。 */
    private void tickLobby(final DungeonInstance instance) {
        if (!instance.isInLobby()) return;
        instance.incrementTicks();
        // 每 5 秒顯示準備狀態
        if (instance.elapsedTicks() % 100 == 0) {
            final int total = instance.members().size();
            final int ready = (int) instance.readyState().values().stream().filter(Boolean.TRUE::equals).count();
            this.broadcastToInstance(instance, "§e準備狀態：§a" + ready + "§7/" + total
                    + "  §f使用 §a/tech dungeon ready §f確認準備");
        }
    }

    private void tickCountdown(final DungeonInstance instance) {
        final int countdown = instance.countdownSeconds();
        if (countdown > 0) {
            this.broadcastTitle(instance,
                Component.text(String.valueOf(countdown), NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                RichText.mini("<#9AD4FF>副本即將開始"),
                5, 15, 5);
            this.broadcastSound(instance, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f + (COUNTDOWN_SECONDS - countdown) * 0.1f);
            instance.decrementCountdown();
            return;
        }
        // 倒數結束，開始
        instance.setState(State.RUNNING);
        instance.setStartTimeMillis(System.currentTimeMillis());
        // 初始化並啟動功能引擎
        this.functionEngine.initInstance(instance);
        this.functionEngine.onDungeonStart(instance);
        this.broadcastTitle(instance,
            RichText.mini("<gradient:#8BFFB0:#22C55E><bold>開始！</bold></gradient>"),
            Component.empty(), 0, 20, 10);
        this.broadcastSound(instance, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.2f);
        // 啟動第一波怪物
        if (!instance.definition().waves().isEmpty()) {
            if (this.usesScriptDrivenWaveProgression(instance)) {
                instance.setCurrentWave(-1);
                instance.setAliveMobCount(0);
            } else {
                this.startWave(instance, 0);
            }
        }
    }

    private void tickRunning(final DungeonInstance instance) {
        instance.incrementTicks();
        // 功能引擎 tick（PLAYER_DETECTOR 等週期觸發）
        this.functionEngine.tick(instance);
        // 超時判定
        if (instance.isTimedOut()) {
            this.broadcastToInstance(instance, "§c§l時間到！副本失敗！");
            instance.setState(State.FAILED);
            return;
        }
        // 更新存活怪物數量
        instance.removeDeadEntities();
        int alive = 0;
        for (final Entity e : instance.spawnedEntities()) {
            if (e instanceof LivingEntity le && !le.isDead()) alive++;
        }
        instance.setAliveMobCount(alive);

        // 波次清完推進
        if (instance.state() == State.RUNNING && instance.currentWave() >= 0 && instance.isWaveCleared()) {
            this.onWaveCleared(instance, instance.currentWave());
        }

        // Boss 血量檢查
        if (instance.state() == State.BOSS_FIGHT && instance.hasBoss()) {
            this.tickBoss(instance);
        } else if (instance.state() == State.BOSS_FIGHT && !instance.hasBoss()) {
            // Boss 已死
            this.onBossDeath(instance);
        }

        // Actionbar 計時顯示
        this.updateActionBar(instance);

        // 走到離開點自動離開（MythicDungeons 習慣行為）
        this.tickExitPointLeave(instance);

        // 檢查是否所有人都離線/死亡
        boolean anyAlive = false;
        for (final UUID uuid : instance.members()) {
            final Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline() && !p.isDead()) { anyAlive = true; break; }
        }
        if (!anyAlive && instance.isActive()) {
            // 給一個寬限，不在此立即失敗（透過 player_death 腳本處理）
        }
    }

    private void tickCompleted(final DungeonInstance instance) {
        // 通關後等待幾秒再關閉
        instance.incrementTicks();
        if (instance.elapsedTicks() % 20 == 0 && !instance.isCleared()) {
            instance.setCleared(true);
            this.distributeRewards(instance);
        }
        if (instance.elapsedTicks() >= CLOSE_DELAY_TICKS) {
            this.beginClose(instance);
        }
    }

    private void tickFailed(final DungeonInstance instance) {
        instance.incrementTicks();
        if (instance.elapsedTicks() >= CLOSE_DELAY_TICKS) {
            this.beginClose(instance);
        }
    }

    // ──────────────────────────────────────────────
    //  波次系統
    // ──────────────────────────────────────────────

    public void startWave(final DungeonInstance instance, final int waveIndex) {
        final DungeonDefinition def = instance.definition();
        if (waveIndex < 0 || waveIndex >= def.waves().size()) {
            this.plugin.getLogger().info("[副本] startWave(" + waveIndex + ") 超出範圍，waves.size=" + def.waves().size());
            return;
        }
        final WaveDefinition wave = def.waves().get(waveIndex);
        instance.setCurrentWave(waveIndex);
        instance.setWaveSpawning(true); // 防止 tickRunning 誤判波次已清空
        this.plugin.getLogger().info("[副本] 啟動波次 " + waveIndex + "，怪物種類數=" + wave.mobEntries().size());

        if (this.shouldShowWaveAnnouncements() && wave.message() != null && !wave.message().isBlank()) {
            this.broadcastToInstance(instance, wave.message());
        }

        // 延遲生怪
        final int delayTicks = wave.spawnDelay() * 20;
        final World world = instance.instanceWorld();
        if (world == null) {
            this.plugin.getLogger().warning("[副本] startWave 時 instanceWorld 為 null！");
            return;
        }
        final double[] sp = wave.spawnLocation() != null ? wave.spawnLocation() : def.spawnPoint();
        final Location baseSpawn = new Location(world, sp[0], sp[1], sp[2]);

        this.scheduler.runGlobalDelayed(task -> {
            this.plugin.getLogger().info("[副本] 延遲生怪開始，波次=" + waveIndex + "，基準點=" + baseSpawn);
            // 先計算總生成數來正確設定 aliveMobCount
            int totalMobs = 0;
            for (final MobEntry me : wave.mobEntries()) totalMobs += me.count();
            instance.setAliveMobCount(totalMobs);
            for (final MobEntry entry : wave.mobEntries()) {
                this.plugin.getLogger().info("[副本] 生怪：type=" + entry.entityType()
                        + " mythic=" + entry.mythicMobId() + " count=" + entry.count());
                this.spawnMobs(instance, baseSpawn, entry);
            }
            instance.setWaveSpawning(false); // 生成完成，允許波次清空判定
        }, Math.max(1, delayTicks));
    }

    private void spawnMobs(final DungeonInstance instance, final Location base, final MobEntry entry) {
        final World world = instance.instanceWorld();
        if (world == null) return;
        final double[] offset = entry.spawnOffset();
        final Location spawnLoc = base.clone().add(offset[0], offset.length > 1 ? offset[1] : 0, offset.length > 2 ? offset[2] : 0);

        for (int i = 0; i < entry.count(); i++) {
            final Location loc = spawnLoc.clone().add(
                ThreadLocalRandom.current().nextDouble(-2, 2), 0,
                ThreadLocalRandom.current().nextDouble(-2, 2));
            this.scheduler.runRegion(loc, task -> {
                try {
                    // 優先嘗試 MythicMobs
                    if (entry.mythicMobId() != null && !entry.mythicMobId().isEmpty()) {
                        final Entity mythicEntity = this.functionEngine.spawnMythicMobEntity(entry.mythicMobId(), loc, entry.level());
                        if (mythicEntity != null) {
                            this.trackEntityForInstance(instance, mythicEntity);
                            return; // MythicMobs 生成成功
                        }
                        // MythicMobs 不可用，fallback 到原版
                    }
                    final EntityType type = EntityType.valueOf(entry.entityType().toUpperCase());
                    final Entity entity = world.spawnEntity(loc, type);
                    if (entity instanceof LivingEntity living) {
                        if (entry.customName() != null) {
                            living.customName(Component.text(entry.customName()));
                            living.setCustomNameVisible(true);
                        }
                        // 等級加成
                        final double levelMul = 1.0 + (entry.level() - 1) * 0.3;
                        final var maxHealthAttr = living.getAttribute(Attribute.MAX_HEALTH);
                        if (maxHealthAttr != null) {
                            maxHealthAttr.setBaseValue(maxHealthAttr.getBaseValue() * levelMul);
                            living.setHealth(maxHealthAttr.getBaseValue());
                        }
                        living.setRemoveWhenFarAway(false);
                        living.setPersistent(true);
                    }
                    this.trackEntityForInstance(instance, entity);
                } catch (final Exception e) {
                    this.plugin.getLogger().warning("[副本] 生怪失敗：" + e.getMessage());
                }
            });
        }
    }

    private void onWaveCleared(final DungeonInstance instance, final int waveIndex) {
        if (this.shouldShowWaveAnnouncements()) {
            this.broadcastToInstance(instance, "§a波次 " + (waveIndex + 1) + " 已清除！");
            this.broadcastSound(instance, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
        }

        // 觸發波次清除腳本
        this.triggerScripts(instance, "wave_clear", Map.of("wave", waveIndex));

        // 若此副本由腳本自行控制波次，清完後交回腳本/偵測器推進。
        if (this.usesScriptDrivenWaveProgression(instance)) {
            instance.setCurrentWave(-1);
            instance.setAliveMobCount(0);
            return;
        }

        // 檢查是否有波次後 Boss
        for (final BossDefinition boss : instance.definition().bosses()) {
            if (boss.afterWave() == waveIndex) {
                this.spawnBoss(instance, boss);
                return;
            }
        }

        // 自動推進下一波
        if (instance.hasMoreWaves()) {
            this.startWave(instance, waveIndex + 1);
            return;
        }

        // 沒有下一波時：若沒有待生成的自動 Boss，直接通關。
        // 這可避免「最後一波已清除」在每個 tick 重複刷頻。
        final boolean hasPendingAutoBoss = instance.definition().bosses().stream()
                .anyMatch(b -> b.afterWave() > waveIndex);
        if (!hasPendingAutoBoss && instance.state() == State.RUNNING) {
            this.completeDungeon(instance);
        }
    }

    private void tickExitPointLeave(final DungeonInstance instance) {
        final double[] exitPoint = instance.definition().exitPoint();
        if (exitPoint == null || exitPoint.length < 3) {
            return;
        }
        final World world = instance.instanceWorld();
        if (world == null) {
            return;
        }
        final Location exit = new Location(world, exitPoint[0], exitPoint[1], exitPoint[2]);
        final double triggerRadius = 1.5;
        final double triggerRadiusSquared = triggerRadius * triggerRadius;

        final List<Player> leavingPlayers = new ArrayList<>();
        for (final UUID uuid : new ArrayList<>(instance.members())) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || player.isDead()) {
                continue;
            }
            if (!player.getWorld().equals(world)) {
                continue;
            }
            if (player.getLocation().distanceSquared(exit) <= triggerRadiusSquared) {
                leavingPlayers.add(player);
            }
        }

        for (final Player player : leavingPlayers) {
            player.sendMessage(this.msg("§e你已抵達離開點，正在離開副本。"));
            this.leaveDungeon(player);
        }
    }

    // ──────────────────────────────────────────────
    //  Boss 系統
    // ──────────────────────────────────────────────

    public void spawnBoss(final DungeonInstance instance, final BossDefinition bossDef) {
        final World world = instance.instanceWorld();
        if (world == null) return;
        final double[] bp = bossDef.spawnPoint();
        final Location loc = new Location(world, bp[0], bp[1], bp.length > 2 ? bp[2] : 0);

        instance.setState(State.BOSS_FIGHT);
        this.broadcastTitle(instance,
            RichText.parse(bossDef.displayName()),
            RichText.mini("<gradient:#FF8A80:#EF4444><bold>Boss 出現了！</bold></gradient>"),
            10, 40, 10);
        this.broadcastSound(instance, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);

        this.scheduler.runRegion(loc, task -> {
            try {
                final EntityType type = EntityType.valueOf(bossDef.entityType().toUpperCase());
                final Entity entity = world.spawnEntity(loc, type);
                if (entity instanceof LivingEntity living) {
                    living.customName(RichText.parse(bossDef.displayName()));
                    living.setCustomNameVisible(true);
                    living.setPersistent(true);
                    living.setRemoveWhenFarAway(false);
                    final var maxHealthAttr = living.getAttribute(Attribute.MAX_HEALTH);
                    if (maxHealthAttr != null) {
                        maxHealthAttr.setBaseValue(bossDef.health());
                        living.setHealth(bossDef.health());
                    }
                    final var attackAttr = living.getAttribute(Attribute.ATTACK_DAMAGE);
                    if (attackAttr != null) {
                        attackAttr.setBaseValue(bossDef.damage());
                    }
                    instance.setActiveBoss(living, bossDef.id());
                    this.trackEntityForInstance(instance, entity);

                    // BossBar
                    if (bossDef.bossBar()) {
                        final BossBar bar = BossBar.bossBar(
                            RichText.parse(bossDef.displayName()),
                            1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
                        this.bossBars.put(instance.instanceId(), bar);
                        for (final UUID uuid : instance.members()) {
                            final Player p = Bukkit.getPlayer(uuid);
                            if (p != null) bar.addViewer(p);
                        }
                    }
                }
            } catch (final Exception e) {
                this.plugin.getLogger().warning("[副本] Boss 生成失敗：" + e.getMessage());
            }
        });
    }

    private void tickBoss(final DungeonInstance instance) {
        final LivingEntity boss = instance.activeBoss();
        if (boss == null || boss.isDead()) return;
        final String bossId = instance.activeBossId();
        final BossDefinition bossDef = instance.definition().bosses().stream()
            .filter(b -> b.id().equals(bossId)).findFirst().orElse(null);
        if (bossDef == null) return;

        final double maxHealth = boss.getAttribute(Attribute.MAX_HEALTH) != null
            ? boss.getAttribute(Attribute.MAX_HEALTH).getBaseValue() : bossDef.health();
        final double healthRatio = boss.getHealth() / maxHealth;

        // 更新 BossBar
        final BossBar bar = this.bossBars.get(instance.instanceId());
        if (bar != null) {
            bar.progress(Math.max(0f, Math.min(1f, (float) healthRatio)));
            bar.name(Component.text(bossDef.displayName() + " §c" +
                (int) boss.getHealth() + "§7/§c" + (int) maxHealth));
        }

        // 階段檢查
        for (int i = 0; i < bossDef.phases().size(); i++) {
            final BossPhase phase = bossDef.phases().get(i);
            if (healthRatio <= phase.healthThreshold() && instance.currentPhaseIndex() < i) {
                instance.setCurrentPhaseIndex(i);
                this.activateBossPhase(instance, boss, bossDef, phase);
            }
        }

        // 技能 tick（基於 instance elapsed ticks）
        for (final BossSkill skill : bossDef.skills()) {
            if (instance.elapsedTicks() % skill.cooldownTicks() == 0) {
                this.executeBossSkill(instance, boss, skill);
            }
        }
    }

    private void activateBossPhase(final DungeonInstance instance, final LivingEntity boss,
                                    final BossDefinition bossDef, final BossPhase phase) {
        if (phase.message() != null) {
            this.broadcastToInstance(instance, phase.message());
        }
        // 傷害 / 速度加成
        final var attackAttr = boss.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attackAttr != null) {
            attackAttr.setBaseValue(bossDef.damage() * phase.damageMultiplier());
        }
        final var speedAttr = boss.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(speedAttr.getBaseValue() * phase.speedMultiplier());
        }
        // 增援
        if (phase.reinforcements() != null) {
            final double[] sp = instance.definition().spawnPoint();
            final Location base = new Location(instance.instanceWorld(), sp[0], sp[1], sp[2]);
            for (final MobEntry entry : phase.reinforcements()) {
                this.spawnMobs(instance, boss.getLocation(), entry);
            }
        }
        // 特效
        this.broadcastSound(instance, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.6f);
        final World world = instance.instanceWorld();
        if (world != null) {
            world.spawnParticle(Particle.EXPLOSION, boss.getLocation(), 5, 1, 1, 1, 0);
        }
    }

    private void executeBossSkill(final DungeonInstance instance, final LivingEntity boss, final BossSkill skill) {
        if (skill.message() != null) {
            this.broadcastToInstance(instance, skill.message());
        }
        switch (skill.type().toLowerCase()) {
            case "aoe_damage" -> {
                for (final UUID uuid : instance.members()) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p == null || p.isDead()) continue;
                    if (p.getLocation().distance(boss.getLocation()) <= skill.radius()) {
                        p.damage(skill.damage(), boss);
                    }
                }
                // 粒子
                final String particleName = String.valueOf(skill.params().getOrDefault("particle", "EXPLOSION"));
                final int count = toInt(skill.params().get("particle-count"), 20);
                try {
                    final Particle particle = Particle.valueOf(particleName.toUpperCase());
                    final World world = instance.instanceWorld();
                    if (world != null) {
                        world.spawnParticle(particle, boss.getLocation(), count, skill.radius() / 2, 1, skill.radius() / 2, 0.01);
                    }
                } catch (final Exception ignored) {}
            }
            case "summon" -> {
                final String mobType = toString(skill.params().get("mob-type"), "ZOMBIE");
                final int mobCount = toInt(skill.params().get("mob-count"), 2);
                final String mobName = toString(skill.params().get("mob-name"), null);
                final MobEntry entry = new MobEntry(mobType, null, mobCount, 1, new double[]{0, 1, 0}, mobName, Map.of());
                this.spawnMobs(instance, boss.getLocation(), entry);
            }
            case "line_damage" -> {
                // 朝目標方向發射直線傷害
                final int range = toInt(skill.params().get("range"), 15);
                Player nearest = null;
                double nearestDist = Double.MAX_VALUE;
                for (final UUID uuid : instance.members()) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p != null && !p.isDead() && p.getWorld().equals(boss.getWorld())) {
                        final double d = p.getLocation().distance(boss.getLocation());
                        if (d < nearestDist) { nearestDist = d; nearest = p; }
                    }
                }
                if (nearest != null && nearestDist <= range) {
                    nearest.damage(skill.damage(), boss);
                    // 光束粒子
                    final World world = instance.instanceWorld();
                    if (world != null) {
                        final Location start = boss.getEyeLocation();
                        final var direction = nearest.getLocation().toVector().subtract(start.toVector()).normalize();
                        for (double d = 0; d < nearestDist; d += 0.5) {
                            final Location point = start.clone().add(direction.clone().multiply(d));
                            world.spawnParticle(Particle.END_ROD, point, 1, 0, 0, 0, 0);
                        }
                    }
                }
            }
        }
    }

    private void onBossDeath(final DungeonInstance instance) {
        final String bossId = instance.activeBossId();
        this.broadcastToInstance(instance, "§a§lBoss 已被擊敗！");
        this.broadcastSound(instance, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

        // 移除 BossBar
        final BossBar bar = this.bossBars.remove(instance.instanceId());
        if (bar != null) {
            final List<Object> snap = new ArrayList<>();
            bar.viewers().forEach(snap::add);
            for (final var viewer : snap) {
                if (viewer instanceof net.kyori.adventure.audience.Audience a) bar.removeViewer(a);
            }
        }

        // 觸發 boss_death 腳本
        this.triggerScripts(instance, "boss_death", Map.of("boss_id", bossId != null ? bossId : ""));

        // 如果沒有更多波次且沒有更多 Boss，自動完成
        final boolean moreBosses = instance.definition().bosses().stream()
            .anyMatch(b -> !b.id().equals(bossId) && b.afterWave() > instance.currentWave());
        if (!moreBosses && !instance.hasMoreWaves()) {
            // 等待腳本可能呼叫 complete_dungeon
            if (instance.state() != State.COMPLETED) {
                this.scheduler.runGlobalDelayed(task -> {
                    if (instance.state() == State.BOSS_FIGHT) {
                        this.completeDungeon(instance);
                    }
                }, 100L);
            }
        } else {
            instance.setState(State.RUNNING);
            if (instance.hasMoreWaves()) {
                this.startWave(instance, instance.currentWave() + 1);
            }
        }
    }

    // ──────────────────────────────────────────────
    //  完成 / 失敗 / 關閉
    // ──────────────────────────────────────────────

    public void completeDungeon(final DungeonInstance instance) {
        if (instance.state() == State.COMPLETED || instance.state() == State.CLOSING) return;
        instance.setState(State.COMPLETED);
        instance.setCleared(false); // 會在 tickCompleted 中設為 true 並發獎勵
        this.broadcastTitle(instance,
            RichText.mini("<gradient:#A7F3D0:#22C55E><bold>通關！</bold></gradient>"),
            RichText.parse(instance.definition().displayName()),
            10, 60, 20);
        this.killInstanceEntities(instance);

        // 設定進入冷卻（on-finish 模式）
        final DungeonConfig cfg = instance.definition().config();
        if (cfg.accessCooldownEnabled() && cfg.accessCooldownOnFinish()) {
            for (final UUID uuid : instance.members()) {
                this.getOrCreatePlayerData(uuid).setAccessCooldown(
                        instance.definition().id(), System.currentTimeMillis());
            }
        }
        // 恢復觀戰者為生存模式
        for (final UUID uuid : instance.spectators()) {
            final Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setGameMode(GameMode.SURVIVAL);
            }
        }
    }

    public void failDungeon(final DungeonInstance instance) {
        if (instance.state() == State.FAILED || instance.state() == State.CLOSING) return;
        instance.setState(State.FAILED);
        final long savedTicks = instance.elapsedTicks();
        this.broadcastTitle(instance,
            RichText.mini("<gradient:#FCA5A5:#EF4444><bold>失敗</bold></gradient>"),
            RichText.parse(instance.definition().displayName()),
            10, 60, 20);
        this.killInstanceEntities(instance);
    }

    private void distributeRewards(final DungeonInstance instance) {
        final DungeonDefinition def = instance.definition();
        final int elapsedSeconds = instance.elapsedSeconds();
        final ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (final UUID uuid : instance.members()) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            final DungeonPlayerData pd = this.getOrCreatePlayerData(uuid);
            final boolean firstClear = !pd.hasCleared(def.id());
            pd.incrementClearCount(def.id());
            pd.updateBestTime(def.id(), elapsedSeconds);

            // 更新排行榜
            this.updateLeaderboard(def.id(), uuid, player.getName(), elapsedSeconds);

            player.sendMessage(this.msg("§a§l═══ 副本通關獎勵 ═══"));
            player.sendMessage(this.msg("§7通關時間：§f" + this.formatTime(elapsedSeconds)));
            if (firstClear) {
                player.sendMessage(this.msg("§6★ 首次通關！"));
            }

            for (final RewardDefinition reward : def.rewards()) {
                if (reward.firstClearOnly() && !firstClear) continue;
                if (rng.nextDouble() > reward.chance()) continue;
                this.giveReward(player, reward);
            }
            player.sendMessage(this.msg("§a§l═══════════════════"));
        }
    }

    private void giveReward(final Player player, final RewardDefinition reward) {
        switch (reward.type().toLowerCase()) {
            case "item" -> {
                try {
                    final Material mat = Material.valueOf(reward.value().toUpperCase());
                    final ItemStack stack = new ItemStack(mat, reward.amount());
                    player.getInventory().addItem(stack).values().forEach(overflow ->
                        player.getWorld().dropItemNaturally(player.getLocation(), overflow));
                    player.sendMessage(this.msg("§7 + §f" + reward.value() + " x" + reward.amount()));
                } catch (final Exception ignored) {}
            }
            case "tech_material", "tech_blueprint" -> {
                try {
                    String materialName = reward.value();
                    if (materialName.contains(":")) {
                        materialName = materialName.substring(materialName.indexOf(':') + 1);
                    }
                    final Material mat = Material.valueOf(materialName.toUpperCase(Locale.ROOT));
                    final ItemStack stack = new ItemStack(mat, reward.amount());
                    player.getInventory().addItem(stack).values().forEach(overflow ->
                            player.getWorld().dropItemNaturally(player.getLocation(), overflow));
                    player.sendMessage(this.msg("§7 + §f" + reward.value() + " x" + reward.amount()));
                } catch (final Exception ignored) {
                    this.plugin.getLogger().warning("[副本] 無法發放獎勵物品: " + reward.value());
                }
            }
            case "command" -> {
                final String cmd = reward.value().replace("{player}", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                player.sendMessage(this.msg("§7 + §f指令獎勵已發放"));
            }
            case "exp" -> {
                try {
                    player.giveExp(Integer.parseInt(reward.value()) * reward.amount());
                    player.sendMessage(this.msg("§7 + §f" + reward.value() + " 經驗值"));
                } catch (final NumberFormatException ignored) {}
            }
            case "money" -> {
                // TODO: 接入經濟插件
                player.sendMessage(this.msg("§7 + §f$" + reward.value()));
            }
        }
    }

    private void beginClose(final DungeonInstance instance) {
        if (instance.state() == State.CLOSING) return;
        instance.setState(State.CLOSING);
        // 傳送所有玩家回去
        for (final UUID uuid : new ArrayList<>(instance.members())) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                this.removePlayerFromInstance(player, instance);
            }
        }
        // 延遲清理世界
        this.scheduler.runGlobalDelayed(task -> this.cleanupInstance(instance), 40L);
    }

    private void forceCloseInstance(final DungeonInstance instance, final String reason) {
        this.killInstanceEntities(instance);
        this.broadcastToInstance(instance, "§c副本關閉：" + reason);
        this.beginClose(instance);
    }

    /**
     * Folia 安全版：透過 scheduler.runEntity 在每個實體自己的 region thread 上移除。
     */
    private void killInstanceEntities(final DungeonInstance instance) {
        for (final Entity entity : instance.spawnedEntities()) {
            if (entity != null && entity.isValid() && !entity.isDead()) {
                this.scheduler.runEntity(entity, () -> {
                    if (entity.isValid() && !entity.isDead()) entity.remove();
                });
            }
        }
        instance.spawnedEntities().clear();
    }

    private void cleanupInstance(final DungeonInstance instance) {
        final String instanceId = instance.instanceId();
        // 銷毀功能引擎上下文
        this.functionEngine.destroyInstance(instanceId);
        this.instances.remove(instanceId);
        this.playerRegionStates.remove(instanceId);
        // 移除 BossBar
        final BossBar bar = this.bossBars.remove(instanceId);
        if (bar != null) {
            final List<Object> snap = new ArrayList<>();
            bar.viewers().forEach(snap::add);
            for (final var viewer : snap) {
                if (viewer instanceof net.kyori.adventure.audience.Audience a) bar.removeViewer(a);
            }
        }
        // 清除玩家映射
        for (final UUID uuid : instance.members()) {
            this.playerInstanceMap.remove(uuid);
        }
        // 疏散殘留玩家 → 卸載並刪除世界
        final World world = instance.instanceWorld();
        if (world != null) {
            final World fb = Bukkit.getWorlds().get(0);
            final List<CompletableFuture<Boolean>> tp = new ArrayList<>();
            for (final Player p : world.getPlayers()) {
                // 清除可能指向此副本世界的重生點，避免重連後被送回
                this.clearDungeonRespawnPoint(p);
                tp.add(p.teleportAsync(fb.getSpawnLocation()));
            }
            final Runnable doClean = () -> this.scheduler.runGlobal(task -> {
                try {
                    Bukkit.unloadWorld(world, false);
                } catch (final UnsupportedOperationException ignored) {
                    this.plugin.getLogger().fine("[副本] unloadWorld 不受此伺服器支援，跳過。");
                }
                this.scheduler.runAsync(() -> this.deleteWorldFolder(new File(Bukkit.getWorldContainer(), instanceId)));
            });
            if (tp.isEmpty()) doClean.run();
            else CompletableFuture.allOf(tp.toArray(new CompletableFuture[0])).thenRun(doClean);
        }
    }

    /**
     * 安全地嘗試卸載編輯世界。
     * 若 {@code save} 為 {@code true} 且 {@code templateWorldName} 不為 null，
     * 會在卸載後將世界複製回模板目錄。
     * Folia / Luminol 不支援 unloadWorld 時會安靜跳過。
     */
    private void tryUnloadEditWorld(final String worldName, final boolean save, final String templateWorldName) {
        final World editWorld = Bukkit.getWorld(worldName);
        if (editWorld == null) return;

        // 先傳送所有殘留玩家出去
        final World fallback = Bukkit.getWorlds().get(0);
        final List<CompletableFuture<Boolean>> teleports = new ArrayList<>();
        for (final Player p : editWorld.getPlayers()) {
            teleports.add(p.teleportAsync(fallback.getSpawnLocation()));
        }

        // 等所有傳送完成 → 卸載世界 → 非同步清理
        final Runnable doUnload = () -> this.scheduler.runGlobal(task -> {
            boolean unloaded = false;
            try {
                unloaded = Bukkit.unloadWorld(editWorld, save);
            } catch (final UnsupportedOperationException e) {
                this.plugin.getLogger().warning("[副本] 此伺服器不支援 unloadWorld，跳過卸載。");
            } catch (final Exception e) {
                this.plugin.getLogger().warning("[副本] unloadWorld 失敗：" + e.getMessage());
            }

            // 不管 unload 成不成功，只要 save=true 就把編輯目錄回存到模板
            if (save && templateWorldName != null) {
                final boolean worldUnloaded = unloaded;
                this.scheduler.runAsync(() -> {
                    final File editDir = new File(Bukkit.getWorldContainer(), worldName);
                    if (!editDir.isDirectory()) return;
                    final File templateDir = new File(this.plugin.getDataFolder(),
                            TEMPLATE_DIR + File.separator + templateWorldName);
                    try {
                        // 使用合併複製：將 edit 世界的檔案覆蓋到模板上，
                        // 而不是先刪除整個模板再複製。
                        // 這樣可以保留模板中原有但 edit 世界中不存在的 region 檔案，
                        // 避免 Folia 無法 unloadWorld 時導致模板 region 遺失。
                        this.copyFolder(editDir.toPath(), templateDir.toPath());
                        this.plugin.getLogger().info("[副本] 編輯世界已合併回存到模板：" + templateWorldName);
                    } catch (final IOException ex) {
                        this.plugin.getLogger().log(Level.WARNING, "[副本] 儲存模板失敗", ex);
                    }
                    // 只有在世界成功卸載時才刪除 edit 目錄
                    // Folia 無法 unloadWorld → 世界仍在記憶體中，刪除目錄會導致
                    // NMS 之後只寫回已載入的 chunks，丟失未載入的 region 資料
                    if (worldUnloaded) {
                        this.deleteWorldFolder(editDir);
                    }
                });
            } else {
                final boolean worldUnloaded = unloaded;
                this.scheduler.runAsync(() -> {
                    final File editDir = new File(Bukkit.getWorldContainer(), worldName);
                    if (worldUnloaded) {
                        this.deleteWorldFolder(editDir);
                    }
                });
            }
        });

        if (teleports.isEmpty()) {
            doUnload.run();
        } else {
            CompletableFuture.allOf(teleports.toArray(new CompletableFuture[0]))
                    .thenRun(doUnload);
        }
    }

    // ──────────────────────────────────────────────
    //  世界複製 / 刪除
    // ──────────────────────────────────────────────

    private void copyTemplateWorld(final String templateName, final String targetName) throws IOException {
        final File templateDir = new File(this.plugin.getDataFolder(), TEMPLATE_DIR + File.separator + templateName);
        if (!templateDir.exists() || !templateDir.isDirectory()) {
            throw new IOException("找不到模板世界目錄：" + templateDir.getAbsolutePath());
        }
        final File targetDir = new File(Bukkit.getWorldContainer(), targetName);
        this.copyFolder(templateDir.toPath(), targetDir.toPath());
        // 確保 data/ 目錄存在，避免 chunks.dat NoSuchFileException
        new File(targetDir, "data").mkdirs();
        // 刪除 uid.dat 以免衝突
        final File uidDat = new File(targetDir, "uid.dat");
        if (uidDat.exists()) uidDat.delete();
        final File sessionLock = new File(targetDir, "session.lock");
        if (sessionLock.exists()) sessionLock.delete();
        // 刪除 entities/ 資料夾，防止模板實體 UUID 與主世界衝突
        // → Folia 下 UUID 碰撞會導致跨世界 POI 存取執行緒錯誤
        final File entitiesDir = new File(targetDir, "entities");
        if (entitiesDir.exists()) this.deleteWorldFolder(entitiesDir);
        // 刪除 poi/ 資料夾，防止副本世界的 POI 資料干擾主世界村民 AI
        // → Folia 的 PoiManager 線程檢查會因跨世界存取而拋出 IllegalStateException
        final File poiDir = new File(targetDir, "poi");
        if (poiDir.exists()) this.deleteWorldFolder(poiDir);
    }

    private void copyFolder(final Path source, final Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteWorldFolder(final File folder) {
        if (!folder.exists()) return;
        try {
            Files.walkFileTree(folder.toPath(), new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (final IOException e) {
            this.plugin.getLogger().warning("[副本] 刪除世界資料夾失敗：" + e.getMessage());
        }
    }

    // ──────────────────────────────────────────────
    //  腳本事件系統
    // ──────────────────────────────────────────────

    private void tickScriptTimers() {
        for (final DungeonInstance instance : this.instances.values()) {
            if (!instance.isActive()) continue;
            for (final ScriptDefinition script : instance.definition().scripts()) {
                if (!this.matchesTrigger(script.trigger(), "timer")) continue;
                if (script.oneShot() && instance.hasFired(script.id())) continue;
                // timer 以 delay-ticks 指定觸發時間點（副本開始後）
                if (instance.elapsedTicks() == script.delayTicks()) {
                    if (this.checkScriptConditions(instance, script)) {
                        this.executeScriptActions(instance, script);
                    }
                }
            }
        }
    }

    /**
     * 觸發指定類型的腳本。
     */
    public void triggerScripts(final DungeonInstance instance, final String trigger, final Map<String, Object> context) {
        for (final ScriptDefinition script : instance.definition().scripts()) {
            if (!this.matchesTrigger(script.trigger(), trigger)) continue;
            if (script.oneShot() && instance.hasFired(script.id())) continue;
            if (!this.checkScriptConditions(instance, script, context)) continue;

            if (script.delayTicks() > 0) {
                this.scheduler.runGlobalDelayed(task -> this.executeScriptActions(instance, script), script.delayTicks());
            } else {
                this.executeScriptActions(instance, script);
            }
        }
    }

    private boolean checkScriptConditions(final DungeonInstance instance, final ScriptDefinition script) {
        return this.checkScriptConditions(instance, script, Map.of());
    }

    private boolean checkScriptConditions(final DungeonInstance instance, final ScriptDefinition script,
                                           final Map<String, Object> context) {
        for (final ScriptCondition condition : script.conditions()) {
            if (!this.evaluateCondition(instance, condition, context)) return false;
        }
        return true;
    }

    private boolean evaluateCondition(final DungeonInstance instance, final ScriptCondition condition,
                                       final Map<String, Object> context) {
        return switch (condition.type().toLowerCase()) {
            case "state", "dungeon_status" -> {
                final String expected = toString(condition.params().get("state"), "RUNNING");
                yield instance.state().name().equalsIgnoreCase(expected);
            }
            case "password" -> {
                yield true; // 條件成立由 trigger handler 保證
            }
            case "block_location" -> {
                final int x = toInt(condition.params().get("x"), 0);
                final int y = toInt(condition.params().get("y"), 0);
                final int z = toInt(condition.params().get("z"), 0);
                final Object ctx = context.get("block_location");
                if (ctx instanceof int[] loc) {
                    yield loc[0] == x && loc[1] == y && loc[2] == z;
                }
                yield false;
            }
            case "region", "area", "cuboid", "enter_region", "leave_region" -> {
                final Location playerLoc = this.contextLocation(instance, context.get("player_location"));
                yield playerLoc != null && this.isInsideRegion(playerLoc, condition.params());
            }
            case "variable", "variable_comparison" -> {
                final String key = toString(condition.params().get("key"), "");
                final Object expected = condition.params().get("value");
                final String op = toString(condition.params().get("operator"), "equals");
                final Object actual = instance.getVariable(key);
                yield this.compareValues(actual, expected, op);
            }
            case "wave" -> {
                final int wave = toInt(condition.params().get("wave"), 0);
                final Object ctx = context.get("wave");
                yield ctx instanceof Integer w && w == wave;
            }
            case "boss_id" -> {
                final String id = toString(condition.params().get("id"), "");
                yield id.equals(context.getOrDefault("boss_id", ""));
            }
            case "all_dead" -> {
                boolean anyAlive = false;
                for (final UUID uuid : instance.members()) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline() && !p.isDead() && !instance.isSpectator(uuid)) {
                        anyAlive = true; break;
                    }
                }
                yield !anyAlive;
            }
            // ── MythicDungeons 式新條件 ──
            case "players_within" -> {
                // 檢查指定範圍內的玩家數量
                final double x = toDouble(condition.params().get("x"), 0);
                final double y = toDouble(condition.params().get("y"), 65);
                final double z = toDouble(condition.params().get("z"), 0);
                final double radius = toDouble(condition.params().get("radius"), 5);
                final int minCount = toInt(condition.params().get("min"), 1);
                final World world = instance.instanceWorld();
                if (world == null) yield false;
                final Location center = new Location(world, x, y, z);
                int count = 0;
                for (final UUID uuid : instance.members()) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p != null && !p.isDead() && p.getWorld().equals(world)
                            && p.getLocation().distance(center) <= radius) {
                        count++;
                    }
                }
                yield count >= minCount;
            }
            case "mobs_within" -> {
                // 檢查指定範圍內的怪物數量
                final double x = toDouble(condition.params().get("x"), 0);
                final double y = toDouble(condition.params().get("y"), 65);
                final double z = toDouble(condition.params().get("z"), 0);
                final double radius = toDouble(condition.params().get("radius"), 5);
                final int minCount = toInt(condition.params().get("min"), 0);
                final int maxCount = toInt(condition.params().get("max"), Integer.MAX_VALUE);
                final World world = instance.instanceWorld();
                if (world == null) yield false;
                final Location center = new Location(world, x, y, z);
                int count = 0;
                for (final Entity e : instance.spawnedEntities()) {
                    if (e != null && e.isValid() && !e.isDead()
                            && e.getLocation().distance(center) <= radius) {
                        count++;
                    }
                }
                yield count >= minCount && count <= maxCount;
            }
            case "player_count" -> {
                final int min = toInt(condition.params().get("min"), 0);
                final int max = toInt(condition.params().get("max"), Integer.MAX_VALUE);
                final int online = (int) instance.members().stream()
                        .map(Bukkit::getPlayer)
                        .filter(p -> p != null && p.isOnline() && !p.isDead())
                        .count();
                yield online >= min && online <= max;
            }
            case "trigger_chance" -> {
                final double chance = toDouble(condition.params().get("chance"), 1.0);
                yield ThreadLocalRandom.current().nextDouble() <= chance;
            }
            case "time_elapsed" -> {
                final int minTime = toInt(condition.params().get("min"), 0);
                final int maxTime = toInt(condition.params().get("max"), Integer.MAX_VALUE);
                final int elapsed = instance.elapsedSeconds();
                yield elapsed >= minTime && elapsed <= maxTime;
            }
            case "dungeon_difficulty" -> {
                final String expected = toString(condition.params().get("difficulty"), "");
                yield expected.equalsIgnoreCase(instance.selectedDifficulty() != null
                        ? instance.selectedDifficulty() : "normal");
            }
            case "signal" -> {
                final String signal = toString(condition.params().get("signal"), "");
                yield instance.hasSignal(signal);
            }
            case "mob_death_count" -> {
                final String counter = toString(condition.params().get("counter"), "default");
                final int required = toInt(condition.params().get("count"), 1);
                yield instance.getMobDeathCount(counter) >= required;
            }
            case "player_death_count" -> {
                final int required = toInt(condition.params().get("count"), 1);
                yield instance.playerDeathCount() >= required;
            }
            default -> true;
        };
    }

    /** 比較兩個值（支援 equals, greater, less, greater_equals, less_equals, not_equals）。 */
    private boolean compareValues(final Object actual, final Object expected, final String operator) {
        if (actual == null && expected == null) return "equals".equals(operator);
        if (actual == null || expected == null) return "not_equals".equals(operator);
        // 嘗試數字比較
        if (actual instanceof Number na && expected instanceof Number ne) {
            final double a = na.doubleValue(), b = ne.doubleValue();
            return switch (operator.toLowerCase()) {
                case "equals" -> a == b;
                case "not_equals" -> a != b;
                case "greater", ">" -> a > b;
                case "less", "<" -> a < b;
                case "greater_equals", ">=" -> a >= b;
                case "less_equals", "<=" -> a <= b;
                default -> a == b;
            };
        }
        // 字串比較
        try {
            final double a = Double.parseDouble(String.valueOf(actual));
            final double b = Double.parseDouble(String.valueOf(expected));
            return switch (operator.toLowerCase()) {
                case "equals" -> a == b;
                case "not_equals" -> a != b;
                case "greater", ">" -> a > b;
                case "less", "<" -> a < b;
                case "greater_equals", ">=" -> a >= b;
                case "less_equals", "<=" -> a <= b;
                default -> a == b;
            };
        } catch (final NumberFormatException ignored) {}
        final String sa = String.valueOf(actual), sb = String.valueOf(expected);
        return switch (operator.toLowerCase()) {
            case "equals" -> sa.equals(sb);
            case "not_equals" -> !sa.equals(sb);
            default -> sa.equals(sb);
        };
    }

    private boolean matchesTrigger(final String scriptTrigger, final String expected) {
        return scriptTrigger != null
                && expected != null
                && scriptTrigger.trim().equalsIgnoreCase(expected.trim());
    }

    private Location contextLocation(final DungeonInstance instance, final Object contextValue) {
        if (contextValue instanceof Location loc) {
            final World world = instance.instanceWorld();
            if (world == null || loc.getWorld() == null || !loc.getWorld().equals(world)) {
                return null;
            }
            return loc;
        }
        if (contextValue instanceof int[] arr && arr.length >= 3) {
            final World world = instance.instanceWorld();
            if (world == null) {
                return null;
            }
            return new Location(world, arr[0] + 0.5, arr[1], arr[2] + 0.5);
        }
        return null;
    }

    private boolean isInsideRegion(final Location loc, final Map<String, Object> params) {
        if (loc == null || params == null || params.isEmpty()) {
            return false;
        }

        if (params.containsKey("x1") || params.containsKey("x2")
                || params.containsKey("min-x") || params.containsKey("max-x")) {
            final double x1 = this.toDoubleFlexible(params.getOrDefault("x1", params.get("min-x")), loc.getX());
            final double y1 = this.toDoubleFlexible(params.getOrDefault("y1", params.get("min-y")), loc.getY());
            final double z1 = this.toDoubleFlexible(params.getOrDefault("z1", params.get("min-z")), loc.getZ());
            final double x2 = this.toDoubleFlexible(params.getOrDefault("x2", params.get("max-x")), x1);
            final double y2 = this.toDoubleFlexible(params.getOrDefault("y2", params.get("max-y")), y1);
            final double z2 = this.toDoubleFlexible(params.getOrDefault("z2", params.get("max-z")), z1);

            final double minX = Math.min(x1, x2);
            final double maxX = Math.max(x1, x2);
            final double minY = Math.min(y1, y2);
            final double maxY = Math.max(y1, y2);
            final double minZ = Math.min(z1, z2);
            final double maxZ = Math.max(z1, z2);

            return loc.getX() >= minX && loc.getX() <= maxX
                    && loc.getY() >= minY && loc.getY() <= maxY
                    && loc.getZ() >= minZ && loc.getZ() <= maxZ;
        }

        if (params.containsKey("x") && params.containsKey("z")) {
            final double x = this.toDoubleFlexible(params.get("x"), loc.getX());
            final double y = this.toDoubleFlexible(params.getOrDefault("y", loc.getY()), loc.getY());
            final double z = this.toDoubleFlexible(params.get("z"), loc.getZ());
            final double radius = this.toDoubleFlexible(params.getOrDefault("radius", params.getOrDefault("distance", 1.5)), 1.5);
            final Location center = new Location(loc.getWorld(), x, y, z);
            return loc.distanceSquared(center) <= radius * radius;
        }

        return false;
    }

    private boolean isInScriptRegion(final DungeonInstance instance, final ScriptDefinition script, final Location location) {
        if (location == null) {
            return false;
        }
        final World world = instance.instanceWorld();
        if (world == null || location.getWorld() == null || !location.getWorld().equals(world)) {
            return false;
        }

        boolean hasRegionCondition = false;
        for (final ScriptCondition condition : script.conditions()) {
            final String type = condition.type() == null ? "" : condition.type().toLowerCase(Locale.ROOT);
            if ("region".equals(type) || "area".equals(type) || "cuboid".equals(type)
                    || "enter_region".equals(type) || "leave_region".equals(type)
                    || "players_within".equals(type)) {
                hasRegionCondition = true;
                if (!this.isInsideRegion(location, condition.params())) {
                    return false;
                }
            } else if ("block_location".equals(type)) {
                hasRegionCondition = true;
                final int x = toInt(condition.params().get("x"), Integer.MIN_VALUE);
                final int y = toInt(condition.params().get("y"), Integer.MIN_VALUE);
                final int z = toInt(condition.params().get("z"), Integer.MIN_VALUE);
                if (location.getBlockX() != x || location.getBlockY() != y || location.getBlockZ() != z) {
                    return false;
                }
            }
        }
        return hasRegionCondition;
    }

    private void clearRegionState(final String instanceId, final UUID uuid) {
        final Map<UUID, Set<String>> byPlayer = this.playerRegionStates.get(instanceId);
        if (byPlayer == null) {
            return;
        }
        byPlayer.remove(uuid);
        if (byPlayer.isEmpty()) {
            this.playerRegionStates.remove(instanceId);
        }
    }

    private double toDoubleFlexible(final Object obj, final double def) {
        if (obj instanceof Number number) {
            return number.doubleValue();
        }
        if (obj instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (final NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private void executeScriptActions(final DungeonInstance instance, final ScriptDefinition script) {
        if (script.oneShot()) instance.markFired(script.id());
        for (final ScriptAction action : script.actions()) {
            this.executeAction(instance, action);
        }
    }

    private void executeAction(final DungeonInstance instance, final ScriptAction action) {
        switch (action.type().toLowerCase()) {
            case "message" -> {
                final String text = toString(action.params().get("text"), "");
                this.broadcastToInstance(instance, text);
            }
            case "title" -> {
                final String titleText = toString(action.params().get("title"), "");
                final String subtitle = toString(action.params().get("subtitle"), "");
                final int fadeIn = toInt(action.params().get("fade-in"), 10);
                final int stay = toInt(action.params().get("stay"), 40);
                final int fadeOut = toInt(action.params().get("fade-out"), 10);
                this.broadcastTitle(instance,
                    Component.text(titleText), Component.text(subtitle),
                    fadeIn, stay, fadeOut);
            }
            case "actionbar" -> {
                final String text = toString(action.params().get("text"), "");
                for (final UUID uuid : instance.members()) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p != null) p.sendActionBar(Component.text(text));
                }
            }
            case "sound" -> {
                final String soundName = toString(action.params().get("sound"), "ENTITY_EXPERIENCE_ORB_PICKUP");
                final float volume = (float) toDouble(action.params().get("volume"), 1.0);
                final float pitch = (float) toDouble(action.params().get("pitch"), 1.0);
                try {
                    final Sound sound = Sound.valueOf(soundName.toUpperCase());
                    this.broadcastSound(instance, sound, volume, pitch);
                } catch (final Exception ignored) {}
            }
            case "teleport" -> {
                final double x = toDouble(action.params().get("x"), 0);
                final double y = toDouble(action.params().get("y"), 65);
                final double z = toDouble(action.params().get("z"), 0);
                final World world = instance.instanceWorld();
                if (world == null) break;
                final Location loc = new Location(world, x, y, z);
                for (final UUID uuid : instance.members()) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p != null) p.teleportAsync(loc);
                }
            }
            case "spawn_mob" -> {
                final String entityType = toString(action.params().get("entity-type"), "ZOMBIE");
                final int count = toInt(action.params().get("count"), 1);
                final double x = toDouble(action.params().get("x"), 0);
                final double y = toDouble(action.params().get("y"), 65);
                final double z = toDouble(action.params().get("z"), 0);
                final String name = toString(action.params().get("custom-name"), null);
                final MobEntry entry = new MobEntry(entityType, null, count, 1, new double[]{0, 0, 0}, name, Map.of());
                final World world = instance.instanceWorld();
                if (world != null) {
                    this.spawnMobs(instance, new Location(world, x, y, z), entry);
                }
            }
            case "set_block" -> {
                final int x = toInt(action.params().get("x"), 0);
                final int y = toInt(action.params().get("y"), 65);
                final int z = toInt(action.params().get("z"), 0);
                final String materialName = toString(action.params().get("material"), "AIR");
                final World world = instance.instanceWorld();
                if (world != null) {
                    final Location loc = new Location(world, x, y, z);
                    this.scheduler.runRegion(loc, task -> {
                        try {
                            world.getBlockAt(x, y, z).setType(Material.valueOf(materialName.toUpperCase()));
                        } catch (final Exception ignored) {}
                    });
                }
            }
            case "give_item" -> {
                final String itemId = toString(action.params().get("item"), "DIAMOND");
                final int amount = toInt(action.params().get("amount"), 1);
                for (final UUID uuid : instance.members()) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    try {
                        final Material mat = Material.valueOf(itemId.toUpperCase());
                        p.getInventory().addItem(new ItemStack(mat, amount));
                    } catch (final Exception ignored) {}
                }
            }
            case "run_command" -> {
                final String command = toString(action.params().get("command"), "");
                if (!command.isBlank()) {
                    for (final UUID uuid : instance.members()) {
                        final Player p = Bukkit.getPlayer(uuid);
                        if (p != null) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                                command.replace("{player}", p.getName()));
                        }
                    }
                }
            }
            case "start_wave" -> {
                final int wave = toInt(action.params().get("wave"), 0);
                this.startWave(instance, wave);
            }
            case "spawn_boss" -> {
                final String bossId = toString(action.params().get("boss-id"), "");
                instance.definition().bosses().stream()
                    .filter(b -> b.id().equals(bossId))
                    .findFirst()
                    .ifPresent(b -> this.spawnBoss(instance, b));
            }
            case "complete_dungeon" -> this.completeDungeon(instance);
            case "fail_dungeon" -> this.failDungeon(instance);
            case "set_variable" -> {
                final String key = toString(action.params().get("key"), "");
                final Object value = action.params().get("value");
                if (!key.isBlank()) instance.setVariable(key, value);
            }
            case "check_variable" -> {
                final String key = toString(action.params().get("key"), "");
                final Object expected = action.params().get("value");
                if (!Objects.equals(instance.getVariable(key), expected)
                    && !String.valueOf(instance.getVariable(key)).equals(String.valueOf(expected))) {
                    return; // 中斷後續動作
                }
            }
            case "particle" -> {
                final String particleName = toString(action.params().get("particle"), "FLAME");
                final double x = toDouble(action.params().get("x"), 0);
                final double y = toDouble(action.params().get("y"), 65);
                final double z = toDouble(action.params().get("z"), 0);
                final int count = toInt(action.params().get("count"), 20);
                final World world = instance.instanceWorld();
                if (world != null) {
                    try {
                        world.spawnParticle(Particle.valueOf(particleName.toUpperCase()),
                            x, y, z, count, 0.5, 0.5, 0.5, 0.01);
                    } catch (final Exception ignored) {}
                }
            }
            case "explosion" -> {
                final double x = toDouble(action.params().get("x"), 0);
                final double y = toDouble(action.params().get("y"), 65);
                final double z = toDouble(action.params().get("z"), 0);
                final float power = (float) toDouble(action.params().get("power"), 2);
                final boolean breakBlocks = toBool(action.params().get("break-blocks"), false);
                final World world = instance.instanceWorld();
                if (world != null) {
                    final Location loc = new Location(world, x, y, z);
                    this.scheduler.runRegion(loc, task -> world.createExplosion(loc, power, false, breakBlocks));
                }
            }
            case "function" -> {
                final String funcId = toString(action.params().get("id"), "");
                this.triggerScripts(instance, "function_call", Map.of("function_id", funcId));
            }
            case "wait" -> {
                // wait 只在排程裡有意義——此處暫不實作異步等待
            }
            // ── MythicDungeons 式新動作 ──
            case "checkpoint" -> {
                // 為所有玩家設定檢查點
                final double x = toDouble(action.params().get("x"), 0);
                final double y = toDouble(action.params().get("y"), 65);
                final double z = toDouble(action.params().get("z"), 0);
                final World world = instance.instanceWorld();
                if (world != null) {
                    final Location loc = new Location(world, x, y, z);
                    instance.setCheckpointAll(loc);
                    this.broadcastToInstance(instance, "§a§l✦ 檢查點已更新！");
                    this.broadcastSound(instance, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
                }
            }
            case "signal", "send_signal" -> {
                final String signal = toString(action.params().get("signal"), "");
                if (!signal.isBlank()) {
                    instance.sendSignal(signal);
                    // 觸發 signal_receiver 腳本
                    this.triggerScripts(instance, "signal_receiver", Map.of("signal", signal));
                }
            }
            case "clear_signal" -> {
                final String signal = toString(action.params().get("signal"), "");
                if (!signal.isBlank()) instance.clearSignal(signal);
            }
            case "lives_edit", "edit_lives" -> {
                final String op = toString(action.params().get("operation"), "set"); // set, add, remove
                final int value = toInt(action.params().get("value"), 1);
                final boolean allPlayers = toBool(action.params().get("all"), true);
                if (allPlayers) {
                    for (final UUID uuid : instance.members()) {
                        this.modifyPlayerLives(instance, uuid, op, value);
                    }
                } else {
                    final Object ctx = action.params().get("player");
                    if (ctx instanceof UUID uuid) this.modifyPlayerLives(instance, uuid, op, value);
                }
            }
            case "revive" -> {
                // 復活所有觀戰者
                for (final UUID uuid : new ArrayList<>(instance.spectators())) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        instance.removeSpectator(uuid);
                        instance.initPlayerLives(uuid, toInt(action.params().get("lives"), 1));
                        p.setGameMode(GameMode.SURVIVAL);
                        this.respawnPlayerInDungeon(p, instance);
                        p.sendMessage(this.msg("§a你已被復活！"));
                    }
                }
            }
            case "teleporter" -> {
                final double x = toDouble(action.params().get("x"), 0);
                final double y = toDouble(action.params().get("y"), 65);
                final double z = toDouble(action.params().get("z"), 0);
                final float yaw = (float) toDouble(action.params().get("yaw"), 0);
                final float pitch = (float) toDouble(action.params().get("pitch"), 0);
                final World world = instance.instanceWorld();
                if (world != null) {
                    final Location loc = new Location(world, x, y, z, yaw, pitch);
                    final double radius = toDouble(action.params().get("radius"), 0);
                    if (radius > 0) {
                        // 只傳送範圍內的玩家
                        final double sx = toDouble(action.params().get("source-x"), x);
                        final double sy = toDouble(action.params().get("source-y"), y);
                        final double sz = toDouble(action.params().get("source-z"), z);
                        final Location source = new Location(world, sx, sy, sz);
                        for (final UUID uuid : instance.members()) {
                            final Player p = Bukkit.getPlayer(uuid);
                            if (p != null && !p.isDead() && p.getWorld().equals(world)
                                    && p.getLocation().distance(source) <= radius) {
                                p.teleportAsync(loc);
                            }
                        }
                    } else {
                        for (final UUID uuid : instance.members()) {
                            final Player p = Bukkit.getPlayer(uuid);
                            if (p != null) p.teleportAsync(loc);
                        }
                    }
                }
            }
            case "item_dispenser", "give_key" -> {
                final String itemId = toString(action.params().get("item"), "TRIPWIRE_HOOK");
                final int amount = toInt(action.params().get("amount"), 1);
                final String itemName = toString(action.params().get("name"), null);
                for (final UUID uuid : instance.members()) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p == null) continue;
                    try {
                        final Material mat = Material.valueOf(itemId.toUpperCase());
                        final ItemStack stack = new ItemStack(mat, amount);
                        if (itemName != null) {
                            final ItemMeta meta = stack.getItemMeta();
                            if (meta != null) {
                                meta.displayName(Component.text(itemName, NamedTextColor.GOLD));
                                meta.getPersistentDataContainer().set(
                                    new NamespacedKey(this.plugin, "dungeon_key"), PersistentDataType.STRING, itemId);
                                stack.setItemMeta(meta);
                            }
                        }
                        p.getInventory().addItem(stack);
                    } catch (final Exception ignored) {}
                }
            }
            case "door_controller" -> {
                // 開啟或關閉門（將指定區域的方塊設為 AIR 或恢復）
                final String doorId = toString(action.params().get("door-id"), "door");
                final boolean open = toBool(action.params().get("open"), true);
                final String material = toString(action.params().get("material"), "AIR");
                final int x1 = toInt(action.params().get("x1"), 0);
                final int y1 = toInt(action.params().get("y1"), 0);
                final int z1 = toInt(action.params().get("z1"), 0);
                final int x2 = toInt(action.params().get("x2"), 0);
                final int y2 = toInt(action.params().get("y2"), 0);
                final int z2 = toInt(action.params().get("z2"), 0);
                final World world = instance.instanceWorld();
                if (world != null) {
                    final Material mat = open ? Material.AIR :
                            this.parseMaterial(material, Material.STONE);
                    final Location loc = new Location(world, x1, y1, z1);
                    this.scheduler.runRegion(loc, task -> {
                        for (int bx = Math.min(x1, x2); bx <= Math.max(x1, x2); bx++) {
                            for (int by = Math.min(y1, y2); by <= Math.max(y1, y2); by++) {
                                for (int bz = Math.min(z1, z2); bz <= Math.max(z1, z2); bz++) {
                                    world.getBlockAt(bx, by, bz).setType(mat);
                                }
                            }
                        }
                    });
                }
            }
            case "block_controller" -> {
                // 將指定位置的方塊設為指定材質
                final int x = toInt(action.params().get("x"), 0);
                final int y = toInt(action.params().get("y"), 65);
                final int z = toInt(action.params().get("z"), 0);
                final String material = toString(action.params().get("material"), "REDSTONE_BLOCK");
                final World world = instance.instanceWorld();
                if (world != null) {
                    final Location loc = new Location(world, x, y, z);
                    this.scheduler.runRegion(loc, task -> {
                        world.getBlockAt(x, y, z).setType(this.parseMaterial(material, Material.STONE));
                    });
                }
            }
            case "command_sender" -> {
                final String command = toString(action.params().get("command"), "");
                final boolean asConsole = toBool(action.params().get("console"), true);
                final boolean perPlayer = toBool(action.params().get("per-player"), true);
                if (!command.isBlank()) {
                    if (perPlayer) {
                        for (final UUID uuid : instance.members()) {
                            final Player p = Bukkit.getPlayer(uuid);
                            if (p == null) continue;
                            final String cmd = command.replace("{player}", p.getName())
                                    .replace("{dungeon}", instance.definition().id())
                                    .replace("{instance}", instance.instanceId());
                            if (asConsole) {
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                            } else {
                                p.performCommand(cmd);
                            }
                        }
                    } else {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    }
                }
            }
            case "multi_function" -> {
                // 依序執行多個腳本動作
                final Object funcs = action.params().get("functions");
                if (funcs instanceof List<?> list) {
                    for (final Object funcId : list) {
                        this.triggerScripts(instance, "function_call", Map.of("function_id", String.valueOf(funcId)));
                    }
                }
            }
            case "delayed_function" -> {
                final String funcId = toString(action.params().get("id"), "");
                final int delay = toInt(action.params().get("delay"), 20);
                this.scheduler.runGlobalDelayed(task ->
                    this.triggerScripts(instance, "function_call", Map.of("function_id", funcId)), delay);
            }
            case "function_repeater" -> {
                final String funcId = toString(action.params().get("id"), "");
                final int interval = toInt(action.params().get("interval"), 20);
                final int times = toInt(action.params().get("times"), 3);
                for (int i = 0; i < times; i++) {
                    final int delay = interval * (i + 1);
                    this.scheduler.runGlobalDelayed(task -> {
                        if (!instance.isActive()) return;
                        this.triggerScripts(instance, "function_call", Map.of("function_id", funcId));
                    }, delay);
                }
            }
            case "function_randomizer" -> {
                final Object funcs = action.params().get("functions");
                if (funcs instanceof List<?> list && !list.isEmpty()) {
                    final String chosen = String.valueOf(list.get(ThreadLocalRandom.current().nextInt(list.size())));
                    this.triggerScripts(instance, "function_call", Map.of("function_id", chosen));
                }
            }
            case "function_sequencer" -> {
                // 依序以延遲執行多個 function
                final Object funcs = action.params().get("functions");
                final int interval = toInt(action.params().get("interval"), 20);
                if (funcs instanceof List<?> list) {
                    for (int i = 0; i < list.size(); i++) {
                        final String funcId = String.valueOf(list.get(i));
                        final int delay = interval * (i + 1);
                        this.scheduler.runGlobalDelayed(task -> {
                            if (!instance.isActive()) return;
                            this.triggerScripts(instance, "function_call", Map.of("function_id", funcId));
                        }, delay);
                    }
                }
            }
            case "set_difficulty" -> {
                final String diff = toString(action.params().get("difficulty"), "normal");
                this.applyDifficultyToInstance(instance, diff);
            }
            case "finish_dungeon" -> this.completeDungeon(instance);
            case "leave_all" -> {
                for (final UUID uuid : new ArrayList<>(instance.members())) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p != null) this.removePlayerFromInstance(p, instance);
                }
            }
        }
    }

    /** 修改玩家生命。 */
    private void modifyPlayerLives(final DungeonInstance instance, final UUID uuid, final String op, final int value) {
        switch (op.toLowerCase()) {
            case "set" -> instance.initPlayerLives(uuid, value);
            case "add" -> instance.initPlayerLives(uuid, instance.getPlayerLives(uuid) + value);
            case "remove" -> instance.initPlayerLives(uuid, Math.max(0, instance.getPlayerLives(uuid) - value));
        }
    }

    /** 解析 Material，失敗回傳 fallback。 */
    private Material parseMaterial(final String name, final Material fallback) {
        try { return Material.valueOf(name.toUpperCase()); } catch (final Exception e) { return fallback; }
    }

    /** 套用難度等級到實例。 */
    private void applyDifficultyToInstance(final DungeonInstance instance, final String difficultyId) {
        instance.setSelectedDifficulty(difficultyId);
        final DungeonConfig cfg = instance.definition().config();
        for (final DungeonConfig.DifficultyLevel level : cfg.difficultyLevels()) {
            if (level.id().equalsIgnoreCase(difficultyId)) {
                instance.applyDifficulty(level.mobHealth(), level.mobDamage(), level.mobAmounts(),
                        level.bonusLoot(), level.bonusMythicLevels());
                this.broadcastToInstance(instance, "§6難度已設定為：§e" + level.displayName());
                return;
            }
        }
    }

    // ── 隊列/準備系統 ──

    /** 玩家按下 Ready。 */
    public void handleReady(final Player player) {
        final String instanceId = this.playerInstanceMap.get(player.getUniqueId());
        if (instanceId == null) {
            player.sendMessage(this.msg("§c你不在任何副本中。"));
            return;
        }
        final DungeonInstance instance = this.instances.get(instanceId);
        if (instance == null || !instance.isInLobby()) {
            player.sendMessage(this.msg("§c副本不在大廳等待階段。"));
            return;
        }
        instance.setReady(player.getUniqueId(), true);
        this.broadcastToInstance(instance, "§a" + player.getName() + " §f已準備就緒！");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

        if (instance.checkAllReady()) {
            this.broadcastToInstance(instance, "§a全員準備就緒！副本即將開始...");
            instance.setInLobby(false);
            instance.setState(State.COUNTDOWN);
            instance.setCountdownSeconds(COUNTDOWN_SECONDS);
            // 傳送到正式出生點
            final double[] sp = instance.definition().spawnPoint();
            final World world = instance.instanceWorld();
            if (world != null) {
                final Location spawn = new Location(world, sp[0], sp[1], sp[2],
                        sp.length > 3 ? (float) sp[3] : 0f, sp.length > 4 ? (float) sp[4] : 0f);
                for (final UUID uuid : instance.members()) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p != null) this.teleportPlayerSafely(p, spawn);
                }
            }
        }
    }

    /** /tech dungeon stuck — 傳回最近的檢查點或出生點。 */
    public void handleStuck(final Player player) {
        final String instanceId = this.playerInstanceMap.get(player.getUniqueId());
        if (instanceId == null) {
            player.sendMessage(this.msg("§c你不在任何副本中。"));
            return;
        }
        final DungeonInstance instance = this.instances.get(instanceId);
        if (instance == null) return;
        this.respawnPlayerInDungeon(player, instance);
        player.sendMessage(this.msg("§e已傳送至最近的安全位置。"));
    }

    /** 取得玩家所在副本的 Config（供 TechListener 規則引擎使用）。 */
    public DungeonConfig getPlayerDungeonConfig(final UUID uuid) {
        final String instanceId = this.playerInstanceMap.get(uuid);
        if (instanceId == null) return null;
        final DungeonInstance instance = this.instances.get(instanceId);
        if (instance == null) return null;
        return instance.definition().config();
    }

    /** 取得玩家所在副本的 Instance（供 TechListener 規則引擎使用）。 */
    public DungeonInstance getPlayerDungeonInstance(final UUID uuid) {
        final String instanceId = this.playerInstanceMap.get(uuid);
        if (instanceId == null) return null;
        return this.instances.get(instanceId);
    }

    /** 檢查玩家是否在副本世界中。 */
    public boolean isInDungeonWorld(final Player player) {
        return this.playerInstanceMap.containsKey(player.getUniqueId());
    }

    /** 檢查指定世界是否為副本實例世界。 */
    public boolean isInDungeonWorld(final org.bukkit.World world) {
        if (world == null) return false;
        for (final DungeonInstance inst : this.instances.values()) {
            if (world.equals(inst.instanceWorld())) return true;
        }
        return false;
    }

    // ──────────────────────────────────────────────
    //  外部事件入口（由 TechListener 呼叫）
    // ──────────────────────────────────────────────

    /** 處理聊天輸入（密碼機關）。 */
    public boolean handleChat(final Player player, final String message) {
        final String instanceId = this.playerInstanceMap.get(player.getUniqueId());
        if (instanceId == null) return false;
        final DungeonInstance instance = this.instances.get(instanceId);
        if (instance == null || !instance.isActive()) return false;

        for (final ScriptDefinition script : instance.definition().scripts()) {
            if (!this.matchesTrigger(script.trigger(), "chat_password")) continue;
            if (script.oneShot() && instance.hasFired(script.id())) continue;
            // 檢查密碼條件
            for (final ScriptCondition condition : script.conditions()) {
                if ("password".equalsIgnoreCase(condition.type())) {
                    final String password = toString(condition.params().get("password"), "");
                    final boolean caseSensitive = toBool(condition.params().get("case-sensitive"), false);
                    final boolean matches = caseSensitive
                        ? message.equals(password)
                        : message.equalsIgnoreCase(password);
                    if (matches) {
                        this.executeScriptActions(instance, script);
                        return true; // 攔截聊天訊息
                    }
                }
            }
        }
        // 功能引擎：CHAT_MESSAGE 觸發
        this.functionEngine.onChatMessage(instance, player, message);
        return false;
    }
    public void handleInteract(final Player player, final Block block) {
        final String instanceId = this.playerInstanceMap.get(player.getUniqueId());
        if (instanceId == null) return;
        final DungeonInstance instance = this.instances.get(instanceId);
        if (instance == null || !instance.isActive()) return;

        final int[] blockLoc = {block.getX(), block.getY(), block.getZ()};
        this.triggerScripts(instance, "interact_block", Map.of("block_location", blockLoc));
        // 功能引擎：RIGHT_CLICK / KEY_ITEM_DETECTOR 觸發
        this.functionEngine.onPlayerInteract(instance, player, block, Action.RIGHT_CLICK_BLOCK);
        final ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != Material.AIR) {
            this.functionEngine.onKeyItemUse(instance, player, block, hand);
        }
    }

    /** 處理玩家移動（區域進入/離開腳本）。 */
    public void handlePlayerMove(final Player player, final Location from, final Location to) {
        if (to == null) {
            return;
        }
        final String instanceId = this.playerInstanceMap.get(player.getUniqueId());
        if (instanceId == null) {
            return;
        }
        final DungeonInstance instance = this.instances.get(instanceId);
        if (instance == null || !instance.isActive()) {
            return;
        }
        final World world = instance.instanceWorld();
        if (world == null || to.getWorld() == null || !world.equals(to.getWorld())) {
            return;
        }
        if (from != null && from.getWorld() != null
                && from.getWorld().equals(to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ()) {
            return;
        }

        final Map<UUID, Set<String>> byPlayer = this.playerRegionStates.computeIfAbsent(instanceId,
                k -> new ConcurrentHashMap<>());
        final Set<String> entered = byPlayer.computeIfAbsent(player.getUniqueId(),
                k -> ConcurrentHashMap.newKeySet());

        boolean fireEnterRegion = false;
        boolean fireLeaveRegion = false;
        boolean fireEnterArea = false;
        boolean fireLeaveArea = false;

        for (final ScriptDefinition script : instance.definition().scripts()) {
            final boolean enterRegion = this.matchesTrigger(script.trigger(), "enter_region");
            final boolean leaveRegion = this.matchesTrigger(script.trigger(), "leave_region");
            final boolean enterArea = this.matchesTrigger(script.trigger(), "enter_area");
            final boolean leaveArea = this.matchesTrigger(script.trigger(), "leave_area");
            if (!enterRegion && !leaveRegion && !enterArea && !leaveArea) {
                continue;
            }

            final boolean wasInside = entered.contains(script.id());
            final boolean insideNow = this.isInScriptRegion(instance, script, to);

            if (insideNow) {
                entered.add(script.id());
            } else {
                entered.remove(script.id());
            }

            if (!wasInside && insideNow) {
                if (enterRegion) fireEnterRegion = true;
                if (enterArea) fireEnterArea = true;
            } else if (wasInside && !insideNow) {
                if (leaveRegion) fireLeaveRegion = true;
                if (leaveArea) fireLeaveArea = true;
            }
        }

        if (entered.isEmpty()) {
            byPlayer.remove(player.getUniqueId());
            if (byPlayer.isEmpty()) {
                this.playerRegionStates.remove(instanceId);
            }
        }

        final Map<String, Object> enterContext = Map.of(
                "player", player.getUniqueId(),
                "player_location", new int[]{to.getBlockX(), to.getBlockY(), to.getBlockZ()}
        );
        final Location leaveLoc = from != null ? from : to;
        final Map<String, Object> leaveContext = Map.of(
                "player", player.getUniqueId(),
                "player_location", new int[]{leaveLoc.getBlockX(), leaveLoc.getBlockY(), leaveLoc.getBlockZ()}
        );

        if (fireEnterRegion) {
            this.triggerScripts(instance, "enter_region", enterContext);
        }
        if (fireEnterArea) {
            this.triggerScripts(instance, "enter_area", enterContext);
        }
        if (fireLeaveRegion) {
            this.triggerScripts(instance, "leave_region", leaveContext);
        }
        if (fireLeaveArea) {
            this.triggerScripts(instance, "leave_area", leaveContext);
        }
    }

    /** 處理紅石變化（功能引擎 REDSTONE_RECEIVER 觸發）。 */
    public void handleRedstoneChange(final Block block, final int oldCurrent, final int newCurrent) {
        if (block == null || newCurrent <= 0 || newCurrent == oldCurrent) {
            return;
        }
        final World world = block.getWorld();
        for (final DungeonInstance instance : this.instances.values()) {
            if (!instance.isActive()) {
                continue;
            }
            if (world.equals(instance.instanceWorld())) {
                this.functionEngine.onRedstoneChange(instance, block);
                return;
            }
        }
    }

    /** 處理實體死亡（副本怪物）。 */
    public void handleEntityDeath(final Entity entity) {
        for (final DungeonInstance instance : this.instances.values()) {
            if (!instance.isActive()) continue;
            if (instance.spawnedEntities().contains(entity)) {
                instance.decrementAliveMobs();
                instance.spawnedEntities().remove(entity);
                this.cleanupModelArtifactsNearDeath(instance, entity);
                // 計入怪物死亡計數器
                final String mobType = entity.getType().name().toLowerCase();
                instance.incrementMobDeathCounter(mobType);
                instance.incrementMobDeathCounter("_total");
                // 觸發怪物死亡腳本
                this.triggerScripts(instance, "mob_kill", Map.of("mob_type", mobType, "mob_deaths", instance.getMobDeathCount(mobType)));
                // 功能引擎：MOB_DEATH_COUNTER 觸發
                this.functionEngine.onMobDeath(instance, entity);
                // 檢查是否是 Boss
                if (entity.equals(instance.activeBoss())) {
                    // Boss 死亡在 tickBoss 中自然檢測
                    this.triggerScripts(instance, "boss_death", Map.of("mob_type", mobType));
                }
                break;
            }
        }
    }

    /** 處理玩家死亡 — 完整生命/重生/觀戰系統。 */
    public void handlePlayerDeath(final Player player) {
        final String instanceId = this.playerInstanceMap.get(player.getUniqueId());
        if (instanceId == null) return;
        final DungeonInstance instance = this.instances.get(instanceId);
        if (instance == null || !instance.isActive()) return;

        final UUID uuid = player.getUniqueId();
        final DungeonConfig cfg = instance.definition().config();
        instance.incrementPlayerDeathCount();
        this.triggerScripts(instance, "player_death", Map.of("player", uuid));
        // 功能引擎：PLAYER_DEATH_COUNTER 觸發
        this.functionEngine.onPlayerDeath(instance, player);

        // 扣除生命
        final int livesRemaining;
        if (cfg.playerLives() > 0) {
            livesRemaining = instance.decrementPlayerLives(uuid);
            this.broadcastToInstance(instance, "§c" + player.getName() + " §7死亡！剩餘生命：§e" + Math.max(0, livesRemaining));
        } else {
            livesRemaining = -1; // 無限
        }

        // 即時重生
        if (cfg.instantRespawn()) {
            this.scheduler.runEntityDelayed(player, () -> {
                if (!player.isOnline()) return;
                player.spigot().respawn();
                // Folia 安全網：setRespawnLocation 可能不生效，延遲補傳送
                this.scheduler.runEntityDelayed(player, () -> this.safeRespawnTeleport(player, instance), 2L);
            }, 1L);
        } else if (livesRemaining > 0 || livesRemaining == -1) {
            // 有生命→等待重生→若玩家自行按了RESPAWN也會由 PlayerRespawnEvent 處理
            this.scheduler.runEntityDelayed(player, () -> {
                if (!player.isOnline()) return;
                if (player.isDead()) {
                    player.spigot().respawn();
                    // Folia 安全網：setRespawnLocation 可能不生效，延遲補傳送
                    this.scheduler.runEntityDelayed(player, () -> this.safeRespawnTeleport(player, instance), 2L);
                }
            }, 40L); // 2 秒後自動嘗試重生
        }

        // 生命耗盡 → 觀戰模式或離開
        if (livesRemaining != -1 && livesRemaining <= 0) {
            if (cfg.deadPlayersSpectate()) {
                instance.addSpectator(uuid);
                this.scheduler.runEntityDelayed(player, () -> {
                    if (!player.isOnline()) return;
                    player.spigot().respawn();
                    player.setGameMode(GameMode.SPECTATOR);
                    player.sendMessage(this.msg("§c你的生命已耗盡！進入觀戰模式。"));
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 0.8f);
                }, 1L);

                // 設定進入冷卻（on-lose-lives 模式）
                if (cfg.accessCooldownEnabled() && cfg.accessCooldownOnLoseLives()) {
                    this.getOrCreatePlayerData(uuid).setAccessCooldown(
                            instance.definition().id(), System.currentTimeMillis());
                }

                // 全員觀戰 → 失敗
                if (cfg.closeDungeonWhenAllSpectating() && instance.allMembersSpectating()) {
                    this.scheduler.runGlobalDelayed(task -> this.failDungeon(instance), 60L); // 3 秒後失敗
                }
            } else {
                // 直接離開副本
                this.scheduler.runEntityDelayed(player, () -> {
                    if (!player.isOnline()) return;
                    player.spigot().respawn();
                    this.removePlayerFromInstance(player, instance);
                    player.sendMessage(this.msg("§c你的生命已耗盡，已被移出副本。"));
                }, 1L);
                if (instance.members().size() <= 1) {
                    this.scheduler.runGlobalDelayed(task -> this.failDungeon(instance), 40L);
                }
            }
        }
    }

    /**
     * Folia 安全網：respawn 後驗證玩家是否在正確位置，不是就強制傳送。
     * 解決 setRespawnLocation 在 Folia/Luminol 上可能不生效的問題。
     */
    private void safeRespawnTeleport(final Player player, final DungeonInstance instance) {
        if (!player.isOnline() || player.isDead()) return;
        if (!instance.isActive()) return;
        final World iw = instance.instanceWorld();
        if (iw == null) return;
        // 如果玩家已經不在副本裡（被移除），不要傳送
        if (!this.playerInstanceMap.containsKey(player.getUniqueId())) return;
        // 如果玩家是觀戰者，不處理
        if (instance.isSpectator(player.getUniqueId())) return;
        // 計算預期位置
        final UUID uuid = player.getUniqueId();
        final DungeonConfig cfg = instance.definition().config();
        Location expected = instance.getCheckpoint(uuid);
        if (expected == null && cfg.respawnPoint() != null) {
            final double[] rp = cfg.respawnPoint();
            expected = new Location(iw, rp[0], rp[1], rp[2]);
        }
        if (expected == null) {
            final double[] sp = instance.definition().spawnPoint();
            expected = new Location(iw, sp[0], sp[1], sp[2]);
        }
        expected = this.findNearestSafeLocation(expected);
        // 比較當前位置 — 如果距離 > 5 格，代表 setRespawnLocation 沒生效
        if (player.getWorld() != iw
                || player.getLocation().distanceSquared(expected) > 25
                || this.isLocationUnsafe(player.getLocation())) {
            this.teleportPlayerSafely(player, expected);
        }
    }

    /** 將死亡後重生的玩家傳送回副本中。 */
    private void respawnPlayerInDungeon(final Player player, final DungeonInstance instance) {
        final UUID uuid = player.getUniqueId();
        final DungeonConfig cfg = instance.definition().config();
        final World world = instance.instanceWorld();
        if (world == null) return;

        // 優先使用 checkpoint → config respawnPoint → definition spawnPoint
        Location respawnLoc = instance.getCheckpoint(uuid);
        if (respawnLoc == null && cfg.respawnPoint() != null) {
            final double[] rp = cfg.respawnPoint();
            respawnLoc = new Location(world, rp[0], rp[1], rp[2],
                    rp.length > 3 ? (float) rp[3] : 0f, rp.length > 4 ? (float) rp[4] : 0f);
        }
        if (respawnLoc == null) {
            final double[] sp = instance.definition().spawnPoint();
            respawnLoc = new Location(world, sp[0], sp[1], sp[2],
                    sp.length > 3 ? (float) sp[3] : 0f, sp.length > 4 ? (float) sp[4] : 0f);
        }
        this.teleportPlayerSafely(player, respawnLoc);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
    }

    /**
     * 攔截 PlayerRespawnEvent，為副本內玩家直接設定正確的重生位置。
     * 如果玩家不在副本內，本方法不做任何修改，原版重生邏輯正常進行。
     */
    public void handlePlayerRespawn(final org.bukkit.event.player.PlayerRespawnEvent event) {
        final Player player = event.getPlayer();
        final String instanceId = this.playerInstanceMap.get(player.getUniqueId());
        if (instanceId == null) return;
        final DungeonInstance instance = this.instances.get(instanceId);
        if (instance == null || !instance.isActive()) return;
        final World world = instance.instanceWorld();
        if (world == null) return;

        final UUID uuid = player.getUniqueId();
        final DungeonConfig cfg = instance.definition().config();

        // 優先使用 checkpoint → config respawnPoint → definition spawnPoint
        Location respawnLoc = instance.getCheckpoint(uuid);
        if (respawnLoc == null && cfg.respawnPoint() != null) {
            final double[] rp = cfg.respawnPoint();
            respawnLoc = new Location(world, rp[0], rp[1], rp[2],
                    rp.length > 3 ? (float) rp[3] : 0f, rp.length > 4 ? (float) rp[4] : 0f);
        }
        if (respawnLoc == null) {
            final double[] sp = instance.definition().spawnPoint();
            respawnLoc = new Location(world, sp[0], sp[1], sp[2],
                    sp.length > 3 ? (float) sp[3] : 0f, sp.length > 4 ? (float) sp[4] : 0f);
        }
        event.setRespawnLocation(this.findNearestSafeLocation(respawnLoc));
    }

    /** 處理玩家退出伺服器。 */
    public void handlePlayerQuit(final Player player) {
        final String instanceId = this.playerInstanceMap.get(player.getUniqueId());
        if (instanceId == null) return;
        final DungeonInstance instance = this.instances.get(instanceId);
        if (instance == null) {
            this.playerInstanceMap.remove(player.getUniqueId());
            this.clearRegionState(instanceId, player.getUniqueId());
            return;
        }
        final DungeonConfig cfg = instance.definition().config();
        // 設定進入冷卻（on-leave 模式）
        if (cfg.accessCooldownEnabled() && cfg.accessCooldownOnLeave()) {
            this.getOrCreatePlayerData(player.getUniqueId()).setAccessCooldown(
                    instance.definition().id(), System.currentTimeMillis());
        }
        // 踢出離線玩家
        if (cfg.kickOfflinePlayers()) {
            if (cfg.kickOfflinePlayersDelay() <= 0) {
                instance.removeMember(player.getUniqueId());
                this.playerInstanceMap.remove(player.getUniqueId());
                this.clearRegionState(instanceId, player.getUniqueId());
            } else {
                // 延遲踢出
                this.scheduler.runGlobalDelayed(task -> {
                    if (Bukkit.getPlayer(player.getUniqueId()) != null) return; // 已重新上線
                    instance.removeMember(player.getUniqueId());
                    this.playerInstanceMap.remove(player.getUniqueId());
                    this.clearRegionState(instanceId, player.getUniqueId());
                    if (instance.members().isEmpty()) {
                        this.forceCloseInstance(instance, "所有玩家離開");
                    }
                }, cfg.kickOfflinePlayersDelay() * 20L);
                return;
            }
        } else {
            instance.removeMember(player.getUniqueId());
            this.playerInstanceMap.remove(player.getUniqueId());
            this.clearRegionState(instanceId, player.getUniqueId());
        }
        if (instance.members().isEmpty()) {
            this.forceCloseInstance(instance, "所有玩家離開");
        }
    }

    // ──────────────────────────────────────────────
    //  排行榜
    // ──────────────────────────────────────────────

    private void updateLeaderboard(final String dungeonId, final UUID uuid, final String name, final int seconds) {
        final List<LeaderboardEntry> list = this.leaderboards.computeIfAbsent(dungeonId, k -> new ArrayList<>());
        list.removeIf(e -> e.uuid().equals(uuid));
        list.add(new LeaderboardEntry(uuid, name, seconds));
        list.sort(LeaderboardEntry::compareTo);
        if (list.size() > 20) list.subList(20, list.size()).clear();
    }

    public List<LeaderboardEntry> getLeaderboard(final String dungeonId) {
        return Collections.unmodifiableList(this.leaderboards.getOrDefault(dungeonId, List.of()));
    }

    // ──────────────────────────────────────────────
    //  輔助
    // ──────────────────────────────────────────────

    private DungeonPlayerData getOrCreatePlayerData(final UUID uuid) {
        return this.playerData.computeIfAbsent(uuid, DungeonPlayerData::new);
    }

    public DungeonPlayerData getPlayerData(final UUID uuid) {
        return this.playerData.get(uuid);
    }

    private void broadcastToInstance(final DungeonInstance instance, final String message) {
        for (final UUID uuid : instance.members()) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.sendMessage(this.msg(message));
        }
    }

    private void broadcastToParty(final DungeonParty party, final String message) {
        for (final UUID uuid : party.members()) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.sendMessage(this.msg("§7[隊伍] " + message));
        }
    }

    private void broadcastTitle(final DungeonInstance instance, final Component title, final Component subtitle,
                                 final int fadeIn, final int stay, final int fadeOut) {
        for (final UUID uuid : instance.members()) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.showTitle(Title.title(title, subtitle,
                    Title.Times.times(
                        java.time.Duration.ofMillis(fadeIn * 50L),
                        java.time.Duration.ofMillis(stay * 50L),
                        java.time.Duration.ofMillis(fadeOut * 50L))));
            }
        }
    }

    private void broadcastSound(final DungeonInstance instance, final Sound sound, final float volume, final float pitch) {
        for (final UUID uuid : instance.members()) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private void updateActionBar(final DungeonInstance instance) {
        if (!this.shouldShowRuntimeActionBar()) {
            return;
        }
        final DungeonDefinition def = instance.definition();
        final DungeonConfig cfg = def.config();
        final int elapsed = instance.elapsedSeconds();
        final String timeStr;
        if (def.timeLimitSeconds() > 0) {
            final int remaining = def.timeLimitSeconds() - elapsed;
            timeStr = "§f剩餘 §e" + this.formatTime(remaining);
        } else {
            timeStr = "§f經過 §e" + this.formatTime(elapsed);
        }
        final String waveStr = instance.currentWave() >= 0
            ? " §7| §f波次 §a" + (instance.currentWave() + 1) + "§7/" + instance.totalWaves()
            : "";
        final String mobStr = instance.aliveMobCount() > 0
            ? " §7| §f怪物 §c" + instance.aliveMobCount()
            : "";
        for (final UUID uuid : instance.members()) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player == null) continue;
            // 每個玩家的生命狀態
            final String livesStr;
            if (cfg.playerLives() > 0) {
                final int lives = instance.getPlayerLives(uuid);
                livesStr = " §7| §f❤ §c" + Math.max(0, lives);
            } else {
                livesStr = "";
            }
            final String diffStr = instance.selectedDifficulty() != null
                ? " §7| §6✦ " + instance.selectedDifficulty() : "";
            final Component actionBar = RichText.parse(timeStr + waveStr + mobStr + livesStr + diffStr);
            player.sendActionBar(actionBar);
        }
    }

    private Component msg(final String text) {
        return RichText.mini("<gray>[</gray><gradient:#22D3EE:#3B82F6><bold>副本</bold></gradient><gray>]</gray> ")
                .append(RichText.parse(text));
    }

    private String formatTime(final int totalSeconds) {
        final int minutes = totalSeconds / 60;
        final int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    // ──────────────────────────────────────────────
    //  管理員 — 編輯模式（類 MythicDungeons）
    // ──────────────────────────────────────────────

    /** 取得正在編輯的副本 ID，若不在編輯模式則 null。 */
    public String getEditingDungeonId(final UUID uuid) {
        return this.editingSessions.get(uuid);
    }

    /**
     * 建立新副本定義（僅骨架），自動載入模板世界並進入編輯模式。
     * 等同於 MythicDungeons 的 {@code /md create <name>}。
     */
    public void adminCreate(final Player admin, final String dungeonId) {
        if (this.definitions.containsKey(dungeonId)) {
            admin.sendMessage(this.msg("§c副本 '" + dungeonId + "' 已存在。使用 edit 進入編輯。"));
            return;
        }
        // 建立模板目錄
        final File templateDir = new File(this.plugin.getDataFolder(),
                TEMPLATE_DIR + File.separator + "dungeon_" + dungeonId);
        if (!templateDir.exists()) templateDir.mkdirs();

        // 建立最小定義
        final DungeonDefinition stub = new DungeonDefinition(
                dungeonId,
                "§e" + dungeonId,
                "新副本 - 請使用 /tech dg edit " + dungeonId + " 編輯",
                "dungeon_" + dungeonId,
                1, 4, 0, 0, 0,
                new double[]{0.5, 65, 0.5, 0, 0},
                null,
                List.of(), List.of(), List.of(), List.of(),
                null, false, "冒險",
                DungeonConfig.defaults()
        );
        this.definitions.put(dungeonId, stub);
        this.saveDungeonDefinition(dungeonId, stub);

        admin.sendMessage(this.msg("§a已建立副本骨架：§e" + dungeonId));
        admin.sendMessage(this.msg("§7模板目錄：§f" + templateDir.getAbsolutePath()));
        admin.sendMessage(this.msg("§7使用 §e/tech dg edit " + dungeonId + " §7進入編輯模式。"));
    }

    /**
     * 從伺服器根目錄匯入一個已存在的世界資料夾作為副本模板。
     * <p>用法：{@code /tech dg import <副本ID> <世界資料夾名稱>}
     */
    public void adminImport(final Player admin, final String dungeonId) {
        // 來源：伺服器根目錄下的世界資料夾（名稱 = 副本ID）
        final String worldFolderName = dungeonId;
        final File sourceDir = new File(Bukkit.getWorldContainer(), worldFolderName);
        if (!sourceDir.isDirectory()) {
            admin.sendMessage(this.msg("§c找不到世界資料夾：§f" + sourceDir.getAbsolutePath()));
            admin.sendMessage(this.msg("§7請把地圖資料夾放到伺服器根目錄下。"));
            return;
        }
        // 驗證是否包含 region 資料
        final File regionDir = new File(sourceDir, "region");
        if (!regionDir.isDirectory() || regionDir.listFiles() == null || regionDir.listFiles().length == 0) {
            admin.sendMessage(this.msg("§c該資料夾不像是有效的 Minecraft 世界（找不到 region/ 資料夾或為空）。"));
            return;
        }

        // 目標模板目錄
        final File templateDir = new File(this.plugin.getDataFolder(),
                TEMPLATE_DIR + File.separator + "dungeon_" + dungeonId);

        admin.sendMessage(this.msg("§e正在匯入世界 §f" + worldFolderName + " §e→ 副本 §f" + dungeonId + "§e..."));

        // 非同步複製
        this.scheduler.runAsync(() -> {
            try {
                if (templateDir.exists()) this.deleteWorldFolder(templateDir);
                templateDir.mkdirs();
                this.copyFolder(sourceDir.toPath(), templateDir.toPath());
                // 清理不需要的檔案
                for (final String cleanup : new String[]{
                        "uid.dat", "session.lock", "playerdata", "advancements", "stats",
                        "entities", "poi", "players", "DIM-1", "DIM1"}) {
                    final File f = new File(templateDir, cleanup);
                    if (f.exists()) {
                        if (f.isDirectory()) this.deleteWorldFolder(f);
                        else f.delete();
                    }
                }
            } catch (final IOException e) {
                this.scheduler.runEntity(admin, () ->
                        admin.sendMessage(this.msg("§c匯入失敗：" + e.getMessage())));
                return;
            }

            // 建立或更新副本定義
            this.scheduler.runEntity(admin, () -> {
                if (!this.definitions.containsKey(dungeonId)) {
                    final DungeonDefinition stub = new DungeonDefinition(
                            dungeonId,
                            "§e" + dungeonId,
                            "從 " + worldFolderName + " 匯入",
                            "dungeon_" + dungeonId,
                            1, 4, 0, 0, 0,
                            new double[]{0.5, 65, 0.5, 0, 0},
                            null,
                            List.of(), List.of(), List.of(), List.of(),
                            null, false, "冒險",
                            DungeonConfig.defaults()
                    );
                    this.definitions.put(dungeonId, stub);
                    this.saveDungeonDefinition(dungeonId, stub);
                }
                admin.sendMessage(this.msg("§a匯入完成！副本 §e" + dungeonId + " §a已就緒。"));
                admin.sendMessage(this.msg("§7使用 §e/tech dg edit " + dungeonId + " §7進入編輯模式設定出生點等。"));
            });
        });
    }

    /**
     * 進入編輯模式 — 嘗試載入模板世界並傳送管理員進去。
     * 若伺服器不支援動態世界建立（Folia / Luminol），則以純 GUI 模式編輯。
     * 等同於 MythicDungeons 的 {@code /md edit <name>}。
     */
    public void adminEdit(final Player admin, final String dungeonId) {
        final DungeonDefinition def = this.definitions.get(dungeonId);
        if (def == null) {
            admin.sendMessage(this.msg("§c找不到副本：" + dungeonId + "。先用 create 建立。"));
            return;
        }
        if (this.editingSessions.containsKey(admin.getUniqueId())) {
            admin.sendMessage(this.msg("§c你已在編輯模式中（" + this.editingSessions.get(admin.getUniqueId()) + "）。先用 §e/tech dg save §c或 §e/tech dg cancel §c退出。"));
            return;
        }

        // 嘗試使用已載入的世界
        final String worldName = "dungeon_edit_" + dungeonId;
        final World existing = Bukkit.getWorld(worldName);
        if (existing != null) {
            // 嘗試卸載舊世界，以便重新從模板載入
            boolean unloaded = false;
            try { unloaded = Bukkit.unloadWorld(existing, false); } catch (final Exception ignored) {}
            if (!unloaded) {
                // Folia 不支援 unloadWorld — 直接進入已載入的世界
                admin.sendMessage(this.msg("§e使用已載入的編輯世界（如需重新載入模板，請重啟伺服器後再試）。"));
                this.editReturnLocations.putIfAbsent(admin.getUniqueId(), admin.getLocation().clone());
                this.enterEditWorld(admin, existing, def, dungeonId);
                return;
            }
            // 卸載成功 → 繼續下方流程，重新從模板複製
        }

        // 需要建立世界 — 先記錄返回位置（在指令執行緒上安全取得）
        final Location returnLoc = admin.getLocation().clone();
        admin.sendMessage(this.msg("§e正在準備編輯世界..."));

        // 非同步：清除舊編輯世界 → 從模板複製
        this.scheduler.runAsync(() -> {
            final File templateDir = new File(this.plugin.getDataFolder(),
                    TEMPLATE_DIR + File.separator + def.templateWorld());
            final File editDir = new File(Bukkit.getWorldContainer(), worldName);

            this.plugin.getLogger().info("[副本] 準備編輯世界 " + worldName
                    + " — 模板=" + templateDir.getAbsolutePath()
                    + ", 模板存在=" + templateDir.isDirectory()
                    + ", 編輯目錄存在=" + editDir.exists());

            // 清除舊的編輯世界目錄，確保使用最新的模板
            if (editDir.exists()) {
                try {
                    this.deleteWorldFolder(editDir);
                    this.plugin.getLogger().info("[副本] 已清除舊編輯目錄: " + editDir.getAbsolutePath());
                } catch (final Exception e) {
                    this.plugin.getLogger().warning("[副本] 清除舊目錄失敗: " + e.getMessage());
                }
            }
            if (templateDir.exists() && templateDir.isDirectory()) {
                try {
                    this.copyFolder(templateDir.toPath(), editDir.toPath());
                    // 確保 data/ 目錄存在，避免 chunks.dat NoSuchFileException
                    new File(editDir, "data").mkdirs();
                    final File uid = new File(editDir, "uid.dat");
                    if (uid.exists()) uid.delete();
                    final File session = new File(editDir, "session.lock");
                    if (session.exists()) session.delete();
                    // 刪除 entities/ 資料夾，防止模板實體 UUID 與主世界衝突
                    // → Folia 下 UUID 碰撞會導致跨世界 POI 存取執行緒錯誤
                    final File entitiesDir = new File(editDir, "entities");
                    if (entitiesDir.exists()) {
                        this.deleteWorldFolder(entitiesDir);
                        this.plugin.getLogger().info("[副本] 已清除 entities/ 資料夾，避免 Folia UUID 衝突。");
                    }

                    // 驗證複製結果
                    final File regionCheck = new File(editDir, "region");
                    final String[] regionFiles = regionCheck.isDirectory() ? regionCheck.list() : null;
                    this.plugin.getLogger().info("[副本] 複製完成 — region 檔案數="
                            + (regionFiles != null ? regionFiles.length : 0)
                            + ", level.dat=" + new File(editDir, "level.dat").exists()
                            + ", data/=" + new File(editDir, "data").isDirectory());
                } catch (final IOException e) {
                    this.plugin.getLogger().severe("[副本] 複製模板失敗: " + e.getMessage());
                    e.printStackTrace();
                    this.scheduler.runEntity(admin, () ->
                            admin.sendMessage(this.msg("§c複製模板世界失敗：" + e.getMessage())));
                    return;
                }
            } else {
                this.scheduler.runEntity(admin, () ->
                        admin.sendMessage(this.msg("§c找不到模板目錄：" + templateDir.getAbsolutePath()
                                + "\n§7請先用 §e/tech dg import " + dungeonId + " <世界資料夾> §7匯入地圖。")));
                return;
            }
            // 回主線程建立世界 — 使用 NMS 繞過 Folia 限制
            this.scheduler.runGlobal(task -> {
                World world = null;
                try {
                    world = com.rui.techproject.util.NmsWorldUtil.loadWorld(worldName, this.plugin.getLogger());
                } catch (final Exception e) {
                    this.plugin.getLogger().warning("[副本] NMS 編輯世界載入失敗：" + e.getMessage());
                }
                if (world == null) {
                    // createWorld 失敗或不支援 → 純 GUI 模式
                    this.scheduler.runEntity(admin, () ->
                            this.enterGuiOnlyEditMode(admin, def, dungeonId));
                    return;
                }
                world.setAutoSave(true);
                world.setDifficulty(Difficulty.PEACEFUL);
                world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                world.setGameRule(GameRule.MOB_GRIEFING, false);
                world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
                world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
                world.setTime(6000L);

                this.editReturnLocations.put(admin.getUniqueId(), returnLoc);
                final World finalWorld = world;
                this.scheduler.runEntity(admin, () ->
                        this.enterEditWorld(admin, finalWorld, def, dungeonId));
            });
        });
    }

    /** 純 GUI 編輯模式 — 不傳送、不建立世界，留在原地使用 GUI 工具。 */
    private void enterGuiOnlyEditMode(final Player admin, final DungeonDefinition def, final String dungeonId) {
        this.editReturnLocations.putIfAbsent(admin.getUniqueId(), admin.getLocation().clone());
        this.editingSessions.put(admin.getUniqueId(), dungeonId);

        // 只在快捷欄給 GUI 工具，不清空背包
        this.giveEditorTools(admin);

        admin.sendMessage(this.msg("§6§l⚠ 純 GUI 編輯模式"));
        admin.sendMessage(this.msg("§7此伺服器不支援動態世界建立（Folia / Luminol），"));
        admin.sendMessage(this.msg("§7你可以透過 GUI 編輯所有副本設定（波次、Boss、獎勵、腳本等）。"));
        admin.sendMessage(this.msg("§7出生點 / 離開點請使用 GUI 中的「§e基本設定§7」頁面手動輸入座標。"));
        admin.sendMessage(this.msg("§7地圖建築請直接編輯模板目錄中的世界檔案："));
        admin.sendMessage(this.msg("§f  " + new File(this.plugin.getDataFolder(),
                TEMPLATE_DIR + File.separator + def.templateWorld()).getAbsolutePath()));
        admin.sendMessage(this.msg("§a已進入副本 §e" + dungeonId + " §a的編輯模式（§6純 GUI§a）。"));
        admin.sendMessage(this.msg("§7完成後使用 §e/tech dg save §7或 §e/tech dg cancel §7退出。"));
    }

    /** 完整編輯模式 — 傳送進編輯世界。 */
    private void enterEditWorld(final Player admin, final World world,
                                final DungeonDefinition def, final String dungeonId) {
        this.editReturnLocations.putIfAbsent(admin.getUniqueId(), admin.getLocation().clone());
        this.editingSessions.put(admin.getUniqueId(), dungeonId);

        final double[] sp = def.spawnPoint();
        final Location spawn = new Location(world, sp[0], sp[1], sp[2],
                sp.length > 3 ? (float) sp[3] : 0f, sp.length > 4 ? (float) sp[4] : 0f);
        admin.teleportAsync(spawn).thenAccept(success -> {
            if (!success) {
                admin.sendMessage(this.msg("§c傳送到編輯世界失敗，改用純 GUI 模式。"));
                this.enterGuiOnlyEditMode(admin, def, dungeonId);
                return;
            }
            admin.setGameMode(GameMode.CREATIVE);
            this.giveEditorTools(admin);
            // 載入 Function Builder 功能並建立標記
            if (this.editorManager != null) {
                this.editorManager.loadFunctions(dungeonId);
                this.editorManager.createMarkers(dungeonId);
            }
            admin.sendMessage(this.msg("§a已進入副本 §e" + dungeonId + " §a的編輯模式。"));
            admin.sendMessage(this.msg("§7快捷鍵盤上的工具可以右鍵使用："));
            admin.sendMessage(this.msg("§e  [1] §b主選單 §7— 右鍵打開副本編輯 GUI"));
            admin.sendMessage(this.msg("§e  [2] §a設定出生點 §7— 右鍵設定玩家出生位置"));
            admin.sendMessage(this.msg("§e  [3] §d設定離開點 §7— 右鍵設定離開傳送位置"));
            admin.sendMessage(this.msg("§e  [4] §b功能建構器 §7— 右鍵方塊建立 MythicDungeons 式功能"));
            admin.sendMessage(this.msg("§e  [5] §6怪物生成器 §7— 右鍵方塊設定怪物生成點"));
            admin.sendMessage(this.msg("§e  [6] §c事件觸發器 §7— 右鍵方塊設定腳本觸發"));
            admin.sendMessage(this.msg("§e  [9] §4儲存並退出 §7— 右鍵儲存所有修改"));
            admin.playSound(admin.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
        });
    }

    /** 給予玩家編輯器工具組。 */
    private void giveEditorTools(final Player admin) {
        admin.getInventory().clear();
        admin.getInventory().setItem(0, this.buildEditorTool(Material.NETHER_STAR,       "§b§l主選單",         "§7右鍵打開副本編輯 GUI",     "editor:menu"));
        admin.getInventory().setItem(1, this.buildEditorTool(Material.ENDER_PEARL,       "§a§l設定出生點",     "§7右鍵：設定此位置為出生點", "editor:setspawn"));
        admin.getInventory().setItem(2, this.buildEditorTool(Material.CHORUS_FRUIT,      "§d§l設定離開點",     "§7右鍵：設定此位置為離開點", "editor:setexit"));
        // Slot 3: Function Builder 工具（對應 MythicDungeons 的羽毛工具）
        if (this.editorManager != null) {
            admin.getInventory().setItem(3, this.editorManager.createFunctionBuilderTool());
        }
        admin.getInventory().setItem(4, this.buildEditorTool(Material.ZOMBIE_SPAWN_EGG,  "§6§l怪物生成器",     "§7右鍵方塊：設定怪物生成點", "editor:mob_spawner"));
        admin.getInventory().setItem(5, this.buildEditorTool(Material.REDSTONE_TORCH,    "§c§l事件觸發器",     "§7右鍵方塊：設定腳本事件",   "editor:event_trigger"));
        admin.getInventory().setItem(6, this.buildEditorTool(Material.CLOCK,             "§f§l基本設定",       "§7右鍵：設定限時/人數等",    "editor:settings"));
        admin.getInventory().setItem(8, this.buildEditorTool(Material.BARRIER,           "§4§l儲存並退出",     "§7右鍵：儲存所有修改並退出", "editor:save"));
    }

    private ItemStack buildEditorTool(final Material material, final String name, final String lore, final String action) {
        final ItemStack item = new ItemStack(material);
        final var meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text(lore).decoration(TextDecoration.ITALIC, false),
                Component.text(""),
                Component.text("§8[副本編輯器]").decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(this.plugin, "dungeon_editor_action"),
                PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    /** 檢查一個 ItemStack 是否是編輯器工具，回傳 action 或 null。 */
    public String getEditorToolAction(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        return stack.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(this.plugin, "dungeon_editor_action"),
                PersistentDataType.STRING);
    }

    /** 設定出生點為管理員目前站立的位置。 */
    public void adminSetSpawn(final Player admin) {
        final String dungeonId = this.editingSessions.get(admin.getUniqueId());
        if (dungeonId == null) {
            admin.sendMessage(this.msg("§c你不在編輯模式中。"));
            return;
        }
        final Location loc = admin.getLocation();
        final double[] sp = {loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()};
        this.replaceDungeonField(dungeonId, def -> new DungeonDefinition(
                def.id(), def.displayName(), def.description(), def.templateWorld(),
                def.minPlayers(), def.maxPlayers(), def.timeLimitSeconds(), def.cooldownSeconds(), def.dailyLimit(),
                sp, def.exitPoint(), def.waves(), def.bosses(), def.rewards(), def.scripts(),
                def.requiredPermission(), def.techThemed(), def.category(), def.config()
        ));
        admin.sendMessage(this.msg("§a出生點已設定為 §e" + formatLoc(loc)));
    }

    /** 設定離開副本後傳送的世界座標。需要離開編輯世界設定。此指令記錄管理員當前位置。 */
    public void adminSetExit(final Player admin) {
        final String dungeonId = this.editingSessions.get(admin.getUniqueId());
        if (dungeonId == null) {
            admin.sendMessage(this.msg("§c你不在編輯模式中。"));
            return;
        }
        final Location loc = admin.getLocation();
        final double[] ep = {loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()};
        this.replaceDungeonField(dungeonId, def -> new DungeonDefinition(
                def.id(), def.displayName(), def.description(), def.templateWorld(),
                def.minPlayers(), def.maxPlayers(), def.timeLimitSeconds(), def.cooldownSeconds(), def.dailyLimit(),
                def.spawnPoint(), ep, def.waves(), def.bosses(), def.rewards(), def.scripts(),
                def.requiredPermission(), def.techThemed(), def.category(), def.config()
        ));
        admin.sendMessage(this.msg("§a離開傳送點已設定為 §e" + formatLoc(loc)));
    }

    /** 設定等待大廳位置（在編輯世界中的相對座標）。 */
    public void adminSetLobby(final Player admin) {
        final String dungeonId = this.editingSessions.get(admin.getUniqueId());
        if (dungeonId == null) {
            admin.sendMessage(this.msg("§c你不在編輯模式中。"));
            return;
        }
        final Location loc = admin.getLocation();
        // lobby 暫存在 variables 裡，save 時寫入 YAML
        final double[] lobby = {loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()};
        this.replaceDungeonField(dungeonId, def -> new DungeonDefinition(
                def.id(), def.displayName(), def.description(), def.templateWorld(),
                def.minPlayers(), def.maxPlayers(), def.timeLimitSeconds(), def.cooldownSeconds(), def.dailyLimit(),
                lobby, def.exitPoint(), def.waves(), def.bosses(), def.rewards(), def.scripts(),
                def.requiredPermission(), def.techThemed(), def.category(), def.config()
        ));
        admin.sendMessage(this.msg("§a大廳 / 出生點已設定為 §e" + formatLoc(loc)));
    }

    /** 設定副本顯示名稱。 */
    public void adminSetName(final Player admin, final String displayName) {
        final String dungeonId = this.editingSessions.get(admin.getUniqueId());
        if (dungeonId == null) {
            admin.sendMessage(this.msg("§c你不在編輯模式中。"));
            return;
        }
        this.replaceDungeonField(dungeonId, def -> new DungeonDefinition(
                def.id(), displayName, def.description(), def.templateWorld(),
                def.minPlayers(), def.maxPlayers(), def.timeLimitSeconds(), def.cooldownSeconds(), def.dailyLimit(),
                def.spawnPoint(), def.exitPoint(), def.waves(), def.bosses(), def.rewards(), def.scripts(),
                def.requiredPermission(), def.techThemed(), def.category(), def.config()
        ));
        admin.sendMessage(this.msg("§a顯示名稱已設定為 §e" + displayName));
    }

    /** 設定限時。 */
    public void adminSetTime(final Player admin, final int seconds) {
        final String dungeonId = this.editingSessions.get(admin.getUniqueId());
        if (dungeonId == null) {
            admin.sendMessage(this.msg("§c你不在編輯模式中。"));
            return;
        }
        this.replaceDungeonField(dungeonId, def -> new DungeonDefinition(
                def.id(), def.displayName(), def.description(), def.templateWorld(),
                def.minPlayers(), def.maxPlayers(), seconds, def.cooldownSeconds(), def.dailyLimit(),
                def.spawnPoint(), def.exitPoint(), def.waves(), def.bosses(), def.rewards(), def.scripts(),
                def.requiredPermission(), def.techThemed(), def.category(), def.config()
        ));
        admin.sendMessage(this.msg("§a限時已設定為 §e" + seconds + " 秒 (" + seconds / 60 + " 分鐘)"));
    }

    /** 設定人數限制。 */
    public void adminSetPlayers(final Player admin, final int min, final int max) {
        final String dungeonId = this.editingSessions.get(admin.getUniqueId());
        if (dungeonId == null) {
            admin.sendMessage(this.msg("§c你不在編輯模式中。"));
            return;
        }
        this.replaceDungeonField(dungeonId, def -> new DungeonDefinition(
                def.id(), def.displayName(), def.description(), def.templateWorld(),
                min, max, def.timeLimitSeconds(), def.cooldownSeconds(), def.dailyLimit(),
                def.spawnPoint(), def.exitPoint(), def.waves(), def.bosses(), def.rewards(), def.scripts(),
                def.requiredPermission(), def.techThemed(), def.category(), def.config()
        ));
        admin.sendMessage(this.msg("§a人數已設定為 §e" + min + " ~ " + max));
    }

    /** 設定冷卻時間。 */
    public void adminSetCooldown(final Player admin, final int seconds) {
        final String dungeonId = this.editingSessions.get(admin.getUniqueId());
        if (dungeonId == null) {
            admin.sendMessage(this.msg("§c你不在編輯模式中。"));
            return;
        }
        this.replaceDungeonField(dungeonId, def -> new DungeonDefinition(
                def.id(), def.displayName(), def.description(), def.templateWorld(),
                def.minPlayers(), def.maxPlayers(), def.timeLimitSeconds(), seconds, def.dailyLimit(),
                def.spawnPoint(), def.exitPoint(), def.waves(), def.bosses(), def.rewards(), def.scripts(),
                def.requiredPermission(), def.techThemed(), def.category(), def.config()
        ));
        admin.sendMessage(this.msg("§a冷卻時間已設定為 §e" + seconds + " 秒"));
    }

    /**
     * 儲存編輯世界回模板目錄、寫入 YAML 設定、卸載編輯世界、傳送管理員回去。
     * 等同於 MythicDungeons 的 {@code /md save}。
     */
    public void adminSave(final Player admin) {
        final String dungeonId = this.editingSessions.get(admin.getUniqueId());
        if (dungeonId == null) {
            admin.sendMessage(this.msg("§c你不在編輯模式中。"));
            return;
        }
        final DungeonDefinition def = this.definitions.get(dungeonId);
        if (def == null) {
            admin.sendMessage(this.msg("§c定義遺失！"));
            return;
        }

        final String worldName = "dungeon_edit_" + dungeonId;

        // 清理狀態
        final Location returnLoc = this.editReturnLocations.remove(admin.getUniqueId());
        this.editingSessions.remove(admin.getUniqueId());
        this.cleanupEditorState(admin.getUniqueId());
        admin.getInventory().clear();

        // 清除指向編輯世界的重生點，避免離開後死亡被帶回編輯世界
        this.clearDungeonRespawnPoint(admin);

        // 儲存 Function Builder 功能
        if (this.editorManager != null) {
            this.editorManager.saveFunctions(dungeonId);
            this.editorManager.removeMarkers(dungeonId);
        }

        // 儲存 YAML 設定
        this.saveDungeonDefinition(dungeonId, def);

        // 傳回原位
        final Location target = (returnLoc != null && returnLoc.getWorld() != null)
                ? returnLoc : Bukkit.getWorlds().get(0).getSpawnLocation();
        admin.teleportAsync(target).thenAccept(ok -> {
            admin.sendMessage(this.msg("§a副本 §e" + dungeonId + " §a已儲存！"));
            admin.playSound(admin.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        });

        // 延遲卸載編輯世界（確保傳送完成）
        this.scheduler.runGlobalDelayed(task ->
                this.tryUnloadEditWorld(worldName, true, def.templateWorld()), 20L);
    }

    /** 放棄修改並退出編輯模式。 */
    public void adminCancel(final Player admin) {
        final String dungeonId = this.editingSessions.get(admin.getUniqueId());
        if (dungeonId == null) {
            admin.sendMessage(this.msg("§c你不在編輯模式中。"));
            return;
        }

        final String worldName = "dungeon_edit_" + dungeonId;

        // 清理功能標記
        if (this.editorManager != null) {
            this.editorManager.removeMarkers(dungeonId);
        }

        // 清理狀態
        final Location returnLoc = this.editReturnLocations.remove(admin.getUniqueId());
        this.editingSessions.remove(admin.getUniqueId());
        this.cleanupEditorState(admin.getUniqueId());
        admin.getInventory().clear();

        // 清除指向編輯世界的重生點，避免離開後死亡被帶回編輯世界
        this.clearDungeonRespawnPoint(admin);

        // 傳回原位
        final Location target = (returnLoc != null && returnLoc.getWorld() != null)
                ? returnLoc : Bukkit.getWorlds().get(0).getSpawnLocation();
        admin.teleportAsync(target).thenAccept(ok ->
                admin.sendMessage(this.msg("§e已取消編輯 §c" + dungeonId + " §e（修改未儲存）。")));

        // 延遲卸載編輯世界
        this.scheduler.runGlobalDelayed(task ->
                this.tryUnloadEditWorld(worldName, false, null), 20L);
    }

    /** 刪除副本定義（含模板目錄）。 */
    public void adminDelete(final Player admin, final String dungeonId) {
        final DungeonDefinition def = this.definitions.remove(dungeonId);
        if (def == null) {
            admin.sendMessage(this.msg("§c找不到副本：" + dungeonId));
            return;
        }
        // 刪除 YAML 條目
        final File file = new File(this.plugin.getDataFolder(), "tech-dungeons.yml");
        if (file.isFile()) {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            yaml.set(dungeonId, null);
            try { yaml.save(file); } catch (final IOException ignored) {}
        }
        // 刪除模板
        final File templateDir = new File(this.plugin.getDataFolder(),
                TEMPLATE_DIR + File.separator + def.templateWorld());
        if (templateDir.exists()) this.deleteWorldFolder(templateDir);

        admin.sendMessage(this.msg("§a副本 §e" + dungeonId + " §a已刪除。"));
    }

    /** 列出所有已定義的副本（管理員視角，顯示更多資訊）。 */
    public void adminList(final Player admin) {
        admin.sendMessage(this.msg("§6=== 副本管理列表 ==="));
        if (this.definitions.isEmpty()) {
            admin.sendMessage(this.msg("§7（無副本定義）"));
            return;
        }
        for (final DungeonDefinition def : this.definitions.values()) {
            final boolean hasTemplate = new File(this.plugin.getDataFolder(),
                    TEMPLATE_DIR + File.separator + def.templateWorld()).isDirectory();
            final String templateIcon = hasTemplate ? "§a✔" : "§c✖";
            admin.sendMessage(this.msg("§e" + def.id() + " §7- " + def.displayName()
                    + " §7[" + def.minPlayers() + "-" + def.maxPlayers() + "人]"
                    + " §7模板:" + templateIcon
                    + " §7波次:" + def.waves().size()
                    + " §7Boss:" + def.bosses().size()));
        }
    }

    // ── 編輯輔助 ──

    /** GUI 點擊音效。 */
    private void clickSound(final Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    private void teleportBackFromEdit(final Player admin) {
        final Location ret = this.editReturnLocations.get(admin.getUniqueId());
        if (ret != null && ret.getWorld() != null) {
            admin.teleportAsync(ret);
        } else {
            admin.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
    }

    @FunctionalInterface
    private interface DungeonTransformer {
        DungeonDefinition apply(DungeonDefinition def);
    }

    private void replaceDungeonField(final String dungeonId, final DungeonTransformer transformer) {
        final DungeonDefinition old = this.definitions.get(dungeonId);
        if (old == null) return;
        this.definitions.put(dungeonId, transformer.apply(old));
        // 如有編輯者正在此副本，即時重新整理場景設定標記
        if (this.editorManager != null && this.hasEditorsIn(dungeonId)) {
            this.editorManager.refreshMarkers(dungeonId);
        }
    }

    private void saveDungeonDefinition(final String dungeonId, final DungeonDefinition def) {
        final File file = new File(this.plugin.getDataFolder(), "tech-dungeons.yml");
        YamlConfiguration yaml;
        if (file.isFile()) {
            yaml = YamlConfiguration.loadConfiguration(file);
        } else {
            yaml = new YamlConfiguration();
        }
        final ConfigurationSection section = yaml.createSection(dungeonId);
        section.set("display-name", def.displayName());
        section.set("description", def.description());
        section.set("template-world", def.templateWorld());
        section.set("category", def.category());
        section.set("tech-themed", def.techThemed());
        section.set("min-players", def.minPlayers());
        section.set("max-players", def.maxPlayers());
        section.set("time-limit", def.timeLimitSeconds());
        section.set("cooldown", def.cooldownSeconds());
        section.set("daily-limit", def.dailyLimit());
        section.set("spawn-point", this.doubleArrayToList(def.spawnPoint()));
        if (def.exitPoint() != null) {
            section.set("exit-point", this.doubleArrayToList(def.exitPoint()));
        }
        if (def.requiredPermission() != null) {
            section.set("required-permission", def.requiredPermission());
        }
        // waves / bosses / rewards / scripts — 總是從記憶體寫入最新狀態
        this.saveWavesList(section, def.waves());
        this.saveBossesList(section, def.bosses());
        this.saveRewardsList(section, def.rewards());
        this.saveScriptsList(section, def.scripts());
        // ── 儲存 Config ──
        this.saveDungeonConfig(section, def.config());

        // ── 儲存 Function Builder 功能 ──
        if (this.editorManager != null) {
            this.editorManager.saveFunctions(dungeonId);
        }

        try {
            yaml.save(file);
        } catch (final IOException e) {
            this.plugin.getLogger().warning("[副本] 儲存 tech-dungeons.yml 失敗：" + e.getMessage());
        }
    }

    /** 將波次列表寫入 YAML。 */
    private void saveWavesList(final ConfigurationSection parent, final List<WaveDefinition> waves) {
        if (waves == null || waves.isEmpty()) {
            parent.set("waves", List.of());
            return;
        }
        final List<Map<String, Object>> list = new ArrayList<>();
        for (final WaveDefinition w : waves) {
            final Map<String, Object> wMap = new LinkedHashMap<>();
            wMap.put("wave-index", w.waveIndex());
            wMap.put("spawn-delay", w.spawnDelay());
            if (w.message() != null && !w.message().isEmpty()) wMap.put("message", w.message());
            wMap.put("require-clear", w.requireClear());
            if (w.spawnLocation() != null) {
                wMap.put("spawn-location", List.of(w.spawnLocation()[0], w.spawnLocation()[1], w.spawnLocation()[2]));
            }
            final List<Map<String, Object>> mobList = new ArrayList<>();
            for (final MobEntry m : w.mobEntries()) {
                final Map<String, Object> mMap = new LinkedHashMap<>();
                mMap.put("type", m.entityType());
                mMap.put("count", m.count());
                if (m.customName() != null && !m.customName().isEmpty()) mMap.put("custom-name", m.customName());
                if (m.level() > 0) mMap.put("level", m.level());
                if (m.mythicMobId() != null && !m.mythicMobId().isEmpty()) mMap.put("mythic-mob-id", m.mythicMobId());
                if (m.spawnOffset() != null) mMap.put("spawn-offset", List.of(m.spawnOffset()[0], m.spawnOffset()[1], m.spawnOffset()[2]));
                if (m.equipment() != null && !m.equipment().isEmpty()) mMap.put("equipment", m.equipment());
                mobList.add(mMap);
            }
            wMap.put("mobs", mobList);
            list.add(wMap);
        }
        parent.set("waves", list);
    }

    /** 將 Boss 列表寫入 YAML。 */
    private void saveBossesList(final ConfigurationSection parent, final List<BossDefinition> bosses) {
        if (bosses == null || bosses.isEmpty()) {
            parent.set("bosses", List.of());
            return;
        }
        final List<Map<String, Object>> list = new ArrayList<>();
        for (final BossDefinition b : bosses) {
            final Map<String, Object> bMap = new LinkedHashMap<>();
            bMap.put("id", b.id());
            bMap.put("display-name", b.displayName());
            bMap.put("entity-type", b.entityType());
            bMap.put("health", b.health());
            bMap.put("damage", b.damage());
            bMap.put("after-wave", b.afterWave());
            bMap.put("boss-bar", b.bossBar());
            if (b.mythicMobId() != null && !b.mythicMobId().isEmpty()) bMap.put("mythic-mob-id", b.mythicMobId());
            if (b.skills() != null && !b.skills().isEmpty()) {
                final List<Map<String, Object>> sList = new ArrayList<>();
                for (final BossSkill s : b.skills()) {
                    final Map<String, Object> sMap = new LinkedHashMap<>();
                    sMap.put("id", s.id());
                    sMap.put("type", s.type());
                    sMap.put("cooldown-ticks", s.cooldownTicks());
                    sMap.put("radius", s.radius());
                    sMap.put("damage", s.damage());
                    if (s.message() != null) sMap.put("message", s.message());
                    if (s.params() != null) sMap.put("params", s.params());
                    sList.add(sMap);
                }
                bMap.put("skills", sList);
            }
            if (b.phases() != null && !b.phases().isEmpty()) {
                final List<Map<String, Object>> pList = new ArrayList<>();
                for (final BossPhase p : b.phases()) {
                    final Map<String, Object> pMap = new LinkedHashMap<>();
                    pMap.put("health-threshold", p.healthThreshold());
                    pMap.put("damage-multiplier", p.damageMultiplier());
                    pMap.put("speed-multiplier", p.speedMultiplier());
                    if (p.message() != null) pMap.put("message", p.message());
                    pList.add(pMap);
                }
                bMap.put("phases", pList);
            }
            if (b.spawnPoint() != null) {
                bMap.put("spawn-point", List.of(b.spawnPoint()[0], b.spawnPoint()[1], b.spawnPoint()[2]));
            }
            if (b.lootTable() != null && !b.lootTable().isEmpty()) bMap.put("loot-table", b.lootTable());
            list.add(bMap);
        }
        parent.set("bosses", list);
    }

    /** 將獎勵列表寫入 YAML。 */
    private void saveRewardsList(final ConfigurationSection parent, final List<RewardDefinition> rewards) {
        if (rewards == null || rewards.isEmpty()) {
            parent.set("rewards", List.of());
            return;
        }
        final List<Map<String, Object>> list = new ArrayList<>();
        for (final RewardDefinition r : rewards) {
            final Map<String, Object> rMap = new LinkedHashMap<>();
            rMap.put("type", r.type());
            if (r.value() != null) rMap.put("value", r.value());
            if (r.amount() > 0) rMap.put("amount", r.amount());
            if (r.chance() < 1.0) rMap.put("chance", r.chance());
            rMap.put("first-clear-only", r.firstClearOnly());
            list.add(rMap);
        }
        parent.set("rewards", list);
    }

    /** 將腳本列表寫入 YAML。 */
    private void saveScriptsList(final ConfigurationSection parent, final List<ScriptDefinition> scripts) {
        if (scripts == null || scripts.isEmpty()) {
            parent.set("scripts", List.of());
            return;
        }
        final List<Map<String, Object>> list = new ArrayList<>();
        for (final ScriptDefinition s : scripts) {
            final Map<String, Object> sMap = new LinkedHashMap<>();
            sMap.put("id", s.id());
            sMap.put("trigger", s.trigger());
            sMap.put("one-shot", s.oneShot());
            sMap.put("delay-ticks", s.delayTicks());
            if (s.conditions() != null && !s.conditions().isEmpty()) {
                final List<Map<String, Object>> cList = new ArrayList<>();
                for (final ScriptCondition c : s.conditions()) {
                    final Map<String, Object> cMap = new LinkedHashMap<>();
                    cMap.put("type", c.type());
                    if (c.params() != null) cMap.put("params", c.params());
                    cList.add(cMap);
                }
                sMap.put("conditions", cList);
            }
            if (s.actions() != null && !s.actions().isEmpty()) {
                final List<Map<String, Object>> aList = new ArrayList<>();
                for (final ScriptAction a : s.actions()) {
                    final Map<String, Object> aMap = new LinkedHashMap<>();
                    aMap.put("type", a.type());
                    if (a.params() != null) aMap.put("params", a.params());
                    aList.add(aMap);
                }
                sMap.put("actions", aList);
            }
            list.add(sMap);
        }
        parent.set("scripts", list);
    }

    /** 將 DungeonConfig 寫入 YAML section。 */
    private void saveDungeonConfig(final ConfigurationSection parent, final DungeonConfig cfg) {
        if (cfg == null) return;
        final ConfigurationSection cs = parent.createSection("config");

        cs.set("dungeon-type", cfg.dungeonType());
        cs.set("show-title-on-start", cfg.showTitleOnStart());
        if (cfg.lobbyPoint() != null) cs.set("lobby-point", this.doubleArrayToList(cfg.lobbyPoint()));
        if (cfg.exitLocation() != null) cs.set("exit-location", this.doubleArrayToList(cfg.exitLocation()));
        cs.set("always-use-exit", cfg.alwaysUseExit());
        cs.set("gamemode", cfg.gamemode());
        cs.set("player-lives", cfg.playerLives());
        cs.set("instant-respawn", cfg.instantRespawn());
        cs.set("dead-players-spectate", cfg.deadPlayersSpectate());
        cs.set("close-when-all-spectating", cfg.closeDungeonWhenAllSpectating());
        if (cfg.respawnPoint() != null) cs.set("respawn-point", this.doubleArrayToList(cfg.respawnPoint()));
        cs.set("keep-inventory-on-enter", cfg.keepInventoryOnEnter());
        cs.set("keep-health-on-enter", cfg.keepHealthOnEnter());
        cs.set("keep-food-on-enter", cfg.keepFoodOnEnter());
        cs.set("keep-potion-effects-on-enter", cfg.keepPotionEffectsOnEnter());
        cs.set("keep-exp-on-enter", cfg.keepExpOnEnter());
        cs.set("kick-offline-players", cfg.kickOfflinePlayers());
        cs.set("kick-offline-delay", cfg.kickOfflinePlayersDelay());
        cs.set("max-backups", cfg.maxBackups());
        cs.set("max-instances", cfg.maxInstances());
        cs.set("cleanup-delay", cfg.cleanupDelay());

        // 獎勵冷卻
        final ConfigurationSection lootCd = cs.createSection("loot-cooldown");
        lootCd.set("enabled", cfg.lootCooldownEnabled());
        lootCd.set("per-reward", cfg.lootCooldownPerReward());
        lootCd.set("type", cfg.lootCooldownType());
        lootCd.set("time", cfg.lootCooldownTime());
        lootCd.set("reset-day", cfg.lootCooldownResetDay());

        // 進入冷卻
        final ConfigurationSection accessCd = cs.createSection("access-cooldown");
        accessCd.set("enabled", cfg.accessCooldownEnabled());
        accessCd.set("on-finish", cfg.accessCooldownOnFinish());
        accessCd.set("on-leave", cfg.accessCooldownOnLeave());
        accessCd.set("on-lose-lives", cfg.accessCooldownOnLoseLives());
        accessCd.set("on-start", cfg.accessCooldownOnStart());
        accessCd.set("type", cfg.accessCooldownType());
        accessCd.set("time", cfg.accessCooldownTime());
        accessCd.set("reset-day", cfg.accessCooldownResetDay());

        // 要求
        final ConfigurationSection req = cs.createSection("requirements");
        req.set("permissions", cfg.requiredPermissions());
        req.set("min-party-size", cfg.minPartySize());
        req.set("max-party-size", cfg.maxPartySize());
        req.set("dungeons-complete", cfg.requiredDungeons());
        req.set("cost", cfg.cost());
        req.set("leader-only-cost", cfg.leaderOnlyCost());

        // 鑰匙
        final ConfigurationSection keys = cs.createSection("access-keys");
        keys.set("consume", cfg.accessKeysConsume());
        keys.set("leader-only", cfg.accessKeysLeaderOnly());
        keys.set("items", cfg.accessKeyItems());

        // 難度
        final ConfigurationSection diff = cs.createSection("difficulty");
        diff.set("enable-levels", cfg.enableDifficultyLevels());
        diff.set("enable-menu", cfg.enableDifficultyMenu());
        if (!cfg.difficultyLevels().isEmpty()) {
            final ConfigurationSection levels = diff.createSection("levels");
            for (final DungeonConfig.DifficultyLevel lvl : cfg.difficultyLevels()) {
                final ConfigurationSection lvlSec = levels.createSection(lvl.id());
                lvlSec.set("display-name", lvl.displayName());
                lvlSec.set("mob-health", lvl.mobHealth());
                lvlSec.set("mob-amounts", lvl.mobAmounts());
                lvlSec.set("mob-damage", lvl.mobDamage());
                lvlSec.set("bonus-mythic-levels", lvl.bonusMythicLevels());
                lvlSec.set("bonus-loot", lvl.bonusLoot());
            }
        }

        // 地圖
        final ConfigurationSection map = cs.createSection("map");
        map.set("give-on-join", cfg.mapGiveOnJoin());
        map.set("max-render-depth", cfg.mapMaxRenderDepth());
        map.set("display-floors", cfg.mapDisplayFloors());
        map.set("floor-depth", cfg.mapFloorDepth());
        map.set("show-all-players", cfg.mapShowAllPlayers());

        // 規則
        final DungeonConfig.DungeonRules r = cfg.rules();
        final ConfigurationSection rules = cs.createSection("rules");
        rules.set("spawn-mobs", r.spawnMobs());
        rules.set("spawn-animals", r.spawnAnimals());
        rules.set("spawn-monsters", r.spawnMonsters());
        rules.set("allow-break-blocks", r.allowBreakBlocks());
        rules.set("allow-place-blocks", r.allowPlaceBlocks());
        rules.set("allow-enderpearl", r.allowEnderpearl());
        rules.set("allow-chorus-fruit", r.allowChorusFruit());
        rules.set("allow-bucket", r.allowBucket());
        rules.set("allow-place-entities", r.allowPlaceEntities());
        rules.set("disable-random-tick", r.disableRandomTick());
        rules.set("prevent-plant-growth", r.preventPlantGrowth());
        rules.set("prevent-explosion-block-damage", r.preventExplosionBlockDamage());
        rules.set("pvp", r.pvp());
        rules.set("hide-death-messages", r.hideDeathMessages());
        rules.set("prevent-teleport-in", r.preventTeleportIn());
        rules.set("allow-commands", r.allowCommands());
        rules.set("allowed-commands", r.allowedCommands());
        rules.set("disallowed-commands", r.disallowedCommands());
        rules.set("banned-items", r.bannedItems());
        rules.set("custom-banned-items", r.customBannedItems());
        rules.set("block-place-whitelist", r.blockPlaceWhitelist());
        rules.set("block-place-blacklist", r.blockPlaceBlacklist());
        rules.set("block-break-whitelist", r.blockBreakWhitelist());
        rules.set("block-break-blacklist", r.blockBreakBlacklist());
        rules.set("allow-break-placed-blocks", r.allowBreakPlacedBlocks());
        rules.set("damage-protected-entities", r.damageProtectedEntities());
        rules.set("interact-protected-entities", r.interactProtectedEntities());
        rules.set("prevent-durability-loss", r.preventDurabilityLoss());
    }

    private List<Double> doubleArrayToList(final double[] arr) {
        if (arr == null) return List.of();
        final List<Double> list = new ArrayList<>(arr.length);
        for (final double v : arr) list.add(v);
        return list;
    }

    private static String formatLoc(final Location loc) {
        return String.format("%.1f, %.1f, %.1f (yaw=%.0f)", loc.getX(), loc.getY(), loc.getZ(), loc.getYaw());
    }

    /** 翻譯腳本觸發事件名稱為中文。 */
    private String translateScriptTrigger(final String trigger) {
        if (trigger == null) return "未設定";
        return switch (trigger.toUpperCase()) {
            case "WAVE_CLEAR" -> "波次清除";
            case "BOSS_DEATH" -> "Boss死亡";
            case "TIMER" -> "計時器";
            case "PASSWORD_MATCH" -> "密碼匹配";
            case "INTERACT_BLOCK" -> "互動方塊";
            case "DUNGEON_START" -> "副本開始";
            case "DUNGEON_END" -> "副本結束";
            case "PLAYER_DEATH" -> "玩家死亡";
            case "MOB_DEATH" -> "怪物死亡";
            case "ENTER_AREA" -> "進入區域";
            case "LEAVE_AREA" -> "離開區域";
            case "ITEM_PICKUP" -> "撿起物品";
            case "CUSTOM" -> "自訂";
            default -> trigger;
        };
    }

    // ═══════════════════════════════════════════════════════════
    //  編輯器 GUI 系統（MythicDungeons 風格）
    // ═══════════════════════════════════════════════════════════

    /** 檢查標題是否為副本編輯器 GUI。 */
    public boolean isDungeonEditorGui(final String title) {
        return title.contains("副本編輯") || title.contains("功能編輯");
    }

    /** 檢查玩家是否正在等待編輯器聊天輸入。 */
    public boolean isAwaitingEditorInput(final UUID uuid) {
        return this.pendingEditorInput.containsKey(uuid)
                || (this.editorManager != null && this.editorManager.hasPendingInput(uuid));
    }

    /** 處理編輯器的聊天輸入。回傳 true 表示已消費。 */
    public boolean handleEditorChatInput(final Player player, final String text) {
        // 先嘗試 Function Builder 的聊天輸入
        if (this.editorManager != null && this.editorManager.hasPendingInput(player.getUniqueId())) {
            return this.editorManager.handleChatInput(player, text);
        }

        final PendingEditorInput pending = this.pendingEditorInput.remove(player.getUniqueId());
        if (pending == null) return false;

        if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("取消")) {
            player.sendMessage(this.msg("§e已取消輸入。"));
            // 重新打開上一個 GUI
            this.scheduler.runEntity(player, () -> this.openEditorGui(player, pending.returnState()));
            return true;
        }

        final DungeonDefinition def = this.definitions.get(pending.dungeonId());
        if (def == null) {
            player.sendMessage(this.msg("§c副本定義遺失。"));
            return true;
        }

        this.applyEditorInput(player, pending, def, text);
        return true;
    }

    /** 處理編輯器工具的右鍵使用。 */
    public void handleEditorToolUse(final Player player, final String action, final Block clickedBlock) {
        final String dungeonId = this.editingSessions.get(player.getUniqueId());
        if (dungeonId == null) return;

        // 檢查是否為 Function Builder 快捷欄互動
        if (this.editorManager != null && this.editorManager.isInHotbarEditor(player)) {
            this.editorManager.handleHotbarInteract(player, player.getInventory().getHeldItemSlot());
            return;
        }

        switch (action) {
            case "editor:menu" -> { this.clickSound(player); this.openEditorMainMenu(player, dungeonId); }
            case "editor:setspawn" -> {
                final Location loc = player.getLocation();
                this.replaceDungeonField(dungeonId, d -> d.withSpawnPoint(
                        new double[]{loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()}));
                player.sendMessage(this.msg("§a出生點已設定為 §e" + formatLoc(loc)));
                player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            }
            case "editor:setexit" -> {
                final Location loc = player.getLocation();
                this.replaceDungeonField(dungeonId, d -> d.withExitPoint(
                        new double[]{loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()}));
                player.sendMessage(this.msg("§a離開點已設定為 §e" + formatLoc(loc)));
                player.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
            }
            case "editor:mob_spawner" -> { this.clickSound(player); this.openEditorGui(player, new EditorGuiState(dungeonId, EditorPage.WAVES, 0, 0)); }
            case "editor:event_trigger" -> { this.clickSound(player); this.openEditorGui(player, new EditorGuiState(dungeonId, EditorPage.SCRIPTS, 0, 0)); }
            case "editor:rewards" -> { this.clickSound(player); this.openEditorGui(player, new EditorGuiState(dungeonId, EditorPage.REWARDS, 0, 0)); }
            case "editor:settings" -> { this.clickSound(player); this.openEditorGui(player, new EditorGuiState(dungeonId, EditorPage.SETTINGS, 0, 0)); }
            case "editor:save" -> this.adminSave(player);
        }
    }

    /** 處理編輯器 GUI 的點擊。 */
    public void handleEditorGuiClick(final Player player, final int rawSlot, final org.bukkit.event.inventory.ClickType clickType) {
        // 先檢查是否為 Function Builder GUI
        if (this.editorManager != null && this.editorManager.isFuncEditorGui(
                PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title()))) {
            this.editorManager.handleGuiClick(player, rawSlot);
            return;
        }

        final EditorGuiState state = this.editorGuiStates.get(player.getUniqueId());
        if (state == null) return;

        switch (state.page()) {
            case MAIN -> this.handleMainMenuClick(player, state, rawSlot);
            case SETTINGS -> this.handleSettingsClick(player, state, rawSlot);
            case WAVES -> this.handleWavesClick(player, state, rawSlot);
            case WAVE_DETAIL -> this.handleWaveDetailClick(player, state, rawSlot, clickType);
            case MOB_SELECT -> this.handleMobSelectClick(player, state, rawSlot);
            case BOSSES -> this.handleBossesClick(player, state, rawSlot);
            case BOSS_DETAIL -> this.handleBossDetailClick(player, state, rawSlot);
            case REWARDS -> this.handleRewardsClick(player, state, rawSlot);
            case SCRIPTS -> this.handleScriptsClick(player, state, rawSlot);
        }
    }

    /** 打開編輯器 GUI。 */
    public void openEditorGui(final Player player, final EditorGuiState state) {
        this.editorGuiStates.put(player.getUniqueId(), state);
        switch (state.page()) {
            case MAIN -> this.openEditorMainMenu(player, state.dungeonId());
            case SETTINGS -> this.buildSettingsGui(player, state);
            case WAVES -> this.buildWavesGui(player, state);
            case WAVE_DETAIL -> this.buildWaveDetailGui(player, state);
            case MOB_SELECT -> this.buildMobSelectGui(player, state);
            case BOSSES -> this.buildBossesGui(player, state);
            case BOSS_DETAIL -> this.buildBossDetailGui(player, state);
            case REWARDS -> this.buildRewardsGui(player, state);
            case SCRIPTS -> this.buildScriptsGui(player, state);
        }
    }

    // ── 主選單 ──

    private void openEditorMainMenu(final Player player, final String dungeonId) {
        final DungeonDefinition def = this.definitions.get(dungeonId);
        if (def == null) {
            player.sendMessage(this.msg("§c副本定義遺失。"));
            return;
        }
        final EditorGuiState state = new EditorGuiState(dungeonId, EditorPage.MAIN, 0, 0);
        this.editorGuiStates.put(player.getUniqueId(), state);

        final Inventory gui = Bukkit.createInventory(null, 27,
                Component.text(EDITOR_GUI_PREFIX + def.displayName()));

        // Row 1: Info banner
        gui.setItem(4, this.editorIcon(Material.NETHER_STAR, "§b§l" + def.displayName(),
                "§7ID: " + def.id(),
                "§7模板: " + def.templateWorld(),
                "§7分類: " + def.category(),
                "§7人數: " + def.minPlayers() + "-" + def.maxPlayers()));

        // Row 2: Main buttons
        gui.setItem(10, this.editorIcon(Material.CLOCK, "§f§l基本設定",
                "§7限時 / 人數 / 冷卻 / 名稱", "§e點擊編輯"));
        gui.setItem(11, this.editorIcon(Material.ZOMBIE_HEAD, "§6§l波次管理",
                "§7目前 " + def.waves().size() + " 個波次", "§e點擊編輯"));
        gui.setItem(12, this.editorIcon(Material.DRAGON_HEAD, "§c§lBoss 管理",
                "§7目前 " + def.bosses().size() + " 個 Boss", "§e點擊編輯"));
        gui.setItem(14, this.editorIcon(Material.CHEST, "§e§l獎勵管理",
                "§7目前 " + def.rewards().size() + " 個獎勵", "§e點擊編輯"));
        gui.setItem(15, this.editorIcon(Material.REDSTONE_TORCH, "§d§l腳本事件",
                "§7目前 " + def.scripts().size() + " 個腳本", "§e點擊編輯"));
        gui.setItem(16, this.editorIcon(Material.ENDER_PEARL, "§a§l傳送點",
                "§7出生: " + this.formatDoubleArray(def.spawnPoint()),
                "§7離開: " + (def.exitPoint() != null ? this.formatDoubleArray(def.exitPoint()) : "§c未設定")));

        // Row 3: Save / Cancel
        gui.setItem(22, this.editorIcon(Material.LIME_WOOL, "§a§l儲存並退出", "§7儲存所有修改並退出編輯模式"));
        gui.setItem(18, this.editorIcon(Material.RED_WOOL, "§c§l放棄修改", "§7放棄所有修改並退出"));

        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleMainMenuClick(final Player player, final EditorGuiState state, final int slot) {
        switch (slot) {
            case 10 -> { this.clickSound(player); this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.SETTINGS, 0, 0)); }
            case 11 -> { this.clickSound(player); this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.WAVES, 0, 0)); }
            case 12 -> { this.clickSound(player); this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.BOSSES, 0, 0)); }
            case 14 -> { this.clickSound(player); this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.REWARDS, 0, 0)); }
            case 15 -> { this.clickSound(player); this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.SCRIPTS, 0, 0)); }
            case 22 -> { player.closeInventory(); this.adminSave(player); }
            case 18 -> { player.closeInventory(); this.adminCancel(player); }
        }
    }

    // ── 基本設定 ──

    private void buildSettingsGui(final Player player, final EditorGuiState state) {
        final DungeonDefinition def = this.definitions.get(state.dungeonId());
        if (def == null) return;
        final Inventory gui = Bukkit.createInventory(null, 27,
                Component.text(EDITOR_GUI_PREFIX + "基本設定"));

        gui.setItem(10, this.editorIcon(Material.NAME_TAG, "§e§l顯示名稱",
                "§7目前: §f" + def.displayName(), "§a點擊修改（聊天輸入）"));
        gui.setItem(11, this.editorIcon(Material.CLOCK, "§6§l限時（秒）",
                "§7目前: §f" + def.timeLimitSeconds() + " 秒", "§a點擊修改"));
        gui.setItem(12, this.editorIcon(Material.PLAYER_HEAD, "§b§l最少人數",
                "§7目前: §f" + def.minPlayers(), "§a點擊修改"));
        gui.setItem(13, this.editorIcon(Material.PLAYER_HEAD, "§b§l最多人數",
                "§7目前: §f" + def.maxPlayers(), "§a點擊修改"));
        gui.setItem(14, this.editorIcon(Material.SNOWBALL, "§d§l冷卻（秒）",
                "§7目前: §f" + def.cooldownSeconds() + " 秒", "§a點擊修改"));
        gui.setItem(15, this.editorIcon(Material.BOOK, "§f§l說明文字",
                "§7目前: §f" + (def.description().length() > 30 ? def.description().substring(0, 30) + "..." : def.description()),
                "§a點擊修改（聊天輸入）"));
        gui.setItem(16, this.editorIcon(Material.EMERALD, "§2§l每日限制",
                "§7目前: §f" + def.dailyLimit() + " 次", "§a點擊修改"));

        gui.setItem(22, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleSettingsClick(final Player player, final EditorGuiState state, final int slot) {
        switch (slot) {
            case 10 -> this.promptInput(player, state, "display-name", "§e請在聊天中輸入新的顯示名稱：§7（輸入 '取消' 取消）");
            case 11 -> this.promptInput(player, state, "time-limit", "§e請在聊天中輸入限時秒數：§7（數字，輸入 '取消' 取消）");
            case 12 -> this.promptInput(player, state, "min-players", "§e請在聊天中輸入最少人數：§7（數字）");
            case 13 -> this.promptInput(player, state, "max-players", "§e請在聊天中輸入最多人數：§7（數字）");
            case 14 -> this.promptInput(player, state, "cooldown", "§e請在聊天中輸入冷卻秒數：§7（數字）");
            case 15 -> this.promptInput(player, state, "description", "§e請在聊天中輸入副本說明：");
            case 16 -> this.promptInput(player, state, "daily-limit", "§e請在聊天中輸入每日限制次數：§7（數字）");
            case 22 -> this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.MAIN, 0, 0));
        }
    }

    // ── 波次管理 ──

    private void buildWavesGui(final Player player, final EditorGuiState state) {
        final DungeonDefinition def = this.definitions.get(state.dungeonId());
        if (def == null) return;
        final int size = Math.max(27, ((def.waves().size() / 9) + 1) * 9 + 9);
        final Inventory gui = Bukkit.createInventory(null, Math.min(size, 54),
                Component.text(EDITOR_GUI_PREFIX + "波次管理"));

        for (int i = 0; i < def.waves().size() && i < 45; i++) {
            final WaveDefinition w = def.waves().get(i);
            final int totalMobs = w.mobEntries().stream().mapToInt(MobEntry::count).sum();
            gui.setItem(i, this.editorIcon(Material.SPAWNER, "§6§l波次 " + (i + 1),
                    "§7怪物種類: §f" + w.mobEntries().size(),
                    "§7怪物總數: §f" + totalMobs,
                    "§7延遲: §f" + w.spawnDelay() + " 秒",
                    "§e左鍵 編輯  §c右鍵 刪除"));
        }

        // 新增波次按鈕
        final int addSlot = Math.min(def.waves().size(), 44);
        gui.setItem(addSlot, this.editorIcon(Material.LIME_DYE, "§a§l+ 新增波次", "§7點擊新增一個空波次"));

        gui.setItem(size > 27 ? size - 5 : 22, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleWavesClick(final Player player, final EditorGuiState state, final int slot) {
        final DungeonDefinition def = this.definitions.get(state.dungeonId());
        if (def == null) return;

        final int backSlot = this.getBackSlot(player);
        if (slot == backSlot) {
            this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.MAIN, 0, 0));
            return;
        }

        // 新增波次
        if (slot == Math.min(def.waves().size(), 44)) {
            final List<WaveDefinition> newWaves = new ArrayList<>(def.waves());
            newWaves.add(new WaveDefinition(newWaves.size(), List.of(new MobEntry("ZOMBIE", null, 5, 1, new double[]{0, 1, 0}, null, Map.of())), 5, "", true, null));
            this.replaceDungeonField(state.dungeonId(), d -> d.withWaves(newWaves));
            player.sendMessage(this.msg("§a已新增波次 " + newWaves.size()));
            this.openEditorGui(player, state); // 重新打開
            return;
        }

        // 波次詳情
        if (slot >= 0 && slot < def.waves().size()) {
            this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.WAVE_DETAIL, slot, 0));
        }
    }

    // ── 波次詳情 ──

    private void buildWaveDetailGui(final Player player, final EditorGuiState state) {
        final DungeonDefinition def = this.definitions.get(state.dungeonId());
        if (def == null || state.subIndex() >= def.waves().size()) return;
        final WaveDefinition wave = def.waves().get(state.subIndex());
        final Inventory gui = Bukkit.createInventory(null, 36,
                Component.text(EDITOR_GUI_PREFIX + "波次 " + (state.subIndex() + 1)));

        gui.setItem(4, this.editorIcon(Material.CLOCK, "§e§l延遲秒數",
                "§7目前: §f" + wave.spawnDelay() + " 秒", "§a點擊修改"));

        // 生成位置設定
        final String locText = wave.spawnLocation() != null
                ? String.format("§f%.1f, %.1f, %.1f", wave.spawnLocation()[0], wave.spawnLocation()[1], wave.spawnLocation()[2])
                : "§8使用副本出生點";
        gui.setItem(5, this.editorIcon(Material.COMPASS, "§b§l生成位置",
                "§7目前: " + locText,
                "§a左鍵 設定為你的位置",
                "§e右鍵 手動輸入座標",
                "§c中鍵 清除（使用副本出生點）"));

        // 列出此波次的怪物
        for (int i = 0; i < wave.mobEntries().size() && i < 18; i++) {
            final MobEntry mob = wave.mobEntries().get(i);
            final String mythicInfo = (mob.mythicMobId() != null && !mob.mythicMobId().isEmpty())
                    ? "§dMythicMobs: §f" + mob.mythicMobId() : "§7MythicMobs: §8無";
            gui.setItem(9 + i, this.editorIcon(this.getMobIcon(mob.entityType()),
                    "§6" + mob.entityType() + (mob.mythicMobId() != null && !mob.mythicMobId().isEmpty() ? " §d[§fM§d]" : ""),
                    "§7數量: §f" + mob.count(),
                    "§7等級: §f" + mob.level(),
                    mythicInfo,
                    "§7自訂名稱: §f" + (mob.customName() != null ? mob.customName() : "無"),
                    "§e左鍵 修改數量  §c右鍵 刪除",
                    "§b中鍵 設定 MythicMobs ID"));
        }

        // 新增怪物按鈕
        if (wave.mobEntries().size() < 18) {
            gui.setItem(9 + wave.mobEntries().size(), this.editorIcon(Material.LIME_DYE, "§a§l+ 新增怪物", "§7點擊選擇怪物類型"));
        }

        // 刪除此波次
        gui.setItem(27, this.editorIcon(Material.RED_DYE, "§c§l刪除此波次", "§7移除波次 " + (state.subIndex() + 1)));
        gui.setItem(31, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleWaveDetailClick(final Player player, final EditorGuiState state, final int slot, final org.bukkit.event.inventory.ClickType clickType) {
        final DungeonDefinition def = this.definitions.get(state.dungeonId());
        if (def == null || state.subIndex() >= def.waves().size()) return;
        final WaveDefinition wave = def.waves().get(state.subIndex());

        if (slot == 31) {
            this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.WAVES, 0, 0));
            return;
        }
        if (slot == 4) {
            this.promptInput(player, state, "wave-delay", "§e請輸入波次延遲秒數：§7（數字）");
            return;
        }
        // 生成位置
        if (slot == 5) {
            if (clickType == org.bukkit.event.inventory.ClickType.MIDDLE) {
                // 中鍵 — 清除自訂位置
                this.replaceWave(state.dungeonId(), state.subIndex(), w ->
                        new WaveDefinition(w.waveIndex(), w.mobEntries(), w.spawnDelay(), w.message(), w.requireClear(), null));
                player.sendMessage(this.msg("§e已清除波次生成位置，將使用副本出生點。"));
                this.openEditorGui(player, state);
            } else if (clickType == org.bukkit.event.inventory.ClickType.RIGHT) {
                // 右鍵 — 手動輸入座標
                this.promptInput(player, state, "wave-spawn-location", "§e請輸入生成座標：§7（格式: x y z，例: 100 65 200）");
            } else {
                // 左鍵 — 使用玩家目前位置
                final Location loc = player.getLocation();
                final double[] coords = new double[]{loc.getX(), loc.getY(), loc.getZ()};
                this.replaceWave(state.dungeonId(), state.subIndex(), w ->
                        new WaveDefinition(w.waveIndex(), w.mobEntries(), w.spawnDelay(), w.message(), w.requireClear(), coords));
                player.sendMessage(this.msg("§a已設定波次生成位置為 §e" + String.format("%.1f, %.1f, %.1f", loc.getX(), loc.getY(), loc.getZ())));
                this.openEditorGui(player, state);
            }
            return;
        }
        // 刪除波次
        if (slot == 27) {
            final List<WaveDefinition> newWaves = new ArrayList<>(def.waves());
            newWaves.remove(state.subIndex());
            this.replaceDungeonField(state.dungeonId(), d -> d.withWaves(newWaves));
            player.sendMessage(this.msg("§c已刪除波次 " + (state.subIndex() + 1)));
            this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.WAVES, 0, 0));
            return;
        }
        // 新增怪物 → 打開怪物選擇器
        if (slot == 9 + wave.mobEntries().size() && wave.mobEntries().size() < 18) {
            this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.MOB_SELECT, state.subIndex(), -1));
            return;
        }
        // 已有怪物 slot
        final int mobIndex = slot - 9;
        if (mobIndex >= 0 && mobIndex < wave.mobEntries().size()) {
            final MobEntry mob = wave.mobEntries().get(mobIndex);
            if (clickType == org.bukkit.event.inventory.ClickType.MIDDLE) {
                // 中鍵 → 設定 MythicMobs ID
                this.promptInput(player,
                        new EditorGuiState(state.dungeonId(), EditorPage.WAVE_DETAIL, state.subIndex(), mobIndex),
                        "mob-mythic-id", "§e請輸入 MythicMobs 怪物 ID：§7（輸入 'none' 清除 MythicMobs 設定）");
            } else if (clickType == org.bukkit.event.inventory.ClickType.RIGHT) {
                // 右鍵 → 刪除怪物
                final List<MobEntry> newMobs = new ArrayList<>(wave.mobEntries());
                newMobs.remove(mobIndex);
                this.replaceWave(state.dungeonId(), state.subIndex(), w -> new WaveDefinition(w.waveIndex(), newMobs, w.spawnDelay(), w.message(), w.requireClear(), w.spawnLocation()));
                player.sendMessage(this.msg("§c已移除 §6" + mob.entityType()));
                this.openEditorGui(player, state);
            } else {
                // 左鍵 → 修改數量
                this.promptInput(player,
                        new EditorGuiState(state.dungeonId(), EditorPage.WAVE_DETAIL, state.subIndex(), mobIndex),
                        "mob-count", "§e請輸入 §6" + mob.entityType() + " §e的生成數量：§7（數字，輸入 0 刪除）");
            }
        }
    }

    // ── 怪物類型選擇器 ──

    private static final EntityType[] MOB_TYPES = {
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER,
            EntityType.ENDERMAN, EntityType.WITCH, EntityType.BLAZE, EntityType.WITHER_SKELETON,
            EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.RAVAGER, EntityType.PHANTOM,
            EntityType.HOGLIN, EntityType.PIGLIN_BRUTE, EntityType.WARDEN, EntityType.BREEZE,
            EntityType.CAVE_SPIDER, EntityType.SILVERFISH, EntityType.SLIME, EntityType.MAGMA_CUBE,
            EntityType.GHAST, EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN, EntityType.SHULKER,
            EntityType.VEX, EntityType.EVOKER, EntityType.HUSK, EntityType.DROWNED,
            EntityType.STRAY, EntityType.BOGGED
    };

    private void buildMobSelectGui(final Player player, final EditorGuiState state) {
        final int size = ((MOB_TYPES.length / 9) + 2) * 9 + 9;
        final Inventory gui = Bukkit.createInventory(null, Math.min(size, 54),
                Component.text(EDITOR_GUI_PREFIX + "選擇怪物類型"));
        for (int i = 0; i < MOB_TYPES.length; i++) {
            gui.setItem(i, this.editorIcon(this.getMobIcon(MOB_TYPES[i].name()),
                    "§6" + MOB_TYPES[i].name(), "§7點擊選擇"));
        }
        // MythicMobs 自訂怪物按鈕
        gui.setItem(MOB_TYPES.length, this.editorIcon(Material.BLAZE_POWDER,
                "§d§lMythicMobs 自訂怪物", "§7使用 MythicMobs 怪物 ID 生成",
                "§e點擊後輸入 MythicMobs 怪物 ID"));
        gui.setItem(Math.min(size, 54) - 5, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleMobSelectClick(final Player player, final EditorGuiState state, final int slot) {
        final int backSlot = this.getBackSlot(player);
        if (slot == backSlot) {
            this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.WAVE_DETAIL, state.subIndex(), 0));
            return;
        }
        // MythicMobs 自訂怪物按鈕
        if (slot == MOB_TYPES.length) {
            this.promptInput(player,
                    new EditorGuiState(state.dungeonId(), EditorPage.WAVE_DETAIL, state.subIndex(), 0),
                    "mythic-mob-add", "§e請輸入 MythicMobs 怪物 ID：§7（格式：ID:數量，例如 SkeletonKing:3）");
            return;
        }
        if (slot < 0 || slot >= MOB_TYPES.length) return;
        final EntityType selected = MOB_TYPES[slot];

        // 提示輸入數量
        this.promptInput(player,
                new EditorGuiState(state.dungeonId(), EditorPage.WAVE_DETAIL, state.subIndex(), 0),
                "mob-add:" + selected.name(), "§e已選擇 §6" + selected.name() + "§e，請輸入生成數量：§7（數字）");
    }

    // ── Boss 管理 ──

    private void buildBossesGui(final Player player, final EditorGuiState state) {
        final DungeonDefinition def = this.definitions.get(state.dungeonId());
        if (def == null) return;
        final Inventory gui = Bukkit.createInventory(null, 27,
                Component.text(EDITOR_GUI_PREFIX + "Boss 管理"));

        for (int i = 0; i < def.bosses().size() && i < 18; i++) {
            final BossDefinition boss = def.bosses().get(i);
            gui.setItem(i, this.editorIcon(Material.DRAGON_HEAD, "§c§l" + boss.displayName(),
                    "§7實體: §f" + boss.entityType(),
                    "§7血量: §f" + boss.health(),
                    "§7傷害: §f" + boss.damage(),
                    "§7技能: §f" + boss.skills().size(),
                    "§e點擊編輯"));
        }

        if (def.bosses().size() < 18) {
            gui.setItem(def.bosses().size(), this.editorIcon(Material.LIME_DYE, "§a§l+ 新增 Boss", "§7點擊新增"));
        }

        gui.setItem(22, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleBossesClick(final Player player, final EditorGuiState state, final int slot) {
        final DungeonDefinition def = this.definitions.get(state.dungeonId());
        if (def == null) return;

        if (slot == 22) {
            this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.MAIN, 0, 0));
            return;
        }

        // 新增 Boss
        if (slot == def.bosses().size() && def.bosses().size() < 18) {
            this.promptInput(player, state, "boss-add",
                    "§e請輸入新 Boss 的顯示名稱：§7（例如: 機械守衛）");
            return;
        }

        if (slot >= 0 && slot < def.bosses().size()) {
            this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.BOSS_DETAIL, slot, 0));
        }
    }

    // ── Boss 詳情 ──

    private void buildBossDetailGui(final Player player, final EditorGuiState state) {
        final DungeonDefinition def = this.definitions.get(state.dungeonId());
        if (def == null || state.subIndex() >= def.bosses().size()) return;
        final BossDefinition boss = def.bosses().get(state.subIndex());
        final Inventory gui = Bukkit.createInventory(null, 36,
                Component.text(EDITOR_GUI_PREFIX + "Boss: " + boss.displayName()));

        gui.setItem(10, this.editorIcon(Material.NAME_TAG, "§e§l顯示名稱",
                "§7目前: §f" + boss.displayName(), "§a點擊修改"));
        gui.setItem(11, this.editorIcon(Material.ZOMBIE_SPAWN_EGG, "§6§l實體類型",
                "§7目前: §f" + boss.entityType(), "§a點擊修改"));
        gui.setItem(12, this.editorIcon(Material.GOLDEN_APPLE, "§c§l血量",
                "§7目前: §f" + boss.health(), "§a點擊修改"));
        gui.setItem(13, this.editorIcon(Material.DIAMOND_SWORD, "§4§l傷害",
                "§7目前: §f" + boss.damage(), "§a點擊修改"));
        gui.setItem(14, this.editorIcon(Material.BLAZE_POWDER, "§d§l技能",
                "§7目前 " + boss.skills().size() + " 個技能",
                "§e（技能需在 YAML 中手動編輯）"));
        gui.setItem(15, this.editorIcon(Material.EXPERIENCE_BOTTLE, "§a§l階段",
                "§7目前 " + boss.phases().size() + " 個階段",
                "§e（階段需在 YAML 中手動編輯）"));

        gui.setItem(27, this.editorIcon(Material.RED_DYE, "§c§l刪除此 Boss", "§7§l小心！不可復原"));
        gui.setItem(31, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleBossDetailClick(final Player player, final EditorGuiState state, final int slot) {
        final DungeonDefinition def = this.definitions.get(state.dungeonId());
        if (def == null || state.subIndex() >= def.bosses().size()) return;

        if (slot == 31) {
            this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.BOSSES, 0, 0));
            return;
        }
        switch (slot) {
            case 10 -> this.promptInput(player, state, "boss-name", "§e請輸入 Boss 顯示名稱：");
            case 11 -> this.promptInput(player, state, "boss-type", "§e請輸入實體類型：§7（例如: WITHER_SKELETON）");
            case 12 -> this.promptInput(player, state, "boss-health", "§e請輸入 Boss 血量：§7（數字）");
            case 13 -> this.promptInput(player, state, "boss-damage", "§e請輸入 Boss 傷害：§7（數字）");
            case 27 -> {
                final List<BossDefinition> newBosses = new ArrayList<>(def.bosses());
                newBosses.remove(state.subIndex());
                this.replaceDungeonField(state.dungeonId(), d -> d.withBosses(newBosses));
                player.sendMessage(this.msg("§c已刪除 Boss"));
                this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.BOSSES, 0, 0));
            }
        }
    }

    // ── 獎勵管理 ──

    private void buildRewardsGui(final Player player, final EditorGuiState state) {
        final DungeonDefinition def = this.definitions.get(state.dungeonId());
        if (def == null) return;
        final Inventory gui = Bukkit.createInventory(null, 27,
                Component.text(EDITOR_GUI_PREFIX + "獎勵管理"));

        for (int i = 0; i < def.rewards().size() && i < 18; i++) {
            final RewardDefinition r = def.rewards().get(i);
            gui.setItem(i, this.editorIcon(Material.CHEST, "§e§l獎勵 " + (i + 1),
                    "§7類型: §f" + r.type(),
                    "§7值: §f" + r.value(),
                    "§7機率: §f" + (int) (r.chance() * 100) + "%",
                    "§c右鍵 刪除"));
        }

        if (def.rewards().size() < 18) {
            gui.setItem(def.rewards().size(), this.editorIcon(Material.LIME_DYE, "§a§l+ 新增獎勵",
                    "§7可用類型: command / item / exp / money"));
        }

        gui.setItem(22, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleRewardsClick(final Player player, final EditorGuiState state, final int slot) {
        final DungeonDefinition def = this.definitions.get(state.dungeonId());
        if (def == null) return;

        if (slot == 22) {
            this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.MAIN, 0, 0));
            return;
        }

        // 新增獎勵
        if (slot == def.rewards().size() && def.rewards().size() < 18) {
            this.promptInput(player, state, "reward-add",
                    "§e請輸入獎勵（格式: 類型 值 機率）\n§7範例: §fcommand give {player} diamond 5 1.0\n§7範例: §fexp 500 0.5");
            return;
        }

        // 刪除已有獎勵
        if (slot >= 0 && slot < def.rewards().size()) {
            final List<RewardDefinition> newRewards = new ArrayList<>(def.rewards());
            newRewards.remove(slot);
            this.replaceDungeonField(state.dungeonId(), d -> d.withRewards(newRewards));
            player.sendMessage(this.msg("§c已刪除獎勵 " + (slot + 1)));
            this.openEditorGui(player, state);
        }
    }

    // ── 腳本事件 ──

    private void buildScriptsGui(final Player player, final EditorGuiState state) {
        final DungeonDefinition def = this.definitions.get(state.dungeonId());
        if (def == null) return;
        final Inventory gui = Bukkit.createInventory(null, 27,
                Component.text(EDITOR_GUI_PREFIX + "腳本事件"));

        for (int i = 0; i < def.scripts().size() && i < 18; i++) {
            final ScriptDefinition s = def.scripts().get(i);
            final String triggerDisplay = this.translateScriptTrigger(s.trigger());
            gui.setItem(i, this.editorIcon(Material.REDSTONE_TORCH, "§d§l" + s.id(),
                    "§7觸發: §f" + triggerDisplay,
                    "§7條件: §f" + s.conditions().size(),
                    "§7動作: §f" + s.actions().size(),
                    "§e（腳本詳情需在 YAML 中編輯）",
                    "§c點擊刪除"));
        }

        if (def.scripts().size() < 18) {
            gui.setItem(def.scripts().size(), this.editorIcon(Material.LIME_DYE, "§a§l+ 新增腳本",
                    "§7點擊輸入腳本 ID 和觸發事件"));
        }

        gui.setItem(22, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleScriptsClick(final Player player, final EditorGuiState state, final int slot) {
        final DungeonDefinition def = this.definitions.get(state.dungeonId());
        if (def == null) return;

        if (slot == 22) {
            this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.MAIN, 0, 0));
            return;
        }

        // 新增腳本
        if (slot == def.scripts().size() && def.scripts().size() < 18) {
            this.promptInput(player, state, "script-add",
                    "§e請輸入腳本（格式: ID 觸發事件）\n§7範例: §fopen_door 波次清除\n§7可用觸發: 波次清除, Boss死亡, 計時器, 密碼匹配, 互動方塊");
            return;
        }

        // 刪除腳本
        if (slot >= 0 && slot < def.scripts().size()) {
            final List<ScriptDefinition> newScripts = new ArrayList<>(def.scripts());
            newScripts.remove(slot);
            this.replaceDungeonField(state.dungeonId(), d -> d.withScripts(newScripts));
            player.sendMessage(this.msg("§c已刪除腳本 " + (slot + 1)));
            this.openEditorGui(player, state);
        }
    }

    // ── 聊天輸入提示 ──

    private void promptInput(final Player player, final EditorGuiState returnState, final String field, final String message) {
        final String dungeonId = this.editingSessions.get(player.getUniqueId());
        if (dungeonId == null) return;
        this.pendingEditorInput.put(player.getUniqueId(),
                new PendingEditorInput(dungeonId, field, returnState));
        player.closeInventory();
        player.sendMessage(this.msg(""));
        player.sendMessage(this.msg(message));
        player.sendMessage(this.msg("§8輸入 '取消' 以取消操作"));
        player.sendMessage(this.msg(""));
    }

    private void applyEditorInput(final Player player, final PendingEditorInput pending,
                                  final DungeonDefinition def, final String text) {
        final String field = pending.field();
        final String did = pending.dungeonId();
        boolean success = true;

        switch (field) {
            case "display-name" -> this.replaceDungeonField(did, d -> d.withDisplayName(text));
            case "description" -> this.replaceDungeonField(did, d -> d.withDescription(text));
            case "time-limit" -> {
                final int val = this.parseIntOrZero(text);
                if (val <= 0) { player.sendMessage(this.msg("§c無效數字。")); success = false; break; }
                this.replaceDungeonField(did, d -> d.withTimeLimitSeconds(val));
            }
            case "min-players" -> {
                final int val = this.parseIntOrZero(text);
                if (val <= 0) { player.sendMessage(this.msg("§c無效數字。")); success = false; break; }
                this.replaceDungeonField(did, d -> d.withMinPlayers(val));
            }
            case "max-players" -> {
                final int val = this.parseIntOrZero(text);
                if (val <= 0) { player.sendMessage(this.msg("§c無效數字。")); success = false; break; }
                this.replaceDungeonField(did, d -> d.withMaxPlayers(val));
            }
            case "cooldown" -> {
                final int val = this.parseIntOrZero(text);
                if (val < 0) { player.sendMessage(this.msg("§c無效數字。")); success = false; break; }
                this.replaceDungeonField(did, d -> d.withCooldownSeconds(val));
            }
            case "daily-limit" -> {
                final int val = this.parseIntOrZero(text);
                if (val < 0) { player.sendMessage(this.msg("§c無效數字。")); success = false; break; }
                this.replaceDungeonField(did, d -> d.withDailyLimit(val));
            }
            case "wave-delay" -> {
                final int val = this.parseIntOrZero(text);
                if (val < 0) { player.sendMessage(this.msg("§c無效數字。")); success = false; break; }
                this.replaceWave(did, pending.returnState().subIndex(), w ->
                        new WaveDefinition(w.waveIndex(), w.mobEntries(), val, w.message(), w.requireClear(), w.spawnLocation()));
            }
            case "wave-spawn-location" -> {
                final String[] parts = text.trim().split("\\s+");
                if (parts.length < 3) { player.sendMessage(this.msg("§c格式錯誤，需要 3 個數字：x y z")); success = false; break; }
                try {
                    final double x = Double.parseDouble(parts[0]);
                    final double y = Double.parseDouble(parts[1]);
                    final double z = Double.parseDouble(parts[2]);
                    this.replaceWave(did, pending.returnState().subIndex(), w ->
                            new WaveDefinition(w.waveIndex(), w.mobEntries(), w.spawnDelay(), w.message(), w.requireClear(), new double[]{x, y, z}));
                    player.sendMessage(this.msg("§a已設定波次生成位置為 §e" + String.format("%.1f, %.1f, %.1f", x, y, z)));
                } catch (final NumberFormatException e) {
                    player.sendMessage(this.msg("§c無效座標格式。")); success = false;
                }
            }
            case "mob-count" -> {
                final int val = this.parseIntOrZero(text);
                final int waveIdx = pending.returnState().subIndex();
                final int mobIdx = pending.returnState().subIndex2();
                final DungeonDefinition d2 = this.definitions.get(did);
                if (d2 == null || waveIdx >= d2.waves().size()) break;
                final WaveDefinition wave = d2.waves().get(waveIdx);
                if (mobIdx >= wave.mobEntries().size()) break;
                final List<MobEntry> newMobs = new ArrayList<>(wave.mobEntries());
                if (val <= 0) {
                    newMobs.remove(mobIdx);
                    player.sendMessage(this.msg("§c已移除怪物。"));
                } else {
                    final MobEntry old = newMobs.get(mobIdx);
                    newMobs.set(mobIdx, new MobEntry(old.entityType(), old.mythicMobId(), val, old.level(), old.spawnOffset(), old.customName(), old.equipment()));
                }
                this.replaceWave(did, waveIdx, w -> new WaveDefinition(w.waveIndex(), newMobs, w.spawnDelay(), w.message(), w.requireClear(), w.spawnLocation()));
            }
            case "boss-add" -> {
                final List<BossDefinition> newBosses = new ArrayList<>(def.bosses());
                newBosses.add(new BossDefinition("boss_" + newBosses.size(), text, "WITHER_SKELETON", null, 200, 15.0,
                        new double[]{0, 65, 0}, List.of(), List.of(), -1, true, List.of()));
                this.replaceDungeonField(did, d -> d.withBosses(newBosses));
                player.sendMessage(this.msg("§a已新增 Boss: §e" + text));
            }
            case "boss-name" -> {
                this.replaceBoss(did, pending.returnState().subIndex(), b ->
                        new BossDefinition(b.id(), text, b.entityType(), b.mythicMobId(), b.health(), b.damage(), b.spawnPoint(), b.skills(), b.phases(), b.afterWave(), b.bossBar(), b.lootTable()));
            }
            case "boss-type" -> {
                try { EntityType.valueOf(text.toUpperCase()); } catch (final Exception e) {
                    player.sendMessage(this.msg("§c無效實體類型。")); success = false; break;
                }
                this.replaceBoss(did, pending.returnState().subIndex(), b ->
                        new BossDefinition(b.id(), b.displayName(), text.toUpperCase(), b.mythicMobId(), b.health(), b.damage(), b.spawnPoint(), b.skills(), b.phases(), b.afterWave(), b.bossBar(), b.lootTable()));
            }
            case "boss-health" -> {
                final int val = this.parseIntOrZero(text);
                if (val <= 0) { player.sendMessage(this.msg("§c無效數字。")); success = false; break; }
                this.replaceBoss(did, pending.returnState().subIndex(), b ->
                        new BossDefinition(b.id(), b.displayName(), b.entityType(), b.mythicMobId(), val, b.damage(), b.spawnPoint(), b.skills(), b.phases(), b.afterWave(), b.bossBar(), b.lootTable()));
            }
            case "boss-damage" -> {
                final double val;
                try { val = Double.parseDouble(text); } catch (final NumberFormatException e) {
                    player.sendMessage(this.msg("§c無效數字。")); success = false; break;
                }
                this.replaceBoss(did, pending.returnState().subIndex(), b ->
                        new BossDefinition(b.id(), b.displayName(), b.entityType(), b.mythicMobId(), b.health(), val, b.spawnPoint(), b.skills(), b.phases(), b.afterWave(), b.bossBar(), b.lootTable()));
            }
            case "reward-add" -> {
                // 格式: 類型 值 機率
                final String[] parts = text.split("\\s+", 3);
                if (parts.length < 2) { player.sendMessage(this.msg("§c格式錯誤，需要至少: 類型 值")); success = false; break; }
                final String type = parts[0];
                final String value = parts.length >= 3 ? parts[1] : parts[1];
                final double chance = parts.length >= 3 ? this.parseDoubleOrOne(parts[2]) : 1.0;
                final List<RewardDefinition> newRewards = new ArrayList<>(def.rewards());
                newRewards.add(new RewardDefinition(type, parts.length >= 3 ? parts[1] + " " + text.substring(text.indexOf(parts[1]) + parts[1].length()).trim() : parts[1], 1, chance, false));
                this.replaceDungeonField(did, d -> d.withRewards(newRewards));
                player.sendMessage(this.msg("§a已新增獎勵: §e" + type + " " + value));
            }
            case "script-add" -> {
                final String[] parts = text.split("\\s+", 2);
                if (parts.length < 2) { player.sendMessage(this.msg("§c格式: ID 觸發事件")); success = false; break; }
                final List<ScriptDefinition> newScripts = new ArrayList<>(def.scripts());
                newScripts.add(new ScriptDefinition(parts[0], parts[1].toUpperCase(),
                        List.of(), List.of(), false, 0));
                this.replaceDungeonField(did, d -> d.withScripts(newScripts));
                player.sendMessage(this.msg("§a已新增腳本: §e" + parts[0] + " §7[" + parts[1] + "]"));
            }
            default -> {
                // mob-add:TYPE 格式
                if (field.startsWith("mob-add:")) {
                    final String mobType = field.substring("mob-add:".length());
                    final int count = this.parseIntOrZero(text);
                    if (count <= 0) { player.sendMessage(this.msg("§c無效數字。")); success = false; break; }
                    final int waveIdx = pending.returnState().subIndex();
                    final DungeonDefinition d2 = this.definitions.get(did);
                    if (d2 == null || waveIdx >= d2.waves().size()) break;
                    final List<MobEntry> newMobs = new ArrayList<>(d2.waves().get(waveIdx).mobEntries());
                    newMobs.add(new MobEntry(mobType, null, count, 1, new double[]{0, 1, 0}, null, Map.of()));
                    this.replaceWave(did, waveIdx, w -> new WaveDefinition(w.waveIndex(), newMobs, w.spawnDelay(), w.message(), w.requireClear(), w.spawnLocation()));
                    player.sendMessage(this.msg("§a已新增 §6" + mobType + " §ax" + count));
                } else if (field.equals("mythic-mob-add")) {
                    // MythicMobs 自訂怪物新增，格式：ID:數量（例如 SkeletonKing:3）或只有 ID（默認數量為 1）
                    final String[] parts = text.split(":", 2);
                    final String mythicId = parts[0].trim();
                    final int count = parts.length >= 2 ? this.parseIntOrZero(parts[1].trim()) : 1;
                    if (mythicId.isEmpty()) { player.sendMessage(this.msg("§c請輸入有效的 MythicMobs ID。")); success = false; break; }
                    if (count <= 0) { player.sendMessage(this.msg("§c無效數字。")); success = false; break; }
                    final int waveIdx = pending.returnState().subIndex();
                    final DungeonDefinition d2 = this.definitions.get(did);
                    if (d2 == null || waveIdx >= d2.waves().size()) break;
                    final List<MobEntry> newMobs = new ArrayList<>(d2.waves().get(waveIdx).mobEntries());
                    newMobs.add(new MobEntry("ZOMBIE", mythicId, count, 1, new double[]{0, 1, 0}, null, Map.of()));
                    this.replaceWave(did, waveIdx, w -> new WaveDefinition(w.waveIndex(), newMobs, w.spawnDelay(), w.message(), w.requireClear(), w.spawnLocation()));
                    player.sendMessage(this.msg("§a已新增 MythicMobs §d" + mythicId + " §ax" + count));
                } else if (field.equals("mob-mythic-id")) {
                    // 修改已有怪物的 MythicMobs ID
                    final int waveIdx = pending.returnState().subIndex();
                    final int mobIdx = pending.returnState().subIndex2();
                    final DungeonDefinition d2 = this.definitions.get(did);
                    if (d2 == null || waveIdx >= d2.waves().size()) break;
                    final WaveDefinition wave = d2.waves().get(waveIdx);
                    if (mobIdx >= wave.mobEntries().size()) break;
                    final List<MobEntry> newMobs = new ArrayList<>(wave.mobEntries());
                    final MobEntry old = newMobs.get(mobIdx);
                    final String mythicId = text.equalsIgnoreCase("none") || text.equalsIgnoreCase("null") ? null : text.trim();
                    newMobs.set(mobIdx, new MobEntry(old.entityType(), mythicId, old.count(), old.level(), old.spawnOffset(), old.customName(), old.equipment()));
                    this.replaceWave(did, waveIdx, w -> new WaveDefinition(w.waveIndex(), newMobs, w.spawnDelay(), w.message(), w.requireClear(), w.spawnLocation()));
                    if (mythicId == null) {
                        player.sendMessage(this.msg("§e已清除 §6" + old.entityType() + " §e的 MythicMobs 設定。"));
                    } else {
                        player.sendMessage(this.msg("§a已設定 §6" + old.entityType() + " §a的 MythicMobs ID 為 §d" + mythicId));
                    }
                } else {
                    player.sendMessage(this.msg("§c未知欄位: " + field));
                    success = false;
                }
            }
        }

        if (success) {
            player.sendMessage(this.msg("§a設定已更新。"));
        }
        // 1 tick 後重新打開 GUI
        this.scheduler.runEntityDelayed(player, () ->
                this.openEditorGui(player, pending.returnState()), 1L);
    }

    // ── GUI 建構輔助 ──

    private ItemStack editorIcon(final Material material, final String name, final String... loreLines) {
        final ItemStack item = new ItemStack(material);
        final var meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        final List<Component> lore = new ArrayList<>();
        for (final String line : loreLines) {
            lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack backButton() {
        return this.editorIcon(Material.ARROW, "§7§l← 返回", "§7回到上一頁");
    }

    private void fillEmpty(final Inventory gui) {
        final ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        final var meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) gui.setItem(i, filler);
        }
    }

    private int getBackSlot(final Player player) {
        final var inv = player.getOpenInventory().getTopInventory();
        return inv.getSize() - 5;
    }

    private Material getMobIcon(final String entityType) {
        return switch (entityType.toUpperCase()) {
            case "ZOMBIE" -> Material.ZOMBIE_HEAD;
            case "SKELETON" -> Material.SKELETON_SKULL;
            case "CREEPER" -> Material.CREEPER_HEAD;
            case "SPIDER", "CAVE_SPIDER" -> Material.SPIDER_EYE;
            case "ENDERMAN" -> Material.ENDER_PEARL;
            case "BLAZE" -> Material.BLAZE_ROD;
            case "WITHER_SKELETON" -> Material.WITHER_SKELETON_SKULL;
            case "WITCH" -> Material.POTION;
            case "PILLAGER" -> Material.CROSSBOW;
            case "PHANTOM" -> Material.PHANTOM_MEMBRANE;
            case "SLIME", "MAGMA_CUBE" -> Material.SLIME_BALL;
            case "GHAST" -> Material.GHAST_TEAR;
            case "GUARDIAN", "ELDER_GUARDIAN" -> Material.PRISMARINE_SHARD;
            case "WARDEN" -> Material.SCULK_CATALYST;
            case "BREEZE" -> Material.WIND_CHARGE;
            default -> Material.SPAWNER;
        };
    }

    private String formatDoubleArray(final double[] arr) {
        if (arr == null || arr.length == 0) return "§c未設定";
        return String.format("%.1f, %.1f, %.1f", arr[0], arr.length > 1 ? arr[1] : 0, arr.length > 2 ? arr[2] : 0);
    }

    private int parseIntOrZero(final String text) {
        try { return Integer.parseInt(text.trim()); } catch (final NumberFormatException e) { return 0; }
    }

    private double parseDoubleOrOne(final String text) {
        try { return Double.parseDouble(text.trim()); } catch (final NumberFormatException e) { return 1.0; }
    }

    @FunctionalInterface
    private interface WaveTransformer {
        WaveDefinition apply(WaveDefinition wave);
    }

    @FunctionalInterface
    private interface BossTransformer {
        BossDefinition apply(BossDefinition boss);
    }

    private void replaceWave(final String dungeonId, final int waveIndex, final WaveTransformer transformer) {
        final DungeonDefinition d = this.definitions.get(dungeonId);
        if (d == null || waveIndex >= d.waves().size()) return;
        final List<WaveDefinition> newWaves = new ArrayList<>(d.waves());
        newWaves.set(waveIndex, transformer.apply(newWaves.get(waveIndex)));
        this.replaceDungeonField(dungeonId, old -> old.withWaves(newWaves));
    }

    private void replaceBoss(final String dungeonId, final int bossIndex, final BossTransformer transformer) {
        final DungeonDefinition d = this.definitions.get(dungeonId);
        if (d == null || bossIndex >= d.bosses().size()) return;
        final List<BossDefinition> newBosses = new ArrayList<>(d.bosses());
        newBosses.set(bossIndex, transformer.apply(newBosses.get(bossIndex)));
        this.replaceDungeonField(dungeonId, old -> old.withBosses(newBosses));
    }

    /** 清理玩家的編輯器 GUI 狀態。 */
    public void cleanupEditorState(final UUID uuid) {
        this.editorGuiStates.remove(uuid);
        this.pendingEditorInput.remove(uuid);
        if (this.editorManager != null) {
            this.editorManager.cleanupPlayer(uuid);
        }
    }

    // ══════════════════════════════════════════════════
    //  Function Builder 編輯器橋接
    // ══════════════════════════════════════════════════

    /** 取得功能編輯器管理器。 */
    public DungeonEditorManager getEditorManager() {
        return this.editorManager;
    }

    /** 取得功能運行引擎。 */
    public DungeonFunctionEngine getFunctionEngine() {
        return this.functionEngine;
    }

    /** 取得玩家正在編輯的副本 ID。 */
    public String getEditingDungeonId(final Player player) {
        return this.editingSessions.get(player.getUniqueId());
    }

    /** 檢查指定副本是否有人正在編輯。 */
    public boolean hasEditorsIn(final String dungeonId) {
        return this.editingSessions.containsValue(dungeonId);
    }

    public void reload() {
        this.loadDefinitions();
        this.plugin.getLogger().info("[副本] 重新載入完成，共 " + this.definitions.size() + " 個副本定義。");
    }
}
