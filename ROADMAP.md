# Roadmap

What's next for raylib-ios, roughly in order. Shipped work moves to Done at the
bottom, and the dated detail lives in `CHANGELOG.md`.

## Port backlog

[raylib-jlt](https://github.com/jlt-commons/raylib-jlt) has 187 examples. 50 of
them were in the gallery on 2026-09-30, which leaves 137. They sort into three
groups by what a port would need. The grouping comes from reading each
example's docstring and the raylib calls it makes, so a closer read may move a
few of them.

**Ready to port, no new bindings (34).** Most of these need a touch mapping
rather than anything from raylib.

- No input at all: `core`, `format_text`, `random_values`, `delta_time`,
  `logo`, `triangle_strip`, `text`, `inline_styling`, `outlines_thickness`.
- A finger stands in for the mouse: `mouse`, `breakout`, `particles`,
  `rlgl_triangle`, `rectangle_bounds`, `input_virtual_controls`, which already
  draws its own on-screen pad.
- A tap or a swipe stands in for a key: `bounce`, `basic_screen_manager`,
  `easings_box`, `easings_testbed`, `starfield_effect`, `wheel`, `input`,
  `rlgl_color_wheel`, `snake`, `game_2048`, `minesweeper` (long-press to flag),
  `undo_redo`, `strings_management` (the typing has to go).
- Games that need a touch control scheme designed first: `pong`,
  `space_invaders`, `tetris`, `asteroids`, `vampire_survivors`, `pacman`.

**A few new scalar bindings (8).**

- `DrawEllipse`: `shapes`, `ellipse_collision`. An rlgl fan would also do.
- `rlRotatef` and `rlScalef`, standing in for `BeginMode2D`'s by-value
  Camera2D: `camera2d`, `camera_2d_mouse_zoom`, `camera_2d_platformer`,
  `camera_2d_split_screen`. Pinch and pan suit these well.
- `SetGesturesEnabled` and `GetGestureDetected`: `input_gestures`. Whether
  raylib's gestures fire under the SDL iOS host is unverified.
- `helitorus`, possibly needing `rlDisableBackfaceCulling`.

**Blocked for now (95).** These need something the project doesn't bind or the
phone doesn't have: shaders (20), textures, images and render textures (34), 3D
cameras and models (21), desktop windowing (7), the keyboard, gamepad or
clipboard (6), files and drag-and-drop (4), and audio (3). A few could be
rewritten rather than ported. The wireframe 3D ones could project in software
the way `tesseract` does, and `screen_buffer`, `mouse_painting` and
`bunnymark` would work as plain rectangles.

## Infrastructure

- **Split the drawing out of `raylib.gallery`.** It is 1711 lines and every
  port adds about thirty. The `draw-scene!` methods could move to their own
  namespace before it reaches about 2500.
- **Rebalance the categories.** Toys holds most of the gallery, so a scroll
  through it is long. raylib-jlt's own groups (core, shapes, text) would be a
  starting point.
- **Pick the nREPL port at run time.** `tools/ios/live.sh` and
  `tools/ios/proxy.sh` default to 7888. `proxy.sh` already refuses a busy port,
  but it asks `lsof` rather than attempting the bind, writes no port file, and
  `exec`s iproxy, so a cleanup trap would never run.
- **Move the CI jolt pin forward** from 0.8.6. The suite is green on 0.8.15.

## Done

Nothing yet. This file started on 2026-09-30.
