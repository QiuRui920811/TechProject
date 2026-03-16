# TechProject 記憶體洩漏與效能審計報告

> **目標平台**: Paper / Folia — Java 21  
> **審計範圍**: `src/main/java/com/rui/techproject/` 下 47 個 Java 原始碼檔案  
> **排程器**: 全部使用 `SafeScheduler`（Folia region-aware），無 `BukkitRunnable`

---

## 一、嚴重問題（Critical）

### 1. `PlayerProgressService.cache` — 永不驅逐的玩家快取

| 項目 | 值 |
|------|-----|
| **檔案** | `service/PlayerProgressService.java` |
| **欄位** | `private final Map<UUID, PlayerProgress> cache = new ConcurrentHashMap<>()` |
| **風險** | 🔴 HIGH — 無限增長 |

**問題**：`ensureLoaded()` 使用 `computeIfAbsent` 將玩家進度載入 `cache`，`save()` 僅寫入後端但**不會從 `cache` 中移除**。`onQuit` (TechListener L147) 僅呼叫 `save()`，不呼叫 `cache.remove()`。

每個 `PlayerProgress` 包含 4 個 `ConcurrentHashMap.newKeySet` + 1 個 `ConcurrentHashMap<String, Long>`（stats），佔用 ~1–5 KB。

**影響**：伺服器執行期間，每個加入過的玩家都會永久保留在記憶體中。100 名不同玩家 ≈ 100–500 KB，但在大型伺服器上（數千名不同玩家）會持續累積，在 restart 前永不釋放。

**修復建議**：
```java
// PlayerProgressService.java — 新增方法
public void evict(UUID playerId) {
    this.cache.remove(playerId);
}

// TechListener.java — onQuit 加入
this.plugin.getPlayerProgressService().save(playerId);
this.plugin.getPlayerProgressService().evict(playerId);  // ✚ 新增
```

---

### 2. `PlanetService` — 5+ 個玩家 UUID 鍵映射在離線時不清理

| 項目 | 值 |
|------|-----|
| **檔案** | `service/PlanetService.java` |
| **風險** | 🔴 HIGH — 多映射永不清理 |

以下 UUID 鍵映射**從未在 `PlayerQuitEvent` 中清理**，且 `shutdown()` 只在插件停用時呼叫：

| 映射 | 行號 | 說明 |
|------|------|------|
| `personalHarvestCooldowns` `Map<UUID, Map<LocationKey, Long>>` | L132 | 每個玩家包含多個 LocationKey 條目，內部 Map 也會增長 |
| `playerActivatedRuins` `Map<UUID, Set<LocationKey>>` | L124 | 每次啟用遺跡就累加 |
| `boundaryWarningCooldowns` `Map<UUID, Long>` | L140 | 單值，但持續累積 |
| `cuisineWardExpiries` `Map<UUID, Map<HazardType, Long>>` | L141 | 嵌套映射 |
| `pendingHarvests` `Map<UUID, PendingHarvest>` | L137 | 含 LocationKey + 物品資料 |

**問題**：`TechListener.onQuit()` **完全沒有呼叫 PlanetService 的任何清理方法**。比對 quit handler（TechListener L133–148），僅清理了 listener 本身的映射 + machineService + cookingService + achievementGuiService + playerProgressService。PlanetService 被遺漏。

**影響**：每個離線玩家在這 5 個映射中殘留條目，直到 `shutdown()` 才全部清除。`personalHarvestCooldowns` 和 `playerActivatedRuins` 的嵌套結構尤其危險，每個條目 ~200–800 bytes。

**修復建議**：
```java
// PlanetService.java — 新增方法
public void cleanupPlayer(UUID playerId) {
    this.personalHarvestCooldowns.remove(playerId);
    this.playerActivatedRuins.remove(playerId);
    this.boundaryWarningCooldowns.remove(playerId);
    this.cuisineWardExpiries.remove(playerId);
    this.pendingHarvests.remove(playerId);
    this.travelingPlayers.remove(playerId);
    this.travelPlayerStates.remove(playerId);
    this.openPlanetaryGateMenus.remove(playerId);
}

// TechListener.java — onQuit 加入
this.plugin.getPlanetService().cleanupPlayer(playerId);  // ✚ 新增
```

---

### 3. `PlanetService.processedDatapackChunks` / `decoratedPlanetChunks` — 無界集合

| 項目 | 值 |
|------|-----|
| **檔案** | `service/PlanetService.java` |
| **欄位** | `processedDatapackChunks` (L126), `decoratedPlanetChunks` (L127) |
| **類型** | `ConcurrentHashMap.newKeySet<String>` |
| **風險** | 🟠 MEDIUM-HIGH — 與探索區塊數成正比 |

