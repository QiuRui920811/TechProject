# TechProject V1 一次性重構架構規格

## 目的

本文件定義 TechProject 的一次性大改版方向，目標不是局部補丁，而是把整個插件收斂成一套：

- 內容分類一致
- GUI 一致
- 文案一致
- 原版物 / 科技物視覺清楚分離
- progression 清楚
- Mk 系列定位清楚
- 採集 → 加工 → 高階裝配 路線固定
- 中後期內容可持續擴充

本規格對應目前專案現況：高資料驅動、內容量已足夠，但執行層與 GUI 規則仍過度集中在 `MachineService`，且 schema、導引、分類與 progression 未完全統一。

---

## 一、最終產品定位

TechProject 不走「迷你 Slimefun」路線，而是走：

> 更聚焦、更直觀、更一致、為伺服器量身打造的科技生存插件。

### 核心定位

- 保留「不用裝模組也有科技服體驗」
- 不追求 Slimefun 式超大雜燴
- 追求玩家一眼就能理解的科技鏈
- 每個物品、每台機器、每條支線都要有明確定位

### 體驗目標

玩家應能快速理解：

1. 這個物品是原版資源還是科技材料
2. 這台機器屬於哪條支線
3. 它吃什麼、產什麼、需要什麼能源
4. 下一步該做哪台機器
5. 中後期目標是什麼

---

## 二、現況總覽

### 內容量

目前內容規模已可支撐完整大改版：

- 約 184 個物品
- 約 78 台機器
- 約 197 筆配方
- 約 21 個成就
- 少量硬編碼互動儀式

### 核心模組

- 啟動 / 註冊：`TechProjectPlugin`, `TechRegistry`
- 研究 / 科技書：`TechBookService`, `PlayerProgressService`, `AchievementService`
- 藍圖 / 工作台：`BlueprintService`, `TechListener`
- 機器 / 能源 / 物流 / 世界互動：`MachineService`, `PlacedMachine`
- 科技方塊追蹤：`PlacedTechBlockService`
- 物品 / GUI / 視覺：`ItemFactoryUtil`
- 附加互動：`TechAddonService`

### 主要內容來源

- `src/main/resources/tech-content-core.yml`
- `src/main/resources/tech-content-systems.yml`
- `src/main/resources/tech-content.yml`
- `src/main/resources/tech-content-expansion.yml`
- `src/main/resources/tech-content-megastructures.yml`
- `src/main/resources/tech-blueprints.yml`
- `src/main/resources/tech-guides*.yml`

---

## 三、目前最需要解決的架構問題

### 1. 內容分類不顯式

目前許多分類依賴 ID 推斷，而不是資料欄位明確標註。

後果：
- GUI 分類易出錯
- progression 分支難重排
- 科技書導引難精準

### 2. 文案語系混用

同一內容區存在：
- 純英文
- 英中混排
- 純中文

後果：
- 視覺雜訊大
- 玩家難判斷原版物 / 科技物

### 3. 原版物 / 科技物視覺混淆

科技物大量借原版材質與外觀，原版資源又曾被套用科技式名稱。

後果：
- 玩家誤判資源性質
- progression 理解成本高

### 4. 機器資料模型與真實行為不一致

部分機器 YAML 中有 `inputs / outputs`，但 GUI 或實作根本不吃該欄位。

後果：
- lore 誤導
- 科技書資訊不可信
- 機器 archetype 難抽象化

### 5. GUI 樣式與互動規則不一致

不同機器頁面資訊密度與可操作性差異大。

後果：
- 玩家學習成本高
- bug 容易藏在特例 UI 中

### 6. 藍圖與取得模式混亂

目前同時混有：
- 進階工作台直作
- 機器組裝
- fallback 藍圖
- 僅預覽不可製作

後果：
- 玩家難理解「這東西到底在哪做」

### 7. progression 是全域，不是分支式

目前階級門檻較像全專案完成度，而不是分支推進。

後果：
- 玩家想走能源線，卻被其他支線卡住

### 8. Mk 系列沒有成體系

目前只有採礦線比較像系列升級，其他支線則缺乏一致的 family 規則。

### 9. 視覺欄位與玩法欄位耦合

