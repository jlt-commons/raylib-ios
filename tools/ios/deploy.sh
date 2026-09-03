#!/bin/sh
# deploy.sh: install and launch RaylibIOS.app.
#
#   TARGET=sim    sh tools/ios/deploy.sh
#   TARGET=device sh tools/ios/deploy.sh
#
# Build first: NS=<ns> TARGET=<t> sh tools/ios/build.sh
set -eu

TARGET=${TARGET:-device}
APP=${APP:-RaylibIOS.app}
BUNDLE_ID=$(plutil -extract CFBundleIdentifier raw "$APP/Info.plist")

# Validate, do not just test for "sim". An unrecognised value used to fall
# through to the DEVICE branch, so `TARGET=simulator` (or `Sim`, or a trailing
# space) would sign with a real identity and push to the phone, exit 0, and look
# exactly like an intended device deploy. build.sh validates the same variable.
case "$TARGET" in
  sim|device) ;;
  *) echo "deploy.sh: TARGET must be sim or device, got '$TARGET'" >&2; exit 2 ;;
esac

if [ "$TARGET" = sim ]; then
  xcrun simctl install booted "$APP"
  xcrun simctl launch --console booted "$BUNDLE_ID"
  exit 0
fi

# ---- device: sign with the minted identity and a profile covering this phone
UDID=${UDID:?set UDID to the phone HARDWARE udid, from: xcrun devicectl list devices -j}
IDENTITY=${SIGN_ID:-$(security find-identity -v -p codesigning | grep -oE '"Apple Development: [^"]+"' | head -1 | tr -d '"')}
[ -n "$IDENTITY" ] || { echo "deploy.sh: no Apple Development identity" >&2; exit 2; }

# Scratch space for the decoded profile. It used to be three fixed /tmp paths,
# world-readable, never cleaned up, holding the team identifier and the full
# ProvisionedDevices list. One of them is an INPUT TO CODE SIGNING, so two
# deploys running at once could sign one app with the other's entitlements, both
# succeeding silently. mktemp -d gives a fresh unpredictable directory, umask
# keeps it to this user, and the trap removes it however the script exits.
umask 077
T=$(mktemp -d "${TMPDIR:-/tmp}/raylib-deploy.XXXXXX")
trap 'rm -rf "$T"' EXIT INT TERM

# A glob, not $(ls ...): the Xcode directory name has a space in it and word
# splitting would cut every path in two.
PROFILE=${PROFILE:-}
if [ -z "$PROFILE" ]; then
  for p in "$HOME/Library/Developer/Xcode/UserData/Provisioning Profiles"/*.mobileprovision \
           "$HOME/.config/appstore-connect"/*.mobileprovision; do
    [ -f "$p" ] || continue
    security cms -D -i "$p" > "$T/prof-scan.plist" 2>/dev/null || continue
    appid=$(plutil -extract Entitlements.application-identifier raw "$T/prof-scan.plist" 2>/dev/null || true)
    gta=$(plutil -extract Entitlements.get-task-allow raw "$T/prof-scan.plist" 2>/dev/null || true)
    case "$appid" in
      *".$BUNDLE_ID"|*".*")
        if [ "$gta" = true ] && { [ -z "$PROFILE" ] || [ "$p" -nt "$PROFILE" ]; }; then PROFILE="$p"; fi ;;
    esac
  done
fi
[ -n "$PROFILE" ] || { echo "deploy.sh: no development profile covering $BUNDLE_ID" >&2; exit 2; }

cp "$PROFILE" "$APP/embedded.mobileprovision"
# Decode the MATCHED profile again. The scan loop above left whichever profile it
# looked at LAST in the scratch file, which is not the one it matched, the demo
# shipped exactly this bug and signed with an App Store profile's entitlements.
security cms -D -i "$PROFILE" > "$T/prof.plist"
plutil -extract Entitlements xml1 -o "$T/ent.plist" "$T/prof.plist"

# Validate the profile we are ACTUALLY going to sign with, whether the scan
# chose it or PROFILE was set by hand. Setting it by hand used to skip every
# check the scan does, and the RUNBOOK tells you to set it by hand. Nothing
# checked expiry or device coverage on either path, though the comment above
# says "covering this phone".
prof_gta=$(plutil -extract Entitlements.get-task-allow raw "$T/prof.plist" 2>/dev/null || echo false)
[ "$prof_gta" = true ] || { echo "deploy.sh: $PROFILE is not a development profile (get-task-allow is not true). Signing with it would install but not debug." >&2; exit 2; }

prof_exp=$(plutil -extract ExpirationDate raw "$T/prof.plist" 2>/dev/null || true)
if [ -n "$prof_exp" ]; then
  exp_s=$(date -j -f "%Y-%m-%dT%H:%M:%SZ" "$prof_exp" +%s 2>/dev/null || echo "")
  if [ -n "$exp_s" ] && [ "$exp_s" -lt "$(date +%s)" ]; then
    echo "deploy.sh: $PROFILE expired on $prof_exp" >&2; exit 2
  fi
fi

# A profile that does not list this phone installs and then fails at launch with
# a message about the provisioning profile, which reads like a signing bug.
if plutil -extract ProvisionedDevices xml1 -o - "$T/prof.plist" >/dev/null 2>&1; then
  plutil -extract ProvisionedDevices xml1 -o - "$T/prof.plist" 2>/dev/null | grep -q "$UDID" \
    || { echo "deploy.sh: $PROFILE does not list device $UDID. Add the phone to the profile and re-download it." >&2; exit 2; }
fi
# plutil splits key paths on dots unless they are escaped
TEAM=$(plutil -extract 'com\.apple\.developer\.team-identifier' raw "$T/ent.plist")
# The profile is a team wildcard, but the app must carry a CONCRETE app id the
# wildcard covers, or installd answers "A valid provisioning profile for this
# executable was not found".
plutil -replace application-identifier -string "$TEAM.$BUNDLE_ID" "$T/ent.plist"

codesign --force --sign "$IDENTITY" --entitlements "$T/ent.plist" --timestamp=none "$APP"
codesign --verify --verbose=2 "$APP"
xcrun devicectl device install app --device "$UDID" "$APP"
xcrun devicectl device process launch --console --terminate-existing --device "$UDID" "$BUNDLE_ID"
