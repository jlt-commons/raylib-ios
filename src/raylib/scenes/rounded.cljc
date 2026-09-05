(ns raylib.scenes.rounded
  "A rectangle with quarter-circle corners, its radius breathing from square to
  fully round. Ported from raylib-jlt's `rounded_rectangle`, itself raylib's
  `shapes_rounded_rectangle_drawing`.

  raylib's `DrawRectangleRounded` takes a Rectangle by value and cannot be bound
  here, so the shape is assembled: a cross of two rectangles covering the middle,
  plus four quarter disks at the corners. That decomposition is the example.

  The interesting part is the degenerate ends. At radius 0 the corner disks
  vanish and the cross has to be exactly the rectangle, with no seam. At the
  maximum, radius is half the shorter side and the two arms of the cross meet
  along a single line, so any rounding error shows as a gap. Both ends are
  tested, because both are where an off-by-one in the decomposition hides."
  (:require [clojure.string]))

(def cycle-seconds 4.0)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        rw (* 0.72 w)
        rh (* 0.42 h)]
    {:w (double w) :h (double h)
     :x (* 0.5 (- w rw)) :y (* 0.5 (- h rh))
     :rect-w rw :rect-h rh
     :max-radius (* 0.5 (min rw rh))
     :label-size (max 20 (int (* 0.028 (min w h))))}))

(defn radius-at
  "0 to the maximum and back, so both degenerate ends are visited every cycle."
  [{:keys [max-radius]} t]
  (let [x (/ (mod t cycle-seconds) cycle-seconds)
        tri (if (< x 0.5) (* 2.0 x) (- 2.0 (* 2.0 x)))]
    (* max-radius tri)))

(defn parts
  "The shape as [:rects [[x y w h] ...] :corners [[cx cy start end] ...]].

  Two rectangles, not three: a tall one spanning the full height between the
  left and right corner columns, and a wide one spanning the full width between
  the top and bottom corner rows. They overlap in the middle, which costs one
  extra fill and removes every chance of a seam."
  [{:keys [x y rect-w rect-h]} r]
  (let [r (max 0.0 (double r))
        x1 (+ x rect-w) y1 (+ y rect-h)]
    {:rects [[(+ x r) y (- rect-w (* 2 r)) rect-h]
             [x (+ y r) rect-w (- rect-h (* 2 r))]]
     ;; zero points up and the angle grows clockwise, matching draw-ring
     :corners (when (pos? r)
                [[(+ x r) (+ y r) 270.0 360.0]
                 [(- x1 r) (+ y r) 0.0 90.0]
                 [(- x1 r) (- y1 r) 90.0 180.0]
                 [(+ x r) (- y1 r) 180.0 270.0]])}))

(defn advance [state input]
  (update state :t + (max 0.0 (double (:delta-seconds input 0.0)))))

(defn- init [_] [{:t 0.0} [[:scene/init :rounded]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :rounded]]])

(defn scene []
  {:id :rounded :title "Rounded Rect"
   :init init :update update-scene :draw draw :dispose dispose})
