package com.rui.techproject.model.dungeon;

import java.util.List;
import java.util.Map;

/**
 * 完整的副本組態 — 對應 MythicDungeons 的 dungeon config.yml。
 * 包含生命系統、規則、難度、大廳、獎勵冷卻、進入要求等所有設定。
 */
public final class DungeonConfig {

    // ── 基本 ──
    private String dungeonType = "INSTANCED";
    private boolean showTitleOnStart = true;

    // ── 大廳 ──
    private double[] lobbyPoint;          // [x, y, z, yaw, pitch] 相對座標
    private double[] exitLocation;        // [x, y, z, yaw, pitch] 絕對或相對
    private boolean alwaysUseExit = false;

    // ── 遊戲模式 ──
    private String gamemode = "SURVIVAL";

    // ── 生命與死亡 ──
    private int playerLives = -1;          // -1 = 無限
    private boolean instantRespawn = false;
    private boolean deadPlayersSpectate = true;
    private boolean closeDungeonWhenAllSpectating = true;
    private double[] respawnPoint;         // 自訂重生點（null = 使用 checkpoint 或 spawn）

    // ── 進入時保留 ──
    private boolean keepInventoryOnEnter = true;
    private boolean keepHealthOnEnter = true;
    private boolean keepFoodOnEnter = true;
    private boolean keepPotionEffectsOnEnter = false;
    private boolean keepExpOnEnter = true;

    // ── 斷線處理 ──
    private boolean kickOfflinePlayers = true;
    private int kickOfflinePlayersDelay = 60; // 秒

    // ── 實例管理 ──
    private int maxBackups = 3;
    private int maxInstances = 5;
    private int cleanupDelay = 10;   // 秒

    // ── 獎勵冷卻 ──
    private boolean lootCooldownEnabled = false;
    private boolean lootCooldownPerReward = false;
    private String lootCooldownType = "PLAYER";  // "PLAYER" or "DUNGEON"
    private int lootCooldownTime = 86400;        // 秒
    private String lootCooldownResetDay = "MONDAY";

    // ── 進入冷卻 ──
    private boolean accessCooldownEnabled = false;
    private boolean accessCooldownOnFinish = true;
    private boolean accessCooldownOnLeave = false;
    private boolean accessCooldownOnLoseLives = false;
    private boolean accessCooldownOnStart = false;
    private String accessCooldownType = "PLAYER";
    private int accessCooldownTime = 3600;
    private String accessCooldownResetDay = "MONDAY";

    // ── 進入要求 ──
    private List<String> requiredPermissions = List.of();
    private int minPartySize = 1;
    private int maxPartySize = 4;
    private List<String> requiredDungeons = List.of(); // 需先通關的副本 ID
    private double cost = 0.0;
    private boolean leaderOnlyCost = true;

    // ── 鑰匙 ──
    private boolean accessKeysConsume = true;
    private boolean accessKeysLeaderOnly = true;
    private List<String> accessKeyItems = List.of();

    // ── 難度 ──
    private boolean enableDifficultyLevels = false;
    private boolean enableDifficultyMenu = false;
    private List<DifficultyLevel> difficultyLevels = List.of();

    // ── 地圖 (副本內小地圖) ──
    private boolean mapGiveOnJoin = false;
    private int mapMaxRenderDepth = 5;
    private boolean mapDisplayFloors = true;
    private int mapFloorDepth = 10;
    private boolean mapShowAllPlayers = true;

    // ── 規則 ──
    private DungeonRules rules = new DungeonRules();

    // ══════════════════════════════════════════
    //  內部類：難度等級
    // ══════════════════════════════════════════

    public static final class DifficultyLevel {
        private final String id;
        private final String displayName;
        private double mobHealth = 1.0;
        private double mobAmounts = 1.0;
        private double mobDamage = 1.0;
        private int bonusMythicLevels = 0;
        private double bonusLoot = 1.0;

