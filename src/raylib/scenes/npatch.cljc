(ns raylib.scenes.npatch
  "Npatch Drawing, ported from raylib-jlt's `npatch_drawing`
  (net/b12n/raylib_jlt/npatch_drawing.clj, zlib licence), which is raylib's
  `textures_npatch_drawing`: three panels stretched by the pointer, a nine-patch
  that grows in both axes and two three-patches that grow in one. The corners
  stay the size they were drawn at while the edges and the middle take up the
  slack.

  The original has no npatch helper in its library. `npatch!` (lines 49-83) is
  written out in the example itself, nine quads whose source rectangles carve
  the image into a 3x3, and `cells` here is that arithmetic.

  Mirrored from npatch_drawing.clj:
  - The source (lines 31-47): 64 by 64 (line 28), a border of 16 (line 29),
    corner studs where `edge` is between 5 and 8, and the four bands of colour
    by `edge`, as `patch-colour`. It is clamped and unfiltered (lines 98-99).
  - `cells` (lines 49-83): destination spans `xs`/`ws`/`ys`/`hs` with the middle
    taking what is left over, the source split `us`/`vs`, and a cell with no
    width or height skipped.
  - The panels (lines 116-131): a nine-patch at (380, 150), a horizontal
    three-patch 48 high at (60, 100) with no top or bottom, a vertical one 48
    wide at (60, 150) with no left or right. Their sizes follow the pointer
    (lines 110-113), `px - 380` held to 40..360, `py - 150` to 40..250, `px - 60`
    to 40..280 and `py - 150` to 40..250.
  - The idle pointer (lines 104-109): until it moves, it breathes,
    `420 + 180 sin t` and `260 + 120 sin 1.3t` with `t = frame * 0.02`.
  - The text (lines 132-155 and 160-163) and the source drawn at its own size in
    the bottom-right corner (lines 156-159).

  Deviations. The mouse is a finger: a drag over the screen is the pointer, and
  it stays where the finger left. The panels are laid out on the original's
  800-wide canvas, scaled to the screen and put under the text; the pointer is
  carried into that canvas, so the original's sums hold in its own units. The
  scale is the screen's, so the corners are 16 texels at the source's size times
  that scale. Update runs before draw, so the first drawn frame is frame 1.

  The state holds `:frame`, `:touch` (the last place a finger was, in canvas
  units, or nil) and `:screen`. Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]))

(def src-size "The original's SRC: the source is this many texels square." 64)
(def border "The original's BORDER." 16)
(def canvas-w "The original's window width." 800)
(def canvas-top "The first canvas row that is drawn (the labels sit just above it)." 70)
(def canvas-h "Canvas rows from `canvas-top` to the foot of the source, 430." 360)
(def idle-step "The original's idle time step a frame." 0.02)

(def background-colour "The original's clear, (245, 246, 250)." [245 246 250 255])
(def title-colour "DARKGRAY." [80 80 80 255])
(def hint-colour "GRAY." [130 130 130 255])

(def title-line "The original's title (line 132)." "nine-patch: corners fixed, edges and middle stretch")
(def idle-hint "The original's idle hint, for a finger." "breathing on its own - drag to size them")
(def touch-hint "The original's hint, for a finger." "drag to size them")

(def panels
  "The three panels as `npatch!` takes them, in canvas units. The sizes the
  pointer sets are left out: `sizes` fills them in."
  {:nine {:x 380
          :y 150}
   :horiz {:x 60
           :y 100
           :height 48
           :top 0
           :bottom 0}
   :vert {:x 60
          :y 150
          :width 48
          :left 0
          :right 0}})

(def label-spots
  "Where the original puts its labels (lines 144-155), as the canvas `[x y]` of
  the panel each belongs to. The text sits above the panel, or beside it."
  [["3-patch H" 60 100 :above]
   ["3-patch V" 118 150 :beside]
   ["9-patch" 380 150 :above]])

