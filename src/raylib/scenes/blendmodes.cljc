(ns raylib.scenes.blendmodes
  "Blend Modes, ported from raylib-jlt's `blend_modes`
  (net/b12n/raylib_jlt/blend_modes.clj, zlib licence): a cluster of three
  coloured glows drawn over a night skyline through each of four blend modes in
  turn.

  What is the original's:
  - The modes and their order (lines 28-31): ALPHA, ADDITIVE, MULTIPLIED and
    ADD_COLORS, which are raylib.h's `BlendMode` values 0, 1, 2 and 3. The
    skyline is drawn plainly, then the glow between `BeginBlendMode` and
    `EndBlendMode` (lines 106-115).
  - The picture: 800 by 450 units, drawn from two 400 by 225 textures stretched
    to it, so a texture pixel is 2 units (lines 24-27, 106-114).
  - The clear colour, RAYWHITE, and the GRAY text (lines 105, 116-125).

  The two textures are procedural (`sky-pixel`, lines 44-66, and `glow-pixel`,
  lines 73-87), and there is no texture here, so each is redrawn as shapes:
  - The sky is one vertical gradient quad from (18, 12, 42) to (58, 24, 74), the
    ends of `lerp` (lines 48-51; the original truncates each channel to an int
    per row, so its gradient steps where this one is smooth).
  - The buildings are one rect a column of 22 texture pixels, from `top` down:
    `top` is `horizon + int(span * 0.5 * hash01(col))` with `horizon` 153 (lines
    52-57), in (14, 10, 22). `hash01` is the original's (lines 37-41), so the
    skyline is the same one.
  - The windows (line 61) are lit where `lx` is 5 to 17, `(quot (- y top) 10) +
    (quot lx 6)` is a multiple of 3, and `hash01(col * 977 + quot y 10)` is
    under 0.4. Those depend only on the row band and on which sixth of the
    column `lx` is in, so each is a rect, a run of rows by one of the three
    spans of `lx` (5, 6 to 11, 12 to 17). `emit-windows!` is every such rect, and
    a test rasterises the buildings and windows and compares all 90000 pixels with
    the original's rule.
  - Each glow is a triangle fan of 16 triangles, its centre (r, g, b, 255) and
    its rim (0, 0, 0, 0). A colour interpolated linearly across a fan is `k *
    colour` with `k = 1 - d / radius`, which is the original's `k * br`, `k * 255`
    (lines 80-86) to within the fan's 16-sided edge. A base quad of (0, 0, 0, 0)
    is drawn first, over the whole picture, as the texture is transparent black
    outside the blobs.

  Deviations, all in the glow: the original sums the three blobs into the
  texture and blends once, where here each blob is blended on its own, so an
  overlap is not the sum of the two glows' alphas but the second blended over
  the first. The fan has 16 sides, not a circle. Under MULTIPLIED, rlgl's
  `glBlendFunc(GL_DST_COLOR, GL_ONE_MINUS_SRC_ALPHA)` leaves a pixel alone where
  the source is (0, 0, 0, 0), so the original's note that MULTIPLIED blackens
  everything the blobs do not reach is not what the equation gives: the base
  quad changes nothing in any of the four modes, and is kept because the
  texture it stands for covers the rect.

  Controls: a tap anywhere outside Back, in place of SPACE, moves to the next
  mode. The tap is by `raylib.gesture`, so it is where the finger started. The
  texts go above the picture, not on it at (300, 350) and (W / 2 - 60, 370) at
  size 10, since size 10 is too small to read on a phone; the second still names
  the mode.

  The picture is the largest 16:9 that fits below the texts, centred. The draw
  method draws the glow through `call-blended!`, which ends the blend mode in a
  `finally`, so a failed draw cannot leave a later scene blending."
  (:require [raylib.gesture :as gesture]))

(def view-w "The original's window width, in its units." 800.0)
(def view-h "The original's window height." 450.0)
(def tex-w "The original's texture width." 400)
(def tex-h "The original's texture height." 225)

(def modes
  "The original's MODES: raylib.h's BlendMode value and the name it prints."
  [[0 "BLEND_ALPHA"]
   [1 "BLEND_ADDITIVE"]
   [2 "BLEND_MULTIPLIED"]
   [3 "BLEND_ADD_COLORS"]])

