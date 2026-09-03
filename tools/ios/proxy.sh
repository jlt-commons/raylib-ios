#!/bin/sh
# proxy.sh: forward the phone's nREPL port to this machine, over USB.
#
#   UDID=<hardware udid> sh tools/ios/proxy.sh
#   UDID=... LOCAL_PORT=17888 DEVICE_PORT=7888 sh tools/ios/proxy.sh
#
# jolt.nrepl binds loopback only, so the phone's listener is unreachable
# without this. iproxy speaks usbmuxd, which needs the CABLE: a phone paired
# to Xcode over Wi-Fi answers devicectl happily and iproxy not at all.
set -eu

: "${UDID:?set UDID to the phone hardware udid, from: jolt devices}"
LOCAL_PORT=${LOCAL_PORT:-7888}
DEVICE_PORT=${DEVICE_PORT:-7888}

# REFUSE to forward onto a port something already holds, rather than binding
# beside it and hoping.
#
# This is not hypothetical. A stray JVM nREPL held 127.0.0.1:7889 while iproxy
# bound the IPv6 wildcard *:7889; connections to 127.0.0.1 went to the JVM, and
# an eval answered with plausible values (os.name "Mac OS X", an aarch64 arch)
# that were simply the Mac's. Nothing failed, and the wrong answers looked
# exactly like right ones. The only tell was asking for something only a phone
# has, which is why the check below exists and why `jolt live` prints a HOME
# probe as its first suggestion.
HOLDER=$(lsof -nP -iTCP:"$LOCAL_PORT" -sTCP:LISTEN 2>/dev/null | tail -n +2 || true)
if [ -n "$HOLDER" ]; then
  echo "proxy.sh: something is already listening on $LOCAL_PORT:" >&2
  echo "$HOLDER" | sed 's/^/  /' >&2
  echo "proxy.sh: forwarding onto it would send your evals to THAT process, and" >&2
  echo "proxy.sh: its answers would look perfectly reasonable. Pick another:" >&2
  echo "proxy.sh:   UDID=$UDID LOCAL_PORT=17888 sh tools/ios/proxy.sh" >&2
  exit 2
fi

echo "proxy.sh: localhost:$LOCAL_PORT -> phone 127.0.0.1:$DEVICE_PORT (ctrl-c to stop)"
echo "proxy.sh: prove it is the phone:  tools/ios/nrepl-eval $LOCAL_PORT '(System/getenv \"HOME\")'"
echo "proxy.sh: an iOS sandbox answers /private/var/mobile/Containers/Data/Application/..."
exec iproxy -u "$UDID" "$LOCAL_PORT:$DEVICE_PORT"
