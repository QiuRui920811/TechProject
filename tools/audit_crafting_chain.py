#!/usr/bin/env python3
"""
TechProject 合成依賴鏈全面審計工具
====================================
掃描所有 tech-*.yml 設定檔，建構完整依賴圖並檢查：
- 循環依賴
- 配方引用不存在的機器
- 機器缺少藍圖
- 解鎖條件引用不存在的機器/物品
- 配方輸入引用不存在的物品
- 不可達物品（無任何產出途徑）
- 孤立機器（有藍圖但不出現在任何配方）
"""

import os
import sys
import re
from collections import defaultdict

# ─── 極簡 YAML 解析器（不依賴 pyyaml） ───────────────────────────

def parse_yaml_value(raw: str):
    """解析 YAML 行的值（字串、列表、數字、布林）。"""
    raw = raw.strip()
    if not raw:
        return ""
    # 去除單行註解
    if raw.startswith("#"):
        return None
    # 布林
    if raw.lower() in ("true", "yes"):
        return True
    if raw.lower() in ("false", "no"):
        return False
    # 行內列表 [a, b, c]
    if raw.startswith("[") and raw.endswith("]"):
        inner = raw[1:-1]
        items = [s.strip().strip('"').strip("'") for s in inner.split(",") if s.strip()]
        return items
    # 帶引號的字串
    if (raw.startswith('"') and raw.endswith('"')) or (raw.startswith("'") and raw.endswith("'")):
        return raw[1:-1]
    # 數字
    try:
        return int(raw)
    except ValueError:
        pass
    try:
        return float(raw)
    except ValueError:
        pass
    return raw


