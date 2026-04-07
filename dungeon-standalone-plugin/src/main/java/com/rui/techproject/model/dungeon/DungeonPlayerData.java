package com.rui.techproject.model.dungeon;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追蹤每位玩家的副本統計資料。
 */
public final class DungeonPlayerData {

    private final UUID uuid;
    /** 每個副本 ID → 通關次數 */
    private final Map<String, Integer> clearCounts = new ConcurrentHashMap<>();
    /** 每個副本 ID → 最佳通關秒數 */
    private final Map<String, Integer> bestTimes = new ConcurrentHashMap<>();
    /** 每個副本 ID → 上次進入的 epoch millis */
    private final Map<String, Long> lastEntryTime = new ConcurrentHashMap<>();
    /** 每個副本 ID → 今日已進入次數 */
    private final Map<String, Integer> dailyEntryCounts = new ConcurrentHashMap<>();
    /** 今日日期 key（用於日重置） */
    private String dailyDateKey = "";
    /** 每個副本 ID → 上次獎勵領取 epoch millis */
    private final Map<String, Long> lootCooldownTimes = new ConcurrentHashMap<>();
    /** 每個 (dungeonId + ":" + rewardIndex) → 上次領取 epoch millis （per-reward 模式）*/
    private final Map<String, Long> perRewardCooldowns = new ConcurrentHashMap<>();
    /** 每個副本 ID → 上次進入冷卻起始 epoch millis */
    private final Map<String, Long> accessCooldownTimes = new ConcurrentHashMap<>();

