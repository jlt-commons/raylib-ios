(ns raylib.scenes.textiling
  "Texture Tiling, ported from raylib-jlt's `texture_tiling`
  (net/b12n/raylib_jlt/texture_tiling.clj, zlib licence), which is raylib-jlt's
  own example and not a port of a raylib C example. raylib 6.0's nearest is
  `textures_tiled_drawing`, which tiles with DrawTextureTiled where this lets
  the GPU's REPEAT wrap do it: one small procedural tile covers the screen as a
  single textured quad, by asking for texture coordinates well past 1.0.

  Mirrored from texture_tiling.clj:
  - The tile (lines 22-35): 64 by 64 (line 21), a diagonal weave over a dark
    ground with a dot in the middle. Inside radius 5 of (32, 32) it is GOLD
    (255, 203, 0); else where `(x + y) mod 16` is under 3 it is BLUE
    (0, 121, 241); else where `(x - y) mod 16` is under 3 it is SKYBLUE
    (102, 191, 255); else (20, 24, 34). `tile-spec` packs it with
    `raylib.texel/pack`, whose byte order is raylib-jlt's `rgba`.
  - The state (lines 49-66): tiles start at 6.0, scroll starts at 0 and grows
    0.004 a frame, and the density moves 0.08 a frame while a key is held, held
    to 1.0 to 24.0. UP wins over DOWN when both are held.
  - The quad (lines 68-77): texcoords run from `scroll` to `scroll + tiles` in
    u and `scroll + tiles * (h / w)` in v, so the tiles stay square.
  - The overlay (lines 78-95): a translucent black band under two lines of
    text, a count of `tiles * tiles * (h / w)` tiles from one 64x64 texture and
    the hint.

  Deviations. The picture is the screen, not an 800 by 450 window, so `h / w` is
  the screen's own. UP and DOWN are two buttons under the text, and a finger held
  on one repeats as the key does. The band sits below Back, and its text is
  sized to fit the width. The hint reads \"Hold UP or DOWN to change density\".

  The state holds `:tiles`, `:scroll`, `:held` (`:up`, `:down` or nil) and
  `:screen`. Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.texel :as texel]))

(def tex-size "The original's TEX: the tile is this many texels square." 64)
(def start-tiles "The original's starting density." 6.0)
(def min-tiles "The lowest density." 1.0)
(def max-tiles "The highest density." 24.0)
(def tiles-step "How far the density moves a frame while a button is held." 0.08)
(def scroll-step "How far the scroll grows a frame." 0.004)

(def background-colour "RAYWHITE." [245 245 245 255])
(def band-colour "The original's band: black at alpha 150." [0 0 0 150])
(def title-colour "RAYWHITE." [245 245 245 255])
(def hint-colour "LIGHTGRAY." [200 200 200 255])
(def button-colour "A button at rest." [200 200 200 255])
(def button-held-colour "A button under a finger." [130 130 130 255])
(def button-label-colour "DARKGRAY." [80 80 80 255])

(def hint-line "The original's hint, with buttons for the keys." "Hold UP or DOWN to change density")
(def labels {:up "UP"
             :down "DOWN"})

(def ^:private colours
  {:gold [255 203 0 255]
   :blue [0 121 241 255]
   :skyblue [102 191 255 255]
   :ground [20 24 34 255]})

(defn tile-colour
  "The original's `tile` (lines 22-35) at texel `x`, `y`, as `[r g b a]`."
  [x y]
  (let [d (mod (+ x y) 16)
        e (mod (- x y) 16)
        c (/ tex-size 2.0)
        r (Math/sqrt (+ (* (- x c) (- x c)) (* (- y c) (- y c))))]
    (cond
      (< r 5) (:gold colours)
      (< d 3) (:blue colours)
      (< e 3) (:skyblue colours)
      :else (:ground colours))))

(defn tile-spec
  "The texture for `raylib.texture/id!`: 64 by 64, repeating, unfiltered."
  []
  {:w tex-size
   :h tex-size
   :wrap :repeat
   :filter :nearest
   :pixel (fn [x y] (texel/pack (tile-colour x y)))})

