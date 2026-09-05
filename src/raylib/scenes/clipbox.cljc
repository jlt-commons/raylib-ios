(ns raylib.scenes.clipbox
  "A grid drawn across the whole screen, with only the part inside a moving box
  visible. Ported from raylib-jlt's `scissor_test`.

  The scene exists to show the scissor, and porting it exposed something about
  how this project already uses one. Every scene here is drawn inside a scissor
  set to the phone's safe region, so the notch and home indicator stay clear. A
  scene that then sets a scissor of its own does not nest inside that: rlgl's
  scissor is a single rectangle and `BeginScissorMode` replaces it outright. So
  a scene clipping to its own box would be free to paint over the status bar the
  host had just moved it clear of.

  The fix is to intersect rather than replace, which is why `clip-rect` takes the
  safe region as well as the wanted box. Two rectangles that do not overlap
  intersect to nothing, and this returns nil for that rather than a negative
  width, because raylib reads a negative width as a very large unsigned one and
  clips nothing at all."
  (:require [clojure.string]))

(def cell 76)
(def gap 4)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:w (double w) :h (double h)
     :box-w (* 0.62 w) :box-h (* 0.30 h)
     :label-size (max 20 (int (* 0.028 (min w h))))}))

(defn box
  "The clipping box in scene coordinates, drifting so it visits the edges."
  [{:keys [w h box-w box-h]} t]
  (let [x (+ (* 0.5 (- w box-w)) (* 0.30 (- w box-w) (Math/sin (* 0.7 t))))
        y (+ (* 0.5 (- h box-h)) (* 0.34 (- h box-h) (Math/sin (* 0.43 t))))]
    [x y box-w box-h]))

(defn intersect
  "The overlap of two [x y w h] rectangles, or nil when they miss.

  nil rather than a zero or negative width. raylib takes the width as an int and
  a negative one reads as an enormous unsigned value, so a degenerate box does
  not clip to nothing, it clips to everything, which is the opposite of what the
  caller asked for and looks like the scissor being ignored."
  [[ax ay aw ah] [bx by bw bh]]
  (let [x (max ax bx) y (max ay by)
        r (min (+ ax aw) (+ bx bw))
        b (min (+ ay ah) (+ by bh))]
    (when (and (> r x) (> b y))
      [x y (- r x) (- b y)])))

(defn clip-rect
  "The scene's wanted box, in screen coordinates, clipped to the safe region.

  The scene draws in a space whose origin is the safe region's corner, so the
  box is offset into screen space first and then intersected. Skipping the
  intersection is the bug this function exists to prevent."
  [safe [bx by bw bh]]
  (let [{:keys [x y width height]} safe]
    (intersect [x y width height]
               [(+ x bx) (+ y by) bw bh])))

(defn cells
  "The grid, as [x y size [r g b]], covering the whole scene area."
  [{:keys [w h]}]
  (let [step (+ cell gap)]
    (for [gy (range 0 (long h) step)
          gx (range 0 (long w) step)]
      [gx gy cell [(mod (* gx 3) 256) (mod (* gy 5) 256) 180]])))

(defn advance [state input]
  (update state :t + (max 0.0 (double (:delta-seconds input 0.0)))))

(defn- init [_] [{:t 0.0} [[:scene/init :clipbox]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :clipbox]]])

(defn scene []
  {:id :clipbox :title "Scissor"
   :init init :update update-scene :draw draw :dispose dispose})