        public DifficultyLevel(final String id, final String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public String id()                  { return this.id; }
        public String displayName()          { return this.displayName; }
        public double mobHealth()             { return this.mobHealth; }
        public double mobAmounts()            { return this.mobAmounts; }
        public double mobDamage()             { return this.mobDamage; }
        public int bonusMythicLevels()        { return this.bonusMythicLevels; }
        public double bonusLoot()             { return this.bonusLoot; }

        public DifficultyLevel mobHealth(double v)        { this.mobHealth = v; return this; }
        public DifficultyLevel mobAmounts(double v)       { this.mobAmounts = v; return this; }
        public DifficultyLevel mobDamage(double v)        { this.mobDamage = v; return this; }
        public DifficultyLevel bonusMythicLevels(int v)   { this.bonusMythicLevels = v; return this; }
        public DifficultyLevel bonusLoot(double v)        { this.bonusLoot = v; return this; }
    }

    // ══════════════════════════════════════════
    //  內部類：副本規則
    // ══════════════════════════════════════════

    public static final class DungeonRules {
        private boolean spawnMobs = false;
        private boolean spawnAnimals = false;
        private boolean spawnMonsters = false;
        private boolean allowBreakBlocks = false;
        private boolean allowPlaceBlocks = false;
        private boolean allowEnderpearl = false;
        private boolean allowChorusFruit = false;
        private boolean allowBucket = false;
        private boolean allowPlaceEntities = false;
        private boolean disableRandomTick = true;
        private boolean preventPlantGrowth = true;
        private boolean preventExplosionBlockDamage = true;
        private boolean pvp = false;
        private boolean hideDeathMessages = true;
        private boolean preventTeleportIn = true;
        private boolean allowCommands = false;
        private List<String> allowedCommands = List.of();
        private List<String> disallowedCommands = List.of();
        private List<String> bannedItems = List.of();
        private List<String> customBannedItems = List.of();
        private List<String> blockPlaceWhitelist = List.of();
        private List<String> blockPlaceBlacklist = List.of();
        private List<String> blockBreakWhitelist = List.of();
        private List<String> blockBreakBlacklist = List.of();
        private boolean allowBreakPlacedBlocks = false;
        private List<String> damageProtectedEntities = List.of();
        private List<String> interactProtectedEntities = List.of();
        private boolean preventDurabilityLoss = false;

        // Getters
        public boolean spawnMobs()                   { return this.spawnMobs; }
        public boolean spawnAnimals()                { return this.spawnAnimals; }
        public boolean spawnMonsters()               { return this.spawnMonsters; }
        public boolean allowBreakBlocks()            { return this.allowBreakBlocks; }
        public boolean allowPlaceBlocks()            { return this.allowPlaceBlocks; }
        public boolean allowEnderpearl()             { return this.allowEnderpearl; }
        public boolean allowChorusFruit()            { return this.allowChorusFruit; }
        public boolean allowBucket()                 { return this.allowBucket; }
        public boolean allowPlaceEntities()          { return this.allowPlaceEntities; }
        public boolean disableRandomTick()           { return this.disableRandomTick; }
        public boolean preventPlantGrowth()          { return this.preventPlantGrowth; }
        public boolean preventExplosionBlockDamage() { return this.preventExplosionBlockDamage; }
        public boolean pvp()                         { return this.pvp; }
        public boolean hideDeathMessages()           { return this.hideDeathMessages; }
        public boolean preventTeleportIn()           { return this.preventTeleportIn; }
        public boolean allowCommands()               { return this.allowCommands; }
        public List<String> allowedCommands()        { return this.allowedCommands; }
        public List<String> disallowedCommands()     { return this.disallowedCommands; }
        public List<String> bannedItems()            { return this.bannedItems; }
        public List<String> customBannedItems()      { return this.customBannedItems; }
        public List<String> blockPlaceWhitelist()    { return this.blockPlaceWhitelist; }
        public List<String> blockPlaceBlacklist()    { return this.blockPlaceBlacklist; }
        public List<String> blockBreakWhitelist()    { return this.blockBreakWhitelist; }
        public List<String> blockBreakBlacklist()    { return this.blockBreakBlacklist; }
        public boolean allowBreakPlacedBlocks()      { return this.allowBreakPlacedBlocks; }
        public List<String> damageProtectedEntities()  { return this.damageProtectedEntities; }
        public List<String> interactProtectedEntities(){ return this.interactProtectedEntities; }
        public boolean preventDurabilityLoss()       { return this.preventDurabilityLoss; }

