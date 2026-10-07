"""Interactive terminal viewer loop.

:class:`Viewer` owns the capture -> render -> draw -> input cycle. The pieces
are kept separate from the terminal I/O so the loop can be driven in tests
without a real tty: :meth:`Viewer.step` returns the exact string that would be
drawn, and :meth:`Viewer.handle_keys` returns the resulting action.
"""

from __future__ import annotations

import shutil
import sys
import time
from dataclasses import dataclass

import numpy as np

from .capture import BaseCapture
from .input import InputInjector, KeyReader
from .renderer import RenderOptions, fit_grid, render

MODES = ["half", "full", "ascii"]
DEPTHS = ["truecolor", "256", "16"]

# Every key is forwarded to the game by default. Local tool commands live
# behind a prefix so they can never steal a movement/interaction key. Tap the
# prefix, then a command letter.
COMMAND_PREFIX = "`"

CLEAR = "\x1b[2J"
HOME = "\x1b[H"
HIDE_CURSOR = "\x1b[?25l"
SHOW_CURSOR = "\x1b[?25h"

HELP = (
    "termview: tap ` then a command | "
    "` q quit  ` h help  ` m mode  ` d depth  ` p pause  ` i mouse-look  "
    "` c click  ` v right-click  ` u / ` j scroll  ` r redraw  ` + / - zoom | "
    "all other keys (wasd, arrows, enter, ...) go to the game"
)

# `-prefixed letters that drive the mouse directly, with no mouse-look required.
MOUSE_COMMANDS = {
    "c": "click",        # left click / break
    "v": "right-click",  # right click / place
    "u": "scroll-up",
    "j": "scroll-down",
}


@dataclass
class ViewerState:
    mode: str = "half"
    depth: str = "truecolor"
    cols: int = 100
    rows: int | None = None
    fps: float = 20.0
    paused: bool = False
    mouse_look: bool = False
    mouse_enabled: bool = False
    show_help: bool = False
    command_mode: bool = False
    prefix: str = COMMAND_PREFIX


