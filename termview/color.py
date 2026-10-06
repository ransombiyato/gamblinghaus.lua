"""Colour handling: palette quantization + ANSI SGR sequences.

The renderer needs two things from this module:

* a fast way to quantize an RGB frame to a small set of terminal colours, and
* the escape sequences that actually paint those colours on screen.

We support the three depths a terminal might have:

``truecolor``   24-bit ``38;2;r;g;b`` (best fidelity, used when available)
``256``         xterm-256 cube/greyscale approximation
``16``          the classic ANSI palette

Everything degrades gracefully so a dumb terminal still produces *something*
recognisable instead of erroring out.
"""

from __future__ import annotations

from typing import Sequence

import numpy as np

# The 16 basic ANSI colours (0-7 normal, 8-15 bright) as RGB.
ANSI16 = np.array(
    [
        (0, 0, 0),        # 0 black
        (205, 49, 49),    # 1 red
        (13, 188, 121),   # 2 green
        (229, 229, 16),   # 3 yellow
        (36, 114, 200),   # 4 blue
        (188, 63, 188),   # 5 magenta
        (17, 168, 205),   # 6 cyan
        (229, 229, 229),  # 7 white
        (102, 102, 102),  # 8 bright black / grey
        (241, 76, 76),    # 9 bright red
        (35, 209, 139),   # 10 bright green
        (245, 245, 67),   # 11 bright yellow
        (59, 142, 234),   # 12 bright blue
        (214, 112, 214),  # 13 bright magenta
        (41, 184, 219),   # 14 bright cyan
        (255, 255, 255),  # 15 bright white
    ],
    dtype=np.int16,
)


def _build_xterm256() -> np.ndarray:
    """Return the 256-entry xterm palette as an (256, 3) int16 array."""
    pal = np.zeros((256, 3), dtype=np.int16)
    pal[:16] = ANSI16
    # 6x6x6 colour cube: indices 16..231.
    steps = [0, 95, 135, 175, 215, 255]
    idx = 16
    for r in steps:
        for g in steps:
            for b in steps:
                pal[idx] = (r, g, b)
                idx += 1
    # 24-step greyscale ramp: indices 232..255.
    for i in range(24):
        v = 8 + i * 10
        pal[232 + i] = (v, v, v)
    return pal


XTERM256 = _build_xterm256()


def quantize(rgb: np.ndarray, depth: str) -> np.ndarray:
    """Map an ``(H, W, 3)`` uint8 RGB array onto palette indices.

    ``depth`` is one of ``"16"``, ``"256"`` or ``"truecolor"``. For
    ``truecolor`` the input is returned unchanged (there is nothing to
    quantize) so callers can treat every depth uniformly.
    """
    if depth == "truecolor":
        return rgb

    palette = ANSI16 if depth == "16" else XTERM256
    flat = rgb.reshape(-1, 3).astype(np.int32)
    # Nearest palette entry by squared euclidean distance. Chunked so we never
    # materialise an (N, 256) matrix for large frames.
    out = np.empty(flat.shape[0], dtype=np.uint8)
    chunk = 1 << 16
    pal32 = palette.astype(np.int32)
    for start in range(0, flat.shape[0], chunk):
        block = flat[start : start + chunk]
        d = ((block[:, None, :] - pal32[None, :, :]) ** 2).sum(axis=2)
        out[start : start + chunk] = np.argmin(d, axis=1)
    return out.reshape(rgb.shape[:2])


def sgr(fg: int | None = None, bg: int | None = None, depth: str = "truecolor") -> str:
    """Build an SGR escape sequence for a foreground and/or background colour.

    ``fg``/``bg`` are either palette indices (for 16/256) or ``(r, g, b)``
    tuples (for truecolor). Passing ``None`` omits that component.
    """
    parts = []
    if fg is not None:
        parts.extend(_sgr_component(fg, background=False, depth=depth))
    if bg is not None:
        parts.extend(_sgr_component(bg, background=True, depth=depth))
    if not parts:
        return ""
    return "\x1b[" + ";".join(str(p) for p in parts) + "m"


def _sgr_component(color, background: bool, depth: str) -> Sequence[int]:
    if depth == "truecolor":
        r, g, b = color
        return (48 if background else 38, 2, int(r), int(g), int(b))
    if depth == "256":
        return (48 if background else 38, 5, int(color))
    # 16-colour: codes 30-37/40-47 normal, 90-97/100-107 bright.
    idx = int(color)
    if idx < 8:
        return (40 + idx if background else 30 + idx,)
    return (100 + (idx - 8) if background else 90 + (idx - 8),)


RESET = "\x1b[0m"
