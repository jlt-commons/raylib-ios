(ns net.b12n.raylib-ios.scenes.game2048.draw
  "The draw-scene! method for the `:game2048` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.game2048 :as g2048]))

(defmethod draw-scene! :game2048 [_ {:keys [board score]
                                     :as state} {:keys [m]}]
  (rl/clear-background (rl/rgba 0 0 0 255))
  (let [dims (g2048/dimensions m)
        {:keys [board-x board-y board-side score-x score-y score-size msg-y msg-size]} dims
        pack (fn [[r g b a]] (rl/rgba r g b a))
        text-colour (pack g2048/text-colour)]
    (rl/draw-rectangle (int board-x) (int board-y) (int board-side) (int board-side)
                       (pack g2048/background-colour))
    (doseq [c (range 4)
            r (range 4)
            :let [v (nth board (+ (* r 4) c))
                  [x y w h] (g2048/tile-rect dims c r)]]
      (rl/draw-rectangle (int x) (int y) (int w) (int h) (pack (g2048/tile-colour v)))
      (when (pos? v)
        (let [{tx :x
               ty :y
               size :size} (g2048/tile-text dims x y v)]
          (rl/draw-text (str v) tx ty size text-colour))))
    (rl/draw-text (g2048/score-line score) score-x score-y score-size
                  (pack g2048/score-colour))
    (when (g2048/finished? state)
      (let [line (if (:won? state) g2048/won-line g2048/over-line)]
        (rl/draw-text line (g2048/msg-x dims line) msg-y msg-size
                      (pack g2048/score-colour))))))
