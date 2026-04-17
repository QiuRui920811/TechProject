package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.util.ItemFactoryUtil;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 生活 + 科技技能系統 — 結合 EcoSkills 的 6 大技能與 8 項屬性。
 *
 * <p>技能體系：6 個技能線，每個技能最高 50 級，每級經驗值指數成長。
 * <ul>
 *   <li>combat（戰鬥）：擊殺怪物獲得 → 每級 +0.5 力量</li>
 *   <li>exploration（探索）：進入新區塊、完成事件 → 每級 +0.4 速度</li>
 *   <li>gathering（採集）：挖礦/砍樹/採集科技作物 → 每級 +0.6 幸運、+0.3 效率</li>
 *   <li>engineering（工程）：放置/升級/合成科技物品 → 每級 +0.5 效率</li>
 *   <li>research（研究）：消耗科技經驗 / 解鎖配方 → 每級 +0.4 爆擊率</li>
 *   <li>resonance（共鳴）：完成迷宮任務/隨機事件 → 每級 +0.5 魔力恢復、+0.3 爆擊傷害</li>
 * </ul>
 *
 * <p>屬性：8 項派生屬性（strength/defense/speed/crit_chance/crit_damage/efficiency/luck/mana_regen），
 * 由技能等級自動計算，套用時透過 potion effect / attribute modifier 影響玩家實際行為。
 *
 * <p>持久化：完全透過 PlayerProgressService 的 stats Map 存 key "skill_{id}_xp" / "skill_{id}_level"，
 * 不需要額外檔案或遷移。
 */
public final class SkillService {

    /** 最大技能等級 */
    public static final int MAX_LEVEL = 50;
    /** 每小時每技能 XP 上限 — 防止 AFK 自動農場刷經驗 */
    private static final long HOURLY_XP_CAP = 5000L;
    /** 玩家每小時 XP 累計：key = "uuid:skillId", value = {amount, hourTimestamp} */
    private final ConcurrentHashMap<String, long[]> hourlyXpTracker = new ConcurrentHashMap<>();
    /** 里程碑級別（送額外獎勵） */
    private static final int[] MILESTONES = {5, 10, 15, 20, 25, 30, 40, 50};

    // ── 主題色板（完全自訂 hex，刻意避開 Minecraft 原版 16 色） ──
    // Rose gradient（主色系）
    private static final TextColor C_ROSE_DEEP   = TextColor.color(0xBE185D);
    private static final TextColor C_ROSE_HOT    = TextColor.color(0xEC4899);
    private static final TextColor C_ROSE        = TextColor.color(0xF472B6);
    private static final TextColor C_ROSE_SOFT   = TextColor.color(0xF9A8D4);
    // Amber / 金色系（屬性數值）
    private static final TextColor C_AMBER_DEEP  = TextColor.color(0xB45309);
    private static final TextColor C_AMBER       = TextColor.color(0xF59E0B);
    private static final TextColor C_AMBER_LIGHT = TextColor.color(0xFCD34D);
    private static final TextColor C_CREAM_WARM  = TextColor.color(0xFEF3C7);
    // Emerald / 綠色系（完成/進度達成）
    private static final TextColor C_EMERALD_DEEP  = TextColor.color(0x047857);
    private static final TextColor C_EMERALD       = TextColor.color(0x10B981);
    private static final TextColor C_EMERALD_LIGHT = TextColor.color(0x6EE7B7);
    // Sky / 資訊色
    private static final TextColor C_SKY         = TextColor.color(0x0EA5E9);
    private static final TextColor C_SKY_LIGHT   = TextColor.color(0x7DD3FC);
    // Violet / 紫色系（稀有/特殊）
    private static final TextColor C_VIOLET      = TextColor.color(0x8B5CF6);
    private static final TextColor C_VIOLET_LIGHT = TextColor.color(0xC4B5FD);
    // Coral / 警示紅
    private static final TextColor C_CORAL       = TextColor.color(0xFB7185);
    private static final TextColor C_CORAL_DEEP  = TextColor.color(0xE11D48);
    // Neutrals（slate 系，刻意非 0xAAAAAA / 0x555555 Minecraft 灰）
    private static final TextColor C_PEARL       = TextColor.color(0xF3F4F6);
    private static final TextColor C_SLATE_LIGHT = TextColor.color(0xCBD5E1);
    private static final TextColor C_SLATE       = TextColor.color(0x94A3B8);
    private static final TextColor C_SLATE_DIM   = TextColor.color(0x64748B);
    private static final TextColor C_SLATE_DARK  = TextColor.color(0x475569);

    // 向後兼容 alias（舊程式用）
    private static final TextColor C_NAME      = C_ROSE_HOT;
    private static final TextColor C_NAME_DARK = C_ROSE_DEEP;
    private static final TextColor C_NUMERAL   = C_ROSE_SOFT;
    private static final TextColor C_DESC      = C_SLATE_DIM;
    private static final TextColor C_WHITE     = C_PEARL;
    private static final TextColor C_LGRAY     = C_SLATE_LIGHT;
    private static final TextColor C_DGRAY     = C_SLATE_DIM;
    private static final TextColor C_YELLOW    = C_AMBER_LIGHT;
    private static final TextColor C_GOLD      = C_AMBER;
    private static final TextColor C_GREEN     = C_EMERALD;
    private static final TextColor C_DGREEN    = C_EMERALD_DEEP;
    private static final TextColor C_RED       = C_CORAL;
    private static final TextColor C_DRED      = C_CORAL_DEEP;
    private static final TextColor C_BLUE      = C_SKY;
    private static final TextColor C_AQUA      = C_SKY_LIGHT;
    private static final TextColor C_TITLE     = C_ROSE_HOT;
    private static final TextColor C_SUBTITLE  = C_SLATE_LIGHT;
    private static final TextColor C_ACCENT    = C_EMERALD;
    private static final TextColor C_INFO      = C_AMBER_LIGHT;
    private static final TextColor C_CREAM     = C_CREAM_WARM;
    private static final TextColor C_MUTED     = C_SLATE;
    private static final TextColor C_DIM       = C_SLATE_DARK;
    private static final TextColor C_LOCKED    = C_CORAL_DEEP;

