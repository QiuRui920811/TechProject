package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.util.ItemFactoryUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 排行榜系統 — 從 PlayerProgressService 的已載入玩家中擷取資料，
 * 依類別排序並展示在 GUI 上。
 *
 * <p>類別：
 * <ul>
 *   <li>TOTAL_SKILL_LEVEL — 所有技能等級總和</li>
 *   <li>COMBAT_LEVEL — 戰鬥技能等級</li>
 *   <li>GATHERING_LEVEL — 採集技能等級</li>
 *   <li>ENGINEERING_LEVEL — 工程技能等級</li>
 *   <li>TECH_XP — 總科技經驗</li>
 *   <li>MOBS_KILLED — 累積擊殺怪物數（由 stats 統計）</li>
 *   <li>QUESTS_CLAIMED — 累積已領取任務數</li>
 * </ul>
 *
 * <p>資料範圍：線上玩家 + 已載入快取的離線玩家（最近登入過的玩家），
 * 不會主動 IO 所有歷史玩家，避免記憶體爆炸。
 */
public final class LeaderboardService {

    private static final int TOP_N = 10;

    // ── 主題色板（完全自訂 hex，避開 Minecraft 原版 16 色） ──
    private static final TextColor C_ROSE_DEEP    = TextColor.color(0xBE185D);
    private static final TextColor C_ROSE_HOT     = TextColor.color(0xEC4899);
    private static final TextColor C_ROSE_SOFT    = TextColor.color(0xF9A8D4);
    private static final TextColor C_AMBER        = TextColor.color(0xF59E0B);
    private static final TextColor C_AMBER_LIGHT  = TextColor.color(0xFCD34D);
    private static final TextColor C_CREAM_WARM   = TextColor.color(0xFEF3C7);
    private static final TextColor C_EMERALD      = TextColor.color(0x10B981);
    private static final TextColor C_EMERALD_LIGHT = TextColor.color(0x6EE7B7);
    private static final TextColor C_SKY_LIGHT    = TextColor.color(0x7DD3FC);
    private static final TextColor C_VIOLET       = TextColor.color(0x8B5CF6);
    private static final TextColor C_VIOLET_LIGHT = TextColor.color(0xC4B5FD);
    private static final TextColor C_CORAL        = TextColor.color(0xFB7185);
    private static final TextColor C_CORAL_DEEP   = TextColor.color(0xE11D48);
    private static final TextColor C_PEARL        = TextColor.color(0xF3F4F6);
    private static final TextColor C_SLATE_LIGHT  = TextColor.color(0xCBD5E1);
    private static final TextColor C_SLATE        = TextColor.color(0x94A3B8);
    private static final TextColor C_SLATE_DIM    = TextColor.color(0x64748B);
    private static final TextColor C_SLATE_DARK   = TextColor.color(0x475569);
    // 榜單金銀銅
    private static final TextColor C_MEDAL_GOLD   = TextColor.color(0xFFD700);
    private static final TextColor C_MEDAL_SILVER = TextColor.color(0xE5E7EB);
    private static final TextColor C_MEDAL_BRONZE = TextColor.color(0xD97706);

    // Aliases
    private static final TextColor C_NAME    = C_ROSE_HOT;
    private static final TextColor C_NUMERAL = C_ROSE_SOFT;
    private static final TextColor C_WHITE   = C_PEARL;
    private static final TextColor C_LGRAY   = C_SLATE_LIGHT;
    private static final TextColor C_DGRAY   = C_SLATE_DARK;
    private static final TextColor C_YELLOW  = C_AMBER_LIGHT;
    private static final TextColor C_GOLD    = C_AMBER;
    private static final TextColor C_GREEN   = C_EMERALD;
    private static final TextColor C_RED     = C_CORAL;
    private static final TextColor C_AQUA    = C_SKY_LIGHT;
    private static final TextColor C_SILVER  = C_MEDAL_SILVER;
    private static final TextColor C_BRONZE  = C_MEDAL_BRONZE;

    // ── 文案裝飾 ────────────────────────────────────────
    private static Component decorLine() {
        final int[] cols = {0xBE185D, 0xDB2777, 0xEC4899, 0xF472B6,
                            0xF9A8D4, 0xF472B6, 0xEC4899, 0xDB2777, 0xBE185D};
        Component c = Component.empty();
        for (int col : cols) {
            c = c.append(Component.text("▬▬", TextColor.color(col))
                    .decoration(TextDecoration.ITALIC, false));
        }
        return c;
    }

