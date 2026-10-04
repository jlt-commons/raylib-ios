(ns net.b12n.raylib-ios.scenes.palette.draw
  "The draw-scene! method for the `:palette` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.palette :as pal]))

(defmethod draw-scene! :palette [_ _ {:keys [m]}]
  (rl/clear-background (rl/rgba 30 32 40 255))
  (let [{:keys [swatch-h label-size]
         :as d} (pal/dimensions m)]
    (doseq [[i entry] (map-indexed vector pal/colours)]
      (let [[x y w _] (pal/cell d i)
            [nm r g b] entry
            ink (if (pal/light? entry) (rl/rgba 20 20 20 255) rl/RAYWHITE)]
        (rl/draw-rectangle (int x) (int y) (int w) (int swatch-h) (rl/rgba r g b 255))
        ;; The name sits ON the swatch rather than under it, so the contrast
        ;; choice is visible: a label that vanishes is the bug this scene would
        ;; otherwise hide.
        (rl/draw-text nm (int (+ x (* 0.06 w))) (int (+ y (* 0.5 swatch-h) (- (quot label-size 2))))
                      label-size ink)
        (rl/draw-text (str r " " g " " b)
                      (int (+ x (* 0.06 w))) (int (+ y swatch-h (* 0.12 label-size)))
                      (max 14 (int (* 0.8 label-size))) (rl/rgba 150 150 160 255))))))
