(ns raylib.scenes.clock
  "A seven-segment clock, ported from raylib-jlt's `digital_clock`.

  Each digit is seven rectangles, and which of them are lit comes from a table
  with one entry per numeral. Unlit segments are drawn too, dimly, because that
  is what a real seven-segment display looks like and because leaving them out
  makes a 1 look like it is floating.

  The layout is derived from the screen rather than the original's fixed pixel
  columns, and stacked as HH / MM / SS rather than run across in one row: six
  digits side by side on a portrait phone leaves each one about 90 pixels wide,
  which is smaller than the clock in the status bar directly above it.

  The colon between rows blinks on even seconds, which is the one piece of
  state a clock face has that is not the time."
  (:require [clojure.string]))

;; a top, b top-right, c bottom-right, d bottom, e bottom-left, f top-left,
;; g middle. The usual naming, clockwise from the top with the middle last.
(def lit-segments
  {0 #{:a :b :c :d :e :f}
   1 #{:b :c}
   2 #{:a :b :g :e :d}
   3 #{:a :b :g :c :d}
   4 #{:f :g :b :c}
   5 #{:a :f :g :c :d}
   6 #{:a :f :g :e :c :d}
   7 #{:a :b :c}
   8 #{:a :b :c :d :e :f :g}
   9 #{:a :b :c :d :f :g}})

(defn dimensions
  "Three rows of two digits, centred."
  [metrics]
  (let [[w h] (:screen metrics)
        digit-w (* 0.30 w)
        thick (* 0.055 digit-w)
        half (* 0.62 digit-w)                    ; height of one half of a digit
        gap (* 0.10 digit-w)
        row-h (+ (* 2 half) thick)
        block-h (+ (* 3 row-h) (* 2 gap))]
    {:w w :h h
     :digit-w digit-w :thick thick :half half
     :gap gap :row-h row-h
     :x0 (- (* 0.5 w) digit-w (* 0.5 gap))       ; left digit of each pair
     :x1 (+ (* 0.5 w) (* 0.5 gap))
     :y0 (- (* 0.5 h) (* 0.5 block-h))}))

(defn segment-rects
  "The seven rectangles of one digit at [x y], as {segment [x y w h]}.

  Horizontal bars are inset by the thickness at both ends so the corners do not
  overlap the verticals, which on a real display is the little diagonal notch
  at each corner."
  [{:keys [digit-w thick half]} x y]
  (let [w digit-w, t thick, hh half
        inset (* 0.9 t)]
    {:a [(+ x inset) y (- w (* 2 inset)) t]
     :g [(+ x inset) (+ y hh) (- w (* 2 inset)) t]
     :d [(+ x inset) (+ y (* 2 hh)) (- w (* 2 inset)) t]
     :f [x y t hh]
     :b [(+ x (- w t)) y t hh]
     :e [x (+ y hh) t hh]
     :c [(+ x (- w t)) (+ y hh) t hh]}))

(defn digit-pairs
  "[hour minute second] to three [tens units] pairs."
  [[h m s]]
  [[(quot h 10) (mod h 10)]
   [(quot m 10) (mod m 10)]
   [(quot s 10) (mod s 10)]])

(defn row-origin
  "Top-left y of row `i`, counting hours as zero."
  [{:keys [y0 row-h gap]} i]
  (+ y0 (* i (+ row-h gap))))

(defn- init [_] [{:time [0 0 0]} [[:scene/init :clock]]])

;; The time is read by the impure half and handed in, so this namespace stays
;; testable without a clock. update-scene keeps whatever it was last given.
(defn- update-scene [state _] [state []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :clock]]])

(defn scene []
  {:id :clock :title "Clock"
   :init init :update update-scene :draw draw :dispose dispose})
