(ns net.b12n.raylib-ios.scenes.easingsbox.draw
  "The draw-scene! method for the `:easingsbox` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.easingsbox :as ebox]))

(defmethod draw-scene! :easingsbox [_ {:keys [stage counter]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack ebox/background-colour))
        dims (ebox/dimensions m)
        sh (ebox/shape dims stage counter)
        [r g b] ebox/box-colour
        colour (rl/rgba r g b (int (* 255 (max 0.0 (min 1.0 (:alpha sh))))))
        [[ax ay] [bx by] [cx cy] [dx dy]] (ebox/quad-corners-of sh)
        {:keys [x y size]} (first (:lines dims))]
    ;; DrawRectanglePro is unbound, so the rotated box is two triangles.
    (when (pos? (:alpha sh))
      (rl/draw-triangle ax ay bx by cx cy colour)
      (rl/draw-triangle ax ay cx cy dx dy colour))
    (rl/draw-text (ebox/stage-line stage) x y size (pack ebox/text-colour))))
