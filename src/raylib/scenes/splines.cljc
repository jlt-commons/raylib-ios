(ns raylib.scenes.splines
  "Three spline bases over the same five control points. Ported from raylib-jlt's
  `splines`, itself raylib's `shapes_splines_drawing` minus its raygui controls.

  raylib's own `DrawSpline*` take Vector2 arrays by value, which nothing here can
  bind, so the curve is evaluated in Clojure and drawn as a polyline. That turns
  out to be the better version for showing what the example is about, because the
  three bases can then be drawn over each other from identical control points.

  Catmull-Rom passes through its control points. The cubic Bezier and the uniform
  B-spline do not, and the B-spline stays furthest away. Seeing that is the whole
  lesson, and it only reads if the three share their points, which is why the
  original cycles between them with SPACE and this draws all three at once. A
  phone has no SPACE, and the comparison is stronger anyway."
  (:require [clojure.string]))

(def control-points 5)
(def samples 40)

(defn catmull-rom
  "Passes through b and c. The tangent at each point comes from its neighbours."
  [a b c d t]
  (let [t2 (* t t) t3 (* t2 t)]
    (* 0.5 (+ (* 2.0 b)
              (* (+ (- a) c) t)
              (* (+ (* 2.0 a) (* -5.0 b) (* 4.0 c) (- d)) t2)
              (* (+ (- a) (* 3.0 b) (* -3.0 c) d) t3)))))

(defn bezier
  "Interpolates a and d, only approaches b and c."
  [a b c d t]
  (let [u (- 1.0 t)]
    (+ (* u u u a) (* 3.0 u u t b) (* 3.0 u t t c) (* t t t d))))

(defn b-spline
  "Approaches none of its control points, which is what buys it smoothness."
  [a b c d t]
  (let [t2 (* t t) t3 (* t2 t)]
    (/ (+ (* (+ (- a) (* 3.0 b) (* -3.0 c) d) t3)
          (* (+ (* 3.0 a) (* -6.0 b) (* 3.0 c)) t2)
          (* (+ (* -3.0 a) (* 3.0 c)) t)
          (+ a (* 4.0 b) c))
       6.0)))

(def kinds
  [["Catmull-Rom" catmull-rom]
   ["cubic Bezier" bezier]
   ["uniform B-spline" b-spline]])

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:w (double w) :h (double h)
     :x0 (* 0.10 w) :span (* 0.80 w)
     :cy (* 0.46 h)
     :amplitude (* 0.16 h)
     :dot (* 0.011 w)
     :thick (* 0.006 w)
     :label-size (max 20 (int (* 0.028 (min w h))))}))

(defn points
  "The five control points at time `t`, bobbing at different rates so the curves
  keep changing shape rather than sliding."
  [{:keys [x0 span cy amplitude]} t]
  (mapv (fn [i]
          [(+ x0 (* span (/ (double i) (dec control-points))))
           (+ cy (* amplitude (Math/sin (+ (* 0.9 t) (* 1.3 i)))))])
        (range control-points)))

(defn curve
  "The polyline for one basis over `pts`, as [[x y] ...].

  Each span needs four control points, so the ends are duplicated rather than
  dropped. Dropping them would shorten every curve by one span and make the
  three impossible to compare at the edges, which is where they differ most."
  [f pts]
  (let [n (count pts)
        at (fn [i] (nth pts (max 0 (min (dec n) i))))]
    (vec (for [i (range (dec n))
               s (range (inc samples))
               :let [t (/ (double s) samples)
                     [ax ay] (at (dec i)) [bx by] (at i)
                     [cx cy] (at (inc i)) [dx dy] (at (+ i 2))]]
           [(f ax bx cx dx t) (f ay by cy dy t)]))))

(defn advance [state input]
  (update state :t + (max 0.0 (double (:delta-seconds input 0.0)))))

(defn- init [_] [{:t 0.0} [[:scene/init :splines]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :splines]]])

(defn scene []
  {:id :splines :title "Splines"
   :init init :update update-scene :draw draw :dispose dispose})
