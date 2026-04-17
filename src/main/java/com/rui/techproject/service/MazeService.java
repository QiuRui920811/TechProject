package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.SafeScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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
    private static final int MAZE_HALF_EXTENT = 1000; // 匹配世界邊界半徑
    private static final int GLADE_HALF = 50;
    private static final int WALL_HEIGHT = 100;
    private static final int FLOOR_Y = 64;
    private static final long WALL_SHIFT_INTERVAL_TICKS = 20L * 300L; // 5 分鐘
    private static final long WALL_SHIFT_WARNING_TICKS = 20L * 30L;   // 提前 30 秒預警
    private static final int WALLS_PER_SHIFT = 18;
    private static final int SAFE_RADIUS_SQ = 3 * 3; // 不能在玩家 3 格內關閉牆壁
    private static final int WALL_ANIM_TICKS = 60;     // 牆壁升降動畫 3 秒
    private static final String WALL_ANIM_TAG = "techproject:maze_wall_anim";

    // ─── 門系統：BigDoor 雙邊滑動 ───
    private static final long GATE_INTERVAL_TICKS = 20L * 600L;  // 10 分鐘
    private static final long GATE_OPEN_DURATION_TICKS = 20L * 30L; // 30 秒
    private static final int GATE_WALL_THICKNESS = 3;  // 圍牆厚度 3 格 (|coord|=48,49,50)
    private static final int GATE_WIDTH = 20;          // 門寬 20 格（每半邊 10 格）
    private static final int GATE_HALF_WIDTH = GATE_WIDTH / 2; // 10
    private static final int GATE_OPENING_HEIGHT = 30; // 門高 30 格
    private static final int GATE_SLIDE_DISTANCE = GATE_HALF_WIDTH; // 每半邊滑出 10 格（剛好清空門洞）
    private static final int GATE_ANIM_TICKS = 120;    // 滑動動畫 6 秒
    private static final String GATE_TEXT_TAG = "techproject:maze_gate_text";
    private static final String GATE_SLIDE_TAG = "techproject:maze_gate_slide";
    // 漂浮文字色彩 — 使用柔和 hex 色，避開過於鮮艷的 NamedTextColor.RED
    private static final TextColor GATE_CLOSED_COLOR = TextColor.color(0xE8A36A); // 琥珀橙（低飽和）
    private static final TextColor GATE_OPEN_COLOR = TextColor.color(0x8FE3B4);   // 薄荷青（柔和）
    /** Glade 四面牆方向：+Z(S), -Z(N), +X(E), -X(W) */
    private static final int[][] GATE_DIRECTIONS = {
        {0,  1},  // South
        {0, -1},  // North
        {1,  0},  // East
        {-1, 0},  // West
    };
    private static final String[] GATE_DIRECTION_NAMES = {"南", "北", "東", "西"};

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

    private final TechMCPlugin plugin;
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

    /** 門狀態 */
    private boolean gatesOpen = false;
    private int openWallIdx = -1; // 當前開啟的牆壁索引（對應 GATE_DIRECTIONS）, -1 = 全關
    private long lastGateOpenTick = 0L;
    private long gateOpenEndTick = 0L;
    private final List<UUID> gateTextDisplays = new ArrayList<>();
    private final List<UUID> activeGateSlideDisplays = new ArrayList<>();
    /** 當前開啟牆壁的方塊快照，用於關門時復原：key = "x:y:z"。 */
    private final Map<String, org.bukkit.block.data.BlockData> gateSnapshot = new ConcurrentHashMap<>();
    /** 追蹤玩家上一個 tick 是否在 Glade 內部，用於門口穿越偵測。 */
    private final Map<UUID, Boolean> playerInGlade = new ConcurrentHashMap<>();

    // ─── 中央撤離點（原電梯改造）───
    private static final int EXTRACTION_TRIGGER_Y = 65;    // 玩家腳下方塊 Y=64，腳掌位置 Y=65
    private static final long EXTRACTION_COOLDOWN_MS = 15_000L;
    private static final int EXTRACTION_STASH_SIZE = 54;   // 虛擬倉庫 6 行
    // 冒險者拾取物倉庫桶位置（與 PlanetService.buildAncientElevatorColumn 同步）
    private static final int STASH_BARREL_X = 3;
    private static final int STASH_BARREL_Y = 65;
    private static final int STASH_BARREL_Z = 3;
    private final Map<UUID, Long> extractionCooldowns = new ConcurrentHashMap<>();
    private final Set<UUID> extractingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Location> extractionReturnPoints = new ConcurrentHashMap<>();
    /** 冒險者拾取物虛擬倉庫：玩家在迷宮世界撿到的物品都會進這裡，撤離時一次給回。 */
    private final Map<UUID, List<ItemStack>> adventurerStash = new ConcurrentHashMap<>();

    public MazeService(final TechMCPlugin plugin,
                       final SafeScheduler scheduler,
                       final ItemFactoryUtil itemFactory) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.itemFactory = itemFactory;
    }

    private World resolveLabyrinthWorld() {
        final PlanetService ps = this.plugin.getPlanetService();
        return ps != null ? ps.findPlanetWorld("labyrinth") : null;
    }

    public void startTimers() {
        // 迷宮牆壁移動 tick
        this.scheduler.runGlobalTimer(task -> this.tickMazeWalls(), 100L, 20L);
        // 挖掘動畫清理 tick
        this.scheduler.runGlobalTimer(task -> this.tickMiningAnimations(), 60L, 5L);
        // 門計時器 tick
        this.scheduler.runGlobalTimer(task -> this.tickGladeGates(), 80L, 20L);
    }

    // ═══════════════════════════════════════
    //  迷宮牆壁移動系統
    // ═══════════════════════════════════════

    private void tickMazeWalls() {
        final World world = this.resolveLabyrinthWorld();
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
        for (final long packed : this.pendingShiftWalls) {
            final int wx = unpackX(packed);
            final int wz = unpackZ(packed);
            final Location wallBase = new Location(world, wx, FLOOR_Y + 1, wz);

            if (!wallBase.isChunkLoaded()) {
                continue;
            }

            // 方塊讀寫需在 region thread 上
            this.scheduler.runRegion(wallBase, task -> {
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
            });
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
                // 修正大尺寸縮放時的破圖：固定亮度，繞過 entity 單點光取樣
                bd.setBrightness(new Display.Brightness(15, 15));
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
                // 修正大尺寸縮放時的破圖：固定亮度，繞過 entity 單點光取樣
                bd.setBrightness(new Display.Brightness(15, 15));
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
            player.sendActionBar(this.itemFactory.warning(
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

        // 新增：共鳴技能經驗 + 引導鏈推進
        if (this.plugin.getSkillService() != null) {
            this.plugin.getSkillService().grantXp(player, "resonance", 80L);
        }
        if (this.plugin.getTutorialChainService() != null) {
            this.plugin.getTutorialChainService().onMazeQuestComplete(player);
        }
    }

    private void giveQuestReward(final Player player, final QuestType type) {
        final String itemId = switch (type) {
            case KILL_MOBS -> "tech_labyrinth_fragment";
            case COLLECT_FRAGMENTS -> "tech_maze_vine";
            case REACH_CENTER, EXPLORE_ZONES -> "tech_labyrinth_relic";
            case DEFEAT_ELITE -> "tech_guardian_core";
        };
        final int amount = switch (type) {
            case KILL_MOBS -> 3;
            case COLLECT_FRAGMENTS -> 4;
            default -> 1;
        };
        ItemStack reward = this.itemFactory.tryBuildNexoItemPublic(itemId);
        if (reward != null) { reward.setAmount(Math.min(amount, reward.getMaxStackSize())); }
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

        final World world = this.resolveLabyrinthWorld();
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
                player.sendMessage(Component.text("⚠ ", TextColor.color(0xC67C5A))
                        .append(Component.text("迷宮魔像", TextColor.color(0xB186D8), TextDecoration.BOLD))
                        .append(Component.text(" 已在迷宮中心甦醒！", TextColor.color(0xE8A36A))));
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
            final var definition = this.plugin.getTechRegistry().getItem(rewardId);
            if (definition == null) {
                continue;
            }
            final ItemStack item = this.itemFactory.buildTechItem(definition);
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
        if (!this.isLabyrinthWorld(world)) {
            return;
        }
        final int zone = getMazeZone(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (zone == 0) {
            this.advanceQuest(player, QuestType.REACH_CENTER, 1);
        }
    }

    /**
     * 偵測玩家從 Glade 內部走出到迷宮（跨越牆壁邊界），播放震撼 title 與音效。
     */
    public void checkGladeBoundaryCrossing(final Player player) {
        final World world = player.getWorld();
        if (!this.isLabyrinthWorld(world)) {
            this.playerInGlade.remove(player.getUniqueId());
            return;
        }
        final int bx = player.getLocation().getBlockX();
        final int bz = player.getLocation().getBlockZ();
        final boolean insideGlade = Math.abs(bx) <= GLADE_HALF && Math.abs(bz) <= GLADE_HALF;
        final Boolean previous = this.playerInGlade.put(player.getUniqueId(), insideGlade);
        if (previous != null && previous && !insideGlade) {
            // 從 Glade 內部走出 → 進入迷宮（小小 action bar 白字，不用大 title）
            // 新手引導：進入迷宮
            if (this.plugin.getTutorialChainService() != null) {
                this.plugin.getTutorialChainService().onMazeEnter(player);
            }
            player.sendActionBar(Component.text("已踏入未知領域 迷宮之眼注視中",
                    NamedTextColor.WHITE));
            player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_EMERGE,
                    SoundCategory.AMBIENT, 1.5f, 0.4f);
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE,
                    SoundCategory.AMBIENT, 2.0f, 0.6f);
            player.getWorld().spawnParticle(Particle.SCULK_SOUL,
                    player.getLocation().add(0, 1, 0), 30,
                    1.0, 1.0, 1.0, 0.05);
        }
    }

    /** 玩家離線時清理追蹤狀態。 */
    public void onPlayerQuitCleanup(final UUID uuid) {
        this.playerInGlade.remove(uuid);
        this.extractingPlayers.remove(uuid);
        this.extractionCooldowns.remove(uuid);
        // 注意：extractionReturnPoints 與 adventurerStash 不清除，玩家重新登入可繼續
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
        if (world == null) {
            return false;
        }
        final PlanetService ps = this.plugin.getPlanetService();
        return ps != null && ps.isPlanetWorld("labyrinth", world);
    }

    /**
     * 位置是否在 Glade 倖存者基地範圍內（含牆壁）。
     */
    public boolean isInsideGlade(final Location loc) {
        if (loc == null || !this.isLabyrinthWorld(loc.getWorld())) {
            return false;
        }
        return Math.abs(loc.getBlockX()) <= GLADE_HALF && Math.abs(loc.getBlockZ()) <= GLADE_HALF;
    }

    /**
     * 取得迷宮分區：0 = Glade, 1 = 內圈, 2 = 中圈, 3 = 外圈。
     * 僅對迷宮世界有效；其他世界或超出邊界回傳 -1。
     */
    public int getMazeZoneForLocation(final Location loc) {
        if (loc == null || !this.isLabyrinthWorld(loc.getWorld())) {
            return -1;
        }
        final int bx = loc.getBlockX();
        final int bz = loc.getBlockZ();
        if (Math.abs(bx) <= GLADE_HALF && Math.abs(bz) <= GLADE_HALF) {
            return 0;
        }
        final int dist = Math.max(Math.abs(bx), Math.abs(bz));
        if (dist > MAZE_HALF_EXTENT) {
            return -1;
        }
        final int cellDist = dist / MAZE_CELL_SIZE;
        if (cellDist <= 20) return 1;
        if (cellDist <= 40) return 2;
        return 3;
    }

    // ═══════════════════════════════════════
    //  Glade 門系統（10分開一次、30秒後關）
    // ═══════════════════════════════════════

    private void tickGladeGates() {
        final World world = this.resolveLabyrinthWorld();
        if (world == null || world.getPlayers().isEmpty()) {
            return;
        }
        final long currentTick = world.getGameTime();
        // 所有方塊/實體操作都必須在 region thread 上執行
        final Location regionRef = new Location(world, 0, FLOOR_Y + 1, 0);
        this.scheduler.runRegion(regionRef, task -> this.tickGladeGatesRegion(world, currentTick));
    }

    private void tickGladeGatesRegion(final World world, final long currentTick) {
        if (this.lastGateOpenTick == 0L) {
            this.lastGateOpenTick = currentTick;
            // 初始化：生成 TextDisplay（牆壁由 ChunkGenerator 生成，預設關閉）
            this.spawnGateTextDisplays(world);
            return;
        }

        // 門開著的時候：檢查是否該關閉
        if (this.gatesOpen) {
            if (currentTick >= this.gateOpenEndTick) {
                this.closeGate(world, this.openWallIdx);
                this.gatesOpen = false;
                final String name = (this.openWallIdx >= 0 && this.openWallIdx < GATE_DIRECTION_NAMES.length)
                        ? GATE_DIRECTION_NAMES[this.openWallIdx] : "?";
                this.openWallIdx = -1;
                this.broadcastGateTitle(world,
                        Component.text("門戶閉合", TextColor.color(0xFF6B6B), TextDecoration.BOLD),
                        Component.text("Glade " + name + " 面牆壁正在重新合攏", TextColor.color(0xC9A77E)),
                        40L, Sound.BLOCK_NOTE_BLOCK_BASS);
                for (final Player player : world.getPlayers()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, SoundCategory.BLOCKS, 1.0f, 0.6f);
                }
            }
        } else {
            // 門關著：檢查是否該開啟
            if (currentTick - this.lastGateOpenTick >= GATE_INTERVAL_TICKS) {
                final int wallIdx = this.random.nextInt(GATE_DIRECTIONS.length);
                this.openGate(world, wallIdx);
                this.gatesOpen = true;
                this.openWallIdx = wallIdx;
                this.lastGateOpenTick = currentTick;
                this.gateOpenEndTick = currentTick + GATE_OPEN_DURATION_TICKS;
                final String name = GATE_DIRECTION_NAMES[wallIdx];
                this.broadcastGateTitle(world,
                        Component.text("門戶開啟", TextColor.color(0x7CFCC0), TextDecoration.BOLD),
                        Component.text("Glade " + name + " 面 · 30 秒後閉合", TextColor.color(0xE8E0C0)),
                        60L, Sound.BLOCK_NOTE_BLOCK_BELL);
                for (final Player player : world.getPlayers()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, SoundCategory.BLOCKS, 1.0f, 0.6f);
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, SoundCategory.AMBIENT, 0.4f, 1.2f);
                    player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 1.5f, 0.4f);
                }
            }
        }

        // 更新倒數文字
        this.updateGateCountdownText(world, currentTick);
    }

    /** 管理員指令：強制開啟/關閉 Glade 大門。 */
    public void forceOpenGates() {
        final World world = this.resolveLabyrinthWorld();
        if (world == null) return;
        final Location regionRef = new Location(world, 0, FLOOR_Y + 1, 0);
        this.scheduler.runRegion(regionRef, task -> {
            if (this.gatesOpen) return;
            final int wallIdx = this.random.nextInt(GATE_DIRECTIONS.length);
            this.openGate(world, wallIdx);
            this.gatesOpen = true;
            this.openWallIdx = wallIdx;
            this.lastGateOpenTick = world.getGameTime();
            this.gateOpenEndTick = this.lastGateOpenTick + GATE_OPEN_DURATION_TICKS;
            this.broadcastGateTitle(world,
                    Component.text("門戶開啟", TextColor.color(0x7CFCC0), TextDecoration.BOLD),
                    Component.text("Glade " + GATE_DIRECTION_NAMES[wallIdx] + " 面 · 30 秒後閉合", TextColor.color(0xE8E0C0)),
                    60L, Sound.BLOCK_NOTE_BLOCK_BELL);
        });
    }

    public void forceCloseGates() {
        final World world = this.resolveLabyrinthWorld();
        if (world == null) return;
        final Location regionRef = new Location(world, 0, FLOOR_Y + 1, 0);
        this.scheduler.runRegion(regionRef, task -> {
            if (!this.gatesOpen) return;
            this.closeGate(world, this.openWallIdx);
            this.gatesOpen = false;
            this.openWallIdx = -1;
            this.broadcastGateTitle(world,
                    Component.text("門戶閉合", TextColor.color(0xFF6B6B), TextDecoration.BOLD),
                    Component.text("管理員已強制關閉 Glade 大門", TextColor.color(0xC9A77E)),
                    40L, Sound.BLOCK_NOTE_BLOCK_BASS);
        });
    }

    /** 透過 TitleMsgService 對世界中所有玩家廣播打字機風格 title。 */
    private void broadcastGateTitle(final World world,
                                    final Component title,
                                    final Component subtitle,
                                    final long holdTicks,
                                    final Sound tickSound) {
        final TitleMsgService titleMsg = this.plugin.getTitleMsgService();
        if (titleMsg == null) return;
        for (final Player player : world.getPlayers()) {
            titleMsg.send(player, title, subtitle, holdTicks, tickSound);
        }
    }

    /**
     * BigDoor 風格開門：20×30 門洞，雙邊滑開（每邊滑 10 格，剛好清空門洞）。
     */
    private void openGate(final World world, final int wallIdx) {
        if (wallIdx < 0 || wallIdx >= GATE_DIRECTIONS.length) {
            return;
        }
        final int[] dir = GATE_DIRECTIONS[wallIdx];
        final boolean wallAxisIsX = dir[0] == 0; // N/S 牆：長度方向沿 X 軸
        this.gateSnapshot.clear();
        this.removeGateSlideDisplays();

        // 門洞：以牆中央為中心，len ∈ [-GATE_HALF_WIDTH, GATE_HALF_WIDTH-1]（20 格寬）
        for (int len = -GATE_HALF_WIDTH; len < GATE_HALF_WIDTH; len++) {
            for (int thick = 0; thick < GATE_WALL_THICKNESS; thick++) {
                final int thickOffset = GLADE_HALF - 2 + thick; // 48, 49, 50
                final int bx;
                final int bz;
                if (wallAxisIsX) {
                    bx = len;
                    bz = dir[1] * thickOffset;
                } else {
                    bx = dir[0] * thickOffset;
                    bz = len;
                }
                // 左半邊 (len<0) 滑向 -GATE_SLIDE_DISTANCE，右半邊滑向 +GATE_SLIDE_DISTANCE
                final float slideDelta = len < 0 ? -GATE_SLIDE_DISTANCE : GATE_SLIDE_DISTANCE;
                final float dx = wallAxisIsX ? slideDelta : 0f;
                final float dz = wallAxisIsX ? 0f : slideDelta;
                this.spawnSlidingColumn(world, bx, bz, dx, dz, true);
            }
        }

        // 開門音效與粒子特效（門洞中央）
        final double cx = wallAxisIsX ? 0.0 : dir[0] * (GLADE_HALF - 0.5);
        final double cz = wallAxisIsX ? dir[1] * (GLADE_HALF - 0.5) : 0.0;
        final Location center = new Location(world, cx, FLOOR_Y + GATE_OPENING_HEIGHT / 2.0, cz);
        world.playSound(center, Sound.BLOCK_PISTON_EXTEND, SoundCategory.BLOCKS, 2.0f, 0.3f);
        world.playSound(center, Sound.ENTITY_WARDEN_EMERGE, SoundCategory.AMBIENT, 1.2f, 0.5f);
        world.spawnParticle(Particle.BLOCK, center, 60, 8.0, 12.0, 8.0, 0.05,
                Material.DEEPSLATE_BRICKS.createBlockData());
    }

    /**
     * BigDoor 風格關門：從滑出位置回滑到原位。
     */
    private void closeGate(final World world, final int wallIdx) {
        if (wallIdx < 0 || wallIdx >= GATE_DIRECTIONS.length) {
            return;
        }
        final int[] dir = GATE_DIRECTIONS[wallIdx];
        final boolean wallAxisIsX = dir[0] == 0;
        this.removeGateSlideDisplays();

        for (int len = -GATE_HALF_WIDTH; len < GATE_HALF_WIDTH; len++) {
            for (int thick = 0; thick < GATE_WALL_THICKNESS; thick++) {
                final int thickOffset = GLADE_HALF - 2 + thick;
                final int bx;
                final int bz;
                if (wallAxisIsX) {
                    bx = len;
                    bz = dir[1] * thickOffset;
                } else {
                    bx = dir[0] * thickOffset;
                    bz = len;
                }
                final float slideDelta = len < 0 ? -GATE_SLIDE_DISTANCE : GATE_SLIDE_DISTANCE;
                final float dx = wallAxisIsX ? slideDelta : 0f;
                final float dz = wallAxisIsX ? 0f : slideDelta;
                this.spawnSlidingColumn(world, bx, bz, dx, dz, false);
            }
        }

        final double cx = wallAxisIsX ? 0.0 : dir[0] * (GLADE_HALF - 0.5);
        final double cz = wallAxisIsX ? dir[1] * (GLADE_HALF - 0.5) : 0.0;
        final Location center = new Location(world, cx, FLOOR_Y + GATE_OPENING_HEIGHT / 2.0, cz);
        world.playSound(center, Sound.BLOCK_PISTON_CONTRACT, SoundCategory.BLOCKS, 2.0f, 0.3f);
        world.playSound(center, Sound.BLOCK_DEEPSLATE_BRICKS_PLACE, SoundCategory.BLOCKS, 1.5f, 0.5f);
    }

    /**
     * 為單一 (bx, bz) 垂直柱（100 格高）產生 BlockDisplay 並做滑動動畫。
     *
     * @param opening true = 開門（從原位滑到目標位，並先清除實體方塊）
     *                false = 關門（從滑出位滑回原位，動畫結束後放回實體方塊）
     */
    private void spawnSlidingColumn(final World world,
                                     final int bx,
                                     final int bz,
                                     final float finalDx,
                                     final float finalDz,
                                     final boolean opening) {
        final int yMax = FLOOR_Y + GATE_OPENING_HEIGHT;
        final Location base = new Location(world, bx, FLOOR_Y + 1, bz);
        if (!base.isChunkLoaded()) {
            if (opening) {
                for (int y = FLOOR_Y + 1; y <= yMax; y++) {
                    this.gateSnapshot.put(bx + ":" + y + ":" + bz,
                            Material.DEEPSLATE_BRICKS.createBlockData());
                }
            }
            return;
        }

        // 開門時：擷取現有方塊快照（用於關門復原）
        if (opening) {
            for (int y = FLOOR_Y + 1; y <= yMax; y++) {
                final org.bukkit.block.data.BlockData data = world.getBlockAt(bx, y, bz).getBlockData();
                this.gateSnapshot.put(bx + ":" + y + ":" + bz, data);
            }
            // 清除實體方塊
            for (int y = FLOOR_Y + 1; y <= yMax; y++) {
                world.getBlockAt(bx, y, bz).setType(Material.AIR, false);
            }
        }

        // 統一材質避免不同牆面素材被拉伸疊加時出現亂色破圖
        final org.bukkit.block.data.BlockData displayData = Material.DEEPSLATE_BRICKS.createBlockData();

        // 將 30 格高門柱切成多段 3 格高的小 BlockDisplay，降低縮放倍率避免拉伸破圖
        final int segmentHeight = 3;
        final int segmentCount = GATE_OPENING_HEIGHT / segmentHeight; // 10 段
        for (int seg = 0; seg < segmentCount; seg++) {
            final int segY = FLOOR_Y + 1 + seg * segmentHeight;
            final Location segBase = new Location(world, bx, segY, bz);
            final BlockDisplay display = world.spawn(segBase, BlockDisplay.class, bd -> {
                bd.setBlock(displayData);
                bd.setPersistent(false);
                bd.setGravity(false);
                bd.setInvulnerable(true);
                bd.setBrightness(new Display.Brightness(15, 15));
                bd.setInterpolationDelay(-1);
                bd.setInterpolationDuration(GATE_ANIM_TICKS);
                bd.setTeleportDuration(0);
                bd.addScoreboardTag(GATE_SLIDE_TAG);
                bd.setTransformation(new Transformation(
                        opening ? new Vector3f(0f, 0f, 0f) : new Vector3f(finalDx, 0f, finalDz),
                        new AxisAngle4f(0f, 0f, 1f, 0f),
                        new Vector3f(1f, (float) segmentHeight, 1f),
                        new AxisAngle4f(0f, 0f, 1f, 0f)));
            });
            this.activeGateSlideDisplays.add(display.getUniqueId());

            // 下一 tick 觸發插值到目標位置
            this.scheduler.runRegionDelayed(segBase, t2 -> {
                if (display.isValid()) {
                    display.setInterpolationDelay(0);
                    display.setTransformation(new Transformation(
                            opening ? new Vector3f(finalDx, 0f, finalDz) : new Vector3f(0f, 0f, 0f),
                            new AxisAngle4f(0f, 0f, 1f, 0f),
                            new Vector3f(1f, (float) segmentHeight, 1f),
                            new AxisAngle4f(0f, 0f, 1f, 0f)));
                }
            }, 1L);

            // 動畫結束：移除 display
            this.scheduler.runRegionDelayed(segBase, t3 -> {
                if (display.isValid()) {
                    display.remove();
                }
                this.activeGateSlideDisplays.remove(display.getUniqueId());
            }, GATE_ANIM_TICKS + 5L);
        }

        // 關門時：動畫結束後一次性復原實體方塊
        if (!opening) {
            this.scheduler.runRegionDelayed(base, t3 -> {
                for (int y = FLOOR_Y + 1; y <= yMax; y++) {
                    final org.bukkit.block.data.BlockData snap = this.gateSnapshot.get(bx + ":" + y + ":" + bz);
                    if (snap != null) {
                        world.getBlockAt(bx, y, bz).setBlockData(snap, false);
                    } else {
                        final int relY = y - FLOOR_Y;
                        final Material fallback;
                        if (relY <= 3) fallback = Material.DEEPSLATE;
                        else fallback = Material.DEEPSLATE_BRICKS;
                        world.getBlockAt(bx, y, bz).setType(fallback, false);
                    }
                }
            }, GATE_ANIM_TICKS + 5L);
        }
    }

    private void removeGateSlideDisplays() {
        for (final UUID id : this.activeGateSlideDisplays) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        this.activeGateSlideDisplays.clear();
    }

    private void spawnGateTextDisplays(final World world) {
        // 清除舊的 TextDisplay
        this.removeGateTextDisplays();

        for (final int[] dir : GATE_DIRECTIONS) {
            // 文字顯示在 Glade 內側（站在基地裡面向牆壁就看得到）
            final double tx = dir[0] * (GLADE_HALF - 4) + 0.5;
            final double tz = dir[1] * (GLADE_HALF - 4) + 0.5;
            final Location textLoc = new Location(world, tx, FLOOR_Y + 6, tz);

            if (!textLoc.isChunkLoaded()) {
                continue;
            }
            this.scheduler.runRegion(textLoc, task -> {
                final TextDisplay td = world.spawn(textLoc, TextDisplay.class, display -> {
                    display.text(Component.text("░ 門將在 10:00 後開啟 ░", GATE_CLOSED_COLOR));
                    display.setBillboard(Display.Billboard.CENTER);
                    // 純文字：關閉預設底色、把背景設為完全透明
                    display.setDefaultBackground(false);
                    display.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                    display.setSeeThrough(true);
                    display.setShadowed(true);
                    display.setPersistent(false);
                    display.setGravity(false);
                    display.addScoreboardTag(GATE_TEXT_TAG);
                    display.setTransformation(new Transformation(
                            new Vector3f(0, 0, 0),
                            new AxisAngle4f(0, 0, 1, 0),
                            new Vector3f(2.5f, 2.5f, 2.5f),
                            new AxisAngle4f(0, 0, 1, 0)));
                });
                this.gateTextDisplays.add(td.getUniqueId());
            });
        }
    }

    private void removeGateTextDisplays() {
        for (final UUID id : this.gateTextDisplays) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
        }
        this.gateTextDisplays.clear();
    }

    private void updateGateCountdownText(final World world, final long currentTick) {
        final Component text;
        if (this.gatesOpen) {
            final long remainTicks = Math.max(0, this.gateOpenEndTick - currentTick);
            final int secs = (int) (remainTicks / 20L);
            text = Component.text(String.format("█ 門將在 %02d 秒後關閉 █", secs), GATE_OPEN_COLOR, TextDecoration.BOLD);
        } else {
            final long remainTicks = Math.max(0, GATE_INTERVAL_TICKS - (currentTick - this.lastGateOpenTick));
            final int totalSecs = (int) (remainTicks / 20L);
            final int mins = totalSecs / 60;
            final int secs = totalSecs % 60;
            text = Component.text(String.format("░ 門將在 %d:%02d 後開啟 ░", mins, secs), GATE_CLOSED_COLOR);
        }

        for (final UUID id : this.gateTextDisplays) {
            final Entity entity = Bukkit.getEntity(id);
            if (entity instanceof TextDisplay td && td.isValid()) {
                td.text(text);
            }
        }
    }

    // ═══════════════════════════════════════
    //  中央撤離點（跳躍觸發 → 火箭送回原位置）
    // ═══════════════════════════════════════

    /**
     * 由 PlanetService.teleportToPlanet 在玩家進入迷途星之前呼叫，記下返回點。
     */
    public void rememberReturnPoint(final Player player, final Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }
        this.extractionReturnPoints.put(player.getUniqueId(), location.clone());
    }

    /** 查詢玩家目前記錄的返回點（若無則回傳 null）。 */
    public Location returnPointFor(final UUID uuid) {
        final Location loc = this.extractionReturnPoints.get(uuid);
        return loc == null ? null : loc.clone();
    }

    /**
     * 由 TechListener.onPlayerJump 呼叫：若玩家在撤離點中央跳躍，啟動返回火箭。
     */
    public void checkExtractionTrigger(final Player player) {
        if (!this.isLabyrinthWorld(player.getWorld())) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        if (this.extractingPlayers.contains(uuid)) {
            return;
        }
        final Location loc = player.getLocation();
        // 站在 (0,0) 中央撤離踏板正上方（Y=65 = 地板 64 + 1）
        if (loc.getBlockX() != 0 || loc.getBlockZ() != 0) {
            return;
        }
        if (loc.getBlockY() != EXTRACTION_TRIGGER_Y) {
            return;
        }
        final long now = System.currentTimeMillis();
        if (now - this.extractionCooldowns.getOrDefault(uuid, 0L) < EXTRACTION_COOLDOWN_MS) {
            return;
        }
        final Location returnPoint = this.extractionReturnPoints.get(uuid);
        if (returnPoint == null || returnPoint.getWorld() == null) {
            player.sendMessage(this.plugin.getItemFactory().warning("找不到返回點，請從星門重新進入。"));
            return;
        }
        this.extractionCooldowns.put(uuid, now);
        this.startExtraction(player, returnPoint);
    }

    private void startExtraction(final Player player, final Location returnPoint) {
        final UUID uuid = player.getUniqueId();
        this.extractingPlayers.add(uuid);
        final World world = player.getWorld();
        final Location origin = player.getLocation();

        // 啟動音效與粒子
        world.playSound(origin, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 1.5f, 0.7f);
        world.playSound(origin, Sound.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.AMBIENT, 1.2f, 0.8f);
        world.spawnParticle(Particle.SCULK_SOUL, origin, 40, 1.5, 0.5, 1.5, 0.02);
        world.spawnParticle(Particle.REVERSE_PORTAL, origin.clone().add(0, 0.5, 0), 60, 1.2, 0.5, 1.2, 0.1);

        final TitleMsgService titleMsg = this.plugin.getTitleMsgService();
        if (titleMsg != null) {
            titleMsg.send(player,
                    Component.text("█ 撤離程序啟動 █", NamedTextColor.AQUA, TextDecoration.BOLD),
                    Component.text("返回火箭即將發射", NamedTextColor.GRAY),
                    60L, Sound.BLOCK_BEACON_ACTIVATE);
        }

        // 委派給 PlanetService 的自訂火箭動畫；完成時傳送到返回點並發放倉庫物品
        this.plugin.getPlanetService().startCustomTravel(player, origin, p -> {
            if (!p.isOnline()) {
                this.extractingPlayers.remove(uuid);
                return;
            }
            p.teleportAsync(returnPoint).thenAccept(ok -> this.scheduler.runEntity(p, () -> {
                this.extractingPlayers.remove(uuid);
                this.extractionReturnPoints.remove(uuid);
                if (!p.isOnline()) return;
                this.giveStashToPlayer(p);
                final World rw = returnPoint.getWorld();
                if (rw != null) {
                    rw.playSound(returnPoint, Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.4f);
                    rw.spawnParticle(Particle.END_ROD, returnPoint, 60, 1.5, 1.0, 1.5, 0.05);
                }
                if (titleMsg != null) {
                    titleMsg.send(p,
                            Component.text("✔ 撤離成功", NamedTextColor.GREEN, TextDecoration.BOLD),
                            Component.text("冒險者拾取物已入手", NamedTextColor.GRAY),
                            40L, Sound.BLOCK_NOTE_BLOCK_BELL);
                }
            }));
        });
    }

    // ═══════════════════════════════════════
    //  冒險者拾取物虛擬倉庫
    // ═══════════════════════════════════════

    /**
     * 是否為撤離點的倉庫桶座標（固定位置）。
     */
    public boolean isAdventurerStashBlock(final Location loc) {
        if (loc == null || !this.isLabyrinthWorld(loc.getWorld())) {
            return false;
        }
        return loc.getBlockX() == STASH_BARREL_X
                && loc.getBlockY() == STASH_BARREL_Y
                && loc.getBlockZ() == STASH_BARREL_Z;
    }

    /**
     * 將物品存入玩家專屬的虛擬倉庫。若倉庫已滿回傳 false。
     */
    public boolean depositToAdventurerStash(final Player player, final ItemStack stack) {
        if (player == null || stack == null || stack.getType().isAir()) {
            return false;
        }
        final List<ItemStack> stash = this.adventurerStash.computeIfAbsent(
                player.getUniqueId(), id -> new ArrayList<>());
        // 嘗試合併到相同物品
        for (final ItemStack existing : stash) {
            if (existing.isSimilar(stack) && existing.getAmount() < existing.getMaxStackSize()) {
                final int free = existing.getMaxStackSize() - existing.getAmount();
                final int move = Math.min(free, stack.getAmount());
                existing.setAmount(existing.getAmount() + move);
                stack.setAmount(stack.getAmount() - move);
                if (stack.getAmount() <= 0) {
                    return true;
                }
            }
        }
        // 剩餘量作為新堆疊加入
        if (stash.size() >= EXTRACTION_STASH_SIZE) {
            return false;
        }
        stash.add(stack.clone());
        return true;
    }

    /**
     * 開啟玩家專屬的「冒險者拾取物」虛擬 GUI（唯讀檢視）。
     */
    public void openAdventurerStash(final Player player) {
        final List<ItemStack> stash = this.adventurerStash.computeIfAbsent(
                player.getUniqueId(), id -> new ArrayList<>());
        final AdventurerStashHolder holder = new AdventurerStashHolder(player.getUniqueId());
        final org.bukkit.inventory.Inventory inv = Bukkit.createInventory(holder, EXTRACTION_STASH_SIZE,
                Component.text("冒險者拾取物 ", NamedTextColor.AQUA, TextDecoration.BOLD)
                        .append(Component.text("(" + stash.size() + "/" + EXTRACTION_STASH_SIZE + ")", NamedTextColor.GRAY)));
        holder.setInventory(inv);
        for (int i = 0; i < Math.min(stash.size(), EXTRACTION_STASH_SIZE); i++) {
            inv.setItem(i, stash.get(i));
        }
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.7f, 1.0f);
    }

    /**
     * 撤離時把倉庫內容全部塞回玩家背包，塞不下的丟在腳邊。
     */
    private void giveStashToPlayer(final Player player) {
        final List<ItemStack> stash = this.adventurerStash.remove(player.getUniqueId());
        if (stash == null || stash.isEmpty()) {
            return;
        }
        final World w = player.getWorld();
        for (final ItemStack item : stash) {
            if (item == null || item.getType().isAir()) continue;
            final Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
            for (final ItemStack drop : overflow.values()) {
                w.dropItemNaturally(player.getLocation(), drop);
            }
        }
        player.sendMessage(Component.text("✔ 冒險者拾取物已全部送達背包。", NamedTextColor.GREEN));
    }
}