(def background-colour "RAYWHITE." [245 245 245 255])
(def text-colour "GRAY." [130 130 130 255])
(def sky-top "Texture row 0 of `sky-pixel`'s gradient." [18 12 42 255])
(def sky-bottom "The gradient's far end (t = 1)." [58 24 74 255])
(def building-colour "`sky-pixel`'s wall." [14 10 22 255])
(def window-colour "`sky-pixel`'s lit window." [255 214 120 255])
(def clear-glow "The texture outside every blob: transparent black." [0 0 0 0])

(def blobs
  "The original's BLOBS (lines 68-71): centre x, centre y and radius in texture
  pixels, and the colour."
  [[120.0 110.0 70.0 [0 255 255]]
   [220.0 150.0 60.0 [255 0 255]]
   [170.0 170.0 55.0 [255 230 0]]])

(def fan-segments "Triangles in one glow's fan." 16)

(def hint-line "The original's hint, with a tap for SPACE." "Tap to change blend modes")

(defn current-line [mode-idx]
  (str "Current: " (second (nth modes mode-idx))))

(defn hash01
  "The original's `hash01` (lines 37-41): a deterministic value in [0, 1) from
  an int."
  [n]
  (let [h (bit-and (* (+ n 12345) 2654435761) 0xffffffff)]
    (/ (double (bit-and h 0xffff)) 65536.0)))

(def ^:private horizon (int (* tex-h 0.68)))
(def ^:private column 22)

(defn- col-top [col]
  (+ horizon (int (* (- tex-h horizon) 0.5 (hash01 col)))))

(def buildings
  "One `[x y w h]` in texture pixels a column of 22, from its `top` to the
  bottom."
  (vec (for [col (range (quot (+ tex-w column -1) column))
             :let [x0 (* col column)
                   top (col-top col)]]
         [x0 top (min column (- tex-w x0)) (- tex-h top)])))

(defn- column-windows
  "Column `col`'s windows as `[x y w h]` in texture pixels. A run of rows is
  one rect when neither `(quot (- y top) 10)` nor `(quot y 10)` changes in it."
  [col]
  (let [x0 (* col column)
        top (col-top col)]
    (loop [y top
           acc []]
      (if (>= y tex-h)
        acc
        (let [qrel (quot (- y top) 10)
              band (quot y 10)
              y-end (min tex-h (+ top (* 10 (inc qrel))) (* 10 (inc band)))
              j (mod (- 3 (mod qrel 3)) 3)
              [lx0 lx1] (case (int j) 0 [5 6] 1 [6 12] [12 18])
              lit? (< (hash01 (+ (* col 977) band)) 0.4)
              x (+ x0 lx0)]
          (recur y-end
                 (if (and lit? (< x tex-w))
                   (conj acc [x y (min (- lx1 lx0) (- tex-w x)) (- y-end y)])
                   acc)))))))

(def windows
  "Every lit window as `[x y w h]` in texture pixels."
  (vec (mapcat column-windows (range (count buildings)))))

(def ^:private ring
  "Unit-circle points for one fan, `fan-segments` + 1 of them."
  (vec (for [k (range (inc fan-segments))
             :let [a (/ (* 2.0 Math/PI k) fan-segments)]]
         [(Math/cos a) (Math/sin a)])))

(defn mode-of
  "`[blend-mode name]` for `state`."
  [state]
  (nth modes (:mode-idx state)))

(defn geometry
  "The layout for `metrics`' `:screen`, without text: `:w :h :top` (the bottom
  of Back), `:size :pad :row` (the text's), `:scale` (pixels per original unit),
  `:f` (pixels per texture pixel), `:ox :oy` (the picture's corner) and `:pic-w
  :pic-h`."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (double (+ back-y back-h))
        side (min w h)
        size (max 16 (int (* 0.03 side)))
        pad (max 8 (int (* 0.5 size)))
        row (int (* 1.3 size))
        pic-top (+ top pad row row pad)
        scale (max 0.01 (min (/ (double w) view-w) (/ (- h pic-top pad) view-h)))
        pic-w (* scale view-w)]
    {:w w
     :h h
     :top top
     :size size
     :pad pad
     :row row
     :scale scale
     :f (* 2.0 scale)
     :ox (* 0.5 (- w pic-w))
     :oy pic-top
     :pic-w pic-w
     :pic-h (* scale view-h)}))

(defn dimensions
  "`geometry` plus `:lines`: the hint and the widest current-mode line as `{:s
  :x :y :size}`, at one size cut back until every mode's line fits the width.
  `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w pad row size top]
         :as geo} (geometry metrics)
        names (map current-line (range (count modes)))
        widest (apply max-key #(measure % 100) names)
        room (- w (* 2 pad))
        fit (fn [s] (max 8 (min size (int (/ (* room 100.0) (measure s 100))))))
        size1 (apply min (fit hint-line) (map fit names))
        y1 (int (+ top pad))]
    (assoc geo
           :lines [{:s hint-line
                    :x pad
                    :y y1
                    :size size1}
                   {:s widest
                    :x pad
                    :y (+ y1 row)
                    :size size1}])))

