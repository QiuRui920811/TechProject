package com.rui.techproject.service.talent;

import com.rui.techproject.service.SkillService.Skill;
import com.rui.techproject.service.SkillService.Stat;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 所有 6 棵技能樹的靜態定義。總節點：6 × 15 = 90 個。
 * 由 class-load 時一次建立，之後唯讀。
 */
public final class TalentTreeRegistry {

    private static final Map<Skill, TalentTree> TREES = new EnumMap<>(Skill.class);

    static {
        TREES.put(Skill.COMBAT,      buildCombatTree());
        TREES.put(Skill.EXPLORATION, buildExplorationTree());
        TREES.put(Skill.GATHERING,   buildGatheringTree());
        TREES.put(Skill.ENGINEERING, buildEngineeringTree());
        TREES.put(Skill.RESEARCH,    buildResearchTree());
        TREES.put(Skill.RESONANCE,   buildResonanceTree());
    }

    private TalentTreeRegistry() {}

    public static TalentTree tree(final Skill skill) {
        return TREES.get(skill);
    }

    public static Map<Skill, TalentTree> all() {
        return TREES;
    }

    /** 取得全樹任何節點（不分 skill）— 用於反查。 */
    public static TalentNode findNode(final Skill skill, final TalentSlot slot) {
        final TalentTree tree = TREES.get(skill);
        return tree == null ? null : tree.node(slot);
    }

    // ═══════════════════════════════════════════════════════════
    //  COMBAT — 蒼浪劍道
    // ═══════════════════════════════════════════════════════════
    private static TalentTree buildCombatTree() {
        final Map<TalentSlot, TalentNode> nodes = new LinkedHashMap<>();

        nodes.put(TalentSlot.START, new TalentNode(
                Skill.COMBAT, TalentSlot.START,
                "劍士之心", "免費起點 — 踏上蒼浪劍道",
                Material.LIME_STAINED_GLASS_PANE,
                Map.of(Stat.STRENGTH, 2.0),
                List.of("以水為劍，斬破萬物")));

        nodes.put(TalentSlot.MINOR_BASE, new TalentNode(
                Skill.COMBAT, TalentSlot.MINOR_BASE,
                "水面集中", "每級 +1 力量",
                Material.IRON_INGOT,
                Map.of(Stat.STRENGTH, 1.0),
                List.of("感受水的流動，平靜心神")));

        nodes.put(TalentSlot.MINOR_LEFT_L, new TalentNode(
                Skill.COMBAT, TalentSlot.MINOR_LEFT_L,
                "堅韌體魄", "每級 +1 防禦",
                Material.IRON_CHESTPLATE,
                Map.of(Stat.DEFENSE, 1.0),
                List.of("如磐石般承受水流的衝擊")));

        nodes.put(TalentSlot.MINOR_MID, new TalentNode(
                Skill.COMBAT, TalentSlot.MINOR_MID,
                "致命水流", "每級 +2% 爆擊傷害",
                Material.PRISMARINE_SHARD,
                Map.of(Stat.CRIT_DAMAGE, 2.0),
                List.of("水能穿石，找到最脆弱之處")));

        nodes.put(TalentSlot.MINOR_RIGHT_R, new TalentNode(
                Skill.COMBAT, TalentSlot.MINOR_RIGHT_R,
                "激流之力", "每級 +1 力量",
                Material.HEART_OF_THE_SEA,
                Map.of(Stat.STRENGTH, 1.0),
                List.of("急流洶湧，勢不可擋")));

        nodes.put(TalentSlot.NOTABLE_LEFT, new TalentNode(
                Skill.COMBAT, TalentSlot.NOTABLE_LEFT,
                "碎波斬擊", "一次性 +5 力量、+10% 爆擊傷害",
                Material.NETHERITE_SWORD,
                Map.of(Stat.STRENGTH, 5.0, Stat.CRIT_DAMAGE, 10.0),
                List.of("揮劍如碎浪，擊穿一切防禦")));

        nodes.put(TalentSlot.KEYSTONE_LLR, new TalentNode(
                Skill.COMBAT, TalentSlot.KEYSTONE_LLR,
                "漩淵 ✦", "解鎖主動：生成水漩渦吸引並傷害敵人",
                Material.NAUTILUS_SHELL,
                Map.of(Stat.STRENGTH, 5.0),
                List.of("▸ 輸入 右·右·蹲右 施放", "▸ 45 秒冷卻 / 消耗 65 魔力")));

        nodes.put(TalentSlot.KEYSTONE_RRL, new TalentNode(
                Skill.COMBAT, TalentSlot.KEYSTONE_RRL,
                "止水 ✦", "解鎖主動：3 秒無敵結界 + 反射傷害",
                Material.BLUE_ICE,
                Map.of(Stat.DEFENSE, 10.0, Stat.STRENGTH, 5.0),
                List.of("▸ 輸入 蹲右·蹲右·右 施放", "▸ 60 秒冷卻 / 消耗 80 魔力")));

        nodes.put(TalentSlot.NOTABLE_MID, new TalentNode(
                Skill.COMBAT, TalentSlot.NOTABLE_MID,
                "奔流連斬", "一次性 +10 暴擊傷害、+5% 爆擊率",
                Material.IRON_SWORD,
                Map.of(Stat.CRIT_DAMAGE, 10.0, Stat.CRIT_CHANCE, 5.0),
                List.of("水流奔騰，劍劍相連")));

        nodes.put(TalentSlot.MINOR_UP1, new TalentNode(
                Skill.COMBAT, TalentSlot.MINOR_UP1,
                "劍氣凝水", "每級 +2 力量",
                Material.TRIDENT,
                Map.of(Stat.STRENGTH, 2.0),
                List.of("將劍氣化為水刃")));

        nodes.put(TalentSlot.MINOR_UP2, new TalentNode(
                Skill.COMBAT, TalentSlot.MINOR_UP2,
                "怒濤之息", "每級 +2% 爆擊傷害、+0.5% 爆擊率",
                Material.PRISMARINE_CRYSTALS,
                Map.of(Stat.CRIT_DAMAGE, 2.0, Stat.CRIT_CHANCE, 0.5),
                List.of("深呼吸，如海嘯般爆發")));

        nodes.put(TalentSlot.NOTABLE_TL, new TalentNode(
                Skill.COMBAT, TalentSlot.NOTABLE_TL,
                "大海之力", "一次性 +8 力量、+5 防禦",
                Material.CONDUIT,
                Map.of(Stat.STRENGTH, 8.0, Stat.DEFENSE, 5.0),
                List.of("蘊含著整片海洋的力量")));

        nodes.put(TalentSlot.NOTABLE_TR, new TalentNode(
                Skill.COMBAT, TalentSlot.NOTABLE_TR,
                "潮汐衝鋒", "一次性 +5% 爆擊率、+15% 爆擊傷害",
                Material.GHAST_TEAR,
                Map.of(Stat.CRIT_CHANCE, 5.0, Stat.CRIT_DAMAGE, 15.0),
                List.of("乘著潮汐，一擊必殺")));

        nodes.put(TalentSlot.KEYSTONE_LRL, new TalentNode(
                Skill.COMBAT, TalentSlot.KEYSTONE_LRL,
                "碎浪斬 ✦", "解鎖主動：前方 180° 弧形劍氣 + 水花衝擊波",
                Material.DIAMOND_SWORD,
                Map.of(Stat.STRENGTH, 8.0),
                List.of("▸ 輸入 右·蹲右·右 施放", "▸ 10 秒冷卻 / 消耗 40 魔力")));

        nodes.put(TalentSlot.KEYSTONE_RLR, new TalentNode(
                Skill.COMBAT, TalentSlot.KEYSTONE_RLR,
                "奔流三式 ✦", "解鎖主動：3 段水流突進 + 殘影軌跡",
                Material.NETHERITE_AXE,
                Map.of(Stat.STRENGTH, 5.0, Stat.CRIT_CHANCE, 5.0),
                List.of("▸ 輸入 蹲右·右·蹲右 施放", "▸ 18 秒冷卻 / 消耗 50 魔力")));

        return new TalentTree(Skill.COMBAT, "戰鬥", "蒼浪劍道", nodes);
    }

