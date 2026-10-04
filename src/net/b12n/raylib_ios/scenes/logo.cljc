(ns net.b12n.raylib-ios.scenes.logo
  "The raylib logo, still. Ported from raylib-jlt's `logo`: a thick black square
  border built from two rectangles, a black one with a background-coloured one
  inside it, and 'raylib' tucked into the bottom-right corner of the inside.

  It differs from `logoanim`, which is the same logo assembling itself over a few
  hundred frames. This one is the finished picture and nothing moves, so there
  is no state worth keeping beyond the screen it was laid out for, and no input
  is read.

  The original is 256 pixels square with a 16 pixel border and 40 pixel text in
  an 800 by 450 window. Here the size is the smaller of 0.72 of the shorter side
  and the room below Back, so it never reaches the Back button, and the border,
  the text and the 4 pixel gap in the corner scale with it. The logo is centred
  across the screen and down the region below Back.

  Where the label goes depends on its width, which a pure namespace cannot ask
  raylib for. `layout` takes a `measure` function `(fn [s size] -> px)`. The draw
  method passes the real one and the tests pass an estimate of 0.6 of the size
  per character. Text is sized in `dimensions`."
  (:require [net.b12n.raylib-ios.gesture :as gesture]))

(def word "raylib")
(def background-colour "The original's RAYWHITE." [245 245 245 255])
(def logo-colour "The original's BLACK, which `net.b12n.raylib-ios.host` has no name for." [0 0 0 255])

(def ^:private logo-units 256.0)
(def ^:private border-units 16.0)
(def ^:private text-units 40.0)
(def ^:private gap-units 4.0)

(defn dimensions
  "The geometry for `metrics`' `:screen`. `:top` is where the region below Back
  starts, `:side` the logo's size, `:x :y` its top-left corner, `:border` the
  border's thickness, `:gap` the label's distance from the inner corner and
  `:size` the label's text size. `:u` is the scale of the original's pixels."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        room (- h top)
        side (min (* 0.72 (min w h)) (* 0.9 room))
        u (/ side logo-units)]
    {:w w
     :h h
     :top top
     :u u
     :side side
     :x (* 0.5 (- w side))
     :y (+ top (* 0.5 (- room side)))
     :border (* border-units u)
     :gap (* gap-units u)
     :size (max 8 (int (* text-units u)))}))

(defn layout
  "The shapes for `dims` with the label placed by `measure`, `(fn [s size] -> px)`.
  `:outer` and `:inner` are `[x y w h]` rectangles, the outer in the logo colour
  and the inner, drawn over it, in the background colour. `:label` is
  `{:s :x :y :size}`, its right and bottom edges `:gap` inside the inner square,
  as the original puts it."
  [{:keys [x y side border gap size]} measure]
  (let [lw (measure word size)]
    {:outer [x y side side]
     :inner [(+ x border) (+ y border) (- side (* 2 border)) (- side (* 2 border))]
     :label {:s word
             :x (- (+ x side) lw border gap)
             :y (- (+ y side) size border gap)
             :size size}}))

(defn- init [{:keys [metrics]}]
  [{:screen (:screen metrics)} [[:scene/init :logo]]])

(defn- update-scene
  "Nothing moves. The state follows the screen so a rotation is noticed, and the
  draw method lays out from the metrics it is given."
  [state input]
  [(assoc state :screen (get-in input [:metrics :screen] (:screen state))) []])

(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :logo]]])

(defn scene []
  {:id :logo
   :title "Still Logo"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
