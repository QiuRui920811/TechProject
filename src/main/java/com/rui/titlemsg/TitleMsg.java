package com.rui.titlemsg;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Folia-safe 打字機風格 Title 庫。
 *
 * <p>所有 title / subtitle 文字會逐字出現、停留後再反向逐字消失。
 * 不使用原版淡入淡出（fadeIn = 0, fadeOut = 0）。</p>
 *
 * <h3>用法</h3>
 * <pre>{@code
 * // 初始化（通常在 onEnable）
 * TitleMsg titleMsg = new TitleMsg(this);
 *
 * // 送出
 * titleMsg.send(player,
 *     Component.text("標題", NamedTextColor.GOLD),
 *     Component.text("副標題", NamedTextColor.GRAY));
 *
 * // 自訂停留時間與音效
 * titleMsg.send(player, title, subtitle, 40L, Sound.BLOCK_NOTE_BLOCK_CHIME);
 *
 * // 靜音
 * titleMsg.send(player, title, subtitle, 60L, null);
 *
 * // 廣播
 * titleMsg.broadcast(world, title, subtitle, 60L, Sound.BLOCK_NOTE_BLOCK_HAT);
 *
 * // 取消
 * titleMsg.cancel(player.getUniqueId());
 *
 * // 玩家離線時清理（在 PlayerQuitEvent 中呼叫）
 * titleMsg.cleanup(player.getUniqueId());
 * }</pre>
 */
public final class TitleMsg {

    /** 每字出現間隔：3 tick = 150ms。 */
    private static final long CHAR_INTERVAL_TICKS = 3L;
    /** 完整顯示後的預設停留時間：3 秒。 */
    public static final long DEFAULT_HOLD_TICKS = 60L;
    /** 逐字消失的總長：3 秒內完成。 */
    private static final long FADE_TOTAL_TICKS = 60L;
    /** 客戶端 Title.Times：fadeIn=0, stay=10s（由我們手動管理生命週期）, fadeOut=0。 */
    private static final Title.Times NO_FADE_TIMES = Title.Times.times(
            Duration.ZERO,
            Duration.ofSeconds(10),
            Duration.ZERO);

    private final Plugin plugin;
    private final Map<UUID, ScheduledTask> activeTasks = new ConcurrentHashMap<>();

