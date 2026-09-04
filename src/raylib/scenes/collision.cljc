(ns raylib.scenes.collision
  "Two boxes and their overlap, ported from raylib-jlt's `collision_area`.

  One box slides back and forth. The other follows your finger. Where they
  overlap, the intersection is filled in red, and the intersection is computed
  here rather than by raylib: an axis-aligned overlap is four `max` and `min`
  calls, and asking raylib would mean passing a Rectangle by value across the
  FFI for no gain.

  The original follows the mouse. A phone has no mouse and does have a finger,
  which is a better fit for this example than it was for the desktop: you can
  drag the box around and watch the overlap change, where a mouse only hovers.

  With nothing touching the screen the box drifts on its own, so the scene is
  alive in a gallery nobody is touching. That is a change from upstream, where
  a still mouse means a still box."
  (:require [clojure.string]))

(def slide-speed 5.0)
(def drift-speed 2.3)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:w (double w) :h (double h)
     :slider-w (* 0.55 w) :slider-h (* 0.22 h)
     :slider-y (* 0.30 h)
     :finger-w (* 0.38 w) :finger-h (* 0.18 h)}))

(defn intersection
  "The overlap of two [x y w h] boxes, or nil when they do not touch.

  Nil rather than a zero-sized rectangle, so the caller cannot accidentally
  draw a degenerate box and think it means contact."
  [[ax ay aw ah] [bx by bw bh]]
  (let [x1 (max ax bx) y1 (max ay by)
        x2 (min (+ ax aw) (+ bx bw))
        y2 (min (+ ay ah) (+ by bh))]
    (when (and (< x1 x2) (< y1 y2))
      [x1 y1 (- x2 x1) (- y2 y1)])))

(defn slider-box [{:keys [slider-w slider-h slider-y]} x]
  [x slider-y slider-w slider-h])

(defn finger-box
  "The follower, centred on [x y] and clamped so it stays fully on screen."
  [{:keys [w h finger-w finger-h]} [x y]]
  [(max 0.0 (min (- w finger-w) (- x (* 0.5 finger-w))))
   (max 0.0 (min (- h finger-h) (- y (* 0.5 finger-h))))
   finger-w finger-h])

(defn advance
  "Slide the first box, and move the second to the finger or drift it."
  [state input]
  (let [dims (dimensions (:metrics input))
        {:keys [w h slider-w]} dims
        x (+ (:x state) (:vx state))
        vx (if (or (>= (+ x slider-w) w) (<= x 0.0)) (- (:vx state)) (:vx state))
        x (max 0.0 (min (- w slider-w) x))
        phase (get-in input [:pointer :phase])
        point (get-in input [:pointer :position])
        ;; a finger wins; with none, the follower drifts on a lissajous so it
        ;; crosses the slider from several angles rather than one
        target (if (and point (#{:press :down} phase))
                 [(double (first point)) (double (second point))]
                 (let [t (* 0.012 (:t state))]
                   [(+ (* 0.5 w) (* 0.34 w (Math/sin t)))
                    (+ (* 0.5 h) (* 0.30 h (Math/sin (* 1.7 t))))]))]
    (assoc state :x x :vx vx :t (inc (:t state)) :target target
           :touching? (boolean (and point (#{:press :down} phase))))))

(defn- init [{:keys [metrics]}]
  (let [{:keys [w h]} (dimensions metrics)]
    [{:x 0.0 :vx slide-speed :t 0 :target [(* 0.5 w) (* 0.5 h)] :touching? false}
     [[:scene/init :collision]]]))

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :collision]]])

(defn scene []
  {:id :collision :title "Collision Area"
   :init init :update update-scene :draw draw :dispose dispose})
