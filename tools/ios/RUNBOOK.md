# RUNBOOK

Operating this project: first-time setup, the daily loop, live development, and
the failures you will actually hit. Everything here was run before it was
written down.

## First time

```sh
jolt test                     # 19 tests, 89 assertions, no device needed
SDK=device sh tools/ios/deps.sh   # SDL 2.32.10 + raylib 6.0, static, a few minutes
jolt devices                  # find the phone's HARDWARE udid
export UDID=00008150-...      # the 00008150-... one, not the CoreDevice identifier
```

`deps.sh` caches under `~/dev/{sdl2,raylib}-ios-dev` and is a no-op afterwards.
It verifies both archives are `platform 2` rather than trusting that the
sysroot flag went through.

You also need a target pack. `build.sh` borrows the sibling glimmer-ios
project's from `/tmp/glimmer-ios/pack/device` if one is there, and says so when
it does. A pack describes the Chez runtime and the SDK rather than an app, so
they are interchangeable. To build your own instead, which needs a ChezScheme
10.4.1 checkout and about twenty minutes, run `sh tools/ios/pack.sh`.

## The loop

```sh
NS=raylib.gallery TARGET=device jolt build-app
jolt deploy                              # signs, installs, launches, streams stdout
CONSOLE=0 jolt deploy                    # launches detached, for actually playing
```

Namespaces worth building: `raylib.link` (does it link at all), `raylib.touch`,
`raylib.flappy`, `raylib.gallery`, `raylib.live`.

## Live development

```sh
jolt live                                # builds raylib.live, deploys it detached
jolt proxy                               # in another terminal: forwards the port over USB
tools/ios/nrepl-eval 7888 '(System/getenv "HOME")'
tools/ios/nrepl-repl 127.0.0.1 7888      # or a prompt
```

Both ports override: `DEVICE_PORT` is the phone's own loopback port and reaches
the app through devicectl's `DEVICECTL_CHILD_` mechanism, since an iOS app
inherits no shell environment. `LOCAL_PORT` is this machine's end of the
forward.

```sh
DEVICE_PORT=7999 LOCAL_PORT=17888 jolt live
UDID=$UDID DEVICE_PORT=7999 LOCAL_PORT=17888 jolt proxy
```

**Always prove you are talking to the phone before believing an answer.** Ask
for `HOME`: an iOS sandbox says
`/private/var/mobile/Containers/Data/Application/...` and anything else means a
process on this Mac. See "An eval answered, and it was the wrong machine"
below, which is not a hypothetical.

An eval runs on jolt.nrepl's accept thread while the main thread is parked
inside `SDL_UIKitRunApp`, and raylib and SDL are main-thread-affine. So:

```clojure
;; safe: the loop refreshes this every frame
(do (require '[raylib.host]) (raylib.host/state))

;; safe: runs at the top of the next frame, on the main thread
(raylib.host/on-next-frame! (fn [] (raylib.host/set-target-fps 30)))

;; NOT safe: calls raylib from the nREPL thread
(raylib.host/set-target-fps 30)
```

An nREPL is a development feature only. App Store rule 2.5.2 forbids executing
downloaded code, so a shipped build carries no listener.

## When it goes wrong

**"the device was not, or could not be, unlocked"** (`FBSOpenApplicationErrorDomain
error 7`, wrapped in a useless outer `CoreDeviceError 10002`). Unlock the phone.
The install succeeds and only the launch fails, so a retry costs nothing.

**`jolt devices` says a live phone is unavailable.** `devicectl list devices`
caches and lies. `devices.sh` asks each device for its own `tunnelState`
instead, which is why it is a script and not a one-liner. If it still says
unavailable, the phone is asleep or the cable is out.

**The app shows a black window after you stop watching the console.** The
console is a leash. `--console` streams stdout, and ending that session signals
the app: SDL posts `SDL_QUIT`, raylib sets `shouldClose`, the loop closes the
window, and SDL's delegate deliberately does not exit. Use `CONSOLE=0`.

**A timed console run leaves a process behind.** `devicectl device process
launch --console` ignores `SIGTERM`, so `timeout N jolt deploy` returns while
devicectl keeps running. `pkill -f "devicectl device process launch"`.

**An eval answered, and it was the wrong machine.** A stray JVM nREPL held
`127.0.0.1:7889` while iproxy bound the IPv6 wildcard `*:7889`; connections to
loopback went to the JVM. The evals succeeded and returned entirely plausible
values, `os.name "Mac OS X"` and an `aarch64` arch, which were simply the Mac's.
Nothing failed and nothing warned. `proxy.sh` now refuses to bind beside an
existing listener and names it, but the habit that actually protects you is
asking for `HOME` first.

**`iproxy` cannot see the phone.** It speaks usbmuxd, which needs the cable. A
phone paired to Xcode over Wi-Fi answers `devicectl` happily and `iproxy` not at
all. Check with `idevice_id -l`: an empty list with a working `jolt devices`
means exactly this.

**The screen is black on the simulator.** Expected. The simulator has not
displayed OpenGL ES since iOS 17.5. Pixels reach the framebuffer, which
`glReadPixels` will confirm, and nothing is shown. Test on the phone.

**`GetFPS` returns something absurd.** It is a stateful sampler that must be
called every frame; see the README. The host computes its own rate now, and
scenes that draw it every frame are fine.

## Signing

`deploy.sh` finds a development profile covering the bundle id, validates it
(`get-task-allow`, expiry, and that it lists this phone) and signs with the
`Apple Development` identity. Override with `PROFILE=` or `SIGN_ID=` if you have
more than one. A profile that does not list the phone installs and then fails at
launch with a message about provisioning, which reads like a signing bug.
