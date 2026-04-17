package com.rui.techproject.service.talent;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.service.PlayerProgressService;
import com.rui.techproject.service.SkillService;
import com.rui.techproject.service.SkillService.Skill;
import com.rui.techproject.service.SkillService.Stat;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 天賦樹 GUI (ValhallaMMO / Path of Exile 風格)：chest 上面 5 列 (45 格) 全部當畫布，
 * 最底下一列固定當工具列 — 8 方向箭頭 + 回到原點。這樣畫布區完全沒有遮擋，
 * 玩家可以自由在超大畫布 {@link TalentSlot#CANVAS_W}×{@link TalentSlot#CANVAS_H} 上拖拉點選。
 *
 * <p>設計原則：畫布區純粹只顯示節點和連線，所有導覽按鈕集中在底部一列，
 * 不蓋到天賦樹本身的視覺空間，方便美術自由設計 HUD。
 *
 * <p>座標轉換：
 * <pre>
 *   guiCol = canvasX - viewportX   (0..8)
 *   guiRow = canvasY - viewportY   (0..4)   ← 注意只有 5 列畫布
 *   guiSlot = guiRow * 9 + guiCol
 * </pre>
 */
public final class TalentGuiService {

    // ── Viewport (上面 5 列畫布，下面 1 列工具列) ──
    private static final int INV_ROWS = 6;             // chest 總列數
    private static final int INV_COLS = 9;
    private static final int INV_SIZE = INV_ROWS * INV_COLS;
    private static final int CANVAS_ROWS = 5;          // 畫布佔上面 5 列
    private static final int TOOLBAR_ROW = 5;          // 底部工具列那一列
    private static final int TOOLBAR_BASE = TOOLBAR_ROW * INV_COLS; // 45
    private static final int MAX_VX = TalentSlot.CANVAS_W - INV_COLS;     // 0 (畫布寬等於 GUI 寬)
    private static final int MAX_VY = TalentSlot.CANVAS_H - CANVAS_ROWS;  // 12

    // ── 底部工具列：中間 3 顆導航鈕 + 左右各 3 個技能切換鈕 ──
    //  45 46 47 │ 48 49 50 │ 51 52 53
    //  戰 探 採 │ ↑  ⊙  ↓ │ 工 研 共
    private static final int BTN_N    = TOOLBAR_BASE + 3; // 48
    private static final int BTN_HOME = TOOLBAR_BASE + 4; // 49
    private static final int BTN_S    = TOOLBAR_BASE + 5; // 50
    /** 6 個技能切換按鈕的 rawSlot，順序對應 {@link Skill#values()}。 */
    private static final int[] SKILL_BTN_SLOTS = {
            TOOLBAR_BASE,     // 45 COMBAT
            TOOLBAR_BASE + 1, // 46 EXPLORATION
            TOOLBAR_BASE + 2, // 47 GATHERING
            TOOLBAR_BASE + 6, // 51 ENGINEERING
            TOOLBAR_BASE + 7, // 52 RESEARCH
            TOOLBAR_BASE + 8, // 53 RESONANCE
    };

    // ── 滾動幅度 ──
    private static final int SCROLL_NORMAL = 2;
    private static final int SCROLL_SHIFT  = 4;

    // ═══════════════════════════════════════════
    //  CustomModelData 掛勾
    // ═══════════════════════════════════════════
    //
    //  Plugin 本身不畫貼圖，只負責把每一格 item 標上一個 CMD 數值；
    //  美術在 resource pack 裡用 `overrides.predicate.custom_model_data`
    //  把每個 CMD 對應到一張自訂貼圖（發光線、亮框節點、HUD 底圖 …）。
    //  沒有 resource pack 時所有 item fallback 回原版玻璃樣式，不會出錯。
    //
    //  線路 & 背景
    private static final int CMD_BG_CANVAS     = 9000; // 畫布底（黑玻璃）
    private static final int CMD_BG_TOOLBAR    = 9010; // 工具列底（紫玻璃）
    private static final int CMD_LINE_LOCKED   = 9001; // 未解鎖連線（淺灰玻璃）
    private static final int CMD_LINE_UNLOCKED = 9002; // 已解鎖連線（淺藍玻璃）
    //
    //  節點：9100 + typeOrd*10 + stateOrd
    //   typeOrd : 0=START 1=MINOR 2=NOTABLE 3=KEYSTONE
    //   stateOrd: 0=locked 1=available 2=invested 3=maxed
    //  例如滿級的 KEYSTONE = 9100 + 30 + 3 = 9133
    private static final int CMD_NODE_BASE = 9100;

    private final TechMCPlugin plugin;
    private final TalentService talentService;
    private final PlayerProgressService progressService;
    private final SkillService skillService;

    /** 每個玩家每棵樹的視窗左上角座標 (vx, vy)；記憶式滾動，關 GUI 再開仍回到原位。 */
    private final Map<UUID, Map<Skill, int[]>> viewportState = new HashMap<>();

    public TalentGuiService(final TechMCPlugin plugin,
                             final TalentService talentService,
                             final PlayerProgressService progressService,
                             final SkillService skillService) {
        this.plugin = plugin;
        this.talentService = talentService;
        this.progressService = progressService;
        this.skillService = skillService;
    }

    // ═══════════════════════════════════════════
    //  對外開啟 API
    // ═══════════════════════════════════════════

    public void openTree(final Player player, final Skill skill) {
        final int[] vp = this.getOrInitViewport(player.getUniqueId(), skill);
        this.render(player, skill, vp[0], vp[1]);
    }

    private int[] getOrInitViewport(final UUID uuid, final Skill skill) {
        final Map<Skill, int[]> byPlayer =
                this.viewportState.computeIfAbsent(uuid, k -> new EnumMap<>(Skill.class));
        return byPlayer.computeIfAbsent(skill, k -> defaultViewport());
    }

    /** 首次開啟時把 START 放到畫布區中央。 */
    private static int[] defaultViewport() {
        final TalentSlot start = TalentSlot.START;
        final int vx = clamp(start.x - INV_COLS / 2, 0, MAX_VX);
        final int vy = clamp(start.y - CANVAS_ROWS / 2, 0, MAX_VY);
        return new int[]{vx, vy};
    }

    // ═══════════════════════════════════════════
    //  繪製
    // ═══════════════════════════════════════════

    // ── Nexo font HUD glyph（搭配 pack/assets/minecraft/font/techproject_skilltree.json） ──
    //  字元: \uF102 (shift 佔位，進 space provider 會算成 -48px)
    //        \uF007 (bitmap 本體，對應 font/techproject_skilltree.png)
    //  只要用 minecraft:techproject_skilltree 這個 font 才會生效
    private static final Key SKILLTREE_MENU_FONT = Key.key("minecraft", "techproject_skilltree");
    private static final String SKILLTREE_MENU_SHIFT = "\uF102";
    private static final String SKILLTREE_MENU_GLYPH = "\uF007";
    private static final String SKILLTREE_MENU_TITLE = SKILLTREE_MENU_SHIFT + SKILLTREE_MENU_GLYPH;

    /**
     * 靜態標題 — 所有 6 棵樹共用，讓客戶端 GUI resource pack 不必重載。
     * 套上 Nexo 手繪 HUD 底圖：透過 custom font 把一個 PUA 字元映射到 256×256 的 PNG，
     * 字元本身是白色（避免 Minecraft 上色時把貼圖變色），沒有斜體。
     */
    private static final Component STATIC_TITLE = Component
            .text(SKILLTREE_MENU_TITLE, NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false)
            .font(SKILLTREE_MENU_FONT);

    /**
     * 繪製 / 更新天賦樹 GUI。
     *
     * <p><b>避免 HUD resource pack 閃爍的關鍵</b>：如果玩家已經開著一個
     * TalentTreeHolder 的箱子，就直接重用那個 Inventory — 只 {@code setItem}
     * 更新每一格、修改 holder 的可變欄位，絕不呼叫 {@code openInventory} 重開。
     * 只有第一次開啟（或上一個箱子已關閉）才會真的建立新 Inventory。
     *
     * <p>為了讓這個重用模式在 6 棵樹之間都成立，標題必須完全靜態
     * （{@link #STATIC_TITLE}），所以點數/樹名都移到 lore 顯示。
     */
    private void render(final Player player, final Skill skill, int vx, int vy) {
        vx = clamp(vx, 0, MAX_VX);
        vy = clamp(vy, 0, MAX_VY);
        final UUID uuid = player.getUniqueId();
        this.talentService.ensureStartUnlocked(uuid, skill);
        this.viewportState.computeIfAbsent(uuid, k -> new EnumMap<>(Skill.class))
                           .put(skill, new int[]{vx, vy});

        // 嘗試重用玩家目前已經開著的 TalentTreeHolder Inventory
        final Inventory existing = currentTalentInventory(player);
        final Inventory inv;
        final TalentTreeHolder holder;
        final boolean reuse = existing != null;
        if (reuse) {
            inv = existing;
            holder = (TalentTreeHolder) inv.getHolder();
            holder.skill = skill;
            holder.vx = vx;
            holder.vy = vy;
        } else {
            holder = new TalentTreeHolder(skill, vx, vy);
            inv = Bukkit.createInventory(holder, INV_SIZE, STATIC_TITLE);
        }

        this.paint(inv, uuid, skill, vx, vy);

        if (!reuse) {
            player.openInventory(inv);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.PLAYERS, 0.7f, 1.3f);
        }
    }

    /** 回傳玩家目前開著的 Talent GUI Inventory，沒有就回 null。 */
    private static Inventory currentTalentInventory(final Player player) {
        final Inventory top = player.getOpenInventory().getTopInventory();
        if (top == null || top.getSize() != INV_SIZE) return null;
        if (!(top.getHolder() instanceof TalentTreeHolder)) return null;
        return top;
    }

    /** 把一個已經存在（或新建）的 Inventory 全部 54 格 setItem 重繪一次。 */
    private void paint(final Inventory inv, final UUID uuid,
                        final Skill skill, final int vx, final int vy) {
        final int points = this.talentService.getAvailablePoints(uuid, skill);

        // 1. 畫布區 (上面 5 列) 清空 — Nexo HUD 底圖從標題 glyph 渲染上來，
        //    填任何 item 都會蓋住底圖，所以直接留空
        for (int i = 0; i < TOOLBAR_BASE; i++) {
            inv.setItem(i, null);
        }

        // 2. 工具列區 (底部那列) 塗紫色玻璃當分隔背景（CMD 供工具列底圖覆蓋）
        final ItemStack toolbarBg = filler(Material.PURPLE_STAINED_GLASS_PANE, CMD_BG_TOOLBAR);
        for (int i = TOOLBAR_BASE; i < INV_SIZE; i++) {
            inv.setItem(i, toolbarBg);
        }

        // 3. 繪製連線（灰/淺藍玻璃）
        this.drawConnections(inv, uuid, skill, vx, vy);

        // 4. 繪製 15 個節點
        for (final TalentSlot slot : TalentSlot.values()) {
            final int col = slot.x - vx;
            final int row = slot.y - vy;
            if (col < 0 || col >= INV_COLS || row < 0 || row >= CANVAS_ROWS) continue;
            inv.setItem(row * INV_COLS + col, this.buildNodeIcon(uuid, skill, slot));
        }

        // 5. 底部工具列：中央 ↑ / ⊙ / ↓ 導航
        final boolean canN = vy > 0;
        final boolean canS = vy < MAX_VY;
        inv.setItem(BTN_N,    this.buildArrowButton("↑ 向上", true,  canN, vx, vy));
        inv.setItem(BTN_HOME, this.buildHomeButton(skill, points));
        inv.setItem(BTN_S,    this.buildArrowButton("↓ 向下", false, canS, vx, vy));

        // 6. 6 個技能切換按鈕：順序對應 Skill.values()
        final Skill[] allSkills = Skill.values();
        for (int i = 0; i < allSkills.length && i < SKILL_BTN_SLOTS.length; i++) {
            inv.setItem(SKILL_BTN_SLOTS[i],
                    this.buildSkillButton(uuid, allSkills[i], allSkills[i] == skill));
        }
    }

    private void drawConnections(final Inventory inv, final UUID uuid,
                                  final Skill skill, final int vx, final int vy) {
        for (final TalentSlot slot : TalentSlot.values()) {
            if (slot.prereqs.isEmpty()) continue;
            for (final String prereqId : slot.prereqs) {
                final TalentSlot prereq = TalentSlot.bySlotId(prereqId);
                if (prereq == null) continue;
                // PoE 風格：整條連線「亮」的條件是「往目標走那端」（子節點 slot）已投入。
                // 這樣走過哪裡才亮到哪裡；START 連出去的路徑預設是不亮的，
                // 等玩家真的投入 MINOR_BASE 才會亮起來。
                final boolean linkActive = this.talentService.isUnlocked(uuid, skill, slot);
                this.drawLine(inv, slot.x, slot.y, prereq.x, prereq.y, vx, vy, linkActive);
            }
        }
    }

    /**
     * 在兩個畫布座標之間以整數步長鋪連線（不覆蓋端點），畫面外自動跳過。
     *
     * <p>因為 TalentSlot 規定任一對 prereq/child 必定共用 x 或 y，所以這裡只會走
     * 純直線或純橫線；方向由差值判斷後換成對應的 Nexo 路徑貼圖。
     */
    private void drawLine(final Inventory inv, final int x1, final int y1,
                           final int x2, final int y2,
                           final int vx, final int vy, final boolean active) {
        final int dx = x2 - x1;
        final int dy = y2 - y1;
        final int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 1) return;
        // 純橫線：dy == 0；純直線：dx == 0
        final boolean horizontal = Math.abs(dx) >= Math.abs(dy);
        final String nexoId = "tp_talent_line_"
                + (horizontal ? "h" : "v")
                + (active ? "_lit" : "");
        ItemStack pane = this.plugin.getItemFactory().tryBuildNexoItemPublic(nexoId);
        if (pane == null) {
            // Fallback：Nexo 未載入時改回原本的玻璃片做法
            final Material mat = active
                    ? Material.LIGHT_BLUE_STAINED_GLASS_PANE
                    : Material.LIGHT_GRAY_STAINED_GLASS_PANE;
            final int lineCmd = active ? CMD_LINE_UNLOCKED : CMD_LINE_LOCKED;
            pane = filler(mat, lineCmd);
        }
        // 清掉 Nexo yml 的 itemname / 原本玻璃片的預設名 — 路徑段是純背景，
        // 不需要 tooltip，避免 hover 到每一段都顯示「Paper」或 "&7·" 等字樣
        final ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.displayName(Component.text(" ")
                    .decoration(TextDecoration.ITALIC, false));
            paneMeta.lore(null);
            paneMeta.addItemFlags(ItemFlag.values());
            pane.setItemMeta(paneMeta);
        }
        for (int i = 1; i < steps; i++) {
            final double t = (double) i / steps;
            final int cx = (int) Math.round(x1 + dx * t);
            final int cy = (int) Math.round(y1 + dy * t);
            final int col = cx - vx;
            final int row = cy - vy;
            if (col < 0 || col >= INV_COLS || row < 0 || row >= CANVAS_ROWS) continue;
            // 不要蓋掉等等要畫的節點
            boolean onNode = false;
            for (final TalentSlot s : TalentSlot.values()) {
                if (s.x == cx && s.y == cy) { onNode = true; break; }
            }
            if (onNode) continue;
            inv.setItem(row * INV_COLS + col, pane);
        }
    }

    // ═══════════════════════════════════════════
    //  節點圖示
    // ═══════════════════════════════════════════

    private ItemStack buildNodeIcon(final UUID uuid, final Skill skill, final TalentSlot slot) {
        final TalentNode node = TalentTreeRegistry.findNode(skill, slot);
        if (node == null) return filler(Material.BLACK_STAINED_GLASS_PANE);

        final int rank = this.talentService.getRank(uuid, skill, slot);
        final int maxRank = node.maxRank();
        final boolean prereqMet = this.talentService.isPrereqMet(uuid, skill, slot);
        final boolean maxed = rank >= maxRank;
        final boolean unlocked = rank > 0;

        // 狀態著色
        final int nameStart;
        final int nameEnd;
        final String stateLabel;
        final int stateColor;
        if (maxed) {
            nameStart = 0xFBBF24;
            nameEnd = 0xFCD34D;
            stateLabel = "✦ 滿級";
            stateColor = 0xFCD34D;
        } else if (unlocked) {
            nameStart = 0x10B981;
            nameEnd = 0x6EE7B7;
            stateLabel = "◆ 已投入 " + rank + "/" + maxRank;
            stateColor = 0x6EE7B7;
        } else if (prereqMet) {
            nameStart = 0xEC4899;
            nameEnd = 0xF9A8D4;
            stateLabel = "◆ 可投入（消耗 " + node.costPerRank() + " 點）";
            stateColor = 0xF9A8D4;
        } else {
            nameStart = 0x64748B;
            nameEnd = 0x94A3B8;
            stateLabel = "✗ 前置節點未解鎖";
            stateColor = 0xFB7185;
        }

        // 節點類型標籤 + CMD type ordinal
        final String typeLabel;
        final int typeColor;
        final int typeOrd;
        switch (node.type()) {
            case START     -> { typeLabel = "起點";     typeColor = 0x6EE7B7; typeOrd = 0; }
            case MINOR     -> { typeLabel = "小節點";   typeColor = 0xCBD5E1; typeOrd = 1; }
            case NOTABLE   -> { typeLabel = "著名節點"; typeColor = 0xFBBF24; typeOrd = 2; }
            case KEYSTONE  -> { typeLabel = "關鍵節點"; typeColor = 0xEC4899; typeOrd = 3; }
            default        -> { typeLabel = "節點";     typeColor = 0xCBD5E1; typeOrd = 1; }
        }

        // CMD state ordinal: 0=locked 1=available 2=invested 3=maxed
        final int stateOrd = maxed ? 3 : (unlocked ? 2 : (prereqMet ? 1 : 0));
        final int nodeCmd = CMD_NODE_BASE + typeOrd * 10 + stateOrd;

        // START 節點（戰士之心等）視覺上就是一段「直向亮路徑」的延伸，
        // 所以直接套用 tp_talent_line_v_lit Nexo item 做底。
        ItemStack stack = null;
        if (slot == TalentSlot.START) {
            stack = this.plugin.getItemFactory().tryBuildNexoItemPublic("tp_talent_line_v_lit");
        }
        if (stack == null) {
            stack = new ItemStack(node.icon());
        }
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setCustomModelData(nodeCmd);
            meta.displayName(gradientText(node.displayName(), nameStart, nameEnd, true));
            final List<Component> lore = new ArrayList<>();
            lore.add(plain("[" + typeLabel + "]", typeColor, false));
            lore.add(plain(node.description(), 0xCBD5E1, false));
            if (!node.flavorText().isEmpty()) {
                lore.add(Component.empty());
                for (final String flavor : node.flavorText()) {
                    lore.add(plain("  \"" + flavor + "\"", 0x64748B, false)
                            .decoration(TextDecoration.ITALIC, true));
                }
            }
            lore.add(Component.empty());
            if (!node.bonusesPerRank().isEmpty()) {
                lore.add(plain("◆ 每層提供", 0xFEF3C7, false));
                for (final Map.Entry<Stat, Double> e : node.bonusesPerRank().entrySet()) {
                    final Stat stat = e.getKey();
                    final double perRank = e.getValue();
                    final String val = stat.isPercentage
                            ? String.format(Locale.ROOT, "+%.1f%% %s", perRank, stat.displayName)
                            : String.format(Locale.ROOT, "+%.1f %s", perRank, stat.displayName);
                    lore.add(plain("  » ", 0x475569, false)
                            .append(plain(val, 0xFCD34D, false)));
                    if (rank > 0) {
                        final String current = stat.isPercentage
                                ? String.format(Locale.ROOT, "目前 +%.1f%%", perRank * rank)
                                : String.format(Locale.ROOT, "目前 +%.1f", perRank * rank);
                        lore.add(plain("     ", 0x475569, false)
                                .append(plain(current, 0x6EE7B7, false)));
                    }
                }
            }
            if (node.isKeystone()) {
                final SpellId spell = node.spell();
                if (spell != null) {
                    lore.add(Component.empty());
                    lore.add(plain("✦ 解鎖法術", 0xEC4899, true));
                    lore.add(plain("  » ", 0x475569, false)
                            .append(plain(spell.displayName, 0xF9A8D4, true))
                            .append(plain(" (" + spell.combo.display + ")", 0xC4B5FD, false)));
                    lore.add(plain("  » ", 0x475569, false)
                            .append(plain(spell.description, 0xCBD5E1, false)));
                    lore.add(plain("  » ", 0x475569, false)
                            .append(plain("消耗 " + spell.manaCost + " 魔力", 0x8B5CF6, false))
                            .append(plain("　冷卻 " + (spell.cooldownTicks / 20) + " 秒",
                                    0xFB7185, false)));
                    lore.add(plain("  » ", 0x475569, false)
                            .append(plain("武器：" + spell.weaponHint, 0x94A3B8, false)));
                }
            }
            lore.add(Component.empty());
            lore.add(plain(stateLabel, stateColor, false));
            if (prereqMet && !maxed) {
                lore.add(plain("▸ 點擊投入 1 層", 0xC4B5FD, false));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            // 不加附魔閃光 — 狀態差異由 Nexo 手繪貼圖本身表示
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ═══════════════════════════════════════════
    //  方向箭頭
    // ═══════════════════════════════════════════

    private ItemStack buildHomeButton(final Skill skill, final int availablePoints) {
        final TalentTree tree = TalentTreeRegistry.tree(skill);
        final String treeName = tree != null ? tree.displayName() : skill.displayName;
        ItemStack stack = this.plugin.getItemFactory().tryBuildNexoItemPublic("tp_talent_home");
        if (stack == null) {
            stack = new ItemStack(Material.NETHER_STAR);
        }
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(gradientText("⊙ " + treeName + " 天賦樹", 0xFBBF24, 0xFCD34D, true));
            final List<Component> lore = new ArrayList<>();
            lore.add(plain("可用天賦點 ", 0xF3F4F6, false)
                    .append(plain(String.valueOf(availablePoints), 0xFCD34D, true)));
            lore.add(Component.empty());
            lore.add(plain("▸ 點擊 將視窗移回 START 節點", 0xCBD5E1, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack buildSkillButton(final UUID uuid, final Skill s, final boolean current) {
        final int pts = this.talentService.getAvailablePoints(uuid, s);
        // 優先用 Nexo 自訂材質按鈕（tp_talent_btn_{id}[_active]），沒有就 fallback 到原版 icon material
        final String nexoId = "tp_talent_btn_" + s.id + (current ? "_active" : "");
        ItemStack stack = this.plugin.getItemFactory().tryBuildNexoItemPublic(nexoId);
        if (stack == null) {
            stack = new ItemStack(s.iconMaterial);
        }
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            final int nameStart = current ? 0xFBBF24 : 0xC4B5FD;
            final int nameEnd   = current ? 0xFCD34D : 0xDDD6FE;
            meta.displayName(gradientText(
                    (current ? "▶ " : "  ") + s.displayName + " 天賦樹",
                    nameStart, nameEnd, true));
            final List<Component> lore = new ArrayList<>();
            lore.add(plain(s.description, 0x94A3B8, false));
            // 施法武器提示（對應科技物品名稱）
            final String weaponName = switch (s) {
                case COMBAT      -> "蒼浪之刃";
                case EXPLORATION -> "疾風獵弓";
                case GATHERING   -> "大地之鎬";
                case ENGINEERING -> "動力扳手";
                case RESEARCH    -> "奧術法杖";
                case RESONANCE   -> "共鳴水晶";
            };
            lore.add(plain("施法武器：", 0x94A3B8, false)
                    .append(plain(weaponName, 0x7DD3FC, false)));
            lore.add(Component.empty());
            lore.add(plain("可用點數 ", 0xCBD5E1, false)
                    .append(plain(String.valueOf(pts), 0xFCD34D, true)));
            lore.add(Component.empty());
            if (current) {
                lore.add(plain("◆ 目前檢視中", 0x6EE7B7, true));
            } else {
                lore.add(plain("▸ 點擊 切換到此天賦樹", 0xC4B5FD, false));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            // 選中狀態改用 Nexo _active 亮框貼圖本身呈現，不再疊附魔閃光
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack buildArrowButton(final String label, final boolean up,
                                        final boolean enabled,
                                        final int vx, final int vy) {
        final String nexoId = "tp_talent_arrow_" + (up ? "up" : "down");
        ItemStack stack = this.plugin.getItemFactory().tryBuildNexoItemPublic(nexoId);
        if (stack == null) {
            final Material mat = enabled ? Material.ARROW : Material.GRAY_DYE;
            stack = new ItemStack(mat);
        }
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(gradientText(label,
                    enabled ? 0x6EE7B7 : 0x475569,
                    enabled ? 0x10B981 : 0x64748B, true));
            final List<Component> lore = new ArrayList<>();
            lore.add(plain("視窗 (" + vx + ", " + vy + ")", 0x94A3B8, false));
            lore.add(plain("畫布 " + TalentSlot.CANVAS_W + "×" + TalentSlot.CANVAS_H,
                    0x64748B, false));
            if (enabled) {
                lore.add(Component.empty());
                lore.add(plain("▸ 點擊 移動 " + SCROLL_NORMAL + " 格", 0xCBD5E1, false));
                lore.add(plain("▸ Shift 點擊 移動 " + SCROLL_SHIFT + " 格", 0xCBD5E1, false));
            } else {
                lore.add(Component.empty());
                lore.add(plain("✗ 已到畫布邊界", 0xFB7185, false));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ═══════════════════════════════════════════
    //  點擊處理
    // ═══════════════════════════════════════════

    public boolean handleClick(final Player player, final int rawSlot, final Object holder,
                                final boolean rightClick, final boolean shiftClick) {
        if (!(holder instanceof TalentTreeHolder treeHolder)) return false;

        // 1. 底部工具列：回到起點按鈕
        if (rawSlot == BTN_HOME) {
            final int[] home = defaultViewport();
            this.render(player, treeHolder.skill, home[0], home[1]);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.PLAYERS, 0.5f, 1.6f);
            return true;
        }

        // 2. 底部工具列：↑ / ↓
        final int step = shiftClick ? SCROLL_SHIFT : SCROLL_NORMAL;
        int dy = 0;
        if (rawSlot == BTN_N) dy = -step;
        else if (rawSlot == BTN_S) dy = step;
        if (dy != 0) {
            this.render(player, treeHolder.skill,
                    treeHolder.vx, treeHolder.vy + dy);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK,
                    SoundCategory.PLAYERS, 0.4f, 1.6f);
            return true;
        }

        // 3. 底部工具列：6 個技能切換按鈕
        final Skill[] allSkills = Skill.values();
        for (int i = 0; i < allSkills.length && i < SKILL_BTN_SLOTS.length; i++) {
            if (rawSlot == SKILL_BTN_SLOTS[i]) {
                final Skill target = allSkills[i];
                if (target != treeHolder.skill) {
                    this.openTree(player, target);
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                            SoundCategory.PLAYERS, 0.6f, 1.4f);
                }
                return true;
            }
        }

        // 4. 畫布節點點擊：只處理上面 5 列
        final int row = rawSlot / INV_COLS;
        final int col = rawSlot % INV_COLS;
        if (row >= CANVAS_ROWS) return true; // 工具列空位點擊忽略
        final int cx = col + treeHolder.vx;
        final int cy = row + treeHolder.vy;
        TalentSlot clicked = null;
        for (final TalentSlot s : TalentSlot.values()) {
            if (s.x == cx && s.y == cy) { clicked = s; break; }
        }
        if (clicked != null) {
            if (this.talentService.investPoint(player, treeHolder.skill, clicked)) {
                this.render(player, treeHolder.skill, treeHolder.vx, treeHolder.vy);
            }
        }
        return true;
    }

    // ═══════════════════════════════════════════
    //  清理（退出伺服器時釋放視窗狀態）
    // ═══════════════════════════════════════════

    public void clearPlayer(final UUID uuid) {
        this.viewportState.remove(uuid);
    }

    // ═══════════════════════════════════════════
    //  Inventory holder
    // ═══════════════════════════════════════════

    /**
     * Holder 內的狀態全部可變：切換技能樹、捲動視窗都只修改這個物件，
     * 不重新開新的 Inventory，讓客戶端的 HUD resource pack 維持穩定、不會閃爍。
     */
    public static final class TalentTreeHolder implements InventoryHolder {
        public Skill skill;
        public int vx;
        public int vy;
        public TalentTreeHolder(final Skill skill, final int vx, final int vy) {
            this.skill = skill;
            this.vx = vx;
            this.vy = vy;
        }
        @Override public Inventory getInventory() { return null; }
    }

    // ═══════════════════════════════════════════
    //  共用小工具
    // ═══════════════════════════════════════════

    private static int clamp(final int v, final int lo, final int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static ItemStack filler(final Material material) {
        return filler(material, 0);
    }

    /** 建立一個純背景 item，並選擇性掛上 CustomModelData 供 resource pack 換貼圖。 */
    private static ItemStack filler(final Material material, final int customModelData) {
        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(ItemFlag.values());
            if (customModelData > 0) {
                meta.setCustomModelData(customModelData);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Component plain(final String s, final int hex, final boolean bold) {
        Component c = Component.text(s, TextColor.color(hex))
                .decoration(TextDecoration.ITALIC, false);
        if (bold) c = c.decoration(TextDecoration.BOLD, true);
        return c;
    }

    private static Component gradientText(final String text, final int startHex,
                                           final int endHex, final boolean bold) {
        // Root 顯式 italic:false，否則 Minecraft 的 display name 預設會套斜體
        Component c = Component.empty().decoration(TextDecoration.ITALIC, false);
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
}
