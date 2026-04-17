package com.rui.techproject.service.talent;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.service.SkillService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

    // ── 技能狀態追蹤（跨 tick / 跨事件） ──
    /** 止水反射：UUID → 到期時間（System.currentTimeMillis） */
    private final Map<UUID, Long> deadCalmReflectExpiry = new ConcurrentHashMap<>();
    /** 影襲必爆擊旗標 */
    private final Set<UUID> shadowStrikeCrit = ConcurrentHashMap.newKeySet();
/** 礦脈連鎖待觸發：UUID → 到期時間 */
    private final Map<UUID, Long> veinChainPending = new ConcurrentHashMap<>();
    /** 傳送信標標記點：UUID → 標記位置 */
    private final Map<UUID, Location> beaconMarks = new ConcurrentHashMap<>();

    public SpellExecutor(final TechMCPlugin plugin, final SkillService skillService) {
        this.plugin = plugin;
        this.skillService = skillService;
        this.shapes = new ParticleShapes(plugin);
    }

    /**
     * 技能「揮劍命中」統一入口：
     * <ul>
     *   <li>強制玩家播放揮手動畫（手感：像真的在打）</li>
     *   <li>傷害依玩家當下的 GENERIC_ATTACK_DAMAGE 加成（吃武器材質／鋒利）</li>
     *   <li>命中時播放強攻擊音 + 暴擊星星粒子（讓「打中了」的回饋明確）</li>
     *   <li>實際扣血仍走 Bukkit {@code LivingEntity#damage(double, Entity)}，
     *       會正常觸發 EntityDamageByEntityEvent、護甲、抗性、無敵幀</li>
     * </ul>
     * @param p        施放者
     * @param le       目標
     * @param baseDmg  技能基礎傷害（寫死的 base）
     * @param scale    武器屬性影響係數（建議 0.4–0.8；例如 0.5 = 武器加成的一半）
     */
    private void spellHit(final Player p, final LivingEntity le, final double baseDmg, final double scale) {
        if (p == null || le == null || le.isDead()) return;
        // ① 揮劍動畫
        p.swingMainHand();
        // ② 武器加成
        double atk = 0.0;
        try {
            final var attr = p.getAttribute(Attribute.ATTACK_DAMAGE);
            if (attr != null) atk = attr.getValue();
        } catch (final Throwable ignored) {
            // 不同版本屬性名可能不同，取不到就純 base
        }
        final double finalDmg = baseDmg + atk * scale;
        // ③ 命中回饋：暴擊星 + 強擊音
        final Location lePos = le.getLocation().add(0, le.getHeight() * 0.6, 0);
        lePos.getWorld().spawnParticle(Particle.CRIT, lePos, 12, 0.3, 0.3, 0.3, 0.25);
        lePos.getWorld().playSound(lePos, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, 1.0f);
        // ④ 正規傷害
        le.damage(finalDmg, p);
    }

    /** 預設 scale=0.5 的便捷版本。 */
    private void spellHit(final Player p, final LivingEntity le, final double baseDmg) {
        this.spellHit(p, le, baseDmg, 0.5);
    }

    /**
     * 模擬 BlockBreakEvent 檢查領地/保護插件是否允許玩家修改該方塊。
     * 共用 TechListener.simulatingBreakThreads 重入保護，避免觸發 onBlockBreak 處理器。
     */
    private boolean canModifyBlock(final Player player, final Block block) {
        final long threadId = Thread.currentThread().getId();
        com.rui.techproject.listener.TechListener.simulatingBreakThreads.add(threadId);
        try {
            final org.bukkit.event.block.BlockBreakEvent simulated =
                    new org.bukkit.event.block.BlockBreakEvent(block, player);
            org.bukkit.Bukkit.getPluginManager().callEvent(simulated);
            return !simulated.isCancelled();
        } finally {
            com.rui.techproject.listener.TechListener.simulatingBreakThreads.remove(threadId);
        }
    }

    // ── 公開方法：供 TechListener 查詢 / 消耗技能狀態 ──

    /** 止水反射是否生效中。 */
    public boolean isDeadCalmActive(final UUID uuid) {
        final Long expiry = this.deadCalmReflectExpiry.get(uuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            this.deadCalmReflectExpiry.remove(uuid);
            return false;
        }
        return true;
    }

    /** 消耗影襲必爆擊（一次性）。 */
    public boolean consumeShadowCrit(final UUID uuid) {
        return this.shadowStrikeCrit.remove(uuid);
    }

