(ns net.b12n.raylib-ios.scenes.spirograph.draw
  "The draw-scene! method for the `:spirograph` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.spirograph :as spiro]))

(defmethod draw-scene! :spirograph [_ {:keys [points]} _]
  ;; An indexed loop rather than (map-indexed vector (partition 2 1 points)).
  ;; Measured on device: the lazy sequence and its per-segment tuple took this
  ;; from about 50 fps to 14 at a thousand points, while the draw calls
  ;; themselves were never the problem.
  (rl/clear-background (rl/rgba 0 0 0 255))
  (let [n (count points)]
    (loop [i 1]
      (when (< i n)
        (let [a (nth points (dec i))
              b (nth points i)
              [r g b' a'] (spiro/rainbow i)]
          (rl/draw-line (int (nth a 0)) (int (nth a 1)) (int (nth b 0)) (int (nth b 1))
                        (rl/rgba r g b' a')))
        (recur (inc i))))))
