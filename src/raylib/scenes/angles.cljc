(ns raylib.scenes.angles
  "A ring of fixed spokes and one that turns, ported from raylib-jlt's
  `math_angle_rotation`.

  The smallest scene here, and the one that says the most about how everything
  else is drawn. Every circular thing in this gallery is the same two lines:

      x = cx + r * cos(a)
      y = cy + r * sin(a)

  The kaleidoscope, the epicycles, the colour wheel, the pie chart and the
  tesseract's projection are all that, with different values of `a`. This one
  draws it plainly so the rest are easier to read.

  Screen y grows downward, so a positive angle sweeps clockwise here where the
  same maths on graph paper goes anticlockwise. Nothing compensates for it: the
  spokes are evenly spaced either way, and pretending otherwise would mean a
  minus sign that has to be explained everywhere it appears."
  (:require [clojure.string]))

(def spokes 12)
(def radians-per-frame 0.012)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:cx (* 0.5 w)
     :cy (* 0.5 h)
     :radius (* 0.40 (min w h))}))

(defn spoke-end
  "Where a spoke at angle `a` meets the rim."
  [{:keys [cx cy radius]} a]
  [(+ cx (* radius (Math/cos a)))
   (+ cy (* radius (Math/sin a)))])

(defn fixed-angles
  "The evenly spaced spokes, one full turn divided `spokes` ways."
  []
  (mapv #(* (/ (* 2.0 Math/PI) spokes) %) (range spokes)))

(defn- init [_] [{:angle 0.0} [[:scene/init :angles]]])
(defn- update-scene [state _] [(update state :angle + radians-per-frame) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :angles]]])

(defn scene []
  {:id :angles :title "Angles"
   :init init :update update-scene :draw draw :dispose dispose})
