(ns raylib.scenes.dashed
  "A dashed line from the centre to your finger, ported from raylib-jlt's
  `dashed_line`.

  raylib has no dashed-line call, so a dash is every other segment of a line
  chopped into equal pieces. Walking the unit vector rather than interpolating
  between endpoints keeps the dashes a constant length however far the finger
  is, which is the property that makes it read as a dashed line rather than a
  line with a varying pattern.

  Touch rather than mouse, and it drifts when nothing is touching, for the same
  reasons as the collision scene beside it."
  (:require [clojure.string]))

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:w (double w) :h (double h)
     :cx (* 0.5 w) :cy (* 0.5 h)
     :dash (* 0.022 (min w h))
     :hub (* 0.018 (min w h))}))

(defn dashes
  "The segments to draw, as [x1 y1 x2 y2], from the hub toward `target`.

  Every other piece, so a dash and a gap are the same length. Returns nothing
  at all when the target is on the hub, rather than dividing by a zero length."
  [{:keys [cx cy dash]} [tx ty]]
  (let [dx (- tx cx) dy (- ty cy)
        len (Math/sqrt (+ (* dx dx) (* dy dy)))]
    (if (< len 1e-6)
      []
      (let [ux (/ dx len) uy (/ dy len)
            steps (int (/ len dash))]
        (mapv (fn [i]
                [(+ cx (* ux dash i))       (+ cy (* uy dash i))
                 (+ cx (* ux dash (inc i))) (+ cy (* uy dash (inc i)))])
              (range 0 steps 2))))))

(defn advance [state input]
  (let [{:keys [w h]} (dimensions (:metrics input))
        phase (get-in input [:pointer :phase])
        point (get-in input [:pointer :position])
        t (inc (:t state))]
    (assoc state :t t
           :touching? (boolean (and point (#{:press :down} phase)))
           :target (if (and point (#{:press :down} phase))
                     [(double (first point)) (double (second point))]
                     (let [a (* 0.013 t)]
                       [(+ (* 0.5 w) (* 0.38 w (Math/cos a)))
                        (+ (* 0.5 h) (* 0.36 h (Math/sin (* 1.3 a))))])))))

(defn- init [{:keys [metrics]}]
  (let [{:keys [w h]} (dimensions metrics)]
    [{:t 0 :target [(* 0.9 w) (* 0.5 h)] :touching? false} [[:scene/init :dashed]]]))

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :dashed]]])

(defn scene []
  {:id :dashed :title "Dashed Line"
   :init init :update update-scene :draw draw :dispose dispose})
