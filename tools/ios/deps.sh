#!/bin/sh
# deps.sh: cross-build SDL2 and raylib as static archives for iOS.
#
#   SDK=device sh tools/ios/deps.sh      # -> ~/dev/{sdl2,raylib}-ios-dev
#   SDK=sim    sh tools/ios/deps.sh      # -> ~/dev/{sdl2,raylib}-ios-sim
#
# raylib ships no iOS platform layer and does not need one: built PLATFORM=SDL
# against an iOS SDL2, SDL's own iOS support IS the platform layer.
#
# Sources unpack to ~/dev/SDL2-<v> and ~/dev/raylib-<v>, NOT ~/dev/SDL2 and
# ~/dev/raylib: this machine already has a raylib git checkout at ~/dev/raylib
# (6.1-dev, a fork) and the recipe is pinned to the 6.0 release.
set -eu

SDL_VERSION=2.32.10
RAYLIB_VERSION=6.0
SDK=${SDK:-device}
DEV=${DEV:-$HOME/dev}

case "$SDK" in
  device) SYSROOT=iphoneos;        SUFFIX=dev; WANT=2 ;;
  sim)    SYSROOT=iphonesimulator; SUFFIX=sim; WANT=7 ;;
  *) echo "deps.sh: SDK must be device or sim, got '$SDK'" >&2; exit 2 ;;
esac

SDL_SRC="$DEV/SDL2-$SDL_VERSION"
RAYLIB_SRC="$DEV/raylib-$RAYLIB_VERSION"
SDL_PREFIX="$DEV/sdl2-ios-$SUFFIX"
RAYLIB_PREFIX="$DEV/raylib-ios-$SUFFIX"
BUILD="build-ios-$SUFFIX"

COMMON="-DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_SYSROOT=$SYSROOT -DCMAKE_OSX_ARCHITECTURES=arm64 -DCMAKE_OSX_DEPLOYMENT_TARGET=14.0 -DCMAKE_BUILD_TYPE=Release"

# ---------------------------------------------------------------- 1. SDL2
# JOYSTICK stays ON deliberately: raylib's InitPlatform asks SDL_Init for
# GAMECONTROLLER, which implies joysticks, and SDL_Init failing means
# InitWindow bails before the first frame. HIDAPI stays off, so no
# CoreBluetooth.
if [ -f "$SDL_PREFIX/lib/libSDL2.a" ]; then
  echo "== SDL2 ($SUFFIX) already built"
else
  echo "== SDL2 $SDL_VERSION for $SUFFIX"
  [ -d "$SDL_SRC" ] || {
    curl -sfL "https://github.com/libsdl-org/SDL/releases/download/release-$SDL_VERSION/SDL2-$SDL_VERSION.tar.gz" \
      | tar xz -C "$DEV"
  }
  cd "$SDL_SRC"
  # shellcheck disable=SC2086
  cmake -S . -B "$BUILD" -G "Unix Makefiles" $COMMON \
    -DCMAKE_INSTALL_PREFIX="$SDL_PREFIX" \
    -DSDL_SHARED=OFF -DSDL_STATIC=ON -DSDL_TEST=OFF -DSDL_TESTS=OFF \
    -DSDL_AUDIO=OFF -DSDL_HAPTIC=OFF -DSDL_HIDAPI=OFF -DSDL_JOYSTICK=ON -DSDL_SENSOR=OFF >/dev/null
  cmake --build "$BUILD" -j8 >/dev/null
  cmake --install "$BUILD" >/dev/null
fi

# --------------------------------------------------------------- 2. raylib
# Three CMake traps, all of them CMake's or Homebrew's rather than raylib's C:
#
# 1. CMAKE_SYSTEM_NAME=iOS makes find_package search ONLY the sysroot and
#    ignore CMAKE_PREFIX_PATH, so SDL2_DIR must be named and the find root
#    widened with CMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH.
# 2. Widening it finds the WRONG SDL: raylib prefers SDL3, and Homebrew has a
#    macOS SDL3 on this machine which it will happily use for an iOS build.
# 3. Audio is not USE_AUDIO. The module switch is SUPPORT_MODULE_RAUDIO under
#    CUSTOMIZE_BUILD; without it raudio.c compiles miniaudio, which on
#    TARGET_OS_IPHONE includes AVFoundation.h -- Objective-C headers in a C
#    translation unit.
#
# And CUSTOMIZE_BUILD=ON turns DISABLED options ON: raylib's config.h spells a
# disabled option `#define SUPPORT_X 0` and the parser reads any `#define
# SUPPORT_*` as ON, value ignored. Under SUPPORT_CUSTOM_FRAME_CONTROL,
# EndDrawing does no swap, no timing and no event poll, and GetFPS returns a
# literal 0. Both are explicit OFF below.
if [ -f "$RAYLIB_PREFIX/lib/libraylib.a" ]; then
  echo "== raylib ($SUFFIX) already built"
else
  echo "== raylib $RAYLIB_VERSION for $SUFFIX"
  [ -d "$RAYLIB_SRC" ] || {
    curl -sfL "https://github.com/raysan5/raylib/archive/refs/tags/$RAYLIB_VERSION.tar.gz" | tar xz -C "$DEV"
  }
  cd "$RAYLIB_SRC"
  # shellcheck disable=SC2086
  cmake -S . -B "$BUILD" -G "Unix Makefiles" $COMMON \
    -DCMAKE_INSTALL_PREFIX="$RAYLIB_PREFIX" \
    -DCMAKE_FIND_ROOT_PATH="$SDL_PREFIX" -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=BOTH \
    -DSDL2_DIR="$SDL_PREFIX/lib/cmake/SDL2" -DCMAKE_DISABLE_FIND_PACKAGE_SDL3=ON \
    -DPLATFORM=SDL -DGRAPHICS=GRAPHICS_API_OPENGL_ES2 \
    -DBUILD_SHARED_LIBS=OFF -DBUILD_EXAMPLES=OFF \
    -DCUSTOMIZE_BUILD=ON -DSUPPORT_MODULE_RAUDIO=OFF \
    -DSUPPORT_CUSTOM_FRAME_CONTROL=OFF -DSUPPORT_BUSY_WAIT_LOOP=OFF >/dev/null
  cmake --build "$BUILD" -j8 >/dev/null
  cmake --install "$BUILD" >/dev/null
fi

# ------------------------------------------------------- 3. verify, do not assume
# A wrong-SDK archive links and then fails somewhere far from here, so assert
# the platform rather than trusting the sysroot flag went through.
# Read a real member name out of the archive rather than naming one: SDL's
# objects are SDL.c.o and raylib's rcore.c.o under CMake, but that is a build
# system's convention and a guess that misses fails identically to a genuinely
# wrong-platform archive.
verify() {                        # $1 = archive
  member=$(ar t "$1" | grep -m1 '\.o$') || { echo "deps.sh: $1 has no objects" >&2; exit 1; }
  T=$(mktemp -d)
  ( cd "$T" && ar x "$1" "$member" && otool -l "$member" | grep -A2 LC_BUILD_VERSION | grep -q "platform $WANT" ) \
    || { echo "deps.sh: $1 ($member) is not platform $WANT" >&2; rm -rf "$T"; exit 1; }
  rm -rf "$T"
  echo "   $1: $member is platform $WANT"
}
verify "$SDL_PREFIX/lib/libSDL2.a"
verify "$RAYLIB_PREFIX/lib/libraylib.a"
echo "deps.sh: $SUFFIX ok (platform $WANT)"
ls -l "$SDL_PREFIX/lib/libSDL2.a" "$RAYLIB_PREFIX/lib/libraylib.a"