(defn tile-count
  "The original's count line (line 88): `tiles * tiles * (h / w)`, truncated."
  [tiles aspect]
  (int (* tiles tiles aspect)))

(defn title-line [tiles aspect]
  (str "texture tiling - " (tile-count tiles aspect) " tiles from one "
       tex-size "x" tex-size " texture, one quad"))

(defn clamp-tiles [t] (max min-tiles (min max-tiles t)))

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measured: `:w :h`, `:aspect`
  (`h / w`), `:size :pad :row` (the text's), `:band` (`[x y w h]`, under Back),
  and `:up` and `:down`, the buttons as `[x y w h]`."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (double (+ back-y back-h))
        side (min w h)
        size (max 16 (int (* 0.03 side)))
        pad (max 8 (int (* 0.5 size)))
        row (int (* 1.3 size))
        bh (* 3 size)
        bw (/ (- w (* 3.0 pad)) 2.0)
        by (+ top pad row row)]
    {:w w
     :h h
     :aspect (/ (double h) w)
     :size size
     :pad pad
     :row row
     :band [0.0 top (double w) (+ pad row row bh pad)]
     :up [(double pad) by bw (double bh)]
     :down [(+ pad bw pad) by bw (double bh)]}))

(defn dimensions
  "`geometry` plus `:lines`, the title (at its widest count) and the hint as
  `{:s :x :y :size}`, at one size cut back until both fit the width, and
  `:labels`, each button's label placed in it. `measure` is `(fn [s size] ->
  px)`."
  [metrics measure]
  (let [{:keys [w pad row size band up down]
         :as geo} (geometry metrics)
        [_ top] band
        widest (title-line max-tiles 99999.0)
        room (- w (* 2 pad))
        fit (fn [s] (max 8 (min size (int (/ (* room 100.0) (measure s 100))))))
        sz (min (fit widest) (fit hint-line))
        y1 (int (+ top pad))
        place (fn [[bx by bw bh] s]
                {:s s
                 :x (int (+ bx (* 0.5 (- bw (measure s size)))))
                 :y (int (+ by (* 0.5 (- bh size))))
                 :size size})]
    (assoc geo
           :lines [{:s widest
                    :x pad
                    :y y1
                    :size sz}
                   {:s hint-line
                    :x pad
                    :y (+ y1 row)
                    :size sz}]
           :labels {:up (place up (:up labels))
                    :down (place down (:down labels))})))

(defn quad
  "The textured quad for `state`, as `raylib.texture/quad!` takes it: the whole
  screen, with texcoords from `scroll` to `scroll + tiles`, `h / w` as many down."
  [state {:keys [w h aspect]}]
  (let [{:keys [tiles scroll]} state]
    {:x 0
     :y 0
     :width w
     :height h
     :u0 scroll
     :v0 scroll
     :u1 (+ scroll tiles)
     :v1 (+ scroll (* tiles aspect))}))

(defn held-button
  "`:up`, `:down` or nil: the button a finger is on this frame."
  [geo input]
  (when (gesture/down? input)
    (let [at (:position (:pointer input))]
      (cond
        (gesture/in-rect? (:up geo) at) :up
        (gesture/in-rect? (:down geo) at) :down))))

(defn advance
  "One frame: the density moves while a button is held and the scroll grows."
  [state {:keys [metrics]
          :as input}]
  (let [held (held-button (geometry metrics) input)
        tiles (case held
                :up (clamp-tiles (+ (:tiles state) tiles-step))
                :down (clamp-tiles (- (:tiles state) tiles-step))
                (:tiles state))]
    (assoc state
           :tiles tiles
           :scroll (+ (:scroll state) scroll-step)
           :held held
           :screen (:screen metrics))))

(defn- init [{:keys [metrics]}]
  [{:tiles start-tiles
    :scroll 0.0
    :held nil
    :screen (:screen metrics)}
   [[:scene/init :textiling]]])

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :textiling]]])

(defn scene []
  {:id :textiling
   :title "Texture Tiling"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
