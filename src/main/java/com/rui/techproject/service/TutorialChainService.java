package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.util.ItemFactoryUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 新手引導鏈 — 13 個階段，使用 PlayerProgressService stats 記錄進度。
 *
 * <p>每完成一個階段，給予科技經驗 + 技能經驗獎勵。
 * 狀態儲存在 stats key "tutorial_stage"（0=未開始，13=全部完成）。
 */
public final class TutorialChainService {

    private static final String STAGE_KEY = "tutorial_stage";
    private static final int TOTAL_STAGES = Stage.values().length;

    // ── 文字色彩 ──
    private static final TextColor C_GOLD     = TextColor.color(0xFFD700);
    private static final TextColor C_AMBER    = TextColor.color(0xF59E0B);
    private static final TextColor C_EMERALD  = TextColor.color(0x34D399);
    private static final TextColor C_SKY      = TextColor.color(0x38BDF8);
    private static final TextColor C_LAVENDER = TextColor.color(0xA78BFA);

    private final TechMCPlugin plugin;
    private final PlayerProgressService progressService;
    private final SkillService skillService;
    private final ItemFactoryUtil itemFactory;

    public TutorialChainService(final TechMCPlugin plugin,
                                  final PlayerProgressService progressService,
                                  final SkillService skillService,
                                  final ItemFactoryUtil itemFactory) {
        this.plugin = plugin;
        this.progressService = progressService;
        this.skillService = skillService;
        this.itemFactory = itemFactory;
    }

    // ═══════════════════════════════════════════
    //  查詢 API
    // ═══════════════════════════════════════════

    public int getStage(final UUID uuid) {
        return (int) this.progressService.getStat(uuid, STAGE_KEY);
    }

    public Stage getCurrentStage(final UUID uuid) {
        final int idx = this.getStage(uuid);
        if (idx >= TOTAL_STAGES) return null;  // 已全部完成
        return Stage.values()[idx];
    }

    public boolean isComplete(final UUID uuid) {
        return this.getStage(uuid) >= TOTAL_STAGES;
    }

    // ═══════════════════════════════════════════
    //  觸發入口（由各 Service 呼叫）
    // ═══════════════════════════════════════════

    /** 新玩家加入時發送引導訊息。 */
    public void onPlayerJoin(final Player player) {
        final int stage = this.getStage(player.getUniqueId());
        if (stage >= TOTAL_STAGES) return;  // 已完成
        if (stage == 0) {
            this.showStageHint(player, Stage.values()[0]);
        } else {
            this.showStageHint(player, Stage.values()[stage]);
        }
    }

    /** 打開科技書時。 */
    public void onBookOpen(final Player player) {
        this.tryAdvance(player, Stage.OPEN_BOOK);
    }

    /** 放置科技機器時。 */
    public void onMachinePlace(final Player player) {
        this.tryAdvance(player, Stage.PLACE_MACHINE);
        // BUILD_5_MACHINES 也靠此觸發
        this.tryAdvance(player, Stage.BUILD_5_MACHINES);
    }

    /** 合成科技物品時。 */
    public void onTechCraft(final Player player) {
        this.tryAdvance(player, Stage.FIRST_CRAFT);
    }

    /** 用扳手開關機時。 */
    public void onWrenchToggle(final Player player) {
        this.tryAdvance(player, Stage.WRENCH_TOGGLE);
    }

    /** 解鎖科技研究時。 */
    public void onResearchUnlock(final Player player) {
        this.tryAdvance(player, Stage.UNLOCK_RESEARCH);
    }

    /** 進入星球時。 */
    public void onPlanetEnter(final Player player) {
        this.tryAdvance(player, Stage.ENTER_PLANET);
    }

    /** 星球採集時。 */
    public void onPlanetHarvest(final Player player) {
        this.tryAdvance(player, Stage.PLANET_HARVEST);
    }

    /** 技能升級時。 */
    public void onSkillLevelUp(final Player player) {
        this.tryAdvance(player, Stage.SKILL_LEVEL_UP);
    }

