#!/usr/bin/env python3
"""
Preview storm wall texture variants used by the Eye of the Storm mod.

Mirrors the Java Voronoi in StormWallVoronoi.java so you can compare:
  - legacy organic pixel noise
  - seamless 2D Voronoi slice (W=0)
  - pseudo-3D slices at different W depths (each concentric layer uses a different slice)
  - 3x3 tiled preview (shows repetition / seamlessness)
  - edge mismatch heatmap (seamless = dark edges)

Usage:
  python tools/preview_storm_wall_textures.py
  python tools/preview_storm_wall_textures.py --size 256 --out tools/output/preview.png

Requires: Pillow (pip install pillow). numpy is optional (edge diff only).
"""

from __future__ import annotations

import argparse
import math
import os

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError as exc:
    raise SystemExit("Install Pillow: pip install pillow") from exc

SEED = 0x0156A11C
MIN_ALPHA = 0.06
MAX_ALPHA = 1.0
CELLS_PER_TILE = 5
PERIOD_CELLS = 5
EDGE_WIDTH = 0.14


def wrap_cell(c: int, period: int) -> int:
    return c % period


def wrap_delta(d: float, period: float) -> float:
    d = d / period
    d = d - math.floor(d)
    if d > 0.5:
        d -= 1.0
    return d * period


def toroidal_dist_sq(
    x: float, y: float, z: float, px: float, py: float, pz: float, period: float
) -> float:
    dx = wrap_delta(x - px, period)
    dy = wrap_delta(y - py, period)
    dz = wrap_delta(z - pz, period)
    return dx * dx + dy * dy + dz * dz


def clamp(x: float, lo: float, hi: float) -> float:
    return max(lo, min(hi, x))


def smoothstep(edge0: float, edge1: float, x: float) -> float:
    t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)


def hash01(x: int, y: int, z: int, channel: int) -> float:
    h = SEED & 0xFFFFFFFFFFFFFFFF
    h = (h * 6364136223846793005 + (x & 0xFFFFFFFF) * 374761393) & 0xFFFFFFFFFFFFFFFF
    h = (h * 6364136223846793005 + (y & 0xFFFFFFFF) * 668265263) & 0xFFFFFFFFFFFFFFFF
    h = (h * 6364136223846793005 + (z & 0xFFFFFFFF) * 2147483647) & 0xFFFFFFFFFFFFFFFF
    h = (h * 6364136223846793005 + channel * 1442695040888963407) & 0xFFFFFFFFFFFFFFFF
    h ^= h >> 33
    h = (h * 0xFF51AFD7ED558CCD) & 0xFFFFFFFFFFFFFFFF
    h ^= h >> 33
    return (h & 0xFFFFF) / float(0xFFFFF)


def cell_density(x: float, y: float, z: float) -> float:
    period = float(PERIOD_CELLS)
    x *= period
    y *= period
    z *= period

    f1 = float("inf")
    f2 = float("inf")
    nearest_cx = nearest_cy = nearest_cz = 0
    ix = math.floor(x)
    iy = math.floor(y)
    iz = math.floor(z)

    for dz in (-1, 0, 1):
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                cx = int(ix) + dx
                cy = int(iy) + dy
                cz = int(iz) + dz
                wx = wrap_cell(cx, PERIOD_CELLS)
                wy = wrap_cell(cy, PERIOD_CELLS)
                wz = wrap_cell(cz, PERIOD_CELLS)
                px = cx + hash01(wx, wy, wz, 0)
                py = cy + hash01(wx, wy, wz, 1)
                pz = cz + hash01(wx, wy, wz, 2)
                dist_sq = toroidal_dist_sq(x, y, z, px, py, pz, period)
                if dist_sq < f1:
                    f2 = f1
                    f1 = dist_sq
                    nearest_cx, nearest_cy, nearest_cz = cx, cy, cz
                elif dist_sq < f2:
                    f2 = dist_sq

    cell_roll = hash01(
        wrap_cell(nearest_cx, PERIOD_CELLS),
        wrap_cell(nearest_cy, PERIOD_CELLS),
        wrap_cell(nearest_cz, PERIOD_CELLS),
        7,
    )
    interior = 0.08 + cell_roll * (0.38 - 0.08)

    edge_dist = math.sqrt(f2) - math.sqrt(f1)
    edge_factor = 1.0 - smoothstep(0.0, EDGE_WIDTH, edge_dist)
    edge_roll = hash01(
        wrap_cell(nearest_cx, PERIOD_CELLS),
        wrap_cell(nearest_cy, PERIOD_CELLS),
        wrap_cell(nearest_cz, PERIOD_CELLS),
        8,
    )
    edge = 0.82 + edge_roll * (1.0 - 0.82)

    return clamp(interior * (1.0 - edge_factor) + edge * edge_factor, 0.0, 1.0)


