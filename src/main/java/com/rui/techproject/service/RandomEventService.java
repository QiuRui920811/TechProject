package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.SafeScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 隨機事件系統 — 迷宮與星球世界內的突發事件。
 *
 * <p>事件在玩家接取迷宮任務或進入星球時，有一定機率觸發：
 * <ul>
 *   <li>迷途獵手來襲：玩家身邊刷出 3-5 隻強化怪，掉落稀有碎片</li>
 *   <li>異界寶箱：附近生成一個臨時寶箱實體（ArmorStand 載體），包含稀有素材</li>
 *   <li>遺跡共鳴：周圍 20 格內刷一圈信標粒子，擊敗 10 隻普通怪即可獲得共鳴之石</li>
 *   <li>時空裂縫：小型凋零骷髏精英一隻，掉落時空碎片</li>
 *   <li>沉默守衛：守衛者單體，Boss 戰獎勵守衛核心</li>
 * </ul>
 *
 * <p>實作重點：<br>
 *  - 完全獨立於 MachineService / TechRegistry，不會影響既有生產線；<br>
 *  - 事件實體全部加上 scoreboard tag，便於重啟清理；<br>
 *  - 冷卻時間針對玩家個體，避免刷屏；<br>
 *  - 使用 SafeScheduler.runRegion 確保 Folia region 安全。
 */
public final class RandomEventService {

    /** 事件相關實體的共用 scoreboard tag */
    public static final String EVENT_ENTITY_TAG = "techproject:random_event";
    public static final String EVENT_HUNTER_TAG = "techproject:event_hunter";
    public static final String EVENT_RIFT_TAG = "techproject:event_rift";
    public static final String EVENT_GUARDIAN_TAG = "techproject:event_guardian";

    /** 玩家冷卻時間：5 分鐘 */
    private static final long PLAYER_COOLDOWN_MS = 5L * 60L * 1000L;
    /** 事件最長存活時間：3 分鐘後自動清理 */
    private static final long EVENT_LIFETIME_TICKS = 20L * 180L;
    /** 觸發機率（當玩家接取迷宮任務時） */
    private static final double TRIGGER_CHANCE = 0.22;

    private final TechMCPlugin plugin;
    private final SafeScheduler scheduler;
    private final ItemFactoryUtil itemFactory;

    /** 玩家 UUID → 上次觸發事件時間戳 */
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    /** 玩家 UUID → 進行中事件 */
    private final Map<UUID, ActiveEvent> activeEvents = new ConcurrentHashMap<>();
    /** 事件 UUID → 已擊殺數量（用於遺跡共鳴等累積型事件） */
    private final Map<UUID, Integer> eventProgress = new ConcurrentHashMap<>();

