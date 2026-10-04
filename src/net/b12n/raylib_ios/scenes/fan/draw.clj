(ns net.b12n.raylib-ios.scenes.fan.draw
  "The draw-scene! method for the `:fan` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.fan :as fan]))

(defmethod draw-scene! :fan [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 18 20 28 255))
  (let [{:keys [cx cy label-size w h]
         :as d} (fan/dimensions m)]
    ;; One draw-line-ex per spoke rather than a batch: sixteen calls a frame is
    ;; nothing, and each spoke needs its own colour anyway, which a single
    ;; batched colour would not give.
    (dotimes [i fan/spokes]
      (let [{:keys [from to thick hue]} (fan/spoke d t i)
            [r g b] (fan/hsv->rgb hue)
            [x1 y1] from [x2 y2] to]
        (rl/draw-line-ex x1 y1 x2 y2 thick (rl/rgba r g b 255))))
    (rl/draw-circle (int cx) (int cy) (float (* 0.012 w)) rl/RAYWHITE)
    (rl/draw-text "sixteen widths, thinnest at the top"
                  (int (* 0.08 w)) (int (- h (* 0.14 h)))
                  label-size (rl/rgba 150 150 160 255))))
