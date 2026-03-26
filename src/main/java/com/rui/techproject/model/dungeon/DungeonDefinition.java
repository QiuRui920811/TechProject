package com.rui.techproject.model.dungeon;

import java.util.List;
import java.util.Map;

/**
 * 副本靜態定義 — 從 YAML 載入，不可變。
 *
 * @param id               唯一 ID（例如 "lost_factory"）
 * @param displayName      顯示名稱
 * @param description      副本描述
 * @param templateWorld    模板世界資料夾名稱
 * @param minPlayers       最少玩家人數
 * @param maxPlayers       最多玩家人數
 * @param timeLimitSeconds 限時秒數（0=無限時）
 * @param cooldownSeconds  冷卻秒數（0=無冷卻）
 * @param dailyLimit       每日進入上限（0=無限制）
 * @param spawnPoint       玩家進入後的初始相對座標 [x, y, z, yaw, pitch]
 * @param exitPoint        離開後傳送的座標（若 null 則送回進入前位置）
 * @param waves            波次定義列表
 * @param bosses           Boss 定義列表
 * @param rewards          通關獎勵列表
 * @param scripts          腳本事件列表
 * @param requiredPermission 進入所需權限（null=不需要）
 * @param techThemed       是否與科技系統整合
 * @param category         副本分類
 */
public record DungeonDefinition(
        String id,
        String displayName,
        String description,
        String templateWorld,
        int minPlayers,
        int maxPlayers,
        int timeLimitSeconds,
        int cooldownSeconds,
        int dailyLimit,
        double[] spawnPoint,
        double[] exitPoint,
        List<WaveDefinition> waves,
        List<BossDefinition> bosses,
        List<RewardDefinition> rewards,
        List<ScriptDefinition> scripts,
        String requiredPermission,
        boolean techThemed,
        String category
) {
    /**
     * 波次定義。
     *
     * @param waveIndex   波次序號 (0-based)
     * @param mobEntries  本波要生成的怪物列表
     * @param spawnDelay  開始生怪前的延遲（秒）
     * @param message     波次開始時的提示訊息
     * @param requireClear 是否要求清完所有上一波才推進
     */
    public record WaveDefinition(
            int waveIndex,
            List<MobEntry> mobEntries,
            int spawnDelay,
            String message,
            boolean requireClear
    ) {}

    /**
     * 波次中的單一怪物條目。
     *
     * @param entityType  原版 EntityType 名稱
     * @param mythicMobId MythicMobs 的 mob ID（若有，優先使用）
     * @param count       生成數量
     * @param level       等級（影響血量/傷害加成）
     * @param spawnOffset 相對波次起點的偏移 [x, y, z]
     * @param customName  自訂名稱（null=使用預設）
     * @param equipment   裝備設定 map
     */
    public record MobEntry(
            String entityType,
            String mythicMobId,
            int count,
            int level,
            double[] spawnOffset,
            String customName,
            Map<String, String> equipment
    ) {}

    /**
     * Boss 定義。
     *
     * @param id          Boss 唯一 ID
     * @param displayName 顯示名稱
     * @param entityType  原版 EntityType
     * @param mythicMobId MythicMobs 的 mob ID
     * @param health      生命值
     * @param damage      傷害
     * @param spawnPoint  生成座標偏移
     * @param skills      技能列表
     * @param phases      階段定義（血量閾值 → 行為變化）
     * @param afterWave   在第幾波結束後出現（-1=獨立觸發）
     * @param bossBar     是否顯示 BossBar
     * @param lootTable   Boss 專屬掉落表
     */
    public record BossDefinition(
            String id,
            String displayName,
            String entityType,
            String mythicMobId,
            double health,
            double damage,
            double[] spawnPoint,
            List<BossSkill> skills,
            List<BossPhase> phases,
            int afterWave,
            boolean bossBar,
            List<String> lootTable
    ) {}

    /** Boss 技能。 */
    public record BossSkill(
            String id,
            String type,
            int cooldownTicks,
            double radius,
            double damage,
            String message,
            Map<String, Object> params
    ) {}

    /** Boss 階段：當 Boss 血量比例 <= threshold 時觸發。 */
    public record BossPhase(
            double healthThreshold,
            String message,
            double damageMultiplier,
            double speedMultiplier,
            List<MobEntry> reinforcements,
            List<String> activateSkills
    ) {}

    /**
     * 通關獎勵。
     *
     * @param type     獎勵類型：item / command / tech_material / tech_blueprint / money / exp
     * @param value    值（物品ID / 指令 / 金額等）
     * @param amount   數量
     * @param chance   掉落機率 0.0~1.0
     * @param firstClearOnly 是否僅首次通關才給
     */
    public record RewardDefinition(
            String type,
            String value,
            int amount,
            double chance,
            boolean firstClearOnly
    ) {}

    /**
     * 腳本事件定義 — 副本中的互動 / 機關 / 觸發器。
     *
     * @param id         唯一 ID
     * @param trigger    觸發類型：enter_region / kill_all / interact_block / chat_password /
     *                             timer / boss_death / wave_clear / player_death / function_call
     * @param conditions 觸發條件列表
     * @param actions    觸發後執行的動作列表
     * @param oneShot    是否只觸發一次
     * @param delayTicks 延遲幾 tick 後執行動作
     */
    public record ScriptDefinition(
            String id,
            String trigger,
            List<ScriptCondition> conditions,
            List<ScriptAction> actions,
            boolean oneShot,
            int delayTicks
    ) {}

    /** 腳本條件。 */
    public record ScriptCondition(
            String type,
            Map<String, Object> params
    ) {}

    /**
     * 腳本動作。
     *
     * @param type   動作類型：message / title / sound / teleport / spawn_mob /
     *                         set_block / open_door / give_item / run_command /
     *                         start_wave / spawn_boss / complete_dungeon /
     *                         fail_dungeon / set_variable / check_variable /
     *                         particle / explosion / function
     * @param params 動作參數
     */
    public record ScriptAction(
            String type,
            Map<String, Object> params
    ) {}
}
