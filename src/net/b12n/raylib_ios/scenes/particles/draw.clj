(ns net.b12n.raylib-ios.scenes.particles.draw
  "The draw-scene! method for the `:particles` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.particles :as parts]))

(defmethod draw-scene! :particles [_ {:keys [particles]
                                      :as state} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [text-size line1-x line1-y line2-x line2-y info-rect]} (parts/dimensions m)
        [bx by bw bh] info-rect
        n (count particles)]
    ;; An indexed loop over the vector, so a frame at the cap allocates no seq.
    (loop [i 0]
      (when (< i n)
        (let [{:keys [x y radius]
               :as p} (nth particles i)
              [r g b a] (parts/colour p)]
          (rl/draw-circle (int x) (int y) (float radius) (rl/rgba r g b a)))
        (recur (inc i))))
    ;; A BLUE border and a pale SKYBLUE fill: a rectangle with a smaller one
    ;; inside it, since host has no rectangle-lines.
    (rl/draw-rectangle (int bx) (int by) (int bw) (int bh) (rl/rgba 0 121 241 255))
    (rl/draw-rectangle (+ (int bx) 3) (+ (int by) 3) (- (int bw) 6) (- (int bh) 6)
                       (rl/rgba 205 232 255 255))
    (rl/draw-text parts/info-line line1-x line1-y text-size (rl/rgba 0 0 0 255))
    (rl/draw-text (parts/type-line state) line2-x line2-y text-size rl/DARKGRAY)))