        // Setters (chain)
        public DungeonRules spawnMobs(boolean v)                   { this.spawnMobs = v; return this; }
        public DungeonRules spawnAnimals(boolean v)                { this.spawnAnimals = v; return this; }
        public DungeonRules spawnMonsters(boolean v)               { this.spawnMonsters = v; return this; }
        public DungeonRules allowBreakBlocks(boolean v)            { this.allowBreakBlocks = v; return this; }
        public DungeonRules allowPlaceBlocks(boolean v)            { this.allowPlaceBlocks = v; return this; }
        public DungeonRules allowEnderpearl(boolean v)             { this.allowEnderpearl = v; return this; }
        public DungeonRules allowChorusFruit(boolean v)            { this.allowChorusFruit = v; return this; }
        public DungeonRules allowBucket(boolean v)                 { this.allowBucket = v; return this; }
        public DungeonRules allowPlaceEntities(boolean v)          { this.allowPlaceEntities = v; return this; }
        public DungeonRules disableRandomTick(boolean v)           { this.disableRandomTick = v; return this; }
        public DungeonRules preventPlantGrowth(boolean v)          { this.preventPlantGrowth = v; return this; }
        public DungeonRules preventExplosionBlockDamage(boolean v) { this.preventExplosionBlockDamage = v; return this; }
        public DungeonRules pvp(boolean v)                         { this.pvp = v; return this; }
        public DungeonRules hideDeathMessages(boolean v)           { this.hideDeathMessages = v; return this; }
        public DungeonRules preventTeleportIn(boolean v)           { this.preventTeleportIn = v; return this; }
        public DungeonRules allowCommands(boolean v)               { this.allowCommands = v; return this; }
        public DungeonRules allowedCommands(List<String> v)        { this.allowedCommands = v; return this; }
        public DungeonRules disallowedCommands(List<String> v)     { this.disallowedCommands = v; return this; }
        public DungeonRules bannedItems(List<String> v)            { this.bannedItems = v; return this; }
        public DungeonRules customBannedItems(List<String> v)      { this.customBannedItems = v; return this; }
        public DungeonRules blockPlaceWhitelist(List<String> v)    { this.blockPlaceWhitelist = v; return this; }
        public DungeonRules blockPlaceBlacklist(List<String> v)    { this.blockPlaceBlacklist = v; return this; }
        public DungeonRules blockBreakWhitelist(List<String> v)    { this.blockBreakWhitelist = v; return this; }
        public DungeonRules blockBreakBlacklist(List<String> v)    { this.blockBreakBlacklist = v; return this; }
        public DungeonRules allowBreakPlacedBlocks(boolean v)      { this.allowBreakPlacedBlocks = v; return this; }
        public DungeonRules damageProtectedEntities(List<String> v)  { this.damageProtectedEntities = v; return this; }
        public DungeonRules interactProtectedEntities(List<String> v){ this.interactProtectedEntities = v; return this; }
        public DungeonRules preventDurabilityLoss(boolean v)       { this.preventDurabilityLoss = v; return this; }
    }

    // ══════════════════════════════════════════
    //  DungeonConfig 主體 Getters / Setters
    // ══════════════════════════════════════════

