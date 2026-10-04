(ns net.b12n.raylib-ios.scenes.rounded.draw
  "The draw-scene! method for the `:rounded` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.rounded :as rnd]))

(defmethod draw-scene! :rounded [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [{:keys [label-size w h]
         :as d} (rnd/dimensions m)
        r (rnd/radius-at d t)
        {:keys [rects corners]} (rnd/parts d r)
        fill (rl/rgba 0 121 241 255)]
    ;; Rounded outward, not truncated, and grown by a pixel. draw-rectangle
    ;; takes ints while draw-ring takes doubles, so truncating the rects left
    ;; sub-pixel gaps against the corner disks and along the arms' shared edge:
    ;; a faint cross of background showing through the middle of a solid shape.
    ;; Overlapping by a pixel costs one row of overdraw and removes it.
    (doseq [[x y rw rh] rects]
      (rl/draw-rectangle (int (Math/floor x)) (int (Math/floor y))
                         (int (Math/ceil (inc rw))) (int (Math/ceil (inc rh))) fill))
    (doseq [[cx cy start end] corners]
      ;; a quarter disk is draw-ring with no hole. A degree either side of the
      ;; quarter, for the same reason: the arc's flat ends have to reach under
      ;; the rects rather than stop exactly at them.
      (rl/draw-ring cx cy 0.0 (inc r) (- start 1.0) (+ end 1.0) 24 fill))
    (rl/draw-text (str "corner radius " (int r) " of " (int (:max-radius d)))
                  (int (* 0.08 w)) (int (- h (* 0.18 h)))
                  label-size (rl/rgba 60 60 60 255))))
