(ns net.b12n.raylib-ios.scenes.touch-trail.draw
  "The draw-scene! method for the `:touch-trail` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.touch-trail :as trail]))

(defmethod draw-scene! :touch-trail [_ {:keys [points]} {:keys [m]}]
  ;; The trail is drawn oldest first, so each circle overlaps the one before
  ;; and the stroke reads as a single tapering shape rather than a row of
  ;; discs. Radius scales with position in the trail, which is what makes the
  ;; head look like the finger and the tail look like where it has been.
  (let [{:keys [radius]} (trail/layout m)
        n (max 1 (count points))
        biggest (double radius)]
    (rl/clear-background rl/RAYWHITE)
    (loop [i 0 ps points]
      (when-let [p (first ps)]
        (let [scale (/ (double (inc i)) n)]
          (rl/draw-circle (int (nth p 0)) (int (nth p 1)) (* biggest scale) rl/MAROON))
        (recur (inc i) (rest ps))))))
