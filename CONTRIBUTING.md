# Contributing

Thanks for looking. The most useful contribution is another scene, and the
second most useful is a correction to something in `docs/guide/` that turns out
not to be true.

## Before anything

```sh
clojure -M:test     # 72 tests, no device needed
clj-kondo --lint src test
```

Both are gates in CI and both run in seconds. The tests need no raylib, no SDL
and no phone, which is the entire point of the scene contract.

## Adding a scene

Four touchpoints. Miss one and the failure is quiet rather than loud, which is
why they are listed rather than discovered.

**1. A pure namespace** at `src/raylib/scenes/<name>.cljc`.

It must not require `raylib.host` or call raylib. It is state and the functions
that advance it, and it returns:

```clojure
(defn scene []
  {:id :yourscene :title "Your Scene"
   :init init :update update-scene :draw draw :dispose dispose})
```

Derive geometry from `(:screen metrics)` rather than hardcoding pixels. The
metrics a scene receives are the safe region, not the whole display, and they
differ between devices. See [the safe area](docs/guide/the-safe-area.md).

Use a seeded generator rather than `GetRandomValue` if the scene is random.
Every existing one uses the same LCG, which makes a scene replay identically and
makes its tests possible.

**2. A test** at `test/raylib/scenes/<name>_test.cljc`.

Prefer properties over golden values: that a rotation preserves length, that
slices tile a circle exactly, that a trail stays bounded. Two of this project's
own tests shipped wrong expectations that a property would have caught.

**Test the first frame.** A scene crashed in production asking for element 0 of
an empty buffer, past 1400 assertions, because every test called `advance`
before looking at anything.

**3. Register it** in `src/raylib/gallery.clj`: add the require, add
`(yours/scene)` to the `scenes` vector, and add its `:id` to a category's
`:scenes` list. All three, or it will not appear.

**4. A `draw-scene!` method**, also in `raylib.gallery`. This is the only place
raylib gets called. Drawing reads the state the scene produced and calls
`rl/draw-line` and friends.

Then add it to `test/raylib/test_runner.clj`, which lists its namespaces
explicitly. It also fails if a `*_test` file exists that it does not list, so
forgetting is caught rather than silently skipped.

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
and the scenes are ports from raylib-jlt, which is zlib. Those keep their own
licences: a change of outbound licence cannot relicense someone else's
copyright. `NOTICE` has the detail and reproduces every notice.

One inherited obligation applies to anyone adding a scene. zlib requires that
altered source versions be plainly marked as such, so a port names its original
in its docstring and says what changed. That is not a stylistic convention here,
it is the licence.

## Reporting something wrong in the docs

Especially welcome. Several claims in `docs/guide/` were written confidently and
then disproved by a measurement, including an upstream bug report this project
filed against raylib and withdrew. If something reads as true and is not, that
is worth an issue on its own.
