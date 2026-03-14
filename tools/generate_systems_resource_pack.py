from __future__ import annotations

import json
import math
import re
import struct
import zipfile
import zlib
from dataclasses import dataclass
from pathlib import Path
from typing import Callable


ROOT = Path(__file__).resolve().parents[1]
PACK_ROOT = ROOT / "resourcepack" / "techproject_systems_pack"
PACK_ZIP = ROOT / "resourcepack" / "techproject_systems_pack.zip"


@dataclass(frozen=True)
class AssetDef:
	id: str
	material: str
	cmd: int
	kind: str
	parent: str
	symbol: str
	palette: tuple[tuple[int, int, int], tuple[int, int, int], tuple[int, int, int]]


TECH = "item"
MACHINE = "machine"

CYAN = ((20, 25, 35), (89, 214, 255), (148, 255, 233))
AMBER = ((27, 23, 18), (255, 193, 76), (255, 237, 140))
LIME = ((18, 27, 20), (110, 255, 145), (210, 255, 128))
PURPLE = ((24, 18, 33), (195, 126, 255), (255, 168, 255))
RED = ((34, 18, 20), (255, 102, 119), (255, 171, 126))
SLATE = ((17, 20, 28), (135, 153, 181), (219, 231, 255))
OBSIDIAN = ((10, 10, 14), (90, 92, 118), (166, 175, 213))
SKY = ((14, 23, 35), (110, 196, 255), (213, 242, 255))
GOLD = ((34, 24, 10), (255, 202, 87), (255, 241, 179))
ORANGE = ((38, 20, 10), (255, 145, 74), (255, 211, 151))
TEAL = ((12, 28, 30), (77, 233, 220), (190, 255, 243))
BLUE = ((12, 18, 38), (97, 142, 255), (196, 221, 255))
GREEN = ((15, 30, 18), (100, 207, 110), (204, 255, 188))
BROWN = ((36, 24, 16), (168, 120, 74), (226, 198, 162))
PALETTES = [CYAN, AMBER, LIME, PURPLE, RED, SKY, GOLD, TEAL, BLUE, GREEN, BROWN, SLATE, OBSIDIAN, ORANGE]

HUD_ASSETS: list[AssetDef] = [
	AssetDef("hud_book", "BOOK", 830201, TECH, "minecraft:item/generated", "guide_book", CYAN),
	AssetDef("hud_enchanted_book", "ENCHANTED_BOOK", 830202, TECH, "minecraft:item/generated", "enchanted_book", PURPLE),
	AssetDef("hud_writable_book", "WRITABLE_BOOK", 830203, TECH, "minecraft:item/generated", "write_book", AMBER),
	AssetDef("hud_knowledge_book", "KNOWLEDGE_BOOK", 830204, TECH, "minecraft:item/generated", "knowledge_book", SKY),
	AssetDef("hud_xp_bottle", "EXPERIENCE_BOTTLE", 830205, TECH, "minecraft:item/generated", "xp_bottle", LIME),
	AssetDef("hud_arrow", "ARROW", 830206, TECH, "minecraft:item/generated", "arrow_right", CYAN),
	AssetDef("hud_spectral_arrow", "SPECTRAL_ARROW", 830207, TECH, "minecraft:item/generated", "arrow_right", GOLD),
	AssetDef("hud_lightning_rod", "LIGHTNING_ROD", 830208, TECH, "minecraft:item/generated", "energy_rod", PURPLE),
	AssetDef("hud_crafting_table", "CRAFTING_TABLE", 830209, TECH, "minecraft:item/generated", "craft_chip", AMBER),
]

RESOURCE_FILES = [
	ROOT / "src" / "main" / "resources" / "tech-content-systems.yml",
	ROOT / "src" / "main" / "resources" / "tech-content.yml",
	ROOT / "src" / "main" / "resources" / "tech-content-expansion.yml",
	ROOT / "src" / "main" / "resources" / "tech-content-megastructures.yml",
]
REGISTRY_FILE = ROOT / "src" / "main" / "java" / "com" / "rui" / "techproject" / "service" / "TechRegistry.java"


def ensure(path: Path) -> None:
	path.mkdir(parents=True, exist_ok=True)


def chunk(tag: bytes, data: bytes) -> bytes:
	return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)


def write_png(path: Path, pixels: list[list[tuple[int, int, int, int]]]) -> None:
	height = len(pixels)
	width = len(pixels[0]) if height else 0
	raw = bytearray()
	for row in pixels:
		raw.append(0)
		for r, g, b, a in row:
			raw.extend((r, g, b, a))
	data = b"".join([
		b"\x89PNG\r\n\x1a\n",
		chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)),
		chunk(b"IDAT", zlib.compress(bytes(raw), 9)),
		chunk(b"IEND", b""),
	])
	path.write_bytes(data)


def blank(color: tuple[int, int, int]) -> list[list[tuple[int, int, int, int]]]:
	return [[(*color, 0) for _ in range(16)] for _ in range(16)]


