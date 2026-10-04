(ns net.b12n.raylib-ios.scenes.screenbuf
  "Screen Buffer, ported from raylib-jlt's `screen_buffer`
  (net/b12n/raylib_jlt/screen_buffer.clj, EPL 2.0), which is raylib's
  `textures_screen_buffer`: the classic DOS fire as a software screen buffer. A
  100 by 56 grid of palette indices is simulated, blitted through a 256-colour
  flame palette into a texture and drawn scaled up.
  The raylib C example it follows is zlib licensed, and this is an altered
  version of that too.

  Mirrored from screen_buffer.clj:
  - MAX-COLORS 256 (lines 21-26). The grid size is not mirrored, see below.
  - `hsv->color` and PALETTE (lines 28-53): `hsv->colour` and `palette`. Entry
    `i` has `t = i / 255` and hue `250 + 150 t^2`, with saturation and value both
    `t`, so the low indices stay dark.
  - `grow-roots` (lines 55-60): each root from column 2 on gains 0 to 2 a step,
    capped at 255; columns 0 and 1 stay put.
  - `seed-and-clear` (lines 62-69): the roots are copied into the bottom row and
    the top row is blanked.
  - `rise` (lines 71-96): a lit cell clears itself, drifts one column by
    `GetRandomValue(0, 2) - 1`, and if the new column is in 1..199 lands one row
    up, losing `GetRandomValue(0, 3)` (but never below 0). A cell whose new column
    falls outside vanishes. Rows go in ascending order from row 1.
  - The picture (lines 98-126): the whole grid, drawn as one scaled texture with
    nothing else on screen.

  Deviations. The randomness comes from the project LCG, seeded in the state, in
  place of GetRandomValue, so a seed always replays the same fire. Every call
  keeps the original's order and range, one for each root from column 2 and then
  one for the drift and one for the decay of each lit cell that stays inside.

  The grid. The original's is 200 by 112 (raylib's C example's is 400 by 225);
  this one is 100 by 56, a quarter of the cells. The rules are the same, so the
  flame is relatively taller (the decay per row is unchanged) and each cell is
  twice as wide on the screen.

  The step rate. The original runs the whole simulation and rewrites every texel
  each frame. Under laptop jolt one whole step on this grid is about 0.47 ms and
  a whole upload about 0.7 ms, which the phone, about 33 times slower, cannot pay
  in a frame. So both go a band at a time, on one schedule. Each frame steps and
  uploads `band-rows` (8) rows, top to bottom, and a sweep of `period` (7) frames
  is exactly one of the original's steps: the roots grow and the bottom and top
  rows are set on the first band, then the rest rise in the original's ascending
  order. So the fire follows the original's rules but runs `period` times slower,
  60 / 7, about 8.6 steps a second at 60 frames a second against the original's
  60. Measured on a laptop under jolt, a frame (the step of its band plus the
  upload of the band and the row above) took 0.19 ms on average and 0.29 ms at
  the 99th percentile, with an occasional 2 ms frame when the collector ran. A
  band's upload also takes the row above it, because that is where the band's
  cells land. Rows below the band are older than rows above it by up to `period`
  frames, so a moving seam between fresher and older rows can show.

  The hot start. The original's roots start at 0 and grow by 0 to 2 a step, so
  at 60 steps a second the fire takes a few seconds to light. At 8.6 steps a
  second a cold fire would take about 30 s to light (an estimate from the step
  rate, not a measurement: the roots grow a mean of 1 a step, so 255 / 8.57 steps
  a second). That is long to watch a dark screen, so the roots start hot: from
  column 2 on each is a random 192 to 255 (one LCG draw each), and columns 0 and
  1 stay dark as they do in the original. The first sweep already has a flame.

  Entering the scene fills all 5600 texels once, a one-off cost.

  The picture is the 100 by 56 grid scaled to the widest size the free area
  allows, below Back, with nearest-neighbour filtering. The original's window
  title is the only text, so there is none. The state holds `:frame`, `:seed`,
  `:roots`, `:buf`, `:stepped` (the band the last frame stepped, which the draw uploads) and `:screen`."
  (:require [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.texel :as texel]))

