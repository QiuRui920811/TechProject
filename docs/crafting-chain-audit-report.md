# TechProject 合成依賴鏈審計報告

> **掃描範圍**：6 個 YAML 設定檔（tech-blueprints / tech-content-core / tech-content-expansion / tech-content-systems / tech-content-megastructures / tech-content）  
> **統計**：445 物品 · 100 機器 · 414 配方 · 49 藍圖 · 67 成就

---

## 總覽

| 嚴重度 | 數量 | 說明 |
|--------|------|------|
| 🔴 CRITICAL | 4 | 需立即處理的結構性問題（含 1 條已確認死鎖） |
| 🟡 WARNING | 4 類共 ~50 項 | 可能影響可玩性的問題 |
| 🟢 INFO | 3 | 設計提醒 / 假陽性 |

---

## 🔴 CRITICAL — 結構性問題

### C-1. 15 台「register-recipe: false」藍圖機器缺少實際合成配方

**衝擊**：玩家無法透過任何配方系統取得這些機器。  
**說明**：這 15 台機器在 `tech-blueprints.yml` 中設為 `register-recipe: false`（即不透過進階工作台合成），但在所有檔案的 `recipes:` 區塊中均找不到對應的機器組裝配方。  
**與系統機器的對比**：同樣設為 `register-recipe: false` 的 systems 機器（如 quarry_drill、crop_harvester 等）都在 `tech-content-systems.yml` 的 recipes 中有完整的組裝配方。

| # | 機器 ID | 藍圖 ingredient-lines 描述的製作站 | 所屬檔案 |
|---|---------|-------------------------------------|----------|
| 1 | `kiln` | 熔爐後段 | expansion |
| 2 | `cryo_distiller` | 進階工作台後段 | expansion |
| 3 | `gene_splicer` | 進階工作台後段 | expansion |
| 4 | `plasma_refiner` | 聚變後段設備 | expansion |
| 5 | `observatory` | 量子 / 光學支線 | expansion |
| 6 | `survey_beacon` | 觀測站後段 | expansion |
| 7 | `relic_analyzer` | 星球研究前段 | expansion |
| 8 | `xeno_refinery` | 星球材料中段 | expansion |
| 9 | `planetary_forge` | 星球裝備終端 | expansion |
| 10 | `planetary_harvester` | 星球外勤自動化 | expansion |
| 11 | `void_extractor` | 物質編譯器後段 | tech-content |
| 12 | `singularity_press` | 終局物質鏈中段 | tech-content |
| 13 | `chrono_engine` | 軌道 / 時序支線 | tech-content |
| 14 | `starsteel_foundry` | 巨構階段入口 | megastructures |
| 15 | `apex_forge` | 巨構最終鍛造線 | megastructures |

**建議修復**：為每台機器在對應檔案的 `recipes:` 區塊新增組裝配方，格式參考 systems 機器（如 `quarry_drill_recipe`）。例如：

```yaml
# tech-content-expansion.yml → recipes:
kiln_build_recipe:
  machine: furnace             # 或 assembler
  inputs: [ceramic_plate, heat_coil, machine_casing]
  output: kiln
  energy: 16
  guide: "陶瓷板 + 熱線圈 + 機器外殼 → 熔爐 → 工業窯爐"
```

---

### C-2. 循環依賴（實為終局升級配方，建議加註解）

偵測到兩條迴路，但經人工分析後並非「死鎖」；它們是**終局品質升級/替代路線**配方：

| 迴路 | 涉及配方 | 實際含義 |
|------|----------|----------|
| `void_beacon_core` ↔ `energy_node` | `void_beacon_core_recipe`（void_crystal + **energy_node** → void_beacon_core）與 `relay_node_chain`（**void_beacon_core** + logistics_node → energy_node） | energy_node 本身有原始配方 (`assembler_energy_node`)；`relay_node_chain` 是**終局品質替代路線**，不影響初次取得 |
| `item_tube` → `item_tube` | `long_range_tube_recipe`（item_tube + void_crystal → item_tube） | 自我升級配方，item_tube 有原始的 `wire_item_tube` 配方 |

**衝擊**：目前不會造成死鎖，但如果未來移除原始配方，迴路就會變成真正的死結。  
**建議**：在這兩條配方旁加上 `# UPGRADE-LOOP` 或類似註釋，確保維護者不誤刪原始路線。

---

### C-3. 36 台機器既無藍圖也無組裝配方

這 36 台機器在所有檔案中找不到進階工作台藍圖，也找不到機器組裝配方。它們的取得方式可能完全依賴插件的「研究解鎖後自動出現在玩家清單」機制。

