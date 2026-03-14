package com.rui.techproject.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Redis 儲存後端。
 * <p>
 * 使用 Paper 內建的 Gson，透過 Jedis 連接 Redis。
 * Redis 適合當快取層或跨服共享玩家資料。
 * </p>
 * <p>
 * 注意：此後端需要伺服器 classpath 上有 {@code redis.clients.jedis} 類別，
 * 若不存在則 init() 時會拋出異常，StorageManager 會自動 fallback 到 YAML。
 * </p>
 */
public final class RedisStorageBackend implements StorageBackend {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final int poolSize;
    private final String keyPrefix;
    private final Logger logger;

    /**
     * Jedis 連線池 — 用 Object 避免編譯期硬依賴。
     * 實際類型為 {@code redis.clients.jedis.JedisPool}。
     */
    private Object jedisPool;

    public RedisStorageBackend(final String host, final int port, final String password,
                                final int database, final int poolSize,
                                final String keyPrefix, final Logger logger) {
        this.host = host;
        this.port = port;
        this.password = password == null || password.isBlank() ? null : password;
        this.database = database;
        this.poolSize = Math.max(2, poolSize);
        this.keyPrefix = keyPrefix == null ? "tp:" : keyPrefix;
        this.logger = logger;
    }

    @Override
    public void init() throws Exception {
        // 動態載入 Jedis，避免編譯期硬依賴
        final Class<?> jedisPoolConfigClass = Class.forName("redis.clients.jedis.JedisPoolConfig");
        final Object config = jedisPoolConfigClass.getDeclaredConstructor().newInstance();
        jedisPoolConfigClass.getMethod("setMaxTotal", int.class).invoke(config, this.poolSize);
        jedisPoolConfigClass.getMethod("setMaxIdle", int.class).invoke(config, this.poolSize);

        final Class<?> jedisPoolClass = Class.forName("redis.clients.jedis.JedisPool");
        if (this.password != null) {
            this.jedisPool = jedisPoolClass
                    .getDeclaredConstructor(jedisPoolConfigClass, String.class, int.class, int.class, String.class, int.class)
                    .newInstance(config, this.host, this.port, 2000, this.password, this.database);
        } else {
            this.jedisPool = jedisPoolClass
                    .getDeclaredConstructor(jedisPoolConfigClass, String.class, int.class, int.class, String.class, int.class)
                    .newInstance(config, this.host, this.port, 2000, (String) null, this.database);
        }
        // 測試連線
        try (AutoCloseable jedis = this.getResource()) {
            final Object pong = jedis.getClass().getMethod("ping").invoke(jedis);
            this.logger.info("[Redis] 連線成功：" + pong);
        }
    }

    @Override
    public void shutdown() {
        if (this.jedisPool != null) {
            try {
                this.jedisPool.getClass().getMethod("close").invoke(this.jedisPool);
            } catch (final Exception ignored) {
            }
        }
    }

    @Override
    public String name() {
        return "redis";
    }

    // ═══════════════════ 玩家進度 ═══════════════════

    @Override
    public Map<String, Object> loadPlayerProgress(final UUID uuid) {
        return this.getJson(this.keyPrefix + "player:" + uuid);
    }

    @Override
    public void savePlayerProgress(final UUID uuid, final Map<String, Object> data) {
        this.setJson(this.keyPrefix + "player:" + uuid, data);
    }

    @Override
    public void deletePlayerProgress(final UUID uuid) {
        this.del(this.keyPrefix + "player:" + uuid);
    }

    // ═══════════════════ 機器 ═══════════════════

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> loadAllMachines() {
        return this.getNestedJson(this.keyPrefix + "machines");
    }

    @Override
    public void saveAllMachines(final Map<String, Map<String, Object>> machines) {
        this.setJson(this.keyPrefix + "machines", new LinkedHashMap<>(machines));
    }

    // ═══════════════════ 科技方塊 ═══════════════════

    @Override
    public Map<String, Map<String, Object>> loadAllTechBlocks() {
        return this.getNestedJson(this.keyPrefix + "tech_blocks");
    }

    @Override
    public void saveAllTechBlocks(final Map<String, Map<String, Object>> blocks) {
        this.setJson(this.keyPrefix + "tech_blocks", new LinkedHashMap<>(blocks));
    }

    // ═══════════════════ 科技作物 ═══════════════════

    @Override
    public Map<String, Map<String, Object>> loadAllCrops() {
        return this.getNestedJson(this.keyPrefix + "crops");
    }

    @Override
    public void saveAllCrops(final Map<String, Map<String, Object>> crops) {
        this.setJson(this.keyPrefix + "crops", new LinkedHashMap<>(crops));
    }

    // ═══════════════════ 星球狀態 ═══════════════════

    @Override
    public Map<String, Object> loadPlanetState() {
        return this.getJson(this.keyPrefix + "planet_state");
    }

    @Override
    public void savePlanetState(final Map<String, Object> state) {
        this.setJson(this.keyPrefix + "planet_state", state);
    }

    // ═══════════════════ Schema 版本 ═══════════════════

    @Override
    public int getSchemaVersion() {
        final Map<String, Object> meta = this.getJson(this.keyPrefix + "schema_meta");
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
        this.setJson(this.keyPrefix + "schema_meta", meta);
    }

    // ═══════════════════ 內部方法 ═══════════════════

    @SuppressWarnings("unchecked")
    private AutoCloseable getResource() {
        try {
            return (AutoCloseable) this.jedisPool.getClass().getMethod("getResource").invoke(this.jedisPool);
        } catch (final Exception exception) {
            throw new RuntimeException("[Redis] 無法取得連線：" + exception.getMessage(), exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(final String key) {
        try (AutoCloseable jedis = this.getResource()) {
            final String json = (String) jedis.getClass().getMethod("get", String.class).invoke(jedis, key);
            if (json == null || json.isBlank()) {
                return null;
            }
            return GSON.fromJson(json, MAP_TYPE);
        } catch (final Exception exception) {
            this.logger.warning("[Redis] GET " + key + " 失敗：" + exception.getMessage());
            return null;
        }
    }

    private void setJson(final String key, final Map<String, ?> data) {
        final String json = GSON.toJson(data);
        try (AutoCloseable jedis = this.getResource()) {
            jedis.getClass().getMethod("set", String.class, String.class).invoke(jedis, key, json);
        } catch (final Exception exception) {
            this.logger.warning("[Redis] SET " + key + " 失敗：" + exception.getMessage());
        }
    }

    private void del(final String key) {
        try (AutoCloseable jedis = this.getResource()) {
            jedis.getClass().getMethod("del", String[].class).invoke(jedis, (Object) new String[]{key});
        } catch (final Exception exception) {
            this.logger.warning("[Redis] DEL " + key + " 失敗：" + exception.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> getNestedJson(final String key) {
        final Map<String, Object> raw = this.getJson(key);
        if (raw == null) {
            return new LinkedHashMap<>();
        }
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
}
