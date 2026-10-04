# Changelog

Notable changes, newest first. Dates are the day the work landed.

## Unreleased

### Changed

- **Every namespace is now `net.b12n.raylib-ios.*`.** `raylib.<x>` became
  `net.b12n.raylib-ios.<x>`, so `raylib.gallery` is
  `net.b12n.raylib-ios.gallery` and `raylib.scenes.<x>` is
  `net.b12n.raylib-ios.scenes.<x>`, and the files moved to
  `src/net/b12n/raylib_ios/` and `test/net/b12n/raylib_ios/` to match. The six
  `poc.raylib` namespaces became `net.b12n.raylib-ios.gallery.core`,
  `.gallery.ui`, `.gallery.diagnostics`, `.scenes.flappy-bird`,
  `.scenes.following-eyes` and `.scenes.touch-trail`. Build with
  `NS=net.b12n.raylib-ios.gallery`, `.link` or `.live`; the test runner is
  `net.b12n.raylib-ios.test-runner`. Entries below keep the names they were
  written with.
- **The six jasalt namespaces are checked as "identical apart from names".**
  `net.b12n.raylib-ios.jasalt-identity-test` puts the upstream names back,
  drops whitespace and compares the sha256 with the upstream file's, using the
  table in `tools/jasalt-identity.edn`, which `tools/extract-from-notebooks`
  reads too. The whitespace step is there because five of the six had been
  through `clojure-lsp format` since 4c23d11, so their raw hashes stopped
  matching the notebooks that day and nothing noticed. The test runs on the
  JVM only: jolt has no sha256.
- **`no-old-namespace-remains`** in the gallery smoke test fails on any
  leftover `raylib.<x>` or `poc.raylib` outside this file and the identity table.

## 2026-10-04

### Added

- **Five render-target scenes, a hundred and thirty-seven in all.** Toys now
  holds a hundred and thirteen. Render Texture, Framebuffer Rendering, Mouse
  Painting, Magnifying Glass and Top Down Lights draw into off-screen
  framebuffers and draw the result back. A device pass of the final build read
  all five at 58 fps, and Top Down Lights held 58 with 16 lights while dragging
  one. Top Down Lights pauses 75 ms on its first open, Render Texture 103 ms
  (the first scene opened after launch) and the other three 29 to 33 ms.
- **Render targets.** `raylib.texture/target!` makes a framebuffer with an
  RGBA8 colour texture, and a depth buffer unless asked not to, from rlgl's
  scalar calls, never raylib's by-value `RenderTexture2D`. `with-target!` draws
  into one and gives everything back afterwards: SDL's framebuffer (on iOS the
  screen is not framebuffer 0, and rlgl's framebuffer calls bind 0), the
  viewport and projection, the gallery's translate, which rlgl keeps in its
  `transform` matrix rather than modelview, and the scissor.
  `with-blend-factors!` runs a draw under custom blend factors, which is how
  Top Down Lights gets GL_MIN and GL_MAX. `:depth?` is part of a target's
  identity, and `band!` refuses a render target.
- **raylib's own Perlin image.** `perlin-texture!` calls `GenImagePerlinNoise`
  through jolt's by-value struct return and uploads the pixels raylib made
  (11.7 ms on the phone for 800 by 450). `raylib.perlin` is a pure port of
  stb_perlin and `GenImagePerlinNoise`, bit-exact with the C, kept as the
  tested model.
- **`host/draw-circle-gradient`**, raylib's `DrawCircleGradient` rebuilt from
  rlgl, since the C takes its centre by value.
- **Ten texture scenes, a hundred and thirty-two in all.** Toys now holds a
  hundred and eight. Texture Tiling, Source and Destination Rects, Sprite
  Button, Npatch Drawing, Polygon Drawing, Procedural Textures, Sprite
  Animation, Textured Curve, Raw Data and Screen Buffer draw real GPU textures.
  A device pass read all ten at 58 or 59 fps. Raw Data and Screen Buffer
  refresh a band of rows a frame rather than the whole texture, and say so.
- **Texture bindings.** `raylib.texture` uploads, updates and draws textures
  through rlgl's scalar calls (`rlLoadTexture`, `rlUpdateTexture`,
  `rlSetTexture`), never raylib's by-value `Texture2D`. `id!` uploads a spec,
  `quad!` stands in for `DrawTexturePro`, `triangles!` winds each triangle
  itself and `band!` refreshes a few rows. It throws on `:repeat` with a size
  that is not a power of two, and frees a scene's textures when the scene is
  left.
- **`raylib.texel`.** Packs RGBA8 texels and mirrors the `ImageDraw*`
  rasterisers of raylib 6.0's `rtextures.c`, including their quirks. The pixel
  functions of the ten scenes are tested texel by texel against the originals.
- **Banded uploads.** Raw Data refills 3 rows of its live panel a frame and
  Screen Buffer steps and uploads 8 rows, so no frame pays for more than a band.
  Both show a moving seam between fresher and older rows. Screen Buffer's fire
  is a 100 by 56 grid, a quarter of the original's, and its roots start hot so
  the first sweep has a flame.
- **Retained buffers.** A static spec, handed back as the same object, keeps its
  filled staging buffer, so a scene that was opened before costs about one frame
  to open again. The noise of Procedural Textures and the live panel of Raw Data are not
  kept, and reopen in about 0.14 to 0.15 s.
- **Texture stubs in the smoke test.** The smoke test stubs the rlgl texture
  calls with each argument's type checked, and fails a draw that leaves a
  texture bound.
- **NOTICE names the new derivations.** `src/raylib/texture.clj` lifts from
  raylib-jlt's textures library and `src/raylib/texel.cljc` follows raylib's
  `rtextures.c`.
- **Textures sections** in CONTRIBUTING and the porting guide.

### Changed

- **Every count in the docs is derived from the code again.** The scene,
  category, ported and blocked counts in the README, the guide, the catalog,
  the site and the ROADMAP now read 137 scenes (Generative 9, Fractals 4, Toys
  113, Games 11) and 139 of raylib-jlt's 187 examples. Test counts are no longer
  quoted, since they change every batch.
