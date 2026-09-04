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

## The fix, without touching nineteen draw methods

The obvious approach is to pass the insets to every scene and have each one
offset its own drawing. That is nineteen edits, nineteen chances to get an
offset wrong, and every future scene inherits the obligation.

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
above and below keep whatever the gallery drew there. That is the behaviour you
want and it is not obvious from the name.

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

This one hid for a while, because for a long time no scene read the pointer at
all. The gallery's own hit-testing was never affected: it compares a screen
point against a layout already moved into screen coordinates, so both sides were
consistently wrong together and cancelled out. The bug only became reachable
when a scene first wanted a finger for itself.

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
