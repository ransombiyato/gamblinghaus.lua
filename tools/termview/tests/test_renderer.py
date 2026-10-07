"""Renderer tests: geometry, orientation, and ANSI structure."""

from __future__ import annotations

import re

import numpy as np
import pytest

from termview.renderer import (
    ASCII_RAMP,
    FULL_BLOCK,
    UPPER_HALF,
    RenderOptions,
    fit_grid,
    render,
)
from tests.conftest import split_frame


ANSI_RE = re.compile(r"\x1b\[[0-9;]*m")


def strip_ansi(s: str) -> str:
    return ANSI_RE.sub("", s)


def test_fit_grid_preserves_aspect():
    # 16:9 source at 80 cols -> rows = 80 * (9/16) / 2 = 22.5 -> 23 (round half even -> 22)
    assert fit_grid(160, 90, 80) in (22, 23)
    assert fit_grid(100, 100, 50) == 25  # square: rows = cols/2
    assert fit_grid(0, 10, 10) == 1


def test_render_rejects_bad_shape():
    with pytest.raises(ValueError):
        render(np.zeros((10, 10), dtype=np.uint8), 10, RenderOptions())


@pytest.mark.parametrize("mode", ["half", "full", "ascii"])
def test_render_line_and_col_count(screen, mode):
    cols, rows = 40, 10
    out = render(screen, cols, RenderOptions(mode=mode), rows=rows)
    lines = out.split("\n")
    assert len(lines) == rows
    for line in lines:
        assert len(strip_ansi(line)) == cols


def test_half_block_orientation():
    """Top colour must land on the upper half-block glyph, bottom on bg.

    Build a frame whose colour boundary falls *inside* the first output cell:
    the cell's top sub-pixel is red, its bottom sub-pixel is blue.
    """
    frame = np.zeros((16, 20, 3), dtype=np.uint8)
    frame[:2] = (255, 0, 0)     # top sub-pixel of cell 0
    frame[2:] = (0, 0, 255)     # bottom sub-pixel of cell 0 and everything below
    out = render(frame, 8, RenderOptions(mode="half", depth="truecolor"), rows=4)
    first_line = out.split("\n")[0]
    # Foreground of the glyph should be the top (red) colour.
    assert "38;2;255;0;0" in first_line
    assert "48;2;0;0;255" in first_line
    assert UPPER_HALF in first_line


def test_full_block_glyph():
    frame = split_frame(4, 4, (255, 0, 0), (0, 0, 255))
    out = render(frame, 2, RenderOptions(mode="full"), rows=2)
    assert FULL_BLOCK in out


def test_ascii_uses_ramp_only():
    frame = split_frame(8, 8, (0, 0, 0), (255, 255, 255))
    out = strip_ansi(render(frame, 4, RenderOptions(mode="ascii", color_ascii=False), rows=4))
    allowed = set(ASCII_RAMP)
    for ch in out.replace("\n", ""):
        assert ch in allowed


def test_ascii_dark_vs_light():
    dark = np.zeros((8, 8, 3), dtype=np.uint8)
    light = np.full((8, 8, 3), 255, dtype=np.uint8)
    d = strip_ansi(render(dark, 4, RenderOptions(mode="ascii", color_ascii=False), rows=2))
    l = strip_ansi(render(light, 4, RenderOptions(mode="ascii", color_ascii=False), rows=2))
    # Light should use a denser glyph than dark.
    d_max = max(ASCII_RAMP.index(ch) for ch in d if ch in ASCII_RAMP)
    l_max = max(ASCII_RAMP.index(ch) for ch in l if ch in ASCII_RAMP)
    assert l_max > d_max
    assert d_max == 0  # all black -> spaces
    assert l_max == len(ASCII_RAMP) - 1  # all white -> '@'


def test_depth_256_uses_256_sgr(screen):
    out = render(screen, 20, RenderOptions(mode="half", depth="256"), rows=5)
    assert "38;5;" in out
    assert "48;5;" in out


def test_render_is_deterministic(screen):
    opts = RenderOptions(mode="half")
    a = render(screen, 30, opts, rows=8)
    b = render(screen, 30, opts, rows=8)
    assert a == b
