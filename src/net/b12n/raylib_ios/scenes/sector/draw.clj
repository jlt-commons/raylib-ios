(ns net.b12n.raylib-ios.scenes.sector.draw
  "The draw-scene! method for the `:sector` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.sector :as sector]))

(defmethod draw-scene! :sector [_ {:keys [start-angle end-angle requested]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [cx cy radius label-size w h]} (sector/dimensions m)
        {:keys [segments floor mode]} (sector/resolve-segments start-angle end-angle requested)
        auto? (= :auto mode)]
    ;; A sector is a ring with no hole, so draw-ring covers it. raylib's own
    ;; DrawCircleSector takes its centre as a by-value Vector2, which is the
    ;; same reason draw-ring exists at all.
    (rl/draw-ring cx cy 0.0 radius start-angle end-angle segments
                  (rl/rgba 190 33 55 90))
    (rl/draw-ring cx cy (* radius 0.97) radius start-angle end-angle segments
                  (rl/rgba 190 33 55 255))
    (let [x (int (* 0.06 w))
          y0 (int (- h (* 0.30 h)))
          line (fn [i s c] (rl/draw-text s x (+ y0 (* i (+ label-size 12))) label-size c))]
      (line 0 (str "arc " (int (- end-angle start-angle)) " degrees") rl/DARKGRAY)
      (line 1 (str "asked for " requested " segments") rl/DARKGRAY)
      (line 2 (str "floor is " floor " (one per 90 degrees)") (rl/rgba 130 130 130 255))
      (line 3 (if auto? (str "AUTO: drawing " segments)
                  (str "drawing " segments " as asked"))
            (if auto? (rl/rgba 190 33 55 255) (rl/rgba 0 130 60 255))))))
