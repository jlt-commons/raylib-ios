(ns net.b12n.raylib-ios.scenes.multitouch.draw
  "The draw-scene! method for the `:multitouch` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.multitouch :as multi]))

(defmethod draw-scene! :multitouch [_ {:keys [trails live peak colours]} {:keys [m]}]
  (rl/clear-background rl/RAYWHITE)
  (let [{:keys [label-size count-size touch-radius dot-radius centre-radius label-lift]}
        (multi/dimensions m)
        n (count live)]
    ;; Trails first so the live circles sit on top of their own history.
    (doseq [[id trail] trails]
      (let [[r g b] (multi/colour-for (get colours id))
            len (count trail)]
        (dotimes [i len]
          (let [[x y] (nth trail i)
                a (int (* 150 (/ (double (inc i)) len)))]
            (rl/draw-circle (int x) (int y) (float dot-radius) (rl/rgba r g b a))))))
    (doseq [[id [x y]] live]
      (let [[r g b] (multi/colour-for (get colours id))
            ix (int x) iy (int y)]
        (rl/draw-circle ix iy (float touch-radius) (rl/rgba r g b 70))
        (rl/draw-circle-lines ix iy (float touch-radius) (rl/rgba r g b 255))
        (rl/draw-circle ix iy (float centre-radius) (rl/rgba r g b 255))
        (rl/draw-text (str "id " id) (- ix (int (* 2.2 label-size))) (- iy (int label-lift))
                      label-size (rl/rgba r g b 255))))
    ;; Bottom left, for the third time in this file: the host owns the top left
    ;; for Back, and the first version of every one of these labels sat under it.
    (let [[sw sh] (:screen m)
          x (int (* 0.04 sw))
          y0 (int (- sh (* 0.055 sh) (* 2 (+ label-size 10)) count-size))]
      (rl/draw-text (str n " touch " (if (= 1 n) "point" "points"))
                    x y0 count-size rl/DARKGRAY)
      (rl/draw-text (if (zero? n)
                      "put fingers on the glass"
                      (str "most at once so far: " peak))
                    x (+ y0 count-size 10) label-size (rl/rgba 130 130 130 255))
      ;; A phone reports every point. The desktop example this came from could
      ;; only ever draw one, and said so.
      (rl/draw-text (str "all " n " positions read, not just point 0")
                    x (+ y0 count-size 10 label-size 10) label-size
                    (rl/rgba 130 130 130 255)))))
