# Changelog

Notable changes, newest first. Dates are the day the work landed.

## 2026-09-03 (later)

### Added

- **Two scenes, seventeen in total.** `lorenz`, the Lorenz attractor with a
  hand-rolled orbiting camera since this host binds none of raylib's 3D, and
  `tesseract`, a 4D hypercube projected 4D to 3D to 2D.
- **`raylib.scenes.lorenz/trail-length` is an atom**, not a `def`, so the
  performance ceiling can be swept from a REPL without a rebuild.

### Fixed

- **The test runner silently skipped new test files.** Its namespace list was
  hardcoded, so two new `*_test.cljc` files never ran and `Ran 23 tests` read
  as a pass. The runner now compares the list against what is on disk and fails
  if a test file is not listed.
- **Lorenz allocated per segment**, a fresh vector from both `project` and
  `trail-colour`, about 2400 a frame. 18 fps before, 31 after, 58 once the
  trail was sized from the sweep.
- **The trail could open on half a butterfly.** A 450-point window is short
  enough to sit inside one lobe: 499 frames of 600 straddle both. `warm` now
  runs on until the window spans the divide.

### Measured

- Lorenz's ceiling: flat at vsync to a 480-point trail, slipping at 500,
  falling away past 600. Default set to 450 at 58 fps.
- Euler at `dt` 0.006 tracks a near-exact reference; 0.009 and 0.012 inflate
  the attractor, so a bigger step does not buy more trajectory per point.

## 2026-09-03

The whole project, in one day. raylib and SDL2 rendering on an iPhone from
Clojure, fifteen scenes, and the measurements that shaped them.

### Added

- **zlib licence**, in `LICENSE`, matching raylib-jlt, raylib and SDL2. See
  `NOTICE` for third-party provenance and for the one blocker that has to clear
  before this repository can be published or transferred.
- **raylib 6.0 and SDL2 on iOS.** raylib ships no iOS platform layer and needs
  none: built `PLATFORM=SDL` with `GRAPHICS_API_OPENGL_ES2` against an SDL2
  compiled for iOS, SDL's own iOS support becomes the platform layer. Both go
  into the executable as static archives, because `jolt build --target` emits
  the whole binary and Chez owns `main`.
- **`raylib.host`**, the owner loop. Chez's `main` calls `SDL_UIKitRunApp`
  through the FFI, SDL runs `UIApplicationMain`, and its delegate calls back on
  thread 0, where the raylib loop lives for the life of the app.
- **Fifteen scenes** in four categories. Six are pure `.cljc` from
  jasalt/jolt-android-experiment, carried byte-identical with their sha256
  verified. Ten are ports from jlt-commons/raylib-jlt.
- **Two-level navigation.** `poc.raylib.gallery-ui` fits every card on one
  screen by dividing the height by the row count, which is unreadable past a
  handful. Categories sit above the pure contract instead, reusing
  `gallery-layout` with a different id list, so that file stays byte-identical.
- **A live seam.** `raylib.host/state` reads what the running scene holds,
  `on-next-frame!` queues work onto the main thread, and
  `raylib.gallery/tap!` injects a synthetic tap, so an editor can drive the app
  without a finger.
- **cider-nrepl**, opt-in behind `-A:cider`. The default build has no
  dependencies at all and that is worth keeping.
- **Capture tooling**, since moved out to a separate capture project so the
  next iOS app can reuse it. Every image in `docs/images` was taken off a real
  device by it, unattended, and Flappy Bird plays itself for the camera,
  flapped by a loop running inside the app.
- **Two guides.** `performance-on-a-phone.md` and `porting-an-example.md`.

### Fixed

- **`GetFPS` was being misread, not misbehaving.** It is a per-frame sampler:
  each call advances a 30-slot ring by one, so calling it from a 300-frame
  summary returns `1/(n * frame-time/30)` and decays toward the truth. The host
  computes its own rate from frame times now. Documented upstream in
  raysan5/raylib#6120.
- **Draw loops rewritten as indexed loops.** `partition` and `map-indexed` per
  frame cost three to four times what the FFI calls they fed did. spirograph
  went from 14 fps to 52 at the same point count, drawing the same lines.
- **A `safe-area-top` that could never work.** `SDL_GetDisplayUsableBounds`
  returns `uiscreen.bounds` on iOS and knows nothing of safe areas, so it
  always answered 0 against a real 62 pt inset. Removed; scenes ask UIKit.

### Upstream

- **jolt-lang/jolt#829, merged.** `sa-os-family` called a native iOS build
  Linux, so `tarm64ios` took Linux's `SIGCHLD`, `EAGAIN`, `O_NONBLOCK` and
  `struct stat` offsets on a Darwin system. The maintainer extended the fix to
  `build.ss`'s own `bld-tgt-osx?`.
- **raysan5/raylib#6120, open.** Documents that `GetFPS` must be called every
  frame.

### Not filed

- **raylib's SDL platform binding no framebuffer.** The code reading is correct
  and the conclusion drawn from it is not: measured on a device, both the
  drawable framebuffer and the colour renderbuffer are already bound at swap
  time whether or not this host binds anything. The report was written and
  withdrawn before filing. See `docs/guide/performance-on-a-phone.md`.
