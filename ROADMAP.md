# Roadmap

What's next for raylib-ios, roughly in order. Shipped work moves to Done at the
bottom, and the dated detail lives in `CHANGELOG.md`.

## Port backlog

[raylib-jlt](https://github.com/jlt-commons/raylib-jlt) has 187 examples. 134 of
them are in the gallery as of 2026-10-04, which leaves 53. That counts
examples and not scenes: the gallery has 129 scenes ported from raylib-jlt, one
of which (`easings`) covers three examples, and the three Android scenes are
versions of `flappy_bird`, `eyes` and `mouse_trail`, so 129 + 2 + 3 = 134. They sort into
three groups by what a port would need. The grouping comes from reading each
example's docstring and the raylib calls it makes, so a closer read may move a
few of them.

**Ready to port, no new bindings (0).** The list is empty after batch 7.

**A few new scalar bindings (0).** The group is empty after batch 8.

**Blocked for now (53).** The 2026-10-02 triage sorted the then 95 unported
examples by what a port would need. About 32 could be rebuilt with what is
already bound, and batches 9 to 13 ported 30 of them, so 2 remain. Two more
needed only a pair of scalar blend-mode bindings, and batch 13 added them. The
other 51 need something the project doesn't bind or the phone doesn't have:
shaders, texture and image pipelines, 3D models and meshes, desktop windowing,
the keyboard, gamepad or clipboard, files, or audio. 2 + 0 + 51 = 53. These
remain rewrite-ready:

- 3D, projected in software: `dna_helix`. A faithful one built in 12.7 ms on
  the laptop, about 42 times the 0.30 ms a scene is sized to there, so it waits
  for a rewrite that draws far less.
- `reasings` is already ported: it is the easing header, and `raylib.easings`
  carries it, so no scene is left to add for it.

## Infrastructure

- **Split the drawing out of `raylib.gallery`.** It is 4235 lines and grows
  by about thirty a scene, so splitting it is due. The `draw-scene!` methods
  could move to their own namespace. The file now has 47 `*-cache` atoms with
  the same eight-line body, so a `memo-last` helper belongs in the same split.
  The two newest caches hold a whole draw list (`geoshapes-cache` and
  `split3d-list-cache`), so under `DEV_BUILD=1` a redefined `scene-list` or
  soft3d builder does not show until the screen or a player changes. Clearing
  both when a scene opens would fix that.
- **Lift the virtual window into `raylib.vwindow`.** Viewport Scaling and
  Window Letterbox carry the same `window`, `handle`, `clamp`, start geometry
  and drag step, about 55 lines each. A pure `.cljc` next to `raylib.stick`
  would hold `window`, `handle`, `clamp`, the start geometry and `drag-step`,
  with Viewport Scaling passing its button claim in. It takes about an hour and
  is worth doing when a third scene would use it.
- **Rebalance the categories.** Toys holds 108 of the 132 scenes, and Games has 11, so a scroll
  through Toys is long. raylib-jlt's own groups (core, shapes, text) would be a
  starting point.
- **Add a batch `soft3d/cubes` builder.** 3D Split Screen carries `flat-cubes`,
  a 100-line unrolled copy of `soft3d/cube {:shade :flat}` that tests hold to the
  live builder. A builder that takes many cubes (shared matrix destructuring, no
  scratch array) could serve it, Basic Voxel's unit-face fallback and any future
  grove scene, and then `flat-cubes` goes.
- **Share the corners of a billboard.** A `soft3d/billboard-parts` that works out
  the corners once for a billboard and derives its parts from them would make the
  ring of discs in Billboard Rendering affordable (0.62 ms as strips today,
  against a target of 0.30) and make Directional Billboard cheaper.
- **Extract `call-blended!` into `raylib.blend`** when a third blend scene
  arrives. Blend Modes and Particles Blending each carry the same six lines and a
  test for them, and none is planned yet.
- **Pick the Basic Voxel axis at an edge.** A place at an edge or corner hit
  chooses an arbitrary axis from `max-key` over a tied normal.
- **Tighten some tests, each optional.** The capsule sphere case checks only
  counts, Directional Billboard's grid-behind claim is untested, 3D Split Screen's
  `tol` and near-plane `>=` mutants survive, and the text-fit tests use a
  synthetic measure across the project.
- **Pick the nREPL port at run time.** `tools/ios/live.sh` and
  `tools/ios/proxy.sh` default to 7888. `proxy.sh` already refuses a busy port,
  but it asks `lsof` rather than attempting the bind, writes no port file, and
  `exec`s iproxy, so a cleanup trap would never run.
- **Silence the per-frame `GetWindowScaleDPI` warning.** The gallery logs
  `WARNING: GetWindowScaleDPI() not implemented on target platform` every frame.
  raylib's `BeginScissorMode` calls `GetWindowScaleDPI` on Apple, and raylib's
  SDL2 platform doesn't implement it. Found on device on 2026-09-30. The noise is
  harmless because clipping is correct, but it floods the console.
- **Share the touch helpers.** `raylib.gesture` now holds `down?`, `in-rect?`,
  `back-region`, `in-back-region?` and the tap, swipe and long-press `track`,
  and the batch 3 scenes use it. The batch 1 and 2 scenes (`touchball`,
  `rlgltriangle`, `particles` and `breakout`) still carry their own copies of
  some of it. `breakout` still carries its own Back region. `breakout`,
  `particles` and `rlgltriangle` each carry a closed `in-rect?`. All four still
  carry their own press-or-down predicate. Migrating them
  is worth doing as its own task, with one catch: those `in-rect?` copies are
  closed on the right and bottom edge, while `gesture/in-rect?` is half-open, so
  a touch exactly on that edge changes sides by one pixel.
- **Lift the relative thumb-stick into `raylib.gesture`.** `stick-dir`,
  `next-stick` and `knob` are the same text in `survivors` and `nudge`. One home
  means a fix to the stick lands once. `raylib.stick` now exists and `freecam`,
  `yawpitchroll` and `boxcollide` use it, so this is a matter of moving
  `survivors` and `nudge` onto it, or onto the part of it that fits.
- **Fix the painter's grid-first order.** `raylib.soft3d` paints the grid under
  every face, so grid lines that cross a cube's lower half are hidden where a
  depth buffer would show them. A below-grid, above-grid order would fix it.
- **Close freecam's two-finger gap.** When two fingers land on the same frame,
  the look path can still adopt a finger that was already down.
- **Move `nudge` and `splitscreen` onto `raylib.stick`.** Each keeps its own
  tracker, and the shared one could replace both.
- **Give the older games the idle `:press` exception.** Tetris, Asteroids,
  Snake, Space Invaders and Pong store `gesture/idle` on the frame the game ends
  even when that frame is a fresh press, so a tap landing on that one frame is
  lost. A held touch can't restart them, which is right. Pac-Man and Vampire
  Survivors keep a press that lands on the ending frame.
- **Try batch 7 with a real finger.** The device pass drove it with synthetic
  touches, which never reach iOS, so whether swipes and thumb-sticks near the
  bottom edge fight the home gesture is still open. The small hint lines in
  Vampire Survivors, Keyboard Ball and Mouse Wheel, about 8 to 10 pt, are worth
  a look too.
- **Synthetic input once produced an extra event.** Twice in the batch-7 pass,
  a `drag!` sent soon after a `tap!` was followed by one more swipe or tap than
  was queued. A later session could not reproduce either: drag, tap and drag in
  Undo Redo logged exactly at gaps from 1 s down to 0.25 s, and the Strings
  Management sequence came out right. The Strings one may have been a hold
  aimed at a particle that had already moved. Watch for it rather than fix it.
- **Decide Minesweeper's rules.** The port keeps the original's: a tap on a
  flagged cell reveals it, and the first tap can hit a mine. A flag guard would
  stop the first, and first-tap safety the second.
- **Decide Breakout's pace.** The ball takes about 5 s from the paddle to the
  bricks on a portrait phone, since its speed scales with the width.
- **3D Split Screen from outside the grove.** It reads 58 fps idle and while a
  player walks through the trees, but 31 while a player walks out of the grove
  and looks back, with all 121 trees in view. The original doesn't clamp the
  players. A lossless cut or a disclosed one is still to choose.
- **Basic Voxel freezes the phone on a tap.** Measured on the phone on
  2026-10-03: a tap holds a single frame for about 285 ms, because the mesh
  rebuild takes 309 ms, and 574 ms for a hollowed block. A hollowed block's
  frame build is also 23 ms, against 11.6 ms for the full block. The fix is an
  incremental mesh update on a tap, so a tap changes the faces around one voxel
  instead of rebuilding them all. The four blend modes were checked by eye on
  2026-10-03 and look as each mode should.
- **Try the cameras with a real finger.** The camera pinch and twist in 2D
  Camera and 2D Camera Zoom, and the two thumbs in 2D Split Screen, have not
  been driven by a hand on the phone.
- **See DRAG and DOUBLETAP in Input Gestures.** A real finger logged TAP, HOLD,
  SWIPE RIGHT and SWIPE DOWN, so raylib's recogniser does fire under the SDL
  host. DRAG and DOUBLETAP have not shown up yet, and pinch can't, because SDL
  feeds raylib one finger at a time.
- **Texture first opens pause.** The first open after launch holds the largest
  single frame at about 1.0 s for Sprite Animation, 0.8 s for Polygon Drawing,
  0.5 s for Procedural Textures, 0.2 s for Srcrec Dstrec and Sprite Button, and
  0.17 s for Raw Data (phone measurements, 2026-10-04). The cost is the pixel fn
  and the per-texel write, so the cure is a faster fill or a cached upload.
  Texture Tiling, Srcrec Dstrec, Sprite Button, Npatch Drawing and the noise
  panel of Procedural Textures also still pack a vector per texel (`texel/pack`
  where `texel/pack4` would do), which is part of that fill cost. The
  catalog discloses the pauses for now.
- **Audit the licence wording on the older scenes.** The 33 scenes that
  predate the texture arc, and NOTICE's "Ported, and altered" section, call
  their raylib-jlt originals zlib. raylib-jlt relicensed to EPL 2.0 on
  2026-09-05, so an original added to it after that date is EPL 2.0. The ten
  texture scenes already say which.
- **`jolt live` fails to build under jolt v0.8.16.** The phone's live build
  stops with `variable error is not bound` in the `jolt.socket.native` unit,
  and so do builds after that release. v0.8.15 builds it, and the 2026-10-04
  device pass ran on it. Pin the live build to v0.8.15 until jolt fixes it.
- **Move the CI jolt pin forward** from 0.8.6. The suite is green on 0.8.15.

## Done

- 2026-10-04: the texture arc closed, with ten Toys scenes, `textiling`,
  `srcrec`, `spritebutton`, `npatch`, `texpoly`, `texproc`, `spriteanim`,
  `texcurve`, `rawdata` and `screenbuf`, which make a hundred and thirty-two
  scenes and give Toys a hundred and eight. They add `raylib.texture` and
  `raylib.texel`. A device pass read all ten at 58 or 59 fps, and a scene opened
  before reopens in about one frame, except the noise of Procedural Textures and
  Raw Data's live panel (0.14 to 0.15 s).
- 2026-10-03: batch 13 closed, with eight Toys scenes, `blendmodes`,
  `blendparticles`, `billboard`, `dirbillboard`, `texcube`, `geoshapes`, `voxel`
  and `doom`, which make a hundred and twenty-two scenes and give Toys ninety-eight.
  They add `BeginBlendMode` and `EndBlendMode` and the soft3d `billboard`,
  `cylinder` and `capsule` builders, and 3D Split Screen got faster. A device pass
  read every scene at 52 to 60 fps, Doom-like Raycaster at 58, except 3D Split
  Screen from outside its grove (31).
- 2026-10-02: batch 12 closed, with seven Toys scenes drawn without textures,
  `bunnymark`, `bgscroll`, `spritestack`, `pixelperfect`, `vpscaling`,
  `letterbox` and `fogofwar`, which make a hundred and fourteen scenes and give
  Toys ninety. None of the seven has run on the phone. Bunnymark clamps its
  bunnies into the field when the phone turns.
- 2026-10-02: the finger helpers copied into Free Camera, First-Person Camera and
  First-Person Maze moved into `raylib.stick` as `follow-pair` and
  `begin-owners`, with no change in behaviour.
- 2026-10-02: batch 11 closed, with four Toys scenes, `fpcamera`, `fpmaze`,
  `split3d` and `spheres`, which make a hundred and seven scenes and give Toys
  eighty-three. `raylib.soft3d/cube` now uses the fast box emitter lifted from
  Waving Cubes. The four scenes have not run on the phone. `dna_helix` is
  deferred, since a faithful port built in 12.7 ms on the laptop.
- 2026-10-02: batch 10 closed early, with `raylib.soft3d/sphere` and `plane` and
  three Toys scenes, `wavecubes`, `solarsystem` and `pointcloud`, which make
  a hundred and three scenes and give Toys seventy-nine. Waving Cubes draws 81
  of the original's 196 columns and Point Cloud 400 of its 1500 points, because
  the first versions ran at 15 and 9 fps. Both paint far to near by their own
  order and skip `finish`. The other five planned examples stay on the list above.
- 2026-10-02: the ten 3D scenes measured on an iPhone 17 Pro, all at 58 or 59 fps,
  and 3D Picking's tap-to-pick confirmed on the phone.
- 2026-10-02: ten raylib-jlt 3D examples, `rotcube`, `camera3d`, `ortho`,
  `spincubes`, `worldscreen`, `wireframes`, `freecam`, `yawpitchroll`,
  `boxcollide` and `picking`, which make a hundred scenes and give Toys
  seventy-six. They are projected in software through the new `raylib.soft3d`,
  drawn by `raylib.host/draw-3d!`, and `raylib.stick` tracks the thumb-stick by
  touch id. No new bindings. None has run on the phone yet.
- 2026-10-02: six scenes, `camera2d`, `camera_2d_mouse_zoom`,
  `camera_2d_platformer`, `camera_2d_split_screen`, `input_gestures` and
  `helitorus`, which make ninety scenes and give Toys sixty-six. They add
  `raylib.camera2d`, `raylib.host/with-camera-2d` and the `:raylib-gesture`
  input key, and empty the scalar-binding group. All six run at 58 or 59 fps
  on an iPhone 17 Pro. Helitorus starts at a detail of 64 because 260 ran at 19
  fps, and it no longer toggles culling, since rlgl draws at the batch flush.
- 2026-10-01: every scene measured on an iPhone 17 Pro, all 84 at 52 to 60 fps,
  so the catalog has no blank fps cell. Batch 7 has stills, and the device pass
  fixed four things the phone showed.
- 2026-10-01: Asteroids keeps firing while fire is held, and Strings Management
  glues only on a slow drop.
- 2026-09-30: a jolt-only smoke test that loads the gallery, checks its four
  registration points and runs every scene for 120 frames.
- 2026-09-30: a guard so a scene that throws returns to its list instead of
  ending the app.
- 2026-10-01: seven scenes, `vampire_survivors`, `pacman`, `core`, `input`,
  `wheel`, `undo_redo` and `strings_management`, which make eighty-four scenes,
  give Toys sixty and Games eleven, and empty the ready list. Every scene's input
  now carries `:measure`.
- 2026-10-01: seven scenes, `logo`, `text`, `inline_styling`,
  `outlines_thickness`, `shapes`, `ellipse_collision` and
  `basic_screen_manager`, which make seventy-seven scenes and give Toys
  fifty-five.
- 2026-10-01: six touch toys, `input_virtual_controls`, `starfield_effect`,
  `easings_box`, `easings_testbed`, `rectangle_bounds` and `rlgl_color_wheel`,
  which make seventy scenes and give Toys forty-eight.
- 2026-10-01: four arcade ports, `pong`, `space_invaders`, `tetris` and
  `asteroids`, which make sixty-four scenes and give Games nine. A touch held
  through a game's end no longer restarts it.
- 2026-09-30: `raylib.gesture` for tap, swipe and long-press, and four scenes on
  it, Bouncing Ball, Snake, 2048 and Minesweeper, which make sixty scenes and
  give Games five. Swiping inside a scene no longer scrolls the list behind it.
- 2026-09-30: four touch-driven ports, `mouse` as Touch Ball, `rlgl_triangle`,
  `particles` and `breakout`, which make fifty-six scenes and give Games its
  second game. Breakout and the triangle start over cleanly on a rotation.
- 2026-09-30: four ports, `delta_time`, `random_values`, `format_text` and
  `triangle_strip`, which make fifty-two scenes.
