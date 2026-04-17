# TechMC 全 YAML 配方與機器研究報告

---

## TASK 1：所有機器定義（依類別分組）

### BASIC（基礎）— 共 11 台

| 機器 ID | 來源檔案 | Tier |
|---------|----------|------|
| research_desk | core | — |
| crusher | core | — |
| furnace | core | — |
| auto_farm | core | — |
| electric_saw | core | — |
| recycler | core | — |
| solar_generator | core | — |
| coal_generator | core | — |
| genetic_sequencer | chickens | — |
| private_coop | chickens | — |
| excitation_chamber | chickens | — |

### INTERMEDIATE（中階）— 共 25 台

| 機器 ID | 來源檔案 | Tier |
|---------|----------|------|
| compressor | core | — |
| assembler | core | — |
| smeltery | core | — |
| solar_array | core | — |
| battery_bank | core | — |
| purifier | core | — |
| ore_washer | core | — |
| bio_lab | core | — |
| greenhouse | core | — |
| polymer_press | core | — |
| wire_mill | core | — |
| coolant_mixer | core | — |
| refinery | core | — |
| energy_node | core | — |
| energy_cable | core | — |
| logistics_node | core | — |
| item_tube | core | — |
| kiln | expansion | — |
| insulation_press | expansion | — |
| crop_harvester | systems | — |
| vacuum_inlet | systems | — |
| tree_feller | systems | — |
| fishing_dock | systems | — |
| storage_hub | systems | — |
| trash_node | systems | — |

### ADVANCED（進階）— 共 56 台

| 機器 ID | 來源檔案 | Tier |
|---------|----------|------|
| quantum_processor | core | — |
| advanced_assembler | core | — |
| centrifuge | core | — |
| chemical_reactor | core | — |
| laser_engraver | core | — |
| drone_bay | core | — |
| android_bay | core | — |
| android_station | core | — |
| android_item_interface | core | — |
| android_fuel_interface | core | — |
| crystal_growth_chamber | core | — |
| field_forge | core | — |
| matter_compiler | core | — |
| electric_crusher | core | — |
| electric_compressor | core | — |
| electric_ore_washer | core | — |
| electric_wire_mill | core | — |
| electric_purifier | core | — |
| electric_centrifuge | core | — |
| electric_bio_lab | core | — |
| electric_chemical_reactor | core | — |
| electric_sifter | core | — |
| fusion_reactor | core | TIER4 |
| cryo_distiller | expansion | — |
| gene_splicer | expansion | — |
| biosynth_vat | expansion | — |
| photon_weaver | expansion | — |
| observatory | expansion | — |
| data_archive | expansion | — |
| plasma_refiner | expansion | TIER4 |
| survey_beacon | expansion | TIER4 |
| relic_analyzer | expansion | TIER4 |
| xeno_refinery | expansion | TIER4 |
| planetary_forge | expansion | TIER4 |
| planetary_gate | expansion | TIER4 |
| planetary_harvester | expansion | TIER4 |
| quarry_drill | systems | — |
| quarry_drill_mk2 | systems | — |
| quarry_drill_mk3 | systems | TIER4 |
| storm_turbine | systems | — |
| mob_collector | systems | — |
| filter_router | systems | — |
| splitter_node | systems | — |
| industrial_bus | systems | — |
| cargo_input_node | systems | — |
| cargo_manager | systems | — |
| cargo_output_node | systems | — |
| cargo_motor | systems | — |
| void_extractor | main | TIER4 |
| dark_matter_loom | main | TIER4 |
| graviton_stabilizer | main | TIER4 |
| singularity_press | main | TIER4 |
| antimatter_synthesizer | main | TIER4 |
| stellar_forge | main | TIER4 |
| orbital_printer | main | TIER4 |
| chrono_engine | main | TIER4 |
| void_beacon | main | TIER4 |
| nanite_foundry | main | TIER4 |
| entropy_chamber | main | TIER4 |
| celestial_assembler | main | TIER4 |
| omega_fabricator | main | TIER4 |
| starsteel_foundry | megastructures | TIER4 |
| vacuum_chamber | megastructures | TIER4 |
| warp_assembler | megastructures | TIER4 |
| event_horizon_smith | megastructures | TIER4 |
| continuum_lathe | megastructures | TIER4 |
| relic_scanner | megastructures | TIER4 |
| anchor_forge | megastructures | TIER4 |
| apex_forge | megastructures | TIER4 |

**機器總計：BASIC 11 + INTERMEDIATE 25 + ADVANCED 56 = 92 台**

---

## TASK 2：所有配方一覽

### 📄 tech-content-core.yml（核心）

