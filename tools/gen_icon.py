"""Generates the EcoRewards icon: a gold coin with a diamond pickaxe (no Pillow needed).

Usage: python tools/gen_icon.py <project root>
Draws 16x16 pixel art and scales it 8x to src/main/resources/assets/ecorewards/icon.png (128x128).
"""
import math
import os
import struct
import sys
import zlib

SCALE = 8

# coin, darkest to lightest
RIM_DARK = (110, 66, 14, 255)
RIM = (168, 108, 22, 255)
SHADOW = (206, 140, 28, 255)
FACE = (234, 172, 40, 255)
LIGHT = (250, 206, 82, 255)
SHINE = (255, 242, 176, 255)

# pickaxe drawn over the coin: O outline, D/m/l diamond dark/mid/light, b/w handle dark/light
PICKAXE = [
    "................",
    "................",
    "....OOOOO.......",
    "...OlllmmOO.....",
    "....OOOOmmDO....",
    "........OOmDO...",
    ".......OwOOmDO..",
    "......OwbO.OmDO.",
    ".....OwbO...ODO.",
    "....OwbO.....OO.",
    "...OwbO.........",
    "..OwbO..........",
    "..ObO...........",
    "...O............",
    "................",
    "................",
]
PICKAXE_COLORS = {
    "O": (34, 24, 12, 255),
    "D": (20, 128, 122, 255),
    "m": (60, 214, 200, 255),
    "l": (178, 255, 240, 255),
    "b": (92, 60, 26, 255),
    "w": (150, 108, 52, 255),
}


def coin_pixel(x, y):
    dx, dy = x + 0.5 - 8.0, y + 0.5 - 8.0
    dist = math.hypot(dx, dy)
    if dist > 7.9:
        return None
    # light comes from the top left
    light = -(dx + dy) / 11.0
    if dist > 6.9:
        return RIM_DARK if light < 0.2 else RIM
    if dist > 5.9:
        return RIM if light < 0.35 else LIGHT
    if light > 0.45:
        return SHINE
    if light > 0.1:
        return LIGHT
    if light > -0.35:
        return FACE
    return SHADOW


def draw():
    pixels = []
    for y in range(16):
        row = []
        for x in range(16):
            color = PICKAXE_COLORS.get(PICKAXE[y][x]) or coin_pixel(x, y) or (0, 0, 0, 0)
            row.append(color)
        pixels.append(row)
    return pixels


def write_png(path, rows):
    width, height = len(rows[0]), len(rows)
    raw = bytearray()
    for row in rows:
        raw.append(0)
        for px in row:
            raw.extend(px)

    def chunk(tag, body):
        return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body) & 0xFFFFFFFF)

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
                + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    root = sys.argv[1] if len(sys.argv) > 1 else "."
    small = draw()
    big = [[px for px in row for _ in range(SCALE)] for row in small for _ in range(SCALE)]
    write_png(os.path.join(root, "src/main/resources/assets/ecorewards/icon.png"), big)
