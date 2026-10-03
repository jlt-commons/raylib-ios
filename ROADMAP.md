# Roadmap

What's next for raylib-ios, roughly in order. Shipped work moves to Done at the
bottom, and the dated detail lives in `CHANGELOG.md`.

## Port backlog

[raylib-jlt](https://github.com/jlt-commons/raylib-jlt) has 187 examples. 116 of
them are in the gallery as of 2026-10-02, which leaves 71. That counts
examples and not scenes: the gallery has 111 scenes ported from raylib-jlt, one
of which (`easings`) covers three examples, and the three Android scenes are
versions of `flappy_bird`, `eyes` and `mouse_trail`, so 111 + 2 + 3 = 116. They sort into
three groups by what a port would need. The grouping comes from reading each
example's docstring and the raylib calls it makes, so a closer read may move a
few of them.

**Ready to port, no new bindings (0).** The list is empty after batch 7.

**A few new scalar bindings (0).** The group is empty after batch 8.

**Blocked for now (71).** The 2026-10-02 triage sorted the then 95 unported
examples by what a port would need. About 32 could be rebuilt with what is
already bound, and batches 9 to 12 ported 24 of them, so 8 remain. Two more need
only a pair of scalar blend-mode bindings. The other 61 need something the
project doesn't bind or the phone doesn't have: shaders, texture and image
pipelines, 3D models and meshes, desktop windowing, the keyboard, gamepad or
clipboard, files, or audio. These remain rewrite-ready:

- 3D, projected in software: `dna_helix`, `geometric_shapes` and
  `basic_voxel`. A faithful `dna_helix` built in 12.7 ms on the laptop, about 28
  times the phone's 0.45 ms budget, so it waits for a rewrite that draws far less.
- Textures drawn as primitives: `billboard_rendering`,
  `directional_billboard` and `textured_cube`.
- Other: `doom` and `reasings`.
- `blend_modes` and `particles_blending`, which need `BeginBlendMode` and
  `EndBlendMode`, two scalar bindings.

## Infrastructure

- **Split the drawing out of `raylib.gallery`.** It is 3365 lines and grows
  by about thirty a scene, so splitting it is due. The `draw-scene!` methods
  could move to their own namespace.
- **Lift the virtual window into `raylib.vwindow`.** Viewport Scaling and
  Window Letterbox carry the same `window`, `handle`, `clamp`, start geometry
  and drag step, about 55 lines each. A pure `.cljc` next to `raylib.stick`
  would hold `window`, `handle`, `clamp`, the start geometry and `drag-step`,
  with Viewport Scaling passing its button claim in. It takes about an hour and
  is worth doing when a third scene would use it.
- **Rebalance the categories.** Toys holds 90 of the 114 scenes, and Games has 11, so a scroll
  through Toys is long. raylib-jlt's own groups (core, shapes, text) would be a
  starting point.
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
- **Time the eleven new scenes on the phone.** The four of batch 11,
  First-Person Camera, First-Person Maze, 3D Split Screen and Bouncing Spheres,
  and the seven of batch 12, Bunnymark, Background Scrolling, Sprite Stacking,
  Smooth Pixel-Perfect, Viewport Scaling, Window Letterbox and Fog of War, have
  not run on a device, so their fps cells are empty. On the laptop Split Screen
  and Bouncing Spheres sit at the 0.45 ms budget, and a First-Person Maze
  corridor can run over it, so walk a long one. Fog of War is the one to watch,
  since its 0.30 ms leaves out about 3000 FFI calls, and the performance guide
  has 2400 calls holding 57 to 59 fps. Bunnymark is a stress test by design.
- **Try the cameras with a real finger.** The camera pinch and twist in 2D
  Camera and 2D Camera Zoom, and the two thumbs in 2D Split Screen, have not
  been driven by a hand on the phone.
- **See DRAG and DOUBLETAP in Input Gestures.** A real finger logged TAP, HOLD,
  SWIPE RIGHT and SWIPE DOWN, so raylib's recogniser does fire under the SDL
  host. DRAG and DOUBLETAP have not shown up yet, and pinch can't, because SDL
  feeds raylib one finger at a time.
- **Move the CI jolt pin forward** from 0.8.6. The suite is green on 0.8.15.

## Done

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
