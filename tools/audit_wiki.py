#!/usr/bin/env python3
"""
拿 wiki 去對程式和設定檔，把「文件說的」和「實際跑的」對不起來的地方抓出來。

為什麼要有這支：
    先前幾次「掃 wiki」掃的都是用詞（去英文、去術語），
    從來沒有拿文件去對過資料，所以數字和名稱的錯一個都沒被抓到，
    最後是玩家在遊戲裡問出來的 —— 堆疊升級的表格寫「放 3 個 +1.5」，
    但程式寫死只吃 1 個；tier-3 說要靠加熱線圈加熱，但那東西根本沒有加熱功能。

    這種錯不該靠人一頁一頁看。能機器驗的就機器驗。

檢查項目：
    1. wiki 裡用反引號寫的 id（`blast_furnace`）在 yml 裡真的存在
    2. 機器表格的中文名跟 yml 的 display-name 一致
    3. 機器表格的耗電（N EU/秒）跟 yml 的 energy-per-tick 一致
    4. 物品表格的中文名跟 yml 一致

用法：
    python3 tools/audit_wiki.py          # 有問題就列出來，回傳碼 1
    python3 tools/audit_wiki.py --quiet  # 只回傳碼，給腳本用
"""
import glob
import os
import re
import sys

import yaml

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
RES = os.path.join(BASE, 'src', 'main', 'resources')
WIKI = os.path.join(BASE, 'docs', 'wiki')

# 這些 id 是原版的或外部系統的，不會出現在 tech-content 裡
IGNORE_IDS = {
    'stack_upgrade', 'speed_upgrade', 'efficiency_upgrade', 'range_upgrade',
}

# 表格的「表頭列」第一格會是這些字，那不是資料，別拿去比對
HEADER_CELLS = {
    'id', '中文名', '名稱', '屬性', '部件', '欄位', '項目', '指令', '參數',
    '分類', '類型', '產物', '機器', '物品', '效果', '說明', '英文 id', '英文ID',
    '內部 ID', '內部id', '內部 id', '英文名', '值', '數值', '來源', '用途',
}

# 裝備部位這種上下文很清楚的縮寫，不用跟完整名稱一模一樣
PART_WORDS = {'頭盔', '胸甲', '護腿', '戰靴', '靴子', '鞋子'}


def load_definitions():
    """把所有 tech-content*.yml 的 items / machines 讀成兩張表。"""
    items, machines = {}, {}
    for path in glob.glob(os.path.join(RES, 'tech-content*.yml')):
        data = yaml.safe_load(open(path, encoding='utf-8')) or {}
        for key, value in (data.get('items') or {}).items():
            items.setdefault(key, value)
        for key, value in (data.get('machines') or {}).items():
            machines.setdefault(key, value)
    return items, machines


# 有些數字不在 yml，是寫死在 Java 常數裡。這裡對照過去，
# 常數改了 wiki 沒跟著改一樣會被抓出來（不是白名單放行）。
CODE_BACKED_ENERGY = {
    'nuclear_reactor': 'NUCLEAR_BASE_GEN_PER_TICK',
}


def load_code_constants():
    """從 MachineService 撈 private static final long XXX = 123L; 這種常數。"""
    path = os.path.join(BASE, 'src', 'main', 'java', 'com', 'rui',
                        'techproject', 'service', 'MachineService.java')
    if not os.path.isfile(path):
        return {}
    text = open(path, encoding='utf-8').read()
    return {name: int(value) for name, value in
            re.findall(r'static final (?:long|int) ([A-Z_]+)\s*=\s*(\d+)[LI]?;', text)}


def load_hardcoded_names():
    """寫死在 Java 裡的物品名。yml 的 machines 也有 geo_scanner 叫「地質掃描器」，
    但玩家手上那把是 Java 建的「地質掃描儀」—— 同一個 id 兩個名字，
    文件對齊的是玩家看到的那個，兩邊都算合法。"""
    names = set()
    for path in glob.glob(os.path.join(BASE, 'src/main/java/com/rui/techproject/**/*.java'),
                          recursive=True):
        body = open(path, encoding='utf-8').read()
        names.update(re.findall(
            r'displayName\(this\.\w+\("[◆✦★]?\s*([\u4e00-\u9fff][\u4e00-\u9fffA-Za-z0-9 ]{1,12})"', body))
    return {n.strip() for n in names if n.strip()}


