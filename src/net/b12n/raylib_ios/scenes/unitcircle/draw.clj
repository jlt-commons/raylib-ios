(ns net.b12n.raylib-ios.scenes.unitcircle.draw
  "The draw-scene! method for the `:unitcircle` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene! stroke!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.unitcircle :as circle]))

(defmethod draw-scene! :unitcircle [_ {:keys [angle trace]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (circle/dimensions m)
        cx (int (:cx d)) cy (int (:cy d)) r (:radius d)
        [px py] (circle/point-at d angle)
        px (int px) py (int py)
        grey (rl/rgba 200 200 200 255)
        blue (rl/rgba 0 121 241 255)
        green (rl/rgba 0 158 47 255)
        maroon (rl/rgba 190 33 55 255)]
    ;; the circle and its axes
    (rl/draw-circle-lines cx cy r grey)
    (rl/draw-line (int (- cx r)) cy (int (+ cx r)) cy grey)
    (rl/draw-line cx (int (- cy r)) cx (int (+ cy r)) grey)
    ;; the two projections of the radius, and the radius itself
    (stroke! px py px cy blue)
    (stroke! px cy cx cy green)
    (stroke! cx cy px py maroon)
    (rl/draw-circle px py (* 0.02 (min (:w d) (:h d))) maroon)
    ;; and the same two values traced over time, underneath
    (doseq [[pick colour centre] [[first blue 0.28] [second green 0.72]]]
      (let [pts (circle/wave-points d trace pick centre)
            n (count pts)]
        (loop [i 1]
          (when (< i n)
            (let [a (nth pts (dec i)) b (nth pts i)]
              (stroke! (int (nth a 0)) (int (nth a 1))
                       (int (nth b 0)) (int (nth b 1)) colour))
            (recur (inc i))))))))
