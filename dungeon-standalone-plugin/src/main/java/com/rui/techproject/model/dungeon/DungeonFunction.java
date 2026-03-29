package com.rui.techproject.model.dungeon;

import org.bukkit.Material;
import org.bukkit.Particle;

import java.util.*;

/**
 * 副本功能（Function）— 對應 MythicDungeons 的方塊綁定功能系統。
 * 每個 Function 綁定在地圖中的一個方塊位置，由 Trigger 觸發、受 Condition 約束。
 *
 * @param id              唯一 ID（自動產生或使用者指定）
 * @param functionType    功能類型
 * @param blockX          方塊座標 X
 * @param blockY          方塊座標 Y
 * @param blockZ          方塊座標 Z
 * @param triggerType     觸發器類型
 * @param triggerOptions  觸發器選項
 * @param conditions      觸發條件列表
 * @param functionOptions 功能選項
 * @param targetType      目標類型
 * @param allowRetrigger  是否允許重複觸發
 */
public record DungeonFunction(
        String id,
        FunctionType functionType,
        int blockX, int blockY, int blockZ,
        TriggerType triggerType,
        Map<String, Object> triggerOptions,
        List<FunctionCondition> conditions,
        Map<String, Object> functionOptions,
        TargetType targetType,
        boolean allowRetrigger
) {

    /** 取得此功能在世界中的方塊位置描述。 */
    public String locationString() {
        return this.blockX + ", " + this.blockY + ", " + this.blockZ;
    }

    // ══════════════════════════════════════════════════
    //  目標類型
    // ══════════════════════════════════════════════════

    public enum TargetType {
        PLAYER("§a玩家", "觸發此功能的單一玩家", Material.PLAYER_HEAD),
        PARTY("§b隊伍", "目標為隊伍中所有玩家", Material.TOTEM_OF_UNDYING),
        NONE("§7無", "不需要目標", Material.BARRIER);

        private final String display;
        private final String description;
        private final Material icon;

        TargetType(String display, String description, Material icon) {
            this.display = display;
            this.description = description;
            this.icon = icon;
        }

        public String display() { return this.display; }
        public String description() { return this.description; }
        public Material icon() { return this.icon; }

        public TargetType next() {
            final TargetType[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }
    }

    // ══════════════════════════════════════════════════
    //  功能分類
    // ══════════════════════════════════════════════════

    public enum FunctionCategory {
        DUNGEON("§6副本", "副本核心功能", Material.NETHER_STAR, Particle.FLAME),
        PLAYER("§a玩家", "影響玩家的功能", Material.PLAYER_HEAD, Particle.HAPPY_VILLAGER),
        LOCATION("§b位置", "影響特定位置的功能", Material.COMPASS, Particle.ENCHANT),
        META("§d進階", "進階組合與邏輯功能", Material.COMMAND_BLOCK, Particle.WITCH);

        private final String display;
        private final String description;
        private final Material icon;
        private final Particle particle;

        FunctionCategory(String display, String description, Material icon, Particle particle) {
            this.display = display;
            this.description = description;
            this.icon = icon;
            this.particle = particle;
        }

        public String display() { return this.display; }
        public String description() { return this.description; }
        public Material icon() { return this.icon; }
        public Particle particle() { return this.particle; }
    }

    // ══════════════════════════════════════════════════
    //  功能類型（30 種，完全對應 MythicDungeons）
    // ══════════════════════════════════════════════════

    public enum FunctionType {
        // ── 副本分類 ──
        START_DUNGEON(FunctionCategory.DUNGEON, Material.LIME_DYE,
                "§a開始副本", "啟動副本。若使用大廳則此功能為必要！",
                new String[]{}, new TargetType[]{TargetType.NONE}),
        FINISH_DUNGEON(FunctionCategory.DUNGEON, Material.GOLDEN_APPLE,
                "§6完成副本", "正式完成副本並發放獎勵。",
                new String[]{}, new TargetType[]{TargetType.PLAYER, TargetType.PARTY}),
        LEAVE_DUNGEON(FunctionCategory.DUNGEON, Material.IRON_DOOR,
                "§c離開副本", "將玩家送出副本（不算通關）。",
                new String[]{}, new TargetType[]{TargetType.PLAYER, TargetType.PARTY}),
        CHECKPOINT(FunctionCategory.DUNGEON, Material.RESPAWN_ANCHOR,
                "§e檢查點", "設定玩家復活/傳送回歸點。",
                new String[]{}, new TargetType[]{TargetType.PLAYER, TargetType.PARTY}),
        SIGNAL_SENDER(FunctionCategory.DUNGEON, Material.SCULK_SENSOR,
                "§d信號發送器", "向所有「信號接收器」觸發器廣播信號。",
                new String[]{"signal-name"}, new TargetType[]{TargetType.NONE}),
        LIVES_EDITOR(FunctionCategory.DUNGEON, Material.GOLDEN_APPLE,
                "§c生命編輯器", "修改玩家的剩餘生命次數。",
                new String[]{"action", "amount"}, new TargetType[]{TargetType.PLAYER, TargetType.PARTY}),
        REVIVER(FunctionCategory.DUNGEON, Material.TOTEM_OF_UNDYING,
                "§a復活器", "讓已死亡的隊友復活。",
                new String[]{}, new TargetType[]{TargetType.PLAYER, TargetType.PARTY}),
        CHUNK_LOADER(FunctionCategory.DUNGEON, Material.LODESTONE,
                "§b區塊載入器", "強制保持此功能所在區塊載入。再觸發一次則取消。",
                new String[]{}, new TargetType[]{TargetType.NONE}),
        DUNGEON_STATUS(FunctionCategory.DUNGEON, Material.OAK_SIGN,
                "§f副本狀態", "設定副本的「狀態」文字（類似 MythicMobs 的 stance）。",
                new String[]{"status-text"}, new TargetType[]{TargetType.NONE}),
        DIFFICULTY(FunctionCategory.DUNGEON, Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                "§4難度設定", "即時更改副本難度等級。",
                new String[]{"difficulty-level"}, new TargetType[]{TargetType.NONE}),

        // ── 玩家分類 ──
        MESSAGE_SENDER(FunctionCategory.PLAYER, Material.WRITABLE_BOOK,
                "§e訊息發送", "向玩家發送訊息。",
                new String[]{"message"}, new TargetType[]{TargetType.PLAYER, TargetType.PARTY}),
        TELEPORTER(FunctionCategory.PLAYER, Material.ENDER_PEARL,
                "§d傳送器", "將玩家傳送至指定位置。",
                new String[]{"dest-x", "dest-y", "dest-z", "dest-yaw", "dest-pitch"}, new TargetType[]{TargetType.PLAYER, TargetType.PARTY}),
        TITLE_SENDER(FunctionCategory.PLAYER, Material.NAME_TAG,
                "§6標題發送", "向玩家顯示標題訊息（大字+副標）。",
                new String[]{"title", "subtitle", "fade-in", "stay", "fade-out"}, new TargetType[]{TargetType.PLAYER, TargetType.PARTY}),
        ITEM_DISPENSER(FunctionCategory.PLAYER, Material.DROPPER,
                "§b物品發放器", "給予或掉落物品。",
                new String[]{"item-type", "amount", "custom-name", "drop-at-location"}, new TargetType[]{TargetType.PLAYER, TargetType.PARTY}),
        KEY_DISPENSER(FunctionCategory.PLAYER, Material.TRIPWIRE_HOOK,
                "§e鑰匙發放器", "給予隊伍鑰匙物品（優先給隊長）。",
                new String[]{"key-material", "key-name"}, new TargetType[]{TargetType.PLAYER, TargetType.PARTY}),
        REWARDS(FunctionCategory.PLAYER, Material.CHEST,
                "§e獎勵箱", "打開獎勵物品欄讓玩家拿取。",
                new String[]{"reward-items"}, new TargetType[]{TargetType.PLAYER}),
        RANDOM_REWARDS(FunctionCategory.PLAYER, Material.ENDER_CHEST,
                "§d隨機獎勵", "打開隨機內容的獎勵物品欄。",
                new String[]{"min-items", "max-items", "reward-pool"}, new TargetType[]{TargetType.PLAYER}),
        LOOT_TABLE_REWARDS(FunctionCategory.PLAYER, Material.BARREL,
                "§6戰利品表獎勵", "根據戰利品表隨機獎勵。",
                new String[]{"loot-table"}, new TargetType[]{TargetType.PLAYER}),

        // ── 位置分類 ──
        MOB_SPAWNER(FunctionCategory.LOCATION, Material.SPAWNER,
                "§c怪物生成器", "在此位置生成配置的怪物。",
                new String[]{"mob-type", "mythic-mob-id", "count", "level", "custom-name", "spawn-radius"},
                new TargetType[]{TargetType.NONE}),
        NPC_SPAWNER(FunctionCategory.LOCATION, Material.VILLAGER_SPAWN_EGG,
                "§aNPC 生成器", "在此位置生成 NPC（需要 Citizens）。",
                new String[]{"npc-name", "npc-skin"}, new TargetType[]{TargetType.NONE}),
        BLOCK_EDITOR(FunctionCategory.LOCATION, Material.DIAMOND_PICKAXE,
                "§f方塊編輯器", "放置或移除此位置的方塊。",
                new String[]{"block-type", "block-action"}, new TargetType[]{TargetType.NONE}),
        SOUND_PLAYER(FunctionCategory.LOCATION, Material.NOTE_BLOCK,
                "§b音效播放器", "在此位置播放音效。",
                new String[]{"sound-name", "volume", "pitch"}, new TargetType[]{TargetType.NONE}),
        REDSTONE_BLOCK(FunctionCategory.LOCATION, Material.REDSTONE_BLOCK,
                "§c紅石方塊", "在此位置放置紅石方塊。",
                new String[]{"duration-ticks"}, new TargetType[]{TargetType.NONE}),
        DOOR_CONTROLLER(FunctionCategory.LOCATION, Material.OAK_DOOR,
                "§6門控制器", "鎖定或解鎖此位置的門。",
                new String[]{"door-action"}, new TargetType[]{TargetType.NONE}),
        BLOCK_CONTROLLER(FunctionCategory.LOCATION, Material.BEDROCK,
                "§7方塊控制器", "控制此位置是否允許放置/破壞方塊。",
                new String[]{"allow-place", "allow-break", "block-filter"}, new TargetType[]{TargetType.NONE}),
        HOLOGRAM(FunctionCategory.LOCATION, Material.ARMOR_STAND,
                "§e全息文字", "在指定位置顯示浮動文字。",
                new String[]{"hologram-text", "duration-ticks"}, new TargetType[]{TargetType.NONE}),
        MOVING_BLOCK(FunctionCategory.LOCATION, Material.PISTON,
                "§a移動方塊", "將此位置的方塊變為可移動方塊。",
                new String[]{"dest-x", "dest-y", "dest-z", "speed-ticks", "return-after"}, new TargetType[]{TargetType.NONE}),
        MOVING_BLOCK_CLUSTER(FunctionCategory.LOCATION, Material.STICKY_PISTON,
                "§a移動方塊群組", "將一組方塊變為可移動的群組。",
                new String[]{"dest-x", "dest-y", "dest-z", "speed-ticks", "radius", "return-after"}, new TargetType[]{TargetType.NONE}),

        // ── 進階分類 ──
        MYTHIC_SKILL(FunctionCategory.META, Material.BLAZE_POWDER,
                "§5MythicMobs 技能", "執行指定的 MythicMobs 技能。",
                new String[]{"skill-name"}, new TargetType[]{TargetType.PLAYER, TargetType.PARTY, TargetType.NONE}),
        MYTHIC_SIGNAL(FunctionCategory.META, Material.ECHO_SHARD,
                "§5MythicMobs 信號", "向附近怪物發送 MythicMobs 信號。",
                new String[]{"signal-name", "signal-radius"}, new TargetType[]{TargetType.NONE}),
        COMMAND_SENDER(FunctionCategory.META, Material.COMMAND_BLOCK,
                "§6指令發送", "以玩家或控制台身分執行指令。",
                new String[]{"command", "as-console"}, new TargetType[]{TargetType.PLAYER, TargetType.PARTY}),
        MULTI_FUNCTION(FunctionCategory.META, Material.CHAIN_COMMAND_BLOCK,
                "§b多重功能", "同時執行多個功能。",
                new String[]{"function-ids"}, new TargetType[]{TargetType.NONE}),
        FUNCTION_REPEATER(FunctionCategory.META, Material.REPEATER,
                "§e功能重複器", "重複執行一個功能 N 次。",
                new String[]{"function-id", "repeat-count", "interval-ticks"}, new TargetType[]{TargetType.NONE}),
        DELAYED_FUNCTION(FunctionCategory.META, Material.CLOCK,
                "§f延遲功能", "延遲指定 tick 數後執行功能。",
                new String[]{"function-id", "delay-ticks"}, new TargetType[]{TargetType.NONE}),
        FUNCTION_RANDOMIZER(FunctionCategory.META, Material.DISPENSER,
                "§d隨機功能", "從功能清單中隨機選一個執行。",
                new String[]{"function-ids"}, new TargetType[]{TargetType.NONE}),
        FUNCTION_SEQUENCER(FunctionCategory.META, Material.COMPARATOR,
                "§a順序功能", "每次觸發依序執行下一個功能。",
                new String[]{"function-ids"}, new TargetType[]{TargetType.NONE}),
        DUNGEON_VARIABLE(FunctionCategory.META, Material.BOOK,
                "§f副本變數", "設定或修改自訂變數（可搭配變數比較條件）。",
                new String[]{"variable-name", "variable-action", "variable-value"}, new TargetType[]{TargetType.NONE});

        private final FunctionCategory category;
        private final Material icon;
        private final String display;
        private final String description;
        private final String[] optionKeys;
        private final TargetType[] allowedTargets;

        FunctionType(FunctionCategory category, Material icon, String display, String description,
                     String[] optionKeys, TargetType[] allowedTargets) {
            this.category = category;
            this.icon = icon;
            this.display = display;
            this.description = description;
            this.optionKeys = optionKeys;
            this.allowedTargets = allowedTargets;
        }

        public FunctionCategory category() { return this.category; }
        public Material icon() { return this.icon; }
        public String display() { return this.display; }
        public String description() { return this.description; }
        public String[] optionKeys() { return this.optionKeys; }
        public TargetType[] allowedTargets() { return this.allowedTargets; }

        /** 取得此功能類型的預設選項。 */
        public Map<String, Object> defaultOptions() {
            final Map<String, Object> opts = new LinkedHashMap<>();
            switch (this) {
                case START_DUNGEON, FINISH_DUNGEON, LEAVE_DUNGEON, CHECKPOINT, REVIVER, CHUNK_LOADER -> {}
                case SIGNAL_SENDER -> opts.put("signal-name", "預設信號");
                case LIVES_EDITOR -> { opts.put("action", "ADD"); opts.put("amount", 1); }
                case DUNGEON_STATUS -> opts.put("status-text", "進行中");
                case DIFFICULTY -> opts.put("difficulty-level", "普通");
                case MESSAGE_SENDER -> opts.put("message", "§e歡迎來到副本！");
                case TELEPORTER -> {
                    opts.put("dest-x", 0.0); opts.put("dest-y", 65.0); opts.put("dest-z", 0.0);
                    opts.put("dest-yaw", 0.0f); opts.put("dest-pitch", 0.0f);
                }
                case TITLE_SENDER -> {
                    opts.put("title", "§6§l副本開始"); opts.put("subtitle", "§e準備好了嗎？");
                    opts.put("fade-in", 10); opts.put("stay", 60); opts.put("fade-out", 20);
                }
                case ITEM_DISPENSER -> {
                    opts.put("item-type", "DIAMOND"); opts.put("amount", 1);
                    opts.put("custom-name", ""); opts.put("drop-at-location", false);
                }
                case KEY_DISPENSER -> { opts.put("key-material", "TRIPWIRE_HOOK"); opts.put("key-name", "§e副本鑰匙"); }
                case REWARDS -> opts.put("reward-items", List.of());
                case RANDOM_REWARDS -> { opts.put("min-items", 1); opts.put("max-items", 3); opts.put("reward-pool", List.of()); }
                case LOOT_TABLE_REWARDS -> opts.put("loot-table", "預設");
                case MOB_SPAWNER -> {
                    opts.put("mob-type", "ZOMBIE"); opts.put("mythic-mob-id", "");
                    opts.put("count", 5); opts.put("level", 1);
                    opts.put("custom-name", ""); opts.put("spawn-radius", 3.0);
                }
                case NPC_SPAWNER -> { opts.put("npc-name", "NPC"); opts.put("npc-skin", ""); }
                case BLOCK_EDITOR -> { opts.put("block-type", "STONE"); opts.put("block-action", "PLACE"); }
                case SOUND_PLAYER -> { opts.put("sound-name", "ENTITY_EXPERIENCE_ORB_PICKUP"); opts.put("volume", 1.0); opts.put("pitch", 1.0); }
                case REDSTONE_BLOCK -> opts.put("duration-ticks", 20);
                case DOOR_CONTROLLER -> opts.put("door-action", "TOGGLE");
                case BLOCK_CONTROLLER -> { opts.put("allow-place", false); opts.put("allow-break", false); opts.put("block-filter", ""); }
                case HOLOGRAM -> { opts.put("hologram-text", "§e文字顯示"); opts.put("duration-ticks", -1); }
                case MOVING_BLOCK -> {
                    opts.put("dest-x", 0); opts.put("dest-y", 0); opts.put("dest-z", 0);
                    opts.put("speed-ticks", 5); opts.put("return-after", 0);
                }
                case MOVING_BLOCK_CLUSTER -> {
                    opts.put("dest-x", 0); opts.put("dest-y", 0); opts.put("dest-z", 0);
                    opts.put("speed-ticks", 5); opts.put("radius", 3); opts.put("return-after", 0);
                }
                case MYTHIC_SKILL -> opts.put("skill-name", "");
                case MYTHIC_SIGNAL -> { opts.put("signal-name", ""); opts.put("signal-radius", 16.0); }
                case COMMAND_SENDER -> { opts.put("command", "say 你好 {player}"); opts.put("as-console", true); }
                case MULTI_FUNCTION, FUNCTION_RANDOMIZER, FUNCTION_SEQUENCER -> opts.put("function-ids", List.of());
                case FUNCTION_REPEATER -> { opts.put("function-id", ""); opts.put("repeat-count", 3); opts.put("interval-ticks", 20); }
                case DELAYED_FUNCTION -> { opts.put("function-id", ""); opts.put("delay-ticks", 20); }
                case DUNGEON_VARIABLE -> { opts.put("variable-name", "我的變數"); opts.put("variable-action", "設定"); opts.put("variable-value", "0"); }
            }
            return opts;
        }

        /** 取得指定選項的顯示圖示。 */
        public static Material getOptionIcon(final String optionKey) {
            return switch (optionKey) {
                case "signal-name", "signal-radius" -> Material.SCULK_SENSOR;
                case "action", "block-action", "door-action", "variable-action" -> Material.REPEATER;
                case "amount", "count", "repeat-count" -> Material.RABBIT_FOOT;
                case "status-text", "message", "title", "subtitle", "hologram-text", "key-name", "custom-name", "npc-name" -> Material.NAME_TAG;
                case "difficulty-level", "level" -> Material.EXPERIENCE_BOTTLE;
                case "dest-x", "dest-y", "dest-z", "dest-yaw", "dest-pitch" -> Material.COMPASS;
                case "fade-in", "stay", "fade-out", "delay-ticks", "interval-ticks", "duration-ticks", "speed-ticks", "return-after" -> Material.CLOCK;
                case "item-type", "key-material", "block-type", "block-filter" -> Material.CHEST;
                case "drop-at-location" -> Material.DROPPER;
                case "mob-type" -> Material.SPAWNER;
                case "mythic-mob-id", "skill-name" -> Material.BLAZE_POWDER;
                case "spawn-radius", "radius" -> Material.COMPASS;
                case "sound-name" -> Material.NOTE_BLOCK;
                case "volume", "pitch" -> Material.BELL;
                case "allow-place", "allow-break", "as-console" -> Material.LEVER;
                case "command" -> Material.COMMAND_BLOCK;
                case "function-id", "function-ids" -> Material.CHAIN_COMMAND_BLOCK;
                case "loot-table" -> Material.BARREL;
                case "reward-items", "reward-pool" -> Material.CHEST;
                case "min-items", "max-items" -> Material.HOPPER;
                case "variable-name" -> Material.BOOK;
                case "variable-value" -> Material.EMERALD;
                case "npc-skin" -> Material.PLAYER_HEAD;
                default -> Material.PAPER;
            };
        }

        /** 取得指定選項的中文顯示名稱。 */
        public static String getOptionDisplayName(final String optionKey) {
            return switch (optionKey) {
                case "signal-name" -> "§e信號名稱";
                case "signal-radius" -> "§e信號半徑";
                case "action" -> "§e操作方式";
                case "amount" -> "§e數量";
                case "status-text" -> "§e狀態文字";
                case "difficulty-level" -> "§e難度等級";
                case "message" -> "§e訊息內容";
                case "title" -> "§e標題文字";
                case "subtitle" -> "§e副標題";
                case "fade-in" -> "§e淡入 tick";
                case "stay" -> "§e停留 tick";
                case "fade-out" -> "§e淡出 tick";
                case "dest-x" -> "§e目標 X";
                case "dest-y" -> "§e目標 Y";
                case "dest-z" -> "§e目標 Z";
                case "dest-yaw" -> "§e目標 Yaw";
                case "dest-pitch" -> "§e目標 Pitch";
                case "item-type" -> "§e物品類型";
                case "custom-name" -> "§e自訂名稱";
                case "drop-at-location" -> "§e掉落在位置";
                case "key-material" -> "§e鑰匙材質";
                case "key-name" -> "§e鑰匙名稱";
                case "reward-items" -> "§e獎勵物品";
                case "min-items" -> "§e最少物品";
                case "max-items" -> "§e最多物品";
                case "reward-pool" -> "§e獎勵池";
                case "loot-table" -> "§e戰利品表";
                case "mob-type" -> "§e怪物類型";
                case "mythic-mob-id" -> "§eMythicMobs ID";
                case "count" -> "§e生成數量";
                case "level" -> "§e怪物等級";
                case "spawn-radius" -> "§e生成半徑";
                case "npc-name" -> "§eNPC 名稱";
                case "npc-skin" -> "§eNPC 皮膚";
                case "block-type" -> "§e方塊類型";
                case "block-action" -> "§e方塊操作";
                case "block-filter" -> "§e方塊過濾";
                case "sound-name" -> "§e音效名稱";
                case "volume" -> "§e音量";
                case "pitch" -> "§e音高";
                case "duration-ticks" -> "§e持續 tick";
                case "door-action" -> "§e門操作";
                case "allow-place" -> "§e允許放置";
                case "allow-break" -> "§e允許破壞";
                case "hologram-text" -> "§e全息文字";
                case "speed-ticks" -> "§e移動速度 tick";
                case "radius" -> "§e半徑";
                case "return-after" -> "§e返回延遲 tick";
                case "skill-name" -> "§e技能名稱";
                case "command" -> "§e指令內容";
                case "as-console" -> "§e以控制台執行";
                case "function-id" -> "§e功能 ID";
                case "function-ids" -> "§e功能 ID 列表";
                case "repeat-count" -> "§e重複次數";
                case "interval-ticks" -> "§e間隔 tick";
                case "delay-ticks" -> "§e延遲 tick";
                case "variable-name" -> "§e變數名稱";
                case "variable-action" -> "§e變數操作";
                case "variable-value" -> "§e變數值";
                default -> "§e" + optionKey;
            };
        }

        /** 判斷選項是否為布林切換類型。 */
        public static boolean isBooleanOption(final String optionKey) {
            return switch (optionKey) {
                case "drop-at-location", "allow-place", "allow-break", "as-console" -> true;
                default -> false;
            };
        }

        /** 判斷選項是否為循環選擇類型，回傳可選值或 null。 */
        public static List<String> getCycleValues(final String optionKey) {
            return switch (optionKey) {
                case "action" -> List.of("增加", "移除", "設定");
                case "block-action" -> List.of("放置", "移除");
                case "door-action" -> List.of("鎖定", "解鎖", "切換");
                case "variable-action" -> List.of("設定", "增加", "減少");
                default -> null;
            };
        }

        /** 取得某分類底下的所有功能。 */
        public static List<FunctionType> byCategory(final FunctionCategory category) {
            return Arrays.stream(values()).filter(f -> f.category == category).toList();
        }
    }

    // ══════════════════════════════════════════════════
    //  觸發器類型（14 種，完全對應 MythicDungeons）
    // ══════════════════════════════════════════════════

    public enum TriggerType {
        // ── 副本觸發 ──
        DUNGEON_START(Material.LIME_DYE,
                "§a副本開始", "副本開始時觸發。",
                new String[]{}),
        SIGNAL_RECEIVER(Material.SCULK_SENSOR,
                "§d信號接收器", "收到匹配信號時觸發。",
                new String[]{"signal-name"}),
        LEAVE_DUNGEON_LISTENER(Material.IRON_DOOR,
                "§c離開監聽", "玩家離開副本時觸發。",
                new String[]{}),

        // ── 玩家觸發 ──
        RIGHT_CLICK(Material.FEATHER,
                "§e右鍵點擊", "玩家右鍵點擊此方塊時觸發。",
                new String[]{}),
        PLAYER_DETECTOR(Material.OBSERVER,
                "§b玩家偵測器", "預設偵測玩家；設定 mob-type 後改為偵測怪物。",
                new String[]{"distance", "player-count", "mob-type"}),
        KEY_ITEM_DETECTOR(Material.TRIPWIRE_HOOK,
                "§e鑰匙偵測", "玩家使用鑰匙物品時觸發。",
                new String[]{"key-item-type"}),
        CHAT_MESSAGE(Material.WRITABLE_BOOK,
                "§f聊天訊息", "玩家發送匹配訊息時觸發。",
                new String[]{"chat-pattern"}),
        BLOCK_DETECTOR(Material.GRASS_BLOCK,
                "§a方塊偵測", "玩家在此位置放置/破壞方塊時觸發。",
                new String[]{"detect-block-type", "detect-place", "detect-break"}),
        PLAYER_DEATH_COUNTER(Material.SKELETON_SKULL,
                "§c玩家死亡計數", "指定數量的玩家死亡時觸發。",
                new String[]{"death-count"}),

        // ── 通用觸發 ──
        MOB_DEATH_COUNTER(Material.ZOMBIE_HEAD,
                "§6怪物死亡計數", "指定數量的怪物死亡時觸發。",
                new String[]{"mob-type", "kill-count"}),
        REDSTONE_RECEIVER(Material.REDSTONE,
                "§c紅石接收", "此位置收到紅石訊號時觸發。",
                new String[]{}),

        // ── 邏輯觸發 ──
        AND_GATE(Material.REDSTONE_TORCH,
                "§d與邏輯閘", "所有配置的觸發器都被觸發後觸發。",
                new String[]{"trigger-ids"}),
        OR_GATE(Material.SOUL_TORCH,
                "§b或邏輯閘", "任一配置的觸發器被觸發後觸發。",
                new String[]{"trigger-ids"});

        private final Material icon;
        private final String display;
        private final String description;
        private final String[] optionKeys;

        TriggerType(Material icon, String display, String description, String[] optionKeys) {
            this.icon = icon;
            this.display = display;
            this.description = description;
            this.optionKeys = optionKeys;
        }

        public Material icon() { return this.icon; }
        public String display() { return this.display; }
        public String description() { return this.description; }
        public String[] optionKeys() { return this.optionKeys; }

        /** 取得觸發器預設選項。 */
        public Map<String, Object> defaultOptions() {
            final Map<String, Object> opts = new LinkedHashMap<>();
            switch (this) {
                case DUNGEON_START, LEAVE_DUNGEON_LISTENER, RIGHT_CLICK, REDSTONE_RECEIVER -> {}
                case SIGNAL_RECEIVER -> opts.put("signal-name", "預設信號");
                                case PLAYER_DETECTOR -> {
                                        opts.put("distance", 10.0);
                                        opts.put("player-count", 1);
                                        opts.put("mob-type", "");
                                }
                case KEY_ITEM_DETECTOR -> opts.put("key-item-type", "TRIPWIRE_HOOK");
                case CHAT_MESSAGE -> opts.put("chat-pattern", "開啟");
                case BLOCK_DETECTOR -> { opts.put("detect-block-type", ""); opts.put("detect-place", true); opts.put("detect-break", true); }
                case PLAYER_DEATH_COUNTER -> opts.put("death-count", 1);
                case MOB_DEATH_COUNTER -> { opts.put("mob-type", ""); opts.put("kill-count", 5); }
                case AND_GATE, OR_GATE -> opts.put("trigger-ids", List.of());
            }
            return opts;
        }

        /** 取得觸發器選項的中文顯示名稱。 */
        public static String getOptionDisplayName(final String optionKey) {
            return switch (optionKey) {
                case "signal-name" -> "§e信號名稱";
                case "distance" -> "§e偵測距離";
                case "player-count" -> "§e所需玩家數";
                case "key-item-type" -> "§e鑰匙物品類型";
                case "chat-pattern" -> "§e匹配文字";
                case "detect-block-type" -> "§e偵測方塊類型";
                case "detect-place" -> "§e偵測放置";
                case "detect-break" -> "§e偵測破壞";
                case "death-count" -> "§e死亡次數";
                case "mob-type" -> "§e怪物類型";
                case "kill-count" -> "§e擊殺數量";
                case "trigger-ids" -> "§e觸發器 ID 列表";
                default -> "§e" + optionKey;
            };
        }

        /** 判斷觸發器選項是否為布林值。 */
        public static boolean isBooleanOption(final String optionKey) {
            return switch (optionKey) {
                case "detect-place", "detect-break" -> true;
                default -> false;
            };
        }
    }

    // ══════════════════════════════════════════════════
    //  條件類型（9 種，完全對應 MythicDungeons）
    // ══════════════════════════════════════════════════

    public enum ConditionType {
        PLAYERS_WITHIN(Material.OBSERVER,
                "§b附近玩家數", "檢查指定距離內的玩家數量。",
                new String[]{"distance", "count"}),
        MOBS_WITHIN(Material.SPAWNER,
                "§c附近怪物數", "檢查指定距離內的怪物數量。",
                new String[]{"mob-type", "distance", "count"}),
        PLAYER_COUNT(Material.PLAYER_HEAD,
                "§a副本玩家數", "檢查副本中的玩家總數。",
                new String[]{"count", "comparison"}),
        MYTHIC_CONDITION(Material.BLAZE_POWDER,
                "§5MythicMobs 條件", "使用 MythicMobs 條件判斷。",
                new String[]{"condition-name"}),
        DUNGEON_STATUS_CHECK(Material.OAK_SIGN,
                "§f副本狀態檢查", "檢查副本狀態是否為指定值。",
                new String[]{"status-text"}),
        TRIGGER_CHANCE(Material.RABBIT_FOOT,
                "§e觸發機率", "此觸發器有百分比機率成功。",
                new String[]{"chance"}),
        TIME_ELAPSED(Material.CLOCK,
                "§6經過時間", "檢查副本已過了指定秒數。",
                new String[]{"seconds"}),
        DUNGEON_DIFFICULTY_CHECK(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                "§4副本難度檢查", "檢查副本難度是否為指定值。",
                new String[]{"difficulty-level"}),
        VARIABLE_COMPARISON(Material.BOOK,
                "§f變數比較", "比較兩個值（支援副本變數 <variable_name>）。",
                new String[]{"value-a", "comparison", "value-b"});

        private final Material icon;
        private final String display;
        private final String description;
        private final String[] optionKeys;

        ConditionType(Material icon, String display, String description, String[] optionKeys) {
            this.icon = icon;
            this.display = display;
            this.description = description;
            this.optionKeys = optionKeys;
        }

        public Material icon() { return this.icon; }
        public String display() { return this.display; }
        public String description() { return this.description; }
        public String[] optionKeys() { return this.optionKeys; }

        /** 取得條件預設選項。 */
        public Map<String, Object> defaultOptions() {
            final Map<String, Object> opts = new LinkedHashMap<>();
            switch (this) {
                case PLAYERS_WITHIN -> { opts.put("distance", 10.0); opts.put("count", 1); }
                case MOBS_WITHIN -> { opts.put("mob-type", ""); opts.put("distance", 10.0); opts.put("count", 1); }
                case PLAYER_COUNT -> { opts.put("count", 1); opts.put("comparison", ">="); }
                case MYTHIC_CONDITION -> opts.put("condition-name", "");
                case DUNGEON_STATUS_CHECK -> opts.put("status-text", "進行中");
                case TRIGGER_CHANCE -> opts.put("chance", 0.5);
                case TIME_ELAPSED -> opts.put("seconds", 30);
                case DUNGEON_DIFFICULTY_CHECK -> opts.put("difficulty-level", "普通");
                case VARIABLE_COMPARISON -> { opts.put("value-a", "<我的變數>"); opts.put("comparison", ">="); opts.put("value-b", "5"); }
            }
            return opts;
        }
    }

    // ══════════════════════════════════════════════════
    //  功能條件 Record
    // ══════════════════════════════════════════════════

    /**
     * 附加在觸發器上的條件。
     *
     * @param type    條件類型
     * @param options 條件選項
     */
    public record FunctionCondition(
            ConditionType type,
            Map<String, Object> options
    ) {}
}
