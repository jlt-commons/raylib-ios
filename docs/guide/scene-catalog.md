# The scenes

Eighty-four, in four categories. Three come byte-identical from
[jasalt/jolt-android-experiment](https://github.com/jasalt/jolt-android-experiment)
with their sha256 verified, and the other eighty-one are ports from
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
| Multitouch | a circle and trail per finger | 58 | every point, not just point zero |
| Analog Clock | a bezel, 60 ticks, 3 hands | 59 | rlgl stands in for the by-value Vector2 calls |
| Clock of Clocks | 144 bezels, 288 hands | 59 | the time spelled by little clock faces |
| Circle Sector | one sector, 1-36 segments | 58 | raylib's own segment floor, demonstrated |
| Colours | 25 swatches | 58 | every colour raylib names |
| Gradients | 4 quads, 8 vertices | 58 | one rlgl quad covers raylib's three calls |
| Ring Drawing | a breathing annulus, stroked | 59 | the scene that would have caught the winding bug |
| Splines | 3 bases, 480 segments | 59 | Catmull-Rom passes through, the others do not |
| Rounded Rect | 2 rects, 4 quarter disks | 58 | DrawRectangleRounded, decomposed |
| Vector Angle | 2 arms and a filled arc | 58 | the angle is signed, and you can see the sign |
| Rounded Bars | 5 fans, 200 triangles | 60 | per-side rounding and a gradient, from one loop |
| Bezier | 48 segments and a control polygon | 58 | follows a finger; handles are derived, not dragged |
| Line Widths | 16 spokes, 16 widths | 58 | a direct test of thick-line winding |
| Scissor | 435 rectangles, most clipped | 58 | a scene's own clip, intersected with the safe region |
| Resize | a rectangle and a corner handle | 58 | a sticky grab, because a finger has no hover |
| Text Alignment | 3 boxes, one word | 58 | MeasureText, the rare call with nothing to work around |
| Dashed Line | ~24 short lines | 58 | equal dashes, by walking the unit vector |
| Delta Time | 2 rectangles, 2 labels and an fps line | 58 | per-frame against delta time; at 58 fps the delta box already gains about 9 px a second |
| Random Values | a number and a label | 58 | seeded, so it replays |
| Formatted Text | 2 lines of text | 58 | zero-padded score and MM:SS, padded by a private `zero-pad` built from `str` |
| Triangle Strip | 32 triangles | 58 | two per band, through `draw-triangle` so winding cannot cull them |
| Touch Ball | 1 circle and a caption | 58 | follows a finger; green while held, and it stays put when the finger lifts |
| rlgl Triangle | 3 vertices, 3 handles, 2 buttons | 58 | a colour per vertex; sticky corner grab, and the keys became buttons |
| Particles | about 190 circles for fire, 324 for smoke, and 65 to 290 for water depending on the finger's height, plus an info box | 59 | emits at the finger while it is down, up to a cap of 600; tapping the box cycles water, smoke and fire |
| Bouncing Ball | 1 circle and 1 or 2 lines of text | 58 | a tap pauses, in place of SPACE; speed and radius scale with the shorter side, and a rotation clamps the ball back inside |
| Virtual Controls | a D-pad of 4 circles and a ring, an A button, a square, 3 lines of text | 58 | every finger down is tested, so a direction and A work together, in place of the arrow keys and SPACE; motion is delta-time based as in the original, and a rotation starts over |
| Starfield Effect | up to 350 circles or streaks and 3 lines of text | 52 | a vertical drag sets the speed in place of the mouse wheel and a tap toggles streaks in place of SPACE; motion is delta-time based as in the original, and a rotation needs no reset |
| Easings Box | 2 triangles and 1 line of text | 58 | a tap restarts, in place of SPACE; frame-locked as in the original, and the box grows to fill the region below Back |
| Easings Testbed | 69 lines (4 frame, 64 plot segments, 1 rail), a circle and 3 lines of text | 58 | a swipe changes the curve in place of LEFT and RIGHT, a tap replays in place of SPACE and a long press toggles the plot in place of D; frame-locked as in the original |
| Rectangle Bounds | 4 lines, 2 rectangles and 3 or more lines of text, depending on the box | 58 | dragging the corner handle resizes the box and a tap on the wrap button toggles word or character wrap, in place of the mouse and SPACE; the layout is cached, so an idle frame measures nothing |
| rlgl Hue Wheel | a fan of 3 to 128 triangles with a colour on every vertex, or 2 lines a wedge as a wireframe, and 3 lines of text | 58 | a vertical swipe doubles or halves the triangle count in place of the mouse wheel, a horizontal drag sets the centre brightness in place of the arrow keys and a tap toggles the wireframe in place of SPACE, and the wireframe spokes ignore the brightness because a thick line takes one colour; the wheel fits the safe region, so UP and DOWN resizing is dropped; a different example from Colour Wheel |
| Still Logo | 2 rectangles and 1 line of text | 58 | nothing moves and nothing is read, so the logo is the finished picture where raylib Logo is the assembly; the label is placed by the measured text width, and the logo is sized to fit below Back |
| Font Sizes | 6 lines of text at 5 sizes and 6 colours | 58 | nothing moves and nothing is read; sizes are the original's 10, 20, 24, 30 and 40 scaled by one factor to fit below Back and rounded to whole numbers, and two lines are centred by the measured width; it is about size where Text Alignment is about placement |
| Inline Styling | 5 lines of text in runs, a rectangle behind some runs, a box of 4 lines and 1 legend line | 58 | nothing is read; the colours come from a tiny markup inside each string, `[cRRGGBBAA]` for the text, `[bRRGGBBAA]` for the background and `[r]` to reset, with a tag's alpha multiplied by the base's, and a bad tag prints as text; the CREATIVE word takes a new colour every 20 frames, frame-locked as in the original, from the project's seeded generator; lines are scaled by one factor and each run starts where the last ends by the measured width |
| Outline Thickness | 7 lines of text, 3 filled shapes, 8 lines (4 rectangle sides and 4 rounded sides), 4 corner arcs and 1 ring | 58 | the value sweeps on its own as the original does, until a touch takes over, and a vertical drag then sets it in place of UP and DOWN, the anchor taken on press and the release swipe ignored; below zero the plain rectangle draws nothing, the rounded one is a one pixel hairline and only the ring grows outward, as raylib does; the range is the original's -30 to 30 and the shapes are scaled to fit below Back |
| Basic Shapes | 1 line of text, 2 rectangles, 2 circles, an ellipse of up to 180 triangles, 1 line, 1 triangle and 4 outline lines | 58 | nothing moves and nothing is read; the ellipse is a triangle fan whose segment count follows its radius, the circle outline is a ring and the lines are thicker than the original's hairlines so they show on a phone; laid out for portrait, where the original is 800 by 450 |
| Ellipse Collision | 2 ellipses of up to 180 triangles each, 2 outlines of as many lines, 2 centre dots and 3 lines of text | 59 | one ellipse follows your finger while it is down and stays where it was on lift; both turn red when they overlap, by the original's test, which samples 64 points round each rim and so can miss a very thin lens; a tap that starts inside the other ellipse hands the steering to it, and that touch does not drag the steered one, so a swap never moves it; a touch under Back is ignored; both sizes share one scale and the centres are clamped to stay on screen below the text |
| Screen Manager | 1 line of text for the screen and 1 for the hint, on a flat colour | 58 | a tap steps LOGO, TITLE, GAMEPLAY, ENDING in place of ENTER, and the timer steps every 90th frame as the original does, frame-locked and counted from the start so a tap does not postpone it; ENDING goes back to LOGO; a tap and the timer on one frame step once, and a finger down when the timer fires is dropped; a touch under Back is ignored |
| Basic Window | 1 line of text on a flat colour | 58 | nothing moves and nothing is read, so there is no control to replace; the line is centred by the measured text width and sits the original's 200/450 of the way down the field below Back, size and colour depart from the original for legibility: the line is fitted by the measured text width to about 85 percent of the screen width, where the original's size 20 came out near 10 pt on the phone, and it is DARKGRAY in place of LIGHTGRAY |
| Keyboard Ball | 1 circle, 1 line of text, and a thumb-stick ring and knob while held | 58 | a relative thumb-stick moves the ball, in place of the arrow keys: the press point is its centre, a finger within the tap slop of it does nothing, and beyond it the ball goes that way at the original's 2 pixels a frame, frame-locked like it; the vector is normalised, so a diagonal is no faster where the original's keys make it 2.83, and the ball is held wholly inside the field, which starts below the caption so the ball never covers it, and below Back where the original lets it leave the window; the speed and radius are scaled by one factor, and a rotation pulls the ball back in |
| Mouse Wheel | 1 square and 1 line of text | 58 | a vertical drag moves the box, in place of the mouse wheel: it follows the finger one for one from where the press anchored it, so the 20 pixel notch is whatever the finger travels, and the release swipe is ignored so the box does not move twice; the box is the original's 80 scaled by one factor, centred across the width and held inside the field below Back as the original holds it inside the window, and a rotation pulls it back in |
| Undo Redo | 1 line of text, up to 26 trail squares, 45 grid lines, 1 square, 1 line of text for the history, up to 26 history slots and 2 labelled buttons | 58 | a swipe moves the square one cell in place of the arrow keys, a tap on the square or the field recolours it in place of SPACE, and two buttons along the bottom replace CTRL-Z and CTRL-Y; the grid is the original's 30 by 13 and is not turned with the screen, the history holds the original's 26 states with redo cleared by a new action, and it is sampled every second frame as the original does; the history count sits above its strip instead of beside it, the grid lines are thicker so they show on a phone, and a button greys out when it has nothing to do; a touch under Back is ignored |
| Strings Management | up to 100 bordered text particles, each a border, a fill and 1 line of text, plus 2 lines of text and 3 labelled buttons | 58 | dragging a particle and lifting throws it in place of the left button, with the velocity of the last four `:down` frames; a long press cuts it in half in place of right-click; the shatter button arms the next tap to shatter it into characters in place of SHIFT and right-click; the shake button replaces middle-click; releasing a dragged particle over another glues them in place of CTRL while dragging, and only a drag past the tap slop that is set down slowly glues, so a throw across another flies on; the case button steps the six case transforms in place of keys 1 to 6, starting at UPPER because the opening sentence is already plain; typing a character to split the lone sentence is dropped, since there is no keyboard; physics use `:delta-seconds` with the original's per-frame friction, and the opening sentence is centred, with the text size fitted to the widest of the six case versions; a touch under Back is ignored |
| 2D Camera | the original's 30-building skyline, the ground and the player box through a camera, a screen-space centre line, 1 line of text and 1 labelled button | | a one-finger drag is a relative thumb-stick on x only (the press point is the centre, a dead zone of the tap slop) that moves the player at the original's 4 units a frame in place of the arrow keys; a two-finger pinch zooms between 0.25 and 3.0 in place of the wheel and a twist rotates in place of A and D, both about the field's centre; the reset button below Back replaces R and leaves the player where it is; a second finger ends the stick, and the finger left after a lift starts none until it lands again; the original's 800x450 view is fitted into the field below the button by a base zoom and the world is clipped to that field; a touch under Back is ignored |
| 2D Camera Zoom | a 22 by 22 line grid (the original's 21x21 cells), 1 square, 1 circle, 1 box and the text "world origin" through a camera, a screen-space crosshair and 2 lines of text | | a one-finger drag pans by the finger's movement over the zoom in place of the left-drag, so the world point under the finger stays under it; a two-finger pinch zooms between 0.125 and 64 in log space about its midpoint in place of the wheel and the right-drag, and the midpoint drags the world if it drifts; keys 1 and 2 are dropped, so the original's mode and its HUD word are gone, and the pinch's twist is ignored; the crosshair sits at the last touch or the pinch midpoint; a second finger ends the pan, a pinch acts only while exactly two fingers stay down, so a third finger or a lift pauses it and moves nothing, and the finger left after a lift pans nothing until it lands again; the world origin starts at the field's centre, not the top-left, because that is under Back; a touch that begins under Back pans nothing |
| 2D Platformer | the 3 platforms, the floor and the sky of the original's 1000 by 600 map, the 40 by 40 player, 1 line of text for the camera mode and 5 labelled buttons | | five buttons along the bottom, read from every touch point so two can be held at once, replace the keys: < and > walk in place of the arrows (the default font has no triangles), jump replaces SPACE and is held as the original holds it, camera steps the five modes on a press in place of C, and reset replaces R on a press and keeps the mode; the original's key-help line is dropped for a line naming the mode; physics, platforms and the five modes (centre, clamped, smooth, even-out, push) are the original's, with `:delta-seconds` for `get-frame-time` and no clamp (a 0.1 s stall still lands); the 800x450 view is fitted into the field by a base zoom and every mode's width and height become the field's, so the clamp and the push margins act on the phone's pixels; a touch under Back is ignored |
| 2D Split Screen | the original's 20 by 11 grid of 40-unit cells with 220 `[i,j]` labels, a red and a blue player and a banner with 1 line of text in each of 2 scissored halves, and a divider | | a relative thumb-stick in each half, read from `:touch-points` and assigned by the half a point lies in, steers that half's player at the original's 3 units a frame, normalised, in place of W, A, S, D and the arrows; a stick starts on the frame a point first appears in its half, with its centre there, so a tap never moves a player; two thumbs work at once; with two points in one half the stick follows the one nearer its finger; a thumb sliding across the divider ends the stick it left and starts a fresh one in the other half; the render textures become a scissor and a camera per half, stacked in portrait and side by side in landscape, with the camera's offset at the half's centre and a base zoom fitting the original's 400 by 440 half; the banners read "drag to move" in place of the key names and the labels keep the original's size 10, scaled with the world; a touch under Back is ignored |
| Input Gestures | a log of up to 20 gesture names in alternating rows, the newest in maroon, a test box with a title and a hint line, and a finger circle while a gesture is set | | raylib's own gesture recogniser, read through `GetGestureDetected` as `:raylib-gesture`, names tap, double-tap, hold, drag and the four swipes, logged when the code changes and the finger is in the box; the finger comes from `:pointer`, the last position seen on a press or a drag when the gesture is reported on release, never the release position; **pinch in and pinch out never appear**, because raylib's SDL platform feeds its gesture system one touch point at a time, and the hint line says so; the log stacks above the box in portrait and runs down its left in landscape, with text sizes shrunk until 20 rows fit |
| Helitorus | a helix wound round a torus and swept into a lit tube of 64 rings of 12 points (up to 900), hidden faces removed by hand, 3 lines of text (fps, compute and draw milliseconds; windings and detail; a hint) and 4 labelled buttons | | a one-finger drag turns it by the original's 0.008 radians a pixel, with its velocity memory and coast to the idle turn, in place of the mouse drag; a two-finger pinch zooms between 110 and 520 in place of the wheel, and its midpoint and twist are ignored; four buttons below the field replace the arrow keys, read from every touch point so a button and the field work at once: windings - and windings + change the windings from 3 to 24 by one on a press (LEFT and RIGHT), detail - and detail + change the rings from 60 to 900 by 4 a frame while held (DOWN and UP); the original's key-help line is dropped for a hint line and its thousand-vertices-a-second figure is dropped from the HUD; the centre and zoom scale come from the field, not the original's 1000 by 560 window; a finger on a button or under Back turns and zooms nothing; the original starts at 260 rings and this starts at 64, chosen from the phone measuring 19 fps at 260, where 64 holds 60 fps (detail + still reaches 260); each visible quad is sent in the winding rlgl keeps, because the batch is drawn at a later flush with culling on |

## Games

| scene | per frame | fps | notes |
| --- | --- | ---: | --- |
| Flappy Bird | ~30 shapes | 59 | **byte-identical** from the Android experiment |
| Breakout | up to 60 bricks, a paddle, a ball | 58 | the paddle follows the finger, and a tap restarts after game over or a win; frame-locked like the original, and a rotation starts a new game |
| Snake | up to 576 cells, a board and a score line | 58 | a swipe steers and a tap restarts, in place of the arrow keys and SPACE; the 32 by 18 grid turns to 18 by 32 on a tall phone, and a rotation starts a new game |
| 2048 | 16 tiles on a board, a score line | 58 | a swipe slides and a tap restarts once stuck or won, in place of the arrow keys and SPACE; reaching 2048 wins and stops play, which the original does not, and a rotation only re-lays out the board |
| Minesweeper | up to 192 cells, a status line | 58 | a tap reveals and a long press flags, in place of left and right click; a tap restarts after a mine or a win, in place of SPACE; the 16 by 12 grid turns to 12 by 16 on a tall phone with the same 30 mines, a rotation starts a new game, and as in the original a tap on a flagged cell opens it and the first tap can hit a mine |
| Pong | 2 paddles, a ball, a dashed line, 2 scores | 58 | your paddle follows the finger and a tap restarts after a win, in place of W, S and ENTER; the court turns 90 degrees on a tall phone, with you at the bottom and the CPU at the top, and a rotation starts a new game; the CPU, the english off the paddle and first to 7 are the original's, and the ball does not speed up |
| Space Invaders | up to 32 aliens, a ship, bullets, a score line | 58 | the ship follows the finger and fires while one is down, and a tap restarts after a loss or a win, in place of the arrow keys and SPACE; the 8 by 4 formation, its 1.2 px march, 18 px drops, the 15-frame fire cooldown and the loss rule (the lowest alien reaches the ship's row) are the original's, scaled to the safe region per axis, and a rotation starts a new game |
| Tetris | a 10 by 20 well, up to 4 falling cells, a next-piece preview, 3 counters | 59 | a horizontal drag moves the piece a column per cell of travel, a tap rotates, a swipe down hard drops and a tap restarts after game over, in place of the arrow keys, SPACE and ENTER; the release swipe of a drag is ignored so the piece does not move twice, and the soft drop is dropped; the seven pieces, the score table, the level speed (a frame count) and the uniform piece choice (from the project's LCG) are the original's, and a rotation starts a new game |
| Asteroids | outlined rocks, a ship, bullets, 4 buttons, 2 counters | 58 | four buttons along the bottom (rotate left, rotate right, thrust, fire), held together by two thumbs, and a tap on the field, not on a button, restarts after game over, in place of the arrow keys, SPACE and ENTER; the physics (ROT, THRUST, FRICTION, wrap), the three asteroid sizes and splits, the 55-frame bullet life, the waves, 3 lives and the invulnerability blink are the original's; a press fires at once as the original's does, and a held fire button keeps firing every 10 frames, where the original fires once per press; the speeds and radii are scaled by one factor so thrust still goes where the ship points, the field wraps above the buttons and below Back, and a rotation starts a new game |
| Vampire Survivors | up to ~50 enemies and gems (through the first 12 s) and 18 bullets, a hero, two bars, 3 counters, a thumb-stick ring while held | 58 | a relative thumb-stick moves the hero, in place of WASD and the arrows: the press point is its centre, a finger within the tap slop of it does nothing, and beyond it the hero goes that way at one fixed speed, so a diagonal is no faster; a tap on the field restarts after game over, in place of ENTER; the spawn rate and wave growth, speeds, radii, two-hit enemies, the auto-fire at the nearest enemy and its level-based cooldown, gem pickup, levelling, contact damage with its grace period and 100 HP are the original's, frame-locked like it; the speeds and radii are scaled by one factor, enemies appear on the field's edge one radius inside it and never touching the hero where the original starts them 20 px outside, gems drawn centred (the original draws from the top-left corner), the field is below Back, and a rotation starts a new game |
| Pac-Man | 194 walls as two rectangles each, up to 197 dots, a 28-triangle Pac-Man, four ghosts of 9 shapes, a HUD row | 58 | a swipe sets the desired direction, in place of the arrow keys and WASD, and Pac-Man takes it at the next tile centre where that way is open, so a swipe into a wall is remembered and not dropped; a tap restarts after game over, outside Back, in place of ENTER; the maze, the four ghost personalities (Blinky, Pinky, Inky and Clyde), scatter for 7 s and chase for 20 s, 7 s of fright with the 200/400/800/1600 combo, scoring, three lives and the levels are the original's, in seconds from `:delta-seconds` like it (it calls `get-frame-time`); the level clear is fixed, because the original re-arms its two second timer every frame and never starts the next level, and the board freezes while LEVEL CLEARED shows; the ghosts' feet are spaced inside the body, where the original's third foot sticks out past it; the mouth is a 28-triangle fan, since `sector!` is not bound, the frightened ghosts' random turns come from the project's LCG, the maze is drawn with square tiles in the safe region below Back, and a rotation re-lays it out and keeps the game |

## What byte-identical means

Three scenes and the three namespaces carrying the scene contract were copied
from the Android experiment without a character changed, and
`tools/extract-from-notebooks` verifies their sha256 on every extraction.

They were written for Android. They run here untouched because the contract
they were written against never mentions a platform: a scene is `:init`,
`:update`, `:draw` and `:dispose` over immutable state, and every raylib call
lives in `raylib.gallery`'s drawing methods instead.

That is the whole argument for the split, and it is the reason most of the
ports were transcription rather than rewrites. Not all of them: several needed
resizing for a phone, and `multitouch` does something its original could not.
See
[porting an example](porting-an-example.html) for what a port actually involves.

## Adding one

Four touchpoints, listed in `CONTRIBUTING.md`. The short version is a pure
`.cljc` under `src/raylib/scenes/`, a test beside it, a `draw-scene!` method in
`raylib.gallery`, and an entry in the category list.
