(ns net.b12n.raylib-ios.scenes.bounce.draw
  "The draw-scene! method for the `:bounce` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.bounce :as bounce]))

(defmethod draw-scene! :bounce [_ {:keys [pos paused?]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [radius hint-size hint-x hint-y paused-size paused-x paused-y]} (bounce/dimensions m)
        [r g b a] bounce/ball-colour
        [hr hg hb ha] bounce/hint-colour]
    (rl/draw-circle (int (nth pos 0)) (int (nth pos 1)) (float radius) (rl/rgba r g b a))
    (rl/draw-text bounce/hint-line hint-x hint-y hint-size (rl/rgba hr hg hb ha))
    (when paused?
      (rl/draw-text bounce/paused-line paused-x paused-y paused-size (rl/rgba r g b a)))))
