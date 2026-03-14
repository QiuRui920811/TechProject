# TechProject 完整 Wiki 資料提取

> 自動提取自 `/rui/fs/大廳/plugins/科技專案` 全部 YAML 設定檔與 Java 原始碼。

---

## 1. 插件概覽 (Plugin Overview)

| 欄位 | 值 |
|---|---|
| 名稱 | TechProject |
| 版本 | 0.1.0 |
| API 版本 | 1.21 |
| 作者 | _RuCat |
| Folia 支援 | ✅ |
| 依賴 | Nexo (硬性), PlaceholderAPI (軟性) |
| 主類別 | `com.rui.techproject.TechProjectPlugin` |
| 儲存後端 | YAML (預設)、MySQL、Redis |
| 機器 Tick 週期 | 20 ticks |

### 研究 XP 系統

| 等級 | 解鎖物品成本 | 解鎖機器成本 | 互動成本 |
|---|---|---|---|
| TIER1 | 14 | 30 | 20 |
| TIER2 | 32 | 64 | 44 |
| TIER3 | 72 | 128 | 92 |
| TIER4 | 132 | 208 | 152 |

### 初始解鎖物品
`crusher`, `auto_farm`, `electric_saw`, `recycler`, `solar_generator`, `coal_generator`

---

## 2. 全部機器 (All Machines)

### 核心機器 (Core Machines)

| 機器 ID | 顯示名稱 | 階級 | 說明 |
|---|---|---|---|
| research_desk | 研究台 | TIER1 | 解鎖科技物品的核心工作站 |
| crusher | 碎礦機 | TIER1 | 礦石→粉末 (雙倍產出) |
| furnace | 電爐 | TIER1 | 電力冶煉 |
| auto_farm | 自動農場 | TIER1 | 自動種植/收穫 |
| electric_saw | 電鋸 | TIER1 | 木材加工 |
| recycler | 回收機 | TIER1 | 回收→回收材料 |
| solar_generator | 太陽能發電機 | TIER1 | 白天發電 |
| coal_generator | 燃煤發電機 | TIER1 | 燒燃料發電 |
| compressor | 壓縮器 | TIER1 | 壓縮零件 |
| assembler | 製造機 | TIER1 | 基礎組裝 |
| quantum_processor | 量子處理器 | TIER2 | 量子級合成 |
| smeltery | 精煉爐 | TIER1 | 精煉合金 |
| advanced_assembler | 進階製造機 | TIER2 | 進階組裝 |
| solar_array | 太陽能陣列 | TIER2 | 進階太陽能 |
| battery_bank | 電池庫 | TIER2 | 能源儲存 |
| purifier | 淨化器 | TIER2 | 礦漿/冷卻液淨化 |
| centrifuge | 離心機 | TIER2 | 離心分離 |
| ore_washer | 洗礦機 | TIER2 | 洗礦→礦漿 |
| bio_lab | 生質實驗室 | TIER2 | 生質加工 |
| greenhouse | 溫室培育艙 | TIER2 | 特殊作物培育 |
| polymer_press | 聚合壓床 | TIER2 | 橡膠/塑膠加工 |
| wire_mill | 拉線機 | TIER2 | 線材製造 |
| chemical_reactor | 化學反應器 | TIER2 | 化學合成 |
| coolant_mixer | 冷卻混合器 | TIER2 | 冷卻液製造 |
| refinery | 精煉塔 | TIER2 | 精煉油製造 |
| laser_engraver | 雷射雕刻機 | TIER3 | 雷射蝕刻/控制單元 |
| drone_bay | 無人機艙 | TIER3 | 無人機零件製造 |
| android_bay | 安卓艙 | TIER3 | 安卓零件製造 |
| android_station | 安卓工作站 | TIER3 | 安卓機器人部署 |
| android_item_interface | 安卓物品介面 | TIER3 | 安卓存取箱子 |
| android_fuel_interface | 安卓燃料介面 | TIER3 | 安卓燃料供應 |
| fusion_reactor | 聚變反應爐 | TIER3 | 聚變材料合成 |
| crystal_growth_chamber | 晶體培育艙 | TIER3 | 晶種→培育晶體 |
| field_forge | 力場鍛造台 | TIER3 | 力場板/武器製造 |
| matter_compiler | 物質編譯器 | TIER3 | 物質合成/奇點碎片 |
| energy_node | 能源節點 | TIER2 | 能源網路節點 |
| energy_cable | 導能線纜 | TIER1 | 傳輸能源 |
| logistics_node | 物流節點 | TIER2 | 物流網路節點 |
| item_tube | 物流導管 | TIER1 | 物品傳輸 |

### 擴展機器 (Expansion Machines)

| 機器 ID | 顯示名稱 | 階級 | 說明 |
|---|---|---|---|
| kiln | 窯爐 | TIER2 | 陶瓷燒製 |
| insulation_press | 絕緣壓機 | TIER2 | 絕緣線材 |
| cryo_distiller | 低溫蒸餾器 | TIER2 | 低溫加工 |
| gene_splicer | 基因剪接台 | TIER2 | 基因材料加工 |
| biosynth_vat | 生物合成槽 | TIER2 | 合成肉/食品 |
| plasma_refiner | 電漿精煉器 | TIER3 | 電漿加工 |
| photon_weaver | 光子編織機 | TIER3 | 光子材料 |
| observatory | 天文觀測台 | TIER3 | 星圖/測繪 |
| data_archive | 資料檔案庫 | TIER3 | 資料儲存 |
| survey_beacon | 測繪信標 | TIER3 | 行星探測 |
| relic_analyzer | 遺物解析儀 | TIER3 | 遺物→訊號/核心碎片 |
| xeno_refinery | 異星精煉台 | TIER3 | 異星合金/防護層 |
| planetary_forge | 行星鍛造台 | TIER3 | 行星裝備/前線套裝 |
| planetary_gate | 行星星門 | TIER3 | 星際傳送 (1200 EU) |
| planetary_harvester | 行星收穫者 | TIER3 | 行星資源採集 |

### 系統機器 (System Machines)

| 機器 ID | 顯示名稱 | 階級 | 說明 |
|---|---|---|---|
| quarry_drill (Mk1/Mk2/Mk3) | 礦場鑽機 | TIER2-3 | 自動採礦 |
| crop_harvester | 作物收割器 | TIER2 | 大範圍收割 |
| vacuum_inlet | 真空吸入口 | TIER2 | 物品拾取 |
| storm_turbine | 風暴渦輪 | TIER2 | 風力發電 |
| tree_feller | 伐木機 | TIER2 | 自動伐木 |
| mob_collector | 生物收集器 | TIER2 | 自動收集掉落物 |
| fishing_dock | 釣魚碼頭 | TIER2 | 自動釣魚 |
| storage_hub | 儲存中心 | TIER2 | 數位儲存 |
| filter_router | 過濾路由器 | TIER2 | 物品過濾分配 |
| splitter_node | 分流節點 | TIER2 | 物品分流 |
| industrial_bus | 工業匯流排 | TIER2 | 大量物流 |
| cargo_input_node | 貨物輸入節點 | TIER2 | 貨物輸入端 |
| cargo_manager | 貨物管理器 | TIER3 | 貨物管理 |
| trash_node | 垃圾節點 | TIER2 | 銷毀多餘物品 |
| cargo_motor | 貨物馬達 | TIER2 | 驅動貨物網路 |

### TIER4 終局機器 (Endgame Machines)

| 機器 ID | 顯示名稱 | 階級 | 說明 |
|---|---|---|---|
| void_extractor | 虛空抽取器 | TIER4 | 中子/暗物質材料 |
| dark_matter_loom | 暗物質織機 | TIER4 | 暗物質加工 |
| graviton_stabilizer | 重力子穩定器 | TIER4 | 重力子元件 |
| singularity_press | 奇點壓床 | TIER4 | 奇點材料 |
| antimatter_synthesizer | 反物質合成器 | TIER4 | 反物質 |
| stellar_forge | 恆星鑄造廠 | TIER4 | 恆星合金 |
| orbital_printer | 軌道列印機 | TIER4 | 軌道元件 |
| chrono_engine | 時序引擎 | TIER4 | 時序材料 |
| void_beacon | 虛空信標 | TIER4 | 虛空信標核心 |
| nanite_foundry | 奈米工坊 | TIER4 | 奈米材料 |
| entropy_chamber | 熵值艙 | TIER4 | 熵材料 |
| celestial_assembler | 天穹裝配台 | TIER4 | 宇宙級組裝 |
| omega_fabricator | 歐米茄製造器 | TIER4 | 最終裝配 |

### 巨構機器 (Megastructure Machines)

| 機器 ID | 顯示名稱 | 階級 | 說明 |
|---|---|---|---|
| starsteel_foundry | 星鋼鑄造廠 | TIER4+ | 星鋼材料 |
| vacuum_chamber | 真空室 | TIER4+ | 真空/諧波材料 |
| warp_assembler | 曲速裝配台 | TIER4+ | 曲速元件 |
| event_horizon_smith | 事件視界鍛台 | TIER4+ | 事件視界板 |
| continuum_lathe | 連續體車床 | TIER4+ | 連續體/極光材料 |
| relic_scanner | 遺物掃描儀 | TIER4+ | 遺物資料 |
| anchor_forge | 錨定鍛台 | TIER4+ | 維度錨定 |
| apex_forge | 巔峰鍛台 | TIER4+ | 最終巨構製造 |

---

## 3. 全部物品 (All Items)

### 3.1 核心材料 (Core Materials)

| 物品 ID | 顯示名稱 | 說明/用途 |
|---|---|---|
| iron_dust | 鐵粉 | 碎礦機產出 |
| copper_ingot | 銅錠 | 冶煉銅粉 |
| copper_dust | 銅粉 | 碎礦機產出 |
| coal_dust | 煤粉 | 碎礦機產出 |
| silicon | 矽 | 碎礦機碎石英 |
| wood_dust | 木屑 | 電鋸產出 |
| plastic | 塑膠 | 壓縮機產出 |
| steel_plate | 鋼板 | 精煉爐產出 |
| iron_plate | 鐵板 | 壓縮機產出 |
| glass_panel | 玻璃板 | 壓縮/製造用 |
| circuit_board | 電路板 | 製造機（矽+銅錠） |
| machine_component | 機械零件 | 製造機（鐵板+銅錠） |
| energy_cell | 能源電池 | 製造機（銅錠+煤粉） |
| compressed_iron_bundle | 壓縮鐵束 | 壓縮機 |
| recycled_material | 回收材料 | 回收機產出 |
| crop_seeds | 作物種子 | 自動農場產出 |
| crops | 作物 | 自動農場產出 |

