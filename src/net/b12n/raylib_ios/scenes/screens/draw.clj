(ns net.b12n.raylib-ios.scenes.screens.draw
  "The draw-scene! method for the `:screens` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.screens :as screens]))

(defmethod draw-scene! :screens [_ {:keys [screen]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack (screens/background screen)))
        {:keys [rows]} (screens/dimensions m)]
    (doseq [[{:keys [x y size]} [s colour]] (map vector rows (screens/lines screen))]
      (rl/draw-text s (int x) (int y) size (pack colour)))))
