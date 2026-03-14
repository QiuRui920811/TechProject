#!/usr/bin/env python3
"""Cross-reference audit for tech-content YAML files."""
import yaml
import os
import sys

BASE = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'resources')

# Known vanilla / non-custom items that don't need definition
VANILLA_ITEMS = {
    'iron_ore', 'copper_ore', 'coal', 'quartz', 'iron_ingot', 'copper_ingot',
    'oak_planks', 'bamboo', 'glass', 'paper', 'bone_meal', 'redstone',
    'blaze_powder', 'snowball', 'amethyst_shard', 'sunlight', 'chest',
    'oak_log', 'birch_log', 'spruce_log', 'jungle_log', 'acacia_log',
    'dark_oak_log', 'cherry_log', 'mangrove_log', 'oak_sapling',
    'leather', 'beef', 'white_wool', 'mutton', 'porkchop', 'chicken',
    'feather', 'rotten_flesh', 'bone', 'gunpowder', 'string', 'spider_eye',
    'slime_ball', 'cod', 'salmon', 'tropical_fish', 'pufferfish',
    'nautilus_shell', 'junk', 'clay_ball', 'energy', 'iron_ingot',
    'glass_panel',  # this IS defined as a tech item
}

def load(fname):
    path = os.path.join(BASE, fname)
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    # Fix known YAML issue: structure-preview indented under unlock value
    # Convert "unlock: value\n      structure-preview:" to "unlock: value\n    structure-preview:"
    import re
    content = re.sub(
        r'^(\s+unlock:\s+[^\n]+)\n(\s+)(structure-preview:)',
        lambda m: m.group(1) + '\n' + ' ' * 4 + m.group(3),
        content,
        flags=re.MULTILINE
    )
    try:
        return yaml.safe_load(content)
    except yaml.YAMLError as e:
        print(f"YAML ERROR in {fname}: {e}")
        return None

files_to_load = [
    'tech-content-core.yml',
    'tech-content-systems.yml',
    'tech-content-expansion.yml',
    'tech-content-megastructures.yml',
    'tech-content.yml',
    'tech-guides_zh_tw.yml',
    'tech-blueprints.yml',
]

data = {}
for fname in files_to_load:
    data[fname] = load(fname)

# Collect all item IDs, machine IDs, recipe IDs across all content files
all_items = {}  # id -> source_file
all_machines = {}  # id -> source_file
all_recipes = {}  # id -> {machine, inputs, output, source_file}

content_files = [
    'tech-content-core.yml',
    'tech-content-systems.yml',
    'tech-content-expansion.yml',
    'tech-content-megastructures.yml',
    'tech-content.yml',
]

for fname in content_files:
    d = data[fname]
    if not d:
        continue
    if 'items' in d and d['items']:
        for item_id in d['items']:
            if item_id in all_items:
                print(f"[DUPLICATE ITEM] '{item_id}' defined in both {all_items[item_id]} and {fname}")
            all_items[item_id] = fname
    if 'machines' in d and d['machines']:
        for machine_id in d['machines']:
            if machine_id in all_machines:
                print(f"[DUPLICATE MACHINE] '{machine_id}' defined in both {all_machines[machine_id]} and {fname}")
            all_machines[machine_id] = fname
    if 'recipes' in d and d['recipes']:
        for recipe_id, recipe_data in d['recipes'].items():
            if recipe_id in all_recipes:
                print(f"[DUPLICATE RECIPE] '{recipe_id}' defined in both {all_recipes[recipe_id]['source_file']} and {fname}")
            all_recipes[recipe_id] = {
                'machine': recipe_data.get('machine'),
                'inputs': recipe_data.get('inputs', []),
                'output': recipe_data.get('output'),
                'source_file': fname,
            }

# Combined known IDs (items + machines, since machines can also be recipe outputs)
all_known_ids = set(all_items.keys()) | set(all_machines.keys())

print("=" * 80)
print("INVENTORY COUNTS PER FILE")
print("=" * 80)

for fname in content_files:
    d = data[fname]
    if not d:
        print(f"\n{fname}: EMPTY or None")
        continue
    items_count = len(d.get('items', {}) or {})
    machines_count = len(d.get('machines', {}) or {})
    recipes_count = len(d.get('recipes', {}) or {})
    achievements_count = len(d.get('achievements', {}) or {})
    print(f"\n{fname}:")
    print(f"  Items:        {items_count}")
    print(f"  Machines:     {machines_count}")
    print(f"  Recipes:      {recipes_count}")
    print(f"  Achievements: {achievements_count}")

print(f"\n--- TOTALS ---")
print(f"  Total Items:    {len(all_items)}")
print(f"  Total Machines: {len(all_machines)}")
print(f"  Total Recipes:  {len(all_recipes)}")

