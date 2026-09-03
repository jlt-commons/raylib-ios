#!/bin/sh
# devices.sh: what deploy.sh could talk to.
#
# Two traps this script exists to sidestep.
#
# `xcrun devicectl list devices` CACHES AND LIES about state: it reported
# `disconnected` for a phone whose own tunnelState was `connected` seconds
# later. The authoritative probe is per device, so this asks each one rather
# than trusting the summary column.
#
# And it prints the CoreDevice identifier, which is not what anything wants.
# deploy.sh needs the HARDWARE udid (00008150-...), because that is what a
# provisioning profile lists under ProvisionedDevices; passing the CoreDevice
# identifier makes the device-coverage check fail on a profile that covers the
# phone perfectly well.
set -eu

echo "# simulators (booted)"
xcrun simctl list devices booted | grep -E 'Booted' || echo "  (none)"

echo "# devices (export UDID=<udid>, the hardware one)"
J=$(mktemp -t devices); trap 'rm -f "$J"' EXIT INT TERM
xcrun devicectl list devices -j "$J" >/dev/null 2>&1 || { echo "  (devicectl failed)"; exit 0; }
python3 - "$J" <<'PY'
import json, subprocess, sys
devs = json.load(open(sys.argv[1])).get('result', {}).get('devices', [])
if not devs:
    print("  (none paired)")
for d in devs:
    hw   = d.get('hardwareProperties', {})
    name = d.get('deviceProperties', {}).get('name', '?')
    udid = hw.get('udid', '?')
    # ask the device itself; the JSON's connectionProperties.tunnelState is the
    # cached answer and has been wrong in this project already
    out = subprocess.run(['xcrun', 'devicectl', 'device', 'info', 'details',
                          '--device', d.get('identifier', '')],
                         capture_output=True, text=True).stdout
    live = next((l.split(':', 1)[1].strip() for l in out.splitlines() if 'tunnelState' in l), 'unreachable')
    print(f"  {name:14s} {udid}  {hw.get('marketingName','?')}  iOS {d.get('deviceProperties',{}).get('osVersionNumber','?')}  tunnelState: {live}")
PY
