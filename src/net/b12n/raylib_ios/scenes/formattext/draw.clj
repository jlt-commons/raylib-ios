(ns net.b12n.raylib-ios.scenes.formattext.draw
  "The draw-scene! method for the `:formattext` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.formattext :as ftext]))

(defmethod draw-scene! :formattext [_ {:keys [frame]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [size x score-y time-y]} (ftext/dimensions m)
        [score time] (ftext/readouts frame)]
    ;; raylib's DARKBLUE below, which net.b12n.raylib-ios.host does not name.
    (rl/draw-text score x score-y size rl/MAROON)
    (rl/draw-text time x time-y size (rl/rgba 0 82 172 255))))
