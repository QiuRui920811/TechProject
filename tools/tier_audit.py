#!/usr/bin/env python3
"""
全面分層審計腳本 — TechProject Tier System Audit
=================================================
規則定義：
  TIER1 (BASIC)        = 第一階：初始機器 (crusher, furnace, electric_saw, recycler, research_desk, auto_farm, ...)
  TIER2 (INTERMEDIATE) = 第二階：需要 TIER1 機器解鎖的機器 (compressor → assembler → wire_mill, smeltery, ore_washer, ...)
  TIER3 (ADVANCED)     = 第三階：需要 TIER2 機器解鎖的機器 (advanced_assembler, purifier, quantum_processor, ...)
  TIER4                = 第四階：終局機器 (explicit tier: TIER4 in YAML)

驗證邏輯：
  [R1] recipe.machine 的 tier <= recipe.output 物品的 tier
       ⇒ 配方不能用比產出物更低階的機器（解鎖順序正確）
  [R2] recipe.input 中每個材料的 tier <= recipe.machine 的 tier
       ⇒ 配方需要的材料，在該機器解鎖時應該已經能取得
  [R3] item.unlock (解鎖機器) 的 tier <= item 本身的 tier
       ⇒ 物品標記的 category/tier 不能低於其解鎖條件所需的機器
  [R4] machine.unlock 引用的先決機器/物品，其 tier < 該機器的 tier（或同階）
       ⇒ 機器解鎖鏈不能形成循環或跨階反向依賴
  [R5] recipe.input 中的自研材料（非原版），其 unlock 機器的 tier <= recipe.machine 的 tier
       ⇒ 配方輸入材料必須在配方機器的同階或更早階段就能生產

輸出：
  - 所有違規項目，按規則分組
  - 材料可達性分析（哪些材料在哪個 tier 首次可生產）
"""

import yaml
import sys
import os
from collections import defaultdict
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent / "src" / "main" / "resources"

CONTENT_FILES = [
    "tech-content-core.yml",
    "tech-content-systems.yml",
    "tech-content-expansion.yml",
    "tech-content-megastructures.yml",
    "tech-content.yml",
]

# Category → numeric tier
CATEGORY_TIER = {
    "BASIC": 1,
    "INTERMEDIATE": 2,
    "ADVANCED": 3,
    "SPECIAL": 1,
}

TIER_OVERRIDE = {
    "TIER1": 1,
    "TIER2": 2,
    "TIER3": 3,
    "TIER4": 4,
}

# Vanilla materials (not tech items, always available = tier 0)
VANILLA_MATS = {
    "iron_ore", "copper_ore", "gold_ore", "coal", "quartz", "raw_iron", "raw_copper",
    "raw_gold", "oak_planks", "spruce_planks", "birch_planks", "jungle_planks",
    "acacia_planks", "dark_oak_planks", "cherry_planks", "mangrove_planks",
    "bamboo_planks", "crimson_planks", "warped_planks",
    "iron_ingot", "gold_ingot", "diamond", "emerald", "lapis_lazuli",
    "redstone", "glowstone_dust", "glass", "glass_pane", "stone", "cobblestone",
    "sand", "gravel", "clay_ball", "leather", "string", "feather",
    "bone", "bone_meal", "gunpowder", "ender_pearl", "blaze_rod", "blaze_powder",
    "magma_cream", "nether_star", "prismarine_shard", "prismarine_crystals",
    "shulker_shell", "phantom_membrane", "nautilus_shell", "heart_of_the_sea",
    "netherite_ingot", "netherite_scrap", "ancient_debris",
    "wheat", "wheat_seeds", "beetroot", "beetroot_seeds", "carrot", "potato",
    "melon_slice", "watermelon_slice", "pumpkin", "sugar_cane", "cactus", "cocoa_beans",
    "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log",
    "dark_oak_log", "cherry_log", "mangrove_log", "bamboo",
    "stick", "chest", "hopper", "piston", "observer", "dropper", "dispenser",
    "slime_ball", "honey_bottle", "honeycomb",
    "copper_block", "iron_block", "gold_block", "diamond_block",
    "amethyst_shard", "budding_amethyst",
    "ghast_tear", "snowball", "lava_bucket", "water_bucket",
    "gold_nugget", "iron_nugget",
    "ender_eye", "paper", "book", "obsidian", "soul_sand", "soul_lantern",
    "glow_ink_sac", "moss_block", "lightning_rod",
    "wither_skeleton_skull", "necrotic_skull",
    "chorus_fruit", "chorus_flower",
    "strange_nether_goo",  # vanilla nether item
    # Uppercase versions (some YAML uses MC names directly)
    "COPPER_INGOT", "IRON_INGOT", "GOLD_INGOT",
}

