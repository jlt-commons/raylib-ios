(ns raylib.scenes.hilbert
  "A Hilbert space-filling curve, ported from raylib-jlt's `hilbert_curve`.

  The classic four-way recursion, drawn as one continuous rainbow line. Order 5
  is 1024 points, so 1023 segments a frame.

  The curve never changes, so it and its colours are computed ONCE at init.
  That matters here more than it would elsewhere: the original recomputes each
  segment's colour inline with three sin calls, which at 1023 segments is 3069
  transcendentals a frame on an interpreter, for a picture that is identical
  every time. See docs/guide/performance-on-a-phone.md.")

(def order 5)

(defn dimensions [metrics]
  (let [[width height] (:screen metrics)
        size (* (min (double width) (double height)) 0.86)]
    {:size size
     :ox (/ (- (double width) size) 2.0)
     :oy (/ (- (double height) size) 2.0)}))

(defn- hilbert
  "Appends midpoints to `pts`. Origin `o`, basis vectors `ei` and `ej`."
  [[x y :as o] [xi xj] [yi yj] n pts]
  (if (<= n 0)
    (conj pts [(+ x (/ (+ xi yi) 2.0)) (+ y (/ (+ xj yj) 2.0))])
    (as-> pts p
      (hilbert o [(/ yi 2.0) (/ yj 2.0)] [(/ xi 2.0) (/ xj 2.0)] (dec n) p)
      (hilbert [(+ x (/ xi 2.0)) (+ y (/ xj 2.0))]
               [(/ xi 2.0) (/ xj 2.0)] [(/ yi 2.0) (/ yj 2.0)] (dec n) p)
      (hilbert [(+ x (/ xi 2.0) (/ yi 2.0)) (+ y (/ xj 2.0) (/ yj 2.0))]
               [(/ xi 2.0) (/ xj 2.0)] [(/ yi 2.0) (/ yj 2.0)] (dec n) p)
      (hilbert [(+ x (/ xi 2.0) yi) (+ y (/ xj 2.0) yj)]
               [(/ (- yi) 2.0) (/ (- yj) 2.0)] [(/ (- xi) 2.0) (/ (- xj) 2.0)] (dec n) p))))

(defn curve
  "The whole curve for this screen, as [[x y] ...]."
  [metrics]
  (let [{:keys [size ox oy]} (dimensions metrics)]
    (hilbert [ox oy] [size 0.0] [0.0 size] order [])))

(defn colours
  "One [r g b a] per segment, offset rainbow, computed once."
  [n]
  (let [pi Math/PI
        chan (fn [t off] (int (* 255 (max 0.0 (Math/sin (* pi (+ t off)))))))]
    (mapv (fn [i] (let [t (/ (double i) n)] [(chan t 0.0) (chan t 0.33) (chan t 0.66) 255]))
          (range n))))

(defn- init [input]
  (let [pts (curve (:metrics input))]
    [{:points pts :colours (colours (count pts))} [[:scene/init :hilbert]]]))

;; Nothing moves, so the update is a no-op: this scene is a still that costs
;; 1023 draw calls a frame.
(defn- update-scene [state _input] [state []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :hilbert]]])

(defn scene []
  {:id :hilbert :title "Hilbert Curve"
   :init init :update update-scene :draw draw :dispose dispose})