(def source-at "The source's top-left corner on the canvas (lines 156-159)." [716 366])

(defn patch-colour
  "The original's `patch-pixel` (lines 31-47) at texel `x`, `y`, as `[r g b a]`."
  [x y]
  (let [edge (min x y (- src-size 1 x) (- src-size 1 y))
        left? (< x border)
        right? (>= x (- src-size border))
        top? (< y border)
        bottom? (>= y (- src-size border))
        corner? (and (or left? right?) (or top? bottom?))
        stud? (and corner? (< 4 edge 9))]
    (cond
      stud? [255 220 90 255]
      (< edge 2) [20 28 48 255]
      (< edge 6) [90 150 230 255]
      (< edge 10) [45 80 150 255]
      :else [235 240 250 255])))

(defn patch-spec
  "The texture for `raylib.texture/id!`: 64 by 64, clamped (a stretched edge cell
  samples right up to its border and a repeat would wrap the far side in), and
  unfiltered."
  [pack]
  {:w src-size
   :h src-size
   :wrap :clamp
   :filter :nearest
   :pixel (fn [x y] (pack (patch-colour x y)))})

(defn cells
  "The original's `npatch!` (lines 49-83) as data: the cells to draw for a
  destination `x y width height` and the four borders, as `{:x :y :width :height
  :u0 :u1 :v0 :v1}` in the destination's own units, row by row. A cell with no
  width or no height is a border the patch does not have, and is left out."
  [{:keys [x y width height left right top bottom]
    :or {x 0
         y 0
         width 64
         height 64
         left border
         right border
         top border
         bottom border}}]
  (let [xs [x (+ x left) (+ x (- width right))]
        ws [left (max 0 (- width left right)) right]
        ys [y (+ y top) (+ y (- height bottom))]
        hs [top (max 0 (- height top bottom)) bottom]
        us [0.0 (/ (double left) src-size) (/ (double (- src-size right)) src-size) 1.0]
        vs [0.0 (/ (double top) src-size) (/ (double (- src-size bottom)) src-size) 1.0]]
    (vec (for [row (range 3)
               col (range 3)
               :let [w (nth ws col)
                     h (nth hs row)]
               :when (and (pos? w) (pos? h))]
           {:x (nth xs col)
            :y (nth ys row)
            :width w
            :height h
            :u0 (nth us col)
            :u1 (nth us (inc col))
            :v0 (nth vs row)
            :v1 (nth vs (inc row))}))))

(defn- clamp [v lo hi] (-> v (max lo) (min hi)))

(defn sizes
  "The original's sizes for a pointer at canvas `px`, `py` (lines 110-113), as
  `{:nine-w :nine-h :horiz-w :vert-h}`."
  [px py]
  {:nine-w (clamp (- px 380) 40.0 360.0)
   :nine-h (clamp (- py 150) 40.0 250.0)
   :horiz-w (clamp (- px 60) 40.0 280.0)
   :vert-h (clamp (- py 150) 40.0 250.0)})

(defn pointer
  "The canvas `[px py]` the sizes follow: the last touch, or the idle breathing
  of frame `frame` (lines 104-109)."
  [{:keys [frame touch]}]
  (or touch
      (let [t (* frame idle-step)]
        [(+ 420 (* 180 (Math/sin t)))
         (+ 260 (* 120 (Math/sin (* 1.3 t))))])))