/** 消耗礦脈連鎖待觸發。 */
    public boolean consumeVeinChain(final UUID uuid) {
        final Long expiry = this.veinChainPending.remove(uuid);
        return expiry != null && System.currentTimeMillis() <= expiry;
    }

    /** 清理離線玩家的所有狀態。 */
    public void cleanupPlayer(final UUID uuid) {
        this.deadCalmReflectExpiry.remove(uuid);
        this.shadowStrikeCrit.remove(uuid);
        this.veinChainPending.remove(uuid);
        this.beaconMarks.remove(uuid);
    }

    /** 入口：執行指定法術。 */
    public void execute(final Player caster, final SpellId spell) {
        if (caster == null || spell == null) return;

        // 新手引導：施放天賦技能
        if (this.plugin.getTutorialChainService() != null) {
            this.plugin.getTutorialChainService().onSpellCast(caster);
        }

        // 共通施法圓（cast circle）+ 啟動音 + 訊息
        this.playCastCircle(caster);
        caster.playSound(caster.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT,
                SoundCategory.PLAYERS, 0.8f, 1.4f);
        caster.sendMessage(Component.text("✦ ", TextColor.color(0xC4B5FD))
                .append(Component.text(spell.displayName, TextColor.color(0xEC4899), TextDecoration.BOLD))
                .append(Component.text(" ▸ " + spell.description, TextColor.color(0xCBD5E1))
                        .decoration(TextDecoration.ITALIC, false))
                .append(Component.text("  [" + spell.weaponHint + "]", TextColor.color(0x94A3B8))
                        .decoration(TextDecoration.ITALIC, false)));

        switch (spell) {
            case COMBAT_LRL -> this.waterSurfaceSlash(caster);
            case COMBAT_RLR -> this.flowingDance(caster);
            case COMBAT_LLR -> this.whirlpool(caster);
            case COMBAT_RRL -> this.seiseiRuten(caster);
            case EXPLORATION_LRL -> this.windWaltz(caster);
            case EXPLORATION_RLR -> this.grapplingHook(caster);
            case EXPLORATION_LLR -> this.stormArrows(caster);
            case EXPLORATION_RRL -> this.shadowStrike(caster);
            case GATHERING_LRL -> this.veinChain(caster);
            case GATHERING_RLR -> this.earthPulse(caster);
            case GATHERING_LLR -> this.earthShatter(caster);
            case GATHERING_RRL -> this.earthResonance(caster);
            case ENGINEERING_LRL -> this.turretDeploy(caster);
            case ENGINEERING_RLR -> this.overloadCore(caster);
            case ENGINEERING_LLR -> this.railgun(caster);
            case ENGINEERING_RRL -> this.empBurst(caster);
            case RESEARCH_LRL -> this.meteor(caster);
            case RESEARCH_RLR -> this.timeFreeze(caster);
            case RESEARCH_LLR -> this.elementalBeam(caster);
            case RESEARCH_RRL -> this.voidCollapse(caster);
            case RESONANCE_LRL -> this.healingWave(caster);
            case RESONANCE_RLR -> this.resonanceShield(caster);
            case RESONANCE_LLR -> this.starburst(caster);
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

    // ── 水系色譜 ──
    private static final Particle.DustOptions WATER_BLUE   = ParticleShapes.dust(0x22A7F0, 1.6f);
    private static final Particle.DustOptions WATER_LIGHT  = ParticleShapes.dust(0x7DD3FC, 1.3f);
    private static final Particle.DustOptions WATER_WHITE  = ParticleShapes.dust(0xD6F0FF, 1.0f);
    private static final Particle.DustOptions WATER_DEEP   = ParticleShapes.dust(0x1565C0, 1.8f);
    private static final Particle.DustOptions WATER_CYAN   = ParticleShapes.dust(0x00EAFF, 1.4f);
    private static final Particle.DustOptions WATER_FOAM   = ParticleShapes.dust(0xE0F7FA, 0.8f);
    // ── 雷系色譜（霹靂一閃）──
    private static final Particle.DustOptions THUNDER_GOLD     = ParticleShapes.dust(0xFFD700, 1.8f);
    private static final Particle.DustOptions THUNDER_YELLOW   = ParticleShapes.dust(0xFFF176, 1.4f);
    private static final Particle.DustOptions THUNDER_WHITE    = ParticleShapes.dust(0xFFFDE7, 1.0f);
    private static final Particle.DustOptions THUNDER_BRIGHT   = ParticleShapes.dust(0xFFEB3B, 1.6f);
    private static final Particle.DustOptions THUNDER_BLUE     = ParticleShapes.dust(0x42A5F5, 1.5f);
    private static final Particle.DustOptions THUNDER_INDIGO   = ParticleShapes.dust(0x7C4DFF, 1.2f);
    private static final Particle.DustOptions THUNDER_PURPLE   = ParticleShapes.dust(0xAB47BC, 1.0f);
    private static final Particle.DustOptions THUNDER_HOTWHITE = ParticleShapes.dust(0xFFFFFF, 2.0f);

    /** 角色位移微調：以 yaw 為基準向前(+)/後(-)、右(+)/左(-)、上推動。 */
    private void nudge(final Player p, final double forward, final double right, final double up) {
        final double yawRad = Math.toRadians(p.getLocation().getYaw());
        final double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);
        final double rx = Math.cos(yawRad),  rz = Math.sin(yawRad);
        p.setVelocity(new Vector(fx * forward + rx * right, up, fz * forward + rz * right));
    }

    // ────────────────────────────────────────────────────────
    //  LRL 碎浪斬 — 三段漸進式弧斬 + 身體搖擺
    // ────────────────────────────────────────────────────────

    private void waterSurfaceSlash(final Player p) {
        final double bonus = this.magicOrStrengthMultiplier(p, false);
        final double baseDmg = 14.0 * bonus;

        // ── 第一斬（t=0-3）── 身體右傾 + 90° 弧線掃出
        this.nudge(p, 0.3, 0.18, 0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.5f, 1.8f);
        this.shapes.animateArcSweep(p.getLocation(), p.getLocation().getYaw(),
                3.5, 1.0, 90, 0.1, 4, Particle.DUST, WATER_LIGHT, WATER_WHITE);
        this.shapes.animateArcSweep(p.getLocation(), p.getLocation().getYaw(),
                4.0, 1.0, 90, 0.12, 4, Particle.DUST, WATER_CYAN, WATER_FOAM);

        // ── 第二斬（t=5-9）── 身體左傾 + 140° 弧線反向掃
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            this.nudge(p, 0.5, -0.22, 0);
            final Location loc2 = p.getLocation();
            loc2.getWorld().playSound(loc2, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 0.9f);
            loc2.getWorld().playSound(loc2, Sound.ITEM_TRIDENT_THROW, 0.4f, 1.6f);
            // 3 層弧線逐幀掃
            this.shapes.animateArcSweep(loc2, loc2.getYaw(), 4.5, 0.8, 140, 0.1,
                    5, Particle.DUST, WATER_DEEP, WATER_BLUE);
            this.shapes.animateArcSweep(loc2, loc2.getYaw(), 5.0, 1.0, 140, 0.1,
                    5, Particle.DUST, WATER_BLUE, WATER_CYAN);
            this.shapes.animateArcSweep(loc2, loc2.getYaw(), 5.5, 1.2, 140, 0.12,
                    5, Particle.DUST, WATER_CYAN, WATER_WHITE);
            // 水滴跟隨
            for (int t = 0; t < 5; t++) {
                this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                    if (!p.isOnline()) return;
                    p.getWorld().spawnParticle(Particle.DRIPPING_WATER,
                            p.getLocation().add(0, 1.5, 0), 8, 3.0, 0.3, 3.0, 0);
                }, t);
            }
        }, 5L);

        // ── 第三斬（t=12-20）── 前衝 + 180° 全力弧斬
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            this.nudge(p, 0.8, 0, 0.1);
            final Location loc3 = p.getLocation();
            final float yaw3 = loc3.getYaw();
            loc3.getWorld().playSound(loc3, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.6f, 1.0f);
            loc3.getWorld().playSound(loc3, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.3f, 1.8f);
            loc3.getWorld().playSound(loc3, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 0.6f);
            // 6 層逐幀弧斬（每層不同半徑/高度/顏色，8 tick 掃完）
            this.shapes.animateArcSweep(loc3, yaw3, 4.5, 0.7, 180, 0.1,
                    8, Particle.DUST, WATER_DEEP, WATER_BLUE);
            this.shapes.animateArcSweep(loc3, yaw3, 5.0, 0.85, 180, 0.1,
                    8, Particle.DUST, WATER_BLUE, WATER_CYAN);
            this.shapes.animateArcSweep(loc3, yaw3, 5.5, 1.0, 180, 0.08,
                    8, Particle.DUST, WATER_CYAN, WATER_LIGHT);
            this.shapes.animateArcSweep(loc3, yaw3, 6.0, 1.0, 180, 0.08,
                    8, Particle.DUST, WATER_LIGHT, WATER_WHITE);
            this.shapes.animateArcSweep(loc3, yaw3, 6.3, 1.1, 180, 0.1,
                    8, Particle.DUST, WATER_WHITE, WATER_FOAM);
            this.shapes.animateArcSweep(loc3, yaw3, 6.5, 1.2, 180, 0.12,
                    8, Particle.DUST, WATER_FOAM, WATER_WHITE);
            // 雲霧掃描跟隨弧斬（稍微延後 2t）
            this.shapes.animateCloudSweep(loc3, yaw3, 6.3, 1.0, 180, 10, 6);
            // 逐幀水花飛濺
            for (int t = 0; t < 8; t++) {
                final int tt = t;
                this.plugin.getSafeScheduler().runRegionDelayed(loc3, task -> {
                    final double prog = (double) (tt + 1) / 8;
                    loc3.getWorld().spawnParticle(Particle.SPLASH,
                            loc3.clone().add(0, 0.3, 0), (int)(10 + 40 * prog),
                            4.0 * prog, 0.2, 4.0 * prog, 0.05 + 0.1 * prog);
                }, t);
            }
        }, 12L);

        // ── 傷害判定（t=16）── 弧斬完成時
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            final Location dmgLoc = p.getLocation();
            final Vector facing = dmgLoc.getDirection().setY(0).normalize();
            for (final Entity e : p.getNearbyEntities(6, 3, 6)) {
                if (e instanceof LivingEntity le && !e.equals(p)) {
                    final Vector toEntity = le.getLocation().toVector()
                            .subtract(dmgLoc.toVector()).setY(0).normalize();
                    if (facing.dot(toEntity) > 0.0) {
                        this.spellHit(p, le, baseDmg);
                        le.setVelocity(toEntity.multiply(0.6).setY(0.4));
                        le.getWorld().spawnParticle(Particle.SPLASH,
                                le.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.1);
                    }
                }
            }
        }, 16L);

        // ── 水面餘韻（t=24）── 向後微退 + 漣漪擴散
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            this.nudge(p, -0.15, 0, 0);
            final Location end = p.getLocation();
            this.shapes.animateDenseRipples(end.clone().add(0, 0.05, 0),
                    5.0, 3, 6, 0.15, Particle.DUST, WATER_CYAN);
            end.getWorld().spawnParticle(Particle.DRIPPING_WATER,
                    end.clone().add(0, 2, 0), 25, 3.0, 0.5, 3.0, 0);
            end.getWorld().playSound(end, Sound.ENTITY_GENERIC_SPLASH, 0.4f, 1.4f);
        }, 24L);
    }

    // ────────────────────────────────────────────────────────
    //  RLR 霹靂一閃 — 蓄力拔刀 → 雷速突進 → 落雷爆發
    // ────────────────────────────────────────────────────────

    private void flowingDance(final Player p) {
        final double bonus = this.magicOrStrengthMultiplier(p, false);
        final double baseDmg = 18.0 * bonus;

        // ══ 蓄力階段（t=0-12）══ 雷電逐漸匯聚，顏色從靛藍→電藍→金→白熱
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 18, 4, false, false));
        this.nudge(p, -0.2, 0, 0);  // 微後退（蓄力姿勢）
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,
                SoundCategory.PLAYERS, 0.8f, 1.6f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                SoundCategory.PLAYERS, 0.25f, 2.0f);

        for (int t = 0; t <= 12; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                if (!p.isOnline()) return;
                final Location cl = p.getLocation();
                final float cy = cl.getYaw();
                final double prog = (double) tick / 12.0;

                // 顏色漸變
                final Particle.DustOptions ringColor = prog < 0.3 ? THUNDER_INDIGO
                        : prog < 0.55 ? THUNDER_BLUE : prog < 0.8 ? THUNDER_GOLD : THUNDER_HOTWHITE;
                final Particle.DustOptions outerColor = prog < 0.4 ? THUNDER_BLUE
                        : prog < 0.7 ? THUNDER_YELLOW : THUNDER_BRIGHT;

                // 地面圓陣逐漸顯現（60°→360°）
                final double circleArc = 60 + 300 * prog;
                ParticleShapes.denseArc(cl, cy, 1.8, 0.05, circleArc, 0.08,
                        Particle.DUST, ringColor);
                ParticleShapes.denseArc(cl, cy, 2.3, 0.05, circleArc, 0.1,
                        Particle.DUST, outerColor);

                // 6 臂螺旋收攏（外→內，顏色漸變）
                final double radius = 3.2 * (1.0 - prog) + 0.25;
                final double spin = prog * Math.PI * 8;
                final double yOff = 0.15 + prog * 1.8;
                for (int arm = 0; arm < 6; arm++) {
                    final double angle = 2 * Math.PI * arm / 6 + spin;
                    final Location sp = cl.clone().add(
                            Math.cos(angle) * radius, yOff, Math.sin(angle) * radius);
                    cl.getWorld().spawnParticle(Particle.DUST, sp, 1, 0, 0, 0, 0, ringColor);
                    // 尾巴粒子（較暗）
                    if (prog > 0.3) {
                        final double trailAngle = angle - 0.4;
                        final double trailR = radius * 1.15;
                        cl.getWorld().spawnParticle(Particle.DUST,
                                cl.clone().add(Math.cos(trailAngle) * trailR, yOff * 0.8,
                                        Math.sin(trailAngle) * trailR),
                                1, 0, 0, 0, 0, THUNDER_PURPLE);
                    }
                }

                // 電火花密度隨蓄力提升
                final int sparkCount = 4 + (int) (prog * 40);
                cl.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                        cl.clone().add(0, 1, 0), sparkCount,
                        0.5 + prog * 0.9, 0.6, 0.5 + prog * 0.9,
                        0.04 + prog * 0.18);

                // END_ROD 光芒（後半段）
                if (prog > 0.5) {
                    cl.getWorld().spawnParticle(Particle.END_ROD,
                            cl.clone().add(0, 1.2, 0), (int) (5 + prog * 20),
                            0.3, 0.5, 0.3, 0.02 + prog * 0.06);
                }

                // 身體搖擺
                if (tick == 4) nudge(p, 0, 0.16, 0);     // 右傾（握刀手）
                if (tick == 8) nudge(p, 0, -0.16, 0);    // 左傾（拔刀預備）

                // 音效漸進
                if (tick == 3)
                    cl.getWorld().playSound(cl, Sound.ENTITY_WARDEN_HEARTBEAT,
                            SoundCategory.PLAYERS, 0.15f, 1.4f);
                if (tick == 6)
                    cl.getWorld().playSound(cl, Sound.BLOCK_ANVIL_LAND,
                            SoundCategory.PLAYERS, 0.075f, 0.6f);
                if (tick == 9) {
                    cl.getWorld().playSound(cl, Sound.ITEM_TRIDENT_THUNDER,
                            SoundCategory.PLAYERS, 0.175f, 1.8f);
                    cl.getWorld().playSound(cl, Sound.ENTITY_WARDEN_HEARTBEAT,
                            SoundCategory.PLAYERS, 0.2f, 1.6f);
                }
                if (tick == 11) {
                    // 蓄力完成閃光
                    cl.getWorld().spawnParticle(Particle.END_ROD,
                            cl.clone().add(0, 1, 0), 60, 0.15, 0.4, 0.15, 0.25);
                    cl.getWorld().playSound(cl, Sound.BLOCK_END_PORTAL_FRAME_FILL,
                            SoundCategory.PLAYERS, 0.3f, 1.2f);
                }
            }, tick);
        }

        // ══ 拔刀斬（t=14）══ 瞬間傳送（射線偵測牆壁）+ 雷電軌跡爬行
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            final Location startPos = p.getLocation().clone();
            final float yaw = startPos.getYaw();
            final Vector dashDir = startPos.getDirection().setY(0).normalize();

            // 射線偵測：往前掃 22 格，撞到方塊就停（保留 1 格安全距離）
            final Location eyeLevel = startPos.clone().add(0, 0.5, 0);
            final RayTraceResult ray = eyeLevel.getWorld().rayTraceBlocks(
                    eyeLevel, dashDir, 22,
                    org.bukkit.FluidCollisionMode.NEVER, true);
            final double dashDist;
            if (ray != null && ray.getHitBlock() != null) {
                dashDist = Math.max(2.0,
                        ray.getHitPosition().distance(startPos.toVector()) - 1.5);
            } else {
                dashDist = 22.0;
            }
            final Location endPos = startPos.clone().add(dashDir.clone().multiply(dashDist));

            // 瞬間傳送
            p.teleportAsync(endPos);

            // 起點爆閃
            startPos.getWorld().spawnParticle(Particle.END_ROD,
                    startPos.clone().add(0, 1, 0), 80, 0.2, 0.5, 0.2, 0.3);
            startPos.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    startPos.clone().add(0, 1, 0), 100, 0.4, 0.7, 0.4, 0.5);
            // 起點垂直弧（拔刀殘影）
            ParticleShapes.denseArcV(startPos.clone().add(0, 0.2, 0), yaw,
                    1.5, 0.06, Particle.DUST, THUNDER_HOTWHITE);
            ParticleShapes.denseArcV(startPos.clone().add(0, 0.2, 0), yaw,
                    1.8, 0.08, Particle.DUST, THUNDER_GOLD);

            // 音效
            startPos.getWorld().playSound(startPos, Sound.ENTITY_LIGHTNING_BOLT_IMPACT,
                    SoundCategory.PLAYERS, 0.75f, 1.2f);
            startPos.getWorld().playSound(startPos, Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                    SoundCategory.PLAYERS, 0.6f, 0.5f);
            startPos.getWorld().playSound(startPos, Sound.ITEM_TRIDENT_THUNDER,
                    SoundCategory.PLAYERS, 0.5f, 1.5f);
            startPos.getWorld().playSound(startPos, Sound.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.PLAYERS, 0.2f, 1.8f);

            // ── 3 條閃電逐幀爬行（8 tick 從起點蔓延到終點）──
            final Location ts = startPos.clone().add(0, 1.0, 0);
            final Location te = endPos.clone().add(0, 1.0, 0);
            // 中央白熱主線
            this.shapes.animateLightningCrawl(ts, te, 8, 0.06,
                    Particle.DUST, THUNDER_HOTWHITE, THUNDER_GOLD);
            // 上方金色線
            this.shapes.animateLightningCrawl(
                    ts.clone().add(0, 0.45, 0), te.clone().add(0, 0.45, 0),
                    8, 0.07, Particle.DUST, THUNDER_GOLD, THUNDER_YELLOW);
            // 下方電藍線
            this.shapes.animateLightningCrawl(
                    ts.clone().add(0, -0.45, 0), te.clone().add(0, -0.45, 0),
                    8, 0.07, Particle.DUST, THUNDER_BLUE, THUNDER_BRIGHT);

            // ── 沿路逐幀電火花擴散 ──
            for (int s = 0; s < 8; s++) {
                final int sparkTick = s;
                this.plugin.getSafeScheduler().runRegionDelayed(startPos, task -> {
                    final double prog = (double) (sparkTick + 1) / 8;
                    for (int d = 0; d < (int) (dashDist * prog); d += 2) {
                        final Location sparkLoc = startPos.clone()
                                .add(dashDir.clone().multiply(d)).add(0, 1, 0);
                        sparkLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                                sparkLoc, 5, 0.15, 0.35, 0.15, 0.06);
                    }
                    // 紫色殘電散落（已過區段）
                    if (sparkTick >= 3) {
                        for (int d = 0; d < (int) (dashDist * (prog - 0.3)); d += 3) {
                            final Location rpLoc = startPos.clone()
                                    .add(dashDir.clone().multiply(d)).add(0, 0.8, 0);
                            rpLoc.getWorld().spawnParticle(Particle.DUST,
                                    rpLoc, 2, 0.3, 0.4, 0.3, 0, THUNDER_PURPLE);
                        }
                    }
                }, s);
            }

            // ── 4 個殘影依次浮現 + 逐漸淡出（等比分佈）──
            final double[] imgDist = {
                    dashDist * 0.23, dashDist * 0.48, dashDist * 0.73, dashDist * 0.98};
            for (int img = 0; img < 4; img++) {
                final int imgIdx = img;
                final Location imgPos = startPos.clone()
                        .add(dashDir.clone().multiply(imgDist[img]));
                // 殘影浮現
                this.plugin.getSafeScheduler().runRegionDelayed(startPos, task -> {
                    ParticleShapes.denseArcV(imgPos.clone().add(0, 0.2, 0), yaw,
                            1.4, 0.06, Particle.DUST, THUNDER_GOLD);
                    ParticleShapes.denseArcV(imgPos.clone().add(0, 0.2, 0), yaw,
                            1.7, 0.08, Particle.DUST, THUNDER_YELLOW);
                    ParticleShapes.denseArcV(imgPos.clone().add(0, 0.2, 0), yaw,
                            2.0, 0.1, Particle.DUST, THUNDER_BLUE);
                    imgPos.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                            imgPos.clone().add(0, 1, 0), 25, 0.3, 0.7, 0.3, 0.15);
                    imgPos.getWorld().spawnParticle(Particle.END_ROD,
                            imgPos.clone().add(0, 1.2, 0), 10, 0.2, 0.4, 0.2, 0.04);
                }, 2 + imgIdx * 2);
                // 殘影縮小淡出
                this.plugin.getSafeScheduler().runRegionDelayed(startPos, task -> {
                    ParticleShapes.denseArcV(imgPos.clone().add(0, 0.2, 0), yaw,
                            1.0, 0.1, Particle.DUST, THUNDER_PURPLE);
                    ParticleShapes.denseArcV(imgPos.clone().add(0, 0.2, 0), yaw,
                            0.6, 0.12, Particle.DUST, THUNDER_INDIGO);
                    imgPos.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                            imgPos.clone().add(0, 1, 0), 8, 0.5, 0.8, 0.5, 0.04);
                }, 5 + imgIdx * 2);
            }

            // ── 沿路傷害 ──
            final Set<LivingEntity> damaged = new HashSet<>();
            for (int d = 0; d <= (int) dashDist; d++) {
                final Location checkPos = startPos.clone()
                        .add(dashDir.clone().multiply(d));
                for (final Entity e : checkPos.getWorld()
                        .getNearbyEntities(checkPos, 2.5, 2, 2.5)) {
                    if (e instanceof LivingEntity target && !e.equals(p)
                            && damaged.add(target)) {
                        this.spellHit(p, target, baseDmg);
                        target.setVelocity(dashDir.clone().multiply(0.8).setY(0.3));
                        target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                                target.getLocation().add(0, 1, 0),
                                30, 0.3, 0.5, 0.3, 0.25);
                    }
                }
            }
        }, 14L);

        // ══ 落雷爆發（t=24）══ 終點衝擊波 + 閃電柱
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            final Location impactPos = p.getLocation();
            final float impYaw = impactPos.getYaw();
            this.nudge(p, -0.2, 0, 0);  // 後座力

            // 多色衝擊波（由內到外，依次擴散）
            this.shapes.animateShockRing(impactPos.clone().add(0, 0.1, 0),
                    0.5, 6.0, 8, 50, Particle.DUST, THUNDER_HOTWHITE);
            this.shapes.animateShockRing(impactPos.clone().add(0, 0.15, 0),
                    1.0, 7.5, 10, 40, Particle.DUST, THUNDER_GOLD);
            this.shapes.animateShockRing(impactPos.clone().add(0, 0.2, 0),
                    1.5, 9.0, 12, 35, Particle.DUST, THUNDER_BLUE);

            // 4 根閃電柱依次升起
            for (int pillar = 0; pillar < 4; pillar++) {
                final double angle = Math.PI * 2 * pillar / 4 + Math.toRadians(impYaw);
                final Location pillarBase = impactPos.clone().add(
                        Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
                final Particle.DustOptions pillarColor = pillar % 2 == 0
                        ? THUNDER_GOLD : THUNDER_BLUE;
                this.plugin.getSafeScheduler().runRegionDelayed(impactPos, task -> {
                    this.shapes.animateLightPillar(pillarBase, 0.3, 6.0, 8, 20,
                            Particle.DUST, pillarColor);
                }, pillar * 2);
            }

            // 大量粒子爆發
            impactPos.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    impactPos.clone().add(0, 1.5, 0), 150, 2.5, 2.0, 2.5, 0.5);
            impactPos.getWorld().spawnParticle(Particle.END_ROD,
                    impactPos.clone().add(0, 1, 0), 60, 2.0, 1.5, 2.0, 0.15);
            impactPos.getWorld().spawnParticle(Particle.FIREWORK,
                    impactPos.clone().add(0, 2, 0), 35, 1.5, 1.5, 1.5, 0.25);

            // 音效
            impactPos.getWorld().playSound(impactPos, Sound.ENTITY_LIGHTNING_BOLT_THUNDER,
                    SoundCategory.PLAYERS, 0.5f, 1.5f);
            impactPos.getWorld().playSound(impactPos, Sound.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.PLAYERS, 0.25f, 1.2f);
        }, 24L);

        // ══ 殘雷餘韻（t=32）══ 紫色殘電散落
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            final Location rem = p.getLocation();
            ParticleShapes.denseArc(rem, rem.getYaw(), 3.0, 0.1, 360, 0.12,
                    Particle.DUST, THUNDER_PURPLE);
            ParticleShapes.denseArc(rem, rem.getYaw(), 4.0, 0.15, 360, 0.15,
                    Particle.DUST, THUNDER_INDIGO);
            rem.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    rem.clone().add(0, 1, 0), 25, 2.0, 1.0, 2.0, 0.04);
        }, 32L);
    }

    // ────────────────────────────────────────────────────────
    //  LLR 漩淵 — 水漩渦持續吸引 + 身體搖擺
    // ────────────────────────────────────────────────────────

    private void whirlpool(final Player p) {
        final Location center = p.getLocation();
        final double bonus = this.magicOrStrengthMultiplier(p, false);
        final double tickDmg = 3.0 * bonus;

        center.getWorld().playSound(center, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.3f, 1.8f);
        center.getWorld().playSound(center, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.5f, 0.8f);
        center.getWorld().playSound(center, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.4f, 0.6f);

        // 高密度龍捲動畫 — 外層（4 臂，60 tick）
        this.shapes.animateDenseTornado(center, 3.0, 5.0, 60, 4, 0.15,
                Particle.DUST, WATER_BLUE);
        // 內層（3 臂）
        this.shapes.animateDenseTornado(center, 1.5, 4.0, 60, 3, 0.2,
                Particle.DUST, WATER_WHITE);

        // 底部持續水花 + cloud + 身體左右搖擺
        for (int t = 0; t < 60; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                if (!p.isOnline()) return;
                center.getWorld().spawnParticle(Particle.SPLASH,
                        center.clone().add(0, 0.3, 0), 10, 2.5, 0.2, 2.5, 0.05);
                center.getWorld().spawnParticle(Particle.CLOUD,
                        center.clone().add(0, 0.5, 0), 4, 1.5, 0.3, 1.5, 0.02);
                center.getWorld().spawnParticle(Particle.BUBBLE_POP,
                        center.clone().add(0, 2.5, 0), 6, 1.0, 1.5, 1.0, 0.02);
                // 每 8 tick 身體微晃一次
                if (tick % 8 == 0) {
                    final double sway = (tick / 8 % 2 == 0) ? 0.1 : -0.1;
                    nudge(p, 0, sway, 0);
                }
            }, t);
        }

        // 每 10 tick 傷害 + 吸引
        for (int pulse = 0; pulse < 6; pulse++) {
            this.plugin.getSafeScheduler().runRegionDelayed(center, task -> {
                for (final Entity e : center.getWorld().getNearbyEntities(center, 5, 4, 5)) {
                    if (e instanceof LivingEntity le && !(e instanceof Player pl && pl.equals(p))) {
                        final Vector pull = center.toVector().subtract(le.getLocation().toVector())
                                .setY(0.3).normalize().multiply(0.5);
                        le.setVelocity(pull);
                        this.spellHit(p, le, tickDmg, 0.25); // DoT：武器加成減半
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
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_SPLASH, 0.6f, 0.6f);
        }, 60L);
    }

    // ────────────────────────────────────────────────────────
    //  RRL 生生流轉 — 具象水龍沿視線蜿蜒疾馳 40 格
    // ────────────────────────────────────────────────────────

    private void seiseiRuten(final Player p) {
        final double bonus = this.magicOrStrengthMultiplier(p, false);
        final double slashDmg = 9.0 * bonus;
        final double diveDmg  = 28.0 * bonus;

        final Location origin = p.getEyeLocation();
        final java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();

        // 前搖蓄力（8 tick 聚氣）
        origin.getWorld().playSound(origin, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.5f, 0.7f);
        origin.getWorld().playSound(origin, Sound.ENTITY_WITHER_AMBIENT, 0.4f, 1.6f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 140, 0, false, false));

        final Set<UUID> slashHit = ConcurrentHashMap.newKeySet();
        final Set<UUID> diveHit  = ConcurrentHashMap.newKeySet();

        // 蓄力圓 + 往中心聚合的粒子（0~8 tick）
        for (int t = 0; t < 8; t++) {
            final int tt = t;
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                if (!p.isOnline()) return;
                final Location pl = p.getLocation();
                final Location body = pl.clone().add(0, 1.0, 0);
                // 從 3 格外往玩家聚合
                final double shrink = 3.0 - tt * 0.3;
                for (int k = 0; k < 24; k++) {
                    final double ang = 2 * Math.PI * k / 24 + tt * 0.4;
                    final Location gp = body.clone()
                            .add(Math.cos(ang) * shrink, (rng.nextDouble() - 0.5) * 1.0, Math.sin(ang) * shrink);
                    final Vector inward = body.toVector().subtract(gp.toVector()).normalize().multiply(0.25);
                    gp.getWorld().spawnParticle(Particle.DUST, gp, 0,
                            inward.getX(), inward.getY(), inward.getZ(), 1, WATER_CYAN);
                    gp.getWorld().spawnParticle(Particle.DUST, gp, 0,
                            inward.getX(), inward.getY(), inward.getZ(), 1, WATER_FOAM);
                }
                // 腳下發光圓
                ParticleShapes.circleH(pl.clone().add(0, 0.05, 0),
                        2.2 - tt * 0.1, 32, Particle.DUST, WATER_LIGHT);
                if (tt == 0) {
                    pl.getWorld().playSound(pl, Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 0.6f);
                }
            }, t);
        }

        // ═════════ Phase 1：搖擺衝刺 + 密集水流 + S 形水刃（0~50 tick / 2.5s）═════════
        final int dashTicks = 50;
        // 7 道 S 形水刃，時間點不規則
        final int[] slashAt = {
            3 + rng.nextInt(3),
            10 + rng.nextInt(4),
            17 + rng.nextInt(4),
            24 + rng.nextInt(4),
            31 + rng.nextInt(4),
            38 + rng.nextInt(4),
            45 + rng.nextInt(3)
        };

        final int CHARGE = 8;
        for (int t = 0; t < dashTicks; t++) {
            final int tt = t;
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                if (!p.isOnline()) return;
                final Location pl = p.getLocation();
                final Vector fwd = pl.getDirection().clone().normalize();
                final Vector sd = new Vector(-fwd.getZ(), 0, fwd.getX());
                if (sd.lengthSquared() < 1e-4) sd.setX(1);
                sd.normalize();

                // ── 左右搖擺衝刺：sideways sway + forward push ──
                final double sway = Math.sin(tt * 0.35) * 0.45; // 左右 ±0.45
                final Vector vel = fwd.clone().multiply(0.85)
                        .add(sd.clone().multiply(sway))
                        .setY(Math.max(p.getVelocity().getY(), -0.08));
                p.setVelocity(vel);

                // ── 密集水流（高密度 + 渦旋 + 隨機飄散）──
                final Location body = pl.clone().add(0, 1.0, 0);

                // (1) 散落霧氣：每 tick 25~40 顆隨機位置
                final int n = 25 + rng.nextInt(16);
                for (int k = 0; k < n; k++) {
                    final double rx = (rng.nextDouble() - 0.5) * 2.2;
                    final double ry = (rng.nextDouble() - 0.5) * 2.6;
                    final double rz = (rng.nextDouble() - 0.5) * 2.2;
                    final Location pt = body.clone().add(rx, ry, rz);
                    final double dvx = (rng.nextDouble() - 0.5) * 0.12;
                    final double dvy = rng.nextDouble() * 0.06;
                    final double dvz = (rng.nextDouble() - 0.5) * 0.12;
                    final Particle.DustOptions hue = switch (rng.nextInt(4)) {
                        case 0 -> WATER_DEEP;
                        case 1 -> WATER_BLUE;
                        case 2 -> WATER_CYAN;
                        default -> WATER_FOAM;
                    };
                    pt.getWorld().spawnParticle(Particle.DUST, pt, 0, dvx, dvy, dvz, 1, hue);
                }

                // (2) 雙螺旋渦旋繞身（8 層 × 2 條）
                final double spin = tt * 0.6;
                for (int helix = 0; helix < 2; helix++) {
                    final double offset = helix * Math.PI;
                    for (int hy = 0; hy < 8; hy++) {
                        final double ang = spin + offset + hy * 0.85;
                        final double rr = 1.3 + Math.sin(tt * 0.2 + hy) * 0.25;
                        final double yy = -0.3 + hy * 0.35;
                        final Location hp = body.clone()
                                .add(Math.cos(ang) * rr, yy, Math.sin(ang) * rr);
                        hp.getWorld().spawnParticle(Particle.DUST, hp, 0, 0, 0, 0, 1, WATER_BLUE);
                        hp.getWorld().spawnParticle(Particle.DUST, hp, 0, 0, 0, 0, 1, WATER_FOAM);
                    }
                }

                // (3) 三層離散點環：腰/頭/腳三層水圈，每圈多顆可辨識的點
                final double[] ringYs    = {0.3, 1.0, 1.8};
                final double[] ringRs    = {1.6, 1.8, 1.5};
                final int[]    ringDots  = { 20,  24,  18};
                final double[] ringSpin  = {spin, -spin * 0.8, spin * 1.2};
                for (int r = 0; r < 3; r++) {
                    final int dots = ringDots[r];
                    final double rad = ringRs[r];
                    final double sp = ringSpin[r];
                    for (int k = 0; k < dots; k++) {
                        final double ang = 2 * Math.PI * k / dots + sp;
                        final double px = Math.cos(ang) * rad;
                        final double pz = Math.sin(ang) * rad;
                        final Location dot = pl.clone().add(px, ringYs[r], pz);
                        dot.getWorld().spawnParticle(Particle.DUST, dot, 0, 0, 0, 0, 1, WATER_BLUE);
                        dot.getWorld().spawnParticle(Particle.DUST, dot, 0, 0, 0, 0, 1,
                                (k % 2 == 0) ? WATER_CYAN : WATER_FOAM);
                    }
                }

                // (4) 垂直立環（XY + ZY 十字，用來構成球感）
                for (int k = 0; k < 16; k++) {
                    final double ang = 2 * Math.PI * k / 16 + spin * 0.5;
                    final double cy = Math.sin(ang) * 1.2 + 1.0;
                    final double cr = Math.cos(ang) * 1.3;
                    final Location v1 = pl.clone().add(cr, cy, 0);
                    final Location v2 = pl.clone().add(0, cy, cr);
                    v1.getWorld().spawnParticle(Particle.DUST, v1, 0, 0, 0, 0, 1, WATER_DEEP);
                    v2.getWorld().spawnParticle(Particle.DUST, v2, 0, 0, 0, 0, 1, WATER_DEEP);
                }

                // (5) 聚合脈動：每 2 tick 一次，從外往內捲
                if (tt % 2 == 0) {
                    for (int k = 0; k < 22; k++) {
                        final double ang = 2 * Math.PI * k / 22 + rng.nextDouble() * 0.3;
                        final double r2 = 1.6 + rng.nextDouble() * 0.6;
                        final Location ringP = body.clone()
                                .add(Math.cos(ang) * r2, (rng.nextDouble() - 0.5) * 1.6, Math.sin(ang) * r2);
                        final Vector inward = body.toVector().subtract(ringP.toVector()).normalize().multiply(0.2);
                        ringP.getWorld().spawnParticle(Particle.DUST, ringP, 0,
                                inward.getX(), inward.getY(), inward.getZ(), 1, WATER_LIGHT);
                    }
                }

                // (6) 地面水痕（腳下旋轉圓環 + 地面水花）
                final Location ground = pl.clone().add(0, 0.05, 0);
                for (int k = 0; k < 16; k++) {
                    final double ang = 2 * Math.PI * k / 16 + tt * 0.15;
                    final Location gp = ground.clone().add(Math.cos(ang) * 1.6, 0, Math.sin(ang) * 1.6);
                    gp.getWorld().spawnParticle(Particle.DUST, gp, 0, 0, 0, 0, 1, WATER_FOAM);
                }
                ground.getWorld().spawnParticle(Particle.SPLASH, ground, 5, 1.4, 0.05, 1.4, 0.02);

                // (7) 水中星光（END_ROD 散點閃爍感）
                if (rng.nextInt(2) == 0) {
                    pl.getWorld().spawnParticle(Particle.END_ROD,
                            body.clone().add((rng.nextDouble() - 0.5) * 2.0, (rng.nextDouble() - 0.5) * 1.6, (rng.nextDouble() - 0.5) * 2.0),
                            1, 0, 0, 0, 0.02);
                }

                // (8) 水花 + 水滴 + 氣泡
                pl.getWorld().spawnParticle(Particle.SPLASH,
                        body.clone(), 8, 1.2, 1.0, 1.2, 0.06);
                pl.getWorld().spawnParticle(Particle.DRIPPING_WATER,
                        body.clone(), 5, 1.2, 1.0, 1.2, 0);
                pl.getWorld().spawnParticle(Particle.BUBBLE_POP,
                        body.clone(), 3, 1.0, 0.8, 1.0, 0.05);

                // ── S 形水刃發動 ──
                for (final int st : slashAt) {
                    if (st == tt) {
                        fireSBlade(p, pl, sd, fwd, rng, slashDmg, slashHit);
                    }
                }
            }, t + CHARGE);
        }

        // ═════════ Phase 2：上跳蓄勢 ═════════
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            final Location l = p.getLocation();
            p.setVelocity(new Vector(0, 1.15, 0));
            l.getWorld().playSound(l, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.0f);
            l.getWorld().playSound(l, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.5f, 1.6f);
            this.shapes.animateLightPillar(l, 1.4, 3.0, 6, 8,
                    Particle.DUST, WATER_CYAN);
            l.getWorld().spawnParticle(Particle.SPLASH, l, 20, 1.2, 0.3, 1.2, 0.2);
        }, 50L + CHARGE);

        // ═════════ Phase 3：俯衝龍頭 ═════════
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            final Location l = p.getLocation();
            l.getWorld().playSound(l, Sound.ENTITY_WITHER_AMBIENT, 0.75f, 0.7f);
            l.getWorld().playSound(l, Sound.BLOCK_BEACON_POWER_SELECT, 0.75f, 0.6f);
        }, 58L + CHARGE);

        // 俯衝 + 線框龍頭
        for (int t = 58; t <= 80; t++) {
            final int localT = t;
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                if (!p.isOnline()) return;
                // 俯衝：視線方向強制向下傾
                final Vector look = p.getLocation().getDirection().clone();
                look.setY(-1.4);
                look.normalize();
                p.setVelocity(look.multiply(1.3));

                final Location l = p.getLocation();
                final Vector fwd = p.getLocation().getDirection().clone().normalize();
                final Vector sd = new Vector(-fwd.getZ(), 0, fwd.getX());
                if (sd.lengthSquared() < 1e-4) sd.setX(1);
                sd.normalize();
                final Vector upA = new Vector(0, 1, 0);

                // 龍頭錨點：玩家前方 2.2、上方 0.6
                final Location headAt = l.clone()
                        .add(fwd.clone().multiply(2.2))
                        .add(0, 0.6, 0);

                drawDragonHeadWireframe(headAt, fwd, sd, upA, localT - 58);

                // 龍身（玩家連到龍頭後方）：用稀疏脊線 + 左右波浪
                for (int s = 1; s <= 5; s++) {
                    final double f = s * 0.38;
                    final double swayB = Math.sin((localT + s) * 0.4) * 0.25;
                    final Location seg = l.clone()
                            .add(fwd.clone().multiply(f))
                            .add(sd.clone().multiply(swayB))
                            .add(0, 1.0 - s * 0.04, 0);
                    // 只畫 3 顆：脊 + 側兩翼
                    seg.getWorld().spawnParticle(Particle.DUST, seg, 0, 0, 0, 0, 1, WATER_DEEP);
                    final Location lSide = seg.clone().add(sd.clone().multiply(0.35));
                    final Location rSide = seg.clone().add(sd.clone().multiply(-0.35));
                    lSide.getWorld().spawnParticle(Particle.DUST, lSide, 0, 0, 0, 0, 1, WATER_BLUE);
                    rSide.getWorld().spawnParticle(Particle.DUST, rSide, 0, 0, 0, 0, 1, WATER_BLUE);
                }

                // 撞擊判定
                for (final Entity e : headAt.getWorld().getNearbyEntities(headAt, 2.5, 2.5, 2.5)) {
                    if (!(e instanceof LivingEntity le) || e.equals(p)) continue;
                    if (!diveHit.add(e.getUniqueId())) continue;
                    this.spellHit(p, le, diveDmg);
                    le.setVelocity(fwd.clone().multiply(1.0).setY(0.5));
                    le.getWorld().spawnParticle(Particle.SPLASH, le.getLocation().add(0, 1, 0),
                            35, 0.5, 0.8, 0.5, 0.25);
                    le.getWorld().playSound(le.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.2f, 0.9f);
                }

                // 著陸 / 收招
                final Location below = l.clone().subtract(0, 0.3, 0);
                if (below.getBlock().getType().isSolid() || localT == 80) {
                    this.plugin.getSafeScheduler().runRegionDelayed(headAt, t2 -> {
                        explodeHead(headAt);
                        headAt.getWorld().playSound(headAt, Sound.ENTITY_GENERIC_EXPLODE, 0.75f, 0.8f);
                        for (final Entity e : headAt.getWorld().getNearbyEntities(headAt, 5.0, 4.0, 5.0)) {
                            if (!(e instanceof LivingEntity le) || e.equals(p)) continue;
                            if (!diveHit.add(e.getUniqueId())) continue;
                            this.spellHit(p, le, diveDmg * 0.6);
                        }
                        this.shapes.animateShockRing(headAt.clone().add(0, -0.8, 0),
                                0.5, 6.0, 10, 32, Particle.DUST, WATER_DEEP);
                    }, 0L);
                }
            }, t + CHARGE);
        }
    }

    /**
     * 用線框方式畫龍頭（比填滿球殼更有辨識度）。
     * 結構：上下頷（往 fwd 延伸 1.4 格的兩條線，間隙 = 張嘴）+ 頭蓋骨球殼輪廓 + 龍角 + 龍鬚 + 龍眼
     */
    private void drawDragonHeadWireframe(final Location headAt, final Vector fwd,
                                         final Vector sd, final Vector upA, final int localT) {
        final World w = headAt.getWorld();
        // 張嘴動畫：tick 越後嘴越開（0.1 → 0.55）
        final double jawGap = 0.12 + Math.min(localT * 0.025, 0.5);

        // ── 上頷：從頭部前端往前延伸 1.4 格，以 10 段粒子畫 ──
        for (int i = 0; i <= 10; i++) {
            final double f = 0.4 + i * 0.14;  // 0.4 ~ 1.8
            final Location jawUp = headAt.clone()
                    .add(fwd.clone().multiply(f))
                    .add(upA.clone().multiply(jawGap));
            w.spawnParticle(Particle.DUST, jawUp, 0, 0, 0, 0, 1, WATER_WHITE);
            // 上頷兩側寬度（讓嘴有厚度）
            final Location jawUpL = jawUp.clone().add(sd.clone().multiply(0.2 - i * 0.015));
            final Location jawUpR = jawUp.clone().add(sd.clone().multiply(-0.2 + i * 0.015));
            w.spawnParticle(Particle.DUST, jawUpL, 0, 0, 0, 0, 1, WATER_CYAN);
            w.spawnParticle(Particle.DUST, jawUpR, 0, 0, 0, 0, 1, WATER_CYAN);
        }
        // ── 下頷：同上但往下偏移 jawGap ──
        for (int i = 0; i <= 10; i++) {
            final double f = 0.4 + i * 0.13;
            final Location jawDn = headAt.clone()
                    .add(fwd.clone().multiply(f))
                    .add(upA.clone().multiply(-jawGap));
            w.spawnParticle(Particle.DUST, jawDn, 0, 0, 0, 0, 1, WATER_WHITE);
            final Location jawDnL = jawDn.clone().add(sd.clone().multiply(0.18 - i * 0.015));
            final Location jawDnR = jawDn.clone().add(sd.clone().multiply(-0.18 + i * 0.015));
            w.spawnParticle(Particle.DUST, jawDnL, 0, 0, 0, 0, 1, WATER_CYAN);
            w.spawnParticle(Particle.DUST, jawDnR, 0, 0, 0, 0, 1, WATER_CYAN);
        }
        // ── 嘴部水氣（張開的嘴噴霧）──
        final Location mouth = headAt.clone().add(fwd.clone().multiply(1.6));
        w.spawnParticle(Particle.SPLASH, mouth, 3, 0.2, 0.2, 0.2, 0.08);
        w.spawnParticle(Particle.DRIPPING_WATER, mouth, 2, 0.2, 0.2, 0.2, 0);

        // ── 頭蓋骨：12 顆粒子圍成一個稀疏球（輪廓感） ──
        for (int i = 0; i < 12; i++) {
            final double theta = 2 * Math.PI * i / 12;
            final double phi = (i % 2 == 0) ? 0.6 : -0.6;
            final double cx = Math.cos(theta) * Math.cos(phi) * 0.7;
            final double cy = Math.sin(phi) * 0.7;
            final double cz = Math.sin(theta) * Math.cos(phi) * 0.7;
            final Location skullP = headAt.clone()
                    .add(sd.clone().multiply(cx))
                    .add(upA.clone().multiply(cy))
                    .add(fwd.clone().multiply(cz * 0.5)); // fwd 方向壓扁一點成頭形
            w.spawnParticle(Particle.DUST, skullP, 0, 0, 0, 0, 1, WATER_BLUE);
        }

        // ── 龍角：從頭頂後方兩側彎曲向後上 ──
        for (int h = 0; h < 7; h++) {
            final double t = h / 6.0;
            final double up = 0.4 + t * 0.9;
            final double back = -0.3 - t * t * 0.7;   // 二次曲線往後
            final Location hornL = headAt.clone()
                    .add(sd.clone().multiply(0.35 + t * 0.1))
                    .add(upA.clone().multiply(up))
                    .add(fwd.clone().multiply(back));
            final Location hornR = headAt.clone()
                    .add(sd.clone().multiply(-0.35 - t * 0.1))
                    .add(upA.clone().multiply(up))
                    .add(fwd.clone().multiply(back));
            w.spawnParticle(Particle.DUST, hornL, 0, 0, 0, 0, 1, WATER_WHITE);
            w.spawnParticle(Particle.DUST, hornR, 0, 0, 0, 0, 1, WATER_WHITE);
        }

        // ── 龍鬚：從嘴角兩側隨 tick 擺動 ──
        for (int b = 1; b <= 8; b++) {
            final double phase = localT * 0.4 + b * 0.5;
            final double wav = Math.sin(phase) * 0.25;
            final Location whiskL = headAt.clone()
                    .add(fwd.clone().multiply(1.2 - b * 0.12))
                    .add(sd.clone().multiply(0.3 + wav))
                    .add(upA.clone().multiply(-0.05 - b * 0.08));
            final Location whiskR = headAt.clone()
                    .add(fwd.clone().multiply(1.2 - b * 0.12))
                    .add(sd.clone().multiply(-0.3 - wav))
                    .add(upA.clone().multiply(-0.05 - b * 0.08));
            w.spawnParticle(Particle.DUST, whiskL, 0, 0, 0, 0, 1, WATER_CYAN);
            w.spawnParticle(Particle.DUST, whiskR, 0, 0, 0, 0, 1, WATER_CYAN);
        }

        // ── 龍眼：兩顆亮黃 ──
        final Particle.DustOptions eye = ParticleShapes.dust(0xFCD34D, 1.6f);
        final Location eyeL = headAt.clone().add(sd.clone().multiply(0.5)).add(upA.clone().multiply(0.25)).add(fwd.clone().multiply(0.2));
        final Location eyeR = headAt.clone().add(sd.clone().multiply(-0.5)).add(upA.clone().multiply(0.25)).add(fwd.clone().multiply(0.2));
        w.spawnParticle(Particle.DUST, eyeL, 0, 0, 0, 0, 1, eye);
        w.spawnParticle(Particle.DUST, eyeR, 0, 0, 0, 0, 1, eye);
    }

    /**
     * 發射一道 S 形水刃：軌跡是 sin 波而非扇形，朝隨機方向、隨機傾斜。
     * 立即畫出整條刃（20 段粒子），再補兩個 tick 的殘影。
     */
    private void fireSBlade(final Player caster, final Location origin,
                             final Vector sd, final Vector fwd,
                             final java.util.concurrent.ThreadLocalRandom rng,
                             final double dmg, final Set<UUID> hitSet) {
        // 隨機方向：水平 ±140°、俯仰 ±60°
        final double yawOff = Math.toRadians((rng.nextDouble() - 0.5) * 280);
        final double pitchOff = Math.toRadians((rng.nextDouble() - 0.5) * 120);
        // 以玩家 fwd 為基準旋轉
        final double cy = Math.cos(yawOff), sy = Math.sin(yawOff);
        final double cp = Math.cos(pitchOff), sp = Math.sin(pitchOff);
        // 刃前進方向（yaw 旋轉 + pitch 上下）
        final Vector bladeFwd = fwd.clone().multiply(cy).add(sd.clone().multiply(sy));
        bladeFwd.setY(bladeFwd.getY() * cp - sp);
        bladeFwd.normalize();
        // 刃的「正交軸」用來做 S 波偏移（同時水平 + 垂直混合）
        final Vector bladeSide = new Vector(-bladeFwd.getZ(), 0, bladeFwd.getX());
        if (bladeSide.lengthSquared() < 1e-4) bladeSide.setX(1);
        bladeSide.normalize();
        final Vector bladeUp = bladeFwd.clone().crossProduct(bladeSide).normalize();

        final double length = 5.5 + rng.nextDouble() * 2.0;   // 5.5~7.5 格
        final double amp = 0.8 + rng.nextDouble() * 0.7;      // S 波振幅 0.8~1.5
        final double waves = 1.3 + rng.nextDouble() * 0.8;    // 1.3~2.1 個完整波
        final double phase0 = rng.nextDouble() * Math.PI * 2; // 隨機起始相位
        // 軸向混合：部分 up、部分 side → 不規則歪斜
        final double mixS = rng.nextDouble();
        final double mixU = 1 - mixS;

        final Location startAt = origin.clone().add(0, 1.0, 0);
        startAt.getWorld().playSound(startAt, Sound.ENTITY_PLAYER_ATTACK_SWEEP,
                1.0f, 0.8f + rng.nextFloat() * 0.8f);
        startAt.getWorld().playSound(startAt, Sound.ITEM_TRIDENT_THROW,
                0.8f, 1.3f + rng.nextFloat() * 0.5f);

        // 逐 tick 繪製刃（4 tick 完成，每 tick 畫 10 段）
        final int totalSegs = 40;
        final int bladeTicks = 4;
        for (int tk = 0; tk < bladeTicks; tk++) {
            final int tick = tk;
            this.plugin.getSafeScheduler().runRegionDelayed(startAt, t2 -> {
                final int segFrom = totalSegs * tick / bladeTicks;
                final int segTo = totalSegs * (tick + 1) / bladeTicks;
                for (int i = segFrom; i < segTo; i++) {
                    final double u = i / (double) totalSegs;              // 0~1
                    final double dist = u * length;
                    final double sine = Math.sin(phase0 + u * Math.PI * 2 * waves) * amp;
                    // 核心點：延前進方向 + S 波偏移（混合兩個正交軸）
                    final Location p0 = startAt.clone()
                            .add(bladeFwd.clone().multiply(dist))
                            .add(bladeSide.clone().multiply(sine * mixS))
                            .add(bladeUp.clone().multiply(sine * mixU));
                    // 加一點隨機抖動，讓刃「毛毛的」不是死板曲線
                    final double jx = (rng.nextDouble() - 0.5) * 0.12;
                    final double jy = (rng.nextDouble() - 0.5) * 0.12;
                    final double jz = (rng.nextDouble() - 0.5) * 0.12;
                    final Location p1 = p0.clone().add(jx, jy, jz);
                    // 多層：核心 + 外層 + 白浪（加厚：每點多灑幾顆）
                    p1.getWorld().spawnParticle(Particle.DUST, p1, 3, 0.1, 0.1, 0.1, 0, WATER_CYAN);
                    p1.getWorld().spawnParticle(Particle.DUST, p1, 3, 0.15, 0.15, 0.15, 0, WATER_BLUE);
                    p1.getWorld().spawnParticle(Particle.DUST, p1, 2, 0.18, 0.18, 0.18, 0, WATER_FOAM);
                    if (i % 2 == 0) {
                        p1.getWorld().spawnParticle(Particle.DUST, p1, 2, 0.15, 0.15, 0.15, 0, WATER_WHITE);
                        p1.getWorld().spawnParticle(Particle.SPLASH, p1, 2, 0.12, 0.12, 0.12, 0.04);
                    }
                    // 隨機點加 DRIPPING_WATER 營造水滴感
                    if (rng.nextInt(4) == 0) {
                        p1.getWorld().spawnParticle(Particle.DRIPPING_WATER, p1, 1, 0.1, 0.1, 0.1, 0);
                    }
                }
            }, tk);
        }

        // 傷害檢定：刃路徑上的實體（在中心線上查 N 個點）
        for (int i = 0; i <= 10; i++) {
            final double u = i / 10.0;
            final double dist = u * length;
            final double sine = Math.sin(phase0 + u * Math.PI * 2 * waves) * amp;
            final Location probe = startAt.clone()
                    .add(bladeFwd.clone().multiply(dist))
                    .add(bladeSide.clone().multiply(sine * mixS))
                    .add(bladeUp.clone().multiply(sine * mixU));
            for (final Entity e : probe.getWorld().getNearbyEntities(probe, 1.3, 1.3, 1.3)) {
                if (!(e instanceof LivingEntity le) || e.equals(caster)) continue;
                if (!hitSet.add(e.getUniqueId())) continue;
                this.spellHit(caster, le, dmg);
                le.getWorld().spawnParticle(Particle.SPLASH,
                        le.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0.1);
            }
        }
    }

    /** 龍頭結束/撞擊時的水爆 */
    private void explodeHead(final Location at) {
        at.getWorld().playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f);
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.5f, 0.8f);
        ParticleShapes.sphereShell(at, 2.5, 70, Particle.DUST, WATER_BLUE);
        ParticleShapes.sphereShell(at, 3.2, 60, Particle.DUST, WATER_WHITE);
        at.getWorld().spawnParticle(Particle.SPLASH, at, 100, 2.5, 2.5, 2.5, 0.35);
        at.getWorld().spawnParticle(Particle.CLOUD, at, 25, 1.4, 1.4, 1.4, 0.1);
        at.getWorld().spawnParticle(Particle.DRIPPING_WATER, at, 40, 2.0, 2.0, 2.0, 0);
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
                l.getWorld().playSound(l, Sound.ENTITY_PHANTOM_FLAP, 0.5f, 1.8f);
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
        start.getWorld().playSound(start, Sound.ENTITY_FISHING_BOBBER_THROW, 0.5f, 1.4f);
        start.getWorld().playSound(start, Sound.ENTITY_ITEM_FRAME_ADD_ITEM, 0.5f, 1.8f);
    }

    /** LLR 獵人之眼 — 腳下金色擴張環 + 生物發光 30s。 */
    // ── 風暴色譜 ──
    private static final Particle.DustOptions STORM_CYAN   = ParticleShapes.dust(0x22D3EE, 1.6f);
    private static final Particle.DustOptions STORM_WHITE  = ParticleShapes.dust(0xE0F2FE, 1.2f);
    private static final Particle.DustOptions STORM_DARK   = ParticleShapes.dust(0x164E63, 1.8f);
    private static final Particle.DustOptions STORM_TEAL   = ParticleShapes.dust(0x2DD4BF, 1.4f);

    /**
     * LLR 風暴箭雨 — 召喚風暴旋渦 → 箭矢從天降下轟炸前方圓形區域。
     * 多階段動畫：旋風蓄力 → 天空裂開 → 箭雨逐波墜落 → 著地爆炸衝擊波。
     */
    private void stormArrows(final Player p) {
        final double bonus = this.magicOrStrengthMultiplier(p, false);
        final double baseDmg = 10.0 * bonus;
        final Vector dir = p.getLocation().getDirection().setY(0).normalize();
        final Location center = p.getLocation().clone().add(dir.clone().multiply(10));
        center.setY(p.getLocation().getY());

        // ══ 蓄力（t=0-8）══ 腳下風暴圓陣旋轉 + 頭上雲層匯聚
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.4f, 2.0f);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 0.8f, 1.4f);
        for (int t = 0; t <= 8; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                if (!p.isOnline()) return;
                final double prog = (double) tick / 8.0;
                final Location cl = p.getLocation();
                // 旋轉風陣
                final double spin = prog * Math.PI * 6;
                for (int arm = 0; arm < 4; arm++) {
                    final double a = spin + Math.PI * 0.5 * arm;
                    final double r = 1.5 + prog * 0.5;
                    cl.getWorld().spawnParticle(Particle.DUST,
                            cl.clone().add(Math.cos(a) * r, 0.2 + prog * 0.3, Math.sin(a) * r),
                            2, 0, 0, 0, 0, STORM_CYAN);
                }
                // 頭上雲層
                cl.getWorld().spawnParticle(Particle.CLOUD,
                        center.clone().add(0, 12 - prog * 3, 0),
                        (int) (5 + prog * 25), 3.0 + prog * 2, 0.3, 3.0 + prog * 2, 0.02);
                if (tick == 4)
                    cl.getWorld().playSound(cl, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.15f, 1.8f);
            }, tick);
        }

        // ══ 天空裂開（t=10）══ 閃電 + 圓陣標記落點
        this.plugin.getSafeScheduler().runRegionDelayed(center, task -> {
            center.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.5f);
            center.getWorld().playSound(center, Sound.ITEM_TRIDENT_THUNDER, 0.5f, 1.2f);
            // 目標圈
            ParticleShapes.circleH(center.clone().add(0, 0.15, 0), 5.0, 40, Particle.DUST, STORM_CYAN);
            ParticleShapes.circleH(center.clone().add(0, 0.2, 0), 4.5, 30, Particle.DUST, STORM_TEAL);
            center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    center.clone().add(0, 10, 0), 100, 2.0, 3.0, 2.0, 0.5);
        }, 10L);

        // ══ 箭雨（t=12-24, 3 波, 每波 4 tick）══
        final java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        for (int wave = 0; wave < 3; wave++) {
            final int waveDelay = 12 + wave * 4;
            final int arrowCount = 6 + wave * 3; // 6, 9, 12
            this.plugin.getSafeScheduler().runRegionDelayed(center, task -> {
                center.getWorld().playSound(center, Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.6f + rng.nextFloat(0.4f));
                center.getWorld().playSound(center, Sound.ITEM_TRIDENT_THROW, 0.4f, 1.6f);
                // 每支箭的落點
                for (int a = 0; a < arrowCount; a++) {
                    final double angle = rng.nextDouble(Math.PI * 2);
                    final double dist = rng.nextDouble(4.5);
                    final Location impact = center.clone().add(
                            Math.cos(angle) * dist, 0.2, Math.sin(angle) * dist);
                    // 箭矢下落軌跡（從高處到地面，4 tick 動畫）
                    final Location skyPos = impact.clone().add(
                            rng.nextDouble(-1, 1), 8 + rng.nextDouble(4), rng.nextDouble(-1, 1));
                    for (int ft = 0; ft < 4; ft++) {
                        final int frameTick = ft;
                        final double frac = (double) (ft + 1) / 4.0;
                        final Location framePos = skyPos.clone().add(
                                (impact.getX() - skyPos.getX()) * frac,
                                (impact.getY() - skyPos.getY()) * frac,
                                (impact.getZ() - skyPos.getZ()) * frac);
                        // 這裡用 runRegionDelayed 避免實體追蹤問題
                        SpellExecutor.this.plugin.getSafeScheduler().runRegionDelayed(center, t2 -> {
                            center.getWorld().spawnParticle(Particle.DUST, framePos,
                                    2, 0.05, 0.1, 0.05, 0, STORM_WHITE);
                            center.getWorld().spawnParticle(Particle.END_ROD, framePos,
                                    1, 0, 0, 0, 0.01);
                        }, frameTick);
                    }
                    // 著地爆炸（t+4）
                    SpellExecutor.this.plugin.getSafeScheduler().runRegionDelayed(center, t2 -> {
                        impact.getWorld().spawnParticle(Particle.DUST, impact,
                                8, 0.4, 0.2, 0.4, 0, STORM_CYAN);
                        impact.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, impact,
                                12, 0.3, 0.3, 0.3, 0.15);
                        impact.getWorld().spawnParticle(Particle.CLOUD, impact,
                                4, 0.2, 0.1, 0.2, 0.05);
                    }, 4L);
                }
            }, waveDelay);
        }

        // ══ 傷害判定（t=14, 18, 22 每波判定）══
        for (int wave = 0; wave < 3; wave++) {
            final int dmgTick = 16 + wave * 4;
            this.plugin.getSafeScheduler().runRegionDelayed(center, task -> {
                for (final Entity e : center.getWorld().getNearbyEntities(center, 5, 3, 5)) {
                    if (e instanceof LivingEntity le && !e.equals(p)) {
                        le.damage(baseDmg, p);
                        le.getWorld().spawnParticle(Particle.CRIT, le.getLocation().add(0, 1, 0),
                                8, 0.3, 0.5, 0.3, 0.1);
                    }
                }
            }, dmgTick);
        }

        // ══ 終結衝擊波（t=28）══ 最後一波大爆炸
        this.plugin.getSafeScheduler().runRegionDelayed(center, task -> {
            this.shapes.animateShockRing(center.clone().add(0, 0.15, 0),
                    0.5, 7.0, 10, 40, Particle.DUST, STORM_CYAN);
            this.shapes.animateShockRing(center.clone().add(0, 0.2, 0),
                    0.3, 5.0, 8, 20, Particle.DUST, STORM_DARK);
            center.getWorld().spawnParticle(Particle.FIREWORK,
                    center.clone().add(0, 1, 0), 40, 2.5, 1.5, 2.5, 0.3);
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.4f);
            center.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 1.8f);
            // 最終傷害（強化）
            for (final Entity e : center.getWorld().getNearbyEntities(center, 6, 3, 6)) {
                if (e instanceof LivingEntity le && !e.equals(p)) {
                    le.damage(baseDmg * 1.5, p);
                    le.setVelocity(le.getLocation().toVector()
                            .subtract(center.toVector()).normalize().multiply(0.8).setY(0.5));
                }
            }
        }, 28L);
    }

    /** RRL 影襲 — 深紫煙霧球殼 + 隱身 8s。 */
    private void shadowStrike(final Player p) {
        final Location loc = p.getLocation().add(0, 1, 0);
        final Particle.DustOptions shadow = ParticleShapes.dust(0x312E81, 1.8f);

        ParticleShapes.sphereShell(loc, 1.6, 36, Particle.LARGE_SMOKE, null);
        ParticleShapes.sphereShell(loc, 1.4, 28, Particle.DUST, shadow);
        loc.getWorld().spawnParticle(Particle.SQUID_INK, loc, 20, 0.5, 0.7, 0.5, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.8f);

        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 160, 0, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 2, false, true));
        // 下一擊必爆擊
        this.shadowStrikeCrit.add(p.getUniqueId());
        p.sendActionBar(Component.text("✦ 影襲就緒 — 下一擊必定暴擊",
                TextColor.color(0x818CF8)).decoration(TextDecoration.ITALIC, false));
    }

    // ═══════════════════════════════════════════════════════════
    //  GATHERING
    // ═══════════════════════════════════════════════════════════

    /** LRL 礦脈連鎖 — 下一鎬觸發礦脈連挖 64 格。 */
    private void veinChain(final Player p) {
        final Location loc = p.getLocation();
        final Particle.DustOptions gold = ParticleShapes.dust(0xFBBF24, 1.4f);

        ParticleShapes.circleH(loc.clone().add(0, 0.1, 0), 1.5, 20, Particle.DUST, gold);
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(0, 1.5, 0),
                30, 0.8, 1.0, 0.8, 0.5);
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.8f);

        // 30 秒內下一鎬觸發
        this.veinChainPending.put(p.getUniqueId(), System.currentTimeMillis() + 30_000L);
        p.sendActionBar(Component.text("✦ 礦脈連鎖就緒 — 挖掘礦石觸發",
                TextColor.color(0xFBBF24)).decoration(TextDecoration.ITALIC, false));
    }

    /** 由 TechListener 呼叫：執行礦脈連鎖 BFS。 */
    public void executeVeinChain(final Player p, final Block origin) {
        final Material type = origin.getType();
        final Set<Block> visited = new HashSet<>();
        final java.util.Deque<Block> queue = new java.util.ArrayDeque<>();
        queue.add(origin);
        int broken = 0;
        final Particle.DustOptions gold = ParticleShapes.dust(0xFBBF24, 1.1f);
        while (!queue.isEmpty() && broken < 64) {
            final Block b = queue.poll();
            if (!visited.add(b) || b.getType() != type) continue;
            if (!this.canModifyBlock(p, b)) continue;
            broken++;
            final Location l = b.getLocation().add(0.5, 0.5, 0.5);
            b.breakNaturally(p.getInventory().getItemInMainHand());
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
                0.5, 3.5, 12, 24, Particle.SOUL_FIRE_FLAME, null);
        this.shapes.animateShockRing(center.clone().add(0, 0.2, 0),
                0.5, 3.0, 14, 20, Particle.DUST, green);
        center.getWorld().playSound(center, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.5f, 1.2f);

        int collected = 0;
        final org.bukkit.inventory.ItemStack tool = p.getInventory().getItemInMainHand();
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -2; dz <= 2; dz++) {
                    final Block b = center.clone().add(dx, dy, dz).getBlock();
                    if (isOre(b.getType()) && this.canModifyBlock(p, b)) {
                        // 取得掉落物後直接塞入背包
                        final Collection<org.bukkit.inventory.ItemStack> drops = b.getDrops(tool, p);
                        for (final org.bukkit.inventory.ItemStack drop : drops) {
                            final Map<Integer, org.bukkit.inventory.ItemStack> overflow =
                                    p.getInventory().addItem(drop);
                            // 背包滿了就掉地上
                            for (final org.bukkit.inventory.ItemStack leftover : overflow.values()) {
                                p.getWorld().dropItemNaturally(p.getLocation(), leftover);
                            }
                        }
                        // 給經驗球
                        b.getWorld().spawn(b.getLocation().add(0.5, 0.5, 0.5),
                                org.bukkit.entity.ExperienceOrb.class,
                                orb -> orb.setExperience(1));
                        b.setType(Material.AIR);
                        collected++;
                    }
                }
        p.sendMessage(Component.text("  ✓ 震盪收集了 " + collected + " 礦物（已放入背包）",
                        TextColor.color(0x10B981)).decoration(TextDecoration.ITALIC, false));
    }

    /** LLR 豐饒祝福 — 粉色雙螺旋 + 30s 掉落加成。 */
    // ── 大地色譜 ──
    private static final Particle.DustOptions EARTH_BROWN  = ParticleShapes.dust(0x92400E, 1.8f);
    private static final Particle.DustOptions EARTH_AMBER  = ParticleShapes.dust(0xFBBF24, 1.6f);
    private static final Particle.DustOptions EARTH_ORANGE = ParticleShapes.dust(0xEA580C, 1.4f);
    private static final Particle.DustOptions EARTH_GOLD   = ParticleShapes.dust(0xFFD700, 1.2f);

    /**
     * LLR 大地裂擊 — 重鎬轟地 → 裂縫從腳下沿視線方向延伸 → 岩石飛濺拋飛敵人。
     * 多階段：舉鎬蓄力 → 重擊地面 → 裂縫逐格推進 → 沿路岩柱噴射。
     */
    private void earthShatter(final Player p) {
        final double bonus = this.magicOrStrengthMultiplier(p, false);
        final double baseDmg = 16.0 * bonus;

        // ══ 舉鎬蓄力（t=0-6）══ 上升粒子匯聚 + 身體後仰
        this.nudge(p, -0.2, 0, 0);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 0.5f);
        for (int t = 0; t <= 6; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                if (!p.isOnline()) return;
                final double prog = (double) tick / 6.0;
                final Location cl = p.getLocation();
                // 腳下震動粒子
                cl.getWorld().spawnParticle(Particle.DUST,
                        cl.clone().add(0, 0.1, 0), (int) (3 + prog * 15),
                        0.8 + prog, 0.1, 0.8 + prog, 0, EARTH_BROWN);
                cl.getWorld().spawnParticle(Particle.BLOCK,
                        cl.clone().add(0, 0.3, 0), (int) (2 + prog * 10),
                        0.6, 0.2, 0.6, 0.1,
                        Material.DIRT.createBlockData());
                if (tick == 3) {
                    cl.getWorld().playSound(cl, Sound.ENTITY_WARDEN_HEARTBEAT, 0.25f, 0.8f);
                    nudge(p, 0, 0, 0.15); // 微跳
                }
                if (tick == 5) {
                    cl.getWorld().playSound(cl, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.3f, 0.6f);
                }
            }, tick);
        }

        // ══ 重擊地面（t=8）══ 前衝 + 巨大衝擊波
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            this.nudge(p, 0.6, 0, -0.1); // 前衝下壓
            final Location impactLoc = p.getLocation();
            final float yaw = impactLoc.getYaw();
            final Vector fissureDir = impactLoc.getDirection().setY(0).normalize();

            // 撞擊音效
            impactLoc.getWorld().playSound(impactLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.5f);
            impactLoc.getWorld().playSound(impactLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.3f, 0.5f);
            impactLoc.getWorld().playSound(impactLoc, Sound.BLOCK_ANVIL_LAND, 0.6f, 0.3f);

            // 腳下碎裂圓
            this.shapes.animateShockRing(impactLoc.clone().add(0, 0.15, 0),
                    0.3, 4.0, 8, 30, Particle.DUST, EARTH_AMBER);
            impactLoc.getWorld().spawnParticle(Particle.BLOCK,
                    impactLoc.clone().add(0, 0.5, 0), 80,
                    2.0, 0.3, 2.0, 0.3, Material.STONE.createBlockData());
            impactLoc.getWorld().spawnParticle(Particle.BLOCK,
                    impactLoc.clone().add(0, 0.3, 0), 40,
                    1.5, 0.2, 1.5, 0.2, Material.DIRT.createBlockData());

            // ── 裂縫逐格推進（t+1 到 t+12, 每 tick 前進 1.5 格）──
            final Set<LivingEntity> damaged = new HashSet<>();
            for (int step = 1; step <= 12; step++) {
                final int s = step;
                final double dist = s * 1.5;
                this.plugin.getSafeScheduler().runRegionDelayed(impactLoc, task -> {
                    final Location fLoc = impactLoc.clone().add(fissureDir.clone().multiply(dist));
                    // 裂縫線粒子
                    fLoc.getWorld().spawnParticle(Particle.DUST, fLoc.clone().add(0, 0.2, 0),
                            6, 0.25, 0.1, 0.25, 0, EARTH_ORANGE);
                    fLoc.getWorld().spawnParticle(Particle.DUST, fLoc.clone().add(0, 0.3, 0),
                            4, 0.15, 0.05, 0.15, 0, EARTH_GOLD);
                    // 岩石碎片噴射（越遠越高）
                    final double height = 0.5 + s * 0.15;
                    fLoc.getWorld().spawnParticle(Particle.BLOCK,
                            fLoc.clone().add(0, height, 0), 8 + s,
                            0.4, height * 0.5, 0.4, 0.15,
                            Material.STONE.createBlockData());
                    // 兩側碎裂展開
                    final double yawRad = Math.toRadians(yaw);
                    final double rx = Math.cos(yawRad), rz = Math.sin(yawRad);
                    final double spread = 0.5 + s * 0.08;
                    fLoc.getWorld().spawnParticle(Particle.DUST,
                            fLoc.clone().add(rx * spread, 0.15, rz * spread),
                            3, 0.1, 0.05, 0.1, 0, EARTH_BROWN);
                    fLoc.getWorld().spawnParticle(Particle.DUST,
                            fLoc.clone().add(-rx * spread, 0.15, -rz * spread),
                            3, 0.1, 0.05, 0.1, 0, EARTH_BROWN);
                    // 音效
                    if (s % 3 == 0)
                        fLoc.getWorld().playSound(fLoc, Sound.BLOCK_STONE_BREAK, 0.8f, 0.6f + s * 0.05f);
                    // 傷害
                    for (final Entity e : fLoc.getWorld().getNearbyEntities(fLoc, 2, 2, 2)) {
                        if (e instanceof LivingEntity le && !e.equals(p) && damaged.add(le)) {
                            le.damage(baseDmg, p);
                            le.setVelocity(new Vector(
                                    fissureDir.getX() * 0.5, 0.6 + s * 0.04, fissureDir.getZ() * 0.5));
                            le.getWorld().spawnParticle(Particle.BLOCK,
                                    le.getLocation().add(0, 1, 0), 15,
                                    0.3, 0.4, 0.3, 0.1, Material.STONE.createBlockData());
                        }
                    }
                }, s);
            }

            // ── 末端爆發岩柱（t+14）──
            this.plugin.getSafeScheduler().runRegionDelayed(impactLoc, task -> {
                final Location endLoc = impactLoc.clone().add(fissureDir.clone().multiply(18));
                endLoc.getWorld().playSound(endLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.7f);
                // 岩柱噴發
                this.shapes.animateLightPillar(endLoc, 1.5, 5.0, 10, 30, Particle.DUST, EARTH_AMBER);
                endLoc.getWorld().spawnParticle(Particle.BLOCK,
                        endLoc.clone().add(0, 3, 0), 120,
                        1.5, 2.5, 1.5, 0.5, Material.DEEPSLATE.createBlockData());
                endLoc.getWorld().spawnParticle(Particle.LAVA, endLoc.clone().add(0, 1, 0),
                        15, 1.0, 0.5, 1.0, 0);
                // 末端 AoE
                for (final Entity e : endLoc.getWorld().getNearbyEntities(endLoc, 3, 3, 3)) {
                    if (e instanceof LivingEntity le && !e.equals(p) && damaged.add(le)) {
                        le.damage(baseDmg * 1.3, p);
                        le.setVelocity(new Vector(0, 1.0, 0));
                    }
                }
            }, 14L);
        }, 8L);
    }

    /** RRL 大地共鳴 — 50 格礦脈顯形光柱（持續 60s）。 */
    private void earthResonance(final Player p) {
        final Location loc = p.getLocation();
        final Particle.DustOptions amber = ParticleShapes.dust(0xFBBF24, 1.4f);

        this.shapes.animateShockRing(loc.clone().add(0, 0.1, 0),
                0.5, 8.0, 18, 36, Particle.ELECTRIC_SPARK, null);
        ParticleShapes.circleH(loc.clone().add(0, 1.5, 0), 3.0, 30, Particle.DUST, amber);
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.5f, 1.8f);

        // 掃描 50 格內礦脈
        final java.util.List<Location> oreLocations = new java.util.ArrayList<>();
        for (int dx = -25; dx <= 25; dx += 2)
            for (int dy = -10; dy <= 10; dy += 2)
                for (int dz = -25; dz <= 25; dz += 2) {
                    final Block b = loc.clone().add(dx, dy, dz).getBlock();
                    if (isOre(b.getType())) {
                        oreLocations.add(b.getLocation().add(0.5, 0.5, 0.5));
                    }
                }

        p.sendMessage(Component.text("  ✓ 感應到 " + oreLocations.size() + " 個礦脈訊號（持續 60 秒）",
                        TextColor.color(0xFBBF24)).decoration(TextDecoration.ITALIC, false));

        // 每 20 tick 刷新一次粒子，持續 60 秒（60 次）
        if (!oreLocations.isEmpty()) {
            for (int pulse = 0; pulse < 60; pulse++) {
                this.plugin.getSafeScheduler().runRegionDelayed(loc, task -> {
                    for (final Location oreLoc : oreLocations) {
                        // 如果礦石已被挖走就跳過
                        if (!isOre(oreLoc.getBlock().getType())) continue;
                        oreLoc.getWorld().spawnParticle(Particle.END_ROD,
                                oreLoc, 1, 0.1, 0.1, 0.1, 0);
                        oreLoc.getWorld().spawnParticle(Particle.DUST,
                                oreLoc.clone().add(0, 0.5, 0), 1, 0.1, 0.1, 0.1, 0, amber);
                    }
                }, pulse * 20L);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ENGINEERING
    // ═══════════════════════════════════════════════════════════

    /** LRL 自動砲台 — 部署持續 15 秒的科技砲台，每秒自動鎖定攻擊最近敵人。 */
    private void turretDeploy(final Player p) {
        final double bonus = this.magicOrStrengthMultiplier(p, false);
        final double shotDmg = 8.0 * bonus;
        final Location loc = p.getLocation().clone();
        final World world = loc.getWorld();
        final Particle.DustOptions turretOrange = ParticleShapes.dust(0xF97316, 1.6f);
        final Particle.DustOptions turretCyan   = ParticleShapes.dust(0x06B6D4, 1.2f);

        // ── 部署動畫 ──
        ParticleShapes.pentagram(loc.clone().add(0, 0.1, 0), 1.5, Particle.DUST, turretOrange);
        world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.1);
        world.spawnParticle(Particle.FLAME, loc.clone().add(0, 0.5, 0), 16, 0.3, 0.4, 0.3, 0.02);
        world.playSound(loc, Sound.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.4f, 1.6f);
        world.playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.4f, 0.8f);

        // 生成砲台實體（盔甲架）
        final ArmorStand turret = world.spawn(loc, ArmorStand.class, stand -> {
            stand.setVisible(false);
            stand.setGravity(false);
            stand.setSmall(false);
            stand.setMarker(true);
            stand.setCustomName("§6§l科技砲台");
            stand.setCustomNameVisible(true);
            stand.getEquipment().setHelmet(new org.bukkit.inventory.ItemStack(Material.DISPENSER));
        });

        // ── 每秒射擊（共 15 次，持續 15 秒）──
        final int totalShots = 15;
        for (int shot = 0; shot < totalShots; shot++) {
            final long delay = 20L * (shot + 1);  // 第 1 秒、第 2 秒...
            this.plugin.getSafeScheduler().runRegionDelayed(loc, task -> {
                if (turret.isDead()) return;
                final Location turretLoc = turret.getLocation().clone().add(0, 1.5, 0);

                // 砲台旋轉環（持續視覺回饋）
                ParticleShapes.circleH(turretLoc.clone().add(0, 0.3, 0),
                        0.6, 8, Particle.DUST, turretCyan);

                // 找最近敵人（10 格範圍）
                LivingEntity target = null;
                double closest = 100.0;
                for (final Entity e : turret.getNearbyEntities(10, 5, 10)) {
                    if (!(e instanceof LivingEntity le)) continue;
                    if (e instanceof Player) continue;   // 不打玩家
                    if (le.isDead()) continue;
                    final double dist = le.getLocation().distance(turretLoc);
                    if (dist < closest) {
                        closest = dist;
                        target = le;
                    }
                }

                if (target == null) {
                    // 無目標 — 待機旋轉粒子
                    world.spawnParticle(Particle.ELECTRIC_SPARK, turretLoc, 6, 0.3, 0.3, 0.3, 0.02);
                    return;
                }

                // ── 射擊光束 ──
                final Location targetLoc = target.getLocation().clone().add(0, target.getHeight() * 0.5, 0);
                final Vector beamDir = targetLoc.toVector().subtract(turretLoc.toVector());
                final double beamLen = beamDir.length();
                if (beamLen > 0) beamDir.normalize();

                // 沿光束繪粒子
                final int steps = (int) Math.ceil(beamLen * 2);
                for (int s = 0; s < steps; s++) {
                    final Location point = turretLoc.clone().add(beamDir.clone().multiply(s * 0.5));
                    world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.05, 0.05, 0.05, 0);
                    if (s % 3 == 0) {
                        world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, turretOrange);
                    }
                }

                // 命中特效
                world.spawnParticle(Particle.CRIT, targetLoc, 8, 0.2, 0.2, 0.2, 0.15);
                world.spawnParticle(Particle.ELECTRIC_SPARK, targetLoc, 10, 0.3, 0.3, 0.3, 0.1);
                world.playSound(turretLoc, Sound.ENTITY_FIREWORK_ROCKET_BLAST,
                        SoundCategory.PLAYERS, 0.4f, 1.8f);
                world.playSound(turretLoc, Sound.BLOCK_PISTON_EXTEND,
                        SoundCategory.PLAYERS, 0.3f, 1.6f);

                // 傷害
                target.damage(shotDmg, p);
            }, delay);
        }

        // ── 砲台消失 ──
        this.plugin.getSafeScheduler().runRegionDelayed(loc, task -> {
            if (!turret.isDead()) {
                final Location deathLoc = turret.getLocation().add(0, 1, 0);
                ParticleShapes.sphereShell(deathLoc, 1.0, 20, Particle.LARGE_SMOKE, null);
                world.spawnParticle(Particle.ELECTRIC_SPARK, deathLoc, 24, 0.4, 0.6, 0.4, 0.1);
                world.playSound(deathLoc, Sound.BLOCK_ANVIL_DESTROY,
                        SoundCategory.PLAYERS, 0.3f, 1.2f);
                turret.remove();
            }
        }, 20L * totalShots + 10L);

        p.sendMessage(Component.text("  ✓ 科技砲台已部署（15 秒 / 10 格範圍）",
                TextColor.color(0xF97316)).decoration(TextDecoration.ITALIC, false));
    }

    /** RLR 過載核心 — 附近科技機器 ×3 速 45s + 自身 Haste。 */
    private void overloadCore(final Player p) {
        final Location loc = p.getLocation();
        final Particle.DustOptions gold = ParticleShapes.dust(0xD97706, 1.8f);

        this.shapes.animateDoubleHelix(loc.clone().add(0, 0.1, 0),
                1.3, 2.6, 3, 18, Particle.DUST, gold);
        this.shapes.animateShockRing(loc.clone().add(0, 0.1, 0),
                0.5, 3.5, 12, 20, Particle.ELECTRIC_SPARK, null);
        loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0),
                30, 1.0, 0.8, 1.0, 0.02);
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 0.7f);
        loc.getWorld().playSound(loc, Sound.BLOCK_CONDUIT_ACTIVATE, 0.5f, 1.2f);

        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 900, 3, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 900, 1, false, true));

        // 附近 15 格機器 ×3 速 45 秒
        final int boosted = this.plugin.getMachineService()
                .overloadNearbyMachines(loc, 15.0, 45_000L);
        if (boosted > 0) {
            p.sendMessage(Component.text("  ✓ 過載了 " + boosted + " 台科技機器（×3 速 45 秒）",
                            TextColor.color(0xD97706)).decoration(TextDecoration.ITALIC, false));
        } else {
            p.sendMessage(Component.text("  ◇ 附近沒有科技機器",
                            TextColor.color(0x9CA3AF)).decoration(TextDecoration.ITALIC, false));
        }
    }

    /** LLR 磁軌砲 — 蓄力後發射電磁穿透砲擊。 */
    // ── 磁軌砲色譜 ──
    private static final Particle.DustOptions RAIL_BLUE    = ParticleShapes.dust(0x3B82F6, 1.8f);
    private static final Particle.DustOptions RAIL_CYAN    = ParticleShapes.dust(0x06B6D4, 1.4f);
    private static final Particle.DustOptions RAIL_WHITE   = ParticleShapes.dust(0xF0F9FF, 2.0f);
    private static final Particle.DustOptions RAIL_VIOLET  = ParticleShapes.dust(0x8B5CF6, 1.2f);

    /**
     * LLR 磁軌砲 — 電磁蓄力 → 環型加速圈逐層亮起 → 穿透射線爆射。
     * 多階段：肩部電弧蓄力 → 前方 3 環加速軌道 → 白熱射線穿透 → 終端爆炸。
     */
    private void railgun(final Player p) {
        final double bonus = this.magicOrStrengthMultiplier(p, false);
        final double baseDmg = 22.0 * bonus;

        // ══ 蓄力（t=0-10）══ 電弧匯聚到手臂 + 3 環逐層亮起
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 16, 4, false, false));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.8f, 0.4f);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT, 0.6f, 1.8f);

        final Vector aimDir = p.getLocation().getDirection().setY(0).normalize();
        for (int t = 0; t <= 10; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                if (!p.isOnline()) return;
                final double prog = (double) tick / 10.0;
                final Location cl = p.getLocation();
                // 電弧在身體周圍跳動
                cl.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                        cl.clone().add(0, 1, 0), (int) (5 + prog * 40),
                        0.3 + prog * 0.4, 0.5, 0.3 + prog * 0.4, 0.05 + prog * 0.2);
                // 前方 3 個加速環依次亮起
                final Vector dir = cl.getDirection().setY(0).normalize();
                for (int ring = 0; ring < 3; ring++) {
                    final double ringProg = (prog * 3 - ring);
                    if (ringProg < 0 || ringProg > 1) continue;
                    final double ringDist = 2.0 + ring * 2.5;
                    final Location ringCenter = cl.clone().add(dir.clone().multiply(ringDist)).add(0, 1, 0);
                    final double ringR = 1.2 - ring * 0.2;
                    final Particle.DustOptions ringColor = ring == 0 ? RAIL_BLUE : ring == 1 ? RAIL_CYAN : RAIL_WHITE;
                    // 環的弧度隨蓄力展開
                    final int pts = (int) (ringProg * 20) + 4;
                    for (int pt = 0; pt < pts; pt++) {
                        final double a = Math.PI * 2 * pt / pts;
                        // 環面垂直於 dir
                        final double px = -dir.getZ() * Math.cos(a) * ringR;
                        final double py = Math.sin(a) * ringR;
                        final double pz = dir.getX() * Math.cos(a) * ringR;
                        cl.getWorld().spawnParticle(Particle.DUST,
                                ringCenter.clone().add(px, py, pz),
                                1, 0, 0, 0, 0, ringColor);
                    }
                }
                // 音效
                if (tick == 3) cl.getWorld().playSound(cl, Sound.BLOCK_BEACON_ACTIVATE, 0.25f, 1.8f);
                if (tick == 6) cl.getWorld().playSound(cl, Sound.ENTITY_WARDEN_HEARTBEAT, 0.2f, 1.6f);
                if (tick == 9) {
                    cl.getWorld().playSound(cl, Sound.ITEM_TRIDENT_THUNDER, 0.25f, 2.0f);
                    cl.getWorld().spawnParticle(Particle.END_ROD,
                            cl.clone().add(0, 1, 0), 40, 0.2, 0.3, 0.2, 0.15);
                }
            }, tick);
        }

        // ══ 射線發射（t=12）══ 穿透白熱射線
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            this.nudge(p, -0.5, 0, 0); // 後座力

            final Location start = p.getEyeLocation();
            final Vector dir = start.getDirection().normalize();

            // 音效
            start.getWorld().playSound(start, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.75f, 0.8f);
            start.getWorld().playSound(start, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.5f, 2.0f);
            start.getWorld().playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.4f, 1.5f);

            // 起點閃光
            start.getWorld().spawnParticle(Particle.END_ROD,
                    start, 60, 0.15, 0.15, 0.15, 0.3);
            start.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    start, 80, 0.3, 0.3, 0.3, 0.5);

            // ── 射線逐幀延伸（6 tick 延伸 30 格）──
            final Set<LivingEntity> damaged = new HashSet<>();
            for (int frame = 0; frame < 6; frame++) {
                final int f = frame;
                this.plugin.getSafeScheduler().runRegionDelayed(start, task -> {
                    final double reach = (f + 1) * 5.0;
                    final double prevReach = f * 5.0;
                    // 主射線（白熱核心）
                    for (double d = prevReach; d <= reach; d += 0.3) {
                        final Location beamPos = start.clone().add(dir.clone().multiply(d));
                        beamPos.getWorld().spawnParticle(Particle.DUST, beamPos,
                                2, 0.05, 0.05, 0.05, 0, RAIL_WHITE);
                        beamPos.getWorld().spawnParticle(Particle.END_ROD, beamPos,
                                1, 0.02, 0.02, 0.02, 0.01);
                    }
                    // 外層光暈（藍色）
                    for (double d = prevReach; d <= reach; d += 0.6) {
                        final Location haloPos = start.clone().add(dir.clone().multiply(d));
                        haloPos.getWorld().spawnParticle(Particle.DUST, haloPos,
                                3, 0.15, 0.15, 0.15, 0, RAIL_BLUE);
                        haloPos.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, haloPos,
                                4, 0.2, 0.2, 0.2, 0.08);
                    }
                    // 沿路傷害
                    for (double d = prevReach; d <= reach; d += 1.0) {
                        final Location checkPos = start.clone().add(dir.clone().multiply(d));
                        for (final Entity e : checkPos.getWorld().getNearbyEntities(checkPos, 1.5, 1.5, 1.5)) {
                            if (e instanceof LivingEntity le && !e.equals(p) && damaged.add(le)) {
                                le.damage(baseDmg, p);
                                le.setVelocity(dir.clone().multiply(1.2).setY(0.3));
                                le.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                                        le.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.2);
                            }
                        }
                    }
                }, f);
            }

            // ── 終端爆炸（t+8）──
            this.plugin.getSafeScheduler().runRegionDelayed(start, task -> {
                final Location endPos = start.clone().add(dir.clone().multiply(30));
                this.shapes.animateShockRing(endPos, 0.5, 5.0, 8, 36, Particle.DUST, RAIL_CYAN);
                this.shapes.animateShockRing(endPos, 0.3, 3.0, 6, 20, Particle.ELECTRIC_SPARK, null);
                endPos.getWorld().spawnParticle(Particle.FIREWORK,
                        endPos, 50, 1.5, 1.5, 1.5, 0.4);
                endPos.getWorld().spawnParticle(Particle.END_ROD,
                        endPos, 40, 1.0, 1.0, 1.0, 0.2);
                endPos.getWorld().playSound(endPos, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f);
                // 終端 AoE
                for (final Entity e : endPos.getWorld().getNearbyEntities(endPos, 4, 3, 4)) {
                    if (e instanceof LivingEntity le && !e.equals(p) && damaged.add(le)) {
                        le.damage(baseDmg * 0.6, p);
                        final Vector kb = le.getLocation().toVector().subtract(endPos.toVector()).normalize();
                        le.setVelocity(kb.multiply(1.0).setY(0.5));
                    }
                }
            }, 8L);
        }, 12L);

        // ══ 殘電餘韻（t=24）══
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            final Location rem = p.getLocation();
            rem.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    rem.clone().add(0, 1, 0), 15, 1.0, 0.5, 1.0, 0.04);
            ParticleShapes.circleH(rem.clone().add(0, 0.1, 0), 2.0, 16, Particle.DUST, RAIL_VIOLET);
        }, 24L);
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
        loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
        loc.getWorld().playSound(loc, Sound.BLOCK_CONDUIT_ATTACK_TARGET, 0.6f, 1.2f);

        final double bonus = this.magicOrStrengthMultiplier(p, false);
        for (final Entity e : p.getNearbyEntities(10, 5, 10)) {
            if (e instanceof LivingEntity le && !e.equals(p)) {
                le.damage(6.0 * bonus, p);
                // 真正的麻痹：極高緩速 + 虛弱 + 跳躍阻止 + 挖掘疲勞
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 255, false, true));
                le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 5, false, true));
                le.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 128, false, true));
                le.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 5, false, true));
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
        impact.getWorld().playSound(impact, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 0.6f);

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
            impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.75f, 0.5f);
            impact.getWorld().playSound(impact, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.6f);
            final double bonus = this.magicOrStrengthMultiplier(p, true);
            for (final Entity e : impact.getWorld().getNearbyEntities(impact, 6, 5, 6)) {
                if (e instanceof LivingEntity le && !e.equals(p)) {
                    le.damage(30.0 * bonus, p);
                    le.setFireTicks(100);
                }
            }
        }, 42L);
    }

    /** RLR 時間凍結 — 冰晶球殼 + 15 格 AoE 凍結 5s，敵人脖子持續轉粒子。 */
    private void timeFreeze(final Player p) {
        final Location loc = p.getLocation().add(0, 1.5, 0);
        final Particle.DustOptions ice = ParticleShapes.dust(0xBFDBFE, 1.8f);

        ParticleShapes.sphereShell(loc, 4.5, 60, Particle.DUST, ice);
        ParticleShapes.sphereShell(loc, 4.0, 42, Particle.SNOWFLAKE, null);
        ParticleShapes.sphereShell(loc, 3.5, 28, Particle.WHITE_ASH, null);
        loc.getWorld().spawnParticle(Particle.INSTANT_EFFECT, loc, 30, 3.0, 2.0, 3.0, 0, new Particle.Spell(Color.fromRGB(0xBFDBFE), 1.0f));
        loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.5f, 0.4f);
        loc.getWorld().playSound(loc, Sound.BLOCK_POWDER_SNOW_STEP, 0.5f, 0.8f);

        final long durationTicks = 100L;  // 5s
        final long periodTicks = 2L;
        final Particle.DustOptions neckDust = ParticleShapes.dust(0xBFDBFE, 1.0f);

        for (final Entity e : p.getNearbyEntities(15, 8, 15)) {
            if (!(e instanceof LivingEntity le) || e.equals(p)) continue;

            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) durationTicks, 255, false, true));
            le.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, (int) durationTicks, 10, false, true));
            le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, (int) durationTicks, 10, false, true));
            le.setFreezeTicks(Math.max(le.getMaxFreezeTicks(), 140));

            final java.util.concurrent.atomic.AtomicInteger step = new java.util.concurrent.atomic.AtomicInteger();
            final long totalIters = durationTicks / periodTicks;
            this.plugin.getSafeScheduler().runEntityTimer(le, task -> {
                if (!le.isValid() || le.isDead() || step.get() >= totalIters) {
                    task.cancel();
                    return;
                }
                // 持續補凍結（非 powder snow 下會每 tick 遞減 2）
                le.setFreezeTicks(Math.max(le.getFreezeTicks(), le.getMaxFreezeTicks()));
                final int i = step.getAndIncrement();
                final double neckY = Math.max(0.8, le.getHeight() * 0.82);
                final Location neck = le.getLocation().add(0, neckY, 0);
                final double baseAngle = i * 0.55;  // 每 tick 旋轉
                final int ringPoints = 5;
                final double radius = 0.42;
                final World w = neck.getWorld();
                if (w == null) return;
                for (int k = 0; k < ringPoints; k++) {
                    final double ang = baseAngle + (2 * Math.PI * k / ringPoints);
                    final Location pt = neck.clone().add(Math.cos(ang) * radius, Math.sin(ang * 0.5) * 0.08, Math.sin(ang) * radius);
                    w.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, neckDust);
                    if (k == 0) {
                        w.spawnParticle(Particle.SNOWFLAKE, pt, 1, 0.02, 0.02, 0.02, 0.0);
                    }
                }
                // 偶發脖子裂冰聲
                if (i % 10 == 0) {
                    w.playSound(neck, Sound.BLOCK_GLASS_STEP, SoundCategory.PLAYERS, 0.25f, 1.6f);
                }
            }, 1L, periodTicks);
        }
    }

    /** LLR 元素裂變 — 漸變紫色穿透能量束（AoE 穿透）。 */
    private void elementalBeam(final Player p) {
        final Vector dir = p.getLocation().getDirection().normalize();
        // 從略低於眼睛的位置發射，確保能打到地面怪物
        final Location start = p.getEyeLocation().subtract(0, 0.3, 0);
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
            // 加大搜索範圍（2.5 垂直，2.0 水平）確保能命中地面怪物
            for (final Entity e : point.getWorld().getNearbyEntities(point, 2.0, 2.5, 2.0)) {
                if (e instanceof LivingEntity le && !e.equals(p) && hit.add(le)) {
                    le.damage(18.0 * bonus, p);
                    point.getWorld().spawnParticle(Particle.INSTANT_EFFECT,
                            le.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.1, new Particle.Spell(Color.fromRGB(0x8B5CF6), 1.0f));
                }
            }
        }
        start.getWorld().playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.4f);
    }

    /** RRL 量子複製 — 雙向 portal 漩渦 + Luck 30s。 */
    // ── 虛空色譜 ──
    private static final Particle.DustOptions VOID_DARK    = ParticleShapes.dust(0x1E1B4B, 2.0f);
    private static final Particle.DustOptions VOID_PURPLE  = ParticleShapes.dust(0x7C3AED, 1.6f);
    private static final Particle.DustOptions VOID_MAGENTA = ParticleShapes.dust(0xC026D3, 1.4f);
    private static final Particle.DustOptions VOID_WHITE   = ParticleShapes.dust(0xEDE9FE, 1.0f);

    /**
     * RRL 虛空坍縮 — 前方生成微型黑洞 → 吸引周圍敵人 → 坍縮爆炸。
     * 多階段：裂隙展開 → 黑洞旋轉吸引 → 壓縮 → 紫色爆炸新星。
     */
    private void voidCollapse(final Player p) {
        final double bonus = this.magicOrStrengthMultiplier(p, true);
        final double baseDmg = 20.0 * bonus;
        final Vector dir = p.getLocation().getDirection().setY(0).normalize();
        final Location center = p.getLocation().clone().add(dir.clone().multiply(8)).add(0, 1.5, 0);

        // ══ 裂隙展開（t=0-5）══ 空間撕裂效果
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 0.3f);
        for (int t = 0; t <= 5; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runRegionDelayed(center, task -> {
                final double prog = (double) tick / 5.0;
                // 裂隙擴張
                center.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                        center, (int) (10 + prog * 40), prog * 1.5, prog * 1.5, prog * 1.5, 0.1);
                center.getWorld().spawnParticle(Particle.DUST, center,
                        (int) (5 + prog * 15), prog, prog, prog, 0, VOID_DARK);
                if (tick == 2)
                    center.getWorld().playSound(center, Sound.ENTITY_ENDERMAN_SCREAM, 0.15f, 0.3f);
                if (tick == 4)
                    center.getWorld().playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 0.25f, 1.5f);
            }, tick);
        }

        // ══ 黑洞旋轉吸引（t=6-18, 12 tick）══
        for (int t = 0; t < 12; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runRegionDelayed(center, task -> {
                final double prog = (double) tick / 12.0;
                final double radius = 2.0 - prog * 1.2; // 逐漸縮小

                // 旋轉環
                final double spin = tick * Math.PI * 0.7;
                for (int arm = 0; arm < 6; arm++) {
                    final double a = spin + Math.PI * 2 * arm / 6;
                    final double r = radius + 0.3;
                    final Particle.DustOptions color = arm % 2 == 0 ? VOID_PURPLE : VOID_MAGENTA;
                    center.getWorld().spawnParticle(Particle.DUST,
                            center.clone().add(Math.cos(a) * r, Math.sin(a * 0.5) * 0.3, Math.sin(a) * r),
                            2, 0, 0, 0, 0, color);
                }
                // 核心暗球
                center.getWorld().spawnParticle(Particle.DUST, center,
                        8, 0.15, 0.15, 0.15, 0, VOID_DARK);
                center.getWorld().spawnParticle(Particle.SQUID_INK, center,
                        3, 0.2, 0.2, 0.2, 0.01);
                // 吸引碎片向心
                center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center,
                        15, radius + 1, radius + 1, radius + 1, 0.3);

                // 每 3 tick 吸引一次敵人
                if (tick % 3 == 0) {
                    for (final Entity e : center.getWorld().getNearbyEntities(center, 8, 4, 8)) {
                        if (e instanceof LivingEntity le && !e.equals(p)) {
                            final Vector pull = center.toVector().subtract(le.getLocation().toVector());
                            final double dist = pull.length();
                            if (dist > 0.5) {
                                le.setVelocity(pull.normalize().multiply(Math.min(1.2, 3.0 / dist)).setY(0.15));
                            }
                        }
                    }
                }
                // 吸引音效
                if (tick == 0) center.getWorld().playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 0.3f, 0.5f);
                if (tick == 4) center.getWorld().playSound(center, Sound.BLOCK_CONDUIT_AMBIENT, 0.4f, 0.4f);
                if (tick == 8) center.getWorld().playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 0.4f, 0.3f);
            }, 6 + tick);
        }

        // ══ 坍縮爆炸（t=20）══ 壓縮後紫色新星
        this.plugin.getSafeScheduler().runRegionDelayed(center, task -> {
            // 壓縮閃光
            center.getWorld().spawnParticle(Particle.END_ROD, center, 80, 0.1, 0.1, 0.1, 0.4);
            center.getWorld().playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.2f);
            center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.6f);
            center.getWorld().playSound(center, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 1.0f);
        }, 19L);

        this.plugin.getSafeScheduler().runRegionDelayed(center, task -> {
            // 新星爆炸
            this.shapes.animateShockRing(center, 0.5, 10.0, 10, 50, Particle.DUST, VOID_PURPLE);
            this.shapes.animateShockRing(center.clone().add(0, 0.3, 0),
                    0.3, 8.0, 8, 40, Particle.DUST, VOID_MAGENTA);
            this.shapes.animateShockRing(center.clone().add(0, -0.3, 0),
                    0.4, 6.0, 6, 30, Particle.DUST, VOID_WHITE);
            // 垂直柱
            this.shapes.animateLightPillar(center.clone().add(0, -1.5, 0),
                    0.8, 8.0, 10, 24, Particle.DUST, VOID_PURPLE);
            // 大量粒子
            center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center,
                    200, 3.0, 3.0, 3.0, 0.8);
            center.getWorld().spawnParticle(Particle.END_ROD, center,
                    60, 2.5, 2.5, 2.5, 0.3);
            center.getWorld().spawnParticle(Particle.FIREWORK, center,
                    40, 2.0, 2.0, 2.0, 0.4);
            // 爆炸傷害 + 擊飛
            for (final Entity e : center.getWorld().getNearbyEntities(center, 8, 4, 8)) {
                if (e instanceof LivingEntity le && !e.equals(p)) {
                    le.damage(baseDmg, p);
                    final Vector kb = le.getLocation().toVector().subtract(center.toVector());
                    if (kb.lengthSquared() > 0.01) {
                        le.setVelocity(kb.normalize().multiply(1.5).setY(0.7));
                    }
                    le.getWorld().spawnParticle(Particle.DUST,
                            le.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0, VOID_MAGENTA);
                }
            }
        }, 20L);

        // ══ 殘留虛空裂痕（t=28）══
        this.plugin.getSafeScheduler().runRegionDelayed(center, task -> {
            center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center,
                    30, 1.5, 1.5, 1.5, 0.02);
            ParticleShapes.circleH(center.clone().add(0, -1.3, 0), 2.0, 16, Particle.DUST, VOID_DARK);
            center.getWorld().playSound(center, Sound.BLOCK_PORTAL_AMBIENT, 0.2f, 2.0f);
        }, 28L);
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
                60, 3.0, 1.5, 3.0, 0, new Particle.Spell(Color.fromRGB(0xFFFFFF), 1.0f));
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 2.0f);

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
        loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.4f);

        // ABSORPTION 9 = 10 顆心 ≈ 50% HP 緩衝；RESISTANCE 2 ≈ 減傷 24%
        p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 400, 9, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 400, 2, false, true));
        for (final Entity e : p.getNearbyEntities(15, 8, 15)) {
            if (e instanceof Player ally) {
                ally.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 400, 9, false, true));
                ally.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 400, 2, false, true));
                final Location al = ally.getLocation().add(0, 1, 0);
                ParticleShapes.sphereShell(al, 1.0, 20, Particle.DUST, violet);
            }
        }
    }

    /** LLR 力量共鳴 — 金色光柱 + 隊友 30s Strength. */
    // ── 星爆色譜 ──
    private static final Particle.DustOptions STAR_GOLD    = ParticleShapes.dust(0xFCD34D, 1.8f);
    private static final Particle.DustOptions STAR_WHITE   = ParticleShapes.dust(0xFFFBEB, 2.0f);
    private static final Particle.DustOptions STAR_ROSE    = ParticleShapes.dust(0xFB7185, 1.4f);
    private static final Particle.DustOptions STAR_AQUA    = ParticleShapes.dust(0x67E8F9, 1.2f);
    private static final Particle.DustOptions STAR_AMBER   = ParticleShapes.dust(0xF59E0B, 1.6f);

    /**
     * LLR 星爆 — 聚集星光能量 → 結晶碎片向心匯聚 → 六芒星陣 → 爆炸新星。
     * 多階段：星光降臨 → 碎片螺旋收攏 → 六芒星閃爍 → 放射新星爆發。
     */
    private void starburst(final Player p) {
        final double bonus = this.magicOrStrengthMultiplier(p, true);
        final double baseDmg = 18.0 * bonus;
        final Location castLoc = p.getLocation().clone();

        // ══ 星光降臨（t=0-8）══ 星光碎片從天空落下匯聚
        p.getWorld().playSound(castLoc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 1.8f);
        p.getWorld().playSound(castLoc, Sound.BLOCK_BEACON_POWER_SELECT, 0.3f, 2.0f);

        for (int t = 0; t <= 8; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                if (!p.isOnline()) return;
                final double prog = (double) tick / 8.0;
                final Location cl = p.getLocation();
                // 向心螺旋碎片（從遠處收攏到身邊）
                final double spin = prog * Math.PI * 8;
                final double radius = 5.0 * (1.0 - prog) + 0.3;
                for (int arm = 0; arm < 8; arm++) {
                    final double a = spin + Math.PI * 2 * arm / 8;
                    final double yOff = 2.0 + Math.sin(a * 2) * 0.5;
                    final Particle.DustOptions color = arm % 3 == 0 ? STAR_GOLD
                            : arm % 3 == 1 ? STAR_ROSE : STAR_AQUA;
                    cl.getWorld().spawnParticle(Particle.DUST,
                            cl.clone().add(Math.cos(a) * radius, yOff, Math.sin(a) * radius),
                            2, 0, 0, 0, 0, color);
                    // 尾巴拖曳
                    if (prog > 0.3) {
                        final double trailA = a - 0.3;
                        final double trailR = radius * 1.2;
                        cl.getWorld().spawnParticle(Particle.END_ROD,
                                cl.clone().add(Math.cos(trailA) * trailR, yOff * 0.8, Math.sin(trailA) * trailR),
                                1, 0, 0, 0, 0.02);
                    }
                }
                // 中心逐漸發光
                cl.getWorld().spawnParticle(Particle.END_ROD,
                        cl.clone().add(0, 1.5, 0), (int) (prog * 20),
                        0.2, 0.3, 0.2, 0.01 + prog * 0.05);
                // 音效
                if (tick == 3) cl.getWorld().playSound(cl, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.4f, 1.6f);
                if (tick == 6) {
                    cl.getWorld().playSound(cl, Sound.ENTITY_PLAYER_LEVELUP, 0.2f, 2.0f);
                    cl.getWorld().playSound(cl, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 2.0f);
                }
            }, tick);
        }

        // ══ 六芒星陣（t=10）══ 腳下出現六芒星
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            final Location cl = p.getLocation();
            // 六芒星 = 兩個交疊正三角
            ParticleShapes.pentagram(cl.clone().add(0, 0.15, 0), 2.5, Particle.DUST, STAR_GOLD);
            ParticleShapes.circleH(cl.clone().add(0, 0.2, 0), 2.8, 30, Particle.DUST, STAR_AMBER);
            cl.getWorld().spawnParticle(Particle.ENCHANT, cl.clone().add(0, 1, 0),
                    60, 1.5, 1.0, 1.5, 1.0);
            cl.getWorld().playSound(cl, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.4f, 1.5f);

            // 星陣閃爍 2 次
            for (int flash = 0; flash < 2; flash++) {
                this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
                    if (!p.isOnline()) return;
                    final Location fl = p.getLocation();
                    fl.getWorld().spawnParticle(Particle.END_ROD,
                            fl.clone().add(0, 0.3, 0), 30, 2.5, 0.1, 2.5, 0.02);
                    fl.getWorld().spawnParticle(Particle.DUST,
                            fl.clone().add(0, 0.2, 0), 20, 2.0, 0.1, 2.0, 0, STAR_WHITE);
                }, flash * 3L);
            }
        }, 10L);

        // ══ 新星爆發（t=15）══ 放射狀結晶碎片
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            final Location cl = p.getLocation().add(0, 1, 0);

            // 音效
            cl.getWorld().playSound(cl, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.8f);
            cl.getWorld().playSound(cl, Sound.ITEM_TOTEM_USE, 0.3f, 1.5f);
            cl.getWorld().playSound(cl, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.25f, 1.6f);
            cl.getWorld().playSound(cl, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.5f, 0.8f);

            // 多色衝擊波
            this.shapes.animateShockRing(cl.clone().add(0, -0.8, 0),
                    0.5, 8.0, 8, 40, Particle.DUST, STAR_GOLD);
            this.shapes.animateShockRing(cl.clone().add(0, -0.6, 0),
                    0.8, 10.0, 10, 36, Particle.DUST, STAR_ROSE);
            this.shapes.animateShockRing(cl.clone().add(0, -0.4, 0),
                    1.0, 12.0, 12, 30, Particle.DUST, STAR_AQUA);

            // 12 道放射狀光線
            for (int ray = 0; ray < 12; ray++) {
                final double angle = Math.PI * 2 * ray / 12;
                final Particle.DustOptions rayColor = ray % 3 == 0 ? STAR_GOLD
                        : ray % 3 == 1 ? STAR_ROSE : STAR_AQUA;
                for (double d = 1.0; d <= 8.0; d += 0.4) {
                    final Location rayPos = cl.clone().add(
                            Math.cos(angle) * d, -0.5, Math.sin(angle) * d);
                    cl.getWorld().spawnParticle(Particle.DUST, rayPos,
                            1, 0.05, 0.05, 0.05, 0, rayColor);
                }
            }

            // 大量粒子
            cl.getWorld().spawnParticle(Particle.END_ROD, cl, 100, 2.0, 2.0, 2.0, 0.3);
            cl.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, cl, 50, 3.0, 2.0, 3.0, 0.2);
            cl.getWorld().spawnParticle(Particle.FIREWORK, cl, 40, 2.0, 1.5, 2.0, 0.3);

            // 傷害 + 擊飛
            for (final Entity e : cl.getWorld().getNearbyEntities(cl, 10, 5, 10)) {
                if (e instanceof LivingEntity le && !e.equals(p)) {
                    le.damage(baseDmg, p);
                    final Vector kb = le.getLocation().toVector().subtract(cl.toVector());
                    if (kb.lengthSquared() > 0.01) {
                        le.setVelocity(kb.normalize().multiply(1.2).setY(0.6));
                    }
                    le.getWorld().spawnParticle(Particle.END_ROD,
                            le.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.1);
                }
            }
        }, 15L);

        // ══ 餘光飄散（t=22）══
        this.plugin.getSafeScheduler().runEntityDelayed(p, () -> {
            if (!p.isOnline()) return;
            final Location rem = p.getLocation();
            rem.getWorld().spawnParticle(Particle.END_ROD,
                    rem.clone().add(0, 2, 0), 20, 4.0, 2.0, 4.0, 0.02);
            rem.getWorld().spawnParticle(Particle.CHERRY_LEAVES,
                    rem.clone().add(0, 3, 0), 15, 3.0, 1.5, 3.0, 0.01);
            rem.getWorld().playSound(rem, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, 2.0f);
        }, 22L);
    }

    /** RRL 傳送信標 — 第一次標記位置，第二次召集隊友到標記點。 */
    private void beaconCall(final Player p) {
        final UUID uuid = p.getUniqueId();
        final Location existing = this.beaconMarks.get(uuid);

        if (existing == null) {
            // ── 第一階段：標記位置 ──
            final Location loc = p.getLocation().clone();
            this.beaconMarks.put(uuid, loc);

            final Particle.DustOptions indigo = ParticleShapes.dust(0x6366F1, 1.8f);
            this.shapes.animateLightPillar(loc.clone().add(0, 0.1, 0),
                    1.2, 5.0, 20, 14, Particle.DUST, indigo);
            loc.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc.clone().add(0, 2.5, 0),
                    60, 0.8, 2.5, 0.8, 0.2);
            loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.8f);

            p.sendMessage(Component.text("  ✓ 信標已標記 — 再次施法召集隊友到此處",
                            TextColor.color(0xA5B4FC)).decoration(TextDecoration.ITALIC, false));

            // 5 分鐘後自動清除標記
            this.plugin.getSafeScheduler().runEntityDelayed(p, () -> this.beaconMarks.remove(uuid, loc), 6000L);
        } else {
            // ── 第二階段：召集隊友到標記點 ──
            this.beaconMarks.remove(uuid);
            final Particle.DustOptions indigo = ParticleShapes.dust(0x6366F1, 1.8f);

            this.shapes.animateLightPillar(existing.clone().add(0, 0.1, 0),
                    1.2, 5.0, 20, 14, Particle.DUST, indigo);
            this.shapes.animateLightPillar(existing.clone().add(0, 0.2, 0),
                    1.0, 5.0, 20, 10, Particle.END_ROD, null);
            existing.getWorld().spawnParticle(Particle.GLOW, existing.clone().add(0, 2, 0),
                    50, 1.2, 1.5, 1.2, 0.05);
            existing.getWorld().playSound(existing, Sound.BLOCK_CONDUIT_ACTIVATE, 0.5f, 1.4f);

            int teleported = 0;
            for (final Player ally : p.getWorld().getPlayers()) {
                if (ally.equals(p)) continue;
                if (ally.getLocation().distance(p.getLocation()) > 60) continue;
                final Location src = ally.getLocation().add(0, 1, 0);
                ParticleShapes.sphereShell(src, 1.2, 24, Particle.PORTAL, null);
                ally.teleportAsync(existing);
                teleported++;
            }
            // 施法者自己也傳送到標記點
            ParticleShapes.sphereShell(p.getLocation().add(0, 1, 0), 1.2, 24, Particle.PORTAL, null);
            p.teleportAsync(existing);

            p.sendMessage(Component.text("  ✓ 召集了 " + teleported + " 名隊友到信標位置",
                            TextColor.color(0xC4B5FD)).decoration(TextDecoration.ITALIC, false));
        }
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
