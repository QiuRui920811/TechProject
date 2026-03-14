package com.rui.techproject.storage;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.logging.Logger;

/**
 * 統一儲存管理器。
 * <p>
 * 根據 config.yml 的 {@code storage.backend} 設定選擇後端，
 * 若指定的後端初始化失敗會自動 fallback 到 YAML。
 * </p>
 */
public final class StorageManager {

    private final File dataFolder;
    private final Logger logger;
    private StorageBackend backend;

    public StorageManager(final File dataFolder, final Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    /**
     * 根據 config 初始化後端。
     */
    public void init(final FileConfiguration config) {
        final String backendName = config.getString("storage.backend", "yaml").toLowerCase().trim();

        switch (backendName) {
            case "mysql", "mariadb" -> {
                try {
                    final MySQLStorageBackend mysql = new MySQLStorageBackend(
                            config.getString("storage.mysql.host", "localhost"),
                            config.getInt("storage.mysql.port", 3306),
                            config.getString("storage.mysql.database", "techproject"),
                            config.getString("storage.mysql.username", "root"),
                            config.getString("storage.mysql.password", ""),
                            config.getInt("storage.mysql.pool-size", 10),
                            config.getString("storage.mysql.table-prefix", "tp_"),
                            this.logger
                    );
                    mysql.init();
                    this.backend = mysql;
                    this.logger.info("[Storage] 使用 MySQL 後端");
                } catch (final Exception exception) {
                    this.logger.warning("[Storage] MySQL 初始化失敗，回退到 YAML：" + exception.getMessage());
                    this.initYaml();
                }
            }
            case "redis" -> {
                try {
                    final RedisStorageBackend redis = new RedisStorageBackend(
                            config.getString("storage.redis.host", "localhost"),
                            config.getInt("storage.redis.port", 6379),
                            config.getString("storage.redis.password", ""),
                            config.getInt("storage.redis.database", 0),
                            config.getInt("storage.redis.pool-size", 8),
                            config.getString("storage.redis.key-prefix", "tp:"),
                            this.logger
                    );
                    redis.init();
                    this.backend = redis;
                    this.logger.info("[Storage] 使用 Redis 後端");
                } catch (final Exception exception) {
                    this.logger.warning("[Storage] Redis 初始化失敗，回退到 YAML：" + exception.getMessage());
                    this.initYaml();
                }
            }
            default -> this.initYaml();
        }
    }

    /**
     * 取得目前使用的後端。
     */
    public StorageBackend getBackend() {
        return this.backend;
    }

    /**
     * 關閉後端。
     */
    public void shutdown() {
        if (this.backend != null) {
            this.backend.shutdown();
        }
    }

    private void initYaml() {
        final YamlStorageBackend yaml = new YamlStorageBackend(this.dataFolder, this.logger);
        yaml.init();
        this.backend = yaml;
        this.logger.info("[Storage] 使用 YAML 後端");
    }
}
