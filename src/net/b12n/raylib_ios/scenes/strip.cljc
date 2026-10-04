(ns net.b12n.raylib-ios.scenes.strip
  "A rainbow band across the screen, sixteen contiguous slices of colour.
  Ported from raylib-jlt's `triangle_strip`.

  The original feeds rlgl immediate mode vertex by vertex. Here each slice is
  two `net.b12n.raylib-ios.host/draw-triangle` calls, which fixes its own winding so a
  triangle cannot be culled for the order its points arrive in.")

(def segments 16)

(defn band-colour
  "The colour of slice `i` as [r g b a]: three sines a third of a turn apart,
  clamped at 0, scaled to 255. The original's formula, with alpha fixed."
  [i]
  (let [t (/ (double i) segments)
        pi 3.141592653589793
        channel (fn [shift]
                  (int (* 255 (max 0.0 (Math/sin (* pi (+ t shift)))))))]
    [(channel 0.0) (channel 0.33) (channel 0.66) 255]))

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        size (max 20 (int (* 0.034 side)))]
    {:w w
     :top (* 0.4 h)
     :bot (* 0.6 h)
     :caption-size size
     :caption-x (int (* 0.04 w))
     :caption-y (int (- h (* 0.055 h)))}))

(defn bands
  "Sixteen [x0 x1 top bot colour] slices that tile the full width. Each edge is
  computed from its index rather than accumulated, so the last `x1` is exactly
  `w` with no rounding drift."
  [dims]
  (let [{:keys [w top bot]} dims
        edge (fn [i] (/ (* (double w) i) segments))]
    (mapv (fn [i] [(edge i) (edge (inc i)) top bot (band-colour i)])
          (range segments))))

(defn advance [state _input] state)

(defn- init [_] [{} [[:scene/init :strip]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :strip]]])

(defn scene []
  {:id :strip
   :title "Triangle Strip"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