    // ── 文案裝飾元素 ───────────────────────────────────────
    /** Rose 漸層分隔線（共 18 個 ▬） */
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

    /** Amber 漸層分隔線（金色版） */
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

    /** RGB 插值漸層文字 */
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

    private final TechMCPlugin plugin;
    private final PlayerProgressService progressService;
    private final ItemFactoryUtil itemFactory;
    /** 後注入：天賦加成來源（可能為 null，表 onEnable 尚未建立）。 */
    private com.rui.techproject.service.talent.TalentService talentService;

    public SkillService(final TechMCPlugin plugin,
                        final PlayerProgressService progressService,
                        final ItemFactoryUtil itemFactory) {
        this.plugin = plugin;
        this.progressService = progressService;
        this.itemFactory = itemFactory;
    }

    /** 由 {@link TechMCPlugin#onEnable} 在建立 TalentService 之後注入。 */
    public void setTalentService(final com.rui.techproject.service.talent.TalentService talentService) {
        this.talentService = talentService;
    }

    // ═══════════════════════════════════════════
    //  核心 XP / 等級 API
    // ═══════════════════════════════════════════

    /**
     * 授予玩家某技能經驗值。若升級會發送通知與音效。
     */
    public void grantXp(final Player player, final String skillId, final long amount) {
        if (player == null || skillId == null || amount <= 0L) return;
        final Skill skill = Skill.byId(skillId);
        if (skill == null) return;
        final UUID uuid = player.getUniqueId();
        final int prevLevel = this.getSkillLevel(uuid, skill);
        if (prevLevel >= MAX_LEVEL) return;

        // 每小時 XP 上限防護
        final long currentHour = System.currentTimeMillis() / 3_600_000L;
        final String hourKey = uuid + ":" + skillId;
        final long[] tracker = this.hourlyXpTracker.compute(hourKey, (k, v) -> {
            if (v == null || v[1] != currentHour) return new long[]{0L, currentHour};
            return v;
        });
        if (tracker[0] >= HOURLY_XP_CAP) return; // 已達上限，靜默忽略
        final long effectiveAmount = Math.min(amount, HOURLY_XP_CAP - tracker[0]);
        tracker[0] += effectiveAmount;

        final long newXp = this.progressService.incrementStat(uuid, xpKey(skill), effectiveAmount);
        // 每日任務：技能經驗進度
        if (this.plugin.getDailyQuestService() != null) {
            this.plugin.getDailyQuestService().onSkillXp(player, effectiveAmount);
        }
        final int newLevel = this.computeLevelFromXp(newXp);
        if (newLevel > prevLevel) {
            this.progressService.setStat(uuid, levelKey(skill), newLevel);
            this.onLevelUp(player, skill, prevLevel, newLevel);
        }
        // 浮動文字經驗提示（≥3 XP 才顯示，避免畫面太雜）
        if (amount >= 3L) {
            com.rui.techproject.util.FloatingTextUtil.spawnAbove(
                    player.getEyeLocation(),
                    Component.text("+" + amount + " ", C_EMERALD)
                            .append(Component.text(skill.displayName, C_SKY_LIGHT))
                            .decoration(TextDecoration.ITALIC, false),
                    0.5f,
                    this.plugin.getSafeScheduler());
        }
        // action bar 經驗通知（低頻率）— 只在大額獎勵才顯示
        if (amount >= 20L) {
            final long levelXp = this.getXpIntoLevel(uuid, skill);
            final long requiredXp = this.xpForNextLevel(newLevel);
            player.sendActionBar(Component.text("+" + amount + " ", NamedTextColor.GREEN)
                    .append(Component.text(skill.displayName + " ", NamedTextColor.AQUA))
                    .append(Component.text("[" + levelXp + "/" + requiredXp + "]",
                            NamedTextColor.GRAY)));
        }
    }