print("\n" + "=" * 80)
print("CROSS-REFERENCE ISSUES")
print("=" * 80)

issues = []

# 1. Recipe inputs referencing undefined items
print("\n--- Recipe inputs referencing undefined items (excluding vanilla) ---")
for recipe_id, rdata in sorted(all_recipes.items()):
    for inp in (rdata['inputs'] or []):
        if inp not in all_known_ids and inp not in VANILLA_ITEMS:
            msg = f"  [RECIPE INPUT] recipe '{recipe_id}' ({rdata['source_file']}) uses input '{inp}' — NOT DEFINED anywhere"
            issues.append(msg)
            print(msg)

# 2. Recipe outputs referencing undefined items/machines
print("\n--- Recipe outputs referencing undefined items/machines ---")
for recipe_id, rdata in sorted(all_recipes.items()):
    out = rdata.get('output')
    if out and out not in all_known_ids and out not in VANILLA_ITEMS:
        msg = f"  [RECIPE OUTPUT] recipe '{recipe_id}' ({rdata['source_file']}) produces '{out}' — NOT DEFINED anywhere"
        issues.append(msg)
        print(msg)

# 3. Recipe machine references
print("\n--- Recipe machine references not defined ---")
for recipe_id, rdata in sorted(all_recipes.items()):
    machine = rdata.get('machine')
    if machine and machine not in all_machines:
        msg = f"  [RECIPE MACHINE] recipe '{recipe_id}' ({rdata['source_file']}) uses machine '{machine}' — NOT DEFINED"
        issues.append(msg)
        print(msg)

# 4. Machine definition inputs/outputs referencing undefined items
print("\n--- Machine inputs/outputs referencing undefined items ---")
for fname in content_files:
    d = data[fname]
    if not d or 'machines' not in d or not d['machines']:
        continue
    for machine_id, mdata in d['machines'].items():
        for inp in (mdata.get('inputs') or []):
            if inp not in all_known_ids and inp not in VANILLA_ITEMS:
                msg = f"  [MACHINE INPUT] machine '{machine_id}' ({fname}) lists input '{inp}' — NOT DEFINED"
                issues.append(msg)
                print(msg)
        for out in (mdata.get('outputs') or []):
            if out not in all_known_ids and out not in VANILLA_ITEMS:
                msg = f"  [MACHINE OUTPUT] machine '{machine_id}' ({fname}) lists output '{out}' — NOT DEFINED"
                issues.append(msg)
                print(msg)

# 5. Item unlock fields referencing machines/items not defined
print("\n--- Item unlock fields referencing undefined machines/items ---")
for fname in content_files:
    d = data[fname]
    if not d or 'items' not in d or not d['items']:
        continue
    for item_id, idata in d['items'].items():
        unlock = idata.get('unlock')
        if not unlock or unlock in ('initial',):
            continue
        # Parse unlock field: can be "machine_id", "machine:xxx&item:yyy", etc.
        # Split by & for compound conditions
        parts = unlock.replace('(', '').replace(')', '').split('&')
        for part in parts:
            part = part.strip()
            # Handle | alternatives
            alternatives = part.split('|')
            for alt in alternatives:
                alt = alt.strip()
                if alt.startswith('machine:'):
                    ref = alt[len('machine:'):]
                    if ref not in all_machines:
                        msg = f"  [ITEM UNLOCK] item '{item_id}' ({fname}) unlock references machine '{ref}' — NOT DEFINED"
                        issues.append(msg)
                        print(msg)
                elif alt.startswith('item:'):
                    ref = alt[len('item:'):]
                    if ref not in all_known_ids:
                        msg = f"  [ITEM UNLOCK] item '{item_id}' ({fname}) unlock references item '{ref}' — NOT DEFINED"
                        issues.append(msg)
                        print(msg)
                elif alt.startswith('stat:'):
                    pass  # stat conditions are fine
                else:
                    # Direct machine/item reference (no prefix)
                    if alt not in all_machines and alt not in all_items and alt not in ('initial',):
                        # Could be either a machine or item reference
                        msg = f"  [ITEM UNLOCK] item '{item_id}' ({fname}) unlock references '{alt}' — NOT DEFINED as machine or item"
                        issues.append(msg)
                        print(msg)

