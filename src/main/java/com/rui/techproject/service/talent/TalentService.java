package com.rui.techproject.service.talent;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.service.PlayerProgressService;
import com.rui.techproject.service.SkillService;
import com.rui.techproject.service.SkillService.Skill;
import com.rui.techproject.service.SkillService.Stat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * 天賦點數 + 節點投入 持久化服務。
 *
 * <p>資料全部寫到 {@link PlayerProgressService#stats} Map，key 形式：
 * <ul>
 *   <li>{@code talent_points_{skillId}} — 該技能可用的天賦點數（升級時 +1）</li>
 *   <li>{@code talent_rank_{skillId}_{slotId}} — 某節點目前的 rank 數</li>
 * </ul>
 *
 * <p>規則：
 * <ul>
 *   <li>每次該技能升級會自動 +1 天賦點（由 {@link SkillService#onLevelUp} 呼叫 {@link #grantPoint}）</li>
 *   <li>要投資某節點，它的 prereq（前置節點）必須至少 rank ≥ 1</li>
 *   <li>起點節點（START）由 {@link #ensureStartUnlocked} 自動投入 1 rank（免費）</li>
 *   <li>Keystone 節點投入後等同解鎖該法術</li>
 *   <li>重置整棵樹會退還全部天賦點，並扣除 {@value #RESET_COST_TECH_XP} 點科技經驗</li>
 * </ul>
 */
public final class TalentService {

    /** 重置整棵樹的科技經驗花費。 */
    public static final long RESET_COST_TECH_XP = 1000L;

    private final TechMCPlugin plugin;
    private final PlayerProgressService progressService;
    private final SkillService skillService;

    public TalentService(final TechMCPlugin plugin,
                          final PlayerProgressService progressService,
                          final SkillService skillService) {
        this.plugin = plugin;
        this.progressService = progressService;
        this.skillService = skillService;
    }

    // ═══════════════════════════════════════════
    //  Key helpers
    // ═══════════════════════════════════════════

    private static String pointsKey(final Skill skill) {
        return "talent_points_" + skill.id;
    }

    private static String rankKey(final Skill skill, final TalentSlot slot) {
        return "talent_rank_" + skill.id + "_" + slot.slotId;
    }

    // ═══════════════════════════════════════════
    //  點數 API
    // ═══════════════════════════════════════════

    /** 取得某技能目前可用的天賦點數。 */
    public int getAvailablePoints(final UUID uuid, final Skill skill) {
        return (int) this.progressService.getStat(uuid, pointsKey(skill));
    }

    /** 授予某技能 1 點天賦點（升級時由 SkillService 呼叫）。 */
    public void grantPoint(final Player player, final Skill skill, final int amount) {
        if (amount <= 0) return;
        this.progressService.incrementStat(player.getUniqueId(), pointsKey(skill), amount);
        player.sendMessage(Component.text("✦ ", NamedTextColor.GOLD)
                .append(Component.text("獲得 " + amount + " 點 ", NamedTextColor.YELLOW))
                .append(Component.text(skill.displayName + " 天賦點",
                        NamedTextColor.AQUA, TextDecoration.BOLD)));
    }

    /** 起點節點保證已投入 1 rank（GUI 開啟時自動呼叫）。 */
    public void ensureStartUnlocked(final UUID uuid, final Skill skill) {
        if (this.getRank(uuid, skill, TalentSlot.START) == 0) {
            this.progressService.setStat(uuid, rankKey(skill, TalentSlot.START), 1L);
        }
    }

    // ═══════════════════════════════════════════
    //  節點 rank API
    // ═══════════════════════════════════════════

    public int getRank(final UUID uuid, final Skill skill, final TalentSlot slot) {
        return (int) this.progressService.getStat(uuid, rankKey(skill, slot));
    }

    public boolean isUnlocked(final UUID uuid, final Skill skill, final TalentSlot slot) {
        return this.getRank(uuid, skill, slot) > 0;
    }

    /** 檢查前置節點是否全部已投入（至少 rank ≥ 1）。 */
    public boolean isPrereqMet(final UUID uuid, final Skill skill, final TalentSlot slot) {
        if (slot.prereqs.isEmpty()) return true;
        for (final String prereqId : slot.prereqs) {
            final TalentSlot prereq = TalentSlot.bySlotId(prereqId);
            if (prereq == null) return false;
            if (this.getRank(uuid, skill, prereq) <= 0) return false;
        }
        return true;
    }

    /**
     * 嘗試投入一點到指定節點。
     *
     * @return true 成功投入；false 表示點數不足 / 前置未滿足 / 已達最大 rank
     */
    public boolean investPoint(final Player player, final Skill skill, final TalentSlot slot) {
        final UUID uuid = player.getUniqueId();
        this.ensureStartUnlocked(uuid, skill);

        final TalentNode node = TalentTreeRegistry.findNode(skill, slot);
        if (node == null) return false;

        final int currentRank = this.getRank(uuid, skill, slot);
        if (currentRank >= node.maxRank()) {
            player.sendActionBar(Component.text("✗ 此節點已達最高層級",
                            TextColor.color(0xFB7185))
                    .decoration(TextDecoration.ITALIC, false));
            return false;
        }
        if (!this.isPrereqMet(uuid, skill, slot)) {
            player.sendActionBar(Component.text("✗ 前置節點尚未投入",
                            TextColor.color(0xFB7185))
                    .decoration(TextDecoration.ITALIC, false));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS,
                    SoundCategory.PLAYERS, 0.5f, 0.7f);
            return false;
        }
        final int cost = node.costPerRank();
        final int available = this.getAvailablePoints(uuid, skill);
        if (available < cost) {
            player.sendActionBar(Component.text("✗ 天賦點不足（需要 " + cost
                            + "，目前 " + available + "）", TextColor.color(0xFB7185))
                    .decoration(TextDecoration.ITALIC, false));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS,
                    SoundCategory.PLAYERS, 0.5f, 0.9f);
            return false;
        }

        // 扣點、增加 rank
        this.progressService.setStat(uuid, pointsKey(skill), available - cost);
        this.progressService.setStat(uuid, rankKey(skill, slot), currentRank + 1L);

        player.sendMessage(Component.text("✦ ", TextColor.color(0xFBBF24))
                .append(Component.text("投入 ", TextColor.color(0xFCD34D)))
                .append(Component.text(node.displayName(), TextColor.color(0xEC4899),
                        TextDecoration.BOLD))
                .append(Component.text(" → 層級 " + (currentRank + 1) + "/" + node.maxRank(),
                        TextColor.color(0xC4B5FD))));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS, 0.8f, 1.3f);

        if (node.isKeystone() && currentRank == 0) {
            // 首次解鎖 keystone → 宣告法術解鎖
            final SpellId spell = node.spell();
            if (spell != null) {
                player.sendMessage(Component.text("✦✦✦ ", TextColor.color(0xFBBF24))
                        .append(Component.text("解鎖法術：", TextColor.color(0xEC4899),
                                TextDecoration.BOLD))
                        .append(Component.text(spell.displayName, TextColor.color(0xC4B5FD),
                                TextDecoration.BOLD))
                        .append(Component.text("（" + spell.combo.display + "）",
                                TextColor.color(0xCBD5E1))));
                player.sendMessage(Component.text("  » " + spell.description,
                        TextColor.color(0x94A3B8)));
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE,
                        SoundCategory.PLAYERS, 1.0f, 1.5f);
            }
        }
        return true;
    }

    // ═══════════════════════════════════════════
    //  重置
    // ═══════════════════════════════════════════

    /**
     * 重置整棵技能樹（退還全部點數並扣除 1000 科技經驗）。
     */
    public boolean resetTree(final Player player, final Skill skill) {
        final UUID uuid = player.getUniqueId();
        if (!this.progressService.spendTechXp(uuid, RESET_COST_TECH_XP)) {
            player.sendMessage(Component.text("✗ 科技經驗不足，重置需要 "
                    + RESET_COST_TECH_XP + " 點。", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS,
                    SoundCategory.PLAYERS, 0.6f, 0.6f);
            return false;
        }

        int refund = 0;
        for (final TalentSlot slot : TalentSlot.values()) {
            final int rank = this.getRank(uuid, skill, slot);
            if (rank <= 0) continue;
            if (slot.type == TalentNodeType.START) continue; // 起點免費，不退
            refund += rank * slot.type.costPerRank;
            this.progressService.setStat(uuid, rankKey(skill, slot), 0L);
        }
        if (refund > 0) {
            this.progressService.incrementStat(uuid, pointsKey(skill), refund);
        }

        player.sendMessage(Component.text("✦ ", TextColor.color(0xFBBF24))
                .append(Component.text("重置 " + skill.displayName + " 技能樹，",
                        TextColor.color(0xFCD34D)))
                .append(Component.text("退還 " + refund + " 點", TextColor.color(0xC4B5FD)))
                .append(Component.text("（-" + RESET_COST_TECH_XP + " 科技經驗）",
                        TextColor.color(0x94A3B8))));
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,
                SoundCategory.PLAYERS, 0.8f, 1.2f);
        return true;
    }

    // ═══════════════════════════════════════════
    //  法術解鎖查詢 — 由 SpellCastService 呼叫
    // ═══════════════════════════════════════════

    public boolean hasLearnedSpell(final UUID uuid, final SpellId spell) {
        if (spell == null) return false;
        // 找到該 spell 對應的 keystone slot
        for (final TalentSlot slot : TalentSlot.values()) {
            if (slot.type != TalentNodeType.KEYSTONE) continue;
            if (slot.combo != spell.combo) continue;
            // 必須同技能樹
            if (this.getRank(uuid, spell.skill, slot) > 0) return true;
        }
        return false;
    }

    // ═══════════════════════════════════════════
    //  天賦屬性加成（供 SkillService.getStatValue 合併）
    // ═══════════════════════════════════════════

    /**
     * 計算某玩家全部 6 棵技能樹上的屬性加成總和。
     * 由 {@link SkillService#getStatValue} 在計算屬性時呼叫。
     */
    public Map<Stat, Double> sumAllBonuses(final UUID uuid) {
        final Map<Stat, Double> result = new EnumMap<>(Stat.class);
        for (final Skill skill : Skill.values()) {
            final TalentTree tree = TalentTreeRegistry.tree(skill);
            if (tree == null) continue;
            for (final Map.Entry<TalentSlot, TalentNode> entry : tree.nodes().entrySet()) {
                final TalentSlot slot = entry.getKey();
                final TalentNode node = entry.getValue();
                final int rank = this.getRank(uuid, skill, slot);
                if (rank <= 0) continue;
                for (final Map.Entry<Stat, Double> bonus : node.bonusesPerRank().entrySet()) {
                    result.merge(bonus.getKey(), bonus.getValue() * rank, Double::sum);
                }
            }
        }
        return result;
    }

    public double sumBonus(final UUID uuid, final Stat stat) {
        double total = 0.0;
        for (final Skill skill : Skill.values()) {
            final TalentTree tree = TalentTreeRegistry.tree(skill);
            if (tree == null) continue;
            for (final Map.Entry<TalentSlot, TalentNode> entry : tree.nodes().entrySet()) {
                final int rank = this.getRank(uuid, skill, entry.getKey());
                if (rank <= 0) continue;
                final Double per = entry.getValue().bonusesPerRank().get(stat);
                if (per != null) total += per * rank;
            }
        }
        return total;
    }
}
