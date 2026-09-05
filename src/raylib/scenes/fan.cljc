(ns raylib.scenes.fan
  "A turning fan of thick lines, each a different width and colour. Ported from
  raylib-jlt's `lines_drawing`, itself raylib's `shapes_lines_drawing` without
  its texture cursor.

  The subject is line thickness, which sounds like nothing until you try to draw
  a thick line. raylib's `DrawLineEx` takes both endpoints as by-value Vector2,
  so this project builds thick lines from rlgl quads, and every one of those
  quads has to be wound correctly or it vanishes. A fan of sixteen at sixteen
  widths is a direct test of that: any width that fails to appear is a bug, and
  it is obvious which.

  Widths run from under a pixel to a fat bar. The thin end matters most, because
  a quad narrower than a pixel is where a rounding error stops being invisible
  and starts being a gap in the fan."
  (:require [clojure.string]))

(def spokes 16)
(def turn-rate 18.0)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:w (double w) :h (double h)
     :cx (* 0.5 w) :cy (* 0.44 h)
     :inner (* 0.06 (min w h))
     :outer (* 0.36 (min w h))
     :max-thick (* 0.020 w)
     :label-size (max 20 (int (* 0.028 (min w h))))}))

(defn spoke
  "Spoke `i` at time `t`, as {:from [x y] :to [x y] :thick n :hue deg}.

  Thickness grows around the fan rather than at random, so a missing one is
  read as a gap at a known width rather than as noise."
  [{:keys [cx cy inner outer max-thick]} t i]
  (let [frac (/ (double i) spokes)
        a (Math/toRadians (+ (* turn-rate t) (* 360.0 frac)))
        s (Math/sin a) c (Math/cos a)]
    {:from [(+ cx (* inner s)) (- cy (* inner c))]
     :to [(+ cx (* outer s)) (- cy (* outer c))]
     ;; from a quarter of a pixel to the full width
     :thick (* max-thick (+ 0.02 (* 0.98 frac)))
     :hue (* 360.0 frac)}))

(defn hsv->rgb
  "Hue in degrees to [r g b] at full saturation and value."
  [deg]
  (let [h (/ (mod deg 360.0) 60.0)
        i (long h)
        f (- h i)
        q (long (* 255 (- 1.0 f)))
        u (long (* 255 f))]
    (case i
      0 [255 u 0] 1 [q 255 0] 2 [0 255 u]
      3 [0 q 255] 4 [u 0 255] [255 0 q])))

(defn advance [state input]
  (update state :t + (max 0.0 (double (:delta-seconds input 0.0)))))

(defn- init [_] [{:t 0.0} [[:scene/init :fan]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :fan]]])

(defn scene []
  {:id :fan :title "Line Widths"
   :init init :update update-scene :draw draw :dispose dispose})