def bake_voronoi_colored(size: int, w: float) -> Image.Image:
    """Reference-style per-cell random colors (like the sphere example)."""
    inv = 1.0 / size
    rgba = bytearray(size * size * 4)
    scale = float(PERIOD_CELLS)
    period = scale

    for y in range(size):
        for x in range(size):
            u = (x + 0.5) * inv * scale
            v = (y + 0.5) * inv * scale
            ww = w * scale

            f1 = float("inf")
            nearest_cx = nearest_cy = nearest_cz = 0
            ix = math.floor(u)
            iy = math.floor(v)
            iz = math.floor(ww)

            for dz in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    for dx in (-1, 0, 1):
                        cx = int(ix) + dx
                        cy = int(iy) + dy
                        cz = int(iz) + dz
                        wx = wrap_cell(cx, PERIOD_CELLS)
                        wy = wrap_cell(cy, PERIOD_CELLS)
                        wz = wrap_cell(cz, PERIOD_CELLS)
                        px = cx + hash01(wx, wy, wz, 0)
                        py = cy + hash01(wx, wy, wz, 1)
                        pz = cz + hash01(wx, wy, wz, 2)
                        dist_sq = toroidal_dist_sq(u, v, ww, px, py, pz, period)
                        if dist_sq < f1:
                            f1 = dist_sq
                            nearest_cx, nearest_cy, nearest_cz = cx, cy, cz

            hr = hash01(wrap_cell(nearest_cx, PERIOD_CELLS), wrap_cell(nearest_cy, PERIOD_CELLS), wrap_cell(nearest_cz, PERIOD_CELLS), 10)
            hg = hash01(wrap_cell(nearest_cx, PERIOD_CELLS), wrap_cell(nearest_cy, PERIOD_CELLS), wrap_cell(nearest_cz, PERIOD_CELLS), 11)
            hb = hash01(wrap_cell(nearest_cx, PERIOD_CELLS), wrap_cell(nearest_cy, PERIOD_CELLS), wrap_cell(nearest_cz, PERIOD_CELLS), 12)
            i = (y * size + x) * 4
            rgba[i : i + 4] = (
                int(80 + hr * 175),
                int(80 + hg * 175),
                int(80 + hb * 175),
                255,
            )
    return Image.frombytes("RGBA", (size, size), bytes(rgba))


def bake_voronoi_slice(size: int, w: float) -> Image.Image:
    inv = 1.0 / size
    rgba = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) * inv
            v = (y + 0.5) * inv
            n = cell_density(u, v, w)
            alpha = MIN_ALPHA + n * (MAX_ALPHA - MIN_ALPHA)
            a = int(clamp(alpha, 0.0, 1.0) * 255)
            i = (y * size + x) * 4
            rgba[i : i + 4] = (255, 255, 255, a)
    return Image.frombytes("RGBA", (size, size), bytes(rgba))


def generate_organic_pixel_art(size: int) -> Image.Image:
    import random

    rng = random.Random(SEED)
    cell = 2
    grid = size // cell
    cells = [[0.0] * grid for _ in range(grid)]
    for gy in range(grid):
        for gx in range(grid):
            base = rng.random()
            cluster = 0.35 if rng.random() > 0.72 else 0.0
            cells[gx][gy] = clamp(base * 0.85 + cluster, 0.0, 1.0)

    rgba = bytearray(size * size * 4)
    for y in range(size):
        for x in range(size):
            gx, gy = x // cell, y // cell
            n = cells[gx][gy]
            lx = (x % cell) / cell
            ly = (y % cell) / cell
            crack = lx < 0.12 or lx > 0.88 or ly < 0.12 or ly > 0.88
            alpha = 0.28 + n * 0.62
            if crack:
                alpha = min(1.0, alpha + 0.18)
            brightness = int(170 + n * 85)
            a = int(clamp(alpha, 0.0, 1.0) * 255)
            i = (y * size + x) * 4
            rgba[i : i + 4] = (brightness, brightness, brightness, a)
    return Image.frombytes("RGBA", (size, size), bytes(rgba))


def tile_preview(img: Image.Image, reps: int = 3) -> Image.Image:
    w, h = img.size
    out = Image.new("RGBA", (w * reps, h * reps))
    for ty in range(reps):
        for tx in range(reps):
            out.paste(img, (tx * w, ty * h))
    return out


def seamless_edge_diff(img: Image.Image) -> tuple[float, Image.Image | None]:
    """Returns max channel diff across opposite edges and optional heatmap."""
    w, h = img.size
    px = img.load()
    max_diff = 0.0

    for x in range(w):
        for c in range(4):
            d = abs(px[x, 0][c] - px[x, h - 1][c])
            max_diff = max(max_diff, d)
    for y in range(h):
        for c in range(4):
            d = abs(px[0, y][c] - px[w - 1, y][c])
            max_diff = max(max_diff, d)

    try:
        import numpy as np

        arr = np.array(img, dtype=np.int16)
        top = arr[0, :, :]
        bottom = arr[-1, :, :]
        left = arr[:, 0, :]
        right = arr[:, -1, :]
        v_diff = np.abs(top - bottom).max(axis=1).astype(np.uint8)
        h_diff = np.abs(left - right).max(axis=1).astype(np.uint8)
        heat = Image.new("RGBA", (w, h), (0, 0, 0, 255))
        heat_px = heat.load()
        for x in range(w):
            v = int(v_diff[x])
            heat_px[x, 0] = (v, 0, 0, 255)
            heat_px[x, h - 1] = (v, 0, 0, 255)
        for y in range(h):
            u = int(h_diff[y])
            heat_px[0, y] = (0, u, 0, 255)
            heat_px[w - 1, y] = (0, u, 0, 255)
        return max_diff, heat
    except ImportError:
        return max_diff, None


