(ns net.b12n.raylib-ios.scenes.resize.draw
  "The draw-scene! method for the `:resize` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene! stroke!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.resize :as rsz]))

(defmethod draw-scene! :resize [_ {:keys [rw rh holding?]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [x y handle label-size w h]} (rsz/dimensions m)
        ink (if holding? (rl/rgba 230 41 55 255) (rl/rgba 0 82 172 255))]
    (rl/draw-rectangle (int x) (int y) (int rw) (int rh) (rl/rgba 70 130 200 90))
    (stroke! (int x) (int y) (int (+ x rw)) (int y) (rl/rgba 0 121 241 255))
    (stroke! (int (+ x rw)) (int y) (int (+ x rw)) (int (+ y rh)) (rl/rgba 0 121 241 255))
    (stroke! (int (+ x rw)) (int (+ y rh)) (int x) (int (+ y rh)) (rl/rgba 0 121 241 255))
    (stroke! (int x) (int (+ y rh)) (int x) (int y) (rl/rgba 0 121 241 255))
    ;; The corner handle, as a triangle pointing into the rectangle. Through
    ;; draw-triangle, which sorts its own winding: the order written here by
    ;; hand was culled, for the fourth time in this project.
    (let [hx (+ x rw) hy (+ y rh)]
      (rl/draw-triangle (- hx handle) hy hx (- hy handle) hx hy ink))
    (rl/draw-text (str (int rw) " x " (int rh))
                  (int (* 0.10 w)) (int (- h (* 0.20 h))) label-size (rl/rgba 60 60 60 255))
    (rl/draw-text (if holding? "resizing" "drag the corner")
                  (int (* 0.10 w)) (int (- h (* 0.20 h) (- (+ label-size 14))))
                  label-size (rl/rgba 130 130 135 255))))