    // ═══════════════════════════════════════════════════════════
    //  EXPLORATION — 弓/十字弓，機動性與偵察
    // ═══════════════════════════════════════════════════════════
    private static TalentTree buildExplorationTree() {
        final Map<TalentSlot, TalentNode> nodes = new LinkedHashMap<>();

        nodes.put(TalentSlot.START, new TalentNode(
                Skill.EXPLORATION, TalentSlot.START,
                "旅人之心", "免費起點 — 無止境的遠方",
                Material.LIME_STAINED_GLASS_PANE,
                Map.of(Stat.SPEED, 2.0),
                List.of("雙腳就是最可靠的交通工具")));

        nodes.put(TalentSlot.MINOR_BASE, new TalentNode(
                Skill.EXPLORATION, TalentSlot.MINOR_BASE,
                "輕盈步伐", "每級 +1 速度",
                Material.FEATHER,
                Map.of(Stat.SPEED, 1.0),
                List.of("像風一樣自由")));

        nodes.put(TalentSlot.MINOR_LEFT_L, new TalentNode(
                Skill.EXPLORATION, TalentSlot.MINOR_LEFT_L,
                "精準射擊", "每級 +1% 爆擊率",
                Material.ARROW,
                Map.of(Stat.CRIT_CHANCE, 1.0),
                List.of("瞄準一次，命中一次")));

        nodes.put(TalentSlot.MINOR_MID, new TalentNode(
                Skill.EXPLORATION, TalentSlot.MINOR_MID,
                "野外求生", "每級 +1 速度、+1 生命回復",
                Material.COOKED_BEEF,
                Map.of(Stat.SPEED, 1.0, Stat.HEALTH_REGEN, 1.0),
                List.of("風餐露宿練就的耐性")));

        nodes.put(TalentSlot.MINOR_RIGHT_R, new TalentNode(
                Skill.EXPLORATION, TalentSlot.MINOR_RIGHT_R,
                "快速反應", "受到傷害時獲得迅捷效果（每級 +2 秒）",
                Material.LEATHER_BOOTS,
                Map.of(),
                List.of("險境中磨練的直覺")));

        nodes.put(TalentSlot.NOTABLE_LEFT, new TalentNode(
                Skill.EXPLORATION, TalentSlot.NOTABLE_LEFT,
                "獵人專注", "一次性 +5% 爆擊率、+10% 爆擊傷害",
                Material.BOW,
                Map.of(Stat.CRIT_CHANCE, 5.0, Stat.CRIT_DAMAGE, 10.0),
                List.of("獵人從不錯過目標")));

        nodes.put(TalentSlot.KEYSTONE_LLR, new TalentNode(
                Skill.EXPLORATION, TalentSlot.KEYSTONE_LLR,
                "獵人之眼 ✦", "解鎖主動：80 格內生物顯形 30s",
                Material.SPYGLASS,
                Map.of(Stat.CRIT_CHANCE, 3.0),
                List.of("▸ 輸入 右·右·蹲右 施放", "▸ 90 秒冷卻 / 消耗 60 魔力")));

        nodes.put(TalentSlot.KEYSTONE_RRL, new TalentNode(
                Skill.EXPLORATION, TalentSlot.KEYSTONE_RRL,
                "影襲 ✦", "解鎖主動：隱身 8s + 下一擊必暴",
                Material.ENDER_PEARL,
                Map.of(Stat.SPEED, 5.0),
                List.of("▸ 輸入 蹲右·蹲右·右 施放", "▸ 45 秒冷卻 / 消耗 50 魔力")));

        nodes.put(TalentSlot.NOTABLE_MID, new TalentNode(
                Skill.EXPLORATION, TalentSlot.NOTABLE_MID,
                "疾風行者", "一次性 +8 速度",
                Material.RABBIT_FOOT,
                Map.of(Stat.SPEED, 8.0),
                List.of("踏過的地方捲起塵土")));

        nodes.put(TalentSlot.MINOR_UP1, new TalentNode(
                Skill.EXPLORATION, TalentSlot.MINOR_UP1,
                "遠射專家", "每級 +2% 爆擊傷害",
                Material.SPECTRAL_ARROW,
                Map.of(Stat.CRIT_DAMAGE, 2.0),
                List.of("千里之外一擊斃命")));

        nodes.put(TalentSlot.MINOR_UP2, new TalentNode(
                Skill.EXPLORATION, TalentSlot.MINOR_UP2,
                "風行步", "每級 +2 速度",
                Material.SUGAR,
                Map.of(Stat.SPEED, 2.0),
                List.of("連風也追不上")));

        nodes.put(TalentSlot.NOTABLE_TL, new TalentNode(
                Skill.EXPLORATION, TalentSlot.NOTABLE_TL,
                "流星射手", "一次性 +10% 爆擊率、+5% 爆擊傷害",
                Material.TIPPED_ARROW,
                Map.of(Stat.CRIT_CHANCE, 10.0, Stat.CRIT_DAMAGE, 5.0),
                List.of("劃過天際的致命光")));

        nodes.put(TalentSlot.NOTABLE_TR, new TalentNode(
                Skill.EXPLORATION, TalentSlot.NOTABLE_TR,
                "疾風之主", "一次性 +10 速度、+3% 爆擊率",
                Material.PHANTOM_MEMBRANE,
                Map.of(Stat.SPEED, 10.0, Stat.CRIT_CHANCE, 3.0),
                List.of("御風而行的術師")));

        nodes.put(TalentSlot.KEYSTONE_LRL, new TalentNode(
                Skill.EXPLORATION, TalentSlot.KEYSTONE_LRL,
                "疾風三步 ✦", "解鎖主動：3 段瞬移各 8 格 + 緩降",
                Material.CHORUS_FRUIT,
                Map.of(Stat.SPEED, 5.0),
                List.of("▸ 輸入 右·蹲右·右 施放", "▸ 20 秒冷卻 / 消耗 40 魔力")));

        nodes.put(TalentSlot.KEYSTONE_RLR, new TalentNode(
                Skill.EXPLORATION, TalentSlot.KEYSTONE_RLR,
                "能量鉤索 ✦", "解鎖主動：鉤向視線方塊（30 格）",
                Material.FISHING_ROD,
                Map.of(Stat.SPEED, 3.0),
                List.of("▸ 輸入 蹲右·右·蹲右 施放", "▸ 8 秒冷卻 / 消耗 20 魔力")));

        return new TalentTree(Skill.EXPLORATION, "探索", "風的旅人", nodes);
    }