def load_enchant_ids():
    """特殊附魔是寫在 EnchantService 的 enum 裡，不在 yml。"""
    path = os.path.join(BASE, 'src', 'main', 'java', 'com', 'rui',
                        'techproject', 'service', 'EnchantService.java')
    if not os.path.isfile(path):
        return set()
    return set(re.findall(r'\("([a-z][a-z0-9_]+)",\s*"',
                          open(path, encoding='utf-8').read()))


def wiki_files():
    for path in glob.glob(os.path.join(WIKI, '**', '*.md'), recursive=True):
        # 更新日記記的是「當時」的數值，本來就會跟現在不一樣，不檢查
        if os.path.basename(path) == 'changelog.md':
            continue
        yield path


HARDCODED = set()

ID_PATTERN = re.compile(r'`([a-z][a-z0-9_]{2,})`')
ROW_PATTERN = re.compile(r'^\|([^|]+)\|\s*`([a-z][a-z0-9_]+)`\s*\|(.*)$')
ENERGY_PATTERN = re.compile(r'(\d+)\s*EU\s*/\s*秒')


def main():
    quiet = '--quiet' in sys.argv
    items, machines = load_definitions()
    known = set(items) | set(machines) | IGNORE_IDS | load_enchant_ids()
    global HARDCODED
    HARDCODED = load_hardcoded_names()
    constants = load_code_constants()
    problems = []

    for path in wiki_files():
        rel = os.path.relpath(path, BASE)
        for number, line in enumerate(open(path, encoding='utf-8'), 1):
            # ① 反引號裡的 id 存不存在
            for found in ID_PATTERN.findall(line):
                if found in known:
                    continue
                # 純英文單字（yaml、config…）不算 id，只抓有底線的
                if '_' not in found:
                    continue
                if found.endswith('_dye') or found.endswith('_wool') or found.endswith('_'):
                    continue   # 原版方塊/物品，或句子裡的半截字
                problems.append(f'{rel}:{number}  找不到這個 id：`{found}`')

            # ② ③ ④ 表格列：| 中文名 | `id` | … | N EU/秒 |
            row = ROW_PATTERN.match(line.strip())
            if not row:
                continue
            name = row.group(1).strip().strip('*').strip()
            ident = row.group(2)
            rest = row.group(3)

            if name in HEADER_CELLS:
                continue   # 表頭列，不是資料

            source = machines.get(ident) or items.get(ident)
            if source is None:
                continue

            actual_name = str(source.get('display-name', '')).strip()
            if name in HARDCODED:
                continue   # 這個名字是 Java 直接建的，玩家看到的就是它
            # 縮寫是可以的（套裝表格裡寫「頭盔」而不是「抗輻射頭盔」，上下文很清楚），
            # 只抓「真的講錯」的：兩邊互不包含，例如 地質掃描儀 vs 地質掃描器
            if name in PART_WORDS:
                continue   # 套裝表格裡的「戰靴」對上「抗輻射靴」，上下文很清楚
            if actual_name and name and name not in actual_name and actual_name not in name:
                problems.append(
                    f'{rel}:{number}  `{ident}` 的名字對不上：wiki 寫「{name}」、實際是「{actual_name}」')

            energy = ENERGY_PATTERN.search(rest)
            if energy and ident in machines:
                claimed = int(energy.group(1))
                # 同一個「N EU/秒」欄位，發電機寫的是發電量、加工機寫的是耗電量，
                # 表格標題不一定抓得到，所以兩個欄位對得上任一個就算過。
                per_tick = int(machines[ident].get('energy-per-tick', 0) or 0)
                generation = int(machines[ident].get('energy-generation', 0) or 0)
                allowed = {per_tick, generation}
                constant_name = CODE_BACKED_ENERGY.get(ident)
                if constant_name and constant_name in constants:
                    allowed.add(constants[constant_name])
                if claimed not in allowed:
                    problems.append(
                        f'{rel}:{number}  `{ident}` 的電力數字對不上：wiki 寫 {claimed}、'
                        f'實際耗電 {per_tick} / 發電 {generation}')

    if not problems:
        if not quiet:
            print('✔ wiki 跟設定檔對得起來')
        return 0

    if not quiet:
        print(f'✗ 找到 {len(problems)} 處對不上：\n')
        for problem in problems:
            print('  ' + problem)
        print('\n（更新日記不檢查——那本來就是歷史紀錄）')
    return 1


if __name__ == '__main__':
    sys.exit(main())
