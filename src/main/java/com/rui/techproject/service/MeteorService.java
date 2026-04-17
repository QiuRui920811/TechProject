package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.SafeScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * 流星系統 — 天空中定期飛過可見的流星，使用 BlockDisplay 實體組成。
 * <p>
 * 技術同噴射飛船（TravelVessel）：多個 BlockDisplay 組成流星本體，
 * 每 tick teleport 移動，沿途噴射火焰/熔岩粒子尾跡與低頻音效。
 * </p>
 */
public final class MeteorService {

    // ── 配置常數 ──────────────────────────────────────────────
    /** 最短生成間隔（tick）：2 分鐘 */
    private static final long MIN_INTERVAL_TICKS = 2400L;
    /** 最長生成間隔（tick）：8 分鐘 */
    private static final long MAX_INTERVAL_TICKS = 9600L;
    /** 流星高度範圍 */
    private static final double ALTITUDE_MIN = 150.0;
    private static final double ALTITUDE_MAX = 230.0;
    /** 每 tick 移動距離（方塊） */
    private static final double SPEED = 4.2;
    /** 飛行總 tick 數（約 4.5 秒）*/
    private static final int FLIGHT_TICKS = 90;
    /** 玩家可見範圍（方塊）*/
    private static final double VISIBILITY_RANGE = 260.0;
    /** 播放音效的最大距離 */
    private static final double SOUND_RANGE = 180.0;
    /** 碎片尾跡數量 */
    private static final int TAIL_FRAGMENT_COUNT = 5;
    /** 流星生成的最少線上人數 */
    private static final int MIN_ONLINE_PLAYERS = 1;
    /** 標籤用於清理 */
    private static final String METEOR_TAG = "techproject:meteor";
    /** 碰撞/墜落機率（百分比）*/
    private static final int CRASH_CHANCE_PERCENT = 12;

    // ── 記錄型別 ──────────────────────────────────────────────
    private record MeteorPart(BlockDisplay display, double offsetX, double offsetY, double offsetZ,
                              float tumbleSpeed) {
    }

    // ── 實例欄位 ──────────────────────────────────────────────
    private final TechMCPlugin plugin;
    private final SafeScheduler scheduler;
    private final ItemFactoryUtil itemFactory;
    private final Logger logger;
    private final Random random = new Random();

    /** 活動中的流星（每個由多個 BlockDisplay 組成）*/
    private final List<ActiveMeteor> activeMeteors = new CopyOnWriteArrayList<>();
    private long nextSpawnTick;
    private long globalTick;
    private boolean running;

