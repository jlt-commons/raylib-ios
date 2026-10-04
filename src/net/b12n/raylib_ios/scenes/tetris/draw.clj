(ns net.b12n.raylib-ios.scenes.tetris.draw
  "The draw-scene! method for the `:tetris` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.tetris :as tet]))

(defmethod draw-scene! :tetris [_ {:keys [board piece next score lines level over?]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack tet/background-colour))
        dims (tet/dimensions m)
        {:keys [cell wx wy text-size score-x score-y lines-y level-y next-x next-y
                msg-size msg-x msg-y]} dims
        rect (fn [[x y w h] colour]
               (rl/draw-rectangle (int x) (int y) (int w) (int h) (pack colour)))
        colour-of (fn [ty] (:colour (tet/pieces ty)))]
    ;; Four 2 px bars round the well, since there is no rectangle-outline binding.
    (let [ww (* tet/cols cell)
          wh (* tet/rows cell)]
      (doseq [bar [[(- wx 2) (- wy 2) (+ ww 4) 2]
                   [(- wx 2) (+ wy wh) (+ ww 4) 2]
                   [(- wx 2) wy 2 wh]
                   [(+ wx ww) wy 2 wh]]]
        (rect bar tet/well-colour)))
    (doseq [r (range tet/rows) c (range tet/cols)
            :let [ty (get-in board [r c])]
            :when ty]
      (rect (tet/cell-rect dims c r) (colour-of ty)))
    (when-not over?
      (doseq [[c r] (tet/piece-cells piece)
              :when (>= r 0)]
        (rect (tet/cell-rect dims c r) (colour-of (:type piece)))))
    (rl/draw-text (tet/score-line score) score-x score-y text-size (pack tet/text-colour))
    (rl/draw-text (tet/lines-line lines) score-x lines-y text-size (pack tet/text-colour))
    (rl/draw-text (tet/level-line level) score-x level-y text-size (pack tet/text-colour))
    (rl/draw-text tet/next-line next-x next-y text-size (pack tet/label-colour))
    (doseq [r (tet/preview-rects dims next)]
      (rect r (colour-of next)))
    (when over?
      (rl/draw-text tet/over-line msg-x msg-y msg-size (pack tet/over-colour)))))
