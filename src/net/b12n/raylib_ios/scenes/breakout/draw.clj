(ns net.b12n.raylib-ios.scenes.breakout.draw
  "The draw-scene! method for the `:breakout` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.breakout :as brk]))

(defmethod draw-scene! :breakout [_ {:keys [bricks ball paddle-x lives over? won?]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [dims (brk/dimensions m)
        {:keys [paddle-w paddle-h paddle-y ball-r lives-x lives-y lives-size
                msg-x msg-y msg-size]} dims
        pack (fn [[r g b a]] (rl/rgba r g b a))]
    (doseq [[c r] bricks
            :let [[x y w h] (brk/brick-rect dims c r)]]
      (rl/draw-rectangle (int x) (int y) (int w) (int h) (pack (nth brk/row-colors r))))
    (rl/draw-rectangle (int paddle-x) (int paddle-y) (int paddle-w) (int paddle-h)
                       (pack brk/paddle-colour))
    (rl/draw-circle (int (:x ball)) (int (:y ball)) (float ball-r) (pack brk/ball-colour))
    (rl/draw-text (brk/lives-line lives) lives-x lives-y lives-size (pack brk/text-colour))
    (when over?
      (rl/draw-text brk/over-line msg-x msg-y msg-size (pack brk/over-colour)))
    (when won?
      (rl/draw-text brk/won-line msg-x msg-y msg-size (pack brk/won-colour)))))
