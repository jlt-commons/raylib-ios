(ns net.b12n.raylib-ios.scenes.touchball.draw
  "The draw-scene! method for the `:touchball` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.touchball :as tball]))

(defmethod draw-scene! :touchball [_ {:keys [pos]
                                      :as state} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [radius caption-size caption-x caption-y]} (tball/dimensions m)
        [r g b a] (tball/colour state)]
    (rl/draw-circle (int (nth pos 0)) (int (nth pos 1)) (double radius) (rl/rgba r g b a))
    (rl/draw-text tball/caption
                  caption-x caption-y caption-size rl/DARKGRAY)))
