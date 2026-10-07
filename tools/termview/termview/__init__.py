"""termview - render a Minecraft (or any) window as a 1:1 terminal display.

See ``python -m termview --help`` for usage.
"""

from .capture import BaseCapture, CaptureError, ImageCapture, MssCapture, X11Capture, make_capture
from .color import ANSI16, XTERM256, quantize, sgr
from .input import InputInjector, KeyReader, decode_key
from .renderer import RenderOptions, fit_grid, render
from .viewer import Viewer, ViewerState

__version__ = "0.1.0"

__all__ = [
    "BaseCapture",
    "CaptureError",
    "ImageCapture",
    "MssCapture",
    "X11Capture",
    "make_capture",
    "ANSI16",
    "XTERM256",
    "quantize",
    "sgr",
    "InputInjector",
    "KeyReader",
    "decode_key",
    "RenderOptions",
    "fit_grid",
    "render",
    "Viewer",
    "ViewerState",
    "__version__",
]