# 6. Machine unlock fields referencing undefined machines/items
print("\n--- Machine unlock fields referencing undefined machines/items ---")
for fname in content_files:
    d = data[fname]
    if not d or 'machines' not in d or not d['machines']:
        continue
    for machine_id, mdata in d['machines'].items():
        unlock = mdata.get('unlock')
        if not unlock or unlock in ('initial',):
            continue
        unlock_str = str(unlock)
        parts = unlock_str.replace('(', '').replace(')', '').split('&')
        for part in parts:
            part = part.strip()
            alternatives = part.split('|')
            for alt in alternatives:
                alt = alt.strip()
                if alt.startswith('machine:'):
                    ref = alt[len('machine:'):]
                    if ref not in all_machines:
                        msg = f"  [MACHINE UNLOCK] machine '{machine_id}' ({fname}) unlock references machine '{ref}' — NOT DEFINED"
                        issues.append(msg)
                        print(msg)
                elif alt.startswith('item:'):
                    ref = alt[len('item:'):]
                    if ref not in all_known_ids:
                        msg = f"  [MACHINE UNLOCK] machine '{machine_id}' ({fname}) unlock references item '{ref}' — NOT DEFINED"
                        issues.append(msg)
                        print(msg)
                elif alt.startswith('stat:'):
                    pass
                else:
                    if alt not in all_machines and alt not in all_items and alt not in ('initial',):
                        msg = f"  [MACHINE UNLOCK] machine '{machine_id}' ({fname}) unlock references '{alt}' — NOT DEFINED"
                        issues.append(msg)
                        print(msg)

# 7. Structure-preview references
print("\n--- Structure-preview map referencing undefined machines ---")
for fname in content_files:
    d = data[fname]
    if not d or 'machines' not in d or not d['machines']:
        continue
    for machine_id, mdata in d['machines'].items():
        sp = mdata.get('structure-preview')
        if not sp or not isinstance(sp, dict):
            continue
        smap = sp.get('map', {})
        if not smap:
            continue
        for key, val in smap.items():
            if isinstance(val, str) and val.startswith('machine:'):
                ref = val[len('machine:'):]
                if ref not in all_machines:
                    msg = f"  [STRUCTURE MAP] machine '{machine_id}' ({fname}) structure-preview references machine '{ref}' — NOT DEFINED"
                    issues.append(msg)
                    print(msg)

# 8. Blueprint recipe references
print("\n--- Blueprint references ---")
bp_data = data['tech-blueprints.yml']
if bp_data and 'blueprints' in bp_data:
    for bp_id, bp in bp_data['blueprints'].items():
        # Check if blueprint corresponds to a machine or item
        if bp_id not in all_machines and bp_id not in all_items:
            msg = f"  [BLUEPRINT] blueprint '{bp_id}' does NOT correspond to any defined machine or item"
            issues.append(msg)
            print(msg)

# 9. Guides referencing machines/items
print("\n--- Guide mentions check ---")
guides_data = data['tech-guides_zh_tw.yml']
# Just check that each guide is valid; we can look for mentions of machines/items in their text
# This is less critical since guides are free-text

# 10. Check for items that are recipe outputs but have no recipe producing them
print("\n--- Items with no recipe producing them ---")
all_recipe_outputs = set()
for rdata in all_recipes.values():
    out = rdata.get('output')
    if out:
        all_recipe_outputs.add(out)

# Also check items that are only obtained via vanilla means or drops
items_without_recipe = []
for item_id, source in sorted(all_items.items()):
    if item_id not in all_recipe_outputs:
        items_without_recipe.append((item_id, source))

if items_without_recipe:
    print(f"  (Total: {len(items_without_recipe)} items with no recipe producing them)")
    for item_id, source in items_without_recipe:
        print(f"    '{item_id}' ({source})")

# 11. Check for machines that are recipe outputs but defined as machines (OK) vs items
print("\n--- Machines with no recipe producing them ---")
machines_without_recipe = []
for machine_id, source in sorted(all_machines.items()):
    if machine_id not in all_recipe_outputs:
        machines_without_recipe.append((machine_id, source))

if machines_without_recipe:
    print(f"  (Total: {len(machines_without_recipe)} machines with no recipe producing them)")
    for machine_id, source in machines_without_recipe:
        # Check blueprints
        has_blueprint = bp_data and 'blueprints' in bp_data and machine_id in bp_data['blueprints']
        bp_note = " [HAS BLUEPRINT]" if has_blueprint else ""
        print(f"    '{machine_id}' ({source}){bp_note}")

# 12. Check for recipe outputs that produce a machine which is also represented in blueprints
print("\n--- Recipes producing items that are actually machines (cross-category) ---")
for recipe_id, rdata in sorted(all_recipes.items()):
    out = rdata.get('output')
    if out and out in all_machines and out not in all_items:
        # This is fine — recipe outputs can be machines
        pass

# Summary
print("\n" + "=" * 80)
print(f"TOTAL ISSUES FOUND: {len(issues)}")
print("=" * 80)
