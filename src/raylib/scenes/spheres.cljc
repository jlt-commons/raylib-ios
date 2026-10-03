(ns raylib.scenes.spheres
  "Six balls bouncing inside a box under gravity, ported from raylib-jlt's
  `bouncing_spheres` (zlib licence). The projection is in software, by
  `raylib.soft3d`.

  The original (bouncing_spheres.clj) keeps six spheres (`spawn`, lines 15-25),
  each at x and z `GetRandomValue(-30, 30) / 10` and y `GetRandomValue(10, 40) /
  10`, with vx and vz `GetRandomValue(-10, 10) / 100`, vy 0, radius
  `GetRandomValue(3, 6) / 10` and the colour `palette` gives it (line 13: RED,
  ORANGE, GREEN, SKYBLUE, VIOLET, GOLD). The draws run in the map's order: x, y,
  z, vx, vz, r. Here `GetRandomValue` is the project's LCG seeded with 20261002,
  taking its high bits (`(mod (quot seed' 65536) (inc (- hi lo)))` plus lo), so
  the first six are the same every run and a respawn goes on from where the LCG
  stopped.

  `step` (lines 33-40) runs once a frame, with no frame time, like the
  original: gravity 0.01 comes off vy, then each axis moves by its velocity and
  `reflect` (lines 27-31) bounces it. A sphere whose near side `p - r` is
  below -4 is put at `-4 + r` and its velocity is reversed and scaled by the
  restitution 0.9. A far side `p + r` above 4 is put at `4 - r` the same way.
  The camera (lines 55-57) is at (10, 8, 10) looking at the origin, fovy 45, up
  (0, 1, 0), and a `DrawGrid(10, 1)` lies on the floor. The original has no
  walls of the box, only the bounce.

  Controls: SPACE respawns all six (line 52). Here a tap anywhere outside Back
  does, by `raylib.gesture/track`: a tap is a finger that lifts without
  travelling past the slop. The release position is never read. Nothing else is
  read, as the original reads nothing else. The on-screen text becomes \"Spheres
  bouncing in a 3D box - tap respawns\".

  The spheres are the original's `rl/sphere!`, which is `raylib.soft3d/sphere`.
  The original tessellates each at 10 rings by 14 slices, 140 quads, 280
  triangles, 122 of which face the camera for a ball at the origin. This scene
  draws 6 rings by 8 slices, 48 quads, because the original's tessellation
  built in 0.89 ms a frame under jolt on the laptop, and a scene is sized to
  about 0.30 ms there (the performance guide's \"Sizing a scene on the laptop\":
  a build is about 33 times slower on the phone). 6 by 8 builds in about 0.39 ms
  with about 240 triangles facing the camera, which is inside the edge of that
  rule and held 60 fps on the phone. The sphere count, the radii and
  the physics are the original's. The grid goes in first, then the balls in
  groups, far to near by the distance of each group's mean centre from the eye.
  A ball whose sphere touches no other
  (centres nearer than the sum of the radii, taken transitively) is a group of
  one and goes in whole, and `raylib.soft3d/finish` is not called for it (sorting
  every triangle cost more than the phone's budget). The triangles of a group of
  two or more are sorted together by `finish`, because the original has no
  collision between balls and they interpenetrate in about 4 frames in 9 once
  they have settled (measured, 322 of 720 sampled poses with a touching pair).
  Whole-ball order paints a small ball over a big one it is half inside, wrongly,
  so those triangles are depth sorted. What stays is the depth sort's own limit:
  a triangle is ordered by its mean depth, so a residue at the lens where two
  balls cross can remain. A lone ball whose depth falls between the members of a
  group can also paint over the nearer member (5 of 12,000 simulated poses).
  The grid is drawn first, so a line under a ball is
  lost, as it is meant to be. Every ball stays in the box, which the camera
  sees, so none is culled, though one at the near floor corner reaches past the
  field's edge and is clipped by the draw method's scissor.

  The state holds the balls (maps of numbers), the LCG seed, the gesture and
  `:frame`. Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.soft3d :as s3]))

(def bound "The box's half side, the original's." 4.0)
(def gravity "Taken off vy every frame, the original's." 0.01)
(def restitution "A bounce keeps this much of the speed, the original's." 0.9)
(def seed "The LCG's start." 20261002)

(def palette
  "RED, ORANGE, GREEN, SKYBLUE, VIOLET, GOLD, as raylib defines them."
  [[230 41 55 255] [255 161 0 255] [0 228 48 255] [102 191 255 255] [135 60 190 255] [255 203 0 255]])

(def rings "Latitude bands per sphere here. The original's is 10." 6)
(def slices "Longitude slices per sphere here. The original's is 14." 8)
(def original-rings 10)
(def original-slices 14)

