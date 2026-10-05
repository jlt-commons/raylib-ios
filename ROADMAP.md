# Roadmap

What's next for raylib-ios, the platform, roughly in order. The scenes moved to
[raylib-ios-demo](https://github.com/jlt-commons/raylib-ios-demo) on 2026-10-04, and so did the port backlog and every
item about a single scene; its ROADMAP has them. Shipped work moves to Done at the
bottom, and the dated detail lives in `CHANGELOG.md`.

## Bindings the project does not have

Some raylib-jlt examples wait on these, and the port backlog that counts them is in
raylib-ios-demo's ROADMAP.

- **Shaders.** The examples that pair a render texture with a shader
  (`postprocessing` and the rest) wait on shader bindings.
- **The Image API.** `perlin-texture!` shows jolt can take raylib's `Image` by
  value as a return, but the `Image*` calls that take one as an argument still
  can't be called.

## Infrastructure

- **`resolve-insets` can leave three edges unfetched.** A positive `inset-top`
  at init stops bottom, left and right from ever being fetched. It is not
  reachable while the phone starts at 0.
- **`prepare` does not check the categories.** Every category entry should name
  a scene in `:scenes`, and the behaviour on a bad id needs deciding: a throw at
  launch kills the app on the phone with the message only on the console.
- **Add a batch `soft3d/cubes` builder.** 3D Split Screen carries `flat-cubes`,
  a 100-line unrolled copy of `soft3d/cube {:shade :flat}` that tests hold to the
  live builder. A builder that takes many cubes (shared matrix destructuring, no
  scratch array) could serve it, Basic Voxel's unit-face fallback and any future
  grove scene, and then `flat-cubes` goes.
- **Share the corners of a billboard.** A `soft3d/billboard-parts` that works out
  the corners once for a billboard and derives its parts from them would make the
  ring of discs in Billboard Rendering affordable (0.62 ms as strips today,
  against a target of 0.30) and make Directional Billboard cheaper.
- **Pick the nREPL port at run time.** `tools/ios/live.sh` and
  `tools/ios/proxy.sh` default to 7888. `proxy.sh` already refuses a busy port,
  but it asks `lsof` rather than attempting the bind, writes no port file, and
  `exec`s iproxy, so a cleanup trap would never run.
- **Silence the per-frame `GetWindowScaleDPI` warning.** The gallery logs
  `WARNING: GetWindowScaleDPI() not implemented on target platform` every frame.
  raylib's `BeginScissorMode` calls `GetWindowScaleDPI` on Apple, and raylib's
  SDL2 platform doesn't implement it. Found on device on 2026-09-30. The noise is
  harmless because clipping is correct, but it floods the console.
- **Fix the painter's grid-first order.** `net.b12n.raylib-ios.soft3d` paints the grid under
  every face, so grid lines that cross a cube's lower half are hidden where a
  depth buffer would show them. A below-grid, above-grid order would fix it.
- **Synthetic input once produced an extra event.** Twice in the batch-7 pass,
  a `drag!` sent soon after a `tap!` was followed by one more swipe or tap than
  was queued. A later session could not reproduce either: drag, tap and drag in
  Undo Redo logged exactly at gaps from 1 s down to 0.25 s, and the Strings
  Management sequence came out right. The Strings one may have been a hold
  aimed at a particle that had already moved. Watch for it rather than fix it.
- **`jolt live` fails to build under jolt v0.8.16.** The phone's live build
  stops with `variable error is not bound` in the `jolt.socket.native` unit,
  and so do builds after that release. v0.8.15 builds it, and the 2026-10-04
  device pass ran on it. Pin the live build to v0.8.15 until jolt fixes it.
- **App lifecycle.** Nothing handles `SDL_APP_WILLENTERBACKGROUND` or
  `SDL_APP_LOWMEMORY`. Top Down Lights with 16 lights keeps about 182 MB of
  render targets resident (arithmetic, not measured), background included.
  Stop drawing in the background and free targets on a memory warning. Locking
  the phone and switching apps in that scene has not been tried.
- **`texture/release!`.** Top Down Lights shrinks a dropped light's mask to
  1x1, which keeps a framebuffer and a texture until the scene is left. A
  release that frees one key is the honest primitive.
- **Move the CI jolt pin forward** from 0.8.6. The suite is green on 0.8.15.

## Done

- 2026-10-04: the scenes moved to raylib-ios-demo, and this repo keeps the platform
  and Hello. The gallery shell takes its scenes and categories as data, with
  `net.b12n.raylib-ios.gallery/run!` and `net.b12n.raylib-ios.live/live-run!`. See
  `CHANGELOG.md`.
- 2026-10-04: the render-textures arc closed, with five Toys scenes, `rendertex`,
  `fbrender`, `mousepaint`, `magnify` and `toplights`, which make a hundred and
  thirty-seven scenes and give Toys a hundred and thirteen. They add
  `net.b12n.raylib-ios.texture/target!`, `with-target!`, `with-blend-factors!` and
  `perlin-texture!`, which calls raylib's own `GenImagePerlinNoise`, and
  `host/draw-circle-gradient`. A device pass of the final build read all five at 58 fps,
  Top Down Lights included with 16 lights, and its first open pauses 75 ms.
- 2026-10-04: the texture arc closed, with ten Toys scenes, `textiling`,
  `srcrec`, `spritebutton`, `npatch`, `texpoly`, `texproc`, `spriteanim`,
  `texcurve`, `rawdata` and `screenbuf`, which make a hundred and thirty-two
  scenes and give Toys a hundred and eight. They add `net.b12n.raylib-ios.texture` and
  `net.b12n.raylib-ios.texel`. A device pass read all ten at 58 or 59 fps, and a scene opened
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
  First-Person Maze moved into `net.b12n.raylib-ios.stick` as `follow-pair` and
  `begin-owners`, with no change in behaviour.
- 2026-10-02: batch 11 closed, with four Toys scenes, `fpcamera`, `fpmaze`,
  `split3d` and `spheres`, which make a hundred and seven scenes and give Toys
  eighty-three. `net.b12n.raylib-ios.soft3d/cube` now uses the fast box emitter lifted from
  Waving Cubes. The four scenes have not run on the phone. `dna_helix` is
  deferred, since a faithful port built in 12.7 ms on the laptop.
- 2026-10-02: batch 10 closed early, with `net.b12n.raylib-ios.soft3d/sphere` and `plane` and
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
  seventy-six. They are projected in software through the new `net.b12n.raylib-ios.soft3d`,
  drawn by `net.b12n.raylib-ios.host/draw-3d!`, and `net.b12n.raylib-ios.stick` tracks the thumb-stick by
  touch id. No new bindings. None has run on the phone yet.
- 2026-10-02: six scenes, `camera2d`, `camera_2d_mouse_zoom`,
  `camera_2d_platformer`, `camera_2d_split_screen`, `input_gestures` and
  `helitorus`, which make ninety scenes and give Toys sixty-six. They add
  `net.b12n.raylib-ios.camera2d`, `net.b12n.raylib-ios.host/with-camera-2d` and the `:raylib-gesture`
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
- 2026-09-30: `net.b12n.raylib-ios.gesture` for tap, swipe and long-press, and four scenes on
  it, Bouncing Ball, Snake, 2048 and Minesweeper, which make sixty scenes and
  give Games five. Swiping inside a scene no longer scrolls the list behind it.
- 2026-09-30: four touch-driven ports, `mouse` as Touch Ball, `rlgl_triangle`,
  `particles` and `breakout`, which make fifty-six scenes and give Games its
  second game. Breakout and the triangle start over cleanly on a rotation.
- 2026-09-30: four ports, `delta_time`, `random_values`, `format_text` and
  `triangle_strip`, which make fifty-two scenes.