    private static Component decorLineGold() {
        final int[] cols = {0xB45309, 0xD97706, 0xF59E0B, 0xFBBF24,
                            0xFFD700, 0xFBBF24, 0xF59E0B, 0xD97706, 0xB45309};
        Component c = Component.empty();
        for (int col : cols) {
            c = c.append(Component.text("▬▬", TextColor.color(col))
                    .decoration(TextDecoration.ITALIC, false));
        }
        return c;
    }

    private static Component gradientText(final String text, final int startHex,
                                           final int endHex, final boolean bold) {
        Component c = Component.empty();
        final int len = text.length();
        final int sr = (startHex >> 16) & 0xFF;
        final int sg = (startHex >> 8) & 0xFF;
        final int sb = startHex & 0xFF;
        final int er = (endHex >> 16) & 0xFF;
        final int eg = (endHex >> 8) & 0xFF;
        final int eb = endHex & 0xFF;
        for (int i = 0; i < len; i++) {
            final double t = len <= 1 ? 0 : (double) i / (len - 1);
            final int r = (int) (sr + (er - sr) * t);
            final int g = (int) (sg + (eg - sg) * t);
            final int b = (int) (sb + (eb - sb) * t);
            Component ch = Component.text(String.valueOf(text.charAt(i)),
                            TextColor.color(r, g, b))
                    .decoration(TextDecoration.ITALIC, false);
            if (bold) ch = ch.decoration(TextDecoration.BOLD, true);
            c = c.append(ch);
        }
        return c;
    }

    /** 主頁 mask：2=藍玻璃邊框, 1=黑玻璃填充, 0=可放置內容 */
    private static final String[] LEADERBOARD_MASK = {
            "211101112",  // row 0: slot 4 = header
            "200000112",  // row 1: slots 10-14 = ranks 1-5
            "200000112",  // row 2: slots 19-23 = ranks 6-10
            "211101112",  // row 3: slot 31 = viewer self
            "200000002",  // row 4: slots 37-43 = category switcher
            "211101112",  // row 5: slot 49 = close
    };

    private static final int[] RANK_SLOTS = {10, 11, 12, 13, 14, 19, 20, 21, 22, 23};
    private static final int HEADER_SLOT = 4;
    private static final int SELF_SLOT   = 31;
    private static final int CLOSE_SLOT  = 49;

    private final TechMCPlugin plugin;
    private final PlayerProgressService progressService;
    private final SkillService skillService;
    private final ItemFactoryUtil itemFactory;

    public LeaderboardService(final TechMCPlugin plugin,
                                final PlayerProgressService progressService,
                                final SkillService skillService,
                                final ItemFactoryUtil itemFactory) {
        this.plugin = plugin;
        this.progressService = progressService;
        this.skillService = skillService;
        this.itemFactory = itemFactory;
    }

    // ═══════════════════════════════════════════
    //  資料源 — 已載入玩家 UUID
    // ═══════════════════════════════════════════

    private Set<UUID> collectCandidates() {
        final Set<UUID> ids = new LinkedHashSet<>();
        for (final Player p : Bukkit.getOnlinePlayers()) {
            ids.add(p.getUniqueId());
        }
        ids.addAll(this.progressService.cachedPlayerIds());
        return ids;
    }

    /**
     * 依類別排序取得前 N 名。
     */
    public List<Entry> getTop(final Category category) {
        final Set<UUID> candidates = this.collectCandidates();
        final List<Entry> entries = new ArrayList<>();
        for (final UUID uuid : candidates) {
            final long value = this.scoreFor(uuid, category);
            if (value <= 0L) continue;
            final String name = Bukkit.getOfflinePlayer(uuid).getName();
            entries.add(new Entry(uuid, name == null ? "?" : name, value));
        }
        entries.sort(Comparator.comparingLong((Entry e) -> e.value).reversed());
        if (entries.size() > TOP_N) {
            return entries.subList(0, TOP_N);
        }
        return entries;
    }

    private long scoreFor(final UUID uuid, final Category category) {
        return switch (category) {
            case TOTAL_SKILL_LEVEL -> this.skillService.getTotalSkillLevel(uuid);
            case COMBAT_LEVEL -> this.skillService.getSkillLevel(uuid, SkillService.Skill.COMBAT);
            case GATHERING_LEVEL -> this.skillService.getSkillLevel(uuid, SkillService.Skill.GATHERING);
            case ENGINEERING_LEVEL -> this.skillService.getSkillLevel(uuid, SkillService.Skill.ENGINEERING);
            case TECH_XP -> this.progressService.getTechXpTotal(uuid);
            case MOBS_KILLED -> this.progressService.getStat(uuid, "lb_mobs_killed");
            case QUESTS_CLAIMED -> this.progressService.getStat(uuid, "lb_quests_claimed");
        };
    }

