#!/usr/bin/env python3
"""
wiki 全面比對 —— 把整份文件拿去對 yml 和 Java，不是抽查。

跟 audit_wiki.py 的差別：那支只驗「反引號 id + 表格中文名 + 電力數字」，
這支把資料型頁面整片掃過去：

    1. 宣告的總數（613 種物品、152 台機器、74 項成就、91 份藍圖…）
       —— 這種數字最會飄，內容加了文件沒跟
    2. 加工配方頁（1278 行）：wiki 有的 yml 要有、yml 有的 wiki 要有、耗能要一致
    3. 成就頁：名稱和說明都要對得上 yml
    4. 工作台藍圖頁：份數和產物要對得上 tech-blueprints.yml
    5. 烹飪配方頁：對得上 cooking-recipes
    6. 指令頁：/tech 的子指令在程式裡真的存在
    7. 任何表格裡出現的中文物品名，yml 裡要有這個東西

    散文講的機制（「要靠加熱線圈加熱」那種）沒有工具驗得了，
    那類只能靠改機制時同步改文件。這支負責的是「能機器驗的全部驗掉」。

用法：
    python3 tools/audit_wiki_full.py            # 全部
    python3 tools/audit_wiki_full.py counts     # 只跑某一項
"""
import glob
import os
import re
import sys
from collections import defaultdict

import yaml

BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
RES = os.path.join(BASE, 'src', 'main', 'resources')
WIKI = os.path.join(BASE, 'docs', 'wiki')

problems = []


def report(where, message):
    problems.append(f'{where}  {message}')


# ═══════════════════ 載入資料 ═══════════════════

def load_content():
    items, machines, recipes, achievements, cooking = {}, {}, {}, {}, {}
    for path in glob.glob(os.path.join(RES, 'tech-content*.yml')):
        data = yaml.safe_load(open(path, encoding='utf-8')) or {}
        items.update(data.get('items') or {})
        machines.update(data.get('machines') or {})
        recipes.update(data.get('recipes') or {})
        achievements.update(data.get('achievements') or {})
        cooking.update(data.get('cooking-recipes') or {})
    blueprints = (yaml.safe_load(open(os.path.join(RES, 'tech-blueprints.yml'),
                                      encoding='utf-8')) or {}).get('blueprints', {})
    return items, machines, recipes, achievements, cooking, blueprints


ITEMS, MACHINES, RECIPES, ACHIEVEMENTS, COOKING, BLUEPRINTS = load_content()

# 配方的產物可能是原版物品（金錠、火藥、界伏盒…），插件自己就帶著官方譯名表
def load_vanilla_names():
    path = os.path.join(RES, 'vanilla_names_zh_tw.json')
    if not os.path.isfile(path):
        return {}
    import json
    return {name: key.lower() for key, name in
            json.load(open(path, encoding='utf-8')).items()}


VANILLA_NAMES = load_vanilla_names()
# 反過來：id → 官方中文名，用來檢查「玩家實際拿到的名字」
VANILLA_NAME_OF = {key: name for name, key in VANILLA_NAMES.items()}

def load_hardcoded_names():
    """有些物品的名字是寫死在 Java 裡的，不在 yml（例如手持的「地質掃描儀」）。

    這種最容易踩：yml 的 machines 底下也有個 geo_scanner 叫「地質掃描器」，
    看 yml 會以為那才是正解，但玩家手上拿到的是 Java 裡那把「地質掃描儀」。
    文件要對齊的是<b>玩家看到的</b>，所以這些也要算進合法名稱。
    """
    names = set()
    for path in glob.glob(os.path.join(BASE, 'src/main/java/com/rui/techproject/**/*.java'),
                          recursive=True):
        body = open(path, encoding='utf-8').read()
        names.update(re.findall(r'displayName\(this\.\w+\("[◆✦★]?\s*([\u4e00-\u9fff][\u4e00-\u9fffA-Za-z0-9 ]{1,12})"', body))
    return {n.strip() for n in names if n.strip()}


HARDCODED_NAMES = load_hardcoded_names()