### 3.2 進階材料 (Advanced Materials)

| 物品 ID | 顯示名稱 |
|---|---|
| advanced_circuit | 進階電路 |
| quantum_chip | 量子晶片 |
| nano_coating | 奈米塗層 |
| precision_frame | 精密框架 |
| carbon_mesh | 碳纖網 |
| dense_carbon_block | 高密碳塊 |
| lithium_cell | 鋰電池 |
| reactor_core | 反應爐核心 |
| reinforced_glass | 強化玻璃 |
| titanium_alloy | 鈦合金 |
| coolant_cell | 冷卻電池 |
| bronze_gear | 青銅齒輪 |
| micro_processor | 微型處理器 |
| data_matrix | 數據矩陣 |
| fiber_mesh | 纖維網 |
| pressure_tube | 壓力導管 |
| alloy_frame | 合金框架 |
| rust_filter | 鏽蝕濾芯 |
| optic_lens | 光學鏡片 |
| plasma_cell | 電漿電池 |
| crystal_matrix | 晶體矩陣 |
| logic_gate | 邏輯閘 |
| servo_motor | 伺服馬達 |
| heat_coil | 熱線圈 |
| machine_casing | 機械外殼 |

### 3.3 量子/力場材料 (Quantum & Field Materials)

| 物品 ID | 顯示名稱 |
|---|---|
| quantum_fluid | 量子流體 |
| quantum_frame | 量子框架 |
| starlight_plate | 星光板 |
| emitter_node | 發射節點 |
| capacitor_bank | 電容組 |
| fusion_mesh | 聚變網 |
| vacuum_tube | 真空管 |
| signal_relay | 訊號中繼器 |
| composite_panel | 複合面板 |
| flux_link | 通量連結 |
| phase_plate | 相位板 |
| field_emitter | 力場發射器 |
| field_plate | 力場板 |
| fusion_core | 聚變核心 |
| stability_core | 穩定核心 |
| crystal_seed | 晶種 |
| grown_crystal | 培育晶體 |
| matter_blob | 物質團 |
| singularity_fragment | 奇點碎片 |

### 3.4 加工中間材料 (Processing Intermediates)

| 物品 ID | 顯示名稱 |
|---|---|
| ore_slurry | 礦漿 |
| purified_shard | 淨化碎晶 |
| purifier_membrane | 淨化膜片 |
| bio_resin | 生質樹脂 |
| bio_fiber | 生質纖維 |
| ion_dust | 離子粉 |
| magnetic_ring | 磁環 |
| centrifuge_rotor | 離心轉子 |
| thermal_shell | 隔熱外殼 |
| crushed_biomass | 粉碎生質 |
| hydro_gel | 水凝膠 |
| rubber_sheet | 橡膠片 |
| copper_wire | 銅線 |
| steel_wire | 鋼線 |
| wiring_bundle | 工業線束 |
| polymer_resin | 聚合樹脂 |
| treated_plastic | 處理塑膠 |
| synthetic_fiber | 合成纖維 |
| coolant_mix | 冷卻混合液 |
| refined_oil | 精煉油 |
| laser_lens | 雷射鏡 |
| etched_circuit | 蝕刻電路 |
| control_unit | 控制單元 |
| precision_nozzle | 精密噴嘴 |
| grid_module | 電網模組 |
| seed_cluster | 種子簇 |
| harvest_unit | 收割單元 |
| growth_lamp | 生長燈 |
| agri_module | 農業模組 |
| fertilizer_mix | 肥料混合物 |

### 3.5 無人機/安卓材料 (Drone & Android Materials)

| 物品 ID | 顯示名稱 |
|---|---|
| drone_core | 無人機核心 |
| drone_shell | 無人機外殼 |
| drone_frame | 無人機框架 |
| android_core | 安卓核心 |
| android_shell | 安卓外殼 |
| android_frame | 安卓框架 |
| android_memory_matrix | 安卓記憶矩陣 |
| android_harvest_script | 安卓農務程序 |
| android_logging_script | 安卓伐木程序 |
| android_salvage_script | 安卓回收程序 |
| android_planet_script | 安卓行星採樣程序 |
| android_overclock_card | 安卓超頻卡 |
| android_efficiency_card | 安卓節能卡 |
| android_expansion_card | 安卓擴域卡 |

### 3.6 升級模組 (Upgrades)

| 物品 ID | 顯示名稱 | 效果 |
|---|---|---|
| speed_upgrade | 速度升級 | 加速機器運作 |
| efficiency_upgrade | 效率升級 | 降低能耗 |
| stack_upgrade | 堆疊升級 | 增加處理量 |
| range_upgrade | 範圍升級 | 增加作用範圍 |

### 3.7 特殊物品 (Special Items)

| 物品 ID | 顯示名稱 | 說明 |
|---|---|---|
| tech_book | 科技百科 | 開啟導覽書 GUI |
| energy_token | 能源代幣 | 獎勵貨幣 |
| achievement_badge | 成就徽章 | 成就獎勵 |

### 3.8 擴展材料 — 熱/絕緣/低溫 (Expansion: Thermal & Cryo)

| 物品 ID | 顯示名稱 |
|---|---|
| ceramic_plate | 陶瓷板 |
| thermal_ceramic | 隔熱陶瓷 |
| insulated_wire | 絕緣線材 |
| sensor_array | 感測陣列 |
| servo_bundle | 伺服模組束 |
| coolant_canister | 冷卻劑罐 |
| cryo_gel | 低溫凝膠 |
| cryo_core | 低溫核心 |

### 3.9 擴展材料 — 生物合成 (Expansion: Biosynth)

| 物品 ID | 顯示名稱 |
|---|---|
| bio_catalyst | 生質催化劑 |
| gene_template | 基因模板 |
| neural_resin | 神經樹脂 |
| splicer_blade | 剪接刀片 |
| synth_meat | 合成肉 |

### 3.10 擴展材料 — 電漿/光子/天文 (Expansion: Plasma & Observatory)

| 物品 ID | 顯示名稱 |
|---|---|
| plasma_dust | 電漿粉 |
| plasma_ingot | 電漿錠 |
| reactor_lattice | 反應格架 |
| photon_shard | 光子碎片 |
| photon_plate | 光子板 |
| observatory_lens | 觀測透鏡 |
| star_chart | 星圖 |
| gravitic_bearing | 重力軸承 |
| vacuum_module | 真空模組 |
| data_core | 數據核心 |
| archive_plate | 檔案板 |

### 3.11 種子與作物 (Seeds & Crops)

#### 科技作物
| 種子 ID | 產出 ID | 顯示名稱 |
|---|---|---|
| soybean_seeds | soybean_pods | 黃豆 |
| spiceberry_seeds | spiceberry | 香莓 |
| tea_leaf_seeds | tea_leaf | 茶葉 |
| tomato_seeds | tomato | 番茄 |
| cabbage_seeds | cabbage | 高麗菜 |
| corn_seeds | corn | 甜玉米 |
| onion_bulbs | onion | 洋蔥 |

#### 外星果實
| 樹苗 ID | 果實 ID | 顯示名稱 |
|---|---|---|
| lumenfruit_sapling | lumenfruit | 微光果 |
| frost_apple_sapling | frost_apple | 霜蘋果 |
| shadow_berry_sapling | shadow_berry_cluster | 影莓串 |
| sunflare_fig_sapling | sunflare_fig | 烈陽無花果 |
| stormplum_sapling | stormplum | 風暴李 |

#### 普通水果
| 樹苗/灌木 ID | 果實 ID | 顯示名稱 |
|---|---|---|
| cherry_sapling | cherry | 櫻桃 |
| lemon_sapling | lemon | 黃檸果 |
| peach_sapling | peach | 蜜桃 |
| pear_sapling | pear | 青梨 |
| orange_sapling | orange | 香橙 |
| grape_sapling | grape | 葡萄 |
| banana_sapling | banana | 香蕉 |
| mango_sapling | mango | 芒果 |
| coconut_palm | coconut | 椰子 |
| strawberry_bush | strawberry | 草莓 |
| blueberry_bush | blueberry | 藍莓 |
| raspberry_bush | raspberry | 覆盆子 |
| — | watermelon_slice | 西瓜片 |
| — | pineapple | 鳳梨 |
| — | kiwi | 奇異果 |

#### 蔬菜
garlic (大蒜)、lettuce (生菜)、bell_pepper (甜椒)、spinach (菠菜)、radish (蘿蔔)

### 3.12 行星材料 (Planet Materials)

| 物品 ID | 顯示名稱 | 所屬星球 |
|---|---|---|
| radiation_filter | 輻射濾網 | 通用 |
| irradiated_shard | 輻照碎片 | Aurelia |
| void_bloom / void_bloom_seeds | 虛空花 | Aurelia |
| planetary_relic | 行星遺物 | Aurelia |
| aurelia_parasite_gland | 奧蕾莉亞寄生腺體 | Aurelia (怪物掉落) |
| cryonite_crystal | 低溫晶體 | Cryon |
| frostbloom / frostbloom_seeds | 霜花 | Cryon |
| cryon_relic | 克里昂遺物 | Cryon |
| cryon_ice_heart | 克里昂冰脈核心 | Cryon (怪物掉落) |
| voidglass_fragment | 虛玻碎片 | Nyx |
| echo_spore / echo_spore_seeds | 回聲孢子 | Nyx |
| nyx_relic | 倪克斯遺核 | Nyx |
| nyx_phase_tissue | 倪克斯相位膜組織 | Nyx (怪物掉落) |
| solarite_shard | 日灼碎片 | Helion |
| emberbloom / emberroot_seeds | 餘燼花 | Helion |
| helion_relic | 赫利昂遺物 | Helion |
| helion_cinder_core | 赫利昂灰燼燃核 | Helion (怪物掉落) |
| stormglass_shard | 暴嵐玻片 | Tempest |
| ion_fern / ion_fern_seeds | 離子蕨 | Tempest |
| tempest_relic | 坦佩斯特遺物 | Tempest |
| tempest_capacitor | 坦佩斯特電容核 | Tempest (怪物掉落) |
| survey_chip | 測繪晶片 | 通用 |
| survey_data | 測繪資料 | 通用 |
| ancient_signal | 古代訊號 | 多星球 |
| xeno_alloy | 異星合金 | 多星球 |
| exo_frame | 外骨骼框架 | 合成 |
| seal_fabric | 密封織層 | 合成 |
| radiation_mesh | 輻射網層 | 合成 |
| thermal_lining | 低溫內襯 | 合成 |
| pressure_membrane | 壓差膜層 | 合成 |
| flare_shielding | 耀焰屏護層 | 合成 |
| storm_baffle | 風暴導流層 | 合成 |
| frontier_core_fragment | 前線核心碎片 | 高階遺物 |
| frontier_core | 前線核心 | 合成 |