**問題**：兩個集合以 chunk key 字串（如 `"world:32:64"`）為元素，只有 `add`，**永不 `remove` 或 `clear`**。`shutdown()` 也不清除這兩個集合。每個字串 ~40–80 bytes。

**影響**：隨著玩家探索星球地圖，集合無限增長。若星球世界面積為 10,000×10,000（~39,000 個 chunk），則佔用 ~3–6 MB。長時間運行伺服器可能更多。

**修復建議**：
- 在 `shutdown()` 中加入 `this.processedDatapackChunks.clear(); this.decoratedPlanetChunks.clear();`
- 考慮改為 `WeakHashMap` 或 bloom filter，或在世界卸載時清除
- 或考慮將此資訊持久化到 storage backend 而非記憶體中

---

## 二、中度問題（Medium）

### 4. `PlanetService.techItemStackCache` — 無界物品快取

| 項目 | 值 |
|------|-----|
| **檔案** | `service/PlanetService.java` |
| **欄位** | `private final Map<String, ItemStack> techItemStackCache = new ConcurrentHashMap<>()` |
| **風險** | 🟡 MEDIUM |

**問題**：快取以物品 ID 為鍵，儲存 `ItemStack` 克隆。無 TTL、無容量限制。雖然物品 ID 數量有限（由 `TechRegistry` 定義），但如果 ID 空間很大或含動態字串，可能增長。

**注意**：`shutdown()` 有呼叫 `this.techItemStackCache.clear()`。風險取決於快取鍵來源——若僅為靜態 registry ID，此快取實際上是有界的且問題較小。

---

### 5. `MachineService.openViews` / `viewersByMachine` — RECIPES 模式下關閉不清理

| 項目 | 值 |
|------|-----|
| **檔案** | `service/MachineService.java` |
| **欄位** | `openViews` (ConcurrentHashMap<UUID, MachineViewSession>), `viewersByMachine` (ConcurrentHashMap<LocationKey, Set<UUID>>) |
| **風險** | 🟡 MEDIUM |

**問題**：`closeMachineView()`（L1106）在 `session.mode() != ViewMode.MAIN` 時提前 return，**不呼叫 `trackViewClose()`**。因此，若玩家在 RECIPES 模式下直接關閉背包或斷線，`openViews` 和 `viewersByMachine` 中的條目不會被清理。

`cleanupPlayer()`（L1484）僅清理 BossBar + `playerLookingAt`，**不清理 `openViews`**。

**流程重現**：
1. 玩家打開機器選單 → `trackViewOpen`（MAIN 模式）
2. 點擊配方按鈕 → `trackViewOpen`（RECIPES 模式，替換 MAIN）
3. 直接按 ESC 關閉 → `closeMachineView` 檢查 `mode != MAIN`，return
4. 玩家離線 → `cleanupPlayer` 不清理 `openViews`
5. `openViews` 殘留 `MachineViewSession` 條目

**修復建議**：
```java
// MachineService.java — closeMachineView 修改
public void closeMachineView(final Player player, final Inventory inventory) {
    final MachineViewSession session = this.openViews.get(player.getUniqueId());
    if (session == null) return;
    // 若非 MAIN 模式，仍需清理追蹤 ✚
    if (session.mode() != ViewMode.MAIN) {
        this.trackViewClose(player.getUniqueId());
        return;
    }
    if (session.inventory() != inventory) return;
    this.trackViewClose(player.getUniqueId());
    // ... 原有邏輯 ...
}
```

---

### 6. `TechBookService.pendingSearchInput` — 玩家離線時可能殘留

| 項目 | 值 |
|------|-----|
| **檔案** | `service/TechBookService.java` |
| **欄位** | `pendingSearchInput` (ConcurrentHashMap.newKeySet<UUID>) |
| **風險** | 🟡 MEDIUM-LOW |

**問題**：玩家在搜尋等待狀態（`pendingSearchInput.add` L754）時離線，`consumeSearchInput` 永不被呼叫。`clearBookView()`（在 onInventoryClose 中呼叫）僅清理 `openBookViewPlayers` 和 `openBookInventories`，**不清理 `pendingSearchInput`**。

`onQuit` handler 未明確呼叫 `pendingSearchInput.remove()`。

**修復建議**：
```java
// TechBookService.java — clearBookView 修改
public void clearBookView(UUID playerId) {
    this.openBookViewPlayers.remove(playerId);
    this.openBookInventories.remove(playerId);
    this.pendingSearchInput.remove(playerId);  // ✚ 新增
}
```

