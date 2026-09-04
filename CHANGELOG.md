# Changelog

Notable changes, newest first. Dates are the day the work landed.

## 2026-09-04

### Added

- **Four more, twenty-nine in all.** `angles` (a ring of spokes and one that
  turns), `writing` (a message typing itself), `balls` (gravity and bouncing)
  and `sequence` (a shuffled permutation of bar heights).
- **`easings`, the twenty-fifth scene.** All fifteen easing curves plotted at
  once, each running a dot on a shared clock so the only differences are the
  curves. raylib-jlt splits this across three examples; a phone screen holds
  the lot, and seeing them together is the point.
- **`raylib.easings`**, the curve library, shared rather than part of the scene
  for the same reason the upstream examples share one header. Keeps raylib's
  `(t b c d)` signature deliberately.
- **Seven more scenes, twenty-four in all.** `life` and `automata`, then
  `colorwheel` and `unitcircle`, then `clock`, `piechart` and `logoanim`. All
  ports from raylib-jlt, and all of them needed resizing for a phone rather
  than transcribing.
- **libc for wall-clock time.** raylib's `GetTime` counts seconds since
  `InitWindow`, which is the wrong clock for a scene that shows the time, so
  the host binds `time()` and `localtime()`. Both resolve in libSystem, which
  is already in the process, so no extra linking. Verified against the Mac's
  own clock to the second.
- **rlgl's matrix stack and scissor rectangle**, which is how scenes came to
  respect the safe area without any of them changing.

### Fixed

- **The test runner's exit code did nothing, so the CI test job was never a
  gate.** It called `(resolve 'System/exit)` and fell through to `nil` when
  that returned nil, which it always does: Clojure's `resolve` looks up vars
  and a static method is not one. A failing test printed FAIL and exited 0.
  Proved by adding a deliberately failing test and reading the exit code, then
  fixed by calling `System/exit` directly, which works under both
  `clojure -M:test` and `jolt -M:test`. Both gates now stop the run: a failing
  test, and a test file on disk the runner does not list.
- **`bounce-in` was inconsistent at a zero duration.** It is defined by
  reflecting `bounce-out`, and the reflection inverts that degenerate case:
  every other curve reads `d=0` as finished and returns `b+c` where `bounce-in`
  returned `b`. A caller cannot know which curve it is holding, so it is
  special-cased.
- **Scenes drew under the Dynamic Island and the home indicator.** The insets
  had been read since the beginning and only `:top` was used, and only for the
  gallery's own cards. A scene is now handed metrics whose screen IS the safe
  region, and the host translates and clips it into place. No scene knows a
  safe area exists.
- **`unitcircle` crashed on its own first frame**, asking for element 0 of an
  empty trace. Every test in that file called `advance` first, so none had seen
  the state the scene actually starts in.
- **The automaton covered 39% of the display.** Cell width and row height were
  one number, and a cell wide enough to draw quickly is too wide to need many
  rows. They are chosen separately now.
- **Hairline strokes.** A one-pixel line reads fine in the 800-pixel window
  these examples were written for and disappears on a 1206-pixel screen.

### Measured

- **A filled rectangle is much cheaper than the guide assumed.** The automaton
  draws 2551 of them a frame at 58 fps, against the "roughly a thousand
  primitives" figure that came from lines and circles. Both new grid scenes had
  been tuned down twice against a ceiling they were never near.
- Every scene added here holds 57 to 59 fps on an iPhone 17 Pro.

### Changed

- The capture tooling moved to b12n-screen-grab's `contrib/ios-device` and
  `contrib/montage`, so the next iOS project does not write it a third time.

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
