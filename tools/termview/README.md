# termview

Render a window (for example a **client-side Minecraft** window) as a 1:1
terminal display using text and block characters, and forward your keystrokes
back into the game.

The renderer maps each terminal cell to one or two pixels, so a 100-column
terminal shows roughly a 100x80 "pixel" image. Keys you type are sent to the
target window with XTEST, so the game receives real input (movement, jump,
inventory, chat, ...).

## How it works

```
 X window ──capture──▶ numpy RGB ──render──▶ ANSI text ──▶ your terminal
     ▲                                                          │
     └──────────────── xdotool (XTEST) ◀── keys ◀───────────────┘
```

* **capture** – grabs the target window (or the whole screen) via X11 or `mss`.
* **render** – downsamples to the terminal grid and emits truecolor / 256 /
  16-colour ANSI using half-block, full-block or ASCII glyphs.
* **input** – every key is forwarded to the game. Local commands sit behind a
  prefix (`` ` ``) so they can never steal a movement key.

## Install

```bash
pip install -r requirements.txt
# X helpers (Debian/Ubuntu):
sudo apt-get install -y xvfb xdotool x11-utils xterm
```

## Usage

```bash
# Render a screenshot once.
python -m termview image screenshot.png --cols 120

# Capture a live window (e.g. under Xvfb) and print one frame.
python -m termview grab --window Minecraft --display :99 --depth 256

# Interactive live view: watch the window and control the game.
python -m termview live --window Minecraft --cols 140 --fps 20
```

Inside `live`, **all keys go to the game**. Tap `` ` `` first for local
commands:

| keys        | action                          |
|-------------|---------------------------------|
| `` ` `` `q` | quit                            |
| `` ` `` `h` | toggle help                     |
| `` ` `` `m` | cycle render mode (half/full/ascii) |
| `` ` `` `d` | cycle colour depth (truecolor/256/16) |
| `` ` `` `p` | pause / resume the display      |
| `` ` `` `i` | mouse-look mode (arrows move the mouse, enter/space click) |
| `` ` `` `c` | left-click (break / press) under the pointer |
| `` ` `` `v` | right-click (place / use) under the pointer |
| `` ` `` `u` / `` ` `` `j` | scroll wheel up / down |
| `` ` `` `r` | force a redraw                  |
| `` ` `` `+` / `` ` `` `-` | zoom (columns)       |

Mouse clicks land wherever the pointer currently is. Use mouse-look (`` ` `` `i`)
to move the pointer, or `` ` `` `c` / `` ` `` `v` / `` ` `` `u` / `` ` `` `j`
to click and scroll directly. Start with `--mouse` to make a bare Enter click
without arming a mode first. This means buttons and inventory slots can be
pressed live, with no preparation.

## Render modes

* `half` – two pixels per cell using the upper-half block (`▀`). Square pixels,
  best fidelity.
* `full` – one pixel per cell using a full block. Coarser but smaller output.
* `ascii` – luminance mapped to ASCII, readable in any terminal / log.

## Seeing what it renders

Turn rendered output back into a PNG (useful for eyeballing fidelity):

```bash
python -m termview image shot.png --cols 80 > frame.txt
python scripts/ansi_to_png.py frame.txt frame.png --cell 6
```

## Development

```bash
pip install -e '.[dev]'
pytest
```

The test suite covers colour quantisation, the renderer, input decoding,
capture, the interactive loop, ANSI round-trip fidelity, and end-to-end runs
under Xvfb (capturing a real window and injecting keys into a real X client).

## Requirements

Python 3.10+, `numpy`, `Pillow`, `python-xlib`, `mss`. On Linux the live
viewer also needs an X server and `xdotool`.