    // ═══════════════════════════════════════════
    //  事件通知 API — 由 listener 呼叫更新計數
    // ═══════════════════════════════════════════

    public void incrementKills(final UUID uuid, final long amount) {
        if (amount <= 0) return;
        this.progressService.incrementStat(uuid, "lb_mobs_killed", amount);
    }

    public void incrementQuestsClaimed(final UUID uuid) {
        this.progressService.incrementStat(uuid, "lb_quests_claimed", 1L);
    }

    // ═══════════════════════════════════════════
    //  GUI
    // ═══════════════════════════════════════════

    public void openLeaderboardMenu(final Player viewer) {
        this.openCategory(viewer, Category.TOTAL_SKILL_LEVEL);
    }

    public void openCategory(final Player viewer, final Category category) {
        final Inventory inv = Bukkit.createInventory(new LeaderboardHolder(category), 54,
                gradientText("✦ 榮耀榜 ✦", 0xEC4899, 0xF9A8D4, true));
        applyMask(inv, LEADERBOARD_MASK);

        // 頂部標題（slot 4）
        final List<Entry> top = this.getTop(category);
        final long best = top.isEmpty() ? 0L : top.get(0).value;
        final ItemStack header = new ItemStack(category.iconMaterial);
        final ItemMeta hMeta = header.getItemMeta();
        if (hMeta != null) {
            hMeta.displayName(gradientText("✦ " + category.displayName + " 排行 ✦",
                    0xEC4899, 0xF9A8D4, true));
            hMeta.lore(List.of(
                    decorLine(),
                    plain(category.description, C_SLATE_DIM, false)
                            .decoration(TextDecoration.ITALIC, true),
                    Component.empty(),
                    plain("◆ ", C_MEDAL_GOLD, false)
                            .append(plain("榜首 ", C_CREAM_WARM, false))
                            .append(plain(formatNumber(best), C_AMBER_LIGHT, false)),
                    plain("◆ ", C_VIOLET, false)
                            .append(plain("上榜 ", C_CREAM_WARM, false))
                            .append(plain(top.size() + " / " + TOP_N,
                                    C_VIOLET_LIGHT, false)),
                    decorLine(),
                    plain("▸ 名單僅統計已載入玩家資料", C_SLATE, false)));
            hMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            header.setItemMeta(hMeta);
        }
        inv.setItem(HEADER_SLOT, header);

        // 排行榜條目
        for (int i = 0; i < top.size() && i < RANK_SLOTS.length; i++) {
            inv.setItem(RANK_SLOTS[i], this.buildRankIcon(i + 1, top.get(i), category));
        }

        // 空位佔位
        for (int i = top.size(); i < RANK_SLOTS.length; i++) {
            final ItemStack empty = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
            final ItemMeta eMeta = empty.getItemMeta();
            if (eMeta != null) {
                eMeta.displayName(plain("#" + (i + 1) + "  虛席以待", C_SLATE_DIM, false)
                        .decoration(TextDecoration.ITALIC, true));
                eMeta.lore(List.of(
                        decorLine(),
                        plain("▸ 等待挑戰者的腳步", C_SLATE, false),
                        decorLine()));
                empty.setItemMeta(eMeta);
            }
            inv.setItem(RANK_SLOTS[i], empty);
        }

        // 玩家自己排名卡片（slot 31）
        final long viewerScore = this.scoreFor(viewer.getUniqueId(), category);
        final int viewerRank = this.computeRank(viewer.getUniqueId(), category);
        final ItemStack selfIcon = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta selfMeta = selfIcon.getItemMeta();
        if (selfMeta instanceof SkullMeta skull) {
            skull.setOwningPlayer(viewer);
            skull.displayName(gradientText("✦ " + viewer.getName() + " 的戰績 ✦",
                    0xEC4899, 0xF9A8D4, true));
            final List<Component> lore = new ArrayList<>();
            lore.add(decorLine());
            lore.add(plain(category.displayName, C_SLATE_DIM, false)
                    .decoration(TextDecoration.ITALIC, true));
            lore.add(Component.empty());
            lore.add(plain("◆ ", C_AMBER, false)
                    .append(plain("排名 ", C_CREAM_WARM, false))
                    .append(plain(viewerRank > 0 ? "#" + viewerRank : "未上榜",
                            viewerRank > 0 ? C_AMBER_LIGHT : C_CORAL, false)));
            lore.add(plain("◆ ", C_AMBER, false)
                    .append(plain("數值 ", C_CREAM_WARM, false))
                    .append(plain(formatNumber(viewerScore), C_AMBER_LIGHT, false)));
            lore.add(decorLineGold());
            if (viewerRank > 0 && viewerRank <= TOP_N) {
                lore.add(plain("★ ", C_MEDAL_GOLD, false)
                        .append(plain("你正站在榮耀榜上", C_EMERALD_LIGHT, true)));
            } else if (best > 0) {
                lore.add(plain("▸ ", C_CORAL, false)
                        .append(plain("距榜首還差 ", C_SLATE_LIGHT, false))
                        .append(plain(formatNumber(Math.max(0, best - viewerScore)),
                                C_CORAL, false)));
            }
            skull.lore(lore);
            selfIcon.setItemMeta(skull);
        }
        inv.setItem(SELF_SLOT, selfIcon);

        // 類別切換（slots 37-43）
        final int[] catSlots = {37, 38, 39, 40, 41, 42, 43};
        final Category[] cats = Category.values();
        for (int i = 0; i < cats.length && i < catSlots.length; i++) {
            final Category cat = cats[i];
            final ItemStack icon = new ItemStack(cat.iconMaterial);
            final ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                final boolean active = cat == category;
                if (active) {
                    meta.displayName(gradientText("◆ " + cat.displayName + " ◆",
                            0x10B981, 0x6EE7B7, true));
                } else {
                    meta.displayName(gradientText(cat.displayName,
                            0xEC4899, 0xF9A8D4, true));
                }
                meta.lore(List.of(
                        decorLine(),
                        plain(cat.description, C_SLATE_DIM, false)
                                .decoration(TextDecoration.ITALIC, true),
                        Component.empty(),
                        plain("» ", C_SLATE_DARK, false)
                                .append(plain(active ? "目前顯示中" : "點擊切換榜單",
                                        active ? C_EMERALD_LIGHT : C_AMBER_LIGHT, false)),
                        decorLine()));
                meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
                icon.setItemMeta(meta);
            }
            inv.setItem(catSlots[i], icon);
        }

