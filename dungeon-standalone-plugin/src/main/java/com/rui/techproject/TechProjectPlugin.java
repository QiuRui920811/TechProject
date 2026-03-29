package com.rui.techproject;

import com.rui.techproject.command.DungeonCommand;
import com.rui.techproject.listener.DungeonListener;
import com.rui.techproject.service.DungeonService;
import com.rui.techproject.util.SafeScheduler;
import org.bukkit.plugin.java.JavaPlugin;

public final class TechProjectPlugin extends JavaPlugin {

    private SafeScheduler safeScheduler;
    private DungeonService dungeonService;

    @Override
    public void onEnable() {
        this.saveDefaultConfig();
        this.saveResource("tech-dungeons.yml", false);

        this.safeScheduler = new SafeScheduler(this);
        this.dungeonService = new DungeonService(this, this.safeScheduler);

        this.getServer().getPluginManager().registerEvents(new DungeonListener(this), this);

        final DungeonCommand command = new DungeonCommand(this);
        if (this.getCommand("dungeon") != null) {
            this.getCommand("dungeon").setExecutor(command);
            this.getCommand("dungeon").setTabCompleter(command);
        }

        this.dungeonService.loadPlayerData();
        this.dungeonService.start();
        this.getLogger().info("TechDungeon enabled: loaded " + this.dungeonService.definitions().size() + " dungeons.");
    }

    @Override
    public void onDisable() {
        if (this.dungeonService != null) {
            this.dungeonService.saveAll();
            this.dungeonService.shutdown();
        }
    }

    public SafeScheduler getSafeScheduler() {
        return this.safeScheduler;
    }

    public DungeonService getDungeonService() {
        return this.dungeonService;
    }
}
