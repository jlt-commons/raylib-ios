(ns net.b12n.raylib-ios.scenes.strip.draw
  "The draw-scene! method for the `:strip` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.strip :as strip]))

(defmethod draw-scene! :strip [_ _ {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [caption-size caption-x caption-y]
         :as dims} (strip/dimensions m)]
    ;; Two triangles a band. draw-triangle fixes its own winding, so the
    ;; order the corners are written in here does not matter.
    (doseq [[x0 x1 top bot [r g b a]] (strip/bands dims)
            :let [c (rl/rgba r g b a)]]
      (rl/draw-triangle x0 top x0 bot x1 top c)
      (rl/draw-triangle x1 top x0 bot x1 bot c))
    (rl/draw-text "a rainbow strip via rlgl immediate mode"
                  caption-x caption-y caption-size rl/DARKGRAY)))