def label(img: Image.Image, text: str, bar_h: int = 22) -> Image.Image:
    w, h = img.size
    out = Image.new("RGBA", (w, h + bar_h), (24, 24, 28, 255))
    out.paste(img, (0, bar_h))
    draw = ImageDraw.Draw(out)
    try:
        font = ImageFont.truetype("consola.ttf", 13)
    except OSError:
        font = ImageFont.load_default()
    draw.text((6, 4), text, fill=(230, 230, 235, 255), font=font)
    return out


def compose_sheet(
    size: int,
    layer_depths: list[float],
) -> tuple[Image.Image, dict[str, float]]:
    organic = generate_organic_pixel_art(size)
    voronoi_w0 = bake_voronoi_slice(size, 0.0)
    colored = bake_voronoi_colored(size, 0.0)
    slices = [bake_voronoi_slice(size, w) for w in layer_depths]

    stats: dict[str, float] = {}
    panels: list[tuple[str, Image.Image]] = []

    def add(name: str, img: Image.Image, tiled: bool = False) -> None:
        diff, _ = seamless_edge_diff(img)
        stats[f"{name}_seam_max_diff"] = diff
        view = tile_preview(img) if tiled else img
        panels.append((name, view))

    add("legacy_organic", organic, tiled=True)
    add("voronoi_density_w0", voronoi_w0, tiled=True)
    add("voronoi_colored_ref", colored, tiled=True)
    for i, (w, sl) in enumerate(zip(layer_depths, slices)):
        add(f"voronoi_layer_{i}_w{w:.2f}", sl, tiled=False)

    # 3D slice strip: same U,V but different W — what each concentric layer sees
    strip_w = size * len(slices)
    strip = Image.new("RGBA", (strip_w, size))
    for i, sl in enumerate(slices):
        strip.paste(sl, (i * size, 0))

    # Seam heatmap for voronoi
    _, heat = seamless_edge_diff(voronoi_w0)
    if heat is not None:
        panels.append(("voronoi_seam_heatmap", heat))

    panels.append(("3d_w_slices_strip", strip))

    # Layout grid
    thumb = size if size <= 128 else 128
    labeled = []
    for name, img in panels:
        if img.size[0] != thumb or img.size[1] != thumb:
            ratio = min(thumb / img.size[0], thumb / img.size[1])
            nw = max(1, int(img.size[0] * ratio))
            nh = max(1, int(img.size[1] * ratio))
            img = img.resize((nw, nh), Image.Resampling.NEAREST)
        seam = stats.get(f"{name}_seam_max_diff", seamless_edge_diff(
            bake_voronoi_slice(size, 0.0) if "voronoi" in name else organic
        )[0])
        seam_note = f" | seam Δ={seam:.0f}" if "seam" not in name else ""
        labeled.append(label(img, f"{name}{seam_note}"))

    cols = 3
    rows = (len(labeled) + cols - 1) // cols
    pad = 8
    lw = max(im.size[0] for im in labeled) + pad
    lh = max(im.size[1] for im in labeled) + pad
    sheet = Image.new("RGBA", (cols * lw + pad, rows * lh + pad), (16, 16, 20, 255))
    for idx, im in enumerate(labeled):
        cx = idx % cols
        cy = idx // cols
        sheet.paste(im, (pad + cx * lw, pad + cy * lh))
    return sheet, stats


def main() -> None:
    parser = argparse.ArgumentParser(description="Preview storm wall textures")
    parser.add_argument("--size", type=int, default=256, help="Texture resolution")
    parser.add_argument(
        "--layers",
        type=float,
        nargs="*",
        default=[0.0, 0.37, 0.74, 1.11, 1.48, 1.85, 2.22, 2.59],
        help="W depths for pseudo-3D layer slices",
    )
    parser.add_argument(
        "--out",
        default="tools/output/storm_wall_texture_preview.png",
        help="Output PNG path",
    )
    args = parser.parse_args()

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    sheet, stats = compose_sheet(args.size, args.layers)
    sheet.save(args.out)

    print(f"Wrote {args.out}")
    print("Seamlessness (max RGBA diff across opposite edges; 0 = perfect tile):")
    for k, v in sorted(stats.items()):
        if k.endswith("_seam_max_diff"):
            print(f"  {k}: {v:.1f}")
    print("\nNotes:")
    print("  - Voronoi uses toroidal 3D cells; the 2D texture is a W=0 slice.")
    print("  - Each concentric layer offsets U/V by layerDepth * wallVoronoiLayerSeparation.")
    print("  - 3x3 tiled panels show whether repetition is visible at scale.")
    print("  - Tune MIN_ALPHA / MAX_ALPHA at top of script to match StormConfig.")


if __name__ == "__main__":
    main()
