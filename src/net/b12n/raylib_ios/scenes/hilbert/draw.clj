(ns net.b12n.raylib-ios.scenes.hilbert.draw
  "The draw-scene! method for the `:hilbert` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]))

(defmethod draw-scene! :hilbert [_ {:keys [points colours]} _]
  (rl/clear-background (rl/rgba 12 12 20 255))
  (let [n (count points)]
    (loop [i 1]
      (when (< i n)
        (let [a (nth points (dec i))
              b (nth points i)
              [r g bl al] (nth colours i)]
          (rl/draw-line (int (nth a 0)) (int (nth a 1)) (int (nth b 0)) (int (nth b 1))
                        (rl/rgba r g bl al)))
        (recur (inc i))))))