class Viewer:
    def __init__(
        self,
        capture: BaseCapture,
        state: ViewerState | None = None,
        injector: InputInjector | None = None,
        out=None,
    ):
        self.capture = capture
        self.state = state or ViewerState()
        self.injector = injector or InputInjector()
        self.out = out or sys.stdout
        self._last_frame = ""

    # -- rendering ---------------------------------------------------------

    def terminal_size(self) -> tuple[int, int]:
        size = shutil.get_terminal_size(fallback=(self.state.cols, 40))
        return size.columns, size.lines

    def effective_cols(self) -> int:
        term_cols, _ = self.terminal_size()
        return max(10, min(self.state.cols, term_cols - 1))

    def render_frame(self, rgb: np.ndarray) -> str:
        cols = self.effective_cols()
        rows = self.state.rows or fit_grid(rgb.shape[1], rgb.shape[0], cols)
        opts = RenderOptions(mode=self.state.mode, depth=self.state.depth)
        return render(rgb, cols, opts, rows=rows)

    def compose(self, body: str) -> str:
        """Wrap a rendered body with cursor positioning + status line."""
        status = (
            f"[termview] {self.state.mode}/{self.state.depth} "
            f"{self.effective_cols()}x{self.state.rows or 'auto'}"
            f" fps={self.state.fps:g}"
            f"{' PAUSED' if self.state.paused else ''}"
            f"{' MOUSE' if self.state.mouse_look else ''}"
            f"{' MOUSE+' if self.state.mouse_enabled else ''}"
            f"{' CMD(``+key)' if self.state.command_mode else ''}"
        )
        lines = body.split("\n")
        out = [HOME, body]
        # Move to the row after the image and print the status/help lines.
        out.append(f"\x1b[{len(lines) + 1};1H")
        out.append("\x1b[K" + status)
        if self.state.show_help:
            out.append("\n\x1b[K" + HELP)
        return "".join(out)

    def step(self) -> str:
        """Capture + render one frame. Returns the string to draw."""
        rgb = self.capture.grab()
        body = self.render_frame(rgb)
        self._last_frame = body
        return self.compose(body)

    # -- input -------------------------------------------------------------

    def handle_keys(self, keys: list[str]) -> str:
        """Apply logical keys. Returns ``"quit"``, ``"redraw"`` or ``"none"``.

        All keys are forwarded to the game unless we are in command mode (after
        the prefix key) or mouse-look mode remaps the arrows.
        """
        action = "none"
        for key in keys:
            if self.state.command_mode:
                self.state.command_mode = False
                act = self._run_command(key)
                if act == "quit":
                    return "quit"
                if act == "redraw":
                    action = "redraw"
                continue

            if key == self.state.prefix:
                self.state.command_mode = True
                action = "redraw"
                continue

            self._forward(key)
        return action

    def _run_command(self, key: str) -> str:
        if key in ("q", "ctrl-c", "ctrl-d"):
            return "quit"
        if key == "p":
            self.state.paused = not self.state.paused
        elif key == "m":
            i = MODES.index(self.state.mode)
            self.state.mode = MODES[(i + 1) % len(MODES)]
        elif key == "d":
            i = DEPTHS.index(self.state.depth)
            self.state.depth = DEPTHS[(i + 1) % len(DEPTHS)]
        elif key in ("+", "="):
            self.state.cols = min(self.state.cols + 10, 400)
        elif key in ("-", "_"):
            self.state.cols = max(self.state.cols - 10, 20)
        elif key == "r":
            pass
        elif key == "h":
            self.state.show_help = not self.state.show_help
        elif key == "i":
            self.state.mouse_look = not self.state.mouse_look
        elif key in MOUSE_COMMANDS:
            self._mouse_command(MOUSE_COMMANDS[key])
        else:
            # Unknown command: ignore rather than injecting the letter.
            return "redraw"
        return "redraw"

    def _mouse_command(self, action: str) -> None:
        if action == "click":
            self.injector.click(1)
        elif action == "right-click":
            self.injector.click(3)
        elif action == "scroll-up":
            self.injector.scroll(True)
        elif action == "scroll-down":
            self.injector.scroll(False)

    def _forward(self, key: str) -> None:
        if self.state.mouse_look:
            delta = {"up": (0, -20), "down": (0, 20), "left": (-20, 0), "right": (20, 0)}
            if key in delta:
                self.injector.move_mouse(*delta[key])
                return
            if key in ("enter", "space"):
                self.injector.click(1)
                return
        elif self.state.mouse_enabled and key == "enter":
            # Outside mouse-look, Enter clicks the element under the pointer so
            # buttons can be pressed without arming a mode first.
            self.injector.click(1)
            return
        self.injector.send_key(key)

    # -- main loop ---------------------------------------------------------

    def run(self, reader: KeyReader | None = None) -> int:
        reader = reader or KeyReader(fd=sys.stdin.fileno(), timeout=0.0)
        frame_interval = 1.0 / max(1e-3, self.state.fps)
        try:
            self._write(CLEAR + HIDE_CURSOR)
            with reader:
                while True:
                    start = time.monotonic()
                    keys = reader.read()
                    if keys:
                        action = self.handle_keys(keys)
                        if action == "quit":
                            break
                        if action == "redraw":
                            if not self._write(self.step()):
                                break
                            continue
                    if not self.state.paused:
                        if not self._write(self.step()):
                            break
                    elapsed = time.monotonic() - start
                    remaining = frame_interval - elapsed
                    if remaining > 0 and not keys:
                        # Small sleep so we do not spin the CPU when idle.
                        time.sleep(remaining)
        finally:
            # Best-effort: the terminal may already be gone.
            self._write(SHOW_CURSOR + "\n")
            self.capture.close()
        return 0

    def _write(self, text: str) -> bool:
        """Write to the terminal, returning False if it has gone away."""
        try:
            self.out.write(text)
            self.out.flush()
            return True
        except (OSError, ValueError):
            return False
