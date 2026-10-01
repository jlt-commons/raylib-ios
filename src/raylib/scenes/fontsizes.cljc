(ns raylib.scenes.fontsizes
  "Lines at several font sizes and colours, two of them centred by measuring
  them. Ported from raylib-jlt's `text`, which uses raylib's built-in bitmap
  font and no external one.

  It differs from `align`, which is about where a word sits in a box (left,
  centre, right) and changes as it runs. This one is about size: the same kind
  of line at 10, 20, 30 and 40 in the original, plus a centred title and a
  centred footer. Nothing moves and no input is read.

  The original's window is 800 by 450 and its sizes are 10, 20, 30 and 40.
  Everything is scaled by one factor `u`, the smaller of the width over 800 and
  the room below Back over the original's 384 pixels of content, so the sizes
  keep their ratios and the whole block fits. Sizes are rounded to whole
  numbers, because raylib's default font is a bitmap that is scaled to any other
  size and an int is all `draw-text` takes. The block is offset from the top of
  the region below Back, not from the top of the screen.

  Centring depends on the width of a line, which a pure namespace cannot ask
  raylib for. `layout` takes a `measure` function `(fn [s size] -> px)`. The
  draw method passes the real one and the tests pass an estimate of 0.6 of the
  size per character. Text is laid out in `dimensions`, with widths estimated at
  0.6 of the size per character."
  (:require [raylib.gesture :as gesture]))

(def background-colour "The original's RAYWHITE." [245 245 245 255])

(def rows
  "The original's lines as `{:s :size :y :colour :centred?}`, in its 800 by 450
  pixels, with `:y` measured from the first line's top."
  [{:s "raylib text - default font"
    :size 40
    :y 0
    :colour [0 82 172 255]
    :centred? true}
   {:s "size 10"
    :size 10
    :y 90
    :colour [80 80 80 255]}
   {:s "size 20"
    :size 20
    :y 120
    :colour [190 33 55 255]}
   {:s "size 30"
    :size 30
    :y 160
    :colour [0 117 44 255]}
   {:s "size 40"
    :size 40
    :y 210
    :colour [200 122 255 255]}
   {:s "MeasureText centers this line"
    :size 24
    :y 360
    :colour [130 130 130 255]
    :centred? true}])

(def ^:private original-width 800.0)
(def ^:private original-extent "Top of the first line to the bottom of the last." 384.0)
(def ^:private left-units 40.0)

(defn dimensions
  "The layout for `metrics`' `:screen`. `:u` is the scale of the original's
  pixels, `:top` where the region below Back starts, and `:lines` the rows as
  `{:s :size :x :y :colour :centred?}`. `:x` is the left margin for a left-hand
  line and nil for a centred one, which `layout` places."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        y0 (+ top (* 0.02 h))
        u (min (/ w original-width) (/ (- h y0 (* 0.02 h)) original-extent))]
    {:w w
     :h h
     :top top
     :u u
     :lines (mapv (fn [{:keys [size y centred?]
                        :as row}]
                    (assoc row
                           :size (max 1 (int (+ 0.5 (* u size))))
                           :x (when-not centred? (int (* left-units u)))
                           :y (int (+ y0 (* u y)))))
                  rows)}))

(defn layout
  "`dims`' lines with every `:x` filled in. A centred line starts at half of
  what is left of the width after `measure`, `(fn [s size] -> px)`, takes its
  share."
  [{:keys [w lines]} measure]
  (mapv (fn [{:keys [s size x]
              :as line}]
          (assoc line :x (or x (int (* 0.5 (- w (measure s size)))))))
        lines))

(defn- init [{:keys [metrics]}]
  [{:screen (:screen metrics)} [[:scene/init :fontsizes]]])

(defn- update-scene
  "Nothing moves. The state follows the screen so a rotation is noticed, and the
  draw method lays out from the metrics it is given."
  [state input]
  [(assoc state :screen (get-in input [:metrics :screen] (:screen state))) []])

(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :fontsizes]]])

(defn scene []
  {:id :fontsizes
   :title "Font Sizes"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
