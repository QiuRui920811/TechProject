# ⚙️ 機器全索引

> 完整機器索引，共 **152** 台機器，依科技階級分組。
> 詳細頁：[一階](tier-1.md) / [二階](tier-2.md) / [三階](tier-3.md) / [四階](tier-4.md)

| 階級 | 對應分類 | 數量 |
|---|---|---|
| 一階 | BASIC | 18 台 |
| 二階 | INTERMEDIATE | 36 台 |
| 三階 | ADVANCED | 66 台 |
| 四階 | TIER4 | 32 台 |
| **合計** | | **152 台** |

> 💡「輸入 → 輸出」與「英文 ID」欄位使用機器的內部識別碼，方便對照配方與指令；一般遊玩時看中文名即可。

---

## 一階（BASIC，18 台）

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 耗能 |
|---|---|---|---|---|
| 自動農場 | `auto_farm` | 作物生長動畫 | crop_seeds → crops | 3 EU/t |
| 方塊破壞器 | `block_breaker` | 收到紅石訊號時破壞前方一格方塊，掉落物存入內部容器 | — | — |
| 煤炭發電機 | `coal_generator` | 火焰 + 蒸氣 | coal → energy_cell | 發電 2 |
| 焦爐 | `coke_oven` | 無需電力，緩慢碳化 | coal, charcoal → coke, creosote_oil | — |
| 壓縮機 | `compressor` | 金屬壓縮動畫 | iron_ingot, iron_plate, tin_ingot → iron_plate, steel_plate, compressed_iron_bundle, dense_carbon_block, tin_plate | 8 EU/t |
| 粉碎機 | `crusher` | 旋轉渦輪 | iron_ore, copper_ore → iron_dust, copper_dust | 5 EU/t |
| 電鋸 | `electric_saw` | 鋸片旋轉 | oak_planks → wood_dust | 4 EU/t |
| 激發室 | `excitation_chamber` | 激發基因產出資源 | pocket_chicken → — | 4 EU/t |
| 熔爐 | `furnace` | 火焰動畫 | iron_dust, copper_dust → iron_ingot, copper_ingot | 10 EU/t |
| 基因定序器 | `genetic_sequencer` | 分析 DNA 序列 | pocket_chicken → pocket_chicken | 3 EU/t |
| 散熱片 | `heat_sink` | 被動冷卻相鄰機器 -3 熱/tick | — | — |
| 岩漿發電機 | `lava_generator` | 岩漿燃燒 | lava_bucket → energy_cell | 發電 5 |
| 私人雞舍 | `private_coop` | 基因繁殖 | pocket_chicken, pocket_chicken → pocket_chicken | 2 EU/t |
| 回收機 | `recycler` | 回收動畫 | wood_dust, paper, coal_dust → recycled_material, plastic, rust_filter | 3 EU/t |
| 研究台 | `research_desk` | 研究場（8 格內 +1 科技經驗/秒） | tech_book, copper_ingot → — | — |
| 太陽能發電機 | `solar_generator` | 太陽光束 | sunlight → energy_cell | 發電 3 |
| 蒸汽鍋爐 | `steam_boiler` | 燃燒產生蒸汽發電 | coal, coke, water_bucket → — | 發電 8 |
| 拉線機 | `wire_mill` | 高速牽引 | copper_ingot, steel_plate, tin_ingot → copper_wire, steel_wire, wiring_bundle, tin_wire | 9 EU/t |