### 3.13 系統物品 (System Items)

| 物品 ID | 顯示名稱 |
|---|---|
| drill_head | 鑽頭 |
| quarry_frame | 礦場框架 |
| harvest_matrix | 收穫矩陣 |
| vacuum_core | 真空核心 |
| storm_rotor | 風暴轉子 |
| weather_sensor | 天氣感測器 |
| field_processor | 田野處理器 |
| logging_blade | 伐木刀片 |
| arbor_frame | 林業框架 |
| net_launcher | 捕蟲器 |
| bait_module | 魚餌模組 |
| dock_frame | 碼頭框架 |
| storage_crate | 儲存箱 |
| routing_chip | 路由晶片 |
| filter_mesh_core | 過濾網心 |
| bus_frame | 匯流排框架 |
| splitter_core | 分流核心 |

### 3.14 TIER4 終局材料 (Endgame Materials)

| 物品 ID | 顯示名稱 |
|---|---|
| neutronium_ingot | 中子合金錠 |
| neutronium_plate | 中子合金板 |
| dark_matter_dust | 暗物質粉 |
| dark_matter_sheet | 暗物質片 |
| graviton_lens | 重力子透鏡 |
| graviton_core | 重力子核心 |
| singularity_mesh | 奇點網 |
| singularity_core | 奇點核心 |
| antimatter_cluster | 反物質簇 |
| antimatter_cell | 反物質電池 |
| stellar_alloy | 恆星合金 |
| stellar_glass | 恆星玻璃 |
| orbit_frame | 軌道框架 |
| orbit_processor | 軌道處理器 |
| quantum_fabric | 量子織物 |
| chrono_thread | 時序絲線 |
| chrono_core | 時序核心 |
| void_shard | 虛空碎片 |
| void_crystal | 虛空晶體 |
| void_beacon_core | 虛空信標核心 |
| nanite_slurry | 奈米漿液 |
| nanite_cluster | 奈米簇 |
| entropy_plate | 熵值板 |
| entropy_core | 熵值核心 |
| cosmic_matrix | 宇宙矩陣 |
| cosmic_panel | 宇宙面板 |
| omega_core | 歐米茄核心 |

### 3.15 巨構材料 (Megastructure Materials)

| 物品 ID | 顯示名稱 |
|---|---|
| starsteel_ingot | 星鋼錠 |
| starsteel_frame | 星鋼框架 |
| vacuum_glass | 真空玻璃 |
| harmonic_crystal | 諧波晶體 |
| celestial_lens | 天穹透鏡 |
| nebula_fiber | 星雲纖維 |
| warp_cell | 曲速電池 |
| warp_coil | 曲速線圈 |
| astral_circuit | 星界電路 |
| event_horizon_plate | 事件視界板 |
| singularity_casing | 奇點外殼 |
| chrono_lattice | 時序格架 |
| continuum_shell | 連續體殼 |
| aurora_plate | 極光板 |
| relic_data | 遺物數據 |
| archive_core | 檔案核心 |
| dimension_anchor | 維度錨定 |
| cluster_manifold | 簇流形 |
| omega_matrix | 歐米茄矩陣 |
| apex_core | 巔峰核心 |
| omega_archive | 歐米茄檔案 |

---

## 4. 全部配方 (All Recipes)

### 4.1 工作台配方 (Blueprint / Workbench Recipes)

> 來源: `tech-blueprints.yml` — 3×3 工作台合成，需研究台解鎖

| 配方 ID | 產出 | 形狀 (3×3) | 材料 |
|---|---|---|---|
| research_desk | 研究台 | `BCB / ADA / AAA` | A=鐵錠, B=書, C=鑽石, D=紅石 |
| solar_generator | 太陽能發電機 | `DDD / ACA / ABA` | A=鐵錠, B=紅石, C=閃電力, D=玻璃 |
| coal_generator | 燃煤發電機 | `AAA / ABA / ACA` | A=鐵錠, B=熔爐, C=紅石 |
| crusher | 碎礦機 | `ACA / ABA / ADA` | A=鐵錠, B=活塞, C=鐵砧, D=紅石 |
| furnace | 電爐 | `AAA / ABA / ACA` | A=鐵錠, B=熔爐, C=紅石 |
| compressor | 壓縮器 | `ADA / ABA / ACA` | A=鐵錠, B=活塞, C=紅石, D=石頭 |
| assembler | 製造機 | `ACA / ABA / ADA` | A=鐵錠, B=工作台, C=紅石, D=銅錠 |
| advanced_assembler | 進階製造機 | `ACA / ABA / ADA` | A=鐵錠, B=assembler, C=advanced_circuit, D=quantum_chip |
| quantum_processor | 量子處理器 | `ACA / ABA / ADA` | A=鐵錠, B=advanced_assembler, C=quantum_chip, D=nano_coating |
| fusion_reactor | 聚變反應爐 | `ACA / ABA / ADA` | A=鐵錠, B=reactor_core, C=fusion_mesh, D=stability_core |
| electric_saw | 電鋸 | `_AA / _BA / _CA` | A=鐵錠, B=鐵劍, C=紅石 |
| recycler | 回收機 | `ACA / ABA / ADA` | A=鐵錠, B=堆肥桶, C=活塞, D=紅石 |
| auto_farm | 自動農場 | `ADA / ABA / ACA` | A=鐵錠, B=鋤, C=紅石, D=骨粉 |
| battery_bank | 電池庫 | `ACA / ABA / ADA` | A=鐵錠, B=energy_cell, C=lithium_cell, D=emitter_node |
| greenhouse | 溫室培育艙 | `ADA / ABA / ACA` | A=鐵錠, B=玻璃, C=growth_lamp, D=agri_module |
| energy_node | 能源節點 | `_A_ / ABA / _A_` | A=鐵錠, B=emitter_node |
| energy_cable | 導能線纜 | `___ / AAA / ___` | A=copper_wire |
| logistics_node | 物流節點 | `_A_ / ABA / _A_` | A=鐵錠, B=signal_relay |
| item_tube | 物流導管 | `___ / AAA / ___` | A=pressure_tube |

### 4.2 核心機器配方 (Core Machine Recipes)

#### 碎礦機 (Crusher)
| 配方 ID | 輸入 | 輸出 | 能耗 |
|---|---|---|---|
| crusher_iron_ore | 鐵礦 | iron_dust | 6 |
| crusher_copper_ore | 銅礦 | copper_dust | 6 |
| crusher_quartz | 石英 | silicon | 7 |
| crusher_coal | 煤炭 | coal_dust | 5 |
| crusher_iron_plate | iron_plate | recycled_material | 5 |

#### 電爐 (Furnace)
| 配方 ID | 輸入 | 輸出 | 能耗 |
|---|---|---|---|
| furnace_iron_dust | iron_dust | 鐵錠 | 8 |
| furnace_copper_dust | copper_dust | copper_ingot | 8 |
| furnace_glass | 沙 | glass_panel | 6 |
| furnace_reinforced_glass | glass_panel+iron_plate | reinforced_glass | 10 |
| furnace_heat_coil | copper_wire+coal_dust | heat_coil | 11 |
| furnace_vacuum_tube | glass_panel+copper_wire | vacuum_tube | 10 |
| furnace_optic_lens | reinforced_glass+silicon | optic_lens | 12 |

#### 電鋸 (Electric Saw)
| 配方 ID | 輸入 | 輸出 | 能耗 |
|---|---|---|---|
| saw_wood | 原木 | wood_dust | 4 |
| saw_fiber | wood_dust | fiber_mesh | 5 |

#### 自動農場 (Auto Farm)
seeds/骨粉→crop_seeds (3)、crop_seeds→crops (4)

#### 回收機 (Recycler)
任何物品→recycled_material (3)

#### 壓縮器 (Compressor)
| 配方 ID | 輸入 | 輸出 | 能耗 |
|---|---|---|---|
| compressor_iron | 鐵錠 | iron_plate | 8 |
| compressor_iron_bundle | iron_plate ×多 | compressed_iron_bundle | 10 |
| compressor_plastic | coal_dust+木棍 | plastic | 7 |
| compressor_carbon_mesh | coal_dust ×2 | carbon_mesh | 9 |
| compressor_dense_carbon | carbon_mesh ×2 | dense_carbon_block | 12 |
| compressor_composite | iron_plate+carbon_mesh | composite_panel | 12 |

#### 製造機 (Assembler)
| 輸入 | 輸出 | 能耗 |
|---|---|---|
| silicon+copper_ingot | circuit_board | 10 |
| iron_plate+copper_ingot | machine_component | 9 |
| copper_ingot+coal_dust | energy_cell | 8 |
| machine_component+circuit_board | precision_frame | 12 |
| machine_component+iron_plate | machine_casing | 11 |
| circuit_board+optic_lens | logic_gate | 12 |
| machine_component+copper_wire | servo_motor | 12 |
| signal_relay+chest | android_item_interface | 16 |
| emitter_node+pressure_tube | android_fuel_interface | 16 |
| emitter_node+machine_casing | energy_node | 14 |
| signal_relay+machine_component | logistics_node | 14 |
| harvest_unit+steel_plate | field_sickle | 14 |
| agri_module+servo_motor | hydro_spade | 14 |
| servo_bundle+insulated_wire+sensor_array | vector_grapple | 18 |
| agri_module+logic_gate | android_harvest_script | 14 |
| harvest_unit+servo_motor | android_logging_script | 15 |
| signal_relay+emitter_node | android_salvage_script | 15 |

#### 精煉爐 (Smeltery)
iron_plate+copper_ingot→steel_plate (10)、iron_plate+plastic→alloy_frame (10)、steel_plate+silicon→titanium_alloy (14)、copper_ingot+circuit_board→micro_processor (12)

