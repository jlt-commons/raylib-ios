#!/bin/sh
# build.sh: cross-compile NS into RaylibIOS.app for TARGET, with SDL2 and
# raylib linked in statically.
#
#   NS=net.b12n.raylib-ios.link    TARGET=device sh tools/ios/build.sh
#   NS=net.b12n.raylib-ios.gallery TARGET=device sh tools/ios/build.sh
#
# It builds the jolt project it is run from, not the one it lives in, so another
# repo can build its own app with these tools:
#
#   cd ~/dev/my-app && NS=my.app.main sh /path/to/raylib-ios/tools/ios/build.sh
#
# PROJECT_DIR (default: the current directory) holds deps.edn, and the app is
# written to $PROJECT_DIR/RaylibIOS.app unless APP says otherwise. Info.plist
# and the pack and archive paths belong to this repo and are found from where
# this script is, whatever the current directory is. DRY_RUN=1 prints what was
# resolved and stops, before anything is checked or written.
#
# Both targets are tpb64l: threaded portable bytecode. Native code (tarm64ios)
# is not an option on a device -- iOS requires executable pages to come from a
# signed, immutable source, so a native build dies on launch with `mprotect
# failed`. On the simulator native WOULD work, but see the warning below: the
# simulator cannot display OpenGL ES at all, so there is nothing to see there.
set -eu

# This repo's tools directory, found from the script and not from $PWD.
TOOLS_DIR=$(cd "$(dirname "$0")" && pwd)

NS=${NS:-net.b12n.raylib-ios.link}
TARGET=${TARGET:-device}
ALIAS=${ALIAS:-}
DEV=${DEV:-$HOME/dev}

