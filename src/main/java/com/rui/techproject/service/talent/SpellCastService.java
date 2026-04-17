package com.rui.techproject.service.talent;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.service.SkillService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wynncraft 風格的按鍵組合（LRL / RLR / LLR / RRL）施法偵測器。
 *
 * <p>運作方式：玩家手持「施法武器」時，每次右鍵點擊（依是否下蹲區分）記錄下來，累積 3 次後組成
 * {@link Combo}。根據手持武器的類型自動對應到一棵 {@link SkillService.Skill} 技能樹，
 * 再查詢 {@link SpellId#find} 取得對應法術，執行前檢查魔力 + 冷卻。
 *
 * <p>觸發規則：
 * <ul>
 *   <li>1.5 秒內完成 3 次點擊才會視為 combo</li>
 *   <li>玩家必須已透過天賦樹解鎖該 Keystone 節點（由 {@link TalentService#hasLearnedSpell} 判定）</li>
 *   <li>魔力足夠才能施放（透過 {@link ManaService#consume}）</li>
 *   <li>施放後會進入該法術的冷卻（{@link SpellId#cooldownTicks}）</li>
 * </ul>
 *
 * <p>武器到技能的對應：
 * <ul>
 *   <li>劍 / 斧 → COMBAT</li>
 *   <li>弓 / 十字弓 → EXPLORATION</li>
 *   <li>鎬 / 鋤 → GATHERING</li>
 *   <li>WRENCH (預設為紅石火把) → ENGINEERING</li>
 *   <li>STAFF (預設為終界棒) → RESEARCH</li>
 *   <li>CRYSTAL (預設為紫水晶碎片) → RESONANCE</li>
 * </ul>
 */
public final class SpellCastService {

    /** 完成一次 combo 的最長時間（毫秒）。 */
    private static final long COMBO_WINDOW_MS = 1500L;

    /** PDC key：與 ItemFactoryUtil 的 tech_item_id 相同。 */
    @SuppressWarnings("deprecation")
    private static final NamespacedKey TECH_ITEM_KEY =
            new NamespacedKey("techproject", "tech_item_id");

    private final TechMCPlugin plugin;
    private final SkillService skillService;
    private final ManaService manaService;
    private final TalentService talentService;
    private final SpellExecutor spellExecutor;

    /** 每位玩家的進行中 combo 緩衝。 */
    private final Map<UUID, ComboBuffer> comboBuffers = new HashMap<>();
    /** 每位玩家每個法術的冷卻到期時間（server tick 時間戳，毫秒）。 */
    private final Map<UUID, Map<SpellId, Long>> cooldowns = new HashMap<>();

    public SpellCastService(final TechMCPlugin plugin,
                             final SkillService skillService,
                             final ManaService manaService,
                             final TalentService talentService,
                             final SpellExecutor spellExecutor) {
        this.plugin = plugin;
        this.skillService = skillService;
        this.manaService = manaService;
        this.talentService = talentService;
        this.spellExecutor = spellExecutor;
    }

    // ═══════════════════════════════════════════
    //  武器 → 技能 對應（僅限科技施法武器）
    // ═══════════════════════════════════════════

    /** 科技施法武器的 tech_item_id → 技能樹 對應表。 */
    private static final Map<String, SkillService.Skill> SPELL_WEAPON_MAP = Map.of(
            "spell_blade",   SkillService.Skill.COMBAT,
            "spell_bow",     SkillService.Skill.EXPLORATION,
            "spell_pick",    SkillService.Skill.GATHERING,
            "spell_wrench",  SkillService.Skill.ENGINEERING,
            "spell_staff",   SkillService.Skill.RESEARCH,
            "spell_crystal", SkillService.Skill.RESONANCE
    );

    /** 所有施法武器的 tech_item_id 集合（快速判斷用）。 */
    private static final Set<String> SPELL_WEAPON_IDS = SPELL_WEAPON_MAP.keySet();

    /**
     * 判斷手持物品是否為施法武器（ManaService 的 actionbar 顯示會用到）。
     * 僅限科技施法武器，原版武器不會觸發。
     */
    public static boolean isSpellWeapon(final ItemStack item) {
        return skillFromWeapon(item) != null;
    }

    /**
     * 取得手持物品對應的技能樹。
     * 僅接受帶有 tech_item_id PDC 標籤的科技施法武器。
     */
    public static SkillService.Skill skillFromWeapon(final ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        final String techId = meta.getPersistentDataContainer()
                .get(TECH_ITEM_KEY, PersistentDataType.STRING);
        if (techId == null) return null;
        return SPELL_WEAPON_MAP.get(techId);
    }

    // ═══════════════════════════════════════════
    //  Combo 輸入 API（由 SpellCastListener 呼叫）
    // ═══════════════════════════════════════════

    /**
     * 記錄一次點擊。當緩衝累積 3 次點擊時嘗試解析 combo 並施放對應法術。
     *
     * @param player   施法者
     * @param sneaking true = 下蹲右鍵（內部 R），false = 一般右鍵（內部 L）
     * @return true 若本次點擊完成一次 combo（不論是否成功施放）
     */
    public boolean recordClick(final Player player, final boolean sneaking, final boolean isLeftClick) {
        if (player == null) return false;
        final ItemStack main = player.getInventory().getItemInMainHand();
        final SkillService.Skill skill = skillFromWeapon(main);
        if (skill == null) return false;

        final UUID uuid = player.getUniqueId();
        final long now = System.currentTimeMillis();
        ComboBuffer buffer = this.comboBuffers.get(uuid);
        // 重置逾時的 buffer
        if (buffer == null || now - buffer.startMs > COMBO_WINDOW_MS || buffer.skill != skill) {
            // 第一下是左鍵 → 不開始 combo（左鍵攻擊不應誤觸發）
            if (isLeftClick) return false;
            buffer = new ComboBuffer(skill, now);
            this.comboBuffers.put(uuid, buffer);
        }

        buffer.push(sneaking, isLeftClick);

        // 施法回饋音：每次點擊一點小聲響
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK,
                SoundCategory.PLAYERS, 0.3f, sneaking ? 1.8f : 1.4f);

        if (buffer.size() < 3) {
            // 尚未湊滿 3 連點，顯示 combo 進度
            this.showComboProgress(player, buffer);
            return false;
        }

        // 第 3 下也顯示完整 combo 進度
        this.showComboProgress(player, buffer);

        // 嘗試解析 combo
        final Combo combo = Combo.of(buffer.c1, buffer.c2, buffer.c3);
        this.comboBuffers.remove(uuid);
        if (combo == null) {
            player.sendActionBar(Component.text("✗ 無效連擊序列", TextColor.color(0xE11D48))
                    .decoration(TextDecoration.ITALIC, false));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS,
                    SoundCategory.PLAYERS, 0.6f, 0.6f);
            return true;
        }

        // 查詢對應的法術
        final SpellId spell = SpellId.find(skill, combo);
        if (spell == null) {
            player.sendActionBar(Component.text("✗ " + skill.displayName + " 沒有 " + combo.display + " 連擊技",
                            TextColor.color(0xE11D48))
                    .decoration(TextDecoration.ITALIC, false));
            return true;
        }

        // 檢查是否透過天賦樹解鎖
        if (this.talentService != null && !this.talentService.hasLearnedSpell(uuid, spell)) {
            player.sendActionBar(Component.text("✗ 尚未解鎖 ", TextColor.color(0xE11D48))
                    .append(Component.text(spell.displayName, TextColor.color(0xFB7185)))
                    .append(Component.text("（前往天賦樹投入關鍵節點）", TextColor.color(0xCBD5E1)))
                    .decoration(TextDecoration.ITALIC, false));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS,
                    SoundCategory.PLAYERS, 0.6f, 0.6f);
            return true;
        }

        // 檢查冷卻
        final long cdExpire = this.getCooldownExpire(uuid, spell);
        if (now < cdExpire) {
            final long remain = (cdExpire - now + 999L) / 1000L;
            player.sendActionBar(Component.text("✗ ", TextColor.color(0xE11D48))
                    .append(Component.text(spell.displayName + " 冷卻中 ", TextColor.color(0xFB7185)))
                    .append(Component.text(remain + " 秒", TextColor.color(0xFCD34D)))
                    .decoration(TextDecoration.ITALIC, false));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS,
                    SoundCategory.PLAYERS, 0.5f, 0.8f);
            return true;
        }

        // 檢查魔力
        if (!this.manaService.consume(uuid, spell.manaCost)) {
            player.sendActionBar(Component.text("✗ 魔力不足 ", TextColor.color(0xE11D48))
                    .append(Component.text("(需要 " + spell.manaCost + " 魔力)",
                            TextColor.color(0xC4B5FD)))
                    .decoration(TextDecoration.ITALIC, false));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS,
                    SoundCategory.PLAYERS, 0.5f, 1.0f);
            return true;
        }

        // 進入冷卻（套用冷卻縮減屬性，上限 40%）
        double cdr = this.skillService.getStatValue(uuid,
                com.rui.techproject.service.SkillService.Stat.COOLDOWN_REDUCTION);
        cdr = Math.min(40.0, Math.max(0.0, cdr));
        final long actualCooldownMs = (long) (spell.cooldownTicks * 50L * (1.0 - cdr / 100.0));
        this.setCooldown(uuid, spell, now + actualCooldownMs);
        this.spellExecutor.execute(player, spell);
        // 施法成功 → 共鳴經驗
        if (this.plugin.getSkillService() != null) {
            this.plugin.getSkillService().grantXp(player, "resonance", 3L);
        }
        return true;
    }

    /**
     * 顯示 combo 進度（輸入序列 + 還缺幾下）。
     *
     * <p>使用 Title 的 subtitle 顯示，避免被 {@link ManaService} 每秒更新的
     * 魔力 actionbar 蓋掉。Title 的 stay time 設為比 combo window 略長，
     * 讓玩家有充足時間看清楚進度；下次再呼叫會自動覆寫前一個 title。
     */
    private void showComboProgress(final Player player, final ComboBuffer buffer) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i < buffer.size()) {
                sb.append(buffer.displayAt(i));
            } else {
                sb.append("·");
            }
            if (i < 2) sb.append(" · ");
        }
        final net.kyori.adventure.text.Component subtitle =
                Component.text("▸ ", TextColor.color(0xC4B5FD))
                        .append(Component.text(sb.toString(),
                                TextColor.color(0xFBBF24), TextDecoration.BOLD))
                        .decoration(TextDecoration.ITALIC, false);
        final net.kyori.adventure.title.Title title =
                net.kyori.adventure.title.Title.title(
                        Component.empty(),
                        subtitle,
                        net.kyori.adventure.title.Title.Times.times(
                                java.time.Duration.ZERO,
                                java.time.Duration.ofMillis(COMBO_WINDOW_MS + 300L),
                                java.time.Duration.ofMillis(200L)));
        player.showTitle(title);
    }

    // ═══════════════════════════════════════════
    //  板手互動排除：給 SpellCastListener 呼叫
    // ═══════════════════════════════════════════

    /**
     * 判斷玩家右鍵點擊的方塊是否為受管機器。
     * 若是，Listener 就不會把這次點擊餵進 combo 緩衝，避免板手操作被算成 R 輸入。
     */
    public boolean isManagedMachineBlock(final org.bukkit.block.Block block) {
        if (block == null) return false;
        if (this.plugin.getMachineService() == null) return false;
        return this.plugin.getMachineService().resolveManagedMachineBlock(block) != null;
    }

    // ═══════════════════════════════════════════
    //  冷卻查詢 API（供 GUI 顯示使用）
    // ═══════════════════════════════════════════

    public long getCooldownExpire(final UUID uuid, final SpellId spell) {
        final Map<SpellId, Long> map = this.cooldowns.get(uuid);
        if (map == null) return 0L;
        final Long v = map.get(spell);
        return v == null ? 0L : v;
    }

    /** 查詢剩餘冷卻秒數；0 = 無冷卻。 */
    public long getCooldownSeconds(final UUID uuid, final SpellId spell) {
        final long remain = this.getCooldownExpire(uuid, spell) - System.currentTimeMillis();
        return remain <= 0 ? 0 : (remain + 999L) / 1000L;
    }

    private void setCooldown(final UUID uuid, final SpellId spell, final long expireMs) {
        this.cooldowns.computeIfAbsent(uuid, k -> new HashMap<>()).put(spell, expireMs);
    }

    /** 玩家離線時清理狀態。 */
    public void clearPlayer(final UUID uuid) {
        this.comboBuffers.remove(uuid);
        this.cooldowns.remove(uuid);
    }

    // ═══════════════════════════════════════════
    //  內部資料結構
    // ═══════════════════════════════════════════

    private static final class ComboBuffer {
        final SkillService.Skill skill;
        final long startMs;
        /** combo 判定用（true=蹲, false=不蹲） */
        boolean c1;
        boolean c2;
        boolean c3;
        /** 顯示用：記錄每次點擊是否為左鍵 */
        boolean left1;
        boolean left2;
        boolean left3;
        int count;

        ComboBuffer(final SkillService.Skill skill, final long startMs) {
            this.skill = skill;
            this.startMs = startMs;
        }

        int size() { return this.count; }

        void push(final boolean sneaking, final boolean isLeftClick) {
            switch (this.count) {
                case 0 -> { this.c1 = sneaking; this.left1 = isLeftClick; }
                case 1 -> { this.c2 = sneaking; this.left2 = isLeftClick; }
                case 2 -> { this.c3 = sneaking; this.left3 = isLeftClick; }
                default -> { return; }
            }
            this.count++;
        }

        /** 取得第 i 個輸入的顯示文字（0-based） */
        String displayAt(final int i) {
            final boolean sneak = i == 0 ? this.c1 : (i == 1 ? this.c2 : this.c3);
            final boolean left = i == 0 ? this.left1 : (i == 1 ? this.left2 : this.left3);
            if (sneak) return left ? "蹲左" : "蹲右";
            return left ? "左" : "右";
        }
    }
}
