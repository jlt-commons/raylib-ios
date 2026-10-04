(ns net.b12n.raylib-ios.scenes.texpoly
  "Polygon Drawing, ported from raylib-jlt's `polygon_drawing`
  (net/b12n/raylib_jlt/polygon_drawing.clj, EPL 2.0), which is raylib's
  `textures_polygon_drawing`: a hue wheel mapped onto a spinning ten-sided
  polygon, drawn as a triangle fan, the same reimplementation of DrawTexturePoly
  the C example uses. The original has no polygon helper in its library; the fan
  is written out in the example (lines 85-101), and `fan` here is that loop.
  The raylib C example it follows is zlib licensed, and this is an altered
  version of that too.

  Mirrored from polygon_drawing.clj:
  - The texture (lines 35-59): 256 by 256 (line 23), the angle of each texel
    around the middle as a hue at full saturation and value, `hsv->colour` and
    `wheel-colour`.
  - `TEXCOORDS` (lines 27-29) and `POINTS` (lines 32-33), the points being
    `(uv - 0.5) * 256`.
  - The fan (lines 85-101): ten triangles, each the middle at uv (0.5, 0.5) and
    two neighbouring points rotated by the angle about the middle, with the
    neighbours' own uvs; the last point repeats the first, so the loop closes.
  - The spin (lines 76-77): the angle grows 1.0 degree a frame, with no wrap, and
    `DEG2RAD` is 0.0174532925 (line 24).
  - The caption (lines 80-83): \"textured polygon\", DARKGRAY.

  Deviations. The polygon is scaled so that its farthest point, at a distance of
  143 from the middle, sits at 45 percent of the shorter free side, and the middle
  is the middle of the free area under the caption. At a scale of 1 and the
  middle at (400, 225) the fan is the original's to the digit. The caption is
  fitted to the width. Update runs before draw, so the first drawn frame is
  already at 1 degree, as in the original, whose angle is added before use.

  Wrap. The sheet is clamped. Every uv here lies in 0..1, so nothing needs
  REPEAT, and a clamp keeps a vertex sitting exactly on u or v of 1.0 from
  sampling the far side.

  Entry cost. The first open after launch pauses for about 0.8 s on the phone
  (the largest single frame, measured), while the texture's pixels are computed
  and uploaded. Opening it again takes about one frame, because the filled
  texture buffer is kept.

  The state holds `:angle` and `:screen`. Colours are `[r g b a]` vectors."
  (:require [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.texel :as texel]))

(def tex-size "The original's TEX: the wheel is this many texels square." 256)
(def deg2rad "The original's DEG2RAD (line 24)." 0.0174532925)
(def spin-step "Degrees a frame." 1.0)
(def radius-fraction "How far the farthest point reaches, of the shorter free side." 0.45)

(def background-colour "RAYWHITE." [245 245 245 255])
(def title-colour "DARKGRAY." [80 80 80 255])
(def tint "WHITE." [255 255 255 255])

(def title-line "The original's caption." "textured polygon")

(def texcoords
  "The original's TEXCOORDS: where each point is in the wheel; the last closes the
  loop."
  [[0.75 0.0] [0.25 0.0] [0.0 0.5] [0.0 0.75] [0.25 1.0]
   [0.375 0.875] [0.625 0.875] [0.75 1.0] [1.0 0.75] [1.0 0.5] [0.75 0.0]])

(def points
  "The original's POINTS: `(uv - 0.5) * 256`."
  (mapv (fn [[u v]] [(* (- u 0.5) 256.0) (* (- v 0.5) 256.0)]) texcoords))

(def reach
  "How far the farthest point is from the middle."
  (reduce max (map (fn [[x y]] (Math/sqrt (+ (* x x) (* y y)))) points)))

