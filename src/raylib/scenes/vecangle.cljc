(ns raylib.scenes.vecangle
  "Two vectors from one origin, with the signed angle between them filled as an
  arc and read out in degrees. Ported from raylib-jlt's `vector_angle`, itself
  raylib's `shapes_vector_angle`.

  The angle is signed, and that is the whole subject. An unsigned angle is a
  distance and tells you nothing about which way to turn; a signed one is a
  rotation. The readout swings through zero and past 180 into negative, and the
  filled arc follows it, so the sign is visible rather than asserted.

  Screen space runs clockwise from up, because y grows downward, so the bearing
  of a vector is `atan2(x, -y)` rather than the `atan2(y, x)` of a maths
  textbook. Getting that backwards produces angles that look plausible and turn
  the wrong way, which is the kind of bug a still screenshot cannot show."
  (:require [clojure.string]))

(def turn-rate 40.0)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:w (double w) :h (double h)
     :cx (* 0.5 w) :cy (* 0.45 h)
     :length (* 0.30 (min w h))
     :arc (* 0.16 (min w h))
     :thick (* 0.010 w)
     :label-size (max 22 (int (* 0.032 (min w h))))}))

(defn bearing
  "The compass bearing of [x y] in screen space: 0 is up, growing clockwise.

  `atan2(x, -y)`, not `atan2(y, x)`. The screen's y axis points down, so the
  textbook form runs anticlockwise from the right and every angle here would be
  measured from the wrong axis in the wrong direction."
  [[x y]]
  (Math/toDegrees (Math/atan2 (double x) (- (double y)))))

(defn signed-between
  "The signed turn from bearing `a` to bearing `b`, in (-180, 180].

  Wrapped so the answer is always the shorter way round. Without the wrap, a
  turn from 350 to 10 degrees reads as -340 rather than +20, which is the same
  rotation described the long way and looks like the vector jumped."
  [a b]
  (let [d (mod (- (double b) a) 360.0)]
    (if (> d 180.0) (- d 360.0) d)))

(defn vectors
  "The fixed vector A and the turning vector B at time `t`."
  [{:keys [length]} t]
  (let [b (Math/toRadians (* turn-rate t))]
    {:a [0.0 (- length)]
     :b [(* length (Math/sin b)) (- (* length (Math/cos b)))]}))

(defn advance [state input]
  (update state :t + (max 0.0 (double (:delta-seconds input 0.0)))))

(defn- init [_] [{:t 0.0} [[:scene/init :vecangle]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :vecangle]]])

(defn scene []
  {:id :vecangle :title "Vector Angle"
   :init init :update update-scene :draw draw :dispose dispose})
