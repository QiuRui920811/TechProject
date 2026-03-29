package com.rui.techproject.model.dungeon;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 副本運行時實例 — 可變狀態物件。
 * 每次開啟副本會建立一個新實例，結束後清理。
 */
public final class DungeonInstance {

    /** 實例狀態。 */
    public enum State {
        PREPARING,      // 準備中（複製世界、傳送玩家）
        COUNTDOWN,      // 倒數中
        RUNNING,        // 進行中
        BOSS_FIGHT,     // Boss 戰鬥中
        COMPLETED,      // 已通關
        FAILED,         // 失敗
        CLOSING         // 關閉中（清理世界）
    }

    private final String instanceId;
    private final DungeonDefinition definition;
    private final UUID leader;
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> returnLocations = new ConcurrentHashMap<>();
    private final List<Entity> spawnedEntities = new CopyOnWriteArrayList<>();
    private final Map<String, Object> variables = new ConcurrentHashMap<>();
    private final Set<String> firedOneShots = ConcurrentHashMap.newKeySet();
    private final List<String> completedObjectives = new CopyOnWriteArrayList<>();

    private State state = State.PREPARING;
    private World instanceWorld;
    private int currentWave = -1;
    private int aliveMobCount;
    private long startTimeMillis;
    private long elapsedTicks;
    private int countdownSeconds;
    private LivingEntity activeBoss;
    private String activeBossId;
    private int currentPhaseIndex = -1;
    private boolean cleared;

    // ── 生命系統 ──
    private final Map<UUID, Integer> playerLives = new ConcurrentHashMap<>();
    private final Set<UUID> spectators = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> checkpoints = new ConcurrentHashMap<>();

    // ── 難度 ──
    private String selectedDifficulty;
    private double difficultyMobHealthMult = 1.0;
    private double difficultyMobDamageMult = 1.0;
    private double difficultyMobAmountsMult = 1.0;
    private double difficultyBonusLoot = 1.0;
    private int difficultyBonusMythicLevels = 0;

    // ── 隊列/準備 ──
    private final Map<UUID, Boolean> readyState = new ConcurrentHashMap<>();
    private boolean allReady = false;
    private boolean inLobby = false;

    // ── 方塊追蹤（allowBreakPlacedBlocks 規則用）──
    private final Set<Long> placedBlockKeys = ConcurrentHashMap.newKeySet();

    // ── 信號系統 ──
    private final Map<String, Boolean> signals = new ConcurrentHashMap<>();

    // ── 門/方塊控制器 ──
    private final Map<String, List<int[]>> doorStates = new ConcurrentHashMap<>();

    // ── 怪物死亡計數（mob_death_counter 觸發器）──
    private final Map<String, Integer> mobDeathCounters = new ConcurrentHashMap<>();

    // ── 玩家死亡計數 ──
    private int playerDeathCount = 0;

    public DungeonInstance(final String instanceId,
                           final DungeonDefinition definition,
                           final UUID leader) {
        this.instanceId = instanceId;
        this.definition = definition;
        this.leader = leader;
        this.members.add(leader);
    }

    // ── 基本資訊 ──

    public String instanceId() { return this.instanceId; }
    public DungeonDefinition definition() { return this.definition; }
    public UUID leader() { return this.leader; }
    public Set<UUID> members() { return Collections.unmodifiableSet(this.members); }
    public Map<UUID, Location> returnLocations() { return Collections.unmodifiableMap(this.returnLocations); }

    public void addMember(final UUID uuid) { this.members.add(uuid); }
    public void removeMember(final UUID uuid) { this.members.remove(uuid); }
    public boolean isMember(final UUID uuid) { return this.members.contains(uuid); }
    public int memberCount() { return this.members.size(); }

    public void setReturnLocation(final UUID uuid, final Location location) {
        this.returnLocations.put(uuid, location.clone());
    }
    public Location getReturnLocation(final UUID uuid) {
        return this.returnLocations.get(uuid);
    }

    // ── 世界 ──

    public World instanceWorld() { return this.instanceWorld; }
    public void setInstanceWorld(final World world) { this.instanceWorld = world; }

    // ── 狀態 ──

    public State state() { return this.state; }
    public void setState(final State state) { this.state = state; }
    public boolean isActive() {
        return this.state == State.RUNNING || this.state == State.BOSS_FIGHT || this.state == State.COUNTDOWN;
    }

    // ── 時間 ──

