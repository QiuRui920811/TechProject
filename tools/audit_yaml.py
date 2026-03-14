#!/usr/bin/env python3
"""Comprehensive YAML audit for TechProject plugin."""
import yaml
import sys
import os
from collections import defaultdict

BASE = "/rui/fs/大廳/plugins/科技專案/src/main/resources"
FILES = [
    "tech-content-core.yml",
    "tech-content-expansion.yml",
    "tech-content-systems.yml",
    "tech-content-megastructures.yml",
    "tech-content.yml",
    "tech-blueprints.yml",
]

# Known vanilla Minecraft items (common ones)
VANILLA_ITEMS = {
    "iron_ore", "copper_ore", "coal", "quartz", "glass", "oak_planks",
    "paper", "bone_meal", "redstone", "iron_ingot", "bamboo",
    "blaze_powder", "sunlight", "crop_seeds", "clay_ball",
    "iron_ore", "copper_ore", "coal", "quartz", "diamond",
    "gold_ingot", "emerald", "lapis_lazuli", "netherite_ingot",
    "oak_log", "birch_log", "spruce_log", "jungle_log",
    "acacia_log", "dark_oak_log", "cherry_log", "mangrove_log",
    "oak_sapling", "leather", "beef", "white_wool", "mutton",
    "porkchop", "chicken", "feather", "rotten_flesh", "bone",
    "gunpowder", "string", "spider_eye", "slime_ball",
    "cod", "salmon", "tropical_fish", "pufferfish", "nautilus_shell",
    "junk", "chest", "lava_bucket",
}

def load_all():
    data = {}
    for f in FILES:
        path = os.path.join(BASE, f)
        if os.path.exists(path):
            with open(path) as fh:
                data[f] = yaml.safe_load(fh)
        else:
            print(f"WARNING: File not found: {path}")
    return data

def find_line_numbers(filepath, key_to_find):
    """Find line number of a YAML key."""
    results = {}
    with open(os.path.join(BASE, filepath)) as f:
        for i, line in enumerate(f, 1):
            stripped = line.strip()
            if stripped and not stripped.startswith('#'):
                # Top-level section or item key
                if ':' in stripped:
                    k = stripped.split(':')[0].strip()
                    if k == key_to_find:
                        results[k] = i
    return results

def get_line_for_key(filepath, section, key):
    """Get approximate line number for a key under a section."""
    in_section = False
    indent_level = None
    with open(os.path.join(BASE, filepath)) as f:
        for i, line in enumerate(f, 1):
            stripped = line.rstrip()
            if not stripped or stripped.lstrip().startswith('#'):
                continue
            curr_indent = len(stripped) - len(stripped.lstrip())
            
            if curr_indent == 0 and stripped.startswith(section + ':'):
                in_section = True
                indent_level = 2  # expect items at indent 2
                continue
            if in_section and curr_indent == 0 and not stripped.startswith(' '):
                in_section = False
                continue
            if in_section and curr_indent == indent_level:
                k = stripped.lstrip().split(':')[0].strip()
                if k == key:
                    return i
    return None

def build_line_index(filepath):
    """Build index of section -> key -> line_number."""
    index = {}
    current_section = None
    current_indent = None
    
    with open(os.path.join(BASE, filepath)) as f:
        for i, line in enumerate(f, 1):
            stripped = line.rstrip()
            if not stripped or stripped.lstrip().startswith('#'):
                continue
            curr_indent = len(stripped) - len(stripped.lstrip())
            
            if curr_indent == 0 and ':' in stripped and not stripped.startswith(' '):
                current_section = stripped.split(':')[0].strip()
                if current_section not in index:
                    index[current_section] = {}
                continue
            
            if current_section and curr_indent == 2 and ':' in stripped:
                k = stripped.lstrip().split(':')[0].strip()
                if k not in ('display-name', 'category', 'icon', 'description',
                             'unlock', 'tier', 'block', 'energy-per-tick', 'effect',
                             'energy', 'machine', 'output', 'guide', 'inputs',
                             'outputs', 'item-model', 'nexo-id', 'head-texture',
                             'item-class', 'visual-tier', 'acquisition-mode',
                             'family', 'role', 'system-group', 'machine-archetype',
                             'structure-preview', 'reward-xp', 'reward-tokens', 'hint',
                             'shape', 'ingredients', 'tutorial', 'placement',
                             'register-recipe', 'ingredient-lines',
                             'title', 'rows', 'map'):
                    index[current_section][k] = i
    return index

