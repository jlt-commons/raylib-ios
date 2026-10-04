(ns net.b12n.raylib-ios.scenes.easingstestbed.draw
  "The draw-scene! method for the `:easingstestbed` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.easingstestbed :as etb]))

(defmethod draw-scene! :easingstestbed [_ {:keys [idx counter plot?]} {:keys [m]}]
  (let [pack (fn [[r g b a]] (rl/rgba r g b a))
        _ (rl/clear-background (pack etb/background-colour))
        dims (etb/dimensions m)
        f (etb/curve-fn idx)
        thick (max 1.0 (* 1.5 (:u dims)))
        [title hint1 hint2] (:lines dims)
        {:keys [plot-x plot-y plot-w plot-h ball-y ball-r]} dims
        frame (pack etb/frame-colour)
        ink (pack etb/curve-colour)]
    (rl/draw-text (etb/title-line idx) (:x title) (:y title) (:size title) (pack etb/title-colour))
    (rl/draw-text (:s hint1) (:x hint1) (:y hint1) (:size hint1) (pack etb/hint-colour))
    (rl/draw-text (:s hint2) (:x hint2) (:y hint2) (:size hint2) (pack etb/hint-colour))
    (when plot?
      ;; The [0,1] band, which a curve that overshoots visibly leaves. The
      ;; frame is four lines, since DrawRectangleLines is a hairline here.
      (let [x2 (+ plot-x plot-w)
            y2 (+ plot-y plot-h)]
        (rl/draw-line-ex plot-x plot-y x2 plot-y thick frame)
        (rl/draw-line-ex x2 plot-y x2 y2 thick frame)
        (rl/draw-line-ex x2 y2 plot-x y2 thick frame)
        (rl/draw-line-ex plot-x y2 plot-x plot-y thick frame))
      (doseq [[[ax ay] [bx by]] (partition 2 1 (etb/plot-points dims f))]
        (rl/draw-line-ex ax ay bx by thick ink)))
    (rl/draw-line-ex plot-x ball-y (+ plot-x plot-w) ball-y thick (pack etb/rail-colour))
    (let [[bx by] (etb/ball dims f counter)]
      (rl/draw-circle (int bx) (int by) (float ball-r) (pack etb/title-colour)))))
