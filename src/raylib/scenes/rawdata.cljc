(ns raylib.scenes.rawdata
  "Raw Data, ported from raylib-jlt's `raw_data`
  (net/b12n/raylib_jlt/raw_data.clj, EPL 2.0), which is raylib's
  `textures_raw_data`: a texture is a flat block of bytes this program fills in
  itself. Two panels, a 256 by 256 checkerboard, uploaded once, and a 128 by 128
  panel whose colour is arithmetic on x, y and time.
  The raylib C example it follows is zlib licensed, and this is an altered
  version of that too.

  Mirrored from raw_data.clj:
  - The sizes (lines 26-38): PANEL 256, LIVE 128, and CHECK 32 for the squares.
  - `checker` (lines 40-45): ORANGE (255, 161, 0) where `(x / 32) + (y / 32)` is
    even, else GOLD (255, 203, 0).
  - `channels` (lines 47-56): with `fx = x / 128` and `fy = y / 128`, red is
    `255 * |sin (6 fx + t)|`, green `255 * |sin (6 fy + 0.7 t)|` and blue
    `255 * |cos (4 (fx + fy) + 1.3 t)|`, each truncated, alpha 255.
  - The clock (line 71): `t = frame * 0.03`.
  - The text (lines 75-124): the heading, the subtitle, the two panel names and
    the stride caption.

  Deviations. The original rewrites all 16384 live texels every frame. That cost
  about 7 ms on a laptop; on the phone a whole refill of the panel is most of
  the 171 ms measured on the first open after launch, so the live panel is
  refreshed a band at a time: each frame refills `band-rows` (3) rows, walking
  down the panel, so every row is refreshed every `period` (43) frames
  (the last band is 2 rows), which is about 1.4 times a second at 60 frames a
  second. The rows of one band share one `t`, but a row refreshed this sweep and
  one refreshed the last are `period` frames apart in time, so a moving seam
  shows between fresh and older rows, and the picture drifts at a fraction of the
  original's rate. The live panel's name says \"a band at a time\" in place of
  \"every frame\". The fps readout is left out. The panels stack on a portrait
  screen and sit side by side on a landscape one, whichever gives the bigger
  square, with each name above its panel.

  The checkerboard is a 64 by 64 texture drawn with `:wrap :repeat` and texcoords
  0 to 4, not the original's 256 by 256 upload. With CHECK 32 the pattern repeats
  every 64 texels, so each drawn texel is the same colour, and the entry fill
  drops from 65536 texels to 4096. Opening the scene again
  costs about 0.15 s, because the banded live panel is not kept.

  The state holds `:frame` and `:screen`. Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.texel :as texel]))

(def panel-size "The original's PANEL: the drawn checkerboard is this many texels square." 256)
(def checker-size "The checkerboard texture's side: CHECK * 2, the pattern's period." 64)
(def checker-repeat "How many times the checker texture repeats across the panel." (quot panel-size checker-size))
(def live-size "The original's LIVE: the live panel is this many texels square." 128)
(def check "The original's CHECK: a square of the checkerboard is this many texels." 32)
(def time-step "The original's clock: `t` grows this much a frame." 0.03)

(def band-rows "How many rows of the live panel one frame refills." 3)
(def period
  "How many frames a full refresh of the live panel takes: its rows divided by
  the band, rounded up, so every row is refreshed once every `period` frames."
  (quot (+ live-size band-rows -1) band-rows))

(def background-colour "RAYWHITE." [245 245 245 255])
(def title-colour "DARKGRAY." [80 80 80 255])
(def subtitle-colour "GRAY." [130 130 130 255])
(def label-colour "DARKGRAY." [80 80 80 255])
(def caption-colour "GRAY." [130 130 130 255])
(def outline-colour "DARKGRAY." [80 80 80 255])
(def orange "ORANGE." [255 161 0 255])
(def gold "GOLD." [255 203 0 255])

(def title-line "The original's heading (line 75)." "raylib [textures] example - raw data")
(def subtitle-line "The original's subtitle (line 80)." "textures built from a byte buffer this program filled in itself")
(def caption-line "The original's stride caption (line 116)." "RGBA8, row-major: pixel (x,y) starts at byte 4*(x + y*w)")

(def panel-keys "The order of the panels, as the original's." [:checker :live])
(def labels {:checker "checkerboard, uploaded once"
             :live "RGB computed per pixel, re-uploaded a band at a time"})

(defn checker-colour
  "The original's `checker` (lines 40-45) at texel `x`, `y`, as `[r g b a]`."
  [x y]
  (if (even? (+ (quot x check) (quot y check)))
    orange
    gold))

(defn live-time
  "The original's `t` (line 71) on frame `frame`."
  [frame]
  (* frame time-step))

(defn live-colour
  "The original's `channels` (lines 47-56) at texel `x`, `y` and time `t`, as
  `[r g b a]`."
  [t x y]
  (let [fx (/ (double x) live-size)
        fy (/ (double y) live-size)
        r (int (* 255 (Math/abs (Math/sin (+ (* fx 6.0) t)))))
        g (int (* 255 (Math/abs (Math/sin (+ (* fy 6.0) (* t 0.7))))))
        b (int (* 255 (Math/abs (Math/cos (+ (* (+ fx fy) 4.0) (* t 1.3))))))]
    [r g b 255]))

