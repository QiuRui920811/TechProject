package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.model.MachineDefinition;
import com.rui.techproject.model.MachineRecipe;
import com.rui.techproject.model.MachineRuntimeState;
import com.rui.techproject.model.PlacedMachine;
import com.rui.techproject.storage.StorageBackend;
import com.rui.techproject.util.ItemStackSerializer;
import com.rui.techproject.util.LocationKey;
import com.rui.techproject.util.SafeScheduler;
import com.rui.techproject.util.ItemFactoryUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.util.RayTraceResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class MachineService {
    private static final String MACHINE_TITLE_PREFIX = "機器:";
    private static final String RECIPE_TITLE_PREFIX = "機器配方:";
    private static final int[] INPUT_SLOTS = {0, 1, 2, 3, 4, 5, 6, 7, 8};
    private static final int[] OUTPUT_SLOTS = {27, 28, 29, 30, 31, 32, 33, 34, 35};
    private static final int[] UPGRADE_SLOTS = {39, 40, 41};
    private static final String[] DIRECTION_ORDER = {"ALL", "NORTH", "EAST", "SOUTH", "WEST", "UP", "DOWN"};
    private static final int ANDROID_SCRIPT_SLOT = 1;
    private static final int ANDROID_FILTER_SLOT = 0;
    private static final int ANDROID_MAX_FUEL = 256;
    private static final int ANDROID_LINK_RADIUS = 4;
    private static final int ANDROID_INTERFACE_LOW_WATERMARK = 32;
    private static final String[] ANDROID_ROUTE_MODES = {"SERPENTINE", "SPIRAL", "RING"};
    private static final String ANDROID_OVERCLOCK_CARD = "android_overclock_card";
    private static final String ANDROID_EFFICIENCY_CARD = "android_efficiency_card";
    private static final String ANDROID_EXPANSION_CARD = "android_expansion_card";

    private enum AndroidScriptProfile {
        HARVEST("android_harvest_script", "農務程序", 8L, 1, 8L),
        LOGGING("android_logging_script", "伐木程序", 12L, 2, 12L),
        SALVAGE("android_salvage_script", "回收程序", 6L, 1, 6L),
        PLANET("android_planet_script", "行星採樣程序", 10L, 2, 14L),
        HUNT(null, "獵捕程序", 10L, 2, 16L);

        private final String itemId;
        private final String displayName;
        private final long intervalTicks;
        private final int fuelCost;
        private final long baseEnergyCost;

        AndroidScriptProfile(final String itemId,
                             final String displayName,
                             final long intervalTicks,
                             final int fuelCost,
                             final long baseEnergyCost) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.intervalTicks = intervalTicks;
            this.fuelCost = fuelCost;
            this.baseEnergyCost = baseEnergyCost;
        }

        public String displayName() {
            return this.displayName;
        }

        public long intervalTicks() {
            return this.intervalTicks;
        }

        public int fuelCost() {
            return this.fuelCost;
        }

        public long baseEnergyCost() {
            return this.baseEnergyCost;
        }

        public static AndroidScriptProfile fromItemId(final String itemId) {
            if (itemId == null || itemId.isBlank()) {
                return null;
            }
            for (final AndroidScriptProfile profile : values()) {
                if (profile.itemId != null && profile.itemId.equalsIgnoreCase(itemId)) {
                    return profile;
                }
            }
            return null;
        }
    }

    private record AndroidFuelProfile(int fuelUnits, ItemStack remainder) {
    }

    private final TechProjectPlugin plugin;
    private final TechRegistry registry;
    private final PlayerProgressService progressService;
    private final AchievementService achievementService;
    private final SafeScheduler scheduler;
    private final ItemFactoryUtil itemFactory;
    private final TechCropService techCropService;
    private final ChickenGeneticsService chickenGenetics = new ChickenGeneticsService();
    private final Map<LocationKey, PlacedMachine> machines = new ConcurrentHashMap<>();
    private final Map<UUID, MachineViewSession> openViews = new ConcurrentHashMap<>();
    private final Map<LocationKey, java.util.Set<UUID>> viewersByMachine = new ConcurrentHashMap<>();
    private final Map<LocationKey, UUID> machineDisplays = new ConcurrentHashMap<>();
    private final Map<LocationKey, UUID> machineVisualDisplays = new ConcurrentHashMap<>();
    private final java.util.Set<LocationKey> quarryBusy = ConcurrentHashMap.newKeySet();
    private final java.util.Set<LocationKey> quarryWarmedUp = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BossBar> machineBossBars = new ConcurrentHashMap<>();
    private final Map<UUID, LocationKey> playerLookingAt = new ConcurrentHashMap<>();
    private StorageBackend storageBackend;

    private record MachineViewSession(LocationKey locationKey, ViewMode mode, int page, Inventory inventory) {
    }

    private enum ViewMode {
        MAIN,
        RECIPES
    }

    private enum MachineGuiTheme {
        GENERATOR,
        LOGISTICS,
        FIELD,
        PROCESSOR
    }

    private void trackViewOpen(final UUID uuid, final LocationKey key, final MachineViewSession session) {
        final MachineViewSession old = this.openViews.put(uuid, session);
        if (old != null && !key.equals(old.locationKey())) {
            final java.util.Set<UUID> prev = this.viewersByMachine.get(old.locationKey());
            if (prev != null) {
                prev.remove(uuid);
            }
        }
        this.viewersByMachine.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(uuid);
    }

    private void trackViewClose(final UUID uuid) {
        final MachineViewSession old = this.openViews.remove(uuid);
        if (old != null) {
            final java.util.Set<UUID> set = this.viewersByMachine.get(old.locationKey());
            if (set != null) {
                set.remove(uuid);
            }
        }
    }

    private record MachineLayoutSpec(String inputZone,
                                     String outputZone,
                                     String upgradeZone,
                                     String recipeTitle,
                                     String inputDirectionTitle,
                                     String outputDirectionTitle) {
    }

    private record QuarryRigLayout(Block core,
                                   Block chest,
                                   Block supportA,
                                   Block supportB,
                                   Block machineA,
                                   Block machineB) {
    }

    private record CrusherRigLayout(Block core,
                                    Block grate) {
    }

    private record CompressorRigLayout(Block core,
                                       Block pressPlate) {
    }

    private record OreWasherRigLayout(Block core,
                                      Block lever) {
    }

    private record WireMillRigLayout(Block core,
                                     Block ironBars) {
    }

    private record PurifierRigLayout(Block core,
                                     Block button) {
    }

    private record CentrifugeRigLayout(Block core,
                                       Block plate) {
    }

    private record BioLabRigLayout(Block core,
                                   Block hook) {
    }

    private record ChemReactorRigLayout(Block core,
                                        Block comparator) {
    }

    private record RecipeRuntimeSnapshot(MachineRuntimeState state, String detail) {
    }

    public MachineService(final TechProjectPlugin plugin,
                          final TechRegistry registry,
                          final PlayerProgressService progressService,
                          final AchievementService achievementService,
                          final SafeScheduler scheduler,
                          final ItemFactoryUtil itemFactory,
                          final TechCropService techCropService) {
        this.plugin = plugin;
        this.registry = registry;
        this.progressService = progressService;
        this.achievementService = achievementService;
        this.scheduler = scheduler;
        this.itemFactory = itemFactory;
        this.techCropService = techCropService;
    }

    public void setStorageBackend(final StorageBackend backend) {
        this.storageBackend = backend;
        this.loadMachines();
    }

    public void start() {
        final long interval = Math.max(1L, this.plugin.getConfig().getLong("machine-tick-interval", 20L));
        this.scheduler.runGlobalTimer(task -> this.tickAllMachines(), interval, interval);
        this.scheduler.runGlobalTimer(task -> this.tickMachineLookAt(), 4L, 4L);
    }

    public void registerPlacedMachine(final Player player, final Block block, final String machineId, final ItemStack sourceStack) {
        final MachineDefinition definition = this.registry.getMachine(machineId);
        if (definition == null) {
            return;
        }

        final LocationKey key = LocationKey.from(block.getLocation());
        final PlacedMachine placedMachine = new PlacedMachine(key, machineId, player.getUniqueId());
        placedMachine.setStoredEnergy(this.itemFactory.getStoredMachineEnergy(sourceStack));
        this.clampMachineEnergy(placedMachine);
        if (this.isQuarryLike(machineId)) {
            placedMachine.setEnabled(false);
        }
        this.machines.put(key, placedMachine);
        this.progressService.unlockMachine(player.getUniqueId(), machineId);
        this.progressService.unlockByRequirement(player.getUniqueId(), machineId);
        this.progressService.unlockByRequirement(player.getUniqueId(), "machine:" + machineId);
        this.progressService.incrementStat(player.getUniqueId(), "machines_placed", 1);
        final long activeMachines = this.progressService.incrementStat(player.getUniqueId(), "active_machines", 1);
        this.progressService.setStatMax(player.getUniqueId(), "max_active_machines", activeMachines);
        this.achievementService.evaluate(player.getUniqueId());
        this.spawnMachineAppearance(this.machines.get(key), definition, block.getLocation());
        final String summary = this.placementHintSummary(machineId);
        if (!summary.isBlank()) {
            player.sendMessage(this.itemFactory.secondary("擺放提示：" + summary));
        }
    }

    public void unregisterMachine(final Block block) {
        final LocationKey key = LocationKey.from(block.getLocation());
        final PlacedMachine removed = this.machines.remove(key);
        if (removed != null) {
            if (this.isQuarryLike(removed.machineId())) {
                this.quarryBusy.remove(removed.locationKey());
                this.quarryWarmedUp.remove(removed.locationKey());
            }
            this.removeMachineDisplay(key);
            this.dropStoredItems(block.getLocation(), removed.inputInventory());
            this.dropStoredItems(block.getLocation(), removed.outputInventory());
            this.dropStoredItems(block.getLocation(), removed.upgradeInventory());
            final long activeMachines = Math.max(0L, this.progressService.incrementStat(removed.owner(), "active_machines", -1));
            this.progressService.setStatMax(removed.owner(), "max_active_machines", activeMachines);
        }
    }

    public int machineCount() {
        return this.machines.size();
    }

    public boolean isManagedMachine(final Block block) {
        return this.machines.containsKey(LocationKey.from(block.getLocation()));
    }

    public Block resolveManagedMachineBlock(final Block block) {
        if (block == null) {
            return null;
        }
        if (this.isManagedMachine(block)) {
            final PlacedMachine machine = this.machines.get(LocationKey.from(block.getLocation()));
            if (machine != null && this.isQuarryLike(machine.machineId())) {
                final QuarryRigLayout layout = this.quarryRigLayout(block);
                if (layout != null) {
                    return this.primaryQuarryMachine(layout);
                }
            }
            return block;
        }
        final Block crusherCore = this.crusherCoreCandidate(block);
        if (crusherCore != null) {
            return crusherCore;
        }
        final Block compressorCore = this.compressorCoreCandidate(block);
        if (compressorCore != null) {
            return compressorCore;
        }
        final Block oreWasherCore = this.oreWasherCoreCandidate(block);
        if (oreWasherCore != null) {
            return oreWasherCore;
        }
        final Block wireMillCore = this.wireMillCoreCandidate(block);
        if (wireMillCore != null) {
            return wireMillCore;
        }
        final Block purifierCore = this.purifierCoreCandidate(block);
        if (purifierCore != null) {
            return purifierCore;
        }
        final Block centrifugeCore = this.centrifugeCoreCandidate(block);
        if (centrifugeCore != null) {
            return centrifugeCore;
        }
        final Block bioLabCore = this.bioLabCoreCandidate(block);
        if (bioLabCore != null) {
            return bioLabCore;
        }
        final Block chemReactorCore = this.chemReactorCoreCandidate(block);
        if (chemReactorCore != null) {
            return chemReactorCore;
        }
        final QuarryRigLayout layout = this.quarryRigLayout(block);
        if (layout != null) {
            return this.primaryQuarryMachine(layout);
        }
        final Block candidateCore = this.quarryCoreCandidate(block);
        return candidateCore == null ? null : this.quarryControlMachine(candidateCore);
    }

    public ItemStack buildPlacedMachineItem(final Block block) {
        final PlacedMachine machine = this.machines.get(LocationKey.from(block.getLocation()));
        if (machine == null) {
            return null;
        }
        final MachineDefinition definition = this.registry.getMachine(machine.machineId());
        return definition == null ? null : this.itemFactory.buildMachineItem(definition, machine.storedEnergy());
    }

    public PlacedMachine placedMachineAt(final Block block) {
        if (block == null) {
            return null;
        }
        return this.machines.get(LocationKey.from(block.getLocation()));
    }

    public void openMachineMenu(final Player player, final Block block) {
        final LocationKey key = LocationKey.from(block.getLocation());
        this.openMachineMenu(player, key);
    }

    public boolean handleManagedMachineInteract(final Player player, final Block block) {
        return this.handleManagedMachineInteract(player, block, block);
    }

    public boolean handleManagedMachineInteract(final Player player, final Block block, final Block interactedBlock) {
        final PlacedMachine machine = this.machines.get(LocationKey.from(block.getLocation()));
        if (machine == null) {
            return false;
        }
        if (this.normalizeId(machine.machineId()).equals("planetary_gate")) {
            this.absorbNearbyEnergy(machine, block.getLocation(), 32L);
            return this.plugin.getPlanetService().handlePlanetaryGateInteract(player, block, machine);
        }
        if (this.isQuarryLike(machine.machineId())) {
            final QuarryRigLayout layout = this.quarryRigLayout(block);
            final Block controlBlock = layout == null ? block : this.primaryQuarryMachine(layout);
            final PlacedMachine controlMachine = this.machines.get(LocationKey.from(controlBlock.getLocation()));
            if (controlMachine == null) {
                return false;
            }
            if (player.isSneaking()) {
                this.openMachineMenu(player, controlBlock);
                return true;
            }
            final boolean enabled = !controlMachine.enabled();
            this.setQuarryRigEnabled(layout, enabled);
            if (layout != null) {
                this.refreshQuarryDisplays(layout);
            } else {
                this.refreshMachineDisplayNow(controlMachine.locationKey());
            }
            player.sendMessage(enabled
                    ? this.itemFactory.success("採礦鑽機已啟動，會開始抽取目前區塊往下的礦物。")
                    : this.itemFactory.warning("採礦鑽機已停止。"));
            return true;
        }
        if (this.isManualCrusher(machine.machineId())) {
            if (player.isSneaking()) {
                this.openMachineMenu(player, block);
                return true;
            }
            if (!this.isCrusherControlBlock(interactedBlock, block)) {
                player.sendMessage(this.itemFactory.secondary("請右鍵上方鐵柵欄啟動粉碎；蹲下 + 右鍵核心可打開介面。"));
                return true;
            }
            return this.tryStartManualCrusher(player, machine, block);
        }
        if (this.isManualCompressor(machine.machineId())) {
            if (player.isSneaking()) {
                this.openMachineMenu(player, block);
                return true;
            }
            if (!this.isCompressorControlBlock(interactedBlock, block)) {
                player.sendMessage(this.itemFactory.secondary("請右鍵上方重型壓力板啟動壓縮；蹲下 + 右鍵核心可打開介面。"));
                return true;
            }
            return this.tryStartManualCompressor(player, machine, block);
        }
        if (this.isManualOreWasher(machine.machineId())) {
            if (player.isSneaking()) {
                this.openMachineMenu(player, block);
                return true;
            }
            if (!this.isOreWasherControlBlock(interactedBlock, block)) {
                player.sendMessage(this.itemFactory.secondary("請右鍵上方拉桿啟動洗礦；蹲下 + 右鍵核心可打開介面。"));
                return true;
            }
            return this.tryStartManualOreWasher(player, machine, block);
        }
        if (this.isManualWireMill(machine.machineId())) {
            if (player.isSneaking()) {
                this.openMachineMenu(player, block);
                return true;
            }
            if (!this.isWireMillControlBlock(interactedBlock, block)) {
                player.sendMessage(this.itemFactory.secondary("請右鍵上方鐵柵欄啟動拉線；蹲下 + 右鍵核心可打開介面。"));
                return true;
            }
            return this.tryStartManualWireMill(player, machine, block);
        }
        if (this.isManualPurifier(machine.machineId())) {
            if (player.isSneaking()) {
                this.openMachineMenu(player, block);
                return true;
            }
            if (!this.isPurifierControlBlock(interactedBlock, block)) {
                player.sendMessage(this.itemFactory.secondary("請右鍵上方石按鈕啟動淨化；蹲下 + 右鍵核心可打開介面。"));
                return true;
            }
            return this.tryStartManualPurifier(player, machine, block);
        }
        if (this.isManualCentrifuge(machine.machineId())) {
            if (player.isSneaking()) {
                this.openMachineMenu(player, block);
                return true;
            }
            if (!this.isCentrifugeControlBlock(interactedBlock, block)) {
                player.sendMessage(this.itemFactory.secondary("請右鍵上方輕型壓力板啟動離心；蹲下 + 右鍵核心可打開介面。"));
                return true;
            }
            return this.tryStartManualCentrifuge(player, machine, block);
        }
        if (this.isManualBioLab(machine.machineId())) {
            if (player.isSneaking()) {
                this.openMachineMenu(player, block);
                return true;
            }
            if (!this.isBioLabControlBlock(interactedBlock, block)) {
                player.sendMessage(this.itemFactory.secondary("請右鍵上方絆線鉤啟動培養；蹲下 + 右鍵核心可打開介面。"));
                return true;
            }
            return this.tryStartManualBioLab(player, machine, block);
        }
        if (this.isManualChemReactor(machine.machineId())) {
            if (player.isSneaking()) {
                this.openMachineMenu(player, block);
                return true;
            }
            if (!this.isChemReactorControlBlock(interactedBlock, block)) {
                player.sendMessage(this.itemFactory.secondary("請右鍵上方比較器啟動反應；蹲下 + 右鍵核心可打開介面。"));
                return true;
            }
            return this.tryStartManualChemReactor(player, machine, block);
        }
        this.openMachineMenu(player, block);
        return true;
    }

    public boolean suppressRedstoneOutput(final Block block) {
        return this.resolveManagedMachineBlock(block) != null;
    }

    public boolean canModifyMachine(final Player player, final Block block, final boolean breaking) {
        final PlacedMachine machine = this.machines.get(LocationKey.from(block.getLocation()));
        if (machine == null || !this.plugin.getConfig().getBoolean("machine-safety.restrict-machine-access", true)) {
            return true;
        }
        if (player.hasPermission("techproject.admin") || machine.owner().equals(player.getUniqueId())) {
            return true;
        }
        player.sendMessage(this.itemFactory.danger(breaking ? "這台機器不是你的，不能拆除。" : "這台機器不是你的，不能打開。"));
        return false;
    }

    public boolean validatePlacement(final Player player, final Block block, final String machineId) {
        if (!this.canUseMachineType(player, machineId, true)) {
            return false;
        }
        if (this.isWorldInteractionMachine(machineId) && !this.isAllowedWorld(block.getWorld())) {
            player.sendMessage(this.itemFactory.danger("這個世界目前停用戶外互動型機器。"));
            return false;
        }
        if ("fishing_dock".equalsIgnoreCase(machineId) && this.countAdjacentWater(block.getWorld(), block.getLocation()) < 3) {
            player.sendMessage(this.itemFactory.warning("自動釣台旁邊至少要有 3 格水面，否則不會運作。"));
            return false;
        }
        if (("solar_generator".equalsIgnoreCase(machineId) || "storm_turbine".equalsIgnoreCase(machineId))
                && block.getWorld().getHighestBlockYAt(block.getX(), block.getZ()) > block.getY()) {
            player.sendMessage(this.itemFactory.warning("這台最好放在露天，否則發電效率會很差。"));
        }
        if ("crop_harvester".equalsIgnoreCase(machineId) && !this.hasFarmlandNearby(block, 2)) {
            player.sendMessage(this.itemFactory.warning("作物收割機建議與農地同高度放置。"));
        }
        if ("planetary_harvester".equalsIgnoreCase(machineId) && !this.plugin.getPlanetService().isPlanetWorld(block.getWorld())) {
            player.sendMessage(this.itemFactory.danger("行星採集機只能放在星球世界中使用。"));
            return false;
        }
        if (this.isQuarryLike(machineId)) {
            player.sendMessage(this.itemFactory.warning("採礦鑽機新結構：中心放原版高爐、上方放箱子、同一軸兩側放鐵方塊，鐵方塊上方各放一台採礦鑽機。"));
            player.sendMessage(this.itemFactory.secondary("燃料直接放上方箱子，右鍵中央高爐啟動；蹲下 + 右鍵中央高爐才會打開設定介面。"));
        }
        if (this.isManualCrusher(machineId)) {
            player.sendMessage(this.itemFactory.warning("粉碎機現在採手動結構：核心放粉碎機本體，上方加鐵柵欄作為投料口。"));
            player.sendMessage(this.itemFactory.secondary("右鍵鐵柵欄才會啟動粉碎；蹲下 + 右鍵核心可以打開設定介面。"));
        }
        if (this.isManualCompressor(machineId)) {
            player.sendMessage(this.itemFactory.warning("壓縮機現在採手動結構：核心放壓縮機本體，上方放重型壓力板作為壓床。"));
            player.sendMessage(this.itemFactory.secondary("右鍵壓力板才會啟動壓縮；蹲下 + 右鍵核心可以打開設定介面。"));
        }
        if (this.isManualOreWasher(machineId)) {
            player.sendMessage(this.itemFactory.warning("洗礦機現在採手動結構：核心放洗礦機本體，上方裝拉桿作為攪拌控制。"));
            player.sendMessage(this.itemFactory.secondary("右鍵拉桿才會啟動洗礦；蹲下 + 右鍵核心可以打開設定介面。"));
        }
        if (this.isManualWireMill(machineId)) {
            player.sendMessage(this.itemFactory.warning("拉線機現在採手動結構：核心放拉線機本體，上方加鐵柵欄作為牽引把手。"));
            player.sendMessage(this.itemFactory.secondary("右鍵鐵柵欄才會啟動拉線；蹲下 + 右鍵核心可以打開設定介面。"));
        }
        if (this.isManualPurifier(machineId)) {
            player.sendMessage(this.itemFactory.warning("淨化器現在採手動結構：核心放淨化器本體，上方裝石按鈕作為脈衝淨化控制。"));
            player.sendMessage(this.itemFactory.secondary("右鍵石按鈕才會啟動淨化；蹲下 + 右鍵核心可以打開設定介面。"));
        }
        if (this.isManualCentrifuge(machineId)) {
            player.sendMessage(this.itemFactory.warning("離心機現在採手動結構：核心放離心機本體，上方放輕型壓力板作為旋轉啟動盤。"));
            player.sendMessage(this.itemFactory.secondary("右鍵壓力板才會啟動離心；蹲下 + 右鍵核心可以打開設定介面。"));
        }
        if (this.isManualBioLab(machineId)) {
            player.sendMessage(this.itemFactory.warning("生質實驗室現在採手動結構：核心放釀造台，上方裝絆線鉤作為反應啟動器。"));
            player.sendMessage(this.itemFactory.secondary("右鍵絆線鉤才會啟動培養；蹲下 + 右鍵核心可以打開介面。"));
        }
        if (this.isManualChemReactor(machineId)) {
            player.sendMessage(this.itemFactory.warning("化學反應器現在採手動結構：核心放信標，上方裝比較器作為反應觸發器。"));
            player.sendMessage(this.itemFactory.secondary("右鍵比較器才會啟動反應；蹲下 + 右鍵核心可以打開介面。"));
        }
        if (this.isElectricAutoMachine(machineId)) {
            player.sendMessage(this.itemFactory.success("純電力版機器已放置，接上電源後會全自動加工。右鍵即可打開介面。"));
        }
        return true;
    }

    private boolean tryStartManualCrusher(final Player player, final PlacedMachine machine, final Block machineBlock) {
        final CrusherRigLayout layout = this.crusherRigLayout(machineBlock);
        if (layout == null) {
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方鐵柵欄");
            player.sendMessage(this.itemFactory.warning("粉碎機缺少結構：請在核心正上方放鐵柵欄後再啟動。"));
            return true;
        }
        if (machine.hasManualOperation()) {
            player.sendMessage(this.itemFactory.warning(machine.manualOperationTicks() > 0 ? "粉碎機正在震動運轉中。" : "粉碎機已完成本輪，請先清空輸出。"));
            return true;
        }
        final List<MachineRecipe> recipes = this.effectiveRecipes(machine);
        if (recipes.isEmpty()) {
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待互動");
            player.sendMessage(this.itemFactory.warning("這台粉碎機目前沒有可用配方。"));
            return true;
        }
        for (final MachineRecipe recipe : recipes) {
            if (!this.canCraftRecipe(machine, recipe)) {
                continue;
            }
            final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
            if (output == null) {
                this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
                player.sendMessage(this.itemFactory.warning("目前無法初始化粉碎配方產物。"));
                return true;
            }
            if (!this.canStoreOutput(machine, output)) {
                this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
                player.sendMessage(this.itemFactory.warning("粉碎機輸出已滿，先清掉成品再啟動。"));
                return true;
            }
            final long requiredEnergy = this.effectiveEnergyCost(machine, recipe.energyCost());
            this.absorbNearbyEnergy(machine, machineBlock.getLocation(), requiredEnergy);
            if (!machine.consumeEnergy(requiredEnergy)) {
                this.setRuntimeState(machine, MachineRuntimeState.NO_POWER, "缺少電力");
                player.sendMessage(this.itemFactory.warning("粉碎機電力不足，先把能源接過來。"));
                return true;
            }
            this.consumeRecipeInputs(machine, recipe);
            machine.setManualOperationRecipeId(recipe.id());
            machine.setManualOperationTicks(this.manualCrusherDuration(machine, recipe));
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "人工作業中 " + machine.manualOperationTicks() + " 秒");
            final World world = machineBlock.getWorld();
            world.spawnParticle(Particle.CRIT, layout.grate().getLocation().clone().add(0.5, 0.45, 0.5), 12, 0.18, 0.08, 0.18, 0.01);
            world.playSound(layout.grate().getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.25f, 1.35f);
            player.sendMessage(this.itemFactory.success("你啟動了粉碎機，鐵柵欄開始震動。"));
            return true;
        }
        final RecipeRuntimeSnapshot snapshot = this.inspectManualCrusherRuntime(machine);
        this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
        player.sendMessage(this.itemFactory.warning(snapshot.detail()));
        return true;
    }

    private int manualCrusherDuration(final PlacedMachine machine, final MachineRecipe recipe) {
        return Math.max(2, recipe.energyCost() - this.countUpgrade(machine, "speed_upgrade"));
    }

    private boolean tryStartManualCompressor(final Player player, final PlacedMachine machine, final Block machineBlock) {
        final CompressorRigLayout layout = this.compressorRigLayout(machineBlock);
        if (layout == null) {
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方重型壓力板");
            player.sendMessage(this.itemFactory.warning("壓縮機缺少結構：請在核心正上方放重型壓力板後再啟動。"));
            return true;
        }
        if (machine.hasManualOperation()) {
            player.sendMessage(this.itemFactory.warning(machine.manualOperationTicks() > 0 ? "壓縮機正在反覆下壓。" : "壓縮機已完成本輪，請先清空輸出。"));
            return true;
        }
        for (final MachineRecipe recipe : this.effectiveRecipes(machine)) {
            if (!this.canCraftRecipe(machine, recipe)) {
                continue;
            }
            final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
            if (output == null) {
                this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
                player.sendMessage(this.itemFactory.warning("目前無法初始化壓縮配方產物。"));
                return true;
            }
            if (!this.canStoreOutput(machine, output)) {
                this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
                player.sendMessage(this.itemFactory.warning("壓縮機輸出已滿，先清掉成品再啟動。"));
                return true;
            }
            final long requiredEnergy = this.effectiveEnergyCost(machine, recipe.energyCost());
            this.absorbNearbyEnergy(machine, machineBlock.getLocation(), requiredEnergy);
            if (!machine.consumeEnergy(requiredEnergy)) {
                this.setRuntimeState(machine, MachineRuntimeState.NO_POWER, "缺少電力");
                player.sendMessage(this.itemFactory.warning("壓縮機電力不足，先把能源接過來。"));
                return true;
            }
            this.consumeRecipeInputs(machine, recipe);
            machine.setManualOperationRecipeId(recipe.id());
            machine.setManualOperationTicks(this.manualCompressorDuration(machine, recipe));
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "壓製中 " + machine.manualOperationTicks() + " 秒");
            final World world = machineBlock.getWorld();
            world.spawnParticle(Particle.BLOCK, layout.pressPlate().getLocation().clone().add(0.5, 0.12, 0.5), 10, 0.18, 0.02, 0.18, Material.HEAVY_WEIGHTED_PRESSURE_PLATE.createBlockData());
            world.playSound(layout.pressPlate().getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.25f, 0.85f);
            player.sendMessage(this.itemFactory.success("你啟動了壓縮機，壓床開始反覆下壓。"));
            return true;
        }
        final RecipeRuntimeSnapshot snapshot = this.inspectManualCompressorRuntime(machine);
        this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
        player.sendMessage(this.itemFactory.warning(snapshot.detail()));
        return true;
    }

    private boolean tryStartManualOreWasher(final Player player, final PlacedMachine machine, final Block machineBlock) {
        final OreWasherRigLayout layout = this.oreWasherRigLayout(machineBlock);
        if (layout == null) {
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方拉桿");
            player.sendMessage(this.itemFactory.warning("洗礦機缺少結構：請在核心正上方裝拉桿後再啟動。"));
            return true;
        }
        if (machine.hasManualOperation()) {
            player.sendMessage(this.itemFactory.warning(machine.manualOperationTicks() > 0 ? "洗礦機正在翻攪礦漿。" : "洗礦機已完成本輪，請先清空輸出。"));
            return true;
        }
        for (final MachineRecipe recipe : this.effectiveRecipes(machine)) {
            if (!this.canCraftRecipe(machine, recipe)) {
                continue;
            }
            final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
            if (output == null) {
                this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
                player.sendMessage(this.itemFactory.warning("目前無法初始化洗礦配方產物。"));
                return true;
            }
            if (!this.canStoreOutput(machine, output)) {
                this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
                player.sendMessage(this.itemFactory.warning("洗礦機輸出已滿，先清掉成品再啟動。"));
                return true;
            }
            final long requiredEnergy = this.effectiveEnergyCost(machine, recipe.energyCost());
            this.absorbNearbyEnergy(machine, machineBlock.getLocation(), requiredEnergy);
            if (!machine.consumeEnergy(requiredEnergy)) {
                this.setRuntimeState(machine, MachineRuntimeState.NO_POWER, "缺少電力");
                player.sendMessage(this.itemFactory.warning("洗礦機電力不足，先把能源接過來。"));
                return true;
            }
            this.consumeRecipeInputs(machine, recipe);
            machine.setManualOperationRecipeId(recipe.id());
            machine.setManualOperationTicks(this.manualOreWasherDuration(machine, recipe));
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "洗礦中 " + machine.manualOperationTicks() + " 秒");
            final World world = machineBlock.getWorld();
            world.spawnParticle(Particle.SPLASH, layout.lever().getLocation().clone().add(0.5, 0.35, 0.5), 12, 0.18, 0.12, 0.18, 0.05);
            world.playSound(layout.lever().getLocation(), Sound.BLOCK_LEVER_CLICK, 0.35f, 0.8f);
            player.sendMessage(this.itemFactory.success("你啟動了洗礦機，洗槽開始翻攪。"));
            return true;
        }
        final RecipeRuntimeSnapshot snapshot = this.inspectManualOreWasherRuntime(machine);
        this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
        player.sendMessage(this.itemFactory.warning(snapshot.detail()));
        return true;
    }

    private int manualCompressorDuration(final PlacedMachine machine, final MachineRecipe recipe) {
        return Math.max(3, recipe.energyCost() + 1 - this.countUpgrade(machine, "speed_upgrade"));
    }

    private int manualOreWasherDuration(final PlacedMachine machine, final MachineRecipe recipe) {
        return Math.max(2, recipe.energyCost() - this.countUpgrade(machine, "speed_upgrade"));
    }

    private boolean tryStartManualWireMill(final Player player, final PlacedMachine machine, final Block machineBlock) {
        final WireMillRigLayout layout = this.wireMillRigLayout(machineBlock);
        if (layout == null) {
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方鐵柵欄");
            player.sendMessage(this.itemFactory.warning("拉線機缺少結構：請在核心正上方放鐵柵欄後再啟動。"));
            return true;
        }
        if (machine.hasManualOperation()) {
            player.sendMessage(this.itemFactory.warning(machine.manualOperationTicks() > 0 ? "拉線機正在持續牽引。" : "拉線機已完成本輪，請先清空輸出。"));
            return true;
        }
        for (final MachineRecipe recipe : this.effectiveRecipes(machine)) {
            if (!this.canCraftRecipe(machine, recipe)) {
                continue;
            }
            final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
            if (output == null) {
                this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
                player.sendMessage(this.itemFactory.warning("目前無法初始化拉線配方產物。"));
                return true;
            }
            if (!this.canStoreOutput(machine, output)) {
                this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
                player.sendMessage(this.itemFactory.warning("拉線機輸出已滿，先清掉成品再啟動。"));
                return true;
            }
            final long requiredEnergy = this.effectiveEnergyCost(machine, recipe.energyCost());
            this.absorbNearbyEnergy(machine, machineBlock.getLocation(), requiredEnergy);
            if (!machine.consumeEnergy(requiredEnergy)) {
                this.setRuntimeState(machine, MachineRuntimeState.NO_POWER, "缺少電力");
                player.sendMessage(this.itemFactory.warning("拉線機電力不足，先把能源接過來。"));
                return true;
            }
            this.consumeRecipeInputs(machine, recipe);
            machine.setManualOperationRecipeId(recipe.id());
            machine.setManualOperationTicks(this.manualWireMillDuration(machine, recipe));
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "牽引中 " + machine.manualOperationTicks() + " 秒");
            final World world = machineBlock.getWorld();
            world.spawnParticle(Particle.CRIT, layout.ironBars().getLocation().clone().add(0.5, 0.5, 0.5), 10, 0.12, 0.2, 0.12, 0.01);
            world.playSound(layout.ironBars().getLocation(), Sound.BLOCK_CHAIN_HIT, 0.3f, 1.2f);
            player.sendMessage(this.itemFactory.success("你啟動了拉線機，牽引架開始高速拉動。"));
            return true;
        }
        final RecipeRuntimeSnapshot snapshot = this.inspectManualWireMillRuntime(machine);
        this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
        player.sendMessage(this.itemFactory.warning(snapshot.detail()));
        return true;
    }

    private boolean tryStartManualPurifier(final Player player, final PlacedMachine machine, final Block machineBlock) {
        final PurifierRigLayout layout = this.purifierRigLayout(machineBlock);
        if (layout == null) {
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方石按鈕");
            player.sendMessage(this.itemFactory.warning("淨化器缺少結構：請在核心正上方裝石按鈕後再啟動。"));
            return true;
        }
        if (machine.hasManualOperation()) {
            player.sendMessage(this.itemFactory.warning(machine.manualOperationTicks() > 0 ? "淨化器正在脈衝過濾。" : "淨化器已完成本輪，請先清空輸出。"));
            return true;
        }
        for (final MachineRecipe recipe : this.effectiveRecipes(machine)) {
            if (!this.canCraftRecipe(machine, recipe)) {
                continue;
            }
            final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
            if (output == null) {
                this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
                player.sendMessage(this.itemFactory.warning("目前無法初始化淨化配方產物。"));
                return true;
            }
            if (!this.canStoreOutput(machine, output)) {
                this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
                player.sendMessage(this.itemFactory.warning("淨化器輸出已滿，先清掉成品再啟動。"));
                return true;
            }
            final long requiredEnergy = this.effectiveEnergyCost(machine, recipe.energyCost());
            this.absorbNearbyEnergy(machine, machineBlock.getLocation(), requiredEnergy);
            if (!machine.consumeEnergy(requiredEnergy)) {
                this.setRuntimeState(machine, MachineRuntimeState.NO_POWER, "缺少電力");
                player.sendMessage(this.itemFactory.warning("淨化器電力不足，先把能源接過來。"));
                return true;
            }
            this.consumeRecipeInputs(machine, recipe);
            machine.setManualOperationRecipeId(recipe.id());
            machine.setManualOperationTicks(this.manualPurifierDuration(machine, recipe));
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "淨化中 " + machine.manualOperationTicks() + " 秒");
            final World world = machineBlock.getWorld();
            world.spawnParticle(Particle.BUBBLE, layout.button().getLocation().clone().add(0.5, 0.35, 0.5), 10, 0.16, 0.12, 0.16, 0.03);
            world.playSound(layout.button().getLocation(), Sound.BLOCK_STONE_BUTTON_CLICK_ON, 0.35f, 1.1f);
            player.sendMessage(this.itemFactory.success("你啟動了淨化器，過濾槽開始脈衝淨化。"));
            return true;
        }
        final RecipeRuntimeSnapshot snapshot = this.inspectManualPurifierRuntime(machine);
        this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
        player.sendMessage(this.itemFactory.warning(snapshot.detail()));
        return true;
    }

    private boolean tryStartManualCentrifuge(final Player player, final PlacedMachine machine, final Block machineBlock) {
        final CentrifugeRigLayout layout = this.centrifugeRigLayout(machineBlock);
        if (layout == null) {
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方輕型壓力板");
            player.sendMessage(this.itemFactory.warning("離心機缺少結構：請在核心正上方放輕型壓力板後再啟動。"));
            return true;
        }
        if (machine.hasManualOperation()) {
            player.sendMessage(this.itemFactory.warning(machine.manualOperationTicks() > 0 ? "離心機正在高速旋轉。" : "離心機已完成本輪，請先清空輸出。"));
            return true;
        }
        for (final MachineRecipe recipe : this.effectiveRecipes(machine)) {
            if (!this.canCraftRecipe(machine, recipe)) {
                continue;
            }
            final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
            if (output == null) {
                this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
                player.sendMessage(this.itemFactory.warning("目前無法初始化離心配方產物。"));
                return true;
            }
            if (!this.canStoreOutput(machine, output)) {
                this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
                player.sendMessage(this.itemFactory.warning("離心機輸出已滿，先清掉成品再啟動。"));
                return true;
            }
            final long requiredEnergy = this.effectiveEnergyCost(machine, recipe.energyCost());
            this.absorbNearbyEnergy(machine, machineBlock.getLocation(), requiredEnergy);
            if (!machine.consumeEnergy(requiredEnergy)) {
                this.setRuntimeState(machine, MachineRuntimeState.NO_POWER, "缺少電力");
                player.sendMessage(this.itemFactory.warning("離心機電力不足，先把能源接過來。"));
                return true;
            }
            this.consumeRecipeInputs(machine, recipe);
            machine.setManualOperationRecipeId(recipe.id());
            machine.setManualOperationTicks(this.manualCentrifugeDuration(machine, recipe));
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "離心中 " + machine.manualOperationTicks() + " 秒");
            final World world = machineBlock.getWorld();
            world.spawnParticle(Particle.CLOUD, layout.plate().getLocation().clone().add(0.5, 0.14, 0.5), 12, 0.18, 0.03, 0.18, 0.02);
            world.playSound(layout.plate().getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.25f, 1.35f);
            player.sendMessage(this.itemFactory.success("你啟動了離心機，轉盤開始高速旋轉。"));
            return true;
        }
        final RecipeRuntimeSnapshot snapshot = this.inspectManualCentrifugeRuntime(machine);
        this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
        player.sendMessage(this.itemFactory.warning(snapshot.detail()));
        return true;
    }

    private int manualWireMillDuration(final PlacedMachine machine, final MachineRecipe recipe) {
        return Math.max(2, recipe.energyCost() - this.countUpgrade(machine, "speed_upgrade"));
    }

    private int manualPurifierDuration(final PlacedMachine machine, final MachineRecipe recipe) {
        return Math.max(3, recipe.energyCost() - this.countUpgrade(machine, "speed_upgrade"));
    }

    private int manualCentrifugeDuration(final PlacedMachine machine, final MachineRecipe recipe) {
        return Math.max(4, recipe.energyCost() + 1 - this.countUpgrade(machine, "speed_upgrade"));
    }

    private boolean tryStartManualBioLab(final Player player, final PlacedMachine machine, final Block machineBlock) {
        final BioLabRigLayout layout = this.bioLabRigLayout(machineBlock);
        if (layout == null) {
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方絆線鉤");
            player.sendMessage(this.itemFactory.warning("生質實驗室缺少結構：請在核心正上方放絆線鉤後再啟動。"));
            return true;
        }
        if (machine.hasManualOperation()) {
            player.sendMessage(this.itemFactory.warning(machine.manualOperationTicks() > 0 ? "生質實驗室正在培養反應中。" : "實驗室已完成本輪，請先清空輸出。"));
            return true;
        }
        for (final MachineRecipe recipe : this.effectiveRecipes(machine)) {
            if (!this.canCraftRecipe(machine, recipe)) {
                continue;
            }
            final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
            if (output == null) {
                this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
                player.sendMessage(this.itemFactory.warning("目前無法初始化生質配方產物。"));
                return true;
            }
            if (!this.canStoreOutput(machine, output)) {
                this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
                player.sendMessage(this.itemFactory.warning("實驗室輸出已滿，先清掉成品再啟動。"));
                return true;
            }
            final long requiredEnergy = this.effectiveEnergyCost(machine, recipe.energyCost());
            this.absorbNearbyEnergy(machine, machineBlock.getLocation(), requiredEnergy);
            if (!machine.consumeEnergy(requiredEnergy)) {
                this.setRuntimeState(machine, MachineRuntimeState.NO_POWER, "缺少電力");
                player.sendMessage(this.itemFactory.warning("實驗室電力不足，先把能源接過來。"));
                return true;
            }
            this.consumeRecipeInputs(machine, recipe);
            machine.setManualOperationRecipeId(recipe.id());
            machine.setManualOperationTicks(this.manualBioLabDuration(machine, recipe));
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "培養中 " + machine.manualOperationTicks() + " 秒");
            final World world = machineBlock.getWorld();
            world.spawnParticle(Particle.WAX_ON, layout.hook().getLocation().clone().add(0.5, 0.1, 0.5), 10, 0.15, 0.15, 0.15, 0.01);
            world.playSound(layout.hook().getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.3f, 1.1f);
            player.sendMessage(this.itemFactory.success("你啟動了生質實驗室，培養液開始冒泡。"));
            return true;
        }
        final RecipeRuntimeSnapshot snapshot = this.inspectManualBioLabRuntime(machine);
        this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
        player.sendMessage(this.itemFactory.warning(snapshot.detail()));
        return true;
    }

    private int manualBioLabDuration(final PlacedMachine machine, final MachineRecipe recipe) {
        return Math.max(3, recipe.energyCost() - this.countUpgrade(machine, "speed_upgrade"));
    }

    private boolean tryStartManualChemReactor(final Player player, final PlacedMachine machine, final Block machineBlock) {
        final ChemReactorRigLayout layout = this.chemReactorRigLayout(machineBlock);
        if (layout == null) {
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方比較器");
            player.sendMessage(this.itemFactory.warning("化學反應器缺少結構：請在核心正上方放比較器後再啟動。"));
            return true;
        }
        if (machine.hasManualOperation()) {
            player.sendMessage(this.itemFactory.warning(machine.manualOperationTicks() > 0 ? "化學反應器正在進行鏈式反應。" : "反應器已完成本輪，請先清空輸出。"));
            return true;
        }
        for (final MachineRecipe recipe : this.effectiveRecipes(machine)) {
            if (!this.canCraftRecipe(machine, recipe)) {
                continue;
            }
            final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
            if (output == null) {
                this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
                player.sendMessage(this.itemFactory.warning("目前無法初始化反應配方產物。"));
                return true;
            }
            if (!this.canStoreOutput(machine, output)) {
                this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
                player.sendMessage(this.itemFactory.warning("反應器輸出已滿，先清掉成品再啟動。"));
                return true;
            }
            final long requiredEnergy = this.effectiveEnergyCost(machine, recipe.energyCost());
            this.absorbNearbyEnergy(machine, machineBlock.getLocation(), requiredEnergy);
            if (!machine.consumeEnergy(requiredEnergy)) {
                this.setRuntimeState(machine, MachineRuntimeState.NO_POWER, "缺少電力");
                player.sendMessage(this.itemFactory.warning("反應器電力不足，先把能源接過來。"));
                return true;
            }
            this.consumeRecipeInputs(machine, recipe);
            machine.setManualOperationRecipeId(recipe.id());
            machine.setManualOperationTicks(this.manualChemReactorDuration(machine, recipe));
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "反應中 " + machine.manualOperationTicks() + " 秒");
            final World world = machineBlock.getWorld();
            world.spawnParticle(Particle.WITCH, layout.comparator().getLocation().clone().add(0.5, 0.15, 0.5), 12, 0.15, 0.08, 0.15, 0.01);
            world.playSound(layout.comparator().getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.3f, 0.75f);
            player.sendMessage(this.itemFactory.success("你啟動了化學反應器，煙霧與蒸氣升起。"));
            return true;
        }
        final RecipeRuntimeSnapshot snapshot = this.inspectManualChemReactorRuntime(machine);
        this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
        player.sendMessage(this.itemFactory.warning(snapshot.detail()));
        return true;
    }

    private int manualChemReactorDuration(final PlacedMachine machine, final MachineRecipe recipe) {
        return Math.max(4, recipe.energyCost() - this.countUpgrade(machine, "speed_upgrade"));
    }

    private MachineRecipe findMachineRecipeById(final String machineId, final String recipeId) {
        if (recipeId == null || recipeId.isBlank()) {
            return null;
        }
        for (final MachineRecipe recipe : this.registry.getRecipesForMachine(machineId)) {
            if (recipe.id().equalsIgnoreCase(recipeId)) {
                return recipe;
            }
        }
        return null;
    }

    private void openMachineMenu(final Player player, final LocationKey key) {
        final PlacedMachine machine = this.machines.get(key);
        if (machine == null) {
            return;
        }
        final MachineDefinition definition = this.registry.getMachine(machine.machineId());
        if (definition == null) {
            return;
        }
        if (!this.canUseMachineType(player, definition.id(), false)) {
            return;
        }
        if (this.isResearchDesk(definition.id())) {
            this.trackViewClose(player.getUniqueId());
            this.plugin.getTechBookService().openResearchDesk(player);
            return;
        }

        final MachineGuiTheme theme = this.resolveMachineGuiTheme(definition.id());
        final Inventory inventory = Bukkit.createInventory(null, 45, this.itemFactory.hex(MACHINE_TITLE_PREFIX + this.itemFactory.displayNameForId(definition.id()), this.titleColor(theme)));
        this.decorateMachineMenu(inventory, machine, definition);
        this.renderMachineStorageSlots(inventory, machine, definition);
        this.trackViewOpen(player.getUniqueId(), key, new MachineViewSession(key, ViewMode.MAIN, 0, inventory));
        player.openInventory(inventory);
    }

    private void openRecipeMenu(final Player player, final LocationKey key, final int page) {
        final PlacedMachine machine = this.machines.get(key);
        if (machine == null) {
            return;
        }
        final MachineDefinition definition = this.registry.getMachine(machine.machineId());
        if (definition == null) {
            return;
        }

        final List<MachineRecipe> recipes = this.registry.getRecipesForMachine(machine.machineId());
        final int maxPage = Math.max(0, (recipes.size() - 1) / 21);
        final int safePage = Math.max(0, Math.min(maxPage, page));
        final MachineGuiTheme theme = this.resolveMachineGuiTheme(definition.id());
        final Inventory inventory = Bukkit.createInventory(null, 54, this.itemFactory.hex(RECIPE_TITLE_PREFIX + this.itemFactory.displayNameForId(definition.id()) + " • P" + (safePage + 1), this.titleColor(theme)));
        this.decorateMachineRecipeMenu(inventory, theme, safePage, maxPage);
        final int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        final int start = safePage * slots.length;
        for (int index = 0; index < slots.length && start + index < recipes.size(); index++) {
            final MachineRecipe recipe = recipes.get(start + index);
            inventory.setItem(slots[index], this.itemFactory.tagGuiAction(this.recipeInfo(recipe), "recipe-detail:" + recipe.id()));
        }
        for (final int slot : slots) {
            if (inventory.getItem(slot) == null || inventory.getItem(slot).getType() == Material.AIR) {
                inventory.setItem(slot, this.sectionPane(this.recipeAccentPane(theme), " ", List.of()));
            }
        }
            inventory.setItem(45, this.itemFactory.tagGuiAction(this.guiButton("machine-recipes-prev", this.previousButtonMaterial(theme), "◀ 上一頁", List.of("第 {page} / {max-page} 頁", "{hint}"), this.placeholders("page", String.valueOf(safePage + 1), "max-page", String.valueOf(maxPage + 1), "hint", safePage <= 0 ? "已經是第一頁" : "點擊查看上一頁")), "recipes:" + Math.max(0, safePage - 1)));
            inventory.setItem(49, this.itemFactory.tagGuiAction(this.guiButton("machine-recipes-back-machine", Material.ARROW, "返回機器", List.of("{name}"), this.placeholders("name", this.itemFactory.displayNameForId(definition.id()))), "back-main"));
            inventory.setItem(53, this.itemFactory.tagGuiAction(this.guiButton("machine-recipes-next", this.nextButtonMaterial(theme), "下一頁 ▶", List.of("第 {page} / {max-page} 頁", "{hint}"), this.placeholders("page", String.valueOf(safePage + 1), "max-page", String.valueOf(maxPage + 1), "hint", safePage >= maxPage ? "已經是最後一頁" : "點擊查看下一頁")), "recipes:" + Math.min(maxPage, safePage + 1)));
        this.trackViewOpen(player.getUniqueId(), key, new MachineViewSession(key, ViewMode.RECIPES, safePage, inventory));
        player.openInventory(inventory);
    }

    public boolean isMachineView(final String title) {
        return title.startsWith(MACHINE_TITLE_PREFIX) || title.startsWith(RECIPE_TITLE_PREFIX);
    }

    public void closeMachineView(final Player player, final Inventory inventory) {
        final MachineViewSession session = this.openViews.get(player.getUniqueId());
        if (session == null || session.mode() != ViewMode.MAIN) {
            return;
        }
        if (session.inventory() != inventory) {
            return;
        }
        this.trackViewClose(player.getUniqueId());
        final PlacedMachine machine = this.machines.get(session.locationKey());
        if (machine == null) {
            return;
        }

        final ItemStack[] input = new ItemStack[INPUT_SLOTS.length];
        final ItemStack[] output = new ItemStack[OUTPUT_SLOTS.length];
        final ItemStack[] upgrades = new ItemStack[UPGRADE_SLOTS.length];
        for (int index = 0; index < INPUT_SLOTS.length; index++) {
            input[index] = this.sanitizeStoredStack(inventory.getItem(INPUT_SLOTS[index]));
        }
        for (int index = 0; index < OUTPUT_SLOTS.length; index++) {
            output[index] = this.sanitizeStoredStack(inventory.getItem(OUTPUT_SLOTS[index]));
        }
        for (int index = 0; index < UPGRADE_SLOTS.length; index++) {
            upgrades[index] = this.sanitizeStoredStack(inventory.getItem(UPGRADE_SLOTS[index]));
        }
        machine.replaceInputInventory(input);
        machine.replaceOutputInventory(output);
        machine.replaceUpgradeInventory(upgrades);
    }

    public void handleMachineInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final MachineViewSession session = this.openViews.get(player.getUniqueId());
        if (session == null) {
            event.setCancelled(true);
            return;
        }

        if (session.mode() == ViewMode.RECIPES) {
            event.setCancelled(true);
            final String action = this.itemFactory.getGuiAction(event.getCurrentItem());
            if (action == null) {
                return;
            }
            if (action.startsWith("recipes:")) {
                this.openRecipeMenuNextTick(player, session.locationKey(), this.parsePage(action.substring("recipes:".length())));
                return;
            }
            if (action.equals("back-main")) {
                this.openMachineMenuNextTick(player, session.locationKey());
                return;
            }
            if (action.startsWith("recipe-detail:")) {
                this.showRecipeDetailNextTick(player, session.locationKey(), action.substring("recipe-detail:".length()), session.page());
            }
            return;
        }

        final PlacedMachine machine = this.machines.get(session.locationKey());
        if (machine == null) {
            event.setCancelled(true);
            return;
        }

        final int rawSlot = event.getRawSlot();
        final int topSize = event.getView().getTopInventory().getSize();
        if (rawSlot < topSize) {
            event.setCancelled(true);
            final String action = this.itemFactory.getGuiAction(event.getCurrentItem());
            if (action != null) {
                this.handleMachineAction(player, session.locationKey(), action);
                return;
            }
            if (this.isInputSlot(rawSlot) || this.isUpgradeSlot(rawSlot)) {
                if (!this.isMachineSlotEditable(machine, rawSlot)) {
                    return;
                }
                if (event.isShiftClick()) {
                    this.moveMachineSlotToPlayer(player, machine, rawSlot);
                } else {
                    this.handleManualMachineStorageClick(player, machine, rawSlot, event.isRightClick());
                }
                this.refreshManualMachineInteraction(player, machine);
                return;
            }
            if (this.isOutputSlot(rawSlot)) {
                if (!this.isMachineSlotEditable(machine, rawSlot)) {
                    return;
                }
                if (event.isShiftClick()) {
                    this.moveMachineSlotToPlayer(player, machine, rawSlot);
                } else {
                    this.handleManualMachineOutputClick(player, machine, rawSlot, event.isRightClick());
                }
                this.refreshManualMachineInteraction(player, machine);
                return;
            }
            return;
        }

        if (rawSlot >= topSize && event.getClickedInventory() != null) {
            if (event.isShiftClick() && event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
                event.setCancelled(true);
                final ItemStack remaining = this.pushIntoMachineStorage(machine, event.getCurrentItem().clone());
                if (remaining.getAmount() != event.getCurrentItem().getAmount()) {
                    if (remaining.getType() == Material.AIR || remaining.getAmount() <= 0) {
                        event.getClickedInventory().setItem(event.getSlot(), null);
                    } else {
                        event.getClickedInventory().setItem(event.getSlot(), remaining);
                    }
                    this.refreshManualMachineInteraction(player, machine);
                }
                return;
            }

            if (event.getClick() == ClickType.NUMBER_KEY) {
                event.setCancelled(true);
                return;
            }

            event.setCancelled(false);
            return;
        }

        return;
    }

    public void handleMachineInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final MachineViewSession session = this.openViews.get(player.getUniqueId());
        if (session == null || session.mode() != ViewMode.MAIN) {
            event.setCancelled(true);
            return;
        }
        final int topSize = event.getView().getTopInventory().getSize();
        final PlacedMachine machine = this.machines.get(session.locationKey());
        if (machine == null) {
            event.setCancelled(true);
            return;
        }
        boolean touchesTop = false;
        for (final int rawSlot : event.getRawSlots()) {
            if (rawSlot >= topSize) {
                continue;
            }
            touchesTop = true;
            if (!this.isMachineSlotEditable(machine, rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
        if (!touchesTop) {
            event.setCancelled(false);
            return;
        }
        event.setCancelled(true);
        this.applyManualMachineDrag(player, machine, event);
        this.refreshManualMachineInteraction(player, machine);
    }

    public void removeAllDisplays() {
        for (final UUID displayId : this.machineDisplays.values()) {
            final Entity entity = Bukkit.getEntity(displayId);
            if (entity != null) {
                this.safeRemoveEntity(entity);
            }
        }
        this.machineDisplays.clear();
        for (final UUID displayId : this.machineVisualDisplays.values()) {
            final Entity entity = Bukkit.getEntity(displayId);
            if (entity != null) {
                this.safeRemoveEntity(entity);
            }
        }
        this.machineVisualDisplays.clear();
        for (final Map.Entry<UUID, BossBar> entry : this.machineBossBars.entrySet()) {
            final Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        this.machineBossBars.clear();
        this.playerLookingAt.clear();
    }

    public void purgeOrphanDisplays() {
        for (final World world : Bukkit.getWorlds()) {
            for (final Entity entity : world.getEntities()) {
                if (!(entity instanceof TextDisplay)) {
                    continue;
                }
                this.scheduler.runEntity(entity, () -> {
                    final TextDisplay display = (TextDisplay) entity;
                    try {
                        if (display.isPersistent() || !display.isInvulnerable()) {
                            return;
                        }
                        if (!display.hasGravity() && display.getBrightness() != null && display.getBrightness().getBlockLight() == 15
                                && display.getScoreboardTags().contains("techproject_display")) {
                            entity.remove();
                        }
                    } catch (final Exception ignored) {
                    }
                });
            }
        }
    }

    private void safeRemoveEntity(final Entity entity) {
        try {
            this.scheduler.runEntity(entity, entity::remove);
        } catch (final Exception ignored) {
            entity.remove();
        }
    }

    public void saveAll() {
        final Map<String, Map<String, Object>> machineMap = new LinkedHashMap<>();
        int index = 0;
        for (final PlacedMachine machine : this.machines.values()) {
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("world", machine.locationKey().worldName());
            entry.put("x", machine.locationKey().x());
            entry.put("y", machine.locationKey().y());
            entry.put("z", machine.locationKey().z());
            entry.put("machine-id", machine.machineId());
            entry.put("owner", machine.owner().toString());
            entry.put("enabled", machine.enabled());
            entry.put("stored-energy", machine.storedEnergy());
            entry.put("total-generated", machine.totalGenerated());
            entry.put("ticks-active", machine.ticksActive());
            entry.put("quarry-fuel", machine.quarryFuel());
            entry.put("quarry-cursor-x", machine.quarryCursorX());
            entry.put("quarry-cursor-z", machine.quarryCursorZ());
            entry.put("android-fuel", machine.androidFuel());
            entry.put("android-patrol-radius", machine.androidPatrolRadius());
            entry.put("android-patrol-height", machine.androidPatrolHeight());
            entry.put("android-route-cursor", machine.androidRouteCursor());
            entry.put("android-route-mode", machine.androidRouteMode());
            entry.put("runtime-state", machine.runtimeState().name());
            entry.put("runtime-detail", machine.runtimeDetail());
            entry.put("manual-operation-ticks", machine.manualOperationTicks());
            entry.put("manual-operation-recipe", machine.manualOperationRecipeId());
            entry.put("locked-recipe", machine.lockedRecipeId());
            entry.put("input-direction", machine.inputDirection());
            entry.put("output-direction", machine.outputDirection());
            entry.put("filter-mode", machine.filterMode());
            entry.put("input", ItemStackSerializer.toBase64(machine.inputInventory()));
            entry.put("output", ItemStackSerializer.toBase64(machine.outputInventory()));
            entry.put("upgrades", ItemStackSerializer.toBase64(machine.upgradeInventory()));
            machineMap.put(String.valueOf(index++), entry);
        }
        this.storageBackend.saveAllMachines(machineMap);
    }

    private void loadMachines() {
        final Map<String, Map<String, Object>> machineMap = this.storageBackend.loadAllMachines();
        for (final Map.Entry<String, Map<String, Object>> mapEntry : machineMap.entrySet()) {
            final Map<String, Object> data = mapEntry.getValue();
            final String worldName = data.get("world") instanceof String s ? s : "";
            final String machineId = data.get("machine-id") instanceof String s ? s : "";
            final String ownerRaw = data.get("owner") instanceof String s ? s : "";
            if (worldName.isBlank() || machineId.isBlank() || ownerRaw.isBlank()) {
                continue;
            }

            try {
                final LocationKey locationKey = new LocationKey(
                        worldName,
                        data.get("x") instanceof Number n ? n.intValue() : 0,
                        data.get("y") instanceof Number n ? n.intValue() : 0,
                        data.get("z") instanceof Number n ? n.intValue() : 0
                );
                final PlacedMachine machine = new PlacedMachine(locationKey, machineId, java.util.UUID.fromString(ownerRaw));
                machine.restoreState(
                        data.get("stored-energy") instanceof Number n ? n.longValue() : 0L,
                        data.get("total-generated") instanceof Number n ? n.longValue() : 0L,
                        data.get("ticks-active") instanceof Number n ? n.longValue() : 0L
                );
                machine.setQuarryFuel(data.get("quarry-fuel") instanceof Number n ? n.intValue() : 0);
                machine.setQuarryCursor(
                        data.get("quarry-cursor-x") instanceof Number n ? n.intValue() : 0,
                        data.get("quarry-cursor-z") instanceof Number n ? n.intValue() : 0);
                machine.setAndroidFuel(data.get("android-fuel") instanceof Number n ? n.intValue() : 0);
                machine.setAndroidPatrolRadius(data.get("android-patrol-radius") instanceof Number n ? n.intValue() : 2);
                machine.setAndroidPatrolHeight(data.get("android-patrol-height") instanceof Number n ? n.intValue() : 1);
                machine.setAndroidRouteCursor(data.get("android-route-cursor") instanceof Number n ? n.intValue() : 0);
                machine.setAndroidRouteMode(data.get("android-route-mode") instanceof String s ? s : "SERPENTINE");
                machine.setRuntimeState(
                        this.parseRuntimeState(data.get("runtime-state") instanceof String s ? s : "IDLE"),
                        data.get("runtime-detail") instanceof String s ? s : "待命");
                machine.setManualOperationTicks(data.get("manual-operation-ticks") instanceof Number n ? n.intValue() : 0);
                machine.setManualOperationRecipeId(data.get("manual-operation-recipe") instanceof String s ? s : null);
                machine.setLockedRecipeId(data.get("locked-recipe") instanceof String s ? s : null);
                if (machine.manualOperationTicks() > 0 && machine.manualOperationRecipeId() == null) {
                    machine.clearManualOperation();
                }
                machine.setEnabled(data.get("enabled") instanceof Boolean b ? b : !this.isQuarryLike(machineId));
                this.clampMachineEnergy(machine);
                machine.setInputDirection(data.get("input-direction") instanceof String s ? s : "ALL");
                machine.setOutputDirection(data.get("output-direction") instanceof String s ? s : "ALL");
                machine.setFilterMode(data.get("filter-mode") instanceof String s ? s : "WHITELIST");
                final Object inputData = data.get("input");
                final Object outputData = data.get("output");
                final Object upgradeData = data.get("upgrades");
                if (inputData instanceof String base64) {
                    machine.replaceInputInventory(this.sanitizeStoredContents(ItemStackSerializer.fromBase64(base64, 9)));
                } else if (inputData instanceof List<?> list) {
                    machine.replaceInputInventory(this.sanitizeStoredContents(this.readInventorySection(list)));
                }
                if (outputData instanceof String base64) {
                    machine.replaceOutputInventory(this.sanitizeStoredContents(ItemStackSerializer.fromBase64(base64, 9)));
                } else if (outputData instanceof List<?> list) {
                    machine.replaceOutputInventory(this.sanitizeStoredContents(this.readInventorySection(list)));
                }
                if (upgradeData instanceof String base64) {
                    machine.replaceUpgradeInventory(this.sanitizeStoredContents(ItemStackSerializer.fromBase64(base64, UPGRADE_SLOTS.length)));
                } else if (upgradeData instanceof List<?> list) {
                    machine.replaceUpgradeInventory(this.sanitizeStoredContents(this.readInventorySection(list, UPGRADE_SLOTS.length)));
                }
                this.machines.put(locationKey, machine);
            } catch (final IllegalArgumentException ignored) {
                this.plugin.getLogger().warning("略過損壞機器資料節點：" + mapEntry.getKey());
            }
        }
    }

    private void tickMachineLookAt() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            this.scheduler.runEntity(player, () -> this.tickPlayerLookAt(player));
        }
    }

    private void tickPlayerLookAt(final Player player) {
        final UUID uid = player.getUniqueId();
        try {
            final Block targetBlock = player.getTargetBlockExact(6, FluidCollisionMode.NEVER);
            final Block machineBlock = targetBlock == null ? null : this.resolveManagedMachineBlock(targetBlock);
            if (machineBlock == null) {
                this.clearMachineBossBar(uid, player);
                return;
            }
            final LocationKey key = LocationKey.from(machineBlock.getLocation());
            final PlacedMachine machine = this.machines.get(key);
            if (machine == null) {
                this.clearMachineBossBar(uid, player);
                return;
            }
            final MachineDefinition definition = this.registry.getMachine(machine.machineId());
            if (definition == null) {
                this.clearMachineBossBar(uid, player);
                return;
            }
            final Component title = this.machineBossBarContent(machine, definition, machineBlock.getLocation());
            final float progress = this.machineEnergyProgress(machine);
            final BossBar.Color barColor = this.bossBarColor(machine);
            BossBar bar = this.machineBossBars.get(uid);
            if (bar == null) {
                bar = BossBar.bossBar(title, progress, barColor, BossBar.Overlay.PROGRESS);
                this.machineBossBars.put(uid, bar);
                player.showBossBar(bar);
            } else {
                bar.name(title);
                bar.progress(progress);
                bar.color(barColor);
            }
            this.playerLookingAt.put(uid, key);
        } catch (final Exception ignored) {
            this.clearMachineBossBar(uid, player);
        }
    }

    private void clearMachineBossBar(final UUID playerId, final Player player) {
        this.playerLookingAt.remove(playerId);
        final BossBar bar = this.machineBossBars.remove(playerId);
        if (bar != null && player != null) {
            player.hideBossBar(bar);
        }
    }

    public void cleanupPlayer(final UUID playerId) {
        final Player player = Bukkit.getPlayer(playerId);
        this.clearMachineBossBar(playerId, player);
    }

    private Component machineBossBarContent(final PlacedMachine machine, final MachineDefinition definition, final Location location) {
        final Component name = this.itemFactory.tierName(definition.tier(), this.itemFactory.displayNameForId(definition.id()));
        final long stored = machine.storedEnergy();
        final long max = this.maxEnergyCapacity(machine);
        final TextColor energyColor = stored > 0 ? TextColor.color(0x7C, 0xFC, 0x9A) : TextColor.color(0xFF, 0x7B, 0x7B);
        final Component energy = Component.text(" ⚡" + stored + "/" + max, energyColor);
        final Component separator = Component.text(" | ", TextColor.color(0xA8, 0xB2, 0xC1));
        final Component state = this.bossBarStateLine(machine, definition, location);
        return name.append(energy).append(separator).append(state);
    }

    private Component bossBarStateLine(final PlacedMachine machine, final MachineDefinition definition, final Location location) {
        if (this.isQuarryLike(machine.machineId())) {
            return this.bossBarQuarryState(machine, location);
        }
        final String id = this.normalizeId(definition.id());
        if (id.equals("android_station")) {
            final AndroidScriptProfile script = this.resolveAndroidScript(machine, location);
            return Component.text("燃料:" + machine.androidFuel() + "/" + ANDROID_MAX_FUEL, TextColor.color(0x7F, 0xDB, 0xFF))
                    .append(Component.text(" " + (script == null ? "待命" : script.displayName()), TextColor.color(0xA8, 0xB2, 0xC1)));
        }
        return switch (machine.runtimeState()) {
            case RUNNING -> Component.text("▶ " + machine.runtimeDetail(), TextColor.color(0x7C, 0xFC, 0x9A));
            case IDLE -> Component.text("◼ 閒置", TextColor.color(0xA8, 0xB2, 0xC1));
            case STANDBY -> Component.text("⏸ 待機", TextColor.color(0xFF, 0xD1, 0x66));
            case NO_POWER -> Component.text("⚠ 電力不足", TextColor.color(0xFF, 0x7B, 0x7B));
            case NO_INPUT -> Component.text("⚠ 缺少材料", TextColor.color(0xFF, 0xD1, 0x66));
            case OUTPUT_BLOCKED -> Component.text("⚠ 輸出滿載", TextColor.color(0xFF, 0xD1, 0x66));
            case NO_FUEL -> Component.text("⚠ 缺燃料", TextColor.color(0xFF, 0xD1, 0x66));
            case STRUCTURE_INCOMPLETE -> Component.text("✖ 結構不完整", TextColor.color(0xFF, 0x7B, 0x7B));
            case LINK_MISSING -> Component.text("✖ 未連線", TextColor.color(0xFF, 0x7B, 0x7B));
        };
    }

    private Component bossBarQuarryState(final PlacedMachine machine, final Location location) {
        final Block block = location.getBlock();
        final QuarryRigLayout layout = this.quarryRigLayout(block);
        if (layout == null) {
            return Component.text("✖ 結構不完整", TextColor.color(0xFF, 0x7B, 0x7B));
        }
        final int fuel = this.quarryAvailableFuel(layout, machine);
        final Component fuelComp = Component.text("燃料:" + fuel, TextColor.color(0x7F, 0xDB, 0xFF));
        if (!machine.enabled()) {
            return fuelComp.append(Component.text(" ⏸ 待機", TextColor.color(0xFF, 0xD1, 0x66)));
        }
        final LocationKey controlKey = LocationKey.from(location);
        if (this.quarryBusy.contains(controlKey) && this.quarryWarmedUp.contains(controlKey)) {
            return fuelComp.append(Component.text(" ▶ 鑽掘中", TextColor.color(0x7C, 0xFC, 0x9A)));
        }
        if (this.quarryBusy.contains(controlKey)) {
            return fuelComp.append(Component.text(" ⏳ 暖機中", TextColor.color(0xFF, 0xD1, 0x66)));
        }
        return fuelComp.append(Component.text(" ◼ 就緒", TextColor.color(0xA8, 0xB2, 0xC1)));
    }

    private float machineEnergyProgress(final PlacedMachine machine) {
        final long max = this.maxEnergyCapacity(machine);
        if (max <= 0L) {
            return 0f;
        }
        return Math.min(1.0f, Math.max(0f, (float) machine.storedEnergy() / max));
    }

    private BossBar.Color bossBarColor(final PlacedMachine machine) {
        return switch (machine.runtimeState()) {
            case RUNNING -> BossBar.Color.GREEN;
            case IDLE, STANDBY -> BossBar.Color.YELLOW;
            case NO_POWER, STRUCTURE_INCOMPLETE, LINK_MISSING -> BossBar.Color.RED;
            case NO_INPUT, OUTPUT_BLOCKED, NO_FUEL -> BossBar.Color.YELLOW;
        };
    }

    private void tickAllMachines() {
        for (final PlacedMachine machine : this.machines.values()) {
            final World world = Bukkit.getWorld(machine.locationKey().worldName());
            if (world == null) {
                continue;
            }
            final Location location = new Location(world, machine.locationKey().x(), machine.locationKey().y(), machine.locationKey().z());
            this.scheduler.runRegion(location, task -> this.tickMachine(machine, location));
        }
    }

    private void tickMachine(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        final MachineDefinition definition = this.registry.getMachine(machine.machineId());
        if (definition == null) {
            return;
        }

        if (this.hasOpenMainView(machine.locationKey()) && this.shouldPauseWhileEditing(definition)) {
            this.updateMachineDisplay(machine, definition, location);
            return;
        }

        this.pullOpenViewState(machine.locationKey(), machine);

        final Block block = world.getBlockAt(location);
        if (!this.isMachineBlock(block, definition)) {
            this.unregisterMachine(block);
            return;
        }

        machine.tick();
        if (!machine.enabled()) {
            this.setRuntimeState(machine, MachineRuntimeState.STANDBY, "待機");
        }
        switch (definition.id()) {
            case "research_desk" -> this.tickResearchDesk(machine, location);
            case "solar_generator" -> this.tickSolarGenerator(machine, location);
            case "solar_array" -> this.tickSolarArray(machine, definition, location);
            case "coal_generator" -> this.tickCoalGenerator(machine, location);
            case "storm_turbine" -> this.tickStormTurbine(machine, location);
            case "planetary_gate" -> this.tickPlanetaryGate(machine, location);
            case "energy_node", "energy_cable", "logistics_node", "item_tube" -> this.tickRelay(machine, location, definition.id());
            case "auto_farm" -> this.tickFarm(machine, location);
            case "crop_harvester" -> this.tickCropHarvester(machine, location);
            case "planetary_harvester" -> this.tickPlanetaryHarvester(machine, location);
            case "tree_feller" -> this.tickTreeFeller(machine, location);
            case "mob_collector" -> this.tickMobCollector(machine, location);
            case "fishing_dock" -> this.tickFishingDock(machine, location);
            case "recycler" -> this.tickRecycler(machine, location);
            case "vacuum_inlet" -> this.tickVacuumInlet(machine, location);
            case "battery_bank" -> this.tickBatteryBank(machine, location);
            case "genetic_sequencer" -> this.tickGeneticSequencer(machine, location);
            case "private_coop" -> this.tickPrivateCoop(machine, location);
            case "excitation_chamber" -> this.tickExcitationChamber(machine, location);
            case "greenhouse" -> this.tickGreenhouse(machine, definition, location);
            case "quarry_drill", "quarry_drill_mk2", "quarry_drill_mk3" -> this.tickQuarryDrill(machine, location);
            case "storage_hub" -> this.tickStorageHub(machine, location);
            case "filter_router" -> this.tickFilterRouter(machine, location);
            case "splitter_node" -> this.tickSplitterNode(machine, location);
            case "industrial_bus" -> this.tickIndustrialBus(machine, location);
            case "cargo_input_node" -> this.tickCargoInputNode(machine, location);
            case "cargo_manager" -> this.tickCargoManager(machine, location);
            case "trash_node" -> this.tickTrashNode(machine, location);
            case "cargo_motor" -> this.tickCargoMotor(machine, location);
            case "android_station" -> this.tickAndroidStation(machine, definition, location);
            case "android_item_interface" -> this.tickAndroidItemInterface(machine, location);
            case "android_fuel_interface" -> this.tickAndroidFuelInterface(machine, location);
            case "crusher" -> this.tickManualCrusher(machine, definition, location);
            case "compressor" -> this.tickManualCompressor(machine, definition, location);
            case "ore_washer" -> this.tickManualOreWasher(machine, definition, location);
            case "wire_mill" -> this.tickManualWireMill(machine, definition, location);
            case "bio_lab" -> this.tickManualBioLab(machine, definition, location);
            case "purifier" -> this.tickManualPurifier(machine, definition, location);
            case "centrifuge" -> this.tickManualCentrifuge(machine, definition, location);
            case "chemical_reactor" -> this.tickManualChemReactor(machine, definition, location);
            case "assembler", "advanced_assembler", "laser_engraver", "quantum_processor", "field_forge", "matter_compiler" -> this.tickProcessor(machine, definition, location, Particle.ELECTRIC_SPARK, definition.id() + "_cycles");
            case "smeltery", "fusion_reactor" -> this.tickProcessor(machine, definition, location, Particle.LAVA, definition.id() + "_cycles");
            case "coolant_mixer", "refinery", "crystal_growth_chamber", "drone_bay", "android_bay", "polymer_press" -> this.tickProcessor(machine, definition, location, Particle.WITCH, definition.id() + "_cycles");
            case "electric_crusher", "electric_compressor", "electric_ore_washer", "electric_wire_mill",
                 "electric_purifier", "electric_centrifuge", "electric_bio_lab", "electric_chemical_reactor" ->
                    this.tickProcessor(machine, definition, location, Particle.ELECTRIC_SPARK, definition.id() + "_cycles");
            default -> this.tickConsumer(machine, location, definition.energyPerTick());
        }
        this.transferOutputs(machine, location);
        this.pushOpenViewState(machine.locationKey(), machine);
        this.updateMachineDisplay(machine, definition, location);
        this.achievementService.evaluate(machine.owner());
    }

    private void tickRelay(final PlacedMachine machine, final Location location, final String relayId) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        this.setRuntimeState(machine, MachineRuntimeState.RUNNING, relayId.contains("energy") ? "能源傳輸" : "物流傳輸");
        final Particle particle = relayId.contains("energy") ? Particle.END_ROD : Particle.WAX_OFF;
        world.spawnParticle(particle, location.clone().add(0.5, 0.8, 0.5), 4, 0.18, 0.18, 0.18, 0.0);
    }

    private void tickResearchDesk(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null || machine.ticksActive() % 20L != 0L) {
            return;
        }
        this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "研究脈衝");
        world.spawnParticle(Particle.ENCHANT, location.clone().add(0.5, 1.08, 0.5), 10, 0.28, 0.18, 0.28, 0.0);
        world.spawnParticle(Particle.WAX_ON, location.clone().add(0.5, 1.0, 0.5), 4, 0.18, 0.1, 0.18, 0.0);
        if (machine.ticksActive() % 80L == 0L) {
            world.playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.2f, 1.08f);
        }
    }

    private void tickSolarGenerator(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final Block block = world.getBlockAt(location);
        final long time = world.getTime();
        final boolean active = time >= 0L && time < 12300L && block.getLightFromSky() >= 14 && world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ()) <= location.getBlockY();
        if (!active) {
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待日照");
            world.spawnParticle(Particle.CLOUD, location.clone().add(0.5, 1.0, 0.5), 2, 0.12, 0.12, 0.12, 0.0);
            return;
        }
        this.tickGenerator(machine, location, 3L, Particle.END_ROD, Sound.BLOCK_BEACON_POWER_SELECT);
    }

    private void tickSolarArray(final PlacedMachine machine, final MachineDefinition definition, final Location location) {
        this.tickSolarGenerator(machine, location);
        if (this.processMachineRecipes(machine, location, "solar_array_cycles", Particle.END_ROD, Sound.BLOCK_BEACON_AMBIENT)) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "加工中");
            return;
        }
        final RecipeRuntimeSnapshot snapshot = this.inspectRecipeRuntime(machine);
        if (snapshot.state() != MachineRuntimeState.IDLE) {
            this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
        }
    }

    private void tickCoalGenerator(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        if (machine.ticksActive() % 10L != 0L) {
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 2, 0.15, 0.2, 0.15, 0.0);
            return;
        }
        if (!this.consumeInput(machine, List.of("coal", "coal_dust"), 1)) {
            this.setRuntimeState(machine, MachineRuntimeState.NO_FUEL, "缺少燃料");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 3, 0.16, 0.2, 0.16, 0.0);
            return;
        }
        final long accepted = this.addEnergyCapped(machine, 18L);
        this.setRuntimeState(machine, accepted > 0L ? MachineRuntimeState.RUNNING : MachineRuntimeState.IDLE, accepted > 0L ? "燃燒發電" : "儲能已滿");
        this.progressService.incrementStat(machine.owner(), "energy_generated", accepted);
        this.progressService.unlockItem(machine.owner(), "energy_cell");
        this.distributeNearbyEnergy(machine, location, 4L);
        world.spawnParticle(Particle.FLAME, location.clone().add(0.5, 1.0, 0.5), 8, 0.2, 0.25, 0.2, 0.01);
        world.playSound(location, Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.35f, 1.0f);
    }

    private void tickStormTurbine(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final boolean storming = world.hasStorm() || world.isThundering();
        final long amount = storming ? 8L : 2L;
        final long accepted = this.addEnergyCapped(machine, amount);
        this.setRuntimeState(machine, accepted > 0L ? MachineRuntimeState.RUNNING : MachineRuntimeState.IDLE, storming ? "風暴發電" : "低風發電");
        this.progressService.incrementStat(machine.owner(), "energy_generated", accepted);
        this.progressService.incrementStat(machine.owner(), "storm_energy", accepted);
        this.distributeNearbyEnergy(machine, location, storming ? 6L : 2L);
        world.spawnParticle(storming ? Particle.ELECTRIC_SPARK : Particle.CLOUD, location.clone().add(0.5, 1.1, 0.5), storming ? 10 : 5, 0.28, 0.28, 0.28, 0.01);
        if (storming && machine.ticksActive() % 10L == 0L) {
            world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.2f, 1.8f);
        }
    }

    private void tickGenerator(final PlacedMachine machine, final Location location, final long amount, final Particle particle, final Sound sound) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final long accepted = this.addEnergyCapped(machine, amount);
        this.setRuntimeState(machine, accepted > 0L ? MachineRuntimeState.RUNNING : MachineRuntimeState.IDLE, accepted > 0L ? "發電中" : "儲能已滿");
        this.progressService.incrementStat(machine.owner(), "energy_generated", accepted);
        this.progressService.unlockItem(machine.owner(), "energy_cell");
        this.distributeNearbyEnergy(machine, location, amount / 2L + 1L);
        world.spawnParticle(particle, location.clone().add(0.5, 1.1, 0.5), 8, 0.25, 0.35, 0.25, 0.01);
        if (machine.ticksActive() % 5L == 0L) {
            world.playSound(location, sound, 0.35f, 1.2f);
        }
    }

    private void tickFarm(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }

        if (this.processMachineRecipes(machine, location, "farm_harvested", Particle.HAPPY_VILLAGER, Sound.ITEM_BONE_MEAL_USE)) {
            this.progressService.unlockItem(machine.owner(), "crops");
            this.progressService.unlockItem(machine.owner(), "seed_cluster");
            this.progressService.unlockItem(machine.owner(), "fertilizer_mix");
            this.progressService.unlockByRequirement(machine.owner(), "auto_farm");
        }
    }

    private void tickCropHarvester(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null || machine.ticksActive() % 8L != 0L) {
            return;
        }
        if (!this.isAllowedWorld(world)) {
            return;
        }
        final int radius = 2 + this.countUpgrade(machine, "range_upgrade");
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                final Block crop = world.getBlockAt(location.getBlockX() + dx, location.getBlockY(), location.getBlockZ() + dz);
                if (!this.isSafeCropTarget(crop)) {
                    continue;
                }
                final BlockData blockData = crop.getBlockData();
                if (!(blockData instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
                    continue;
                }
                final boolean customCrop = this.techCropService.isTrackedCrop(crop);
                final List<ItemStack> outputs = customCrop
                        ? this.techCropService.harvest(crop, true)
                        : this.harvestOutputsFor(crop.getType());
                final long energy = this.effectiveEnergyCost(machine, 8L);
                if (outputs.isEmpty() || !this.canStoreAllOutputs(machine, outputs)) {
                    continue;
                }
                this.absorbNearbyEnergy(machine, location, energy);
                if (!machine.consumeEnergy(energy)) {
                    return;
                }
                if (!customCrop) {
                    ageable.setAge(0);
                    crop.setBlockData(ageable, true);
                }
                this.storeOutputs(machine, outputs);
                this.progressService.incrementStat(machine.owner(), "farm_harvested", outputs.stream().mapToInt(ItemStack::getAmount).sum());
                this.progressService.unlockItem(machine.owner(), "crops");
                this.progressService.unlockItem(machine.owner(), "crop_seeds");
                for (final ItemStack output : outputs) {
                    final String outputId = this.resolveStackId(output);
                    if (outputId != null) {
                        this.progressService.unlockItem(machine.owner(), outputId);
                    }
                }
                this.progressService.unlockByRequirement(machine.owner(), "machine:crop_harvester");
                world.spawnParticle(Particle.HAPPY_VILLAGER, crop.getLocation().add(0.5, 0.8, 0.5), 8, 0.2, 0.2, 0.2, 0.01);
                world.playSound(crop.getLocation(), Sound.ITEM_BONE_MEAL_USE, 0.4f, 1.15f);
                return;
            }
        }
    }

    private void tickPlanetaryHarvester(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null || machine.ticksActive() % 10L != 0L) {
            return;
        }
        if (!this.plugin.getPlanetService().isPlanetWorld(world)) {
            return;
        }
        final int radius = 3 + Math.min(2, this.countUpgrade(machine, "range_upgrade"));
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 3; dy++) {
                    final Block target = world.getBlockAt(location.getBlockX() + dx, location.getBlockY() + dy, location.getBlockZ() + dz);
                    final List<ItemStack> preview = this.plugin.getPlanetService().previewMachineHarvest(target);
                    if (preview.isEmpty() || !this.canStoreAllOutputs(machine, preview)) {
                        continue;
                    }
                    final long energy = this.effectiveEnergyCost(machine, 12L + preview.stream().mapToInt(ItemStack::getAmount).sum());
                    this.absorbNearbyEnergy(machine, location, energy);
                    if (!machine.consumeEnergy(energy)) {
                        return;
                    }
                    final List<ItemStack> outputs = this.plugin.getPlanetService().harvestForMachine(target);
                    if (outputs.isEmpty()) {
                        this.addEnergyCapped(machine, energy);
                        continue;
                    }
                    this.storeOutputs(machine, outputs);
                    this.progressService.incrementStat(machine.owner(), "planetary_samples_collected", outputs.stream().mapToInt(ItemStack::getAmount).sum());
                    this.progressService.unlockByRequirement(machine.owner(), "machine:planetary_harvester");
                    for (final ItemStack output : outputs) {
                        final String outputId = this.resolveStackId(output);
                        if (outputId != null) {
                            this.progressService.unlockItem(machine.owner(), outputId);
                        }
                    }
                    world.spawnParticle(Particle.END_ROD, target.getLocation().add(0.5, 0.65, 0.5), 8, 0.2, 0.2, 0.2, 0.01);
                    world.playSound(target.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.3f, 1.4f);
                    return;
                }
            }
        }
    }

    private void tickTreeFeller(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null || machine.ticksActive() % 12L != 0L) {
            return;
        }
        if (!this.isAllowedWorld(world)) {
            return;
        }
        final int radius = 2 + Math.min(2, this.countUpgrade(machine, "range_upgrade"));
        final int maxLogs = 16 + this.countUpgrade(machine, "stack_upgrade") * 6;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy <= 6; dy++) {
                    final Block origin = world.getBlockAt(location.getBlockX() + dx, location.getBlockY() + dy, location.getBlockZ() + dz);
                    if (!this.isTreeLog(origin.getType()) || !this.isSafeTreeTarget(origin)) {
                        continue;
                    }
                    final List<Block> logs = this.collectConnectedLogs(origin, maxLogs);
                    if (logs.isEmpty()) {
                        continue;
                    }
                    final Material logType = logs.get(0).getType();
                    final List<ItemStack> outputs = new ArrayList<>();
                    outputs.add(this.buildStackForId(logType.name(), logs.size()));
                    final ItemStack sapling = this.buildStackForId(this.saplingForLog(logType).name(), Math.max(1, logs.size() / 8));
                    if (sapling != null) {
                        outputs.add(sapling);
                    }
                    if (!this.canStoreAllOutputs(machine, outputs)) {
                        return;
                    }
                    final long energy = this.effectiveEnergyCost(machine, 10L + logs.size());
                    this.absorbNearbyEnergy(machine, location, energy);
                    if (!machine.consumeEnergy(energy)) {
                        return;
                    }
                    for (final Block log : logs) {
                        log.setType(Material.AIR, false);
                        world.spawnParticle(Particle.BLOCK, log.getLocation().add(0.5, 0.5, 0.5), 8, 0.2, 0.2, 0.2, log.getBlockData());
                    }
                    this.storeOutputs(machine, outputs);
                    this.progressService.incrementStat(machine.owner(), "logs_felled", logs.size());
                    this.progressService.unlockByRequirement(machine.owner(), "machine:tree_feller");
                    world.playSound(location, Sound.BLOCK_WOOD_BREAK, 0.45f, 1.0f);
                    return;
                }
            }
        }
    }

    private void tickMobCollector(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null || machine.ticksActive() % 20L != 0L) {
            return;
        }
        if (!this.isAllowedWorld(world)) {
            return;
        }
        final double radius = 4.0D + this.countUpgrade(machine, "range_upgrade");
        for (final Entity entity : world.getNearbyEntities(location.clone().add(0.5, 0.5, 0.5), radius, 2.5D, radius)) {
            if (!(entity instanceof LivingEntity living) || living instanceof Player || !this.isCollectableMob(living.getType()) || !this.isSafeMobTarget(living)) {
                continue;
            }
            final List<ItemStack> outputs = this.mobDropsFor(living.getType());
            if (outputs.isEmpty() || !this.canStoreAllOutputs(machine, outputs)) {
                continue;
            }
            final long energy = this.effectiveEnergyCost(machine, 14L);
            this.absorbNearbyEnergy(machine, location, energy);
            if (!machine.consumeEnergy(energy)) {
                return;
            }
            living.remove();
            this.storeOutputs(machine, outputs);
            this.progressService.incrementStat(machine.owner(), "mobs_collected", 1L);
            this.progressService.unlockByRequirement(machine.owner(), "machine:mob_collector");
            world.spawnParticle(Particle.SOUL, entity.getLocation().add(0.5, 0.5, 0.5), 10, 0.2, 0.3, 0.2, 0.02);
            world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.25f, 0.75f);
            return;
        }
    }

    private void tickFishingDock(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null || machine.ticksActive() % 16L != 0L) {
            return;
        }
        if (!this.isAllowedWorld(world)) {
            return;
        }
        if (this.countAdjacentWater(world, location) < 3) {
            return;
        }
        final ItemStack output = this.randomFishingLoot();
        final long energy = this.effectiveEnergyCost(machine, 7L);
        if (output == null || !this.canStoreOutput(machine, output)) {
            return;
        }
        this.absorbNearbyEnergy(machine, location, energy);
        if (!machine.consumeEnergy(energy)) {
            return;
        }
        this.storeOutput(machine, output);
        this.progressService.incrementStat(machine.owner(), "fish_caught", output.getAmount());
        this.progressService.unlockByRequirement(machine.owner(), "machine:fishing_dock");
        world.spawnParticle(Particle.SPLASH, location.clone().add(0.5, 0.2, 0.5), 10, 0.5, 0.05, 0.5, 0.05);
        world.playSound(location, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.35f, 1.1f);
    }

    private void tickAndroidStation(final PlacedMachine machine, final MachineDefinition definition, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final AndroidScriptProfile script = this.resolveAndroidScript(machine, location);
        if (machine.ticksActive() % 20L == 0L) {
            world.spawnParticle(script == null ? Particle.SMOKE : Particle.END_ROD, location.clone().add(0.5, 1.02, 0.5), 4, 0.18, 0.12, 0.18, 0.0);
        }
        if (script == null) {
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "待命：附近沒有可執行目標");
            return;
        }
        if (machine.ticksActive() % this.androidInterval(script, machine) != 0L) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "巡邏中：" + script.displayName());
            return;
        }
        if (script != AndroidScriptProfile.PLANET && !this.isAllowedWorld(world)) {
            this.setRuntimeState(machine, MachineRuntimeState.STANDBY, "目前世界停用戶外自動機");
            return;
        }
        if (script == AndroidScriptProfile.PLANET && !this.plugin.getPlanetService().isPlanetWorld(world)) {
            this.setRuntimeState(machine, MachineRuntimeState.STANDBY, "不是可採樣星球");
            return;
        }
        final boolean acted = switch (script) {
            case HARVEST -> this.runAndroidHarvest(machine, location, script);
            case LOGGING -> this.runAndroidLogging(machine, location, script);
            case SALVAGE -> this.runAndroidSalvage(machine, location, script);
            case PLANET -> this.runAndroidPlanetSampling(machine, location, script);
            case HUNT -> this.runAndroidHunt(machine, location, script);
        };
        if (acted) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "執行中：" + script.displayName());
            this.progressService.incrementStat(machine.owner(), "android_cycles", 1L);
            this.progressService.unlockByRequirement(machine.owner(), definition.id());
            this.progressService.unlockByRequirement(machine.owner(), "machine:" + definition.id());
            if (script == AndroidScriptProfile.HUNT) {
                this.progressService.incrementStat(machine.owner(), "android_hunts", 1L);
            }
            return;
        }
        this.setRuntimeState(machine, MachineRuntimeState.IDLE, "巡邏中：" + script.displayName());
    }

    private boolean runAndroidHarvest(final PlacedMachine machine, final Location location, final AndroidScriptProfile script) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final Location patrol = this.currentAndroidPatrolTarget(machine, location);
        final Block crop = world.getBlockAt(patrol.getBlockX(), patrol.getBlockY(), patrol.getBlockZ());
        if (!this.isSafeCropTarget(crop)) {
            this.advanceAndroidPatrol(machine, location);
            return false;
        }
        final BlockData data = crop.getBlockData();
        if (!(data instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
            this.advanceAndroidPatrol(machine, location);
            return false;
        }
        final boolean customCrop = this.techCropService.isTrackedCrop(crop);
        final List<ItemStack> outputs = customCrop
                ? this.techCropService.harvest(crop, true)
                : this.harvestOutputsFor(crop.getType());
        if (outputs.isEmpty() || !this.canStoreAllOutputs(machine, outputs)) {
            this.advanceAndroidPatrol(machine, location);
            return false;
        }
        if (!this.consumeAndroidRuntime(machine, location, script.baseEnergyCost(), this.androidFuelCost(script, machine))) {
            return false;
        }
        if (!customCrop) {
            ageable.setAge(0);
            crop.setBlockData(ageable, true);
        }
        this.storeOutputs(machine, outputs);
        this.progressService.incrementStat(machine.owner(), "android_harvested", outputs.stream().mapToInt(ItemStack::getAmount).sum());
        this.progressService.unlockItem(machine.owner(), "android_harvest_script");
        world.spawnParticle(Particle.HAPPY_VILLAGER, crop.getLocation().add(0.5, 0.8, 0.5), 10, 0.2, 0.2, 0.2, 0.01);
        world.playSound(crop.getLocation(), Sound.ITEM_BONE_MEAL_USE, 0.45f, 1.2f);
        this.advanceAndroidPatrol(machine, location);
        return true;
    }

    private boolean runAndroidLogging(final PlacedMachine machine, final Location location, final AndroidScriptProfile script) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final int maxLogs = 12 + this.countUpgrade(machine, "stack_upgrade") * 8;
        final Location patrol = this.currentAndroidPatrolTarget(machine, location);
        for (int dy = 0; dy <= Math.max(7, machine.androidPatrolHeight() + 4); dy++) {
            final Block origin = world.getBlockAt(patrol.getBlockX(), patrol.getBlockY() + dy, patrol.getBlockZ());
            if (!this.isTreeLog(origin.getType()) || !this.isSafeTreeTarget(origin)) {
                continue;
            }
            final List<Block> logs = this.collectConnectedLogs(origin, maxLogs);
            if (logs.isEmpty()) {
                continue;
            }
            final Material logType = logs.get(0).getType();
            final List<ItemStack> outputs = new ArrayList<>();
            outputs.add(this.buildStackForId(logType.name(), logs.size()));
            final ItemStack sapling = this.buildStackForId(this.saplingForLog(logType).name(), Math.max(1, logs.size() / 8));
            if (sapling != null) {
                outputs.add(sapling);
            }
            if (!this.canStoreAllOutputs(machine, outputs)) {
                this.advanceAndroidPatrol(machine, location);
                return false;
            }
            if (!this.consumeAndroidRuntime(machine, location, script.baseEnergyCost() + logs.size(), this.androidFuelCost(script, machine))) {
                return false;
            }
            for (final Block log : logs) {
                log.setType(Material.AIR, false);
                world.spawnParticle(Particle.BLOCK, log.getLocation().add(0.5, 0.5, 0.5), 8, 0.2, 0.2, 0.2, log.getBlockData());
            }
            this.storeOutputs(machine, outputs);
            this.progressService.incrementStat(machine.owner(), "android_logs_cut", logs.size());
            this.progressService.unlockItem(machine.owner(), "android_logging_script");
            world.playSound(location, Sound.BLOCK_WOOD_BREAK, 0.45f, 1.0f);
            this.advanceAndroidPatrol(machine, location);
            return true;
        }
        this.advanceAndroidPatrol(machine, location);
        return false;
    }

    private boolean runAndroidSalvage(final PlacedMachine machine, final Location location, final AndroidScriptProfile script) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final Location patrol = this.currentAndroidPatrolTarget(machine, location).clone().add(0.5, 0.5, 0.5);
        for (final Entity entity : world.getNearbyEntities(patrol, 1.5D, 1.8D, 1.5D)) {
            if (!(entity instanceof Item item)) {
                continue;
            }
            if (!this.isSafeVacuumItem(item)) {
                continue;
            }
            final ItemStack stack = item.getItemStack();
            if (stack == null || stack.getType() == Material.AIR || !this.canStoreOutput(machine, stack)) {
                continue;
            }
            if (!this.consumeAndroidRuntime(machine, location, script.baseEnergyCost(), this.androidFuelCost(script, machine))) {
                return false;
            }
            this.storeOutput(machine, stack.clone());
            item.remove();
            this.progressService.incrementStat(machine.owner(), "android_salvaged", stack.getAmount());
            this.progressService.unlockItem(machine.owner(), "android_salvage_script");
            world.spawnParticle(Particle.WITCH, entity.getLocation().add(0.0, 0.2, 0.0), 8, 0.15, 0.15, 0.15, 0.0);
            world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.2f, 1.3f);
            this.advanceAndroidPatrol(machine, location);
            return true;
        }
        this.advanceAndroidPatrol(machine, location);
        return false;
    }

    private boolean runAndroidHunt(final PlacedMachine machine, final Location location, final AndroidScriptProfile script) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final Location patrol = this.currentAndroidPatrolTarget(machine, location).clone().add(0.5, 0.5, 0.5);
        for (final Entity entity : world.getNearbyEntities(patrol, 1.8D, 2.2D, 1.8D)) {
            if (!(entity instanceof LivingEntity living) || living instanceof Player || !this.isCollectableMob(living.getType()) || !this.isSafeMobTarget(living)) {
                continue;
            }
            final List<ItemStack> outputs = this.mobDropsFor(living.getType());
            if (outputs.isEmpty() || !this.canStoreAllOutputs(machine, outputs)) {
                this.advanceAndroidPatrol(machine, location);
                return false;
            }
            if (!this.consumeAndroidRuntime(machine, location, script.baseEnergyCost() + outputs.stream().mapToInt(ItemStack::getAmount).sum(), this.androidFuelCost(script, machine))) {
                return false;
            }
            final Location entityLocation = living.getLocation();
            living.remove();
            this.storeOutputs(machine, outputs);
            this.progressService.incrementStat(machine.owner(), "android_mobs_hunted", 1L);
            world.spawnParticle(Particle.SOUL, entityLocation.clone().add(0.0, 0.7, 0.0), 12, 0.22, 0.28, 0.22, 0.02);
            world.spawnParticle(Particle.CRIT, entityLocation.clone().add(0.0, 0.8, 0.0), 10, 0.22, 0.28, 0.22, 0.01);
            world.playSound(entityLocation, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 0.8f);
            this.advanceAndroidPatrol(machine, location);
            return true;
        }
        this.advanceAndroidPatrol(machine, location);
        return false;
    }

    private boolean runAndroidPlanetSampling(final PlacedMachine machine, final Location location, final AndroidScriptProfile script) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final Location patrol = this.currentAndroidPatrolTarget(machine, location);
        for (int dy = -1; dy <= Math.max(3, machine.androidPatrolHeight() + 1); dy++) {
            final Block target = world.getBlockAt(patrol.getBlockX(), patrol.getBlockY() + dy, patrol.getBlockZ());
            final List<ItemStack> preview = this.plugin.getPlanetService().previewMachineHarvest(target);
            if (preview.isEmpty() || !this.canStoreAllOutputs(machine, preview)) {
                continue;
            }
            final int fuelCost = this.androidFuelCost(script, machine);
            if (!this.consumeAndroidRuntime(machine, location, script.baseEnergyCost() + preview.stream().mapToInt(ItemStack::getAmount).sum(), fuelCost)) {
                return false;
            }
            final List<ItemStack> outputs = this.plugin.getPlanetService().harvestForMachine(target);
            if (outputs.isEmpty()) {
                this.addEnergyCapped(machine, this.effectiveEnergyCost(machine, this.androidEnergyCost(script.baseEnergyCost(), machine)));
                machine.setAndroidFuel(Math.min(ANDROID_MAX_FUEL, machine.androidFuel() + fuelCost));
                this.advanceAndroidPatrol(machine, location);
                return false;
            }
            this.storeOutputs(machine, outputs);
            this.progressService.incrementStat(machine.owner(), "android_planet_samples", outputs.stream().mapToInt(ItemStack::getAmount).sum());
            this.progressService.unlockItem(machine.owner(), "android_planet_script");
            world.spawnParticle(Particle.END_ROD, target.getLocation().add(0.5, 0.65, 0.5), 8, 0.2, 0.2, 0.2, 0.01);
            world.playSound(target.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.35f, 1.35f);
            this.advanceAndroidPatrol(machine, location);
            return true;
        }
        this.advanceAndroidPatrol(machine, location);
        return false;
    }

    private void tickAndroidItemInterface(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null || machine.ticksActive() % 6L != 0L) {
            return;
        }
        final PlacedMachine station = this.findLinkedAndroidStation(machine, location);
        if (station == null) {
            return;
        }
        final ItemStack filter = machine.upgradeAt(ANDROID_FILTER_SLOT);
        final String filterId = this.resolveStackId(filter);
        final Inventory targetInventory = this.interfaceTargetInventory(location);
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            final ItemStack current = station.outputAt(slot);
            if (current == null || current.getType() == Material.AIR) {
                continue;
            }
            final String currentId = this.resolveStackId(current);
            if (filterId != null && !filterId.equalsIgnoreCase(currentId)) {
                continue;
            }
            final boolean canMove = targetInventory != null
                    ? this.canStoreInInventory(targetInventory, current)
                    : this.canStoreOutput(machine, current);
            if (!canMove) {
                continue;
            }
            final long energy = this.effectiveEnergyCost(machine, 4L);
            this.absorbNearbyEnergy(machine, location, energy);
            if (!machine.consumeEnergy(energy)) {
                return;
            }
            if (targetInventory != null) {
                this.storeInInventory(targetInventory, current);
            } else {
                this.storeOutput(machine, current);
            }
            station.setOutputAt(slot, null);
            this.progressService.incrementStat(machine.owner(), "android_interface_exports", current.getAmount());
            world.spawnParticle(Particle.WAX_OFF, location.clone().add(0.5, 0.8, 0.5), 6, 0.18, 0.18, 0.18, 0.0);
            world.playSound(location, Sound.BLOCK_CHAIN_PLACE, 0.2f, 1.4f);
            return;
        }
    }

    private void tickAndroidFuelInterface(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null || machine.ticksActive() % 10L != 0L) {
            return;
        }
        final PlacedMachine station = this.findLinkedAndroidStation(machine, location);
        if (station == null || station.androidFuel() >= ANDROID_INTERFACE_LOW_WATERMARK) {
            return;
        }
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            final ItemStack stack = machine.inputAt(slot);
            final AndroidFuelProfile profile = this.androidFuelProfile(stack);
            if (stack == null || profile == null) {
                continue;
            }
            final int acceptedFuel = Math.min(profile.fuelUnits(), this.availableAndroidFuelCapacity(station));
            if (acceptedFuel <= 0) {
                return;
            }
            if (profile.remainder() != null && !this.canStoreOutput(machine, profile.remainder())) {
                continue;
            }
            final long energy = this.effectiveEnergyCost(machine, 3L);
            this.absorbNearbyEnergy(machine, location, energy);
            if (!machine.consumeEnergy(energy)) {
                return;
            }
            stack.setAmount(stack.getAmount() - 1);
            if (stack.getAmount() <= 0) {
                machine.setInputAt(slot, null);
            } else {
                machine.setInputAt(slot, stack);
            }
            station.setAndroidFuel(Math.min(ANDROID_MAX_FUEL, station.androidFuel() + acceptedFuel));
            if (profile.remainder() != null) {
                this.storeOutput(machine, profile.remainder().clone());
            }
            this.progressService.incrementStat(machine.owner(), "android_fuel_loaded", acceptedFuel);
            world.spawnParticle(Particle.FLAME, location.clone().add(0.5, 0.8, 0.5), 5, 0.15, 0.15, 0.15, 0.01);
            world.playSound(location, Sound.ITEM_BUCKET_FILL_LAVA, 0.18f, 1.4f);
            return;
        }
    }

    private boolean consumeAndroidRuntime(final PlacedMachine machine,
                                          final Location location,
                                          final long baseEnergyCost,
                                          final int fuelCost) {
        final long energy = this.effectiveEnergyCost(machine, this.androidEnergyCost(baseEnergyCost, machine));
        if (machine.androidFuel() < fuelCost) {
            return false;
        }
        this.absorbNearbyEnergy(machine, location, energy);
        if (machine.storedEnergy() < energy) {
            return false;
        }
        machine.consumeAndroidFuel(fuelCost);
        return machine.consumeEnergy(energy);
    }

    private AndroidScriptProfile androidScript(final PlacedMachine machine) {
        if (machine == null) {
            return null;
        }
        return AndroidScriptProfile.fromItemId(this.resolveStackId(machine.upgradeAt(ANDROID_SCRIPT_SLOT)));
    }

    private AndroidScriptProfile resolveAndroidScript(final PlacedMachine machine, final Location location) {
        final AndroidScriptProfile configured = this.androidScript(machine);
        return configured != null ? configured : this.inferAndroidScript(machine, location);
    }

    private AndroidScriptProfile inferAndroidScript(final PlacedMachine machine, final Location location) {
        final World world = location == null ? null : location.getWorld();
        if (machine == null || world == null) {
            return null;
        }
        if (this.plugin.getPlanetService().isPlanetWorld(world) && this.hasAndroidPlanetTarget(machine, location)) {
            return AndroidScriptProfile.PLANET;
        }
        if (this.hasAndroidHarvestTarget(machine, location)) {
            return AndroidScriptProfile.HARVEST;
        }
        if (this.hasAndroidLoggingTarget(machine, location)) {
            return AndroidScriptProfile.LOGGING;
        }
        if (this.hasAndroidHuntTarget(machine, location)) {
            return AndroidScriptProfile.HUNT;
        }
        if (this.hasAndroidSalvageTarget(machine, location)) {
            return AndroidScriptProfile.SALVAGE;
        }
        return null;
    }

    private boolean hasAndroidHarvestTarget(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final Block crop = world.getBlockAt(this.currentAndroidPatrolTarget(machine, location));
        if (!this.isSafeCropTarget(crop)) {
            return false;
        }
        final BlockData data = crop.getBlockData();
        return data instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge();
    }

    private boolean hasAndroidLoggingTarget(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final Location patrol = this.currentAndroidPatrolTarget(machine, location);
        for (int dy = 0; dy <= Math.max(7, machine.androidPatrolHeight() + 4); dy++) {
            final Block origin = world.getBlockAt(patrol.getBlockX(), patrol.getBlockY() + dy, patrol.getBlockZ());
            if (this.isTreeLog(origin.getType()) && this.isSafeTreeTarget(origin)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAndroidHuntTarget(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final Location patrol = this.currentAndroidPatrolTarget(machine, location).clone().add(0.5, 0.5, 0.5);
        for (final Entity entity : world.getNearbyEntities(patrol, 1.8D, 2.2D, 1.8D)) {
            if (entity instanceof LivingEntity living && !(living instanceof Player) && this.isCollectableMob(living.getType()) && this.isSafeMobTarget(living)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAndroidSalvageTarget(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final Location patrol = this.currentAndroidPatrolTarget(machine, location).clone().add(0.5, 0.5, 0.5);
        for (final Entity entity : world.getNearbyEntities(patrol, 1.5D, 1.8D, 1.5D)) {
            if (entity instanceof Item item && this.isSafeVacuumItem(item) && item.getItemStack() != null && item.getItemStack().getType() != Material.AIR) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAndroidPlanetTarget(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return false;
        }
        final Location patrol = this.currentAndroidPatrolTarget(machine, location);
        for (int dy = -1; dy <= Math.max(3, machine.androidPatrolHeight() + 1); dy++) {
            final Block target = world.getBlockAt(patrol.getBlockX(), patrol.getBlockY() + dy, patrol.getBlockZ());
            if (!this.plugin.getPlanetService().previewMachineHarvest(target).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private long androidInterval(final AndroidScriptProfile script, final PlacedMachine machine) {
        if (script == null) {
            return 20L;
        }
        long interval = script.intervalTicks();
        if (this.hasAndroidCard(machine, ANDROID_OVERCLOCK_CARD)) {
            interval = Math.max(2L, interval / 2L);
        }
        return interval;
    }

    private long androidEnergyCost(final long baseEnergyCost, final PlacedMachine machine) {
        double scaled = Math.max(1L, baseEnergyCost);
        if (this.hasAndroidCard(machine, ANDROID_OVERCLOCK_CARD)) {
            scaled *= 1.6D;
        }
        if (this.hasAndroidCard(machine, ANDROID_EFFICIENCY_CARD)) {
            scaled *= 0.7D;
        }
        return Math.max(1L, Math.round(scaled));
    }

    private int androidFuelCost(final AndroidScriptProfile script, final PlacedMachine machine) {
        if (script == null) {
            return 1;
        }
        int fuelCost = script.fuelCost();
        if (this.hasAndroidCard(machine, ANDROID_OVERCLOCK_CARD)) {
            fuelCost += 1;
        }
        if (this.hasAndroidCard(machine, ANDROID_EFFICIENCY_CARD)) {
            fuelCost = Math.max(1, fuelCost - 1);
        }
        return fuelCost;
    }

    private boolean hasAndroidCard(final PlacedMachine machine, final String cardId) {
        if (machine == null || cardId == null || cardId.isBlank()) {
            return false;
        }
        for (int slot = 0; slot < UPGRADE_SLOTS.length; slot++) {
            if (slot == ANDROID_SCRIPT_SLOT) {
                continue;
            }
            if (cardId.equalsIgnoreCase(this.resolveStackId(machine.upgradeAt(slot)))) {
                return true;
            }
        }
        return false;
    }

    private int effectiveAndroidPatrolRadius(final PlacedMachine machine) {
        int radius = machine == null ? 2 : machine.androidPatrolRadius();
        radius += this.countUpgrade(machine, "range_upgrade");
        if (this.hasAndroidCard(machine, ANDROID_EXPANSION_CARD)) {
            radius += 2;
        }
        return Math.max(1, Math.min(8, radius));
    }

    private int effectiveAndroidPatrolHeight(final PlacedMachine machine) {
        int height = machine == null ? 1 : machine.androidPatrolHeight();
        if (this.hasAndroidCard(machine, ANDROID_EXPANSION_CARD)) {
            height += 1;
        }
        return Math.max(0, Math.min(6, height));
    }

    private Location currentAndroidPatrolTarget(final PlacedMachine machine, final Location origin) {
        final List<int[]> offsets = this.androidPatrolOffsets(machine);
        if (offsets.isEmpty()) {
            return origin.clone();
        }
        final int index = Math.floorMod(machine.androidRouteCursor(), offsets.size());
        final int[] offset = offsets.get(index);
        return origin.clone().add(offset[0], offset[1], offset[2]);
    }

    private void advanceAndroidPatrol(final PlacedMachine machine, final Location origin) {
        final int size = this.androidPatrolOffsets(machine).size();
        if (size <= 0) {
            machine.setAndroidRouteCursor(0);
            return;
        }
        machine.setAndroidRouteCursor((machine.androidRouteCursor() + 1) % size);
    }

    private List<int[]> androidPatrolOffsets(final PlacedMachine machine) {
        final int radius = this.effectiveAndroidPatrolRadius(machine);
        final int height = this.effectiveAndroidPatrolHeight(machine);
        final String mode = this.normalizeAndroidRouteMode(machine == null ? null : machine.androidRouteMode());
        final List<int[]> offsets = new ArrayList<>();
        switch (mode) {
            case "SPIRAL" -> {
                offsets.add(new int[]{0, 0, 0});
                for (int y = 0; y <= height; y++) {
                    for (int ring = 1; ring <= radius; ring++) {
                        for (int z = -ring + 1; z <= ring; z++) {
                            offsets.add(new int[]{ring, y, z});
                        }
                        for (int x = ring - 1; x >= -ring; x--) {
                            offsets.add(new int[]{x, y, ring});
                        }
                        for (int z = ring - 1; z >= -ring; z--) {
                            offsets.add(new int[]{-ring, y, z});
                        }
                        for (int x = -ring + 1; x <= ring; x++) {
                            offsets.add(new int[]{x, y, -ring});
                        }
                    }
                }
            }
            case "RING" -> {
                for (int y = 0; y <= height; y++) {
                    for (int ring = radius; ring >= 1; ring--) {
                        for (int x = -ring; x <= ring; x++) {
                            offsets.add(new int[]{x, y, -ring});
                            offsets.add(new int[]{x, y, ring});
                        }
                        for (int z = -ring + 1; z <= ring - 1; z++) {
                            offsets.add(new int[]{-ring, y, z});
                            offsets.add(new int[]{ring, y, z});
                        }
                    }
                    offsets.add(new int[]{0, y, 0});
                }
            }
            default -> {
                for (int y = 0; y <= height; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if ((z + radius) % 2 == 0) {
                            for (int x = -radius; x <= radius; x++) {
                                offsets.add(new int[]{x, y, z});
                            }
                        } else {
                            for (int x = radius; x >= -radius; x--) {
                                offsets.add(new int[]{x, y, z});
                            }
                        }
                    }
                }
            }
        }
        return offsets;
    }

    private String normalizeAndroidRouteMode(final String mode) {
        final String normalized = mode == null ? "SERPENTINE" : mode.trim().toUpperCase(java.util.Locale.ROOT);
        for (final String candidate : ANDROID_ROUTE_MODES) {
            if (candidate.equals(normalized)) {
                return candidate;
            }
        }
        return ANDROID_ROUTE_MODES[0];
    }

    private String nextAndroidRouteMode(final String current) {
        final String normalized = this.normalizeAndroidRouteMode(current);
        for (int index = 0; index < ANDROID_ROUTE_MODES.length; index++) {
            if (ANDROID_ROUTE_MODES[index].equals(normalized)) {
                return ANDROID_ROUTE_MODES[(index + 1) % ANDROID_ROUTE_MODES.length];
            }
        }
        return ANDROID_ROUTE_MODES[0];
    }

    private String androidRouteModeLabel(final String mode) {
        return switch (this.normalizeAndroidRouteMode(mode)) {
            case "SPIRAL" -> "螺旋巡邏";
            case "RING" -> "環帶巡邏";
            default -> "蛇形巡邏";
        };
    }

    private List<String> androidCardSummaryLines(final PlacedMachine machine) {
        final List<String> lines = new ArrayList<>();
        if (this.hasAndroidCard(machine, ANDROID_OVERCLOCK_CARD)) {
            lines.add("超頻卡：更快執行，但更耗能 / 燃料");
        }
        if (this.hasAndroidCard(machine, ANDROID_EFFICIENCY_CARD)) {
            lines.add("節能卡：降低 EU 與燃料消耗");
        }
        if (this.hasAndroidCard(machine, ANDROID_EXPANSION_CARD)) {
            lines.add("擴域卡：增加巡邏半徑與高度");
        }
        if (lines.isEmpty()) {
            lines.add("尚未插入進階程序卡");
            lines.add("也可不放程序晶片，直接自動偵測附近目標");
        }
        return lines;
    }

    private PlacedMachine findLinkedAndroidStation(final PlacedMachine machine, final Location location) {
        PlacedMachine closest = null;
        int closestDistance = Integer.MAX_VALUE;
        for (final PlacedMachine candidate : this.machines.values()) {
            if (!candidate.owner().equals(machine.owner()) || !"android_station".equalsIgnoreCase(candidate.machineId())) {
                continue;
            }
            if (!candidate.locationKey().worldName().equalsIgnoreCase(machine.locationKey().worldName())) {
                continue;
            }
            final int dx = candidate.locationKey().x() - location.getBlockX();
            final int dy = candidate.locationKey().y() - location.getBlockY();
            final int dz = candidate.locationKey().z() - location.getBlockZ();
            final int distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared > ANDROID_LINK_RADIUS * ANDROID_LINK_RADIUS || distanceSquared >= closestDistance) {
                continue;
            }
            closestDistance = distanceSquared;
            closest = candidate;
        }
        return closest;
    }

    private Inventory interfaceTargetInventory(final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return null;
        }
        return this.containerInventory(world.getBlockAt(location).getRelative(BlockFace.UP));
    }

    private AndroidFuelProfile androidFuelProfile(final ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return null;
        }
        return switch (this.resolveStackId(stack)) {
            case "coal", "coal_dust", "charcoal" -> new AndroidFuelProfile(8, null);
            case "blaze_powder" -> new AndroidFuelProfile(12, null);
            case "refined_oil" -> new AndroidFuelProfile(24, null);
            case "energy_cell" -> new AndroidFuelProfile(32, null);
            case "lithium_cell" -> new AndroidFuelProfile(48, null);
            case "plasma_cell" -> new AndroidFuelProfile(72, null);
            case "lava_bucket" -> new AndroidFuelProfile(64, new ItemStack(Material.BUCKET));
            default -> null;
        };
    }

    private int availableAndroidFuelCapacity(final PlacedMachine machine) {
        return Math.max(0, ANDROID_MAX_FUEL - machine.androidFuel());
    }

    private void tickRecycler(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }

        if (this.processMachineRecipes(machine, location, "recycled_items", Particle.SMOKE, Sound.BLOCK_SMOKER_SMOKE)) {
            this.progressService.unlockItem(machine.owner(), "recycled_material");
            this.progressService.unlockItem(machine.owner(), "plastic");
            this.progressService.unlockByRequirement(machine.owner(), "recycler");
        }
    }

    private void tickVacuumInlet(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        if (!this.isAllowedWorld(world)) {
            return;
        }
        final double radius = 2.5D + this.countUpgrade(machine, "range_upgrade");
        for (final var entity : world.getNearbyEntities(location.clone().add(0.5, 0.5, 0.5), radius, 1.5D, radius)) {
            if (!(entity instanceof Item itemEntity)) {
                continue;
            }
            if (!this.isSafeVacuumItem(itemEntity)) {
                continue;
            }
            final ItemStack stack = itemEntity.getItemStack();
            final long energy = this.effectiveEnergyCost(machine, 3L);
            if (stack.getType() == Material.AIR || !this.canStoreOutput(machine, stack)) {
                continue;
            }
            this.absorbNearbyEnergy(machine, location, energy);
            if (!machine.consumeEnergy(energy)) {
                return;
            }
            this.storeOutput(machine, stack.clone());
            itemEntity.remove();
            this.progressService.incrementStat(machine.owner(), "vacuum_collected", stack.getAmount());
            this.progressService.unlockByRequirement(machine.owner(), "machine:vacuum_inlet");
            world.spawnParticle(Particle.PORTAL, location.clone().add(0.5, 0.8, 0.5), 12, 0.25, 0.25, 0.25, 0.2);
            world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.15f, 1.6f);
            return;
        }
    }

    private void tickBatteryBank(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }

        final long requiredEnergy = 2L;
        this.absorbNearbyEnergy(machine, location, requiredEnergy);
        this.distributeNearbyEnergy(machine, location, 4L);
        if (this.processMachineRecipes(machine, location, "battery_bank_cycles", Particle.GLOW, Sound.BLOCK_AMETHYST_BLOCK_STEP)) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "加工中");
            return;
        }
        final RecipeRuntimeSnapshot snapshot = this.inspectRecipeRuntime(machine);
        this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
        world.spawnParticle(Particle.GLOW, location.clone().add(0.5, 1.0, 0.5), 6, 0.25, 0.25, 0.25, 0.01);
    }

    // ── 基因雞工程：基因定序器 ─────────────────────────────
    private void tickGeneticSequencer(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final long energy = 3L;
        this.absorbNearbyEnergy(machine, location, energy);

        // 搜尋輸入格中未定序的口袋雞
        for (int slot = 0; slot < 9; slot++) {
            final ItemStack input = machine.inputAt(slot);
            if (input == null || input.getType() == Material.AIR) {
                continue;
            }
            if (!this.itemFactory.isPocketChicken(input)) {
                continue;
            }
            if (this.itemFactory.isChickenSequenced(input)) {
                continue; // 已定序，跳過
            }
            // 消耗能源
            if (!machine.consumeEnergy(energy)) {
                this.setRuntimeState(machine, MachineRuntimeState.NO_POWER, "電力不足");
                return;
            }
            // 讀取 DNA，建立已定序版本
            final String dna = this.itemFactory.getChickenDna(input);
            if (dna == null) {
                continue;
            }
            final String resourceName = this.chickenGenetics.resourceNameZh(dna);
            final ItemStack sequenced = this.itemFactory.buildPocketChicken(dna, true, resourceName);
            // 嘗試放入輸出格
            if (!this.canStoreOutput(machine, sequenced)) {
                this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "輸出已滿");
                return;
            }
            // 消耗輸入，存入輸出
            input.setAmount(input.getAmount() - 1);
            if (input.getAmount() <= 0) {
                machine.setInputAt(slot, null);
            }
            this.storeOutput(machine, sequenced);
            this.progressService.incrementStat(machine.owner(), "chickens_sequenced", 1);
            this.progressService.unlockByRequirement(machine.owner(), "genetic_sequencer");
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "定序中");
            world.spawnParticle(Particle.ENCHANT, location.clone().add(0.5, 1.0, 0.5), 15, 0.3, 0.3, 0.3, 0.1);
            world.playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.4f, 1.4f);
            return;
        }
        this.setRuntimeState(machine, MachineRuntimeState.NO_INPUT, "等待未定序口袋雞");
    }

    // ── 基因雞工程：私人雞舍 ──────────────────────────────
    private void tickPrivateCoop(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final long energy = 2L;
        this.absorbNearbyEnergy(machine, location, energy);

        // 搜尋兩隻已定序口袋雞
        int parentSlotA = -1;
        int parentSlotB = -1;
        String dnaA = null;
        String dnaB = null;
        for (int slot = 0; slot < 9; slot++) {
            final ItemStack input = machine.inputAt(slot);
            if (input == null || input.getType() == Material.AIR) {
                continue;
            }
            if (!this.itemFactory.isPocketChicken(input) || !this.itemFactory.isChickenSequenced(input)) {
                continue;
            }
            final String dna = this.itemFactory.getChickenDna(input);
            if (dna == null || dna.length() != 12) {
                continue;
            }
            if (parentSlotA < 0) {
                parentSlotA = slot;
                dnaA = dna;
            } else {
                parentSlotB = slot;
                dnaB = dna;
                break;
            }
        }
        if (parentSlotA < 0 || parentSlotB < 0) {
            this.setRuntimeState(machine, MachineRuntimeState.NO_INPUT, "需要兩隻已定序口袋雞");
            return;
        }
        if (!machine.consumeEnergy(energy)) {
            this.setRuntimeState(machine, MachineRuntimeState.NO_POWER, "電力不足");
            return;
        }
        // 繁殖後代
        final String childDna = this.chickenGenetics.breed(dnaA, dnaB);
        final String childResource = this.chickenGenetics.resourceNameZh(childDna);
        final ItemStack child = this.itemFactory.buildPocketChicken(childDna, true, childResource);
        if (!this.canStoreOutput(machine, child)) {
            machine.addEnergy(energy); // 退還能源
            this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "輸出已滿");
            return;
        }
        this.storeOutput(machine, child);
        this.progressService.incrementStat(machine.owner(), "chickens_bred", 1);
        this.progressService.unlockByRequirement(machine.owner(), "private_coop");
        this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "繁殖中");
        world.spawnParticle(Particle.HEART, location.clone().add(0.5, 1.2, 0.5), 5, 0.3, 0.3, 0.3, 0.0);
        world.playSound(location, Sound.ENTITY_CHICKEN_EGG, 0.5f, 1.2f);
    }

    // ── 基因雞工程：激發室 ───────────────────────────────
    private void tickExcitationChamber(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final long energy = 4L;
        this.absorbNearbyEnergy(machine, location, energy);

        // 搜尋輸入格中有資源能力的已定序口袋雞
        for (int slot = 0; slot < 9; slot++) {
            final ItemStack input = machine.inputAt(slot);
            if (input == null || input.getType() == Material.AIR) {
                continue;
            }
            if (!this.itemFactory.isPocketChicken(input) || !this.itemFactory.isChickenSequenced(input)) {
                continue;
            }
            final String dna = this.itemFactory.getChickenDna(input);
            if (dna == null || !this.chickenGenetics.canProduceResource(dna)) {
                continue;
            }
            // 消耗能源
            if (!machine.consumeEnergy(energy)) {
                this.setRuntimeState(machine, MachineRuntimeState.NO_POWER, "電力不足");
                return;
            }
            // 產出資源
            final String resourceId = this.chickenGenetics.resourceId(dna);
            final ItemStack product = this.buildStackForId(resourceId, 1);
            if (product == null) {
                continue;
            }
            if (!this.canStoreOutput(machine, product)) {
                machine.addEnergy(energy);
                this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "輸出已滿");
                return;
            }
            this.storeOutput(machine, product);
            final String resourceName = this.chickenGenetics.resourceNameZh(dna);
            this.progressService.incrementStat(machine.owner(), "chicken_resources_produced", 1);
            this.progressService.unlockByRequirement(machine.owner(), "excitation_chamber");
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "產出：" + resourceName);
            world.spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0.5, 1.0, 0.5), 8, 0.25, 0.25, 0.25, 0.02);
            world.playSound(location, Sound.ENTITY_CHICKEN_AMBIENT, 0.3f, 1.5f);
            return; // 雞不被消耗，留在輸入格
        }
        this.setRuntimeState(machine, MachineRuntimeState.NO_INPUT, "需要有資源能力的口袋雞");
    }

    private void tickPlanetaryGate(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        this.absorbNearbyEnergy(machine, location, 12L);
        world.spawnParticle(Particle.END_ROD, location.clone().add(0.5, 1.0, 0.5), 2, 0.18, 0.12, 0.18, 0.0);
    }

    private void tickGreenhouse(final PlacedMachine machine,
                               final MachineDefinition definition,
                               final Location location) {
        this.tickProcessor(machine, definition, location, Particle.SPORE_BLOSSOM_AIR, "greenhouse_cycles");
        final World world = location.getWorld();
        if (world == null || machine.ticksActive() % 6L != 0L) {
            return;
        }
        final int radius = 2;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                final Block crop = world.getBlockAt(location.getBlockX() + dx, location.getBlockY(), location.getBlockZ() + dz);
                final BlockData data = crop.getBlockData();
                if (!(data instanceof Ageable ageable) || ageable.getAge() >= ageable.getMaximumAge()) {
                    continue;
                }
                final long energy = this.effectiveEnergyCost(machine, 2L);
                this.absorbNearbyEnergy(machine, location, energy);
                if (!machine.consumeEnergy(energy)) {
                    return;
                }
                ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1));
                crop.setBlockData(ageable, true);
                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, crop.getLocation().add(0.5, 0.75, 0.5), 4, 0.15, 0.15, 0.15, 0.01);
                return;
            }
        }
    }

    private void tickQuarryDrill(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        if (!this.isAllowedWorld(world)) {
            return;
        }
        final Block controller = world.getBlockAt(location);
        final QuarryRigLayout layout = this.quarryRigLayout(controller);
        if (layout == null) {
            this.animateQuarryRig(controller, false);
            this.refreshMachineDisplayNow(machine.locationKey());
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.18, 0.18, 0.18, 0.0);
            return;
        }
        if (!this.isPrimaryQuarryMachine(machine, layout)) {
            this.setBlastFurnaceLit(controller, false);
            return;
        }
        final Block core = layout.core();
        if (!machine.enabled()) {
            this.quarryBusy.remove(LocationKey.from(this.primaryQuarryMachine(layout).getLocation()));
            this.quarryWarmedUp.remove(LocationKey.from(this.primaryQuarryMachine(layout).getLocation()));
            this.animateQuarryRig(core, false);
            this.refreshQuarryDisplays(layout);
            return;
        }
        final LocationKey controlKey = LocationKey.from(this.primaryQuarryMachine(layout).getLocation());
        if (this.quarryBusy.contains(controlKey)) {
            return;
        }
        if (this.findNextQuarryTarget(machine, core) == null) {
            this.animateQuarryRig(core, false);
            this.refreshQuarryDisplays(layout);
            return;
        }
        if (!this.canQuarryStartOperation(machine, layout, core)) {
            this.animateQuarryRig(core, false);
            this.refreshQuarryDisplays(layout);
            world.spawnParticle(Particle.SMOKE, core.getLocation().clone().add(0.5, 1.0, 0.5), 8, 0.25, 0.18, 0.25, 0.01);
            return;
        }
        if (!this.quarryWarmedUp.contains(controlKey)) {
            this.startQuarryWarmUp(machine, layout);
            return;
        }
        this.startQuarryRun(machine, layout);
    }

    private void startQuarryWarmUp(final PlacedMachine machine, final QuarryRigLayout layout) {
        final LocationKey controlKey = LocationKey.from(this.primaryQuarryMachine(layout).getLocation());
        if (!this.quarryBusy.add(controlKey)) {
            return;
        }
        this.refreshQuarryDisplays(layout);
        final Location coreLocation = layout.core().getLocation();
        this.scheduleQuarryPistonStep(coreLocation, layout.machineA().getLocation(), true, 4L);
        this.scheduleQuarryPistonStep(coreLocation, layout.machineA().getLocation(), false, 10L);
        this.scheduleQuarryPistonStep(coreLocation, layout.machineB().getLocation(), true, 18L);
        this.scheduleQuarryPistonStep(coreLocation, layout.machineB().getLocation(), false, 24L);
        this.scheduleQuarryPistonStep(coreLocation, layout.machineA().getLocation(), true, 27L);
        this.scheduleQuarryPistonStep(coreLocation, layout.machineA().getLocation(), false, 32L);
        this.scheduleQuarryPistonStep(coreLocation, layout.machineB().getLocation(), true, 35L);
        this.scheduleQuarryPistonStep(coreLocation, layout.machineB().getLocation(), false, 39L);
        this.scheduler.runRegionDelayed(coreLocation, task -> {
            this.quarryWarmedUp.add(controlKey);
            this.quarryBusy.remove(controlKey);
            this.refreshQuarryDisplays(layout);
            final PlacedMachine refreshed = this.machines.get(controlKey);
            final Block machineBlock = this.machineBlock(controlKey);
            if (refreshed != null && refreshed.enabled() && machineBlock != null) {
                this.tickQuarryDrill(refreshed, machineBlock.getLocation());
            }
        }, 41L);
    }

    private void startQuarryRun(final PlacedMachine machine, final QuarryRigLayout layout) {
        final LocationKey controlKey = LocationKey.from(this.primaryQuarryMachine(layout).getLocation());
        if (!this.quarryBusy.add(controlKey)) {
            return;
        }
        this.refreshQuarryDisplays(layout);
        final Location coreLocation = layout.core().getLocation();
        this.scheduleQuarryPistonStep(coreLocation, layout.machineA().getLocation(), true, 1L);
        this.scheduleQuarryPistonStep(coreLocation, layout.machineA().getLocation(), false, 3L);
        this.scheduleQuarryPistonStep(coreLocation, layout.machineB().getLocation(), true, 4L);
        this.scheduleQuarryPistonStep(coreLocation, layout.machineB().getLocation(), false, 6L);
        this.scheduler.runRegionDelayed(coreLocation, task -> {
            final PlacedMachine refreshed = this.machines.get(controlKey);
            final Block machineBlock = this.machineBlock(controlKey);
            final QuarryRigLayout refreshedLayout = machineBlock == null ? null : this.quarryRigLayout(machineBlock);
            boolean mined = false;
            if (refreshed != null && refreshed.enabled() && refreshedLayout != null) {
                mined = this.performQuarryMiningStep(refreshed, refreshedLayout);
            }
            this.quarryBusy.remove(controlKey);
            if (refreshedLayout != null) {
                this.refreshQuarryDisplays(refreshedLayout);
            }
            if (mined && refreshed != null && refreshed.enabled() && machineBlock != null) {
                final long repeatDelay = Math.max(2L, 6L - Math.min(4L, this.quarryOperationsPerTick(refreshed.machineId()) - 1L));
                this.scheduler.runRegionDelayed(coreLocation, next -> this.tickQuarryDrill(refreshed, machineBlock.getLocation()), repeatDelay);
            } else if (refreshedLayout != null) {
                this.animateQuarryRig(refreshedLayout.core(), false);
            }
        }, 7L);
    }

    private void scheduleQuarryPistonStep(final Location coreLocation, final Location machineLocation, final boolean extended, final long delay) {
        this.scheduler.runRegionDelayed(coreLocation, task -> {
            final World world = machineLocation.getWorld();
            if (world == null) {
                return;
            }
            this.setQuarryPistonState(world.getBlockAt(machineLocation), extended);
        }, delay);
    }

    private boolean performQuarryMiningStep(final PlacedMachine machine, final QuarryRigLayout layout) {
        final Block core = layout.core();
        final Location coreLocation = core.getLocation();
        final Block target = this.findNextQuarryTarget(machine, core);
        if (target == null) {
            return false;
        }
        final QuarryDrop drop = this.quarryDropFor(target.getType());
        if (drop == null) {
            return false;
        }
        final ItemStack output = this.buildStackForId(drop.outputId(), drop.amount() + this.countUpgrade(machine, "stack_upgrade") + this.quarryOutputBonus(machine.machineId()));
        if (output == null || !this.canStoreQuarryOutput(layout, machine, output)) {
            return false;
        }
        if (!this.ensureQuarryFuel(layout, machine)) {
            core.getWorld().spawnParticle(Particle.SMOKE, coreLocation.clone().add(0.5, 1.0, 0.5), 8, 0.25, 0.18, 0.25, 0.01);
            return false;
        }
        final long energy = this.effectiveEnergyCost(machine, Math.max(1L, drop.energyCost() + this.quarryEnergyOffset(machine.machineId())));
        this.absorbNearbyEnergy(machine, coreLocation, energy);
        if (!machine.consumeEnergy(energy)) {
            return false;
        }
        final BlockData targetData = target.getBlockData();
        target.setType(drop.replacement(), false);
        if (target.getType() != drop.replacement()) {
            this.addEnergyCapped(machine, energy);
            return false;
        }
        machine.consumeQuarryFuel(1);
        this.refreshQuarryDisplays(layout);
        if (!this.storeQuarryOutput(layout, machine, output)) {
            final Block chest = layout.chest();
            final Location dropLocation = chest == null
                ? coreLocation.clone().add(0.5, 1.0, 0.5)
                : chest.getLocation().add(0.5, 0.8, 0.5);
            core.getWorld().dropItemNaturally(dropLocation, output.clone());
        }
        this.progressService.incrementStat(machine.owner(), "quarry_mined", output.getAmount());
        this.progressService.unlockByRequirement(machine.owner(), machine.machineId());
        this.progressService.unlockByRequirement(machine.owner(), "machine:" + machine.machineId());
        core.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0.5, 0.5, 0.5), 14, 0.28, 0.28, 0.28, targetData);
        core.getWorld().spawnParticle(Particle.BLOCK, core.getLocation().add(0.5, 0.45, 0.5), 8, 0.18, 0.18, 0.18, core.getBlockData());
        core.getWorld().playSound(coreLocation, Sound.BLOCK_STONE_BREAK, 0.45f, 0.85f);
        core.getWorld().playSound(coreLocation, Sound.ENTITY_ITEM_BREAK, 0.35f, 0.9f);
        core.getWorld().playSound(coreLocation, Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 0.4f, 0.92f);
        return true;
    }

    private boolean canQuarryStartOperation(final PlacedMachine machine, final QuarryRigLayout layout, final Block core) {
        final Block target = this.findNextQuarryTarget(machine, core);
        if (target == null) {
            return false;
        }
        final QuarryDrop drop = this.quarryDropFor(target.getType());
        if (drop == null) {
            return false;
        }
        final ItemStack output = this.buildStackForId(drop.outputId(), drop.amount() + this.countUpgrade(machine, "stack_upgrade") + this.quarryOutputBonus(machine.machineId()));
        return output != null
                && this.canStoreQuarryOutput(layout, machine, output)
            && (machine.quarryFuel() > 0 || this.hasQuarryFuelInChest(layout));
    }

    private Block findNextQuarryTarget(final PlacedMachine machine, final Block core) {
        final World world = core.getWorld();
        final int startY = Math.min(core.getY() - 1, world.getMaxHeight() - 1);
        final var chunk = world.getChunkAt(core.getLocation());
        final int startIndex = machine.quarryCursorZ() * 16 + machine.quarryCursorX();
        for (int offset = 0; offset < 256; offset++) {
            final int index = (startIndex + offset) % 256;
            final int localX = index % 16;
            final int localZ = index / 16;
            final Block target = this.findQuarryTargetInColumn(chunk, localX, localZ, startY, world.getMinHeight());
            if (target != null) {
                machine.setQuarryCursor(localX, localZ);
                return target;
            }
        }
        return null;
    }

    private Block findQuarryTargetInColumn(final org.bukkit.Chunk chunk, final int localX, final int localZ, final int startY, final int minY) {
        for (int y = startY; y >= minY; y--) {
            final Block target = chunk.getBlock(localX, y, localZ);
            if (this.quarryDropFor(target.getType()) != null) {
                return target;
            }
        }
        return null;
    }

    private QuarryRigLayout quarryRigLayout(final Block anchor) {
        if (anchor == null) {
            return null;
        }
        final QuarryRigLayout fromCore = this.quarryRigLayoutFromCore(anchor);
        if (fromCore != null) {
            return fromCore;
        }
        return this.quarryRigLayoutFromMachine(anchor);
    }

    private QuarryRigLayout quarryRigLayoutFromCore(final Block core) {
        if (core == null || core.getType() != Material.BLAST_FURNACE || core.getRelative(BlockFace.UP).getType() != Material.CHEST) {
            return null;
        }
        final QuarryRigLayout eastWest = this.buildQuarryRigLayout(core, BlockFace.EAST, BlockFace.WEST);
        if (eastWest != null) {
            return eastWest;
        }
        return this.buildQuarryRigLayout(core, BlockFace.NORTH, BlockFace.SOUTH);
    }

    private QuarryRigLayout quarryRigLayoutFromMachine(final Block machineBlock) {
        if (this.quarryMachineAt(machineBlock) == null) {
            return null;
        }
        final Block support = machineBlock.getRelative(BlockFace.DOWN);
        if (support.getType() != Material.IRON_BLOCK) {
            return null;
        }
        for (final BlockFace face : Arrays.asList(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            final Block core = support.getRelative(face);
            final QuarryRigLayout layout = this.buildQuarryRigLayout(core, face.getOppositeFace(), face);
            if (layout != null && (this.sameBlock(layout.machineA(), machineBlock) || this.sameBlock(layout.machineB(), machineBlock))) {
                return layout;
            }
        }
        return null;
    }

    private QuarryRigLayout buildQuarryRigLayout(final Block core, final BlockFace faceA, final BlockFace faceB) {
        final Block supportA = core.getRelative(faceA);
        final Block supportB = core.getRelative(faceB);
        final Block machineA = supportA.getRelative(BlockFace.UP);
        final Block machineB = supportB.getRelative(BlockFace.UP);
        if (supportA.getType() != Material.IRON_BLOCK || supportB.getType() != Material.IRON_BLOCK) {
            return null;
        }
        if (!this.isPlacedQuarryMachineBlock(machineA) || !this.isPlacedQuarryMachineBlock(machineB)) {
            return null;
        }
        return new QuarryRigLayout(core, core.getRelative(BlockFace.UP), supportA, supportB, machineA, machineB);
    }

    private boolean ensureQuarryFuel(final QuarryRigLayout layout, final PlacedMachine machine) {
        if (machine.quarryFuel() > 0) {
            return true;
        }
        return this.refillQuarryFuel(layout, machine);
    }

    private boolean refillQuarryFuel(final QuarryRigLayout layout, final PlacedMachine machine) {
        final Inventory inventory = this.quarryChestInventory(layout.chest());
        if (inventory == null) {
            return false;
        }
        final int fuelEnd = Math.min(9, inventory.getSize());
        for (int slot = 0; slot < fuelEnd; slot++) {
            final ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR || !this.isQuarryFuel(stack)) {
                continue;
            }
            final int fuelValue = this.quarryFuelValue(stack);
            if (fuelValue <= 0) {
                continue;
            }
            if (stack.getType() == Material.LAVA_BUCKET) {
                inventory.setItem(slot, new ItemStack(Material.BUCKET));
            } else {
                stack.setAmount(stack.getAmount() - 1);
                inventory.setItem(slot, stack.getAmount() <= 0 ? null : stack);
            }
            machine.setQuarryFuel(fuelValue);
            this.refreshQuarryDisplays(layout);
            return true;
        }
        return false;
    }

    private boolean hasOpenMainView(final LocationKey key) {
        for (final MachineViewSession session : this.openViews.values()) {
            if (session != null && session.mode() == ViewMode.MAIN && key.equals(session.locationKey())) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldPauseWhileEditing(final MachineDefinition definition) {
        return definition != null && (!definition.inputs().isEmpty() || !definition.outputs().isEmpty());
    }

    private boolean isQuarryFuel(final ItemStack stack) {
        final String id = this.resolveStackId(stack);
        return id != null && (id.equalsIgnoreCase("charcoal") || id.equalsIgnoreCase("lava_bucket"));
    }

    private int quarryFuelValue(final ItemStack stack) {
        final String id = this.resolveStackId(stack);
        if (id == null) {
            return 0;
        }
        return switch (id.toLowerCase()) {
            case "charcoal" -> 1;
            case "lava_bucket" -> 8;
            default -> 0;
        };
    }

    private boolean hasQuarryFuelInChest(final QuarryRigLayout layout) {
        return this.quarryFuelCountInChest(layout) > 0;
    }

    private int quarryFuelCountInChest(final QuarryRigLayout layout) {
        final Inventory inventory = this.quarryChestInventory(layout.chest());
        if (inventory == null) {
            return 0;
        }
        int total = 0;
        final int fuelEnd = Math.min(9, inventory.getSize());
        for (int slot = 0; slot < fuelEnd; slot++) {
            final ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            total += this.quarryFuelValue(stack) * stack.getAmount();
        }
        return total;
    }

    private int quarryAvailableFuel(final QuarryRigLayout layout, final PlacedMachine machine) {
        return Math.max(0, machine.quarryFuel()) + this.quarryFuelCountInChest(layout);
    }

    private boolean canStoreQuarryOutput(final QuarryRigLayout layout, final PlacedMachine machine, final ItemStack output) {
        return this.canStoreInQuarryChest(layout.chest(), output);
    }

    private boolean storeQuarryOutput(final QuarryRigLayout layout, final PlacedMachine machine, final ItemStack output) {
        return this.storeInQuarryChest(layout.chest(), output);
    }

    private boolean canStoreInQuarryChest(final Block block, final ItemStack stack) {
        final Inventory inventory = this.quarryChestInventory(block);
        if (inventory == null) {
            return false;
        }
        final int outputStart = this.quarryChestOutputStart(inventory);
        for (int slot = outputStart; slot < inventory.getSize(); slot++) {
            final ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                return true;
            }
            if (!this.isQuarryFuel(current) && current.isSimilar(stack) && current.getAmount() + stack.getAmount() <= current.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private boolean storeInQuarryChest(final Block block, final ItemStack stack) {
        final Inventory inventory = this.quarryChestInventory(block);
        if (inventory == null) {
            return false;
        }
        final int outputStart = this.quarryChestOutputStart(inventory);
        for (int slot = outputStart; slot < inventory.getSize(); slot++) {
            final ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                inventory.setItem(slot, stack.clone());
                return true;
            }
            if (!this.isQuarryFuel(current) && current.isSimilar(stack) && current.getAmount() + stack.getAmount() <= current.getMaxStackSize()) {
                current.setAmount(current.getAmount() + stack.getAmount());
                inventory.setItem(slot, current);
                return true;
            }
        }
        return false;
    }

    private Inventory quarryChestInventory(final Block block) {
        return this.containerInventory(block);
    }

    private Inventory containerInventory(final Block block) {
        if (block == null) {
            return null;
        }
        final var state = block.getState();
        if (state instanceof Chest chest) {
            return chest.getBlockInventory();
        }
        if (state instanceof Container container) {
            return container.getInventory();
        }
        return null;
    }

    private boolean canStoreInInventory(final Inventory inventory, final ItemStack stack) {
        if (inventory == null || stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                return true;
            }
            if (current.isSimilar(stack) && current.getAmount() + stack.getAmount() <= current.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private boolean storeInInventory(final Inventory inventory, final ItemStack stack) {
        if (!this.canStoreInInventory(inventory, stack)) {
            return false;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack current = inventory.getItem(slot);
            if (current == null || current.getType() == Material.AIR) {
                inventory.setItem(slot, stack.clone());
                return true;
            }
            if (current.isSimilar(stack) && current.getAmount() + stack.getAmount() <= current.getMaxStackSize()) {
                current.setAmount(current.getAmount() + stack.getAmount());
                inventory.setItem(slot, current);
                return true;
            }
        }
        return false;
    }

    private int quarryChestOutputStart(final Inventory inventory) {
        return Math.min(9, inventory.getSize());
    }

    private void animateQuarryRig(final Block core, final boolean active) {
        this.animateQuarryRig(core, active, -1);
    }

    private void animateQuarryRig(final Block core, final boolean active, final int stage) {
        final QuarryRigLayout layout = this.quarryRigLayout(core);
        if (layout != null) {
            final boolean extendA = active && stage == 0;
            final boolean extendB = active && stage == 2;
            this.setBlastFurnaceLit(layout.core(), active);
            this.setQuarryPistonState(layout.machineA(), extendA);
            this.setQuarryPistonState(layout.machineB(), extendB);
            return;
        }
        this.setBlastFurnaceLit(core, active);
    }

    private void setQuarryPistonState(final Block block, final boolean extended) {
        final PlacedMachine machine = this.quarryMachineAt(block);
        if (machine == null) {
            return;
        }
        final MachineDefinition definition = this.registry.getMachine(machine.machineId());
        if (definition == null) {
            return;
        }
        final Block head = block.getRelative(BlockFace.UP);
        if (!head.isEmpty() && head.getType() != Material.PISTON_HEAD) {
            return;
        }
        if (block.getType() != Material.PISTON) {
            block.setType(Material.PISTON, false);
        }
        final org.bukkit.block.data.type.Piston piston = (org.bukkit.block.data.type.Piston) block.getBlockData();
        piston.setFacing(BlockFace.UP);
        final boolean changed = piston.isExtended() != extended;
        piston.setExtended(extended);
        block.setBlockData(piston, false);
        if (extended) {
            final org.bukkit.block.data.type.PistonHead pistonHead = (org.bukkit.block.data.type.PistonHead) Material.PISTON_HEAD.createBlockData();
            pistonHead.setFacing(BlockFace.UP);
            pistonHead.setShort(false);
            head.setBlockData(pistonHead, false);
        } else if (head.getType() == Material.PISTON_HEAD) {
            head.setType(Material.AIR, false);
        }
        if (changed) {
            block.getWorld().playSound(block.getLocation(), extended ? Sound.BLOCK_PISTON_EXTEND : Sound.BLOCK_PISTON_CONTRACT, 0.18f, 1.0f);
        }
    }

    private void setBlastFurnaceLit(final Block block, final boolean lit) {
        final BlockData blockData = block.getBlockData();
        if (blockData instanceof Lightable lightable) {
            lightable.setLit(lit);
            block.setBlockData(lightable, false);
        }
    }

    private void removeMachineDisplay(final LocationKey key) {
        final UUID displayId = this.machineDisplays.remove(key);
        if (displayId == null) {
            this.removeMachineVisualDisplay(key);
            return;
        }
        final Entity entity = Bukkit.getEntity(displayId);
        if (entity != null) {
            entity.remove();
        }
        this.removeMachineVisualDisplay(key);
    }

    private void removeMachineVisualDisplay(final LocationKey key) {
        final UUID displayId = this.machineVisualDisplays.remove(key);
        if (displayId == null) {
            return;
        }
        final Entity entity = Bukkit.getEntity(displayId);
        if (entity != null) {
            entity.remove();
        }
    }

    private void updateMachineDisplay(final PlacedMachine machine, final MachineDefinition definition, final Location location) {
        this.updateMachineDisplay(machine, definition, location, false);
    }

    private void updateMachineDisplay(final PlacedMachine machine, final MachineDefinition definition, final Location location, final boolean force) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        if (!force && machine.ticksActive() % 10L != 0L) {
            return;
        }
        if (!this.hasNearbyViewer(world, location, 16.0D)) {
            this.removeMachineDisplay(machine.locationKey());
            return;
        }
        this.removeMachineVisualDisplay(machine.locationKey());
        final TextDisplay display = this.resolveMachineDisplay(machine.locationKey(), location);
        if (display == null) {
            return;
        }
        display.text(this.machineDisplayText(machine, definition, location));
    }

    private void refreshMachineDisplayNow(final LocationKey key) {
        final PlacedMachine machine = this.machines.get(key);
        if (machine == null) {
            return;
        }
        final MachineDefinition definition = this.registry.getMachine(machine.machineId());
        final Block block = this.machineBlock(key);
        if (definition == null || block == null) {
            return;
        }
        this.updateMachineDisplay(machine, definition, block.getLocation(), true);
    }

    private void refreshQuarryDisplays(final QuarryRigLayout layout) {
        if (layout == null) {
            return;
        }
        final LocationKey machineAKey = LocationKey.from(layout.machineA().getLocation());
        final LocationKey machineBKey = LocationKey.from(layout.machineB().getLocation());
        this.refreshMachineDisplayNow(machineAKey);
        this.refreshMachineDisplayNow(machineBKey);
    }

    private void spawnMachineAppearance(final PlacedMachine machine, final MachineDefinition definition, final Location location) {
        if (machine != null && definition != null) {
            this.updateMachineVisual(machine, definition, location);
        }
    }

    private void updateMachineVisual(final PlacedMachine machine, final MachineDefinition definition, final Location location) {
        this.removeMachineVisualEntity(machine.locationKey());
        this.purgeNearbyMachineVisualEntities(location);
        if (this.isQuarryLike(definition.id())) {
            this.setQuarryPistonState(location.getBlock(), false);
        }
    }

    private void removeMachineVisualEntity(final LocationKey key) {
        final UUID displayId = this.machineVisualDisplays.remove(key);
        if (displayId == null) {
            return;
        }
        final Entity entity = Bukkit.getEntity(displayId);
        if (entity != null) {
            entity.remove();
        }
    }

    private boolean hasNearbyViewer(final World world, final Location location, final double range) {
        final double squared = range * range;
        for (final Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= squared) {
                return true;
            }
        }
        return false;
    }

    private boolean isMachineBlock(final Block block, final MachineDefinition definition) {
        final Material type = block.getType();
        if (definition != null && this.isQuarryLike(definition.id())) {
            return type == Material.BLAST_FURNACE
                    || type == Material.PISTON
                    || type == Material.MOVING_PISTON
                    || type == Material.PLAYER_HEAD
                    || type == Material.PLAYER_WALL_HEAD;
        }
        return type == definition.blockMaterial() || type == Material.PLAYER_HEAD || type == Material.PLAYER_WALL_HEAD;
    }

    private TextDisplay resolveMachineDisplay(final LocationKey key, final Location location) {
        final UUID uuid = this.machineDisplays.get(key);
        if (uuid != null) {
            final Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof TextDisplay display && entity.isValid()) {
                this.configureMachineTextDisplay(display);
                return display;
            }
            if (entity != null) {
                entity.remove();
            }
            this.machineDisplays.remove(key);
        }
        final World world = location.getWorld();
        if (world == null) {
            return null;
        }
        final TextDisplay display = world.spawn(location.clone().add(0.5, 1.35, 0.5), TextDisplay.class, spawned -> {
            this.configureMachineTextDisplay(spawned);
        });
        this.machineDisplays.put(key, display.getUniqueId());
        return display;
    }

    private void configureMachineTextDisplay(final TextDisplay display) {
        display.setBillboard(Display.Billboard.CENTER);
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setGravity(false);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setSeeThrough(false);
        display.setShadowed(false);
        display.setTextOpacity((byte) 255);
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setLineWidth(120);
        display.setViewRange(32.0F);
        display.setDisplayWidth(2.5F);
        display.setDisplayHeight(1.2F);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(1);
        display.setTeleportDuration(0);
        display.addScoreboardTag("techproject_display");
    }

    private void purgeNearbyMachineVisualEntities(final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final Location center = location.clone().add(0.5, 0.35, 0.5);
        for (final Entity entity : world.getNearbyEntities(center, 0.9D, 0.9D, 0.9D)) {
            if (!(entity instanceof org.bukkit.entity.ItemDisplay display)) {
                continue;
            }
            if (display.isPersistent() || !display.isInvulnerable() || display.hasGravity()) {
                continue;
            }
            entity.remove();
        }
    }

    private Component machineDisplayText(final PlacedMachine machine, final MachineDefinition definition, final Location location) {
        final PlacedMachine displayMachine;
        if (this.isQuarryLike(machine.machineId())) {
            final Block machineBlock = this.machineBlock(machine.locationKey());
            final QuarryRigLayout displayLayout = machineBlock == null ? null : this.quarryRigLayout(machineBlock);
            final Block primaryBlock = displayLayout == null ? null : this.primaryQuarryMachine(displayLayout);
            final PlacedMachine primaryMachine = primaryBlock == null ? null : this.machines.get(LocationKey.from(primaryBlock.getLocation()));
            displayMachine = primaryMachine != null ? primaryMachine : machine;
        } else {
            displayMachine = machine;
        }
        final Component stateIcon = this.compactStateIcon(displayMachine);
        final Component energyShort = Component.text("⚡" + displayMachine.storedEnergy(), TextColor.color(0xFF, 0xD1, 0x66));
        return stateIcon.append(Component.text(" ", NamedTextColor.GRAY)).append(energyShort);
    }

    private Component compactStateIcon(final PlacedMachine machine) {
        return switch (machine.runtimeState()) {
            case RUNNING -> Component.text("▶", TextColor.color(0x7C, 0xFC, 0x9A));
            case IDLE -> Component.text("◼", TextColor.color(0xA8, 0xB2, 0xC1));
            case STANDBY -> Component.text("⏸", TextColor.color(0xFF, 0xD1, 0x66));
            case NO_POWER -> Component.text("⚠", TextColor.color(0xFF, 0x7B, 0x7B));
            case NO_INPUT, OUTPUT_BLOCKED, NO_FUEL -> Component.text("⚠", TextColor.color(0xFF, 0xD1, 0x66));
            case STRUCTURE_INCOMPLETE, LINK_MISSING -> Component.text("✖", TextColor.color(0xFF, 0x7B, 0x7B));
        };
    }



    private MachineRuntimeState parseRuntimeState(final String raw) {
        if (raw == null || raw.isBlank()) {
            return MachineRuntimeState.IDLE;
        }
        try {
            return MachineRuntimeState.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return MachineRuntimeState.IDLE;
        }
    }

    private void setRuntimeState(final PlacedMachine machine,
                                 final MachineRuntimeState state,
                                 final String detail) {
        if (machine == null) {
            return;
        }
        machine.setRuntimeState(state, detail);
    }

    private Block peekNextQuarryTarget(final PlacedMachine machine, final Block core) {
        final World world = core.getWorld();
        final int startY = Math.min(core.getY() - 1, world.getMaxHeight() - 1);
        final var chunk = world.getChunkAt(core.getLocation());
        final int startIndex = machine.quarryCursorZ() * 16 + machine.quarryCursorX();
        for (int offset = 0; offset < 256; offset++) {
            final int index = (startIndex + offset) % 256;
            final int localX = index % 16;
            final int localZ = index / 16;
            final Block target = this.findQuarryTargetInColumn(chunk, localX, localZ, startY, world.getMinHeight());
            if (target != null) {
                return target;
            }
        }
        return null;
    }

    private int quarryOutputBonus(final String machineId) {
        return switch (this.normalizeId(machineId)) {
            case "quarry_drill_mk2" -> 1;
            case "quarry_drill_mk3" -> 2;
            default -> 0;
        };
    }

    private int quarryOperationsPerTick(final String machineId) {
        return switch (this.normalizeId(machineId)) {
            case "quarry_drill_mk2" -> 2;
            case "quarry_drill_mk3" -> 4;
            default -> 1;
        };
    }

    private long quarryEnergyOffset(final String machineId) {
        return switch (this.normalizeId(machineId)) {
            case "quarry_drill_mk2" -> 16L;
            case "quarry_drill_mk3" -> 32L;
            default -> 0L;
        };
    }

    private boolean isQuarryLike(final String machineId) {
        final String normalized = this.normalizeId(machineId);
        return normalized.equals("quarry_drill") || normalized.equals("quarry_drill_mk2") || normalized.equals("quarry_drill_mk3");
    }

    private Block machineBlock(final LocationKey key) {
        final World world = Bukkit.getWorld(key.worldName());
        return world == null ? null : world.getBlockAt(key.x(), key.y(), key.z());
    }

    private Location locationFromKey(final LocationKey key) {
        final World world = Bukkit.getWorld(key.worldName());
        return world == null ? new Location(Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0), key.x(), key.y(), key.z()) : new Location(world, key.x(), key.y(), key.z());
    }

    private ItemStack structureStatus(final Block core) {
        final Block candidateCore = this.quarryCoreCandidate(core);
        if (candidateCore == null) {
            return this.info(Material.BARRIER, "結構狀態", List.of("世界未載入"));
        }
        final List<String> missing = new ArrayList<>();
        if (candidateCore.getType() != Material.BLAST_FURNACE) {
            missing.add("中央高爐核心");
        }
        if (candidateCore.getRelative(BlockFace.UP).getType() != Material.CHEST) {
            missing.add("上方箱子");
        }
        final BlockFace[] supportFaces = this.quarrySupportFaces(candidateCore);
        if (supportFaces == null) {
            missing.add("同一軸的左右鐵方塊");
        } else {
            final Block machineA = candidateCore.getRelative(supportFaces[0]).getRelative(BlockFace.UP);
            final Block machineB = candidateCore.getRelative(supportFaces[1]).getRelative(BlockFace.UP);
            if (this.quarryMachineAt(machineA) == null || this.quarryMachineAt(machineB) == null) {
                missing.add("鐵方塊上方雙採礦機");
            }
        }
        if (missing.isEmpty()) {
            return this.info(Material.LIME_DYE, "結構狀態", List.of("完整", "右鍵中央高爐可切換啟動 / 停止", "蹲下 + 右鍵中央高爐可打開設定介面"));
        }
        final List<String> lines = new ArrayList<>();
        lines.add("缺少：");
        lines.addAll(missing);
        lines.add("核心偵測：" + this.describeDetectedBlock(candidateCore));
        lines.add("上方偵測：" + this.describeDetectedBlock(candidateCore.getRelative(BlockFace.UP)));
        if (supportFaces != null) {
            lines.add("左側上方：" + this.describeDetectedBlock(candidateCore.getRelative(supportFaces[0]).getRelative(BlockFace.UP)));
            lines.add("右側上方：" + this.describeDetectedBlock(candidateCore.getRelative(supportFaces[1]).getRelative(BlockFace.UP)));
        }
        return this.info(Material.RED_DYE, "結構狀態", lines);
    }

    private String quarryTierLabel(final String machineId) {
        return switch (this.normalizeId(machineId)) {
            case "quarry_drill_mk2" -> "採礦鑽機 Mk2";
            case "quarry_drill_mk3" -> "採礦鑽機 Mk3";
            default -> "採礦鑽機 Mk1";
        };
    }

    private void tickStorageHub(final PlacedMachine machine, final Location location) {
        final int moved = this.moveInputToOutput(machine, 0, null, false, 9);
        if (moved > 0) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "匯流中 x" + moved);
        } else if (this.hasTransferableInput(machine, null, false)) {
            this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "輸出緩衝已滿");
        } else {
            this.setRuntimeState(machine, MachineRuntimeState.NO_INPUT, "等待上游物流");
        }
        final World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.WAX_OFF, location.clone().add(0.5, 0.8, 0.5), 3, 0.2, 0.2, 0.2, 0.01);
        }
    }

    private void tickFilterRouter(final PlacedMachine machine, final Location location) {
        final boolean blacklist = machine.filterMode().equalsIgnoreCase("BLACKLIST");
        final java.util.Set<String> filterIds = new java.util.LinkedHashSet<>();
        for (int s = 0; s < Math.min(3, INPUT_SLOTS.length); s++) {
            final String fid = this.resolveStackId(machine.inputAt(s));
            if (fid != null) {
                filterIds.add(fid);
            } else {
                break;
            }
        }
        if (filterIds.isEmpty()) {
            this.setRuntimeState(machine, MachineRuntimeState.NO_INPUT, "前 1~3 格放樣本做過濾");
            return;
        }
        final int dataStart = filterIds.size();
        int moved = 0;
        for (int slot = dataStart; slot < INPUT_SLOTS.length && moved < 6; slot++) {
            final ItemStack current = machine.inputAt(slot);
            final String id = this.resolveStackId(current);
            if (current == null || id == null) {
                continue;
            }
            final boolean matches = filterIds.contains(id);
            if (blacklist ? matches : !matches) {
                continue;
            }
            final ItemStack oneItem = current.clone();
            oneItem.setAmount(1);
            if (!this.canStoreOutput(machine, oneItem)) {
                break;
            }
            this.storeOutput(machine, oneItem);
            current.setAmount(current.getAmount() - 1);
            if (current.getAmount() <= 0) {
                machine.setInputAt(slot, null);
            } else {
                machine.setInputAt(slot, current);
            }
            moved++;
        }
        final String modeLabel = blacklist ? "黑名單" : "白名單";
        if (moved > 0) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, modeLabel + "過濾 x" + moved);
        } else {
            this.setRuntimeState(machine, MachineRuntimeState.NO_INPUT, modeLabel + " 無匹配材料");
        }
        final World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.END_ROD, location.clone().add(0.5, 0.8, 0.5), 4, 0.18, 0.18, 0.18, 0.01);
        }
    }

    private void tickSplitterNode(final PlacedMachine machine, final Location location) {
        final int moved = this.moveInputToOutput(machine, 0, null, false, 3);
        if (moved > 0) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "分流中 x" + moved);
        } else if (this.hasTransferableInput(machine, null, false)) {
            this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "分流出口已滿");
        } else {
            this.setRuntimeState(machine, MachineRuntimeState.NO_INPUT, "等待可分流材料");
        }
        final World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.CRIT, location.clone().add(0.5, 0.8, 0.5), 4, 0.18, 0.18, 0.18, 0.01);
        }
    }

    private void tickIndustrialBus(final PlacedMachine machine, final Location location) {
        final int moved = this.moveInputToOutput(machine, 0, null, false, 16);
        if (moved > 0) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "總線傳輸 x" + moved);
        } else if (this.hasTransferableInput(machine, null, false)) {
            this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "總線末端已滿");
        } else {
            this.setRuntimeState(machine, MachineRuntimeState.NO_INPUT, "總線待料");
        }
        final World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, location.clone().add(0.5, 0.9, 0.5), 8, 0.25, 0.25, 0.25, 0.01);
        }
    }

    private void tickCargoInputNode(final PlacedMachine machine, final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "待命");
            return;
        }
        int pulled = 0;
        final int[][] offsets = {{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}, {0, 1, 0}, {0, -1, 0}};
        final String dirFilter = machine.inputDirection();
        for (final int[] offset : offsets) {
            if (pulled >= 4) {
                break;
            }
            if (!dirFilter.equalsIgnoreCase("ALL") && !this.matchesDirection(dirFilter, this.directionForOffset(offset))) {
                continue;
            }
            final Block neighbor = world.getBlockAt(location.clone().add(offset[0], offset[1], offset[2]));
            final LocationKey neighborKey = LocationKey.from(neighbor.getLocation());
            if (this.machines.containsKey(neighborKey)) {
                continue;
            }
            final Inventory inventory = this.containerInventory(neighbor);
            if (inventory == null) {
                continue;
            }
            for (int slot = 0; slot < inventory.getSize() && pulled < 4; slot++) {
                final ItemStack item = inventory.getItem(slot);
                if (item == null || item.getType() == Material.AIR) {
                    continue;
                }
                final ItemStack oneItem = item.clone();
                oneItem.setAmount(1);
                if (!this.canStoreOutput(machine, oneItem)) {
                    break;
                }
                this.storeOutput(machine, oneItem);
                item.setAmount(item.getAmount() - 1);
                if (item.getAmount() <= 0) {
                    inventory.setItem(slot, null);
                }
                pulled++;
            }
        }
        if (pulled > 0) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "拉取 x" + pulled);
            world.spawnParticle(Particle.ENCHANT, location.clone().add(0.5, 0.8, 0.5), 6, 0.25, 0.25, 0.25, 0.02);
        } else {
            this.setRuntimeState(machine, MachineRuntimeState.NO_INPUT, "周圍無可拉取容器");
        }
    }

    private void tickCargoManager(final PlacedMachine machine, final Location location) {
        final int moved = this.moveInputToOutput(machine, 0, null, false, 16);
        final World world = location.getWorld();
        if (world == null) {
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "待命");
            return;
        }
        int pulled = 0;
        final int scanRadius = 16 + this.countUpgrade(machine, "range_upgrade") * 8;
        for (int dx = -scanRadius; dx <= scanRadius && pulled < 8; dx++) {
            for (int dy = -4; dy <= 4 && pulled < 8; dy++) {
                for (int dz = -scanRadius; dz <= scanRadius && pulled < 8; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    final Block candidate = world.getBlockAt(location.getBlockX() + dx, location.getBlockY() + dy, location.getBlockZ() + dz);
                    final LocationKey candidateKey = LocationKey.from(candidate.getLocation());
                    final PlacedMachine upstream = this.machines.get(candidateKey);
                    if (upstream == null || upstream == machine || !upstream.owner().equals(machine.owner())) {
                        continue;
                    }
                    for (int slot = 0; slot < OUTPUT_SLOTS.length && pulled < 8; slot++) {
                        final ItemStack outStack = upstream.outputAt(slot);
                        if (outStack == null || outStack.getType() == Material.AIR) {
                            continue;
                        }
                        final ItemStack oneItem = outStack.clone();
                        oneItem.setAmount(1);
                        if (!this.canStoreOutput(machine, oneItem)) {
                            break;
                        }
                        this.storeOutput(machine, oneItem);
                        outStack.setAmount(outStack.getAmount() - 1);
                        if (outStack.getAmount() <= 0) {
                            upstream.setOutputAt(slot, null);
                        } else {
                            upstream.setOutputAt(slot, outStack);
                        }
                        pulled++;
                    }
                }
            }
        }
        final int total = moved + pulled;
        if (total > 0) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "無線物流 x" + total + " (半徑 " + scanRadius + ")");
        } else {
            this.setRuntimeState(machine, MachineRuntimeState.NO_INPUT, "無線範圍無可調度物品");
        }
        if (world != null) {
            world.spawnParticle(Particle.PORTAL, location.clone().add(0.5, 1.2, 0.5), 12, 0.4, 0.4, 0.4, 0.02);
        }
    }

    private void tickTrashNode(final PlacedMachine machine, final Location location) {
        int destroyed = 0;
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            final ItemStack stack = machine.inputAt(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            destroyed += stack.getAmount();
            machine.setInputAt(slot, null);
        }
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            final ItemStack stack = machine.outputAt(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            destroyed += stack.getAmount();
            machine.setOutputAt(slot, null);
        }
        if (destroyed > 0) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "銷毀 x" + destroyed);
        } else {
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待輸入物品");
        }
        final World world = location.getWorld();
        if (world != null && destroyed > 0) {
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 0.9, 0.5), 10, 0.2, 0.3, 0.2, 0.03);
            if (machine.ticksActive() % 3L == 0L) {
                world.playSound(location, Sound.BLOCK_LAVA_EXTINGUISH, 0.15f, 1.6f);
            }
        }
    }

    private void tickCargoMotor(final PlacedMachine machine, final Location location) {
        final int moved = this.moveInputToOutput(machine, 0, null, false, 8);
        if (moved > 0) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "加速中繼 x" + moved);
        } else if (this.hasTransferableInput(machine, null, false)) {
            this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "下游已滿");
        } else {
            this.setRuntimeState(machine, MachineRuntimeState.NO_INPUT, "等待上游物料");
        }
        final World world = location.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, location.clone().add(0.5, 0.8, 0.5), 4, 0.15, 0.15, 0.15, 0.01);
        }
    }

    private void tickManualWireMill(final PlacedMachine machine,
                                    final MachineDefinition definition,
                                    final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final WireMillRigLayout layout = this.wireMillRigLayout(world.getBlockAt(location));
        if (layout == null) {
            if (machine.hasManualOperation()) {
                machine.clearManualOperation();
            }
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方鐵柵欄");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.12, 0.16, 0.0);
            return;
        }
        if (!machine.hasManualOperation()) {
            final RecipeRuntimeSnapshot snapshot = this.inspectManualWireMillRuntime(machine);
            this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
            if (machine.ticksActive() % 4L == 0L) {
                world.spawnParticle(Particle.ASH, layout.ironBars().getLocation().clone().add(0.5, 0.45, 0.5), 2, 0.1, 0.05, 0.1, 0.0);
            }
            return;
        }
        final MachineRecipe recipe = this.findMachineRecipeById(machine.machineId(), machine.manualOperationRecipeId());
        if (recipe == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待手動啟動");
            return;
        }
        final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
        if (output == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
            return;
        }
        if (machine.manualOperationTicks() > 0) {
            machine.tickManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "牽引中 " + Math.max(1, machine.manualOperationTicks()) + " 秒");
            world.spawnParticle(Particle.CRIT, layout.ironBars().getLocation().clone().add(0.5, 0.45, 0.5), 8, 0.12, 0.2, 0.12, 0.01);
            if (machine.ticksActive() % 2L == 0L) {
                world.playSound(layout.ironBars().getLocation(), Sound.BLOCK_CHAIN_HIT, 0.28f, 1.05f);
            }
            if (machine.manualOperationTicks() > 0) {
                return;
            }
        }
        if (!this.canStoreOutput(machine, output)) {
            this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.16, 0.16, 0.0);
            return;
        }
        this.finishManualMachineRecipe(machine, definition, recipe, output);
        this.setRuntimeState(machine, MachineRuntimeState.IDLE, "完成，右鍵鐵柵欄再啟動");
        world.spawnParticle(Particle.ELECTRIC_SPARK, location.clone().add(0.5, 1.0, 0.5), 10, 0.2, 0.15, 0.2, 0.01);
        world.playSound(location, Sound.BLOCK_ANVIL_USE, 0.3f, 1.3f);
    }

    private void tickManualPurifier(final PlacedMachine machine,
                                    final MachineDefinition definition,
                                    final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final PurifierRigLayout layout = this.purifierRigLayout(world.getBlockAt(location));
        if (layout == null) {
            if (machine.hasManualOperation()) {
                machine.clearManualOperation();
            }
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方石按鈕");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.12, 0.16, 0.0);
            return;
        }
        if (!machine.hasManualOperation()) {
            final RecipeRuntimeSnapshot snapshot = this.inspectManualPurifierRuntime(machine);
            this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
            if (machine.ticksActive() % 4L == 0L) {
                world.spawnParticle(Particle.BUBBLE, layout.button().getLocation().clone().add(0.5, 0.35, 0.5), 2, 0.1, 0.08, 0.1, 0.02);
            }
            return;
        }
        final MachineRecipe recipe = this.findMachineRecipeById(machine.machineId(), machine.manualOperationRecipeId());
        if (recipe == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待手動啟動");
            return;
        }
        final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
        if (output == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
            return;
        }
        if (machine.manualOperationTicks() > 0) {
            machine.tickManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "淨化中 " + Math.max(1, machine.manualOperationTicks()) + " 秒");
            world.spawnParticle(Particle.BUBBLE, location.clone().add(0.5, 0.9, 0.5), 8, 0.18, 0.18, 0.18, 0.03);
            if (machine.ticksActive() % 2L == 0L) {
                world.playSound(layout.button().getLocation(), Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.22f, 1.2f);
            }
            if (machine.manualOperationTicks() > 0) {
                return;
            }
        }
        if (!this.canStoreOutput(machine, output)) {
            this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.16, 0.16, 0.0);
            return;
        }
        this.finishManualMachineRecipe(machine, definition, recipe, output);
        this.setRuntimeState(machine, MachineRuntimeState.IDLE, "完成，右鍵石按鈕再啟動");
        world.spawnParticle(Particle.WAX_ON, location.clone().add(0.5, 1.0, 0.5), 10, 0.2, 0.15, 0.2, 0.01);
        world.playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.25f, 1.35f);
    }

    private void tickManualCentrifuge(final PlacedMachine machine,
                                      final MachineDefinition definition,
                                      final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final CentrifugeRigLayout layout = this.centrifugeRigLayout(world.getBlockAt(location));
        if (layout == null) {
            if (machine.hasManualOperation()) {
                machine.clearManualOperation();
            }
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方輕型壓力板");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.12, 0.16, 0.0);
            return;
        }
        if (!machine.hasManualOperation()) {
            final RecipeRuntimeSnapshot snapshot = this.inspectManualCentrifugeRuntime(machine);
            this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
            if (machine.ticksActive() % 4L == 0L) {
                world.spawnParticle(Particle.CLOUD, layout.plate().getLocation().clone().add(0.5, 0.15, 0.5), 2, 0.1, 0.03, 0.1, 0.01);
            }
            return;
        }
        final MachineRecipe recipe = this.findMachineRecipeById(machine.machineId(), machine.manualOperationRecipeId());
        if (recipe == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待手動啟動");
            return;
        }
        final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
        if (output == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
            return;
        }
        if (machine.manualOperationTicks() > 0) {
            machine.tickManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "離心中 " + Math.max(1, machine.manualOperationTicks()) + " 秒");
            world.spawnParticle(Particle.CLOUD, location.clone().add(0.5, 0.95, 0.5), 10, 0.22, 0.06, 0.22, 0.02);
            if (machine.ticksActive() % 2L == 0L) {
                world.playSound(layout.plate().getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.16f, 1.45f);
            }
            if (machine.manualOperationTicks() > 0) {
                return;
            }
        }
        if (!this.canStoreOutput(machine, output)) {
            this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.16, 0.16, 0.0);
            return;
        }
        this.finishManualMachineRecipe(machine, definition, recipe, output);
        this.setRuntimeState(machine, MachineRuntimeState.IDLE, "完成，右鍵壓力板再啟動");
        world.spawnParticle(Particle.FIREWORK, location.clone().add(0.5, 1.0, 0.5), 10, 0.2, 0.15, 0.2, 0.01);
        world.playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.25f, 1.4f);
    }

    private void tickManualBioLab(final PlacedMachine machine,
                                  final MachineDefinition definition,
                                  final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final BioLabRigLayout layout = this.bioLabRigLayout(world.getBlockAt(location));
        if (layout == null) {
            if (machine.hasManualOperation()) {
                machine.clearManualOperation();
            }
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方絆線鉤");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.12, 0.16, 0.0);
            return;
        }
        if (!machine.hasManualOperation()) {
            final RecipeRuntimeSnapshot snapshot = this.inspectManualBioLabRuntime(machine);
            this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
            if (machine.ticksActive() % 4L == 0L) {
                world.spawnParticle(Particle.WAX_ON, layout.hook().getLocation().clone().add(0.5, 0.12, 0.5), 3, 0.1, 0.05, 0.1, 0.0);
            }
            return;
        }
        final MachineRecipe recipe = this.findMachineRecipeById(machine.machineId(), machine.manualOperationRecipeId());
        if (recipe == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待手動啟動");
            return;
        }
        final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
        if (output == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
            return;
        }
        if (machine.manualOperationTicks() > 0) {
            machine.tickManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "培養中 " + Math.max(1, machine.manualOperationTicks()) + " 秒");
            world.spawnParticle(Particle.WAX_ON, location.clone().add(0.5, 0.95, 0.5), 8, 0.2, 0.06, 0.2, 0.01);
            if (machine.ticksActive() % 3L == 0L) {
                world.playSound(layout.hook().getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.22f, 1.15f);
            }
            if (machine.manualOperationTicks() > 0) {
                return;
            }
        }
        if (!this.canStoreOutput(machine, output)) {
            this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.16, 0.16, 0.0);
            return;
        }
        this.finishManualMachineRecipe(machine, definition, recipe, output);
        this.setRuntimeState(machine, MachineRuntimeState.IDLE, "完成，右鍵絆線鉤再啟動");
        world.spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0.5, 1.0, 0.5), 10, 0.2, 0.15, 0.2, 0.01);
        world.playSound(location, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.3f, 1.2f);
    }

    private void tickManualChemReactor(final PlacedMachine machine,
                                       final MachineDefinition definition,
                                       final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final ChemReactorRigLayout layout = this.chemReactorRigLayout(world.getBlockAt(location));
        if (layout == null) {
            if (machine.hasManualOperation()) {
                machine.clearManualOperation();
            }
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方比較器");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.12, 0.16, 0.0);
            return;
        }
        if (!machine.hasManualOperation()) {
            final RecipeRuntimeSnapshot snapshot = this.inspectManualChemReactorRuntime(machine);
            this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
            if (machine.ticksActive() % 4L == 0L) {
                world.spawnParticle(Particle.WITCH, layout.comparator().getLocation().clone().add(0.5, 0.1, 0.5), 3, 0.12, 0.05, 0.12, 0.0);
            }
            return;
        }
        final MachineRecipe recipe = this.findMachineRecipeById(machine.machineId(), machine.manualOperationRecipeId());
        if (recipe == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待手動啟動");
            return;
        }
        final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
        if (output == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
            return;
        }
        if (machine.manualOperationTicks() > 0) {
            machine.tickManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "反應中 " + Math.max(1, machine.manualOperationTicks()) + " 秒");
            world.spawnParticle(Particle.WITCH, location.clone().add(0.5, 0.95, 0.5), 10, 0.22, 0.08, 0.22, 0.015);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, location.clone().add(0.5, 1.1, 0.5), 1, 0.08, 0.0, 0.08, 0.002);
            if (machine.ticksActive() % 3L == 0L) {
                world.playSound(layout.comparator().getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.2f, 0.7f);
            }
            if (machine.manualOperationTicks() > 0) {
                return;
            }
        }
        if (!this.canStoreOutput(machine, output)) {
            this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.16, 0.16, 0.0);
            return;
        }
        this.finishManualMachineRecipe(machine, definition, recipe, output);
        this.setRuntimeState(machine, MachineRuntimeState.IDLE, "完成，右鍵比較器再啟動");
        world.spawnParticle(Particle.ELECTRIC_SPARK, location.clone().add(0.5, 1.0, 0.5), 12, 0.2, 0.15, 0.2, 0.015);
        world.playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 0.3f, 1.3f);
    }

    private void tickProcessor(final PlacedMachine machine,
                               final MachineDefinition definition,
                               final Location location,
                               final Particle particle,
                               final String statKey) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }

        final long requiredEnergy = Math.max(1L, definition.energyPerTick());
        this.absorbNearbyEnergy(machine, location, requiredEnergy);
        if (this.processMachineRecipes(machine, location, statKey, particle, Sound.BLOCK_AMETHYST_BLOCK_STEP)) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "加工中");
            if (machine.ticksActive() % 8L == 0L) {
                world.playSound(location, Sound.BLOCK_AMETHYST_BLOCK_STEP, 0.25f, 1.3f);
            }
            return;
        }
        final RecipeRuntimeSnapshot snapshot = this.inspectRecipeRuntime(machine);
        this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
    }

    private void tickManualCrusher(final PlacedMachine machine,
                                   final MachineDefinition definition,
                                   final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final CrusherRigLayout layout = this.crusherRigLayout(world.getBlockAt(location));
        if (layout == null) {
            if (machine.hasManualOperation()) {
                machine.clearManualOperation();
            }
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方鐵柵欄");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.05, 0.5), 4, 0.16, 0.12, 0.16, 0.0);
            return;
        }

        if (!machine.hasManualOperation()) {
            final RecipeRuntimeSnapshot snapshot = this.inspectManualCrusherRuntime(machine);
            this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
            if (machine.ticksActive() % 3L == 0L) {
                world.spawnParticle(Particle.ASH, layout.grate().getLocation().clone().add(0.5, 0.45, 0.5), 2, 0.1, 0.05, 0.1, 0.0);
            }
            return;
        }

        final MachineRecipe recipe = this.findMachineRecipeById(machine.machineId(), machine.manualOperationRecipeId());
        if (recipe == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待手動啟動");
            return;
        }

        final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
        if (output == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
            return;
        }

        if (machine.manualOperationTicks() > 0) {
            machine.tickManualOperation();
            final int remaining = machine.manualOperationTicks();
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "人工作業中 " + Math.max(1, remaining) + " 秒");
            world.spawnParticle(Particle.CRIT, layout.grate().getLocation().clone().add(0.5, 0.4, 0.5), 8, 0.18, 0.08, 0.18, 0.01);
            world.spawnParticle(Particle.BLOCK, location.clone().add(0.5, 1.0, 0.5), 6, 0.22, 0.12, 0.22, Material.IRON_BARS.createBlockData());
            if (machine.ticksActive() % 2L == 0L) {
                world.playSound(layout.grate().getLocation(), Sound.BLOCK_CHAIN_HIT, 0.28f, 0.75f);
            }
            if (machine.manualOperationTicks() > 0) {
                return;
            }
        }

        if (!this.canStoreOutput(machine, output)) {
            this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.16, 0.16, 0.0);
            return;
        }

        this.finishManualMachineRecipe(machine, definition, recipe, output);
        this.setRuntimeState(machine, MachineRuntimeState.IDLE, "完成，右鍵鐵柵欄再啟動");
        world.spawnParticle(Particle.ELECTRIC_SPARK, location.clone().add(0.5, 1.0, 0.5), 10, 0.2, 0.15, 0.2, 0.01);
        world.playSound(location, Sound.BLOCK_ANVIL_USE, 0.3f, 1.15f);
    }

    private void tickManualCompressor(final PlacedMachine machine,
                                      final MachineDefinition definition,
                                      final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final CompressorRigLayout layout = this.compressorRigLayout(world.getBlockAt(location));
        if (layout == null) {
            if (machine.hasManualOperation()) {
                machine.clearManualOperation();
            }
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方重型壓力板");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.12, 0.16, 0.0);
            return;
        }
        if (!machine.hasManualOperation()) {
            final RecipeRuntimeSnapshot snapshot = this.inspectManualCompressorRuntime(machine);
            this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
            if (machine.ticksActive() % 4L == 0L) {
                world.spawnParticle(Particle.ASH, layout.pressPlate().getLocation().clone().add(0.5, 0.1, 0.5), 3, 0.1, 0.02, 0.1, 0.0);
            }
            return;
        }
        final MachineRecipe recipe = this.findMachineRecipeById(machine.machineId(), machine.manualOperationRecipeId());
        if (recipe == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待手動啟動");
            return;
        }
        final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
        if (output == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
            return;
        }
        if (machine.manualOperationTicks() > 0) {
            machine.tickManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "壓製中 " + Math.max(1, machine.manualOperationTicks()) + " 秒");
            world.spawnParticle(Particle.BLOCK, layout.pressPlate().getLocation().clone().add(0.5, 0.12, 0.5), 8, 0.18, 0.02, 0.18, Material.HEAVY_WEIGHTED_PRESSURE_PLATE.createBlockData());
            if (machine.ticksActive() % 2L == 0L) {
                world.playSound(layout.pressPlate().getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.2f, 0.8f);
            }
            if (machine.manualOperationTicks() > 0) {
                return;
            }
        }
        if (!this.canStoreOutput(machine, output)) {
            this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.16, 0.16, 0.0);
            return;
        }
        this.finishManualMachineRecipe(machine, definition, recipe, output);
        this.setRuntimeState(machine, MachineRuntimeState.IDLE, "完成，右鍵壓力板再啟動");
        world.spawnParticle(Particle.CRIT, location.clone().add(0.5, 1.0, 0.5), 10, 0.2, 0.15, 0.2, 0.01);
        world.playSound(location, Sound.BLOCK_ANVIL_USE, 0.3f, 0.9f);
    }

    private void tickManualOreWasher(final PlacedMachine machine,
                                     final MachineDefinition definition,
                                     final Location location) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final OreWasherRigLayout layout = this.oreWasherRigLayout(world.getBlockAt(location));
        if (layout == null) {
            if (machine.hasManualOperation()) {
                machine.clearManualOperation();
            }
            this.setRuntimeState(machine, MachineRuntimeState.STRUCTURE_INCOMPLETE, "缺少上方拉桿");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.12, 0.16, 0.0);
            return;
        }
        if (!machine.hasManualOperation()) {
            final RecipeRuntimeSnapshot snapshot = this.inspectManualOreWasherRuntime(machine);
            this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
            if (machine.ticksActive() % 4L == 0L) {
                world.spawnParticle(Particle.SPLASH, layout.lever().getLocation().clone().add(0.5, 0.2, 0.5), 2, 0.1, 0.08, 0.1, 0.01);
            }
            return;
        }
        final MachineRecipe recipe = this.findMachineRecipeById(machine.machineId(), machine.manualOperationRecipeId());
        if (recipe == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待手動啟動");
            return;
        }
        final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
        if (output == null) {
            machine.clearManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.IDLE, "等待配方初始化");
            return;
        }
        if (machine.manualOperationTicks() > 0) {
            machine.tickManualOperation();
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "洗礦中 " + Math.max(1, machine.manualOperationTicks()) + " 秒");
            world.spawnParticle(Particle.SPLASH, location.clone().add(0.5, 0.9, 0.5), 10, 0.2, 0.12, 0.2, 0.05);
            world.spawnParticle(Particle.BUBBLE, location.clone().add(0.5, 0.8, 0.5), 6, 0.2, 0.1, 0.2, 0.02);
            if (machine.ticksActive() % 2L == 0L) {
                world.playSound(layout.lever().getLocation(), Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_AMBIENT, 0.25f, 1.15f);
            }
            if (machine.manualOperationTicks() > 0) {
                return;
            }
        }
        if (!this.canStoreOutput(machine, output)) {
            this.setRuntimeState(machine, MachineRuntimeState.OUTPUT_BLOCKED, "出料槽滿，等待清空");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 4, 0.16, 0.16, 0.16, 0.0);
            return;
        }
        this.finishManualMachineRecipe(machine, definition, recipe, output);
        this.setRuntimeState(machine, MachineRuntimeState.IDLE, "完成，右鍵拉桿再啟動");
        world.spawnParticle(Particle.SPLASH, location.clone().add(0.5, 0.9, 0.5), 12, 0.2, 0.12, 0.2, 0.05);
        world.playSound(location, Sound.ITEM_BUCKET_EMPTY, 0.25f, 1.1f);
    }

    private void tickConsumer(final PlacedMachine machine, final Location location, final long requiredEnergy) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        final List<MachineRecipe> recipes = this.registry.getRecipesForMachine(machine.machineId());
        this.absorbNearbyEnergy(machine, location, Math.max(1L, requiredEnergy));
        if (this.processMachineRecipes(machine, location, machine.machineId() + "_cycles", Particle.ELECTRIC_SPARK, Sound.BLOCK_REDSTONE_TORCH_BURNOUT)) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "運轉中");
            world.spawnParticle(Particle.ELECTRIC_SPARK, location.clone().add(0.5, 1.0, 0.5), 5, 0.2, 0.2, 0.2, 0.0);
        } else if (!recipes.isEmpty()) {
            final RecipeRuntimeSnapshot snapshot = this.inspectRecipeRuntime(machine);
            this.setRuntimeState(machine, snapshot.state(), snapshot.detail());
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 3, 0.15, 0.15, 0.15, 0.0);
        } else if (machine.consumeEnergy(requiredEnergy)) {
            this.setRuntimeState(machine, MachineRuntimeState.RUNNING, "持續耗能");
            final MachineDefinition definition = this.registry.getMachine(machine.machineId());
            if (definition != null) {
                for (final String output : definition.outputs()) {
                    this.progressService.unlockItem(machine.owner(), output);
                    this.progressService.unlockByRequirement(machine.owner(), "item:" + output);
                }
                this.progressService.unlockByRequirement(machine.owner(), definition.id());
                this.progressService.unlockByRequirement(machine.owner(), "machine:" + definition.id());
            }
            world.spawnParticle(Particle.ELECTRIC_SPARK, location.clone().add(0.5, 1.0, 0.5), 5, 0.2, 0.2, 0.2, 0.0);
        } else {
            this.setRuntimeState(machine, MachineRuntimeState.NO_POWER, "缺少電力");
            world.spawnParticle(Particle.SMOKE, location.clone().add(0.5, 1.0, 0.5), 3, 0.15, 0.15, 0.15, 0.0);
        }
    }

    private RecipeRuntimeSnapshot inspectRecipeRuntime(final PlacedMachine machine) {
        final List<MachineRecipe> recipes = this.effectiveRecipes(machine);
        if (recipes.isEmpty()) {
            return new RecipeRuntimeSnapshot(MachineRuntimeState.IDLE, "等待互動");
        }
        boolean hasAnyInput = false;
        for (final ItemStack stack : machine.inputInventory()) {
            if (stack != null && stack.getType() != Material.AIR) {
                hasAnyInput = true;
                break;
            }
        }
        for (final MachineRecipe recipe : recipes) {
            if (!this.canCraftRecipe(machine, recipe)) {
                continue;
            }
            final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
            if (output == null) {
                return new RecipeRuntimeSnapshot(MachineRuntimeState.IDLE, "等待配方初始化");
            }
            if (!this.canStoreOutput(machine, output)) {
                return new RecipeRuntimeSnapshot(MachineRuntimeState.OUTPUT_BLOCKED, "輸出阻塞");
            }
            final long requiredEnergy = this.effectiveEnergyCost(machine, recipe.energyCost());
            if (machine.storedEnergy() < requiredEnergy) {
                return new RecipeRuntimeSnapshot(MachineRuntimeState.NO_POWER, "缺少電力");
            }
            return new RecipeRuntimeSnapshot(MachineRuntimeState.IDLE, "等待下一輪加工");
        }
        return hasAnyInput
                ? new RecipeRuntimeSnapshot(MachineRuntimeState.NO_INPUT, "材料不符配方")
                : new RecipeRuntimeSnapshot(MachineRuntimeState.NO_INPUT, "缺少材料");
    }

    private RecipeRuntimeSnapshot inspectManualCrusherRuntime(final PlacedMachine machine) {
        final RecipeRuntimeSnapshot snapshot = this.inspectRecipeRuntime(machine);
        if (snapshot.state() == MachineRuntimeState.IDLE) {
            final MachineRecipe recipe = this.firstReadyManualRecipe(machine);
            if (recipe != null) {
                return new RecipeRuntimeSnapshot(MachineRuntimeState.IDLE, "就緒，右鍵鐵柵欄啟動");
            }
        }
        return snapshot;
    }

    private RecipeRuntimeSnapshot inspectManualCompressorRuntime(final PlacedMachine machine) {
        final RecipeRuntimeSnapshot snapshot = this.inspectRecipeRuntime(machine);
        if (snapshot.state() == MachineRuntimeState.IDLE && this.firstReadyManualRecipe(machine) != null) {
            return new RecipeRuntimeSnapshot(MachineRuntimeState.IDLE, "就緒，右鍵壓力板啟動");
        }
        return snapshot;
    }

    private RecipeRuntimeSnapshot inspectManualOreWasherRuntime(final PlacedMachine machine) {
        final RecipeRuntimeSnapshot snapshot = this.inspectRecipeRuntime(machine);
        if (snapshot.state() == MachineRuntimeState.IDLE && this.firstReadyManualRecipe(machine) != null) {
            return new RecipeRuntimeSnapshot(MachineRuntimeState.IDLE, "就緒，右鍵拉桿啟動");
        }
        return snapshot;
    }

    private RecipeRuntimeSnapshot inspectManualWireMillRuntime(final PlacedMachine machine) {
        final RecipeRuntimeSnapshot snapshot = this.inspectRecipeRuntime(machine);
        if (snapshot.state() == MachineRuntimeState.IDLE && this.firstReadyManualRecipe(machine) != null) {
            return new RecipeRuntimeSnapshot(MachineRuntimeState.IDLE, "就緒，右鍵鐵柵欄啟動");
        }
        return snapshot;
    }

    private RecipeRuntimeSnapshot inspectManualPurifierRuntime(final PlacedMachine machine) {
        final RecipeRuntimeSnapshot snapshot = this.inspectRecipeRuntime(machine);
        if (snapshot.state() == MachineRuntimeState.IDLE && this.firstReadyManualRecipe(machine) != null) {
            return new RecipeRuntimeSnapshot(MachineRuntimeState.IDLE, "就緒，右鍵石按鈕啟動");
        }
        return snapshot;
    }

    private RecipeRuntimeSnapshot inspectManualCentrifugeRuntime(final PlacedMachine machine) {
        final RecipeRuntimeSnapshot snapshot = this.inspectRecipeRuntime(machine);
        if (snapshot.state() == MachineRuntimeState.IDLE && this.firstReadyManualRecipe(machine) != null) {
            return new RecipeRuntimeSnapshot(MachineRuntimeState.IDLE, "就緒，右鍵壓力板啟動");
        }
        return snapshot;
    }

    private RecipeRuntimeSnapshot inspectManualBioLabRuntime(final PlacedMachine machine) {
        final RecipeRuntimeSnapshot snapshot = this.inspectRecipeRuntime(machine);
        if (snapshot.state() == MachineRuntimeState.IDLE && this.firstReadyManualRecipe(machine) != null) {
            return new RecipeRuntimeSnapshot(MachineRuntimeState.IDLE, "就緒，右鍵絆線鉤啟動");
        }
        return snapshot;
    }

    private RecipeRuntimeSnapshot inspectManualChemReactorRuntime(final PlacedMachine machine) {
        final RecipeRuntimeSnapshot snapshot = this.inspectRecipeRuntime(machine);
        if (snapshot.state() == MachineRuntimeState.IDLE && this.firstReadyManualRecipe(machine) != null) {
            return new RecipeRuntimeSnapshot(MachineRuntimeState.IDLE, "就緒，右鍵比較器啟動");
        }
        return snapshot;
    }

    private MachineRecipe firstReadyManualRecipe(final PlacedMachine machine) {
        final List<MachineRecipe> recipes = this.effectiveRecipes(machine);
        for (final MachineRecipe recipe : recipes) {
            if (!this.canCraftRecipe(machine, recipe)) {
                continue;
            }
            final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
            if (output == null || !this.canStoreOutput(machine, output)) {
                continue;
            }
            final long requiredEnergy = this.effectiveEnergyCost(machine, recipe.energyCost());
            if (machine.storedEnergy() >= requiredEnergy) {
                return recipe;
            }
        }
        return null;
    }

    private void finishManualMachineRecipe(final PlacedMachine machine,
                                           final MachineDefinition definition,
                                           final MachineRecipe recipe,
                                           final ItemStack output) {
        this.storeOutput(machine, output);
        this.progressService.incrementStat(machine.owner(), definition.id() + "_cycles", output.getAmount());
        this.progressService.unlockItem(machine.owner(), recipe.outputId());
        this.progressService.unlockByRequirement(machine.owner(), machine.machineId());
        this.progressService.unlockByRequirement(machine.owner(), "machine:" + machine.machineId());
        this.progressService.unlockByRequirement(machine.owner(), "item:" + recipe.outputId());
        machine.clearManualOperation();
    }

    /**
     * 傳回該機器「有效」的配方清單：若已鎖定某配方，只回傳該配方；否則回傳全部。
     */
    private List<MachineRecipe> effectiveRecipes(final PlacedMachine machine) {
        final List<MachineRecipe> all = this.registry.getRecipesForMachine(machine.machineId());
        final String locked = machine.lockedRecipeId();
        if (locked == null) {
            return all;
        }
        for (final MachineRecipe recipe : all) {
            if (recipe.id().equals(locked)) {
                return List.of(recipe);
            }
        }
        // 鎖定的配方已被刪除或改名，退回全部
        machine.setLockedRecipeId(null);
        return all;
    }

    private boolean processMachineRecipes(final PlacedMachine machine,
                                          final Location location,
                                          final String statKey,
                                          final Particle particle,
                                          final Sound sound) {
        final List<MachineRecipe> recipes = this.effectiveRecipes(machine);
        if (recipes.isEmpty()) {
            return false;
        }

        boolean processedAny = false;
        final int attempts = 1 + this.countUpgrade(machine, "speed_upgrade");
        for (int attempt = 0; attempt < attempts; attempt++) {
            boolean processedThisAttempt = false;
            for (final MachineRecipe recipe : recipes) {
                if (!this.canCraftRecipe(machine, recipe)) {
                    continue;
                }
                final long requiredEnergy = this.effectiveEnergyCost(machine, recipe.energyCost());
                this.absorbNearbyEnergy(machine, location, requiredEnergy);
                if (!machine.consumeEnergy(requiredEnergy)) {
                    return processedAny;
                }

                final ItemStack output = this.buildStackForId(recipe.outputId(), recipe.outputCount() + this.countUpgrade(machine, "stack_upgrade"));
                if (output == null || !this.canStoreOutput(machine, output)) {
                    this.addEnergyCapped(machine, requiredEnergy);
                    return processedAny;
                }

                this.consumeRecipeInputs(machine, recipe);
                this.storeOutput(machine, output);
                this.progressService.incrementStat(machine.owner(), statKey, output.getAmount());
                this.progressService.unlockItem(machine.owner(), recipe.outputId());
                this.progressService.unlockByRequirement(machine.owner(), machine.machineId());
                this.progressService.unlockByRequirement(machine.owner(), "machine:" + machine.machineId());
                this.progressService.unlockByRequirement(machine.owner(), "item:" + recipe.outputId());
                final World world = location.getWorld();
                if (world != null) {
                    world.spawnParticle(particle, location.clone().add(0.5, 1.0, 0.5), 7, 0.22, 0.22, 0.22, 0.01);
                    world.playSound(location, sound, 0.3f, 1.15f);
                }
                processedAny = true;
                processedThisAttempt = true;
                break;
            }
            if (!processedThisAttempt) {
                break;
            }
        }
        return processedAny;
    }

    private boolean canCraftRecipe(final PlacedMachine machine, final MachineRecipe recipe) {
        final Map<String, Integer> required = new HashMap<>();
        for (final String input : recipe.inputIds()) {
            required.merge(this.normalizeRecipeInputKey(input), 1, Integer::sum);
        }

        final Map<String, Integer> available = new HashMap<>();
        for (final ItemStack stack : machine.inputInventory()) {
            final String id = this.resolveRecipeStackKey(stack);
            if (id == null) {
                continue;
            }
            available.merge(id, stack.getAmount(), Integer::sum);
        }

        for (final Map.Entry<String, Integer> entry : required.entrySet()) {
            if (available.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void consumeRecipeInputs(final PlacedMachine machine, final MachineRecipe recipe) {
        final Map<String, Integer> remaining = new HashMap<>();
        for (final String input : recipe.inputIds()) {
            remaining.merge(this.normalizeRecipeInputKey(input), 1, Integer::sum);
        }

        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            final ItemStack stack = machine.inputAt(slot);
            final String id = this.resolveRecipeStackKey(stack);
            if (stack == null || id == null) {
                continue;
            }
            int needed = remaining.getOrDefault(id, 0);
            if (needed <= 0) {
                continue;
            }
            final int consume = Math.min(needed, stack.getAmount());
            stack.setAmount(stack.getAmount() - consume);
            if (stack.getAmount() <= 0) {
                machine.setInputAt(slot, null);
            } else {
                machine.setInputAt(slot, stack);
            }
            remaining.put(id, needed - consume);
        }
    }

    private boolean canStoreOutput(final PlacedMachine machine, final ItemStack output) {
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            final ItemStack current = machine.outputAt(slot);
            if (current == null || current.getType() == Material.AIR) {
                return true;
            }
            if (current.isSimilar(output) && current.getAmount() + output.getAmount() <= current.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void storeOutput(final PlacedMachine machine, final ItemStack output) {
        for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
            final ItemStack current = machine.outputAt(slot);
            if (current == null || current.getType() == Material.AIR) {
                machine.setOutputAt(slot, output);
                return;
            }
            if (current.isSimilar(output) && current.getAmount() + output.getAmount() <= current.getMaxStackSize()) {
                current.setAmount(current.getAmount() + output.getAmount());
                machine.setOutputAt(slot, current);
                return;
            }
        }
    }

    private boolean canStoreAllOutputs(final PlacedMachine machine, final List<ItemStack> outputs) {
        final ItemStack[] simulated = machine.outputInventory();
        for (final ItemStack output : outputs) {
            if (output == null || output.getType() == Material.AIR) {
                continue;
            }
            boolean stored = false;
            for (int slot = 0; slot < simulated.length; slot++) {
                final ItemStack current = simulated[slot];
                if (current == null || current.getType() == Material.AIR) {
                    simulated[slot] = output.clone();
                    stored = true;
                    break;
                }
                if (current.isSimilar(output) && current.getAmount() + output.getAmount() <= current.getMaxStackSize()) {
                    current.setAmount(current.getAmount() + output.getAmount());
                    simulated[slot] = current;
                    stored = true;
                    break;
                }
            }
            if (!stored) {
                return false;
            }
        }
        return true;
    }

    private void storeOutputs(final PlacedMachine machine, final List<ItemStack> outputs) {
        for (final ItemStack output : outputs) {
            if (output != null && output.getType() != Material.AIR) {
                this.storeOutput(machine, output);
            }
        }
    }

    private int moveInputToOutput(final PlacedMachine machine,
                                  final int startSlot,
                                  final String requiredId,
                                  final boolean preserveTemplate,
                                  final int maxMoves) {
        int moved = 0;
        for (int slot = startSlot; slot < INPUT_SLOTS.length && moved < maxMoves; slot++) {
            final ItemStack current = machine.inputAt(slot);
            final String id = this.resolveStackId(current);
            if (current == null || id == null) {
                continue;
            }
            if (requiredId != null && !requiredId.equalsIgnoreCase(id)) {
                continue;
            }
            final int keepAmount = preserveTemplate && slot == 0 ? 1 : 0;
            if (current.getAmount() <= keepAmount) {
                continue;
            }
            final ItemStack movedStack = current.clone();
            movedStack.setAmount(1);
            if (!this.canStoreOutput(machine, movedStack)) {
                return moved;
            }
            this.storeOutput(machine, movedStack);
            current.setAmount(current.getAmount() - 1);
            if (current.getAmount() <= 0) {
                machine.setInputAt(slot, null);
            } else {
                machine.setInputAt(slot, current);
            }
            moved++;
        }
        return moved;
    }

    private boolean hasTransferableInput(final PlacedMachine machine,
                                         final String requiredId,
                                         final boolean preserveTemplate) {
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            final ItemStack current = machine.inputAt(slot);
            final String id = this.resolveStackId(current);
            if (current == null || id == null) {
                continue;
            }
            if (requiredId != null && !requiredId.equalsIgnoreCase(id)) {
                continue;
            }
            final int keepAmount = preserveTemplate && slot == 0 ? 1 : 0;
            if (current.getAmount() > keepAmount) {
                return true;
            }
        }
        return false;
    }

    private boolean consumeInput(final PlacedMachine machine, final List<String> acceptedIds, final int amount) {
        int available = 0;
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            final ItemStack stack = machine.inputAt(slot);
            if (stack != null && acceptedIds.stream().anyMatch(candidate -> this.stackMatchesRecipeInput(stack, candidate))) {
                available += stack.getAmount();
            }
        }
        if (available < amount) {
            return false;
        }
        int remaining = amount;
        for (int slot = 0; slot < INPUT_SLOTS.length; slot++) {
            final ItemStack stack = machine.inputAt(slot);
            if (stack == null || acceptedIds.stream().noneMatch(candidate -> this.stackMatchesRecipeInput(stack, candidate))) {
                continue;
            }
            final int consume = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - consume);
            remaining -= consume;
            if (stack.getAmount() <= 0) {
                machine.setInputAt(slot, null);
            } else {
                machine.setInputAt(slot, stack);
            }
            if (remaining <= 0) {
                return true;
            }
        }
        return false;
    }

    private List<ItemStack> harvestOutputsFor(final Material material) {
        final List<ItemStack> outputs = new ArrayList<>();
        switch (material) {
            case WHEAT -> {
                outputs.add(this.buildStackForId("crops", 2));
                outputs.add(this.buildStackForId("crop_seeds", 1));
            }
            case CARROTS, POTATOES -> outputs.add(this.buildStackForId("crops", 2));
            case BEETROOTS -> {
                outputs.add(this.buildStackForId("crops", 1));
                outputs.add(this.buildStackForId("crop_seeds", 1));
            }
            case NETHER_WART -> outputs.add(this.buildStackForId("bio_fiber", 1));
            default -> {
                return List.of();
            }
        }
        outputs.removeIf(stack -> stack == null || stack.getType() == Material.AIR);
        return outputs;
    }

    private QuarryDrop quarryDropFor(final Material material) {
        return switch (material) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> new QuarryDrop("raw_iron", Material.AIR, 1, 16L);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> new QuarryDrop("raw_copper", Material.AIR, 2, 16L);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> new QuarryDrop("raw_gold", Material.AIR, 1, 18L);
            case COAL_ORE, DEEPSLATE_COAL_ORE -> new QuarryDrop("coal", Material.AIR, 2, 14L);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> new QuarryDrop("redstone", Material.AIR, 3, 18L);
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> new QuarryDrop("lapis_lazuli", Material.AIR, 3, 18L);
            case DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE -> new QuarryDrop("diamond", Material.AIR, 1, 22L);
            case EMERALD_ORE, DEEPSLATE_EMERALD_ORE -> new QuarryDrop("emerald", Material.AIR, 1, 22L);
            case NETHER_GOLD_ORE -> new QuarryDrop("raw_gold", Material.AIR, 1, 18L);
            case NETHER_QUARTZ_ORE -> new QuarryDrop("quartz", Material.AIR, 2, 16L);
            case ANCIENT_DEBRIS -> new QuarryDrop("ancient_debris", Material.AIR, 1, 48L);
            default -> null;
        };
    }

    private ItemStack buildStackForId(final String id) {
        return this.buildStackForId(id, 1);
    }

    private ItemStack buildStackForId(final String id, final int amount) {
        if (this.registry.getItem(id) != null) {
            final ItemStack stack = this.itemFactory.buildTechItem(this.registry.getItem(id));
            stack.setAmount(Math.min(amount, stack.getMaxStackSize()));
            return stack;
        }
        if (this.registry.getMachine(id) != null) {
            final ItemStack stack = this.itemFactory.buildMachineItem(this.registry.getMachine(id));
            stack.setAmount(Math.min(amount, stack.getMaxStackSize()));
            return stack;
        }
        final Material material = Material.matchMaterial(id.toUpperCase());
        if (material == null || material == Material.AIR) {
            return null;
        }
        final Material displayMaterial = this.itemFactory.safeItemMaterial(material);
        return new ItemStack(displayMaterial, Math.min(amount, displayMaterial.getMaxStackSize()));
    }

    /** 讓指定機器嘗試從鄰近電網拉取電力（供充電用途）。 */
    public void pullNearbyEnergyInto(final PlacedMachine machine, final Location location) {
        this.absorbNearbyEnergy(machine, location, this.remainingEnergyCapacity(machine));
    }

    private void absorbNearbyEnergy(final PlacedMachine machine, final Location location, final long amountPerSource) {
        for (final PlacedMachine neighbor : this.findConnectedMachines(machine, location, machine.inputDirection(), null, false)) {
            final long transfer = Math.min(Math.min(amountPerSource, neighbor.storedEnergy()), this.remainingEnergyCapacity(machine));
            if (transfer <= 0L || !neighbor.consumeEnergy(transfer)) {
                continue;
            }
            this.addEnergyCapped(machine, transfer);
        }
    }

    private void distributeNearbyEnergy(final PlacedMachine machine, final Location location, final long amountPerTarget) {
        for (final PlacedMachine neighbor : this.findConnectedMachines(machine, location, null, machine.outputDirection(), false)) {
            if (machine.storedEnergy() <= 0L) {
                return;
            }
            final long transfer = Math.min(Math.min(amountPerTarget, machine.storedEnergy()), this.remainingEnergyCapacity(neighbor));
            if (transfer <= 0L || !machine.consumeEnergy(transfer)) {
                continue;
            }
            this.addEnergyCapped(neighbor, transfer);
        }
    }

    private long addEnergyCapped(final PlacedMachine machine, final long amount) {
        if (machine == null || amount <= 0L) {
            return 0L;
        }
        final long accepted = Math.min(amount, this.remainingEnergyCapacity(machine));
        if (accepted <= 0L) {
            return 0L;
        }
        machine.addEnergy(accepted);
        return accepted;
    }

    private long remainingEnergyCapacity(final PlacedMachine machine) {
        return Math.max(0L, this.maxEnergyCapacity(machine) - machine.storedEnergy());
    }

    private void clampMachineEnergy(final PlacedMachine machine) {
        if (machine == null) {
            return;
        }
        machine.setStoredEnergy(Math.min(machine.storedEnergy(), this.maxEnergyCapacity(machine)));
    }

    private long maxEnergyCapacity(final PlacedMachine machine) {
        return machine == null ? 0L : this.maxEnergyCapacity(this.registry.getMachine(machine.machineId()));
    }

    private long maxEnergyCapacity(final MachineDefinition definition) {
        if (definition == null) {
            return 0L;
        }
        return switch (this.normalizeId(definition.id())) {
            case "battery_bank" -> 24000L;
            case "solar_array", "storm_turbine", "fusion_reactor" -> 12000L;
            case "energy_node", "energy_cable" -> 6000L;
            case "android_station" -> 7200L;
            case "android_item_interface", "android_fuel_interface" -> 3600L;
            case "quarry_drill", "quarry_drill_mk2", "quarry_drill_mk3" -> 6400L;
            case "solar_generator", "coal_generator" -> 3200L;
            default -> switch (definition.tier()) {
                case TIER1 -> 2400L;
                case TIER2 -> 4800L;
                case TIER3 -> 9600L;
                case TIER4 -> 16000L;
            };
        };
    }

    private List<PlacedMachine> findConnectedMachines(final PlacedMachine machine,
                                                      final Location location,
                                                      final String inputDirection,
                                                      final String outputDirection,
                                                      final boolean logisticsMode) {
        final World world = location.getWorld();
        if (world == null) {
            return List.of();
        }

        final List<PlacedMachine> result = new ArrayList<>();
        List<LocationKey> frontier = new ArrayList<>();
        final java.util.Set<LocationKey> visited = new java.util.HashSet<>();
        frontier.add(machine.locationKey());
        visited.add(machine.locationKey());
        final int maxDepth = this.networkDepth(machine, logisticsMode);
        final int[][] offsets = {
                {1, 0, 0}, {-1, 0, 0},
                {0, 0, 1}, {0, 0, -1},
                {0, 1, 0}, {0, -1, 0}
        };
        for (int depth = 0; depth < maxDepth && !frontier.isEmpty(); depth++) {
            final List<LocationKey> next = new ArrayList<>();
            for (final LocationKey currentKey : frontier) {
                final PlacedMachine currentMachine = this.machines.get(currentKey);
                final Location current = new Location(world, currentKey.x(), currentKey.y(), currentKey.z());
                for (final int[] offset : offsets) {
                    final String direction = this.directionForOffset(offset);
                    if (currentKey.equals(machine.locationKey())) {
                        if (inputDirection != null && !this.matchesDirection(inputDirection, direction)) {
                            continue;
                        }
                        if (outputDirection != null && !this.matchesDirection(outputDirection, direction)) {
                            continue;
                        }
                    } else if (currentMachine != null && this.isRelayMachine(currentMachine.machineId(), logisticsMode)) {
                        if (inputDirection != null && !this.matchesDirection(currentMachine.inputDirection(), direction)) {
                            continue;
                        }
                        if (outputDirection != null && !this.matchesDirection(currentMachine.outputDirection(), direction)) {
                            continue;
                        }
                    }
                    final LocationKey key = LocationKey.from(current.clone().add(offset[0], offset[1], offset[2]));
                    if (!visited.add(key)) {
                        continue;
                    }
                    final PlacedMachine neighbor = this.machines.get(key);
                    if (neighbor == null || neighbor.owner().equals(machine.owner()) == false) {
                        continue;
                    }
                    if (this.isRelayMachine(neighbor.machineId(), logisticsMode)) {
                        if (outputDirection != null && !this.matchesDirection(neighbor.inputDirection(), this.oppositeDirection(direction))) {
                            continue;
                        }
                        if (inputDirection != null && !this.matchesDirection(neighbor.outputDirection(), this.oppositeDirection(direction))) {
                            continue;
                        }
                        next.add(key);
                        continue;
                    }
                    if (outputDirection != null && !this.matchesDirection(neighbor.inputDirection(), this.oppositeDirection(direction))) {
                        continue;
                    }
                    if (inputDirection != null && !this.matchesDirection(neighbor.outputDirection(), this.oppositeDirection(direction))) {
                        continue;
                    }
                    result.add(neighbor);
                }
            }
            frontier = next;
        }
        return result;
    }

    private void transferOutputs(final PlacedMachine machine, final Location location) {
        if (!this.isAutoTransferSource(machine.machineId())) {
            return;
        }
        final List<PlacedMachine> targets = new ArrayList<>(this.findConnectedMachines(machine, location, null, machine.outputDirection(), true));
        if (targets.isEmpty()) {
            return;
        }
        final int maxTransfers = switch (machine.machineId().toLowerCase()) {
            case "industrial_bus", "cargo_manager" -> 16;
            case "cargo_motor" -> 8;
            case "splitter_node" -> 4;
            case "storage_hub", "filter_router" -> 3;
            default -> 1 + this.countUpgrade(machine, "stack_upgrade");
        };
        for (int pass = 0; pass < maxTransfers; pass++) {
            if (machine.machineId().equalsIgnoreCase("splitter_node")) {
                final int rotation = (int) ((machine.ticksActive() + pass) % targets.size());
                Collections.rotate(targets, -rotation);
            }
            if (!this.transferSingleOutput(machine, targets)) {
                return;
            }
        }
    }

    private boolean transferSingleOutput(final PlacedMachine machine, final List<PlacedMachine> targets) {
        for (final PlacedMachine neighbor : targets) {
            if (neighbor == machine) {
                continue;
            }
            for (int slot = 0; slot < OUTPUT_SLOTS.length; slot++) {
                final ItemStack stack = machine.outputAt(slot);
                if (stack == null || stack.getType() == Material.AIR) {
                    continue;
                }
                if (!this.machineAcceptsInput(neighbor, stack)) {
                    continue;
                }
                final ItemStack oneItem = stack.clone();
                oneItem.setAmount(1);
                final ItemStack remainder = this.insertIntoMachineInputs(neighbor, oneItem);
                if (remainder.getType() != Material.AIR && remainder.getAmount() > 0) {
                    continue;
                }
                stack.setAmount(stack.getAmount() - 1);
                if (stack.getAmount() <= 0) {
                    machine.setOutputAt(slot, null);
                } else {
                    machine.setOutputAt(slot, stack);
                }
                this.progressService.incrementStat(machine.owner(), "items_transferred", 1L);
                this.pushOpenViewState(neighbor.locationKey(), neighbor);
                return true;
            }
        }
        return false;
    }

    private void handleMachineAction(final Player player, final LocationKey key, final String action) {
        final PlacedMachine machine = this.machines.get(key);
        if (machine == null) {
            return;
        }
        switch (action) {
            case "dir-input" -> {
                machine.setInputDirection(this.nextDirection(machine.inputDirection()));
                player.sendMessage(this.itemFactory.secondary("輸入方向已切換為：" + this.directionDisplayName(machine.inputDirection())));
                this.openMachineMenuNextTick(player, key);
            }
            case "dir-output" -> {
                machine.setOutputDirection(this.nextDirection(machine.outputDirection()));
                player.sendMessage(this.itemFactory.secondary("輸出方向已切換為：" + this.directionDisplayName(machine.outputDirection())));
                this.openMachineMenuNextTick(player, key);
            }
            case "filter-mode" -> {
                machine.setFilterMode(machine.filterMode().equalsIgnoreCase("WHITELIST") ? "BLACKLIST" : "WHITELIST");
                player.sendMessage(this.itemFactory.secondary("過濾模式已切換為：" + (machine.filterMode().equalsIgnoreCase("BLACKLIST") ? "黑名單" : "白名單")));
                this.openMachineMenuNextTick(player, key);
            }
            case "android-radius" -> {
                machine.setAndroidPatrolRadius(machine.androidPatrolRadius() >= 6 ? 1 : machine.androidPatrolRadius() + 1);
                machine.setAndroidRouteCursor(0);
                player.sendMessage(this.itemFactory.secondary("自動機巡邏半徑已調整為：" + machine.androidPatrolRadius() + " 格"));
                this.openMachineMenuNextTick(player, key);
            }
            case "android-height" -> {
                machine.setAndroidPatrolHeight(machine.androidPatrolHeight() >= 4 ? 0 : machine.androidPatrolHeight() + 1);
                machine.setAndroidRouteCursor(0);
                player.sendMessage(this.itemFactory.secondary("自動機掃描高度已調整為：+" + machine.androidPatrolHeight() + " 格"));
                this.openMachineMenuNextTick(player, key);
            }
            case "android-route" -> {
                machine.setAndroidRouteMode(this.nextAndroidRouteMode(machine.androidRouteMode()));
                machine.setAndroidRouteCursor(0);
                player.sendMessage(this.itemFactory.secondary("自動機巡邏模式已切換為：" + this.androidRouteModeLabel(machine.androidRouteMode())));
                this.openMachineMenuNextTick(player, key);
            }
            case "android-guide" -> this.scheduler.runEntityDelayed(player, () -> this.plugin.getTechBookService().openGuideDetail(player, "android_system_overview", 0), 1L);
            case "lock-recipe" -> {
                final List<MachineRecipe> allRecipes = this.registry.getRecipesForMachine(machine.machineId());
                if (allRecipes.size() <= 1) {
                    break;
                }
                final String current = machine.lockedRecipeId();
                if (current == null) {
                    // 未鎖定 → 鎖定第一個配方
                    machine.setLockedRecipeId(allRecipes.getFirst().id());
                } else {
                    // 找當前鎖定的下一個，超過最後則解鎖
                    int idx = -1;
                    for (int i = 0; i < allRecipes.size(); i++) {
                        if (allRecipes.get(i).id().equals(current)) {
                            idx = i;
                            break;
                        }
                    }
                    if (idx >= 0 && idx + 1 < allRecipes.size()) {
                        machine.setLockedRecipeId(allRecipes.get(idx + 1).id());
                    } else {
                        machine.setLockedRecipeId(null);
                    }
                }
                final String newLock = machine.lockedRecipeId();
                if (newLock == null) {
                    player.sendMessage(this.itemFactory.secondary("配方鎖定已解除，恢復自動匹配。"));
                } else {
                    final MachineRecipe locked = this.findMachineRecipeById(machine.machineId(), newLock);
                    final String name = locked != null ? this.itemFactory.displayNameForId(locked.outputId()) : newLock;
                    player.sendMessage(this.itemFactory.secondary("配方已鎖定為：" + name));
                }
                this.openMachineMenuNextTick(player, key);
            }
            case "back-main" -> this.openMachineMenuNextTick(player, key);
            default -> {
                if (action.startsWith("recipes:")) {
                    this.openRecipeMenuNextTick(player, key, this.parsePage(action.substring("recipes:".length())));
                }
            }
        }
    }

    private void openMachineMenuNextTick(final Player player, final LocationKey key) {
        this.scheduler.runEntityDelayed(player, () -> this.openMachineMenu(player, key), 1L);
    }

    private void openRecipeMenuNextTick(final Player player, final LocationKey key, final int page) {
        this.scheduler.runEntityDelayed(player, () -> this.openRecipeMenu(player, key, page), 1L);
    }

    private void showRecipeDetailNextTick(final Player player, final LocationKey key, final String recipeId, final int backPage) {
        this.scheduler.runEntityDelayed(player, () -> this.showRecipeDetail(player, key, recipeId, backPage), 1L);
    }

    private int parsePage(final String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (final NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean isAllowedWorld(final World world) {
        if (world == null) {
            return false;
        }
        final List<String> allowedWorlds = this.plugin.getConfig().getStringList("machine-safety.allowed-worlds");
        return allowedWorlds.isEmpty() || allowedWorlds.stream().anyMatch(entry -> entry.equalsIgnoreCase(world.getName()));
    }

    private boolean isWorldInteractionMachine(final String machineId) {
        return switch (machineId.toLowerCase()) {
            case "crop_harvester", "planetary_harvester", "tree_feller", "mob_collector", "fishing_dock", "vacuum_inlet", "quarry_drill", "storm_turbine", "android_station" -> true;
            default -> false;
        };
    }

    private boolean isSafeCropTarget(final Block crop) {
        if (!this.plugin.getConfig().getBoolean("machine-safety.crop-harvester-require-farmland", true)) {
            return true;
        }
        return crop.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND;
    }

    private boolean isSafeTreeTarget(final Block origin) {
        if (!this.plugin.getConfig().getBoolean("machine-safety.tree-feller-require-natural-tree", true)) {
            return true;
        }
        final Material below = origin.getRelative(BlockFace.DOWN).getType();
        final boolean rooted = below == Material.DIRT || below == Material.GRASS_BLOCK || below == Material.PODZOL || below == Material.MYCELIUM || below == Material.MOSS_BLOCK;
        if (!rooted) {
            return false;
        }
        int leaves = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 1; dy <= 4; dy++) {
                    final Material type = origin.getWorld().getBlockAt(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz).getType();
                    if (type.name().endsWith("_LEAVES")) {
                        leaves++;
                        if (leaves >= 3) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private boolean isSafeMobTarget(final LivingEntity living) {
        final List<String> blacklist = this.plugin.getConfig().getStringList("machine-safety.mob-blacklist");
        if (blacklist.stream().anyMatch(entry -> entry.equalsIgnoreCase(living.getType().name()))) {
            return false;
        }
        if (this.plugin.getConfig().getBoolean("machine-safety.mob-collector-ignore-named", true) && living.customName() != null) {
            return false;
        }
        if (this.plugin.getConfig().getBoolean("machine-safety.mob-collector-ignore-tamed", true)
                && living instanceof Tameable tameable && tameable.isTamed()) {
            return false;
        }
        if (this.plugin.getConfig().getBoolean("machine-safety.mob-collector-ignore-leashed", true)
                && living instanceof Mob mob && mob.isLeashed()) {
            return false;
        }
        return !(this.plugin.getConfig().getBoolean("machine-safety.mob-collector-ignore-babies", true)
                && living instanceof org.bukkit.entity.Ageable ageable && !ageable.isAdult());
    }

    private boolean isSafeVacuumItem(final Item item) {
        return item.getTicksLived() >= Math.max(0, this.plugin.getConfig().getInt("machine-safety.vacuum-min-item-age", 40))
                && item.getPickupDelay() <= 0;
    }

    private boolean hasFarmlandNearby(final Block origin, final int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (origin.getWorld().getBlockAt(origin.getX() + dx, origin.getY() - 1, origin.getZ() + dz).getType() == Material.FARMLAND) {
                    return true;
                }
            }
        }
        return false;
    }

    private String placementHintSummary(final String machineId) {
        final List<String> lines = this.itemFactory.machinePlacementHintLines(machineId);
        if (lines.isEmpty()) {
            return "";
        }
        return lines.get(0);
    }

    private boolean isManualCrusher(final String machineId) {
        return this.normalizeId(machineId).equals("crusher");
    }

    private boolean isManualCompressor(final String machineId) {
        return this.normalizeId(machineId).equals("compressor");
    }

    private boolean isManualOreWasher(final String machineId) {
        return this.normalizeId(machineId).equals("ore_washer");
    }

    private boolean isManualWireMill(final String machineId) {
        return this.normalizeId(machineId).equals("wire_mill");
    }

    private boolean isManualPurifier(final String machineId) {
        return this.normalizeId(machineId).equals("purifier");
    }

    private boolean isManualCentrifuge(final String machineId) {
        return this.normalizeId(machineId).equals("centrifuge");
    }

    private boolean isManualBioLab(final String machineId) {
        return this.normalizeId(machineId).equals("bio_lab");
    }

    private boolean isManualChemReactor(final String machineId) {
        return this.normalizeId(machineId).equals("chemical_reactor");
    }

    private boolean isElectricAutoMachine(final String machineId) {
        return switch (this.normalizeId(machineId)) {
            case "electric_crusher", "electric_compressor", "electric_ore_washer", "electric_wire_mill",
                 "electric_purifier", "electric_centrifuge", "electric_bio_lab", "electric_chemical_reactor" -> true;
            default -> false;
        };
    }

    private Block crusherCoreCandidate(final Block anchor) {
        if (anchor == null) {
            return null;
        }
        final PlacedMachine direct = this.machines.get(LocationKey.from(anchor.getLocation()));
        if (direct != null && this.isManualCrusher(direct.machineId())) {
            return anchor;
        }
        if (anchor.getType() != Material.IRON_BARS) {
            return null;
        }
        final Block below = anchor.getRelative(BlockFace.DOWN);
        final PlacedMachine machineBelow = this.machines.get(LocationKey.from(below.getLocation()));
        return machineBelow != null && this.isManualCrusher(machineBelow.machineId()) ? below : null;
    }

    private CrusherRigLayout crusherRigLayout(final Block anchor) {
        final Block core = this.crusherCoreCandidate(anchor);
        if (core == null || core.getType() != Material.BLAST_FURNACE) {
            return null;
        }
        final Block grate = core.getRelative(BlockFace.UP);
        return grate.getType() == Material.IRON_BARS ? new CrusherRigLayout(core, grate) : null;
    }

    private boolean isCrusherControlBlock(final Block interactedBlock, final Block machineBlock) {
        final CrusherRigLayout layout = this.crusherRigLayout(machineBlock);
        return layout != null && this.sameBlock(layout.grate(), interactedBlock);
    }

    private Block compressorCoreCandidate(final Block anchor) {
        if (anchor == null) {
            return null;
        }
        final PlacedMachine direct = this.machines.get(LocationKey.from(anchor.getLocation()));
        if (direct != null && this.isManualCompressor(direct.machineId())) {
            return anchor;
        }
        if (anchor.getType() != Material.HEAVY_WEIGHTED_PRESSURE_PLATE) {
            return null;
        }
        final Block below = anchor.getRelative(BlockFace.DOWN);
        final PlacedMachine machineBelow = this.machines.get(LocationKey.from(below.getLocation()));
        return machineBelow != null && this.isManualCompressor(machineBelow.machineId()) ? below : null;
    }

    private CompressorRigLayout compressorRigLayout(final Block anchor) {
        final Block core = this.compressorCoreCandidate(anchor);
        if (core == null || core.getType() != Material.PISTON) {
            return null;
        }
        final Block plate = core.getRelative(BlockFace.UP);
        return plate.getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE ? new CompressorRigLayout(core, plate) : null;
    }

    private boolean isCompressorControlBlock(final Block interactedBlock, final Block machineBlock) {
        final CompressorRigLayout layout = this.compressorRigLayout(machineBlock);
        return layout != null && this.sameBlock(layout.pressPlate(), interactedBlock);
    }

    private Block oreWasherCoreCandidate(final Block anchor) {
        if (anchor == null) {
            return null;
        }
        final PlacedMachine direct = this.machines.get(LocationKey.from(anchor.getLocation()));
        if (direct != null && this.isManualOreWasher(direct.machineId())) {
            return anchor;
        }
        if (anchor.getType() != Material.LEVER) {
            return null;
        }
        final Block below = anchor.getRelative(BlockFace.DOWN);
        final PlacedMachine machineBelow = this.machines.get(LocationKey.from(below.getLocation()));
        return machineBelow != null && this.isManualOreWasher(machineBelow.machineId()) ? below : null;
    }

    private OreWasherRigLayout oreWasherRigLayout(final Block anchor) {
        final Block core = this.oreWasherCoreCandidate(anchor);
        if (core == null || core.getType() != Material.BARREL) {
            return null;
        }
        final Block lever = core.getRelative(BlockFace.UP);
        return lever.getType() == Material.LEVER ? new OreWasherRigLayout(core, lever) : null;
    }

    private boolean isOreWasherControlBlock(final Block interactedBlock, final Block machineBlock) {
        final OreWasherRigLayout layout = this.oreWasherRigLayout(machineBlock);
        return layout != null && this.sameBlock(layout.lever(), interactedBlock);
    }

    private Block wireMillCoreCandidate(final Block anchor) {
        if (anchor == null) {
            return null;
        }
        final PlacedMachine direct = this.machines.get(LocationKey.from(anchor.getLocation()));
        if (direct != null && this.isManualWireMill(direct.machineId())) {
            return anchor;
        }
        if (anchor.getType() != Material.IRON_BARS) {
            return null;
        }
        final Block below = anchor.getRelative(BlockFace.DOWN);
        final PlacedMachine machineBelow = this.machines.get(LocationKey.from(below.getLocation()));
        return machineBelow != null && this.isManualWireMill(machineBelow.machineId()) ? below : null;
    }

    private WireMillRigLayout wireMillRigLayout(final Block anchor) {
        final Block core = this.wireMillCoreCandidate(anchor);
        if (core == null || core.getType() != Material.ANVIL) {
            return null;
        }
        final Block bars = core.getRelative(BlockFace.UP);
        return bars.getType() == Material.IRON_BARS ? new WireMillRigLayout(core, bars) : null;
    }

    private boolean isWireMillControlBlock(final Block interactedBlock, final Block machineBlock) {
        final WireMillRigLayout layout = this.wireMillRigLayout(machineBlock);
        return layout != null && this.sameBlock(layout.ironBars(), interactedBlock);
    }

    private Block purifierCoreCandidate(final Block anchor) {
        if (anchor == null) {
            return null;
        }
        final PlacedMachine direct = this.machines.get(LocationKey.from(anchor.getLocation()));
        if (direct != null && this.isManualPurifier(direct.machineId())) {
            return anchor;
        }
        if (anchor.getType() != Material.STONE_BUTTON) {
            return null;
        }
        final Block below = anchor.getRelative(BlockFace.DOWN);
        final PlacedMachine machineBelow = this.machines.get(LocationKey.from(below.getLocation()));
        return machineBelow != null && this.isManualPurifier(machineBelow.machineId()) ? below : null;
    }

    private PurifierRigLayout purifierRigLayout(final Block anchor) {
        final Block core = this.purifierCoreCandidate(anchor);
        if (core == null || core.getType() != Material.CAULDRON) {
            return null;
        }
        final Block button = core.getRelative(BlockFace.UP);
        return button.getType() == Material.STONE_BUTTON ? new PurifierRigLayout(core, button) : null;
    }

    private boolean isPurifierControlBlock(final Block interactedBlock, final Block machineBlock) {
        final PurifierRigLayout layout = this.purifierRigLayout(machineBlock);
        return layout != null && this.sameBlock(layout.button(), interactedBlock);
    }

    private Block centrifugeCoreCandidate(final Block anchor) {
        if (anchor == null) {
            return null;
        }
        final PlacedMachine direct = this.machines.get(LocationKey.from(anchor.getLocation()));
        if (direct != null && this.isManualCentrifuge(direct.machineId())) {
            return anchor;
        }
        if (anchor.getType() != Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            return null;
        }
        final Block below = anchor.getRelative(BlockFace.DOWN);
        final PlacedMachine machineBelow = this.machines.get(LocationKey.from(below.getLocation()));
        return machineBelow != null && this.isManualCentrifuge(machineBelow.machineId()) ? below : null;
    }

    private CentrifugeRigLayout centrifugeRigLayout(final Block anchor) {
        final Block core = this.centrifugeCoreCandidate(anchor);
        if (core == null || core.getType() != Material.HOPPER) {
            return null;
        }
        final Block plate = core.getRelative(BlockFace.UP);
        return plate.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE ? new CentrifugeRigLayout(core, plate) : null;
    }

    private boolean isCentrifugeControlBlock(final Block interactedBlock, final Block machineBlock) {
        final CentrifugeRigLayout layout = this.centrifugeRigLayout(machineBlock);
        return layout != null && this.sameBlock(layout.plate(), interactedBlock);
    }

    private Block bioLabCoreCandidate(final Block anchor) {
        if (anchor == null) {
            return null;
        }
        final PlacedMachine direct = this.machines.get(LocationKey.from(anchor.getLocation()));
        if (direct != null && this.isManualBioLab(direct.machineId())) {
            return anchor;
        }
        if (anchor.getType() != Material.TRIPWIRE_HOOK) {
            return null;
        }
        final Block below = anchor.getRelative(BlockFace.DOWN);
        final PlacedMachine machineBelow = this.machines.get(LocationKey.from(below.getLocation()));
        return machineBelow != null && this.isManualBioLab(machineBelow.machineId()) ? below : null;
    }

    private BioLabRigLayout bioLabRigLayout(final Block anchor) {
        final Block core = this.bioLabCoreCandidate(anchor);
        if (core == null || core.getType() != Material.BREWING_STAND) {
            return null;
        }
        final Block hook = core.getRelative(BlockFace.UP);
        return hook.getType() == Material.TRIPWIRE_HOOK ? new BioLabRigLayout(core, hook) : null;
    }

    private boolean isBioLabControlBlock(final Block interactedBlock, final Block machineBlock) {
        final BioLabRigLayout layout = this.bioLabRigLayout(machineBlock);
        return layout != null && this.sameBlock(layout.hook(), interactedBlock);
    }

    private Block chemReactorCoreCandidate(final Block anchor) {
        if (anchor == null) {
            return null;
        }
        final PlacedMachine direct = this.machines.get(LocationKey.from(anchor.getLocation()));
        if (direct != null && this.isManualChemReactor(direct.machineId())) {
            return anchor;
        }
        if (anchor.getType() != Material.COMPARATOR) {
            return null;
        }
        final Block below = anchor.getRelative(BlockFace.DOWN);
        final PlacedMachine machineBelow = this.machines.get(LocationKey.from(below.getLocation()));
        return machineBelow != null && this.isManualChemReactor(machineBelow.machineId()) ? below : null;
    }

    private ChemReactorRigLayout chemReactorRigLayout(final Block anchor) {
        final Block core = this.chemReactorCoreCandidate(anchor);
        if (core == null || core.getType() != Material.BEACON) {
            return null;
        }
        final Block comparator = core.getRelative(BlockFace.UP);
        return comparator.getType() == Material.COMPARATOR ? new ChemReactorRigLayout(core, comparator) : null;
    }

    private boolean isChemReactorControlBlock(final Block interactedBlock, final Block machineBlock) {
        final ChemReactorRigLayout layout = this.chemReactorRigLayout(machineBlock);
        return layout != null && this.sameBlock(layout.comparator(), interactedBlock);
    }

    private PlacedMachine quarryMachineAt(final Block block) {
        if (block == null) {
            return null;
        }
        final PlacedMachine machine = this.machines.get(LocationKey.from(block.getLocation()));
        return machine != null && this.isQuarryLike(machine.machineId()) ? machine : null;
    }

    private boolean isPlacedQuarryMachineBlock(final Block block) {
        final PlacedMachine machine = this.quarryMachineAt(block);
        if (machine == null) {
            return false;
        }
        final MachineDefinition definition = this.registry.getMachine(machine.machineId());
        return definition != null && this.isMachineBlock(block, definition);
    }

    private Block primaryQuarryMachine(final QuarryRigLayout layout) {
        return this.compareBlockLocation(layout.machineA(), layout.machineB()) <= 0 ? layout.machineA() : layout.machineB();
    }

    private int compareBlockLocation(final Block first, final Block second) {
        int compare = first.getWorld().getName().compareTo(second.getWorld().getName());
        if (compare != 0) {
            return compare;
        }
        compare = Integer.compare(first.getY(), second.getY());
        if (compare != 0) {
            return compare;
        }
        compare = Integer.compare(first.getX(), second.getX());
        if (compare != 0) {
            return compare;
        }
        return Integer.compare(first.getZ(), second.getZ());
    }

    private boolean sameBlock(final Block first, final Block second) {
        return first != null
                && second != null
                && first.getWorld().equals(second.getWorld())
                && first.getX() == second.getX()
                && first.getY() == second.getY()
                && first.getZ() == second.getZ();
    }

    private boolean isPrimaryQuarryMachine(final PlacedMachine machine, final QuarryRigLayout layout) {
        return this.sameBlock(this.machineBlock(machine.locationKey()), this.primaryQuarryMachine(layout));
    }

    private void setQuarryRigEnabled(final QuarryRigLayout layout, final boolean enabled) {
        if (layout == null) {
            return;
        }
        final LocationKey controlKey = LocationKey.from(this.primaryQuarryMachine(layout).getLocation());
        final PlacedMachine machineA = this.quarryMachineAt(layout.machineA());
        final PlacedMachine machineB = this.quarryMachineAt(layout.machineB());
        if (machineA != null) {
            machineA.setEnabled(enabled);
            machineA.setQuarryCursor(0, 0);
        }
        if (machineB != null) {
            machineB.setEnabled(enabled);
            machineB.setQuarryCursor(0, 0);
        }
        if (!enabled) {
            this.quarryBusy.remove(controlKey);
        }
        this.quarryWarmedUp.remove(controlKey);
        this.refreshQuarryDisplays(layout);
    }

    private BlockFace[] quarrySupportFaces(final Block core) {
        if (core == null) {
            return null;
        }
        if (core.getRelative(BlockFace.EAST).getType() == Material.IRON_BLOCK
                && core.getRelative(BlockFace.WEST).getType() == Material.IRON_BLOCK) {
            return new BlockFace[]{BlockFace.EAST, BlockFace.WEST};
        }
        if (core.getRelative(BlockFace.NORTH).getType() == Material.IRON_BLOCK
                && core.getRelative(BlockFace.SOUTH).getType() == Material.IRON_BLOCK) {
            return new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH};
        }
        return null;
    }

    private Block quarryCoreCandidate(final Block anchor) {
        if (anchor == null) {
            return null;
        }
        final QuarryRigLayout layout = this.quarryRigLayout(anchor);
        if (layout != null) {
            return layout.core();
        }
        if (anchor.getType() == Material.BLAST_FURNACE && anchor.getRelative(BlockFace.UP).getType() == Material.CHEST) {
            return anchor;
        }
        final Block support = anchor.getRelative(BlockFace.DOWN);
        if (support.getType() != Material.IRON_BLOCK) {
            return null;
        }
        Block best = null;
        int bestScore = Integer.MIN_VALUE;
        for (final BlockFace face : Arrays.asList(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            final Block candidate = support.getRelative(face);
            int score = 0;
            if (candidate.getType() == Material.BLAST_FURNACE) {
                score += 3;
            }
            if (candidate.getRelative(BlockFace.UP).getType() == Material.CHEST) {
                score += 2;
            }
            if (candidate.getRelative(face).getType() == Material.IRON_BLOCK) {
                score += 2;
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return bestScore > 0 ? best : null;
    }

    private Block quarryControlMachine(final Block core) {
        final BlockFace[] supportFaces = this.quarrySupportFaces(core);
        if (supportFaces == null) {
            return null;
        }
        final Block machineA = core.getRelative(supportFaces[0]).getRelative(BlockFace.UP);
        final Block machineB = core.getRelative(supportFaces[1]).getRelative(BlockFace.UP);
        final PlacedMachine placedA = this.quarryMachineAt(machineA);
        final PlacedMachine placedB = this.quarryMachineAt(machineB);
        if (placedA == null && placedB == null) {
            return null;
        }
        if (placedA == null) {
            return machineB;
        }
        if (placedB == null) {
            return machineA;
        }
        return this.compareBlockLocation(machineA, machineB) <= 0 ? machineA : machineB;
    }

    private String describeDetectedBlock(final Block block) {
        if (block == null) {
            return "無";
        }
        final String raw = block.getType().name().toLowerCase().replace('_', ' ');
        final StringBuilder builder = new StringBuilder();
        boolean capitalize = true;
        for (int index = 0; index < raw.length(); index++) {
            final char current = raw.charAt(index);
            if (capitalize && Character.isLetter(current)) {
                builder.append(Character.toUpperCase(current));
                capitalize = false;
            } else {
                builder.append(current);
            }
            if (current == ' ') {
                capitalize = true;
            }
        }
        return builder.toString();
    }

    private ItemStack recipeInfo(final MachineRecipe recipe) {
        return this.info(Material.PAPER, this.itemFactory.displayNameForId(recipe.outputId()), List.of(
            "輸入：" + this.itemFactory.joinDisplayNames(recipe.inputIds(), " + "),
            "輸出：" + this.itemFactory.displayNameForId(recipe.outputId()),
                "耗能：" + recipe.energyCost() + " EU",
                this.describeRecipeFlow(recipe),
                "提示：條件不足、輸出滿格或能量不足時不會生產"
        ));
    }

    private void showRecipeDetail(final Player player, final LocationKey key, final String recipeId, final int backPage) {
        final PlacedMachine placedMachine = this.machines.get(key);
        if (placedMachine == null) {
            return;
        }
        final MachineGuiTheme theme = this.resolveMachineGuiTheme(placedMachine.machineId());
        final MachineRecipe recipe = this.registry.getRecipesForMachine(placedMachine.machineId()).stream()
                .filter(entry -> entry.id().equalsIgnoreCase(recipeId))
                .findFirst()
                .orElse(null);
        if (recipe == null) {
            return;
        }
        final Inventory inventory = Bukkit.createInventory(null, 54, this.itemFactory.hex(RECIPE_TITLE_PREFIX + this.itemFactory.displayNameForId(recipe.outputId()), this.titleColor(theme)));
        this.decorateMachineRecipeDetailMenu(inventory, theme);
        inventory.setItem(4, this.info(this.recipeBookMaterial(theme), this.itemFactory.displayNameForId(recipe.outputId()), List.of(
            "機器：" + this.itemFactory.displayNameForId(recipe.machineId()),
            "耗能：" + recipe.energyCost() + " EU",
            this.describeRecipeFlow(recipe)
        )));
        inventory.setItem(10, this.sectionPane(this.recipeInputPane(theme), "材料區", List.of("左下 3x3 是實際投入順序")));
        inventory.setItem(13, this.sectionPane(this.recipeProcessPane(theme), "製程", List.of("中間顯示工作站 / 機器")));
        inventory.setItem(16, this.sectionPane(this.recipeOutputPane(theme), "結果", List.of("右側顯示完成產物")));
        final int[] gridSlots = {19, 20, 21, 28, 29, 30, 37, 38, 39};
        for (int index = 0; index < gridSlots.length; index++) {
            inventory.setItem(gridSlots[index], recipe.inputIds().size() > index
                    ? this.recipeResultStack(recipe.inputIds().get(index))
                : this.sectionPane(this.recipeAccentPane(theme), "·", List.of()));
        }
        inventory.setItem(23, this.sectionPane(this.recipeAccentPane(theme), "→", List.of()));
        inventory.setItem(24, this.info(this.processInfoMaterial(theme), this.processLabel(theme), List.of(
            "機器：" + this.itemFactory.displayNameForId(recipe.machineId()),
            "耗能：" + recipe.energyCost() + " EU"
        )));
        inventory.setItem(25, this.sectionPane(this.recipeAccentPane(theme), "→", List.of()));
        inventory.setItem(32, this.sectionPane(this.recipeOutputPane(theme), "產出", List.of("完成後會進入輸出槽")));
        inventory.setItem(33, this.recipeResultStack(recipe.outputId()));
        inventory.setItem(42, this.info(Material.PAPER, "配方說明", List.of(
            "輸入：" + this.itemFactory.joinDisplayNames(recipe.inputIds(), " + "),
            "輸出：" + this.itemFactory.displayNameForId(recipe.outputId()),
            this.describeRecipeFlow(recipe)
        )));
        inventory.setItem(49, this.itemFactory.tagGuiAction(this.guiButton("machine-recipes-back-list", this.previousButtonMaterial(theme), "返回配方列表", List.of("回到第 {page} 頁"), this.placeholders("page", String.valueOf(backPage + 1))), "recipes:" + backPage));
        this.trackViewOpen(player.getUniqueId(), key, new MachineViewSession(key, ViewMode.RECIPES, backPage, inventory));
        player.openInventory(inventory);
    }

    private ItemStack recipeResultStack(final String outputId) {
        final ItemStack stack = this.buildStackForId(outputId);
        return stack == null ? this.info(Material.BARRIER, "未知輸出", List.of(outputId)) : stack;
    }

    private String describeRecipeFlow(final MachineRecipe recipe) {
        return this.itemFactory.joinDisplayNames(recipe.inputIds(), " + ") + " → "
                + this.itemFactory.displayNameForId(recipe.machineId()) + " → "
                + this.itemFactory.displayNameForId(recipe.outputId());
    }

    private ItemStack info(final Material material, final String title, final List<String> lines) {
        if (material != null && material.name().contains("GLASS_PANE")) {
            return this.itemFactory.tagGuiPlaceholder(this.itemFactory.buildGuiPane(
                    material,
                    this.itemFactory.warning(title),
                    this.itemFactory.mutedLore(lines),
                    true
            ));
        }
        final ItemStack stack = new ItemStack(this.itemFactory.safeItemMaterial(material));
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.itemFactory.warning(title));
        meta.lore(this.itemFactory.mutedLore(lines));
        this.itemFactory.applyGuiHudModel(meta, stack.getType(), true);
        stack.setItemMeta(meta);
        return this.itemFactory.tagGuiPlaceholder(stack);
    }

    private ItemStack guiButton(final String key,
                                final Material fallbackMaterial,
                                final String fallbackTitle,
                                final List<String> fallbackLore) {
        return this.itemFactory.buildGuiButton(key, fallbackMaterial, fallbackTitle, fallbackLore);
    }

    private ItemStack guiButton(final String key,
                                final Material fallbackMaterial,
                                final String fallbackTitle,
                                final List<String> fallbackLore,
                                final java.util.Map<String, String> placeholders) {
        return this.itemFactory.buildGuiButton(key, fallbackMaterial, fallbackTitle, fallbackLore, placeholders);
    }

    private java.util.Map<String, String> placeholders(final String... values) {
        final java.util.Map<String, String> placeholders = new java.util.LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            placeholders.put(values[index], values[index + 1]);
        }
        return placeholders;
    }

    private boolean isInputSlot(final int rawSlot) {
        for (final int slot : INPUT_SLOTS) {
            if (slot == rawSlot) {
                return true;
            }
        }
        return false;
    }

    private boolean isOutputSlot(final int rawSlot) {
        for (final int slot : OUTPUT_SLOTS) {
            if (slot == rawSlot) {
                return true;
            }
        }
        return false;
    }

    private boolean isUpgradeSlot(final int rawSlot) {
        for (final int slot : UPGRADE_SLOTS) {
            if (slot == rawSlot) {
                return true;
            }
        }
        return false;
    }

    private ItemStack insertIntoMachineInputs(final PlacedMachine machine, final ItemStack stack) {
        ItemStack remaining = stack.clone();
        int startSlot = 0;
        if (machine.machineId().equalsIgnoreCase("filter_router")) {
            for (int s = 0; s < Math.min(3, INPUT_SLOTS.length); s++) {
                if (this.resolveStackId(machine.inputAt(s)) != null) {
                    startSlot = s + 1;
                } else {
                    break;
                }
            }
        }
        for (int slot = startSlot; slot < INPUT_SLOTS.length; slot++) {
            final ItemStack current = machine.inputAt(slot);
            if (current == null || current.getType() == Material.AIR) {
                machine.setInputAt(slot, remaining);
                return new ItemStack(Material.AIR);
            }
            if (!current.isSimilar(remaining) || current.getAmount() >= current.getMaxStackSize()) {
                continue;
            }
            final int room = current.getMaxStackSize() - current.getAmount();
            final int move = Math.min(room, remaining.getAmount());
            current.setAmount(current.getAmount() + move);
            machine.setInputAt(slot, current);
            remaining.setAmount(remaining.getAmount() - move);
            if (remaining.getAmount() <= 0) {
                return new ItemStack(Material.AIR);
            }
        }
        return remaining;
    }

    private List<Block> collectConnectedLogs(final Block origin, final int maxLogs) {
        final List<Block> result = new ArrayList<>();
        final List<Block> frontier = new ArrayList<>();
        final java.util.Set<String> visited = new java.util.HashSet<>();
        frontier.add(origin);
        while (!frontier.isEmpty() && result.size() < maxLogs) {
            final Block current = frontier.remove(0);
            final String key = current.getWorld().getName() + ":" + current.getX() + ":" + current.getY() + ":" + current.getZ();
            if (!visited.add(key) || !this.isTreeLog(current.getType())) {
                continue;
            }
            result.add(current);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        frontier.add(current.getWorld().getBlockAt(current.getX() + dx, current.getY() + dy, current.getZ() + dz));
                    }
                }
            }
        }
        return result;
    }

    private boolean isTreeLog(final Material material) {
        final String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_WOOD") || material == Material.MANGROVE_ROOTS;
    }

    private Material saplingForLog(final Material log) {
        return switch (log) {
            case SPRUCE_LOG, SPRUCE_WOOD -> Material.SPRUCE_SAPLING;
            case BIRCH_LOG, BIRCH_WOOD -> Material.BIRCH_SAPLING;
            case JUNGLE_LOG, JUNGLE_WOOD -> Material.JUNGLE_SAPLING;
            case ACACIA_LOG, ACACIA_WOOD -> Material.ACACIA_SAPLING;
            case DARK_OAK_LOG, DARK_OAK_WOOD -> Material.DARK_OAK_SAPLING;
            case CHERRY_LOG, CHERRY_WOOD -> Material.CHERRY_SAPLING;
            case MANGROVE_LOG, MANGROVE_WOOD, MANGROVE_ROOTS -> Material.MANGROVE_PROPAGULE;
            default -> Material.OAK_SAPLING;
        };
    }

    private boolean isCollectableMob(final EntityType type) {
        return switch (type) {
            case COW, SHEEP, PIG, CHICKEN, RABBIT, ZOMBIE, SKELETON, CREEPER, SPIDER, SLIME, HUSK, STRAY, DROWNED -> true;
            default -> false;
        };
    }

    private List<ItemStack> mobDropsFor(final EntityType type) {
        final List<ItemStack> drops = new ArrayList<>();
        switch (type) {
            case COW -> {
                drops.add(this.buildStackForId("leather", 1));
                drops.add(this.buildStackForId("beef", 2));
            }
            case SHEEP -> {
                drops.add(this.buildStackForId("white_wool", 1));
                drops.add(this.buildStackForId("mutton", 1));
            }
            case PIG -> drops.add(this.buildStackForId("porkchop", 2));
            case CHICKEN -> {
                drops.add(this.buildStackForId("chicken", 1));
                drops.add(this.buildStackForId("feather", 1));
            }
            case RABBIT -> drops.add(this.buildStackForId("rabbit_hide", 1));
            case ZOMBIE, HUSK, DROWNED -> drops.add(this.buildStackForId("rotten_flesh", 2));
            case SKELETON, STRAY -> {
                drops.add(this.buildStackForId("bone", 2));
                drops.add(this.buildStackForId("arrow", 1));
            }
            case CREEPER -> drops.add(this.buildStackForId("gunpowder", 2));
            case SPIDER -> {
                drops.add(this.buildStackForId("string", 2));
                drops.add(this.buildStackForId("spider_eye", 1));
            }
            case SLIME -> drops.add(this.buildStackForId("slime_ball", 2));
            default -> {
                return List.of();
            }
        }
        drops.removeIf(stack -> stack == null || stack.getType() == Material.AIR);
        return drops;
    }

    private int countAdjacentWater(final World world, final Location location) {
        int total = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                final Material material = world.getBlockAt(location.getBlockX() + dx, location.getBlockY() - 1, location.getBlockZ() + dz).getType();
                if (material == Material.WATER) {
                    total++;
                }
            }
        }
        return total;
    }

    private ItemStack randomFishingLoot() {
        final int roll = ThreadLocalRandom.current().nextInt(100);
        if (roll < 45) {
            return this.buildStackForId("cod", 2);
        }
        if (roll < 70) {
            return this.buildStackForId("salmon", 2);
        }
        if (roll < 82) {
            return this.buildStackForId("tropical_fish", 1);
        }
        if (roll < 90) {
            return this.buildStackForId("pufferfish", 1);
        }
        if (roll < 96) {
            return this.buildStackForId("nautilus_shell", 1);
        }
        return this.buildStackForId("string", 2);
    }

    private ItemStack[] readInventorySection(final List<?> rawList) {
        return this.readInventorySection(rawList, 9);
    }

    private ItemStack[] readInventorySection(final List<?> rawList, final int size) {
        final ItemStack[] contents = new ItemStack[size];
        if (rawList == null) {
            return contents;
        }
        for (int index = 0; index < Math.min(contents.length, rawList.size()); index++) {
            final Object value = rawList.get(index);
            if (value instanceof ItemStack itemStack) {
                contents[index] = itemStack;
            }
        }
        return contents;
    }

    private String resolveStackId(final ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return null;
        }
        final String techId = this.itemFactory.getTechItemId(stack);
        if (techId != null && !techId.isBlank()) {
            return this.normalizeId(techId.contains(":") ? techId.substring(0, techId.indexOf(':')) : techId);
        }
        final String machineId = this.itemFactory.getMachineId(stack);
        if (machineId != null && !machineId.isBlank()) {
            return this.normalizeId(machineId);
        }
        final String materialId = this.normalizeId(stack.getType().name());
        return switch (materialId) {
            case "wheat_seeds" -> "crop_seeds";
            case "wheat" -> "crops";
            default -> materialId;
        };
    }

    private String resolveRecipeStackKey(final ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return null;
        }
        final String techId = this.itemFactory.getTechItemId(stack);
        if (techId != null && !techId.isBlank()) {
            return "tech:" + this.normalizeId(techId.contains(":") ? techId.substring(0, techId.indexOf(':')) : techId);
        }
        final String machineId = this.itemFactory.getMachineId(stack);
        if (machineId != null && !machineId.isBlank()) {
            return "machine:" + this.normalizeId(machineId);
        }
        return "vanilla:" + this.resolveVanillaAlias(stack.getType());
    }

    private String normalizeRecipeInputKey(final String input) {
        final String normalized = this.normalizeId(input);
        if (normalized.startsWith("tech:") || normalized.startsWith("machine:") || normalized.startsWith("vanilla:")) {
            return normalized;
        }
        if (this.isVanillaBackedAlias(normalized)) {
            return "vanilla:" + normalized;
        }
        if (this.registry.getItem(normalized) != null) {
            return "tech:" + normalized;
        }
        if (this.registry.getMachine(normalized) != null) {
            return "machine:" + normalized;
        }
        return "vanilla:" + normalized;
    }

    private boolean stackMatchesRecipeInput(final ItemStack stack, final String expectedId) {
        final String stackKey = this.resolveRecipeStackKey(stack);
        return stackKey != null && stackKey.equalsIgnoreCase(this.normalizeRecipeInputKey(expectedId));
    }

    private String resolveVanillaAlias(final Material material) {
        final String normalized = this.normalizeId(material.name());
        return switch (normalized) {
            case "wheat_seeds" -> "crop_seeds";
            case "wheat" -> "crops";
            default -> normalized;
        };
    }

    private boolean isVanillaBackedAlias(final String id) {
        return id.equalsIgnoreCase("crop_seeds") || id.equalsIgnoreCase("crops");
    }

    private String normalizeId(final String id) {
        return id == null ? "" : id.toLowerCase().trim();
    }

    private boolean matchesDirection(final String configured, final String actual) {
        return configured == null || configured.equalsIgnoreCase("ALL") || configured.equalsIgnoreCase(actual);
    }

    private String directionForOffset(final int[] offset) {
        if (offset[0] == 1) {
            return "EAST";
        }
        if (offset[0] == -1) {
            return "WEST";
        }
        if (offset[2] == 1) {
            return "SOUTH";
        }
        if (offset[2] == -1) {
            return "NORTH";
        }
        if (offset[1] == 1) {
            return "UP";
        }
        return "DOWN";
    }

    private String oppositeDirection(final String direction) {
        return switch (direction.toUpperCase()) {
            case "NORTH" -> "SOUTH";
            case "SOUTH" -> "NORTH";
            case "EAST" -> "WEST";
            case "WEST" -> "EAST";
            case "UP" -> "DOWN";
            case "DOWN" -> "UP";
            default -> "ALL";
        };
    }

    private String nextDirection(final String current) {
        final String normalized = current == null ? "ALL" : current.toUpperCase();
        for (int index = 0; index < DIRECTION_ORDER.length; index++) {
            if (!DIRECTION_ORDER[index].equals(normalized)) {
                continue;
            }
            return DIRECTION_ORDER[(index + 1) % DIRECTION_ORDER.length];
        }
        return "ALL";
    }

    private String directionDisplayName(final String direction) {
        return switch ((direction == null ? "ALL" : direction).toUpperCase()) {
            case "NORTH" -> "北";
            case "EAST" -> "東";
            case "SOUTH" -> "南";
            case "WEST" -> "西";
            case "UP" -> "上";
            case "DOWN" -> "下";
            default -> "全部";
        };
    }

    private String directionCycleDisplay() {
        return "順序：全部 → 北 → 東 → 南 → 西 → 上 → 下";
    }

    private record QuarryDrop(String outputId, Material replacement, int amount, long energyCost) {
    }

    private boolean machineAcceptsInput(final PlacedMachine machine, final ItemStack stack) {
        final String stackId = this.resolveStackId(stack);
        if (stackId == null) {
            return false;
        }

        final String mid = machine.machineId().toLowerCase();

        if (mid.equals("trash_node")) {
            return true;
        }

        if (mid.equals("storage_hub") || mid.equals("splitter_node") || mid.equals("industrial_bus")
                || mid.equals("cargo_manager") || mid.equals("cargo_motor")) {
            return this.hasMachineInputRoom(machine, stack, 0, INPUT_SLOTS.length);
        }
        if (mid.equals("filter_router")) {
            final boolean blacklist = machine.filterMode().equalsIgnoreCase("BLACKLIST");
            final java.util.Set<String> filterIds = new java.util.HashSet<>();
            for (int s = 0; s < Math.min(3, INPUT_SLOTS.length); s++) {
                final String fid = this.resolveStackId(machine.inputAt(s));
                if (fid != null) {
                    filterIds.add(fid);
                }
            }
            if (filterIds.isEmpty()) {
                return false;
            }
            final boolean matches = filterIds.contains(stackId);
            if (blacklist ? matches : !matches) {
                return false;
            }
            final int startSlot = filterIds.size();
            return this.hasMachineInputRoom(machine, stack, startSlot, INPUT_SLOTS.length);
        }

        boolean usedInRecipe = false;
        for (final MachineRecipe recipe : this.registry.getRecipesForMachine(machine.machineId())) {
            if (recipe.inputIds().stream().map(this::normalizeId).anyMatch(stackId::equals)) {
                usedInRecipe = true;
                break;
            }
        }
        if (!usedInRecipe) {
            return false;
        }

        return this.hasMachineInputRoom(machine, stack, 0, INPUT_SLOTS.length);
    }

    /** 判斷一台機器是否應該自動轉移輸出（物流類機器才允許）。 */
    private boolean isAutoTransferSource(final String machineId) {
        return switch (machineId.toLowerCase()) {
            case "storage_hub", "filter_router", "splitter_node", "industrial_bus",
                 "cargo_manager", "cargo_motor", "cargo_input_node", "trash_node",
                 "logistics_node", "item_tube", "recycler", "vacuum_inlet",
                 "auto_farm", "crop_harvester", "planetary_harvester", "tree_feller",
                 "mob_collector", "fishing_dock", "greenhouse",
                 "quarry_drill", "quarry_drill_mk2", "quarry_drill_mk3" -> true;
            default -> false;
        };
    }

    private boolean isRelayMachine(final String machineId, final boolean logisticsMode) {
        final String id = machineId.toLowerCase();
        return logisticsMode
                ? id.equals("logistics_node") || id.equals("item_tube") || id.equals("industrial_bus") || id.equals("cargo_motor")
                : id.equals("energy_node") || id.equals("energy_cable");
    }

    private int networkDepth(final PlacedMachine machine, final boolean logisticsMode) {
        int depth = 8 + this.countUpgrade(machine, "range_upgrade") * 6;
        final String id = machine.machineId().toLowerCase();
        if (logisticsMode && id.equals("industrial_bus")) {
            depth += 16;
        }
        if (logisticsMode && id.equals("cargo_manager")) {
            depth += 32;
        }
        if (logisticsMode && (id.equals("storage_hub") || id.equals("cargo_motor"))) {
            depth += 8;
        }
        return depth;
    }

    private boolean hasMachineInputRoom(final PlacedMachine machine, final ItemStack stack, final int startSlot, final int endSlot) {
        for (int slot = startSlot; slot < endSlot; slot++) {
            final ItemStack current = machine.inputAt(slot);
            if (current == null || current.getType() == Material.AIR) {
                return true;
            }
            if (current.isSimilar(stack) && current.getAmount() < current.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private int countUpgrade(final PlacedMachine machine, final String upgradeId) {
        int total = 0;
        for (final ItemStack stack : machine.upgradeInventory()) {
            final String id = this.resolveStackId(stack);
            if (upgradeId.equalsIgnoreCase(id)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    private boolean isUpgradeItem(final ItemStack stack) {
        final String id = this.resolveStackId(stack);
        return id != null && (id.equals("speed_upgrade") || id.equals("efficiency_upgrade") || id.equals("stack_upgrade") || id.equals("range_upgrade"));
    }

    private void handleManualMachineStorageClick(final Player player,
                                                 final PlacedMachine machine,
                                                 final int rawSlot,
                                                 final boolean rightClick) {
        if (player.getItemOnCursor() == null || player.getItemOnCursor().getType() == Material.AIR) {
            this.takeMachineSlotToCursor(player, machine, rawSlot, rightClick);
            return;
        }
        final boolean upgradeSlot = this.isUpgradeSlot(rawSlot);
        if (upgradeSlot && !this.isUpgradeItem(player.getItemOnCursor())) {
            return;
        }
        this.placeCursorIntoMachineSlot(player, machine, rawSlot, rightClick);
    }

    private void handleManualMachineOutputClick(final Player player,
                                                final PlacedMachine machine,
                                                final int rawSlot,
                                                final boolean rightClick) {
        final ItemStack slotStack = this.machineSlotItem(machine, rawSlot);
        if (slotStack == null || slotStack.getType() == Material.AIR) {
            return;
        }
        final ItemStack cursor = player.getItemOnCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            this.takeMachineSlotToCursor(player, machine, rawSlot, rightClick);
            return;
        }
        if (!cursor.isSimilar(slotStack) || cursor.getAmount() >= cursor.getMaxStackSize()) {
            return;
        }
        final int move = Math.min(rightClick ? 1 : slotStack.getAmount(), cursor.getMaxStackSize() - cursor.getAmount());
        if (move <= 0) {
            return;
        }
        cursor.setAmount(cursor.getAmount() + move);
        this.setPlayerCursor(player, cursor);
        slotStack.setAmount(slotStack.getAmount() - move);
        this.setMachineSlotItem(machine, rawSlot, slotStack.getAmount() <= 0 ? null : slotStack);
        this.pushOpenViewState(machine.locationKey(), machine);
    }

    private void takeMachineSlotToCursor(final Player player,
                                         final PlacedMachine machine,
                                         final int rawSlot,
                                         final boolean rightClick) {
        final ItemStack slotStack = this.machineSlotItem(machine, rawSlot);
        if (slotStack == null || slotStack.getType() == Material.AIR) {
            return;
        }
        final int takeAmount = rightClick ? Math.max(1, (slotStack.getAmount() + 1) / 2) : slotStack.getAmount();
        final ItemStack taken = slotStack.clone();
        taken.setAmount(takeAmount);
        this.setPlayerCursor(player, taken);
        slotStack.setAmount(slotStack.getAmount() - takeAmount);
        this.setMachineSlotItem(machine, rawSlot, slotStack.getAmount() <= 0 ? null : slotStack);
        this.pushOpenViewState(machine.locationKey(), machine);
    }

    private void placeCursorIntoMachineSlot(final Player player,
                                            final PlacedMachine machine,
                                            final int rawSlot,
                                            final boolean rightClick) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            return;
        }
        final ItemStack slotStack = this.machineSlotItem(machine, rawSlot);
        if (slotStack == null || slotStack.getType() == Material.AIR) {
            final ItemStack placed = cursor.clone();
            if (rightClick) {
                placed.setAmount(1);
                cursor.setAmount(cursor.getAmount() - 1);
                this.setPlayerCursor(player, cursor.getAmount() <= 0 ? null : cursor);
            } else {
                this.setPlayerCursor(player, null);
            }
            this.setMachineSlotItem(machine, rawSlot, placed);
            this.pushOpenViewState(machine.locationKey(), machine);
            return;
        }
        if (slotStack.isSimilar(cursor)) {
            final int room = slotStack.getMaxStackSize() - slotStack.getAmount();
            if (room <= 0) {
                return;
            }
            final int move = Math.min(room, rightClick ? 1 : cursor.getAmount());
            slotStack.setAmount(slotStack.getAmount() + move);
            cursor.setAmount(cursor.getAmount() - move);
            this.setMachineSlotItem(machine, rawSlot, slotStack);
            this.setPlayerCursor(player, cursor.getAmount() <= 0 ? null : cursor);
            this.pushOpenViewState(machine.locationKey(), machine);
            return;
        }
        if (this.isUpgradeSlot(rawSlot) && !this.isUpgradeItem(cursor)) {
            return;
        }
        this.setMachineSlotItem(machine, rawSlot, cursor);
        this.setPlayerCursor(player, slotStack);
        this.pushOpenViewState(machine.locationKey(), machine);
    }

    private void moveMachineSlotToPlayer(final Player player, final PlacedMachine machine, final int rawSlot) {
        final ItemStack slotStack = this.machineSlotItem(machine, rawSlot);
        if (slotStack == null || slotStack.getType() == Material.AIR) {
            return;
        }
        final ItemStack remaining = this.addToPlayerInventory(player, slotStack);
        this.setMachineSlotItem(machine, rawSlot, remaining.getType() == Material.AIR ? null : remaining);
        this.pushOpenViewState(machine.locationKey(), machine);
    }

    private ItemStack addToPlayerInventory(final Player player, final ItemStack stack) {
        final Map<Integer, ItemStack> leftover = player.getInventory().addItem(stack.clone());
        if (leftover.isEmpty()) {
            return new ItemStack(Material.AIR);
        }
        return leftover.values().iterator().next();
    }

    private void applyManualMachineDrag(final Player player,
                                        final PlacedMachine machine,
                                        final InventoryDragEvent event) {
        ItemStack cursor = event.getOldCursor() == null ? null : event.getOldCursor().clone();
        if (cursor == null || cursor.getType() == Material.AIR) {
            return;
        }
        for (final var entry : event.getNewItems().entrySet()) {
            final int rawSlot = entry.getKey();
            if (!this.isInputSlot(rawSlot) && !this.isUpgradeSlot(rawSlot)) {
                continue;
            }
            if (this.isUpgradeSlot(rawSlot) && !this.isUpgradeItem(cursor)) {
                continue;
            }
            final ItemStack before = this.machineSlotItem(machine, rawSlot);
            final ItemStack after = entry.getValue();
            final int beforeAmount = before == null || before.getType() == Material.AIR ? 0 : before.getAmount();
            final int afterAmount = after == null || after.getType() == Material.AIR ? 0 : after.getAmount();
            final int placed = Math.max(0, afterAmount - beforeAmount);
            if (placed <= 0) {
                continue;
            }
            if (before == null || before.getType() == Material.AIR) {
                final ItemStack placedStack = cursor.clone();
                placedStack.setAmount(Math.min(placed, cursor.getAmount()));
                this.setMachineSlotItem(machine, rawSlot, placedStack);
            } else if (before.isSimilar(cursor)) {
                final ItemStack updated = before.clone();
                updated.setAmount(Math.min(updated.getMaxStackSize(), before.getAmount() + placed));
                this.setMachineSlotItem(machine, rawSlot, updated);
            }
            cursor.setAmount(cursor.getAmount() - placed);
            if (cursor.getAmount() <= 0) {
                cursor = null;
                break;
            }
        }
        this.setPlayerCursor(player, cursor == null || cursor.getAmount() <= 0 ? null : cursor);
        this.pushOpenViewState(machine.locationKey(), machine);
    }

    private void setPlayerCursor(final Player player, final ItemStack stack) {
        player.setItemOnCursor(stack);
        player.getOpenInventory().setCursor(stack);
    }

    private void refreshManualMachineInteraction(final Player player, final PlacedMachine machine) {
        this.pushOpenViewState(machine.locationKey(), machine);
        player.updateInventory();
        this.scheduler.runEntityDelayed(player, player::updateInventory, 1L);
    }

    private ItemStack pushIntoMachineStorage(final PlacedMachine machine, final ItemStack stack) {
        if (this.isUpgradeItem(stack)) {
            ItemStack remaining = stack.clone();
            for (int slot = 0; slot < UPGRADE_SLOTS.length; slot++) {
                final ItemStack current = machine.upgradeAt(slot);
                if (current == null || current.getType() == Material.AIR) {
                    machine.setUpgradeAt(slot, remaining);
                    return new ItemStack(Material.AIR);
                }
                if (!current.isSimilar(remaining) || current.getAmount() >= current.getMaxStackSize()) {
                    continue;
                }
                final int room = current.getMaxStackSize() - current.getAmount();
                final int move = Math.min(room, remaining.getAmount());
                current.setAmount(current.getAmount() + move);
                machine.setUpgradeAt(slot, current);
                remaining.setAmount(remaining.getAmount() - move);
                if (remaining.getAmount() <= 0) {
                    return new ItemStack(Material.AIR);
                }
            }
            return remaining;
        }
        return this.insertIntoMachineInputs(machine, stack);
    }

    private ItemStack machineSlotItem(final PlacedMachine machine, final int rawSlot) {
        for (int index = 0; index < INPUT_SLOTS.length; index++) {
            if (INPUT_SLOTS[index] == rawSlot) {
                return machine.inputAt(index);
            }
        }
        for (int index = 0; index < OUTPUT_SLOTS.length; index++) {
            if (OUTPUT_SLOTS[index] == rawSlot) {
                return machine.outputAt(index);
            }
        }
        for (int index = 0; index < UPGRADE_SLOTS.length; index++) {
            if (UPGRADE_SLOTS[index] == rawSlot) {
                return machine.upgradeAt(index);
            }
        }
        return null;
    }

    private void setMachineSlotItem(final PlacedMachine machine, final int rawSlot, final ItemStack stack) {
        for (int index = 0; index < INPUT_SLOTS.length; index++) {
            if (INPUT_SLOTS[index] == rawSlot) {
                machine.setInputAt(index, stack);
                return;
            }
        }
        for (int index = 0; index < OUTPUT_SLOTS.length; index++) {
            if (OUTPUT_SLOTS[index] == rawSlot) {
                machine.setOutputAt(index, stack);
                return;
            }
        }
        for (int index = 0; index < UPGRADE_SLOTS.length; index++) {
            if (UPGRADE_SLOTS[index] == rawSlot) {
                machine.setUpgradeAt(index, stack);
                return;
            }
        }
    }

    private long effectiveEnergyCost(final PlacedMachine machine, final long baseCost) {
        final int efficiency = this.countUpgrade(machine, "efficiency_upgrade");
        final double multiplier = Math.max(0.35D, 1.0D - (efficiency * 0.2D));
        return Math.max(1L, Math.round(baseCost * multiplier));
    }

    private void pullOpenViewState(final LocationKey key, final PlacedMachine machine) {
        final java.util.Set<UUID> viewers = this.viewersByMachine.get(key);
        if (viewers == null || viewers.isEmpty()) {
            return;
        }
        for (final UUID uuid : viewers) {
            final MachineViewSession session = this.openViews.get(uuid);
            if (session == null || session.mode() != ViewMode.MAIN || !key.equals(session.locationKey())) {
                continue;
            }
            final Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                continue;
            }
            final Inventory top = online.getOpenInventory().getTopInventory();
            final String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(online.getOpenInventory().title());
            if (!this.isMachineView(title)) {
                continue;
            }
            final ItemStack[] input = new ItemStack[INPUT_SLOTS.length];
            final ItemStack[] output = new ItemStack[OUTPUT_SLOTS.length];
            final ItemStack[] upgrades = new ItemStack[UPGRADE_SLOTS.length];
            for (int index = 0; index < INPUT_SLOTS.length; index++) {
                input[index] = this.isMachineSlotEditable(machine, INPUT_SLOTS[index]) ? top.getItem(INPUT_SLOTS[index]) : machine.inputAt(index);
            }
            for (int index = 0; index < OUTPUT_SLOTS.length; index++) {
                output[index] = this.isMachineSlotEditable(machine, OUTPUT_SLOTS[index]) ? top.getItem(OUTPUT_SLOTS[index]) : machine.outputAt(index);
            }
            for (int index = 0; index < UPGRADE_SLOTS.length; index++) {
                upgrades[index] = this.isMachineSlotEditable(machine, UPGRADE_SLOTS[index]) ? top.getItem(UPGRADE_SLOTS[index]) : machine.upgradeAt(index);
            }
            machine.replaceInputInventory(input);
            machine.replaceOutputInventory(output);
            machine.replaceUpgradeInventory(upgrades);
        }
    }

    private void pushOpenViewState(final LocationKey key, final PlacedMachine machine) {
        final java.util.Set<UUID> viewers = this.viewersByMachine.get(key);
        if (viewers == null || viewers.isEmpty()) {
            return;
        }
        for (final UUID uuid : viewers) {
            final MachineViewSession session = this.openViews.get(uuid);
            if (session == null || session.mode() != ViewMode.MAIN || !key.equals(session.locationKey())) {
                continue;
            }
            final Player online = Bukkit.getPlayer(uuid);
            if (online == null) {
                continue;
            }
            final String title = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(online.getOpenInventory().title());
            if (!this.isMachineView(title)) {
                continue;
            }
            final Inventory top = online.getOpenInventory().getTopInventory();
            final MachineDefinition definition = this.registry.getMachine(machine.machineId());
            if (definition != null) {
                this.decorateMachineMenu(top, machine, definition);
                this.renderMachineStorageSlots(top, machine, definition);
            }
        }
    }

    private void renderMachineStorageSlots(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        for (int index = 0; index < INPUT_SLOTS.length; index++) {
            inventory.setItem(INPUT_SLOTS[index], this.renderMachineSlot(machine, definition, INPUT_SLOTS[index], machine.inputAt(index), "這台不吃原料"));
        }
        for (int index = 0; index < OUTPUT_SLOTS.length; index++) {
            inventory.setItem(OUTPUT_SLOTS[index], this.renderMachineSlot(machine, definition, OUTPUT_SLOTS[index], machine.outputAt(index), "這台不吐物品"));
        }
        for (int index = 0; index < UPGRADE_SLOTS.length; index++) {
            inventory.setItem(UPGRADE_SLOTS[index], this.renderMachineSlot(machine, definition, UPGRADE_SLOTS[index], machine.upgradeAt(index), "這格目前停用"));
        }
    }

    private ItemStack renderMachineSlot(final PlacedMachine machine,
                                        final MachineDefinition definition,
                                        final int rawSlot,
                                        final ItemStack actual,
                                        final String disabledText) {
        if (this.isMachineSlotEditable(machine, rawSlot)) {
            return actual;
        }
        final Material paneMaterial = this.isOutputSlot(rawSlot) ? Material.RED_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        final String title = this.isOutputSlot(rawSlot) ? "已封鎖輸出槽" : this.isUpgradeSlot(rawSlot) ? "已封鎖升級槽" : "已封鎖輸入槽";
        final List<String> lines = new ArrayList<>();
        lines.add(disabledText);
        lines.add("機器：" + this.itemFactory.displayNameForId(definition.id()));
        if (this.normalizeId(definition.id()).equals("solar_generator")) {
            lines.add("太陽能發電機只看日照，不需放燃料。");
        }
        if (this.normalizeId(definition.id()).equals("energy_node") || this.normalizeId(definition.id()).equals("energy_cable")) {
            lines.add("能源節點靠方向傳電，不存放物品。");
        }
        if (this.normalizeId(definition.id()).equals("logistics_node") || this.normalizeId(definition.id()).equals("item_tube")) {
            lines.add("這台是中繼結構，不是儲物箱。");
        }
        return this.info(paneMaterial, title, lines);
    }

    private boolean isMachineSlotEditable(final PlacedMachine machine, final int rawSlot) {
        final String machineId = this.normalizeId(machine.machineId());
        if (this.isUpgradeSlot(rawSlot)) {
            return !this.machineDisablesUpgradeSlots(machineId);
        }
        if (this.isInputSlot(rawSlot)) {
            return !this.machineDisablesInputSlots(machineId);
        }
        if (this.isOutputSlot(rawSlot)) {
            return !this.machineDisablesOutputSlots(machineId);
        }
        return false;
    }

    private boolean machineDisablesInputSlots(final String machineId) {
        return switch (machineId) {
            case "solar_generator", "storm_turbine", "energy_node", "energy_cable", "logistics_node", "item_tube" -> true;
            default -> false;
        };
    }

    private boolean machineDisablesOutputSlots(final String machineId) {
        return switch (machineId) {
            case "solar_generator", "coal_generator", "storm_turbine", "energy_node", "energy_cable", "logistics_node", "item_tube" -> true;
            default -> false;
        };
    }

    private boolean machineDisablesUpgradeSlots(final String machineId) {
        return switch (machineId) {
            case "solar_generator", "energy_node", "energy_cable", "logistics_node", "item_tube" -> true;
            default -> false;
        };
    }

    private void decorateMachineMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        boolean handled = true;
        switch (this.normalizeId(definition.id())) {
            case "crusher" -> this.decorateCrusherMenu(inventory, machine, definition);
            case "furnace" -> this.decorateFurnaceMenu(inventory, machine, definition);
            case "assembler" -> this.decorateAssemblerMenu(inventory, machine, definition);
            case "battery_bank" -> this.decorateBatteryBankMenu(inventory, machine, definition);
            case "logistics_node" -> this.decorateLogisticsNodeMenu(inventory, machine, definition);
            case "auto_farm" -> this.decorateAutoFarmMenu(inventory, machine, definition);
            case "electric_saw" -> this.decorateElectricSawMenu(inventory, machine, definition);
            case "recycler" -> this.decorateRecyclerMenu(inventory, machine, definition);
            case "solar_generator" -> this.decorateSolarGeneratorMenu(inventory, machine, definition);
            case "coal_generator" -> this.decorateCoalGeneratorMenu(inventory, machine, definition);
            case "compressor" -> this.decorateCompressorMenu(inventory, machine, definition);
            case "wire_mill" -> this.decorateWireMillMenu(inventory, machine, definition);
            case "ore_washer" -> this.decorateOreWasherMenu(inventory, machine, definition);
            case "purifier" -> this.decoratePurifierMenu(inventory, machine, definition);
            case "centrifuge" -> this.decorateCentrifugeMenu(inventory, machine, definition);
            case "greenhouse" -> this.decorateGreenhouseMenu(inventory, machine, definition);
            case "bio_lab" -> this.decorateBioLabMenu(inventory, machine, definition);
            case "energy_node" -> this.decorateEnergyNodeMenu(inventory, machine, definition);
            case "item_tube" -> this.decorateItemTubeMenu(inventory, machine, definition);
            case "storage_hub" -> this.decorateStorageHubMenu(inventory, machine, definition);
            case "filter_router" -> this.decorateFilterRouterMenu(inventory, machine, definition);
            case "splitter_node" -> this.decorateSplitterNodeMenu(inventory, machine, definition);
            case "industrial_bus" -> this.decorateIndustrialBusMenu(inventory, machine, definition);
            case "cargo_input_node" -> this.decorateCargoInputNodeMenu(inventory, machine, definition);
            case "cargo_manager" -> this.decorateCargoManagerMenu(inventory, machine, definition);
            case "trash_node" -> this.decorateTrashNodeMenu(inventory, machine, definition);
            case "cargo_motor" -> this.decorateCargoMotorMenu(inventory, machine, definition);
            case "quarry_drill", "quarry_drill_mk2", "quarry_drill_mk3" -> this.decorateQuarryDrillMenu(inventory, machine, definition);
            case "android_station" -> this.decorateAndroidStationMenu(inventory, machine, definition);
            case "android_item_interface" -> this.decorateAndroidItemInterfaceMenu(inventory, machine, definition);
            case "android_fuel_interface" -> this.decorateAndroidFuelInterfaceMenu(inventory, machine, definition);
            default -> handled = false;
        }
        if (!handled) {
            switch (this.resolveMachineGuiTheme(definition.id())) {
                case GENERATOR -> this.decorateGeneratorMachineMenu(inventory, machine, definition);
                case LOGISTICS -> this.decorateLogisticsMachineMenu(inventory, machine, definition);
                case FIELD -> this.decorateFieldMachineMenu(inventory, machine, definition);
                case PROCESSOR -> this.decorateProcessorMachineMenu(inventory, machine, definition);
            }
        }
        // 配方鎖定按鈕 — 放在所有裝飾之後，避免被 applyIdentityBand 等覆蓋
        this.applyRecipeLockButton(inventory, machine);
    }

    private void applyRecipeLockButton(final Inventory inventory, final PlacedMachine machine) {
        final List<MachineRecipe> allRecipes = this.registry.getRecipesForMachine(machine.machineId());
        if (allRecipes.size() <= 1) {
            return;
        }
        final String lockedId = machine.lockedRecipeId();
        String lockLabel;
        if (lockedId == null) {
            lockLabel = "§7未鎖定（自動匹配）";
        } else {
            lockLabel = lockedId;
            for (final MachineRecipe r : allRecipes) {
                if (r.id().equals(lockedId)) {
                    lockLabel = this.itemFactory.displayNameForId(r.outputId());
                    break;
                }
            }
        }
        inventory.setItem(15, this.itemFactory.tagGuiAction(this.guiButton("machine-lock-recipe", Material.COMPARATOR, "配方鎖定", List.of(
            "目前：" + lockLabel,
            "點一下切換鎖定配方",
            "§8鎖定後機器只跑該配方"
        )), "lock-recipe"));
    }

    private void decorateMachineRecipeMenu(final Inventory inventory, final MachineGuiTheme theme, final int safePage, final int maxPage) {
        final int[] frameSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 14, 15, 17, 18, 26, 27, 35, 36, 44, 46, 48, 50, 52};
        final int[] footerSlots = {45, 47, 49, 51, 53};
        for (final int slot : frameSlots) {
            inventory.setItem(slot, this.sectionPane(this.recipeInputPane(theme), " ", List.of()));
        }
        for (final int slot : footerSlots) {
            inventory.setItem(slot, this.sectionPane(this.recipeFooterPane(theme), " ", List.of()));
        }
        inventory.setItem(4, this.info(this.recipeBookMaterial(theme), this.themeRecipeTitle(theme), List.of("中間區域是可點擊配方", "第 " + (safePage + 1) + " / " + (maxPage + 1) + " 頁")));
        inventory.setItem(10, this.sectionPane(this.recipeInputPane(theme), "配方列表", List.of("下方每張卡都可直接點開")));
        inventory.setItem(13, this.sectionPane(this.recipeProcessPane(theme), "流程", List.of("統一配方檢視版型")));
        inventory.setItem(16, this.sectionPane(this.recipeOutputPane(theme), "說明", List.of("左鍵查看單一配方詳情")));
    }

    private void decorateMachineRecipeDetailMenu(final Inventory inventory, final MachineGuiTheme theme) {
        final int[] frameSlots = {0, 1, 2, 3, 5, 6, 7, 8, 9, 11, 12, 14, 15, 17, 18, 22, 26, 27, 31, 35, 36, 40, 44, 46, 47, 48, 50, 51, 52};
        for (final int slot : frameSlots) {
            inventory.setItem(slot, this.sectionPane(this.recipeInputPane(theme), " ", List.of()));
        }
        inventory.setItem(40, this.sectionPane(this.recipeFooterPane(theme), "材料流程", List.of("左側材料 → 中間製程 → 右側產出")));
    }

    private void decorateProcessorMachineMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.GRAY_STAINED_GLASS_PANE,
                Material.ORANGE_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.BOOK);
    }

    private void decorateGeneratorMachineMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.YELLOW_STAINED_GLASS_PANE,
                Material.ORANGE_STAINED_GLASS_PANE,
                Material.ORANGE_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE,
                Material.BLAZE_POWDER);
    }

    private void decorateLogisticsMachineMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.BLUE_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.COMPASS);
    }

    private void decorateFieldMachineMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIME_STAINED_GLASS_PANE,
                Material.GREEN_STAINED_GLASS_PANE,
                Material.BROWN_STAINED_GLASS_PANE,
                Material.GREEN_STAINED_GLASS_PANE,
                Material.OAK_SAPLING);
    }

    private void decorateCrusherMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.GRAY_STAINED_GLASS_PANE,
                Material.ORANGE_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.IRON_PICKAXE);
        final Block core = this.machineBlock(machine.locationKey());
        final CrusherRigLayout layout = this.crusherRigLayout(core);
        final List<String> operationLines = machine.hasManualOperation()
            ? List.of("剩餘：" + Math.max(0, machine.manualOperationTicks()) + " 秒", "加工完成後會自動吐出成品", "若卡住請先清空輸出槽")
            : List.of("目前待命", "準備好原料後右鍵鐵柵欄", "不是自動連續加工");
        inventory.setItem(12, this.sectionPane(Material.GRAY_STAINED_GLASS_PANE, "⟱", List.of()));
        inventory.setItem(15, this.sectionPane(Material.GRAY_STAINED_GLASS_PANE, "⟱", List.of()));
        inventory.setItem(17, this.sectionPane(Material.ORANGE_STAINED_GLASS_PANE, "粉末", List.of()));
        inventory.setItem(20, this.sectionPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "礦石", List.of()));
        inventory.setItem(21, this.sectionPane(Material.GRAY_STAINED_GLASS_PANE, "→", List.of()));
        inventory.setItem(22, this.sectionPane(Material.GRAY_STAINED_GLASS_PANE, "粉碎核心", List.of()));
        inventory.setItem(23, this.sectionPane(Material.GRAY_STAINED_GLASS_PANE, "→", List.of()));
        inventory.setItem(24, this.sectionPane(Material.ORANGE_STAINED_GLASS_PANE, "粉末", List.of()));
        inventory.setItem(25, this.info(layout != null ? Material.IRON_BARS : Material.BARRIER, "手動結構", List.of(
            layout != null ? "上方鐵柵欄：已就位" : "缺少上方鐵柵欄",
            "右鍵鐵柵欄：啟動粉碎",
            "蹲下 + 右鍵核心：開設定"
        )));
        inventory.setItem(26, this.info(machine.hasManualOperation() ? Material.CLOCK : Material.IRON_PICKAXE, "運轉節奏", operationLines));
    }

    private void decorateFurnaceMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.YELLOW_STAINED_GLASS_PANE,
                Material.ORANGE_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.FLINT_AND_STEEL);
        inventory.setItem(12, this.sectionPane(Material.ORANGE_STAINED_GLASS_PANE, "火焰", List.of()));
        inventory.setItem(15, this.sectionPane(Material.ORANGE_STAINED_GLASS_PANE, "火焰", List.of()));
        inventory.setItem(20, this.sectionPane(Material.YELLOW_STAINED_GLASS_PANE, "進料", List.of()));
        inventory.setItem(21, this.sectionPane(Material.ORANGE_STAINED_GLASS_PANE, "🔥", List.of()));
        inventory.setItem(22, this.sectionPane(Material.ORANGE_STAINED_GLASS_PANE, "熔煉", List.of()));
        inventory.setItem(23, this.sectionPane(Material.ORANGE_STAINED_GLASS_PANE, "🔥", List.of()));
        inventory.setItem(24, this.sectionPane(Material.RED_STAINED_GLASS_PANE, "錠材", List.of()));
    }

    private void decorateAssemblerMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.GREEN_STAINED_GLASS_PANE,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.CRAFTER);
        inventory.setItem(12, this.sectionPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "╔", List.of()));
        inventory.setItem(15, this.sectionPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "╗", List.of()));
        inventory.setItem(20, this.sectionPane(Material.GREEN_STAINED_GLASS_PANE, "零件", List.of()));
        inventory.setItem(21, this.sectionPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "+", List.of()));
        inventory.setItem(22, this.sectionPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "裝配", List.of()));
        inventory.setItem(23, this.sectionPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "+", List.of()));
        inventory.setItem(24, this.sectionPane(Material.CYAN_STAINED_GLASS_PANE, "組件", List.of()));
    }

    private void decorateAutoFarmMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIME_STAINED_GLASS_PANE,
                Material.GREEN_STAINED_GLASS_PANE,
                Material.YELLOW_STAINED_GLASS_PANE,
                Material.GREEN_STAINED_GLASS_PANE,
                Material.WHEAT_SEEDS);
        this.applyIdentityBand(inventory, Material.GREEN_STAINED_GLASS_PANE, "✿", "✿", "種子", "→", "耕作", "→", "作物");
    }

    private void decorateElectricSawMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.BROWN_STAINED_GLASS_PANE,
                Material.GRAY_STAINED_GLASS_PANE,
                Material.ORANGE_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.IRON_AXE);
        this.applyIdentityBand(inventory, Material.GRAY_STAINED_GLASS_PANE, "⇣", "⇣", "木材", "→", "鋸切", "→", "木屑");
    }

    private void decorateRecyclerMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.GRAY_STAINED_GLASS_PANE,
                Material.GREEN_STAINED_GLASS_PANE,
                Material.LIME_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.HOPPER);
        this.applyIdentityBand(inventory, Material.GREEN_STAINED_GLASS_PANE, "♻", "♻", "廢料", "→", "回收", "→", "再製");
    }

    private void decorateSolarGeneratorMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.YELLOW_STAINED_GLASS_PANE,
                Material.ORANGE_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE,
                Material.SUNFLOWER);
        this.applyIdentityBand(inventory, Material.ORANGE_STAINED_GLASS_PANE, "☀", "☀", "日照", "→", "轉能", "→", "電池");
    }

    private void decorateCoalGeneratorMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.YELLOW_STAINED_GLASS_PANE,
                Material.ORANGE_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE,
                Material.COAL);
        this.applyIdentityBand(inventory, Material.ORANGE_STAINED_GLASS_PANE, "🔥", "🔥", "燃煤", "→", "發電", "→", "電池");
    }

    private void decorateCompressorMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.GRAY_STAINED_GLASS_PANE,
                Material.ORANGE_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.PISTON);
        this.applyIdentityBand(inventory, Material.GRAY_STAINED_GLASS_PANE, "⇓", "⇓", "板材", "→", "壓縮", "→", "鋼板");
    }

    private void decorateWireMillMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.GRAY_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.IRON_BARS);
        this.applyIdentityBand(inventory, Material.GRAY_STAINED_GLASS_PANE, "↻", "↻", "錠材", "→", "拉線", "→", "線材");
    }

    private void decorateOreWasherMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.BLUE_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.WATER_BUCKET);
        this.applyIdentityBand(inventory, Material.CYAN_STAINED_GLASS_PANE, "≈", "≈", "礦粉", "→", "洗礦", "→", "礦漿");
    }

    private void decoratePurifierMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.WHITE_STAINED_GLASS_PANE,
                Material.YELLOW_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.SNOWBALL);
        this.applyIdentityBand(inventory, Material.WHITE_STAINED_GLASS_PANE, "✦", "✦", "原液", "→", "淨化", "→", "結晶");
    }

    private void decorateCentrifugeMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.GRAY_STAINED_GLASS_PANE,
                Material.YELLOW_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.HOPPER);
        this.applyIdentityBand(inventory, Material.GRAY_STAINED_GLASS_PANE, "⟳", "⟲", "晶體", "→", "離心", "→", "離子");
    }

    private void decorateGreenhouseMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIME_STAINED_GLASS_PANE,
                Material.GREEN_STAINED_GLASS_PANE,
                Material.YELLOW_STAINED_GLASS_PANE,
                Material.GREEN_STAINED_GLASS_PANE,
                Material.MANGROVE_LEAVES);
        this.applyIdentityBand(inventory, Material.GREEN_STAINED_GLASS_PANE, "❀", "❀", "苗種", "→", "培育", "→", "燈/膠");
    }

    private void decorateBioLabMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.GREEN_STAINED_GLASS_PANE,
                Material.LIME_STAINED_GLASS_PANE,
                Material.YELLOW_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.SLIME_BALL);
        this.applyIdentityBand(inventory, Material.LIME_STAINED_GLASS_PANE, "☣", "☣", "生質", "→", "分析", "→", "樹脂");
    }

    private void decorateBatteryBankMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.YELLOW_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE,
                Material.ORANGE_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE,
                Material.REDSTONE_BLOCK);
        inventory.setItem(12, this.sectionPane(Material.RED_STAINED_GLASS_PANE, "⚡", List.of()));
        inventory.setItem(15, this.sectionPane(Material.RED_STAINED_GLASS_PANE, "⚡", List.of()));
        inventory.setItem(20, this.sectionPane(Material.YELLOW_STAINED_GLASS_PANE, "充電", List.of()));
        inventory.setItem(21, this.sectionPane(Material.RED_STAINED_GLASS_PANE, "→", List.of()));
        inventory.setItem(22, this.sectionPane(Material.RED_STAINED_GLASS_PANE, "儲能庫", List.of()));
        inventory.setItem(23, this.sectionPane(Material.RED_STAINED_GLASS_PANE, "→", List.of()));
        inventory.setItem(24, this.sectionPane(Material.ORANGE_STAINED_GLASS_PANE, "放電", List.of()));
    }

    private void decorateQuarryDrillMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.GRAY_STAINED_GLASS_PANE,
                Material.BLACK_STAINED_GLASS_PANE,
                Material.ORANGE_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.IRON_PICKAXE);
        final Block machineBlock = this.machineBlock(machine.locationKey());
        final QuarryRigLayout layout = this.quarryRigLayout(machineBlock);
        final Block core = layout != null ? layout.core() : this.quarryCoreCandidate(machineBlock);
        final boolean complete = layout != null;
        final BlockFace[] supportFaces = this.quarrySupportFaces(core);
        final boolean supportsReady = supportFaces != null;
        inventory.setItem(12, this.sectionPane(complete ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE, complete ? "結構完整" : "結構缺件", List.of()));
        inventory.setItem(15, this.sectionPane(Material.ORANGE_STAINED_GLASS_PANE, this.quarryTierLabel(machine.machineId()), List.of()));
        inventory.setItem(20, this.info(core != null && core.getType() == Material.BLAST_FURNACE ? Material.BLAST_FURNACE : Material.BARRIER, "中央高爐", List.of(core != null && core.getType() == Material.BLAST_FURNACE ? "已就位" : "缺少或放錯")));
        inventory.setItem(21, this.info(core != null && core.getRelative(BlockFace.UP).getType() == Material.CHEST ? Material.CHEST : Material.BARRIER, "上方箱子", List.of(core != null && core.getRelative(BlockFace.UP).getType() == Material.CHEST ? "已就位" : "缺少或放錯")));
        inventory.setItem(22, this.structureStatus(core));
        inventory.setItem(23, this.info(supportsReady ? Material.IRON_BLOCK : Material.BARRIER, "雙側鐵方塊", List.of(supportsReady ? "已就位" : "需放在高爐同一軸兩側")));
        inventory.setItem(24, this.info(layout != null ? Material.BLAST_FURNACE : Material.BARRIER, "雙採礦機", List.of(layout != null ? "已就位" : "鐵方塊上方各放一台採礦鑽機")));
        inventory.setItem(25, this.info(machine.enabled() ? Material.LIME_DYE : Material.GRAY_DYE, "啟動狀態", List.of(machine.enabled() ? "運轉中" : "待機", "右鍵中央高爐：啟動 / 停止", "蹲下 + 右鍵中央高爐：開設定")));
        inventory.setItem(26, this.info(Material.LAVA_BUCKET, "燃料與輸出", List.of("可用：木炭 / 岩漿桶", "木炭 1 秒、岩漿桶 8 秒", "岩漿桶消耗後會留下空桶", "燃料請放上方箱子第 1 排", "採出的礦會放進箱子第 2 排之後")));
    }

    private void decorateAndroidStationMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.LIME_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.RECOVERY_COMPASS);
        final AndroidScriptProfile script = this.resolveAndroidScript(machine, this.locationFromKey(machine.locationKey()));
        inventory.setItem(11, this.itemFactory.tagGuiAction(this.guiButton("android-area-radius", Material.COMPASS, "區域半徑", List.of(
                "目前：" + machine.androidPatrolRadius() + " 格",
                "進階範圍升級與擴域卡會再加成",
                "點擊循環切換 1 ~ 6 格"
        )), "android-radius"));
        inventory.setItem(12, this.info(Material.BLAZE_POWDER, "運轉燃料", List.of(
                "目前：" + machine.androidFuel() + " / " + ANDROID_MAX_FUEL,
                "由相鄰自動機燃料介面補給",
                "接受煤、精煉油、能源電池等燃料"
        )));
        inventory.setItem(14, this.itemFactory.tagGuiAction(this.guiButton("android-guide-open", Material.WRITABLE_BOOK, "自動機教學頁", List.of(
                "點擊直接打開自動機專屬研究 / 教學頁",
                "包含巡邏、接法與自動模式"
        )), "android-guide"));
        inventory.setItem(15, this.info(script == null ? Material.BARRIER : Material.WRITABLE_BOOK, "執行程序", List.of(
                script == null ? "自動待命：附近沒有可執行目標" : script.displayName(),
                "未放程序晶片時會自動偵測作物 / 樹木 / 生物 / 掉落物",
                "左右兩格仍可放進階程序卡或一般升級"
        )));
        inventory.setItem(16, this.itemFactory.tagGuiAction(this.guiButton("android-route-mode", Material.CLOCK, "巡邏路線", List.of(
                "目前：" + this.androidRouteModeLabel(machine.androidRouteMode()),
                "點擊切換蛇形 / 螺旋 / 環帶",
                "真正用於單點巡邏掃描"
        )), "android-route"));
        inventory.setItem(20, this.sectionPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "物資倉", List.of()));
        inventory.setItem(21, this.sectionPane(Material.PURPLE_STAINED_GLASS_PANE, "→", List.of()));
        inventory.setItem(22, this.sectionPane(Material.PURPLE_STAINED_GLASS_PANE, "自動機核心", List.of()));
        inventory.setItem(23, this.sectionPane(Material.PURPLE_STAINED_GLASS_PANE, "→", List.of()));
        inventory.setItem(24, this.sectionPane(Material.LIME_STAINED_GLASS_PANE, "回收倉", List.of()));
        inventory.setItem(42, this.sectionPane(Material.CYAN_STAINED_GLASS_PANE, "濾波 / 程序 / 升級", List.of()));
        inventory.setItem(43, this.itemFactory.tagGuiAction(this.guiButton("android-area-height", Material.SPYGLASS, "掃描高度", List.of(
                "目前：+" + machine.androidPatrolHeight() + " 格",
                "點擊循環切換 0 ~ 4 格",
                "用於樹木 / 行星樣本立體巡邏"
        )), "android-height"));
        inventory.setItem(44, this.info(Material.REDSTONE, "進階程序卡", this.androidCardSummaryLines(machine)));
    }

        private void decorateAndroidItemInterfaceMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.CHEST);
        final PlacedMachine station = this.findLinkedAndroidStation(machine, this.locationFromKey(machine.locationKey()));
        inventory.setItem(12, this.info(station == null ? Material.BARRIER : Material.LODESTONE, "連線狀態", List.of(
            station == null ? "附近找不到你的自動機工作站" : "已連到最近的自動機工作站",
            "搜尋半徑：" + ANDROID_LINK_RADIUS + " 格"
        )));
        inventory.setItem(15, this.info(Material.HOPPER, "篩選樣本", List.of(
            "左側升級槽可放樣本物品",
            "有樣本時只會抽指定類型",
            "沒放樣本就抽全部產出"
        )));
        inventory.setItem(20, this.sectionPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "自動機輸出", List.of()));
        inventory.setItem(21, this.sectionPane(Material.BLUE_STAINED_GLASS_PANE, "→", List.of()));
        inventory.setItem(22, this.sectionPane(Material.BLUE_STAINED_GLASS_PANE, "介面緩衝", List.of()));
        inventory.setItem(23, this.sectionPane(Material.BLUE_STAINED_GLASS_PANE, "→", List.of()));
        inventory.setItem(24, this.sectionPane(Material.CYAN_STAINED_GLASS_PANE, "上方容器", List.of()));
        }

        private void decorateAndroidFuelInterfaceMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.RED_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.BLAZE_POWDER);
        final PlacedMachine station = this.findLinkedAndroidStation(machine, this.locationFromKey(machine.locationKey()));
        inventory.setItem(12, this.info(station == null ? Material.BARRIER : Material.LAVA_BUCKET, "燃料補給", List.of(
            station == null ? "附近找不到你的自動機工作站" : "工作站燃料：" + station.androidFuel() + " / " + ANDROID_MAX_FUEL,
            "低於 " + ANDROID_INTERFACE_LOW_WATERMARK + " 會自動補給",
            "支援岩漿桶並會回收空桶"
        )));
        inventory.setItem(15, this.info(Material.REDSTONE, "介面電力", List.of(
            "每次補給會消耗少量 EU",
            "可接能源節點長時間運轉"
        )));
        inventory.setItem(20, this.sectionPane(Material.YELLOW_STAINED_GLASS_PANE, "燃料輸入", List.of()));
        inventory.setItem(21, this.sectionPane(Material.ORANGE_STAINED_GLASS_PANE, "→", List.of()));
        inventory.setItem(22, this.sectionPane(Material.ORANGE_STAINED_GLASS_PANE, "轉換核心", List.of()));
        inventory.setItem(23, this.sectionPane(Material.ORANGE_STAINED_GLASS_PANE, "→", List.of()));
        inventory.setItem(24, this.sectionPane(Material.RED_STAINED_GLASS_PANE, "回收 / 空桶", List.of()));
        }

    private void decorateEnergyNodeMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.YELLOW_STAINED_GLASS_PANE,
                Material.ORANGE_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.LIGHTNING_ROD);
        this.applyIdentityBand(inventory, Material.ORANGE_STAINED_GLASS_PANE, "⚡", "⚡", "匯入", "→", "節點", "→", "匯出");
        inventory.setItem(11, this.itemFactory.tagGuiAction(this.guiButton("machine-dir-input", Material.HOPPER, "收電方向", List.of(
            "目前：" + this.directionDisplayName(machine.inputDirection()),
            "點一下切換方向",
            "這一側會從相鄰機器 / 節點收電",
            "全部 表示四周都可收"
        ), this.placeholders("current", String.valueOf(machine.inputDirection()))), "dir-input"));
        inventory.setItem(16, this.itemFactory.tagGuiAction(this.guiButton("machine-dir-output", Material.DROPPER, "送電方向", List.of(
            "目前：" + this.directionDisplayName(machine.outputDirection()),
            "點一下切換方向",
            "這一側會把電送往相鄰機器 / 節點",
            "全部 表示四周都可送"
        ), this.placeholders("current", String.valueOf(machine.outputDirection()))), "dir-output"));
        inventory.setItem(14, this.itemFactory.tagGuiAction(this.guiButton("energy-node-usage", Material.LIGHTNING_ROD, "節點用法", List.of(
            "至少成對放置最容易看懂",
            "A 節點收電，B 節點送電",
            "中間再接主幹或耗能機器"
        )), "recipes:0"));
    }

    private void decorateLogisticsNodeMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.BLUE_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.COMPASS);
        inventory.setItem(12, this.sectionPane(Material.BLUE_STAINED_GLASS_PANE, "⇄", List.of()));
        inventory.setItem(15, this.sectionPane(Material.BLUE_STAINED_GLASS_PANE, "⇄", List.of()));
        inventory.setItem(20, this.sectionPane(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "來源", List.of()));
        inventory.setItem(21, this.sectionPane(Material.BLUE_STAINED_GLASS_PANE, "→", List.of()));
        inventory.setItem(22, this.sectionPane(Material.BLUE_STAINED_GLASS_PANE, "中繼", List.of()));
        inventory.setItem(23, this.sectionPane(Material.BLUE_STAINED_GLASS_PANE, "→", List.of()));
        inventory.setItem(24, this.sectionPane(Material.CYAN_STAINED_GLASS_PANE, "去向", List.of()));
    }

    private void decorateItemTubeMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.BLUE_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.GLASS_PANE);
        this.applyIdentityBand(inventory, Material.BLUE_STAINED_GLASS_PANE, "⇢", "⇢", "入口", "→", "導管", "→", "出口");
    }

    private void decorateStorageHubMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.BLUE_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.CHEST);
        this.applyIdentityBand(inventory, Material.BLUE_STAINED_GLASS_PANE, "▣", "▣", "匯入", "→", "倉儲", "→", "匯出");
    }

    private void decorateFilterRouterMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.BLUE_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.TARGET);
        final boolean blacklist = machine.filterMode().equalsIgnoreCase("BLACKLIST");
        this.applyIdentityBand(inventory, Material.BLUE_STAINED_GLASS_PANE, "◈", "◈", "樣本", "→", blacklist ? "黑名單" : "白名單", "→", "指定線");
        inventory.setItem(15, this.itemFactory.tagGuiAction(this.guiButton("filter-mode-toggle",
                blacklist ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
                blacklist ? "§c黑名單模式" : "§a白名單模式",
                List.of(
                        blacklist ? "排除樣本物品，其餘全部通過" : "僅通過樣本物品，其餘全部阻擋",
                        "點擊切換模式",
                        "最多可放 3 個樣本物品"
                )), "filter-mode"));
    }

    private void decorateSplitterNodeMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.BLUE_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.DROPPER);
        this.applyIdentityBand(inventory, Material.BLUE_STAINED_GLASS_PANE, "↖", "↗", "主線", "→", "分流", "→", "支線");
    }

    private void decorateIndustrialBusMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.BLUE_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.IRON_BARS);
        this.applyIdentityBand(inventory, Material.BLUE_STAINED_GLASS_PANE, "═", "═", "主幹", "→", "總線", "→", "支幹");
    }

    private void decorateCargoInputNodeMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.YELLOW_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.HOPPER);
        this.applyIdentityBand(inventory, Material.YELLOW_STAINED_GLASS_PANE, "⇐", "⇒", "容器", "→", "抽取", "→", "產線");
    }

    private void decorateCargoManagerMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.MAGENTA_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.ENDER_EYE);
        this.applyIdentityBand(inventory, Material.MAGENTA_STAINED_GLASS_PANE, "◉", "◉", "無線", "→", "管理", "→", "匯集");
    }

    private void decorateTrashNodeMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.RED_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.LAVA_BUCKET);
        this.applyIdentityBand(inventory, Material.RED_STAINED_GLASS_PANE, "✕", "✕", "輸入", "→", "銷毀", "→", "§c虛空");
    }

    private void decorateCargoMotorMenu(final Inventory inventory, final PlacedMachine machine, final MachineDefinition definition) {
        this.decorateMinimalMachineMenu(inventory, machine, definition,
                Material.LIGHT_BLUE_STAINED_GLASS_PANE,
                Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                Material.CYAN_STAINED_GLASS_PANE,
                Material.PURPLE_STAINED_GLASS_PANE,
                Material.PISTON);
        this.applyIdentityBand(inventory, Material.LIGHT_GRAY_STAINED_GLASS_PANE, "⚡", "⚡", "高速", "→", "驅動", "→", "中繼");
    }

    private void applyIdentityBand(final Inventory inventory,
                                   final Material accent,
                                   final String topLeft,
                                   final String topRight,
                                   final String left,
                                   final String midLeft,
                                   final String core,
                                   final String midRight,
                                   final String right) {
        inventory.setItem(12, this.sectionPane(accent, topLeft, List.of()));
        inventory.setItem(15, this.sectionPane(accent, topRight, List.of()));
        inventory.setItem(20, this.sectionPane(accent, left, List.of()));
        inventory.setItem(21, this.sectionPane(accent, midLeft, List.of()));
        inventory.setItem(22, this.sectionPane(accent, core, List.of()));
        inventory.setItem(23, this.sectionPane(accent, midRight, List.of()));
        inventory.setItem(24, this.sectionPane(accent, right, List.of()));
    }

    private void decorateMinimalMachineMenu(final Inventory inventory,
                                            final PlacedMachine machine,
                                            final MachineDefinition definition,
                                            final Material inputPane,
                                            final Material centerPane,
                                            final Material outputPane,
                                            final Material upgradePane,
                                            final Material recipeMaterial) {
        final MachineLayoutSpec spec = this.resolveMachineLayoutSpec(definition.id());
        final int[] centerFrame = {9, 10, 12, 15, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26};
        for (final int slot : centerFrame) {
            inventory.setItem(slot, this.sectionPane(centerPane, " ", List.of()));
        }
        inventory.setItem(9, this.sectionPane(inputPane, "↘", List.of()));
        inventory.setItem(10, this.sectionPane(inputPane, "↓", List.of()));
        inventory.setItem(17, this.sectionPane(outputPane, "↙", List.of()));
        inventory.setItem(18, this.sectionPane(inputPane, spec.inputZone(), List.of()));
        inventory.setItem(19, this.sectionPane(inputPane, "↓", List.of()));
        inventory.setItem(25, this.sectionPane(outputPane, "↑", List.of()));
        inventory.setItem(26, this.sectionPane(outputPane, spec.outputZone(), List.of()));

        inventory.setItem(11, this.itemFactory.tagGuiAction(this.guiButton("machine-dir-input", Material.HOPPER, spec.inputDirectionTitle(), List.of(
            "目前：" + this.directionDisplayName(machine.inputDirection()),
            "點一下切換方向",
            this.directionCycleDisplay()
        ), this.placeholders("current", String.valueOf(machine.inputDirection()))), "dir-input"));
        inventory.setItem(13, this.itemFactory.buildMachineGuiIcon(definition));
        inventory.setItem(14, this.itemFactory.tagGuiAction(this.guiButton("machine-open-recipes", recipeMaterial, spec.recipeTitle(), List.of("點擊查看此機器配方列表")), "recipes:0"));
        inventory.setItem(16, this.itemFactory.tagGuiAction(this.guiButton("machine-dir-output", Material.DROPPER, spec.outputDirectionTitle(), List.of(
            "目前：" + this.directionDisplayName(machine.outputDirection()),
            "點一下切換方向",
            this.directionCycleDisplay()
        ), this.placeholders("current", String.valueOf(machine.outputDirection()))), "dir-output"));

        inventory.setItem(36, this.sectionPane(outputPane, "↑", List.of()));
        inventory.setItem(37, this.sectionPane(outputPane, spec.outputZone(), List.of()));
        inventory.setItem(38, this.sectionPane(outputPane, "↑", List.of()));
        inventory.setItem(42, this.sectionPane(upgradePane, spec.upgradeZone(), List.of()));
        inventory.setItem(43, this.sectionPane(upgradePane, "↑", List.of()));
        inventory.setItem(44, this.sectionPane(upgradePane, spec.upgradeZone(), List.of()));
    }

    private MachineLayoutSpec resolveMachineLayoutSpec(final String machineId) {
        return switch (this.normalizeId(machineId)) {
            case "research_desk" -> new MachineLayoutSpec("研究媒介", "研究輸出", "研究模組", "研究項目", "研究方向", "研究同步");
            case "crusher", "ore_washer", "purifier", "centrifuge", "plasma_refiner", "void_extractor" ->
                new MachineLayoutSpec("原料", "精煉物", "升級", "精煉配方", "原料方向", "精煉方向");
            case "furnace", "smeltery", "kiln", "refinery", "stellar_forge", "starsteel_foundry", "event_horizon_smith" ->
                new MachineLayoutSpec("熔材", "成品", "增幅", "熔鍛配方", "進料方向", "出料方向");
            case "assembler", "advanced_assembler", "orbital_printer", "celestial_assembler", "omega_fabricator", "warp_assembler", "anchor_forge", "apex_forge" ->
                new MachineLayoutSpec("零件", "組件", "模組", "裝配配方", "進件方向", "出件方向");
            case "laser_engraver", "wire_mill", "polymer_press", "insulation_press", "photon_weaver", "continuum_lathe" ->
                new MachineLayoutSpec("素材", "加工件", "調校", "加工配方", "素材方向", "成品方向");
            case "auto_farm", "greenhouse", "bio_lab", "gene_splicer", "biosynth_vat", "crystal_growth_chamber", "nanite_foundry" ->
                new MachineLayoutSpec("培育材", "培育物", "培養", "培育配方", "投入方向", "收成方向");
            case "solar_generator", "coal_generator", "solar_array", "storm_turbine", "fusion_reactor", "battery_bank", "chrono_engine", "entropy_chamber" ->
                new MachineLayoutSpec("能源材", "電力", "增幅", "能源配方", "進能方向", "出能方向");
            case "energy_node", "energy_cable" ->
                new MachineLayoutSpec("匯入", "導出", "增幅", "導能配方", "匯入方向", "導出方向");
            case "logistics_node", "item_tube", "storage_hub", "filter_router", "splitter_node", "industrial_bus", "cargo_motor", "data_archive", "observatory", "relic_scanner" ->
                new MachineLayoutSpec("匯入", "匯出", "路由", "物流配方", "匯入方向", "匯出方向");
            case "cargo_input_node" ->
                new MachineLayoutSpec("抽取", "匯出", "路由", "物流配方", "抽取方向", "匯出方向");
            case "cargo_manager" ->
                new MachineLayoutSpec("無線匯入", "匯出", "路由", "物流配方", "匯入方向", "匯出方向");
            case "trash_node" ->
                new MachineLayoutSpec("銷毀", "虛空", "加速", "物流配方", "匯入方向", "匯出方向");
            case "android_station" ->
                new MachineLayoutSpec("任務物資", "任務產出", "自動機模組", "自動機程序", "補給方向", "回收方向");
            case "android_item_interface" ->
                new MachineLayoutSpec("介面輸入", "介面輸出", "篩選樣本", "回收流程", "匯入方向", "匯出方向");
            case "android_fuel_interface" ->
                new MachineLayoutSpec("燃料輸入", "燃料回收", "轉換模組", "燃料流程", "匯入方向", "匯出方向");
            case "quarry_drill", "quarry_drill_mk2", "quarry_drill_mk3", "crop_harvester", "planetary_harvester", "planetary_gate", "vacuum_inlet", "tree_feller", "mob_collector", "fishing_dock" ->
                new MachineLayoutSpec("作業材", "收成", "強化", "作業配方", "匯入方向", "匯出方向");
            default -> new MachineLayoutSpec("投入", "產出", "模組", "配方", "輸入方向", "輸出方向");
        };
    }

    private MachineGuiTheme resolveMachineGuiTheme(final String machineId) {
        final String normalized = this.normalizeId(machineId);
        if (normalized.equals("research_desk")) {
            return MachineGuiTheme.PROCESSOR;
        }
        if (normalized.contains("generator") || normalized.contains("turbine") || normalized.contains("reactor") || normalized.contains("battery") || normalized.startsWith("energy_") || normalized.equals("solar_array")) {
            return MachineGuiTheme.GENERATOR;
        }
        if (normalized.equals("android_fuel_interface")) {
            return MachineGuiTheme.GENERATOR;
        }
        if (normalized.contains("router") || normalized.contains("splitter") || normalized.contains("bus") || normalized.contains("storage") || normalized.contains("logistics") || normalized.contains("tube") || normalized.contains("node") || normalized.contains("cable") || normalized.contains("cargo") || normalized.contains("motor") || normalized.contains("trash")) {
            return MachineGuiTheme.LOGISTICS;
        }
        if (normalized.equals("android_item_interface")) {
            return MachineGuiTheme.LOGISTICS;
        }
        if (normalized.equals("quarry_drill") || normalized.equals("quarry_drill_mk2") || normalized.equals("quarry_drill_mk3") || normalized.equals("crop_harvester") || normalized.equals("planetary_harvester") || normalized.equals("planetary_gate") || normalized.equals("vacuum_inlet") || normalized.equals("tree_feller") || normalized.equals("mob_collector") || normalized.equals("fishing_dock") || normalized.equals("greenhouse") || normalized.equals("auto_farm")) {
            return MachineGuiTheme.FIELD;
        }
        if (normalized.equals("android_station")) {
            return MachineGuiTheme.FIELD;
        }
        return MachineGuiTheme.PROCESSOR;
    }

    private String titleColor(final MachineGuiTheme theme) {
        return switch (theme) {
            case GENERATOR -> "#FFB703";
            case LOGISTICS -> "#4CC9F0";
            case FIELD -> "#80ED99";
            case PROCESSOR -> "#C77DFF";
        };
    }

    private Material previousButtonMaterial(final MachineGuiTheme theme) {
        return switch (theme) {
            case GENERATOR -> Material.YELLOW_DYE;
            case LOGISTICS -> Material.LIGHT_BLUE_DYE;
            case FIELD -> Material.LIME_DYE;
            case PROCESSOR -> Material.LIME_DYE;
        };
    }

    private Material nextButtonMaterial(final MachineGuiTheme theme) {
        return switch (theme) {
            case GENERATOR -> Material.ORANGE_DYE;
            case LOGISTICS -> Material.CYAN_DYE;
            case FIELD -> Material.GREEN_DYE;
            case PROCESSOR -> Material.MAGENTA_DYE;
        };
    }

    private Material recipeBookMaterial(final MachineGuiTheme theme) {
        return switch (theme) {
            case GENERATOR -> Material.BLAZE_POWDER;
            case LOGISTICS -> Material.COMPASS;
            case FIELD -> Material.OAK_SAPLING;
            case PROCESSOR -> Material.BOOK;
        };
    }

    private Material recipeInputPane(final MachineGuiTheme theme) {
        return switch (theme) {
            case GENERATOR -> Material.YELLOW_STAINED_GLASS_PANE;
            case LOGISTICS -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case FIELD -> Material.LIME_STAINED_GLASS_PANE;
            case PROCESSOR -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
        };
    }

    private Material recipeOutputPane(final MachineGuiTheme theme) {
        return switch (theme) {
            case GENERATOR -> Material.ORANGE_STAINED_GLASS_PANE;
            case LOGISTICS -> Material.CYAN_STAINED_GLASS_PANE;
            case FIELD -> Material.GREEN_STAINED_GLASS_PANE;
            case PROCESSOR -> Material.LIME_STAINED_GLASS_PANE;
        };
    }

    private Material recipeProcessPane(final MachineGuiTheme theme) {
        return switch (theme) {
            case GENERATOR -> Material.RED_STAINED_GLASS_PANE;
            case LOGISTICS -> Material.BLUE_STAINED_GLASS_PANE;
            case FIELD -> Material.BROWN_STAINED_GLASS_PANE;
            case PROCESSOR -> Material.CYAN_STAINED_GLASS_PANE;
        };
    }

    private Material recipeAccentPane(final MachineGuiTheme theme) {
        return switch (theme) {
            case GENERATOR -> Material.BLACK_STAINED_GLASS_PANE;
            case LOGISTICS -> Material.GRAY_STAINED_GLASS_PANE;
            case FIELD -> Material.GRAY_STAINED_GLASS_PANE;
            case PROCESSOR -> Material.GRAY_STAINED_GLASS_PANE;
        };
    }

    private Material recipeFooterPane(final MachineGuiTheme theme) {
        return switch (theme) {
            case GENERATOR -> Material.RED_STAINED_GLASS_PANE;
            case LOGISTICS -> Material.BLUE_STAINED_GLASS_PANE;
            case FIELD -> Material.GREEN_STAINED_GLASS_PANE;
            case PROCESSOR -> Material.PURPLE_STAINED_GLASS_PANE;
        };
    }

    private Material processInfoMaterial(final MachineGuiTheme theme) {
        return switch (theme) {
            case GENERATOR -> Material.LIGHTNING_ROD;
            case LOGISTICS -> Material.COMPASS;
            case FIELD -> Material.CHEST;
            case PROCESSOR -> Material.REDSTONE;
        };
    }

    private String processLabel(final MachineGuiTheme theme) {
        return switch (theme) {
            case GENERATOR -> "發電流程";
            case LOGISTICS -> "物流流程";
            case FIELD -> "作業流程";
            case PROCESSOR -> "製程";
        };
    }

    private String themeRecipeTitle(final MachineGuiTheme theme) {
        return switch (theme) {
            case GENERATOR -> "發電配方總覽";
            case LOGISTICS -> "物流配方總覽";
            case FIELD -> "戶外作業配方總覽";
            case PROCESSOR -> "加工配方總覽";
        };
    }

    private boolean canUseMachineType(final Player player, final String machineId, final boolean placing) {
        if (player.hasPermission("techproject.admin")) {
            return true;
        }
        if (machineId == null || machineId.isBlank()) {
            return false;
        }
        if (this.progressService.hasMachineUnlocked(player.getUniqueId(), machineId)) {
            return true;
        }
        player.sendMessage(this.itemFactory.danger((placing ? "你尚未研究這台機器，不能放置：" : "你尚未研究這台機器，不能使用介面：") + this.itemFactory.displayNameForId(machineId)));
        return false;
    }

    private boolean isResearchDesk(final String machineId) {
        return machineId != null && machineId.equalsIgnoreCase("research_desk");
    }

    private ItemStack sectionPane(final Material material, final String title, final List<String> lines) {
        if (material != null && material.name().contains("GLASS_PANE")) {
            return this.itemFactory.tagGuiPlaceholder(this.itemFactory.buildGuiPane(
                    material,
                    this.itemFactory.secondary(title),
                    this.itemFactory.mutedLore(lines),
                    false
            ));
        }
        return this.itemFactory.tagGuiPlaceholder(this.itemFactory.buildGuiPane(
                material,
                this.itemFactory.secondary(title),
                this.itemFactory.mutedLore(lines),
                false
        ));
    }

    private void dropStoredItems(final Location location, final ItemStack[] contents) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }
        for (final ItemStack stack : contents) {
            final ItemStack sanitized = this.sanitizeStoredStack(stack);
            if (sanitized == null || sanitized.getType() == Material.AIR) {
                continue;
            }
            world.dropItemNaturally(location.clone().add(0.5, 0.5, 0.5), sanitized.clone());
        }
    }

    private ItemStack[] sanitizeStoredContents(final ItemStack[] contents) {
        final ItemStack[] sanitized = new ItemStack[contents.length];
        for (int index = 0; index < contents.length; index++) {
            sanitized[index] = this.sanitizeStoredStack(contents[index]);
        }
        return sanitized;
    }

    private ItemStack sanitizeStoredStack(final ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return null;
        }
        if (this.itemFactory.getGuiAction(stack) != null || this.itemFactory.isGuiPlaceholder(stack) || this.isLegacyGuiPlaceholder(stack)) {
            return null;
        }
        return stack;
    }

    private boolean isLegacyGuiPlaceholder(final ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = stack.getItemMeta();
        final String title = meta.displayName() == null
                ? ""
                : net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        if (title.contains("已封鎖") || title.contains("輸入需求") || title.contains("返回機器") || title.contains("返回配方列表")) {
            return true;
        }
        if (meta.lore() == null || meta.lore().isEmpty()) {
            return false;
        }
        for (final Component line : meta.lore()) {
            final String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line);
            if (plain.contains("機器：") || plain.contains("這格目前停用") || plain.contains("這台不吐物品") || plain.contains("點擊查看") || plain.contains("回到第 ")) {
                return true;
            }
        }
        return false;
    }
}
