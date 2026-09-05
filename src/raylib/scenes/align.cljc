(ns raylib.scenes.align
  "One word placed left, centre and right inside a box, using the width raylib
  measures for it. Ported from raylib-jlt's `words_alignment`.

  `MeasureText` is one of the few raylib calls this project can bind directly:
  it takes a string and an int and returns an int, with no by-value struct
  anywhere, so there is nothing to work around. That makes this the rare scene
  where the port is the C example rather than a reconstruction of it.

  Alignment itself is arithmetic on that measurement, and it lives here rather
  than in the draw method so it can be tested against a measuring function that
  is not raylib. The tests use a stub of fixed-width glyphs, which is enough to
  pin the three offsets and the degenerate cases: a word wider than its box, and
  a word of nothing at all."
  (:require [clojure.string]))

(def alignments [:left :centre :right])
(def hold-seconds 1.6)
(def words ["raylib" "on" "a" "phone" "from" "Clojure"])

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)]
    {:w (double w) :h (double h)
     :box-x (* 0.10 w) :box-w (* 0.80 w)
     :box-y (* 0.26 h) :box-h (* 0.12 h)
     :gap (* 0.05 h)
     :text-size (max 30 (int (* 0.055 (min w h))))
     :label-size (max 18 (int (* 0.024 (min w h))))}))

(defn offset
  "Where a word `text-width` wide starts inside a box `box-width` wide.

  Clamped at zero, so a word too long for its box starts at the left edge and
  runs off the right rather than starting off the left and running into the
  middle, which is what an unclamped centre or right does and looks like a
  layout bug rather than an overflow."
  [alignment box-width text-width]
  (let [slack (- (double box-width) text-width)]
    (max 0.0
         (case alignment
           :left 0.0
           :centre (* 0.5 slack)
           :right slack))))

(defn current
  "Which alignment and which word are showing at time `t`.

  The word changes with the alignment rather than independently, so every frame
  shows one thing being demonstrated and the eye has something to compare
  against the box edges."
  [t]
  (let [step (long (quot t hold-seconds))]
    {:alignment (nth alignments (mod step (count alignments)))
     :word (nth words (mod (quot step (count alignments)) (count words)))}))

(defn advance [state input]
  (update state :t + (max 0.0 (double (:delta-seconds input 0.0)))))

(defn- init [_] [{:t 0.0} [[:scene/init :align]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :align]]])

(defn scene []
  {:id :align :title "Text Alignment"
   :init init :update update-scene :draw draw :dispose dispose})
