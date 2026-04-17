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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每日 / 每週任務系統。
 *
 * <p>任務在每個玩家第一次開啟選單時按玩家獨立隨機分配（依 UUID + 日期 seed），
 * 這樣同一天所有玩家的任務可能不同，但對同一位玩家整天固定。
 *
 * <p>任務類型：
 * <ul>
 *   <li>KILL_MOBS — 擊殺 N 隻怪物</li>
 *   <li>MINE_BLOCKS — 採集 N 個方塊（任意）</li>
 *   <li>GAIN_SKILL_XP — 獲得 N 點任意技能經驗</li>
 *   <li>CRAFT_TECH — 合成 N 個科技物品</li>
 * </ul>
 *
 * <p>獎勵：每日任務 = 40 技能 XP + 10 tech XP；每週任務 = 200 技能 XP + 80 tech XP + 稀有掉落。
 *
 * <p>持久化：全部透過 PlayerProgressService stats 存
 * ("daily_{type}_{date}_progress", "daily_{type}_{date}_claimed") 與週任務同理。
 */
public final class DailyQuestService {

    private static final int DAILY_QUEST_COUNT = 3;
    private static final int WEEKLY_QUEST_COUNT = 2;
    private static final ZoneId SERVER_ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final TechMCPlugin plugin;
    private final PlayerProgressService progressService;
    private final ItemFactoryUtil itemFactory;
    private final SkillService skillService;

    /** 快取：玩家 UUID → 當日任務清單（避免每次重新 seed） */
    private final Map<UUID, List<Quest>> dailyCache = new ConcurrentHashMap<>();
    private final Map<UUID, List<Quest>> weeklyCache = new ConcurrentHashMap<>();
    private final Map<UUID, String> dailyCacheKey = new ConcurrentHashMap<>();
    private final Map<UUID, String> weeklyCacheKey = new ConcurrentHashMap<>();

    public DailyQuestService(final TechMCPlugin plugin,
                              final PlayerProgressService progressService,
                              final ItemFactoryUtil itemFactory,
                              final SkillService skillService) {
        this.plugin = plugin;
        this.progressService = progressService;
        this.itemFactory = itemFactory;
        this.skillService = skillService;
    }

    // ═══════════════════════════════════════════
    //  任務清單產生（per player per day）
    // ═══════════════════════════════════════════

    public List<Quest> getDailyQuests(final UUID uuid) {
        final String todayKey = LocalDate.now(SERVER_ZONE).format(DAY_FMT);
        final String cacheKey = this.dailyCacheKey.get(uuid);
        if (todayKey.equals(cacheKey) && this.dailyCache.containsKey(uuid)) {
            return this.dailyCache.get(uuid);
        }
        final long seed = ((long) uuid.hashCode() << 32) | Long.parseLong(todayKey);
        final List<Quest> quests = generateQuests(seed, DAILY_QUEST_COUNT, "daily_" + todayKey, false);
        this.dailyCache.put(uuid, quests);
        this.dailyCacheKey.put(uuid, todayKey);
        return quests;
    }

    public List<Quest> getWeeklyQuests(final UUID uuid) {
        final LocalDate now = LocalDate.now(SERVER_ZONE);
        final WeekFields wf = WeekFields.of(Locale.getDefault());
        final String weekKey = now.getYear() + "w" + now.get(wf.weekOfYear());
        final String cacheKey = this.weeklyCacheKey.get(uuid);
        if (weekKey.equals(cacheKey) && this.weeklyCache.containsKey(uuid)) {
            return this.weeklyCache.get(uuid);
        }
        final long seed = ((long) uuid.hashCode() << 32) | weekKey.hashCode();
        final List<Quest> quests = generateQuests(seed, WEEKLY_QUEST_COUNT, "weekly_" + weekKey, true);
        this.weeklyCache.put(uuid, quests);
        this.weeklyCacheKey.put(uuid, weekKey);
        return quests;
    }

