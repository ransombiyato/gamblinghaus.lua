"""CLI + interactive loop tests."""

from __future__ import annotations

import io
import re
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

from termview.capture import BaseCapture
from termview.viewer import Viewer, ViewerState

from tests.conftest import coloured_window, wait_for_window, wait_for_xvfb

REPO = Path(__file__).resolve().parents[1]


class FakeCapture(BaseCapture):
    def __init__(self, frame):
        self.frame = frame
        self.closed = False

    def grab(self):
        return self.frame

    def close(self):
        self.closed = True


class FakeInjector:
    def __init__(self):
        self.keys = []

    def send_key(self, key, repeat=1):
        self.keys.append(key)
        return True

    def move_mouse(self, dx, dy):
        pass

    def click(self, button=1):
        pass


class FakeReader:
    """Yields a scripted list of key batches, then nothing."""

    def __init__(self, batches):
        self.batches = list(batches)

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False

    def read(self):
        if self.batches:
            return self.batches.pop(0)
        return []


def test_run_loop_draws_and_quits(screen):
    out = io.StringIO()
    cap = FakeCapture(screen)
    viewer = Viewer(cap, state=ViewerState(cols=30, fps=1000), injector=FakeInjector(), out=out)
    reader = FakeReader([[], [], ["`", "q"]])
    rc = viewer.run(reader=reader)
    assert rc == 0
    text = out.getvalue()
    assert "\x1b[?25l" in text   # cursor hidden on start
    assert "\x1b[?25h" in text   # cursor restored on exit
    assert "termview" in text    # at least one frame drawn
    assert cap.closed


def test_run_loop_pause_skips_draw(screen):
    out = io.StringIO()
    cap = FakeCapture(screen)
    viewer = Viewer(cap, state=ViewerState(cols=30, fps=1000), injector=FakeInjector(), out=out)
    # pause, then quit; only the pause redraw should be drawn, no extra frames.
    reader = FakeReader([["`", "p"], ["`", "q"]])
    viewer.run(reader=reader)
    assert "PAUSED" in out.getvalue()


def test_cli_image(tmp_path, screen):
    path = tmp_path / "in.png"
    Image.fromarray(screen, "RGB").save(path)
    proc = subprocess.run(
        [sys.executable, "-m", "termview", "image", str(path), "--cols", "40", "--rows", "10"],
        cwd=REPO,
        capture_output=True,
        text=True,
    )
    assert proc.returncode == 0, proc.stderr
    assert len(proc.stdout.rstrip("\n").split("\n")) == 10


def test_cli_image_ascii(tmp_path, screen):
    path = tmp_path / "in.png"
    Image.fromarray(screen, "RGB").save(path)
    proc = subprocess.run(
        [sys.executable, "-m", "termview", "image", str(path), "--mode", "ascii",
         "--cols", "40", "--rows", "10"],
        cwd=REPO,
        capture_output=True,
        text=True,
    )
    assert proc.returncode == 0, proc.stderr
    assert len(proc.stdout.rstrip("\n").split("\n")) == 10


def _have(cmd: str) -> bool:
    return shutil.which(cmd) is not None


@pytest.mark.skipif(not _have("Xvfb") or not _have("xdotool"), reason="needs Xvfb+xdotool")
def test_cli_grab_under_xvfb():
    display = ":92"
    env = {**os.environ, "DISPLAY": display}
    xvfb = subprocess.Popen(
        ["Xvfb", display, "-screen", "0", "320x240x24"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    try:
        if not wait_for_xvfb(display, env):
            pytest.skip("Xvfb did not come up")
        with coloured_window(display, "termview-cli", 240, 160, (0, 0, 255)):
            proc = subprocess.run(
                [sys.executable, "-m", "termview", "grab", "--window", "termview-cli",
                 "--display", display, "--cols", "30", "--rows", "8"],
                cwd=REPO, capture_output=True, text=True, env=env,
            )
            assert proc.returncode == 0, proc.stderr
            assert len(proc.stdout.rstrip("\n").split("\n")) == 8
    finally:
        xvfb.terminate()
        xvfb.wait(timeout=5)


@pytest.mark.skipif(not _have("Xvfb") or not _have("xev"), reason="needs Xvfb+xev")
def test_live_cli_drives_real_window(tmp_path):
    """Full end-to-end: run ``termview live``, feed keys on stdin, verify the
    keys reach a real X client and that frames are drawn on stdout.

    xev is the target client so we can assert on delivered keysyms.
    """
    display = ":91"
    xev_out = tmp_path / "xev.txt"
    env = {**os.environ, "DISPLAY": display}
    xvfb = subprocess.Popen(
        ["Xvfb", display, "-screen", "0", "400x300x24"],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    )
    xev = None
    try:
        for _ in range(50):
            if subprocess.run(["xdpyinfo"], env=env, capture_output=True).returncode == 0:
                break
            time.sleep(0.1)
        xev = subprocess.Popen(
            ["stdbuf", "-oL", "xev", "-name", "termview-live"],
            env=env, stdout=open(xev_out, "wb"), stderr=subprocess.STDOUT,
        )
        wait_for_window("termview-live", display)
        time.sleep(0.3)

        proc = subprocess.Popen(
            [sys.executable, "-m", "termview", "live", "--window", "termview-live",
             "--display", display, "--cols", "40", "--fps", "30"],
            cwd=REPO, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
            stderr=subprocess.PIPE, env=env,
        )
        # Feed movement keys, let a few frames render, then quit with `` `q ``.
        time.sleep(0.8)
        try:
            proc.stdin.write(b"wasd")
            proc.stdin.flush()
        except BrokenPipeError:
            pass
        time.sleep(0.8)
        try:
            proc.stdin.write(b"`q")
            proc.stdin.flush()
        except BrokenPipeError:
            pass
        out, err = proc.communicate(timeout=10)

        assert b"\x1b[H" in out, f"no frames were drawn; stderr={err[:500]!r}"
        assert proc.returncode == 0, f"exit={proc.returncode} stderr={err[:500]!r}"
        time.sleep(0.3)
        text = xev_out.read_text(errors="replace")
        syms = re.findall(r"keysym 0x[0-9a-f]+, (\w+)", text)
        assert all(k in syms for k in "wasd"), f"keys not received; saw {syms}"
    finally:
        if xev is not None:
            xev.terminate()
        xvfb.terminate()
        xvfb.wait(timeout=5)
