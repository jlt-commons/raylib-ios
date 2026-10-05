# Contributing

Thanks for looking. raylib-ios is the platform: the host loop, the gallery shell,
the single-scene runner, the bindings and the build tools. The scenes that run on
it live in [raylib-ios-demo](https://github.com/jlt-commons/raylib-ios-demo), so **a new scene goes there**, and the
second most useful contribution is a correction to something in `docs/guide/`
that turns out not to be true.

## Before anything

```sh
jolt -M:test        # the shipping runtime; also runs the jolt-only smoke tests
clojure -M:test     # the JVM job; skips the jolt-only namespaces and says so
clj-kondo --lint src test --fail-level warning
clojure-lsp format --dry && clojure-lsp clean-ns --dry
shellcheck -S warning tools/ios/*.sh docs/check-site.sh
```

All of these are gates in CI and all run in seconds. The tests need no raylib, no
SDL and no phone. raylib-ios-demo pins this repo by sha. To try a platform change
there before it is pushed, point its `common/deps.edn` at your checkout with
`{:local/root "../raylib-ios"}`, run its gates (`bb gen --check`, `bb doctor` and
its own test suites), and don't commit that line.

## What goes where

- **Here:** anything a scene stands on. `host.clj` and its bindings, `frame.clj`,
  the gallery shell (`gallery.clj`) and the pure gallery under `gallery/`,
  `runner.clj`, the texture and render-target code, the pure helpers
  (`soft3d`, `camera2d`, `stick`, `gesture`, `scroll`, `easings`, `perlin`, `texel`),
  `tools/ios/` and the guides about the platform. Hello is the one scene, as the
  platform's proof.
- **raylib-ios-demo:** every other scene, its draw method and its tests, the
  generated registry the gallery app passes to `gallery/run!`, and the scene
  catalog. Adding a scene there is one line in `demos.edn` and `bb gen`; its
  CONTRIBUTING has the steps.

The shell holds no scene list. `(gallery/run! {:scenes [...] :categories [...]})`
takes both as data, and `net.b12n.raylib-ios.live/live-run!` does the same with an
nREPL. Its own `-main` runs `gallery/platform-gallery`, which is Hello alone. A
test fails if a scene namespace other than Hello comes back to this repo.

## What a scene can use

A scene is a pure namespace that returns `{:id :title :init :update :draw
:dispose}`, plus a `draw-scene!` method in its own draw namespace. It must not
require `net.b12n.raylib-ios.host` or call raylib outside that method. Derive
geometry from `(:screen metrics)` rather than hardcoding pixels. The metrics a
scene receives are the safe region, not the whole display, and they differ
between devices. See [the safe area](docs/guide/the-safe-area.md). Use a seeded
generator rather than `GetRandomValue`, so a scene replays identically and its
tests are possible.

These are the platform helpers a scene reaches for, and how each works.

**Text layout.** A pure scene can't call `MeasureText`, so a scene that wraps or
centres text takes a `measure` function `(fn [s size] -> px)`. Every scene's
input carries one as `:measure`, which is raylib's own text width. A scene that
keeps text widths in its state, as the `strings` scene does, reads it in `init` and
`update`, with a fallback so tests can run without the FFI. A scene that lays
text out only in `dimensions` still gets `measure` from its draw method, as
`rectbounds` does. If the draw method caches the layout, the
cache key has to hold every input to the computation, which here means the text,
the box size, the font size and the wrap mode.

**Camera examples.** A 2D camera example draws inside
`net.b12n.raylib-ios.host/with-camera-2d`, which pushes `rlTranslatef`, `rlRotatef` and
`rlScalef` on top of the gallery's own translate in place of `BeginMode2D`,
because `BeginMode2D` loads the identity matrix and would throw that translate
away. The camera math itself stays pure, in `net.b12n.raylib-ios.camera2d`.

**3D examples.** A 3D example is projected in software, because the project
binds no 3D mode. The pure part is `net.b12n.raylib-ios.soft3d`: `field` lays out the caption
and the 3D view under Back, `fit-camera` widens the original's fovy for a
portrait field, the builders (`cube`, `cube-wires`, `grid`, `lines`, `sphere`,
`plane`, `billboard`, `cylinder`, `cylinder-wires`, `capsule`, `capsule-wires`)
project as they go, and `finish` sorts the result far to near. The draw method
hands that list to `net.b12n.raylib-ios.host/draw-3d!`, scissored to the field. Nothing
behind the near plane is drawn and every triangle keeps rlgl's front winding. A
scene that draws many small boxes may bypass `finish` with its own paint order,
as raylib-ios-demo's `wavecubes` and `pointcloud` do, if the order is provably right for that
scene.

**Textures.** A scene that draws a texture stays pure and never touches FFI.
It builds a spec map, `{:w :h :wrap :filter :pixel :version}`, and the
`draw-scene!` method hands it to `net.b12n.raylib-ios.texture`. `:pixel` is `(f x y)` and
answers a packed colour, `r | g<<8 | b<<16 | a<<24`, which is what
`net.b12n.raylib-ios.texel/pack` builds. `:wrap` is `:clamp` (the default) or `:repeat`,
`:filter` is `:nearest` (the default) or `:linear`, and `:version` is optional.

- `id!` takes `(scene-id key spec)` and answers the texture's id, uploading on
  first use. Use the scene's own registry id as `scene-id`: a texture filed
  under any other id is freed and uploaded again on every frame. A later call
  with a new `:version` rewrites the pixels in place; the same version is a
  lookup.
- `quad!` draws an id as one quad, the stand-in for `DrawTexturePro`
  (`:x :y :width :height :u0 :v0 :u1 :v1 :rotation :origin-x :origin-y :tint`).
  `triangles!` draws `[x y u v ...]` triples and winds each triangle itself, so
  rlgl's back-face culling cannot drop one.
- `band!` refreshes a few rows of a texture a frame, for a picture that has to
  change continuously. The phone writes a texel in at most about 2.6 us (derived), so a whole
  128 by 128 rewrite every frame stalls it; `band!` costs the band alone, at
  the price of a moving seam between fresher and older rows, which the scene
  should say.
- `perlin-texture!` takes `(scene-id key {:w :h :offset-x :offset-y :scale})` and
  answers the id of raylib's own `GenImagePerlinNoise` image, made in C and
  uploaded straight from the buffer raylib allocated (which it then frees). Use
  it for a whole image: `net.b12n.raylib-ios.perlin/perlin-grey` is the tested model, a texel
  at a time, and costs about 17 us a texel under laptop jolt. The same spec
  values answer the same id with no work while the scene is open.
- GLES2 repeats only a power-of-two texture. `:repeat` on any other size throws
  before anything is allocated, so a non-power-of-two sheet is `:clamp`.
- Test the pixel fn texel by texel against the original, over the whole
  texture, using `net.b12n.raylib-ios.texel` (which follows raylib 6.0's `ImageDraw*` loops,
  quirks included). Pixel fns that run on the phone should not allocate a vector
  per texel; `texel/pack4` takes the four channels as arguments.
- Make a static spec a `def` or a `delay` in the scene's draw namespace, built once. A
  spec without a `:version`, handed back as the identical object, is kept as a
  filled buffer, so a reopen costs about one frame instead of a refill. A spec
  built fresh each frame is refilled each time. Only one scene's textures are on
  the GPU at once: `net.b12n.raylib-ios.texture/enter!` frees the rest when a scene opens.
- A first open can pause, because the pixels are computed and uploaded then.
  Measure it on the phone and say so in the scene's catalog row in raylib-ios-demo,
  and in the scene's docstring, when the largest frame is over 100 ms.

**Render textures.** A scene that draws into an off-screen framebuffer stays
pure too. The `draw-scene!` method asks `net.b12n.raylib-ios.texture` for a target, draws
into it, then draws the target back. `LoadRenderTexture` and `BeginTextureMode`
can't be called, because they pass and return structs by value, so `net.b12n.raylib-ios.texture`
rebuilds them from rlgl's scalar calls.

- `target!` takes `(scene-id key {:w :h :depth?})` and answers
  `{:fbo :texture :w :h}`. Use the scene's own registry id. Ask for the target
  every frame and don't keep the map: a new size or `enter!` frees the
  framebuffer it names, and a new size starts empty. `:depth?` defaults to true
  and is part of the target's identity. Pass `:depth? false` for any pass that
  draws 2D, since rlgl keeps the depth test off outside `BeginMode3D`.
- `with-target!` takes `(rt safe f)`, where `safe` is the scissor rectangle the
  gallery has up. `f` draws untranslated in the target's own pixels, from (0, 0).
  The batch is flushed on the way in and out, and everything is restored on every
  path, throws included. Do the passes before drawing anything of the scene's own.
- On iOS the screen is SDL's drawable framebuffer, not 0, and every rlgl
  framebuffer call binds 0. A scene never calls them directly: `target!` and
  `with-target!` rebind SDL's. The gallery's safe-area translate also lives in
  rlgl's `transform` matrix, not modelview, and `with-target!` handles that too.
- GL stores a target bottom-up, so draw it back with `quad!` and
  `:v0 1.0 :v1 0.0`, or flip v yourself as Magnifying Glass's disc does.
- Don't call `BeginScissorMode` inside `f`, nest `with-target!`, or draw `rt`'s
  own texture inside its pass (reading and writing one texture at once is
  undefined in GL). `f` can assume modelview is identity on entry.
- A persistent canvas, such as Mouse Painting's, keeps its picture on the GPU
  and replays only the frame's marks, which the pure scene hands over as data.
  A resize or a turn of the phone gives an empty target, so the scene has to
  say what that clears.
- `with-blend-factors!` takes `(src dst equation f)` and runs `f` under those GL
  enums, then puts the default blend mode back. It doesn't nest. Top Down Lights
  uses it for GL_MIN and GL_MAX.
- `perlin-texture!` answers the id of raylib's own `GenImagePerlinNoise` image.
  The Image comes back by value, which jolt takes as a buffer passed first, so
  it is the one native call here with a struct return. `net.b12n.raylib-ios.perlin` is the
  pure model that tests it.
- A field-sized RGBA8 target is about 10.7 MB in portrait. Give the total in the
  scene's catalog row in raylib-ios-demo, and measure the first open on the phone.
- Testing: raylib-ios-demo's gallery smoke test checks SDL's framebuffer, the scissor
  and the full matrix snapshot after every frame, and `texture_test.clj` here checks the
  pass itself. `test/net/b12n/raylib_ios/rlgl_model.clj` models rlgl's
  matrix state, so a pass can be tested for what it leaves behind, and a stub
  that skips it would hide the transform and modelview split.
- The native Perlin test needs libraylib. Plain `jolt -M:test` and CI skip it and
  say so. To run it against Homebrew's raylib 6.0:
  `jolt -Sdeps '{:jolt/native [{:name "raylib" :darwin ["/opt/homebrew/lib/libraylib.dylib"]}]}' -M:test`.

**Thumb-sticks.** A scene that steers with a relative stick tracks it with
`net.b12n.raylib-ios.stick`, which follows one finger by its touch id and never adopts a
finger that was already down. raylib-ios-demo's `freecam`, `yawpitchroll` and `boxcollide` use it.

**Gestures.** A scene that wants raylib's own recogniser reads `:raylib-gesture`
from its input, which is the code from `GetGestureDetected`, as raylib-ios-demo's
`gestures` scene does. It only ever reports one finger, so pinch never appears there.

## Changing the shell

`test/net/b12n/raylib_ios/gallery_smoke_test.clj` covers the platform gallery (a walk
from the category to Hello over stubbed raylib, and Hello running 120 frames of
touch), a scene that throws, and the scroll rule.
raylib-ios-demo's smoke test runs every scene through the same shell for 120 frames
over stubbed raylib. `guard-scene` and `next-scroll` are public so both can reach
them.

Adding a test namespace means listing it in
`test/net/b12n/raylib_ios/test_runner.clj`. The runner also fails if a `*_test`
file exists that it does not list, so forgetting is caught rather than silently
skipped.

## The performance budget

Read [performance on a phone](docs/guide/performance-on-a-phone.md) before
sizing anything. The short version:

- An indexed `loop`/`recur` beats every sequence function in a draw loop, by
  about 3.5x for identical output.
- Allocation costs more than the FFI call. A scene making 2400 calls into C a
  frame holds 59 fps; one allocating a vector per point does not.
- Roughly a thousand primitives is comfortable for lines and circles, and
  filled rectangles are much cheaper than that suggests: 2551 a frame at 58 fps.
- Measure on a device. Every number in these docs came off hardware, and several
  of them replaced a confident wrong guess.

## Running on a phone

You need a paired iPhone and an Apple Development identity. `tools/ios/deps.sh`
builds the archives, `build.sh` builds the app, `deploy.sh` installs it, and
`tools/ios/RUNBOOK.md` has the failure modes.

For live development, [a REPL on the phone](docs/guide/a-repl-on-the-phone.md).
Note the trap there about release builds and redefinition.

## Style

Match the file you are editing. Comments explain **why**, since what is
generally visible in the code, and a comment recording a measurement or a wrong
turn is worth more than one restating the line below it.

Prose in this repository avoids em-dashes.

## Licensing

EPL 2.0, and by contributing you agree your work is licensed the same way. It
was zlib until 2026-09-05; the change was to match the rest of jlt-commons.

Parts of `host.clj`, `gallery.clj`, `touch.clj` and `link.clj` derive from
[glimmer-ios-demo](https://github.com/statonjr/glimmer-ios-demo), which is MIT,
and Hello is a port from raylib-jlt, which is zlib. Those keep their own
licences: a change of outbound licence cannot relicense someone else's
copyright. `NOTICE` has the detail and reproduces every notice.

One inherited obligation applies to anyone porting a scene, in raylib-ios-demo or
here. zlib requires that
altered source versions be plainly marked as such, so a port names its original
in its docstring and says what changed. That is not a stylistic convention here,
it is the licence.

## Reporting something wrong in the docs

Especially welcome. Several claims in `docs/guide/` were written confidently and
then disproved by a measurement, including an upstream bug report this project
filed against raylib and withdrew. If something reads as true and is not, that
is worth an issue on its own.