#### 進階製造機 (Advanced Assembler)
| 輸入 | 輸出 | 能耗 |
|---|---|---|
| precision_frame+circuit_board | advanced_circuit | 16 |
| advanced_circuit+plastic | nano_coating | 16 |
| micro_processor+logic_gate | signal_relay | 17 |
| carbon_mesh+machine_casing | thermal_shell | 18 |
| circuit_board+iron_plate | grid_module | 16 |
| steel_plate+plastic | pressure_tube | 16 |
| grid_module+emitter_node | starlight_plate | 18 |
| machine_casing+reactor_core | reactor_core (升級) | 20 |
| drone_core+servo_motor | drone_core | 18 |
| android_core+data_matrix | android_core | 20 |
| speed_upgrade+android_memory_matrix | android_overclock_card | 20 |
| efficiency_upgrade+android_memory_matrix | android_efficiency_card | 20 |
| android_frame+android_memory_matrix | android_station | 24 |
| heat_coil+ion_dust | plasma_cell | 22 |
| servo_motor+micro_processor | speed_upgrade | 18 |
| nano_coating+capacitor_bank | efficiency_upgrade | 18 |
| machine_casing+composite_panel | stack_upgrade | 18 |
| pressure_membrane+plasma_cell+precision_nozzle | pulse_thruster | 24 |
| gravitic_bearing+quantum_frame+xeno_alloy | planetary_gate | 34 |

#### 太陽能陣列 & 電池庫
solar: starlight_plate+glass_panel→solar_array 產出 (10)
battery: lithium_cell+emitter_node→capacitor_bank (11)

#### 洗礦機 (Ore Washer)
iron_dust→ore_slurry (7)、crops→crushed_biomass (6)

#### 淨化器 (Purifier)
ore_slurry→purified_shard (9)、snowball+glass_panel+purifier_membrane→coolant_cell (10)、rust_filter+fiber_mesh→purifier_membrane (10)、rust_filter+nano_coating→radiation_filter (14)

#### 離心機 (Centrifuge)
purified_shard→ion_dust (18)、ion_dust+alloy_frame→centrifuge_rotor (20)、ion_dust+steel_wire+centrifuge_rotor→magnetic_ring (20)

#### 生質實驗室 (Bio Lab)
crops+wood_dust→bio_resin (11)、crushed_biomass+fiber_mesh→bio_fiber (10)
食品: tomato+cabbage→orchard_salad (11)、corn+soybean_pods→cornbread (12)、cabbage+onion→stuffed_cabbage (12)、orange+lemon→citrus_salad (11)、tomato+onion→tomato_stew (11)、lumenfruit+irradiated_shard→aurelia_glaze (18)、onion+bell_pepper→vegetable_curry (18)、soybean_pods+spiceberry→ramen (18)、corn+tomato→sushi_roll (16)、tomato+garlic→cheese_pizza (18)、lettuce+tomato→veggie_wrap (14)、bell_pepper+corn→breakfast_burrito (16)、strawberry+blueberry→fruit_parfait (12)

#### 溫室培育艙 (Greenhouse)
glass_panel+agri_module→growth_lamp (8)、bio_resin+crops→hydro_gel (8)
種子: seed_cluster+fertilizer_mix→spiceberry_seeds/corn_seeds (8)、seed_cluster+hydro_gel→tea_leaf_seeds/onion_bulbs (8)

#### 聚合壓床 (Polymer Press)
bio_resin→rubber_sheet (9)、plastic+polymer_resin→treated_plastic (10)

#### 拉線機 (Wire Mill)
copper_ingot→copper_wire (9)、steel_plate→steel_wire (11)、copper_wire+steel_wire→wiring_bundle (12)、pressure_tube+steel_wire→item_tube (11)

#### 化學反應器 (Chemical Reactor)
bio_resin+coal_dust→polymer_resin (16)、polymer_resin+bio_fiber→synthetic_fiber (16)、pressure_tube+steel_wire→precision_nozzle (15)

#### 冷卻混合器 (Coolant Mixer)
coolant_cell+hydro_gel→coolant_mix (7)、tea_leaf+hydro_gel→radiant_tea (10)
果汁: lemon/orange/peach/pear/cherry + hydro_gel → 各果汁 (10), peach+pear→orchard_smoothie (11), lemon+orange→sparkling_lemonade (11), shadow_berry+voidglass→nyx_phase_gel (18), stormplum+stormglass→tempest_fizz (18)
更多飲料: grape+hydro_gel→grape_juice (14), mango+coconut→mango_smoothie (14), coconut+hydro_gel→coconut_water (12), strawberry+blueberry→berry_blend (14), pineapple+mango→tropical_punch (14), tea_leaf+hydro_gel→iced_tea (12), pear+hydro_gel→apple_cider (12), watermelon+hydro_gel→ginger_ale (12), banana+coconut→vanilla_milkshake (14), strawberry+coconut→strawberry_milkshake (14)

#### 雷射雕刻機 (Laser Engraver)
optic_lens+reinforced_glass→laser_lens (17)、laser_lens+advanced_circuit→etched_circuit (18)、etched_circuit+logic_gate→control_unit (18)
護符: advanced_circuit+composite_panel→talisman_knight (20), advanced_circuit+optic_lens→talisman_hunter (20), advanced_circuit+heat_coil→talisman_miner (20), advanced_circuit+titanium_alloy→talisman_anvil (22), advanced_circuit+data_matrix→talisman_heal (20), advanced_circuit+dense_carbon_block→talisman_whirlwind (20)

#### 無人機艙 (Drone Bay)
treated_plastic+machine_casing→drone_shell (18)、drone_shell+synthetic_fiber→drone_frame (19)

#### 安卓艙 (Android Bay)
treated_plastic+machine_casing→android_shell (18)、android_shell+synthetic_fiber→android_frame (19)、android_core+data_matrix→android_memory_matrix (20)

#### 量子處理器 (Quantum Processor)
advanced_circuit+nano_coating→quantum_chip (20)、ion_dust+flux_link→quantum_fluid (20)、flux_link+reinforced_glass→quantum_frame (22)、control_unit+quantum_chip→data_matrix (21)、grown_crystal+data_matrix→crystal_matrix (21)、magnetic_ring+energy_cell→flux_link (20)、quantum_frame+grown_crystal→phase_plate (22)、flux_link+control_unit→field_emitter (23)、range_upgrade+android_memory_matrix→android_expansion_card (22)、quantum_frame+field_emitter→android_planet_script (23)
裝甲: quantum_chip+titanium_alloy→quantum_helmet (24), quantum_chip+photon_plate→quantum_chestplate (26), quantum_chip+carbon_mesh→quantum_leggings (24), quantum_chip+graviton_lens→quantum_boots (24)

#### 晶體培育艙 (Crystal Growth Chamber)
amethyst_shard+coolant_mix→crystal_seed (18)、crystal_seed+quantum_fluid→grown_crystal (19)

#### 力場鍛造台 (Field Forge)
field_emitter+phase_plate→field_plate (22)
武器: advanced_circuit+dense_carbon_block→pulse_staff (20), advanced_circuit+heat_coil→storm_staff (22), graviton_lens+advanced_circuit→gravity_staff (22), quantum_chip+graviton_lens→warp_orb (24), coolant_cell+advanced_circuit→cryo_wand (20)
護符: circuit_board+heat_coil→talisman_fire (14), circuit_board+hydro_gel→talisman_water (14), circuit_board+rubber_sheet→talisman_angel (12), circuit_board+dense_carbon→talisman_warrior (14), circuit_board+copper_wire→talisman_traveler (12), circuit_board+hydro_gel→talisman_farmer (14)

#### 聚變反應爐 (Fusion Reactor)
thermal_shell+field_plate→fusion_mesh (25)、magnetic_ring+reactor_core→stability_core (26)、fusion_mesh+stability_core→fusion_core (28)

#### 物質編譯器 (Matter Compiler)
quantum_fluid+grown_crystal→matter_blob (26)、matter_blob+fusion_core→singularity_fragment (27)
武器: plasma_cell+titanium_alloy→plasma_lance (30), photon_plate+graviton_lens→void_mirror (32), quantum_chip+control_unit→time_dilator (30), data_core+fusion_core→heal_beacon (34), void_beacon_core+plasma_cell→entropy_scepter (36)
裝甲: void_beacon_core+photon_plate→void_crown (34), void_beacon_core+titanium_alloy→void_cuirass (36), void_beacon_core+carbon_mesh→void_greaves (34), void_beacon_core+graviton_lens→void_sabatons (34)

### 4.3 擴展機器配方 (Expansion Machine Recipes)

#### 窯爐 (Kiln)
鐵錠+黏土→ceramic_plate (8)、ceramic_plate+heat_coil→thermal_ceramic (12)

#### 絕緣壓機 (Insulation Press)
copper_wire+rubber_sheet→insulated_wire (10)、servo_motor+insulated_wire→servo_bundle (12)、sensor_array (logic_gate+insulated_wire) (14)

#### 低溫蒸餾器 (Cryo Distiller)
coolant_mix+glass_panel→cryo_gel (10)、cryo_gel+thermal_ceramic→cryo_core (14)、coolant_canister (coolant_mix+ceramic_plate) (12)
食品: sunflare_fig+solarite_shard→helion_sorbet (18), coconut+fruit_puree→vanilla_ice_cream (18)

#### 基因剪接台 (Gene Splicer)
bio_resin+crops→bio_catalyst (14)、bio_catalyst+data_matrix→gene_template (16)、bio_resin+synthetic_fiber→neural_resin (19)、steel_plate+precision_nozzle→splicer_blade (17)

#### 生物合成槽 (Biosynth Vat)
gene_template+bio_catalyst→synth_meat (16)
食品: synth_meat+soybean_pods→protein_ration (18), fruit_puree+soybean_pods→orchard_ration (18), orange+peach→sunrise_pie (16), shadow_berry+spiceberry→berry_tart (16), peach+corn→peach_cobbler (16), pear+cornbread→pear_crisp (16), frost_apple+cryonite→cryon_hotpot (18), fruit_puree+soybean→cheesecake (16), fruit_puree+spiceberry→chocolate_cake (16)

#### 電漿精煉器 (Plasma Refiner)
ion_dust+refined_oil→plasma_dust (26)、titanium_alloy+plasma_dust→plasma_ingot (28)、plasma_ingot+alloy_frame→reactor_lattice (29)

#### 光子編織機 (Photon Weaver)
laser_lens+starlight_plate→photon_shard (23)、photon_shard+reinforced_glass→photon_plate (25)、photon_shard+optic_lens→observatory_lens (24)

#### 天文觀測台 (Observatory)
observatory_lens+data_matrix→star_chart (22)、magnetic_ring+graviton_lens→gravitic_bearing (24)、star_chart+vacuum_tube→vacuum_module (22)、star_chart+control_unit→data_core (23)、star_chart+data_core→survey_chip (24)

#### 資料檔案庫 (Data Archive)
data_core+gravitic_bearing→archive_plate (20)

#### 測繪信標 (Survey Beacon)
survey_chip+star_chart→survey_data (24)

