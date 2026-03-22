#!/usr/bin/env python3
"""
TechProject Energy Pipeline — Nexo Connectable 簡潔管線 v4

設計理念：管線就是管線，乾淨的細管。
  - 管體截面：4×4 px（座標 6~10）
  - 接點小方塊：4×4×4（座標 6~10）
  - 無法蘭、無鉚釘、無螺栓
  - 兩張材質足夠：pipe_side（管壁）+ pipe_end（管截面/接頭）

Nexo connectable 變體：
  - default   → 獨立接頭（4×4 小方塊）
  - straight  → 直管 N↔S
  - left      → L 彎管 S→W
  - right     → L 彎管 S→E
  - inner     → T 三通 S+N+E
  - outer     → 死端 S

YAML 不手寫 variant key → Nexo 自動從 item_model 路徑衍生後綴。
每個 element 完整 6 面 → display entity 無破洞。
"""

import json
import struct
import zlib
from pathlib import Path

# ── 路徑常數 ──────────────────────────────────────────────
NEXO_ROOT    = Path("/media/ruiouo/d5339ede-13e7-49e2-8d9b-b6abc85649eb2/tech/plugins/Nexo")
TEXTURE_DIR  = NEXO_ROOT / "pack/assets/techproject/textures/block/pipeline"
MODEL_DIR    = NEXO_ROOT / "pack/assets/techproject/models/block/pipeline"
ITEM_DIR     = NEXO_ROOT / "pack/assets/techproject/items/pipeline"
ITEM_YML_DIR = NEXO_ROOT / "items"

# ── 色彩定義 (R, G, B, A) ────────────────────────────────
C_DARK       = (40, 42, 46, 255)     # 管壁暗面
C_MID        = (58, 62, 68, 255)     # 管壁中間色
C_STEEL      = (72, 78, 84, 255)     # 管壁主色
C_LIGHT      = (90, 96, 104, 255)    # 管壁亮面
C_HIGHLIGHT  = (110, 118, 128, 255)  # 高光邊緣
C_SHADOW     = (28, 30, 34, 255)     # 深陰影

C_CYAN_CORE  = (0, 220, 255, 255)    # 能量核心亮
C_CYAN_MID   = (0, 160, 200, 230)    # 能量核心中
C_CYAN_DIM   = (0, 100, 140, 180)    # 能量核心暗

C_TRANSPARENT = (0, 0, 0, 0)


# ── PNG 寫入器 ────────────────────────────────────────────

def write_png(path: Path, width: int, height: int, pixels: list):
    path.parent.mkdir(parents=True, exist_ok=True)
    def chunk(ctype, data):
        c = ctype + data
        return struct.pack('>I', len(data)) + c + struct.pack('>I', zlib.crc32(c) & 0xffffffff)
    raw = b''
    for y in range(height):
        raw += b'\x00'
        for x in range(width):
            r, g, b, a = pixels[y][x]
            raw += struct.pack('BBBB', r, g, b, a)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)))
        f.write(chunk(b'IDAT', zlib.compress(raw, 9)))
        f.write(chunk(b'IEND', b''))
    print(f"  [PNG]  {path.relative_to(NEXO_ROOT)}")


# ── 繪圖工具 ─────────────────────────────────────────────

def blank(w=16, h=16):
    return [[C_TRANSPARENT for _ in range(w)] for _ in range(h)]

def px(img, x, y, c):
    if 0 <= x < len(img[0]) and 0 <= y < len(img):
        img[y][x] = c

def rect(img, x1, y1, x2, y2, c):
    for yy in range(y1, y2 + 1):
        for xx in range(x1, x2 + 1):
            px(img, xx, yy, c)


# ── 2 張材質 ─────────────────────────────────────────────

def tex_pipe_side():
    """
    管體側面 16×16：填滿整張材質（模型幾何已限制形狀）。
    金屬漸層 + 中間能量流。
    """
    img = blank()
    for y in range(16):
        for x in range(16):
            # 上→下漸層：亮→暗
            if y < 2:
                px(img, x, y, C_HIGHLIGHT)
            elif y < 4:
                px(img, x, y, C_LIGHT)
            elif y < 7:
                px(img, x, y, C_STEEL)
            elif y < 9:
                # 能量流核心（中間 2 行）
                c = C_CYAN_CORE if x % 4 == 0 else (C_CYAN_MID if x % 2 == 0 else C_CYAN_DIM)
                px(img, x, y, c)
            elif y < 12:
                px(img, x, y, C_MID)
            elif y < 14:
                px(img, x, y, C_DARK)
            else:
                px(img, x, y, C_SHADOW)
    # 邊緣高光線 (頂行)
    for x in range(16):
        px(img, x, 0, C_SHADOW)
        px(img, x, 15, C_SHADOW)
    return img


