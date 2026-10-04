(ns net.b12n.raylib-ios.scenes.huewheel.draw
  "The draw-scene! method for the `:huewheel` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.huewheel :as hue]))

(defmethod draw-scene! :huewheel [_ {:keys [tris brightness lines?]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack hue/background-colour))
        d (hue/dimensions m)
        [hint1 hint2 count-row] (:lines d)]
    (if lines?
      ;; RL_LINES is not bound, so the wireframe is one draw-line-ex a segment.
      (doseq [[x0 y0 x1 y1 c] (hue/wire-segments d tris)]
        (rl/draw-line-ex x0 y0 x1 y1 (:thick d) (pack c)))
      ;; One colour per vertex, so draw-triangle (one colour) does not fit. Each
      ;; wedge arrives wound to survive culling.
      (do
        (rl/rl-begin rl/RL-TRIANGLES)
        (doseq [t (hue/fan d tris brightness)
                {[x y] :pos
                 [r g b a] :color} t]
          (rl/rl-color-4ub r g b a)
          (rl/rl-vertex-2f (float x) (float y)))
        (rl/rl-end)))
    (doseq [{:keys [s x y size]} [hint1 hint2]]
      (rl/draw-text s x y size (pack hue/hint-colour)))
    (rl/draw-text (hue/count-line tris) (:x count-row) (:y count-row) (:size count-row)
                  (pack hue/count-colour))))