#### 遺物解析儀 (Relic Analyzer)
survey_data+planetary_relic→ancient_signal (26 Aurelia)
survey_data+cryon_relic→ancient_signal (26 Cryon)
survey_data+nyx_relic→ancient_signal (26 Nyx)
ancient_signal+helion_relic→frontier_core_fragment (30 Helion)
ancient_signal+tempest_relic→frontier_core_fragment (30 Tempest)

#### 異星精煉台 (Xeno Refinery)
ancient_signal+irradiated_shard→xeno_alloy (28)
xeno_alloy+irradiated_shard+aurelia_parasite_gland→radiation_mesh (29)
xeno_alloy+cryonite_crystal+cryon_ice_heart→thermal_lining (29)
xeno_alloy+voidglass_fragment+nyx_phase_tissue→pressure_membrane (29)
xeno_alloy+solarite_shard+helion_cinder_core→flare_shielding (31)
xeno_alloy+stormglass_shard+tempest_capacitor→storm_baffle (31)

#### 行星鍛造台 (Planetary Forge)
xeno_alloy+composite_panel→exo_frame (30)、synthetic_fiber+void_bloom→seal_fabric (28)
**抗輻射套裝**: exo_frame+seal_fabric+radiation_mesh → helmet/chest/legs/boots (31-34)
**低溫套裝**: exo_frame+seal_fabric+thermal_lining → helmet/chest/legs/boots (31-34)
**真空套裝**: exo_frame+seal_fabric+pressure_membrane → helmet/chest/legs/boots (31-34)
**前線核心**: frontier_core_fragment+xeno_alloy+helion_cinder_core+tempest_capacitor → frontier_core (34)
**前線套裝**: frontier_core+flare_shielding+storm_baffle → helmet/chest/legs/boots (35-38)
**風暴噴射背包**: frontier_core+storm_baffle+drone_frame+precision_nozzle → storm_jetpack (40)

### 4.4 系統配方 (System Recipes)
> 來自 `tech-content-systems.yml`，共 28 道系統配方（quarry、harvester、turbine、feller、collector、dock、storage、filter、splitter、bus、cargo 等元件製造）

### 4.5 TIER4 終局配方 (Endgame Recipes)
> 來自 `tech-content.yml`，共 30 道終局配方

| 機器 | 輸入 | 輸出 | 能耗 |
|---|---|---|---|
| void_extractor | singularity_fragment+fusion_core | neutronium_ingot | 30 |
| void_extractor | neutronium_ingot+field_plate | neutronium_plate | 32 |
| dark_matter_loom | neutronium_plate+quantum_fluid | dark_matter_dust | 34 |
| dark_matter_loom | dark_matter_dust+fusion_mesh | dark_matter_sheet | 36 |
| graviton_stabilizer | dark_matter_dust+crystal_matrix | graviton_lens | 36 |
| graviton_stabilizer | graviton_lens+stability_core | graviton_core | 38 |
| singularity_press | dark_matter_sheet+graviton_core | singularity_mesh | 40 |
| singularity_press | singularity_mesh+reactor_core | singularity_core | 42 |
| antimatter_synthesizer | singularity_core+plasma_cell | antimatter_cluster | 44 |
| antimatter_synthesizer | antimatter_cluster+field_plate | antimatter_cell | 46 |
| stellar_forge | antimatter_cell+neutronium_plate | stellar_alloy | 46 |
| stellar_forge | stellar_alloy+crystal_matrix | stellar_glass | 48 |
| orbital_printer | stellar_alloy+quantum_frame | orbit_frame | 50 |
| orbital_printer | orbit_frame+data_matrix | orbit_processor | 52 |
| chrono_engine | orbit_processor+singularity_mesh | quantum_fabric | 52 |
| chrono_engine | quantum_fabric+graviton_core | chrono_thread | 54 |
| chrono_engine | chrono_thread+antimatter_cell | chrono_core | 56 |
| void_beacon | chrono_core+singularity_core | void_shard | 56 |
| void_beacon | void_shard+stellar_glass | void_crystal | 58 |
| void_beacon | void_crystal+chrono_core | void_beacon_core | 60 |
| nanite_foundry | void_shard+dark_matter_dust | nanite_slurry | 54 |
| nanite_foundry | nanite_slurry+quantum_fabric | nanite_cluster | 56 |
| entropy_chamber | nanite_cluster+antimatter_cluster | entropy_plate | 58 |
| entropy_chamber | entropy_plate+void_crystal | entropy_core | 60 |
| celestial_assembler | entropy_core+stellar_alloy | cosmic_matrix | 62 |
| celestial_assembler | cosmic_matrix+orbit_processor | cosmic_panel | 64 |
| omega_fabricator | cosmic_panel+void_beacon_core | omega_core | 66 |

### 4.6 巨構配方 (Megastructure Recipes)
> 來自 `tech-content-megastructures.yml`，共 21 道配方

| 機器 | 輸入 | 輸出 | 能耗 |
|---|---|---|---|
| starsteel_foundry | stellar_alloy+neutronium_plate | starsteel_ingot | 50 |
| starsteel_foundry | starsteel_ingot+orbit_frame | starsteel_frame | 52 |
| vacuum_chamber | stellar_glass+dark_matter_sheet | vacuum_glass | 54 |
| vacuum_chamber | vacuum_glass+singularity_core | harmonic_crystal | 56 |
| vacuum_chamber | harmonic_crystal+graviton_lens | celestial_lens | 58 |
| vacuum_chamber | chrono_thread+dark_matter_sheet | nebula_fiber | 56 |
| warp_assembler | antimatter_cell+starsteel_frame | warp_cell | 58 |
| warp_assembler | warp_cell+chrono_core | warp_coil | 60 |
| warp_assembler | warp_coil+orbit_processor | astral_circuit | 62 |
| event_horizon_smith | singularity_mesh+starsteel_ingot | event_horizon_plate | 60 |
| event_horizon_smith | event_horizon_plate+graviton_core | singularity_casing | 62 |
| event_horizon_smith | void_crystal+chrono_thread | chrono_lattice | 64 |
| continuum_lathe | chrono_lattice+singularity_casing | continuum_shell | 64 |
| continuum_lathe | nanite_cluster+stellar_glass | aurora_plate | 62 |
| relic_scanner | entropy_core+data_matrix | relic_data | 60 |
| relic_scanner | relic_data+celestial_lens | archive_core | 62 |
| anchor_forge | warp_coil+event_horizon_plate | dimension_anchor | 66 |
| anchor_forge | astral_circuit+continuum_shell | cluster_manifold | 68 |
| apex_forge | cluster_manifold+dimension_anchor+archive_core | omega_matrix | 70 |
| apex_forge | omega_matrix+void_beacon_core | apex_core | 72 |
| apex_forge | apex_core+cosmic_panel+omega_matrix | omega_archive | 74 |

### 4.7 互動烹飪配方 (Cooking Recipes)
> 來自 `tech-content-expansion.yml: cooking-recipes`。放在營火/煙燻/高爐上右鍵互動。

| 配方 ID | 輸入 | 輸出 | 烹飪時間(tick) | 可用站 |
|---|---|---|---|---|
| cooked_beef | BEEF | 烤牛排 | 100 | 營火/煙燻/高爐 |
| cooked_porkchop | PORKCHOP | 烤豬排 | 100 | 營火/煙燻/高爐 |
| cooked_chicken | CHICKEN | 烤雞肉 | 80 | 營火/煙燻 |
| cooked_mutton | MUTTON | 烤羊排 | 100 | 營火/煙燻 |
| cooked_rabbit | RABBIT | 烤兔肉 | 80 | 營火/煙燻 |
| cooked_cod | COD | 烤鱈魚 | 60 | 營火/煙燻 |
| cooked_salmon | SALMON | 烤鮭魚 | 60 | 營火/煙燻 |
| baked_potato | POTATO | 烤馬鈴薯 | 80 | 營火/煙燻/高爐 |
| dried_kelp | KELP | 乾海帶 | 40 | 營火/煙燻 |
| synth_steak | synth_meat (科技) | 合成肉排 | 120 | 營火/煙燻/高爐 |
| roasted_corn | corn (科技) | 烤玉米 | 80 | 營火/煙燻 |
| garlic_bread | garlic (科技) | 蒜香麵包 | 60 | 營火/煙燻 |
| grilled_vegetables | bell_pepper (科技) | 烤時蔬 | 100 | 營火/煙燻/高爐 |
| pancakes | WHEAT | 鬆餅 | 80 | 營火/煙燻 |
| honey_toast | BREAD | 蜂蜜吐司 | 60 | 營火/煙燻 |
| green_tea | tea_leaf (科技) | 綠茶 | 80 | 營火/煙燻 |
| espresso | tea_leaf (科技) | 濃縮咖啡 | 100 | 營火/煙燻/高爐 |
| hot_cocoa | COCOA_BEANS | 熱可可 | 80 | 營火/煙燻 |
| matcha_latte | tea_leaf (科技) | 抹茶拿鐵 | 100 | 營火/煙燻 |
| milk_tea | tea_leaf (科技) | 奶茶 | 80 | 營火/煙燻 |
| fried_rice | corn (科技) | 炒飯 | 100 | 營火/煙燻 |
| noodle_soup | WHEAT | 湯麵 | 100 | 營火/煙燻 |
| mushroom_risotto | RED_MUSHROOM | 蘑菇燉飯 | 120 | 營火/煙燻 |
| potato_soup | POTATO | 馬鈴薯湯 | 100 | 營火/煙燻 |
| dumpling | cabbage (科技) | 蒸餃 | 100 | 營火/煙燻 |
| fish_stew | COD | 鮮魚湯 | 100 | 營火/煙燻 |
| donut | WHEAT | 甜甜圈 | 80 | 營火/煙燻 |
| brownie | COCOA_BEANS | 布朗尼 | 100 | 營火/煙燻 |
| cinnamon_roll | WHEAT | 肉桂捲 | 80 | 營火/煙燻 |
| muffin | blueberry (科技) | 鬆糕 | 80 | 營火/煙燻 |

---

## 5. 行星系統 (Planet System)

### 概覽
- 星門旅行能耗: **1200 EU**
- 星門閒置消耗: **12 EU/tick**
- 行星邊界半徑: **3000 方塊**
- 6 個目的地: 地球 (Earth) + 5 顆星球

### 星球一覽