例如是否追蹤科技方塊，曾受 icon 類型影響。

### 10. Addon / 儀式內容尚未資料化

主體已 YAML 化，但互動儀式仍屬例外系統。

---

## 四、一次性重構的總原則

### 原則 A：先統一定義，再改內容

先統一 schema、分類、命名、GUI 與 acquisition-mode，再改 progression。

### 原則 B：先做主線，再補支線

主線應先確立：
- 開局加工
- 基礎能源
- 中間件加工
- 物流骨幹
- 精密 / 量子
- 終局 / 巨構

### 原則 C：先做辨識，再做數值

先讓玩家知道這是什麼，再調平衡。

### 原則 D：原版物維持原版、科技物才科技化

不要再讓原版資源長得像科技專用礦。

### 原則 E：所有系統都要可觀測

玩家必須能看懂：
- 為何停機
- 缺什麼
- 目前狀態
- 下一步去哪裡

---

## 五、最終內容分層模型

所有物品與機器統一納入以下四層：

### Layer 1：原版資源

定義：
- Minecraft 原版物品
- 不加科技專屬命名
- 不加科技 lore
- 維持原版外觀與名稱

例：
- `coal`
- `diamond`
- `raw_iron`
- `quartz`

### Layer 2：科技中間材料

定義：
- 產線加工材料
- 有統一色系與命名規則
- 有用途說明

例：
- `iron_dust`
- `ore_slurry`
- `steel_plate`
- `machine_component`

### Layer 3：高階科技核心

定義：
- 中後期高價值部件
- 應有更明顯的科技視覺與識別

例：
- `quantum_chip`
- `fusion_core`
- `field_plate`
- `control_unit`

### Layer 4：終局文明級部件

定義：
- 終局或巨構支線核心資源
- 應有最強視覺識別與獨特定位

例：
- `omega_core`
- `archive_core`
- `dimension_anchor`

---

## 六、Canonical Content Schema 提案

所有 item / machine / interaction 最終都應顯式擁有下列 metadata：

### 共通欄位

- `id`
- `display-name-key`
- `tier`
- `system-group`
- `guide-category`
- `family`
- `role`
- `visual-tier`
- `unlock`
- `acquisition-mode`

### 物品欄位

- `item-class`
  - `vanilla-resource`
  - `tech-material`
  - `core-component`
  - `endgame-component`
- `icon`
- `item-model`
- `nexo-id`
- `description-key`
- `use-cases`

### 機器欄位

- `machine-archetype`
  - `processor`
  - `generator`
  - `relay`
  - `storage`
  - `field`
  - `research`
  - `ritual`
- `block`
- `ui-layout`
- `energy-profile`
- `io-profile`
- `multiblock-profile`
- `state-profile`
- `recipes-visible`

### 配方欄位

- `craft-mode`
  - `advanced_workbench`
  - `machine_assembly`
  - `multiblock`
  - `ritual`
  - `uncraftable-preview`
- `input-types`
- `output-type`
- `energy`
- `guide-key`

---

## 七、第一階段：一致性重構

## 目標

建立全專案一致的物品、GUI、狀態、視覺與藍圖規則。

### A. 物品分類一致

#### 交付標準

- 所有物品明確標示 `item-class`
- 原版資源不再套科技名稱
- 科技材料一定有科技視覺、用途文案、分類標記
- 高階核心與終局件有獨立色彩規範

#### 改動方向

- `TechRegistry`：改成讀顯式欄位，不再猜分類
- `ItemFactoryUtil`：依 `item-class` 控制名稱、lore、顏色、外觀
- 所有 content YAML 補齊分類 metadata

### B. GUI 一致

#### 統一規則

- 裝飾格不可拿
- 提示格不可拿
- 配方錯誤結果不可拿
- 輸入區 / 輸出區 / 燃料區 / 升級區固定位置與顏色邏輯
- 每個 archetype 使用固定版型

#### 機器 archetype 對應版型

- `processor`：輸入 / 輸出 / 升級 / 配方
- `generator`：燃料 / 電量 / 狀態
- `relay`：方向 / 流量 / 過濾
- `storage`：容量 / 匯流狀態
- `field`：結構 / 範圍 / 狀態 / 現場條件
- `research`：研究 / 導引 / 解鎖