    public String dungeonType()           { return this.dungeonType; }
    public boolean showTitleOnStart()     { return this.showTitleOnStart; }
    public double[] lobbyPoint()          { return this.lobbyPoint; }
    public double[] exitLocation()        { return this.exitLocation; }
    public boolean alwaysUseExit()        { return this.alwaysUseExit; }
    public String gamemode()              { return this.gamemode; }
    public int playerLives()              { return this.playerLives; }
    public boolean instantRespawn()       { return this.instantRespawn; }
    public boolean deadPlayersSpectate()  { return this.deadPlayersSpectate; }
    public boolean closeDungeonWhenAllSpectating() { return this.closeDungeonWhenAllSpectating; }
    public double[] respawnPoint()        { return this.respawnPoint; }
    public boolean keepInventoryOnEnter() { return this.keepInventoryOnEnter; }
    public boolean keepHealthOnEnter()    { return this.keepHealthOnEnter; }
    public boolean keepFoodOnEnter()      { return this.keepFoodOnEnter; }
    public boolean keepPotionEffectsOnEnter() { return this.keepPotionEffectsOnEnter; }
    public boolean keepExpOnEnter()       { return this.keepExpOnEnter; }
    public boolean kickOfflinePlayers()   { return this.kickOfflinePlayers; }
    public int kickOfflinePlayersDelay()  { return this.kickOfflinePlayersDelay; }
    public int maxBackups()               { return this.maxBackups; }
    public int maxInstances()             { return this.maxInstances; }
    public int cleanupDelay()             { return this.cleanupDelay; }
    public boolean lootCooldownEnabled()  { return this.lootCooldownEnabled; }
    public boolean lootCooldownPerReward(){ return this.lootCooldownPerReward; }
    public String lootCooldownType()      { return this.lootCooldownType; }
    public int lootCooldownTime()         { return this.lootCooldownTime; }
    public String lootCooldownResetDay()  { return this.lootCooldownResetDay; }
    public boolean accessCooldownEnabled()  { return this.accessCooldownEnabled; }
    public boolean accessCooldownOnFinish() { return this.accessCooldownOnFinish; }
    public boolean accessCooldownOnLeave()  { return this.accessCooldownOnLeave; }
    public boolean accessCooldownOnLoseLives() { return this.accessCooldownOnLoseLives; }
    public boolean accessCooldownOnStart()  { return this.accessCooldownOnStart; }
    public String accessCooldownType()      { return this.accessCooldownType; }
    public int accessCooldownTime()         { return this.accessCooldownTime; }
    public String accessCooldownResetDay()  { return this.accessCooldownResetDay; }
    public List<String> requiredPermissions() { return this.requiredPermissions; }
    public int minPartySize()              { return this.minPartySize; }
    public int maxPartySize()              { return this.maxPartySize; }
    public List<String> requiredDungeons() { return this.requiredDungeons; }
    public double cost()                   { return this.cost; }
    public boolean leaderOnlyCost()        { return this.leaderOnlyCost; }
    public boolean accessKeysConsume()     { return this.accessKeysConsume; }
    public boolean accessKeysLeaderOnly()  { return this.accessKeysLeaderOnly; }
    public List<String> accessKeyItems()   { return this.accessKeyItems; }
    public boolean enableDifficultyLevels() { return this.enableDifficultyLevels; }
    public boolean enableDifficultyMenu()  { return this.enableDifficultyMenu; }
    public List<DifficultyLevel> difficultyLevels() { return this.difficultyLevels; }
    public boolean mapGiveOnJoin()         { return this.mapGiveOnJoin; }
    public int mapMaxRenderDepth()         { return this.mapMaxRenderDepth; }
    public boolean mapDisplayFloors()      { return this.mapDisplayFloors; }
    public int mapFloorDepth()             { return this.mapFloorDepth; }
    public boolean mapShowAllPlayers()     { return this.mapShowAllPlayers; }
    public DungeonRules rules()            { return this.rules; }