| # | Recipe ID | machine | inputs | input-amounts | output | count |
|---|-----------|---------|--------|---------------|--------|-------|
| 1 | crusher_iron_ore | crusher | iron_ore | [2] | iron_dust | 2 |
| 2 | crusher_copper_ore | crusher | copper_ore | [2] | copper_dust | 2 |
| 3 | crusher_quartz | crusher | quartz | [2] | silicon | 2 |
| 4 | crusher_raw_iron | crusher | raw_iron | [2] | iron_dust | 2 |
| 5 | crusher_raw_copper | crusher | raw_copper | [2] | copper_dust | 2 |
| 6 | crusher_coal | crusher | coal | [2] | coal_dust | 2 |
| 7 | compressor_iron_plate | compressor | iron_ingot | [3] | iron_plate | 1 |
| 8 | furnace_iron_dust | furnace | iron_dust | [2] | iron_ingot | 1 |
| 9 | furnace_copper_dust | furnace | copper_dust | [2] | copper_ingot | 1 |
| 10 | crusher_iron_ingot | crusher | iron_ingot | [1] | iron_dust | 1 |
| 11 | crusher_copper_ingot | crusher | copper_ingot | [1] | copper_dust | 1 |
| 12 | furnace_glass | furnace | glass | [2] | glass_panel | 1 |
| 13 | furnace_reinforced_glass | furnace | glass_panel, carbon_mesh | [2,2] | reinforced_glass | 1 |
| 14 | furnace_heat_coil | furnace | copper_wire, blaze_powder | [2,2] | heat_coil | 1 |
| 15 | furnace_vacuum_tube | furnace | glass_panel, copper_wire | [2,2] | vacuum_tube | 1 |
| 16 | furnace_optic_lens | furnace | glass_panel, silicon | [2,2] | optic_lens | 1 |
| 17 | saw_wood | electric_saw | oak_planks | [2] | wood_dust | 1 |
| 18 | saw_fiber | electric_saw | bamboo | [2] | fiber_mesh | 1 |
| 19 | auto_farm_seed_cluster | auto_farm | crop_seeds, bone_meal | [3,2] | seed_cluster | 1 |
| 20 | auto_farm_crop | auto_farm | crop_seeds | [3] | crops | 1 |
| 21 | auto_farm_fertilizer | auto_farm | crops, bone_meal | [3,2] | fertilizer_mix | 1 |
| 22 | auto_farm_agri | auto_farm | crops, hydro_gel | [3,2] | agri_module | 1 |
| 23 | assembler_harvest_unit | assembler | servo_motor, iron_plate | [3,2] | harvest_unit | 1 |
| 24 | recycler_material | recycler | wood_dust | [2] | recycled_material | 1 |
| 25 | recycler_plastic | recycler | wood_dust, recycled_material | [2,2] | plastic | 1 |
| 26 | recycler_filter | recycler | paper, coal_dust | [2,2] | rust_filter | 1 |
| 27 | compressor_steel | compressor | iron_plate, coal_dust | [3,2] | steel_plate | 1 |
| 28 | compressor_compressed_iron | compressor | iron_plate, iron_plate | [3,2] | compressed_iron_bundle | 1 |
| 29 | compressor_mesh | compressor | coal_dust | [3] | carbon_mesh | 1 |
| 30 | compressor_dense_carbon | compressor | carbon_mesh, carbon_mesh | [3,2] | dense_carbon_block | 1 |
| 31 | compressor_pressure_tube | compressor | copper_ingot, glass_panel | [3,2] | pressure_tube | 1 |
| 32 | compressor_composite | compressor | steel_plate, plastic | [3,2] | composite_panel | 1 |
| 33 | assembler_component | assembler | compressed_iron_bundle, circuit_board, bronze_gear | [2,2,2] | machine_component | 1 |
| 34 | assembler_precision_frame | assembler | machine_component, dense_carbon_block | [3,2] | precision_frame | 1 |
| 35 | assembler_circuit | assembler | copper_ingot, silicon, solder_alloy | [2,2,2] | circuit_board | 1 |
| 36 | assembler_advanced_circuit | assembler | circuit_board, logic_gate, wiring_bundle, brass_ingot | [2,2,1,1] | advanced_circuit | 1 |
| 37 | assembler_tech_wrench | assembler | iron_plate, copper_ingot | [3,2] | tech_wrench | 1 |
| 38 | assembler_logistics_wrench | assembler | tech_wrench, logic_gate | [3,2] | logistics_wrench | 1 |
| 39 | assembler_energy_cell | assembler | copper_ingot, redstone | [3,2] | energy_cell | 1 |
| 40 | assembler_logic_gate | assembler | copper_wire, silicon | [3,2] | logic_gate | 1 |
| 41 | assembler_servo | assembler | steel_wire, machine_component | [3,2] | servo_motor | 1 |
| 42 | assembler_machine_casing | assembler | precision_frame, glass_panel | [3,2] | machine_casing | 1 |
| 43 | assembler_signal_relay | assembler | logic_gate, copper_wire | [3,2] | signal_relay | 1 |
| 44 | assembler_bronze_gear | assembler | bronze_ingot, iron_plate | [3,2] | bronze_gear | 1 |
| 45 | smeltery_alloy | smeltery | iron_dust, coal_dust | [3,2] | titanium_alloy | 1 |
| 46 | smeltery_frame | smeltery | titanium_alloy, steel_wire | [3,2] | alloy_frame | 1 |
| 47 | smeltery_shell | smeltery | heat_coil, steel_plate | [3,2] | thermal_shell | 1 |
| 48 | advanced_nano | advanced_assembler | precision_frame, advanced_circuit | [3,2] | nano_coating | 1 |
| 49 | advanced_micro | advanced_assembler | advanced_circuit, dense_carbon_block, wiring_bundle | [2,2,2] | micro_processor | 1 |
| 50 | advanced_reactor_core | advanced_assembler | control_unit, heat_coil | [3,2] | reactor_core | 1 |
| 51 | advanced_drone_core | advanced_assembler | control_unit, emitter_node | [3,2] | drone_core | 1 |
| 52 | advanced_android_core | advanced_assembler | control_unit, servo_motor | [3,2] | android_core | 1 |
| 53 | solar_plate | solar_array | glass_panel, energy_cell | [3,2] | starlight_plate | 1 |
| 54 | solar_grid | solar_array | starlight_plate, copper_wire | [3,2] | grid_module | 1 |
| 55 | battery_lithium | battery_bank | energy_cell, copper_ingot | [3,2] | lithium_cell | 1 |
| 56 | battery_emitter | battery_bank | energy_cell, logic_gate | [3,2] | emitter_node | 1 |
| 57 | battery_capacitor | battery_bank | lithium_cell, emitter_node | [3,2] | capacitor_bank | 1 |
| 58 | washer_slurry | ore_washer | iron_dust | [3] | ore_slurry | 1 |
| 59 | washer_biomass | ore_washer | crops | [3] | crushed_biomass | 1 |
| 60 | purifier_shard | purifier | ore_slurry | [3] | purified_shard | 1 |
| 61 | purifier_coolant | purifier | snowball, glass_panel, purifier_membrane | [2,2,2] | coolant_cell | 1 |
| 62 | purifier_membrane_recipe | purifier | rust_filter, fiber_mesh | [2,2] | purifier_membrane | 1 |
| 63 | centrifuge_ion | centrifuge | purified_shard | [3] | ion_dust | 1 |
| 64 | centrifuge_rotor_recipe | centrifuge | ion_dust, alloy_frame | [3,2] | centrifuge_rotor | 1 |
| 65 | centrifuge_ring | centrifuge | ion_dust, steel_wire, centrifuge_rotor | [3,2,2] | magnetic_ring | 1 |
| 66 | bio_resin_recipe | bio_lab | crops, wood_dust | [3,2] | bio_resin | 1 |
| 67 | bio_fiber_recipe | bio_lab | crushed_biomass, fiber_mesh | [3,2] | bio_fiber | 1 |
| 68 | greenhouse_growth_lamp | greenhouse | glass_panel, agri_module | [3,2] | growth_lamp | 1 |
| 69 | greenhouse_hydro | greenhouse | bio_resin, crops | [3,2] | hydro_gel | 1 |
| 70 | polymer_rubber | polymer_press | bio_resin | [3] | rubber_sheet | 1 |
| 71 | polymer_treated | polymer_press | plastic, polymer_resin | [3,2] | treated_plastic | 1 |
| 72 | wire_mill_copper | wire_mill | copper_ingot | [3] | copper_wire | 1 |
| 73 | wire_mill_steel | wire_mill | steel_plate | [3] | steel_wire | 1 |
| 74 | wire_mill_bundle | wire_mill | copper_wire, steel_wire | [3,2] | wiring_bundle | 1 |
| 75 | chemical_polymer | polymer_press | bio_resin, coal_dust | [3,2] | polymer_resin | 1 |
| 76 | chemical_synthetic | chemical_reactor | polymer_resin, bio_fiber | [3,2] | synthetic_fiber | 1 |
| 77 | chemical_nozzle | chemical_reactor | pressure_tube, steel_wire | [3,2] | precision_nozzle | 1 |
| 78 | coolant_mix_recipe | coolant_mixer | coolant_cell, hydro_gel | [3,2] | coolant_mix | 1 |
| 79 | refinery_oil | refinery | bio_resin, ore_slurry | [3,2] | refined_oil | 1 |
| 80 | laser_lens_recipe | laser_engraver | optic_lens, reinforced_glass | [3,2] | laser_lens | 1 |
| 81 | laser_etched | laser_engraver | laser_lens, advanced_circuit | [3,2] | etched_circuit | 1 |
| 82 | laser_control | laser_engraver | etched_circuit, logic_gate | [3,2] | control_unit | 1 |
| 83 | drone_shell_recipe | drone_bay | treated_plastic, machine_casing | [3,2] | drone_shell | 1 |
| 84 | drone_frame_recipe | drone_bay | drone_shell, synthetic_fiber | [3,2] | drone_frame | 1 |
| 85 | android_shell_recipe | android_bay | treated_plastic, machine_casing | [3,2] | android_shell | 1 |
| 86 | android_frame_recipe | android_bay | android_shell, synthetic_fiber | [3,2] | android_frame | 1 |
| 87 | android_memory_matrix_recipe | android_bay | android_core, data_matrix | [3,2] | android_memory_matrix | 1 |
| 88 | assembler_android_harvest_script | assembler | agri_module, logic_gate | [3,2] | android_harvest_script | 1 |
| 89 | assembler_android_logging_script | assembler | harvest_unit, servo_motor | [3,2] | android_logging_script | 1 |
| 90 | assembler_android_salvage_script | assembler | signal_relay, emitter_node | [3,2] | android_salvage_script | 1 |
| 91 | advanced_android_overclock_card | advanced_assembler | speed_upgrade, android_memory_matrix | [3,2] | android_overclock_card | 1 |
| 92 | advanced_android_efficiency_card | advanced_assembler | efficiency_upgrade, android_memory_matrix | [3,2] | android_efficiency_card | 1 |
| 93 | quantum_android_expansion_card | quantum_processor | range_upgrade, android_memory_matrix | [3,2] | android_expansion_card | 1 |
| 94 | quantum_android_planet_script | quantum_processor | quantum_frame, field_emitter | [3,2] | android_planet_script | 1 |
| 95 | advanced_android_station | advanced_assembler | android_frame, android_memory_matrix | [3,2] | android_station | 1 |
| 96 | advanced_plasma_cell | advanced_assembler | heat_coil, ion_dust | [3,2] | plasma_cell | 1 |
| 97 | assembler_android_item_interface | assembler | signal_relay, chest | [3,2] | android_item_interface | 1 |
| 98 | assembler_android_fuel_interface | assembler | emitter_node, pressure_tube | [3,2] | android_fuel_interface | 1 |
| 99 | quantum_chip_recipe | quantum_processor | advanced_circuit, nano_coating | [3,2] | quantum_chip | 1 |
| 100 | quantum_fluid_recipe | quantum_processor | ion_dust, flux_link | [3,2] | quantum_fluid | 1 |
| 101 | quantum_frame_recipe | quantum_processor | flux_link, reinforced_glass | [3,2] | quantum_frame | 1 |
| 102 | quantum_data_matrix | quantum_processor | control_unit, quantum_chip | [3,2] | data_matrix | 1 |
| 103 | quantum_crystal_matrix | quantum_processor | grown_crystal, data_matrix | [3,2] | crystal_matrix | 1 |
| 104 | quantum_flux_link | quantum_processor | magnetic_ring, energy_cell | [3,2] | flux_link | 1 |
| 105 | quantum_phase_plate | quantum_processor | quantum_frame, grown_crystal | [3,2] | phase_plate | 1 |
| 106 | quantum_field_emitter | quantum_processor | flux_link, control_unit | [3,2] | field_emitter | 1 |
| 107 | crystal_seed_recipe | crystal_growth_chamber | amethyst_shard, coolant_mix | [3,2] | crystal_seed | 1 |
| 108 | grown_crystal_recipe | crystal_growth_chamber | crystal_seed, quantum_fluid | [3,2] | grown_crystal | 1 |
| 109 | field_plate_recipe | field_forge | field_emitter, phase_plate | [3,2] | field_plate | 1 |
| 110 | fusion_mesh_recipe | fusion_reactor | thermal_shell, field_plate | [3,2] | fusion_mesh | 1 |
| 111 | stability_core_recipe | fusion_reactor | magnetic_ring, reactor_core | [3,2] | stability_core | 1 |
| 112 | fusion_core_recipe | fusion_reactor | fusion_mesh, stability_core | [3,2] | fusion_core | 1 |
| 113 | matter_blob_recipe | matter_compiler | quantum_fluid, grown_crystal | [3,2] | matter_blob | 1 |
| 114 | singularity_fragment_recipe | matter_compiler | matter_blob, fusion_core | [3,2] | singularity_fragment | 1 |
| 115 | advanced_speed_upgrade | advanced_assembler | servo_motor, micro_processor | [3,2] | speed_upgrade | 1 |
| 116 | advanced_efficiency_upgrade | advanced_assembler | nano_coating, capacitor_bank | [3,2] | efficiency_upgrade | 1 |
| 117 | advanced_stack_upgrade | advanced_assembler | machine_casing, composite_panel | [3,2] | stack_upgrade | 1 |
| 118 | quantum_range_upgrade | quantum_processor | flux_link, sensor_array | [3,2] | range_upgrade | 1 |
| 119 | assembler_redstone_control | assembler | circuit_board, redstone | [3,2] | redstone_control | 1 |
| 120 | assembler_energy_node | assembler | emitter_node, machine_casing | [3,2] | energy_node | 1 |
| 121 | assembler_energy_cable | assembler | copper_wire, iron_plate | [3,2] | energy_cable | 1 |
| 122 | assembler_logistics_node | assembler | signal_relay, machine_component | [3,2] | logistics_node | 1 |
| 123 | wire_item_tube | wire_mill | pressure_tube, steel_wire | [3,2] | item_tube | 1 |
| 124 | assembler_auto_processing_card | advanced_assembler | advanced_circuit, machine_component, speed_upgrade | [2,2,2] | auto_processing_card | 1 |
| 125 | assembler_electric_crusher | advanced_assembler | crusher, auto_processing_card, wiring_bundle, servo_motor, steel_plate | [2,2,1,1,1] | electric_crusher | 1 |
| 126 | assembler_electric_compressor | advanced_assembler | compressor, auto_processing_card, servo_motor, pressure_tube, steel_plate | [2,2,1,1,1] | electric_compressor | 1 |
| 127 | assembler_electric_ore_washer | advanced_assembler | ore_washer, auto_processing_card, pressure_tube, coolant_cell, alloy_frame | [2,2,1,1,1] | electric_ore_washer | 1 |
| 128 | assembler_electric_wire_mill | advanced_assembler | wire_mill, auto_processing_card, servo_motor, wiring_bundle, bronze_gear | [2,2,1,1,1] | electric_wire_mill | 1 |
| 129 | assembler_electric_purifier | advanced_assembler | purifier, auto_processing_card, coolant_cell, precision_nozzle, alloy_frame | [2,2,1,1,1] | electric_purifier | 1 |
| 130 | assembler_electric_centrifuge | advanced_assembler | centrifuge, auto_processing_card, magnetic_ring, servo_motor, energy_cell | [2,2,1,1,1] | electric_centrifuge | 1 |
| 131 | assembler_electric_bio_lab | advanced_assembler | bio_lab, auto_processing_card, bio_fiber, coolant_cell, circuit_board | [2,2,1,1,1] | electric_bio_lab | 1 |
| 132 | assembler_electric_chemical_reactor | advanced_assembler | chemical_reactor, auto_processing_card, precision_nozzle, coolant_cell, energy_cell | [2,2,1,1,1] | electric_chemical_reactor | 1 |
| 133 | assembler_electric_sifter | advanced_assembler | gold_pan, auto_processing_card, bronze_gear, wiring_bundle, alloy_frame | [2,2,1,1,1] | electric_sifter | 1 |
| 134 | electric_crusher_iron_ore | electric_crusher | iron_ore | [2] | iron_dust | 2 |
| 135 | electric_crusher_copper_ore | electric_crusher | copper_ore | [2] | copper_dust | 2 |
| 136 | electric_crusher_quartz | electric_crusher | quartz | [2] | silicon | 2 |
| 137 | electric_crusher_raw_iron | electric_crusher | raw_iron | [2] | iron_dust | 2 |
| 138 | electric_crusher_raw_copper | electric_crusher | raw_copper | [2] | copper_dust | 2 |
| 139 | electric_crusher_coal | electric_crusher | coal | [2] | coal_dust | 2 |
| 140 | electric_compressor_iron_plate | electric_compressor | iron_ingot | [3] | iron_plate | 1 |
| 141 | electric_compressor_steel_plate | electric_compressor | iron_plate, coal_dust | [3,2] | steel_plate | 1 |
| 142 | electric_compressor_iron_bundle | electric_compressor | iron_plate, iron_plate | [3,2] | compressed_iron_bundle | 1 |
| 143 | electric_compressor_carbon | electric_compressor | coal_dust | [3] | carbon_mesh | 1 |
| 144 | electric_compressor_dense_carbon | electric_compressor | carbon_mesh, carbon_mesh | [3,2] | dense_carbon_block | 1 |
| 145 | electric_compressor_pressure_tube | electric_compressor | copper_ingot, glass_panel | [3,2] | pressure_tube | 1 |
| 146 | electric_compressor_composite | electric_compressor | steel_plate, plastic | [3,2] | composite_panel | 1 |
| 147 | electric_ore_washer_slurry | electric_ore_washer | iron_dust | [3] | ore_slurry | 1 |
| 148 | electric_ore_washer_biomass | electric_ore_washer | crops | [3] | crushed_biomass | 1 |
| 149 | electric_wire_mill_copper | electric_wire_mill | copper_ingot | [3] | copper_wire | 1 |
| 150 | electric_wire_mill_steel | electric_wire_mill | steel_plate | [3] | steel_wire | 1 |
| 151 | electric_wire_mill_bundle | electric_wire_mill | copper_wire, steel_wire | [3,2] | wiring_bundle | 1 |
| 152 | electric_wire_mill_item_tube | electric_wire_mill | pressure_tube, steel_wire | [3,2] | item_tube | 1 |
| 153 | electric_purifier_shard | electric_purifier | ore_slurry | [3] | purified_shard | 1 |
| 154 | electric_purifier_coolant | electric_purifier | snowball, glass_panel, purifier_membrane | [2,2,2] | coolant_cell | 1 |
| 155 | electric_purifier_membrane | electric_purifier | rust_filter, fiber_mesh | [2,2] | purifier_membrane | 1 |
| 156 | electric_centrifuge_ion | electric_centrifuge | purified_shard | [3] | ion_dust | 1 |
| 157 | electric_centrifuge_rotor | electric_centrifuge | ion_dust, alloy_frame | [3,2] | centrifuge_rotor | 1 |
| 158 | electric_centrifuge_ring | electric_centrifuge | ion_dust, steel_wire, centrifuge_rotor | [3,2,2] | magnetic_ring | 1 |
| 159 | electric_bio_lab_resin | electric_bio_lab | crops, wood_dust | [3,2] | bio_resin | 1 |
| 160 | electric_bio_lab_fiber | electric_bio_lab | crushed_biomass, fiber_mesh | [3,2] | bio_fiber | 1 |
| 161 | electric_chemical_polymer | electric_chemical_reactor | bio_resin, coal_dust | [3,2] | polymer_resin | 1 |
| 162 | electric_chemical_fiber | electric_chemical_reactor | polymer_resin, bio_fiber | [3,2] | synthetic_fiber | 1 |
| 163 | electric_chemical_nozzle | electric_chemical_reactor | pressure_tube, steel_wire | [3,2] | precision_nozzle | 1 |
| 164 | electric_sifter_gravel_iron | electric_sifter | gravel | [2] | iron_dust | 1 |
| 165 | electric_sifter_gravel_copper | electric_sifter | gravel | [2] | copper_dust | 1 |
| 166 | electric_sifter_gravel_tin | electric_sifter | gravel | [2] | tin_dust | 1 |
| 167 | electric_sifter_gravel_zinc | electric_sifter | gravel | [2] | zinc_dust | 1 |
| 168 | electric_sifter_gravel_sifted | electric_sifter | gravel | [2] | sifted_ore | 1 |
| 169 | electric_sifter_gravel_silicon | electric_sifter | gravel | [2] | silicon | 1 |
| 170 | electric_sifter_soul_lead | electric_sifter | soul_sand | [2] | lead_dust | 1 |
| 171 | electric_sifter_soul_sifted | electric_sifter | soul_sand | [2] | sifted_ore | 1 |
| 172 | electric_sifter_soul_gold | electric_sifter | soul_sand | [2] | gold_nugget | 1 |
| 173 | electric_sifter_soul_quartz | electric_sifter | soul_sand | [2] | quartz | 1 |
| 174 | electric_sifter_soul_zinc | electric_sifter | soul_sand | [2] | zinc_dust | 1 |
| 175 | electric_sifter_soul_iron | electric_sifter | soul_sand | [2] | iron_dust | 1 |
| 176 | smeltery_bronze | smeltery | copper_dust, tin_dust | [3,2] | bronze_ingot | 1 |
| 177 | smeltery_solder | smeltery | lead_dust, zinc_dust | [3,2] | solder_alloy | 1 |
| 178 | smeltery_brass | smeltery | copper_dust, zinc_dust | [3,2] | brass_ingot | 1 |
| 179 | furnace_tin_dust | furnace | tin_dust | [2] | tin_ingot | 1 |
| 180 | furnace_lead_dust | furnace | lead_dust | [2] | lead_ingot | 1 |
| 181 | furnace_zinc_dust | furnace | zinc_dust | [2] | zinc_ingot | 1 |
| 182 | furnace_bronze_dust | furnace | bronze_dust | [2] | bronze_ingot | 1 |
| 183 | furnace_solder_alloy_dust | furnace | solder_alloy_dust | [2] | solder_alloy | 1 |
| 184 | furnace_brass_dust | furnace | brass_dust | [2] | brass_ingot | 1 |
| 185 | crusher_tin_ingot | crusher | tin_ingot | [1] | tin_dust | 1 |
| 186 | crusher_lead_ingot | crusher | lead_ingot | [1] | lead_dust | 1 |
| 187 | crusher_zinc_ingot | crusher | zinc_ingot | [1] | zinc_dust | 1 |
| 188 | crusher_bronze_ingot | crusher | bronze_ingot | [1] | bronze_dust | 1 |
| 189 | crusher_solder_alloy | crusher | solder_alloy | [1] | solder_alloy_dust | 1 |
| 190 | crusher_brass_ingot | crusher | brass_ingot | [1] | brass_dust | 1 |
| 191 | furnace_bronze | furnace | copper_dust, tin_dust | [2,2] | bronze_ingot | 1 |
| 192 | furnace_sifted_ore | furnace | sifted_ore | [2] | iron_ingot | 1 |

