(ns net.b12n.raylib-ios.scenes.logoanim.draw
  "The draw-scene! method for the `:logoanim` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.logoanim :as logoanim]))

(defmethod draw-scene! :logoanim [_ {:keys [stage counter top left bottom right letters alpha]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [{:keys [x y side scale border]} (logoanim/dimensions m)
        u (fn [units] (* units scale))
        ink (fn [a] (rl/rgba 0 0 0 (int (* 255 (max 0.0 (min 1.0 a))))))
        black (ink 1.0)]
    (case stage
      0 (when (logoanim/blink-on? counter)
          (rl/draw-rectangle (int x) (int y) (int border) (int border) black))

      (1 2) (do
              (rl/draw-rectangle (int x) (int y) (int (u top)) (int border) black)
              (rl/draw-rectangle (int x) (int y) (int border) (int (u left)) black)
              (when (= stage 2)
                (rl/draw-rectangle (int (+ x side (- border))) (int y)
                                   (int border) (int (u right)) black)
                (rl/draw-rectangle (int (- (+ x side) (u bottom))) (int (+ y side (- border)))
                                   (int (u bottom)) (int border) black)))

      3 (let [c (ink alpha)]
          (rl/draw-rectangle (int x) (int y) (int side) (int border) c)
          (rl/draw-rectangle (int x) (int y) (int border) (int side) c)
          (rl/draw-rectangle (int (+ x side (- border))) (int y) (int border) (int side) c)
          (rl/draw-rectangle (int x) (int (+ y side (- border))) (int side) (int border) c)
          (let [txt (logoanim/visible-word letters)
                size (int (* 0.20 side))]
            (when (seq txt)
              (rl/draw-text txt
                            (int (- (+ x side) (rl/measure-text txt size) (u 20)))
                            (int (- (+ y side) size (u 26)))
                            size c))))
      nil)))