def set_px(canvas, x, y, color, alpha=255):
	if 0 <= x < 16 and 0 <= y < 16:
		canvas[y][x] = (*color, alpha)


def rect(canvas, x1, y1, x2, y2, color, fill=False):
	for y in range(y1, y2 + 1):
		for x in range(x1, x2 + 1):
			if fill or y in (y1, y2) or x in (x1, x2):
				set_px(canvas, x, y, color)


def line(canvas, points, color):
	for x, y in points:
		set_px(canvas, x, y, color)


def glow_frame(canvas, base, glow, accent, machine=False):
	rect(canvas, 1, 1, 14, 14, glow)
	rect(canvas, 2, 2, 13, 13, base)
	rect(canvas, 3, 3, 12, 12, (0, 0, 0), fill=True)
	for x in range(4, 12, 2):
		set_px(canvas, x, 2, accent)
		set_px(canvas, x + 1, 13, accent)
	for y in range(4, 12, 2):
		set_px(canvas, 2, y, accent)
		set_px(canvas, 13, y + 1, accent)
	if machine:
		rect(canvas, 4, 4, 11, 11, base)
		rect(canvas, 5, 5, 10, 10, (8, 10, 16), fill=True)


def draw_drill(canvas, glow, accent):
	line(canvas, [(7, 3), (8, 3), (7, 4), (8, 4)], accent)
	rect(canvas, 5, 5, 10, 8, glow, fill=True)
	line(canvas, [(6, 9), (7, 10), (8, 11), (9, 12), (8, 12), (7, 13)], accent)
	line(canvas, [(5, 6), (4, 7), (5, 8)], accent)
	line(canvas, [(10, 6), (11, 7), (10, 8)], accent)


def draw_frame(canvas, glow, accent):
	rect(canvas, 4, 4, 11, 11, glow)
	rect(canvas, 5, 5, 10, 10, accent)
	rect(canvas, 6, 6, 9, 9, (0, 0, 0), fill=True)


def draw_matrix(canvas, glow, accent):
	for x in (5, 8, 11):
		for y in (5, 8, 11):
			rect(canvas, x - 1, y - 1, x, y, glow, fill=True)
	line(canvas, [(4, 8), (12, 8), (8, 4), (8, 12)], accent)


def draw_core(canvas, glow, accent):
	rect(canvas, 5, 5, 10, 10, glow)
	rect(canvas, 6, 6, 9, 9, accent, fill=True)
	line(canvas, [(8, 3), (8, 4), (8, 11), (8, 12), (3, 8), (4, 8), (11, 8), (12, 8)], glow)
	line(canvas, [(6, 5), (9, 5), (6, 10), (9, 10), (5, 6), (5, 9), (10, 6), (10, 9)], accent)
	set_px(canvas, 8, 8, (255, 255, 255))


def draw_rotor(canvas, glow, accent):
	rect(canvas, 7, 7, 8, 8, accent, fill=True)
	for points in [
		[(8, 3), (8, 4), (9, 5)],
		[(12, 8), (11, 8), (10, 9)],
		[(8, 12), (8, 11), (7, 10)],
		[(3, 8), (4, 8), (5, 7)],
	]:
		line(canvas, points, glow)


def draw_sensor(canvas, glow, accent):
	rect(canvas, 4, 4, 11, 11, glow)
	rect(canvas, 6, 6, 9, 9, (0, 0, 0), fill=True)
	line(canvas, [(3, 8), (12, 8), (8, 3), (8, 12)], accent)


def draw_chip(canvas, glow, accent):
	rect(canvas, 4, 4, 11, 11, glow, fill=True)
	rect(canvas, 5, 5, 10, 10, (12, 18, 24), fill=True)
	for i in range(5, 11):
		set_px(canvas, i, 3, accent)
		set_px(canvas, i, 12, accent)
		set_px(canvas, 3, i, accent)
		set_px(canvas, 12, i, accent)
	line(canvas, [(6, 6), (9, 6), (9, 9), (6, 9), (6, 6)], glow)
	line(canvas, [(5, 7), (7, 7), (7, 5), (8, 8), (10, 8), (10, 10)], accent)
	set_px(canvas, 8, 8, accent)


def draw_blade(canvas, glow, accent):
	line(canvas, [(5, 11), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5)], glow)
	line(canvas, [(4, 12), (5, 13), (6, 12)], accent)
	line(canvas, [(10, 4), (11, 3), (12, 4)], accent)


def draw_launcher(canvas, glow, accent):
	rect(canvas, 4, 6, 11, 9, glow, fill=True)
	line(canvas, [(3, 8), (12, 8), (11, 5), (12, 4)], accent)
	line(canvas, [(6, 10), (8, 12), (10, 10)], accent)