| 星球 ID | 顯示名稱 | 世界名 | 環境 | 危害類型 | 防護套裝 | 遺跡核心 | 遺跡啟動物品 | 遺跡獎勵 |
|---|---|---|---|---|---|---|---|---|
| aurelia | 奧蕾莉亞 | tech_aurelia | NORMAL | RADIATION (輻射) | anti_radiation 套裝 | RESPAWN_ANCHOR | survey_data | planetary_relic, ancient_signal |
| cryon | 克里昂 | tech_cryon | NORMAL | CRYO (低溫) | thermal 套裝 | LODESTONE | survey_data | cryon_relic, ancient_signal |
| nyx | 倪克斯 | tech_nyx | THE_END | VACUUM (真空) | pressure 套裝 | END_PORTAL_FRAME | survey_data | nyx_relic, ancient_signal |
| helion | 赫利昂 | tech_helion | NORMAL | SOLAR (日灼) | frontier 套裝 | BEACON | ancient_signal | helion_relic, frontier_core_fragment |
| tempest | 坦佩斯特 | tech_tempest | NORMAL | STORM (風暴) | frontier 套裝 | LIGHTNING_ROD | ancient_signal | tempest_relic, frontier_core_fragment |

### 行星進階說明
- **Aurelia / Cryon / Nyx**: 初階星球，使用 `survey_data` 啟動遺跡，各自對應單一防護套裝
- **Helion / Tempest**: 進階星球，需已有 `ancient_signal` 才能啟動遺跡，需 **前線套裝** (Frontier suit)
- 前線套裝可抵禦 **所有** 5 種星球危害
- 每顆星球有特殊怪物掉落品（寄生腺體/冰脈核心/相位膜組織/灰燼燃核/電容核），用於製作進階防護層

### 星門選單佈局 (Gate Menu)
6 格選擇：Earth (回地球) | Aurelia | Helion | Nyx | Cryon | Tempest

### 行星反食物 (Planet Counter-Foods)
每顆星球對應特殊飲料可減輕危害效果：
| 食物 ID | 顯示名稱 | 星球 | 配方 |
|---|---|---|---|
| aurelia_glaze | 輻晶蜜凍 | Aurelia | lumenfruit + irradiated_shard (bio_lab) |
| cryon_hotpot | 霜芯暖鍋 | Cryon | frost_apple + cryonite_crystal (biosynth_vat) |
| nyx_phase_gel | 相位凝露 | Nyx | shadow_berry + voidglass_fragment (coolant_mixer) |
| helion_sorbet | 日冕雪酪 | Helion | sunflare_fig + solarite_shard (cryo_distiller) |
| tempest_fizz | 雷雲汽泡飲 | Tempest | stormplum + stormglass_shard (coolant_mixer) |

---

## 6. 成就系統 (Achievements)

### 入門 (Beginner) — 4 項
| ID | 名稱 | 條件 | XP | 代幣 | 稱號 |
|---|---|---|---|---|---|
| first_machine | 第一台機器 | 放置第一台機器 | 50 | 0 | &7[&f機械新手&7] |
| newcomer_craft | 合成學徒 | 首次在研究台合成 | 30 | 2 | &7[&f合成學徒&7] |
| starter_power | 初始電力 | 首次放置發電機 | 40 | 3 | &7[&f電力初心&7] |
| first_research | 首次研究 | 首次花費研究點數 | 30 | 2 | &7[&f研究見習&7] |

### 加工 (Processing) — 8 項
| ID | 名稱 | 條件 | XP | 代幣 | 稱號 |
|---|---|---|---|---|---|
| first_crush | 碎礦入門 | 首次使用碎礦機 | 60 | 4 | &7[&f碎礦工&7] |
| first_compress | 壓縮初試 | 首次使用壓縮器 | 60 | 4 | &7[&f壓縮工&7] |
| first_smelt | 冶煉先行 | 首次使用精煉爐 | 60 | 4 | &7[&f冶煉工&7] |
| assembly_starter | 組裝新手 | 首次使用裝配台 | 80 | 6 | &7[&f組裝技師&7] |
| process_milestone_100 | 加工百次 | 累計加工 100 物品 | 100 | 8 | &6[&e百鍊匠&6] |
| process_milestone_500 | 流水線工人 | 累計加工 500 物品 | 180 | 14 | &6[&e流水線之星&6] |
| process_milestone_2000 | 加工之王 | 累計加工 2000 物品 | 300 | 22 | &c[&6加工之王&c] |
| recycler_expert | 回收專家 | 回收 1000 物品 | 180 | 15 | &a[&2回收大師&a] |

### 能源 (Energy) — 7 項
| ID | 名稱 | 條件 | XP | 代幣 | 稱號 |
|---|---|---|---|---|---|
| energy_beginner | 能源新手 | 累計發電 100 | 100 | 10 | &e[&f能源學徒&e] |
| energy_500 | 小型電廠 | 發電 500 | 140 | 10 | &e[&6小型電廠&e] |
| energy_2000 | 中型電廠 | 發電 2000 | 200 | 16 | &6[&e中型電廠&6] |
| energy_10000 | 大型電廠 | 發電 10000 | 350 | 24 | &c[&6大型電廠&c] |
| solar_pioneer | 太陽能先驅 | 建造太陽能板 | 80 | 6 | &e[&f☀ 太陽先驅&e] |
| generator_collector | 發電機收藏家 | 建造 3 種發電機 | 160 | 12 | &6[&e發電收藏家&6] |
| power_grid_master | 電網主宰 | 發電 50000 | 500 | 32 | &4[&c⚡ 電網主宰&4] |

### 農業 / 烹飪 (Agriculture) — 8 項
| ID | 名稱 | 條件 | XP | 代幣 | 稱號 |
|---|---|---|---|---|---|
| first_harvest | 初次收穫 | 收穫第一批作物 | 40 | 3 | &a[&f農夫&a] |
| crop_100 | 百株農場 | 收割 100 批作物 | 100 | 8 | &a[&2百畝農場&a] |
| crop_king | 農作之王 | 收割 1000 作物 | 180 | 12 | &2[&a農作之王&2] |
| cook_first_meal | 首道料理 | 首次互動烹調 | 50 | 4 | &e[&f見習廚師&e] |
| cook_10_meals | 業餘廚師 | 互動烹調 10 道菜 | 100 | 8 | &6[&e業餘廚師&6] |
| cook_50_meals | 料理達人 | 互動烹調 50 道菜 | 200 | 16 | &c[&6料理達人&c] |
| bio_researcher | 生質研究員 | 首次使用基因切片台 | 120 | 10 | &a[&2生質研究員&a] |
| orchard_keeper | 果園園丁 | 種植/收穫 5 種作物 | 80 | 6 | &a[&f果園園丁&a] |

### 物流 (Logistics) — 3 項
| ID | 名稱 | 條件 | XP | 代幣 | 稱號 |
|---|---|---|---|---|---|
| first_transfer | 首次搬運 | 完成首次物流搬運 | 60 | 4 | &b[&f搬運工&b] |
| transfer_500 | 物流新星 | 500 次物流搬運 | 140 | 10 | &b[&3物流新星&b] |
| storage_master | 倉儲大師 | 建立 5+數位儲存 | 180 | 14 | &3[&b倉儲大師&3] |

### 探索 (Exploration) — 6 項
| ID | 名稱 | 條件 | XP | 代幣 | 稱號 |
|---|---|---|---|---|---|
| first_planet_visit | 星際旅人 | 首次前往其他星球 | 200 | 16 | &9[&b✦ 星際旅人&9] |
| planet_explorer_3 | 三星探索者 | 造訪 3 顆星球 | 300 | 22 | &9[&b✦ 三星探索者&9] |
| planet_explorer_all | 全星系漫遊 | 造訪所有星球 | 500 | 32 | &5[&d✦ 全星系漫遊&5] |
| gate_builder | 星門建造者 | 建造第一座星際門 | 180 | 14 | &9[&b星門建造者&9] |
| discover_ruins | 遺跡發現者 | 在星球啟動遺跡 | 240 | 18 | &5[&d遺跡發現者&5] |
| planet_sample_collector | 星球採樣員 | 收集 20 份樣本 | 260 | 20 | &9[&b星球採樣員&9] |

### 收集 (Collection) — 9 項
| ID | 名稱 | 條件 | XP | 代幣 | 稱號 |
|---|---|---|---|---|---|
| tech_collector | 科技收藏家 | 收集全部基礎材料 | 120 | 8 | &e[&f收藏家&e] |
| collect_10 | 初窺門徑 | 解鎖 10 種物品 | 60 | 4 | &7[&f初窺門徑&7] |
| collect_25 | 物品鑑賞家 | 解鎖 25 種 | 120 | 10 | &e[&f鑑賞家&e] |
| item_conqueror | 物品征服者 | 解鎖 50 種 | 300 | 25 | &6[&e征服者&6] |
| collect_100 | 收集狂人 | 解鎖 100 種 | 400 | 28 | &c[&6收集狂人&c] |
| collect_200 | 科技博物館 | 解鎖 200 種 | 600 | 40 | &4[&c科技博物館&4] |
| machine_collector_10 | 機器建造師 | 建造 10 種機器 | 160 | 12 | &b[&3機器建造師&b] |
| machine_collector_20 | 工業大亨 | 建造 20 種機器 | 280 | 20 | &6[&e工業大亨&6] |
| all_machines_collector | 機器全通 | 建造所有機器 | 500 | 36 | &4[&c&l機器全通&4] |

### 終局 (Endgame) — 4 項
| ID | 名稱 | 條件 | XP | 代幣 | 稱號 |
|---|---|---|---|---|---|
| quantum_engineer | 量子工程師 | 製作量子晶片 | 250 | 20 | &d[&5量子工程師&d] |
| plasma_forge_master | 等離子鍛造師 | 使用電漿精煉爐 | 300 | 22 | &c[&6等離子鍛造師&c] |
| cosmic_assembler | 宇宙裝配員 | 使用天穹裝配台 | 360 | 26 | &d[&5宇宙裝配員&d] |
| mega_builder | 巨構工程師 | 建造第一座巨構 | 450 | 30 | &6[&e巨構工程師&6] |

### 通用 (General) — 4 項
| ID | 名稱 | 條件 | XP | 代幣 | 稱號 |
|---|---|---|---|---|---|
| automation_master | 自動化大師 | 同時 10 台運作 | 150 | 12 | &b[&3自動化大師&b] |
| full_factory | 滿載工廠 | 機器達 20 台 | 220 | 18 | &6[&e滿載工廠&6] |
| mega_factory | 超級工廠 | 同時 50 台運作 | 400 | 28 | &4[&c&l超級工廠&4] |
| completionist | 全成就達成者 | 完成所有成就 | 500 | 32 | &4[&6&l✦ 全成就 ✦&4] |

