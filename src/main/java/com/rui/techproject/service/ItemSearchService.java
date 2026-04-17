package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.util.ItemFactoryUtil;
import com.rui.techproject.util.SafeScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 物品搜尋服務 — 鐵砧輸入式搜尋。
 * <p>
 * 玩家打開一個鐵砧 GUI，在重新命名欄位輸入關鍵字，
 * 點擊輸出槽後關閉鐵砧，轉由 {@link TechBookService} 打開圖鑑搜尋結果頁。
 * <br>
 * 搜尋觸發方式：
 * <ul>
 *   <li>聊天輸入 {@code ?關鍵字}（事件取消，不送 Discord）</li>
 *   <li>指令 {@code /tech search}</li>
 * </ul>
 * </p>
 */
public final class ItemSearchService {

    /** 鐵砧 GUI 的標題，用於在 TechListener 辨識 */
    public static final String ANVIL_SEARCH_TITLE = "🔍 物品搜尋";

    private static final String SEARCH_PREFIX = "?";

    private final TechMCPlugin plugin;
    private final SafeScheduler scheduler;
    private final ItemFactoryUtil itemFactory;

    /** 追蹤正在使用鐵砧搜尋的玩家 */
    private final Set<UUID> anvilSearchPlayers = ConcurrentHashMap.newKeySet();

    public ItemSearchService(final TechMCPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = plugin.getSafeScheduler();
        this.itemFactory = plugin.getItemFactory();
    }

    // ═══════════════ 公開 API ═══════════════

    /**
     * 判斷聊天訊息是否為搜尋指令（{@code ?關鍵字}）。
     */
    public boolean isSearchQuery(final String rawMessage) {
        return rawMessage != null
                && rawMessage.startsWith(SEARCH_PREFIX)
                && rawMessage.length() > SEARCH_PREFIX.length();
    }

    /**
     * 從聊天訊息抽取搜尋關鍵字（不含前綴）。
     */
    public String extractQuery(final String rawMessage) {
        return rawMessage.substring(SEARCH_PREFIX.length()).trim();
    }

    /**
     * 打開鐵砧搜尋 GUI。
     * <p>
     * 如果提供了 {@code directQuery}，則跳過鐵砧直接打開圖鑑搜尋結果。
     * </p>
     */
    public void openSearch(final Player player, final String directQuery) {
        if (directQuery != null && !directQuery.isBlank()) {
            // 有直接關鍵字 → 跳過鐵砧，直接打開圖鑑搜尋結果
            this.plugin.getTechBookService().openSearchResults(player, directQuery, 0);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.2f);
            return;
        }
        // 打開鐵砧 GUI
        this.openAnvilGui(player);
    }

    /**
     * 打開鐵砧搜尋 GUI。
     */
    public void openAnvilGui(final Player player) {
        final UUID uuid = player.getUniqueId();
        this.anvilSearchPlayers.add(uuid);

        final Inventory anvil = Bukkit.createInventory(null, InventoryType.ANVIL,
                Component.text(ANVIL_SEARCH_TITLE));

        // 左側輸入欄放一張紙，提示玩家輸入
        final ItemStack prompt = new ItemStack(Material.PAPER);
        final ItemMeta meta = prompt.getItemMeta();
        meta.displayName(Component.text("在此輸入關鍵字", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("在上方欄位輸入搜尋文字", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("然後點擊右側結果確認搜尋", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("支援中文 / 英文 / ID", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
        ));
        prompt.setItemMeta(meta);

        anvil.setItem(0, prompt);
        player.openInventory(anvil);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.4f, 1.2f);
    }

    /**
     * 處理鐵砧 GUI 的點擊事件。
     *
     * @return true 如果此事件已被處理（應取消原事件）
     */
    public boolean handleAnvilClick(final Player player, final int rawSlot) {
        final UUID uuid = player.getUniqueId();
        if (!this.anvilSearchPlayers.contains(uuid)) {
            return false;
        }

        // 只處理輸出欄（slot 2）的點擊
        if (rawSlot != 2) {
            return true; // 仍然取消事件（防止偷物品）
        }

        // 取得玩家輸入的文字
        String query = null;
        if (player.getOpenInventory() instanceof AnvilView anvilView) {
            query = anvilView.getRenameText();
        }

        // 如果沒有從 AnvilView 取到，嘗試從輸出欄物品的名稱取得
        if ((query == null || query.isBlank()) && player.getOpenInventory().getTopInventory().getItem(2) != null) {
            final ItemStack output = player.getOpenInventory().getTopInventory().getItem(2);
            if (output != null && output.hasItemMeta() && output.getItemMeta().hasDisplayName()) {
                query = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(output.getItemMeta().displayName());
            }
        }

        final String finalQuery = query != null ? query.trim() : "";

        // 關閉鐵砧
        this.anvilSearchPlayers.remove(uuid);
        this.scheduler.runEntity(player, () -> {
            player.closeInventory();

            if (finalQuery.isBlank()) {
                player.sendMessage(this.itemFactory.warning("未輸入搜尋關鍵字，已取消搜尋。"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return;
            }

            // 委託 TechBookService 打開圖鑑搜尋結果頁
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.2f);
            this.plugin.getTechBookService().openSearchResults(player, finalQuery, 0);
        });

        return true;
    }

    /**
     * 判斷此玩家是否正在使用鐵砧搜尋。
     */
    public boolean isAnvilSearchOpen(final UUID uuid) {
        return this.anvilSearchPlayers.contains(uuid);
    }

    /**
     * 玩家關閉鐵砧時清理狀態。
     */
    public void clearState(final UUID uuid) {
        this.anvilSearchPlayers.remove(uuid);
    }

    /**
     * 判斷某個 Inventory 標題是否為搜尋鐵砧。
     */
    public boolean isSearchAnvilTitle(final String title) {
        return ANVIL_SEARCH_TITLE.equals(title);
    }
}
