(ns raylib.scenes.pointcloud
  "A cloud of 1500 points turning slowly, ported from raylib-jlt's
  `point_cloud` (zlib licence).

  The original makes 1500 points with `GetRandomValue(-50, 50) / 10` for each of
  x, y and z, colours each `(int (+ 128 (* 25 c)))` per axis with alpha 255, and
  draws each as a cube of side 0.06 inside `rlRotatef(frame * 0.3, 0, 1, 0)`,
  seen from (0, 0, 12) at the origin through a 45 degree perspective camera on
  black. Here `GetRandomValue` is the project's LCG seeded with 20261002,
  taking its high bits (`(mod (quot seed' 65536) 101)`, minus 50), so the cloud
  is the same every run. The points, the rotation, the camera and the colours
  are the original's.

  The points are drawn as screen-space squares, not cubes. Measured on the
  laptop under jolt, for one frame of 1500 points on a 1206x2334 screen, cubes
  through `raylib.soft3d/cube` come to 8952 triangles and 12000 projected
  corners and took 16 to 21 ms to build, while squares come to 3000 triangles
  and 1500 projections and took 2 to 3 ms. A cube of side 0.06 is a few pixels
  across, so each point is the square its face would cover seen head on: a side
  of `0.06 * k / depth` pixels, where `k = 0.5 * viewport-height / tan(fovy/2)`.
  It wears the point's colour unshaded (a cube's front and top faces take shade
  1.0, its sides 0.7 to 0.85), is wound the way rlgl keeps, and is painted far to
  near by `raylib.soft3d/finish`. A square is two triangles, so the frame is
  3000 triangles and 9000 vertices, the heaviest of the 3D scenes.

  There is no input and so no control to map. The original's caption says
  \"each a tiny rlgl cube\"; here it says \"each a tiny square\", because that is
  what is drawn. It sits below Back, with the 3D view in the field under it, full
  width to the bottom, clipped to it by the draw method. The original's fovy is
  kept while the field is at least as wide as 800x450, and in a narrower field
  `raylib.soft3d/fit-camera` widens it so the original's horizontal view still
  fits. In a wide field the cloud's nearest points lie outside the view, as
  they do in the original.

  The state holds only `:frame`; the points are a constant. Colours are
  `[r g b a]` vectors."
  (:require [raylib.soft3d :as s3]))

(def n-points "How many points. The original's." 1500)

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

(defn scene-list
  "The finished draw list for `state`: one square for each point in front of the
  camera."
  [state dims]
  (let [vp (s3/view-proj (camera dims) (:viewport dims))
        m (transform state)
        ;; screen pixels a world unit spans at depth 1
        k (/ (* 0.5 (:h vp)) (Math/tan (* 0.5 (Math/toRadians (double (:fovy (:camera vp)))))))
        m0 (nth m 0) m2 (nth m 2) m8 (nth m 8) m10 (nth m 10)]
    (s3/finish
     (reduce (fn [dl [x y z [r g b a]]]
               ;; the y axis turn leaves y alone
               (if-let [[sx sy depth] (s3/project vp [(+ (* m0 x) (* m2 z)) y (+ (* m8 x) (* m10 z))])]
                 (let [h (/ (* 0.5 point-size k) depth)
                       l (- sx h) rt (+ sx h) t (- sy h) bt (+ sy h)]
                   (conj dl
                         [:tri l t l bt rt bt r g b a depth]
                         [:tri l t rt bt rt t r g b a depth]))
                 dl))
             []
             points))))

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