- **The ten docstrings give the licence of their raylib-jlt originals.** Eight
  were added to raylib-jlt after its 2026-09-05 relicence and are EPL 2.0; the
  other two, Texture Tiling and Procedural Textures, predate it and are zlib.
  The raylib C examples behind them are zlib, and the scenes say they are altered
  versions.
- **Screen Buffer's warm-up is stated as an estimate.** A cold fire at 8.6 steps
  a second would take about 30 s to light, worked out from the step rate and not
  measured.

### Found

- **First opens pause.** The first open after launch holds the largest single
  frame at about 1.0 s for Sprite Animation, 0.8 s for Polygon Drawing, 0.5 s for
  Procedural Textures, 0.2 s for Srcrec Dstrec and Sprite Button, and 0.17 s for
  Raw Data. The catalog rows say so.
- **Basic Voxel freezes on a tap.** A tap holds the phone for about 285 ms. The
  ROADMAP has the fix.

## 2026-10-03

### Added

- **Eight more scenes, a hundred and twenty-two in all.** Toys now holds
  ninety-eight. Blend Modes and Particles Blending, Billboard Rendering and
  Directional Billboard, Textured Cube, Geometric Shapes, Basic Voxel and the
  Doom-like Raycaster come from examples that used blend modes, textures or
  models, and each draws flat shapes instead. Basic Voxel adds a place mode the
  original lacks. The Doom-like Raycaster casts 180 columns where the original
  casts 450.
- **Blend bindings.** `BeginBlendMode` and `EndBlendMode`, the first of their
  kind here. Both blend scenes end the mode in a `finally`, so a throw in the
  draw cannot leave the blend on.
- **`soft3d` builders.** `billboard`, `cylinder`, `cylinder-wires`, `capsule` and
  `capsule-wires`, each following the matching raylib models code, and a flat
  shade for `sphere`.
- **The gallery caches two draw lists.** Geometric Shapes builds its list once per
  screen, and 3D Split Screen caches its list, keyed on the scene state rather than the
  screen.

### Changed

- **3D Split Screen is faster.** It draws the same picture with a quicker cube
  builder that matches `soft3d/cube` item for item, and a test holds the two
  together.
- **The smoke test runs every scene's `draw-scene!`.** It used to run the pure
  update and draw for 120 frames and the draw method for Doom alone. Now every
  draw method runs over stubbed raylib with each argument's type checked against
  its FFI signature, and the blend calls must balance on every frame.
- **NOTICE names the Doom-like Raycaster and Pac-Man** among the files ported
  from babashka/ffi.