**需確認**：如果插件程式碼確實在玩家解鎖後自動給予機器，則此項為 INFO。如果插件需要有配方或藍圖才能放置，則為 CRITICAL。

<details>
<summary>完整清單（點擊展開）</summary>

**核心模組（16 台）**：  
smeltery, solar_array, purifier, centrifuge, ore_washer, bio_lab, polymer_press, wire_mill, chemical_reactor, coolant_mixer, refinery, drone_bay, android_bay, crystal_growth_chamber, field_forge, matter_compiler

**擴充模組（4 台）**：  
insulation_press, biosynth_vat, photon_weaver, data_archive

**巨構模組（6 台）**：  
vacuum_chamber, warp_assembler, event_horizon_smith, continuum_lathe, relic_scanner, anchor_forge

**終局鏈（10 台）**：  
dark_matter_loom, graviton_stabilizer, antimatter_synthesizer, stellar_forge, orbital_printer, void_beacon, nanite_foundry, entropy_chamber, celestial_assembler, omega_fabricator

</details>

---

## 🟡 WARNING — 可能影響可玩性

### W-1. 30 項農作物/食物原料缺少明確產出途徑

以下物品被配方當作 inputs 使用（料理、飲料、加工），但在 YAML 中找不到任何配方能 output 它們。
它們理應由「種植種子 → 自然生長 → 手動/自動收割」取得，但目前只有 **種子的配方**，沒有定義收割後的產出。

> 如果插件程式碼自動處理「種植 → 產出」邏輯（例如右鍵收成、crop_harvester 自動產出），則此項可降為 INFO。

| 類型 | 物品 |
|------|------|
| 基礎蔬果 | tomato, cabbage, corn, onion, lettuce, garlic, bell_pepper |
| 豆類 | soybean_pods |
| 香料/茶 | spiceberry, tea_leaf |
| 一般水果 | cherry, lemon, peach, pear, orange, grape, banana, mango, coconut, strawberry, blueberry, pineapple, watermelon_slice |
| 星球特殊果 | frost_apple, shadow_berry_cluster, lumenfruit, sunflare_fig, stormplum |

### W-2. 3 項原版物品未列入已知清單

| 配方 | 缺少的輸入 | 類型 |
|------|-----------|------|
| `saw_fiber` | `bamboo` | 原版物品 |
| `auto_farm_seed_cluster` | `bone_meal` | 原版物品 |
| `auto_farm_fertilizer` | `bone_meal` | 原版物品 |

**衝擊**：無實際衝擊（這些是 Minecraft 原版物品，玩家可自行取得）。僅為審計工具的已知物品清單不完整。

### W-3. `planetary_gate` 配方引用未定義的中間件 `gravitic_bearing`

配方 `planetary_gate_recipe` 使用 `gravitic_bearing` 作為材料，而 `gravitic_bearing` 的唯一產出是 `observatory_bearing_recipe`（observatory 機器）。  
**依賴鏈**：observatory → graviton_lens + magnetic_ring → gravitic_bearing → planetary_gate

此依賴鏈雖然可行，但 `gravitic_bearing` 本身**未被定義為 items 物品**（沒有 display-name、icon 等），僅作為配方中間產物存在。建議在 expansion 或 core 的 items 區塊補上定義，以便在指南中正確顯示。

### W-4. 🔴 survey_beacon 星球鏈存在**已確認的循環死鎖**

**已人工驗證 — 此為真實死鎖，非假陽性。**

survey_beacon 的解鎖條件：
```yaml
unlock: observatory&item:anti_radiation_helmet&stat:planetary_samples_collected>=6
```

要求玩家**持有** `anti_radiation_helmet`（`item:` 前綴 = 背包中需有此物品）。

但 `anti_radiation_helmet` 的**唯一配方**是：
```yaml
advanced_anti_radiation_helmet:
  machine: planetary_forge          # ← 需要 planetary_forge
  inputs: [exo_frame, seal_fabric, radiation_mesh, glass_panel]
  output: anti_radiation_helmet
```

而 `planetary_forge` 的解鎖鏈為：
```
planetary_forge ← unlock: machine:xeno_refinery & ...
xeno_refinery   ← unlock: machine:relic_analyzer & ...
relic_analyzer  ← unlock: machine:survey_beacon & ...    ⚠️ 回到 survey_beacon！
```

**完整死鎖環路**：
```
survey_beacon
  需要 item:anti_radiation_helmet
    唯一來源 → planetary_forge 配方
      planetary_forge 需要 xeno_refinery
        xeno_refinery 需要 relic_analyzer
          relic_analyzer 需要 survey_beacon   ← 🔄 死鎖
```

**根因**：survey_beacon 同時是星球探索鏈的「第一台機器」，卻要求持有只有鏈後段機器才能生產的裝備。