def draw_hook(canvas, glow, accent):
	line(canvas, [(5, 4), (6, 5), (7, 6), (8, 7), (9, 8), (9, 9), (8, 10)], glow)
	rect(canvas, 4, 3, 6, 5, accent, fill=True)
	line(canvas, [(8, 10), (8, 11), (7, 12)], accent)


def draw_dock(canvas, glow, accent):
	rect(canvas, 3, 9, 12, 11, glow, fill=True)
	line(canvas, [(4, 8), (6, 6), (8, 8), (10, 6), (12, 8)], accent)


def draw_crate(canvas, glow, accent):
	rect(canvas, 4, 4, 11, 11, glow, fill=True)
	line(canvas, [(4, 7), (11, 7), (7, 4), (7, 11)], accent)


def draw_filter(canvas, glow, accent):
	for i in range(4, 12):
		set_px(canvas, i, i, glow)
		set_px(canvas, 15 - i, i, glow)
	rect(canvas, 5, 5, 10, 10, accent)


def draw_bus(canvas, glow, accent):
	rect(canvas, 3, 6, 12, 9, glow, fill=True)
	line(canvas, [(5, 5), (5, 10), (10, 5), (10, 10)], accent)


def draw_splitter(canvas, glow, accent):
	line(canvas, [(8, 4), (8, 8), (5, 11), (8, 8), (11, 11)], glow)
	rect(canvas, 7, 7, 8, 8, accent, fill=True)


def draw_quarry_machine(canvas, glow, accent, stage):
	rect(canvas, 4, 4, 11, 11, glow)
	line(canvas, [(8, 5), (8, 6), (8, 7), (7, 8), (8, 9), (7, 10), (8, 11)], accent)
	if stage >= 2:
		line(canvas, [(5, 8), (6, 8), (10, 8), (11, 8)], accent)
	if stage >= 3:
		line(canvas, [(5, 5), (11, 5), (5, 11), (11, 11)], accent)


def draw_harvester(canvas, glow, accent):
	rect(canvas, 4, 8, 11, 11, glow, fill=True)
	line(canvas, [(5, 7), (6, 5), (7, 7), (8, 4), (9, 7), (10, 5)], accent)


def draw_vacuum(canvas, glow, accent):
	line(canvas, [(4, 4), (11, 4), (9, 7), (9, 9), (7, 12), (6, 12), (7, 9), (7, 7)], glow)
	rect(canvas, 6, 11, 8, 13, accent, fill=True)


def draw_turbine(canvas, glow, accent):
	draw_rotor(canvas, glow, accent)
	line(canvas, [(8, 12), (8, 13)], accent)


def draw_feller(canvas, glow, accent):
	rect(canvas, 6, 3, 9, 12, glow, fill=True)
	line(canvas, [(5, 6), (10, 6), (4, 9), (11, 9)], accent)


def draw_collector(canvas, glow, accent):
	rect(canvas, 4, 4, 11, 11, glow)
	line(canvas, [(8, 5), (8, 10), (5, 8), (10, 8)], accent)
	rect(canvas, 7, 7, 8, 8, accent, fill=True)


def draw_router(canvas, glow, accent):
	rect(canvas, 5, 5, 10, 10, glow)
	line(canvas, [(8, 3), (8, 5), (8, 10), (8, 12), (3, 8), (5, 8), (10, 8), (12, 8)], accent)


def draw_hub(canvas, glow, accent):
	rect(canvas, 3, 5, 12, 10, glow, fill=True)
	rect(canvas, 6, 6, 9, 9, accent)
	line(canvas, [(5, 8), (3, 8), (11, 8), (13, 8)], accent)


def draw_tech_book(canvas, glow, accent):
	rect(canvas, 4, 3, 11, 12, glow, fill=True)
	rect(canvas, 5, 4, 10, 11, accent)
	line(canvas, [(6, 4), (6, 11), (9, 4), (9, 11)], (24, 28, 36))
	line(canvas, [(7, 6), (8, 5), (9, 6), (8, 7), (8, 8), (8, 9)], glow)
	line(canvas, [(4, 4), (3, 5), (3, 10), (4, 11)], accent)


def draw_assembler(canvas, glow, accent):
	rect(canvas, 4, 4, 11, 11, glow, fill=True)
	rect(canvas, 5, 5, 10, 10, (10, 14, 22), fill=True)
	line(canvas, [(5, 7), (10, 7), (7, 5), (7, 10)], accent)
	line(canvas, [(9, 5), (9, 10), (5, 9), (10, 9)], glow)
	rect(canvas, 6, 6, 8, 8, accent)


def draw_reactor(canvas, glow, accent):
	rect(canvas, 4, 4, 11, 11, glow)
	rect(canvas, 6, 6, 9, 9, accent, fill=True)
	for points in [
		[(8, 3), (8, 4), (8, 5)],
		[(12, 8), (11, 8), (10, 8)],
		[(8, 12), (8, 11), (8, 10)],
		[(3, 8), (4, 8), (5, 8)],
		[(5, 5), (6, 6)],
		[(11, 5), (10, 6)],
		[(5, 11), (6, 10)],
		[(11, 11), (10, 10)],
	]:
		line(canvas, points, glow)


