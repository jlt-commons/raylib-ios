(ns net.b12n.raylib-ios.scenes.hello.draw
  "The draw-scene! method for the `:hello` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.draw :refer [draw-scene! host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.hello :as hello]))

(defmethod draw-scene! :hello [_ _ {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack hello/background-colour))
        {:keys [s x y size]} (:text (hello/dimensions m host-measure))]
    (rl/draw-text s (int x) (int y) size (pack hello/text-colour))))