def collect_all_items(data):
    """Collect all item IDs from all files."""
    items = {}  # id -> (file, category)
    for fname, content in data.items():
        if content and 'items' in content and isinstance(content['items'], dict):
            for item_id, item_data in content['items'].items():
                cat = item_data.get('category', 'UNKNOWN') if isinstance(item_data, dict) else 'UNKNOWN'
                tier = item_data.get('tier', None) if isinstance(item_data, dict) else None
                items[item_id] = {'file': fname, 'category': cat, 'tier': tier, 'data': item_data}
    return items

def collect_all_machines(data):
    """Collect all machine IDs from all files."""
    machines = {}
    for fname, content in data.items():
        if content and 'machines' in content and isinstance(content['machines'], dict):
            for mid, mdata in content['machines'].items():
                cat = mdata.get('category', 'UNKNOWN') if isinstance(mdata, dict) else 'UNKNOWN'
                tier = mdata.get('tier', None) if isinstance(mdata, dict) else None
                machines[mid] = {'file': fname, 'category': cat, 'tier': tier, 'data': mdata}
    return machines

def collect_all_recipes(data):
    """Collect all recipe IDs from all files."""
    recipes = {}
    for fname, content in data.items():
        if content and 'recipes' in content and isinstance(content['recipes'], dict):
            for rid, rdata in content['recipes'].items():
                if isinstance(rdata, dict):
                    recipes[rid] = {'file': fname, 'data': rdata}
    return recipes

def parse_unlock(unlock_str):
    """Parse unlock string, returns list of (type, id) tuples."""
    if not unlock_str or unlock_str == 'initial':
        return []
    refs = []
    # Split by & and |
    parts = unlock_str.replace('&', '|').split('|')
    for part in parts:
        part = part.strip().strip('(').strip(')')
        if part.startswith('machine:'):
            refs.append(('machine', part[8:]))
        elif part.startswith('item:'):
            refs.append(('item', part[5:]))
        elif part.startswith('stat:'):
            continue  # stat conditions, skip
        else:
            # Could be either item or machine
            refs.append(('ambiguous', part))
    return refs

