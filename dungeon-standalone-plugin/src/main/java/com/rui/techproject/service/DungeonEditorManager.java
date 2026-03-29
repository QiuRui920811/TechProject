package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.model.dungeon.DungeonFunction;
import com.rui.techproject.model.dungeon.DungeonFunction.*;
import com.rui.techproject.util.RichText;
import com.rui.techproject.util.SafeScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * 副本視覺化編輯器管理器 — 完全對應 MythicDungeons 的 Function Builder 系統。
 * <p>
 * 核心功能：
 * <ul>
 *   <li>Function Builder 工具：右鍵方塊→選功能→選觸發器→快捷欄選項編輯</li>
 *   <li>浮動文字標記：已配置方塊上方顯示 TextDisplay 標記</li>
 *   <li>粒子特效：已配置方塊週期性顯示分類顏色粒子</li>
 *   <li>完整 YAML 序列化/反序列化</li>
 * </ul>
 */
public final class DungeonEditorManager {

    // ══════════════════════════════════════════════════
    //  常數
    // ══════════════════════════════════════════════════

    private static final String FUNC_GUI_PREFIX = "§1§l功能編輯 §8» §r";
    private static final String FUNC_BUILDER_KEY = "dungeon_function_builder";
    private static final String FUNC_OPTION_KEY = "dungeon_func_option";

    // ══════════════════════════════════════════════════
    //  注入
    // ══════════════════════════════════════════════════

    private final TechProjectPlugin plugin;
    private final SafeScheduler scheduler;
    private final DungeonService dungeonService;

    // ══════════════════════════════════════════════════
    //  功能資料（每個副本的 Function 列表）
    // ══════════════════════════════════════════════════

    /** 副本 ID → 功能列表 */
    private final Map<String, List<DungeonFunction>> dungeonFunctions = new ConcurrentHashMap<>();
    /** 功能 ID 計數器 */
    private final AtomicInteger funcIdCounter = new AtomicInteger(0);

    // ══════════════════════════════════════════════════
    //  編輯器狀態
    // ══════════════════════════════════════════════════

    /** 玩家 UUID → 目前的功能建構狀態 */
    private final Map<UUID, FunctionBuilderState> builderStates = new ConcurrentHashMap<>();
    /** 玩家 UUID → 目前 GUI 狀態 */
    private final Map<UUID, FuncGuiState> guiStates = new ConcurrentHashMap<>();
    /** 玩家 UUID → 等待的聊天輸入 */
    private final Map<UUID, PendingFuncInput> pendingInputs = new ConcurrentHashMap<>();
    /** 玩家 UUID → 快捷欄編輯狀態 */
    private final Map<UUID, HotbarEditorState> hotbarStates = new ConcurrentHashMap<>();

    // ══════════════════════════════════════════════════
    //  標記實體追蹤
    // ══════════════════════════════════════════════════

    /** 副本 ID → 標記實體列表（TextDisplay） */
    private final Map<String, List<Entity>> markerEntities = new ConcurrentHashMap<>();
    /** 副本場景設定標記位置（出生點/離開點/波次生成點） */
    private record SettingMarkerInfo(int blockX, int blockY, int blockZ, String label, Particle.DustOptions dust) {}
    private final Map<String, List<SettingMarkerInfo>> dungeonSettingMarkers = new ConcurrentHashMap<>();
    /** 粒子效果排程任務 */
    private ScheduledTask particleTask;

    // ══════════════════════════════════════════════════
    //  內部 Record / Enum
    // ══════════════════════════════════════════════════

    /** 功能建構器的逐步狀態。 */
    private record FunctionBuilderState(
            String dungeonId,
            int blockX, int blockY, int blockZ,
            BuilderStep step,
            FunctionType selectedFunction,
            TriggerType selectedTrigger,
            Map<String, Object> functionOptions,
            Map<String, Object> triggerOptions,
            List<FunctionCondition> conditions,
            TargetType targetType,
            boolean allowRetrigger,
            String editingFunctionId   // non-null = 編輯既有功能
    ) {}

    private enum BuilderStep {
        SELECT_CATEGORY,
        SELECT_FUNCTION,
        SELECT_TRIGGER,
        HOTBAR_OPTIONS
    }

    /** GUI 狀態追蹤。 */
    record FuncGuiState(String dungeonId, FuncGuiPage page, int pageNum, Object extra) {}

    enum FuncGuiPage {
        CATEGORY_SELECT,
        FUNCTION_SELECT,
        TRIGGER_SELECT,
        FUNCTION_LIST,
        FUNCTION_DETAIL,
        CONDITION_SELECT,
        CONDITION_LIST,
        OPTION_EDIT
    }

    /** 等待聊天輸入。 */
    private record PendingFuncInput(UUID playerId, String dungeonId, String optionKey, String context) {}

    /** 快捷欄編輯器狀態。 */
    private record HotbarEditorState(
            String dungeonId,
            FunctionBuilderState builder,
            ItemStack[] savedInventory,
            ItemStack[] savedArmor,
            ItemStack savedOffhand
    ) {}

    // ══════════════════════════════════════════════════
    //  建構
    // ══════════════════════════════════════════════════