**core 配方小計：192 筆**

---

### 📄 tech-content-expansion.yml（擴展）

| # | Recipe ID | machine | inputs | input-amounts | output | count |
|---|-----------|---------|--------|---------------|--------|-------|
| 1 | kiln_ceramic_plate | kiln | clay_ball | [3] | ceramic_plate | 1 |
| 2 | kiln_thermal_ceramic | kiln | ceramic_plate, heat_coil | [3,2] | thermal_ceramic | 1 |
| 3 | insulation_wire_recipe | insulation_press | copper_wire, plastic | [3,2] | insulated_wire | 1 |
| 4 | insulation_sensor_array | insulation_press | insulated_wire, logic_gate | [3,2] | sensor_array | 1 |
| 5 | insulation_servo_bundle | insulation_press | servo_motor, insulated_wire | [3,2] | servo_bundle | 1 |
| 6 | cryo_canister_recipe | cryo_distiller | coolant_mix, refined_oil | [3,2] | coolant_canister | 1 |
| 7 | cryo_gel_recipe | cryo_distiller | hydro_gel, coolant_cell | [3,2] | cryo_gel | 1 |
| 8 | cryo_core_recipe | cryo_distiller | coolant_canister, grown_crystal | [3,2] | cryo_core | 1 |
| 9 | gene_bio_catalyst_recipe | gene_splicer | bio_resin, fertilizer_mix | [3,2] | bio_catalyst | 1 |
| 10 | gene_template_recipe | gene_splicer | crops, etched_circuit | [3,2] | gene_template | 1 |
| 11 | gene_neural_resin_recipe | gene_splicer | bio_resin, synthetic_fiber | [3,2] | neural_resin | 1 |
| 12 | gene_splicer_blade_recipe | gene_splicer | steel_plate, precision_nozzle | [3,2] | splicer_blade | 1 |
| 13 | biosynth_meat_recipe | biosynth_vat | gene_template, bio_catalyst | [3,2] | synth_meat | 1 |
| 14 | auto_farm_soybean_seed | auto_farm | crop_seeds, fertilizer_mix | [3,2] | soybean_seeds | 1 |
| 15 | greenhouse_spiceberry_seed | greenhouse | seed_cluster, fertilizer_mix | [3,2] | spiceberry_seeds | 1 |
| 16 | greenhouse_tea_leaf_seed | greenhouse | seed_cluster, hydro_gel | [3,2] | tea_leaf_seeds | 1 |
| 17 | bio_lab_nutrition_bar | bio_lab | soybean_pods, spiceberry | [3,2] | nutrition_bar | 1 |
| 18 | biosynth_protein_ration | biosynth_vat | synth_meat, soybean_pods | [3,2] | protein_ration | 1 |
| 19 | coolant_radiant_tea | coolant_mixer | tea_leaf, hydro_gel | [3,2] | radiant_tea | 1 |
| 20 | bio_lab_fruit_puree | bio_lab | lumenfruit, shadow_berry_cluster | [3,2] | fruit_puree | 1 |
| 21 | coolant_nebula_juice | coolant_mixer | frost_apple, lumenfruit | [3,2] | nebula_juice | 1 |
| 22 | biosynth_orchard_ration | biosynth_vat | fruit_puree, soybean_pods | [3,2] | orchard_ration | 1 |
| 23 | bio_lab_stormplum_gel | bio_lab | stormplum, hydro_gel | [3,2] | radiant_tea | 1 |
| 24 | biosynth_fig_bar | biosynth_vat | sunflare_fig, fruit_puree | [3,2] | nutrition_bar | 1 |
| 25 | auto_farm_tomato_seed | auto_farm | crop_seeds, bio_catalyst | [3,2] | tomato_seeds | 1 |
| 26 | auto_farm_cabbage_seed | auto_farm | crop_seeds, hydro_gel | [3,2] | cabbage_seeds | 1 |
| 27 | greenhouse_corn_seed | greenhouse | seed_cluster, bio_catalyst | [3,2] | corn_seeds | 1 |
| 28 | greenhouse_onion_bulbs | greenhouse | seed_cluster, gene_template | [3,2] | onion_bulbs | 1 |
| 29 | coolant_lemon_juice | coolant_mixer | lemon, hydro_gel | [3,2] | lemon_juice | 1 |
| 30 | coolant_orange_juice | coolant_mixer | orange, hydro_gel | [3,2] | orange_juice | 1 |
| 31 | coolant_peach_juice | coolant_mixer | peach, hydro_gel | [3,2] | peach_juice | 1 |
| 32 | coolant_pear_juice | coolant_mixer | pear, hydro_gel | [3,2] | pear_juice | 1 |
| 33 | coolant_cherry_juice | coolant_mixer | cherry, hydro_gel | [3,2] | cherry_juice | 1 |
| 34 | bio_lab_orchard_salad | bio_lab | tomato, cabbage | [3,2] | orchard_salad | 1 |
| 35 | bio_lab_cornbread | bio_lab | corn, soybean_pods | [3,2] | cornbread | 1 |
| 36 | bio_lab_stuffed_cabbage | bio_lab | cabbage, onion | [3,2] | stuffed_cabbage | 1 |
| 37 | biosynth_sunrise_pie | biosynth_vat | orange, peach | [3,2] | sunrise_pie | 1 |
| 38 | biosynth_berry_tart | biosynth_vat | shadow_berry_cluster, spiceberry | [3,2] | berry_tart | 1 |
| 39 | bio_lab_citrus_salad | bio_lab | orange, lemon | [3,2] | citrus_salad | 1 |
| 40 | bio_lab_tomato_stew | bio_lab | tomato, onion | [3,2] | tomato_stew | 1 |
| 41 | coolant_orchard_smoothie | coolant_mixer | peach, pear | [3,2] | orchard_smoothie | 1 |
| 42 | coolant_sparkling_lemonade | coolant_mixer | lemon, orange | [3,2] | sparkling_lemonade | 1 |
| 43 | biosynth_peach_cobbler | biosynth_vat | peach, corn | [3,2] | peach_cobbler | 1 |
| 44 | biosynth_pear_crisp | biosynth_vat | pear, cornbread | [3,2] | pear_crisp | 1 |
| 45 | bio_lab_aurelia_glaze | bio_lab | lumenfruit, irradiated_shard | [3,2] | aurelia_glaze | 1 |
| 46 | biosynth_cryon_hotpot | biosynth_vat | frost_apple, cryonite_crystal | [3,2] | cryon_hotpot | 1 |
| 47 | coolant_nyx_phase_gel | coolant_mixer | shadow_berry_cluster, voidglass_fragment | [3,2] | nyx_phase_gel | 1 |
| 48 | cryo_helion_sorbet | cryo_distiller | sunflare_fig, solarite_shard | [3,2] | helion_sorbet | 1 |
| 49 | coolant_tempest_fizz | coolant_mixer | stormplum, stormglass_shard | [3,2] | tempest_fizz | 1 |
| 50 | assembler_field_sickle | assembler | harvest_unit, steel_plate | [3,2] | field_sickle | 1 |
| 51 | assembler_hydro_spade | assembler | agri_module, servo_motor | [3,2] | hydro_spade | 1 |
| 52 | assembler_vector_grapple | assembler | servo_bundle, insulated_wire, sensor_array | [2,2,2] | vector_grapple | 1 |
| 53 | assembler_tech_magnet | assembler | magnetic_ring, energy_cell, copper_wire | [2,2,2] | tech_magnet | 1 |
| 54 | advanced_pulse_thruster | advanced_assembler | pressure_membrane, plasma_cell, precision_nozzle | [2,2,2] | pulse_thruster | 1 |
| 55 | purifier_radiation_filter | purifier | rust_filter, nano_coating | [2,2] | radiation_filter | 1 |
| 56 | observatory_survey_chip | observatory | star_chart, data_core | [3,2] | survey_chip | 1 |
| 57 | survey_beacon_data | survey_beacon | survey_chip, star_chart | [3,2] | survey_data | 1 |
| 58 | relic_analyzer_aurelia | relic_analyzer | survey_data, planetary_relic | [3,2] | ancient_signal | 1 |
| 59 | relic_analyzer_cryon | relic_analyzer | survey_data, cryon_relic | [3,2] | ancient_signal | 1 |
| 60 | relic_analyzer_nyx | relic_analyzer | survey_data, nyx_relic | [3,2] | ancient_signal | 1 |
| 61 | relic_analyzer_helion | relic_analyzer | ancient_signal, helion_relic | [3,2] | frontier_core_fragment | 1 |
| 62 | relic_analyzer_tempest | relic_analyzer | ancient_signal, tempest_relic | [3,2] | frontier_core_fragment | 1 |
| 63 | xeno_alloy_recipe | xeno_refinery | ancient_signal, irradiated_shard | [3,2] | xeno_alloy | 1 |
| 64 | xeno_radiation_mesh | xeno_refinery | xeno_alloy, irradiated_shard, aurelia_parasite_gland | [3,2] | radiation_mesh | 1 |
| 65 | xeno_thermal_lining | xeno_refinery | xeno_alloy, cryonite_crystal, cryon_ice_heart | [3,2] | thermal_lining | 1 |
| 66 | xeno_pressure_membrane | xeno_refinery | xeno_alloy, voidglass_fragment, nyx_phase_tissue | [3,2] | pressure_membrane | 1 |
| 67 | xeno_flare_shielding | xeno_refinery | xeno_alloy, solarite_shard, helion_cinder_core | [3,2] | flare_shielding | 1 |
| 68 | xeno_storm_baffle | xeno_refinery | xeno_alloy, stormglass_shard, tempest_capacitor | [3,2] | storm_baffle | 1 |
| 69 | planetary_exo_frame | planetary_forge | xeno_alloy, composite_panel | [3,2] | exo_frame | 1 |
| 70 | planetary_seal_fabric | planetary_forge | synthetic_fiber, void_bloom | [3,2] | seal_fabric | 1 |
| 71 | advanced_anti_radiation_helmet | planetary_forge | exo_frame, seal_fabric, radiation_mesh, glass_panel | [3,2,2,2] | anti_radiation_helmet | 1 |
| 72 | advanced_anti_radiation_chestplate | planetary_forge | exo_frame, seal_fabric, radiation_mesh, carbon_mesh | [3,2,2,2] | anti_radiation_chestplate | 1 |
| 73 | advanced_anti_radiation_leggings | planetary_forge | exo_frame, seal_fabric, radiation_mesh, insulated_wire | [3,2,2,2] | anti_radiation_leggings | 1 |
| 74 | advanced_anti_radiation_boots | planetary_forge | exo_frame, seal_fabric, radiation_mesh, rubber_sheet | [3,2,2,2] | anti_radiation_boots | 1 |
| 75 | planetary_thermal_helmet | planetary_forge | exo_frame, seal_fabric, thermal_lining, glass_panel | [3,2,2,2] | thermal_helmet | 1 |
| 76 | planetary_thermal_chestplate | planetary_forge | exo_frame, seal_fabric, thermal_lining, carbon_mesh | [3,2,2,2] | thermal_chestplate | 1 |
| 77 | planetary_thermal_leggings | planetary_forge | exo_frame, seal_fabric, thermal_lining, insulated_wire | [3,2,2,2] | thermal_leggings | 1 |
| 78 | planetary_thermal_boots | planetary_forge | exo_frame, seal_fabric, thermal_lining, rubber_sheet | [3,2,2,2] | thermal_boots | 1 |
| 79 | planetary_pressure_helmet | planetary_forge | exo_frame, seal_fabric, pressure_membrane, glass_panel | [3,2,2,2] | pressure_helmet | 1 |
| 80 | planetary_pressure_chestplate | planetary_forge | exo_frame, seal_fabric, pressure_membrane, carbon_mesh | [3,2,2,2] | pressure_chestplate | 1 |
| 81 | planetary_pressure_leggings | planetary_forge | exo_frame, seal_fabric, pressure_membrane, insulated_wire | [3,2,2,2] | pressure_leggings | 1 |
| 82 | planetary_pressure_boots | planetary_forge | exo_frame, seal_fabric, pressure_membrane, rubber_sheet | [3,2,2,2] | pressure_boots | 1 |
| 83 | frontier_core_recipe | planetary_forge | frontier_core_fragment, xeno_alloy, helion_cinder_core, tempest_capacitor | [3,2,2,2] | frontier_core | 1 |
| 84 | frontier_helmet_recipe | planetary_forge | frontier_core, flare_shielding, storm_baffle, glass_panel | [3,2,2,2] | frontier_helmet | 1 |
| 85 | frontier_chestplate_recipe | planetary_forge | frontier_core, flare_shielding, storm_baffle, carbon_mesh | [3,2,2,2] | frontier_chestplate | 1 |
| 86 | frontier_leggings_recipe | planetary_forge | frontier_core, flare_shielding, storm_baffle, insulated_wire | [3,2,2,2] | frontier_leggings | 1 |
| 87 | frontier_boots_recipe | planetary_forge | frontier_core, flare_shielding, storm_baffle, rubber_sheet | [3,2,2,2] | frontier_boots | 1 |
| 88 | planetary_gate_recipe | advanced_assembler | gravitic_bearing, quantum_frame, data_core | [2,2,2] | planetary_gate | 1 |
| 89 | storm_jetpack_recipe | planetary_forge | frontier_core, storm_baffle, drone_frame, precision_nozzle | [3,2,2,2] | storm_jetpack | 1 |
| 90 | plasma_dust_recipe | plasma_refiner | ion_dust, refined_oil | [3,2] | plasma_dust | 1 |
| 91 | plasma_ingot_recipe | plasma_refiner | titanium_alloy, plasma_dust | [3,2] | plasma_ingot | 1 |
| 92 | reactor_lattice_recipe | plasma_refiner | plasma_ingot, alloy_frame | [3,2] | reactor_lattice | 1 |
| 93 | photon_shard_recipe | photon_weaver | laser_lens, starlight_plate | [3,2] | photon_shard | 1 |
| 94 | photon_plate_recipe | photon_weaver | photon_shard, reinforced_glass | [3,2] | photon_plate | 1 |
| 95 | observatory_lens_recipe | photon_weaver | photon_shard, optic_lens | [3,2] | observatory_lens | 1 |
| 96 | observatory_star_chart_recipe | observatory | observatory_lens, data_matrix | [3,2] | star_chart | 1 |
| 97 | observatory_bearing_recipe | observatory | magnetic_ring, graviton_lens | [3,2] | gravitic_bearing | 1 |
| 98 | observatory_vacuum_module_recipe | observatory | star_chart, vacuum_tube | [3,2] | vacuum_module | 1 |
| 99 | observatory_data_core_recipe | observatory | star_chart, control_unit | [3,2] | data_core | 1 |
| 100 | archive_plate_recipe | data_archive | data_core, gravitic_bearing | [3,2] | archive_plate | 1 |
| 101 | craft_pulse_staff | field_forge | advanced_circuit, dense_carbon_block | [3,2] | pulse_staff | 1 |
| 102 | craft_storm_staff | field_forge | advanced_circuit, heat_coil | [3,2] | storm_staff | 1 |
| 103 | craft_gravity_staff | field_forge | graviton_lens, advanced_circuit | [3,2] | gravity_staff | 1 |
| 104 | craft_warp_orb | field_forge | quantum_chip, graviton_lens | [3,2] | warp_orb | 1 |
| 105 | craft_cryo_wand | field_forge | coolant_cell, advanced_circuit | [3,2] | cryo_wand | 1 |
| 106 | craft_plasma_lance | matter_compiler | plasma_cell, titanium_alloy | [3,2] | plasma_lance | 1 |
| 107 | craft_void_mirror | matter_compiler | photon_plate, graviton_lens | [3,2] | void_mirror | 1 |
| 108 | craft_time_dilator | matter_compiler | quantum_chip, control_unit | [3,2] | time_dilator | 1 |
| 109 | craft_heal_beacon | matter_compiler | data_core, fusion_core | [3,2] | heal_beacon | 1 |
| 110 | craft_entropy_scepter | matter_compiler | void_beacon_core, plasma_cell | [3,2] | entropy_scepter | 1 |
| 111 | craft_titan_helmet | assembler | composite_panel, circuit_board | [3,2] | titan_helmet | 1 |
| 112 | craft_titan_chestplate | assembler | composite_panel, carbon_mesh | [3,2] | titan_chestplate | 1 |
| 113 | craft_titan_leggings | assembler | composite_panel, copper_wire | [3,2] | titan_leggings | 1 |
| 114 | craft_titan_boots | assembler | composite_panel, rubber_sheet | [3,2] | titan_boots | 1 |
| 115 | craft_quantum_helmet | quantum_processor | quantum_chip, titanium_alloy | [3,2] | quantum_helmet | 1 |
| 116 | craft_quantum_chestplate | quantum_processor | quantum_chip, photon_plate | [3,2] | quantum_chestplate | 1 |
| 117 | craft_quantum_leggings | quantum_processor | quantum_chip, carbon_mesh | [3,2] | quantum_leggings | 1 |
| 118 | craft_quantum_boots | quantum_processor | quantum_chip, graviton_lens | [3,2] | quantum_boots | 1 |
| 119 | craft_void_crown | matter_compiler | void_beacon_core, photon_plate | [3,2] | void_crown | 1 |
| 120 | craft_void_cuirass | matter_compiler | void_beacon_core, titanium_alloy | [3,2] | void_cuirass | 1 |
| 121 | craft_void_greaves | matter_compiler | void_beacon_core, carbon_mesh | [3,2] | void_greaves | 1 |
| 122 | craft_void_sabatons | matter_compiler | void_beacon_core, graviton_lens | [3,2] | void_sabatons | 1 |
| 123 | craft_talisman_fire | field_forge | circuit_board, heat_coil | [3,2] | talisman_fire | 1 |
| 124 | craft_talisman_water | field_forge | circuit_board, hydro_gel | [3,2] | talisman_water | 1 |
| 125 | craft_talisman_angel | field_forge | circuit_board, rubber_sheet | [3,2] | talisman_angel | 1 |
| 126 | craft_talisman_warrior | field_forge | circuit_board, dense_carbon_block | [3,2] | talisman_warrior | 1 |
| 127 | craft_talisman_knight | laser_engraver | advanced_circuit, composite_panel | [3,2] | talisman_knight | 1 |
| 128 | craft_talisman_traveler | field_forge | circuit_board, copper_wire | [3,2] | talisman_traveler | 1 |
| 129 | craft_talisman_hunter | laser_engraver | advanced_circuit, optic_lens | [3,2] | talisman_hunter | 1 |
| 130 | craft_talisman_miner | laser_engraver | advanced_circuit, heat_coil | [3,2] | talisman_miner | 1 |
| 131 | craft_talisman_farmer | field_forge | circuit_board, bio_catalyst | [3,2] | talisman_farmer | 1 |
| 132 | craft_talisman_anvil | laser_engraver | advanced_circuit, titanium_alloy | [3,2] | talisman_anvil | 1 |
| 133 | craft_talisman_heal | laser_engraver | advanced_circuit, data_matrix | [3,2] | talisman_heal | 1 |
| 134 | craft_talisman_whirlwind | laser_engraver | advanced_circuit, dense_carbon_block | [3,2] | talisman_whirlwind | 1 |
| 135 | craft_magic_lump_1 | assembler | iron_dust, gold_nugget | [1,1] | magic_lump_1 | 1 |
| 136 | craft_magic_lump_2 | field_forge | magic_lump_1 ×4 | [1,1,1,1] | magic_lump_2 | 1 |
| 137 | craft_magic_lump_3 | laser_engraver | magic_lump_2 ×4 | [1,1,1,1] | magic_lump_3 | 1 |
| 138 | craft_ender_lump_1 | assembler | iron_dust, ender_pearl | [1,1] | ender_lump_1 | 1 |
| 139 | craft_ender_lump_2 | field_forge | ender_lump_1 ×4 | [1,1,1,1] | ender_lump_2 | 1 |
| 140 | craft_ender_lump_3 | laser_engraver | ender_lump_2 ×4 | [1,1,1,1] | ender_lump_3 | 1 |
| 141 | craft_magic_book_cover | field_forge | magic_lump_2, magic_lump_2, book, magic_lump_2 | [3,2,2,2] | magic_book_cover | 1 |
| 142 | craft_magic_glass | field_forge | magic_lump_2, copper_dust, glass_pane | [3,2,2] | magic_glass | 1 |
| 143 | craft_lava_crystal | field_forge | magic_lump_1, ancient_rune_fire, blaze_powder | [3,2,2] | lava_crystal | 1 |
| 144 | craft_common_talisman | field_forge | magic_lump_2, magic_lump_2, gold_ingot, ender_lump_1 | [3,2,2,2] | common_talisman | 1 |
| 145 | craft_necrotic_skull | laser_engraver | magic_lump_3, wither_skeleton_skull | [3,2] | necrotic_skull | 1 |
| 146 | craft_essence_of_afterlife | quantum_processor | necrotic_skull, ender_lump_3, nether_star | [3,2,2] | essence_of_afterlife | 1 |
| 147 | craft_synthetic_shulker_shell | field_forge | magic_lump_2, iron_ingot, ender_lump_1 | [3,2,2] | synthetic_shulker_shell | 1 |
| 148 | craft_shulker_box | field_forge | synthetic_shulker_shell ×2, chest | [3,2,2] | SHULKER_BOX | 1 |
| 149 | craft_ancient_rune_fire | laser_engraver | magic_lump_2, ender_lump_2, blaze_powder, lava_bucket | [2,2,1,1] | ancient_rune_fire | 1 |
| 150 | craft_ancient_rune_water | laser_engraver | magic_lump_2, ender_lump_2, prismarine_shard, water_bucket | [2,2,1,1] | ancient_rune_water | 1 |
| 151 | craft_ancient_rune_earth | laser_engraver | magic_lump_2, ender_lump_2, obsidian, moss_block | [2,2,1,1] | ancient_rune_earth | 1 |
| 152 | craft_ancient_rune_air | laser_engraver | magic_lump_2, ender_lump_2, feather, phantom_membrane | [2,2,1,1] | ancient_rune_air | 1 |
| 153 | craft_ancient_rune_ender | laser_engraver | magic_lump_2, ender_lump_2, chorus_fruit, ender_eye | [2,2,1,1] | ancient_rune_ender | 1 |
| 154 | craft_ancient_rune_lightning | laser_engraver | magic_lump_2, ender_lump_2, copper_ingot, lightning_rod | [2,2,1,1] | ancient_rune_lightning | 1 |
| 155 | craft_ancient_rune_rainbow | laser_engraver | magic_lump_2, ender_lump_2, glow_ink_sac, amethyst_shard | [2,2,1,1] | ancient_rune_rainbow | 1 |
| 156 | craft_ancient_rune_soulbound | laser_engraver | magic_lump_3, ender_lump_3, soul_lantern, necrotic_skull | [2,2,1,1] | ancient_rune_soulbound | 1 |
| 157 | craft_ancient_rune_villager | laser_engraver | magic_lump_2, ender_lump_2, emerald, strange_nether_goo | [2,2,1,1] | ancient_rune_villager | 1 |
| 158 | bio_lab_vegetable_curry | bio_lab | onion, bell_pepper | [3,2] | vegetable_curry | 1 |
| 159 | bio_lab_ramen | bio_lab | soybean_pods, corn | [3,2] | ramen | 1 |
| 160 | bio_lab_sushi_roll | bio_lab | corn, tomato | [3,2] | sushi_roll | 1 |
| 161 | bio_lab_cheese_pizza | bio_lab | tomato, garlic | [3,2] | cheese_pizza | 1 |
| 162 | bio_lab_veggie_wrap | bio_lab | lettuce, tomato | [3,2] | veggie_wrap | 1 |
| 163 | bio_lab_breakfast_burrito | bio_lab | bell_pepper, corn | [3,2] | breakfast_burrito | 1 |
| 164 | bio_lab_fruit_parfait | bio_lab | strawberry, blueberry | [3,2] | fruit_parfait | 1 |
| 165 | biosynth_cheesecake | biosynth_vat | fruit_puree, bio_catalyst | [3,2] | cheesecake | 1 |
| 166 | biosynth_chocolate_cake | biosynth_vat | fruit_puree, spiceberry | [3,2] | chocolate_cake | 1 |
| 167 | cryo_vanilla_ice_cream | cryo_distiller | coconut, fruit_puree | [3,2] | vanilla_ice_cream | 1 |
| 168 | coolant_grape_juice | coolant_mixer | grape, hydro_gel | [3,2] | grape_juice | 1 |
| 169 | coolant_mango_smoothie | coolant_mixer | mango, coconut | [3,2] | mango_smoothie | 1 |
| 170 | coolant_coconut_water | coolant_mixer | coconut, hydro_gel | [3,2] | coconut_water | 1 |
| 171 | coolant_berry_blend | coolant_mixer | strawberry, blueberry | [3,2] | berry_blend | 1 |
| 172 | coolant_tropical_punch | coolant_mixer | pineapple, mango | [3,2] | tropical_punch | 1 |
| 173 | coolant_iced_tea | coolant_mixer | tea_leaf, coolant_mix | [3,2] | iced_tea | 1 |
| 174 | coolant_apple_cider | coolant_mixer | pear, coolant_mix | [3,2] | apple_cider | 1 |
| 175 | coolant_ginger_ale | coolant_mixer | watermelon_slice, hydro_gel | [3,2] | ginger_ale | 1 |
| 176 | coolant_vanilla_milkshake | coolant_mixer | banana, coconut | [3,2] | vanilla_milkshake | 1 |
| 177 | coolant_strawberry_milkshake | coolant_mixer | strawberry, coconut | [3,2] | strawberry_milkshake | 1 |

