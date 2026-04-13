package com.rui.techproject.service.talent;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.service.SkillService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;

/**
 * 24 個主動技能的粒子 + 遊戲效果執行器（MagicSpells 風格改版）。
 *
 * <p>共通施法節奏：
 * <ol>
 *   <li><b>Cast 前搖</b>：在 caster 腳下畫施法圓 + 啟動音，提醒旁人「正在施法」。</li>
 *   <li><b>Charge 蓄力</b>：粒子從外圍螺旋收攏（可選，高階技能才有）。</li>
 *   <li><b>Release 釋放</b>：幾何粒子圖樣 (shockring / helix / sphere / pentagram) 配合主效果。</li>
 *   <li><b>Tail 尾韻</b>：在 release 後幾 tick 內殘留淡化粒子。</li>
 * </ol>
 *
 * <p>所有動畫都透過 SafeScheduler 分散到多個 tick，避免單 tick 撒幾百顆粒子爆 TPS。
 */
public final class SpellExecutor {

    private final TechMCPlugin plugin;
    private final SkillService skillService;
    private final ParticleShapes shapes;

    public SpellExecutor(final TechMCPlugin plugin, final SkillService skillService) {
        this.plugin = plugin;
        this.skillService = skillService;
        this.shapes = new ParticleShapes(plugin);
    }

    /** 入口：執行指定法術。 */
    public void execute(final Player caster, final SpellId spell) {
        if (caster == null || spell == null) return;

        // 共通施法圓（cast circle）+ 啟動音 + 訊息
        this.playCastCircle(caster);
        caster.playSound(caster.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT,
                SoundCategory.PLAYERS, 0.8f, 1.4f);
        caster.sendMessage(Component.text("✦ ", TextColor.color(0xC4B5FD))
                .append(Component.text(spell.displayName, TextColor.color(0xEC4899), TextDecoration.BOLD))
                .append(Component.text(" ▸ " + spell.description, TextColor.color(0xCBD5E1))
                        .decoration(TextDecoration.ITALIC, false)));

        switch (spell) {
            case COMBAT_LRL -> this.waterSurfaceSlash(caster);
            case COMBAT_RLR -> this.flowingDance(caster);
            case COMBAT_LLR -> this.whirlpool(caster);
            case COMBAT_RRL -> this.deadCalm(caster);
            case EXPLORATION_LRL -> this.windWaltz(caster);
            case EXPLORATION_RLR -> this.grapplingHook(caster);
            case EXPLORATION_LLR -> this.huntersMark(caster);
            case EXPLORATION_RRL -> this.shadowStrike(caster);
            case GATHERING_LRL -> this.veinChain(caster);
            case GATHERING_RLR -> this.earthPulse(caster);
            case GATHERING_LLR -> this.bountifulBlessing(caster);
            case GATHERING_RRL -> this.earthResonance(caster);
            case ENGINEERING_LRL -> this.mechConstruct(caster);
            case ENGINEERING_RLR -> this.overloadCore(caster);
            case ENGINEERING_LLR -> this.autoRepair(caster);
            case ENGINEERING_RRL -> this.empBurst(caster);
            case RESEARCH_LRL -> this.meteor(caster);
            case RESEARCH_RLR -> this.timeFreeze(caster);
            case RESEARCH_LLR -> this.elementalBeam(caster);
            case RESEARCH_RRL -> this.quantumDup(caster);
            case RESONANCE_LRL -> this.healingWave(caster);
            case RESONANCE_RLR -> this.resonanceShield(caster);
            case RESONANCE_LLR -> this.powerResonance(caster);
            case RESONANCE_RRL -> this.beaconCall(caster);
        }
    }

    /** 共通施法圓：每個技能都會先畫一圈紫色 dust + enchant 星光。 */
    private void playCastCircle(final Player p) {
        final Location l = p.getLocation();
        final Particle.DustOptions cast = ParticleShapes.dust(0xC4B5FD, 1.3f);
        ParticleShapes.circleH(l.clone().add(0, 0.1, 0), 1.4, 24, Particle.DUST, cast);
        l.getWorld().spawnParticle(Particle.ENCHANT, l.clone().add(0, 1, 0),
                30, 0.4, 0.6, 0.4, 0.6);
    }

    // ═══════════════════════════════════════════════════════════
    //  COMBAT — 蒼浪劍道
    // ═══════════════════════════════════════════════════════════

    // ── 水系共用色 ──
    private static final Particle.DustOptions WATER_BLUE   = ParticleShapes.dust(0x22A7F0, 1.6f);
    private static final Particle.DustOptions WATER_LIGHT  = ParticleShapes.dust(0x7DD3FC, 1.3f);
    private static final Particle.DustOptions WATER_WHITE  = ParticleShapes.dust(0xD6F0FF, 1.0f);
    private static final Particle.DustOptions WATER_DEEP   = ParticleShapes.dust(0x1565C0, 1.8f);
    private static final Particle.DustOptions WATER_CYAN   = ParticleShapes.dust(0x00EAFF, 1.4f);
    private static final Particle.DustOptions WATER_FOAM   = ParticleShapes.dust(0xE0F7FA, 0.8f);

    /**
     * LRL 碎浪斬 — 前方 180° 高密度弧形劍氣。
     * 三階段：初斬（窄弧）→ 中斬（中弧+前衝）→ 全力弧斬（6 層密集弧+雲霧掃描）。
     */
    private void waterSurfaceSlash(final Player p) {
        final double bonus = this.magicOrStrengthMultiplier(p, false);
        final double baseDmg = 14.0 * bonus;

        // ── 階段 1：初斬（t=0）── 小前衝 + 窄弧
        final Location loc1 = p.getLocation();
        p.setVelocity(loc1.getDirection().setY(0).normalize().multiply(0.4));
        loc1.getWorld().playSound(loc1, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
        ParticleShapes.denseArc(loc1, loc1.getYaw(), 3.0, 1.0, 90, 0.12,
                Particle.DUST, WATER_LIGHT);
        ParticleShapes.denseArc(loc1, loc1.getYaw(), 3.5, 1.0, 90, 0.15,
                Particle.DUST, WATER_CYAN);

        // ── 階段 2：中斬（t=4）── 大前衝 + 中弧
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            p.setVelocity(p.getLocation().getDirection().setY(0).normalize().multiply(0.8));
            final Location loc2 = p.getLocation();
            loc2.getWorld().playSound(loc2, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.9f);
            loc2.getWorld().playSound(loc2, Sound.ITEM_TRIDENT_THROW, 0.8f, 1.6f);
            ParticleShapes.denseArc(loc2, loc2.getYaw(), 4.0, 0.8, 140, 0.12,
                    Particle.DUST, WATER_BLUE);
            ParticleShapes.denseArc(loc2, loc2.getYaw(), 4.5, 1.0, 140, 0.1,
                    Particle.DUST, WATER_CYAN);
            ParticleShapes.denseArc(loc2, loc2.getYaw(), 5.0, 1.2, 140, 0.15,
                    Particle.DUST, WATER_WHITE);
        }, 4L);

