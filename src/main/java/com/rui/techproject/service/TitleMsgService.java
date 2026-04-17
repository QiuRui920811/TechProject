package com.rui.techproject.service;

import com.rui.techproject.TechMCPlugin;
import com.rui.techproject.util.SafeScheduler;
import com.rui.titlemsg.TitleMsg;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * TechProject 的 Title 服務 — 委派給獨立庫 {@link TitleMsg}。
 */
public final class TitleMsgService {

    public static final long DEFAULT_HOLD_TICKS = TitleMsg.DEFAULT_HOLD_TICKS;

    private final TitleMsg titleMsg;

    public TitleMsgService(final TechMCPlugin plugin, final SafeScheduler scheduler) {
        this.titleMsg = new TitleMsg(plugin);
    }

    public void send(final Player player, final Component title, final Component subtitle) {
        this.titleMsg.send(player, title, subtitle);
    }

    public void send(final Player player,
                     final Component title,
                     final Component subtitle,
                     final long holdTicks,
                     final Sound tickSound) {
        this.titleMsg.send(player, title, subtitle, holdTicks, tickSound);
    }

    public void sendAll(final World world,
                        final Component title,
                        final Component subtitle,
                        final long holdTicks,
                        final Sound tickSound) {
        this.titleMsg.broadcast(world, title, subtitle, holdTicks, tickSound);
    }

    public void onPlayerQuit(final UUID uuid) {
        this.titleMsg.cleanup(uuid);
    }

    public void cancel(final UUID uuid) {
        this.titleMsg.cancel(uuid);
    }
}
