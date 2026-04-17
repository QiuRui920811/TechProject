package com.rui.techproject.service.talent;

import com.rui.techproject.TechMCPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * MagicSpells 風格的幾何粒子繪製 + 動畫工具箱。
 *
 * <p>設計重點：
 * <ul>
 *   <li>所有 spawn 都是 count=1、offset=0 的精準繪製，避免亂數飄散破壞幾何形狀。</li>
 *   <li>動畫方法以 SafeScheduler 把粒子分散到多個 tick，避免單 tick 撒幾百顆爆 TPS。</li>
 *   <li>顏色透過 {@link Particle.DustOptions} 控制，呼叫端只需傳入 RGB。</li>
 * </ul>
 */
public final class ParticleShapes {

    private final TechMCPlugin plugin;

    public ParticleShapes(final TechMCPlugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════
    //  靜態幾何（單 tick）
    // ═══════════════════════════════════════════

    /** 水平圓圈。 */
    public static void circleH(final Location center, final double radius, final int points,
                                final Particle particle, final Particle.DustOptions dust) {
        final World w = center.getWorld();
        if (w == null) return;
        for (int i = 0; i < points; i++) {
            final double angle = 2 * Math.PI * i / points;
            final Location p = center.clone().add(Math.cos(angle) * radius, 0, Math.sin(angle) * radius);
            if (dust != null) {
                w.spawnParticle(particle, p, 1, 0, 0, 0, 0, dust);
            } else {
                w.spawnParticle(particle, p, 1, 0, 0, 0, 0);
            }
        }
    }

    /** 垂直圓圈（朝向 direction 平面）。 */
    public static void circleV(final Location center, final double radius, final int points,
                                final Vector normal, final Particle particle,
                                final Particle.DustOptions dust) {
        final World w = center.getWorld();
        if (w == null) return;
        final Vector n = normal.clone().normalize();
        // 任選一個與 n 不平行的向量作 u
        Vector u = Math.abs(n.getY()) < 0.9 ? new Vector(0, 1, 0) : new Vector(1, 0, 0);
        u = u.subtract(n.clone().multiply(u.dot(n))).normalize();
        final Vector v = n.clone().crossProduct(u).normalize();
        for (int i = 0; i < points; i++) {
            final double angle = 2 * Math.PI * i / points;
            final double cos = Math.cos(angle) * radius;
            final double sin = Math.sin(angle) * radius;
            final Location p = center.clone().add(
                    u.getX() * cos + v.getX() * sin,
                    u.getY() * cos + v.getY() * sin,
                    u.getZ() * cos + v.getZ() * sin);
            if (dust != null) {
                w.spawnParticle(particle, p, 1, 0, 0, 0, 0, dust);
            } else {
                w.spawnParticle(particle, p, 1, 0, 0, 0, 0);
            }
        }
    }

    /** 球殼（fibonacci 均勻分佈）。 */
    public static void sphereShell(final Location center, final double radius, final int points,
                                    final Particle particle, final Particle.DustOptions dust) {
        final World w = center.getWorld();
        if (w == null) return;
        for (int i = 0; i < points; i++) {
            final double phi = Math.acos(1 - 2.0 * (i + 0.5) / points);
            final double theta = Math.PI * (1 + Math.sqrt(5)) * i;
            final double x = Math.sin(phi) * Math.cos(theta) * radius;
            final double y = Math.cos(phi) * radius;
            final double z = Math.sin(phi) * Math.sin(theta) * radius;
            final Location p = center.clone().add(x, y, z);
            if (dust != null) {
                w.spawnParticle(particle, p, 1, 0, 0, 0, 0, dust);
            } else {
                w.spawnParticle(particle, p, 1, 0, 0, 0, 0);
            }
        }
    }

    /** 直線粒子。 */
    public static void line(final Location from, final Location to, final double step,
                             final Particle particle, final Particle.DustOptions dust) {
        final World w = from.getWorld();
        if (w == null) return;
        final Vector dir = to.toVector().subtract(from.toVector());
        final double dist = dir.length();
        if (dist <= 0) return;
        dir.normalize();
        final int steps = (int) Math.min(200, dist / step);
        for (int i = 0; i <= steps; i++) {
            final Location p = from.clone().add(dir.clone().multiply(i * step));
            if (dust != null) {
                w.spawnParticle(particle, p, 1, 0, 0, 0, 0, dust);
            } else {
                w.spawnParticle(particle, p, 1, 0, 0, 0, 0);
            }
        }
    }

    /** 五芒星（地面平面）。 */
    public static void pentagram(final Location center, final double radius,
                                  final Particle particle, final Particle.DustOptions dust) {
        // 5 個頂點 (0, 144, 288, 72, 216)
        final int[] order = {0, 2, 4, 1, 3, 0};
        final Location[] pts = new Location[5];
        for (int i = 0; i < 5; i++) {
            final double angle = -Math.PI / 2 + 2 * Math.PI * i / 5;
            pts[i] = center.clone().add(Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius);
        }
        for (int i = 0; i < 5; i++) {
            line(pts[order[i]], pts[order[i + 1]], 0.2, particle, dust);
        }
        // 外圓
        circleH(center, radius, 40, particle, dust);
    }

    // ═══════════════════════════════════════════
    //  動畫（跨 tick，分散 TPS 負載）
    // ═══════════════════════════════════════════

    /**
     * 施法蓄力：粒子從外圍螺旋朝 caster 收攏，持續 ticks。
     * 用在主動技能施放前做 0.2~0.5 秒的前搖，讓使用者/旁人都能意識到「施法中」。
     */
    public void animateCharge(final Player caster, final int ticks,
                               final Particle particle, final Particle.DustOptions dust) {
        for (int t = 0; t < ticks; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runEntityDelayed(caster, () -> {
                if (!caster.isOnline()) return;
                final Location base = caster.getLocation();
                final World w = base.getWorld();
                if (w == null) return;
                final double progress = (double) tick / Math.max(1, ticks);
                final double radius = 2.8 * (1.0 - progress) + 0.2;
                final double y = 0.3 + progress * 1.4;
                final double spin = progress * Math.PI * 4;
                // 4 個對稱點做螺旋
                for (int i = 0; i < 4; i++) {
                    final double angle = 2 * Math.PI * i / 4 + spin;
                    final Location p = base.clone().add(
                            Math.cos(angle) * radius, y, Math.sin(angle) * radius);
                    if (dust != null) {
                        w.spawnParticle(particle, p, 1, 0, 0, 0, 0, dust);
                    } else {
                        w.spawnParticle(particle, p, 1, 0, 0, 0, 0);
                    }
                }
            }, t);
        }
    }

    /**
     * 擴張環（衝擊波）：從 startRadius 擴張到 endRadius，持續 ticks。
     */
    public void animateShockRing(final Location center, final double startR, final double endR,
                                  final int ticks, final int pointsPerFrame,
                                  final Particle particle, final Particle.DustOptions dust) {
        final Location frozen = center.clone();
        for (int t = 0; t < ticks; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runRegionDelayed(frozen, task -> {
                final double progress = (double) tick / Math.max(1, ticks - 1);
                final double r = startR + (endR - startR) * progress;
                circleH(frozen, r, pointsPerFrame, particle, dust);
            }, t);
        }
    }

    /**
     * 雙螺旋上升（buff 常用）：從 base 升到 base+height，持續 ticks。
     */
    public void animateDoubleHelix(final Location base, final double radius, final double height,
                                    final int turns, final int ticks,
                                    final Particle particle, final Particle.DustOptions dust) {
        final Location frozen = base.clone();
        for (int t = 0; t < ticks; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runRegionDelayed(frozen, task -> {
                final World w = frozen.getWorld();
                if (w == null) return;
                final double progress = (double) tick / Math.max(1, ticks - 1);
                final double y = progress * height;
                final double angle = progress * turns * 2 * Math.PI;
                for (int arm = 0; arm < 2; arm++) {
                    final double a = angle + arm * Math.PI;
                    final Location p = frozen.clone().add(
                            Math.cos(a) * radius, y, Math.sin(a) * radius);
                    if (dust != null) {
                        w.spawnParticle(particle, p, 1, 0, 0, 0, 0, dust);
                    } else {
                        w.spawnParticle(particle, p, 1, 0, 0, 0, 0);
                    }
                }
            }, t);
        }
    }

    /**
     * 垂直光柱：在 center 位置豎起 height 高的粒子柱，每 tick 從底往上漲一段。
     */
    public void animateLightPillar(final Location center, final double radius, final double height,
                                    final int ticks, final int pointsPerLevel,
                                    final Particle particle, final Particle.DustOptions dust) {
        final Location frozen = center.clone();
        for (int t = 0; t < ticks; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runRegionDelayed(frozen, task -> {
                final double y = (double) tick / Math.max(1, ticks - 1) * height;
                final Location lvl = frozen.clone().add(0, y, 0);
                circleH(lvl, radius, pointsPerLevel, particle, dust);
            }, t);
        }
    }

    // ═══════════════════════════════════════════
    //  高密度弧斬系統（MythicMobs 級精緻度）
    // ═══════════════════════════════════════════

    /**
     * 高密度水平弧線（靜態，單 tick）。
     * 以 center 為圓心，朝 yaw 方向繪製 arcDeg 度弧。
     * 粒子間距 step（0.1 = MythicMobs 級密度，每 0.1 格一顆）。
     */
    public static void denseArc(final Location center, final float yaw,
                                 final double radius, final double yOffset,
                                 final double arcDeg, final double step,
                                 final Particle particle, final Particle.DustOptions dust) {
        final World w = center.getWorld();
        if (w == null) return;
        final double yawRad = Math.toRadians(yaw);
        final double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);
        final double rx = Math.cos(yawRad), rz = Math.sin(yawRad);
        final double arcRad = Math.toRadians(arcDeg);
        final int points = Math.max(1, (int) (radius * arcRad / step));
        for (int i = 0; i <= points; i++) {
            final double angle = -arcRad / 2.0 + arcRad * i / points;
            final double side = radius * Math.sin(angle);
            final double fwd = radius * Math.cos(angle);
            final Location p = new Location(w,
                    center.getX() + rx * side + fx * fwd,
                    center.getY() + yOffset,
                    center.getZ() + rz * side + fz * fwd);
            if (dust != null) w.spawnParticle(particle, p, 1, 0, 0, 0, 0, dust);
            else w.spawnParticle(particle, p, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 高密度垂直半圓弧（靜態，單 tick）。
     * 在 center 處垂直於 yaw 方向繪製 180° 半圓。
     */
    public static void denseArcV(final Location center, final float yaw,
                                  final double radius, final double step,
                                  final Particle particle, final Particle.DustOptions dust) {
        final World w = center.getWorld();
        if (w == null) return;
        final double yawRad = Math.toRadians(yaw);
        final double rx = Math.cos(yawRad), rz = Math.sin(yawRad);
        final int points = Math.max(1, (int) (radius * Math.PI / step));
        for (int i = 0; i <= points; i++) {
            final double angle = Math.PI * i / points;
            final double h = radius * Math.cos(angle);
            final double v = radius * Math.sin(angle);
            final Location p = new Location(w,
                    center.getX() + rx * h,
                    center.getY() + v,
                    center.getZ() + rz * h);
            if (dust != null) w.spawnParticle(particle, p, 1, 0, 0, 0, 0, dust);
            else w.spawnParticle(particle, p, 1, 0, 0, 0, 0);
        }
    }

    /**
     * 動態雲霧掃描：cloud 粒子沿弧線逐 tick 掃過。
     * 模仿 MythicMobs 圓弧斬氣的逐幀掃描效果。
     */
    public void animateCloudSweep(final Location center, final float yaw,
                                   final double radius, final double yOffset,
                                   final double arcDeg, final int ticks,
                                   final int particlesPerTick) {
        final Location frozen = center.clone();
        final double yawRad = Math.toRadians(yaw);
        final double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);
        final double rx = Math.cos(yawRad), rz = Math.sin(yawRad);
        final double arcRad = Math.toRadians(arcDeg);
        final int total = ticks * particlesPerTick;
        for (int t = 0; t < ticks; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runRegionDelayed(frozen, task -> {
                final World w = frozen.getWorld();
                if (w == null) return;
                for (int j = 0; j < particlesPerTick; j++) {
                    final double progress = (double) (tick * particlesPerTick + j) / total;
                    final double angle = -arcRad / 2.0 + arcRad * progress;
                    final double side = radius * Math.sin(angle);
                    final double fwd = radius * Math.cos(angle);
                    w.spawnParticle(Particle.CLOUD, new Location(w,
                            frozen.getX() + rx * side + fx * fwd,
                            frozen.getY() + yOffset,
                            frozen.getZ() + rz * side + fz * fwd),
                            1, 0, 0, 0, 0.02);
                }
            }, t);
        }
    }

    /**
     * 高密度螺旋龍捲，每 tick 繪製多臂螺旋柱。
     */
    public void animateDenseTornado(final Location center, final double baseRadius,
                                     final double height, final int ticks,
                                     final int arms, final double step,
                                     final Particle particle, final Particle.DustOptions dust) {
        final Location frozen = center.clone();
        for (int t = 0; t < ticks; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runRegionDelayed(frozen, task -> {
                final World w = frozen.getWorld();
                if (w == null) return;
                final double spin = tick * 0.3;
                for (int arm = 0; arm < arms; arm++) {
                    final double armOff = arm * 2.0 * Math.PI / arms;
                    for (double y = 0; y <= height; y += step) {
                        final double yFrac = y / height;
                        final double r = baseRadius * (0.3 + 0.7 * Math.sin(yFrac * Math.PI));
                        final double angle = spin + armOff + yFrac * Math.PI * 6;
                        final Location p = frozen.clone().add(
                                Math.cos(angle) * r, y, Math.sin(angle) * r);
                        if (dust != null) w.spawnParticle(particle, p, 1, 0, 0, 0, 0, dust);
                        else w.spawnParticle(particle, p, 1, 0, 0, 0, 0);
                    }
                }
            }, t);
        }
    }

    /**
     * 高密度漣漪擴散，每個同心圓環以 step 間距繪製粒子。
     */
    public void animateDenseRipples(final Location center, final double maxRadius,
                                     final int waves, final int ticksPerWave,
                                     final double step,
                                     final Particle particle, final Particle.DustOptions dust) {
        final Location frozen = center.clone();
        for (int wave = 0; wave < waves; wave++) {
            final int waveDelay = wave * ticksPerWave;
            for (int t = 0; t < ticksPerWave; t++) {
                final int tick = t;
                this.plugin.getSafeScheduler().runRegionDelayed(frozen, task -> {
                    final double progress = (double) tick / Math.max(1, ticksPerWave - 1);
                    final double r = progress * maxRadius;
                    if (r < 0.1) return;
                    final int pts = Math.max(8, (int) (2 * Math.PI * r / step));
                    circleH(frozen.clone().add(0, 0.05, 0), r, pts, particle, dust);
                }, waveDelay + t);
            }
        }
    }



    /**
     * 弧線逐幀掃描：弧線在 ticks 內從一端逐漸繪製到另一端，前端有明亮「筆頭」。
     */
    public void animateArcSweep(final Location center, final float yaw,
                                 final double radius, final double yOffset,
                                 final double arcDeg, final double step,
                                 final int ticks,
                                 final Particle particle, final Particle.DustOptions dust,
                                 final Particle.DustOptions headDust) {
        final Location frozen = center.clone();
        final double yawRad = Math.toRadians(yaw);
        final double fx = -Math.sin(yawRad), fz = Math.cos(yawRad);
        final double rx = Math.cos(yawRad), rz = Math.sin(yawRad);
        final double arcRad = Math.toRadians(arcDeg);
        final int totalPoints = Math.max(1, (int) (radius * arcRad / step));
        for (int t = 0; t < ticks; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runRegionDelayed(frozen, task -> {
                final World w = frozen.getWorld();
                if (w == null) return;
                final int segStart = totalPoints * tick / ticks;
                final int segEnd = Math.min(totalPoints, totalPoints * (tick + 1) / ticks);
                for (int i = segStart; i <= segEnd; i++) {
                    final double angle = -arcRad / 2.0 + arcRad * ((double) i / totalPoints);
                    final double side = radius * Math.sin(angle);
                    final double fwd = radius * Math.cos(angle);
                    final Location p = new Location(w,
                            frozen.getX() + rx * side + fx * fwd,
                            frozen.getY() + yOffset,
                            frozen.getZ() + rz * side + fz * fwd);
                    final boolean isHead = i >= segEnd - 4;
                    final Particle.DustOptions d = isHead && headDust != null ? headDust : dust;
                    if (d != null) w.spawnParticle(particle, p, 1, 0, 0, 0, 0, d);
                    else w.spawnParticle(particle, p, 1, 0, 0, 0, 0);
                }
            }, t);
        }
    }

    /**
     * 閃電逐幀爬行：鋸齒閃電從 from 逐漸蔓延到 to，前端有電火花。
     */
    public void animateLightningCrawl(final Location from, final Location to,
                                       final int ticks, final double step,
                                       final Particle particle,
                                       final Particle.DustOptions dust,
                                       final Particle.DustOptions headDust) {
        final World w = from.getWorld();
        if (w == null) return;
        final Vector dir = to.toVector().subtract(from.toVector());
        final double dist = dir.length();
        if (dist <= 0) return;
        final Vector normDir = dir.clone().normalize();
        final Vector perp = Math.abs(normDir.getY()) < 0.9
                ? normDir.clone().crossProduct(new Vector(0, 1, 0)).normalize()
                : normDir.clone().crossProduct(new Vector(1, 0, 0)).normalize();
        final Vector perp2 = normDir.clone().crossProduct(perp).normalize();
        final double segLen = 0.5;
        final int segments = Math.max(1, (int) (dist / segLen));
        final Location[] path = new Location[segments + 1];
        path[0] = from.clone();
        for (int i = 1; i < segments; i++) {
            final double t = (double) i / segments;
            final double off1 = (Math.random() - 0.5) * 1.2;
            final double off2 = (Math.random() - 0.5) * 0.6;
            path[i] = from.clone().add(normDir.clone().multiply(t * dist))
                    .add(perp.clone().multiply(off1)).add(perp2.clone().multiply(off2));
        }
        path[segments] = to.clone();
        for (int t = 0; t < ticks; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runRegionDelayed(from.clone(), task -> {
                if (w.getPlayers().isEmpty()) return;
                final int segStart = segments * tick / ticks;
                final int segEnd = Math.min(segments, segments * (tick + 1) / ticks);
                for (int i = segStart; i < segEnd; i++) {
                    final boolean isHead = i >= segEnd - 2;
                    final Particle.DustOptions d = isHead && headDust != null ? headDust : dust;
                    line(path[i], path[i + 1], step, particle, d);
                }
                if (segEnd <= segments) {
                    w.spawnParticle(Particle.ELECTRIC_SPARK, path[Math.min(segEnd, segments)],
                            8, 0.12, 0.12, 0.12, 0.1);
                }
            }, t);
        }
    }

    /**
     * 閃電逐幀爬行（可控鋸齒幅度）：segLen 控制每段轉折長度，amplitude 控制左右偏移幅度。
     * 幅度越大，鋸齒越明顯（圖片級閃電效果建議 segLen=1.5~2.5, amplitude=2.0~3.5）。
     */
    public void animateLightningCrawl(final Location from, final Location to,
                                       final int ticks, final double step,
                                       final double segLen, final double amplitude,
                                       final Particle particle,
                                       final Particle.DustOptions dust,
                                       final Particle.DustOptions headDust) {
        final World w = from.getWorld();
        if (w == null) return;
        final Vector dir = to.toVector().subtract(from.toVector());
        final double dist = dir.length();
        if (dist <= 0) return;
        final Vector normDir = dir.clone().normalize();
        final Vector perp = Math.abs(normDir.getY()) < 0.9
                ? normDir.clone().crossProduct(new Vector(0, 1, 0)).normalize()
                : normDir.clone().crossProduct(new Vector(1, 0, 0)).normalize();
        final Vector perp2 = normDir.clone().crossProduct(perp).normalize();
        final int segments = Math.max(1, (int) (dist / segLen));
        final Location[] path = new Location[segments + 1];
        path[0] = from.clone();
        for (int i = 1; i < segments; i++) {
            final double t = (double) i / segments;
            final double off1 = (Math.random() - 0.5) * amplitude * 2;
            final double off2 = (Math.random() - 0.5) * amplitude * 0.5;
            path[i] = from.clone().add(normDir.clone().multiply(t * dist))
                    .add(perp.clone().multiply(off1)).add(perp2.clone().multiply(off2));
        }
        path[segments] = to.clone();
        for (int t = 0; t < ticks; t++) {
            final int tick = t;
            this.plugin.getSafeScheduler().runRegionDelayed(from.clone(), task -> {
                if (w.getPlayers().isEmpty()) return;
                final int segStart = segments * tick / ticks;
                final int segEnd = Math.min(segments, segments * (tick + 1) / ticks);
                for (int i = segStart; i < segEnd; i++) {
                    final boolean isHead = i >= segEnd - 2;
                    final Particle.DustOptions d = isHead && headDust != null ? headDust : dust;
                    line(path[i], path[i + 1], step, particle, d);
                }
                if (segEnd <= segments) {
                    w.spawnParticle(Particle.ELECTRIC_SPARK, path[Math.min(segEnd, segments)],
                            12, 0.15, 0.15, 0.15, 0.12);
                }
            }, t);
        }
    }

    /**
     * 閃電鋸齒線：在 from 到 to 之間繪製隨機鋸齒的閃電效果。
     */
    public static void lightningBolt(final Location from, final Location to,
                                      final double step,
                                      final Particle particle, final Particle.DustOptions dust) {
        final World w = from.getWorld();
        if (w == null) return;
        final Vector dir = to.toVector().subtract(from.toVector());
        final double dist = dir.length();
        if (dist <= 0) return;
        dir.normalize();
        final Vector perp = Math.abs(dir.getY()) < 0.9
                ? dir.clone().crossProduct(new Vector(0, 1, 0)).normalize()
                : dir.clone().crossProduct(new Vector(1, 0, 0)).normalize();
        final Vector perp2 = dir.clone().crossProduct(perp).normalize();

        Location current = from.clone();
        final double segLen = 0.5;
        final int segments = Math.max(1, (int) (dist / segLen));

        for (int i = 0; i < segments; i++) {
            Location next;
            if (i == segments - 1) {
                next = to.clone();
            } else {
                final double t = (i + 1.0) / segments;
                final double off1 = (Math.random() - 0.5) * 1.2;
                final double off2 = (Math.random() - 0.5) * 0.6;
                next = from.clone().add(dir.clone().multiply(t * dist))
                        .add(perp.clone().multiply(off1))
                        .add(perp2.clone().multiply(off2));
            }
            line(current, next, step, particle, dust);
            current = next;
        }
    }

    // ═══════════════════════════════════════════
    //  顏色 / Dust 便利方法
    // ═══════════════════════════════════════════

    public static Particle.DustOptions dust(final int rgb, final float size) {
        return new Particle.DustOptions(Color.fromRGB(rgb), size);
    }

    public static Particle.DustOptions dust(final int r, final int g, final int b, final float size) {
        return new Particle.DustOptions(Color.fromRGB(r, g, b), size);
    }
}
