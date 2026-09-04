(ns raylib.scenes.gradient
  "Colour interpolated across a rectangle. Ported from raylib-jlt's `gradient`,
  itself raylib's `shapes_rectangle_gradient.c`.

  The original draws one vertical band with `DrawRectangleGradientV`, which the
  docstring there calls a good check that two by-value Colors survive a single
  call. That check does not apply here, because nothing in this project passes a
  Color by value: they go across the FFI packed into a uint.

  So the scene grew into what the technique is actually for. raylib spends three
  entry points on this shape, V, H and Ex, and one rlgl quad with a colour per
  vertex covers all three: a vertical gradient is the four-corner case with the
  top pair equal. The bands below are V, H, a four-corner Ex, and one whose
  corners rotate through the wheel so the interpolation is visible in motion."
  (:require [clojure.string]))

(def bands 4)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        margin (* 0.06 w)
        ;; The top margin is its own number, and derived from the HEIGHT. Using
        ;; the width-derived margin put the first band at y 72, underneath the
        ;; host's Back button at 40 to 113, so its label was unreadable. Every
        ;; scene here that puts anything near the top has made this mistake once.
        top (* 0.075 h)
        gap (* 0.03 h)
        band-w (- w (* 2 margin))
        band-h (/ (- h top margin (* (dec bands) gap)) bands)]
    {:w (double w) :h (double h)
     :margin margin :top top :gap gap
     :band-w band-w :band-h band-h
     :label-size (max 18 (int (* 0.024 (min w h))))}))

(defn band-rect [{:keys [margin top gap band-w band-h]} i]
  [margin (+ top (* i (+ band-h gap))) band-w band-h])

(defn hsv->rgb
  "Hue in degrees to [r g b], full saturation and value.

  The same six-sector walk the colour wheel uses. Kept here rather than shared
  because the two scenes want different signatures and neither is the other's
  helper."
  [deg]
  (let [h (/ (mod deg 360.0) 60.0)
        i (long h)
        f (- h i)
        q (long (* 255 (- 1.0 f)))
        t (long (* 255 f))]
    (case i
      0 [255 t 0] 1 [q 255 0] 2 [0 255 t]
      3 [0 q 255] 4 [t 0 255] [255 0 q])))

(defn corners
  "The four corner hues for band `i` at time `t`, as [tl tr br bl].

  Bands 0 to 2 are static and show what the three raylib calls each do. Band 3
  turns, which is the only way to see that the interpolation is happening per
  pixel rather than being a fixed image."
  [i t]
  (case (long i)
    0 [200.0 200.0 280.0 280.0]                 ; vertical: top pair equal
    1 [200.0 320.0 320.0 200.0]                 ; horizontal: left pair equal
    2 [0.0 90.0 180.0 270.0]                    ; four corners, all different
    (let [a (* 40.0 t)]
      [a (+ a 90.0) (+ a 180.0) (+ a 270.0)])))

(defn advance [state input]
  (update state :t + (max 0.0 (double (:delta-seconds input 0.0)))))

(defn- init [_] [{:t 0.0} [[:scene/init :gradient]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :gradient]]])

(defn scene []
  {:id :gradient :title "Gradients"
   :init init :update update-scene :draw draw :dispose dispose})
