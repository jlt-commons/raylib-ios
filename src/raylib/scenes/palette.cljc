(ns raylib.scenes.palette
  "Every colour raylib names, as a labelled grid. Ported from raylib-jlt's
  `colors`, which is itself a showcase rather than a port of one C example.

  The values are raylib's own, transcribed from `raylib.h` by way of
  raylib-jlt's `raylib.clj`, and they are held here as data rather than as
  twenty-five more constants in `raylib.host`. The host defines the five it
  actually uses. A scene that wants to draw the palette wants a sequence it can
  iterate, not twenty-five vars it would have to list again by hand.

  Worth knowing what this exercises: every swatch is a separate packed Color
  crossing the FFI, so a grid of twenty-five is a cheap check that the packing
  is right across the whole range rather than for the handful a typical scene
  touches. BLANK is deliberately absent, since a fully transparent swatch would
  draw nothing and read as a bug."
  (:require [clojure.string]))

(def colours
  "raylib's named colours, in the order raylib.h declares them."
  [["LIGHTGRAY"  200 200 200] ["GRAY"       130 130 130]
   ["DARKGRAY"    80  80  80] ["YELLOW"     253 249   0]
   ["GOLD"       255 203   0] ["ORANGE"     255 161   0]
   ["PINK"       255 109 194] ["RED"        230  41  55]
   ["MAROON"     190  33  55] ["GREEN"        0 228  48]
   ["LIME"         0 158  47] ["DARKGREEN"    0 117  44]
   ["SKYBLUE"    102 191 255] ["BLUE"         0 121 241]
   ["DARKBLUE"     0  82 172] ["PURPLE"     200 122 255]
   ["VIOLET"     135  60 190] ["DARKPURPLE" 112  31 126]
   ["BEIGE"      211 176 131] ["BROWN"      127 106  79]
   ["DARKBROWN"   76  63  47] ["WHITE"      255 255 255]
   ["BLACK"        0   0   0] ["MAGENTA"    255   0 255]
   ["RAYWHITE"   245 245 245]])

(def cols 4)

(defn rows [] (long (Math/ceil (/ (double (count colours)) cols))))

(defn dimensions [metrics]
  (let [[w h] (:screen metrics)
        margin (* 0.05 w)
        gap (* 0.02 w)
        cell-w (/ (- w (* 2 margin) (* (dec cols) gap)) cols)
        n (rows)
        cell-h (/ (- h (* 2 margin) (* (dec n) gap)) n)]
    {:w (double w) :h (double h)
     :margin margin :gap gap
     :cell-w cell-w :cell-h cell-h
     :swatch-h (* 0.66 cell-h)
     :label-size (max 16 (int (* 0.020 (min w h))))}))

(defn cell
  "Where swatch `i` goes, as [x y w h]."
  [{:keys [margin gap cell-w cell-h]} i]
  [(+ margin (* (mod i cols) (+ cell-w gap)))
   (+ margin (* (quot i cols) (+ cell-h gap)))
   cell-w cell-h])

(defn light?
  "Whether a swatch needs dark text on it.

  Rec. 601 luma, because green carries far more perceived brightness than blue
  does and a flat channel average pretends otherwise. Two colours in this very
  palette come out on opposite sides of the two measures:

    ORANGE  255 161 0    flat 139 (dark)   luma 171 (light)
    MAGENTA 255 0 255    flat 170 (light)  luma 105 (dark)

  Both times luma matches what the eye does, so a flat average would put light
  ink on orange and dark ink on magenta, and both labels would struggle."
  [[_ r g b]]
  (> (+ (* 0.299 r) (* 0.587 g) (* 0.114 b)) 140.0))

(defn- init [_] [{:t 0.0} [[:scene/init :palette]]])
(defn- update-scene [state input]
  [(update state :t + (max 0.0 (double (:delta-seconds input 0.0)))) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :palette]]])

(defn scene []
  {:id :palette :title "Colours"
   :init init :update update-scene :draw draw :dispose dispose})
