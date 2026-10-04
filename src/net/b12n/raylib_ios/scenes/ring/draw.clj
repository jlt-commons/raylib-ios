(ns net.b12n.raylib-ios.scenes.ring.draw
  "The draw-scene! method for the `:ring` scene, beside the scene it draws."
  (:require [net.b12n.raylib-ios.gallery.draw-util :refer [draw-scene!]]
            [net.b12n.raylib-ios.host :as rl]
            [net.b12n.raylib-ios.scenes.ring :as ring]))

(defmethod draw-scene! :ring [_ {:keys [t]} {:keys [m]}]
  (rl/clear-background (rl/rgba 22 24 30 255))
  ;; The outline is one rlgl batch with nothing allocated in it. The first
  ;; version built two 97-point vectors with arc-points and partitioned them
  ;; into pairs, then called draw-line-ex per segment: about 200 vectors and
  ;; 1700 FFI calls a frame, for 40 fps. Same trade the clock grid made, and
  ;; the same answer. arc-points stays, because the tests use it to assert the
  ;; stroke actually follows the arc.
  (let [{:keys [cx cy outer thick label-size w h]
         :as d} (ring/dimensions m)
        {:keys [inner start end]} (ring/geometry d t)
        n ring/outline-segments
        span (- end start)
        half (* 0.5 (double thick))
        seg (fn [x1 y1 x2 y2]
              (let [dx (- x2 x1) dy (- y2 y1)
                    len (Math/sqrt (+ (* dx dx) (* dy dy)))]
                (when (pos? len)
                  ;; This vertex order is draw-line-ex's, copied rather than
                  ;; rederived. An earlier version here swapped two of them,
                  ;; which flips the cross product positive and culls every
                  ;; triangle: the outline simply did not appear, for the third
                  ;; time tonight. Winding is not something to reason out fresh
                  ;; each time it is written.
                  (let [px (* half (/ dy len)) py (* half (/ (- dx) len))]
                    (rl/rl-vertex-2f (float (+ x1 px)) (float (+ y1 py)))
                    (rl/rl-vertex-2f (float (- x1 px)) (float (- y1 py)))
                    (rl/rl-vertex-2f (float (- x2 px)) (float (- y2 py)))
                    (rl/rl-vertex-2f (float (+ x1 px)) (float (+ y1 py)))
                    (rl/rl-vertex-2f (float (- x2 px)) (float (- y2 py)))
                    (rl/rl-vertex-2f (float (+ x2 px)) (float (+ y2 py)))))))]
    (rl/draw-ring cx cy inner outer start end ring/fill-segments (rl/rgba 90 170 240 255))
    (rl/rl-begin rl/RL-TRIANGLES)
    ;; Light on a dark ground. The first value was 20 40 72 against 22 24 30,
    ;; which is the clock grid's invisible-bezel mistake made twice in one night.
    (rl/rl-color-4ub 226 240 255 255)
    (doseq [r [outer inner]]
      (dotimes [i n]
        (let [a0 (Math/toRadians (+ start (* span (/ (double i) n))))
              a1 (Math/toRadians (+ start (* span (/ (double (inc i)) n))))]
          (seg (+ cx (* r (Math/sin a0))) (- cy (* r (Math/cos a0)))
               (+ cx (* r (Math/sin a1))) (- cy (* r (Math/cos a1)))))))
    ;; the radial end caps, which are what make it read as a closed shape
    (doseq [deg [start end]]
      (let [a (Math/toRadians (double deg))
            s (Math/sin a) c (Math/cos a)]
        (seg (+ cx (* inner s)) (- cy (* inner c))
             (+ cx (* outer s)) (- cy (* outer c)))))
    (rl/rl-end)
    (rl/draw-text "draw-ring, an rlgl annulus"
                  (int (* 0.06 w)) (int (- h (* 0.14 h)))
                  label-size rl/RAYWHITE)))
