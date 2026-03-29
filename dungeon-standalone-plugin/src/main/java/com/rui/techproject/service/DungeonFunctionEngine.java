package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.model.dungeon.DungeonDefinition;
import com.rui.techproject.model.dungeon.DungeonFunction;
import com.rui.techproject.model.dungeon.DungeonFunction.*;
import com.rui.techproject.model.dungeon.DungeonInstance;
import com.rui.techproject.util.SafeScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

/**
 * 副本功能運行引擎 — 在副本實例運作期間，負責觸發偵測、條件評估、功能執行。
 * <p>
 * MythicDungeons 完整對應：
 * <ul>
 *   <li>30 種功能全部執行</li>
 *   <li>14 種觸發器全部偵測</li>
 *   <li>9 種條件全部評估</li>
 *   <li>信號系統（SIGNAL_SENDER → SIGNAL_RECEIVER）串連事件</li>
 *   <li>MULTI_FUNCTION / DELAYED_FUNCTION / FUNCTION_REPEATER / FUNCTION_RANDOMIZER / FUNCTION_SEQUENCER 串連組合</li>
 *   <li>MythicMobs 軟依賴（反射 API）：怪物生成、技能執行、信號發送</li>
 * </ul>
 */
public final class DungeonFunctionEngine {

    private final TechProjectPlugin plugin;
    private final SafeScheduler scheduler;
    private final DungeonService dungeonService;

    // ══════════════════════════════════════════════════
    //  每個副本實例的運行時上下文
    // ══════════════════════════════════════════════════

    /** instanceId → 運行上下文 */
    private final Map<String, FunctionRuntimeContext> contexts = new ConcurrentHashMap<>();

    /** 運行時上下文：追蹤一個副本實例中所有功能的狀態。 */
    private static final class FunctionRuntimeContext {
        final String instanceId;
        final DungeonInstance instance;
        final List<DungeonFunction> functions;
        /** funcId → DungeonFunction 快速查找 */
        final Map<String, DungeonFunction> byId = new LinkedHashMap<>();
        /** signalName → 監聽該信號的功能列表 */
        final Map<String, List<DungeonFunction>> signalListeners = new HashMap<>();
        /** 已觸發且 allowRetrigger=false 的功能 ID */
        final Set<String> triggeredOnce = ConcurrentHashMap.newKeySet();
        /** FUNCTION_SEQUENCER 的當前索引 */
        final Map<String, Integer> sequencerIndices = new ConcurrentHashMap<>();
        /** MOB_DEATH_COUNTER 觸發器的累計計數（per-function） */
        final Map<String, Integer> mobDeathCounts = new ConcurrentHashMap<>();
        /** PLAYER_DEATH_COUNTER 觸發器的累計計數（per-function） */
        final Map<String, Integer> playerDeathCounts = new ConcurrentHashMap<>();
        /** AND_GATE 觸發器的子觸發追蹤 */
        final Map<String, Set<String>> andGateStates = new ConcurrentHashMap<>();
        /** OR_GATE 觸發器的子觸發追蹤 */
        final Map<String, Set<String>> orGateStates = new ConcurrentHashMap<>();
        /** 區塊載入器追蹤 */
        final Set<Long> loadedChunks = ConcurrentHashMap.newKeySet();
        /** 全息文字實體 */
        final List<Entity> hologramEntities = new ArrayList<>();

        FunctionRuntimeContext(final String instanceId, final DungeonInstance instance,
                               final List<DungeonFunction> functions) {
            this.instanceId = instanceId;
            this.instance = instance;
            this.functions = new ArrayList<>(functions);
        }
    }

    // ══════════════════════════════════════════════════
    //  MythicMobs 反射快取
    // ══════════════════════════════════════════════════

    private boolean mythicMobsChecked = false;
    private boolean mythicMobsAvailable = false;
    private Object mythicBukkitInstance; // io.lumine.mythic.bukkit.MythicBukkit
    private Method mythicGetApiHelper;   // MythicBukkit.getAPIHelper()
    private Method mythicSpawnMethod;    // BukkitAPIHelper.spawnMythicMob(String, Location, int)
    private Method mythicSpawnMethodNoLevel; // BukkitAPIHelper.spawnMythicMob(String, Location)

    // ══════════════════════════════════════════════════
    //  建構
    // ══════════════════════════════════════════════════

