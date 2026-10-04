(ns net.b12n.raylib-ios.scenes.bezier.draw
  "The draw-scene! method for the `:bezier` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.bezier :as bez]))

(defmethod draw-scene! :bezier [_ {:keys [end touching?]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  (let [{:keys [anchor dot thick label-size w h]} (bez/dimensions m)
        ctrl (bez/controls anchor end)
        [[ax ay] [bx by] [cx cy] [dx dy]] ctrl
        half (* 0.5 thick)]
    ;; The control polygon first, thin and grey, so the curve reads against it.
    (doseq [[[x1 y1] [x2 y2]] (partition 2 1 ctrl)]
      (rl/draw-line (int x1) (int y1) (int x2) (int y2) (rl/rgba 190 190 195 255)))
    ;; The curve, sampled inline into one batch. bez/curve exists for the tests.
    (rl/rl-begin rl/RL-TRIANGLES)
    (rl/rl-color-4ub 0 121 241 255)
    (loop [i 1 px (double ax) py (double ay)]
      (when (<= i bez/samples)
        (let [[qx qy] (bez/at ctrl (/ (double i) bez/samples))
              ex (- qx px) ey (- qy py)
              len (Math/sqrt (+ (* ex ex) (* ey ey)))]
          (when (pos? len)
            ;; draw-line-ex's vertex order, copied not rederived
            (let [ox (* half (/ ey len)) oy (* half (/ (- ex) len))]
              (rl/rl-vertex-2f (float (+ px ox)) (float (+ py oy)))
              (rl/rl-vertex-2f (float (- px ox)) (float (- py oy)))
              (rl/rl-vertex-2f (float (- qx ox)) (float (- qy oy)))
              (rl/rl-vertex-2f (float (+ px ox)) (float (+ py oy)))
              (rl/rl-vertex-2f (float (- qx ox)) (float (- qy oy)))
              (rl/rl-vertex-2f (float (+ qx ox)) (float (+ qy oy)))))
          (recur (inc i) (double qx) (double qy)))))
    (rl/rl-end)
    (doseq [[[x y] fill?] [[[ax ay] true] [[bx by] false] [[cx cy] false] [[dx dy] true]]]
      (if fill?
        (rl/draw-circle (int x) (int y) (float dot) (rl/rgba 230 41 55 255))
        (rl/draw-circle-lines (int x) (int y) (float dot) (rl/rgba 130 130 135 255))))
    (rl/draw-text (if touching? "following your finger" "drag anywhere")
                  (int (* 0.08 w)) (int (- h (* 0.14 h)))
                  label-size (rl/rgba 60 60 60 255))))