    /** 查看每日任務時。 */
    public void onDailyQuestOpen(final Player player) {
        this.tryAdvance(player, Stage.CHECK_DAILY_QUEST);
    }

    /** 施放天賦技能時。 */
    public void onSpellCast(final Player player) {
        this.tryAdvance(player, Stage.CAST_SPELL);
    }

    /** 進入迷宮時（離開 Glade 進入迷宮區域）。 */
    public void onMazeEnter(final Player player) {
        this.tryAdvance(player, Stage.ENTER_MAZE);
    }

    /** 完成迷宮任務時。 */
    public void onMazeQuestComplete(final Player player) {
        this.tryAdvance(player, Stage.MAZE_QUEST);
    }

    // ═══════════════════════════════════════════
    //  核心邏輯
    // ═══════════════════════════════════════════

    private void tryAdvance(final Player player, final Stage target) {
        if (player == null) return;
        final UUID uuid = player.getUniqueId();
        final int currentIdx = this.getStage(uuid);
        if (currentIdx != target.ordinal()) return;  // 順序必須正確

        // 有額外條件的階段：檢查是否達標
        if (!this.meetsCondition(uuid, target)) return;

        // 推進
        this.progressService.setStat(uuid, STAGE_KEY, currentIdx + 1);

        // ── 獎勵 ──
        this.progressService.addTechXp(uuid, target.techXpReward);
        if (target.skillId != null && target.skillXpReward > 0L) {
            this.skillService.grantXp(player, target.skillId, target.skillXpReward);
        }

        // ── 完成訊息 ──
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  ✦ ", C_GOLD)
                .append(Component.text("引導完成", C_EMERALD, TextDecoration.BOLD))
                .append(Component.text(" — ", NamedTextColor.DARK_GRAY))
                .append(Component.text(target.displayName, NamedTextColor.WHITE)));

