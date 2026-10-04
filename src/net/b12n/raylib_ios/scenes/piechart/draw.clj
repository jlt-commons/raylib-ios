(ns net.b12n.raylib-ios.scenes.piechart.draw
  "The draw-scene! method for the `:piechart` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.piechart :as pie]))

(defmethod draw-scene! :piechart [_ {:keys [base]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (pie/dimensions m)
        cx (double (:cx d)) cy (double (:cy d))]
    ;; Same triangle fan as the colour wheel, one run per wedge. Flat colour
    ;; this time, so all three vertices carry it.
    (rl/rl-begin rl/RL-TRIANGLES)
    (doseq [wedge (pie/arcs base)]
      (let [[r g b] (:colour wedge)]
        (doseq [t (pie/triangles d wedge)]
          (rl/rl-color-4ub r g b 255)
          (rl/rl-vertex-2f (double (nth t 0)) (double (nth t 1)))
          (rl/rl-color-4ub r g b 255)
          (rl/rl-vertex-2f cx cy)
          (rl/rl-color-4ub r g b 255)
          (rl/rl-vertex-2f (double (nth t 2)) (double (nth t 3))))))
    (rl/rl-end)
    ;; legend, under the chart
    (let [size (int (* 0.30 (:swatch d)))]
      (doseq [[i wedge] (map-indexed vector (pie/arcs 0.0))]
        (let [[r g b] (:colour wedge)
              y (+ (:legend-y d) (* i (:legend-step d)))
              sw (int (:swatch d))]
          (rl/draw-rectangle (int (:legend-x d)) (int y) sw sw (rl/rgba r g b 255))
          (rl/draw-text (str (:label wedge) "  " (pie/percent (:value wedge)) "%")
                        (int (+ (:legend-x d) (* 1.5 sw))) (int (+ y (* 0.25 sw)))
                        (max 20 size) (rl/rgba 80 80 80 255)))))))