# ── Non-recipe production: items available WITHOUT a recipe entry ──
# Maps item_id → earliest tier at which the item becomes obtainable.
# These supplement the BFS reachability computation which only considers recipes.

NON_RECIPE_SOURCES = {
    # ─── Gold Pan (hand tool, unlocked: initial → tier 0) ───
    # gravel: iron_dust, copper_dust, tin_dust, zinc_dust, sifted_ore, silicon
    # soul_sand: lead_dust, zinc_dust, sifted_ore, gold_nugget, quartz, iron_dust
    "gold_pan": 0,  # the tool itself is initial unlock, used as upgrade ingredient
    "iron_dust": 0, "copper_dust": 0,
    "tin_dust": 0, "zinc_dust": 0, "lead_dust": 0,
    "sifted_ore": 0, "silicon": 0,

    # ─── Field Collection (acquisition-mode: FIELD_COLLECTION) ───
    # Crops (seeds + produce, from farming)
    "soybean_seeds": 0, "soybean_pods": 0,
    "spiceberry_seeds": 0, "spiceberry": 0,
    "tea_leaf_seeds": 0, "tea_leaf": 0,
    "tomato_seeds": 0, "tomato": 0,
    "cabbage_seeds": 0, "cabbage": 0,
    "corn_seeds": 0, "corn": 0,
    "onion_bulbs": 0, "onion": 0,
    # Orchards (saplings + fruit)
    "lumenfruit_sapling": 0, "lumenfruit": 0,
    "frost_apple_sapling": 0, "frost_apple": 0,
    "shadow_berry_sapling": 0, "shadow_berry_cluster": 0,
    "sunflare_fig_sapling": 0, "sunflare_fig": 0,
    "stormplum_sapling": 0, "stormplum": 0,
    "cherry_sapling": 0, "cherry": 0,
    "lemon_sapling": 0, "lemon": 0,
    "peach_sapling": 0, "peach": 0,
    "pear_sapling": 0, "pear": 0,
    "orange_sapling": 0, "orange": 0,
    # Planetary surface samples (FIELD_COLLECTION — available once planet unlocked)
    # Planet unlock is T4 (megastructures), but we mark them T4 so BFS doesn't block
    "irradiated_shard": 4, "void_bloom": 4, "planetary_relic": 4,
    "cryonite_crystal": 4, "frostbloom": 4, "cryon_relic": 4,
    "voidglass_fragment": 4, "echo_spore": 4, "nyx_relic": 4,
    "solarite_shard": 4, "emberbloom": 4, "helion_relic": 4,
    "stormglass_shard": 4, "ion_fern": 4, "tempest_relic": 4,
    "frontier_core_fragment": 4,
    "pocket_chicken": 0,

    # ─── Planet elite mob drops ───
    "aurelia_parasite_gland": 4,
    "cryon_ice_heart": 4,
    "nyx_phase_tissue": 4,
    "helion_cinder_core": 4,
    "tempest_capacitor": 4,

    # ─── Planet bio seeds (from block harvest, same tier as planet) ───
    "void_bloom_seeds": 4, "frostbloom_seeds": 4, "echo_spore_seeds": 4,
    "emberroot_seeds": 4, "ion_fern_seeds": 4,

    # ─── Expansion food (fruits/vegetables from saplings/bushes/gardens) ───
    "grape": 0, "banana": 0, "mango": 0, "coconut": 0,
    "strawberry": 0, "blueberry": 0, "raspberry": 0,
    "pineapple": 0, "garlic": 0, "lettuce": 0, "bell_pepper": 0,
    # Saplings/bushes themselves
    "grape_sapling": 0, "banana_sapling": 0, "mango_sapling": 0, "coconut_palm": 0,
    "strawberry_bush": 0, "blueberry_bush": 0, "raspberry_bush": 0,

    # ─── Research rewards ───
    "tech_book": 0, "energy_token": 0, "achievement_badge": 0,

    # ─── Gene chicken system (BASIC tier, activator is early game) ───
    # iron_dust + copper_dust already listed above

    # ─── Crop-related items (farmed / processed by BASIC machines) ───
    "crops": 0, "crop_seeds": 0, "seed_cluster": 0,
    "bio_resin": 0, "bio_fiber": 0, "crushed_biomass": 0,
    "fiber_mesh": 0, "fertilizer_mix": 0, "hydro_gel": 0,
}