        // 獎勵明細
        final StringBuilder rewardLine = new StringBuilder("    獎勵：");
        rewardLine.append("+").append(target.techXpReward).append(" 科技經驗");
        if (target.skillId != null && target.skillXpReward > 0L) {
            rewardLine.append(", +").append(target.skillXpReward).append(" ")
                    .append(this.skillDisplayName(target.skillId)).append("經驗");
        }
        player.sendMessage(Component.text(rewardLine.toString(), C_AMBER));
        player.sendMessage(Component.empty());

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP,
                SoundCategory.PLAYERS, 0.7f, 1.5f);
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME,
                SoundCategory.PLAYERS, 0.6f, 1.8f);

        // ── 下一階段提示 ──
        if (currentIdx + 1 < TOTAL_STAGES) {
            final Stage next = Stage.values()[currentIdx + 1];
            this.plugin.getSafeScheduler().runGlobalDelayed(task -> {
                if (player.isOnline()) this.showStageHint(player, next);
            }, 60L);
        } else {
            // 全部完成！
            this.onAllComplete(player);
        }
    }

    private void onAllComplete(final Player player) {
        final UUID uuid = player.getUniqueId();
        // 完成獎勵
        this.progressService.addTechXp(uuid, 1000L);
        for (final SkillService.Skill s : SkillService.Skill.values()) {
            this.skillService.grantXp(player, s.id, 300L);
        }

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  ★ ", C_GOLD)
                .append(Component.text("新手引導全部完成！", C_GOLD, TextDecoration.BOLD)));
        player.sendMessage(Component.text("    完成獎勵：+1000 科技經驗, 全技能 +300 經驗",
                C_AMBER));
        player.sendMessage(Component.text("    科技世界的大門已為你敞開，盡情探索吧！",
                C_LAVENDER));
        player.sendMessage(Component.empty());

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * 檢查有額外門檻的階段是否達標。
     */
    private boolean meetsCondition(final UUID uuid, final Stage stage) {
        return switch (stage) {
            case BUILD_5_MACHINES ->
                    this.progressService.getStat(uuid, "machines_placed") >= 5L;
            default -> true;
        };
    }

    // ═══════════════════════════════════════════
    //  顯示提示
    // ═══════════════════════════════════════════

    public void showStageHint(final Player player, final Stage stage) {
        if (stage == null) return;
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  ▶ ", C_SKY)
                .append(Component.text("科技引導", C_SKY, TextDecoration.BOLD))
                .append(Component.text(" [" + (stage.ordinal() + 1) + "/" + TOTAL_STAGES + "]",
                        NamedTextColor.GRAY)));
        player.sendMessage(Component.text("    " + stage.displayName,
                NamedTextColor.WHITE, TextDecoration.BOLD));
        player.sendMessage(Component.text("    " + stage.hint, NamedTextColor.GRAY));
        player.sendMessage(Component.empty());
    }

    private String skillDisplayName(final String skillId) {
        final SkillService.Skill s = SkillService.Skill.byId(skillId);
        return s != null ? s.displayName : skillId;
    }

    // ═══════════════════════════════════════════
    //  12 個引導階段
    // ═══════════════════════════════════════════

    public enum Stage {
        /** 0 — 打開科技書 */
        OPEN_BOOK(
                "打開科技書",
                "輸入 /tech book 或手持科技書右鍵，開啟你的科技指南",
                50L, "research", 50L),

        /** 1 — 放置第一台機器 */
        PLACE_MACHINE(
                "放置科技機器",
                "從科技書中查找機器，將它放置在地上開始自動化之旅",
                100L, "engineering", 100L),

        /** 2 — 合成第一個科技物品 */
        FIRST_CRAFT(
                "合成科技物品",
                "將材料放入機器，等待產出你的第一個科技產物",
                100L, "engineering", 100L),

        /** 3 — 使用扳手開關機省電 */
        WRENCH_TOGGLE(
                "扳手開關機省電",
                "手持科技扳手，左鍵對準運行中的機器可暫停它；不用的機器記得關機，節省電力",
                80L, "engineering", 80L),

        /** 4 — 解鎖第一項研究 */
        UNLOCK_RESEARCH(
                "解鎖科技研究",
                "建造研究台，花費研究點解鎖一項新科技",
                150L, "research", 150L),

        /** 4 — 進入星球 */
        ENTER_PLANET(
                "探索星球",
                "在主世界找到迷途星傳送門，前往任一星球探索",
                200L, "exploration", 200L),

        /** 5 — 星球採集 */
        PLANET_HARVEST(
                "星球採集",
                "在星球表面採集任何資源節點",
                200L, "gathering", 200L),

        /** 6 — 技能升級 */
        SKILL_LEVEL_UP(
                "技能升級",
                "透過各種活動累積經驗，讓任意技能升級一次",
                200L, "resonance", 200L),

        /** 7 — 查看每日任務 */
        CHECK_DAILY_QUEST(
                "查看每日任務",
                "使用 /tech quest 開啟任務面板，了解每日與每週挑戰",
                150L, "exploration", 100L),

        /** 8 — 累計放置 5 台機器 */
        BUILD_5_MACHINES(
                "建造 5 台機器",
                "持續擴展你的科技基地，累計放置 5 台科技機器",
                300L, "engineering", 300L),

        /** 9 — 施放天賦技能 */
        CAST_SPELL(
                "施放天賦技能",
                "裝備法器武器（可在組裝機合成），輸入技能組合鍵施放天賦技能",
                300L, "combat", 300L),

        /** 10 — 進入迷宮 */
        ENTER_MAZE(
                "進入迷宮",
                "從迷途星中心走出安全區，踏入未知的迷宮領域",
                300L, "exploration", 300L),

        /** 11 — 完成迷宮任務 */
        MAZE_QUEST(
                "完成迷宮任務",
                "在迷宮中接取並完成一個任務，證明你的實力",
                500L, "resonance", 500L);

        public final String displayName;
        public final String hint;
        public final long techXpReward;
        public final String skillId;
        public final long skillXpReward;

        Stage(final String displayName, final String hint,
              final long techXpReward, final String skillId, final long skillXpReward) {
            this.displayName = displayName;
            this.hint = hint;
            this.techXpReward = techXpReward;
            this.skillId = skillId;
            this.skillXpReward = skillXpReward;
        }
    }
}