(def img-w "This grid's width: the original's IMG-W is 200, raylib's C is 400." 100)
(def img-h "This grid's height: the original's IMG-H is 112, raylib's C is 225." 56)
(def max-colours "The original's MAX-COLORS." 256)
(def band-rows "How many rows one frame steps and uploads." 8)
(def period
  "How many frames one sweep takes, which is one of the original's steps: the
  rows divided by the band, rounded up."
  (quot (+ img-h band-rows -1) band-rows))
(def hot-low "The coolest a root can start: indices 192 to 255 are the palette's bright end." 192)
(def default-seed "Where the first fire starts." 2026)

(def background-colour "RAYWHITE." [245 245 245 255])

(defn hsv->colour
  "The original's `hsv->color` (lines 28-45) as `[r g b a]`."
  [h s v]
  (let [h' (/ (mod h 360.0) 60.0)
        i (long (Math/floor h'))
        f (- h' i)
        p (* v (- 1.0 s))
        q (* v (- 1.0 (* s f)))
        t (* v (- 1.0 (* s (- 1.0 f))))
        [r g b] (case (mod i 6)
                  0 [v t p]
                  1 [q v p]
                  2 [p v t]
                  3 [p q v]
                  4 [t p v]
                  5 [v p q])]
    [(int (* 255 r)) (int (* 255 g)) (int (* 255 b)) 255]))

(def palette
  "The original's PALETTE (lines 47-53): 256 `[r g b a]`, hue eased by t*t."
  (mapv (fn [i]
          (let [t (/ i (double (dec max-colours)))
                hue (* t t)]
            (hsv->colour (+ 250.0 (* 150.0 hue)) t t)))
        (range max-colours)))

(def palette-texels "`palette`, packed." (mapv texel/pack palette))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn grow-roots
  "The original's `grow-roots` (lines 55-60): each root from column 2 on gains a
  random 0 to 2, capped at 255. Answers `[roots seed]`."
  [roots seed]
  (loop [x 0 seed seed out (transient [])]
    (if (< x img-w)
      (let [v (nth roots x)]
        (if (>= x 2)
          (let [s' (next-random seed)]
            (recur (inc x) s' (conj! out (min 255 (+ v (mod (quot s' 65536) 3))))))
          (recur (inc x) seed (conj! out v))))
      [(persistent! out) seed])))

(defn seed-and-clear
  "The original's `seed-and-clear` (lines 62-69): the roots into the bottom row,
  then the top row blanked."
  [buf roots]
  (let [base (* (dec img-h) img-w)
        b (reduce (fn [b x] (assoc b (+ x base) (nth roots x))) buf (range img-w))]
    (reduce (fn [b x] (assoc b x 0)) b (range img-w))))

(defn rise
  "The original's `rise` (lines 71-96) over rows `y0` (at least 1) up to but not
  including `y1`: every lit cell moves up one row with a random sideways drift
  and a random decay, clearing the cell it came from. Rows go in ascending order
  and a cell writes into the row above, which has already been visited, so
  nothing rises twice in one pass. Answers `[buf seed]`."
  [buf seed y0 y1]
  (loop [y (max 1 y0) b buf seed seed]
    (if (< y y1)
      (let [[b seed]
            (loop [x 0 b b seed seed]
              (if (>= x img-w)
                [b seed]
                (let [i (+ x (* y img-w))
                      ci (nth b i)]
                  (if (zero? ci)
                    (recur (inc x) b seed)
                    (let [b (assoc b i 0)
                          s1 (next-random seed)
                          mv (dec (mod (quot s1 65536) 3))
                          nx (+ x mv)]
                      (if (and (> nx 0) (< nx img-w))
                        (let [s2 (next-random s1)
                              d (mod (quot s2 65536) 4)
                              nc (- ci (min d ci))]
                          (recur (inc x) (assoc b (+ (- i img-w) mv) nc) s2))
                        (recur (inc x) b s1)))))))]
        (recur (inc y) b seed))
      [b seed])))

