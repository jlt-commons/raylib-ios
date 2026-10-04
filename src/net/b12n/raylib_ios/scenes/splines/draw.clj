(ns net.b12n.raylib-ios.scenes.splines.draw
  "The draw-scene! method for the `:splines` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.splines :as spl]))

(defmethod draw-scene! :splines [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 245 245 245 255))
  ;; Evaluated straight into the vertex stream. The first version called
  ;; spl/curve to build a vector of 164 points per basis and then partitioned it
  ;; into pairs, which is about a thousand allocations a frame across the three,
  ;; and ran at 20 fps. spl/curve stays because the tests inspect its output;
  ;; the draw path carries the previous point in locals instead.
  (let [{:keys [dot thick label-size w h]
         :as d} (spl/dimensions m)
        pts (spl/points d t)
        n (count pts)
        at (fn [i] (nth pts (max 0 (min (dec n) i))))
        half (* 0.5 thick)
        rgb [[230 41 55] [0 121 241] [0 158 47]]]
    (doseq [[ci [_ f]] (map-indexed vector spl/kinds)]
      (let [[cr cg cb] (nth rgb ci)]
        (rl/rl-begin rl/RL-TRIANGLES)
        (rl/rl-color-4ub cr cg cb 255)
        (dotimes [i (dec n)]
          (let [[ax ay] (at (dec i)) [bx by] (at i)
                [cx cy] (at (inc i)) [dx dy] (at (+ i 2))]
            (loop [s 1
                   px (double (f ax bx cx dx 0.0))
                   py (double (f ay by cy dy 0.0))]
              (when (<= s spl/samples)
                (let [tt (/ (double s) spl/samples)
                      qx (double (f ax bx cx dx tt))
                      qy (double (f ay by cy dy tt))
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
                  (recur (inc s) qx qy))))))
        (rl/rl-end)))
    ;; The control points last, on top, so it is obvious which curves touch them.
    (doseq [[x y] pts]
      (rl/draw-circle (int x) (int y) (float dot) (rl/rgba 40 40 40 255))
      (rl/draw-circle-lines (int x) (int y) (float (* 1.9 dot)) (rl/rgba 40 40 40 255)))
    (doseq [[i [nm _]] (map-indexed vector spl/kinds)]
      (let [[cr cg cb] (nth rgb i)]
        (rl/draw-text nm (int (* 0.08 w))
                      (int (- h (* 0.22 h) (* (- 2 i) (+ label-size 14))))
                      label-size (rl/rgba cr cg cb 255))))))