**expansion 配方小計：177 筆**

---

### 📄 tech-content-systems.yml（系統）

| # | Recipe ID | machine | inputs | input-amounts | output | count |
|---|-----------|---------|--------|---------------|--------|-------|
| 1 | drill_head_recipe | assembler | steel_plate, titanium_alloy | [3,2] | drill_head | 1 |
| 2 | quarry_frame_recipe | assembler | machine_component, alloy_frame | [3,2] | quarry_frame | 1 |
| 3 | harvest_matrix_recipe | assembler | agri_module, signal_relay | [3,2] | harvest_matrix | 1 |
| 4 | vacuum_core_recipe | assembler | signal_relay, pressure_tube | [3,2] | vacuum_core | 1 |
| 5 | storm_rotor_recipe | wire_mill | steel_wire, magnetic_ring | [3,2] | storm_rotor | 1 |
| 6 | weather_sensor_recipe | insulation_press | sensor_array, insulated_wire | [3,2] | weather_sensor | 1 |
| 7 | field_processor_recipe | advanced_assembler | control_unit, logic_gate | [3,2] | field_processor | 1 |
| 8 | quarry_drill_recipe | advanced_assembler | drill_head, quarry_frame | [3,2] | quarry_drill | 1 |
| 9 | quarry_drill_mk2_recipe | advanced_assembler | quarry_drill, capacitor_bank, control_unit | [2,2,2] | quarry_drill_mk2 | 1 |
| 10 | quarry_drill_mk3_recipe | quantum_processor | quarry_drill_mk2, fusion_core, quantum_frame | [3,2,2] | quarry_drill_mk3 | 1 |
| 11 | crop_harvester_recipe | assembler | harvest_matrix, agri_module | [3,2] | crop_harvester | 1 |
| 12 | vacuum_inlet_recipe | assembler | vacuum_core, machine_casing | [3,2] | vacuum_inlet | 1 |
| 13 | storm_turbine_recipe | advanced_assembler | storm_rotor, weather_sensor | [3,2] | storm_turbine | 1 |
| 14 | logging_blade_recipe | assembler | steel_plate, servo_motor | [3,2] | logging_blade | 1 |
| 15 | arbor_frame_recipe | assembler | machine_component, treated_plastic | [3,2] | arbor_frame | 1 |
| 16 | tree_feller_recipe | assembler | logging_blade, arbor_frame | [3,2] | tree_feller | 1 |
| 17 | bait_module_recipe | bio_lab | bio_resin, crops | [3,2] | bait_module | 1 |
| 18 | dock_frame_recipe | assembler | pressure_tube, composite_panel | [3,2] | dock_frame | 1 |
| 19 | fishing_dock_recipe | assembler | bait_module, dock_frame | [3,2] | fishing_dock | 1 |
| 20 | net_launcher_recipe | advanced_assembler | field_processor, servo_motor | [3,2] | net_launcher | 1 |
| 21 | mob_collector_recipe | advanced_assembler | net_launcher, field_processor | [3,2] | mob_collector | 1 |
| 22 | storage_crate_recipe | assembler | machine_casing, plastic, wiring_bundle | [2,2,2] | storage_crate | 1 |
| 23 | storage_hub_recipe | assembler | storage_crate, machine_casing, wiring_bundle | [2,2,2] | storage_hub | 1 |
| 24 | routing_chip_recipe | laser_engraver | etched_circuit, logic_gate, wiring_bundle | [3,2,2] | routing_chip | 1 |
| 25 | filter_mesh_core_recipe | advanced_assembler | routing_chip, nano_coating, purifier_membrane | [2,2,2] | filter_mesh_core | 1 |
| 26 | filter_router_recipe | advanced_assembler | filter_mesh_core, routing_chip, purifier_membrane | [2,2,2] | filter_router | 1 |
| 27 | splitter_core_recipe | assembler | routing_chip, signal_relay, servo_motor | [2,2,2] | splitter_core | 1 |
| 28 | splitter_node_recipe | assembler | splitter_core, routing_chip, servo_motor | [2,2,2] | splitter_node | 1 |
| 29 | bus_frame_recipe | wire_mill | steel_wire, copper_wire, centrifuge_rotor | [3,2,2] | bus_frame | 1 |
| 30 | industrial_bus_recipe | advanced_assembler | bus_frame, storage_crate, routing_chip, centrifuge_rotor | [2,2,1,1] | industrial_bus | 1 |
| 31 | cargo_input_node_recipe | assembler | routing_chip, servo_motor, storage_crate | [2,2,2] | cargo_input_node | 1 |
| 32 | cargo_manager_recipe | advanced_assembler | bus_frame, routing_chip, control_unit, signal_relay | [2,2,1,1] | cargo_manager | 1 |
| 33 | trash_node_recipe | assembler | machine_casing, lava_bucket | [3,2] | trash_node | 1 |
| 34 | cargo_output_node_recipe | assembler | routing_chip, servo_motor, pressure_tube | [2,2,2] | cargo_output_node | 1 |
| 35 | cargo_motor_recipe | assembler | bus_frame, servo_motor, routing_chip | [2,2,2] | cargo_motor | 1 |