def draw_hud_panel(canvas, glow, accent):
	panel = (42, 46, 56)
	inner = (20, 23, 31)
	rect(canvas, 1, 1, 14, 14, panel, fill=True)
	rect(canvas, 1, 1, 14, 14, glow)
	rect(canvas, 2, 2, 13, 13, inner, fill=True)
	rect(canvas, 2, 2, 13, 13, (115, 122, 138))
	for x, y in [(3, 3), (12, 3), (3, 12), (12, 12)]:
		rect(canvas, x, y, x + 1, y + 1, accent, fill=True)
	line(canvas, [(5, 2), (6, 2), (9, 2), (10, 2), (5, 13), (6, 13), (9, 13), (10, 13)], glow)
	line(canvas, [(2, 5), (2, 6), (2, 9), (2, 10), (13, 5), (13, 6), (13, 9), (13, 10)], glow)


def draw_hud_info(canvas, glow, accent):
	draw_hud_panel(canvas, glow, accent)
	rect(canvas, 3, 3, 4, 12, accent, fill=True)
	line(canvas, [(6, 5), (11, 5), (6, 8), (11, 8), (6, 11), (10, 11)], glow)
	set_px(canvas, 5, 5, accent)
	set_px(canvas, 5, 8, accent)
	set_px(canvas, 5, 11, accent)


def draw_guide_book(canvas, glow, accent):
	rect(canvas, 4, 3, 11, 12, glow, fill=True)
	rect(canvas, 5, 4, 10, 11, accent, fill=True)
	line(canvas, [(6, 6), (9, 6), (6, 8), (9, 8), (6, 10), (8, 10)], glow)
	line(canvas, [(4, 4), (3, 5), (3, 10), (4, 11)], accent)


def draw_enchanted_book(canvas, glow, accent):
	draw_guide_book(canvas, glow, accent)
	line(canvas, [(8, 4), (9, 5), (10, 6), (9, 7), (8, 8), (7, 7), (6, 6), (7, 5)], (255, 255, 255))


def draw_write_book(canvas, glow, accent):
	draw_guide_book(canvas, glow, accent)
	line(canvas, [(10, 10), (11, 11), (12, 12)], (255, 255, 255))
	line(canvas, [(9, 11), (10, 10), (11, 9)], glow)


def draw_knowledge_book(canvas, glow, accent):
	draw_guide_book(canvas, glow, accent)
	line(canvas, [(7, 5), (8, 5), (9, 5), (8, 6), (7, 7), (8, 8), (9, 7)], glow)


def draw_xp_bottle(canvas, glow, accent):
	rect(canvas, 6, 3, 9, 5, accent, fill=True)
	rect(canvas, 5, 6, 10, 11, glow, fill=True)
	rect(canvas, 6, 7, 9, 10, accent)
	line(canvas, [(7, 8), (8, 9), (8, 8), (7, 9)], (255, 255, 255))


def draw_arrow_right(canvas, glow, accent):
	line(canvas, [(3, 8), (4, 8), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8)], glow)
	line(canvas, [(8, 6), (9, 7), (10, 8), (9, 9), (8, 10)], accent)


def draw_energy_rod(canvas, glow, accent):
	line(canvas, [(8, 3), (8, 4), (8, 5), (8, 10), (8, 11), (8, 12)], glow)
	line(canvas, [(6, 6), (9, 5), (7, 8), (10, 7), (6, 10)], accent)


def draw_craft_chip(canvas, glow, accent):
	rect(canvas, 4, 4, 11, 11, glow)
	rect(canvas, 6, 6, 9, 9, accent)
	line(canvas, [(7, 3), (7, 4), (10, 7), (11, 7), (8, 11), (8, 12), (4, 8), (3, 8)], accent)


def draw_ingot(canvas, glow, accent):
	rect(canvas, 3, 6, 12, 10, glow, fill=True)
	line(canvas, [(4, 5), (11, 5), (12, 6), (3, 10)], accent)
	line(canvas, [(5, 11), (10, 11), (11, 10), (4, 6)], accent)
	rect(canvas, 5, 7, 10, 9, (255, 255, 255))
	line(canvas, [(5, 8), (10, 8)], accent)


def draw_plate(canvas, glow, accent):
	rect(canvas, 3, 4, 12, 11, glow, fill=True)
	rect(canvas, 4, 5, 11, 10, (14, 18, 24), fill=True)
	rect(canvas, 4, 5, 11, 10, accent)
	for x, y in [(5, 6), (10, 6), (5, 9), (10, 9)]:
		set_px(canvas, x, y, (255, 255, 255))
	line(canvas, [(6, 7), (9, 7), (6, 8), (9, 8)], accent)


