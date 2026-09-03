#!/bin/sh
# live.sh: the gallery with an nREPL, on the phone.
#
#   UDID=<hardware udid> sh tools/ios/live.sh
#
# Builds raylib.live, signs, installs and launches it detached, then tells you
# how to reach the REPL. jolt.nrepl binds loopback only, so the port has to be
# forwarded over USB with iproxy (`jolt proxy` in another terminal).
set -eu

: "${UDID:?set UDID to the phone hardware udid, from: jolt devices}"
DEVICE_PORT=${DEVICE_PORT:-7888}
LOCAL_PORT=${LOCAL_PORT:-7888}

NS=raylib.live TARGET=device sh tools/ios/build.sh
DEVICE_PORT="$DEVICE_PORT" CONSOLE=${CONSOLE:-0} sh tools/ios/deploy.sh

cat <<MSG

live: the gallery is running with an nREPL on the phone's 127.0.0.1:$DEVICE_PORT.

  1. in another terminal:   UDID=$UDID LOCAL_PORT=$LOCAL_PORT jolt proxy
  2. prove it is the phone: tools/ios/nrepl-eval $LOCAL_PORT '(System/getenv "HOME")'
     an iOS sandbox answers /private/var/mobile/Containers/Data/Application/...
     anything else means you are talking to a process on this Mac
  3. a REPL prompt:         tools/ios/nrepl-repl 127.0.0.1 $LOCAL_PORT

Reads are free:

  tools/ios/nrepl-eval $LOCAL_PORT '(do (require (quote [raylib.host])) (pr-str (raylib.host/state)))'

Anything touching raylib or SDL must go through on-next-frame!, because an
eval runs on the nREPL thread while the toolkit is main-thread-affine:

  tools/ios/nrepl-eval $LOCAL_PORT '(do (require (quote [raylib.host])) (raylib.host/on-next-frame! (fn [] (raylib.host/set-target-fps 30))))'

Both ports override: DEVICE_PORT and LOCAL_PORT.
MSG
