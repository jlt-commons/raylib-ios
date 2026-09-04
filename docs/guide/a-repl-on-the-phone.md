# A REPL on the phone

Most of the measurements in
[performance on a phone](performance-on-a-phone.html) were taken by asking a
running app on a physical device what it was doing, from an editor on a laptop,
without rebuilding between readings. That changed how the work went enough to be
worth its own page.

## Getting one

```sh
UDID=<hardware udid> sh tools/ios/live.sh    # build with an nREPL, install, launch
UDID=<hardware udid> sh tools/ios/proxy.sh   # forward its port over USB
```

`live.sh` builds `raylib.live` rather than `raylib.gallery`: the same app plus
`jolt.nrepl`. The listener binds loopback only, so `proxy.sh` runs `iproxy` to
forward the phone's port to the laptop. That needs the cable, since `iproxy`
speaks usbmuxd and a Wi-Fi pairing does not answer it.

Then anything that speaks nREPL:

```sh
tools/ios/nrepl-eval 7888 '(+ 1 2)'
```

## Prove it is the phone before you trust a word of it

```clojure
(System/getenv "HOME")
;; /private/var/mobile/Containers/Data/Application/E1F53813-...
```

An iOS sandbox answers with that path. A JVM on the laptop answers
`/Users/you`, and it answers instantly, and every subsequent reading looks
entirely plausible.

This is not hypothetical. An early session in this project reported an nREPL
working on the phone when it was a stray JVM on the Mac holding the same port,
and several minutes of "results" came from the wrong process. `proxy.sh` now
refuses to bind beside an existing listener for exactly that reason, and prints
the check above when it starts.

## The two seams

The app is parked inside `SDL_UIKitRunApp` with the whole raylib loop on thread
0, and raylib and SDL are both main-thread-affine. An nREPL eval arrives on
jolt.nrepl's accept thread. Calling `DrawCircle` from there is a race waiting to
crash rather than a working REPL, and the scene's state is threaded through the
loop, so there is nothing global for an editor to look at either.

Two small things fix both.

**`raylib.host/state`** is refreshed every frame, so an eval can read what the
scene currently holds:

```clojure
(let [g (:gstate @raylib.host/current-state)]
  {:scene (:active-scene-id g)
   :live  (count (get-in g [:scene-state :live]))})
;; {:scene :life, :live 1026}
```

**`raylib.host/on-next-frame!`** queues a thunk the loop runs on the main
thread, between `BeginDrawing` and the scene's own frame function, which is the
only safe place to touch raylib from outside.

The drain uses `swap-vals!` rather than deref-then-reset. A thunk posted between
a read and a clear would be captured by neither and vanish with no error and no
log line, which is the kind of bug that gets blamed on the phone.

## Driving the UI without a finger

`raylib.gallery/tap!` queues a synthetic tap at a point in screen pixels. The
next frame sees a press there and the frame after sees the release, which is the
edge the gallery opens a card on.

```clojure
(raylib.gallery/tap! 316 901)     ; a category card
(raylib.gallery/tap! 316 2201)    ; a scene inside it
```

Ask the running layout where a card is rather than hardcoding pixels. Card
positions move whenever a category gains or loses a scene, and a stale
coordinate does not fail: it taps empty space or the wrong card, and whatever
you were measuring quietly measures something else. Adding two scenes once moved
five of them.

That seam is also what makes unattended capture possible: every screenshot and
GIF in these docs was taken by a script that navigated the app this way.

## Reading the frame rate

`raylib.probe/fps-every-frame?` turns on a per-frame `GetFPS` call and parks the
answer in `last-fps`, so the frame rate becomes a value to read rather than a
console line to scrape:

```clojure
(reset! raylib.probe/fps-every-frame? true)
@raylib.probe/last-fps
;; 58
```

It is off by default, and the reason is in
[performance on a phone](performance-on-a-phone.html): `GetFPS` is a sampler
that advances a ring on every call, so turning this on changes what a scene
drawing its own fps reports.

## The trap that makes redefinition lie

A release build inlines across call sites. Redefining a var updates what the
REPL sees while the running loop keeps calling the original, so an edit appears
to take and nothing changes on screen. Worse, the value you read back afterwards
is the new one, which is a very convincing wrong answer.

Build `DEV_BUILD=1` when you intend redefinition to take effect:

```sh
DEV_BUILD=1 UDID=<udid> sh tools/ios/live.sh
```

The cost is negligible. A `--dev` build held a mean frame time of 17.05 ms
against release's 17.02.

What makes this genuinely dangerous is that it is selective, so some of what you
redefine really does take effect and you learn to trust the session. A
`defmethod` works on a release build, because dispatch is a lookup in a table at
call time and a new method replaces the entry. A `def` holding a literal does
not, because it was folded into its callers when they were compiled.

Sitting next to each other in one session, that asymmetry is very hard to see:

```clojure
;; this took: the scene visibly changed, and the frame rate with it
(defmethod draw-scene! :bullets [_ {:keys [bullets]} {:keys [m]}] ...)

;; this did not, on a release build
(in-ns 'raylib.scenes.bullets)
(def speed 8.0)

;; and here is the convincing wrong answer
raylib.scenes.bullets/speed                        ;=> 8.0
(mapv :vx (raylib.scenes.bullets/emit origin 0.0)) ;=> [4.0 -2.0 -2.0]
```

The var says 8.0. `emit` still uses 4.0. A sweep over four values of `speed`
built on that read measured the same unchanged scene four times and produced a
flat line, which looked like a real result rather than a broken probe. The tell
was reading back a value the var should have determined, rather than the var
itself: check the effect, never the definition.

## What it is not

This is a development tool and not a shipping feature. App Store rule 2.5.2
bans executing downloaded code, and a build made without `live.sh` carries no
listener at all. The dependency is scoped to an alias for the same reason: the
default build has none.
