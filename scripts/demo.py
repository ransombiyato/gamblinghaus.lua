"""End-to-end demo: boot Xvfb, show a colourful window, render it via termview.

Run: python scripts/demo.py [--cols 80] [--rows 20] [--mode half]
Prints the rendered frame to stdout so you can eyeball it in a terminal.
"""
import argparse
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from termview.capture import X11Capture
from termview.renderer import RenderOptions, render

DEMO_CMD = (
    "printf '\\n  MINECRAFT SERVER\\n  =================\\n"
    "  player: Steve\\n  health: ############----\\n  pos: 128 64 -204\\n"
    "  blocks: 42\\n\\n  > _'; sleep 300"
)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--cols", type=int, default=80)
    ap.add_argument("--rows", type=int, default=20)
    ap.add_argument("--mode", default="half", choices=["half", "full", "ascii"])
    ap.add_argument("--depth", default="truecolor", choices=["truecolor", "256", "16"])
    args = ap.parse_args()

    display = ":93"
    env = {**os.environ, "DISPLAY": display}
    xvfb = subprocess.Popen(
        ["Xvfb", display, "-screen", "0", "640x480x24"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    xterm = None
    try:
        for _ in range(50):
            if subprocess.run(["xdpyinfo"], env=env, capture_output=True).returncode == 0:
                break
            time.sleep(0.1)
        xterm = subprocess.Popen(
            [
                "xterm", "-T", "termview-demo",
                "-bg", "#101820", "-fg", "#39d353",
                "-geometry", "50x16", "-fa", "Monospace", "-fs", "14",
                "-e", "sh", "-c", DEMO_CMD,
            ],
            env=env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        time.sleep(1.0)
        cap = X11Capture(display=display, window="termview-demo")
        frame = cap.grab()
        cap.close()
        opts = RenderOptions(mode=args.mode, depth=args.depth)
        print(render(frame, args.cols, opts, rows=args.rows))
    finally:
        if xterm is not None:
            xterm.terminate()
        xvfb.terminate()
        xvfb.wait(timeout=5)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
