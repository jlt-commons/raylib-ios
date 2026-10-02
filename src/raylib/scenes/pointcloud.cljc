(ns raylib.scenes.pointcloud
  "A cloud of points turning slowly, ported from raylib-jlt's `point_cloud`
  (zlib licence), with fewer points.

  The original makes 1500 points with `GetRandomValue(-50, 50) / 10` for each of
  x, y and z, colours each `(int (+ 128 (* 25 c)))` per axis with alpha 255, and
  draws each as a cube of side 0.06 inside `rlRotatef(frame * 0.3, 0, 1, 0)`,
  seen from (0, 0, 12) at the origin through a 45 degree perspective camera on
  black. Here `GetRandomValue` is the project's LCG seeded with 20261002,
  taking its high bits (`(mod (quot seed' 65536) 101)`, minus 50), so the cloud
  is the same every run. The distribution, colours, rotation and camera are the
  original's.

  There are 400 points here, where the original has 1500. They are the first
  400 of the cloud the 1500 would be. The first version of this port, with all
  1500 points as squares sorted by `finish`, ran at 9 fps on an iPhone 17 Pro,
  110 ms a frame.

  The points are drawn as screen-space squares, not cubes. A cube of side 0.06
  is a few pixels across and its shades cannot be told apart, so each point is
  the square its face would cover seen head on: a side of `0.06 * k / depth`
  pixels, where `k = 0.5 * viewport-height / tan(fovy/2)`. It wears the point's
  colour unshaded (a cube's front and top faces take shade 1.0, its sides 0.7
  to 0.85) and is wound the way rlgl keeps. A cube takes about six triangles to
  the square's two.

  Squares are painted far to near by depth bucket (`scene-list`), not by a
  full sort. There is no input and so no control to map. The original's caption
  says \"each a tiny rlgl cube\"; here it says \"each a tiny square\", because that
  is what is drawn, and it gives this scene's count. It sits below Back, with
  the 3D view in the field under it, full width to the bottom, clipped to it by
  the draw method. The original's fovy is kept while the field is at least as
  wide as 800x450, and in a narrower field `raylib.soft3d/fit-camera` widens it
  so the original's horizontal view still fits. In a wide field the cloud's
  nearest points lie outside the view, as they do in the original.

  The state holds only `:frame`; the points are a constant. Colours are
  `[r g b a]` vectors."
  (:require [raylib.soft3d :as s3]))

(def original-points "How many points the original draws." 1500)

(def n-points "How many points are drawn here." 400)

(def seed "The LCG's start." 20261002)

(def caption-text (str n-points " points, each a tiny square"))

(def background-colour [0 0 0 255])
(def caption-colour "RAYWHITE, as raylib defines it." [245 245 245 255])

(def point-size "A cube's side in the original; here the square's, at head-on." 0.06)

(defn- next-random [s]
  (mod (+ (* 1103515245 (long s)) 12345) 2147483648))

