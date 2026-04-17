package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.LocationKey;
import com.rui.techproject.util.SafeScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 互動式烹飪系統。
 * <p>
 * 玩家對營火 / 煙燻爐 / 高爐 / 熔爐右鍵放食材，
 * 食物會懸浮在方塊上方旋轉，透過 BossBar 與 Title 動畫顯示烹調進度，
 * 完成後產物掉落並觸發特效。
 */
public final class CookingService {

    // ── 色彩常量 ──
    private static final TextColor HEAT_COLOR    = TextColor.color(0xFF6B35);
    private static final TextColor COOK_COLOR    = TextColor.color(0xFFD166);
    private static final TextColor DONE_COLOR    = TextColor.color(0x7CFC9A);
    private static final TextColor PROGRESS_FILL = TextColor.color(0xFF9F43);
    private static final TextColor PROGRESS_EMPTY = TextColor.color(0x3D3D3D);
    private static final TextColor SUBTITLE_COLOR = TextColor.color(0xA8B2C1);

    // ── 烹飪階段 ──
    private static final String[] PHASE_LABELS = {
            "🔥 加熱中…",
            "🍳 烹調中…",
            "♨ 翻炒中…",
            "✦ 收汁中…",
            "✨ 即將完成…"
    };

    private static final Sound[] PHASE_SOUNDS = {
            Sound.BLOCK_FIRE_AMBIENT,
            Sound.BLOCK_FURNACE_FIRE_CRACKLE,
            Sound.ENTITY_GENERIC_BURN,
            Sound.BLOCK_LAVA_POP,
            Sound.BLOCK_AMETHYST_BLOCK_CHIME
    };

    private static final Set<Material> COOKING_STATIONS = Set.of(
            Material.CAMPFIRE, Material.SOUL_CAMPFIRE,
            Material.SMOKER, Material.BLAST_FURNACE, Material.FURNACE
    );

    private static final String COOKING_DISPLAY_TAG = "techproject_cooking_display";

    // ── 互動烹飪 ──
    private static final String[] INTERACTION_LABELS = {
            "\uD83D\uDD14 點擊翻面！",
            "\uD83D\uDD14 點擊攪拌！",
            "\uD83D\uDD14 點擊調味！"
    };
    private static final float[] INTERACTION_THRESHOLDS = {0.30f, 0.55f, 0.80f};
    private static final int INTERACTION_WINDOW_TICKS = 60; // 3 秒
    private static final int INTERACTION_SPEED_BONUS = 16;
    private static final Sound[] INTERACTION_CUES = {
            Sound.BLOCK_NOTE_BLOCK_BELL,
            Sound.BLOCK_NOTE_BLOCK_CHIME,
            Sound.BLOCK_NOTE_BLOCK_PLING
    };
    private static final String[] INTERACTION_SUCCESS = {"翻面成功！", "攪拌完成！", "調味完美！"};

    // ── 食材追加 ──
    private static final int INGREDIENT_WINDOW_TICKS = 80; // 4 秒加料窗口

    private static final String[] INGREDIENT_PROMPTS_ICON = {"🧂", "🫧", "✦"};

    // ── 記錄類 ──
    private record IngredientAddition(float progressThreshold, String requiredItemId,
                                      Material requiredMaterial, String prompt) {}

    private record CookingRecipe(String id, String inputId, Material inputMaterial,
                                 String outputId, int cookTimeTicks,
                                 String stationType, String displayName,
                                 boolean advanced, int perfectOutputCount,
                                 List<IngredientAddition> ingredientAdditions) {}

    private static final class CookingSession {
        final UUID playerId;
        final LocationKey location;
        final CookingRecipe recipe;
        final long startTick;
        final int totalTicks;
        UUID displayEntityId;
        BossBar bossBar;
        int ticksElapsed;
        int lastPhase = -1;
        ScheduledTask tickTask;
        boolean awaitingInteraction;
        int nextInteractionIndex;   // 下一個互動階段 (0→翻面, 1→攪拌, 2→調味)
        int interactionsCompleted;
        int interactionPromptTick;  // 互動提示出現的 tick
        int bonusTicks;             // 互動成功累計加速
        // ── 食材追加系統 ──
        int nextIngredientIndex;
        boolean awaitingIngredient;
        int ingredientPromptTick;
        int ingredientsAdded;

        CookingSession(UUID playerId, LocationKey location, CookingRecipe recipe,
                       long startTick, int totalTicks) {
            this.playerId = playerId;
            this.location = location;
            this.recipe = recipe;
            this.startTick = startTick;
            this.totalTicks = totalTicks;
        }
    }

    // ── 欄位 ──
    private final TechMCPlugin plugin;
    private final TechRegistry registry;
    private final ItemFactoryUtil itemFactory;
    private final SafeScheduler scheduler;
    private final Map<LocationKey, CookingSession> activeSessions = new ConcurrentHashMap<>();
    private final List<CookingRecipe> recipes = new ArrayList<>();

    public CookingService(final TechMCPlugin plugin,
                          final TechRegistry registry,
                          final ItemFactoryUtil itemFactory,
                          final SafeScheduler scheduler) {
        this.plugin = plugin;
        this.registry = registry;
        this.itemFactory = itemFactory;
        this.scheduler = scheduler;
        this.loadRecipes(plugin);
    }

    public void reloadRecipes() {
        this.loadRecipes(this.plugin);
    }

    // ══════════════════════ 公開 API ══════════════════════

    /**
     * 判斷材質是否為烹飪站。
     */
    public boolean isCookingStation(final Material material) {
        return material != null && COOKING_STATIONS.contains(material);
    }

    /**
     * 判斷方塊是否為烹飪站。
     */
    public boolean isCookingStation(final Block block) {
        return block != null && COOKING_STATIONS.contains(block.getType());
    }

