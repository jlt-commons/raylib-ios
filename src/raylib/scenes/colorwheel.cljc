(ns raylib.scenes.colorwheel
  "An HSV colour wheel drawn as an rlgl triangle fan, ported from raylib-jlt's
  `color_wheel`.

  Every other scene here draws outlines. This one fills, and it is the only
  reason the host binds rlgl's immediate mode at all: raylib's shapes API has no
  call for a triangle with a different colour at each corner, and the gradient
  around the rim is exactly that. Each slice is one triangle, rim to rim to
  centre, with the two rim vertices carrying their own hue and the centre white.
  The card between them is interpolated by the rasteriser for free.

  Saturation and value are both pinned at one, so this is the outer surface of
  the HSV cylinder: every hue at full strength. The wheel turns because the hue
  offset advances, not because anything is rotated.

  Cheap, at three vertices a slice. The slice count is high enough that the rim
  reads as a curve rather than a polygon."
  (:require [clojure.string]))

(def slices 180)
(def degrees-per-frame 0.35)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:cx (* 0.5 w)
     :cy (* 0.5 h)
     ;; the short side, so it fits whichever way the phone is held
     :radius (* 0.42 (min w h))}))

(defn hsv->rgb
  "Hue in degrees to [r g b], at full saturation and value.

  The usual six-case form. `i` picks which sixth of the wheel the hue is in,
  and `f` is how far through it: in every sixth one channel is full, one is
  empty, and the third ramps between them, which is what makes the six cases
  look repetitive without being the same."
  [hue]
  (let [h (/ (mod hue 360.0) 60.0)
        i (int (Math/floor h))
        f (- h i)
        q (- 1.0 f)
        [r g b] (case i
                  0 [1.0 f 0.0]
                  1 [q 1.0 0.0]
                  2 [0.0 1.0 f]
                  3 [0.0 q 1.0]
                  4 [f 0.0 1.0]
                  [1.0 0.0 q])]
    [(int (* 255 r)) (int (* 255 g)) (int (* 255 b))]))

(defn slice
  "One slice as [x0 y0 hue0 x1 y1 hue1], rim points and their hues.

  Angles start at twelve o'clock and go clockwise, which is why sine is the x
  term and cosine the negated y one."
  [{:keys [cx cy radius]} offset i]
  (let [t (/ (* 2.0 Math/PI) slices)
        a0 (* t i)
        a1 (* t (inc i))
        hue (fn [k] (+ offset (* 360.0 (/ (double k) slices))))]
    [(+ cx (* radius (Math/sin a0))) (- cy (* radius (Math/cos a0))) (hue i)
     (+ cx (* radius (Math/sin a1))) (- cy (* radius (Math/cos a1))) (hue (inc i))]))

(defn- init [_] [{:offset 0.0} [[:scene/init :colorwheel]]])
(defn- update-scene [state _] [(update state :offset + degrees-per-frame) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :colorwheel]]])

(defn scene []
  {:id :colorwheel :title "Colour Wheel"
   :init init :update update-scene :draw draw :dispose dispose})