## 二階（INTERMEDIATE，36 台）

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 耗能 |
|---|---|---|---|---|
| 合金爐 | `alloy_furnace` | 雙輸入合金配方 | copper_ingot, tin_ingot → bronze_ingot, solder_ingot, gilded_iron_ingot, ferrosilicon_ingot | 15 EU/t |
| 製造機 | `assembler` | 光束組裝 | circuit_board, steel_plate, tin_ingot → machine_component | 15 EU/t |
| 萬用合成站 | `auto_crafter` | 聚合所有加工配方，耗能加倍 | — | 20 EU/t |
| 電池庫 | `battery_bank` | 能源儲存與充放電站。將機器物品放入輸入槽可吸收其殘餘電量；將科技裝備放入輸入槽可從電池庫充電至滿。放在發電區與耗能區之間當緩衝最有效。 | energy_cell, lithium_cell, tin_plate → capacitor_bank | 2 EU/t |
| 生質實驗室 | `bio_lab` | 綠色培養動畫 | crops, wood_dust → bio_resin, bio_fiber | 11 EU/t |
| 生質反應爐 | `bio_reactor` | 生質發酵 | crops → energy_cell | 發電 4 |
| 冷卻混合器 | `coolant_mixer` | 低溫氣霧 | coolant_cell, hydro_gel → coolant_mix | 7 EU/t |
| 作物收割機 | `crop_harvester` | 收割附近成熟作物並自動重植 | harvest_matrix, agri_module → crops, crop_seeds, bio_fiber | 8 EU/t |
| 導能線纜 | `energy_cable` | 能量穿透傳輸 | flux_link → flux_link | — |
| 能源節點 | `energy_node` | 遠距能量橋接 | energy_cell → energy_cell | — |
| 自動釣台 | `fishing_dock` | 依附近水域持續取得漁獲 | bait_module, dock_frame → cod, salmon, tropical_fish, pufferfish, nautilus_shell, string | 7 EU/t |
| 溫室培育艙 | `greenhouse` | 綠光培育 | seed_cluster, fertilizer_mix → growth_lamp, hydro_gel | 8 EU/t |
| 漏斗介面 | `hopper_interface` | 原版漏斗/箱子 ↔ 科技機器物品橋接 | — | — |
| 工業壓縮機 | `industrial_compressor` | 高壓壓製 | iron_ingot, carbon_dust → iron_plate, carbon_plate, carbon_rod | 12 EU/t |
| 工業洗礦機 | `industrial_washer` | 水洗淨化礦粉 | iron_dust, copper_dust → purified_iron, purified_copper | 8 EU/t |
| 絕緣壓製台 | `insulation_press` | 線材包覆壓製 | copper_wire, plastic, logic_gate, servo_motor → insulated_wire, sensor_array, servo_bundle | 11 EU/t |
| 物流導管 | `item_tube` | 物品穿透傳輸 | pressure_tube → pressure_tube | — |
| 工業窯爐 | `kiln` | 陶材燒結 | clay_ball, ceramic_plate, heat_coil → ceramic_plate, thermal_ceramic | 12 EU/t |
| 物流節點 | `logistics_node` | 遠距物流橋接 | machine_component → machine_component | — |
| 洗礦機 | `ore_washer` | 水流翻騰 | iron_dust → ore_slurry | 7 EU/t |
| 溢出處理器 | `overflow_handler` | 主路滿載自動轉送備用路線 | — | — |
| 聚合壓床 | `polymer_press` | 黏性壓製 | rubber_sheet, polymer_resin → rubber_sheet, polymer_resin, treated_plastic | 10 EU/t |
| 淨化器 | `purifier` | 淨化泡沫 | ore_slurry → purified_shard, coolant_cell, purifier_membrane | 9 EU/t |
| 紅石轉接器 | `redstone_adapter` | 紅石 ↔ 科技信號雙向橋接（方塊自身發 0-15 紅石） | signal_relay → signal_relay | — |
| 精煉塔 | `refinery` | 蒸餾氣柱 | bio_resin, ore_slurry → refined_oil | 10 EU/t |
| 信號發射器 | `signal_emitter` | 讀取相鄰機器狀態 → 輸出信號 0-15 | signal_relay → signal_relay | 1 EU/t |
| 信號反轉器 | `signal_inverter` | 反轉信號（NOT 邏輯閘）：輸出 = 15 - 輸入 | signal_relay → signal_relay | — |
| 信號接收器 | `signal_receiver` | 接收信號 → 控制相鄰機器開關 | signal_relay → signal_relay | 1 EU/t |
| 信號線 | `signal_wire` | 傳輸信號（0-15），連接信號設備 | signal_relay → signal_relay | — |
| 合金熔煉爐 | `smeltery` | 熔岩流動 | iron_dust, coal_dust → titanium_alloy | 12 EU/t |
| 太陽能陣列 | `solar_array` | 多段光能動畫 | starlight_plate → energy_cell | 發電 3 |
| 倉儲匯流站 | `storage_hub` | 接收任意物流並作為中型緩衝倉 | storage_crate, machine_casing, wiring_bundle → machine_component, steel_plate, circuit_board, crops, plastic | — |
| 銷毀節點 | `trash_node` | 永久銷毀所有匯入的物品 | machine_casing, lava_bucket → — | — |
| 伐木機 | `tree_feller` | 搜尋附近樹幹並整株砍伐 | logging_blade, arbor_frame → oak_log, birch_log, spruce_log, jungle_log, acacia_log, dark_oak_log, cherry_log, mangrove_log, oak_sapling | 12 EU/t |
| 真空吸入口 | `vacuum_inlet` | 將周圍掉落物直接吸入產線 | vacuum_core, pressure_tube → junk, crops, iron_dust, copper_dust, plastic, circuit_board | 3 EU/t |
| 風力渦輪機 | `wind_turbine` | 風力發電（高空效率更佳） | wind → energy_cell | 發電 3 |

