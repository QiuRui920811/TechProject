# ⚙️ 機器全索引

> 自動產出的完整機器索引，共 **139** 台機器。
> 詳細頁：[一階](tier-1.md) / [二階](tier-2.md) / [三階](tier-3.md) / [四階](tier-4.md) / [巨構](megastructures.md) / [純電力版](electric-machines.md) / [工業擴展](industrial.md)

---

## 一階 (BASIC)（18 台）

| 中文名 | 英文 ID | Tier | 方塊 | 輸入 → 輸出 | 耗能 | 解鎖 |
|---|---|---|---|---|---|---|
| 研究台 | `research_desk` | - | ENCHANTING_TABLE | tech_book, copper_ingot →  | 0 EU/t | initial |
| 粉碎機 | `crusher` | - | BLAST_FURNACE | iron_ore, copper_ore → iron_dust, copper_dust | 5 EU/t | initial |
| 熔爐 | `furnace` | - | FURNACE | iron_dust, copper_dust → iron_ingot, copper_ingot | 10 EU/t | crusher |
| 自動農場 | `auto_farm` | - | MOSS_BLOCK | crop_seeds → crops | 3 EU/t | initial |
| 電鋸 | `electric_saw` | - | STONECUTTER | oak_planks → wood_dust | 4 EU/t | initial |
| 回收機 | `recycler` | - | SMOKER | wood_dust, paper, coal_dust → recycled_material, plastic, rust_filter | 3 EU/t | initial |
| 太陽能發電機 | `solar_generator` | - | DAYLIGHT_DETECTOR | sunlight → energy_cell | 0 EU/t | initial |
| 煤炭發電機 | `coal_generator` | - | CAMPFIRE | coal → energy_cell | 5 EU/t | initial |
| 岩漿發電機 | `lava_generator` | - | MAGMA_BLOCK | lava_bucket → energy_cell | 0 EU/t | coal_generator |
| 壓縮機 | `compressor` | - | PISTON | iron_ingot, iron_plate, tin_ingot → iron_plate, steel_plate, compressed_iron | 8 EU/t | furnace |
| 拉線機 | `wire_mill` | - | ANVIL | copper_ingot, steel_plate, tin_ingot → copper_wire, steel_wire, wiring_bundle,  | 9 EU/t | compressor |
| 焦爐 | `coke_oven` | - | BRICKS | coal, charcoal → coke, creosote_oil | 0 EU/t | initial |
| 蒸汽鍋爐 | `steam_boiler` | - | FURNACE | coal, coke, water_bucket →  | 0 EU/t | coke_oven |
| 散熱片 | `heat_sink` | - | IRON_TRAPDOOR |  →  | 0 EU/t | industrial_compressor |
| 基因定序器 | `genetic_sequencer` | - | STONECUTTER | pocket_chicken → pocket_chicken | 3 EU/t | chicken_net |
| 私人雞舍 | `private_coop` | - | COMPOSTER | pocket_chicken, pocket_chicken → pocket_chicken | 2 EU/t | genetic_sequencer |
| 激發室 | `excitation_chamber` | - | BREWING_STAND | pocket_chicken →  | 4 EU/t | private_coop |
| 方塊破壞器 | `block_breaker` | TIER1 | DROPPER |  →  | 0 EU/t | item:iron_plate&item:machine_component |

## 二階 (INTERMEDIATE)（36 台）

