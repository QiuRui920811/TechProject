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
    public boolean isWaveCleared() { return this.aliveMobCount <= 0; }

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
}