    public long startTimeMillis() { return this.startTimeMillis; }
    public void setStartTimeMillis(final long millis) { this.startTimeMillis = millis; }
    public long elapsedTicks() { return this.elapsedTicks; }
    public void incrementTicks() { this.elapsedTicks++; }
    public int elapsedSeconds() { return (int) (this.elapsedTicks / 20L); }

    public int countdownSeconds() { return this.countdownSeconds; }
    public void setCountdownSeconds(final int seconds) { this.countdownSeconds = seconds; }
    public void decrementCountdown() { if (this.countdownSeconds > 0) this.countdownSeconds--; }

    /** 是否已超時 */
    public boolean isTimedOut() {
        return this.definition.timeLimitSeconds() > 0
                && this.elapsedSeconds() >= this.definition.timeLimitSeconds();
    }

    // ── 波次 ──

    public int currentWave() { return this.currentWave; }
    public void setCurrentWave(final int wave) { this.currentWave = wave; }
    public int totalWaves() { return this.definition.waves().size(); }
    public boolean hasMoreWaves() { return this.currentWave + 1 < this.totalWaves(); }

    public int aliveMobCount() { return this.aliveMobCount; }
    public void setAliveMobCount(final int count) { this.aliveMobCount = count; }
    public void decrementAliveMobs() { if (this.aliveMobCount > 0) this.aliveMobCount--; }
    public boolean isWaveCleared() { return this.aliveMobCount == 0 && !this.waveSpawning; }

    /** 波次生成中標記（防止非同步生怪期間誤判波次已清空） */
    private volatile boolean waveSpawning;
    public boolean isWaveSpawning() { return this.waveSpawning; }
    public void setWaveSpawning(final boolean v) { this.waveSpawning = v; }

    // ── 生成的實體追蹤 ──

    public List<Entity> spawnedEntities() { return this.spawnedEntities; }
    public void trackEntity(final Entity entity) { this.spawnedEntities.add(entity); }
    public void removeDeadEntities() {
        this.spawnedEntities.removeIf(e -> e == null || e.isDead() || !e.isValid());
    }
    public void killAllEntities() {
        for (final Entity entity : this.spawnedEntities) {
            if (entity != null && entity.isValid() && !entity.isDead()) {
                entity.remove();
            }
        }
        this.spawnedEntities.clear();
    }

    // ── Boss ──

    public LivingEntity activeBoss() { return this.activeBoss; }
    public void setActiveBoss(final LivingEntity boss, final String bossId) {
        this.activeBoss = boss;
        this.activeBossId = bossId;
        this.currentPhaseIndex = -1;
    }
    public String activeBossId() { return this.activeBossId; }
    public boolean hasBoss() { return this.activeBoss != null && !this.activeBoss.isDead(); }

    public int currentPhaseIndex() { return this.currentPhaseIndex; }
    public void setCurrentPhaseIndex(final int index) { this.currentPhaseIndex = index; }

    // ── 腳本變數 ──

    public Map<String, Object> variables() { return this.variables; }
    public Object getVariable(final String key) { return this.variables.get(key); }
    public void setVariable(final String key, final Object value) { this.variables.put(key, value); }
    public boolean hasVariable(final String key) { return this.variables.containsKey(key); }

    // ── OneShot 追蹤 ──

    public boolean hasFired(final String scriptId) { return this.firedOneShots.contains(scriptId); }
    public void markFired(final String scriptId) { this.firedOneShots.add(scriptId); }

    // ── 目標追蹤 ──

    public List<String> completedObjectives() { return this.completedObjectives; }
    public void completeObjective(final String objective) { this.completedObjectives.add(objective); }
    public boolean isObjectiveCompleted(final String objective) { return this.completedObjectives.contains(objective); }

    // ── 通關狀態 ──

    public boolean isCleared() { return this.cleared; }
    public void setCleared(final boolean cleared) { this.cleared = cleared; }

    // ── 生命系統 ──

    /** 設定玩家初始生命數。 */
    public void initPlayerLives(final UUID uuid, final int lives) { this.playerLives.put(uuid, lives); }
    /** 取得玩家剩餘生命（-1 = 無限）。 */
    public int getPlayerLives(final UUID uuid) { return this.playerLives.getOrDefault(uuid, -1); }
    /** 移除一條命，回傳剩餘生命數。 */
    public int decrementPlayerLives(final UUID uuid) {
        return this.playerLives.merge(uuid, -1, Integer::sum);
    }
    public boolean isPlayerOutOfLives(final UUID uuid) {
        final int lives = this.getPlayerLives(uuid);
        return lives != -1 && lives <= 0;
    }

