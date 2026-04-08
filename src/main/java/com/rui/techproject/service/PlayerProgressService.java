package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.storage.StorageBackend;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerProgressService {
    private final TechProjectPlugin plugin;
    private final TechRegistry registry;
    private StorageBackend storageBackend;
    private final Map<UUID, PlayerProgress> cache = new ConcurrentHashMap<>();

    public PlayerProgressService(final TechProjectPlugin plugin, final TechRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void setStorageBackend(final StorageBackend backend) {
        this.storageBackend = backend;
    }

    public void ensureLoaded(final UUID uuid) {
        this.cache.computeIfAbsent(uuid, this::load);
    }

    public void grantStartingProgress(final Player player) {
        final PlayerProgress progress = this.progress(player.getUniqueId());
        for (final String itemId : this.plugin.getConfig().getStringList("starting-unlocks.items")) {
            progress.unlockedItems.add(itemId);
        }
        for (final String machineId : this.plugin.getConfig().getStringList("starting-unlocks.machines")) {
            progress.unlockedMachines.add(machineId);
        }
        progress.unlockedItems.add("tech_book");
        // 只直接解鎖標記為 "initial" 的物品/機器，不觸發級聯
        // （級聯在玩家實際操作機器時由 unlockByRequirement 處理）
        for (final var item : this.registry.allItems()) {
            final String req = item.unlockRequirement();
            if (req == null || req.isBlank() || req.equalsIgnoreCase("initial")) {
                progress.unlockedItems.add(item.id());
            }
        }
        for (final var machine : this.registry.allMachines()) {
            final String req = machine.unlockRequirement();
            if (req == null || req.isBlank() || req.equalsIgnoreCase("initial")) {
                progress.unlockedMachines.add(machine.id());
            }
        }
        if (this.getStat(player.getUniqueId(), "starter_research_xp_seeded") <= 0L) {
            this.addTechXp(player.getUniqueId(), this.configLong("research.xp.starting", 36L));
            this.incrementStat(player.getUniqueId(), "starter_research_xp_seeded", 1L);
        }
    }

    public PlayerProgress progress(final UUID uuid) {
        return this.cache.computeIfAbsent(uuid, this::load);
    }

    public boolean unlockItem(final UUID uuid, final String itemId) {
        return this.progress(uuid).unlockedItems.add(itemId);
    }

    public boolean unlockMachine(final UUID uuid, final String machineId) {
        return this.progress(uuid).unlockedMachines.add(machineId);
    }

    public boolean unlockAchievement(final UUID uuid, final String achievementId) {
        return this.progress(uuid).unlockedAchievements.add(achievementId);
    }

    public boolean unlockInteraction(final UUID uuid, final String interactionId) {
        return this.progress(uuid).unlockedInteractions.add(interactionId);
    }

    public boolean hasItemUnlocked(final UUID uuid, final String itemId) {
        return this.progress(uuid).unlockedItems.contains(itemId);
    }

    public boolean hasMachineUnlocked(final UUID uuid, final String machineId) {
        return this.progress(uuid).unlockedMachines.contains(machineId);
    }

    public boolean hasAchievementUnlocked(final UUID uuid, final String achievementId) {
        return this.progress(uuid).unlockedAchievements.contains(achievementId);
    }

    public boolean hasInteractionUnlocked(final UUID uuid, final String interactionId) {
        return this.progress(uuid).unlockedInteractions.contains(interactionId);
    }

    public String getSelectedTitle(final UUID uuid) {
        return this.progress(uuid).selectedTitle;
    }

    public void setSelectedTitle(final UUID uuid, final String titleId) {
        this.progress(uuid).selectedTitle = titleId == null ? "" : titleId;
    }

    public Set<String> unlockedItems(final UUID uuid) {
        return Set.copyOf(this.progress(uuid).unlockedItems);
    }

    public int unlockedItemCount(final UUID uuid) {
        return this.progress(uuid).unlockedItems.size();
    }

    public Set<String> unlockedMachines(final UUID uuid) {
        return Set.copyOf(this.progress(uuid).unlockedMachines);
    }

    public int unlockedMachineCount(final UUID uuid) {
        return this.progress(uuid).unlockedMachines.size();
    }

    public Set<String> unlockedAchievements(final UUID uuid) {
        return Set.copyOf(this.progress(uuid).unlockedAchievements);
    }

    public int unlockedAchievementCount(final UUID uuid) {
        return this.progress(uuid).unlockedAchievements.size();
    }

    public Set<String> unlockedInteractions(final UUID uuid) {
        return Set.copyOf(this.progress(uuid).unlockedInteractions);
    }

    public int unlockAllResearch(final UUID uuid, final Collection<String> interactionIds) {
        final PlayerProgress progress = this.progress(uuid);
        int unlocked = 0;
        if (progress.unlockedItems.add("tech_book")) {
            unlocked++;
        }
        for (final var item : this.registry.allItems()) {
            if (progress.unlockedItems.add(item.id())) {
                unlocked++;
            }
        }
        for (final var machine : this.registry.allMachines()) {
            if (progress.unlockedMachines.add(machine.id())) {
                unlocked++;
            }
        }
        if (interactionIds != null) {
            for (final String interactionId : interactionIds) {
                if (interactionId != null && !interactionId.isBlank() && progress.unlockedInteractions.add(interactionId)) {
                    unlocked++;
                }
            }
        }
        return unlocked;
    }

    public long addTechXp(final UUID uuid, final long amount) {
        if (amount <= 0L) {
            return this.progress(uuid).techXpTotal;
        }
        final PlayerProgress progress = this.progress(uuid);
        progress.techXpTotal += amount;
        return progress.techXpTotal;
    }

    public boolean spendTechXp(final UUID uuid, final long amount) {
        if (amount <= 0L) {
            return true;
        }
        final PlayerProgress progress = this.progress(uuid);
        if (progress.techXpTotal - progress.techXpSpent < amount) {
            return false;
        }
        progress.techXpSpent += amount;
        return true;
    }

    public long getTechXpTotal(final UUID uuid) {
        return this.progress(uuid).techXpTotal;
    }

    public long getTechXpSpent(final UUID uuid) {
        return this.progress(uuid).techXpSpent;
    }

    public long getAvailableTechXp(final UUID uuid) {
        final PlayerProgress progress = this.progress(uuid);
        return Math.max(0L, progress.techXpTotal - progress.techXpSpent);
    }

    public int getTechLevel(final UUID uuid) {
        final long totalXp = this.getTechXpTotal(uuid);
        int level = 0;
        long remaining = totalXp;
        while (remaining >= this.xpForNextLevel(level)) {
            remaining -= this.xpForNextLevel(level);
            level++;
        }
        return level;
    }

    public long getXpIntoCurrentLevel(final UUID uuid) {
        final long totalXp = this.getTechXpTotal(uuid);
        int level = 0;
        long remaining = totalXp;
        while (remaining >= this.xpForNextLevel(level)) {
            remaining -= this.xpForNextLevel(level);
            level++;
        }
        return remaining;
    }

    public long getXpForNextLevel(final UUID uuid) {
        return this.xpForNextLevel(this.getTechLevel(uuid));
    }

    public long incrementStat(final UUID uuid, final String statKey, final long amount) {
        final PlayerProgress progress = this.progress(uuid);
        final long previousValue = progress.stats.getOrDefault(statKey, 0L);
        final long newValue = previousValue + amount;
        progress.stats.put(statKey, newValue);
        this.applyResearchXpMilestones(uuid, progress, statKey, previousValue, newValue);
        if (statKey != null && !statKey.isBlank()) {
            this.unlockByRequirement(uuid, "stat:" + statKey.trim().toLowerCase(Locale.ROOT));
        }
        return newValue;
    }

    public void setStatMax(final UUID uuid, final String statKey, final long candidateValue) {
        final PlayerProgress progress = this.progress(uuid);
        progress.stats.merge(statKey, candidateValue, Math::max);
    }

    public long getStat(final UUID uuid, final String statKey) {
        return this.progress(uuid).stats.getOrDefault(statKey, 0L);
    }

    public void setStat(final UUID uuid, final String statKey, final long value) {
        this.progress(uuid).stats.put(statKey, value);
    }

    public void save(final UUID uuid) {
        final PlayerProgress progress = this.cache.get(uuid);
        if (progress == null) {
            return;
        }

        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("unlocked-items", new ArrayList<>(progress.unlockedItems));
        data.put("unlocked-machines", new ArrayList<>(progress.unlockedMachines));
        data.put("unlocked-achievements", new ArrayList<>(progress.unlockedAchievements));
        data.put("unlocked-interactions", new ArrayList<>(progress.unlockedInteractions));
        for (final Map.Entry<String, Long> stat : progress.stats.entrySet()) {
            data.put("stats." + stat.getKey(), stat.getValue());
        }
        data.put("tech-xp.total", progress.techXpTotal);
        data.put("tech-xp.spent", progress.techXpSpent);
        data.put("selected-title", progress.selectedTitle);
        if (this.storageBackend == null) return;
        this.storageBackend.savePlayerProgress(uuid, data);
    }

    /**
     * 儲存並從快取中移除離線玩家的進度資料，避免記憶體洩漏。
     */
    public void saveAndEvict(final UUID uuid) {
        this.save(uuid);
        this.cache.remove(uuid);
    }

    public void saveAll() {
        for (final UUID uuid : this.cache.keySet()) {
            this.save(uuid);
        }
    }

    public void unlockByRequirement(final UUID uuid, final String unlockKey) {
        final String normalizedTrigger = this.normalizeTriggerKey(unlockKey);
        boolean changed;
        do {
            changed = false;
            for (final var item : this.registry.allItems()) {
                if (this.hasItemUnlocked(uuid, item.id())) {
                    continue;
                }
                if (this.matchesUnlockRequirement(uuid, item.unlockRequirement(), normalizedTrigger)) {
                    changed |= this.unlockItem(uuid, item.id());
                }
            }
            for (final var machine : this.registry.allMachines()) {
                if (this.hasMachineUnlocked(uuid, machine.id())) {
                    continue;
                }
                if (this.matchesUnlockRequirement(uuid, machine.unlockRequirement(), normalizedTrigger)) {
                    changed |= this.unlockMachine(uuid, machine.id());
                }
            }
        } while (changed);
    }

    private boolean matchesUnlockRequirement(final UUID uuid, final String requirement, final String triggerKey) {
        final String normalizedRequirement = requirement == null ? "" : requirement.trim();
        if (normalizedRequirement.isBlank() || normalizedRequirement.equalsIgnoreCase("initial")) {
            return triggerKey.equals("initial");
        }
        return !triggerKey.isBlank()
            && this.requirementMentionsTrigger(normalizedRequirement, triggerKey)
            && this.evaluateRequirement(uuid, normalizedRequirement);
    }

    private String normalizeTriggerKey(final String triggerKey) {
        return triggerKey == null ? "" : triggerKey.trim().toLowerCase(Locale.ROOT);
    }

    private boolean requirementMentionsTrigger(final String requirement, final String triggerKey) {
        final String normalizedTrigger = this.normalizeTriggerKey(triggerKey);
        if (normalizedTrigger.isBlank()) {
            return false;
        }
        if (normalizedTrigger.startsWith("stat:")) {
            final String statTrigger = normalizedTrigger.substring(5).trim();
            for (final String orGroup : requirement.toLowerCase(Locale.ROOT).split("\\|")) {
                for (final String rawToken : orGroup.split("&")) {
                    final String token = rawToken.trim();
                    if (!token.startsWith("stat:")) {
                        continue;
                    }
                    String statToken = token.substring(5).trim();
                    final int comparatorIndex = statToken.indexOf(">=");
                    if (comparatorIndex > 0) {
                        statToken = statToken.substring(0, comparatorIndex).trim();
                    } else {
                        final int legacyIndex = statToken.lastIndexOf(':');
                        if (legacyIndex > 0) {
                            statToken = statToken.substring(0, legacyIndex).trim();
                        }
                    }
                    if (statToken.equals(statTrigger)) {
                        return true;
                    }
                }
            }
        }
        final String baseTrigger = normalizedTrigger.contains(":")
            ? normalizedTrigger.substring(normalizedTrigger.indexOf(':') + 1)
            : normalizedTrigger;
        final List<String> aliases = List.of(
            normalizedTrigger,
            baseTrigger,
            "item:" + baseTrigger,
            "machine:" + baseTrigger,
            "achievement:" + baseTrigger,
            "interaction:" + baseTrigger
        );
        for (final String orGroup : requirement.toLowerCase(Locale.ROOT).split("\\|")) {
            for (final String rawToken : orGroup.split("&")) {
                final String token = rawToken.trim();
                if (aliases.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    public boolean meetsRequirement(final UUID uuid, final String requirement) {
        final String normalizedRequirement = requirement == null ? "" : requirement.trim();
        if (normalizedRequirement.isBlank() || normalizedRequirement.equalsIgnoreCase("initial")) {
            return true;
        }
        return this.evaluateRequirement(uuid, normalizedRequirement);
    }

    private boolean evaluateRequirement(final UUID uuid, final String expression) {
        final String[] orGroups = expression.split("\\|");
        for (final String orGroup : orGroups) {
            boolean groupMatches = true;
            for (final String rawToken : orGroup.split("&")) {
                final String token = rawToken.trim();
                if (token.isBlank()) {
                    continue;
                }
                if (!this.evaluateRequirementToken(uuid, token)) {
                    groupMatches = false;
                    break;
                }
            }
            if (groupMatches) {
                return true;
            }
        }
        return false;
    }

    private boolean evaluateRequirementToken(final UUID uuid, final String token) {
        if (token.equalsIgnoreCase("initial")) {
            return true;
        }
        if (token.regionMatches(true, 0, "item:", 0, 5)) {
            return this.hasItemUnlocked(uuid, token.substring(5));
        }
        if (token.regionMatches(true, 0, "machine:", 0, 8)) {
            return this.hasMachineUnlocked(uuid, token.substring(8));
        }
        if (token.regionMatches(true, 0, "achievement:", 0, 12)) {
            return this.hasAchievementUnlocked(uuid, token.substring(12));
        }
        if (token.regionMatches(true, 0, "interaction:", 0, 12)) {
            return this.hasInteractionUnlocked(uuid, token.substring(12));
        }
        if (token.regionMatches(true, 0, "stat:", 0, 5)) {
            final String raw = token.substring(5);
            final int comparatorIndex = raw.indexOf(">=");
            if (comparatorIndex > 0) {
                final String statKey = raw.substring(0, comparatorIndex).trim();
                final long required = this.parseLong(raw.substring(comparatorIndex + 2).trim());
                return this.getStat(uuid, statKey) >= required;
            }
            final int legacyIndex = raw.lastIndexOf(':');
            if (legacyIndex > 0) {
                final String statKey = raw.substring(0, legacyIndex).trim();
                final long required = this.parseLong(raw.substring(legacyIndex + 1).trim());
                return this.getStat(uuid, statKey) >= required;
            }
            return this.getStat(uuid, raw.trim()) > 0L;
        }
        return this.hasItemUnlocked(uuid, token)
            || this.hasMachineUnlocked(uuid, token)
            || this.hasAchievementUnlocked(uuid, token)
            || this.hasInteractionUnlocked(uuid, token);
    }

    private long parseLong(final String value) {
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException ignored) {
            return 0L;
        }
    }

    @SuppressWarnings("unchecked")
    private PlayerProgress load(final UUID uuid) {
        final PlayerProgress progress = new PlayerProgress();
        final Map<String, Object> data = this.storageBackend.loadPlayerProgress(uuid);
        if (data == null) {
            return progress;
        }

        if (data.get("unlocked-items") instanceof List<?> items) {
            items.forEach(v -> { if (v instanceof String s) progress.unlockedItems.add(s); });
        }
        if (data.get("unlocked-machines") instanceof List<?> machines) {
            machines.forEach(v -> { if (v instanceof String s) progress.unlockedMachines.add(s); });
        }
        if (data.get("unlocked-achievements") instanceof List<?> achievements) {
            achievements.forEach(v -> { if (v instanceof String s) progress.unlockedAchievements.add(s); });
        }
        if (data.get("unlocked-interactions") instanceof List<?> interactions) {
            interactions.forEach(v -> { if (v instanceof String s) progress.unlockedInteractions.add(s); });
        }
        for (final Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getKey().startsWith("stats.") && entry.getValue() instanceof Number number) {
                progress.stats.put(entry.getKey().substring(6), number.longValue());
            }
        }
        progress.techXpTotal = data.get("tech-xp.total") instanceof Number n ? n.longValue() : 0L;
        progress.techXpSpent = data.get("tech-xp.spent") instanceof Number n ? n.longValue() : 0L;
        progress.selectedTitle = data.get("selected-title") instanceof String s ? s : "";
        return progress;
    }

    private long xpForNextLevel(final int currentLevel) {
        final long base = this.configLong("research.xp.level-curve.base", 40L);
        final long linear = this.configLong("research.xp.level-curve.linear", 20L);
        final long quadratic = this.configLong("research.xp.level-curve.quadratic", 0L);
        final long level = Math.max(0L, currentLevel);
        return Math.max(1L, base + linear * level + quadratic * level * level);
    }

    private long configLong(final String path, final long fallback) {
        return this.plugin.getConfig().getLong(path, fallback);
    }

    private void applyResearchXpMilestones(final UUID uuid,
                                           final PlayerProgress progress,
                                           final String statKey,
                                           final long previousValue,
                                           final long newValue) {
        if (newValue <= previousValue || statKey == null || statKey.isBlank()) {
            return;
        }
        final MilestoneReward milestone = this.milestoneReward(statKey);
        if (milestone == null || milestone.step() <= 0L || milestone.rewardXp() <= 0L) {
            return;
        }
        final long previousMilestones = Math.max(0L, previousValue / milestone.step());
        final long newMilestones = Math.max(0L, newValue / milestone.step());
        final long gainedMilestones = newMilestones - previousMilestones;
        if (gainedMilestones <= 0L) {
            return;
        }
        progress.techXpTotal += gainedMilestones * milestone.rewardXp();
    }

    private MilestoneReward milestoneReward(final String statKey) {
        final String normalized = statKey.toLowerCase();
        if (normalized.endsWith("_cycles")) {
            return new MilestoneReward(48L, 1L);
        }
        return switch (normalized) {
            case "machines_placed" -> new MilestoneReward(2L, 2L);
            case "energy_generated" -> new MilestoneReward(800L, 2L);
            case "farm_harvested" -> new MilestoneReward(160L, 2L);
            case "recycled_items" -> new MilestoneReward(160L, 2L);
            case "items_transferred" -> new MilestoneReward(320L, 2L);
            case "quarry_mined" -> new MilestoneReward(128L, 3L);
            case "vacuum_collected" -> new MilestoneReward(160L, 2L);
            case "logs_felled" -> new MilestoneReward(96L, 2L);
            case "fish_caught" -> new MilestoneReward(64L, 2L);
            case "mobs_collected" -> new MilestoneReward(32L, 2L);
            case "planetary_samples_collected" -> new MilestoneReward(12L, 3L);
            case "planet_ruins_activated" -> new MilestoneReward(1L, 8L);
            case "planet_elites_defeated" -> new MilestoneReward(2L, 5L);
            default -> null;
        };
    }

    public static final class PlayerProgress {
        private final Set<String> unlockedItems = ConcurrentHashMap.newKeySet();
        private final Set<String> unlockedMachines = ConcurrentHashMap.newKeySet();
        private final Set<String> unlockedAchievements = ConcurrentHashMap.newKeySet();
        private final Set<String> unlockedInteractions = ConcurrentHashMap.newKeySet();
        private final Map<String, Long> stats = new ConcurrentHashMap<>();
        private long techXpTotal;
        private long techXpSpent;
        private String selectedTitle = "";

        public Collection<String> unlockedItems() {
            return List.copyOf(this.unlockedItems);
        }

        public Collection<String> unlockedMachines() {
            return List.copyOf(this.unlockedMachines);
        }

        public Collection<String> unlockedAchievements() {
            return List.copyOf(this.unlockedAchievements);
        }

        public Collection<String> unlockedInteractions() {
            return List.copyOf(this.unlockedInteractions);
        }
    }

    private record MilestoneReward(long step, long rewardXp) {
    }
}