### C. 狀態文案一致

統一狀態詞典：

- `待機`
- `運轉中`
- `缺電`
- `缺燃料`
- `缺件`
- `輸出已滿`
- `條件不足`
- `未解鎖`

所有機器只使用這些標準詞，不再各寫各的。

### D. 原版物 / 科技物視覺分離

#### 原版資源
- 原版名稱
- 原版貼圖
- 不加科技色名

#### 科技材料
- 有科技色系
- 有固定 lore 模板
- 優先補 item-model

#### 最低標準
- 主線物品 100% 可辨識
- 支線物品至少名稱與 lore 不可混淆

### E. acquisition-mode 一致

所有內容必須明示取得方式：

- `advanced_workbench`
- `machine_assembly`
- `field_collection`
- `ritual`
- `research_reward`
- `uncraftable_preview`

玩家不需要猜這東西在哪做。

---

## 八、第二階段：progression 重構

## 目標

把現在的內容量整理成一條清楚主線與多條支線，而不是機器名互相亂解鎖。

### A. 主線章節

建議主線章節固定為：

1. 開局與研究台
2. 基礎加工
3. 基礎能源
4. 標準工業中間件
5. 物流與儲存骨幹
6. 戶外採集自動化
7. 精密與量子
8. 虛空 / 奇點 / 反物質
9. 巨構與文明級終局

### B. 支線分組

每個內容必須歸屬某條 branch：

- `processing`
- `energy`
- `agri-bio`
- `field-harvest`
- `logistics`
- `quantum-precision`
- `void-endgame`
- `mega-structures`

### C. tier gate 改為分支式

禁止再使用純全域完成度門檻。

改成：
- branch 進度達標才開下一段
- 主線節點達標可解鎖橫向支線

### D. Mk 系列規範

Mk 系列只應存在於「同 family 的垂直升級」：

- `quarry_drill_mk1`
- `quarry_drill_mk2`
- `quarry_drill_mk3`

若其他機器沒有明確 family，不要硬套 Mk。

### E. 採集 → 加工 → 高階裝配 路線明確化

所有主線產線遵守：

1. 世界採集 / 原版素材
2. 一次加工
3. 二次中間件
4. 核心零件
5. 高階裝配
6. 終局核心

### F. 研究書變成導引系統

研究書不只是資料庫，而是導航器。

每個條目應顯示：
- 它是做什麼的
- 它屬於哪條 branch
- 它前面要先做什麼
- 它後面會接去哪裡
- 建議下一步是什麼

### G. 起始解鎖重設

開局只保留必要最小集合：
- 科技書
- 研究台入口
- 極少數開局加工件

不要一開始直接送太多核心機器。

---

## 九、第三階段：內容量擴充

## 目標

在規則已穩定的前提下擴充內容，而不是一邊擴充一邊修補舊架構。

### A. 更多機器支線

優先補「每條 branch 的缺口」，不是隨機加新機器。

#### 建議優先補的類型
- 中期過渡機器
- branch 專用升級件
- 終局前置穩定器
- 多方塊高級裝配站

### B. 更多能源型態

目前能源應整理成完整族譜：
- 開局燃燒 / 太陽能
- 中期儲能 / 陣列 / 天候發電
- 後期場域 / 聚變 / 終局能源

每階段都應有：
- 發電
- 儲能
- 傳輸
- 調度

### C. 更多物流玩法

物流不只搬運，還應有：
- 過濾
- 分流
- 優先級
- 匯流
- 長距中繼
- 緩衝與倉儲邏輯

### D. 更多中後期目標

終局應不是「更多機器」，而是更明確的文明級成果：
- 軌道 / 衛星
- 巨構裝置
- 能源奇點
- 終局展示型建築
- 資料觀測 / 歷史檔案 / 維度控制

### E. 儀式與 addon 內容資料化

把 `TechAddonService` 目前硬編碼內容搬回 registry / YAML。

### F. Guide 全面重寫

每條 branch 至少提供：
- 入門說明
- 常見瓶頸
- 推薦順序
- 範例產線
- 升級時機

---

## 十、最終系統群組定義