(defn hot-roots
  "The roots a fire starts from: columns 0 and 1 dark, as they stay in the
  original, and every other root a random `hot-low` to 255, one LCG draw each.
  The original starts every root at 0 and waits for them to grow. Answers
  `[roots seed]`."
  [seed]
  (loop [x 0 seed seed out (transient [])]
    (if (< x img-w)
      (if (>= x 2)
        (let [s' (next-random seed)]
          (recur (inc x) s' (conj! out (+ hot-low (mod (quot s' 65536) (- 256 hot-low))))))
        (recur (inc x) seed (conj! out 0)))
      [(persistent! out) seed])))

(defn fresh
  "The fire before its first step: every cell dark, and the roots hot (see
  `hot-roots`), so the first sweep already has a flame."
  [seed]
  (let [[roots seed] (hot-roots seed)]
    {:buf (vec (repeat (* img-w img-h) 0))
     :roots roots
     :seed seed}))

(defn band
  "The rows of band `k` as `[y0 y1]`, `y1` exclusive and held to the grid."
  [k]
  (let [y0 (* k band-rows)]
    [y0 (min img-h (+ y0 band-rows))]))

(defn step-band
  "Band `k` (0 to `period` - 1) of one of the original's steps over `sim`, the
  `{:buf :roots :seed}` map. Band 0 first does the step's opening, in the
  original's order: the roots grow, then they are copied into the bottom row and
  the top row is blanked. Every band then lets its rows rise. A sweep of bands
  0 to `period` - 1 is therefore the original's step exactly, draw for draw."
  [{:keys [buf roots seed]} k]
  (let [[y0 y1] (band k)
        [roots seed buf] (if (zero? k)
                           (let [[roots seed] (grow-roots roots seed)]
                             [roots seed (seed-and-clear buf roots)])
                           [roots seed buf])
        [buf seed] (rise buf seed y0 y1)]
    {:buf buf
     :roots roots
     :seed seed}))

(defn upload-rows
  "The rows to refill after band `k` stepped, as `[y0 rows]`: the band's own rows
  and the row above them, which is where their cells landed (the first band has
  no row above). `k` is the band `advance` just stepped, kept in `:stepped`,
  because the draw runs after the update."
  [k]
  (let [[y0 y1] (band k)
        top (max 0 (dec y0))]
    [top (- y1 top)]))

(defn palette-pixel
  "The texel at `x`, `y` of `buf`, through the palette, packed."
  [buf x y]
  (nth palette-texels (nth buf (+ x (* y img-w)))))

(defn spec
  "The fire's texture for `net.b12n.raylib-ios.texture/band!`: 100 by 56, clamped and
  unfiltered, each texel the palette colour of its cell in `buf`."
  [buf]
  {:w img-w
   :h img-h
   :pixel (fn [x y] (palette-pixel buf x y))})

(defn geometry
  "The picture's destination quad on `metrics`' `:screen`: the 100 by 56 grid
  scaled to the biggest size that fits below Back, centred. `:x :y :width
  :height`."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        free-h (- h top)
        k (min (/ w (double img-w)) (/ free-h (double img-h)))
        qw (max 1 (int (* k img-w)))
        qh (max 1 (int (* k img-h)))]
    {:x (quot (- w qw) 2)
     :y (+ top (quot (- free-h qh) 2))
     :width qw
     :height qh}))

(defn advance
  "One frame: band `frame mod period` of the fire steps."
  [state {:keys [metrics]}]
  (let [k (mod (:frame state) period)
        sim (step-band state k)]
    (assoc state
           :stepped k
           :buf (:buf sim)
           :roots (:roots sim)
           :seed (:seed sim)
           :frame (inc (:frame state))
           :screen (:screen metrics))))

(defn- init [{:keys [metrics]}]
  [(assoc (fresh default-seed)
          :frame 0
          :stepped 0
          :screen (:screen metrics))
   [[:scene/init :screenbuf]]])

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :screenbuf]]])

(defn scene []
  {:id :screenbuf
   :title "Screen Buffer"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