**建議修復方案**（擇一）：
1. **新增基礎抗輻射頭盔配方**：在 assembler 或 advanced_assembler 中加入一個簡化版 anti_radiation_helmet 配方（例如 `radiation_filter + glass_panel + steel_plate → anti_radiation_helmet`），讓玩家無需 planetary_forge 就能首次取得
2. **移除 survey_beacon 的 item 條件**：改為 `observatory&stat:planetary_samples_collected>=6`，移除 `item:anti_radiation_helmet` 前置
3. **改為 unlock（非 item）條件**：改為 `observatory&anti_radiation_helmet&stat:...`（無 `item:` 前綴），僅要求玩家解鎖過該物品（不需實際持有），然後另外確保基礎解鎖路線（radiation_filter → anti_radiation_helmet 解鎖鏈）是可達的

### W-5. `android_station` 兼具機器定義和配方產出

`android_station` 同時是一台「機器」(在 machines 中定義) 和一個「配方產物」(`advanced_android_station` 配方的 output)。  
這本身不是錯誤，但需確認插件程式碼能正確區分「玩家手持的機器物品」和「已放置的機器方塊」。

---

## 🟢 INFO — 設計提醒

### I-1. 進程鏈完整性驗證通過

所有 100 台機器都可以從 `initial` 開始，透過解鎖條件鏈最終到達。沒有偵測到「完全不可達」的機器。

### I-2. 藍圖 tech: 引用全部有效

所有藍圖中使用 `tech:xxx` 語法引用的物品（如 `tech:steel_plate`、`tech:advanced_circuit` 等）都能在 items 區塊找到對應定義。

### I-3. 所有配方引用的機器都有定義

414 條配方中使用的所有 machine 欄位值，都能在某個檔案的 machines 區塊找到對應定義。沒有「幽靈機器」。

---

## 機器進程鏈圖譜

### 核心鏈（TIER1 → TIER3）

```
initial
├── crusher ──── furnace ──── compressor ──── wire_mill
│   └── ore_washer ── purifier ── centrifuge     └── insulation_press
│                       └── coolant_mixer ── crystal_growth_chamber
│                                                     │
├── electric_saw                                      │
├── recycler                                          │
├── auto_farm ── bio_lab ── gene_splicer ── biosynth_vat
│   │              └── chemical_reactor ── polymer_press
│   │              └── refinery (bio_lab & ore_washer)
│   └── greenhouse
│
├── solar_generator                     steel_plate  
├── coal_generator                        ↓
│                                      assembler ── advanced_assembler ── laser_engraver
│                                         │              │                │
│                                      battery_bank     drone_bay       photon_weaver
│                                         │           android_bay
│                                      solar_array
│                                      energy_node / energy_cable
│
assembler ← steel_plate (compressor)
```

### 高階鏈（TIER3 → TIER4）

```
advanced_assembler → quantum_processor → field_forge
                                            │
coolant_mixer → crystal_growth_chamber ─────┤
                        │                   │
                        └───────┐     ┌─────┘
                          fusion_reactor
                                │
                          matter_compiler
```

### 終局鏈（TIER4 → 終點）

```
matter_compiler
├── void_extractor
│   ├── dark_matter_loom
│   └── void_beacon
│
field_forge → graviton_stabilizer → singularity_press → antimatter_synthesizer
                                                             │
                                                        stellar_forge → orbital_printer
                                                             │              ├── chrono_engine → entropy_chamber
                                                        starsteel_foundry   └── celestial_assembler → omega_fabricator
                                                                                                         │
                                                                                                    apex_forge
```

### 星球探索鏈

```
quantum_processor → observatory → data_archive → relic_scanner (megastructures)
                        │
                   survey_beacon → relic_analyzer → xeno_refinery → planetary_forge → planetary_gate
                        │                                                                    
                   planetary_harvester
```

---

## 修復優先順序建議

| 優先級 | 項目 | 影響範圍 |
|--------|------|----------|
| P0 | **W-4** survey_beacon ↔ anti_radiation_helmet 循環死鎖 | 整條星球探索鏈完全不可進入 |
| P0 | **C-1** 為 15 台 register-recipe:false 機器補上組裝配方 | 玩家無法合成這些機器 |
| P1 | **C-3** 為 36 台無藍圖/無配方機器確認取得機制 | 取決於插件實作 |
| P2 | **C-2** 在 relay_node_chain / long_range_tube_recipe 旁加註 UPGRADE-LOOP | 預防未來誤改 |
| P2 | **W-3** 為 gravitic_bearing 等中間產物補上 items 定義 | 指南顯示不完整 |
| P3 | **W-1** 為 30 項農作物產出加上來源文件說明 | 可讀性改善 |
