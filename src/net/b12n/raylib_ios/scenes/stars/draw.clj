(ns net.b12n.raylib-ios.scenes.stars.draw
  "The draw-scene! method for the `:stars` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.stars :as stars]))

(defmethod draw-scene! :stars [_ {:keys [stars]} {:keys [m]}]
  (rl/clear-background (rl/rgba 0 0 8 255))
  (let [dims (stars/dimensions m)
        n (count stars)
        white (rl/rgba 255 255 255 255)]
    (loop [i 0]
      (when (< i n)
        (when-let [p (stars/project dims (nth stars i))]
          (rl/draw-circle (int (nth p 0)) (int (nth p 1)) (double (nth p 2)) white))
        (recur (inc i))))))
