(ns net.b12n.raylib-ios.scenes.gradient.draw
  "The draw-scene! method for the `:gradient` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.gradient :as grad]))

(defmethod draw-scene! :gradient [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 18 18 24 255))
  (let [{:keys [label-size]
         :as d} (grad/dimensions m)
        labels ["vertical, top pair equal"
                "horizontal, left pair equal"
                "four corners, all different"
                "and turning, so it is per pixel"]]
    (dotimes [i grad/bands]
      (let [[x y w h] (grad/band-rect d i)
            [tl tr br bl] (grad/corners i t)
            col (fn [hue] (let [[r g b] (grad/hsv->rgb hue)] (rl/rgba r g b 255)))]
        (rl/draw-gradient-quad x y w h (col tl) (col tr) (col br) (col bl))
        (rl/draw-text (nth labels i)
                      (int (+ x (* 0.03 w))) (int (+ y (* 0.04 h)))
                      label-size (rl/rgba 255 255 255 220))))))
