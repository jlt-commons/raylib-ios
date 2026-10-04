(ns net.b12n.raylib-ios.scenes.spincubes
  "A row of cubes each spinning in place with a phase offset, ported from
  raylib-jlt's `spinning_cubes` (zlib licence).

  The original looks from (0, 6, 10) at the origin through a 45 degree
  perspective camera, draws a grid of 12 and then five cubes of side 1 at
  x = -4, -2, 0, 2 and 4, height 0.5, coloured red, orange, green, blue and
  violet. Each sits in rlPushMatrix/rlPopMatrix after `rlTranslatef(x, 0.5, 0)`
  and `rlRotatef(angle, 0.3, 1, 0)`, where the angle is the frame number times 2
  plus 30 for each cube's index. Here that is `net.b12n.raylib-ios.soft3d/compose` of
  `translate` and `rotate-axis` in the same order, so as in rlgl the turn
  applies to the cube first. The camera, the grid, the cubes, their colours and
  their shading (`net.b12n.raylib-ios.soft3d/cube`'s default is raylib-jlt's `cube!`) are the
  original's. Nothing reads a frame time, like the original: the angle grows 2
  degrees an update.

  There is no input and so no control to map. The original's caption sits below
  Back, with the 3D view in the field under it, full width to the bottom. The
  view is `net.b12n.raylib-ios.soft3d`'s `[x y w h]` viewport on that field. The original's
  45 degree fovy is kept while the field is at least as wide as 800x450. In a
  narrower field `net.b12n.raylib-ios.soft3d/fit-camera` widens it so the original's
  horizontal view still fits, and the field shows more above and below. The
  grid reaches past the field's edges, so the draw method clips to the field.

  The camera never moves, so `grid-list` builds the grid once per layout and
  `scene-list` adds the cubes to it each frame. The state holds only `:frame`.
  Colours are `[r g b a]` vectors."
  (:require [net.b12n.raylib-ios.soft3d :as s3]))

(def caption-text "Five cubes spinning with a phase offset")

(def background-colour [245 245 245 255])
(def caption-colour [80 80 80 255])

(def palette
  "RED, ORANGE, GREEN, BLUE and VIOLET, as raylib defines them."
  [[230 41 55 255] [255 161 0 255] [0 228 48 255] [0 121 241 255] [135 60 190 255]])

(def cube-count (count palette))

(def spin-rate "Degrees a frame. The original's." 2.0)
(def phase "Degrees of offset for each cube's index. The original's." 30.0)

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
  "The original's camera, (0, 6, 10) looking at the origin with fovy 45, fitted
  to `dims`' field by `net.b12n.raylib-ios.soft3d/fit-camera`."
  [dims]
  (s3/fit-camera {:position [0.0 6.0 10.0]
                  :target [0.0 0.0 0.0]
                  :up [0.0 1.0 0.0]
                  :fovy 45.0
                  :projection :perspective}
                 original-aspect (:aspect dims)))

(defn cube-x "Cube `i`'s x: `2i - 4`." [i] (- (* i 2.0) 4.0))

(defn angle
  "Cube `i`'s turn in degrees for `state`: 2 a frame plus 30 for each index."
  [state i]
  (+ (* (:frame state) spin-rate) (* i phase)))

(defn transform
  "Cube `i`'s transform for `state`: rlTranslatef(x, 0.5, 0) then
  rlRotatef(angle, 0.3, 1, 0), composed in the order the original calls them."
  [state i]
  (s3/compose (s3/translate (cube-x i) 0.5 0.0)
              (s3/rotate-axis (angle state i) 0.3 1.0 0.0)))

(defn grid-list
  "The grid of 12, spacing 1, as an unfinished draw list for `dims`' viewport.
  It depends only on the layout, so a draw can keep it."
  [cam dims]
  (s3/grid [] (s3/view-proj cam (:viewport dims)) 12 1.0))

(defn scene-list
  "The finished draw list for `state`: `base` (the `grid-list`) and the five
  cubes."
  [base state dims]
  (let [vp (s3/view-proj (camera dims) (:viewport dims))]
    (s3/finish
     (reduce (fn [dl i]
               (s3/cube dl vp (transform state i) [0.0 0.0 0.0] 1.0 (nth palette i)))
             base
             (range cube-count)))))

(defn- init [_] [{:frame 0} [[:scene/init :spincubes]]])
(defn- update-scene [state _] [(update state :frame inc) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :spincubes]]])

(defn scene []
  {:id :spincubes
   :title "Spinning Cubes"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