    // ── 建構 ──────────────────────────────────────────────────
    public MeteorService(final TechMCPlugin plugin,
                         final SafeScheduler scheduler,
                         final ItemFactoryUtil itemFactory) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.itemFactory = itemFactory;
        this.logger = plugin.getLogger();
    }

    // ── 啟動 / 關閉 ──────────────────────────────────────────
    public void start() {
        this.running = true;
        this.nextSpawnTick = this.randomInterval();
        this.globalTick = 0;
        // 全域計時器 — 每 2 tick 檢查是否該生成 + tick 現有流星
        this.scheduler.runGlobalTimer(task -> {
            if (!this.running) {
                task.cancel();
                return;
            }
            this.globalTick += 2;
            this.tickAll();
            if (this.globalTick >= this.nextSpawnTick) {
                this.trySpawnMeteor();
                this.nextSpawnTick = this.globalTick + this.randomInterval();
            }
        }, 40L, 2L);
        this.logger.info("[MeteorService] 流星系統已啟動");
    }

    public void shutdown() {
        this.running = false;
        for (final ActiveMeteor meteor : this.activeMeteors) {
            this.cleanupMeteor(meteor);
        }
        this.activeMeteors.clear();
        // Folia: onDisable 在主控台線程，無法存取區域實體 (getEntitiesByClass 會拋異常)
        // 伺服器即將關閉，Display 實體會自動消失，不需要手動清理
        this.logger.info("[MeteorService] 流星系統已關閉");
    }

    // ── 核心邏輯 ──────────────────────────────────────────────

    private void trySpawnMeteor() {
        if (Bukkit.getOnlinePlayers().size() < MIN_ONLINE_PLAYERS) {
            return;
        }
        // 從線上玩家中隨機挑一位，以附近生成流星（確保有人看到）
        final List<Player> candidates = new ArrayList<>();
        for (final Player p : Bukkit.getOnlinePlayers()) {
            if (this.plugin.getPlanetService().isPlanetWorld(p.getWorld())) {
                candidates.add(p);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        final Player target = candidates.get(this.random.nextInt(candidates.size()));
        final World world = target.getWorld();
        final Location playerLoc = target.getLocation();

        // 計算軌跡：從玩家附近天空一端飛向另一端
        final double altitude = ALTITUDE_MIN + this.random.nextDouble() * (ALTITUDE_MAX - ALTITUDE_MIN);
        final double angle = this.random.nextDouble() * 2.0 * Math.PI; // 隨機飛行方向
        final double halfRange = SPEED * FLIGHT_TICKS * 0.5;

        // 起點：在玩家上空偏移方向的反向
        final double startX = playerLoc.getX() - Math.cos(angle) * halfRange + (this.random.nextDouble() - 0.5) * 60.0;
        final double startZ = playerLoc.getZ() - Math.sin(angle) * halfRange + (this.random.nextDouble() - 0.5) * 60.0;
        final Location startLoc = new Location(world, startX, altitude, startZ);

        // 方向向量
        final Vector direction = new Vector(
                Math.cos(angle) * SPEED,
                -0.15 - this.random.nextDouble() * 0.25, // 微微向下（流星角度）
                Math.sin(angle) * SPEED
        );

        // 決定是否墜落
        final boolean willCrash = this.random.nextInt(100) < CRASH_CHANCE_PERCENT;

        // 在起點區域生成實體
        this.scheduler.runRegion(startLoc, regionTask -> {
            final List<MeteorPart> parts = this.spawnMeteorParts(world, startLoc);
            if (parts.isEmpty()) {
                return;
            }
            final ActiveMeteor meteor = new ActiveMeteor(parts, startLoc.clone(), direction.clone(),
                    FLIGHT_TICKS, 0, willCrash, world);
            this.activeMeteors.add(meteor);
        });
    }

    private List<MeteorPart> spawnMeteorParts(final World world, final Location center) {
        final List<MeteorPart> parts = new ArrayList<>();
        // ── 核心：發光的岩漿塊 ──
        parts.add(this.spawnPart(world, center, "core", 0.0, 0.0, 0.0,
                Material.MAGMA_BLOCK, 1.6F, 1.4F, 1.6F, 0.0F));
        // ── 內殼：哭泣黑曜石 ──
        parts.add(this.spawnPart(world, center, "shellTop", 0.0, 0.72, 0.0,
                Material.CRYING_OBSIDIAN, 1.1F, 0.8F, 1.1F, 0.12F));
        parts.add(this.spawnPart(world, center, "shellLeft", -0.7, -0.1, 0.2,
                Material.CRYING_OBSIDIAN, 0.8F, 0.9F, 0.7F, 0.08F));
        parts.add(this.spawnPart(world, center, "shellRight", 0.65, 0.05, -0.15,
                Material.NETHERRACK, 0.7F, 0.75F, 0.8F, 0.1F));
        // ── 表面碎片 ──
        parts.add(this.spawnPart(world, center, "fragmentA", -0.4, 0.55, 0.5,
                Material.BLACKSTONE, 0.45F, 0.4F, 0.5F, 0.18F));
        parts.add(this.spawnPart(world, center, "fragmentB", 0.5, -0.4, -0.55,
                Material.BASALT, 0.5F, 0.35F, 0.4F, 0.22F));
        parts.add(this.spawnPart(world, center, "fragmentC", 0.2, 0.85, -0.35,
                Material.DEEPSLATE, 0.35F, 0.3F, 0.45F, 0.15F));
        // ── 發光核心（海晶燈，超亮） ──
        parts.add(this.spawnPart(world, center, "glow", 0.0, 0.1, 0.0,
                Material.SEA_LANTERN, 0.9F, 0.85F, 0.9F, 0.0F));
        // ── 尾跡碎片（拖在後方） ──
        for (int i = 0; i < TAIL_FRAGMENT_COUNT; i++) {
            final double tailOffset = -(1.8 + i * 1.4);
            final double spread = (this.random.nextDouble() - 0.5) * 0.6;
            final double spreadY = (this.random.nextDouble() - 0.5) * 0.4;
            final float size = 0.3F + this.random.nextFloat() * 0.35F;
            final Material tailMat = i % 2 == 0 ? Material.MAGMA_BLOCK : Material.NETHERRACK;
            parts.add(this.spawnPart(world, center, "tail" + i,
                    spread, spreadY, tailOffset,
                    tailMat, size, size * 0.8F, size, 0.25F + this.random.nextFloat() * 0.15F));
        }
        return parts;
    }

    private MeteorPart spawnPart(final World world,
                                 final Location center,
                                 final String role,
                                 final double ox, final double oy, final double oz,
                                 final Material material,
                                 final float scaleX, final float scaleY, final float scaleZ,
                                 final float tumbleSpeed) {
        final Location loc = center.clone().add(ox, oy, oz);
        final BlockDisplay display = world.spawn(loc, BlockDisplay.class, spawned -> {
            spawned.addScoreboardTag(METEOR_TAG);
            spawned.setBlock(material.createBlockData());
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setInterpolationDelay(0);
            spawned.setInterpolationDuration(2);
            spawned.setTeleportDuration(2);
            spawned.setViewRange(6.0F); // 非常遠可見
            spawned.setShadowRadius(0.0F);
            spawned.setShadowStrength(0.0F);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTransformation(new Transformation(
                    new Vector3f(-0.5F * scaleX, -0.5F * scaleY, -0.5F * scaleZ),
                    new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F),
                    new Vector3f(scaleX, scaleY, scaleZ),
                    new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F)
            ));
        });
        return new MeteorPart(display, ox, oy, oz, tumbleSpeed);
    }

    /** 每個 tick 更新所有活動中流星的位置與特效 */
    private void tickAll() {
        for (final ActiveMeteor meteor : this.activeMeteors) {
            if (meteor.finished()) {
                this.onMeteorEnd(meteor);
                this.cleanupMeteor(meteor);
            } else {
                this.tickMeteor(meteor);
                meteor.advance();
            }
        }
        this.activeMeteors.removeIf(ActiveMeteor::finished);
    }

    private void tickMeteor(final ActiveMeteor meteor) {
        final int tick = meteor.currentTick;
        final double progress = (double) tick / meteor.totalTicks;
        final Location currentCenter = meteor.currentPosition();
        final World world = meteor.world;
        if (world == null) {
            return;
        }

        // Folia: 世界操作必須在區域線程執行，從全域線程排程到流星目前位置的區域
        this.scheduler.runRegion(currentCenter, task -> {
            // ── 移動所有 BlockDisplay 部件 ──
            final double tumbleAngle = tick * 0.14;
            for (final MeteorPart part : meteor.parts) {
                if (part.display() == null || !part.display().isValid()) {
                    continue;
                }
                final double tumble = part.tumbleSpeed() * tumbleAngle;
                final double rx = part.offsetX() * Math.cos(tumble) - part.offsetZ() * Math.sin(tumble);
                final double rz = part.offsetX() * Math.sin(tumble) + part.offsetZ() * Math.cos(tumble);
                final double ry = part.offsetY() + Math.sin(tumbleAngle * part.tumbleSpeed() * 1.7) * 0.08;

                final Location partLoc = currentCenter.clone().add(rx, ry, rz);
                final BlockDisplay display = part.display();
                if (display.isValid()) {
                    display.teleportAsync(partLoc);
                }
            }

            // ── 粒子尾跡 ──
            this.spawnTrailParticles(world, currentCenter, meteor.direction, tick, progress);

            // ── 音效：對附近玩家播放 ──
            if (tick % 4 == 0) {
                this.playMeteorSounds(world, currentCenter, tick, progress);
            }

            // ── Title 提示：第一次靠近玩家時 ──
            if (tick == 10) {
                this.notifyNearbyPlayers(world, currentCenter);
            }
        });
    }

    private void spawnTrailParticles(final World world,
                                     final Location center,
                                     final Vector direction,
                                     final int tick,
                                     final double progress) {
        // 火焰核心
        world.spawnParticle(Particle.LAVA, center, 8, 0.6, 0.4, 0.6, 0.0);
        world.spawnParticle(Particle.FLAME, center, 14, 0.8, 0.5, 0.8, 0.02);

        // 高溫粉塵
        final Particle.DustOptions hotCore = new Particle.DustOptions(Color.fromRGB(255, 140, 20), 2.8F);
        world.spawnParticle(Particle.DUST, center, 10, 0.5, 0.3, 0.5, 0.0, hotCore);

        final Particle.DustOptions hotGlow = new Particle.DustOptions(Color.fromRGB(255, 220, 80), 2.0F);
        world.spawnParticle(Particle.DUST, center, 6, 0.3, 0.2, 0.3, 0.0, hotGlow);

        // 煙霧尾跡（沿飛行反方向擴散）
        final Location trailBack = center.clone().subtract(direction.clone().normalize().multiply(2.5));
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, trailBack, 5, 0.8, 0.6, 0.8, 0.01);
        world.spawnParticle(Particle.SMOKE, trailBack, 12, 1.2, 0.8, 1.2, 0.03);

        // 長尾火花
        final Location farTrail = center.clone().subtract(direction.clone().normalize().multiply(5.0));
        world.spawnParticle(Particle.END_ROD, farTrail, 4, 1.5, 1.0, 1.5, 0.01);

        // 碎屑火花
        if (tick % 3 == 0) {
            world.spawnParticle(Particle.LAVA, trailBack, 3, 1.4, 0.6, 1.4, 0.0);
        }

        // 大氣衝擊波紋（每 5 tick）
        if (tick % 5 == 0) {
            final Particle.DustTransition shockwave = new Particle.DustTransition(
                    Color.fromRGB(255, 180, 60),
                    Color.fromRGB(180, 80, 20),
                    3.5F
            );
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center, 18, 2.0, 1.5, 2.0, 0.0, shockwave);
        }

        // 進入中後段：加大尾焰
        if (progress > 0.3) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, trailBack, 6, 1.0, 0.5, 1.0, 0.02);
        }
        if (progress > 0.6) {
            final Location deepTrail = center.clone().subtract(direction.clone().normalize().multiply(8.0));
            world.spawnParticle(Particle.SMOKE, deepTrail, 8, 2.0, 1.2, 2.0, 0.04);
            world.spawnParticle(Particle.ASH, center, 6, 1.5, 1.0, 1.5, 0.02);
        }
    }

    private void playMeteorSounds(final World world,
                                  final Location center,
                                  final int tick,
                                  final double progress) {
        for (final Player player : world.getPlayers()) {
            final double dist = player.getLocation().distance(center);
            if (dist > SOUND_RANGE) {
                continue;
            }
            final float volume = (float) Math.max(0.05, 0.8 - dist / SOUND_RANGE * 0.7);
            // 低頻轟鳴
            player.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT,
                    volume * 0.35F, 0.42F + (float) (progress * 0.18F));
            // 火焰呼嘯
            player.playSound(center, Sound.ITEM_FIRECHARGE_USE, SoundCategory.AMBIENT,
                    volume * 0.5F, 0.6F + (float) (progress * 0.2F));
            // 風切聲
            if (tick % 8 == 0) {
                player.playSound(center, Sound.ITEM_ELYTRA_FLYING, SoundCategory.AMBIENT,
                        volume * 0.4F, 0.5F + (float) (progress * 0.3F));
            }
            // 撞擊前加大
            if (progress > 0.7 && tick % 4 == 0) {
                player.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.AMBIENT,
                        volume * 0.15F, 1.6F);
            }
        }
    }

    private void notifyNearbyPlayers(final World world, final Location center) {
        for (final Player player : world.getPlayers()) {
            final double dist = player.getLocation().distance(center);
            if (dist > VISIBILITY_RANGE) {
                continue;
            }
            this.plugin.getTitleMsgService().send(player,
                    Component.empty(),
                    this.itemFactory.muted("☄ 一顆流星正劃過天際……"),
                    48L, Sound.BLOCK_NOTE_BLOCK_HAT);
        }
    }

    /** 流星結束時的效果 — 墜落爆炸或消散 */
    private void onMeteorEnd(final ActiveMeteor meteor) {
        final Location endPos = meteor.currentPosition();
        final World world = meteor.world;
        if (world == null) {
            return;
        }

        this.scheduler.runRegion(endPos, task -> {
            if (meteor.willCrash) {
                this.spawnCrashEffect(world, endPos);
            } else {
                this.spawnFadeEffect(world, endPos);
            }
        });
    }

    /** 墜落爆炸特效（不破壞地形） */
    private void spawnCrashEffect(final World world, final Location pos) {
        // 找地面座標
        final Location ground = world.getHighestBlockAt(pos).getLocation().add(0.5, 1.0, 0.5);
        final Location impactPoint = ground.getY() < pos.getY() ? ground : pos;

        // 巨大爆炸粒子
        world.spawnParticle(Particle.EXPLOSION, impactPoint, 6, 2.0, 1.5, 2.0, 0.0);
        world.spawnParticle(Particle.LAVA, impactPoint, 40, 3.0, 2.0, 3.0, 0.0);
        world.spawnParticle(Particle.FLAME, impactPoint, 60, 4.0, 3.0, 4.0, 0.05);
        world.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, impactPoint, 25, 3.0, 4.0, 3.0, 0.02);

        final Particle.DustOptions crashDust = new Particle.DustOptions(Color.fromRGB(255, 100, 0), 4.0F);
        world.spawnParticle(Particle.DUST, impactPoint, 30, 4.0, 2.5, 4.0, 0.0, crashDust);

        // 餘波環
        final Particle.DustTransition shockRing = new Particle.DustTransition(
                Color.fromRGB(255, 200, 50),
                Color.fromRGB(120, 40, 0),
                5.0F
        );
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, impactPoint, 50, 6.0, 0.5, 6.0, 0.0, shockRing);

        // 撞擊音效
        world.playSound(impactPoint, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.AMBIENT, 1.2F, 0.5F);
        world.playSound(impactPoint, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.AMBIENT, 0.8F, 0.6F);
        world.playSound(impactPoint, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, SoundCategory.AMBIENT, 0.6F, 0.72F);
        world.playSound(impactPoint, Sound.ITEM_TRIDENT_THUNDER, SoundCategory.AMBIENT, 0.5F, 0.4F);

        // 通知附近玩家
        for (final Player player : world.getPlayers()) {
            if (player.getLocation().distance(impactPoint) <= VISIBILITY_RANGE) {
                this.plugin.getTitleMsgService().send(player,
                        this.itemFactory.primary("☄ 流星墜落！"),
                        this.itemFactory.secondary("一顆流星在附近墜落了——快去看看！"),
                        64L, Sound.BLOCK_NOTE_BLOCK_BASS);
                player.sendMessage(this.itemFactory.secondary("✦ 一顆流星在 " +
                        impactPoint.getBlockX() + ", " + impactPoint.getBlockY() + ", " + impactPoint.getBlockZ() +
                        " 附近墜落！可能殘留了珍貴的碎片……"));
            }
        }

        // 延遲生成殘留碎片掉落（作為獎勵線索） — 小型發光標記
        this.scheduler.runRegionDelayed(impactPoint, delayTask -> {
            this.spawnCrashMarker(world, impactPoint);
        }, 10L);
    }

    /** 在墜落點生成發光標記（短暫存在的裝飾） */
    private void spawnCrashMarker(final World world, final Location pos) {
        // 殘留火焰粒子持續 6 秒
        for (int i = 0; i < 6; i++) {
            final int delay = i * 20;
            this.scheduler.runRegionDelayed(pos, t -> {
                if (world.isChunkLoaded(pos.getBlockX() >> 4, pos.getBlockZ() >> 4)) {
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, pos, 8, 1.5, 0.5, 1.5, 0.01);
                    world.spawnParticle(Particle.SMOKE, pos, 5, 1.0, 0.8, 1.0, 0.02);
                    world.spawnParticle(Particle.LAVA, pos, 3, 0.8, 0.3, 0.8, 0.0);
                }
            }, delay);
        }
    }

    /** 流星消散（沒有墜落） */
    private void spawnFadeEffect(final World world, final Location pos) {
        world.spawnParticle(Particle.END_ROD, pos, 20, 2.0, 1.5, 2.0, 0.03);
        world.spawnParticle(Particle.SMOKE, pos, 15, 2.5, 1.5, 2.5, 0.04);
        world.spawnParticle(Particle.ASH, pos, 10, 3.0, 2.0, 3.0, 0.02);
        final Particle.DustOptions fade = new Particle.DustOptions(Color.fromRGB(200, 120, 40), 2.0F);
        world.spawnParticle(Particle.DUST, pos, 12, 2.0, 1.0, 2.0, 0.0, fade);
    }

    private void cleanupMeteor(final ActiveMeteor meteor) {
        for (final MeteorPart part : meteor.parts) {
            if (part.display() != null && part.display().isValid()) {
                this.scheduler.runEntity(part.display(), () -> {
                    if (part.display().isValid()) {
                        part.display().remove();
                    }
                });
            }
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────

    private long randomInterval() {
        return MIN_INTERVAL_TICKS + (long) (this.random.nextDouble() * (MAX_INTERVAL_TICKS - MIN_INTERVAL_TICKS));
    }

    // ── 活動流星實體 ────────────────────────────────────────────

    private static final class ActiveMeteor {
        final List<MeteorPart> parts;
        final Location start;
        final Vector direction;
        final int totalTicks;
        final boolean willCrash;
        final World world;
        int currentTick;

        ActiveMeteor(final List<MeteorPart> parts,
                     final Location start,
                     final Vector direction,
                     final int totalTicks,
                     final int currentTick,
                     final boolean willCrash,
                     final World world) {
            this.parts = parts;
            this.start = start;
            this.direction = direction;
            this.totalTicks = totalTicks;
            this.currentTick = currentTick;
            this.willCrash = willCrash;
            this.world = world;
        }

        Location currentPosition() {
            return this.start.clone().add(this.direction.clone().multiply(this.currentTick));
        }

        boolean finished() {
            return this.currentTick >= this.totalTicks;
        }

        void advance() {
            this.currentTick++;
        }
    }
}
