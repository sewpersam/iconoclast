#!/usr/bin/env python3
"""Spike renderer: Arcticons SVG -> styled PNG.

plain  <svg> <out.png>                  white strokes on a dark rounded square
jade   <svg> <out.png> [stroke] [gap]   invert fill + procedural texture + streaks
"""
import re, subprocess, sys, tempfile
from pathlib import Path

import numpy as np
from PIL import Image
from scipy import ndimage as ndi

N = 432  # adaptive icon full size at xxxhdpi


def raster_strokes(svg, stroke=1.0, size=N, inset=0.62):
    """Render only the line art as an alpha mask, scaled into the safe zone."""
    src = Path(svg).read_text()
    src = re.sub(r"<metadata>.*?</metadata>", "", src, flags=re.S)
    src = src.replace("stroke:#fff;", f"stroke:#fff;stroke-width:{stroke};")
    with tempfile.TemporaryDirectory() as d:
        s, p = Path(d) / "i.svg", Path(d) / "i.png"
        s.write_text(src)
        inner = int(size * inset)
        subprocess.run(["inkscape", s, "--export-type=png", f"--export-filename={p}",
                        f"--export-width={inner}", f"--export-height={inner}",
                        "--export-background-opacity=0"],
                       check=True, capture_output=True)
        a = np.asarray(Image.open(p).convert("RGBA"))[..., 3] / 255.0
    out = np.zeros((size, size))
    o = (size - inner) // 2
    out[o:o + inner, o:o + inner] = a
    return out


def rounded_square(size=N, r=0.28):
    y, x = np.mgrid[0:size, 0:size] + 0.5
    rad = size * r
    cx = np.clip(x, rad, size - rad)
    cy = np.clip(y, rad, size - rad)
    d = np.hypot(x - cx, y - cy)
    return np.clip(rad - d + 0.5, 0, 1)


def fbm(size, seed, octaves=6):
    rng = np.random.default_rng(seed)
    acc = np.zeros((size, size))
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        n = 2 ** (o + 2)
        g = rng.random((n, n))
        acc += amp * np.asarray(Image.fromarray((g * 255).astype(np.uint8)).resize(
            (size, size), Image.BICUBIC)) / 255.0
        tot += amp
        amp *= 0.55
    return acc / tot


def invert_fill(stroke_mask, gap_px=6, close_px=3):
    """Enclosed regions become solid, strokes become gaps."""
    b = stroke_mask > 0.5
    closed = ndi.binary_closing(b, iterations=int(close_px))
    lab, n = ndi.label(~closed)
    border = set(np.unique(np.r_[lab[0], lab[-1], lab[:, 0], lab[:, -1]]))
    interior = np.isin(lab, [i for i in range(1, n + 1) if i not in border])
    cut = ndi.binary_dilation(b, iterations=int(gap_px))
    fill = interior & ~cut
    return ndi.gaussian_filter(fill.astype(float), 0.7)


def plain(svg, out):
    s = raster_strokes(svg, stroke=1.2)
    bg = rounded_square()
    rgb = np.zeros((N, N, 3)) + 0.08
    rgb = rgb * (1 - s[..., None]) + s[..., None]
    save(rgb, bg, out)


def jade(svg, out, stroke=2.2, gap=5):
    s = raster_strokes(svg, stroke=stroke)
    fill = invert_fill(s, gap_px=gap)
    if fill.sum() < 0.04 * (s > 0.5).sum() * 10:  # nothing enclosed: fall back to strokes
        fill = s
    bg = rounded_square()
    t = fbm(N, seed=7)
    veins = np.abs(fbm(N, seed=11) - 0.5) * 2
    tex = np.clip(0.55 * t + 0.45 * (1 - veins ** 0.35), 0, 1)
    jade_lo, jade_hi = np.array([0.05, 0.32, 0.22]), np.array([0.55, 0.92, 0.70])
    jade_rgb = jade_lo + (jade_hi - jade_lo) * tex[..., None]
    # streaks: smear the fill down-right, break it with stretched noise
    smear = np.zeros_like(fill)
    for k in range(1, 60, 2):
        smear = np.maximum(smear, ndi.shift(fill, (k * 0.9, k * 0.45), order=0) * (1 - k / 60))
    streak_noise = np.asarray(Image.fromarray((fbm(N // 8, seed=3) * 255).astype(np.uint8)).resize(
        (N // 8, N), Image.BICUBIC).resize((N, N), Image.BICUBIC)) / 255.0
    streak = np.clip(smear * (streak_noise - 0.35) * 2.2, 0, 1) * 0.45
    rgb = np.zeros((N, N, 3)) + 0.03
    rgb = rgb * (1 - streak[..., None]) + jade_rgb * 0.7 * streak[..., None]
    # bevel-ish highlight from distance field (screen blend)
    dist = ndi.distance_transform_edt(fill > 0.5)
    hi = np.clip(1 - dist / 6, 0, 1) * (fill > 0.5) * 0.35
    body = 1 - (1 - jade_rgb) * (1 - hi[..., None])
    rgb = rgb * (1 - fill[..., None]) + body * fill[..., None]
    save(rgb, bg, out)


def save(rgb, alpha, out):
    a = np.dstack([rgb, alpha])
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), "RGBA").save(out)


if __name__ == "__main__":
    mode, svg, out = sys.argv[1:4]
    if mode == "plain":
        plain(svg, out)
    else:
        jade(svg, out, *[float(x) for x in sys.argv[4:6]])
