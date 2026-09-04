# Filling shapes with rlgl

Almost every scene here draws outlines: lines, circle outlines, rectangles.
Two of them fill, and those two are the entire reason the host binds rlgl's
immediate mode at all.

## What raylib's shapes API cannot do

raylib will fill a circle, a rectangle, a triangle. What it will not do is fill
a triangle with a **different colour at each corner**, and the gradient around
the rim of an HSV colour wheel is exactly that: one slice, hue at each rim
vertex, white at the centre, and everything between interpolated.

There is no `DrawTriangleGradient`. There is rlgl.

## The four calls

```clojure
(ffi/defcfn rl-begin     "rlBegin"     [:int] :void)
(ffi/defcfn rl-end       "rlEnd"       [] :void)
(ffi/defcfn rl-vertex-2f "rlVertex2f"  [:float :float] :void)
(ffi/defcfn rl-color-4ub "rlColor4ub"  [:uint8 :uint8 :uint8 :uint8] :void)
(def RL-TRIANGLES 0x0004)
```

All four are scalar, which matters: they need none of the by-value struct
machinery the `Vector2` and `Color` calls elsewhere in raylib do. That makes
this the cheapest possible FFI surface, four functions taking numbers.

The shape is the OpenGL 1.x one, and it is a state machine. Colour applies to
whatever vertex comes next:

```clojure
(rl/rl-begin rl/RL-TRIANGLES)
(rl/rl-color-4ub 255 0 0 255)
(rl/rl-vertex-2f x0 y0)        ; this corner is red
(rl/rl-color-4ub 255 255 255 255)
(rl/rl-vertex-2f cx cy)        ; this one is white
(rl/rl-color-4ub 0 255 0 255)
(rl/rl-vertex-2f x1 y1)        ; and this one green
(rl/rl-end)
```

Three vertices between `rlBegin` and `rlEnd` is one triangle. Six is two. rlgl
batches them and flushes on `rlEnd` or when its buffer fills.

## The colour wheel

180 slices, each a triangle from rim to rim to the centre. The rim vertices
carry their own hue at full saturation and value, so the wheel is the outer
surface of the HSV cylinder, and the centre is white. The rasteriser does the
rest for free.

It turns because the hue offset advances, not because anything is rotated. That
is cheaper and it also means the geometry is recomputed identically every frame,
which is fine at 540 vertices and would not be at 54,000.

## The pie chart

Same primitive, different problem. raylib-jlt reaches for a `sector!` helper;
there is none here, so a wedge is built from the same triangles, enough of them
that the arc reads as curved rather than faceted.

The number is per wedge and proportional to its angle:

```clojure
(def degrees-per-segment 6.0)
(let [n (max 2 (int (Math/ceil (/ span degrees-per-segment))))] ...)
```

A 30% wedge gets 18 triangles and a 12% wedge gets 8. Giving every wedge the
same twenty is 100 triangles a frame for a picture identical to the 62 this
draws.

All three vertices of a pie triangle carry the same colour, so the gradient
machinery is not doing anything here. It is used purely because the alternative
is not having filled arcs at all.

## When to reach for it

Rarely. An outline is cheaper, most of these scenes are outlines by nature, and
the immediate-mode state machine is easy to leave in a bad state.

The test is whether raylib's own shapes API can express what you want. If it can,
use it: `DrawRectangle` is one call where the equivalent here is `rlBegin`, six
vertices and `rlEnd`. If it cannot, because you need per-vertex colour or an arc
raylib has no call for, this is the escape hatch and it is a small one.

## Cost

Cheaper than it looks. The colour wheel is 540 vertices a frame and holds 59 fps;
the pie chart is 186 and holds 58. Compare that against
[performance on a phone](performance-on-a-phone.html), where a scene making 2400
separate FFI calls a frame also holds 59: the per-call overhead is not what
limits these scenes, and batched vertices are further from the limit still.
