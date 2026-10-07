"""Turn an RGB frame into a grid of ANSI terminal cells.

Three rendering strategies are supported:

``half``  Two vertical sub-pixels per cell using the upper-half-block glyph
          (``\u2580``). The glyph is painted with the *top* colour and the cell
          background with the *bottom* colour, which gives square pixels on a
          normal 1:2 terminal cell - the sweet spot for pixel-art games.

``full``  One solid block per cell. Cheap, but pixels are 1:2 so detail is
          halved vertically.

``ascii`` Luminance mapped through a character ramp. No colour needed, so it
          works even on a monochrome terminal.

The frame is resized with an area filter (``BOX``) so downscaling averages
pixels rather than dropping them, which matters a lot for text and HUDs.
"""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np
from PIL import Image

from .color import RESET, quantize, sgr

# Dark -> light ramp for ascii mode.
ASCII_RAMP = " .:-=+*#%@"
UPPER_HALF = "\u2580"
FULL_BLOCK = "\u2588"


@dataclass
class RenderOptions:
    mode: str = "half"           # half | full | ascii
    depth: str = "truecolor"     # truecolor | 256 | 16
    resample: str = "box"        # box | nearest | bilinear
    ascii_ramp: str = ASCII_RAMP
    color_ascii: bool = True     # tint ascii glyphs with the source colour
    invert: bool = False


def fit_grid(img_w: int, img_h: int, cols: int) -> int:
    """Return the row count that preserves aspect for a given column count.

    Terminal cells are ~twice as tall as wide, so the physical height of a grid
    is ``rows * 2`` cells-units. Solving ``(rows * 2) / cols == img_h / img_w``
    gives the formula below.
    """
    if img_w <= 0 or cols <= 0:
        return 1
    rows = round(cols * (img_h / img_w) / 2)
    return max(1, rows)


_RESAMPLE = {
    "box": Image.BOX,
    "nearest": Image.NEAREST,
    "bilinear": Image.BILINEAR,
}


def _resize(rgb: np.ndarray, size: tuple[int, int], resample: str) -> np.ndarray:
    img = Image.fromarray(rgb, "RGB")
    return np.asarray(img.resize(size, _RESAMPLE.get(resample, Image.BOX)))


def render(rgb: np.ndarray, cols: int, opts: RenderOptions, rows: int | None = None) -> str:
    """Render an ``(H, W, 3)`` uint8 RGB array into an ANSI string.

    Returns one string containing ``rows`` lines separated by ``\n`` (no
    trailing newline). Callers are responsible for cursor positioning.
    """
    if rgb.ndim != 3 or rgb.shape[2] != 3:
        raise ValueError("render() expects an (H, W, 3) RGB array")

    img_h, img_w = rgb.shape[:2]
    if rows is None:
        rows = fit_grid(img_w, img_h, cols)
    rows = max(1, rows)

    if opts.mode == "ascii":
        return _render_ascii(rgb, cols, rows, opts)
    if opts.mode == "half":
        return _render_half(rgb, cols, rows, opts)
    return _render_full(rgb, cols, rows, opts)


def _render_half(rgb: np.ndarray, cols: int, rows: int, opts: RenderOptions) -> str:
    sub = _resize(rgb, (cols, rows * 2), opts.resample)
    top = sub[0::2]   # (rows, cols, 3)
    bottom = sub[1::2]
    if top.shape[0] != rows:
        top = top[:rows]
        bottom = bottom[:rows]

    if opts.depth != "truecolor":
        top_q = quantize(top, opts.depth)
        bottom_q = quantize(bottom, opts.depth)
    else:
        top_q = top
        bottom_q = bottom

    lines = []
    for r in range(rows):
        parts = []
        prev_fg = prev_bg = object()
        for c in range(cols):
            fg = top_q[r, c]
            bg = bottom_q[r, c]
            fk = tuple(int(x) for x in fg) if opts.depth == "truecolor" else int(fg)
            bk = tuple(int(x) for x in bg) if opts.depth == "truecolor" else int(bg)
            if fk != prev_fg or bk != prev_bg:
                parts.append(sgr(fg=fk, bg=bk, depth=opts.depth))
                prev_fg, prev_bg = fk, bk
            parts.append(UPPER_HALF)
        parts.append(RESET)
        lines.append("".join(parts))
    return "\n".join(lines)


def _render_full(rgb: np.ndarray, cols: int, rows: int, opts: RenderOptions) -> str:
    # Cell is 1:2, so physical height is rows*2 -> resize to that many rows and
    # treat each output row as one cell.
    sub = _resize(rgb, (cols, rows), opts.resample)
    if opts.depth != "truecolor":
        sub_q = quantize(sub, opts.depth)
    else:
        sub_q = sub

    lines = []
    for r in range(rows):
        parts = []
        prev = object()
        for c in range(cols):
            col = sub_q[r, c]
            key = tuple(int(x) for x in col) if opts.depth == "truecolor" else int(col)
            if key != prev:
                parts.append(sgr(fg=key, depth=opts.depth))
                prev = key
            parts.append(FULL_BLOCK)
        parts.append(RESET)
        lines.append("".join(parts))
    return "\n".join(lines)


def _render_ascii(rgb: np.ndarray, cols: int, rows: int, opts: RenderOptions) -> str:
    sub = _resize(rgb, (cols, rows * 2), opts.resample)
    # Collapse the two vertical sub-pixels into one luminance per cell.
    lum = (0.2126 * sub[..., 0] + 0.7152 * sub[..., 1] + 0.0722 * sub[..., 2])
    lum = lum.reshape(rows, 2, cols).mean(axis=1)
    if opts.invert:
        lum = 255.0 - lum
    ramp = opts.ascii_ramp or ASCII_RAMP
    n = len(ramp) - 1
    idx = np.clip((lum / 255.0 * n).round().astype(np.int32), 0, n)

    if opts.color_ascii and opts.depth != "truecolor":
        cell_rgb = _resize(rgb, (cols, rows), opts.resample)
        cell_q = quantize(cell_rgb, opts.depth)
    else:
        cell_rgb = None
        cell_q = None

    lines = []
    for r in range(rows):
        parts = []
        for c in range(cols):
            ch = ramp[idx[r, c]]
            if opts.color_ascii:
                if opts.depth == "truecolor":
                    fg = tuple(int(x) for x in sub.reshape(rows, 2, cols, 3)[r, :, c].mean(axis=0))
                else:
                    fg = int(cell_q[r, c])
                parts.append(sgr(fg=fg, depth=opts.depth))
            parts.append(ch)
        parts.append(RESET)
        lines.append("".join(parts))
    return "\n".join(lines)
