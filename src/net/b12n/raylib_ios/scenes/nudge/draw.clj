(ns net.b12n.raylib-ios.scenes.nudge.draw
  "The draw-scene! method for the `:nudge` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.nudge :as nudge]))

(defmethod draw-scene! :nudge [_ {:keys [pos stick]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack nudge/background-colour))
        dims (nudge/dimensions m)
        {:keys [s x y size]} (:caption dims)
        [bx by] pos]
    (rl/draw-text s (int x) (int y) size (pack nudge/caption-colour))
    (rl/draw-circle (int bx) (int by) (float (:ball-r dims)) (pack nudge/ball-colour))
    (when stick
      (let [[cx cy] (:centre stick)
            [kx ky] (nudge/knob dims stick)
            r (:stick-r dims)]
        (rl/draw-ring (int cx) (int cy) (- r (max 2.0 (* r 0.06))) r 0 360 48 (pack nudge/stick-colour))
        (rl/draw-circle (int kx) (int ky) (float (:knob-r dims)) (pack nudge/knob-colour))))))
