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
emptied for this work; history is retained but that code is gone.

## Layout (multi-version Gradle build)

```
common/          pure-Java core, no Minecraft dependency, fully unit-tested
forge-1.20.1/    Forge adapter (primary target)
forge-1.21.1/    Forge adapter (planned)
forge-1.21.11/   Forge adapter (planned)
fabric-*/        Fabric adapters (planned)
neoforge-*/      NeoForge adapters (planned; NeoForge has no 1.20.1)
```

`settings.gradle` only includes loader dirs that actually exist, so partial
checkouts configure cleanly.

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
