#!/bin/sh
# pack.sh: build the two tpb64l target packs, one per SDK.
#
#   sh tools/ios/pack.sh
#
# Produces /tmp/raylib-ios/pack/{device,sim}. Roughly 20 minutes, mostly Chez.
# Everything lives under /tmp because it is cheap to rebuild and expensive to
# explain; nothing here is source.
set -eu

WORK=${WORK:-/tmp/raylib-ios}
CHEZ_WT=${CHEZ_WT:-/tmp/raylib-ios/chez}  # the v10.4.1 worktree from P0.T2
HOST_CHEZ=${HOST_CHEZ:-/opt/homebrew/bin/chez}
# ~/dev/jolt, the public checkout, NOT ~/dev/jolt-ios-spike. make-pack.sh is
# byte-identical on both branches and reads only from $CHEZ_SRC -- it copies
# ChezScheme's boots, xpatch, kernel and static libs into a directory and
# writes a link-libs line. Nothing from the jolt tree ends up in a pack, so
# the private spike fork buys this project nothing and would make it
# unbuildable for anyone without it.
JOLT_SRC=${JOLT_SRC:-$HOME/dev/jolt}   # for tools/cross-compile/make-pack.sh
FFI_VERSION=3.6.0

SDK_DEVICE=$(xcrun -sdk iphoneos --show-sdk-path)
SDK_SIM=$(xcrun -sdk iphonesimulator --show-sdk-path)
TARGET_DEVICE="-target arm64-apple-ios14.0"
TARGET_SIM="-target arm64-apple-ios-simulator"

die() { echo "pack.sh: $1" >&2; exit 1; }

[ -d "$CHEZ_WT" ] || die "no Chez worktree at $CHEZ_WT (see RUNBOOK, P0.T2)"
[ -x "$HOST_CHEZ" ] || die "no host Chez at $HOST_CHEZ"
case "$("$HOST_CHEZ" --version 2>&1)" in
  10.4.1) : ;;
  *) die "host Chez is $("$HOST_CHEZ" --version 2>&1), not 10.4.1. The pack's xpatch would not load" ;;
esac

mkdir -p "$WORK"

# ---------------------------------------------------------------- 1. libffi
# Bytecode has no native FFI trampolines, so Chez routes foreign calls through
# libffi. Without it the app dies at the first foreign call with
# "protocol not supported (libffi unavailable)", and every ObjC message send
# is a foreign call, so that means no UIKit at all.
#
# 3.6.0 specifically: 3.4.6 fails on current Xcode with
# "invalid CFI advance_loc expression" in its aarch64 assembly.
build_ffi() {                     # $1 = device|sim, $2 = sdk path, $3 = target triple flag
  name=$1; sdk=$2; triple=$3
  out="$WORK/ffi/$name"
  [ -f "$out/lib/libffi.a" ] && { echo "== libffi ($name) already built"; return 0; }
  echo "== libffi $FFI_VERSION for $name"
  mkdir -p "$WORK/ffi/src"
  cd "$WORK/ffi/src"
  [ -d "libffi-$FFI_VERSION" ] || {
    curl -sfL -o f.tgz "https://github.com/libffi/libffi/releases/download/v$FFI_VERSION/libffi-$FFI_VERSION.tar.gz"
    tar xzf f.tgz
  }
  # configure caches host detection, so each SDK gets a clean tree
  rm -rf "build-$name" && cp -R "libffi-$FFI_VERSION" "build-$name"
  cd "build-$name"
  ./configure --host=aarch64-apple-darwin --prefix="$out" \
    --enable-static --disable-shared --disable-docs \
    CC="clang $triple -isysroot $sdk" \
    CFLAGS="$triple -isysroot $sdk -O2" \
    LDFLAGS="$triple -isysroot $sdk" >/dev/null
  make -j8 >/dev/null && make install >/dev/null
}

build_ffi device "$SDK_DEVICE" "$TARGET_DEVICE"
build_ffi sim    "$SDK_SIM"    "$TARGET_SIM"

# ------------------------------------------------- 2. boots and the xpatch
# ONCE, not per-SDK. Boot files and the xpatch describe the bytecode machine
# (tpb64l: threaded, portable bytecode, 64-bit, little-endian) and say nothing
# about the OS, so both SDKs share them. Only the C kernel below is SDK-specific.
#
# --host-scheme compiles the cross compiler with an INSTALLED Scheme instead of
# an in-tree host build. --xpatch is the only thing that leaves xc-tpb64l/s/xpatch
# behind, and make-pack.sh requires it. Flags come BEFORE the machine name:
# parse-boot-args strips --xpatch, then expects --host-scheme at the head of
# what remains, and only then reads the machine.
if [ ! -f "$CHEZ_WT/xc-tpb64l/s/xpatch" ]; then
  echo "== boots + xpatch for tpb64l (once, shared by both SDKs)"
  cd "$CHEZ_WT"
  rm -rf boot/tpb64l xc-tpb64l                 # bootquick cycles if these exist
  ./configure --cross --force -m=tpb64l --disable-curses --disable-x11 \
    CFLAGS="$TARGET_DEVICE -isysroot $SDK_DEVICE -DTARGET_OS_IPHONE=1" \
    LDFLAGS="-liconv" CC_FOR_BUILD=clang >/dev/null
  make bin/zuo >/dev/null
  bin/zuo tpb64l bootquick --xpatch --host-scheme "$HOST_CHEZ" tpb64l
fi
[ -f "$CHEZ_WT/xc-tpb64l/s/xpatch" ] || die "bootquick produced no xpatch"

