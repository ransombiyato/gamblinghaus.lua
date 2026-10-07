"""Colour quantization + SGR tests."""

from __future__ import annotations

import numpy as np
import pytest

from termview.color import ANSI16, XTERM256, quantize, sgr


def test_palettes_well_formed():
    assert ANSI16.shape == (16, 3)
    assert XTERM256.shape == (256, 3)
    # First 16 must match ANSI.
    assert np.array_equal(XTERM256[:16], ANSI16)
    # Cube corners.
    assert tuple(XTERM256[16]) == (0, 0, 0)
    assert tuple(XTERM256[231]) == (255, 255, 255)


def test_quantize_16_exact_primaries():
    # Pure red should map to ANSI index 1.
    red = np.zeros((2, 2, 3), dtype=np.uint8)
    red[:, :] = (205, 49, 49)
    out = quantize(red, "16")
    assert out.shape == (2, 2)
    assert (out == 1).all()


def test_quantize_256_greyscale_ramp():
    grey = np.zeros((1, 1, 3), dtype=np.uint8)
    grey[:, :] = (8, 8, 8)
    out = quantize(grey, "256")
    assert out[0, 0] == 232


def test_quantize_truecolor_is_identity():
    arr = np.random.randint(0, 256, (5, 7, 3), dtype=np.uint8)
    assert np.array_equal(quantize(arr, "truecolor"), arr)


def test_sgr_truecolor_fg_bg():
    seq = sgr(fg=(1, 2, 3), bg=(4, 5, 6), depth="truecolor")
    assert seq == "\x1b[38;2;1;2;3;48;2;4;5;6m"


def test_sgr_256():
    assert sgr(fg=200, depth="256") == "\x1b[38;5;200m"
    assert sgr(bg=12, depth="256") == "\x1b[48;5;12m"


def test_sgr_16_normal_and_bright():
    assert sgr(fg=1, depth="16") == "\x1b[31m"
    assert sgr(fg=9, depth="16") == "\x1b[91m"
    assert sgr(bg=2, depth="16") == "\x1b[42m"
    assert sgr(bg=10, depth="16") == "\x1b[102m"


def test_sgr_empty_when_no_colour():
    assert sgr(depth="truecolor") == ""
