"""Create a plain coloured X window and keep it mapped.

Used by the capture tests so they have a real, painted window to grab without
depending on a terminal emulator. Usage::

    python tests/_xwindow.py <display> <name> <w> <h> <r> <g> <b>

Colour channels are 16-bit (0-65535). Runs until killed.
"""

from __future__ import annotations

import sys
import time

from Xlib import X, display


def main() -> int:
    disp_name, name, w, h, r, g, b = sys.argv[1:8]
    d = display.Display(disp_name)
    screen = d.screen()
    pixel = screen.default_colormap.alloc_color(int(r), int(g), int(b)).pixel
    win = screen.root.create_window(
        0, 0, int(w), int(h), 0, screen.root_depth,
        X.InputOutput, X.CopyFromParent,
        background_pixel=pixel,
        event_mask=X.ExposureMask,
    )
    win.set_wm_name(name)
    win.map()
    d.sync()
    time.sleep(300)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