(defn checker-spec
  "The checkerboard for `raylib.texture/id!`: one 64 by 64 period, repeating,
  unfiltered. `quad` repeats it 4 times across, which is the original's 256 by
  256 panel."
  []
  {:w checker-size
   :h checker-size
   :wrap :repeat
   :pixel (fn [x y]
            (if (even? (+ (quot x check) (quot y check)))
              (texel/pack4 255 161 0 255)
              (texel/pack4 255 203 0 255)))})

(defn live-spec
  "The live panel at `frame` for `raylib.texture/band!`: 128 by 128, each texel
  `live-colour` at `(live-time frame)`, packed without a vector."
  [frame]
  (let [t (live-time frame)]
    {:w live-size
     :h live-size
     :pixel (fn [x y]
              (let [fx (/ (double x) live-size)
                    fy (/ (double y) live-size)
                    r (int (* 255 (Math/abs (Math/sin (+ (* fx 6.0) t)))))
                    g (int (* 255 (Math/abs (Math/sin (+ (* fy 6.0) (* t 0.7))))))
                    b (int (* 255 (Math/abs (Math/cos (+ (* (+ fx fy) 4.0) (* t 1.3))))))]
                (texel/pack4 r g b 255)))}))

(defn band
  "The rows to refill on `frame`, as `[y0 rows]`: the band walks down the panel
  and starts again at the top, so `period` frames cover every row once. The last
  band is cut short where the panel ends."
  [frame]
  (let [y0 (* band-rows (mod frame period))]
    [y0 (min band-rows (- live-size y0))]))

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measured: `:size :pad :row`
  (the text's), `:y1`, `:caption-y`, `:cell` (a panel's side), `:cols` and
  `:panels`, a map of key to `{:rect [x y w h] :label-y y}`. Each name sits above
  its panel."
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
        caption-y (- h pad size)
        free-h (- caption-y pad free-top)
        fit (fn [cols]
              (let [rows (quot (+ 1 cols) cols)
                    cw (quot (- w (* (inc cols) pad)) cols)
                    ch (quot (- free-h (* rows (+ pad row))) rows)]
                {:cols cols
                 :rows rows
                 :cell (max 1 (min cw ch))}))
        {:keys [cols rows cell]} (apply max-key :cell (map fit [1 2]))
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
     :caption-y caption-y
     :cols cols
     :cell cell
     :panels (into {}
                   (map-indexed
                    (fn [i k]
                      (let [x (+ x0 (* (rem i cols) (+ cell pad)))
                            y (+ y0 (* (quot i cols) (+ cell row pad)))]
                        [k {:rect [x (+ y row) cell cell]
                            :label-y y}])))
                   panel-keys)}))

(defn dimensions
  "`geometry` plus `:lines`, the heading, the subtitle and the stride caption as
  `{:s :x :y :size}` at one size cut back until the widest fits the width, and
  `:labels`, each panel's name above it, left-aligned with the panel at a size
  cut back until it fits between the panel's left edge and the screen's right margin. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w pad row size y1 caption-y panels]
         :as geo} (geometry metrics)
        room (- w (* 2 pad))
        fit (fn [s sz room] (max 8 (min sz (int (/ (* room 100.0) (measure s 100))))))
        sz (min (fit title-line size room) (fit subtitle-line size room) (fit caption-line size room))]
    (assoc geo
           :lines [{:s title-line
                    :x pad
                    :y y1
                    :size sz}
                   {:s subtitle-line
                    :x pad
                    :y (+ y1 row)
                    :size sz}
                   {:s caption-line
                    :x pad
                    :y caption-y
                    :size sz}]
           :labels (into {}
                         (map (fn [k]
                                (let [{[x] :rect
                                       ly :label-y} (get panels k)
                                      s (get labels k)]
                                  [k {:s s
                                      :x x
                                      :y ly
                                      :size (fit s size (- w x pad))}])))
                         panel-keys))))

(defn quad
  "The destination quad for panel `k` on the screen `geo` describes. The
  checkerboard's texcoords run 0 to `checker-repeat`, so its 64 texel period
  covers the panel as the original's 256 texels did."
  [geo k]
  (let [[x y w h] (:rect (get (:panels geo) k))
        uv (if (= :checker k) (double checker-repeat) 1.0)]
    {:x x
     :y y
     :width w
     :height h
     :u1 uv
     :v1 uv}))

(defn outline-rects
  "The outline of panel `k` as four `[x y w h]` rectangles, 2 pixels thick."
  [geo k]
  (let [[x y w h] (:rect (get (:panels geo) k))
        t 2]
    [[x y w t] [x (- (+ y h) t) w t] [x y t h] [(- (+ x w) t) y t h]]))

(defn advance
  "One frame: the clock moves on."
  [state {:keys [metrics]}]
  (assoc state
         :frame (inc (:frame state))
         :screen (:screen metrics)))

(defn- init [{:keys [metrics]}]
  [{:frame 0
    :screen (:screen metrics)}
   [[:scene/init :rawdata]]])

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :rawdata]]])

(defn scene []
  {:id :rawdata
   :title "Raw Data"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
