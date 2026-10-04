(ns net.b12n.raylib-ios.scenes.snake.draw
  "The draw-scene! method for the `:snake` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.snake :as snk]))

(defmethod draw-scene! :snake [_ {:keys [snake food dead?]} {:keys [m]}]
  (rl/clear-background (rl/rgba 0 0 0 255))
  (let [dims (snk/dimensions m)
        {:keys [cols rows cell ox oy score-x score-y score-size msg-x msg-y msg-size]} dims
        pack (fn [[r g b a]] (rl/rgba r g b a))]
    (rl/draw-rectangle (int ox) (int oy) (int (* cols cell)) (int (* rows cell))
                       (pack snk/board-colour))
    (when food
      (let [[x y w h] (snk/cell-rect dims (nth food 0) (nth food 1))]
        (rl/draw-rectangle (int x) (int y) (int w) (int h) (pack snk/food-colour))))
    ;; A pixel inside its cell on each side, as in the original, so the body
    ;; shows as segments.
    (doseq [[c r] snake
            :let [[x y w h] (snk/cell-rect dims c r)]]
      (rl/draw-rectangle (inc (int x)) (inc (int y)) (- (int w) 2) (- (int h) 2)
                         (pack snk/snake-colour)))
    (rl/draw-text (snk/score-line (count snake)) score-x score-y score-size
                  (pack snk/text-colour))
    (when dead?
      (rl/draw-text snk/over-line msg-x msg-y msg-size (pack snk/text-colour)))))