    private void onLevelUp(final Player player, final Skill skill,
                            final int prevLevel, final int newLevel) {
        player.sendMessage(Component.text("★ ", NamedTextColor.GOLD)
                .append(Component.text("技能升級：", NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(skill.displayName + " ", NamedTextColor.AQUA))
                .append(Component.text("Lv." + prevLevel + " → Lv." + newLevel,
                        NamedTextColor.WHITE)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS, 0.7f, 1.3f);
        // 浮動文字：升級特效（較大字）
        com.rui.techproject.util.FloatingTextUtil.spawnAbove(
                player.getEyeLocation(),
                Component.text("★ ", C_AMBER)
                        .append(Component.text(skill.displayName + " ", C_SKY_LIGHT))
                        .append(Component.text("Lv." + newLevel, C_AMBER_LIGHT))
                        .decorate(TextDecoration.BOLD)
                        .decoration(TextDecoration.ITALIC, false),
                0.7f,
                this.plugin.getSafeScheduler());
        // 每級 +1 天賦點
        if (this.talentService != null) {
            this.talentService.grantPoint(player, skill, newLevel - prevLevel);
        }
        // 新手引導：技能升級
        if (this.plugin.getTutorialChainService() != null) {
            this.plugin.getTutorialChainService().onSkillLevelUp(player);
        }
        // 里程碑獎勵
        for (final int milestone : MILESTONES) {
            if (prevLevel < milestone && newLevel >= milestone) {
                this.grantMilestoneReward(player, skill, milestone);
            }
        }
    }

    private void grantMilestoneReward(final Player player, final Skill skill, final int milestone) {
        player.sendMessage(Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text("里程碑達成！", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                .append(Component.text(" " + skill.displayName + " Lv." + milestone,
                        NamedTextColor.WHITE)));
        // 發送 2 倍科技經驗
        this.progressService.addTechXp(player.getUniqueId(), milestone * 10L);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE,
                SoundCategory.PLAYERS, 0.8f, 1.5f);
    }

    /** XP 需求公式：基礎 100 * level^1.25（原 1.55 太陡，滿級需 84 萬降至 ~30 萬） */
    public long xpForNextLevel(final int currentLevel) {
        if (currentLevel >= MAX_LEVEL) return Long.MAX_VALUE;
        final double base = 100.0;
        final double growth = Math.pow(Math.max(1, currentLevel + 1), 1.25);
        return Math.max(100L, (long) (base * growth));
    }

    /** 從總 XP 累加計算等級。 */
    public int computeLevelFromXp(final long totalXp) {
        int level = 0;
        long remaining = totalXp;
        while (level < MAX_LEVEL && remaining >= this.xpForNextLevel(level)) {
            remaining -= this.xpForNextLevel(level);
            level++;
        }
        return level;
    }

    public int getSkillLevel(final UUID uuid, final Skill skill) {
        // 優先讀快取 level，若不存在則重新計算
        final long cached = this.progressService.getStat(uuid, levelKey(skill));
        if (cached > 0) return (int) cached;
        final long xp = this.progressService.getStat(uuid, xpKey(skill));
        if (xp == 0L) return 0;
        final int level = this.computeLevelFromXp(xp);
        if (level > 0) this.progressService.setStat(uuid, levelKey(skill), level);
        return level;
    }

    public int getSkillLevel(final UUID uuid, final String skillId) {
        final Skill skill = Skill.byId(skillId);
        return skill == null ? 0 : this.getSkillLevel(uuid, skill);
    }

    public long getSkillXp(final UUID uuid, final Skill skill) {
        return this.progressService.getStat(uuid, xpKey(skill));
    }

    public long getXpIntoLevel(final UUID uuid, final Skill skill) {
        long remaining = this.getSkillXp(uuid, skill);
        int level = 0;
        while (level < MAX_LEVEL && remaining >= this.xpForNextLevel(level)) {
            remaining -= this.xpForNextLevel(level);
            level++;
        }
        return remaining;
    }

    public int getTotalSkillLevel(final UUID uuid) {
        int total = 0;
        for (final Skill skill : Skill.values()) {
            total += this.getSkillLevel(uuid, skill);
        }
        return total;
    }

    // ═══════════════════════════════════════════
    //  屬性計算（由技能等級派生）
    // ═══════════════════════════════════════════

    public double getStatValue(final UUID uuid, final Stat stat) {
        double value = stat.baseValue;
        for (final Map.Entry<Skill, Double> contrib : stat.contributions.entrySet()) {
            value += this.getSkillLevel(uuid, contrib.getKey()) * contrib.getValue();
        }
        // 加上天賦樹節點投入的加成
        if (this.talentService != null) {
            value += this.talentService.sumBonus(uuid, stat);
        }
        return value;
    }

    public Map<Stat, Double> getAllStats(final UUID uuid) {
        final Map<Stat, Double> result = new LinkedHashMap<>();
        for (final Stat stat : Stat.values()) {
            result.put(stat, this.getStatValue(uuid, stat));
        }
        return result;
    }

    // ═══════════════════════════════════════════
    //  GUI
    // ═══════════════════════════════════════════

    // EcoSkills 主選單 mask (2=藍邊框, 1=黑填充, 0=開放槽)
    // row 0 slot 4 = 玩家頭像
    // row 2 slots 20-25 = 6 技能
    // row 4 slot 40 = 屬性總覽
    // row 5 slot 49 = 關閉
    private static final String[] SKILLS_MASK = {
            "211101112",
            "211111112",
            "210000012",
            "211111112",
            "211101112",
            "211101112",
    };

    public void openSkillMenu(final Player player) {
        final Inventory inv = Bukkit.createInventory(new SkillMenuHolder(), 54,
                gradientText("✦ 技能總覽 ✦", 0xEC4899, 0xF9A8D4, true));
        final UUID uuid = player.getUniqueId();

        applyMask(inv, SKILLS_MASK);

        // slot 4：玩家頭像總覽
        inv.setItem(4, this.buildPlayerHeader(player));

        // 技能圖標（slots 20, 21, 22, 23, 24, 25）
        final int[] skillSlots = {20, 21, 22, 23, 24, 25};
        final Skill[] skills = Skill.values();
        for (int i = 0; i < skills.length && i < skillSlots.length; i++) {
            inv.setItem(skillSlots[i], this.buildSkillIcon(uuid, skills[i]));
        }

        // 排行榜（slot 38）
        {
            final ItemStack lb = new ItemStack(Material.GOLD_INGOT);
            final ItemMeta lbm = lb.getItemMeta();
            if (lbm != null) {
                lbm.displayName(gradientText("✦ 科技排行榜", 0xFBBF24, 0xF59E0B, true));
                final List<Component> lbLore = new ArrayList<>();
                lbLore.add(decorLine());
                lbLore.add(plain("查看等級、經驗、擊殺等排名", C_SLATE_DIM, false));
                lbLore.add(decorLine());
                lbLore.add(plain("▸ 點擊開啟排行榜", C_VIOLET_LIGHT, false));
                lbm.lore(lbLore);
                lbm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                lb.setItemMeta(lbm);
            }
            inv.setItem(38, lb);
        }

        // 屬性總覽（slot 40）
        inv.setItem(40, this.buildStatsOverview(uuid));

        // 天賦樹（slot 42）
        {
            final ItemStack tt = new ItemStack(Material.ENCHANTING_TABLE);
            final ItemMeta ttm = tt.getItemMeta();
            if (ttm != null) {
                ttm.displayName(gradientText("✦ 天賦樹", 0xA78BFA, 0x7C3AED, true));
                final List<Component> ttLore = new ArrayList<>();
                ttLore.add(decorLine());
                ttLore.add(plain("投入天賦點解鎖被動與技能", C_SLATE_DIM, false));
                ttLore.add(decorLine());
                ttLore.add(plain("▸ 點擊開啟天賦樹", C_VIOLET_LIGHT, false));
                ttm.lore(ttLore);
                ttm.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                tt.setItemMeta(ttm);
            }
            inv.setItem(42, tt);
        }

        // 關閉（slot 49）
        inv.setItem(49, buttonClose());

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK,
                SoundCategory.PLAYERS, 0.6f, 1.2f);
    }

    private ItemStack buildPlayerHeader(final Player player) {
        final UUID uuid = player.getUniqueId();
        final int totalLv = this.getTotalSkillLevel(uuid);
        final int maxPossible = Skill.values().length * MAX_LEVEL;
        final double totalPct = maxPossible > 0 ? totalLv * 100.0 / maxPossible : 0;

        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = head.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.SkullMeta skull) {
            skull.setOwningPlayer(player);
            skull.displayName(gradientText(player.getName() + " 的技能總覽",
                    0xEC4899, 0xF9A8D4, true));
            final List<Component> lore = new ArrayList<>();
            lore.add(decorLine());
            lore.add(plain("你的冒險編年史", C_SLATE_DIM, false)
                    .decoration(TextDecoration.ITALIC, true));
            lore.add(Component.empty());
            lore.add(plain("» ", C_SLATE_DARK, false)
                    .append(plain("總等級 ", C_PEARL, false))
                    .append(plain(String.valueOf(totalLv), C_AMBER_LIGHT, false))
                    .append(plain(" / ", C_SLATE_DARK, false))
                    .append(plain(String.valueOf(maxPossible), C_SLATE_LIGHT, false)));
            lore.add(plain("» ", C_SLATE_DARK, false)
                    .append(plain("精通度 ", C_PEARL, false))
                    .append(plain(String.format(Locale.ROOT, "%.1f%%", totalPct),
                            C_AMBER_LIGHT, false)));
            lore.add(decorLine());
            lore.add(plain("點擊下方技能圖標查看進程", C_VIOLET_LIGHT, false));
            skull.lore(lore);
            head.setItemMeta(skull);
        }
        return head;
    }

