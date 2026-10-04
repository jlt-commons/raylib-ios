(ns net.b12n.raylib-ios.scenes.clock.draw
  "The draw-scene! method for the `:clock` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.clock :as clock]))

(defmethod draw-scene! :clock [_ _ {:keys [m]}]
  (rl/clear-background (rl/rgba 16 20 18 255))
  ;; The time is read here rather than in the scene, so the pure half stays
  ;; testable without a clock and this stays the only namespace that talks to
  ;; anything outside the process.
  (let [[_ _ ss :as now] (rl/local-time)
        d (clock/dimensions m)
        on (rl/rgba 80 230 120 255)
        off (rl/rgba 28 44 34 255)
        pairs (clock/digit-pairs now)]
    (dotimes [row 3]
      (let [y (clock/row-origin d row)
            [tens units] (nth pairs row)]
        (doseq [[digit x] [[tens (:x0 d)] [units (:x1 d)]]]
          (let [lit (get clock/lit-segments digit)]
            (doseq [[seg r] (clock/segment-rects d x y)]
              (rl/draw-rectangle (int (nth r 0)) (int (nth r 1))
                                 (int (nth r 2)) (int (nth r 3))
                                 (if (contains? lit seg) on off)))))))
    ;; the separator between rows, blinking on even seconds
    (let [c (if (even? ss) on off)
          dot (* 0.9 (:thick d))]
      (dotimes [row 2]
        (let [y (+ (clock/row-origin d row) (:row-h d) (* 0.25 (:gap d)))]
          (rl/draw-rectangle (int (- (* 0.5 (:w d)) (* 0.5 dot))) (int y)
                             (int dot) (int dot) c))))))
