package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.model.AchievementDefinition;
import com.rui.techproject.util.ItemFactoryUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 精緻的成就 GUI 系統。
 * <p>
 * 6 列大型 GUI：
 *  – 頂部標題欄（裝飾）
 *  – 中間 4 行 = 28 格成就展示
 *  – 底部分類導航 + 翻頁按鈕
 * <p>
 * 成就分類：
 *  入門、加工、能源、農業、物流、探索、終局、收集
 */
public final class AchievementGuiService {

    // ── 色彩 ──
    private static final TextColor TITLE_COLOR     = TextColor.color(0xFFD700);
    private static final TextColor UNLOCKED_COLOR  = TextColor.color(0x7CFC9A);
    private static final TextColor LOCKED_COLOR    = TextColor.color(0x6B7B8D);
    private static final TextColor PROGRESS_COLOR  = TextColor.color(0xF38CFF);
    private static final TextColor HINT_COLOR      = TextColor.color(0x7FDBFF);
    private static final TextColor XP_COLOR        = TextColor.color(0xC58BFF);
    private static final TextColor TOKEN_COLOR     = TextColor.color(0xFFD166);
    private static final TextColor BAR_FILL        = TextColor.color(0x7CFC9A);
    private static final TextColor BAR_EMPTY       = TextColor.color(0x3D3D3D);
    private static final TextColor CATEGORY_ACTIVE = TextColor.color(0xFFD700);
    private static final TextColor CATEGORY_IDLE   = TextColor.color(0xA8B2C1);

    private static final TextColor TITLE_EQUIPPED = TextColor.color(0xFFD700);

    private static final String GUI_TITLE_PREFIX = "成就：";
    private static final int GUI_SIZE = 54; // 6 rows
    private static final int ITEMS_PER_PAGE = 28; // rows 1-4, slots 10-16, 19-25, 28-34, 37-43

    // 成就展示槽位（中央 7×4 矩形）
    private static final int[] DISPLAY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    // 分類定義
    public enum AchievementCategory {
        ALL("全部", Material.NETHER_STAR, "all"),
        BEGINNER("入門", Material.CRAFTING_TABLE, "beginner"),
        PROCESSING("加工", Material.BLAST_FURNACE, "processing"),
        ENERGY("能源", Material.REDSTONE_LAMP, "energy"),
        AGRICULTURE("農業", Material.WHEAT, "agriculture"),
        LOGISTICS("物流", Material.HOPPER, "logistics"),
        EXPLORATION("探索", Material.COMPASS, "exploration"),
        ENDGAME("終局", Material.DRAGON_EGG, "endgame"),
        COLLECTION("收集", Material.CHEST, "collection");

        final String displayName;
        final Material icon;
        final String key;

        AchievementCategory(String displayName, Material icon, String key) {
            this.displayName = displayName;
            this.icon = icon;
            this.key = key;
        }
    }

    // 玩家目前的 GUI 狀態
    private record GuiState(AchievementCategory category, int page) {}
    private final Map<UUID, GuiState> playerStates = new ConcurrentHashMap<>();

    // ── 依賴 ──
    private final TechProjectPlugin plugin;
    private final TechRegistry registry;
    private final PlayerProgressService progressService;
    private final ItemFactoryUtil itemFactory;
    private TitleService titleService;

    public AchievementGuiService(final TechProjectPlugin plugin,
                                  final TechRegistry registry,
                                  final PlayerProgressService progressService,
                                  final ItemFactoryUtil itemFactory) {
        this.plugin = plugin;
        this.registry = registry;
        this.progressService = progressService;
        this.itemFactory = itemFactory;
    }

    public void setTitleService(final TitleService titleService) {
        this.titleService = titleService;
    }

    // ══════════════════════ 公開 API ══════════════════════

    /**
     * 開啟成就 GUI。
     * 開啟時先對玩家執行一次全面成就掃描，補發任何已達成但未解鎖的成就。
     */
    public void openAchievementGui(final Player player) {
        this.plugin.getAchievementService().evaluate(player.getUniqueId());
        this.openAchievementGui(player, AchievementCategory.ALL, 0);
    }