    /**
     * 嘗試在烹飪站上開始烹調（從玩家手持物品推斷）。
     */
    public boolean tryStartCooking(final Player player, final Block block) {
        return this.tryStartCooking(player, block, player.getInventory().getItemInMainHand());
    }

    /**
     * 嘗試在烹飪站上開始烹調。
     * @return true 表示事件已處理（成功或拒絕），false 表示不是烹飪行為。
     */
    public boolean tryStartCooking(final Player player, final Block block, final ItemStack handItem) {
        if (!this.isCookingStation(block)) {
            return false;
        }

        // 互動烹飪：檢查待處理的翻面/攪拌/調味互動
        final LocationKey activeKey = LocationKey.from(block.getLocation());
        final CookingSession active = this.activeSessions.get(activeKey);
        if (active != null && active.playerId.equals(player.getUniqueId())) {
            // QTE 互動窗口
            if (active.awaitingInteraction) {
                this.handleInteraction(player, active, block.getLocation());
                return true;
            }
            // 食材追加窗口
            if (active.awaitingIngredient) {
                this.handleIngredientAddition(player, active, handItem, block.getLocation());
                return true;
            }
            // 非互動時間點擊 → 烹調失敗（防止按住右鍵完美烹飪）
            this.failCooking(player, active, activeKey, block.getLocation());
            return true;
        }

        if (handItem == null || handItem.getType().isAir()) {
            // 空手右鍵：如果正在烹調，顯示進度
            if (active != null) {
                final int effectiveTicks = Math.max(1, active.totalTicks - active.bonusTicks);
                final float progress = Math.min(1f, (float) active.ticksElapsed / effectiveTicks);
                final int pct = (int) (progress * 100);
                player.sendActionBar(Component.text("烹調進度：" + pct + "% — " + active.recipe.displayName, COOK_COLOR));
                return true;
            }
            return false;
        }

        if (active != null) {
            player.sendActionBar(Component.text("這個烹飪台正在使用中！", TextColor.color(0xFF7B7B)));
            return true;
        }

        // 尋找匹配配方
        final CookingRecipe recipe = this.findRecipe(handItem, block.getType());
        if (recipe == null) {
            return false; // 不是可烹調的食材，讓事件繼續
        }

        // 扣除食材
        if (handItem.getAmount() > 1) {
            handItem.setAmount(handItem.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        // 啟動烹調
        this.startSession(player, block, recipe);
        return true;
    }

    /**
     * 方塊被破壞時中斷烹調。
     */
    public void interruptCooking(final Block block) {
        final LocationKey key = LocationKey.from(block.getLocation());
        final CookingSession session = this.activeSessions.remove(key);
        if (session == null) {
            return;
        }
        this.cleanupSession(session, block.getLocation(), true);
    }

    /** 別名：方塊被破壞時中斷烹調。 */
    public void interruptByBlockBreak(final Block block) {
        this.interruptCooking(block);
    }

    /**
     * 玩家斷線時清理。
     */
    public void cleanupPlayer(final UUID playerId) {
        final var iterator = this.activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            final var entry = iterator.next();
            if (entry.getValue().playerId.equals(playerId)) {
                final CookingSession session = entry.getValue();
                iterator.remove();
                final World world = Bukkit.getWorld(session.location.worldName());
                if (world != null) {
                    final Location loc = new Location(world, session.location.x(), session.location.y(), session.location.z());
                    this.cleanupSession(session, loc, true);
                }
            }
        }
    }

    /** 別名：清理玩家。 */
    public void cancelSession(final UUID playerId) {
        this.cleanupPlayer(playerId);
    }

    /**
     * 伺服器關閉時清除所有烹調。
     */
    public void shutdown() {
        for (final var entry : this.activeSessions.entrySet()) {
            final CookingSession session = entry.getValue();
            if (session.tickTask != null) {
                session.tickTask.cancel();
            }
            this.removeDisplayEntity(session);
            final Player player = Bukkit.getPlayer(session.playerId);
            if (player != null && session.bossBar != null) {
                player.hideBossBar(session.bossBar);
            }
        }
        this.activeSessions.clear();
        // Folia: onDisable 在主控台線程，無法存取區域實體 (getEntities 會拋異常)
        // 伺服器即將關閉，Display 實體會自動消失，不需要手動清理
        // 下次啟動時 purgeOrphanedDisplays() 會清理殘留
    }

    /**
     * 啟動時清理前次熱插拔遺留的孤兒 ItemDisplay。
     */
    public void purgeOrphanedDisplays() {
        this.removeOrphanedCookingDisplays();
    }

    private void removeOrphanedCookingDisplays() {
        for (final World world : Bukkit.getWorlds()) {
            for (final Entity entity : world.getEntities()) {
                if (entity instanceof ItemDisplay) {
                    this.scheduler.runEntity(entity, () -> {
                        try {
                            if (entity.getScoreboardTags().contains(COOKING_DISPLAY_TAG)) {
                                entity.remove();
                            }
                        } catch (final Exception ignored) {
                        }
                    });
                }
            }
        }
    }

    // ══════════════════════ 烹調邏輯 ══════════════════════

    private void startSession(final Player player, final Block block, final CookingRecipe recipe) {
        final LocationKey key = LocationKey.from(block.getLocation());
        final int cookTicks = recipe.cookTimeTicks;
        final CookingSession session = new CookingSession(
                player.getUniqueId(), key, recipe,
                block.getWorld().getFullTime(), cookTicks
        );

        // 建立懸浮食物展示
        final Location displayLoc = block.getLocation().clone().add(0.5, 1.15, 0.5);
        this.spawnFoodDisplay(session, displayLoc, recipe);

        // 建立 BossBar
        session.bossBar = BossBar.bossBar(
                Component.text("🔥 " + recipe.displayName + " — 加熱中…", HEAT_COLOR),
                0.0f,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
        );
        player.showBossBar(session.bossBar);

        // 開場 Title（打字機）
        this.plugin.getTitleMsgService().send(player,
                Component.text("🔥", HEAT_COLOR),
                Component.text("開始烹調 " + recipe.displayName, SUBTITLE_COLOR),
                16L, Sound.BLOCK_NOTE_BLOCK_HAT);

        // 開場音效
        player.playSound(block.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.6f, 1.0f);
        player.playSound(block.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 0.4f, 1.3f);

        this.activeSessions.put(key, session);

        // 啟動 tick 計時器（每 4 ticks 一次）
        session.tickTask = this.scheduler.runRegionTimer(
                block.getLocation(),
                task -> this.tickSession(key, task),
                4L, 4L
        );
    }

    private void tickSession(final LocationKey key, final ScheduledTask task) {
        final CookingSession session = this.activeSessions.get(key);
        if (session == null) {
            task.cancel();
            return;
        }

        session.ticksElapsed += 4;
        final int effectiveTotalTicks = Math.max(1, session.totalTicks - session.bonusTicks);
        float progress = Math.min(1.0f, (float) session.ticksElapsed / effectiveTotalTicks);
        // 若還有未觸發的互動步驟，將進度條限制在下一個觸發閾值之前，
        // 避免加速後進度直接跳過互動點
        if (session.nextInteractionIndex < INTERACTION_THRESHOLDS.length) {
            progress = Math.min(progress, INTERACTION_THRESHOLDS[session.nextInteractionIndex] + 0.02f);
        }
        // 互動或加料等待中時，進度鎖在 99% 以內，防止看起來已完成
        if (session.awaitingInteraction || session.awaitingIngredient) {
            progress = Math.min(progress, 0.99f);
        }
        final int phaseIndex = Math.min(PHASE_LABELS.length - 1,
                (int) (progress * PHASE_LABELS.length));

        final Player player = Bukkit.getPlayer(session.playerId);
        final World world = Bukkit.getWorld(key.worldName());

        // ── 互動提示系統 ──
        if (session.awaitingInteraction) {
            // 檢查互動窗口是否過期
            if (session.ticksElapsed - session.interactionPromptTick >= INTERACTION_WINDOW_TICKS) {
                session.awaitingInteraction = false;
                if (player != null) {
                    player.sendActionBar(Component.text("⏳ 互動超時", TextColor.color(0x888888)));
                    player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 0.8f);
                }
            } else if (player != null) {
                // 閃爍 ActionBar 提示
                final int idx = session.nextInteractionIndex - 1;
                final boolean flash = (session.ticksElapsed / 4) % 2 == 0;
                final TextColor promptColor = flash ? TextColor.color(0xFFE066) : TextColor.color(0xFF9F43);
                player.sendActionBar(Component.text("▶ " + INTERACTION_LABELS[idx] + " ◀", promptColor)
                        .decoration(TextDecoration.BOLD, true));
            }
        } else if (session.nextInteractionIndex < INTERACTION_THRESHOLDS.length) {
            // 檢查是否到達下一個互動觸發點
            if (progress >= INTERACTION_THRESHOLDS[session.nextInteractionIndex]) {
                session.awaitingInteraction = true;
                session.interactionPromptTick = session.ticksElapsed;
                final int idx = session.nextInteractionIndex;
                session.nextInteractionIndex++;
                if (player != null) {
                    this.plugin.getTitleMsgService().send(player,
                            Component.text(INTERACTION_LABELS[idx].substring(0, 2),
                                    TextColor.color(0xFFE066)).decoration(TextDecoration.BOLD, true),
                            Component.text(INTERACTION_LABELS[idx].substring(2).trim(),
                                    TextColor.color(0xFFF4C2)),
                            16L, Sound.BLOCK_NOTE_BLOCK_HAT);
                    player.playSound(player.getLocation(), INTERACTION_CUES[idx], 0.8f, 1.2f);
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 2.0f);
                }
                // 互動提示粒子環
                if (world != null) {
                    final Location ringLoc = new Location(world, key.x() + 0.5, key.y() + 1.3, key.z() + 0.5);
                    for (int i = 0; i < 12; i++) {
                        final double angle = Math.toRadians(i * 30);
                        final Location pt = ringLoc.clone().add(Math.cos(angle) * 0.4, 0, Math.sin(angle) * 0.4);
                        world.spawnParticle(Particle.END_ROD, pt, 1, 0, 0.05, 0, 0.01);
                    }
                }
            }
        }