(defn- emit-rects!
  "Call `(rect! x y w h)` in pixels for each of `rects` in texture pixels, the
  edges cut to whole pixels so neighbours share an edge."
  [rect! rects {:keys [ox oy f]}]
  (let [ox (double ox)
        oy (double oy)
        f (double f)
        n (count rects)]
    (loop [i 0]
      (when (< i n)
        (let [[tx ty tw th] (nth rects i)
              x0 (int (+ ox (* f tx)))
              y0 (int (+ oy (* f ty)))
              x1 (int (+ ox (* f (+ tx tw))))
              y1 (int (+ oy (* f (+ ty th))))]
          (rect! x0 y0 (- x1 x0) (- y1 y0)))
        (recur (inc i))))))

(defn emit-buildings!
  "Call `(rect! x y w h)` for each building, in pixels. `dims` needs `:ox :oy
  :f`."
  [rect! dims]
  (emit-rects! rect! buildings dims))

(defn emit-windows!
  "Call `(rect! x y w h)` for each lit window, in pixels."
  [rect! dims]
  (emit-rects! rect! windows dims))

(defn emit-glows!
  "Call `(tri! x1 y1 x2 y2 x3 y3 r g b)` for each triangle of each blob's fan,
  in pixels. The first vertex is the centre, drawn (r, g, b, 255); the other two
  are the rim, drawn (0, 0, 0, 0). Every triangle is wound as `draw-triangle`
  winds one: a negative cross product of its first two edges."
  [tri! {:keys [ox oy f]}]
  (let [ox (double ox)
        oy (double oy)
        f (double f)]
    (doseq [[cx cy radius [r g b]] blobs]
      (let [px (+ ox (* f cx))
            py (+ oy (* f cy))
            rad (* f radius)]
        (loop [k 0]
          (when (< k fan-segments)
            (let [[c0 s0] (nth ring k)
                  [c1 s1] (nth ring (inc k))]
              (tri! px py
                    (+ px (* rad c1)) (+ py (* rad s1))
                    (+ px (* rad c0)) (+ py (* rad s0))
                    r g b))
            (recur (inc k))))))))

(defn call-blended!
  "Run `(body!)` with `mode` begun, calling `(end!)` afterwards on every path,
  a throw included. `begin!` is inside the `try` too, so a begin that fails
  half way still ends: ending the default mode is harmless."
  [begin! end! mode body!]
  (try
    (begin! mode)
    (body!)
    (finally
      (end!))))

(defn advance
  "One frame. A tap that began outside Back moves to the next mode, wrapping.
  When the metrics report a different `:screen` the gesture is reset, since its
  start was in the old screen's pixels."
  [state {:keys [metrics]
          :as input}]
  (let [turned? (not= (:screen state) (:screen metrics))
        [g event] (gesture/track (if turned? gesture/idle (:gesture state)) input)
        next? (and (= :tap (:type event))
                   (not (gesture/in-back-region? (:at event))))]
    (assoc state
           :mode-idx (if next? (mod (inc (:mode-idx state)) (count modes)) (:mode-idx state))
           :gesture g
           :screen (:screen metrics))))

(defn- init [{:keys [metrics]}]
  [{:mode-idx 0
    :gesture gesture/idle
    :screen (:screen metrics)}
   [[:scene/init :blendmodes]]])

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :blendmodes]]])

(defn scene []
  {:id :blendmodes
   :title "Blend Modes"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