        // 關閉按鈕
        inv.setItem(CLOSE_SLOT, buttonClose());

        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK,
                SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    private int computeRank(final UUID uuid, final Category category) {
        final List<Entry> all = new ArrayList<>();
        for (final UUID id : this.collectCandidates()) {
            final long v = this.scoreFor(id, category);
            if (v <= 0) continue;
            all.add(new Entry(id, "", v));
        }
        all.sort(Comparator.comparingLong((Entry e) -> e.value).reversed());
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).uuid.equals(uuid)) return i + 1;
        }
        return -1;
    }

    private ItemStack buildRankIcon(final int rank, final Entry entry, final Category category) {
        final Material icon;
        final TextColor color;
        final int gradStart, gradEnd;
        switch (rank) {
            case 1 -> {
                icon = Material.NETHER_STAR;
                color = C_MEDAL_GOLD;
                gradStart = 0xB45309; gradEnd = 0xFCD34D;
            }
            case 2 -> {
                icon = Material.DIAMOND;
                color = C_MEDAL_SILVER;
                gradStart = 0x64748B; gradEnd = 0xE5E7EB;
            }
            case 3 -> {
                icon = Material.EMERALD;
                color = C_MEDAL_BRONZE;
                gradStart = 0x9A3412; gradEnd = 0xFB923C;
            }
            default -> {
                icon = rank <= 5 ? Material.AMETHYST_SHARD : Material.PAPER;
                color = rank <= 5 ? C_VIOLET_LIGHT : C_SLATE_LIGHT;
                gradStart = rank <= 5 ? 0x7C3AED : 0x475569;
                gradEnd   = rank <= 5 ? 0xC4B5FD : 0xCBD5E1;
            }
        }
        final ItemStack stack = new ItemStack(icon);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(gradientText("#" + rank + "  " + entry.name,
                    gradStart, gradEnd, true));
            final List<Component> lore = new ArrayList<>();
            lore.add(decorLine());
            lore.add(plain(category.displayName, C_SLATE_DIM, false)
                    .decoration(TextDecoration.ITALIC, true));
            lore.add(Component.empty());
            lore.add(plain("◆ ", C_AMBER, false)
                    .append(plain("數值 ", C_CREAM_WARM, false))
                    .append(plain(formatNumber(entry.value), color, false)));
            if (rank == 1) {
                lore.add(decorLineGold());
                lore.add(plain("★ ", C_MEDAL_GOLD, false)
                        .append(plain("冠絕群倫", C_MEDAL_GOLD, true)));
            } else if (rank <= 3) {
                lore.add(decorLineGold());
                lore.add(plain("★ ", color, false)
                        .append(plain("榮登三甲", color, true)));
            } else {
                lore.add(decorLine());
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void handleClick(final Player viewer, final int slot, final Category current) {
        // 類別切換：slots 37-43
        if (slot >= 37 && slot <= 43) {
            final int idx = slot - 37;
            final Category[] cats = Category.values();
            if (idx < cats.length && cats[idx] != current) {
                this.openCategory(viewer, cats[idx]);
                return;
            }
        }
        if (slot == CLOSE_SLOT) {
            viewer.closeInventory();
        }
    }

    // ── GUI 小工具 ─────────────────────────────────────
    private static Component plain(final String s, final TextColor color, final boolean bold) {
        Component c = Component.text(s, color).decoration(TextDecoration.ITALIC, false);
        if (bold) c = c.decoration(TextDecoration.BOLD, true);
        return c;
    }

    private static String formatNumber(final long value) {
        if (value >= 1_000_000L) {
            return String.format("%.1fM", value / 1_000_000.0);
        }
        if (value >= 10_000L) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return Long.toString(value);
    }

    private static void applyMask(final Inventory inv, final String[] mask) {
        final ItemStack blue = fillerItem(Material.BLUE_STAINED_GLASS_PANE);
        final ItemStack black = fillerItem(Material.BLACK_STAINED_GLASS_PANE);
        for (int row = 0; row < mask.length; row++) {
            final String line = mask[row];
            for (int col = 0; col < 9 && col < line.length(); col++) {
                final int slot = row * 9 + col;
                switch (line.charAt(col)) {
                    case '2' -> inv.setItem(slot, blue);
                    case '1' -> inv.setItem(slot, black);
                    default -> { /* '0' 保留空槽 */ }
                }
            }
        }
    }

    private static ItemStack fillerItem(final Material material) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static ItemStack buttonClose() {
        final ItemStack close = new ItemStack(Material.BARRIER);
        final ItemMeta meta = close.getItemMeta();
        if (meta != null) {
            meta.displayName(gradientText("✕ 關閉", 0xE11D48, 0xFB7185, true));
            meta.lore(List.of(
                    decorLine(),
                    plain("» ", C_SLATE_DARK, false)
                            .append(plain("關閉榮耀榜", C_SLATE_LIGHT, false)),
                    decorLine()));
            close.setItemMeta(meta);
        }
        return close;
    }

    // ═══════════════════════════════════════════
    //  資料結構
    // ═══════════════════════════════════════════

    public enum Category {
        TOTAL_SKILL_LEVEL("總技能等級", "所有技能等級總和", Material.NETHER_STAR),
        COMBAT_LEVEL("戰鬥", "戰鬥技能等級", Material.DIAMOND_SWORD),
        GATHERING_LEVEL("採集", "採集技能等級", Material.IRON_PICKAXE),
        ENGINEERING_LEVEL("工程", "工程技能等級", Material.REDSTONE),
        TECH_XP("科技經驗", "累積科技經驗總量", Material.EXPERIENCE_BOTTLE),
        MOBS_KILLED("擊殺數", "累積擊殺怪物總數", Material.SKELETON_SKULL),
        QUESTS_CLAIMED("完成任務", "累積已領取任務數", Material.ENDER_EYE);

        public final String displayName;
        public final String description;
        public final Material iconMaterial;

        Category(final String displayName, final String description, final Material iconMaterial) {
            this.displayName = displayName;
            this.description = description;
            this.iconMaterial = iconMaterial;
        }
    }

    public static final class Entry {
        public final UUID uuid;
        public final String name;
        public final long value;

        public Entry(final UUID uuid, final String name, final long value) {
            this.uuid = uuid;
            this.name = name;
            this.value = value;
        }
    }

    public static final class LeaderboardHolder implements InventoryHolder {
        public final Category category;

        public LeaderboardHolder(final Category category) {
            this.category = category;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