        // ── 階段 3：全力弧斬（t=8）── 6 層高密度 180° 弧 + 雲霧掃描
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            final Location loc3 = p.getLocation();
            final float yaw = loc3.getYaw();
            loc3.getWorld().playSound(loc3, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 1.0f);
            loc3.getWorld().playSound(loc3, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.6f, 1.8f);

            // 6 層高密度弧（不同半徑、高度、顏色、密度）
            ParticleShapes.denseArc(loc3, yaw, 4.5, 0.7, 180, 0.15,
                    Particle.DUST, WATER_DEEP);
            ParticleShapes.denseArc(loc3, yaw, 5.0, 0.85, 180, 0.12,
                    Particle.DUST, WATER_BLUE);
            ParticleShapes.denseArc(loc3, yaw, 5.5, 1.0, 180, 0.1,
                    Particle.DUST, WATER_CYAN);
            ParticleShapes.denseArc(loc3, yaw, 6.0, 1.0, 180, 0.1,
                    Particle.DUST, WATER_LIGHT);
            ParticleShapes.denseArc(loc3, yaw, 6.3, 1.1, 180, 0.12,
                    Particle.DUST, WATER_WHITE);
            ParticleShapes.denseArc(loc3, yaw, 6.5, 1.2, 180, 0.15,
                    Particle.DUST, WATER_FOAM);

            // 雲霧掃描動畫（14 tick，每 tick 6 顆 cloud）
            this.shapes.animateCloudSweep(loc3, yaw, 6.3, 1.0, 180, 14, 6);

            // 水花飛濺
            loc3.getWorld().spawnParticle(Particle.SPLASH, loc3.clone().add(0, 0.3, 0),
                    60, 4.0, 0.2, 4.0, 0.1);
            loc3.getWorld().spawnParticle(Particle.DRIPPING_WATER, loc3.clone().add(0, 1.5, 0),
                    30, 4.0, 0.5, 4.0, 0);

