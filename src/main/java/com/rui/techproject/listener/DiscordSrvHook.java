package com.rui.techproject.listener;

import com.rui.techproject.TechProjectPlugin;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;

/**
 * DiscordSRV API 掛鉤 — 攔截搜尋指令等訊息，避免轉發至 Discord。
 * <p>
 * 以 {@code ?} 開頭的訊息為科技圖鑑搜尋指令，
 * 不應公開到 Discord 頻道中。
 * </p>
 */
public final class DiscordSrvHook {

    private static final String SEARCH_PREFIX = "?";

    private final TechProjectPlugin plugin;
    private boolean registered = false;

    public DiscordSrvHook(final TechProjectPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 嘗試向 DiscordSRV 註冊事件訂閱。
     * 若 DiscordSRV 未安裝則忽略。
     */
    public void tryRegister() {
        try {
            DiscordSRV.api.subscribe(this);
            this.registered = true;
            this.plugin.getLogger().info("DiscordSRV 已掛接：搜尋指令將不會轉發至 Discord。");
        } catch (final Exception e) {
            this.plugin.getLogger().warning("DiscordSRV 掛接失敗（插件可能未載入）：" + e.getMessage());
        }
    }

    /**
     * 取消訂閱（插件關閉時呼叫）。
     */
    public void tryUnregister() {
        if (!this.registered) {
            return;
        }
        try {
            DiscordSRV.api.unsubscribe(this);
        } catch (final Exception ignored) {
            // 插件已卸載，忽略
        }
    }

    @Subscribe
    public void onGameChatPreProcess(final GameChatMessagePreProcessEvent event) {
        final String message = event.getMessage();
        if (message == null) {
            return;
        }
        // 攔截搜尋指令：以 ? 開頭
        if (message.startsWith(SEARCH_PREFIX)) {
            event.setCancelled(true);
            return;
        }
        // 攔截圖鑑搜尋等候中的輸入（由 TechBookService 處理的純文字搜尋）
        if (this.plugin.getTechBookService().isAwaitingSearchInput(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }
}