    // ═══════════════════════════════════════════════════════════
    //  GATHERING — 鎬/鋤，資源採集專家
    // ═══════════════════════════════════════════════════════════
    private static TalentTree buildGatheringTree() {
        final Map<TalentSlot, TalentNode> nodes = new LinkedHashMap<>();

        nodes.put(TalentSlot.START, new TalentNode(
                Skill.GATHERING, TalentSlot.START,
                "工匠之心", "免費起點 — 大地的饋贈",
                Material.LIME_STAINED_GLASS_PANE,
                Map.of(Stat.LUCK, 2.0),
                List.of("一鎬一鎬，累積無數財富")));

        nodes.put(TalentSlot.MINOR_BASE, new TalentNode(
                Skill.GATHERING, TalentSlot.MINOR_BASE,
                "勤勞之手", "每級 +1 幸運",
                Material.IRON_PICKAXE,
                Map.of(Stat.LUCK, 1.0),
                List.of("多流的每滴汗都有回報")));

        nodes.put(TalentSlot.MINOR_LEFT_L, new TalentNode(
                Skill.GATHERING, TalentSlot.MINOR_LEFT_L,
                "礦工體魄", "每級 +2 最大生命",
                Material.APPLE,
                Map.of(Stat.MAX_HEALTH, 2.0),
                List.of("粗壯的背肌")));

        nodes.put(TalentSlot.MINOR_MID, new TalentNode(
                Skill.GATHERING, TalentSlot.MINOR_MID,
                "熟練採集", "每級 +1 效率",
                Material.GOLDEN_PICKAXE,
                Map.of(Stat.EFFICIENCY, 1.0),
                List.of("一眼看穿礦脈走向")));

        nodes.put(TalentSlot.MINOR_RIGHT_R, new TalentNode(
                Skill.GATHERING, TalentSlot.MINOR_RIGHT_R,
                "好運護符", "每級 +1 幸運",
                Material.GOLD_NUGGET,
                Map.of(Stat.LUCK, 1.0),
                List.of("口袋裡的幸運石")));

        nodes.put(TalentSlot.NOTABLE_LEFT, new TalentNode(
                Skill.GATHERING, TalentSlot.NOTABLE_LEFT,
                "大地祝福", "一次性 +5 幸運、+8 最大生命",
                Material.GRASS_BLOCK,
                Map.of(Stat.LUCK, 5.0, Stat.MAX_HEALTH, 8.0),
                List.of("大地記得每個勤奮的孩子")));

        nodes.put(TalentSlot.KEYSTONE_LLR, new TalentNode(
                Skill.GATHERING, TalentSlot.KEYSTONE_LLR,
                "豐饒祝福 ✦", "解鎖主動：30 秒內掉落 ×3",
                Material.ENCHANTED_GOLDEN_APPLE,
                Map.of(Stat.LUCK, 5.0),
                List.of("▸ 輸入 右·右·蹲右 施放", "▸ 180 秒冷卻 / 消耗 60 魔力")));

        nodes.put(TalentSlot.KEYSTONE_RRL, new TalentNode(
                Skill.GATHERING, TalentSlot.KEYSTONE_RRL,
                "大地共鳴 ✦", "解鎖主動：50 格礦脈顯形 60s",
                Material.SCULK_SENSOR,
                Map.of(Stat.EFFICIENCY, 5.0),
                List.of("▸ 輸入 蹲右·蹲右·右 施放", "▸ 60 秒冷卻 / 消耗 40 魔力")));

        nodes.put(TalentSlot.NOTABLE_MID, new TalentNode(
                Skill.GATHERING, TalentSlot.NOTABLE_MID,
                "採集大師", "一次性 +8 效率、+3 幸運",
                Material.DIAMOND_PICKAXE,
                Map.of(Stat.EFFICIENCY, 8.0, Stat.LUCK, 3.0),
                List.of("手感天生的採礦者")));

        nodes.put(TalentSlot.MINOR_UP1, new TalentNode(
                Skill.GATHERING, TalentSlot.MINOR_UP1,
                "鑽石之眼", "每級 +2 幸運",
                Material.DIAMOND,
                Map.of(Stat.LUCK, 2.0),
                List.of("能辨認任何礦石的光")));

        nodes.put(TalentSlot.MINOR_UP2, new TalentNode(
                Skill.GATHERING, TalentSlot.MINOR_UP2,
                "自然親和", "每級 +1 治療強度、+1 效率",
                Material.OAK_SAPLING,
                Map.of(Stat.HEALING_POWER, 1.0, Stat.EFFICIENCY, 1.0),
                List.of("草木皆我友")));

        nodes.put(TalentSlot.NOTABLE_TL, new TalentNode(
                Skill.GATHERING, TalentSlot.NOTABLE_TL,
                "礦脈嗅覺", "一次性 +10 效率、+5 幸運",
                Material.EMERALD,
                Map.of(Stat.EFFICIENCY, 10.0, Stat.LUCK, 5.0),
                List.of("遠方都能聞到礦脈的氣息")));

        nodes.put(TalentSlot.NOTABLE_TR, new TalentNode(
                Skill.GATHERING, TalentSlot.NOTABLE_TR,
                "豐收之神", "一次性 +10 幸運、+5 治療強度",
                Material.GOLDEN_APPLE,
                Map.of(Stat.LUCK, 10.0, Stat.HEALING_POWER, 5.0),
                List.of("神明親賜的幸運")));

        nodes.put(TalentSlot.KEYSTONE_LRL, new TalentNode(
                Skill.GATHERING, TalentSlot.KEYSTONE_LRL,
                "礦脈連鎖 ✦", "解鎖主動：下一鎬連挖整礦脈",
                Material.BEACON,
                Map.of(Stat.EFFICIENCY, 5.0),
                List.of("▸ 輸入 右·蹲右·右 施放", "▸ 15 秒冷卻 / 消耗 30 魔力")));

        nodes.put(TalentSlot.KEYSTONE_RLR, new TalentNode(
                Skill.GATHERING, TalentSlot.KEYSTONE_RLR,
                "地脈震盪 ✦", "解鎖主動：5×5×5 礦物全收",
                Material.NETHERITE_PICKAXE,
                Map.of(Stat.EFFICIENCY, 8.0),
                List.of("▸ 輸入 蹲右·右·蹲右 施放", "▸ 120 秒冷卻 / 消耗 80 魔力")));

        return new TalentTree(Skill.GATHERING, "採集", "大地的使者", nodes);
    }

