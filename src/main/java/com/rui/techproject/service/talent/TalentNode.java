package com.rui.techproject.service.talent;

import com.rui.techproject.service.SkillService;
import org.bukkit.Material;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 單一技能樹節點（實例，隸屬於某棵樹）。
 * 由 {@link TalentTreeRegistry} 在 class-load 時建立。
 */
public final class TalentNode {

    private final String id;                        // 全域唯一 id：{skillId}_{slotId}
    private final SkillService.Skill skill;
    private final TalentSlot slot;
    private final String displayName;
    private final String description;
    private final Material icon;
    private final Map<SkillService.Stat, Double> bonusesPerRank;  // 每 rank 加多少
    private final List<String> flavorText;          // 下方灰色說明文

    public TalentNode(final SkillService.Skill skill,
                       final TalentSlot slot,
                       final String displayName,
                       final String description,
                       final Material icon,
                       final Map<SkillService.Stat, Double> bonusesPerRank,
                       final List<String> flavorText) {
        this.skill = skill;
        this.slot = slot;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.bonusesPerRank = bonusesPerRank == null
                ? Collections.emptyMap()
                : new LinkedHashMap<>(bonusesPerRank);
        this.flavorText = flavorText == null ? Collections.emptyList() : flavorText;
        this.id = skill.id + "_" + slot.slotId;
    }

    public String id() { return this.id; }
    public SkillService.Skill skill() { return this.skill; }
    public TalentSlot slot() { return this.slot; }
    public String displayName() { return this.displayName; }
    public String description() { return this.description; }
    public Material icon() { return this.icon; }
    public Map<SkillService.Stat, Double> bonusesPerRank() { return this.bonusesPerRank; }
    public List<String> flavorText() { return this.flavorText; }

    public TalentNodeType type() { return this.slot.type; }
    public int gridX() { return this.slot.x; }
    public int gridY() { return this.slot.y; }
    public int maxRank() { return this.slot.type.maxRank; }
    public int costPerRank() { return this.slot.type.costPerRank; }
    public Combo combo() { return this.slot.combo; }
    public SpellId spell() {
        return this.slot.combo == null ? null : SpellId.find(this.skill, this.slot.combo);
    }

    public boolean isKeystone() { return this.slot.type == TalentNodeType.KEYSTONE; }
    public boolean isStart() { return this.slot.type == TalentNodeType.START; }

    /** 計算在指定 rank 時總共加多少某屬性。 */
    public double totalBonus(final SkillService.Stat stat, final int rank) {
        final Double per = this.bonusesPerRank.get(stat);
        if (per == null) return 0.0;
        return per * Math.max(0, rank);
    }
}
