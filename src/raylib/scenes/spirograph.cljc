(ns raylib.scenes.spirograph
  "A hypotrochoid, ported from jlt-commons/raylib-jlt's `spirograph` example.

  A pen at offset d on a wheel of radius r rolling inside a ring of radius R
  traces a roulette curve; after enough points it resets with fresh random
  r and d.

  Two things changed on the way over, and both are the port pattern for the
  rest of that collection.

  The original owns its loop, calling window!, looping to a deadline, and
  closing. Here the host owns the loop and this is a reducer over frames,
  which is the scene contract in poc.raylib.gallery: init, update, draw and
  dispose, each taking and returning state, with no raylib call anywhere in
  this file.

  And the original draws at a fixed 800x450 with the ring hard-coded at
  radius 170 about (400, 225). A phone is 1206x2622, so the geometry derives
  from the live screen the way poc.raylib.flappy-bird's `dimensions` does.

  Randomness is a seeded LCG rather than raylib's GetRandomValue, for the
  same reason flappy-bird carries one: it keeps the namespace pure, so the
  curve is reproducible from a seed and the whole thing is testable on a
  build host with no raylib, no SDL and no device.")

(def default-seed 20260903)

;; How many points before the figure resets with new parameters. The original
;; used 1600 at 800x450, and a phone has more room, but this is a frame-rate
;; number rather than an aesthetic one: every point is a line drawn every
;; frame. Measured on an iPhone 17 Pro, 1024 points holds 55 fps, 1344 gives
;; 40 and 1616 gives 35. See docs/guide/performance-on-a-phone.md.
(def max-points 1000)

;; Points added per frame. Eight at 0.04 radians apart was the original's
;; trade between a smooth curve and reaching the reset in reasonable time.
(def points-per-frame 8)
(def step-radians 0.04)
(def frame-advance 0.32)

(defn dimensions
  "Ring geometry for the live screen: centre, outer radius, and the range the
  wheel and pen are drawn from. Everything scales off the smaller dimension so
  a tall phone and a wide desktop both get a figure that fits."
  [metrics]
  (let [[width height] (:screen metrics)
        w (double width)
        h (double height)
        span (min w h)]
    {:cx (/ w 2.0)
     :cy (/ h 2.0)
     :big-r (* span 0.40)
     :r-min (* span 0.07)
     :r-max (* span 0.23)
     :d-min (* span 0.10)
     :d-max (* span 0.32)}))

;; The LCG from poc.raylib.flappy-bird, same constants, so the two agree on
;; what a seed means.
(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- pick
  "A double in [lo hi) from `seed`, and the next seed."
  [seed lo hi]
  (let [seed' (next-random seed)
        span (- hi lo)]
    [(+ lo (* span (/ (double (mod seed' 100000)) 100000.0))) seed']))

(defn new-figure
  "Fresh r, d and an empty point list, from `seed`."
  [metrics seed]
  (let [{:keys [r-min r-max d-min d-max]} (dimensions metrics)
        [r seed'] (pick seed r-min r-max)
        [d seed''] (pick seed' d-min d-max)]
    {:r r :d d :t 0.0 :points [] :seed seed'' :figures 0}))

(defn point
  "The pen position at angle t, for a wheel of radius r and pen offset d."
  [{:keys [cx cy big-r]} r d t]
  (let [k (/ (- big-r r) r)]
    [(+ cx (* (- big-r r) #?(:clj (Math/cos t) :default (Math/cos t)))
        (* d #?(:clj (Math/cos (* k t)) :default (Math/cos (* k t)))))
     (+ cy (* (- big-r r) #?(:clj (Math/sin t) :default (Math/sin t)))
        (- (* d #?(:clj (Math/sin (* k t)) :default (Math/sin (* k t))))))]))

(defn rainbow
  "A colour for the i'th segment, as [r g b a]. The host packs it; this
  namespace never touches a raylib colour."
  [i]
  (let [h (mod (* i 3) 360)
        ramp (fn [x] (int (* 255 x)))]
    (cond
      (< h 60) [255 (ramp (/ h 60.0)) 0 255]
      (< h 120) [(ramp (/ (- 120 h) 60.0)) 255 0 255]
      (< h 180) [0 255 (ramp (/ (- h 120) 60.0)) 255]
      (< h 240) [0 (ramp (/ (- 240 h) 60.0)) 255 255]
      (< h 300) [(ramp (/ (- h 240) 60.0)) 0 255 255]
      :else [255 0 (ramp (/ (- 360 h) 60.0)) 255])))

(defn advance
  "One frame: extend the curve, or start a new figure once it is long enough."
  [state metrics]
  (let [{:keys [r d t points seed figures]} state
        dims (dimensions metrics)
        added (mapv (fn [i] (point dims r d (+ t (* i step-radians))))
                    (range points-per-frame))
        points' (into points added)]
    (if (> (count points') max-points)
      (assoc (new-figure metrics seed) :figures (inc figures))
      (assoc state :t (+ t frame-advance) :points points'))))

;; --- the scene contract ------------------------------------------------------
(defn- init [input]
  [(assoc (new-figure (:metrics input) default-seed) :metrics (:metrics input))
   [[:scene/init :spirograph]]])

(defn- update-scene [state input]
  [(advance (assoc state :metrics (:metrics input)) (:metrics input)) []])

(defn- draw [state _input]
  [state []])

(defn- dispose [state]
  [state [[:scene/dispose :spirograph]]])

(defn scene
  "The scene map poc.raylib.gallery's registry expects."
  []
  {:id :spirograph
   :title "Spirograph"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