# -------------------------------------------------------------- 3. kernels
# Two builds differing ONLY in -target and -isysroot. They share one workarea
# ($CHEZ_WT/tpb64l), so each one's artifacts are copied out before the next
# configure clobbers them.
build_kernel() {                  # $1 = device|sim, $2 = sdk path, $3 = target triple flag
  name=$1; sdk=$2; triple=$3
  out="$WORK/kernel/$name"
  # Cache on the kernel AND on the archive being non-trivial. Keying on mere
  # presence turns one bad build into a permanent one: the file exists, every
  # re-run skips, and the failure repeats with no way to tell it is cached.
  # Delete "$WORK/kernel" to force a rebuild.
  if [ -f "$out/libkernel.a" ] && [ -s "$out/libkernel.a" ]; then
    echo "== kernel ($name) already built (rm -rf $WORK/kernel to force)"; return 0
  fi
  echo "== Chez tpb64l kernel for $name"
  cd "$CHEZ_WT"
  rm -rf tpb64l/c tpb64l/zlib tpb64l/lz4
  ./configure --cross --force -m=tpb64l --enable-libffi --disable-curses --disable-x11 \
    CFLAGS="$triple -isysroot $sdk -DTARGET_OS_IPHONE=1 -I$WORK/ffi/$name/include" \
    LDFLAGS="-L$WORK/ffi/$name/lib -liconv" CC_FOR_BUILD=clang >/dev/null
  make >/dev/null
  mkdir -p "$out"
  cp tpb64l/boot/tpb64l/libkernel.a "$out/"
  # lz4 and zlib do NOT live beside the kernel. These paths are make-pack.sh's own
  # (tools/cross-compile/make-pack.sh, the two `cp ... 2>/dev/null || true` lines),
  # which is the authority on where a cross build leaves them.
  # These two are copied only if the cross build produced them, which is what
  # make-pack.sh does. But do NOT swallow the reason: if they are missing, the
  # kernel is still written, the pack then fails to link, and every re-run
  # short-circuits on "already built" and fails identically forever with nothing
  # pointing at the cause. Say what happened.
  for lib in tpb64l/lz4/lib/liblz4.a tpb64l/zlib/libz.a; do
    if [ -f "$lib" ]; then cp "$lib" "$out/"
    else echo "   note: $lib absent, not copied (fine unless the link later wants it)"; fi
  done
}

build_kernel device "$SDK_DEVICE" "$TARGET_DEVICE"
build_kernel sim    "$SDK_SIM"    "$TARGET_SIM"

# ----------------------------------------------------------------- 4. packs
# make-pack.sh collects the csv, the xpatch and the static libs into one
# directory. It writes link-libs as "-llz4 -lz -lm" and knows nothing about
# iconv, libffi or frameworks, so those are added here.
# UIKit is here because SDL2's iOS video driver is UIKit, and Foundation
# because on iOS a bare executable does not get it and every class lookup fails
# with `no such Objective-C class: NSString`. The rest of the frameworks raylib
# and SDL need (QuartzCore, OpenGLES, Metal, GameController, ...) are added by
# tools/ios/build.sh, beside the two static archives, so the pack stays a
# description of the RUNTIME and not of this particular app.
LINK_LIBS='-llz4 -lz -lm -liconv -lffi -framework Foundation -framework UIKit'

build_pack() {                    # $1 = device|sim
  name=$1
  out="$WORK/pack/$name"
  echo "== target pack ($name)"
  rm -rf "$out"
  ( cd "$JOLT_SRC" && CHEZ_SRC="$CHEZ_WT" tools/cross-compile/make-pack.sh tpb64l "$out" )
  # make-pack.sh copies whatever kernel the workarea happens to hold, which is
  # the LAST one built. Overwrite it with this SDK's, explicitly.
  cp "$WORK/kernel/$name/libkernel.a" "$out/"
  cp "$WORK/kernel/$name/liblz4.a" "$WORK/kernel/$name/libz.a" "$out/lib/"
  cp "$WORK/ffi/$name/lib/libffi.a" "$out/lib/"
  # ONE LINE, NO TRAILING NEWLINE. printf, never echo.
  printf -- '%s' "$LINK_LIBS" > "$out/link-libs"
}

build_pack device
build_pack sim

# ------------------------------------------------------- 5. verify the packs
# make-pack.sh copies whichever kernel the Chez workarea currently holds, and
# build_pack overwrites it with the right one straight after. An interruption
# between the two leaves a pack that LOOKS complete and carries the other SDK's
# kernel. That happened twice during P1.T4, once with no triggering action, and
# nothing announced it. So the packs are checked rather than assumed.
verify_pack() {                   # $1 = device|sim, $2 = expected platform number
  name=$1; want=$2; p="$WORK/pack/$name"
  for f in libkernel.a link-libs petite.boot scheme.boot scheme.h xpatch \
           lib/libffi.a lib/liblz4.a lib/libz.a; do
    [ -f "$p/$f" ] || die "pack/$name is missing $f"
  done
  cmp -s "$p/libkernel.a" "$WORK/kernel/$name/libkernel.a" \
    || die "pack/$name carries the WRONG kernel (re-run this script to repair)"
  [ "$(tail -c1 "$p/link-libs" | xxd -p)" != "0a" ] \
    || die "pack/$name link-libs has a trailing newline; the link would fail with exit 127"
  ( cd "$(mktemp -d)" && ar x "$p/libkernel.a" alloc.o \
      && otool -l alloc.o | grep -A2 LC_BUILD_VERSION | grep -q "platform $want" ) \
    || die "pack/$name kernel is not platform $want"
}

verify_pack device 2
verify_pack sim 7
cmp -s "$WORK/pack/device/libkernel.a" "$WORK/pack/sim/libkernel.a" \
  && die "both packs carry the SAME kernel"
echo "packs verified: $WORK/pack/device  $WORK/pack/sim"