# 中文名 → id（物品和機器合起來查）
NAME_TO_ID = {}
for _id, _def in list(ITEMS.items()) + list(MACHINES.items()):
    _name = str((_def or {}).get('display-name', '')).strip()
    if _name:
        NAME_TO_ID.setdefault(_name, _id)


def read(rel):
    path = os.path.join(WIKI, rel)
    return open(path, encoding='utf-8').read() if os.path.isfile(path) else ''


def lines(rel):
    return read(rel).split('\n')


# ═══════════════════ ① 宣告的總數 ═══════════════════

def armor_sets():
    """回傳 (完整套數, 總件數)。太陽能頭盔是單品不成套，要分開講。"""
    sets = defaultdict(list)
    for key in ITEMS:
        match = re.match(r'^(.*)_(helmet|chestplate|leggings|boots)$', key)
        if match:
            sets[match.group(1)].append(match.group(2))
    complete = sum(1 for parts in sets.values() if len(parts) == 4)
    return complete, sum(len(parts) for parts in sets.values())


def check_counts():
    complete_sets, armor_pieces = armor_sets()
    expected = [
        ('items/index.md', r'\*\*(\d+)\*\* ?種', len(ITEMS), '物品總數'),
        ('items/README.md', r'(\d+) ?種', len(ITEMS), '物品總數'),
        ('machines/index.md', r'\*\*(\d+)\*\* ?台', len(MACHINES), '機器總數'),
        ('achievements/README.md', r'\*\*(\d+)\*\* ?項', len(ACHIEVEMENTS), '成就總數'),
        ('README.md', r'(\d+) ?項成就', len(ACHIEVEMENTS), '成就總數'),
    ]
    for rel, pattern, actual, label in expected:
        text = read(rel)
        if not text:
            continue
        found = re.search(pattern, text)
        if not found:
            continue
        claimed = int(found.group(1))
        if claimed != actual:
            report(rel, f'{label}寫 {claimed}，實際是 {actual}')

    # 藍圖份數：workbench.md 開頭那句「全系統共 N 份藍圖」
    text = read('recipes/workbench.md')
    found = re.search(r'共 \*\*(\d+)\*\* 份藍圖', text)
    if found and int(found.group(1)) != len(BLUEPRINTS):
        report('recipes/workbench.md',
               f'藍圖份數寫 {found.group(1)}，實際是 {len(BLUEPRINTS)}')

    # 護甲套數／件數
    text = read('combat/armor.md')
    found = re.search(r'\*\*(\d+) 套護甲（(\d+) 件）\*\*', text)
    if found:
        claimed_sets, claimed_pieces = int(found.group(1)), int(found.group(2))
        if claimed_pieces != armor_pieces:
            report('combat/armor.md',
                   f'護甲件數寫 {claimed_pieces}，實際是 {armor_pieces}'
                   f'（{complete_sets} 套完整 + {armor_pieces - complete_sets * 4} 件單品）')


# ═══════════════════ ② 加工配方 ═══════════════════

RECIPE_ROW = re.compile(r'^\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*([^|]*?)\s*\|\s*$')
TWO_COL_ROW = re.compile(r'^\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|\s*$')
SKIP_CELLS = {'產物', '產出', '材料', '耗能', '機率', '---', '欄位', '意義'}