### 系統成就 (System) — 4 項
| ID | 名稱 | 條件 | XP | 代幣 | 稱號 |
|---|---|---|---|---|---|
| field_operator | 野外作業員 | — | — | — | &a[&2野外作業員&a] |
| ecosystem_engineer | 生態工程師 | — | — | — | &2[&a生態工程師&2] |
| logistics_director | 物流總監 | — | — | — | &3[&b物流總監&3] |
| cargo_architect | 貨運架構師 | — | — | — | &3[&b&l貨運架構師&3] |

### 巨構成就 (Megastructure) — 4 項
| ID | 名稱 | 條件 | XP | 代幣 | 稱號 |
|---|---|---|---|---|---|
| singularity_breaker | 奇點破界者 | — | — | — | &5[&d✧ 奇點破界者&5] |
| stellar_founder | 星核開鑄者 | — | — | — | &6[&e✧ 星核開鑄者&6] |
| omega_theory | 終極理論家 | — | — | — | &d[&5✧ 終極理論家&d] |
| apex_of_tech | 科技巔峰 | — | — | — | &4[&c&l★ 科技巔峰&4] |

### 擴展成就 (Expansion) — 6 項
| ID | 名稱 | 條件 | XP | 代幣 | 稱號 |
|---|---|---|---|---|---|
| network_architect | 物流架構師 | 累計 2000 次搬運 | 320 | 18 | &3[&b物流架構師&3] |
| upgrade_engineer | 升級工程師 | 首次取得距離升級 | 220 | 12 | &b[&3升級工程師&b] |
| fusion_overlord | 聚變霸主 | 製作聚變核心 | 360 | 24 | &c[&4聚變霸主&c] |
| void_pioneer | 虛空先鋒 | 首次抽出虛空碎片 | 420 | 28 | &5[&d虛空先鋒&5] |
| cryo_specialist | 低溫專家 | 首次使用低溫蒸餾器 | 180 | 12 | &b[&3低溫專家&b] |
| refinery_master | 精煉大師 | 精煉系加工 500 個 | 240 | 16 | &6[&e精煉大師&6] |

**成就總計: 67 項**

---

## 7. 藍圖系統 (Blueprint System)

### 工作台藍圖

工作台 (`register-recipe: true`) 使用標準 3×3 合成，每個藍圖定義：
- `shape`: 3 行字串，每字元代表一個材料槽 (`_` = 空)
- `ingredients`: 字元對應的物品 (支援 `minecraft:` 和 科技物品 `techproject:`)
- `result`: 產出物品及數量
- `tutorial`: 教學文字
- `placement`: 放置提示

**有工作台藍圖的機器 (19 台):**
research_desk, solar_generator, coal_generator, crusher, furnace, compressor, assembler, advanced_assembler, quantum_processor, fusion_reactor, electric_saw, recycler, auto_farm, battery_bank, greenhouse, energy_node, energy_cable, logistics_node, item_tube

**僅教學無合成的機器 (`register-recipe: false`):**
系統機器 (quarry_drill, crop_harvester, vacuum_inlet, storm_turbine, tree_feller, mob_collector, fishing_dock, storage_hub, filter_router, splitter_node, industrial_bus, cargo_input_node, cargo_manager, trash_node, cargo_motor) 和擴展機器 (kiln, insulation_press, cryo_distiller, gene_splicer, biosynth_vat, plasma_refiner, photon_weaver, observatory, data_archive, survey_beacon, relic_analyzer, xeno_refinery, planetary_forge, planetary_gate, planetary_harvester) — 透過研究台機器配方解鎖

---

## 8. 導覽書系統 (Guide / Book System)

### 指令
- `/tech book` 或 `/techbook` — 開啟科技百科

### 導覽章節列表 (tech-guides_zh_tw.yml)

| 章節 ID | 標題 | 類型 |
|---|---|---|
| starter_path | 新手入門路線 | 入門 |
| chapter_1 | 工業基礎 | 基礎 |
| chapter_2 | 碎礦與冶煉 | 加工 |
| chapter_3 | 壓縮器與製造 | 加工 |
| chapter_4 | 太陽能與能源 | 能源 |
| chapter_5 | 電池與升級 | 能源 |
| chapter_6 | 淨化與離心 | 精煉 |
| chapter_7 | 進階製造與進階電路 | 進階 |
| chapter_8 | 量子科技 | 量子 |
| chapter_9 | 晶體、力場與聚變 | 高階 |
| chapter_10 | 物質編譯器與奇點 | 高階 |
| chapter_11 | 能源網路與物流 | 物流 |
| chapter_12 | 電漿與光子科技 | 電漿 |
| chapter_13 | 天文觀測與資料 | 天文 |
| chapter_14 | 異星精煉 | 星球 |
| chapter_15 | 行星防護套裝 | 裝備 |
| chapter_16 | 終局科技（TIER4） | 終局 |
| chapter_17 | 虛空科技線 | 終局 |
| chapter_18 | 巨構科技 | 巨構 |
| chapter_19 | 安卓機器人 | 安卓 |
| chapter_20 | 武器與護符 | 戰鬥 |
| chapter_21 | 野外自動化 | 系統 |
| chapter_22 | 科技農業 | 農業 |
| chapter_23 | 行星系統概覽 | 星球 |
| chapter_24 | 奧蕾莉亞 | 星球 |
| chapter_25 | 克里昂 | 星球 |
| chapter_26 | 倪克斯 | 星球 |
| chapter_27 | 赫利昂 | 星球 |
| chapter_28 | 坦佩斯特 | 星球 |
| chapter_29 | 星門建造 | 星球 |
| chapter_30 | 前線套裝 | 裝備 |
| chapter_31 | 遺跡探索 | 星球 |
| chapter_32 | 行星反食物 | 星球 |
| android_overview | 安卓系統總覽 | 安卓 |
| android_scripts | 安卓程序腳本 | 安卓 |
| android_cards | 安卓升級卡 | 安卓 |

---

## 9. 科技樹進程 (Tech Tree Progression)

### 階級架構

```
TIER 1 (初始/基礎)
├── 碎礦機 → 鐵粉/銅粉/煤粉/矽
├── 電爐 → 鐵錠/銅錠/玻璃板/強化玻璃
├── 壓縮器 → 鐵板/塑膠/碳纖網/複合面板
├── 製造機 → 電路板/機械零件/能源電池
├── 電鋸 → 木屑/纖維網
├── 回收機 → 回收材料
├── 自動農場 → 種子/作物
├── 太陽能發電機
└── 燃煤發電機

TIER 2 (中期)
├── 精煉爐 → 鋼板/合金框架/鈦合金
├── 進階製造機 → 進階電路/奈米塗層/訊號中繼器
├── 洗礦機 → 礦漿/粉碎生質
├── 淨化器 → 淨化碎晶/冷卻電池
├── 離心機 → 離子粉/磁環
├── 生質實驗室 → 生質樹脂/生質纖維
├── 溫室培育艙 → 水凝膠/生長燈
├── 聚合壓床 → 橡膠片/處理塑膠
├── 拉線機 → 銅線/鋼線/線束
├── 化學反應器 → 聚合樹脂/合成纖維
├── 冷卻混合器 → 冷卻混合液/飲料
├── 精煉塔 → 精煉油
├── 太陽能陣列
├── 電池庫
├── 能源節點/導能線纜
├── 物流節點/物流導管
├── 窯爐/絕緣壓機/低溫蒸餾器
├── 基因剪接台/生物合成槽
└── 系統機器 (quarry/harvester/turbine...)

TIER 3 (高階)
├── 量子處理器 → 量子晶片/量子流體/量子框架/通量連結
├── 雷射雕刻機 → 雷射鏡/蝕刻電路/控制單元
├── 無人機艙/安卓艙 → 無人機/安卓框架
├── 安卓工作站 → 部署安卓機器人
├── 聚變反應爐 → 聚變網/穩定核心/聚變核心
├── 晶體培育艙 → 晶種/培育晶體
├── 力場鍛造台 → 力場板/武器/護符
├── 物質編譯器 → 物質團/奇點碎片/終極武器
├── 電漿精煉器 → 電漿粉/電漿錠
├── 光子編織機 → 光子碎片/光子板
├── 天文觀測台 → 星圖/測繪晶片
├── 行星系統 (survey_beacon → relic_analyzer → xeno_refinery → planetary_forge)
├── 行星星門 (planetary_gate)
└── 貨物管理器 (cargo_manager)

TIER 4 (終局)
├── 虛空抽取器 → 中子合金
├── 暗物質織機 → 暗物質
├── 重力子穩定器 → 重力子透鏡/核心
├── 奇點壓床 → 奇點網/核心
├── 反物質合成器 → 反物質
├── 恆星鑄造廠 → 恆星合金/玻璃
├── 軌道列印機 → 軌道框架/處理器
├── 時序引擎 → 時序絲線/核心
├── 虛空信標 → 虛空碎片/晶體/信標核心
├── 奈米工坊 → 奈米漿液/簇
├── 熵值艙 → 熵值板/核心
├── 天穹裝配台 → 宇宙矩陣/面板
└── 歐米茄製造器 → 歐米茄核心

TIER 4+ (巨構)
├── 星鋼鑄造廠 → 星鋼錠/框架
├── 真空室 → 真空玻璃/諧波晶體/天穹透鏡/星雲纖維
├── 曲速裝配台 → 曲速電池/線圈/星界電路
├── 事件視界鍛台 → 事件視界板/奇點外殼/時序格架
├── 連續體車床 → 連續體殼/極光板
├── 遺物掃描儀 → 遺物數據/檔案核心
├── 錨定鍛台 → 維度錨定/簇流形
└── 巔峰鍛台 → 歐米茄矩陣/巔峰核心/歐米茄檔案
```

---

## 10. 食物系統 (Food System)

### 食物類型

