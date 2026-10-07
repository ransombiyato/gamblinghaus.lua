"""Frame sources.

A *capture* is anything that can hand back the current screen as an
``(H, W, 3)`` uint8 RGB numpy array. Three implementations cover the useful
cases:

``ImageCapture``  a still file (PNG/JPG/...). Handy for tests and for
                  rendering a screenshot you already have.
``X11Capture``    grabs a live X11 window (or the root screen) through
                  python-xlib. This is what the interactive viewer uses; it
                  works under Xvfb with no GPU.
``MssCapture``    grabs a whole display through the ``mss`` library. A
                  fallback for odd X setups.

``X11Capture`` locates the target window with ``xdotool`` and reads pixels
straight from the X server, which is far more reliable than trying to capture
a redirected window directly.
"""

from __future__ import annotations

import subprocess
from pathlib import Path

import numpy as np
from PIL import Image


class CaptureError(RuntimeError):
    pass


class BaseCapture:
    def grab(self) -> np.ndarray:  # pragma: no cover - interface
        raise NotImplementedError

    def close(self) -> None:
        pass


class ImageCapture(BaseCapture):
    """A still image loaded once and returned on every grab."""

    def __init__(self, path: str | Path):
        self.path = Path(path)
        if not self.path.exists():
            raise CaptureError(f"image not found: {self.path}")
        with Image.open(self.path) as im:
            self._rgb = np.asarray(im.convert("RGB"))

    def grab(self) -> np.ndarray:
        return self._rgb


def _run(cmd: list[str]) -> str:
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise CaptureError(
            f"command failed ({' '.join(cmd)}): {proc.stderr.strip()}"
        )
    return proc.stdout


def find_window_id(name: str, display: str | None = None) -> str:
    """Return the X window id whose name matches ``name`` (substring)."""
    env = None
    if display:
        import os

        env = {**os.environ, "DISPLAY": display}
    proc = subprocess.run(
        ["xdotool", "search", "--name", name],
        capture_output=True,
        text=True,
        env=env,
    )
    ids = [line.strip() for line in proc.stdout.splitlines() if line.strip()]
    if not ids:
        raise CaptureError(f"no X window matching name {name!r}")
    # Prefer the last id (usually the top-most matching window).
    return ids[-1]


def window_geometry(win_id: str, display: str | None = None) -> tuple[int, int, int, int]:
    """Return ``(x, y, w, h)`` for an X window id."""
    env = None
    if display:
        import os

        env = {**os.environ, "DISPLAY": display}
    out = subprocess.run(
        ["xdotool", "getwindowgeometry", "--shell", win_id],
        capture_output=True,
        text=True,
        env=env,
    ).stdout
    vals: dict[str, int] = {}
    for line in out.splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            try:
                vals[k.strip()] = int(v.strip())
            except ValueError:
                pass
    if not {"WIDTH", "HEIGHT"} <= vals.keys():
        raise CaptureError(f"could not read geometry for window {win_id}")
    return vals.get("X", 0), vals.get("Y", 0), vals["WIDTH"], vals["HEIGHT"]


class X11Capture(BaseCapture):
    """Capture a live X11 window (or root) via python-xlib.

    Parameters
    ----------
    display:
        X display string, e.g. ``":99"``. ``None`` uses ``$DISPLAY``.
    window:
        Window name substring to locate with xdotool. If ``None`` the root
        window is captured.
    region:
        Optional explicit ``(x, y, w, h)`` override.
    """

    def __init__(
        self,
        display: str | None = None,
        window: str | None = None,
        region: tuple[int, int, int, int] | None = None,
    ):
        from Xlib import display as xdisplay

        self.display_name = display
        self.window_name = window
        self._region = region
        self._d = xdisplay.Display(display) if display else xdisplay.Display()
        self._root = self._d.screen().root
        geom = self._root.get_geometry()
        self._root_w, self._root_h = geom.width, geom.height
        self._win_id = None
        if window:
            self._win_id = find_window_id(window, display)

    def _resolve_region(self) -> tuple[int, int, int, int]:
        if self._region:
            x, y, w, h = self._region
        elif self._win_id:
            x, y, w, h = window_geometry(self._win_id, self.display_name)
        else:
            x, y, w, h = 0, 0, self._root_w, self._root_h
        # Clamp to the root window: get_image fails with BadMatch if any part
        # of the requested rectangle lies outside the screen (e.g. a window
        # larger than the display, or dragged partly offscreen).
        x0 = max(0, x)
        y0 = max(0, y)
        x1 = min(self._root_w, x + w)
        y1 = min(self._root_h, y + h)
        if x1 <= x0 or y1 <= y0:
            raise CaptureError(
                f"capture region {x},{y},{w},{h} is entirely outside the "
                f"{self._root_w}x{self._root_h} screen"
            )
        return x0, y0, x1 - x0, y1 - y0

    def grab(self) -> np.ndarray:
        x, y, w, h = self._resolve_region()
        if w <= 0 or h <= 0:
            raise CaptureError("capture region has zero size")
        raw = self._root.get_image(x, y, w, h, 2, 0xFFFFFFFF)  # X.ZPixmap
        data = raw.data
        if isinstance(data, str):  # pragma: no cover - older python-xlib
            data = data.encode("latin-1")
        # 24/32-bit TrueColor pixels arrive as BGRX (little endian).
        expected = w * h * 4
        if len(data) < expected:
            raise CaptureError(
                f"short pixel buffer: got {len(data)}, expected {expected}"
            )
        arr = np.frombuffer(data[:expected], dtype=np.uint8).reshape(h, w, 4)
        return arr[:, :, [2, 1, 0]].copy()  # BGRX -> RGB

    def close(self) -> None:
        try:
            self._d.close()
        except Exception:
            pass


class MssCapture(BaseCapture):
    """Whole-display capture through ``mss`` (fallback backend)."""

    def __init__(self, display: str | None = None, monitor: int = 1):
        import mss

        self._sct = mss.mss(display=display) if display else mss.mss()
        self._monitor = monitor

    def grab(self) -> np.ndarray:
        mon = self._sct.monitors[self._monitor]
        shot = self._sct.grab(mon)
        arr = np.frombuffer(shot.rgb, dtype=np.uint8).reshape(shot.height, shot.width, 3)
        return arr.copy()

    def close(self) -> None:
        try:
            self._sct.close()
        except Exception:
            pass


def make_capture(spec: str, window: str | None = None, display: str | None = None) -> BaseCapture:
    """Build a capture from a simple string spec.

    ``spec`` is either a path to an image file, ``"x11"`` for a window/root
    grab, or ``"mss"`` for a whole-display grab.
    """
    if spec == "x11":
        return X11Capture(display=display, window=window)
    if spec == "mss":
        return MssCapture(display=display)
    return ImageCapture(spec)
