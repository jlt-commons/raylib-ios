(ns net.b12n.raylib-ios.scenes.penrose.draw
  "The draw-scene! method for the `:penrose` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [color draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.penrose :as pen]))

(defmethod draw-scene! :penrose [_ {:keys [tris]} _]
  (rl/clear-background (rl/rgba 18 18 24 255))
  ;; one rlgl batch for every fill, then the edges as ordinary lines
  (rl/rl-begin rl/RL-TRIANGLES)
  (doseq [[k [ax ay] [bx by] [cx cy]] tris]
    (let [[r g b a] (if (zero? k) pen/colour-thin pen/colour-thick)]
      (rl/rl-color-4ub r g b a)
      (rl/rl-vertex-2f (float ax) (float ay))
      (rl/rl-vertex-2f (float bx) (float by))
      (rl/rl-vertex-2f (float cx) (float cy))))
  (rl/rl-end)
  (let [edge (color pen/colour-edge)]
    (doseq [[_ [ax ay] [bx by] [cx cy]] tris]
      (rl/draw-line (int ax) (int ay) (int bx) (int by) edge)
      (rl/draw-line (int bx) (int by) (int cx) (int cy) edge)
      (rl/draw-line (int cx) (int cy) (int ax) (int ay) edge))))
