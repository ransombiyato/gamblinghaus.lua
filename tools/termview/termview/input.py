"""Terminal input -> X11 input.

Two halves:

* :class:`RawKeyReader` puts stdin into cbreak mode and yields logical key
  names (``"up"``, ``"enter"``, ``"a"`` ...) from raw bytes, decoding the
  usual escape sequences for arrows / function keys.
* :class:`InputInjector` forwards those logical keys to a live X window with
  ``xdotool``, and exposes click/move helpers for mouse look.

Keeping the two apart means the key decoding is trivially unit-testable
without an X server, and the injection layer is a thin, auditable shell-out.
"""

from __future__ import annotations

import os
import select
import subprocess
import sys
from dataclasses import dataclass, field

# Terminal escape sequences -> logical key names.
ESCAPE_SEQUENCES = {
    "\x1b[A": "up",
    "\x1b[B": "down",
    "\x1b[C": "right",
    "\x1b[D": "left",
    "\x1bOA": "up",
    "\x1bOB": "down",
    "\x1bOC": "right",
    "\x1bOD": "left",
    "\x1b[1~": "home",
    "\x1b[4~": "end",
    "\x1b[5~": "pageup",
    "\x1b[6~": "pagedown",
    "\x1b[2~": "insert",
    "\x1b[3~": "delete",
    "\x1b[H": "home",
    "\x1b[F": "end",
    "\x1bOP": "f1",
    "\x1bOQ": "f2",
    "\x1bOR": "f3",
    "\x1bOS": "f4",
}

# Single control bytes -> logical key names.
CONTROL_KEYS = {
    "\x03": "ctrl-c",
    "\x04": "ctrl-d",
    "\x08": "backspace",
    "\x7f": "backspace",
    "\t": "tab",
    "\r": "enter",
    "\n": "enter",
    "\x1b": "escape",
    " ": "space",
}

# Logical key -> xdotool key name.
XDOTOOL_KEYS = {
    "up": "Up",
    "down": "Down",
    "left": "Left",
    "right": "Right",
    "enter": "Return",
    "escape": "Escape",
    "space": "space",
    "tab": "Tab",
    "backspace": "BackSpace",
    "delete": "Delete",
    "home": "Home",
    "end": "End",
    "pageup": "Prior",
    "pagedown": "Next",
    "insert": "Insert",
}
for _i in range(1, 13):
    XDOTOOL_KEYS[f"f{_i}"] = f"F{_i}"
for _c in "abcdefghijklmnopqrstuvwxyz":
    XDOTOOL_KEYS[_c] = _c
for _d in "0123456789":
    XDOTOOL_KEYS[_d] = _d


def decode_key(data: str) -> list[str]:
    """Decode a raw byte string into zero or more logical key names.

    ``data`` may contain several keys (fast typists, pasted text, or an escape
    sequence split across reads). Unknown characters fall through as their own
    literal value so callers can still forward them.
    """
    keys: list[str] = []
    i = 0
    while i < len(data):
        # Try the longest escape sequence match first.
        matched = False
        if data[i] == "\x1b":
            for seq in sorted(ESCAPE_SEQUENCES, key=len, reverse=True):
                if data.startswith(seq, i):
                    keys.append(ESCAPE_SEQUENCES[seq])
                    i += len(seq)
                    matched = True
                    break
            if matched:
                continue
            # Lone ESC (arrow keys may arrive split; treat as escape for now).
            keys.append("escape")
            i += 1
            continue
        ch = data[i]
        if ch in CONTROL_KEYS:
            keys.append(CONTROL_KEYS[ch])
        else:
            keys.append(ch)
        i += 1
    return keys


@dataclass
class KeyReader:
    """Read logical keys from a file descriptor in cbreak mode."""

    fd: int = 0
    timeout: float = 0.0
    _saved: list | None = field(default=None, repr=False)

    def __enter__(self) -> "KeyReader":
        self._enable()
        return self

    def __exit__(self, *exc) -> None:
        self._disable()

    def _enable(self) -> None:
        try:
            import termios
            import tty

            self._saved = termios.tcgetattr(self.fd)
            tty.setcbreak(self.fd)
        except Exception:
            # Not a tty (e.g. piped input in tests): stay in cooked mode.
            self._saved = None

    def _disable(self) -> None:
        if self._saved is not None:
            try:
                import termios

                termios.tcsetattr(self.fd, termios.TCSADRAIN, self._saved)
            except Exception:
                pass
            self._saved = None

    def read(self) -> list[str]:
        """Return keys available within ``timeout`` seconds (possibly empty)."""
        try:
            r, _, _ = select.select([self.fd], [], [], self.timeout)
        except (OSError, ValueError):
            return []
        if not r:
            return []
        try:
            data = os.read(self.fd, 64).decode("utf-8", errors="replace")
        except OSError:
            return []
        if not data:
            return []
        return decode_key(data)


class InputInjector:
    """Forward logical keys and mouse actions to an X window via xdotool.

    Keys are delivered with XTEST (the same mechanism as real hardware), after
    focusing the target window. ``xdotool key --window`` uses synthetic
    ``XSendEvent`` events which many apps (xterm, most games) deliberately
    ignore, so we avoid it.
    """

    def __init__(self, window: str | None = None, display: str | None = None):
        self.window = window
        self.display = display

    def _env(self) -> dict:
        env = dict(os.environ)
        if self.display:
            env["DISPLAY"] = self.display
        return env

    def _win_id(self) -> str:
        from .capture import find_window_id

        return find_window_id(self.window, self.display)

    def focus_window(self) -> bool:
        """Give the target window input focus. Returns True on success."""
        if not self.window:
            return True
        try:
            win_id = self._win_id()
        except Exception:
            return False
        ok = False
        # Raise + activate first (some WMs need this before focus sticks), then
        # set input focus explicitly.
        for args in (
            ["xdotool", "windowactivate", "--sync", win_id],
            ["xdotool", "windowfocus", "--sync", win_id],
        ):
            proc = subprocess.run(
                args, env=self._env(), capture_output=True, timeout=2
            )
            ok = ok or proc.returncode == 0
        return ok

    def send_key(self, key: str, repeat: int = 1) -> bool:
        """Send a logical key via XTEST. Returns False if it has no mapping."""
        name = XDOTOOL_KEYS.get(key, key if len(key) == 1 else None)
        if not name:
            return False
        if self.window:
            self.focus_window()
        for _ in range(max(1, repeat)):
            subprocess.run(
                ["xdotool", "key", "--clearmodifiers", name],
                env=self._env(),
                capture_output=True,
            )
        return True

    def move_mouse(self, dx: int, dy: int) -> None:
        subprocess.run(
            ["xdotool", "mousemove_relative", "--", str(dx), str(dy)],
            env=self._env(),
            capture_output=True,
        )

    def click(self, button: int = 1) -> None:
        subprocess.run(
            ["xdotool", "click", str(button)],
            env=self._env(),
            capture_output=True,
        )

    def scroll(self, up: bool = True, amount: int = 1) -> None:
        """Scroll the wheel via XTEST (buttons 4=up, 5=down)."""
        button = "4" if up else "5"
        for _ in range(max(1, amount)):
            subprocess.run(
                ["xdotool", "click", button],
                env=self._env(),
                capture_output=True,
            )

    def move_to(self, x: int, y: int) -> None:
        subprocess.run(
            ["xdotool", "mousemove", str(x), str(y)],
            env=self._env(),
            capture_output=True,
        )
