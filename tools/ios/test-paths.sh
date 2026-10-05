#!/bin/sh
# test-paths.sh: the iOS tools resolve their paths from PROJECT_DIR and from
# their own location, not from the directory they were started in.
#
#   sh tools/ios/test-paths.sh
#
# build.sh has a DRY_RUN=1 mode that prints what it resolved and exits, which is
# what most of this checks. live.sh has none, so it runs here beside stub
# build.sh and deploy.sh that print what they were given. Nothing is built,
# signed or installed, and no device or SDK is needed.
set -eu

HERE=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d "${TMPDIR:-/tmp}/raylib-paths.XXXXXX")
trap 'rm -rf "$T"' EXIT INT TERM

# The tools print the logical path `cd` gives them, so expect the same spelling.
mkdir -p "$T/proj" "$T/elsewhere" "$T/stubs"
PROJ=$(cd "$T/proj" && pwd)
ELSE=$(cd "$T/elsewhere" && pwd)

failures=0
pass() { echo "ok   $1"; }
fail() { echo "FAIL $1" >&2; failures=$((failures + 1)); }

# expect NAME OUTPUT KEY VALUE: OUTPUT has a line "dry-run: KEY=VALUE"
expect() {
  if printf '%s\n' "$2" | grep -qxF "dry-run: $3=$4"; then
    pass "$1: $3=$4"
  else
    fail "$1: wanted $3=$4, got: $(printf '%s\n' "$2" | grep "^dry-run: $3=" || echo '(no such line)')"
  fi
}

dry() { # dry [VAR=value ...]: run build.sh from another directory
  ( cd "$ELSE" && env DRY_RUN=1 WORK="$T/work" DEV="$T/dev" "$@" sh "$HERE/build.sh" 2>&1 )
}

# ---- build-from-another-dir
out=$(dry PROJECT_DIR="$PROJ" NS=foo.app TARGET=device)
expect build-from-another-dir "$out" PROJECT_DIR "$PROJ"
expect build-from-another-dir "$out" NS foo.app
expect build-from-another-dir "$out" TARGET device
expect build-from-another-dir "$out" APP "$PROJ/RaylibIOS.app"
expect build-from-another-dir "$out" INFO_PLIST "$HERE/Info.plist"
expect build-from-another-dir "$out" PACK "$T/work/pack/device"
expect build-from-another-dir "$out" SDL_A "$T/dev/sdl2-ios-dev/lib/libSDL2.a"
expect build-from-another-dir "$out" RAYLIB_A "$T/dev/raylib-ios-dev/lib/libraylib.a"
[ -f "$HERE/Info.plist" ] && pass "Info.plist is beside the tools" || fail "no $HERE/Info.plist"
[ ! -e "$PROJ/RaylibIOS.app" ] && [ ! -e "$ELSE/RaylibIOS.app" ] \
  && pass "a dry run writes no .app" || fail "a dry run wrote an .app"

# ---- PROJECT_DIR defaults to the directory the tool is run from
out=$( cd "$PROJ" && env DRY_RUN=1 WORK="$T/work" DEV="$T/dev" NS=foo.app sh "$HERE/build.sh" 2>&1 )
expect project-dir-defaults-to-pwd "$out" PROJECT_DIR "$PROJ"
expect project-dir-defaults-to-pwd "$out" APP "$PROJ/RaylibIOS.app"

# ---- APP still overrides, and TARGET picks the sim archives
out=$(dry PROJECT_DIR="$PROJ" NS=foo.app TARGET=sim APP="$T/out/Demo.app")
expect app-override "$out" APP "$T/out/Demo.app"
expect sim-target "$out" PACK "$T/work/pack/sim"
expect sim-target "$out" SDL_A "$T/dev/sdl2-ios-sim/lib/libSDL2.a"

# ---- relative DEV, WORK and RAYLIB_A are the caller's, not the project's
out=$(dry PROJECT_DIR="$PROJ" NS=foo.app TARGET=device DEV=reldev WORK=relwork)
expect relative-dev "$out" SDL_A "$ELSE/reldev/sdl2-ios-dev/lib/libSDL2.a"
expect relative-dev "$out" RAYLIB_A "$ELSE/reldev/raylib-ios-dev/lib/libraylib.a"
expect relative-work "$out" PACK "$ELSE/relwork/pack/device"
out=$(dry PROJECT_DIR="$PROJ" NS=foo.app TARGET=device RAYLIB_A=rel/libraylib.a)
expect relative-raylib-a "$out" RAYLIB_A "$ELSE/rel/libraylib.a"

# ---- a PROJECT_DIR that is not a directory is refused
if out=$(dry PROJECT_DIR="$T/missing" NS=foo.app 2>&1); then
  fail "a missing PROJECT_DIR was accepted"
else
  printf '%s\n' "$out" | grep -q "PROJECT_DIR" \
    && pass "a missing PROJECT_DIR is refused, by name" \
    || fail "a missing PROJECT_DIR failed without naming it: $out"
fi

# ---- live.sh passes NS, ALIAS and PROJECT_DIR on to build.sh and deploy.sh
cp "$HERE/live.sh" "$T/stubs/live.sh"
cat > "$T/stubs/build.sh" <<'STUB'
#!/bin/sh
echo "stub-build: NS=$NS ALIAS=${ALIAS:-} PROJECT_DIR=$PROJECT_DIR TARGET=$TARGET"
STUB
cat > "$T/stubs/deploy.sh" <<'STUB'
#!/bin/sh
echo "stub-deploy: PROJECT_DIR=$PROJECT_DIR"
STUB
live() { ( cd "$ELSE" && env UDID=test "$@" sh "$T/stubs/live.sh" 2>&1 ); }
stubbed() { # stubbed NAME OUTPUT LINE
  printf '%s\n' "$2" | grep -qxF "$3" && pass "$1: $3" \
    || fail "$1: wanted '$3', got: $(printf '%s\n' "$2" | grep '^stub-' || echo '(no stub lines)')"
}
out=$(live PROJECT_DIR="$PROJ")
stubbed live-defaults "$out" "stub-build: NS=net.b12n.raylib-ios.live ALIAS= PROJECT_DIR=$PROJ TARGET=device"
stubbed live-defaults "$out" "stub-deploy: PROJECT_DIR=$PROJ"
out=$(live PROJECT_DIR="$PROJ" NS=foo.live)
stubbed live-ns "$out" "stub-build: NS=foo.live ALIAS= PROJECT_DIR=$PROJ TARGET=device"
out=$(live PROJECT_DIR="$PROJ" CIDER=1)
stubbed live-cider "$out" "stub-build: NS=net.b12n.raylib-ios.live-cider ALIAS=:cider PROJECT_DIR=$PROJ TARGET=device"
out=$(live PROJECT_DIR="$PROJ" CIDER=1 NS=foo.live-cider)
stubbed live-cider-ns "$out" "stub-build: NS=foo.live-cider ALIAS=:cider PROJECT_DIR=$PROJ TARGET=device"

if [ "$failures" -gt 0 ]; then
  echo "test-paths: $failures failed" >&2
  exit 1
fi
echo "test-paths: all passed"
