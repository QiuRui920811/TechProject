package com.rui.techproject.service.talent;

import com.rui.techproject.service.SkillService;

/**
 * 24 個主動技能定義。每個技能綁定一棵技能樹（Skill）與一組按鍵組合（Combo），
 * 並定義顯示名稱、描述、魔力消耗、冷卻時間（tick）。
 *
 * <p>實際的粒子效果與遊戲邏輯由 {@link SpellExecutor} 以 switch 分派實作。
 */
public enum SpellId {
    // ── Combat（蒼浪之刃）── 蒼浪劍道
    COMBAT_LRL("water_surface_slash", SkillService.Skill.COMBAT, Combo.LRL,
            "碎浪斬", "前方 180° 弧形劍氣 + 水花衝擊波", "蒼浪之刃", 40, 200),
    COMBAT_RLR("thunderclap_flash", SkillService.Skill.COMBAT, Combo.RLR,
            "霹靂一閃", "瞬間雷速拔刀斬 + 雷電軌跡", "蒼浪之刃", 50, 360),
    COMBAT_LLR("whirlpool", SkillService.Skill.COMBAT, Combo.LLR,
            "漩淵", "生成水漩渦，吸引並持續傷害周圍敵人", "蒼浪之刃", 65, 900),
    COMBAT_RRL("seisei_ruten", SkillService.Skill.COMBAT, Combo.RRL,
            "生生流轉", "凝出具象水龍，沿視線蜿蜒疾馳 40 格貫穿敵陣", "蒼浪之刃", 80, 1200),

    // ── Exploration（疾風獵弓） ──
    EXPLORATION_LRL("wind_waltz", SkillService.Skill.EXPLORATION, Combo.LRL,
            "疾風三步", "3 段瞬移各 8 格 + 緩降", "疾風獵弓", 40, 400),
    EXPLORATION_RLR("grappling_hook", SkillService.Skill.EXPLORATION, Combo.RLR,
            "能量鉤索", "鉤向視線方塊（最遠 30 格）", "疾風獵弓", 20, 160),
    EXPLORATION_LLR("storm_arrows", SkillService.Skill.EXPLORATION, Combo.LLR,
            "風暴箭雨", "召喚風暴箭矢從天而降轟炸前方", "疾風獵弓", 60, 1200),
    EXPLORATION_RRL("shadow_strike", SkillService.Skill.EXPLORATION, Combo.RRL,
            "影襲", "隱身 8s + 下一擊必爆擊", "疾風獵弓", 50, 900),

    // ── Gathering（大地之鎬） ──
    GATHERING_LRL("vein_chain", SkillService.Skill.GATHERING, Combo.LRL,
            "礦脈連鎖", "下一鎬自動連挖整礦脈（64格）", "大地之鎬", 30, 300),
    GATHERING_RLR("earth_pulse", SkillService.Skill.GATHERING, Combo.RLR,
            "地脈震盪", "5×5×5 礦物全收背包", "大地之鎬", 80, 2400),
    GATHERING_LLR("earth_shatter", SkillService.Skill.GATHERING, Combo.LLR,
            "大地裂擊", "重鎬轟地製造前方裂縫拋飛敵人", "大地之鎬", 60, 1200),
    GATHERING_RRL("earth_resonance", SkillService.Skill.GATHERING, Combo.RRL,
            "大地共鳴", "50 格內礦脈全顯（60s）", "大地之鎬", 40, 1200),

    // ── Engineering（動力扳手） ──
    ENGINEERING_LRL("turret_deploy", SkillService.Skill.ENGINEERING, Combo.LRL,
            "自動砲台", "部署 15s 自動鎖定砲台持續輸出", "動力扳手", 80, 1800),
    ENGINEERING_RLR("overload_core", SkillService.Skill.ENGINEERING, Combo.RLR,
            "過載核心", "附近科技機器 ×3 速 45s", "動力扳手", 100, 4800),
    ENGINEERING_LLR("railgun", SkillService.Skill.ENGINEERING, Combo.LLR,
            "磁軌砲", "蓄力後發射電磁穿透砲擊", "動力扳手", 70, 1200),
    ENGINEERING_RRL("emp_burst", SkillService.Skill.ENGINEERING, Combo.RRL,
            "電磁脈衝", "10 格 AoE 麻痹 5s", "動力扳手", 50, 1200),

    // ── Research（奧術法杖） ──
    RESEARCH_LRL("meteor", SkillService.Skill.RESEARCH, Combo.LRL,
            "隕石術", "視線方向召喚隕石墜落", "奧術法杖", 100, 900),
    RESEARCH_RLR("time_freeze", SkillService.Skill.RESEARCH, Combo.RLR,
            "時間凍結", "15 格敵人凍結 5s", "奧術法杖", 80, 1800),
    RESEARCH_LLR("elemental_beam", SkillService.Skill.RESEARCH, Combo.LLR,
            "元素裂變", "穿透能量束", "奧術法杖", 60, 600),
    RESEARCH_RRL("void_collapse", SkillService.Skill.RESEARCH, Combo.RRL,
            "虛空坍縮", "製造黑洞吸引敵人後爆炸", "奧術法杖", 80, 1800),

    // ── Resonance（共鳴水晶） ──
    RESONANCE_LRL("healing_wave", SkillService.Skill.RESONANCE, Combo.LRL,
            "治癒波動", "15 格 AoE 回 60% HP", "共鳴水晶", 80, 1200),
    RESONANCE_RLR("resonance_shield", SkillService.Skill.RESONANCE, Combo.RLR,
            "共振護盾", "自己+隊友 50% 吸收 20s", "共鳴水晶", 70, 1800),
    RESONANCE_LLR("starburst", SkillService.Skill.RESONANCE, Combo.LLR,
            "星爆", "聚集星光能量後釋放結晶新星爆炸", "共鳴水晶", 80, 1500),
    RESONANCE_RRL("beacon_call", SkillService.Skill.RESONANCE, Combo.RRL,
            "傳送信標", "標記位置，再次施法召集隊友", "共鳴水晶", 120, 6000);

    public final String id;
    public final SkillService.Skill skill;
    public final Combo combo;
    public final String displayName;
    public final String description;
    /** 對應武器的簡短提示（如「⚔ 劍/斧」）。 */
    public final String weaponHint;
    public final int manaCost;
    public final int cooldownTicks;

    SpellId(final String id, final SkillService.Skill skill, final Combo combo,
            final String displayName, final String description, final String weaponHint,
            final int manaCost, final int cooldownTicks) {
        this.id = id;
        this.skill = skill;
        this.combo = combo;
        this.displayName = displayName;
        this.description = description;
        this.weaponHint = weaponHint;
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
