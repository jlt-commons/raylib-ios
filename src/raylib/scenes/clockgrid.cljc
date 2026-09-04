(ns raylib.scenes.clockgrid
  "The time spelled out by a grid of little clock faces. Ported from raylib-jlt's
  `clock_of_clocks`, itself a port of raylib's `shapes_clock_of_clocks.c`.

  Six digits, each a 4x6 grid of small clocks whose 48 hands swing into position
  to draw the digit. Every digit is a lookup of 24 hand-angle pairs, and each
  second the hands sweep from wherever they are to wherever the next digit wants
  them.

  Laid out three rows of two rather than the original's single row of six.
  Twenty-four faces across a portrait phone leaves each one about 50 pixels
  wide, which is too small to read as a clock. Stacked as hours, minutes and
  seconds it is eight across, and the same picture through ninety degrees. Same
  decision as the epicycles port, for the same reason.

  Angles follow raylib's screen convention: 0 points right and positive turns
  clockwise, because y grows downward."
  (:require [clojure.string]))

(def move-seconds 0.5)

;; The six hand pairs a cell can hold, as [hand-1-deg hand-2-deg].
(def TL [0.0 90.0])    ; corner opening right and down
(def TR [90.0 180.0])  ; down and left
(def BR [180.0 270.0]) ; left and up
(def BL [0.0 270.0])   ; right and up
(def HH [0.0 180.0])   ; horizontal bar
(def VV [90.0 270.0])  ; vertical bar
(def ZZ [135.0 135.0]) ; blank: both hands together, pointing nowhere

;; Ten digits, each 24 cells read left to right then top to bottom, 4 wide by 6
;; tall. Transcribed from the C table.
(def digit-angles
  [[TL HH HH TR, VV TL TR VV, VV VV VV VV, VV VV VV VV, VV BL BR VV, BL HH HH BR]
   [TL HH TR ZZ, BL TR VV ZZ, ZZ VV VV ZZ, ZZ VV VV ZZ, TL BR BL TR, BL HH HH BR]
   [TL HH HH TR, BL HH TR VV, TL HH BR VV, VV TL HH BR, VV BL HH TR, BL HH HH BR]
   [TL HH HH TR, BL HH TR VV, TL HH BR VV, BL HH TR VV, TL HH BR VV, BL HH HH BR]
   [TL TR TL TR, VV VV VV VV, VV BL BR VV, BL HH TR VV, ZZ ZZ VV VV, ZZ ZZ BL BR]
   [TL HH HH TR, VV TL HH BR, VV BL HH TR, BL HH TR VV, TL HH BR VV, BL HH HH BR]
   [TL HH HH TR, VV TL HH BR, VV BL HH TR, VV TL TR VV, VV BL BR VV, BL HH HH BR]
   [TL HH HH TR, BL HH TR VV, ZZ ZZ VV VV, ZZ ZZ VV VV, ZZ ZZ VV VV, ZZ ZZ BL BR]
   [TL HH HH TR, VV TL TR VV, VV BL BR VV, VV TL TR VV, VV BL BR VV, BL HH HH BR]
   [TL HH HH TR, VV TL TR VV, VV BL BR VV, BL HH TR VV, TL HH BR VV, BL HH HH BR]])

(def cols 4)
(def rows 6)
(def cells (* cols rows))

(defn dimensions
  "Geometry for a 2-wide, 3-tall arrangement of digits.

  The cell size comes from the height, not the width. Three digits stacked is
  eighteen cells tall against eight across, so on a portrait phone height is
  what binds: sizing from the width overflowed the bottom of the screen by 173
  pixels and pushed the left column off the edge."
  [metrics]
  (let [[w h] (:screen metrics)
        row-gap (* 0.015 h)
        step (/ (- (* 0.94 h) (* 2 row-gap)) (* 3 rows))
        face (* 0.90 step)
        block-h (* rows step)
        pair-gap (* 0.05 w)
        total-w (+ (* 2 cols step) pair-gap)
        total-h (+ (* 3 block-h) (* 2 row-gap))]
    {:w (double w) :h (double h)
     :step step :face face
     :radius (* 0.5 face)
     :hand (* 0.42 face)
     :pair-w (+ (* cols step) pair-gap)
     :x0 (* 0.5 (- w total-w))
     :y0 (* 0.5 (- h total-h))
     :row-step (+ block-h row-gap)}))

(defn lerp [a b t] (+ a (* (- b a) t)))

(defn shortest-from
  "Sets the source angle so a hand sweeps forward into its target rather than
  winding backwards. raylib does this by subtracting a full turn from any source
  that already leads its destination."
  [src dst]
  (if (> src dst) (- src 360.0) src))

(defn digits-of
  "The six digits of `[h m s]`, most significant first."
  [[h m s]]
  [(quot h 10) (mod h 10) (quot m 10) (mod m 10) (quot s 10) (mod s 10)])

(defn target-angles
  "Every cell's destination pair for a set of digits."
  [digits]
  (mapv (fn [d] (nth digit-angles d)) digits))

(defn- blank-grid [] (vec (repeat 6 (vec (repeat cells ZZ)))))

(defn advance
  "Sweep the hands toward this second's digits.

  The wall clock arrives in the input rather than being read here, the same way
  the analog clock takes it, so this namespace stays pure."
  [state input]
  (let [now (:local-time input)
        sec (when now (nth now 2))
        ticked? (and sec (not= sec (:sec state)))
        dst (if ticked? (target-angles (digits-of now)) (:dst state))
        src (if ticked?
              (mapv (fn [cs ts]
                      (mapv (fn [[a b] [ta tb]]
                              [(shortest-from a ta) (shortest-from b tb)])
                            cs ts))
                    (:current state) dst)
              (:src state))
        timer (if ticked?
                0.0
                (+ (:timer state 0.0) (max 0.0 (double (:delta-seconds input 0.0)))))
        t (min 1.0 (/ timer move-seconds))
        current (mapv (fn [ss dd]
                        (mapv (fn [[sa sb] [da db]]
                                [(lerp sa da t) (lerp sb db t)])
                              ss dd))
                      src dst)]
    (assoc state :sec (or sec (:sec state)) :src src :dst dst
           :timer timer :current current)))

(defn- init [_]
  [{:sec nil :timer 0.0 :current (blank-grid) :src (blank-grid) :dst (blank-grid)}
   [[:scene/init :clockgrid]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :clockgrid]]])

(defn scene []
  {:id :clockgrid :title "Clock of Clocks"
   :init init :update update-scene :draw draw :dispose dispose})
