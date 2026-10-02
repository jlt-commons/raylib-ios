(ns raylib.scenes.rotcube
  "A cube turning in place, ported from raylib-jlt's `rotating_cube`, which is
  raylib's rlgl matrix-stack demonstration (zlib licence).

  The original looks from (4, 4, 4) at the origin through a 45 degree
  perspective camera, draws a grid of 10 and then a red cube of side 2 inside
  rlPushMatrix/rlPopMatrix, after `rlRotatef(angle, 1, 0, 0)` and
  `rlRotatef(angle * 0.7, 0, 1, 0)`, where the angle is the frame number in
  degrees. Here the same two turns are `raylib.soft3d/compose` of two
  `rotate-axis` calls in the same order, so as in rlgl the y turn applies to the
  cube first. The camera, the grid, the cube, its colour and its shading
  (`raylib.soft3d/cube`'s default is raylib-jlt's `cube!`) are the original's.
  Nothing reads a frame time, like the original: the angle grows one degree an
  update.

  There is no input and so no control to map. The original's caption sits below
  Back, with the 3D view in the field under it, full width to the bottom. The
  view is `raylib.soft3d`'s `[x y w h]` viewport on that field. The original's
  45 degree fovy is kept while the field is at least as wide as 800x450. In a
  narrower field `raylib.soft3d/fit-camera` widens it so the original's
  horizontal view still fits, and the field shows more above and below.

  The grid never changes, so `grid-list` builds it once per layout and
  `scene-list` adds the cube to a copy each frame. The state holds only
  `:frame`. Colours are `[r g b a]` vectors."
  (:require [raylib.soft3d :as s3]))

(def caption-text "A cube rotating via the rlgl matrix stack")

(def background-colour [245 245 245 255])
(def caption-colour [80 80 80 255])
(def cube-colour [230 41 55 255])

(def y-rate "The y turn as a fraction of the x turn. The original's." 0.7)

(defn geometry
  "The layout for `metrics`' `:screen`: `:size` the caption's text size, `:pad`
  the gap around it and `:viewport` the field `[x y w h]` below the caption,
  which is below Back and runs to the bottom."
  [metrics]
  (let [[w h] (:screen metrics)
        back-bottom 120
        size (max 16 (int (* 0.03 (min w h))))
        pad (max 8 (int (* 0.5 size)))
        text-y (+ back-bottom pad)
        ftop (+ text-y size pad)]
    {:size size
     :pad pad
     :text-y text-y
     :viewport [0.0 (double ftop) (double w) (double (- h ftop))]}))

(defn dimensions
  "`geometry` plus the caption as `{:s :x :y :size}`, in `:lines` as well so a
  test can check it fits. The size is cut back from `geometry`'s when the
  caption would cover more than 0.92 of the width. `measure` is
  `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [size pad text-y]
         [_ _ w _] :viewport
         :as geo} (geometry metrics)
        widest (measure caption-text 100)
        size (max 8 (min size (int (/ (* 0.92 w 100.0) widest))))
        line {:s caption-text
              :x pad
              :y text-y
              :size size}]
    (assoc geo :caption line :lines [line])))

(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

(defn- field-aspect [{[_ _ w h] :viewport}] (/ w h))

(defn camera
  "The original's camera, (4, 4, 4) looking at the origin with fovy 45, fitted
  to `dims`' field by `raylib.soft3d/fit-camera`."
  [dims]
  (s3/fit-camera {:position [4.0 4.0 4.0]
                  :target [0.0 0.0 0.0]
                  :up [0.0 1.0 0.0]
                  :fovy 45.0
                  :projection :perspective}
                 original-aspect (field-aspect dims)))

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