PROJECT_DIR=${PROJECT_DIR:-$PWD}
[ -d "$PROJECT_DIR" ] || { echo "build.sh: PROJECT_DIR '$PROJECT_DIR' is not a directory" >&2; exit 2; }
PROJECT_DIR=$(cd "$PROJECT_DIR" && pwd)
APP=${APP:-$PROJECT_DIR/RaylibIOS.app}
# A relative APP is relative to the project, which is where jolt runs.
case "$APP" in /*) ;; *) APP="$PROJECT_DIR/$APP" ;; esac
INFO_PLIST="$TOOLS_DIR/Info.plist"

case "$TARGET" in
  device) SUFFIX=dev; WANT=2; SDK=iphoneos; TRIPLE=arm64-apple-ios14.0 ;;
  sim)    SUFFIX=sim; WANT=7; SDK=iphonesimulator; TRIPLE=arm64-apple-ios-simulator ;;
  *) echo "build.sh: TARGET must be sim or device, got '$TARGET'" >&2; exit 2 ;;
esac

WORK=${WORK:-/tmp/raylib-ios}
# Relative paths are the caller's; the build cds into PROJECT_DIR below.
case "$DEV" in /*) ;; *) DEV="$PWD/$DEV" ;; esac
case "$WORK" in /*) ;; *) WORK="$PWD/$WORK" ;; esac
PACK="$WORK/pack/$([ "$TARGET" = device ] && echo device || echo sim)"
SDL_A="$DEV/sdl2-ios-$SUFFIX/lib/libSDL2.a"
RAYLIB_A="${RAYLIB_A:-$DEV/raylib-ios-$SUFFIX/lib/libraylib.a}"
case "$RAYLIB_A" in /*) ;; *) RAYLIB_A="$PWD/$RAYLIB_A" ;; esac

if [ "${DRY_RUN:-0}" = 1 ]; then
  for v in PROJECT_DIR NS ALIAS TARGET APP INFO_PLIST PACK SDL_A RAYLIB_A; do
    eval "echo \"dry-run: $v=\$$v\""
  done
  exit 0
fi

# Computed after the dry run so that a dry run needs no Xcode.
ARCH="-target $TRIPLE -isysroot $(xcrun -sdk "$SDK" --show-sdk-path)"
if [ "$TARGET" = sim ]; then
  echo "build.sh: NOTE the simulator has not displayed OpenGL ES since iOS 17.5." >&2
  echo "build.sh: pixels reach the framebuffer and the screen stays black; test on the phone." >&2
fi

# ---- the target pack
# Gate on link-libs rather than the directory: pack.sh rm -rf's its output and
# recreates it before make-pack.sh runs, so the DIRECTORY exists long before the
# pack is usable and a half-built pack would sail past a -d test.
#
# A tpb64l pack describes the Chez runtime and the SDK, not the app, so the
# sibling glimmer-ios project's packs are interchangeable with ones this project
# would build -- same Chez 10.4.1, same machine type, same two SDKs. Falling
# back to them saves twenty minutes and a ChezScheme checkout; it is announced
# rather than silent, because a pack you did not build is a pack you cannot
# reason about when a link goes wrong.
if [ ! -f "$PACK/link-libs" ]; then
  BORROWED="/tmp/glimmer-ios/pack/$([ "$TARGET" = device ] && echo device || echo sim)"
  if [ -f "$BORROWED/link-libs" ]; then
    echo "build.sh: no pack at $PACK, borrowing the glimmer-ios one at $BORROWED"
    PACK="$BORROWED"
  else
    echo "build.sh: no usable target pack at $PACK (link-libs missing), run $TOOLS_DIR/pack.sh" >&2
    exit 2
  fi
fi

# Read link-libs on its own line. As an assignment PREFIX to the jolt command
# below, a failing $(cat ...) does NOT trip set -e -- the command still runs,
# with the libraries silently empty, and you wait out a whole cross-compile to
# get undefined _ffi_call with the real cause long gone.
LINK_LIBS=$(cat "$PACK/link-libs")

# ---- the two static archives
for a in "$SDL_A" "$RAYLIB_A"; do
  [ -f "$a" ] || { echo "build.sh: no $a -- run: SDK=$([ "$TARGET" = device ] && echo device || echo sim) sh $TOOLS_DIR/deps.sh" >&2; exit 2; }
done

# -force_load, not -l. Nothing in C references either archive: Chez looks its
# symbols up at RUN time, long after linking is over, and an archive member no
# C code references is dropped before Chez ever asks for it.
#
# -export_dynamic keeps the executable's OWN globals in its export trie, which
# is where dlsym looks -- and a defcfn becomes a dlsym at first call. Without it
# every raylib symbol resolves to nothing in an executable that provably
# contains it.
ARCHIVES="-Wl,-force_load,$SDL_A -Wl,-force_load,$RAYLIB_A -Wl,-export_dynamic"

# Read off the archive rather than guessed: `nm -u libSDL2.a` names the
# Objective-C classes it wants and each class names its framework. CoreHaptics,
# CoreMotion and GameController are the joystick driver's -- raylib's
# InitPlatform asks SDL_Init for GAMECONTROLLER, so SDL is built with joysticks
# on and drags them in. OpenGLES is raylib's GLES2 context.
FRAMEWORKS="-framework CoreGraphics -framework QuartzCore -framework OpenGLES -framework Metal -framework CoreVideo -framework AVFoundation -framework CoreMotion -framework GameController -framework CoreHaptics -lobjc"

mkdir -p "$APP"
cp "$INFO_PLIST" "$APP/Info.plist"

# jolt reads deps.edn from the directory it runs in, so run it in the project.
cd "$PROJECT_DIR"

JOLT_TARGET_CC=clang \
JOLT_TARGET_ARCH_FLAG="$ARCH" \
JOLT_TARGET_LINK_LIBS="$ARCHIVES -L$PACK/lib $LINK_LIBS $FRAMEWORKS" \
  jolt ${ALIAS:+-A$ALIAS} build -m "$NS" -o "$APP/RaylibIOS" ${DEV_BUILD:+--dev} --target tpb64l --target-pack "$PACK"
# DEV_BUILD=1 builds --dev instead of the default release. Not DEV, which is
# already this script's name for ~/dev and would break the archive paths. Release inlines across
# call sites, so a var redefined over the nREPL does not reach code that was
# already compiled: the REPL sees the new value and the running loop keeps
# calling the old one. --dev is what makes redefinition actually take effect.
#
# -A must precede the `build` subcommand: `jolt build -A:alias ...` silently
# drops the alias's extra-paths.

rm -rf "$APP/RaylibIOS.build"
# a previous deploy leaves its signature and profile behind; a fresh build is unsigned
rm -rf "$APP/_CodeSignature" "$APP/embedded.mobileprovision"

# assert rather than assume: platform 2 is iOS, 7 is the simulator
otool -l "$APP/RaylibIOS" | grep -A2 LC_BUILD_VERSION | grep -q "platform $WANT" \
  || { echo "build.sh: built binary is not platform $WANT for TARGET=$TARGET" >&2; exit 1; }
echo "build.sh: wrote $APP/RaylibIOS ($NS, $TARGET, platform $WANT, $(du -h "$APP/RaylibIOS" | cut -f1))"
