(ns raylib.scenes.boids
  "Flocking, ported from raylib-jlt's `boids`.

  Cohesion, alignment and separation over neighbours within `radius`, wrapping
  at the edges. Pure, with the seeded LCG the other scenes use in place of
  raylib's GetRandomValue.

  One number changed for the phone and it is worth knowing why. The original
  flies 70 boids and the neighbour search is O(n^2), so a frame is n^2 distance
  tests: 4900 there. On portable bytecode every one of those is interpreted,
  and the collector section of the notebooks measured this interpreter at
  roughly 45x native on arithmetic. `default-count` is therefore a starting
  point rather than a constant, and `advance` takes the flock it is given, so
  the number can be tuned live over the nREPL without a rebuild.")

(def default-seed 4423)
(def default-count 45)

(defn dimensions [metrics]
  (let [[width height] (:screen metrics)
        span (min (double width) (double height))]
    {:width (double width) :height (double height)
     :radius (* span 0.10)
     :max-speed (* span 0.0026)
     :body (max 2 (int (* span 0.004)))
     :tail (* span 0.010)}))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- pick [seed lo hi]
  (let [seed' (next-random seed)]
    [(+ lo (* (- hi lo) (/ (double (mod seed' 100000)) 100000.0))) seed']))

(defn spawn [dims n seed]
  (loop [i 0 seed seed out []]
    (if (= i n)
      [out seed]
      (let [[x s1] (pick seed 0.0 (:width dims))
            [y s2] (pick s1 0.0 (:height dims))
            [vx s3] (pick s2 -1.0 1.0)
            [vy s4] (pick s3 -1.0 1.0)]
        (recur (inc i) s4 (conj out {:x x :y y :vx vx :vy vy}))))))

(defn- limit [vx vy m]
  (let [s (Math/sqrt (+ (* vx vx) (* vy vy)))]
    (if (> s m) [(* (/ vx s) m) (* (/ vy s) m)] [vx vy])))

(defn- step-boid [dims b flock]
  (let [{:keys [radius max-speed width height]} dims
        r2 (* radius radius)
        near (filterv (fn [o]
                        (let [dx (- (:x o) (:x b)) dy (- (:y o) (:y b))]
                          (< (+ (* dx dx) (* dy dy)) r2)))
                      flock)
        k (max 1 (count near))
        avg (fn [f] (/ (reduce + (map f near)) k))
        sep (fn [f] (reduce + (map (fn [o] (- (f b) (f o))) near)))
        vx (+ (:vx b) (* 0.0008 (- (avg :x) (:x b))) (* 0.05 (- (avg :vx) (:vx b))) (* 0.0010 (sep :x)))
        vy (+ (:vy b) (* 0.0008 (- (avg :y) (:y b))) (* 0.05 (- (avg :vy) (:vy b))) (* 0.0010 (sep :y)))
        [vx vy] (limit vx vy max-speed)]
    {:x (mod (+ (:x b) vx) width) :y (mod (+ (:y b) vy) height) :vx vx :vy vy}))

(defn advance [state metrics]
  (let [dims (dimensions metrics)
        flock (:flock state)]
    (assoc state :flock (mapv (fn [b] (step-boid dims b flock)) flock))))

(defn heading
  "A boid's nose, for the host to draw a line to."
  [{:keys [x y vx vy]} {:keys [tail]}]
  (let [s (Math/sqrt (+ (* vx vx) (* vy vy)))
        s (if (< s 0.001) 1.0 s)]
    [(+ x (* (/ vx s) tail)) (+ y (* (/ vy s) tail))]))

(defn- init [input]
  (let [dims (dimensions (:metrics input))
        [flock seed] (spawn dims default-count default-seed)]
    [{:flock flock :seed seed} [[:scene/init :boids]]]))

(defn- update-scene [state input] [(advance state (:metrics input)) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :boids]]])

(defn scene []
  {:id :boids :title "Boids"
   :init init :update update-scene :draw draw :dispose dispose})