    // ═══════════════════════════════════════════════════════════
    //  ENGINEERING — 扳手，機械 + 防禦向
    // ═══════════════════════════════════════════════════════════
    private static TalentTree buildEngineeringTree() {
        final Map<TalentSlot, TalentNode> nodes = new LinkedHashMap<>();

        nodes.put(TalentSlot.START, new TalentNode(
                Skill.ENGINEERING, TalentSlot.START,
                "工匠之心", "免費起點 — 機械之道",
                Material.LIME_STAINED_GLASS_PANE,
                Map.of(Stat.DEFENSE, 2.0),
                List.of("齒輪與扳手是朋友")));

        nodes.put(TalentSlot.MINOR_BASE, new TalentNode(
                Skill.ENGINEERING, TalentSlot.MINOR_BASE,
                "鋼鐵意志", "每級 +1 防禦",
                Material.IRON_BLOCK,
                Map.of(Stat.DEFENSE, 1.0),
                List.of("跟鋼鐵共舞的工匠")));

        nodes.put(TalentSlot.MINOR_LEFT_L, new TalentNode(
                Skill.ENGINEERING, TalentSlot.MINOR_LEFT_L,
                "護體裝甲", "每級 +2 最大生命",
                Material.SHIELD,
                Map.of(Stat.MAX_HEALTH, 2.0),
                List.of("自製的重甲")));

        nodes.put(TalentSlot.MINOR_MID, new TalentNode(
                Skill.ENGINEERING, TalentSlot.MINOR_MID,
                "精密製造", "每級 +1 效率",
                Material.CRAFTING_TABLE,
                Map.of(Stat.EFFICIENCY, 1.0),
                List.of("公差 0.01mm")));

        nodes.put(TalentSlot.MINOR_RIGHT_R, new TalentNode(
                Skill.ENGINEERING, TalentSlot.MINOR_RIGHT_R,
                "電力掌控", "每級 +1 防禦",
                Material.REDSTONE_BLOCK,
                Map.of(Stat.DEFENSE, 1.0),
                List.of("紅石流淌在血管裡")));

        nodes.put(TalentSlot.NOTABLE_LEFT, new TalentNode(
                Skill.ENGINEERING, TalentSlot.NOTABLE_LEFT,
                "裝甲核心", "一次性 +8 防禦、+10 最大生命",
                Material.NETHERITE_CHESTPLATE,
                Map.of(Stat.DEFENSE, 8.0, Stat.MAX_HEALTH, 10.0),
                List.of("移動堡壘")));

        nodes.put(TalentSlot.KEYSTONE_LLR, new TalentNode(
                Skill.ENGINEERING, TalentSlot.KEYSTONE_LLR,
                "自動修復 ✦", "解鎖主動：所有裝備回滿耐久",
                Material.ANVIL,
                Map.of(Stat.DEFENSE, 5.0),
                List.of("▸ 輸入 右·右·蹲右 施放", "▸ 300 秒冷卻 / 消耗 60 魔力")));

        nodes.put(TalentSlot.KEYSTONE_RRL, new TalentNode(
                Skill.ENGINEERING, TalentSlot.KEYSTONE_RRL,
                "電磁脈衝 ✦", "解鎖主動：10 格 AoE 麻痹 5s",
                Material.LIGHTNING_ROD,
                Map.of(Stat.DEFENSE, 3.0, Stat.EFFICIENCY, 3.0),
                List.of("▸ 輸入 蹲右·蹲右·右 施放", "▸ 60 秒冷卻 / 消耗 50 魔力")));

        nodes.put(TalentSlot.NOTABLE_MID, new TalentNode(
                Skill.ENGINEERING, TalentSlot.NOTABLE_MID,
                "機械專精", "一次性 +10 效率、+5 防禦",
                Material.PISTON,
                Map.of(Stat.EFFICIENCY, 10.0, Stat.DEFENSE, 5.0),
                List.of("機器是延伸的手臂")));

        nodes.put(TalentSlot.MINOR_UP1, new TalentNode(
                Skill.ENGINEERING, TalentSlot.MINOR_UP1,
                "液壓強化", "每級 +2 最大生命",
                Material.PISTON,
                Map.of(Stat.MAX_HEALTH, 2.0),
                List.of("液壓系統撐起一切")));

        nodes.put(TalentSlot.MINOR_UP2, new TalentNode(
                Skill.ENGINEERING, TalentSlot.MINOR_UP2,
                "熱力管控", "每級 +2 防禦",
                Material.FURNACE,
                Map.of(Stat.DEFENSE, 2.0),
                List.of("熱情燃燒不熄")));

        nodes.put(TalentSlot.NOTABLE_TL, new TalentNode(
                Skill.ENGINEERING, TalentSlot.NOTABLE_TL,
                "動力源泉", "一次性 +12 防禦",
                Material.DIAMOND_BLOCK,
                Map.of(Stat.DEFENSE, 12.0),
                List.of("核心能量穩定輸出")));

        nodes.put(TalentSlot.NOTABLE_TR, new TalentNode(
                Skill.ENGINEERING, TalentSlot.NOTABLE_TR,
                "奈米裝甲", "一次性 +15 最大生命、+5 防禦",
                Material.NETHERITE_BLOCK,
                Map.of(Stat.MAX_HEALTH, 15.0, Stat.DEFENSE, 5.0),
                List.of("微小機械巡迴維護")));

        nodes.put(TalentSlot.KEYSTONE_LRL, new TalentNode(
                Skill.ENGINEERING, TalentSlot.KEYSTONE_LRL,
                "機械傀儡 ✦", "解鎖主動：召喚 30s 戰鬥機器人",
                Material.IRON_GOLEM_SPAWN_EGG,
                Map.of(Stat.DEFENSE, 5.0),
                List.of("▸ 輸入 右·蹲右·右 施放", "▸ 120 秒冷卻 / 消耗 80 魔力")));

        nodes.put(TalentSlot.KEYSTONE_RLR, new TalentNode(
                Skill.ENGINEERING, TalentSlot.KEYSTONE_RLR,
                "過載核心 ✦", "解鎖主動：科技機器 ×3 速 45s",
                Material.CONDUIT,
                Map.of(Stat.EFFICIENCY, 8.0),
                List.of("▸ 輸入 蹲右·右·蹲右 施放", "▸ 240 秒冷卻 / 消耗 100 魔力")));

        return new TalentTree(Skill.ENGINEERING, "工程", "鋼鐵工匠", nodes);
    }