def draw_dust(canvas, glow, accent):
	for points in [
		[(5, 6), (6, 5), (7, 6), (6, 7)],
		[(9, 6), (10, 5), (11, 6), (10, 7)],
		[(7, 9), (8, 8), (9, 9), (8, 10)],
	]:
		line(canvas, points, glow)
	set_px(canvas, 6, 11, accent)
	set_px(canvas, 10, 10, accent)
	set_px(canvas, 4, 9, accent)


def draw_wire(canvas, glow, accent):
	rect(canvas, 3, 8, 6, 10, glow, fill=True)
	rect(canvas, 10, 5, 12, 8, glow, fill=True)
	line(canvas, [(6, 9), (7, 8), (8, 8), (9, 7), (10, 7)], accent)
	line(canvas, [(4, 9), (5, 9), (11, 6), (11, 7)], (255, 255, 255))
	set_px(canvas, 7, 9, glow)
	set_px(canvas, 8, 7, glow)


def draw_circuit(canvas, glow, accent):
	rect(canvas, 3, 4, 12, 11, glow, fill=True)
	rect(canvas, 4, 5, 11, 10, (9, 14, 18), fill=True)
	for x in range(4, 12, 2):
		set_px(canvas, x, 3, accent)
		set_px(canvas, x, 12, accent)
	line(canvas, [(5, 9), (5, 7), (7, 7), (7, 6), (10, 6)], glow)
	line(canvas, [(6, 10), (8, 10), (8, 8), (10, 8)], accent)
	rect(canvas, 8, 7, 9, 8, (255, 255, 255), fill=True)


def draw_machine_part(canvas, glow, accent):
	rect(canvas, 3, 3, 12, 12, glow, fill=True)
	rect(canvas, 4, 4, 11, 11, (17, 21, 28), fill=True)
	rect(canvas, 5, 5, 10, 10, accent)
	rect(canvas, 6, 6, 9, 9, (9, 12, 18), fill=True)
	for x, y in [(4, 4), (11, 4), (4, 11), (11, 11)]:
		set_px(canvas, x, y, (255, 255, 255))
	line(canvas, [(6, 8), (9, 8), (8, 6), (8, 9)], glow)


def draw_core_node(canvas, glow, accent):
	line(canvas, [(8, 3), (10, 5), (10, 10), (8, 12), (6, 10), (6, 5), (8, 3)], glow)
	rect(canvas, 7, 6, 8, 9, accent, fill=True)
	line(canvas, [(4, 8), (6, 8), (10, 8), (12, 8), (8, 4), (8, 6), (8, 10), (8, 12)], accent)
	set_px(canvas, 8, 8, (255, 255, 255))


def draw_tube(canvas, glow, accent):
	rect(canvas, 5, 3, 10, 12, glow)
	rect(canvas, 6, 4, 9, 11, accent)
	line(canvas, [(7, 3), (8, 3), (7, 12), (8, 12)], (255, 255, 255))


def draw_crystal(canvas, glow, accent):
	line(canvas, [(8, 3), (11, 7), (9, 12), (7, 12), (5, 7), (8, 3)], glow)
	line(canvas, [(8, 5), (9, 7), (8, 10), (7, 7), (8, 5)], accent)


def draw_gear(canvas, glow, accent):
	rect(canvas, 6, 6, 9, 9, glow)
	for x, y in [(7, 3), (11, 7), (7, 11), (3, 7), (4, 4), (10, 4), (4, 10), (10, 10)]:
		set_px(canvas, x, y, accent)
		set_px(canvas, x + (1 if x < 8 else -1), y, glow)


def draw_cell(canvas, glow, accent):
	rect(canvas, 5, 3, 10, 12, glow)
	rect(canvas, 6, 5, 9, 10, accent, fill=True)
	line(canvas, [(7, 4), (8, 4), (7, 11), (8, 11)], (255, 255, 255))


def draw_lens(canvas, glow, accent):
	line(canvas, [(8, 3), (10, 4), (11, 6), (11, 8), (10, 10), (8, 11), (6, 10), (5, 8), (5, 6), (6, 4), (8, 3)], glow)
	rect(canvas, 7, 6, 8, 8, accent, fill=True)


def draw_shard(canvas, glow, accent):
	line(canvas, [(8, 3), (10, 6), (9, 12), (6, 11), (5, 7), (8, 3)], glow)
	line(canvas, [(8, 5), (8, 9), (7, 10)], accent)


def draw_blob(canvas, glow, accent):
	rect(canvas, 4, 5, 11, 10, glow, fill=True)
	line(canvas, [(5, 4), (10, 4), (12, 6), (11, 11), (6, 12), (4, 10), (3, 7)], accent)


