package com.rui.techproject.service.talent;

import com.rui.techproject.service.SkillService;

/**
 * 24 個主動技能定義。每個技能綁定一棵技能樹（Skill）與一組按鍵組合（Combo），
 * 並定義顯示名稱、描述、魔力消耗、冷卻時間（tick）。
 *
 * <p>實際的粒子效果與遊戲邏輯由 {@link SpellExecutor} 以 switch 分派實作。
 */
public enum SpellId {
    // ── Combat（劍/斧）── 蒼浪劍道
    COMBAT_LRL("water_surface_slash", SkillService.Skill.COMBAT, Combo.LRL,
            "碎浪斬", "前方 180° 弧形劍氣 + 水花衝擊波", 40, 200),
    COMBAT_RLR("flowing_dance", SkillService.Skill.COMBAT, Combo.RLR,
            "奔流三式", "3 段水流突進，留下殘影軌跡", 50, 360),
    COMBAT_LLR("whirlpool", SkillService.Skill.COMBAT, Combo.LLR,
            "漩淵", "生成水漩渦，吸引並持續傷害周圍敵人", 65, 900),
    COMBAT_RRL("dead_calm", SkillService.Skill.COMBAT, Combo.RRL,
            "止水", "進入止水之境：3 秒無敵 + 反射傷害", 80, 1200),

    // ── Exploration（弓/十字弓） ──
    EXPLORATION_LRL("wind_waltz", SkillService.Skill.EXPLORATION, Combo.LRL,
            "疾風三步", "3 段瞬移各 8 格 + 緩降", 40, 400),
    EXPLORATION_RLR("grappling_hook", SkillService.Skill.EXPLORATION, Combo.RLR,
            "能量鉤索", "鉤向視線方塊（最遠 30 格）", 20, 160),
    EXPLORATION_LLR("hunters_mark", SkillService.Skill.EXPLORATION, Combo.LLR,
            "獵人之眼", "80 格內生物全顯形 30s", 60, 1800),
    EXPLORATION_RRL("shadow_strike", SkillService.Skill.EXPLORATION, Combo.RRL,
            "影襲", "隱身 8s + 下一擊必爆擊", 50, 900),

    // ── Gathering（鎬/鋤） ──
    GATHERING_LRL("vein_chain", SkillService.Skill.GATHERING, Combo.LRL,
            "礦脈連鎖", "下一鎬自動連挖整礦脈（64格）", 30, 300),
    GATHERING_RLR("earth_pulse", SkillService.Skill.GATHERING, Combo.RLR,
            "地脈震盪", "5×5×5 礦物全收背包", 80, 2400),
    GATHERING_LLR("bountiful", SkillService.Skill.GATHERING, Combo.LLR,
            "豐饒祝福", "30 秒內掉落 ×3", 60, 3600),
    GATHERING_RRL("earth_resonance", SkillService.Skill.GATHERING, Combo.RRL,
            "大地共鳴", "50 格內礦脈全顯（60s）", 40, 1200),

    // ── Engineering（扳手） ──
    ENGINEERING_LRL("mech_construct", SkillService.Skill.ENGINEERING, Combo.LRL,
            "機械傀儡", "召 30s 跟隨戰鬥機器人", 80, 2400),
    ENGINEERING_RLR("overload_core", SkillService.Skill.ENGINEERING, Combo.RLR,
            "過載核心", "附近科技機器 ×3 速 45s", 100, 4800),
    ENGINEERING_LLR("auto_repair", SkillService.Skill.ENGINEERING, Combo.LLR,
            "自動修復", "所有裝備立即回滿耐久", 60, 6000),
    ENGINEERING_RRL("emp_burst", SkillService.Skill.ENGINEERING, Combo.RRL,
            "電磁脈衝", "10 格 AoE 麻痹 5s", 50, 1200),

    // ── Research（法杖） ──
    RESEARCH_LRL("meteor", SkillService.Skill.RESEARCH, Combo.LRL,
            "隕石術", "視線方向召喚隕石墜落", 100, 900),
    RESEARCH_RLR("time_freeze", SkillService.Skill.RESEARCH, Combo.RLR,
            "時間凍結", "15 格敵人凍結 5s", 80, 1800),
    RESEARCH_LLR("elemental_beam", SkillService.Skill.RESEARCH, Combo.LLR,
            "元素裂變", "穿透能量束", 60, 600),
    RESEARCH_RRL("quantum_dup", SkillService.Skill.RESEARCH, Combo.RRL,
            "量子複製", "下次合成產出 ×2", 80, 3600),

    // ── Resonance（共鳴器） ──
    RESONANCE_LRL("healing_wave", SkillService.Skill.RESONANCE, Combo.LRL,
            "治癒波動", "15 格 AoE 回 60% HP", 80, 1200),
    RESONANCE_RLR("resonance_shield", SkillService.Skill.RESONANCE, Combo.RLR,
            "共振護盾", "自己+隊友 50% 吸收 20s", 70, 1800),
    RESONANCE_LLR("power_resonance", SkillService.Skill.RESONANCE, Combo.LLR,
            "力量共鳴", "附近玩家 +30% 傷 30s", 90, 2400),
    RESONANCE_RRL("beacon_call", SkillService.Skill.RESONANCE, Combo.RRL,
            "傳送信標", "標記位置，再次施法召集隊友", 120, 6000);

    public final String id;
    public final SkillService.Skill skill;
    public final Combo combo;
    public final String displayName;
    public final String description;
    public final int manaCost;
    public final int cooldownTicks;

    SpellId(final String id, final SkillService.Skill skill, final Combo combo,
            final String displayName, final String description,
            final int manaCost, final int cooldownTicks) {
        this.id = id;
        this.skill = skill;
        this.combo = combo;
        this.displayName = displayName;
        this.description = description;
        this.manaCost = manaCost;
        this.cooldownTicks = cooldownTicks;
    }

    public static SpellId find(final SkillService.Skill skill, final Combo combo) {
        if (skill == null || combo == null) return null;
        for (final SpellId s : values()) {
            if (s.skill == skill && s.combo == combo) return s;
        }
        return null;
    }
}
