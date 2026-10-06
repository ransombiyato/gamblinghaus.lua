"""Command line entry point for termview.

Sub-commands
------------
``image PATH``   render a still image once and exit.
``grab``         capture the screen/window once and render it, then exit.
``live``         interactive loop: live capture + keyboard control.

Examples
--------
Render a screenshot::

    python -m termview image screenshot.png --cols 120

Watch a live X window (under Xvfb or a real display)::

    python -m termview live --window Minecraft --cols 140

One-shot grab of the whole display, 256 colours::

    python -m termview grab --depth 256
"""

from __future__ import annotations

import argparse
import sys

import numpy as np

from .capture import make_capture
from .input import InputInjector, KeyReader
from .renderer import RenderOptions, render
from .viewer import Viewer, ViewerState


def _add_common(p: argparse.ArgumentParser) -> None:
    p.add_argument("--mode", choices=["half", "full", "ascii"], default="half")
    p.add_argument("--depth", choices=["truecolor", "256", "16"], default="truecolor")
    p.add_argument("--cols", type=int, default=100, help="width in terminal columns")
    p.add_argument("--rows", type=int, default=None, help="height in terminal rows (default: aspect-fit)")
    p.add_argument("--resample", choices=["box", "nearest", "bilinear"], default="box")
    p.add_argument("--invert", action="store_true", help="invert ascii luminance")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="termview", description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    img = sub.add_parser("image", help="render a still image once")
    img.add_argument("path")
    _add_common(img)

    grab = sub.add_parser("grab", help="capture screen/window once and render")
    grab.add_argument("--window", default=None, help="X window name substring")
    grab.add_argument("--display", default=None, help="X display, e.g. :99")
    grab.add_argument("--source", choices=["x11", "mss"], default="x11")
    _add_common(grab)

    live = sub.add_parser("live", help="interactive live viewer")
    live.add_argument("--window", default=None, help="X window name substring to forward input to")
    live.add_argument("--display", default=None, help="X display, e.g. :99")
    live.add_argument("--fps", type=float, default=20.0)
    _add_common(live)

    return parser


def _opts(args) -> RenderOptions:
    return RenderOptions(mode=args.mode, depth=args.depth, resample=args.resample, invert=args.invert)


def _render_once(rgb: np.ndarray, args) -> str:
    cols = args.cols
    return render(rgb, cols, _opts(args), rows=args.rows)


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)

    if args.command == "image":
        cap = make_capture(args.path)
        sys.stdout.write(_render_once(cap.grab(), args) + "\n")
        return 0

    if args.command == "grab":
        cap = make_capture(args.source, window=args.window, display=args.display)
        sys.stdout.write(_render_once(cap.grab(), args) + "\n")
        cap.close()
        return 0

    if args.command == "live":
        cap = make_capture("x11", window=args.window, display=args.display)
        state = ViewerState(
            mode=args.mode,
            depth=args.depth,
            cols=args.cols,
            rows=args.rows,
            fps=args.fps,
        )
        injector = InputInjector(window=args.window, display=args.display)
        viewer = Viewer(cap, state=state, injector=injector)
        return viewer.run(KeyReader(fd=sys.stdin.fileno()))

    return 2


if __name__ == "__main__":
    raise SystemExit(main())
