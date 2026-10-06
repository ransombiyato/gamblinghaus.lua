"""Parse termview ANSI output back into pixels.

This is the inverse of :mod:`termview.renderer`. It exists for two reasons:

* visual verification - turn a rendered frame back into a PNG so a human (or a
  visual-regression test) can see exactly what the terminal would show, and
* fidelity measurement - round-tripping an image through render -> parse lets
  us quantify how close the terminal display is to the source.

Only the SGR subset that the renderer emits is understood (truecolor, 256 and
16-colour fg/bg) plus the block glyphs.
"""

from __future__ import annotations

import re

import numpy as np
from PIL import Image

from .color import ANSI16, XTERM256

SGR_RE = re.compile(r"\x1b\[([0-9;]*)m")
UPPER_HALF = "\u2580"
LOWER_HALF = "\u2584"
FULL_BLOCK = "\u2588"

DEFAULT_FG = (255, 255, 255)
DEFAULT_BG = (0, 0, 0)


def _palette_rgb(index: int) -> tuple[int, int, int]:
    if 0 <= index < 256:
        return tuple(int(v) for v in XTERM256[index])
    return DEFAULT_FG


def parse_line(
    line: str,
    default_fg: tuple[int, int, int] = DEFAULT_FG,
    default_bg: tuple[int, int, int] = DEFAULT_BG,
) -> list[tuple[str, tuple[int, int, int], tuple[int, int, int]]]:
    """Return ``(char, fg, bg)`` for each cell in one rendered line."""
    fg, bg = default_fg, default_bg
    out: list[tuple[str, tuple[int, int, int], tuple[int, int, int]]] = []
    i = 0
    while i < len(line):
        m = SGR_RE.match(line, i)
        if m:
            params = [int(p) for p in m.group(1).split(";") if p != ""] or [0]
            j = 0
            while j < len(params):
                p = params[j]
                if p == 0:
                    fg, bg = default_fg, default_bg
                    j += 1
                elif p == 38 and j + 1 < len(params) and params[j + 1] == 2:
                    fg = (params[j + 2], params[j + 3], params[j + 4])
                    j += 5
                elif p == 48 and j + 1 < len(params) and params[j + 1] == 2:
                    bg = (params[j + 2], params[j + 3], params[j + 4])
                    j += 5
                elif p == 38 and j + 1 < len(params) and params[j + 1] == 5:
                    fg = _palette_rgb(params[j + 2])
                    j += 3
                elif p == 48 and j + 1 < len(params) and params[j + 1] == 5:
                    bg = _palette_rgb(params[j + 2])
                    j += 3
                elif 30 <= p <= 37:
                    fg = tuple(int(v) for v in ANSI16[p - 30])
                    j += 1
                elif 90 <= p <= 97:
                    fg = tuple(int(v) for v in ANSI16[p - 90 + 8])
                    j += 1
                elif 40 <= p <= 47:
                    bg = tuple(int(v) for v in ANSI16[p - 40])
                    j += 1
                elif 100 <= p <= 107:
                    bg = tuple(int(v) for v in ANSI16[p - 100 + 8])
                    j += 1
                else:
                    j += 1
            i = m.end()
            continue
        out.append((line[i], fg, bg))
        i += 1
    return out


def ansi_to_rgb(text: str, cell: int = 1) -> np.ndarray:
    """Rasterise rendered ANSI text into an ``(H, W, 3)`` uint8 RGB array.

    ``cell`` is the pixel size of one terminal cell's width; height is twice
    that, matching the half-block geometry.
    """
    lines = text.rstrip("\n").split("\n")
    parsed = [parse_line(line) for line in lines]
    rows = len(parsed)
    cols = max((len(c) for c in parsed), default=0)
    img = np.zeros((max(1, rows * 2 * cell), max(1, cols * cell), 3), dtype=np.uint8)
    for r, cells in enumerate(parsed):
        for c, (ch, fg, bg) in enumerate(cells):
            y0, x0 = r * 2 * cell, c * cell
            if ch == UPPER_HALF:
                top, bottom = fg, bg
            elif ch == LOWER_HALF:
                top, bottom = bg, fg
            elif ch == FULL_BLOCK:
                top = bottom = fg
            else:
                # ascii glyph: paint the whole cell with the glyph colour.
                top = bottom = fg
            img[y0 : y0 + cell, x0 : x0 + cell] = top
            img[y0 + cell : y0 + 2 * cell, x0 : x0 + cell] = bottom
    return img


def ansi_to_image(text: str, cell: int = 8) -> Image.Image:
    return Image.fromarray(ansi_to_rgb(text, cell=cell), "RGB")
