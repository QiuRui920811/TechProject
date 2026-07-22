# 二階 — 中階機器

二階機器提供更精密的加工能力，共 **36 台**，開啟合金、精煉、生質、化學等進階產線，並帶來能源網路、物流管線與信號紅石系統。

> 🎨 大部分機器在物品欄中顯示為自訂頭顱材質，讓每台機器都有獨特外觀。
> 💡「輸入 → 輸出」欄位使用物品的內部識別碼，方便對照配方。

---

## 完整機器一覽

### ⚗️ 加工·冶煉·精煉

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 耗能 |
|---|---|---|---|---|
| 合金爐 | `alloy_furnace` | 雙輸入合金配方 | copper_ingot, tin_ingot → bronze_ingot, solder_ingot, gilded_iron_ingot, ferrosilicon_ingot | 15 EU/t |
| 合金熔煉爐 | `smeltery` | 熔岩流動 | iron_dust, coal_dust → titanium_alloy | 12 EU/t |
| 工業壓縮機 | `industrial_compressor` | 高壓壓製 | iron_ingot, carbon_dust → iron_plate, carbon_plate, carbon_rod | 12 EU/t |
| 工業洗礦機 | `industrial_washer` | 水洗淨化礦粉 | iron_dust, copper_dust → purified_iron, purified_copper | 8 EU/t |
| 工業窯爐 | `kiln` | 陶材燒結 | clay_ball, ceramic_plate, heat_coil → ceramic_plate, thermal_ceramic | 12 EU/t |
| 洗礦機 | `ore_washer` | 水流翻騰 | iron_dust → ore_slurry | 7 EU/t |
| 淨化器 | `purifier` | 淨化泡沫 | ore_slurry → purified_shard, coolant_cell, purifier_membrane | 9 EU/t |
| 精煉塔 | `refinery` | 蒸餾氣柱 | bio_resin, ore_slurry → refined_oil | 10 EU/t |
| 聚合壓床 | `polymer_press` | 黏性壓製 | rubber_sheet, polymer_resin → rubber_sheet, polymer_resin, treated_plastic | 10 EU/t |
| 冷卻混合器 | `coolant_mixer` | 低溫氣霧 | coolant_cell, hydro_gel → coolant_mix | 7 EU/t |
| 絕緣壓製台 | `insulation_press` | 線材包覆壓製 | copper_wire, plastic, logic_gate, servo_motor → insulated_wire, sensor_array, servo_bundle | 11 EU/t |

### 🏭 組裝·合成

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 耗能 |
|---|---|---|---|---|
| 製造機 | `assembler` | 光束組裝 | circuit_board, steel_plate, tin_ingot → machine_component | 15 EU/t |
| 萬用合成站 | `auto_crafter` | 聚合所有加工配方，耗能加倍 | — | 20 EU/t |

### 🌱 生態·生質

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 耗能 |
|---|---|---|---|---|
| 生質實驗室 | `bio_lab` | 綠色培養動畫 | crops, wood_dust → bio_resin, bio_fiber | 11 EU/t |
| 溫室培育艙 | `greenhouse` | 綠光培育 | seed_cluster, fertilizer_mix → growth_lamp, hydro_gel | 8 EU/t |
| 作物收割機 | `crop_harvester` | 收割附近成熟作物並自動重植 | harvest_matrix, agri_module → crops, crop_seeds, bio_fiber | 8 EU/t |
| 自動釣台 | `fishing_dock` | 依附近水域持續取得漁獲 | bait_module, dock_frame → cod, salmon, tropical_fish, pufferfish, nautilus_shell, string | 7 EU/t |
| 伐木機 | `tree_feller` | 搜尋附近樹幹並整株砍伐 | logging_blade, arbor_frame → oak_log, birch_log, spruce_log, jungle_log, acacia_log, dark_oak_log, cherry_log, mangrove_log, oak_sapling | 12 EU/t |

