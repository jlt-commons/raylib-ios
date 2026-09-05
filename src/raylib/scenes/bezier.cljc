(ns raylib.scenes.bezier
  "A cubic Bezier whose far end follows your finger. Ported from raylib-jlt's
  `lines_bezier`.

  raylib's `DrawLineBezier` takes its endpoints as by-value Vector2, so the curve
  is sampled here and drawn as segments. That is the third time this suite has
  reached the same conclusion, and each time it has been an improvement rather
  than a workaround: sampling in Clojure is what let `splines` draw three bases
  over shared points, and it is what lets this one show its control polygon.

  The control points are placed from the two endpoints rather than dragged
  separately, which is what the original does with a mouse it can move freely.
  A finger has one position, so the handles are derived: each pulls horizontally
  toward the other end by a fixed fraction of the gap. That keeps the curve
  well-behaved at every finger position, including the degenerate one where the
  finger is on the anchor."
  (:require [clojure.string]))

(def samples 48)
(def handle-fraction 0.55)

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:w (double w) :h (double h)
     :anchor [(* 0.14 w) (* 0.30 h)]
     :dot (* 0.013 w)
     :thick (* 0.009 w)
     :label-size (max 20 (int (* 0.028 (min w h))))}))

(defn controls
  "The four control points, from the anchor and the moving end.

  The two handles pull horizontally toward each other, so the curve leaves each
  end level and sweeps into an S. Deriving them keeps the shape sane wherever
  the finger goes, which a pair of independently dragged handles does not."
  [[ax ay] [bx by]]
  (let [pull (* handle-fraction (- (double bx) ax))]
    [[ax ay] [(+ ax pull) ay] [(- bx pull) by] [bx by]]))

(defn at
  "The curve point at `t`, from the four control points."
  [[[ax ay] [bx by] [cx cy] [dx dy]] t]
  (let [u (- 1.0 (double t))
        w0 (* u u u) w1 (* 3.0 u u t) w2 (* 3.0 u t t) w3 (* t t t)]
    [(+ (* w0 ax) (* w1 bx) (* w2 cx) (* w3 dx))
     (+ (* w0 ay) (* w1 by) (* w2 cy) (* w3 dy))]))

(defn curve
  "The polyline, for tests. The draw path samples inline instead."
  [ctrl]
  (mapv (fn [i] (at ctrl (/ (double i) samples))) (range (inc samples))))

(defn advance
  "Follow a finger, and drift on a lissajous when there is none, so the scene is
  alive in a gallery nobody is holding."
  [state input]
  (let [phase (get-in input [:pointer :phase])
        point (get-in input [:pointer :position])
        touching? (boolean (and point (#{:press :down} phase)))
        t (+ (:t state 0.0) (max 0.0 (double (:delta-seconds input 0.0))))
        {:keys [w h]} (dimensions (:metrics input))]
    (assoc state
           :t t
           :touching? touching?
           :end (if touching?
                  [(double (first point)) (double (second point))]
                  (let [s (* 0.7 t)]
                    [(+ (* 0.62 w) (* 0.22 w (Math/sin s)))
                     (+ (* 0.55 h) (* 0.30 h (Math/sin (* 1.6 s))))])))))

(defn- init [{:keys [metrics]}]
  (let [{:keys [w h]} (dimensions metrics)]
    [{:t 0.0 :touching? false :end [(* 0.8 w) (* 0.7 h)]}
     [[:scene/init :bezier]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :bezier]]])

(defn scene []
  {:id :bezier :title "Bezier"
   :init init :update update-scene :draw draw :dispose dispose})