**systems 配方小計：35 筆**

---

### 📄 tech-content.yml（主終局線）

| # | Recipe ID | machine | inputs | input-amounts | output | count |
|---|-----------|---------|--------|---------------|--------|-------|
| 1 | stellar_neutronium_ingot | void_extractor | dark_matter_dust, singularity_fragment | [3,2] | neutronium_ingot | 1 |
| 2 | singularity_neutronium_plate | singularity_press | neutronium_ingot, quantum_chip | [3,2] | neutronium_plate | 1 |
| 3 | void_dark_matter_dust | void_extractor | matter_blob, ion_dust | [3,2] | dark_matter_dust | 1 |
| 4 | void_shard_recipe | void_extractor | dark_matter_dust, crystal_seed | [3,2] | void_shard | 1 |
| 5 | loom_quantum_fabric | dark_matter_loom | dark_matter_dust, synthetic_fiber | [3,2] | quantum_fabric | 1 |
| 6 | loom_dark_matter_sheet | dark_matter_loom | dark_matter_dust, phase_plate | [3,2] | dark_matter_sheet | 1 |
| 7 | graviton_lens_recipe | graviton_stabilizer | flux_link, optic_lens | [3,2] | graviton_lens | 1 |
| 8 | graviton_core_recipe | graviton_stabilizer | graviton_lens, field_plate | [3,2] | graviton_core | 1 |
| 9 | singularity_mesh_recipe | singularity_press | singularity_fragment, dark_matter_sheet | [3,2] | singularity_mesh | 1 |
| 10 | singularity_core_recipe | singularity_press | singularity_mesh, graviton_core | [3,2] | singularity_core | 1 |
| 11 | antimatter_cluster_recipe | antimatter_synthesizer | ion_dust, void_shard | [3,2] | antimatter_cluster | 1 |
| 12 | antimatter_cell_recipe | antimatter_synthesizer | antimatter_cluster, quantum_fabric | [3,2] | antimatter_cell | 1 |
| 13 | stellar_alloy_recipe | stellar_forge | neutronium_plate, antimatter_cell | [3,2] | stellar_alloy | 1 |
| 14 | stellar_glass_recipe | stellar_forge | void_shard, crystal_matrix | [3,2] | stellar_glass | 1 |
| 15 | orbit_frame_recipe | orbital_printer | stellar_alloy, dark_matter_sheet | [3,2] | orbit_frame | 1 |
| 16 | orbit_processor_recipe | orbital_printer | stellar_glass, data_matrix | [3,2] | orbit_processor | 1 |
| 17 | void_crystal_recipe | void_beacon | void_shard, grown_crystal | [3,2] | void_crystal | 1 |
| 18 | void_beacon_core_recipe | void_beacon | void_crystal, singularity_core | [3,2] | void_beacon_core | 1 |
| 19 | nanite_slurry_recipe | nanite_foundry | quantum_fluid, antimatter_cluster | [3,2] | nanite_slurry | 1 |
| 20 | nanite_cluster_recipe | nanite_foundry | nanite_slurry, neutronium_plate | [3,2] | nanite_cluster | 1 |
| 21 | chrono_thread_recipe | chrono_engine | quantum_fabric, phase_plate | [3,2] | chrono_thread | 1 |
| 22 | chrono_core_recipe | chrono_engine | chrono_thread, graviton_core | [3,2] | chrono_core | 1 |
| 23 | entropy_plate_recipe | entropy_chamber | dark_matter_sheet, fusion_mesh | [3,2] | entropy_plate | 1 |
| 24 | entropy_core_recipe | entropy_chamber | entropy_plate, void_beacon_core | [3,2] | entropy_core | 1 |
| 25 | cosmic_matrix_recipe | celestial_assembler | nanite_cluster, chrono_core | [3,2] | cosmic_matrix | 1 |
| 26 | cosmic_panel_recipe | celestial_assembler | cosmic_matrix, entropy_core | [3,2] | cosmic_panel | 1 |
| 27 | omega_core_recipe | omega_fabricator | cosmic_panel, singularity_core | [3,2] | omega_core | 1 |
| 28 | relay_node_chain | omega_fabricator | orbit_frame, orbit_processor | [3,2] | relay_node | 1 |
| 29 | long_range_tube_recipe | omega_fabricator | orbit_frame, field_plate | [3,2] | long_range_tube | 1 |