---

### 7. `ItemSearchService.anvilSearchPlayers` — 斷線可能不觸發清理

| 項目 | 值 |
|------|-----|
| **檔案** | `service/ItemSearchService.java` |
| **欄位** | `anvilSearchPlayers` (ConcurrentHashMap.newKeySet<UUID>) |
| **風險** | 🟢 LOW |

**問題**：`clearState()` 僅在 `InventoryCloseEvent` 中呼叫（TechListener L596），而非 `PlayerQuitEvent`。正常情況下 Bukkit/Paper 會在玩家斷線前觸發 `InventoryCloseEvent`，但非正常斷連（timeout/crash）可能跳過。每個條目僅一個 UUID（16 bytes），風險極低。

---

### 8. `TechPlaceholderExpansion` 重複註冊

| 項目 | 值 |
|------|-----|
| **檔案** | `TechProjectPlugin.java` |
| **位置** | `reloadProjectData()` 方法 |
| **風險** | 🟡 MEDIUM-LOW |

**問題**：`reloadProjectData()` 每次呼叫都 `new TechPlaceholderExpansion(this).register()`。PlaceholderAPI 的 `register()` 應會替換舊實例（因為 `persist()` 返回 true），但舊的 `TechPlaceholderExpansion` 物件可能不會立即被 GC（若 PlaceholderAPI 內部仍持有引用）。

**影響**：每次 `/techproject reload` 可能產生一個殘留的 expansion 實例。

**修復建議**：在 `reloadProjectData()` 中先呼叫 `unregister()` 或快取 expansion 實例，僅在首次時 `register()`。

---

## 三、排定任務分析（Scheduled Tasks）

### 全部排定任務清單

| 來源 | 類型 | 頻率 | 取消機制 |
|------|------|------|----------|
| `TechProjectPlugin` L160 | `runGlobalTimer` | 每 5 分鐘 (`6000L`) — autoSave | Folia 自動取消（插件停用） |
| `MachineService` L262 | `runGlobalTimer` | 每 `machine-tick-interval`（預設 20L） — tickAllMachines | Folia 自動取消 |
| `MachineService` L263 | `runGlobalTimer` | 每 4L — tickMachineLookAt | Folia 自動取消 |
| `PlanetService` 5 個 timer | `runGlobalTimer` | 40L / 2L / 20L / 40L / 40L | Folia 自動取消 |
| `MeteorService` | `runGlobalTimer` | 每 2L | Folia 自動取消 |
| `CookingSession` (per-session) | `runEntityTimer` | 每 ticks — cooking tick | `cancelSession()` 手動取消 ✅ |
| TechListener 各處 | `runEntityDelayed` | 一次性延遲（grapple 160L, 各 UI 1L） | 一次性，自動完成 |

**結論**：所有排定任務均使用 Folia 的 `ScheduledTask`，插件停用時 Folia 會自動取消所有由該插件排程的任務。`CookingService` 也有手動取消邏輯（`shutdown()` 中遍歷所有 session 並取消 task）。**無排定任務洩漏**。

**注意**：排定任務本身不洩漏，但 timer 可能無限存取已洩漏的集合（如 `PlanetService` 的 hazard/ambience timer 每 40 tick 遍歷所有 `boundaryWarningCooldowns` 條目——已洩漏的條目會持續被遍歷，造成不必要的 CPU 開銷）。

---

## 四、Storage / 連線管理分析

### MySQLStorageBackend

| 項目 | 值 |
|------|-----|
| **檔案** | `storage/MySQLStorageBackend.java` |
| **連線池** | `ArrayBlockingQueue<Connection>`，固定大小 |
| **風險** | 🟢 LOW |

- `shutdown()` 正確關閉所有連線並清空池
- `borrow()` 驗證連線有效性（`isValid(2)` 使用 2 秒 timeout）
- `release()` 將連線歸還池中
- **微小風險**：`isValid(2)` 可能導致呼叫線程阻塞 2 秒。在 Folia 的全域 region 線程上（tick 線程）可能造成 TPS 下降。建議改為非同步存取或降低 timeout。

### RedisStorageBackend

| 項目 | 值 |
|------|-----|
| **檔案** | `storage/RedisStorageBackend.java` |
| **風險** | 🟢 OK |

- 透過反射使用 Jedis，`shutdown()` 正確呼叫 `close()`
- 無洩漏風險

### YamlStorageBackend

- 純檔案操作，無持久連線
- 🟢 OK

---

