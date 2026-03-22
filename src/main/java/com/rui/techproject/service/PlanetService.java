package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.model.PlacedMachine;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.LocationKey;
import com.rui.techproject.util.SafeScheduler;
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
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
import java.util.concurrent.ConcurrentHashMap;

public final class PlanetService {
    public static final String AURELIA_WORLD = "tech_aurelia";
    public static final String CRYON_WORLD = "tech_cryon";
    public static final String NYX_WORLD = "tech_nyx";
    public static final String HELION_WORLD = "tech_helion";
    public static final String TEMPEST_WORLD = "tech_tempest";

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
    private static final int PLANET_FRUIT_TREE_BURST_CHANCE = 36;
    private static final int PLANET_FRUIT_TREE_EXTRA_BURST_CHANCE = 8;
    private static final int MAX_FRUIT_PER_TREE = 2;
    private static final int PERSONAL_NODE_SCAN_RADIUS = 8;
    private static final int PERSONAL_NODE_SCAN_Y_RADIUS = 3;
    private static final int MAX_VISIBLE_HARVEST_NODES = 10;
    private static final int PLANET_BOUNDARY_RADIUS = 3000;
    private static final long PLANETARY_GATE_TRAVEL_COST = 1200L;
    private static final String SHIP_FUEL_ITEM_ID = "refined_oil";
    private static final int SHIP_BASE_FUEL_COST = 2;
    private static final int SHIP_FUEL_WEIGHT_DIVISOR = 64;
    private static final int SHIP_COUNTDOWN_SECONDS = 5;
    private static final Key PLANETARY_GATE_MENU_FONT = Key.key("minecraft", "techproject_gate");
    private static final String PLANETARY_GATE_MENU_SHIFT = "\uF100";
    private static final String PLANETARY_GATE_MENU_GLYPH = "\uF000";
    private static final String PLANETARY_GATE_MENU_TITLE = PLANETARY_GATE_MENU_SHIFT + PLANETARY_GATE_MENU_GLYPH;
    private static final String EARTH_DESTINATION_ID = "earth";
    private static final String EARTH_WORLD_NAME = "world";

    private final TechProjectPlugin plugin;
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
    private final Map<java.util.UUID, LocationKey> openPlanetaryGateMenus = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Map<LocationKey, Long>> personalHarvestCooldowns = new ConcurrentHashMap<>();
    private final Map<LocationKey, Long> machineHarvestCooldowns = new ConcurrentHashMap<>();
    private final Map<LocationKey, java.util.UUID> harvestNodeDisplays = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, PendingHarvest> pendingHarvests = new ConcurrentHashMap<>();
    private final Map<LocationKey, FruitNodeRegrowth> fruitNodeRegrowths = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Long> boundaryWarningCooldowns = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Map<HazardType, Long>> cuisineWardExpiries = new ConcurrentHashMap<>();
    private final Map<String, ItemStack> techItemStackCache = new ConcurrentHashMap<>();
    private final Random ambientRandom = new Random();
    private StorageBackend storageBackend;
    private volatile boolean worldCreationUnsupported;

    private enum HazardType {
        RADIATION,
        CRYO,
        VACUUM,
        SOLAR,
        STORM
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