    public DungeonPlayerData(final UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() { return this.uuid; }

    // ── 通關次數 ──

    public int getClearCount(final String dungeonId) {
        return this.clearCounts.getOrDefault(dungeonId, 0);
    }
    public void incrementClearCount(final String dungeonId) {
        this.clearCounts.merge(dungeonId, 1, Integer::sum);
    }
    public boolean hasCleared(final String dungeonId) { return this.getClearCount(dungeonId) > 0; }

    // ── 最佳時間 ──

    public int getBestTime(final String dungeonId) {
        return this.bestTimes.getOrDefault(dungeonId, Integer.MAX_VALUE);
    }
    public void updateBestTime(final String dungeonId, final int seconds) {
        this.bestTimes.merge(dungeonId, seconds, Math::min);
    }

    // ── 冷卻 ──

    public long getLastEntryTime(final String dungeonId) {
        return this.lastEntryTime.getOrDefault(dungeonId, 0L);
    }
    public void setLastEntryTime(final String dungeonId, final long millis) {
        this.lastEntryTime.put(dungeonId, millis);
    }
    public boolean isOnCooldown(final String dungeonId, final int cooldownSeconds) {
        if (cooldownSeconds <= 0) return false;
        final long last = this.getLastEntryTime(dungeonId);
        return System.currentTimeMillis() - last < cooldownSeconds * 1000L;
    }
    public int remainingCooldownSeconds(final String dungeonId, final int cooldownSeconds) {
        if (cooldownSeconds <= 0) return 0;
        final long elapsed = System.currentTimeMillis() - this.getLastEntryTime(dungeonId);
        return Math.max(0, cooldownSeconds - (int) (elapsed / 1000L));
    }

    // ── 每日次數 ──

    public void resetDailyIfNeeded(final String todayKey) {
        if (!todayKey.equals(this.dailyDateKey)) {
            this.dailyDateKey = todayKey;
            this.dailyEntryCounts.clear();
        }
    }
    public int getDailyEntryCount(final String dungeonId) {
        return this.dailyEntryCounts.getOrDefault(dungeonId, 0);
    }
    public void incrementDailyEntry(final String dungeonId) {
        this.dailyEntryCounts.merge(dungeonId, 1, Integer::sum);
    }
    public boolean hasReachedDailyLimit(final String dungeonId, final int limit) {
        if (limit <= 0) return false;
        return this.getDailyEntryCount(dungeonId) >= limit;
    }

    // ── 獎勵冷卻 ──

    public void setLootCooldown(final String dungeonId, final long millis) {
        this.lootCooldownTimes.put(dungeonId, millis);
    }
    public boolean isLootOnCooldown(final String dungeonId, final int cooldownSeconds) {
        if (cooldownSeconds <= 0) return false;
        final long last = this.lootCooldownTimes.getOrDefault(dungeonId, 0L);
        return System.currentTimeMillis() - last < cooldownSeconds * 1000L;
    }
    public void setPerRewardCooldown(final String key, final long millis) {
        this.perRewardCooldowns.put(key, millis);
    }
    public boolean isPerRewardOnCooldown(final String key, final int cooldownSeconds) {
        if (cooldownSeconds <= 0) return false;
        final long last = this.perRewardCooldowns.getOrDefault(key, 0L);
        return System.currentTimeMillis() - last < cooldownSeconds * 1000L;
    }

    // ── 進入冷卻 ──

    public void setAccessCooldown(final String dungeonId, final long millis) {
        this.accessCooldownTimes.put(dungeonId, millis);
    }
    public boolean isAccessOnCooldown(final String dungeonId, final int cooldownSeconds) {
        if (cooldownSeconds <= 0) return false;
        final long last = this.accessCooldownTimes.getOrDefault(dungeonId, 0L);
        return System.currentTimeMillis() - last < cooldownSeconds * 1000L;
    }
    public int remainingAccessCooldownSeconds(final String dungeonId, final int cooldownSeconds) {
        if (cooldownSeconds <= 0) return 0;
        final long elapsed = System.currentTimeMillis() - this.accessCooldownTimes.getOrDefault(dungeonId, 0L);
        return Math.max(0, cooldownSeconds - (int) (elapsed / 1000L));
    }
    public void clearAllCooldowns(final String dungeonId) {
        this.accessCooldownTimes.remove(dungeonId);
        this.lastEntryTime.remove(dungeonId);
        this.lootCooldownTimes.remove(dungeonId);
    }

    // ── 序列化 ──

    public Map<String, Object> serialize() {
        final Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("clear-counts", new ConcurrentHashMap<>(this.clearCounts));
        data.put("best-times", new ConcurrentHashMap<>(this.bestTimes));
        data.put("last-entry", new ConcurrentHashMap<>(this.lastEntryTime));
        data.put("daily-date", this.dailyDateKey);
        data.put("daily-counts", new ConcurrentHashMap<>(this.dailyEntryCounts));
        data.put("loot-cooldowns", new ConcurrentHashMap<>(this.lootCooldownTimes));
        data.put("per-reward-cooldowns", new ConcurrentHashMap<>(this.perRewardCooldowns));
        data.put("access-cooldowns", new ConcurrentHashMap<>(this.accessCooldownTimes));
        return data;
    }

    @SuppressWarnings("unchecked")
    public void deserialize(final Map<String, Object> data) {
        if (data == null) return;
        if (data.get("clear-counts") instanceof Map<?, ?> map) {
            map.forEach((k, v) -> { if (k instanceof String s && v instanceof Number n) this.clearCounts.put(s, n.intValue()); });
        }
        if (data.get("best-times") instanceof Map<?, ?> map) {
            map.forEach((k, v) -> { if (k instanceof String s && v instanceof Number n) this.bestTimes.put(s, n.intValue()); });
        }
        if (data.get("last-entry") instanceof Map<?, ?> map) {
            map.forEach((k, v) -> { if (k instanceof String s && v instanceof Number n) this.lastEntryTime.put(s, n.longValue()); });
        }
        if (data.get("daily-date") instanceof String s) this.dailyDateKey = s;
        if (data.get("daily-counts") instanceof Map<?, ?> map) {
            map.forEach((k, v) -> { if (k instanceof String s && v instanceof Number n) this.dailyEntryCounts.put(s, n.intValue()); });
        }
        if (data.get("loot-cooldowns") instanceof Map<?, ?> map) {
            map.forEach((k, v) -> { if (k instanceof String s && v instanceof Number n) this.lootCooldownTimes.put(s, n.longValue()); });
        }
        if (data.get("per-reward-cooldowns") instanceof Map<?, ?> map) {
            map.forEach((k, v) -> { if (k instanceof String s && v instanceof Number n) this.perRewardCooldowns.put(s, n.longValue()); });
        }
        if (data.get("access-cooldowns") instanceof Map<?, ?> map) {
            map.forEach((k, v) -> { if (k instanceof String s && v instanceof Number n) this.accessCooldownTimes.put(s, n.longValue()); });
        }
    }
}
