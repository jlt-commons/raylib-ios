(ns raylib.scenes.ring
  "A filled annulus whose sweep and inner radius breathe, with a stroked
  outline. Ported from raylib-jlt's `ring_drawing`, itself raylib's
  `shapes_ring_drawing` minus its raygui sliders.

  This is the scene that would have caught the winding bug on day one. Its whole
  subject is `draw-ring`, so an annulus that renders nothing is not a subtle
  regression here, it is a blank screen. The analog clock hid the same fault for
  a day because its bezel is one thin ring among sixty tick marks, and a missing
  ring there looks like a design choice.

  The outline is drawn as arcs of short thick lines rather than as a second,
  slightly larger ring. Two rings would z-fight along their shared edge, and the
  lines also give the radial end caps that close the shape."
  (:require [clojure.string]))

(def outline-segments 96)
(def fill-segments 120)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        outer (* 0.36 (min w h))]
    {:w (double w) :h (double h)
     :cx (* 0.5 w) :cy (* 0.45 h)
     :outer outer
     :thick (* 0.008 (min w h))
     :label-size (max 20 (int (* 0.030 (min w h))))}))

(defn polar
  "Zero points up and the angle grows clockwise, matching draw-ring."
  [cx cy r deg]
  (let [t (Math/toRadians (double deg))]
    [(+ cx (* r (Math/sin t))) (- cy (* r (Math/cos t)))]))

(defn geometry
  "The annulus for frame time `t`: inner radius and the two sweep angles.

  Three cycles at different rates, so the shape never repeats on a short loop
  and a still frame is unlikely to catch it at an extreme."
  [{:keys [outer]} t]
  (let [inner (* outer (+ 0.50 (* 0.17 (Math/sin (* 1.8 t)))))
        start (* 30.0 t)
        ;; 100 to 330 degrees. The original's own formula reaches 380, which is
        ;; past a full turn: the arc laps its own start and the overlap shades
        ;; twice. Harmless on a desktop demo you glance at, wrong in a gallery
        ;; that sits on one scene.
        end (+ start 100.0 (* 115.0 (+ 1.0 (Math/sin (* 1.02 t)))))]
    {:inner inner :start start :end end}))

(defn arc-points
  "`n`+1 points along an arc, for stroking it as short segments."
  [cx cy r start end n]
  (let [span (- (double end) start)]
    (mapv (fn [i] (polar cx cy r (+ start (* span (/ (double i) n)))))
          (range (inc n)))))

(defn advance [state input]
  (update state :t + (max 0.0 (double (:delta-seconds input 0.0)))))

(defn- init [_] [{:t 0.0} [[:scene/init :ring]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :ring]]])

(defn scene []
  {:id :ring :title "Ring Drawing"
   :init init :update update-scene :draw draw :dispose dispose})
