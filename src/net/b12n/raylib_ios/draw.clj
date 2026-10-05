(ns net.b12n.raylib-ios.draw
  "The public draw API: the `draw-scene!` multimethod and the helpers that more
  than one draw method needs.

  A scene is a pure reducer (see net.b12n.raylib-ios.gallery.core) and draws
  through a method of `draw-scene!`, dispatched on the scene's `:id`:

      (defmethod draw/draw-scene! :my-scene [id state {:keys [k m safe]}]
        ...)

  A method receives
    `id`    the scene's id, which it was dispatched on;
    `state` the scene's own state, as its :update last returned it;
    `env`   `{:k :m :safe}`: `k` is the display scale (pixels per UIKit point),
            `m` the scene's metrics, whose `:screen` is the SAFE REGION's
            size (not the display's), and `safe` the safe region itself,
            `{:x :y :width :height}` in pixels.
  The host has already translated the origin to the safe region's corner and
  scissored to it, so a method draws in the coordinates its scene computed.

  Helpers:
    `color`         an `[r g b a]` vector as a raylib color; `WHITE` is one
    `clear-to!`     clear the frame to an `[r g b a]`
    `stroke!`       a line a few pixels wide
    `outline!`      a one pixel rectangle outline
    `draw-caption!` a `{:keys [s x y size]}` caption in a color
    `draw-in-field!` run a function scissored to a `[x y w h]` field
    `host-measure`  raylib's own text width, `(fn [s size])`

  The multimethod lives here and not in net.b12n.raylib-ios.gallery because
  every draw namespace requires it, and a scene's draw namespace must not need
  the gallery shell: the one-scene runner and every scene app load the draw
  without it. Renaming anything here breaks every project that draws scenes, so
  treat it as API."
  (:require [net.b12n.raylib-ios.host :as rl]))

(def WHITE (rl/rgba 255 255 255 255))

(defn color [[r g b a]] (rl/rgba r g b a))

;; --- drawing: the owner-affine half of the contract
(defmulti draw-scene! (fn [id _state _env] id))

(def host-measure
  "raylib's own text width, `(fn [s size] -> px)`, for scenes that need it."
  (fn [s sz] (rl/measure-text s (int sz))))

(defn stroke!
  "A line a few pixels wide, drawn as offset copies of a one-pixel one.

  raylib has DrawLineEx for this and it takes two Vector2 by value, which is a
  different and more expensive FFI path than every other call here. At three
  copies per line and a handful of lines per frame, this is cheaper than the
  binding would be.

  It matters because a one-pixel line is a hairline on a 1206-pixel-wide screen.
  The examples these scenes come from were written for an 800-pixel window,
  where the same line is half again as thick relative to everything else."
  [x1 y1 x2 y2 colour]
  (rl/draw-line x1 y1 x2 y2 colour)
  (rl/draw-line (inc x1) y1 (inc x2) y2 colour)
  (rl/draw-line x1 (inc y1) x2 (inc y2) colour))

(defn draw-in-field!
  "Run `f` with drawing clipped to the 3D field `[x y w h]`, in scene pixels.
  BeginScissorMode takes screen pixels, so the field is moved by the offset the
  gallery translates the scene by (the safe region's corner). Scissor does not
  nest: it replaces the gallery's own, so the safe region's is put back after."
  [safe [fx fy fw fh] f]
  (rl/begin-scissor-mode (int (+ (:x safe) fx)) (int (+ (:y safe) fy)) (int fw) (int fh))
  (try
    (f)
    (finally
      (rl/end-scissor-mode)
      (rl/begin-scissor-mode (:x safe) (:y safe) (:width safe) (:height safe)))))

(defn draw-caption! [{:keys [s x y size]} colour]
  (let [[r g b a] colour]
    (rl/draw-text s (int x) (int y) (int size) (rl/rgba r g b a))))

(defn clear-to! [[r g b a]] (rl/clear-background (rl/rgba r g b a)))

(defn outline!
  "A one pixel rectangle outline, the way DrawRectangleLines draws it. There is
  no rectangle-lines call bound, so it is four thin rectangles."
  [x y w h colour]
  (let [x (int x)
        y (int y)
        w (int w)
        h (int h)
        c (color colour)]
    (rl/draw-rectangle x y w 1 c)
    (rl/draw-rectangle x (+ y h -1) w 1 c)
    (rl/draw-rectangle x (inc y) 1 (- h 2) c)
    (rl/draw-rectangle (+ x w -1) (inc y) 1 (- h 2) c)))
