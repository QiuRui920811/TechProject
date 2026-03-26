package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.model.dungeon.*;
import com.rui.techproject.model.dungeon.DungeonDefinition.*;
import com.rui.techproject.model.dungeon.DungeonInstance.State;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.SafeScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
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
    private static final String TEMPLATE_DIR = "dungeon_templates";

    // ── 注入 ──
    private final TechProjectPlugin plugin;
    private final SafeScheduler scheduler;
    private final ItemFactoryUtil itemFactory;
    private final TechRegistry registry;
    private final PlayerProgressService progressService;

    // ── 資料 ──
    private final Map<String, DungeonDefinition> definitions = new ConcurrentHashMap<>();
    private final Map<String, DungeonInstance> instances = new ConcurrentHashMap<>();
    private final Map<UUID, DungeonParty> parties = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerPartyMap = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerInstanceMap = new ConcurrentHashMap<>();
    private final Map<UUID, DungeonPlayerData> playerData = new ConcurrentHashMap<>();
    private final Map<String, BossBar> bossBars = new ConcurrentHashMap<>();
    private final AtomicInteger instanceCounter = new AtomicInteger(0);
    private final Map<String, List<LeaderboardEntry>> leaderboards = new ConcurrentHashMap<>();

    // ── 編輯模式 ──
    /** 玩家 UUID → 正在編輯的副本 ID */
    private final Map<UUID, String> editingSessions = new ConcurrentHashMap<>();
    /** 玩家 UUID → 進入編輯模式前的位置 */
    private final Map<UUID, Location> editReturnLocations = new ConcurrentHashMap<>();

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
                          final SafeScheduler scheduler,
                          final ItemFactoryUtil itemFactory,
                          final TechRegistry registry,
                          final PlayerProgressService progressService) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.itemFactory = itemFactory;
        this.registry = registry;
        this.progressService = progressService;
        this.loadDefinitions();
    }

    public void start() {
        // 主 tick loop：每 20 tick (1 秒) 處理所有活躍副本
        this.scheduler.runGlobalTimer(task -> this.tickAllInstances(), 20L, 20L);
        // 腳本 timer 檢查：每 20 tick
        this.scheduler.runGlobalTimer(task -> this.tickScriptTimers(), 20L, 20L);
        this.plugin.getLogger().info("[副本] 系統啟動，已載入 " + this.definitions.size() + " 個副本定義。");
    }

    public void shutdown() {
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
            final var resource = this.plugin.getResource("tech-dungeons.yml");
            if (resource == null) return;
            yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
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
            section.getString("category", "冒險")
        );
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
            waves.add(new WaveDefinition(
                toInt(map.get("wave"), waves.size()),
                this.parseMobEntries((List<Map<?, ?>>) map.get("mobs")),
                toInt(map.get("spawn-delay"), 5),
                toString(map.get("message"), ""),
                toBool(map.get("require-clear"), true)
            ));
        }
        return waves;
    }

    @SuppressWarnings("unchecked")
    private List<MobEntry> parseMobEntries(final List<Map<?, ?>> list) {
        if (list == null) return List.of();
        final List<MobEntry> entries = new ArrayList<>();
        for (final Map<?, ?> map : list) {
            entries.add(new MobEntry(
                toString(map.get("entity-type"), "ZOMBIE"),
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
        // 權限檢查
        if (def.requiredPermission() != null && !leader.hasPermission(def.requiredPermission())) {
            leader.sendMessage(this.msg("§c你沒有進入此副本的權限。"));
            return;
        }
        // 是否已在副本中
        if (this.playerInstanceMap.containsKey(leader.getUniqueId())) {
            leader.sendMessage(this.msg("§c你已經在一個副本中了。"));
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

        // 人數檢查
        if (members.size() < def.minPlayers()) {
            leader.sendMessage(this.msg("§c人數不足！需要至少 " + def.minPlayers() + " 人。"));
            return;
        }
        if (members.size() > def.maxPlayers()) {
            leader.sendMessage(this.msg("§c人數超出！最多 " + def.maxPlayers() + " 人。"));
            return;
        }

        // 冷卻 + 每日次數檢查
        final String todayKey = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        for (final UUID uuid : members) {
            final DungeonPlayerData pd = this.getOrCreatePlayerData(uuid);
            pd.resetDailyIfNeeded(todayKey);
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

        // 建立實例
        final String instanceId = INSTANCE_PREFIX + this.instanceCounter.incrementAndGet();
        final DungeonInstance instance = new DungeonInstance(instanceId, def, leader.getUniqueId());
        for (final UUID uuid : members) {
            instance.addMember(uuid);
            this.playerInstanceMap.put(uuid, instanceId);
        }
        this.instances.put(instanceId, instance);

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

    private void onWorldCopied(final DungeonInstance instance) {
        final DungeonDefinition def = instance.definition();
        final String worldName = instance.instanceId();

        // 載入世界
        final WorldCreator creator = new WorldCreator(worldName);
        creator.environment(World.Environment.NORMAL);
        creator.generateStructures(false);
        final World world = Bukkit.createWorld(creator);
        if (world == null) {
            this.broadcastToInstance(instance, "§c載入副本世界失敗！");
            this.cleanupInstance(instance);
            return;
        }

        world.setAutoSave(false);
        world.setDifficulty(Difficulty.HARD);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.SHOW_DEATH_MESSAGES, false);
        world.setTime(6000L); // 正午

        instance.setInstanceWorld(world);
        instance.setState(State.COUNTDOWN);
        instance.setCountdownSeconds(COUNTDOWN_SECONDS);

        // 記錄進入時間 + 每日次數
        final String todayKey = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        for (final UUID uuid : instance.members()) {
            final DungeonPlayerData pd = this.getOrCreatePlayerData(uuid);
            pd.setLastEntryTime(def.id(), System.currentTimeMillis());
            pd.resetDailyIfNeeded(todayKey);
            pd.incrementDailyEntry(def.id());
        }

        // 傳送玩家
        final double[] sp = def.spawnPoint();
        final Location spawn = new Location(world, sp[0], sp[1], sp[2],
                sp.length > 3 ? (float) sp[3] : 0f, sp.length > 4 ? (float) sp[4] : 0f);

        for (final UUID uuid : instance.members()) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                instance.setReturnLocation(uuid, player.getLocation());
                this.scheduler.runEntity(player, () -> player.teleport(spawn));
            }
        }

        this.broadcastToInstance(instance, "§a副本已準備完成！倒數開始...");
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
            return;
        }
        this.removePlayerFromInstance(player, instance);
        player.sendMessage(this.msg("§e你已離開副本。"));
        if (instance.members().isEmpty()) {
            this.forceCloseInstance(instance, "所有玩家離開");
        }
    }

    private void removePlayerFromInstance(final Player player, final DungeonInstance instance) {
        final UUID uuid = player.getUniqueId();
        instance.removeMember(uuid);
        this.playerInstanceMap.remove(uuid);
        // 移除 BossBar
        final BossBar bar = this.bossBars.get(instance.instanceId());
        if (bar != null) bar.removeViewer(player);
        // 傳回原位
        final Location returnLoc = instance.getReturnLocation(uuid);
        if (returnLoc != null && returnLoc.getWorld() != null) {
            this.scheduler.runEntity(player, () -> player.teleport(returnLoc));
        } else {
            final World mainWorld = Bukkit.getWorlds().get(0);
            this.scheduler.runEntity(player, () -> player.teleport(mainWorld.getSpawnLocation()));
        }
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
            case COUNTDOWN -> this.tickCountdown(instance);
            case RUNNING, BOSS_FIGHT -> this.tickRunning(instance);
            case COMPLETED -> this.tickCompleted(instance);
            case FAILED -> this.tickFailed(instance);
            case CLOSING -> {} // 等待排程清理
            default -> {}
        }
    }

    private void tickCountdown(final DungeonInstance instance) {
        final int countdown = instance.countdownSeconds();
        if (countdown > 0) {
            this.broadcastTitle(instance,
                Component.text(String.valueOf(countdown), NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true),
                Component.text("副本即將開始", NamedTextColor.GRAY),
                5, 15, 5);
            this.broadcastSound(instance, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f + (COUNTDOWN_SECONDS - countdown) * 0.1f);
            instance.decrementCountdown();
            return;
        }
        // 倒數結束，開始
        instance.setState(State.RUNNING);
        instance.setStartTimeMillis(System.currentTimeMillis());
        this.broadcastTitle(instance,
            Component.text("開始！", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
            Component.empty(), 0, 20, 10);
        this.broadcastSound(instance, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1.2f);
    }

    private void tickRunning(final DungeonInstance instance) {
        instance.incrementTicks();
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
        if (waveIndex < 0 || waveIndex >= def.waves().size()) return;
        final WaveDefinition wave = def.waves().get(waveIndex);
        instance.setCurrentWave(waveIndex);

        if (wave.message() != null && !wave.message().isBlank()) {
            this.broadcastToInstance(instance, wave.message());
        }

        // 延遲生怪
        final int delayTicks = wave.spawnDelay() * 20;
        final World world = instance.instanceWorld();
        if (world == null) return;
        final double[] sp = def.spawnPoint();
        final Location baseSpawn = new Location(world, sp[0], sp[1], sp[2]);

        this.scheduler.runGlobalDelayed(task -> {
            for (final MobEntry entry : wave.mobEntries()) {
                this.spawnMobs(instance, baseSpawn, entry);
            }
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
                    instance.trackEntity(entity);
                } catch (final Exception e) {
                    this.plugin.getLogger().warning("[副本] 生怪失敗：" + e.getMessage());
                }
            });
        }
    }

    private void onWaveCleared(final DungeonInstance instance, final int waveIndex) {
        this.broadcastToInstance(instance, "§a波次 " + (waveIndex + 1) + " 已清除！");
        this.broadcastSound(instance, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);

        // 觸發波次清除腳本
        this.triggerScripts(instance, "wave_clear", Map.of("wave", waveIndex));

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
        } else if (instance.definition().bosses().isEmpty()) {
            // 沒有 Boss，波次全部清完就通關
            this.completeDungeon(instance);
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
            Component.text(bossDef.displayName()).decoration(TextDecoration.BOLD, true),
            Component.text("Boss 出現了！", NamedTextColor.RED),
            10, 40, 10);
        this.broadcastSound(instance, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);

        this.scheduler.runRegion(loc, task -> {
            try {
                final EntityType type = EntityType.valueOf(bossDef.entityType().toUpperCase());
                final Entity entity = world.spawnEntity(loc, type);
                if (entity instanceof LivingEntity living) {
                    living.customName(Component.text(bossDef.displayName()));
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
                    instance.trackEntity(entity);

                    // BossBar
                    if (bossDef.bossBar()) {
                        final BossBar bar = BossBar.bossBar(
                            Component.text(bossDef.displayName()),
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
        // 記錄清除 tick 用於延遲關閉
        final long savedTicks = instance.elapsedTicks();
        // 重置 ticks 用於延遲計時
        // 不重置 — 用現有計時
        this.broadcastTitle(instance,
            Component.text("通關！", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true),
            Component.text(instance.definition().displayName(), NamedTextColor.GOLD),
            10, 60, 20);
        instance.killAllEntities();
    }

    public void failDungeon(final DungeonInstance instance) {
        if (instance.state() == State.FAILED || instance.state() == State.CLOSING) return;
        instance.setState(State.FAILED);
        final long savedTicks = instance.elapsedTicks();
        this.broadcastTitle(instance,
            Component.text("失敗", NamedTextColor.RED).decoration(TextDecoration.BOLD, true),
            Component.text(instance.definition().displayName(), NamedTextColor.GRAY),
            10, 60, 20);
        instance.killAllEntities();
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
                final var itemDef = this.registry.getItem(reward.value());
                if (itemDef != null) {
                    final ItemStack stack = this.itemFactory.buildTechItem(itemDef);
                    stack.setAmount(reward.amount());
                    player.getInventory().addItem(stack).values().forEach(overflow ->
                        player.getWorld().dropItemNaturally(player.getLocation(), overflow));
                    player.sendMessage(this.msg("§7 + §f" + reward.value() + " x" + reward.amount()));
                } else {
                    final var machineDef = this.registry.getMachine(reward.value());
                    if (machineDef != null) {
                        for (int i = 0; i < reward.amount(); i++) {
                            player.getInventory().addItem(this.itemFactory.buildMachineItem(machineDef)).values()
                                .forEach(overflow -> player.getWorld().dropItemNaturally(player.getLocation(), overflow));
                        }
                        player.sendMessage(this.msg("§7 + §f" + reward.value() + " x" + reward.amount()));
                    }
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
        instance.killAllEntities();
        this.broadcastToInstance(instance, "§c副本關閉：" + reason);
        this.beginClose(instance);
    }

    private void cleanupInstance(final DungeonInstance instance) {
        final String instanceId = instance.instanceId();
        this.instances.remove(instanceId);
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
        // 卸載並刪除世界
        final World world = instance.instanceWorld();
        if (world != null) {
            Bukkit.unloadWorld(world, false);
            this.scheduler.runAsync(() -> this.deleteWorldFolder(new File(Bukkit.getWorldContainer(), instanceId)));
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
        // 刪除 uid.dat 以免衝突
        final File uidDat = new File(targetDir, "uid.dat");
        if (uidDat.exists()) uidDat.delete();
        final File sessionLock = new File(targetDir, "session.lock");
        if (sessionLock.exists()) sessionLock.delete();
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
                if (!"timer".equals(script.trigger())) continue;
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
            if (!trigger.equals(script.trigger())) continue;
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
            case "state" -> {
                final String expected = toString(condition.params().get("state"), "RUNNING");
                yield instance.state().name().equalsIgnoreCase(expected);
            }
            case "password" -> {
                // 密碼檢查在 chat handler 中處理，條件在此只是提取密碼供比對
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
            case "variable" -> {
                final String key = toString(condition.params().get("key"), "");
                final Object expected = condition.params().get("value");
                yield Objects.equals(instance.getVariable(key), expected)
                    || String.valueOf(instance.getVariable(key)).equals(String.valueOf(expected));
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
                    if (p != null && p.isOnline() && !p.isDead()) { anyAlive = true; break; }
                }
                yield !anyAlive;
            }
            default -> true;
        };
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
                    if (p != null) this.scheduler.runEntity(p, () -> p.teleport(loc));
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
                    } catch (final Exception e) {
                        final var itemDef = this.registry.getItem(itemId);
                        if (itemDef != null) {
                            final ItemStack stack = this.itemFactory.buildTechItem(itemDef);
                            stack.setAmount(amount);
                            p.getInventory().addItem(stack);
                        }
                    }
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
                // 可改用 delay-ticks 在腳本層面處理
            }
        }
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
            if (!"chat_password".equals(script.trigger())) continue;
            if (script.oneShot() && instance.hasFired(script.id())) continue;
            // 檢查密碼條件
            for (final ScriptCondition condition : script.conditions()) {
                if ("password".equals(condition.type())) {
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
        return false;
    }

    /** 處理方塊互動。 */
    public void handleInteract(final Player player, final Block block) {
        final String instanceId = this.playerInstanceMap.get(player.getUniqueId());
        if (instanceId == null) return;
        final DungeonInstance instance = this.instances.get(instanceId);
        if (instance == null || !instance.isActive()) return;

        final int[] blockLoc = {block.getX(), block.getY(), block.getZ()};
        this.triggerScripts(instance, "interact_block", Map.of("block_location", blockLoc));
    }

    /** 處理實體死亡（副本怪物）。 */
    public void handleEntityDeath(final Entity entity) {
        for (final DungeonInstance instance : this.instances.values()) {
            if (!instance.isActive()) continue;
            if (instance.spawnedEntities().contains(entity)) {
                instance.decrementAliveMobs();
                // 檢查是否是 Boss
                if (entity.equals(instance.activeBoss())) {
                    // Boss 死亡在 tickBoss 中自然檢測
                }
                break;
            }
        }
    }

    /** 處理玩家死亡。 */
    public void handlePlayerDeath(final Player player) {
        final String instanceId = this.playerInstanceMap.get(player.getUniqueId());
        if (instanceId == null) return;
        final DungeonInstance instance = this.instances.get(instanceId);
        if (instance == null || !instance.isActive()) return;
        this.triggerScripts(instance, "player_death", Map.of("player", player.getUniqueId()));
    }

    /** 處理玩家退出伺服器。 */
    public void handlePlayerQuit(final Player player) {
        final String instanceId = this.playerInstanceMap.get(player.getUniqueId());
        if (instanceId == null) return;
        final DungeonInstance instance = this.instances.get(instanceId);
        if (instance == null) {
            this.playerInstanceMap.remove(player.getUniqueId());
            return;
        }
        instance.removeMember(player.getUniqueId());
        this.playerInstanceMap.remove(player.getUniqueId());
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
        final DungeonDefinition def = instance.definition();
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
        final Component actionBar = Component.text(timeStr + waveStr + mobStr);
        for (final UUID uuid : instance.members()) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null) player.sendActionBar(actionBar);
        }
    }

    private Component msg(final String text) {
        return Component.text("§7[§c副本§7] §f" + text);
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
                null, false, "冒險"
        );
        this.definitions.put(dungeonId, stub);
        this.saveDungeonDefinition(dungeonId, stub);

        admin.sendMessage(this.msg("§a已建立副本骨架：§e" + dungeonId));
        admin.sendMessage(this.msg("§7模板目錄：§f" + templateDir.getAbsolutePath()));
        admin.sendMessage(this.msg("§7使用 §e/tech dg edit " + dungeonId + " §7進入編輯模式。"));
    }

    /**
     * 進入編輯模式 — 載入模板世界並傳送管理員進去。
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

        final String worldName = "dungeon_edit_" + dungeonId;
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            // 嘗試從模板複製
            final File templateDir = new File(this.plugin.getDataFolder(),
                    TEMPLATE_DIR + File.separator + def.templateWorld());
            final File editDir = new File(Bukkit.getWorldContainer(), worldName);
            if (templateDir.exists() && templateDir.isDirectory()) {
                try {
                    this.copyFolder(templateDir.toPath(), editDir.toPath());
                    // 刪除 uid.dat / session.lock
                    final File uid = new File(editDir, "uid.dat");
                    if (uid.exists()) uid.delete();
                    final File session = new File(editDir, "session.lock");
                    if (session.exists()) session.delete();
                } catch (final IOException e) {
                    admin.sendMessage(this.msg("§c複製模板世界失敗：" + e.getMessage()));
                    return;
                }
            }
            // 載入世界
            final WorldCreator creator = new WorldCreator(worldName);
            creator.environment(World.Environment.NORMAL);
            creator.generateStructures(false);
            world = Bukkit.createWorld(creator);
            if (world == null) {
                admin.sendMessage(this.msg("§c載入編輯世界失敗！如果是新副本，模板世界目錄可能是空的。"));
                admin.sendMessage(this.msg("§7請先將地圖檔案放入：§f" + templateDir.getAbsolutePath()));
                return;
            }
            world.setAutoSave(true);
            world.setDifficulty(Difficulty.PEACEFUL);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.MOB_GRIEFING, false);
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            world.setTime(6000L);
        }

        // 記錄返回位置 & 進入編輯
        this.editReturnLocations.put(admin.getUniqueId(), admin.getLocation().clone());
        this.editingSessions.put(admin.getUniqueId(), dungeonId);

        final double[] sp = def.spawnPoint();
        final Location spawn = new Location(world, sp[0], sp[1], sp[2],
                sp.length > 3 ? (float) sp[3] : 0f, sp.length > 4 ? (float) sp[4] : 0f);
        admin.teleport(spawn);
        admin.setGameMode(GameMode.CREATIVE);

        // ── 給予編輯工具 ──
        this.giveEditorTools(admin);

        admin.sendMessage(this.msg("§a已進入副本 §e" + dungeonId + " §a的編輯模式。"));
        admin.sendMessage(this.msg("§7快捷鍵盤上的工具可以右鍵使用："));
        admin.sendMessage(this.msg("§e  [1] §b主選單 §7— 右鍵打開副本編輯 GUI"));
        admin.sendMessage(this.msg("§e  [2] §a設定出生點 §7— 右鍵設定玩家出生位置"));
        admin.sendMessage(this.msg("§e  [3] §d設定離開點 §7— 右鍵設定離開傳送位置"));
        admin.sendMessage(this.msg("§e  [4] §6怪物生成器 §7— 右鍵方塊設定怪物生成點"));
        admin.sendMessage(this.msg("§e  [5] §c事件觸發器 §7— 右鍵方塊設定腳本觸發"));
        admin.sendMessage(this.msg("§e  [9] §4儲存並退出 §7— 右鍵儲存所有修改"));
    }

    /** 給予玩家編輯器工具組。 */
    private void giveEditorTools(final Player admin) {
        admin.getInventory().clear();
        admin.getInventory().setItem(0, this.buildEditorTool(Material.NETHER_STAR,       "§b§l主選單",         "§7右鍵打開副本編輯 GUI",     "editor:menu"));
        admin.getInventory().setItem(1, this.buildEditorTool(Material.ENDER_PEARL,       "§a§l設定出生點",     "§7右鍵：設定此位置為出生點", "editor:setspawn"));
        admin.getInventory().setItem(2, this.buildEditorTool(Material.CHORUS_FRUIT,      "§d§l設定離開點",     "§7右鍵：設定此位置為離開點", "editor:setexit"));
        admin.getInventory().setItem(3, this.buildEditorTool(Material.ZOMBIE_SPAWN_EGG,  "§6§l怪物生成器",     "§7右鍵方塊：設定怪物生成點", "editor:mob_spawner"));
        admin.getInventory().setItem(4, this.buildEditorTool(Material.REDSTONE_TORCH,    "§c§l事件觸發器",     "§7右鍵方塊：設定腳本事件",   "editor:event_trigger"));
        admin.getInventory().setItem(5, this.buildEditorTool(Material.CHEST,             "§e§l獎勵編輯",       "§7右鍵：編輯通關獎勵",       "editor:rewards"));
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
                def.requiredPermission(), def.techThemed(), def.category()
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
                def.requiredPermission(), def.techThemed(), def.category()
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
                def.requiredPermission(), def.techThemed(), def.category()
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
                def.requiredPermission(), def.techThemed(), def.category()
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
                def.requiredPermission(), def.techThemed(), def.category()
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
                def.requiredPermission(), def.techThemed(), def.category()
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
                def.requiredPermission(), def.techThemed(), def.category()
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
        final World editWorld = Bukkit.getWorld(worldName);

        // 先把管理員傳回原位
        this.teleportBackFromEdit(admin);

        // 儲存 YAML 設定
        this.saveDungeonDefinition(dungeonId, def);

        // 儲存並卸載編輯世界，複製回模板目錄
        if (editWorld != null) {
            editWorld.save();
            // 確保所有玩家離開
            final World fallback = Bukkit.getWorlds().get(0);
            for (final Player p : editWorld.getPlayers()) {
                p.teleport(fallback.getSpawnLocation());
            }
            Bukkit.unloadWorld(editWorld, true);

            // 複製回模板
            final File editDir = new File(Bukkit.getWorldContainer(), worldName);
            final File templateDir = new File(this.plugin.getDataFolder(),
                    TEMPLATE_DIR + File.separator + def.templateWorld());
            try {
                // 先清空舊模板
                if (templateDir.exists()) this.deleteWorldFolder(templateDir);
                this.copyFolder(editDir.toPath(), templateDir.toPath());
                // 清理編輯世界
                this.deleteWorldFolder(editDir);
            } catch (final IOException e) {
                admin.sendMessage(this.msg("§c儲存模板失敗：" + e.getMessage()));
                this.plugin.getLogger().log(Level.WARNING, "[副本] 儲存模板失敗", e);
            }
        }

        this.editingSessions.remove(admin.getUniqueId());
        this.editReturnLocations.remove(admin.getUniqueId());
        this.cleanupEditorState(admin.getUniqueId());
        admin.getInventory().clear();
        admin.sendMessage(this.msg("§a副本 §e" + dungeonId + " §a已儲存！"));
    }

    /** 放棄修改並退出編輯模式。 */
    public void adminCancel(final Player admin) {
        final String dungeonId = this.editingSessions.get(admin.getUniqueId());
        if (dungeonId == null) {
            admin.sendMessage(this.msg("§c你不在編輯模式中。"));
            return;
        }

        final String worldName = "dungeon_edit_" + dungeonId;
        final World editWorld = Bukkit.getWorld(worldName);

        // 傳回
        this.teleportBackFromEdit(admin);

        // 卸載並刪除編輯世界（不儲存）
        if (editWorld != null) {
            final World fallback = Bukkit.getWorlds().get(0);
            for (final Player p : editWorld.getPlayers()) {
                p.teleport(fallback.getSpawnLocation());
            }
            Bukkit.unloadWorld(editWorld, false);
            final File editDir = new File(Bukkit.getWorldContainer(), worldName);
            this.deleteWorldFolder(editDir);
        }

        this.editingSessions.remove(admin.getUniqueId());
        this.editReturnLocations.remove(admin.getUniqueId());
        this.cleanupEditorState(admin.getUniqueId());
        admin.getInventory().clear();
        admin.sendMessage(this.msg("§e已取消編輯 §c" + dungeonId + " §e（修改未儲存）。"));
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

    private void teleportBackFromEdit(final Player admin) {
        final Location ret = this.editReturnLocations.get(admin.getUniqueId());
        if (ret != null && ret.getWorld() != null) {
            admin.teleport(ret);
        } else {
            admin.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
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
        // waves / bosses / rewards / scripts 保留原有 YAML（不覆蓋已手動編輯的部分）
        // 只有不存在時才寫空列表
        if (!yaml.contains(dungeonId + ".waves") && def.waves().isEmpty()) {
            section.set("waves", List.of());
        }
        if (!yaml.contains(dungeonId + ".bosses") && def.bosses().isEmpty()) {
            section.set("bosses", List.of());
        }
        if (!yaml.contains(dungeonId + ".rewards") && def.rewards().isEmpty()) {
            section.set("rewards", List.of());
        }
        if (!yaml.contains(dungeonId + ".scripts") && def.scripts().isEmpty()) {
            section.set("scripts", List.of());
        }
        try {
            yaml.save(file);
        } catch (final IOException e) {
            this.plugin.getLogger().warning("[副本] 儲存 tech-dungeons.yml 失敗：" + e.getMessage());
        }
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

    // ═══════════════════════════════════════════════════════════
    //  編輯器 GUI 系統（MythicDungeons 風格）
    // ═══════════════════════════════════════════════════════════

    /** 檢查標題是否為副本編輯器 GUI。 */
    public boolean isDungeonEditorGui(final String title) {
        return title.startsWith("副本編輯");
    }

    /** 檢查玩家是否正在等待編輯器聊天輸入。 */
    public boolean isAwaitingEditorInput(final UUID uuid) {
        return this.pendingEditorInput.containsKey(uuid);
    }

    /** 處理編輯器的聊天輸入。回傳 true 表示已消費。 */
    public boolean handleEditorChatInput(final Player player, final String text) {
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

        switch (action) {
            case "editor:menu" -> this.openEditorMainMenu(player, dungeonId);
            case "editor:setspawn" -> {
                final Location loc = player.getLocation();
                this.replaceDungeonField(dungeonId, d -> d.withSpawnPoint(
                        new double[]{loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()}));
                player.sendMessage(this.msg("§a出生點已設定為 §e" + formatLoc(loc)));
            }
            case "editor:setexit" -> {
                final Location loc = player.getLocation();
                this.replaceDungeonField(dungeonId, d -> d.withExitPoint(
                        new double[]{loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch()}));
                player.sendMessage(this.msg("§a離開點已設定為 §e" + formatLoc(loc)));
            }
            case "editor:mob_spawner" -> this.openEditorGui(player, new EditorGuiState(dungeonId, EditorPage.WAVES, 0, 0));
            case "editor:event_trigger" -> this.openEditorGui(player, new EditorGuiState(dungeonId, EditorPage.SCRIPTS, 0, 0));
            case "editor:rewards" -> this.openEditorGui(player, new EditorGuiState(dungeonId, EditorPage.REWARDS, 0, 0));
            case "editor:settings" -> this.openEditorGui(player, new EditorGuiState(dungeonId, EditorPage.SETTINGS, 0, 0));
            case "editor:save" -> this.adminSave(player);
        }
    }

    /** 處理編輯器 GUI 的點擊。 */
    public void handleEditorGuiClick(final Player player, final int rawSlot) {
        final EditorGuiState state = this.editorGuiStates.get(player.getUniqueId());
        if (state == null) return;

        switch (state.page()) {
            case MAIN -> this.handleMainMenuClick(player, state, rawSlot);
            case SETTINGS -> this.handleSettingsClick(player, state, rawSlot);
            case WAVES -> this.handleWavesClick(player, state, rawSlot);
            case WAVE_DETAIL -> this.handleWaveDetailClick(player, state, rawSlot);
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
            case 10 -> this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.SETTINGS, 0, 0));
            case 11 -> this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.WAVES, 0, 0));
            case 12 -> this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.BOSSES, 0, 0));
            case 14 -> this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.REWARDS, 0, 0));
            case 15 -> this.openEditorGui(player, new EditorGuiState(state.dungeonId(), EditorPage.SCRIPTS, 0, 0));
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
            newWaves.add(new WaveDefinition(newWaves.size(), List.of(new MobEntry("ZOMBIE", null, 5, 1, new double[]{0, 1, 0}, null, Map.of())), 5, "", true));
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

        // 列出此波次的怪物
        for (int i = 0; i < wave.mobEntries().size() && i < 18; i++) {
            final MobEntry mob = wave.mobEntries().get(i);
            gui.setItem(9 + i, this.editorIcon(this.getMobIcon(mob.entityType()),
                    "§6" + mob.entityType(),
                    "§7數量: §f" + mob.count(),
                    "§7自訂名稱: §f" + (mob.customName() != null ? mob.customName() : "無"),
                    "§e左鍵 修改數量  §c右鍵 刪除"));
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

    private void handleWaveDetailClick(final Player player, final EditorGuiState state, final int slot) {
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
            // 左鍵修改數量，右鍵刪除 — 但這裡拿不到 clickType，走 prompt
            this.promptInput(player,
                    new EditorGuiState(state.dungeonId(), EditorPage.WAVE_DETAIL, state.subIndex(), mobIndex),
                    "mob-count", "§e請輸入 §6" + wave.mobEntries().get(mobIndex).entityType() + " §e的生成數量：§7（數字，輸入 0 刪除）");
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
        final int size = ((MOB_TYPES.length / 9) + 1) * 9 + 9;
        final Inventory gui = Bukkit.createInventory(null, Math.min(size, 54),
                Component.text(EDITOR_GUI_PREFIX + "選擇怪物類型"));
        for (int i = 0; i < MOB_TYPES.length; i++) {
            gui.setItem(i, this.editorIcon(this.getMobIcon(MOB_TYPES[i].name()),
                    "§6" + MOB_TYPES[i].name(), "§7點擊選擇"));
        }
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
            gui.setItem(i, this.editorIcon(Material.REDSTONE_TORCH, "§d§l" + s.id(),
                    "§7觸發: §f" + s.trigger(),
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
                    "§e請輸入腳本（格式: ID 觸發事件）\n§7範例: §fopen_door PASSWORD_MATCH\n§7可用觸發: WAVE_CLEAR, BOSS_DEATH, TIMER, PASSWORD_MATCH, INTERACT_BLOCK");
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
                        new WaveDefinition(w.waveIndex(), w.mobEntries(), val, w.message(), w.requireClear()));
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
                this.replaceWave(did, waveIdx, w -> new WaveDefinition(w.waveIndex(), newMobs, w.spawnDelay(), w.message(), w.requireClear()));
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
                    this.replaceWave(did, waveIdx, w -> new WaveDefinition(w.waveIndex(), newMobs, w.spawnDelay(), w.message(), w.requireClear()));
                    player.sendMessage(this.msg("§a已新增 §6" + mobType + " §ax" + count));
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
    }

    public void reload() {
        this.loadDefinitions();
        this.plugin.getLogger().info("[副本] 重新載入完成，共 " + this.definitions.size() + " 個副本定義。");
    }
}
