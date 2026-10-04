(ns net.b12n.raylib-ios.scenes.randomvalues.draw
  "The draw-scene! method for the `:randomvalues` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.randomvalues :as rv]))

(defmethod draw-scene! :randomvalues [_ {:keys [value history]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [[w _] (:screen m)
        {:keys [label-size big-size caption-x caption-y value-y recent-x recent-y]}
        (rv/dimensions m)
        text (str value)
        tw (rl/measure-text text big-size)]
    (rl/draw-text "a new random value every 2 seconds" caption-x caption-y label-size rl/DARKGRAY)
    (rl/draw-text text (int (* 0.5 (- w tw))) value-y big-size rl/MAROON)
    (rl/draw-text (str "recent: " (apply str (interpose " " history)))
                  recent-x recent-y label-size (rl/rgba 130 130 130 255))))
