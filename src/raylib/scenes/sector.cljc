(ns raylib.scenes.sector
  "A pie slice degrading into a triangle as its segment count falls. Ported from
  raylib-jlt's `circle_sector_drawing`, itself raylib's
  `shapes_circle_sector_drawing.c`.

  The point of the example is a piece of arithmetic rather than a picture.
  raylib needs at least one segment per 90 degrees of arc to draw something that
  still reads as a curve, and when handed fewer it computes that floor itself as
  `ceil((end - start) / 90)`. Below the floor your number is ignored and the
  floor is used, which the original labels AUTO. At or above it, your number is
  what draws.

  The C drives all four values with raygui sliders and raylib-jlt swapped those
  for keys, neither of which a phone has. So the segment count sweeps on a timer
  instead, down from smooth to the floor and back, and the readout says which
  number is in force. The arithmetic being demonstrated is untouched, which is
  the part worth keeping."
  (:require [clojure.string]))

(def max-segments 36)
(def min-shown 1)
(def sweep-seconds 9.0)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:w (double w) :h (double h)
     :cx (* 0.5 w) :cy (* 0.46 h)
     :radius (* 0.34 (min w h))
     :label-size (max 22 (int (* 0.036 (min w h))))}))

(defn auto-floor
  "raylib's own minimum for an arc of this width, one segment per 90 degrees."
  [start-deg end-deg]
  (max 1 (long (Math/ceil (/ (- (double end-deg) start-deg) 90.0)))))

(defn resolve-segments
  "What raylib will actually draw with, and whether it took your number.

  Below the floor the request is discarded, which is the whole lesson: asking
  for 2 segments across 270 degrees does not give you 2."
  [start-deg end-deg requested]
  (let [floor (auto-floor start-deg end-deg)
        manual? (>= requested floor)]
    {:segments (max 1 (if manual? requested floor))
     :floor floor
     :mode (if manual? :requested :auto)}))

(defn- triangle-wave
  "0 to 1 and back, once per period."
  [t period]
  (let [x (/ (mod t period) period)]
    (if (< x 0.5) (* 2.0 x) (- 2.0 (* 2.0 x)))))

(defn advance [state input]
  (let [t (+ (:t state 0.0) (max 0.0 (double (:delta-seconds input 0.0))))
        ;; the arc widens slowly so the floor itself moves, which is the only
        ;; way to see that the floor depends on the arc and not on the request
        end-angle (+ 90.0 (* 260.0 (triangle-wave t (* 3.7 sweep-seconds))))
        requested (long (+ min-shown (Math/round (* (- max-segments min-shown)
                                                    (triangle-wave t sweep-seconds)))))]
    (assoc state :t t :start-angle 0.0 :end-angle end-angle :requested requested)))

(defn- init [_]
  [{:t 0.0 :start-angle 0.0 :end-angle 270.0 :requested max-segments}
   [[:scene/init :sector]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :sector]]])

(defn scene []
  {:id :sector :title "Circle Sector"
   :init init :update update-scene :draw draw :dispose dispose})
