package com.rui.techproject.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public final class SafeScheduler {
    private final Plugin plugin;

    public SafeScheduler(final Plugin plugin) {
        this.plugin = plugin;
    }

    private boolean inactive() {
        return this.plugin == null || !this.plugin.isEnabled();
    }

    public void runAsync(final Runnable runnable) {
        if (this.inactive()) {
            return;
        }
        Bukkit.getAsyncScheduler().runNow(this.plugin, task -> runnable.run());
    }

    public ScheduledTask runGlobal(final Consumer<ScheduledTask> task) {
        if (this.inactive()) {
            return null;
        }
        return Bukkit.getGlobalRegionScheduler().run(this.plugin, task);
    }

    public ScheduledTask runGlobalDelayed(final Consumer<ScheduledTask> task, final long delayTicks) {
        if (this.inactive()) {
            return null;
        }
        return Bukkit.getGlobalRegionScheduler().runDelayed(this.plugin, task, delayTicks);
    }

    public ScheduledTask runGlobalTimer(final Consumer<ScheduledTask> task, final long delayTicks, final long periodTicks) {
        if (this.inactive()) {
            return null;
        }
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(this.plugin, task, delayTicks, periodTicks);
    }

    public ScheduledTask runRegion(final Location location, final Consumer<ScheduledTask> task) {
        if (this.inactive()) {
            return null;
        }
        return Bukkit.getRegionScheduler().run(this.plugin, location, task);
    }

    public ScheduledTask runRegionDelayed(final Location location, final Consumer<ScheduledTask> task, final long delayTicks) {
        if (this.inactive()) {
            return null;
        }
        if (delayTicks <= 0L) {
            return this.runRegion(location, task);
        }
        return Bukkit.getRegionScheduler().runDelayed(this.plugin, location, task, delayTicks);
    }

    public ScheduledTask runRegionTimer(final Location location, final Consumer<ScheduledTask> task, final long delayTicks, final long periodTicks) {
        if (this.inactive()) {
            return null;
        }
        return Bukkit.getRegionScheduler().runAtFixedRate(this.plugin, location, task, delayTicks, periodTicks);
    }

    public void runEntity(final Entity entity, final Runnable runnable) {
        if (this.inactive()) {
            return;
        }
        entity.getScheduler().run(this.plugin, task -> runnable.run(), null);
    }

    public void runEntityDelayed(final Entity entity, final Runnable runnable, final long delayTicks) {
        if (this.inactive()) {
            return;
        }
        if (delayTicks <= 0L) {
            this.runEntity(entity, runnable);
            return;
        }
        entity.getScheduler().runDelayed(this.plugin, task -> runnable.run(), null, delayTicks);
    }

    public ScheduledTask runEntityTimer(final Entity entity, final Consumer<ScheduledTask> task, final long delayTicks, final long periodTicks) {
        if (this.inactive()) {
            return null;
        }
        final long safeDelay = Math.max(1L, delayTicks);
        final long safePeriod = Math.max(1L, periodTicks);
        return entity.getScheduler().runAtFixedRate(this.plugin, task, null, safeDelay, safePeriod);
    }
}
