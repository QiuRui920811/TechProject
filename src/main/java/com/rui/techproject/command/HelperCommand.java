package com.rui.techproject.command;

import com.rui.techproject.TechProjectPlugin;
import com.rui.techproject.util.ItemFactoryUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

    private final TechProjectPlugin plugin;
    private final ItemFactoryUtil itemFactory;
    private final HttpClient httpClient;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 15_000L;

    public HelperCommand(final TechProjectPlugin plugin, final ItemFactoryUtil itemFactory) {
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

                // 在主執行緒回覆（分段避免過長）
                this.plugin.getSafeScheduler().runEntity(player, () -> {
                    player.sendMessage(Component.text("💡 ", NamedTextColor.GOLD)
                            .append(Component.text("科技幫手", NamedTextColor.AQUA, TextDecoration.BOLD)));
                    // 每行分開傳送，避免超長訊息
                    for (final String line : answer.split("\n")) {
                        player.sendMessage(Component.text(line, NamedTextColor.WHITE));
                    }
                });
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
}