def tex_pipe_end():
    """
    管體截面 16×16：填滿整張材質。
    金屬框 + 能量核心 + 四角高光。
    """
    img = blank()
    # 底色
    rect(img, 0, 0, 15, 15, C_STEEL)
    # 外框深色邊
    for i in range(16):
        px(img, i, 0, C_SHADOW); px(img, i, 15, C_SHADOW)
        px(img, 0, i, C_SHADOW); px(img, 15, i, C_SHADOW)
    # 亮面漸層（上邊 + 左邊）
    for i in range(1, 15):
        px(img, i, 1, C_HIGHLIGHT); px(img, i, 2, C_LIGHT)
        px(img, 1, i, C_HIGHLIGHT); px(img, 2, i, C_LIGHT)
    # 暗面漸層（下邊 + 右邊）
    for i in range(1, 15):
        px(img, i, 14, C_DARK); px(img, i, 13, C_MID)
        px(img, 14, i, C_DARK); px(img, 13, i, C_MID)
    # 能量核心 (中間 4×4)
    rect(img, 6, 6, 9, 9, C_CYAN_DIM)
    rect(img, 7, 7, 8, 8, C_CYAN_CORE)
    px(img, 6, 6, C_CYAN_MID); px(img, 9, 9, C_CYAN_MID)
    px(img, 6, 9, C_CYAN_DIM); px(img, 9, 6, C_CYAN_DIM)
    # 內框線
    for i in range(5, 11):
        px(img, i, 5, C_DARK); px(img, i, 10, C_LIGHT)
        px(img, 5, i, C_DARK); px(img, 10, i, C_LIGHT)
    return img


# ── JSON 模型建構器 ───────────────────────────────────────

TEX = {
    "side": "techproject:block/pipeline/pipe_side",
    "end":  "techproject:block/pipeline/pipe_end",
}

