# The safe area, and how scenes came to respect it

An iPhone's screen is not all usable. A band at the top holds the status bar and
the Dynamic Island, and one at the bottom holds the home indicator. On the phone
this project was built against those are 62 and 34 points, which at a scale of
three is **186 and 102 pixels** of a 2622-pixel display.

For a long time every full-screen scene drew straight through both of them.
Conway's Life is what made it obvious: live cells running behind the clock and
the battery.

## The insets were never the hard part

`raylib.host/safe-area-insets` had been there since the first week:

```clojure
(raylib.host/safe-area-insets)
;; {:top 62.0, :left 0.0, :bottom 34.0, :right 0.0}
```

Four Objective-C message sends, because there is no shorter path to them: the
shared application, its windows, the first of those, and its insets. What was
missing was not the number. Only `:top` was being used, and only to place the
gallery's own cards and Back button. Scenes were handed the raw screen.

**Ask from inside the loop, not before it.** UIKit computes insets during
layout, so a call made before the first frame returns zeros, and zeros are a
perfectly plausible answer on a device that genuinely has no notch. The gallery
asks each frame until it gets a non-zero answer and then keeps it, which is not
an optimisation: it is four message sends per frame for a number that never
changes while the app is upright.

SDL cannot help here, incidentally. `SDL_GetDisplayUsableBounds` looks like
exactly the right call and returns `uiscreen.bounds`, which knows nothing about
safe areas. Measured on a phone whose real inset is 62 points, SDL reported 0.

## The fix, without touching every draw method

The obvious approach is to pass the insets to every scene and have each one
offset its own drawing. That was nineteen edits when this was written and is
thirty-four now, which is the argument rather than a footnote to it: the count
only ever grows, every one is a chance to get an offset wrong, and every future
scene inherits the obligation.

rlgl already has the machinery. raylib's 2D shape calls go through its immediate
mode, so the current MODELVIEW matrix applies to them:

```clojure
(rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe))
(rl/rl-push-matrix)
(rl/rl-translatef (float (:x safe)) (float (:y safe)) 0.0)
(draw-scene! id state {:k k :m m})
(rl/rl-pop-matrix)
(rl/end-scissor-mode)
```

A scene is handed metrics whose `:screen` **is** the safe region, so it computes
its geometry for the space it will actually get, and the host translates it into
place. No scene knows a safe area exists, and none of them changed.

**Scissor as well as translate.** Translation alone moves a well-behaved scene
into the right place and does nothing about one that overshoots its own bounds,
which would then paint over the status bar it was just moved clear of.

One consequence worth knowing: `ClearBackground` is affected by the scissor
test, because it becomes a `glClear` and `glClear` respects it. So a scene
clearing to its own background colour clears only the safe region, and the bands
outside it keep whatever was drawn there last. That is not obvious from the name.

It is also not the behaviour you want, which took a while to notice. An earlier
version of this page said it was. For a scene opened from the gallery the bands
held the card grid, and in portrait they are thin strips mostly behind the
status bar, so nobody looked. Rotating the phone to landscape moves the insets
to the sides, 186 pixels of stale cards down each edge, and it is the first
thing you see. The host clears the whole screen before raising the scissor now.

**A scene that clips for itself has to intersect, not replace.** rlgl keeps one
scissor rectangle. `BeginScissorMode` inside another does not nest, it takes
over, so a scene clipping to a box of its own would be free to paint over the
status bar the host had just moved it clear of. The host passes each scene its
safe region for exactly this, and `clipbox/clip-rect` shows the shape of the
fix: offset the scene-space box into screen coordinates, intersect with the
region, and return nil when they miss. nil rather than a zero or negative
width, because raylib takes the width as an int and a negative one reads as an
enormous unsigned value, so a degenerate box clips to everything instead of
nothing, which looks exactly like the scissor being ignored.

Worth noting what rotation did get right on its own. The insets come from UIKit
each frame rather than being cached from launch, so landscape reports left and
right 186 with top 0, every scene re-derives its geometry from the new safe
region, and nothing else had to change.

**The pointer has to make the same journey.** Moving the drawing is only half
of it. A scene told its screen is the safe region believes its own y of 0 is the
top of that region, but the touch coordinates raylib reports are still measured
from the top of the physical screen. Leave them alone and the two disagree by
exactly the inset: a tap at screen y 1500 arrives at a scene that thinks its
screen starts at 0, so whatever it draws under the finger appears 186 pixels
below it.

So the input gets the mirror of what the layout gets. `below-the-safe-area`
moves rectangles the host computed down into the region, and `into-safe-region`
moves a point the hardware reported up out of it:

```clojure
(defn- into-safe-region [input {:keys [x y]}]
  (if-let [[px py] (get-in input [:pointer :position])]
    (assoc-in input [:pointer :position] [(- px x) (- py y)])
    input))
```

This was a regression introduced by the safe-area fix itself, which is worth
being precise about because the comfortable version of the story is wrong.

`touch-trail` and `following-eyes` have read the pointer since the first commit
in this repo. Moving scene drawing into the safe region without moving the
pointer with it broke both of them, and they stayed broken for nine commits on
the same day before three new touch scenes made it obvious. Nobody re-ran the
two scenes that already depended on the thing being changed.

The gallery's own hit-testing was never affected, which is why the app kept
feeling fine. It compares a screen point against a layout already moved into
screen coordinates, so both sides were wrong together and cancelled out.

A fix that moves a coordinate system has to move everything expressed in it. The
tell here was that the change touched output and left input alone, and there was
no test that put a finger somewhere and asserted what got drawn there.

## What it exposed

Sizing the automaton against the safe region immediately showed a second bug the
full screen had been hiding: it covered 39% of the display with white below it.

Cell width and row height had been the same number, and a cell wide enough to
draw quickly is also too wide to need many rows. They are chosen separately
now, 13 pixels wide and 21 tall, which fills 2310 of the 2334 available.

That is the general shape of this kind of fix. Getting the geometry honest tends
to surface whatever was quietly wrong underneath it.

## If you are porting this

The pieces are small and none of them are specific to this project:

- Read the insets from UIKit, from inside the loop, and cache the first non-zero
  answer.
- Give the drawing code a region rather than a screen.
- Translate and clip around it.
- Translate the pointer the other way, so input and output agree.

The whole thing is about sixty lines across `raylib.host` and `raylib.gallery`:
45 in `safe-region`, `below-the-safe-area`, `into-safe-region` and
`safe-area-pixels`, five one-line FFI bindings for the matrix stack and the
scissor, and the handful of call sites that use them.
