package com.rui.techproject.listener;

import com.rui.techproject.service.talent.SpellCastService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 將玩家的點擊事件餵給 {@link SpellCastService}，
 * 並在離線時清理該玩家的 combo / cooldown 狀態。
 *
 * <p>輸入方式：
 * <ul>
 *   <li>一般右鍵 / 左鍵 → 一般輸入</li>
 *   <li>下蹲 + 右鍵 / 左鍵 → 重擊輸入</li>
 * </ul>
 *
 * <p>右鍵方塊時若該方塊是受管機器（例如板手配機器），會直接略過
 * combo 記錄，避免機器互動被當成 R 輸入。
 */
public final class SpellCastListener implements Listener {

    private final SpellCastService spellCastService;

    public SpellCastListener(final SpellCastService spellCastService) {
        this.spellCastService = spellCastService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Action action = event.getAction();
        // 右鍵優先偵測
        final boolean isRightClick = (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK);
        // 左鍵也接受（放在右鍵後面偵測）
        final boolean isLeftClick = (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK);
        if (!isRightClick && !isLeftClick) {
            return;
        }
        // 只接受主手觸發
        if (event.getHand() == null) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        // 只對有綁定的武器生效
        if (!SpellCastService.isSpellWeapon(event.getItem())) return;

        // 點擊受管機器（例如板手點/拆機器）不記錄 combo
        if ((action == Action.RIGHT_CLICK_BLOCK || action == Action.LEFT_CLICK_BLOCK)
                && this.spellCastService.isManagedMachineBlock(event.getClickedBlock())) {
            return;
        }

        final boolean sneaking = event.getPlayer().isSneaking();
        this.spellCastService.recordClick(event.getPlayer(), sneaking, isLeftClick);
    }

    @EventHandler
    public void onQuit(final PlayerQuitEvent event) {
        this.spellCastService.clearPlayer(event.getPlayer().getUniqueId());
    }
}
