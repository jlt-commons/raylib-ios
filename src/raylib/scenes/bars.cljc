(ns raylib.scenes.bars
  "Five bars, each rounded by a different amount on its left and right ends, each
  filled with a horizontal gradient. Ported from raylib-jlt's
  `rectangle_advanced`, itself raylib's `shapes_rectangle_advanced.c`.

  raylib has no call for this and the C example builds it from rlgl by hand, for
  the same reason everything else here reaches for rlgl: `DrawRectangleRounded`
  takes a Rectangle by value and accepts one roundness and one colour, and this
  wants two of each.

  The shape is a triangle fan from the centre over a walked outline. Each corner
  is a quarter arc at whatever radius its own side asked for, and the straight
  runs between them fall out of the same loop, so a roundness of 0 collapses to a
  right angle with no special case. That is the part worth keeping: one loop
  draws a square, a lozenge, and everything between.

  The gradient is free once the fan exists, because colour is interpolated per
  vertex from each point's own x. See `rounded` for the single-roundness version
  and `gradient` for the same per-vertex colouring on a plain quad."
  (:require [clojure.string]))

(def segments 36)
(def bar-count 5)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        margin (* 0.08 w)
        gap (* 0.022 h)
        bar-w (- w (* 2 margin))
        bar-h (/ (- (* 0.66 h) (* (dec bar-count) gap)) bar-count)]
    {:w (double w) :h (double h)
     :x margin :y (* 0.16 h)
     :bar-w bar-w :bar-h bar-h :gap gap
     :label-size (max 18 (int (* 0.024 (min w h))))}))

(defn bar-rect [{:keys [x y bar-w bar-h gap]} i]
  [x (+ y (* i (+ bar-h gap))) bar-w bar-h])

(defn roundness
  "The left and right roundness of bar `i`, each 0 to 1.

  Left grows down the stack while right shrinks, so the five bars between them
  cover square, both ends round, and each one-sided case."
  [i]
  (let [n (dec bar-count)]
    [(/ (double i) n) (- 1.0 (/ (double i) n))]))

(defn outline
  "The perimeter as [x y] points, walked clockwise from the top-left corner.

  Roundness is scaled against half the shorter dimension, so a fully rounded
  short bar becomes a lozenge rather than overshooting into itself. A corner
  whose radius is zero contributes `per`+1 identical points, which the fan
  renders as a right angle: degenerate triangles have no area and disappear,
  which is why no special case is needed."
  [x y w h round-left round-right]
  (let [half (* 0.5 (min w h))
        rl (* half (max 0.0 (min 1.0 (double round-left))))
        rr (* half (max 0.0 (min 1.0 (double round-right))))
        per (max 1 (quot segments 4))
        arc (fn [cx cy r from-deg]
              (map (fn [i]
                     (let [a (Math/toRadians (+ from-deg (* 90.0 (/ (double i) per))))]
                       [(+ cx (* r (Math/cos a))) (+ cy (* r (Math/sin a)))]))
                   (range (inc per))))]
    (vec (concat (arc (+ x rl) (+ y rl) rl 180.0)
                 (arc (- (+ x w) rr) (+ y rr) rr 270.0)
                 (arc (- (+ x w) rr) (- (+ y h) rr) rr 0.0)
                 (arc (+ x rl) (- (+ y h) rl) rl 90.0)))))

(defn shade
  "Interpolate between two colours by a point's position across the bar."
  [x w px [lr lg lb] [rr rg rb]]
  (let [t (max 0.0 (min 1.0 (/ (- (double px) x) w)))
        mix (fn [a b] (long (+ a (* (- b a) t))))]
    [(mix lr rr) (mix lg rg) (mix lb rb)]))

(def left-colour [230 41 55])
(def right-colour [0 121 241])

(defn advance [state input]
  (update state :t + (max 0.0 (double (:delta-seconds input 0.0)))))

(defn- init [_] [{:t 0.0} [[:scene/init :bars]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :bars]]])

(defn scene []
  {:id :bars :title "Rounded Bars"
   :init init :update update-scene :draw draw :dispose dispose})