    // ── 觀戰者 ──

    public Set<UUID> spectators() { return Collections.unmodifiableSet(this.spectators); }
    public void addSpectator(final UUID uuid) { this.spectators.add(uuid); }
    public void removeSpectator(final UUID uuid) { this.spectators.remove(uuid); }
    public boolean isSpectator(final UUID uuid) { return this.spectators.contains(uuid); }
    public boolean allMembersSpectating() {
        for (final UUID uuid : this.members) {
            if (!this.spectators.contains(uuid)) return false;
        }
        return !this.members.isEmpty();
    }

    // ── 檢查點 ──

    public void setCheckpoint(final UUID uuid, final Location loc) { this.checkpoints.put(uuid, loc.clone()); }
    public void setCheckpointAll(final Location loc) {
        for (final UUID uuid : this.members) this.checkpoints.put(uuid, loc.clone());
    }
    public Location getCheckpoint(final UUID uuid) { return this.checkpoints.get(uuid); }
    public boolean hasCheckpoint(final UUID uuid) { return this.checkpoints.containsKey(uuid); }

    // ── 難度 ──

    public String selectedDifficulty()        { return this.selectedDifficulty; }
    public void setSelectedDifficulty(final String difficulty) { this.selectedDifficulty = difficulty; }
    public double difficultyMobHealthMult()   { return this.difficultyMobHealthMult; }
    public double difficultyMobDamageMult()   { return this.difficultyMobDamageMult; }
    public double difficultyMobAmountsMult()  { return this.difficultyMobAmountsMult; }
    public double difficultyBonusLoot()       { return this.difficultyBonusLoot; }
    public int difficultyBonusMythicLevels()  { return this.difficultyBonusMythicLevels; }
    public void applyDifficulty(final double health, final double damage, final double amounts,
                                 final double loot, final int mythicLevels) {
        this.difficultyMobHealthMult = health;
        this.difficultyMobDamageMult = damage;
        this.difficultyMobAmountsMult = amounts;
        this.difficultyBonusLoot = loot;
        this.difficultyBonusMythicLevels = mythicLevels;
    }

    // ── 隊列/準備 ──

    public Map<UUID, Boolean> readyState() { return this.readyState; }
    public void setReady(final UUID uuid, final boolean ready) { this.readyState.put(uuid, ready); }
    public boolean isReady(final UUID uuid) { return Boolean.TRUE.equals(this.readyState.get(uuid)); }
    public boolean isAllReady() { return this.allReady; }
    public void setAllReady(final boolean v) { this.allReady = v; }
    public boolean isInLobby() { return this.inLobby; }
    public void setInLobby(final boolean v) { this.inLobby = v; }
    public boolean checkAllReady() {
        for (final UUID uuid : this.members) {
            if (!Boolean.TRUE.equals(this.readyState.get(uuid))) return false;
        }
        this.allReady = !this.members.isEmpty();
        return this.allReady;
    }

    // ── 方塊追蹤 ──

    public static long blockKey(final int x, final int y, final int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }
    public void trackPlacedBlock(final int x, final int y, final int z) {
        this.placedBlockKeys.add(blockKey(x, y, z));
    }
    public boolean isPlacedBlock(final int x, final int y, final int z) {
        return this.placedBlockKeys.contains(blockKey(x, y, z));
    }
    public void removePlacedBlock(final int x, final int y, final int z) {
        this.placedBlockKeys.remove(blockKey(x, y, z));
    }

    // ── 信號系統 ──

    public void sendSignal(final String signal) { this.signals.put(signal, true); }
    public boolean hasSignal(final String signal) { return Boolean.TRUE.equals(this.signals.get(signal)); }
    public void clearSignal(final String signal) { this.signals.remove(signal); }
    public Map<String, Boolean> signals() { return this.signals; }

    // ── 門控制器 ──

    public Map<String, List<int[]>> doorStates() { return this.doorStates; }

    // ── 怪物死亡計數 ──

    public void incrementMobDeathCounter(final String counterId) {
        this.mobDeathCounters.merge(counterId, 1, Integer::sum);
    }
    public int getMobDeathCount(final String counterId) {
        return this.mobDeathCounters.getOrDefault(counterId, 0);
    }
    public Map<String, Integer> mobDeathCounters() { return this.mobDeathCounters; }

    // ── 玩家死亡計數 ──

    public int playerDeathCount() { return this.playerDeathCount; }
    public void incrementPlayerDeathCount() { this.playerDeathCount++; }
}