## 三階（ADVANCED，66 台）

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 耗能 |
|---|---|---|---|---|
| 進階製造機 | `advanced_assembler` | 脈衝光場 | machine_component, advanced_circuit → nano_coating | 24 EU/t |
| 紫水晶培養機 | `amethyst_grower` | 消耗冷卻混合液培養紫水晶碎片 | coolant_mix → amethyst_shard | 16 EU/t |
| 安卓艙 | `android_bay` | 程序裝配 | android_core, machine_casing → android_shell, android_frame, android_memory_matrix | 20 EU/t |
| 安卓燃料介面 | `android_fuel_interface` | 燃料補給 | emitter_node, pressure_tube → — | 4 EU/t |
| 安卓物品介面 | `android_item_interface` | 物資回收 | signal_relay, machine_casing → — | 4 EU/t |
| 安卓工作站 | `android_station` | 部署與巡檢 | android_frame, android_memory_matrix → android_item_interface, android_fuel_interface | 12 EU/t |
| 生質合成槽 | `biosynth_vat` | 生質批次培養 | gene_template, bio_catalyst, soybean_pods → synth_meat, protein_ration | 16 EU/t |
| 高爐 | `blast_furnace` | 3×3×3 磚塊結構，需要溫度 400+ 運作 | iron_ingot, coke, carbon_dust → steel_ingot, tungsten_ingot, carbide_dust | 40 EU/t |
| 量子倉儲抽屜 | `cargo_drawer` | 單一類型大容量倉儲；容量 50,000，可配合貨物網路自動進出 | storage_crate, routing_chip → storage_crate | — |
| 貨物抽取節點 | `cargo_input_node` | 從相鄰原版容器（箱子/桶/漏斗）抽取物品送入產線 | routing_chip, servo_motor, storage_crate → machine_component, steel_plate, circuit_board, crops, plastic | 2 EU/t |
| 貨物管理站 | `cargo_manager` | 無線掃描範圍內同主人機器並抽取輸出物品 | bus_frame, routing_chip, control_unit, signal_relay → machine_component, steel_plate, circuit_board, crops, plastic | 5 EU/t |
| 貨物驅動器 | `cargo_motor` | 高速物流中繼站，每次傳輸最多 8 件物品 | bus_frame, servo_motor, routing_chip → machine_component, steel_plate, circuit_board, crops, plastic | 1 EU/t |
| 貨物輸出節點 | `cargo_output_node` | 將輸入物品推送至相鄰原版容器（箱子/桶/漏斗） | routing_chip, servo_motor, storage_crate → machine_component, steel_plate, circuit_board, crops, plastic | 2 EU/t |
| 鑄造台 | `casting_table` | 熔融金屬 + 模具 → 零件 | molten_iron, molten_copper, molten_gold, molten_steel, molten_bronze → iron_plate, copper_plate, steel_plate, bronze_plate, iron_gear, steel_gear, copper_wire, gold_wire, iron_rod, steel_rod, iron_pipe, steel_pipe | 10 EU/t |
| 離心機 | `centrifuge` | 高速旋轉 | purified_shard → ion_dust, magnetic_ring, centrifuge_rotor | 18 EU/t |
| 化學反應器 | `chemical_reactor` | 化學煙霧 | bio_resin, coal_dust → polymer_resin, synthetic_fiber, precision_nozzle | 16 EU/t |
| 冷卻液循環器 | `coolant_circulator` | 消耗冷卻液，冷卻相鄰機器 -15 熱/tick | coolant_cell → — | 15 EU/t |
| 低溫蒸餾器 | `cryo_distiller` | 低溫相變蒸餾 | coolant_mix, refined_oil, hydro_gel, coolant_cell, grown_crystal → coolant_canister, cryo_gel, cryo_core | 18 EU/t |
| 低溫冷卻塔 | `cryo_tower` | 多方塊，範圍冷卻 3×3 區域 -20 熱/tick | coolant_cell → — | 20 EU/t |
| 晶體培育艙 | `crystal_growth_chamber` | 晶簇成長 | crystal_seed, coolant_mix → grown_crystal | 19 EU/t |
| 資料封存庫 | `data_archive` | 壓縮封存資料 | data_core, gravitic_bearing → archive_plate | 20 EU/t |
| 柴油發電機 | `diesel_generator` | 雜酚油驅動發電 | creosote_oil → — | 發電 24 |
| 無人機艙 | `drone_bay` | 飛行裝配 | drone_core, machine_casing → drone_shell, drone_frame | 18 EU/t |
| 電動生質實驗室 | `electric_bio_lab` | 自動培養循環 | crops, wood_dust → bio_resin, bio_fiber | 24 EU/t |
| 電動離心機 | `electric_centrifuge` | 超高速離心 | purified_shard → ion_dust, magnetic_ring, centrifuge_rotor | 38 EU/t |
| 電動化學反應器 | `electric_chemical_reactor` | 自動化學反應 | bio_resin, coal_dust → polymer_resin, synthetic_fiber, precision_nozzle | 34 EU/t |
| 電動壓縮機 | `electric_compressor` | 電動壓縮衝程 | iron_ingot, iron_plate, tin_ingot → iron_plate, steel_plate, compressed_iron_bundle, dense_carbon_block, tin_plate | 18 EU/t |
| 電動粉碎機 | `electric_crusher` | 高速旋轉渦輪 | iron_ore, copper_ore → iron_dust, copper_dust | 12 EU/t |
| 電動洗礦機 | `electric_ore_washer` | 高壓水流翻騰 | iron_dust → ore_slurry | 16 EU/t |
| 電動淨化器 | `electric_purifier` | 加壓淨化脈衝 | ore_slurry → purified_shard, coolant_cell, purifier_membrane | 22 EU/t |
| 電動篩礦機 | `electric_sifter` | 自動篩礦循環 | gravel, soul_sand → iron_dust, copper_dust, tin_dust, zinc_dust, sifted_ore, silicon, lead_dust | 10 EU/t |
| 電爐 | `electric_smelter` | 高溫電弧冶煉 | tungsten_dust, chromium_dust, cobalt_dust → tungsten_ingot, chromium_ingot, cobalt_ingot | 30 EU/t |
| 電動拉線機 | `electric_wire_mill` | 自動高速牽引 | copper_ingot, steel_plate, tin_ingot → copper_wire, steel_wire, wiring_bundle, tin_wire | 20 EU/t |
| 電解分離機 | `electrolytic_separator` | 電解分離元素 | water_bucket, redstone → hydrogen, oxygen, selenium_dust, silicon | 25 EU/t |
| 力場鍛造台 | `field_forge` | 藍色場域波動 | field_emitter, phase_plate → field_plate | 22 EU/t |
| 過濾路由器 | `filter_router` | 用第 1 格樣本物品做精準過濾匯流 | filter_mesh_core, routing_chip, purifier_membrane → machine_component, steel_plate, circuit_board, crops, plastic | — |
| 燃氣渦輪 | `gas_turbine` | 氫燃料高功率發電 | hydrogen → — | 發電 50 |
| 基因切片台 | `gene_splicer` | 基因樣板切片 | bio_resin, fertilizer_mix, crops, etched_circuit, synthetic_fiber, steel_plate, precision_nozzle → bio_catalyst, gene_template, neural_resin, splicer_blade | 20 EU/t |
| 地質抽取機 | `geo_extractor` | 持續抽取腳下區塊的地質資源，需先用地質掃描儀勘探 | drill_head, precision_frame → crude_oil, rock_salt, sulfur_dust, uranium_ore_raw, copper_dust | 18 EU/t |
| 地質掃描器 | `geo_scanner` | 利用探針與定位標記掃描區塊地質資料 | geo_scanner_probe, gps_marker → data_matrix | 12 EU/t |
| 地熱發電機 | `geothermal_generator` | 岩漿桶穩定發電 | lava_bucket → — | 發電 16 |
| GPS 控制塔 | `gps_control_tower` | 將定位標記與訊號中繼器整合為衛星模組 | gps_marker, signal_relay → satellite_module | 14 EU/t |
| GPS 網路節點 | `gps_network_node` | 利用衛星模組與資料核心合成傳送矩陣 | satellite_module, data_core → teleport_matrix | 20 EU/t |
| GPS 發射器 | `gps_transmitter` | 消耗定位電路產生定位標記 | gps_circuit → gps_marker | 8 EU/t |
| 工業總線 | `industrial_bus` | 高吞吐量遠距物流骨幹與中繼站 | bus_frame, routing_chip, storage_crate, centrifuge_rotor → machine_component, steel_plate, circuit_board, crops, plastic | — |
| 工業離心機 | `industrial_centrifuge` | 高速離心分離 | purified_iron, uranium_ore_dust → iron_dust, enriched_uranium, chromium_dust, cobalt_dust | 20 EU/t |
| 工業冶煉爐 | `industrial_smeltery` | 十字形多方塊，熔融合金爐，溫度 600+ 運作 | steel_ingot, damascus_steel_ingot, tungsten_ingot → damascus_steel_ingot, hardened_alloy_ingot, reinforced_alloy_ingot, conductive_alloy_ingot, cobalt_chrome_ingot, molten_iron, molten_steel, molten_copper, molten_gold, molten_bronze | 60 EU/t |
| 雷射雕刻機 | `laser_engraver` | 雷射掃描 | silicon, optic_lens → laser_lens, etched_circuit, control_unit | 18 EU/t |
| 物質編譯器 | `matter_compiler` | 物質編譯脈衝 | quantum_fluid, grown_crystal → matter_blob, singularity_fragment | 26 EU/t |
| 生物收集器 | `mob_collector` | 收集附近生物並轉化為掉落物 | net_launcher, field_processor → leather, beef, white_wool, mutton, porkchop, chicken, feather, rotten_flesh, bone, gunpowder, string, spider_eye, slime_ball, blaze_rod, magma_cream, ghast_tear, coal, gold_nugget | 14 EU/t |
| 核反應堆 | `nuclear_reactor` | 反應堆冷卻液為【永久催化劑】，放入一次後永不消耗；每根鈾燃料棒可運轉 40 秒，穩定輸出 200 EU/秒（加速模組額外 +60 EU），循環結束自動輸出乏燃料棒（可於物質編譯器回收為新燃料棒）。無燃料時完全不發電。 | uranium_rod, reactor_coolant → spent_fuel_rod | 發電 |
| 軌道觀測台 | `observatory` | 軌道資料測繪 | observatory_lens, data_matrix, magnetic_ring, graviton_lens, vacuum_tube, control_unit → star_chart, gravitic_bearing, vacuum_module, data_core | 22 EU/t |
| 光子織構機 | `photon_weaver` | 光束編織成形 | laser_lens, starlight_plate, reinforced_glass, optic_lens → photon_shard, photon_plate, observatory_lens | 24 EU/t |
| 加壓室 | `pressure_chamber` | 3×3×3 鐵塊結構，超高壓合成 | carbon_dust, carbide_dust → synthetic_diamond, carbide_ingot | 50 EU/t |
| 優先路由器 | `priority_router` | 依過濾欄優先級分流物品（匹配→主路，不匹配→備路） | — | — |
| 量子處理器 | `quantum_processor` | 閃爍光束 / 能量流 | advanced_circuit, nano_coating → quantum_chip | 20 EU/t |
| 採礦鑽機 | `quarry_drill` | 區塊級向下抽取礦脈 | — | 24 EU/t |
| 採礦鑽機二型 | `quarry_drill_mk2` | 區塊級高速抽取礦脈 | — | 48 EU/t |
| 輪流分配器 | `round_robin` | 平均分配物品到多台下游機器 | — | — |
| 信號比較器 | `signal_comparator` | 比較或相減兩路信號（A≥B→A / A-B） | signal_relay → signal_relay | 1 EU/t |
| 信號計數器 | `signal_counter` | 計算輸入信號上升沿次數（輸出 = 計數 mod 16） | signal_relay → signal_relay | 1 EU/t |
| 信號計時器 | `signal_timer` | 週期性脈衝信號（可設定 1-60 秒間隔） | signal_relay → signal_relay | 1 EU/t |
| 分流節點 | `splitter_node` | 將同一輸出平均拆往多條產線 | splitter_core, routing_chip, servo_motor → machine_component, steel_plate, circuit_board, crops, plastic | — |
| 風暴渦輪機 | `storm_turbine` | 暴雨與雷暴時會大幅增加發電量（晴天 2 EU/刻，風暴 8 EU/刻） | storm_rotor, weather_sensor → energy_cell | 發電 2 |
| 傳送面板 | `teleport_pad` | 消耗背包中的原版末影珍珠與能量執行空間跳躍 | teleport_matrix → — | 28 EU/t |
| 水冷散熱器 | `water_cooler` | 消耗水桶，冷卻相鄰機器 -8 熱/tick | water_bucket → — | 5 EU/t |