    // Setters (chain)
    public DungeonConfig dungeonType(String v)           { this.dungeonType = v; return this; }
    public DungeonConfig showTitleOnStart(boolean v)     { this.showTitleOnStart = v; return this; }
    public DungeonConfig lobbyPoint(double[] v)          { this.lobbyPoint = v; return this; }
    public DungeonConfig exitLocation(double[] v)        { this.exitLocation = v; return this; }
    public DungeonConfig alwaysUseExit(boolean v)        { this.alwaysUseExit = v; return this; }
    public DungeonConfig gamemode(String v)              { this.gamemode = v; return this; }
    public DungeonConfig playerLives(int v)              { this.playerLives = v; return this; }
    public DungeonConfig instantRespawn(boolean v)       { this.instantRespawn = v; return this; }
    public DungeonConfig deadPlayersSpectate(boolean v)  { this.deadPlayersSpectate = v; return this; }
    public DungeonConfig closeDungeonWhenAllSpectating(boolean v) { this.closeDungeonWhenAllSpectating = v; return this; }
    public DungeonConfig respawnPoint(double[] v)        { this.respawnPoint = v; return this; }
    public DungeonConfig keepInventoryOnEnter(boolean v) { this.keepInventoryOnEnter = v; return this; }
    public DungeonConfig keepHealthOnEnter(boolean v)    { this.keepHealthOnEnter = v; return this; }
    public DungeonConfig keepFoodOnEnter(boolean v)      { this.keepFoodOnEnter = v; return this; }
    public DungeonConfig keepPotionEffectsOnEnter(boolean v) { this.keepPotionEffectsOnEnter = v; return this; }
    public DungeonConfig keepExpOnEnter(boolean v)       { this.keepExpOnEnter = v; return this; }
    public DungeonConfig kickOfflinePlayers(boolean v)   { this.kickOfflinePlayers = v; return this; }
    public DungeonConfig kickOfflinePlayersDelay(int v)  { this.kickOfflinePlayersDelay = v; return this; }
    public DungeonConfig maxBackups(int v)               { this.maxBackups = v; return this; }
    public DungeonConfig maxInstances(int v)             { this.maxInstances = v; return this; }
    public DungeonConfig cleanupDelay(int v)             { this.cleanupDelay = v; return this; }
    public DungeonConfig lootCooldownEnabled(boolean v)  { this.lootCooldownEnabled = v; return this; }
    public DungeonConfig lootCooldownPerReward(boolean v){ this.lootCooldownPerReward = v; return this; }
    public DungeonConfig lootCooldownType(String v)      { this.lootCooldownType = v; return this; }
    public DungeonConfig lootCooldownTime(int v)         { this.lootCooldownTime = v; return this; }
    public DungeonConfig lootCooldownResetDay(String v)  { this.lootCooldownResetDay = v; return this; }
    public DungeonConfig accessCooldownEnabled(boolean v){ this.accessCooldownEnabled = v; return this; }
    public DungeonConfig accessCooldownOnFinish(boolean v) { this.accessCooldownOnFinish = v; return this; }
    public DungeonConfig accessCooldownOnLeave(boolean v){ this.accessCooldownOnLeave = v; return this; }
    public DungeonConfig accessCooldownOnLoseLives(boolean v) { this.accessCooldownOnLoseLives = v; return this; }
    public DungeonConfig accessCooldownOnStart(boolean v){ this.accessCooldownOnStart = v; return this; }
    public DungeonConfig accessCooldownType(String v)    { this.accessCooldownType = v; return this; }
    public DungeonConfig accessCooldownTime(int v)       { this.accessCooldownTime = v; return this; }
    public DungeonConfig accessCooldownResetDay(String v){ this.accessCooldownResetDay = v; return this; }
    public DungeonConfig requiredPermissions(List<String> v) { this.requiredPermissions = v; return this; }
    public DungeonConfig minPartySize(int v)             { this.minPartySize = v; return this; }
    public DungeonConfig maxPartySize(int v)             { this.maxPartySize = v; return this; }
    public DungeonConfig requiredDungeons(List<String> v) { this.requiredDungeons = v; return this; }
    public DungeonConfig cost(double v)                  { this.cost = v; return this; }
    public DungeonConfig leaderOnlyCost(boolean v)       { this.leaderOnlyCost = v; return this; }
    public DungeonConfig accessKeysConsume(boolean v)    { this.accessKeysConsume = v; return this; }
    public DungeonConfig accessKeysLeaderOnly(boolean v) { this.accessKeysLeaderOnly = v; return this; }
    public DungeonConfig accessKeyItems(List<String> v)  { this.accessKeyItems = v; return this; }
    public DungeonConfig enableDifficultyLevels(boolean v) { this.enableDifficultyLevels = v; return this; }
    public DungeonConfig enableDifficultyMenu(boolean v) { this.enableDifficultyMenu = v; return this; }
    public DungeonConfig difficultyLevels(List<DifficultyLevel> v) { this.difficultyLevels = v; return this; }
    public DungeonConfig mapGiveOnJoin(boolean v)        { this.mapGiveOnJoin = v; return this; }
    public DungeonConfig mapMaxRenderDepth(int v)        { this.mapMaxRenderDepth = v; return this; }
    public DungeonConfig mapDisplayFloors(boolean v)     { this.mapDisplayFloors = v; return this; }
    public DungeonConfig mapFloorDepth(int v)            { this.mapFloorDepth = v; return this; }
    public DungeonConfig mapShowAllPlayers(boolean v)    { this.mapShowAllPlayers = v; return this; }
    public DungeonConfig rules(DungeonRules v)           { this.rules = v; return this; }

    /** 以所有預設值建立新配置。 */
    public static DungeonConfig defaults() {
        return new DungeonConfig();
    }
}
