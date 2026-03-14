package com.rui.techproject.service;

import com.rui.techproject.TechProjectPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class TitleService {
    private static final String FILE_NAME = "tech-titles.yml";
    private final TechProjectPlugin plugin;
    private final PlayerProgressService progressService;
    private final Map<String, String> titleMap = new LinkedHashMap<>();

    public TitleService(final TechProjectPlugin plugin, final PlayerProgressService progressService) {
        this.plugin = plugin;
        this.progressService = progressService;
        this.reload();
    }

    public void reload() {
        this.titleMap.clear();
        final File file = new File(this.plugin.getDataFolder(), FILE_NAME);
        if (!file.exists()) {
            try (final InputStream stream = this.plugin.getResource(FILE_NAME)) {
                if (stream != null) {
                    Files.createDirectories(file.getParentFile().toPath());
                    Files.copy(stream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (final IOException exception) {
                this.plugin.getLogger().warning("無法釋出 " + FILE_NAME + "：" + exception.getMessage());
            }
        }
        if (!file.exists()) {
            return;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection section = yaml.getConfigurationSection("titles");
        if (section == null) {
            return;
        }
        for (final String key : section.getKeys(false)) {
            final String title = section.getString(key + ".title", "");
            if (!title.isBlank()) {
                this.titleMap.put(key, ChatColor.translateAlternateColorCodes('&', title));
            }
        }
        this.plugin.getLogger().info("已載入 " + this.titleMap.size() + " 個稱號定義。");
    }

    public String getTitleDisplay(final String achievementId) {
        return this.titleMap.getOrDefault(achievementId, "");
    }

    public boolean hasTitle(final String achievementId) {
        return this.titleMap.containsKey(achievementId);
    }

    public Set<String> allTitleIds() {
        return Collections.unmodifiableSet(this.titleMap.keySet());
    }

    public int totalTitleCount() {
        return this.titleMap.size();
    }

    public String getPlayerTitle(final UUID uuid) {
        final String selectedId = this.progressService.getSelectedTitle(uuid);
        if (selectedId == null || selectedId.isBlank()) {
            return "";
        }
        if (!this.progressService.hasAchievementUnlocked(uuid, selectedId)) {
            return "";
        }
        return this.titleMap.getOrDefault(selectedId, "");
    }

    public String getPlayerTitleRaw(final UUID uuid) {
        final String display = this.getPlayerTitle(uuid);
        if (display.isBlank()) {
            return "";
        }
        return ChatColor.stripColor(display);
    }

    public String getPlayerTitleId(final UUID uuid) {
        final String selectedId = this.progressService.getSelectedTitle(uuid);
        if (selectedId == null || selectedId.isBlank()) {
            return "none";
        }
        if (!this.progressService.hasAchievementUnlocked(uuid, selectedId)) {
            return "none";
        }
        return selectedId;
    }

    public int getPlayerUnlockedTitleCount(final UUID uuid) {
        int count = 0;
        for (final String id : this.titleMap.keySet()) {
            if (this.progressService.hasAchievementUnlocked(uuid, id)) {
                count++;
            }
        }
        return count;
    }

    public boolean setTitle(final UUID uuid, final String achievementId) {
        if (achievementId == null || achievementId.isBlank()) {
            this.progressService.setSelectedTitle(uuid, "");
            return true;
        }
        if (!this.titleMap.containsKey(achievementId)) {
            return false;
        }
        if (!this.progressService.hasAchievementUnlocked(uuid, achievementId)) {
            return false;
        }
        this.progressService.setSelectedTitle(uuid, achievementId);
        return true;
    }

    public boolean clearTitle(final UUID uuid) {
        this.progressService.setSelectedTitle(uuid, "");
        return true;
    }
}
