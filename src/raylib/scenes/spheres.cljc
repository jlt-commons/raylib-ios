(ns raylib.scenes.spheres
  "Six balls bouncing inside a box under gravity, ported from raylib-jlt's
  `bouncing_spheres` (zlib licence). The projection is in software, by
  `raylib.soft3d`.

  The original (bouncing_spheres.clj) keeps six spheres (`spawn`, lines 18-27),
  each at x and z `GetRandomValue(-30, 30) / 10` and y `GetRandomValue(10, 40) /
  10`, with vx and vz `GetRandomValue(-10, 10) / 100`, vy 0, radius
  `GetRandomValue(3, 6) / 10` and the colour `palette` gives it (line 12: RED,
  ORANGE, GREEN, SKYBLUE, VIOLET, GOLD). The draws run in the map's order: x, y,
  z, vx, vz, r. Here `GetRandomValue` is the project's LCG seeded with 20261002,
  taking its high bits (`(mod (quot seed' 65536) (inc (- hi lo)))` plus lo), so
  the first six are the same every run and a respawn goes on from where the LCG
  stopped.

  `step` (lines 36-42) runs once a frame, with no frame time, like the
  original: gravity 0.01 comes off vy, then each axis moves by its velocity and
  `reflect` (lines 29-33) bounces it. A sphere whose near side `p - r` is
  below -4 is put at `-4 + r` and its velocity is reversed and scaled by the
  restitution 0.9. A far side `p + r` above 4 is put at `4 - r` the same way.
  The camera (line 59) is at (10, 8, 10) looking at the origin, fovy 45, up
  (0, 1, 0), and a `DrawGrid(10, 1)` lies on the floor. The original has no
  walls of the box, only the bounce.

  Controls: SPACE respawns all six (line 56). Here a tap anywhere outside Back
  does, by `raylib.gesture/track`: a tap is a finger that lifts without
  travelling past the slop. The release position is never read. Nothing else is
  read, as the original reads nothing else. The on-screen text becomes \"Spheres
  bouncing in a 3D box - tap respawns\".

  The spheres are the original's `rl/sphere!`, which is `raylib.soft3d/sphere`.
  The original tessellates each at 10 rings by 14 slices, 140 quads, 280
  triangles, 122 of which face the camera for a ball at the origin. This scene
  draws 6 rings by 8 slices, 48 quads, because the original's tessellation
  built in 0.89 ms a frame under jolt on the laptop and the phone's budget is
  0.45 (a build is about 33 times slower there). 6 by 8 builds in about 0.39 ms
  with about 240 triangles facing the camera. The sphere count, the radii and
  the physics are the original's. The grid goes in first, then the spheres
  whole, far to near by the distance of their centres from the eye, and `raylib.soft3d/finish`
  is not called (sorting every triangle cost more than the phone's budget). Where
  two spheres overlap on screen the nearer centre paints over, which is right
  for balls that do not touch, and can differ from a depth buffer where they
  interpenetrate. The grid is drawn first, so a line under a ball is lost, as it
  is meant to be. Every ball stays inside the view, so none is culled.

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

(defn paint-order
  "`balls` far to near by the squared distance of their centres from `eye`."
  [balls [ex ey ez]]
  (sort-by (fn [{:keys [x y z]}]
             (let [dx (- x ex) dy (- y ey) dz (- z ez)]
               (- (+ (* dx dx) (* dy dy) (* dz dz)))))
           balls))

(defn scene-list
  "The draw list for `state`: the grid, then each ball whole, far to near."
  [state dims]
  (let [cam (camera dims)
        vp (s3/view-proj cam (:viewport dims))
        opts {:rings rings
              :slices slices}]
    (reduce (fn [dl {:keys [x y z r colour]}]
              (s3/sphere dl vp nil [x y z] r colour opts))
            (s3/grid [] vp 10 1.0)
            (paint-order (:balls state) (:position cam)))))

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
