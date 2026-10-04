(ns net.b12n.raylib-ios.scenes.rlgltriangle.draw
  "The draw-scene! method for the `:rlgltriangle` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.rlgltriangle :as rlgl]))

(defmethod draw-scene! :rlgltriangle [_ {:keys [corners dragging lines?]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [handle thick label-size buttons]} (rlgl/dimensions m)
        corners* (rlgl/wound corners)]
    (if lines?
      ;; Three thick lines through draw-line-ex, which avoids an RL_LINES
      ;; constant. Each edge takes the colour of the corner it starts at.
      (doseq [i (range 3)
              :let [a (nth corners* i)
                    b (nth corners* (mod (inc i) 3))
                    [r g bl al] (:color a)]]
        (rl/draw-line-ex (nth (:pos a) 0) (nth (:pos a) 1)
                         (nth (:pos b) 0) (nth (:pos b) 1)
                         thick (rl/rgba r g bl al)))
      ;; One colour per vertex, so draw-triangle (one colour) does not fit. The
      ;; corners are already wound to survive culling.
      (do
        (rl/rl-begin rl/RL-TRIANGLES)
        (doseq [{[x y] :pos
                 [r g b a] :color} corners*]
          (rl/rl-color-4ub r g b a)
          (rl/rl-vertex-2f (float x) (float y)))
        (rl/rl-end)))
    (doseq [[i {[x y] :pos}] (map-indexed vector corners)]
      (rl/draw-circle (int x) (int y) (double handle)
                      (if (= dragging i) rl/DARKGRAY (rl/rgba 130 130 130 255))))
    (doseq [{:keys [id label rect label-x label-y]} buttons
            :let [[bx by bw bh] rect
                  on? (and lines? (= id :outline))]]
      (rl/draw-rectangle (int bx) (int by) (int bw) (int bh)
                         (if on? (rl/rgba 200 200 200 255) (rl/rgba 225 228 236 255)))
      (rl/draw-text label label-x label-y label-size rl/DARKGRAY))))
