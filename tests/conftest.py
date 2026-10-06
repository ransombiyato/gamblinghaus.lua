"""Shared test fixtures / helpers."""

from __future__ import annotations

import numpy as np
import pytest


def solid(w: int, h: int, rgb: tuple[int, int, int]) -> np.ndarray:
    arr = np.zeros((h, w, 3), dtype=np.uint8)
    arr[:, :] = rgb
    return arr


def split_frame(w: int, h: int, top_rgb, bottom_rgb) -> np.ndarray:
    """Top half one colour, bottom half another - used to verify orientation."""
    arr = np.zeros((h, w, 3), dtype=np.uint8)
    arr[: h // 2] = top_rgb
    arr[h // 2 :] = bottom_rgb
    return arr


@pytest.fixture
def screen() -> np.ndarray:
    """A 320x180 'game-like' frame with distinct top/bottom and a marker."""
    arr = np.zeros((180, 320, 3), dtype=np.uint8)
    arr[:90] = (30, 120, 220)      # sky-ish blue on top
    arr[90:] = (40, 180, 60)       # grass-ish green on bottom
    arr[80:100, 140:180] = (255, 255, 255)  # a white "HUD" block near centre
    return arr