def load_yaml(filepath):
    with open(filepath, "r", encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def resolve_item_tier(item_data):
    """Resolve the effective tier of an item."""
    explicit_tier = item_data.get("tier")
    if explicit_tier and explicit_tier in TIER_OVERRIDE:
        return TIER_OVERRIDE[explicit_tier]
    cat = item_data.get("category", "BASIC")
    return CATEGORY_TIER.get(cat, 1)


def resolve_machine_tier(machine_data):
    """Resolve the effective tier of a machine."""
    explicit_tier = machine_data.get("tier")
    if explicit_tier and explicit_tier in TIER_OVERRIDE:
        return TIER_OVERRIDE[explicit_tier]
    cat = machine_data.get("category", "BASIC")
    return CATEGORY_TIER.get(cat, 1)


def is_vanilla(mat_id):
    """Check if a material is vanilla (tier 0, always available)."""
    return mat_id in VANILLA_MATS or mat_id.upper() in VANILLA_MATS or mat_id.startswith("minecraft:")


def main():
    # ── Phase 1: Load all data ──
    all_items = {}      # id → {category, tier, unlock, ...}
    all_machines = {}   # id → {category, tier, unlock, outputs, ...}
    all_recipes = {}    # id → {machine, inputs, output, ...}

    for fname in CONTENT_FILES:
        fpath = BASE / fname
        if not fpath.exists():
            print(f"⚠ 檔案不存在: {fpath}")
            continue

        data = load_yaml(fpath)

        # Items
        items = data.get("items", {})
        if items:
            for item_id, item_data in items.items():
                if not isinstance(item_data, dict):
                    continue
                item_data["_source"] = fname
                all_items[item_id] = item_data

        # Machines
        machines = data.get("machines", {})
        if machines:
            for m_id, m_data in machines.items():
                if not isinstance(m_data, dict):
                    continue
                m_data["_source"] = fname
                all_machines[m_id] = m_data

        # Recipes
        recipes = data.get("recipes", {})
        if recipes:
            for r_id, r_data in recipes.items():
                if not isinstance(r_data, dict):
                    continue
                r_data["_source"] = fname
                all_recipes[r_id] = r_data

    print(f"載入完成: {len(all_items)} 物品, {len(all_machines)} 機器, {len(all_recipes)} 配方\n")

    # ── Phase 2: Build tier maps ──
    item_tier = {}  # item_id → numeric tier
    for item_id, data in all_items.items():
        item_tier[item_id] = resolve_item_tier(data)

    machine_tier = {}  # machine_id → numeric tier
    for m_id, data in all_machines.items():
        machine_tier[m_id] = resolve_machine_tier(data)

    # Build: which machine can first produce an item (lowest tier recipe)
    item_produced_by = defaultdict(list)  # item_id → [(recipe_id, machine_id, machine_tier)]
    for r_id, r_data in all_recipes.items():
        machine_id = r_data.get("machine", "")
        output = r_data.get("output", "")
        # Some recipes have list outputs
        outputs = r_data.get("outputs", [])
        if output:
            m_tier = machine_tier.get(machine_id, None)
            item_produced_by[output].append((r_id, machine_id, m_tier))
        if outputs and isinstance(outputs, list):
            for o in outputs:
                m_tier = machine_tier.get(machine_id, None)
                item_produced_by[o].append((r_id, machine_id, m_tier))

    # item_unlock_machine: item → the machine in its "unlock" field
    item_unlock = {}
    for item_id, data in all_items.items():
        unlock = data.get("unlock", "")
        if unlock and unlock != "initial":
            item_unlock[item_id] = unlock

    # machine_unlock: machine → its "unlock" field (can be multi, split by &)
    machine_unlock = {}
    for m_id, data in all_machines.items():
        unlock = data.get("unlock", "")
        if unlock and unlock != "initial":
            # Handle "A & B" multi-unlock syntax
            parts = [p.strip() for p in str(unlock).replace("&", ",").split(",") if p.strip()]
            machine_unlock[m_id] = parts

    # ── Phase 3: Compute effective reachability tier ──
    # The EARLIEST tier at which each material becomes available
    # Algorithm: BFS/propagation from tier-0 (vanilla) upward
    reachable_tier = {}  # item_id → lowest tier at which it can be produced

    # Machine IDs are also reachable at their own tier (for recipes that use
    # a machine as an input ingredient, e.g. upgrading compressor → advanced_compressor)
    for m_id, m_t in machine_tier.items():
        reachable_tier[m_id] = m_t

    # Vanilla materials are always reachable at tier 0
    for v in VANILLA_MATS:
        reachable_tier[v] = 0
        reachable_tier[v.lower()] = 0
        reachable_tier[v.upper()] = 0

    # Non-recipe sources (gold_pan, field collection, mob drops, etc.)
    for item_id, tier_val in NON_RECIPE_SOURCES.items():
        current = reachable_tier.get(item_id)
        if current is None or tier_val < current:
            reachable_tier[item_id] = tier_val

    # Iteratively compute reachability until stable
    changed = True
    iterations = 0
    while changed and iterations < 20:
        changed = False
        iterations += 1
        for r_id, r_data in all_recipes.items():
            machine_id = r_data.get("machine", "")
            m_tier_val = machine_tier.get(machine_id)
            if m_tier_val is None:
                continue  # Unknown machine

            inputs = r_data.get("inputs", [])
            if not isinstance(inputs, list):
                inputs = [inputs]

            # Check if all inputs are reachable at or below this machine's tier
            all_inputs_reachable = True
            max_input_tier = 0
            for inp in inputs:
                inp_lower = inp.lower()
                if is_vanilla(inp):
                    continue  # tier 0
                rt = reachable_tier.get(inp)
                if rt is None:
                    rt = reachable_tier.get(inp_lower)
                if rt is None:
                    all_inputs_reachable = False
                    break
                max_input_tier = max(max_input_tier, rt)

            if not all_inputs_reachable:
                continue

            # The output is reachable at max(machine_tier, max_input_tier)
            effective_tier = max(m_tier_val, max_input_tier)

            output = r_data.get("output", "")
            outputs_list = r_data.get("outputs", [])
            all_outputs = []
            if output:
                all_outputs.append(output)
            if outputs_list and isinstance(outputs_list, list):
                all_outputs.extend(outputs_list)

            for o in all_outputs:
                current = reachable_tier.get(o)
                if current is None or effective_tier < current:
                    reachable_tier[o] = effective_tier
                    changed = True

    print(f"可達性分析完成 (迭代 {iterations} 次), {len(reachable_tier)} 項目有可達性\n")

    # ── Phase 4: Violations ──
    violations = []

    tier_name = {0: "T0(原版)", 1: "T1(BASIC)", 2: "T2(INTERMEDIATE)", 3: "T3(ADVANCED)", 4: "T4(TIER4)"}

    def tn(t):
        return tier_name.get(t, f"T?({t})")

    # [R1] recipe.machine 的 tier 不應比 output 物品聲明的 tier 更高
    # 注意：這裡檢查的是 "一個物品聲明為 BASIC，但只能在 ADVANCED 機器上生產" 的情況
    # 反過來（低tier機器生產高tier物品）是更常見的設計模式，但也要注意
    print("=" * 80)
    print("[R1] 配方產出物的聲明 tier vs 生產機器 tier 對比")
    print("=" * 80)
    for r_id, r_data in sorted(all_recipes.items()):
        machine_id = r_data.get("machine", "")
        output = r_data.get("output", "")
        if not machine_id or not output:
            continue
        m_t = machine_tier.get(machine_id)
        o_t = item_tier.get(output) or item_tier.get(output.lower())
        if m_t is None or o_t is None:
            continue
        if m_t > o_t:
            msg = f"  ⚠ {r_id}: 產出 [{output}]({tn(o_t)}) 由 [{machine_id}]({tn(m_t)}) 生產 — 機器比產物高階"
            violations.append(("R1", r_id, msg))
            print(msg)

    # [R2] recipe 的 input 材料在配方機器的 tier 時是否已可取得
    print()
    print("=" * 80)
    print("[R2] 配方輸入材料在該機器 tier 時是否可取得")
    print("=" * 80)
    for r_id, r_data in sorted(all_recipes.items()):
        machine_id = r_data.get("machine", "")
        inputs = r_data.get("inputs", [])
        if not isinstance(inputs, list):
            inputs = [inputs]
        m_t = machine_tier.get(machine_id)
        if m_t is None:
            continue
        for inp in inputs:
            if is_vanilla(inp):
                continue
            rt = reachable_tier.get(inp)
            if rt is None:
                rt = reachable_tier.get(inp.lower())
            if rt is None:
                msg = f"  ⚠ {r_id}: 輸入 [{inp}] 無法通過任何已知配方生產 (機器: {machine_id}/{tn(m_t)})"
                violations.append(("R2-unreachable", r_id, msg))
                print(msg)
            elif rt > m_t:
                msg = f"  ⚠ {r_id}: 輸入 [{inp}] 最早 {tn(rt)} 才可取得，但配方機器 [{machine_id}] 只有 {tn(m_t)}"
                violations.append(("R2-tier-mismatch", r_id, msg))
                print(msg)

    # [R3] item.unlock 機器的 tier vs item 自身 category/tier
    print()
    print("=" * 80)
    print("[R3] 物品 unlock 機器 tier vs 物品自身聲明 tier")
    print("=" * 80)
    for item_id, unlock_machine in sorted(item_unlock.items()):
        i_t = item_tier.get(item_id, None)
        # unlock can reference a machine or item
        m_t = machine_tier.get(unlock_machine)
        if m_t is not None and i_t is not None:
            if m_t > i_t:
                msg = f"  ⚠ {item_id}: 聲明 {tn(i_t)} 但 unlock 需要 [{unlock_machine}]({tn(m_t)})"
                violations.append(("R3", item_id, msg))
                print(msg)

    # [R4] machine.unlock 依賴鏈的 tier 合理性
    print()
    print("=" * 80)
    print("[R4] 機器 unlock 依賴鏈 tier 檢查")
    print("=" * 80)
    for m_id, prereqs in sorted(machine_unlock.items()):
        m_t = machine_tier.get(m_id)
        if m_t is None:
            continue
        for prereq in prereqs:
            prereq = prereq.strip()
            # prereq can be a machine or item
            p_t = machine_tier.get(prereq) or item_tier.get(prereq)
            if p_t is not None and p_t > m_t:
                msg = f"  ⚠ {m_id}({tn(m_t)}): unlock 依賴 [{prereq}]({tn(p_t)}) — 先決條件比自身更高階"
                violations.append(("R4", m_id, msg))
                print(msg)

    # [R5] 深度查驗：配方 input → 最早產出機器 tier 分析
    # 找出實際上無法在當前 tier 取得的材料（考慮完整生產鏈）
    print()
    print("=" * 80)
    print("[R5] 生產鏈可達性問題：材料需要更高階機器才能取得")
    print("=" * 80)
    for r_id, r_data in sorted(all_recipes.items()):
        machine_id = r_data.get("machine", "")
        inputs = r_data.get("inputs", [])
        if not isinstance(inputs, list):
            inputs = [inputs]
        m_t = machine_tier.get(machine_id)
        if m_t is None:
            continue

        output = r_data.get("output", "")
        for inp in inputs:
            if is_vanilla(inp):
                continue
            # Find the earliest producing recipe for this input
            producers = item_produced_by.get(inp, [])
            if not producers:
                producers = item_produced_by.get(inp.lower(), [])
            if not producers:
                continue  # already flagged in R2
            earliest_prod_tier = min((pt for _, _, pt in producers if pt is not None), default=None)
            if earliest_prod_tier is not None and earliest_prod_tier > m_t:
                prod_machines = [(rm, rt) for _, rm, rt in producers]
                msg = (f"  ⚠ {r_id}: 輸入 [{inp}] 最早由 {prod_machines} 生產({tn(earliest_prod_tier)})，"
                       f"但配方在 [{machine_id}]({tn(m_t)}) 上 → 玩家需要先造更高階機器才能得到材料")
                violations.append(("R5", r_id, msg))
                print(msg)

    # ── Phase 5: Summary ──
    print()
    print("=" * 80)
    print("審計摘要")
    print("=" * 80)

    by_rule = defaultdict(list)
    for rule, entity, msg in violations:
        by_rule[rule].append((entity, msg))

    total = len(violations)
    if total == 0:
        print("✅ 沒有發現任何 tier 違規！")
    else:
        print(f"🔴 共發現 {total} 個潛在問題：")
        for rule in sorted(by_rule.keys()):
            items_list = by_rule[rule]
            print(f"  [{rule}] {len(items_list)} 項")

    # ── Phase 6: Material reachability report ──
    print()
    print("=" * 80)
    print("材料可達性報告（各物品最早可取得的 tier）")
    print("=" * 80)

    unreachable_items = []
    for item_id in sorted(all_items.keys()):
        if item_id in reachable_tier:
            continue
        if is_vanilla(item_id):
            continue
        # Check if it has any production recipe
        suppliers = item_produced_by.get(item_id, [])
        unlock = all_items[item_id].get("unlock", "initial")
        unreachable_items.append((item_id, unlock, len(suppliers)))

    if unreachable_items:
        print(f"\n⚠ {len(unreachable_items)} 個科技物品無法通過任何配方生產:")
        for item_id, unlock, n_recipes in unreachable_items:
            print(f"  - {item_id} (unlock: {unlock}, 配方數: {n_recipes})")
    else:
        print("✅ 所有科技物品都有生產途徑")

    # Print reachable tiers for items that have explicit tier mismatch with reachability
    print()
    print("=" * 80)
    print("聲明 tier vs 實際可達 tier 差異")
    print("=" * 80)
    tier_mismatches = []
    for item_id, data in sorted(all_items.items()):
        declared = resolve_item_tier(data)
        actual = reachable_tier.get(item_id)
        if actual is not None and actual > declared:
            tier_mismatches.append((item_id, declared, actual, data.get("_source", "?")))

    if tier_mismatches:
        print(f"\n⚠ {len(tier_mismatches)} 個物品的聲明 tier 低於實際最早可生產 tier:")
        for item_id, decl, actual, src in tier_mismatches:
            print(f"  - {item_id}: 聲明 {tn(decl)} 但最早 {tn(actual)} 才能生產 [{src}]")
    else:
        print("✅ 所有物品的聲明 tier 與實際可達 tier 一致")

    return 1 if violations else 0


if __name__ == "__main__":
    sys.exit(main())
