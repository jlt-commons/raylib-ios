# raylib-ios

raylib 6.0 and SDL2 rendering on a physical iPhone, driven from Clojure running
on Chez Scheme via [Jolt](https://github.com/jolt-lang/jolt). Forty scenes
at 52 to 60 fps, as threaded portable bytecode, with no JIT and nothing
generated at run time.

This is the orientation page. The two guides after it are the ones worth
reading, and both are about things the device taught us rather than things the
code implied.

## Why this works at all

Two facts do most of the work, and neither is obvious.

**raylib has no iOS platform layer, and does not need one.** It ships backends
for Android, GLFW, RGFW, SDL, Win32 and DRM. There is no `rcore_ios.c`. Built
`PLATFORM=SDL` with `GRAPHICS_API_OPENGL_ES2` against an SDL2 compiled for iOS,
SDL's own iOS support becomes the platform layer, and the question stops being
"how do we port raylib" and starts being "how do we link two static archives".

**iOS forbids generating code at run time**, which rules out a JIT. Chez
Scheme's threaded portable bytecode target sidesteps that by shipping the
interpreter as the app: nothing is compiled on the device, so App Store rule
2.5.2 is satisfied by construction rather than by permission.

## Who owns thread 0

This is the whole design, and it is the question every attempt at this founders
on.

`jolt build --target tpb64l` emits a complete executable and Chez owns `main`,
so there is no host process and no dylib to load. raylib and SDL2 are static
archives linked in with `-force_load`, and every `defcfn` resolves against the
process image at first call.

From Clojure, `main` calls `SDL_UIKitRunApp` through the FFI, blocking. SDL runs
`UIApplicationMain`, and its delegate calls back on thread 0. The raylib loop
then lives there for the life of the app.

A blocking `while` loop is normally wrong on iOS, because it starves the run
loop that delivers touches and lifecycle events. It is fine here, and the reason
is worth tracing rather than taking on trust, since it is the single fact the
whole design rests on. Every link below was read in the sources this project
actually builds, raylib 6.0 and SDL 2.32.10:

`EndDrawing` calls `PollInputEvents`, which runs `while (SDL_PollEvent(&e))`.
`SDL_PollEvent` is `SDL_WaitEventTimeout(event, 0)`, and at a zero timeout that
calls `SDL_PumpEventsInternal`. On UIKit the pump is `UIKit_PumpEvents`, which
spins `CFRunLoopRunInMode`.

So the run loop gets a turn once per frame, from inside the draw call, and the
two take turns rather than competing. Note that `EndDrawing` never names
`SDL_PumpEvents` itself. Three functions sit between them, which is why reading
`rcore_desktop_sdl.c` alone does not settle the question.

## The scene contract

Every scene is a reducer over frames:

```clojure
{:id :lorenz :title "Lorenz"
 :init    (fn [input]        [state events])
 :update  (fn [state input]  [state events])
 :draw    (fn [state input]  [state events])
 :dispose (fn [state]        [state events])}
```

No raylib call appears anywhere in it. Drawing is a separate multimethod that
reads the state a scene produced, which is why the scenes are pure `.cljc` and
test on a build host with no raylib, no SDL and no device.

That separation is not tidiness. Three of the scenes came from the
[Jolt Android experiment](https://github.com/jasalt/jolt-android-experiment)
byte-identical, sha256 verified, along with three more namespaces carrying the
contract itself. They were written for a different platform and run here
untouched. The other thirty-seven are ports from
[raylib-jlt](https://github.com/jlt-commons/raylib-jlt).

## The guides

**[Performance on a phone](performance-on-a-phone.html)** is the one to read
first. It starts with a wrong guess, that the FFI boundary was the cost, and
follows the measurements to what it actually was. One scene makes about 2400
calls into C every frame at 59 fps while another drawing 1800 lines managed 18.
The answer was allocation in the draw loop. It also covers why `GetFPS` returns
a plausible wrong number when you call it the obvious way, why primitive count
is a proxy rather than a budget, and why a reading past the knee is not
repeatable.

**[Porting an example](porting-an-example.html)** is the practical one: taking a
raylib-jlt example that owns its own loop and turning it into a scene that does
not. Inverting the loop, replacing `GetRandomValue` with a seeded generator so
a scene replays identically, deriving geometry from the live screen instead of a
fixed 800x450, and moving drawing to the caller.

**[Building the archives](building-the-archives.html)** is the part that has to
work before anything else does: cross-compiling raylib and SDL2 for iOS, and the
four CMake traps that each cost a debugging session. One of them makes raylib
report a frame rate of zero while time keeps advancing.

**[A REPL on the phone](a-repl-on-the-phone.html)** is how most of the
measurements here were taken, from an editor against a running app on a device.
It also covers the two traps that make it lie: forwarding onto the wrong
process, and a release build where redefinition updates what the REPL sees and
not what the loop calls.

**[The safe area](the-safe-area.html)** is the Dynamic Island and the home
indicator, and how scenes came to respect them without any scene changing.
rlgl's matrix stack does the work.

**[Filling shapes with rlgl](rlgl-immediate-mode.html)** is the escape hatch for
the two scenes that fill rather than outline, and why raylib's own shapes API
cannot draw a triangle with a different colour at each corner.

**[The scenes](scene-catalog.html)** lists all forty with what each costs
per frame and what it measured.

## Getting it running

The README covers the build in full. The short version:

```sh
SDK=device sh tools/ios/deps.sh                   # raylib + SDL2 static archives
NS=raylib.gallery TARGET=device sh tools/ios/build.sh
UDID=<hardware udid> sh tools/ios/deploy.sh
```

`tools/ios/RUNBOOK.md` has the failure modes, including the four CMake traps
worth knowing before the first build.

For a REPL inside the running app, `tools/ios/live.sh` builds a variant with
`jolt.nrepl` and `tools/ios/proxy.sh` forwards its port over USB. Reading live
scene state from an editor is how most of the measurements in the performance
guide were taken. One trap: a release build inlines across call sites, so
redefining a var updates what the REPL sees while the running loop keeps calling
the original. Use `DEV_BUILD=1` when you intend redefinition to take effect.