**main 配方小計：29 筆**

---

### 📄 tech-content-chickens.yml（雞牧場）

| # | Recipe ID | machine | inputs | input-amounts | output | count |
|---|-----------|---------|--------|---------------|--------|-------|
| 1 | assembler_chicken_net | assembler | string, stick, feather | [2,2,2] | chicken_net | 4 |
| 2 | assembler_genetic_sequencer | assembler | circuit_board, glass_panel, copper_wire | [2,2,2] | genetic_sequencer | 1 |
| 3 | assembler_private_coop | assembler | machine_casing, fence | [3,2] | private_coop | 1 |
| 4 | assembler_excitation_chamber | assembler | machine_casing, circuit_board, copper_wire | [2,2,2] | excitation_chamber | 1 |

**chickens 配方小計：4 筆**

---

### 📄 tech-content-megastructures.yml（巨構）

| # | Recipe ID | machine | inputs | input-amounts | output | count |
|---|-----------|---------|--------|---------------|--------|-------|
| 1 | starsteel_ingot_recipe | starsteel_foundry | stellar_alloy, neutronium_ingot | [3,2] | starsteel_ingot | 1 |
| 2 | starsteel_frame_recipe | starsteel_foundry | starsteel_ingot, orbit_frame | [3,2] | starsteel_frame | 1 |
| 3 | vacuum_glass_recipe | vacuum_chamber | stellar_glass, void_crystal | [3,2] | vacuum_glass | 1 |
| 4 | harmonic_crystal_recipe | vacuum_chamber | void_crystal, chrono_thread | [3,2] | harmonic_crystal | 1 |
| 5 | celestial_lens_recipe | vacuum_chamber | void_crystal, graviton_lens | [3,2] | celestial_lens | 1 |
| 6 | nebula_fiber_recipe | warp_assembler | quantum_fabric, chrono_thread | [3,2] | nebula_fiber | 1 |
| 7 | warp_cell_recipe | warp_assembler | antimatter_cell, singularity_mesh | [3,2] | warp_cell | 1 |
| 8 | warp_coil_recipe | warp_assembler | warp_cell, nanite_cluster | [3,2] | warp_coil | 1 |
| 9 | astral_circuit_recipe | event_horizon_smith | orbit_processor, entropy_plate | [3,2] | astral_circuit | 1 |
| 10 | event_horizon_plate_recipe | event_horizon_smith | entropy_plate, dark_matter_sheet | [3,2] | event_horizon_plate | 1 |
| 11 | singularity_casing_recipe | event_horizon_smith | singularity_mesh, starsteel_frame | [3,2] | singularity_casing | 1 |
| 12 | chrono_lattice_recipe | continuum_lathe | chrono_core, harmonic_crystal | [3,2] | chrono_lattice | 1 |
| 13 | continuum_shell_recipe | continuum_lathe | chrono_lattice, starsteel_frame | [3,2] | continuum_shell | 1 |
| 14 | aurora_plate_recipe | continuum_lathe | nebula_fiber, vacuum_glass | [3,2] | aurora_plate | 1 |
| 15 | relic_data_recipe | relic_scanner | astral_circuit, celestial_lens | [3,2] | relic_data | 1 |
| 16 | archive_core_recipe | relic_scanner | relic_data, nanite_cluster | [3,2] | archive_core | 1 |
| 17 | dimension_anchor_recipe | anchor_forge | singularity_casing, warp_coil | [3,2] | dimension_anchor | 1 |
| 18 | cluster_manifold_recipe | anchor_forge | dimension_anchor, continuum_shell | [3,2] | cluster_manifold | 1 |
| 19 | omega_matrix_recipe | apex_forge | archive_core, event_horizon_plate | [3,2] | omega_matrix | 1 |
| 20 | apex_core_recipe | apex_forge | omega_matrix, cluster_manifold | [3,2] | apex_core | 1 |
| 21 | omega_archive_recipe | apex_forge | apex_core, aurora_plate | [3,2] | omega_archive | 1 |