- **The phone budget is restated from the device.** A scene is sized to about
  0.30 ms on the laptop (update, build and the draw side's loops, under jolt),
  where earlier entries said 0.45. The 2026-10-03 pass read 0.36 to 0.39 ms at 57
  to 60 fps and 0.5 ms and over at 32 to 41. The performance guide has a section
  on it, and the catalog and docstrings quote the new figure. Seventeen
  empty fps cells are filled, 120 in all.

### Found

- Basic Voxel measured 32 fps at 0.56 ms and 57 at 0.38 on the phone before
  its fixes, which is where the new line was drawn. After them it reads 59.

## 2026-10-02

### Added

- **Seven more 2D scenes, a hundred and fourteen in all.** Toys now holds
  ninety. Bunnymark, Background Scrolling, Sprite Stacking, Smooth Pixel-Perfect,
  Viewport Scaling, Window Letterbox and Fog of War come from examples that
  used textures, and each draws rects, circles and triangles instead. Bunnymark
  keeps its bunnies inside the field when the phone turns. The seven have not
  run on the phone, and neither have the four before them, so eleven fps cells
  are empty and the first hundred and three scenes are the measured ones.
- **Four more 3D scenes, a hundred and seven in all.** Toys now holds
  eighty-three. First-Person Camera and First-Person Maze walk with a thumb-stick
  and look with a drag, the maze drawing only the walls a ray can reach. 3D Split
  Screen gives each half its own camera and stick, and Bouncing Spheres
  re-deals every ball on a tap. The four have not run on the phone, so their fps
  cells are empty. `dna_helix` is deferred, because a faithful port built in
  12.7 ms on the laptop, about 28 times the phone's budget.
- **`soft3d/cube` is faster.** It takes the box emitter lifted from Waving Cubes,
  which finds the faces toward the eye from the eye's place in the box's own
  space. Its output matches the old body item for item.
- **Three more 3D scenes, a hundred and three in all.** Toys now holds
  seventy-nine. Waving Cubes draws 81 of the original's 196 columns, Solar
  System nests a Sun, an Earth and a Moon, and Point Cloud draws 400 of the
  original's 1500 points as screen-space squares. The first version of Waving
  Cubes ran at 15 fps on an iPhone 17 Pro and the first Point Cloud at 9, so both
  were cut and now paint far to near by their own order instead of `finish`.
  Solar System measured 58 fps in its first version and is unchanged. The cut
  versions of the other two have not run on the phone, so their fps cells are
  empty.
- **`soft3d/sphere` and `soft3d/plane`.** Both follow raylib's models code, with
  its latitude and longitude bands and its one-sided quad.
- **Ten 3D scenes, a hundred in all.** Toys now holds seventy-six. Rotating Cube,
  3D Camera, Orthographic Projection, Spinning Cubes, World to Screen and
  Wireframe Shapes turn and orbit with no input. 3D Free Camera rebuilds raylib's
  free camera, with a drag to look and a thumb-stick to move. Yaw Pitch Roll
  flies a plane of five boxes with a thumb-stick, Box Collisions walks a cube
  among five static boxes, and 3D Picking casts a ray through a tap. None of them
  has run on the phone yet, so their fps cells are empty. The per-frame estimate
  puts Wireframe Shapes heaviest, at about 7.6 ms.
- **3D from scalar calls and no depth buffer.** `raylib.soft3d` holds raylib's
  Camera3D projection, the transforms, the cube, wire, grid and line builders, a
  draw list sorted far to near, `field` for the layout and `fit-camera`, which
  widens the original's view to fit a portrait field. Every triangle keeps the
  front winding rlgl culls by, and nothing behind the near plane is drawn.
  `raylib.host/draw-3d!` sends the list out in one batch per run of triangles
  or lines. No new raylib bindings.
- **One thumb-stick tracker.** `raylib.stick` follows a finger by its touch id
  and never adopts one that was already down. Free Camera, Yaw Pitch Roll and
  Box Collisions use it, and Nudge and Split Screen keep their own for now.

- **Six scenes, ninety in all.** Toys now holds sixty-six. 2D Camera moves a box
  through a skyline with a relative thumb-stick on x, pinches to zoom and twists
  to rotate. 2D Camera Zoom pans under one finger and pinches in log space about
  its midpoint. 2D Platformer has five labelled buttons along the bottom and
  steps through the original's five camera modes. 2D Split Screen gives each half
  a thumb-stick and draws the halves with a scissor and a camera in place of
  render textures. Input Gestures logs the codes from raylib's own recogniser.
  Helitorus sweeps a tube round a torus by hand, turns on a one-finger drag and
  zooms on a pinch.
- **A 2D camera from scalar calls.** `raylib.camera2d` holds the camera math and
  one pinch rule shared by 2D Camera, 2D Camera Zoom and Helitorus.
  `raylib.host/with-camera-2d` pushes `rlTranslatef`, `rlRotatef` and `rlScalef`
  on top of the gallery's own translate, because `BeginMode2D` loads the identity
  matrix and would drop it.
- **`:raylib-gesture` in the frame input.** It is the code from
  `GetGestureDetected`, read once a frame by `raylib.gallery`.

### Changed

- **Waving Cubes and Point Cloud measured after their cut.** On an iPhone 17 Pro
  they now run at 59 and 60 fps, against 15 and 9 for their first versions, so
  every one of the hundred and three scenes has a measured rate.

- **The ten 3D scenes measured on an iPhone 17 Pro.** All run at 58 or 59 fps,
  Wireframe Shapes included, so every one of the hundred scenes has a measured
  rate again. A tap on 3D Picking's box hit it on the phone, and the box turned
  red with the ray drawn outside it.

- **Helitorus starts at a detail of 64 rings.** At its original 260 the phone
  ran 19 fps, with 29.5 ms of compute and 19.5 ms of draw in a release build.
- **Helitorus no longer toggles culling.** rlgl draws at the batch flush and not
  at `rlEnd`, so a toggle around immediate-mode calls had already been put back
  by the time the triangles drew. The scene now emits the front winding, and the
  culling binding is gone.
- **All six measured on an iPhone 17 Pro.** 2D Camera, 2D Camera Zoom, 2D Split
  Screen, Input Gestures and Helitorus run at 58 fps and 2D Platformer at 59, so
  every one of the ninety scenes has a measured rate. Split Screen draws 440
  labels a frame, and Helitorus holds 58 to 60 at a detail of 64 with its tube
  drawn solid.

### Found

- **The painter's order has two known limits.** The grid is painted under every
  face, so grid lines that cross a cube's lower half are hidden where a depth
  buffer would show them. And in 3D Free Camera, two fingers landing on the same
  frame can still make the look path adopt one that was already down.
- **raylib's gesture recogniser fires under the SDL host.** A real finger logged
  TAP, HOLD, SWIPE RIGHT and SWIPE DOWN. DRAG and DOUBLETAP were not seen, and
  pinch can't fire, because SDL feeds raylib one finger at a time.

## 2026-10-01

### Added

- **Seven scenes, eighty-four in all.** Games now holds eleven and Toys sixty.
  Vampire Survivors steers the hero with a relative thumb-stick, and Pac-Man
  steers with swipes. Basic Window is a flat colour with one line of text and
  reads nothing. Keyboard Ball moves a ball with a thumb-stick in place of the
  arrow keys, Mouse Wheel moves a box with a vertical drag in place of the wheel,
  and Undo Redo and Strings Management map their keys to taps and swipes, with
  the typing dropped from Strings Management. On an iPhone 17 Pro all seven
  hold 58 fps, Pac-Man included, and each has a still on the homepage and in
  the README.
- **Every scene's input carries `:measure`.** It is raylib's own text width, as
  `(fn [s size] -> px)`. Strings Management keeps text widths in its state, so it
  reads the key in `init` and `update`, and falls back to an estimate so tests
  run without the FFI.
- **Seven scenes, seventy-seven in all.** Toys now holds fifty-five. Still Logo
  draws the raylib logo finished, with nothing to touch. Font Sizes shows lines
  at five sizes and reads nothing. Inline Styling colours runs of text with tags
  inside the string, `[cRRGGBBAA]` for the text, `[bRRGGBBAA]` for the
  background and `[r]` to reset, and one word changes colour every 20 frames.
  Outline Thickness sweeps its value on its own until you touch, and a vertical
  drag then sets one thickness for a rectangle, a rounded rectangle and a ring.
  Basic Shapes is a static tour of circles, an ellipse, a line, a triangle and
  outlines, with the ellipse drawn as a triangle fan. In Ellipse Collision one
  ellipse follows your finger and both turn red when they overlap, and a tap
  that starts inside the other ellipse hands the steering to it. Screen Manager
  steps through LOGO, TITLE, GAMEPLAY and ENDING on a tap or on the original's
  90-frame timer. Their frame rates were measured later the same day, under
  Changed.
- **Where the seven differ from their originals.** Below zero, Outline Thickness
  behaves per shape as the original does. The plain rectangle draws nothing, the
  rounded one is a one pixel hairline and only the ring grows outward. Ellipse
  Collision keeps the original's overlap test, which samples 64 points round each
  rim, so it misses a very thin lens where two ellipses just cross. Screen
  Manager's ENDING goes back to LOGO, as the original does. Font Sizes keeps the
  original's ratios from 10 to 40, so its smallest line is small on a phone.
- **Six touch toys, seventy scenes in all.** Toys now holds forty-eight. Virtual
  Controls draws a D-pad and an A button that read every finger down, so a
  direction and A work together, and every direction moves at one speed.
  Starfield takes a vertical drag to set the speed and a tap to toggle streaks.
  Easings Box runs its five stages and a tap restarts it. Easings Testbed changes
  the curve on a swipe, replays on a tap and toggles the plot on a long press.
  Rectangle Bounds wraps text in a box you resize by its corner handle, with a
  button that switches between word and character wrap. Hue Wheel doubles or
  halves its triangles on a swipe up or down, sets the centre brightness on a
  horizontal drag and toggles the wireframe on a tap. Their frame rates were
  measured later the same day, under Changed.
- **Where the six differ from their originals.** Easings Testbed narrows its plot
  so elastic curves stay on screen. Easings Box grows to fill the area below
  Back. Hue Wheel doubles or halves per swipe where the original stepped one at a
  time, and its wireframe spokes ignore brightness because a thick line takes one
  colour.
- **Four arcade games, sixty-four scenes in all.** Games now holds nine. Pong
  turns the court to portrait with you at the bottom, and the paddle follows a
  finger. In Space Invaders the ship follows a finger and holding fires on the
  original's 15-frame cooldown. Tetris moves the piece a column per cell of drag,
  turns it on a tap and hard-drops it on a swipe down, and has no soft drop.
  Asteroids has four on-screen buttons for rotating, thrust and fire that work
  together, so two thumbs can steer and shoot. It fires once per press of Fire, as
  the original does, so a held Fire button doesn't repeat. A tap on the field
  restarts each game after it ends. Their frame rates were measured later the
  same day, under Changed.

### Changed

- **Every scene has a measured frame rate.** The 21 scenes from batches 3 to 6
  had blank fps cells. On an iPhone 17 Pro they run at 58 or 59 fps, apart from
  Starfield Effect at 52, so all 84 now sit between 52 and 60.
- **Asteroids keeps firing while fire is held.** The original fires once per
  SPACE press, so the port fired once per touch-down and a thumb had to mash.
  A press still fires at once, and a held button now fires again every 10
  frames, six shots a second.
- **Strings Management glues only on a slow drop.** The original glues while
  CTRL is held. The port first glued any drag released over another particle,
  so after a shatter an ordinary throw stuck by accident. Now the drop must be
  slower than 200 px a second, scaled to the screen, and a throw flies on.
- **Starfield Effect is the new title of batch 5's Starfield.** The old title
  matched the older stars scene, so two cards in Toys read the same.

### Fixed

- **Four things the phone showed.** Strings Management fitted its text to the
  plain sentence, so the upper-case one ran off the left edge, and now it fits
  the widest of the six. Pac-Man's ghosts had a third foot outside the body, as in
  the original, which read as a loose dot at phone scale. Keyboard Ball's ball
  could cover its own caption, so the field now starts below it. Basic Window's
  line was about 10 pt and pale grey, so it now spans most of the width in dark
  grey.
- **Pac-Man reaches level 2.** The original's LEVEL CLEARED timer never ran out,
  so the original never got past level 1. The port lets it run out, and it
  freezes the board while LEVEL CLEARED shows, so no ghost can take a life from
  a cleared board.
- **A touch held through a game's end no longer restarts it.** Pong, Space
  Invaders, Tetris, Asteroids and Snake all treated the finger still on the glass
  as the tap that restarts, so the game began again the moment it ended.

## 2026-09-30

### Added

- **A gesture layer and four scenes on it, sixty in all.** `raylib.gesture`
  turns the per-frame pointer into taps, swipes and long presses, so a scene
  reads what a touch meant and no longer pieces it together from presses. Bouncing
  Ball is raylib-jlt's `bounce`, where a tap pauses in place of SPACE. Snake,
  2048 and Minesweeper join Games, which now holds five. Snake and 2048 are
  steered by swipes, and Minesweeper reveals on a tap and flags on a long press.
  Minesweeper keeps the original's rules, so a tap on a flagged cell opens it and
  the first tap can hit a mine. 2048 adds a win at 2048 that the original lacks,
  and says so in its docstring and its catalog row. Each board starts below Back.
  Their frame rates were measured on 2026-10-01.

- **Four touch-driven scenes, fifty-six in all.** Touch Ball is raylib-jlt's
  `mouse`: a circle that follows a finger, green while it is down and staying put
  when it lifts. The rlgl Triangle has a colour per vertex, three handles that
  stay grabbed once a finger lands on them, and two buttons where the original
  used keys. Particles emits at the finger while it is down, and tapping its box
  cycles water, smoke and fire. The cap is 600, but a held finger settles at
  about 190 live particles for fire and 324 for smoke, which emits three a frame
  and keeps each for about 108. Water depends on where the finger is, because a
  drop lives until it leaves the screen, and it reaches about 280 near the top of
  a phone.

  Breakout is the gallery's second game, in Games beside Flappy Bird. The paddle
  follows the finger and a tap restarts after game over or a win. It is
  frame-locked like the original, so its speed follows the frame rate. Breakout
  and the triangle both survive a rotation, Breakout by starting a new game and
  the triangle by clamping its corners into the new screen. On an iPhone 17 Pro
  all four hold 58 to 59 fps while a finger drags, Particles included with 324
  smoke particles on screen, and each has a still taken mid-drag on the homepage
  and in the README.

- **A section on reading a finger** in the porting guide, covering the
  press-and-down rule, why the position on `:release` can't be trusted, sticky
  grabs and keeping buttons off Back.

- **A smoke test for the gallery**, which nothing in CI had ever loaded. A scene
  left out of a category just vanished from the menu, and a missing `draw-scene!`
  method crashed on the phone, so the test checks the four registration points
  and then runs every scene for 120 frames of scripted touch. That also covers
  the crash-on-open path for scenes with no test of their own. It needs
  `jolt.ffi`, so it runs under jolt only, and on the JVM the runner prints a line
  saying it skipped it and why.

  The runner changed with it. It now prints which jolt-only namespaces it runs,
  fails if they ran zero tests, and exits 1 when any test namespace fails to
  load. It used to print the load error and carry on, so a namespace that broke
  on require was reported as green.

- **A guard around scene code.** A bug in a scene's `:init` on open, its
  `:update` or `:draw` on each frame, its `:dispose` on Back, or its
  `draw-scene!` method used to end the process on the phone. Now the gallery catches it, drops
  back to the list the scene was opened from and prints one line to the console,
  such as `gallery: :strip failed, back to the list: <message>`. Some exceptions
  carry no message, a null pointer among them, so the line falls back to the
  exception's own string rather than ending in nothing.

- **Four more scenes, fifty-two in all.** Delta Time draws two rectangles, one
  that moves a fixed step each frame and one that moves by the time since the
  last frame. The two only agree at exactly 60 fps, and the phone averages about
  58.5, so they drift apart by roughly 9 pixels a second before anyone touches
  the frame rate. Random Values is seeded and replays. Formatted Text shows a
  zero-padded score and a MM:SS clock, and Triangle Strip draws through
  `draw-triangle` so winding cannot cull it. All four hold 58 fps on an iPhone
  17 Pro, and each has a still on the homepage and in the README, taken off the
  phone with the scene opened over the nREPL.

  Random Values takes the high bits of the LCG rather than the low ones. The low
  bit of this generator alternates on every step, so a coin flip built from it
  lands heads, tails, heads, tails for ever.

- **A `ROADMAP.md`** for the port backlog and the gallery's growing pains. It
  sorts the 133 examples not yet ported by what a port would need, and lists the
  infrastructure work in view, such as splitting the drawing out of
  `raylib.gallery` and picking the nREPL port at run time. The 133 counts
  raylib-jlt's examples and not the gallery's scenes, because one `easings` scene
  covers three examples and the three Android scenes stand in for `flappy_bird`,
  `eyes` and `mouse_trail`.

- **A paragraph on `raylib.gesture`** in the porting guide's section on reading
  a finger.

### Changed

- **The counts and the porting guide**, which still said seventeen and
  forty-eight scenes in places. The README layout lists the directories that
  were missing, and the `proxy` comment in `deps.edn` now matches `proxy.sh`,
  which forwards 7888 to 7888 unless told otherwise.

### Fixed

- **Swiping inside a scene no longer scrolls the list behind it.** The gallery
  kept following the finger with the category list hidden behind the scene, so a
  swipe game left the list scrolled to somewhere arbitrary once it ended.
- **Breakout's ball climbs at the original's pace.** Its vertical speed is now
  scaled by `h / 450` and capped at a quarter of a brick per frame. It used to
  scale with the width, so on a tall phone the ball climbed slowly.
- **`pack.sh` no longer fails in silence.** Its configure and make steps wrote to
  `/dev/null`, so when a ChezScheme worktree made fresh after `/tmp` was cleared
  had its submodules empty, configure's "Source in zuo is missing" went nowhere
  and the log simply stopped. The script now checks the submodules first and
  names the command that fills them, keeps each step's output in a log, and
  prints the tail of that log when a step fails. The RUNBOOK has the two
  commands that make the worktree.

## 2026-09-05

### Added

- **`resize` and `align`**, forty-eight in all. `resize` drags a corner handle
  to resize a rectangle: the original tracks a mouse, which is always somewhere
  even with no button down, so it highlights on hover. A finger has no hover, so
  the handle highlights while held and the grab is sticky, kept until the finger
  lifts rather than re-tested each frame, which drops the rectangle the moment
  you drag faster than the corner follows. `align` places one word left, centre
  and right from `MeasureText`, which is the rare raylib call this project can
  bind directly, with no by-value struct to work around.

- **`raylib.host/draw-triangle`, which sorts out its own winding.** Getting it
  wrong has happened four times here: in `draw-ring`, in the ring scene's
  outline, nearly in `draw-gradient-quad`, and in this batch's resize handle,
  whose triangle scored +3600 and was culled. The symptom is identical every
  time and always misleading, because rlgl discards the triangle silently and
  what you see is a shape that is not there. One cross-product comparison per
  triangle ends the class.

- **Relicensed from zlib to EPL 2.0**, matching the rest of jlt-commons and jolt
  itself. zlib was chosen to match the graphics stack, since raylib-jlt, raylib
  and SDL2 are all zlib, but one exception across the organisation is harder to
  explain than that symmetry was worth. The `LICENSE` file is byte-identical to
  jolt's.

  This relicenses nothing that arrived under another licence, because those
  files are not ours to relicense: the whole is EPL 2.0 and each part keeps what
  it came with. Both inbound licences here are permissive and impose nothing EPL
  conflicts with. zlib's requirement that altered sources be plainly marked
  survives the change and the ports still satisfy it in their docstrings.
  `NOTICE` records all of it.

- **The scene list scrolls.** At forty-six scenes the Toys category is
  thirty-two cards, and `gallery-layout` sizes cards to FIT: it divides the
  height it is given by the row count, so more scenes means shorter cards rather
  than a longer list. Each card had shrunk to about 138 pixels.

  That file is one of the six verified byte-identical against the notebooks and
  does not change. It does not need to. `below-the-safe-area` already hands it a
  screen SHORTER than the real one and shifts the result down; scrolling hands
  it one TALLER and shifts the result up. The pure function lays out a
  comfortable grid for a screen that does not exist, and the host moves a window
  over it. Cards are now at least 16% of the shorter side, about 193 pixels.

  A list that scrolls cannot open a card on press, because at press time there
  is no way to know whether a drag is starting. So a press begins a gesture,
  movement scrolls, and the release opens a card only if the finger stayed
  within a slop of 1.8% of the shorter side. The hit test uses where the gesture
  STARTED rather than where it ended, since the release frame does not carry a
  reliable position.

- **`drag!`, a synthetic drag to go with `tap!`.** A drag is several frames of a
  finger moving while down, which `tap!` cannot express: it is a press and a
  release with nothing between. Without this the scroll could only be tested by
  a person swiping, and a person cannot swipe while iPhone Mirroring holds the
  device screen locked.

- **`clipbox`**, forty-six in all, and the host now hands each scene its safe
  region. rlgl keeps a single scissor rectangle, so a scene calling
  `BeginScissorMode` replaces the host's safe-area one outright rather than
  nesting inside it, and would be free to paint over the status bar it had just
  been moved clear of. `clip-rect` intersects instead, and returns nil for a
  miss rather than a negative width, because raylib reads a negative width as an
  enormous unsigned one and clips nothing at all.
- **`bezier` and `fan`**, forty-five in all. `bezier` is a cubic curve whose far
  end follows a finger, with its control polygon drawn so the shape is
  explained rather than just shown. The handles are derived from the two
  endpoints rather than dragged separately, because a finger has one position
  and derived handles stay well behaved everywhere, including with the finger
  on the anchor. `fan` is sixteen spokes at sixteen widths, from under a pixel
  to a fat bar, which is a direct test of thick-line winding: a spoke that
  fails to draw is a visible gap at a known width.
- **`bars`**, forty-three in all. Five bars with independent rounding on each
  end, each filled with a horizontal gradient. raylib has no call for this and
  the C example builds it from rlgl by hand: a triangle fan over a walked
  outline, where a corner of roundness 0 contributes coincident points whose
  triangles have no area and vanish, so one loop draws a square, a lozenge and
  everything between with no special case.

### Fixed

- **Every tap was read as a scroll, the first time scrolling shipped.** Travel
  was accumulated on the release frame as well as on movement, and raylib
  answers `GetTouchX` and `GetTouchY` with whatever it last had even when no
  finger is down. On device that put a motionless tap's travel at 1941 pixels,
  far past the slop, so nothing opened. Travel accumulates on `:down` only.

- **A fractional scroll offset killed the process.** `gallery-layout` rounds its
  rectangles to ints and the offset is added afterwards, so a fractional one
  made a card's `:y` a double, which `DrawRectangle`'s `[:int :int :int :int
  :uint]` binding rejects at the FFI boundary. Reachable from any real swipe,
  found by the synthetic one, which interpolates in doubles. `clamp` returns a
  long now.

- **The inset bands kept the previous screen's drawing.** A scene's own
  `ClearBackground` is subject to the scissor, because it becomes a `glClear`
  and `glClear` respects it, so the bands outside the safe region held whatever
  was there before: for a scene opened from the gallery, the card grid. In
  portrait those bands are thin strips behind the status bar and it went
  unnoticed for weeks. The phone rotated to landscape mid-session, which puts
  186 pixels down each side, and the stale cards were unmissable. The host now
  clears the full screen before raising the scissor.

- **`rounded` and `vecangle`**, forty-two in all. `DrawRectangleRounded` takes a
  Rectangle by value and cannot be bound here, so the shape is assembled from
  two overlapping rectangles and four quarter disks, and the radius breathes
  between square and fully round so both degenerate ends are visible. `vecangle`
  fills the signed angle between two vectors as an arc: an unsigned angle is a
  distance, a signed one is a rotation, and the readout swinging through zero
  into negative is the point.
- **`splines`**, forty in all. Three bases over one set of control points, so
  the difference is visible rather than described: Catmull-Rom passes through
  its points, the cubic Bezier and the uniform B-spline do not. The original
  cycles between them with SPACE, which a phone has no way to press, and drawing
  all three at once is the stronger comparison anyway. raylib's own DrawSpline*
  take Vector2 arrays by value and are unbindable here, so the curves are
  evaluated in Clojure, which is what makes sharing the points possible at all.
- **`ring`**, thirty-nine in all. A breathing annulus with a stroked outline,
  and the scene that would have caught the winding bug on day one: its whole
  subject is `draw-ring`, so a culled annulus is a blank screen rather than a
  missing detail. Its sweep is bounded to 330 degrees, where the original's own
  formula reaches 380 and laps its own start, shading the overlap twice.
- **`gradient`**, thirty-eight in all. The original draws one vertical band and
  calls itself a check that two by-value Colors survive one FFI call, which does
  not apply here since colours cross packed into a uint. So it grew into what the
  technique is actually for: raylib spends three entry points on this shape, V, H
  and Ex, and one rlgl quad with a colour per vertex covers all three, because a
  vertical gradient is the four-corner case with the top pair equal. Four bands,
  the last one turning so the interpolation is visible as motion.
- **Three more, thirty-seven in all.** `clockgrid`, `sector` and `palette`, all
  from raylib-jlt and all reusing the rlgl helpers added for the analog clock
  rather than needing new FFI.
- **`clockgrid`** spells the time with 144 little clock faces whose 288 hands
  swing into position each second. Laid out three rows of two rather than the
  original's single row of six: twenty-four faces across a portrait phone leaves
  each about 50px, too small to read as a clock. Sized from the height, since
  eighteen cells stacked bind before eight across do.
- **`sector`** is a pie slice degrading into a triangle. The example is really
  about one piece of arithmetic: raylib needs one segment per 90 degrees and
  computes that floor itself when handed fewer, so asking for 2 across 270
  degrees gives you 3. Driven by a timer, since the original uses raygui sliders
  and a phone has neither those nor keys.
- **`palette`** draws all 25 colours raylib names, with the label's ink chosen
  by Rec. 601 luma. Two colours in the palette come out on opposite sides of
  luma and a flat channel average: ORANGE reads dark by average and light by
  luma, MAGENTA the reverse, and luma matches the eye both times.

### Fixed

- **`draw-ring` had never drawn anything, in either scene that called it.** Its
  triangles were wound the wrong way round, and rlgl culls back faces, so every
  one was silently discarded: no error, no warning, nothing on screen. The
  analog clock shipped yesterday without the bezel it was supposed to have, and
  I reported it as working after mistaking its tick marks for the ring in a
  screenshot. The tell is the sign of the cross product of a triangle's first
  two edges, which the working `draw-line-ex` gets negative and `draw-ring` got
  positive. Reversing the winding fixed both scenes at once.

- **The clock of clocks ran at 6 fps, and getting it to 59 took three drafts.**
  The bezels were `draw-ring` at 20 segments, which is 120 vertices each and
  about 17,000 FFI calls a frame for 144 of them. `DrawCircleLines` draws the
  same circle in one call and needs no rlgl helper, because unlike `DrawRing` it
  takes no by-value Vector2. That alone reached 50.

  Then the 288 hands were collected into a vector and drawn in one batch, to
  save the 864 calls that 288 separate begin/colour/end triples cost. It
  measured **slower**, 47, because the batch had traded those calls for 288
  vector allocations. Keeping the batch and emitting vertices straight from the
  loop with nothing allocated reached 59. See
  [performance](docs/guide/performance-on-a-phone.md).

## 2026-09-04

### Added

- **Two more, thirty-four in all.** `multitouch` and `analog`, both from
  raylib-jlt.
- **`multitouch` does what its original documented itself as unable to do.**
  The desktop example reads point zero through the scalar `GetTouchX` and
  `GetTouchY` pair, because `GetTouchPosition` returns a Vector2 by value and
  the desktop binding set had no path for it. Its docstring calls that the
  honest limit. A phone is the machine the example was written about, we already
  bind the by-value return, so this draws every finger with its own id, colour
  and trail. Trails are keyed by touch id rather than list index: raylib does
  not promise ids are 0..n-1, and when a middle finger lifts the survivors shift
  down an index, which would make two fingers swap trails.

  Colours come from a slot rather than from the id, which real hardware forced.
  iOS derives touch ids from object pointers, so they are 8-byte aligned: four
  fingers reported 809313472, 809313920, 809314368 and 809317952 in one run and
  809133248, 809134144, 809136832, 809137728 in another, and 163292352 onward
  after a relaunch put them in a different address range. Every one divisible by
  8. `(mod id 8)` sent all four to the same entry and every finger drew in the
  same blue. No hash fixes it either. With eight colours and four fingers even a
  perfectly uniform hash puts them in distinct slots only 8/8 x 7/8 x 6/8 x 5/8
  of the time, which is 41%, and a murmur3 finalizer measured exactly that. So
  the lowest free slot is assigned instead, which is exact rather than
  probabilistic for up to eight simultaneous touches.
- **`raylib.host/draw-ring` and `draw-line-ex`**, built from the rlgl primitives
  already bound for the colour wheel rather than as new FFI. raylib's own
  `DrawRing` and `DrawLineEx` take their points as by-value Vector2, and
  raylib-jlt solves it the same way upstream for the same reason.

### Fixed

- **The analog clock's second hand jittered backward mid-second.** Its
  sub-second fraction accumulated frame deltas and wrapped on its own schedule,
  which drifts against the clock it subdivides. Measured on device: at second 12
  the fraction ran .60 .68 .75 .81 .88 .95 and then wrapped to .02 while the
  second was still 12, so the hand jumped back 5.6 degrees and forward again at
  the tick. It is now re-phased to zero whenever the second changes, and the
  test asserts the hand never moves backward across a run of ticks.

- **Three more, thirty-two in all.** `bullets` (a three-armed spiral of
  straight lines), `collision` (an overlap that follows a finger) and `dashed`
  (a dashed line to wherever you touch). The last two are the first scenes here
  that read the pointer for themselves.

### Fixed

- **The pointer now arrives in the coordinates a scene draws in.** Scenes are
  handed the safe region as their whole screen and the host translates their
  drawing into place, but the touch coordinates were still being passed through
  measured from the top of the physical screen. A tap at screen y 1500 reached
  the scene as 1500 rather than 1314, so anything drawn under a finger appeared
  186 pixels below it.

  This was a regression the safe-area change introduced nine commits earlier the
  same day, not a latent bug. `touch-trail` and `following-eyes` have read the
  pointer since the first commit here, and both were silently broken by it. The
  gallery's own hit-testing was never affected, since it compares a screen point
  against a layout already moved into screen coordinates, so the app kept
  feeling fine. See [the safe area](docs/guide/the-safe-area.md).

### Changed

- **`bullets` flies at 8 rather than 4.** A faster bullet spends fewer frames on
  screen, so the settled count halves from 693 to 349 and the scene goes from 38
  to 59 fps. The cost was never the drawing: emptying the draw method entirely
  moved 700 bullets from 35 to 43 fps and swapping circles for squares moved it
  to 39, so it is `advance` rebuilding one map per bullet per frame that pays.

- **Four more, twenty-nine in all.** `angles` (a ring of spokes and one that
  turns), `writing` (a message typing itself), `balls` (gravity and bouncing)
  and `sequence` (a shuffled permutation of bar heights).
- **`easings`, the twenty-fifth scene.** All fifteen easing curves plotted at
  once, each running a dot on a shared clock so the only differences are the
  curves. raylib-jlt splits this across three examples; a phone screen holds
  the lot, and seeing them together is the point.
- **`raylib.easings`**, the curve library, shared rather than part of the scene
  for the same reason the upstream examples share one header. Keeps raylib's
  `(t b c d)` signature deliberately.
- **Seven more scenes, twenty-four in all.** `life` and `automata`, then
  `colorwheel` and `unitcircle`, then `clock`, `piechart` and `logoanim`. All
  ports from raylib-jlt, and all of them needed resizing for a phone rather
  than transcribing.
- **libc for wall-clock time.** raylib's `GetTime` counts seconds since
  `InitWindow`, which is the wrong clock for a scene that shows the time, so
  the host binds `time()` and `localtime()`. Both resolve in libSystem, which
  is already in the process, so no extra linking. Verified against the Mac's
  own clock to the second.
- **rlgl's matrix stack and scissor rectangle**, which is how scenes came to
  respect the safe area without any of them changing.

### Fixed

- **The test runner's exit code did nothing, so the CI test job was never a
  gate.** It called `(resolve 'System/exit)` and fell through to `nil` when
  that returned nil, which it always does: Clojure's `resolve` looks up vars
  and a static method is not one. A failing test printed FAIL and exited 0.
  Proved by adding a deliberately failing test and reading the exit code, then
  fixed by calling `System/exit` directly, which works under both
  `clojure -M:test` and `jolt -M:test`. Both gates now stop the run: a failing
  test, and a test file on disk the runner does not list.
- **`bounce-in` was inconsistent at a zero duration.** It is defined by
  reflecting `bounce-out`, and the reflection inverts that degenerate case:
  every other curve reads `d=0` as finished and returns `b+c` where `bounce-in`
  returned `b`. A caller cannot know which curve it is holding, so it is
  special-cased.
- **Scenes drew under the Dynamic Island and the home indicator.** The insets
  had been read since the beginning and only `:top` was used, and only for the
  gallery's own cards. A scene is now handed metrics whose screen IS the safe
  region, and the host translates and clips it into place. No scene knows a
  safe area exists.
- **`unitcircle` crashed on its own first frame**, asking for element 0 of an
  empty trace. Every test in that file called `advance` first, so none had seen
  the state the scene actually starts in.
- **The automaton covered 39% of the display.** Cell width and row height were
  one number, and a cell wide enough to draw quickly is too wide to need many
  rows. They are chosen separately now.
- **Hairline strokes.** A one-pixel line reads fine in the 800-pixel window
  these examples were written for and disappears on a 1206-pixel screen.

### Measured

- **A filled rectangle is much cheaper than the guide assumed.** The automaton
  draws 2551 of them a frame at 58 fps, against the "roughly a thousand
  primitives" figure that came from lines and circles. Both new grid scenes had
  been tuned down twice against a ceiling they were never near.
- Every scene added here holds 57 to 59 fps on an iPhone 17 Pro.

### Changed

- The capture tooling moved to b12n-screen-grab's `contrib/ios-device` and
  `contrib/montage`, so the next iOS project does not write it a third time.

## 2026-09-03 (later)

### Added

- **Two scenes, seventeen in total.** `lorenz`, the Lorenz attractor with a
  hand-rolled orbiting camera since this host binds none of raylib's 3D, and
  `tesseract`, a 4D hypercube projected 4D to 3D to 2D.
- **`raylib.scenes.lorenz/trail-length` is an atom**, not a `def`, so the
  performance ceiling can be swept from a REPL without a rebuild.

### Fixed

- **The test runner silently skipped new test files.** Its namespace list was
  hardcoded, so two new `*_test.cljc` files never ran and `Ran 23 tests` read
  as a pass. The runner now compares the list against what is on disk and fails
  if a test file is not listed.
- **Lorenz allocated per segment**, a fresh vector from both `project` and
  `trail-colour`, about 2400 a frame. 18 fps before, 31 after, 58 once the
  trail was sized from the sweep.
- **The trail could open on half a butterfly.** A 450-point window is short
  enough to sit inside one lobe: 499 frames of 600 straddle both. `warm` now
  runs on until the window spans the divide.

### Measured

- Lorenz's ceiling: flat at vsync to a 480-point trail, slipping at 500,
  falling away past 600. Default set to 450 at 58 fps.
- Euler at `dt` 0.006 tracks a near-exact reference; 0.009 and 0.012 inflate
  the attractor, so a bigger step does not buy more trajectory per point.

## 2026-09-03

The whole project, in one day. raylib and SDL2 rendering on an iPhone from
Clojure, fifteen scenes, and the measurements that shaped them.

### Added

- **zlib licence**, in `LICENSE`, matching raylib-jlt, raylib and SDL2. See
  `NOTICE` for third-party provenance and for the one blocker that has to clear
  before this repository can be published or transferred.
- **raylib 6.0 and SDL2 on iOS.** raylib ships no iOS platform layer and needs
  none: built `PLATFORM=SDL` with `GRAPHICS_API_OPENGL_ES2` against an SDL2
  compiled for iOS, SDL's own iOS support becomes the platform layer. Both go
  into the executable as static archives, because `jolt build --target` emits
  the whole binary and Chez owns `main`.
- **`raylib.host`**, the owner loop. Chez's `main` calls `SDL_UIKitRunApp`
  through the FFI, SDL runs `UIApplicationMain`, and its delegate calls back on
  thread 0, where the raylib loop lives for the life of the app.
- **Fifteen scenes** in four categories. Six are pure `.cljc` from
  jasalt/jolt-android-experiment, carried byte-identical with their sha256
  verified. Ten are ports from jlt-commons/raylib-jlt.
- **Two-level navigation.** `poc.raylib.gallery-ui` fits every card on one
  screen by dividing the height by the row count, which is unreadable past a
  handful. Categories sit above the pure contract instead, reusing
  `gallery-layout` with a different id list, so that file stays byte-identical.
- **A live seam.** `raylib.host/state` reads what the running scene holds,
  `on-next-frame!` queues work onto the main thread, and
  `raylib.gallery/tap!` injects a synthetic tap, so an editor can drive the app
  without a finger.
- **cider-nrepl**, opt-in behind `-A:cider`. The default build has no
  dependencies at all and that is worth keeping.
- **Capture tooling**, since moved out to a separate capture project so the
  next iOS app can reuse it. Every image in `docs/images` was taken off a real
  device by it, unattended, and Flappy Bird plays itself for the camera,
  flapped by a loop running inside the app.
- **Two guides.** `performance-on-a-phone.md` and `porting-an-example.md`.

### Fixed

- **`GetFPS` was being misread, not misbehaving.** It is a per-frame sampler:
  each call advances a 30-slot ring by one, so calling it from a 300-frame
  summary returns `1/(n * frame-time/30)` and decays toward the truth. The host
  computes its own rate from frame times now. Documented upstream in
  raysan5/raylib#6120.
- **Draw loops rewritten as indexed loops.** `partition` and `map-indexed` per
  frame cost three to four times what the FFI calls they fed did. spirograph
  went from 14 fps to 52 at the same point count, drawing the same lines.
- **A `safe-area-top` that could never work.** `SDL_GetDisplayUsableBounds`
  returns `uiscreen.bounds` on iOS and knows nothing of safe areas, so it
  always answered 0 against a real 62 pt inset. Removed; scenes ask UIKit.

### Upstream

- **jolt-lang/jolt#829, merged.** `sa-os-family` called a native iOS build
  Linux, so `tarm64ios` took Linux's `SIGCHLD`, `EAGAIN`, `O_NONBLOCK` and
  `struct stat` offsets on a Darwin system. The maintainer extended the fix to
  `build.ss`'s own `bld-tgt-osx?`.
- **raysan5/raylib#6120, open.** Documents that `GetFPS` must be called every
  frame.

### Not filed

- **raylib's SDL platform binding no framebuffer.** The code reading is correct
  and the conclusion drawn from it is not: measured on a device, both the
  drawable framebuffer and the colour renderbuffer are already bound at swap
  time whether or not this host binds anything. The report was written and
  withdrawn before filing. See `docs/guide/performance-on-a-phone.md`.