### 1. 開局加工
- `research_desk`
- `crusher`
- `furnace`
- `electric_saw`
- `recycler`

### 2. 能源
- `solar_generator`
- `coal_generator`
- `battery_bank`
- `solar_array`
- `storm_turbine`
- `fusion_reactor`
- `energy_node`
- `energy_cable`

### 3. 標準工業加工
- `compressor`
- `assembler`
- `smeltery`
- `advanced_assembler`
- `ore_washer`
- `purifier`
- `centrifuge`
- `wire_mill`
- `polymer_press`
- `chemical_reactor`
- `coolant_mixer`
- `refinery`
- `laser_engraver`

### 4. 農業 / 生質
- `auto_farm`
- `greenhouse`
- `bio_lab`
- 相關 expansion 支線機器

### 5. 戶外採集
- `crop_harvester`
- `tree_feller`
- `fishing_dock`
- `mob_collector`
- `vacuum_inlet`
- `quarry_drill` family

### 6. 物流 / 儲存
- `logistics_node`
- `item_tube`
- `storage_hub`
- `filter_router`
- `splitter_node`
- `industrial_bus`

### 7. 精密 / 量子
- `quantum_processor`
- `crystal_growth_chamber`
- `field_forge`
- `matter_compiler`
- `drone_bay`
- 相關 precision / observatory / archive 線

### 8. 終局 / 巨構
- 虛空、奇點、反物質、stellar、omega、mega structures 全部內容

---

## 十一、程式重構方向

### A. `TechRegistry`

改為：
- 只做 schema 驗證與載入
- 不再猜測分類與 guide-category

### B. `ItemFactoryUtil`

改為：
- 依 `item-class` / `machine-archetype` / `visual-tier` 決定外觀
- 統一 lore 模板
- 嚴格分離原版物與科技物

### C. `TechBookService`

改為：
- 以 `branch + chapter + tier` 組書
- 研究書具備下一步導引能力
- 不再只是資料列表

### D. `BlueprintService`

改為：
- 所有內容都明示 `craft-mode`
- 不再大量 fallback 推導

### E. `MachineService`

改為：
- 拆分成多個 handler：
  - `ProcessingMachineRuntime`
  - `GeneratorRuntime`
  - `FieldMachineRuntime`
  - `LogisticsRuntime`
  - `QuarryRuntime`
- GUI 與 tick 邏輯分離
- 懸浮字與狀態詞典統一

### F. `PlacedTechBlockService`

改為：
- 依顯式欄位決定是否追蹤放置
- 不再依賴 icon 是否為 block

### G. `TechAddonService`

改為：
- registry 化 / YAML 化
- 不再成為硬編碼例外系統

---

## 十二、執行順序

### Phase 1：一致性
1. 定義 canonical schema
2. 補齊所有內容 metadata
3. 統一 locale / naming
4. 統一 item 視覺策略
5. 統一 GUI archetype
6. 統一 acquisition-mode
7. 解耦 icon 與玩法欄位

### Phase 2：progression
1. 劃分 branch
2. 重排主線章節
3. 重算研究成本
4. 重設起始解鎖
5. 導入 branch gate
6. 重寫科技書導引
7. 明確定義 Mk family

### Phase 3：內容量
1. 補 branch 缺口
2. 擴能源族譜
3. 擴物流玩法
4. 補中後期文明級目標
5. interaction YAML 化
6. guide 全面重寫
7. 視覺與資源包全覆蓋

---

## 十三、驗收標準

### 一致性驗收
- 玩家能一眼分辨原版物 / 科技物
- 所有 GUI 規則一致
- 所有狀態詞一致
- 所有取得方式能在科技書內直接看懂

### progression 驗收
- 每條 branch 有清楚起點與終點
- 玩家不會因 unrelated 支線卡主線
- 研究書會告訴玩家下一步
- Mk 系列只有在合理 family 中出現

### 內容量驗收
- 每條 branch 不再斷層
- 中後期目標清楚
- 能源 / 物流 / 採集都能形成完整玩法閉環

---

## 結論

這份重構不是「把零碎 bug 補起來」，而是把 TechProject 從：

- 有很多內容但規則不夠統一

重構成：

- 有完整主線、清楚支線、強一致性、可持續擴充的科技插件。