    /** EcoSkills 風格技能主選單圖標。 */
    private ItemStack buildSkillIcon(final UUID uuid, final Skill skill) {
        final int level = this.getSkillLevel(uuid, skill);
        final long xpIntoLevel = this.getXpIntoLevel(uuid, skill);
        final long required = this.xpForNextLevel(level);
        final boolean maxed = level >= MAX_LEVEL;
        final double pct = (!maxed && required > 0) ? Math.min(1.0, xpIntoLevel / (double) required) : 1.0;

        final ItemStack icon = new ItemStack(skill.iconMaterial);
        final ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.displayName(gradientText(skill.displayName + "  " + toRoman(level),
                    0xEC4899, 0xF9A8D4, true));
            final List<Component> lore = new ArrayList<>();
            lore.add(decorLine());
            lore.add(plain(skill.description, C_SLATE_DIM, false)
                    .decoration(TextDecoration.ITALIC, true));
            lore.add(Component.empty());
            lore.add(plain("◆ ", C_AMBER, false)
                    .append(plain("每級提供", C_CREAM_WARM, false)));
            for (final Stat stat : Stat.values()) {
                final Double contrib = stat.contributions.get(skill);
                if (contrib == null || contrib <= 0) continue;
                lore.add(plain("  » ", C_SLATE_DARK, false)
                        .append(plain("+" + formatContribValue(contrib, stat.isPercentage)
                                + " ", C_AMBER_LIGHT, false))
                        .append(plain(stat.displayName, C_SLATE_LIGHT, false)));
            }
            lore.add(decorLineAmber());
            if (maxed) {
                lore.add(plain("◆ ", C_EMERALD, false)
                        .append(plain("已達最高等級 Lv." + MAX_LEVEL,
                                C_EMERALD_LIGHT, true)));
            } else {
                lore.add(plain("◆ ", C_VIOLET, false)
                        .append(plain("升至下一級", C_CREAM_WARM, false)));
                lore.add(plain("  » ", C_SLATE_DARK, false)
                        .append(plain(String.format(Locale.ROOT, "%.1f%%", pct * 100.0),
                                C_AMBER_LIGHT, false)));
                lore.add(plain("  » ", C_SLATE_DARK, false)
                        .append(plain(xpIntoLevel + "", C_AMBER_LIGHT, false))
                        .append(plain(" / ", C_SLATE_DARK, false))
                        .append(plain(required + "", C_SLATE_LIGHT, false))
                        .append(plain(" XP", C_PEARL, false)));
            }
            lore.add(decorLine());
            lore.add(plain("▸ 點擊查看等級進程", C_VIOLET_LIGHT, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            icon.setItemMeta(meta);
        }
        return icon;
    }

    // EcoSkills snake 等級路徑：字元 → 槽位
    // 對應 pattern:
    //  "109ab0jkl"
    //  "2080c0i0m"
    //  "3070d0h0n"
    //  "4560efg0o"
    //  "00000000p"
    //  "00000000q"
    private static final int[] SNAKE_LEVEL_SLOTS = {
            0,   // 1
            9,   // 2
            18,  // 3
            27,  // 4
            28,  // 5
            29,  // 6
            20,  // 7
            11,  // 8
            2,   // 9
            3,   // a (10)
            4,   // b (11)
            13,  // c (12)
            22,  // d (13)
            31,  // e (14)
            32,  // f (15)
            33,  // g (16)
            24,  // h (17)
            15,  // i (18)
            6,   // j (19)
            7,   // k (20)
            8,   // l (21)
            17,  // m (22)
            26,  // n (23)
            35,  // o (24)
    };
    private static final int DETAIL_BACK_SLOT = 44;
    private static final int DETAIL_CLOSE_SLOT = 53;

    /** 技能等級進程頁（EcoSkills 風格蛇形路徑）。 */
    public void openSkillDetail(final Player player, final Skill skill) {
        this.openSkillDetail(player, skill, -1);
    }

    public void openSkillDetail(final Player player, final Skill skill, final int requestedPage) {
        if (skill == null) return;
        final UUID uuid = player.getUniqueId();
        final int level = this.getSkillLevel(uuid, skill);
        final int pageSize = SNAKE_LEVEL_SLOTS.length;  // 24
        final int totalPages = (MAX_LEVEL + pageSize - 1) / pageSize;  // ceil(50/24) = 3
        final int page = requestedPage < 0
                ? Math.min(Math.max(0, (Math.max(1, level) - 1) / pageSize), totalPages - 1)
                : Math.max(0, Math.min(requestedPage, totalPages - 1));
        final int startLv = page * pageSize + 1;  // 1-indexed

        final Inventory inv = Bukkit.createInventory(new SkillDetailHolder(skill, page), 54,
                gradientText("✦ " + skill.displayName + " 等級進程 ✦",
                        0xEC4899, 0xF9A8D4, true));

        // 先全部填黑玻璃
        final ItemStack blackPane = fillerItem(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) inv.setItem(i, blackPane);

        // 鋪每一個等級節點
        for (int i = 0; i < pageSize; i++) {
            final int lv = startLv + i;
            if (lv > MAX_LEVEL) break;
            inv.setItem(SNAKE_LEVEL_SLOTS[i], this.buildLevelNode(uuid, skill, lv, level));
        }

        // 返回 / 分頁按鈕
        inv.setItem(DETAIL_BACK_SLOT, this.buildPaginationButton(skill, page, totalPages));
        inv.setItem(DETAIL_CLOSE_SLOT, buttonClose());

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN,
                SoundCategory.PLAYERS, 0.7f, 1.2f);
    }

