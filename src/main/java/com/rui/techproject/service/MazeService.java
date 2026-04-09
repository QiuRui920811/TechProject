package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.SafeScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 迷途星（Labyrinth）專屬服務：迷宮牆壁移動、挖掘動畫、任務、Boss 管理。
 */
public final class MazeService {

    // ─── 迷宮常數 ───
    private static final int MAZE_CELL_SIZE = 9;
    private static final int MAZE_HALF_EXTENT = 490;
    private static final int GLADE_HALF = 50;
    private static final int WALL_HEIGHT = 100;
    private static final int FLOOR_Y = 64;
    private static final long WALL_SHIFT_INTERVAL_TICKS = 20L * 300L; // 5 分鐘
    private static final long WALL_SHIFT_WARNING_TICKS = 20L * 30L;   // 提前 30 秒預警
    private static final int WALLS_PER_SHIFT = 18;
    private static final int SAFE_RADIUS_SQ = 3 * 3; // 不能在玩家 3 格內關閉牆壁
    private static final int WALL_ANIM_TICKS = 60;     // 牆壁升降動畫 3 秒
    private static final String WALL_ANIM_TAG = "techproject:maze_wall_anim";

    // ─── 挖掘動畫 ───
    private static final String MINING_STAND_TAG = "techproject:maze_mining";
    private static final long MINING_DURATION_TICKS = 30L; // 1.5 秒
    private static final double[] PICKAXE_ANGLES = {
        -0.4, -0.8, -1.2, -0.8, -0.4, 0.0  // 揮動循環
    };

    // ─── Boss ───
    private static final String BOSS_TAG = "techproject:maze_boss";
    private static final String BOSS_NAME = "§5§l迷宮魔像";
    private static final double BOSS_HEALTH = 200.0;
    private static final double BOSS_DAMAGE = 12.0;
    private static final long BOSS_COOLDOWN_MS = 30L * 60L * 1000L; // 30 分鐘

    // ─── 任務 ───
    private static final String QUEST_TAG_PREFIX = "techproject:maze_quest_";

    private final TechProjectPlugin plugin;
    private final SafeScheduler scheduler;
    private final ItemFactoryUtil itemFactory;

    /** 被動態切換的牆壁 (worldX:worldZ → 是否為牆) */
    private final Set<Long> shiftedWallsOpen = ConcurrentHashMap.newKeySet();
    private final Set<Long> shiftedWallsClosed = ConcurrentHashMap.newKeySet();
    /** 延遲警告中的牆壁位置 */
    private final Set<Long> pendingShiftWalls = ConcurrentHashMap.newKeySet();

    /** 挖掘動畫：玩家 UUID → ArmorStand UUID */
    private final Map<UUID, UUID> activeMiningAnimations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> miningExpiry = new ConcurrentHashMap<>();

    /** Boss 冷卻 */
    private final Map<UUID, Long> bossCooldowns = new ConcurrentHashMap<>();

    /** 任務狀態：玩家 UUID → MazeQuest */
    private final Map<UUID, MazeQuest> activeQuests = new ConcurrentHashMap<>();

    private long lastShiftTick = 0L;
    private long wallShiftWarningTick = 0L;
    private final Random random = new Random();