## 五、所有集合欄位完整清單

### TechListener.java

| 欄位 | 類型 | 鍵 | quit 清理 | close 清理 |
|------|------|-----|-----------|------------|
| `grappleCooldowns` | CHM<UUID, Long> | player UUID | ✅ L135 | — |
| `thrusterCooldowns` | CHM<UUID, Long> | player UUID | ✅ L136 | — |
| `jetpackCooldowns` | CHM<UUID, Long> | player UUID | ✅ L137 | — |
| `mobilityGracePeriods` | CHM<UUID, Long> | player UUID | ✅ L138 | — |
| `managedJetpackFlight` | CHM.KeySet<UUID> | player UUID | ✅ L139 | — |
| `artifactCooldowns` | CHM<UUID, Map> | player UUID | ✅ L140 | — |
| `talismanCooldowns` | CHM<UUID, Map> | player UUID | ✅ L141 | — |
| `lastEquipmentTick` | CHM<UUID, Long> | player UUID | ✅ L142 | — |
| `magnetDisabled` | CHM.KeySet<UUID> | player UUID | ✅ L143 | — |
| `grappleArrowOwners` | CHM<UUID, UUID> | arrow UUID | —¹ | — |

> ¹ `grappleArrowOwners` 以箭矢 UUID 為鍵，在 `ProjectileHitEvent` (L1499) 和延遲任務 (L1487, 160 tick) 中清理。玩家離線時箭矢仍存在所以會正常清理。**極低風險**。

### PlayerProgressService.java

| 欄位 | 類型 | 鍵 | quit 清理 |
|------|------|-----|-----------|
| `cache` | CHM<UUID, PlayerProgress> | player UUID | ❌ **永不清理** |

### MachineService.java

| 欄位 | 類型 | 鍵 | quit/close 清理 |
|------|------|-----|----------------|
| `machines` | CHM<LocationKey, PlacedMachine> | location | N/A（世界資料）|
| `openViews` | CHM<UUID, MachineViewSession> | player UUID | ⚠ 部分（RECIPES 模式不清理）|
| `viewersByMachine` | CHM<LocationKey, Set<UUID>> | location → UUID | ⚠ 同上 |
| `machineDisplays` | CHM<LocationKey, UUID> | location | ✅ removeAllDisplays |
| `machineVisualDisplays` | CHM<LocationKey, UUID> | location | ✅ removeAllDisplays |
| `quarryBusy` | CHM.KeySet<LocationKey> | location | N/A |
| `quarryWarmedUp` | CHM.KeySet<LocationKey> | location | N/A |
| `machineBossBars` | CHM<UUID, BossBar> | player UUID | ✅ cleanupPlayer L1484 |
| `playerLookingAt` | CHM<UUID, LocationKey> | player UUID | ✅ cleanupPlayer L1477 |

### PlanetService.java

| 欄位 | 類型 | 鍵 | quit 清理 | shutdown 清理 |
|------|------|-----|-----------|---------------|
| `planets` | LHM<String, PlanetDef> | planet ID | N/A（靜態）| — |
| `activatedRuins` | CHM.KS<LocationKey> | location | N/A | — |
| `playerActivatedRuins` | CHM<UUID, Set<LK>> | player UUID | ❌ | — |
| `gateDestinations` | CHM<LK, String> | location | N/A | — |
| `generatedPlanetWorlds` | CHM.KS<String> | world name | N/A | — |
| `processedDatapackChunks` | CHM.KS<String> | chunk key | ❌ 無界 | ❌ |
| `decoratedPlanetChunks` | CHM.KS<String> | chunk key | ❌ 無界 | ❌ |
| `travelingPlayers` | CHM.KS<UUID> | player UUID | join 恢復 | ✅ |
| `travelVessels` | CHM<UUID, TravelVessel> | player UUID | join 恢復 | ✅ |
| `travelPlayerStates` | CHM<UUID, TPS> | player UUID | join 恢復 | ✅ |
| `openPlanetaryGateMenus` | CHM<UUID, LK> | player UUID | close 清理 | — |
| `personalHarvestCooldowns` | CHM<UUID, Map<LK, Long>> | player UUID | ❌ | — |
| `machineHarvestCooldowns` | CHM<LK, Long> | location | N/A | — |
| `harvestNodeDisplays` | CHM<LK, UUID> | location | N/A | ✅ |
| `pendingHarvests` | CHM<UUID, PendingHarvest> | player UUID | ❌ | ✅ |
| `fruitNodeRegrowths` | CHM<LK, Long> | location | N/A | ✅ |
| `boundaryWarningCooldowns` | CHM<UUID, Long> | player UUID | ❌ | ✅ |
| `cuisineWardExpiries` | CHM<UUID, Map> | player UUID | ❌ | ✅ |
| `techItemStackCache` | CHM<String, ItemStack> | item ID | N/A | ✅ |