    public PlanetService(final TechProjectPlugin plugin,
                         final SafeScheduler scheduler,
                         final ItemFactoryUtil itemFactory,
                         final TechRegistry registry) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.itemFactory = itemFactory;
        this.registry = registry;
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
            this.tickPlanetHazards();
            this.tickPlanetAmbience();
        }, 40L, 40L);
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
        if (this.travelVessels.containsKey(playerId)) {
            this.cleanupTravelVessel(playerId);
        }
        this.travelPlayerStates.remove(playerId);
    }

    public void shutdown() {
        this.pendingHarvests.clear();
        for (final java.util.UUID playerId : new ArrayList<>(this.travelVessels.keySet())) {
            this.cleanupTravelVessel(playerId);
        }
        this.travelingPlayers.clear();
        this.travelPlayerStates.clear();
        this.boundaryWarningCooldowns.clear();
        this.cuisineWardExpiries.clear();
        for (final Map.Entry<LocationKey, java.util.UUID> entry : new ArrayList<>(this.harvestNodeDisplays.entrySet())) {
            this.removeHarvestNodeDisplay(entry.getKey(), entry.getValue());
        }
        this.harvestNodeDisplays.clear();
        this.fruitNodeRegrowths.clear();
        this.techItemStackCache.clear();
        this.purgeManagedPlanetEntities();
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
        if (block.getType() != definition.ruinCoreMaterial()) {
            return this.collectPlanetSurfaceNode(player, block, definition);
        }
        final LocationKey key = LocationKey.from(block.getLocation());
        final boolean globallyActivated = this.activatedRuins.contains(key);
        final boolean playerActivated = this.hasPlayerActivatedRuin(player.getUniqueId(), key);
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
        final List<ItemStack> rewards = this.buildRewardStacks(definition.ruinRewardIds());
        for (final ItemStack reward : rewards) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 1.0, 0.5), reward);
            final String rewardId = this.itemFactory.getTechItemId(reward);
            if (rewardId != null) {
                this.unlockPlanetItem(player, rewardId);
            }
        }
        this.plugin.getPlayerProgressService().incrementStat(player.getUniqueId(), "planet_ruins_activated", 1);
        block.getWorld().spawnParticle(Particle.END_ROD, block.getLocation().add(0.5, 1.0, 0.5), 18, 0.35, 0.35, 0.35, 0.02);
        block.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, block.getLocation().add(0.5, 1.1, 0.5), 10, 0.4, 0.4, 0.4, 0.01);
        block.getWorld().playSound(block.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.05f);
        player.sendMessage(this.itemFactory.success((globallyActivated ? "已重新同步 " : "已啟動 ") + definition.displayName() + " 遺跡核心，取得新的研究樣本。"));
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

    public String planetDisplayName(final String planetId) {
        final PlanetDefinition definition = this.planets.get(planetId == null ? "" : planetId.toLowerCase(Locale.ROOT));
        return definition == null ? planetId : definition.displayName();
    }

    public List<String> planetInfoLines() {
        return this.planets.values().stream()
                .map(definition -> definition.displayName() + "：" + this.hazardLabel(definition.hazardType()) + " / " + this.gravityLabel(definition) + "，" + this.planetProtectionHint(definition))
                .toList();
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
        if (this.ambientRandom.nextInt(8) != 0) {
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
        this.adjustAttribute(monster, Attribute.MAX_HEALTH, profile.healthMultiplier(), 0.0D, 8.0D);
        final var maxHealth = monster.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            monster.setHealth(Math.max(1.0D, maxHealth.getValue()));
        }
        this.adjustAttribute(monster, Attribute.ATTACK_DAMAGE, 1.0D, profile.attackBonus(), 2.0D);
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
        killer.sendActionBar(this.itemFactory.success("已擊退「" + profile.displayName() + "」，回收戰鬥樣本。"));
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
        for (final Entity entity : new ArrayList<>(world.getEntities())) {
            try {
                if (this.isManagedPlanetEntity(entity)) {
                    entity.remove();
                }
            } catch (final IllegalStateException ignored) {
            }
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
            return;
        }
        final Location anchor = new Location(world, 0.5, Math.max(world.getMinHeight() + 8, 80), 0.5);
        this.scheduler.runRegion(anchor, task -> {
            switch (definition.id()) {
                case "aurelia" -> this.generateAurelia(world, definition.markerMaterial(), definition.ruinCoreMaterial());
                case "cryon" -> this.generateCryon(world, definition.markerMaterial(), definition.ruinCoreMaterial());
                case "nyx" -> this.generateNyx(world, definition.markerMaterial(), definition.ruinCoreMaterial());
                case "helion" -> this.generateHelion(world, definition.markerMaterial(), definition.ruinCoreMaterial());
                case "tempest" -> this.generateTempest(world, definition.markerMaterial(), definition.ruinCoreMaterial());
                default -> {
                }
            }
        });
    }

    private void generateAurelia(final World world, final Material markerMaterial, final Material ruinCoreMaterial) {
        final int baseY = this.terrainPlateauY(world, 0, 0, 8) + 2;
        this.buildLandingPad(world, 0, baseY, 0, 7, Material.PURPUR_BLOCK, Material.CRYING_OBSIDIAN);
        this.buildRuinRing(world, 0, baseY + 1, 0, Material.PURPUR_PILLAR, Material.AMETHYST_BLOCK, 7);
        this.buildSpire(world, 11, this.surfaceY(world, 11, -6) + 1, -6, Material.AMETHYST_BLOCK, 6);
        this.buildSpire(world, -12, this.surfaceY(world, -12, 8) + 1, 8, Material.SCULK, 5);
        this.scatterSurfacePatch(world, 8, -9, Material.AMETHYST_CLUSTER, 8, 6);
        this.scatterSurfacePatch(world, -10, 10, Material.SCULK_CATALYST, 6, 5);
        world.getBlockAt(0, baseY + 1, 0).setType(markerMaterial, false);
        world.getBlockAt(0, baseY + 2, 7).setType(ruinCoreMaterial, false);
    }

    private void generateCryon(final World world, final Material markerMaterial, final Material ruinCoreMaterial) {
        final int baseY = this.terrainPlateauY(world, 0, 0, 8) + 2;
        this.buildLandingPad(world, 0, baseY, 0, 7, Material.PACKED_ICE, Material.BLUE_ICE);
        this.buildRuinRing(world, 3, baseY + 1, 2, Material.PACKED_ICE, Material.BLUE_ICE, 6);
        this.buildSpire(world, -9, this.surfaceY(world, -9, -8) + 1, -8, Material.BLUE_ICE, 7);
        this.buildSpire(world, 12, this.surfaceY(world, 12, 6) + 1, 6, Material.PACKED_ICE, 6);
        this.scatterSurfacePatch(world, -8, 7, Material.SNOW_BLOCK, 12, 7);
        this.scatterSurfacePatch(world, 10, -10, Material.BLUE_ICE, 8, 6);
        world.getBlockAt(0, baseY + 1, 0).setType(markerMaterial, false);
        world.getBlockAt(3, baseY + 2, 8).setType(ruinCoreMaterial, false);
    }

    private void generateNyx(final World world, final Material markerMaterial, final Material ruinCoreMaterial) {
        final int baseY = this.terrainPlateauY(world, 0, 0, 7) + 2;
        this.buildLandingPad(world, 0, baseY, 0, 6, Material.END_STONE_BRICKS, Material.CRYING_OBSIDIAN);
        this.buildRuinRing(world, 2, baseY + 1, 1, Material.END_STONE_BRICKS, Material.CRYING_OBSIDIAN, 6);
        this.buildSpire(world, -11, this.surfaceY(world, -11, 5) + 1, 5, Material.END_STONE_BRICKS, 7);
        this.scatterSurfacePatch(world, 9, 9, Material.CRYING_OBSIDIAN, 10, 6);
        this.scatterSurfacePatch(world, -8, -7, Material.CHORUS_FLOWER, 6, 5);
        world.getBlockAt(0, baseY + 1, 0).setType(markerMaterial, false);
        world.getBlockAt(2, baseY + 2, 7).setType(ruinCoreMaterial, false);
    }

    private void generateHelion(final World world, final Material markerMaterial, final Material ruinCoreMaterial) {
        final int baseY = this.terrainPlateauY(world, 0, 0, 8) + 2;
        this.buildLandingPad(world, 0, baseY, 0, 7, Material.POLISHED_BLACKSTONE_BRICKS, Material.MAGMA_BLOCK);
        this.buildRuinRing(world, -1, baseY + 1, 2, Material.POLISHED_BLACKSTONE_BRICKS, Material.MAGMA_BLOCK, 6);
        this.buildSpire(world, 10, this.surfaceY(world, 10, -7) + 1, -7, Material.BASALT, 7);
        this.buildSpire(world, -12, this.surfaceY(world, -12, 8) + 1, 8, Material.BLACKSTONE, 6);
        this.scatterSurfacePatch(world, -8, 6, Material.SHROOMLIGHT, 8, 5);
        this.scatterSurfacePatch(world, 10, 8, Material.GILDED_BLACKSTONE, 7, 5);
        world.getBlockAt(0, baseY + 1, 0).setType(markerMaterial, false);
        world.getBlockAt(-1, baseY + 2, 8).setType(ruinCoreMaterial, false);
    }

    private void generateTempest(final World world, final Material markerMaterial, final Material ruinCoreMaterial) {
        final int baseY = this.terrainPlateauY(world, 0, 0, 8) + 2;
        this.buildLandingPad(world, 0, baseY, 0, 7, Material.CUT_COPPER, Material.COPPER_BULB);
        this.buildRuinRing(world, 1, baseY + 1, 0, Material.WEATHERED_CUT_COPPER, Material.COPPER_BULB, 6);
        this.buildSpire(world, -9, this.surfaceY(world, -9, 7) + 1, 7, Material.WEATHERED_CUT_COPPER, 7);
        this.buildSpire(world, 11, this.surfaceY(world, 11, -6) + 1, -6, Material.OXIDIZED_CUT_COPPER, 6);
        this.scatterSurfacePatch(world, -7, 6, Material.LIGHTNING_ROD, 8, 5);
        this.scatterSurfacePatch(world, 8, 8, Material.SEA_LANTERN, 6, 5);
        world.getBlockAt(0, baseY + 1, 0).setType(markerMaterial, false);
        world.getBlockAt(1, baseY + 2, 7).setType(ruinCoreMaterial, false);
    }

    private void applyPlanetWorldSettings(final PlanetDefinition definition, final World world) {
        this.scheduler.runGlobal(task -> {
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            switch (definition.id()) {
                case "aurelia" -> {
                    world.setTime(2500L);
                    world.setStorm(false);
                    world.setThundering(false);
                }
                case "cryon" -> {
                    world.setTime(23000L);
                    world.setStorm(false);
                    world.setThundering(false);
                }
                case "nyx" -> {
                    world.setTime(23000L);
                    world.setStorm(false);
                    world.setThundering(false);
                }
                case "helion" -> {
                    world.setTime(4500L);
                    world.setStorm(false);
                    world.setThundering(false);
                }
                case "tempest" -> {
                    world.setTime(12800L);
                    world.setStorm(true);
                    world.setThundering(true);
                }
                default -> {
                    world.setTime(6000L);
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
                : "https://textures.minecraft.net/texture/" + trimmed;
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
        return outputs;
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
        final Entity entity = Bukkit.getEntity(entityId);
        if (entity != null && entity.isValid()) {
            if (this.plugin.isEnabled()) {
                this.scheduler.runEntity(entity, entity::remove);
            } else {
                entity.remove();
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
            inventory.setItem(centerRow * 9 + centerColumn, this.buildPlanetaryGateHoverItem(layout, viewer));
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
            this.showTravelTitle(player,
                    Component.text("▶ 發射", NamedTextColor.GREEN, TextDecoration.BOLD),
                    this.itemFactory.secondary("目的地：" + destination.displayName()),
                    2L, 16L, 4L);
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
        this.showTravelTitle(player,
                Component.text("▶ " + seconds, color, TextDecoration.BOLD),
                this.itemFactory.secondary("星門啟動倒數"),
                3L, 17L, 2L);
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
        final Location seatLocation = center.clone().add(sway * 0.55D, -1.15D + Math.abs(sway) * 0.12D, 0.0D);
        this.scheduler.runEntity(seat, () -> {
            if (!seat.isValid()) {
                return;
            }
            seat.teleportAsync(seatLocation);
            seat.setRotation(vessel.yaw(), 0.0f);
        });
        final ArmorStand cameraAnchor = vessel.cameraAnchor();
        if (cameraAnchor != null && cameraAnchor.isValid()) {
            final Location cameraLocation = this.travelCameraLocation(vessel, center, hatchProgress, ascentProgress, sway);
            this.scheduler.runEntity(cameraAnchor, () -> {
                if (!cameraAnchor.isValid()) {
                    return;
                }
                cameraAnchor.teleportAsync(cameraLocation);
                cameraAnchor.setRotation(vessel.yaw(), (float) Math.max(-18.0D, -2.0D - ascentProgress * 10.0D));
            });
            if (player != null && player.isValid() && player.getGameMode() == GameMode.SPECTATOR && player.getSpectatorTarget() != cameraAnchor) {
                this.scheduler.runEntity(player, () -> {
                    if (player.isValid() && player.getGameMode() == GameMode.SPECTATOR) {
                        player.setSpectatorTarget(cameraAnchor);
                    }
                });
            }
        }
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
            final BlockDisplay display = part.display();
            final Location displayLocation = center.clone().add(rotated);
            this.scheduler.runEntity(display, () -> {
                if (display.isValid()) {
                    display.teleportAsync(displayLocation);
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
        player.showTitle(Title.title(
                title,
                subtitle,
                Title.Times.times(
                        Duration.ofMillis(fadeInTicks * 50L),
                        Duration.ofMillis(stayTicks * 50L),
                        Duration.ofMillis(fadeOutTicks * 50L)
                )
        ));
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
            case "aurelia", AURELIA_WORLD, "d_techproject_aurelia_surface" -> "aurelia";
            case "cryon", CRYON_WORLD, "d_techproject_cryon_surface" -> "cryon";
            case "nyx", NYX_WORLD, "d_techproject_nyx_orbit" -> "nyx";
            case "helion", HELION_WORLD, "d_techproject_helion_surface" -> "helion";
            case "tempest", TEMPEST_WORLD, "d_techproject_tempest_surface" -> "tempest";
            default -> null;
        };
    }

    private static final class PlanetChunkGenerator extends ChunkGenerator {
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