(defn panel-cells
  "The cells of the three panels for `state`, in canvas units."
  [state]
  (let [[px py] (pointer state)
        {:keys [nine-w nine-h horiz-w vert-h]} (sizes px py)]
    (into []
          (mapcat cells)
          [(assoc (:nine panels) :width nine-w :height nine-h)
           (assoc (:horiz panels) :width horiz-w)
           (assoc (:vert panels) :height vert-h)])))

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measured: `:w :h`, `:size
  :pad :row` (the text's), `:y1`, `:y2` (the title's and hint's tops), and the
  canvas's `:scale`, `:ox` and `:oy`, where canvas `(x, y)` lands at `(ox + scale
  x, oy + scale (y - canvas-top))`."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        side (min w h)
        size (max 16 (int (* 0.03 side)))
        pad (max 8 (int (* 0.5 size)))
        row (int (* 1.3 size))
        y1 (+ top pad)
        y2 (+ y1 row)
        oy (+ y2 size pad)
        scale (min (/ w (double canvas-w))
                   (/ (- h oy pad size 4) (double canvas-h)))]
    {:w w
     :h h
     :size size
     :pad pad
     :row row
     :y1 y1
     :y2 y2
     :scale scale
     :ox (/ (- w (* canvas-w scale)) 2.0)
     :oy (double oy)}))

(defn to-canvas
  "The canvas point under screen point `[x y]`."
  [{:keys [scale ox oy]} [x y]]
  [(/ (- x ox) scale)
   (+ canvas-top (/ (- y oy) scale))])

(defn- to-screen-x [{:keys [scale ox]} x] (+ ox (* scale x)))
(defn- to-screen-y [{:keys [scale oy]} y] (+ oy (* scale (- y canvas-top))))

(defn dimensions
  "`geometry` plus `:lines`, the title and the hint as `{:s :x :y :size}` at one
  size cut back until both fit the width, and `:labels`, the three panel labels
  and the source's, kept inside the screen. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w pad size y1 y2]
         :as geo} (geometry metrics)
        room (- w (* 2 pad))
        fit (fn [s] (max 8 (min size (int (/ (* room 100.0) (measure s 100))))))
        sz (min (fit title-line) (fit idle-hint))
        place (fn [s x y]
                {:s s
                 :x (int (max pad (min (- w pad (measure s size)) x)))
                 :y (int y)
                 :size size})
        [sx sy] source-at
        panel-label (fn [[s x y where]]
                      (place s
                             (to-screen-x geo x)
                             (if (= where :above)
                               (- (to-screen-y geo y) size 2)
                               (to-screen-y geo y))))]
    (assoc geo
           :lines [{:s title-line
                    :x pad
                    :y y1
                    :size sz}
                   {:s idle-hint
                    :x pad
                    :y y2
                    :size sz}]
           :labels (conj (mapv panel-label label-spots)
                         (place "source"
                                (to-screen-x geo sx)
                                (+ (to-screen-y geo (+ sy src-size)) 2))))))

(defn hint [state] (if (:touch state) touch-hint idle-hint))

(defn- scale-cell [geo {:keys [x y width height]
                        :as cell}]
  (let [s (:scale geo)]
    (assoc cell
           :x (to-screen-x geo x)
           :y (to-screen-y geo y)
           :width (* s width)
           :height (* s height))))

(defn quads
  "The nine-patch cells of all three panels for `state`, in screen pixels, as
  `raylib.texture/quad!` takes them."
  [state geo]
  (mapv #(scale-cell geo %) (panel-cells state)))

(defn source-quad
  "The source drawn at its own size, scaled, in the bottom-right corner."
  [geo]
  (scale-cell geo {:x (first source-at)
                   :y (second source-at)
                   :width src-size
                   :height src-size
                   :u0 0.0
                   :v0 0.0
                   :u1 1.0
                   :v1 1.0}))

(defn advance
  "One frame: the frame count moves on, and a finger on the glass becomes the
  pointer, carried into the canvas."
  [state {:keys [metrics]
          :as input}]
  (let [geo (geometry metrics)
        at (when (gesture/down? input) (:position (:pointer input)))]
    (assoc state
           :frame (inc (:frame state))
           :touch (if at (to-canvas geo at) (:touch state))
           :screen (:screen metrics))))

(defn- init [{:keys [metrics]}]
  [{:frame 0
    :touch nil
    :screen (:screen metrics)}
   [[:scene/init :npatch]]])

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :npatch]]])

(defn scene []
  {:id :npatch
   :title "Npatch Drawing"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