### CookingService.java

| 欄位 | 類型 | 鍵 | quit 清理 |
|------|------|-----|-----------|
| `activeSessions` | CHM<LocationKey, CookingSession> | location | ✅ cancelSession |
| `recipes` | ArrayList<CookingRecipe> | — | N/A（靜態）|

### AchievementGuiService.java

| 欄位 | 類型 | 鍵 | quit 清理 |
|------|------|-----|-----------|
| `playerStates` | CHM<UUID, GuiState> | player UUID | ✅ clearState |

### TechBookService.java

| 欄位 | 類型 | 鍵 | quit 清理 |
|------|------|-----|-----------|
| `guidesByLocale` | LHM | locale | N/A（靜態）|
| `machineStructurePreviews` | LHM | machine ID | N/A（靜態）|
| `pendingSearchInput` | CHM.KS<UUID> | player UUID | ⚠ 不清理 |
| `openBookViewPlayers` | CHM.KS<UUID> | player UUID | ✅ clearBookView |
| `openBookInventories` | CHM<UUID, Inv> | player UUID | ✅ clearBookView |

### ItemSearchService.java

| 欄位 | 類型 | 鍵 | quit 清理 |
|------|------|-----|-----------|
| `anvilSearchPlayers` | CHM.KS<UUID> | player UUID | ⚠ 僅靠 InvClose |

### MeteorService.java

| 欄位 | 類型 | 鍵 | shutdown 清理 |
|------|------|-----|---------------|
| `activeMeteors` | COWAL<ActiveMeteor> | — | ✅ |

### 其他服務

| 服務 | 集合 | 風險 |
|------|------|------|
| TechRegistry | 4 個 volatile LHM（靜態定義）| 🟢 OK |
| PlacedTechBlockService | CHM<LocationKey, String>（世界資料）| 🟢 OK |
| TechCropService | LHM（靜態）+ CHM<LK, String>（世界資料）| 🟢 OK |
| TitleService | LHM（靜態）| 🟢 OK |
| BlueprintService | LHM（靜態）| 🟢 OK |
| ItemFactoryUtil | LHM（guiButtons/guiIcons/guiPanes — 靜態定義）| 🟢 OK |

> CHM = ConcurrentHashMap, LHM = LinkedHashMap, KS = KeySet, LK = LocationKey, COWAL = CopyOnWriteArrayList

---

## 六、修復優先級摘要

| # | 嚴重度 | 問題 | 影響 |
|---|--------|------|------|
| 1 | 🔴 CRITICAL | PlayerProgressService.cache 永不驅逐 | 記憶體持續增長 |
| 2 | 🔴 CRITICAL | PlanetService 5+ 個 UUID 映射不清理 | 記憶體持續增長 |
| 3 | 🟠 HIGH | processedDatapackChunks / decoratedPlanetChunks 無界 | 與探索面積正比增長 |
| 4 | 🟡 MEDIUM | MachineService openViews RECIPES 模式洩漏 | 少量殘留 |
| 5 | 🟡 MEDIUM | TechBookService pendingSearchInput 不清理 | 極少條目 |
| 6 | 🟡 MEDIUM-LOW | TechPlaceholderExpansion 重複註冊 | reload 時產生 |
| 7 | 🟢 LOW | ItemSearchService 僅靠 InvClose 清理 | 極低 |
| 8 | 🟢 LOW | grappleArrowOwners 箭矢 UUID 殘留 | 8 秒自動清理 |

---

## 七、附錄：無問題項目

- **排定任務**：全部使用 Folia ScheduledTask，插件停用自動取消。CookingService 有額外手動取消。✅
- **Storage 連線**：MySQL 連線池正確 shutdown，Redis 正確 close，YAML 無連線。✅
- **靜態集合**：TechRegistry 的 volatile 引用替換策略正確，舊 Map 可被 GC。✅
- **World/Chunk/Entity 引用**：MachineService 以 `LocationKey` (record) 儲存而非直接持有 `World`/`Chunk` 引用。MeteorService 持有 `World` 引用但流星生命週期短且有 cleanup。PlanetService 的 `PlanetDefinition` 使用世界名稱字串而非 World 物件。✅
- **Event Listener**：單一 `TechListener` 在 `onEnable` 註冊，不動態增減。✅
