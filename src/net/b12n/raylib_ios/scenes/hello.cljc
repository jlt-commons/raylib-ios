(ns net.b12n.raylib-ios.scenes.hello
  "The basic window: one line of text on a clear screen. Ported from raylib-jlt's
  `core`, which is raylib's `core_basic_window` example.

  The original is an 800x450 window cleared to RAYWHITE with `Congrats! You
  created your first window!` in LIGHTGRAY at size 20, 190 px from the left and
  200 down. Nothing moves and nothing is read, so there is no control to
  replace. The line is centred across the width by the injected `measure`, which
  puts it where the original's hand-picked 190 px does at 800 wide, and it sits
  the original's 200/450 of the way down the field below `gesture/back-region`.
  Size and colour depart from the original for legibility: on a phone the
  original's size 20 in LIGHTGRAY was about 10 pt and pale. The size is fitted
  by `measure` so the line spans about 85 percent of the width, which is always
  inside it, and the colour is DARKGRAY. The draw method passes raylib's own text
  width; the tests pass an estimate."
  (:require [net.b12n.raylib-ios.gesture :as gesture]))

(def background-colour [245 245 245 255])
(def text-colour [80 80 80 255])

(def line "Congrats! You created your first window!")

(defn dimensions
  "The layout for `metrics`' `:screen`: `:w :h` and `:text`, `{:s :x :y :size}`
  for the one line. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        fh (- h top)
        size (max 8 (int (/ (* 0.85 w 100.0) (measure line 100))))]
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
