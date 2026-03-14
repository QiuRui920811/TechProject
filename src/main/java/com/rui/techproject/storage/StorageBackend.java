package com.rui.techproject.storage;

import java.util.Map;
import java.util.UUID;

/**
 * 統一資料儲存介面。
 * <p>
 * 每種 backend（YAML / MySQL / Redis）皆實作此介面，
 * 使得 service 層不需要知道底層儲存細節。
 * </p>
 */
public interface StorageBackend {

    /**
     * 初始化連線池或目錄結構等。
     */
    void init() throws Exception;

    /**
     * 關閉連線池等資源。
     */
    void shutdown();

    // ═══════════════════ 玩家進度 ═══════════════════

    /**
     * 載入指定玩家的進度資料。
     *
     * @return key-value 形式的扁平資料，如果不存在則返回 null
     */
    Map<String, Object> loadPlayerProgress(UUID uuid);

    /**
     * 儲存指定玩家的進度資料。
     */
    void savePlayerProgress(UUID uuid, Map<String, Object> data);

    /**
     * 刪除指定玩家的進度資料。
     */
    void deletePlayerProgress(UUID uuid);

    // ═══════════════════ 機器 ═══════════════════

    /**
     * 載入所有已放置的機器資料。
     *
     * @return 以序號為 key、機器扁平資料為 value 的 Map
     */
    Map<String, Map<String, Object>> loadAllMachines();

    /**
     * 儲存所有已放置的機器資料（全量覆蓋）。
     */
    void saveAllMachines(Map<String, Map<String, Object>> machines);

    // ═══════════════════ 科技方塊 ═══════════════════

    Map<String, Map<String, Object>> loadAllTechBlocks();

    void saveAllTechBlocks(Map<String, Map<String, Object>> blocks);

    // ═══════════════════ 科技作物 ═══════════════════

    Map<String, Map<String, Object>> loadAllCrops();

    void saveAllCrops(Map<String, Map<String, Object>> crops);

    // ═══════════════════ 星球狀態 ═══════════════════

    Map<String, Object> loadPlanetState();

    void savePlanetState(Map<String, Object> state);

    // ═══════════════════ Schema 版本 ═══════════════════

    /**
     * 取得目前儲存的 schema 版本號。
     */
    int getSchemaVersion();

    /**
     * 設定 schema 版本號。
     */
    void setSchemaVersion(int version);

    /**
     * 取得此 backend 的名稱。
     */
    String name();
}
