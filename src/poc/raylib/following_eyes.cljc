(ns poc.raylib.following-eyes
  "Pure touch-first adaptation of pinned raylib-jlt Following Eyes.

  Upstream attribution: shapes_following_eyes / raylib-jlt eyes. This namespace
  consumes normalized pointer data; it owns neither polling nor drawing.")

(defn layout [metrics]
  (let [[width height] (:screen metrics)
        scale (/ (double (min width height)) 450.0)
        radius (* 60.0 scale)]
    {:width width :height height :eye-radius radius :pupil-radius (* 22.0 scale)
     :left [(* width 0.35) (* height 0.5)]
     :right [(* width 0.65) (* height 0.5)]
     :neutral [(/ width 2.0) (/ height 2.0)]}))

(defn pupil
  "Return pupil center clamped inside the eye, even for a remote pointer."
  [[ex ey] eye-radius pupil-radius [tx ty]]
  (let [dx (- tx ex) dy (- ty ey)
        distance (Math/sqrt (+ (* dx dx) (* dy dy)))
        maximum (max 0.0 (- eye-radius pupil-radius))
        scale (if (and (pos? distance) (> distance maximum)) (/ maximum distance) 1.0)]
    [(+ ex (* dx scale)) (+ ey (* dy scale))]))

(defn update-state [state input]
  (let [point (get-in input [:pointer :position])
        phase (get-in input [:pointer :phase])
        target (if (#{:press :down} phase) point (:target state))]
    (assoc state :target target :phase phase)))

(defn scene []
  {:id :following-eyes
   :title "Following Eyes"
   :init (fn [input]
           (let [layout (layout (:metrics input))]
             [{:target (:neutral layout) :phase :idle} [[:scene/init :following-eyes]]]))
   :update (fn [state input] [(update-state state input) []])
   :draw (fn [state _] [state []])
   :dispose (fn [state] [state [[:scene/dispose :following-eyes]]])})