            // AoE 傷害 — 前方 6 格 180°
            final Vector facing = loc3.getDirection().setY(0).normalize();
            for (final Entity e : p.getNearbyEntities(6, 3, 6)) {
                if (e instanceof LivingEntity le && !e.equals(p)) {
                    final Vector toEntity = le.getLocation().toVector()
                            .subtract(loc3.toVector()).setY(0).normalize();
                    if (facing.dot(toEntity) > 0.0) {
                        le.damage(baseDmg, p);
                        le.setVelocity(toEntity.multiply(0.6).setY(0.4));
                        le.getWorld().spawnParticle(Particle.SPLASH,
                                le.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
                    }
                }
            }
        }, 8L);

        // ── 餘韻漣漪（t=16）──
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            final Location end = p.getLocation();
            this.shapes.animateDenseRipples(end.clone().add(0, 0.05, 0),
                    5.0, 3, 6, 0.15, Particle.DUST, WATER_CYAN);
            end.getWorld().playSound(end, Sound.ENTITY_GENERIC_SPLASH, 0.8f, 1.4f);
        }, 16L);
    }

    /**
     * RLR 奔流三式 — 3 段水流突進 + 殘影軌跡。
     * 每段衝刺繪製高密度垂直弧 + 水流軌跡。
     */
    private void flowingDance(final Player p) {
        final double bonus = this.magicOrStrengthMultiplier(p, false);
        final double baseDmg = 9.0 * bonus;
        final Particle.DustOptions[] stepColors = {WATER_LIGHT, WATER_BLUE, WATER_DEEP};
        final Particle.DustOptions[] trailColors = {WATER_CYAN, WATER_CYAN, WATER_WHITE};

        for (int step = 0; step < 3; step++) {
            final int s = step;
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                if (!p.isOnline()) return;
                final Location start = p.getLocation();
                final float yaw = start.getYaw();
                final Vector dir = start.getDirection().normalize().setY(0.12);
                p.setVelocity(dir.clone().multiply(1.8));

                start.getWorld().playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                        1.0f, 1.0f + s * 0.2f);
                start.getWorld().playSound(start, Sound.ENTITY_DOLPHIN_SWIM,
                        0.7f, 1.2f + s * 0.15f);

                // 高密度垂直弧（3 層，每段漸深）
                final Location arcCenter = start.clone().add(0, 0.5, 0);
                ParticleShapes.denseArcV(arcCenter, yaw, 1.2, 0.1,
                        Particle.DUST, stepColors[s]);
                ParticleShapes.denseArcV(arcCenter, yaw, 1.5, 0.12,
                        Particle.DUST, trailColors[s]);
                ParticleShapes.denseArcV(arcCenter, yaw, 1.8, 0.15,
                        Particle.DUST, WATER_FOAM);

                // 水流軌跡線
                final Location trailEnd = start.clone()
                        .subtract(dir.clone().normalize().multiply(3));
                trailEnd.setY(start.getY() + 1.0);
                ParticleShapes.line(trailEnd, start.clone().add(0, 1.0, 0),
                        0.12, Particle.DUST, stepColors[s]);

                // 水花效果
                start.getWorld().spawnParticle(Particle.SPLASH,
                        start.clone().add(0, 1, 0), 25, 0.5, 0.3, 0.5, 0.05);
                start.getWorld().spawnParticle(Particle.BUBBLE_POP,
                        start.clone().add(0, 0.8, 0), 12, 0.3, 0.3, 0.3, 0.02);
                start.getWorld().spawnParticle(Particle.CLOUD,
                        start.clone().add(0, 0.5, 0), 8, 0.4, 0.2, 0.4, 0.02);

                // 傷害
                for (final Entity e : p.getNearbyEntities(3, 2, 3)) {
                    if (e instanceof LivingEntity le && !e.equals(p)) {
                        le.damage(baseDmg, p);
                        le.getWorld().spawnParticle(Particle.SPLASH,
                                le.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
                    }
                }
            }, step * 7L);
        }

        // 最終水花爆發 (t=21)
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            final Location end = p.getLocation();
            final float yaw = end.getYaw();
            ParticleShapes.denseArc(end, yaw, 2.0, 0.5, 360, 0.1,
                    Particle.DUST, WATER_CYAN);
            ParticleShapes.denseArc(end, yaw, 2.5, 0.8, 360, 0.12,
                    Particle.DUST, WATER_LIGHT);
            ParticleShapes.denseArc(end, yaw, 3.0, 1.0, 360, 0.15,
                    Particle.DUST, WATER_FOAM);
            end.getWorld().spawnParticle(Particle.SPLASH,
                    end.clone().add(0, 0.5, 0), 50, 2.0, 0.5, 2.0, 0.15);
            end.getWorld().spawnParticle(Particle.BUBBLE_POP,
                    end.clone().add(0, 1.0, 0), 25, 1.5, 0.8, 1.5, 0.05);
            end.getWorld().playSound(end, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 0.8f);
        }, 21L);
    }

    /**
     * LLR 漩淵 — 生成水漩渦，吸引並持續傷害。
     * 高密度螺旋龍捲 + 持續吸引。
     */
    private void whirlpool(final Player p) {
        final Location center = p.getLocation();
        final double bonus = this.magicOrStrengthMultiplier(p, false);
        final double tickDmg = 3.0 * bonus;

        center.getWorld().playSound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 1.8f);
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 0.8f);
        center.getWorld().playSound(center, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.8f, 0.6f);

        // 高密度龍捲動畫 — 外層（4 臂，60 tick）
        this.shapes.animateDenseTornado(center, 3.0, 5.0, 60, 4, 0.15,
                Particle.DUST, WATER_BLUE);
        // 內層（3 臂）
        this.shapes.animateDenseTornado(center, 1.5, 4.0, 60, 3, 0.2,
                Particle.DUST, WATER_WHITE);

        // 底部持續水花 + cloud
        for (int t = 0; t < 60; t++) {
            this.plugin.getSafeScheduler().runRegionDelayed(center, task -> {
                center.getWorld().spawnParticle(Particle.SPLASH,
                        center.clone().add(0, 0.3, 0), 10, 2.5, 0.2, 2.5, 0.05);
                center.getWorld().spawnParticle(Particle.CLOUD,
                        center.clone().add(0, 0.5, 0), 4, 1.5, 0.3, 1.5, 0.02);
                center.getWorld().spawnParticle(Particle.BUBBLE_POP,
                        center.clone().add(0, 2.5, 0), 6, 1.0, 1.5, 1.0, 0.02);
            }, t);
        }

        // 每 10 tick 傷害 + 吸引 (共 6 次)
        for (int pulse = 0; pulse < 6; pulse++) {
            this.plugin.getSafeScheduler().runRegionDelayed(center, task -> {
                for (final Entity e : center.getWorld().getNearbyEntities(center, 5, 4, 5)) {
                    if (e instanceof LivingEntity le && !(e instanceof Player pl && pl.equals(p))) {
                        final Vector pull = center.toVector().subtract(le.getLocation().toVector())
                                .setY(0.3).normalize().multiply(0.5);
                        le.setVelocity(pull);
                        le.damage(tickDmg, p);
                        le.getWorld().spawnParticle(Particle.SPLASH,
                                le.getLocation().add(0, 1, 0), 6, 0.2, 0.3, 0.2, 0.05);
                    }
                }
                center.getWorld().spawnParticle(Particle.END_ROD,
                        center.clone().add(0, 2.5, 0), 10, 0.5, 1.5, 0.5, 0.03);
            }, pulse * 10L);
        }

        // 結束爆發 (t=60)
        this.plugin.getSafeScheduler().runRegionDelayed(center, task -> {
            ParticleShapes.sphereShell(center.clone().add(0, 2, 0), 3.5, 80,
                    Particle.DUST, WATER_CYAN);
            ParticleShapes.sphereShell(center.clone().add(0, 2, 0), 3.0, 60,
                    Particle.DUST, WATER_WHITE);
            center.getWorld().spawnParticle(Particle.SPLASH,
                    center.clone().add(0, 1, 0), 100, 4.0, 2.0, 4.0, 0.2);
            center.getWorld().spawnParticle(Particle.DRIPPING_WATER,
                    center.clone().add(0, 4, 0), 50, 3.0, 1.0, 3.0, 0);
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_SPLASH, 1.2f, 0.6f);
        }, 60L);
    }

    /**
     * RRL 止水 — 止水之境。
     * 3 秒無敵 + 高密度漣漪結界 + 結束爆發。
     */
    private void deadCalm(final Player p) {
        final Location loc = p.getLocation();
        final double bonus = this.magicOrStrengthMultiplier(p, false);

        loc.getWorld().playSound(loc, Sound.AMBIENT_UNDERWATER_ENTER, 1.0f, 1.2f);
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.8f);

        // 高密度漣漪（6 波，每波 10 tick，0.12 間距）
        this.shapes.animateDenseRipples(loc.clone().add(0, 0.05, 0),
                5.0, 6, 10, 0.12, Particle.DUST, WATER_LIGHT);
        this.shapes.animateDenseRipples(loc.clone().add(0, 0.08, 0),
                6.0, 4, 15, 0.18, Particle.DUST, WATER_FOAM);

        // 光柱
        this.shapes.animateLightPillar(loc, 2.5, 3.5, 15, 30,
                Particle.DUST, WATER_CYAN);
        this.shapes.animateLightPillar(loc, 3.0, 4.0, 18, 24,
                Particle.DUST, WATER_FOAM);

        // 中心粒子
        loc.getWorld().spawnParticle(Particle.DRIPPING_WATER,
                loc.clone().add(0, 2, 0), 40, 2.0, 1.5, 2.0, 0);
        loc.getWorld().spawnParticle(Particle.END_ROD,
                loc.clone().add(0, 1.5, 0), 20, 1.5, 1.0, 1.5, 0.01);

        // 無敵 buff (60 tick = 3 秒)
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 254, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, false));

        // 持續水環（每 5t 畫高密度圓環）
        for (int i = 0; i < 12; i++) {
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                if (!p.isOnline()) return;
                final Location l = p.getLocation();
                final int pts1 = (int) (2 * Math.PI * 2.5 / 0.12);
                ParticleShapes.circleH(l.clone().add(0, 0.05, 0), 2.5, pts1,
                        Particle.DUST, WATER_LIGHT);
                final int pts2 = (int) (2 * Math.PI * 1.8 / 0.15);
                ParticleShapes.circleH(l.clone().add(0, 2.5, 0), 1.8, pts2,
                        Particle.DUST, WATER_FOAM);
                l.getWorld().spawnParticle(Particle.SPLASH,
                        l.clone().add(0, 0.1, 0), 6, 1.5, 0.1, 1.5, 0.01);
                l.getWorld().spawnParticle(Particle.CLOUD,
                        l.clone().add(0, 1.5, 0), 3, 0.8, 0.5, 0.8, 0.01);
            }, i * 5L);
        }

        // 結界結束 — 水面碎裂爆發 (t=60)
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            final Location end = p.getLocation();
            final float yaw = end.getYaw();
            final double burstDmg = 18.0 * bonus;

            end.getWorld().playSound(end, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.8f);
            end.getWorld().playSound(end, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 0.6f);
            end.getWorld().playSound(end, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.0f, 1.2f);

            // 高密度球殼
            ParticleShapes.sphereShell(end.clone().add(0, 1.5, 0), 4.0, 100,
                    Particle.DUST, WATER_BLUE);
            ParticleShapes.sphereShell(end.clone().add(0, 1.5, 0), 3.5, 80,
                    Particle.DUST, WATER_WHITE);
            // 360° 衝擊環
            ParticleShapes.denseArc(end, yaw, 5.0, 0.1, 360, 0.1,
                    Particle.DUST, WATER_DEEP);
            ParticleShapes.denseArc(end, yaw, 5.5, 0.1, 360, 0.12,
                    Particle.DUST, WATER_CYAN);

            end.getWorld().spawnParticle(Particle.SPLASH,
                    end.clone().add(0, 1, 0), 100, 4.0, 2.0, 4.0, 0.3);
            end.getWorld().spawnParticle(Particle.BUBBLE_POP,
                    end.clone().add(0, 2, 0), 40, 3.0, 1.5, 3.0, 0.05);

            this.shapes.animateShockRing(end.clone().add(0, 0.1, 0),
                    0.5, 7.0, 14, 36, Particle.DUST, WATER_DEEP);

            for (final Entity e : p.getNearbyEntities(6, 3, 6)) {
                if (e instanceof LivingEntity le && !e.equals(p)) {
                    le.damage(burstDmg, p);
                    le.setVelocity(le.getLocation().toVector()
                            .subtract(end.toVector()).setY(0).normalize()
                            .multiply(0.8).setY(0.5));
                    le.getWorld().spawnParticle(Particle.SPLASH,
                            le.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.1);
                }
            }
        }, 60L);
    }


    // ═══════════════════════════════════════════════════════════
    //  EXPLORATION
    // ═══════════════════════════════════════════════════════════

    /** LRL 疾風三步 — 3 段瞬移，每段畫雲霧環。 */
    private void windWaltz(final Player p) {
        final Particle.DustOptions sky = ParticleShapes.dust(0x7DD3FC, 1.4f);
        for (int step = 0; step < 3; step++) {
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                if (!p.isOnline()) return;
                final Vector dir = p.getLocation().getDirection().normalize().multiply(8);
                final Location target = p.getLocation().add(dir);
                if (target.getBlock().getType().isAir()) {
                    p.teleportAsync(target);
                }
                final Location l = p.getLocation().add(0, 1, 0);
                // 雲霧環 + 星光
                ParticleShapes.circleH(l, 1.8, 28, Particle.CLOUD, null);
                ParticleShapes.circleH(l.clone().add(0, 0.5, 0), 1.4, 20, Particle.DUST, sky);
                l.getWorld().spawnParticle(Particle.END_ROD, l, 20, 0.5, 0.8, 0.5, 0.06);
                l.getWorld().spawnParticle(Particle.POOF, l, 15, 0.4, 0.2, 0.4, 0.1);
                l.getWorld().playSound(l, Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.8f);
            }, step * 8L);
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0, false, true));
    }

    /** RLR 能量鉤索 — 視線粒子線 + 拉向目標。 */
    private void grapplingHook(final Player p) {
        final RayTraceResult ray = p.getWorld().rayTraceBlocks(
                p.getEyeLocation(), p.getLocation().getDirection(), 30);
        if (ray == null || ray.getHitBlock() == null) {
            p.sendMessage(Component.text("  ✗ 視線內無方塊", TextColor.color(0xFB7185))
                    .decoration(TextDecoration.ITALIC, false));
            return;
        }
        final Location target = ray.getHitBlock().getLocation().add(0.5, 0.5, 0.5);
        final Location start = p.getEyeLocation();
        final Particle.DustOptions cyan = ParticleShapes.dust(0x22D3EE, 1.2f);

        // 繩索粒子線 + 閃光
        ParticleShapes.line(start, target, 0.4, Particle.DUST, cyan);
        ParticleShapes.line(start, target, 0.7, Particle.ENCHANTED_HIT, null);

        // 目標端爆星
        target.getWorld().spawnParticle(Particle.FIREWORK, target, 25, 0.4, 0.4, 0.4, 0.15);
        ParticleShapes.sphereShell(target, 0.8, 20, Particle.END_ROD, null);

        // 拉向目標
        final Vector pullVec = target.toVector().subtract(p.getLocation().toVector())
                .normalize().multiply(Math.min(2.0, start.distance(target) * 0.15));
        pullVec.setY(pullVec.getY() + 0.4);
        p.setVelocity(pullVec);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, true));
        start.getWorld().playSound(start, Sound.ENTITY_FISHING_BOBBER_THROW, 1.0f, 1.4f);
        start.getWorld().playSound(start, Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 1.0f, 1.8f);
    }

    /** LLR 獵人之眼 — 腳下金色擴張環 + 生物發光 30s。 */
    private void huntersMark(final Player p) {
        final Location loc = p.getLocation().add(0, 0.1, 0);
        final Particle.DustOptions yellow = ParticleShapes.dust(0xFCD34D, 1.6f);

        this.shapes.animateShockRing(loc, 0.5, 6.0, 20, 30, Particle.DUST, yellow);
        this.shapes.animateShockRing(loc, 0.3, 4.0, 15, 16, Particle.ELECTRIC_SPARK, null);
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);

        int marked = 0;
        for (final Entity e : p.getNearbyEntities(30, 15, 30)) {
            if (e instanceof LivingEntity le && !e.equals(p)) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 600, 0, false, false));
                if (++marked >= 50) break; // 上限 50 個，避免效能問題
            }
        }
        p.sendMessage(Component.text("  ✓ 標記了 " + marked + " 個生物",
                        TextColor.color(0xFCD34D)).decoration(TextDecoration.ITALIC, false));
    }

    /** RRL 影襲 — 深紫煙霧球殼 + 隱身 8s。 */
    private void shadowStrike(final Player p) {
        final Location loc = p.getLocation().add(0, 1, 0);
        final Particle.DustOptions shadow = ParticleShapes.dust(0x312E81, 1.8f);

        ParticleShapes.sphereShell(loc, 1.6, 36, Particle.LARGE_SMOKE, null);
        ParticleShapes.sphereShell(loc, 1.4, 28, Particle.DUST, shadow);
        loc.getWorld().spawnParticle(Particle.SQUID_INK, loc, 20, 0.5, 0.7, 0.5, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.8f);

        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 160, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 2, false, true));
    }

    // ═══════════════════════════════════════════════════════════
    //  GATHERING
    // ═══════════════════════════════════════════════════════════

    /** LRL 礦脈連鎖 — 視線前方礦脈連挖 64 格。 */
    private void veinChain(final Player p) {
        final RayTraceResult ray = p.getWorld().rayTraceBlocks(
                p.getEyeLocation(), p.getLocation().getDirection(), 8);
        if (ray == null || ray.getHitBlock() == null) {
            p.sendMessage(Component.text("  ✗ 視線內無方塊", TextColor.color(0xFB7185))
                    .decoration(TextDecoration.ITALIC, false));
            return;
        }
        final Block origin = ray.getHitBlock();
        final Material type = origin.getType();
        if (!isOre(type)) {
            p.sendMessage(Component.text("  ✗ 該方塊非礦石", TextColor.color(0xFB7185))
                    .decoration(TextDecoration.ITALIC, false));
            return;
        }
        final Set<Block> visited = new HashSet<>();
        final java.util.Deque<Block> queue = new java.util.ArrayDeque<>();
        queue.add(origin);
        int broken = 0;
        final Particle.DustOptions gold = ParticleShapes.dust(0xFBBF24, 1.1f);
        while (!queue.isEmpty() && broken < 64) {
            final Block b = queue.poll();
            if (!visited.add(b) || b.getType() != type) continue;
            broken++;
            final Location l = b.getLocation().add(0.5, 0.5, 0.5);
            b.breakNaturally(p.getInventory().getItemInMainHand());
            // 每個礦點畫小型球殼
            ParticleShapes.sphereShell(l, 0.6, 10, Particle.DUST, gold);
            l.getWorld().spawnParticle(Particle.CRIT, l, 4, 0.2, 0.2, 0.2, 0.1);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        queue.add(b.getRelative(dx, dy, dz));
                    }
        }
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
        p.sendMessage(Component.text("  ✓ 連鎖開採了 " + broken + " 格礦石",
                        TextColor.color(0xFBBF24)).decoration(TextDecoration.ITALIC, false));
    }

    /** RLR 地脈震盪 — 5×5×5 礦物全收 + 綠色擴張環。 */
    private void earthPulse(final Player p) {
        final Location center = p.getLocation();
        final Particle.DustOptions green = ParticleShapes.dust(0x10B981, 1.8f);

        this.shapes.animateShockRing(center.clone().add(0, 0.1, 0),
                0.5, 3.5, 12, 24, Particle.SCULK_CHARGE, null);
        this.shapes.animateShockRing(center.clone().add(0, 0.2, 0),
                0.5, 3.0, 14, 20, Particle.DUST, green);
        center.getWorld().playSound(center, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 1.0f, 1.2f);

        int collected = 0;
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -2; dz <= 2; dz++) {
                    final Block b = center.clone().add(dx, dy, dz).getBlock();
                    if (isOre(b.getType())) {
                        b.breakNaturally(p.getInventory().getItemInMainHand());
                        collected++;
                    }
                }
        p.sendMessage(Component.text("  ✓ 震盪收集了 " + collected + " 礦物",
                        TextColor.color(0x10B981)).decoration(TextDecoration.ITALIC, false));
    }

    /** LLR 豐饒祝福 — 粉色雙螺旋 + 30s 掉落加成。 */
    private void bountifulBlessing(final Player p) {
        final Location loc = p.getLocation();
        final Particle.DustOptions petal = ParticleShapes.dust(0xF9A8D4, 1.6f);

        this.shapes.animateDoubleHelix(loc.clone().add(0, 0.1, 0),
                1.2, 2.8, 3, 22, Particle.DUST, petal);
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1.5, 0),
                30, 1.2, 1.2, 1.2, 0);
        loc.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc.clone().add(0, 2.5, 0),
                30, 1.5, 1.5, 1.5, 0.05);
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.0f, 1.6f);

        p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 600, 4, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 600, 2, false, true));

        // 持續 30s，每 20t 丟幾片花瓣
        for (int i = 0; i < 30; i++) {
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                if (!p.isOnline()) return;
                final Location l = p.getLocation().add(0, 2.5, 0);
                l.getWorld().spawnParticle(Particle.CHERRY_LEAVES, l,
                        6, 0.8, 0.4, 0.8, 0.02);
                l.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, l.clone().add(0, -1, 0),
                        3, 0.5, 0.3, 0.5, 0);
            }, i * 20L);
        }
    }

    /** RRL 大地共鳴 — 50 格礦脈顯形光柱。 */
    private void earthResonance(final Player p) {
        final Location loc = p.getLocation();
        final Particle.DustOptions amber = ParticleShapes.dust(0xFBBF24, 1.4f);

        this.shapes.animateShockRing(loc.clone().add(0, 0.1, 0),
                0.5, 8.0, 18, 36, Particle.ELECTRIC_SPARK, null);
        ParticleShapes.circleH(loc.clone().add(0, 1.5, 0), 3.0, 30, Particle.DUST, amber);
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1.0f, 1.8f);

        int found = 0;
        for (int dx = -25; dx <= 25; dx += 2)
            for (int dy = -10; dy <= 10; dy += 2)
                for (int dz = -25; dz <= 25; dz += 2) {
                    final Block b = loc.clone().add(dx, dy, dz).getBlock();
                    if (isOre(b.getType())) {
                        found++;
                        final Location lb = b.getLocation().add(0.5, 0.5, 0.5);
                        for (int y = 0; y < 3; y++) {
                            lb.getWorld().spawnParticle(Particle.END_ROD,
                                    lb.clone().add(0, y, 0), 1, 0.1, 0.1, 0.1, 0);
                        }
                    }
                }
        p.sendMessage(Component.text("  ✓ 感應到 " + found + " 個礦脈訊號",
                        TextColor.color(0xFBBF24)).decoration(TextDecoration.ITALIC, false));
    }

    // ═══════════════════════════════════════════════════════════
    //  ENGINEERING
    // ═══════════════════════════════════════════════════════════

    /** LRL 機械傀儡 — 召喚前先五芒星蓄力，Iron Golem 30s。 */
    private void mechConstruct(final Player p) {
        final Location loc = p.getLocation().clone().add(
                p.getLocation().getDirection().multiply(2));
        final Particle.DustOptions orange = ParticleShapes.dust(0xF97316, 1.8f);

        // 召喚陣
        ParticleShapes.pentagram(loc.clone().add(0, 0.1, 0), 2.0, Particle.DUST, orange);
        loc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, loc, 60, 0.8, 1.2, 0.8, 0.15);
        loc.getWorld().spawnParticle(Particle.FLAME, loc, 24, 0.6, 0.8, 0.6, 0.05);
        loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_LAND, 0.8f, 1.6f);
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.8f);

        final org.bukkit.entity.IronGolem golem = loc.getWorld().spawn(loc,
                org.bukkit.entity.IronGolem.class, g -> {
                    g.setPlayerCreated(true);
                    g.setCustomName("§6§l科技傀儡");
                    g.setCustomNameVisible(true);
                });
        this.plugin.getSafeScheduler().runRegionDelayed(loc, task -> {
            if (!golem.isDead()) {
                final Location deathLoc = golem.getLocation().add(0, 1, 0);
                ParticleShapes.sphereShell(deathLoc, 1.2, 24, Particle.LARGE_SMOKE, null);
                deathLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, deathLoc,
                        30, 0.5, 1.0, 0.5, 0.1);
                golem.remove();
            }
        }, 600L);
    }

    /** RLR 過載核心 — 橘金色雙螺旋 + 45s Haste III。 */
    private void overloadCore(final Player p) {
        final Location loc = p.getLocation();
        final Particle.DustOptions gold = ParticleShapes.dust(0xD97706, 1.8f);

        this.shapes.animateDoubleHelix(loc.clone().add(0, 0.1, 0),
                1.3, 2.6, 3, 18, Particle.DUST, gold);
        this.shapes.animateShockRing(loc.clone().add(0, 0.1, 0),
                0.5, 3.5, 12, 20, Particle.ELECTRIC_SPARK, null);
        loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0),
                30, 1.0, 0.8, 1.0, 0.02);
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.7f);
        loc.getWorld().playSound(loc, Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.2f);

        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 900, 3, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 900, 1, false, true));
    }

    /** LLR 自動修復 — 銀色球殼 + 附魔星光 + 全裝備修滿。 */
    private void autoRepair(final Player p) {
        final Location loc = p.getLocation().add(0, 1, 0);
        final Particle.DustOptions silver = ParticleShapes.dust(0xCBD5E1, 1.4f);

        ParticleShapes.sphereShell(loc, 1.4, 36, Particle.DUST, silver);
        ParticleShapes.sphereShell(loc, 1.2, 24, Particle.WAX_ON, null);
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(0, 1, 0),
                50, 0.8, 1.2, 0.8, 0.8);
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 15, 0.5, 0.6, 0.5, 0);
        loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_USE, 0.6f, 1.8f);
        loc.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.3f);

        for (final var item : p.getInventory().getArmorContents()) {
            if (item != null && item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg) {
                dmg.setDamage(0);
                item.setItemMeta(dmg);
            }
        }
        final var main = p.getInventory().getItemInMainHand();
        if (main.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg) {
            dmg.setDamage(0);
            main.setItemMeta(dmg);
        }
        final var off = p.getInventory().getItemInOffHand();
        if (off.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg) {
            dmg.setDamage(0);
            off.setItemMeta(dmg);
        }
    }

    /** RRL 電磁脈衝 — 藍白擴張環 + 10 格 AoE 麻痹。 */
    private void empBurst(final Player p) {
        final Location loc = p.getLocation();

        this.shapes.animateShockRing(loc.clone().add(0, 0.1, 0),
                0.5, 10.0, 14, 40, Particle.ELECTRIC_SPARK, null);
        this.shapes.animateShockRing(loc.clone().add(0, 0.2, 0),
                0.3, 8.0, 14, 24, Particle.END_ROD, null);
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc.clone().add(0, 1, 0),
                30, 0.8, 0.8, 0.8, 0.25);
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.8f);
        loc.getWorld().playSound(loc, Sound.BLOCK_CONDUIT_ATTACK_TARGET, 1.2f, 1.2f);

        final double bonus = this.magicOrStrengthMultiplier(p, false);
        for (final Entity e : p.getNearbyEntities(10, 5, 10)) {
            if (e instanceof LivingEntity le && !e.equals(p)) {
                le.damage(6.0 * bonus, p);
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 5, false, true));
                le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 2, false, true));
                final Location el = le.getLocation().add(0, 1, 0);
                el.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, el, 12, 0.3, 0.5, 0.3, 0.15);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  RESEARCH
    // ═══════════════════════════════════════════════════════════

    /** LRL 隕石術 — 紅色預警環 → 下墜動畫 → 爆炸衝擊波。 */
    private void meteor(final Player p) {
        final RayTraceResult ray = p.getWorld().rayTraceBlocks(
                p.getEyeLocation(), p.getLocation().getDirection(), 40);
        final Location impact;
        if (ray == null || ray.getHitBlock() == null) {
            impact = p.getLocation().add(p.getLocation().getDirection().multiply(20));
        } else {
            impact = ray.getHitBlock().getLocation().add(0.5, 1, 0.5);
        }
        final Particle.DustOptions warning = ParticleShapes.dust(0xDC2626, 2.0f);

        // 預警圓 + 五芒星
        ParticleShapes.circleH(impact, 3.0, 40, Particle.DUST, warning);
        ParticleShapes.pentagram(impact.clone().add(0, 0.05, 0), 2.6, Particle.DUST, warning);
        impact.getWorld().playSound(impact, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.6f);

        // 下墜動畫（20 tick 從 30 格高墜落）
        for (int tick = 0; tick < 20; tick++) {
            final int t = tick;
            this.plugin.getSafeScheduler().runRegionDelayed(impact, task -> {
                final Location l = impact.clone().add(0, 30 - t * 1.5, 0);
                // 隕石本體殼
                ParticleShapes.sphereShell(l, 0.9, 18, Particle.FLAME, null);
                ParticleShapes.sphereShell(l, 1.1, 14, Particle.LAVA, null);
                l.getWorld().spawnParticle(Particle.LARGE_SMOKE, l, 10, 0.4, 0.4, 0.4, 0.03);
            }, t * 2L);
        }
        // 爆炸衝擊波
        this.plugin.getSafeScheduler().runRegionDelayed(impact, task -> {
            impact.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, impact, 5, 1, 0.5, 1, 0);
            this.shapes.animateShockRing(impact, 0.5, 7.0, 15, 36, Particle.DUST, warning);
            this.shapes.animateShockRing(impact, 0.3, 5.5, 18, 24, Particle.FLAME, null);
            impact.getWorld().spawnParticle(Particle.LAVA, impact, 40, 2.2, 0.5, 2.2, 0);
            impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);
            impact.getWorld().playSound(impact, Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
            final double bonus = this.magicOrStrengthMultiplier(p, true);
            for (final Entity e : impact.getWorld().getNearbyEntities(impact, 6, 5, 6)) {
                if (e instanceof LivingEntity le && !e.equals(p)) {
                    le.damage(30.0 * bonus, p);
                    le.setFireTicks(100);
                }
            }
        }, 42L);
    }

    /** RLR 時間凍結 — 冰晶球殼 + 15 格 AoE 凍結 5s。 */
    private void timeFreeze(final Player p) {
        final Location loc = p.getLocation().add(0, 1.5, 0);
        final Particle.DustOptions ice = ParticleShapes.dust(0xBFDBFE, 1.8f);

        ParticleShapes.sphereShell(loc, 4.5, 60, Particle.DUST, ice);
        ParticleShapes.sphereShell(loc, 4.0, 42, Particle.SNOWFLAKE, null);
        ParticleShapes.sphereShell(loc, 3.5, 28, Particle.WHITE_ASH, null);
        loc.getWorld().spawnParticle(Particle.INSTANT_EFFECT, loc, 30, 3.0, 2.0, 3.0, 0);
        loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.4f);
        loc.getWorld().playSound(loc, Sound.BLOCK_POWDER_SNOW_STEP, 1.0f, 0.8f);

        for (final Entity e : p.getNearbyEntities(15, 8, 15)) {
            if (e instanceof LivingEntity le && !e.equals(p)) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 255, false, true));
                le.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 10, false, true));
                le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 10, false, true));
                le.setFreezeTicks(140);
                final Location el = le.getLocation().add(0, 1, 0);
                ParticleShapes.circleH(el, 0.8, 12, Particle.WHITE_ASH, null);
            }
        }
    }

    /** LLR 元素裂變 — 漸變紫色穿透能量束。 */
    private void elementalBeam(final Player p) {
        final Vector dir = p.getLocation().getDirection().normalize();
        final Location start = p.getEyeLocation();
        final double bonus = this.magicOrStrengthMultiplier(p, true);
        final Set<LivingEntity> hit = new HashSet<>();
        final Particle.DustOptions violet = ParticleShapes.dust(0x8B5CF6, 1.4f);

        // 主能量束 + 側邊螺旋包覆
        for (int i = 0; i < 40; i++) {
            final Location point = start.clone().add(dir.clone().multiply(i * 0.5));
            point.getWorld().spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
            point.getWorld().spawnParticle(Particle.END_ROD, point, 2, 0.1, 0.1, 0.1, 0);
            // 雙側螺旋小環
            if (i % 2 == 0) {
                ParticleShapes.circleV(point, 0.35, 8, dir, Particle.DUST, violet);
            }
            for (final Entity e : point.getWorld().getNearbyEntities(point, 1.2, 1.2, 1.2)) {
                if (e instanceof LivingEntity le && !e.equals(p) && hit.add(le)) {
                    le.damage(18.0 * bonus, p);
                    point.getWorld().spawnParticle(Particle.INSTANT_EFFECT,
                            le.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.1);
                }
            }
        }
        start.getWorld().playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.4f);
    }

    /** RRL 量子複製 — 雙向 portal 漩渦 + Luck 30s。 */
    private void quantumDup(final Player p) {
        final Location loc = p.getLocation();
        final Particle.DustOptions purple = ParticleShapes.dust(0xA855F7, 1.6f);

        this.shapes.animateDoubleHelix(loc.clone().add(0, 0.1, 0),
                1.0, 2.4, 4, 20, Particle.REVERSE_PORTAL, null);
        this.shapes.animateDoubleHelix(loc.clone().add(0, 0.2, 0),
                1.2, 2.6, 3, 20, Particle.DUST, purple);
        loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0),
                80, 1.0, 1.2, 1.0, 0.4);
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(0, 2, 0),
                60, 1.0, 1.2, 1.0, 1.0);
        loc.getWorld().playSound(loc, Sound.BLOCK_PORTAL_AMBIENT, 1.0f, 1.8f);
        loc.getWorld().playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.6f);

        p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 600, 9, false, true));
        p.sendActionBar(Component.text("✦ 量子態就緒 — 下次合成加成",
                TextColor.color(0xC4B5FD)).decoration(TextDecoration.ITALIC, false));
    }

    // ═══════════════════════════════════════════════════════════
    //  RESONANCE
    // ═══════════════════════════════════════════════════════════

    /** LRL 治癒波動 — 粉色擴張環 + 15 格 AoE 回 60% HP。 */
    private void healingWave(final Player p) {
        final Location loc = p.getLocation();
        final Particle.DustOptions pink = ParticleShapes.dust(0xF9A8D4, 1.6f);

        this.shapes.animateShockRing(loc.clone().add(0, 0.1, 0),
                0.5, 7.5, 18, 32, Particle.DUST, pink);
        loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 1.5, 0),
                20, 2.0, 1.5, 2.0, 0);
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1.5, 0),
                40, 3.0, 1.5, 3.0, 0);
        loc.getWorld().spawnParticle(Particle.INSTANT_EFFECT, loc.clone().add(0, 1.5, 0),
                60, 3.0, 1.5, 3.0, 0);
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 2.0f);

        final double healPower = this.skillService.getStatValue(p.getUniqueId(),
                SkillService.Stat.HEALING_POWER);
        for (final Entity e : p.getNearbyEntities(15, 8, 15)) {
            if (e instanceof Player ally) {
                final double maxHp = ally.getMaxHealth();
                final double heal = maxHp * 0.6 + healPower * 2;
                ally.setHealth(Math.min(maxHp, ally.getHealth() + heal));
                final Location al = ally.getLocation().add(0, 1, 0);
                ally.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, al,
                        24, 0.4, 0.6, 0.4, 0.2);
                ParticleShapes.circleH(al.clone().add(0, -0.9, 0), 0.8, 10,
                        Particle.DUST, pink);
            }
        }
        p.setHealth(Math.min(p.getMaxHealth(),
                p.getHealth() + p.getMaxHealth() * 0.6 + healPower * 2));
    }

    /** RLR 共振護盾 — 紫色球殼 + 20s Absorption。 */
    private void resonanceShield(final Player p) {
        final Location loc = p.getLocation().add(0, 1, 0);
        final Particle.DustOptions violet = ParticleShapes.dust(0xA78BFA, 1.8f);

        ParticleShapes.sphereShell(loc, 1.8, 48, Particle.DUST, violet);
        ParticleShapes.sphereShell(loc, 1.6, 32, Particle.END_ROD, null);
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(0, 1, 0),
                50, 1.0, 1.2, 1.0, 1.0);
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.4f);

        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 400, 3, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 400, 1, false, true));
        for (final Entity e : p.getNearbyEntities(15, 8, 15)) {
            if (e instanceof Player ally) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 400, 3, false, true));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 400, 1, false, true));
                final Location al = ally.getLocation().add(0, 1, 0);
                ParticleShapes.sphereShell(al, 1.0, 20, Particle.DUST, violet);
            }
        }
    }

    /** LLR 力量共鳴 — 金色光柱 + 隊友 30s Strength. */
    private void powerResonance(final Player p) {
        final Location loc = p.getLocation();
        final Particle.DustOptions gold = ParticleShapes.dust(0xFBBF24, 1.8f);

        this.shapes.animateLightPillar(loc.clone().add(0, 0.1, 0),
                1.5, 3.0, 18, 16, Particle.DUST, gold);
        loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1.5, 0),
                30, 2.0, 1.0, 2.0, 0.12);
        loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0, 1.5, 0),
                30, 3.0, 1.5, 3.0, 0);
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.6f);

        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 1, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 0, false, true));
        for (final Entity e : p.getNearbyEntities(15, 8, 15)) {
            if (e instanceof Player ally) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 1, false, true));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 0, false, true));
                final Location al = ally.getLocation().add(0, 0.1, 0);
                ParticleShapes.circleH(al, 1.0, 16, Particle.DUST, gold);
            }
        }
    }

    /** RRL 傳送信標 — 藍紫色光柱 + 召集 60 格內隊友。 */
    private void beaconCall(final Player p) {
        final Location loc = p.getLocation();
        final Particle.DustOptions indigo = ParticleShapes.dust(0x6366F1, 1.8f);

        this.shapes.animateLightPillar(loc.clone().add(0, 0.1, 0),
                1.2, 5.0, 20, 14, Particle.DUST, indigo);
        this.shapes.animateLightPillar(loc.clone().add(0, 0.2, 0),
                1.0, 5.0, 20, 10, Particle.END_ROD, null);
        loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc.clone().add(0, 2.5, 0),
                80, 0.8, 2.5, 0.8, 0.2);
        loc.getWorld().spawnParticle(Particle.GLOW, loc.clone().add(0, 2, 0),
                50, 1.2, 1.5, 1.2, 0.05);
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 1.8f);
        loc.getWorld().playSound(loc, Sound.BLOCK_CONDUIT_ACTIVATE, 1.0f, 1.4f);

        int teleported = 0;
        for (final Player ally : p.getWorld().getPlayers()) {
            if (ally.equals(p)) continue;
            if (ally.getLocation().distance(loc) > 60) continue;
            final Location src = ally.getLocation().add(0, 1, 0);
            ParticleShapes.sphereShell(src, 1.2, 24, Particle.PORTAL, null);
            ally.teleportAsync(loc);
            teleported++;
        }
        p.sendMessage(Component.text("  ✓ 召集了 " + teleported + " 名隊友",
                        TextColor.color(0xC4B5FD)).decoration(TextDecoration.ITALIC, false));
    }

    // ═══════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════

    private double magicOrStrengthMultiplier(final Player p, final boolean magic) {
        final double stat = this.skillService.getStatValue(p.getUniqueId(),
                magic ? SkillService.Stat.MAGIC_POWER : SkillService.Stat.STRENGTH);
        return 1.0 + stat / 100.0;
    }

    private static boolean isOre(final Material m) {
        if (m == null) return false;
        final String n = m.name();
        return n.endsWith("_ORE") || n.equals("ANCIENT_DEBRIS") || n.equals("NETHER_QUARTZ_ORE");
    }
}
