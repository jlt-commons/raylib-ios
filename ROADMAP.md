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
- Before that, 137 scenes were built and measured here, in batches. The batch-by-batch
  record is in `CHANGELOG.md`, and the scenes' own roadmap is in raylib-ios-demo.