DISPLAY = {
    "gui":                   {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground":                {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed":                 {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}

OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}


def face(uv, tex):
    return {"uv": uv, "texture": tex}

def elem(name, frm, to, faces):
    return {"name": name, "from": frm, "to": to, "faces": faces}


# ── 管線元素 ──────────────────────────────────────────────

def junction_cube():
    """接點小方塊 4×4×4（座標 6~10），全面 #end 材質。"""
    f, t = [6, 6, 6], [10, 10, 10]
    return elem("junction", f, t, {
        "north": face([6, 6, 10, 10], "#end"),
        "south": face([6, 6, 10, 10], "#end"),
        "east":  face([6, 6, 10, 10], "#end"),
        "west":  face([6, 6, 10, 10], "#end"),
        "up":    face([6, 6, 10, 10], "#end"),
        "down":  face([6, 6, 10, 10], "#end"),
    })


def pipe_segment(direction):
    """
    一個方向的管段：4×4 截面，從接點邊緣（6 或 10）延伸到方塊邊（0 或 16）。
    完整 6 面。
    """
    opp = OPPOSITE[direction]

    configs = {
        "north": ([6, 6, 0],  [10, 10, 6]),
        "south": ([6, 6, 10], [10, 10, 16]),
        "east":  ([10, 6, 6], [16, 10, 10]),
        "west":  ([0, 6, 6],  [6, 10, 10]),
    }
    pf, pt = configs[direction]

    def uv(f_dir):
        x1, y1, z1 = pf
        x2, y2, z2 = pt
        if f_dir in ("up", "down"):
            return [x1, z1, x2, z2]
        elif f_dir in ("east", "west"):
            return [z1, y1, z2, y2]
        else:
            return [x1, y1, x2, y2]

    faces = {}
    for f_dir in ("north", "south", "east", "west", "up", "down"):
        if f_dir == direction:
            faces[f_dir] = face(uv(f_dir), "#end")
        elif f_dir == opp:
            faces[f_dir] = face(uv(f_dir), "#end")
        else:
            faces[f_dir] = face(uv(f_dir), "#side")

    return elem(f"pipe_{direction}", pf, pt, faces)


def build_model(elems):
    return {
        "textures": {
            "side": TEX["side"],
            "end":  TEX["end"],
        },
        "elements": elems,
        "display": DISPLAY,
    }


# ── 6 個連接變體 ─────────────────────────────────────────

VARIANT_ARMS = {
    "default":  ["north", "south", "east", "west"],
    "straight": ["north", "south"],
    "left":     ["south", "west"],
    "right":    ["south", "east"],
    "inner":    ["south", "north", "east"],
    "outer":    ["south"],
}


def build_variant(name):
    arms = VARIANT_ARMS[name]
    elems = [junction_cube()]
    for d in arms:
        elems.append(pipe_segment(d))
    return build_model(elems)


# ── Item model JSON ───────────────────────────────────────

def item_model_json(block_name):
    return {
        "model": {
            "type": "minecraft:model",
            "model": f"techproject:block/pipeline/{block_name}"
        }
    }


# ── Nexo YAML（不手寫 variant key）──────────────────────

NEXO_YAML = """\
tech_energy_pipeline:
  itemname: <gradient:#55FFFF:#4488AA>能量管線</gradient>
  Components:
    max_stack_size: 64
    item_model: techproject:pipeline/energy_pipeline
  Mechanics:
    furniture:
      restricted_rotation: VERY_STRICT
      hitbox:
        interactions:
          - 0,0,0 1,1
      connectable:
        type: ITEM_MODEL
"""


# ── 工具 ─────────────────────────────────────────────────

def write_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
    print(f"  [JSON] {path.relative_to(NEXO_ROOT)}")


def cleanup_old():
    """清理所有舊版產生的檔案"""
    old_files = []
    for d in ["north","south","east","west","up","down"]:
        old_files.append(MODEL_DIR / f"arm_{d}.json")
    old_files.append(MODEL_DIR / "center.json")
    for name in ["flange_side.png", "flange_end.png", "center_top.png"]:
        old_files.append(TEXTURE_DIR / name)
    n = 0
    for f in old_files:
        if f.exists():
            f.unlink()
            n += 1
            print(f"  [DEL]  {f.relative_to(NEXO_ROOT)}")
    print(f"  清理了 {n} 個舊檔案" if n else "  無需清理")


VARIANT_SUFFIXES = {
    "":          "default",
    "_straight": "straight",
    "_left":     "left",
    "_right":    "right",
    "_inner":    "inner",
    "_outer":    "outer",
}


# ── 主流程 ────────────────────────────────────────────────

def main():
    print("=" * 60)
    print("  TechProject Energy Pipeline — 簡潔細管 v4")
    print("=" * 60)

    print("\n[0/4] 清理舊版資產...")
    cleanup_old()

    print("\n[1/4] 生成材質 (2 張 16×16 PNG)...")
    write_png(TEXTURE_DIR / "pipe_side.png", 16, 16, tex_pipe_side())
    write_png(TEXTURE_DIR / "pipe_end.png",  16, 16, tex_pipe_end())

    print("\n[2/4] 生成 block model (6 個變體)...")
    for suffix, block_name in VARIANT_SUFFIXES.items():
        write_json(MODEL_DIR / f"{block_name}.json", build_variant(block_name))

    print("\n[3/4] 生成 item model (6 個)...")
    for suffix, block_name in VARIANT_SUFFIXES.items():
        write_json(ITEM_DIR / f"energy_pipeline{suffix}.json", item_model_json(block_name))

    print("\n[4/4] 生成 Nexo YAML...")
    yml = ITEM_YML_DIR / "techproject_pipeline.yml"
    yml.parent.mkdir(parents=True, exist_ok=True)
    with open(yml, 'w', encoding='utf-8') as f:
        f.write(NEXO_YAML)
    print(f"  [YAML] {yml.relative_to(NEXO_ROOT)}")

    print(f"""
{'='*60}
  v4 生成完成！
{'='*60}

  管線設計：4×4 截面細管（座標 6~10）
  材質 : 2 張（pipe_side + pipe_end）
  模型 : 6 個 block model（每 element 完整 6 面）
  物品 : 6 個 item model
  YAML : 無顯式 variant key（Nexo 自動 select）

  下一步：/nexo reload → 放置測試
""")


if __name__ == "__main__":
    main()
