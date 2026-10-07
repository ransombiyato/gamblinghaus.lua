"""Shared test fixtures / helpers."""

from __future__ import annotations

import contextlib
import os
import subprocess
import sys
import time
from pathlib import Path

import numpy as np
import pytest

REPO = Path(__file__).resolve().parents[1]
_XWINDOW = REPO / "tests" / "_xwindow.py"


def wait_for_xvfb(display: str, env: dict, timeout: float = 8.0) -> bool:
    """Block until ``xdpyinfo`` can talk to ``display``."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if subprocess.run(["xdpyinfo"], env=env, capture_output=True).returncode == 0:
            return True
        time.sleep(0.1)
    return False


def wait_for_window(name: str, display: str | None = None, timeout: float = 10.0) -> str:
    """Block until an X window matching ``name`` exists; return its id.

    Window managers on CI can be slow to map a new window, so tests must poll
    for it rather than sleeping a fixed amount.
    """
    from termview.capture import find_window_id

    deadline = time.time() + timeout
    last_exc: Exception | None = None
    while time.time() < deadline:
        try:
            return find_window_id(name, display)
        except Exception as exc:  # noqa: BLE001 - retry any lookup failure
            last_exc = exc
            time.sleep(0.1)
    raise AssertionError(f"window {name!r} never appeared: {last_exc}")


@contextlib.contextmanager
def coloured_window(display: str, name: str, w: int, h: int, rgb: tuple[int, int, int]):
    """Map a plain coloured X window for the duration of the block.

    Avoids depending on a terminal emulator: we create the window ourselves via
    python-xlib, so it is mapped and painted the moment it exists.
    """
    env = {**os.environ, "DISPLAY": display}
    r, g, b = (c * 257 for c in rgb)  # 8-bit -> 16-bit channels
    proc = subprocess.Popen(
        [sys.executable, str(_XWINDOW), display, name, str(w), str(h), str(r), str(g), str(b)],
        env=env, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
    )
    try:
        wait_for_window(name, display)
        yield
    finally:
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            proc.kill()


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