    public DungeonFunctionEngine(final TechProjectPlugin plugin,
                                  final SafeScheduler scheduler,
                                  final DungeonService dungeonService) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.dungeonService = dungeonService;
    }

    // ══════════════════════════════════════════════════
    //  實例生命週期
    // ══════════════════════════════════════════════════

    /**
     * 初始化副本實例的功能引擎。
     * 在副本世界載入完成、玩家傳送進去後呼叫。
     */
    public void initInstance(final DungeonInstance instance) {
        final String instanceId = instance.instanceId();
        final String dungeonId = instance.definition().id();

        // 從編輯器管理器拿功能定義
        final List<DungeonFunction> funcs = this.dungeonService.getEditorManager().getFunctions(dungeonId);
        if (funcs.isEmpty()) {
            this.plugin.getLogger().info("[功能引擎] " + dungeonId + " 沒有設定功能，跳過初始化。");
            return;
        }

        final FunctionRuntimeContext ctx = new FunctionRuntimeContext(instanceId, instance, funcs);

        // 建立索引
        for (final DungeonFunction func : ctx.functions) {
            ctx.byId.put(func.id(), func);
            // 索引 SIGNAL_RECEIVER 觸發器
            if (func.triggerType() == TriggerType.SIGNAL_RECEIVER) {
                final String signal = toString(func.triggerOptions().get("signal-name"), "default_signal");
                ctx.signalListeners.computeIfAbsent(signal, k -> new ArrayList<>()).add(func);
            }
        }

        this.contexts.put(instanceId, ctx);
        this.plugin.getLogger().info("[功能引擎] 已為 " + instanceId + " 初始化 " + ctx.functions.size() + " 個功能");
    }

    /** 銷毀副本實例的功能引擎。 */
    public void destroyInstance(final String instanceId) {
        final FunctionRuntimeContext ctx = this.contexts.remove(instanceId);
        if (ctx != null) {
            // 清理全息文字
            for (final Entity e : ctx.hologramEntities) {
                if (e != null && e.isValid()) e.remove();
            }
            // 取消區塊載入
            final World world = ctx.instance.instanceWorld();
            if (world != null) {
                for (final long key : ctx.loadedChunks) {
                    final int cx = (int) (key >> 32);
                    final int cz = (int) key;
                    world.removePluginChunkTicket(cx, cz, this.plugin);
                }
            }
        }
    }

    // ══════════════════════════════════════════════════
    //  周期性 Tick（每 tick 由 DungeonService 呼叫）
    // ══════════════════════════════════════════════════

    /**
     * 每 tick 檢查需要周期偵測的觸發器。
     * PLAYER_DETECTOR 會依 mob-type 選項決定偵測玩家或怪物。
     */
    public void tick(final DungeonInstance instance) {
        final FunctionRuntimeContext ctx = this.contexts.get(instance.instanceId());
        if (ctx == null) return;

        for (final DungeonFunction func : ctx.functions) {
            if (func.triggerType() == TriggerType.PLAYER_DETECTOR) {
                this.tickPlayerDetector(ctx, func);
            }
        }
    }

    private void tickPlayerDetector(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        final World world = ctx.instance.instanceWorld();
        if (world == null) return;

        final double distance = toDouble(func.triggerOptions().get("distance"), 10.0);
        final int requiredCount = toInt(func.triggerOptions().get("player-count"), 1);
        final String mobTypeFilter = toString(func.triggerOptions().get("mob-type"), "").trim();
        final Location center = new Location(world,
                func.blockX() + 0.5, func.blockY() + 0.5, func.blockZ() + 0.5);

        // 設定 mob-type 時改為偵測副本怪物，支援「怪物經過觸發」流程。
        if (!mobTypeFilter.isEmpty()) {
            int mobCount = 0;
            for (final Entity entity : ctx.instance.spawnedEntities()) {
                if (!(entity instanceof LivingEntity living)
                        || living.isDead()
                        || !entity.isValid()
                        || !entity.getWorld().equals(world)) {
                    continue;
                }
                if (entity.getLocation().distanceSquared(center) > distance * distance) {
                    continue;
                }
                if (!"any".equalsIgnoreCase(mobTypeFilter)
                        && !entity.getType().name().equalsIgnoreCase(mobTypeFilter)) {
                    continue;
                }
                mobCount++;
            }

            if (mobCount >= requiredCount) {
                this.tryExecute(ctx, func, null);
            }
            return;
        }

        int count = 0;
        Player firstPlayer = null;
        for (final UUID uuid : ctx.instance.members()) {
            final Player p = Bukkit.getPlayer(uuid);
            if (p != null && !p.isDead() && p.getWorld().equals(world)) {
                if (p.getLocation().distanceSquared(center) <= distance * distance) {
                    count++;
                    if (firstPlayer == null) firstPlayer = p;
                }
            }
        }

        if (count >= requiredCount && firstPlayer != null) {
            this.tryExecute(ctx, func, firstPlayer);
        }
    }

    // ══════════════════════════════════════════════════
    //  事件觸發入口
    // ══════════════════════════════════════════════════

    /** 副本開始時觸發所有 DUNGEON_START 觸發器。 */
    public void onDungeonStart(final DungeonInstance instance) {
        final FunctionRuntimeContext ctx = this.contexts.get(instance.instanceId());
        if (ctx == null) return;

        for (final DungeonFunction func : ctx.functions) {
            if (func.triggerType() == TriggerType.DUNGEON_START) {
                this.tryExecute(ctx, func, null);
            }
        }
    }

    /** 玩家右鍵/左鍵方塊時。 */
    public void onPlayerInteract(final DungeonInstance instance, final Player player,
                                  final Block block, final Action action) {
        final FunctionRuntimeContext ctx = this.contexts.get(instance.instanceId());
        if (ctx == null || block == null) return;

        final int bx = block.getX(), by = block.getY(), bz = block.getZ();
        for (final DungeonFunction func : ctx.functions) {
            if (func.blockX() == bx && func.blockY() == by && func.blockZ() == bz) {
                if (func.triggerType() == TriggerType.RIGHT_CLICK
                        && (action == Action.RIGHT_CLICK_BLOCK)) {
                    this.tryExecute(ctx, func, player);
                }
            }
        }
    }

    /** 鑰匙物品使用。 */
    public void onKeyItemUse(final DungeonInstance instance, final Player player,
                              final Block block, final ItemStack item) {
        final FunctionRuntimeContext ctx = this.contexts.get(instance.instanceId());
        if (ctx == null || block == null) return;

        final int bx = block.getX(), by = block.getY(), bz = block.getZ();
        for (final DungeonFunction func : ctx.functions) {
            if (func.blockX() == bx && func.blockY() == by && func.blockZ() == bz
                    && func.triggerType() == TriggerType.KEY_ITEM_DETECTOR) {
                final String requiredType = toString(func.triggerOptions().get("key-item-type"), "TRIPWIRE_HOOK");
                if (item.getType().name().equalsIgnoreCase(requiredType)) {
                    this.tryExecute(ctx, func, player);
                }
            }
        }
    }

    /** 聊天訊息。 */
    public void onChatMessage(final DungeonInstance instance, final Player player, final String message) {
        final FunctionRuntimeContext ctx = this.contexts.get(instance.instanceId());
        if (ctx == null) return;

        for (final DungeonFunction func : ctx.functions) {
            if (func.triggerType() == TriggerType.CHAT_MESSAGE) {
                final String pattern = toString(func.triggerOptions().get("chat-pattern"), "");
                if (!pattern.isEmpty() && message.toLowerCase().contains(pattern.toLowerCase())) {
                    this.tryExecute(ctx, func, player);
                }
            }
        }
    }

    /** 方塊放置/破壞。 */
    public void onBlockEvent(final DungeonInstance instance, final Player player,
                              final Block block, final boolean isPlace) {
        final FunctionRuntimeContext ctx = this.contexts.get(instance.instanceId());
        if (ctx == null || block == null) return;

        final int bx = block.getX(), by = block.getY(), bz = block.getZ();
        for (final DungeonFunction func : ctx.functions) {
            if (func.triggerType() == TriggerType.BLOCK_DETECTOR) {
                // 檢查是否為功能位置附近的方塊偵測
                final double dx = Math.abs(func.blockX() - bx);
                final double dy = Math.abs(func.blockY() - by);
                final double dz = Math.abs(func.blockZ() - bz);
                if (dx <= 5 && dy <= 5 && dz <= 5) {
                    final String blockType = toString(func.triggerOptions().get("detect-block-type"), "");
                    final boolean detectPlace = toBool(func.triggerOptions().get("detect-place"), true);
                    final boolean detectBreak = toBool(func.triggerOptions().get("detect-break"), true);

                    if ((isPlace && detectPlace) || (!isPlace && detectBreak)) {
                        if (blockType.isEmpty() || block.getType().name().equalsIgnoreCase(blockType)) {
                            this.tryExecute(ctx, func, player);
                        }
                    }
                }
            }
        }
    }

    /** 玩家死亡。 */
    public void onPlayerDeath(final DungeonInstance instance, final Player player) {
        final FunctionRuntimeContext ctx = this.contexts.get(instance.instanceId());
        if (ctx == null) return;

        for (final DungeonFunction func : ctx.functions) {
            if (func.triggerType() == TriggerType.PLAYER_DEATH_COUNTER) {
                final int required = toInt(func.triggerOptions().get("death-count"), 1);
                final int current = ctx.playerDeathCounts.merge(func.id(), 1, Integer::sum);
                if (current >= required) {
                    this.tryExecute(ctx, func, player);
                }
            }
        }
    }

    /** 怪物死亡。 */
    public void onMobDeath(final DungeonInstance instance, final Entity entity) {
        final FunctionRuntimeContext ctx = this.contexts.get(instance.instanceId());
        if (ctx == null) return;

        final String mobType = entity.getType().name();
        for (final DungeonFunction func : ctx.functions) {
            if (func.triggerType() == TriggerType.MOB_DEATH_COUNTER) {
                final String requiredType = toString(func.triggerOptions().get("mob-type"), "");
                final int requiredCount = toInt(func.triggerOptions().get("kill-count"), 5);

                if (requiredType.isEmpty() || requiredType.equalsIgnoreCase(mobType)) {
                    final int current = ctx.mobDeathCounts.merge(func.id(), 1, Integer::sum);
                    if (current >= requiredCount) {
                        this.tryExecute(ctx, func, null);
                    }
                }
            }
        }
    }

    /** 紅石訊號變化。 */
    public void onRedstoneChange(final DungeonInstance instance, final Block block) {
        final FunctionRuntimeContext ctx = this.contexts.get(instance.instanceId());
        if (ctx == null || block == null) return;

        final int bx = block.getX(), by = block.getY(), bz = block.getZ();
        for (final DungeonFunction func : ctx.functions) {
            if (func.triggerType() == TriggerType.REDSTONE_RECEIVER
                    && func.blockX() == bx && func.blockY() == by && func.blockZ() == bz) {
                this.tryExecute(ctx, func, null);
            }
        }
    }

    /** 玩家離開副本。 */
    public void onPlayerLeave(final DungeonInstance instance, final Player player) {
        final FunctionRuntimeContext ctx = this.contexts.get(instance.instanceId());
        if (ctx == null) return;

        for (final DungeonFunction func : ctx.functions) {
            if (func.triggerType() == TriggerType.LEAVE_DUNGEON_LISTENER) {
                this.tryExecute(ctx, func, player);
            }
        }
    }

    /**
     * 廣播信號 — SIGNAL_SENDER 執行時呼叫。
     * 觸發所有監聽此信號的 SIGNAL_RECEIVER 功能。
     */
    public void broadcastSignal(final FunctionRuntimeContext ctx,
                                 final String signalName, final Player triggerPlayer) {
        // 記錄到 instance 信號系統
        ctx.instance.sendSignal(signalName);

        // 觸發所有 SIGNAL_RECEIVER
        final List<DungeonFunction> listeners = ctx.signalListeners.getOrDefault(signalName, List.of());
        for (final DungeonFunction listener : listeners) {
            this.tryExecute(ctx, listener, triggerPlayer);
        }

        // 信號也可以作為邏輯閘的 trigger-id token。
        this.notifyLogicGates(ctx, signalName, triggerPlayer);

        this.plugin.getLogger().fine("[功能引擎] 廣播信號 '" + signalName + "' → "
                + listeners.size() + " 個接收器");
    }

    private void notifyLogicGates(final FunctionRuntimeContext ctx,
                                   final String triggerToken,
                                   final Player triggerPlayer) {
        if (triggerToken == null || triggerToken.isBlank()) {
            return;
        }

        final String normalizedToken = triggerToken.trim().toLowerCase(Locale.ROOT);
        for (final DungeonFunction gate : ctx.functions) {
            if (gate.triggerType() != TriggerType.AND_GATE && gate.triggerType() != TriggerType.OR_GATE) {
                continue;
            }
            if (gate.id().equalsIgnoreCase(triggerToken)) {
                continue;
            }

            final List<String> triggerIds = toStringList(gate.triggerOptions().get("trigger-ids")).stream()
                    .map(id -> id == null ? "" : id.trim().toLowerCase(Locale.ROOT))
                    .filter(id -> !id.isEmpty())
                    .toList();
            if (triggerIds.isEmpty() || !triggerIds.contains(normalizedToken)) {
                continue;
            }

            if (gate.triggerType() == TriggerType.OR_GATE) {
                this.tryExecute(ctx, gate, triggerPlayer);
                continue;
            }

            final Set<String> fired = ctx.andGateStates.computeIfAbsent(gate.id(),
                    key -> ConcurrentHashMap.newKeySet());
            fired.add(normalizedToken);
            if (fired.containsAll(triggerIds)) {
                this.tryExecute(ctx, gate, triggerPlayer);
                fired.clear();
            }
        }
    }

    // ══════════════════════════════════════════════════
    //  核心執行邏輯
    // ══════════════════════════════════════════════════

    /**
     * 嘗試執行功能：先檢查重複觸發限制，再評估條件，最後執行。
     */
    private void tryExecute(final FunctionRuntimeContext ctx, final DungeonFunction func,
                             final Player triggerPlayer) {
        // 檢查重複觸發限制
        if (!func.allowRetrigger() && ctx.triggeredOnce.contains(func.id())) {
            return;
        }

        // 評估條件
        if (!this.evaluateConditions(ctx, func, triggerPlayer)) {
            return;
        }

        // 標記為已觸發
        if (!func.allowRetrigger()) {
            ctx.triggeredOnce.add(func.id());
        }

        // 根據 TargetType 取得目標玩家列表並執行
        final List<Player> targets = this.resolveTargets(ctx, func, triggerPlayer);
        if (targets.isEmpty()) {
            // 對 NONE 目標的功能直接執行
            this.executeFunction(ctx, func, triggerPlayer);
        } else {
            for (final Player target : targets) {
                this.executeFunction(ctx, func, target);
            }
        }

        // 任何功能成功進入執行流程後，都可作為 AND/OR 邏輯閘的觸發 token。
        this.notifyLogicGates(ctx, func.id(), triggerPlayer);
    }

    /** 解析目標玩家。 */
    private List<Player> resolveTargets(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                         final Player triggerPlayer) {
        return switch (func.targetType()) {
            case PLAYER -> {
                if (triggerPlayer != null) yield List.of(triggerPlayer);
                yield List.of();
            }
            case PARTY -> {
                final List<Player> list = new ArrayList<>();
                for (final UUID uuid : ctx.instance.members()) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline() && !p.isDead()) list.add(p);
                }
                yield list;
            }
            case NONE -> List.of();
        };
    }

    // ══════════════════════════════════════════════════
    //  條件評估（9 種 ConditionType）
    // ══════════════════════════════════════════════════

    private boolean evaluateConditions(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                        final Player triggerPlayer) {
        for (final FunctionCondition condition : func.conditions()) {
            if (!this.evaluateCondition(ctx, func, condition, triggerPlayer)) {
                return false;
            }
        }
        return true;
    }

    private boolean evaluateCondition(final FunctionRuntimeContext ctx,
                                       final DungeonFunction func,
                                       final FunctionCondition condition,
                                       final Player triggerPlayer) {
        final Map<String, Object> opts = condition.options();
        final World world = ctx.instance.instanceWorld();
        final Location center = world == null ? null
                : new Location(world, func.blockX() + 0.5, func.blockY() + 0.5, func.blockZ() + 0.5);

        return switch (condition.type()) {
            case PLAYERS_WITHIN -> {
                if (world == null || center == null) yield false;
                final double distance = toDouble(opts.get("distance"), 10.0);
                final int required = toInt(opts.get("count"), 1);
                int count = 0;
                for (final UUID uuid : ctx.instance.members()) {
                    final Player p = Bukkit.getPlayer(uuid);
                    if (p != null && !p.isDead() && p.getWorld().equals(world)) {
                        if (p.getLocation().distanceSquared(center) <= distance * distance) {
                            count++;
                        }
                    }
                }
                yield count >= required;
            }
            case MOBS_WITHIN -> {
                if (world == null || center == null) yield false;
                final String mobType = toString(opts.get("mob-type"), "");
                final double distance = toDouble(opts.get("distance"), 10.0);
                final int required = toInt(opts.get("count"), 0);
                int count = 0;
                for (final Entity e : ctx.instance.spawnedEntities()) {
                    if (e != null && e.isValid() && !e.isDead() && e.getWorld().equals(world)
                            && e.getLocation().distanceSquared(center) <= distance * distance) {
                        if (mobType.isEmpty() || e.getType().name().equalsIgnoreCase(mobType)) {
                            count++;
                        }
                    }
                }
                yield count >= required;
            }
            case PLAYER_COUNT -> {
                final int required = toInt(opts.get("count"), 1);
                final String comparison = toString(opts.get("comparison"), ">=");
                final int online = (int) ctx.instance.members().stream()
                        .map(Bukkit::getPlayer)
                        .filter(p -> p != null && p.isOnline() && !p.isDead())
                        .count();
                yield compare(online, required, comparison);
            }
            case MYTHIC_CONDITION -> {
                final String conditionName = toString(opts.get("condition-name"), "").trim();
                yield this.evaluateMythicCondition(ctx, center, conditionName);
            }
            case DUNGEON_STATUS_CHECK -> {
                final String expected = toString(opts.get("status-text"), "ACTIVE");
                final Object current = ctx.instance.getVariable("__dungeon_status");
                yield expected.equalsIgnoreCase(String.valueOf(current != null ? current : ""));
            }
            case TRIGGER_CHANCE -> {
                final double chance = toDouble(opts.get("chance"), 0.5);
                yield ThreadLocalRandom.current().nextDouble() <= chance;
            }
            case TIME_ELAPSED -> {
                final int seconds = toInt(opts.get("seconds"), 30);
                yield ctx.instance.elapsedSeconds() >= seconds;
            }
            case DUNGEON_DIFFICULTY_CHECK -> {
                final String expected = toString(opts.get("difficulty-level"), "NORMAL");
                final String actual = ctx.instance.selectedDifficulty() != null
                        ? ctx.instance.selectedDifficulty() : "NORMAL";
                yield expected.equalsIgnoreCase(actual);
            }
            case VARIABLE_COMPARISON -> {
                final String valA = resolveVariable(ctx, toString(opts.get("value-a"), "0"));
                final String comparison = toString(opts.get("comparison"), ">=");
                final String valB = resolveVariable(ctx, toString(opts.get("value-b"), "0"));
                try {
                    yield compare(Double.parseDouble(valA), Double.parseDouble(valB), comparison);
                } catch (final NumberFormatException e) {
                    yield valA.equals(valB) == comparison.contains("=");
                }
            }
        };
    }

    /** 解析變數引用：<variable_name> 會替換為副本變數值。 */
    private String resolveVariable(final FunctionRuntimeContext ctx, final String text) {
        if (text.startsWith("<") && text.endsWith(">")) {
            final String varName = text.substring(1, text.length() - 1);
            final Object val = ctx.instance.getVariable(varName);
            return val != null ? String.valueOf(val) : "0";
        }
        return text;
    }

    // ══════════════════════════════════════════════════
    //  功能執行（30 種 FunctionType）
    // ══════════════════════════════════════════════════

    private void executeFunction(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                  final Player player) {
        final Map<String, Object> opts = func.functionOptions();

        try {
            switch (func.functionType()) {
                // ── 副本分類 ──
                case START_DUNGEON -> this.execStartDungeon(ctx, func);
                case FINISH_DUNGEON -> this.execFinishDungeon(ctx, player);
                case LEAVE_DUNGEON -> this.execLeaveDungeon(ctx, player);
                case CHECKPOINT -> this.execCheckpoint(ctx, func, player);
                case SIGNAL_SENDER -> this.execSignalSender(ctx, func, player);
                case LIVES_EDITOR -> this.execLivesEditor(ctx, func, player);
                case REVIVER -> this.execReviver(ctx, player);
                case CHUNK_LOADER -> this.execChunkLoader(ctx, func);
                case DUNGEON_STATUS -> this.execDungeonStatus(ctx, func);
                case DIFFICULTY -> this.execDifficulty(ctx, func);

                // ── 玩家分類 ──
                case MESSAGE_SENDER -> this.execMessageSender(func, player);
                case TELEPORTER -> this.execTeleporter(ctx, func, player);
                case TITLE_SENDER -> this.execTitleSender(func, player);
                case ITEM_DISPENSER -> this.execItemDispenser(ctx, func, player);
                case KEY_DISPENSER -> this.execKeyDispenser(func, player);
                case REWARDS -> this.execRewards(ctx, func, player);
                case RANDOM_REWARDS -> this.execRandomRewards(ctx, func, player);
                case LOOT_TABLE_REWARDS -> this.execLootTableRewards(ctx, func, player);

                // ── 位置分類 ──
                case MOB_SPAWNER -> this.execMobSpawner(ctx, func);
                case NPC_SPAWNER -> this.execNpcSpawner(ctx, func);
                case BLOCK_EDITOR -> this.execBlockEditor(ctx, func);
                case SOUND_PLAYER -> this.execSoundPlayer(ctx, func, player);
                case REDSTONE_BLOCK -> this.execRedstoneBlock(ctx, func);
                case DOOR_CONTROLLER -> this.execDoorController(ctx, func);
                case BLOCK_CONTROLLER -> {} // 由事件攔截處理，此處不需主動執行
                case HOLOGRAM -> this.execHologram(ctx, func);
                case MOVING_BLOCK -> this.execMovingBlock(ctx, func);
                case MOVING_BLOCK_CLUSTER -> this.execMovingBlockCluster(ctx, func);

                // ── 進階分類（META）── 串連核心 ──
                case MYTHIC_SKILL -> this.execMythicSkill(ctx, func, player);
                case MYTHIC_SIGNAL -> this.execMythicSignal(ctx, func);
                case COMMAND_SENDER -> this.execCommandSender(func, player);
                case MULTI_FUNCTION -> this.execMultiFunction(ctx, func, player);
                case FUNCTION_REPEATER -> this.execFunctionRepeater(ctx, func, player);
                case DELAYED_FUNCTION -> this.execDelayedFunction(ctx, func, player);
                case FUNCTION_RANDOMIZER -> this.execFunctionRandomizer(ctx, func, player);
                case FUNCTION_SEQUENCER -> this.execFunctionSequencer(ctx, func, player);
                case DUNGEON_VARIABLE -> this.execDungeonVariable(ctx, func);
            }
        } catch (final Exception e) {
            this.plugin.getLogger().log(Level.WARNING,
                    "[功能引擎] 執行功能 " + func.id() + " (" + func.functionType() + ") 失敗", e);
        }
    }

    // ──────────────────────────────────────────────
    //  副本分類功能
    // ──────────────────────────────────────────────

    private void execStartDungeon(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        this.dungeonService.triggerScripts(ctx.instance, "function_start", Map.of("func_id", func.id()));
        this.plugin.getLogger().info("[功能引擎] START_DUNGEON 觸發 @ " + func.locationString());
    }

    private void execFinishDungeon(final FunctionRuntimeContext ctx, final Player player) {
        this.dungeonService.completeDungeon(ctx.instance);
    }

    private void execLeaveDungeon(final FunctionRuntimeContext ctx, final Player player) {
        if (player != null) {
            this.dungeonService.leaveDungeon(player);
        }
    }

    private void execCheckpoint(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                 final Player player) {
        final World world = ctx.instance.instanceWorld();
        if (world == null || player == null) return;
        final Location cp = new Location(world, func.blockX() + 0.5, func.blockY() + 1, func.blockZ() + 0.5);
        if (func.targetType() == TargetType.PARTY) {
            ctx.instance.setCheckpointAll(cp);
            this.broadcastToInstance(ctx.instance, "§a§l✔ §e檢查點已更新！");
        } else {
            ctx.instance.setCheckpoint(player.getUniqueId(), cp);
            player.sendMessage(Component.text("§a§l✔ §e你的檢查點已更新！"));
        }
        this.broadcastSound(ctx.instance, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
    }

    private void execSignalSender(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                   final Player player) {
        final String signalName = toString(func.functionOptions().get("signal-name"), "default_signal");
        this.broadcastSignal(ctx, signalName, player);
    }

    private void execLivesEditor(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                  final Player player) {
        if (player == null) return;
        final String action = this.normalizeLivesAction(toString(func.functionOptions().get("action"), "ADD"));
        final int amount = toInt(func.functionOptions().get("amount"), 1);
        final int current = ctx.instance.getPlayerLives(player.getUniqueId());
        if (current == -1) return; // 無限生命
        final int newVal = switch (action) {
            case "ADD" -> current + amount;
            case "REMOVE" -> Math.max(0, current - amount);
            case "SET" -> amount;
            default -> current;
        };
        ctx.instance.initPlayerLives(player.getUniqueId(), newVal);
        player.sendMessage(Component.text("§e剩餘生命: §c" + newVal));
    }

    private void execReviver(final FunctionRuntimeContext ctx, final Player player) {
        for (final UUID uuid : ctx.instance.spectators()) {
            final Player spectator = Bukkit.getPlayer(uuid);
            if (spectator != null) {
                ctx.instance.removeSpectator(uuid);
                spectator.setGameMode(GameMode.SURVIVAL);
                final Location cp = ctx.instance.getCheckpoint(uuid);
                if (cp != null) spectator.teleportAsync(cp);
                spectator.sendMessage(Component.text("§a§l✔ §e你已被復活！"));
            }
        }
    }

    private void execChunkLoader(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        final World world = ctx.instance.instanceWorld();
        if (world == null) return;
        final int cx = func.blockX() >> 4;
        final int cz = func.blockZ() >> 4;
        final long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        if (ctx.loadedChunks.contains(key)) {
            world.removePluginChunkTicket(cx, cz, this.plugin);
            ctx.loadedChunks.remove(key);
        } else {
            world.addPluginChunkTicket(cx, cz, this.plugin);
            ctx.loadedChunks.add(key);
        }
    }

    private void execDungeonStatus(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        final String status = toString(func.functionOptions().get("status-text"), "ACTIVE");
        ctx.instance.setVariable("__dungeon_status", status);
    }

    private void execDifficulty(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        final String difficulty = toString(func.functionOptions().get("difficulty-level"), "NORMAL");
        ctx.instance.setSelectedDifficulty(difficulty);
    }

    // ──────────────────────────────────────────────
    //  玩家分類功能
    // ──────────────────────────────────────────────

    private void execMessageSender(final DungeonFunction func, final Player player) {
        if (player == null) return;
        final String message = toString(func.functionOptions().get("message"), "");
        player.sendMessage(Component.text(message.replace('&', '§')));
    }

    private void execTeleporter(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                 final Player player) {
        if (player == null) return;
        final World world = ctx.instance.instanceWorld();
        if (world == null) return;
        final double x = toDouble(func.functionOptions().get("dest-x"), 0);
        final double y = toDouble(func.functionOptions().get("dest-y"), 65);
        final double z = toDouble(func.functionOptions().get("dest-z"), 0);
        final float yaw = (float) toDouble(func.functionOptions().get("dest-yaw"), 0);
        final float pitch = (float) toDouble(func.functionOptions().get("dest-pitch"), 0);
        player.teleportAsync(new Location(world, x, y, z, yaw, pitch));
    }

    private void execTitleSender(final DungeonFunction func, final Player player) {
        if (player == null) return;
        final String titleText = toString(func.functionOptions().get("title"), "").replace('&', '§');
        final String subtitleText = toString(func.functionOptions().get("subtitle"), "").replace('&', '§');
        final int fadeIn = toInt(func.functionOptions().get("fade-in"), 10) * 50;
        final int stay = toInt(func.functionOptions().get("stay"), 60) * 50;
        final int fadeOut = toInt(func.functionOptions().get("fade-out"), 20) * 50;
        player.showTitle(Title.title(
                Component.text(titleText),
                Component.text(subtitleText),
                Title.Times.times(Duration.ofMillis(fadeIn), Duration.ofMillis(stay), Duration.ofMillis(fadeOut))));
    }

    private void execItemDispenser(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                    final Player player) {
        if (player == null) return;
        final String itemType = toString(func.functionOptions().get("item-type"), "DIAMOND");
        final int amount = toInt(func.functionOptions().get("amount"), 1);
        final boolean dropAtLocation = toBool(func.functionOptions().get("drop-at-location"), false);

        try {
            final ItemStack stack = new ItemStack(Material.valueOf(itemType.toUpperCase()), amount);
            final String customName = toString(func.functionOptions().get("custom-name"), "");
            if (!customName.isEmpty()) {
                final ItemMeta meta = stack.getItemMeta();
                meta.displayName(Component.text(customName.replace('&', '§')));
                stack.setItemMeta(meta);
            }

            if (dropAtLocation) {
                final World world = ctx.instance.instanceWorld();
                if (world != null) {
                    world.dropItemNaturally(new Location(world,
                            func.blockX() + 0.5, func.blockY() + 1, func.blockZ() + 0.5), stack);
                }
            } else {
                player.getInventory().addItem(stack);
            }
        } catch (final Exception e) {
            this.plugin.getLogger().warning("[功能引擎] ITEM_DISPENSER 物品類型錯誤: " + itemType);
        }
    }

    private void execKeyDispenser(final DungeonFunction func, final Player player) {
        if (player == null) return;
        final String material = toString(func.functionOptions().get("key-material"), "TRIPWIRE_HOOK");
        final String name = toString(func.functionOptions().get("key-name"), "§e副本鑰匙");
        try {
            final ItemStack key = new ItemStack(Material.valueOf(material.toUpperCase()));
            final ItemMeta meta = key.getItemMeta();
            meta.displayName(Component.text(name.replace('&', '§')));
            key.setItemMeta(meta);
            player.getInventory().addItem(key);
        } catch (final Exception ignored) {}
    }

    private void execRewards(final FunctionRuntimeContext ctx, final DungeonFunction func,
                              final Player player) {
        if (player == null) return;
        final List<RewardEntry> rewards = this.parseRewardEntries(func.functionOptions().get("reward-items"));
        if (rewards.isEmpty()) {
            player.sendMessage(Component.text("§e此獎勵尚未配置物品。"));
            return;
        }

        int granted = 0;
        for (final RewardEntry reward : rewards) {
            final ItemStack stack = this.buildRewardStack(reward);
            if (stack == null) {
                continue;
            }
            granted += stack.getAmount();
            this.giveReward(player, stack);
        }

        if (granted > 0) {
            player.sendMessage(Component.text("§6§l獎勵！§e已發放 " + granted + " 個獎勵物品。"));
            this.broadcastSound(ctx.instance, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            player.sendMessage(Component.text("§c獎勵設定無效，未發放任何物品。"));
        }
    }

    private void execRandomRewards(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                    final Player player) {
        if (player == null) return;
        final List<RewardEntry> pool = this.parseRewardEntries(func.functionOptions().get("reward-pool"));
        if (pool.isEmpty()) {
            player.sendMessage(Component.text("§e隨機獎勵池為空。"));
            return;
        }

        int minItems = toInt(func.functionOptions().get("min-items"), 1);
        int maxItems = toInt(func.functionOptions().get("max-items"), Math.max(1, pool.size()));
        if (maxItems < minItems) {
            maxItems = minItems;
        }

        final int clampedMax = Math.max(1, Math.min(maxItems, pool.size()));
        final int clampedMin = Math.max(1, Math.min(minItems, clampedMax));
        final int drawCount = clampedMin == clampedMax
                ? clampedMin
                : ThreadLocalRandom.current().nextInt(clampedMin, clampedMax + 1);

        final List<RewardEntry> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled);

        int granted = 0;
        for (int i = 0; i < drawCount && i < shuffled.size(); i++) {
            final ItemStack stack = this.buildRewardStack(shuffled.get(i));
            if (stack == null) {
                continue;
            }
            granted += stack.getAmount();
            this.giveReward(player, stack);
        }

        if (granted > 0) {
            player.sendMessage(Component.text("§6§l隨機獎勵！§e已獲得 " + granted + " 個物品。"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.1f);
        } else {
            player.sendMessage(Component.text("§c隨機獎勵配置無效，未發放物品。"));
        }
    }

    private void execLootTableRewards(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                       final Player player) {
        if (player == null) return;
        final String tableId = toString(func.functionOptions().get("loot-table"), "").trim();
        if (tableId.isEmpty()) {
            player.sendMessage(Component.text("§e尚未設定戰利品表。"));
            return;
        }

        NamespacedKey key = NamespacedKey.fromString(tableId);
        if (key == null) {
            key = NamespacedKey.fromString(tableId.toLowerCase(Locale.ROOT));
        }
        if (key == null && !tableId.contains(":")) {
            key = NamespacedKey.minecraft(tableId.toLowerCase(Locale.ROOT));
        }
        if (key == null) {
            player.sendMessage(Component.text("§c戰利品表 ID 無效：" + tableId));
            return;
        }

        final LootTable table = Bukkit.getLootTable(key);
        if (table == null) {
            player.sendMessage(Component.text("§c找不到戰利品表：" + tableId));
            return;
        }

        final LootContext context = new LootContext.Builder(player.getLocation()).build();
        final Collection<ItemStack> generated = table.populateLoot(ThreadLocalRandom.current(), context);
        int granted = 0;
        for (final ItemStack stack : generated) {
            if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
                continue;
            }
            granted += stack.getAmount();
            this.giveReward(player, stack);
        }

        if (granted > 0) {
            player.sendMessage(Component.text("§6§l戰利品！§e已發放 " + granted + " 個物品。"));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            player.sendMessage(Component.text("§e戰利品表未產生物品。"));
        }
    }

    private record RewardEntry(String id, int amount, boolean preferTech) {}

    private List<RewardEntry> parseRewardEntries(final Object raw) {
        final List<String> tokens = toStringList(raw);
        if (tokens.isEmpty()) {
            return List.of();
        }

        final List<RewardEntry> entries = new ArrayList<>();
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            token = token.trim();
            if (token.isEmpty()) {
                continue;
            }

            int amount = 1;
            String id = token;
            boolean preferTech = false;

            final int multiplyIndex = token.lastIndexOf('*');
            if (multiplyIndex > 0 && multiplyIndex < token.length() - 1) {
                final String parsedAmount = token.substring(multiplyIndex + 1).trim();
                try {
                    amount = Math.max(1, Integer.parseInt(parsedAmount));
                    id = token.substring(0, multiplyIndex).trim();
                } catch (final NumberFormatException ignored) {
                    id = token;
                }
            } else {
                final int colonIndex = token.lastIndexOf(':');
                if (colonIndex > 0 && colonIndex < token.length() - 1) {
                    final String parsedAmount = token.substring(colonIndex + 1).trim();
                    try {
                        amount = Math.max(1, Integer.parseInt(parsedAmount));
                        id = token.substring(0, colonIndex).trim();
                    } catch (final NumberFormatException ignored) {
                        id = token;
                    }
                }
            }

            if (id.toLowerCase(Locale.ROOT).startsWith("tech:")) {
                preferTech = true;
                id = id.substring("tech:".length()).trim();
            }

            if (!id.isEmpty()) {
                entries.add(new RewardEntry(id, amount, preferTech));
            }
        }
        return entries;
    }

    private ItemStack buildRewardStack(final RewardEntry reward) {
        String materialId = reward.id();
        final int namespaceSplit = materialId.indexOf(':');
        if (namespaceSplit >= 0 && namespaceSplit < materialId.length() - 1) {
            materialId = materialId.substring(namespaceSplit + 1);
        }
        if (reward.preferTech() && !reward.id().contains(":")) {
            materialId = reward.id();
        }
        try {
            return new ItemStack(Material.valueOf(materialId.toUpperCase(Locale.ROOT)), reward.amount());
        } catch (final IllegalArgumentException ex) {
            this.plugin.getLogger().warning("[功能引擎] 找不到獎勵物品材質: " + reward.id());
            return null;
        }
    }

    private void giveReward(final Player player, final ItemStack stack) {
        final Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
        for (final ItemStack extra : overflow.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), extra);
        }
    }

    // ──────────────────────────────────────────────
    //  位置分類功能
    // ──────────────────────────────────────────────

    private void execMobSpawner(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        final World world = ctx.instance.instanceWorld();
        if (world == null) return;

        final String mobType = toString(func.functionOptions().get("mob-type"), "ZOMBIE");
        final String mythicMobId = toString(func.functionOptions().get("mythic-mob-id"), "");
        final int count = toInt(func.functionOptions().get("count"), 5);
        final int level = toInt(func.functionOptions().get("level"), 1);
        final String customName = toString(func.functionOptions().get("custom-name"), "");
        final double spawnRadius = toDouble(func.functionOptions().get("spawn-radius"), 3.0);

        final Location center = new Location(world,
                func.blockX() + 0.5, func.blockY() + 1, func.blockZ() + 0.5);

        for (int i = 0; i < count; i++) {
            final Location spawnLoc = center.clone().add(
                    ThreadLocalRandom.current().nextDouble(-spawnRadius, spawnRadius), 0,
                    ThreadLocalRandom.current().nextDouble(-spawnRadius, spawnRadius));

            this.scheduler.runRegion(spawnLoc, task -> {
                // 優先使用 MythicMobs
                if (!mythicMobId.isEmpty()) {
                    final Entity mythicEntity = this.spawnMythicMobEntity(mythicMobId, spawnLoc, level);
                    if (mythicEntity != null) {
                        ctx.instance.trackEntity(mythicEntity);
                        return; // MythicMobs 生成成功
                    }
                    // MythicMobs 不可用，fallback 到原版
                }

                try {
                    final EntityType entityType = EntityType.valueOf(mobType.toUpperCase());
                    final Entity entity = world.spawnEntity(spawnLoc, entityType);
                    if (entity instanceof LivingEntity living) {
                        if (!customName.isEmpty()) {
                            living.customName(Component.text(customName.replace('&', '§')));
                            living.setCustomNameVisible(true);
                        }
                        // 等級加成
                        final double levelMul = 1.0 + (level - 1) * 0.3;
                        final var maxHealthAttr = living.getAttribute(
                                org.bukkit.attribute.Attribute.MAX_HEALTH);
                        if (maxHealthAttr != null) {
                            maxHealthAttr.setBaseValue(maxHealthAttr.getBaseValue() * levelMul);
                            living.setHealth(maxHealthAttr.getBaseValue());
                        }
                        living.setRemoveWhenFarAway(false);
                        living.setPersistent(true);
                    }
                    ctx.instance.trackEntity(entity);
                } catch (final Exception e) {
                    this.plugin.getLogger().warning("[功能引擎] MOB_SPAWNER 生怪失敗: " + e.getMessage());
                }
            });
        }
    }

    private void execNpcSpawner(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        final World world = ctx.instance.instanceWorld();
        if (world == null) return;
        final String npcName = toString(func.functionOptions().get("npc-name"), "NPC");
        final Location loc = new Location(world,
                func.blockX() + 0.5, func.blockY() + 1, func.blockZ() + 0.5);
        // Citizens NPC — 需要 Citizens API，暫用 Villager 代替
        this.scheduler.runRegion(loc, task -> {
            final Villager villager = world.spawn(loc, Villager.class, v -> {
                v.customName(Component.text(npcName.replace('&', '§')));
                v.setCustomNameVisible(true);
                v.setAI(false);
                v.setInvulnerable(true);
                v.setSilent(true);
                v.setPersistent(false);
            });
            ctx.instance.trackEntity(villager);
        });
    }

    private void execBlockEditor(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        final World world = ctx.instance.instanceWorld();
        if (world == null) return;
        final String blockType = toString(func.functionOptions().get("block-type"), "STONE");
        final String action = this.normalizeBlockAction(toString(func.functionOptions().get("block-action"), "PLACE"));
        final int x = func.blockX(), y = func.blockY(), z = func.blockZ();

        final Location loc = new Location(world, x, y, z);
        this.scheduler.runRegion(loc, task -> {
            try {
                if ("REMOVE".equalsIgnoreCase(action)) {
                    world.getBlockAt(x, y, z).setType(Material.AIR);
                } else {
                    world.getBlockAt(x, y, z).setType(Material.valueOf(blockType.toUpperCase()));
                }
            } catch (final Exception e) {
                this.plugin.getLogger().warning("[功能引擎] BLOCK_EDITOR 失敗: " + e.getMessage());
            }
        });
    }

    private void execSoundPlayer(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                  final Player player) {
        final World world = ctx.instance.instanceWorld();
        if (world == null) return;
        final String soundName = toString(func.functionOptions().get("sound-name"), "ENTITY_EXPERIENCE_ORB_PICKUP");
        final float volume = (float) toDouble(func.functionOptions().get("volume"), 1.0);
        final float pitch = (float) toDouble(func.functionOptions().get("pitch"), 1.0);
        final Location loc = new Location(world,
                func.blockX() + 0.5, func.blockY() + 0.5, func.blockZ() + 0.5);
        try {
            final Sound sound = Sound.valueOf(soundName.toUpperCase());
            if (player != null) {
                player.playSound(loc, sound, volume, pitch);
            } else {
                world.playSound(loc, sound, volume, pitch);
            }
        } catch (final Exception ignored) {}
    }

    private void execRedstoneBlock(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        final World world = ctx.instance.instanceWorld();
        if (world == null) return;
        final int durationTicks = toInt(func.functionOptions().get("duration-ticks"), 20);
        final int x = func.blockX(), y = func.blockY(), z = func.blockZ();
        final Location loc = new Location(world, x, y, z);

        this.scheduler.runRegion(loc, task -> {
            final Material original = world.getBlockAt(x, y, z).getType();
            world.getBlockAt(x, y, z).setType(Material.REDSTONE_BLOCK);
            if (durationTicks > 0) {
                this.scheduler.runRegionDelayed(loc, restore ->
                        world.getBlockAt(x, y, z).setType(original), durationTicks);
            }
        });
    }

    private void execDoorController(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        final World world = ctx.instance.instanceWorld();
        if (world == null) return;
        final String action = this.normalizeDoorAction(toString(func.functionOptions().get("door-action"), "TOGGLE"));
        final int x = func.blockX(), y = func.blockY(), z = func.blockZ();
        final Location loc = new Location(world, x, y, z);

        this.scheduler.runRegion(loc, task -> {
            final Block block = world.getBlockAt(x, y, z);
            if (block.getBlockData() instanceof Openable door) {
                switch (action) {
                    case "LOCK" -> door.setOpen(false);
                    case "UNLOCK" -> door.setOpen(true);
                    case "TOGGLE" -> door.setOpen(!door.isOpen());
                }
                block.setBlockData(door);
                world.playSound(loc, door.isOpen()
                        ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 1f);
            }
        });
    }

    private void execHologram(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        final World world = ctx.instance.instanceWorld();
        if (world == null) return;
        final String text = toString(func.functionOptions().get("hologram-text"), "").replace('&', '§');
        final int durationTicks = toInt(func.functionOptions().get("duration-ticks"), -1);
        final Location loc = new Location(world,
                func.blockX() + 0.5, func.blockY() + 1.5, func.blockZ() + 0.5);

        this.scheduler.runRegion(loc, task -> {
            try {
                final TextDisplay display = world.spawn(loc, TextDisplay.class, d -> {
                    d.text(Component.text(text));
                    d.setBillboard(Display.Billboard.CENTER);
                    d.setBackgroundColor(Color.fromARGB(128, 0, 0, 0));
                    d.setSeeThrough(false);
                    d.setShadowed(true);
                    d.setGravity(false);
                    d.setPersistent(false);
                });
                ctx.hologramEntities.add(display);

                if (durationTicks > 0) {
                    this.scheduler.runRegionDelayed(loc, remove -> {
                        if (display.isValid()) display.remove();
                        ctx.hologramEntities.remove(display);
                    }, durationTicks);
                }
            } catch (final Exception e) {
                this.plugin.getLogger().warning("[功能引擎] HOLOGRAM 生成失敗: " + e.getMessage());
            }
        });
    }

    private void execMovingBlock(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        final World world = ctx.instance.instanceWorld();
        if (world == null) return;
        final int sx = func.blockX(), sy = func.blockY(), sz = func.blockZ();
        final int dx = toInt(func.functionOptions().get("dest-x"), sx);
        final int dy = toInt(func.functionOptions().get("dest-y"), sy);
        final int dz = toInt(func.functionOptions().get("dest-z"), sz);
        final int speedTicks = Math.max(1, toInt(func.functionOptions().get("speed-ticks"), 5));
        final int returnAfter = toInt(func.functionOptions().get("return-after"), 0);

        final Location srcLoc = new Location(world, sx, sy, sz);
        this.scheduler.runRegion(srcLoc, task -> {
            final Material mat = world.getBlockAt(sx, sy, sz).getType();
            world.getBlockAt(sx, sy, sz).setType(Material.AIR);

            final Location destLoc = new Location(world, dx, dy, dz);
            this.scheduler.runRegionDelayed(destLoc, place -> {
                world.getBlockAt(dx, dy, dz).setType(mat);

                if (returnAfter > 0) {
                    this.scheduler.runRegionDelayed(destLoc, ret -> {
                        world.getBlockAt(dx, dy, dz).setType(Material.AIR);
                        this.scheduler.runRegion(srcLoc, put ->
                                world.getBlockAt(sx, sy, sz).setType(mat));
                    }, returnAfter);
                }
            }, speedTicks);
        });
    }

    private void execMovingBlockCluster(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        // 簡化版：相當於半徑內所有非空氣方塊都移動
        final World world = ctx.instance.instanceWorld();
        if (world == null) return;
        final int sx = func.blockX(), sy = func.blockY(), sz = func.blockZ();
        final int dx = toInt(func.functionOptions().get("dest-x"), sx);
        final int dy = toInt(func.functionOptions().get("dest-y"), sy);
        final int dz = toInt(func.functionOptions().get("dest-z"), sz);
        final int radius = toInt(func.functionOptions().get("radius"), 3);
        final int speedTicks = Math.max(1, toInt(func.functionOptions().get("speed-ticks"), 5));

        final int offsetX = dx - sx, offsetY = dy - sy, offsetZ = dz - sz;
        final Location srcLoc = new Location(world, sx, sy, sz);

        this.scheduler.runRegion(srcLoc, task -> {
            // 收集方塊
            final List<int[]> blocks = new ArrayList<>();
            for (int x = sx - radius; x <= sx + radius; x++) {
                for (int y = sy - radius; y <= sy + radius; y++) {
                    for (int z = sz - radius; z <= sz + radius; z++) {
                        if (world.getBlockAt(x, y, z).getType() != Material.AIR) {
                            blocks.add(new int[]{x, y, z,
                                    world.getBlockAt(x, y, z).getType().ordinal()});
                        }
                    }
                }
            }
            // 清除原位置
            for (final int[] b : blocks) {
                world.getBlockAt(b[0], b[1], b[2]).setType(Material.AIR);
            }
            // 延遲放置到新位置
            final Location destLoc = new Location(world, dx, dy, dz);
            this.scheduler.runRegionDelayed(destLoc, place -> {
                for (final int[] b : blocks) {
                    try {
                        final Material mat = Material.values()[b[3]];
                        world.getBlockAt(b[0] + offsetX, b[1] + offsetY, b[2] + offsetZ).setType(mat);
                    } catch (final Exception ignored) {}
                }
            }, speedTicks);
        });
    }

    // ──────────────────────────────────────────────
    //  進階分類功能（META）— 串連核心
    // ──────────────────────────────────────────────

    private void execMythicSkill(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                  final Player player) {
        final String skillName = toString(func.functionOptions().get("skill-name"), "");
        if (skillName.isEmpty()) return;
        this.executeMythicSkillReflection(skillName,
                new Location(ctx.instance.instanceWorld(),
                        func.blockX() + 0.5, func.blockY() + 1, func.blockZ() + 0.5),
                player);
    }

    private void execMythicSignal(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        final String signalName = toString(func.functionOptions().get("signal-name"), "");
        final double radius = toDouble(func.functionOptions().get("signal-radius"), 16.0);
        if (signalName.isEmpty()) return;

        final World world = ctx.instance.instanceWorld();
        if (world == null) return;
        final Location center = new Location(world,
                func.blockX() + 0.5, func.blockY() + 0.5, func.blockZ() + 0.5);

        // 嘗試透過 MythicMobs API 發送信號
        this.sendMythicMobSignal(signalName, center, radius);
    }

    private void execCommandSender(final DungeonFunction func, final Player player) {
        final String command = toString(func.functionOptions().get("command"), "");
        if (command.isEmpty()) return;
        final boolean asConsole = toBool(func.functionOptions().get("as-console"), true);

        if (player != null) {
            final String resolved = command.replace("{player}", player.getName())
                    .replace("{uuid}", player.getUniqueId().toString());
            if (asConsole) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
            } else {
                player.performCommand(resolved);
            }
        }
    }

    /**
     * MULTI_FUNCTION — 同時執行多個功能（串連核心）。
     */
    private void execMultiFunction(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                    final Player player) {
        final List<String> ids = toStringList(func.functionOptions().get("function-ids"));
        for (final String id : ids) {
            final DungeonFunction target = ctx.byId.get(id.trim());
            if (target != null) {
                this.executeFunction(ctx, target, player);
            } else {
                this.plugin.getLogger().warning("[功能引擎] MULTI_FUNCTION 找不到功能: " + id);
            }
        }
    }

    /**
     * FUNCTION_REPEATER — 重複執行一個功能 N 次（帶間隔）。
     */
    private void execFunctionRepeater(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                       final Player player) {
        final String targetId = toString(func.functionOptions().get("function-id"), "");
        final int repeatCount = toInt(func.functionOptions().get("repeat-count"), 3);
        final int interval = toInt(func.functionOptions().get("interval-ticks"), 20);
        final DungeonFunction target = ctx.byId.get(targetId);
        if (target == null) return;

        for (int i = 0; i < repeatCount; i++) {
            final long delay = (long) i * interval;
            if (delay == 0) {
                this.executeFunction(ctx, target, player);
            } else {
                this.scheduler.runGlobalDelayed(task ->
                        this.executeFunction(ctx, target, player), delay);
            }
        }
    }

    /**
     * DELAYED_FUNCTION — 延遲後執行功能。
     */
    private void execDelayedFunction(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                      final Player player) {
        final String targetId = toString(func.functionOptions().get("function-id"), "");
        final int delay = toInt(func.functionOptions().get("delay-ticks"), 20);
        final DungeonFunction target = ctx.byId.get(targetId);
        if (target == null) return;

        this.scheduler.runGlobalDelayed(task ->
                this.executeFunction(ctx, target, player), Math.max(1, delay));
    }

    /**
     * FUNCTION_RANDOMIZER — 從列表中隨機選一個執行。
     */
    private void execFunctionRandomizer(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                         final Player player) {
        final List<String> ids = toStringList(func.functionOptions().get("function-ids"));
        if (ids.isEmpty()) return;
        final String picked = ids.get(ThreadLocalRandom.current().nextInt(ids.size()));
        final DungeonFunction target = ctx.byId.get(picked.trim());
        if (target != null) {
            this.executeFunction(ctx, target, player);
        }
    }

    /**
     * FUNCTION_SEQUENCER — 每次觸發依序執行列表中的下一個功能。
     */
    private void execFunctionSequencer(final FunctionRuntimeContext ctx, final DungeonFunction func,
                                        final Player player) {
        final List<String> ids = toStringList(func.functionOptions().get("function-ids"));
        if (ids.isEmpty()) return;
        final int idx = ctx.sequencerIndices.getOrDefault(func.id(), 0);
        final String targetId = ids.get(idx % ids.size());
        ctx.sequencerIndices.put(func.id(), idx + 1);
        final DungeonFunction target = ctx.byId.get(targetId.trim());
        if (target != null) {
            this.executeFunction(ctx, target, player);
        }
    }

    /**
     * DUNGEON_VARIABLE — 修改副本變數。
     */
    private void execDungeonVariable(final FunctionRuntimeContext ctx, final DungeonFunction func) {
        final String varName = toString(func.functionOptions().get("variable-name"), "my_var");
        final String action = this.normalizeVariableAction(toString(func.functionOptions().get("variable-action"), "SET"));
        final String valueStr = toString(func.functionOptions().get("variable-value"), "0");

        final Object current = ctx.instance.getVariable(varName);
        switch (action) {
            case "SET" -> ctx.instance.setVariable(varName, valueStr);
            case "ADD" -> {
                try {
                    final double cur = current != null ? Double.parseDouble(String.valueOf(current)) : 0;
                    ctx.instance.setVariable(varName, cur + Double.parseDouble(valueStr));
                } catch (final NumberFormatException e) {
                    ctx.instance.setVariable(varName, valueStr);
                }
            }
            case "SUBTRACT" -> {
                try {
                    final double cur = current != null ? Double.parseDouble(String.valueOf(current)) : 0;
                    ctx.instance.setVariable(varName, cur - Double.parseDouble(valueStr));
                } catch (final NumberFormatException e) {
                    ctx.instance.setVariable(varName, valueStr);
                }
            }
        }
    }

    private String normalizeLivesAction(final String raw) {
        return switch (raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT)) {
            case "ADD", "增加", "+" -> "ADD";
            case "REMOVE", "移除", "減少", "SUB", "SUBTRACT", "-" -> "REMOVE";
            case "SET", "設定", "=" -> "SET";
            default -> "ADD";
        };
    }

    private String normalizeBlockAction(final String raw) {
        return switch (raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT)) {
            case "REMOVE", "移除", "DELETE", "BREAK" -> "REMOVE";
            case "PLACE", "放置", "SET" -> "PLACE";
            default -> "PLACE";
        };
    }

    private String normalizeDoorAction(final String raw) {
        return switch (raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT)) {
            case "LOCK", "鎖定", "CLOSE" -> "LOCK";
            case "UNLOCK", "解鎖", "OPEN" -> "UNLOCK";
            case "TOGGLE", "切換", "SWITCH" -> "TOGGLE";
            default -> "TOGGLE";
        };
    }

    private String normalizeVariableAction(final String raw) {
        return switch (raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT)) {
            case "SET", "設定", "=" -> "SET";
            case "ADD", "增加", "+" -> "ADD";
            case "SUBTRACT", "SUB", "減少", "REMOVE", "-" -> "SUBTRACT";
            default -> "SET";
        };
    }

    private boolean evaluateMythicCondition(final FunctionRuntimeContext ctx,
                                             final Location center,
                                             final String conditionName) {
        if (conditionName == null || conditionName.isBlank()) {
            return false;
        }

        this.initMythicMobs();
        if (!this.mythicMobsAvailable) {
            return false;
        }

        String mobId = null;
        double radius = -1;
        final String normalized = conditionName.trim();
        final String lower = normalized.toLowerCase(Locale.ROOT);

        if (lower.startsWith("mob:") || lower.startsWith("id:")) {
            final int split = normalized.indexOf(':');
            String spec = split >= 0 && split + 1 < normalized.length()
                    ? normalized.substring(split + 1).trim()
                    : "";
            final int at = spec.indexOf('@');
            if (at >= 0) {
                final String radiusText = spec.substring(at + 1).trim();
                spec = spec.substring(0, at).trim();
                try {
                    radius = Double.parseDouble(radiusText);
                } catch (final NumberFormatException ignored) {
                    radius = -1;
                }
            }
            mobId = spec;
            if (mobId.isBlank()) {
                return false;
            }
        } else if (lower.startsWith("near:")) {
            final String radiusText = normalized.substring("near:".length()).trim();
            try {
                radius = Double.parseDouble(radiusText);
            } catch (final NumberFormatException ignored) {
                return false;
            }
        } else if (!"any".equalsIgnoreCase(normalized)
                && !"has_mythic_mob".equalsIgnoreCase(normalized)
                && !"has_mythic_mobs".equalsIgnoreCase(normalized)) {
            return false;
        }

        final Collection<?> activeMobs = this.getActiveMythicMobs();
        if (activeMobs.isEmpty()) {
            return false;
        }

        final World world = ctx.instance.instanceWorld();
        for (final Object activeMob : activeMobs) {
            final Entity entity = this.extractMythicBukkitEntity(activeMob);
            if (entity == null || world == null || !entity.getWorld().equals(world)) {
                continue;
            }

            if (center != null && radius > 0
                    && entity.getLocation().distanceSquared(center) > radius * radius) {
                continue;
            }

            if (mobId != null) {
                final String internalName = this.extractMythicInternalName(activeMob);
                if (internalName.isBlank() || !internalName.equalsIgnoreCase(mobId)) {
                    continue;
                }
            }

            return true;
        }

        return false;
    }

    private Collection<?> getActiveMythicMobs() {
        this.initMythicMobs();
        if (!this.mythicMobsAvailable || this.mythicBukkitInstance == null) {
            return List.of();
        }
        try {
            final Method getMobManager = this.mythicBukkitInstance.getClass().getMethod("getMobManager");
            final Object mobManager = getMobManager.invoke(this.mythicBukkitInstance);
            for (final Method method : mobManager.getClass().getMethods()) {
                if ("getActiveMobs".equals(method.getName()) && method.getParameterCount() == 0) {
                    final Object result = method.invoke(mobManager);
                    if (result instanceof Collection<?> collection) {
                        return collection;
                    }
                }
            }
        } catch (final Exception ignored) {
            // 版本差異時回傳空集合，視為條件不成立。
        }
        return List.of();
    }

    private Entity extractMythicBukkitEntity(final Object activeMob) {
        if (activeMob == null) {
            return null;
        }
        try {
            final Object abstractEntity = this.invokeNoArg(activeMob, "getEntity");
            if (abstractEntity == null) {
                return null;
            }
            final Object bukkit = this.invokeNoArg(abstractEntity, "getBukkitEntity");
            if (bukkit instanceof Entity entity) {
                return entity;
            }
        } catch (final Exception ignored) {
            return null;
        }
        return null;
    }

    private String extractMythicInternalName(final Object activeMob) {
        final Object type = this.invokeNoArg(activeMob, "getType");
        if (type != null) {
            for (final String method : List.of("getInternalName", "getConfigName", "getName")) {
                final Object name = this.invokeNoArg(type, method);
                if (name != null && !String.valueOf(name).isBlank()) {
                    return String.valueOf(name);
                }
            }
        }
        for (final String method : List.of("getMobType", "getTypeName", "getInternalName", "getName")) {
            final Object name = this.invokeNoArg(activeMob, method);
            if (name != null && !String.valueOf(name).isBlank()) {
                return String.valueOf(name);
            }
        }
        return "";
    }

    private Object invokeNoArg(final Object target, final String methodName) {
        if (target == null || methodName == null || methodName.isBlank()) {
            return null;
        }
        try {
            final Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (final Exception ignored) {
            return null;
        }
    }

    // ══════════════════════════════════════════════════
    //  MythicMobs 反射整合
    // ══════════════════════════════════════════════════

    /** 初始化 MythicMobs 反射（僅檢查一次）。 */
    private void initMythicMobs() {
        if (this.mythicMobsChecked) return;
        this.mythicMobsChecked = true;

        try {
            final Class<?> mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            final Method instMethod = mythicBukkitClass.getMethod("inst");
            this.mythicBukkitInstance = instMethod.invoke(null);

            this.mythicGetApiHelper = mythicBukkitClass.getMethod("getAPIHelper");
            final Object apiHelper = this.mythicGetApiHelper.invoke(this.mythicBukkitInstance);

            // 優先找 spawnMythicMob(String, Location, int)，其次 fallback 到 2 參數版本。
            for (final Method m : apiHelper.getClass().getMethods()) {
                if (!m.getName().equals("spawnMythicMob")) {
                    continue;
                }
                final Class<?>[] paramTypes = m.getParameterTypes();
                if (paramTypes.length == 3
                        && paramTypes[0] == String.class
                        && Location.class.isAssignableFrom(paramTypes[1])
                        && (paramTypes[2] == int.class || paramTypes[2] == Integer.class)) {
                    this.mythicSpawnMethod = m;
                    continue;
                }
                if (paramTypes.length == 2
                        && paramTypes[0] == String.class
                        && Location.class.isAssignableFrom(paramTypes[1])) {
                    this.mythicSpawnMethodNoLevel = m;
                }
            }

            this.mythicMobsAvailable = this.mythicSpawnMethod != null || this.mythicSpawnMethodNoLevel != null;
            if (this.mythicMobsAvailable) {
                this.plugin.getLogger().info("[功能引擎] MythicMobs API 已連接！");
            }
        } catch (final ClassNotFoundException e) {
            this.plugin.getLogger().info("[功能引擎] MythicMobs 未安裝，使用原版怪物。");
        } catch (final Exception e) {
            this.plugin.getLogger().warning("[功能引擎] MythicMobs API 連接失敗: " + e.getMessage());
        }
    }

    /**
     * 使用 MythicMobs API 生成怪物。
     * @return true 如果成功
     */
    boolean spawnMythicMob(final String mobId, final Location location, final int level) {
        this.initMythicMobs();
        if (!this.mythicMobsAvailable) return false;

        try {
            final Object apiHelper = this.mythicGetApiHelper.invoke(this.mythicBukkitInstance);
            if (this.mythicSpawnMethod != null) {
                this.mythicSpawnMethod.invoke(apiHelper, mobId, location, level);
            } else if (this.mythicSpawnMethodNoLevel != null) {
                this.mythicSpawnMethodNoLevel.invoke(apiHelper, mobId, location);
            } else {
                return false;
            }
            return true;
        } catch (final Exception e) {
            this.plugin.getLogger().warning("[功能引擎] MythicMobs 生成 '" + mobId + "' 失敗: " + e.getMessage());
            return false;
        }
    }

    /**
     * 生成 MythicMobs 怪物並回傳 Bukkit Entity（供副本波次追蹤用）。
     */
    Entity spawnMythicMobEntity(final String mobId, final Location location, final int level) {
        this.initMythicMobs();
        if (!this.mythicMobsAvailable) return null;

        try {
            final Object apiHelper = this.mythicGetApiHelper.invoke(this.mythicBukkitInstance);
            final Object spawnResult;
            if (this.mythicSpawnMethod != null) {
                spawnResult = this.mythicSpawnMethod.invoke(apiHelper, mobId, location, level);
            } else if (this.mythicSpawnMethodNoLevel != null) {
                spawnResult = this.mythicSpawnMethodNoLevel.invoke(apiHelper, mobId, location);
            } else {
                return null;
            }
            if (spawnResult == null) return null;
            if (spawnResult instanceof Entity entity) {
                return entity;
            }
            // 舊版 API 可能回傳 ActiveMob
            final Method getEntity = spawnResult.getClass().getMethod("getEntity");
            final Object abstractEntity = getEntity.invoke(spawnResult);
            if (abstractEntity == null) return null;
            final Method getBukkitEntity = abstractEntity.getClass().getMethod("getBukkitEntity");
            return (Entity) getBukkitEntity.invoke(abstractEntity);
        } catch (final Exception e) {
            this.plugin.getLogger().warning("[功能引擎] MythicMobs 生成 '" + mobId + "' (entity) 失敗: " + e.getMessage());
            return null;
        }
    }

    /**
     * 使用 MythicMobs API 執行技能。
     */
    private void executeMythicSkillReflection(final String skillName, final Location location,
                                               final Player target) {
        this.initMythicMobs();
        if (!this.mythicMobsAvailable) {
            this.plugin.getLogger().warning("[功能引擎] MYTHIC_SKILL 需要 MythicMobs 但未安裝。");
            return;
        }

        try {
            final Class<?> mythicBukkitClass = this.mythicBukkitInstance.getClass();
            final Method getSkillManager = mythicBukkitClass.getMethod("getSkillManager");
            final Object skillManager = getSkillManager.invoke(this.mythicBukkitInstance);

            // SkillManager.runSkill(String, Location, Player)
            for (final Method m : skillManager.getClass().getMethods()) {
                if (m.getName().equals("runSkill") || m.getName().equals("executeMechanic")) {
                    // 嘗試各種 API 版本
                    try {
                        m.invoke(skillManager, skillName, location, target);
                        return;
                    } catch (final Exception ignored) {}
                }
            }
            this.plugin.getLogger().warning("[功能引擎] 找不到 MythicMobs 技能執行方法。");
        } catch (final Exception e) {
            this.plugin.getLogger().warning("[功能引擎] MYTHIC_SKILL 執行失敗: " + e.getMessage());
        }
    }

    /**
     * 向 MythicMobs 怪物發送信號。
     */
    private void sendMythicMobSignal(final String signalName, final Location center, final double radius) {
        this.initMythicMobs();
        if (!this.mythicMobsAvailable) return;

        try {
            for (final Object activeMob : this.getActiveMythicMobs()) {
                final Entity entity = this.extractMythicBukkitEntity(activeMob);
                if (entity == null || entity.getLocation().distanceSquared(center) > radius * radius) {
                    continue;
                }
                try {
                    final Method signal = activeMob.getClass().getMethod("signal", String.class);
                    signal.invoke(activeMob, signalName);
                } catch (final Exception ignored) {
                    // 忽略單一怪物的 signal 失敗，繼續處理其他目標。
                }
            }
        } catch (final Exception e) {
            this.plugin.getLogger().warning("[功能引擎] MYTHIC_SIGNAL 發送失敗: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════
    //  公開查詢方法
    // ══════════════════════════════════════════════════

    /** 檢查是否有方塊控制器在指定位置（供事件攔截用）。 */
    public boolean isBlockControlled(final DungeonInstance instance, final int x, final int y, final int z,
                                      final boolean isPlace) {
        final FunctionRuntimeContext ctx = this.contexts.get(instance.instanceId());
        if (ctx == null) return false;

        for (final DungeonFunction func : ctx.functions) {
            if (func.functionType() == FunctionType.BLOCK_CONTROLLER) {
                final double dx = Math.abs(func.blockX() - x);
                final double dy = Math.abs(func.blockY() - y);
                final double dz = Math.abs(func.blockZ() - z);
                if (dx <= 5 && dy <= 5 && dz <= 5) {
                    if (isPlace) {
                        return !toBool(func.functionOptions().get("allow-place"), false);
                    } else {
                        return !toBool(func.functionOptions().get("allow-break"), false);
                    }
                }
            }
        }
        return false;
    }

    /** 是否已初始化。 */
    public boolean hasContext(final String instanceId) {
        return this.contexts.containsKey(instanceId);
    }

    // ══════════════════════════════════════════════════
    //  廣播輔助
    // ══════════════════════════════════════════════════

    private void broadcastToInstance(final DungeonInstance instance, final String message) {
        for (final UUID uuid : instance.members()) {
            final Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(Component.text(message));
        }
    }

    private void broadcastSound(final DungeonInstance instance, final Sound sound,
                                 final float volume, final float pitch) {
        for (final UUID uuid : instance.members()) {
            final Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.playSound(p.getLocation(), sound, volume, pitch);
        }
    }

    // ══════════════════════════════════════════════════
    //  類型轉換輔助
    // ══════════════════════════════════════════════════

    private static String toString(final Object obj, final String def) {
        if (obj == null) return def;
        return String.valueOf(obj);
    }

    private static int toInt(final Object obj, final int def) {
        if (obj == null) return def;
        if (obj instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(obj).trim()); } catch (final Exception e) { return def; }
    }

    private static double toDouble(final Object obj, final double def) {
        if (obj == null) return def;
        if (obj instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(obj).trim()); } catch (final Exception e) { return def; }
    }

    private static boolean toBool(final Object obj, final boolean def) {
        if (obj == null) return def;
        if (obj instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(obj).trim());
    }

    @SuppressWarnings("unchecked")
    private static List<String> toStringList(final Object obj) {
        if (obj instanceof List<?> list) {
            final List<String> result = new ArrayList<>();
            for (final Object item : list) {
                result.add(String.valueOf(item));
            }
            return result;
        }
        if (obj instanceof String s) {
            if (s.contains(",")) {
                return Arrays.asList(s.split(","));
            }
            return s.isEmpty() ? List.of() : List.of(s);
        }
        return List.of();
    }

    private static boolean compare(final double a, final double b, final String op) {
        return switch (op.trim()) {
            case ">=" -> a >= b;
            case "<=" -> a <= b;
            case ">" -> a > b;
            case "<" -> a < b;
            case "==" , "=" -> a == b;
            case "!=" -> a != b;
            default -> a >= b;
        };
    }
}
