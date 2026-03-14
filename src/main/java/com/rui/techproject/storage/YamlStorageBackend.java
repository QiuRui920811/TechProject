package com.rui.techproject.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * YAML 檔案儲存後端 — 預設實作，與現有行為完全相容。
 * <p>
 * 所有寫入操作皆使用原子寫入（先寫 .tmp 再 rename）。
 * </p>
 */
public final class YamlStorageBackend implements StorageBackend {

    private final File dataFolder;
    private final Logger logger;
    private final File playerDir;
    private final File machineFile;
    private final File techBlockFile;
    private final File cropFile;
    private final File planetFile;
    private final File metaFile;

    public YamlStorageBackend(final File dataFolder, final Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.playerDir = new File(dataFolder, "players");
        this.machineFile = new File(dataFolder, "machines.yml");
        this.techBlockFile = new File(dataFolder, "placed-tech-blocks.yml");
        this.cropFile = new File(dataFolder, "tech-crops.yml");
        this.planetFile = new File(dataFolder, "planet-state.yml");
        this.metaFile = new File(dataFolder, "storage-meta.yml");
    }

    @Override
    public void init() {
        if (!this.playerDir.exists()) {
            this.playerDir.mkdirs();
        }
    }

    @Override
    public void shutdown() {
        // YAML 不需要關閉
    }

    @Override
    public String name() {
        return "yaml";
    }

    // ═══════════════════ 玩家進度 ═══════════════════

    @Override
    public Map<String, Object> loadPlayerProgress(final UUID uuid) {
        final File file = new File(this.playerDir, uuid + ".yml");
        if (!file.exists()) {
            return null;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return this.sectionToMap(yaml);
    }

    @Override
    public void savePlayerProgress(final UUID uuid, final Map<String, Object> data) {
        final File file = new File(this.playerDir, uuid + ".yml");
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<String, Object> entry : data.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue());
        }
        this.atomicSave(yaml, file, "玩家進度 " + uuid);
    }

    @Override
    public void deletePlayerProgress(final UUID uuid) {
        final File file = new File(this.playerDir, uuid + ".yml");
        if (file.exists()) {
            file.delete();
        }
    }

    // ═══════════════════ 機器 ═══════════════════

    @Override
    public Map<String, Map<String, Object>> loadAllMachines() {
        return this.loadIndexedData(this.machineFile, "machines");
    }

    @Override
    public void saveAllMachines(final Map<String, Map<String, Object>> machines) {
        this.saveIndexedData(this.machineFile, "machines", machines, "機器");
    }

    // ═══════════════════ 科技方塊 ═══════════════════

    @Override
    public Map<String, Map<String, Object>> loadAllTechBlocks() {
        return this.loadIndexedData(this.techBlockFile, "blocks");
    }

    @Override
    public void saveAllTechBlocks(final Map<String, Map<String, Object>> blocks) {
        this.saveIndexedData(this.techBlockFile, "blocks", blocks, "科技方塊");
    }

    // ═══════════════════ 科技作物 ═══════════════════

    @Override
    public Map<String, Map<String, Object>> loadAllCrops() {
        return this.loadIndexedData(this.cropFile, "crops");
    }

    @Override
    public void saveAllCrops(final Map<String, Map<String, Object>> crops) {
        this.saveIndexedData(this.cropFile, "crops", crops, "科技作物");
    }

    // ═══════════════════ 星球狀態 ═══════════════════

    @Override
    public Map<String, Object> loadPlanetState() {
        if (!this.planetFile.exists()) {
            return null;
        }
        return this.sectionToMap(YamlConfiguration.loadConfiguration(this.planetFile));
    }

    @Override
    public void savePlanetState(final Map<String, Object> state) {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<String, Object> entry : state.entrySet()) {
            yaml.set(entry.getKey(), entry.getValue());
        }
        this.atomicSave(yaml, this.planetFile, "星球狀態");
    }

    // ═══════════════════ Schema 版本 ═══════════════════

    @Override
    public int getSchemaVersion() {
        if (!this.metaFile.exists()) {
            return 0;
        }
        return YamlConfiguration.loadConfiguration(this.metaFile).getInt("schema-version", 0);
    }

    @Override
    public void setSchemaVersion(final int version) {
        final YamlConfiguration yaml;
        if (this.metaFile.exists()) {
            yaml = YamlConfiguration.loadConfiguration(this.metaFile);
        } else {
            yaml = new YamlConfiguration();
        }
        yaml.set("schema-version", version);
        this.atomicSave(yaml, this.metaFile, "schema 版本");
    }

    // ═══════════════════ 內部工具 ═══════════════════

    private Map<String, Map<String, Object>> loadIndexedData(final File file, final String rootKey) {
        final Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (!file.exists()) {
            return result;
        }
        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        final ConfigurationSection section = yaml.getConfigurationSection(rootKey);
        if (section == null) {
            return result;
        }
        for (final String key : section.getKeys(false)) {
            final ConfigurationSection child = section.getConfigurationSection(key);
            if (child != null) {
                result.put(key, this.sectionToMap(child));
            }
        }
        return result;
    }

    private void saveIndexedData(final File file, final String rootKey,
                                  final Map<String, Map<String, Object>> data, final String label) {
        final YamlConfiguration yaml = new YamlConfiguration();
        for (final Map.Entry<String, Map<String, Object>> entry : data.entrySet()) {
            for (final Map.Entry<String, Object> field : entry.getValue().entrySet()) {
                yaml.set(rootKey + "." + entry.getKey() + "." + field.getKey(), field.getValue());
            }
        }
        this.atomicSave(yaml, file, label);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sectionToMap(final ConfigurationSection section) {
        final Map<String, Object> map = new LinkedHashMap<>();
        for (final String key : section.getKeys(true)) {
            if (!section.isConfigurationSection(key)) {
                map.put(key, section.get(key));
            }
        }
        return map;
    }

    private void atomicSave(final YamlConfiguration yaml, final File target, final String label) {
        final File tempFile = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            yaml.save(tempFile);
            Files.move(tempFile.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException exception) {
            this.logger.warning("無法儲存" + label + "：" + exception.getMessage());
            try {
                if (tempFile.exists()) {
                    Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (final IOException fallback) {
                this.logger.warning(label + " fallback 儲存也失敗：" + fallback.getMessage());
            }
        }
    }
}
