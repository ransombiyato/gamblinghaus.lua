"""End-to-end test: render a real file, and capture a real Xvfb X window."""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import time

import numpy as np
import pytest
from PIL import Image

from termview.capture import ImageCapture, X11Capture, find_window_id, window_geometry
from termview.renderer import RenderOptions, render

from tests.conftest import wait_for_window, wait_for_xvfb


def _have(cmd: str) -> bool:
    return shutil.which(cmd) is not None


def test_image_capture_roundtrip(tmp_path, screen):
    path = tmp_path / "shot.png"
    Image.fromarray(screen, "RGB").save(path)
    cap = ImageCapture(path)
    got = cap.grab()
    assert got.shape == screen.shape
    assert np.array_equal(got, screen)
    out = render(got, 40, RenderOptions(), rows=10)
    assert len(out.split("\n")) == 10


def test_image_capture_missing_file(tmp_path):
    from termview.capture import CaptureError

    with pytest.raises(CaptureError):
        ImageCapture(tmp_path / "nope.png")


@pytest.mark.skipif(not _have("Xvfb"), reason="Xvfb not installed")
@pytest.mark.skipif(not _have("xdotool"), reason="xdotool not installed")
@pytest.mark.skipif(not _have("xterm"), reason="xterm not installed")
def test_live_x11_capture_under_xvfb():
    """Boot a throwaway Xvfb, show a coloured window, capture it for real."""
    display = ":97"
    xvfb = subprocess.Popen(
        ["Xvfb", display, "-screen", "0", "640x480x24"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    xterm = None
    try:
        env = {**os.environ, "DISPLAY": display}
        for _ in range(50):
            if subprocess.run(["xdpyinfo"], env=env, capture_output=True).returncode == 0:
                break
            time.sleep(0.1)
        else:
            pytest.skip("Xvfb did not come up")

        # Paint a known colour into a real (mapped) window. The root window of
        # a bare Xvfb is never painted, so we must capture a window.
        xterm = subprocess.Popen(
            ["xterm", "-T", "termview-capture", "-bg", "blue", "-geometry", "40x12"],
            env=env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        wait_for_window("termview-capture", display)
        time.sleep(0.3)  # let it paint

        cap = X11Capture(display=display, window="termview-capture")
        frame = cap.grab()
        cap.close()
        assert frame.shape[0] > 0 and frame.shape[1] > 0
        # The blue we painted should dominate the frame.
        mean = frame.reshape(-1, 3).mean(axis=0)
        assert mean[2] > 200 and mean[2] > mean[0], f"expected blue, got {mean}"
        # And it should render.
        out = render(frame, 60, RenderOptions(), rows=12)
        assert len(out.split("\n")) == 12
    finally:
        if xterm is not None:
            xterm.terminate()
        xvfb.terminate()
        xvfb.wait(timeout=5)


@pytest.mark.skipif(not _have("Xvfb"), reason="Xvfb not installed")
@pytest.mark.skipif(not _have("xdotool"), reason="xdotool not installed")
@pytest.mark.skipif(not _have("xterm"), reason="xterm not installed")
def test_capture_clamps_window_larger_than_screen():
    """A window bigger than the screen must still capture (clamped), not crash."""
    display = ":89"
    xvfb = subprocess.Popen(
        ["Xvfb", display, "-screen", "0", "320x240x24"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    xterm = None
    try:
        env = {**os.environ, "DISPLAY": display}
        for _ in range(50):
            if subprocess.run(["xdpyinfo"], env=env, capture_output=True).returncode == 0:
                break
            time.sleep(0.1)
        else:
            pytest.skip("Xvfb did not come up")
        # A 60x30 xterm is far wider than the 320px screen.
        xterm = subprocess.Popen(
            ["xterm", "-T", "termview-big", "-bg", "blue", "-geometry", "60x30"],
            env=env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        )
        wait_for_window("termview-big", display)
        time.sleep(0.3)
        cap = X11Capture(display=display, window="termview-big")
        frame = cap.grab()
        cap.close()
        assert frame.shape[0] <= 240 and frame.shape[1] <= 320
        assert frame.shape[0] > 0 and frame.shape[1] > 0
    finally:
        if xterm is not None:
            xterm.terminate()
        xvfb.terminate()
        xvfb.wait(timeout=5)


@pytest.mark.skipif(not _have("Xvfb"), reason="Xvfb not installed")
@pytest.mark.skipif(not _have("xdotool"), reason="xdotool not installed")
def test_window_search_and_geometry_under_xvfb():
    display = ":96"
    xvfb = subprocess.Popen(
        ["Xvfb", display, "-screen", "0", "400x300x24"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    try:
        env = {**os.environ, "DISPLAY": display}
        for _ in range(50):
            if subprocess.run(["xdpyinfo"], env=env, capture_output=True).returncode == 0:
                break
            time.sleep(0.1)
        else:
            pytest.skip("Xvfb did not come up")

        xterm = subprocess.Popen(
            ["xterm", "-T", "termview-e2e", "-geometry", "40x12"],
            env=env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        try:
            win_id = None
            for _ in range(50):
                try:
                    win_id = find_window_id("termview-e2e", display)
                    break
                except Exception:
                    time.sleep(0.1)
            if win_id is None:
                pytest.skip("xterm window never appeared")
            x, y, w, h = window_geometry(win_id, display)
            assert w > 0 and h > 0
        finally:
            xterm.terminate()
    finally:
        xvfb.terminate()
        xvfb.wait(timeout=5)


@pytest.mark.skipif(not _have("Xvfb"), reason="Xvfb not installed")
@pytest.mark.skipif(not _have("xdotool"), reason="xdotool not installed")
@pytest.mark.skipif(not _have("xev"), reason="xev not installed")
def test_input_injection_reaches_real_window(tmp_path):
    """Drive a real X client with InputInjector and confirm it saw the keys.

    We use ``xev`` as the target because it prints every event it receives, so
    we can assert on the actual keysyms delivered. (xterm ignores synthetic
    events in headless containers, but xev - like a normal game - processes
    them.)
    """
    from termview.input import InputInjector

    display = ":94"
    out_file = tmp_path / "xev.txt"
    xvfb = subprocess.Popen(
        ["Xvfb", display, "-screen", "0", "400x300x24"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    xev = None
    try:
        env = {**os.environ, "DISPLAY": display}
        for _ in range(50):
            if subprocess.run(["xdpyinfo"], env=env, capture_output=True).returncode == 0:
                break
            time.sleep(0.1)
        else:
            pytest.skip("Xvfb did not come up")

        xev = subprocess.Popen(
            ["stdbuf", "-oL", "xev", "-name", "termview-xev"],
            env=env, stdout=open(out_file, "wb"), stderr=subprocess.STDOUT,
        )
        wait_for_window("termview-xev", display)
        time.sleep(0.3)

        inj = InputInjector(window="termview-xev", display=display)
        assert inj.focus_window()
        for k in ["w", "a", "s", "d", "up", "enter"]:
            assert inj.send_key(k)
            time.sleep(0.12)
        time.sleep(0.5)

        xev.terminate()
        xev.wait(timeout=5)
        xev = None
        text = out_file.read_text(errors="replace")
        syms = re.findall(r"keysym 0x[0-9a-f]+, (\w+)", text)
        for expected in ("w", "a", "s", "d", "Up", "Return"):
            assert expected in syms, f"{expected} not delivered; saw {syms}"
    finally:
        if xev is not None:
            xev.terminate()
        xvfb.terminate()
        xvfb.wait(timeout=5)
