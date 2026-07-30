# 一階 — 基礎機器

一階機器是科技線的起點，共 **18 台**。它們使用原版材料合成，負責最基本的資源加工、發電與養殖，是踏入科技工業的第一步。

> 🎨 大部分機器在物品欄中顯示為自訂頭顱材質，讓每台機器都有獨特外觀。
> 💡「輸入 → 輸出」欄位使用物品的內部識別碼，方便對照配方。

---

## 完整機器一覽

### ⚙️ 加工冶煉

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 耗能 |
|---|---|---|---|---|
| 粉碎機 | `crusher` | 旋轉渦輪 | iron_ore, copper_ore → iron_dust, copper_dust | 5 EU/t |
| 熔爐 | `furnace` | 火焰動畫 | iron_dust, copper_dust → iron_ingot, copper_ingot | 10 EU/t |
| 壓縮機 | `compressor` | 金屬壓縮動畫 | iron_ingot, iron_plate, tin_ingot → iron_plate, steel_plate, compressed_iron_bundle, dense_carbon_block, tin_plate | 8 EU/t |
| 拉線機 | `wire_mill` | 高速牽引 | copper_ingot, steel_plate, tin_ingot → copper_wire, steel_wire, wiring_bundle, tin_wire | 9 EU/t |
| 電鋸 | `electric_saw` | 鋸片旋轉 | oak_planks → wood_dust | 4 EU/t |
| 回收機 | `recycler` | 回收動畫 | wood_dust, paper, coal_dust → recycled_material, plastic, rust_filter | 3 EU/t |
| 自動農場 | `auto_farm` | 作物生長動畫 | crop_seeds → crops | 3 EU/t |
| 焦爐 | `coke_oven` | 無需電力，緩慢碳化 | coal, charcoal → coke, creosote_oil | — |

### 🔋 發電機

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 發電 |
|---|---|---|---|---|
| 煤炭發電機 | `coal_generator` | 火焰 + 蒸氣 | coal → energy_cell | 2 EU/t |
| 岩漿發電機 | `lava_generator` | 岩漿燃燒 | lava_bucket → energy_cell | 5 EU/t |
| 太陽能發電機 | `solar_generator` | 太陽光束 | sunlight → energy_cell | 3 EU/t |
| 蒸汽鍋爐 | `steam_boiler` | 燃燒產生蒸汽發電 | coal, coke, water_bucket → — | 8 EU/t |

### 🐔 基因·養殖

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 耗能 |
|---|---|---|---|---|
| 基因定序器 | `genetic_sequencer` | 分析 DNA 序列 | pocket_chicken → pocket_chicken | 3 EU/t |
| 私人雞舍 | `private_coop` | 基因繁殖 | pocket_chicken, pocket_chicken → pocket_chicken | 2 EU/t |
| 激發室 | `excitation_chamber` | 激發基因產出資源 | pocket_chicken → — | 4 EU/t |

### 🛠️ 研究·輔助

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 耗能 |
|---|---|---|---|---|
| 研究台 | `research_desk` | 研究場（8 格內 +1 科技經驗/秒） | tech_book, copper_ingot → — | — |
| 散熱片 | `heat_sink` | 被動冷卻相鄰機器 -3 熱/秒 | — | — |
| 方塊破壞器 | `block_breaker` | 收到紅石訊號時破壞前方一格方塊，掉落物存入內部容器 | — | — |

---

## 重點提示

### 粉碎 → 熔煉 起手線

`crusher`（粉碎機）把礦石研磨成粉，再交給 `furnace`（熔爐）冶煉成錠，通常能取得比原版更高的產出。這條「粉碎 → 熔煉」是所有科技產線的第一步。

### 壓縮機的配方鎖定

`compressor`（壓縮機）的「鐵板→鋼板」與「鐵板×2→壓實鐵胚」使用相同材料，開始加工前請用 **配方鎖定**（機器 GUI 中的比較器圖示）選擇想要的配方。`auto_farm`（自動農場）、`recycler`（回收機）同樣有相同材料的多配方，詳見 [配方鎖定](README.md#配方鎖定)。

### 口袋雞基因線

`genetic_sequencer` → `private_coop` → `excitation_chamber` 是口袋雞的基因產線：定序分析 DNA、雞舍繁殖優良個體，最後由激發室把基因轉換成資源產出。

### 焦爐與蒸汽鍋爐

`coke_oven`（焦爐）**不需要電力**，靠時間緩慢碳化煤炭得到焦炭與雜酚油；焦炭再送進 `steam_boiler`（蒸汽鍋爐）燃燒發電。這是最早期不依賴電網也能運轉的加工鏈。
