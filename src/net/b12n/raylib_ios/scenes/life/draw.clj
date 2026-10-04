(ns net.b12n.raylib-ios.scenes.life.draw
  "The draw-scene! method for the `:life` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.life :as life]))

(defmethod draw-scene! :life [_ {:keys [live]} {:keys [m]}]
  (rl/clear-background (rl/rgba 8 10 16 255))
  (let [{:keys [cell]} (life/dimensions m)
        size (max 1 (dec cell))
        lime (rl/rgba 0 228 48 255)]
    ;; One rectangle per live cell, straight off the set. There is no ordering
    ;; to respect, so this does not need an indexed loop the way a trail does.
    (doseq [c live]
      (rl/draw-rectangle (* (nth c 0) cell) (* (nth c 1) cell) size size lime))))
