# AGENTS.md

Repository-specific notes for agents working in this repo.

## What this repo is

`termview` — a Python tool that renders a window (target use case: a
**client-side Minecraft** window) as a 1:1 terminal display and forwards
keystrokes back into the game. The repo was originally `gamblinghaus.lua` and
was emptied; the name will be repurposed once the full mod context is provided.

## Environment (this container)

- No GPU (`/dev/dri` absent), Docker daemon unavailable.
- Java/JDK not installed (apt-installable, OpenJDK 21) if Minecraft needs to run.
- Network to Maven Central / GitHub works.
- X stack: `Xvfb`, `xdotool`, `xterm`, `x11-utils` (incl. `xev`) installed.
  Python: `numpy`, `Pillow`, `python-xlib`, `mss`, `pytest`.
- **`xterm` ignores XTEST key events in this container** — use `xev` as the
  verifiable injection target in tests instead.

## Conventions

- Run tests with `pytest` from the repo root (config in `pyproject.toml`).
- X-dependent tests are guarded with `pytest.mark.skipif(shutil.which(...))`.
- Keys: every key is forwarded to the game by default; local tool commands are
  behind the `` ` `` prefix key so they never collide with WASD.

## Git / credentials

- `GITHUB_TOKEN` is a **read-only GitHub App token** — pushing with it 403s.
  Use `GITHUB_PERSONAL_ACCESS_TOKEN` (has `repo` + `workflow` scope) for pushes.
- Remote: `https://<PAT>@github.com/ransombiyato/gamblinghaus.lua.git`.
- Git identity: `openhands` / `openhands@all-hands.dev`.
- Per user policy: commit and push everything; run builds via GitHub Actions.

## Testing notes

- `tests/test_ansi.py` measures render fidelity by parsing ANSI back to pixels
  (PSNR). Half-block truecolor is near-lossless for flat colours.
- Capture tests do **not** rely on a terminal emulator: `tests/_xwindow.py`
  maps a plain coloured window via python-xlib and the `coloured_window()`
  context manager in `conftest.py` yields once it is mapped. Use it instead of
  `xterm` (xterm does not start on the CI runner).
- Key-injection tests target `xev` and assert on the keysyms it prints; xterm
  ignores synthetic (XTEST) events in headless containers.
- Always poll for a window with `wait_for_window()` rather than sleeping a fixed
  time - window mapping on CI is slow.
- Capture clamps the region to the root window (windows larger than the screen
  would otherwise raise Xlib `BadMatch`).