(defn- random-value
  "`[v seed']`: an int in [-50, 50] from the LCG's high bits. The low bit
  alternates on every step, so `(quot seed' 65536)` is what gets used."
  [s]
  (let [s' (next-random s)]
    [(- (mod (quot s' 65536) 101) 50) s']))

(defn make-points
  "The cloud as `[x y z colour]` vectors, drawn from the LCG seeded with `seed`
  in the original's order: x, y, z of one point, then the next."
  []
  (loop [i 0 s seed out []]
    (if (< i n-points)
      (let [[vx s1] (random-value s)
            [vy s2] (random-value s1)
            [vz s3'] (random-value s2)
            x (/ vx 10.0) y (/ vy 10.0) z (/ vz 10.0)]
        (recur (inc i) s3'
               (conj out [x y z [(int (+ 128 (* 25 x))) (int (+ 128 (* 25 y))) (int (+ 128 (* 25 z))) 255]])))
      out)))

(def points "The cloud, made once." (make-points))

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
  "The original's camera, (0, 0, 12) looking at the origin with fovy 45, fitted
  to `dims`' field by `raylib.soft3d/fit-camera`."
  [dims]
  (s3/fit-camera {:position [0.0 0.0 12.0]
                  :target [0.0 0.0 0.0]
                  :up [0.0 1.0 0.0]
                  :fovy 45.0
                  :projection :perspective}
                 original-aspect (:aspect dims)))

(defn angle "The turn in degrees for `state`: 0.3 a frame." [state]
  (* (:frame state) 0.3))

(defn transform
  "rlRotatef(angle, 0, 1, 0) for `state`."
  [state]
  (s3/rotate-axis (angle state) 0.0 1.0 0.0))

(def ^:private near "rlgl.h RL_CULL_DISTANCE_NEAR, as `raylib.soft3d` has it." 0.05)

(def bucket-count "How many depth buckets the squares are painted by." 64)

(def depth-far
  "The view depth the first bucket starts at. A point lies within 5 sqrt 3 =
  8.66 of the origin, and the camera is 12 from it, so depths run from 3.34 to
  20.66."
  20.7)

(def bucket-width "A bucket's depth: (20.7 - 3.3) / 64." 0.272)

(defn scene-list
  "The draw list for `state`: one square for each point in front of the camera,
  painted far to near by depth bucket. The points are put into `bucket-count`
  buckets of `bucket-width` depth and the buckets emitted far first, in place
  of `raylib.soft3d/finish`, whose comparator sort was most of the frame. Two
  squares less than a bucket apart in depth can paint in either order, which
  at a few pixels across is not visible.

  Each point is projected in place. The turn about y is folded into the clip
  rows once a frame, so a point costs three multiplies a row; the view depth is
  one more row."
  [state dims]
  (let [vp (s3/view-proj (camera dims) (:viewport dims))
        rot (transform state)
        c (nth rot 0) s (nth rot 2)
        {:keys [m d]
         ox :x
         oy :y
         w :w
         h :h} vp
        hw (* 0.5 w) hh (* 0.5 h)
        ;; a clip row's coefficients on x and z once x and z are turned
        fold (fn [r0 r2] [(- (* r0 c) (* r2 s)) (+ (* r0 s) (* r2 c))])
        [axX azX] (fold (nth m 0) (nth m 2))
        [axY azY] (fold (nth m 4) (nth m 6))
        [axW azW] (fold (nth m 12) (nth m 14))
        [axD azD] (fold (nth d 0) (nth d 2))
        ayX (nth m 1) ayY (nth m 5) ayW (nth m 13) ayD (nth d 1)
        kX (nth m 3) kY (nth m 7) kW (nth m 15) kD (nth d 3)
        ;; pixels a world unit spans at depth 1
        k (/ hh (Math/tan (* 0.5 (Math/toRadians (double (:fovy (:camera vp)))))))
        half1 (* 0.5 point-size k)
        top (dec bucket-count)
        buckets (object-array (repeat bucket-count []))]
    (loop [i 0]
      (when (< i n-points)
        (let [[x y z [r g b a]] (nth points i)
              D (+ (* axD x) (* ayD y) (* azD z) kD)]
          (when (and (>= D near) (> D 1.0e-6))
            (let [W (+ (* axW x) (* ayW y) (* azW z) kW)
                  sx (+ ox (* hw (+ 1.0 (/ (+ (* axX x) (* ayX y) (* azX z) kX) W))))
                  sy (+ oy (* hh (- 1.0 (/ (+ (* axY x) (* ayY y) (* azY z) kY) W))))
                  q (/ half1 D)
                  l (- sx q) rt (+ sx q) t (- sy q) bt (+ sy q)
                  bk (min top (max 0 (int (/ (- depth-far D) bucket-width))))]
              (aset buckets bk (conj (aget buckets bk)
                                     [:tri l t l bt rt bt r g b a D]
                                     [:tri l t rt bt rt t r g b a D])))))
        (recur (inc i))))
    (loop [j 0 dl []]
      (if (< j bucket-count)
        (recur (inc j) (into dl (aget buckets j)))
        dl))))

(defn- init [_] [{:frame 0} [[:scene/init :pointcloud]]])
(defn- update-scene [state _] [(update state :frame inc) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :pointcloud]]])

(defn scene []
  {:id :pointcloud
   :title "Point Cloud"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