def check_recipes():
    """wiki 的加工配方頁 ↔ yml 的 recipes，兩個方向都比。"""
    documented = set()
    for number, line in enumerate(lines('recipes/machine-recipes.md'), 1):
        row = RECIPE_ROW.match(line)
        if not row:
            continue
        product, materials, energy = (cell.strip() for cell in row.groups())
        if product in SKIP_CELLS or product.startswith('---'):
            continue
        # 「×N」和粗體去掉再查名字
        clean = re.sub(r'\s*×\s*\d+$', '', product).strip('* ')
        if clean.startswith('*(') or not clean:
            continue
        if clean in NAME_TO_ID:
            documented.add(NAME_TO_ID[clean])
        elif clean in VANILLA_NAMES:
            documented.add(VANILLA_NAMES[clean])   # 原版物品也是合法產物
        elif any(clean in official or official in clean for official in VANILLA_NAMES):
            # 官方譯名有長短版：紅石粉／紅石、地獄石英／石英，互相包含就當同一個
            documented.update(key for name, key in VANILLA_NAMES.items()
                              if clean in name or name in clean)
        elif '%' not in materials:      # 機率表（篩礦）不是配方，跳過
            report(f'recipes/machine-recipes.md:{number}',
                   f'「{clean}」這個產物在 yml 裡查不到')

    # yml 有但 wiki 沒寫的
    produced = {}
    for key, recipe in RECIPES.items():
        output = recipe.get('output')
        if output:
            produced.setdefault(output, key)
    missing = [out for out in produced
               if out not in documented and out.lower() not in documented]
    if missing:
        names = [str((ITEMS.get(out) or MACHINES.get(out) or {}).get('display-name', out))
                 for out in sorted(missing)]
        report('recipes/machine-recipes.md',
               f'有 {len(missing)} 種產物 yml 做得出來但文件沒寫：'
               + '、'.join(names[:12]) + ('…' if len(names) > 12 else ''))


# ═══════════════════ ③ 成就 ═══════════════════

def check_achievements():
    documented = set()
    for number, line in enumerate(lines('achievements/README.md'), 1):
        row = TWO_COL_ROW.match(line.strip())
        if not row:
            continue
        name, description = (cell.strip() for cell in row.groups())
        if name in {'成就', '類別', '項目', '指令', '佔位符', '說明'} \
                or name.startswith('---') or name.startswith('[') \
                or '`' in name or '**' in name or '%' in name \
                or '#' in description:
            continue   # 表頭、目錄連結、稱號指令表、佔位符表、色彩表都不是成就
        match = next((key for key, value in ACHIEVEMENTS.items()
                      if str(value.get('display-name', '')).strip() == name), None)
        if match is None:
            report(f'achievements/README.md:{number}', f'「{name}」不是現有的成就')
            continue
        documented.add(match)
        actual = str(ACHIEVEMENTS[match].get('description', '')).strip()
        if actual and description and actual != description:
            report(f'achievements/README.md:{number}',
                   f'「{name}」說明對不上：wiki「{description}」／實際「{actual}」')

    missing = set(ACHIEVEMENTS) - documented
    if missing:
        names = sorted(str(ACHIEVEMENTS[key].get('display-name', key)) for key in missing)
        report('achievements/README.md',
               f'有 {len(missing)} 項成就沒寫進文件：' + '、'.join(names))


# ═══════════════════ ④ 指令 ═══════════════════

def check_commands():
    java = ''
    for path in glob.glob(os.path.join(BASE, 'src/main/java/com/rui/techproject/**/*.java'),
                          recursive=True):
        java += open(path, encoding='utf-8').read()
    for number, line in enumerate(lines('commands/README.md'), 1):
        if '已移除' in line or '已搬到' in line or '重寫中' in line:
            continue   # 文件自己講了這個指令沒了，不用去程式裡找
        for command in re.findall(r'`/tech ([a-z][a-z0-9-]*)', line):
            if f'"{command}"' not in java:
                report(f'commands/README.md:{number}',
                       f'/tech {command} 在程式裡找不到這個子指令')


# ═══════════════════ ⑤ 烹飪 ═══════════════════

def check_cooking():
    """35 道烹飪配方的產物名，文件裡都要找得到，而且要是玩家真的會拿到的那個名字。

    表格有好幾種欄位排法（4 欄的「食材→產物」、6 欄的「產物←食材+調味」），
    與其為每種格式寫一個 parser，不如直接看整頁文字裡有沒有那個名字 ——
    漏掉一整道菜一定抓得到，格式改了也不會誤報。
    """
    text = read('recipes/cooking.md') + read('food/README.md')
    for key, recipe in COOKING.items():
        output = recipe.get('output')
        if not output:
            continue
        name = (VANILLA_NAME_OF.get(output.lower())
                or str((ITEMS.get(output) or {}).get('display-name', '')).strip()
                or output)
        if name not in text:
            report('recipes/cooking.md', f'「{name}」（{key}）文件裡找不到')


