#!/bin/sh
# live.sh: the gallery with an nREPL, on the phone.
#
#   UDID=<hardware udid> sh tools/ios/live.sh
#   UDID=... NS=my.app.live sh /path/to/raylib-ios/tools/ios/live.sh   # from another project
#
# Builds NS (default net.b12n.raylib-ios.live, the gallery), signs, installs and
# launches it detached, then tells you how to reach the REPL. jolt.nrepl binds
# loopback only, so the port has to be forwarded over USB with iproxy
# (`jolt proxy` in another terminal).
#
# NS is how a project that shows one scene builds its live variant: an
# entry namespace whose -main calls net.b12n.raylib-ios.runner.live/live-run!.
# PROJECT_DIR (default: the current directory) is the jolt project to build and
# where RaylibIOS.app lands, as in build.sh.
set -eu

# This repo's tools directory, found from the script and not from $PWD.
TOOLS_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_DIR=${PROJECT_DIR:-$PWD}
export PROJECT_DIR

: "${UDID:?set UDID to the phone hardware udid, from: jolt devices}"
DEVICE_PORT=${DEVICE_PORT:-7888}
LOCAL_PORT=${LOCAL_PORT:-7888}

# CIDER=1 swaps in the middleware build, which needs the :cider alias because
# that is where the dependency lives. The default has none.
if [ "${CIDER:-0}" = 1 ]; then
  NS=${NS:-net.b12n.raylib-ios.live-cider}
  ALIAS=${ALIAS:-:cider}
else
  NS=${NS:-net.b12n.raylib-ios.live}
  ALIAS=${ALIAS:-}
fi
NS="$NS" ALIAS="$ALIAS" TARGET=device sh "$TOOLS_DIR/build.sh"
DEVICE_PORT="$DEVICE_PORT" CONSOLE=${CONSOLE:-0} sh "$TOOLS_DIR/deploy.sh"

cat <<MSG

live: $NS is running with an nREPL on the phone's 127.0.0.1:$DEVICE_PORT.

  1. in another terminal:   UDID=$UDID LOCAL_PORT=$LOCAL_PORT jolt proxy
  2. prove it is the phone: $TOOLS_DIR/nrepl-eval $LOCAL_PORT '(System/getenv "HOME")'
     an iOS sandbox answers /private/var/mobile/Containers/Data/Application/...
     anything else means you are talking to a process on this Mac
  3. a REPL prompt:         $TOOLS_DIR/nrepl-repl 127.0.0.1 $LOCAL_PORT

Reads are free:

  $TOOLS_DIR/nrepl-eval $LOCAL_PORT '(do (require (quote [net.b12n.raylib-ios.host])) (pr-str (net.b12n.raylib-ios.host/state)))'

Anything touching raylib or SDL must go through on-next-frame!, because an
eval runs on the nREPL thread while the toolkit is main-thread-affine:

  $TOOLS_DIR/nrepl-eval $LOCAL_PORT '(do (require (quote [net.b12n.raylib-ios.host])) (net.b12n.raylib-ios.host/on-next-frame! (fn [] (net.b12n.raylib-ios.host/set-target-fps 30))))'

Both ports override: DEVICE_PORT and LOCAL_PORT.

Built-in ops here: clone, describe, eval, load-file, close. For an editor,
CIDER=1 jolt live adds completions, info, eldoc, the namespace browser,
macroexpansion, apropos and the test ops.
MSG
