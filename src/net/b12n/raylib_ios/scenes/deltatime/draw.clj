(ns net.b12n.raylib-ios.scenes.deltatime.draw
  "The draw-scene! method for the `:deltatime` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.deltatime :as dtime]))

(defmethod draw-scene! :deltatime [_ {:keys [xf xd]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [box top-y bottom-y label-size label-x top-label-y bottom-label-y fps-y]}
        (dtime/dimensions m)]
    ;; Each label sits just above its own lane, clear of the Back button.
    (rl/draw-text "per frame" label-x top-label-y label-size rl/DARKGRAY)
    (rl/draw-rectangle (int xf) (int top-y) (int box) (int box) rl/MAROON)
    (rl/draw-text "delta time" label-x bottom-label-y label-size rl/DARKGRAY)
    (rl/draw-rectangle (int xd) (int bottom-y) (int box) (int box) (rl/rgba 0 82 172 255))
    ;; Read every frame, which is the only way GetFPS gives a true number.
    (rl/draw-text (str (rl/get-fps) " fps") label-x fps-y label-size rl/DARKGRAY)))
