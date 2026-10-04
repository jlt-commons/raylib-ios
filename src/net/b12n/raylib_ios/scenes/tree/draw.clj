(ns net.b12n.raylib-ios.scenes.tree.draw
  "The draw-scene! method for the `:tree` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [color draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.tree :as tree]))

(defmethod draw-scene! :tree [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [segs (tree/branches m (tree/wind t))
        n (count segs)
        bark (color tree/bark)
        leaf (color tree/leaf)]
    (loop [i 0]
      (when (< i n)
        (let [s (nth segs i)]
          (rl/draw-line (int (nth s 0)) (int (nth s 1)) (int (nth s 2)) (int (nth s 3))
                        (if (nth s 4) leaf bark)))
        (recur (inc i))))))
