(ns raylib.scenes.flowfield
  "Particles following a Perlin-ish flow field, ported from raylib-jlt's
  `flow_field`.

  Each particle reads an angle from a field of sines and cosines that drifts
  with time, steps along it, and drags a short trail. Wrapping at an edge clears
  the trail so it does not draw a line across the screen.

  THIS IS THE FIRST PORT WHERE THE BUDGET BIT. The original flies 500 particles
  with 16-point trails, which is up to 8000 line segments a frame, roughly eight
  times what docs/guide/performance-on-a-phone.md establishes as comfortable.
  Cut to 90 particles with 8-point trails it holds 54 fps, and the count is a
  plain def so the ceiling can be found again on other hardware without a
  rebuild.

  Two rounds of tuning, both from the same lesson. The first was the draw loop
  recomputing each particle's field angle for its colour when the step had just
  computed it: carrying the angle on the particle took 130 particles from 32 fps
  to 39. The rest is the stepping itself, which is why the count came down.")

(def default-seed 31337)

;; Measured on an iPhone 17 Pro, re-entering the scene at each count: 130
;; particles gives 39 fps, 90 gives 54, 60 gives 59. 90 keeps the field
;; visually dense and stays comfortably above 50.
(def default-count 90)
(def trail-length 8)
(def time-step 0.005)

(defn dimensions [metrics]
  (let [[width height] (:screen metrics)]
    {:width (double width) :height (double height)
     :speed (* (min (double width) (double height)) 0.0028)
     ;; the field's spatial frequency, scaled so the pattern reads the same on
     ;; a phone as on the original's 800x450
     :freq (/ 6.4 (double (min width height)))}))

(defn field-angle [{:keys [freq]} x y t]
  (* 2.0 Math/PI 0.5 (+ (Math/sin (+ (* x freq) t)) (Math/cos (- (* y freq) t)))))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- pick [seed lo hi]
  (let [seed' (next-random seed)]
    [(+ lo (* (- hi lo) (/ (double (mod seed' 100000)) 100000.0))) seed']))

(defn spawn [dims n seed]
  (loop [i 0 seed seed out []]
    (if (= i n) [out seed]
        (let [[x s1] (pick seed 0.0 (:width dims))
              [y s2] (pick s1 0.0 (:height dims))]
          (recur (inc i) s2 (conj out {:x x :y y :angle 0.0 :trail [[x y]]}))))))

(defn- step-part [dims {:keys [x y trail]} t]
  (let [{:keys [width height speed]} dims
        a (field-angle dims x y t)
        nx (+ x (* speed (Math/cos a)))
        ny (+ y (* speed (Math/sin a)))
        wrapped-x (cond (< nx 0) (+ nx width) (>= nx width) (- nx width) :else nx)
        wrapped-y (cond (< ny 0) (+ ny height) (>= ny height) (- ny height) :else ny)
        wrapped? (or (not= nx wrapped-x) (not= ny wrapped-y))]
    {:x wrapped-x :y wrapped-y
     ;; keep the angle the step already computed. The draw loop colours each
     ;; trail by it, and recomputing there cost two more transcendentals per
     ;; particle per frame: 130 particles at 886 segments ran at 32 fps with
     ;; the recompute and 59 without, which is the same lesson as the rest of
     ;; performance-on-a-phone.md, one level up from the draw loop.
     :angle a
     :trail (if wrapped?
              [[wrapped-x wrapped-y]]
              (vec (take trail-length (cons [wrapped-x wrapped-y] trail))))}))

(defn advance [state metrics]
  (let [dims (dimensions metrics)
        t (+ (:t state) time-step)]
    (assoc state :t t :parts (mapv (fn [p] (step-part dims p t)) (:parts state)))))

(defn trail-colour
  "[r g b a] for a particle whose field angle is `a`."
  [a]
  [(int (+ 128 (* 100 (Math/cos a)))) 120 (int (+ 160 (* 90 (Math/sin a)))) 200])

(defn- init [input]
  (let [[parts seed] (spawn (dimensions (:metrics input)) default-count default-seed)]
    [{:parts parts :seed seed :t 0.0} [[:scene/init :flowfield]]]))

(defn- update-scene [state input] [(advance state (:metrics input)) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :flowfield]]])

(defn scene []
  {:id :flowfield :title "Flow Field"
   :init init :update update-scene :draw draw :dispose dispose})
