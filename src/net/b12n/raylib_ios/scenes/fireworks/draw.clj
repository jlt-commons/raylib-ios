(ns net.b12n.raylib-ios.scenes.fireworks.draw
  "The draw-scene! method for the `:fireworks` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.fireworks :as fw]))

(defmethod draw-scene! :fireworks [_ {:keys [rockets parts]} {:keys [m]}]
  (let [{:keys [rocket-radius particle-radius]} (fw/dimensions m)]
    (rl/clear-background (rl/rgba 0 0 0 255))
    (doseq [{:keys [x y color]} rockets]
      (let [[r g b] color]
        (rl/draw-circle (int x) (int y) (double rocket-radius) (rl/rgba r g b 255))))
    (doseq [{:keys [x y life color]} parts]
      (let [[r g b] color]
        (rl/draw-circle (int x) (int y) (double particle-radius)
                        (rl/rgba r g b (int (* 255 (max 0.0 life)))))))))
