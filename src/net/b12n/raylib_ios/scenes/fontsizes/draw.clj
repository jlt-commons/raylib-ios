(ns net.b12n.raylib-ios.scenes.fontsizes.draw
  "The draw-scene! method for the `:fontsizes` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.fontsizes :as fsizes]))

(defmethod draw-scene! :fontsizes [_ _ {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack fsizes/background-colour))
        measure (fn [s sz] (rl/measure-text s (int sz)))]
    (doseq [{:keys [s x y size colour]} (fsizes/layout (fsizes/dimensions m) measure)]
      (rl/draw-text s (int x) (int y) size (pack colour)))))