    public MazeService(final TechProjectPlugin plugin,
                       final SafeScheduler scheduler,
                       final ItemFactoryUtil itemFactory) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.itemFactory = itemFactory;
    }

    public void startTimers() {
        // 迷宮牆壁移動 tick
        this.scheduler.runGlobalTimer(task -> this.tickMazeWalls(), 100L, 20L);
        // 挖掘動畫清理 tick
        this.scheduler.runGlobalTimer(task -> this.tickMiningAnimations(), 60L, 5L);
    }

    // ═══════════════════════════════════════
    //  迷宮牆壁移動系統
    // ═══════════════════════════════════════

    private void tickMazeWalls() {
        final World world = Bukkit.getWorld(PlanetService.LABYRINTH_WORLD);
        if (world == null || world.getPlayers().isEmpty()) {
            return;
        }
        final long currentTick = world.getGameTime();

        // 預警階段
        if (this.wallShiftWarningTick > 0 && currentTick >= this.wallShiftWarningTick) {
            this.showWallShiftWarning(world);
            this.wallShiftWarningTick = 0;
        }

        // 移動時機
        if (this.lastShiftTick == 0L) {
            this.lastShiftTick = currentTick;
            return;
        }
        if (currentTick - this.lastShiftTick < WALL_SHIFT_INTERVAL_TICKS) {
            // 發出預警
            if (this.wallShiftWarningTick == 0 && currentTick - this.lastShiftTick >= WALL_SHIFT_INTERVAL_TICKS - WALL_SHIFT_WARNING_TICKS) {
                this.wallShiftWarningTick = currentTick + WALL_SHIFT_WARNING_TICKS - 20L;
                this.prepareWallShift(world);
            }
            return;
        }

        this.lastShiftTick = currentTick;
        this.executeWallShift(world);
    }

    private void prepareWallShift(final World world) {
        this.pendingShiftWalls.clear();
        final long seed = world.getSeed() ^ ((long) "labyrinth".hashCode() << 32);
        final ThreadLocalRandom rng = ThreadLocalRandom.current();
        final int halfCells = MAZE_HALF_EXTENT / MAZE_CELL_SIZE;
        final int gladeCells = GLADE_HALF / MAZE_CELL_SIZE + 1;

        int count = 0;
        int attempts = 0;
        while (count < WALLS_PER_SHIFT && attempts < WALLS_PER_SHIFT * 10) {
            attempts++;
            final int cellX = rng.nextInt(-halfCells, halfCells);
            final int cellZ = rng.nextInt(-halfCells, halfCells);
            if (Math.abs(cellX) <= gladeCells && Math.abs(cellZ) <= gladeCells) {
                continue; // The Glade 區域
            }
            final int dir = rng.nextInt(2); // 0=X border, 1=Z border
            final int wallWorldX;
            final int wallWorldZ;
            if (dir == 0) {
                wallWorldX = cellX * MAZE_CELL_SIZE - MAZE_HALF_EXTENT;
                wallWorldZ = cellZ * MAZE_CELL_SIZE - MAZE_HALF_EXTENT + rng.nextInt(1, MAZE_CELL_SIZE);
            } else {
                wallWorldX = cellX * MAZE_CELL_SIZE - MAZE_HALF_EXTENT + rng.nextInt(1, MAZE_CELL_SIZE);
                wallWorldZ = cellZ * MAZE_CELL_SIZE - MAZE_HALF_EXTENT;
            }
            this.pendingShiftWalls.add(packCoord(wallWorldX, wallWorldZ));
            count++;
        }

        // 發送預警訊息給世界中的玩家
        for (final Player player : world.getPlayers()) {
            player.sendActionBar(this.itemFactory.warning("⚠ 迷宮結構即將重組..."));
            player.playSound(player.getLocation(), Sound.BLOCK_SCULK_CATALYST_BLOOM, SoundCategory.AMBIENT, 0.8f, 0.6f);
        }
    }

    private void showWallShiftWarning(final World world) {
        // 在即將改變的牆壁位置顯示粒子
        for (final long packed : this.pendingShiftWalls) {
            final int wx = unpackX(packed);
            final int wz = unpackZ(packed);
            final Location loc = new Location(world, wx + 0.5, FLOOR_Y + 50, wz + 0.5);
            if (loc.isChunkLoaded()) {
                world.spawnParticle(Particle.SCULK_SOUL, loc, 20, 0.3, 40.0, 0.3, 0.02);
            }
        }
    }

    private void executeWallShift(final World world) {
        final long seed = world.getSeed() ^ ((long) "labyrinth".hashCode() << 32);

        for (final long packed : this.pendingShiftWalls) {
            final int wx = unpackX(packed);
            final int wz = unpackZ(packed);
            final Location wallBase = new Location(world, wx, FLOOR_Y + 1, wz);

            if (!wallBase.isChunkLoaded()) {
                continue;
            }

            // 檢查附近是否有玩家，避免困住
            boolean playerNearby = false;
            for (final Player player : world.getPlayers()) {
                final double distSq = player.getLocation().distanceSquared(wallBase);
                if (distSq < SAFE_RADIUS_SQ) {
                    playerNearby = true;
                    break;
                }
            }

            final boolean currentlyWall = wallBase.getBlock().getType() == Material.DEEPSLATE_BRICKS
                    || wallBase.getBlock().getType() == Material.CHISELED_DEEPSLATE;

            if (currentlyWall) {
                this.animateWallFall(world, wx, wz);
            } else if (!playerNearby) {
                this.animateWallRise(world, wx, wz);
            }
        }
        this.pendingShiftWalls.clear();

        // 通知玩家
        for (final Player player : world.getPlayers()) {
            player.sendActionBar(this.itemFactory.warning("迷宮結構已重組！小心新的通道與死路。"));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, SoundCategory.AMBIENT, 0.45f, 1.4f);
        }
    }

    /**
     * 牆壁升起動畫：用 BlockDisplay 從地下平滑滑出。
     */
    private void animateWallRise(final World world, final int wx, final int wz) {
        final Location base = new Location(world, wx, FLOOR_Y + 1, wz);
        this.scheduler.runRegion(base, task -> {
            final BlockDisplay display = world.spawn(base, BlockDisplay.class, bd -> {
                bd.setBlock(Material.DEEPSLATE_BRICKS.createBlockData());
                bd.setPersistent(false);
                bd.setGravity(false);
                bd.setInvulnerable(true);
                bd.setInterpolationDelay(-1);
                bd.setInterpolationDuration(WALL_ANIM_TICKS);
                bd.setTeleportDuration(0);
                bd.addScoreboardTag(WALL_ANIM_TAG);
                bd.setTransformation(new Transformation(
                        new Vector3f(0.0f, (float) -WALL_HEIGHT, 0.0f),
                        new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f),
                        new Vector3f(1.0f, (float) WALL_HEIGHT, 1.0f),
                        new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f)));
            });

            // 下一 tick 設定目標 transformation（觸發插值動畫）
            this.scheduler.runRegionDelayed(base, t2 -> {
                if (display.isValid()) {
                    display.setInterpolationDelay(0);
                    display.setTransformation(new Transformation(
                            new Vector3f(0.0f, 0.0f, 0.0f),
                            new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f),
                            new Vector3f(1.0f, (float) WALL_HEIGHT, 1.0f),
                            new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f)));
                }
            }, 1L);

            // 升起音效
            world.playSound(base, Sound.ENTITY_WARDEN_EMERGE, SoundCategory.BLOCKS, 0.8f, 0.5f);
            world.playSound(base, Sound.BLOCK_DEEPSLATE_BRICKS_PLACE, SoundCategory.BLOCKS, 1.2f, 0.6f);
            world.spawnParticle(Particle.BLOCK, base.clone().add(0.5, 0, 0.5), 40,
                    0.3, 0.5, 0.3, 0.05, Material.DEEPSLATE_BRICKS.createBlockData());

            // 動畫結束後放置實際方塊並移除 display
            this.scheduler.runRegionDelayed(base, t3 -> {
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + WALL_HEIGHT; y++) {
                    final Material mat;
                    final int relY = y - FLOOR_Y;
                    if (relY == WALL_HEIGHT) {
                        mat = Material.CHISELED_DEEPSLATE;
                    } else if (relY >= WALL_HEIGHT - 4) {
                        mat = Material.POLISHED_DEEPSLATE;
                    } else if (relY <= 5) {
                        mat = Material.DEEPSLATE;
                    } else {
                        mat = Material.DEEPSLATE_BRICKS;
                    }
                    world.getBlockAt(wx, y, wz).setType(mat, false);
                }
                if (display.isValid()) {
                    display.remove();
                }
                this.shiftedWallsClosed.add(packCoord(wx, wz));
                this.shiftedWallsOpen.remove(packCoord(wx, wz));
            }, WALL_ANIM_TICKS + 5L);
        });
    }

    /**
     * 牆壁沉降動畫：先移除實際方塊，用 BlockDisplay 平滑沉入地下。
     */
    private void animateWallFall(final World world, final int wx, final int wz) {
        final Location base = new Location(world, wx, FLOOR_Y + 1, wz);
        this.scheduler.runRegion(base, task -> {
            // 先移除實際方塊
            for (int y = FLOOR_Y + 1; y <= FLOOR_Y + WALL_HEIGHT + 1; y++) {
                world.getBlockAt(wx, y, wz).setType(Material.AIR, false);
            }

            final BlockDisplay display = world.spawn(base, BlockDisplay.class, bd -> {
                bd.setBlock(Material.DEEPSLATE_BRICKS.createBlockData());
                bd.setPersistent(false);
                bd.setGravity(false);
                bd.setInvulnerable(true);
                bd.setInterpolationDelay(-1);
                bd.setInterpolationDuration(WALL_ANIM_TICKS);
                bd.setTeleportDuration(0);
                bd.addScoreboardTag(WALL_ANIM_TAG);
                bd.setTransformation(new Transformation(
                        new Vector3f(0.0f, 0.0f, 0.0f),
                        new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f),
                        new Vector3f(1.0f, (float) WALL_HEIGHT, 1.0f),
                        new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f)));
            });

            // 下一 tick 設定沉降目標
            this.scheduler.runRegionDelayed(base, t2 -> {
                if (display.isValid()) {
                    display.setInterpolationDelay(0);
                    display.setTransformation(new Transformation(
                            new Vector3f(0.0f, (float) -WALL_HEIGHT, 0.0f),
                            new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f),
                            new Vector3f(1.0f, (float) WALL_HEIGHT, 1.0f),
                            new AxisAngle4f(0.0f, 0.0f, 1.0f, 0.0f)));
                }
            }, 1L);

            // 沉降音效 + 粒子
            world.playSound(base, Sound.BLOCK_DEEPSLATE_BRICKS_BREAK, SoundCategory.BLOCKS, 1.2f, 0.5f);
            world.playSound(base, Sound.ENTITY_WARDEN_EMERGE, SoundCategory.BLOCKS, 0.6f, 1.4f);
            world.spawnParticle(Particle.BLOCK, base.clone().add(0.5, 50, 0.5), 80,
                    0.4, 40.0, 0.4, 0.1, Material.DEEPSLATE_BRICKS.createBlockData());

            // 動畫結束後移除 display
            this.scheduler.runRegionDelayed(base, t3 -> {
                if (display.isValid()) {
                    display.remove();
                }
                this.shiftedWallsOpen.add(packCoord(wx, wz));
                this.shiftedWallsClosed.remove(packCoord(wx, wz));
            }, WALL_ANIM_TICKS + 5L);
        });
    }

    // ═══════════════════════════════════════
    //  挖掘動畫系統 (稿子實體)
    // ═══════════════════════════════════════

    /**
     * 在資源節點位置播放稿子挖掘動畫。
     */
    public void playMiningAnimation(final Player player, final Block block) {
        if (player == null || block == null) {
            return;
        }
        // 移除舊動畫
        this.cancelMiningAnimation(player);

        final Location spawnLoc = block.getLocation().add(0.5, 0.0, 0.5);
        final World world = block.getWorld();

        this.scheduler.runRegion(spawnLoc, task -> {
            final ArmorStand stand = world.spawn(spawnLoc, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setGravity(false);
                as.setSmall(true);
                as.setMarker(true);
                as.setInvulnerable(true);
                as.addScoreboardTag(MINING_STAND_TAG);
                as.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_PICKAXE));
                as.setRightArmPose(new EulerAngle(Math.toRadians(-30), 0, 0));
                as.setPersistent(false);
            });

            final UUID standId = stand.getUniqueId();
            this.activeMiningAnimations.put(player.getUniqueId(), standId);
            this.miningExpiry.put(player.getUniqueId(), System.currentTimeMillis() + MINING_DURATION_TICKS * 50L);

            // 揮動動畫：每 5 tick 改變手臂角度
            for (int frame = 0; frame < PICKAXE_ANGLES.length; frame++) {
                final int f = frame;
                this.scheduler.runRegionDelayed(spawnLoc, t -> {
                    final Entity entity = Bukkit.getEntity(standId);
                    if (entity instanceof ArmorStand as && as.isValid()) {
                        as.setRightArmPose(new EulerAngle(PICKAXE_ANGLES[f], 0, 0));
                        // 方塊破壞粒子
                        world.spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5),
                                6, 0.25, 0.25, 0.25, 0.05, block.getBlockData());
                        world.playSound(block.getLocation(), Sound.BLOCK_STONE_HIT, SoundCategory.BLOCKS, 0.6f, 1.2f);
                    }
                }, 5L * frame);
            }

            // 結束時移除
            this.scheduler.runRegionDelayed(spawnLoc, t -> {
                final Entity entity = Bukkit.getEntity(standId);
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
                this.activeMiningAnimations.remove(player.getUniqueId());
                this.miningExpiry.remove(player.getUniqueId());
            }, MINING_DURATION_TICKS);
        });
    }

    public void cancelMiningAnimation(final Player player) {
        final UUID standId = this.activeMiningAnimations.remove(player.getUniqueId());
        this.miningExpiry.remove(player.getUniqueId());
        if (standId != null) {
            final Entity entity = Bukkit.getEntity(standId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
    }

    private void tickMiningAnimations() {
        final long now = System.currentTimeMillis();
        this.miningExpiry.entrySet().removeIf(entry -> {
            if (now > entry.getValue()) {
                final UUID standId = this.activeMiningAnimations.remove(entry.getKey());
                if (standId != null) {
                    final Entity entity = Bukkit.getEntity(standId);
                    if (entity != null && entity.isValid()) {
                        entity.remove();
                    }
                }
                return true;
            }
            return false;
        });
    }

    // ═══════════════════════════════════════
    //  任務系統
    // ═══════════════════════════════════════

    public enum QuestType {
        KILL_MOBS("消滅迷宮怪物", 15),
        COLLECT_FRAGMENTS("採集迷宮碎片", 8),
        REACH_CENTER("抵達迷宮核心", 1),
        DEFEAT_ELITE("擊敗迷宮守衛者", 1),
        EXPLORE_ZONES("探索迷宮區域", 3);

        final String displayName;
        final int targetCount;

        QuestType(final String displayName, final int targetCount) {
            this.displayName = displayName;
            this.targetCount = targetCount;
        }
    }

    public static final class MazeQuest {
        private final QuestType type;
        private int progress;
        private final long startTime;

        MazeQuest(final QuestType type) {
            this.type = type;
            this.progress = 0;
            this.startTime = System.currentTimeMillis();
        }

        public QuestType type() { return this.type; }
        public int progress() { return this.progress; }
        public int target() { return this.type.targetCount; }
        public boolean isComplete() { return this.progress >= this.type.targetCount; }

        public void increment(final int amount) {
            this.progress = Math.min(this.progress + amount, this.type.targetCount);
        }
    }

    /**
     * 給玩家分配一個隨機迷宮任務。
     */
    public void assignRandomQuest(final Player player) {
        if (this.activeQuests.containsKey(player.getUniqueId())) {
            player.sendMessage(this.itemFactory.warning("你已有進行中的迷宮任務。使用 /maze quest 查看進度。"));
            return;
        }
        final QuestType[] types = QuestType.values();
        final QuestType type = types[ThreadLocalRandom.current().nextInt(types.length)];
        final MazeQuest quest = new MazeQuest(type);
        this.activeQuests.put(player.getUniqueId(), quest);
        player.sendMessage(Component.text("⬡ ", NamedTextColor.DARK_AQUA)
                .append(Component.text("迷宮任務：", NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(type.displayName + " (0/" + type.targetCount + ")", NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.2f);
    }

    public MazeQuest getActiveQuest(final Player player) {
        return this.activeQuests.get(player.getUniqueId());
    }

    /**
     * 推進任務進度。
     */
    public void advanceQuest(final Player player, final QuestType type, final int amount) {
        final MazeQuest quest = this.activeQuests.get(player.getUniqueId());
        if (quest == null || quest.type != type) {
            return;
        }
        quest.increment(amount);
        if (quest.isComplete()) {
            this.completeQuest(player, quest);
        } else {
            player.sendActionBar(this.itemFactory.info(
                    quest.type.displayName + "  §e" + quest.progress + "§7/§e" + quest.target()));
        }
    }

    private void completeQuest(final Player player, final MazeQuest quest) {
        this.activeQuests.remove(player.getUniqueId());
        player.sendMessage(Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text("迷宮任務完成！", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text(" " + quest.type.displayName, NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.8f, 1.0f);

        // 獎勵
        this.giveQuestReward(player, quest.type);
    }

    private void giveQuestReward(final Player player, final QuestType type) {
        final ItemStack reward = switch (type) {
            case KILL_MOBS -> this.itemFactory.createTechItem("labyrinth_fragment", 3);
            case COLLECT_FRAGMENTS -> this.itemFactory.createTechItem("maze_vine", 4);
            case REACH_CENTER, EXPLORE_ZONES -> this.itemFactory.createTechItem("labyrinth_relic", 1);
            case DEFEAT_ELITE -> this.itemFactory.createTechItem("guardian_core", 1);
        };
        if (reward != null) {
            final var remaining = player.getInventory().addItem(reward);
            remaining.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
    }

    // ═══════════════════════════════════════
    //  Boss 系統
    // ═══════════════════════════════════════

    /**
     * 在迷宮中心生成 Boss。
     */
    public void trySpawnBoss(final Player summoner) {
        final long now = System.currentTimeMillis();
        final Long lastSpawn = this.bossCooldowns.get(summoner.getUniqueId());
        if (lastSpawn != null && now - lastSpawn < BOSS_COOLDOWN_MS) {
            final long remaining = (BOSS_COOLDOWN_MS - (now - lastSpawn)) / 60000L;
            summoner.sendMessage(this.itemFactory.warning("迷宮魔像尚在休眠，約 " + remaining + " 分鐘後可再次召喚。"));
            return;
        }

        final World world = Bukkit.getWorld(PlanetService.LABYRINTH_WORLD);
        if (world == null) {
            return;
        }

        // 检查中心附近是否已有 Boss
        final Location center = new Location(world, 0.5, FLOOR_Y + 1, 0.5);
        for (final Entity entity : world.getNearbyEntities(center, 20, 10, 20)) {
            if (entity.getScoreboardTags().contains(BOSS_TAG)) {
                summoner.sendMessage(this.itemFactory.warning("迷宮魔像已經存在！"));
                return;
            }
        }

        this.bossCooldowns.put(summoner.getUniqueId(), now);

        this.scheduler.runRegion(center, task -> {
            final org.bukkit.entity.IronGolem golem = world.spawn(center, org.bukkit.entity.IronGolem.class, boss -> {
                boss.customName(Component.text(BOSS_NAME));
                boss.setCustomNameVisible(true);
                boss.addScoreboardTag(BOSS_TAG);
                boss.setPersistent(true);
                boss.setRemoveWhenFarAway(false);
                boss.setPlayerCreated(false);

                // 強化屬性
                final var healthAttr = boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (healthAttr != null) {
                    healthAttr.setBaseValue(BOSS_HEALTH);
                    boss.setHealth(BOSS_HEALTH);
                }
                final var attackAttr = boss.getAttribute(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
                if (attackAttr != null) {
                    attackAttr.setBaseValue(BOSS_DAMAGE);
                }
                final var knockbackAttr = boss.getAttribute(org.bukkit.attribute.Attribute.ATTACK_KNOCKBACK);
                if (knockbackAttr != null) {
                    knockbackAttr.setBaseValue(2.5);
                }
            });

            // 通知所有玩家
            for (final Player player : world.getPlayers()) {
                player.sendMessage(Component.text("⚠ ", NamedTextColor.DARK_RED)
                        .append(Component.text("迷宮魔像", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                        .append(Component.text(" 已在迷宮中心甦醒！", NamedTextColor.RED)));
                player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 1.0f, 0.6f);
            }
        });
    }

    /**
     * Boss 死亡時呼叫 → 獎勵掉落。
     */
    public void onBossDeath(final LivingEntity boss, final Player killer) {
        if (!boss.getScoreboardTags().contains(BOSS_TAG)) {
            return;
        }
        final Location loc = boss.getLocation();

        // 掉落獎勵
        final String[] rewards = { "guardian_core", "labyrinth_relic", "maze_blade" };
        for (final String rewardId : rewards) {
            final ItemStack item = this.itemFactory.createTechItem(rewardId, 1);
            if (item != null) {
                loc.getWorld().dropItemNaturally(loc, item);
            }
        }

        // 通知
        for (final Player player : loc.getWorld().getPlayers()) {
            player.sendMessage(Component.text("✦ ", NamedTextColor.GOLD)
                    .append(Component.text("迷宮魔像", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD))
                    .append(Component.text(" 已被擊敗！", NamedTextColor.GREEN)));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0f, 0.8f);
        }

        // 推進任務
        if (killer != null) {
            this.advanceQuest(killer, QuestType.DEFEAT_ELITE, 1);
        }
    }

    // ═══════════════════════════════════════
    //  迷宮區域判定
    // ═══════════════════════════════════════

    /**
     * 判定世界座標屬於哪個迷宮區域。
     * @return 0=中心, 1=內圈, 2=中圈, 3=外圈, -1=迷宮外
     */
    public static int getMazeZone(final int worldX, final int worldZ) {
        final int dist = Math.max(Math.abs(worldX), Math.abs(worldZ));
        if (dist > MAZE_HALF_EXTENT) {
            return -1;
        }
        if (dist <= GLADE_HALF) {
            return 0;
        }
        final int cellDist = dist / MAZE_CELL_SIZE;
        if (cellDist <= 20) {
            return 1; // 內圈
        }
        if (cellDist <= 40) {
            return 2; // 中圈
        }
        return 3; // 外圈
    }

    /**
     * 檢查玩家是否在迷宮中心區域（用於 REACH_CENTER 任務）。
     */
    public void checkCenterReach(final Player player) {
        final World world = player.getWorld();
        if (!PlanetService.LABYRINTH_WORLD.equals(world.getName())) {
            return;
        }
        final int zone = getMazeZone(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (zone == 0) {
            this.advanceQuest(player, QuestType.REACH_CENTER, 1);
        }
    }

    /**
     * 怪物擊殺推進任務。
     */
    public void onMobKill(final Player killer) {
        this.advanceQuest(killer, QuestType.KILL_MOBS, 1);
    }

    /**
     * 採集推進任務。
     */
    public void onCollect(final Player player) {
        this.advanceQuest(player, QuestType.COLLECT_FRAGMENTS, 1);
    }

    // ─── 工具方法 ───

    private static long packCoord(final int x, final int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int unpackX(final long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(final long packed) {
        return (int) packed;
    }

    public boolean isLabyrinthWorld(final World world) {
        return world != null && PlanetService.LABYRINTH_WORLD.equals(world.getName());
    }
}