    private static List<Quest> generateQuests(final long seed, final int count,
                                                final String prefix, final boolean weekly) {
        final Random rng = new Random(seed);
        final QuestType[] types = QuestType.values();
        final List<QuestType> pool = new ArrayList<>(Arrays.asList(types));
        Collections.shuffle(pool, rng);
        final List<Quest> result = new ArrayList<>();
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            final QuestType type = pool.get(i);
            final int target = weekly ? type.weeklyTarget : type.dailyTarget;
            result.add(new Quest(prefix + "_" + type.name().toLowerCase(Locale.ROOT),
                    type, target, weekly));
        }
        return result;
    }

    // ═══════════════════════════════════════════
    //  進度 API（由各事件監聽呼叫）
    // ═══════════════════════════════════════════

    public void onMobKill(final Player player) {
        this.advance(player, QuestType.KILL_MOBS, 1);
    }

    public void onBlockMine(final Player player) {
        this.advance(player, QuestType.MINE_BLOCKS, 1);
    }

    public void onSkillXp(final Player player, final long amount) {
        this.advance(player, QuestType.GAIN_SKILL_XP, (int) Math.min(Integer.MAX_VALUE, amount));
    }

public void onTechCraft(final Player player) {
        this.advance(player, QuestType.CRAFT_TECH, 1);
    }

    private void advance(final Player player, final QuestType type, final int amount) {
        if (player == null || amount <= 0) return;
        final UUID uuid = player.getUniqueId();
        // 每日任務
        for (final Quest quest : this.getDailyQuests(uuid)) {
            if (quest.type == type && !this.isClaimed(uuid, quest)) {
                this.addProgress(uuid, quest, amount);
            }
        }
        // 每週任務
        for (final Quest quest : this.getWeeklyQuests(uuid)) {
            if (quest.type == type && !this.isClaimed(uuid, quest)) {
                this.addProgress(uuid, quest, amount);
            }
        }
    }

    private void addProgress(final UUID uuid, final Quest quest, final int amount) {
        final long current = this.progressService.getStat(uuid, progressKey(quest));
        if (current >= quest.target) return;
        final long newValue = Math.min(quest.target, current + amount);
        this.progressService.setStat(uuid, progressKey(quest), newValue);
        if (newValue >= quest.target && current < quest.target) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.sendMessage(plain("» ", C_DGRAY, false)
                        .append(plain("任務達成 ", C_GREEN, true))
                        .append(plain(quest.type.displayName, C_WHITE, false))
                        .append(plain(quest.weekly ? " [週]" : " [日]",
                                C_NUMERAL, false))
                        .append(plain("  /tech quest 領取", C_YELLOW, false)));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                        SoundCategory.PLAYERS, 0.8f, 1.5f);
            }
        }
    }

    public long getProgress(final UUID uuid, final Quest quest) {
        return this.progressService.getStat(uuid, progressKey(quest));
    }

    public boolean isClaimed(final UUID uuid, final Quest quest) {
        return this.progressService.getStat(uuid, claimKey(quest)) > 0L;
    }

    public boolean isCompleted(final UUID uuid, final Quest quest) {
        return this.getProgress(uuid, quest) >= quest.target;
    }

    // ═══════════════════════════════════════════
    //  獎勵領取
    // ═══════════════════════════════════════════

    public boolean claimReward(final Player player, final Quest quest) {
        if (player == null || quest == null) return false;
        final UUID uuid = player.getUniqueId();
        if (!this.isCompleted(uuid, quest)) {
            player.sendMessage(this.itemFactory.warning("任務尚未完成。"));
            return false;
        }
        if (this.isClaimed(uuid, quest)) {
            player.sendMessage(this.itemFactory.warning("已領取過。"));
            return false;
        }
        this.progressService.setStat(uuid, claimKey(quest), 1L);

        // 發放技能經驗 + 科技經驗
        final long skillXp = quest.weekly ? 200L : 40L;
        final long techXp = quest.weekly ? 80L : 10L;
        this.skillService.grantXp(player, "resonance", skillXp / 2);
        this.skillService.grantXp(player, quest.type.primarySkill, skillXp / 2);
        this.progressService.addTechXp(uuid, techXp);

        player.sendMessage(plain("» ", C_DGRAY, false)
                .append(plain("獲得獎勵 ", C_GREEN, true))
                .append(plain("+" + skillXp + " 技能經驗", C_YELLOW, false))
                .append(plain("  ", C_DGRAY, false))
                .append(plain("+" + techXp + " 科技經驗", C_YELLOW, false)));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 0.8f, 1.2f);

        // 週任務額外掉落
        if (quest.weekly) {
            ItemStack stack = this.itemFactory.tryBuildNexoItemPublic("tech_labyrinth_relic");
            if (stack == null) stack = new ItemStack(Material.DIAMOND, 3);
            final var remaining = player.getInventory().addItem(stack);
            remaining.values().forEach(item ->
                    player.getWorld().dropItemNaturally(player.getLocation(), item));
        }
        return true;
    }

    // ═══════════════════════════════════════════
    //  GUI
    // ═══════════════════════════════════════════

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

    // Aliases（舊訊息呼叫仍沿用）
    private static final TextColor C_NAME    = C_ROSE_HOT;
    private static final TextColor C_NUMERAL = C_ROSE_SOFT;
    private static final TextColor C_WHITE   = C_PEARL;
    private static final TextColor C_LGRAY   = C_SLATE_LIGHT;
    private static final TextColor C_DGRAY   = C_SLATE_DARK;
    private static final TextColor C_YELLOW  = C_AMBER_LIGHT;
    private static final TextColor C_GOLD    = C_AMBER;
    private static final TextColor C_GREEN   = C_EMERALD;
    private static final TextColor C_RED     = C_CORAL;
    private static final TextColor C_TITLE   = C_ROSE_HOT;
    private static final TextColor C_ACCENT  = C_EMERALD;
    private static final TextColor C_INFO    = C_SKY_LIGHT;
    private static final TextColor C_CREAM   = C_CREAM_WARM;
    private static final TextColor C_DIM     = C_SLATE_DARK;
    private static final TextColor C_WEEKLY  = C_VIOLET;

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

    private static Component decorLineAmber() {
        final int[] cols = {0xB45309, 0xD97706, 0xF59E0B, 0xFBBF24,
                            0xFCD34D, 0xFBBF24, 0xF59E0B, 0xD97706, 0xB45309};
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
    private static final String[] QUEST_MASK = {
            "211101112",  // row 0: slot 4 = header
            "211111112",  // row 1: filler
            "210101012",  // row 2: slots 20, 22, 24 = 3 daily
            "211111112",  // row 3: filler
            "211010112",  // row 4: slots 39, 41 = 2 weekly
            "211101112",  // row 5: slot 49 = close
    };

    private static final int[] DAILY_SLOTS  = {20, 22, 24};
    private static final int[] WEEKLY_SLOTS = {39, 41};
    private static final int HEADER_SLOT = 4;
    private static final int CLOSE_SLOT  = 49;

    public void openQuestMenu(final Player player) {
        // 新手引導：查看每日任務
        if (this.plugin.getTutorialChainService() != null) {
            this.plugin.getTutorialChainService().onDailyQuestOpen(player);
        }
        final Inventory inv = Bukkit.createInventory(new QuestMenuHolder(), 54,
                gradientText("✦ 任務日誌 ✦", 0xEC4899, 0xF9A8D4, true));
        final UUID uuid = player.getUniqueId();
        applyMask(inv, QUEST_MASK);

        // 頂部摘要（slot 4）
        final LocalDate today = LocalDate.now(SERVER_ZONE);
        final String dayStr = today.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        final List<Quest> daily = this.getDailyQuests(uuid);
        final List<Quest> weekly = this.getWeeklyQuests(uuid);
        final int dailyDone = (int) daily.stream().filter(q -> this.isClaimed(uuid, q)).count();
        final int weeklyDone = (int) weekly.stream().filter(q -> this.isClaimed(uuid, q)).count();

        final ItemStack header = new ItemStack(Material.WRITABLE_BOOK);
        final ItemMeta hMeta = header.getItemMeta();
        if (hMeta != null) {
            hMeta.displayName(gradientText("✦ 冒險契約 ✦", 0xEC4899, 0xF9A8D4, true));
            hMeta.lore(List.of(
                    decorLine(),
                    plain(player.getName() + "  ·  " + dayStr, C_SLATE_DIM, false)
                            .decoration(TextDecoration.ITALIC, true),
                    Component.empty(),
                    plain("◆ ", C_AMBER, false)
                            .append(plain("每日進度 ", C_CREAM_WARM, false))
                            .append(plain(dailyDone + " / " + daily.size(),
                                    C_AMBER_LIGHT, false)),
                    plain("◆ ", C_VIOLET, false)
                            .append(plain("每週進度 ", C_CREAM_WARM, false))
                            .append(plain(weeklyDone + " / " + weekly.size(),
                                    C_VIOLET_LIGHT, false)),
                    decorLine(),
                    plain("▸ 每日於日切時重置", C_SLATE, false),
                    plain("▸ 每週於週一零時重置", C_SLATE, false)));
            hMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            header.setItemMeta(hMeta);
        }
        inv.setItem(HEADER_SLOT, header);

        // 每日任務
        for (int i = 0; i < daily.size() && i < DAILY_SLOTS.length; i++) {
            inv.setItem(DAILY_SLOTS[i], this.buildQuestIcon(uuid, daily.get(i)));
        }

        // 每週任務
        for (int i = 0; i < weekly.size() && i < WEEKLY_SLOTS.length; i++) {
            inv.setItem(WEEKLY_SLOTS[i], this.buildQuestIcon(uuid, weekly.get(i)));
        }

        // 關閉
        inv.setItem(CLOSE_SLOT, buttonClose());

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN,
                SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    private ItemStack buildQuestIcon(final UUID uuid, final Quest quest) {
        final long progress = this.getProgress(uuid, quest);
        final boolean done = progress >= quest.target;
        final boolean claimed = this.isClaimed(uuid, quest);
        final Material icon = claimed ? Material.LIME_STAINED_GLASS_PANE
                : done ? Material.YELLOW_STAINED_GLASS_PANE
                : quest.type.iconMaterial;
        final double pct = quest.target > 0
                ? Math.min(1.0, progress / (double) quest.target) : 0;
        final long skillXp = quest.weekly ? 200L : 40L;
        final long techXp = quest.weekly ? 80L : 10L;

        final ItemStack stack = new ItemStack(icon);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            final int gradStart, gradEnd;
            if (claimed)    { gradStart = 0x047857; gradEnd = 0x6EE7B7; }
            else if (done)  { gradStart = 0xF59E0B; gradEnd = 0xFCD34D; }
            else if (quest.weekly) { gradStart = 0x7C3AED; gradEnd = 0xC4B5FD; }
            else            { gradStart = 0xEC4899; gradEnd = 0xF9A8D4; }
            meta.displayName(gradientText(quest.type.displayName
                    + "  " + (quest.weekly ? "[週]" : "[日]"),
                    gradStart, gradEnd, true));

            final List<Component> lore = new ArrayList<>();
            lore.add(decorLine());
            lore.add(plain(quest.type.description, C_SLATE_DIM, false)
                    .decoration(TextDecoration.ITALIC, true));
            lore.add(Component.empty());
            lore.add(plain("◆ ", C_AMBER, false)
                    .append(plain("進度", C_CREAM_WARM, false)));
            lore.add(plain("  » ", C_SLATE_DARK, false)
                    .append(plain(progress + " / " + quest.target,
                            done ? C_EMERALD_LIGHT : C_AMBER_LIGHT, false)));
            lore.add(plain("  » ", C_SLATE_DARK, false)
                    .append(plain(String.format("%.1f%%", pct * 100),
                            done ? C_EMERALD_LIGHT : C_AMBER_LIGHT, false)));
            lore.add(decorLineAmber());
            lore.add(plain("◆ ", C_VIOLET, false)
                    .append(plain("獎勵", C_CREAM_WARM, false)));
            lore.add(plain("  » ", C_SLATE_DARK, false)
                    .append(plain("共鳴技能 ", C_SLATE_LIGHT, false))
                    .append(plain("+" + (skillXp / 2), C_AMBER_LIGHT, false)));
            lore.add(plain("  » ", C_SLATE_DARK, false)
                    .append(plain(skillToName(quest.type.primarySkill) + " ",
                            C_SLATE_LIGHT, false))
                    .append(plain("+" + (skillXp / 2), C_AMBER_LIGHT, false)));
            lore.add(plain("  » ", C_SLATE_DARK, false)
                    .append(plain("科技經驗 ", C_SLATE_LIGHT, false))
                    .append(plain("+" + techXp, C_SKY_LIGHT, false)));
            if (quest.weekly) {
                lore.add(plain("  » ", C_SLATE_DARK, false)
                        .append(plain("稀有遺物 ", C_SLATE_LIGHT, false))
                        .append(plain("×1", C_VIOLET_LIGHT, false)));
            }
            lore.add(decorLine());
            if (claimed) {
                lore.add(plain("✓ 獎勵已入囊", C_EMERALD_LIGHT, false));
            } else if (done) {
                lore.add(plain("▸ 點擊領取獎勵", C_AMBER_LIGHT, false));
            } else {
                lore.add(plain("⏳ 繼續冒險以推進進度", C_SLATE, false));
            }
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 由 InventoryClickEvent 呼叫，處理領取按鈕點擊。 */
    public void handleClick(final Player player, final int slot) {
        final UUID uuid = player.getUniqueId();
        final List<Quest> daily = this.getDailyQuests(uuid);
        for (int i = 0; i < daily.size() && i < DAILY_SLOTS.length; i++) {
            if (slot == DAILY_SLOTS[i]) {
                if (this.claimReward(player, daily.get(i))) {
                    this.openQuestMenu(player);
                }
                return;
            }
        }
        final List<Quest> weekly = this.getWeeklyQuests(uuid);
        for (int i = 0; i < weekly.size() && i < WEEKLY_SLOTS.length; i++) {
            if (slot == WEEKLY_SLOTS[i]) {
                if (this.claimReward(player, weekly.get(i))) {
                    this.openQuestMenu(player);
                }
                return;
            }
        }
        if (slot == CLOSE_SLOT) {
            player.closeInventory();
        }
    }

    // ── GUI 小工具 ─────────────────────────────────────
    private static Component plain(final String s, final TextColor color, final boolean bold) {
        Component c = Component.text(s, color).decoration(TextDecoration.ITALIC, false);
        if (bold) c = c.decoration(TextDecoration.BOLD, true);
        return c;
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
                            .append(plain("合上任務日誌", C_SLATE_LIGHT, false)),
                    decorLine()));
            close.setItemMeta(meta);
        }
        return close;
    }

    private static String skillToName(final String id) {
        if (id == null) return "技能";
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "combat" -> "戰鬥";
            case "exploration" -> "探索";
            case "gathering" -> "採集";
            case "engineering" -> "工程";
            case "research" -> "研究";
            case "resonance" -> "共鳴";
            default -> id;
        };
    }

    // ═══════════════════════════════════════════
    //  內部 helper
    // ═══════════════════════════════════════════

    private static String progressKey(final Quest quest) {
        return "quest_" + quest.id + "_progress";
    }

    private static String claimKey(final Quest quest) {
        return "quest_" + quest.id + "_claimed";
    }

    // ═══════════════════════════════════════════
    //  資料結構
    // ═══════════════════════════════════════════

    public enum QuestType {
        KILL_MOBS("擊殺怪物", "擊殺任意怪物累積進度", Material.DIAMOND_SWORD,
                "combat", 30, 150),
        MINE_BLOCKS("採集方塊", "挖掘任何方塊累積進度", Material.IRON_PICKAXE,
                "gathering", 100, 500),
        GAIN_SKILL_XP("獲得技能經驗", "任意技能獲得經驗累積", Material.EXPERIENCE_BOTTLE,
                "research", 200, 1500),
        CRAFT_TECH("合成科技物品", "合成任何科技物品", Material.CRAFTING_TABLE,
                "engineering", 10, 50);

        public final String displayName;
        public final String description;
        public final Material iconMaterial;
        public final String primarySkill;
        public final int dailyTarget;
        public final int weeklyTarget;

        QuestType(final String displayName, final String description,
                  final Material iconMaterial, final String primarySkill,
                  final int dailyTarget, final int weeklyTarget) {
            this.displayName = displayName;
            this.description = description;
            this.iconMaterial = iconMaterial;
            this.primarySkill = primarySkill;
            this.dailyTarget = dailyTarget;
            this.weeklyTarget = weeklyTarget;
        }
    }

    public static final class Quest {
        public final String id;
        public final QuestType type;
        public final int target;
        public final boolean weekly;

        Quest(final String id, final QuestType type, final int target, final boolean weekly) {
            this.id = id;
            this.type = type;
            this.target = target;
            this.weekly = weekly;
        }
    }

    public static final class QuestMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
