package com.rui.techproject.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * 資料版本遷移管理器。
 * <p>
 * 每次啟動時檢查 backend 中的 {@code schema_version}，
 * 按順序執行所有新的遷移（migration），確保改動機器 ID、欄位名稱等時
 * 不會導致舊資料損壞。
 * </p>
 *
 * <h3>新增遷移方法</h3>
 * <pre>
 * // 在 registerMigrations() 內新增：
 * this.register(3, "描述", backend -&gt; {
 *     // 例如把 machine-id "old_name" 改為 "new_name"
 *     Map&lt;String, Map&lt;String, Object&gt;&gt; machines = backend.loadAllMachines();
 *     for (Map&lt;String, Object&gt; machine : machines.values()) {
 *         if ("old_name".equals(machine.get("machine-id"))) {
 *             machine.put("machine-id", "new_name");
 *         }
 *     }
 *     backend.saveAllMachines(machines);
 * });
 * </pre>
 */
public final class MigrationManager {

    /** 當前 schema 版本。每次新增遷移時遞增此值。 */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final Logger logger;
    private final List<Migration> migrations = new ArrayList<>();

    public MigrationManager(final Logger logger) {
        this.logger = logger;
        this.registerMigrations();
    }

    /**
     * 檢查並依序執行未套用的遷移。
     *
     * @return 套用的遷移數量
     */
    public int runPendingMigrations(final StorageBackend backend) {
        final int currentVersion = backend.getSchemaVersion();
        if (currentVersion >= CURRENT_SCHEMA_VERSION) {
            return 0;
        }

        int applied = 0;
        for (final Migration migration : this.migrations) {
            if (migration.version() <= currentVersion) {
                continue;
            }
            try {
                this.logger.info("[Migration] 執行 V" + migration.version() + "：" + migration.description());
                migration.action().accept(backend);
                backend.setSchemaVersion(migration.version());
                applied++;
                this.logger.info("[Migration] V" + migration.version() + " 完成。");
            } catch (final Exception exception) {
                this.logger.severe("[Migration] V" + migration.version() + " 失敗：" + exception.getMessage());
                this.logger.severe("[Migration] 遷移中斷！請修復後重啟。");
                break;
            }
        }

        if (applied > 0) {
            this.logger.info("[Migration] 共套用 " + applied + " 個遷移，schema 版本：" + backend.getSchemaVersion());
        }
        return applied;
    }

    // ═══════════════════ 遷移定義 ═══════════════════

    private void registerMigrations() {
        // V1: 初始化 — 設定 schema_version（首次執行）
        this.register(1, "初始化 schema 版本標記", backend -> {
            // 首次遷移：不需要改資料，只是建立版本號
            // 之後的遷移才會實際修改資料
        });

        // ═══ 未來的遷移範例（取消註解即可使用）═══
        //
        // this.register(2, "solar_panel 改名 solar_generator", backend -> {
        //     final Map<String, Map<String, Object>> machines = backend.loadAllMachines();
        //     for (final Map<String, Object> machine : machines.values()) {
        //         if ("solar_panel".equals(machine.get("machine-id"))) {
        //             machine.put("machine-id", "solar_generator");
        //         }
        //     }
        //     backend.saveAllMachines(machines);
        // });
        //
        // this.register(3, "新增 recipe-version 欄位到所有機器", backend -> {
        //     final Map<String, Map<String, Object>> machines = backend.loadAllMachines();
        //     for (final Map<String, Object> machine : machines.values()) {
        //         machine.putIfAbsent("recipe-version", 1);
        //     }
        //     backend.saveAllMachines(machines);
        // });
    }

    private void register(final int version, final String description, final Consumer<StorageBackend> action) {
        this.migrations.add(new Migration(version, description, action));
    }

    private record Migration(int version, String description, Consumer<StorageBackend> action) {}
}