    // ═══════════════════════════════════════════════════════════
    //  RESEARCH — 法杖，魔法爆發
    // ═══════════════════════════════════════════════════════════
    private static TalentTree buildResearchTree() {
        final Map<TalentSlot, TalentNode> nodes = new LinkedHashMap<>();

        nodes.put(TalentSlot.START, new TalentNode(
                Skill.RESEARCH, TalentSlot.START,
                "學者之心", "免費起點 — 真理的追尋",
                Material.LIME_STAINED_GLASS_PANE,
                Map.of(Stat.MAX_MANA, 20.0),
                List.of("知識即是力量")));

        nodes.put(TalentSlot.MINOR_BASE, new TalentNode(
                Skill.RESEARCH, TalentSlot.MINOR_BASE,
                "魔力精通", "每級 +10 最大魔力",
                Material.LAPIS_LAZULI,
                Map.of(Stat.MAX_MANA, 10.0),
                List.of("魔力池越深，越能容納狂潮")));

        nodes.put(TalentSlot.MINOR_LEFT_L, new TalentNode(
                Skill.RESEARCH, TalentSlot.MINOR_LEFT_L,
                "術法強化", "每級 +2 魔法強度",
                Material.BLAZE_POWDER,
                Map.of(Stat.MAGIC_POWER, 2.0),
                List.of("元素愈發暴烈")));

        nodes.put(TalentSlot.MINOR_MID, new TalentNode(
                Skill.RESEARCH, TalentSlot.MINOR_MID,
                "魔能流轉", "每級 +1.0 魔力回復",
                Material.EXPERIENCE_BOTTLE,
                Map.of(Stat.MANA_REGEN, 1.0),
                List.of("魔力如同呼吸")));

        nodes.put(TalentSlot.MINOR_RIGHT_R, new TalentNode(
                Skill.RESEARCH, TalentSlot.MINOR_RIGHT_R,
                "奧義專注", "每級 +1% 爆擊率",
                Material.GLOWSTONE_DUST,
                Map.of(Stat.CRIT_CHANCE, 1.0),
                List.of("集中精神，一擊必中")));

        nodes.put(TalentSlot.NOTABLE_LEFT, new TalentNode(
                Skill.RESEARCH, TalentSlot.NOTABLE_LEFT,
                "元素召喚", "一次性 +8 魔法強度",
                Material.BLAZE_ROD,
                Map.of(Stat.MAGIC_POWER, 8.0),
                List.of("風火雷電皆聽我令")));

        nodes.put(TalentSlot.KEYSTONE_LLR, new TalentNode(
                Skill.RESEARCH, TalentSlot.KEYSTONE_LLR,
                "元素裂變 ✦", "解鎖主動：穿透能量束",
                Material.NETHER_STAR,
                Map.of(Stat.MAGIC_POWER, 5.0),
                List.of("▸ 輸入 右·右·蹲右 施放", "▸ 30 秒冷卻 / 消耗 60 魔力")));

        nodes.put(TalentSlot.KEYSTONE_RRL, new TalentNode(
                Skill.RESEARCH, TalentSlot.KEYSTONE_RRL,
                "量子複製 ✦", "解鎖主動：下次合成 ×2",
                Material.END_CRYSTAL,
                Map.of(Stat.MAGIC_POWER, 5.0),
                List.of("▸ 輸入 蹲右·蹲右·右 施放", "▸ 180 秒冷卻 / 消耗 80 魔力")));

        nodes.put(TalentSlot.NOTABLE_MID, new TalentNode(
                Skill.RESEARCH, TalentSlot.NOTABLE_MID,
                "秘術專精", "一次性 +10 魔法強度、+3 魔力回復",
                Material.BOOK,
                Map.of(Stat.MAGIC_POWER, 10.0, Stat.MANA_REGEN, 3.0),
                List.of("萬卷書中自有乾坤")));

        nodes.put(TalentSlot.MINOR_UP1, new TalentNode(
                Skill.RESEARCH, TalentSlot.MINOR_UP1,
                "法能回流", "每級 +15 最大魔力",
                Material.AMETHYST_SHARD,
                Map.of(Stat.MAX_MANA, 15.0),
                List.of("魔力無盡的容器")));

        nodes.put(TalentSlot.MINOR_UP2, new TalentNode(
                Skill.RESEARCH, TalentSlot.MINOR_UP2,
                "奧秘洞察", "每級 +2 魔法強度",
                Material.SPYGLASS,
                Map.of(Stat.MAGIC_POWER, 2.0),
                List.of("看穿萬物本質")));

        nodes.put(TalentSlot.NOTABLE_TL, new TalentNode(
                Skill.RESEARCH, TalentSlot.NOTABLE_TL,
                "烈焰核心", "一次性 +15 魔法強度",
                Material.FIRE_CHARGE,
                Map.of(Stat.MAGIC_POWER, 15.0),
                List.of("心中烈火永不熄滅")));

        nodes.put(TalentSlot.NOTABLE_TR, new TalentNode(
                Skill.RESEARCH, TalentSlot.NOTABLE_TR,
                "冰霜結界", "一次性 +10 魔法強度、+10 防禦",
                Material.PACKED_ICE,
                Map.of(Stat.MAGIC_POWER, 10.0, Stat.DEFENSE, 10.0),
                List.of("冰冷的理性屏障")));

        nodes.put(TalentSlot.KEYSTONE_LRL, new TalentNode(
                Skill.RESEARCH, TalentSlot.KEYSTONE_LRL,
                "隕石術 ✦", "解鎖主動：視線召喚隕石",
                Material.FIRE_CHARGE,
                Map.of(Stat.MAGIC_POWER, 10.0),
                List.of("▸ 輸入 右·蹲右·右 施放", "▸ 45 秒冷卻 / 消耗 100 魔力")));

        nodes.put(TalentSlot.KEYSTONE_RLR, new TalentNode(
                Skill.RESEARCH, TalentSlot.KEYSTONE_RLR,
                "時間凍結 ✦", "解鎖主動：15 格敵人凍結 5s",
                Material.CLOCK,
                Map.of(Stat.MAGIC_POWER, 8.0),
                List.of("▸ 輸入 蹲右·右·蹲右 施放", "▸ 90 秒冷卻 / 消耗 80 魔力")));

        return new TalentTree(Skill.RESEARCH, "研究", "奧秘的學者", nodes);
    }

