(ns raylib.scenes.srcrec
  "Source and Destination Rects, ported from raylib-jlt's `srcrec_dstrec`
  (net/b12n/raylib_jlt/srcrec_dstrec.clj, EPL 2.0), which is raylib's
  `textures_srcrec_dstrec`: one frame of a sprite sheet drawn the way
  DrawTexturePro draws it. A source rectangle picks the frame, a destination
  rectangle scales it, and an origin offset makes it spin in place.
  The raylib C example it follows is zlib licensed, and this is an altered
  version of that too.

  Mirrored from srcrec_dstrec.clj:
  - The sheet (lines 32-42): six frames of 64 by 64 side by side, 384 by 64.
    Frame `(quot x 64)` takes its ground colour from RED, ORANGE, GOLD, GREEN,
    SKYBLUE, VIOLET in that order, and a disc of radius 64 * 0.32 about the
    frame's centre is RAYWHITE. `sheet-spec` packs it with `raylib.texel/pack`,
    whose byte order is raylib-jlt's `rgba`.
  - The frame (lines 30, 96-100): `FRAME-SHOWN` is 3, so the source rectangle
    is the fourth frame, texcoords 192/384 to 256/384 across and 0 to 1 down.
  - The spin (lines 92, 101, 119): the rotation starts at 0 and grows 1.0 degree a
    frame, clockwise, about the middle of the destination rectangle (the
    original's origin of 64 64 in a 128 by 128 destination).
  - The crosshair (lines 102-111): a GRAY line through the middle each way.
  - The text (lines 112-117): \"srcrec picks the frame, dstrec scales it,
    origin spins it\".

  Deviations. The destination is a square of 0.32 of the screen's shorter side
  (the original's 128 of an 800 by 450 window is 0.28 of its height), so the
  scale is not a whole 2x. The vertical line starts below Back and the text sits
  under it. The first frame drawn has already turned one degree, because the
  gallery runs `update` before `draw`.

  The state holds `:rotation` and `:screen`. Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.texel :as texel]))

(def frame-w "The original's FRAME-W." 64)
(def frame-h "The original's FRAME-H." 64)
(def frames "The original's FRAMES." 6)
(def sheet-w "The original's SHEET-W." (* frame-w frames))
(def frame-shown "The original's FRAME-SHOWN." 3)
(def spin-step "Degrees the rotation grows a frame." 1.0)
(def dst-fraction "The destination square, as a fraction of the shorter side." 0.32)

(def background-colour "RAYWHITE." [245 245 245 255])
(def line-colour "GRAY." [130 130 130 255])
(def text-colour "GRAY." [130 130 130 255])

(def text-line "The original's text." "srcrec picks the frame, dstrec scales it, origin spins it")

(def ^:private ring-colours
  "RED ORANGE GOLD GREEN SKYBLUE VIOLET."
  [[230 41 55 255] [255 161 0 255] [255 203 0 255]
   [0 228 48 255] [102 191 255 255] [135 60 190 255]])

(def ^:private disc-colour "RAYWHITE." [245 245 245 255])

(defn sheet-colour
  "The original's `sheet-pixel` (lines 32-42) at texel `x`, `y`, as `[r g b a]`."
  [x y]
  (let [frame (quot x frame-w)
        lx (- x (* frame frame-w))
        dx (- lx (/ frame-w 2.0))
        dy (- y (/ frame-h 2.0))
        d (Math/sqrt (+ (* dx dx) (* dy dy)))]
    (if (< d (* frame-w 0.32)) disc-colour (nth ring-colours frame))))

(defn sheet-spec
  "The texture for `raylib.texture/id!`: 384 by 64, clamped (it is not a power
  of two, so GLES2 will not repeat it), unfiltered."
  []
  {:w sheet-w
   :h frame-h
   :wrap :clamp
   :filter :nearest
   :pixel (fn [x y] (texel/pack (sheet-colour x y)))})

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measured: `:w :h`, the
  middle `:cx :cy`, `:side` (the destination square), `:size :pad` (the text's),
  and `:vline` and `:hline` as `[x1 y1 x2 y2]`, the vertical one starting below
  Back."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        side (min w h)
        size (max 16 (int (* 0.03 side)))
        pad (max 8 (int (* 0.5 size)))
        cx (/ w 2.0)
        cy (/ h 2.0)]
    {:w w
     :h h
     :cx cx
     :cy cy
     :side (* dst-fraction side)
     :size size
     :pad pad
     :top top
     :vline [(int cx) top (int cx) h]
     :hline [0 (int cy) w (int cy)]}))

(defn dimensions
  "`geometry` plus `:lines`, the text as `{:s :x :y :size}`, cut back until it
  fits the width. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w pad size top]
         :as geo} (geometry metrics)
        room (- w (* 2 pad))
        sz (max 8 (min size (int (/ (* room 100.0) (measure text-line 100)))))]
    (assoc geo :lines [{:s text-line
                        :x pad
                        :y (+ top pad)
                        :size sz}])))

(defn quad
  "The textured quad for `state`, as `raylib.texture/quad!` takes it: the
  original's DrawTexturePro call, with the fourth frame as the source rectangle
  and the middle of the destination as the origin."
  [state {:keys [cx cy side]}]
  {:x cx
   :y cy
   :width side
   :height side
   :origin-x (/ side 2.0)
   :origin-y (/ side 2.0)
   :rotation (:rotation state)
   :u0 (/ (double (* frame-shown frame-w)) sheet-w)
   :v0 0.0
   :u1 (/ (double (* (inc frame-shown) frame-w)) sheet-w)
   :v1 1.0})

(defn advance
  "One frame: the rotation grows a degree."
  [state {:keys [metrics]}]
  (assoc state
         :rotation (+ (:rotation state) spin-step)
         :screen (:screen metrics)))

(defn- init [{:keys [metrics]}]
  [{:rotation 0.0
    :screen (:screen metrics)}
   [[:scene/init :srcrec]]])

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :srcrec]]])

(defn scene []
  {:id :srcrec
   :title "Srcrec Dstrec"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
