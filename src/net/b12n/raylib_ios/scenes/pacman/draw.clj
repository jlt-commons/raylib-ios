(ns net.b12n.raylib-ios.scenes.pacman.draw
  "The draw-scene! method for the `:pacman` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!
                                                           host-measure]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.pacman :as pacman]
            [net.b12n.raylib-ios.scenes.pacman.maze :as maze]))

(defmethod draw-scene! :pacman [_ {:keys [pac ghosts dots score lives level message over? clock]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack pacman/background-colour))
        dims (pacman/dimensions m)
        {:keys [cell ox oy]} dims
        measure host-measure
        k (/ cell 30.0)
        px (fn [gx] (+ ox (* gx cell)))
        py (fn [gy] (+ oy (* gy cell)))
        inset (max 1 (int (* 3 k)))
        rect (fn [[x y w h] colour] (rl/draw-rectangle (int x) (int y) (int w) (int h) (pack colour)))
        text (fn [{:keys [x y size]} s colour] (rl/draw-text s (int x) (int y) size (pack colour)))]
    (dotimes [gy maze/height]
      (dotimes [gx maze/width]
        (let [c (maze/tile-at gx gy)
              [x y w h :as r] (pacman/tile-rect dims gx gy)]
          (cond
            (= \# c) (do (rect r pacman/wall-edge-colour)
                         (rect [(+ x inset) (+ y inset) (- w (* 2 inset)) (- h (* 2 inset))]
                               pacman/wall-colour))
            (= \- c) (rect [x (+ y (quot h 2) (- (int (* 2 k)))) w (max 2 (int (* 4 k)))]
                           pacman/door-colour)))))
    (doseq [[gx gy] dots]
      (let [pellet? (= \o (maze/tile-at gx gy))]
        (when (or (not pellet?) (pacman/power-blink? clock))
          (rl/draw-circle (int (px (+ gx 0.5))) (int (py (+ gy 0.5)))
                          (float (* k (if pellet? 7.0 3.0))) (pack pacman/pellet-colour)))))
    (let [c (pack pacman/pellet-colour)]
      (doseq [[x1 y1 x2 y2 x3 y3] (pacman/pac-fan (px (:x pac)) (py (:y pac)) (* 0.46 cell)
                                                  (:fx pac) (:fy pac) (:mouth pac) 28)]
        (rl/draw-triangle x1 y1 x2 y2 x3 y3 c)))
    (doseq [g ghosts]
      (let [cx (long (px (:x g)))
            cy (long (py (:y g)))
            r (long (* 0.44 cell))
            scared? (pos? (:frightened g))
            colour (pack (cond
                           (and scared? (pacman/ghost-blink? clock)) pacman/label-colour
                           scared? pacman/scared-colour
                           :else (:color g)))
            eye (* 5 k)]
        ;; A dome over a body with three feet, as the original draws it.
        (rl/draw-circle (int cx) (int (- cy (* 2 k))) (float r) colour)
        (rl/draw-rectangle (int (- cx r)) (int (- cy (* 2 k))) (int (* 2 r)) (int (+ r (* 2 k))) colour)
        (doseq [fx (pacman/foot-xs cx r)]
          (rl/draw-circle (int fx) (int (+ cy r)) (float (/ r 2.6)) colour))
        (rl/draw-circle (int (- cx eye)) (int (- cy eye)) (float eye) (pack pacman/label-colour))
        (rl/draw-circle (int (+ cx eye)) (int (- cy eye)) (float eye) (pack pacman/label-colour))
        (when-not scared?
          (let [ex (* 4 k (:dx g))
                ey (* 4 k (:dy g))]
            (rl/draw-circle (int (+ (- cx eye) ex)) (int (+ (- cy eye) ey)) (float (* 2.5 k)) (pack pacman/pupil-colour))
            (rl/draw-circle (int (+ cx eye ex)) (int (+ (- cy eye) ey)) (float (* 2.5 k)) (pack pacman/pupil-colour))))))
    (text (:score dims) (pacman/score-line score) pacman/label-colour)
    (text (:level dims) (pacman/level-line level) pacman/label-colour)
    (let [{:keys [x0 y r step]} (:lives dims)]
      (dotimes [i lives]
        (rl/draw-circle (int (+ x0 (* i step))) (int y) (float r) (pack pacman/pellet-colour))))
    (when message
      (let [{:keys [y size]} (:msg dims)]
        (rl/draw-text message (pacman/centred-x dims size message measure) (int y) size
                      (pack (if over? pacman/over-colour pacman/pellet-colour)))))
    (when over?
      (let [{:keys [y size]} (:msg2 dims)]
        (rl/draw-text pacman/restart-line (pacman/centred-x dims size pacman/restart-line measure) (int y) size
                      (pack pacman/label-colour))))))