    public void openAchievementGui(final Player player, final AchievementCategory category, final int page) {
        final List<AchievementDefinition> achievements = this.filterByCategory(category);
        final int maxPage = Math.max(0, (achievements.size() - 1) / ITEMS_PER_PAGE);
        final int safePage = Math.max(0, Math.min(page, maxPage));

        final Inventory gui = Bukkit.createInventory(null, GUI_SIZE,
                Component.text(GUI_TITLE_PREFIX + category.displayName, TITLE_COLOR));

        this.renderGui(gui, player, achievements, category, safePage, maxPage);
        player.openInventory(gui);
        this.playerStates.put(player.getUniqueId(), new GuiState(category, safePage));
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.2f);
    }

    /**
     * 判斷是否為成就 GUI。
     */
    public boolean isAchievementGui(final String title) {
        return title != null && title.startsWith(GUI_TITLE_PREFIX);
    }

    /**
     * 處理 GUI 點擊。
     */
    public void handleClick(final Player player, final int rawSlot) {
        final GuiState state = this.playerStates.get(player.getUniqueId());
        if (state == null) {
            return;
        }

        // 分類按鈕（底部行 slots 45-53）
        if (rawSlot >= 45 && rawSlot <= 53) {
            final int categoryIndex = rawSlot - 45;
            if (categoryIndex < AchievementCategory.values().length) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
                this.openAchievementGui(player, AchievementCategory.values()[categoryIndex], 0);
            }
            return;
        }

        // 上一頁（slot 0）
        if (rawSlot == 0 && state.page > 0) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
            this.openAchievementGui(player, state.category, state.page - 1);
            return;
        }

        // 下一頁（slot 8）
        if (rawSlot == 8) {
            final List<AchievementDefinition> achievements = this.filterByCategory(state.category);
            final int maxPage = Math.max(0, (achievements.size() - 1) / ITEMS_PER_PAGE);
            if (state.page < maxPage) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
                this.openAchievementGui(player, state.category, state.page + 1);
            }
            return;
        }

        // 成就槽位點擊 —— 顯示詳情
        for (int i = 0; i < DISPLAY_SLOTS.length; i++) {
            if (DISPLAY_SLOTS[i] == rawSlot) {
                final List<AchievementDefinition> achievements = this.filterByCategory(state.category);
                final int index = state.page * ITEMS_PER_PAGE + i;
                if (index < achievements.size()) {
                    final AchievementDefinition achievement = achievements.get(index);
                    final boolean unlocked = this.progressService.hasAchievementUnlocked(player.getUniqueId(), achievement.id());

                    if (!unlocked) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.7f);
                        player.sendActionBar(Component.text("🔒 尚未解鎖此成就", NamedTextColor.RED));
                        return;
                    }

                    if (this.titleService == null || !this.titleService.hasTitle(achievement.id())) {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.3f);
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("  ✔ " + achievement.displayName(), UNLOCKED_COLOR).decoration(TextDecoration.BOLD, true));
                        player.sendMessage(Component.text("    此成就沒有對應的稱號", TextColor.color(0x888888)));
                        player.sendMessage(Component.empty());
                        return;
                    }

                    final String currentTitle = this.progressService.getSelectedTitle(player.getUniqueId());
                    if (achievement.id().equals(currentTitle)) {
                        this.titleService.clearTitle(player.getUniqueId());
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("  ✖ 稱號已卸下", NamedTextColor.GRAY));
                        player.sendMessage(Component.text("    你現在沒有裝備任何稱號", TextColor.color(0x888888)));
                        player.sendMessage(Component.empty());
                        player.sendActionBar(Component.text("✖ 稱號已卸下", NamedTextColor.GRAY));
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
                    } else {
                        final String titleDisplay = this.titleService.getTitleDisplay(achievement.id());
                        final Component titleComp = LegacyComponentSerializer.legacySection().deserialize(titleDisplay);
                        this.titleService.setTitle(player.getUniqueId(), achievement.id());
                        player.sendMessage(Component.empty());
                        player.sendMessage(Component.text("  ★ 稱號已裝備！", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
                        player.sendMessage(Component.text("    ").append(titleComp));
                        player.sendMessage(Component.text("    你的稱號將顯示在名稱前方", TextColor.color(0x888888)));
                        player.sendMessage(Component.empty());
                        player.sendActionBar(Component.text("★ 稱號裝備：", NamedTextColor.GOLD).append(titleComp));
                        player.showTitle(Title.title(
                                titleComp,
                                Component.text("稱號已裝備！", TextColor.color(0xAAAAAA)),
                                Title.Times.times(
                                        java.time.Duration.ofMillis(200),
                                        java.time.Duration.ofMillis(1500),
                                        java.time.Duration.ofMillis(500)
                                )
                        ));
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.2f);
                        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.5, 0), 20, 0.5, 0.5, 0.5, 0.05);
                    }
                    this.progressService.save(player.getUniqueId());
                    this.openAchievementGui(player, state.category, state.page);
                }
                return;
            }
        }
    }

    /**
     * 清理玩家狀態。
     */
    public void clearState(final UUID playerId) {
        this.playerStates.remove(playerId);
    }

    // ══════════════════════ GUI 渲染 ══════════════════════

    private void renderGui(final Inventory gui, final Player player,
                           final List<AchievementDefinition> achievements,
                           final AchievementCategory category, final int page, final int maxPage) {
        final UUID uuid = player.getUniqueId();
        final Set<String> unlocked = this.progressService.unlockedAchievements(uuid);
        final int totalAchievements = this.registry.allAchievements().size();
        final int totalUnlocked = unlocked.size();

        // ── 頂部裝飾列 ──
        for (int i = 0; i < 9; i++) {
            gui.setItem(i, this.glassPane(Material.BLACK_STAINED_GLASS_PANE, " "));
        }

        // 標題（中央 slot 4）
        gui.setItem(4, this.titleItem(totalUnlocked, totalAchievements, category));

        // 上一頁（slot 0）
        if (page > 0) {
            gui.setItem(0, this.navButton("◀ 上一頁", Material.ARROW, page));
        }

        // 下一頁（slot 8）
        if (page < maxPage) {
            gui.setItem(8, this.navButton("下一頁 ▶", Material.ARROW, page + 2));
        }

        // ── 側邊裝飾 ──
        for (int row = 1; row <= 4; row++) {
            gui.setItem(row * 9, this.glassPane(Material.GRAY_STAINED_GLASS_PANE, " "));
            gui.setItem(row * 9 + 8, this.glassPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        // 左邊裝飾 - 進度條
        gui.setItem(9, this.progressSummaryItem(totalUnlocked, totalAchievements));
        gui.setItem(17, this.xpSummaryItem(uuid));
        gui.setItem(27, this.glassPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        gui.setItem(35, this.glassPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        gui.setItem(36, this.glassPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        gui.setItem(44, this.glassPane(Material.GRAY_STAINED_GLASS_PANE, " "));

        // ── 成就展示區 ──
        final int startIndex = page * ITEMS_PER_PAGE;
        for (int i = 0; i < DISPLAY_SLOTS.length; i++) {
            final int index = startIndex + i;
            if (index < achievements.size()) {
                final AchievementDefinition achievement = achievements.get(index);
                final boolean isUnlocked = unlocked.contains(achievement.id());
                gui.setItem(DISPLAY_SLOTS[i], this.achievementItem(achievement, isUnlocked, uuid));
            } else {
                gui.setItem(DISPLAY_SLOTS[i], null);
            }
        }

        // ── 底部分類導航列 ──
        for (int i = 0; i < AchievementCategory.values().length && i < 9; i++) {
            final AchievementCategory cat = AchievementCategory.values()[i];
            final boolean active = cat == category;
            gui.setItem(45 + i, this.categoryButton(cat, active, unlocked));
        }
    }

    // ── 成就物品 ──

    private ItemStack achievementItem(final AchievementDefinition achievement, final boolean unlocked, final UUID uuid) {
        final String selectedTitle = this.progressService.getSelectedTitle(uuid);
        final boolean isEquipped = unlocked && achievement.id().equals(selectedTitle);
        final Material material = isEquipped ? Material.GOLD_NUGGET : (unlocked ? Material.LIME_DYE : Material.GRAY_DYE);
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();

        final TextColor nameColor = isEquipped ? TITLE_EQUIPPED : (unlocked ? UNLOCKED_COLOR : LOCKED_COLOR);
        final String prefix = isEquipped ? "★ " : (unlocked ? "✔ " : "✖ ");
        meta.displayName(Component.text(prefix + achievement.displayName(), nameColor)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, unlocked));

        final List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (isEquipped) {
            lore.add(Component.text("  ╔══════════════════════╗", TITLE_EQUIPPED).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  ║  ★ 目前裝備的稱號      ║", TITLE_EQUIPPED).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  ╚══════════════════════╝", TITLE_EQUIPPED).decoration(TextDecoration.ITALIC, false));
        } else if (unlocked) {
            lore.add(Component.text("  ╔══════════════════════╗", UNLOCKED_COLOR).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  ║  ✅ 已完成              ║", UNLOCKED_COLOR).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  ╚══════════════════════╝", UNLOCKED_COLOR).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  ╔══════════════════════╗", LOCKED_COLOR).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  ║  🔒 未解鎖              ║", LOCKED_COLOR).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("  ╚══════════════════════╝", LOCKED_COLOR).decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());

        // 描述
        lore.add(Component.text("  📋 " + achievement.description(), TextColor.color(0xDDDDDD)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        // 提示
        if (!unlocked && achievement.hint() != null && !achievement.hint().isBlank()) {
            lore.add(Component.text("  💡 提示: " + achievement.hint(), HINT_COLOR).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        // 獎勵
        lore.add(Component.text("  ┌─ 獎勵 ─────────────────┐", TextColor.color(0x888888)).decoration(TextDecoration.ITALIC, false));
        if (achievement.rewardXp() > 0) {
            lore.add(Component.text("  │ ⭐ 研究經驗 +" + achievement.rewardXp(), XP_COLOR).decoration(TextDecoration.ITALIC, false));
        }
        if (achievement.rewardTokens() > 0) {
            lore.add(Component.text("  │ 🪙 能源代幣 ×" + achievement.rewardTokens(), TOKEN_COLOR).decoration(TextDecoration.ITALIC, false));
        }
        lore.add(Component.text("  └──────────────────────┘", TextColor.color(0x888888)).decoration(TextDecoration.ITALIC, false));

        if (this.titleService != null && this.titleService.hasTitle(achievement.id())) {
            lore.add(Component.empty());
            final Component titleComp = LegacyComponentSerializer.legacySection().deserialize(this.titleService.getTitleDisplay(achievement.id()));
            lore.add(Component.text("  🏷 稱號: ", TITLE_EQUIPPED).decoration(TextDecoration.ITALIC, false).append(titleComp));
            if (unlocked) {
                if (isEquipped) {
                    lore.add(Component.text("  ✦ 點擊取消裝備", TextColor.color(0xFF6666)).decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text("  ✦ 點擊裝備此稱號", TextColor.color(0x7CFC9A)).decoration(TextDecoration.ITALIC, false));
                }
            }
        }

        if (isEquipped) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    // ── 標題物品 ──

    private ItemStack titleItem(final int unlocked, final int total, final AchievementCategory category) {
        final ItemStack stack = new ItemStack(Material.NETHER_STAR);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("★ 科技成就系統 ★", TITLE_COLOR)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        final float pct = total > 0 ? (float) unlocked / total : 0;
        final List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  分類: " + category.displayName, CATEGORY_ACTIVE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  總進度: " + unlocked + " / " + total + " (" + (int) (pct * 100) + "%)", PROGRESS_COLOR).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  " + this.buildProgressBar(pct, 20), pct >= 1.0f ? UNLOCKED_COLOR : PROGRESS_COLOR).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  點擊成就查看詳情", TextColor.color(0x888888)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  底部切換分類", TextColor.color(0x888888)).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    // ── 進度摘要 ──

    private ItemStack progressSummaryItem(final int unlocked, final int total) {
        final float pct = total > 0 ? (float) unlocked / total : 0;
        final ItemStack stack = new ItemStack(pct >= 1.0f ? Material.DIAMOND : Material.EXPERIENCE_BOTTLE);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("📊 總體進度", PROGRESS_COLOR).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("  " + unlocked + " / " + total, TextColor.color(0xDDDDDD)).decoration(TextDecoration.ITALIC, false),
                Component.text("  " + this.buildProgressBar(pct, 16), pct >= 1.0f ? UNLOCKED_COLOR : PROGRESS_COLOR).decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack xpSummaryItem(final UUID uuid) {
        final long xp = this.progressService.getTechXpTotal(uuid);
        final int level = this.progressService.getTechLevel(uuid);
        final ItemStack stack = new ItemStack(Material.ENCHANTED_BOOK);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("📖 研究等級", XP_COLOR).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.empty(),
                Component.text("  等級：Lv." + level, XP_COLOR).decoration(TextDecoration.ITALIC, false),
                Component.text("  累計經驗：" + xp, TextColor.color(0xDDDDDD)).decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    // ── 分類按鈕 ──

    private ItemStack categoryButton(final AchievementCategory category, final boolean active,
                                     final Set<String> unlocked) {
        final ItemStack stack = new ItemStack(active ? Material.LIME_STAINED_GLASS_PANE : category.icon);
        final ItemMeta meta = stack.getItemMeta();

        final TextColor color = active ? CATEGORY_ACTIVE : CATEGORY_IDLE;
        meta.displayName(Component.text((active ? "▸ " : "  ") + category.displayName, color)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, active));

        // 計算該分類解鎖數
        final List<AchievementDefinition> catAchievements = this.filterByCategory(category);
        final long catUnlocked = catAchievements.stream().filter(a -> unlocked.contains(a.id())).count();
        meta.lore(List.of(
                Component.text("  " + catUnlocked + " / " + catAchievements.size(), TextColor.color(0xAAAAAA))
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    // ── 導航按鈕 ──

    private ItemStack navButton(final String title, final Material material, final int targetPage) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(title, HINT_COLOR).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("  第 " + targetPage + " 頁", TextColor.color(0x888888)).decoration(TextDecoration.ITALIC, false)));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    // ── 玻璃板 ──

    private ItemStack glassPane(final Material material, final String name) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name, TextColor.color(0x333333)).decoration(TextDecoration.ITALIC, false));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    // ── 工具方法 ──

    private String buildProgressBar(final float progress, final int length) {
        final int filled = (int) (progress * length);
        final StringBuilder bar = new StringBuilder("┃");
        for (int i = 0; i < length; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        bar.append("┃");
        return bar.toString();
    }

    /**
     * 根據成就 ID 推斷分類。
     */
    private List<AchievementDefinition> filterByCategory(final AchievementCategory category) {
        if (category == AchievementCategory.ALL) {
            return new ArrayList<>(this.registry.allAchievements());
        }
        final List<AchievementDefinition> result = new ArrayList<>();
        for (final AchievementDefinition a : this.registry.allAchievements()) {
            if (this.matchCategory(a, category)) {
                result.add(a);
            }
        }
        return result;
    }

    private boolean matchCategory(final AchievementDefinition achievement, final AchievementCategory category) {
        final String id = achievement.id().toLowerCase(Locale.ROOT);
        return switch (category) {
            case ALL -> true;
            case BEGINNER -> id.contains("first") || id.contains("beginner") || id.contains("starter")
                    || id.contains("initial") || id.contains("newcomer");
            case PROCESSING -> id.contains("process") || id.contains("craft") || id.contains("smelt")
                    || id.contains("recycl") || id.contains("crush") || id.contains("compress")
                    || id.contains("assembl") || id.contains("refine");
            case ENERGY -> id.contains("energy") || id.contains("power") || id.contains("solar")
                    || id.contains("generator") || id.contains("fusion") || id.contains("reactor")
                    || id.contains("watt");
            case AGRICULTURE -> id.contains("crop") || id.contains("farm") || id.contains("harvest")
                    || id.contains("seed") || id.contains("grow") || id.contains("cook")
                    || id.contains("meal") || id.contains("orchard") || id.contains("bio");
            case LOGISTICS -> id.contains("logistic") || id.contains("network") || id.contains("transfer")
                    || id.contains("route") || id.contains("bus") || id.contains("storage")
                    || id.contains("cargo");
            case EXPLORATION -> id.contains("planet") || id.contains("explore") || id.contains("pioneer")
                    || id.contains("void") || id.contains("discover") || id.contains("stellar")
                    || id.contains("gate") || id.contains("travel");
            case ENDGAME -> id.contains("quantum") || id.contains("omega") || id.contains("apex")
                    || id.contains("singularity") || id.contains("cosmic") || id.contains("mega")
                    || id.contains("completionist") || id.contains("master");
            case COLLECTION -> id.contains("collect") || id.contains("conquer") || id.contains("unlock")
                    || id.contains("full") || id.contains("all_") || id.contains("hoard");
        };
    }
}
