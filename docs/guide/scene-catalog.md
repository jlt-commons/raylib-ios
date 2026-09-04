# The scenes

Thirty-two, in four categories. Three come byte-identical from
[jasalt/jolt-android-experiment](https://github.com/jasalt/jolt-android-experiment)
with their sha256 verified, and the other twenty-nine are ports from
[raylib-jlt](https://github.com/jlt-commons/raylib-jlt).

Frame rates are measured on an iPhone 17 Pro running iOS 26.6.1, with the probe
seam described in [a REPL on the phone](a-repl-on-the-phone.html). Anything at
58 or 59 is vsync-limited and has headroom; the numbers below 58 are the ones
that were tuned to get there.

## Generative

| scene | per frame | fps | notes |
| --- | --- | ---: | --- |
| Spirograph | ~1000 lines | 55 | draws itself, then resets |
| Kaleidoscope | 708 lines | 58 | 60-point trail, twelve-fold symmetry |
| Fireworks | ~120 circles | 58 | seeded, so it replays identically |
| Penrose | 340 triangles, ~2400 calls | 57 | P3 tiling by deflation |
| Fourier Epicycles | ~200 lines | 59 | a square wave, drawn by circles |
| Flow Field | 90 particles, 8-point trails | 54 | the first port the budget bit |
| Lorenz | 450 lines, re-projected each frame | 58 | see the sweep in [performance](performance-on-a-phone.html) |
| Life | 1470 rectangles falling to ~670 | 59 | reseeds itself when the board stalls |
| Bullet Spiral | ~350 circles | 59 | the count is bounded by how fast a bullet leaves |

## Fractals

| scene | per frame | fps | notes |
| --- | --- | ---: | --- |
| Hilbert Curve | 1023 lines | 60 | order 5, drawn progressively |
| Fractal Tree | 511 lines | 59 | sways; the only light-background scene |
| L-system Plant | 1488 segments | 59 | static once grown, which is why it is cheap |
| Automata | 2551 rectangles | 58 | scrolls; rules cycle on a timer |

## Toys

| scene | per frame | fps | notes |
| --- | --- | ---: | --- |
| Following Eyes | 6 circles | 59 | **byte-identical** from the Android experiment |
| Touch Trail | ~40 circles | 59 | **byte-identical**; the first scene here that wanted a finger |
| Boids | 2025 distance tests, 90 draws | 52 | the cheapest drawing and the dearest thinking |
| Double Pendulum | ~200 trail points | 59 | chaotic, so it never repeats |
| Starfield | ~300 points | 58 | seeded |
| Tesseract | 32 lines | 59 | the cheapest scene here, and a useful control |
| Colour Wheel | 540 vertices | 59 | [rlgl immediate mode](rlgl-immediate-mode.html) |
| Sine & Cosine | ~600 lines | 58 | the projections, and their traces |
| Clock | 42 rectangles | 59 | libc `time()` through the FFI |
| Pie Chart | 186 vertices | 58 | segments sized per wedge |
| raylib Logo | 4 rectangles and a word | 58 | nothing eased, on purpose |
| Easings | 405 lines and 15 dots | 58 | all fifteen curves on one clock |
| Angles | 13 lines and a circle | 59 | the two lines every circular scene here uses |
| Writing | a few lines of text | 58 | the only scene that animates text |
| Ball Physics | 9 circles | 58 | restarts when everything settles |
| Random Sequence | 24 rectangles | 57 | a permutation, not independent draws |
| Collision Area | 3 rectangles | 59 | follows a finger, and drifts without one |
| Dashed Line | ~24 short lines | 58 | equal dashes, by walking the unit vector |

## Games

| scene | per frame | fps | notes |
| --- | --- | ---: | --- |
| Flappy Bird | ~30 shapes | 59 | **byte-identical** from the Android experiment |

## What byte-identical means

Three scenes and the three namespaces carrying the scene contract were copied
from the Android experiment without a character changed, and
`tools/extract-from-notebooks` verifies their sha256 on every extraction.

They were written for Android. They run here untouched because the contract
they were written against never mentions a platform: a scene is `:init`,
`:update`, `:draw` and `:dispose` over immutable state, and every raylib call
lives in `raylib.gallery`'s drawing methods instead.

That is the whole argument for the split, and it is the reason the other
twenty-four ports were mechanical rather than rewrites. See
[porting an example](porting-an-example.html) for what a port actually involves.

## Adding one

Four touchpoints, listed in `CONTRIBUTING.md`. The short version is a pure
`.cljc` under `src/raylib/scenes/`, a test beside it, a `draw-scene!` method in
`raylib.gallery`, and an entry in the category list.