    public RandomEventService(final TechMCPlugin plugin,
                              final SafeScheduler scheduler,
                              final ItemFactoryUtil itemFactory) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.itemFactory = itemFactory;
    }

    /**
     * 嘗試為玩家觸發一個隨機事件。<br>
     * 呼叫點：MazeService.assignRandomQuest 完成後、玩家進入星球世界時。
     */
    public void maybeTrigger(final Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        final long now = System.currentTimeMillis();
        final Long last = this.playerCooldowns.get(uuid);
        if (last != null && now - last < PLAYER_COOLDOWN_MS) {
            return;
        }
        if (this.activeEvents.containsKey(uuid)) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() > TRIGGER_CHANCE) {
            return;
        }
        final EventType[] types = EventType.values();
        final EventType chosen = types[ThreadLocalRandom.current().nextInt(types.length)];
        this.triggerEvent(player, chosen);
        this.playerCooldowns.put(uuid, now);
    }

    /**
     * 強制觸發指定類型事件（給 /tech event 指令呼叫）。
     */
    public boolean forceTrigger(final Player player, final EventType type) {
        if (player == null || !player.isOnline() || type == null) {
            return false;
        }
        if (this.activeEvents.containsKey(player.getUniqueId())) {
            player.sendMessage(this.itemFactory.warning("你已有進行中的隨機事件。"));
            return false;
        }
        this.triggerEvent(player, type);
        return true;
    }

    private void triggerEvent(final Player player, final EventType type) {
        final Location origin = player.getLocation();
        if (origin.getWorld() == null) {
            return;
        }
        final UUID eventId = UUID.randomUUID();
        final ActiveEvent event = new ActiveEvent(eventId, type, player.getUniqueId(),
                System.currentTimeMillis());
        this.activeEvents.put(player.getUniqueId(), event);

        // 事件公告
        player.sendMessage(Component.text("◆ ", NamedTextColor.GOLD)
                .append(Component.text("隨機事件：", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text(type.displayName, NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  " + type.description, NamedTextColor.GRAY));
        player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN,
                SoundCategory.PLAYERS, 0.6f, 1.4f);

        // 視覺特效
        origin.getWorld().spawnParticle(Particle.LARGE_SMOKE, origin.clone().add(0, 1.5, 0),
                30, 1.5, 1.0, 1.5, 0.05);
        origin.getWorld().spawnParticle(Particle.PORTAL, origin.clone().add(0, 1.5, 0),
                40, 1.2, 0.8, 1.2, 0.1);

        // 具體事件邏輯
        switch (type) {
            case HUNTER_WAVE -> this.spawnHunterWave(player, origin, event);
            case TREASURE_CHEST -> this.spawnTreasureChest(player, origin, event);
            case RESONANCE -> this.spawnResonance(player, origin, event);
            case RIFT_ELITE -> this.spawnRiftElite(player, origin, event);
            case SILENT_GUARDIAN -> this.spawnSilentGuardian(player, origin, event);
        }

        // 生命週期清理
        this.scheduler.runGlobalDelayed(task -> this.expireEvent(eventId, player.getUniqueId()),
                EVENT_LIFETIME_TICKS);
    }

    // ─── 事件 1：迷途獵手來襲 ───
    private void spawnHunterWave(final Player player, final Location origin, final ActiveEvent event) {
        final World world = origin.getWorld();
        final int count = 3 + ThreadLocalRandom.current().nextInt(3); // 3-5 隻
        final List<UUID> spawned = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            final double angle = (Math.PI * 2 / count) * i;
            final double dx = Math.cos(angle) * 8.0;
            final double dz = Math.sin(angle) * 8.0;
            final Location spawnLoc = origin.clone().add(dx, 0, dz);
            // 找安全的 Y
            final Location safeLoc = this.findSafeSpawnY(spawnLoc);
            if (safeLoc == null) continue;
            this.scheduler.runRegion(safeLoc, task -> {
                final EntityType mobType = ThreadLocalRandom.current().nextBoolean()
                        ? EntityType.ZOMBIE : EntityType.SKELETON;
                final LivingEntity mob = (LivingEntity) world.spawnEntity(safeLoc, mobType);
                mob.addScoreboardTag(EVENT_ENTITY_TAG);
                mob.addScoreboardTag(EVENT_HUNTER_TAG);
                mob.addScoreboardTag("techproject:event_owner_" + player.getUniqueId());
                mob.customName(Component.text("迷途獵手", NamedTextColor.DARK_PURPLE));
                mob.setCustomNameVisible(true);
                // 屬性強化
                final var healthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
                if (healthAttr != null) {
                    healthAttr.setBaseValue(40.0);
                    mob.setHealth(40.0);
                }
                final var dmgAttr = mob.getAttribute(Attribute.ATTACK_DAMAGE);
                if (dmgAttr != null) dmgAttr.setBaseValue(8.0);
                mob.setGlowing(true);
                mob.setPersistent(false);
                if (mob instanceof Mob m) m.setTarget(player);
                world.spawnParticle(Particle.SOUL, safeLoc.clone().add(0, 1, 0),
                        15, 0.3, 0.6, 0.3, 0.02);
                world.playSound(safeLoc, Sound.ENTITY_WARDEN_EMERGE,
                        SoundCategory.HOSTILE, 0.5f, 1.5f);
                spawned.add(mob.getUniqueId());
            });
        }
        event.entities.addAll(spawned);
    }

    // ─── 事件 2：異界寶箱 ───
    private void spawnTreasureChest(final Player player, final Location origin, final ActiveEvent event) {
        final World world = origin.getWorld();
        final double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        final double dist = 10.0 + ThreadLocalRandom.current().nextDouble() * 5.0;
        final Location chestLoc = origin.clone().add(
                Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        final Location safeLoc = this.findSafeSpawnY(chestLoc);
        if (safeLoc == null) {
            this.expireEvent(event.id, player.getUniqueId());
            return;
        }
        this.scheduler.runRegion(safeLoc, task -> {
            // 使用 Item 作為寶箱的代理（發光 + 掉落特殊物品）
            final ItemStack chest = new ItemStack(Material.CHEST);
            final var meta = chest.getItemMeta();
            if (meta != null) {
                meta.displayName(Component.text("✦ 異界寶箱 ✦", NamedTextColor.GOLD,
                        TextDecoration.BOLD));
                chest.setItemMeta(meta);
            }
            final Item itemEntity = world.dropItem(safeLoc.clone().add(0, 1.2, 0), chest);
            itemEntity.setGlowing(true);
            itemEntity.setUnlimitedLifetime(true);
            itemEntity.setPickupDelay(Integer.MAX_VALUE);  // 防止直接撿起
            itemEntity.setInvulnerable(true);
            itemEntity.addScoreboardTag(EVENT_ENTITY_TAG);
            itemEntity.addScoreboardTag("techproject:event_owner_" + player.getUniqueId());
            event.entities.add(itemEntity.getUniqueId());

            // 羅盤指向特效
            player.sendMessage(this.itemFactory.warning(
                    "寶箱位置：X=" + safeLoc.getBlockX() + " Z=" + safeLoc.getBlockZ()));
            world.spawnParticle(Particle.END_ROD, safeLoc.clone().add(0, 2, 0),
                    30, 0.4, 1.5, 0.4, 0.02);
            world.playSound(safeLoc, Sound.BLOCK_END_PORTAL_FRAME_FILL,
                    SoundCategory.AMBIENT, 1.0f, 1.2f);

            // 每 5 秒在寶箱位置發出導引粒子
            this.scheduler.runRegionTimer(safeLoc, pulseTask -> {
                if (!itemEntity.isValid()) {
                    pulseTask.cancel();
                    return;
                }
                world.spawnParticle(Particle.DUST, itemEntity.getLocation().add(0, 0.5, 0),
                        20, 0.4, 0.5, 0.4, 0.02,
                        new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.2f));
            }, 20L, 100L);
        });
    }

    // ─── 事件 3：遺跡共鳴 ───
    private void spawnResonance(final Player player, final Location origin, final ActiveEvent event) {
        this.eventProgress.put(event.id, 0);
        final World world = origin.getWorld();
        world.spawnParticle(Particle.ENCHANT, origin.clone().add(0, 2, 0),
                80, 3.0, 2.0, 3.0, 0.5);
        world.playSound(origin, Sound.BLOCK_BEACON_ACTIVATE,
                SoundCategory.AMBIENT, 1.0f, 1.4f);
        player.sendMessage(this.itemFactory.warning(
                "§e擊殺任意怪物 §f10 §e隻以共鳴獲得獎勵！"));
        // 周圍漂浮指示
        this.scheduler.runRegionTimer(origin, task -> {
            if (!this.activeEvents.containsKey(player.getUniqueId())) {
                task.cancel();
                return;
            }
            if (!player.isOnline() || !player.getWorld().equals(world)) {
                return;
            }
            final Location p = player.getLocation();
            if (p.distanceSquared(origin) > 80 * 80) {
                return;
            }
            world.spawnParticle(Particle.ENCHANT, p.clone().add(0, 2, 0),
                    10, 1.5, 1.0, 1.5, 0.5);
        }, 40L, 40L);
    }

    // ─── 事件 4：時空裂縫精英 ───
    private void spawnRiftElite(final Player player, final Location origin, final ActiveEvent event) {
        final World world = origin.getWorld();
        final double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        final Location spawnLoc = origin.clone().add(
                Math.cos(angle) * 6, 0, Math.sin(angle) * 6);
        final Location safeLoc = this.findSafeSpawnY(spawnLoc);
        if (safeLoc == null) {
            this.expireEvent(event.id, player.getUniqueId());
            return;
        }
        this.scheduler.runRegion(safeLoc, task -> {
            final LivingEntity mob = (LivingEntity) world.spawnEntity(safeLoc, EntityType.WITHER_SKELETON);
            mob.addScoreboardTag(EVENT_ENTITY_TAG);
            mob.addScoreboardTag(EVENT_RIFT_TAG);
            mob.addScoreboardTag("techproject:event_owner_" + player.getUniqueId());
            mob.customName(Component.text("◈ 時空裂影 ◈", NamedTextColor.DARK_PURPLE,
                    TextDecoration.BOLD));
            mob.setCustomNameVisible(true);
            final var healthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(120.0);
                mob.setHealth(120.0);
            }
            final var dmgAttr = mob.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmgAttr != null) dmgAttr.setBaseValue(15.0);
            final var speedAttr = mob.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speedAttr != null) speedAttr.setBaseValue(speedAttr.getBaseValue() * 1.5);
            mob.setGlowing(true);
            mob.setPersistent(false);
            mob.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 180, 0,
                    false, false, false));
            if (mob instanceof Mob m) m.setTarget(player);
            world.spawnParticle(Particle.PORTAL, safeLoc.clone().add(0, 1, 0),
                    50, 0.8, 1.5, 0.8, 0.5);
            world.playSound(safeLoc, Sound.ENTITY_WITHER_SPAWN,
                    SoundCategory.HOSTILE, 0.8f, 1.3f);
            event.entities.add(mob.getUniqueId());
        });
    }

    // ─── 事件 5：沉默守衛 ───
    private void spawnSilentGuardian(final Player player, final Location origin, final ActiveEvent event) {
        final World world = origin.getWorld();
        final double angle = ThreadLocalRandom.current().nextDouble() * Math.PI * 2;
        final Location spawnLoc = origin.clone().add(
                Math.cos(angle) * 8, 0, Math.sin(angle) * 8);
        final Location safeLoc = this.findSafeSpawnY(spawnLoc);
        if (safeLoc == null) {
            this.expireEvent(event.id, player.getUniqueId());
            return;
        }
        this.scheduler.runRegion(safeLoc, task -> {
            final LivingEntity mob = (LivingEntity) world.spawnEntity(safeLoc, EntityType.IRON_GOLEM);
            mob.addScoreboardTag(EVENT_ENTITY_TAG);
            mob.addScoreboardTag(EVENT_GUARDIAN_TAG);
            mob.addScoreboardTag("techproject:event_owner_" + player.getUniqueId());
            mob.customName(Component.text("§b§l沉默守衛"));
            mob.setCustomNameVisible(true);
            final var healthAttr = mob.getAttribute(Attribute.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(180.0);
                mob.setHealth(180.0);
            }
            final var dmgAttr = mob.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmgAttr != null) dmgAttr.setBaseValue(18.0);
            mob.setGlowing(true);
            mob.setPersistent(false);
            if (mob instanceof org.bukkit.entity.IronGolem golem) {
                golem.setPlayerCreated(false);
            }
            world.spawnParticle(Particle.SCULK_SOUL, safeLoc.clone().add(0, 1, 0),
                    40, 0.8, 1.5, 0.8, 0.1);
            world.playSound(safeLoc, Sound.ENTITY_WARDEN_ANGRY,
                    SoundCategory.HOSTILE, 1.0f, 0.8f);
            event.entities.add(mob.getUniqueId());
        });
    }

    /**
     * 由 EntityDeathEvent 呼叫，追蹤事件相關怪物的擊殺並發放獎勵。
     */
    public void onEntityDeath(final LivingEntity entity, final Player killer) {
        if (entity == null || killer == null) return;
        final Set<String> tags = entity.getScoreboardTags();
        if (!tags.contains(EVENT_ENTITY_TAG)) {
            // 檢查遺跡共鳴進度 — 任何怪物被擊殺都算
            this.advanceResonance(killer);
            return;
        }
        // 找出事件擁有者
        UUID ownerId = null;
        for (final String tag : tags) {
            if (tag.startsWith("techproject:event_owner_")) {
                try {
                    ownerId = UUID.fromString(tag.substring("techproject:event_owner_".length()));
                } catch (final IllegalArgumentException ignored) {}
                break;
            }
        }
        if (ownerId == null) return;
        final ActiveEvent event = this.activeEvents.get(ownerId);
        if (event == null) return;
        event.entities.remove(entity.getUniqueId());

        // 根據事件類型給獎勵
        switch (event.type) {
            case HUNTER_WAVE -> {
                // 掉落稀有碎片（Nexo 物品優先）
                this.dropReward(entity.getLocation(), "tech_labyrinth_fragment", 1);
                if (event.entities.isEmpty()) {
                    this.completeEvent(ownerId, killer, event,
                            "迷途獵手已全數肅清", "tech_maze_vine", 3);
                }
            }
            case RIFT_ELITE -> this.completeEvent(ownerId, killer, event,
                    "時空裂影崩潰", "tech_labyrinth_relic", 1);
            case SILENT_GUARDIAN -> this.completeEvent(ownerId, killer, event,
                    "沉默守衛沉眠", "tech_guardian_core", 1);
            default -> {}
        }
    }

    private void advanceResonance(final Player killer) {
        final ActiveEvent event = this.activeEvents.get(killer.getUniqueId());
        if (event == null || event.type != EventType.RESONANCE) return;
        final int next = this.eventProgress.merge(event.id, 1, Integer::sum);
        if (next >= 10) {
            this.completeEvent(killer.getUniqueId(), killer, event,
                    "遺跡共鳴爆發", "tech_labyrinth_fragment", 5);
            this.eventProgress.remove(event.id);
        } else if (next % 2 == 0) {
            killer.sendActionBar(Component.text("遺跡共鳴 ", NamedTextColor.AQUA)
                    .append(Component.text(next + "/10", NamedTextColor.YELLOW)));
        }
    }

    /**
     * 由 TechListener.onMove 呼叫，玩家靠近異界寶箱時自動觸發開啟。
     */
    public void checkTreasureChestProximity(final Player player) {
        final ActiveEvent event = this.activeEvents.get(player.getUniqueId());
        if (event == null || event.type != EventType.TREASURE_CHEST) {
            return;
        }
        final Location playerLoc = player.getLocation();
        for (final UUID entityId : event.entities) {
            final Entity e = Bukkit.getEntity(entityId);
            if (e instanceof Item item && item.isValid()
                    && item.getWorld().equals(playerLoc.getWorld())
                    && item.getLocation().distanceSquared(playerLoc) < 6.25) { // 2.5 格內觸發
                this.scheduler.runEntity(item, () -> {
                    if (!item.isValid()) return;
                    item.remove();
                    this.completeEvent(player.getUniqueId(), player, event,
                            "異界寶箱開啟", "tech_labyrinth_relic", 2);
                });
                return;
            }
        }
    }

    private void completeEvent(final UUID ownerId, final Player rewardTarget,
                                final ActiveEvent event,
                                final String message, final String rewardId, final int amount) {
        this.activeEvents.remove(ownerId);
        this.eventProgress.remove(event.id);
        // 清理殘存實體
        for (final UUID entityId : event.entities) {
            final Entity e = Bukkit.getEntity(entityId);
            if (e != null && e.isValid()) {
                this.scheduler.runEntity(e, e::remove);
            }
        }
        event.entities.clear();

        if (rewardTarget != null && rewardTarget.isOnline()) {
            rewardTarget.sendMessage(Component.text("✦ ", NamedTextColor.GOLD)
                    .append(Component.text("事件完成：", NamedTextColor.GREEN, TextDecoration.BOLD))
                    .append(Component.text(message, NamedTextColor.WHITE)));
            rewardTarget.playSound(rewardTarget.getLocation(),
                    Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.9f, 1.2f);
            this.dropReward(rewardTarget.getLocation(), rewardId, amount);
            // 觸發技能經驗獎勵（若 SkillService 已初始化）
            if (this.plugin.getSkillService() != null) {
                this.plugin.getSkillService().grantXp(rewardTarget, "exploration", 50L);
            }
        }
    }

    private void dropReward(final Location loc, final String itemId, final int amount) {
        ItemStack stack = this.itemFactory.tryBuildNexoItemPublic(itemId);
        if (stack == null) {
            // 後備：以鑽石作為替代獎勵，永遠不會讓事件完成卻沒獎勵
            stack = new ItemStack(Material.DIAMOND, Math.max(1, amount));
        } else {
            stack.setAmount(Math.max(1, Math.min(amount, stack.getMaxStackSize())));
        }
        if (loc.getWorld() != null) {
            final ItemStack finalStack = stack;
            this.scheduler.runRegion(loc, task ->
                    loc.getWorld().dropItemNaturally(loc, finalStack));
        }
    }

    private void expireEvent(final UUID eventId, final UUID ownerId) {
        final ActiveEvent event = this.activeEvents.get(ownerId);
        if (event == null || !event.id.equals(eventId)) return;
        this.activeEvents.remove(ownerId);
        this.eventProgress.remove(eventId);
        // 清理殘存實體
        for (final UUID entityId : event.entities) {
            final Entity e = Bukkit.getEntity(entityId);
            if (e != null && e.isValid()) {
                this.scheduler.runEntity(e, e::remove);
            }
        }
        final Player player = Bukkit.getPlayer(ownerId);
        if (player != null && player.isOnline()) {
            player.sendMessage(this.itemFactory.warning("隨機事件已過期消散。"));
        }
    }

    /**
     * 玩家離線時清理進行中事件的實體（避免殘留污染世界）。
     */
    public void onPlayerQuit(final UUID uuid) {
        final ActiveEvent event = this.activeEvents.remove(uuid);
        if (event == null) return;
        this.eventProgress.remove(event.id);
        for (final UUID entityId : event.entities) {
            final Entity e = Bukkit.getEntity(entityId);
            if (e != null && e.isValid()) {
                this.scheduler.runEntity(e, e::remove);
            }
        }
    }

    public void shutdown() {
        // 不強制清理實體 — 讓 tag 機制在下次啟動時自行處理
        this.activeEvents.clear();
        this.eventProgress.clear();
        this.playerCooldowns.clear();
    }

    /**
     * 在指定位置上方尋找可站立的 Y 值。
     */
    private Location findSafeSpawnY(final Location base) {
        if (base.getWorld() == null) return null;
        final World world = base.getWorld();
        final int bx = base.getBlockX();
        final int bz = base.getBlockZ();
        // 從 base Y 開始往上找 5 格，再往下找 5 格
        for (int dy = 0; dy < 5; dy++) {
            final int y = base.getBlockY() + dy;
            if (world.getBlockAt(bx, y, bz).isEmpty()
                    && world.getBlockAt(bx, y + 1, bz).isEmpty()
                    && !world.getBlockAt(bx, y - 1, bz).isEmpty()) {
                return new Location(world, bx + 0.5, y, bz + 0.5);
            }
        }
        for (int dy = 1; dy <= 5; dy++) {
            final int y = base.getBlockY() - dy;
            if (y < world.getMinHeight() + 2) break;
            if (world.getBlockAt(bx, y, bz).isEmpty()
                    && world.getBlockAt(bx, y + 1, bz).isEmpty()
                    && !world.getBlockAt(bx, y - 1, bz).isEmpty()) {
                return new Location(world, bx + 0.5, y, bz + 0.5);
            }
        }
        return null;
    }

    public boolean hasActiveEvent(final UUID uuid) {
        return this.activeEvents.containsKey(uuid);
    }

    public EventType getActiveEventType(final UUID uuid) {
        final ActiveEvent e = this.activeEvents.get(uuid);
        return e == null ? null : e.type;
    }

    // ═══════════════════════════════════════════
    //  資料結構
    // ═══════════════════════════════════════════

    public enum EventType {
        HUNTER_WAVE("迷途獵手來襲", "3-5 名強化敵人包圍了你，肅清全員以獲得稀有掉落"),
        TREASURE_CHEST("異界寶箱", "附近出現一個神秘寶箱，找到並觸碰它"),
        RESONANCE("遺跡共鳴", "擊殺 10 隻怪物以啟動共鳴"),
        RIFT_ELITE("時空裂影", "一名強大的時空精英出現，擊敗它奪取時空碎片"),
        SILENT_GUARDIAN("沉默守衛", "沉默守衛現身，將它擊倒以取得守衛核心");

        public final String displayName;
        public final String description;

        EventType(final String displayName, final String description) {
            this.displayName = displayName;
            this.description = description;
        }
    }

    private static final class ActiveEvent {
        final UUID id;
        final EventType type;
        final UUID ownerId;
        final long startTime;
        final List<UUID> entities = Collections.synchronizedList(new ArrayList<>());

        ActiveEvent(final UUID id, final EventType type, final UUID ownerId, final long startTime) {
            this.id = id;
            this.type = type;
            this.ownerId = ownerId;
            this.startTime = startTime;
        }
    }
}
