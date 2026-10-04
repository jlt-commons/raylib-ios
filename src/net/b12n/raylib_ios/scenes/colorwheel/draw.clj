(ns net.b12n.raylib-ios.scenes.colorwheel.draw
  "The draw-scene! method for the `:colorwheel` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.colorwheel :as wheel]))

(defmethod draw-scene! :colorwheel [_ {:keys [offset]} {:keys [m]}]
  (rl/clear-background (rl/rgba 20 20 28 255))
  ;; The only place this project uses rlgl immediate mode. raylib's shapes API
  ;; cannot draw a triangle with a different colour at each corner, and the
  ;; gradient around the rim is exactly that.
  (let [dims (wheel/dimensions m)
        cx (double (:cx dims))
        cy (double (:cy dims))]
    (rl/rl-begin rl/RL-TRIANGLES)
    (loop [i 0]
      (when (< i wheel/slices)
        (let [sl (wheel/slice dims offset i)
              [r0 g0 b0] (wheel/hsv->rgb (nth sl 2))
              [r1 g1 b1] (wheel/hsv->rgb (nth sl 5))]
          (rl/rl-color-4ub r0 g0 b0 255)
          (rl/rl-vertex-2f (double (nth sl 0)) (double (nth sl 1)))
          (rl/rl-color-4ub 255 255 255 255)
          (rl/rl-vertex-2f cx cy)
          (rl/rl-color-4ub r1 g1 b1 255)
          (rl/rl-vertex-2f (double (nth sl 3)) (double (nth sl 4))))
        (recur (inc i))))
    (rl/rl-end)))