    /** 建立單一等級節點圖標：lime/yellow/red 依狀態。 */
    private ItemStack buildLevelNode(final UUID uuid, final Skill skill,
                                      final int lv, final int currentLevel) {
        final boolean unlocked = lv <= currentLevel;
        final boolean inProgress = lv == currentLevel + 1;
        final Material material;
        final TextColor stateColor;
        final String stateLabel;
        if (unlocked) {
            material = Material.LIME_STAINED_GLASS_PANE;
            stateColor = C_GREEN;
            stateLabel = "已解鎖";
        } else if (inProgress) {
            material = Material.YELLOW_STAINED_GLASS_PANE;
            stateColor = C_YELLOW;
            stateLabel = "進行中";
        } else {
            material = Material.RED_STAINED_GLASS_PANE;
            stateColor = C_RED;
            stateLabel = "尚未解鎖";
        }

        final ItemStack stack = new ItemStack(material);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            // 依狀態選漸層配色
            final int gradStart, gradEnd;
            if (unlocked)       { gradStart = 0x10B981; gradEnd = 0x6EE7B7; }
            else if (inProgress){ gradStart = 0xF59E0B; gradEnd = 0xFCD34D; }
            else                { gradStart = 0xE11D48; gradEnd = 0xFB7185; }
            meta.displayName(gradientText(skill.displayName + "  " + toRoman(lv),
                    gradStart, gradEnd, true));

            final List<Component> lore = new ArrayList<>();
            lore.add(decorLine());
            lore.add(plain("◆ ", stateColor, false)
                    .append(plain(stateLabel, stateColor, true)));
            lore.add(Component.empty());

            // 屬性解鎖
            lore.add(plain("◆ ", C_AMBER, false)
                    .append(plain("屬性", C_CREAM_WARM, false)));
            boolean anyStat = false;
            for (final Stat stat : Stat.values()) {
                final Double contrib = stat.contributions.get(skill);
                if (contrib == null || contrib <= 0) continue;
                anyStat = true;
                final TextColor statCol = unlocked ? C_AMBER_LIGHT
                        : (inProgress ? C_AMBER : C_SLATE);
                lore.add(plain("  » ", C_SLATE_DARK, false)
                        .append(plain("+" + formatContribValue(contrib * lv, stat.isPercentage)
                                + " ", statCol, false))
                        .append(plain(stat.displayName,
                                unlocked ? C_CREAM_WARM : C_SLATE_LIGHT, false)));
            }
            if (!anyStat) {
                lore.add(plain("  » ", C_SLATE_DARK, false)
                        .append(plain("（此技能無直接屬性）", C_SLATE_DIM, false)));
            }

            // 里程碑
            boolean isMilestone = false;
            for (final int m : MILESTONES) if (m == lv) { isMilestone = true; break; }
            if (isMilestone) {
                lore.add(decorLineAmber());
                lore.add(plain("◆ ", C_VIOLET, false)
                        .append(plain("里程碑獎勵", C_VIOLET_LIGHT, true)));
                lore.add(plain("  » ", C_SLATE_DARK, false)
                        .append(plain("+" + (lv * 10L) + " 科技經驗",
                                unlocked ? C_SKY_LIGHT : C_SLATE, false)));
            }

            // 進度資訊（僅 in-progress 顯示）
            if (inProgress) {
                final long xpIntoLevel = this.getXpIntoLevel(uuid, skill);
                final long required = this.xpForNextLevel(currentLevel);
                final double pct = required > 0
                        ? Math.min(1.0, xpIntoLevel / (double) required) : 0;
                lore.add(decorLineAmber());
                lore.add(plain("◆ ", C_AMBER, false)
                        .append(plain("進度", C_CREAM_WARM, false)));
                lore.add(plain("  » ", C_SLATE_DARK, false)
                        .append(plain(String.format(Locale.ROOT, "%.1f%%", pct * 100.0),
                                C_AMBER_LIGHT, false)));
                lore.add(plain("  » ", C_SLATE_DARK, false)
                        .append(plain(xpIntoLevel + "", C_AMBER_LIGHT, false))
                        .append(plain(" / ", C_SLATE_DARK, false))
                        .append(plain(required + "", C_SLATE_LIGHT, false))
                        .append(plain(" XP", C_PEARL, false)));
            }

            // 所需總經驗（僅 locked 顯示）
            if (!unlocked && !inProgress) {
                long totalReq = 0;
                for (int l = 0; l < lv - 1; l++) totalReq += this.xpForNextLevel(l);
                lore.add(decorLineAmber());
                lore.add(plain("◆ ", C_CORAL, false)
                        .append(plain("達成條件", C_CREAM_WARM, false)));
                lore.add(plain("  » ", C_SLATE_DARK, false)
                        .append(plain("累積 ", C_SLATE_LIGHT, false))
                        .append(plain(totalReq + "", C_CORAL, false))
                        .append(plain(" XP", C_SLATE_LIGHT, false)));
            }

            lore.add(decorLine());
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack buildPaginationButton(final Skill skill, final int page, final int totalPages) {
        final int pageSize = SNAKE_LEVEL_SLOTS.length;
        final int startLv = page * pageSize + 1;
        final int endLv = Math.min(MAX_LEVEL, startLv + pageSize - 1);

        final ItemStack stack = new ItemStack(Material.ENDER_PEARL);
        final ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(gradientText("第 " + (page + 1) + " / " + totalPages + " 頁",
                    0xEC4899, 0xF9A8D4, true));
            final List<Component> lore = new ArrayList<>();
            lore.add(decorLine());
            lore.add(plain("顯示等級 ", C_SLATE_LIGHT, false)
                    .append(plain(startLv + "", C_AMBER_LIGHT, false))
                    .append(plain(" - ", C_SLATE_DARK, false))
                    .append(plain(endLv + "", C_AMBER_LIGHT, false)));
            lore.add(Component.empty());
            if (totalPages > 1) {
                lore.add(plain("» ", C_SLATE_DARK, false)
                        .append(plain("左鍵：下一頁", C_EMERALD_LIGHT, false)));
                lore.add(plain("» ", C_SLATE_DARK, false)
                        .append(plain("右鍵：返回技能列表", C_SKY_LIGHT, false)));
            } else {
                lore.add(plain("» ", C_SLATE_DARK, false)
                        .append(plain("點擊返回技能列表", C_SKY_LIGHT, false)));
            }
            lore.add(decorLine());
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack buildStatsOverview(final UUID uuid) {
        final ItemStack statsIcon = new ItemStack(Material.NETHER_STAR);
        final ItemMeta statsMeta = statsIcon.getItemMeta();
        if (statsMeta != null) {
            statsMeta.displayName(gradientText("屬性總覽", 0xEC4899, 0xF9A8D4, true));
            final List<Component> lore = new ArrayList<>();
            lore.add(decorLine());
            lore.add(plain("由全部技能等級綜合計算", C_SLATE_DIM, false)
                    .decoration(TextDecoration.ITALIC, true));
            lore.add(Component.empty());
            for (final Stat stat : Stat.values()) {
                final double value = this.getStatValue(uuid, stat);
                lore.add(plain("» ", C_SLATE_DARK, false)
                        .append(plain(stat.displayName + " ", C_CREAM_WARM, false))
                        .append(plain(formatStat(value, stat), C_AMBER_LIGHT, false)));
            }
            lore.add(decorLine());
            lore.add(plain("▸ 持續提升技能即可強化屬性", C_VIOLET_LIGHT, false));
            statsMeta.lore(lore);
            statsMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            statsIcon.setItemMeta(statsMeta);
        }
        return statsIcon;
    }

    /** InventoryClickEvent 分派器：由 TechListener 呼叫。 */
    public boolean handleClick(final Player player, final int slot, final Object holder) {
        return this.handleClick(player, slot, holder, false);
    }

    public boolean handleClick(final Player player, final int slot, final Object holder,
                                final boolean rightClick) {
        if (holder instanceof SkillMenuHolder) {
            final int[] skillSlots = {20, 21, 22, 23, 24, 25};
            final Skill[] skills = Skill.values();
            for (int i = 0; i < skillSlots.length && i < skills.length; i++) {
                if (slot == skillSlots[i]) {
                    this.openSkillDetail(player, skills[i]);
                    return true;
                }
            }
            if (slot == 38) {
                // 排行榜
                if (this.plugin.getLeaderboardService() != null) {
                    this.plugin.getLeaderboardService().openLeaderboardMenu(player);
                }
                return true;
            }
            if (slot == 42) {
                // 天賦樹
                if (this.plugin.getTalentGuiService() != null) {
                    this.plugin.getTalentGuiService().openTree(player, Skill.COMBAT);
                }
                return true;
            }
            if (slot == 49) {
                player.closeInventory();
                return true;
            }
            return true;
        }
        if (holder instanceof SkillDetailHolder detailHolder) {
            if (slot == DETAIL_BACK_SLOT) {
                final int pageSize = SNAKE_LEVEL_SLOTS.length;
                final int totalPages = (MAX_LEVEL + pageSize - 1) / pageSize;
                if (totalPages > 1 && !rightClick) {
                    // 左鍵翻頁
                    final int next = (detailHolder.page + 1) % totalPages;
                    this.openSkillDetail(player, detailHolder.skill, next);
                } else {
                    // 右鍵或只有一頁：返回主選單
                    this.openSkillMenu(player);
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK,
                            SoundCategory.PLAYERS, 0.5f, 1.0f);
                }
                return true;
            }
            if (slot == DETAIL_CLOSE_SLOT) {
                player.closeInventory();
                return true;
            }
            return true;
        }
        return false;
    }

    // ── GUI 小工具 ─────────────────────────────────────
    private static Component plain(final String s, final TextColor color, final boolean bold) {
        Component c = color != null ? Component.text(s, color) : Component.text(s);
        c = c.decoration(TextDecoration.ITALIC, false);
        if (bold) c = c.decoration(TextDecoration.BOLD, true);
        return c;
    }

    private static String formatContribValue(final double value, final boolean percentage) {
        if (percentage) return String.format(Locale.ROOT, "%.1f%%", value);
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** 將 mask 字串陣列套用至 inventory：2=藍邊框，1=黑填充，0=保留。 */
    private static void applyMask(final Inventory inv, final String[] mask) {
        final ItemStack blackPane = fillerItem(Material.BLACK_STAINED_GLASS_PANE);
        final ItemStack bluePane = fillerItem(Material.BLUE_STAINED_GLASS_PANE);
        for (int row = 0; row < mask.length && row < 6; row++) {
            final String line = mask[row];
            for (int col = 0; col < 9 && col < line.length(); col++) {
                final char ch = line.charAt(col);
                final int slot = row * 9 + col;
                switch (ch) {
                    case '2' -> inv.setItem(slot, bluePane);
                    case '1' -> inv.setItem(slot, blackPane);
                    default -> { /* 0 或其他：保留空槽 */ }
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
                            .append(plain("關閉此界面", C_SLATE_LIGHT, false)),
                    decorLine()));
            close.setItemMeta(meta);
        }
        return close;
    }

    /** 阿拉伯數字轉羅馬數字（1-50）。 */
    private static String toRoman(final int n) {
        if (n <= 0) return "0";
        final int[] values =  {50, 40, 10, 9, 5, 4, 1};
        final String[] roman = {"L","XL","X","IX","V","IV","I"};
        final StringBuilder sb = new StringBuilder();
        int v = n;
        for (int i = 0; i < values.length; i++) {
            while (v >= values[i]) { sb.append(roman[i]); v -= values[i]; }
        }
        return sb.toString();
    }

    private static String formatStat(final double value, final Stat stat) {
        if (stat.isPercentage) {
            return String.format(Locale.ROOT, "%.1f%%", value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    // ═══════════════════════════════════════════
    //  套用屬性到玩家（潛行/打擊時呼叫）
    // ═══════════════════════════════════════════

    /**
     * 計算擊殺傷害加成（由力量 + 爆擊計算）。<br>
     * 呼叫點：TechListener.onEntityDamageByEntity（玩家攻擊）
     */
    public double computeDamageBonus(final UUID uuid, final double baseDamage) {
        final double strength = this.getStatValue(uuid, Stat.STRENGTH);
        final double critChance = this.getStatValue(uuid, Stat.CRIT_CHANCE);
        final double critDamage = this.getStatValue(uuid, Stat.CRIT_DAMAGE);
        double result = baseDamage * (1.0 + strength / 100.0);
        if (Math.random() * 100.0 < critChance) {
            result *= (1.0 + critDamage / 100.0);
        }
        return result;
    }

    /**
     * 計算減傷（防禦）。
     */
    public double computeDamageReduction(final UUID uuid, final double incomingDamage) {
        final double defense = this.getStatValue(uuid, Stat.DEFENSE);
        // 簡化：每點防禦減 0.5% 傷害，上限 50%
        final double reduction = Math.min(0.5, defense / 200.0);
        return incomingDamage * (1.0 - reduction);
    }

    // ═══════════════════════════════════════════
    //  內部 helper
    // ═══════════════════════════════════════════

    private static String xpKey(final Skill skill) {
        return "skill_" + skill.id + "_xp";
    }

    private static String levelKey(final Skill skill) {
        return "skill_" + skill.id + "_level";
    }

    // ═══════════════════════════════════════════
    //  資料結構：Skill + Stat enum
    // ═══════════════════════════════════════════

    public enum Skill {
        COMBAT("combat", "戰鬥", "擊殺怪物提升等級", Material.DIAMOND_SWORD),
        EXPLORATION("exploration", "探索", "遊歷世界與完成事件提升等級", Material.COMPASS),
        GATHERING("gathering", "採集", "挖礦、砍樹、採集作物提升等級", Material.IRON_PICKAXE),
        ENGINEERING("engineering", "工程", "放置/升級/合成科技物提升等級", Material.REDSTONE),
        RESEARCH("research", "研究", "解鎖配方與消耗科技經驗提升等級", Material.ENCHANTED_BOOK),
        RESONANCE("resonance", "共鳴", "完成迷宮任務與隨機事件提升等級", Material.AMETHYST_SHARD);

        public final String id;
        public final String displayName;
        public final String description;
        public final Material iconMaterial;

        Skill(final String id, final String displayName, final String description,
              final Material iconMaterial) {
            this.id = id;
            this.displayName = displayName;
            this.description = description;
            this.iconMaterial = iconMaterial;
        }

        public static Skill byId(final String id) {
            if (id == null) return null;
            for (final Skill s : values()) {
                if (s.id.equalsIgnoreCase(id)) return s;
            }
            return null;
        }
    }

    public enum Stat {
        MAX_HEALTH("最大生命", 20.0, false, Map.of(Skill.ENGINEERING, 0.5, Skill.GATHERING, 0.3)),
        MAX_MANA("最大魔力", 100.0, false, Map.of(Skill.RESEARCH, 0.6, Skill.RESONANCE, 0.4)),
        // base 4.0：低等玩家約 4-6/sec，配合擊殺/挖礦回魔 + 天賦加成足以撐起頻繁施法
        MANA_REGEN("魔力回復", 4.0, false, Map.of(Skill.RESONANCE, 0.5, Skill.RESEARCH, 0.2)),
        HEALTH_REGEN("生命回復", 0.5, false, Map.of(Skill.GATHERING, 0.3, Skill.ENGINEERING, 0.2)),
        STRENGTH("力量", 1.0, false, Map.of(Skill.COMBAT, 0.5)),
        DEFENSE("防禦", 0.0, false, Map.of(Skill.ENGINEERING, 0.4, Skill.COMBAT, 0.2)),
        SPEED("速度", 0.0, false, Map.of(Skill.EXPLORATION, 0.5)),
        CRIT_CHANCE("爆擊率", 5.0, true, Map.of(Skill.EXPLORATION, 0.3, Skill.RESEARCH, 0.2)),
        CRIT_DAMAGE("爆擊傷害", 50.0, true, Map.of(Skill.COMBAT, 0.3, Skill.RESONANCE, 0.2)),
        EFFICIENCY("效率", 0.0, false, Map.of(Skill.ENGINEERING, 0.4, Skill.GATHERING, 0.3)),
        LUCK("幸運", 0.0, false, Map.of(Skill.GATHERING, 0.6)),
        MAGIC_POWER("魔法強度", 0.0, false, Map.of(Skill.RESEARCH, 0.5, Skill.RESONANCE, 0.2)),
        HEALING_POWER("治療強度", 0.0, false, Map.of(Skill.RESONANCE, 0.5, Skill.GATHERING, 0.2)),
        // 冷卻縮減：基礎 0%，上限 40%（由 SpellCastService 在施法時套用）
        COOLDOWN_REDUCTION("冷卻縮減", 0.0, true, Map.of(Skill.RESEARCH, 0.2, Skill.RESONANCE, 0.15));

        public final String displayName;
        public final double baseValue;
        public final boolean isPercentage;
        public final Map<Skill, Double> contributions;

        Stat(final String displayName, final double baseValue, final boolean isPercentage,
             final Map<Skill, Double> contributions) {
            this.displayName = displayName;
            this.baseValue = baseValue;
            this.isPercentage = isPercentage;
            this.contributions = contributions;
        }

        public static Stat byName(final String name) {
            if (name == null) return null;
            try {
                return Stat.valueOf(name.toUpperCase(java.util.Locale.ROOT));
            } catch (final IllegalArgumentException ex) {
                return null;
            }
        }
    }

    /** 空 InventoryHolder，用於區分技能選單與其他選單。 */
    public static final class SkillMenuHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    /** 技能詳情頁 Holder（帶技能資料與分頁）。 */
    public static final class SkillDetailHolder implements InventoryHolder {
        public final Skill skill;
        public final int page;

        public SkillDetailHolder(final Skill skill, final int page) {
            this.skill = skill;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
