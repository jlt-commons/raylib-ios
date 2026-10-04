(ns net.b12n.raylib-ios.scenes.bars.draw
  "The draw-scene! method for the `:bars` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.bars :as bars]))

(defmethod draw-scene! :bars [_ _ {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  ;; bars/outline still returns a vector, because the tests walk it, and it is
  ;; called once per bar rather than once per vertex. bars/shade is not called
  ;; here at all: it allocated a colour vector per vertex, 400 a frame across the
  ;; five bars, which with the outlines came to 54 fps. The channels are mixed
  ;; inline from primitives instead. Fourth scene to make this trade.
  (let [{:keys [label-size w h]
         :as d} (bars/dimensions m)
        [lr lg lb] bars/left-colour
        [rr rg rb] bars/right-colour]
    (dotimes [i bars/bar-count]
      (let [[x y bw bh] (bars/bar-rect d i)
            [rl* rrn] (bars/roundness i)
            pts (bars/outline x y bw bh rl* rrn)
            n (count pts)
            cx (+ x (* 0.5 bw))
            cy (+ y (* 0.5 bh))
            emit (fn [px py]
                   (let [t (max 0.0 (min 1.0 (/ (- (double px) x) bw)))]
                     (rl/rl-color-4ub (long (+ lr (* (- rr lr) t)))
                                      (long (+ lg (* (- rg lg) t)))
                                      (long (+ lb (* (- rb lb) t)))
                                      255)
                     (rl/rl-vertex-2f (float px) (float py))))]
        (rl/rl-begin rl/RL-TRIANGLES)
        (dotimes [k n]
          (let [[px py] (nth pts k)
                [qx qy] (nth pts (mod (inc k) n))]
            ;; centre, then NEXT, then current. The natural centre-current-next
            ;; order gives a positive cross for this clockwise outline and every
            ;; triangle is culled. Checked by hand on a square bar before
            ;; building: +80000 one way round, -80000 the other.
            (emit cx cy)
            (emit qx qy)
            (emit px py)))
        (rl/rl-end)
        (rl/draw-text (str "left " (format "%.2f" rl*) "   right " (format "%.2f" rrn))
                      (int (+ x (* 0.02 w))) (int (+ y (* 0.06 bh)))
                      label-size (rl/rgba 255 255 255 220))))
    (rl/draw-text "one loop draws a square and a lozenge"
                  (int (* 0.08 w)) (int (- h (* 0.14 h)))
                  label-size (rl/rgba 60 60 60 255))))
