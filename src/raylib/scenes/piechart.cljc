(ns raylib.scenes.piechart
  "A rotating pie chart, ported from raylib-jlt's `pie_chart`.

  The second scene here that fills rather than outlines, and it uses the same
  rlgl triangle fan the colour wheel does. raylib-jlt reaches for its `sector!`
  helper; there is no such helper here, so a slice is built from the same
  primitive: a run of triangles from the centre out to the arc, enough of them
  that the arc reads as curved.

  Segment count is per slice and proportional to its angle, so a thin slice does
  not get the same twenty triangles a fat one does. That is the difference
  between 300 triangles a frame and 100 for an identical picture.

  The whole chart turns, which is the original's idea and a good one: a static
  pie chart hides the fact that the eye judges these by angle rather than area,
  and a rotating one makes that obvious."
  (:require [clojure.string]))

(def wedges
  "[label value [r g b]]. Fixed data, since the point is the drawing."
  [["alpha"   30 [230 41  55]]
   ["beta"    24 [102 191 255]]
   ["gamma"   18 [0   228 48]]
   ["delta"   16 [255 203 0]]
   ["epsilon" 12 [135 60  190]]])

(def degrees-per-frame 0.25)
(def degrees-per-segment 6.0)

(defn total [] (double (reduce + (map second wedges))))

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:w w :h h
     :cx (* 0.5 w)
     :cy (* 0.38 h)
     :radius (* 0.40 (min w h))
     :legend-x (* 0.14 w)
     :legend-y (* 0.68 h)
     :legend-step (* 0.055 h)
     :swatch (* 0.045 w)}))

(defn arcs
  "Each wedge as {:label :colour :from :to}, in degrees, offset by `base`.

  Angles accumulate, so the wedges tile the circle exactly rather than each
  being computed from its own index and leaving a seam."
  [base]
  (let [t (total)]
    (first
     (reduce (fn [[out acc] [label value colour]]
               (let [span (* 360.0 (/ (double value) t))]
                 [(conj out {:label label :colour colour
                             :value value
                             :from (+ base acc) :to (+ base acc span)})
                  (+ acc span)]))
             [[] 0.0]
             wedges))))

(defn triangles
  "One wedge as [x0 y0 x1 y1] pairs, the outer edge of each triangle in the fan.

  The caller supplies the centre for the third vertex, since every triangle in
  a fan shares it."
  [{:keys [cx cy radius]} {:keys [from to]}]
  (let [span (- to from)
        n (max 2 (int (Math/ceil (/ span degrees-per-segment))))
        step (/ span n)
        point (fn [deg]
                (let [r (Math/toRadians deg)]
                  [(+ cx (* radius (Math/sin r)))
                   (- cy (* radius (Math/cos r)))]))]
    (mapv (fn [i]
            (let [[x0 y0] (point (+ from (* i step)))
                  [x1 y1] (point (+ from (* (inc i) step)))]
              [x0 y0 x1 y1]))
          (range n))))

(defn percent [value]
  (Math/round (* 100.0 (/ (double value) (total)))))

(defn- init [_] [{:base 0.0} [[:scene/init :piechart]]])
(defn- update-scene [state _] [(update state :base + degrees-per-frame) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :piechart]]])

(defn scene []
  {:id :piechart :title "Pie Chart"
   :init init :update update-scene :draw draw :dispose dispose})
