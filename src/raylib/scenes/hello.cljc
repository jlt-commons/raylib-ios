(ns raylib.scenes.hello
  "The basic window: one line of text on a clear screen. Ported from raylib-jlt's
  `core`, which is raylib's `core_basic_window` example.

  The original is an 800x450 window cleared to RAYWHITE with `Congrats! You
  created your first window!` in LIGHTGRAY at size 20, 190 px from the left and
  200 down. Nothing moves and nothing is read, so there is no control to
  replace. The line is centred across the width by the injected `measure`, which
  puts it where the original's hand-picked 190 px does at 800 wide, and it sits
  the original's 200/450 of the way down the field below `gesture/back-region`.
  Its size is the original's 20 scaled by the smaller of the two axes' scales,
  never under 20, then cut back when the line would cover more than nine tenths
  of the width, so it fits a phone held upright. The draw method passes raylib's own text
  width; the tests pass an estimate."
  (:require [raylib.gesture :as gesture]))

(def background-colour [245 245 245 255])
(def text-colour [200 200 200 255])

(def line "Congrats! You created your first window!")

(defn dimensions
  "The layout for `metrics`' `:screen`: `:w :h` and `:text`, `{:s :x :y :size}`
  for the one line. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        fh (- h top)
        ts (min (/ w 800.0) (/ fh 450.0))
        fit (int (/ (* 0.9 w 100.0) (measure line 100)))
        size (max 8 (min (max 20 (int (* 20 ts))) fit))]
    {:w w
     :h h
     :text {:s line
            :x (int (/ (- w (measure line size)) 2.0))
            :y (int (+ top (* fh (/ 200.0 450.0))))
            :size size}}))

(defn- init [_] [{} [[:scene/init :hello]]])
(defn- update-scene [state _] [state []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :hello]]])

(defn scene []
  {:id :hello
   :title "Basic Window"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