def apply_variant_marks(canvas, asset, glow, accent):
	variant = zlib.crc32(asset.id.encode("utf-8")) % 6
	if variant == 0:
		line(canvas, [(4, 3), (5, 3), (10, 12), (11, 12)], accent)
	elif variant == 1:
		line(canvas, [(3, 4), (3, 5), (12, 10), (12, 11)], glow)
	elif variant == 2:
		line(canvas, [(4, 12), (5, 11), (10, 4), (11, 3)], accent)
	elif variant == 3:
		set_px(canvas, 4, 4, glow)
		set_px(canvas, 11, 4, glow)
		set_px(canvas, 4, 11, accent)
		set_px(canvas, 11, 11, accent)
	elif variant == 4:
		line(canvas, [(6, 3), (8, 3), (10, 3), (6, 12), (8, 12), (10, 12)], accent)
	else:
		line(canvas, [(3, 6), (3, 8), (3, 10), (12, 6), (12, 8), (12, 10)], glow)


DRAWERS: dict[str, Callable[[list, tuple[int, int, int], tuple[int, int, int]], None]] = {
	"tech_book": draw_tech_book,
	"drill": draw_drill,
	"frame": draw_frame,
	"matrix": draw_matrix,
	"core": draw_core,
	"rotor": draw_rotor,
	"sensor": draw_sensor,
	"chip": draw_chip,
	"blade": draw_blade,
	"launcher": draw_launcher,
	"hook": draw_hook,
	"dock": draw_dock,
	"crate": draw_crate,
	"filter": draw_filter,
	"bus": draw_bus,
	"splitter": draw_splitter,
	"harvester": draw_harvester,
	"vacuum": draw_vacuum,
	"turbine": draw_turbine,
	"feller": draw_feller,
	"collector": draw_collector,
	"router": draw_router,
	"hub": draw_hub,
	"assembler": draw_assembler,
	"reactor": draw_reactor,
	"hud_panel": draw_hud_panel,
	"hud_info": draw_hud_info,
	"guide_book": draw_guide_book,
	"enchanted_book": draw_enchanted_book,
	"write_book": draw_write_book,
	"knowledge_book": draw_knowledge_book,
	"xp_bottle": draw_xp_bottle,
	"arrow_right": draw_arrow_right,
	"energy_rod": draw_energy_rod,
	"craft_chip": draw_craft_chip,
	"ingot": draw_ingot,
	"plate": draw_plate,
	"dust": draw_dust,
	"wire": draw_wire,
	"circuit": draw_circuit,
	"machine_part": draw_machine_part,
	"core_node": draw_core_node,
	"tube": draw_tube,
	"crystal": draw_crystal,
	"gear": draw_gear,
	"cell": draw_cell,
	"lens": draw_lens,
	"shard": draw_shard,
	"blob": draw_blob,
	"dock_machine": draw_hub,
	"splitter_machine": draw_splitter,
	"bus_machine": draw_bus,
}


def render_icon(asset: AssetDef) -> list[list[tuple[int, int, int, int]]]:
	base, glow, accent = asset.palette
	canvas = blank(base)
	machine = asset.kind == MACHINE
	if machine:
		glow_frame(canvas, base, glow, accent, machine=True)
	if asset.symbol == "quarry1":
		draw_quarry_machine(canvas, glow, accent, 1)
	elif asset.symbol == "quarry2":
		draw_quarry_machine(canvas, glow, accent, 2)
	elif asset.symbol == "quarry3":
		draw_quarry_machine(canvas, glow, accent, 3)
	else:
		DRAWERS[asset.symbol](canvas, glow, accent)
	if machine:
		apply_variant_marks(canvas, asset, glow, accent)
	return canvas