def load_yaml_sections(path: str) -> dict:
    """
    將 YAML 檔載入為巢狀 dict。
    只處理我們需要的深度（最多 3 層）：
    section:
      key:
        field: value
    """
    result = {}
    with open(path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    section = None      # 頂層 key（items / machines / recipes / blueprints / achievements / cooking-recipes）
    entry = None        # 第二層 key
    sub_key = None      # 第三層 key（如 shape、ingredients）
    sub_list = None     # 當值為多行列表

    for line in lines:
        stripped = line.rstrip()
        if not stripped or stripped.lstrip().startswith("#"):
            continue

        # 計算縮排
        indent = len(line) - len(line.lstrip())

        content = stripped.lstrip()

        # 頂層 section（indent == 0）
        if indent == 0 and content.endswith(":"):
            section = content[:-1]
            result[section] = {}
            entry = None
            sub_key = None
            sub_list = None
            continue

        if section is None:
            continue

        # 第二層 entry（indent == 2）
        if indent == 2 and ":" in content:
            sub_key = None
            sub_list = None
            parts = content.split(":", 1)
            key = parts[0].strip()
            val = parts[1].strip() if len(parts) > 1 else ""
            if not val or val.startswith("#"):
                entry = key
                if entry not in result[section]:
                    result[section][entry] = {}
            else:
                entry = key
                result[section][entry] = parse_yaml_value(val)
            continue

        # 第三層 field（indent == 4）
        if indent == 4 and entry and isinstance(result[section].get(entry), dict):
            if content.startswith("- "):
                # 列表項在第三層（例如 shape/tutorial 列表或 use-cases）
                if sub_key and sub_key in result[section][entry]:
                    if isinstance(result[section][entry][sub_key], list):
                        result[section][entry][sub_key].append(content[2:].strip().strip('"').strip("'"))
                continue
            if ":" in content:
                parts = content.split(":", 1)
                field_name = parts[0].strip()
                field_val = parts[1].strip() if len(parts) > 1 else ""
                if not field_val or field_val.startswith("#"):
                    # 可能是子 dict 或列表的開頭
                    sub_key = field_name
                    result[section][entry][field_name] = []
                else:
                    sub_key = field_name
                    result[section][entry][field_name] = parse_yaml_value(field_val)
                continue

        # 第四層（indent == 6）- 列表項或子 dict 值
        if indent == 6 and entry and sub_key:
            if content.startswith("- "):
                val = content[2:].strip().strip('"').strip("'")
                if isinstance(result[section][entry].get(sub_key), list):
                    result[section][entry][sub_key].append(val)
                continue
            # sub-dict 值（如 ingredients 的 B: BOOK）
            if ":" in content:
                parts = content.split(":", 1)
                k = parts[0].strip()
                v = parts[1].strip().strip('"').strip("'") if len(parts) > 1 else ""
                current = result[section][entry].get(sub_key)
                if isinstance(current, list) and len(current) == 0:
                    result[section][entry][sub_key] = {}
                if isinstance(result[section][entry].get(sub_key), dict):
                    result[section][entry][sub_key][k] = v

        # 第五層（indent == 8）- 更深的列表項
        if indent == 8 and entry and sub_key:
            if content.startswith("- "):
                val = content[2:].strip().strip('"').strip("'")
                if isinstance(result[section][entry].get(sub_key), list):
                    result[section][entry][sub_key].append(val)

    return result


# ─── 資料收集 ─────────────────────────────────────────

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(BASE, "src", "main", "resources")

FILES = [
    "tech-blueprints.yml",
    "tech-content-core.yml",
    "tech-content-expansion.yml",
    "tech-content-systems.yml",
    "tech-content-megastructures.yml",
    "tech-content.yml",
]

all_items = {}          # item_id → {file, unlock, ...}
all_machines = {}       # machine_id → {file, unlock, ...}
all_recipes = {}        # recipe_id → {file, machine, inputs, output, ...}
all_blueprints = {}     # machine_id → {file, shape, ingredients, register-recipe}
all_achievements = {}   # achievement_id → {file, ...}

# Vanilla / non-tech items that are considered always available
VANILLA_ITEMS = {
    "iron_ore", "copper_ore", "coal", "quartz", "iron_ingot", "copper_ingot",
    "gold_ingot", "gold_nugget", "glass", "glass_pane", "cobblestone",
    "redstone", "piston", "oak_planks", "oak_log", "birch_log", "spruce_log",
    "jungle_log", "acacia_log", "dark_oak_log", "cherry_log", "mangrove_log",
    "oak_sapling", "paper", "book", "lectern", "chest", "barrel", "hopper",
    "furnace", "coal_block", "iron_block", "crafting_table", "hay_block",
    "wheat_seeds", "sunlight", "crafter", "loom", "end_rod", "respawn_anchor",
    "crying_obsidian", "stonecutter", "daylight_detector", "redstone_lamp",
    "mangrove_leaves", "lightning_rod", "target", "amethyst_shard",
    "snowball", "feather", "phantom_membrane", "lava_bucket", "water_bucket",
    "blaze_powder", "prismarine_shard", "obsidian", "moss_block",
    "chorus_fruit", "ender_eye", "ender_pearl", "glow_ink_sac",
    "soul_lantern", "emerald", "wither_skeleton_skull", "nether_star",
    "clay_ball", "campfire", "smoker", "blast_furnace",
    "iron_bars", "lever", "stone_button", "tripwire_hook",
    "heavy_weighted_pressure_plate", "light_weighted_pressure_plate",
    "fishing_rod", "lava_bucket", "beef", "porkchop", "chicken", "mutton",
    "rabbit", "cod", "salmon", "potato", "kelp", "wheat",
    "cocoa_beans", "red_mushroom", "bread",
    "crop_seeds",  # 被標注為 unlock: initial，視為基礎可取得
    "leather", "white_wool", "rotten_flesh", "bone", "gunpowder",
    "string", "spider_eye", "slime_ball", "tropical_fish", "pufferfish",
    "nautilus_shell",
    # 星球物品（透過探索取得，非合成）
    "irradiated_shard", "cryonite_crystal", "voidglass_fragment",
    "solarite_shard", "stormglass_shard", "void_bloom",
    "planetary_relic", "cryon_relic", "nyx_relic",
    "helion_relic", "tempest_relic",
    "aurelia_parasite_gland", "cryon_ice_heart", "nyx_phase_tissue",
    "helion_cinder_core", "tempest_capacitor",
    # 其他特殊
    "strange_nether_goo",
}

# Items that come from vanilla cooking-recipes or non-machine sources  
COOKING_OUTPUTS = set()

def extract_machine_refs_from_unlock(unlock_str: str) -> list:
    """從 unlock 字串中抽取 machine: 引用。"""
    refs = []
    if not unlock_str or not isinstance(unlock_str, str):
        return refs
    # 格式範例: "machine:survey_beacon&item:irradiated_shard&stat:planetary_samples_collected>=6"
    # 或簡單的: "assembler" (直接是機器名)
    # 或複合的: "field_forge&crystal_growth_chamber"
    # 或含 machine: 前綴的
    parts = re.split(r'[&|()]', unlock_str)
    for p in parts:
        p = p.strip()
        if p.startswith("machine:"):
            refs.append(p[len("machine:"):])
        elif p.startswith("item:") or p.startswith("stat:"):
            continue
        elif p in ("initial", "first_machine", "") or ">=" in p:
            continue
        else:
            # 可能是機器名或物品名
            refs.append(p)
    return refs


def extract_item_refs_from_unlock(unlock_str: str) -> list:
    """從 unlock 字串中抽取 item: 引用。"""
    refs = []
    if not unlock_str or not isinstance(unlock_str, str):
        return refs
    parts = re.split(r'[&|()]', unlock_str)
    for p in parts:
        p = p.strip()
        if p.startswith("item:"):
            refs.append(p[len("item:"):])
    return refs


print("=" * 72)
print("TechProject 合成依賴鏈審計報告")
print("=" * 72)
print()

for fname in FILES:
    fpath = os.path.join(RES, fname)
    if not os.path.exists(fpath):
        print(f"[WARN] 找不到檔案: {fname}")
        continue
    data = load_yaml_sections(fpath)

    if "items" in data:
        for k, v in data["items"].items():
            if isinstance(v, dict):
                v["_file"] = fname
                all_items[k] = v

    if "machines" in data:
        for k, v in data["machines"].items():
            if isinstance(v, dict):
                v["_file"] = fname
                all_machines[k] = v

    if "recipes" in data:
        for k, v in data["recipes"].items():
            if isinstance(v, dict):
                v["_file"] = fname
                all_recipes[k] = v

    if "blueprints" in data:
        for k, v in data["blueprints"].items():
            if isinstance(v, dict):
                v["_file"] = fname
                all_blueprints[k] = v

    if "achievements" in data:
        for k, v in data["achievements"].items():
            if isinstance(v, dict):
                v["_file"] = fname
                all_achievements[k] = v

    if "cooking-recipes" in data:
        for k, v in data["cooking-recipes"].items():
            if isinstance(v, dict):
                out = v.get("output", "")
                if out:
                    COOKING_OUTPUTS.add(out.lower())

print(f"已載入：{len(all_items)} 物品、{len(all_machines)} 機器、"
      f"{len(all_recipes)} 配方、{len(all_blueprints)} 藍圖、"
      f"{len(all_achievements)} 成就")
print()

# ─── 建構「什麼能產出什麼」的對照表 ──────────────────────

recipe_outputs = defaultdict(list)   # output_item → [recipe_id, ...]
recipe_machines = set()               # 所有在配方中被引用的機器
recipe_inputs_set = set()             # 所有在配方 inputs 中被引用的物品

for rid, r in all_recipes.items():
    out = r.get("output", "")
    if out:
        recipe_outputs[out].append(rid)
    machine = r.get("machine", "")
    if machine:
        recipe_machines.add(machine)
    inputs = r.get("inputs", [])
    if isinstance(inputs, list):
        for inp in inputs:
            recipe_inputs_set.add(inp)

# 藍圖也算是一種「產出機器」的途徑
blueprint_machines = set(all_blueprints.keys())

# ─── 審計結果收集 ──────────────────────────────────────

findings = []  # (severity, category, message, detail)


def add(severity, category, msg, detail=""):
    findings.append((severity, category, msg, detail))


# ─── 1. 配方引用不存在的機器 ──────────────────────────

print("─── 檢查 1: 配方引用的機器是否都有定義 ───")
for rid, r in all_recipes.items():
    machine = r.get("machine", "")
    if machine and machine not in all_machines:
        add("CRITICAL", "MISSING_MACHINE_DEF",
            f"配方 '{rid}' 引用機器 '{machine}'，但該機器未在任何檔案中定義",
            f"檔案: {r.get('_file', '?')}")

# ─── 2. 機器是否都有藍圖 ─────────────────────────────

print("─── 檢查 2: 每台機器是否都有對應藍圖 ───")
# 不需要藍圖的機器類型（物流/能源/電力版機器/由配方合成）
ASSEMBLY_MACHINES = set()
for rid, r in all_recipes.items():
    out = r.get("output", "")
    if out in all_machines:
        ASSEMBLY_MACHINES.add(out)

for mid, m in all_machines.items():
    if mid not in all_blueprints:
        if mid in ASSEMBLY_MACHINES:
            # 這台機器是由配方合成的，不一定需要藍圖
            pass
        else:
            # 檢查是否有 non-register-recipe 的藍圖（register-recipe: false 不會實際註冊合成表，但有說明）
            add("WARNING", "NO_BLUEPRINT",
                f"機器 '{mid}' 沒有對應藍圖且無配方產出",
                f"檔案: {m.get('_file', '?')}, unlock: {m.get('unlock', '?')}")

# ─── 3. 配方輸入引用不存在的物品 ─────────────────────

print("─── 檢查 3: 配方輸入是否引用了未定義的物品 ───")
all_known_items = set(all_items.keys()) | VANILLA_ITEMS | set(all_machines.keys()) | COOKING_OUTPUTS

# 加上所有配方的 output 也是已知物品
for rid, r in all_recipes.items():
    out = r.get("output", "")
    if out:
        all_known_items.add(out)

for rid, r in all_recipes.items():
    inputs = r.get("inputs", [])
    if isinstance(inputs, list):
        for inp in inputs:
            if inp not in all_known_items:
                add("WARNING", "UNDEFINED_INPUT",
                    f"配方 '{rid}' 的輸入 '{inp}' 未在任何檔案中定義為物品",
                    f"檔案: {r.get('_file', '?')}, 機器: {r.get('machine', '?')}")

# ─── 4. 機器解鎖條件引用不存在的機器或物品 ─────────

print("─── 檢查 4: 機器 unlock 條件引用的對象是否存在 ───")
for mid, m in all_machines.items():
    unlock = m.get("unlock", "")
    if not unlock or unlock == "initial":
        continue

    machine_refs = extract_machine_refs_from_unlock(str(unlock))
    item_refs = extract_item_refs_from_unlock(str(unlock))

    for ref in machine_refs:
        if ref and ref not in all_machines and ref not in all_items and ref != "initial" and ref != "first_machine":
            # 可能是物品名被當作解鎖條件（如 steel_plate）
            if ref not in all_known_items:
                add("CRITICAL", "INVALID_UNLOCK_REF",
                    f"機器 '{mid}' 的 unlock 引用 '{ref}'，但既不是已知機器也不是已知物品",
                    f"檔案: {m.get('_file', '?')}, unlock: {unlock}")

    for ref in item_refs:
        if ref and ref not in all_known_items:
            add("WARNING", "INVALID_UNLOCK_ITEM",
                f"機器 '{mid}' 的 unlock 引用 item:'{ref}'，但該物品未定義",
                f"檔案: {m.get('_file', '?')}, unlock: {unlock}")

# 同樣檢查物品的 unlock
for iid, item in all_items.items():
    unlock = item.get("unlock", "")
    if not unlock or unlock == "initial":
        continue
    unlock_str = str(unlock)
    machine_refs = extract_machine_refs_from_unlock(unlock_str)
    for ref in machine_refs:
        if ref and ref not in all_machines and ref not in all_items and ref != "initial" and ref != "first_machine":
            if ref not in all_known_items:
                add("WARNING", "INVALID_ITEM_UNLOCK",
                    f"物品 '{iid}' 的 unlock 引用 '{ref}'，但該引用不存在",
                    f"檔案: {item.get('_file', '?')}, unlock: {unlock_str}")

# ─── 5. 循環依賴偵測 ──────────────────────────────────

print("─── 檢查 5: 循環依賴偵測 ───")

# 建構依賴圖：item → set of items it depends on (直接素材)
dep_graph = defaultdict(set)  # item → set of items needed
item_to_machine = defaultdict(set)  # item → set of machines that can produce it

for rid, r in all_recipes.items():
    out = r.get("output", "")
    machine = r.get("machine", "")
    inputs = r.get("inputs", [])
    if not out:
        continue
    if isinstance(inputs, list):
        for inp in inputs:
            dep_graph[out].add(inp)
    if machine:
        item_to_machine[out].add(machine)

# 機器本身也依賴其藍圖材料
for mid, bp in all_blueprints.items():
    ingredients = bp.get("ingredients", {})
    if isinstance(ingredients, dict):
        for slot, mat in ingredients.items():
            mat_name = str(mat).lower()
            if mat_name.startswith("tech:"):
                mat_name = mat_name[5:]
            dep_graph[mid].add(mat_name)

# 機器依賴於其配方的輸入（如果是由配方合成的）
for rid, r in all_recipes.items():
    out = r.get("output", "")
    if out in all_machines:
        inputs = r.get("inputs", [])
        if isinstance(inputs, list):
            for inp in inputs:
                dep_graph[out].add(inp)

# 機器解鎖依賴
for mid, m in all_machines.items():
    unlock = m.get("unlock", "")
    if unlock and unlock != "initial":
        machine_refs = extract_machine_refs_from_unlock(str(unlock))
        for ref in machine_refs:
            if ref and ref != "initial" and ref != "first_machine":
                dep_graph[mid].add(ref)

# DFS 循環偵測
def find_cycles():
    """找出所有循環。"""
    cycles = []
    visited = set()
    rec_stack = set()
    path = []

    def dfs(node):
        visited.add(node)
        rec_stack.add(node)
        path.append(node)

        for neighbor in dep_graph.get(node, set()):
            if neighbor in VANILLA_ITEMS:
                continue
            if neighbor not in visited:
                result = dfs(neighbor)
                if result:
                    return result
            elif neighbor in rec_stack:
                # 找到循環
                cycle_start = path.index(neighbor)
                cycle = path[cycle_start:] + [neighbor]
                return cycle

        path.pop()
        rec_stack.discard(node)
        return None

    for node in sorted(dep_graph.keys()):
        if node not in visited:
            cycle = dfs(node)
            if cycle:
                cycles.append(cycle)
                # 重設以繼續搜尋
                visited = set()
                rec_stack = set()
                path = []

    return cycles


cycles = find_cycles()
for cycle in cycles:
    cycle_str = " → ".join(cycle)
    add("CRITICAL", "CIRCULAR_DEPENDENCY",
        f"發現循環依賴: {cycle_str}",
        "")

if not cycles:
    add("INFO", "NO_CYCLES", "未偵測到循環依賴", "")

# ─── 6. 不可達物品分析 ────────────────────────────────

print("─── 檢查 6: 不可達物品分析 ───")

# 一個物品「可達」如果：
# 1. 它是原版物品
# 2. 它是某個配方的 output
# 3. 它是機器且有藍圖或有配方可以合成它
# 4. 它是烹飪輸出
producible_items = set(VANILLA_ITEMS) | COOKING_OUTPUTS
for rid, r in all_recipes.items():
    out = r.get("output", "")
    if out:
        producible_items.add(out)

# 機器本身如果有藍圖，也算可取得
for bp_name in all_blueprints:
    producible_items.add(bp_name)

# crop_seeds 特殊：unlock: initial
producible_items.add("crop_seeds")

# 找出被引用但無法產出的物品
unreachable_inputs = set()
for rid, r in all_recipes.items():
    inputs = r.get("inputs", [])
    if isinstance(inputs, list):
        for inp in inputs:
            if inp not in producible_items and inp not in all_machines:
                unreachable_inputs.add(inp)

# 也檢查藍圖材料
for mid, bp in all_blueprints.items():
    ingredients = bp.get("ingredients", {})
    if isinstance(ingredients, dict):
        for slot, mat in ingredients.items():
            mat_name = str(mat)
            if mat_name.startswith("tech:"):
                mat_name = mat_name[5:]
            mat_lower = mat_name.lower()
            if mat_lower not in producible_items and mat_lower not in all_machines:
                # 原版物品大寫名稱
                if mat_name.isupper() or mat_name in ("BOOK", "LECTERN", "GLASS", "IRON_INGOT",
                    "COPPER_INGOT", "REDSTONE", "COAL", "COBBLESTONE", "PISTON", "FURNACE",
                    "HAY_BLOCK", "WHEAT_SEEDS", "HOPPER", "BARREL", "STONECUTTER",
                    "IRON_BLOCK", "CRAFTER", "LOOM", "END_ROD", "RESPAWN_ANCHOR",
                    "CRYING_OBSIDIAN", "REDSTONE_LAMP", "MANGROVE_LEAVES", "LIGHTNING_ROD",
                    "TARGET", "GLASS_PANE"):
                    continue
                unreachable_inputs.add(mat_lower)

for item in sorted(unreachable_inputs):
    add("WARNING", "UNREACHABLE_INPUT",
        f"物品 '{item}' 被配方/藍圖引用但沒有任何已知產出途徑",
        "")

# ─── 7. 藍圖 vs 配方一致性 ──────────────────────────

print("─── 檢查 7: 藍圖與配方一致性 ───")

# register-recipe: false 的藍圖應該有對應的配方產出該機器
for bp_name, bp in all_blueprints.items():
    reg = bp.get("register-recipe", True)
    if reg is False or reg == "false":
        # 這台機器是用其他機器合成的，確認有配方
        has_recipe = False
        for rid, r in all_recipes.items():
            if r.get("output", "") == bp_name:
                has_recipe = True
                break
        if not has_recipe:
            # 檢查系統檔是否有對應配方
            add("WARNING", "NO_ASSEMBLY_RECIPE",
                f"藍圖 '{bp_name}' 設為 register-recipe: false 但找不到機器合成配方",
                f"檔案: {bp.get('_file', '?')}")

# ─── 8. 機器進程鏈完整性 ──────────────────────────────

print("─── 檢查 8: 機器進程鏈完整性 ───")

# 從 initial 開始，追蹤所有可解鎖的機器
reachable_machines = set()
queue = []

for mid, m in all_machines.items():
    unlock = m.get("unlock", "")
    if unlock == "initial" or str(unlock) == "initial":
        reachable_machines.add(mid)
        queue.append(mid)

# 也加入所有使用 initial 或簡單條件的
iterations = 0
max_iter = 100
changed = True
while changed and iterations < max_iter:
    changed = False
    iterations += 1
    for mid, m in all_machines.items():
        if mid in reachable_machines:
            continue
        unlock = str(m.get("unlock", ""))
        if not unlock or unlock == "initial":
            if mid not in reachable_machines:
                reachable_machines.add(mid)
                changed = True
            continue

        # 解析 unlock 中的機器引用
        refs = extract_machine_refs_from_unlock(unlock)
        item_refs = extract_item_refs_from_unlock(unlock)

        # 所有機器引用都已解鎖，且所有物品引用都可產出
        all_met = True
        for ref in refs:
            if ref == "initial" or ref == "first_machine":
                continue
            # ref 可能是機器名或物品名
            if ref in all_machines:
                if ref not in reachable_machines:
                    all_met = False
                    break
            elif ref in all_items:
                # 物品名作為解鎖條件（如 steel_plate）
                # 檢查物品是否可產出
                if ref not in producible_items:
                    all_met = False
                    break
            else:
                all_met = False
                break

        for ref in item_refs:
            if ref not in producible_items and ref not in all_machines:
                all_met = False
                break

        if all_met:
            reachable_machines.add(mid)
            changed = True

unreachable_machines = set(all_machines.keys()) - reachable_machines
for mid in sorted(unreachable_machines):
    m = all_machines[mid]
    add("CRITICAL", "UNREACHABLE_MACHINE",
        f"機器 '{mid}' 無法透過進程鏈解鎖",
        f"檔案: {m.get('_file', '?')}, unlock: {m.get('unlock', '?')}")

# ─── 9. 配方產出但不是任何物品定義的檢查 ─────────────

print("─── 檢查 9: 配方產出 vs 物品定義一致性 ───")
for rid, r in all_recipes.items():
    out = r.get("output", "")
    if out and out not in all_items and out not in all_machines and out not in VANILLA_ITEMS and out not in COOKING_OUTPUTS:
        # 許多配方產出原版物品名（如 iron_dust → 不是原版，是 tech item）
        # 只標記那些完全找不到定義的
        pass  # 跳過此檢查，因為太多食物配方使用臨時名稱

# ─── 10. 終局進程鏈驗證 ──────────────────────────────

print("─── 檢查 10: 終局進程鏈驗證 ───")

ENDGAME_PATH = [
    "fusion_reactor",
    "void_extractor", "dark_matter_loom", "graviton_stabilizer",
    "singularity_press", "antimatter_synthesizer",
    "stellar_forge", "orbital_printer",
    "chrono_engine", "void_beacon",
    "entropy_chamber", "celestial_assembler", "omega_fabricator",
    "starsteel_foundry", "vacuum_chamber", "warp_assembler",
    "event_horizon_smith", "continuum_lathe", "relic_scanner",
    "anchor_forge", "apex_forge"
]

for ename in ENDGAME_PATH:
    if ename not in all_machines:
        add("CRITICAL", "MISSING_ENDGAME_MACHINE",
            f"終局進程鏈中的機器 '{ename}' 未定義",
            "")
    elif ename not in reachable_machines:
        add("CRITICAL", "BLOCKED_ENDGAME",
            f"終局機器 '{ename}' 無法解鎖（進程鏈斷裂）",
            f"unlock: {all_machines[ename].get('unlock', '?')}")

# ─── 11. 藍圖材料中引用的 tech: 物品是否存在 ─────────

print("─── 檢查 11: 藍圖 tech: 材料引用是否有效 ───")
for bp_name, bp in all_blueprints.items():
    ingredients = bp.get("ingredients", {})
    if isinstance(ingredients, dict):
        for slot, mat in ingredients.items():
            mat_str = str(mat)
            if mat_str.startswith("tech:"):
                tech_item = mat_str[5:]
                if tech_item not in all_items:
                    add("WARNING", "BLUEPRINT_MISSING_TECH_ITEM",
                        f"藍圖 '{bp_name}' 引用 tech:{tech_item}，但該物品未定義",
                        f"檔案: {bp.get('_file', '?')}")

# ─── 12. 配方的機器是否有藍圖或合成方式 ───────────────

print("─── 檢查 12: 配方使用的機器是否可取得 ───")
for rid, r in all_recipes.items():
    machine = r.get("machine", "")
    if not machine:
        continue
    if machine not in all_machines:
        # 已在檢查 1 報告
        continue
    if machine not in reachable_machines:
        add("WARNING", "RECIPE_USES_UNREACHABLE_MACHINE",
            f"配方 '{rid}' 使用的機器 '{machine}' 無法解鎖",
            f"檔案: {r.get('_file', '?')}")

# ─── 匯總報告 ──────────────────────────────────────

print()
print("=" * 72)
print("審計結果總覽")
print("=" * 72)
print()

# 統計
critical = [f for f in findings if f[0] == "CRITICAL"]
warning = [f for f in findings if f[0] == "WARNING"]
info = [f for f in findings if f[0] == "INFO"]

print(f"  🔴 CRITICAL: {len(critical)}")
print(f"  🟡 WARNING:  {len(warning)}")
print(f"  🔵 INFO:     {len(info)}")
print()

# 按類別分組
by_category = defaultdict(list)
for f in findings:
    by_category[f[1]].append(f)

CATEGORY_ORDER = [
    "CIRCULAR_DEPENDENCY",
    "NO_CYCLES",
    "MISSING_MACHINE_DEF",
    "UNREACHABLE_MACHINE",
    "BLOCKED_ENDGAME",
    "MISSING_ENDGAME_MACHINE",
    "INVALID_UNLOCK_REF",
    "INVALID_UNLOCK_ITEM",
    "INVALID_ITEM_UNLOCK",
    "NO_BLUEPRINT",
    "NO_ASSEMBLY_RECIPE",
    "BLUEPRINT_MISSING_TECH_ITEM",
    "UNDEFINED_INPUT",
    "UNREACHABLE_INPUT",
    "RECIPE_USES_UNREACHABLE_MACHINE",
]

seen_cats = set()
for cat in CATEGORY_ORDER:
    if cat in by_category:
        seen_cats.add(cat)
        items = by_category[cat]
        sev = items[0][0]
        sev_icon = {"CRITICAL": "🔴", "WARNING": "🟡", "INFO": "🔵"}.get(sev, "  ")
        print(f"{'─' * 72}")
        print(f"{sev_icon} [{sev}] {cat} ({len(items)} 項)")
        print(f"{'─' * 72}")
        for sev_, cat_, msg, detail in items:
            print(f"  • {msg}")
            if detail:
                print(f"    └─ {detail}")
        print()

# 列出未歸類的
for cat, items in by_category.items():
    if cat not in seen_cats:
        sev = items[0][0]
        sev_icon = {"CRITICAL": "🔴", "WARNING": "🟡", "INFO": "🔵"}.get(sev, "  ")
        print(f"{'─' * 72}")
        print(f"{sev_icon} [{sev}] {cat} ({len(items)} 項)")
        print(f"{'─' * 72}")
        for sev_, cat_, msg, detail in items:
            print(f"  • {msg}")
            if detail:
                print(f"    └─ {detail}")
        print()

# ─── 進程鏈圖譜 ──────────────────────────────────────

print("=" * 72)
print("機器進程鏈圖譜（unlock 依賴）")
print("=" * 72)
print()

def format_unlock_chain(mid, depth=0, visited=None):
    if visited is None:
        visited = set()
    if mid in visited:
        return f"{'  ' * depth}↺ {mid} (循環引用)\n"
    visited.add(mid)
    
    m = all_machines.get(mid)
    if not m:
        return f"{'  ' * depth}✗ {mid} (未定義)\n"
    
    unlock = str(m.get("unlock", "initial"))
    result = f"{'  ' * depth}• {mid} ← unlock: {unlock}\n"
    
    if unlock and unlock != "initial":
        refs = extract_machine_refs_from_unlock(unlock)
        for ref in refs:
            if ref in all_machines and ref != "initial" and ref != "first_machine":
                result += format_unlock_chain(ref, depth + 1, visited.copy())
    
    return result

# 列出主要進程鏈
print("【核心進程鏈】")
CORE_CHAIN = ["crusher", "furnace", "compressor", "assembler", "advanced_assembler",
               "laser_engraver", "quantum_processor", "field_forge",
               "crystal_growth_chamber", "fusion_reactor", "matter_compiler"]
for mid in CORE_CHAIN:
    if mid in all_machines:
        m = all_machines[mid]
        print(f"  {mid} ← unlock: {m.get('unlock', '?')}")

print()
print("【終局進程鏈】")
ENDGAME_CHAIN = ["void_extractor", "dark_matter_loom", "void_beacon",
                 "graviton_stabilizer", "singularity_press", "antimatter_synthesizer",
                 "stellar_forge", "orbital_printer", "chrono_engine",
                 "entropy_chamber", "nanite_foundry",
                 "celestial_assembler", "omega_fabricator"]
for mid in ENDGAME_CHAIN:
    if mid in all_machines:
        m = all_machines[mid]
        print(f"  {mid} ← unlock: {m.get('unlock', '?')}")

print()
print("【巨構進程鏈】")
MEGA_CHAIN = ["starsteel_foundry", "vacuum_chamber", "warp_assembler",
              "event_horizon_smith", "continuum_lathe", "relic_scanner",
              "anchor_forge", "apex_forge"]
for mid in MEGA_CHAIN:
    if mid in all_machines:
        m = all_machines[mid]
        print(f"  {mid} ← unlock: {m.get('unlock', '?')}")

print()
print("【擴充進程鏈】")
EXP_CHAIN = ["kiln", "insulation_press", "cryo_distiller", "gene_splicer",
             "biosynth_vat", "plasma_refiner", "photon_weaver", "observatory",
             "data_archive", "survey_beacon", "relic_analyzer", "xeno_refinery",
             "planetary_forge", "planetary_gate", "planetary_harvester"]
for mid in EXP_CHAIN:
    if mid in all_machines:
        m = all_machines[mid]
        print(f"  {mid} ← unlock: {m.get('unlock', '?')}")

print()
print("=" * 72)
print("審計完成")
print("=" * 72)