    public TitleMsg(final Plugin plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════
    //  公開 API
    // ═══════════════════════════════════════════════════════════

    /**
     * 送出打字機 title（預設 3 秒停留、HAT 音效）。
     */
    public void send(final Player player, final Component title, final Component subtitle) {
        this.send(player, title, subtitle, DEFAULT_HOLD_TICKS, Sound.BLOCK_NOTE_BLOCK_HAT);
    }

    /**
     * 送出打字機 title。
     *
     * @param player    目標玩家
     * @param title     主標題 Component（保留 Style）
     * @param subtitle  副標題 Component（保留 Style）
     * @param holdTicks 完整顯示後的停留 tick 數
     * @param tickSound 每個字元出現時播放的音效（null = 靜音）
     */
    public void send(final Player player,
                     final Component title,
                     final Component subtitle,
                     final long holdTicks,
                     final Sound tickSound) {
        if (player == null || !player.isOnline()) {
            return;
        }
        final String titleText = PlainTextComponentSerializer.plainText().serialize(title);
        final String subtitleText = PlainTextComponentSerializer.plainText().serialize(subtitle);
        final Style titleStyle = title.style();
        final Style subtitleStyle = subtitle.style();

        this.cancelFor(player.getUniqueId());
        this.startTypewriter(player, titleText, titleStyle, subtitleText, subtitleStyle, holdTicks, tickSound);
    }

    /**
     * 直接顯示 title（不走打字機，無淡入淡出）。
     *
     * @param player    目標玩家
     * @param title     主標題
     * @param subtitle  副標題
     * @param stayTicks 停留 tick 數
     */
    public void showInstant(final Player player,
                            final Component title,
                            final Component subtitle,
                            final long stayTicks) {
        if (player == null || !player.isOnline()) {
            return;
        }
        this.cancelFor(player.getUniqueId());
        player.showTitle(Title.title(title, subtitle, Title.Times.times(
                Duration.ZERO,
                Duration.ofMillis(stayTicks * 50L),
                Duration.ZERO)));
    }

    /**
     * 向世界所有玩家廣播打字機 title。
     */
    public void broadcast(final World world,
                          final Component title,
                          final Component subtitle,
                          final long holdTicks,
                          final Sound tickSound) {
        if (world == null) {
            return;
        }
        for (final Player p : world.getPlayers()) {
            this.send(p, title, subtitle, holdTicks, tickSound);
        }
    }

    /**
     * 取消玩家的打字機任務並清除 title。
     */
    public void cancel(final UUID uuid) {
        this.cancelFor(uuid);
    }

    /**
     * 玩家離線時清理資源。建議在 PlayerQuitEvent 中呼叫。
     */
    public void cleanup(final UUID uuid) {
        this.cancelFor(uuid);
    }

    // ═══════════════════════════════════════════════════════════
    //  核心實作
    // ═══════════════════════════════════════════════════════════

    private void startTypewriter(final Player player,
                                 final String titleText,
                                 final Style titleStyle,
                                 final String subtitleText,
                                 final Style subtitleStyle,
                                 final long holdTicks,
                                 final Sound tickSound) {
        final int titleLen = titleText.length();
        final int subtitleLen = subtitleText.length();
        final int totalLen = titleLen + subtitleLen;

        final int typeDoneTick = Math.max(1, totalLen) * (int) CHAR_INTERVAL_TICKS;
        final int holdEndTick = typeDoneTick + (int) holdTicks;
        final long fadeInterval = totalLen <= 0 ? FADE_TOTAL_TICKS : Math.max(1L, FADE_TOTAL_TICKS / totalLen);
        final int fadeEndTick = holdEndTick + (int) (totalLen <= 0 ? FADE_TOTAL_TICKS : totalLen * fadeInterval);

        final UUID uuid = player.getUniqueId();
        final int[] relativeTick = {0};

        final ScheduledTask task = player.getScheduler().runAtFixedRate(this.plugin, taskRef -> {
            if (!player.isOnline()) {
                taskRef.cancel();
                this.activeTasks.remove(uuid);
                return;
            }
            final int t = relativeTick[0]++;

            if (t <= typeDoneTick) {
                // 階段 1：逐字輸入（title → subtitle）
                final int charsToShow = (int) (t / CHAR_INTERVAL_TICKS);
                final int showTitleChars = Math.min(charsToShow, titleLen);
                final int showSubtitleChars = Math.max(0, Math.min(charsToShow - titleLen, subtitleLen));

                if (tickSound != null && t > 0 && t % CHAR_INTERVAL_TICKS == 0 && charsToShow <= totalLen) {
                    final float pitch = charsToShow <= titleLen ? 1.8f : 1.5f;
                    player.playSound(player.getLocation(), tickSound, SoundCategory.MASTER, 0.3f, pitch);
                }

                this.pushTitle(player,
                        titleText.substring(0, showTitleChars), titleStyle,
                        subtitleText.substring(0, showSubtitleChars), subtitleStyle);
                return;
            }

            if (t <= holdEndTick) {
                // 階段 2：保留全文
                if (t == typeDoneTick + 1) {
                    this.pushTitle(player, titleText, titleStyle, subtitleText, subtitleStyle);
                }
                return;
            }

            if (t <= fadeEndTick) {
                // 階段 3：反向逐字刪除（先刪 subtitle 再刪 title）
                final long fadeElapsed = t - holdEndTick;
                final int deletedChars = (int) (fadeElapsed / fadeInterval);
                final int remaining = Math.max(0, totalLen - deletedChars);

                final int showTitleChars;
                final int showSubtitleChars;
                if (remaining >= titleLen) {
                    showTitleChars = titleLen;
                    showSubtitleChars = remaining - titleLen;
                } else {
                    showTitleChars = remaining;
                    showSubtitleChars = 0;
                }

                this.pushTitle(player,
                        titleText.substring(0, showTitleChars), titleStyle,
                        subtitleText.substring(0, showSubtitleChars), subtitleStyle);
                return;
            }

            // 結束：清除 title 與任務
            player.clearTitle();
            taskRef.cancel();
            this.activeTasks.remove(uuid);
        }, null, 1L, 1L);

        if (task != null) {
            this.activeTasks.put(uuid, task);
        }
    }

    private void pushTitle(final Player player,
                           final String titlePartial,
                           final Style titleStyle,
                           final String subtitlePartial,
                           final Style subtitleStyle) {
        final Component titleComp = titlePartial.isEmpty()
                ? Component.empty()
                : Component.text(titlePartial).style(titleStyle);
        final Component subtitleComp = subtitlePartial.isEmpty()
                ? Component.empty()
                : Component.text(subtitlePartial).style(subtitleStyle);
        player.showTitle(Title.title(titleComp, subtitleComp, NO_FADE_TIMES));
    }

    private void cancelFor(final UUID uuid) {
        final ScheduledTask existing = this.activeTasks.remove(uuid);
        if (existing != null) {
            try {
                existing.cancel();
            } catch (final Exception ignored) {
                // Folia may throw if already finished
            }
        }
    }
}
