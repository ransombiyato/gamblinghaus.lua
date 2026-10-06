"""Convert termview ANSI output into a PNG so it can be seen as an image.

Usage::

    python -m termview image shot.png --cols 80 > rendered.txt
    python scripts/ansi_to_png.py rendered.txt rendered.png --cell 8
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from termview.ansi import ansi_to_image


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("input", help="file containing rendered ANSI text")
    ap.add_argument("output", help="PNG path to write")
    ap.add_argument("--cell", type=int, default=8, help="pixels per cell width")
    args = ap.parse_args()
    text = Path(args.input).read_text(encoding="utf-8")
    img = ansi_to_image(text, cell=args.cell)
    img.save(args.output)
    print(f"wrote {args.output} ({img.width}x{img.height})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
