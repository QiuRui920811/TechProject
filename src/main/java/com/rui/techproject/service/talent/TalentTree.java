package com.rui.techproject.service.talent;

import com.rui.techproject.service.SkillService;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * 單一技能樹 — 包含 15 個 {@link TalentNode}（每個 {@link TalentSlot} 一個）。
 */
public final class TalentTree {

    private final SkillService.Skill skill;
    private final Map<TalentSlot, TalentNode> nodes;
    private final String displayName;
    private final String tagline;

    public TalentTree(final SkillService.Skill skill,
                       final String displayName,
                       final String tagline,
                       final Map<TalentSlot, TalentNode> nodes) {
        this.skill = skill;
        this.displayName = displayName;
        this.tagline = tagline;
        this.nodes = Collections.unmodifiableMap(new EnumMap<>(nodes));
    }

    public SkillService.Skill skill() { return this.skill; }
    public String displayName() { return this.displayName; }
    public String tagline() { return this.tagline; }
    public Map<TalentSlot, TalentNode> nodes() { return this.nodes; }

    public TalentNode node(final TalentSlot slot) {
        return this.nodes.get(slot);
    }
}