(defn hsv->colour
  "The original's `hsv->color` (lines 35-49) for saturation 1 and value 1, as
  `[r g b a]`. `h` is in degrees and wraps."
  [h]
  (let [h' (/ (mod h 360.0) 60.0)
        i (int (Math/floor h'))
        f (- h' i)
        q (- 1.0 f)
        [r g b] (cond
                  (= i 0) [1.0 f 0.0]
                  (= i 1) [q 1.0 0.0]
                  (= i 2) [0.0 1.0 f]
                  (= i 3) [0.0 q 1.0]
                  (= i 4) [f 0.0 1.0]
                  :else [1.0 0.0 q])]
    [(int (* 255 r)) (int (* 255 g)) (int (* 255 b)) 255]))

(defn wheel-colour
  "The original's `wheel-pixel` (lines 51-59) at texel `x`, `y`: the angle round
  the middle as a hue."
  [x y]
  (let [c (/ tex-size 2.0)]
    (hsv->colour (Math/toDegrees (Math/atan2 (- y c) (- x c))))))

(defn wheel-texel
  "`wheel-colour` at `x`, `y` already packed, with no vector built: the same
  arithmetic as `hsv->colour` and `wheel-colour`, which stay as the readable
  reference and which a test checks this against on every texel. This is the
  texture's pixel fn, which runs 65536 times when the scene opens."
  [x y]
  (let [c (/ tex-size 2.0)
        h (Math/toDegrees (Math/atan2 (- y c) (- x c)))
        h' (/ (mod h 360.0) 60.0)
        i (int (Math/floor h'))
        f (- h' i)
        q (- 1.0 f)
        r (case i 0 1.0 1 q 2 0.0 3 0.0 4 f 1.0)
        g (case i 0 f 1 1.0 2 1.0 3 q 0.0)
        b (case i 0 0.0 1 0.0 2 f 3 1.0 4 1.0 q)]
    (texel/pack4 (int (* 255 r)) (int (* 255 g)) (int (* 255 b)) 255)))

(defn wheel-spec
  "The texture for `net.b12n.raylib-ios.texture/id!`: 256 by 256, clamped, unfiltered."
  []
  {:w tex-size
   :h tex-size
   :wrap :clamp
   :filter :nearest
   :pixel wheel-texel})

(defn rotate
  "The original's `rotate` (lines 61-64)."
  [x y rad]
  [(- (* x (Math/cos rad)) (* y (Math/sin rad)))
   (+ (* x (Math/sin rad)) (* y (Math/cos rad)))])

(defn fan
  "The original's fan (lines 87-99) for `angle` degrees, as the flat `[x y u v
  ...]` of `net.b12n.raylib-ios.texture/triangles!`: ten triangles of the middle and two
  neighbouring points. The points are scaled by `k` before they are turned, and
  the middle is `cx`, `cy`."
  [angle cx cy k]
  (let [rad (* angle deg2rad)]
    (into []
          (mapcat (fn [i]
                    (let [[p0x p0y] (nth points i)
                          [p1x p1y] (nth points (inc i))
                          [t0u t0v] (nth texcoords i)
                          [t1u t1v] (nth texcoords (inc i))
                          [r0x r0y] (rotate (* k p0x) (* k p0y) rad)
                          [r1x r1y] (rotate (* k p1x) (* k p1y) rad)]
                      [cx cy 0.5 0.5
                       (+ r0x cx) (+ r0y cy) t0u t0v
                       (+ r1x cx) (+ r1y cy) t1u t1v])))
          (range 10))))

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measured: `:w :h`, `:size
  :pad` (the caption's), `:y1` (its top), the middle `:cx :cy` and the scale
  `:k`."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        side (min w h)
        size (max 16 (int (* 0.03 side)))
        pad (max 8 (int (* 0.5 size)))
        y1 (+ top pad)
        free-top (+ y1 size pad)
        free-h (- h free-top)]
    {:w w
     :h h
     :size size
     :pad pad
     :y1 y1
     :cx (/ w 2.0)
     :cy (+ free-top (/ free-h 2.0))
     :k (/ (* radius-fraction (min w free-h)) reach)}))

(defn dimensions
  "`geometry` plus `:lines`, the caption as `{:s :x :y :size}` cut back until it
  fits the width. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w pad size y1]
         :as geo} (geometry metrics)
        room (- w (* 2 pad))
        sz (max 8 (min size (int (/ (* room 100.0) (measure title-line 100)))))]
    (assoc geo :lines [{:s title-line
                        :x pad
                        :y y1
                        :size sz}])))

(defn vertices
  "The fan for `state` on the screen `geo` describes."
  [state {:keys [cx cy k]}]
  (fan (:angle state) cx cy k))

(defn advance
  "One frame: the angle grows by a degree."
  [state {:keys [metrics]}]
  (assoc state
         :angle (+ (:angle state) spin-step)
         :screen (:screen metrics)))

(defn- init [{:keys [metrics]}]
  [{:angle 0.0
    :screen (:screen metrics)}
   [[:scene/init :texpoly]]])

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :texpoly]]])

(defn scene []
  {:id :texpoly
   :title "Polygon Drawing"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
