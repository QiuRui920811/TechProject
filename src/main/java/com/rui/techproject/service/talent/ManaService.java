package com.rui.techproject.service.talent;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.service.PlayerProgressService;
import com.rui.techproject.service.SkillService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * 魔力池管理：
 * <ul>
 *   <li>最大魔力 = Stat.MAX_MANA</li>
 *   <li>每秒恢復 = Stat.MANA_REGEN</li>
 *   <li>當前魔力儲存於 PlayerProgressService stats "mana_current"（×10，避免小數）</li>
 *   <li>每 20 tick 自動回復 + actionbar 顯示</li>
 * </ul>
 */
public final class ManaService {

    /** 當前魔力儲存鍵（以整數存，實際值為此 /10）。 */
    private static final String MANA_KEY = "mana_current_x10";

    private final TechMCPlugin plugin;
    private final PlayerProgressService progressService;
    private final SkillService skillService;

    public ManaService(final TechMCPlugin plugin,
                        final PlayerProgressService progressService,
                        final SkillService skillService) {
        this.plugin = plugin;
        this.progressService = progressService;
        this.skillService = skillService;
    }

    public void start() {
        // 每 20 ticks (1 秒) 掃全玩家，累計 regen + 更新 actionbar
        this.plugin.getSafeScheduler().runGlobalTimer(task -> this.tick(), 40L, 20L);
    }

    private void tick() {
        for (final Player p : this.plugin.getServer().getOnlinePlayers()) {
            this.regen(p);
        }
    }

    private void regen(final Player player) {
        final UUID uuid = player.getUniqueId();
        final double max = this.getMaxMana(uuid);
        final double regenPerSec = this.skillService.getStatValue(uuid, SkillService.Stat.MANA_REGEN);
        double current = this.getCurrentMana(uuid);
        if (current < max) {
            current = Math.min(max, current + regenPerSec);
            this.setCurrentMana(uuid, current);
        }
        // 只在持有施法武器時顯示 actionbar（避免干擾其他 UI）
        if (SpellCastService.isSpellWeapon(player.getInventory().getItemInMainHand())) {
            player.sendActionBar(this.buildManaBar(current, max));
        }
    }

    /** 組合漸層魔力條 Component。 */
    private Component buildManaBar(final double current, final double max) {
        final int bars = 20;
        final double ratio = max > 0 ? Math.min(1.0, current / max) : 0;
        final int filled = (int) Math.round(bars * ratio);

        Component line = Component.text("✦ 魔力 ", TextColor.color(0xC4B5FD))
                .decoration(TextDecoration.ITALIC, false);
        final StringBuilder bar = new StringBuilder();
        for (int i = 0; i < bars; i++) bar.append(i < filled ? "▮" : "░");
        final TextColor barColor = ratio > 0.5
                ? TextColor.color(0x8B5CF6)
                : (ratio > 0.2 ? TextColor.color(0x7C3AED) : TextColor.color(0x4C1D95));
        line = line.append(Component.text(bar.toString(), barColor)
                .decoration(TextDecoration.ITALIC, false));
        line = line.append(Component.text(" " + (int) current + "/" + (int) max,
                        TextColor.color(0xF3F4F6))
                .decoration(TextDecoration.ITALIC, false));
        return line;
    }

    public double getMaxMana(final UUID uuid) {
        return this.skillService.getStatValue(uuid, SkillService.Stat.MAX_MANA);
    }

    public double getCurrentMana(final UUID uuid) {
        final long raw = this.progressService.getStat(uuid, MANA_KEY);
        if (raw <= 0L) {
            // 首次初始化為滿值
            final double max = this.getMaxMana(uuid);
            this.setCurrentMana(uuid, max);
            return max;
        }
        return raw / 10.0;
    }

    public void setCurrentMana(final UUID uuid, final double value) {
        final double clamped = Math.max(0.0, Math.min(this.getMaxMana(uuid), value));
        this.progressService.setStat(uuid, MANA_KEY, (long) Math.round(clamped * 10.0));
    }

    /** 嘗試消耗魔力；成功回 true，不足回 false。 */
    public boolean consume(final UUID uuid, final double amount) {
        final double current = this.getCurrentMana(uuid);
        if (current < amount) return false;
        this.setCurrentMana(uuid, current - amount);
        return true;
    }

    /**
     * 補回魔力（不會超過最大值）。供傳統 skill 行動（擊殺、挖礦等）呼叫，
     * 讓玩家在打怪挖礦的同時也能少量回魔，避免長時間卡在空魔力狀態。
     *
     * @param player 目標玩家
     * @param amount 欲補回的魔力量（正數才有效）
     */
    public void restore(final Player player, final double amount) {
        if (player == null || amount <= 0) return;
        final UUID uuid = player.getUniqueId();
        final double current = this.getCurrentMana(uuid);
        final double max = this.getMaxMana(uuid);
        if (current >= max) return;
        this.setCurrentMana(uuid, current + amount);
    }

    public double getRatio(final UUID uuid) {
        final double max = this.getMaxMana(uuid);
        if (max <= 0) return 0;
        return Math.min(1.0, this.getCurrentMana(uuid) / max);
    }

}