**megastructures 配方小計：21 筆**

---

**所有配方總計：192 + 177 + 35 + 29 + 4 + 21 = 458 筆**

---

## TASK 3：屬於 BASIC 類別機器的配方

BASIC 機器清單：`crusher`, `furnace`, `auto_farm`, `electric_saw`, `recycler`, `solar_generator`, `coal_generator`, `research_desk`, `genetic_sequencer`, `private_coop`, `excitation_chamber`

> 注意：`solar_generator`、`coal_generator`、`research_desk`、`genetic_sequencer`、`private_coop`、`excitation_chamber` 無對應加工配方（它們是功能型機器，不作為 `machine:` 出現在任何配方中）。

### crusher（粉碎機）— 14 筆

| Recipe ID | inputs | input-amounts | output | count |
|-----------|--------|---------------|--------|-------|
| crusher_iron_ore | iron_ore | [2] | iron_dust | 2 |
| crusher_copper_ore | copper_ore | [2] | copper_dust | 2 |
| crusher_quartz | quartz | [2] | silicon | 2 |
| crusher_raw_iron | raw_iron | [2] | iron_dust | 2 |
| crusher_raw_copper | raw_copper | [2] | copper_dust | 2 |
| crusher_coal | coal | [2] | coal_dust | 2 |
| crusher_iron_ingot | iron_ingot | [1] | iron_dust | 1 |
| crusher_copper_ingot | copper_ingot | [1] | copper_dust | 1 |
| crusher_tin_ingot | tin_ingot | [1] | tin_dust | 1 |
| crusher_lead_ingot | lead_ingot | [1] | lead_dust | 1 |
| crusher_zinc_ingot | zinc_ingot | [1] | zinc_dust | 1 |
| crusher_bronze_ingot | bronze_ingot | [1] | bronze_dust | 1 |
| crusher_solder_alloy | solder_alloy | [1] | solder_alloy_dust | 1 |
| crusher_brass_ingot | brass_ingot | [1] | brass_dust | 1 |

