package com.rui.techproject.command;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.util.ItemFactoryUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /helper (/幫手) — 向 AI 科技幫手提問。
 * 透過 HTTP 呼叫本機 Python RAG 服務。
 */
public final class HelperCommand implements CommandExecutor {

    private final TechMCPlugin plugin;
    private final ItemFactoryUtil itemFactory;
    private final HttpClient httpClient;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 15_000L;

    public HelperCommand(final TechMCPlugin plugin, final ItemFactoryUtil itemFactory) {
        this.plugin = plugin;
        this.itemFactory = itemFactory;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public boolean onCommand(@NotNull final CommandSender sender,
                             @NotNull final Command command,
                             @NotNull final String label,
                             @NotNull final String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("此指令僅限玩家使用。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(Component.text("📖 ", NamedTextColor.GOLD)
                    .append(Component.text("科技幫手", NamedTextColor.AQUA, TextDecoration.BOLD))
                    .append(Component.text(" — 使用方式：", NamedTextColor.GRAY))
                    .append(Component.text("/幫手 <你的問題>", NamedTextColor.WHITE)));
            player.sendMessage(Component.text("  例如：/幫手 壓縮機怎麼用？", NamedTextColor.GRAY));
            return true;
        }

        // 冷卻
        final long now = System.currentTimeMillis();
        final Long last = this.cooldowns.get(player.getUniqueId());
        if (last != null && now - last < COOLDOWN_MS) {
            final int remaining = (int) ((COOLDOWN_MS - (now - last)) / 1000) + 1;
            player.sendMessage(this.itemFactory.warning("⏳ 冷卻中，請等 " + remaining + " 秒後再問。"));
            return true;
        }
        this.cooldowns.put(player.getUniqueId(), now);

        final String question = String.join(" ", args);
        if (question.length() > 300) {
            player.sendMessage(this.itemFactory.warning("問題太長了，請精簡後再試。"));
            return true;
        }

        player.sendMessage(Component.text("🔍 正在查詢科技知識庫…", NamedTextColor.GRAY));

        // 非同步 HTTP 請求
        final int port = this.plugin.getConfig().getInt("helper-api-port", 18923);
        this.plugin.getSafeScheduler().runAsync(() -> {
            try {
                final String body = "{\"question\":" + escapeJson(question)
                        + ",\"player\":" + escapeJson(player.getName()) + "}";
                final HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://127.0.0.1:" + port + "/ask"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .timeout(Duration.ofSeconds(30))
                        .build();
                final HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                final String answer = extractJsonField(response.body(), "answer");
                if (answer == null || answer.isBlank()) {
                    this.plugin.getSafeScheduler().runEntity(player, () ->
                            player.sendMessage(this.itemFactory.warning("幫手服務沒有回應，請稍後再試。")));
                    return;
                }

                // 在主執行緒回覆（長回覆使用懸停展開）
                this.plugin.getSafeScheduler().runEntity(player, () -> sendFormattedAnswer(player, answer));
            } catch (IOException e) {
                this.plugin.getSafeScheduler().runEntity(player, () ->
                        player.sendMessage(this.itemFactory.warning("⚠ 幫手服務未啟動或連線失敗。")));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        return true;
    }

    /** 簡易 JSON 字串跳脫。 */
    private static String escapeJson(final String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }

    /** 從 JSON 回應中提取指定欄位的值（簡易解析）。 */
    private static String extractJsonField(final String json, final String field) {
        // 找 "field":"value" 或 "field": "value"
        final String key = "\"" + field + "\"";
        final int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return null;
        final int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) return null;
        // 找值的開始引號
        final int startQuote = json.indexOf('"', colonIdx + 1);
        if (startQuote < 0) return null;
        // 找結束引號（處理跳脫）
        final StringBuilder sb = new StringBuilder();
        boolean escaped = false;
        for (int i = startQuote + 1; i < json.length(); i++) {
            final char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'u' -> {
                        // Unicode hex 跳脫 (\\uXXXX)
                        if (i + 4 < json.length()) {
                            final String hex = json.substring(i + 1, i + 5);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append("\\u");
                            }
                        } else {
                            sb.append("\\u");
                        }
                    }
                    default -> { sb.append('\\'); sb.append(c); }
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ─── 色彩常量 ──────────────────────────────────────
    private static final TextColor C_ACCENT    = TextColor.color(0x55FFFF); // 亮青
    private static final TextColor C_TITLE     = TextColor.color(0xFFD700); // 金色
    private static final TextColor C_HEADING   = TextColor.color(0x00E5FF); // 結構青
    private static final TextColor C_SUBHEAD   = TextColor.color(0x7BFFA0); // 淡綠
    private static final TextColor C_BODY      = TextColor.color(0xE0E0E0); // 淡灰白
    private static final TextColor C_HIGHLIGHT  = TextColor.color(0xFFC857); // 重點黃
    private static final TextColor C_CODE      = TextColor.color(0xA8D8EA); // 淤藍
    private static final TextColor C_CODE_FENCE = TextColor.color(0x4A6670); // 深青
    private static final TextColor C_BULLET    = TextColor.color(0xFFAB40); // 橙色
    private static final TextColor C_TABLE     = TextColor.color(0x80CBC4); // 青綠
    private static final TextColor C_HOVER_HINT = TextColor.color(0x607D8B); // 暗灰
    private static final TextColor C_LINK      = TextColor.color(0x4FC3F7); // 亮藍
    private static final TextColor C_SEPARATOR = TextColor.color(0x37474F); // 深底線

    private static final String SEPARATOR = "  \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550";

    // ─── 格式化回覆 ──────────────────────────────────────

    private static final int PREVIEW_LINES = 4;

    private void sendFormattedAnswer(final Player player, final String answer) {
        // 標題分隔線
        player.sendMessage(Component.text(SEPARATOR, C_SEPARATOR));
        player.sendMessage(
            Component.text("  \u2726 ", C_TITLE)
                .append(Component.text("\u79d1\u6280\u5e6b\u624b", C_ACCENT, TextDecoration.BOLD))
                .append(Component.text(" \u2014 AI \u77e5\u8b58\u5eab\u56de\u8986", C_HOVER_HINT))
        );
        player.sendMessage(Component.text(SEPARATOR, C_SEPARATOR));

        final String[] allLines = answer.split("\n");

        // 短回覆：全部顯示
        if (allLines.length <= 8) {
            for (final String line : allLines) {
                player.sendMessage(formatLine(line));
            }
            player.sendMessage(Component.text(SEPARATOR, C_SEPARATOR));
            return;
        }

        // 長回覆：預覽前幾行
        for (int i = 0; i < PREVIEW_LINES && i < allLines.length; i++) {
            player.sendMessage(formatLine(allLines[i]));
        }

        player.sendMessage(Component.text("  \u2500\u2500\u2500 \u5c55\u958b\u66f4\u591a \u2500\u2500\u2500", C_HOVER_HINT, TextDecoration.ITALIC));

        // 將剩餘內容按段落分組，各段顯示為可懸停摘要行
        final StringBuilder remainBuf = new StringBuilder();
        for (int i = PREVIEW_LINES; i < allLines.length; i++) {
            if (i > PREVIEW_LINES) remainBuf.append('\n');
            remainBuf.append(allLines[i]);
        }
        final String[] paragraphs = remainBuf.toString().split("\n\n");

        for (final String para : paragraphs) {
            final String trimmed = para.trim();
            if (trimmed.isEmpty()) continue;

            final String[] pLines = trimmed.split("\n");
            String summary = pLines[0].replaceAll("[#*`]", "").replaceAll("^\\s*-\\s*", "").trim();
            if (summary.length() > 35) summary = summary.substring(0, 35) + "\u2026";
            if (summary.isEmpty()) continue;

            // 構建此段落的懸停文字
            final TextComponent.Builder hover = Component.text();
            hover.append(Component.text("\u250C\u2500 ", C_SEPARATOR))
                 .append(Component.text(summary, C_HIGHLIGHT, TextDecoration.BOLD))
                 .append(Component.text(" \u2500\u2510", C_SEPARATOR));
            for (final String pLine : pLines) {
                hover.append(Component.newline());
                hover.append(Component.text("\u2502 ", C_SEPARATOR));
                hover.append(formatLine(pLine));
            }
            hover.append(Component.newline())
                 .append(Component.text("\u2514\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2518", C_SEPARATOR));

            player.sendMessage(
                Component.text("  \u25B6 ", C_BULLET)
                    .append(Component.text(summary, C_HIGHLIGHT))
                    .append(Component.text("  \u00AB\u61F8\u505C\u67E5\u770B\u00BB", C_HOVER_HINT, TextDecoration.ITALIC))
                    .hoverEvent(HoverEvent.showText(hover.build()))
            );
        }

        // 完整回覆懸停
        final TextComponent.Builder fullHover = Component.text();
        fullHover.append(Component.text("\u2554", C_SEPARATOR))
                 .append(Component.text(" \u2726 \u5B8C\u6574\u56DE\u8986 \u2726 ", C_ACCENT, TextDecoration.BOLD))
                 .append(Component.text("\u2557", C_SEPARATOR));
        for (final String allLine : allLines) {
            fullHover.append(Component.newline());
            fullHover.append(Component.text("\u2551 ", C_SEPARATOR));
            fullHover.append(formatLine(allLine));
        }
        fullHover.append(Component.newline())
                 .append(Component.text("\u255A\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255D", C_SEPARATOR));

        player.sendMessage(
            Component.text("  \u2605 ", C_TITLE)
                .append(Component.text("[\u5B8C\u6574\u56DE\u8986]", C_LINK, TextDecoration.UNDERLINED))
                .append(Component.text("  \u00AB\u61F8\u505C\u67E5\u770B\u5168\u90E8\u00BB", C_HOVER_HINT, TextDecoration.ITALIC))
                .hoverEvent(HoverEvent.showText(fullHover.build()))
        );
        player.sendMessage(Component.text(SEPARATOR, C_SEPARATOR));
    }

    private static Component formatLine(final String raw) {
        final String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Component.empty();

        // Markdown 標題
        if (trimmed.startsWith("### "))
            return Component.text("  \u25C8 ", C_BULLET)
                    .append(Component.text(trimmed.substring(4), C_SUBHEAD, TextDecoration.BOLD));
        if (trimmed.startsWith("## "))
            return Component.text("  \u25C6 ", C_TITLE)
                    .append(Component.text(trimmed.substring(3), C_HEADING, TextDecoration.BOLD));
        if (trimmed.startsWith("# "))
            return Component.text("  \u2726 ", C_TITLE)
                    .append(Component.text(trimmed.substring(2), C_TITLE, TextDecoration.BOLD));

        // 列表項目
        if (trimmed.startsWith("* ") || trimmed.startsWith("- "))
            return Component.text("  \u25B9 ", C_BULLET)
                    .append(formatInline(trimmed.substring(2)));

        // 程式碼區塊圍欄
        if (trimmed.startsWith("```"))
            return Component.text("  \u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550", C_CODE_FENCE);

        // 合成表格行 [X][Y][Z]
        if (trimmed.startsWith("[") && trimmed.contains("]["))
            return Component.text("  ", C_TABLE)
                    .append(Component.text(trimmed, C_TABLE));

        // 箇頭 / 重要提示
        if (trimmed.startsWith("\u21d2 ") || trimmed.startsWith("=> "))
            return Component.text("  \u2794 ", C_HIGHLIGHT)
                    .append(formatInline(trimmed.substring(trimmed.indexOf(' ') + 1)));

        return Component.text("  ").append(formatInline(trimmed));
    }

    private static Component formatInline(final String text) {
        if (!text.contains("**") && !text.contains("`"))
            return Component.text(text, C_BODY);

        final TextComponent.Builder builder = Component.text();
        int i = 0;
        int plainStart = 0;

        while (i < text.length()) {
            // **粗體** — 亮黃加粗
            if (i + 1 < text.length() && text.charAt(i) == '*' && text.charAt(i + 1) == '*') {
                final int end = text.indexOf("**", i + 2);
                if (end > 0) {
                    if (i > plainStart)
                        builder.append(Component.text(text.substring(plainStart, i), C_BODY));
                    builder.append(Component.text(text.substring(i + 2, end), C_HIGHLIGHT, TextDecoration.BOLD));
                    i = end + 2;
                    plainStart = i;
                    continue;
                }
            }
            // `行內程式碼` — 淤藍底
            if (text.charAt(i) == '`') {
                final int end = text.indexOf('`', i + 1);
                if (end > 0) {
                    if (i > plainStart)
                        builder.append(Component.text(text.substring(plainStart, i), C_BODY));
                    builder.append(Component.text(text.substring(i + 1, end), C_CODE));
                    i = end + 1;
                    plainStart = i;
                    continue;
                }
            }
            i++;
        }
        if (plainStart < text.length())
            builder.append(Component.text(text.substring(plainStart), C_BODY));

        return builder.build();
    }
}
