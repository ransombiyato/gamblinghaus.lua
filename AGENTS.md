# AGENTS.md

Repository-specific notes for agents working in this repo.

## What this repo is

**MiNEDAR** — a client-only Minecraft LiDAR mod. It converts real world
geometry, entities, particles, fluids and special materials into a persistent,
material-aware point cloud with a live accumulated-dot heatmap minimap. Inspired
by the classic GMod LiDAR tool. The full spec lives outside the repo; the
defining rules are: LiDAR first, actual geometry, historical scan points,
material-aware transmission, low RAM, no ordinary world rendering while LiDAR is
active, completely client-side.

The repo was previously `gamblinghaus.lua` (a Python `termview` tool) and was
emptied for this work. The MiNEDAR code replaced it, but `termview` itself was
restored under `tools/termview/` — it is the playtesting harness, not the old
project, and must stay in the tree.

## Layout (multi-version Gradle build)

```
common/          pure-Java core, no Minecraft dependency, fully unit-tested
forge-1.20.1/    Forge adapter (primary target)
forge-1.21.1/    Forge adapter
forge-1.21.11/   Forge adapter (planned)
fabric-1.20.1/   Fabric adapter (compiles; builds as its own Gradle project, see below)
fabric-*/        other Fabric adapters (planned)
neoforge-*/      NeoForge adapters (planned; NeoForge has no 1.20.1)
tools/termview/  Python: the AI's screen-to-terminal playtest tool (see below)
```

