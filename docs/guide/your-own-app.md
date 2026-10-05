# Your own app

How to start an iOS raylib app of your own on this platform, from an empty
directory. The platform is a dependency: you write a scene, a draw method and a
four-line entry namespace, and raylib-ios supplies the host loop, the bindings
and the build scripts.

Everything below was run in a scratch project before it was written down.

## The project

```clojure
;; deps.edn
{:paths ["src"]
 :deps {net.b12n/raylib-ios {:git/url "https://github.com/jlt-commons/raylib-ios.git"
                             :git/sha "<full 40-hex sha>"}}}
```

Use the full sha, because tools.deps refuses a short one without a tag. While
you are changing raylib-ios itself, point at your checkout instead, and don't
commit that line:

```clojure
:deps {net.b12n/raylib-ios {:local/root "../raylib-ios"}}
```

## A scene

A scene is a map of pure functions, `{:id :title :init :update :draw :dispose}`.
Each returns `[state events]`, and nothing in it polls, draws or holds a native
value, which is why it tests on the build host with no raylib. Hello, in
`src/net/b12n/raylib_ios/scenes/hello.cljc`, is the smallest real one and the
model to copy.

```clojure
;; src/my/scene.cljc
(ns my.scene)

(defn- init [_] [{:n 0} [[:scene/init :my-scene]]])
(defn- update-scene [state _input] [(update state :n inc) []])
(defn- draw [state _input] [state []])
(defn- dispose [state] [state [[:scene/dispose :my-scene]]])

(defn scene []
  {:id :my-scene :title "My Scene"
   :init init :update update-scene :draw draw :dispose dispose})
```

## A draw method

Drawing is a method of the `draw-scene!` multimethod in
`net.b12n.raylib-ios.draw`, which is public API. It is dispatched on the
scene's `:id` and receives `id`, `state` and an env map `{:k :m :safe}`. The
`net.b12n.raylib-ios.draw` docstring and the README list the env keys and the
helpers (`clear-to!`, `color`, `stroke!`, `outline!`, `draw-caption!`,
`draw-in-field!`, `host-measure`).

```clojure
;; src/my/scene/draw.clj
(ns my.scene.draw
  (:require [net.b12n.raylib-ios.draw :as draw]
            [net.b12n.raylib-ios.host :as rl]))

(defmethod draw/draw-scene! :my-scene [_id state {:keys [m]}]
  (let [[w h] (:screen m)]
    (draw/clear-to! [20 24 40 255])
    (rl/draw-text (str "frame " (:n state)) (quot w 4) (quot h 2) 48
                  (draw/color [255 255 255 255]))))
```

Geometry comes from `(:screen m)`, which is the safe region and not the
display. The host has already moved the origin to the safe region's corner.

## The app namespace

```clojure
;; src/my/app.clj
(ns my.app
  (:require [my.scene :as scene]
            [my.scene.draw]
            [net.b12n.raylib-ios.runner :as runner]))

(defn -main [& _]
  (runner/run! (scene/scene)))
```

The draw namespace has to be required by the app, because the multimethod
dispatches on an id and nothing else loads it. `runner/run!` shows one scene
full screen. For several scenes behind a menu, call
`(net.b12n.raylib-ios.gallery/run! {:scenes [...] :categories [...]})` instead.

Check it loads, on the host, before touching a phone:

```
$ jolt -e "(require 'my.app) (println :loaded)"
:loaded
```

## Finding the tools

The build, deploy and live scripts live in raylib-ios's `tools/ios`, which
arrives with the dependency. Find the checkout from the classpath: it is the
`jolt -Spath` entry ending in `src` whose parent holds `tools/ios/build.sh`.

```sh
TOOLS=$(for p in $(jolt -Spath | tr ':' '\n'); do
  d=$(cd "$p/.." 2>/dev/null && pwd)
  [ -f "$d/tools/ios/build.sh" ] && echo "$d"
done)
echo "$TOOLS"
```

With a `:git` dependency this is a directory under `~/.jolt/gitlibs`. With the
`:local/root` above it printed the raylib-ios checkout itself.

## Build and deploy

Run both from your project's directory. The scripts build the project they are
run from, and put `RaylibIOS.app` there.

```sh
NS=my.app TARGET=device sh "$TOOLS/tools/ios/build.sh"
UDID=<hardware udid> sh "$TOOLS/tools/ios/deploy.sh"
```

`DRY_RUN=1` on the build prints what it resolved and stops, which checks the
paths without needing the SDL2 and raylib archives built:

```
$ DRY_RUN=1 NS=my.app TARGET=device sh "$TOOLS/tools/ios/build.sh"
dry-run: PROJECT_DIR=/path/to/myapp
dry-run: NS=my.app
dry-run: ALIAS=
dry-run: TARGET=device
dry-run: APP=/path/to/myapp/RaylibIOS.app
dry-run: INFO_PLIST=<tools checkout>/tools/ios/Info.plist
dry-run: PACK=/tmp/raylib-ios/pack/device
dry-run: SDL_A=~/dev/sdl2-ios-dev/lib/libSDL2.a
dry-run: RAYLIB_A=~/dev/raylib-ios-dev/lib/libraylib.a
```

The archives come from `SDK=device jolt deps` in raylib-ios, once, and the
README covers that setup.

## Live development

For a REPL on the running phone, make a second entry namespace whose `-main`
calls `net.b12n.raylib-ios.runner.live/live-run!` with the same scene, then
`NS=my.app.live sh "$TOOLS/tools/ios/live.sh"` and, in another terminal,
`UDID=<hardware udid> sh "$TOOLS/tools/ios/proxy.sh"`. Device builds with the
nREPL need jolt v0.8.15 for now; see the RUNBOOK. [A REPL on the
phone](a-repl-on-the-phone.html) has the rest.
