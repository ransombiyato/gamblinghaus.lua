"""Round-trip fidelity tests: render -> parse back to pixels -> compare.

These are the tests that decide whether the terminal display is actually a
faithful 1:1 reproduction. For half-block mode the round trip should be close
to exact for flat-colour images, because each cell stores two colours.
"""

from __future__ import annotations

import numpy as np
import pytest
from PIL import Image

from termview.ansi import ansi_to_image, ansi_to_rgb, parse_line
from termview.renderer import RenderOptions, render

from tests.conftest import solid, split_frame


def _downscale(img: np.ndarray, w: int, h: int) -> np.ndarray:
    return np.asarray(Image.fromarray(img, "RGB").resize((w, h), Image.BOX)).astype(int)


def _psnr(a: np.ndarray, b: np.ndarray) -> float:
    a = a.astype(float)
    b = b.astype(float)
    mse = ((a - b) ** 2).mean()
    if mse == 0:
        return float("inf")
    return 10 * np.log10((255.0**2) / mse)


def test_parse_line_truecolor():
    cells = parse_line("\x1b[38;2;10;20;30;48;2;40;50;60mA\x1b[0m")
    assert cells[0] == ("A", (10, 20, 30), (40, 50, 60))


def test_parse_line_256_and_16():
    cells = parse_line("\x1b[38;5;200;48;5;10mX")
    assert cells[0][0] == "X"
    assert cells[0][1] != cells[0][2]
    cells16 = parse_line("\x1b[31;42mY")
    assert cells16[0][1] == (205, 49, 49)
    assert cells16[0][2] == (13, 188, 121)


def test_half_block_flat_colour_is_exact(screen):
    """A flat-colour cell must round-trip to exactly those two colours."""
    flat = solid(64, 64, (30, 120, 220))
    text = render(flat, 16, RenderOptions(mode="half", depth="truecolor"), rows=8)
    back = ansi_to_rgb(text, cell=1)
    # Every cell is one solid colour, so the round trip is lossless.
    assert (back == np.array([30, 120, 220], dtype=np.uint8)).all()


def test_half_block_split_orientation_roundtrip():
    """Top half red, bottom half blue must come back in the right order."""
    frame = np.zeros((16, 8, 3), dtype=np.uint8)
    frame[:2] = (255, 0, 0)
    frame[2:] = (0, 0, 255)
    text = render(frame, 4, RenderOptions(mode="half", depth="truecolor"), rows=4)
    back = ansi_to_rgb(text, cell=1)
    # Row 0 (top sub-pixel) red, row 1 (bottom sub-pixel) blue.
    assert tuple(back[0, 0]) == (255, 0, 0)
    assert tuple(back[1, 0]) == (0, 0, 255)


def test_half_block_fidelity_high_for_gradient():
    """A smooth gradient should reconstruct with high PSNR in half mode."""
    h, w = 120, 160
    x = np.linspace(0, 255, w, dtype=np.uint8)
    frame = np.zeros((h, w, 3), dtype=np.uint8)
    frame[:, :, 0] = x[None, :]
    frame[:, :, 1] = np.linspace(0, 255, h, dtype=np.uint8)[:, None]
    frame[:, :, 2] = 128
    cols, rows = 80, 30
    text = render(frame, cols, RenderOptions(mode="half", depth="truecolor"), rows=rows)
    back = ansi_to_rgb(text, cell=1)
    assert back.shape == (rows * 2, cols, 3)
    ref = _downscale(frame, cols, rows * 2)
    psnr = _psnr(ref, back)
    # Half-block truecolor stores both sub-pixel colours, so this should be
    # essentially exact (BOX resize averages, which is what the renderer does).
    assert psnr > 30, f"PSNR too low: {psnr:.1f}"


def test_full_mode_fidelity_reasonable():
    h, w = 120, 160
    frame = np.zeros((h, w, 3), dtype=np.uint8)
    frame[:, :, 0] = np.linspace(0, 255, w, dtype=np.uint8)[None, :]
    frame[:, :, 1] = np.linspace(0, 255, h, dtype=np.uint8)[:, None]
    cols, rows = 80, 30
    text = render(frame, cols, RenderOptions(mode="full", depth="truecolor"), rows=rows)
    back = ansi_to_rgb(text, cell=1)
    # Full-block cells paint both sub-rows identically; collapse to one row/cell.
    back = back.reshape(rows, 2, cols, 3).mean(axis=1)
    ref = _downscale(frame, cols, rows)
    assert _psnr(ref, back) > 25


def test_ansi_to_image_dimensions():
    text = render(solid(32, 32, (1, 2, 3)), 8, RenderOptions(), rows=4)
    img = ansi_to_image(text, cell=6)
    assert img.size == (8 * 6, 4 * 2 * 6)


def test_demo_frame_survives_roundtrip():
    """A HUD-like frame with text-ish detail should stay recognisable."""
    frame = solid(200, 120, (16, 24, 32))
    frame[20:40, 20:120] = (41, 140, 64)     # green "text" block
    frame[80:100, 30:90] = (200, 200, 40)    # yellow block
    text = render(frame, 60, RenderOptions(mode="half", depth="truecolor"), rows=18)
    back = ansi_to_rgb(text, cell=1)
    ref = _downscale(frame, 60, 36)
    assert _psnr(ref, back) > 30
