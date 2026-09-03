(ns raylib.scenes.stars
  "A starfield flying toward the viewer, ported from raylib-jlt's `stars`.

  Each star has a z that shrinks every frame; the projection divides by it, so
  a star sweeps outward and grows as it approaches, then respawns at the back.
  Pure, with the seeded LCG in place of GetRandomValue.")

(def default-seed 90210)
(def default-count 220)
(def speed 0.014)

(defn dimensions [metrics]
  (let [[width height] (:screen metrics)]
    {:cx (/ (double width) 2.0)
     :cy (/ (double height) 2.0)
     :spread (double (max width height))
     :max-radius (* (min (double width) (double height)) 0.006)}))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- pick [seed lo hi]
  (let [seed' (next-random seed)]
    [(+ lo (* (- hi lo) (/ (double (mod seed' 100000)) 100000.0))) seed']))

(defn- new-star [dims seed]
  (let [{:keys [spread]} dims
        [x s1] (pick seed (- spread) spread)
        [y s2] (pick s1 (- spread) spread)
        [z s3] (pick s2 0.25 1.0)]
    [{:x x :y y :z z} s3]))

(defn spawn [dims n seed]
  (loop [i 0 seed seed out []]
    (if (= i n) [out seed]
        (let [[s seed'] (new-star dims seed)] (recur (inc i) seed' (conj out s))))))

(defn advance [state metrics]
  (let [dims (dimensions metrics)]
    (loop [i 0 stars (:stars state) seed (:seed state) out []]
      (if (= i (count stars))
        (assoc state :stars out :seed seed)
        (let [s (nth stars i)
              z (- (:z s) speed)]
          (if (pos? z)
            (recur (inc i) stars seed (conj out (assoc s :z z)))
            (let [[fresh seed'] (new-star dims seed)]
              (recur (inc i) stars seed' (conj out fresh)))))))))

(defn project
  "Screen position and radius for a star, or nil when it is behind the viewer."
  [{:keys [cx cy max-radius]} {:keys [x y z]}]
  (when (pos? z)
    [(+ cx (/ x z)) (+ cy (/ y z)) (* max-radius (- 1.0 z))]))

(defn- init [input]
  (let [[stars seed] (spawn (dimensions (:metrics input)) default-count default-seed)]
    [{:stars stars :seed seed} [[:scene/init :stars]]]))

(defn- update-scene [state input] [(advance state (:metrics input)) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :stars]]])

(defn scene []
  {:id :stars :title "Starfield"
   :init init :update update-scene :draw draw :dispose dispose})
