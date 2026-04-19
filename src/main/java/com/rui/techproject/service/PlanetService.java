package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.model.PlacedMachine;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.LocationKey;
import com.rui.techproject.util.SafeScheduler;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import com.rui.techproject.storage.StorageBackend;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

public final class PlanetService {
    public static final String AURELIA_WORLD = "tech_aurelia";
    public static final String CRYON_WORLD = "tech_cryon";
    public static final String NYX_WORLD = "tech_nyx";
    public static final String HELION_WORLD = "tech_helion";
    public static final String TEMPEST_WORLD = "tech_tempest";
    public static final String LABYRINTH_WORLD = "tech_labyrinth";

    private static final List<String> FRONTIER_SUIT = List.of(
            "frontier_helmet",
            "frontier_chestplate",
            "frontier_leggings",
            "frontier_boots"
    );
        private static final List<List<String>> PRE_FRONTIER_SUITS = List.of(
            List.of("anti_radiation_helmet", "anti_radiation_chestplate", "anti_radiation_leggings", "anti_radiation_boots"),
            List.of("thermal_helmet", "thermal_chestplate", "thermal_leggings", "thermal_boots"),
            List.of("pressure_helmet", "pressure_chestplate", "pressure_leggings", "pressure_boots")
        );
    private static final String HARVEST_NODE_DISPLAY_TAG = "techproject:harvest_node";
    private static final String TRAVEL_VESSEL_TAG = "techproject:travel_vessel";
    private static final String TRAVEL_VESSEL_PART_TAG = "techproject:travel_vessel_part";
    private static final String PLANET_ELITE_TAG = "techproject:planet_elite";
    private static final String PLANET_ELITE_TAG_PREFIX = "techproject:planet_elite_";
    private static final long PERSONAL_NODE_TICK_INTERVAL = 20L;
    private static final long HARVEST_DISPLAY_CLEANUP_INTERVAL = 40L;
    private static final long FRUIT_REGROWTH_TICK_INTERVAL = 40L;
    private static final double PLANET_DECORATION_DENSITY = 0.5D;
    private static final double PLANET_HARVEST_NODE_DENSITY = 0.5D;
    private static final int PLANET_FRUIT_TREE_BURST_CHANCE = 10;
    private static final int PLANET_FRUIT_TREE_EXTRA_BURST_CHANCE = 4;
    private static final int MAX_FRUIT_PER_TREE = 2;
    private static final int PERSONAL_NODE_SCAN_RADIUS = 8;
    private static final int PERSONAL_NODE_SCAN_Y_RADIUS = 3;
    private static final int MAX_VISIBLE_HARVEST_NODES = 10;
    private static final int PLANET_BOUNDARY_RADIUS = 3000;
    private static final long PLANETARY_GATE_TRAVEL_COST = 1200L;
    private static final long PLANETARY_GATE_WARMUP_MS = 60_000L; // 開機至少 1 分鐘才能傳送
    private static final String SHIP_FUEL_ITEM_ID = "refined_oil";
    private static final int SHIP_BASE_FUEL_COST = 2;
    private static final int SHIP_FUEL_WEIGHT_DIVISOR = 64;
    private static final int SHIP_COUNTDOWN_SECONDS = 5;
    private static final Key PLANETARY_GATE_MENU_FONT = Key.key("minecraft", "techproject_gate");
    private static final String PLANETARY_GATE_MENU_SHIFT = "\uF100";
    private static final String PLANETARY_GATE_MENU_GLYPH = "\uF000";
    private static final String PLANETARY_GATE_MENU_TITLE = PLANETARY_GATE_MENU_SHIFT + PLANETARY_GATE_MENU_GLYPH;
    // 異界傳送門 GUI — 搭配 pack/assets/minecraft/font/techproject_labyrinth.json
    private static final Key LABYRINTH_MENU_FONT = Key.key("minecraft", "techproject_labyrinth");
    private static final String LABYRINTH_MENU_SHIFT = "\uF102";
    private static final String LABYRINTH_MENU_GLYPH = "\uF006";
    private static final String LABYRINTH_MENU_TITLE = LABYRINTH_MENU_SHIFT + LABYRINTH_MENU_GLYPH;
    // 傳送按鈕所在槽位（GUI 下半段中央附近）
    // 兩顆傳送按鈕上下相疊：原本 {31,40}（row3/row4），改為 {22,31}（row2/row3）往上移一列
    private static final int[] OTHERWORLD_PORTAL_BUTTON_SLOTS = {22, 31};
    private static final String EARTH_DESTINATION_ID = "earth";
    private static final String EARTH_WORLD_NAME = "world";
    private static final long LOOT_BARREL_REFILL_MS = 12L * 60L * 60L * 1000L;

    private final TechMCPlugin plugin;
    private final SafeScheduler scheduler;
    private final ItemFactoryUtil itemFactory;
    private final TechRegistry registry;
    private final Map<String, PlanetDefinition> planets = new LinkedHashMap<>();
    private final Set<LocationKey> activatedRuins = ConcurrentHashMap.newKeySet();
    private final Map<java.util.UUID, Set<LocationKey>> playerActivatedRuins = new ConcurrentHashMap<>();
    private final Map<LocationKey, String> gateDestinations = new ConcurrentHashMap<>();
    private final Set<String> generatedPlanetWorlds = ConcurrentHashMap.newKeySet();
    private final Set<String> processedDatapackChunks = ConcurrentHashMap.newKeySet();
    private final Set<String> decoratedPlanetChunks = ConcurrentHashMap.newKeySet();
    private final Set<java.util.UUID> travelingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<java.util.UUID, TravelVessel> travelVessels = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, TravelPlayerState> travelPlayerStates = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, java.util.function.Consumer<Player>> customTravelCallbacks = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, LocationKey> openPlanetaryGateMenus = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Map<LocationKey, Long>> personalHarvestCooldowns = new ConcurrentHashMap<>();
    private final Map<LocationKey, Long> machineHarvestCooldowns = new ConcurrentHashMap<>();
    private final Map<LocationKey, java.util.UUID> harvestNodeDisplays = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, PendingHarvest> pendingHarvests = new ConcurrentHashMap<>();
    private final Map<LocationKey, FruitNodeRegrowth> fruitNodeRegrowths = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Long> boundaryWarningCooldowns = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Map<HazardType, Long>> cuisineWardExpiries = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> techItemStackCache = new ConcurrentHashMap<>();
    /** 精英技能冷卻：entityUUID → 下次可施放時間 (ms) */
    private final Map<java.util.UUID, Long> eliteSkillCooldowns = new ConcurrentHashMap<>();
    private final Random ambientRandom = new Random();
    private final NamespacedKey lootBarrelPlanetKey;
    private final NamespacedKey lootBarrelTierKey;
    private final NamespacedKey lootBarrelTimeKey;
    private final Map<java.util.UUID, RuinChallenge> activeRuinChallenges = new ConcurrentHashMap<>();
    private static final String RUIN_CHALLENGE_MOB_TAG = "techproject:ruin_challenge_mob";
    private static final int[] RUIN_WAVE_COUNTS = { 3, 5, 7 };
    private static final long RUIN_WAVE_TIMEOUT_TICKS = 20L * 90;
    private static final long RUIN_WAVE_DELAY_TICKS = 20L * 4;
    private static final long RUIN_CHALLENGE_COOLDOWN_MS = 30L * 60L * 1000L;
    private StorageBackend storageBackend;
    private volatile boolean worldCreationUnsupported;

    private enum HazardType {
        RADIATION,
        CRYO,
        VACUUM,
        SOLAR,
        STORM,
        MIASMA
    }

    private static final class RuinChallenge {
        final java.util.UUID playerId;
        final PlanetDefinition definition;
        final Location coreLocation;
        final Set<java.util.UUID> aliveMobs = ConcurrentHashMap.newKeySet();
        int currentWave;
        int waveGeneration;
        long waveStartTick;
        boolean completed;
        boolean failed;
        /**
         * 波進度推進鎖：設為 true 後，其他路徑不得再嘗試推進。
         * 在下一波 spawnRuinWave() 真正開始生怪時清為 false。
         * 修復：原本 onRuinChallengeMobDeath 和 tickRuinBossBar 各自推進導致跳波、重複生怪。
         */
        final java.util.concurrent.atomic.AtomicBoolean waveAdvancing = new java.util.concurrent.atomic.AtomicBoolean(false);
        BossBar bossBar;

        RuinChallenge(final java.util.UUID playerId, final PlanetDefinition definition, final Location coreLocation) {
            this.playerId = playerId;
            this.definition = definition;
            this.coreLocation = coreLocation;
            this.currentWave = 0;
            this.completed = false;
            this.failed = false;
        }
    }

    private record PlanetDefinition(String id,
                                    String displayName,
                                    String worldName,
                                    List<String> worldAliases,
                                    World.Environment environment,
                                    HazardType hazardType,
                                    List<String> suitIds,
                                    Material markerMaterial,
                                    Material ruinCoreMaterial,
                                    String ruinActivationItemId,
                                    List<String> ruinRewardIds) {
    }

    private record TravelPlayerState(GameMode gameMode,
                                     boolean allowFlight,
                                     boolean flying,
                                     float walkSpeed,
                                     float flySpeed) {
    }

    private record TravelDestination(String id,
                                     String displayName,
                                     World world,
                                     PlanetDefinition planetDefinition) {
    }

        private record PlanetaryGateButtonLayout(String key,
                             String targetId,
                             int startRow,
                             int startColumn) {
        }

        private static final List<PlanetaryGateButtonLayout> PLANETARY_GATE_LAYOUTS = List.of(
            new PlanetaryGateButtonLayout("planetary-gate-earth", EARTH_DESTINATION_ID, 0, 0),
            new PlanetaryGateButtonLayout("planetary-gate-aurelia", "aurelia", 0, 3),
            new PlanetaryGateButtonLayout("planetary-gate-helion", "helion", 0, 6),
            new PlanetaryGateButtonLayout("planetary-gate-nyx", "nyx", 3, 0),
            new PlanetaryGateButtonLayout("planetary-gate-cryon", "cryon", 3, 3),
            new PlanetaryGateButtonLayout("planetary-gate-tempest", "tempest", 3, 6)
            // labyrinth 已從星門移除，改由「異界傳送門」進入其他世界
        );

    private record TravelVessel(ArmorStand seat, ArmorStand cameraAnchor, List<TravelVesselPart> parts, float yaw) {
    }

    private record TravelVesselPart(BlockDisplay display, String role, double offsetX, double offsetY, double offsetZ) {
    }

    private record TravelMotionProfile(Location origin,
                                       float yaw,
                                       double startHeight,
                                       double endHeight,
                                       double swayStrength,
                                       double cockpitClosure) {
    }

    private record PlanetHarvestProfile(String dropItemId,
                                        int dropAmount,
                                        String seedItemId,
                                        int seedAmount,
                                        long playerRespawnTicks,
                                        long machineRespawnTicks,
                                        boolean animated) {
    }

    private record HarvestNodeCandidate(Block block,
                                        PlanetHarvestProfile profile,
                                        double distanceSquared) {
    }

    private record FruitTreeProfile(String saplingId,
                                    String fruitItemId,
                                    Material trunkMaterial,
                                    Material canopyMaterial,
                                    Material accentMaterial,
                                    int minHeight,
                                    int maxHeight,
                                    int fruitCount) {
    }

    private record FruitNodeRegrowth(String fruitItemId,
                                     long readyAt) {
    }

    private record PendingHarvest(LocationKey nodeKey,
                                  long token,
                                  long startedAt,
                                  long finishAt,
                                  int totalTicks) {
    }

    private record PlanetEliteProfile(String displayName,
                                      String combatSampleId,
                                      String bonusSampleId,
                                      double healthMultiplier,
                                      double attackBonus,
                                      double speedBonus) {
    }

    public PlanetService(final TechMCPlugin plugin,
                         final SafeScheduler scheduler,
                         final ItemFactoryUtil itemFactory,
                         final TechRegistry registry) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.itemFactory = itemFactory;
        this.registry = registry;
        this.lootBarrelPlanetKey = new NamespacedKey(plugin, "loot_barrel_planet");
        this.lootBarrelTierKey = new NamespacedKey(plugin, "loot_barrel_tier");
        this.lootBarrelTimeKey = new NamespacedKey(plugin, "loot_barrel_fill_time");
        this.registerPlanets();
    }

    public void setStorageBackend(final StorageBackend backend) {
        this.storageBackend = backend;
        this.load();
    }

    public void reloadRuntimeConfig() {
        // 星門 HUD 位置已改為只依賴最終資源包字型 JSON / PNG，不再由插件側參與偏移。
    }

    public void start() {
        this.pendingHarvests.clear();
        this.travelingPlayers.clear();
        this.travelPlayerStates.clear();
        this.travelVessels.clear();
        this.harvestNodeDisplays.clear();
        this.boundaryWarningCooldowns.clear();
        this.cuisineWardExpiries.clear();
        this.purgeManagedPlanetEntities();
        for (final PlanetDefinition definition : this.planets.values()) {
            final World world = this.ensurePlanetWorld(definition);
            if (world != null) {
                this.processLoadedPlanetDecorations(definition, world);
            }
        }
        this.scheduler.runGlobalTimer(task -> {
            try { this.tickPlanetHazards(); } catch (final Exception ex) {
                this.plugin.getLogger().warning("[Planet] tickPlanetHazards 異常：" + ex);
            }
            try { this.tickPlanetAmbience(); } catch (final Exception ex) {
                this.plugin.getLogger().warning("[Planet] tickPlanetAmbience 異常：" + ex);
            }
        }, 40L, 40L);
        this.scheduler.runGlobalTimer(task -> {
            try { this.tickPlanetMobSpawning(); } catch (final Exception ex) {
                this.plugin.getLogger().warning("[Planet] tickPlanetMobSpawning 異常：" + ex);
            }
        }, 15L, 15L);
        this.scheduler.runGlobalTimer(task -> this.tickPlanetEliteAuras(), ELITE_AURA_TICK_INTERVAL, ELITE_AURA_TICK_INTERVAL);
        this.scheduler.runGlobalTimer(task -> this.tickPlanetGravity(), 2L, 2L);
        this.scheduler.runGlobalTimer(task -> this.tickPersonalPlanetNodes(), PERSONAL_NODE_TICK_INTERVAL, PERSONAL_NODE_TICK_INTERVAL);
        this.scheduler.runGlobalTimer(task -> this.cleanupHarvestNodeDisplays(), HARVEST_DISPLAY_CLEANUP_INTERVAL, HARVEST_DISPLAY_CLEANUP_INTERVAL);
        this.scheduler.runGlobalTimer(task -> this.tickFruitNodeRegrowths(), FRUIT_REGROWTH_TICK_INTERVAL, FRUIT_REGROWTH_TICK_INTERVAL);
    }

    /**
     * 清理離線玩家的所有星球相關暑存資料，避免記憶體洩漏。
     */
    public void cleanupPlayer(final java.util.UUID playerId) {
        this.playerActivatedRuins.remove(playerId);
        this.personalHarvestCooldowns.remove(playerId);
        this.pendingHarvests.remove(playerId);
        this.boundaryWarningCooldowns.remove(playerId);
        this.cuisineWardExpiries.remove(playerId);
        this.travelingPlayers.remove(playerId);
        this.openPlanetaryGateMenus.remove(playerId);
        this.customTravelCallbacks.remove(playerId);
        if (this.travelVessels.containsKey(playerId)) {
            this.cleanupTravelVessel(playerId);
        }
        this.travelPlayerStates.remove(playerId);
        final RuinChallenge challenge = this.activeRuinChallenges.get(playerId);
        if (challenge != null) {
            this.cleanupRuinChallenge(challenge, true);
        }
    }

    public void shutdown() {
        this.pendingHarvests.clear();
        for (final java.util.UUID playerId : new ArrayList<>(this.travelVessels.keySet())) {
            this.cleanupTravelVessel(playerId);
        }
        this.travelingPlayers.clear();
        this.travelPlayerStates.clear();
        this.customTravelCallbacks.clear();
        this.boundaryWarningCooldowns.clear();
        this.cuisineWardExpiries.clear();
        for (final Map.Entry<LocationKey, java.util.UUID> entry : new ArrayList<>(this.harvestNodeDisplays.entrySet())) {
            this.removeHarvestNodeDisplay(entry.getKey(), entry.getValue());
        }
        this.harvestNodeDisplays.clear();
        this.fruitNodeRegrowths.clear();
        this.techItemStackCache.clear();
        // Folia: onDisable 在主控台線程，無法存取區域實體，伺服器關閉後實體自動消失
        // this.purgeManagedPlanetEntities();
    }

    public boolean handlePlanetBreak(final BlockBreakEvent event) {
        final Block block = event.getBlock();
        final PlanetDefinition definition = this.planetByWorld(block == null ? null : block.getWorld());
        if (definition == null) {
            return false;
        }
        event.setCancelled(true);
        event.setDropItems(false);
        final Player player = event.getPlayer();
        final String placedItemId = this.plugin.getPlacedTechBlockService().placedItemId(block);
        if (this.fruitTreeProfileForFruitId(placedItemId) != null) {
            player.sendActionBar(this.itemFactory.warning("請直接右鍵採果，不要把整顆果實節點敲掉。"));
            player.playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 1.18f);
            return true;
        }
        if (this.fruitTreeProfileForSaplingId(placedItemId) != null) {
            player.sendActionBar(this.itemFactory.warning("這株異星果苗需要骨粉培育，不可直接破壞。"));
            player.playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.32f, 0.86f);
            return true;
        }
        final PlanetHarvestProfile profile = this.harvestProfileFor(definition, block.getType(), false);
        if (profile != null && this.isHarvestSurfaceNode(block)) {
            player.sendActionBar(this.itemFactory.warning("請對節點右鍵採集，不能直接挖掘。"));
            player.playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 1.1f);
            this.ensureHarvestNodeDisplay(block, profile);
            this.updateHarvestNodeVisibility(player, block, profile);
            return true;
        }
        player.sendActionBar(this.itemFactory.warning("星球地表已鎖定，不可直接破壞。"));
        player.playSound(block.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.7f);
        return true;
    }

    public boolean handlePlanetInteract(final Player player, final Block block, final ItemStack heldItem) {
        final PlanetDefinition definition = this.planetByWorld(block == null ? null : block.getWorld());
        if (player == null || block == null) {
            return false;
        }
        if (this.handleFruitTreeInteract(player, block, heldItem, definition)) {
            return true;
        }
        if (definition == null) {
            return false;
        }
        // 戰利品桶自動刷新（不攔截互動，讓玩家正常打開桶）
        if (block.getType() == Material.BARREL) {
            this.refillLootBarrel(block);
            return false;
        }
        // 迷途星：中心 SCULK_SHRIEKER 召喚 Boss
        if (LABYRINTH_WORLD.equals(block.getWorld().getName())
                && block.getType() == Material.SCULK_SHRIEKER
                && com.rui.techproject.service.MazeService.getMazeZone(block.getX(), block.getZ()) == 0) {
            this.plugin.getMazeService().trySpawnBoss(player);
            return true;
        }
        if (block.getType() != definition.ruinCoreMaterial()) {
            return this.collectPlanetSurfaceNode(player, block, definition);
        }
        final LocationKey key = LocationKey.from(block.getLocation());
        final boolean globallyActivated = this.activatedRuins.contains(key);
        final boolean playerActivated = this.hasPlayerActivatedRuin(player.getUniqueId(), key);
        // 正在進行中的挑戰
        if (this.activeRuinChallenges.containsKey(player.getUniqueId())) {
            player.sendMessage(this.itemFactory.warning("遺跡挑戰進行中，先擊退所有敵人！"));
            return true;
        }
        if (globallyActivated && playerActivated) {
            player.sendMessage(this.itemFactory.warning(definition.displayName() + " 的遺跡核心已經完成同步。"));
            block.getWorld().spawnParticle(Particle.ENCHANT, block.getLocation().add(0.5, 0.8, 0.5), 10, 0.25, 0.2, 0.25, 0.02);
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.35f, 1.2f);
            return true;
        }
        final String heldId = this.itemFactory.getTechItemId(heldItem);
        if (!definition.ruinActivationItemId().equalsIgnoreCase(heldId)) {
            player.sendMessage(this.itemFactory.warning("這座遺跡需要「" + this.itemFactory.displayNameForId(definition.ruinActivationItemId()) + "」才能啟動。"));
            return true;
        }
        this.consumeHeldItem(player, heldItem);
        this.activatedRuins.add(key);
        this.markPlayerActivatedRuin(player.getUniqueId(), key);
        // 啟動遺跡挑戰（波次戰鬥）
        block.getWorld().spawnParticle(Particle.END_ROD, block.getLocation().add(0.5, 1.0, 0.5), 30, 0.5, 0.5, 0.5, 0.04);
        block.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, block.getLocation().add(0.5, 1.1, 0.5), 15, 0.6, 0.6, 0.6, 0.01);
        block.getWorld().spawnParticle(Particle.ENCHANT, block.getLocation().add(0.5, 1.5, 0.5), 25, 0.3, 0.4, 0.3, 1.0);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.05f);
        this.startRuinChallenge(player, definition, block.getLocation());
        return true;
    }

    public List<ItemStack> previewMachineHarvest(final Block block) {
        return this.harvestPlanetBlockForMachine(block, false);
    }

    public List<ItemStack> harvestForMachine(final Block block) {
        return this.harvestPlanetBlockForMachine(block, true);
    }

    public boolean teleportToPlanet(final Player player, final String planetId) {
        final PlanetDefinition definition = this.planets.get(planetId == null ? "" : planetId.toLowerCase(Locale.ROOT));
        if (definition == null) {
            return false;
        }
        final World world = this.ensurePlanetWorld(definition);
        if (player == null || world == null) {
            return false;
        }
        // 進入迷途星前記下返回點，供撤離火箭送玩家回來時使用
        // 注意：若玩家剛跑完火箭動畫（經異界傳送門觸發），不要用當前動畫位置覆寫 —
        // handleOtherworldPortalInteract 已事先在地面記錄了真正的返回點。
        if ("labyrinth".equalsIgnoreCase(definition.id())
                && !this.isPlanetWorld("labyrinth", player.getWorld())
                && !this.travelingPlayers.contains(player.getUniqueId())
                && !this.customTravelCallbacks.containsKey(player.getUniqueId())
                && this.plugin.getMazeService().returnPointFor(player.getUniqueId()) == null) {
            this.plugin.getMazeService().rememberReturnPoint(player, player.getLocation());
        }
        // 教學鏈：進入星球傳送門
        if (this.plugin.getTutorialChainService() != null) {
            this.plugin.getTutorialChainService().onPlanetEnter(player);
        }
        final Location anchor = new Location(world, 0.5, Math.max(world.getMinHeight() + 8, 80), 0.5);
        this.scheduler.runRegion(anchor, task -> {
            final Location spawn = this.planetArrivalLocation(world);
            this.scheduler.runEntity(player, () -> player.teleportAsync(spawn));
        });
        return true;
    }

    public boolean isWorldCreationUnsupported() {
        return this.worldCreationUnsupported;
    }

    public void recoverTravelState(final Player player) {
        if (player == null) {
            return;
        }
        this.cleanupTravelVessel(player.getUniqueId());
        if (player.getVehicle() != null) {
            player.leaveVehicle();
        }
        final boolean hadWalkLock = player.getWalkSpeed() <= 0.0f;
        final boolean hadFlyLock = player.getFlySpeed() <= 0.0f;
        final boolean wasTraveling = this.travelingPlayers.remove(player.getUniqueId());
        final TravelPlayerState state = this.travelPlayerStates.remove(player.getUniqueId());
        if (wasTraveling || state != null) {
            this.restoreTravelPlayerState(player, state, true);
            player.sendMessage(this.itemFactory.warning("已清除上一段中斷的星門傳送狀態。"));
            return;
        }
        if (hadWalkLock) {
            player.setWalkSpeed(0.2f);
        }
        if (hadFlyLock) {
            player.setFlySpeed(0.1f);
        }
        if (hadWalkLock || hadFlyLock) {
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.removePotionEffect(PotionEffectType.NAUSEA);
            player.clearTitle();
            player.sendMessage(this.itemFactory.warning("已恢復移動速度；若剛剛卡在觀察者模式，請手動切回生存或創造。"));
        }
    }

    /**
     * 外部系統（區域系統等）呼叫：啟動完整火箭動畫，動畫結束後執行 onComplete 回調。
     * 播放期間玩家被鎖定在觀察者模式 + 飛船鏡頭，動畫結束後狀態會自動恢復；
     * 但 walkSpeed / flySpeed 仍為 0，呼叫端應自行在 onComplete 中恢復速度。
     */
    public void startCustomTravel(final Player player,
                                  final Location origin,
                                  final java.util.function.Consumer<Player> onComplete) {
        final java.util.UUID uuid = player.getUniqueId();
        if (this.travelingPlayers.contains(uuid)) {
            return;
        }
        this.customTravelCallbacks.put(uuid, onComplete);
        final TravelDestination dummy = new TravelDestination("custom_rtp", "未知區域", origin.getWorld(), null);
        this.startTravelAnimation(player, origin, dummy);
    }

    public boolean isCustomTraveling(final java.util.UUID playerId) {
        return this.customTravelCallbacks.containsKey(playerId);
    }

    public void handleChunkLoad(final Chunk chunk) {
        if (chunk == null) {
            return;
        }
        final PlanetDefinition definition = this.planetByWorld(chunk.getWorld());
        if (definition == null) {
            return;
        }
        this.processPlanetDecorationChunk(definition, chunk, false);
    }

    public void handleChunkUnload(final Chunk chunk) {
        if (chunk == null || this.planetByWorld(chunk.getWorld()) == null) {
            return;
        }
        for (final Map.Entry<LocationKey, java.util.UUID> entry : new ArrayList<>(this.harvestNodeDisplays.entrySet())) {
            final LocationKey key = entry.getKey();
            if (!key.worldName().equalsIgnoreCase(chunk.getWorld().getName())) {
                continue;
            }
            if ((key.x() >> 4) != chunk.getX() || (key.z() >> 4) != chunk.getZ()) {
                continue;
            }
            this.removeHarvestNodeDisplay(entry.getKey(), entry.getValue());
        }
    }

    public static ChunkGenerator generatorForWorld(final String worldName, final String id) {
        final String normalizedId = normalizePlanetKey(id);
        if (normalizedId != null) {
            return new PlanetChunkGenerator(normalizedId);
        }
        final String normalizedWorld = normalizePlanetKey(worldName);
        if (normalizedWorld != null) {
            return new PlanetChunkGenerator(normalizedWorld);
        }
        return null;
    }

    public boolean handlePlanetaryGateInteract(final Player player,
                                               final Block gateBlock,
                                               final PlacedMachine gateMachine) {
        if (player == null || gateBlock == null || gateMachine == null) {
            return false;
        }
        if (this.travelingPlayers.contains(player.getUniqueId())) {
            player.sendMessage(this.itemFactory.warning("星門傳送程序仍在進行中。"));
            return true;
        }
        this.openPlanetaryGateMenu(player, gateBlock);
        final World world = gateBlock.getWorld();
        if (world != null) {
            world.playSound(gateBlock.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.42f, 1.18f);
            world.spawnParticle(Particle.END_ROD, gateBlock.getLocation().add(0.5, 1.0, 0.5), 8, 0.28, 0.18, 0.28, 0.02);
        }
        return true;
    }

    /**
     * 「異界傳送門」右鍵互動：開啟異界專屬 GUI（背景為 techproject_labyrinth_hud glyph）。
     * 玩家在 GUI 內點擊傳送按鈕後才會消耗魔力隕石並啟動火箭躍遷至異世界。
     */
    public boolean handleOtherworldPortalInteract(final Player player,
                                                   final Block portalBlock,
                                                   final PlacedMachine portalMachine) {
        if (player == null || portalBlock == null || portalMachine == null) {
            return false;
        }
        if (this.travelingPlayers.contains(player.getUniqueId())) {
            player.sendMessage(this.itemFactory.warning("異界躍遷程序仍在進行中。"));
            return true;
        }
        this.openOtherworldPortalMenu(player);
        return true;
    }

    /** 開啟異界傳送門 GUI（54 格，背景以 font glyph 呈現 generic.png）。 */
    public void openOtherworldPortalMenu(final Player player) {
        if (player == null) {
            return;
        }
        final OtherworldPortalHolder holder = new OtherworldPortalHolder(player.getUniqueId());
        final net.kyori.adventure.text.Component title = net.kyori.adventure.text.Component
                .text(LABYRINTH_MENU_TITLE, net.kyori.adventure.text.format.NamedTextColor.WHITE)
                .font(LABYRINTH_MENU_FONT);
        final org.bukkit.inventory.Inventory inv = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inv);
        // 背景填充：以透明 PAPER pane（同星球傳送門 hover item）填滿 1-7 欄 × 5 列（slots 1-7, 10-16, 19-25, 28-34, 37-43）
        final org.bukkit.inventory.ItemStack filler = this.itemFactory.buildGuiPane(
                "otherworld-portal-pane",
                Material.PAPER,
                net.kyori.adventure.text.Component
                        .text(" ", net.kyori.adventure.text.format.NamedTextColor.WHITE)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                java.util.List.of(),
                false
        );
        for (int row = 0; row < 5; row++) {
            for (int col = 1; col <= 7; col++) {
                inv.setItem(row * 9 + col, filler.clone());
            }
        }
        // 傳送按鈕：中央槽位覆寫為可點擊的透明按鈕（同樣透明 PAPER pane，只是附帶顯示名與 lore）
        final org.bukkit.inventory.ItemStack button = this.itemFactory.buildGuiPane(
                "otherworld-portal-pane",
                Material.PAPER,
                net.kyori.adventure.text.Component
                        .text("▶ 傳送至異世界", net.kyori.adventure.text.format.NamedTextColor.WHITE)
                        .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                java.util.List.of(
                        net.kyori.adventure.text.Component
                                .text("消耗 1 顆 魔力隕石 啟動異界躍遷", net.kyori.adventure.text.format.TextColor.color(0xB186D8))
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                        net.kyori.adventure.text.Component
                                .text("返回點已鎖定此處", net.kyori.adventure.text.format.TextColor.color(0x8FE3B4))
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
                ),
                false
        );
        for (final int slot : OTHERWORLD_PORTAL_BUTTON_SLOTS) {
            inv.setItem(slot, button.clone());
        }
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 1.2f);
    }

    /** 處理異界傳送門 GUI 的點擊：任何按鈕槽位點擊 → 檢查隕石、消耗、關閉 GUI、啟動火箭。 */
    public void handleOtherworldPortalMenuClick(final Player player, final int rawSlot) {
        if (player == null) {
            return;
        }
        boolean buttonSlot = false;
        for (final int slot : OTHERWORLD_PORTAL_BUTTON_SLOTS) {
            if (slot == rawSlot) {
                buttonSlot = true;
                break;
            }
        }
        if (!buttonSlot) {
            return;
        }
        if (this.travelingPlayers.contains(player.getUniqueId())) {
            player.sendActionBar(this.itemFactory.warning("異界躍遷程序仍在進行中。"));
            return;
        }
        final org.bukkit.inventory.ItemStack main = player.getInventory().getItemInMainHand();
        final String mainId = this.itemFactory.getTechItemId(main);
        if (mainId == null || !"mana_meteorite".equalsIgnoreCase(mainId) || main.getAmount() < 1) {
            player.sendActionBar(this.itemFactory.warning("需要手持 魔力隕石 啟動異界傳送門。"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.4f, 0.7f);
            return;
        }
        // 消耗 1 顆魔力隕石
        main.setAmount(main.getAmount() - 1);
        // 記錄返回點
        this.plugin.getMazeService().rememberReturnPoint(player, player.getLocation());
        // 關閉 GUI
        player.closeInventory();
        // 視覺 & 音效
        final World world = player.getWorld();
        if (world != null) {
            world.playSound(player.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 1.0f, 0.6f);
            world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 0.9f);
            world.spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1.5, 0), 80, 0.6, 1.2, 0.6, 0.3);
            world.spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.2, 0), 40, 0.4, 0.6, 0.4, 0.04);
        }
        // 啟動火箭 → 抵達後送入迷途星
        this.startCustomTravel(player, player.getLocation(), arrived -> {
            if (arrived == null || !arrived.isOnline()) {
                return;
            }
            this.teleportToPlanet(arrived, "labyrinth");
            arrived.sendActionBar(this.itemFactory.success("✦ 已抵達異世界 — 尋找撤離點返回 ✦"));
        });
    }

    public boolean isPlanetaryGateView(final String title) {
        return title != null && title.contains(PLANETARY_GATE_MENU_GLYPH);
    }

    public void closePlanetaryGateMenu(final Player player) {
        if (player == null) {
            return;
        }
        this.openPlanetaryGateMenus.remove(player.getUniqueId());
    }

    public void handlePlanetaryGateMenuClick(final Player player,
                                             final int rawSlot) {
        if (player == null || rawSlot < 0 || rawSlot >= 54) {
            return;
        }
        final PlanetaryGateButtonLayout layout = this.planetaryGateLayoutAt(rawSlot);
        if (layout == null) {
            return;
        }
        final LocationKey gateKey = this.openPlanetaryGateMenus.get(player.getUniqueId());
        if (gateKey == null) {
            player.sendMessage(this.itemFactory.warning("找不到這座星門的定位資料。"));
            player.closeInventory();
            return;
        }
        final World world = Bukkit.getWorld(gateKey.worldName());
        if (world == null) {
            player.sendMessage(this.itemFactory.warning("這座星門目前不在已載入世界中。"));
            player.closeInventory();
            return;
        }
        this.tryStartPlanetaryGateTravel(player, world.getBlockAt(gateKey.x(), gateKey.y(), gateKey.z()), layout.targetId());
    }

    public Collection<String> planetIds() {
        return this.planets.keySet();
    }

    public org.bukkit.Location locateRuinCore(final Player player) {
        final World world = player.getWorld();
        final PlanetDefinition definition = this.planetByWorld(world);
        if (definition == null) {
            return null;
        }
        final int baseY = this.terrainPlateauY(world, 0, 0, definition.id().equals("nyx") ? 7 : 8) + 2;
        final int[] offset = switch (definition.id()) {
            case "aurelia"  -> new int[]{0, 7};
            case "cryon"    -> new int[]{3, 8};
            case "nyx"      -> new int[]{2, 7};
            case "helion"   -> new int[]{-1, 8};
            case "tempest"  -> new int[]{1, 7};
            default -> null;
        };
        if (offset == null) {
            return null;
        }
        final int coreX = offset[0];
        final int coreY = baseY + 2;
        final int coreZ = offset[1];
        // 自動修復：如果遺跡核心方塊不在，重新放置
        final Block coreBlock = world.getBlockAt(coreX, coreY, coreZ);
        if (coreBlock.getType() != definition.ruinCoreMaterial()) {
            coreBlock.setType(definition.ruinCoreMaterial(), false);
            this.highlightRuinCore(world, coreX, coreY, coreZ);
            // 同時確保降落台標記也在
            final Block markerBlock = world.getBlockAt(0, baseY + 1, 0);
            if (markerBlock.getType() != definition.markerMaterial()) {
                markerBlock.setType(definition.markerMaterial(), false);
            }
        }
        return new org.bukkit.Location(world, coreX + 0.5, coreY, coreZ + 0.5);
    }

    /**
     * 強制重新生成指定星球的出生點結構（降落台、遺跡環、尖塔、遺跡核心等）。
     */
    public boolean regenerateSpawnStructures(final Player player) {
        final World world = player.getWorld();
        final PlanetDefinition definition = this.planetByWorld(world);
        if (definition == null) {
            return false;
        }
        switch (definition.id()) {
            case "aurelia" -> this.generateAurelia(world, definition.markerMaterial(), definition.ruinCoreMaterial());
            case "cryon" -> this.generateCryon(world, definition.markerMaterial(), definition.ruinCoreMaterial());
            case "nyx" -> this.generateNyx(world, definition.markerMaterial(), definition.ruinCoreMaterial());
            case "helion" -> this.generateHelion(world, definition.markerMaterial(), definition.ruinCoreMaterial());
            case "tempest" -> this.generateTempest(world, definition.markerMaterial(), definition.ruinCoreMaterial());
            default -> { return false; }
        }
        return true;
    }

    public String planetDisplayName(final String planetId) {
        final PlanetDefinition definition = this.planets.get(planetId == null ? "" : planetId.toLowerCase(Locale.ROOT));
        return definition == null ? planetId : definition.displayName();
    }

    public List<String> planetInfoLines() {
        return this.planets.values().stream()
                .map(definition -> definition.displayName() + "：" + this.hazardLabel(definition.hazardType()) + " / " + this.gravityLabel(definition) + "，" + this.planetProtectionHint(definition))
                .toList();
    }

    public List<String> planetDebugLines(final Player player) {
        final List<String> lines = new ArrayList<>();
        lines.add("§e=== 星球除錯資訊 ===");

        // 玩家當前世界
        final World currentWorld = player.getWorld();
        final PlanetDefinition currentPlanet = this.planetByWorld(currentWorld);
        lines.add("§7你的世界: §f" + currentWorld.getName());
        lines.add("§7是否為星球: " + (currentPlanet != null ? "§a是 (" + currentPlanet.displayName() + ")" : "§c否"));
        lines.add("");

        for (final PlanetDefinition definition : this.planets.values()) {
            final World world = this.resolveExistingPlanetWorld(definition);
            lines.add("§6【" + definition.displayName() + "】 §7id=" + definition.id());
            if (world == null) {
                lines.add("  §c世界未載入 (worldName=" + definition.worldName() + ")");
                lines.add("");
                continue;
            }
            lines.add("  §7世界名: §f" + world.getName());
            lines.add("  §7環境: §f" + world.getEnvironment());
            lines.add("  §7難度: §f" + world.getDifficulty());
            lines.add("  §7生怪旗標(怪物/動物): §f" + world.getAllowMonsters() + " / " + world.getAllowAnimals());
            final int monsterCount = (int) world.getEntities().stream()
                    .filter(e -> e instanceof Monster).count();
            final int eliteCount = (int) world.getEntities().stream()
                    .filter(e -> e instanceof Monster && e.getScoreboardTags().contains(PLANET_ELITE_TAG)).count();
            final int totalEntities = world.getEntities().size();
            final int loadedChunks = world.getLoadedChunks().length;
            lines.add("  §7載入區塊: §f" + loadedChunks);
            lines.add("  §7實體總數: §f" + totalEntities + " §7(怪物: §f" + monsterCount + "§7, 精英: §f" + eliteCount + "§7)");
            final PlanetEliteProfile profile = this.eliteProfileFor(definition);
            if (profile != null) {
                lines.add("  §7精英名稱: §d" + profile.displayName() + " §7(掉落: " + profile.combatSampleId() + ", " + profile.bonusSampleId() + ")");
            }
            lines.add("");
        }
        return lines;
    }

    /**
     * 生怪診斷 — 手動嘗試在玩家附近找位置並生成怪物，回報每個步驟的結果。
     * 這個方法專門在 entity thread 上執行，因此直接在附近 region 做測試。
     */
    public List<String> planetSpawnTestLines(final Player player) {
        final List<String> lines = new ArrayList<>();
        lines.add("§e=== 星球生怪診斷 ===");
        final World world = player.getWorld();
        final PlanetDefinition definition = this.planetByWorld(world);
        if (definition == null) {
            lines.add("§c你不在星球世界中。");
            return lines;
        }
        lines.add("§7星球: §f" + definition.displayName() + " §7(" + definition.id() + ")");
        lines.add("§7你的位置: §f" + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ());
        lines.add("§7世界名: §f" + world.getName());
        lines.add("§7resolveExistingPlanetWorld: §f" + (this.resolveExistingPlanetWorld(definition) != null ? "✔" : "✘ null"));
        lines.add("");

        // 測試座標計算（純數學，不存取方塊）
        lines.add("§e── 座標計算測試 (10次) ──");
        final Location base = player.getLocation();
        final java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        int chunkLoadedCount = 0;
        for (int i = 0; i < 10; i++) {
            final double angle = rng.nextDouble() * Math.PI * 2.0D;
            final int radius = MOB_SPAWN_MIN_DISTANCE + rng.nextInt(MOB_SPAWN_MAX_DISTANCE - MOB_SPAWN_MIN_DISTANCE + 1);
            final int x = base.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            final int z = base.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            final boolean chunkLoaded = world.isChunkLoaded(x >> 4, z >> 4);
            if (chunkLoaded) {
                chunkLoadedCount++;
                lines.add("§a  #" + (i + 1) + " (" + x + "," + z + ") r=" + radius + " → 區塊已載入 ✔");
            } else {
                lines.add("§c  #" + (i + 1) + " (" + x + "," + z + ") r=" + radius + " → 區塊未載入 ✘");
            }
        }
        lines.add("§7區塊已載入: §f" + chunkLoadedCount + "/10");
        lines.add("");

        // 測試直接在玩家腳下位置生成（同 region，一定安全）
        lines.add("§e── 直接生怪測試（玩家附近） ──");
        final org.bukkit.entity.EntityType mobType = this.planetMobTypeFor(definition);
        lines.add("§7  怪物類型: §f" + mobType);
        // 在玩家面前 3 格生成
        final Location testLoc = base.clone().add(base.getDirection().normalize().multiply(3));
        testLoc.setY(base.getBlockY());
        try {
            final Entity spawned = world.spawnEntity(testLoc, mobType);
            if (spawned instanceof LivingEntity living) {
                this.applyPlanetMobSpawn(spawned, testLoc, mobType, definition);
                lines.add("§a  ✔ 生成成功！ " + spawned.getType() + " at " + testLoc.getBlockX() + "," + testLoc.getBlockY() + "," + testLoc.getBlockZ());
                lines.add("§7  entityId=" + spawned.getEntityId());
            } else {
                lines.add("§e  生成了非 LivingEntity: " + spawned.getType());
            }
        } catch (final Exception ex) {
            lines.add("§c  ✘ 生成失敗: " + ex.getClass().getSimpleName() + " — " + ex.getMessage());
        }
        return lines;
    }

    public boolean applyPlanetCuisineBuff(final Player player, final String techItemId) {
        if (player == null || techItemId == null || techItemId.isBlank()) {
            return false;
        }
        return switch (techItemId.toLowerCase(Locale.ROOT)) {
            case "aurelia_glaze" -> {
                this.grantCuisineWard(player, HazardType.RADIATION, 20L * 180L, "輻晶灼蝕");
                yield true;
            }
            case "rad_filter_pill" -> {
                this.grantCuisineWard(player, HazardType.RADIATION, 20L * 90L, "輻射濾除");
                yield true;
            }
            case "cryon_hotpot" -> {
                this.grantCuisineWard(player, HazardType.CRYO, 20L * 180L, "凍骨寒流");
                yield true;
            }
            case "nyx_phase_gel" -> {
                this.grantCuisineWard(player, HazardType.VACUUM, 20L * 180L, "相位失壓");
                yield true;
            }
            case "helion_sorbet" -> {
                this.grantCuisineWard(player, HazardType.SOLAR, 20L * 180L, "日冕灼燒");
                yield true;
            }
            case "tempest_fizz" -> {
                this.grantCuisineWard(player, HazardType.STORM, 20L * 180L, "導電亂流");
                yield true;
            }
            case "labyrinth_salve" -> {
                this.grantCuisineWard(player, HazardType.MIASMA, 20L * 180L, "瘴氣消散");
                yield true;
            }
            default -> false;
        };
    }

    public boolean isPlanetWorld(final World world) {
        return this.planetByWorld(world) != null;
    }

    public void tryEmpowerPlanetMob(final LivingEntity entity) {
        if (!(entity instanceof Monster monster)) {
            return;
        }
        final PlanetDefinition definition = this.planetByWorld(entity.getWorld());
        if (definition == null || entity.getScoreboardTags().contains(PLANET_ELITE_TAG)) {
            return;
        }

        // ── 迷途星分層：越外圈精英率越高，強度越強 ──
        double labyrinthStrengthBonus = 0.0; // 額外屬性倍率（加成到 profile 上）
        int eliteChanceDenominator = 8;      // 預設 1/8 精英率
        if ("labyrinth".equals(definition.id())) {
            final int zone = this.plugin.getMazeService().getMazeZoneForLocation(entity.getLocation());
            switch (zone) {
                case 0, 1 -> {
                    // Glade / 內圈：普通怪（不精英化）
                    return;
                }
                case 2 -> {
                    // 中圈：15% 精英率，+20% 強度
                    eliteChanceDenominator = 7; // ~15%
                    labyrinthStrengthBonus = 0.2;
                }
                case 3 -> {
                    // 外圈：30% 精英率，+40% 強度
                    eliteChanceDenominator = 3; // ~33%
                    labyrinthStrengthBonus = 0.4;
                }
                default -> {
                }
            }
            // 全迷途星：對 Zone 2/3 普通怪也加基礎屬性加成（不管是否精英化）
            if (zone >= 2) {
                this.adjustAttribute(monster, Attribute.MAX_HEALTH, 1.0 + labyrinthStrengthBonus, 0.0D, 4.0D);
                final var hp = monster.getAttribute(Attribute.MAX_HEALTH);
                if (hp != null) {
                    monster.setHealth(Math.max(1.0D, hp.getValue()));
                }
                this.adjustAttribute(monster, Attribute.ATTACK_DAMAGE, 1.0 + labyrinthStrengthBonus, 0.0D, 1.5D);
            }
        }

        if (this.ambientRandom.nextInt(eliteChanceDenominator) != 0) {
            return;
        }
        if (this.countNearbyPlanetElites(entity) >= 3) {
            return;
        }
        final PlanetEliteProfile profile = this.eliteProfileFor(definition);
        if (profile == null) {
            return;
        }
        monster.addScoreboardTag(PLANET_ELITE_TAG);
        monster.addScoreboardTag(PLANET_ELITE_TAG_PREFIX + definition.id());
        monster.customName(Component.text(profile.displayName()));
        monster.setCustomNameVisible(true);
        this.adjustAttribute(monster, Attribute.MAX_HEALTH, profile.healthMultiplier() + labyrinthStrengthBonus, 0.0D, 8.0D);
        final var maxHealth = monster.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            monster.setHealth(Math.max(1.0D, maxHealth.getValue()));
        }
        this.adjustAttribute(monster, Attribute.ATTACK_DAMAGE, 1.0D, profile.attackBonus() + labyrinthStrengthBonus, 2.0D);
        this.adjustAttribute(monster, Attribute.MOVEMENT_SPEED, 1.0D, profile.speedBonus(), 0.22D);
        this.adjustAttribute(monster, Attribute.FOLLOW_RANGE, 1.0D, 8.0D, 24.0D);
        switch (definition.id()) {
            case "aurelia" -> monster.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 60 * 5, 0, true, false, true));
            case "cryon" -> monster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 60 * 5, 0, true, false, true));
            case "nyx" -> monster.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 60 * 5, 0, true, false, true));
            case "helion" -> monster.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 60 * 5, 0, true, false, true));
            case "tempest" -> monster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 60 * 5, 1, true, false, true));
            default -> {
            }
        }
    }

    public void handlePlanetEliteDeath(final LivingEntity entity, final Player killer, final List<ItemStack> drops) {
        if (entity == null || killer == null || drops == null || !entity.getScoreboardTags().contains(PLANET_ELITE_TAG)) {
            return;
        }
        final PlanetDefinition definition = this.planetByWorld(entity.getWorld());
        final PlanetEliteProfile profile = this.eliteProfileFor(definition);
        if (definition == null || profile == null) {
            return;
        }
        final ItemStack combatSample = this.buildTechStack(profile.combatSampleId(), 1);
        if (combatSample != null) {
            drops.add(combatSample);
            this.unlockPlanetItem(killer, profile.combatSampleId());
        }
        if (this.ambientRandom.nextInt(100) < 70) {
            final int bonusAmount = this.ambientRandom.nextInt(4) == 0 ? 2 : 1;
            final ItemStack bonusSample = this.buildTechStack(profile.bonusSampleId(), bonusAmount);
            if (bonusSample != null) {
                drops.add(bonusSample);
                this.unlockPlanetItem(killer, profile.bonusSampleId());
            }
        }
        this.plugin.getPlayerProgressService().incrementStat(killer.getUniqueId(), "planet_elites_defeated", 1L);
        entity.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, entity.getLocation().add(0.0D, 0.8D, 0.0D), 12, 0.35D, 0.35D, 0.35D, 0.02D);
        entity.getWorld().spawnParticle(Particle.END_ROD, entity.getLocation().add(0.0D, 0.9D, 0.0D), 8, 0.28D, 0.28D, 0.28D, 0.01D);
        this.eliteSkillCooldowns.remove(entity.getUniqueId());
        killer.sendActionBar(this.itemFactory.success("已擊退「" + profile.displayName() + "」，回收戰鬥樣本。"));
    }

    // ──────────────────────────────────────────────────────────────
    //  精英怪物技能系統 — 攻擊觸發 + 被動光環
    // ──────────────────────────────────────────────────────────────

    /**
     * 當精英怪攻擊玩家時觸發主動技能。
     * 由 TechListener#onEntityDamageByEntity 呼叫。
     */
    public void handleEliteSkillOnAttack(final Monster attacker, final Player victim) {
        if (!attacker.getScoreboardTags().contains(PLANET_ELITE_TAG)) {
            return;
        }
        final long now = System.currentTimeMillis();
        final Long nextAllowed = this.eliteSkillCooldowns.get(attacker.getUniqueId());
        if (nextAllowed != null && now < nextAllowed) {
            return;
        }
        final String planetId = this.identifyElitePlanet(attacker);
        if (planetId == null) {
            return;
        }
        boolean fired = switch (planetId) {
            case "aurelia" -> this.eliteSkillAureliaAttack(attacker, victim);
            case "cryon" -> this.eliteSkillCryonAttack(attacker, victim);
            case "nyx" -> this.eliteSkillNyxAttack(attacker, victim);
            case "helion" -> this.eliteSkillHelionAttack(attacker, victim);
            case "tempest" -> this.eliteSkillTempestAttack(attacker, victim);
            default -> false;
        };
        if (fired) {
            this.eliteSkillCooldowns.put(attacker.getUniqueId(), now + ELITE_SKILL_COOLDOWN_MS);
        }
    }

    /* ── Aurelia 輻塵寄生體 ─ 攻擊技能：輻射爆裂 ── */
    private boolean eliteSkillAureliaAttack(final Monster attacker, final Player victim) {
        final int roll = this.ambientRandom.nextInt(3);
        final Location loc = attacker.getLocation();
        final World world = attacker.getWorld();
        if (roll == 0) {
            // 孢子噴發 — 附近 5 格 AoE 毒 + 凋零
            world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, loc.clone().add(0, 1, 0), 40, 2.5, 1.5, 2.5, 0.01);
            world.spawnParticle(Particle.DUST, loc.clone().add(0, 1, 0), 25, 2.5, 1.5, 2.5, 0.0,
                    new Particle.DustOptions(Color.fromRGB(100, 200, 50), 1.4f));
            world.playSound(loc, Sound.ENTITY_PUFFER_FISH_BLOW_UP, SoundCategory.HOSTILE, 1.2f, 0.6f);
            for (final Entity nearby : attacker.getNearbyEntities(ELITE_SKILL_RANGE, 3.0, ELITE_SKILL_RANGE)) {
                if (nearby instanceof Player p && p.getGameMode() == GameMode.SURVIVAL) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1, true, true, true));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, true, true, true));
                    p.sendActionBar(this.itemFactory.danger("⚠ 輻塵孢子噴發！"));
                }
            }
        } else {
            // 寄生注射 — 單體中毒 + 飢餓
            victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 1, true, true, true));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 120, 1, true, true, true));
            world.spawnParticle(Particle.ITEM_SLIME, victim.getLocation().add(0, 0.5, 0), 15, 0.3, 0.5, 0.3, 0.02);
            world.playSound(victim.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, SoundCategory.HOSTILE, 1.0f, 0.5f);
            victim.sendActionBar(this.itemFactory.danger("⚠ 寄生注射—正在侵蝕身體！"));
        }
        return true;
    }

    /* ── Cryon 霜脊潛獵者 ─ 攻擊技能：冰鋒突刺 ── */
    private boolean eliteSkillCryonAttack(final Monster attacker, final Player victim) {
        final int roll = this.ambientRandom.nextInt(3);
        final Location loc = attacker.getLocation();
        final World world = attacker.getWorld();
        if (roll == 0) {
            // 極寒衝擊波 — AoE 緩慢 + 冰凍
            world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 50, 3.0, 1.5, 3.0, 0.05);
            world.spawnParticle(Particle.BLOCK_CRUMBLE, loc.clone().add(0, 0.5, 0), 20, 2.0, 0.5, 2.0, 0.0,
                    Material.PACKED_ICE.createBlockData());
            world.playSound(loc, Sound.BLOCK_GLASS_BREAK, SoundCategory.HOSTILE, 1.3f, 0.5f);
            for (final Entity nearby : attacker.getNearbyEntities(ELITE_SKILL_RANGE, 3.0, ELITE_SKILL_RANGE)) {
                if (nearby instanceof Player p && p.getGameMode() == GameMode.SURVIVAL) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2, true, true, true));
                    p.setFreezeTicks(Math.min(p.getMaxFreezeTicks(), p.getFreezeTicks() + 120));
                    p.sendActionBar(this.itemFactory.danger("⚠ 極寒衝擊波—身體凍結中！"));
                }
            }
        } else if (roll == 1) {
            // 霜刺穿心 — 單體高傷 + 緩慢
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 3, true, true, true));
            victim.damage(4.0, attacker);
            world.spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 20, 0.3, 0.8, 0.3, 0.05);
            world.spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 10, 0.2, 0.5, 0.2, 0.1);
            world.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, SoundCategory.HOSTILE, 1.0f, 0.8f);
            victim.sendActionBar(this.itemFactory.danger("⚠ 霜刺穿心！"));
        } else {
            // 冰脊防衛 — 自身獲得抗性提升
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1, true, false, true));
            world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1.2, 0), 30, 0.8, 1.0, 0.8, 0.02);
            world.playSound(loc, Sound.BLOCK_POWDER_SNOW_STEP, SoundCategory.HOSTILE, 1.0f, 0.4f);
        }
        return true;
    }

    /* ── Nyx 虛響觀測者 ─ 攻擊技能：相位跳躍 ── */
    private boolean eliteSkillNyxAttack(final Monster attacker, final Player victim) {
        final int roll = this.ambientRandom.nextInt(3);
        final Location loc = attacker.getLocation();
        final World world = attacker.getWorld();
        if (roll == 0) {
            // 虛空閃現 — 傳送到玩家背後並給予失明
            final Vector behindVec = victim.getLocation().getDirection().normalize().multiply(-2.0);
            final Location behind = victim.getLocation().add(behindVec);
            behind.setY(victim.getLocation().getY());
            if (behind.getBlock().isPassable() && behind.clone().add(0, 1, 0).getBlock().isPassable()) {
                world.spawnParticle(Particle.REVERSE_PORTAL, loc.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
                attacker.teleportAsync(behind);
                world.spawnParticle(Particle.REVERSE_PORTAL, behind.clone().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
                world.playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0f, 1.2f);
            }
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, true, true, true));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, true, true, true));
            victim.sendActionBar(this.itemFactory.danger("⚠ 虛空閃現—視野被扭曲！"));
        } else if (roll == 1) {
            // 虛空牽引 — 將玩家拉向精英
            final Vector pull = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).normalize().multiply(1.2);
            pull.setY(0.35);
            victim.setVelocity(pull);
            world.spawnParticle(Particle.REVERSE_PORTAL, victim.getLocation().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0.1);
            world.playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 0.8f, 0.4f);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, true, true));
            victim.sendActionBar(this.itemFactory.danger("⚠ 虛空牽引—被拉入深淵！"));
        } else {
            // 相位迴避 — 給自身隱形 + 速度
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, true, false, true));
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, true, false, true));
            world.spawnParticle(Particle.REVERSE_PORTAL, loc.clone().add(0, 1, 0), 25, 0.5, 0.8, 0.5, 0.08);
            world.playSound(loc, Sound.ENTITY_PHANTOM_FLAP, SoundCategory.HOSTILE, 1.0f, 0.6f);
        }
        return true;
    }

    /* ── Helion 日灼焰獸 ─ 攻擊技能：太陽焰息 ── */
    private boolean eliteSkillHelionAttack(final Monster attacker, final Player victim) {
        final int roll = this.ambientRandom.nextInt(3);
        final Location loc = attacker.getLocation();
        final World world = attacker.getWorld();
        if (roll == 0) {
            // 日冕爆發 — AoE 火焰 + 高温灼傷
            world.spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 60, 3.0, 1.0, 3.0, 0.08);
            world.spawnParticle(Particle.LAVA, loc.clone().add(0, 1.5, 0), 15, 2.0, 0.5, 2.0, 0.0);
            world.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.2f, 0.6f);
            for (final Entity nearby : attacker.getNearbyEntities(ELITE_SKILL_RANGE, 3.0, ELITE_SKILL_RANGE)) {
                if (nearby instanceof Player p && p.getGameMode() == GameMode.SURVIVAL) {
                    p.setFireTicks(Math.max(p.getFireTicks(), 80));
                    p.damage(3.0, attacker);
                    p.sendActionBar(this.itemFactory.danger("⚠ 日冕爆發—高温灼傷！"));
                }
            }
        } else if (roll == 1) {
            // 隕石轟擊 — 延遲落點攻擊
            final Location impact = victim.getLocation().clone();
            world.spawnParticle(Particle.DUST, impact.clone().add(0, 0.2, 0), 20, 1.5, 0.1, 1.5, 0.0,
                    new Particle.DustOptions(Color.fromRGB(255, 80, 0), 2.0f));
            world.playSound(impact, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.HOSTILE, 1.0f, 0.5f);
            victim.sendActionBar(this.itemFactory.danger("⚠ 注意腳下—隕石即將落下！"));
            this.scheduler.runRegionDelayed(impact, task -> {
                if (!impact.isWorldLoaded()) {
                    return;
                }
                world.spawnParticle(Particle.EXPLOSION, impact.clone().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0.0);
                world.spawnParticle(Particle.FLAME, impact.clone().add(0, 0.5, 0), 40, 2.0, 0.5, 2.0, 0.08);
                world.spawnParticle(Particle.LAVA, impact, 10, 1.5, 0.3, 1.5, 0.0);
                world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, 0.7f);
                for (final Entity nearby : world.getNearbyEntities(impact, 3.0, 3.0, 3.0)) {
                    if (nearby instanceof Player p && p.getGameMode() == GameMode.SURVIVAL) {
                        p.damage(6.0);
                        p.setFireTicks(Math.max(p.getFireTicks(), 60));
                    }
                }
            }, 30L);
        } else {
            // 熔岩護盾 — 自身抗火 + 抗性
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 200, 0, true, false, true));
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1, true, false, true));
            world.spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 30, 0.6, 1.0, 0.6, 0.03);
            world.playSound(loc, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.HOSTILE, 1.0f, 0.4f);
        }
        return true;
    }

    /* ── Tempest 雷殻追獵者 ─ 攻擊技能：風暴鏈擊 ── */
    private boolean eliteSkillTempestAttack(final Monster attacker, final Player victim) {
        final int roll = this.ambientRandom.nextInt(3);
        final Location loc = attacker.getLocation();
        final World world = attacker.getWorld();
        if (roll == 0) {
            // 雷擊召喚 — 在玩家位置召喚閃電
            final Location strikeLoc = victim.getLocation();
            world.strikeLightningEffect(strikeLoc);
            victim.damage(5.0, attacker);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, true, true, true));
            world.spawnParticle(Particle.ELECTRIC_SPARK, strikeLoc.clone().add(0, 1, 0), 20, 0.5, 1.0, 0.5, 0.1);
            victim.sendActionBar(this.itemFactory.danger("⚠ 雷擊召喚—被雷電擊中！"));
        } else if (roll == 1) {
            // 風暴衝擊波 — AoE 擊退
            world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 40, 3.0, 1.0, 3.0, 0.1);
            world.spawnParticle(Particle.ELECTRIC_SPARK, loc.clone().add(0, 1, 0), 25, 3.0, 1.0, 3.0, 0.08);
            world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 0.8f, 1.5f);
            for (final Entity nearby : attacker.getNearbyEntities(ELITE_SKILL_RANGE, 3.0, ELITE_SKILL_RANGE)) {
                if (nearby instanceof Player p && p.getGameMode() == GameMode.SURVIVAL) {
                    final Vector kb = p.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.8);
                    kb.setY(0.6);
                    p.setVelocity(kb);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 0, true, true, true));
                    p.sendActionBar(this.itemFactory.danger("⚠ 風暴衝擊波—被擊飛！"));
                }
            }
        } else {
            // 鏈式閃電 — 連鎖打擊附近多名玩家
            final List<Player> targets = new java.util.ArrayList<>();
            targets.add(victim);
            for (final Entity nearby : attacker.getNearbyEntities(8.0, 4.0, 8.0)) {
                if (nearby instanceof Player p && p != victim && p.getGameMode() == GameMode.SURVIVAL) {
                    targets.add(p);
                    if (targets.size() >= 3) {
                        break;
                    }
                }
            }
            for (int i = 0; i < targets.size(); i++) {
                final Player target = targets.get(i);
                final double dmg = Math.max(1.0, 4.0 - i * 1.5);
                target.damage(dmg, attacker);
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0, true, true, true));
                world.spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.1);
                target.sendActionBar(this.itemFactory.danger("⚠ 鏈式閃電！"));
            }
            world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.HOSTILE, 1.0f, 1.2f);
        }
        return true;
    }

    /**
     * 辨識精英怪所屬星球 id。
     */
    private String identifyElitePlanet(final LivingEntity entity) {
        for (final String tag : entity.getScoreboardTags()) {
            if (tag.startsWith(PLANET_ELITE_TAG_PREFIX)) {
                return tag.substring(PLANET_ELITE_TAG_PREFIX.length());
            }
        }
        return null;
    }

    /**
     * 被動光環 — 每 3 秒 (60 ticks) 對周圍玩家產生效果。
     * 必須透過 runEntity 切到玩家的 Region Thread 才能呼叫 getNearbyEntities。
     */
    private void tickPlanetEliteAuras() {
        for (final PlanetDefinition definition : this.planets.values()) {
            final World world = this.resolveExistingPlanetWorld(definition);
            if (world == null) {
                continue;
            }
            for (final Player player : world.getPlayers()) {
                if (player.getGameMode() != GameMode.SURVIVAL) {
                    continue;
                }
                final String planetId = definition.id();
                this.scheduler.runEntity(player, () -> {
                    if (!player.isValid() || player.getGameMode() != GameMode.SURVIVAL) {
                        return;
                    }
                    for (final Entity nearby : player.getNearbyEntities(10.0, 6.0, 10.0)) {
                        if (!(nearby instanceof Monster monster)) {
                            continue;
                        }
                        if (!monster.getScoreboardTags().contains(PLANET_ELITE_TAG)) {
                            continue;
                        }
                        this.applyEliteAura(planetId, monster, player);
                    }
                });
            }
        }
    }

    private void applyEliteAura(final String planetId, final Monster elite, final Player player) {
        final double dist = elite.getLocation().distance(player.getLocation());
        if (dist > 8.0) {
            return;
        }
        final Location eliteLoc = elite.getLocation();
        final World world = elite.getWorld();
        switch (planetId) {
            case "aurelia" -> {
                // 輻射光環 — 近距離持續中毒
                if (dist <= 4.0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, true, false, false));
                }
                world.spawnParticle(Particle.DUST, eliteLoc.clone().add(0, 1, 0), 5, 2.5, 1.0, 2.5, 0.0,
                        new Particle.DustOptions(Color.fromRGB(120, 220, 40), 1.0f));
            }
            case "cryon" -> {
                // 霜寒光環 — 靠近時增加凍結值
                if (dist <= 5.0) {
                    player.setFreezeTicks(Math.min(player.getMaxFreezeTicks(), player.getFreezeTicks() + 30));
                }
                world.spawnParticle(Particle.SNOWFLAKE, eliteLoc.clone().add(0, 1, 0), 5, 2.0, 0.8, 2.0, 0.01);
            }
            case "nyx" -> {
                // 虛影光環 — 靠近時給予黑暗效果
                if (dist <= 5.0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, true, false, false));
                }
                world.spawnParticle(Particle.REVERSE_PORTAL, eliteLoc.clone().add(0, 1.2, 0), 5, 1.5, 0.8, 1.5, 0.02);
            }
            case "helion" -> {
                // 灼熱光環 — 靠近時著火
                if (dist <= 4.0) {
                    player.setFireTicks(Math.max(player.getFireTicks(), 40));
                }
                world.spawnParticle(Particle.FLAME, eliteLoc.clone().add(0, 1, 0), 5, 2.0, 0.8, 2.0, 0.02);
            }
            case "tempest" -> {
                // 電磁光環 — 靠近時噁心 + 粒子
                if (dist <= 5.0) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 80, 0, true, false, false));
                }
                world.spawnParticle(Particle.ELECTRIC_SPARK, eliteLoc.clone().add(0, 1.2, 0), 5, 2.0, 0.8, 2.0, 0.05);
            }
            default -> {
            }
        }
    }

    public void saveAll() {
        final Map<String, Object> data = new LinkedHashMap<>();
        data.put("generated-worlds", new ArrayList<>(this.generatedPlanetWorlds));
        data.put("terrain-chunks", new ArrayList<>(this.processedDatapackChunks));
        data.put("decorated-chunks", new ArrayList<>(this.decoratedPlanetChunks));
        int index = 0;
        for (final LocationKey key : this.activatedRuins) {
            final String path = "ruins." + index++;
            data.put(path + ".world", key.worldName());
            data.put(path + ".x", key.x());
            data.put(path + ".y", key.y());
            data.put(path + ".z", key.z());
        }
        index = 0;
        for (final Map.Entry<java.util.UUID, Set<LocationKey>> entry : this.playerActivatedRuins.entrySet()) {
            int ruinIndex = 0;
            for (final LocationKey key : entry.getValue()) {
                final String path = "player-ruins." + index + ".entries." + ruinIndex++;
                data.put("player-ruins." + index + ".player", entry.getKey().toString());
                data.put(path + ".world", key.worldName());
                data.put(path + ".x", key.x());
                data.put(path + ".y", key.y());
                data.put(path + ".z", key.z());
            }
            index++;
        }
        index = 0;
        for (final Map.Entry<LocationKey, String> entry : this.gateDestinations.entrySet()) {
            final String path = "gates." + index++;
            data.put(path + ".world", entry.getKey().worldName());
            data.put(path + ".x", entry.getKey().x());
            data.put(path + ".y", entry.getKey().y());
            data.put(path + ".z", entry.getKey().z());
            data.put(path + ".target", entry.getValue());
        }
        index = 0;
        for (final Map.Entry<LocationKey, FruitNodeRegrowth> entry : this.fruitNodeRegrowths.entrySet()) {
            final String path = "fruit-regrowths." + index++;
            data.put(path + ".world", entry.getKey().worldName());
            data.put(path + ".x", entry.getKey().x());
            data.put(path + ".y", entry.getKey().y());
            data.put(path + ".z", entry.getKey().z());
            data.put(path + ".item-id", entry.getValue().fruitItemId());
            data.put(path + ".ready-at", entry.getValue().readyAt());
        }
        if (this.storageBackend == null) return;
        this.storageBackend.savePlanetState(data);
    }

    private void purgeManagedPlanetEntities() {
        for (final PlanetDefinition definition : this.planets.values()) {
            final World world = this.resolveExistingPlanetWorld(definition);
            if (world != null) {
                this.purgeManagedPlanetEntities(world);
            }
        }
    }

    private void purgeManagedPlanetEntities(final World world) {
        // Folia: 存取實體狀態（getScoreboardTags 等）必須在該實體所屬的 region 執行緒上執行
        for (final Entity entity : new ArrayList<>(world.getEntities())) {
            this.scheduler.runEntity(entity, () -> {
                try {
                    if (this.isManagedPlanetEntity(entity)) {
                        entity.remove();
                    }
                } catch (final Exception ignored) {
                }
            });
        }
    }

    private void tickFruitNodeRegrowths() {
        if (this.fruitNodeRegrowths.isEmpty()) {
            return;
        }
        final long now = System.currentTimeMillis();
        for (final Map.Entry<LocationKey, FruitNodeRegrowth> entry : new ArrayList<>(this.fruitNodeRegrowths.entrySet())) {
            if (entry.getValue().readyAt() > now) {
                continue;
            }
            final World world = Bukkit.getWorld(entry.getKey().worldName());
            if (world == null || !world.isChunkLoaded(entry.getKey().x() >> 4, entry.getKey().z() >> 4)) {
                continue;
            }
            final Location anchor = new Location(world, entry.getKey().x(), entry.getKey().y(), entry.getKey().z());
            this.scheduler.runRegion(anchor, task -> {
                final Block fruitBlock = world.getBlockAt(entry.getKey().x(), entry.getKey().y(), entry.getKey().z());
                if (!fruitBlock.isEmpty()) {
                    return;
                }
                if (this.placeFruitNode(fruitBlock, entry.getValue().fruitItemId())) {
                    this.fruitNodeRegrowths.remove(entry.getKey());
                    world.spawnParticle(Particle.WAX_ON, fruitBlock.getLocation().add(0.5, 0.6, 0.5), 5, 0.18, 0.18, 0.18, 0.02);
                    world.playSound(fruitBlock.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.35f, 1.24f);
                }
            });
        }
    }

    private boolean isManagedPlanetEntity(final Entity entity) {
        if (entity == null) {
            return false;
        }
        final Set<String> tags = entity.getScoreboardTags();
        if (tags.contains(HARVEST_NODE_DISPLAY_TAG) || tags.contains(TRAVEL_VESSEL_TAG) || tags.contains(TRAVEL_VESSEL_PART_TAG)) {
            return true;
        }
        if (entity instanceof ItemDisplay display) {
            return !display.isPersistent() && display.isInvulnerable() && !display.hasGravity();
        }
        if (entity instanceof BlockDisplay display) {
            return !display.isPersistent() && display.isInvulnerable() && !display.hasGravity();
        }
        if (entity instanceof ArmorStand stand) {
            return !stand.isPersistent() && stand.isInvulnerable() && !stand.hasGravity() && stand.isInvisible() && stand.isSilent();
        }
        return false;
    }

    private void registerPlanets() {
        this.planets.put("aurelia", new PlanetDefinition(
                "aurelia",
                "奧蕾莉亞",
                AURELIA_WORLD,
            List.of("d_techproject_aurelia_surface", "world_techproject_aurelia_surface"),
            World.Environment.NORMAL,
                HazardType.RADIATION,
                List.of("anti_radiation_helmet", "anti_radiation_chestplate", "anti_radiation_leggings", "anti_radiation_boots"),
                Material.OBSIDIAN,
                Material.RESPAWN_ANCHOR,
                "survey_data",
                List.of("planetary_relic", "ancient_signal")
        ));
        this.planets.put("cryon", new PlanetDefinition(
                "cryon",
                "克里昂",
                CRYON_WORLD,
            List.of("d_techproject_cryon_surface", "world_techproject_cryon_surface"),
            World.Environment.NORMAL,
                HazardType.CRYO,
                List.of("thermal_helmet", "thermal_chestplate", "thermal_leggings", "thermal_boots"),
                Material.BLUE_ICE,
                Material.LODESTONE,
                "survey_data",
                List.of("cryon_relic", "ancient_signal")
        ));
        this.planets.put("nyx", new PlanetDefinition(
                "nyx",
                "倪克斯",
                NYX_WORLD,
            List.of("d_techproject_nyx_orbit", "world_techproject_nyx_orbit"),
            World.Environment.THE_END,
                HazardType.VACUUM,
                List.of("pressure_helmet", "pressure_chestplate", "pressure_leggings", "pressure_boots"),
                Material.END_STONE_BRICKS,
                Material.END_PORTAL_FRAME,
                "survey_data",
                List.of("nyx_relic", "ancient_signal")
        ));
        this.planets.put("helion", new PlanetDefinition(
                "helion",
                "赫利昂",
                HELION_WORLD,
            List.of("d_techproject_helion_surface", "world_techproject_helion_surface"),
            World.Environment.NORMAL,
                HazardType.SOLAR,
                FRONTIER_SUIT,
                Material.BLACKSTONE,
                Material.BEACON,
                "ancient_signal",
                List.of("helion_relic", "frontier_core_fragment")
        ));
        this.planets.put("tempest", new PlanetDefinition(
                "tempest",
                "坦佩斯特",
                TEMPEST_WORLD,
            List.of("d_techproject_tempest_surface", "world_techproject_tempest_surface"),
            World.Environment.NORMAL,
                HazardType.STORM,
                FRONTIER_SUIT,
                Material.WEATHERED_COPPER,
                Material.LIGHTNING_ROD,
                "ancient_signal",
                List.of("tempest_relic", "frontier_core_fragment")
        ));
        this.planets.put("labyrinth", new PlanetDefinition(
                "labyrinth",
                "迷途星",
                LABYRINTH_WORLD,
            List.of("d_techproject_labyrinth_surface", "world_techproject_labyrinth_surface"),
            World.Environment.NORMAL,
                HazardType.MIASMA,
                FRONTIER_SUIT,
                Material.ANCIENT_DEBRIS,
                Material.SCULK_SHRIEKER,
                "ancient_signal",
                List.of("labyrinth_relic", "frontier_core_fragment")
        ));
    }

    private World ensurePlanetWorld(final PlanetDefinition definition) {
        if (this.worldCreationUnsupported) {
            final World loadedWorld = this.resolveExistingPlanetWorld(definition);
            if (loadedWorld != null) {
                this.applyPlanetWorldSettings(definition, loadedWorld);
                this.ensurePlanetGenerated(definition, loadedWorld);
            }
            return loadedWorld;
        }
        World world = this.resolveExistingPlanetWorld(definition);
        if (world != null) {
            this.applyPlanetWorldSettings(definition, world);
            this.ensurePlanetGenerated(definition, world);
            return world;
        }
        final WorldCreator creator = new WorldCreator(definition.worldName())
            .environment(definition.environment())
                .generator(new PlanetChunkGenerator(definition))
                .generateStructures(false);
        try {
            world = creator.createWorld();
        } catch (final UnsupportedOperationException exception) {
            this.worldCreationUnsupported = true;
            final String reason = exception.getMessage() == null || exception.getMessage().isBlank() ? "核心限制" : exception.getMessage();
            this.plugin.getLogger().warning("目前伺服器不支援在執行中建立世界；星球世界改為等待外部世界管理器或完整重啟後載入。若你使用 Worlds，請先建立並載入對應世界。原因：" + reason);
            return null;
        }
        if (world == null) {
            return null;
        }
        this.applyPlanetWorldSettings(definition, world);
        this.ensurePlanetGenerated(definition, world);
        return world;
    }

    private void ensurePlanetGenerated(final PlanetDefinition definition, final World world) {
        if (!this.generatedPlanetWorlds.add(world.getName().toLowerCase(Locale.ROOT))) {
            // 即使已標記為「已生成」，也驗證遺跡核心是否存在；不存在就排程修復
            this.scheduleRuinCoreVerification(definition, world);
            return;
        }
        // 強制載入 chunk(0,0) 確保地形資料可用
        final boolean wasLoaded = world.isChunkLoaded(0, 0);
        if (!wasLoaded) {
            world.getChunkAtAsync(0, 0).thenAccept(chunk -> {
                final Location anchor = new Location(world, 0.5, Math.max(world.getMinHeight() + 8, 80), 0.5);
                this.scheduler.runRegion(anchor, task -> {
                    this.doGeneratePlanetSpawn(definition, world);
                });
            });
        } else {
            final Location anchor = new Location(world, 0.5, Math.max(world.getMinHeight() + 8, 80), 0.5);
            this.scheduler.runRegion(anchor, task -> {
                this.doGeneratePlanetSpawn(definition, world);
            });
        }
    }

    private void doGeneratePlanetSpawn(final PlanetDefinition definition, final World world) {
        // 驗證地形資料有效（不是 minHeight+1 的回退值）
        final int plateauY = this.terrainPlateauY(world, 0, 0, definition.id().equals("nyx") ? 7 : 8);
        if (plateauY <= world.getMinHeight() + 2) {
            // 地形尚未就緒，延遲 2 秒重試（最多 10 次）
            this.retryPlanetGeneration(definition, world, 0);
            return;
        }
        switch (definition.id()) {
            case "aurelia" -> this.generateAurelia(world, definition.markerMaterial(), definition.ruinCoreMaterial());
            case "cryon" -> this.generateCryon(world, definition.markerMaterial(), definition.ruinCoreMaterial());
            case "nyx" -> this.generateNyx(world, definition.markerMaterial(), definition.ruinCoreMaterial());
            case "helion" -> this.generateHelion(world, definition.markerMaterial(), definition.ruinCoreMaterial());
            case "tempest" -> this.generateTempest(world, definition.markerMaterial(), definition.ruinCoreMaterial());
            default -> {
            }
        }
        this.plantSpawnFruitTrees(definition, world);
    }

    private void plantSpawnFruitTrees(final PlanetDefinition definition, final World world) {
        final FruitTreeProfile profile = this.fruitTreeProfileForPlanet(definition.id());
        if (profile == null) {
            return;
        }
        final Random random = new Random(world.getSeed() ^ definition.id().hashCode());
        final int[][] offsets = {
            { 18, 5 }, { -16, 8 }, { 10, -20 }, { -20, -10 },
            { 22, -14 }, { -8, 22 }, { 25, 10 }, { -24, -6 }
        };
        for (final int[] offset : offsets) {
            this.placePlanetMiniTree(definition, world, offset[0], offset[1], random);
        }
    }

    private void retryPlanetGeneration(final PlanetDefinition definition, final World world, final int attempt) {
        if (attempt >= 10) {
            this.plugin.getLogger().warning("星球 " + definition.displayName() + " 出生區域生成失敗（地形資料始終無效），請使用 /tech planet regenerate 手動修復。");
            return;
        }
        this.scheduler.runGlobalDelayed(task -> {
            // 確保 chunk 載入
            if (!world.isChunkLoaded(0, 0)) {
                world.getChunkAtAsync(0, 0).thenAccept(chunk -> {
                    final Location anchor = new Location(world, 0.5, Math.max(world.getMinHeight() + 8, 80), 0.5);
                    this.scheduler.runRegion(anchor, regionTask -> {
                        final int plateauY = this.terrainPlateauY(world, 0, 0, definition.id().equals("nyx") ? 7 : 8);
                        if (plateauY <= world.getMinHeight() + 2) {
                            this.retryPlanetGeneration(definition, world, attempt + 1);
                        } else {
                            this.doGeneratePlanetSpawn(definition, world);
                        }
                    });
                });
            } else {
                final Location anchor = new Location(world, 0.5, Math.max(world.getMinHeight() + 8, 80), 0.5);
                this.scheduler.runRegion(anchor, regionTask -> {
                    final int plateauY = this.terrainPlateauY(world, 0, 0, definition.id().equals("nyx") ? 7 : 8);
                    if (plateauY <= world.getMinHeight() + 2) {
                        this.retryPlanetGeneration(definition, world, attempt + 1);
                    } else {
                        this.doGeneratePlanetSpawn(definition, world);
                    }
                });
            }
        }, 40L);
    }

    /**
     * 驗證已標記為「已生成」的星球是否真的有遺跡核心方塊存在，不存在就修復。
     * 注意：不能用 terrainPlateauY 計算 Y，因為降落台已改變地表高度。
     * 改為在已知 XZ 柱體內搜尋核心方塊。
     */
    private void scheduleRuinCoreVerification(final PlanetDefinition definition, final World world) {
        final int[] offset = switch (definition.id()) {
            case "aurelia"  -> new int[]{0, 7};
            case "cryon"    -> new int[]{3, 8};
            case "nyx"      -> new int[]{2, 7};
            case "helion"   -> new int[]{-1, 8};
            case "tempest"  -> new int[]{1, 7};
            default -> null;
        };
        if (offset == null) return;
        // 用 getChunkAtAsync 載入 chunk 後直接從 chunk 物件讀方塊，完全避免 syncLoad
        final int chunkX = offset[0] >> 4;
        final int chunkZ = offset[1] >> 4;
        world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
            // 直接在 async callback 中用 chunk 物件讀方塊，不經過 world.getBlockAt
            final int localX = offset[0] & 15;
            final int localZ = offset[1] & 15;
            final int minY = Math.max(world.getMinHeight(), 40);
            final int maxY = Math.min(world.getMaxHeight(), 200);
            for (int y = minY; y <= maxY; y++) {
                if (chunk.getBlock(localX, y, localZ).getType() == definition.ruinCoreMaterial()) {
                    return; // 核心存在，無需修復
                }
            }
            // 核心不存在，排程修復（需要在 region 線程執行）
            final Location anchor = new Location(world, offset[0] + 0.5, Math.max(world.getMinHeight() + 8, 80), offset[1] + 0.5);
            this.scheduler.runRegion(anchor, task -> {
                this.plugin.getLogger().info("偵測到 " + definition.displayName() + " 遺跡核心遺失，自動修復中…");
                this.doGeneratePlanetSpawn(definition, world);
            });
        });
    }

    private void generateAurelia(final World world, final Material markerMaterial, final Material ruinCoreMaterial) {
        final int baseY = this.terrainPlateauY(world, 0, 0, 8) + 2;
        this.buildLandingPad(world, 0, baseY, 0, 7, Material.PURPUR_BLOCK, Material.CRYING_OBSIDIAN);
        this.buildRuinRing(world, 0, baseY + 1, 0, Material.PURPUR_PILLAR, Material.AMETHYST_BLOCK, 7);
        this.buildSpire(world, 11, this.surfaceY(world, 11, -6) + 1, -6, Material.AMETHYST_BLOCK, 6);
        this.buildSpire(world, -12, this.surfaceY(world, -12, 8) + 1, 8, Material.SCULK, 5);
        this.buildSpire(world, -8, this.surfaceY(world, -8, -10) + 1, -10, Material.PURPUR_PILLAR, 8);
        this.buildSpire(world, 14, this.surfaceY(world, 14, 4) + 1, 4, Material.AMETHYST_BLOCK, 4);
        this.scatterSurfacePatch(world, 8, -9, Material.AMETHYST_CLUSTER, 8, 6);
        this.scatterSurfacePatch(world, -10, 10, Material.SCULK_CATALYST, 6, 5);
        this.scatterSurfacePatch(world, -14, -5, Material.AMETHYST_BLOCK, 5, 4);
        this.scatterSurfacePatch(world, 6, 14, Material.SCULK_SENSOR, 4, 3);
        // 紫晶弧 — 半圓排列的紫水晶簇
        for (int i = 0; i < 6; i++) {
            final double angle = Math.toRadians(180 + i * 30);
            final int ax = (int) Math.round(Math.cos(angle) * 15);
            final int az = (int) Math.round(Math.sin(angle) * 15);
            final int ay = this.surfaceY(world, ax, az);
            world.getBlockAt(ax, ay + 1, az).setType(Material.AMETHYST_BLOCK, false);
            world.getBlockAt(ax, ay + 2, az).setType(Material.AMETHYST_CLUSTER, false);
        }
        world.getBlockAt(0, baseY + 1, 0).setType(markerMaterial, false);
        world.getBlockAt(0, baseY + 2, 7).setType(ruinCoreMaterial, false);
        this.highlightRuinCore(world, 0, baseY + 2, 7);
    }

    private void generateCryon(final World world, final Material markerMaterial, final Material ruinCoreMaterial) {
        final int baseY = this.terrainPlateauY(world, 0, 0, 8) + 2;
        this.buildLandingPad(world, 0, baseY, 0, 7, Material.PACKED_ICE, Material.BLUE_ICE);
        this.buildRuinRing(world, 3, baseY + 1, 2, Material.PACKED_ICE, Material.BLUE_ICE, 6);
        this.buildSpire(world, -9, this.surfaceY(world, -9, -8) + 1, -8, Material.BLUE_ICE, 7);
        this.buildSpire(world, 12, this.surfaceY(world, 12, 6) + 1, 6, Material.PACKED_ICE, 6);
        this.buildSpire(world, 6, this.surfaceY(world, 6, -12) + 1, -12, Material.BLUE_ICE, 9);
        this.buildSpire(world, -14, this.surfaceY(world, -14, 3) + 1, 3, Material.PACKED_ICE, 5);
        this.scatterSurfacePatch(world, -8, 7, Material.SNOW_BLOCK, 12, 7);
        this.scatterSurfacePatch(world, 10, -10, Material.BLUE_ICE, 8, 6);
        this.scatterSurfacePatch(world, -5, -14, Material.ICE, 10, 5);
        this.scatterSurfacePatch(world, 13, 2, Material.POWDER_SNOW_CAULDRON, 4, 3);
        // 冰柱陣列 — 散布的尖冰柱
        for (int i = 0; i < 5; i++) {
            final int ix = -6 + i * 4 + (i % 2 == 0 ? 1 : -1);
            final int iz = 12 + (i % 2 == 0 ? 2 : -2);
            final int iy = this.surfaceY(world, ix, iz);
            this.buildSpire(world, ix, iy + 1, iz, Material.BLUE_ICE, 3 + (i % 3));
        }
        world.getBlockAt(0, baseY + 1, 0).setType(markerMaterial, false);
        world.getBlockAt(3, baseY + 2, 8).setType(ruinCoreMaterial, false);
        this.highlightRuinCore(world, 3, baseY + 2, 8);
    }

    private void generateNyx(final World world, final Material markerMaterial, final Material ruinCoreMaterial) {
        final int baseY = this.terrainPlateauY(world, 0, 0, 7) + 2;
        this.buildLandingPad(world, 0, baseY, 0, 6, Material.END_STONE_BRICKS, Material.CRYING_OBSIDIAN);
        this.buildRuinRing(world, 2, baseY + 1, 1, Material.END_STONE_BRICKS, Material.CRYING_OBSIDIAN, 6);
        this.buildSpire(world, -11, this.surfaceY(world, -11, 5) + 1, 5, Material.END_STONE_BRICKS, 7);
        this.buildSpire(world, 10, this.surfaceY(world, 10, -9) + 1, -9, Material.OBSIDIAN, 6);
        this.buildSpire(world, -5, this.surfaceY(world, -5, -13) + 1, -13, Material.END_STONE_BRICKS, 8);
        this.scatterSurfacePatch(world, 9, 9, Material.CRYING_OBSIDIAN, 10, 6);
        this.scatterSurfacePatch(world, -8, -7, Material.CHORUS_FLOWER, 6, 5);
        this.scatterSurfacePatch(world, 14, -3, Material.END_ROD, 5, 4);
        this.scatterSurfacePatch(world, -12, -10, Material.TINTED_GLASS, 4, 3);
        // 虛空裂隙 — 末地棒排成的裂縫形光帶
        for (int step = -8; step <= 8; step++) {
            final int rx = 4 + step;
            final int rz = -2 + step / 2;
            final int ry = this.surfaceY(world, rx, rz);
            world.getBlockAt(rx, ry + 1, rz).setType(Material.END_ROD, false);
        }
        world.getBlockAt(0, baseY + 1, 0).setType(markerMaterial, false);
        world.getBlockAt(2, baseY + 2, 7).setType(ruinCoreMaterial, false);
        this.highlightRuinCore(world, 2, baseY + 2, 7);
    }

    private void generateHelion(final World world, final Material markerMaterial, final Material ruinCoreMaterial) {
        final int baseY = this.terrainPlateauY(world, 0, 0, 8) + 2;
        this.buildLandingPad(world, 0, baseY, 0, 7, Material.POLISHED_BLACKSTONE_BRICKS, Material.MAGMA_BLOCK);
        this.buildRuinRing(world, -1, baseY + 1, 2, Material.POLISHED_BLACKSTONE_BRICKS, Material.MAGMA_BLOCK, 6);
        this.buildSpire(world, 10, this.surfaceY(world, 10, -7) + 1, -7, Material.BASALT, 7);
        this.buildSpire(world, -12, this.surfaceY(world, -12, 8) + 1, 8, Material.BLACKSTONE, 6);
        this.buildSpire(world, 7, this.surfaceY(world, 7, 12) + 1, 12, Material.BASALT, 5);
        this.buildSpire(world, -8, this.surfaceY(world, -8, -10) + 1, -10, Material.POLISHED_BLACKSTONE, 8);
        this.scatterSurfacePatch(world, -8, 6, Material.SHROOMLIGHT, 8, 5);
        this.scatterSurfacePatch(world, 10, 8, Material.GILDED_BLACKSTONE, 7, 5);
        this.scatterSurfacePatch(world, -13, -6, Material.MAGMA_BLOCK, 6, 4);
        this.scatterSurfacePatch(world, 5, -13, Material.BASALT, 8, 5);
        // 熔岩裂縫 — 岩漿塊排成的地面裂紋
        for (int step = -6; step <= 6; step++) {
            final int lx = -3 + step;
            final int lz = 14 + (step % 2 == 0 ? 1 : 0);
            final int ly = this.surfaceY(world, lx, lz);
            world.getBlockAt(lx, ly, lz).setType(Material.MAGMA_BLOCK, false);
            world.getBlockAt(lx, ly + 1, lz).setType(Material.FIRE, false);
        }
        world.getBlockAt(0, baseY + 1, 0).setType(markerMaterial, false);
        world.getBlockAt(-1, baseY + 2, 8).setType(ruinCoreMaterial, false);
        this.highlightRuinCore(world, -1, baseY + 2, 8);
    }

    private void generateTempest(final World world, final Material markerMaterial, final Material ruinCoreMaterial) {
        final int baseY = this.terrainPlateauY(world, 0, 0, 8) + 2;
        this.buildLandingPad(world, 0, baseY, 0, 7, Material.CUT_COPPER, Material.COPPER_BULB);
        this.buildRuinRing(world, 1, baseY + 1, 0, Material.WEATHERED_CUT_COPPER, Material.COPPER_BULB, 6);
        this.buildSpire(world, -9, this.surfaceY(world, -9, 7) + 1, 7, Material.WEATHERED_CUT_COPPER, 7);
        this.buildSpire(world, 11, this.surfaceY(world, 11, -6) + 1, -6, Material.OXIDIZED_CUT_COPPER, 6);
        this.buildSpire(world, -13, this.surfaceY(world, -13, -4) + 1, -4, Material.CUT_COPPER, 9);
        this.buildSpire(world, 8, this.surfaceY(world, 8, 11) + 1, 11, Material.WEATHERED_CUT_COPPER, 5);
        this.scatterSurfacePatch(world, -7, 6, Material.LIGHTNING_ROD, 8, 5);
        this.scatterSurfacePatch(world, 8, 8, Material.SEA_LANTERN, 6, 5);
        this.scatterSurfacePatch(world, 13, -8, Material.COPPER_BULB, 5, 4);
        this.scatterSurfacePatch(world, -10, -12, Material.OXIDIZED_CUT_COPPER, 6, 4);
        // 引雷陣 — 避雷針圍成的六邊形
        for (int i = 0; i < 6; i++) {
            final double angle = Math.toRadians(i * 60);
            final int tx = (int) Math.round(Math.cos(angle) * 10) - 5;
            final int tz = (int) Math.round(Math.sin(angle) * 10) + 8;
            final int ty = this.surfaceY(world, tx, tz);
            world.getBlockAt(tx, ty + 1, tz).setType(Material.COPPER_BLOCK, false);
            world.getBlockAt(tx, ty + 2, tz).setType(Material.LIGHTNING_ROD, false);
        }
        world.getBlockAt(0, baseY + 1, 0).setType(markerMaterial, false);
        world.getBlockAt(1, baseY + 2, 7).setType(ruinCoreMaterial, false);
        this.highlightRuinCore(world, 1, baseY + 2, 7);
    }

    /** 安全呼叫 setTime — 若維度已設 fixed_time 則跳過（26.1+ 會拋例外） */
    private static void safeSetTime(final World world, final long time) {
        try {
            world.setTime(time);
        } catch (final IllegalArgumentException ignored) {
            // 維度已設 has_fixed_time，不需要手動設定
        }
    }

    private void applyPlanetWorldSettings(final PlanetDefinition definition, final World world) {
        this.scheduler.runGlobal(task -> {
            world.setDifficulty(org.bukkit.Difficulty.HARD);
            world.setSpawnFlags(true, true);
            try { world.setGameRule(GameRules.ADVANCE_TIME, false); } catch (final Exception ignored) {}
            try { world.setGameRule(GameRules.ADVANCE_WEATHER, false); } catch (final Exception ignored) {}
            world.setGameRule(GameRules.MOB_GRIEFING, false);
            switch (definition.id()) {
                case "aurelia" -> {
                    safeSetTime(world, 2500L);
                    world.setStorm(false);
                    world.setThundering(false);
                }
                case "cryon" -> {
                    safeSetTime(world, 23000L);
                    world.setStorm(false);
                    world.setThundering(false);
                }
                case "nyx" -> {
                    safeSetTime(world, 23000L);
                    world.setStorm(false);
                    world.setThundering(false);
                }
                case "helion" -> {
                    safeSetTime(world, 4500L);
                    world.setStorm(false);
                    world.setThundering(false);
                }
                case "tempest" -> {
                    safeSetTime(world, 12800L);
                    world.setStorm(true);
                    world.setThundering(true);
                }
                case "labyrinth" -> {
                    safeSetTime(world, 18000L); // 午夜
                    world.setStorm(true);  // 厚雲層遮蔽天空
                    world.setThundering(false);
                    // 拉高怪物生成上限（迷宮需要詭譎氛圍 + 危險感）
                    try {
                        world.setMonsterSpawnLimit(120);
                        world.setAmbientSpawnLimit(40);
                    } catch (final Exception ignored) { }
                    // 世界邊界：2000×2000（中心 0,0）
                    final org.bukkit.WorldBorder border = world.getWorldBorder();
                    border.setCenter(0, 0);
                    border.setSize(2000);
                    border.setWarningDistance(20);
                    border.setDamageAmount(2.0);
                    border.setDamageBuffer(5.0);
                }
                default -> {
                    safeSetTime(world, 6000L);
                    world.setStorm(false);
                    world.setThundering(false);
                }
            }
        });
    }

    private void processLoadedDatapackChunks(final PlanetDefinition definition, final World world) {
        if (world == null || !this.isDatapackPlanetWorld(definition, world.getName())) {
            return;
        }
        for (final Chunk chunk : world.getLoadedChunks()) {
            this.processDatapackChunk(definition, chunk);
        }
    }

    private void processLoadedPlanetDecorations(final PlanetDefinition definition, final World world) {
        if (world == null) {
            return;
        }
        for (final Chunk chunk : world.getLoadedChunks()) {
            this.processPlanetDecorationChunk(definition, chunk, true);
        }
    }

    private void processDatapackChunk(final PlanetDefinition definition, final Chunk chunk) {
        final String chunkKey = this.chunkKey(chunk);
        if (!this.processedDatapackChunks.add(chunkKey)) {
            return;
        }
        final Location anchor = new Location(chunk.getWorld(), (chunk.getX() << 4) + 0.5, Math.max(chunk.getWorld().getMinHeight() + 8, 80), (chunk.getZ() << 4) + 0.5);
        this.scheduler.runRegion(anchor, task -> {
            this.reshapeDatapackChunk(definition, chunk);
            this.decoratePlanetChunk(definition, chunk);
        });
    }

    private void processPlanetDecorationChunk(final PlanetDefinition definition,
                                              final Chunk chunk,
                                              final boolean includeFruitRetrofit) {
        final String key = this.chunkKey(chunk) + ":flora";
        final Location anchor = new Location(chunk.getWorld(), (chunk.getX() << 4) + 0.5, Math.max(chunk.getWorld().getMinHeight() + 8, 80), (chunk.getZ() << 4) + 0.5);
        if (this.decoratedPlanetChunks.add(key)) {
            this.scheduler.runRegion(anchor, task -> this.decoratePlanetChunk(definition, chunk));
        }
        final String orchardKey = this.chunkKey(chunk) + ":orchard-v2";
        if (this.decoratedPlanetChunks.add(orchardKey)) {
            this.scheduler.runRegion(anchor, task -> this.enrichPlanetBiologyChunk(definition, chunk));
        }
        final String structKey = this.chunkKey(chunk) + ":structures-v1";
        if (this.decoratedPlanetChunks.add(structKey)) {
            this.scheduler.runRegion(anchor, task -> this.generatePlanetStructures(definition, chunk));
        }
        if (includeFruitRetrofit) {
            final String fruitRetrofitKey = this.chunkKey(chunk) + ":fruit-retrofit-v2";
            if (this.decoratedPlanetChunks.add(fruitRetrofitKey)) {
                this.scheduler.runRegion(anchor, task -> this.retrofitFruitTrees(definition, chunk));
            }
        }
    }

    private void reshapeDatapackChunk(final PlanetDefinition definition, final Chunk chunk) {
        final World world = chunk.getWorld();
        final PlanetTerrainProfile profile = PlanetTerrainProfile.forPlanet(definition.id());
        final long seed = world.getSeed() ^ ((long) definition.id().hashCode() << 32);
        final int minY = world.getMinHeight();
        final int maxY = world.getMaxHeight() - 1;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                final int worldX = (chunk.getX() << 4) + localX;
                final int worldZ = (chunk.getZ() << 4) + localZ;
                final PlanetSurfaceColumn column = profile.sample(seed, worldX, worldZ, minY, maxY + 1);
                world.getBlockAt(worldX, minY, worldZ).setType(Material.BEDROCK, false);
                for (int y = minY + 1; y <= maxY; y++) {
                    final Material desired;
                    if (y <= column.surfaceY()) {
                        desired = y == column.surfaceY()
                                ? column.topMaterial()
                                : y >= column.surfaceY() - 3
                                ? column.subsurfaceMaterial()
                                : column.baseMaterial();
                    } else if (column.fluidMaterial() != null && y <= column.seaLevel()) {
                        desired = column.fluidMaterial();
                    } else {
                        desired = Material.AIR;
                    }
                    final Block block = world.getBlockAt(worldX, y, worldZ);
                    if (block.getType() != desired) {
                        block.setType(desired, false);
                    }
                }
            }
        }
    }

    private Location planetArrivalLocation(final World world) {
        final int y = Math.max(world.getMinHeight() + 4, world.getHighestBlockYAt(0, 0) + 2);
        return new Location(world, 0.5, y, 0.5, 0.0f, 0.0f);
    }

    private int surfaceY(final World world, final int x, final int z) {
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return world.getMinHeight() + 1;
        }
        return Math.max(world.getMinHeight() + 1, world.getHighestBlockYAt(x, z));
    }

    private int terrainPlateauY(final World world, final int centerX, final int centerZ, final int radius) {
        int highest = world.getMinHeight() + 1;
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                highest = Math.max(highest, this.surfaceY(world, x, z));
            }
        }
        return highest;
    }

    private void buildLandingPad(final World world,
                                 final int centerX,
                                 final int y,
                                 final int centerZ,
                                 final int radius,
                                 final Material floor,
                                 final Material rim) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                final double distance = Math.sqrt(x * x + z * z);
                if (distance > radius + 0.25D) {
                    continue;
                }
                final int worldX = centerX + x;
                final int worldZ = centerZ + z;
                final int terrainY = this.surfaceY(world, worldX, worldZ);
                for (int fillY = terrainY + 1; fillY <= y; fillY++) {
                    world.getBlockAt(worldX, fillY, worldZ).setType(distance >= radius - 1.1D ? rim : floor, false);
                }
                for (int clearY = y + 1; clearY <= y + 5; clearY++) {
                    world.getBlockAt(worldX, clearY, worldZ).setType(Material.AIR, false);
                }
            }
        }
    }

    private void scatterSurfacePatch(final World world,
                                     final int centerX,
                                     final int centerZ,
                                     final Material material,
                                     final int amount,
                                     final int spread) {
        final Random random = new Random((long) centerX * 31L + centerZ * 17L + material.ordinal() * 13L);
        final int scaledAmount = this.scaledDecorationCount(amount, true);
        for (int index = 0; index < scaledAmount; index++) {
            final int x = centerX + random.nextInt(spread * 2 + 1) - spread;
            final int z = centerZ + random.nextInt(spread * 2 + 1) - spread;
            final int y = this.surfaceY(world, x, z) + 1;
            final Block block = world.getBlockAt(x, y, z);
            if (block.getType() != Material.AIR) {
                continue;
            }
            block.setType(material, false);
        }
    }

    private void decoratePlanetChunk(final PlanetDefinition definition, final Chunk chunk) {
        // 迷途星有自己的 MazeService 裝飾系統，跳過通用星球裝飾
        if ("labyrinth".equals(definition.id())) {
            return;
        }
        final World world = chunk.getWorld();
        final Random random = new Random(world.getSeed() ^ ((long) definition.id().hashCode() << 27) ^ (chunk.getX() * 341873128712L) ^ (chunk.getZ() * 132897987541L));
        if (random.nextInt(8) == 0) {
            final int x = (chunk.getX() << 4) + 3 + random.nextInt(10);
            final int z = (chunk.getZ() << 4) + 3 + random.nextInt(10);
            this.carvePlanetCrater(definition, world, x, z, 3 + random.nextInt(4), random);
        }
        if (random.nextInt(9) == 0) {
            final int x = (chunk.getX() << 4) + 2 + random.nextInt(12);
            final int z = (chunk.getZ() << 4) + 2 + random.nextInt(12);
            this.buildPlanetCrystalCluster(definition, world, x, z, random);
        }
        if (random.nextInt(11) == 0) {
            final int x = (chunk.getX() << 4) + 1 + random.nextInt(14);
            final int z = (chunk.getZ() << 4) + 1 + random.nextInt(14);
            this.carvePlanetRift(definition, world, x, z, random);
        }
        if (random.nextInt(12) == 0) {
            final int x = (chunk.getX() << 4) + 3 + random.nextInt(10);
            final int z = (chunk.getZ() << 4) + 3 + random.nextInt(10);
            this.buildFloatingShard(definition, world, x, z, random);
        }
        if (random.nextInt(15) == 0) {
            final int x = (chunk.getX() << 4) + 3 + random.nextInt(10);
            final int z = (chunk.getZ() << 4) + 3 + random.nextInt(10);
            this.buildPlanetFrontierRemnant(definition, world, x, z, random);
        }
    }

    private void enrichPlanetBiologyChunk(final PlanetDefinition definition,
                                          final Chunk chunk) {
        final World world = chunk.getWorld();
        final Random random = new Random((world.getSeed() << 1) ^ ((long) definition.id().hashCode() << 29) ^ (chunk.getX() * 91827364577L) ^ (chunk.getZ() * 19283746519L));
        this.enrichPlanetBiologyChunk(definition, chunk, random);
    }

    private void enrichPlanetBiologyChunk(final PlanetDefinition definition,
                                          final Chunk chunk,
                                          final Random random) {
        if ("labyrinth".equals(definition.id())) {
            this.enrichLabyrinthChunk(chunk, random);
            return;
        }
        final World world = chunk.getWorld();
        for (int attempt = 0; attempt < this.scaledDecorationCount(6, true); attempt++) {
            final int x = (chunk.getX() << 4) + random.nextInt(16);
            final int z = (chunk.getZ() << 4) + random.nextInt(16);
            this.placePlanetFlora(definition, world, x, z, random);
        }
        for (int attempt = 0; attempt < this.scaledHarvestNodeCount(3, random); attempt++) {
            final int x = (chunk.getX() << 4) + 1 + random.nextInt(14);
            final int z = (chunk.getZ() << 4) + 1 + random.nextInt(14);
            this.placePlanetHarvestNode(definition, world, x, z, random);
        }
        final int treeBursts = this.rolledNaturalFruitTreeBursts(random);
        for (int attempt = 0; attempt < treeBursts; attempt++) {
            final int x = (chunk.getX() << 4) + 2 + random.nextInt(12);
            final int z = (chunk.getZ() << 4) + 2 + random.nextInt(12);
            this.placePlanetMiniTree(definition, world, x, z, random);
            if (random.nextInt(8) == 0) {
                this.placePlanetMiniTree(definition, world, x + random.nextInt(5) - 2, z + random.nextInt(5) - 2, random);
            }
        }
    }

    /**
     * 迷宮分區採集節點：越外層越稀有，總量遠低於一般星球。
     * Zone 0 (Glade)：無節點
     * Zone 1 (cellDist ≤ 20)：~0.8/區塊，常見礦
     * Zone 2 (cellDist ≤ 40)：~0.4/區塊，中階礦
     * Zone 3 (cellDist > 40)：~0.15/區塊，稀有礦（含遠古殘骸）
     */
    private void enrichLabyrinthChunk(final Chunk chunk, final Random random) {
        final World world = chunk.getWorld();
        final int chunkX = chunk.getX() << 4;
        final int chunkZ = chunk.getZ() << 4;
        final Location centerLoc = new Location(world, chunkX + 8, 65, chunkZ + 8);
        final int chunkZone = this.plugin.getMazeService().getMazeZoneForLocation(centerLoc);
        if (chunkZone < 0) {
            return; // 不在迷宮世界
        }

        // 迷宮地面固定 Y=64（profile 噪點全為 0），節點放置於 Y=65
        final int floorY = 64;
        // 每 chunk 最多嘗試 1 次，大幅降低稀有度
        if (random.nextDouble() >= 0.25D) {
            return;
        }
        final int x = chunkX + 1 + random.nextInt(14);
        final int z = chunkZ + 1 + random.nextInt(14);
        // 嚴格按「每個放置位置」的分區判定，避免 chunk 中心在迷宮外但採集點落在 Glade 內
        final Location nodeLoc = new Location(world, x, floorY + 1, z);
        final int posZone = this.plugin.getMazeService().getMazeZoneForLocation(nodeLoc);
        if (posZone <= 0) {
            return; // Glade（0）或不在迷宮（<0）：嚴禁放置
        }

        final double successChance;
        final Material[] pool;
        switch (posZone) {
            case 1 -> {
                successChance = 0.40D; // 0.25 * 0.40 = 10% 每 chunk
                pool = new Material[] {
                        Material.DEEPSLATE_COAL_ORE, Material.DEEPSLATE_COAL_ORE,
                        Material.DEEPSLATE_IRON_ORE,
                        Material.DEEPSLATE_COPPER_ORE
                };
            }
            case 2 -> {
                successChance = 0.25D; // 0.25 * 0.25 ≈ 6.3% 每 chunk
                pool = new Material[] {
                        Material.DEEPSLATE_REDSTONE_ORE,
                        Material.DEEPSLATE_LAPIS_ORE,
                        Material.DEEPSLATE_GOLD_ORE,
                        Material.DEEPSLATE_DIAMOND_ORE,
                        Material.DEEPSLATE_EMERALD_ORE
                };
            }
            case 3 -> {
                successChance = 0.12D; // 0.25 * 0.12 = 3% 每 chunk
                pool = new Material[] {
                        Material.DEEPSLATE_DIAMOND_ORE,
                        Material.DEEPSLATE_EMERALD_ORE,
                        Material.AMETHYST_CLUSTER,
                        Material.ANCIENT_DEBRIS
                };
            }
            default -> { return; }
        }

        if (random.nextDouble() >= successChance) return;
        final Block floor = world.getBlockAt(x, floorY, z);
        final Block node = world.getBlockAt(x, floorY + 1, z);
        final Block above = world.getBlockAt(x, floorY + 2, z);
        if (!floor.isSolid() || floor.isLiquid()) return;
        if (!node.isEmpty() || !above.isEmpty()) return;
        node.setType(pool[random.nextInt(pool.length)], false);
    }

    private void placePlanetFlora(final PlanetDefinition definition,
                                  final World world,
                                  final int x,
                                  final int z,
                                  final Random random) {
        final int surfaceY = this.surfaceY(world, x, z);
        final Block floor = world.getBlockAt(x, surfaceY, z);
        final Block block = world.getBlockAt(x, surfaceY + 1, z);
        if (!block.isEmpty() || !floor.isSolid()) {
            return;
        }
        final Material flora = switch (definition.id()) {
            case "aurelia" -> random.nextInt(3) == 0 ? Material.SMALL_AMETHYST_BUD : Material.FLOWERING_AZALEA;
            case "cryon" -> random.nextInt(3) == 0 ? Material.WHITE_TULIP : Material.LILY_OF_THE_VALLEY;
            case "nyx" -> random.nextInt(3) == 0 ? Material.END_ROD : Material.CHORUS_FLOWER;
            case "helion" -> random.nextInt(3) == 0 ? Material.TORCHFLOWER : Material.WITHER_ROSE;
            case "tempest" -> random.nextInt(3) == 0 ? Material.END_ROD : Material.LIGHTNING_ROD;
            default -> null;
        };
        if (flora != null) {
            block.setType(flora, false);
        }
    }

    private void placePlanetHarvestNode(final PlanetDefinition definition,
                                        final World world,
                                        final int x,
                                        final int z,
                                        final Random random) {
        final int surfaceY = this.surfaceY(world, x, z);
        final Block floor = world.getBlockAt(x, surfaceY, z);
        final Block node = world.getBlockAt(x, surfaceY + 1, z);
        final Block above = world.getBlockAt(x, surfaceY + 2, z);
        if (!floor.isSolid() || floor.isLiquid() || !node.isEmpty() || !above.isEmpty()) {
            return;
        }
        final Material material = switch (definition.id()) {
            case "aurelia" -> random.nextBoolean() ? Material.AMETHYST_CLUSTER : Material.SCULK_CATALYST;
            case "cryon" -> random.nextBoolean() ? Material.ICE : Material.WHITE_TULIP;
            case "nyx" -> random.nextBoolean() ? Material.CRYING_OBSIDIAN : Material.CHORUS_FLOWER;
            case "helion" -> random.nextBoolean() ? Material.SHROOMLIGHT : Material.CRIMSON_ROOTS;
            case "tempest" -> random.nextBoolean() ? Material.SEA_LANTERN : Material.LIGHTNING_ROD;
            default -> null;
        };
        if (material == null) {
            return;
        }
        node.setType(material, false);
    }

    private void placePlanetMiniTree(final PlanetDefinition definition,
                                     final World world,
                                     final int x,
                                     final int z,
                                     final Random random) {
        final FruitTreeProfile profile = this.fruitTreeProfileForPlanet(definition.id());
        if (profile != null) {
            final int groundY = this.surfaceY(world, x, z);
            final Block root = world.getBlockAt(x, groundY + 1, z);
            this.prepareFruitTreeSite(definition, root, profile.maxHeight() + 2);
            this.growFruitTree(definition, root, profile, random, true);
            return;
        }
        final int groundY = this.surfaceY(world, x, z);
        final Block ground = world.getBlockAt(x, groundY, z);
        if (!ground.isSolid()) {
            return;
        }
        final int trunkHeight = 3 + random.nextInt(3);
        for (int y = 1; y <= trunkHeight; y++) {
            final Block trunk = world.getBlockAt(x, groundY + y, z);
            if (!trunk.isEmpty()) {
                return;
            }
        }
        final Material trunkMaterial = switch (definition.id()) {
            case "aurelia" -> Material.PURPUR_PILLAR;
            case "cryon" -> Material.PACKED_ICE;
            case "nyx" -> Material.END_STONE_BRICKS;
            case "helion" -> Material.BASALT;
            case "tempest" -> Material.WEATHERED_CUT_COPPER;
            default -> Material.OAK_LOG;
        };
        final Material canopyMaterial = switch (definition.id()) {
            case "aurelia" -> Material.FLOWERING_AZALEA_LEAVES;
            case "cryon" -> Material.PEARLESCENT_FROGLIGHT;
            case "nyx" -> Material.PURPLE_STAINED_GLASS;
            case "helion" -> Material.RED_MUSHROOM_BLOCK;
            case "tempest" -> Material.EXPOSED_COPPER_BULB;
            default -> Material.OAK_LEAVES;
        };
        for (int y = 1; y <= trunkHeight; y++) {
            world.getBlockAt(x, groundY + y, z).setType(trunkMaterial, false);
        }
        final int canopyY = groundY + trunkHeight;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 2) {
                    continue;
                }
                final Block canopy = world.getBlockAt(x + dx, canopyY + 1, z + dz);
                if (canopy.isEmpty()) {
                    canopy.setType(canopyMaterial, false);
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    continue;
                }
                if (Math.abs(dx) + Math.abs(dz) < 2) {
                    continue;
                }
                final Block canopy = world.getBlockAt(x + dx, canopyY, z + dz);
                if (canopy.isEmpty() && random.nextInt(3) != 0) {
                    canopy.setType(canopyMaterial, false);
                }
            }
        }
        final Block crown = world.getBlockAt(x, canopyY + 2, z);
        if (crown.isEmpty()) {
            crown.setType(canopyMaterial, false);
        }
    }

    private boolean handleFruitTreeInteract(final Player player,
                                            final Block block,
                                            final ItemStack heldItem,
                                            final PlanetDefinition definition) {
        final String placedItemId = this.plugin.getPlacedTechBlockService().placedItemId(block);
        final FruitTreeProfile saplingProfile = this.fruitTreeProfileForSaplingId(placedItemId);
        if (saplingProfile != null) {
            if (heldItem == null || heldItem.getType() != Material.BONE_MEAL) {
                player.sendActionBar(this.itemFactory.secondary("這株「" + this.itemFactory.displayNameForId(placedItemId) + "」需要骨粉催化成形。"));
                return true;
            }
            return this.tryGrowFruitSapling(player, block, heldItem, definition, saplingProfile);
        }
        final FruitTreeProfile fruitProfile = this.fruitTreeProfileForFruitId(placedItemId);
        if (fruitProfile != null) {
            return this.tryHarvestFruitNode(player, block, fruitProfile);
        }
        return false;
    }

    private boolean tryGrowFruitSapling(final Player player,
                                        final Block saplingBlock,
                                        final ItemStack heldItem,
                                        final PlanetDefinition definition,
                                        final FruitTreeProfile profile) {
        if (definition != null && !definition.id().equalsIgnoreCase(this.planetIdForFruitTree(profile))) {
            player.sendActionBar(this.itemFactory.warning("這株異星果苗無法在當前星球穩定成長。"));
            player.playSound(saplingBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.72f);
            return true;
        }
        if (!this.growFruitTree(definition, saplingBlock, profile, new Random(), false)) {
            player.sendActionBar(this.itemFactory.warning("附近空間不足，果樹結構無法展開。"));
            player.playSound(saplingBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.82f);
            return true;
        }
        this.consumeHeldItem(player, heldItem);
        player.sendMessage(this.itemFactory.success("「" + this.itemFactory.displayNameForId(profile.saplingId()) + "」已展開為異星果樹。"));
        saplingBlock.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, saplingBlock.getLocation().add(0.5, 0.7, 0.5), 12, 0.45, 0.55, 0.45, 0.02);
        saplingBlock.getWorld().playSound(saplingBlock.getLocation(), Sound.ITEM_BONE_MEAL_USE, 0.75f, 1.1f);
        saplingBlock.getWorld().playSound(saplingBlock.getLocation(), Sound.BLOCK_AZALEA_LEAVES_PLACE, 0.45f, 0.95f);
        return true;
    }

    private boolean tryHarvestFruitNode(final Player player,
                                        final Block block,
                                        final FruitTreeProfile profile) {
        final String fruitItemId = profile.fruitItemId();
        final ItemStack fruit = this.buildTechStack(fruitItemId, 1 + this.ambientRandom.nextInt(2));
        if (fruit == null) {
            return true;
        }
        final Location center = block.getLocation().add(0.5, 0.5, 0.5);
        block.setType(Material.AIR, false);
        this.plugin.getPlacedTechBlockService().unregister(block);
        this.fruitNodeRegrowths.put(LocationKey.from(block.getLocation()), new FruitNodeRegrowth(fruitItemId, System.currentTimeMillis() + (120L + this.ambientRandom.nextInt(80)) * 1000L));
        block.getWorld().dropItemNaturally(center, fruit);
        block.getWorld().spawnParticle(Particle.ITEM, center, 6, 0.18, 0.18, 0.18, 0.02, fruit);
        block.getWorld().playSound(center, Sound.BLOCK_CHERRY_LEAVES_BREAK, 0.5f, 1.2f);
        player.sendActionBar(this.itemFactory.success("已採收「" + this.itemFactory.displayNameForId(fruitItemId) + "」，果實會重新生長。"));
        return true;
    }

    private FruitTreeProfile fruitTreeProfileForPlanet(final String planetId) {
        return switch (planetId == null ? "" : planetId.toLowerCase(Locale.ROOT)) {
            case "aurelia" -> new FruitTreeProfile("lumenfruit_sapling", "lumenfruit", Material.PURPUR_PILLAR, Material.FLOWERING_AZALEA_LEAVES, Material.AMETHYST_BLOCK, 3, 5, MAX_FRUIT_PER_TREE);
            case "cryon" -> new FruitTreeProfile("frost_apple_sapling", "frost_apple", Material.PACKED_ICE, Material.PEARLESCENT_FROGLIGHT, Material.BLUE_ICE, 3, 4, MAX_FRUIT_PER_TREE);
            case "nyx" -> new FruitTreeProfile("shadow_berry_sapling", "shadow_berry_cluster", Material.END_STONE_BRICKS, Material.PURPUR_BLOCK, Material.OBSIDIAN, 3, 5, MAX_FRUIT_PER_TREE);
            case "helion" -> new FruitTreeProfile("sunflare_fig_sapling", "sunflare_fig", Material.BASALT, Material.RED_MUSHROOM_BLOCK, Material.SHROOMLIGHT, 4, 5, MAX_FRUIT_PER_TREE);
            case "tempest" -> new FruitTreeProfile("stormplum_sapling", "stormplum", Material.WEATHERED_CUT_COPPER, Material.EXPOSED_COPPER_BULB, Material.LIGHTNING_ROD, 4, 6, MAX_FRUIT_PER_TREE);
            default -> null;
        };
    }

    private List<FruitTreeProfile> domesticFruitTreeProfiles() {
        return List.of(
                new FruitTreeProfile("cherry_sapling", "cherry", Material.CHERRY_LOG, Material.CHERRY_LEAVES, Material.PINK_PETALS, 3, 5, MAX_FRUIT_PER_TREE),
                new FruitTreeProfile("lemon_sapling", "lemon", Material.OAK_LOG, Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES, 3, 5, MAX_FRUIT_PER_TREE),
                new FruitTreeProfile("peach_sapling", "peach", Material.BIRCH_LOG, Material.BIRCH_LEAVES, Material.PINK_PETALS, 3, 5, MAX_FRUIT_PER_TREE),
                new FruitTreeProfile("pear_sapling", "pear", Material.SPRUCE_LOG, Material.OAK_LEAVES, Material.MOSS_BLOCK, 3, 5, MAX_FRUIT_PER_TREE),
            new FruitTreeProfile("orange_sapling", "orange", Material.JUNGLE_LOG, Material.JUNGLE_LEAVES, Material.MOSS_BLOCK, 4, 6, MAX_FRUIT_PER_TREE)
        );
    }

    private List<FruitTreeProfile> allFruitTreeProfiles() {
        final List<FruitTreeProfile> profiles = new ArrayList<>();
        for (final String planetId : this.planets.keySet()) {
            final FruitTreeProfile profile = this.fruitTreeProfileForPlanet(planetId);
            if (profile != null) {
                profiles.add(profile);
            }
        }
        profiles.addAll(this.domesticFruitTreeProfiles());
        return profiles;
    }

    private int scaledDecorationCount(final int amount, final boolean preserveOne) {
        if (amount <= 0) {
            return 0;
        }
        final int scaled = (int) Math.floor(amount * PLANET_DECORATION_DENSITY);
        if (scaled > 0) {
            return scaled;
        }
        return preserveOne ? 1 : 0;
    }

    private int scaledHarvestNodeCount(final int amount, final Random random) {
        final int base = this.scaledDecorationCount(amount, true);
        if (base <= 0) {
            return 0;
        }
        final double scaled = base * PLANET_HARVEST_NODE_DENSITY;
        final int floor = (int) Math.floor(scaled);
        final double remainder = scaled - floor;
        if (remainder > 0.0D && random != null && random.nextDouble() < remainder) {
            return floor + 1;
        }
        return floor;
    }

    private int rolledNaturalFruitTreeBursts(final Random random) {
        if (random == null || random.nextInt(PLANET_FRUIT_TREE_BURST_CHANCE) != 0) {
            return 0;
        }
        return random.nextInt(PLANET_FRUIT_TREE_EXTRA_BURST_CHANCE) == 0 ? 2 : 1;
    }

    private void prepareFruitTreeSite(final PlanetDefinition definition,
                                      final Block rootBlock,
                                      final int clearanceHeight) {
        if (rootBlock == null) {
            return;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= clearanceHeight; dy++) {
                    final Block candidate = rootBlock.getRelative(dx, dy, dz);
                    if (candidate.equals(rootBlock) || candidate.isEmpty()) {
                        continue;
                    }
                    if (this.canReplaceForFruitTree(definition, candidate)) {
                        this.plugin.getPlacedTechBlockService().unregister(candidate);
                        candidate.setType(Material.AIR, false);
                    }
                }
            }
        }
    }

    private boolean canReplaceForFruitTree(final PlanetDefinition definition,
                                           final Block block) {
        if (block == null) {
            return false;
        }
        final Material material = block.getType();
        if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
            return true;
        }
        if (material == Material.PLAYER_HEAD || material == Material.PLAYER_WALL_HEAD) {
            return true;
        }
        if (!material.isSolid()) {
            return true;
        }
        if (definition != null && this.harvestProfileFor(definition, material, false) != null) {
            return true;
        }
        return switch (material) {
            case FLOWERING_AZALEA_LEAVES, PEARLESCENT_FROGLIGHT, PURPUR_BLOCK, RED_MUSHROOM_BLOCK, EXPOSED_COPPER_BULB -> true;
            default -> false;
        };
    }

    private void retrofitFruitTrees(final PlanetDefinition definition,
                                    final Chunk chunk) {
        final FruitTreeProfile profile = this.fruitTreeProfileForPlanet(definition.id());
        if (profile == null) {
            return;
        }
        final int minY = chunk.getWorld().getMinHeight() + 1;
        final int maxY = chunk.getWorld().getMaxHeight() - 2;
        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int y = minY; y <= maxY; y++) {
                    final Block root = chunk.getBlock(localX, y, localZ);
                    if (root.getType() != profile.trunkMaterial()) {
                        continue;
                    }
                    if (root.getRelative(BlockFace.DOWN).getType() == profile.trunkMaterial()) {
                        continue;
                    }
                    if (!this.hasFruitTreeCanopy(definition, root, profile)) {
                        continue;
                    }
                    this.populateExistingTreeFruit(definition, root, profile);
                }
            }
        }
    }

    private boolean hasFruitTreeCanopy(final PlanetDefinition definition,
                                       final Block root,
                                       final FruitTreeProfile profile) {
        for (int dy = 2; dy <= profile.maxHeight() + 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    if (this.isFruitCanopyMaterial(definition, root.getRelative(dx, dy, dz).getType(), profile)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void populateExistingTreeFruit(final PlanetDefinition definition,
                                           final Block rootBlock,
                                           final FruitTreeProfile profile) {
        int height = 1;
        while (height <= profile.maxHeight() + 1 && rootBlock.getRelative(0, height, 0).getType() == profile.trunkMaterial()) {
            height++;
        }
        int existingFruit = 0;
        final List<Block> fruitSpots = this.collectFruitSpots(definition, rootBlock, profile, height - 1);
        for (final Block spot : fruitSpots) {
            final String placedItemId = this.plugin.getPlacedTechBlockService().placedItemId(spot);
            if (profile.fruitItemId().equalsIgnoreCase(placedItemId)) {
                existingFruit++;
            }
        }
        fruitSpots.removeIf(spot -> !spot.isEmpty() || profile.fruitItemId().equalsIgnoreCase(this.plugin.getPlacedTechBlockService().placedItemId(spot)));
        final int missingFruit = Math.max(0, profile.fruitCount() - existingFruit);
        for (int index = 0; index < Math.min(missingFruit, fruitSpots.size()); index++) {
            this.placeFruitNode(fruitSpots.get(index), profile.fruitItemId());
        }
    }

    private List<Block> collectFruitSpots(final PlanetDefinition definition,
                                          final Block rootBlock,
                                          final FruitTreeProfile profile,
                                          final int referenceHeight) {
        final List<Block> fruitSpots = new ArrayList<>();
        final int minY = Math.max(1, referenceHeight - 1);
        final int maxY = Math.max(minY, referenceHeight + 1);
        for (int dy = minY; dy <= maxY; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) < 2) {
                        continue;
                    }
                    final Block spot = rootBlock.getRelative(dx, dy, dz);
                    if (fruitSpots.contains(spot)) {
                        continue;
                    }
                    if (this.canHostFruitNode(definition, spot, profile)) {
                        fruitSpots.add(spot);
                    }
                }
            }
        }
        return fruitSpots;
    }

    private boolean canHostFruitNode(final PlanetDefinition definition,
                                     final Block spot,
                                     final FruitTreeProfile profile) {
        if (spot == null) {
            return false;
        }
        final String placedItemId = this.plugin.getPlacedTechBlockService().placedItemId(spot);
        if (profile.fruitItemId().equalsIgnoreCase(placedItemId)) {
            return true;
        }
        if (!spot.isEmpty()) {
            return false;
        }
        for (final BlockFace face : List.of(BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            if (this.isFruitSupportMaterial(definition, spot.getRelative(face).getType(), profile)) {
                return true;
            }
        }
        return false;
    }

    private boolean isFruitSupportMaterial(final PlanetDefinition definition,
                                           final Material material,
                                           final FruitTreeProfile profile) {
        return material == profile.canopyMaterial()
                || material == profile.accentMaterial()
                || material == profile.trunkMaterial()
                || this.isFruitCanopyMaterial(definition, material, profile);
    }

    private boolean isFruitCanopyMaterial(final PlanetDefinition definition,
                                          final Material material,
                                          final FruitTreeProfile profile) {
        if (material == profile.canopyMaterial()) {
            return true;
        }
        return definition != null
                && "nyx".equalsIgnoreCase(definition.id())
                && material == Material.PURPLE_STAINED_GLASS;
    }

    private FruitTreeProfile fruitTreeProfileForSaplingId(final String saplingId) {
        if (saplingId == null || saplingId.isBlank()) {
            return null;
        }
        for (final FruitTreeProfile profile : this.allFruitTreeProfiles()) {
            if (profile != null && profile.saplingId().equalsIgnoreCase(saplingId)) {
                return profile;
            }
        }
        return null;
    }

    private FruitTreeProfile fruitTreeProfileForFruitId(final String fruitItemId) {
        if (fruitItemId == null || fruitItemId.isBlank()) {
            return null;
        }
        for (final FruitTreeProfile profile : this.allFruitTreeProfiles()) {
            if (profile != null && profile.fruitItemId().equalsIgnoreCase(fruitItemId)) {
                return profile;
            }
        }
        return null;
    }

    private String planetIdForFruitTree(final FruitTreeProfile profile) {
        if (profile == null) {
            return "";
        }
        for (final String planetId : this.planets.keySet()) {
            final FruitTreeProfile mapped = this.fruitTreeProfileForPlanet(planetId);
            if (mapped != null && mapped.saplingId().equalsIgnoreCase(profile.saplingId())) {
                return planetId;
            }
        }
        return "";
    }

    private boolean growFruitTree(final PlanetDefinition definition,
                                  final Block rootBlock,
                                  final FruitTreeProfile profile,
                                  final Random random,
                                  final boolean natural) {
        final Block soil = rootBlock.getRelative(BlockFace.DOWN);
        if (!soil.isSolid()) {
            return false;
        }
        final int height = profile.minHeight() + random.nextInt(Math.max(1, profile.maxHeight() - profile.minHeight() + 1));
        final List<Block> requiredAir = new ArrayList<>();
        for (int y = 0; y <= height + 2; y++) {
            requiredAir.add(rootBlock.getRelative(0, y, 0));
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = height - 1; dy <= height + 2; dy++) {
                    requiredAir.add(rootBlock.getRelative(dx, dy, dz));
                }
            }
        }
        for (final Block candidate : requiredAir) {
            if (!candidate.isEmpty() && !candidate.equals(rootBlock)) {
                if (this.canReplaceForFruitTree(definition, candidate)) {
                    this.plugin.getPlacedTechBlockService().unregister(candidate);
                    candidate.setType(Material.AIR, false);
                    continue;
                }
                return false;
            }
        }
        this.plugin.getPlacedTechBlockService().unregister(rootBlock);
        rootBlock.setType(profile.trunkMaterial(), false);
        for (int y = 1; y < height; y++) {
            rootBlock.getRelative(0, y, 0).setType(profile.trunkMaterial(), false);
        }
        final Block crown = rootBlock.getRelative(0, height, 0);
        crown.setType(profile.accentMaterial(), false);
        final int canopyBase = Math.max(1, height - 1);
        for (int dy = canopyBase; dy <= height + 1; dy++) {
            final int radius = dy >= height + 1 ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius > 1 && random.nextBoolean()) {
                        continue;
                    }
                    if (dx == 0 && dz == 0 && dy <= height) {
                        continue;
                    }
                    final Block canopy = rootBlock.getRelative(dx, dy, dz);
                    if (canopy.isEmpty()) {
                        this.setPersistentTreeBlock(canopy, profile.canopyMaterial());
                    }
                }
            }
        }
        final List<Block> fruitSpots = this.collectFruitSpots(definition, rootBlock, profile, height - 1);
        java.util.Collections.shuffle(fruitSpots, random);
        final int fruitCount = Math.min(profile.fruitCount(), fruitSpots.size());
        for (int index = 0; index < fruitCount; index++) {
            this.placeFruitNode(fruitSpots.get(index), profile.fruitItemId());
        }
        if (natural) {
            rootBlock.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, rootBlock.getLocation().add(0.5, height * 0.5, 0.5), 4, 0.3, 0.9, 0.3, 0.01);
        }
        return true;
    }

    private void setPersistentTreeBlock(final Block block, final Material material) {
        if (block == null || material == null) {
            return;
        }
        block.setType(material, false);
        if (block.getBlockData() instanceof Leaves leaves) {
            leaves.setPersistent(true);
            block.setBlockData(leaves, false);
        }
    }

    private boolean placeFruitNode(final Block block, final String fruitItemId) {
        if (block == null || fruitItemId == null || fruitItemId.isBlank() || this.registry.getItem(fruitItemId) == null) {
            return false;
        }
        final String headTexture = this.registry.getItem(fruitItemId).headTexture();
        if (headTexture == null || headTexture.isBlank()) {
            return false;
        }
        block.setType(Material.PLAYER_HEAD, false);
        if (!(block.getState() instanceof Skull skull)) {
            block.setType(Material.AIR, false);
            return false;
        }
        try {
            final URL skinUrl = this.normalizeHeadTextureUrl(headTexture);
            final PlayerProfile profile = Bukkit.createPlayerProfile(java.util.UUID.randomUUID());
            profile.getTextures().setSkin(skinUrl);
            skull.setOwnerProfile(profile);
            skull.setRotation(BlockFace.NORTH);
            skull.update(true, false);
            this.plugin.getPlacedTechBlockService().registerRuntimeBlock(block, fruitItemId);
            this.fruitNodeRegrowths.remove(LocationKey.from(block.getLocation()));
            return true;
        } catch (final IllegalArgumentException exception) {
            this.plugin.getLogger().warning("無法套用果實頭顱材質：" + fruitItemId + " / " + exception.getMessage());
            block.setType(Material.AIR, false);
            return false;
        }
    }

    private URL normalizeHeadTextureUrl(final String texture) {
        final String trimmed = texture == null ? "" : texture.trim();
        final String normalized = trimmed.startsWith("http://") || trimmed.startsWith("https://")
                ? trimmed
                : "http://textures.minecraft.net/texture/" + trimmed;
        try {
            return URI.create(normalized).toURL();
        } catch (final IllegalArgumentException | MalformedURLException exception) {
            throw new IllegalArgumentException("無效的頭顱材質網址：" + normalized, exception);
        }
    }

    private void buildPlanetFrontierRemnant(final PlanetDefinition definition,
                                            final World world,
                                            final int x,
                                            final int z,
                                            final Random random) {
        final int y = this.surfaceY(world, x, z);
        final Block floor = world.getBlockAt(x, y, z);
        if (!floor.isSolid()) {
            return;
        }
        final Material frame = switch (definition.id()) {
            case "aurelia" -> Material.QUARTZ_PILLAR;
            case "cryon" -> Material.POLISHED_DIORITE;
            case "nyx" -> Material.OBSIDIAN;
            case "helion" -> Material.BLACKSTONE;
            case "tempest" -> Material.WAXED_WEATHERED_CUT_COPPER;
            default -> Material.IRON_BLOCK;
        };
        final Material glass = switch (definition.id()) {
            case "aurelia" -> Material.PINK_STAINED_GLASS;
            case "cryon" -> Material.LIGHT_BLUE_STAINED_GLASS;
            case "nyx" -> Material.CRYING_OBSIDIAN;
            case "helion" -> Material.ORANGE_STAINED_GLASS;
            case "tempest" -> Material.CYAN_STAINED_GLASS;
            default -> Material.GLASS;
        };
        final Material beacon = switch (definition.id()) {
            case "aurelia" -> Material.END_ROD;
            case "cryon" -> Material.SEA_LANTERN;
            case "nyx" -> Material.END_ROD;
            case "helion" -> Material.SHROOMLIGHT;
            case "tempest" -> Material.LIGHTNING_ROD;
            default -> Material.LANTERN;
        };
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(x + dx, y + 1, z + dz).setType(Math.abs(dx) == 2 || Math.abs(dz) == 2 ? frame : glass, false);
            }
        }
        for (int dy = 2; dy <= 4; dy++) {
            world.getBlockAt(x - 2, y + dy, z - 2).setType(frame, false);
            world.getBlockAt(x - 2, y + dy, z + 2).setType(frame, false);
            world.getBlockAt(x + 2, y + dy, z - 2).setType(frame, false);
            world.getBlockAt(x + 2, y + dy, z + 2).setType(frame, false);
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(x + dx, y + 5, z + dz).setType(frame, false);
            }
        }
        world.getBlockAt(x, y + 2, z).setType(beacon, false);
        if (random.nextBoolean()) {
            this.scatterSurfacePatch(world, x, z, beacon, 3, 3);
        }
    }

    // ═══ 星球探險結構（前哨站 / 瞭望塔 / 地下密室 / 墜毀殘骸）═══

    private void generatePlanetStructures(final PlanetDefinition definition, final Chunk chunk) {
        // 迷途星有自己的迷宮結構系統，禁止通用星球瞭望塔/階梯/廢墟等建築
        if ("labyrinth".equals(definition.id())) {
            return;
        }
        final World world = chunk.getWorld();
        final Random random = new Random(world.getSeed() ^ ((long) definition.id().hashCode() << 31)
                ^ (chunk.getX() * 482716293781L) ^ (chunk.getZ() * 782913648173L));
        final int bx = chunk.getX() << 4;
        final int bz = chunk.getZ() << 4;
        if (Math.max(Math.abs(bx + 8), Math.abs(bz + 8)) < 64) {
            return;
        }
        if (random.nextInt(12) == 0) {
            this.buildPlanetOutpost(definition, world, bx + 3 + random.nextInt(8), bz + 3 + random.nextInt(8), random);
        }
        if (random.nextInt(18) == 0) {
            this.buildPlanetWatchtower(definition, world, bx + 4 + random.nextInt(8), bz + 4 + random.nextInt(8), random);
        }
        if (random.nextInt(20) == 0) {
            this.buildPlanetVault(definition, world, bx + 3 + random.nextInt(8), bz + 3 + random.nextInt(8), random);
        }
        if (random.nextInt(30) == 0) {
            this.buildPlanetCrashSite(definition, world, bx + 2 + random.nextInt(10), bz + 2 + random.nextInt(10), random);
        }
        if (random.nextInt(14) == 0) {
            this.buildBuriedRuin(definition, world, bx + 3 + random.nextInt(8), bz + 3 + random.nextInt(8), random);
        }
        if (random.nextInt(22) == 0) {
            this.buildFloatingPlatform(definition, world, bx + 3 + random.nextInt(8), bz + 3 + random.nextInt(8), random);
        }
        if (random.nextInt(16) == 0) {
            this.buildCliffShelter(definition, world, bx + 4 + random.nextInt(8), bz + 4 + random.nextInt(8), random);
        }
        if (random.nextInt(25) == 0) {
            this.buildAncientArchway(definition, world, bx + 3 + random.nextInt(10), bz + 3 + random.nextInt(10), random);
        }
    }

    private Material[] planetStructurePalette(final String planetId) {
        return switch (planetId) {
            case "aurelia" -> new Material[] { Material.PURPUR_BLOCK, Material.QUARTZ_BLOCK, Material.PURPUR_SLAB, Material.AMETHYST_BLOCK };
            case "cryon" -> new Material[] { Material.PACKED_ICE, Material.POLISHED_DIORITE, Material.SNOW_BLOCK, Material.BLUE_ICE };
            case "nyx" -> new Material[] { Material.END_STONE_BRICKS, Material.OBSIDIAN, Material.PURPUR_SLAB, Material.TINTED_GLASS };
            case "helion" -> new Material[] { Material.POLISHED_BLACKSTONE_BRICKS, Material.BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.MAGMA_BLOCK };
            case "tempest" -> new Material[] { Material.WEATHERED_CUT_COPPER, Material.CUT_COPPER, Material.OXIDIZED_CUT_COPPER, Material.COPPER_BLOCK };
            default -> new Material[] { Material.STONE_BRICKS, Material.STONE, Material.STONE_BRICK_SLAB, Material.COBBLESTONE };
        };
    }

    /**
     * 前哨站 — 5×5 牆壁建築，門口開放，內含 1 個戰利品桶。
     */
    private void buildPlanetOutpost(final PlanetDefinition definition, final World world,
                                    final int cx, final int cz, final Random random) {
        final int sy = this.surfaceY(world, cx, cz);
        if (!world.getBlockAt(cx, sy, cz).isSolid()) return;
        final Material[] pal = this.planetStructurePalette(definition.id());
        final Material wall = pal[0], floor = pal[1], roof = pal[2], accent = pal[3];
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(cx + dx, sy + 1, cz + dz).setType(floor, false);
                final boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                for (int dy = 2; dy <= 4; dy++) {
                    if (edge) {
                        if (dx == 0 && dz == -2 && dy <= 3) {
                            world.getBlockAt(cx + dx, sy + dy, cz + dz).setType(Material.AIR, false);
                        } else {
                            world.getBlockAt(cx + dx, sy + dy, cz + dz).setType(wall, false);
                        }
                    } else {
                        world.getBlockAt(cx + dx, sy + dy, cz + dz).setType(Material.AIR, false);
                    }
                }
                world.getBlockAt(cx + dx, sy + 5, cz + dz).setType(roof, false);
            }
        }
        world.getBlockAt(cx, sy + 4, cz).setType(accent, false);
        this.populateLootBarrel(world.getBlockAt(cx + 1, sy + 2, cz + 1), definition, random, 0);
    }

    /**
     * 瞭望塔 — 3×3 基底、8–12 格高，頂部平台有 1 個戰利品桶。
     */
    private void buildPlanetWatchtower(final PlanetDefinition definition, final World world,
                                       final int cx, final int cz, final Random random) {
        final int sy = this.surfaceY(world, cx, cz);
        if (!world.getBlockAt(cx, sy, cz).isSolid()) return;
        final Material[] pal = this.planetStructurePalette(definition.id());
        final Material wall = pal[0], floor = pal[1], accent = pal[3];
        final int height = 8 + random.nextInt(5);
        for (int dy = 1; dy <= height; dy++) {
            world.getBlockAt(cx - 1, sy + dy, cz - 1).setType(wall, false);
            world.getBlockAt(cx + 1, sy + dy, cz - 1).setType(wall, false);
            world.getBlockAt(cx - 1, sy + dy, cz + 1).setType(wall, false);
            world.getBlockAt(cx + 1, sy + dy, cz + 1).setType(wall, false);
            if (dy % 3 == 0) {
                world.getBlockAt(cx, sy + dy, cz).setType(floor, false);
            }
            world.getBlockAt(cx, sy + dy, cz - 1).setType(Material.LADDER, false);
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                world.getBlockAt(cx + dx, sy + height + 1, cz + dz).setType(floor, false);
            }
        }
        world.getBlockAt(cx, sy + height + 2, cz).setType(accent, false);
        this.populateLootBarrel(world.getBlockAt(cx + 1, sy + height + 2, cz), definition, random, 0);
    }

    /**
     * 地下密室 — 地下 5 格深的 5×5 房間，入口有階梯，含 1 個高級戰利品桶。
     */
    private void buildPlanetVault(final PlanetDefinition definition, final World world,
                                  final int cx, final int cz, final Random random) {
        final int sy = this.surfaceY(world, cx, cz);
        if (sy - 6 <= world.getMinHeight()) return;
        final Material[] pal = this.planetStructurePalette(definition.id());
        final Material wall = pal[0], floor = pal[1], accent = pal[3];
        final int vy = sy - 5;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                world.getBlockAt(cx + dx, vy, cz + dz).setType(floor, false);
                world.getBlockAt(cx + dx, vy + 4, cz + dz).setType(wall, false);
                final boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                for (int dy = 1; dy <= 3; dy++) {
                    world.getBlockAt(cx + dx, vy + dy, cz + dz).setType(edge ? wall : Material.AIR, false);
                }
            }
        }
        for (int step = 0; step < 5; step++) {
            world.getBlockAt(cx, sy - step, cz - 3 - step).setType(floor, false);
            world.getBlockAt(cx, sy - step + 1, cz - 3 - step).setType(Material.AIR, false);
            world.getBlockAt(cx, sy - step + 2, cz - 3 - step).setType(Material.AIR, false);
        }
        world.getBlockAt(cx, vy + 1, cz).setType(accent, false);
        this.populateLootBarrel(world.getBlockAt(cx - 1, vy + 1, cz + 1), definition, random, 1);
    }

    /**
     * 墜毀殘骸 — 不規則碎片散落 7×7 區域，含 1–2 個稀有戰利品桶。
     */
    private void buildPlanetCrashSite(final PlanetDefinition definition, final World world,
                                      final int cx, final int cz, final Random random) {
        final int sy = this.surfaceY(world, cx, cz);
        if (!world.getBlockAt(cx, sy, cz).isSolid()) return;
        final Material hull = Material.IRON_BLOCK;
        final Material frame = Material.HEAVY_WEIGHTED_PRESSURE_PLATE;
        final Material glass = Material.GRAY_STAINED_GLASS_PANE;
        for (int i = 0; i < 12 + random.nextInt(8); i++) {
            final int dx = random.nextInt(7) - 3;
            final int dz = random.nextInt(7) - 3;
            final int surfY = this.surfaceY(world, cx + dx, cz + dz);
            final Material debris = random.nextInt(3) == 0 ? glass : (random.nextBoolean() ? hull : frame);
            world.getBlockAt(cx + dx, surfY + 1, cz + dz).setType(debris, false);
            if (random.nextInt(4) == 0) {
                world.getBlockAt(cx + dx, surfY + 2, cz + dz).setType(hull, false);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(cx + dx, sy + 1, cz + dz).setType(hull, false);
            }
        }
        world.getBlockAt(cx, sy + 2, cz).setType(Material.REDSTONE_BLOCK, false);
        this.populateLootBarrel(world.getBlockAt(cx + 2, sy + 1, cz), definition, random, 2);
        if (random.nextInt(3) == 0) {
            this.populateLootBarrel(world.getBlockAt(cx - 2, sy + 1, cz + 1), definition, random, 2);
        }
    }

    /**
     * 埋沒遺跡 — 大部分結構埋入地下，只有頂端露出地面，需要向下挖掘進入。
     */
    private void buildBuriedRuin(final PlanetDefinition definition, final World world,
                                 final int cx, final int cz, final Random random) {
        final int sy = this.surfaceY(world, cx, cz);
        if (sy - 8 <= world.getMinHeight()) return;
        if (!world.getBlockAt(cx, sy, cz).isSolid()) return;
        final Material[] pal = this.planetStructurePalette(definition.id());
        final Material wall = pal[0], floor = pal[1], accent = pal[3];
        final int depth = 6 + random.nextInt(3);
        final int ry = sy - depth;
        final int sizeX = 3 + random.nextInt(2);
        final int sizeZ = 3 + random.nextInt(2);
        // 挖空內部
        for (int dx = -sizeX; dx <= sizeX; dx++) {
            for (int dz = -sizeZ; dz <= sizeZ; dz++) {
                final boolean edge = Math.abs(dx) == sizeX || Math.abs(dz) == sizeZ;
                world.getBlockAt(cx + dx, ry, cz + dz).setType(floor, false);
                world.getBlockAt(cx + dx, ry + depth + 1, cz + dz).setType(wall, false);
                for (int dy = 1; dy <= depth; dy++) {
                    world.getBlockAt(cx + dx, ry + dy, cz + dz).setType(edge ? wall : Material.AIR, false);
                }
            }
        }
        // 地面露出部分 — 破損的柱子頂端
        for (int corner = 0; corner < 4; corner++) {
            final int px = cx + (corner < 2 ? -sizeX : sizeX);
            final int pz = cx + (corner % 2 == 0 ? -sizeZ : sizeZ);
            final int pzActual = cz + (corner % 2 == 0 ? -sizeZ : sizeZ);
            if (random.nextBoolean()) {
                world.getBlockAt(px, sy + 1, pzActual).setType(accent, false);
                if (random.nextBoolean()) {
                    world.getBlockAt(px, sy + 2, pzActual).setType(accent, false);
                }
            }
        }
        // 入口階梯 — 向下的通道
        for (int step = 0; step <= depth; step++) {
            final int stairZ = cz - sizeZ - 1 - step;
            world.getBlockAt(cx, sy - step, stairZ).setType(floor, false);
            world.getBlockAt(cx + 1, sy - step, stairZ).setType(floor, false);
            world.getBlockAt(cx - 1, sy - step, stairZ).setType(wall, false);
            world.getBlockAt(cx + 2, sy - step, stairZ).setType(wall, false);
            for (int clearY = 1; clearY <= 2; clearY++) {
                world.getBlockAt(cx, sy - step + clearY, stairZ).setType(Material.AIR, false);
                world.getBlockAt(cx + 1, sy - step + clearY, stairZ).setType(Material.AIR, false);
            }
        }
        // 內部裝飾與戰利品
        world.getBlockAt(cx, ry + 1, cz).setType(accent, false);
        this.populateLootBarrel(world.getBlockAt(cx - 1, ry + 1, cz + 1), definition, random, 1);
        if (random.nextInt(3) == 0) {
            this.populateLootBarrel(world.getBlockAt(cx + 1, ry + 1, cz - 1), definition, random, 1);
        }
    }

    /**
     * 漂浮平台 — 空中的大型浮島建築，底部有倒三角支撐，頂部有戰利品。
     */
    private void buildFloatingPlatform(final PlanetDefinition definition, final World world,
                                       final int cx, final int cz, final Random random) {
        final int sy = this.surfaceY(world, cx, cz);
        final int floatY = Math.min(world.getMaxHeight() - 10, sy + 12 + random.nextInt(8));
        final Material[] pal = this.planetStructurePalette(definition.id());
        final Material main = pal[0], floor = pal[1], slab = pal[2], accent = pal[3];
        final int radius = 3 + random.nextInt(2);
        // 平台主體
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                final double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius + 0.3) continue;
                world.getBlockAt(cx + dx, floatY, cz + dz).setType(dist >= radius - 0.8 ? slab : floor, false);
                if (random.nextInt(3) == 0) {
                    world.getBlockAt(cx + dx, floatY - 1, cz + dz).setType(main, false);
                }
            }
        }
        // 底部倒三角支撐
        for (int layer = 1; layer <= 3; layer++) {
            final int shrink = layer;
            for (int dx = -radius + shrink; dx <= radius - shrink; dx++) {
                for (int dz = -radius + shrink; dz <= radius - shrink; dz++) {
                    if (random.nextInt(3) != 0) continue;
                    world.getBlockAt(cx + dx, floatY - layer - 1, cz + dz).setType(main, false);
                }
            }
        }
        // 頂部建築 — 四柱 + 屋頂
        for (int corner = 0; corner < 4; corner++) {
            final int px = cx + (corner < 2 ? -(radius - 1) : (radius - 1));
            final int pz = cz + (corner % 2 == 0 ? -(radius - 1) : (radius - 1));
            for (int dy = 1; dy <= 3; dy++) {
                world.getBlockAt(px, floatY + dy, pz).setType(main, false);
            }
            world.getBlockAt(px, floatY + 4, pz).setType(accent, false);
        }
        // 中央戰利品
        this.populateLootBarrel(world.getBlockAt(cx, floatY + 1, cz), definition, random, 1);
        // 連接地面的柱子（部分殘破）
        final int pillarX = cx + (random.nextBoolean() ? -1 : 1);
        final int pillarZ = cz + (random.nextBoolean() ? -1 : 1);
        for (int y = sy + 1; y < floatY; y++) {
            if (random.nextInt(4) != 0) {
                world.getBlockAt(pillarX, y, pillarZ).setType(main, false);
            }
        }
    }

    /**
     * 崖壁庇護所 — 偵測地形高度差，嵌入崖壁側面的房間。
     */
    private void buildCliffShelter(final PlanetDefinition definition, final World world,
                                   final int cx, final int cz, final Random random) {
        // 找到一側有明顯高度差的方向
        final int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int bestDir = -1;
        int bestDrop = 0;
        final int centerY = this.surfaceY(world, cx, cz);
        for (int d = 0; d < dirs.length; d++) {
            final int checkY = this.surfaceY(world, cx + dirs[d][0] * 4, cz + dirs[d][1] * 4);
            final int drop = centerY - checkY;
            if (drop > bestDrop) {
                bestDrop = drop;
                bestDir = d;
            }
        }
        if (bestDrop < 4 || bestDir < 0) return;
        final Material[] pal = this.planetStructurePalette(definition.id());
        final Material wall = pal[0], floor = pal[1], accent = pal[3];
        final int dx = dirs[bestDir][0];
        final int dz = dirs[bestDir][1];
        // 在崖壁面挖出洞穴 — 深 5 格、寬 3 格、高 4 格
        final int entryY = centerY - bestDrop / 2;
        for (int depth = 0; depth < 5; depth++) {
            final int wx = cx + dx * depth;
            final int wz = cz + dz * depth;
            for (int side = -1; side <= 1; side++) {
                final int sx = wx + (dx == 0 ? side : 0);
                final int sz = wz + (dz == 0 ? side : 0);
                world.getBlockAt(sx, entryY, sz).setType(floor, false);
                for (int dy = 1; dy <= 3; dy++) {
                    world.getBlockAt(sx, entryY + dy, sz).setType(Material.AIR, false);
                }
                world.getBlockAt(sx, entryY + 4, sz).setType(wall, false);
            }
        }
        // 崖壁外的門框裝飾
        final int frameX = cx - dx;
        final int frameZ = cz - dz;
        for (int dy = 1; dy <= 3; dy++) {
            world.getBlockAt(frameX + (dx == 0 ? -2 : 0), entryY + dy, frameZ + (dz == 0 ? -2 : 0)).setType(wall, false);
            world.getBlockAt(frameX + (dx == 0 ? 2 : 0), entryY + dy, frameZ + (dz == 0 ? 2 : 0)).setType(wall, false);
        }
        world.getBlockAt(frameX, entryY + 4, frameZ).setType(accent, false);
        // 最深處放置戰利品
        this.populateLootBarrel(world.getBlockAt(cx + dx * 4, entryY + 1, cz + dz * 4), definition, random, 1);
    }

    /**
     * 古代拱門 — 大型裝飾性拱門結構，有時包含隱藏戰利品。
     */
    private void buildAncientArchway(final PlanetDefinition definition, final World world,
                                     final int cx, final int cz, final Random random) {
        final int sy = this.surfaceY(world, cx, cz);
        if (!world.getBlockAt(cx, sy, cz).isSolid()) return;
        final Material[] pal = this.planetStructurePalette(definition.id());
        final Material pillar = pal[0], accent = pal[3];
        final boolean alongX = random.nextBoolean();
        final int height = 6 + random.nextInt(4);
        final int span = 3 + random.nextInt(2);
        // 雙柱
        for (int dy = 1; dy <= height; dy++) {
            world.getBlockAt(cx + (alongX ? -span : 0), sy + dy, cz + (alongX ? 0 : -span)).setType(pillar, false);
            world.getBlockAt(cx + (alongX ? span : 0), sy + dy, cz + (alongX ? 0 : span)).setType(pillar, false);
        }
        // 拱頂
        for (int i = -span; i <= span; i++) {
            final int archY = sy + height + 1 - Math.abs(i) / 2;
            world.getBlockAt(cx + (alongX ? i : 0), archY, cz + (alongX ? 0 : i)).setType(pillar, false);
        }
        // 頂部裝飾
        world.getBlockAt(cx, sy + height + 2, cz).setType(accent, false);
        // 底座裝飾
        for (int side = -1; side <= 1; side += 2) {
            final int bx = cx + (alongX ? side * span : side);
            final int bz = cz + (alongX ? side : side * span);
            world.getBlockAt(bx, sy + 1, bz).setType(accent, false);
        }
        // 拱門底部隱藏戰利品
        if (random.nextInt(3) == 0) {
            this.populateLootBarrel(world.getBlockAt(cx, sy + 1, cz), definition, random, 0);
        }
    }

    // ═══ 戰利品桶填充 ═══

    private static final String[][] PLANET_COMMON_LOOT = {
        { "irradiated_shard", "void_bloom", "copper_wire", "iron_plate" },
        { "cryonite_crystal", "frostbloom", "copper_wire", "iron_plate" },
        { "voidglass_fragment", "echo_spore", "copper_wire", "glass_pane" },
        { "solarite_shard", "emberroot", "iron_plate", "copper_wire" },
        { "stormglass_shard", "ion_fern", "iron_plate", "glass_pane" }
    };
    private static final String[][] PLANET_UNCOMMON_LOOT = {
        { "irradiated_shard", "aurelia_parasite_gland", "capacitor_bank", "void_bloom_seeds" },
        { "cryonite_crystal", "cryon_ice_heart", "control_unit", "frostbloom_seeds" },
        { "voidglass_fragment", "nyx_phase_tissue", "quantum_frame", "echo_spore_seeds" },
        { "solarite_shard", "helion_cinder_core", "fusion_core", "emberroot_seeds" },
        { "stormglass_shard", "tempest_capacitor", "fusion_core", "ion_fern_seeds" }
    };
    private static final String[][] PLANET_RARE_LOOT = {
        { "planetary_relic", "aurelia_parasite_gland", "ancient_signal", "capacitor_bank" },
        { "cryon_relic", "cryon_ice_heart", "ancient_signal", "control_unit" },
        { "nyx_relic", "nyx_phase_tissue", "ancient_signal", "quantum_frame" },
        { "helion_relic", "helion_cinder_core", "frontier_core_fragment", "fusion_core" },
        { "tempest_relic", "tempest_capacitor", "frontier_core_fragment", "fusion_core" }
    };

    private int planetLootIndex(final String planetId) {
        return switch (planetId) {
            case "aurelia" -> 0;
            case "cryon" -> 1;
            case "nyx" -> 2;
            case "helion" -> 3;
            case "tempest" -> 4;
            default -> 0;
        };
    }

    private void populateLootBarrel(final Block block, final PlanetDefinition definition,
                                    final Random random, final int tier) {
        block.setType(Material.BARREL, false);
        final Barrel barrel = (Barrel) block.getState();
        final Inventory inv = barrel.getSnapshotInventory();
        final int idx = this.planetLootIndex(definition.id());
        final String[][] table = tier <= 0 ? PLANET_COMMON_LOOT : tier == 1 ? PLANET_UNCOMMON_LOOT : PLANET_RARE_LOOT;
        final String[] pool = table[idx];
        final int items = switch (tier) {
            case 0 -> 3 + random.nextInt(3);
            case 1 -> 4 + random.nextInt(3);
            default -> 3 + random.nextInt(4);
        };
        for (int i = 0; i < items; i++) {
            final String itemId = pool[random.nextInt(pool.length)];
            final ItemStack stack = this.buildLootStack(itemId, 1 + random.nextInt(tier <= 0 ? 3 : tier == 1 ? 4 : 2));
            if (stack != null) {
                inv.setItem(random.nextInt(27), stack);
            }
        }
        final PersistentDataContainer pdc = barrel.getPersistentDataContainer();
        pdc.set(this.lootBarrelPlanetKey, PersistentDataType.STRING, definition.id());
        pdc.set(this.lootBarrelTierKey, PersistentDataType.INTEGER, tier);
        pdc.set(this.lootBarrelTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
        barrel.update(true, false);
    }

    private void refillLootBarrel(final Block block) {
        if (block.getType() != Material.BARREL) return;
        final Barrel barrel = (Barrel) block.getState();
        final PersistentDataContainer pdc = barrel.getPersistentDataContainer();
        final String planetId = pdc.get(this.lootBarrelPlanetKey, PersistentDataType.STRING);
        final Integer tier = pdc.get(this.lootBarrelTierKey, PersistentDataType.INTEGER);
        final Long lastFill = pdc.get(this.lootBarrelTimeKey, PersistentDataType.LONG);
        if (planetId == null || tier == null || lastFill == null) return;
        if (System.currentTimeMillis() - lastFill < LOOT_BARREL_REFILL_MS) return;
        // 檢查桶內是否已被清空（至少拿走了一半以上的物品）
        final Inventory inv = barrel.getSnapshotInventory();
        int occupied = 0;
        for (final ItemStack item : inv.getContents()) {
            if (item != null && !item.getType().isAir()) occupied++;
        }
        if (occupied > 2) return;
        // 刷新戰利品
        inv.clear();
        final PlanetDefinition definition = this.planets.get(planetId);
        if (definition == null) return;
        final Random random = new Random(System.currentTimeMillis() ^ block.getLocation().hashCode());
        final int idx = this.planetLootIndex(definition.id());
        final String[][] table = tier <= 0 ? PLANET_COMMON_LOOT : tier == 1 ? PLANET_UNCOMMON_LOOT : PLANET_RARE_LOOT;
        final String[] pool = table[idx];
        final int items = switch (tier) {
            case 0 -> 3 + random.nextInt(3);
            case 1 -> 4 + random.nextInt(3);
            default -> 3 + random.nextInt(4);
        };
        for (int i = 0; i < items; i++) {
            final String itemId = pool[random.nextInt(pool.length)];
            final ItemStack stack = this.buildLootStack(itemId, 1 + random.nextInt(tier <= 0 ? 3 : tier == 1 ? 4 : 2));
            if (stack != null) {
                inv.setItem(random.nextInt(27), stack);
            }
        }
        pdc.set(this.lootBarrelTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
        barrel.update(true, false);
    }

    private ItemStack buildLootStack(final String id, final int amount) {
        final var techItem = this.registry.getItem(id);
        if (techItem != null) {
            final ItemStack stack = this.itemFactory.buildTechItem(techItem);
            stack.setAmount(amount);
            return stack;
        }
        try {
            return new ItemStack(Material.valueOf(id.toUpperCase(Locale.ROOT)), amount);
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private void carvePlanetCrater(final PlanetDefinition definition,
                                   final World world,
                                   final int centerX,
                                   final int centerZ,
                                   final int radius,
                                   final Random random) {
        final Material rimMaterial = switch (definition.id()) {
            case "aurelia" -> Material.CALCITE;
            case "cryon" -> Material.BLUE_ICE;
            case "nyx" -> Material.OBSIDIAN;
            case "helion" -> Material.MAGMA_BLOCK;
            case "tempest" -> Material.WEATHERED_CUT_COPPER;
            default -> Material.STONE;
        };
        final Material coreMaterial = switch (definition.id()) {
            case "aurelia" -> Material.AMETHYST_BLOCK;
            case "cryon" -> Material.PACKED_ICE;
            case "nyx" -> Material.END_STONE;
            case "helion" -> Material.BLACKSTONE;
            case "tempest" -> Material.CUT_COPPER;
            default -> Material.STONE;
        };
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                final double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius + 0.4D) {
                    continue;
                }
                final int x = centerX + dx;
                final int z = centerZ + dz;
                final int surfaceY = this.surfaceY(world, x, z);
                final Block surface = world.getBlockAt(x, surfaceY, z);
                if (distance <= radius - 1.2D) {
                    surface.setType(coreMaterial, false);
                    final Block above = world.getBlockAt(x, surfaceY + 1, z);
                    if (!above.isEmpty() && above.getType() != rimMaterial) {
                        above.setType(Material.AIR, false);
                    }
                    if (random.nextInt(3) != 0 && surfaceY - 1 > world.getMinHeight()) {
                        world.getBlockAt(x, surfaceY - 1, z).setType(coreMaterial, false);
                    }
                } else {
                    surface.setType(rimMaterial, false);
                    if (random.nextBoolean()) {
                        world.getBlockAt(x, surfaceY + 1, z).setType(rimMaterial, false);
                    }
                }
            }
        }
    }

    private void buildPlanetCrystalCluster(final PlanetDefinition definition,
                                           final World world,
                                           final int x,
                                           final int z,
                                           final Random random) {
        final Material crystal = switch (definition.id()) {
            case "aurelia" -> Material.AMETHYST_BLOCK;
            case "cryon" -> Material.BLUE_ICE;
            case "nyx" -> Material.TINTED_GLASS;
            case "helion" -> Material.SHROOMLIGHT;
            case "tempest" -> Material.OXIDIZED_CUT_COPPER;
            default -> Material.QUARTZ_BLOCK;
        };
        for (int index = 0; index < 3 + random.nextInt(3); index++) {
            final int offsetX = x + random.nextInt(5) - 2;
            final int offsetZ = z + random.nextInt(5) - 2;
            final int baseY = this.surfaceY(world, offsetX, offsetZ) + 1;
            this.buildSpire(world, offsetX, baseY, offsetZ, crystal, 2 + random.nextInt(5));
        }
    }

    private void carvePlanetRift(final PlanetDefinition definition,
                                 final World world,
                                 final int x,
                                 final int z,
                                 final Random random) {
        final boolean alongX = random.nextBoolean();
        final int length = 7 + random.nextInt(7);
        final int width = 1 + random.nextInt(2);
        final Material seamMaterial = switch (definition.id()) {
            case "aurelia" -> Material.SCULK;
            case "cryon" -> Material.BLUE_ICE;
            case "nyx" -> Material.OBSIDIAN;
            case "helion" -> Material.MAGMA_BLOCK;
            case "tempest" -> Material.WEATHERED_COPPER;
            default -> Material.DEEPSLATE;
        };
        for (int step = -length; step <= length; step++) {
            for (int side = -width; side <= width; side++) {
                final int worldX = alongX ? x + step : x + side;
                final int worldZ = alongX ? z + side : z + step;
                final int surfaceY = this.surfaceY(world, worldX, worldZ);
                for (int depth = 0; depth < 4; depth++) {
                    world.getBlockAt(worldX, surfaceY - depth, worldZ).setType(depth == 0 ? Material.AIR : seamMaterial, false);
                }
            }
        }
    }

    private void buildFloatingShard(final PlanetDefinition definition,
                                    final World world,
                                    final int x,
                                    final int z,
                                    final Random random) {
        final int surfaceY = this.surfaceY(world, x, z);
        final int islandY = Math.min(world.getMaxHeight() - 4, surfaceY + 7 + random.nextInt(5));
        final Material islandMaterial = switch (definition.id()) {
            case "aurelia" -> Material.CALCITE;
            case "cryon" -> Material.PACKED_ICE;
            case "nyx" -> Material.END_STONE;
            case "helion" -> Material.BLACKSTONE;
            case "tempest" -> Material.WEATHERED_CUT_COPPER;
            default -> Material.STONE;
        };
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 2) {
                    continue;
                }
                world.getBlockAt(x + dx, islandY, z + dz).setType(islandMaterial, false);
                if (random.nextBoolean()) {
                    world.getBlockAt(x + dx, islandY - 1, z + dz).setType(islandMaterial, false);
                }
            }
        }
        final Material shard = switch (definition.id()) {
            case "aurelia" -> Material.AMETHYST_CLUSTER;
            case "cryon" -> Material.ICE;
            case "nyx" -> Material.END_ROD;
            case "helion" -> Material.SHROOMLIGHT;
            case "tempest" -> Material.LIGHTNING_ROD;
            default -> Material.QUARTZ_PILLAR;
        };
        final Block top = world.getBlockAt(x, islandY + 1, z);
        if (top.isEmpty()) {
            top.setType(shard, false);
        }
    }

    private void highlightRuinCore(final World world, final int x, final int y, final int z) {
        // 清除遺跡核心周圍的方塊，確保玩家可以到達並互動
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    final Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (dx == 0 && dz == 0 && dy == 0) {
                        continue; // 保留核心方塊本身
                    }
                    b.setType(Material.AIR, false);
                }
            }
        }
        final Location loc = new Location(world, x, y, z);
        // 清除舊的發光標記
        for (final Entity nearby : world.getNearbyEntities(loc.clone().add(0.5, 0.5, 0.5), 1.0, 1.0, 1.0)) {
            if (nearby instanceof BlockDisplay bd && bd.getScoreboardTags().contains("ruin_glow")) {
                bd.remove();
            }
        }
        world.spawn(loc, BlockDisplay.class, display -> {
            display.setBlock(world.getBlockAt(x, y, z).getBlockData());
            display.setGlowing(true);
            display.setPersistent(true);
            display.addScoreboardTag("ruin_glow");
        });
    }

    private void buildRuinRing(final World world,
                               final int centerX,
                               final int centerY,
                               final int centerZ,
                               final Material pillar,
                               final Material accent,
                               final int radius) {
        for (int index = 0; index < 8; index++) {
            final double angle = Math.toRadians(index * 45.0D);
            final int x = centerX + (int) Math.round(Math.cos(angle) * radius);
            final int z = centerZ + (int) Math.round(Math.sin(angle) * radius);
            this.buildSpire(world, x, centerY, z, pillar, 4 + (index % 2));
            world.getBlockAt(x, centerY + 4, z).setType(accent, false);
        }
    }

    private void buildSpire(final World world,
                            final int baseX,
                            final int baseY,
                            final int baseZ,
                            final Material material,
                            final int height) {
        for (int y = 0; y < height; y++) {
            world.getBlockAt(baseX, baseY + y, baseZ).setType(material, false);
        }
    }

    // ═══ 星球主動怪物生成（繞過光照限制）═══

    /** 每個玩家附近維持的最大怪物數量（接近原版體感） */
    private static final int PLANET_MOB_CAP_PER_PLAYER = 30;
    private static final long ELITE_SKILL_COOLDOWN_MS = 4000L;
    private static final long ELITE_AURA_TICK_INTERVAL = 60L;
    private static final double ELITE_SKILL_RANGE = 6.0D;
    /** 怪物生成的最小/最大距離（格） */
    private static final int MOB_SPAWN_MIN_DISTANCE = 16;
    private static final int MOB_SPAWN_MAX_DISTANCE = 48;
    /** 生成後的最低存活時間（ticks），防止怪物剛生成就消失 */
    private static final int MOB_SPAWN_GRACE_TICKS = 20 * 60;

    private void tickPlanetMobSpawning() {
        for (final PlanetDefinition definition : this.planets.values()) {
            final World world = this.resolveExistingPlanetWorld(definition);
            if (world == null) {
                continue;
            }
            final List<Player> players = new ArrayList<>(world.getPlayers());
            for (final Player player : players) {
                if (!player.isValid() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                final Location base = player.getLocation();
                final java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
                final int attempts = 3 + rng.nextInt(3);
                for (int i = 0; i < attempts; i++) {
                    final double angle = rng.nextDouble() * Math.PI * 2.0D;
                    final int radius = MOB_SPAWN_MIN_DISTANCE + rng.nextInt(MOB_SPAWN_MAX_DISTANCE - MOB_SPAWN_MIN_DISTANCE + 1);
                    final int x = base.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
                    final int z = base.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                        continue;
                    }
                    final org.bukkit.entity.EntityType mobType = this.planetMobTypeFor(definition);
                    if (mobType == null) {
                        continue;
                    }
                    final Location regionAnchor = new Location(world, x + 0.5D, 64, z + 0.5D);
                    this.scheduler.runRegion(regionAnchor, task -> {
                        try {
                            final Location capCenter = new Location(world, x + 0.5D, 64, z + 0.5D);
                            final long nearby = world.getNearbyEntities(capCenter, MOB_SPAWN_MAX_DISTANCE, 64, MOB_SPAWN_MAX_DISTANCE)
                                    .stream().filter(e -> e instanceof Monster).count();
                            if (nearby >= PLANET_MOB_CAP_PER_PLAYER) {
                                return;
                            }
                            final int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                            if (y <= world.getMinHeight() + 1 || y > world.getMaxHeight() - 2) {
                                return;
                            }
                            final Block feet = world.getBlockAt(x, y, z);
                            final Block below = world.getBlockAt(x, y - 1, z);
                            if (!below.isSolid() || !feet.isPassable() || !feet.getRelative(BlockFace.UP).isPassable()) {
                                return;
                            }
                            final Location spawnLoc = new Location(world, x + 0.5D, y, z + 0.5D);
                            final Entity spawned = world.spawnEntity(spawnLoc, mobType);
                            this.applyPlanetMobSpawn(spawned, spawnLoc, mobType, definition);
                        } catch (final Exception ex) {
                            this.plugin.getLogger().warning("[生怪] 生成異常 ("
                                    + definition.id() + " " + mobType + " @ " + x + "," + z + "): "
                                    + ex.getClass().getSimpleName() + " — " + ex.getMessage());
                        }
                    });
                }
            }
        }
    }

    /**
     * 統一處理新生成的星球怪物 — 設定屬性 + 出場特效。
     */
    private void applyPlanetMobSpawn(final Entity spawned, final Location spawnLoc, final org.bukkit.entity.EntityType mobType, final PlanetDefinition definition) {
        if (!(spawned instanceof LivingEntity living)) {
            return;
        }
        living.setRemoveWhenFarAway(true);
        // 給予生成保護時間，避免剛生出來就原版消失
        living.setNoDamageTicks(0);
        if (living instanceof Mob mob) {
            mob.setAware(true);
        }
        // 暫時設為不會消失，grace 時間後恢復
        living.setPersistent(true);
        this.scheduler.runEntityDelayed(living, () -> {
            if (living.isValid()) {
                living.setPersistent(false);
            }
        }, MOB_SPAWN_GRACE_TICKS);
        // 白天星球的亡靈系怪物需要防火
        if (living instanceof Monster && this.isUndead(mobType)) {
            living.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 60 * 10, 0, true, false, false));
        }
        // 嘗試菁英化
        this.tryEmpowerPlanetMob(living);

        // ── 出場特效 ──
        final World world = spawnLoc.getWorld();
        final Location vfxLoc = spawnLoc.clone().add(0.0, 0.5, 0.0);
        final boolean isElite = living.getScoreboardTags().contains(PLANET_ELITE_TAG);

        // 基礎粒子 — 所有怪物都有地面裂隙效果
        final Particle.DustOptions baseDust = this.planetSpawnDust(definition.id());
        world.spawnParticle(Particle.DUST, vfxLoc, 20, 0.5, 0.8, 0.5, 0.01, baseDust);
        world.spawnParticle(Particle.SMOKE, spawnLoc.clone().add(0, 0.2, 0), 10, 0.4, 0.1, 0.4, 0.03);
        world.playSound(spawnLoc, Sound.BLOCK_SCULK_SPREAD, SoundCategory.HOSTILE, 0.7f, 0.6f);

        if (isElite) {
            // 精英出場特效 — 更華麗
            world.spawnParticle(Particle.DUST, vfxLoc, 35, 0.8, 1.2, 0.8, 0.02, baseDust);
            world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, vfxLoc, 15, 0.5, 0.8, 0.5, 0.03);
            world.spawnParticle(Particle.END_ROD, vfxLoc.clone().add(0, 0.5, 0), 10, 0.3, 0.5, 0.3, 0.02);
            // 上升螺旋粒子（延遲執行 3 幀）
            this.scheduler.runRegionDelayed(spawnLoc, task -> {
                if (!spawnLoc.isWorldLoaded()) return;
                for (int ring = 0; ring < 8; ring++) {
                    final double angle = Math.toRadians(ring * 45);
                    final double rx = Math.cos(angle) * 1.0;
                    final double rz = Math.sin(angle) * 1.0;
                    world.spawnParticle(Particle.DUST, spawnLoc.clone().add(rx, 0.3 + ring * 0.15, rz), 2, 0.05, 0.05, 0.05, 0.0, baseDust);
                }
            }, 3L);
            // 精英出場音效
            world.playSound(spawnLoc, Sound.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 0.6f, 1.3f);
            world.playSound(spawnLoc, Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, SoundCategory.HOSTILE, 0.8f, 0.8f);

            // 精英發光效果 2 秒
            living.setGlowing(true);
            this.scheduler.runEntityDelayed(living, () -> {
                if (living.isValid()) {
                    living.setGlowing(false);
                }
            }, 40L);
        }
    }

    /**
     * 根據星球返回出場粒子顏色。
     */
    private Particle.DustOptions planetSpawnDust(final String planetId) {
        return switch (planetId) {
            case "aurelia" -> new Particle.DustOptions(Color.fromRGB(100, 220, 50), 1.5f);   // 輻射綠
            case "cryon" -> new Particle.DustOptions(Color.fromRGB(140, 210, 255), 1.5f);     // 冰藍
            case "nyx" -> new Particle.DustOptions(Color.fromRGB(130, 60, 200), 1.5f);        // 虛空紫
            case "helion" -> new Particle.DustOptions(Color.fromRGB(255, 120, 30), 1.5f);     // 烈焰橙
            case "tempest" -> new Particle.DustOptions(Color.fromRGB(60, 180, 255), 1.5f);    // 雷電青
            default -> new Particle.DustOptions(Color.fromRGB(200, 200, 200), 1.2f);
        };
    }

    private org.bukkit.entity.EntityType planetMobTypeFor(final PlanetDefinition definition) {
        final java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        return switch (definition.id()) {
            case "aurelia" -> {
                final int roll = rng.nextInt(10);
                yield roll < 4 ? org.bukkit.entity.EntityType.HUSK
                        : roll < 7 ? org.bukkit.entity.EntityType.SKELETON
                        : roll < 9 ? org.bukkit.entity.EntityType.SPIDER
                        : org.bukkit.entity.EntityType.CAVE_SPIDER;
            }
            case "cryon" -> {
                final int roll = rng.nextInt(10);
                yield roll < 4 ? org.bukkit.entity.EntityType.STRAY
                        : roll < 7 ? org.bukkit.entity.EntityType.SKELETON
                        : roll < 9 ? org.bukkit.entity.EntityType.ZOMBIE
                        : org.bukkit.entity.EntityType.SPIDER;
            }
            case "nyx" -> {
                final int roll = rng.nextInt(10);
                yield roll < 3 ? org.bukkit.entity.EntityType.ENDERMAN
                        : roll < 6 ? org.bukkit.entity.EntityType.PHANTOM
                        : roll < 8 ? org.bukkit.entity.EntityType.SKELETON
                        : org.bukkit.entity.EntityType.CREEPER;
            }
            case "helion" -> {
                final int roll = rng.nextInt(10);
                yield roll < 4 ? org.bukkit.entity.EntityType.BLAZE
                        : roll < 7 ? org.bukkit.entity.EntityType.WITHER_SKELETON
                        : roll < 9 ? org.bukkit.entity.EntityType.HUSK
                        : org.bukkit.entity.EntityType.MAGMA_CUBE;
            }
            case "tempest" -> {
                final int roll = rng.nextInt(10);
                yield roll < 4 ? org.bukkit.entity.EntityType.ZOMBIE
                        : roll < 7 ? org.bukkit.entity.EntityType.CREEPER
                        : roll < 9 ? org.bukkit.entity.EntityType.SKELETON
                        : org.bukkit.entity.EntityType.WITCH;
            }
            case "labyrinth" -> {
                final int roll = rng.nextInt(20);
                yield roll < 6 ? org.bukkit.entity.EntityType.ZOMBIE
                        : roll < 11 ? org.bukkit.entity.EntityType.CAVE_SPIDER
                        : roll < 15 ? org.bukkit.entity.EntityType.SKELETON
                        : roll < 18 ? org.bukkit.entity.EntityType.SILVERFISH
                        : org.bukkit.entity.EntityType.PHANTOM;
            }
            default -> null;
        };
    }

    /**
     * 遺跡挑戰專用怪物池：排除會飛的怪物（BLAZE、PHANTOM），
     * 確保玩家能在限時內清完所有怪物。
     */
    private org.bukkit.entity.EntityType ruinChallengeMobTypeFor(final PlanetDefinition definition) {
        final java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        return switch (definition.id()) {
            case "aurelia" -> {
                final int roll = rng.nextInt(10);
                yield roll < 4 ? org.bukkit.entity.EntityType.HUSK
                        : roll < 7 ? org.bukkit.entity.EntityType.SKELETON
                        : org.bukkit.entity.EntityType.SPIDER;
            }
            case "cryon" -> {
                final int roll = rng.nextInt(10);
                yield roll < 4 ? org.bukkit.entity.EntityType.STRAY
                        : roll < 7 ? org.bukkit.entity.EntityType.SKELETON
                        : org.bukkit.entity.EntityType.ZOMBIE;
            }
            case "nyx" -> {
                final int roll = rng.nextInt(10);
                // 移除 PHANTOM，改為 ENDERMAN / SKELETON / CREEPER
                yield roll < 4 ? org.bukkit.entity.EntityType.ENDERMAN
                        : roll < 7 ? org.bukkit.entity.EntityType.SKELETON
                        : org.bukkit.entity.EntityType.CREEPER;
            }
            case "helion" -> {
                final int roll = rng.nextInt(10);
                // 移除 BLAZE（會飛），改為 WITHER_SKELETON / HUSK / MAGMA_CUBE
                yield roll < 4 ? org.bukkit.entity.EntityType.WITHER_SKELETON
                        : roll < 7 ? org.bukkit.entity.EntityType.HUSK
                        : org.bukkit.entity.EntityType.MAGMA_CUBE;
            }
            case "tempest" -> {
                final int roll = rng.nextInt(10);
                yield roll < 4 ? org.bukkit.entity.EntityType.ZOMBIE
                        : roll < 7 ? org.bukkit.entity.EntityType.CREEPER
                        : roll < 9 ? org.bukkit.entity.EntityType.SKELETON
                        : org.bukkit.entity.EntityType.WITCH;
            }
            case "labyrinth" -> {
                final int roll = rng.nextInt(10);
                yield roll < 4 ? org.bukkit.entity.EntityType.ZOMBIE
                        : roll < 7 ? org.bukkit.entity.EntityType.CAVE_SPIDER
                        : roll < 9 ? org.bukkit.entity.EntityType.SKELETON
                        : org.bukkit.entity.EntityType.SILVERFISH;
            }
            default -> this.planetMobTypeFor(definition);
        };
    }

    private boolean isUndead(final org.bukkit.entity.EntityType type) {
        return type == org.bukkit.entity.EntityType.SKELETON
                || type == org.bukkit.entity.EntityType.STRAY
                || type == org.bukkit.entity.EntityType.ZOMBIE
                || type == org.bukkit.entity.EntityType.HUSK
                || type == org.bukkit.entity.EntityType.PHANTOM
                || type == org.bukkit.entity.EntityType.WITHER_SKELETON;
    }

    private void tickPlanetHazards() {
        for (final PlanetDefinition definition : this.planets.values()) {
            final World world = this.ensurePlanetWorld(definition);
            if (world == null) {
                continue;
            }
            for (final Player player : world.getPlayers()) {
                if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                this.scheduler.runEntity(player, () -> {
                    if (!player.isValid() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                        return;
                    }
                    try {
                        this.applyPlanetHazard(definition, player);
                    } catch (final RuntimeException exception) {
                        this.plugin.getLogger().warning("星球危害套用失敗：" + definition.id() + " / " + player.getName() + " / " + exception.getClass().getSimpleName());
                    }
                });
            }
        }
    }

    private void tickPlanetGravity() {
        for (final PlanetDefinition definition : this.planets.values()) {
            final World world = this.ensurePlanetWorld(definition);
            if (world == null) {
                continue;
            }
            for (final Player player : world.getPlayers()) {
                if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                this.scheduler.runEntity(player, () -> {
                    if (!player.isValid() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                        return;
                    }
                    try {
                        this.applyPlanetLowGravity(definition, player);
                    } catch (final RuntimeException exception) {
                        this.plugin.getLogger().warning("星球重力套用失敗：" + definition.id() + " / " + player.getName() + " / " + exception.getClass().getSimpleName());
                    }
                });
            }
        }
    }

    private void tickPlanetAmbience() {
        for (final PlanetDefinition definition : this.planets.values()) {
            final World world = this.ensurePlanetWorld(definition);
            if (world == null) {
                continue;
            }
            for (final Player player : world.getPlayers()) {
                if (player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                this.scheduler.runEntity(player, () -> {
                    if (!player.isValid() || player.getGameMode() == GameMode.SPECTATOR) {
                        return;
                    }
                    try {
                        this.applyPlanetAmbience(definition, player);
                    } catch (final RuntimeException exception) {
                        this.plugin.getLogger().warning("星球環境演出失敗：" + definition.id() + " / " + player.getName() + " / " + exception.getClass().getSimpleName());
                    }
                });
            }
        }
    }

    private void applyPlanetAmbience(final PlanetDefinition definition, final Player player) {
        final Location origin = player.getLocation().add(0.0, 1.2, 0.0);
        switch (definition.id()) {
            case "aurelia" -> {
                if (this.ambientRandom.nextInt(2) == 0) {
                    player.getWorld().spawnParticle(Particle.ENCHANT, origin, 10, 1.0, 0.6, 1.0, 0.04);
                    player.getWorld().spawnParticle(Particle.ASH, origin, 8, 0.95, 0.55, 0.95, 0.015);
                    this.spawnAmbientDustBurst(player, origin, Color.fromRGB(255, 191, 120), Color.fromRGB(255, 114, 222), 8, 0.88D);
                }
                if (this.ambientRandom.nextInt(6) == 0) {
                    this.playAttachedPlanetSound(player, Sound.BLOCK_BEACON_AMBIENT, SoundCategory.AMBIENT, 0.55f, 0.62f);
                }
                if (this.ambientRandom.nextInt(18) == 0) {
                    this.playAttachedPlanetSound(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, 0.42f, 1.85f);
                }
            }
            case "cryon" -> {
                if (this.ambientRandom.nextInt(2) == 0) {
                    player.getWorld().spawnParticle(Particle.SNOWFLAKE, origin, 12, 1.15, 0.75, 1.15, 0.015);
                    player.getWorld().spawnParticle(Particle.WHITE_ASH, origin, 6, 0.95, 0.45, 0.95, 0.008);
                    this.spawnAmbientDustBurst(player, origin, Color.fromRGB(174, 237, 255), Color.fromRGB(118, 176, 255), 9, 0.92D);
                }
                if (this.ambientRandom.nextInt(5) == 0) {
                    this.playAttachedPlanetSound(player, Sound.BLOCK_POWDER_SNOW_FALL, SoundCategory.AMBIENT, 0.6f, 0.62f);
                }
            }
            case "nyx" -> {
                if (this.ambientRandom.nextInt(2) == 0) {
                    player.getWorld().spawnParticle(Particle.PORTAL, origin, 14, 1.0, 0.85, 1.0, 0.08);
                    player.getWorld().spawnParticle(Particle.END_ROD, origin, 6, 0.8, 0.45, 0.8, 0.015);
                    this.spawnAmbientDustBurst(player, origin, Color.fromRGB(145, 92, 255), Color.fromRGB(43, 18, 79), 10, 0.84D);
                }
                if (this.ambientRandom.nextInt(4) == 0) {
                    this.playAttachedPlanetSound(player, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, SoundCategory.AMBIENT, 0.65f, 0.45f);
                }
            }
            case "helion" -> {
                if (this.ambientRandom.nextInt(2) == 0) {
                    player.getWorld().spawnParticle(Particle.ASH, origin, 10, 1.1, 0.55, 1.1, 0.015);
                    player.getWorld().spawnParticle(Particle.FLAME, origin, 6, 0.7, 0.4, 0.7, 0.015);
                    this.spawnAmbientDustBurst(player, origin, Color.fromRGB(255, 126, 54), Color.fromRGB(255, 214, 99), 9, 0.86D);
                }
                if (this.ambientRandom.nextInt(5) == 0) {
                    this.playAttachedPlanetSound(player, Sound.AMBIENT_BASALT_DELTAS_MOOD, SoundCategory.AMBIENT, 0.62f, 0.74f);
                }
                if (this.ambientRandom.nextInt(20) == 0) {
                    this.playAttachedPlanetSound(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, 0.48f, 0.58f);
                }
            }
            case "tempest" -> {
                if (this.ambientRandom.nextInt(2) == 0) {
                    player.getWorld().spawnParticle(Particle.CLOUD, origin, 12, 1.25, 0.5, 1.25, 0.02);
                    player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, origin, 8, 1.0, 0.45, 1.0, 0.03);
                    this.spawnAmbientDustBurst(player, origin, Color.fromRGB(78, 255, 244), Color.fromRGB(43, 110, 255), 11, 0.96D);
                }
                if (this.ambientRandom.nextInt(3) == 0) {
                    this.playAttachedPlanetSound(player, Sound.WEATHER_RAIN_ABOVE, SoundCategory.AMBIENT, 0.9f, 0.7f);
                }
                if (this.ambientRandom.nextInt(5) == 0) {
                    this.playAttachedPlanetSound(player, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.AMBIENT, 0.85f, 0.92f);
                }
                if (this.ambientRandom.nextInt(7) == 0) {
                    this.triggerAmbientLightning(player, 10, 26);
                }
            }
            case "labyrinth" -> {
                if (this.ambientRandom.nextInt(2) == 0) {
                    player.getWorld().spawnParticle(Particle.SCULK_SOUL, origin, 8, 1.1, 0.6, 1.1, 0.015);
                    player.getWorld().spawnParticle(Particle.SMOKE, origin, 6, 0.9, 0.45, 0.9, 0.01);
                    player.getWorld().spawnParticle(Particle.ASH, origin, 15, 2.0, 1.5, 2.0, 0.02);
                    this.spawnAmbientDustBurst(player, origin, Color.fromRGB(22, 60, 82), Color.fromRGB(10, 180, 160), 9, 0.88D);
                }
                if (this.ambientRandom.nextInt(4) == 0) {
                    this.playAttachedPlanetSound(player, Sound.BLOCK_SCULK_SENSOR_CLICKING, SoundCategory.AMBIENT, 0.45f, 0.5f);
                }
                if (this.ambientRandom.nextInt(8) == 0) {
                    this.playAttachedPlanetSound(player, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, SoundCategory.AMBIENT, 0.55f, 0.38f);
                }
                if (this.ambientRandom.nextInt(12) == 0) {
                    this.playAttachedPlanetSound(player, Sound.ENTITY_WARDEN_AMBIENT, SoundCategory.AMBIENT, 0.3f, 0.4f);
                }
                if (this.ambientRandom.nextInt(20) == 0) {
                    this.playAttachedPlanetSound(player, Sound.ENTITY_PHANTOM_AMBIENT, SoundCategory.AMBIENT, 0.35f, 0.5f);
                }
                if (this.ambientRandom.nextInt(15) == 0) {
                    this.playAttachedPlanetSound(player, Sound.AMBIENT_NETHER_WASTES_MOOD, SoundCategory.AMBIENT, 0.4f, 0.3f);
                }
            }
            default -> {
            }
        }
    }

    private void spawnAmbientDustBurst(final Player player,
                                       final Location origin,
                                       final Color startColor,
                                       final Color endColor,
                                       final int amount,
                                       final double radius) {
        if (player == null || origin == null || startColor == null || endColor == null || amount <= 0) {
            return;
        }
        player.spawnParticle(Particle.DUST, origin, amount, radius, 0.34D, radius, 0.01D,
                new Particle.DustOptions(startColor, 1.18F));
        player.spawnParticle(Particle.DUST_COLOR_TRANSITION, origin, Math.max(6, amount / 2), radius * 0.82D, 0.26D, radius * 0.82D, 0.01D,
                new Particle.DustTransition(startColor, endColor, 1.22F));
        player.spawnParticle(Particle.GLOW, origin, Math.max(4, amount / 3), radius * 0.6D, 0.18D, radius * 0.6D, 0.01D);
    }

    private void playAttachedPlanetSound(final Player player,
                                         final Sound sound,
                                         final SoundCategory category,
                                         final float volume,
                                         final float pitch) {
        if (player == null || sound == null) {
            return;
        }
        try {
            player.playSound(player, sound, category, volume, pitch);
        } catch (final NoSuchMethodError ignored) {
            player.playSound(player.getLocation(), sound, category, volume, pitch);
        }
    }

    private void triggerAmbientLightning(final Player player, final int minDistance, final int maxDistance) {
        final World world = player.getWorld();
        final Location base = player.getLocation();
        final int distance = minDistance + this.ambientRandom.nextInt(Math.max(1, maxDistance - minDistance + 1));
        final double angle = this.ambientRandom.nextDouble() * Math.PI * 2.0D;
        final int x = base.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
        final int z = base.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
        final Location anchor = new Location(world, x + 0.5, Math.max(world.getMinHeight() + 2, base.getBlockY()), z + 0.5);
        this.scheduler.runRegion(anchor, task -> {
            final int y = Math.max(world.getMinHeight() + 2, world.getHighestBlockYAt(x, z) + 1);
            final Location strike = new Location(world, x + 0.5, y, z + 0.5);
            world.strikeLightningEffect(strike);
            world.spawnParticle(Particle.ELECTRIC_SPARK, strike.clone().add(0.0, 1.0, 0.0), 20, 0.55, 1.0, 0.55, 0.03);
        });
    }

    private void applyPlanetHazard(final PlanetDefinition definition, final Player player) {
        this.enforcePlanetBoundary(player);
        final boolean cuisineWard = this.hasCuisineWard(player, definition.hazardType());
        if (!cuisineWard || this.ambientRandom.nextBoolean()) {
            player.setFoodLevel(Math.max(0, player.getFoodLevel() - 1));
        }
        if (this.hasProtection(player, definition.suitIds())) {
            switch (definition.hazardType()) {
                case RADIATION -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, false, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, 0, false, false, true));
                }
                case CRYO -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, false, true));
                    player.setFreezeTicks(Math.max(0, player.getFreezeTicks() - 20));
                }
                case VACUUM -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 60, 0, false, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 60, 0, false, false, true));
                }
                case SOLAR -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 60, 0, false, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 30, 0, false, false, true));
                }
                case STORM -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 60, 0, false, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, false, false, true));
                }
                case MIASMA -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, 0, false, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 60, 0, false, false, true));
                }
            }
            return;
        }
        switch (definition.hazardType()) {
            case RADIATION -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0, false, true, true));
                if (cuisineWard) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 80, 0, false, true, true));
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 90, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 90, 1, false, true, true));
                    player.damage(1.0D);
                }
                player.getWorld().spawnParticle(Particle.ASH, player.getLocation().add(0.0, 1.0, 0.0), 8, 0.35, 0.45, 0.35, 0.01);
                this.playAttachedPlanetSound(player, Sound.ENTITY_PLAYER_BREATH, SoundCategory.PLAYERS, 0.35f, 0.7f);
            }
            case CRYO -> {
                if (cuisineWard) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 0, false, true, true));
                    player.setFreezeTicks(Math.max(player.getFreezeTicks(), 40));
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 90, 1, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 90, 1, false, true, true));
                    player.setFreezeTicks(Math.max(player.getFreezeTicks(), 160));
                    player.damage(1.0D);
                }
                player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0.0, 1.0, 0.0), 12, 0.3, 0.4, 0.3, 0.01);
                this.playAttachedPlanetSound(player, Sound.BLOCK_POWDER_SNOW_HIT, SoundCategory.PLAYERS, 0.35f, 0.8f);
            }
            case VACUUM -> {
                if (cuisineWard) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 80, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0, false, true, true));
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 70, 0, false, true, true));
                    if (this.ambientRandom.nextInt(4) == 0) {
                        player.setVelocity(player.getVelocity().add(new Vector((this.ambientRandom.nextDouble() - 0.5D) * 0.18D, 0.05D, (this.ambientRandom.nextDouble() - 0.5D) * 0.18D)));
                    }
                    player.damage(1.0D);
                }
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0.0, 1.0, 0.0), 10, 0.3, 0.4, 0.3, 0.05);
                this.playAttachedPlanetSound(player, Sound.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 0.3f, 0.65f);
            }
            case SOLAR -> {
                if (cuisineWard) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 80, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 40, 0, false, true, true));
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 90, 1, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 90, 0, false, true, true));
                    player.setFireTicks(Math.max(player.getFireTicks(), 80));
                    player.damage(1.0D);
                }
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0.0, 1.0, 0.0), 12, 0.3, 0.45, 0.3, 0.01);
                this.playAttachedPlanetSound(player, Sound.BLOCK_FIRE_AMBIENT, SoundCategory.PLAYERS, 0.35f, 1.2f);
            }
            case STORM -> {
                if (cuisineWard) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 0, false, true, true));
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.UNLUCK, 120, 0, false, true, true));
                    if (this.ambientRandom.nextInt(4) == 0) {
                        player.setVelocity(player.getVelocity().add(new Vector((this.ambientRandom.nextDouble() - 0.5D) * 0.24D, 0.08D, (this.ambientRandom.nextDouble() - 0.5D) * 0.24D)));
                    }
                    player.damage(1.0D);
                }
                player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, player.getLocation().add(0.0, 1.0, 0.0), 12, 0.35, 0.45, 0.35, 0.02);
                this.playAttachedPlanetSound(player, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 0.25f, 1.4f);
            }
            case MIASMA -> {
                if (cuisineWard) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 80, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 0, false, true, true));
                } else {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 0, false, true, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 90, 0, false, true, true));
                    // 視覺 debuff（DARKNESS）已移除 — 迷途星主要靠氛圍與敵人壓迫感，不再強制黑屏
                    player.damage(1.0D);
                }
                player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0.0, 1.0, 0.0), 10, 0.35, 0.45, 0.35, 0.02);
                this.playAttachedPlanetSound(player, Sound.BLOCK_SCULK_SPREAD, SoundCategory.PLAYERS, 0.35f, 0.6f);
            }
        }
    }

    private void applyPlanetLowGravity(final PlanetDefinition definition, final Player player) {
        if (definition == null || player == null || !player.isValid() || player.isFlying() || player.isGliding() || player.isInsideVehicle()) {
            return;
        }
        if (player.getLocation().clone().subtract(0.0D, 0.15D, 0.0D).getBlock().getType().isSolid()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 8, this.jumpAmplifier(definition), false, false, false));
            return;
        }
        final Vector velocity = player.getVelocity();
        final Vector adjusted = velocity.clone();
        boolean changed = false;
        if (velocity.getY() < 0.0D) {
            adjusted.setY(Math.max(-0.42D, velocity.getY() * this.gravityScale(definition)));
            changed = true;
        }
        switch (definition.id()) {
            case "aurelia" -> {
                if (velocity.getY() < -0.08D) {
                    adjusted.setY(adjusted.getY() + 0.015D);
                    changed = true;
                }
            }
            case "nyx" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 6, 0, false, false, false));
                if (this.ambientRandom.nextInt(8) == 0) {
                    adjusted.setY(Math.min(0.18D, adjusted.getY() + 0.045D));
                    changed = true;
                }
            }
            case "helion" -> {
                if (velocity.getY() < 0.16D) {
                    adjusted.setY(Math.min(0.22D, adjusted.getY() + 0.02D));
                    changed = true;
                }
            }
            case "tempest" -> {
                adjusted.setX(adjusted.getX() + (this.ambientRandom.nextDouble() - 0.5D) * 0.015D);
                adjusted.setZ(adjusted.getZ() + (this.ambientRandom.nextDouble() - 0.5D) * 0.015D);
                changed = true;
            }
            default -> {
            }
        }
        if (changed) {
            player.setVelocity(adjusted);
        }
        player.setFallDistance(Math.max(0.0f, player.getFallDistance() - 0.85f));
    }

    private void enforcePlanetBoundary(final Player player) {
        if (player == null || !player.isValid()) {
            return;
        }
        final Location location = player.getLocation();
        final double dx = location.getX();
        final double dz = location.getZ();
        final double distanceSquared = dx * dx + dz * dz;
        final double radiusSquared = PLANET_BOUNDARY_RADIUS * (double) PLANET_BOUNDARY_RADIUS;
        if (distanceSquared <= radiusSquared) {
            return;
        }
        final double distance = Math.sqrt(distanceSquared);
        final double safeRadius = PLANET_BOUNDARY_RADIUS - 12.0D;
        final double scale = safeRadius / Math.max(1.0D, distance);
        final Location bounce = location.clone();
        bounce.setX(dx * scale);
        bounce.setZ(dz * scale);
        final long now = System.currentTimeMillis();
        final long lastWarn = this.boundaryWarningCooldowns.getOrDefault(player.getUniqueId(), 0L);
        this.boundaryWarningCooldowns.put(player.getUniqueId(), now);
        final Vector push = new Vector(-dx, 0.0D, -dz).normalize().multiply(1.15D).setY(0.32D);
        this.scheduler.runEntity(player, () -> {
            if (!player.isValid()) {
                return;
            }
            player.teleportAsync(bounce);
            player.setVelocity(push);
        });
        if (now - lastWarn > 1800L) {
            player.sendActionBar(this.itemFactory.warning("你已探索到宇宙盡頭，前方只剩沉默與真空。"));
            player.playSound(location, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.55f, 0.58f);
            player.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 0.7f);
        }
    }

    private boolean hasProtection(final Player player, final List<String> expected) {
        if (this.hasSuit(player, expected) || this.hasSuit(player, FRONTIER_SUIT)) {
            return true;
        }
        return expected.equals(FRONTIER_SUIT) && this.hasAnyPreFrontierSuit(player);
    }

    private boolean hasAnyPreFrontierSuit(final Player player) {
        for (final List<String> suit : PRE_FRONTIER_SUITS) {
            if (this.hasSuit(player, suit)) {
                return true;
            }
        }
        return false;
    }

    private String planetProtectionHint(final PlanetDefinition definition) {
        if (definition == null) {
            return "需要完整防護套裝";
        }
        if (definition.suitIds().equals(FRONTIER_SUIT)) {
            return "建議前線套裝；未完成前至少穿任一完整環境套裝";
        }
        return "防護需求「" + this.itemFactory.displayNameForId(definition.suitIds().get(1)) + "」套裝";
    }

    private boolean hasSuit(final Player player, final List<String> expected) {
        final var helmet = player.getInventory().getHelmet();
        final var chestplate = player.getInventory().getChestplate();
        final var leggings = player.getInventory().getLeggings();
        final var boots = player.getInventory().getBoots();
        final var equipped = new org.bukkit.inventory.ItemStack[]{helmet, chestplate, leggings, boots};
        for (int index = 0; index < expected.size(); index++) {
            final var stack = equipped[index];
            final String techId = this.itemFactory.getTechItemId(stack);
            if (techId == null || !expected.get(index).equalsIgnoreCase(techId.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        return true;
    }

    private PlanetDefinition planetByWorld(final World world) {
        if (world == null) {
            return null;
        }
        return this.planets.values().stream()
                .filter(definition -> this.matchesPlanetWorldName(definition, world.getName()))
                .findFirst()
                .orElse(null);
    }

    private World resolveExistingPlanetWorld(final PlanetDefinition definition) {
        World world = Bukkit.getWorld(definition.worldName());
        if (world != null) {
            return world;
        }
        for (final String alias : definition.worldAliases()) {
            world = Bukkit.getWorld(alias);
            if (world != null) {
                return world;
            }
        }
        return null;
    }

    /**
     * 公開查詢指定星球的世界（依主名稱 + 別名查詢）。
     */
    public World findPlanetWorld(final String planetId) {
        final PlanetDefinition def = this.planets.get(planetId);
        if (def == null) {
            return null;
        }
        return this.resolveExistingPlanetWorld(def);
    }

    /**
     * 檢查 world 是否為給定 planetId 的世界。
     */
    public boolean isPlanetWorld(final String planetId, final World world) {
        if (world == null || planetId == null) {
            return false;
        }
        final PlanetDefinition def = this.planets.get(planetId);
        if (def == null) {
            return false;
        }
        return this.matchesPlanetWorldName(def, world.getName());
    }

    private boolean matchesPlanetWorldName(final PlanetDefinition definition, final String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return false;
        }
        if (definition.worldName().equalsIgnoreCase(worldName)) {
            return true;
        }
        return definition.worldAliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(worldName));
    }

    private boolean isDatapackPlanetWorld(final PlanetDefinition definition, final String worldName) {
        if (definition == null || worldName == null || worldName.isBlank()) {
            return false;
        }
        return definition.worldAliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(worldName));
    }

    private String chunkKey(final Chunk chunk) {
        return chunk.getWorld().getName().toLowerCase(Locale.ROOT)
                + ":" + chunk.getWorld().getUID().toString().toLowerCase(Locale.ROOT)
                + ":" + chunk.getX()
                + ":" + chunk.getZ();
    }

    private PlanetHarvestProfile harvestProfileFor(final PlanetDefinition definition,
                                                   final Material material,
                                                   final boolean includeRelics) {
        return switch (definition.id()) {
            case "aurelia" -> switch (material) {
                case AMETHYST_CLUSTER -> new PlanetHarvestProfile("irradiated_shard", 1, null, 0, 180L, 260L, true);
                case SCULK_CATALYST -> new PlanetHarvestProfile("void_bloom", 2, "void_bloom_seeds", 1, 260L, 340L, true);
                case CRYING_OBSIDIAN, RESPAWN_ANCHOR -> includeRelics ? new PlanetHarvestProfile("planetary_relic", 1, null, 0, 720L, 900L, true) : null;
                default -> null;
            };
            case "cryon" -> switch (material) {
                case ICE -> new PlanetHarvestProfile("cryonite_crystal", 1, null, 0, 190L, 270L, true);
                case WHITE_TULIP -> new PlanetHarvestProfile("frostbloom", 2, "frostbloom_seeds", 1, 260L, 340L, true);
                case LODESTONE, CHISELED_POLISHED_BLACKSTONE -> includeRelics ? new PlanetHarvestProfile("cryon_relic", 1, null, 0, 720L, 900L, true) : null;
                default -> null;
            };
            case "nyx" -> switch (material) {
                case CRYING_OBSIDIAN -> new PlanetHarvestProfile("voidglass_fragment", 1, null, 0, 190L, 270L, true);
                case CHORUS_FLOWER -> new PlanetHarvestProfile("echo_spore", 2, "echo_spore_seeds", 1, 270L, 350L, true);
                case END_PORTAL_FRAME, ENDER_CHEST -> includeRelics ? new PlanetHarvestProfile("nyx_relic", 1, null, 0, 740L, 920L, true) : null;
                default -> null;
            };
            case "helion" -> switch (material) {
                case SHROOMLIGHT -> new PlanetHarvestProfile("solarite_shard", 1, null, 0, 190L, 270L, true);
                case CRIMSON_ROOTS -> new PlanetHarvestProfile("emberroot", 2, "emberroot_seeds", 1, 260L, 340L, true);
                case BEACON, GILDED_BLACKSTONE -> includeRelics ? new PlanetHarvestProfile("helion_relic", 1, null, 0, 740L, 920L, true) : null;
                default -> null;
            };
            case "tempest" -> switch (material) {
                case SEA_LANTERN -> new PlanetHarvestProfile("stormglass_shard", 1, null, 0, 190L, 270L, true);
                case LIGHTNING_ROD -> new PlanetHarvestProfile("ion_fern", 2, "ion_fern_seeds", 1, 260L, 340L, true);
                case EXPOSED_COPPER -> includeRelics ? new PlanetHarvestProfile("tempest_relic", 1, null, 0, 740L, 920L, true) : null;
                default -> null;
            };
            case "labyrinth" -> switch (material) {
                case SCULK -> new PlanetHarvestProfile("labyrinth_fragment", 1, null, 0, 190L, 270L, true);
                case MOSS_BLOCK -> new PlanetHarvestProfile("maze_vine", 2, "maze_vine_seeds", 1, 260L, 340L, true);
                case REINFORCED_DEEPSLATE -> includeRelics ? new PlanetHarvestProfile("labyrinth_relic", 1, null, 0, 740L, 920L, true) : null;
                default -> null;
            };
            default -> null;
        };
    }

    private String hazardLabel(final HazardType hazardType) {
        return switch (hazardType) {
            case RADIATION -> "輻晶灼蝕";
            case CRYO -> "凍骨寒流";
            case VACUUM -> "相位失壓";
            case SOLAR -> "日冕灼燒";
            case STORM -> "導電亂流";
            case MIASMA -> "瘴氣侵蝕";
        };
    }

    private String gravityLabel(final PlanetDefinition definition) {
        final double gravity = Math.round(this.gravityScale(definition) * 100.0D) / 100.0D;
        return "低重力 " + gravity + "g";
    }

    private double gravityScale(final PlanetDefinition definition) {
        return switch (definition.id()) {
            case "aurelia" -> 0.78D;
            case "cryon" -> 0.82D;
            case "nyx" -> 0.58D;
            case "helion" -> 0.74D;
            case "tempest" -> 0.70D;
            case "labyrinth" -> 0.92D;
            default -> 0.88D;
        };
    }

    private int jumpAmplifier(final PlanetDefinition definition) {
        return switch (definition.id()) {
            case "nyx", "helion", "tempest" -> 1;
            default -> 0;
        };
    }

    private void grantCuisineWard(final Player player,
                                  final HazardType hazardType,
                                  final long durationTicks,
                                  final String label) {
        final long expiresAt = System.currentTimeMillis() + (durationTicks * 50L);
        this.cuisineWardExpiries.computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>()).put(hazardType, expiresAt);
        player.sendActionBar(this.itemFactory.success(label + " 緩衝已啟用。"));
    }

    private boolean hasCuisineWard(final Player player, final HazardType hazardType) {
        if (player == null) {
            return false;
        }
        final Map<HazardType, Long> wards = this.cuisineWardExpiries.get(player.getUniqueId());
        if (wards == null) {
            return false;
        }
        final long expiresAt = wards.getOrDefault(hazardType, 0L);
        if (expiresAt <= System.currentTimeMillis()) {
            wards.remove(hazardType);
            if (wards.isEmpty()) {
                this.cuisineWardExpiries.remove(player.getUniqueId());
            }
            return false;
        }
        return true;
    }

    private List<ItemStack> harvestPlanetBlockForPlayer(final Player player,
                                                        final Block block,
                                                        final boolean includeRelics) {
        final PlanetDefinition definition = this.planetByWorld(block == null ? null : block.getWorld());
        if (player == null || block == null || definition == null) {
            return List.of();
        }
        final PlanetHarvestProfile profile = this.harvestProfileFor(definition, block.getType(), includeRelics);
        if (profile == null || !this.isHarvestSurfaceNode(block) || !this.isPlayerHarvestReady(player, block, profile)) {
            return List.of();
        }
        this.markPlayerHarvest(player, block, profile.playerRespawnTicks());
        return this.buildHarvestOutputs(profile);
    }

    private List<ItemStack> harvestPlanetBlockForMachine(final Block block, final boolean commit) {
        final PlanetDefinition definition = this.planetByWorld(block == null ? null : block.getWorld());
        if (block == null || definition == null) {
            return List.of();
        }
        final PlanetHarvestProfile profile = this.harvestProfileFor(definition, block.getType(), false);
        if (profile == null || !this.isHarvestSurfaceNode(block)) {
            return List.of();
        }
        final LocationKey key = LocationKey.from(block.getLocation());
        final long now = System.currentTimeMillis();
        final long readyAt = this.machineHarvestCooldowns.getOrDefault(key, 0L);
        if (readyAt > now) {
            return List.of();
        }
        if (commit) {
            this.machineHarvestCooldowns.put(key, now + profile.machineRespawnTicks() * 50L);
        }
        return this.buildHarvestOutputs(profile);
    }

    private List<ItemStack> buildHarvestOutputs(final PlanetHarvestProfile profile) {
        final List<ItemStack> outputs = new ArrayList<>();
        final ItemStack primary = this.buildTechStack(profile.dropItemId(), profile.dropAmount());
        if (primary != null) {
            outputs.add(primary);
        }
        if (profile.seedItemId() != null && !profile.seedItemId().isBlank()) {
            final ItemStack seed = this.buildTechStack(profile.seedItemId(), profile.seedAmount());
            if (seed != null) {
                outputs.add(seed);
            }
        }
        this.appendLabyrinthBonusDrops(profile, outputs);
        return outputs;
    }

    // 異世界（labyrinth）採集附加掉物：
    //  ▸ labyrinth_fragment 主掉：10% 機率額外掉 1 顆 mana_meteorite（科技線燃料）
    //  ▸ labyrinth_relic 主掉：25% 機率額外掉 1~2 顆 mana_meteorite；3% 機率隨機掉落一把 RPG 武器
    private void appendLabyrinthBonusDrops(final PlanetHarvestProfile profile, final List<ItemStack> outputs) {
        if (profile == null || profile.dropItemId() == null) {
            return;
        }
        final java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        final String id = profile.dropItemId();
        if ("labyrinth_fragment".equals(id)) {
            if (rng.nextDouble() < 0.10D) {
                final ItemStack meteor = this.buildTechStack("mana_meteorite", 1);
                if (meteor != null) outputs.add(meteor);
            }
        } else if ("labyrinth_relic".equals(id)) {
            if (rng.nextDouble() < 0.25D) {
                final ItemStack meteor = this.buildTechStack("mana_meteorite", 1 + rng.nextInt(2));
                if (meteor != null) outputs.add(meteor);
            }
            if (rng.nextDouble() < 0.03D) {
                final String[] weapons = { "riftblade", "frostfang_bow", "thunder_hammer", "arcane_scepter" };
                final ItemStack rpg = this.buildTechStack(weapons[rng.nextInt(weapons.length)], 1);
                if (rpg != null) outputs.add(rpg);
            }
        }
    }

    private ItemStack buildTechStack(final String itemId, final int amount) {
        if (itemId == null || itemId.isBlank() || this.registry.getItem(itemId) == null) {
            return null;
        }
        final ItemStack prototype = this.techItemStackCache.computeIfAbsent(itemId,
                key -> this.itemFactory.buildTechItem(this.registry.getItem(key)).clone());
        final ItemStack stack = prototype.clone();
        stack.setAmount(Math.min(Math.max(1, amount), stack.getMaxStackSize()));
        return stack;
    }

    private boolean isPlayerHarvestReady(final Player player,
                                         final Block block,
                                         final PlanetHarvestProfile profile) {
        if (player == null || block == null) {
            return false;
        }
        final Map<LocationKey, Long> cooldowns = this.personalHarvestCooldowns.get(player.getUniqueId());
        final long readyAt = cooldowns == null ? 0L : cooldowns.getOrDefault(LocationKey.from(block.getLocation()), 0L);
        return readyAt <= System.currentTimeMillis();
    }

    private void markPlayerHarvest(final Player player,
                                   final Block block,
                                   final long respawnTicks) {
        if (player == null || block == null) {
            return;
        }
        this.personalHarvestCooldowns
                .computeIfAbsent(player.getUniqueId(), ignored -> new ConcurrentHashMap<>())
                .put(LocationKey.from(block.getLocation()), System.currentTimeMillis() + Math.max(20L, respawnTicks) * 50L);
    }

    private void tickPersonalPlanetNodes() {
        for (final PlanetDefinition definition : this.planets.values()) {
            final World world = this.ensurePlanetWorld(definition);
            if (world == null) {
                continue;
            }
            for (final Player player : world.getPlayers()) {
                if (!player.isValid() || player.getGameMode() == GameMode.SPECTATOR) {
                    continue;
                }
                this.scheduler.runEntity(player, () -> {
                    if (!player.isValid() || player.getGameMode() == GameMode.SPECTATOR) {
                        return;
                    }
                    this.refreshPersonalPlanetNodes(player, definition);
                });
            }
        }
    }

    private void refreshPersonalPlanetNodes(final Player player,
                                            final PlanetDefinition definition) {
        final Location base = player.getLocation();
        final World world = base.getWorld();
        if (world == null) {
            return;
        }
        final int minX = base.getBlockX() - PERSONAL_NODE_SCAN_RADIUS;
        final int maxX = base.getBlockX() + PERSONAL_NODE_SCAN_RADIUS;
        final int minZ = base.getBlockZ() - PERSONAL_NODE_SCAN_RADIUS;
        final int maxZ = base.getBlockZ() + PERSONAL_NODE_SCAN_RADIUS;
        final int minY = Math.max(world.getMinHeight(), base.getBlockY() - PERSONAL_NODE_SCAN_Y_RADIUS);
        final int maxY = Math.min(world.getMaxHeight() - 1, base.getBlockY() + PERSONAL_NODE_SCAN_Y_RADIUS);
        final long now = System.currentTimeMillis();
        final double maxDistanceSquared = Math.pow(PERSONAL_NODE_SCAN_RADIUS + 0.75D, 2.0D);
        final List<HarvestNodeCandidate> candidates = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    final Block block = world.getBlockAt(x, y, z);
                    final PlanetHarvestProfile profile = this.harvestProfileFor(definition, block.getType(), false);
                    if (profile == null) {
                        continue;
                    }
                    if (!this.isHarvestSurfaceNode(block)) {
                        this.hideHarvestNode(player, block);
                        continue;
                    }
                    final LocationKey key = LocationKey.from(block.getLocation());
                    final long readyAt = this.machineHarvestCooldowns.getOrDefault(key, 0L);
                    if (readyAt > now) {
                        this.hideHarvestNode(player, block);
                        continue;
                    }
                    final double dx = (x + 0.5D) - base.getX();
                    final double dy = (y + 0.5D) - base.getY();
                    final double dz = (z + 0.5D) - base.getZ();
                    final double distanceSquared = dx * dx + dy * dy + dz * dz;
                    if (distanceSquared > maxDistanceSquared) {
                        this.hideHarvestNode(player, block);
                        continue;
                    }
                    candidates.add(new HarvestNodeCandidate(block, profile, distanceSquared));
                }
            }
        }
        candidates.sort((left, right) -> Double.compare(left.distanceSquared(), right.distanceSquared()));
        final int limit = Math.min(MAX_VISIBLE_HARVEST_NODES, candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            final HarvestNodeCandidate candidate = candidates.get(index);
            if (index >= limit) {
                this.hideHarvestNode(player, candidate.block());
                continue;
            }
            this.ensureHarvestNodeDisplay(candidate.block(), candidate.profile());
            this.showHarvestNode(player, candidate.block());
            if (candidate.profile().animated() && this.ambientRandom.nextInt(6) == 0) {
                player.spawnParticle(Particle.GLOW, candidate.block().getLocation().add(0.5, 0.72, 0.5), 3, 0.14, 0.16, 0.14, 0.01);
                player.spawnParticle(Particle.WAX_ON, candidate.block().getLocation().add(0.5, 0.68, 0.5), 1, 0.1, 0.12, 0.1, 0.01);
            }
        }
    }

    private boolean collectPlanetSurfaceNode(final Player player,
                                             final Block block,
                                             final PlanetDefinition definition) {
        final PlanetHarvestProfile profile = this.harvestProfileFor(definition, block.getType(), false);
        if (profile == null || !this.isHarvestSurfaceNode(block)) {
            return false;
        }
        final PendingHarvest pending = this.pendingHarvests.get(player.getUniqueId());
        final LocationKey key = LocationKey.from(block.getLocation());
        if (pending != null) {
            if (!pending.nodeKey().equals(key)) {
                player.sendActionBar(this.itemFactory.warning("你正在採集另一個節點，請先完成當前採集。"));
                return true;
            }
            player.sendActionBar(this.itemFactory.secondary("採集中 " + this.harvestProgressBar(this.pendingHarvestProgress(pending))));
            return true;
        }
        this.ensureHarvestNodeDisplay(block, profile);
        if (!this.isPlayerHarvestReady(player, block, profile)) {
            player.sendActionBar(this.itemFactory.warning("這個採集節點還在重新凝聚中。"));
            player.playSound(block.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.3f, 0.75f);
            this.hideHarvestNode(player, block);
            return true;
        }
        return this.beginPlanetSurfaceHarvest(player, block, definition, profile);
    }

    private boolean beginPlanetSurfaceHarvest(final Player player,
                                              final Block block,
                                              final PlanetDefinition definition,
                                              final PlanetHarvestProfile profile) {
        final int harvestTicks = this.harvestDurationTicks(profile);
        final long now = System.currentTimeMillis();
        final long token = System.nanoTime();
        final PendingHarvest pending = new PendingHarvest(LocationKey.from(block.getLocation()), token, now, now + harvestTicks * 50L, harvestTicks);
        this.pendingHarvests.put(player.getUniqueId(), pending);
        final Location center = block.getLocation().add(0.5, 0.72, 0.5);
        player.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.42f, 1.22f);
        player.playSound(center, Sound.ITEM_BRUSH_BRUSHING_GENERIC, 0.35f, 0.92f);
        player.sendActionBar(this.itemFactory.secondary("開始採集「" + this.itemFactory.displayNameForId(profile.dropItemId()) + "」 " + this.harvestProgressBar(0.0D)));
        // 迷途星：播放鎬子挖掘動畫
        if (LABYRINTH_WORLD.equals(block.getWorld().getName())) {
            this.plugin.getMazeService().playMiningAnimation(player, block);
        }
        this.scheduler.runEntityTimer(player, task -> {
            final PendingHarvest current = this.pendingHarvests.get(player.getUniqueId());
            if (current == null || current.token() != token) {
                task.cancel();
                return;
            }
            if (!player.isValid() || player.getWorld() != block.getWorld() || player.getLocation().distanceSquared(center) > 9.0D) {
                this.pendingHarvests.remove(player.getUniqueId(), current);
                task.cancel();
                player.sendActionBar(this.itemFactory.warning("你離開了採集範圍，採集已中斷。"));
                player.playSound(center, Sound.BLOCK_FIRE_EXTINGUISH, 0.25f, 0.7f);
                // 中斷時取消動畫
                if (LABYRINTH_WORLD.equals(block.getWorld().getName())) {
                    this.plugin.getMazeService().cancelMiningAnimation(player);
                }
                return;
            }
            final double progress = this.pendingHarvestProgress(current);
            player.sendActionBar(this.itemFactory.secondary("採集中「" + this.itemFactory.displayNameForId(profile.dropItemId()) + "」 " + this.harvestProgressBar(progress)));
            if (profile.animated()) {
                block.getWorld().spawnParticle(Particle.WAX_ON, center, 2 + (int) Math.floor(progress * 4.0D), 0.14D, 0.18D, 0.14D, 0.01D);
                block.getWorld().spawnParticle(Particle.GLOW, center.clone().add(0.0D, 0.18D, 0.0D), 2 + (int) Math.floor(progress * 5.0D), 0.16D, 0.2D, 0.16D, 0.01D);
            }
            if (progress >= 1.0D) {
                this.pendingHarvests.remove(player.getUniqueId(), current);
                task.cancel();
                this.completePlanetSurfaceHarvest(player, block, definition, profile);
            }
        }, 1L, 2L);
        return true;
    }

    private void completePlanetSurfaceHarvest(final Player player,
                                              final Block block,
                                              final PlanetDefinition definition,
                                              final PlanetHarvestProfile profile) {
        final List<ItemStack> outputs = this.harvestPlanetBlockForPlayer(player, block, false);
        if (outputs.isEmpty()) {
            player.sendActionBar(this.itemFactory.warning("這個節點暫時無法採集。"));
            return;
        }
        for (final ItemStack output : outputs) {
            final Map<Integer, ItemStack> overflow = player.getInventory().addItem(output);
            for (final ItemStack remain : overflow.values()) {
                block.getWorld().dropItemNaturally(player.getLocation().add(0.0, 0.5, 0.0), remain);
            }
            final String outputId = this.itemFactory.getTechItemId(output);
            if (outputId != null) {
                this.unlockPlanetItem(player, outputId);
            }
        }
        this.plugin.getPlayerProgressService().incrementStat(player.getUniqueId(), "planetary_samples_collected", outputs.stream().mapToInt(ItemStack::getAmount).sum());
        // 教學鏈：星球採集
        if (this.plugin.getTutorialChainService() != null) {
            this.plugin.getTutorialChainService().onPlanetHarvest(player);
        }
        // 迷宮採集任務推進
        if (LABYRINTH_WORLD.equals(block.getWorld().getName())) {
            this.plugin.getMazeService().onCollect(player);
        }
        this.hideHarvestNode(player, block);
        player.spawnParticle(Particle.PORTAL, block.getLocation().add(0.5, 0.7, 0.5), 20, 0.2, 0.22, 0.2, 0.03);
        player.spawnParticle(Particle.END_ROD, block.getLocation().add(0.5, 0.9, 0.5), 8, 0.12, 0.16, 0.12, 0.0);
        player.spawnParticle(Particle.WAX_ON, block.getLocation().add(0.5, 0.8, 0.5), 6, 0.1, 0.14, 0.1, 0.01);
        player.playSound(block.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.45f, 0.9f);
        player.playSound(block.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.3f, 1.45f);
        player.sendActionBar(this.itemFactory.success("已採集「" + this.itemFactory.displayNameForId(profile.dropItemId()) + "」。"));
    }

    private int harvestDurationTicks(final PlanetHarvestProfile profile) {
        int ticks = profile.seedItemId() != null && !profile.seedItemId().isBlank() ? 32 : 22;
        if (profile.dropAmount() > 1) {
            ticks += 4;
        }
        if (profile.animated()) {
            ticks += 6;
        }
        return ticks;
    }

    private double pendingHarvestProgress(final PendingHarvest pending) {
        if (pending == null) {
            return 0.0D;
        }
        final long duration = Math.max(50L, pending.finishAt() - pending.startedAt());
        return Math.max(0.0D, Math.min(1.0D, (System.currentTimeMillis() - pending.startedAt()) / (double) duration));
    }

    private String harvestProgressBar(final double progress) {
        final double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        final int filled = (int) Math.round(clamped * 10.0D);
        final StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int index = 0; index < 10; index++) {
            builder.append(index < filled ? '■' : '□');
        }
        builder.append("] ").append((int) Math.round(clamped * 100.0D)).append('%');
        return builder.toString();
    }

    private boolean isHarvestSurfaceNode(final Block block) {
        if (block == null) {
            return false;
        }
        final Block above = block.getRelative(0, 1, 0);
        return !block.isLiquid() && (above.isEmpty() || above.isPassable());
    }

    private void updateHarvestNodeVisibility(final Player player,
                                             final Block block,
                                             final PlanetHarvestProfile profile) {
        if (player == null || block == null || profile == null) {
            return;
        }
        if (this.isPlayerHarvestReady(player, block, profile)) {
            this.showHarvestNode(player, block);
        } else {
            this.hideHarvestNode(player, block);
        }
    }

    private void ensureHarvestNodeDisplay(final Block block,
                                          final PlanetHarvestProfile profile) {
        if (block == null || profile == null || !this.isHarvestSurfaceNode(block)) {
            return;
        }
        final LocationKey key = LocationKey.from(block.getLocation());
        final java.util.UUID existingId = this.harvestNodeDisplays.get(key);
        if (existingId != null) {
            final Entity existing = Bukkit.getEntity(existingId);
            if (existing instanceof ItemDisplay && existing.isValid()) {
                return;
            }
            if (existing != null) {
                existing.remove();
            }
            this.harvestNodeDisplays.remove(key);
        }
        final World world = block.getWorld();
        final ItemStack visual = this.buildTechStack(profile.dropItemId(), 1);
        if (world == null || visual == null) {
            return;
        }
        final Location displayLocation = block.getLocation().add(0.5, 1.05, 0.5);
        final ItemDisplay display = world.spawn(displayLocation, ItemDisplay.class, spawned -> {
            spawned.addScoreboardTag(HARVEST_NODE_DISPLAY_TAG);
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            spawned.setViewRange(12.0F);
            spawned.setDisplayWidth(0.55F);
            spawned.setDisplayHeight(0.55F);
            spawned.setShadowRadius(0.1F);
            spawned.setShadowStrength(0.7F);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setInterpolationDelay(0);
            spawned.setInterpolationDuration(2);
            spawned.setTeleportDuration(1);
            spawned.setTransformation(new Transformation(
                    new Vector3f(0.0F, 0.0F, 0.0F),
                    new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F),
                    new Vector3f(0.72F, 0.72F, 0.72F),
                    new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F)
            ));
            spawned.setItemStack(visual);
        });
        this.harvestNodeDisplays.put(key, display.getUniqueId());
    }

    private void showHarvestNode(final Player player,
                                 final Block block) {
        final Entity entity = this.harvestNodeEntity(block);
        if (player != null && entity != null) {
            player.showEntity(this.plugin, entity);
        }
    }

    private void hideHarvestNode(final Player player,
                                 final Block block) {
        final Entity entity = this.harvestNodeEntity(block);
        if (player != null && entity != null) {
            player.hideEntity(this.plugin, entity);
        }
    }

    private Entity harvestNodeEntity(final Block block) {
        if (block == null) {
            return null;
        }
        final java.util.UUID entityId = this.harvestNodeDisplays.get(LocationKey.from(block.getLocation()));
        if (entityId == null) {
            return null;
        }
        final Entity entity = Bukkit.getEntity(entityId);
        if (entity == null || !entity.isValid()) {
            this.harvestNodeDisplays.remove(LocationKey.from(block.getLocation()));
            return null;
        }
        return entity;
    }

    private void cleanupHarvestNodeDisplays() {
        for (final Map.Entry<LocationKey, java.util.UUID> entry : new ArrayList<>(this.harvestNodeDisplays.entrySet())) {
            final LocationKey key = entry.getKey();
            final java.util.UUID entityId = entry.getValue();
            final Entity entity = Bukkit.getEntity(entityId);
            if (entity == null || !entity.isValid()) {
                this.harvestNodeDisplays.remove(key, entityId);
                continue;
            }
            final World world = Bukkit.getWorld(key.worldName());
            if (world == null) {
                this.removeHarvestNodeDisplay(key, entityId);
                continue;
            }
            final Location anchor = new Location(world, key.x(), key.y(), key.z());
            this.scheduler.runRegion(anchor, task -> this.validateHarvestNodeDisplay(key, entityId, anchor));
        }
    }

    private void validateHarvestNodeDisplay(final LocationKey key,
                                            final java.util.UUID entityId,
                                            final Location anchor) {
        final java.util.UUID currentId = this.harvestNodeDisplays.get(key);
        if (currentId == null || !currentId.equals(entityId)) {
            return;
        }
        final Entity entity = Bukkit.getEntity(entityId);
        if (entity == null || !entity.isValid()) {
            this.harvestNodeDisplays.remove(key, entityId);
            return;
        }
        final World world = anchor.getWorld();
        if (world == null) {
            this.removeHarvestNodeDisplay(key, entityId);
            return;
        }
        if (entity.getTicksLived() > 40 && entity.getTrackedBy().isEmpty()) {
            this.removeHarvestNodeDisplay(key, entityId);
            return;
        }
        final Block block = world.getBlockAt(key.x(), key.y(), key.z());
        final PlanetDefinition definition = this.planetByWorld(world);
        if (definition == null || this.harvestProfileFor(definition, block.getType(), false) == null || !this.isHarvestSurfaceNode(block)) {
            this.removeHarvestNodeDisplay(key, entityId);
        }
    }

    private void removeHarvestNodeDisplay(final LocationKey key,
                                          final java.util.UUID entityId) {
        if (key == null || entityId == null) {
            return;
        }
        final java.util.UUID currentId = this.harvestNodeDisplays.get(key);
        if (currentId == null || !currentId.equals(entityId)) {
            return;
        }
        // 關服期間 Folia RegionizedWorldData 已進入關閉狀態，呼叫 entity.remove() 會 NPE；
        // 關服時直接移除追蹤即可（伺服器關閉後實體本來就會被丟棄）
        if (this.plugin.isEnabled()) {
            final Entity entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid()) {
                this.scheduler.runEntity(entity, entity::remove);
            }
        }
        this.harvestNodeDisplays.remove(key, entityId);
    }

    private List<ItemStack> buildRewardStacks(final List<String> rewardIds) {
        final List<ItemStack> rewards = new ArrayList<>();
        for (final String rewardId : rewardIds) {
            if (this.registry.getItem(rewardId) == null) {
                continue;
            }
            rewards.add(this.itemFactory.buildTechItem(this.registry.getItem(rewardId)));
        }
        return rewards;
    }

    private void unlockPlanetItem(final Player player, final String itemId) {
        if (player == null || itemId == null || itemId.isBlank()) {
            return;
        }
        this.plugin.getPlayerProgressService().unlockItem(player.getUniqueId(), itemId);
        this.plugin.getPlayerProgressService().unlockByRequirement(player.getUniqueId(), "item:" + itemId);
    }

    // ── 遺跡挑戰系統 ──────────────────────────────────────────

    private void startRuinChallenge(final Player player, final PlanetDefinition definition, final Location coreLoc) {
        final RuinChallenge challenge = new RuinChallenge(player.getUniqueId(), definition, coreLoc.clone());
        this.activeRuinChallenges.put(player.getUniqueId(), challenge);

        final World world = coreLoc.getWorld();
        world.playSound(coreLoc, Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, 1.0f, 0.7f);
        world.playSound(coreLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.6f);
        player.sendMessage(this.itemFactory.warning("⚔ 遺跡核心已覺醒！準備迎戰…"));

        this.scheduler.runRegionDelayed(coreLoc, task -> this.spawnRuinWave(challenge), RUIN_WAVE_DELAY_TICKS);
    }

    private void spawnRuinWave(final RuinChallenge challenge) {
        if (challenge.failed || challenge.completed) {
            challenge.waveAdvancing.set(false);
            return;
        }
        final Player player = Bukkit.getPlayer(challenge.playerId);
        if (player == null || !player.isOnline()) {
            challenge.waveAdvancing.set(false);
            this.cleanupRuinChallenge(challenge, false);
            return;
        }
        final int waveIndex = challenge.currentWave;
        if (waveIndex >= RUIN_WAVE_COUNTS.length) {
            challenge.waveAdvancing.set(false);
            this.completeRuinChallenge(challenge, player);
            return;
        }
        final int mobCount = RUIN_WAVE_COUNTS[waveIndex];
        final World world = challenge.coreLocation.getWorld();
        if (world == null) {
            challenge.waveAdvancing.set(false);
            return;
        }

        challenge.waveStartTick = world.getFullTime();
        challenge.waveGeneration++;
        // 進入生怪階段，釋放推進鎖：這一波真正開始了
        challenge.waveAdvancing.set(false);

        final int waveNum = waveIndex + 1;
        player.sendMessage(this.itemFactory.secondary("▸ 第 " + waveNum + "/" + RUIN_WAVE_COUNTS.length + " 波 — " + mobCount + " 隻敵人逼近！"));
        world.playSound(challenge.coreLocation, Sound.BLOCK_TRIAL_SPAWNER_SPAWN_MOB, 1.0f, 0.8f + waveIndex * 0.15f);

        // BossBar 倒數
        this.showRuinBossBar(challenge, player, waveNum, mobCount);
        this.tickRuinBossBar(challenge);

        final Particle.DustOptions dust = this.planetSpawnDust(challenge.definition.id());
        world.spawnParticle(Particle.DUST, challenge.coreLocation.clone().add(0.5, 2.0, 0.5), 40, 1.5, 1.0, 1.5, 0.02, dust);

        for (int i = 0; i < mobCount; i++) {
            final double angle = Math.toRadians((360.0 / mobCount) * i + waveIndex * 30);
            final double radius = 6.0 + waveIndex * 2.0;
            final int sx = challenge.coreLocation.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            final int sz = challenge.coreLocation.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            final Location regionAnchor = new Location(world, sx + 0.5, 64, sz + 0.5);

            this.scheduler.runRegion(regionAnchor, task -> {
                if (challenge.failed || challenge.completed) return;
                final int sy = world.getHighestBlockYAt(sx, sz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                final Location spawnLoc = new Location(world, sx + 0.5, sy, sz + 0.5);
                // 遺跡挑戰使用地面專用怪池（排除會飛的 BLAZE / PHANTOM，避免玩家無法攻擊）
                final org.bukkit.entity.EntityType mobType = this.ruinChallengeMobTypeFor(challenge.definition);
                if (mobType == null) return;

                final Entity spawned = world.spawnEntity(spawnLoc, mobType);
                if (!(spawned instanceof LivingEntity living)) {
                    spawned.remove();
                    return;
                }
                living.addScoreboardTag(RUIN_CHALLENGE_MOB_TAG);
                living.addScoreboardTag(RUIN_CHALLENGE_MOB_TAG + ":" + challenge.playerId);
                living.setRemoveWhenFarAway(false);
                living.setPersistent(true);
                living.setGlowing(true);

                // 波數越高越強
                final double hpMultiplier = 1.0 + waveIndex * 0.4;
                this.adjustAttribute(living, Attribute.MAX_HEALTH, hpMultiplier, 0.0, 6.0);
                final var maxHp = living.getAttribute(Attribute.MAX_HEALTH);
                if (maxHp != null) living.setHealth(Math.max(1.0, maxHp.getValue()));
                this.adjustAttribute(living, Attribute.ATTACK_DAMAGE, 1.0, waveIndex * 1.0, 2.0);

                // 最後一波的怪物有精英特效
                if (waveIndex == RUIN_WAVE_COUNTS.length - 1) {
                    this.tryEmpowerPlanetMob(living);
                }

                if (living instanceof Mob mob) {
                    mob.setTarget(player);
                }

                challenge.aliveMobs.add(spawned.getUniqueId());

                world.spawnParticle(Particle.DUST, spawnLoc.clone().add(0, 0.5, 0), 15, 0.4, 0.6, 0.4, 0.01, dust);
                world.spawnParticle(Particle.SMOKE, spawnLoc, 8, 0.3, 0.1, 0.3, 0.02);
            });
        }

        // 超時檢查（記錄當前波世代，避免舊波超時誤殺新波）
        final int gen = challenge.waveGeneration;
        this.scheduler.runGlobalDelayed(task -> this.checkRuinWaveTimeout(challenge, gen), RUIN_WAVE_TIMEOUT_TICKS);
    }

    private void showRuinBossBar(final RuinChallenge challenge, final Player player, final int waveNum, final int mobCount) {
        this.hideRuinBossBar(challenge);
        final int totalSeconds = (int) (RUIN_WAVE_TIMEOUT_TICKS / 20L);
        final Component title = Component.text("⚔ 第 " + waveNum + "/" + RUIN_WAVE_COUNTS.length + " 波 — 剩餘 " + mobCount + " 隻 — " + totalSeconds + " 秒", NamedTextColor.GOLD);
        challenge.bossBar = BossBar.bossBar(title, 1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        player.showBossBar(challenge.bossBar);
    }

    private void tickRuinBossBar(final RuinChallenge challenge) {
        this.scheduler.runGlobalDelayed(task -> {
            if (challenge.failed || challenge.completed || challenge.bossBar == null) {
                return;
            }
            final World world = challenge.coreLocation.getWorld();
            if (world == null) {
                return;
            }
            // 週期性清除已死亡/失效的怪物（補捕苦力怕自爆等事件遺漏）
            challenge.aliveMobs.removeIf(mobId -> {
                final Entity mob = Bukkit.getEntity(mobId);
                return mob == null || mob.isDead() || !mob.isValid();
            });
            final int alive = challenge.aliveMobs.size();
            if (alive == 0 && !challenge.failed && !challenge.completed) {
                // 安全網：若 onRuinChallengeMobDeath 已觸發推進，則略過避免重複生怪
                if (!challenge.waveAdvancing.compareAndSet(false, true)) {
                    this.tickRuinBossBar(challenge);
                    return;
                }
                // 所有怪物已死 — 推進波數
                challenge.currentWave++;
                if (challenge.currentWave >= RUIN_WAVE_COUNTS.length) {
                    final Player player = Bukkit.getPlayer(challenge.playerId);
                    if (player != null) {
                        this.completeRuinChallenge(challenge, player);
                    }
                    challenge.waveAdvancing.set(false);
                } else {
                    final Player player = Bukkit.getPlayer(challenge.playerId);
                    if (player != null) {
                        world.playSound(challenge.coreLocation, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.4f);
                        player.sendMessage(this.itemFactory.success("✓ 波數清除！下一波即將到來…"));
                    }
                    this.scheduler.runRegionDelayed(challenge.coreLocation,
                            t -> this.spawnRuinWave(challenge), RUIN_WAVE_DELAY_TICKS);
                }
                return;
            }
            final long elapsed = world.getFullTime() - challenge.waveStartTick;
            final float progress = Math.max(0.0f, Math.min(1.0f, 1.0f - (float) elapsed / RUIN_WAVE_TIMEOUT_TICKS));
            final int remaining = Math.max(0, (int) ((RUIN_WAVE_TIMEOUT_TICKS - elapsed) / 20L));
            final int waveNum = challenge.currentWave + 1;
            challenge.bossBar.progress(progress);
            challenge.bossBar.name(Component.text("⚔ 第 " + waveNum + "/" + RUIN_WAVE_COUNTS.length + " 波 — 剩餘 " + alive + " 隻 — " + remaining + " 秒", progress > 0.3f ? NamedTextColor.GOLD : NamedTextColor.RED));
            if (progress > 0.5f) {
                challenge.bossBar.color(BossBar.Color.YELLOW);
            } else if (progress > 0.2f) {
                challenge.bossBar.color(BossBar.Color.RED);
            } else {
                challenge.bossBar.color(BossBar.Color.RED);
            }
            if (progress > 0.0f && alive > 0) {
                this.tickRuinBossBar(challenge);
            }
        }, 20L);
    }

    private void hideRuinBossBar(final RuinChallenge challenge) {
        if (challenge.bossBar == null) {
            return;
        }
        final Player player = Bukkit.getPlayer(challenge.playerId);
        if (player != null) {
            player.hideBossBar(challenge.bossBar);
        }
        challenge.bossBar = null;
    }

    private void checkRuinWaveTimeout(final RuinChallenge challenge, final int generation) {
        if (challenge.failed || challenge.completed) {
            return;
        }
        // 若波世代不符，表示此超時屬於舊波，已被新波取代，忽略
        if (challenge.waveGeneration != generation) {
            return;
        }
        if (!challenge.aliveMobs.isEmpty()) {
            final Player player = Bukkit.getPlayer(challenge.playerId);
            this.hideRuinBossBar(challenge);
            if (player != null) {
                player.sendMessage(this.itemFactory.warning("✘ 遺跡挑戰超時，核心陷入休眠…"));
            }
            this.cleanupRuinChallenge(challenge, true);
        }
    }

    public void onRuinChallengeMobDeath(final LivingEntity entity) {
        final java.util.UUID entityId = entity.getUniqueId();
        for (final RuinChallenge challenge : this.activeRuinChallenges.values()) {
            if (!challenge.aliveMobs.remove(entityId)) {
                continue;
            }
            if (challenge.failed || challenge.completed) {
                return;
            }
            if (!challenge.aliveMobs.isEmpty()) {
                final Player player = Bukkit.getPlayer(challenge.playerId);
                if (player != null) {
                    player.sendActionBar(this.itemFactory.secondary("剩餘敵人：" + challenge.aliveMobs.size()));
                }
                return;
            }
            // 目前波數清完 — CAS 搶推進權，已被推進則略過
            if (!challenge.waveAdvancing.compareAndSet(false, true)) {
                return;
            }
            challenge.currentWave++;
            if (challenge.currentWave >= RUIN_WAVE_COUNTS.length) {
                final Player player = Bukkit.getPlayer(challenge.playerId);
                if (player != null) {
                    this.completeRuinChallenge(challenge, player);
                }
                challenge.waveAdvancing.set(false);
                return;
            }
            // 下一波
            final Player player = Bukkit.getPlayer(challenge.playerId);
            if (player != null) {
                final World world = challenge.coreLocation.getWorld();
                if (world != null) {
                    world.playSound(challenge.coreLocation, Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.4f);
                    player.sendMessage(this.itemFactory.success("✓ 波數清除！下一波即將到來…"));
                }
            }
            this.scheduler.runRegionDelayed(challenge.coreLocation, task -> this.spawnRuinWave(challenge), RUIN_WAVE_DELAY_TICKS);
            return;
        }
    }

    private void completeRuinChallenge(final RuinChallenge challenge, final Player player) {
        challenge.completed = true;
        this.hideRuinBossBar(challenge);
        this.activeRuinChallenges.remove(challenge.playerId);

        final PlanetDefinition definition = challenge.definition;
        final World world = challenge.coreLocation.getWorld();

        // 獎勵
        final List<ItemStack> rewards = this.buildRewardStacks(definition.ruinRewardIds());
        final StringJoiner rewardNames = new StringJoiner("、");
        for (final ItemStack reward : rewards) {
            final Map<Integer, ItemStack> overflow = player.getInventory().addItem(reward);
            for (final ItemStack leftover : overflow.values()) {
                world.dropItemNaturally(challenge.coreLocation.clone().add(0.5, 1.0, 0.5), leftover);
            }
            final String rewardId = this.itemFactory.getTechItemId(reward);
            if (rewardId != null) {
                this.unlockPlanetItem(player, rewardId);
                rewardNames.add(this.itemFactory.displayNameForId(rewardId));
            }
        }

        // 額外獎勵 — 精英掉落
        final PlanetEliteProfile eliteProfile = this.eliteProfileFor(definition);
        if (eliteProfile != null && this.registry.getItem(eliteProfile.combatSampleId()) != null) {
            final ItemStack bonus = this.itemFactory.buildTechItem(this.registry.getItem(eliteProfile.combatSampleId()));
            final Map<Integer, ItemStack> overflow = player.getInventory().addItem(bonus);
            for (final ItemStack leftover : overflow.values()) {
                world.dropItemNaturally(challenge.coreLocation.clone().add(0.5, 1.0, 0.5), leftover);
            }
            this.unlockPlanetItem(player, eliteProfile.combatSampleId());
            rewardNames.add(this.itemFactory.displayNameForId(eliteProfile.combatSampleId()));
        }

        this.plugin.getPlayerProgressService().incrementStat(player.getUniqueId(), "planet_ruins_activated", 1);
        this.plugin.getAchievementService().evaluate(player.getUniqueId());

        // 特效
        if (world != null) {
            world.spawnParticle(Particle.END_ROD, challenge.coreLocation.clone().add(0.5, 1.5, 0.5), 60, 1.0, 1.5, 1.0, 0.08);
            world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, challenge.coreLocation.clone().add(0.5, 1.2, 0.5), 30, 1.0, 1.0, 1.0, 0.02);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, 0.5, 1.0, 0.5, 0.3);
            world.playSound(challenge.coreLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            world.playSound(challenge.coreLocation, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
        }

        final String rewardSuffix = rewardNames.length() > 0 ? "，獲得：" + rewardNames : "。";
        player.sendMessage(this.itemFactory.success("★ 遺跡挑戰完成！" + definition.displayName() + " 核心已同步" + rewardSuffix));
    }

    private void cleanupRuinChallenge(final RuinChallenge challenge, final boolean markFailed) {
        if (markFailed) {
            challenge.failed = true;
            // 失敗時移除玩家啟動標記，允許消耗新物品重新挑戰
            final LocationKey ruinKey = LocationKey.from(challenge.coreLocation);
            final Set<LocationKey> playerRuins = this.playerActivatedRuins.get(challenge.playerId);
            if (playerRuins != null) {
                playerRuins.remove(ruinKey);
            }
        }
        this.hideRuinBossBar(challenge);
        this.activeRuinChallenges.remove(challenge.playerId);
        // 移除殘存怪物
        for (final java.util.UUID mobId : challenge.aliveMobs) {
            final Entity entity = Bukkit.getEntity(mobId);
            if (entity != null && entity.isValid()) {
                this.scheduler.runEntity(entity, entity::remove);
            }
        }
        challenge.aliveMobs.clear();
        // 失敗時的音效
        if (markFailed && challenge.coreLocation.getWorld() != null) {
            challenge.coreLocation.getWorld().playSound(challenge.coreLocation, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 0.6f);
        }
    }

    public boolean hasActiveRuinChallenge(final java.util.UUID playerId) {
        return this.activeRuinChallenges.containsKey(playerId);
    }

    private PlanetEliteProfile eliteProfileFor(final PlanetDefinition definition) {
        if (definition == null) {
            return null;
        }
        return switch (definition.id()) {
            case "aurelia" -> new PlanetEliteProfile("輻塵寄生體", "aurelia_parasite_gland", "irradiated_shard", 1.65D, 2.0D, 0.02D);
            case "cryon" -> new PlanetEliteProfile("霜脊潛獵者", "cryon_ice_heart", "cryonite_crystal", 1.75D, 1.0D, 0.015D);
            case "nyx" -> new PlanetEliteProfile("虛響觀測者", "nyx_phase_tissue", "voidglass_fragment", 1.55D, 2.5D, 0.03D);
            case "helion" -> new PlanetEliteProfile("日灼焰獸", "helion_cinder_core", "solarite_shard", 1.9D, 3.0D, 0.02D);
            case "tempest" -> new PlanetEliteProfile("雷殼追獵者", "tempest_capacitor", "stormglass_shard", 1.7D, 2.0D, 0.04D);
            case "labyrinth" -> new PlanetEliteProfile("迷宮守衛者", "guardian_core", "labyrinth_fragment", 1.8D, 2.5D, 0.025D);
            default -> null;
        };
    }

    private int countNearbyPlanetElites(final LivingEntity entity) {
        int count = 0;
        for (final Entity nearby : entity.getNearbyEntities(18.0D, 8.0D, 18.0D)) {
            if (nearby instanceof LivingEntity living && living.getScoreboardTags().contains(PLANET_ELITE_TAG)) {
                count++;
            }
        }
        return count;
    }

    private void adjustAttribute(final LivingEntity entity,
                                 final Attribute attribute,
                                 final double multiplier,
                                 final double additive,
                                 final double minimumValue) {
        final var instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        final double updated = Math.max(minimumValue, instance.getBaseValue() * Math.max(0.1D, multiplier) + additive);
        instance.setBaseValue(updated);
    }

    private void consumeHeldItem(final Player player, final ItemStack heldItem) {
        if (player.getGameMode() == GameMode.CREATIVE || heldItem == null) {
            return;
        }
        heldItem.setAmount(Math.max(0, heldItem.getAmount() - 1));
        if (heldItem.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(null);
        }
    }

    private void load() {
        final Map<String, Object> data = this.storageBackend.loadPlanetState();
        if (data == null) {
            return;
        }
        if (data.get("generated-worlds") instanceof List<?> list) {
            list.forEach(v -> { if (v instanceof String s) this.generatedPlanetWorlds.add(s.toLowerCase(Locale.ROOT)); });
        }
        if (data.get("terrain-chunks") instanceof List<?> list) {
            list.forEach(v -> { if (v instanceof String s) this.processedDatapackChunks.add(s.toLowerCase(Locale.ROOT)); });
        }
        if (data.get("decorated-chunks") instanceof List<?> list) {
            list.forEach(v -> { if (v instanceof String s) this.decoratedPlanetChunks.add(s.toLowerCase(Locale.ROOT)); });
        }
        final Map<String, Map<String, Object>> ruinEntries = this.extractIndexedSection(data, "ruins");
        for (final Map<String, Object> ruin : ruinEntries.values()) {
            final String world = ruin.get("world") instanceof String s ? s : null;
            if (world == null || world.isBlank()) continue;
            this.activatedRuins.add(new LocationKey(world,
                    ruin.get("x") instanceof Number n ? n.intValue() : 0,
                    ruin.get("y") instanceof Number n ? n.intValue() : 0,
                    ruin.get("z") instanceof Number n ? n.intValue() : 0));
        }
        final Map<String, Map<String, Object>> playerRuinGroups = this.extractIndexedSection(data, "player-ruins");
        for (final Map.Entry<String, Map<String, Object>> groupEntry : playerRuinGroups.entrySet()) {
            final String groupIdx = groupEntry.getKey();
            final String playerId = data.get("player-ruins." + groupIdx + ".player") instanceof String s ? s : null;
            if (playerId == null || playerId.isBlank()) continue;
            final java.util.UUID uuid;
            try {
                uuid = java.util.UUID.fromString(playerId);
            } catch (final IllegalArgumentException ignored) {
                continue;
            }
            final Set<LocationKey> loaded = this.playerActivatedRuins.computeIfAbsent(uuid, ignored -> ConcurrentHashMap.newKeySet());
            final Map<String, Map<String, Object>> entries = this.extractIndexedSection(data, "player-ruins." + groupIdx + ".entries");
            for (final Map<String, Object> entry : entries.values()) {
                final String world = entry.get("world") instanceof String s ? s : null;
                if (world == null || world.isBlank()) continue;
                loaded.add(new LocationKey(world,
                        entry.get("x") instanceof Number n ? n.intValue() : 0,
                        entry.get("y") instanceof Number n ? n.intValue() : 0,
                        entry.get("z") instanceof Number n ? n.intValue() : 0));
            }
        }
        final Map<String, Map<String, Object>> gateEntries = this.extractIndexedSection(data, "gates");
        for (final Map<String, Object> gate : gateEntries.values()) {
            final String world = gate.get("world") instanceof String s ? s : null;
            final String target = this.normalizeGateDestinationId(gate.get("target") instanceof String s ? s : "aurelia");
            if (world == null || world.isBlank()) continue;
            this.gateDestinations.put(new LocationKey(world,
                    gate.get("x") instanceof Number n ? n.intValue() : 0,
                    gate.get("y") instanceof Number n ? n.intValue() : 0,
                    gate.get("z") instanceof Number n ? n.intValue() : 0),
                    target.toLowerCase(Locale.ROOT));
        }
        final Map<String, Map<String, Object>> fruitEntries = this.extractIndexedSection(data, "fruit-regrowths");
        for (final Map<String, Object> fruit : fruitEntries.values()) {
            final String world = fruit.get("world") instanceof String s ? s : null;
            final String itemId = fruit.get("item-id") instanceof String s ? s : null;
            if (world == null || world.isBlank() || itemId == null || itemId.isBlank()) continue;
            this.fruitNodeRegrowths.put(new LocationKey(world,
                    fruit.get("x") instanceof Number n ? n.intValue() : 0,
                    fruit.get("y") instanceof Number n ? n.intValue() : 0,
                    fruit.get("z") instanceof Number n ? n.intValue() : 0),
                    new FruitNodeRegrowth(itemId, fruit.get("ready-at") instanceof Number n ? n.longValue() : System.currentTimeMillis()));
        }
    }

    private Map<String, Map<String, Object>> extractIndexedSection(final Map<String, Object> data, final String prefix) {
        final Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        final String dotPrefix = prefix + ".";
        for (final Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getKey().startsWith(dotPrefix)) {
                final String remainder = entry.getKey().substring(dotPrefix.length());
                final int dot = remainder.indexOf('.');
                if (dot > 0) {
                    final String idx = remainder.substring(0, dot);
                    final String field = remainder.substring(dot + 1);
                    result.computeIfAbsent(idx, k -> new LinkedHashMap<>()).put(field, entry.getValue());
                }
            }
        }
        return result;
    }

    private boolean hasPlayerActivatedRuin(final java.util.UUID playerId, final LocationKey ruinKey) {
        if (playerId == null || ruinKey == null) {
            return false;
        }
        return this.playerActivatedRuins.getOrDefault(playerId, Set.of()).contains(ruinKey);
    }

    private void markPlayerActivatedRuin(final java.util.UUID playerId, final LocationKey ruinKey) {
        if (playerId == null || ruinKey == null) {
            return;
        }
        this.playerActivatedRuins.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(ruinKey);
    }

    private boolean isStandingOnGate(final Player player, final Block gateBlock) {
        final Location location = player.getLocation();
        return location.getWorld() != null
                && gateBlock.getWorld().equals(location.getWorld())
                && location.getBlockX() == gateBlock.getX()
                && Math.abs(location.getBlockY() - gateBlock.getY()) <= 1
                && location.getBlockZ() == gateBlock.getZ();
    }

    private void openPlanetaryGateMenu(final Player player, final Block gateBlock) {
        final Inventory inventory = Bukkit.createInventory(null, 54,
                Component.text(PLANETARY_GATE_MENU_TITLE, NamedTextColor.WHITE).font(PLANETARY_GATE_MENU_FONT));
        this.populatePlanetaryGateMenu(inventory, player);
        final LocationKey gateKey = LocationKey.from(gateBlock.getLocation());
        this.openPlanetaryGateMenus.put(player.getUniqueId(), gateKey);
        player.openInventory(inventory);
    }

    private void populatePlanetaryGateMenu(final Inventory inventory, final Player viewer) {
        if (inventory == null || viewer == null) {
            return;
        }
        for (final PlanetaryGateButtonLayout layout : PLANETARY_GATE_LAYOUTS) {
            final int centerRow = layout.startRow() + 1;
            final int centerColumn = layout.startColumn() + 1;
            final int slot = centerRow * 9 + centerColumn;
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, this.buildPlanetaryGateHoverItem(layout, viewer));
            }
        }
    }

    private ItemStack buildPlanetaryGateHoverItem(final PlanetaryGateButtonLayout layout,
                                                  final Player viewer) {
        final TravelDestination destination = this.resolveTravelDestination(layout.targetId(), false);
        final String displayName = destination == null ? this.planetDisplayName(layout.targetId()) : destination.displayName();
        final List<Component> lore = new ArrayList<>();
        if (destination == null || destination.world() == null) {
            lore.add(Component.text("目的地尚未就緒", NamedTextColor.GRAY));
        } else if (viewer.getWorld().equals(destination.world())) {
            lore.add(Component.text("你目前已在這個星球 / 世界", NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("點擊此區域即可傳送", NamedTextColor.GRAY));
        }
        return this.itemFactory.buildGuiPane(
                layout.key(),
                Material.PAPER,
                Component.text(displayName, NamedTextColor.WHITE),
                lore,
                false
        );
    }

    private PlanetaryGateButtonLayout planetaryGateLayoutAt(final int rawSlot) {
        for (final PlanetaryGateButtonLayout layout : PLANETARY_GATE_LAYOUTS) {
            for (int row = layout.startRow(); row < layout.startRow() + 3; row++) {
                for (int column = layout.startColumn(); column < layout.startColumn() + 3; column++) {
                    if (rawSlot == row * 9 + column) {
                        return layout;
                    }
                }
            }
        }
        return null;
    }

    private void tryStartPlanetaryGateTravel(final Player player,
                                             final Block gateBlock,
                                             final String targetId) {
        if (player == null || gateBlock == null) {
            return;
        }
        if (this.travelingPlayers.contains(player.getUniqueId())) {
            player.sendMessage(this.itemFactory.warning("星門傳送程序仍在進行中。"));
            player.closeInventory();
            return;
        }
        final PlacedMachine gateMachine = this.plugin.getMachineService().placedMachineAt(gateBlock);
        if (gateMachine == null || !"planetary_gate".equalsIgnoreCase(gateMachine.machineId())) {
            player.sendMessage(this.itemFactory.warning("這座星門已離線，請重新放置或檢查結構。"));
            player.closeInventory();
            return;
        }
        if (!gateMachine.enabled()) {
            player.sendMessage(this.itemFactory.warning("星門尚未啟動，請先開機。"));
            player.closeInventory();
            return;
        }
        if (gateMachine.enabledDurationMs() < PLANETARY_GATE_WARMUP_MS) {
            final long remaining = (PLANETARY_GATE_WARMUP_MS - gateMachine.enabledDurationMs()) / 1000L + 1;
            player.sendMessage(this.itemFactory.warning("星門正在暖機中，還需約 " + remaining + " 秒…"));
            gateBlock.getWorld().playSound(gateBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.72f);
            return;
        }
        if (!this.isStandingOnGate(player, gateBlock)) {
            player.sendMessage(this.itemFactory.warning("請先站到星門平台中央，再點選目的地。"));
            gateBlock.getWorld().playSound(gateBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.72f);
            return;
        }
        final TravelDestination destination = this.resolveTravelDestination(targetId, true);
        if (destination == null || destination.world() == null) {
            player.sendMessage(this.itemFactory.warning(this.unavailableGateDestinationReason(targetId)));
            gateBlock.getWorld().playSound(gateBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.7f);
            return;
        }
        if (player.getWorld().equals(destination.world())) {
            player.sendMessage(this.itemFactory.warning("你目前已在「" + destination.displayName() + "」，無法重複傳送到同一地點。"));
            gateBlock.getWorld().playSound(gateBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.72f);
            return;
        }
        if (gateMachine.storedEnergy() < PLANETARY_GATE_TRAVEL_COST) {
            player.sendMessage(this.itemFactory.warning("星門能源不足，需要至少 " + PLANETARY_GATE_TRAVEL_COST + " EU 才能啟動。"));
            gateBlock.getWorld().playSound(gateBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.7f);
            return;
        }
        final int fuelCost = this.calculateShipFuelCost(player);
        final int availableFuel = this.countFuelInInventory(player);
        if (availableFuel < fuelCost) {
            final String fuelName = this.itemFactory.displayNameForId(SHIP_FUEL_ITEM_ID);
            player.sendMessage(this.itemFactory.warning(
                    "燃料不足！需要 " + fuelCost + " 個「" + fuelName + "」，目前持有 " + availableFuel + " 個。"));
            player.sendMessage(this.itemFactory.secondary(
                    "  背包物品越多，所需燃料越多（基礎 " + SHIP_BASE_FUEL_COST
                    + " + 每 " + SHIP_FUEL_WEIGHT_DIVISOR + " 個物品額外 +1）"));
            gateBlock.getWorld().playSound(gateBlock.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.7f);
            return;
        }
        player.closeInventory();
        this.startPlanetaryGateCountdown(player, gateBlock, destination, fuelCost);
    }

    private void startPlanetaryGateCountdown(final Player player,
                                             final Block gateBlock,
                                             final TravelDestination destination,
                                             final int fuelCost) {
        final java.util.UUID uuid = player.getUniqueId();
        this.travelingPlayers.add(uuid);
        final int[] remaining = {SHIP_COUNTDOWN_SECONDS};
        final Location gateLoc = gateBlock.getLocation().clone();
        final String fuelName = this.itemFactory.displayNameForId(SHIP_FUEL_ITEM_ID);
        player.sendMessage(this.itemFactory.secondary(
                "燃料消耗：" + fuelCost + " 個「" + fuelName + "」 ｜ 能源消耗：" + PLANETARY_GATE_TRAVEL_COST + " EU"));
        this.showCountdownTick(player, gateLoc, remaining[0]);
        this.scheduler.runEntityTimer(player, task -> {
            remaining[0]--;
            if (!player.isOnline() || !this.travelingPlayers.contains(uuid)) {
                task.cancel();
                this.travelingPlayers.remove(uuid);
                return;
            }
            if (!this.isStandingOnGate(player, gateBlock)) {
                task.cancel();
                this.travelingPlayers.remove(uuid);
                player.sendMessage(this.itemFactory.warning("你離開了星門平台，發射程序已取消。"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
                return;
            }
            if (remaining[0] > 0) {
                this.showCountdownTick(player, gateLoc, remaining[0]);
                return;
            }
            task.cancel();
            final PlacedMachine gateMachine = this.plugin.getMachineService().placedMachineAt(gateBlock);
            if (gateMachine == null || gateMachine.storedEnergy() < PLANETARY_GATE_TRAVEL_COST) {
                this.travelingPlayers.remove(uuid);
                player.sendMessage(this.itemFactory.warning("星門能源不足，發射程序已中止。"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
                return;
            }
            if (this.countFuelInInventory(player) < fuelCost) {
                this.travelingPlayers.remove(uuid);
                player.sendMessage(this.itemFactory.warning("燃料不足，發射程序已中止。"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
                return;
            }
            gateMachine.consumeEnergy(PLANETARY_GATE_TRAVEL_COST);
            this.consumeShipFuel(player, fuelCost);
            this.gateDestinations.put(LocationKey.from(gateLoc), destination.id());
            // 立即顯示發射字幕（非打字機），避免被後續動畫 title 打斷。
            this.plugin.getTitleMsgService().cancel(player.getUniqueId());
            player.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text("▶ 發射", NamedTextColor.GREEN, TextDecoration.BOLD),
                    this.itemFactory.secondary("目的地：" + destination.displayName()),
                    net.kyori.adventure.title.Title.Times.times(
                            java.time.Duration.ZERO,
                            java.time.Duration.ofMillis(1400L),
                            java.time.Duration.ZERO)));
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.9f, 0.8f);
            gateLoc.getWorld().spawnParticle(Particle.END_ROD,
                    gateLoc.clone().add(0.5, 2.0, 0.5), 40, 0.6, 0.4, 0.6, 0.08);
            gateLoc.getWorld().spawnParticle(Particle.FLAME,
                    gateLoc.clone().add(0.5, 1.0, 0.5), 25, 0.5, 0.2, 0.5, 0.06);
            this.startTravelAnimation(player, gateLoc, destination);
        }, 20L, 20L);
    }

    private void showCountdownTick(final Player player, final Location gateLoc, final int seconds) {
        final NamedTextColor color = seconds <= 2
                ? NamedTextColor.RED
                : seconds <= 3 ? NamedTextColor.GOLD : NamedTextColor.GREEN;
        // 倒數直接顯示（不用打字機），避免下一次 tick 在前一次打字完成前覆蓋。
        this.plugin.getTitleMsgService().cancel(player.getUniqueId());
        player.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("▶ " + seconds, color, TextDecoration.BOLD),
                this.itemFactory.secondary("星門啟動倒數"),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ZERO,
                        java.time.Duration.ofMillis(1100L),
                        java.time.Duration.ZERO)));
        final float pitch = seconds <= 2 ? 1.6f : 1.0f;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, pitch);
        if (gateLoc.getWorld() != null) {
            gateLoc.getWorld().spawnParticle(Particle.PORTAL,
                    gateLoc.clone().add(0.5, 1.5, 0.5), 30, 0.8, 0.5, 0.8, 0.5);
            gateLoc.getWorld().spawnParticle(Particle.END_ROD,
                    gateLoc.clone().add(0.5, 1.5, 0.5), 5, 0.3, 0.3, 0.3, 0.02);
        }
    }

    private int calculateShipFuelCost(final Player player) {
        int totalItems = 0;
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                final String techId = this.itemFactory.getTechItemId(item);
                if (!SHIP_FUEL_ITEM_ID.equals(techId)) {
                    totalItems += item.getAmount();
                }
            }
        }
        return SHIP_BASE_FUEL_COST + (totalItems / SHIP_FUEL_WEIGHT_DIVISOR);
    }

    private int countFuelInInventory(final Player player) {
        int count = 0;
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item != null && SHIP_FUEL_ITEM_ID.equals(this.itemFactory.getTechItemId(item))) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void consumeShipFuel(final Player player, int amount) {
        for (int i = 0; i < player.getInventory().getSize() && amount > 0; i++) {
            final ItemStack item = player.getInventory().getItem(i);
            if (item != null && SHIP_FUEL_ITEM_ID.equals(this.itemFactory.getTechItemId(item))) {
                final int take = Math.min(amount, item.getAmount());
                item.setAmount(item.getAmount() - take);
                amount -= take;
            }
        }
    }

    private TravelDestination resolveTravelDestination(final String targetId,
                                                       final boolean ensureWorld) {
        final String normalizedTarget = this.normalizeGateDestinationId(targetId);
        if (EARTH_DESTINATION_ID.equals(normalizedTarget)) {
            return new TravelDestination(EARTH_DESTINATION_ID, "地球", this.resolveEarthWorld(), null);
        }
        final PlanetDefinition definition = this.planets.get(normalizedTarget);
        if (definition == null) {
            return null;
        }
        final World world = ensureWorld ? this.ensurePlanetWorld(definition) : this.resolveExistingPlanetWorld(definition);
        return new TravelDestination(definition.id(), definition.displayName(), world, definition);
    }

    private String normalizeGateDestinationId(final String targetId) {
        if (targetId == null || targetId.isBlank()) {
            return "aurelia";
        }
        final String normalized = targetId.toLowerCase(Locale.ROOT).trim();
        if (EARTH_DESTINATION_ID.equals(normalized) || EARTH_WORLD_NAME.equals(normalized)) {
            return EARTH_DESTINATION_ID;
        }
        return this.planets.containsKey(normalized) ? normalized : "aurelia";
    }

    private String unavailableGateDestinationReason(final String targetId) {
        final String normalizedTarget = this.normalizeGateDestinationId(targetId);
        if (EARTH_DESTINATION_ID.equals(normalizedTarget)) {
            return "主世界 world 尚未就緒，無法返回地球。";
        }
        return this.worldCreationUnsupported
                ? "星球世界目前無法動態載入。若你使用 Worlds，請先預載對應世界並完整重啟伺服器。"
                : "目標星球尚未就緒。";
    }

    private void startTravelAnimation(final Player player,
                                      final Location gateLocation,
                                      final TravelDestination destination) {
        final long chargeDuration = 24L;
        final int ascentSteps = 66;
        final long ascentInterval = 1L;
        final long ascentDuration = (ascentSteps - 1L) * ascentInterval;
        final long ascentStartTick = chargeDuration;
        final long jumpTick = ascentStartTick + ascentDuration + 6L;
        final java.util.UUID uuid = player.getUniqueId();
        this.travelingPlayers.add(uuid);
        final TravelPlayerState originalState = new TravelPlayerState(
                player.getGameMode(),
                player.getAllowFlight(),
                player.isFlying(),
                player.getWalkSpeed(),
                player.getFlySpeed()
        );
        this.travelPlayerStates.put(uuid, originalState);
        final Location base = gateLocation.clone().add(0.5, 1.0, 0.5);
        this.scheduler.runEntity(player, () -> {
            player.closeInventory();
            this.cleanupTravelVessel(uuid);
            player.setFlySpeed(0.0f);
            player.setWalkSpeed(0.0f);
            final TravelVessel vessel = this.spawnTravelVessel(base, player.getLocation().getYaw());
            if (vessel != null) {
                this.travelVessels.put(uuid, vessel);
                this.updateTravelVesselPosition(vessel, base, player, 0.0D, 0.0D, 0.0D);
                player.setGameMode(GameMode.SPECTATOR);
                player.setSpectatorTarget(vessel.cameraAnchor());
            } else {
                player.teleportAsync(base);
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, (int) jumpTick + 70, 0, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, (int) jumpTick + 28, 0, false, false, true));
            this.showTravelTitle(player,
                    this.itemFactory.primary("星門充能中"),
                    this.itemFactory.secondary("目的地鎖定：" + destination.displayName()),
                    8L,
                    chargeDuration,
                    12L);
            player.sendActionBar(this.itemFactory.secondary("能源正在灌入星門核心，請保持站位……"));
            final World world = gateLocation.getWorld();
            if (world != null) {
                world.spawnParticle(Particle.PORTAL, base, 54, 0.9, 0.35, 0.9, 0.1);
                world.spawnParticle(Particle.END_ROD, base, 28, 0.55, 0.5, 0.55, 0.02);
                world.spawnParticle(Particle.GLOW, base, 24, 0.6, 0.3, 0.6, 0.02);
                this.spawnTravelGreenFlash(world, base, 0.62D, 20);
                world.spawnParticle(Particle.SMOKE, base.clone().add(0.0D, -0.8D, 0.0D), 18, 0.42, 0.08, 0.42, 0.02);
                world.playSound(gateLocation, Sound.BLOCK_BEACON_ACTIVATE, 0.62f, 0.68f);
                world.playSound(gateLocation, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.42f, 0.8f);
                world.playSound(gateLocation, Sound.BLOCK_CONDUIT_ACTIVATE, 0.38f, 0.72f);
                world.playSound(gateLocation, Sound.ITEM_TRIDENT_THUNDER, 0.18f, 1.6f);
            }
        });
        for (int pulse = 0; pulse < 4; pulse++) {
            final int currentPulse = pulse;
            this.scheduler.runEntityDelayed(player, () -> {
                final World world = gateLocation.getWorld();
                if (world == null) {
                    return;
                }
                final double ring = 0.55D + currentPulse * 0.18D;
                world.spawnParticle(Particle.END_ROD, base, 20 + currentPulse * 6, ring, 0.22, ring, 0.015);
                world.spawnParticle(Particle.PORTAL, base, 34 + currentPulse * 10, ring + 0.15D, 0.34, ring + 0.15D, 0.05);
                world.spawnParticle(Particle.GLOW, base, 16 + currentPulse * 5, ring * 0.72D, 0.24, ring * 0.72D, 0.02);
                this.spawnTravelGreenFlash(world, base, ring * 0.94D, 10 + currentPulse * 4);
                world.spawnParticle(Particle.SMOKE, base.clone().add(0.0D, -0.72D, 0.0D), 8 + currentPulse * 4, 0.34, 0.06, 0.34, 0.02);
                world.playSound(gateLocation, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.28f + currentPulse * 0.08f, 0.76f + currentPulse * 0.12f);
                if (currentPulse >= 2) {
                    world.playSound(gateLocation, Sound.BLOCK_AMETHYST_CLUSTER_STEP, 0.22f, 1.12f + currentPulse * 0.08f);
                    world.playSound(gateLocation, Sound.BLOCK_ANVIL_LAND, 0.12f + currentPulse * 0.04f, 0.66f);
                    world.playSound(gateLocation, Sound.ENTITY_WARDEN_HEARTBEAT, 0.24f, 0.76f + currentPulse * 0.04f);
                }
                if (currentPulse == 3) {
                    this.showTravelTitle(player,
                            this.itemFactory.primary("航線已就緒"),
                            this.itemFactory.muted("躍遷程序即將開始"),
                            4L,
                            16L,
                            8L);
                }
            }, pulse * 5L);
        }
        for (int tick = 0; tick < chargeDuration; tick++) {
            final int currentTick = tick;
            this.scheduler.runEntityDelayed(player, () -> {
                final TravelVessel vessel = this.travelVessels.get(uuid);
                if (vessel == null) {
                    return;
                }
                final double chargeProgress = this.easeInOutSine(currentTick / (double) Math.max(1L, chargeDuration - 1L));
                final double rumble = Math.sin((currentTick + 1.0D) * 0.92D) * (0.014D + chargeProgress * 0.068D)
                        + Math.cos((currentTick + 1.0D) * 1.74D) * (0.005D + chargeProgress * 0.012D);
                final double sway = rumble;
                final Location hoverFrame = base.clone().add(0.0D, Math.sin(currentTick * 0.45D) * 0.035D + chargeProgress * 0.18D, 0.0D);
                this.updateTravelVesselPosition(vessel, hoverFrame, player, chargeProgress, 0.0D, sway);
                final World world = gateLocation.getWorld();
                if (world != null) {
                    this.spawnTravelVesselPreheat(world, hoverFrame, vessel, chargeProgress);
                    this.spawnTravelVesselLights(world, hoverFrame, vessel, chargeProgress, true);
                    if (currentTick % 4 == 0 && chargeProgress > 0.16D) {
                        this.spawnTravelGreenFlash(world, hoverFrame.clone().add(0.0D, 0.2D, 0.0D), 0.28D + chargeProgress * 0.32D, 6 + (int) Math.floor(chargeProgress * 10.0D));
                    }
                    if (chargeProgress > 0.34D) {
                        world.spawnParticle(Particle.BLOCK, gateLocation.clone().add(0.5D, 0.12D, 0.5D), 10 + currentTick,
                                0.95D, 0.04D, 0.95D, 0.02D, gateLocation.getBlock().getBlockData());
                    }
                    if (currentTick % 3 == 0) {
                        player.playSound(hoverFrame, Sound.BLOCK_ANVIL_LAND, 0.08f + (float) (chargeProgress * 0.18f), 0.58f + (float) (chargeProgress * 0.12f));
                        player.playSound(hoverFrame, Sound.ENTITY_WARDEN_HEARTBEAT, 0.16f + (float) (chargeProgress * 0.12f), 0.72f);
                    }
                    if (currentTick == 10) {
                        player.playSound(hoverFrame, Sound.BLOCK_IRON_DOOR_CLOSE, 0.52f, 0.72f);
                        player.playSound(hoverFrame, Sound.ITEM_ARMOR_EQUIP_IRON, 0.42f, 0.62f);
                    }
                    if (currentTick == 14) {
                        player.playSound(hoverFrame, Sound.BLOCK_PISTON_CONTRACT, 0.4f, 0.68f);
                        player.playSound(hoverFrame, Sound.BLOCK_BEACON_AMBIENT, 0.34f, 0.54f);
                    }
                    if (currentTick == 20) {
                        player.playSound(hoverFrame, Sound.ENTITY_GENERIC_EXPLODE, 0.16f, 1.72f);
                        player.playSound(hoverFrame, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.42f, 0.7f);
                        player.playSound(hoverFrame, Sound.ITEM_TRIDENT_THUNDER, 0.22f, 1.48f);
                    }
                }
            }, tick);
        }
        this.scheduler.runEntityDelayed(player, () -> {
            final TravelMotionProfile motion = new TravelMotionProfile(base, player.getLocation().getYaw(), 1.7D, 46.0D, 0.028D, 1.0D);
            this.startAscentSequence(player, gateLocation, destination.displayName(), uuid, motion, ascentSteps);
        }, ascentStartTick);
        this.scheduler.runEntityDelayed(player, () -> {
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 54, 0, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 62, 0, false, false, true));
            player.sendActionBar(this.itemFactory.secondary("航線鎖定完成，準備切入新星域……"));
            final World sourceWorld = gateLocation.getWorld();
            if (sourceWorld != null) {
                sourceWorld.playSound(gateLocation, Sound.BLOCK_END_PORTAL_SPAWN, 0.62f, 0.68f);
                sourceWorld.playSound(gateLocation, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.18f, 1.85f);
                sourceWorld.playSound(gateLocation, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.26f, 0.72f);
                sourceWorld.spawnParticle(Particle.PORTAL, base.clone().add(0.0, 10.0, 0.0), 88, 0.8, 2.2, 0.8, 0.18);
                sourceWorld.spawnParticle(Particle.GLOW, base.clone().add(0.0, 3.4, 0.0), 12, 0.2, 0.6, 0.2, 0.0);
                this.spawnTravelGreenFlash(sourceWorld, base.clone().add(0.0D, 2.4D, 0.0D), 0.84D, 34);
                sourceWorld.spawnParticle(Particle.SMOKE, base.clone().add(0.0, 2.0, 0.0), 40, 0.65, 1.2, 0.65, 0.03);
            }
        }, jumpTick - 14L);
        this.scheduler.runEntityDelayed(player, () -> {
            this.cleanupTravelVessel(uuid);
            if (player.getVehicle() != null) {
                player.leaveVehicle();
            }
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setSpectatorTarget(null);
            }
            // ── 自訂傳送回調（區域系統等）──
            final java.util.function.Consumer<Player> customCallback = this.customTravelCallbacks.remove(uuid);
            if (customCallback != null) {
                final TravelPlayerState cState = this.travelPlayerStates.remove(uuid);
                // 完整恢復：gameMode / flight / walkSpeed / flySpeed。
                // 歷史註解說移動速度由呼叫端自行恢復，但實務上呼叫端常忘記，
                // 結果玩家抵達目的地後完全無法移動（walkSpeed=0）。預設完整還原才安全。
                if (cState != null) {
                    player.setGameMode(cState.gameMode());
                    player.setAllowFlight(cState.allowFlight());
                    player.setWalkSpeed(cState.walkSpeed() > 0.0f ? cState.walkSpeed() : 0.2f);
                    player.setFlySpeed(cState.flySpeed() > 0.0f ? cState.flySpeed() : 0.1f);
                } else {
                    player.setGameMode(GameMode.SURVIVAL);
                    player.setWalkSpeed(0.2f);
                    player.setFlySpeed(0.1f);
                }
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.DARKNESS);
                player.removePotionEffect(PotionEffectType.NAUSEA);
                player.clearTitle();
                player.setFallDistance(0.0f);
                this.travelingPlayers.remove(uuid);
                customCallback.accept(player);
                return;
            }
            final World targetWorld = destination.world();
            if (targetWorld == null) {
                this.scheduler.runEntity(player, () -> {
                    if (player.isValid()) {
                        player.sendMessage(this.itemFactory.warning("目標航線已失效，本次躍遷已取消。"));
                    }
                    this.restoreTravelPlayerState(player, this.travelPlayerStates.remove(uuid), true);
                    this.travelingPlayers.remove(uuid);
                });
                return;
            }
            final Location anchor = new Location(targetWorld, 0.5, Math.max(targetWorld.getMinHeight() + 8, 80), 0.5);
            this.scheduler.runRegion(anchor, task -> {
                final Location arrival = this.travelArrivalLocation(destination);
                if (arrival == null) {
                    this.scheduler.runEntity(player, () -> {
                        if (player.isValid()) {
                            player.sendMessage(this.itemFactory.warning("找不到可用的降落座標，本次躍遷已取消。"));
                        }
                        this.restoreTravelPlayerState(player, this.travelPlayerStates.remove(uuid), true);
                        this.travelingPlayers.remove(uuid);
                    });
                    return;
                }
                arrival.setPitch(-10.0f);
                targetWorld.spawnParticle(Particle.PORTAL, arrival, 40, 0.6, 1.2, 0.6, 0.12);
                targetWorld.spawnParticle(Particle.END_ROD, arrival, 30, 0.4, 1.0, 0.4, 0.02);
                targetWorld.spawnParticle(Particle.GLOW, arrival, 16, 0.45, 0.9, 0.45, 0.02);
                this.spawnTravelGreenFlash(targetWorld, arrival.clone().add(0.0D, 0.5D, 0.0D), 0.48D, 18);
                targetWorld.playSound(arrival, Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 0.82f);
                targetWorld.playSound(arrival, Sound.BLOCK_BEACON_POWER_SELECT, 0.45f, 0.72f);
                player.teleportAsync(arrival).thenAccept(success -> {
                    if (!Boolean.TRUE.equals(success)) {
                        this.scheduler.runEntity(player, () -> {
                            if (player.isValid()) {
                                player.sendMessage(this.itemFactory.warning("星門同步失敗，已取消本次躍遷。"));
                            }
                            this.restoreTravelPlayerState(player, this.travelPlayerStates.remove(uuid), true);
                            this.travelingPlayers.remove(uuid);
                        });
                        return;
                    }
                    this.scheduler.runRegion(arrival, arrivalTask -> {
                        if (!player.isValid() || player.getWorld() != targetWorld) {
                            return;
                        }
                        this.plugin.getPlayerProgressService().incrementStat(player.getUniqueId(), "planets_visited", 1);
                        this.plugin.getPlayerProgressService().setStatMax(player.getUniqueId(), "planet_visited_" + destination.id(), 1);
                        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 16, 0, false, false, true));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20, 0, false, false, true));
                        this.showTravelTitle(player,
                            this.itemFactory.success("已抵達：" + destination.displayName()),
                                this.itemFactory.muted("星門同步完成，視界正在重新展開"),
                                10L,
                                34L,
                                18L);
                        player.sendActionBar(this.itemFactory.success("已抵達：" + destination.displayName()));
                        final long landingRestoreDelay = this.playLandingSequence(player, destination, arrival);
                        this.scheduler.runRegionDelayed(arrival, restoreTask -> {
                            if (!player.isValid()) {
                                this.travelingPlayers.remove(uuid);
                                this.travelPlayerStates.remove(uuid);
                                return;
                            }
                            this.restoreTravelPlayerState(player, this.travelPlayerStates.remove(uuid), false);
                            this.travelingPlayers.remove(uuid);
                        }, landingRestoreDelay);
                    });
                });
            });
        }, jumpTick);
    }

    private TravelVessel spawnTravelVessel(final Location center, final float yaw) {
        final World world = center.getWorld();
        if (world == null) {
            return null;
        }
        final ArmorStand seat = world.spawn(center.clone().add(0.0D, -1.15D, 0.0D), ArmorStand.class, stand -> {
            stand.addScoreboardTag(TRAVEL_VESSEL_TAG);
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setSilent(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSmall(false);
            stand.setMarker(false);
            stand.setPersistent(false);
            stand.setRotation(yaw, 0.0f);
        });
        final ArmorStand cameraAnchor = world.spawn(center.clone().add(this.rotateTravelOffset(0.0D, 3.54D, 0.28D, yaw)), ArmorStand.class, stand -> {
            stand.addScoreboardTag(TRAVEL_VESSEL_TAG);
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setSilent(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setPersistent(false);
            stand.setRotation(yaw, -3.0f);
        });
        final List<TravelVesselPart> parts = new ArrayList<>();
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "rocketCoreLower", 0.0D, 0.42D, 0.0D, Material.WHITE_CONCRETE, 1.56F, 1.74F, 1.56F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "serviceRing", 0.0D, 1.08D, 0.0D, Material.IRON_BLOCK, 2.18F, 0.18F, 2.18F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "rocketCoreMid", 0.0D, 1.98D, 0.0D, Material.WHITE_CONCRETE, 1.34F, 1.54F, 1.34F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "rocketCoreUpper", 0.0D, 3.18D, 0.0D, Material.WHITE_CONCRETE, 1.06F, 1.08F, 1.06F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "cockpitShell", 0.0D, 3.78D, 0.0D, Material.LIGHT_GRAY_CONCRETE, 1.18F, 0.86F, 0.98F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "cockpit", 0.0D, 3.8D, 0.54D, Material.LIGHT_BLUE_STAINED_GLASS, 0.9F, 0.8F, 0.2F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "cockpitWindowLeft", -0.52D, 3.72D, 0.16D, Material.LIGHT_BLUE_STAINED_GLASS, 0.18F, 0.62F, 0.58F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "cockpitWindowRight", 0.52D, 3.72D, 0.16D, Material.LIGHT_BLUE_STAINED_GLASS, 0.18F, 0.62F, 0.58F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "cockpitFrameTop", 0.0D, 4.26D, 0.54D, Material.WHITE_CONCRETE, 1.02F, 0.12F, 0.16F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "cockpitFrameBottom", 0.0D, 3.34D, 0.54D, Material.WHITE_CONCRETE, 1.02F, 0.12F, 0.16F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "cockpitFrameLeft", -0.58D, 3.8D, 0.54D, Material.WHITE_CONCRETE, 0.14F, 0.82F, 0.16F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "cockpitFrameRight", 0.58D, 3.8D, 0.54D, Material.WHITE_CONCRETE, 0.14F, 0.82F, 0.16F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "sideHatch", -0.84D, 2.02D, 0.12D, Material.IRON_BLOCK, 0.12F, 0.84F, 0.7F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "countdownStrip", 0.0D, 2.96D, 0.78D, Material.SEA_LANTERN, 0.24F, 1.46F, 0.08F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "navLightLeft", -0.92D, 2.64D, 0.22D, Material.REDSTONE_BLOCK, 0.16F, 0.16F, 0.16F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "navLightRight", 0.92D, 2.64D, 0.22D, Material.LIME_CONCRETE, 0.16F, 0.16F, 0.16F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "noseCone", 0.0D, 5.0D, 0.0D, Material.LIGHT_GRAY_CONCRETE, 0.84F, 1.56F, 0.84F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "leftFin", -0.88D, -0.34D, -0.04D, Material.RED_CONCRETE, 0.28F, 1.06F, 0.88F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "rightFin", 0.88D, -0.34D, -0.04D, Material.RED_CONCRETE, 0.28F, 1.06F, 0.88F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "rearFin", 0.0D, -0.34D, -0.82D, Material.RED_CONCRETE, 0.88F, 1.06F, 0.28F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "thrusterLeft", -0.48D, -1.76D, 0.0D, Material.CRYING_OBSIDIAN, 0.34F, 0.62F, 0.34F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "thrusterRight", 0.48D, -1.76D, 0.0D, Material.CRYING_OBSIDIAN, 0.34F, 0.62F, 0.34F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "thrusterCenter", 0.0D, -1.82D, 0.0D, Material.CRYING_OBSIDIAN, 0.44F, 0.7F, 0.44F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "engineBell", 0.0D, -2.1D, 0.0D, Material.POLISHED_BASALT, 0.92F, 0.28F, 0.92F));
        return new TravelVessel(seat, cameraAnchor, List.copyOf(parts), yaw);
    }

    private TravelVesselPart spawnTravelVesselPart(final World world,
                                                   final Location center,
                                                   final float yaw,
                                                   final String role,
                                                   final double offsetX,
                                                   final double offsetY,
                                                   final double offsetZ,
                                                   final Material material,
                                                   final float scaleX,
                                                   final float scaleY,
                                                   final float scaleZ) {
        final Vector offset = this.rotateTravelOffset(offsetX, offsetY, offsetZ, yaw);
        final Location displayLocation = center.clone().add(offset);
        final BlockDisplay display = world.spawn(displayLocation, BlockDisplay.class, spawned -> {
            spawned.addScoreboardTag(TRAVEL_VESSEL_PART_TAG);
            spawned.setBlock(material.createBlockData());
            spawned.setPersistent(false);
            spawned.setInvulnerable(true);
            spawned.setGravity(false);
            spawned.setInterpolationDelay(0);
            spawned.setInterpolationDuration(4);
            spawned.setTeleportDuration(4);
            spawned.setViewRange(28.0F);
            spawned.setShadowRadius(0.2F);
            spawned.setShadowStrength(0.7F);
            spawned.setBrightness(new Display.Brightness(15, 15));
            spawned.setTransformation(new Transformation(
                    new Vector3f(-0.5F * scaleX, -0.5F * scaleY, -0.5F * scaleZ),
                    new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F),
                    new Vector3f(scaleX, scaleY, scaleZ),
                    new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F)
            ));
        });
        return new TravelVesselPart(display, role, offsetX, offsetY, offsetZ);
    }

    private void updateTravelVesselPosition(final TravelVessel vessel,
                                            final Location center,
                                            final Player player,
                                            final double hatchProgress,
                                            final double ascentProgress,
                                            final double sway) {
        if (vessel == null) {
            return;
        }
        final ArmorStand seat = vessel.seat();
        if (seat == null || !seat.isValid()) {
            return;
        }
        // ── 預先計算所有位置，然後在單一 runEntity 內批次 teleport ──
        // 這避免了每 tick 對 26+ 個實體各自排程 runEntity 導致 Folia region TPS 暴跌
        final Location seatLocation = center.clone().add(sway * 0.55D, -1.15D + Math.abs(sway) * 0.12D, 0.0D);
        final ArmorStand cameraAnchor = vessel.cameraAnchor();
        final Location cameraLocation = (cameraAnchor != null && cameraAnchor.isValid())
                ? this.travelCameraLocation(vessel, center, hatchProgress, ascentProgress, sway) : null;
        final float cameraPitch = (float) Math.max(-18.0D, -2.0D - ascentProgress * 10.0D);
        final boolean needSpectatorFix = player != null && player.isValid()
                && player.getGameMode() == GameMode.SPECTATOR
                && cameraAnchor != null && player.getSpectatorTarget() != cameraAnchor;
        // 預算零件位置
        final List<Map.Entry<BlockDisplay, Location>> partMoves = new ArrayList<>();
        for (final TravelVesselPart part : vessel.parts()) {
            if (part.display() == null || !part.display().isValid()) {
                continue;
            }
            double offsetX = part.offsetX();
            double offsetY = part.offsetY();
            double offsetZ = part.offsetZ();
            if (part.role().startsWith("cockpit")) {
                offsetY += (1.0D - hatchProgress) * 0.22D;
                offsetZ += (1.0D - hatchProgress) * 0.06D;
            } else if (part.role().contains("thruster") || part.role().contains("Bell")) {
                offsetY -= ascentProgress * 0.08D;
            } else if (part.role().contains("Fin")) {
                offsetY += Math.abs(sway) * 0.18D;
            }
            final Vector rotated = this.rotateTravelOffset(offsetX + sway, offsetY, offsetZ, vessel.yaw());
            partMoves.add(Map.entry(part.display(), center.clone().add(rotated)));
        }
        // 單一排程：所有實體同一 region 垂直柱內，只需一次 runEntity
        this.scheduler.runEntity(seat, () -> {
            if (!seat.isValid()) {
                return;
            }
            seat.teleportAsync(seatLocation);
            seat.setRotation(vessel.yaw(), 0.0f);
            if (cameraAnchor != null && cameraAnchor.isValid() && cameraLocation != null) {
                cameraAnchor.teleportAsync(cameraLocation);
                cameraAnchor.setRotation(vessel.yaw(), cameraPitch);
            }
            for (final Map.Entry<BlockDisplay, Location> entry : partMoves) {
                if (entry.getKey().isValid()) {
                    entry.getKey().teleportAsync(entry.getValue());
                }
            }
        });
        if (needSpectatorFix) {
            this.scheduler.runEntity(player, () -> {
                if (player.isValid() && player.getGameMode() == GameMode.SPECTATOR) {
                    player.setSpectatorTarget(cameraAnchor);
                }
            });
        }
    }

    private void startAscentSequence(final Player player,
                                     final Location gateLocation,
                                     final String destinationName,
                                     final java.util.UUID uuid,
                                     final TravelMotionProfile motion,
                                     final int totalSteps) {
        final int[] step = {0};
        this.scheduler.runEntityTimer(player, task -> {
            if (!player.isValid()) {
                task.cancel();
                return;
            }
            final int currentStep = step[0]++;
            final double progress = Math.min(1.0D, currentStep / (double) Math.max(1, totalSteps - 1));
            final double eased = this.sampleCruiseAscent(progress);
            final double thrust = this.sampleCruiseVelocity(progress);
            final double y = motion.origin().getY() + motion.startHeight() + (motion.endHeight() - motion.startHeight()) * eased;
            final float pitch = (float) (1.5D - thrust * 7.5D);
            final Location frame = new Location(motion.origin().getWorld(), motion.origin().getX(), y, motion.origin().getZ(), motion.yaw(), pitch);
            final TravelVessel vessel = this.travelVessels.get(uuid);
            if (vessel != null) {
                final double sway = Math.sin((currentStep + 2.0D) * 0.32D) * (motion.swayStrength() * (0.65D + thrust * 0.35D))
                        + Math.sin((currentStep + 1.0D) * 0.81D) * 0.0035D * (1.0D - Math.min(1.0D, thrust));
                this.updateTravelVesselPosition(vessel, frame, player, motion.cockpitClosure(), eased, sway);
            } else {
                player.teleportAsync(frame);
            }
            final World world = gateLocation.getWorld();
            if (world != null) {
                if (currentStep % 2 == 0) {
                    world.spawnParticle(Particle.PORTAL, frame, 7 + currentStep / 8, 0.26, 0.34, 0.26, 0.05);
                }
                if (currentStep % 3 == 0) {
                    world.spawnParticle(Particle.END_ROD, frame, 4, 0.12, 0.18, 0.12, 0.01);
                    world.spawnParticle(Particle.GLOW, frame, 5, 0.18, 0.2, 0.18, 0.01);
                }
                this.spawnTravelVesselExhaust(world, frame, vessel, thrust);
                if (eased > 0.28D) {
                    world.spawnParticle(Particle.CLOUD, frame.clone().add(0.0D, -1.42D, 0.0D), 8 + (int) Math.floor(thrust * 10.0D), 0.34, 0.08, 0.34, 0.04);
                }
                if (eased > 0.48D && currentStep % 3 == 0) {
                    world.spawnParticle(Particle.ASH, frame, 5, 0.18, 0.2, 0.18, 0.01);
                }
                this.spawnTravelVesselLights(world, frame, vessel, thrust, false);
                if (currentStep % 2 == 0) {
                    player.playSound(frame, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.2f + (float) (thrust * 0.14f), 0.84f + (float) (thrust * 0.42f));
                    player.playSound(frame, Sound.ENTITY_BREEZE_SHOOT, 0.14f + (float) (thrust * 0.1f), 0.64f + (float) (thrust * 0.1f));
                    player.playSound(frame, Sound.ITEM_ELYTRA_FLYING, 0.1f + (float) (thrust * 0.08f), 0.74f + (float) (thrust * 0.14f));
                }
                if (currentStep % 10 == 0) {
                    player.playSound(frame, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.24f, 0.82f + (float) (thrust * 0.24f));
                    player.playSound(frame, Sound.BLOCK_ANVIL_LAND, 0.14f + (float) (thrust * 0.08f), 0.58f);
                }
                if (currentStep == 10) {
                    player.playSound(frame, Sound.BLOCK_BEACON_AMBIENT, 0.32f, 0.58f);
                }
                if (currentStep == 34) {
                    player.playSound(frame, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.42f, 0.74f);
                    player.playSound(frame, Sound.ENTITY_GENERIC_EXPLODE, 0.18f, 1.82f);
                }
            }
            if (currentStep == 10) {
                this.showTravelTitle(player,
                        this.itemFactory.secondary("脫離地表"),
                        this.itemFactory.muted("視界正在沉入靜默深空"),
                        6L,
                        24L,
                        10L);
            }
            if (currentStep == 34) {
                this.showTravelTitle(player,
                        this.itemFactory.primary("躍遷窗口開啟"),
                        this.itemFactory.secondary("即將進入「" + destinationName + "」航道"),
                        6L,
                        20L,
                        10L);
            }
            if (eased >= 0.22D) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 46, 0, false, false, true));
            }
            if (eased >= 0.40D) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 28, 0, false, false, true));
            }
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 36, 0, false, false, true));
            if (currentStep >= totalSteps - 1) {
                task.cancel();
            }
        }, 1L, 1L);
    }

    private double sampleCruiseAscent(final double progress) {
        final double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        final double easeInRatio = 0.34D;
        final double cruiseRatio = 0.18D;
        final double easeOutRatio = 1.0D - easeInRatio - cruiseRatio;
        final double easeInDistance = 0.24D;
        final double cruiseDistance = 0.18D;
        final double easeOutDistance = 0.58D;
        if (clamped <= easeInRatio) {
            final double local = clamped / easeInRatio;
            return easeInDistance * this.easeInOutSine(local);
        }
        if (clamped <= easeInRatio + cruiseRatio) {
            final double local = (clamped - easeInRatio) / cruiseRatio;
            return easeInDistance + cruiseDistance * local;
        }
        final double local = (clamped - easeInRatio - cruiseRatio) / easeOutRatio;
        return easeInDistance + cruiseDistance + easeOutDistance * this.easeInOutSine(local);
    }

    private double sampleCruiseVelocity(final double progress) {
        final double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        if (clamped <= 0.34D) {
            return this.easeInOutSine(clamped / 0.34D);
        }
        if (clamped <= 0.52D) {
            return 1.0D;
        }
        return 1.0D - this.easeInOutSine((clamped - 0.52D) / 0.48D) * 0.16D;
    }

    private Location travelCameraLocation(final TravelVessel vessel,
                                          final Location center,
                                          final double hatchProgress,
                                          final double ascentProgress,
                                          final double sway) {
        final double shakeScale = 1.0D - Math.min(1.0D, ascentProgress * 0.9D);
        final double launchRumble = (1.0D - Math.min(1.0D, hatchProgress)) * 0.65D + shakeScale * 0.35D;
        final double shakeX = Math.sin((hatchProgress + ascentProgress + 0.18D) * 42.0D) * 0.016D * launchRumble;
        final double shakeY = Math.cos((hatchProgress + ascentProgress + 0.31D) * 34.0D) * 0.022D * launchRumble;
        final double shakeZ = Math.sin((hatchProgress + ascentProgress + 0.07D) * 30.0D) * 0.012D * launchRumble;
        final Vector cockpit = this.rotateTravelOffset(
                sway * 0.05D + shakeX,
                3.54D + (1.0D - hatchProgress) * 0.06D + shakeY,
                0.28D + shakeZ,
                vessel.yaw()
        );
        return center.clone().add(cockpit);
    }

    private double easeInOutSine(final double progress) {
        final double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        return 0.5D - Math.cos(Math.PI * clamped) / 2.0D;
    }

    private void spawnTravelGreenFlash(final World world,
                                       final Location center,
                                       final double radius,
                                       final int amount) {
        if (world == null || center == null || amount <= 0) {
            return;
        }
        final Particle.DustOptions coreGlow = new Particle.DustOptions(Color.fromRGB(120, 255, 170), 1.35F);
        final Particle.DustTransition rimGlow = new Particle.DustTransition(
                Color.fromRGB(80, 255, 145),
                Color.fromRGB(210, 255, 235),
                1.7F
        );
        world.spawnParticle(Particle.DUST, center, amount, radius, 0.22D, radius, 0.01D, coreGlow);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center, Math.max(8, amount / 2), radius * 0.82D, 0.18D, radius * 0.82D, 0.01D, rimGlow);
        world.spawnParticle(Particle.WAX_ON, center, Math.max(6, amount / 3), radius * 0.7D, 0.16D, radius * 0.7D, 0.01D);
    }

    private Vector rotateTravelOffset(final double x,
                                      final double y,
                                      final double z,
                                      final float yaw) {
        final double radians = Math.toRadians(-yaw);
        final double cos = Math.cos(radians);
        final double sin = Math.sin(radians);
        final double rotatedX = x * cos - z * sin;
        final double rotatedZ = x * sin + z * cos;
        return new Vector(rotatedX, y, rotatedZ);
    }

    private void spawnTravelVesselExhaust(final World world,
                                          final Location center,
                                          final TravelVessel vessel,
                                          final double progress) {
        if (world == null || vessel == null) {
            return;
        }
        final Vector leftEngine = this.rotateTravelOffset(-0.48D, -2.64D, 0.0D, vessel.yaw());
        final Vector rightEngine = this.rotateTravelOffset(0.48D, -2.64D, 0.0D, vessel.yaw());
        final Vector centerEngine = this.rotateTravelOffset(0.0D, -2.76D, 0.0D, vessel.yaw());
        final Location left = center.clone().add(leftEngine);
        final Location right = center.clone().add(rightEngine);
        final Location middle = center.clone().add(centerEngine);
        final int flameCount = 5 + (int) Math.floor(progress * 12.0D);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, left, flameCount, 0.14, 0.42, 0.14, 0.02);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, right, flameCount, 0.14, 0.42, 0.14, 0.02);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, middle, flameCount + 2, 0.16, 0.48, 0.16, 0.025);
        world.spawnParticle(Particle.FLAME, left, flameCount + 7, 0.1, 0.34, 0.1, 0.03);
        world.spawnParticle(Particle.FLAME, right, flameCount + 7, 0.1, 0.34, 0.1, 0.03);
        world.spawnParticle(Particle.FLAME, middle, flameCount + 10, 0.12, 0.42, 0.12, 0.04);
        world.spawnParticle(Particle.CLOUD, center.clone().add(0.0D, -2.9D, 0.0D), flameCount + 20, 0.56, 0.24, 0.56, 0.05);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center.clone().add(0.0D, -3.08D, 0.0D), 10 + (int) Math.floor(progress * 12.0D), 0.4, 0.18, 0.4, 0.02);
        world.spawnParticle(Particle.WAX_ON, center.clone().add(0.0D, 1.46D, 0.18D), 12, 0.26, 0.32, 0.22, 0.01);
        if (progress > 0.4D) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0.0D, -0.92D, 0.0D), 8, 0.28, 0.52, 0.28, 0.01);
        }
    }

    private void spawnTravelVesselPreheat(final World world,
                                          final Location center,
                                          final TravelVessel vessel,
                                          final double progress) {
        if (world == null || vessel == null) {
            return;
        }
        final Vector core = this.rotateTravelOffset(0.0D, 1.28D, 0.0D, vessel.yaw());
        final Location coreLocation = center.clone().add(core);
        world.spawnParticle(Particle.ELECTRIC_SPARK, coreLocation, 6 + (int) Math.floor(progress * 9.0D), 0.24, 0.1, 0.24, 0.01);
        world.spawnParticle(Particle.GLOW, coreLocation, 8 + (int) Math.floor(progress * 10.0D), 0.32, 0.18, 0.32, 0.01);
        world.spawnParticle(Particle.WAX_ON, coreLocation, 4 + (int) Math.floor(progress * 5.0D), 0.22, 0.08, 0.22, 0.01);
        if (progress > 0.45D) {
            world.spawnParticle(Particle.SMOKE, center.clone().add(0.0D, -1.72D, 0.0D), 10, 0.24, 0.12, 0.24, 0.03);
        }
    }

    private void spawnTravelVesselLights(final World world,
                                         final Location center,
                                         final TravelVessel vessel,
                                         final double progress,
                                         final boolean charging) {
        if (world == null || center == null || vessel == null) {
            return;
        }
        final double clamped = Math.max(0.0D, Math.min(1.0D, progress));
        final Vector strip = this.rotateTravelOffset(0.0D, 2.96D, 0.86D, vessel.yaw());
        final Vector leftLight = this.rotateTravelOffset(-0.92D, 2.64D, 0.22D, vessel.yaw());
        final Vector rightLight = this.rotateTravelOffset(0.92D, 2.64D, 0.22D, vessel.yaw());
        final Location stripLocation = center.clone().add(strip);
        final Location leftLocation = center.clone().add(leftLight);
        final Location rightLocation = center.clone().add(rightLight);
        final Color stripColor = charging
                ? (clamped < 0.34D
                ? Color.fromRGB(255, 90, 90)
                : clamped < 0.72D
                ? Color.fromRGB(255, 210, 95)
                : Color.fromRGB(120, 255, 160))
                : Color.fromRGB(150, 235, 255);
        world.spawnParticle(Particle.DUST, stripLocation, 7 + (int) Math.floor(clamped * 10.0D), 0.06D, 0.7D, 0.04D, 0.01D,
                new Particle.DustOptions(stripColor, charging ? 1.15F : 0.95F));
        final boolean blink = charging || ((int) Math.floor(clamped * 24.0D)) % 2 == 0;
        if (blink) {
            world.spawnParticle(Particle.GLOW, leftLocation, 4, 0.04D, 0.04D, 0.04D, 0.01D);
            world.spawnParticle(Particle.GLOW, rightLocation, 4, 0.04D, 0.04D, 0.04D, 0.01D);
        }
        world.spawnParticle(Particle.DUST, leftLocation, 2, 0.03D, 0.03D, 0.03D, 0.0D,
                new Particle.DustOptions(Color.fromRGB(255, 80, 80), 0.95F));
        world.spawnParticle(Particle.DUST, rightLocation, 2, 0.03D, 0.03D, 0.03D, 0.0D,
                new Particle.DustOptions(Color.fromRGB(90, 255, 120), 0.95F));
    }

    private void cleanupTravelVessel(final java.util.UUID playerId) {
        final TravelVessel vessel = this.travelVessels.remove(playerId);
        if (vessel == null) {
            return;
        }
        final Entity seat = vessel.seat();
        if (seat != null && seat.isValid()) {
            seat.eject();
        }
        this.removeTravelVessel(vessel);
    }

    private void restoreTravelPlayerState(final Player player,
                                          final TravelPlayerState state,
                                          final boolean interrupted) {
        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setSpectatorTarget(null);
        }
        final GameMode targetGameMode = state == null ? GameMode.SURVIVAL : state.gameMode();
        player.setGameMode(targetGameMode);
        player.setAllowFlight(state != null && state.allowFlight());
        if (state != null) {
            player.setWalkSpeed(state.walkSpeed());
            player.setFlySpeed(state.flySpeed());
            if (state.allowFlight() || targetGameMode == GameMode.CREATIVE || targetGameMode == GameMode.SPECTATOR) {
                player.setFlying(state.flying());
            } else {
                player.setFlying(false);
            }
        } else {
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            player.setFlying(false);
        }
        player.removePotionEffect(PotionEffectType.BLINDNESS);
        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.removePotionEffect(PotionEffectType.NAUSEA);
        player.clearTitle();
        player.setFallDistance(0.0f);
        if (interrupted) {
            player.sendActionBar(this.itemFactory.warning("已從中斷的飛船鏡頭恢復。"));
        }
    }

    private void showTravelTitle(final Player player,
                                 final net.kyori.adventure.text.Component title,
                                 final net.kyori.adventure.text.Component subtitle,
                                 final long fadeInTicks,
                                 final long stayTicks,
                                 final long fadeOutTicks) {
        this.plugin.getTitleMsgService().send(player, title, subtitle, stayTicks, Sound.BLOCK_NOTE_BLOCK_HAT);
    }

    private long playLandingSequence(final Player player,
                                     final TravelDestination destinationInfo,
                                     final Location destination) {
        final World world = destination.getWorld();
        if (world == null) {
            return 24L;
        }
        final PlanetDefinition definition = destinationInfo == null ? null : destinationInfo.planetDefinition();
        final long touchdownTick = this.playLandingCraftSequence(player, destinationInfo, destination);
        player.playSound(destination, Sound.ITEM_ARMOR_EQUIP_ELYTRA, 0.52f, 0.84f);
        player.playSound(destination, Sound.BLOCK_IRON_DOOR_OPEN, 0.46f, 0.9f);
        if (definition != null) {
            this.playPlanetLandingAmbience(player, definition, destination, 0L);
        } else {
            player.playSound(destination, Sound.BLOCK_BEACON_AMBIENT, 0.34f, 0.92f);
            player.playSound(destination, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.2f, 1.1f);
        }
        this.scheduler.runEntityDelayed(player, () -> {
            final Location stepLocation = player.getLocation();
            if (definition != null) {
                player.playSound(stepLocation, this.landingFootstepSound(definition), 0.58f, this.landingFootstepPitch(definition));
            } else {
                player.playSound(stepLocation, Sound.BLOCK_STONE_STEP, 0.56f, 1.04f);
            }
            player.playSound(stepLocation, Sound.ENTITY_PLAYER_BREATH, 0.18f, 0.86f);
        }, touchdownTick + 4L);
        this.scheduler.runEntityDelayed(player, () -> {
            final Location ambienceLocation = player.getLocation();
            if (definition != null) {
                this.playPlanetLandingAmbience(player, definition, ambienceLocation, 12L);
            } else {
                player.playSound(ambienceLocation, Sound.BLOCK_BEACON_POWER_SELECT, 0.24f, 1.18f);
            }
        }, touchdownTick + 12L);
        return touchdownTick + 2L;
    }

    private long playLandingCraftSequence(final Player player,
                                          final TravelDestination destinationInfo,
                                          final Location destination) {
        final World world = destination.getWorld();
        if (world == null) {
            return 24L;
        }
        final float yaw = player.getLocation().getYaw();
        Vector forward = player.getLocation().getDirection().setY(0.0D);
        if (forward.lengthSquared() < 1.0E-4D) {
            forward = new Vector(0.0D, 0.0D, 1.0D);
        } else {
            forward = forward.normalize();
        }
        final Vector landingForward = forward.clone();
        final Location landingAnchor = destination.clone();
        landingAnchor.setY(destination.getY() + 0.15D);
        final double descentHeight = 30.0D;
        final int descentSteps = 52;
        final int departSteps = 16;
        final long departStartTick = descentSteps + 12L;
        final TravelVessel vessel = this.spawnLandingVessel(
            landingAnchor.clone().add(0.0D, descentHeight, 0.0D),
            yaw,
            destinationInfo == null ? null : destinationInfo.planetDefinition());
        if (vessel == null) {
            return 24L;
        }
        for (int step = 0; step <= descentSteps; step++) {
            final int currentStep = step;
            this.scheduler.runRegionDelayed(landingAnchor, task -> {
                final double progress = currentStep / (double) descentSteps;
                final double eased = this.sampleCruiseAscent(progress);
                final double remainingHeight = descentHeight * (1.0D - eased);
                final double drift = (1.0D - eased) * 0.2D;
                final double sway = Math.sin((currentStep + 1.0D) * 0.52D) * 0.06D * (1.0D - progress);
                final Location frame = landingAnchor.clone()
                        .add(landingForward.clone().multiply(drift))
                        .add(0.0D, remainingHeight, 0.0D);
                this.updateTravelVesselPosition(vessel, frame, player, 1.0D, eased, sway);
                this.spawnLandingVesselExhaust(world, frame, vessel.yaw(), 1.0D - progress * 0.45D);
                world.spawnParticle(Particle.CLOUD, frame.clone().add(0.0D, -1.3D, 0.0D), 6 + currentStep / 3, 0.34, 0.08, 0.34, 0.025);
                if (currentStep % 6 == 0) {
                    this.spawnTravelGreenFlash(world, frame.clone().add(0.0D, 0.3D, 0.0D), 0.26D, 8);
                }
                if (currentStep == 8) {
                    player.playSound(frame, Sound.ITEM_ARMOR_EQUIP_IRON, 0.28f, 0.7f);
                }
                if (currentStep == 16) {
                    player.playSound(frame, Sound.BLOCK_PISTON_CONTRACT, 0.32f, 0.72f);
                }
                if (currentStep == descentSteps) {
                    if (player.isValid()) {
                        player.teleportAsync(landingAnchor.clone().setDirection(landingForward.clone()));
                    }
                    player.playSound(frame, Sound.BLOCK_IRON_DOOR_OPEN, 0.42f, 0.98f);
                    player.playSound(frame, Sound.BLOCK_COPPER_BULB_TURN_ON, 0.28f, 1.24f);
                    player.playSound(frame, Sound.ENTITY_GENERIC_EXPLODE, 0.12f, 1.8f);
                    this.spawnTravelGreenFlash(world, landingAnchor.clone().add(0.0D, 0.45D, 0.0D), 0.48D, 22);
                    world.spawnParticle(Particle.CLOUD, landingAnchor.clone().add(0.0D, 0.2D, 0.0D), 22, 0.65, 0.18, 0.65, 0.03);
                }
            }, step);
        }
        for (int step = 0; step < departSteps; step++) {
            final int departStep = step;
            this.scheduler.runRegionDelayed(landingAnchor, task -> {
                final double progress = (departStep + 1.0D) / (double) departSteps;
                final double rise = 0.8D + progress * 7.2D;
                final Location frame = landingAnchor.clone()
                        .add(landingForward.clone().multiply(progress * 2.4D))
                        .add(0.0D, rise, 0.0D);
                this.updateTravelVesselPosition(vessel, frame, null, 1.0D, 1.0D, 0.01D * (1.0D - progress));
                this.spawnLandingVesselExhaust(world, frame, vessel.yaw(), 0.55D + progress * 0.45D);
                world.spawnParticle(Particle.CLOUD, frame.clone().add(0.0D, -1.0D, 0.0D), 4 + departStep / 2, 0.28, 0.05, 0.28, 0.02);
                if (departStep == 0) {
                    player.playSound(frame, Sound.ITEM_ELYTRA_FLYING, 0.34f, 1.18f);
                }
            }, departStartTick + step);
        }
        this.scheduler.runRegionDelayed(landingAnchor, task -> this.removeTravelVessel(vessel), departStartTick + departSteps + 2L);
        return descentSteps;
    }

    private void spawnLandingVesselExhaust(final World world,
                                           final Location center,
                                           final float yaw,
                                           final double intensity) {
        if (world == null || center == null) {
            return;
        }
        final Vector nozzle = this.rotateTravelOffset(0.0D, -1.22D, 0.0D, yaw);
        final Location exhaust = center.clone().add(nozzle);
        final int flameCount = 8 + (int) Math.floor(Math.max(0.0D, intensity) * 10.0D);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, exhaust, flameCount, 0.18D, 0.4D, 0.18D, 0.02D);
        world.spawnParticle(Particle.FLAME, exhaust, flameCount + 8, 0.16D, 0.32D, 0.16D, 0.03D);
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, exhaust.clone().add(0.0D, -0.32D, 0.0D), 9 + (int) Math.floor(intensity * 9.0D), 0.26D, 0.16D, 0.26D, 0.02D);
    }

    private TravelVessel spawnLandingVessel(final Location center,
                                            final float yaw,
                                            final PlanetDefinition definition) {
        final World world = center.getWorld();
        if (world == null) {
            return null;
        }
        final ArmorStand seat = world.spawn(center.clone().add(0.0D, -1.35D, 0.0D), ArmorStand.class, stand -> {
            stand.addScoreboardTag(TRAVEL_VESSEL_TAG);
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setSilent(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setMarker(false);
            stand.setPersistent(false);
            stand.setRotation(yaw, 0.0f);
        });
        final ArmorStand cameraAnchor = world.spawn(center.clone().add(this.rotateTravelOffset(0.0D, 1.08D, 0.12D, yaw)), ArmorStand.class, stand -> {
            stand.addScoreboardTag(TRAVEL_VESSEL_TAG);
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCollidable(false);
            stand.setSilent(true);
            stand.setBasePlate(false);
            stand.setArms(false);
            stand.setSmall(true);
            stand.setMarker(true);
            stand.setPersistent(false);
            stand.setRotation(yaw, -4.0f);
        });
        final Material shell = definition == null
                ? Material.IRON_BLOCK
                : switch (definition.id()) {
                    case "cryon" -> Material.POLISHED_ANDESITE;
                    case "nyx" -> Material.POLISHED_BLACKSTONE;
                    case "helion" -> Material.WAXED_CUT_COPPER;
                    case "tempest" -> Material.OXIDIZED_CUT_COPPER;
                    default -> Material.IRON_BLOCK;
                };
        final Material glass = definition == null
                ? Material.LIGHT_BLUE_STAINED_GLASS
                : switch (definition.id()) {
                    case "cryon" -> Material.LIGHT_BLUE_STAINED_GLASS;
                    case "nyx" -> Material.PURPLE_STAINED_GLASS;
                    case "helion" -> Material.ORANGE_STAINED_GLASS;
                    case "tempest" -> Material.CYAN_STAINED_GLASS;
                    default -> Material.TINTED_GLASS;
                };
        final List<TravelVesselPart> parts = new ArrayList<>();
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "landingBody", 0.0D, 0.28D, 0.0D, shell, 0.9F, 1.2F, 0.9F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "cockpit", 0.0D, 0.98D, 0.16D, glass, 0.58F, 0.42F, 0.58F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "landingCone", 0.0D, 1.64D, 0.0D, shell, 0.42F, 0.66F, 0.42F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "landingFinLeft", -0.42D, -0.46D, 0.0D, shell, 0.16F, 0.56F, 0.52F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "landingFinRight", 0.42D, -0.46D, 0.0D, shell, 0.16F, 0.56F, 0.52F));
        parts.add(this.spawnTravelVesselPart(world, center, yaw, "engineBell", 0.0D, -0.9D, 0.0D, Material.CRYING_OBSIDIAN, 0.5F, 0.22F, 0.5F));
        return new TravelVessel(seat, cameraAnchor, List.copyOf(parts), yaw);
    }

    private void removeTravelVessel(final TravelVessel vessel) {
        if (vessel == null) {
            return;
        }
        final Entity seat = vessel.seat();
        if (seat != null && seat.isValid()) {
            seat.remove();
        }
        final Entity cameraAnchor = vessel.cameraAnchor();
        if (cameraAnchor != null && cameraAnchor.isValid()) {
            cameraAnchor.remove();
        }
        for (final TravelVesselPart part : vessel.parts()) {
            if (part.display() != null && part.display().isValid()) {
                part.display().remove();
            }
        }
    }

    private void playPlanetLandingAmbience(final Player player,
                                           final PlanetDefinition definition,
                                           final Location location,
                                           final long stage) {
        switch (definition.id()) {
            case "aurelia" -> {
                player.playSound(location, Sound.BLOCK_BEACON_AMBIENT, stage == 0L ? 0.44f : 0.32f, 0.64f);
                if (stage > 0L) {
                    player.playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.28f, 1.12f);
                }
            }
            case "cryon" -> {
                player.playSound(location, Sound.BLOCK_POWDER_SNOW_STEP, stage == 0L ? 0.55f : 0.36f, 0.72f);
                if (stage > 0L) {
                    player.playSound(location, Sound.BLOCK_GLASS_BREAK, 0.2f, 1.76f);
                }
            }
            case "nyx" -> {
                player.playSound(location, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, stage == 0L ? 0.46f : 0.34f, 0.52f);
                if (stage > 0L) {
                    player.playSound(location, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.24f, 0.62f);
                }
            }
            case "helion" -> {
                player.playSound(location, Sound.AMBIENT_BASALT_DELTAS_MOOD, stage == 0L ? 0.5f : 0.34f, 0.74f);
                if (stage > 0L) {
                    player.playSound(location, Sound.BLOCK_LAVA_POP, 0.24f, 0.88f);
                }
            }
            case "tempest" -> {
                player.playSound(location, Sound.WEATHER_RAIN_ABOVE, stage == 0L ? 0.62f : 0.44f, 0.7f);
                if (stage > 0L) {
                    player.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.22f, 1.52f);
                }
            }
            default -> player.playSound(location, Sound.BLOCK_BEACON_AMBIENT, 0.3f, 0.8f);
        }
    }

    private Sound landingFootstepSound(final PlanetDefinition definition) {
        return switch (definition.id()) {
            case "aurelia" -> Sound.BLOCK_AMETHYST_BLOCK_STEP;
            case "cryon" -> Sound.BLOCK_POWDER_SNOW_STEP;
            case "nyx" -> Sound.BLOCK_SCULK_SENSOR_STEP;
            case "helion" -> Sound.BLOCK_BASALT_STEP;
            case "tempest" -> Sound.BLOCK_WET_GRASS_STEP;
            default -> Sound.ENTITY_PLAYER_SMALL_FALL;
        };
    }

    private float landingFootstepPitch(final PlanetDefinition definition) {
        return switch (definition.id()) {
            case "aurelia" -> 1.18f;
            case "cryon" -> 0.74f;
            case "nyx" -> 0.58f;
            case "helion" -> 0.86f;
            case "tempest" -> 0.92f;
            default -> 1.0f;
        };
    }

    private Location travelArrivalLocation(final TravelDestination destination) {
        if (destination == null || destination.world() == null) {
            return null;
        }
        return destination.planetDefinition() == null
                ? this.earthArrivalLocation(destination.world())
                : this.planetArrivalLocation(destination.world());
    }

    private World resolveEarthWorld() {
        final World primary = Bukkit.getWorld(EARTH_WORLD_NAME);
        if (primary != null) {
            return primary;
        }
        for (final World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL && !this.isPlanetWorld(world)) {
                return world;
            }
        }
        return null;
    }

    private Location earthArrivalLocation(final World world) {
        final Location spawn = world.getSpawnLocation();
        final int x = spawn.getBlockX();
        final int z = spawn.getBlockZ();
        final int y = Math.max(world.getMinHeight() + 1, world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
        return new Location(world, x + 0.5D, y, z + 0.5D, spawn.getYaw(), spawn.getPitch());
    }

    private static String normalizePlanetKey(final String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        final String normalized = key.toLowerCase(Locale.ROOT).trim();
        return switch (normalized) {
            case "aurelia", AURELIA_WORLD, "d_techproject_aurelia_surface", "world_techproject_aurelia_surface" -> "aurelia";
            case "cryon", CRYON_WORLD, "d_techproject_cryon_surface", "world_techproject_cryon_surface" -> "cryon";
            case "nyx", NYX_WORLD, "d_techproject_nyx_orbit", "world_techproject_nyx_orbit" -> "nyx";
            case "helion", HELION_WORLD, "d_techproject_helion_surface", "world_techproject_helion_surface" -> "helion";
            case "tempest", TEMPEST_WORLD, "d_techproject_tempest_surface", "world_techproject_tempest_surface" -> "tempest";
            case "labyrinth", LABYRINTH_WORLD, "d_techproject_labyrinth_surface", "world_techproject_labyrinth_surface" -> "labyrinth";
            default -> null;
        };
    }

    private static final class PlanetChunkGenerator extends ChunkGenerator {
        // 預載所有非同步區塊生成會用到的內部類別，避免 hot-deploy 覆蓋 JAR 後
        // ClassLoader 的 ZipFile 已關閉而觸發 "zip file closed" 崩服。
        static {
            // noinspection ResultOfMethodCallIgnored — 觸發 class loading 即可
            PlanetTerrainProfile.class.getName();
            PlanetSurfaceColumn.class.getName();
            PlanetBiomeProvider.class.getName();
        }

        private final String planetId;

        private PlanetChunkGenerator(final PlanetDefinition definition) {
            this(definition.id());
        }

        private PlanetChunkGenerator(final String planetId) {
            this.planetId = planetId;
        }

        @Override
        public BiomeProvider getDefaultBiomeProvider(final WorldInfo worldInfo) {
            return new PlanetBiomeProvider(this.planetId);
        }

        @Override
        public void generateNoise(final WorldInfo worldInfo,
                                  final Random random,
                                  final int chunkX,
                                  final int chunkZ,
                                  final ChunkData chunkData) {
            final PlanetTerrainProfile profile = PlanetTerrainProfile.forPlanet(this.planetId);
            final long seed = worldInfo.getSeed() ^ ((long) this.planetId.hashCode() << 32);
            final int minY = chunkData.getMinHeight();
            final int maxY = chunkData.getMaxHeight();
            for (int localX = 0; localX < 16; localX++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    final int worldX = (chunkX << 4) + localX;
                    final int worldZ = (chunkZ << 4) + localZ;
                    final PlanetSurfaceColumn column = profile.sample(seed, worldX, worldZ, minY, maxY);
                    chunkData.setBlock(localX, minY, localZ, Material.BEDROCK);
                    for (int y = minY + 1; y <= column.surfaceY(); y++) {
                        final Material material = y == column.surfaceY()
                                ? column.topMaterial()
                                : y >= column.surfaceY() - 3
                                ? column.subsurfaceMaterial()
                                : column.baseMaterial();
                        chunkData.setBlock(localX, y, localZ, material);
                    }
                    if (column.fluidMaterial() != null && column.surfaceY() < column.seaLevel()) {
                        for (int y = column.surfaceY() + 1; y <= column.seaLevel(); y++) {
                            chunkData.setBlock(localX, y, localZ, column.fluidMaterial());
                        }
                    }
                    // ── 迷宮走道地板：噪點替換地表方塊，營造詭譎氛圍 ──
                    // 僅在 labyrinth 星球、The Glade 外且非牆壁位置套用
                    if ("labyrinth".equals(this.planetId)
                            && !(Math.abs(worldX) <= GLADE_HALF && Math.abs(worldZ) <= GLADE_HALF)
                            && !isStaticMazeWall(seed, worldX, worldZ)) {
                        final int floorY = column.surfaceY();
                        final long floorHash = mazeBorderHash(seed, worldX, 0, worldZ, floorY);
                        final int floorRoll = (int) (floorHash & 0x7F);
                        final Material floorMat;
                        // 主調：潮濕破損深板岩，點綴菌絲、靈魂沙、骸骨、凋零玫瑰苗床
                        // 採集物（SCULK/MOSS_BLOCK）降至原本一半 / 三分之一，減少玩家採集負荷
                        if (floorRoll < 2) {
                            floorMat = Material.SOUL_SAND;
                        } else if (floorRoll < 4) {
                            floorMat = Material.SOUL_SOIL;
                        } else if (floorRoll < 6) {
                            floorMat = Material.MYCELIUM;
                        } else if (floorRoll < 7) {
                            floorMat = Material.SCULK;           // 1/128 (原 2/128) 減半
                        } else if (floorRoll < 9) {
                            floorMat = Material.BONE_BLOCK;
                        } else if (floorRoll < 11) {
                            floorMat = Material.MOSS_BLOCK;      // 2/128 (原 6/128) 降至三分之一
                        } else if (floorRoll < 24) {
                            floorMat = Material.MOSSY_COBBLESTONE;
                        } else if (floorRoll < 32) {
                            floorMat = Material.CRACKED_DEEPSLATE_TILES;
                        } else if (floorRoll < 44) {
                            floorMat = Material.COBBLED_DEEPSLATE;
                        } else if (floorRoll < 56) {
                            floorMat = Material.DEEPSLATE_TILES;
                        } else if (floorRoll < 72) {
                            floorMat = Material.CRACKED_DEEPSLATE_BRICKS;
                        } else {
                            floorMat = Material.DEEPSLATE_BRICKS;
                        }
                        chunkData.setBlock(localX, floorY, localZ, floorMat);
                        // 次表層也弄髒：3 層向下填充深板岩變體
                        final long subHash = floorHash >>> 8;
                        for (int depth = 1; depth <= 3; depth++) {
                            final int y = floorY - depth;
                            if (y <= minY) break;
                            final Material subMat = ((subHash >>> (depth * 3)) & 0x7L) == 0
                                    ? Material.COBBLED_DEEPSLATE
                                    : Material.DEEPSLATE;
                            chunkData.setBlock(localX, y, localZ, subMat);
                        }
                        // 稀疏地表裝飾：約 1.2% 機率放置植被（從 3.1% 降低至 ~1/3）
                        final long decoHash = mazeBorderHash(seed, worldX, worldZ, floorY, 0xBEEF);
                        final int decoRoll = (int) (decoHash & 0xFF);
                        if (decoRoll == 0) {
                            chunkData.setBlock(localX, floorY + 1, localZ, Material.DEAD_BUSH);
                        } else if (decoRoll == 1) {
                            chunkData.setBlock(localX, floorY + 1, localZ, Material.WITHER_ROSE);
                        } else if (decoRoll == 2) {
                            chunkData.setBlock(localX, floorY + 1, localZ, Material.COBWEB);
                        }
                    }
                    // 迷宮牆壁：labyrinth 星球在平坦地面上生成高聳迷宮城牆
                    if ("labyrinth".equals(this.planetId) && isStaticMazeWall(seed, worldX, worldZ)) {
                        final int floorY = column.surfaceY();
                        final int wallTop = floorY + 100; // 100 格高城牆（移動迷宮風格）

                        // ── 判斷是否為門框區域（門兩側柱子 + 拱頂）──
                        final boolean isGateFrame = isGladeGateFrame(worldX, worldZ);

                        for (int y = floorY + 1; y <= wallTop; y++) {
                            final Material wallMat;
                            final int relY = y - floorY;
                            if (isGateFrame) {
                                // 門框材質：鍍金黑石 + 紫珀柱 + 靈魂燈籠
                                if (y == wallTop) {
                                    wallMat = Material.CHISELED_POLISHED_BLACKSTONE;
                                } else if (relY <= 6) {
                                    // 門框底部：柱子用黑石磚
                                    wallMat = Material.POLISHED_BLACKSTONE_BRICKS;
                                } else if (relY <= 8) {
                                    wallMat = Material.GILDED_BLACKSTONE;
                                } else {
                                    wallMat = (relY % 8 == 0) ? Material.GILDED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS;
                                }
                            } else if (y == wallTop) {
                                wallMat = Material.CHISELED_DEEPSLATE;
                            } else if (relY >= 95) {
                                wallMat = Material.POLISHED_DEEPSLATE;
                            } else if (relY <= 3) {
                                // 基底：苔蘚與深板岩斑駁（MOSS_BLOCK 為採集物，降至 1/16）
                                final long baseHash = mazeBorderHash(seed, worldX, y, worldZ, relY * 3);
                                wallMat = (baseHash & 0xF) == 0 ? Material.MOSS_BLOCK
                                        : (baseHash & 0x7) == 1 ? Material.MOSSY_COBBLESTONE
                                        : Material.DEEPSLATE;
                            } else {
                                // 中段多重噪點變化：採集物（MOSS/SCULK）降至原本四分之一，避免牆面被玩家當採集礦洞
                                final long vineHash = mazeBorderHash(seed, worldX, y, worldX ^ y, worldZ);
                                final int roll = (int) (vineHash & 0x3F);
                                if (roll == 0) {
                                    wallMat = Material.MOSS_BLOCK;   // 1/64 (原 2/64)
                                } else if (roll == 1) {
                                    wallMat = Material.SCULK;        // 1/64 (原 2/64)
                                } else if (roll == 4) {
                                    wallMat = Material.BONE_BLOCK;
                                } else if (roll == 5) {
                                    wallMat = Material.SOUL_SOIL;
                                } else if (roll < 8) {
                                    wallMat = Material.CRACKED_DEEPSLATE_BRICKS;
                                } else if (roll < 12) {
                                    wallMat = Material.COBBLED_DEEPSLATE;
                                } else if (roll < 16) {
                                    wallMat = Material.DEEPSLATE_TILES;
                                } else if (roll < 20) {
                                    wallMat = Material.CRACKED_DEEPSLATE_TILES;
                                } else {
                                    wallMat = Material.DEEPSLATE_BRICKS;
                                }
                            }
                            chunkData.setBlock(localX, y, localZ, wallMat);
                        }
                        // 中段稀疏放置發光苔蘚（避免完全黑暗），每 ~64 格一塊
                        final long lightHash = mazeBorderHash(seed, worldX, worldZ, wallTop, 42);
                        if ((lightHash & 0x3FL) == 0) {
                            final int glowY = floorY + 20 + (int) ((lightHash >>> 6) & 0x1F);
                            if (glowY < wallTop) {
                                chunkData.setBlock(localX, glowY, localZ, Material.GLOW_LICHEN);
                            }
                        }
                        // 門框頂部加靈魂火把
                        if (isGateFrame && isGladeGatePillarCorner(worldX, worldZ)) {
                            chunkData.setBlock(localX, wallTop + 1, localZ, Material.SOUL_LANTERN);
                        }
                    }
                    // ── The Glade（倖存者基地）地板 ──
                    if ("labyrinth".equals(this.planetId)
                            && Math.abs(worldX) < GLADE_HALF - 2 && Math.abs(worldZ) < GLADE_HALF - 2) {
                        final int gladeFloor = column.surfaceY();
                        // ═══ 中央古代電梯建築（11×11，|x|,|z|≤5）═══
                        if (Math.abs(worldX) <= 5 && Math.abs(worldZ) <= 5) {
                            buildAncientElevatorColumn(chunkData, localX, localZ, worldX, worldZ, gladeFloor);
                        } else {
                            // 基地地面材質
                            final long pathHash = mazeBorderHash(seed, worldX, 0, worldZ, 0);
                            final int dist = Math.max(Math.abs(worldX), Math.abs(worldZ));

                            // 門前通道：黑石磚地板 + 靈魂火把（通往四個門的道路）
                            final boolean gatePathNS = Math.abs(worldX) <= 2 && Math.abs(worldZ) >= GLADE_HALF - 12;
                            final boolean gatePathEW = Math.abs(worldZ) <= 2 && Math.abs(worldX) >= GLADE_HALF - 12;
                            if (gatePathNS || gatePathEW) {
                                if (Math.abs(worldX) == 2 || Math.abs(worldZ) == 2) {
                                    // 通道邊緣
                                    chunkData.setBlock(localX, gladeFloor, localZ, Material.GILDED_BLACKSTONE);
                                } else {
                                    chunkData.setBlock(localX, gladeFloor, localZ, Material.POLISHED_BLACKSTONE_BRICKS);
                                }
                                // 通道邊的靈魂燈
                                if ((Math.abs(worldX) == 2 || Math.abs(worldZ) == 2)
                                        && (worldX % 4 == 0 || worldZ % 4 == 0)) {
                                    chunkData.setBlock(localX, gladeFloor + 1, localZ, Material.SOUL_LANTERN);
                                }
                            } else if (dist >= GLADE_HALF - 8) {
                                // 外圈：破舊深板岩
                                final int outerRoll = (int) (pathHash & 0x7);
                                chunkData.setBlock(localX, gladeFloor, localZ, switch (outerRoll) {
                                    case 0 -> Material.COBBLED_DEEPSLATE;
                                    case 1 -> Material.DEEPSLATE_TILES;
                                    case 2 -> Material.CRACKED_DEEPSLATE_TILES;
                                    case 3 -> Material.MOSS_BLOCK;
                                    default -> Material.DEEPSLATE;
                                });
                            } else {
                                // 內區：隨機破損地板（亂一點）
                                final int floorRoll = (int) (pathHash & 0xF);
                                chunkData.setBlock(localX, gladeFloor, localZ, switch (floorRoll) {
                                    case 0 -> Material.POLISHED_DEEPSLATE;
                                    case 1, 2 -> Material.CRACKED_STONE_BRICKS;
                                    case 3 -> Material.MOSSY_STONE_BRICKS;
                                    case 4 -> Material.COBBLESTONE;
                                    case 5 -> Material.DEEPSLATE_TILES;
                                    case 6 -> Material.CRACKED_DEEPSLATE_BRICKS;
                                    case 7 -> Material.MOSS_BLOCK;
                                    case 8 -> Material.COBBLED_DEEPSLATE;
                                    case 9 -> Material.DEEPSLATE_BRICKS;
                                    case 10 -> Material.MOSSY_COBBLESTONE;
                                    default -> Material.STONE_BRICKS;
                                });
                            }
                            // 每 8 格放置燈籠
                            if (worldX % 8 == 0 && worldZ % 8 == 0 && dist < GLADE_HALF - 8
                                    && !gatePathNS && !gatePathEW) {
                                chunkData.setBlock(localX, gladeFloor + 1, localZ, Material.LANTERN);
                            }
                        }
                    }
                }
            }
        }

        @Override
        public int getBaseHeight(final WorldInfo worldInfo,
                                 final Random random,
                                 final int x,
                                 final int z,
                                 final HeightMap heightMap) {
            final PlanetTerrainProfile profile = PlanetTerrainProfile.forPlanet(this.planetId);
            final long seed = worldInfo.getSeed() ^ ((long) this.planetId.hashCode() << 32);
            return profile.sample(seed, x, z, worldInfo.getMinHeight(), worldInfo.getMaxHeight()).surfaceY() + 1;
        }

        @Override
        public Location getFixedSpawnLocation(final World world, final Random random) {
            final PlanetTerrainProfile profile = PlanetTerrainProfile.forPlanet(this.planetId);
            final int y = profile.sample(world.getSeed() ^ ((long) this.planetId.hashCode() << 32), 0, 0, world.getMinHeight(), world.getMaxHeight()).surfaceY() + 2;
            return new Location(world, 0.5, y, 0.5);
        }

        @Override
        public boolean shouldGenerateStructures() {
            return !"labyrinth".equals(this.planetId);
        }

        @Override
        public boolean shouldGenerateDecorations() {
            return !"labyrinth".equals(this.planetId);
        }

        @Override
        public boolean shouldGenerateCaves() {
            return !"labyrinth".equals(this.planetId);
        }

        @Override
        public boolean shouldGenerateMobs() {
            // labyrinth 需要生怪以營造詭譎氛圍，但關閉裝飾/結構/洞穴避免破壞迷宮
            return true;
        }

        @Override
        public boolean shouldGenerateSurface() {
            return !"labyrinth".equals(this.planetId);
        }

        // ─── Labyrinth 迷宮牆壁生成 ───

        private static final int MAZE_CELL_SIZE = 9;
        private static final int MAZE_HALF_EXTENT = 490; // ~108 cells × 9 / 2
        private static final int GLADE_HALF = 50;         // 100×100 倖存者基地 (The Glade)

        private static final int GATE_EXIT_HALF_WIDTH = 10;   // 門洞半寬（與 MazeService.GATE_HALF_WIDTH 同步）
        private static final int GATE_EXIT_CORRIDOR_LEN = 45; // 門外保底走廊長度 (5 cells)，確保玩家能深入迷宮且不被困

        private static boolean isStaticMazeWall(final long seed, final int worldX, final int worldZ) {
            // ── The Glade（倖存者基地 100×100）── 3 格厚圍牆
            if (Math.abs(worldX) <= GLADE_HALF && Math.abs(worldZ) <= GLADE_HALF) {
                // 基地內部（距圍牆 3 格以上）
                if (Math.abs(worldX) < GLADE_HALF - 2 && Math.abs(worldZ) < GLADE_HALF - 2) {
                    return false;
                }
                // 邊界牆（|x|=48~50 或 |z|=48~50）── 完全封閉，門由 MazeService 管理
                return true;
            }
            // ── 門外保底走廊：四個方向各自向外延伸 27 格開放通道，避免玩家出門即死路 ──
            // 走廊範圍：沿門的軸向 27 格，橫向 20 格（對應 GATE_WIDTH）
            if (Math.abs(worldX) <= GATE_EXIT_HALF_WIDTH
                    && worldZ > GLADE_HALF && worldZ <= GLADE_HALF + GATE_EXIT_CORRIDOR_LEN) {
                return false; // 南門走廊
            }
            if (Math.abs(worldX) <= GATE_EXIT_HALF_WIDTH
                    && worldZ < -GLADE_HALF && worldZ >= -GLADE_HALF - GATE_EXIT_CORRIDOR_LEN) {
                return false; // 北門走廊
            }
            if (Math.abs(worldZ) <= GATE_EXIT_HALF_WIDTH
                    && worldX > GLADE_HALF && worldX <= GLADE_HALF + GATE_EXIT_CORRIDOR_LEN) {
                return false; // 東門走廊
            }
            if (Math.abs(worldZ) <= GATE_EXIT_HALF_WIDTH
                    && worldX < -GLADE_HALF && worldX >= -GLADE_HALF - GATE_EXIT_CORRIDOR_LEN) {
                return false; // 西門走廊
            }
            // ── 外部迷宮（3 格厚牆壁）──
            final int shifted = worldX + MAZE_HALF_EXTENT;
            final int shiftedZ = worldZ + MAZE_HALF_EXTENT;
            final int localX = Math.floorMod(shifted, MAZE_CELL_SIZE);
            final int localZ = Math.floorMod(shiftedZ, MAZE_CELL_SIZE);
            final int cellX = Math.floorDiv(shifted, MAZE_CELL_SIZE);
            final int cellZ = Math.floorDiv(shiftedZ, MAZE_CELL_SIZE);
            // 3 格厚：位置 0, 1 和 MAZE_CELL_SIZE-1 都是牆
            final boolean inXWall = localX <= 1 || localX >= MAZE_CELL_SIZE - 1;
            final boolean inZWall = localZ <= 1 || localZ >= MAZE_CELL_SIZE - 1;
            if (!inXWall && !inZWall) {
                return false;
            }
            if (inXWall && inZWall) {
                return true; // 角柱（交叉處）
            }
            if (inXWall) {
                if (localX >= MAZE_CELL_SIZE - 1) {
                    return !isMazePassage(seed, cellX, cellZ, cellX + 1, cellZ);
                }
                return !isMazePassage(seed, cellX - 1, cellZ, cellX, cellZ);
            }
            // inZWall
            if (localZ >= MAZE_CELL_SIZE - 1) {
                return !isMazePassage(seed, cellX, cellZ, cellX, cellZ + 1);
            }
            return !isMazePassage(seed, cellX, cellZ - 1, cellX, cellZ);
        }

        /**
         * 判斷座標是否為 Glade 大門的門框區域（門兩側 + 門上方拱頂）。
         * 門位於四個方向：N(z=-48~-50,|x|≤2), S(z=48~50,|x|≤2),
         *                  E(x=48~50,|z|≤2), W(x=-48~-50,|z|≤2)
         * 門框 = 門洞邊緣 ±1 格（|coord| = 3~4）
         */
        private static boolean isGladeGateFrame(final int worldX, final int worldZ) {
            if (Math.abs(worldX) > GLADE_HALF || Math.abs(worldZ) > GLADE_HALF) {
                return false;
            }
            final boolean onZWall = Math.abs(worldZ) >= GLADE_HALF - 2;
            final boolean onXWall = Math.abs(worldX) >= GLADE_HALF - 2;
            // N/S 門框：Z 方向是牆，|x| = 3 或 4（門洞邊緣柱子）
            if (onZWall && (Math.abs(worldX) == 3 || Math.abs(worldX) == 4)) {
                return true;
            }
            // E/W 門框：X 方向是牆，|z| = 3 或 4（門洞邊緣柱子）
            if (onXWall && (Math.abs(worldZ) == 3 || Math.abs(worldZ) == 4)) {
                return true;
            }
            // 門拱頂：牆壁上方橫跨門洞的部分也用門框材質
            // (由 MazeService 的 GATE_HALF_WIDTH=2 可知門洞 |coord|≤2)
            // 拱頂不在 ChunkGen，因為它是 MazeService 動態管理的門方塊
            return false;
        }

        /**
         * 判斷是否為門框最外側角落（放靈魂燈籠的位置）。
         */
        private static boolean isGladeGatePillarCorner(final int worldX, final int worldZ) {
            final boolean onZWall = Math.abs(worldZ) >= GLADE_HALF - 2;
            final boolean onXWall = Math.abs(worldX) >= GLADE_HALF - 2;
            if (onZWall && Math.abs(worldX) == 4) return true;
            if (onXWall && Math.abs(worldZ) == 4) return true;
            return false;
        }

        /**
         * 倖存者基地中央的古代撤離點建築 — 11×11、7 格高、四面開門。
         * 玩家站在中心 Reinforced Deepslate 上跳一下即可觸發撤離火箭，被送回進入迷宮前的位置。
         * 內部角落放置一個 BARREL 作為「冒險者拾取物」虛擬倉庫（每玩家獨立視圖）。
         */
        /**
         * 「時空錨點」撤離聖殿（11×11 開放式紀念碑，取代舊的封閉電梯塔）。
         *
         * <p>核心概念：不再是一間屋子，而是四柱托起一塊浮空石碑的開放廣場。
         * 四角有高聳的雕花深板岩柱 + 頂部靈魂燈籠，把整個 Glade 中心化為明顯地標；
         * 中心踏板仍維持原座標與材質，好讓 MazeService 的撤離觸發邏輯不需變動。</p>
         *
         * <p>關鍵座標（MazeService 依賴，禁止改動）：
         * <ul>
         *   <li>(0, floorY, 0) = REINFORCED_DEEPSLATE 撤離觸發踏板</li>
         *   <li>(3, floorY+1, 3) = BARREL 冒險者拾取物倉庫</li>
         * </ul>
         * </p>
         */
        private static void buildAncientElevatorColumn(final ChunkGenerator.ChunkData data,
                                                        final int localX,
                                                        final int localZ,
                                                        final int worldX,
                                                        final int worldZ,
                                                        final int floorY) {
            final int ax = Math.abs(worldX);
            final int az = Math.abs(worldZ);

            // ── 地板：內圈撤離踏板 + 外圈深板岩磚 ──
            final Material floorMat;
            if (worldX == 0 && worldZ == 0) {
                floorMat = Material.REINFORCED_DEEPSLATE;    // 撤離觸發中心（不可改）
            } else if (ax <= 1 && az <= 1) {
                floorMat = Material.SCULK;                    // 中央 3×3 踏板
            } else if (ax == 2 && az == 2) {
                floorMat = Material.CHISELED_DEEPSLATE;       // 踏板四角
            } else if ((ax == 2 && az <= 2) || (az == 2 && ax <= 2)) {
                floorMat = Material.POLISHED_DEEPSLATE;       // 踏板邊緣
            } else if (ax <= 5 && az <= 5) {
                // 廣場地磚：交錯紋路，越靠近中心越精緻
                final int ringDist = Math.max(ax, az);
                floorMat = switch (ringDist) {
                    case 3 -> (ax + az) % 2 == 0 ? Material.DEEPSLATE_TILES : Material.DEEPSLATE_BRICKS;
                    case 4 -> Material.CRACKED_DEEPSLATE_TILES;
                    default -> Material.COBBLED_DEEPSLATE;
                };
            } else {
                return;
            }
            data.setBlock(localX, floorY, localZ, floorMat);

            // ── 四角石柱：|x|=|z|=4，從地面往上升 12 格，頂部靈魂燈 ──
            final boolean isCornerPillar = ax == 4 && az == 4;
            if (isCornerPillar) {
                for (int y = floorY + 1; y <= floorY + 12; y++) {
                    final int rel = y - floorY;
                    final Material pillarMat;
                    if (rel == 12) {
                        pillarMat = Material.SOUL_LANTERN;
                    } else if (rel == 11) {
                        pillarMat = Material.CHISELED_DEEPSLATE;
                    } else if (rel % 3 == 0) {
                        pillarMat = Material.CHISELED_DEEPSLATE;
                    } else {
                        pillarMat = Material.POLISHED_DEEPSLATE;
                    }
                    data.setBlock(localX, y, localZ, pillarMat);
                }
                return;
            }

            // ── 頂部懸浮石碑：在 floorY+10，四角內延伸 3×3 區塊連接 ──
            if (ax <= 4 && az <= 4 && ax >= 2 && az >= 2) {
                // 頂部橫樑：連接四角柱的外圍框架 (|x|=4 或 |z|=4)
                if (ax == 4 || az == 4) {
                    data.setBlock(localX, floorY + 11, localZ, Material.DEEPSLATE_TILES);
                    data.setBlock(localX, floorY + 12, localZ, Material.DEEPSLATE_TILE_SLAB);
                }
            }

            // ── 中央靈魂火陣列：floorY+1 中心踩點上方 6 格處的裝飾火焰 ──
            // 頂部 sculk catalyst 作為視覺標誌（不用 SHRIEKER，避免呼喚 Warden）
            if (worldX == 0 && worldZ == 0) {
                data.setBlock(localX, floorY + 6, localZ, Material.SOUL_FIRE);
                data.setBlock(localX, floorY + 10, localZ, Material.SCULK_CATALYST);
            }

            // ── 中心踏板四角：floorY+1 靈魂燈（視覺引導玩家走到中心）──
            if (ax == 2 && az == 2) {
                data.setBlock(localX, floorY + 1, localZ, Material.SOUL_LANTERN);
                return;
            }

            // ── 儲物倉庫桶（MazeService 依賴，禁止改動）──
            if (worldX == 3 && worldZ == 3) {
                data.setBlock(localX, floorY + 1, localZ, Material.BARREL);
                return;
            }

            // ── 邊緣裝飾：|x|=3 或 |z|=3 的位置散放 sculk vein，呼應迷宮主題 ──
            if ((ax == 3 && az <= 3) || (az == 3 && ax <= 3)) {
                if ((worldX + worldZ) % 2 == 0) {
                    data.setBlock(localX, floorY + 1, localZ, Material.SCULK_VEIN);
                }
            }
        }

        /**
         * 在 The Glade 內生成一間 5×5×4 的簡易小木屋。
         */
        private static void generateGladeHut(final ChunkGenerator.ChunkData data,
                                             final int localX,
                                             final int localZ,
                                             final int worldX,
                                             final int worldZ,
                                             final int floorY,
                                             final int hutCenterX,
                                             final int hutCenterZ) {
            final int dx = worldX - hutCenterX;
            final int dz = worldZ - hutCenterZ;
            if (Math.abs(dx) > 2 || Math.abs(dz) > 2) {
                return;
            }
            final boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
            final boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            for (int y = floorY + 1; y <= floorY + 4; y++) {
                if (y == floorY + 4) {
                    data.setBlock(localX, y, localZ, Material.OAK_SLAB);
                } else if (corner) {
                    data.setBlock(localX, y, localZ, Material.OAK_LOG);
                } else if (edge && y <= floorY + 3) {
                    // 正面門 (dz == -2 的方向留門)
                    if (dx == 0 && dz == -2 && y <= floorY + 2) {
                        continue; // 門口
                    }
                    data.setBlock(localX, y, localZ, Material.OAK_PLANKS);
                }
            }
        }

        private static boolean isMazePassage(final long seed, final int ax, final int az, final int bx, final int bz) {
            // 每個格子用 hash 決定往哪打通 (spanning tree)
            final int dirB = mazeCellDir(seed, bx, bz);
            final int parentBx = bx + (dirB == 1 ? 1 : dirB == 3 ? -1 : 0);
            final int parentBz = bz + (dirB == 0 ? -1 : dirB == 2 ? 1 : 0);
            if (parentBx == ax && parentBz == az) {
                return true;
            }
            final int dirA = mazeCellDir(seed, ax, az);
            final int parentAx = ax + (dirA == 1 ? 1 : dirA == 3 ? -1 : 0);
            final int parentAz = az + (dirA == 0 ? -1 : dirA == 2 ? 1 : 0);
            if (parentAx == bx && parentAz == bz) {
                return true;
            }
            // 額外隨機通道（~31%）大幅提升連通性，避免出現封閉死區困住玩家
            final long borderHash = mazeBorderHash(seed, ax, az, bx, bz);
            return (borderHash & 0x1FL) < 10L;
        }

        private static int mazeCellDir(final long seed, final int cx, final int cz) {
            long h = seed ^ ((long) cx * 0x517cc1b727220a95L) ^ ((long) cz * 0x6c62272e07bb0142L);
            h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
            h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
            return (int) ((h ^ (h >>> 33)) & 3L); // 0=N, 1=E, 2=S, 3=W
        }

        private static long mazeBorderHash(final long seed, final int ax, final int az, final int bx, final int bz) {
            final int minX = Math.min(ax, bx);
            final int minZ = Math.min(az, bz);
            final int maxX = Math.max(ax, bx);
            final int maxZ = Math.max(az, bz);
            long h = seed ^ ((long) minX * 0x9e3779b97f4a7c15L) ^ ((long) minZ * 0x6c62272e07bb0142L)
                    ^ ((long) maxX * 0x2545f4914f6cdd1dL) ^ ((long) maxZ * 0xbf58476d1ce4e5b9L);
            h = (h ^ (h >>> 30)) * 0xbf58476d1ce4e5b9L;
            return h ^ (h >>> 27);
        }
    }

    private static final class PlanetBiomeProvider extends BiomeProvider {
        private final String planetId;

        private PlanetBiomeProvider(final String planetId) {
            this.planetId = planetId;
        }

        @Override
        public Biome getBiome(final WorldInfo worldInfo, final int x, final int y, final int z) {
            final double climate = PlanetTerrainProfile.octaveNoise(worldInfo.getSeed() ^ ((long) this.planetId.hashCode() << 24), x * 0.0028D, z * 0.0028D, 3, 2.0D, 0.5D);
            return switch (this.planetId) {
                case "aurelia" -> climate > 0.32D ? Biome.ERODED_BADLANDS : climate > -0.08D ? Biome.BADLANDS : Biome.DESERT;
                case "cryon" -> climate > 0.28D ? Biome.FROZEN_PEAKS : climate > -0.18D ? Biome.SNOWY_SLOPES : Biome.ICE_SPIKES;
                case "nyx" -> climate > 0.3D ? Biome.END_HIGHLANDS : climate > -0.2D ? Biome.THE_END : Biome.SMALL_END_ISLANDS;
                case "helion" -> climate > 0.34D ? Biome.BASALT_DELTAS : climate > -0.05D ? Biome.BADLANDS : Biome.DESERT;
                case "tempest" -> climate > 0.3D ? Biome.WINDSWEPT_GRAVELLY_HILLS : climate > -0.08D ? Biome.SWAMP : Biome.MANGROVE_SWAMP;
                // DEEP_DARK 只在 shouldGenerateStructures() = true 時會生成 ancient city，
                // 我們已經把迷途星的 structures 全部關掉，所以安全。
                // 原本使用 DARK_FOREST 會讓 vanilla 在迷途星地表強制塞入 woodland mansion（拱門+石磚屋），
                // 因此改用 DEEP_DARK，無原生結構、色調也貼合迷宮氛圍。
                case "labyrinth" -> Biome.DEEP_DARK;
                default -> Biome.PLAINS;
            };
        }

        @Override
        public List<Biome> getBiomes(final WorldInfo worldInfo) {
            return switch (this.planetId) {
                case "aurelia" -> List.of(Biome.DESERT, Biome.BADLANDS, Biome.ERODED_BADLANDS);
                case "cryon" -> List.of(Biome.SNOWY_SLOPES, Biome.FROZEN_PEAKS, Biome.ICE_SPIKES);
                case "nyx" -> List.of(Biome.THE_END, Biome.END_HIGHLANDS, Biome.SMALL_END_ISLANDS);
                case "helion" -> List.of(Biome.DESERT, Biome.BADLANDS, Biome.BASALT_DELTAS);
                case "tempest" -> List.of(Biome.SWAMP, Biome.MANGROVE_SWAMP, Biome.WINDSWEPT_GRAVELLY_HILLS);
                case "labyrinth" -> List.of(Biome.DEEP_DARK);
                default -> List.of(Biome.PLAINS);
            };
        }
    }

    private record PlanetSurfaceColumn(int surfaceY,
                                       Material baseMaterial,
                                       Material subsurfaceMaterial,
                                       Material topMaterial,
                                       Material fluidMaterial,
                                       int seaLevel) {
    }

    private record PlanetTerrainProfile(int baseHeight,
                                        int seaLevel,
                                        double continentScale,
                                        double detailScale,
                                        double ridgeScale,
                                        int craterSpacing,
                                        int craterRadiusMin,
                                        int craterRadiusMax,
                                        double craterDepth,
                        double plateauScale,
                        double canyonDepth,
                        double terraceStep,
                        double spireScale,
                                        Material baseMaterial,
                                        Material subsurfaceMaterial,
                                        Material defaultTopMaterial,
                                        Material fluidMaterial) {

        private static PlanetTerrainProfile forPlanet(final String planetId) {
            return switch (planetId) {
            case "cryon" -> new PlanetTerrainProfile(92, 88, 18.0D, 5.0D, 5.5D, 132, 9, 20, 6.5D,
                6.0D, 3.0D, 0.0D, 2.5D,
                        Material.PACKED_ICE, Material.SNOW_BLOCK, Material.BLUE_ICE, null);
            case "nyx" -> new PlanetTerrainProfile(84, 72, 15.0D, 6.0D, 8.0D, 108, 9, 18, 10.0D,
                8.0D, 4.5D, 2.5D, 4.0D,
                        Material.END_STONE, Material.END_STONE_BRICKS, Material.END_STONE_BRICKS, null);
            case "helion" -> new PlanetTerrainProfile(90, 76, 19.0D, 6.5D, 9.0D, 104, 9, 20, 8.0D,
                9.0D, 4.0D, 2.5D, 4.5D,
                        Material.BASALT, Material.BLACKSTONE, Material.BLACKSTONE, Material.LAVA);
            case "tempest" -> new PlanetTerrainProfile(86, 82, 17.0D, 7.0D, 8.5D, 108, 9, 20, 7.0D,
                7.0D, 3.5D, 2.0D, 3.5D,
                        Material.STONE, Material.DEEPSLATE, Material.WEATHERED_COPPER, Material.WATER);
            case "labyrinth" -> new PlanetTerrainProfile(64, 60, 0.0D, 0.0D, 0.0D, 0, 0, 0, 0.0D,
                0.0D, 0.0D, 0.0D, 0.0D,
                        Material.DEEPSLATE, Material.POLISHED_DEEPSLATE, Material.SMOOTH_STONE, null);
            default -> new PlanetTerrainProfile(88, 74, 18.0D, 6.0D, 7.0D, 118, 9, 20, 8.0D,
                8.0D, 3.5D, 2.0D, 3.0D,
                        Material.END_STONE, Material.END_STONE, Material.END_STONE, null);
            };
        }

        private PlanetSurfaceColumn sample(final long seed,
                                           final int x,
                                           final int z,
                                           final int minY,
                                           final int maxY) {
            final double continental = octaveNoise(seed + 11L, x * 0.0026D, z * 0.0026D, 4, 2.0D, 0.5D);
            final double detail = octaveNoise(seed + 29L, x * 0.013D, z * 0.013D, 4, 2.15D, 0.5D);
            final double ridgeNoise = octaveNoise(seed + 47L, x * 0.006D, z * 0.006D, 3, 2.1D, 0.55D);
            final double ridge = (1.0D - Math.abs(ridgeNoise)) * this.ridgeScale;
            final double crater = craterNoise(seed + 71L, x, z, this.craterSpacing, this.craterRadiusMin, this.craterRadiusMax, this.craterDepth);
            final double plateauNoise = octaveNoise(seed + 89L, x * 0.0018D, z * 0.0018D, 3, 2.0D, 0.52D);
            final double plateau = Math.max(0.0D, plateauNoise - 0.05D) * this.plateauScale;
            final double erosionNoise = Math.abs(octaveNoise(seed + 113L, x * 0.0046D, z * 0.0046D, 3, 2.0D, 0.55D));
            final double canyonMask = Math.max(0.0D, 1.0D - Math.abs(erosionNoise - 0.18D) / 0.11D);
            final double canyon = canyonMask * this.canyonDepth;
            final double spireNoise = octaveNoise(seed + 137L, x * 0.0115D, z * 0.0115D, 2, 2.0D, 0.5D);
            final double spire = Math.max(0.0D, spireNoise - 0.42D) * this.spireScale;
            double rawHeight = this.baseHeight + continental * this.continentScale + detail * this.detailScale + ridge + plateau + spire - canyon - crater;
            if (this.terraceStep > 0.0D) {
                final double terraceNoise = octaveNoise(seed + 149L, x * 0.0034D, z * 0.0034D, 2, 2.0D, 0.5D);
                rawHeight = this.terrace(rawHeight, this.terraceStep, terraceNoise);
            }
            final int surfaceY = Math.max(minY + 6, Math.min(maxY - 12, (int) Math.round(rawHeight)));
            final double surfaceNoise = octaveNoise(seed + 101L, x * 0.018D, z * 0.018D, 3, 2.2D, 0.5D);
            final Material topMaterial = this.resolveTopMaterial(surfaceY, surfaceNoise, crater);
            return new PlanetSurfaceColumn(surfaceY, this.baseMaterial, this.subsurfaceMaterial, topMaterial, this.fluidMaterial, this.seaLevel);
        }

        private double terrace(final double rawHeight, final double step, final double noise) {
            final double shifted = rawHeight + noise * (step * 0.35D);
            final double lower = Math.floor(shifted / step) * step;
            final double delta = shifted - lower;
            final double smoothing = delta / step;
            return lower + step * smoothstep(Math.max(0.0D, Math.min(1.0D, (smoothing - 0.18D) / 0.64D)));
        }

        private Material resolveTopMaterial(final int surfaceY, final double noise, final double crater) {
            if (this.defaultTopMaterial == Material.BLUE_ICE) {
                if (surfaceY >= this.baseHeight + 18) {
                    return Material.SNOW_BLOCK;
                }
                return noise > 0.2D ? Material.BLUE_ICE : Material.PACKED_ICE;
            }
            if (this.defaultTopMaterial == Material.END_STONE_BRICKS) {
                if (noise > 0.58D) {
                    return Material.CRYING_OBSIDIAN;
                }
                return noise < -0.35D ? Material.END_STONE : Material.END_STONE_BRICKS;
            }
            if (this.defaultTopMaterial == Material.BLACKSTONE) {
                if (crater > 6.5D || noise > 0.35D) {
                    return Material.MAGMA_BLOCK;
                }
                return noise < -0.35D ? Material.BASALT : Material.BLACKSTONE;
            }
            if (this.defaultTopMaterial == Material.WEATHERED_COPPER) {
                if (surfaceY <= this.seaLevel + 1) {
                    return Material.CUT_COPPER;
                }
                if (noise > 0.4D) {
                    return Material.OXIDIZED_COPPER;
                }
                return noise < -0.25D ? Material.WEATHERED_COPPER : Material.WEATHERED_CUT_COPPER;
            }
            if (noise > 0.42D) {
                return Material.AMETHYST_BLOCK;
            }
            return noise < -0.4D ? Material.SCULK : this.defaultTopMaterial;
        }

        private static double octaveNoise(final long seed,
                                          final double x,
                                          final double z,
                                          final int octaves,
                                          final double lacunarity,
                                          final double persistence) {
            double total = 0.0D;
            double frequency = 1.0D;
            double amplitude = 1.0D;
            double maxAmplitude = 0.0D;
            for (int index = 0; index < octaves; index++) {
                total += valueNoise(seed + index * 101L, x * frequency, z * frequency) * amplitude;
                maxAmplitude += amplitude;
                amplitude *= persistence;
                frequency *= lacunarity;
            }
            return maxAmplitude == 0.0D ? 0.0D : total / maxAmplitude;
        }

        private static double craterNoise(final long seed,
                                          final int x,
                                          final int z,
                                          final int spacing,
                                          final int radiusMin,
                                          final int radiusMax,
                                          final double maxDepth) {
            if (spacing <= 0 || maxDepth <= 0.0D) {
                return 0.0D;
            }
            final int gridX = Math.floorDiv(x, spacing);
            final int gridZ = Math.floorDiv(z, spacing);
            double deepest = 0.0D;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final int cellX = gridX + dx;
                    final int cellZ = gridZ + dz;
                    final double centerX = (cellX * spacing) + normalized(hash(seed, cellX, cellZ, 17L)) * spacing;
                    final double centerZ = (cellZ * spacing) + normalized(hash(seed, cellX, cellZ, 29L)) * spacing;
                    final int radius = radiusMin + (int) Math.round(normalized(hash(seed, cellX, cellZ, 43L)) * (radiusMax - radiusMin));
                    final double distance = Math.hypot(x - centerX, z - centerZ);
                    if (distance >= radius) {
                        continue;
                    }
                    final double shape = 1.0D - (distance / radius);
                    deepest = Math.max(deepest, shape * shape * maxDepth);
                }
            }
            return deepest;
        }

        private static double valueNoise(final long seed, final double x, final double z) {
            final int x0 = (int) Math.floor(x);
            final int z0 = (int) Math.floor(z);
            final int x1 = x0 + 1;
            final int z1 = z0 + 1;
            final double tx = fade(x - x0);
            final double tz = fade(z - z0);
            final double v00 = normalized(hash(seed, x0, z0, 0L)) * 2.0D - 1.0D;
            final double v10 = normalized(hash(seed, x1, z0, 0L)) * 2.0D - 1.0D;
            final double v01 = normalized(hash(seed, x0, z1, 0L)) * 2.0D - 1.0D;
            final double v11 = normalized(hash(seed, x1, z1, 0L)) * 2.0D - 1.0D;
            final double nx0 = lerp(v00, v10, tx);
            final double nx1 = lerp(v01, v11, tx);
            return lerp(nx0, nx1, tz);
        }

        private static long hash(final long seed, final int x, final int z, final long salt) {
            long value = seed + salt;
            value ^= 0x9E3779B97F4A7C15L * x;
            value ^= 0xC2B2AE3D27D4EB4FL * z;
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdl;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53l;
            value ^= value >>> 33;
            return value & Long.MAX_VALUE;
        }

        private static double normalized(final long value) {
            return (value % 1_000_000L) / 1_000_000.0D;
        }

        private static double fade(final double value) {
            return value * value * value * (value * (value * 6.0D - 15.0D) + 10.0D);
        }

        private static double lerp(final double from, final double to, final double delta) {
            return from + (to - from) * delta;
        }

        private static double smoothstep(final double value) {
            return value * value * (3.0D - 2.0D * value);
        }
    }
}