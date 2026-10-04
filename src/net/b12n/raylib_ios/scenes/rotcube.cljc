(ns net.b12n.raylib-ios.scenes.rotcube
  "A cube turning in place, ported from raylib-jlt's `rotating_cube`, which is
  raylib's rlgl matrix-stack demonstration (zlib licence).

  The original looks from (4, 4, 4) at the origin through a 45 degree
  perspective camera, draws a grid of 10 and then a red cube of side 2 inside
  rlPushMatrix/rlPopMatrix, after `rlRotatef(angle, 1, 0, 0)` and
  `rlRotatef(angle * 0.7, 0, 1, 0)`, where the angle is the frame number in
  degrees. Here the same two turns are `net.b12n.raylib-ios.soft3d/compose` of two
  `rotate-axis` calls in the same order, so as in rlgl the y turn applies to the
  cube first. The camera, the grid, the cube, its colour and its shading
  (`net.b12n.raylib-ios.soft3d/cube`'s default is raylib-jlt's `cube!`) are the original's.
  Nothing reads a frame time, like the original: the angle grows one degree an
  update.

  There is no input and so no control to map. The original's caption sits below
  Back, with the 3D view in the field under it, full width to the bottom. The
  view is `net.b12n.raylib-ios.soft3d`'s `[x y w h]` viewport on that field. The original's
  45 degree fovy is kept while the field is at least as wide as 800x450. In a
  narrower field `net.b12n.raylib-ios.soft3d/fit-camera` widens it so the original's
  horizontal view still fits, and the field shows more above and below.

  The grid is drawn under every face (`net.b12n.raylib-ios.soft3d/finish` puts all grid
  lines first). The cube is centred on y = 0, so half of it is below the grid,
  and where raylib's depth buffer would show grid lines in front of its lower
  half they are hidden here. Splitting the cube at the grid is follow-up work.

  The grid never changes, so `grid-list` builds it once per layout and
  `scene-list` adds the cube to a copy each frame. The state holds only
  `:frame`. Colours are `[r g b a]` vectors."
  (:require [net.b12n.raylib-ios.soft3d :as s3]))

(def caption-text "A cube rotating via the rlgl matrix stack")

(def background-colour [245 245 245 255])
(def caption-colour [80 80 80 255])
(def cube-colour [230 41 55 255])

(def y-rate "The y turn as a fraction of the x turn. The original's." 0.7)

(defn dimensions
  "`net.b12n.raylib-ios.soft3d/field` plus the caption as `{:s :x :y :size}`, in `:lines` as
  well so a test can check it fits. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [size pad text-y]
         :as field} (s3/field metrics (measure caption-text 100))
        line {:s caption-text
              :x pad
              :y text-y
              :size size}]
    (assoc field :caption line :lines [line])))

(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

(defn camera
  "The original's camera, (4, 4, 4) looking at the origin with fovy 45, fitted
  to `dims`' field by `net.b12n.raylib-ios.soft3d/fit-camera`."
  [dims]
  (s3/fit-camera {:position [4.0 4.0 4.0]
                  :target [0.0 0.0 0.0]
                  :up [0.0 1.0 0.0]
                  :fovy 45.0
                  :projection :perspective}
                 original-aspect (:aspect dims)))

(defn angle-x "The turn about x in degrees: the frame number." [state]
  (* 1.0 (:frame state)))

(defn angle-y "The turn about y in degrees: 0.7 of `angle-x`." [state]
  (* (angle-x state) y-rate))

(defn transform
  "The cube's transform for `state`: rlRotatef about x, then about y, composed
  in the order the original calls them."
  [state]
  (s3/compose (s3/rotate-axis (angle-x state) 1.0 0.0 0.0)
              (s3/rotate-axis (angle-y state) 0.0 1.0 0.0)))

(defn grid-list
  "The grid of 10, spacing 1, as an unfinished draw list for `dims`' viewport.
  It depends only on the layout, so a draw can keep it."
  [cam dims]
  (s3/grid [] (s3/view-proj cam (:viewport dims)) 10 1.0))

(defn scene-list
  "The finished draw list for `state`: `base` (the `grid-list`) and the cube."
  [base state dims]
  (let [vp (s3/view-proj (camera dims) (:viewport dims))]
    (-> base
        (s3/cube vp (transform state) [0.0 0.0 0.0] 2.0 cube-colour)
        s3/finish)))

(defn- init [_] [{:frame 0} [[:scene/init :rotcube]]])
(defn- update-scene [state _] [(update state :frame inc) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :rotcube]]])

(defn scene []
  {:id :rotcube
   :title "Rotating Cube"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
