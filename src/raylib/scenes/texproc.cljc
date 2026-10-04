(ns raylib.scenes.texproc
  "Procedural Textures, ported from raylib-jlt's `texture_procedural`
  (net/b12n/raylib_jlt/texture_procedural.clj, zlib licence), which is
  raylib-jlt's own example rather than a port of a raylib C example. raylib
  6.0's nearest is `textures_image_generation`, which builds gradients, a
  checkerboard and noise with GenImage* on the CPU. Here: four textures
  generated pixel by pixel and uploaded to the GPU, each drawn as one quad.

  Mirrored from texture_procedural.clj:
  - TEX (line 21): each texture is 128 by 128 texels.
  - `checker` (lines 24-28): 16-texel cells, dark (40, 44, 52) where
    `(x / 16) + (y / 16)` is even, else light (230, 232, 238).
  - `gradient` (lines 30-35): red runs with x and green with y, each as
    `255 * v / 128` truncated, blue 140.
  - `noise` (lines 37-40): a grey, one value 0..255 a texel.
  - `rings` (lines 42-48): the distance from (64, 64), `sin (d / 6)`, then
    `v = 127 * (1 + t)` truncated, coloured `(v, 0.4 v truncated, 255 - v)`.
  - The order and names (lines 50-54): checkerboard, gradient, noise, rings.
  - SPACE (lines 71-74) re-uploads the noise texture in place. Here a press is
    the tap, and it bumps `:version`, which `raylib.texture/id!` reads as \"write
    the pixels again\".
  - The text (lines 77-82, 98-103): the heading, the size line, the panel names
    and \"SPACE reseeds the noise\", whose hint now reads \"Tap to reseed the
    noise\".

  Deviations. The noise does not come from raylib's GetRandomValue. It comes
  from the project LCG, copied privately as the sibling scenes do, so the scene
  stays pure and a seed always replays the same picture. The value is the high
  bits, `(quot seed' 65536)` mod 256, one step a texel in row order. A tap
  reseeds with a second LCG step (`reseed`), because stepping the same LCG once
  would only slide the old picture along by one texel. The panels are a 2 by 2
  grid on a portrait screen and a row of four on a landscape one, whichever
  gives the bigger panel, square, with the names under them.

  Entry cost. The first open after launch pauses for about 0.5 s on the phone
  (the largest single frame, measured), while the texture's pixels are computed
  and uploaded. Opening it again takes about 0.14 s, because the noise texture
  is versioned and so is not kept; the other three open again in about one
  frame.

  The state holds `:seed`, `:version` and `:screen`. A press bumps `:version` and
  reseeds once; the frames a finger stays down change nothing. Colours are `[r g
  b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.texel :as texel]))

(def tex-size "The original's TEX: each texture is this many texels square." 128)
(def default-seed "Where the first noise starts." 2026)

(def background-colour "RAYWHITE." [245 245 245 255])
(def title-colour "DARKGRAY." [80 80 80 255])
(def subtitle-colour "GRAY." [130 130 130 255])
(def hint-colour "GRAY." [130 130 130 255])
(def label-colour "DARKGRAY." [80 80 80 255])
(def outline-colour "LIGHTGRAY." [200 200 200 255])

(def title-line "The original's heading (line 77)." "procedural textures: every texel from a fn of (x, y)")
(def size-line "The original's size line (line 82)." (str tex-size "x" tex-size " RGBA8, uploaded with rlLoadTexture"))
(def hint-line "The original's hint, for a finger." "Tap to reseed the noise")

(def panel-keys "The order of the panels, as the original's." [:checker :gradient :noise :rings])
(def labels {:checker "checkerboard"
             :gradient "gradient"
             :noise "noise"
             :rings "rings"})

(defn checker-colour [x y]
  (if (even? (+ (quot x 16) (quot y 16)))
    [40 44 52 255]
    [230 232 238 255]))

(defn gradient-colour [x y]
  [(int (* 255 (/ x (double tex-size))))
   (int (* 255 (/ y (double tex-size))))
   140
   255])

(defn rings-colour [x y]
  (let [c (/ tex-size 2.0)
        d (Math/sqrt (+ (* (- x c) (- x c)) (* (- y c) (- y c))))
        t (Math/sin (/ d 6.0))
        v (int (* 127 (+ 1.0 t)))]
    [v (int (* 0.4 v)) (- 255 v) 255]))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn reseed
  "The seed a tap moves to: a second LCG step (multiplier 69069), so the new
  picture is not the old one slid along."
  [seed]
  (mod (+ (* 69069 (long seed)) 1) 2147483648))

(defn noise-values
  "The noise for `seed`: `tex-size` squared values 0..255 in row order, each the
  high bits of one LCG step from the one before. This is the original's `noise`
  fed the project LCG in place of GetRandomValue."
  [seed]
  (loop [i 0
         s (long seed)
         out (transient [])]
    (if (< i (* tex-size tex-size))
      (let [s' (next-random s)]
        (recur (inc i) s' (conj! out (mod (quot s' 65536) 256))))
      (persistent! out))))

(defn- spec [pixel & {:as more}]
  (merge {:w tex-size
          :h tex-size
          :pixel pixel}
         more))

;; The three static panels' pixel fns run 16384 times each when the scene opens,
;; so they pack without building a vector. `checker-colour`, `gradient-colour`
;; and `rings-colour` stay as the readable reference, and a test checks every
;; texel of these against them.

(defn checker-texel [x y]
  (if (even? (+ (quot x 16) (quot y 16)))
    (texel/pack4 40 44 52 255)
    (texel/pack4 230 232 238 255)))

(defn gradient-texel [x y]
  (texel/pack4 (int (* 255 (/ x (double tex-size))))
               (int (* 255 (/ y (double tex-size))))
               140
               255))

(defn rings-texel [x y]
  (let [c (/ tex-size 2.0)
        d (Math/sqrt (+ (* (- x c) (- x c)) (* (- y c) (- y c))))
        t (Math/sin (/ d 6.0))
        v (int (* 127 (+ 1.0 t)))]
    (texel/pack4 v (int (* 0.4 v)) (- 255 v) 255)))

(defn checker-spec [] (spec checker-texel))
(defn gradient-spec [] (spec gradient-texel))
(defn rings-spec [] (spec rings-texel))

(defn noise-spec
  "The noise panel's texture for `state`: grey from `noise-values`, with
  `:version` so `raylib.texture/id!` rewrites it in place when it changes."
  [{:keys [seed version]}]
  (let [vs (noise-values seed)]
    (spec (fn [x y]
            (let [v (nth vs (+ x (* y tex-size)))]
              (texel/pack [v v v 255])))
          :version version)))

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measured: `:w :h`, `:size
  :pad :row` (the text's), `:cols`, `:cell` (a panel's side) and `:panels`, a map
  of key to `{:rect [x y w h] :label-y y}`."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        side (min w h)
        size (max 16 (int (* 0.03 side)))
        pad (max 8 (int (* 0.5 size)))
        row (int (* 1.3 size))
        y1 (+ top pad)
        free-top (+ y1 row row pad)
        hint-y (- h pad size)
        free-h (- hint-y pad free-top)
        fit (fn [cols]
              (let [rows (quot (+ 3 cols) cols)
                    cw (quot (- w (* (inc cols) pad)) cols)
                    ch (quot (- free-h (* rows (+ pad row))) rows)]
                {:cols cols
                 :rows rows
                 :cell (max 1 (min cw ch))}))
        {:keys [cols rows cell]} (apply max-key :cell (map fit [2 4]))
        gw (+ (* cols cell) (* (dec cols) pad))
        gh (+ (* rows (+ cell row)) (* (dec rows) pad))
        x0 (quot (- w gw) 2)
        y0 (+ free-top (quot (- free-h gh) 2))]
    {:w w
     :h h
     :size size
     :pad pad
     :row row
     :y1 y1
     :hint-y hint-y
     :cols cols
     :cell cell
     :panels (into {}
                   (map-indexed
                    (fn [i k]
                      (let [x (+ x0 (* (rem i cols) (+ cell pad)))
                            y (+ y0 (* (quot i cols) (+ cell row pad)))]
                        [k {:rect [x y cell cell]
                            :label-y (+ y cell (quot (- row size) 2))}])))
                   panel-keys)}))

(defn dimensions
  "`geometry` plus `:lines`, the heading, the size line and the hint as `{:s :x
  :y :size}` at one size cut back until the widest fits the width, and `:labels`,
  each panel's name centred under it. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w pad row size y1 hint-y panels]
         :as geo} (geometry metrics)
        room (- w (* 2 pad))
        fit (fn [s] (max 8 (min size (int (/ (* room 100.0) (measure s 100))))))
        sz (min (fit title-line) (fit size-line) (fit hint-line))]
    (assoc geo
           :lines [{:s title-line
                    :x pad
                    :y y1
                    :size sz}
                   {:s size-line
                    :x pad
                    :y (+ y1 row)
                    :size sz}
                   {:s hint-line
                    :x pad
                    :y hint-y
                    :size sz}]
           :labels (into {}
                         (map (fn [k]
                                (let [{[x _ cw] :rect
                                       ly :label-y} (get panels k)
                                      s (get labels k)]
                                  [k {:s s
                                      :x (int (+ x (* 0.5 (- cw (measure s size)))))
                                      :y ly
                                      :size size}])))
                         panel-keys))))

(defn quad
  "The destination quad for panel `k` on the screen `geo` describes."
  [geo k]
  (let [[x y w h] (:rect (get (:panels geo) k))]
    {:x x
     :y y
     :width w
     :height h}))

(defn outline-rects
  "The outline of panel `k` as four `[x y w h]` rectangles, 2 pixels thick."
  [geo k]
  (let [[x y w h] (:rect (get (:panels geo) k))
        t 2]
    [[x y w t] [x (- (+ y h) t) w t] [x y t h] [(- (+ x w) t) y t h]]))

(defn tapped?
  "Whether this frame is a finger landing outside the area the host keeps for
  Back. Only the `:press` frame counts, as a key's first frame does."
  [input]
  (let [{:keys [phase position]} (:pointer input)]
    (boolean (and (= :press phase)
                  position
                  (not (gesture/in-back-region? position))))))

(defn advance
  "One frame: a press reseeds the noise and bumps its version, once."
  [state {:keys [metrics]
          :as input}]
  (let [tap (tapped? input)]
    (assoc state
           :seed (if tap (reseed (:seed state)) (:seed state))
           :version (if tap (inc (:version state)) (:version state))
           :screen (:screen metrics))))

(defn- init [{:keys [metrics]}]
  [{:seed default-seed
    :version 0
    :screen (:screen metrics)}
   [[:scene/init :texproc]]])

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :texproc]]])

(defn scene []
  {:id :texproc
   :title "Procedural Textures"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