    // ═══════════════════════════════════════════════════════════
    //  RESONANCE — 共鳴器，治療 + 支援
    // ═══════════════════════════════════════════════════════════
    private static TalentTree buildResonanceTree() {
        final Map<TalentSlot, TalentNode> nodes = new LinkedHashMap<>();

        nodes.put(TalentSlot.START, new TalentNode(
                Skill.RESONANCE, TalentSlot.START,
                "共鳴之心", "免費起點 — 萬物同頻",
                Material.LIME_STAINED_GLASS_PANE,
                Map.of(Stat.HEALING_POWER, 2.0),
                List.of("我所感，萬物皆感")));

        nodes.put(TalentSlot.MINOR_BASE, new TalentNode(
                Skill.RESONANCE, TalentSlot.MINOR_BASE,
                "療癒之力", "每級 +1 治療強度",
                Material.GLISTERING_MELON_SLICE,
                Map.of(Stat.HEALING_POWER, 1.0),
                List.of("每滴水都能治癒")));

        nodes.put(TalentSlot.MINOR_LEFT_L, new TalentNode(
                Skill.RESONANCE, TalentSlot.MINOR_LEFT_L,
                "魔力充盈", "每級 +8 最大魔力",
                Material.AMETHYST_SHARD,
                Map.of(Stat.MAX_MANA, 8.0),
                List.of("共鳴中魔力自行盈滿")));

        nodes.put(TalentSlot.MINOR_MID, new TalentNode(
                Skill.RESONANCE, TalentSlot.MINOR_MID,
                "生命回流", "每級 +1.0 魔力回復",
                Material.GHAST_TEAR,
                Map.of(Stat.MANA_REGEN, 1.0),
                List.of("呼吸間魔力如泉湧")));

        nodes.put(TalentSlot.MINOR_RIGHT_R, new TalentNode(
                Skill.RESONANCE, TalentSlot.MINOR_RIGHT_R,
                "同頻共振", "每級 +1 治療強度",
                Material.PINK_PETALS,
                Map.of(Stat.HEALING_POWER, 1.0),
                List.of("頻率對上，療愈自來")));

        nodes.put(TalentSlot.NOTABLE_LEFT, new TalentNode(
                Skill.RESONANCE, TalentSlot.NOTABLE_LEFT,
                "神聖光環", "一次性 +8 治療強度、+5 魔力回復",
                Material.BEACON,
                Map.of(Stat.HEALING_POWER, 8.0, Stat.MANA_REGEN, 5.0),
                List.of("光芒四射的救贖")));

        nodes.put(TalentSlot.KEYSTONE_LLR, new TalentNode(
                Skill.RESONANCE, TalentSlot.KEYSTONE_LLR,
                "力量共鳴 ✦", "解鎖主動：隊友 +30% 傷 30s",
                Material.GOLD_BLOCK,
                Map.of(Stat.HEALING_POWER, 5.0),
                List.of("▸ 輸入 右·右·蹲右 施放", "▸ 120 秒冷卻 / 消耗 90 魔力")));

        nodes.put(TalentSlot.KEYSTONE_RRL, new TalentNode(
                Skill.RESONANCE, TalentSlot.KEYSTONE_RRL,
                "傳送信標 ✦", "解鎖主動：標記並召集隊友",
                Material.ENDER_EYE,
                Map.of(Stat.HEALING_POWER, 5.0),
                List.of("▸ 輸入 蹲右·蹲右·右 施放", "▸ 300 秒冷卻 / 消耗 120 魔力")));

        nodes.put(TalentSlot.NOTABLE_MID, new TalentNode(
                Skill.RESONANCE, TalentSlot.NOTABLE_MID,
                "共鳴網絡", "一次性 +10 治療強度、+5 魔法強度",
                Material.AMETHYST_CLUSTER,
                Map.of(Stat.HEALING_POWER, 10.0, Stat.MAGIC_POWER, 5.0),
                List.of("連結每個生靈的感應")));

        nodes.put(TalentSlot.MINOR_UP1, new TalentNode(
                Skill.RESONANCE, TalentSlot.MINOR_UP1,
                "精神防護", "每級 +1 防禦、+1 治療強度",
                Material.SHIELD,
                Map.of(Stat.DEFENSE, 1.0, Stat.HEALING_POWER, 1.0),
                List.of("心靈的護盾")));

        nodes.put(TalentSlot.MINOR_UP2, new TalentNode(
                Skill.RESONANCE, TalentSlot.MINOR_UP2,
                "靈魂共振", "每級 +2 治療強度",
                Material.SOUL_LANTERN,
                Map.of(Stat.HEALING_POWER, 2.0),
                List.of("靈魂層面的連結")));

        nodes.put(TalentSlot.NOTABLE_TL, new TalentNode(
                Skill.RESONANCE, TalentSlot.NOTABLE_TL,
                "慈悲之主", "一次性 +15 治療強度",
                Material.TOTEM_OF_UNDYING,
                Map.of(Stat.HEALING_POWER, 15.0),
                List.of("毫無保留地付出")));

        nodes.put(TalentSlot.NOTABLE_TR, new TalentNode(
                Skill.RESONANCE, TalentSlot.NOTABLE_TR,
                "守護真言", "一次性 +10 治療強度、+8 防禦",
                Material.SHIELD,
                Map.of(Stat.HEALING_POWER, 10.0, Stat.DEFENSE, 8.0),
                List.of("言語本身即為庇護")));

        nodes.put(TalentSlot.KEYSTONE_LRL, new TalentNode(
                Skill.RESONANCE, TalentSlot.KEYSTONE_LRL,
                "治癒波動 ✦", "解鎖主動：15 格 AoE 回 60% HP",
                Material.GOLDEN_APPLE,
                Map.of(Stat.HEALING_POWER, 10.0),
                List.of("▸ 輸入 右·蹲右·右 施放", "▸ 60 秒冷卻 / 消耗 80 魔力")));

        nodes.put(TalentSlot.KEYSTONE_RLR, new TalentNode(
                Skill.RESONANCE, TalentSlot.KEYSTONE_RLR,
                "共振護盾 ✦", "解鎖主動：50% 吸收護盾 20s",
                Material.SHIELD,
                Map.of(Stat.HEALING_POWER, 5.0, Stat.DEFENSE, 5.0),
                List.of("▸ 輸入 蹲右·右·蹲右 施放", "▸ 90 秒冷卻 / 消耗 70 魔力")));

        return new TalentTree(Skill.RESONANCE, "共鳴", "萬物的織者", nodes);
    }
}
