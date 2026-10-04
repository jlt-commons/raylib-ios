(ns net.b12n.raylib-ios.scenes.easings.draw
  "The draw-scene! method for the `:easings` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.easings :as ez]
            [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.easings :as ease]))

(defmethod draw-scene! :easings [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (ease/dimensions m)
        p (ease/progress t)
        grey (rl/rgba 190 190 190 255)
        ink (rl/rgba 40 60 110 255)
        mark (rl/rgba 190 33 55 255)
        label (max 16 (int (* 0.13 (:cell-h d))))]
    (doseq [[i [nm f]] (map-indexed vector ez/curves)]
      (let [[ox oy] (ease/cell-origin d i)
            pts (ease/plot d f ox oy)
            n (count pts)]
        ;; the cell's floor, so an overshoot is visibly below or above a line
        (rl/draw-line (int ox) (int (+ oy (:cell-h d) (- (:inset-y d))))
                      (int (+ ox (:cell-w d))) (int (+ oy (:cell-h d) (- (:inset-y d))))
                      grey)
        (rl/draw-text nm (int ox) (int oy) label (rl/rgba 90 90 90 255))
        (loop [k 1]
          (when (< k n)
            (let [a (nth pts (dec k)) b (nth pts k)]
              (rl/draw-line (int (nth a 0)) (int (nth a 1))
                            (int (nth b 0)) (int (nth b 1)) ink))
            (recur (inc k))))
        (let [[dx dy] (ease/dot d f ox oy p)]
          (rl/draw-circle (int dx) (int dy) (* 0.022 (:cell-w d)) mark))))))