### 🔋 發電·儲能

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 耗能 |
|---|---|---|---|---|
| 生質反應爐 | `bio_reactor` | 生質發酵 | crops → energy_cell | 發電 4 |
| 風力渦輪機 | `wind_turbine` | 風力發電（高空效率更佳） | wind → energy_cell | 發電 3 |
| 太陽能陣列 | `solar_array` | 多段光能動畫 | starlight_plate → energy_cell | 發電 3 |
| 電池庫 | `battery_bank` | 能源儲存與充放電站，放在發電區與耗能區之間當緩衝最有效 | energy_cell, lithium_cell, tin_plate → capacitor_bank | 2 EU/t |

### 🔌 能源·物流

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 耗能 |
|---|---|---|---|---|
| 導能線纜 | `energy_cable` | 能量穿透傳輸 | flux_link → flux_link | — |
| 能源節點 | `energy_node` | 遠距能量橋接 | energy_cell → energy_cell | — |
| 物流導管 | `item_tube` | 物品穿透傳輸 | pressure_tube → pressure_tube | — |
| 物流節點 | `logistics_node` | 遠距物流橋接 | machine_component → machine_component | — |
| 倉儲匯流站 | `storage_hub` | 接收任意物流並作為中型緩衝倉 | storage_crate, machine_casing, wiring_bundle → machine_component, steel_plate, circuit_board, crops, plastic | — |
| 真空吸入口 | `vacuum_inlet` | 將周圍掉落物直接吸入產線 | vacuum_core, pressure_tube → junk, crops, iron_dust, copper_dust, plastic, circuit_board | 3 EU/t |
| 銷毀節點 | `trash_node` | 永久銷毀所有匯入的物品 | machine_casing, lava_bucket → — | — |
| 漏斗介面 | `hopper_interface` | 原版漏斗/箱子 ↔ 科技機器物品橋接 | — | — |
| 溢出處理器 | `overflow_handler` | 主路滿載自動轉送備用路線 | — | — |

### 🚦 信號·紅石

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 耗能 |
|---|---|---|---|---|
| 信號線 | `signal_wire` | 傳輸信號（0-15），連接信號設備 | signal_relay → signal_relay | — |
| 信號發射器 | `signal_emitter` | 讀取相鄰機器狀態 → 輸出信號 0-15 | signal_relay → signal_relay | 1 EU/t |
| 信號接收器 | `signal_receiver` | 接收信號 → 控制相鄰機器開關 | signal_relay → signal_relay | 1 EU/t |
| 信號反轉器 | `signal_inverter` | 反轉信號（NOT 邏輯閘）：輸出 = 15 - 輸入 | signal_relay → signal_relay | — |
| 紅石轉接器 | `redstone_adapter` | 紅石 ↔ 科技信號雙向橋接（方塊自身發 0-15 紅石） | signal_relay → signal_relay | — |

---

## 重點提示

### 洗礦提純鏈

`ore_washer`（洗礦機）把礦粉洗成礦漿，再由 `purifier`（淨化器）淨化成淨化碎晶，是進入三階離心、化學產線前的提純前置。`industrial_washer`（工業洗礦機）則直接把礦粉洗成純鐵/純銅。

### 萬用合成站

`auto_crafter`（萬用合成站）聚合**所有加工類機器**的配方於一台，能耗為原配方的 **2 倍**，適合中期過渡或臨時生產不常用的配方；長期量產仍建議使用專用機器。

### 信號系統起步

`signal_wire`／`signal_emitter`／`signal_receiver`／`signal_inverter` 組成一套純伺服器端的邏輯電路：發射器讀取機器狀態輸出 0-15 信號，經信號線傳遞，由接收器控制其他機器開關；`redstone_adapter`（紅石轉接器）則讓這套系統與原版紅石雙向互通。

### 銷毀節點

`trash_node`（銷毀節點）**永久銷毀所有匯入的物品**，是物流網路末端的處理器，搭配 `overflow_handler`（溢出處理器）自動丟棄多餘副產物。

> ⚠️ 銷毀的物品無法復原，過濾器設定務必謹慎。