| 中文名 | 英文 ID | Tier | 方塊 | 輸入 → 輸出 | 耗能 | 解鎖 |
|---|---|---|---|---|---|---|
| 生質反應爐 | `bio_reactor` | - | COMPOSTER | crops → energy_cell | 0 EU/t | compressor |
| 風力渦輪機 | `wind_turbine` | - | LIGHTNING_ROD | wind → energy_cell | 0 EU/t | assembler |
| 製造機 | `assembler` | - | CRAFTER | circuit_board, steel_plate, tin_ingot → machine_component | 15 EU/t | steel_plate |
| 合金熔煉爐 | `smeltery` | - | MAGMA_BLOCK | iron_dust, coal_dust → titanium_alloy | 12 EU/t | furnace |
| 太陽能陣列 | `solar_array` | - | BLUE_STAINED_GLASS | starlight_plate → energy_cell | 0 EU/t | battery_bank |
| 電池庫 | `battery_bank` | - | REDSTONE_LAMP | energy_cell, lithium_cell, tin_plate → capacitor_bank | 2 EU/t | assembler |
| 淨化器 | `purifier` | - | CAULDRON | ore_slurry → purified_shard, coolant_cell, purifier_m | 9 EU/t | ore_washer |
| 洗礦機 | `ore_washer` | - | BARREL | iron_dust → ore_slurry | 7 EU/t | crusher |
| 生質實驗室 | `bio_lab` | - | BREWING_STAND | crops, wood_dust → bio_resin, bio_fiber | 11 EU/t | auto_farm |
| 溫室培育艙 | `greenhouse` | - | MANGROVE_LEAVES | seed_cluster, fertilizer_mix → growth_lamp, hydro_gel | 8 EU/t | auto_farm |
| 聚合壓床 | `polymer_press` | - | HONEY_BLOCK | rubber_sheet, polymer_resin → rubber_sheet, polymer_resin, treated_pla | 10 EU/t | bio_lab |
| 冷卻混合器 | `coolant_mixer` | - | CAULDRON | coolant_cell, hydro_gel → coolant_mix | 7 EU/t | purifier |
| 精煉塔 | `refinery` | - | SMOKER | bio_resin, ore_slurry → refined_oil | 10 EU/t | bio_lab&ore_washer |
| 能源節點 | `energy_node` | - | LIGHTNING_ROD | energy_cell → energy_cell | 0 EU/t | battery_bank |
| 導能線纜 | `energy_cable` | - | IRON_BARS | flux_link → flux_link | 0 EU/t | battery_bank |
| 物流節點 | `logistics_node` | - | TARGET | machine_component → machine_component | 0 EU/t | assembler |
| 物流導管 | `item_tube` | - | GLASS_PANE | pressure_tube → pressure_tube | 0 EU/t | wire_mill |
| 作物收割機 | `crop_harvester` | - | HAY_BLOCK | harvest_matrix, agri_module → crops, crop_seeds, bio_fiber | 8 EU/t | machine:auto_farm&item:harvest_matrix&item:agri_mo |
| 真空吸入口 | `vacuum_inlet` | - | LODESTONE | vacuum_core, pressure_tube → junk, crops, iron_dust, copper_dust, pla | 3 EU/t | machine:assembler&item:vacuum_core |
| 伐木機 | `tree_feller` | - | SMITHING_TABLE | logging_blade, arbor_frame → oak_log, birch_log, spruce_log, jungle_l | 12 EU/t | machine:electric_saw&item:logging_blade&item:arbor |
| 自動釣台 | `fishing_dock` | - | BARREL | bait_module, dock_frame → cod, salmon, tropical_fish, pufferfish,  | 7 EU/t | machine:auto_farm&item:bait_module&item:dock_frame |
| 倉儲匯流站 | `storage_hub` | - | CHEST | storage_crate, machine_casing, wiring_bu → machine_component, steel_plate, circuit_ | 0 EU/t | machine:assembler&item:storage_crate |
| 銷毀節點 | `trash_node` | - | MAGMA_BLOCK | machine_casing, lava_bucket →  | 0 EU/t | machine:assembler&item:machine_casing |
| 信號線 | `signal_wire` | - | CHAIN | signal_relay → signal_relay | 0 EU/t | machine:assembler&item:signal_core |
| 信號發射器 | `signal_emitter` | - | TARGET | signal_relay → signal_relay | 1 EU/t | machine:assembler&item:signal_core |
| 信號接收器 | `signal_receiver` | - | SCULK_SENSOR | signal_relay → signal_relay | 1 EU/t | machine:assembler&item:signal_core |
| 信號反轉器 | `signal_inverter` | - | OXIDIZED_COPPER_BULB | signal_relay → signal_relay | 0 EU/t | machine:assembler&item:signal_core |
| 紅石轉接器 | `redstone_adapter` | - | TARGET | signal_relay → signal_relay | 0 EU/t | machine:assembler&item:redstone_interface |
| 漏斗介面 | `hopper_interface` | - | DROPPER |  →  | 0 EU/t | machine:assembler&item:hopper_adapter |
| 溢出處理器 | `overflow_handler` | - | BARREL |  →  | 0 EU/t | machine:assembler&item:overflow_valve |
| 合金爐 | `alloy_furnace` | - | BLAST_FURNACE | copper_ingot, tin_ingot → bronze_ingot, solder_ingot, gilded_iron_ | 15 EU/t | furnace |
| 工業洗礦機 | `industrial_washer` | - | CAULDRON | iron_dust, copper_dust → purified_iron, purified_copper | 8 EU/t | crusher |
| 工業壓縮機 | `industrial_compressor` | - | PISTON | iron_ingot, carbon_dust → iron_plate, carbon_plate, carbon_rod | 12 EU/t | furnace |
| 工業窯爐 | `kiln` | - | BRICKS | clay_ball, ceramic_plate, heat_coil → ceramic_plate, thermal_ceramic | 12 EU/t | furnace |
| 絕緣壓製台 | `insulation_press` | - | LOOM | copper_wire, plastic, logic_gate, servo_ → insulated_wire, sensor_array, servo_bund | 11 EU/t | wire_mill |
| 萬用合成站 | `auto_crafter` | TIER2 | SMITHING_TABLE |  →  | 20 EU/t | machine:assembler |

