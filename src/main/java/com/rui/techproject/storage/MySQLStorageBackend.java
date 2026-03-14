package com.rui.techproject.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Logger;

/**
 * MySQL / MariaDB 儲存後端。
 * <p>
 * 使用簡易連線池（不依賴 HikariCP），資料以 JSON 序列化後存入 TEXT 欄位。
 * 若伺服器有 HikariCP 可以升級，目前這個輕量實作足以應付中小規模。
 * </p>
 */
public final class MySQLStorageBackend implements StorageBackend {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
    private static final Type NESTED_MAP_TYPE = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();

    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final int poolSize;
    private final Logger logger;
    private final String tablePrefix;

    private BlockingQueue<Connection> connectionPool;

    public MySQLStorageBackend(final String host, final int port, final String database,
                                final String username, final String password,
                                final int poolSize, final String tablePrefix, final Logger logger) {
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.poolSize = Math.max(2, poolSize);
        this.tablePrefix = tablePrefix == null ? "tp_" : tablePrefix;
        this.logger = logger;
    }

    @Override
    public void init() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        this.connectionPool = new ArrayBlockingQueue<>(this.poolSize);
        final String url = "jdbc:mysql://" + this.host + ":" + this.port + "/" + this.database
                + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true&characterEncoding=utf8mb4";
        for (int i = 0; i < this.poolSize; i++) {
            this.connectionPool.add(DriverManager.getConnection(url, this.username, this.password));
        }
        this.createTables();
        this.logger.info("[MySQL] 連線池已建立，共 " + this.poolSize + " 條連線");
    }

    @Override
    public void shutdown() {
        if (this.connectionPool == null) {
            return;
        }
        for (final Connection connection : this.connectionPool) {
            try {
                connection.close();
            } catch (final SQLException ignored) {
            }
        }
        this.connectionPool.clear();
    }

    @Override
    public String name() {
        return "mysql";
    }

    // ═══════════════════ 玩家進度 ═══════════════════

    @Override
    public Map<String, Object> loadPlayerProgress(final UUID uuid) {
        return this.loadJson(this.tablePrefix + "player_progress", uuid.toString());
    }

    @Override
    public void savePlayerProgress(final UUID uuid, final Map<String, Object> data) {
        this.saveJson(this.tablePrefix + "player_progress", uuid.toString(), data);
    }

    @Override
    public void deletePlayerProgress(final UUID uuid) {
        this.deleteRow(this.tablePrefix + "player_progress", uuid.toString());
    }

    // ═══════════════════ 機器 ═══════════════════

    @Override
    public Map<String, Map<String, Object>> loadAllMachines() {
        return this.loadNestedJson(this.tablePrefix + "global_state", "machines");
    }

    @Override
    public void saveAllMachines(final Map<String, Map<String, Object>> machines) {
        this.saveNestedJson(this.tablePrefix + "global_state", "machines", machines);
    }

    // ═══════════════════ 科技方塊 ═══════════════════

    @Override
    public Map<String, Map<String, Object>> loadAllTechBlocks() {
        return this.loadNestedJson(this.tablePrefix + "global_state", "tech_blocks");
    }

    @Override
    public void saveAllTechBlocks(final Map<String, Map<String, Object>> blocks) {
        this.saveNestedJson(this.tablePrefix + "global_state", "tech_blocks", blocks);
    }

    // ═══════════════════ 科技作物 ═══════════════════

    @Override
    public Map<String, Map<String, Object>> loadAllCrops() {
        return this.loadNestedJson(this.tablePrefix + "global_state", "crops");
    }

    @Override
    public void saveAllCrops(final Map<String, Map<String, Object>> crops) {
        this.saveNestedJson(this.tablePrefix + "global_state", "crops", crops);
    }

    // ═══════════════════ 星球狀態 ═══════════════════

    @Override
    public Map<String, Object> loadPlanetState() {
        return this.loadJson(this.tablePrefix + "global_state", "planet_state");
    }

    @Override
    public void savePlanetState(final Map<String, Object> state) {
        this.saveJson(this.tablePrefix + "global_state", "planet_state", state);
    }

    // ═══════════════════ Schema 版本 ═══════════════════

    @Override
    public int getSchemaVersion() {
        final Map<String, Object> meta = this.loadJson(this.tablePrefix + "global_state", "schema_meta");
        if (meta == null) {
            return 0;
        }
        final Object version = meta.get("schema-version");
        if (version instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    @Override
    public void setSchemaVersion(final int version) {
        final Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("schema-version", version);
        this.saveJson(this.tablePrefix + "global_state", "schema_meta", meta);
    }

    // ═══════════════════ 內部方法 ═══════════════════

    private void createTables() throws SQLException {
        final Connection connection = this.borrow();
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + this.tablePrefix + "player_progress ("
                    + "id VARCHAR(64) PRIMARY KEY,"
                    + "data MEDIUMTEXT NOT NULL,"
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            statement.executeUpdate("CREATE TABLE IF NOT EXISTS " + this.tablePrefix + "global_state ("
                    + "id VARCHAR(64) PRIMARY KEY,"
                    + "data MEDIUMTEXT NOT NULL,"
                    + "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } finally {
            this.release(connection);
        }
    }

    private Map<String, Object> loadJson(final String table, final String id) {
        final Connection connection = this.borrow();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT data FROM " + table + " WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return GSON.fromJson(resultSet.getString("data"), MAP_TYPE);
                }
            }
        } catch (final SQLException exception) {
            this.logger.warning("[MySQL] 載入 " + table + "/" + id + " 失敗：" + exception.getMessage());
        } finally {
            this.release(connection);
        }
        return null;
    }

    private void saveJson(final String table, final String id, final Map<String, Object> data) {
        final String json = GSON.toJson(data);
        final Connection connection = this.borrow();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + table + " (id, data) VALUES (?, ?) ON DUPLICATE KEY UPDATE data = VALUES(data)")) {
            statement.setString(1, id);
            statement.setString(2, json);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            this.logger.warning("[MySQL] 儲存 " + table + "/" + id + " 失敗：" + exception.getMessage());
        } finally {
            this.release(connection);
        }
    }

    private void deleteRow(final String table, final String id) {
        final Connection connection = this.borrow();
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM " + table + " WHERE id = ?")) {
            statement.setString(1, id);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            this.logger.warning("[MySQL] 刪除 " + table + "/" + id + " 失敗：" + exception.getMessage());
        } finally {
            this.release(connection);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> loadNestedJson(final String table, final String id) {
        final Map<String, Object> raw = this.loadJson(table, id);
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        // Gson 反序列化的結果已經是 Map<String, Map<String, Object>>
        final Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> innerMap) {
                final Map<String, Object> typed = new LinkedHashMap<>();
                for (final Map.Entry<?, ?> inner : innerMap.entrySet()) {
                    typed.put(String.valueOf(inner.getKey()), inner.getValue());
                }
                result.put(entry.getKey(), typed);
            }
        }
        return result;
    }

    private void saveNestedJson(final String table, final String id,
                                 final Map<String, Map<String, Object>> data) {
        this.saveJson(table, id, new LinkedHashMap<>(data));
    }

    private Connection borrow() {
        try {
            final Connection connection = this.connectionPool.take();
            if (connection.isClosed() || !connection.isValid(2)) {
                try {
                    connection.close();
                } catch (final SQLException ignored) {
                }
                final String url = "jdbc:mysql://" + this.host + ":" + this.port + "/" + this.database
                        + "?useSSL=false&allowPublicKeyRetrieval=true&autoReconnect=true&characterEncoding=utf8mb4";
                return DriverManager.getConnection(url, this.username, this.password);
            }
            return connection;
        } catch (final Exception exception) {
            throw new RuntimeException("[MySQL] 無法取得連線：" + exception.getMessage(), exception);
        }
    }

    private void release(final Connection connection) {
        if (connection != null) {
            try {
                if (!connection.isClosed()) {
                    this.connectionPool.offer(connection);
                }
            } catch (final SQLException ignored) {
            }
        }
    }
}