        // ── 食材追加提示系統（僅進階配方） ──
        if (!session.awaitingInteraction && session.recipe.advanced
                && session.recipe.ingredientAdditions != null) {
            final List<IngredientAddition> additions = session.recipe.ingredientAdditions;
            if (session.awaitingIngredient) {
                // 檢查食材追加窗口是否過期
                if (session.ticksElapsed - session.ingredientPromptTick >= INGREDIENT_WINDOW_TICKS) {
                    session.awaitingIngredient = false;
                    if (player != null) {
                        player.sendActionBar(Component.text("⏳ 加料超時", TextColor.color(0x888888)));
                        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.3f, 0.8f);
                    }
                } else if (player != null && !session.awaitingInteraction) {
                    // 閃爍食材追加提示
                    final int idx = session.nextIngredientIndex - 1;
                    if (idx >= 0 && idx < additions.size()) {
                        final boolean flash = (session.ticksElapsed / 4) % 2 == 0;
                        final TextColor promptColor = flash ? TextColor.color(0x88EEFF) : TextColor.color(0x44BBDD);
                        final String icon = idx < INGREDIENT_PROMPTS_ICON.length ? INGREDIENT_PROMPTS_ICON[idx] : "🧂";
                        player.sendActionBar(Component.text("▶ " + icon + " " + additions.get(idx).prompt + " ◀", promptColor)
                                .decoration(TextDecoration.BOLD, true));
                    }
                }
            } else if (session.nextIngredientIndex < additions.size()) {
                // 檢查是否到達下一個食材追加觸發點
                if (progress >= additions.get(session.nextIngredientIndex).progressThreshold) {
                    session.awaitingIngredient = true;
                    session.ingredientPromptTick = session.ticksElapsed;
                    final IngredientAddition addition = additions.get(session.nextIngredientIndex);
                    session.nextIngredientIndex++;
                    if (player != null) {
                        this.plugin.getTitleMsgService().send(player,
                                Component.text("🧂", TextColor.color(0x88EEFF)).decoration(TextDecoration.BOLD, true),
                                Component.text(addition.prompt, TextColor.color(0xCCF0FF)),
                                16L, Sound.BLOCK_NOTE_BLOCK_HAT);
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 0.8f, 1.4f);
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.8f);
                    }
                    // 食材追加粒子環
                    if (world != null) {
                        final Location ringLoc = new Location(world, key.x() + 0.5, key.y() + 1.3, key.z() + 0.5);
                        for (int i = 0; i < 12; i++) {
                            final double angle = Math.toRadians(i * 30);
                            final Location pt = ringLoc.clone().add(Math.cos(angle) * 0.4, 0, Math.sin(angle) * 0.4);
                            world.spawnParticle(Particle.SOUL_FIRE_FLAME, pt, 1, 0, 0.05, 0, 0.01);
                        }
                    }
                }
            }
        }

        // ── 更新 BossBar ──
        final TextColor barColor = progress < 0.5f ? HEAT_COLOR : (progress < 0.9f ? COOK_COLOR : DONE_COLOR);
        final String interactHint = session.awaitingInteraction ? " ⚡" : (session.awaitingIngredient ? " 🧂" : "");
        session.bossBar.name(Component.text(
                this.buildProgressBar(progress) + " " + PHASE_LABELS[phaseIndex] + " " +
                        session.recipe.displayName + " " + (int) (progress * 100) + "%" + interactHint,
                barColor
        ));
        session.bossBar.progress(progress);
        session.bossBar.color(progress < 0.5f ? BossBar.Color.RED
                : (progress < 0.9f ? BossBar.Color.YELLOW : BossBar.Color.GREEN));

        // ── 粒子效果 ──
        if (world != null) {
            final Location particleLoc = new Location(world, key.x() + 0.5, key.y() + 1.3, key.z() + 0.5);
            final Location fireLoc = particleLoc.clone().add(0, -0.3, 0);

            if (progress < 0.3f) {
                // 加熱階段：煙霧 + 小火焰
                world.spawnParticle(Particle.SMOKE, particleLoc, 3, 0.15, 0.1, 0.15, 0.01);
                world.spawnParticle(Particle.FLAME, fireLoc, 2, 0.1, 0.05, 0.1, 0.005);
            } else if (progress < 0.6f) {
                // 烹調階段：營火煙 + 蒸氣 + 油星
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, particleLoc, 1, 0.08, 0.1, 0.08, 0.01);
                world.spawnParticle(Particle.CLOUD, particleLoc.clone().add(0, 0.2, 0), 1, 0.06, 0.08, 0.06, 0.005);
                world.spawnParticle(Particle.LAVA, particleLoc, 1, 0.1, 0.05, 0.1, 0.0);
                // 偶爾油爆粒子
                if (session.ticksElapsed % 16 == 0) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, particleLoc, 4, 0.15, 0.1, 0.15, 0.08);
                }
            } else if (progress < 0.85f) {
                // 收汁階段：金色光點 + 蒸氣
                world.spawnParticle(Particle.HAPPY_VILLAGER, particleLoc, 2, 0.2, 0.15, 0.2, 0.0);
                world.spawnParticle(Particle.WAX_ON, particleLoc, 2, 0.15, 0.1, 0.15, 0.0);
                world.spawnParticle(Particle.CLOUD, particleLoc.clone().add(0, 0.3, 0), 1, 0.04, 0.06, 0.04, 0.003);
            } else {
                // 完成階段：璀璨星光
                world.spawnParticle(Particle.HAPPY_VILLAGER, particleLoc, 3, 0.2, 0.15, 0.2, 0.0);
                world.spawnParticle(Particle.WAX_ON, particleLoc, 3, 0.15, 0.1, 0.15, 0.0);
                world.spawnParticle(Particle.END_ROD, particleLoc, 1, 0.1, 0.15, 0.1, 0.02);
            }

            // 互動等待期間額外粒子（旋轉光環）
            if (session.awaitingInteraction && session.ticksElapsed % 8 == 0) {
                for (int i = 0; i < 6; i++) {
                    final double angle = Math.toRadians(i * 60 + session.ticksElapsed * 6);
                    final Location pt = particleLoc.clone().add(Math.cos(angle) * 0.35, 0.05, Math.sin(angle) * 0.35);
                    world.spawnParticle(Particle.END_ROD, pt, 1, 0, 0, 0, 0.0);
                }
            }

            // 旋轉食物展示
            this.rotateFoodDisplay(session, world);
        }

        // ── 階段切換 Title（非互動期間）──
        if (player != null && phaseIndex != session.lastPhase && !session.awaitingInteraction) {
            session.lastPhase = phaseIndex;
            if (phaseIndex > 0) {
                this.plugin.getTitleMsgService().send(player,
                        Component.text(PHASE_LABELS[phaseIndex].substring(0, 2), barColor)
                                .decoration(TextDecoration.BOLD, true),
                        Component.text(PHASE_LABELS[phaseIndex].substring(2).trim(), SUBTITLE_COLOR),
                        12L, Sound.BLOCK_NOTE_BLOCK_HAT);
                player.playSound(player.getLocation(), PHASE_SOUNDS[phaseIndex], 0.45f, 1.0f + phaseIndex * 0.1f);
            }

            player.sendActionBar(Component.text(
                    this.buildProgressBar(progress) + " " + session.recipe.displayName + " " + (int) (progress * 100) + "%",
                    barColor
            ));
        }

        // ── 烹調環境音效 ──
        if (player != null && session.ticksElapsed % 20 == 0) {
            player.playSound(new Location(world, key.x() + 0.5, key.y(), key.z() + 0.5),
                    Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.25f, 0.9f + (float) (Math.random() * 0.3));
        }

        // ── 完成 ──
        // 若有待處理的互動或食材追加，暫緩完成（讓玩家有機會完成第3次互動）
        if (session.ticksElapsed >= effectiveTotalTicks
                && !session.awaitingInteraction && !session.awaitingIngredient) {
            task.cancel();
            this.completeCooking(key);
        }
    }

    private void completeCooking(final LocationKey key) {
        final CookingSession session = this.activeSessions.remove(key);
        if (session == null) {
            return;
        }

        final World world = Bukkit.getWorld(key.worldName());
        final Location loc = world != null
                ? new Location(world, key.x() + 0.5, key.y() + 1.2, key.z() + 0.5)
                : null;

        // 建立產物
        final ItemStack output = this.buildOutputItem(session.recipe);
        final boolean perfect = session.interactionsCompleted >= 3;
        // 進階配方：完美烹飪需要 QTE 全過 + 食材全加
        final int totalIngredients = session.recipe.ingredientAdditions != null
                ? session.recipe.ingredientAdditions.size() : 0;
        final boolean perfectAdvanced = session.recipe.advanced && perfect
                && session.ingredientsAdded >= totalIngredients;

        final int outputCount;
        if (perfectAdvanced && session.recipe.perfectOutputCount > 1) {
            outputCount = session.recipe.perfectOutputCount;
        } else {
            outputCount = 1;
        }

        final String bonusText;
        if (session.recipe.advanced) {
            bonusText = " (互動 " + session.interactionsCompleted + "/3, 加料 "
                    + session.ingredientsAdded + "/" + totalIngredients + ")";
        } else {
            bonusText = session.interactionsCompleted > 0
                    ? " (互動 " + session.interactionsCompleted + "/3)" : "";
        }

        // 掉落物品
        if (loc != null && world != null) {
            for (int i = 0; i < outputCount; i++) {
                world.dropItemNaturally(loc, this.buildOutputItem(session.recipe));
            }

            // 完成特效 — 根據互動完成數增強
            final boolean showPerfect = perfectAdvanced || (!session.recipe.advanced && perfect);
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, showPerfect ? 40 : 25, 0.3, 0.5, 0.3, 0.1);
            world.spawnParticle(Particle.HAPPY_VILLAGER, loc, showPerfect ? 20 : 12, 0.3, 0.35, 0.3, 0.0);
            world.spawnParticle(Particle.END_ROD, loc, showPerfect ? 15 : 6, 0.2, 0.4, 0.2, 0.05);
            world.spawnParticle(Particle.WAX_ON, loc, 10, 0.25, 0.3, 0.25, 0.0);
            if (showPerfect) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 20, 0.3, 0.4, 0.3, 0.08);
            }
            world.playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
            world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.5f);
            world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.2f);
            if (showPerfect) {
                world.playSound(loc, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            }
        }

        // 玩家通知
        final Player player = Bukkit.getPlayer(session.playerId);
        if (player != null) {
            final boolean showPerfect = perfectAdvanced || (!session.recipe.advanced && perfect);
            final Component titleText = showPerfect
                    ? Component.text("\uD83C\uDF1F", TextColor.color(0xFFD700)).decoration(TextDecoration.BOLD, true)
                    : Component.text("✅", DONE_COLOR).decoration(TextDecoration.BOLD, true);
            final String subtitleMsg;
            if (perfectAdvanced) {
                subtitleMsg = "完美烹調！ " + session.recipe.displayName + " ×" + outputCount;
            } else if (showPerfect) {
                subtitleMsg = "完美烹調！ " + session.recipe.displayName;
            } else {
                subtitleMsg = session.recipe.displayName + " 烹調完成！" + bonusText;
            }
            final Component subtitleText = showPerfect
                    ? Component.text(subtitleMsg, TextColor.color(0xFFD700))
                    : Component.text(subtitleMsg, DONE_COLOR);

            this.plugin.getTitleMsgService().send(player, titleText, subtitleText,
                    30L, Sound.BLOCK_NOTE_BLOCK_HAT);
            player.sendActionBar(Component.text("🍽 " + session.recipe.displayName + " 已完成烹調" + bonusText, DONE_COLOR));
            player.hideBossBar(session.bossBar);

            // 成就追蹤
            this.plugin.getPlayerProgressService().incrementStat(player.getUniqueId(), "meals_cooked", 1);
            this.plugin.getAchievementService().evaluate(player.getUniqueId());
        }

        this.removeDisplayEntity(session);
    }

    // ══════════════════════ 互動烹飪處理 ══════════════════════

    private void handleInteraction(final Player player, final CookingSession session, final Location blockLoc) {
        session.awaitingInteraction = false;
        session.interactionsCompleted++;
        session.bonusTicks += INTERACTION_SPEED_BONUS;

        final int idx = session.nextInteractionIndex - 1;
        final String successMsg = idx >= 0 && idx < INTERACTION_SUCCESS.length
                ? INTERACTION_SUCCESS[idx] : "操作成功！";

        // 成功 Title（打字機）
        this.plugin.getTitleMsgService().send(player,
                Component.text("✓", DONE_COLOR).decoration(TextDecoration.BOLD, true),
                Component.text(successMsg, COOK_COLOR),
                10L, Sound.BLOCK_NOTE_BLOCK_HAT);

        // 音效
        player.playSound(blockLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
        player.playSound(blockLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.8f);
        player.playSound(blockLoc, Sound.ENTITY_PLAYER_BURP, 0.3f, 1.3f);

        // 爆發粒子
        final World world = blockLoc.getWorld();
        if (world != null) {
            final Location loc = blockLoc.clone().add(0.5, 1.3, 0.5);
            world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 18, 0.25, 0.2, 0.25, 0.15);
            world.spawnParticle(Particle.FLAME, loc, 10, 0.15, 0.1, 0.15, 0.05);
            world.spawnParticle(Particle.WAX_ON, loc, 12, 0.2, 0.15, 0.2, 0.0);
            world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 6, 0.2, 0.1, 0.2, 0.0);
        }

        // 翻轉食物展示
        this.flipFoodDisplay(session);

        // ActionBar 加速提示
        player.sendActionBar(Component.text("⚡ " + successMsg + " 烹調加速！", DONE_COLOR));
    }

    private void flipFoodDisplay(final CookingSession session) {
        if (session.displayEntityId == null) {
            return;
        }
        final Entity entity = Bukkit.getEntity(session.displayEntityId);
        if (!(entity instanceof ItemDisplay display)) {
            return;
        }
        // 翻轉動畫：放大 + 旋轉 180°
        final float currentAngle = (session.ticksElapsed * 4.5f) % 360f;
        display.setTransformation(new Transformation(
                new Vector3f(0, 0.3f, 0),
                new AxisAngle4f((float) Math.toRadians(currentAngle + 180), 0, 1, 0),
                new Vector3f(0.85f, 0.85f, 0.85f),
                new AxisAngle4f((float) Math.toRadians(360), 1, 0, 0)
        ));
        display.setInterpolationDuration(5);
        display.setInterpolationDelay(0);
    }

    // ══════════════════════ 烹調失敗（非互動時間點擊） ══════════════════════

    private void failCooking(final Player player, final CookingSession session,
                             final LocationKey key, final Location blockLoc) {
        this.activeSessions.remove(key);
        if (session.tickTask != null) {
            session.tickTask.cancel();
        }
        if (session.bossBar != null) {
            player.hideBossBar(session.bossBar);
        }
        this.removeDisplayEntity(session);

        // 失敗 Title
        this.plugin.getTitleMsgService().send(player,
                Component.text("✖", TextColor.color(0xFF4444)).decoration(TextDecoration.BOLD, true),
                Component.text("烹調失敗！非互動時間請勿操作烹飪台", TextColor.color(0xFF7B7B)),
                30L, Sound.BLOCK_NOTE_BLOCK_HAT);
        player.sendActionBar(Component.text("❌ " + session.recipe.displayName + " 燒焦了…", TextColor.color(0xFF4444)));
        player.playSound(blockLoc, Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 0.6f);
        player.playSound(blockLoc, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);

        // 燒焦粒子
        final World world = blockLoc.getWorld();
        if (world != null) {
            final Location loc = blockLoc.clone().add(0.5, 1.2, 0.5);
            world.spawnParticle(Particle.LARGE_SMOKE, loc, 20, 0.3, 0.4, 0.3, 0.05);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 8, 0.2, 0.3, 0.2, 0.02);
        }
    }

    // ══════════════════════ 食材追加處理 ══════════════════════

    private void handleIngredientAddition(final Player player, final CookingSession session,
                                          final ItemStack handItem, final Location blockLoc) {
        final List<IngredientAddition> additions = session.recipe.ingredientAdditions;
        if (session.nextIngredientIndex <= 0 || session.nextIngredientIndex > additions.size()) {
            session.awaitingIngredient = false;
            return;
        }
        final IngredientAddition required = additions.get(session.nextIngredientIndex - 1);

        // 檢查玩家手持物品是否匹配
        boolean match = false;
        if (required.requiredItemId != null && !required.requiredItemId.isEmpty()) {
            final String techId = this.itemFactory.getTechItemId(handItem);
            match = required.requiredItemId.equalsIgnoreCase(techId);
        }
        if (!match && required.requiredMaterial != null && handItem != null
                && handItem.getType() == required.requiredMaterial) {
            match = true;
        }

        if (!match) {
            // 錯誤的材料
            player.sendActionBar(Component.text("❌ 需要的材料不對！" + required.prompt, TextColor.color(0xFF7B7B)));
            player.playSound(blockLoc, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.2f);
            return;
        }

        // 消耗材料
        if (handItem.getAmount() > 1) {
            handItem.setAmount(handItem.getAmount() - 1);
        } else {
            player.getInventory().setItemInMainHand(null);
        }

        session.awaitingIngredient = false;
        session.ingredientsAdded++;
        session.bonusTicks += INTERACTION_SPEED_BONUS;

        // 成功提示
        this.plugin.getTitleMsgService().send(player,
                Component.text("✓", DONE_COLOR).decoration(TextDecoration.BOLD, true),
                Component.text("加料成功！" + required.prompt, COOK_COLOR),
                10L, Sound.BLOCK_NOTE_BLOCK_HAT);
        player.playSound(blockLoc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
        player.playSound(blockLoc, Sound.ITEM_BOTTLE_FILL, 0.5f, 1.3f);

        // 食材追加粒子
        final World world = blockLoc.getWorld();
        if (world != null) {
            final Location loc = blockLoc.clone().add(0.5, 1.3, 0.5);
            world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 12, 0.2, 0.15, 0.2, 0.0);
            world.spawnParticle(Particle.WAX_ON, loc, 8, 0.15, 0.1, 0.15, 0.0);
        }

        player.sendActionBar(Component.text("🧂 " + required.prompt + " 烹調加速！", DONE_COLOR));
    }

    // ══════════════════════ 食物展示實體 ══════════════════════

    private void spawnFoodDisplay(final CookingSession session, final Location location, final CookingRecipe recipe) {
        final World world = location.getWorld();
        if (world == null) {
            return;
        }

        world.spawn(location, ItemDisplay.class, display -> {
            final ItemStack displayItem;
            if (recipe.inputMaterial != null && recipe.inputMaterial != Material.AIR) {
                displayItem = new ItemStack(recipe.inputMaterial);
            } else {
                displayItem = this.buildOutputItem(recipe);
            }
            display.setItemStack(displayItem);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.GROUND);
            display.setBillboard(Display.Billboard.CENTER);
            display.setTransformation(new Transformation(
                    new Vector3f(0, 0.1f, 0),
                    new AxisAngle4f(0, 0, 1, 0),
                    new Vector3f(0.65f, 0.65f, 0.65f),
                    new AxisAngle4f(0, 0, 1, 0)
            ));
            display.setGlowing(true);
            display.setGlowColorOverride(Color.fromRGB(0xFF6B35));
            display.setPersistent(false);
            display.addScoreboardTag(COOKING_DISPLAY_TAG);
            session.displayEntityId = display.getUniqueId();
        });
    }

    private void rotateFoodDisplay(final CookingSession session, final World world) {
        if (session.displayEntityId == null) {
            return;
        }
        final Entity entity = Bukkit.getEntity(session.displayEntityId);
        if (!(entity instanceof ItemDisplay display)) {
            return;
        }
        final int effectiveTotal = Math.max(1, session.totalTicks - session.bonusTicks);
        final float progress = Math.min(1f, (float) session.ticksElapsed / effectiveTotal);

        // 旋轉速度隨進度加快
        final float speedMultiplier = 1.0f + progress * 1.5f;
        final float angle = (session.ticksElapsed * 4.5f * speedMultiplier) % 360f;
        final float bobY = (float) (Math.sin(session.ticksElapsed * 0.15) * 0.04);

        // 互動等待期間脈動效果
        final float scale;
        if (session.awaitingInteraction) {
            final float pulse = (float) (Math.sin(session.ticksElapsed * 0.4) * 0.08);
            scale = 0.65f + pulse;
        } else {
            scale = 0.65f + progress * 0.1f;  // 隨進度略微放大
        }

        // 高度隨進度略微上升
        final float baseY = 0.1f + progress * 0.08f;

        display.setTransformation(new Transformation(
                new Vector3f(0, baseY + bobY, 0),
                new AxisAngle4f((float) Math.toRadians(angle), 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)
        ));

        // 更新發光顏色
        if (session.awaitingInteraction) {
            // 互動等待：金色閃爍
            final boolean flash = (session.ticksElapsed / 4) % 2 == 0;
            display.setGlowColorOverride(flash ? Color.fromRGB(0xFFE066) : Color.fromRGB(0xFF9F43));
        } else if (progress > 0.85f) {
            display.setGlowColorOverride(Color.fromRGB(0x7CFC9A));
        } else if (progress > 0.6f) {
            display.setGlowColorOverride(Color.fromRGB(0xFFD166));
        } else if (progress > 0.3f) {
            display.setGlowColorOverride(Color.fromRGB(0xFF8C42));
        }
    }

    private void removeDisplayEntity(final CookingSession session) {
        if (session.displayEntityId == null) {
            return;
        }
        final Entity entity = Bukkit.getEntity(session.displayEntityId);
        if (entity != null) {
            try {
                this.scheduler.runEntity(entity, entity::remove);
            } catch (final Exception ignored) {
                entity.remove();
            }
        }
    }

    // ══════════════════════ 清理 ══════════════════════

    private void cleanupSession(final CookingSession session, final Location location, final boolean dropRaw) {
        if (session.tickTask != null) {
            session.tickTask.cancel();
        }

        final Player player = Bukkit.getPlayer(session.playerId);
        if (player != null && session.bossBar != null) {
            player.hideBossBar(session.bossBar);
        }

        this.removeDisplayEntity(session);

        // 退還原始食材
        if (dropRaw && location.getWorld() != null) {
            final ItemStack rawItem;
            if (session.recipe.inputMaterial != null && session.recipe.inputMaterial != Material.AIR) {
                rawItem = new ItemStack(session.recipe.inputMaterial);
            } else {
                rawItem = this.buildOutputItem(session.recipe); // fallback
            }
            location.getWorld().dropItemNaturally(location.clone().add(0.5, 1.0, 0.5), rawItem);
        }
    }

    // ══════════════════════ 進度條 ══════════════════════

    private String buildProgressBar(final float progress) {
        final int total = 20;
        final int filled = (int) (progress * total);
        final StringBuilder bar = new StringBuilder("┃");
        for (int i = 0; i < total; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        bar.append("┃");
        return bar.toString();
    }

    // ══════════════════════ 配方系統 ══════════════════════

    private CookingRecipe findRecipe(final ItemStack handItem, final Material stationType) {
        final String techId = this.itemFactory.getTechItemId(handItem);
        final String stationName = stationType.name();

        for (final CookingRecipe recipe : this.recipes) {
            // 站台類型檢查
            if (!recipe.stationType.equalsIgnoreCase("any")) {
                boolean stationMatch = false;
                for (final String allowed : recipe.stationType.split(",")) {
                    if (allowed.trim().equalsIgnoreCase(stationName)) {
                        stationMatch = true;
                        break;
                    }
                }
                if (!stationMatch) {
                    continue;
                }
            }
            // ID 匹配（科技物品或原版）
            if (techId != null && recipe.inputId.equalsIgnoreCase(techId)) {
                return recipe;
            }
            if (recipe.inputMaterial != null && handItem.getType() == recipe.inputMaterial && techId == null) {
                return recipe;
            }
        }
        return null;
    }

    private ItemStack buildOutputItem(final CookingRecipe recipe) {
        final var itemDef = this.registry.getItem(recipe.outputId);
        if (itemDef != null) {
            return this.itemFactory.buildTechItem(itemDef);
        }
        // 嘗試原版物品
        try {
            final Material mat = Material.valueOf(recipe.outputId.toUpperCase(Locale.ROOT));
            return new ItemStack(mat);
        } catch (final IllegalArgumentException ignored) {
            return new ItemStack(Material.COOKED_BEEF);
        }
    }

    private void loadRecipes(final JavaPlugin plugin) {
        this.recipes.clear();

        // 嘗試外部檔案
        final File file = new File(plugin.getDataFolder(), "tech-content-expansion.yml");
        YamlConfiguration yaml = null;
        if (file.isFile()) {
            yaml = YamlConfiguration.loadConfiguration(file);
        } else {
            final var resource = plugin.getResource("tech-content-expansion.yml");
            if (resource != null) {
                yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
            }
        }

        if (yaml != null) {
            final ConfigurationSection section = yaml.getConfigurationSection("cooking-recipes");
            if (section != null) {
                for (final String key : section.getKeys(false)) {
                    final ConfigurationSection rs = section.getConfigurationSection(key);
                    if (rs == null) continue;

                    final String rawInput = rs.getString("input", "");
                    final boolean isTechInput = "TECH_ITEM".equalsIgnoreCase(rs.getString("input-type", ""));
                    final String inputId = isTechInput ? rawInput : "";
                    Material inputMat = null;
                    if (!isTechInput) {
                        try {
                            inputMat = Material.valueOf(rawInput.toUpperCase(Locale.ROOT));
                        } catch (final IllegalArgumentException ignored) {}
                    }

                    final String rawOutput = rs.getString("output", "COOKED_BEEF");
                    final int cookTime = rs.getInt("cook-time", 200);
                    final String displayName = rs.getString("display-name", key);

                    // 判斷站台類型
                    final List<String> stations = rs.getStringList("stations");
                    final String stationType = stations.isEmpty() ? "any" : String.join(",", stations);

                    // 進階配方欄位
                    final boolean advanced = rs.getBoolean("advanced", false);
                    final int perfectOutputCount = rs.getInt("perfect-output-count", 1);

                    // 食材追加
                    final List<IngredientAddition> ingredientAdditions = new ArrayList<>();
                    final var additionsList = rs.getMapList("ingredient-additions");
                    for (final var addMap : additionsList) {
                        final Object progObj = addMap.get("progress");
                        final float threshold = progObj instanceof Number n ? n.floatValue() : 0.5f;
                        final Object itemObj = addMap.get("item");
                        final String itemStr = itemObj != null ? String.valueOf(itemObj) : "";
                        final Object promptObj = addMap.get("prompt");
                        final String prompt = promptObj != null ? String.valueOf(promptObj) : "加入材料！";
                        final Object typeObj = addMap.get("item-type");
                        final boolean isTechIngredient = "TECH_ITEM".equalsIgnoreCase(
                                typeObj != null ? String.valueOf(typeObj) : "");
                        Material ingredientMat = null;
                        String ingredientId = "";
                        if (isTechIngredient) {
                            ingredientId = itemStr;
                        } else {
                            try {
                                ingredientMat = Material.valueOf(itemStr.toUpperCase(Locale.ROOT));
                            } catch (final IllegalArgumentException ignored) {
                                ingredientId = itemStr;
                            }
                        }
                        ingredientAdditions.add(new IngredientAddition(threshold,
                                ingredientId.isEmpty() ? null : ingredientId, ingredientMat, prompt));
                    }

                    this.recipes.add(new CookingRecipe(
                            key, inputId, inputMat, rawOutput,
                            cookTime, stationType, displayName,
                            advanced, perfectOutputCount, ingredientAdditions
                    ));
                }
            }
        }

        // 預設原版食物配方（如果 YAML 沒有定義）
        if (this.recipes.isEmpty()) {
            this.addDefaultRecipes();
        }

        plugin.getLogger().info("烹飪系統：載入 " + this.recipes.size() + " 個烹飪配方。");
    }

    private void addDefaultRecipes() {
        final List<IngredientAddition> none = List.of();
        this.recipes.add(new CookingRecipe("cook_beef", "", Material.BEEF, "COOKED_BEEF", 160, "any", "烤牛排", false, 1, none));
        this.recipes.add(new CookingRecipe("cook_porkchop", "", Material.PORKCHOP, "COOKED_PORKCHOP", 160, "any", "烤豬排", false, 1, none));
        this.recipes.add(new CookingRecipe("cook_chicken", "", Material.CHICKEN, "COOKED_CHICKEN", 140, "any", "烤雞腿", false, 1, none));
        this.recipes.add(new CookingRecipe("cook_mutton", "", Material.MUTTON, "COOKED_MUTTON", 150, "any", "烤羊排", false, 1, none));
        this.recipes.add(new CookingRecipe("cook_rabbit", "", Material.RABBIT, "COOKED_RABBIT", 120, "any", "烤兔肉", false, 1, none));
        this.recipes.add(new CookingRecipe("cook_cod", "", Material.COD, "COOKED_COD", 100, "any", "烤鱈魚", false, 1, none));
        this.recipes.add(new CookingRecipe("cook_salmon", "", Material.SALMON, "COOKED_SALMON", 100, "any", "烤鮭魚", false, 1, none));
        this.recipes.add(new CookingRecipe("cook_potato", "", Material.POTATO, "BAKED_POTATO", 120, "any", "烤馬鈴薯", false, 1, none));
        this.recipes.add(new CookingRecipe("cook_kelp", "", Material.KELP, "DRIED_KELP", 80, "any", "烤乾海帶", false, 1, none));
    }
}