(def caption-text "Spheres bouncing in a 3D box - tap respawns")
(def background-colour "RAYWHITE." [245 245 245 255])
(def caption-colour "DARKGRAY." [80 80 80 255])
(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

(defn- next-random [s]
  (mod (+ (* 1103515245 (long s)) 12345) 2147483648))

(defn random-value
  "`[v seed']`: an int in [lo, hi] from the LCG's high bits."
  [s lo hi]
  (let [s' (next-random s)]
    [(+ lo (mod (quot s' 65536) (inc (- hi lo)))) s']))

(defn spawn
  "`[balls seed']`: the original's six balls drawn from `seed`, in `spawn`'s order."
  [seed]
  (loop [i 0 s seed out []]
    (if (< i 6)
      (let [[x s] (random-value s -30 30)
            [y s] (random-value s 10 40)
            [z s] (random-value s -30 30)
            [vx s] (random-value s -10 10)
            [vz s] (random-value s -10 10)
            [r s] (random-value s 3 6)]
        (recur (inc i) s
               (conj out {:x (/ x 10.0)
                          :y (/ y 10.0)
                          :z (/ z 10.0)
                          :vx (/ vx 100.0)
                          :vy 0.0
                          :vz (/ vz 100.0)
                          :r (/ r 10.0)
                          :colour (nth palette i)})))
      [out s])))

(defn reflect
  "`[p' v']` for position `p` moving by `v` with radius `r`: the original's
  `reflect`, wall at plus or minus `bound`."
  [p v r]
  (cond (< (- p r) (- bound)) [(+ (- bound) r) (* (- v) restitution)]
        (> (+ p r) bound) [(- bound r) (* (- v) restitution)]
        :else [p v]))

(defn step
  "One frame of one ball: the original's `step`."
  [{:keys [x y z vx vy vz r]
    :as b}]
  (let [vy (- vy gravity)
        [nx vx] (reflect (+ x vx) vx r)
        [ny vy] (reflect (+ y vy) vy r)
        [nz vz] (reflect (+ z vz) vz r)]
    (assoc b :x nx :y ny :z nz :vx vx :vy vy :vz vz)))

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

(defn camera
  "The original's camera, (10, 8, 10) looking at the origin with fovy 45 (the
  defaults of `with-camera-3d`), fitted to `dims`' field by
  `raylib.soft3d/fit-camera`."
  [dims]
  (s3/fit-camera {:position [10.0 8.0 10.0]
                  :target [0.0 0.0 0.0]
                  :up [0.0 1.0 0.0]
                  :fovy 45.0
                  :projection :perspective}
                 original-aspect (:aspect dims)))

(defn- touching?
  "Whether the spheres of balls `p` and `q` intersect: their centres are nearer
  than the sum of their radii."
  [p q]
  (let [dx (- (:x p) (:x q)) dy (- (:y p) (:y q)) dz (- (:z p) (:z q))
        rr (+ (:r p) (:r q))]
    (< (+ (* dx dx) (* dy dy) (* dz dz)) (* rr rr))))

(defn- components
  "`balls` split into groups of balls that touch, transitively, each in the
  order the balls had."
  [balls]
  (loop [todo (vec balls) out []]
    (if (empty? todo)
      out
      (let [[group rest*] (loop [group [(first todo)] rest* (vec (rest todo))]
                            (let [[in out*] ((juxt filter remove) (fn [q] (some #(touching? % q) group)) rest*)]
                              (if (empty? in)
                                [group rest*]
                                (recur (into group in) (vec out*)))))]
        (recur rest* (conj out group))))))

(defn paint-groups
  "`balls` as groups of balls that intersect (transitively), the groups far to
  near by the squared distance from `eye` of the group's mean centre. A ball
  that touches no other is a group of one."
  [balls [ex ey ez]]
  (let [mean (fn [g k] (/ (reduce + (map k g)) (double (count g))))
        d2 (fn [g] (let [dx (- (mean g :x) ex) dy (- (mean g :y) ey) dz (- (mean g :z) ez)]
                     (+ (* dx dx) (* dy dy) (* dz dz))))]
    (vec (sort-by (comp - d2) (components balls)))))

(defn scene-list
  "The draw list for `state`: the grid, then the groups of `paint-groups` far to
  near. A ball that touches no other goes in whole. The balls of a group of
  intersecting balls have their triangles put in order together by
  `raylib.soft3d/finish`, so where they interpenetrate the picture is the depth
  sort's rather than the centres'."
  [state dims]
  (let [cam (camera dims)
        vp (s3/view-proj cam (:viewport dims))
        opts {:rings rings
              :slices slices}
        balls (fn [dl group]
                (reduce (fn [dl {:keys [x y z r colour]}]
                          (s3/sphere dl vp nil [x y z] r colour opts))
                        dl group))]
    (reduce (fn [dl group]
              (if (= 1 (count group))
                (balls dl group)
                (into dl (s3/finish (balls [] group)))))
            (s3/grid [] vp 10 1.0)
            (paint-groups (:balls state) (:position cam)))))

(defn advance
  "One frame: every ball steps, and a tap outside Back respawns them all."
  [state input]
  (let [[g event] (gesture/track (:gesture state) input)
        respawn? (and (= :tap (:type event))
                      (not (gesture/in-back-region? (:at event))))
        [balls seed'] (if respawn?
                        (spawn (:seed state))
                        [(mapv step (:balls state)) (:seed state)])]
    (assoc state :balls balls :seed seed' :gesture g :frame (inc (:frame state)))))

(defn- init [_]
  (let [[balls s] (spawn seed)]
    [{:balls balls
      :seed s
      :gesture gesture/idle
      :frame 0}
     [[:scene/init :spheres]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :spheres]]])

(defn scene []
  {:id :spheres
   :title "Bouncing Spheres"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
