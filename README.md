# raylib-ios

raylib and SDL2 on an iPhone, driven from Clojure by
[jolt](https://github.com/jolt-lang/jolt), on threaded portable bytecode with
no JIT and nothing generated at run time.

What runs today: a gallery of three scenes (Following Eyes, Touch Trail and
Flappy Bird), each one a pure `.cljc` simulation taken byte for byte from
[jasalt/jolt-android-experiment](https://github.com/jasalt/jolt-android-experiment)
at `6d2b291`, under an iOS owner loop of about thirty lines. Tap a card to
open a scene, tap Back to leave it. The bird flaps on a press edge.

Private for now.

## Where the code came from

Larry Staton's [glimmer-ios-demo](https://github.com/statonjr/glimmer-ios-demo)
proved this recipe first, as two literate org notebooks: `examples/flappy`
(milestones 0 to 5, the toolchain and the loop) and `examples/gallery` (the
scene contract on top of it). Both are worth reading, because they keep the
mistakes as well as the answers.

This project is the same code as an ordinary source tree, so a build reads
files rather than tangling them out of prose first. `tools/extract-from-notebooks`
did the one-time extraction and is kept for provenance: it refuses to
overwrite a file that has since been edited, and it checks the six pure
namespaces against the sha256 the notebooks record.

```
$ ./tools/extract-from-notebooks
byte-identity of the pure namespaces (jasalt/jolt-android-experiment @ 6d2b291):
  src/poc/raylib/diagnostics.cljc        ok  179b24736879fdf1
  src/poc/raylib/flappy_bird.cljc        ok  4d9cf3ae1984613d
  src/poc/raylib/following_eyes.cljc     ok  9dcd98e36aafcb78
  src/poc/raylib/gallery.cljc            ok  6bfc1f12cb425b9b
  src/poc/raylib/gallery_ui.cljc         ok  a2301b268d555504
  src/poc/raylib/touch_trail.cljc        ok  242385a4855c083a
```

## raylib has no iOS backend, and does not need one

`src/platforms/` in raylib 6.0, the version this project pins, holds
`rcore_android.c`, `rcore_desktop_{glfw,rgfw,sdl,win32}.c`, `rcore_drm.c`,
`rcore_memory.c`, `rcore_web.c`, `rcore_web_emscripten.c` and
`rcore_template.c`. There is no `rcore_ios.c`, and the flappy notebook reports
that raylib issue #330, "Try to support iOS platform", was closed in 2018
without one. Writing that file from scratch means a UIWindow, a GLES layer,
touch handling and the loop: several hundred lines of Objective-C.

The shortcut is SDL. Build raylib with `PLATFORM=SDL` and
`GRAPHICS_API_OPENGL_ES2` against an SDL2 built for iOS, and SDL's own iOS
support becomes the platform layer. That collapses the whole problem to
"build SDL", which is well trodden.

Both libraries go into the executable as static archives. `jolt build
--target` emits the whole binary and Chez owns `main`, so no host process
exists to `dlopen` a shared library, and every `defcfn` resolves against the
process image at first call. This is why the project declares no dependencies
at all: there is no `:jolt/native` entry naming `libraylib.dylib` to satisfy,
because there is no library to load.

## Prerequisites

- **jolt 0.8.0 or newer.** `deps.edn` sets the floor with `:jolt/min-version`,
  and jolt refuses to build below it. `ffi/write` swapped its value and offset
  arguments at 0.8.0, and because both are integers an older jolt cannot tell
  the two spellings apart: it would write the wrong byte to the wrong place in
  silence. Every struct here goes through `ffi/layout` and `write-field`,
  which read the same on either side, so the floor is belt and braces.
- **Chez Scheme 10.4.1 on `PATH` as `chez`.** The pin is load bearing. A
  target pack's xpatch is compiled Scheme that loads only into the exact Chez
  that produced it.
- **Xcode**, for the iOS SDK and `devicectl`.
- **cmake**, to cross-build the two archives.
- **A paired iPhone and an Apple Development identity.** Signing uses whatever
  team wildcard profile already covers your account; `tools/ios/deploy.sh`
  finds it, validates it and embeds it.

No private repositories are needed. `tools/ios/pack.sh` points at the public
`~/dev/jolt` checkout, whose `make-pack.sh` reads only from a ChezScheme
checkout, so nothing from a jolt tree ends up in a pack.

## Quick start

```sh
jolt test                            # the six pure namespaces, on the host

SDK=device jolt deps                 # SDL 2.32.10 and raylib 6.0, static, iphoneos
NS=raylib.link  TARGET=device jolt build-app     # does it link?
UDID=<hardware udid> jolt deploy                # sign, install, launch, watch stdout

NS=raylib.gallery TARGET=device jolt build-app
UDID=<hardware udid> CONSOLE=0 jolt deploy      # detached, for actually playing
```

`jolt devices` lists what `deploy` can talk to, and prints the **hardware**
udid (`00008150-...`), which is what a provisioning profile lists under
`ProvisionedDevices`. Passing the CoreDevice identifier instead makes the
device-coverage check fail against a profile that covers the phone perfectly
well.

The first `jolt deps` takes a few minutes. After that both archives are
cached under `~/dev/{sdl2,raylib}-ios-dev`.

For live development against the running app, and for every failure worth
recognising on sight, see [`tools/ios/RUNBOOK.md`](tools/ios/RUNBOOK.md):

```sh
jolt live                                # the gallery with an nREPL
jolt proxy                               # another terminal: forward it over USB
tools/ios/nrepl-eval 7888 '(System/getenv "HOME")'   # prove it is the phone
```

Reads are free. Anything touching raylib goes through
`raylib.host/on-next-frame!`, which runs it on the main thread at the top of
the next frame, because an eval lands on the nREPL thread and the toolkit is
main-thread-affine.

That gives you jolt's built-in ops: `clone`, `describe`, `eval`, `load-file`,
`close`. Enough for a script or a prompt. For an editor, `CIDER=1 jolt live`
builds `raylib.live-cider` under the `:cider` alias and adds completions,
`info`, `eldoc`, the namespace browser, macroexpansion, apropos and the test
ops, by composing [jolt-lang/nrepl](https://github.com/jolt-lang/nrepl) over
the same handler. It is the project's only dependency and it is opt-in, which
is why the paragraph above can still say there are none: 27 MB and no deps by
default, 33 MB and three with it.

### Namespaces worth building

| namespace | what it does |
|---|---|
| `raylib.link` | one call into each archive, no window. Proves the link, the frameworks and the export trie |
| `raylib.touch` | scalar touch polling, press edges, a marker under the finger |
| `raylib.flappy` | the Android experiment's Flappy Bird, unchanged, under the owner loop |
| `raylib.gallery` | the scene contract: cards, hit testing, Back, all three scenes |
| `raylib.live` | the gallery plus an nREPL, so an editor can drive the running app |
| `raylib.live-cider` | the same with the cider-nrepl ops, under `-A:cider` (the one optional dependency) |

`raylib.link` and `raylib.touch` are bring-up tools, kept on purpose. Nothing
runs them and they are not dead code: they are the two rungs that isolate a
failure when the gallery does not come up. `link` calls one function from each
archive with no window at all, so it separates a broken link, a missing
framework or an empty export trie from anything to do with rendering. `touch`
is the smallest thing that draws and responds to a finger. Reach for them first
after an Xcode, SDK, SDL or raylib bump, when the useful question is which
layer moved rather than what the gallery is doing.

## Layout

```
src/raylib/objc.clj      three Objective-C runtime calls, and nothing else
src/raylib/probe.clj     the measuring apparatus, all of it off by default
src/raylib/host.clj      the owner loop: SDL_UIKitRunApp, InitWindow, the frame
src/raylib/{link,touch,flappy,gallery}.clj   scenes for that host
src/poc/raylib/*.cljc    six pure namespaces, byte-identical to 6d2b291
test/poc/raylib/*.cljc   their tests, likewise
tools/ios/deps.sh        SDL2 and raylib, cross-built static
tools/ios/build.sh       jolt build --target, with both archives on the link line
tools/ios/deploy.sh      sign, install, launch
tools/ios/pack.sh        a target pack from scratch, if you need to build one
tools/ios/devices.sh     what deploy can talk to, asked rather than cached
```

`raylib.host` takes a scene as `{:title :init :frame}` and calls `(frame
state)` between `BeginDrawing` and `EndDrawing`. A scene is a reducer over
frames, so nothing in it polls, draws or holds a native value. That contract
is the Android experiment's, and it is the reason their `.cljc` files run here
untouched.

## Four traps the scripts encode

Each one cost a debugging session in the notebooks.

**`CUSTOMIZE_BUILD=ON` turns disabled options ON.** raylib's `config.h`
spells a disabled option `#define SUPPORT_X 0`, and the parser behind
`CUSTOMIZE_BUILD` keys on the `#define` while ignoring the value. That flips
`SUPPORT_CUSTOM_FRAME_CONTROL` on, under which `EndDrawing` does no swap, no
timing and no event poll, and `GetFPS` returns a literal 0. Both it and
`SUPPORT_BUSY_WAIT_LOOP` are explicitly `OFF` in `deps.sh`. The tell was
`GetTime` advancing while `GetFrameTime` stayed at 0.0.

**Audio is not `USE_AUDIO`.** The module switch is `SUPPORT_MODULE_RAUDIO`.
Without it `raudio.c` compiles miniaudio, which on `TARGET_OS_IPHONE` includes
`AVFoundation.h`: Objective-C headers inside a C translation unit, about forty
errors deep.

**CMake's cross-compile find root.** `CMAKE_SYSTEM_NAME=iOS` makes
`find_package` search only the sysroot and ignore `CMAKE_PREFIX_PATH`, so
`SDL2_DIR` has to be named and the root widened. Widening it then finds the
wrong SDL, because raylib prefers SDL3 and Homebrew may have a macOS SDL3
sitting there, which it will cheerfully use for an iOS build. Hence
`CMAKE_DISABLE_FIND_PACKAGE_SDL3=ON`.

**The archives are `-force_load`ed, not `-l`ed.** No C code references either
one, and Chez looks its symbols up at run time, long after linking has
finished, so an archive member nobody references is dropped before Chez ever
asks for it. `-export_dynamic` goes with it, because a `defcfn` becomes a
`dlsym` at first call and an executable's own globals are not in its export
trie without it.

## Numbers, measured

An iPhone 17 Pro on iOS 26.6.1, running `raylib.gallery` with Flappy Bird
open, over portable bytecode with every draw call a libffi call. The host
prints a summary every 300 frames:

```
host: 402 x 874 points x scale 3.0 -> screen 1206 x 2622 drawable 1206 x 2622 fbo 1
gallery: safe-area top 62.0 pt = 186 px
host:  300 frames, mean 17.83 ms, worst 279.0 ms      <- InitWindow lands in this window
host:  600 frames, mean 17.03 ms, worst  17.4 ms
host: 1200 frames, mean 17.02 ms, worst  17.1 ms
host: 3000 frames, mean 17.03 ms, worst  17.2 ms
host: 5400 frames, mean 17.02 ms, worst  17.1 ms
```

A 60 Hz frame is 16.67 ms, so a mean of 17.02 with a worst of 17.1 over ninety
seconds is a loop that finishes its work and waits for vsync every single
frame, with no outliers at all. The interpreter's cost fits inside the slack
with room to spare: the simulation, the reducer, the twenty-odd FFI calls per
frame and the collector all land well inside a frame. The 279 ms in the first
window is `InitWindow` compiling shaders and building the default font.

### `GetFPS` must be called every frame, and nothing says so

Worth knowing, because it looks exactly like a broken frame rate and is not.

An earlier version of this host printed `GetFPS()` in its 300-frame summary,
and the readings decayed: 1757, 887, 590, 441, 355, 295, and on down to 98
over ninety seconds, while `GetFrameTime` never moved off 17.02 ms.

`GetFPS` is a stateful sampler. Each call advances a 30-slot ring by one
position, writes `GetFrameTime()/30` into that slot, and returns
`1/sum-of-ring`. That is a frame rate only when the ring holds a full 30
slots, which happens only if you call it every frame. `DrawFPS` does, and it
is the only place raylib itself ever calls it. Call it once per 300 frames
instead and after n calls just n slots are filled, so it returns
`1/(n * frame-time/30)`.

That model has no free parameters, since the slot size comes from the measured
frame time and raylib's own `FPS_CAPTURE_FRAMES_COUNT`, and it fits all
eighteen readings to within 0.76%. The controlled run settles it: the same
binary running `raylib.flappy`, which draws `GetFPS` every frame, reported a
steady 59 from the first window through 5700 frames.

So raylib is behaving as designed and the misuse was ours. `raylib.host` now
computes the window's rate from the frame times it already sums, which needs
nothing from raylib and cannot drift. Scenes that draw `GetFPS` every frame,
which is `raylib.flappy` and `raylib.touch`, were always fine.

Before and after on the same phone, both readings taken on the gallery's card
screen so the comparison holds one thing constant:

| window | `GetFPS`, once per 300 frames | the window's own rate |
|---|---|---|
| 300 | 1757 | 49.6 |
| 600 | 887 | 58.8 |
| 900 | 590 | 58.8 |
| 1200 | 441 | 58.8 |
| 1800 | 295 | 58.8 |

Mean frame time sat at 17.01 ms across both runs, so 58.8 is the real number.
The first window reads low rather than high now, which is correct: a 966 ms
`InitWindow` lands inside it.

The one fair complaint is upstream and small: `raylib.h` documents `GetFPS`
as "Get current FPS" and never mentions the requirement, so calling it from a
timer or a summary gives a plausible wrong number rather than an obviously
wrong one.

## Test on the phone, not the simulator

The simulator has not displayed OpenGL ES since iOS 17.5. Pixels reach the
framebuffer, which `glReadPixels` will confirm, and the screen stays black.
SDL2 is named specifically in Apple's own forum thread 803483, and the same
build renders correctly on hardware. `build.sh` says so when asked for a
simulator target.

Two related things about iOS itself, both of which look like rendering bugs:

- **iOS has no default framebuffer, and raylib turns out to cope anyway.**
  SDL's `README-ios` requires the drawable FBO bound while rendering and the
  colour renderbuffer bound at swap, and `rcore_desktop_sdl.c` binds neither.
  The obvious conclusion, which the notebooks drew and this project believed
  for a day, is that raylib therefore draws into framebuffer 0 and a device
  shows nothing. It does not. Measured on hardware, both bindings are already
  correct at swap time whether or not `raylib.host` binds anything, because
  SDL's own swap path leaves the drawable bound and the binding survives
  between frames. `raylib.host` binds them anyway, which is a no-op today and
  cheap insurance against an SDL or raylib that stops doing so. The full
  measurement, and why the wrong conclusion was so easy to reach, is in
  [`docs/upstream-findings.md`](docs/upstream-findings.md).
- **The console is a leash.** `devicectl ... --console` streams stdout, and
  ending that session signals the app: SDL posts `SDL_QUIT`, raylib sets
  `shouldClose`, the loop closes the window, and SDL's delegate deliberately
  does not exit, so a black window is left behind. Use `CONSOLE=0` to launch
  detached once you want to play rather than debug.

## Attribution

- [jasalt/jolt-android-experiment](https://github.com/jasalt/jolt-android-experiment)
  at `6d2b291`: the scene contract, the input normalisation and all three
  scenes, unchanged. RAY-009 established that jolt can own the raylib loop,
  and RAY-018 wrote Flappy Bird as a pure simulation so that the same file
  could run under a different host. It does.
- [statonjr/glimmer-ios-demo](https://github.com/statonjr/glimmer-ios-demo):
  the iOS owner loop, the SDL discovery, the CMake recipes and every trap
  above.
- [raylib](https://github.com/raysan5/raylib) 6.0 and
  [SDL](https://github.com/libsdl-org/SDL) 2.32.10.
- [raylib-jlt](https://github.com/jlt-commons/raylib-jlt) is not a dependency
  here, but `raylib.host`'s binding subset follows the shapes its core example
  established, including packed `:uint` colours and the `[:by-value ...]`
  form.
