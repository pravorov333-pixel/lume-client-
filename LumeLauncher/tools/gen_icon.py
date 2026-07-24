#!/usr/bin/env python3
"""Regenerates LumeLauncher/resources/lume.ico + lume.png from the SAME glass-star sparkle mark
gen_menu.py bakes into the in-game menu (draw_glass_star) — keeps the desktop shortcut icon
pixel-consistent with the current single-sparkle branding instead of an older two-sparkle design.
Run: python tools/gen_icon.py
"""
import os, sys
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '..', 'LumeClient', 'tools'))
from gen_menu import draw_glass_star, PAL, new  # noqa: E402

OUT_DIR = os.path.join(os.path.dirname(__file__), '..', 'resources')
pal = PAL['light']

SIZES = [16, 24, 32, 48, 64, 128, 256]


def render(size):
    # new(size,size) already returns an SS-supersampled canvas (see gen_menu.py) — draw at that
    # resolution, downsample once at the end, same pattern gen_menu.py's own save() uses.
    img = new(size, size)
    s = size * 0.86  # inset slightly so the glow halo doesn't clip the canvas edge
    off = (size - s) / 2
    draw_glass_star(img, off, off, s, pal)
    return img.resize((size, size), Image.LANCZOS)


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    imgs = {sz: render(sz) for sz in SIZES}

    imgs[256].save(os.path.join(OUT_DIR, 'lume.png'))

    ico_path = os.path.join(OUT_DIR, 'lume.ico')
    imgs[256].save(ico_path, format='ICO', sizes=[(s, s) for s in SIZES])
    print('wrote', ico_path, 'and lume.png ->', OUT_DIR)


if __name__ == '__main__':
    main()