## 四階（TIER4 終局，32 台）

| 中文名 | 英文 ID | 說明 | 輸入 → 輸出 | 耗能 |
|---|---|---|---|---|
| 錨定鍛造台 | `anchor_forge` | 空間錨定鍛造 | singularity_casing, void_beacon_core, warp_coil, graviton_core → dimension_anchor, cluster_manifold | 46 EU/t |
| 反物質合成器 | `antimatter_synthesizer` | 反相粒子聚合 | dark_matter_dust, singularity_mesh → antimatter_cluster, antimatter_cell | 34 EU/t |
| 巔峰鍛成儀 | `apex_forge` | 巔峰核心壓縮 | cosmic_matrix, archive_core, continuum_shell, relic_data → omega_matrix, apex_core, omega_archive | 52 EU/t |
| 天穹裝配機 | `celestial_assembler` | 天穹構裝 | stellar_glass, orbit_frame → cosmic_matrix, cosmic_panel | 34 EU/t |
| 時序引擎 | `chrono_engine` | 時流脈動 | orbit_processor, void_crystal → chrono_thread, chrono_core | 38 EU/t |
| 連續體車床 | `continuum_lathe` | 時空精密切削 | chrono_core, reactor_lattice, vacuum_glass, harmonic_crystal, photon_plate → chrono_lattice, continuum_shell, aurora_plate | 40 EU/t |
| 暗物質織機 | `dark_matter_loom` | 暗紋編織 | dark_matter_dust, quantum_fabric → dark_matter_sheet | 26 EU/t |
| 熵穩定艙 | `entropy_chamber` | 熵場鎖定 | chrono_core, antimatter_cell → entropy_plate, entropy_core | 40 EU/t |
| 事件視界鍛台 | `event_horizon_smith` | 視界鍛壓 | singularity_core, entropy_plate, dark_matter_sheet, neutronium_plate → event_horizon_plate, singularity_casing | 44 EU/t |
| 聚變反應爐 | `fusion_reactor` | 聚變環流 | fusion_mesh, stability_core → fusion_core | 28 EU/t |
| 重力穩定器 | `graviton_stabilizer` | 重力場收束 | magnetic_ring, grown_crystal → graviton_lens, graviton_core | 24 EU/t |
| 奈米鑄造廠 | `nanite_foundry` | 奈米聚合霧化 | synthetic_fiber, quantum_fluid → nanite_slurry, nanite_cluster | 24 EU/t |
| 終極製核儀 | `omega_fabricator` | 終局核心鍛成 | cosmic_matrix, entropy_core → omega_core | 48 EU/t |
| 軌道列印機 | `orbital_printer` | 真空列印 | stellar_alloy, graviton_lens → orbit_frame, orbit_processor | 28 EU/t |
| 軌道中繼站 | `orbital_relay_station` | 利用聚變能量與軌道中繼單元批量生產衛星模組 | orbital_relay_unit, fusion_core → satellite_module | 32 EU/t |
| 異界傳送門 | `otherworld_portal` | 手持魔力隕石右鍵啟動，消耗 1 顆隕石將玩家投送至異世界；探索結束後由撤離點自動送回。 | mana_meteorite → — | — |
| 行星鍛造台 | `planetary_forge` | 生產模組化行星防護裝與高階探索骨架 | xeno_alloy, seal_fabric, exo_frame, radiation_mesh, thermal_lining, pressure_membrane → anti_radiation_chestplate, thermal_chestplate, pressure_chestplate, frontier_chestplate | 30 EU/t |
| 行星星門 | `planetary_gate` | 平時維持 12 EU 吸能，啟動躍遷時額外消耗 1200 EU 將玩家投送至目標星球 | survey_data, gravitic_bearing, quantum_frame, data_core → survey_data | 12 EU/t |
| 行星採集機 | `planetary_harvester` | 自動回收行星地表樣本與異星生質 | harvest_unit, sensor_array, servo_bundle, xeno_alloy → irradiated_shard, cryonite_crystal, voidglass_fragment, solarite_shard, stormglass_shard | 20 EU/t |
| 等離子精煉爐 | `plasma_refiner` | 等離子穩定化 | ion_dust, refined_oil, titanium_alloy, alloy_frame → plasma_dust, plasma_ingot, reactor_lattice | 28 EU/t |
| 採礦鑽機三型 | `quarry_drill_mk3` | 區塊級終局抽礦平台 | — | 96 EU/t |
| 遺物解析儀 | `relic_analyzer` | 解析異星遺物並抽出古訊號片段 | survey_data, planetary_relic, cryon_relic, nyx_relic → ancient_signal | 26 EU/t |
| 遺產掃描儀 | `relic_scanner` | 遺跡資料解析 | star_chart, void_beacon_core, data_core, archive_plate → relic_data, archive_core | 30 EU/t |
| 奇點壓縮台 | `singularity_press` | 奇點級壓縮 | dark_matter_sheet, neutronium_ingot → singularity_mesh, singularity_core | 32 EU/t |
| 星鋼鑄造廠 | `starsteel_foundry` | 星鋼熔鑄 | stellar_alloy, neutronium_ingot, orbit_frame → starsteel_ingot, starsteel_frame | 38 EU/t |
| 星核熔鍛爐 | `stellar_forge` | 星核熔流 | antimatter_cluster, neutronium_plate → stellar_alloy, stellar_glass | 36 EU/t |
| 行星測繪信標 | `survey_beacon` | 將觀測資料轉成可用的星球測繪樣本 | survey_chip, star_chart, data_core → survey_data | 24 EU/t |
| 真空工藝艙 | `vacuum_chamber` | 真空穩定製程 | stellar_glass, void_crystal, graviton_lens, quantum_fabric, dark_matter_sheet → vacuum_glass, harmonic_crystal, celestial_lens, nebula_fiber | 34 EU/t |
| 虛空信標 | `void_beacon` | 虛空穩定場 | void_shard, graviton_core → void_crystal, void_beacon_core | 22 EU/t |
| 虛空抽取器 | `void_extractor` | 虛空脈動抽取 | quantum_fluid, grown_crystal → void_shard, dark_matter_dust | 30 EU/t |
| 曲率裝配機 | `warp_assembler` | 曲率場裝配 | antimatter_cell, chrono_thread, magnetic_ring, orbit_processor, celestial_lens → warp_cell, warp_coil, astral_circuit | 42 EU/t |
| 異星精煉槽 | `xeno_refinery` | 將地表樣本、戰鬥樣本與遺跡資料精煉成防護模組與異星合金 | ancient_signal, irradiated_shard, cryonite_crystal, voidglass_fragment, aurelia_parasite_gland, cryon_ice_heart, nyx_phase_tissue → xeno_alloy, radiation_mesh, thermal_lining, pressure_membrane, flare_shielding, storm_baffle | 28 EU/t |