## 三階 / 四階 (ADVANCED + ENDGAME)（84 台）

| 中文名 | 英文 ID | Tier | 方塊 | 輸入 → 輸出 | 耗能 | 解鎖 |
|---|---|---|---|---|---|---|
| 量子處理器 | `quantum_processor` | - | RESPAWN_ANCHOR | advanced_circuit, nano_coating → quantum_chip | 20 EU/t | advanced_assembler |
| 進階製造機 | `advanced_assembler` | - | LOOM | machine_component, advanced_circuit → nano_coating | 24 EU/t | assembler |
| 離心機 | `centrifuge` | - | HOPPER | purified_shard → ion_dust, magnetic_ring, centrifuge_roto | 18 EU/t | purifier |
| 化學反應器 | `chemical_reactor` | - | BEACON | bio_resin, coal_dust → polymer_resin, synthetic_fiber, precisio | 16 EU/t | bio_lab |
| 雷射雕刻機 | `laser_engraver` | - | END_ROD | silicon, optic_lens → laser_lens, etched_circuit, control_unit | 18 EU/t | advanced_assembler |
| 無人機艙 | `drone_bay` | - | SHULKER_BOX | drone_core, machine_casing → drone_shell, drone_frame | 18 EU/t | advanced_assembler |
| 安卓艙 | `android_bay` | - | CHISELED_BOOKSHELF | android_core, machine_casing → android_shell, android_frame, android_me | 20 EU/t | advanced_assembler |
| 安卓工作站 | `android_station` | - | CHISELED_TUFF | android_frame, android_memory_matrix → android_item_interface, android_fuel_int | 12 EU/t | android_bay |
| 安卓物品介面 | `android_item_interface` | - | BARREL | signal_relay, machine_casing →  | 4 EU/t | android_station |
| 安卓燃料介面 | `android_fuel_interface` | - | BLAST_FURNACE | emitter_node, pressure_tube →  | 4 EU/t | android_station |
| 晶體培育艙 | `crystal_growth_chamber` | - | AMETHYST_BLOCK | crystal_seed, coolant_mix → grown_crystal | 19 EU/t | coolant_mixer |
| 力場鍛造台 | `field_forge` | - | LODESTONE | field_emitter, phase_plate → field_plate | 22 EU/t | quantum_processor |
| 物質編譯器 | `matter_compiler` | - | SCULK_CATALYST | quantum_fluid, grown_crystal → matter_blob, singularity_fragment | 26 EU/t | crystal_growth_chamber&quantum_processor |
| 電動粉碎機 | `electric_crusher` | - | BLAST_FURNACE | iron_ore, copper_ore → iron_dust, copper_dust | 12 EU/t | crusher&advanced_assembler |
| 電動壓縮機 | `electric_compressor` | - | PISTON | iron_ingot, iron_plate, tin_ingot → iron_plate, steel_plate, compressed_iron | 18 EU/t | compressor&advanced_assembler |
| 電動洗礦機 | `electric_ore_washer` | - | BARREL | iron_dust → ore_slurry | 16 EU/t | ore_washer&advanced_assembler |
| 電動拉線機 | `electric_wire_mill` | - | ANVIL | copper_ingot, steel_plate, tin_ingot → copper_wire, steel_wire, wiring_bundle,  | 20 EU/t | wire_mill&advanced_assembler |
| 電動淨化器 | `electric_purifier` | - | CAULDRON | ore_slurry → purified_shard, coolant_cell, purifier_m | 22 EU/t | purifier&advanced_assembler |
| 電動離心機 | `electric_centrifuge` | - | HOPPER | purified_shard → ion_dust, magnetic_ring, centrifuge_roto | 38 EU/t | centrifuge&advanced_assembler |
| 電動生質實驗室 | `electric_bio_lab` | - | BREWING_STAND | crops, wood_dust → bio_resin, bio_fiber | 24 EU/t | bio_lab&advanced_assembler |
| 電動化學反應器 | `electric_chemical_reactor` | - | BEACON | bio_resin, coal_dust → polymer_resin, synthetic_fiber, precisio | 34 EU/t | chemical_reactor&advanced_assembler |
| 電動篩礦機 | `electric_sifter` | - | COMPOSTER | gravel, soul_sand → iron_dust, copper_dust, tin_dust, zinc_d | 10 EU/t | gold_pan&advanced_assembler |
| 採礦鑽機 | `quarry_drill` | - | BLAST_FURNACE |  →  | 24 EU/t | machine:assembler&item:drill_head&item:quarry_fram |
| 採礦鑽機二型 | `quarry_drill_mk2` | - | BLAST_FURNACE |  →  | 48 EU/t | machine:quarry_drill&item:capacitor_bank&item:cont |
| 風暴渦輪機 | `storm_turbine` | - | LIGHTNING_ROD | storm_rotor, weather_sensor → energy_cell | 0 EU/t | machine:wire_mill&item:storm_rotor&item:weather_se |
| 生物收集器 | `mob_collector` | - | SPAWNER | net_launcher, field_processor → leather, beef, white_wool, mutton, porkc | 14 EU/t | machine:advanced_assembler&item:net_launcher&item: |
| 過濾路由器 | `filter_router` | - | TARGET | filter_mesh_core, routing_chip, purifier → machine_component, steel_plate, circuit_ | 0 EU/t | machine:advanced_assembler&item:filter_mesh_core&i |
| 分流節點 | `splitter_node` | - | DROPPER | splitter_core, routing_chip, servo_motor → machine_component, steel_plate, circuit_ | 0 EU/t | machine:assembler&item:splitter_core&item:routing_ |
| 工業總線 | `industrial_bus` | - | IRON_BARS | bus_frame, routing_chip, storage_crate,  → machine_component, steel_plate, circuit_ | 0 EU/t | machine:wire_mill&item:bus_frame&item:routing_chip |
| 貨物抽取節點 | `cargo_input_node` | - | LODESTONE | routing_chip, servo_motor, storage_crate → machine_component, steel_plate, circuit_ | 2 EU/t | machine:assembler&item:routing_chip&item:servo_mot |
| 貨物管理站 | `cargo_manager` | - | LODESTONE | bus_frame, routing_chip, control_unit, s → machine_component, steel_plate, circuit_ | 5 EU/t | machine:advanced_assembler&item:bus_frame&item:con |
| 貨物輸出節點 | `cargo_output_node` | - | DROPPER | routing_chip, servo_motor, storage_crate → machine_component, steel_plate, circuit_ | 2 EU/t | machine:assembler&item:routing_chip&item:servo_mot |
| 貨物驅動器 | `cargo_motor` | - | PISTON | bus_frame, servo_motor, routing_chip → machine_component, steel_plate, circuit_ | 1 EU/t | machine:assembler&item:bus_frame&item:servo_motor& |
| 地質抽取機 | `geo_extractor` | - | BLAST_FURNACE | drill_head, precision_frame → crude_oil, rock_salt, sulfur_dust, urani | 18 EU/t | machine:quarry_drill&item:drill_head&item:precisio |
| 量子倉儲抽屜 | `cargo_drawer` | - | BARREL | storage_crate, routing_chip → storage_crate | 0 EU/t | machine:storage_hub&item:storage_crate&item:routin |
| 信號比較器 | `signal_comparator` | - | COPPER_BULB | signal_relay → signal_relay | 1 EU/t | machine:assembler&item:signal_core&item:advanced_c |
| 信號計時器 | `signal_timer` | - | CHISELED_COPPER | signal_relay → signal_relay | 1 EU/t | machine:assembler&item:signal_core&item:servo_moto |
| 信號計數器 | `signal_counter` | - | CALIBRATED_SCULK_SENSOR | signal_relay → signal_relay | 1 EU/t | machine:assembler&item:signal_core&item:advanced_c |
| 優先路由器 | `priority_router` | - | LOOM |  →  | 0 EU/t | machine:assembler&item:priority_module |
| 輪流分配器 | `round_robin` | - | DISPENSER |  →  | 0 EU/t | machine:assembler&item:distribution_gear |
| 工業離心機 | `industrial_centrifuge` | - | IRON_BLOCK | purified_iron, uranium_ore_dust → iron_dust, enriched_uranium, chromium_du | 20 EU/t | industrial_washer |
| 電解分離機 | `electrolytic_separator` | - | IRON_BLOCK | water_bucket, redstone → hydrogen, oxygen, selenium_dust, silicon | 25 EU/t | industrial_centrifuge |
| 電爐 | `electric_smelter` | - | FURNACE | tungsten_dust, chromium_dust, cobalt_dus → tungsten_ingot, chromium_ingot, cobalt_i | 30 EU/t | centrifuge |
| 高爐 | `blast_furnace` | - | NETHER_BRICKS | iron_ingot, coke, carbon_dust → steel_ingot, tungsten_ingot, carbide_dus | 40 EU/t | coke_oven |
| 工業冶煉爐 | `industrial_smeltery` | - | MAGMA_BLOCK | steel_ingot, damascus_steel_ingot, tungs → damascus_steel_ingot, hardened_alloy_ing | 60 EU/t | blast_furnace |
| 鑄造台 | `casting_table` | - | SMOOTH_STONE_SLAB | molten_iron, molten_copper, molten_gold, → iron_plate, copper_plate, steel_plate, b | 10 EU/t | industrial_smeltery |
| 柴油發電機 | `diesel_generator` | - | IRON_BLOCK | creosote_oil →  | 0 EU/t | coke_oven |
| 地熱發電機 | `geothermal_generator` | - | OBSIDIAN | lava_bucket →  | 0 EU/t | furnace |
| 水冷散熱器 | `water_cooler` | - | SEA_LANTERN | water_bucket →  | 5 EU/t | industrial_compressor |
| 低溫蒸餾器 | `cryo_distiller` | - | POWDER_SNOW_CAULDRON | coolant_mix, refined_oil, hydro_gel, coo → coolant_canister, cryo_gel, cryo_core | 18 EU/t | purifier |
| 基因切片台 | `gene_splicer` | - | BREWING_STAND | bio_resin, fertilizer_mix, crops, etched → bio_catalyst, gene_template, neural_resi | 20 EU/t | bio_lab |
| 生質合成槽 | `biosynth_vat` | - | CAULDRON | gene_template, bio_catalyst, soybean_pod → synth_meat, protein_ration | 16 EU/t | gene_splicer |
| 光子織構機 | `photon_weaver` | - | SEA_LANTERN | laser_lens, starlight_plate, reinforced_ → photon_shard, photon_plate, observatory_ | 24 EU/t | laser_engraver |
| 軌道觀測台 | `observatory` | - | LECTERN | observatory_lens, data_matrix, magnetic_ → star_chart, gravitic_bearing, vacuum_mod | 22 EU/t | quantum_processor |
| 資料封存庫 | `data_archive` | - | CHISELED_BOOKSHELF | data_core, gravitic_bearing → archive_plate | 20 EU/t | observatory |
| 紫水晶培養機 | `amethyst_grower` | TIER2 | BUDDING_AMETHYST | coolant_mix → amethyst_shard | 16 EU/t | machine:crystal_growth_chamber |
| GPS 發射器 | `gps_transmitter` | TIER2 | DAYLIGHT_DETECTOR | gps_circuit → gps_marker | 8 EU/t | item:gps_circuit&item:gps_antenna |
| GPS 控制塔 | `gps_control_tower` | TIER2 | END_ROD | gps_marker, signal_relay → satellite_module | 14 EU/t | machine:gps_transmitter&item:signal_relay |
| 地質掃描器 | `geo_scanner` | TIER2 | OBSERVER | geo_scanner_probe, gps_marker → data_matrix | 12 EU/t | item:geo_scanner_probe&item:gps_marker |
| 加壓室 | `pressure_chamber` | TIER3 | IRON_BLOCK | carbon_dust, carbide_dust → synthetic_diamond, carbide_ingot | 50 EU/t | industrial_smeltery |
| 燃氣渦輪 | `gas_turbine` | TIER3 | IRON_BLOCK | hydrogen →  | 0 EU/t | electrolytic_separator |
| 冷卻液循環器 | `coolant_circulator` | TIER3 | DIAMOND_BLOCK | coolant_cell →  | 15 EU/t | industrial_washer |
| 低溫冷卻塔 | `cryo_tower` | TIER3 | PACKED_ICE | coolant_cell →  | 20 EU/t | coolant_circulator |
| 核反應堆 | `nuclear_reactor` | TIER3 | RESPAWN_ANCHOR | uranium_rod, reactor_coolant → spent_fuel_rod | 0 EU/t | item:uranium_rod&item:reactor_coolant |
| GPS 網路節點 | `gps_network_node` | TIER3 | SEA_LANTERN | satellite_module, data_core → teleport_matrix | 20 EU/t | machine:gps_control_tower&item:data_core |
| 傳送面板 | `teleport_pad` | TIER3 | CONDUIT | teleport_matrix →  | 28 EU/t | machine:gps_network_node&item:teleport_matrix |
| 聚變反應爐 | `fusion_reactor` | TIER4 | CRYING_OBSIDIAN | fusion_mesh, stability_core → fusion_core | 28 EU/t | field_forge&crystal_growth_chamber |
| 採礦鑽機三型 | `quarry_drill_mk3` | TIER4 | BLAST_FURNACE |  →  | 96 EU/t | machine:quarry_drill_mk2&item:fusion_core&item:qua |
| 等離子精煉爐 | `plasma_refiner` | TIER4 | BLAST_FURNACE | ion_dust, refined_oil, titanium_alloy, a → plasma_dust, plasma_ingot, reactor_latti | 28 EU/t | fusion_reactor |
| 行星測繪信標 | `survey_beacon` | TIER4 | LODESTONE | survey_chip, star_chart, data_core → survey_data | 24 EU/t | observatory&item:radiation_filter |
| 遺物解析儀 | `relic_analyzer` | TIER4 | CALIBRATED_SCULK_SENSOR | survey_data, planetary_relic, cryon_reli → ancient_signal | 26 EU/t | machine:survey_beacon&(item:planetary_relic|item:c |
| 異星精煉槽 | `xeno_refinery` | TIER4 | SMITHING_TABLE | ancient_signal, irradiated_shard, cryoni → xeno_alloy, radiation_mesh, thermal_lini | 28 EU/t | machine:relic_analyzer&item:cryonite_crystal&item: |
| 行星鍛造台 | `planetary_forge` | TIER4 | NETHERITE_BLOCK | xeno_alloy, seal_fabric, exo_frame, radi → anti_radiation_chestplate, thermal_chest | 30 EU/t | machine:xeno_refinery&item:xeno_alloy&stat:planet_ |
| 行星星門 | `planetary_gate` | TIER4 | CONDUIT | survey_data, gravitic_bearing, quantum_f → survey_data | 12 EU/t | machine:survey_beacon&item:survey_data&item:gravit |
| 行星採集機 | `planetary_harvester` | TIER4 | OBSERVER | harvest_unit, sensor_array, servo_bundle → irradiated_shard, cryonite_crystal, void | 20 EU/t | machine:survey_beacon&item:irradiated_shard&stat:p |
| 軌道中繼站 | `orbital_relay_station` | TIER4 | BEACON | orbital_relay_unit, fusion_core → satellite_module | 32 EU/t | machine:gps_network_node&item:fusion_core&item:orb |
| 星鋼鑄造廠 | `starsteel_foundry` | TIER4 | SMITHING_TABLE | stellar_alloy, neutronium_ingot, orbit_f → starsteel_ingot, starsteel_frame | 38 EU/t | stellar_forge |
| 真空工藝艙 | `vacuum_chamber` | TIER4 | BLACK_STAINED_GLASS | stellar_glass, void_crystal, graviton_le → vacuum_glass, harmonic_crystal, celestia | 34 EU/t | void_beacon |
| 曲率裝配機 | `warp_assembler` | TIER4 | ENDER_CHEST | antimatter_cell, chrono_thread, magnetic → warp_cell, warp_coil, astral_circuit | 42 EU/t | chrono_engine |
| 事件視界鍛台 | `event_horizon_smith` | TIER4 | NETHERITE_BLOCK | singularity_core, entropy_plate, dark_ma → event_horizon_plate, singularity_casing | 44 EU/t | singularity_press |
| 連續體車床 | `continuum_lathe` | TIER4 | STONECUTTER | chrono_core, reactor_lattice, vacuum_gla → chrono_lattice, continuum_shell, aurora_ | 40 EU/t | entropy_chamber |
| 遺產掃描儀 | `relic_scanner` | TIER4 | CARTOGRAPHY_TABLE | star_chart, void_beacon_core, data_core, → relic_data, archive_core | 30 EU/t | data_archive |
| 錨定鍛造台 | `anchor_forge` | TIER4 | RESPAWN_ANCHOR | singularity_casing, void_beacon_core, wa → dimension_anchor, cluster_manifold | 46 EU/t | void_beacon |
| 巔峰鍛成儀 | `apex_forge` | TIER4 | ENCHANTING_TABLE | cosmic_matrix, archive_core, continuum_s → omega_matrix, apex_core, omega_archive | 52 EU/t | omega_fabricator |

## 特殊 (SPECIAL)（1 台）

| 中文名 | 英文 ID | Tier | 方塊 | 輸入 → 輸出 | 耗能 | 解鎖 |
|---|---|---|---|---|---|---|
| 異界傳送門 | `otherworld_portal` | TIER4 | END_PORTAL_FRAME | mana_meteorite →  | 0 EU/t | item:mana_meteorite&machine:planetary_gate |