#### 機器合成食物 (Machine Recipes)
| 食物 ID | 顯示名稱 | 製造機器 | 材料 |
|---|---|---|---|
| nutrition_bar | 營養棒 | bio_lab / biosynth_vat | soybean_pods+spiceberry / sunflare_fig+fruit_puree |
| protein_ration | 蛋白口糧 | biosynth_vat | synth_meat+soybean_pods |
| radiant_tea | 輝光茶飲 | coolant_mixer | tea_leaf+hydro_gel |
| fruit_puree | 果泥 | bio_lab | lumenfruit+shadow_berry |
| nebula_juice | 星雲果汁 | coolant_mixer | frost_apple+lumenfruit |
| orchard_ration | 果園口糧 | biosynth_vat | fruit_puree+soybean_pods |
| orchard_salad | 果園沙拉 | bio_lab | tomato+cabbage |
| cornbread | 玉米麵包 | bio_lab | corn+soybean_pods |
| stuffed_cabbage | 捲心菜捲 | bio_lab | cabbage+onion |
| sunrise_pie | 晨光果派 | biosynth_vat | orange+peach |
| berry_tart | 漿果塔派 | biosynth_vat | shadow_berry+spiceberry |
| citrus_salad | 柑橘沙拉 | bio_lab | orange+lemon |
| tomato_stew | 番茄燉鍋 | bio_lab | tomato+onion |
| orchard_smoothie | 果園奶昔 | coolant_mixer | peach+pear |
| sparkling_lemonade | 氣泡檸橙飲 | coolant_mixer | lemon+orange |
| peach_cobbler | 蜜桃派盆 | biosynth_vat | peach+corn |
| pear_crisp | 青梨脆派 | biosynth_vat | pear+cornbread |
| vegetable_curry | 蔬菜咖哩 | bio_lab | onion+bell_pepper |
| ramen | 拉麵 | bio_lab | soybean_pods+spiceberry |
| sushi_roll | 壽司捲 | bio_lab | corn+tomato |
| cheese_pizza | 起司披薩 | bio_lab | tomato+garlic |
| veggie_wrap | 蔬菜捲餅 | bio_lab | lettuce+tomato |
| breakfast_burrito | 早安捲餅 | bio_lab | bell_pepper+corn |
| fruit_parfait | 水果帕菲 | bio_lab | strawberry+blueberry |
| cheesecake | 起司蛋糕 | biosynth_vat | fruit_puree+soybean_pods |
| chocolate_cake | 巧克力蛋糕 | biosynth_vat | fruit_puree+spiceberry |
| vanilla_ice_cream | 香草冰淇淋 | cryo_distiller | coconut+fruit_puree |

#### 飲料 (Coolant Mixer)
| 飲料 ID | 顯示名稱 | 材料 |
|---|---|---|
| lemon_juice | 檸果汁 | lemon+hydro_gel |
| orange_juice | 香橙汁 | orange+hydro_gel |
| peach_juice | 蜜桃汁 | peach+hydro_gel |
| pear_juice | 青梨汁 | pear+hydro_gel |
| cherry_juice | 櫻桃汁 | cherry+hydro_gel |
| grape_juice | 葡萄汁 | grape+hydro_gel |
| mango_smoothie | 芒果奶昔 | mango+coconut |
| coconut_water | 椰子水 | coconut+hydro_gel |
| berry_blend | 綜合莓果汁 | strawberry+blueberry |
| tropical_punch | 熱帶水果賓治 | pineapple+mango |
| iced_tea | 冰紅茶 | tea_leaf+hydro_gel |
| apple_cider | 蘋果醋飲 | pear+hydro_gel |
| ginger_ale | 薑汁汽水 | watermelon+hydro_gel |
| vanilla_milkshake | 香草奶昔 | banana+coconut |
| strawberry_milkshake | 草莓奶昔 | strawberry+coconut |

#### 互動烹飪 (Cooking — 營火/煙燻)
烤牛排、烤豬排、烤雞肉、烤羊排、烤兔肉、烤鱈魚、烤鮭魚、烤馬鈴薯、乾海帶、合成肉排、烤玉米、蒜香麵包、烤時蔬、鬆餅、蜂蜜吐司、綠茶、濃縮咖啡、熱可可、抹茶拿鐵、奶茶、炒飯、湯麵、蘑菇燉飯、馬鈴薯湯、蒸餃、鮮魚湯、甜甜圈、布朗尼、肉桂捲、鬆糕

#### 星球反食物 (Planet Counter-Foods)
aurelia_glaze (輻晶蜜凍)、cryon_hotpot (霜芯暖鍋)、nyx_phase_gel (相位凝露)、helion_sorbet (日冕雪酪)、tempest_fizz (雷雲汽泡飲)

---

## 11. 神器 / 裝備 / 護符 (Artifacts / Equipment / Talismans)

### 武器 (Weapons)

| 武器 ID | 顯示名稱 | 階級 | 製造機器 | 材料 |
|---|---|---|---|---|
| pulse_staff | 脈衝法杖 | TIER3 | field_forge | advanced_circuit+dense_carbon_block |
| storm_staff | 風暴法杖 | TIER3 | field_forge | advanced_circuit+heat_coil |
| gravity_staff | 重力法杖 | TIER3 | field_forge | graviton_lens+advanced_circuit |
| warp_orb | 曲速球 | TIER3 | field_forge | quantum_chip+graviton_lens |
| cryo_wand | 冰晶權杖 | TIER3 | field_forge | coolant_cell+advanced_circuit |
| plasma_lance | 電漿長矛 | TIER4 | matter_compiler | plasma_cell+titanium_alloy |
| void_mirror | 虛空之鏡 | TIER4 | matter_compiler | photon_plate+graviton_lens |
| time_dilator | 時間膨脹器 | TIER4 | matter_compiler | quantum_chip+control_unit |
| heal_beacon | 修復信標 | TIER4 | matter_compiler | data_core+fusion_core |
| entropy_scepter | 熵變權杖 | TIER4 | matter_compiler | void_beacon_core+plasma_cell |

### 護甲套裝 (Armor Sets)

#### 泰坦套裝 (Titan — TIER2)
| 部件 | 材料 | 機器 | 能耗 |
|---|---|---|---|
| titan_helmet | composite_panel+circuit_board | assembler | 14 |
| titan_chestplate | composite_panel+carbon_mesh | assembler | 16 |
| titan_leggings | composite_panel+copper_wire | assembler | 14 |
| titan_boots | composite_panel+rubber_sheet | assembler | 12 |

#### 量子套裝 (Quantum — TIER3)
| 部件 | 材料 | 機器 | 能耗 |
|---|---|---|---|
| quantum_helmet | quantum_chip+titanium_alloy | quantum_processor | 24 |
| quantum_chestplate | quantum_chip+photon_plate | quantum_processor | 26 |
| quantum_leggings | quantum_chip+carbon_mesh | quantum_processor | 24 |
| quantum_boots | quantum_chip+graviton_lens | quantum_processor | 24 |

#### 虛空套裝 (Void — TIER4)
| 部件 | 材料 | 機器 | 能耗 |
|---|---|---|---|
| void_crown | void_beacon_core+photon_plate | matter_compiler | 34 |
| void_cuirass | void_beacon_core+titanium_alloy | matter_compiler | 36 |
| void_greaves | void_beacon_core+carbon_mesh | matter_compiler | 34 |
| void_sabatons | void_beacon_core+graviton_lens | matter_compiler | 34 |

#### 行星防護套裝 (Planet Suits)
- **抗輻射套裝** (Anti-Radiation): 對抗 Aurelia 輻射；exo_frame+seal_fabric+radiation_mesh
- **低溫套裝** (Thermal): 對抗 Cryon 低溫；exo_frame+seal_fabric+thermal_lining
- **真空套裝** (Pressure): 對抗 Nyx 真空；exo_frame+seal_fabric+pressure_membrane
- **前線套裝** (Frontier): 對抗 Helion+Tempest 全危害；frontier_core+flare_shielding+storm_baffle

### 工具 (Tools)

| 工具 ID | 顯示名稱 | 說明 | 製造機器 |
|---|---|---|---|
| field_sickle | 田野鐮刀 | 3×3 範圍收穫 | assembler |
| hydro_spade | 水培鏟 | 加速作物生長 | assembler |
| vector_grapple | 向量抓鉤 | 鉤爪移動 | assembler |
| pulse_thruster | 脈衝推進器 | 水平衝刺推進 | advanced_assembler |
| storm_jetpack | 風暴噴射背包 | 飛行裝備 | planetary_forge |

### 護符 (Talismans — 12 種)

| 護符 ID | 顯示名稱 | 效果概述 | 製造機器 | 能耗 |
|---|---|---|---|---|
| talisman_fire | 防焰護符 | 火焰抗性 | field_forge | 14 |
| talisman_water | 深海護符 | 水下呼吸 | field_forge | 14 |
| talisman_angel | 天使護符 | 摔落保護 | field_forge | 12 |
| talisman_warrior | 戰士護符 | 力量增強 | field_forge | 14 |
| talisman_knight | 騎士護符 | 防禦增強 | laser_engraver | 20 |
| talisman_traveler | 旅者護符 | 速度增強 | field_forge | 12 |
| talisman_hunter | 獵人護符 | 夜視 | laser_engraver | 20 |
| talisman_miner | 礦工護符 | 急速挖掘 | laser_engraver | 20 |
| talisman_farmer | 農夫護符 | 農業Buff | field_forge | 14 |
| talisman_anvil | 鐵砧護符 | 抗擊退 | laser_engraver | 22 |
| talisman_heal | 再生護符 | 生命恢復 | laser_engraver | 20 |
| talisman_whirlwind | 旋風護符 | 擊退敵人 | laser_engraver | 20 |

---

## 12. 指令 (Commands)

### 主指令
`/tech` (別名: `/techbook`)

### 子指令一覽
| 子指令 | 說明 |
|---|---|
| `/tech book` | 開啟科技百科 GUI |
| `/tech list` | 列出所有科技物品 |
| `/tech stats` | 查看個人科技統計 |
| `/tech achievements` | 查看成就進度 |
| `/tech give <物品> [數量]` | 給予科技物品 (管理員) |
| `/tech reload` | 重載設定檔 (管理員) |
| `/tech xp <玩家> <數量>` | 設定研究 XP (管理員) |
| `/tech research <玩家> <項目>` | 直接解鎖研究 (管理員) |
| `/tech title` | 查看目前稱號 |
| `/tech title clear` | 取消稱號 |

### 權限
| 權限 | 說明 | 預設 |
|---|---|---|
| `techproject.admin` | 管理員權限 (give/reload/xp/research) | OP |

### PlaceholderAPI 佔位符
| 佔位符 | 說明 |
|---|---|
| `%techproject_title%` | 目前稱號（含色碼） |
| `%techproject_title_raw%` | 純文字稱號 |
| `%techproject_title_id%` | 目前套用的成就 ID |
| `%techproject_title_count%` | 已解鎖稱號數量 |
| `%techproject_title_total%` | 稱號總數 |
| `%techproject_level%` | 研究等級 |
| `%techproject_xp%` | 累計研究 XP |

---

## 附錄: 機器安全設定 (config.yml)

```yaml
# 礦場鑽機不可挖掘的方塊
quarry-blacklist: [BEDROCK, BARRIER, COMMAND_BLOCK, ...]

# 生物收集器不可影響的生物
mob-collector-blacklist: [PLAYER, ARMOR_STAND, VILLAGER, WANDERING_TRADER, ...]

# 安卓尊重領地保護
android-respect-protection: true
```

---

*此文檔由程式自動提取產生。最後更新: 2025 年。*