def write_json(path: Path, data: dict) -> None:
	path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def build_zip() -> None:
	with zipfile.ZipFile(PACK_ZIP, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as zf:
		for path in sorted(PACK_ROOT.rglob("*")):
			if path.is_file():
				zf.write(path, path.relative_to(PACK_ROOT).as_posix())


def stable_model_hash(key: str, modulus: int) -> int:
	return zlib.crc32(key.encode("utf-8")) % modulus


def tech_cmd(item_id: str) -> int:
	return 1_000_000 + stable_model_hash(item_id, 4_000_000)


def machine_cmd(machine_id: str) -> int:
	return 6_000_000 + stable_model_hash(machine_id, 2_000_000)


def parse_java_seed_items() -> list[tuple[str, str]]:
	text = REGISTRY_FILE.read_text(encoding="utf-8")
	items: list[tuple[str, str]] = []
	for item_id, material in re.findall(r'this\.registerItem\("([a-z0-9_]+)".*?Material\.([A-Z0-9_]+)', text):
		items.append((item_id, material))
	for item_id, material in re.findall(r'new String\[\]\{"([a-z0-9_]+)",\s*"[^"]+",\s*"[A-Z]+",\s*"([A-Z0-9_]+)"', text):
		items.append((item_id, material))
	return items


def parse_java_seed_machines() -> list[tuple[str, str]]:
	text = REGISTRY_FILE.read_text(encoding="utf-8")
	return re.findall(r'new Object\[\]\{"([a-z0-9_]+)",\s*"[^"]+",\s*TechCategory\.[A-Z]+,\s*Material\.([A-Z0-9_]+)', text)


def parse_yaml_section(path: Path, section_name: str, field_name: str) -> list[tuple[str, str]]:
	entries: list[tuple[str, str]] = []
	current_id: str | None = None
	in_section = False
	for raw in path.read_text(encoding="utf-8").splitlines():
		line = raw.rstrip()
		if not line.strip():
			continue
		if not raw.startswith(" ") and line == f"{section_name}:":
			in_section = True
			current_id = None
			continue
		if not raw.startswith(" ") and line.endswith(":") and line != f"{section_name}:":
			in_section = False
			current_id = None
		if not in_section:
			continue
		item_match = re.match(r"^  ([a-z0-9_]+):\s*$", line)
		if item_match:
			current_id = item_match.group(1)
			continue
		field_match = re.match(rf"^    {field_name}: ([A-Z0-9_]+)\s*$", line)
		if current_id and field_match:
			entries.append((current_id, field_match.group(1)))
	return entries


def unique_defs(rows: list[tuple[str, str]]) -> list[tuple[str, str]]:
	seen: dict[str, str] = {}
	for entry_id, material in rows:
		seen[entry_id] = material
	return sorted(seen.items())


def parent_for_material(material: str) -> str:
	if any(token in material for token in ("SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "ROD", "TRIDENT")):
		return "minecraft:item/handheld"
	return "minecraft:item/generated"


def palette_for(asset_id: str, material: str, kind: str) -> tuple[tuple[int, int, int], tuple[int, int, int], tuple[int, int, int]]:
	key = f"{asset_id}:{material}:{kind}".lower()
	if any(token in key for token in ("quantum", "cryo", "vacuum", "sensor", "lens", "glass", "orbit", "data")):
		return SKY
	if any(token in key for token in ("fusion", "plasma", "heat", "stellar", "star", "antimatter", "solar")):
		return AMBER
	if any(token in key for token in ("bio", "farm", "seed", "harvest", "resin", "fiber", "growth")):
		return LIME
	if any(token in key for token in ("dark", "void", "warp", "singularity", "field", "phase", "graviton", "chrono", "astral")):
		return PURPLE
	if any(token in key for token in ("crusher", "furnace", "kiln", "forge", "alloy", "steel", "plate", "gear")):
		return RED
	return PALETTES[stable_model_hash(key, len(PALETTES))]


def symbol_for(asset_id: str, material: str, kind: str) -> str:
	key = asset_id.lower()
	material_key = material.lower()
	if kind == MACHINE:
		if "quarry_drill_mk3" in key:
			return "quarry3"
		if "quarry_drill_mk2" in key:
			return "quarry2"
		if "quarry_drill" in key:
			return "quarry1"
		if "assembler" in key:
			return "assembler"
		if any(token in key for token in ("fusion", "reactor")):
			return "reactor"
		if any(token in key for token in ("turbine", "wind")):
			return "turbine"
		if any(token in key for token in ("router", "node", "relay")):
			return "router"
		if any(token in key for token in ("collector", "mob")):
			return "collector"
		if any(token in key for token in ("dock", "hub", "storage")):
			return "hub"
		if any(token in key for token in ("vacuum", "chamber")):
			return "vacuum"
		if any(token in key for token in ("harvest", "farm", "greenhouse")):
			return "harvester"
		if any(token in key for token in ("feller", "saw", "tree")):
			return "feller"
		if any(token in key for token in ("splitter",)):
			return "splitter_machine"
		if any(token in key for token in ("bus",)):
			return "bus_machine"
		return "frame"
	if key == "tech_book":
		return "tech_book"
	if any(token in key for token in ("book", "archive", "template")):
		return "guide_book"
	if any(token in key for token in ("machine_component", "component", "casing", "frame")):
		return "machine_part"
	if any(token in key for token in ("fusion_core", "reactor_core", "stability_core", "graviton_core", "singularity_core", "apex_core", "core")):
		return "core_node"
	if any(token in key for token in ("advanced_circuit", "circuit_board", "etched_circuit", "astral_circuit", "orbit_processor", "routing_chip", "control_unit", "micro_processor", "field_processor", "quantum_chip", "data_matrix", "logic_gate", "processor", "chip", "circuit", "module", "unit", "logic")):
		return "circuit"
	if any(token in key for token in ("ingot", "alloy")):
		return "ingot"
	if any(token in key for token in ("plate", "panel", "sheet", "casing", "shell")):
		return "plate"
	if any(token in key for token in ("dust", "powder", "slurry")):
		return "dust"
	if any(token in key for token in ("wire", "coil")):
		return "wire"
	if any(token in key for token in ("tube", "canister", "bottle")):
		return "tube"
	if any(token in key for token in ("crystal", "lattice")):
		return "crystal"
	if any(token in key for token in ("gear", "motor", "servo")):
		return "gear"
	if any(token in key for token in ("cell", "battery")):
		return "cell"
	if any(token in key for token in ("lens",)):
		return "lens"
	if any(token in key for token in ("matrix",)):
		return "chip"
	if any(token in key for token in ("mesh", "lattice")):
		return "frame"
	if any(token in key for token in ("shard", "fragment", "seed")):
		return "shard"
	if any(token in key for token in ("resin", "gel", "blob", "fluid")):
		return "blob"
	if any(token in material_key for token in ("book",)):
		return "guide_book"
	if any(token in material_key for token in ("rod", "axe", "pickaxe", "sword")):
		return "blade"
	return "core"


def build_assets() -> list[AssetDef]:
	tech_rows = parse_java_seed_items()
	machine_rows = parse_java_seed_machines()
	for resource in RESOURCE_FILES:
		tech_rows.extend(parse_yaml_section(resource, "items", "icon"))
		machine_rows.extend(parse_yaml_section(resource, "machines", "block"))
	assets: list[AssetDef] = []
	seen_cmd: set[int] = set()
	for item_id, material in unique_defs(tech_rows):
		cmd = tech_cmd(item_id)
		if cmd in seen_cmd:
			raise RuntimeError(f"Tech item custom model collision: {item_id} -> {cmd}")
		seen_cmd.add(cmd)
		assets.append(AssetDef(item_id, material, cmd, TECH, parent_for_material(material), symbol_for(item_id, material, TECH), palette_for(item_id, material, TECH)))
	for machine_id, material in unique_defs(machine_rows):
		cmd = machine_cmd(machine_id)
		if cmd in seen_cmd:
			raise RuntimeError(f"Machine custom model collision: {machine_id} -> {cmd}")
		seen_cmd.add(cmd)
		assets.append(AssetDef(machine_id, material, cmd, MACHINE, "minecraft:item/generated", symbol_for(machine_id, material, MACHINE), palette_for(machine_id, material, MACHINE)))
	assets.extend(HUD_ASSETS)
	return assets


def material_model_name(material: str) -> str:
	return material.lower()


def build_item_definition(material: str, assets: list[AssetDef]) -> dict:
	entries = [
		{
			"threshold": asset.cmd,
			"model": {
				"type": "minecraft:model",
				"model": f"techproject:item/{asset.id}"
			}
		}
		for asset in sorted(assets, key=lambda it: it.cmd)
	]
	return {
		"model": {
			"type": "minecraft:range_dispatch",
			"property": "minecraft:custom_model_data",
			"entries": entries,
			"fallback": {
				"type": "minecraft:model",
				"model": f"minecraft:item/{material_model_name(material)}"
			}
		}
	}


def main() -> None:
	ensure(PACK_ROOT)
	ensure(PACK_ROOT / "assets" / "minecraft" / "items")
	ensure(PACK_ROOT / "assets" / "techproject" / "models" / "item")
	ensure(PACK_ROOT / "assets" / "techproject" / "textures" / "item")
	assets = build_assets()

	write_json(PACK_ROOT / "pack.mcmeta", {
		"pack": {
			"description": "TechProject 系統線專屬材質與模型包",
			"pack_format": 75
		}
	})

	readme = """# TechProject Systems Resource Pack

包含科技材料、科技機器與少量功能按鈕的專屬材質。

目前覆蓋：
- 研究台到終局科技線的大部分科技材料
- 各科技機器對應的放置物 / 物品圖示
- 科技書、翻頁、返回等少量功能物件

使用方式：
1. 直接使用同層自動產生的 techproject_systems_pack.zip。
2. zip 內最外層必須直接看到 pack.mcmeta 與 assets，不能再多包一層資料夾。
3. 放到伺服器資源包或手動匯入客戶端。
4. 搭配插件中的 CustomModelData 生效。
"""
	(PACK_ROOT / "README.md").write_text(readme, encoding="utf-8")

	icon = render_icon(AssetDef("pack", "PAPER", 0, MACHINE, "minecraft:item/generated", "chip", CYAN))
	write_png(PACK_ROOT / "pack.png", icon)

	by_material: dict[str, list[AssetDef]] = {}
	for asset in assets:
		by_material.setdefault(asset.material, []).append(asset)

		write_json(PACK_ROOT / "assets" / "techproject" / "models" / "item" / f"{asset.id}.json", {
			"parent": asset.parent,
			"textures": {
				"layer0": f"techproject:item/{asset.id}"
			}
		})
		write_png(PACK_ROOT / "assets" / "techproject" / "textures" / "item" / f"{asset.id}.png", render_icon(asset))

	for material, assets in by_material.items():
		write_json(PACK_ROOT / "assets" / "minecraft" / "items" / f"{material_model_name(material)}.json", build_item_definition(material, assets))

	build_zip()


if __name__ == "__main__":
	main()