    public DungeonEditorManager(final TechProjectPlugin plugin,
                                final SafeScheduler scheduler,
                                final DungeonService dungeonService) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.dungeonService = dungeonService;
    }

    // ══════════════════════════════════════════════════
    //  生命週期
    // ══════════════════════════════════════════════════

    /** 啟動粒子效果排程。 */
    public void start() {
        // 每 5 tick (0.25 秒) 更新一次粒子效果
        this.particleTask = this.scheduler.runGlobalTimer(task -> this.tickParticles(), 5L, 5L);
    }

    /** 關閉時清理所有標記。 */
    public void shutdown() {
        if (this.particleTask != null) this.particleTask.cancel();
        // 清除所有標記實體
        for (final List<Entity> markers : this.markerEntities.values()) {
            for (final Entity e : markers) {
                if (e.isValid()) e.remove();
            }
        }
        this.markerEntities.clear();
    }

    // ══════════════════════════════════════════════════
    //  Function Builder 工具
    // ══════════════════════════════════════════════════

    /** 建立 Function Builder 工具（羽毛）。 */
    public ItemStack createFunctionBuilderTool() {
        final ItemStack tool = new ItemStack(Material.FEATHER);
        final var meta = tool.getItemMeta();
        meta.displayName(Component.text("§b§l功能建構器").decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("§7右鍵方塊：在該位置建立功能").decoration(TextDecoration.ITALIC, false),
                Component.text("§7左鍵方塊：查看/編輯已有功能").decoration(TextDecoration.ITALIC, false),
                Component.text("").decoration(TextDecoration.ITALIC, false),
                Component.text("§8[MythicDungeons 式功能建構器]").decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(
                new NamespacedKey(this.plugin, FUNC_BUILDER_KEY),
                PersistentDataType.BOOLEAN, true);
        tool.setItemMeta(meta);
        return tool;
    }

    /** 檢查物品是否為 Function Builder。 */
    public boolean isFunctionBuilder(final ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return false;
        return Boolean.TRUE.equals(stack.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(this.plugin, FUNC_BUILDER_KEY), PersistentDataType.BOOLEAN));
    }

    // ══════════════════════════════════════════════════
    //  右鍵方塊處理（核心入口）
    // ══════════════════════════════════════════════════

    /**
     * 處理 Function Builder 右鍵方塊事件。
     * 如果該方塊已有功能 → 打開編輯 GUI。
     * 如果沒有 → 開始建立新功能流程。
     */
    public void handleRightClickBlock(final Player player, final Block block) {
        final String dungeonId = this.dungeonService.getEditingDungeonId(player);
        if (dungeonId == null) return;

        final int bx = block.getX(), by = block.getY(), bz = block.getZ();

        // 檢查此位置是否已有功能
        final DungeonFunction existing = this.findFunctionAt(dungeonId, bx, by, bz);
        if (existing != null) {
            // 打開現有功能的編輯介面
            this.openFunctionDetailGui(player, dungeonId, existing);
            return;
        }

        // 開始新功能建立流程 → 打開分類選擇 GUI
        final FunctionBuilderState state = new FunctionBuilderState(
                dungeonId, bx, by, bz,
                BuilderStep.SELECT_CATEGORY,
                null, null,
                new LinkedHashMap<>(), new LinkedHashMap<>(),
                new ArrayList<>(), TargetType.NONE, true, null
        );
        this.builderStates.put(player.getUniqueId(), state);
        this.openCategorySelectGui(player, dungeonId);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);
    }

    /**
     * 處理 Function Builder 左鍵方塊事件：刪除或查看。
     */
    public void handleLeftClickBlock(final Player player, final Block block) {
        final String dungeonId = this.dungeonService.getEditingDungeonId(player);
        if (dungeonId == null) return;

        final DungeonFunction existing = this.findFunctionAt(dungeonId, block.getX(), block.getY(), block.getZ());
        if (existing != null) {
            this.openFunctionDetailGui(player, dungeonId, existing);
        } else {
            player.sendMessage(this.msg("§7此方塊沒有設定功能。"));
        }
    }

    /**
     * 處理 Function Builder 右鍵空中事件：打開功能列表。
     */
    public void handleRightClickAir(final Player player) {
        final String dungeonId = this.dungeonService.getEditingDungeonId(player);
        if (dungeonId == null) return;
        this.openFunctionListGui(player, dungeonId, 0);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    /**
     * 處理 Function Builder 左鍵空中事件：取消當前建立狀態。
     */
    public void handleLeftClickAir(final Player player) {
        final String dungeonId = this.dungeonService.getEditingDungeonId(player);
        if (dungeonId == null) return;
        if (this.builderStates.remove(player.getUniqueId()) != null) {
            player.sendMessage(this.msg("§c已取消功能建立。"));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
        } else {
            player.sendMessage(this.msg("§7右鍵方塊：建立/編輯功能  §8|  §7右鍵空中：功能列表"));
        }
    }

    // ══════════════════════════════════════════════════
    //  分類選擇 GUI
    // ══════════════════════════════════════════════════

    private void openCategorySelectGui(final Player player, final String dungeonId) {
        final Inventory gui = Bukkit.createInventory(null, 27,
                Component.text(FUNC_GUI_PREFIX + "選擇功能分類"));
        this.guiStates.put(player.getUniqueId(), new FuncGuiState(dungeonId, FuncGuiPage.CATEGORY_SELECT, 0, null));

        final FunctionCategory[] cats = FunctionCategory.values();
        int slot = 10;
        for (final FunctionCategory cat : cats) {
            final List<FunctionType> funcs = FunctionType.byCategory(cat);
            gui.setItem(slot, this.guiIcon(cat.icon(), cat.display(),
                    "§7" + cat.description(),
                    "§7包含 §f" + funcs.size() + " §7個功能",
                    "§e點擊查看"));
            slot += 2;
        }

        // 功能列表按鈕
        gui.setItem(22, this.guiIcon(Material.BOOK, "§f§l已配置功能列表",
                "§7查看此副本所有已設定的功能", "§e點擊查看"));

        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleCategorySelectClick(final Player player, final int slot) {
        final FuncGuiState gs = this.guiStates.get(player.getUniqueId());
        if (gs == null) return;

        final FunctionCategory selected = switch (slot) {
            case 10 -> FunctionCategory.DUNGEON;
            case 12 -> FunctionCategory.PLAYER;
            case 14 -> FunctionCategory.LOCATION;
            case 16 -> FunctionCategory.META;
            default -> null;
        };

        if (slot == 22) {
            // 功能列表
            this.openFunctionListGui(player, gs.dungeonId(), 0);
            return;
        }

        if (selected == null) return;

        this.clickSound(player);
        this.openFunctionSelectGui(player, gs.dungeonId(), selected);
    }

    // ══════════════════════════════════════════════════
    //  功能類型選擇 GUI
    // ══════════════════════════════════════════════════

    private void openFunctionSelectGui(final Player player, final String dungeonId, final FunctionCategory category) {
        final List<FunctionType> funcs = FunctionType.byCategory(category);
        final int rows = Math.max(3, ((funcs.size() - 1) / 9 + 1) + 1);
        final int size = Math.min(rows * 9, 54);
        final Inventory gui = Bukkit.createInventory(null, size,
                Component.text(FUNC_GUI_PREFIX + category.display() + " 功能"));
        this.guiStates.put(player.getUniqueId(), new FuncGuiState(dungeonId, FuncGuiPage.FUNCTION_SELECT, 0, category));

        for (int i = 0; i < funcs.size(); i++) {
            final FunctionType ft = funcs.get(i);
            gui.setItem(i, this.guiIcon(ft.icon(), ft.display(),
                    "§7" + ft.description(),
                    "",
                    "§a點擊選擇此功能"));
        }

        gui.setItem(size - 5, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleFunctionSelectClick(final Player player, final int slot) {
        final FuncGuiState gs = this.guiStates.get(player.getUniqueId());
        if (gs == null || !(gs.extra() instanceof FunctionCategory cat)) return;

        final var inv = player.getOpenInventory().getTopInventory();
        if (slot == inv.getSize() - 5) {
            this.openCategorySelectGui(player, gs.dungeonId());
            return;
        }

        final List<FunctionType> funcs = FunctionType.byCategory(cat);
        if (slot < 0 || slot >= funcs.size()) return;

        final FunctionType selected = funcs.get(slot);
        this.clickSound(player);

        // 更新 builder 狀態
        final FunctionBuilderState old = this.builderStates.get(player.getUniqueId());
        if (old == null) return;

        final FunctionBuilderState updated = new FunctionBuilderState(
                old.dungeonId(), old.blockX(), old.blockY(), old.blockZ(),
                BuilderStep.SELECT_TRIGGER,
                selected, null,
                selected.defaultOptions(), new LinkedHashMap<>(),
                new ArrayList<>(),
                selected.allowedTargets().length > 0 ? selected.allowedTargets()[0] : TargetType.NONE,
                true, old.editingFunctionId()
        );
        this.builderStates.put(player.getUniqueId(), updated);

        // 打開觸發器選擇 GUI
        this.openTriggerSelectGui(player, gs.dungeonId());
    }

    // ══════════════════════════════════════════════════
    //  觸發器選擇 GUI
    // ══════════════════════════════════════════════════

    private void openTriggerSelectGui(final Player player, final String dungeonId) {
        final TriggerType[] triggers = TriggerType.values();
        final int rows = Math.max(3, ((triggers.length - 1) / 9 + 1) + 1);
        final int size = Math.min(rows * 9, 54);
        final Inventory gui = Bukkit.createInventory(null, size,
                Component.text(FUNC_GUI_PREFIX + "選擇觸發器"));
        this.guiStates.put(player.getUniqueId(), new FuncGuiState(dungeonId, FuncGuiPage.TRIGGER_SELECT, 0, null));

        for (int i = 0; i < triggers.length; i++) {
            final TriggerType t = triggers[i];
            gui.setItem(i, this.guiIcon(t.icon(), t.display(),
                    "§7" + t.description(),
                    "§7選項: §f" + (t.optionKeys().length > 0 ? String.join(", ", t.optionKeys()) : "無"),
                    "§a點擊選擇"));
        }

        gui.setItem(size - 5, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleTriggerSelectClick(final Player player, final int slot) {
        final FuncGuiState gs = this.guiStates.get(player.getUniqueId());
        if (gs == null) return;

        final var inv = player.getOpenInventory().getTopInventory();
        if (slot == inv.getSize() - 5) {
            // 返回分類選擇
            this.openCategorySelectGui(player, gs.dungeonId());
            return;
        }

        final TriggerType[] triggers = TriggerType.values();
        if (slot < 0 || slot >= triggers.length) return;

        final TriggerType selected = triggers[slot];
        this.clickSound(player);

        // 更新 builder 狀態
        final FunctionBuilderState old = this.builderStates.get(player.getUniqueId());
        if (old == null) return;

        final FunctionBuilderState updated = new FunctionBuilderState(
                old.dungeonId(), old.blockX(), old.blockY(), old.blockZ(),
                BuilderStep.HOTBAR_OPTIONS,
                old.selectedFunction(), selected,
                old.functionOptions(), selected.defaultOptions(),
                old.conditions(), old.targetType(), old.allowRetrigger(), old.editingFunctionId()
        );
        this.builderStates.put(player.getUniqueId(), updated);

        // 進入快捷欄選項編輯模式
        player.closeInventory();
        this.enterHotbarEditor(player, updated);
    }

    // ══════════════════════════════════════════════════
    //  快捷欄選項編輯器
    // ══════════════════════════════════════════════════

    /**
     * 進入快捷欄編輯模式。
     * 暫存玩家背包→替換為選項物品→右鍵切換/輸入。
     * 蹲下 (Shift) 確認並儲存。
     */
    private void enterHotbarEditor(final Player player, final FunctionBuilderState state) {
        // 暫存背包
        final ItemStack[] savedInv = player.getInventory().getStorageContents().clone();
        for (int i = 0; i < savedInv.length; i++) {
            savedInv[i] = savedInv[i] != null ? savedInv[i].clone() : null;
        }
        final ItemStack[] savedArmor = player.getInventory().getArmorContents().clone();
        for (int i = 0; i < savedArmor.length; i++) {
            savedArmor[i] = savedArmor[i] != null ? savedArmor[i].clone() : null;
        }
        final ItemStack savedOff = player.getInventory().getItemInOffHand().clone();

        this.hotbarStates.put(player.getUniqueId(), new HotbarEditorState(
                state.dungeonId(), state, savedInv, savedArmor, savedOff));

        // 清空背包
        player.getInventory().clear();

        // 填充快捷欄選項物品
        this.rebuildHotbar(player, state);

        // 通知玩家
        player.sendMessage(this.msg(""));
        player.sendMessage(this.msg("§b§l═══ 功能選項編輯器 ═══"));
        player.sendMessage(this.msg("§7功能: " + state.selectedFunction().display()
                + " §8(" + state.selectedFunction().name() + ")"));
        player.sendMessage(this.msg("§7觸發器: " + state.selectedTrigger().display()
                + " §8(" + state.selectedTrigger().name() + ")"));
        player.sendMessage(this.msg("§7位置: §f" + state.blockX() + ", " + state.blockY() + ", " + state.blockZ()));
        player.sendMessage(this.msg(""));
        player.sendMessage(this.msg("§e右鍵快捷欄物品 §7→ 修改對應選項"));
        player.sendMessage(this.msg("§c蹲下 (Shift) §7→ 確認並儲存功能"));
        player.sendMessage(this.msg("§7丟棄物品 (Q) §7→ 取消建立"));
        player.sendMessage(this.msg(""));
    }

    /**
     * 重建快捷欄顯示。
     * Slot 0: 功能資訊（不可修改）
     * Slot 1: 觸發器資訊
     * Slot 2: 目標類型（可切換）
     * Slot 3: 允許重複觸發（切換）
     * Slot 4: 觸發條件（打開條件 GUI）
     * Slot 5-8: 功能選項（依類型動態顯示）
     */
    private void rebuildHotbar(final Player player, final FunctionBuilderState state) {
        // Slot 0: 功能資訊
        player.getInventory().setItem(0, this.hotbarIcon(state.selectedFunction().icon(),
                state.selectedFunction().display(),
                "§8功能類型（不可更改）",
                "§7" + state.selectedFunction().description()));

        // Slot 1: 觸發器資訊
        final String triggerInfo = this.buildTriggerInfo(state);
        player.getInventory().setItem(1, this.hotbarIcon(state.selectedTrigger().icon(),
                "§7觸發器: " + state.selectedTrigger().display(),
                triggerInfo,
                "§e右鍵查看觸發器選項"));

        // Slot 2: 目標類型
        player.getInventory().setItem(2, this.hotbarIcon(state.targetType().icon(),
                "§b目標: " + state.targetType().display(),
                "§7" + state.targetType().description(),
                "§e右鍵切換"));

        // Slot 3: 允許重複觸發
        player.getInventory().setItem(3, this.hotbarIcon(
                state.allowRetrigger() ? Material.LIME_DYE : Material.RED_DYE,
                "§e重複觸發: " + (state.allowRetrigger() ? "§a是" : "§c否"),
                "§7控制此觸發器是否能多次觸發",
                "§e右鍵切換"));

        // Slot 4: 條件
        player.getInventory().setItem(4, this.hotbarIcon(Material.COMPARATOR,
                "§d觸發條件 §7[" + state.conditions().size() + "]",
                "§7為觸發器添加/移除條件",
                "§e右鍵打開條件編輯器"));

        // Slot 5-8: 功能特定選項
        this.populateFunctionOptions(player, state, 5);

        // Slot 8: 如果功能沒那麼多選項，放觸發器選項
        this.populateTriggerOptions(player, state);
    }

    /** 填入功能選項到快捷欄。 */
    private void populateFunctionOptions(final Player player, final FunctionBuilderState state, final int startSlot) {
        final Map<String, Object> opts = state.functionOptions();
        int slot = startSlot;
        for (final Map.Entry<String, Object> entry : opts.entrySet()) {
            if (slot > 8) break;
            final String key = entry.getKey();
            // 跳過列表類型選項（需要 GUI 編輯）
            if (entry.getValue() instanceof List) continue;

            final ItemStack item = this.buildOptionItem(key, entry.getValue(), "func");
            player.getInventory().setItem(slot, item);
            slot++;
        }
    }

    /** 填入觸發器選項（放在功能選項之後的空位）。 */
    private void populateTriggerOptions(final Player player, final FunctionBuilderState state) {
        final Map<String, Object> opts = state.triggerOptions();
        // 找空位
        for (final Map.Entry<String, Object> entry : opts.entrySet()) {
            if (entry.getValue() instanceof List) continue;
            for (int slot = 5; slot <= 8; slot++) {
                if (player.getInventory().getItem(slot) == null) {
                    final ItemStack item = this.buildOptionItem(entry.getKey(), entry.getValue(), "trig");
                    player.getInventory().setItem(slot, item);
                    break;
                }
            }
        }
    }

    /** 建立選項物品。 */
    private ItemStack buildOptionItem(final String key, final Object value, final String source) {
        final Material icon = FunctionType.getOptionIcon(key);
        final String displayName = FunctionType.getOptionDisplayName(key);
        final String valueStr = value != null ? value.toString() : "§c未設定";

        final ItemStack item = new ItemStack(icon);
        final var meta = item.getItemMeta();
        meta.displayName(Component.text(displayName).decoration(TextDecoration.ITALIC, false));

        final List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7目前值: §f" + valueStr).decoration(TextDecoration.ITALIC, false));

        if (FunctionType.isBooleanOption(key) || TriggerType.isBooleanOption(key)) {
            lore.add(Component.text("§e右鍵切換 是/否").decoration(TextDecoration.ITALIC, false));
        } else {
            final List<String> cycleVals = FunctionType.getCycleValues(key);
            if (cycleVals != null) {
                lore.add(Component.text("§e右鍵切換: " + String.join(" / ", cycleVals)).decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("§e右鍵修改（聊天輸入）").decoration(TextDecoration.ITALIC, false));
            }
        }

        lore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
        final String sourceLabel = switch (source) {
            case "func" -> "功能";
            case "trig" -> "觸發";
            case "cond" -> "條件";
            default -> source;
        };
        lore.add(Component.text("§8[" + sourceLabel + ":" + key + "]").decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        meta.getPersistentDataContainer().set(
                new NamespacedKey(this.plugin, FUNC_OPTION_KEY),
                PersistentDataType.STRING, source + ":" + key);
        item.setItemMeta(meta);
        return item;
    }

    /** 取得選項物品的 key。 */
    private String getOptionKey(final ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(this.plugin, FUNC_OPTION_KEY), PersistentDataType.STRING);
    }

    // ══════════════════════════════════════════════════
    //  快捷欄選項互動
    // ══════════════════════════════════════════════════

    /**
     * 處理快捷欄選項的右鍵互動。
     * @return true 如果已處理
     */
    public boolean handleHotbarInteract(final Player player, final int heldSlot) {
        final HotbarEditorState hs = this.hotbarStates.get(player.getUniqueId());
        if (hs == null) return false;

        final FunctionBuilderState state = hs.builder();

        switch (heldSlot) {
            case 0 -> {
                // 功能資訊 - 不可更改
                player.sendMessage(this.msg("§7此為功能類型，不可在此修改。若要更換請重新建立。"));
                return true;
            }
            case 1 -> {
                // 觸發器選項 - 打開觸發器選項 GUI
                this.openTriggerOptionsGui(player, state);
                return true;
            }
            case 2 -> {
                // 切換目標類型
                final TargetType next = state.targetType().next();
                final FunctionBuilderState newState = new FunctionBuilderState(
                        state.dungeonId(), state.blockX(), state.blockY(), state.blockZ(),
                        state.step(), state.selectedFunction(), state.selectedTrigger(),
                        state.functionOptions(), state.triggerOptions(),
                        state.conditions(), next, state.allowRetrigger(), state.editingFunctionId());
                this.builderStates.put(player.getUniqueId(), newState);
                this.hotbarStates.put(player.getUniqueId(), new HotbarEditorState(
                        hs.dungeonId(), newState, hs.savedInventory(), hs.savedArmor(), hs.savedOffhand()));
                this.rebuildHotbar(player, newState);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                return true;
            }
            case 3 -> {
                // 切換重複觸發
                final boolean newVal = !state.allowRetrigger();
                final FunctionBuilderState newState = new FunctionBuilderState(
                        state.dungeonId(), state.blockX(), state.blockY(), state.blockZ(),
                        state.step(), state.selectedFunction(), state.selectedTrigger(),
                        state.functionOptions(), state.triggerOptions(),
                        state.conditions(), state.targetType(), newVal, state.editingFunctionId());
                this.builderStates.put(player.getUniqueId(), newState);
                this.hotbarStates.put(player.getUniqueId(), new HotbarEditorState(
                        hs.dungeonId(), newState, hs.savedInventory(), hs.savedArmor(), hs.savedOffhand()));
                this.rebuildHotbar(player, newState);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
                return true;
            }
            case 4 -> {
                // 條件編輯
                this.openConditionListGui(player, state);
                return true;
            }
            default -> {
                // Slot 5-8: 選項互動
                return this.handleOptionSlotInteract(player, state, heldSlot);
            }
        }
    }

    /** 處理選項格位的右鍵。 */
    private boolean handleOptionSlotInteract(final Player player, final FunctionBuilderState state, final int slot) {
        final ItemStack item = player.getInventory().getItem(slot);
        final String optionId = this.getOptionKey(item);
        if (optionId == null) return false;

        final String[] parts = optionId.split(":", 2);
        if (parts.length < 2) return false;
        final String source = parts[0]; // "func" or "trig"
        final String key = parts[1];

        final Map<String, Object> opts = source.equals("func") ? state.functionOptions() : state.triggerOptions();
        final Object currentVal = opts.get(key);

        // 布林切換
        if (FunctionType.isBooleanOption(key) || TriggerType.isBooleanOption(key)) {
            final boolean newVal = !(Boolean.TRUE.equals(currentVal));
            opts.put(key, newVal);
            this.rebuildHotbar(player, state);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
            return true;
        }

        // 循環值
        final List<String> cycleVals = FunctionType.getCycleValues(key);
        if (cycleVals != null && !cycleVals.isEmpty()) {
            final String current = currentVal != null ? currentVal.toString() : "";
            final int idx = cycleVals.indexOf(current);
            final String next = cycleVals.get((idx + 1) % cycleVals.size());
            opts.put(key, next);
            this.rebuildHotbar(player, state);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.5f);
            return true;
        }

        // 需要文字輸入
        this.pendingInputs.put(player.getUniqueId(), new PendingFuncInput(
                player.getUniqueId(), state.dungeonId(), source + ":" + key, "hotbar"));
        player.sendMessage(this.msg(""));
        player.sendMessage(this.msg("§e請在聊天中輸入 §f" + FunctionType.getOptionDisplayName(key) + " §e的新值："));
        player.sendMessage(this.msg("§7目前值: §f" + (currentVal != null ? currentVal.toString() : "空")));
        player.sendMessage(this.msg("§8輸入 '取消' 取消"));
        player.sendMessage(this.msg(""));
        return true;
    }

    // ══════════════════════════════════════════════════
    //  蹲下確認 / 丟棄取消
    // ══════════════════════════════════════════════════

    /**
     * 處理蹲下事件 → 確認並儲存功能。
     * @return true 如果在快捷欄編輯模式
     */
    public boolean handleSneak(final Player player) {
        final HotbarEditorState hs = this.hotbarStates.remove(player.getUniqueId());
        if (hs == null) return false;

        final FunctionBuilderState state = hs.builder();

        // 建立/更新 DungeonFunction
        final String funcId = state.editingFunctionId() != null
                ? state.editingFunctionId()
                : "func_" + this.funcIdCounter.incrementAndGet();

        final DungeonFunction func = new DungeonFunction(
                funcId,
                state.selectedFunction(),
                state.blockX(), state.blockY(), state.blockZ(),
                state.selectedTrigger(),
                Map.copyOf(state.triggerOptions()),
                List.copyOf(state.conditions()),
                Map.copyOf(state.functionOptions()),
                state.targetType(),
                state.allowRetrigger()
        );

        // 儲存功能
        if (state.editingFunctionId() != null) {
            this.updateFunction(state.dungeonId(), func);
        } else {
            this.addFunction(state.dungeonId(), func);
        }

        // 還原背包
        this.restoreInventory(player, hs);
        this.builderStates.remove(player.getUniqueId());

        // 通知
        player.sendMessage(this.msg(""));
        player.sendMessage(this.msg("§a§l✔ 功能已儲存！"));
        player.sendMessage(this.msg("§7ID: §f" + funcId));
        player.sendMessage(this.msg("§7類型: " + state.selectedFunction().display()));
        player.sendMessage(this.msg("§7觸發: " + state.selectedTrigger().display()));
        player.sendMessage(this.msg("§7位置: §f" + state.blockX() + ", " + state.blockY() + ", " + state.blockZ()));
        player.sendMessage(this.msg(""));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);

        // 更新標記
        this.refreshMarkers(state.dungeonId());

        return true;
    }

    /**
     * 處理丟棄物品事件 → 取消建立。
     * @return true 如果在快捷欄編輯模式
     */
    public boolean handleDrop(final Player player) {
        final HotbarEditorState hs = this.hotbarStates.remove(player.getUniqueId());
        if (hs == null) return false;

        this.restoreInventory(player, hs);
        this.builderStates.remove(player.getUniqueId());
        player.sendMessage(this.msg("§e已取消功能建立。"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
        return true;
    }

    /** 還原背包。 */
    private void restoreInventory(final Player player, final HotbarEditorState hs) {
        player.getInventory().clear();
        player.getInventory().setStorageContents(hs.savedInventory());
        player.getInventory().setArmorContents(hs.savedArmor());
        player.getInventory().setItemInOffHand(hs.savedOffhand());
    }

    /** 檢查玩家是否在快捷欄編輯模式中。 */
    public boolean isInHotbarEditor(final Player player) {
        return this.hotbarStates.containsKey(player.getUniqueId());
    }

    // ══════════════════════════════════════════════════
    //  觸發器選項 GUI
    // ══════════════════════════════════════════════════

    private void openTriggerOptionsGui(final Player player, final FunctionBuilderState state) {
        final Map<String, Object> opts = state.triggerOptions();
        final int size = Math.max(27, ((opts.size() / 9) + 1) * 9 + 9);
        final Inventory gui = Bukkit.createInventory(null, Math.min(size, 27),
                Component.text(FUNC_GUI_PREFIX + "觸發器選項: " + state.selectedTrigger().display()));
        this.guiStates.put(player.getUniqueId(), new FuncGuiState(state.dungeonId(), FuncGuiPage.OPTION_EDIT, 0, "trigger"));

        int slot = 0;
        for (final Map.Entry<String, Object> entry : opts.entrySet()) {
            final String key = entry.getKey();
            final Object val = entry.getValue();
            gui.setItem(slot, this.guiIcon(
                    FunctionType.getOptionIcon(key),
                    TriggerType.getOptionDisplayName(key),
                    "§7目前值: §f" + (val != null ? val.toString() : "空"),
                    "§e點擊修改"));
            slot++;
        }

        if (opts.isEmpty()) {
            gui.setItem(4, this.guiIcon(Material.STRUCTURE_VOID, "§7此觸發器沒有選項", "§7直接返回即可"));
        }

        gui.setItem(Math.min(size, 27) - 5, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    // ══════════════════════════════════════════════════
    //  條件列表 GUI
    // ══════════════════════════════════════════════════

    private void openConditionListGui(final Player player, final FunctionBuilderState state) {
        final List<FunctionCondition> conditions = state.conditions();
        final int size = Math.max(27, ((conditions.size() / 9) + 2) * 9);
        final Inventory gui = Bukkit.createInventory(null, Math.min(size, 54),
                Component.text(FUNC_GUI_PREFIX + "觸發條件"));
        this.guiStates.put(player.getUniqueId(), new FuncGuiState(state.dungeonId(), FuncGuiPage.CONDITION_LIST, 0, null));

        for (int i = 0; i < conditions.size(); i++) {
            final FunctionCondition cond = conditions.get(i);
            final StringBuilder info = new StringBuilder();
            cond.options().forEach((k, v) -> info.append("§f").append(k).append("§7=§e").append(v).append(" "));
            gui.setItem(i, this.guiIcon(cond.type().icon(),
                    cond.type().display(),
                    "§7" + cond.type().description(),
                    "§7選項: " + (info.length() > 0 ? info.toString().trim() : "無"),
                    "§c點擊移除"));
        }

        // 新增條件按鈕
        final int addSlot = Math.min(conditions.size(), 43);
        gui.setItem(addSlot, this.guiIcon(Material.LIME_DYE, "§a§l+ 新增條件", "§7點擊選擇條件類型"));

        gui.setItem(Math.min(size, 54) - 5, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleConditionListClick(final Player player, final int slot) {
        final FunctionBuilderState state = this.builderStates.get(player.getUniqueId());
        if (state == null) return;

        final var inv = player.getOpenInventory().getTopInventory();
        if (slot == inv.getSize() - 5) {
            // 返回快捷欄
            player.closeInventory();
            this.rebuildHotbar(player, state);
            return;
        }

        final List<FunctionCondition> conditions = state.conditions();

        // 新增按鈕
        if (slot == Math.min(conditions.size(), 43)) {
            this.openConditionSelectGui(player, state);
            return;
        }

        // 刪除條件
        if (slot >= 0 && slot < conditions.size()) {
            conditions.remove(slot);
            player.sendMessage(this.msg("§c已移除條件。"));
            this.clickSound(player);
            this.openConditionListGui(player, state);
        }
    }

    // ══════════════════════════════════════════════════
    //  條件類型選擇 GUI
    // ══════════════════════════════════════════════════

    private void openConditionSelectGui(final Player player, final FunctionBuilderState state) {
        final ConditionType[] types = ConditionType.values();
        final int size = Math.max(27, ((types.length / 9) + 2) * 9);
        final Inventory gui = Bukkit.createInventory(null, Math.min(size, 54),
                Component.text(FUNC_GUI_PREFIX + "選擇條件類型"));
        this.guiStates.put(player.getUniqueId(), new FuncGuiState(state.dungeonId(), FuncGuiPage.CONDITION_SELECT, 0, null));

        for (int i = 0; i < types.length; i++) {
            final ConditionType ct = types[i];
            gui.setItem(i, this.guiIcon(ct.icon(), ct.display(),
                    "§7" + ct.description(),
                    "§7選項: §f" + (ct.optionKeys().length > 0 ? String.join(", ", ct.optionKeys()) : "無"),
                    "§a點擊新增"));
        }

        gui.setItem(Math.min(size, 54) - 5, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleConditionSelectClick(final Player player, final int slot) {
        final FunctionBuilderState state = this.builderStates.get(player.getUniqueId());
        if (state == null) return;

        final var inv = player.getOpenInventory().getTopInventory();
        if (slot == inv.getSize() - 5) {
            this.openConditionListGui(player, state);
            return;
        }

        final ConditionType[] types = ConditionType.values();
        if (slot < 0 || slot >= types.length) return;

        final ConditionType selected = types[slot];
        state.conditions().add(new FunctionCondition(selected, selected.defaultOptions()));
        this.clickSound(player);
        player.sendMessage(this.msg("§a已新增條件: " + selected.display()));
        this.openConditionListGui(player, state);
    }

    // ══════════════════════════════════════════════════
    //  功能列表 GUI（查看所有已配置功能）
    // ══════════════════════════════════════════════════

    public void openFunctionListGui(final Player player, final String dungeonId, final int page) {
        final List<DungeonFunction> funcs = this.dungeonFunctions.getOrDefault(dungeonId, List.of());
        final int pageSize = 45;
        final int totalPages = Math.max(1, (funcs.size() + pageSize - 1) / pageSize);
        final int currentPage = Math.min(page, totalPages - 1);
        final int startIdx = currentPage * pageSize;

        final Inventory gui = Bukkit.createInventory(null, 54,
                Component.text(FUNC_GUI_PREFIX + "功能列表 §7(" + (currentPage + 1) + "/" + totalPages + ")"));
        this.guiStates.put(player.getUniqueId(), new FuncGuiState(dungeonId, FuncGuiPage.FUNCTION_LIST, currentPage, null));

        for (int i = startIdx; i < Math.min(startIdx + pageSize, funcs.size()); i++) {
            final DungeonFunction func = funcs.get(i);
            gui.setItem(i - startIdx, this.guiIcon(func.functionType().icon(),
                    func.functionType().display() + " §8[" + func.id() + "]",
                    "§7觸發器: " + func.triggerType().display(),
                    "§7位置: §f" + func.locationString(),
                    "§7目標: " + func.targetType().display(),
                    "§7條件: §f" + func.conditions().size() + " 個",
                    "§7重複觸發: " + (func.allowRetrigger() ? "§a是" : "§c否"),
                    "",
                    "§e左鍵 編輯  §c右鍵 刪除"));
        }

        // 翻頁
        if (currentPage > 0) {
            gui.setItem(45, this.guiIcon(Material.ARROW, "§f上一頁", "§7頁 " + currentPage));
        }
        if (currentPage < totalPages - 1) {
            gui.setItem(53, this.guiIcon(Material.ARROW, "§f下一頁", "§7頁 " + (currentPage + 2)));
        }

        gui.setItem(49, this.backButton());
        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleFunctionListClick(final Player player, final int slot) {
        final FuncGuiState gs = this.guiStates.get(player.getUniqueId());
        if (gs == null) return;

        final String dungeonId = gs.dungeonId();
        final int page = gs.pageNum();

        if (slot == 49) {
            // 返回分類選擇
            this.openCategorySelectGui(player, dungeonId);
            return;
        }
        if (slot == 45 && page > 0) {
            this.openFunctionListGui(player, dungeonId, page - 1);
            return;
        }
        if (slot == 53) {
            this.openFunctionListGui(player, dungeonId, page + 1);
            return;
        }

        final List<DungeonFunction> funcs = this.dungeonFunctions.getOrDefault(dungeonId, List.of());
        final int idx = page * 45 + slot;
        if (idx >= 0 && idx < funcs.size()) {
            // TODO: 區分左鍵/右鍵，目前統一打開詳情
            this.openFunctionDetailGui(player, dungeonId, funcs.get(idx));
        }
    }

    // ══════════════════════════════════════════════════
    //  功能詳情 GUI
    // ══════════════════════════════════════════════════

    private void openFunctionDetailGui(final Player player, final String dungeonId, final DungeonFunction func) {
        final Inventory gui = Bukkit.createInventory(null, 54,
                Component.text(FUNC_GUI_PREFIX + func.functionType().display()));
        this.guiStates.put(player.getUniqueId(), new FuncGuiState(dungeonId, FuncGuiPage.FUNCTION_DETAIL, 0, func.id()));

        // 第一行：基本資訊
        gui.setItem(4, this.guiIcon(func.functionType().icon(),
                func.functionType().display() + " §8[" + func.id() + "]",
                "§7" + func.functionType().description(),
                "§7分類: " + func.functionType().category().display()));

        // 第二行：觸發器 / 目標 / 重複 / 條件
        gui.setItem(10, this.guiIcon(func.triggerType().icon(),
                "§7觸發器: " + func.triggerType().display(),
                "§7" + func.triggerType().description()));

        gui.setItem(11, this.guiIcon(func.targetType().icon(),
                "§7目標: " + func.targetType().display()));

        gui.setItem(12, this.guiIcon(func.allowRetrigger() ? Material.LIME_DYE : Material.RED_DYE,
                "§7重複觸發: " + (func.allowRetrigger() ? "§a是" : "§c否")));

        gui.setItem(13, this.guiIcon(Material.COMPASS,
                "§7位置: §f" + func.locationString()));

        gui.setItem(14, this.guiIcon(Material.COMPARATOR,
                "§7條件數: §f" + func.conditions().size()));

        // 第三行：功能選項
        int slot = 18;
        for (final Map.Entry<String, Object> entry : func.functionOptions().entrySet()) {
            if (slot > 26) break;
            gui.setItem(slot, this.guiIcon(
                    FunctionType.getOptionIcon(entry.getKey()),
                    FunctionType.getOptionDisplayName(entry.getKey()),
                    "§7值: §f" + entry.getValue()));
            slot++;
        }

        // 第四行：觸發器選項
        slot = 27;
        for (final Map.Entry<String, Object> entry : func.triggerOptions().entrySet()) {
            if (slot > 35) break;
            gui.setItem(slot, this.guiIcon(
                    FunctionType.getOptionIcon(entry.getKey()),
                    "§d" + TriggerType.getOptionDisplayName(entry.getKey()),
                    "§7值: §f" + entry.getValue()));
            slot++;
        }

        // 第五行：條件
        slot = 36;
        for (final FunctionCondition cond : func.conditions()) {
            if (slot > 44) break;
            gui.setItem(slot, this.guiIcon(cond.type().icon(),
                    cond.type().display(),
                    "§7" + cond.options().toString()));
            slot++;
        }

        // 底部：操作按鈕
        gui.setItem(46, this.guiIcon(Material.WRITABLE_BOOK, "§e§l重新編輯",
                "§7進入快捷欄編輯模式修改此功能", "§a點擊"));
        gui.setItem(48, this.guiIcon(Material.ENDER_EYE, "§b§l傳送到功能位置",
                "§7傳送至此功能所在的方塊", "§a點擊"));
        gui.setItem(50, this.guiIcon(Material.RED_DYE, "§c§l刪除此功能",
                "§7§l不可復原！", "§c點擊確認刪除"));
        gui.setItem(52, this.backButton());

        this.fillEmpty(gui);
        player.openInventory(gui);
    }

    private void handleFunctionDetailClick(final Player player, final int slot) {
        final FuncGuiState gs = this.guiStates.get(player.getUniqueId());
        if (gs == null || !(gs.extra() instanceof String funcId)) return;

        final String dungeonId = gs.dungeonId();
        final DungeonFunction func = this.findFunctionById(dungeonId, funcId);
        if (func == null) {
            player.sendMessage(this.msg("§c功能不存在。"));
            return;
        }

        switch (slot) {
            case 46 -> {
                // 重新編輯 → 進入快捷欄模式
                player.closeInventory();
                final FunctionBuilderState state = new FunctionBuilderState(
                        dungeonId, func.blockX(), func.blockY(), func.blockZ(),
                        BuilderStep.HOTBAR_OPTIONS,
                        func.functionType(), func.triggerType(),
                        new LinkedHashMap<>(func.functionOptions()),
                        new LinkedHashMap<>(func.triggerOptions()),
                        new ArrayList<>(func.conditions()),
                        func.targetType(), func.allowRetrigger(), func.id()
                );
                this.builderStates.put(player.getUniqueId(), state);
                this.enterHotbarEditor(player, state);
            }
            case 48 -> {
                // 傳送到功能位置
                final World world = Bukkit.getWorld("dungeon_edit_" + dungeonId);
                if (world != null) {
                    final Location loc = new Location(world, func.blockX() + 0.5, func.blockY() + 1, func.blockZ() + 0.5);
                    player.teleportAsync(loc);
                    player.sendMessage(this.msg("§a已傳送至功能位置。"));
                }
                player.closeInventory();
            }
            case 50 -> {
                // 刪除功能
                this.removeFunction(dungeonId, funcId);
                player.sendMessage(this.msg("§c已刪除功能: §f" + funcId));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                this.refreshMarkers(dungeonId);
                this.openFunctionListGui(player, dungeonId, 0);
            }
            case 52 -> this.openFunctionListGui(player, dungeonId, 0);
        }
    }

    // ══════════════════════════════════════════════════
    //  GUI 總入口
    // ══════════════════════════════════════════════════

    /** 處理所有功能編輯器 GUI 的點擊。 */
    public void handleGuiClick(final Player player, final int rawSlot) {
        final FuncGuiState gs = this.guiStates.get(player.getUniqueId());
        if (gs == null) return;

        this.clickSound(player);
        switch (gs.page()) {
            case CATEGORY_SELECT -> this.handleCategorySelectClick(player, rawSlot);
            case FUNCTION_SELECT -> this.handleFunctionSelectClick(player, rawSlot);
            case TRIGGER_SELECT -> this.handleTriggerSelectClick(player, rawSlot);
            case FUNCTION_LIST -> this.handleFunctionListClick(player, rawSlot);
            case FUNCTION_DETAIL -> this.handleFunctionDetailClick(player, rawSlot);
            case CONDITION_LIST -> this.handleConditionListClick(player, rawSlot);
            case CONDITION_SELECT -> this.handleConditionSelectClick(player, rawSlot);
            case OPTION_EDIT -> this.handleOptionEditClick(player, rawSlot);
        }
    }

    private void handleOptionEditClick(final Player player, final int slot) {
        final FuncGuiState gs = this.guiStates.get(player.getUniqueId());
        if (gs == null) return;

        final var inv = player.getOpenInventory().getTopInventory();
        if (slot == inv.getSize() - 5) {
            // 返回快捷欄
            player.closeInventory();
            final FunctionBuilderState state = this.builderStates.get(player.getUniqueId());
            if (state != null) {
                this.rebuildHotbar(player, state);
            }
            return;
        }

        // 選項 GUI 的點擊 → 提示聊天輸入
        final FunctionBuilderState state = this.builderStates.get(player.getUniqueId());
        if (state == null) return;

        final String source = gs.extra() instanceof String s ? s : "trigger";
        final Map<String, Object> opts = source.equals("trigger") ? state.triggerOptions() : state.functionOptions();
        final List<String> keys = new ArrayList<>(opts.keySet());
        if (slot >= 0 && slot < keys.size()) {
            final String key = keys.get(slot);
            final Object val = opts.get(key);
            this.pendingInputs.put(player.getUniqueId(), new PendingFuncInput(
                    player.getUniqueId(), state.dungeonId(), source + ":" + key, "gui"));
            player.closeInventory();
            player.sendMessage(this.msg("§e請輸入 §f" + TriggerType.getOptionDisplayName(key) + " §e的新值："));
            player.sendMessage(this.msg("§7目前值: §f" + (val != null ? val.toString() : "空")));
            player.sendMessage(this.msg("§8輸入 '取消' 取消"));
        }
    }

    /** 檢查 GUI 標題是否為功能編輯器。 */
    public boolean isFuncEditorGui(final String title) {
        return title != null && title.contains("功能編輯");
    }

    // ══════════════════════════════════════════════════
    //  聊天輸入處理
    // ══════════════════════════════════════════════════

    /**
     * 處理聊天輸入。
     * @return true 如果已處理
     */
    public boolean handleChatInput(final Player player, final String text) {
        final PendingFuncInput pending = this.pendingInputs.remove(player.getUniqueId());
        if (pending == null) return false;

        if (text.equalsIgnoreCase("cancel") || text.equalsIgnoreCase("取消")) {
            player.sendMessage(this.msg("§e已取消輸入。"));
            return true;
        }

        final FunctionBuilderState state = this.builderStates.get(player.getUniqueId());
        if (state == null) return true;

        // 解析 source:key
        final String[] parts = pending.optionKey().split(":", 2);
        if (parts.length < 2) return true;
        final String source = parts[0]; // "func", "trig"
        final String key = parts[1];

        final Map<String, Object> opts = source.equals("func") ? state.functionOptions() : state.triggerOptions();

        // 嘗試智慧型別轉換
        final Object oldVal = opts.get(key);
        final Object newVal = this.smartParse(text, oldVal);
        opts.put(key, newVal);

        player.sendMessage(this.msg("§a已更新 §f" + FunctionType.getOptionDisplayName(key) + " §a→ §e" + newVal));

        // 如果在快捷欄模式則更新
        if (this.hotbarStates.containsKey(player.getUniqueId())) {
            this.scheduler.runEntity(player, () -> this.rebuildHotbar(player, state));
        }

        return true;
    }

    /** 智慧類型轉換：根據原值類型嘗試轉。 */
    private Object smartParse(final String text, final Object oldVal) {
        if (oldVal instanceof Integer) {
            try { return Integer.parseInt(text.trim()); } catch (final NumberFormatException ignored) {}
        }
        if (oldVal instanceof Double) {
            try { return Double.parseDouble(text.trim()); } catch (final NumberFormatException ignored) {}
        }
        if (oldVal instanceof Float) {
            try { return Float.parseFloat(text.trim()); } catch (final NumberFormatException ignored) {}
        }
        if (oldVal instanceof Boolean) {
            return text.trim().equalsIgnoreCase("true") || text.trim().equalsIgnoreCase("是");
        }
        return text;
    }

    // ══════════════════════════════════════════════════
    //  功能 CRUD
    // ══════════════════════════════════════════════════

    public void addFunction(final String dungeonId, final DungeonFunction func) {
        this.dungeonFunctions.computeIfAbsent(dungeonId, k -> new ArrayList<>()).add(func);
    }

    public void updateFunction(final String dungeonId, final DungeonFunction updated) {
        final List<DungeonFunction> list = this.dungeonFunctions.get(dungeonId);
        if (list == null) return;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(updated.id())) {
                list.set(i, updated);
                return;
            }
        }
        list.add(updated);
    }

    public void removeFunction(final String dungeonId, final String funcId) {
        final List<DungeonFunction> list = this.dungeonFunctions.get(dungeonId);
        if (list != null) {
            list.removeIf(f -> f.id().equals(funcId));
        }
    }

    public DungeonFunction findFunctionAt(final String dungeonId, final int x, final int y, final int z) {
        final List<DungeonFunction> list = this.dungeonFunctions.get(dungeonId);
        if (list == null) return null;
        for (final DungeonFunction f : list) {
            if (f.blockX() == x && f.blockY() == y && f.blockZ() == z) return f;
        }
        return null;
    }

    public DungeonFunction findFunctionById(final String dungeonId, final String funcId) {
        final List<DungeonFunction> list = this.dungeonFunctions.get(dungeonId);
        if (list == null) return null;
        for (final DungeonFunction f : list) {
            if (f.id().equals(funcId)) return f;
        }
        return null;
    }

    public List<DungeonFunction> getFunctions(final String dungeonId) {
        return this.dungeonFunctions.getOrDefault(dungeonId, List.of());
    }

    // ══════════════════════════════════════════════════
    //  TextDisplay 浮動文字標記系統
    // ══════════════════════════════════════════════════

    /**
     * 為副本的所有功能建立浮動文字標記。
     * 在進入編輯模式時呼叫。
     */
    public void createMarkers(final String dungeonId) {
        this.removeMarkers(dungeonId);

        final World world = Bukkit.getWorld("dungeon_edit_" + dungeonId);
        this.plugin.getLogger().info("[標記DEBUG] createMarkers 副本=" + dungeonId
                + " 世界=" + (world != null ? world.getName() : "NULL")
                + " 線程=" + Thread.currentThread().getName());
        if (world == null) {
            this.plugin.getLogger().warning("[標記DEBUG] 找不到世界 dungeon_edit_" + dungeonId + "！已載入的世界: "
                    + Bukkit.getWorlds().stream().map(World::getName).toList());
            return;
        }

        final List<DungeonFunction> funcs = this.dungeonFunctions.getOrDefault(dungeonId, List.of());
        this.plugin.getLogger().info("[標記DEBUG] 功能數量=" + funcs.size()
                + " dungeonFunctions包含的副本=" + this.dungeonFunctions.keySet());

        final List<Entity> markers = Collections.synchronizedList(new ArrayList<>());

        for (final DungeonFunction func : funcs) {
            final int cx = func.blockX() >> 4;
            final int cz = func.blockZ() >> 4;
            this.plugin.getLogger().info("[標記DEBUG] 排程生成標記 func=" + func.id()
                    + " 位置=" + func.blockX() + "," + func.blockY() + "," + func.blockZ()
                    + " chunk=" + cx + "," + cz);
            // 先確保 chunk 已載入，再在正確的 region thread 上生成實體
            world.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                this.plugin.getLogger().info("[標記DEBUG] chunk已載入 func=" + func.id()
                        + " chunk=" + chunk.getX() + "," + chunk.getZ()
                        + " 線程=" + Thread.currentThread().getName());
                try {
                    final Entity marker = this.spawnMarker(world, func);
                    if (marker != null) {
                        markers.add(marker);
                        this.plugin.getLogger().info("[標記DEBUG] ✅ 標記生成成功 func=" + func.id()
                                + " entity=" + marker.getType() + " loc=" + marker.getLocation());
                    } else {
                        this.plugin.getLogger().warning("[標記DEBUG] ❌ spawnMarker 回傳 null func=" + func.id());
                    }
                } catch (final Exception e) {
                    this.plugin.getLogger().warning("[標記DEBUG] ❌ 產生標記失敗 func=" + func.id() + ": " + e.getClass().getName() + " - " + e.getMessage());
                    e.printStackTrace();
                }
            }).exceptionally(ex -> {
                this.plugin.getLogger().warning("[標記DEBUG] ❌ getChunkAtAsync 失敗 func=" + func.id() + ": " + ex.getMessage());
                return null;
            });
        }

        this.markerEntities.put(dungeonId, markers);
        this.plugin.getLogger().info("[標記DEBUG] createMarkers 完成排程，副本=" + dungeonId + " 功能數=" + funcs.size());

        // ── 場景設定標記（出生點 / 離開點 / 波次生成點）──
        final var def = this.dungeonService.definitions().get(dungeonId);
        if (def == null) return;

        final List<SettingMarkerInfo> settings = new ArrayList<>();
        final Particle.DustOptions spawnDust  = new Particle.DustOptions(Color.fromRGB(85, 255, 85), 0.8f);   // 綠色
        final Particle.DustOptions exitDust   = new Particle.DustOptions(Color.fromRGB(255, 85, 85), 0.8f);   // 紅色
        final Particle.DustOptions waveDust   = new Particle.DustOptions(Color.fromRGB(255, 255, 85), 0.8f);  // 黃色

        if (def.spawnPoint() != null && def.spawnPoint().length >= 3) {
            final double[] sp = def.spawnPoint();
            settings.add(new SettingMarkerInfo((int) Math.floor(sp[0]), (int) Math.floor(sp[1]), (int) Math.floor(sp[2]),
                    "§a§l⬤ 出生點", spawnDust));
        }
        if (def.exitPoint() != null && def.exitPoint().length >= 3) {
            final double[] ep = def.exitPoint();
            settings.add(new SettingMarkerInfo((int) Math.floor(ep[0]), (int) Math.floor(ep[1]), (int) Math.floor(ep[2]),
                    "§c§l⬤ 離開點", exitDust));
        }
        if (def.waves() != null) {
            for (final var wave : def.waves()) {
                final double[] wl = wave.spawnLocation();
                if (wl != null && wl.length >= 3) {
                    settings.add(new SettingMarkerInfo((int) Math.floor(wl[0]), (int) Math.floor(wl[1]), (int) Math.floor(wl[2]),
                            "§e§l⬤ 波次 " + wave.waveIndex() + " 生成點", waveDust));
                }
            }
        }

        this.dungeonSettingMarkers.put(dungeonId, settings);

        for (final SettingMarkerInfo info : settings) {
            final int cx = info.blockX() >> 4;
            final int cz = info.blockZ() >> 4;
            world.getChunkAtAsync(cx, cz).thenAccept(chunk -> {
                try {
                    final Entity marker = this.spawnSettingMarker(world, info);
                    if (marker != null) markers.add(marker);
                } catch (final Exception e) {
                    this.plugin.getLogger().warning("[標記] 場景設定標記生成失敗: " + e.getMessage());
                }
            });
        }
    }

    /** 移除副本的所有標記。 */
    public void removeMarkers(final String dungeonId) {
        this.dungeonSettingMarkers.remove(dungeonId);
        final List<Entity> old = this.markerEntities.remove(dungeonId);
        if (old != null) {
            for (final Entity e : old) {
                if (e.isValid()) {
                    // Folia 需要在正確的 Region Thread 上操作實體
                    this.scheduler.runEntity(e, () -> {
                        if (e.isValid()) e.remove();
                    });
                }
            }
        }
    }

    /** 重新整理標記（刪除再重建）。 */
    public void refreshMarkers(final String dungeonId) {
        this.removeMarkers(dungeonId);
        this.createMarkers(dungeonId);
    }

    /** 在功能方塊上方生成 TextDisplay 標記。 */
    private Entity spawnMarker(final World world, final DungeonFunction func) {
        final Location loc = new Location(world,
                func.blockX() + 0.5, func.blockY() + 1.3, func.blockZ() + 0.5);

        try {
            return world.spawn(loc, TextDisplay.class, display -> {
                // 建立標記文字
                final String categoryColor = switch (func.functionType().category()) {
                    case DUNGEON -> "§6";
                    case PLAYER -> "§a";
                    case LOCATION -> "§b";
                    case META -> "§d";
                };

                final StringBuilder text = new StringBuilder();
                text.append(categoryColor).append("§l").append(func.functionType().display().replaceAll("§.", "")).append("\n");
                text.append("§7觸發: §f").append(func.triggerType().display().replaceAll("§.", "")).append("\n");
                text.append("§8[").append(func.id()).append("]");

                if (!func.conditions().isEmpty()) {
                    text.append("\n§e⚡ ").append(func.conditions().size()).append(" 個條件");
                }

                display.text(Component.text(text.toString()));
                display.setBillboard(Display.Billboard.CENTER);
                display.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
                display.setSeeThrough(false);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setLineWidth(200);
                display.setShadowed(true);

                // 標記為暫時實體
                display.setPersistent(false);
                display.setCustomNameVisible(false);

                // 使其稍微上下浮動 - 設定為無重力
                display.setGravity(false);
            });
        } catch (final Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "[副本編輯] 無法生成 TextDisplay 標記", e);
            return null;
        }
    }

    /** 在場景設定位置上方生成 TextDisplay 標記。 */
    private Entity spawnSettingMarker(final World world, final SettingMarkerInfo info) {
        final Location loc = new Location(world,
                info.blockX() + 0.5, info.blockY() + 1.3, info.blockZ() + 0.5);
        try {
            return world.spawn(loc, TextDisplay.class, display -> {
                display.text(Component.text(info.label()));
                display.setBillboard(Display.Billboard.CENTER);
                display.setBackgroundColor(Color.fromARGB(160, 0, 0, 0));
                display.setSeeThrough(false);
                display.setAlignment(TextDisplay.TextAlignment.CENTER);
                display.setLineWidth(200);
                display.setShadowed(true);
                display.setPersistent(false);
                display.setCustomNameVisible(false);
                display.setGravity(false);
            });
        } catch (final Exception e) {
            this.plugin.getLogger().log(Level.WARNING, "[副本編輯] 無法生成設定標記", e);
            return null;
        }
    }

    // ══════════════════════════════════════════════════
    //  粒子效果系統
    // ══════════════════════════════════════════════════

    /** 每 5 tick 執行一次的粒子更新。 */
    private void tickParticles() {
        for (final Map.Entry<String, List<DungeonFunction>> entry : this.dungeonFunctions.entrySet()) {
            final String dungeonId = entry.getKey();
            final World world = Bukkit.getWorld("dungeon_edit_" + dungeonId);
            if (world == null) continue;

            // 檢查是否有人在編輯此副本
            if (!this.dungeonService.hasEditorsIn(dungeonId)) continue;

            // 找出在編輯世界中的玩家，直接對玩家發送粒子（避免 Folia 線程問題）
            final List<Player> editors = world.getPlayers();
            if (editors.isEmpty()) continue;

            for (final DungeonFunction func : entry.getValue()) {
                final Location loc = new Location(world,
                        func.blockX() + 0.5, func.blockY() + 1.0, func.blockZ() + 0.5);

                final Particle particle = func.functionType().category().particle();
                final Particle.DustOptions dust = this.getDustOptions(func.functionType().category());

                for (final Player editor : editors) {
                    // 只對 48 格內的玩家顯示粒子
                    if (editor.getLocation().distanceSquared(loc) > 2304) continue;
                    editor.spawnParticle(particle, loc, 3, 0.3, 0.3, 0.3, 0.01);
                    // 方塊邊緣的粒子框線
                    this.spawnBlockOutlineParticleToPlayer(editor, func.blockX(), func.blockY(), func.blockZ(), dust);
                }
            }
        }

        // ── 場景設定標記粒子（出生點 / 離開點 / 波次生成點）──
        for (final var settingEntry : this.dungeonSettingMarkers.entrySet()) {
            final String did = settingEntry.getKey();
            final World w = Bukkit.getWorld("dungeon_edit_" + did);
            if (w == null) continue;
            if (!this.dungeonService.hasEditorsIn(did)) continue;

            final List<Player> eds = w.getPlayers();
            if (eds.isEmpty()) continue;

            for (final SettingMarkerInfo info : settingEntry.getValue()) {
                final Location loc = new Location(w,
                        info.blockX() + 0.5, info.blockY() + 1.0, info.blockZ() + 0.5);
                for (final Player editor : eds) {
                    if (editor.getLocation().distanceSquared(loc) > 2304) continue;
                    editor.spawnParticle(Particle.DUST, loc, 4, 0.3, 0.3, 0.3, 0.01, info.dust());
                    this.spawnBlockOutlineParticleToPlayer(editor, info.blockX(), info.blockY(), info.blockZ(), info.dust());
                }
            }
        }
    }

    private Particle.DustOptions getDustOptions(final FunctionCategory cat) {
        return switch (cat) {
            case DUNGEON -> new Particle.DustOptions(Color.fromRGB(255, 170, 0), 0.6f);
            case PLAYER -> new Particle.DustOptions(Color.fromRGB(85, 255, 85), 0.6f);
            case LOCATION -> new Particle.DustOptions(Color.fromRGB(85, 255, 255), 0.6f);
            case META -> new Particle.DustOptions(Color.fromRGB(255, 85, 255), 0.6f);
        };
    }

    /** 對單一玩家發送方塊邊緣粒子。 */
    private void spawnBlockOutlineParticleToPlayer(final Player player, final int x, final int y, final int z, final Particle.DustOptions dust) {
        // 四條垂直邊
        for (double dy = 0; dy <= 1.0; dy += 0.25) {
            player.spawnParticle(Particle.DUST, x, y + dy, z, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, x + 1, y + dy, z, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, x, y + dy, z + 1, 1, 0, 0, 0, 0, dust);
            player.spawnParticle(Particle.DUST, x + 1, y + dy, z + 1, 1, 0, 0, 0, 0, dust);
        }
    }

    // ══════════════════════════════════════════════════
    //  YAML 序列化 / 反序列化
    // ══════════════════════════════════════════════════

    /**
     * 載入副本的所有功能定義。
     */
    public void loadFunctions(final String dungeonId) {
        this.plugin.getLogger().info("[標記DEBUG] loadFunctions 開始 副本=" + dungeonId);
        final File file = new File(this.plugin.getDataFolder(), "tech-dungeons.yml");
        if (!file.isFile()) {
            this.plugin.getLogger().warning("[標記DEBUG] tech-dungeons.yml 不存在！路徑=" + file.getAbsolutePath());
            return;
        }

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection funcSection = yaml.getConfigurationSection(dungeonId + ".functions");
        if (funcSection == null) return;

        final List<DungeonFunction> funcs = new ArrayList<>();
        int maxId = 0;

        for (final String funcId : funcSection.getKeys(false)) {
            final ConfigurationSection fs = funcSection.getConfigurationSection(funcId);
            if (fs == null) continue;

            try {
                final FunctionType fType = FunctionType.valueOf(fs.getString("type", "MOB_SPAWNER"));
                final TriggerType tType = TriggerType.valueOf(fs.getString("trigger-type", "DUNGEON_START"));
                final TargetType target = TargetType.valueOf(fs.getString("target-type", "NONE"));

                final List<Integer> loc = fs.getIntegerList("location");
                final int bx = loc.size() > 0 ? loc.get(0) : 0;
                final int by = loc.size() > 1 ? loc.get(1) : 0;
                final int bz = loc.size() > 2 ? loc.get(2) : 0;

                // 功能選項
                final Map<String, Object> funcOpts = new LinkedHashMap<>();
                final ConfigurationSection foSection = fs.getConfigurationSection("options");
                if (foSection != null) {
                    for (final String key : foSection.getKeys(false)) {
                        funcOpts.put(key, foSection.get(key));
                    }
                }

                // 觸發器選項
                final Map<String, Object> trigOpts = new LinkedHashMap<>();
                final ConfigurationSection toSection = fs.getConfigurationSection("trigger-options");
                if (toSection != null) {
                    for (final String key : toSection.getKeys(false)) {
                        trigOpts.put(key, toSection.get(key));
                    }
                }

                // 條件
                final List<FunctionCondition> conditions = new ArrayList<>();
                final var condList = fs.getMapList("conditions");
                for (final Map<?, ?> condMap : condList) {
                    try {
                        final ConditionType ct = ConditionType.valueOf(String.valueOf(condMap.get("type")));
                        final Map<String, Object> condOpts = new LinkedHashMap<>();
                        if (condMap.get("options") instanceof Map<?, ?> optMap) {
                            for (final Map.Entry<?, ?> e : optMap.entrySet()) {
                                condOpts.put(String.valueOf(e.getKey()), e.getValue());
                            }
                        }
                        conditions.add(new FunctionCondition(ct, condOpts));
                    } catch (final IllegalArgumentException ignored) {}
                }

                final boolean allowRetrigger = fs.getBoolean("allow-retrigger", true);

                funcs.add(new DungeonFunction(funcId, fType, bx, by, bz, tType, trigOpts, conditions, funcOpts, target, allowRetrigger));

                // 追蹤最高 ID
                if (funcId.startsWith("func_")) {
                    try {
                        final int num = Integer.parseInt(funcId.substring(5));
                        if (num > maxId) maxId = num;
                    } catch (final NumberFormatException ignored) {}
                }
            } catch (final IllegalArgumentException e) {
                this.plugin.getLogger().warning("[副本] 載入功能 " + funcId + " 失敗: " + e.getMessage());
            }
        }

        this.dungeonFunctions.put(dungeonId, funcs);
        if (maxId >= this.funcIdCounter.get()) {
            this.funcIdCounter.set(maxId + 1);
        }

        this.plugin.getLogger().info("[副本] 已載入 " + dungeonId + " 的 " + funcs.size() + " 個功能");
    }

    /**
     * 儲存副本的所有功能到 YAML。
     */
    public void saveFunctions(final String dungeonId) {
        final File file = new File(this.plugin.getDataFolder(), "tech-dungeons.yml");
        YamlConfiguration yaml;
        if (file.isFile()) {
            yaml = YamlConfiguration.loadConfiguration(file);
        } else {
            yaml = new YamlConfiguration();
        }

        // 清除舊的 functions 段落
        yaml.set(dungeonId + ".functions", null);

        final List<DungeonFunction> funcs = this.dungeonFunctions.getOrDefault(dungeonId, List.of());
        if (funcs.isEmpty()) {
            try { yaml.save(file); } catch (final IOException ignored) {}
            return;
        }

        for (final DungeonFunction func : funcs) {
            final String path = dungeonId + ".functions." + func.id();

            yaml.set(path + ".type", func.functionType().name());
            yaml.set(path + ".trigger-type", func.triggerType().name());
            yaml.set(path + ".target-type", func.targetType().name());
            yaml.set(path + ".location", List.of(func.blockX(), func.blockY(), func.blockZ()));
            yaml.set(path + ".allow-retrigger", func.allowRetrigger());

            // 功能選項
            for (final Map.Entry<String, Object> entry : func.functionOptions().entrySet()) {
                yaml.set(path + ".options." + entry.getKey(), entry.getValue());
            }

            // 觸發器選項
            for (final Map.Entry<String, Object> entry : func.triggerOptions().entrySet()) {
                yaml.set(path + ".trigger-options." + entry.getKey(), entry.getValue());
            }

            // 條件
            final List<Map<String, Object>> conditionMaps = new ArrayList<>();
            for (final FunctionCondition cond : func.conditions()) {
                final Map<String, Object> condMap = new LinkedHashMap<>();
                condMap.put("type", cond.type().name());
                condMap.put("options", cond.options());
                conditionMaps.add(condMap);
            }
            if (!conditionMaps.isEmpty()) {
                yaml.set(path + ".conditions", conditionMaps);
            }
        }

        try {
            yaml.save(file);
        } catch (final IOException e) {
            this.plugin.getLogger().warning("[副本] 儲存功能到 YAML 失敗: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════
    //  清理
    // ══════════════════════════════════════════════════

    /** 清理玩家的所有編輯器狀態。 */
    public void cleanupPlayer(final UUID playerId) {
        this.builderStates.remove(playerId);
        this.guiStates.remove(playerId);
        this.pendingInputs.remove(playerId);
        final HotbarEditorState hs = this.hotbarStates.remove(playerId);
        if (hs != null) {
            final Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                this.restoreInventory(player, hs);
            }
        }
    }

    /** 檢查是否有等待輸入。 */
    public boolean hasPendingInput(final UUID playerId) {
        return this.pendingInputs.containsKey(playerId);
    }

    // ══════════════════════════════════════════════════
    //  輔助方法
    // ══════════════════════════════════════════════════

    private Component msg(final String text) {
        return RichText.mini("<gray>[</gray><gradient:#2DD4BF:#0EA5E9><bold>副本編輯</bold></gradient><gray>]</gray> ")
                .append(RichText.parse(text));
    }

    private void clickSound(final Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }

    private String buildTriggerInfo(final FunctionBuilderState state) {
        final StringBuilder sb = new StringBuilder();
        for (final Map.Entry<String, Object> entry : state.triggerOptions().entrySet()) {
            sb.append("§f").append(entry.getKey()).append("§7=§e").append(entry.getValue()).append(" ");
        }
        return sb.length() > 0 ? sb.toString().trim() : "§7無選項";
    }

    private ItemStack guiIcon(final Material material, final String name, final String... loreLines) {
        final ItemStack item = new ItemStack(material);
        final var meta = item.getItemMeta();
        meta.displayName(RichText.parse(name).decoration(TextDecoration.ITALIC, false));
        final List<Component> lore = new ArrayList<>();
        for (final String line : loreLines) {
            lore.add(RichText.parse(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack hotbarIcon(final Material material, final String name, final String... loreLines) {
        return this.guiIcon(material, name, loreLines);
    }

    private ItemStack backButton() {
        return this.guiIcon(Material.ARROW, "§7§l← 返回", "§7回到上一頁");
    }

    private void fillEmpty(final Inventory gui) {
        final ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        final var meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) gui.setItem(i, filler);
        }
    }
}
