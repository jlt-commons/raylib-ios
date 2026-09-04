# Building raylib and SDL2 for iOS

Two static archives, cross-compiled with CMake, and four traps that each cost a
debugging session before the recipe settled. `tools/ios/deps.sh` encodes all of
it. This page is why each flag is there, because the script is short and the
reasoning is not.

```sh
SDK=device sh tools/ios/deps.sh    # -> ~/dev/{sdl2,raylib}-ios-dev
SDK=sim    sh tools/ios/deps.sh    # -> ~/dev/{sdl2,raylib}-ios-sim
```

Slow the first time and cached after. The device build is the one that matters;
the simulator cannot show GLES output, so it is only useful for proving a link.

## Why SDL is in the picture at all

raylib ships platform layers for Android, GLFW, RGFW, SDL, Win32 and DRM. There
is no `rcore_ios.c` and there has never been one. The instinct is to write it,
and that is weeks of work against UIKit, EAGL and the touch API.

SDL already did it. Built `PLATFORM=SDL` against an SDL2 that was itself
compiled for iOS, SDL's own iOS support becomes raylib's platform layer, and the
question stops being "how do we port raylib" and turns into "how do we link two
archives". That is the single decision the rest of this project rests on.

## The shared flags

```sh
-DCMAKE_SYSTEM_NAME=iOS
-DCMAKE_OSX_SYSROOT=iphoneos          # or iphonesimulator
-DCMAKE_OSX_ARCHITECTURES=arm64
-DCMAKE_OSX_DEPLOYMENT_TARGET=14.0
```

`CMAKE_SYSTEM_NAME=iOS` is what makes this a cross-compile rather than a macOS
build with an odd sysroot, and it changes `find_package` behaviour in a way the
third trap below is entirely about.

## SDL2

```sh
-DSDL_SHARED=OFF -DSDL_STATIC=ON
-DSDL_AUDIO=OFF -DSDL_HAPTIC=OFF -DSDL_HIDAPI=OFF -DSDL_SENSOR=OFF
-DSDL_JOYSTICK=ON
```

Static because there is no dylib in an `.ipa` and nothing to load one anyway:
jolt emits a whole executable and Chez owns `main`.

Joystick stays ON despite nothing here using a joystick. Turning it off makes
SDL's video initialisation fail on iOS, and the failure surfaces far from its
cause. The cost of leaving it on is one warning per launch:

```
WARNING: PLATFORM: Unable to open game controller [ERROR: Parameter 'joystick' is invalid]
```

which is harmless and worth not chasing.

## raylib

```sh
-DPLATFORM=SDL -DGRAPHICS=GRAPHICS_API_OPENGL_ES2
-DBUILD_SHARED_LIBS=OFF -DBUILD_EXAMPLES=OFF
-DCUSTOMIZE_BUILD=ON -DSUPPORT_MODULE_RAUDIO=OFF
-DSUPPORT_CUSTOM_FRAME_CONTROL=OFF -DSUPPORT_BUSY_WAIT_LOOP=OFF
-DCMAKE_FIND_ROOT_PATH="$SDL_PREFIX" -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH
-DSDL2_DIR="$SDL_PREFIX/lib/cmake/SDL2" -DCMAKE_DISABLE_FIND_PACKAGE_SDL3=ON
```

## The four traps

Each one is a line above that looks removable and is not.

### 1. CUSTOMIZE_BUILD=ON turns disabled options ON

raylib's `config.h` spells a disabled option as `#define SUPPORT_X 0`. The
parser behind `CUSTOMIZE_BUILD` keys on the `#define` and ignores the value, so
an option written as explicitly off is read as present and therefore on.

The one that matters is `SUPPORT_CUSTOM_FRAME_CONTROL`. Under it `EndDrawing`
does no buffer swap, no timing and no event poll, which means the loop in
`raylib.host` spins without presenting anything and `GetFPS` returns a literal
zero.

The tell is worth remembering because it is not obviously a build problem:
`GetTime` advances normally while `GetFrameTime` stays at 0.0. Time is passing
and no frames are happening.

Both it and `SUPPORT_BUSY_WAIT_LOOP` are therefore named `OFF` explicitly.

### 2. Audio is not USE_AUDIO

The module switch is `SUPPORT_MODULE_RAUDIO`. Guess at `USE_AUDIO` and nothing
happens, so `raudio.c` compiles, which pulls in miniaudio, which on
`TARGET_OS_IPHONE` includes `AVFoundation.h`. Objective-C headers inside a C
translation unit, and the build dies about forty errors deep with nothing in
the output mentioning audio.

### 3. CMake's cross-compile find root

`CMAKE_SYSTEM_NAME=iOS` makes `find_package` search only the sysroot and ignore
`CMAKE_PREFIX_PATH` entirely, so the freshly built iOS SDL2 is invisible.
Naming `SDL2_DIR` and widening `CMAKE_FIND_ROOT_PATH` fixes that, and then
introduces a worse problem.

raylib prefers SDL3 when it can find one. A Homebrew macOS SDL3 sitting in the
widened root is a perfectly good find as far as CMake is concerned, and it will
be used for an iOS build. Hence `CMAKE_DISABLE_FIND_PACKAGE_SDL3=ON`, which
reads like paranoia and is not.

### 4. The archives are -force_load'ed, not -l'ed

This one is about Chez rather than CMake, and it lives in `tools/ios/build.sh`.

No C code in the program references raylib or SDL. Every call goes through
`jolt.ffi`, which resolves a symbol by name at run time, long after linking has
finished. The linker sees an archive nobody references and does exactly what it
is supposed to: drops the members. Then `defcfn` looks for `InitWindow` and it
is not in the binary.

`-force_load` links every member whether anything references it or not.
`-export_dynamic` goes with it, because a `defcfn` becomes a `dlsym` against the
process image at first call and an executable's own globals are not in its
export trie by default.

## Verifying an archive is the right one

A wrong-SDK archive links cleanly and fails somewhere far away, so `deps.sh`
asserts the platform of what it built rather than trusting the flags:

```sh
otool -l "$SDL_PREFIX/lib/libSDL2.a" | grep -c 'platform 2'   # device
```

Platform 2 is iOS and platform 7 is the simulator. Checking here turns a
confusing runtime failure into an obvious build-time one.

## What the build produces

`tools/ios/build.sh` cross-compiles the Clojure to threaded portable bytecode,
links it with both archives and the frameworks SDL needs, and writes an app
bundle:

```sh
NS=raylib.gallery TARGET=device sh tools/ios/build.sh
```

About 29 MB, most of it Chez. `tools/ios/RUNBOOK.md` has the failure modes.
