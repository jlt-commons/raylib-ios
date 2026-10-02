# Porting an example from raylib-jlt

[jlt-commons/raylib-jlt](https://github.com/jlt-commons/raylib-jlt) has 187
examples, and 92 of them are in the gallery. Those count
examples and not scenes, because the `easings` scene covers three of them and
the three Android scenes stand in for `flappy_bird`, `eyes` and `mouse_trail`.
The ones that need no input at all port almost mechanically. This is what "almost"
means, worked through with the first seven.

![Spirograph running on an iPhone 17 Pro](../images/spirograph.png)

*spirograph, the first port. Same maths as the original, a screen 1206x2622
instead of 800x450, and a loop it no longer owns.*

## They are desktop-shaped in two ways

A raylib-jlt example owns its loop:

```clojure
(defn -main [& _]
  (rl/window! :width 800 :height 450 :title "spirograph")
  (rl/set-target-fps 60)
  (loop [frame 0 st (new-params)]
    (when (rl/keep-running? deadline)
      ...
      (rl/begin-drawing) ... (rl/end-drawing)
      (recur (inc frame) st))))
  (rl/close-window))
```

and, in the ones that take input, reads the keyboard inline from inside its
model:

```clojure
(defn- step [s]
  (let [dx (cond (rl/key-pressed? rl/KEY-LEFT) -1 ...)]
    ...))
```

Neither survives on a phone. The host owns the loop here, and there is no
keyboard. The six namespaces this project carries from the Android experiment
came across byte-identical precisely because they were written the other way
round, pure and touch-first, with input arriving as a data snapshot.

## The four changes

**1. Become a reducer over frames.** The scene contract in
`poc.raylib.gallery` is `{:id :title :init :update :draw :dispose}`, where
`update` takes state and an input snapshot and returns the next state. So the
body of the original's `loop` becomes `advance`, and `-main` disappears.

**2. Derive geometry from the live screen.** The originals draw at a fixed
800x450 with constants to match: a ring at radius 170 about (400, 225). A phone
is 1206x2622. Give the namespace a `dimensions` function taking the metrics, the
way `poc.raylib.flappy-bird` does, and scale everything off the smaller
dimension so a tall phone and a wide desktop both get something that fits.

**3. Replace `GetRandomValue` with a seeded LCG.** Not for purity as an
aesthetic, but because it makes the scene runnable and testable on a build host
with no raylib, no SDL and no device, and reproducible from a seed. The
constants are `poc.raylib.flappy-bird`'s, so a seed means the same thing
everywhere:

```clojure
(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))
```

**4. Leave drawing to the host.** The pure namespace computes; a
`draw-scene!` method in `raylib.gallery` draws. Colours come back as
`[r g b a]` and the host packs them, so no raylib type reaches the scene.

## What it costs in bindings

Almost nothing, which was the surprise. Across seven ports:

| example | new bindings needed |
| --- | --- |
| spirograph | none |
| kaleidoscope | none |
| fireworks | none |
| boids | none |
| penrose | four: `rlBegin`, `rlEnd`, `rlVertex2f`, `rlColor4ub` |
| double-pendulum | none |
| fourier-epicycles | none |

`DrawLine`, `DrawCircle`, `DrawText`, `DrawRectangle` and `MeasureText` cover
most of the collection. Of eight further candidates surveyed, seven need
nothing new at all; only `analog_clock` does, wanting `DrawLineEx`, `DrawRing`
and a local-time call. Penrose needed rlgl immediate mode only because it fills
polygons and raylib's shapes API has no call for that.

A pure scene can't call `MeasureText`, so it takes a `measure` function. Every
scene's input carries one as `:measure`, which is raylib's own text width. A
scene that keeps text widths in its state, as `strings` does, reads it in `init`
and `update`, with a fallback so tests can run without the FFI. A scene that
lays text out only in `dimensions`, as `rectbounds` does, still gets `measure`
from its draw method.

An example that sets a `Camera2D` draws inside `raylib.host/with-camera-2d`,
which pushes `rlTranslatef`, `rlRotatef` and `rlScalef` on top of the gallery's
own translate, because `BeginMode2D` would load the identity matrix and drop it.
The pure part is `raylib.camera2d`, which also holds the pinch rule shared by
the three scenes that zoom. An example that reads gestures finds raylib's own
code in the scene input as `:raylib-gesture`. On the phone that code fires for
one finger only, so pinch cannot appear.

<img src="../images/kaleidoscope.gif" width="220" alt="Kaleidoscope">
<img src="../images/spirograph.gif" width="220" alt="Spirograph">
<img src="../images/boids.gif" width="220" alt="Boids">
<img src="../images/fireworks.gif" width="220" alt="Fireworks">

*Four of the seven ports, running on the phone. None of them needed a single
new raylib binding.*

## Then measure it, because the port is the easy half

Two of the first five did not hold 60 fps on first run, and neither for the reason
anyone would guess. Read
[performance-on-a-phone.md](performance-on-a-phone.md) before tuning anything:
the short version is that an indexed `loop` over a vector beats every sequence
function, allocation costs more than the FFI call it decorates, and the fix is
usually in the drawing loop rather than the model.

Give anything that scales with frame cost a plain `def` rather than a literal,
so it can be tuned live over the nREPL: `trail-length`, `max-points`,
`default-deflations`, `default-count` all exist for that reason.

## Sometimes the port is a rotation

`fourier_epicycles` is the one that needed a real design decision rather than a
mechanical change. The original is landscape: the epicycle chain sits on the
left and the wave it traces scrolls rightward across the remaining width. A
phone is 1206 wide and 2622 tall, so there is no horizontal room for a
scrolling wave and a great deal of vertical.

So the chain hangs near the top and the wave scrolls DOWN. It is the same
picture through ninety degrees, and the maths is untouched: what changed is
which axis carries time, and therefore which coordinate of the pen the trace
records. Worth expecting one of these per handful of ports.

## Sometimes the phone can do what the original could not

Most ports lose something to the smaller screen. `multitouch` is the one that
gains, and it is worth knowing the shape of that case because it is easy to port
the limitation along with the code.

The desktop original says plainly what it cannot do. `GetTouchPosition` returns
a Vector2 by value, the desktop binding set had no path for that, so it reads
point zero through the scalar `GetTouchX` and `GetTouchY` pair. Its own docstring
calls this the honest limit: the ids of every point visible, the coordinates of
only the first. Transcribing it faithfully would have reproduced a workaround for
a problem this project does not have.

So read the original's docstrings for what they concede, not only for what they
describe.

## Reading a finger

Touch arrives as a point and a phase, and a few rules keep a scene honest on
a real phone.

- Only `:press` and `:down` with a non-nil point mean a finger is on the glass.
- Never read the position on `:release`. raylib keeps the last hardware value,
  and on the device it isn't the touch that just ended. The comment in
  `raylib.gallery/frame` has the details, and `touchball` stays put when the
  finger lifts for that reason.
- A button fires on `:press` inside its rect, as the two in `rlgltriangle` do
  where the original read keys.
- A drag grab is sticky: the finger that lands on a handle keeps it until the
  release, even when it slides off. `resize` and `rlgltriangle` both work this
  way.
- Keep buttons out of the top-left Back region, `[0 0 400 120]`, which the
  gallery owns.
- A scene that keeps absolute positions has to cope with `:screen` changing on
  rotation. `rlgltriangle` clamps its corners into the new screen and `breakout`
  starts a new game.

`raylib.gesture` does the reading for scenes that need to know what a touch
meant. `track` takes a gesture value and the frame's input, which carries the pointer
and the metrics, and returns
`[g' event]` once per frame. The event is a tap at the gesture's start point, a
swipe by the dominant axis from the start to the last `:down` point (a tie goes
horizontal), or a long press after 27 still frames. A long press that has fired
suppresses the tap, but a drag after it still swipes. The release position is
never read, for the reason above. Boards and controls start below Back, derived
from `gesture/back-region`. `snake`, `game2048` and `minesweeper` show all three
events in use. A game that ends on a tick stores `gesture/idle` on the frame it
ends, so a touch still down then can't restart it on lift, as `tetris` does.

## What only a real device will tell you

The multi-finger path cannot be exercised from a REPL. `tap!` synthesises exactly
one point by construction, so every synthetic test passes on a code path that
has never seen two fingers. It took a person putting four on the glass.

That found a bug no amount of local testing would have. Colours were keyed off
the raylib touch id, `(mod id 8)` into an eight-entry palette. iOS derives those
ids from object pointers, so every one is 8-byte aligned. Two separate runs
reported:

```
809313472  809313920  809314368  809317952     stride 448
809133248  809134144  809136832  809137728     stride 896
163292352  163292800  163294592                after a relaunch
```

Every value divisible by 8, so all four fingers landed on slot 0 and drew in the
same blue. Shifting the alignment away does not help, because the strides are
themselves multiples of 8.

The third run is the useful one for deciding what to rely on. It sits in a
different address range because the app had restarted, so neither the magnitude
nor the spacing survives a relaunch. The alignment is the only invariant, and it
is the one that broke things.

The instructive part is what happened next. The obvious fix is a better hash, and
a tuned one scored 100% on synthetic strides, which turned out to measure the
regularity of the test inputs rather than the quality of the hash. A proper
murmur3 finalizer then scored a flat 41% at every stride, and that number is the
answer: 8/8 x 7/8 x 6/8 x 5/8 is 41%, the chance four items land in four
different buckets out of eight. The hash was already ideal and still collided
most of the time, because with four fingers and eight colours collisions are the
birthday problem, not a hashing defect.

Assigning the lowest unused slot is exact for up to eight simultaneous touches.
No hash needed. When a measurement comes out at exactly the theoretical value,
that usually means the approach is finished rather than that the tuning is.

## Wiring it in

Three edits, all in `raylib.gallery`:

```clojure
(:require ... [raylib.scenes.spirograph :as spiro])
(def scenes [... (spiro/scene)])
{:id :generative :title "Generative" :scenes [:spirograph ...]}
```

plus a `draw-scene!` method. Then a test namespace beside the others, since the
scene is pure and there is no excuse not to.

Under jolt, `jolt -M:test` also runs a smoke test that fails if a scene is
missing from any of the four registration points: the `:require`, the `scenes`
vector, a category and the `draw-scene!` method.
