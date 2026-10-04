(ns net.b12n.raylib-ios.scenes.bullets.draw
  "The draw-scene! method for the `:bullets` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.bullets :as bull]))

(defmethod draw-scene! :bullets [_ {:keys [bullets]} {:keys [m]}]
  (rl/clear-background (rl/rgba 15 15 30 255))
  (let [d (bull/dimensions m)
        gold (rl/rgba 255 203 0 255)
        r (:radius d)]
    (doseq [b bullets]
      (rl/draw-circle (int (:x b)) (int (:y b)) r gold))
    (rl/draw-circle (int (:cx d)) (int (:cy d)) (* 2.4 r) (rl/rgba 230 41 55 255))))
