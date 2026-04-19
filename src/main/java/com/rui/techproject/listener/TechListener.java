package com.rui.techproject.listener;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.model.PlacedMachine;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.block.Container;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Item;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class TechListener implements Listener {
    private static final String VECTOR_GRAPPLE = "vector_grapple";
    private static final String PULSE_THRUSTER = "pulse_thruster";
    private static final String STORM_JETPACK = "storm_jetpack";
    private static final long GRAPPLE_ENERGY_COST = 5L;
    private static final long THRUSTER_ENERGY_COST = 8L;
    private static final long JETPACK_ENERGY_COST = 6L;

    /** 右鍵會觸發原版行為的材質（末影珍珠、終界之眼、玻璃瓶、礦車系列等） */
    private static final Set<Material> VANILLA_INTERACT_MATERIALS = Set.of(
            Material.ENDER_PEARL, Material.ENDER_EYE, Material.GLASS_BOTTLE, Material.WIND_CHARGE,
            Material.MINECART, Material.CHEST_MINECART, Material.HOPPER_MINECART,
            Material.TNT_MINECART, Material.FURNACE_MINECART, Material.COMMAND_BLOCK_MINECART
    );

    private final TechMCPlugin plugin;
    private final Map<UUID, Long> grappleCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> thrusterCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> jetpackCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mobilityGracePeriods = new ConcurrentHashMap<>();
    private final Set<UUID> managedJetpackFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> grappleArrowOwners = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> artifactCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> talismanCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastEquipmentTick = new ConcurrentHashMap<>();
    private final Set<UUID> magnetDisabled = ConcurrentHashMap.newKeySet();
    private final Set<UUID> thornsProcessing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> voidMirrorActive = new ConcurrentHashMap<>();
    private final Map<UUID, Long> seismicAxeCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> wrenchCooldowns = new ConcurrentHashMap<>();
    /**
     * 模擬 BlockBreakEvent 的執行緒 ID 集合 — 用於重入保護。
     * 公開為 static 讓 SpellExecutor 等外部模組共用，避免模擬事件觸發 onBlockBreak 處理器。
     */
    public static final Set<Long> simulatingBreakThreads = ConcurrentHashMap.newKeySet();
    private static final String TECH_MAGNET = "tech_magnet";
    private static final double MAGNET_RANGE = 5.0;

    private static final Set<String> ARTIFACT_IDS = Set.of(
            "pulse_staff", "storm_staff", "gravity_staff", "warp_orb", "cryo_wand",
            "plasma_lance", "void_mirror", "time_dilator", "heal_beacon", "entropy_scepter",
            // 異世界線 RPG 武器
            "riftblade", "frostfang_bow", "thunder_hammer", "arcane_scepter",
            // 迷途星線 RPG 武器
            "maze_blade", "echo_crossbow"
    );

    private static final Set<String> TALISMAN_IDS = Set.of(
            "talisman_fire", "talisman_water", "talisman_angel", "talisman_warrior",
            "talisman_knight", "talisman_traveler", "talisman_hunter", "talisman_miner",
            "talisman_farmer", "talisman_anvil", "talisman_heal", "talisman_whirlwind"
    );

    public TechListener(final TechMCPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        this.plugin.getPlanetService().recoverTravelState(player);
        this.plugin.getPlayerProgressService().ensureLoaded(player.getUniqueId());
        this.plugin.getPlayerProgressService().grantStartingProgress(player);
        if (this.plugin.getConfig().getBoolean("starting-techbook", true) && !this.hasTechBook(player)) {
            player.getInventory().addItem(this.plugin.getItemFactory().buildTechBook());
        }
        this.giveStarterKitIfNeeded(player);
        this.refreshJetpackFlightState(player);
        // 自動刷新背包中版本過舊的科技物品（延遲 1 tick 確保載入完成）
        this.plugin.getSafeScheduler().runEntityDelayed(player, () -> this.refreshPlayerInventory(player), 5L);
        // 登入時延遲掃描成就，補發任何已達成但未解鎖的成就
        this.plugin.getSafeScheduler().runEntityDelayed(player,
                () -> this.plugin.getAchievementService().evaluate(player.getUniqueId()), 40L);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final UUID playerId = event.getPlayer().getUniqueId();
        this.grappleCooldowns.remove(playerId);
        this.thrusterCooldowns.remove(playerId);
        this.jetpackCooldowns.remove(playerId);
        this.mobilityGracePeriods.remove(playerId);
        this.managedJetpackFlight.remove(playerId);
        this.artifactCooldowns.remove(playerId);
        this.talismanCooldowns.remove(playerId);
        this.lastEquipmentTick.remove(playerId);
        this.magnetDisabled.remove(playerId);
        this.voidMirrorActive.remove(playerId);
        this.seismicAxeCooldowns.remove(playerId);
        this.wrenchCooldowns.remove(playerId);
        this.thornsProcessing.remove(playerId);
        this.explorationDistance.remove(playerId);
        // 清理該玩家的抓鉤箭矢 UUID（避免 chunk 卸載導致殘留）
        this.grappleArrowOwners.values().removeIf(owner -> owner.equals(playerId));
        this.plugin.getMachineService().cleanupPlayer(playerId);
        this.plugin.getCookingService().cancelSession(playerId);
        this.plugin.getAchievementGuiService().clearState(playerId);
        this.plugin.getPlanetService().cleanupPlayer(playerId);
        this.plugin.getRegionService().cleanupPlayer(playerId);
        this.plugin.getMazeService().onPlayerQuitCleanup(playerId);
        this.plugin.getTitleMsgService().onPlayerQuit(playerId);
        if (this.plugin.getTalentGuiService() != null) {
            this.plugin.getTalentGuiService().clearPlayer(playerId);
        }
        this.plugin.getPlayerProgressService().saveAndEvict(playerId);
    }

    // 新手引導：首次加入時顯示引導提示
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoinTutorial(final PlayerJoinEvent event) {
        if (this.plugin.getTutorialChainService() != null) {
            // 延遲 40 ticks 讓玩家完全加入後再顯示
            this.plugin.getSafeScheduler().runGlobalDelayed(task ->
                    this.plugin.getTutorialChainService().onPlayerJoin(event.getPlayer()), 40L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(final PlayerDeathEvent event) {
        final UUID deadId = event.getEntity().getUniqueId();
        if (this.plugin.getPlanetService().hasActiveRuinChallenge(deadId)) {
            event.getEntity().sendMessage(this.plugin.getItemFactory().warning("✘ 你在遺跡挑戰中陣亡，核心陷入休眠…"));
            this.plugin.getPlanetService().cleanupPlayer(deadId);
        }
        if (this.plugin.getRegionService().isTraveling(deadId)) {
            this.plugin.getRegionService().cleanupPlayer(deadId);
            this.plugin.getPlanetService().cleanupPlayer(deadId);
        }
    }

    // ── 科技不死圖騰不應觸發復活效果 ──
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResurrect(final org.bukkit.event.entity.EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        for (final EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND}) {
            final ItemStack stack = player.getInventory().getItem(slot);
            if (stack != null && stack.getType() == Material.TOTEM_OF_UNDYING
                    && this.isTaggedTechMaterial(stack)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ── 防止豬靈撿起科技物品 + 迷宮世界撿取攔截到冒險者倉庫 ──
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityPickupItem(final org.bukkit.event.entity.EntityPickupItemEvent event) {
        // 豬靈：擋下科技金錠避免觸發以物易物
        if (event.getEntity() instanceof org.bukkit.entity.Piglin) {
            final ItemStack stack = event.getItem().getItemStack();
            if (this.isTaggedTechMaterial(stack)) {
                event.setCancelled(true);
            }
            return;
        }
        // 玩家在迷途星：攔截所有拾取，改入「冒險者拾取物」虛擬倉庫
        if (event.getEntity() instanceof Player player
                && this.plugin.getMazeService().isLabyrinthWorld(player.getWorld())) {
            final ItemStack stack = event.getItem().getItemStack();
            final boolean ok = this.plugin.getMazeService().depositToAdventurerStash(player, stack);
            event.setCancelled(true);
            if (ok) {
                event.getItem().remove();
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.4f, 1.3f);
            } else {
                // 倉庫已滿：每 5 秒提醒一次（用 cooldown stat 避免洗頻訊息較複雜，這邊直接送一次）
                player.sendActionBar(net.kyori.adventure.text.Component.text(
                        "冒險者拾取物已滿 (54)",
                        net.kyori.adventure.text.format.TextColor.color(0xE8A36A)));
            }
        }
    }

    /** 探索經驗用：累計移動距離（方塊數），每 100 格給一次 XP */
    private final java.util.Map<java.util.UUID, Double> explorationDistance = new java.util.concurrent.ConcurrentHashMap<>();

    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        this.refreshJetpackFlightState(event.getPlayer());
        // 區域進入偵測（不受裝備節流影響，踩到方塊邊界立即觸發）
        if (event.getFrom().getBlockX() != event.getTo().getBlockX()
                || event.getFrom().getBlockY() != event.getTo().getBlockY()
                || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            this.plugin.getRegionService().onPlayerMove(event.getPlayer(), event.getTo());
            // 迷宮中心抵達任務
            this.plugin.getMazeService().checkCenterReach(event.getPlayer());
            // Glade 邊界穿越偵測（進入迷宮時的震撼 title）
            this.plugin.getMazeService().checkGladeBoundaryCrossing(event.getPlayer());
            // 全域探索經驗：累計移動距離，每 100 格 +3 XP
            if (this.plugin.getSkillService() != null
                    && event.getPlayer().getGameMode() == GameMode.SURVIVAL) {
                final double dx = event.getTo().getX() - event.getFrom().getX();
                final double dz = event.getTo().getZ() - event.getFrom().getZ();
                final double dist = Math.sqrt(dx * dx + dz * dz);
                final double total = this.explorationDistance.merge(
                        event.getPlayer().getUniqueId(), dist, Double::sum);
                if (total >= 100.0) {
                    this.explorationDistance.put(event.getPlayer().getUniqueId(),
                            total - 100.0);
                    this.plugin.getSkillService().grantXp(event.getPlayer(), "exploration", 3L);
                }
            }
        }
    }

    // ── 撤離點跳躍觸發 ──
    @EventHandler(ignoreCancelled = true)
    public void onPlayerJump(final com.destroystokyo.paper.event.player.PlayerJumpEvent event) {
        this.plugin.getMazeService().checkExtractionTrigger(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleFlight(final PlayerToggleFlightEvent event) {
        final Player player = event.getPlayer();
        if (!this.hasJetpackEquipped(player) || !this.isManagedSurvivalFlight(player)) {
            return;
        }
        event.setCancelled(true);
        player.setFlying(false);

        if (!this.isCooldownReady(this.jetpackCooldowns, player.getUniqueId())) {
            player.sendActionBar(this.plugin.getItemFactory().warning("風暴噴射背包冷卻中。"));
            this.refreshJetpackFlightState(player);
            return;
        }

        final ItemStack chestplate = player.getInventory().getChestplate();
        final long jetEnergy = this.plugin.getItemFactory().getItemStoredEnergy(chestplate);
        if (jetEnergy < JETPACK_ENERGY_COST) {
            player.sendActionBar(this.plugin.getItemFactory().warning("風暴噴射背包電量不足，請對準儲能機器 蹲下 + 右鍵充電。"));
            this.refreshJetpackFlightState(player);
            return;
        }

        final Vector direction = player.getLocation().getDirection().normalize();
        final double forward = player.isSneaking() ? 0.42 : 1.05;
        final double upward = player.isSneaking() ? 1.1 : 0.78;
        final Vector burst = direction.multiply(forward);
        burst.setY(Math.max(upward, (direction.getY() * 0.35) + upward));

        player.setVelocity(player.getVelocity().multiply(0.15).add(burst));
        player.setFallDistance(0.0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 36, 0, false, true, true));
        this.markGracefulLanding(player, 3200L);
        this.startCooldown(this.jetpackCooldowns, player.getUniqueId(), 950L);

        final Location exhaust = player.getLocation().add(0.0, 0.6, 0.0);
        exhaust.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, exhaust, 18, 0.3, 0.35, 0.3, 0.01);
        exhaust.getWorld().spawnParticle(Particle.CLOUD, exhaust, 12, 0.22, 0.25, 0.22, 0.02);
        exhaust.getWorld().playSound(exhaust, Sound.ITEM_FIRECHARGE_USE, 0.75f, 1.2f);
        exhaust.getWorld().playSound(exhaust, Sound.ENTITY_BREEZE_SHOOT, 0.65f, 1.45f);
        this.plugin.getItemFactory().drainItemEnergy(chestplate, JETPACK_ENERGY_COST);
        final long jetRemaining = this.plugin.getItemFactory().getItemStoredEnergy(chestplate);
        final long jetMax = this.plugin.getItemFactory().maxItemEnergy(STORM_JETPACK);
        player.sendActionBar(this.plugin.getItemFactory().success("風暴噴射背包已點火。 ⚡ " + jetRemaining + "/" + jetMax));

        this.plugin.getSafeScheduler().runEntityDelayed(player, () -> this.refreshJetpackFlightState(player), 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobilityFallDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }
        final long safeUntil = this.mobilityGracePeriods.getOrDefault(player.getUniqueId(), 0L);
        if (safeUntil < System.currentTimeMillis()) {
            this.mobilityGracePeriods.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
        player.setFallDistance(0.0f);
    }

    // ── 聊天稱號 hover 資訊 ──────────────────────────────────

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChatTitleHover(final AsyncChatEvent event) {
        final Player player = event.getPlayer();
        final UUID uuid = player.getUniqueId();
        final var titleService = this.plugin.getTitleService();
        if (titleService == null) { return; }
        final String titleText = titleService.getPlayerTitle(uuid);
        if (titleText == null || titleText.isBlank()) { return; }

        final var progressService = this.plugin.getPlayerProgressService();
        final var registry = this.plugin.getTechRegistry();

        // 將 legacy §色碼 字串轉成 Adventure Component
        final net.kyori.adventure.text.Component titleComp =
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(titleText);

        // 組裝 hover 面板
        final net.kyori.adventure.text.Component hover = net.kyori.adventure.text.Component.text()
            .append(net.kyori.adventure.text.Component.text("══ 科技稱號 ══", net.kyori.adventure.text.format.TextColor.color(0xFFD054)))
            .append(net.kyori.adventure.text.Component.newline())
            .append(net.kyori.adventure.text.Component.text("🏷 ", net.kyori.adventure.text.format.TextColor.color(0xA8B2C1)))
            .append(titleComp)
            .append(net.kyori.adventure.text.Component.newline())
            .append(net.kyori.adventure.text.Component.text("📊 研究等級 Lv." + progressService.getTechLevel(uuid), net.kyori.adventure.text.format.TextColor.color(0x42A5F5)))
            .append(net.kyori.adventure.text.Component.newline())
            .append(net.kyori.adventure.text.Component.text("⚡ 可用研究點 " + progressService.getAvailableTechXp(uuid), net.kyori.adventure.text.format.TextColor.color(0xFFD740)))
            .append(net.kyori.adventure.text.Component.newline())
            .append(net.kyori.adventure.text.Component.text("🏆 成就 " + progressService.unlockedAchievementCount(uuid) + "/" + registry.allAchievements().size(), net.kyori.adventure.text.format.TextColor.color(0x66BB6A)))
            .append(net.kyori.adventure.text.Component.newline())
            .append(net.kyori.adventure.text.Component.text("🎖 稱號 " + titleService.getPlayerUnlockedTitleCount(uuid) + "/" + titleService.totalTitleCount(), net.kyori.adventure.text.format.TextColor.color(0xCE93D8)))
            .build();

        final net.kyori.adventure.text.Component titleWithHover =
            titleComp.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hover))
                     .append(net.kyori.adventure.text.Component.text(" "));

        // 包裝現有的 renderer，在前面加上帶 hover 的稱號
        final io.papermc.paper.chat.ChatRenderer original = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) ->
            titleWithHover.append(original.render(source, sourceDisplayName, message, viewer))
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBookSearchChat(final AsyncChatEvent event) {
        final String plainText = PlainTextComponentSerializer.plainText().serialize(event.message());
        // 傳送面板命名
        if (this.plugin.getMachineService().hasPendingTeleportRename(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            final Player player = event.getPlayer();
            this.plugin.getSafeScheduler().runEntity(player, () ->
                this.plugin.getMachineService().handleTeleportRenameChat(player, plainText)
            );
            return;
        }
        // 物品搜尋：以 ? 開頭的訊息 → 打開鐵砧搜尋 GUI（或帶關鍵字直接開圖鑑結果）
        if (this.plugin.getItemSearchService().isSearchQuery(plainText)) {
            event.setCancelled(true);
            final String query = this.plugin.getItemSearchService().extractQuery(plainText);
            final Player player = event.getPlayer();
            this.plugin.getSafeScheduler().runEntity(player, () ->
                this.plugin.getItemSearchService().openSearch(player, query)
            );
            return;
        }
        if (!this.plugin.getTechBookService().isAwaitingSearchInput(event.getPlayer().getUniqueId())) {
            // AI 幫手輸入
            if (this.plugin.getTechBookService().isAwaitingHelperInput(event.getPlayer().getUniqueId())) {
                event.setCancelled(true);
                this.plugin.getTechBookService().consumeHelperInput(event.getPlayer(), plainText);
            }
            return;
        }
        event.setCancelled(true);
        this.plugin.getTechBookService().consumeSearchInput(event.getPlayer(), plainText);
    }

    @EventHandler
    public void onChunkLoad(final ChunkLoadEvent event) {
        this.plugin.getPlanetService().handleChunkLoad(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(final ChunkUnloadEvent event) {
        this.plugin.getPlanetService().handleChunkUnload(event.getChunk());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGladeSafeZone(final CreatureSpawnEvent event) {
        // Glade 內禁止生敵對怪物（動物允許）
        if (!(event.getEntity() instanceof Monster)) {
            return;
        }
        if (!this.plugin.getMazeService().isInsideGlade(event.getLocation())) {
            return;
        }
        final CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM
                || reason == CreatureSpawnEvent.SpawnReason.COMMAND
                || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return; // 允許玩家/指令召喚
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlanetMobSpawn(final CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.REINFORCEMENTS) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        this.plugin.getPlanetService().tryEmpowerPlanetMob(living);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlanetMobDeath(final EntityDeathEvent event) {
        this.plugin.getPlanetService().handlePlanetEliteDeath(event.getEntity(), event.getEntity().getKiller(), event.getDrops());
        this.plugin.getPlanetService().onRuinChallengeMobDeath(event.getEntity());
        // 迷宮 Boss / 怪物擊殺
        if (this.plugin.getMazeService().isLabyrinthWorld(event.getEntity().getWorld())) {
            this.plugin.getMazeService().onBossDeath(event.getEntity(), event.getEntity().getKiller());
            if (event.getEntity().getKiller() != null) {
                this.plugin.getMazeService().onMobKill(event.getEntity().getKiller());
            }
        }
        // 新增：技能經驗 + 每日任務 + 排行榜 + 隨機事件
        final Player killer = event.getEntity().getKiller();
        if (killer != null) {
            if (this.plugin.getSkillService() != null) {
                this.plugin.getSkillService().grantXp(killer, "combat", 5L);
            }
            if (this.plugin.getDailyQuestService() != null) {
                this.plugin.getDailyQuestService().onMobKill(killer);
            }
            if (this.plugin.getLeaderboardService() != null) {
                this.plugin.getLeaderboardService().incrementKills(killer.getUniqueId(), 1L);
            }
            // 擊殺回魔：讓傳統 skill 行動能搭配施法循環
            if (this.plugin.getManaService() != null) {
                this.plugin.getManaService().restore(killer, 8.0);
            }
        }
    }

    // ── 星球世界限制：禁止設領地、設家、傳送 ──
    private static final Set<String> BLOCKED_PLANET_COMMANDS = Set.of(
            "res", "residence", "resadmin",
            "sethome", "huskhomes:sethome",
            "tpa", "tpaccept", "tpahere", "tpyes",
            "huskhomes:tpa", "huskhomes:tpaccept", "huskhomes:tpahere"
    );

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlanetCommandBlock(final PlayerCommandPreprocessEvent event) {
        if (!this.plugin.getPlanetService().isPlanetWorld(event.getPlayer().getWorld())) {
            return;
        }
        if (event.getPlayer().hasPermission("techproject.planet.bypass")) {
            return;
        }
        final String message = event.getMessage().toLowerCase();
        // /res create, /res set, /sethome, /home set, /tpa ...
        final String[] parts = message.split("\\s+");
        if (parts.length == 0) return;
        final String cmd = parts[0].substring(1); // 去掉前面的 /
        if (BLOCKED_PLANET_COMMANDS.contains(cmd)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(this.plugin.getItemFactory().warning("星球區域禁止使用此指令。"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(final PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        // ── Nexo 家具互動 → 開啟機器界面 ──
        final Entity clicked = event.getRightClicked();
        if (clicked instanceof org.bukkit.entity.Interaction || clicked instanceof org.bukkit.entity.ItemDisplay) {
            // ItemDisplay (techproject_display tag) 或 Interaction 實體 → 嘗試解析為機器方塊
            if (clicked instanceof org.bukkit.entity.ItemDisplay && !clicked.getScoreboardTags().contains("techproject_display")) {
                // 不是我們生成的 ItemDisplay，跳過
            } else if (clicked instanceof org.bukkit.entity.Interaction && !clicked.getScoreboardTags().contains("techproject_hitbox")) {
                // 不是我們生成的 Interaction hitbox，跳過
            } else {
                final Block block = clicked.getLocation().getBlock();
                final Block machineBlock = this.plugin.getMachineService().resolveManagedMachineBlock(block);
                if (machineBlock != null) {
                    event.setCancelled(true);
                    if (!this.plugin.getMachineService().canModifyMachine(event.getPlayer(), machineBlock, false)) {
                        return;
                    }
                    // 扳手拆卸：蹲下+右鍵
                    final ItemStack wrenchHand = event.getPlayer().getInventory().getItemInMainHand();
                    if (event.getPlayer().isSneaking() && this.plugin.getItemFactory().hasWrenchTag(wrenchHand)) {
                        final UUID wrenchUid = event.getPlayer().getUniqueId();
                        final long now = System.currentTimeMillis();
                        final Long lastWrench = this.wrenchCooldowns.get(wrenchUid);
                        if (lastWrench != null && now - lastWrench < 500L) {
                            return;
                        }
                        this.wrenchCooldowns.put(wrenchUid, now);
                        this.handleWrenchDismantle(event.getPlayer(), machineBlock);
                        return;
                    }
                    this.plugin.getMachineService().handleManagedMachineInteract(event.getPlayer(), machineBlock, block);
                    return;
                }
            }
        }
        final Player player = event.getPlayer();
        final ItemStack hand = player.getInventory().getItemInMainHand();
        // ── 科技物品與村民/遊商/豬靈互動防護 ──
        if (hand.getType() != Material.AIR && this.isTaggedTechMaterial(hand)) {
            final Entity target = event.getRightClicked();
            if (target instanceof org.bukkit.entity.Villager
                    || target instanceof org.bukkit.entity.WanderingTrader
                    || target instanceof org.bukkit.entity.Piglin) {
                event.setCancelled(true);
                return;
            }
        }
        if (hand.getType() == Material.AIR || !this.plugin.getItemFactory().isChickenNet(hand)) {
            return;
        }
        if (!(event.getRightClicked() instanceof Chicken chicken)) {
            player.sendMessage(this.plugin.getItemFactory().warning("雞網只能對雞使用！"));
            return;
        }
        event.setCancelled(true);
        // 消耗一個雞網
        hand.setAmount(hand.getAmount() - 1);
        // 移除雞實體
        chicken.remove();
        // 產生隨機 DNA 口袋雞
        final com.rui.techproject.service.ChickenGeneticsService genetics = new com.rui.techproject.service.ChickenGeneticsService();
        final String dna = genetics.generateWildDna();
        final String resourceName = genetics.resourceNameZh(dna);
        final ItemStack pocketChicken = this.plugin.getItemFactory().buildPocketChicken(dna, false, resourceName);
        // 給予玩家
        final Map<Integer, ItemStack> overflow = player.getInventory().addItem(pocketChicken);
        for (final ItemStack drop : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        // 音效 & 粒子
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CHICKEN_HURT, 0.6f, 1.3f);
        player.getWorld().spawnParticle(Particle.CLOUD, chicken.getLocation().add(0, 0.5, 0), 8, 0.3, 0.3, 0.3, 0.02);
        player.sendMessage(this.plugin.getItemFactory().success("成功捕捉一隻口袋雞！放入基因定序器分析 DNA。"));
        this.plugin.getPlayerProgressService().incrementStat(player.getUniqueId(), "chickens_captured", 1);
        this.plugin.getPlayerProgressService().unlockItem(player.getUniqueId(), "pocket_chicken");
        this.plugin.getPlayerProgressService().unlockByRequirement(player.getUniqueId(), "chicken_net");
    }

    private void handleGeoScannerUse(final Player player) {
        final com.rui.techproject.service.GeoResourceService geo = this.plugin.getGeoResourceService();
        if (geo == null) return;
        final org.bukkit.Chunk chunk = player.getLocation().getChunk();
        final com.rui.techproject.service.GeoResourceService.GeoSnapshot snap = geo.peek(chunk);
        geo.markScanned(chunk);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.35f, 1.6f);
        final int[] rgb = snap.type().rgb();
        final org.bukkit.Particle.DustOptions dust = new org.bukkit.Particle.DustOptions(
                org.bukkit.Color.fromRGB(rgb[0], rgb[1], rgb[2]), 1.6f);
        // 在 chunk 四角噴出彩色粒子柱作為視覺提示
        final int cx = chunk.getX() << 4;
        final int cz = chunk.getZ() << 4;
        final double y = player.getLocation().getY();
        for (int i = 0; i <= 16; i += 4) {
            player.spawnParticle(org.bukkit.Particle.DUST, cx + 0.5 + i, y + 1.2, cz + 0.5, 2, 0, 0.25, 0, dust);
            player.spawnParticle(org.bukkit.Particle.DUST, cx + 0.5 + i, y + 1.2, cz + 15.5, 2, 0, 0.25, 0, dust);
            player.spawnParticle(org.bukkit.Particle.DUST, cx + 0.5, y + 1.2, cz + 0.5 + i, 2, 0, 0.25, 0, dust);
            player.spawnParticle(org.bukkit.Particle.DUST, cx + 15.5, y + 1.2, cz + 0.5 + i, 2, 0, 0.25, 0, dust);
        }
        // 訊息
        final net.kyori.adventure.text.format.TextColor typeColor = snap.isDepleted()
                ? net.kyori.adventure.text.format.NamedTextColor.GRAY
                : net.kyori.adventure.text.format.TextColor.color(rgb[0], rgb[1], rgb[2]);
        player.sendMessage(net.kyori.adventure.text.Component.text("◆ 地質掃描 ", net.kyori.adventure.text.format.NamedTextColor.AQUA)
                .append(net.kyori.adventure.text.Component.text("區塊 [" + chunk.getX() + ", " + chunk.getZ() + "]",
                        net.kyori.adventure.text.format.NamedTextColor.GRAY)));
        player.sendMessage(net.kyori.adventure.text.Component.text("  類型：", net.kyori.adventure.text.format.NamedTextColor.GRAY)
                .append(net.kyori.adventure.text.Component.text(snap.type().displayName(), typeColor)));
        if (!snap.isDepleted()) {
            player.sendMessage(net.kyori.adventure.text.Component.text("  剩餘：" + snap.remaining() + " / " + snap.initial(),
                    net.kyori.adventure.text.format.NamedTextColor.WHITE));
            player.sendMessage(net.kyori.adventure.text.Component.text("  → 部署 GEO 抽取機以開始生產",
                    net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
        } else {
            player.sendMessage(net.kyori.adventure.text.Component.text("  此地層已枯竭，請往其他區塊勘探",
                    net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
        }
        this.plugin.getPlayerProgressService().incrementStat(player.getUniqueId(), "geo_scanned", 1);
        this.plugin.getPlayerProgressService().unlockByRequirement(player.getUniqueId(), "geo_scanner");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(final PlayerInteractEvent event) {
        // ── 冒險者拾取物倉庫桶：右鍵開啟專屬虛擬 GUI（唯讀） ──
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.BARREL
                && this.plugin.getMazeService().isAdventurerStashBlock(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
            this.plugin.getMazeService().openAdventurerStash(event.getPlayer());
            return;
        }
        // ── 多方塊結構合成：右鍵發射器 → 偵測結構並合成 ──
        // 蹲下且手持方塊時跳過（允許 shift+右鍵放置機器）
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && event.getClickedBlock().getType() == Material.DISPENSER
                && !event.getPlayer().isSneaking()
                && this.plugin.getMultiblockCraftingService() != null
                && this.plugin.getMultiblockCraftingService().tryCraft(event.getPlayer(), event.getClickedBlock())) {
            event.setCancelled(true);
            return;
        }
        // ── 地質掃描儀：右鍵 → 勘探腳下區塊 ──
        if ((event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
                && event.getItem() != null
                && this.plugin.getItemFactory().isGeoScanner(event.getItem())) {
            event.setCancelled(true);
            this.handleGeoScannerUse(event.getPlayer());
            return;
        }
        // ── 古代祭壇：右鍵 Lodestone 核心 → 嘗試啟動儀式 ──
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && this.plugin.getAltarService() != null
                && this.plugin.getAltarService().isAltarCore(event.getClickedBlock())) {
            if (this.plugin.getAltarService().onAltarInteract(event.getPlayer(), event.getClickedBlock())) {
                event.setCancelled(true);
                return;
            }
        }
        // ── 區域選取工具 ──
        if (event.getItem() != null && event.getItem().hasItemMeta()
                && event.getItem().getItemMeta().getPersistentDataContainer().has(
                        new org.bukkit.NamespacedKey(this.plugin, "region_wand"), org.bukkit.persistence.PersistentDataType.BYTE)) {
            if (event.getClickedBlock() != null) {
                event.setCancelled(true);
                final var rs = this.plugin.getRegionService();
                if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    rs.setPos1(event.getPlayer(), event.getClickedBlock().getLocation());
                } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    rs.setPos2(event.getPlayer(), event.getClickedBlock().getLocation());
                }
            }
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            final ItemStack leftStack = event.getItem();
            if (leftStack != null && this.plugin.getItemFactory().hasTechBookTag(leftStack)) {
                final String cmd = this.plugin.getConfig().getString("techbook-left-click-command", "");
                if (cmd != null && !cmd.isBlank()) {
                    event.setCancelled(true);
                    final Player clicker = event.getPlayer();
                    this.plugin.getSafeScheduler().runEntity(clicker, () ->
                            clicker.performCommand(cmd.replace("{player}", clicker.getName()).trim()));
                }
            }
            // ── 物流扳手：左鍵機器某一面 → 設定輸入方向 ──
            if (event.getAction() == Action.LEFT_CLICK_BLOCK && leftStack != null
                    && this.plugin.getItemFactory().hasLogisticsWrenchTag(leftStack)
                    && event.getClickedBlock() != null) {
                final Block logBlock = this.plugin.getMachineService().resolveManagedMachineBlock(event.getClickedBlock());
                if (logBlock != null) {
                    event.setCancelled(true);
                    if (!this.plugin.getMachineService().canModifyMachine(event.getPlayer(), logBlock, false)) {
                        return;
                    }
                    this.plugin.getMachineService().handleLogisticsWrench(event.getPlayer(), logBlock, event.getBlockFace(), false);
                    return;
                }
            }
            // ── 科技扳手：左鍵機器 → 切換開/關機 ──
            if (event.getAction() == Action.LEFT_CLICK_BLOCK && leftStack != null
                    && this.plugin.getItemFactory().hasWrenchTag(leftStack)
                    && event.getClickedBlock() != null) {
                final Block wrenchBlock = this.plugin.getMachineService().resolveManagedMachineBlock(event.getClickedBlock());
                if (wrenchBlock != null) {
                    event.setCancelled(true);
                    if (!this.plugin.getMachineService().canModifyMachine(event.getPlayer(), wrenchBlock, false)) {
                        return;
                    }
                    final PlacedMachine wrenchPm = this.plugin.getMachineService().placedMachineAt(wrenchBlock);
                    if (wrenchPm != null) {
                        final boolean newEnabled = !wrenchPm.enabled();
                        this.plugin.getMachineService().setMachineEnabled(wrenchBlock, newEnabled);
                        event.getPlayer().sendActionBar(newEnabled
                                ? this.plugin.getItemFactory().success("⚙ 機器已啟動")
                                : this.plugin.getItemFactory().warning("⚙ 機器已暫停"));
                        event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_LEVER_CLICK, 0.6f, newEnabled ? 1.2f : 0.8f);
                        if (this.plugin.getTutorialChainService() != null) {
                            this.plugin.getTutorialChainService().onWrenchToggle(event.getPlayer());
                        }
                    }
                    return;
                }
            }
            // ── BARRIER Nexo 機器：左鍵 → 拆除機器（BARRIER 無法正常挖掘） ──
            if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getClickedBlock() != null
                    && event.getClickedBlock().getType() == Material.BARRIER) {
                final Block barrierMachine = this.plugin.getMachineService().resolveManagedMachineBlock(event.getClickedBlock());
                if (barrierMachine != null) {
                    event.setCancelled(true);
                    if (!this.plugin.getMachineService().canModifyMachine(event.getPlayer(), barrierMachine, true)) {
                        return;
                    }
                    this.handleWrenchDismantle(event.getPlayer(), barrierMachine);
                    return;
                }
            }
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // ── 通用防護：科技物品使用可互動原版材質時封鎖原版右鍵效果 ──
        {
            final ItemStack heldItem = event.getItem();
            if (heldItem != null && VANILLA_INTERACT_MATERIALS.contains(heldItem.getType())
                    && this.isTaggedTechMaterial(heldItem)) {
                event.setUseItemInHand(Result.DENY);
            }
        }
        // 早期攔截：右鍵有原版容器 GUI 的機器方塊（BEACON、ANVIL 等）時立即封鎖，避免原版 GUI 被打開
        final boolean earlyMachineDeny = event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && this.hasNativeContainerGui(event.getClickedBlock().getType())
                && this.plugin.getMachineService().resolveManagedMachineBlock(event.getClickedBlock()) != null;
        if (earlyMachineDeny) {
            event.setUseInteractedBlock(Result.DENY);
        }
        // 傳送面板：任何手持物（含空手）都應能互動，不受其他插件 DENY 影響
        final boolean earlyTeleportPad = event.getAction() == Action.RIGHT_CLICK_BLOCK
                && event.getClickedBlock() != null
                && this.plugin.getMachineService().isTeleportPadBlock(event.getClickedBlock());
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            // 副手持科技物品時阻止原版行為（末影珍珠投擲、終界之眼飛出、玻璃瓶裝水等）
            final ItemStack offHandStack = event.getItem();
            if (offHandStack != null && VANILLA_INTERACT_MATERIALS.contains(offHandStack.getType())
                    && this.isTaggedTechMaterial(offHandStack)) {
                event.setUseItemInHand(Result.DENY);
                event.setCancelled(true);
                return;
            }
            final String offHandId = offHandStack == null ? null : this.plugin.getItemFactory().getTechItemId(offHandStack);
            if (offHandId != null && ARTIFACT_IDS.contains(offHandId.toLowerCase())) {
                event.setUseItemInHand(Result.DENY);
                event.setCancelled(true);
                return;
            }
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null
                    && this.plugin.getMachineService().resolveManagedMachineBlock(event.getClickedBlock()) != null) {
                event.setUseItemInHand(Result.DENY);
                event.setCancelled(true);
            }
            return;
        }
        ItemStack stack = event.getItem();
        if (stack == null && event.getHand() != null) {
            stack = event.getPlayer().getInventory().getItem(event.getHand());
        }
        if (this.plugin.getItemFactory().hasFullUnlockBookTag(stack)) {
            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
            event.setCancelled(true);

            final Player player = event.getPlayer();
            final int unlocked = this.plugin.getPlayerProgressService().unlockAllResearch(
                    player.getUniqueId(),
                    this.plugin.getTechAddonService().allInteractions().stream().map(definition -> definition.id()).toList()
            );
            if (unlocked <= 0) {
                player.sendMessage(this.plugin.getItemFactory().warning("你目前已經是全解鎖狀態。"));
                return;
            }

            if (stack.getAmount() <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                stack.setAmount(stack.getAmount() - 1);
                player.getInventory().setItemInMainHand(stack);
            }
            if (!this.hasTechBook(player)) {
                player.getInventory().addItem(this.plugin.getItemFactory().buildTechBook());
            }
            this.plugin.getPlayerProgressService().save(player.getUniqueId());
            player.sendMessage(this.plugin.getItemFactory().success("已使用全解鎖書，全部科技研究已解鎖。"));
            this.plugin.getSafeScheduler().runEntityDelayed(player, () -> this.plugin.getTechBookService().openDefaultBook(player), 1L);
            return;
        }
        if (this.plugin.getItemFactory().hasTechBookTag(stack)) {
            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
            event.setCancelled(true);
            this.plugin.getSafeScheduler().runEntityDelayed(event.getPlayer(), () -> this.plugin.getTechBookService().openDefaultBook(event.getPlayer()), 1L);
            return;
        }
        if (this.tryUseCustomFood(event)) {
            return;
        }
        if (this.tryUseArtifact(event)) {
            return;
        }
        // 便攜工具優先處理：不受其他插件 DENY 影響
        if (this.handlePortableCrafter(event) || this.handlePortableDustbin(event) || this.handleTechBackpack(event)) {
            return;
        }
        // 機動工具（脈衝推進器／向量抓鉤）優先處理：右鍵空氣時 useInteractedBlock 預設為 DENY，
        // 若等到下方 DENY 檢查會被直接 return，導致空氣右鍵永遠無法觸發。
        if (this.tryUseVectorGrapple(event) || this.tryUsePulseThruster(event)) {
            return;
        }
        if (event.useInteractedBlock() == Result.DENY || event.useItemInHand() == Result.DENY) {
            // 若 DENY 是我們自己設的（早期烽火台攔截）或傳送面板，仍繼續處理機器互動
            // 蹲下右鍵方塊時法器充電也需放行（科技物品原版材質保護會設 DENY）
            final boolean artifactCharging = event.getPlayer().isSneaking()
                    && event.getAction() == Action.RIGHT_CLICK_BLOCK
                    && stack != null
                    && this.plugin.getItemFactory().getTechItemId(stack) != null
                    && ARTIFACT_IDS.contains(this.plugin.getItemFactory().getTechItemId(stack).toLowerCase());
            if (!earlyMachineDeny && !earlyTeleportPad && !artifactCharging) {
                return;
            }
        }
        if (event.getClickedBlock() != null && this.plugin.getPlanetService().handlePlanetInteract(event.getPlayer(), event.getClickedBlock(), stack)) {
            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
            event.setCancelled(true);
            return;
        }
        if (this.tryPlantTechCrop(event) || this.tryUseGoldPan(event) || this.tryUseHydroSpade(event) || this.tryToggleMagnet(event) || this.tryUseMobilityTool(event)
                || this.handleSeismicAxe(event)) {
            return;
        }
        // 互動烹調：右鍵營火/煙燻/高爐並手持食物
        if (event.getClickedBlock() != null && event.getHand() == EquipmentSlot.HAND && !earlyMachineDeny
                && this.plugin.getCookingService().isCookingStation(event.getClickedBlock().getType())) {
            if (this.plugin.getCookingService().tryStartCooking(event.getPlayer(), event.getClickedBlock())) {
                event.setUseInteractedBlock(Result.DENY);
                event.setUseItemInHand(Result.DENY);
                event.setCancelled(true);
                return;
            }
        }
        if (event.getHand() == EquipmentSlot.HAND && this.tryShowAdvancedWorkbenchHint(event.getPlayer(), event.getClickedBlock())) {
            return;
        }
        final Block machineBlock = event.getClickedBlock() == null ? null : this.plugin.getMachineService().resolveManagedMachineBlock(event.getClickedBlock());
        if (machineBlock != null) {
            // ── 物流扳手：右鍵機器某一面 → 設定輸出方向 ──
            if (this.plugin.getItemFactory().hasLogisticsWrenchTag(stack)) {
                event.setUseInteractedBlock(Result.DENY);
                event.setUseItemInHand(Result.DENY);
                event.setCancelled(true);
                if (!this.plugin.getMachineService().canModifyMachine(event.getPlayer(), machineBlock, false)) {
                    return;
                }
                this.plugin.getMachineService().handleLogisticsWrench(event.getPlayer(), machineBlock, event.getBlockFace(), true);
                return;
            }
            // ── 科技扳手：右鍵（不蹲）→ 開機 ──
            if (!event.getPlayer().isSneaking() && this.plugin.getItemFactory().hasWrenchTag(stack)) {
                event.setUseInteractedBlock(Result.DENY);
                event.setUseItemInHand(Result.DENY);
                event.setCancelled(true);
                if (!this.plugin.getMachineService().canModifyMachine(event.getPlayer(), machineBlock, false)) {
                    return;
                }
                this.plugin.getMachineService().setMachineEnabled(machineBlock, true);
                event.getPlayer().sendActionBar(this.plugin.getItemFactory().success("⚙ 機器已啟動"));
                event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_LEVER_CLICK, 0.6f, 1.4f);
                return;
            }
            // ── 扳手拆卸：蹲下+右鍵 ──
            if (event.getPlayer().isSneaking() && this.plugin.getItemFactory().hasWrenchTag(stack)) {
                event.setUseInteractedBlock(Result.DENY);
                event.setUseItemInHand(Result.DENY);
                event.setCancelled(true);
                final UUID wrenchUid = event.getPlayer().getUniqueId();
                final long now = System.currentTimeMillis();
                final Long lastWrench = this.wrenchCooldowns.get(wrenchUid);
                if (lastWrench != null && now - lastWrench < 500L) {
                    return;
                }
                this.wrenchCooldowns.put(wrenchUid, now);
                if (!this.plugin.getMachineService().canModifyMachine(event.getPlayer(), machineBlock, true)) {
                    return;
                }
                this.handleWrenchDismantle(event.getPlayer(), machineBlock);
                return;
            }
            // ── 蹲下+手持可放置方塊：允許原版放置（方便在機器旁放方塊 / 放置新機器）──
            // 採礦鑽機 / 傳送面板例外：蹲下右鍵一律交給 handleManagedMachineInteract
            if (event.getPlayer().isSneaking() && stack != null && stack.getType().isBlock()
                    && !this.plugin.getMachineService().isQuarryBlock(machineBlock)
                    && !this.plugin.getMachineService().isTeleportPadBlock(machineBlock)) {
                // 若早期攔截已將 useInteractedBlock 設為 DENY（防開原版容器 GUI），
                // 須明確放行 useItemInHand，否則部分伺服器實作會連方塊放置一併阻擋
                if (earlyMachineDeny) {
                    event.setUseItemInHand(Result.ALLOW);
                }
                return;
            }
            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
            event.setCancelled(true);
            if (!this.plugin.getMachineService().canModifyMachine(event.getPlayer(), machineBlock, false)) {
                return;
            }
            this.plugin.getMachineService().handleManagedMachineInteract(event.getPlayer(), machineBlock, event.getClickedBlock());
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockRedstone(final BlockRedstoneEvent event) {
        if (this.isProtectedTechBlock(event.getBlock())) {
            // 安裝紅石集成電路的機器允許紅石訊號通過
            if (this.plugin.getMachineService().hasRedstoneControlModule(event.getBlock())) {
                return;
            }
            event.setNewCurrent(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(final BlockPistonExtendEvent event) {
        if (this.isProtectedTechBlock(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        final BlockFace dir = event.getDirection();
        for (final Block pushed : event.getBlocks()) {
            if (this.isProtectedTechBlock(pushed)) {
                event.setCancelled(true);
                return;
            }
            // 也檢查推送目的地方塊，防止普通方塊被推入科技方塊位置
            if (this.isProtectedTechBlock(pushed.getRelative(dir))) {
                event.setCancelled(true);
                return;
            }
        }
        // 活塞臂伸出位置
        if (this.isProtectedTechBlock(event.getBlock().getRelative(dir))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(final BlockPistonRetractEvent event) {
        if (this.isProtectedTechBlock(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        final BlockFace dir = event.getDirection();
        for (final Block pulled : event.getBlocks()) {
            if (this.isProtectedTechBlock(pulled)) {
                event.setCancelled(true);
                return;
            }
            // 檢查拉動目的地，防止方塊被拉入科技方塊位置
            if (this.isProtectedTechBlock(pulled.getRelative(dir))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(final EntityChangeBlockEvent event) {
        // 阻止安德、末影龍、羊（吃草）等生物把科技方塊變成別的方塊
        if (this.isProtectedTechBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(final BlockPhysicsEvent event) {
        if (this.plugin.getMachineService().isManagedMachine(event.getBlock())
                || this.plugin.getPlacedTechBlockService().isTrackedBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFromTo(final BlockFromToEvent event) {
        if (this.isProtectedTechBlock(event.getToBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(final LeavesDecayEvent event) {
        if (this.isProtectedTechBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onStructureGrow(final StructureGrowEvent event) {
        // 阻止科技果樹苗被原版 random tick 長成一般大樹
        if (this.plugin.getPlacedTechBlockService().isTrackedBlock(event.getLocation().getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockGrow(final BlockGrowEvent event) {
        final Block block = event.getBlock();
        if (!this.plugin.getTechCropService().isTrackedCrop(block)) {
            return;
        }
        // 當原版 random tick 讓追蹤作物達到最大成長時，取消原版成長並轉換為成熟頭顱
        if (event.getNewState().getBlockData() instanceof org.bukkit.block.data.Ageable ageable
                && ageable.getAge() >= ageable.getMaximumAge()) {
            event.setCancelled(true);
            this.plugin.getTechCropService().convertToMatureHead(block);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(final InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof Container container
                && this.isProtectedTechBlock(container.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (event.getDestination().getHolder() instanceof Container container
                && this.isProtectedTechBlock(container.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispense(final BlockDispenseEvent event) {
        if (this.isProtectedTechBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        // ── 冒險者拾取物倉庫：唯讀，阻止任何操作 ──
        if (event.getView().getTopInventory().getHolder()
                instanceof com.rui.techproject.service.AdventurerStashHolder) {
            event.setCancelled(true);
            return;
        }
        // ── 新系統 GUI：技能 / 任務 / 排行榜（一律阻止拿取，只處理按鈕點擊） ──
        final Object topHolder = event.getView().getTopInventory().getHolder();
        if (topHolder instanceof com.rui.techproject.service.SkillService.SkillMenuHolder
                || topHolder instanceof com.rui.techproject.service.SkillService.SkillDetailHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player sp
                    && event.getRawSlot() >= 0
                    && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                final boolean right = event.getClick().isRightClick();
                this.plugin.getSkillService().handleClick(sp, event.getRawSlot(), topHolder, right);
            }
            return;
        }
        // ── 天賦樹 GUI（單樹可拖動大畫布） ──
        if (topHolder instanceof com.rui.techproject.service.talent.TalentGuiService.TalentTreeHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player tp
                    && event.getRawSlot() >= 0
                    && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                final boolean right = event.getClick().isRightClick();
                final boolean shift = event.getClick().isShiftClick();
                this.plugin.getTalentGuiService().handleClick(tp, event.getRawSlot(), topHolder,
                        right, shift);
            }
            return;
        }
        if (topHolder instanceof com.rui.techproject.service.DailyQuestService.QuestMenuHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player qp
                    && event.getRawSlot() >= 0
                    && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                this.plugin.getDailyQuestService().handleClick(qp, event.getRawSlot());
            }
            return;
        }
        if (topHolder instanceof com.rui.techproject.service.LeaderboardService.LeaderboardHolder lbHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player lp
                    && event.getRawSlot() >= 0
                    && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                this.plugin.getLeaderboardService().handleClick(lp, event.getRawSlot(), lbHolder.category);
            }
            return;
        }
        // ── 異界傳送門 GUI：取消任何移動，只處理按鈕點擊 ──
        if (event.getView().getTopInventory().getHolder()
                instanceof com.rui.techproject.service.OtherworldPortalHolder) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player otherworldPlayer
                    && event.getRawSlot() >= 0
                    && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                this.plugin.getPlanetService().handleOtherworldPortalMenuClick(otherworldPlayer, event.getRawSlot());
            }
            return;
        }
        if (this.handleProtectedWorkbenchResultClick(event)) {
            return;
        }
        if (this.handleAdvancedWorkbenchClick(event)) {
            return;
        }
        // ── 村民/遊商交易介面：阻止科技物品被放入交易格 ──
        if (event.getView().getTopInventory().getType() == InventoryType.MERCHANT
                && event.getWhoClicked() instanceof Player) {
            final ItemStack moving;
            if (event.isShiftClick()) {
                moving = event.getCurrentItem();
            } else if (event.getRawSlot() < event.getView().getTopInventory().getSize()) {
                moving = event.getCursor();
            } else {
                moving = null;
            }
            if (moving != null && this.isTaggedTechMaterial(moving)) {
                event.setCancelled(true);
                return;
            }
        }
        // 鐵砧搜尋 GUI
        if (event.getWhoClicked() instanceof Player player
                && this.plugin.getItemSearchService().isAnvilSearchOpen(player.getUniqueId())) {
            event.setCancelled(true);
            this.plugin.getItemSearchService().handleAnvilClick(player, event.getRawSlot());
            return;
        }
        final boolean bookViewOpen = this.plugin.getTechBookService().isBookInventory(event.getView().getTopInventory());
        final String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (!bookViewOpen && !this.plugin.getTechBookService().isBookView(title)) {
            if (this.plugin.getAchievementGuiService().isAchievementGui(title)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    this.plugin.getAchievementGuiService().handleClick(player, event.getRawSlot());
                }
                return;
            }
            if (this.plugin.getMachineService().isMachineView(title)) {
                this.plugin.getMachineService().handleMachineInventoryClick(event);
                return;
            }
            if (this.plugin.getPlanetService().isPlanetaryGateView(title)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    this.plugin.getPlanetService().handlePlanetaryGateMenuClick(player, event.getRawSlot());
                }
            }
            if (this.plugin.getMachineService().isTeleportPadView(title)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    this.plugin.getMachineService().handleTeleportPadMenuClick(player, event.getRawSlot());
                }
            }
            // ── 科技背包：禁止放入科技背包或界伏盒（防 NBT 遞迴爆炸） ──
            if (title.equals("科技背包") && event.getWhoClicked() instanceof Player backpackPlayer) {
                // DOUBLE_CLICK（收集至游標）可能從頂部欄位收集禁止物品，直接攔截
                if (event.getClick() == ClickType.DOUBLE_CLICK) {
                    final ItemStack cursor = event.getCursor();
                    if (cursor != null && this.isBackpackForbiddenItem(cursor)) {
                        event.setCancelled(true);
                        backpackPlayer.sendActionBar(this.plugin.getItemFactory().warning("此物品不可放入背包。"));
                        return;
                    }
                }
                final int topSize = event.getView().getTopInventory().getSize();
                ItemStack incoming = null;
                if (event.isShiftClick() && event.getRawSlot() >= topSize) {
                    incoming = event.getCurrentItem();
                } else if (event.getRawSlot() < topSize) {
                    incoming = event.getCursor();
                }
                if (event.getClick() == ClickType.NUMBER_KEY && event.getRawSlot() < topSize) {
                    final ItemStack hotbar = backpackPlayer.getInventory().getItem(event.getHotbarButton());
                    if (hotbar != null && this.isBackpackForbiddenItem(hotbar)) {
                        incoming = hotbar;
                    }
                }
                if (event.getClick() == ClickType.SWAP_OFFHAND && event.getRawSlot() < topSize) {
                    final ItemStack offhand = backpackPlayer.getInventory().getItemInOffHand();
                    if (this.isBackpackForbiddenItem(offhand)) {
                        incoming = offhand;
                    }
                }
                if (incoming != null && this.isBackpackForbiddenItem(incoming)) {
                    event.setCancelled(true);
                    backpackPlayer.sendActionBar(this.plugin.getItemFactory().warning("此物品不可放入背包。"));
                    return;
                }
            }
            return;
        }
        event.setCancelled(true);
        final ItemStack current = event.getCurrentItem();
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            final String shareAction = this.plugin.getItemFactory().getGuiAction(current);
            this.plugin.getTechBookService().shareBookAction(player, shareAction, current);
            return;
        }
        if (event.isRightClick() && this.plugin.getTechBookService().tryGrantPreviewItem(player, current)) {
            return;
        }
        final String action = this.plugin.getItemFactory().getGuiAction(current);
        this.plugin.getTechBookService().handleAction(player, action);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder()
                instanceof com.rui.techproject.service.AdventurerStashHolder) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTopInventory().getHolder()
                instanceof com.rui.techproject.service.OtherworldPortalHolder) {
            event.setCancelled(true);
            return;
        }
        final String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        final boolean bookViewOpen = this.plugin.getTechBookService().isBookInventory(event.getView().getTopInventory());
        if (this.plugin.getPlanetService().isPlanetaryGateView(title)) {
            event.setCancelled(true);
            return;
        }
        if (this.plugin.getMachineService().isTeleportPadView(title)) {
            event.setCancelled(true);
            return;
        }
        if (this.plugin.getAchievementGuiService().isAchievementGui(title)) {
            event.setCancelled(true);
            return;
        }
        if (bookViewOpen || this.plugin.getTechBookService().isBookView(title)) {
            for (final int rawSlot : event.getRawSlots()) {
                if (rawSlot < event.getView().getTopInventory().getSize()) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        // ── 科技背包：禁止拖曳放入科技背包或界伏盒 ──
        if (title.equals("科技背包") && event.getOldCursor() != null && this.isBackpackForbiddenItem(event.getOldCursor())) {
            final int topSize = event.getView().getTopInventory().getSize();
            for (final int rawSlot : event.getRawSlots()) {
                if (rawSlot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
        if (!this.plugin.getMachineService().isMachineView(title)) {
            return;
        }
        this.plugin.getMachineService().handleMachineInventoryDrag(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(final PrepareItemCraftEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory craftingInventory)) {
            return;
        }
        final var match = craftingInventory.getMatrix().length >= 9
                ? this.plugin.getBlueprintService().matchCraftingMatrix(craftingInventory.getMatrix())
                : null;
        if (match == null) {
            if (this.containsTaggedTechMaterial(craftingInventory.getMatrix())) {
                final var misused = this.plugin.getBlueprintService().detectMachineItemMisuse(craftingInventory.getMatrix());
                if (!misused.isEmpty()) {
                    craftingInventory.setResult(this.machineItemMisuseResult(misused));
                } else {
                    craftingInventory.setResult(this.plugin.getBlueprintService().isAdvancedWorkbench(craftingInventory.getLocation())
                            ? this.invalidTechProcessingResult()
                            : this.isolatedTechMaterialResult());
                }
            }
            return;
        }
        if (!this.plugin.getBlueprintService().isAdvancedWorkbench(craftingInventory.getLocation())) {
            craftingInventory.setResult(this.invalidAdvancedWorkbenchResult(match.outputDisplayName()));
            return;
        }
        final Player viewer = event.getViewers().stream()
                .filter(Player.class::isInstance)
                .map(Player.class::cast)
                .findFirst()
                .orElse(null);
        if (match.isItemBlueprint()) {
            // 物品藍圖（例如淘金盤）— 檢查物品解鎖
            if (viewer != null
                    && !viewer.hasPermission("techproject.admin")
                    && !this.plugin.getPlayerProgressService().hasItemUnlocked(viewer.getUniqueId(), match.item().id())) {
                craftingInventory.setResult(this.lockedMachineResult(match.item().displayName()));
                return;
            }
            craftingInventory.setResult(this.plugin.getItemFactory().buildTechItem(match.item()));
        } else {
            // 機器藍圖
            if (viewer != null
                    && !viewer.hasPermission("techproject.admin")
                    && !this.plugin.getPlayerProgressService().hasMachineUnlocked(viewer.getUniqueId(), match.machine().id())) {
                craftingInventory.setResult(this.lockedMachineResult(match.machine().displayName()));
                return;
            }
            craftingInventory.setResult(this.plugin.getItemFactory().buildMachineItem(match.machine()));
        }
    }

    /** 禁止科技機器物品放入原版熔爐/高爐燒製（避免產出錯誤物品）。一般科技材料（粉塵、礦砂等）允許正常燒煉。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceSmelt(final FurnaceSmeltEvent event) {
        if (this.plugin.getItemFactory().getMachineId(event.getSource()) != null) {
            event.setCancelled(true);
            event.setResult(new ItemStack(Material.AIR));
        }
    }

    /** 禁止科技物品被當作熔爐燃料消耗。 */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFurnaceBurn(final FurnaceBurnEvent event) {
        if (this.plugin.getItemFactory().getTechItemId(event.getFuel()) != null
                || this.plugin.getItemFactory().getMachineId(event.getFuel()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            this.plugin.getTechBookService().clearBookView(player.getUniqueId());
            this.plugin.getAchievementGuiService().clearState(player.getUniqueId());
            this.plugin.getItemSearchService().clearState(player.getUniqueId());
        }
        final String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        // 科技背包：關閉時儲存內容到物品 PDC
        if (title.equals("科技背包") && event.getPlayer() instanceof Player player && this.hasOpenBackpack(player.getUniqueId())) {
            this.saveBackpackOnClose(player, event.getInventory());
        }
        // 便攜垃圾桶：關閉時清空所有內容
        if (title.startsWith("便攜垃圾桶")) {
            event.getInventory().clear();
            if (event.getPlayer() instanceof Player player) {
                player.playSound(player.getLocation(), Sound.BLOCK_LAVA_EXTINGUISH, 0.5f, 1.2f);
                player.sendActionBar(this.plugin.getItemFactory().warning("垃圾桶已清空！"));
            }
        }
        if (this.plugin.getPlanetService().isPlanetaryGateView(title) && event.getPlayer() instanceof Player player) {
            this.plugin.getPlanetService().closePlanetaryGateMenu(player);
        }
        if (this.plugin.getMachineService().isTeleportPadView(title) && event.getPlayer() instanceof Player player2) {
            this.plugin.getMachineService().closeTeleportPadMenu(player2);
        }
        if (!this.plugin.getMachineService().isMachineView(title)) {
            return;
        }
        if (event.getPlayer() instanceof Player player) {
            this.plugin.getMachineService().closeMachineView(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        final String techItemId = this.plugin.getItemFactory().getTechItemId(event.getItemInHand());
        final String machineId = this.plugin.getItemFactory().getMachineId(event.getItemInHand());
        if (this.plugin.getPlanetService().isPlanetWorld(event.getBlockPlaced().getWorld())
                && !"planetary_gate".equalsIgnoreCase(machineId)
                && !"planetary_harvester".equalsIgnoreCase(machineId)) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(this.plugin.getItemFactory().warning("星球地表禁止建造，這裡只能探勘、採集與戰鬥。"));
            event.getPlayer().playSound(event.getBlockPlaced().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.7f);
            return;
        }
        if (machineId != null) {
            if (!this.plugin.getMachineService().validatePlacement(event.getPlayer(), event.getBlockPlaced(), machineId)) {
                event.setCancelled(true);
                return;
            }
            this.plugin.getMachineService().registerPlacedMachine(event.getPlayer(), event.getBlockPlaced(), machineId, event.getItemInHand());
            return;
        }
        // 口袋雞頭顱 / 雞網等不可放置的科技消耗品
        if (this.plugin.getItemFactory().isPocketChicken(event.getItemInHand())
                || this.plugin.getItemFactory().isChickenNet(event.getItemInHand())) {
            event.setCancelled(true);
            return;
        }
        if (this.plugin.getPlacedTechBlockService().shouldTrackPlacement(techItemId)) {
            this.plugin.getPlacedTechBlockService().registerPlacedBlock(event.getBlockPlaced(), techItemId);
            return;
        }
        if (techItemId != null) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(this.plugin.getItemFactory().warning("此科技物品不可放置。"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(final PlayerBucketEmptyEvent event) {
        // 禁止在科技方塊上放置液體（水桶/熔岩桶會沖掉機器方塊）
        if (this.isProtectedTechBlock(event.getBlock())) {
            event.setCancelled(true);
            return;
        }
        if (!this.plugin.getPlanetService().isPlanetWorld(event.getBlock().getWorld())) {
            return;
        }
        event.setCancelled(true);
        event.getPlayer().sendActionBar(this.plugin.getItemFactory().warning("星球地表禁止放置液體。"));
        event.getPlayer().playSound(event.getBlock().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.7f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHangingPlace(final HangingPlaceEvent event) {
        if (!this.plugin.getPlanetService().isPlanetWorld(event.getBlock().getWorld())) {
            return;
        }
        event.setCancelled(true);
        if (event.getPlayer() != null) {
            event.getPlayer().sendActionBar(this.plugin.getItemFactory().warning("星球地表禁止放置展示物與裝飾物。"));
            event.getPlayer().playSound(event.getBlock().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.7f);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        // 重入保護：canModifyBlock 模擬的 BlockBreakEvent 不可再進入此處理器
        if (this.simulatingBreakThreads.contains(Thread.currentThread().getId())) {
            return;
        }
        // 採集技能經驗 + 每日任務進度 — 排除科技機器與科技方塊（防止拆放洗經驗）
        if (event.getPlayer() != null && event.getPlayer().getGameMode() == GameMode.SURVIVAL) {
            final boolean isTechBlock = this.plugin.getMachineService().resolveManagedMachineBlock(event.getBlock()) != null
                    || this.plugin.getPlacedTechBlockService().isTrackedBlock(event.getBlock());
            if (!isTechBlock) {
                if (this.plugin.getSkillService() != null) {
                    this.plugin.getSkillService().grantXp(event.getPlayer(), "gathering", 1L);
                }
                if (this.plugin.getDailyQuestService() != null) {
                    this.plugin.getDailyQuestService().onBlockMine(event.getPlayer());
                }
                // 挖礦回魔：小額補充，避免掛機刷方塊過於誇張
                if (this.plugin.getManaService() != null) {
                    this.plugin.getManaService().restore(event.getPlayer(), 0.5);
                }
            }
        }
        // 礦脈連鎖：下一鎬觸發 BFS 連挖（不含原方塊，原方塊由原版事件處理）
        if (event.getPlayer() != null && this.plugin.getSpellExecutor() != null) {
            final Block broken = event.getBlock();
            final Material brokenType = broken.getType();
            final String bName = brokenType.name();
            if ((bName.endsWith("_ORE") || "ANCIENT_DEBRIS".equals(bName) || "NETHER_QUARTZ_ORE".equals(bName))
                    && this.plugin.getSpellExecutor().consumeVeinChain(event.getPlayer().getUniqueId())) {
                // 立即 BFS 鄰居（原方塊還在，BFS 內會跳過已訪問的）
                final Player bp = event.getPlayer();
                this.plugin.getSpellExecutor().executeVeinChain(bp, broken);
            }
        }
        // 互動烹調：破壞烹調中的方塊會中斷烹調
        this.plugin.getCookingService().interruptByBlockBreak(event.getBlock());
        final Block managedMachine = this.plugin.getMachineService().resolveManagedMachineBlock(event.getBlock());
        // 破壞的是配件方塊（壓力板、鐵柵欄、拉桿等），不是核心 → 不拆機器，讓原版掉落
        final boolean isRigComponent = managedMachine != null && !managedMachine.equals(event.getBlock());
        final PlacedMachine placedMachine = isRigComponent ? null : this.plugin.getMachineService().placedMachineAt(managedMachine);
        final boolean allowPlanetaryGateBreak = this.plugin.getPlanetService().isPlanetWorld(event.getBlock().getWorld())
                && placedMachine != null
                && "planetary_gate".equalsIgnoreCase(placedMachine.machineId());
        if (!allowPlanetaryGateBreak && this.plugin.getPlanetService().handlePlanetBreak(event)) {
            return;
        }
        this.handleWildGrassForaging(event);
        this.handleTalismanMiner(event);
        this.handleTalismanFarmer(event);
        if (this.handleFieldSickle(event) || this.handleCustomCropBreak(event)
                || this.handleExplosivePickaxe(event) || this.handleVeinMiningPickaxe(event)
                || this.handleSmeltersPickaxe(event) || this.handleLumberAxe(event)) {
            return;
        }
        final Block protectedMachine = isRigComponent ? null : managedMachine;
        if (protectedMachine != null
                && !this.plugin.getMachineService().canModifyMachine(event.getPlayer(), protectedMachine, true)) {
            event.setCancelled(true);
            return;
        }
        // 優先使用已解析的機器方塊來建立掉落物，避免多方塊機器的非基座方塊無法觸發 setDropItems(false)
        final Block dropTarget = (managedMachine != null && !isRigComponent) ? managedMachine : event.getBlock();
        final ItemStack machineDrop = this.plugin.getMachineService().buildPlacedMachineItem(dropTarget);
        if (machineDrop != null || (managedMachine != null && !isRigComponent)) {
            event.setDropItems(false);
            if (machineDrop != null) {
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), machineDrop);
            }
        }
        final ItemStack techBlockDrop = this.plugin.getPlacedTechBlockService().buildDrop(event.getBlock());
        if (techBlockDrop != null) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), techBlockDrop);
            this.plugin.getPlacedTechBlockService().unregister(event.getBlock());
        }
        this.plugin.getMachineService().unregisterMachine(dropTarget);
        // 破壞核心時，順便清除上方的配件方塊（壓力板等），讓它自然掉落
        if (machineDrop != null && !isRigComponent) {
            this.plugin.getMachineService().breakRigComponent(dropTarget);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(final EntityExplodeEvent event) {
        if (this.plugin.getPlanetService().isPlanetWorld(event.getEntity().getWorld())) {
            event.blockList().clear();
        }
        // 苦力怕自爆：EntityDeathEvent 不保證觸發，在此額外通知遺跡系統
        if (event.getEntity() instanceof org.bukkit.entity.LivingEntity living) {
            this.plugin.getPlanetService().onRuinChallengeMobDeath(living);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(final PlayerItemConsumeEvent event) {
        final String techItemId = this.plugin.getItemFactory().getTechItemId(event.getItem());
        if (techItemId == null) {
            return;
        }
        final Player player = event.getPlayer();
        if (!this.applyCustomFoodEffects(player, techItemId)) {
            return;
        }
        player.sendActionBar(this.plugin.getItemFactory().success("食物 / 飲品效果已生效。"));
    }

    private boolean tryUseCustomFood(final PlayerInteractEvent event) {
        if (event.getItem() == null || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return false;
        }
        // ★ 副手觸發的食物事件一律忽略，防止副手食物免費吃（消耗邏輯只處理主手）
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return false;
        }
        // 若玩家右鍵的是烹調站，優先交給烹調系統處理，不要直接吃掉食材
        if (event.getClickedBlock() != null
                && this.plugin.getCookingService().isCookingStation(event.getClickedBlock().getType())) {
            return false;
        }
        final String techItemId = this.plugin.getItemFactory().getTechItemId(event.getItem());
        if (techItemId == null) {
            return false;
        }
        final int hunger = this.manualFoodHunger(techItemId);
        if (hunger <= 0) {
            return false;
        }
        final Player player = event.getPlayer();
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);

        if (player.getGameMode() != GameMode.CREATIVE) {
            final ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand == null || hand.getAmount() <= 0 || !techItemId.equalsIgnoreCase(this.plugin.getItemFactory().getTechItemId(hand))) {
                return true; // 主手物品與偵測到的食物不符（理論上不會發生），安全跳過
            }
            if (hand.getAmount() <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                hand.setAmount(hand.getAmount() - 1);
                player.getInventory().setItemInMainHand(hand);
            }
        }

        this.restoreFood(player, hunger, this.manualFoodSaturation(techItemId));
        this.applyCustomFoodEffects(player, techItemId);
        player.setCooldown(event.getItem().getType(), this.foodCooldownTicks(techItemId));
        this.playCustomFoodConsumeSound(player, techItemId);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0.0, 1.0, 0.0), 6, 0.25, 0.2, 0.25, 0.01);
        player.sendActionBar(this.plugin.getItemFactory().success("已食用「" + this.plugin.getItemFactory().displayNameForId(techItemId) + "」。"));
        return true;
    }

    private void playCustomFoodConsumeSound(final Player player, final String techItemId) {
        if (player == null || techItemId == null || techItemId.isBlank()) {
            return;
        }
        if (this.isDrinkLikeFood(techItemId)) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.85f, 1.08f);
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_BOTTLE_EMPTY, 0.55f, 1.18f);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.35f, 1.28f);
            return;
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.85f, 1.02f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 0.7f, 1.18f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_BURP, 0.45f, 1.2f);
    }

    private boolean isDrinkLikeFood(final String techItemId) {
        final String normalized = techItemId.toLowerCase();
        return normalized.contains("juice")
                || normalized.contains("tea")
                || normalized.contains("smoothie")
                || normalized.contains("lemonade")
                || normalized.contains("fizz")
                || normalized.contains("glaze")
                || normalized.contains("sorbet")
                || normalized.contains("gel")
                || normalized.contains("milkshake")
                || normalized.contains("cocoa")
                || normalized.contains("cider")
                || normalized.contains("ale")
                || normalized.contains("latte")
                || normalized.contains("espresso")
                || normalized.contains("punch")
                || normalized.contains("blend")
                || normalized.equals("coconut_water");
    }

    private boolean hasTechBook(final Player player) {
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (this.plugin.getItemFactory().hasTechBookTag(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWrench(final Player player) {
        for (final ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (this.plugin.getItemFactory().hasWrenchTag(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryPlantTechCrop(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getItem() == null) {
            return false;
        }
        if (!this.plugin.getTechCropService().tryPlant(event.getPlayer(), event.getClickedBlock(), event.getBlockFace(), event.getItem())) {
            return false;
        }
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);
        return true;
    }

    private void handleWildGrassForaging(final BlockBreakEvent event) {
        if (event.isCancelled() || event.getPlayer().getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (this.plugin.getPlanetService().isPlanetWorld(event.getBlock().getWorld())) {
            return;
        }
        final Material material = event.getBlock().getType();
        if (material != Material.SHORT_GRASS && material != Material.TALL_GRASS && material != Material.FERN && material != Material.LARGE_FERN) {
            return;
        }
        final String dropId = this.rollWildForageDrop();
        if (dropId == null) {
            return;
        }
        final ItemStack drop = this.buildTechDrop(dropId, 1);
        if (drop == null || drop.getType() == Material.AIR) {
            return;
        }
        final Location location = event.getBlock().getLocation().add(0.5, 0.45, 0.5);
        event.getBlock().getWorld().dropItemNaturally(location, drop);
        event.getBlock().getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location, 6, 0.18, 0.12, 0.18, 0.01);
        event.getPlayer().sendActionBar(this.plugin.getItemFactory().success("野外採集到「" + this.plugin.getItemFactory().displayNameForId(dropId) + "」。"));
    }

    private String rollWildForageDrop() {
        final double roll = ThreadLocalRandom.current().nextDouble();
        if (roll < 0.0075D) {
            return "soybean_seeds";
        }
        if (roll < 0.01375D) {
            return "spiceberry_seeds";
        }
        if (roll < 0.020D) {
            return "tea_leaf_seeds";
        }
        if (roll < 0.0255D) {
            return "tomato_seeds";
        }
        if (roll < 0.031D) {
            return "cabbage_seeds";
        }
        if (roll < 0.0365D) {
            return "corn_seeds";
        }
        if (roll < 0.042D) {
            return "onion_bulbs";
        }
        if (roll < 0.0445D) {
            return "lumenfruit_sapling";
        }
        if (roll < 0.047D) {
            return "frost_apple_sapling";
        }
        if (roll < 0.0495D) {
            return "shadow_berry_sapling";
        }
        if (roll < 0.052D) {
            return "sunflare_fig_sapling";
        }
        if (roll < 0.0545D) {
            return "stormplum_sapling";
        }
        if (roll < 0.0565D) {
            return "cherry_sapling";
        }
        if (roll < 0.0585D) {
            return "lemon_sapling";
        }
        if (roll < 0.0605D) {
            return "peach_sapling";
        }
        if (roll < 0.0625D) {
            return "pear_sapling";
        }
        if (roll < 0.0645D) {
            return "orange_sapling";
        }
        return null;
    }

    private ItemStack buildTechDrop(final String id, final int amount) {
        if (this.plugin.getTechRegistry().getItem(id) == null) {
            return null;
        }
        final ItemStack stack = this.plugin.getItemFactory().buildTechItem(this.plugin.getTechRegistry().getItem(id));
        stack.setAmount(Math.min(amount, stack.getMaxStackSize()));
        return stack;
    }

    private int manualFoodHunger(final String techItemId) {
        return switch (techItemId.toLowerCase()) {
            case "dragon_steak", "stellar_risotto" -> 10;
            case "void_ramen", "phoenix_soup" -> 9;
            case "sunrise_pie", "stuffed_cabbage", "orchard_ration", "cryon_hotpot",
                "ramen", "cheese_pizza", "chocolate_cake", "cheesecake", "vegetable_curry" -> 8;
            case "peach_cobbler", "aurelia_glaze", "nyx_phase_gel", "helion_sorbet",
                "mushroom_risotto", "breakfast_burrito", "tropical_punch" -> 7;
            case "orchard_salad", "cornbread", "fruit_puree", "nutrition_bar", "protein_ration",
                "citrus_salad", "tomato_stew", "pear_crisp", "tempest_fizz",
                "fried_rice", "noodle_soup", "grilled_vegetables", "dumpling",
                "fish_stew", "veggie_wrap", "potato_soup", "fruit_parfait", "arcane_tea" -> 6;
            case "lumenfruit", "frost_apple", "sunflare_fig", "soybean_pods", "emberroot",
                "tomato", "cabbage", "corn", "lemon", "peach", "pear", "orange", "orchard_smoothie",
                "mango", "banana", "pineapple", "coconut", "garlic_bread", "roasted_corn",
                "sushi_roll", "pancakes", "vanilla_ice_cream" -> 5;
            case "shadow_berry_cluster", "stormplum", "spiceberry", "frostbloom", "ion_fern",
                    "onion", "cherry", "lemon_juice", "orange_juice", "peach_juice", "pear_juice",
                "cherry_juice", "radiant_tea", "nebula_juice", "berry_tart", "sparkling_lemonade",
                "grape", "strawberry", "blueberry", "raspberry", "watermelon_slice", "kiwi",
                "garlic", "lettuce", "bell_pepper", "spinach", "radish",
                "grape_juice", "mango_smoothie", "coconut_water", "berry_blend",
                "green_tea", "iced_tea", "hot_cocoa", "apple_cider", "ginger_ale",
                "milk_tea", "matcha_latte", "espresso", "vanilla_milkshake", "strawberry_milkshake",
                "donut", "muffin", "brownie", "cinnamon_roll", "honey_toast" -> 4;
            default -> 0;
        };
    }

    private float manualFoodSaturation(final String techItemId) {
        return switch (techItemId.toLowerCase()) {
            case "dragon_steak", "stellar_risotto" -> 12.0f;
            case "void_ramen", "phoenix_soup" -> 10.0f;
            case "sunrise_pie", "stuffed_cabbage", "orchard_ration", "cryon_hotpot",
                "ramen", "cheese_pizza", "chocolate_cake", "cheesecake", "vegetable_curry" -> 9.0f;
            case "peach_cobbler", "aurelia_glaze", "nyx_phase_gel", "helion_sorbet",
                "mushroom_risotto", "breakfast_burrito", "tropical_punch" -> 8.0f;
            case "orchard_salad", "cornbread", "fruit_puree", "nutrition_bar", "protein_ration",
                "citrus_salad", "tomato_stew", "pear_crisp", "tempest_fizz",
                "fried_rice", "noodle_soup", "grilled_vegetables", "dumpling",
                "fish_stew", "veggie_wrap", "potato_soup", "fruit_parfait", "arcane_tea" -> 7.0f;
            case "sunflare_fig", "emberroot", "peach", "orange",
                "mango", "banana", "pineapple", "coconut", "garlic_bread",
                "sushi_roll", "roasted_corn", "pancakes", "vanilla_ice_cream" -> 6.0f;
            case "lumenfruit", "frost_apple", "soybean_pods", "tomato", "cabbage", "corn", "lemon", "pear", "orchard_smoothie",
                "grape", "strawberry", "blueberry", "raspberry", "watermelon_slice", "kiwi" -> 5.0f;
            case "shadow_berry_cluster", "stormplum", "spiceberry", "frostbloom", "ion_fern", "onion",
                    "cherry", "lemon_juice", "orange_juice", "peach_juice", "pear_juice", "cherry_juice",
                "radiant_tea", "nebula_juice", "berry_tart", "sparkling_lemonade",
                "garlic", "lettuce", "bell_pepper", "spinach", "radish",
                "grape_juice", "mango_smoothie", "coconut_water", "berry_blend",
                "green_tea", "iced_tea", "hot_cocoa", "apple_cider", "ginger_ale",
                "milk_tea", "matcha_latte", "espresso", "vanilla_milkshake", "strawberry_milkshake",
                "donut", "muffin", "brownie", "cinnamon_roll", "honey_toast" -> 4.5f;
            default -> 0.0f;
        };
    }

    private void restoreFood(final Player player, final int hunger, final float saturation) {
        if (player == null || hunger <= 0) {
            return;
        }
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + hunger));
        player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + Math.max(0.0f, saturation)));
    }

    /** 高階料理有較長的食用冷卻（ticks）。 */
    private int foodCooldownTicks(final String techItemId) {
        return switch (techItemId.toLowerCase()) {
            case "stellar_risotto" -> 7200;     // 6 分鐘
            case "dragon_steak" -> 6000;         // 5 分鐘
            case "phoenix_soup" -> 5400;         // 4.5 分鐘
            case "void_ramen" -> 4800;           // 4 分鐘
            case "arcane_tea" -> 4200;           // 3.5 分鐘
            default -> 12;
        };
    }

    private boolean applyCustomFoodEffects(final Player player, final String techItemId) {
        if (player == null || techItemId == null || techItemId.isBlank()) {
            return false;
        }
        switch (techItemId.toLowerCase()) {
            case "nutrition_bar" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 8, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, 0, false, true, true));
            }
            case "protein_ration" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 30, 0, false, true, true));
            }
            case "radiant_tea" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 90, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 45, 0, false, true, true));
            }
            case "lumenfruit" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 5, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 40, 0, false, true, true));
            }
            case "frost_apple" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 35, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 16, 0, false, true, true));
            }
            case "shadow_berry_cluster" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 55, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 22, 0, false, true, true));
            }
            case "sunflare_fig" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 6, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 30, 0, false, true, true));
            }
            case "stormplum" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 28, 1, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 18, 0, false, true, true));
            }
            case "spiceberry" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 18, 0, false, true, true));
            }
            case "soybean_pods" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 4, 0, false, true, true));
            }
            case "tomato" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 8, 0, false, true, true));
            }
            case "cabbage" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 5, 0, false, true, true));
            }
            case "corn" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 5, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 12, 0, false, true, true));
            }
            case "onion" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 12, 0, false, true, true));
            }
            case "frostbloom" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 15, 0, false, true, true));
            }
            case "emberroot" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 20, 0, false, true, true));
            }
            case "ion_fern" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 20, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 12, 0, false, true, true));
            }
            case "cherry" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 18, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 10, 0, false, true, true));
            }
            case "lemon" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, 0, false, true, true));
            }
            case "peach" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 0, false, true, true));
            }
            case "pear" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 18, 0, false, true, true));
            }
            case "orange" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, 1, false, true, true));
            }
            case "fruit_puree" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 8, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 7, 0, false, true, true));
            }
            case "lemon_juice" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 35, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 20, 0, false, true, true));
            }
            case "orange_juice" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 28, 1, false, true, true));
            }
            case "peach_juice" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 14, 0, false, true, true));
            }
            case "pear_juice" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 24, 0, false, true, true));
            }
            case "cherry_juice" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 40, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 18, 0, false, true, true));
            }
            case "nebula_juice" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 70, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 40, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 18, 0, false, true, true));
            }
            case "sunrise_pie" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 28, 0, false, true, true));
            }
            case "berry_tart" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 24, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 18, 0, false, true, true));
            }
            case "citrus_salad" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 6, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 16, 0, false, true, true));
            }
            case "tomato_stew" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 14, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 14, 0, false, true, true));
            }
            case "orchard_smoothie" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 28, 0, false, true, true));
            }
            case "sparkling_lemonade" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 30, 1, false, true, true));
            }
            case "peach_cobbler" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 12, 0, false, true, true));
            }
            case "pear_crisp" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 18, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 18, 0, false, true, true));
            }
            case "aurelia_glaze" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 45, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 24, 0, false, true, true));
                this.plugin.getPlanetService().applyPlanetCuisineBuff(player, techItemId);
            }
            case "cryon_hotpot" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 30, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 24, 0, false, true, true));
                this.plugin.getPlanetService().applyPlanetCuisineBuff(player, techItemId);
            }
            case "nyx_phase_gel" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 36, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 36, 0, false, true, true));
                this.plugin.getPlanetService().applyPlanetCuisineBuff(player, techItemId);
            }
            case "helion_sorbet" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 45, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 12, 0, false, true, true));
                this.plugin.getPlanetService().applyPlanetCuisineBuff(player, techItemId);
            }
            case "tempest_fizz" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 30, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 24, 0, false, true, true));
                this.plugin.getPlanetService().applyPlanetCuisineBuff(player, techItemId);
            }
            case "orchard_salad" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 0, false, true, true));
            }
            case "cornbread" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 8, 0, false, true, true));
            }
            case "grape" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 14, 0, false, true, true));
            }
            case "banana" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 6, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 12, 0, false, true, true));
            }
            case "mango" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 6, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 8, 0, false, true, true));
            }
            case "coconut" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 16, 0, false, true, true));
            }
            case "strawberry" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 16, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 6, 0, false, true, true));
            }
            case "blueberry" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 30, 0, false, true, true));
            }
            case "raspberry" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 18, 0, false, true, true));
            }
            case "watermelon_slice" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 5, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 12, 0, false, true, true));
            }
            case "pineapple" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 18, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 10, 0, false, true, true));
            }
            case "kiwi" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 10, 0, false, true, true));
            }
            case "garlic" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 12, 0, false, true, true));
            }
            case "lettuce" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 4, 0, false, true, true));
            }
            case "bell_pepper" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 14, 0, false, true, true));
            }
            case "spinach" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 4, 0, false, true, true));
            }
            case "radish" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 10, 0, false, true, true));
            }
            case "grape_juice" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 22, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 8, 0, false, true, true));
            }
            case "mango_smoothie" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 12, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 6, 0, false, true, true));
            }
            case "coconut_water" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 25, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 14, 0, false, true, true));
            }
            case "berry_blend" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 24, 0, false, true, true));
            }
            case "tropical_punch" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 30, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 0, false, true, true));
            }
            case "green_tea" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 30, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 20, 0, false, true, true));
            }
            case "iced_tea" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 24, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 18, 0, false, true, true));
            }
            case "hot_cocoa" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 24, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 0, false, true, true));
            }
            case "apple_cider" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 12, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 5, 0, false, true, true));
            }
            case "ginger_ale" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 22, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 14, 0, false, true, true));
            }
            case "milk_tea" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 14, 0, false, true, true));
            }
            case "matcha_latte" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 40, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 35, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, 0, false, true, true));
            }
            case "espresso" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 40, 1, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 30, 0, false, true, true));
            }
            case "vanilla_milkshake" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 8, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 14, 0, false, true, true));
            }
            case "strawberry_milkshake" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 16, 0, false, true, true));
            }
            case "fried_rice" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 8, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 14, 0, false, true, true));
            }
            case "noodle_soup" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 12, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 12, 0, false, true, true));
            }
            case "grilled_vegetables" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 6, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 16, 0, false, true, true));
            }
            case "mushroom_risotto" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 20, 0, false, true, true));
            }
            case "vegetable_curry" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 24, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 18, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 8, 0, false, true, true));
            }
            case "sushi_roll" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 14, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 5, 0, false, true, true));
            }
            case "ramen" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 12, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 20, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 0, false, true, true));
            }
            case "potato_soup" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 16, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 8, 0, false, true, true));
            }
            case "garlic_bread" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 6, 0, false, true, true));
            }
            case "cheese_pizza" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 12, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 18, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 8, 0, false, true, true));
            }
            case "dumpling" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 7, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 12, 0, false, true, true));
            }
            case "fish_stew" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 12, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 10, 0, false, true, true));
            }
            case "roasted_corn" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 6, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 14, 0, false, true, true));
            }
            case "veggie_wrap" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 18, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 6, 0, false, true, true));
            }
            case "breakfast_burrito" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 20, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 14, 0, false, true, true));
            }
            case "chocolate_cake" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 12, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 24, 0, false, true, true));
            }
            case "vanilla_ice_cream" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 20, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 6, 0, false, true, true));
            }
            case "donut" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 18, 0, false, true, true));
            }
            case "muffin" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 5, 0, false, true, true));
            }
            case "brownie" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 6, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 10, 0, false, true, true));
            }
            case "cheesecake" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 12, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 12, 0, false, true, true));
            }
            case "fruit_parfait" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 8, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 12, 0, false, true, true));
            }
            case "pancakes" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 7, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 10, 0, false, true, true));
            }
            case "cinnamon_roll" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 5, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 14, 0, false, true, true));
            }
            case "honey_toast" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 5, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 8, 0, false, true, true));
            }
            case "stuffed_cabbage" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 12, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 18, 0, false, true, true));
            }
            case "orchard_ration" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 40, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 25, 0, false, true, true));
            }
            // ── 高階料理：效果強力但時間極短 + 長食用 CD ──
            case "dragon_steak" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 10, 1, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 10, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 10, 1, false, true, true));
            }
            case "void_ramen" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 8, 2, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 15, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 10, 0, false, true, true));
            }
            case "stellar_risotto" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 10, 3, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 8, 2, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 10, 1, false, true, true));
            }
            case "phoenix_soup" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 15, 0, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 6, 2, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 10, 0, false, true, true));
            }
            case "arcane_tea" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 10, 2, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 10, 1, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 20, 0, false, true, true));
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    // ═══ 淘金盤：右鍵礫石/靈魂沙篩出金屬粉塵 ═══
    private boolean tryUseGoldPan(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getItem() == null) {
            return false;
        }
        final String techItemId = this.plugin.getItemFactory().getTechItemId(event.getItem());
        if (!"gold_pan".equalsIgnoreCase(techItemId)) {
            return false;
        }
        final Block target = event.getClickedBlock();
        final Material blockType = target.getType();
        final boolean isGravel = blockType == Material.GRAVEL;
        final boolean isSoulSand = blockType == Material.SOUL_SAND || blockType == Material.SOUL_SOIL;
        if (!isGravel && !isSoulSand) {
            return false;
        }
        final Player player = event.getPlayer();
        // 領地保護：檢查玩家是否有權操作此方塊
        if (!this.canModifyBlock(player, target)) {
            player.sendActionBar(this.plugin.getItemFactory().danger("你沒有權限在這裡使用淘金盤。"));
            return true;
        }
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);
        // 冷卻檢查（20 tick = 1 秒）
        if (player.getCooldown(event.getItem().getType()) > 0) {
            return true;
        }
        player.setCooldown(event.getItem().getType(), 20);
        // 消耗方塊
        final Location loc = target.getLocation().add(0.5, 0.5, 0.5);
        target.setType(Material.AIR);
        // 音效與粒子
        player.getWorld().playSound(loc, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.7f, 1.2f);
        player.getWorld().spawnParticle(Particle.FALLING_WATER, loc, 12, 0.3, 0.2, 0.3, 0.01);
        // 篩礦掉落
        final String dropId = isGravel ? this.rollGravelPanDrop() : this.rollSoulSandPanDrop();
        if (dropId == null) {
            player.sendActionBar(this.plugin.getItemFactory().warning("什麼都沒篩到…"));
            return true;
        }
        final ItemStack drop;
        if ("flint".equalsIgnoreCase(dropId)) {
            drop = new ItemStack(Material.FLINT, 1);
        } else if ("gold_nugget".equalsIgnoreCase(dropId)) {
            drop = new ItemStack(Material.GOLD_NUGGET, 1);
        } else if ("quartz".equalsIgnoreCase(dropId)) {
            drop = new ItemStack(Material.QUARTZ, 1);
        } else {
            drop = this.buildTechDrop(dropId, 1);
        }
        if (drop == null || drop.getType() == Material.AIR) {
            return true;
        }
        player.getWorld().dropItemNaturally(loc, drop);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 0.2, 0.15, 0.2, 0.01);
        player.sendActionBar(this.plugin.getItemFactory().success("篩出了「" + this.plugin.getItemFactory().displayNameForId(dropId) + "」！"));
        return true;
    }

    /**
     * 礫石淘金盤掉落表：
     * 30% 無 → null
     * 15% 礫石(flint)
     * 15% 篩出礦砂
     * 12% 鐵粉
     * 10% 銅粉
     * 8%  錫粉
     * 6%  鋅粉
     * 4%  矽晶
     */
    private String rollGravelPanDrop() {
        final double r = ThreadLocalRandom.current().nextDouble();
        if (r < 0.30) return null;
        if (r < 0.45) return "flint";
        if (r < 0.60) return "sifted_ore";
        if (r < 0.72) return "iron_dust";
        if (r < 0.82) return "copper_dust";
        if (r < 0.90) return "tin_dust";
        if (r < 0.96) return "zinc_dust";
        return "silicon";
    }

    /**
     * 靈魂沙淘金盤掉落表：
     * 30% 無
     * 18% 鉛粉
     * 15% 篩出礦砂
     * 12% 金粒
     * 10% 石英
     * 8%  鋅粉
     * 7%  鐵粉
     */
    private String rollSoulSandPanDrop() {
        final double r = ThreadLocalRandom.current().nextDouble();
        if (r < 0.30) return null;
        if (r < 0.48) return "lead_dust";
        if (r < 0.63) return "sifted_ore";
        if (r < 0.75) return "gold_nugget";
        if (r < 0.85) return "quartz";
        if (r < 0.93) return "zinc_dust";
        return "iron_dust";
    }

    private boolean tryUseHydroSpade(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null || event.getItem() == null) {
            return false;
        }
        final String techItemId = this.plugin.getItemFactory().getTechItemId(event.getItem());
        if (!"hydro_spade".equalsIgnoreCase(techItemId)) {
            return false;
        }
        final Block center = event.getClickedBlock().getBlockData() instanceof Ageable
                ? event.getClickedBlock()
                : event.getClickedBlock().getRelative(BlockFace.UP);
        // 領地保護：檢查玩家是否有權操作中心方塊
        if (!this.canModifyBlock(event.getPlayer(), center)) {
            event.getPlayer().sendActionBar(this.plugin.getItemFactory().danger("你沒有權限在這裡使用水利鏟。"));
            return true;
        }
        int grown = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final Block crop = center.getWorld().getBlockAt(center.getX() + dx, center.getY(), center.getZ() + dz);
                // ★ 逐格領地檢查，防止在邊界催熟別人的作物
                if ((dx != 0 || dz != 0) && !this.canModifyBlock(event.getPlayer(), crop)) {
                    continue;
                }
                if (this.plugin.getTechCropService().grow(crop, 2)) {
                    grown++;
                    continue;
                }
                final BlockData data = crop.getBlockData();
                if (data instanceof Ageable ageable && crop.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND && ageable.getAge() < ageable.getMaximumAge()) {
                    ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 2));
                    crop.setBlockData(ageable, true);
                    grown++;
                }
            }
        }
        if (grown <= 0) {
            return false;
        }
        event.getPlayer().setCooldown(event.getItem().getType(), 10);
        center.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, center.getLocation().add(0.5, 0.8, 0.5), 18, 1.0, 0.35, 1.0, 0.02);
        center.getWorld().playSound(center.getLocation(), Sound.ITEM_BONE_MEAL_USE, 0.6f, 1.25f);
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);
        return true;
    }

    private boolean tryUseMobilityTool(final PlayerInteractEvent event) {
        return this.tryChargeToolAtMachine(event) || this.tryUseVectorGrapple(event) || this.tryUsePulseThruster(event);
    }

    private boolean tryChargeToolAtMachine(final PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || !event.getPlayer().isSneaking()) {
            return false;
        }
        ItemStack target = event.getItem();
        String targetId = target == null ? null : this.plugin.getItemFactory().getTechItemId(target);
        long maxEnergy = this.plugin.getItemFactory().maxItemEnergy(targetId);
        if (maxEnergy <= 0L) {
            target = event.getPlayer().getInventory().getChestplate();
            targetId = target == null ? null : this.plugin.getItemFactory().getTechItemId(target);
            maxEnergy = this.plugin.getItemFactory().maxItemEnergy(targetId);
        }
        if (maxEnergy <= 0L || target == null) {
            return false;
        }
        final Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return false;
        }
        final Block machineBlock = this.plugin.getMachineService().resolveManagedMachineBlock(clicked);
        if (machineBlock == null) {
            return false;
        }
        final var machine = this.plugin.getMachineService().placedMachineAt(machineBlock);
        if (machine == null) {
            return false;
        }
        /* 先從鄰近發電機吸收電力，避免因放置順序而無法充電 */
        this.plugin.getMachineService().pullNearbyEnergyInto(machine, machineBlock.getLocation());
        if (machine.storedEnergy() <= 0L) {
            return false;
        }
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);
        final long currentEnergy = this.plugin.getItemFactory().getItemStoredEnergy(target);
        if (currentEnergy >= maxEnergy) {
            event.getPlayer().sendActionBar(this.plugin.getItemFactory().secondary("⚡ " + currentEnergy + "/" + maxEnergy + " EU — 已滿電"));
            return true;
        }
        final long need = maxEnergy - currentEnergy;
        final long transfer = Math.min(need, machine.storedEnergy());
        machine.consumeEnergy(transfer);
        this.plugin.getItemFactory().setItemStoredEnergy(target, currentEnergy + transfer);
        final long after = currentEnergy + transfer;
        event.getPlayer().sendActionBar(this.plugin.getItemFactory().success("充電完成 ⚡ " + after + "/" + maxEnergy + " EU"));
        event.getPlayer().getWorld().playSound(event.getPlayer().getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, 1.3f);
        return true;
    }

    private boolean tryUseVectorGrapple(final PlayerInteractEvent event) {
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) || event.getItem() == null) {
            return false;
        }
        if (!VECTOR_GRAPPLE.equalsIgnoreCase(this.plugin.getItemFactory().getTechItemId(event.getItem()))) {
            return false;
        }
        final Player player = event.getPlayer();
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);

        if (!this.isCooldownReady(this.grappleCooldowns, player.getUniqueId())) {
            player.sendActionBar(this.plugin.getItemFactory().warning("向量抓鉤冷卻中。"));
            return true;
        }

        final ItemStack held = event.getItem();
        final long energy = this.plugin.getItemFactory().getItemStoredEnergy(held);
        if (energy < GRAPPLE_ENERGY_COST) {
            player.sendActionBar(this.plugin.getItemFactory().warning("向量抓鉤電量不足，請對準儲能機器 蹲下 + 右鍵充電。"));
            return true;
        }

        final Arrow arrow = player.launchProjectile(Arrow.class);
        arrow.setVelocity(player.getLocation().getDirection().multiply(3.5));
        arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
        arrow.setGravity(true);
        // 防止記憶體洩漏：超過 200 個殘留箭矢 UUID 時清理最舊的
        if (this.grappleArrowOwners.size() > 200) {
            final var it = this.grappleArrowOwners.entrySet().iterator();
            while (it.hasNext() && this.grappleArrowOwners.size() > 100) {
                it.next();
                it.remove();
            }
        }
        this.grappleArrowOwners.put(arrow.getUniqueId(), player.getUniqueId());
        this.startCooldown(this.grappleCooldowns, player.getUniqueId(), 1200L);

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 0.75f, 1.45f);
        player.sendActionBar(this.plugin.getItemFactory().success("向量抓鉤已發射…"));

        this.plugin.getSafeScheduler().runEntityDelayed(arrow, () -> {
            if (this.grappleArrowOwners.remove(arrow.getUniqueId()) != null) {
                arrow.remove();
            }
        }, 160L);
        return true;
    }

    @EventHandler
    public void onProjectileHit(final ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Arrow arrow)) {
            return;
        }
        final UUID ownerId = this.grappleArrowOwners.remove(arrow.getUniqueId());
        if (ownerId == null) {
            return;
        }
        final Player player = this.plugin.getServer().getPlayer(ownerId);
        if (player == null || !player.isOnline()) {
            arrow.remove();
            return;
        }
        final Location anchor = arrow.getLocation();
        final Vector delta = anchor.toVector().subtract(player.getLocation().toVector());
        final double distance = delta.length();
        arrow.remove();
        if (distance < 1.35) {
            return;
        }

        final ItemStack held = player.getInventory().getItemInMainHand();
        if (VECTOR_GRAPPLE.equalsIgnoreCase(this.plugin.getItemFactory().getTechItemId(held))) {
            this.plugin.getItemFactory().drainItemEnergy(held, GRAPPLE_ENERGY_COST);
            final long remaining = this.plugin.getItemFactory().getItemStoredEnergy(held);
            final long max = this.plugin.getItemFactory().maxItemEnergy(VECTOR_GRAPPLE);
            player.sendActionBar(this.plugin.getItemFactory().success("向量抓鉤鎖定！ ⚡ " + remaining + "/" + max));
        }

        final Vector launch = delta.normalize().multiply(Math.min(2.15, 0.60 + (distance * 0.072)));
        launch.setY(Math.max(0.55, Math.min(1.45, launch.getY() + 0.38)));
        if (this.isStandingOnSolidGround(player)) {
            launch.setY(Math.max(0.92, launch.getY()));
        }

        player.setVelocity(player.getVelocity().multiply(0.12).add(launch));
        player.setFallDistance(0.0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 38, 0, false, true, true));
        this.markGracefulLanding(player, 3200L);

        this.spawnParticleLine(player.getEyeLocation(), anchor, Particle.ELECTRIC_SPARK, 14);
        player.getWorld().playSound(anchor, Sound.BLOCK_CHAIN_PLACE, 0.55f, 1.7f);
    }

    private boolean tryUsePulseThruster(final PlayerInteractEvent event) {
        if ((event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) || event.getItem() == null) {
            return false;
        }
        if (!PULSE_THRUSTER.equalsIgnoreCase(this.plugin.getItemFactory().getTechItemId(event.getItem()))) {
            return false;
        }
        final Player player = event.getPlayer();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !player.isSneaking()) {
            return false;
        }
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);

        if (!this.isCooldownReady(this.thrusterCooldowns, player.getUniqueId())) {
            player.sendActionBar(this.plugin.getItemFactory().warning("脈衝推進器冷卻中。"));
            return true;
        }

        final ItemStack thrusterItem = event.getItem();
        final long thrusterEnergy = this.plugin.getItemFactory().getItemStoredEnergy(thrusterItem);
        if (thrusterEnergy < THRUSTER_ENERGY_COST) {
            player.sendActionBar(this.plugin.getItemFactory().warning("脈衝推進器電量不足，請對準儲能機器 蹲下 + 右鍵充電。"));
            return true;
        }

        final Vector direction = player.getLocation().getDirection().normalize();
        final double forward = player.isSneaking() ? 0.55 : 1.28;
        final double upward = player.isSneaking() ? 1.02 : 0.42;
        final Vector thrust = direction.multiply(forward);
        thrust.setY(Math.max(upward, (direction.getY() * 0.4) + upward));
        if (this.isStandingOnSolidGround(player)) {
            thrust.setY(thrust.getY() + 0.1);
        }

        player.setVelocity(player.getVelocity().multiply(0.18).add(thrust));
        player.setFallDistance(0.0f);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 24, 0, false, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 24, 0, false, true, true));
        this.markGracefulLanding(player, 2200L);
        this.startCooldown(this.thrusterCooldowns, player.getUniqueId(), 850L);

        final Location exhaust = player.getLocation().add(0.0, 0.8, 0.0);
        exhaust.getWorld().spawnParticle(Particle.FLAME, exhaust, 12, 0.22, 0.25, 0.22, 0.01);
        exhaust.getWorld().spawnParticle(Particle.CLOUD, exhaust, 10, 0.18, 0.22, 0.18, 0.02);
        exhaust.getWorld().playSound(exhaust, Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.35f);
        exhaust.getWorld().playSound(exhaust, Sound.ITEM_FIRECHARGE_USE, 0.55f, 1.55f);
        this.plugin.getItemFactory().drainItemEnergy(thrusterItem, THRUSTER_ENERGY_COST);
        final long thrusterRemaining = this.plugin.getItemFactory().getItemStoredEnergy(thrusterItem);
        final long thrusterMax = this.plugin.getItemFactory().maxItemEnergy(PULSE_THRUSTER);
        player.sendActionBar(this.plugin.getItemFactory().success("脈衝推進器已輸出推力。 ⚡ " + thrusterRemaining + "/" + thrusterMax));
        return true;
    }

    private boolean handleCustomCropBreak(final BlockBreakEvent event) {
        Block block = event.getBlock();
        // 玩家可能直接打頭顱：解析到下方基礎作物方塊
        final Block headBase = this.plugin.getTechCropService().resolveHeadToCropBase(block);
        if (headBase != null) {
            block = headBase;
        }
        if (!this.plugin.getTechCropService().isTrackedCrop(block)) {
            return false;
        }
        event.setCancelled(true);
        event.setDropItems(false);
        final boolean replant = this.plugin.getTechCropService().isMature(block) && !event.getPlayer().isSneaking();
        final List<ItemStack> drops = this.plugin.getTechCropService().harvest(block, replant);
        int harvested = 0;
        for (final ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR) {
                continue;
            }
            harvested += drop.getAmount();
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.4, 0.5), drop);
        }
        if (harvested > 0) {
            this.plugin.getPlayerProgressService().incrementStat(event.getPlayer().getUniqueId(), "farm_harvested", harvested);
            block.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, block.getLocation().add(0.5, 0.75, 0.5), 10, 0.25, 0.15, 0.25, 0.01);
            block.getWorld().playSound(block.getLocation(), Sound.ITEM_BONE_MEAL_USE, 0.45f, 1.2f);
        }
        return true;
    }

    private boolean handleFieldSickle(final BlockBreakEvent event) {
        final ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!"field_sickle".equalsIgnoreCase(this.plugin.getItemFactory().getTechItemId(tool))) {
            return false;
        }
        Block origin = event.getBlock();
        // 若打的是頭顱，解析到下方基礎作物方塊
        final Block headBase = this.plugin.getTechCropService().resolveHeadToCropBase(origin);
        if (headBase != null) {
            origin = headBase;
        }
        if (!this.isHarvestableCrop(origin)) {
            return false;
        }
        event.setCancelled(true);
        event.setDropItems(false);
        int harvested = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                final Block crop = origin.getWorld().getBlockAt(origin.getX() + dx, origin.getY(), origin.getZ() + dz);
                // 領地保護：逐格檢查（含中心方塊）
                if (!this.canModifyBlock(event.getPlayer(), crop)) {
                    continue;
                }
                if (this.plugin.getTechCropService().isTrackedCrop(crop)) {
                    if (!this.plugin.getTechCropService().isMature(crop)) {
                        continue;
                    }
                    for (final ItemStack drop : this.plugin.getTechCropService().harvest(crop, true)) {
                        if (drop == null || drop.getType() == Material.AIR) {
                            continue;
                        }
                        harvested += drop.getAmount();
                        crop.getWorld().dropItemNaturally(crop.getLocation().add(0.5, 0.4, 0.5), drop);
                    }
                    crop.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, crop.getLocation().add(0.5, 0.75, 0.5), 6, 0.18, 0.12, 0.18, 0.01);
                    continue;
                }
                final BlockData data = crop.getBlockData();
                if (!(data instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge() || crop.getRelative(BlockFace.DOWN).getType() != Material.FARMLAND) {
                    continue;
                }
                for (final ItemStack drop : crop.getDrops(tool, event.getPlayer())) {
                    harvested += drop.getAmount();
                    crop.getWorld().dropItemNaturally(crop.getLocation().add(0.5, 0.4, 0.5), drop);
                }
                ageable.setAge(0);
                crop.setBlockData(ageable, true);
                crop.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, crop.getLocation().add(0.5, 0.75, 0.5), 6, 0.18, 0.12, 0.18, 0.01);
            }
        }
        if (harvested > 0) {
            this.plugin.getPlayerProgressService().incrementStat(event.getPlayer().getUniqueId(), "farm_harvested", harvested);
            origin.getWorld().playSound(origin.getLocation(), Sound.ITEM_BONE_MEAL_USE, 0.55f, 1.35f);
        }
        return true;
    }

    private boolean isHarvestableCrop(final Block block) {
        if (block == null) {
            return false;
        }
        if (this.plugin.getTechCropService().isTrackedCrop(block)) {
            return this.plugin.getTechCropService().isMature(block);
        }
        final BlockData data = block.getBlockData();
        return data instanceof Ageable ageable
                && ageable.getAge() >= ageable.getMaximumAge()
                && block.getRelative(BlockFace.DOWN).getType() == Material.FARMLAND;
    }

    // ── 爆破鎬：3×3 範圍挖掘 ──
    private boolean handleExplosivePickaxe(final BlockBreakEvent event) {
        final ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!"explosive_pickaxe".equalsIgnoreCase(this.plugin.getItemFactory().getTechItemId(tool))) {
            return false;
        }
        final Block origin = event.getBlock();
        final Player player = event.getPlayer();
        event.setCancelled(true);
        event.setDropItems(false);
        int broken = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    final Block target = origin.getWorld().getBlockAt(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    if (target.getType() == Material.AIR || target.getType() == Material.BEDROCK) {
                        continue;
                    }
                    if (target.getState() instanceof org.bukkit.block.Container) {
                        continue;
                    }
                    if (this.plugin.getMachineService().resolveManagedMachineBlock(target) != null) {
                        continue;
                    }
                    // ★ 跳過追蹤的科技方塊（古代祭壇、果樹等）
                    if (this.plugin.getPlacedTechBlockService().isTrackedBlock(target)) {
                        continue;
                    }
                    // ★ 含中心方塊在內逐格檢查領地保護
                    if (!this.canModifyBlock(player, target)) {
                        continue;
                    }
                    for (final ItemStack drop : target.getDrops(tool, player)) {
                        target.getWorld().dropItemNaturally(target.getLocation().add(0.5, 0.4, 0.5), drop);
                    }
                    target.getWorld().spawnParticle(Particle.BLOCK, target.getLocation().add(0.5, 0.5, 0.5), 6, 0.2, 0.2, 0.2, target.getBlockData());
                    target.setType(Material.AIR, true);
                    broken++;
                }
            }
        }
        if (broken > 0) {
            this.plugin.getPlayerProgressService().incrementStat(player.getUniqueId(), "blocks_mined", broken);
            // 額外破壞的方塊也要給採集經驗 + 推進每日挖礦任務
            // （原點方塊在 onBlockBreak 頂部已經計過 1 次，這裡補 broken-1 次）
            final int extras = broken - 1;
            if (extras > 0 && player.getGameMode() == GameMode.SURVIVAL) {
                if (this.plugin.getSkillService() != null) {
                    this.plugin.getSkillService().grantXp(player, "gathering", extras);
                }
                if (this.plugin.getDailyQuestService() != null) {
                    for (int i = 0; i < extras; i++) {
                        this.plugin.getDailyQuestService().onBlockMine(player);
                    }
                }
            }
            origin.getWorld().playSound(origin.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.4f);
            this.damageToolInHand(player, broken);
        }
        return true;
    }

    // ── 礦脈鎬：BFS 連鎖開採同類礦石 ──
    private boolean handleVeinMiningPickaxe(final BlockBreakEvent event) {
        final ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!"vein_mining_pickaxe".equalsIgnoreCase(this.plugin.getItemFactory().getTechItemId(tool))) {
            return false;
        }
        final Block origin = event.getBlock();
        if (!this.isOreBlock(origin.getType())) {
            return false;
        }
        final Player player = event.getPlayer();
        event.setCancelled(true);
        event.setDropItems(false);
        final Material oreType = origin.getType();
        final java.util.List<Block> vein = new java.util.ArrayList<>();
        final java.util.List<Block> frontier = new java.util.ArrayList<>();
        final java.util.Set<String> visited = new java.util.HashSet<>();
        frontier.add(origin);
        while (!frontier.isEmpty() && vein.size() < 64) {
            final Block current = frontier.remove(0);
            final String key = current.getX() + ":" + current.getY() + ":" + current.getZ();
            if (!visited.add(key) || current.getType() != oreType) {
                continue;
            }
            if (!this.canModifyBlock(player, current)) {
                continue;
            }
            if (this.plugin.getMachineService().resolveManagedMachineBlock(current) != null) {
                continue;
            }
            if (this.plugin.getPlacedTechBlockService().isTrackedBlock(current)) {
                continue;
            }
            vein.add(current);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) { continue; }
                        frontier.add(current.getWorld().getBlockAt(current.getX() + dx, current.getY() + dy, current.getZ() + dz));
                    }
                }
            }
        }
        int broken = 0;
        for (final Block block : vein) {
            for (final ItemStack drop : block.getDrops(tool, player)) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.4, 0.5), drop);
            }
            block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 6, 0.2, 0.2, 0.2, block.getBlockData());
            block.setType(Material.AIR, true);
            broken++;
        }
        if (broken > 0) {
            this.plugin.getPlayerProgressService().incrementStat(player.getUniqueId(), "blocks_mined", broken);
            // 注意：礦脈鎬連鎖方塊「不」額外給採集經驗，只算原點那 1 顆，避免刷經驗
            origin.getWorld().playSound(origin.getLocation(), Sound.BLOCK_STONE_BREAK, 0.6f, 1.2f);
            player.sendActionBar(this.plugin.getItemFactory().success("礦脈鎬連鎖開採 " + broken + " 塊！"));
            this.damageToolInHand(player, broken);
        }
        return true;
    }

    private boolean isOreBlock(final Material material) {
        final String name = material.name();
        return name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS");
    }

    private void damageToolInHand(final Player player, final int amount) {
        if (player.getGameMode() == GameMode.CREATIVE) { return; }
        final ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType().getMaxDurability() <= 0) { return; }
        if (!(tool.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable meta)) { return; }
        final int unbreaking = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        int actual = 0;
        for (int i = 0; i < amount; i++) {
            if (unbreaking <= 0 || ThreadLocalRandom.current().nextInt(unbreaking + 1) == 0) {
                actual++;
            }
        }
        if (actual <= 0) { return; }
        meta.setDamage(meta.getDamage() + actual);
        if (meta.getDamage() >= tool.getType().getMaxDurability()) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1.0f);
        } else {
            tool.setItemMeta(meta);
        }
    }

    // ── 熔煉鎬：挖掘時自動熔煉 ──
    private boolean handleSmeltersPickaxe(final BlockBreakEvent event) {
        final ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!"smelters_pickaxe".equalsIgnoreCase(this.plugin.getItemFactory().getTechItemId(tool))) {
            return false;
        }
        final Block origin = event.getBlock();
        if (origin.getType() == Material.AIR || origin.getType() == Material.BEDROCK) {
            return false;
        }
        final Player player = event.getPlayer();
        event.setCancelled(true);
        event.setDropItems(false);
        for (final ItemStack drop : origin.getDrops(tool, player)) {
            final Material smelted = this.smeltResult(drop.getType());
            if (smelted != null) {
                origin.getWorld().dropItemNaturally(origin.getLocation().add(0.5, 0.4, 0.5), new ItemStack(smelted, drop.getAmount()));
                origin.getWorld().spawnParticle(Particle.FLAME, origin.getLocation().add(0.5, 0.7, 0.5), 4, 0.15, 0.1, 0.15, 0.01);
            } else {
                origin.getWorld().dropItemNaturally(origin.getLocation().add(0.5, 0.4, 0.5), drop);
            }
        }
        origin.getWorld().spawnParticle(Particle.BLOCK, origin.getLocation().add(0.5, 0.5, 0.5), 6, 0.2, 0.2, 0.2, origin.getBlockData());
        origin.setType(Material.AIR, false);
        this.plugin.getPlayerProgressService().incrementStat(player.getUniqueId(), "blocks_mined", 1);
        this.damageToolInHand(player, 1);
        return true;
    }

    private Material smeltResult(final Material raw) {
        return switch (raw) {
            case RAW_IRON -> Material.IRON_INGOT;
            case RAW_GOLD -> Material.GOLD_INGOT;
            case RAW_COPPER -> Material.COPPER_INGOT;
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case COBBLESTONE -> Material.STONE;
            case SAND -> Material.GLASS;
            case CLAY_BALL -> Material.BRICK;
            case NETHERRACK -> Material.NETHER_BRICK;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            default -> null;
        };
    }

    // ── 伐木斧：BFS 連鎖砍倒整棵樹 ──
    private boolean handleLumberAxe(final BlockBreakEvent event) {
        final ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (!"lumber_axe".equalsIgnoreCase(this.plugin.getItemFactory().getTechItemId(tool))) {
            return false;
        }
        final Block origin = event.getBlock();
        if (!this.isLogBlock(origin.getType())) {
            return false;
        }
        final Player player = event.getPlayer();
        event.setCancelled(true);
        event.setDropItems(false);
        final Material logType = origin.getType();
        final java.util.List<Block> tree = new java.util.ArrayList<>();
        final java.util.List<Block> frontier = new java.util.ArrayList<>();
        final java.util.Set<String> visited = new java.util.HashSet<>();
        frontier.add(origin);
        while (!frontier.isEmpty() && tree.size() < 128) {
            final Block current = frontier.remove(0);
            final String key = current.getX() + ":" + current.getY() + ":" + current.getZ();
            if (!visited.add(key)) continue;
            if (!this.isLogBlock(current.getType())) continue;
            if (current != origin && !this.canModifyBlock(player, current)) continue;
            if (this.plugin.getMachineService().resolveManagedMachineBlock(current) != null) continue;
            tree.add(current);
            // 搜索上方 + 四周（原木通常往上長，也可能有分支）
            for (final int[] offset : new int[][]{{0,1,0},{0,-1,0},{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{1,1,0},{-1,1,0},{0,1,1},{0,1,-1}}) {
                frontier.add(current.getWorld().getBlockAt(current.getX()+offset[0], current.getY()+offset[1], current.getZ()+offset[2]));
            }
        }
        int broken = 0;
        for (final Block block : tree) {
            for (final ItemStack drop : block.getDrops(tool, player)) {
                block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.4, 0.5), drop);
            }
            block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 6, 0.2, 0.2, 0.2, block.getBlockData());
            block.setType(Material.AIR, true);
            broken++;
        }
        if (broken > 0) {
            this.plugin.getPlayerProgressService().incrementStat(player.getUniqueId(), "blocks_mined", broken);
            origin.getWorld().playSound(origin.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.7f, 0.8f);
            player.sendActionBar(this.plugin.getItemFactory().success("伐木斧連鎖砍伐 " + broken + " 根原木！"));
            this.damageToolInHand(player, broken);
        }
        return true;
    }

    private boolean isLogBlock(final Material material) {
        final String name = material.name();
        return name.endsWith("_LOG") || name.endsWith("_STEM") || name.endsWith("_WOOD") || name.endsWith("_HYPHAE");
    }

    private void refreshJetpackFlightState(final Player player) {
        if (!this.isManagedSurvivalFlight(player)) {
            this.managedJetpackFlight.remove(player.getUniqueId());
            return;
        }

        final UUID playerId = player.getUniqueId();
        if (!this.hasJetpackEquipped(player)) {
            if (this.managedJetpackFlight.remove(playerId) && player.getAllowFlight()) {
                player.setAllowFlight(false);
            }
            return;
        }

        final boolean ready = this.isCooldownReady(this.jetpackCooldowns, playerId);
        if (ready) {
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
            this.managedJetpackFlight.add(playerId);
            return;
        }

        if (this.managedJetpackFlight.contains(playerId) && player.getAllowFlight()) {
            player.setAllowFlight(false);
        }
    }

    private boolean hasJetpackEquipped(final Player player) {
        return STORM_JETPACK.equalsIgnoreCase(this.plugin.getItemFactory().getTechItemId(player.getInventory().getChestplate()));
    }

    private boolean isManagedSurvivalFlight(final Player player) {
        return player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
    }

    private boolean isCooldownReady(final Map<UUID, Long> cooldowns, final UUID playerId) {
        return cooldowns.getOrDefault(playerId, 0L) <= System.currentTimeMillis();
    }

    private void startCooldown(final Map<UUID, Long> cooldowns, final UUID playerId, final long durationMillis) {
        cooldowns.put(playerId, System.currentTimeMillis() + durationMillis);
    }

    private void markGracefulLanding(final Player player, final long durationMillis) {
        this.mobilityGracePeriods.put(player.getUniqueId(), System.currentTimeMillis() + durationMillis);
    }

    private boolean isStandingOnSolidGround(final Player player) {
        return player.getLocation().clone().subtract(0.0, 0.18, 0.0).getBlock().getType().isSolid();
    }

    private void spawnParticleLine(final Location start, final Location end, final Particle particle, final int points) {
        if (start.getWorld() == null || end.getWorld() == null || start.getWorld() != end.getWorld()) {
            return;
        }
        final Vector line = end.toVector().subtract(start.toVector());
        final int steps = Math.max(2, points);
        for (int index = 0; index <= steps; index++) {
            final double ratio = (double) index / (double) steps;
            final Location point = start.clone().add(line.clone().multiply(ratio));
            start.getWorld().spawnParticle(particle, point, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    private void giveStarterKitIfNeeded(final Player player) {
        final var progress = this.plugin.getPlayerProgressService();
        if (progress.getStat(player.getUniqueId(), "starter_kit_claimed") > 0L) {
            return;
        }
        final var machineItems = this.plugin.getConfig().getStringList("starting-kit.machine-items");
        final var itemItems = this.plugin.getConfig().getStringList("starting-kit.item-items");
        if (machineItems.isEmpty() && itemItems.isEmpty()) {
            return;
        }
        for (final String id : machineItems) {
            final var machine = this.plugin.getTechRegistry().getMachine(id);
            if (machine != null) {
                player.getInventory().addItem(this.plugin.getItemFactory().buildMachineItem(machine));
            }
        }
        for (final String id : itemItems) {
            final var item = this.plugin.getTechRegistry().getItem(id);
            if (item != null) {
                player.getInventory().addItem(this.plugin.getItemFactory().buildTechItem(item));
            }
        }
        progress.incrementStat(player.getUniqueId(), "starter_kit_claimed", 1L);
        player.sendMessage(this.plugin.getItemFactory().success("你收到新手科技包：先放太陽能發電機，再放粉碎機。"));
    }

    private boolean handleAdvancedWorkbenchClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }
        if (!(event.getView().getTopInventory() instanceof CraftingInventory craftingInventory)
                || event.getView().getTopInventory().getType() != InventoryType.WORKBENCH
                || event.getSlotType() != InventoryType.SlotType.RESULT) {
            return false;
        }
        final var match = this.plugin.getBlueprintService().matchCraftingMatrix(craftingInventory.getMatrix());
        if (match == null) {
            return false;
        }
        if (!this.plugin.getBlueprintService().isAdvancedWorkbench(craftingInventory.getLocation())) {
            event.setCancelled(true);
            player.sendActionBar(this.plugin.getItemFactory().danger("這是科技配方，請到鐵方塊底座的進階工作台製作。"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
            return true;
        }
        final ItemStack result = match.isItemBlueprint()
                ? this.plugin.getItemFactory().buildTechItem(match.item())
                : this.plugin.getItemFactory().buildMachineItem(match.machine());
        event.setCancelled(true);
        if (event.isShiftClick()) {
            // Shift-click：持續合成直到材料不足或背包滿（上限 64 次防無限迴圈）
            int crafted = 0;
            while (crafted < 64) {
                final ItemStack batchResult = match.isItemBlueprint()
                        ? this.plugin.getItemFactory().buildTechItem(match.item())
                        : this.plugin.getItemFactory().buildMachineItem(match.machine());
                if (!this.canStoreCraftResult(player, batchResult)) {
                    if (crafted == 0) {
                        player.sendMessage(this.plugin.getItemFactory().warning("背包滿了，無法從進階工作台取出成品。"));
                    }
                    break;
                }
                // 先消耗材料，再給物品（防止複製）
                final ItemStack[] mat = craftingInventory.getMatrix();
                for (int i = 0; i < mat.length; i++) {
                    final ItemStack ing = mat[i];
                    if (ing == null || ing.getType() == Material.AIR) continue;
                    if (ing.getAmount() <= 1) { mat[i] = null; }
                    else { ing.setAmount(ing.getAmount() - 1); mat[i] = ing; }
                }
                craftingInventory.setMatrix(mat);
                // 材料已消耗，給予成品（處理溢出）
                final java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(batchResult);
                for (final ItemStack leftover : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                }
                crafted++;
                // 檢查剩餘材料是否還能匹配配方
                final var next = this.plugin.getBlueprintService().matchCraftingMatrix(craftingInventory.getMatrix());
                if (next == null || !this.plugin.getBlueprintService().isAdvancedWorkbench(craftingInventory.getLocation())) {
                    break;
                }
            }
            // 更新結果欄
            final var afterMatch = this.plugin.getBlueprintService().matchCraftingMatrix(craftingInventory.getMatrix());
            if (afterMatch != null && this.plugin.getBlueprintService().isAdvancedWorkbench(craftingInventory.getLocation())) {
                craftingInventory.setResult(afterMatch.isItemBlueprint()
                        ? this.plugin.getItemFactory().buildTechItem(afterMatch.item())
                        : this.plugin.getItemFactory().buildMachineItem(afterMatch.machine()));
            } else {
                craftingInventory.setResult(null);
            }
            return true;
        } else {
            final ItemStack cursor = player.getItemOnCursor();
            if (cursor != null && cursor.getType() != Material.AIR) {
                if (!cursor.isSimilar(result) || cursor.getAmount() >= cursor.getMaxStackSize()) {
                    player.sendMessage(this.plugin.getItemFactory().warning("請先清空滑鼠上的物品，再從進階工作台取出成品。"));
                    return true;
                }
            }
            // ★ 先消耗材料，再給物品（防止 TOCTOU 複製）
            final ItemStack[] matrix = craftingInventory.getMatrix();
            for (int index = 0; index < matrix.length; index++) {
                final ItemStack ingredient = matrix[index];
                if (ingredient == null || ingredient.getType() == Material.AIR) {
                    continue;
                }
                if (ingredient.getAmount() <= 1) {
                    matrix[index] = null;
                } else {
                    ingredient.setAmount(ingredient.getAmount() - 1);
                    matrix[index] = ingredient;
                }
            }
            craftingInventory.setMatrix(matrix);
            // 材料已消耗，安全地給予產物
            if (cursor != null && cursor.getType() != Material.AIR) {
                cursor.setAmount(cursor.getAmount() + 1);
                player.setItemOnCursor(cursor);
            } else {
                player.setItemOnCursor(result);
            }
        }
        // matrix already consumed above
        final var nextMatch = this.plugin.getBlueprintService().matchCraftingMatrix(craftingInventory.getMatrix());
        if (nextMatch != null && this.plugin.getBlueprintService().isAdvancedWorkbench(craftingInventory.getLocation())) {
            craftingInventory.setResult(nextMatch.isItemBlueprint()
                    ? this.plugin.getItemFactory().buildTechItem(nextMatch.item())
                    : this.plugin.getItemFactory().buildMachineItem(nextMatch.machine()));
        } else {
            craftingInventory.setResult(null);
        }
        return true;
    }

    private boolean canStoreCraftResult(final Player player, final ItemStack result) {
        if (player.getInventory().firstEmpty() != -1) {
            return true;
        }
        for (final ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null || content.getType() == Material.AIR) {
                return true;
            }
            if (content.isSimilar(result) && content.getAmount() < content.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private boolean tryShowAdvancedWorkbenchHint(final Player player, final Block clickedBlock) {
        if (clickedBlock == null || clickedBlock.getType() != Material.CRAFTING_TABLE) {
            return false;
        }
        if (!this.plugin.getBlueprintService().isAdvancedWorkbench(clickedBlock.getLocation())) {
            return false;
        }
        player.sendActionBar(this.plugin.getItemFactory().success("進階工作台：下方鐵方塊已就緒，可製作科技藍圖。"));
        this.playAdvancedWorkbenchEffect(clickedBlock.getLocation().add(0.5, 0.85, 0.5));
        return false;
    }

    private void playAdvancedWorkbenchEffect(final Location location) {
        location.getWorld().spawnParticle(Particle.END_ROD, location, 8, 0.25, 0.2, 0.25, 0.01);
        location.getWorld().spawnParticle(Particle.WAX_ON, location, 12, 0.3, 0.15, 0.3, 0.0);
        location.getWorld().playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.25f);
        location.getWorld().playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.5f);
    }

    private boolean handleProtectedWorkbenchResultClick(final InventoryClickEvent event) {
        if (!(event.getView().getTopInventory() instanceof CraftingInventory)
                || event.getSlotType() != InventoryType.SlotType.RESULT) {
            return false;
        }
        final ItemStack current = event.getCurrentItem();
        if (!this.isProtectedWorkbenchResult(current)) {
            return false;
        }
        event.setCancelled(true);
        return true;
    }

    private boolean isProtectedWorkbenchResult(final ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }
        if (this.plugin.getItemFactory().isGuiPlaceholder(stack)) {
            return true;
        }
        if (stack.getType() != Material.BARRIER || !stack.hasItemMeta()) {
            return false;
        }
        final ItemMeta meta = stack.getItemMeta();
        final String title = meta.displayName() == null
                ? ""
                : PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        return title.contains("缺少進階工作台結構")
                || title.contains("尚未解鎖此機器")
                || title.contains("科技材料不可用於原版配方")
                || title.contains("此配方不屬於進階工作台");
    }

    private ItemStack invalidAdvancedWorkbenchResult(final String resultName) {
        final ItemStack stack = this.plugin.getItemFactory().tagGuiPlaceholder(new ItemStack(Material.BARRIER));
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.plugin.getItemFactory().danger("缺少進階工作台結構"));
        meta.lore(List.of(
                this.plugin.getItemFactory().muted("這份配方屬於科技合成"),
                this.plugin.getItemFactory().muted("請先在工作台下方放一個鐵方塊"),
                this.plugin.getItemFactory().muted("完成後才能製作：" + resultName)
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack lockedMachineResult(final String resultName) {
        final ItemStack stack = this.plugin.getItemFactory().tagGuiPlaceholder(new ItemStack(Material.BARRIER));
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.plugin.getItemFactory().danger("尚未解鎖此機器"));
        meta.lore(List.of(
                this.plugin.getItemFactory().muted("這份藍圖已存在，但研究進度尚未到達"),
                this.plugin.getItemFactory().muted("請先解鎖後再製作：" + resultName)
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack isolatedTechMaterialResult() {
        final ItemStack stack = this.plugin.getItemFactory().tagGuiPlaceholder(new ItemStack(Material.BARRIER));
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.plugin.getItemFactory().danger("科技材料不可用於原版配方"));
        meta.lore(List.of(
                this.plugin.getItemFactory().muted("帶有科技標記的物品已與原版合成鏈分離"),
                this.plugin.getItemFactory().muted("請改用科技機器或對應科技藍圖處理")
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack machineItemMisuseResult(final java.util.List<String> misusedNames) {
        final ItemStack stack = this.plugin.getItemFactory().tagGuiPlaceholder(new ItemStack(Material.BARRIER));
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.plugin.getItemFactory().danger("放入了科技機器，但配方要原版方塊"));
        final java.util.List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
        lore.add(this.plugin.getItemFactory().muted("你放的是科技機器物品："));
        for (final String name : misusedNames) {
            lore.add(this.plugin.getItemFactory().muted("  ▸ " + name + "（科技版）"));
        }
        lore.add(this.plugin.getItemFactory().muted("配方需要的是原版方塊（用圓石等原版材料製作的）"));
        lore.add(this.plugin.getItemFactory().muted("請用原版合成台 crafting 取得後再放入"));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack invalidTechProcessingResult() {
        final ItemStack stack = this.plugin.getItemFactory().tagGuiPlaceholder(new ItemStack(Material.BARRIER));
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(this.plugin.getItemFactory().danger("此配方不屬於進階工作台"));
        meta.lore(List.of(
                this.plugin.getItemFactory().muted("這些科技材料不是九宮格工作台配方"),
                this.plugin.getItemFactory().muted("請改到對應的科技機器製作，例如製造機或進階製造機"),
                this.plugin.getItemFactory().muted("可從科技書查看每個材料的製作站")
        ));
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean containsTaggedTechMaterial(final ItemStack[] matrix) {
        if (matrix == null) {
            return false;
        }
        for (final ItemStack stack : matrix) {
            if (this.isTaggedTechMaterial(stack)) {
                return true;
            }
        }
        return false;
    }

    private boolean isTaggedTechMaterial(final ItemStack stack) {
        return this.plugin.getItemFactory().getTechItemId(stack) != null
                || this.plugin.getItemFactory().getMachineId(stack) != null;
    }

    /**
     * 掃描玩家背包，自動刷新版本過舊的科技物品的 displayName + lore。
     */
    private void refreshPlayerInventory(final Player player) {
        if (!player.isOnline()) {
            return;
        }
        int refreshed = 0;
        for (final ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            if (this.plugin.getItemFactory().refreshTechItemIfNeeded(stack)) {
                refreshed++;
            }
        }
        if (refreshed > 0 && this.plugin.getConfig().getBoolean("item-refresh-notify", false)) {
            player.sendActionBar(this.plugin.getItemFactory().muted("已自動更新 " + refreshed + " 個科技物品的外觀。"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(final EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        this.applyEquipmentDamageReduction(player, event);
        this.handleTalismanOnDamage(player, event);
        this.handleQuickReactionTalent(player);
    }

    /**
     * 快速反應天賦（探索樹 MINOR_RIGHT_R）：受到傷害時獲得迅捷 I，每級 2 秒。
     * 原為被動爆擊率，改成主動觸發型以契合「反應」的概念。
     */
    private void handleQuickReactionTalent(final Player player) {
        final com.rui.techproject.service.talent.TalentService talent =
                this.plugin.getTalentService();
        if (talent == null) return;
        final int rank = talent.getRank(player.getUniqueId(),
                com.rui.techproject.service.SkillService.Skill.EXPLORATION,
                com.rui.techproject.service.talent.TalentSlot.MINOR_RIGHT_R);
        if (rank <= 0) return;
        final int durationTicks = rank * 2 * 20; // 每級 2 秒
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                durationTicks, 0, true, true, true));
    }

    /**
     * 星球怪物 PVE 保護繞過 — 在 LOW 優先級取消 Residence 等保護插件的攔截。
     * 允許：玩家攻擊星球怪物 / 星球怪物攻擊玩家。
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onPlanetMobDamageBypass(final EntityDamageByEntityEvent event) {
        // 玩家 → 星球怪物
        if (event.getDamager() instanceof Player && event.getEntity() instanceof Monster mob) {
            if (this.plugin.getPlanetService().isPlanetWorld(mob.getWorld())) {
                event.setCancelled(false);
            }
        }
        // 星球怪物 → 玩家
        if (event.getDamager() instanceof Monster mob && event.getEntity() instanceof Player) {
            if (this.plugin.getPlanetService().isPlanetWorld(mob.getWorld())) {
                event.setCancelled(false);
            }
        }
    }

    // ── Nexo 機器 Interaction hitbox 被攻擊（左鍵）：扳手切換 / 蹲下拆除 ──
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onHitboxAttack(final EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof org.bukkit.entity.Interaction interaction)) return;
        if (!interaction.getScoreboardTags().contains("techproject_hitbox")) return;
        event.setCancelled(true);
        final Block block = interaction.getLocation().getBlock();
        final Block machineBlock = this.plugin.getMachineService().resolveManagedMachineBlock(block);
        if (machineBlock == null) return;
        if (!this.plugin.getMachineService().canModifyMachine(player, machineBlock, false)) return;
        final ItemStack hand = player.getInventory().getItemInMainHand();
        // 扳手左鍵 → 切換開/關機
        if (this.plugin.getItemFactory().hasWrenchTag(hand)) {
            final PlacedMachine pm = this.plugin.getMachineService().placedMachineAt(machineBlock);
            if (pm != null) {
                final boolean newEnabled = !pm.enabled();
                this.plugin.getMachineService().setMachineEnabled(machineBlock, newEnabled);
                player.sendActionBar(newEnabled
                        ? this.plugin.getItemFactory().success("⚙ 機器已啟動")
                        : this.plugin.getItemFactory().warning("⚙ 機器已暫停"));
                player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.6f, newEnabled ? 1.2f : 0.8f);
            }
            return;
        }
        // 左鍵攻擊 hitbox → 拆除機器（BARRIER 無法正常挖掘）
        if (!this.plugin.getMachineService().canModifyMachine(player, machineBlock, true)) return;
        this.handleWrenchDismantle(player, machineBlock);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(final EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker) {
            this.handleTalismanOnAttack(attacker, event);
            this.handleVampireBlade(attacker, event);
            // 全域戰鬥經驗：對怪物造成傷害 +1
            if (event.getEntity() instanceof org.bukkit.entity.Monster
                    && this.plugin.getSkillService() != null) {
                this.plugin.getSkillService().grantXp(attacker, "combat", 1L);
            }
            // ── 套用屬性系統：力量加成 + 暴擊率/暴擊傷害 ──
            if (event.getEntity() instanceof LivingEntity victim
                    && this.plugin.getSkillService() != null) {
                final double baseDmg = event.getDamage();
                final com.rui.techproject.service.SkillService sk = this.plugin.getSkillService();
                final java.util.UUID uuid = attacker.getUniqueId();
                final double strength = sk.getStatValue(uuid, com.rui.techproject.service.SkillService.Stat.STRENGTH);
                final double critChance = sk.getStatValue(uuid, com.rui.techproject.service.SkillService.Stat.CRIT_CHANCE);
                final double critDamage = sk.getStatValue(uuid, com.rui.techproject.service.SkillService.Stat.CRIT_DAMAGE);
                // 力量加成
                double finalDmg = baseDmg * (1.0 + strength / 100.0);
                // 影襲必爆擊判定
                final boolean shadowCrit = this.plugin.getSpellExecutor() != null
                        && this.plugin.getSpellExecutor().consumeShadowCrit(uuid);
                final boolean isCrit = shadowCrit || Math.random() * 100.0 < critChance;
                if (isCrit) {
                    finalDmg *= (1.0 + critDamage / 100.0);
                }
                event.setDamage(finalDmg);
                // 浮動傷害數字
                final double displayDmg = finalDmg;
                final String text = (isCrit ? "✦ " : "♥ ")
                        + String.format("%.1f", displayDmg);
                final net.kyori.adventure.text.format.TextColor color = isCrit
                        ? net.kyori.adventure.text.format.TextColor.color(0xFF8800)
                        : net.kyori.adventure.text.format.TextColor.color(0xFF4444);
                final net.kyori.adventure.text.Component comp =
                        net.kyori.adventure.text.Component.text(text, color)
                                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, isCrit)
                                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
                com.rui.techproject.util.FloatingTextUtil.spawnAbove(
                        victim.getEyeLocation(), comp,
                        isCrit ? 0.6f : 0.45f,
                        this.plugin.getSafeScheduler());
                // 暴擊音效回饋
                if (isCrit) {
                    attacker.playSound(attacker.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT,
                            org.bukkit.SoundCategory.PLAYERS, 0.6f, 1.2f);
                }
            }
        }
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof LivingEntity attk) {
            this.handleTalismanWhirlwind(victim);
            this.handleEquipmentThorns(victim, event);
            // 止水：反射傷害
            if (this.plugin.getSpellExecutor() != null
                    && this.plugin.getSpellExecutor().isDeadCalmActive(victim.getUniqueId())) {
                final double reflectDmg = event.getDamage() * 0.5;
                if (!attk.equals(victim)) {
                    this.plugin.getSafeScheduler().runEntity(attk, () -> attk.damage(reflectDmg, victim));
                    attk.getWorld().spawnParticle(Particle.ENCHANTED_HIT,
                            attk.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.1);
                }
            }
        }
        // 虛空之鏡 — 反彈投射物（限制最多反彈 3 次，防止雙方鏡面無限迴圈）
        if (event.getEntity() instanceof Player victim && event.getDamager() instanceof Projectile projectile) {
            final Long expiry = this.voidMirrorActive.get(victim.getUniqueId());
            if (expiry != null && System.currentTimeMillis() < expiry) {
                // 檢查反彈次數
                final org.bukkit.NamespacedKey bounceKey = new org.bukkit.NamespacedKey(this.plugin, "mirror_bounces");
                final int bounces = projectile.getPersistentDataContainer()
                        .getOrDefault(bounceKey, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
                if (bounces >= 3) {
                    // 超過上限，不再反彈，正常受傷
                } else {
                    event.setCancelled(true);
                    projectile.remove();
                    if (projectile.getShooter() instanceof LivingEntity shooter) {
                        final org.bukkit.util.Vector direction = shooter.getLocation().toVector()
                                .subtract(victim.getLocation().toVector()).normalize().multiply(projectile.getVelocity().length());
                        final Projectile reflected = victim.launchProjectile(projectile.getClass(), direction);
                        reflected.setShooter(victim);
                        reflected.getPersistentDataContainer().set(bounceKey,
                                org.bukkit.persistence.PersistentDataType.INTEGER, bounces + 1);
                        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.7f, 1.6f);
                        victim.getWorld().spawnParticle(Particle.END_ROD, victim.getLocation().add(0, 1, 0), 8, 0.3, 0.3, 0.3, 0.02);
                    } else {
                        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.7f, 1.6f);
                    }
                }
            }
        }
        // 精英技能觸發 — 怪物攻擊玩家時
        if (event.getDamager() instanceof Monster monster && event.getEntity() instanceof Player target) {
            this.plugin.getPlanetService().handleEliteSkillOnAttack(monster, target);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeathForHunter(final EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        final Player killer = event.getEntity().getKiller();
        if (!this.hasTalismanInInventory(killer, "talisman_hunter")) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= 0.25) {
            return;
        }
        final List<ItemStack> drops = event.getDrops();
        final List<ItemStack> bonus = new java.util.ArrayList<>();
        for (final ItemStack drop : drops) {
            if (drop != null && drop.getType() != Material.AIR) {
                bonus.add(drop.clone());
            }
        }
        drops.addAll(bonus);
        killer.sendActionBar(this.plugin.getItemFactory().success("獵人護符觸發—雙倍戰利品！"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobDeathForBeheading(final EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        final Player killer = event.getEntity().getKiller();
        final ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (!"beheading_sword".equalsIgnoreCase(this.plugin.getItemFactory().getTechItemId(weapon))) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= 0.40) {
            return;
        }
        final Material headMat = this.headForEntity(event.getEntity());
        if (headMat == null) {
            return;
        }
        event.getDrops().add(new ItemStack(headMat, 1));
        killer.sendActionBar(this.plugin.getItemFactory().success("斬首劍觸發—獲得頭顱！"));
        killer.playSound(killer.getLocation(), Sound.ENTITY_WITHER_HURT, 0.5f, 1.3f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamageForAnvil(final PlayerItemDamageEvent event) {
        final Player player = event.getPlayer();
        final ItemStack item = event.getItem();
        if (item.getType().getMaxDurability() <= 0) {
            return;
        }
        if (!(item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable damageable)) {
            return;
        }
        final int remaining = item.getType().getMaxDurability() - damageable.getDamage();
        if (remaining > 2) {
            return;
        }
        if (!this.hasTalismanInInventory(player, "talisman_anvil")) {
            return;
        }
        if (!this.isTalismanCooldownReady(player, "talisman_anvil")) {
            return;
        }
        event.setCancelled(true);
        this.startTalismanCooldown(player, "talisman_anvil", 120_000L);
        player.sendActionBar(this.plugin.getItemFactory().success("鐵砧護符觸發—工具已被保護！"));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerToggleSneakForTraveler(final PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        final Player player = event.getPlayer();
        if (!player.isSprinting()) {
            return;
        }
        this.handleTalismanTraveler(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMoveForEquipment(final PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }
        final Player player = event.getPlayer();
        final long now = System.currentTimeMillis();
        if (now - this.lastEquipmentTick.getOrDefault(player.getUniqueId(), 0L) < 3000L) {
            return;
        }
        this.lastEquipmentTick.put(player.getUniqueId(), now);
        this.applyEquipmentPassiveEffects(player);
        this.handleTalismanPassiveChecks(player);
        this.handleMagnetTick(player);
        this.handlePortableChargerTick(player);
        this.handleSolarHelmetTick(player);
    }

    private boolean tryUseArtifact(final PlayerInteractEvent event) {
        if (event.getItem() == null || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return false;
        }
        final String techItemId = this.plugin.getItemFactory().getTechItemId(event.getItem());
        if (techItemId == null || !ARTIFACT_IDS.contains(techItemId.toLowerCase())) {
            return false;
        }
        /* 蹲下 + 右鍵方塊 → 跳過法器技能，讓充電邏輯接手 */
        if (event.getPlayer().isSneaking() && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            return false;
        }
        final Player player = event.getPlayer();
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);

        final String id = techItemId.toLowerCase();
        if (!this.isArtifactCooldownReady(player, id)) {
            player.sendActionBar(this.plugin.getItemFactory().warning("法器冷卻中。"));
            return true;
        }

        final long energyCost = this.artifactEnergyCost(id);
        final ItemStack held = event.getItem();
        final long energy = this.plugin.getItemFactory().getItemStoredEnergy(held);
        if (energy < energyCost) {
            player.sendActionBar(this.plugin.getItemFactory().warning("法器電量不足，請對準儲能機器 蹲下 + 右鍵充電。"));
            return true;
        }

        this.executeArtifactAbility(player, held, id);
        this.plugin.getItemFactory().drainItemEnergy(held, energyCost);
        this.startArtifactCooldown(player, id, this.artifactCooldownMs(id));

        final long remaining = this.plugin.getItemFactory().getItemStoredEnergy(held);
        final long max = this.plugin.getItemFactory().maxItemEnergy(id);
        player.sendActionBar(this.plugin.getItemFactory().success(
                this.plugin.getItemFactory().displayNameForId(id) + " 已發動！ ⚡ " + remaining + "/" + max));
        return true;
    }

    private void executeArtifactAbility(final Player player, final ItemStack held, final String id) {
        final Location loc = player.getLocation();
        switch (id) {
            case "pulse_staff" -> {
                for (final Entity entity : player.getNearbyEntities(8, 4, 8)) {
                    if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                        final Vector push = entity.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(2.2);
                        push.setY(0.5);
                        living.setVelocity(push);
                    }
                }
                loc.getWorld().playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.4f);
                loc.getWorld().spawnParticle(Particle.SONIC_BOOM, loc.add(0, 1, 0), 1);
            }
            case "storm_staff" -> {
                final RayTraceResult ray = player.rayTraceBlocks(48, FluidCollisionMode.NEVER);
                final Location target = ray != null && ray.getHitBlock() != null
                        ? ray.getHitBlock().getLocation()
                        : player.getLocation().add(player.getLocation().getDirection().multiply(20));
                target.getWorld().strikeLightning(target);
            }
            case "gravity_staff" -> {
                for (final Entity entity : player.getNearbyEntities(8, 4, 8)) {
                    if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                        living.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20 * 4, 1, false, true, true));
                    }
                }
                loc.getWorld().playSound(loc, Sound.ENTITY_SHULKER_SHOOT, 0.8f, 0.7f);
                loc.getWorld().spawnParticle(Particle.END_ROD, loc.add(0, 1, 0), 30, 3, 2, 3, 0.02);
            }
            case "warp_orb" -> {
                final Vector direction = player.getLocation().getDirection().normalize();
                Location destination = player.getLocation().clone();
                for (int i = 1; i <= 8; i++) {
                    final Location check = player.getLocation().clone().add(direction.clone().multiply(i));
                    if (check.getBlock().getType().isSolid()) {
                        break;
                    }
                    destination = check;
                }
                destination.setYaw(player.getLocation().getYaw());
                destination.setPitch(player.getLocation().getPitch());
                player.teleportAsync(destination);
                player.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.2f);
                player.getWorld().spawnParticle(Particle.PORTAL, loc, 30, 0.5, 1, 0.5, 0.1);
                player.getWorld().spawnParticle(Particle.PORTAL, destination, 30, 0.5, 1, 0.5, 0.1);
            }
            case "cryo_wand" -> {
                for (final Entity entity : player.getNearbyEntities(8, 4, 8)) {
                    if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 5, 2, false, true, true));
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 5, 0, false, true, true));
                        living.setFreezeTicks(20 * 5);
                    }
                }
                loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.5f);
                loc.getWorld().spawnParticle(Particle.SNOWFLAKE, loc.add(0, 1, 0), 40, 4, 2, 4, 0.02);
            }
            case "plasma_lance" -> {
                final Vector dir = player.getEyeLocation().getDirection().normalize();
                final Location start = player.getEyeLocation();
                for (int i = 1; i <= 16; i++) {
                    final Location point = start.clone().add(dir.clone().multiply(i));
                    point.getWorld().spawnParticle(Particle.FLAME, point, 3, 0.05, 0.05, 0.05, 0.01);
                    for (final Entity entity : point.getWorld().getNearbyEntities(point, 0.8, 0.8, 0.8)) {
                        if (entity instanceof LivingEntity living && entity != player) {
                            living.damage(12.0, player);
                        }
                    }
                    if (point.getBlock().getType().isSolid()) {
                        break;
                    }
                }
                loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 0.9f, 1.6f);
            }
            case "void_mirror" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 5, 2, false, true, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 5, 1, false, true, true));
                this.voidMirrorActive.put(player.getUniqueId(), System.currentTimeMillis() + 5000L);
                loc.getWorld().playSound(loc, Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.3f);
                loc.getWorld().spawnParticle(Particle.END_ROD, loc.add(0, 1, 0), 20, 1, 1, 1, 0.02);
            }
            case "time_dilator" -> {
                for (final Entity entity : player.getNearbyEntities(8, 4, 8)) {
                    if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 6, 3, false, true, true));
                        living.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 20 * 6, 1, false, true, true));
                    }
                }
                loc.getWorld().playSound(loc, Sound.BLOCK_BELL_USE, 0.7f, 0.5f);
                loc.getWorld().spawnParticle(Particle.ENCHANT, loc.add(0, 1, 0), 40, 4, 2, 4, 0.1);
            }
            case "heal_beacon" -> {
                final double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
                player.setHealth(Math.min(maxHealth, player.getHealth() + 12.0));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 10, 1, false, true, true));
                loc.getWorld().playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
                loc.getWorld().spawnParticle(Particle.HEART, loc.add(0, 1.5, 0), 12, 0.5, 0.3, 0.5, 0.01);
            }
            case "entropy_scepter" -> {
                for (final Entity entity : player.getNearbyEntities(6, 3, 6)) {
                    if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                        living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 20 * 4, 1, false, true, true));
                        living.damage(8.0, player);
                    }
                }
                loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_AMBIENT, 0.7f, 1.3f);
                loc.getWorld().spawnParticle(Particle.SMOKE, loc.add(0, 1, 0), 30, 3, 1.5, 3, 0.03);
            }
            // ═══ 異世界 RPG 武器技能 ═══
            case "riftblade" -> {
                // 裂空斬：扇形能量波，劈開 6 格內的敵人
                final Vector dir = player.getEyeLocation().getDirection().setY(0).normalize();
                final Location origin = player.getEyeLocation();
                for (int i = 1; i <= 6; i++) {
                    final Location point = origin.clone().add(dir.clone().multiply(i));
                    for (double offset = -1.5; offset <= 1.5; offset += 0.3) {
                        final Vector side = new Vector(-dir.getZ(), 0, dir.getX()).multiply(offset);
                        final Location particlePt = point.clone().add(side);
                        particlePt.getWorld().spawnParticle(Particle.SWEEP_ATTACK, particlePt, 1, 0, 0, 0, 0);
                        particlePt.getWorld().spawnParticle(Particle.END_ROD, particlePt, 2, 0.05, 0.05, 0.05, 0.01);
                    }
                    for (final Entity entity : point.getWorld().getNearbyEntities(point, 1.6, 1.6, 1.6)) {
                        if (entity instanceof LivingEntity living && entity != player) {
                            living.damage(14.0, player);
                            // 附模「空間裂痕」20% 機率吸入
                            if (Math.random() < 0.20) {
                                living.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20, 2, false, true, true));
                                living.getWorld().spawnParticle(Particle.PORTAL, living.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
                            }
                        }
                    }
                }
                loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 0.7f);
                loc.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 1.4f);
            }
            case "frostfang_bow" -> {
                // 霜淵漩渦：10 格內冰封 + 額外傷害
                for (final Entity entity : player.getNearbyEntities(10, 5, 10)) {
                    if (entity instanceof LivingEntity living && entity != player) {
                        living.setFreezeTicks(20 * 6);
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 6, 3, false, true, true));
                        // 附模「永霜共鳴」+25% 傷害
                        living.damage(10.0 * 1.25, player);
                    }
                }
                // 視覺：螺旋冰暴
                for (int ring = 0; ring < 3; ring++) {
                    for (int angle = 0; angle < 360; angle += 12) {
                        final double rad = Math.toRadians(angle);
                        final double radius = 3.0 + ring * 1.5;
                        final Location pt = loc.clone().add(Math.cos(rad) * radius, 0.5 + ring * 0.4, Math.sin(rad) * radius);
                        pt.getWorld().spawnParticle(Particle.SNOWFLAKE, pt, 1, 0, 0, 0, 0);
                        pt.getWorld().spawnParticle(Particle.ITEM_SNOWBALL, pt, 1, 0, 0, 0, 0);
                    }
                }
                loc.getWorld().playSound(loc, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.6f);
                loc.getWorld().playSound(loc, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.9f, 1.1f);
            }
            case "thunder_hammer" -> {
                // 天雷震擊：準心 12 格前落雷 + 擊退
                final RayTraceResult ray = player.rayTraceBlocks(16, FluidCollisionMode.NEVER);
                final Location target;
                if (ray != null && ray.getHitBlock() != null) {
                    target = ray.getHitBlock().getLocation().add(0.5, 1, 0.5);
                } else {
                    target = player.getLocation().add(player.getLocation().getDirection().multiply(12));
                }
                target.getWorld().strikeLightningEffect(target);
                target.getWorld().strikeLightningEffect(target);
                for (final Entity entity : target.getWorld().getNearbyEntities(target, 5, 4, 5)) {
                    if (entity instanceof LivingEntity living && entity != player) {
                        living.damage(18.0, player);
                        // 附模「雷霆怒吼」短暫麻痺（以 SLOWNESS IV + MINING_FATIGUE 模擬）
                        living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 2, 4, false, true, true));
                        living.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 20 * 2, 3, false, true, true));
                        final Vector push = living.getLocation().toVector().subtract(target.toVector()).normalize().multiply(1.4);
                        push.setY(0.7);
                        living.setVelocity(push);
                    }
                }
                target.getWorld().spawnParticle(Particle.EXPLOSION, target, 3, 1, 0.5, 1, 0);
                target.getWorld().spawnParticle(Particle.FIREWORK, target, 40, 2, 1, 2, 0.2);
                loc.getWorld().playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 0.8f);
            }
            case "arcane_scepter" -> {
                // 奧術三連擊：沿視線發射 3 道穿透彈幕
                final Vector dir = player.getEyeLocation().getDirection().normalize();
                final Location start = player.getEyeLocation();
                final double[] spreadAngles = {-8.0, 0.0, 8.0};
                int hitCountTotal = 0;
                for (final double spread : spreadAngles) {
                    final double rad = Math.toRadians(spread);
                    final Vector bolt = new Vector(
                        dir.getX() * Math.cos(rad) - dir.getZ() * Math.sin(rad),
                        dir.getY(),
                        dir.getX() * Math.sin(rad) + dir.getZ() * Math.cos(rad)
                    ).normalize();
                    int pierced = 0;
                    for (int step = 1; step <= 20 && pierced < 3; step++) {
                        final Location pt = start.clone().add(bolt.clone().multiply(step));
                        pt.getWorld().spawnParticle(Particle.END_ROD, pt, 2, 0.02, 0.02, 0.02, 0.01);
                        pt.getWorld().spawnParticle(Particle.WITCH, pt, 1, 0.05, 0.05, 0.05, 0);
                        if (pt.getBlock().getType().isSolid()) {
                            break;
                        }
                        for (final Entity entity : pt.getWorld().getNearbyEntities(pt, 0.9, 0.9, 0.9)) {
                            if (entity instanceof LivingEntity living && entity != player) {
                                // 附模「秘銀共振」每命中一次傷害 +1.5
                                final double stackBonus = hitCountTotal * 1.5;
                                living.damage(6.0 + stackBonus, player);
                                hitCountTotal++;
                                pierced++;
                                if (pierced >= 3) break;
                            }
                        }
                    }
                }
                loc.getWorld().playSound(loc, Sound.ENTITY_EVOKER_CAST_SPELL, 0.9f, 1.4f);
                loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.6f);
            }
            // ═══ 迷途星 RPG 武器技能 ═══
            case "maze_blade" -> {
                // 回聲震擊：向前方揮出一道深淵聲波，擊飛 + 暗黑共振
                final Vector dir = player.getEyeLocation().getDirection().setY(0).normalize();
                final Location origin = player.getEyeLocation();
                // 波形擴張粒子：3 段弧線（wynncraft 風格）
                for (int phase = 0; phase < 3; phase++) {
                    final double phaseRadius = 2.5 + phase * 1.5;
                    for (int angle = -60; angle <= 60; angle += 6) {
                        final double rad = Math.toRadians(angle);
                        final double cx = dir.getX() * Math.cos(rad) - dir.getZ() * Math.sin(rad);
                        final double cz = dir.getX() * Math.sin(rad) + dir.getZ() * Math.cos(rad);
                        final Location pt = origin.clone().add(cx * phaseRadius, 0.2 + phase * 0.15, cz * phaseRadius);
                        pt.getWorld().spawnParticle(Particle.SONIC_BOOM, pt, 1, 0.0, 0.0, 0.0, 0.0);
                        pt.getWorld().spawnParticle(Particle.SCULK_SOUL, pt, 2, 0.05, 0.15, 0.05, 0.015);
                        pt.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, pt, 1, 0.05, 0.1, 0.05, 0.01);
                    }
                }
                // 傷害判定：前方 6 格內
                final Location hitOrigin = player.getLocation().add(dir.clone().multiply(3));
                for (final Entity entity : hitOrigin.getWorld().getNearbyEntities(hitOrigin, 4.5, 3.0, 4.5)) {
                    if (entity instanceof LivingEntity living && entity != player) {
                        living.damage(13.0, player);
                        // 深淵拉扯：把目標往玩家視線方向推飛
                        final Vector push = dir.clone().multiply(1.1);
                        push.setY(0.45);
                        living.setVelocity(push);
                        // 附模：回聲共鳴 — 30% 機率施加短暫盲視 + 緩速
                        if (Math.random() < 0.30) {
                            living.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, true, true));
                            living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, true, true));
                            living.getWorld().spawnParticle(Particle.REVERSE_PORTAL, living.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);
                        }
                    }
                }
                loc.getWorld().playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 0.9f);
                loc.getWorld().playSound(loc, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.8f, 0.7f);
            }
            case "echo_crossbow" -> {
                // 回聲連射：1 秒內射出 5 發穿透箭矢（彌補原版十字弩只能單發）
                final Location shootOrigin = player.getEyeLocation();
                final java.util.UUID uuid = player.getUniqueId();
                for (int i = 0; i < 5; i++) {
                    final int shotIndex = i;
                    this.plugin.getSafeScheduler().runEntityDelayed(player, () -> {
                        if (!player.isOnline() || !player.isValid()) {
                            return;
                        }
                        final Vector shootDir = player.getEyeLocation().getDirection().normalize();
                        // 隨機散佈（小幅度，避免亂射）
                        final double jitterX = (Math.random() - 0.5) * 0.08;
                        final double jitterY = (Math.random() - 0.5) * 0.08;
                        final double jitterZ = (Math.random() - 0.5) * 0.08;
                        final Vector finalDir = shootDir.add(new Vector(jitterX, jitterY, jitterZ)).normalize().multiply(2.6);
                        final org.bukkit.entity.Arrow arrow = player.launchProjectile(org.bukkit.entity.Arrow.class, finalDir);
                        arrow.setShooter(player);
                        arrow.setDamage(5.5);
                        arrow.setPierceLevel(2);
                        arrow.setCritical(true);
                        arrow.setPersistent(false);
                        arrow.getPersistentDataContainer().set(
                                new org.bukkit.NamespacedKey(this.plugin, "echo_arrow"),
                                org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                        // 發射粒子與音效
                        final Location muzzle = player.getEyeLocation().add(shootDir.clone().multiply(0.8));
                        muzzle.getWorld().spawnParticle(Particle.CRIT, muzzle, 6, 0.1, 0.1, 0.1, 0.02);
                        muzzle.getWorld().spawnParticle(Particle.SCULK_CHARGE_POP, muzzle, 3, 0.05, 0.05, 0.05, 0.01);
                        player.getWorld().playSound(muzzle, Sound.ITEM_CROSSBOW_SHOOT, 0.6f, 1.2f + shotIndex * 0.05f);
                    }, i * 4L);  // 每 4 tick 一發，5 發共 0.8 秒
                }
                loc.getWorld().playSound(loc, Sound.ITEM_CROSSBOW_LOADING_START, 0.7f, 1.6f);
            }
        }
    }

    private long artifactEnergyCost(final String id) {
        return switch (id) {
            case "pulse_staff" -> 5L;
            case "storm_staff" -> 8L;
            case "gravity_staff" -> 6L;
            case "warp_orb" -> 10L;
            case "cryo_wand" -> 6L;
            case "plasma_lance" -> 8L;
            case "void_mirror" -> 10L;
            case "time_dilator" -> 8L;
            case "heal_beacon" -> 12L;
            case "entropy_scepter" -> 10L;
            case "riftblade" -> 14L;
            case "frostfang_bow" -> 12L;
            case "thunder_hammer" -> 16L;
            case "arcane_scepter" -> 14L;
            case "maze_blade" -> 12L;
            case "echo_crossbow" -> 10L;
            default -> 0L;
        };
    }

    private long artifactCooldownMs(final String id) {
        return switch (id) {
            case "pulse_staff" -> 3000L;
            case "storm_staff" -> 5000L;
            case "gravity_staff" -> 4000L;
            case "warp_orb" -> 2000L;
            case "cryo_wand" -> 4000L;
            case "plasma_lance" -> 3000L;
            case "void_mirror" -> 8000L;
            case "time_dilator" -> 6000L;
            case "heal_beacon" -> 10000L;
            case "entropy_scepter" -> 5000L;
            case "riftblade" -> 4500L;
            case "frostfang_bow" -> 6000L;
            case "thunder_hammer" -> 7000L;
            case "arcane_scepter" -> 4000L;
            case "maze_blade" -> 5000L;
            case "echo_crossbow" -> 3500L;
            default -> 3000L;
        };
    }

    private boolean isArtifactCooldownReady(final Player player, final String id) {
        final Map<String, Long> map = this.artifactCooldowns.get(player.getUniqueId());
        if (map == null) { return true; }
        return map.getOrDefault(id, 0L) <= System.currentTimeMillis();
    }

    private void startArtifactCooldown(final Player player, final String id, final long ms) {
        this.artifactCooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(id, System.currentTimeMillis() + ms);
    }

    private void applyEquipmentDamageReduction(final Player player, final EntityDamageEvent event) {
        double reduction = 0.0;
        for (final ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType() == Material.AIR) { continue; }
            final String id = this.plugin.getItemFactory().getTechItemId(armor);
            if (id == null) { continue; }
            reduction += switch (id.toLowerCase()) {
                case "titan_chestplate" -> 0.10;
                case "quantum_chestplate" -> 0.15;
                case "void_crown" -> 0.20;
                case "void_cuirass" -> 0.20;
                default -> 0.0;
            };
        }
        if (reduction > 0.0) {
            event.setDamage(event.getDamage() * (1.0 - Math.min(0.50, reduction)));
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            for (final ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor == null) { continue; }
                final String id = this.plugin.getItemFactory().getTechItemId(armor);
                if (id == null) { continue; }
                switch (id.toLowerCase()) {
                    case "titan_boots" -> event.setDamage(event.getDamage() * 0.5);
                    case "quantum_boots", "void_sabatons" -> event.setDamage(0.0);
                }
            }
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FIRE
                || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK
                || event.getCause() == EntityDamageEvent.DamageCause.LAVA
                || event.getCause() == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            for (final ItemStack armor : player.getInventory().getArmorContents()) {
                if (armor == null) { continue; }
                final String id = this.plugin.getItemFactory().getTechItemId(armor);
                if ("void_sabatons".equalsIgnoreCase(id)) {
                    event.setDamage(0.0);
                    event.setCancelled(true);
                    player.setFireTicks(0);
                    break;
                }
            }
        }
    }

    private void applyEquipmentPassiveEffects(final Player player) {
        for (final ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor == null || armor.getType() == Material.AIR) { continue; }
            final String id = this.plugin.getItemFactory().getTechItemId(armor);
            if (id == null) { continue; }
            switch (id.toLowerCase()) {
                case "titan_helmet" -> player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 5, 0, false, false, true));
                case "titan_leggings" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 5, 0, false, false, true));
                case "quantum_helmet" -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 15, 0, false, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 20 * 5, 0, false, false, true));
                }
                case "quantum_leggings" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 5, 1, false, false, true));
                case "quantum_boots" -> player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 20 * 5, 0, false, false, true));
                case "void_crown" -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 15, 0, false, false, true));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 20 * 5, 0, false, false, true));
                }
                case "void_cuirass" -> player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 5, 0, false, false, true));
                case "void_greaves" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 5, 2, false, false, true));
                case "void_sabatons" -> player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 5, 0, false, false, true));
                // ═══ 迷途星套裝被動 ═══
                case "maze_helmet" -> {
                    // 回聲定位：黑暗中保持視野
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 15, 0, false, false, true));
                }
                case "maze_chestplate" -> {
                    // 空間穩定：受傷時減少擊退 + 輕微抗性
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 5, 0, false, false, true));
                }
                case "maze_leggings" -> {
                    // 迷宮行者：提升迷宮探索速度
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 5, 1, false, false, true));
                }
                case "maze_boots" -> {
                    // 回聲步伐：跳躍加強 + 摔落減免（由 talisman_angel 風格衍生）
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 5, 1, false, false, true));
                }
            }
        }
    }

    private void handleEquipmentThorns(final Player victim, final EntityDamageByEntityEvent event) {
        final UUID uid = victim.getUniqueId();
        if (this.thornsProcessing.contains(uid)) { return; }
        final ItemStack chestplate = victim.getInventory().getChestplate();
        if (chestplate == null) { return; }
        final String id = this.plugin.getItemFactory().getTechItemId(chestplate);
        if (!"quantum_chestplate".equalsIgnoreCase(id)) { return; }
        if (event.getDamager() instanceof LivingEntity attacker) {
            // ★ 同時鎖定 victim 和 attacker 的 UUID，防止兩個穿量子胸甲的玩家互毆造成無限遞迴
            final UUID attackerUid = attacker instanceof Player ap ? ap.getUniqueId() : null;
            if (attackerUid != null && this.thornsProcessing.contains(attackerUid)) { return; }
            this.thornsProcessing.add(uid);
            try {
                attacker.damage(event.getDamage() * 0.25, victim);
            } finally {
                this.thornsProcessing.remove(uid);
            }
            victim.getWorld().playSound(victim.getLocation(), Sound.ENCHANT_THORNS_HIT, 0.6f, 1.2f);
        }
    }

    private void handleTalismanOnDamage(final Player player, final EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FIRE || event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK) {
            if (this.hasTalismanInInventory(player, "talisman_fire") && this.isTalismanCooldownReady(player, "talisman_fire")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 30, 0, false, true, true));
                this.startTalismanCooldown(player, "talisman_fire", 60_000L);
                player.sendActionBar(this.plugin.getItemFactory().success("防焰護符觸發—防火效果已啟動！"));
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.5f, 1.3f);
            }
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
            if (this.hasTalismanInInventory(player, "talisman_water") && this.isTalismanCooldownReady(player, "talisman_water")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 20 * 60, 0, false, true, true));
                this.startTalismanCooldown(player, "talisman_water", 90_000L);
                player.sendActionBar(this.plugin.getItemFactory().success("深海護符觸發—水下呼吸已啟動！"));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 0.5f, 1.3f);
            }
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (this.hasTalismanInInventory(player, "talisman_angel") && this.isTalismanCooldownReady(player, "talisman_angel")) {
                event.setDamage(0.0);
                this.startTalismanCooldown(player, "talisman_angel", 45_000L);
                player.sendActionBar(this.plugin.getItemFactory().success("天使護符觸發—摔落傷害已抵消！"));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.5f);
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 10, 0.3, 0.2, 0.3, 0.02);
            }
        }
        final double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        final double healthAfterHit = player.getHealth() - event.getFinalDamage();
        if (healthAfterHit <= maxHealth * 0.5 && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                || event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE
                || event.getCause() == EntityDamageEvent.DamageCause.MAGIC)) {
            if (this.hasTalismanInInventory(player, "talisman_knight") && this.isTalismanCooldownReady(player, "talisman_knight")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 5, 1, false, true, true));
                this.startTalismanCooldown(player, "talisman_knight", 60_000L);
                player.sendActionBar(this.plugin.getItemFactory().success("騎士護符觸發—抗性護盾已啟動！"));
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.6f, 1.2f);
            }
        }
    }

    private void handleTalismanOnAttack(final Player attacker, final EntityDamageByEntityEvent event) {
        if (this.hasTalismanInInventory(attacker, "talisman_warrior") && this.isTalismanCooldownReady(attacker, "talisman_warrior")) {
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 8, 0, false, true, true));
            this.startTalismanCooldown(attacker, "talisman_warrior", 30_000L);
            attacker.sendActionBar(this.plugin.getItemFactory().success("戰士護符觸發—力量提升！"));
        }
    }

    // ── 吸血刃：攻擊回血 ──
    private void handleVampireBlade(final Player attacker, final EntityDamageByEntityEvent event) {
        final ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!"vampire_blade".equalsIgnoreCase(this.plugin.getItemFactory().getTechItemId(weapon))) {
            return;
        }
        final double heal = event.getFinalDamage() * 0.25;
        final double maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
        attacker.setHealth(Math.min(attacker.getHealth() + heal, maxHealth));
        attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, attacker.getLocation().add(0, 1, 0), 4, 0.2, 0.2, 0.2, 0.01);
    }

    // ── 斬首劍：頭顱對應表 ──
    private Material headForEntity(final LivingEntity entity) {
        return switch (entity.getType()) {
            case SKELETON -> Material.SKELETON_SKULL;
            case WITHER_SKELETON -> Material.WITHER_SKELETON_SKULL;
            case ZOMBIE -> Material.ZOMBIE_HEAD;
            case CREEPER -> Material.CREEPER_HEAD;
            case PIGLIN -> Material.PIGLIN_HEAD;
            case ENDER_DRAGON -> Material.DRAGON_HEAD;
            default -> null;
        };
    }

    // ── 地震斧：右鍵 AOE ──
    private boolean handleSeismicAxe(final PlayerInteractEvent event) {
        if (event.getItem() == null) { return false; }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) { return false; }
        final String techItemId = this.plugin.getItemFactory().getTechItemId(event.getItem());
        if (!"seismic_axe".equalsIgnoreCase(techItemId)) { return false; }
        final Player player = event.getPlayer();
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);
        final UUID uuid = player.getUniqueId();
        final long now = System.currentTimeMillis();
        final Long lastUse = this.seismicAxeCooldowns.get(uuid);
        if (lastUse != null && now - lastUse < 5000L) {
            final long remaining = (5000L - (now - lastUse)) / 1000L + 1;
            player.sendActionBar(this.plugin.getItemFactory().warning("冷卻中（" + remaining + "s）"));
            return true;
        }
        this.seismicAxeCooldowns.put(uuid, now);
        final Location loc = player.getLocation();
        final Vector direction = loc.getDirection().setY(0).normalize();
        int hit = 0;
        for (final Entity entity : player.getNearbyEntities(4, 2, 4)) {
            if (!(entity instanceof LivingEntity living) || entity instanceof Player) {
                continue;
            }
            final Vector toEntity = entity.getLocation().toVector().subtract(loc.toVector());
            if (toEntity.dot(direction) < 0) {
                continue;
            }
            living.damage(6.0, player);
            final Vector knockback = toEntity.normalize().multiply(1.2).setY(0.5);
            living.setVelocity(knockback);
            hit++;
        }
        player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.8f);
        player.getWorld().spawnParticle(Particle.EXPLOSION, loc.add(direction.multiply(2)).add(0, 0.5, 0), 3, 0.5, 0.3, 0.5, 0.01);
        if (hit > 0) {
            player.sendActionBar(this.plugin.getItemFactory().success("地震斧命中 " + hit + " 個目標！"));
        }
        return true;
    }

    private void handleTalismanWhirlwind(final Player victim) {
        if (!this.hasTalismanInInventory(victim, "talisman_whirlwind") || !this.isTalismanCooldownReady(victim, "talisman_whirlwind")) {
            return;
        }
        this.startTalismanCooldown(victim, "talisman_whirlwind", 20_000L);
        final Location loc = victim.getLocation();
        for (final Entity entity : victim.getNearbyEntities(3, 2, 3)) {
            if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                final Vector push = entity.getLocation().toVector().subtract(loc.toVector()).normalize().multiply(1.5);
                push.setY(0.4);
                living.setVelocity(push);
            }
        }
        victim.sendActionBar(this.plugin.getItemFactory().success("旋風護符觸發—周圍生物已被擊退！"));
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.3f);
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.add(0, 1, 0), 6, 1, 0.5, 1, 0.01);
    }

    private void handleTalismanTraveler(final Player player) {
        if (!this.hasTalismanInInventory(player, "talisman_traveler") || !this.isTalismanCooldownReady(player, "talisman_traveler")) {
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 15, 1, false, true, true));
        this.startTalismanCooldown(player, "talisman_traveler", 45_000L);
        player.sendActionBar(this.plugin.getItemFactory().success("旅者護符觸發—速度提升！"));
    }

    private void handleTalismanPassiveChecks(final Player player) {
        final double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (player.getHealth() <= maxHealth * 0.3) {
            if (this.hasTalismanInInventory(player, "talisman_heal") && this.isTalismanCooldownReady(player, "talisman_heal")) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 5, 1, false, true, true));
                this.startTalismanCooldown(player, "talisman_heal", 90_000L);
                player.sendActionBar(this.plugin.getItemFactory().success("再生護符觸發—緊急再生已啟動！"));
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
            }
        }
        if (player.isSprinting()) {
            this.handleTalismanTraveler(player);
        }
    }

    private boolean hasTalismanInInventory(final Player player, final String talismanId) {
        for (final ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) { continue; }
            final String id = this.plugin.getItemFactory().getTechItemId(stack);
            if (talismanId.equalsIgnoreCase(id)) { return true; }
        }
        return false;
    }

    private boolean isTalismanCooldownReady(final Player player, final String talismanId) {
        final Map<String, Long> map = this.talismanCooldowns.get(player.getUniqueId());
        if (map == null) { return true; }
        return map.getOrDefault(talismanId, 0L) <= System.currentTimeMillis();
    }

    private void startTalismanCooldown(final Player player, final String talismanId, final long ms) {
        this.talismanCooldowns.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>()).put(talismanId, System.currentTimeMillis() + ms);
    }

    private void handleTalismanMiner(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) { return; }
        if (!this.hasTalismanInInventory(player, "talisman_miner")) { return; }
        final Material type = event.getBlock().getType();
        final Material smelted = switch (type) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> Material.COPPER_INGOT;
            case RAW_IRON_BLOCK -> Material.IRON_BLOCK;
            case RAW_GOLD_BLOCK -> Material.GOLD_BLOCK;
            case RAW_COPPER_BLOCK -> Material.COPPER_BLOCK;
            default -> null;
        };
        if (smelted == null) { return; }
        event.setDropItems(false);
        final int fortune = Math.max(1, 1 + ThreadLocalRandom.current().nextInt(
                Math.min(4, 1 + event.getPlayer().getInventory().getItemInMainHand().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FORTUNE))));
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), new ItemStack(smelted, fortune));
        event.getPlayer().sendActionBar(this.plugin.getItemFactory().success("礦工護符觸發—礦石已自動燒煉！"));
    }

    private boolean isProtectedTechBlock(final Block block) {
        return this.plugin.getMachineService().resolveManagedMachineBlock(block) != null
                || this.plugin.getPlacedTechBlockService().isTrackedBlock(block);
    }

    private boolean hasNativeContainerGui(final Material type) {
        return switch (type) {
            case BEACON, ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL, ENCHANTING_TABLE,
                 STONECUTTER, LOOM, SMITHING_TABLE, CARTOGRAPHY_TABLE, GRINDSTONE,
                 CRAFTING_TABLE, FURNACE, BLAST_FURNACE, SMOKER, BARREL,
                 CHEST, TRAPPED_CHEST, HOPPER, DROPPER, DISPENSER,
                 BREWING_STAND, LECTERN, CHISELED_BOOKSHELF,
                 CAULDRON, WATER_CAULDRON, LAVA_CAULDRON, POWDER_SNOW_CAULDRON -> true;
            default -> false;
        };
    }

    /** 蹲下右鍵機器時，允許這些裝飾物品正常放置（告示牌、展示框、畫）。 */
    private boolean isDecorationPlaceable(final Material type) {
        if (type == null) { return false; }
        final String name = type.name();
        return name.contains("SIGN")
                || type == Material.ITEM_FRAME || type == Material.GLOW_ITEM_FRAME
                || type == Material.PAINTING;
    }

    private void handleTalismanFarmer(final BlockBreakEvent event) {
        final Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) { return; }
        if (!this.hasTalismanInInventory(player, "talisman_farmer")) { return; }
        final BlockData data = event.getBlock().getBlockData();
        if (!(data instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) { return; }
        if (event.getBlock().getRelative(BlockFace.DOWN).getType() != Material.FARMLAND) { return; }
        if (ThreadLocalRandom.current().nextDouble() >= 0.25) { return; }
        for (final ItemStack drop : event.getBlock().getDrops(player.getInventory().getItemInMainHand())) {
            if (drop != null && drop.getType() != Material.AIR) {
                event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation().add(0.5, 0.5, 0.5), drop.clone());
            }
        }
        player.sendActionBar(this.plugin.getItemFactory().success("農夫護符觸發—雙倍收穫！"));
    }

    // ─── 科技磁石 ───────────────────────────────────────

    private boolean hasMagnetInInventory(final Player player) {
        for (final ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType() == Material.AIR) { continue; }
            final String id = this.plugin.getItemFactory().getTechItemId(stack);
            if (TECH_MAGNET.equalsIgnoreCase(id)) { return true; }
        }
        return false;
    }

    private void handleMagnetTick(final Player player) {
        if (this.magnetDisabled.contains(player.getUniqueId())) { return; }
        if (!this.hasMagnetInInventory(player)) { return; }
        final Location loc = player.getLocation();
        final Collection<Entity> nearby = loc.getWorld().getNearbyEntities(loc, MAGNET_RANGE, MAGNET_RANGE, MAGNET_RANGE,
                entity -> entity instanceof Item item && item.getPickupDelay() <= 0 && !item.isDead());
        if (nearby.isEmpty()) { return; }
        for (final Entity entity : nearby) {
            final Item item = (Item) entity;
            final Vector dir = player.getLocation().add(0, 0.5, 0).toVector().subtract(item.getLocation().toVector());
            final double dist = dir.length();
            if (dist < 1.2) {
                item.teleportAsync(player.getLocation());
            } else {
                item.setVelocity(dir.normalize().multiply(0.6));
            }
        }
        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.add(0, 1, 0), 5, 1, 0.5, 1, 0.05);
    }

    private static final long CHARGER_TRANSFER_PER_TICK = 10L;

    private void handlePortableChargerTick(final Player player) {
        final var factory = this.plugin.getItemFactory();
        final var inv = player.getInventory();
        final ItemStack[] contents = inv.getContents();
        // 找到背包中的行動充電器
        int chargerSlot = -1;
        for (int i = 0; i < contents.length; i++) {
            final ItemStack item = contents[i];
            if (item == null || item.isEmpty()) continue;
            final String id = factory.getTechItemId(item);
            if ("portable_charger".equals(id)) {
                chargerSlot = i;
                break;
            }
        }
        if (chargerSlot < 0) return;
        final ItemStack charger = contents[chargerSlot];
        final long originalEnergy = factory.getItemStoredEnergy(charger);
        if (originalEnergy <= 0L) return;
        long chargerEnergy = originalEnergy;
        // 掃描所有電力工具並充電
        for (int i = 0; i < contents.length; i++) {
            if (i == chargerSlot || chargerEnergy <= 0L) continue;
            final ItemStack tool = contents[i];
            if (tool == null || tool.isEmpty()) continue;
            final String toolId = factory.getTechItemId(tool);
            if (toolId == null || "portable_charger".equals(toolId)) continue;
            final long toolMax = factory.maxItemEnergy(toolId);
            if (toolMax <= 0L) continue;
            final long toolCurrent = factory.getItemStoredEnergy(tool);
            if (toolCurrent >= toolMax) continue;
            final long need = Math.min(toolMax - toolCurrent, CHARGER_TRANSFER_PER_TICK);
            final long transfer = Math.min(need, chargerEnergy);
            factory.setItemStoredEnergy(tool, toolCurrent + transfer);
            chargerEnergy -= transfer;
        }
        // 更新充電器自身電量（使用迴圈前保存的原始值比較，避免重複讀取 PDC）
        if (chargerEnergy != originalEnergy) {
            factory.setItemStoredEnergy(charger, chargerEnergy);
        }
    }

    // ── 太陽能頭盔：每 5 秒在陽光下為背包中隨機工具充 1 EU ──
    private void handleSolarHelmetTick(final Player player) {
        final var factory = this.plugin.getItemFactory();
        final ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || !"solar_helmet".equalsIgnoreCase(factory.getTechItemId(helmet))) return;
        // 需要在露天且白天
        final var world = player.getWorld();
        if (world.getEnvironment() != org.bukkit.World.Environment.NORMAL) return;
        final long time = world.getTime();
        if (time > 13000L || time < 0L) return; // 夜晚不充
        if (player.getLocation().getBlockY() < world.getHighestBlockYAt(player.getLocation()) - 1) return; // 需要頭上無遮擋
        // 每 100 tick (5 秒) 充一次
        if (player.getTicksLived() % 100 != 0) return;
        final var inv = player.getInventory();
        final ItemStack[] contents = inv.getContents();
        final java.util.List<Integer> chargeable = new java.util.ArrayList<>();
        for (int i = 0; i < contents.length; i++) {
            final ItemStack item = contents[i];
            if (item == null || item.isEmpty()) continue;
            final String id = factory.getTechItemId(item);
            if (id == null || "solar_helmet".equals(id)) continue;
            final long max = factory.maxItemEnergy(id);
            if (max <= 0L) continue;
            if (factory.getItemStoredEnergy(item) >= max) continue;
            chargeable.add(i);
        }
        if (chargeable.isEmpty()) return;
        final int slot = chargeable.get(ThreadLocalRandom.current().nextInt(chargeable.size()));
        final ItemStack tool = contents[slot];
        final long current = factory.getItemStoredEnergy(tool);
        final long max = factory.maxItemEnergy(factory.getTechItemId(tool));
        factory.setItemStoredEnergy(tool, Math.min(max, current + 1L));
    }

    private void handleWrenchDismantle(final Player player, final Block machineBlock) {
        // 互動烹調：拆卸方塊會中斷烹調
        this.plugin.getCookingService().interruptByBlockBreak(machineBlock);
        // 取得機器掉落物（含儲存能量）
        final ItemStack machineDrop = this.plugin.getMachineService().buildPlacedMachineItem(machineBlock);
        // 取得科技方塊掉落物
        final ItemStack techBlockDrop = this.plugin.getPlacedTechBlockService().buildDrop(machineBlock);
        // 破壞核心時，順便清除上方的配件方塊（壓力板、鐵柵欄等），讓它自然掉落
        if (machineDrop != null) {
            this.plugin.getMachineService().breakRigComponent(machineBlock);
        }
        // 清理機器資料（彈出輸入/輸出/升級欄物品）
        this.plugin.getMachineService().unregisterMachine(machineBlock);
        if (techBlockDrop != null) {
            this.plugin.getPlacedTechBlockService().unregister(machineBlock);
        }
        // 移除方塊
        machineBlock.setType(org.bukkit.Material.AIR, false);
        // 掉落物放進玩家背包（裝不下才掉地上）；迷宮世界改入冒險者倉庫
        final org.bukkit.Location dropLoc = machineBlock.getLocation().add(0.5, 0.5, 0.5);
        final boolean inLabyrinth = this.plugin.getMazeService().isLabyrinthWorld(player.getWorld());
        if (machineDrop != null) {
            if (inLabyrinth) {
                this.plugin.getMazeService().depositToAdventurerStash(player, machineDrop);
            } else {
                final java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(machineDrop);
                for (final ItemStack leftover : overflow.values()) {
                    machineBlock.getWorld().dropItemNaturally(dropLoc, leftover);
                }
            }
        }
        if (techBlockDrop != null) {
            if (inLabyrinth) {
                this.plugin.getMazeService().depositToAdventurerStash(player, techBlockDrop);
            } else {
                final java.util.Map<Integer, ItemStack> overflow = player.getInventory().addItem(techBlockDrop);
                for (final ItemStack leftover : overflow.values()) {
                    machineBlock.getWorld().dropItemNaturally(dropLoc, leftover);
                }
            }
        }
        player.sendActionBar(this.plugin.getItemFactory().success("⚙ 已拆除機器！"));
        player.playSound(player.getLocation(), Sound.BLOCK_IRON_DOOR_OPEN, 0.8f, 1.2f);
        machineBlock.getWorld().spawnParticle(Particle.CLOUD, dropLoc, 8, 0.3, 0.3, 0.3, 0.02);
    }

    private boolean tryToggleMagnet(final PlayerInteractEvent event) {
        if (event.getItem() == null) { return false; }
        if (!event.getPlayer().isSneaking()) { return false; }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) { return false; }
        final String techItemId = this.plugin.getItemFactory().getTechItemId(event.getItem());
        if (!TECH_MAGNET.equalsIgnoreCase(techItemId)) { return false; }
        final Player player = event.getPlayer();
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);
        final UUID uuid = player.getUniqueId();
        if (this.magnetDisabled.contains(uuid)) {
            this.magnetDisabled.remove(uuid);
            player.sendActionBar(this.plugin.getItemFactory().success("科技磁石已開啟 — 自動吸取周圍物品"));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.4f);
        } else {
            this.magnetDisabled.add(uuid);
            player.sendActionBar(this.plugin.getItemFactory().warning("科技磁石已關閉"));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 1.0f);
        }
        return true;
    }

    // ── 便攜合成台 ──
    private boolean handlePortableCrafter(final PlayerInteractEvent event) {
        if (event.getItem() == null) { return false; }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) { return false; }
        final String techItemId = this.plugin.getItemFactory().getTechItemId(event.getItem());
        if (!"portable_crafter".equalsIgnoreCase(techItemId)) { return false; }
        final Player player = event.getPlayer();
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);
        player.openWorkbench(player.getLocation(), true);
        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.6f, 1.2f);
        return true;
    }

    // ── 便攜垃圾桶 ──
    private boolean handlePortableDustbin(final PlayerInteractEvent event) {
        if (event.getItem() == null) { return false; }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) { return false; }
        final String techItemId = this.plugin.getItemFactory().getTechItemId(event.getItem());
        if (!"portable_dustbin".equalsIgnoreCase(techItemId)) { return false; }
        final Player player = event.getPlayer();
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);
        final org.bukkit.inventory.Inventory dustbin = Bukkit.createInventory(null, 54, net.kyori.adventure.text.Component.text("便攜垃圾桶 — 關閉時清空"));
        player.openInventory(dustbin);
        player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_DISPENSE, 0.6f, 1.0f);
        return true;
    }

    // ── 科技背包：便攜儲物空間 ──
    private static final org.bukkit.NamespacedKey BACKPACK_DATA_KEY = new org.bukkit.NamespacedKey("techproject", "backpack_data");
    private final Map<UUID, ItemStack> openBackpacks = new java.util.concurrent.ConcurrentHashMap<>();

    private boolean handleTechBackpack(final PlayerInteractEvent event) {
        if (event.getItem() == null) return false;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return false;
        final String techItemId = this.plugin.getItemFactory().getTechItemId(event.getItem());
        if (!"tech_backpack".equalsIgnoreCase(techItemId)) return false;
        final Player player = event.getPlayer();
        event.setUseInteractedBlock(Result.DENY);
        event.setUseItemInHand(Result.DENY);
        event.setCancelled(true);

        final ItemStack backpackItem = event.getItem();
        final org.bukkit.inventory.Inventory backpackInv = Bukkit.createInventory(null, 27,
                net.kyori.adventure.text.Component.text("科技背包"));
        // 讀取已存的物品
        if (backpackItem.hasItemMeta()) {
            final String base64 = backpackItem.getItemMeta().getPersistentDataContainer()
                    .get(BACKPACK_DATA_KEY, org.bukkit.persistence.PersistentDataType.STRING);
            if (base64 != null && !base64.isBlank()) {
                try {
                    final byte[] bytes = java.util.Base64.getDecoder().decode(base64);
                    final java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes);
                    final org.bukkit.util.io.BukkitObjectInputStream ois = new org.bukkit.util.io.BukkitObjectInputStream(bis);
                    final int size = ois.readInt();
                    for (int i = 0; i < size; i++) {
                        backpackInv.setItem(i, (ItemStack) ois.readObject());
                    }
                    ois.close();
                } catch (final Exception ex) {
                    this.plugin.getLogger().warning("無法讀取背包資料：" + ex.getMessage());
                }
            }
        }
        this.openBackpacks.put(player.getUniqueId(), backpackItem);
        player.openInventory(backpackInv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
        return true;
    }

    /** 關閉背包時序列化內容到物品 PDC。 */
    void saveBackpackOnClose(final Player player, final org.bukkit.inventory.Inventory inv) {
        final ItemStack backpackItem = this.openBackpacks.remove(player.getUniqueId());
        if (backpackItem == null) return;
        // ★ 游標上若有物品，先歸還到背包格內再序列化，防止複製
        final ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && cursor.getType() != Material.AIR) {
            player.setItemOnCursor(null);
            final java.util.HashMap<Integer, ItemStack> overflow = inv.addItem(cursor);
            // 背包格裝不下的掉到地上，絕不允許憑空消失或複製
            for (final ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
        try {
            final java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            final org.bukkit.util.io.BukkitObjectOutputStream oos = new org.bukkit.util.io.BukkitObjectOutputStream(bos);
            oos.writeInt(inv.getSize());
            for (int i = 0; i < inv.getSize(); i++) {
                oos.writeObject(inv.getItem(i));
            }
            oos.close();
            final String base64 = java.util.Base64.getEncoder().encodeToString(bos.toByteArray());
            final org.bukkit.inventory.meta.ItemMeta meta = backpackItem.getItemMeta();
            meta.getPersistentDataContainer().set(BACKPACK_DATA_KEY, org.bukkit.persistence.PersistentDataType.STRING, base64);
            backpackItem.setItemMeta(meta);
        } catch (final Exception ex) {
            this.plugin.getLogger().warning("無法儲存背包資料：" + ex.getMessage());
        }
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 0.5f, 1.2f);
    }

    public boolean hasOpenBackpack(final UUID uuid) {
        return this.openBackpacks.containsKey(uuid);
    }

    /** 科技背包或界伏盒不可放入背包，避免 NBT 遞迴導致資料爆炸。 */
    private boolean isBackpackForbiddenItem(final ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (item.getType().name().contains("SHULKER_BOX")) return true;
        final String techId = this.plugin.getItemFactory().getTechItemId(item);
        return "tech_backpack".equalsIgnoreCase(techId);
    }

    /**
     * 透過模擬 BlockBreakEvent 檢查玩家是否有權修改指定方塊。
     * 任何領地/保護插件（Residence、WorldGuard 等）若監聽 BlockBreakEvent
     * 並 cancel 了該事件，表示玩家無權操作該方塊。
     */
    private boolean canModifyBlock(final Player player, final Block block) {
        final long threadId = Thread.currentThread().getId();
        this.simulatingBreakThreads.add(threadId);
        try {
            final BlockBreakEvent simulated = new BlockBreakEvent(block, player);
            Bukkit.getPluginManager().callEvent(simulated);
            return !simulated.isCancelled();
        } finally {
            this.simulatingBreakThreads.remove(threadId);
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  全域技能經驗（EcoSkills 風格）
    // ═══════════════════════════════════════════════════════════

    /** 探索：切換世界 +15 XP */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(final org.bukkit.event.player.PlayerChangedWorldEvent event) {
        if (this.plugin.getSkillService() != null) {
            this.plugin.getSkillService().grantXp(event.getPlayer(), "exploration", 15L);
        }
    }

    /** 採集：釣魚成功 +5 XP（探索也 +3） */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(final org.bukkit.event.player.PlayerFishEvent event) {
        if (event.getState() != org.bukkit.event.player.PlayerFishEvent.State.CAUGHT_FISH) return;
        if (this.plugin.getSkillService() == null) return;
        this.plugin.getSkillService().grantXp(event.getPlayer(), "gathering", 5L);
        this.plugin.getSkillService().grantXp(event.getPlayer(), "exploration", 3L);
    }

    /** 採集：收成成熟作物 +2 XP */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHarvestBlock(final org.bukkit.event.player.PlayerHarvestBlockEvent event) {
        if (this.plugin.getSkillService() != null) {
            this.plugin.getSkillService().grantXp(event.getPlayer(), "gathering", 2L);
        }
    }

    /** 研究：附魔物品 +5 XP */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEnchant(final org.bukkit.event.enchantment.EnchantItemEvent event) {
        if (this.plugin.getSkillService() != null) {
            this.plugin.getSkillService().grantXp(event.getEnchanter(), "research", 5L);
        }
    }

    /** 研究：釀造完成 +3 XP（給放入材料的玩家，若附近 8 格有玩家） */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBrew(final org.bukkit.event.inventory.BrewEvent event) {
        if (this.plugin.getSkillService() == null) return;
        final Location loc = event.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (final Player nearby : loc.getNearbyPlayers(8.0)) {
            this.plugin.getSkillService().grantXp(nearby, "research", 3L);
            break; // 只給最近的一位
        }
    }

    /** 工程：從熔爐取出成品 +1 XP */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(final org.bukkit.event.inventory.FurnaceExtractEvent event) {
        if (this.plugin.getSkillService() != null) {
            this.plugin.getSkillService().grantXp(event.getPlayer(), "engineering", 1L);
        }
    }

    /** 共鳴：撿到經驗球 +1 XP */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExpPickup(final org.bukkit.event.player.PlayerExpChangeEvent event) {
        if (event.getAmount() <= 0) return;
        if (this.plugin.getSkillService() != null) {
            this.plugin.getSkillService().grantXp(event.getPlayer(), "resonance", 1L);
        }
    }

}
