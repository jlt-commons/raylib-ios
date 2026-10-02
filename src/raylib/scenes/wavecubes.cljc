(ns raylib.scenes.wavecubes
  "A 14 by 14 grid of columns whose heights ripple like water, ported from
  raylib-jlt's `waving_cubes` (zlib licence).

  The original draws a column at every `(ix, iz)` with spacing 1.5, centred on
  the origin: `x = ix * 1.5 - half` and the same for z, where half is
  `0.5 * 13 * 1.5`. Its time is `t = 0.06 * frame`. The height is
  `0.6 + 2.4 * (1 + sin(0.6 ix + 0.6 iz + t))`, so 0.6 to 5.4, and the column
  is a box of size `[1, height, 1]` centred at half that height. The colour is
  three sines, `(int (+ 128 (* 127 (sin ...))))` of `0.35 ix + t`,
  `0.35 iz + t + 2` and `0.35 (ix + iz) + t + 4`, with alpha 255. The camera
  orbits at `a = 0.012 * frame`, at `(27.3 cos a, 18.9, 27.3 sin a)` (the
  span, 21, times 1.3 and 0.9) looking at `(0, 1.5, 0)` through a 45 degree
  perspective camera. All of that is kept, and so is the shading
  (`raylib.soft3d/cube`'s default is raylib-jlt's `cube!`). Nothing reads a
  frame time, like the original: both the wave and the orbit advance by frame.

  There is no input and so no control to map. The original's on-screen fps
  counter (`fps!`) is dropped, as earlier scenes drop theirs; the caption is
  the original's text. It sits below Back, with the 3D view in the field under
  it, full width to the bottom, clipped to it by the draw method. The original's
  fovy is kept while the field is at least as wide as 800x450, and in a
  narrower field `raylib.soft3d/fit-camera` widens it so the original's
  horizontal view still fits.

  Cost: the 196 columns are kept. From a camera above them each shows its top
  and two sides, 6 triangles, and now and then only one side. Measured over a
  whole orbit a frame is 1148 to 1176 triangles (at most 3528 vertices) and 1568
  projected corners (8 a column). There is no grid
  and no line. Faces are painted far to near by mean depth with no depth buffer, so
  where a tall column stands in front of a short one the painter can leave a
  little residue at a shared edge.

  The state holds only `:frame`. Colours are `[r g b a]` vectors."
  (:require [raylib.soft3d :as s3]))

(def n "Columns along each side. The original's." 14)
(def spacing "Distance between column centres. The original's." 1.5)

(def caption-text (str "waving cubes - " (* n n) " columns"))

(def background-colour [18 18 32 255])
(def caption-colour "RAYWHITE, as raylib defines it." [245 245 245 255])

(def ^:private half (* 0.5 (dec n) spacing))
(def ^:private span (* spacing n))

(defn dimensions
  "`raylib.soft3d/field` plus the caption as `{:s :x :y :size}`, in `:lines` as
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
  "The original's orbiting camera for `state`, fitted to `dims`' field by
  `raylib.soft3d/fit-camera`: at radius `span * 1.3`, height `span * 0.9`,
  turned `0.012 * frame` radians, looking at (0, 1.5, 0) with fovy 45."
  [state dims]
  (let [a (* 0.012 (:frame state))]
    (s3/fit-camera {:position [(* span 1.3 (Math/cos a)) (* span 0.9) (* span 1.3 (Math/sin a))]
                    :target [0.0 1.5 0.0]
                    :up [0.0 1.0 0.0]
                    :fovy 45.0
                    :projection :perspective}
                   original-aspect (:aspect dims))))

(defn column
  "The column at grid cell `(ix, iz)` for `state`: `:pos`, `:size` and `:colour`
  as the original passes them to `cube!`."
  [state ix iz]
  (let [t (* 0.06 (:frame state))
        wave (Math/sin (+ (* 0.6 ix) (* 0.6 iz) t))
        hgt (+ 0.6 (* 2.4 (+ 1.0 wave)))
        shade (fn [phase] (int (+ 128 (* 127 (Math/sin phase)))))]
    {:pos [(- (* ix spacing) half) (/ hgt 2.0) (- (* iz spacing) half)]
     :size [1.0 hgt 1.0]
     :colour [(shade (+ (* 0.35 ix) t))
              (shade (+ (* 0.35 iz) t 2.0))
              (shade (+ (* 0.35 (+ ix iz)) t 4.0))
              255]}))

(defn columns
  "All `n * n` columns for `state`, `ix` outer and `iz` inner, as the original
  loops."
  [state]
  (vec (for [ix (range n) iz (range n)] (column state ix iz))))

(defn scene-list
  "The finished draw list for `state`: every column as a box."
  [state dims]
  (let [vp (s3/view-proj (camera state dims) (:viewport dims))]
    (s3/finish
     (reduce (fn [dl {:keys [pos size colour]}] (s3/cube dl vp nil pos size colour))
             []
             (columns state)))))

(defn- init [_] [{:frame 0} [[:scene/init :wavecubes]]])
(defn- update-scene [state _] [(update state :frame inc) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :wavecubes]]])

(defn scene []
  {:id :wavecubes
   :title "Waving Cubes"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
