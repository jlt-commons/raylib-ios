(ns net.b12n.raylib-ios.scenes.wavecubes
  "A grid of columns whose heights ripple like water, ported from raylib-jlt's
  `waving_cubes` (zlib licence), with a smaller grid.

  The original draws 14 by 14 columns, 196, at every `(ix, iz)` with spacing
  1.5, centred on the origin: `x = ix * 1.5 - half` and the same for z, where
  half is `0.5 * (n - 1) * 1.5`. Its time is `t = 0.06 * frame`. The height is
  `0.6 + 2.4 * (1 + sin(0.6 ix + 0.6 iz + t))`, so 0.6 to 5.4, and the column
  is a box of size `[1, height, 1]` centred at half that height. The colour is
  three sines, `(int (+ 128 (* 127 (sin ...))))` of `0.35 ix + t`,
  `0.35 iz + t + 2` and `0.35 (ix + iz) + t + 4`, with alpha 255. The camera
  orbits at `a = 0.012 * frame` at `(1.3 span cos a, 0.9 span, 1.3 span sin a)`
  looking at `(0, 1.5, 0)` through a 45 degree perspective camera, where span
  is `1.5 n`, 21 in the original. The wave, the colours, the spacing, the
  heights and the camera's proportions are the original's, and so is the
  shading (`net.b12n.raylib-ios.soft3d/cube`'s shades are raylib-jlt's `cube!`). Nothing reads
  a frame time, like the original: both the wave and the orbit advance by frame.

  The grid here is 9 by 9, 81 columns, where the original has 14 by 14, 196.
  The first version of this port, with all 196 columns through
  `net.b12n.raylib-ios.soft3d/cube` and `finish`, ran at 15 fps on an iPhone 17 Pro, 67 ms a
  frame. This version paints by axis order instead of `finish` and draws 81.
  Span follows the grid, so the camera comes in to frame 9 columns as the
  original framed 14, and the wave keeps its ripple per column but shows fewer
  ripples across.

  There is no input and so no control to map. The original's on-screen fps
  counter (`fps!`) is dropped, as earlier scenes drop theirs; the caption is
  the original's text with this grid's count. It sits below Back, with the 3D
  view in the field under it, full width to the bottom, clipped to it by the
  draw method. The original's fovy is kept while the field is at least as wide
  as 800x450, and in a narrower field `net.b12n.raylib-ios.soft3d/fit-camera` widens it so
  the original's horizontal view still fits.

  A frame has no grid and no line, and 468 to 486 triangles: from the camera a
  column shows its top and two sides. `scene-list` paints the columns far to
  near by ordering each axis of the grid farthest first (`axis-order`), which a
  grid of boxes needs no sort of triangles for; see `scene-list`. With no
  depth buffer a tall column near the camera over a short one behind it can
  leave a little residue where their edges meet.

  The state holds only `:frame`. Colours are `[r g b a]` vectors."
  (:require [net.b12n.raylib-ios.soft3d :as s3]))

(def original-n "Columns along each side in the original." 14)

(def n "Columns along each side here." 9)
(def spacing "Distance between column centres. The original's." 1.5)

(def caption-text (str "waving cubes - " (* n n) " columns"))

(def background-colour [18 18 32 255])
(def caption-colour "RAYWHITE, as raylib defines it." [245 245 245 255])

(def ^:private half (* 0.5 (dec n) spacing))
(def ^:private span (* spacing n))

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
  "The original's orbiting camera for `state`, fitted to `dims`' field by
  `net.b12n.raylib-ios.soft3d/fit-camera`: at radius `span * 1.3`, height `span * 0.9`,
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

(defn axis-order
  "The cell indices 0 to n-1 ordered farthest first from the world coordinate
  `c` along one axis, by distance of the cell's centre."
  [c]
  (vec (sort-by (fn [i] (- (abs (- (- (* i spacing) half) c)))) (range n))))

(defn scene-list
  "The draw list for `state`: every column as a `net.b12n.raylib-ios.soft3d/cube`, painted
  far to near. The columns stand on a grid and never overlap in plan, so
  taking the x indices and the z indices each farthest first from the camera
  and looping x outside z paints a column after everything behind it. That
  replaces `net.b12n.raylib-ios.soft3d/finish`, whose comparator sort of every triangle cost
  a third of the frame."
  [state dims]
  (let [cam (camera state dims)
        vp (s3/view-proj cam (:viewport dims))
        [camx _ camz] (:position cam)]
    (reduce
     (fn [dl ix]
       (reduce
        (fn [dl iz]
          (let [{:keys [pos size colour]} (column state ix iz)]
            (s3/cube dl vp nil pos size colour)))
        dl (axis-order camz)))
     []
     (axis-order camx))))

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