`settings.gradle` only includes loader dirs that actually exist, so partial
checkouts configure cleanly. Fabric Loom is intentionally *absent* from the
root build: Loom and ForgeGradle cannot share one buildscript classloader
(ForgeGradle's Gson 2.9.1 makes Loom throw on Java 17), so every Fabric adapter
is a standalone Gradle project invoked with `./gradlew -p fabric-1.20.1 build`.

## Playtesting tool: `tools/termview`

`termview` renders a live X window (a headless Minecraft client under Xvfb) as
a 1:1 ANSI text/block display in the terminal and forwards keystrokes back with
XTEST, so the agent can actually *see* and *drive* the game instead of only
launching it. This is the required way to playtest the mod.

- Pure Python (`numpy`, `Pillow`, `python-xlib`, `mss`); self-contained under
  `tools/termview/`. Run its suite with `cd tools/termview && pytest` (97 tests;
  needs `xvfb xdotool x11-utils xterm` for the X integration tests).
- CLI: `python -m termview live --window Minecraft --cols 140 --fps 20`
  (`grab` for one frame, `image` for a still). Every key goes to the game;
  `` ` `` plus a command letter drives the tool. For mouse: `` ` `` `c` click,
  `` ` `` `v` right-click, `` ` `` `u` / `` ` `` `j` scroll, `` ` `` `i`
  mouse-look (arrows move the pointer). Add `--mouse` so a bare Enter clicks
  under the pointer. Keys and clicks go in live via XTEST, so no preparation
  is needed to press buttons.
- CI: `.github/workflows/termview.yml`. It was briefly deleted when the repo was
  repurposed for MiNEDAR; keep it — it is not disposable.

### Driving a live client under Xvfb (this container)

- The client runs headless under `Xvfb :99 -screen 0 1280x720x24`. Launch it with
  the software-GL env or the render thread dies on startup:
  `JAVA_HOME=/workspace/toolchain/jdk-17.0.13+11`, `DISPLAY=:99`,
  `LIBGL_ALWAYS_SOFTWARE=1`, `MESA_GL_VERSION_OVERRIDE=3.2`,
  `MESA_GLSL_VERSION_OVERRIDE=150`, then `./gradlew :forge-1.20.1:runClient
  --no-daemon` (log to a file; the gradle wrapper may need `--offline`).
- Keys and clicks go in via XTEST, so `xdotool key --clearmodifiers g` (plain,
  **without** `--window`) and a real `mousemove` reach the game; `--window` uses
  XSendEvent and the GLFW client ignores it. A click only registers when the
  pointer *moves onto* the target while the window has focus — park the pointer
  at an inert spot first, then move onto the button, then click.
- Verify state by pixels, not by the awkward `termview` interactive menu: diff
  frames with Pillow/numpy, and read `run/logs/latest.log`, `run/options.txt`
  (keybind registration) and `run/config/minedar.json`. `jstack` the render
  thread to tell "hung in our code" from "slow under software GL".
- On this CPU-only host, drawing the full accumulated cloud is slow: keep
  LiDAR test setups near a small scanned region, out of the sky.

## Key conventions

- **All LiDAR logic belongs in `common` (package `dev.minedar.core`)**, which has
  no Minecraft imports and is covered by JUnit tests. Loader modules are thin
  adapters only: events, rendering, real block/entity sampling, disk IO.
- Each loader jar **compiles the core sources in directly**
  (`sourceSets.main.java.srcDir rootProject.file('common/src/main/java')`) so the
  built mod jar is self-contained. Do not reintroduce `project(':common')` +
  `jarJar` dependencies; ForgeGradle 6's jarJar DSL makes that fragile.
- Versions: Forge 1.20.1-47.4.26, 1.21.1-52.1.16, 1.21.11-61.2.1; NeoForge
  1.21.1 (~21.1.256), 1.21.11 (~21.11.45). Toolchains: Java 17 for 1.20.1/1.21.1,
  Java 21 for 1.21.11.
- **No audio assets are ever generated** (spec section 52). Use `SoundEventHooks`.
- Point appearance, scan speed and minimap zoom are intentionally NOT
  configurable.
- **Client key-mapping registration goes on the MOD event bus.** Forge fires
  `RegisterKeyMappingsEvent` only there; `@EventBusSubscriber` auto-subscribes to
  the game bus, so a handler for it silently never runs and *no* keybinds appear
  in `options.txt`. `MinedarKeybinds.register()` (called from the mod
  constructor) wires that one listener explicitly; tick handling stays on the
  game bus.
- Default keys: LiDAR toggle is **`G`** (not `L` — vanilla binds `L` to
  Advancements, and a shared key fires both mappings), minimap toggle `M`,
  fullscreen map UNBOUND (spec). LMB scans continuously, RMB bursts.
- **Entity geometry (section 40)** is captured for real, not as hitboxes.
  `dev.minedar.core.EntityGeometry` (core) converts oriented model boxes into
  dots; `EntityModelCapture` (forge) poses the live `ModelPart` tree and
  projects each `ModelPart.Cube` to world space via `ModelPart.visit`. The only
  non-public API touched is one reflectively-read field on
  `PoseStack.Pose` (`pose`); if that ever changes, capture degrades to
  `entityBoxesAlong` bounding volumes instead of failing. Keep it that way —
  no hard dependency on obfuscated/internal names.

## Build / test

- Gradle wrapper is pinned to **8.8** (ForgeGradle 6 needs it; do not bump to 9).
- `./gradlew :common:test` runs the core suite (fast, no Minecraft download).
- `./gradlew :forge-1.20.1:build` produces the mod jar.
- The foojay toolchain resolver in `settings.gradle` auto-provisions JDK 17/21.
- **All builds run via GitHub Actions** (`.github/workflows/minedar.yml`), per
  user policy, so runs are visible on GitHub.

## Environment (this container)

- No GPU. Network to Maven Central / GitHub / Forge maven works.
- JDK 21 is preinstalled; JDK 17 is not in apt, the toolchain resolver or a
  manual Temurin install provides it.
- `unzip` and `bc` are not installed; use `jar`/`python` for archives.

## Git / credentials

- `GITHUB_TOKEN` is a read-only GitHub App token — pushing with it 403s. Use
  `GITHUB_PERSONAL_ACCESS_TOKEN` (has `repo` + `workflow` scope) for pushes.
- Remote: `https://<PAT>@github.com/ransombiyato/gamblinghaus.lua.git`.
- Git identity: `openhands` / `openhands@all-hands.dev`.
