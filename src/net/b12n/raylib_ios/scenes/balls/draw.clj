(ns net.b12n.raylib-ios.scenes.balls.draw
  "The draw-scene! method for the `:balls` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]))

(defmethod draw-scene! :balls [_ {:keys [balls]} _]
  ;; No env needed: a ball already carries its position in the safe region's
  ;; own coordinates, which is the space the host has translated us into.
  (rl/clear-background (rl/rgba 245 245 245 255))
  (doseq [b balls]
    (let [[r g bl] (:colour b)]
      (rl/draw-circle (int (:x b)) (int (:y b)) (:r b) (rl/rgba r g bl 255))
      (rl/draw-circle-lines (int (:x b)) (int (:y b)) (:r b) (rl/rgba 80 80 80 90)))))