def main():
    print("=" * 80)
    print("TechProject YAML Comprehensive Audit Report")
    print("=" * 80)
    
    data = load_all()
    
    # Build line indexes
    line_indexes = {}
    for fname in FILES:
        path = os.path.join(BASE, fname)
        if os.path.exists(path):
            line_indexes[fname] = build_line_index(fname)
    
    all_items = collect_all_items(data)
    all_machines = collect_all_machines(data)
    all_recipes = collect_all_recipes(data)
    
    all_known_ids = set(all_items.keys()) | set(all_machines.keys()) | VANILLA_ITEMS
    
    print(f"\nTotal items: {len(all_items)}")
    print(f"Total machines: {len(all_machines)}")
    print(f"Total recipes: {len(all_recipes)}")
    
    # ========== A. Recipe Cross-References ==========
    print("\n" + "=" * 80)
    print("A. RECIPE CROSS-REFERENCES — Invalid item references in recipes")
    print("=" * 80)
    
    issues_a = []
    for rid, rinfo in all_recipes.items():
        rdata = rinfo['data']
        fname = rinfo['file']
        
        # Check inputs
        inputs = rdata.get('inputs', [])
        if isinstance(inputs, list):
            for inp in inputs:
                if inp not in all_known_ids:
                    line = line_indexes.get(fname, {}).get('recipes', {}).get(rid, '?')
                    issues_a.append(f"  [{fname}:{line}] Recipe '{rid}' input '{inp}' — NOT FOUND in any items/machines")
        
        # Check output
        output = rdata.get('output', '')
        if output and output not in all_known_ids:
            line = line_indexes.get(fname, {}).get('recipes', {}).get(rid, '?')
            issues_a.append(f"  [{fname}:{line}] Recipe '{rid}' output '{output}' — NOT FOUND in any items/machines")
    
    if issues_a:
        for iss in issues_a:
            print(iss)
    else:
        print("  No issues found.")
    print(f"  Total: {len(issues_a)} issues")
    
    # ========== B. Machine References ==========
    print("\n" + "=" * 80)
    print("B. MACHINE REFERENCES — Invalid machine references in recipes")
    print("=" * 80)
    
    issues_b = []
    for rid, rinfo in all_recipes.items():
        rdata = rinfo['data']
        fname = rinfo['file']
        machine = rdata.get('machine', '')
        if machine and machine not in all_machines:
            line = line_indexes.get(fname, {}).get('recipes', {}).get(rid, '?')
            issues_b.append(f"  [{fname}:{line}] Recipe '{rid}' references machine '{machine}' — NOT FOUND")
    
    if issues_b:
        for iss in issues_b:
            print(iss)
    else:
        print("  No issues found.")
    print(f"  Total: {len(issues_b)} issues")
    
    # ========== C. Unlock Chain ==========
    print("\n" + "=" * 80)
    print("C. UNLOCK CHAIN — Invalid unlock references and circular chains")
    print("=" * 80)
    
    issues_c = []
    unlock_graph = {}  # id -> [dependencies]
    
    # Check items
    for item_id, iinfo in all_items.items():
        idata = iinfo['data']
        if not isinstance(idata, dict):
            continue
        unlock = idata.get('unlock', '')
        if not unlock or unlock == 'initial':
            continue
        
        refs = parse_unlock(str(unlock))
        deps = []
        for ref_type, ref_id in refs:
            if ref_type == 'machine':
                if ref_id not in all_machines:
                    line = line_indexes.get(iinfo['file'], {}).get('items', {}).get(item_id, '?')
                    issues_c.append(f"  [{iinfo['file']}:{line}] Item '{item_id}' unlock references machine '{ref_id}' — NOT FOUND")
                deps.append(ref_id)
            elif ref_type == 'item':
                if ref_id not in all_items:
                    line = line_indexes.get(iinfo['file'], {}).get('items', {}).get(item_id, '?')
                    issues_c.append(f"  [{iinfo['file']}:{line}] Item '{item_id}' unlock references item '{ref_id}' — NOT FOUND")
                deps.append(ref_id)
            else:  # ambiguous
                if ref_id not in all_items and ref_id not in all_machines:
                    line = line_indexes.get(iinfo['file'], {}).get('items', {}).get(item_id, '?')
                    issues_c.append(f"  [{iinfo['file']}:{line}] Item '{item_id}' unlock references '{ref_id}' — NOT FOUND (as item or machine)")
                deps.append(ref_id)
        unlock_graph[item_id] = deps
    
    # Check machines
    for mid, minfo in all_machines.items():
        mdata = minfo['data']
        if not isinstance(mdata, dict):
            continue
        unlock = mdata.get('unlock', '')
        if not unlock or unlock == 'initial':
            continue
        
        refs = parse_unlock(str(unlock))
        deps = []
        for ref_type, ref_id in refs:
            if ref_type == 'machine':
                if ref_id not in all_machines:
                    line = line_indexes.get(minfo['file'], {}).get('machines', {}).get(mid, '?')
                    issues_c.append(f"  [{minfo['file']}:{line}] Machine '{mid}' unlock references machine '{ref_id}' — NOT FOUND")
                deps.append(ref_id)
            elif ref_type == 'item':
                if ref_id not in all_items:
                    line = line_indexes.get(minfo['file'], {}).get('machines', {}).get(mid, '?')
                    issues_c.append(f"  [{minfo['file']}:{line}] Machine '{mid}' unlock references item '{ref_id}' — NOT FOUND")
                deps.append(ref_id)
            else:
                if ref_id not in all_items and ref_id not in all_machines:
                    line = line_indexes.get(minfo['file'], {}).get('machines', {}).get(mid, '?')
                    issues_c.append(f"  [{minfo['file']}:{line}] Machine '{mid}' unlock references '{ref_id}' — NOT FOUND (as item or machine)")
                deps.append(ref_id)
        unlock_graph[mid] = deps
    
    # Check for circular dependencies
    def find_cycle(node, visited, path):
        if node in path:
            cycle_start = path.index(node)
            return path[cycle_start:] + [node]
        if node in visited:
            return None
        visited.add(node)
        path.append(node)
        for dep in unlock_graph.get(node, []):
            cycle = find_cycle(dep, visited, path)
            if cycle:
                return cycle
        path.pop()
        return None
    
    cycles_found = set()
    for node in unlock_graph:
        cycle = find_cycle(node, set(), [])
        if cycle:
            cycle_key = tuple(sorted(cycle[:-1]))
            if cycle_key not in cycles_found:
                cycles_found.add(cycle_key)
                issues_c.append(f"  CIRCULAR UNLOCK CHAIN: {' → '.join(cycle)}")
    
    # Check for items that can never be unlocked
    # (items whose unlock references something that also can't be unlocked)
    # Simple check: items referencing non-existent things (already covered above)
    
    if issues_c:
        for iss in issues_c:
            print(iss)
    else:
        print("  No issues found.")
    print(f"  Total: {len(issues_c)} issues")
    
    # ========== D. Duplicate Recipes ==========
    print("\n" + "=" * 80)
    print("D. DUPLICATE / CONFLICTING RECIPES")
    print("=" * 80)
    
    issues_d = []
    recipe_signatures = defaultdict(list)  # (machine, frozenset(inputs)) -> [(rid, output, file)]
    
    for rid, rinfo in all_recipes.items():
        rdata = rinfo['data']
        machine = rdata.get('machine', '')
        inputs = rdata.get('inputs', [])
        output = rdata.get('output', '')
        
        if isinstance(inputs, list):
            sig = (machine, tuple(sorted(inputs)))
            recipe_signatures[sig].append((rid, output, rinfo['file']))
    
    for sig, entries in recipe_signatures.items():
        if len(entries) > 1:
            outputs = set(e[1] for e in entries)
            if len(outputs) > 1:
                issues_d.append(f"  CONFLICTING: Machine='{sig[0]}', Inputs={list(sig[1])}")
                for rid, out, fname in entries:
                    issues_d.append(f"    - [{fname}] '{rid}' → output: '{out}'")
            else:
                issues_d.append(f"  DUPLICATE: Machine='{sig[0]}', Inputs={list(sig[1])}")
                for rid, out, fname in entries:
                    issues_d.append(f"    - [{fname}] '{rid}' → output: '{out}'")
    
    if issues_d:
        for iss in issues_d:
            print(iss)
    else:
        print("  No issues found.")
    
    # ========== E. Orphan Items ==========
    print("\n" + "=" * 80)
    print("E. ORPHAN ITEMS — defined but never referenced anywhere")
    print("=" * 80)
    
    # Collect all referenced item IDs
    referenced_ids = set()
    
    # From recipes (inputs and outputs)
    for rid, rinfo in all_recipes.items():
        rdata = rinfo['data']
        for inp in rdata.get('inputs', []):
            referenced_ids.add(inp)
        out = rdata.get('output', '')
        if out:
            referenced_ids.add(out)
    
    # From machine inputs/outputs
    for mid, minfo in all_machines.items():
        mdata = minfo['data']
        if isinstance(mdata, dict):
            for inp in mdata.get('inputs', []):
                referenced_ids.add(inp)
            for out in mdata.get('outputs', []):
                referenced_ids.add(out)
    
    # From unlock references
    for item_id, iinfo in all_items.items():
        idata = iinfo['data']
        if isinstance(idata, dict):
            unlock = idata.get('unlock', '')
            if unlock:
                for ref_type, ref_id in parse_unlock(str(unlock)):
                    referenced_ids.add(ref_id)
    for mid, minfo in all_machines.items():
        mdata = minfo['data']
        if isinstance(mdata, dict):
            unlock = mdata.get('unlock', '')
            if unlock:
                for ref_type, ref_id in parse_unlock(str(unlock)):
                    referenced_ids.add(ref_id)
    
    # Blueprints ingredients
    for fname, content in data.items():
        if content and 'blueprints' in content:
            for bid, bdata in content['blueprints'].items():
                if isinstance(bdata, dict) and 'ingredients' in bdata:
                    for key, val in bdata['ingredients'].items():
                        if isinstance(val, str) and val.startswith('tech:'):
                            referenced_ids.add(val[5:])
    
    orphans = []
    # Skip special/meta items, focus on items that should be in production chains
    skip_categories = {'SPECIAL'}
    for item_id, iinfo in all_items.items():
        if item_id not in referenced_ids:
            cat = iinfo.get('category', '')
            orphans.append(f"  [{iinfo['file']}] '{item_id}' (category: {cat})")
    
    if orphans:
        for o in sorted(orphans):
            print(o)
    else:
        print("  No orphan items found.")
    print(f"  Total: {len(orphans)} orphan items")
    
    # ========== F. Category Consistency ==========
    print("\n" + "=" * 80)
    print("F. CATEGORY CONSISTENCY — Tier/category mismatches in unlock chains")
    print("=" * 80)
    
    CATEGORY_ORDER = {'BASIC': 0, 'INTERMEDIATE': 1, 'ADVANCED': 2, 'ENDGAME': 3, 'SPECIAL': -1}
    TIER_ORDER = {None: 0, 'TIER1': 1, 'TIER2': 2, 'TIER3': 3, 'TIER4': 4}
    
    issues_f = []
    
    def get_effective_level(thing_info):
        cat = thing_info.get('category', 'BASIC')
        tier = thing_info.get('tier', None)
        return CATEGORY_ORDER.get(cat, 0), TIER_ORDER.get(tier, 0)
    
    for item_id, iinfo in all_items.items():
        idata = iinfo['data']
        if not isinstance(idata, dict):
            continue
        unlock = idata.get('unlock', '')
        if not unlock or unlock == 'initial':
            continue
        
        my_cat = iinfo['category']
        my_tier = iinfo['tier']
        my_level = get_effective_level(iinfo)
        
        refs = parse_unlock(str(unlock))
        for ref_type, ref_id in refs:
            dep_info = None
            if ref_type == 'machine' and ref_id in all_machines:
                dep_info = all_machines[ref_id]
            elif ref_type == 'item' and ref_id in all_items:
                dep_info = all_items[ref_id]
            elif ref_id in all_items:
                dep_info = all_items[ref_id]
            elif ref_id in all_machines:
                dep_info = all_machines[ref_id]
            
            if dep_info:
                dep_level = get_effective_level(dep_info)
                # A BASIC item should not require ADVANCED/ENDGAME
                if my_level[0] < dep_level[0] and my_level[0] >= 0:
                    issues_f.append(
                        f"  [{iinfo['file']}] Item '{item_id}' ({my_cat}) requires "
                        f"'{ref_id}' ({dep_info['category']}) — lower category requiring higher unlock!"
                    )
    
    for mid, minfo in all_machines.items():
        mdata = minfo['data']
        if not isinstance(mdata, dict):
            continue
        unlock = mdata.get('unlock', '')
        if not unlock or unlock == 'initial':
            continue
        
        my_cat = minfo['category']
        my_tier = minfo['tier']
        my_level = get_effective_level(minfo)
        
        refs = parse_unlock(str(unlock))
        for ref_type, ref_id in refs:
            dep_info = None
            if ref_type == 'machine' and ref_id in all_machines:
                dep_info = all_machines[ref_id]
            elif ref_type == 'item' and ref_id in all_items:
                dep_info = all_items[ref_id]
            elif ref_id in all_items:
                dep_info = all_items[ref_id]
            elif ref_id in all_machines:
                dep_info = all_machines[ref_id]
            
            if dep_info:
                dep_level = get_effective_level(dep_info)
                if my_level[0] < dep_level[0] and my_level[0] >= 0:
                    issues_f.append(
                        f"  [{minfo['file']}] Machine '{mid}' ({my_cat}) requires "
                        f"'{ref_id}' ({dep_info['category']}) — lower category requiring higher unlock!"
                    )
    
    if issues_f:
        for iss in issues_f:
            print(iss)
    else:
        print("  No issues found.")
    print(f"  Total: {len(issues_f)} issues")
    
    # ========== G. Missing Recipe Coverage ==========
    print("\n" + "=" * 80)
    print("G. MISSING RECIPE COVERAGE — Machine outputs with no recipe")
    print("=" * 80)
    
    issues_g = []
    
    # Build set of (machine_id, output_id) from recipes
    recipe_machine_outputs = defaultdict(set)
    for rid, rinfo in all_recipes.items():
        rdata = rinfo['data']
        machine = rdata.get('machine', '')
        output = rdata.get('output', '')
        if machine and output:
            recipe_machine_outputs[machine].add(output)
    
    for mid, minfo in all_machines.items():
        mdata = minfo['data']
        if not isinstance(mdata, dict):
            continue
        declared_outputs = mdata.get('outputs', [])
        if not declared_outputs:
            continue
        
        for out in declared_outputs:
            if out not in recipe_machine_outputs.get(mid, set()):
                # Check if it's a vanilla item (these might be auto-generated)
                if out in VANILLA_ITEMS:
                    continue
                issues_g.append(f"  [{minfo['file']}] Machine '{mid}' declares output '{out}' but no recipe produces it via this machine")
    
    if issues_g:
        for iss in sorted(issues_g):
            print(iss)
    else:
        print("  No issues found.")
    print(f"  Total: {len(issues_g)} issues")
    
    # ========== H. Energy Values ==========
    print("\n" + "=" * 80)
    print("H. ENERGY VALUES — Suspicious energy values in recipes")
    print("=" * 80)
    
    issues_h = []
    
    # Group recipes by machine to find outliers
    machine_energies = defaultdict(list)
    for rid, rinfo in all_recipes.items():
        rdata = rinfo['data']
        machine = rdata.get('machine', '')
        energy = rdata.get('energy', None)
        if machine and energy is not None:
            machine_energies[machine].append((rid, energy, rinfo['file']))
    
    # Check for zero energy
    for rid, rinfo in all_recipes.items():
        rdata = rinfo['data']
        energy = rdata.get('energy', None)
        if energy == 0:
            issues_h.append(f"  [{rinfo['file']}] Recipe '{rid}' has energy: 0")
    
    # Check for outliers within same machine
    for machine, entries in machine_energies.items():
        energies = [e for _, e, _ in entries]
        if len(energies) < 2:
            continue
        avg = sum(energies) / len(energies)
        for rid, energy, fname in entries:
            if avg > 0 and (energy > avg * 3 or energy < avg / 3):
                issues_h.append(
                    f"  [{fname}] Recipe '{rid}' (machine: {machine}) energy={energy} vs avg={avg:.1f} — OUTLIER"
                )
    
    # Check for machines with energy-per-tick: 0 that aren't passive/logistics
    passive_machines = {'solar_generator', 'energy_node', 'energy_cable', 'logistics_node',
                       'item_tube', 'storage_hub', 'filter_router', 'splitter_node',
                       'industrial_bus', 'trash_node', 'storm_turbine'}
    for mid, minfo in all_machines.items():
        mdata = minfo['data']
        if not isinstance(mdata, dict):
            continue
        ept = mdata.get('energy-per-tick', None)
        if ept == 0 and mid not in passive_machines:
            issues_h.append(f"  [{minfo['file']}] Machine '{mid}' has energy-per-tick: 0 (is this intentional?)")
    
    if issues_h:
        for iss in issues_h:
            print(iss)
    else:
        print("  No issues found.")
    print(f"  Total: {len(issues_h)} issues")
    
    # ========== EXTRA: Items referenced but not defined ==========
    print("\n" + "=" * 80)
    print("EXTRA: Items/machines referenced in recipes but NOT defined anywhere")
    print("=" * 80)
    
    undefined = set()
    for rid, rinfo in all_recipes.items():
        rdata = rinfo['data']
        for inp in rdata.get('inputs', []):
            if inp not in all_items and inp not in all_machines and inp not in VANILLA_ITEMS:
                undefined.add(inp)
        out = rdata.get('output', '')
        if out and out not in all_items and out not in all_machines and out not in VANILLA_ITEMS:
            undefined.add(out)
    
    for mid, minfo in all_machines.items():
        mdata = minfo['data']
        if isinstance(mdata, dict):
            for inp in mdata.get('inputs', []):
                if inp not in all_items and inp not in all_machines and inp not in VANILLA_ITEMS:
                    undefined.add(inp)
            for out in mdata.get('outputs', []):
                if out not in all_items and out not in all_machines and out not in VANILLA_ITEMS:
                    undefined.add(out)
    
    if undefined:
        for u in sorted(undefined):
            # Find where it's referenced
            refs = []
            for rid, rinfo in all_recipes.items():
                rdata = rinfo['data']
                if u in rdata.get('inputs', []) or u == rdata.get('output', ''):
                    refs.append(f"recipe:{rid}[{rinfo['file']}]")
            for mid, minfo in all_machines.items():
                mdata = minfo['data']
                if isinstance(mdata, dict):
                    if u in mdata.get('inputs', []) or u in mdata.get('outputs', []):
                        refs.append(f"machine:{mid}[{minfo['file']}]")
            print(f"  '{u}' — referenced in: {', '.join(refs[:5])}")
    else:
        print("  All references are valid.")
    print(f"  Total: {len(undefined)} undefined references")
    
    print("\n" + "=" * 80)
    print("AUDIT COMPLETE")
    print("=" * 80)

if __name__ == '__main__':
    main()
