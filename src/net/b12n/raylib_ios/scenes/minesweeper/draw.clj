(ns net.b12n.raylib-ios.scenes.minesweeper.draw
  "The draw-scene! method for the `:minesweeper` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.minesweeper :as msw]))

(defmethod draw-scene! :minesweeper [_ {:keys [mines revealed flagged over? won?]
                                        :as state} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack msw/background-colour))
        dims (msw/dimensions m)
        {:keys [cols rows cell ox oy status-x status-y status-size
                num-size num-dx num-dy]} dims]
    ;; The board is filled with the grid colour and each cell is drawn one pixel
    ;; in from its edges, which leaves the grid lines without a lines primitive.
    (rl/draw-rectangle ox oy (* cols cell) (* rows cell) (pack msw/grid-colour))
    (doseq [c (range cols)
            r (range rows)
            :let [cl [c r]
                  [x y w h] (msw/cell-rect dims c r)
                  open? (contains? revealed cl)]]
      (rl/draw-rectangle (inc x) (inc y) (- w 2) (- h 2)
                         (pack (if open? msw/open-colour msw/hidden-colour)))
      (when open?
        (let [n (msw/mine-count dims mines cl)]
          (when (pos? n)
            (rl/draw-text (str n) (+ x num-dx) (+ y num-dy) num-size
                          (pack msw/number-colour)))))
      (when (and (contains? flagged cl) (not open?))
        (let [inset (quot cell 4)]
          (rl/draw-rectangle (+ x inset) (+ y inset) (- w (* 2 inset)) (- h (* 2 inset))
                             (pack msw/flag-colour))))
      (when (and over? (contains? mines cl) (not open?))
        (rl/draw-circle (+ x (quot w 2)) (+ y (quot h 2)) (float (quot cell 5))
                        (pack msw/mine-colour))))
    (rl/draw-text (cond won? msw/won-line
                        over? msw/over-line
                        :else (msw/mines-line (msw/flags-left state)))
                  status-x status-y status-size
                  (pack (if won? msw/win-colour msw/lose-colour)))))
