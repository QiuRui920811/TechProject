package com.rui.techproject.listener;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.model.dungeon.DungeonConfig;
import com.rui.techproject.model.dungeon.DungeonInstance;
import com.rui.techproject.service.DungeonService;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

public final class DungeonListener implements Listener {

    private final TechProjectPlugin plugin;

    public DungeonListener(final TechProjectPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final String worldName = player.getWorld().getName();
        if (worldName.startsWith("dungeon_instance_") || worldName.startsWith("dungeon_edit_")) {
            this.plugin.getLogger().warning("[副本] 玩家 " + player.getName()
                    + " 登入時位於殘留副本世界 " + worldName + "，將於登入完成後傳回主世界。");
            // Luminol/Folia 在 PlayerJoinEvent 直接 teleportAsync 可能觸發玩家載入器狀態競態。
            // 延遲到玩家完成登入流程後再傳送可避免「Player is already removed from player chunk loader」。
            this.plugin.getSafeScheduler().runEntityDelayed(player, () -> {
                if (!player.isOnline()) {
                    return;
                }
                final World currentWorld = player.getWorld();
                final String current = currentWorld.getName();
                if (!current.startsWith("dungeon_instance_") && !current.startsWith("dungeon_edit_")) {
                    return;
                }
                if (Bukkit.getWorlds().isEmpty()) {
                    return;
                }
                player.teleportAsync(Bukkit.getWorlds().get(0).getSpawnLocation());
            }, 20L);
        }
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds != null) {
            ds.handlePlayerQuit(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(final PlayerMoveEvent event) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds != null && event.getTo() != null) {
            ds.handlePlayerMove(event.getPlayer(), event.getFrom(), event.getTo());
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDungeonChat(final AsyncChatEvent event) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds == null) {
            return;
        }

        final Player player = event.getPlayer();
        final String plainText = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (ds.isAwaitingEditorInput(player.getUniqueId())) {
            event.setCancelled(true);
            this.plugin.getSafeScheduler().runEntity(player, () -> ds.handleEditorChatInput(player, plainText));
            return;
        }

        if (ds.getPlayerInstanceId(player.getUniqueId()) != null) {
            event.setCancelled(true);
            this.plugin.getSafeScheduler().runEntity(player, () -> ds.handleChat(player, plainText));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeathDungeon(final PlayerDeathEvent event) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds != null) {
            ds.handlePlayerDeath(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawnDungeon(final PlayerRespawnEvent event) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds == null) {
            return;
        }

        ds.handlePlayerRespawn(event);

        // 只有不在副本中的玩家才需要 sanitize（避免覆蓋 handlePlayerRespawn 設定的副本重生點）
        if (ds.getPlayerInstanceId(event.getPlayer().getUniqueId()) == null) {
            final Location safe = ds.sanitizeRespawnLocation(event.getRespawnLocation());
            if (safe != null) {
                event.setRespawnLocation(safe);
            }
        }

        final Location target = event.getRespawnLocation().clone();
        final Player player = event.getPlayer();
        this.plugin.getSafeScheduler().runEntityDelayed(player, () -> {
            if (!player.isOnline() || player.isDead()) {
                return;
            }
            if (player.getWorld() != target.getWorld() || player.getLocation().distanceSquared(target) > 25) {
                player.teleportAsync(target);
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(final PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        final Player player = event.getPlayer();
        final ItemStack heldItem = event.getItem() != null ? event.getItem() : player.getInventory().getItemInMainHand();

        final DungeonService ds = this.plugin.getDungeonService();
        if (ds == null) {
            return;
        }

        final var editorMgr = ds.getEditorManager();
        if (editorMgr != null && editorMgr.isFunctionBuilder(heldItem)) {
            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
            event.setCancelled(true);
            final Block clicked = event.getClickedBlock();
            final Action action = event.getAction();
            if (action == Action.RIGHT_CLICK_BLOCK && clicked != null) {
                this.plugin.getSafeScheduler().runEntity(player, () -> editorMgr.handleRightClickBlock(player, clicked));
            } else if (action == Action.LEFT_CLICK_BLOCK && clicked != null) {
                this.plugin.getSafeScheduler().runEntity(player, () -> editorMgr.handleLeftClickBlock(player, clicked));
            } else if (action == Action.RIGHT_CLICK_AIR) {
                this.plugin.getSafeScheduler().runEntity(player, () -> editorMgr.handleRightClickAir(player));
            } else if (action == Action.LEFT_CLICK_AIR) {
                this.plugin.getSafeScheduler().runEntity(player, () -> editorMgr.handleLeftClickAir(player));
            }
            return;
        }

        if (editorMgr != null && editorMgr.isInHotbarEditor(event.getPlayer())) {
            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
            event.setCancelled(true);
            final int slot = player.getInventory().getHeldItemSlot();
            this.plugin.getSafeScheduler().runEntity(player, () -> editorMgr.handleHotbarInteract(player, slot));
            return;
        }

        final String editorAction = ds.getEditorToolAction(heldItem);
        if (editorAction != null) {
            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
            event.setCancelled(true);
            final Block clicked = event.getClickedBlock();
            this.plugin.getSafeScheduler().runEntity(player, () -> ds.handleEditorToolUse(player, editorAction, clicked));
            return;
        }

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            ds.handleInteract(player, event.getClickedBlock());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockRedstone(final BlockRedstoneEvent event) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds != null) {
            ds.handleRedstoneChange(event.getBlock(), event.getOldCurrent(), event.getNewCurrent());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(final EntityDeathEvent event) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds != null) {
            ds.handleEntityDeath(event.getEntity());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(final InventoryClickEvent event) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds == null) {
            return;
        }
        final String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (ds.isRewardEditorGui(title)) {
            ds.handleRewardEditorInventoryClick(event);
            return;
        }
        if (ds.isRewardClaimGui(title)) {
            ds.handleRewardClaimInventoryClick(event);
            return;
        }
        if (!ds.isDungeonEditorGui(title)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            ds.handleEditorGuiClick(player, event.getRawSlot(), event.getClick());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(final InventoryDragEvent event) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds == null) {
            return;
        }
        final String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (ds.isRewardEditorGui(title)) {
            ds.handleRewardEditorInventoryDrag(event);
            return;
        }
        if (ds.isRewardClaimGui(title)) {
            ds.handleRewardClaimInventoryDrag(event);
            return;
        }
        if (ds.isDungeonEditorGui(title)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClose(final InventoryCloseEvent event) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds == null || !(event.getPlayer() instanceof Player player)) {
            return;
        }
        final String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (ds.isRewardEditorGui(title)) {
            ds.handleRewardEditorInventoryClose(player, event.getInventory());
            return;
        }
        if (ds.isRewardClaimGui(title)) {
            ds.handleRewardClaimInventoryClose(player, event.getInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerToggleSneakForFuncEditor(final PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds == null) {
            return;
        }
        final var editorMgr = ds.getEditorManager();
        if (editorMgr == null || !editorMgr.isInHotbarEditor(event.getPlayer())) {
            return;
        }
        this.plugin.getSafeScheduler().runEntity(event.getPlayer(), () -> editorMgr.handleSneak(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItemForFuncEditor(final PlayerDropItemEvent event) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds == null) {
            return;
        }
        final var editorMgr = ds.getEditorManager();
        if (editorMgr == null || !editorMgr.isInHotbarEditor(event.getPlayer())) {
            return;
        }
        event.setCancelled(true);
        this.plugin.getSafeScheduler().runEntity(event.getPlayer(), () -> editorMgr.handleDrop(event.getPlayer()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDungeonCommand(final PlayerCommandPreprocessEvent event) {
        final DungeonConfig cfg = this.getDungeonConfigFor(event.getPlayer());
        if (cfg == null) {
            return;
        }
        final DungeonConfig.DungeonRules rules = cfg.rules();

        final String message = event.getMessage();
        if (!message.startsWith("/")) {
            return;
        }
        final String cmd = message.substring(1).toLowerCase(Locale.ROOT).split("\\s+")[0];

        if (cmd.equals("dungeon") || cmd.equals("dg")) {
            return;
        }

        if (!rules.allowCommands()) {
            for (final String allowed : rules.allowedCommands()) {
                if (cmd.equalsIgnoreCase(allowed) || cmd.startsWith(allowed.toLowerCase(Locale.ROOT) + " ")) {
                    return;
                }
            }
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("副本中禁止使用此指令。"));
            return;
        }

        for (final String disallowed : rules.disallowedCommands()) {
            if (cmd.equalsIgnoreCase(disallowed) || cmd.startsWith(disallowed.toLowerCase(Locale.ROOT) + " ")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Component.text("副本中禁止使用此指令。"));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDungeonExplosion(final EntityExplodeEvent event) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds == null || !ds.isInDungeonWorld(event.getLocation().getWorld())) {
            return;
        }
        event.blockList().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDungeonCreatureSpawn(final CreatureSpawnEvent event) {
        if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL
                && event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.REINFORCEMENTS) {
            return;
        }
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds == null || !ds.isInDungeonWorld(event.getLocation().getWorld())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDungeonEnderpearl(final PlayerTeleportEvent event) {
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                && event.getCause() != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            return;
        }
        final DungeonConfig cfg = this.getDungeonConfigFor(event.getPlayer());
        if (cfg == null) {
            return;
        }
        final DungeonConfig.DungeonRules rules = cfg.rules();
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && !rules.allowEnderpearl()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("副本中禁止使用末影珍珠。"));
        } else if (event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT && !rules.allowChorusFruit()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("副本中禁止使用合唱果。"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDungeonDeathMessage(final PlayerDeathEvent event) {
        final DungeonConfig cfg = this.getDungeonConfigFor(event.getEntity());
        if (cfg != null && cfg.rules().hideDeathMessages()) {
            event.deathMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDungeonBedEnter(final PlayerBedEnterEvent event) {
        final DungeonConfig cfg = this.getDungeonConfigFor(event.getPlayer());
        if (cfg != null) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("副本中無法使用床。"));
            return;
        }
        final String worldName = event.getPlayer().getWorld().getName();
        if (worldName.startsWith("dungeon_edit_") || worldName.startsWith("dungeon_instance_")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("副本世界中無法使用床。"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDungeonBannedItem(final PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) {
            return;
        }
        final Player player = event.getPlayer();
        final DungeonConfig cfg = this.getDungeonConfigFor(player);
        if (cfg == null) {
            return;
        }

        final ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        final DungeonConfig.DungeonRules rules = cfg.rules();
        final String materialName = item.getType().name();
        if (rules.bannedItems().contains(materialName)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("此物品在副本中禁止使用。"));
            return;
        }

        final String techId = this.getTechItemId(item);
        if (techId != null && rules.customBannedItems().contains(techId)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("此物品在副本中禁止使用。"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(final BlockPlaceEvent event) {
        this.checkDungeonBlockPlace(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(final PlayerBucketEmptyEvent event) {
        this.checkDungeonBucket(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(final BlockBreakEvent event) {
        this.checkDungeonBlockBreak(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDungeonPvP(final EntityDamageByEntityEvent event) {
        this.checkDungeonPvP(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDungeonDurability(final PlayerItemDamageEvent event) {
        this.checkDungeonDurability(event);
    }

    private DungeonConfig getDungeonConfigFor(final Player player) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds == null) {
            return null;
        }
        return ds.getPlayerDungeonConfig(player.getUniqueId());
    }

    private DungeonInstance getDungeonInstanceFor(final Player player) {
        final DungeonService ds = this.plugin.getDungeonService();
        if (ds == null) {
            return null;
        }
        return ds.getPlayerDungeonInstance(player.getUniqueId());
    }

    private boolean checkDungeonBlockBreak(final BlockBreakEvent event) {
        final DungeonConfig cfg = this.getDungeonConfigFor(event.getPlayer());
        if (cfg == null) {
            return false;
        }

        final DungeonInstance inst0 = this.getDungeonInstanceFor(event.getPlayer());
        final DungeonService ds = this.plugin.getDungeonService();
        if (inst0 != null && ds != null && ds.getFunctionEngine() != null) {
            ds.getFunctionEngine().onBlockEvent(inst0, event.getPlayer(), event.getBlock(), false);
        }

        final DungeonConfig.DungeonRules rules = cfg.rules();
        final String blockName = event.getBlock().getType().name();

        if (!rules.blockBreakWhitelist().isEmpty()) {
            if (!rules.blockBreakWhitelist().contains(blockName)) {
                event.setCancelled(true);
                return true;
            }
            return false;
        }

        if (!rules.blockBreakBlacklist().isEmpty() && rules.blockBreakBlacklist().contains(blockName)) {
            event.setCancelled(true);
            return true;
        }

        if (rules.allowBreakPlacedBlocks()) {
            final DungeonInstance inst = this.getDungeonInstanceFor(event.getPlayer());
            if (inst != null && inst.isPlacedBlock(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ())) {
                return false;
            }
        }

        if (!rules.allowBreakBlocks()) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    private boolean checkDungeonBlockPlace(final BlockPlaceEvent event) {
        final DungeonConfig cfg = this.getDungeonConfigFor(event.getPlayer());
        if (cfg == null) {
            return false;
        }

        final DungeonInstance inst0 = this.getDungeonInstanceFor(event.getPlayer());
        final DungeonService ds = this.plugin.getDungeonService();
        if (inst0 != null && ds != null && ds.getFunctionEngine() != null) {
            ds.getFunctionEngine().onBlockEvent(inst0, event.getPlayer(), event.getBlock(), true);
        }

        final DungeonConfig.DungeonRules rules = cfg.rules();
        final String blockName = event.getBlock().getType().name();

        if (!rules.blockPlaceWhitelist().isEmpty()) {
            if (!rules.blockPlaceWhitelist().contains(blockName)) {
                event.setCancelled(true);
                return true;
            }
        } else if (!rules.blockPlaceBlacklist().isEmpty() && rules.blockPlaceBlacklist().contains(blockName)) {
            event.setCancelled(true);
            return true;
        }

        if (!rules.allowPlaceBlocks()) {
            event.setCancelled(true);
            return true;
        }

        final DungeonInstance inst = this.getDungeonInstanceFor(event.getPlayer());
        if (inst != null) {
            inst.trackPlacedBlock(event.getBlock().getX(), event.getBlock().getY(), event.getBlock().getZ());
        }
        return false;
    }

    private boolean checkDungeonPvP(final EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return false;
        }

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) {
            return false;
        }

        final DungeonConfig cfg = this.getDungeonConfigFor(attacker);
        if (cfg == null) {
            return false;
        }

        if (!cfg.rules().pvp()) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    private boolean checkDungeonDurability(final PlayerItemDamageEvent event) {
        final DungeonConfig cfg = this.getDungeonConfigFor(event.getPlayer());
        if (cfg == null) {
            return false;
        }
        if (cfg.rules().preventDurabilityLoss()) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    private boolean checkDungeonBucket(final PlayerBucketEmptyEvent event) {
        final DungeonConfig cfg = this.getDungeonConfigFor(event.getPlayer());
        if (cfg == null) {
            return false;
        }
        if (!cfg.rules().allowBucket()) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    private String getTechItemId(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(
                new NamespacedKey(this.plugin, "tech_item_id"), PersistentDataType.STRING);
    }
}
