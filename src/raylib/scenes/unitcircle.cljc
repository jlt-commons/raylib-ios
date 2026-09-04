(ns raylib.scenes.unitcircle
  "Sine and cosine as projections of a rotating radius, ported from raylib-jlt's
  `math_sine_cosine`.

  A point goes round the unit circle. Its height above the horizontal axis is
  the sine of the angle and its distance along that axis is the cosine, and both
  are drawn as the actual segments rather than described.

  The original stops at the circle, which on a landscape window is the whole
  picture. A phone is tall and leaves most of the screen empty, so the two
  projections are also traced as waves underneath, sharing the circle's vertical
  axis: the height of the blue wave at any moment is the length of the blue
  segment above it. Watching the segment and the wave move together is the point
  the still picture cannot make.

  The trace is a ring buffer of past values rather than a recomputation, so the
  wave is a record of what actually happened rather than a plot of what should
  have."
  (:require [clojure.string]))

(def trace-length 260)
(def radians-per-frame 0.02)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        radius (* 0.36 (min w h))]
    {:w w :h h
     :cx (* 0.5 w)
     :cy (* 0.30 h)
     :radius radius
     ;; the waves live under the circle, sharing its centre line
     :trace-top (+ (* 0.30 h) radius (* 0.06 h))
     :trace-height (* 0.22 h)
     :amplitude (* 0.10 h)}))

(defn point-at
  "Where the radius is pointing, as [x y]. Screen y grows downward, so sine is
  subtracted rather than added."
  [{:keys [cx cy radius]} angle]
  [(+ cx (* radius (Math/cos angle)))
   (- cy (* radius (Math/sin angle)))])

(defn advance
  "Step the angle and push both projections onto the trace."
  [state]
  (let [a (+ (:angle state) radians-per-frame)
        trace (:trace state)
        trace' (if (>= (count trace) trace-length)
                 (conj (subvec trace 1) [(Math/sin a) (Math/cos a)])
                 (conj trace [(Math/sin a) (Math/cos a)]))]
    (assoc state :angle a :trace trace')))

(defn wave-points
  "The trace as screen points for one of the two channels, oldest on the left.

  `pick` is first for sine and second for cosine. Returns [x y] pairs, so the
  caller draws segments between consecutive entries rather than working out any
  geometry itself."
  [{:keys [w trace-top trace-height amplitude]} trace pick centre-fraction]
  (let [n (max 1 (count trace))
        mid (+ trace-top (* centre-fraction trace-height))
        step (/ (double w) trace-length)]
    (mapv (fn [i]
            [(* i step) (- mid (* amplitude (pick (nth trace i))))])
          (range n))))

(defn- init [_] [{:angle 0.0 :trace []} [[:scene/init :unitcircle]]])
(defn- update-scene [state _] [(advance state) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :unitcircle]]])

(defn scene []
  {:id :unitcircle :title "Sine & Cosine"
   :init init :update update-scene :draw draw :dispose dispose})
