#!/usr/bin/env python3
import math
import os
import struct
import zlib

W = H = 1024
OUT = os.path.join(os.path.dirname(__file__), "..", "NM7IPTV", "Assets.xcassets", "AppIcon.appiconset", "AppIcon.png")

pixels = bytearray(W * H * 3)


def put(x, y, rgb):
    if 0 <= x < W and 0 <= y < H:
        i = (y * W + x) * 3
        pixels[i:i+3] = bytes(rgb)


def fill_rect(x0, y0, x1, y1, rgb):
    x0, y0 = max(0, x0), max(0, y0)
    x1, y1 = min(W, x1), min(H, y1)
    row = bytes(rgb) * max(0, x1 - x0)
    for y in range(y0, y1):
        i = (y * W + x0) * 3
        pixels[i:i + len(row)] = row


def fill_round_rect(x0, y0, x1, y1, r, rgb):
    for y in range(y0, y1):
        for x in range(x0, x1):
            dx = 0
            dy = 0
            if x < x0 + r: dx = x0 + r - x
            elif x >= x1 - r: dx = x - (x1 - r - 1)
            if y < y0 + r: dy = y0 + r - y
            elif y >= y1 - r: dy = y - (y1 - r - 1)
            if dx == 0 or dy == 0 or dx * dx + dy * dy <= r * r:
                put(x, y, rgb)


def thick_line(x0, y0, x1, y1, thickness, rgb):
    vx, vy = x1 - x0, y1 - y0
    length2 = max(1, vx * vx + vy * vy)
    r2 = (thickness / 2) ** 2
    minx = max(0, int(min(x0, x1) - thickness))
    maxx = min(W - 1, int(max(x0, x1) + thickness))
    miny = max(0, int(min(y0, y1) - thickness))
    maxy = min(H - 1, int(max(y0, y1) + thickness))
    for y in range(miny, maxy + 1):
        for x in range(minx, maxx + 1):
            t = ((x - x0) * vx + (y - y0) * vy) / length2
            t = max(0.0, min(1.0, t))
            px, py = x0 + t * vx, y0 + t * vy
            if (x - px) ** 2 + (y - py) ** 2 <= r2:
                put(x, y, rgb)


# Dark teal/navy gradient background, fully opaque for App Store compatibility.
for y in range(H):
    t = y / (H - 1)
    for x in range(W):
        s = x / (W - 1)
        r = int(5 + 8 * s)
        g = int(20 + 28 * (1 - t) + 8 * s)
        b = int(34 + 34 * (1 - t))
        put(x, y, (r, g, b))

# Glowing TV tile.
fill_round_rect(150, 170, 874, 680, 92, (25, 185, 190))
fill_round_rect(183, 203, 841, 647, 70, (7, 32, 47))

# Play symbol.
for y in range(300, 555):
    rel = (y - 300) / 255.0
    left = 400
    right = int(400 + (1 - abs(rel - 0.5) * 2) * 220)
    for x in range(left, right):
        put(x, y, (245, 252, 252))

# Stylized NM7 mark below the TV tile.
white = (244, 249, 250)
cyan = (60, 224, 225)
th = 34
# N
thick_line(214, 760, 214, 900, th, white)
thick_line(214, 760, 330, 900, th, white)
thick_line(330, 760, 330, 900, th, white)
# M
thick_line(394, 900, 394, 760, th, white)
thick_line(394, 760, 470, 842, th, white)
thick_line(470, 842, 546, 760, th, white)
thick_line(546, 760, 546, 900, th, white)
# 7
thick_line(620, 765, 806, 765, th, cyan)
thick_line(806, 765, 680, 900, th, cyan)


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)

raw = bytearray()
stride = W * 3
for y in range(H):
    raw.append(0)
    raw.extend(pixels[y * stride:(y + 1) * stride])

png = bytearray(b"\x89PNG\r\n\x1a\n")
png += chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0))
png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
png += chunk(b"IEND", b"")
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, "wb") as f:
    f.write(png)
print(OUT)
