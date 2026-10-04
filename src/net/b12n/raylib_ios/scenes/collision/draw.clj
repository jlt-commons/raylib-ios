(ns net.b12n.raylib-ios.scenes.collision.draw
  "The draw-scene! method for the `:collision` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.collision :as coll]))

(defmethod draw-scene! :collision [_ {:keys [x target touching?]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [d (coll/dimensions m)
        a (coll/slider-box d x)
        b (coll/finger-box d target)
        box! (fn [[bx by bw bh] c]
               (rl/draw-rectangle (int bx) (int by) (int bw) (int bh) c))]
    (box! a (rl/rgba 102 191 255 255))
    (box! b (rl/rgba 255 203 0 255))
    (when-let [ov (coll/intersection a b)]
      (box! ov (rl/rgba 230 41 55 255)))
    (rl/draw-text (if touching? "dragging" "drag a finger over the blue box")
                  (int (* 0.06 (:w d))) (int (* 0.88 (:h d)))
                  (max 20 (int (* 0.035 (:w d)))) (rl/rgba 80 80 80 255))))
