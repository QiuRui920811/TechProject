package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.model.AchievementDefinition;
import com.rui.techproject.util.ItemFactoryUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 成就 + 稱號 GUI。
 * <p>
 * 5 列 GUI（45 格）：
 *  – 頂部：研究等級 / 總進度 / 翻頁
 *  – 中間 4 行 = 28 格成就展示（按進度排序：未解鎖 → 已解鎖未套用 → 已套用）
 *  – 無底部分類列、無玻璃片
 * <p>
 * 成就物品使用 Nexo 自訂材質：
 *  – tp_title_equipped（已套用稱號）
 *  – tp_title_unlocked（已達成但未套用）
 *  – tp_title_locked（未解開）
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
    private static final TextColor TITLE_EQUIPPED  = TextColor.color(0xFFD700);

    // ── Nexo font HUD glyph（搭配 pack/assets/minecraft/font/techproject_achievement.json） ──
    private static final Key ACHIEVEMENT_MENU_FONT = Key.key("minecraft", "techproject_achievement");
    private static final String ACHIEVEMENT_MENU_SHIFT = "\uF102";
    private static final String ACHIEVEMENT_MENU_GLYPH = "\uF007";
    private static final Component STATIC_TITLE = Component
            .text(ACHIEVEMENT_MENU_SHIFT + ACHIEVEMENT_MENU_GLYPH, NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false)
            .font(ACHIEVEMENT_MENU_FONT);

    private static final String GUI_TITLE_PREFIX = "成就：";
    private static final int GUI_SIZE = 54; // 6 rows
    private static final int ITEMS_PER_PAGE = 28; // rows 1-4, columns 1-7

    // 成就展示槽位（中央 7×4 矩形，row 1-4 col 1-7）
    private static final int[] DISPLAY_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    // 玩家目前的 GUI 狀態
    private record GuiState(int page) {}
    private final Map<UUID, GuiState> playerStates = new ConcurrentHashMap<>();

    // ── 依賴 ──
    private final TechMCPlugin plugin;
    private final TechRegistry registry;
    private final PlayerProgressService progressService;
    private final ItemFactoryUtil itemFactory;
    private TitleService titleService;

    public AchievementGuiService(final TechMCPlugin plugin,
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

    public void openAchievementGui(final Player player) {
        this.plugin.getAchievementService().evaluate(player.getUniqueId());
        this.openAchievementGui(player, 0);
    }

    public void openAchievementGui(final Player player, final int page) {
        final UUID uuid = player.getUniqueId();
        final Set<String> unlocked = this.progressService.unlockedAchievements(uuid);
        final List<AchievementDefinition> sorted = this.sortAchievements(unlocked);

        final int maxPage = Math.max(0, (sorted.size() - 1) / ITEMS_PER_PAGE);
        final int safePage = Math.max(0, Math.min(page, maxPage));

        final Inventory gui = Bukkit.createInventory(null, GUI_SIZE, STATIC_TITLE);

        this.renderGui(gui, player, sorted, safePage, maxPage);
        player.openInventory(gui);
        this.playerStates.put(uuid, new GuiState(safePage));
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, 1.2f);
    }

    public boolean isAchievementGui(final String title) {
        if (title == null) return false;
        // glyph 標題序列化後會是 PUA 字元
        return title.contains(ACHIEVEMENT_MENU_GLYPH) || title.startsWith(GUI_TITLE_PREFIX);
    }

    public void handleClick(final Player player, final int rawSlot) {
        final GuiState state = this.playerStates.get(player.getUniqueId());
        if (state == null) return;

        // 上一頁（slot 0）
        if (rawSlot == 0 && state.page > 0) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
            this.openAchievementGui(player, state.page - 1);
            return;
        }

        // 下一頁（slot 8）
        if (rawSlot == 8) {
            final UUID uuid = player.getUniqueId();
            final Set<String> unlocked = this.progressService.unlockedAchievements(uuid);
            final int total = this.registry.allAchievements().size();
            final int maxPage = Math.max(0, (total - 1) / ITEMS_PER_PAGE);
            if (state.page < maxPage) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.0f);
                this.openAchievementGui(player, state.page + 1);
            }
            return;
        }

        // 成就槽位點擊
        for (int i = 0; i < DISPLAY_SLOTS.length; i++) {
            if (DISPLAY_SLOTS[i] == rawSlot) {
                final UUID uuid = player.getUniqueId();
                final Set<String> unlocked = this.progressService.unlockedAchievements(uuid);
                final String selectedTitle = this.progressService.getSelectedTitle(uuid);
                final List<AchievementDefinition> sorted = this.sortAchievements(unlocked);
                final int index = state.page * ITEMS_PER_PAGE + i;
                if (index >= sorted.size()) return;

                final AchievementDefinition achievement = sorted.get(index);
                final boolean isUnlocked = unlocked.contains(achievement.id());

                if (!isUnlocked) {
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

                final String currentTitle = this.progressService.getSelectedTitle(uuid);
                if (achievement.id().equals(currentTitle)) {
                    this.titleService.clearTitle(uuid);
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("  ✖ 稱號已卸下", NamedTextColor.GRAY));
                    player.sendMessage(Component.text("    你現在沒有裝備任何稱號", TextColor.color(0x888888)));
                    player.sendMessage(Component.empty());
                    player.sendActionBar(Component.text("✖ 稱號已卸下", NamedTextColor.GRAY));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f);
                } else {
                    final String titleDisplay = this.titleService.getTitleDisplay(achievement.id());
                    final Component titleComp = LegacyComponentSerializer.legacySection().deserialize(titleDisplay);
                    this.titleService.setTitle(uuid, achievement.id());
                    player.sendMessage(Component.empty());
                    player.sendMessage(Component.text("  ★ 稱號已裝備！", NamedTextColor.GOLD).decoration(TextDecoration.BOLD, true));
                    player.sendMessage(Component.text("    ").append(titleComp));
                    player.sendMessage(Component.text("    你的稱號將顯示在名稱前方", TextColor.color(0x888888)));
                    player.sendMessage(Component.empty());
                    player.sendActionBar(Component.text("★ 稱號裝備：", NamedTextColor.GOLD).append(titleComp));
                    this.plugin.getTitleMsgService().send(player,
                            titleComp,
                            Component.text("稱號已裝備！", TextColor.color(0xAAAAAA)),
                            30L, Sound.BLOCK_NOTE_BLOCK_BELL);
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.2f);
                    player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.5, 0), 20, 0.5, 0.5, 0.5, 0.05);
                }
                this.progressService.save(uuid);
                this.openAchievementGui(player, state.page);
                return;
            }
        }
    }

    public void clearState(final UUID playerId) {
        this.playerStates.remove(playerId);
    }

    // ══════════════════════ 排序 ══════════════════════

    /**
     * 排序成就：已解鎖排前面 → 未解鎖排後面。
     * 同類內按成就名稱排序。不按套用狀態排序，避免裝備/卸下稱號時位置跳動。
     */
    private List<AchievementDefinition> sortAchievements(final Set<String> unlocked) {
        final List<AchievementDefinition> all = new ArrayList<>(this.registry.allAchievements());
        all.sort((a, b) -> {
            final boolean uA = unlocked.contains(a.id());
            final boolean uB = unlocked.contains(b.id());
            if (uA != uB) return uA ? -1 : 1;
            return a.displayName().compareTo(b.displayName());
        });
        return all;
    }

    // ══════════════════════ GUI 渲染 ══════════════════════

    private void renderGui(final Inventory gui, final Player player,
                           final List<AchievementDefinition> achievements,
                           final int page, final int maxPage) {
        final UUID uuid = player.getUniqueId();
        final Set<String> unlocked = this.progressService.unlockedAchievements(uuid);
        final int totalAchievements = this.registry.allAchievements().size();
        final int totalUnlocked = unlocked.size();

        // ── 頂部列 ──
        // slot 2: 研究等級
        gui.setItem(2, this.xpSummaryItem(uuid));
        // slot 6: 科技成就總覽
        gui.setItem(6, this.titleItem(totalUnlocked, totalAchievements));

        // 上一頁（slot 0）— 使用科技書同款按鈕
        if (page > 0) {
            gui.setItem(0, this.itemFactory.buildGuiButton("main-page-prev", Material.ARROW, "上一頁",
                    List.of("頁數 {page}/{max-page}"),
                    java.util.Map.of("page", String.valueOf(page), "max-page", String.valueOf(maxPage + 1))));
        }
        // 下一頁（slot 8）
        if (page < maxPage) {
            gui.setItem(8, this.itemFactory.buildGuiButton("main-page-next", Material.SPECTRAL_ARROW, "下一頁",
                    List.of("頁數 {page}/{max-page}"),
                    java.util.Map.of("page", String.valueOf(page + 2), "max-page", String.valueOf(maxPage + 1))));
        }

        // ── 成就展示區 ──
        final String selectedTitle = this.progressService.getSelectedTitle(uuid);
        final int startIndex = page * ITEMS_PER_PAGE;
        for (int i = 0; i < DISPLAY_SLOTS.length; i++) {
            final int index = startIndex + i;
            if (index < achievements.size()) {
                final AchievementDefinition achievement = achievements.get(index);
                final boolean isUnlocked = unlocked.contains(achievement.id());
                gui.setItem(DISPLAY_SLOTS[i], this.achievementItem(achievement, isUnlocked, uuid, selectedTitle));
            }
        }

        // ── 底部列留空（底圖由 glyph font 標題渲染） ──
    }

    // ── 成就物品（使用 Nexo 材質） ──

    private ItemStack achievementItem(final AchievementDefinition achievement, final boolean unlocked,
                                     final UUID uuid, final String selectedTitle) {
        final boolean isEquipped = unlocked && achievement.id().equals(selectedTitle);

        // 嘗試使用 Nexo 自訂材質
        final String nexoId = isEquipped ? "tp_title_equipped"
                : (unlocked ? "tp_title_unlocked" : "tp_title_locked");
        ItemStack stack = this.itemFactory.tryBuildNexoItemPublic(nexoId);
        if (stack == null) {
            // fallback
            final Material material = isEquipped ? Material.GOLD_NUGGET : (unlocked ? Material.LIME_DYE : Material.GRAY_DYE);
            stack = new ItemStack(material);
        }

        final ItemMeta meta = stack.getItemMeta();

        final TextColor nameColor = isEquipped ? TITLE_EQUIPPED : (unlocked ? UNLOCKED_COLOR : LOCKED_COLOR);
        final String prefix = isEquipped ? "★ " : (unlocked ? "✔ " : "✖ ");
        meta.displayName(Component.text(prefix + achievement.displayName(), nameColor)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, unlocked));

        final List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (isEquipped) {
            lore.add(Component.text("  ★ 目前裝備的稱號", TITLE_EQUIPPED).decoration(TextDecoration.ITALIC, false));
        } else if (unlocked) {
            lore.add(Component.text("  ✅ 已完成", UNLOCKED_COLOR).decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(Component.text("  🔒 未解鎖", LOCKED_COLOR).decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.empty());
        lore.add(Component.text("  📋 " + achievement.description(), TextColor.color(0xDDDDDD)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (!unlocked && achievement.hint() != null && !achievement.hint().isBlank()) {
            lore.add(Component.text("  💡 提示: " + achievement.hint(), HINT_COLOR).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        // 獎勵
        if (achievement.rewardXp() > 0 || achievement.rewardTokens() > 0) {
            lore.add(Component.text("  ─ 獎勵 ─", TextColor.color(0x888888)).decoration(TextDecoration.ITALIC, false));
            if (achievement.rewardXp() > 0) {
                lore.add(Component.text("  ⭐ 研究經驗 +" + achievement.rewardXp(), XP_COLOR).decoration(TextDecoration.ITALIC, false));
            }
            if (achievement.rewardTokens() > 0) {
                lore.add(Component.text("  🪙 能源代幣 ×" + achievement.rewardTokens(), TOKEN_COLOR).decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
        }

        if (this.titleService != null && this.titleService.hasTitle(achievement.id())) {
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



        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        return stack;
    }

    // ── 標題物品 ──

    private ItemStack titleItem(final int unlocked, final int total) {
        final ItemStack stack = new ItemStack(Material.NETHER_STAR);
        final ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("★ 科技成就總覽 ★", TITLE_COLOR)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, true));

        final float pct = total > 0 ? (float) unlocked / total : 0;
        final List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("  總進度: " + unlocked + " / " + total + " (" + (int) (pct * 100) + "%)", PROGRESS_COLOR).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  " + this.buildProgressBar(pct, 20), pct >= 1.0f ? UNLOCKED_COLOR : PROGRESS_COLOR).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("  點擊成就查看詳情 / 裝備稱號", TextColor.color(0x888888)).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
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
}
