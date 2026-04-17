package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 輻射 + Hazmat 防護衣系統。
 *
 * <p>核心資料：
 * <ul>
 *   <li>每位玩家的輻射值（0.0 ~ 100.0），存於 {@link PlayerProgressService} stat "radiation_x100"
 *       （×100 避免小數）</li>
 *   <li>附近輻射源機器（例：核反應堆）每 tick 往 RadiationService 註冊一筆「輻射暴露事件」，
 *       下一次 tick 掃描時才會結算到周圍玩家身上。這樣機器實作不需要知道 Player 實例。</li>
 * </ul>
 *
 * <p>每秒執行的 tick（{@link #tickAll()}）：
 * <ol>
 *   <li>衰減：每秒 -1.0 輻射（不穿任何 Hazmat 時）</li>
 *   <li>結算：遍歷「待處理暴露事件」，找出 16 格內玩家，根據 Hazmat 穿戴情況決定吸收多少</li>
 *   <li>症狀：根據輻射值套用 potion effect（Nausea / Poison / Wither + 扣血）</li>
 *   <li>Actionbar 警示：當輻射值 > 20 時顯示紅色警告條</li>
 * </ol>
 *
 * <p>Hazmat 四件套穿齊時：
 * <ul>
 *   <li>完全阻擋輻射吸收（仍會顯示 actionbar 警告）</li>
 *   <li>每次阻擋事件消耗一件套裝耐久 1</li>
 *   <li>輻射自然衰減速度 ×3</li>
 * </ul>
 */
public final class RadiationService {

    public static final String STAT_KEY = "radiation_x100";
    /** 輻射值上限（= 100.0）。 */
    private static final long MAX_RADIATION_X100 = 10_000L;
    /** 警告閾值。 */
    public static final double WARN_LEVEL = 20.0;
    public static final double SICK_LEVEL = 40.0;
    public static final double DEADLY_LEVEL = 70.0;

    /** 四件套 tech item id。 */
    public static final String HELMET_ID = "hazmat_helmet";
    public static final String CHESTPLATE_ID = "hazmat_chestplate";
    public static final String LEGGINGS_ID = "hazmat_leggings";
    public static final String BOOTS_ID = "hazmat_boots";

    private final TechMCPlugin plugin;
    private final PlayerProgressService progressService;

    /** 待結算的輻射暴露事件。key = 機器位置序列化字串，value = 本秒的輻射強度。 */
    private final Map<String, ExposureEvent> pendingExposures = new HashMap<>();

    public RadiationService(final TechMCPlugin plugin, final PlayerProgressService progressService) {
        this.plugin = plugin;
        this.progressService = progressService;
    }

    public void start() {
        // 每 20 tick 執行一次結算
        this.plugin.getSafeScheduler().runGlobalTimer(task -> this.tickAll(), 60L, 20L);
    }

    // ═══════════════════════════════════════════
    //  暴露登記 API（供輻射源機器呼叫）
    // ═══════════════════════════════════════════

    /**
     * 機器每 tick 呼叫這支 API 告訴 RadiationService「我這裡正在放射」。
     * 實際的輻射結算會延後到下一次 {@link #tickAll()}。
     *
     * @param sourceLoc 輻射源位置
     * @param radius   影響半徑（格）
     * @param strength 每秒輻射增量（推薦 0.5 ~ 5.0）
     */
    public void registerExposure(final Location sourceLoc, final double radius, final double strength) {
        if (sourceLoc == null || sourceLoc.getWorld() == null) return;
        final String key = sourceLoc.getWorld().getName() + ":" + sourceLoc.getBlockX()
                + ":" + sourceLoc.getBlockY() + ":" + sourceLoc.getBlockZ();
        this.pendingExposures.merge(key, new ExposureEvent(sourceLoc.clone(), radius, strength),
                (a, b) -> new ExposureEvent(a.loc, Math.max(a.radius, b.radius), a.strength + b.strength));
    }

    // ═══════════════════════════════════════════
    //  玩家輻射查詢 API
    // ═══════════════════════════════════════════

    public double getRadiation(final UUID uuid) {
        return this.progressService.getStat(uuid, STAT_KEY) / 100.0;
    }

    public void setRadiation(final UUID uuid, final double value) {
        final long clamped = Math.max(0L, Math.min(MAX_RADIATION_X100, Math.round(value * 100.0)));
        this.progressService.setStat(uuid, STAT_KEY, clamped);
    }

    public void addRadiation(final UUID uuid, final double amount) {
        this.setRadiation(uuid, this.getRadiation(uuid) + amount);
    }

    public void clearRadiation(final UUID uuid) {
        this.setRadiation(uuid, 0.0);
    }

    // ═══════════════════════════════════════════
    //  Tick
    // ═══════════════════════════════════════════

    private void tickAll() {
        // 1. 結算待處理的暴露事件
        final Map<String, ExposureEvent> exposures = new HashMap<>(this.pendingExposures);
        this.pendingExposures.clear();
        for (final ExposureEvent evt : exposures.values()) {
            this.applyExposure(evt);
        }

        // 2. 對所有線上玩家做衰減 + 症狀處理
        for (final Player player : this.plugin.getServer().getOnlinePlayers()) {
            this.tickPlayer(player);
        }
    }

    private void applyExposure(final ExposureEvent evt) {
        if (evt.loc.getWorld() == null) return;
        for (final Player player : evt.loc.getWorld().getPlayers()) {
            if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                    || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            final double distSq = player.getLocation().distanceSquared(evt.loc);
            if (distSq > evt.radius * evt.radius) continue;
            // 距離越近吸收越多（線性衰減）
            final double falloff = 1.0 - Math.sqrt(distSq) / evt.radius;
            double absorbed = evt.strength * falloff;

            // Hazmat 全套 → 消耗耐久但不吸收輻射
            final int hazmatPieces = this.countHazmatPieces(player);
            if (hazmatPieces == 4) {
                this.damageHazmatSuit(player, 1);
                absorbed = 0.0;
            } else if (hazmatPieces > 0) {
                // 穿部分套裝 → 每件減 20% 吸收
                absorbed *= (1.0 - hazmatPieces * 0.2);
                this.damageHazmatSuit(player, 1);
            }

            if (absorbed > 0) {
                this.addRadiation(player.getUniqueId(), absorbed);
                // 體感粒子：玩家身上冒綠色電光
                player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                        player.getLocation().add(0, 1, 0), 6, 0.4, 0.6, 0.4, 0.05);
            }
        }
    }

    private void tickPlayer(final Player player) {
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE
                || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        double radiation = this.getRadiation(uuid);
        if (radiation <= 0) return;

        // 自然衰減
        final int hazmatPieces = this.countHazmatPieces(player);
        final double decay = hazmatPieces == 4 ? 3.0 : 1.0;
        radiation = Math.max(0.0, radiation - decay);
        this.setRadiation(uuid, radiation);

        // 症狀
        if (radiation >= DEADLY_LEVEL) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 1, false, true));
            // 直接扣 1 HP
            player.damage(1.0);
        } else if (radiation >= SICK_LEVEL) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, true));
        } else if (radiation >= WARN_LEVEL) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 40, 0, false, true));
        }

        // Actionbar 警示（只在 >= WARN_LEVEL 且未拿施法武器時顯示，避免跟 ManaService 搶）
        if (radiation >= WARN_LEVEL && !com.rui.techproject.service.talent.SpellCastService
                .isSpellWeapon(player.getInventory().getItemInMainHand())) {
            player.sendActionBar(this.buildRadiationBar(radiation));
        }

        // 音效
        if (radiation >= SICK_LEVEL && System.currentTimeMillis() % 4000 < 1100) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT,
                    SoundCategory.PLAYERS, 0.4f, 0.6f);
        }
    }

    // ═══════════════════════════════════════════
    //  Hazmat 套裝檢查
    // ═══════════════════════════════════════════

    public int countHazmatPieces(final Player player) {
        int count = 0;
        if (this.isHazmat(player.getInventory().getHelmet(), HELMET_ID)) count++;
        if (this.isHazmat(player.getInventory().getChestplate(), CHESTPLATE_ID)) count++;
        if (this.isHazmat(player.getInventory().getLeggings(), LEGGINGS_ID)) count++;
        if (this.isHazmat(player.getInventory().getBoots(), BOOTS_ID)) count++;
        return count;
    }

    private boolean isHazmat(final ItemStack stack, final String expectedId) {
        if (stack == null) return false;
        final String id = this.plugin.getItemFactory().getTechItemId(stack);
        return expectedId.equalsIgnoreCase(id);
    }

    /** 對穿戴中的 Hazmat 每件扣 damage 點耐久，壞掉會自動消失。 */
    private void damageHazmatSuit(final Player player, final int damage) {
        this.damagePiece(player, EquipmentSlot.HEAD, HELMET_ID, damage);
        this.damagePiece(player, EquipmentSlot.CHEST, CHESTPLATE_ID, damage);
        this.damagePiece(player, EquipmentSlot.LEGS, LEGGINGS_ID, damage);
        this.damagePiece(player, EquipmentSlot.FEET, BOOTS_ID, damage);
    }

    private void damagePiece(final Player player, final EquipmentSlot slot,
                              final String expectedId, final int damage) {
        final ItemStack stack = player.getInventory().getItem(slot);
        if (!this.isHazmat(stack, expectedId)) return;
        if (!(stack.getItemMeta() instanceof Damageable dmg)) return;
        final int newDmg = dmg.getDamage() + damage;
        if (newDmg >= stack.getType().getMaxDurability()) {
            player.getInventory().setItem(slot, null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK,
                    SoundCategory.PLAYERS, 0.8f, 1.0f);
            return;
        }
        dmg.setDamage(newDmg);
        stack.setItemMeta((org.bukkit.inventory.meta.ItemMeta) dmg);
        player.getInventory().setItem(slot, stack);
    }

    // ═══════════════════════════════════════════
    //  UI
    // ═══════════════════════════════════════════

    private Component buildRadiationBar(final double current) {
        final int bars = 20;
        final double ratio = Math.min(1.0, current / 100.0);
        final int filled = (int) Math.round(bars * ratio);
        final StringBuilder bar = new StringBuilder();
        for (int i = 0; i < bars; i++) bar.append(i < filled ? "▮" : "░");
        final TextColor color = current >= DEADLY_LEVEL
                ? TextColor.color(0xDC2626)
                : current >= SICK_LEVEL ? TextColor.color(0xF97316)
                        : TextColor.color(0xFBBF24);
        return Component.text("☢ 輻射 ", color)
                .decoration(TextDecoration.ITALIC, false)
                .append(Component.text(bar.toString(), color))
                .append(Component.text(" " + ((int) current) + "/100",
                        TextColor.color(0xF3F4F6)));
    }

    // ═══════════════════════════════════════════
    //  內部資料結構
    // ═══════════════════════════════════════════

    private static final class ExposureEvent {
        final Location loc;
        final double radius;
        final double strength;

        ExposureEvent(final Location loc, final double radius, final double strength) {
            this.loc = loc;
            this.radius = radius;
            this.strength = strength;
        }
    }
}