### furnace（熔爐）— 15 筆

| Recipe ID | inputs | input-amounts | output | count |
|-----------|--------|---------------|--------|-------|
| furnace_iron_dust | iron_dust | [2] | iron_ingot | 1 |
| furnace_copper_dust | copper_dust | [2] | copper_ingot | 1 |
| furnace_glass | glass | [2] | glass_panel | 1 |
| furnace_reinforced_glass | glass_panel, carbon_mesh | [2,2] | reinforced_glass | 1 |
| furnace_heat_coil | copper_wire, blaze_powder | [2,2] | heat_coil | 1 |
| furnace_vacuum_tube | glass_panel, copper_wire | [2,2] | vacuum_tube | 1 |
| furnace_optic_lens | glass_panel, silicon | [2,2] | optic_lens | 1 |
| furnace_tin_dust | tin_dust | [2] | tin_ingot | 1 |
| furnace_lead_dust | lead_dust | [2] | lead_ingot | 1 |
| furnace_zinc_dust | zinc_dust | [2] | zinc_ingot | 1 |
| furnace_bronze_dust | bronze_dust | [2] | bronze_ingot | 1 |
| furnace_solder_alloy_dust | solder_alloy_dust | [2] | solder_alloy | 1 |
| furnace_brass_dust | brass_dust | [2] | brass_ingot | 1 |
| furnace_bronze | copper_dust, tin_dust | [2,2] | bronze_ingot | 1 |
| furnace_sifted_ore | sifted_ore | [2] | iron_ingot | 1 |

### auto_farm（自動農場）— 7 筆

| Recipe ID | inputs | input-amounts | output | count |
|-----------|--------|---------------|--------|-------|
| auto_farm_seed_cluster | crop_seeds, bone_meal | [3,2] | seed_cluster | 1 |
| auto_farm_crop | crop_seeds | [3] | crops | 1 |
| auto_farm_fertilizer | crops, bone_meal | [3,2] | fertilizer_mix | 1 |
| auto_farm_agri | crops, hydro_gel | [3,2] | agri_module | 1 |
| auto_farm_soybean_seed | crop_seeds, fertilizer_mix | [3,2] | soybean_seeds | 1 |
| auto_farm_tomato_seed | crop_seeds, bio_catalyst | [3,2] | tomato_seeds | 1 |
| auto_farm_cabbage_seed | crop_seeds, hydro_gel | [3,2] | cabbage_seeds | 1 |

### electric_saw（電鋸）— 2 筆

| Recipe ID | inputs | input-amounts | output | count |
|-----------|--------|---------------|--------|-------|
| saw_wood | oak_planks | [2] | wood_dust | 1 |
| saw_fiber | bamboo | [2] | fiber_mesh | 1 |

### recycler（回收機）— 3 筆

| Recipe ID | inputs | input-amounts | output | count |
|-----------|--------|---------------|--------|-------|
| recycler_material | wood_dust | [2] | recycled_material | 1 |
| recycler_plastic | wood_dust, recycled_material | [2,2] | plastic | 1 |
| recycler_filter | paper, coal_dust | [2,2] | rust_filter | 1 |

---

### BASIC 配方總計：14 + 15 + 7 + 2 + 3 = **41 筆**

（佔全部 458 筆配方的 ~9%）
