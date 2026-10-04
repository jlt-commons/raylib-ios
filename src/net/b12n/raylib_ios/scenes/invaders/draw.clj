(ns net.b12n.raylib-ios.scenes.invaders.draw
  "The draw-scene! method for the `:invaders` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.invaders :as inv]))

(defmethod draw-scene! :invaders [_ {:keys [ship-x bullets aliens ax ay score over? won?]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack inv/background-colour))
        dims (inv/dimensions m)
        {:keys [score-size score-x score-y msg-size msg-y]} dims
        rect (fn [[x y w h] colour]
               (rl/draw-rectangle (int x) (int y) (int w) (int h) (pack colour)))]
    (doseq [cell aliens]
      (rect (inv/alien-rect dims ax ay cell) inv/alien-colour))
    (doseq [b bullets]
      (rect (inv/bullet-rect dims b) inv/bullet-colour))
    (rect (inv/ship-rect dims ship-x) inv/ship-colour)
    (rl/draw-text (inv/score-line score) score-x score-y score-size (pack inv/text-colour))
    (when (or over? won?)
      (let [line (if won? inv/win-line inv/over-line)]
        (rl/draw-text line (inv/msg-x dims line) msg-y msg-size
                      (pack (if won? inv/win-colour inv/over-colour)))))))
