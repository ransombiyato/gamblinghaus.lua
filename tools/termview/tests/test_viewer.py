"""Viewer state machine tests (no tty / X server needed)."""

from __future__ import annotations

import numpy as np
import pytest

from termview.capture import BaseCapture
from termview.viewer import MODES, DEPTHS, Viewer, ViewerState


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
        self.moves = []
        self.clicks = 0

    def send_key(self, key, repeat=1):
        self.keys.append(key)
        return True

    def move_mouse(self, dx, dy):
        self.moves.append((dx, dy))

    def click(self, button=1):
        self.clicks += 1


@pytest.fixture
def viewer(screen):
    return Viewer(FakeCapture(screen), state=ViewerState(cols=40), injector=FakeInjector())


def test_step_returns_composed_frame(viewer):
    out = viewer.step()
    assert "\x1b[H" in out          # cursor home
    assert "termview" in out        # status line
    assert "\x1b[?25l" not in out   # cursor hiding is the run() loop's job


def test_quit_key(viewer):
    assert viewer.handle_keys(["`", "q"]) == "quit"
    assert viewer.handle_keys(["`", "ctrl-c"]) == "quit"
    assert viewer.handle_keys(["`", "ctrl-d"]) == "quit"


def test_mode_cycles(viewer):
    assert viewer.state.mode == "half"
    viewer.handle_keys(["`", "m"])
    assert viewer.state.mode == "full"
    viewer.handle_keys(["`", "m"])
    assert viewer.state.mode == "ascii"
    viewer.handle_keys(["`", "m"])
    assert viewer.state.mode == MODES[0]


def test_depth_cycles(viewer):
    assert viewer.state.depth == DEPTHS[0]
    viewer.handle_keys(["`", "d"])
    assert viewer.state.depth == DEPTHS[1]


def test_zoom_bounds(viewer):
    for _ in range(50):
        viewer.handle_keys(["`", "-"])
    assert viewer.state.cols == 20
    for _ in range(50):
        viewer.handle_keys(["`", "+"])
    assert viewer.state.cols == 400


def test_pause_toggle(viewer):
    assert not viewer.state.paused
    viewer.handle_keys(["`", "p"])
    assert viewer.state.paused


def test_help_toggle(viewer):
    assert not viewer.state.show_help
    viewer.handle_keys(["`", "h"])
    assert viewer.state.show_help
    out = viewer.step()
    assert "controls" in out or "termview:" in out


def test_keys_forwarded_to_game(viewer):
    viewer.handle_keys(["w", "a", "s", "d", "up", "enter"])
    assert viewer.injector.keys == ["w", "a", "s", "d", "up", "enter"]


def test_mouse_look_mode_redirects_arrows(viewer):
    viewer.handle_keys(["`", "i"])
    assert viewer.state.mouse_look
    viewer.handle_keys(["up", "down", "left", "right"])
    assert viewer.injector.moves == [(0, -20), (0, 20), (-20, 0), (20, 0)]
    assert viewer.injector.keys == []  # not sent as keys


def test_mouse_look_click(viewer):
    viewer.handle_keys(["`", "i", "enter"])
    assert viewer.injector.clicks == 1


def test_local_commands_not_forwarded(viewer):
    viewer.handle_keys(["`", "m", "`", "d", "`", "p", "`", "h", "`", "r", "`", "i", "`", "+", "`", "-"])
    assert viewer.injector.keys == []


def test_prefix_key_arms_command_mode(viewer):
    viewer.handle_keys(["`"])
    assert viewer.state.command_mode
    viewer.handle_keys(["m"])
    assert not viewer.state.command_mode
    assert viewer.injector.keys == []  # the m was consumed as a command


def test_unknown_command_ignored(viewer):
    viewer.handle_keys(["`", "z"])
    assert viewer.injector.keys == []
    assert viewer.state.mode == MODES[0]