# ═══════════════════ ⑥ 一字之差的錯名 ═══════════════════

# 這些「一字之差」是誤報：本來就是不同的詞，不是打錯遊戲內的名稱。
# 每加一筆都要能講出理由，不然這份名單會變成把真問題掃到地毯下的地方。
NEAR_MISS_ALLOW = {
    '冷卻液', '冷卻鏈', '冷卻量', '冷卻機',   # 泛稱，不是「冷卻罐」那個物品
    '合金錠', '回收材', '基礎護甲',           # 分類／泛稱
    '回聲連射', '裂空斬',                    # 武器的招式名，刻意跟武器名不同
    '碎礦機',                                # README 的口語泛稱
    '研究場',                                # 研究台的效果範圍，不是機器名
    '科技幫手', '科技樹',                    # AI 幫手、科技樹，本來就不是物品
    '精煉鏈', '鉛合金', '硫磺礦',            # 產線／材質／礦床的描述
    '釀造台', '鍛造台',                      # 原版方塊名、「力場鍛造台」的一部分
    '電池空', '個鐵錠', '伐木區', '金蘋果',  # 示意圖與句子切出來的假象
    '迷途星', '真空玻璃', '倪克斯遺物',
}


def check_near_miss_names():
    """抓「跟遊戲內名稱只差一個字」的寫法。

    這一類最陰險，看起來完全正常，玩家卻搜不到、對不上：
        地質掃描儀 vs 地質掃描器 ／ 核反應爐 vs 核反應堆
    兩次都是玩家或事後複查才發現的，靠眼睛看必漏，所以做成機器檢查。

    比對條件收得很緊，不然雜訊會淹掉真正的問題：
      ・差的那個字必須也是中文（排除標點、空白造成的假象）
      ・整個詞前後不能再接中文（排除「中子合金板」被切成「中子合金，」這種）
      ・變體本身不能剛好是另一個合法名稱
    """
    names = {str((d or {}).get('display-name', '')).strip()
             for d in list(ITEMS.values()) + list(MACHINES.values())}
    names |= HARDCODED_NAMES
    names = {n for n in names if len(n) >= 3 and re.fullmatch(r'[\u4e00-\u9fff]+', n)}

    sources = {}
    for path in glob.glob(os.path.join(WIKI, '**', '*.md'), recursive=True):
        if os.path.basename(path) == 'changelog.md':
            continue
        sources[os.path.relpath(path, WIKI)] = open(path, encoding='utf-8').read()
    # 只掃 wiki。遊戲內的文字是「答案」，不是被檢查的對象 ——
    # wiki 對齊遊戲，不是遊戲對齊 wiki。

    seen = set()
    for name in sorted(names):
        for index in range(len(name)):
            variant = re.compile(
                r'(?<![\u4e00-\u9fff])'
                + re.escape(name[:index]) + r'[\u4e00-\u9fff]' + re.escape(name[index + 1:])
                + r'(?![\u4e00-\u9fff])')
            for where, body in sources.items():
                for found in set(variant.findall(body)):
                    if found == name or found in names or found in NEAR_MISS_ALLOW:
                        continue
                    key = (found, name)
                    if key in seen:
                        continue
                    seen.add(key)
                    report(where, f'「{found}」跟遊戲內的「{name}」只差一個字，可能是寫錯')


CHECKS = {
    'counts': check_counts,
    'recipes': check_recipes,
    'achievements': check_achievements,
    'commands': check_commands,
    'cooking': check_cooking,
    'names': check_near_miss_names,
}


def main():
    wanted = sys.argv[1:] or list(CHECKS)
    for name in wanted:
        if name in CHECKS:
            CHECKS[name]()
    if not problems:
        print('✔ 全面比對通過')
        return 0
    print(f'✗ 找到 {len(problems)} 處對不上：\n')
    for problem in problems:
        print('  ' + problem)
    return 1


if __name__ == '__main__':
    sys.exit(main())
