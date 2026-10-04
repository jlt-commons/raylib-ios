(ns net.b12n.raylib-ios.scenes.wheelbox.draw
  "The draw-scene! method for the `:wheelbox` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.wheelbox :as wbox]))

(defmethod draw-scene! :wheelbox [_ {:keys [y]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack wbox/background-colour))
        dims (wbox/dimensions m)
        side (int (:side dims))
        {:keys [s x size]
         cy :y} (:caption dims)]
    (rl/draw-rectangle (int (:box-x dims)) (int y) side side (pack wbox/box-colour))
    (rl/draw-text s (int x) (int cy) size (pack wbox/caption-colour))))
