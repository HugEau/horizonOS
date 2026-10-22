#!/usr/bin/env python3
"""Génère tizen/icon.png (512x512) sans dépendance : dégradé de ciel, soleil levant et reflets sur l'eau."""
import math
import os
import struct
import zlib

N = 512


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def pixel(x, y):
    top, bottom = (27, 42, 73), (255, 138, 91)
    horizon = 300
    if y < horizon:
        c = lerp(top, bottom, (y / horizon) ** 1.6)
        d = math.hypot(x - 256, y - horizon)
        if d < 96:
            return lerp(c, (255, 200, 90), 1.0)
        return c
    # Eau : reflets horizontaux de plus en plus fins
    base = lerp((20, 30, 58), (12, 18, 38), (y - horizon) / (N - horizon))
    for i, (off, half, alpha) in enumerate(((20, 120, 0.85), (50, 90, 0.6), (80, 62, 0.4), (110, 36, 0.25))):
        if horizon + off <= y < horizon + off + 8 and abs(x - 256) < half:
            return lerp(base, (255, 214, 150), alpha)
    return base


def main():
    raw = bytearray()
    for y in range(N):
        raw.append(0)
        for x in range(N):
            raw.extend(pixel(x, y))

    def chunk(kind, data):
        body = kind + data
        return struct.pack('>I', len(data)) + body + struct.pack('>I', zlib.crc32(body) & 0xFFFFFFFF)

    png = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', N, N, 8, 2, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b'')
    out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'tizen', 'icon.png')
    with open(out, 'wb') as f:
        f.write(png)
    print('Écrit', out)


if __name__ == '__main__':
    main()
