(ns raylib.scenes.texcurve
  "Textured Curve, ported from raylib-jlt's `textured_curve`
  (net/b12n/raylib_jlt/textured_curve.clj, zlib licence), which is raylib's
  `textures_textured_curve`: a road texture laid along a cubic Bezier as a strip
  of quads, each square across the curve at its point.

  Mirrored from textured_curve.clj:
  - The road (lines 45-62): 64 by 128 (lines 37-38), asphalt (58, 58, 64), a
    solid line down each edge (`ImageDrawRectangle` at x 4 and x 55, 5 wide) and
    four dashes of 16 on, 16 off at x 29 (6 wide). `road-grid` replays those
    calls through `raylib.texel`. The wrap is REPEAT with LINEAR filtering; both
    sides are powers of two, as GLES2 needs.
  - The ribbon (lines 65-112): `bezier-at`, then per segment the normal of the
    delta with components swapped and one negated, the previous segment's
    normal at the start edge (the first reuses its own), and v accumulating by
    arc length as `len / (128 * 2)`. `ribbon` is that loop, with each quad as
    the two triangles rlgl splits it into.
  - The limits (lines 40-43, 128-133): width 6 to 80, moving 0.8 a frame while
    held, from 40; segments 3 to 48, one a press, from 24.
  - The flex (lines 134-141): the end points are (80, 360) and (720, 110), the
    middle two swing on `t = frame * 0.015`.
  - The text (lines 148-160): the status line \"width 40.0   segments 24\".

  Deviations. The original is an 800 by 450 window and the curve is built in
  those coordinates, then scaled uniformly to fit the free area under the
  buttons and centred, so the road keeps its shape and its texel aspect on any
  screen. The width is in the original's units. LEFT, RIGHT, UP and DOWN are four
  buttons, WIDTH -, WIDTH +, SEG - and SEG +, the width ones repeating while a
  finger is held and the segment ones a press each. The window-title text is
  dropped, since the gallery shows the title. The first frame drawn is frame 1,
  because the gallery runs `update` before `draw`.

  The state holds `:frame`, `:width`, `:segments`, `:held` and `:screen`. Colours
  are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.texel :as texel]))

(def win-w "The original's W: the space the curve is built in." 800)
(def win-h "The original's H." 450)
(def road-w "The original's ROAD-W." 64)
(def road-h "The original's ROAD-H." 128)
(def min-segments "The original's MIN-SEGMENTS." 3)
(def max-segments "The original's MAX-SEGMENTS." 48)
(def min-width "The original's MIN-WIDTH." 6.0)
(def max-width "The original's MAX-WIDTH." 80.0)
(def start-width "Where the width starts (line 122)." 40.0)
(def start-segments "Where the segment count starts (line 122)." 24)
(def width-step "How far the width moves a frame while held (lines 128-129)." 0.8)
(def flex-step "How far `t` moves a frame (line 134)." 0.015)

(def background-colour "The original's ground." [30 110 60 255])
(def text-colour "RAYWHITE." [245 245 245 255])
(def tint "WHITE." [255 255 255 255])
(def button-colour "A button at rest." [200 200 200 255])
(def button-held-colour "A button under a finger." [130 130 130 255])
(def button-label-colour "DARKGRAY." [80 80 80 255])

(def hint-line "The original's hint (line 156), with buttons for the keys." "Hold WIDTH, tap SEG")
(def button-keys [:width- :width+ :seg- :seg+])
(def labels {:width- "WIDTH -"
             :width+ "WIDTH +"
             :seg- "SEG -"
             :seg+ "SEG +"})

;; ---------------------------------------------------------------- the road

(defn road-grid
  "The original's `build-road!` (lines 45-62) as a `raylib.texel` grid: 64 by
  128, asphalt, two edge lines and four dashes."
  []
  (let [white [235 235 235 255]
        yellow [240 200 60 255]]
    (as-> (texel/grid road-w road-h [58 58 64 255]) g
      (texel/draw-rect g 4 0 5 road-h white)
      (texel/draw-rect g (- road-w 9) 0 5 road-h white)
      (reduce (fn [g i] (texel/draw-rect g (- (quot road-w 2) 3) (* i 32) 6 16 yellow))
              g
              (range 4)))))

(defn road-spec
  "The texture for `raylib.texture/id!`: the road, repeating, linear."
  []
  {:w road-w
   :h road-h
   :wrap :repeat
   :filter :linear
   :pixel (texel/pixel-of (road-grid))})

;; ---------------------------------------------------------------- the curve

(defn flex-points
  "The original's four control points (lines 135-141) at `frame`, as `[x y]`
  in its 800 by 450 coordinates. The end points are fixed, the middle two swing."
  [frame]
  (let [t (* frame flex-step)]
    [[80.0 360.0]
     [(+ 220.0 (* 120.0 (Math/sin t))) (+ 120.0 (* 60.0 (Math/cos (* t 1.3))))]
     [(+ 560.0 (* 120.0 (Math/sin (* t 0.8)))) (+ 330.0 (* 70.0 (Math/sin (* t 1.1))))]
     [720.0 110.0]]))

(defn control-points
  "The four points for `state`: the original's motion at its frame, nothing else."
  [{:keys [frame]}]
  (flex-points frame))

(defn bezier-at
  "The original's `bezier-at` (lines 65-74): the cubic Bezier point at `t`."
  [[p0x p0y] [p1x p1y] [p2x p2y] [p3x p3y] t]
  (let [u (- 1.0 t)
        a (* u u u)
        b (* 3.0 u u t)
        c (* 3.0 u t t)
        d (* t t t)]
    [(+ (* a p0x) (* b p1x) (* c p2x) (* d p3x))
     (+ (* a p0y) (* b p1y) (* c p2y) (* d p3y))]))

(defn ribbon
  "The original's `draw-curve!` loop (lines 76-112) for the four points, `segments`
  and `width`, as the flat `[x y u v ...]` of `raylib.texture/triangles!`: per
  segment one quad A B C D (the previous point's edge, then the new point's) as
  the two triangles A B C and A C D. Positions are `(x * k + ox, y * k + oy)`, so
  the width scales with them; the texcoords do not."
  [[p0 p1 p2 p3] segments width ox oy k]
  (let [sx (fn [x] (+ ox (* k x)))
        sy (fn [y] (+ oy (* k y)))]
    (loop [i 1
           [px py] p0
           pn nil
           prev-v 0.0
           out (transient [])]
      (if (> i segments)
        (persistent! out)
        (let [t (/ (double i) segments)
              [cx cy] (bezier-at p0 p1 p2 p3 t)
              dx (- cx px)
              dy (- cy py)
              len (Math/sqrt (+ (* dx dx) (* dy dy)))
              nx (if (zero? len) 0.0 (/ (- dy) len))
              ny (if (zero? len) 0.0 (/ dx len))
              [ppx ppy] (or pn [nx ny])
              v (+ prev-v (/ len (* road-h 2.0)))
              ax (sx (- px (* ppx width)))
              ay (sy (- py (* ppy width)))
              bx (sx (+ px (* ppx width)))
              by (sy (+ py (* ppy width)))
              qx (sx (+ cx (* nx width)))
              qy (sy (+ cy (* ny width)))
              dxx (sx (- cx (* nx width)))
              dyy (sy (- cy (* ny width)))]
          (recur (inc i)
                 [cx cy]
                 [nx ny]
                 v
                 (-> out
                     (conj! ax) (conj! ay) (conj! 0.0) (conj! prev-v)
                     (conj! bx) (conj! by) (conj! 1.0) (conj! prev-v)
                     (conj! qx) (conj! qy) (conj! 1.0) (conj! v)
                     (conj! ax) (conj! ay) (conj! 0.0) (conj! prev-v)
                     (conj! qx) (conj! qy) (conj! 1.0) (conj! v)
                     (conj! dxx) (conj! dyy) (conj! 0.0) (conj! v))))))))

;; ----------------------------------------------------------------- the layout

(defn status-line
  "The original's status text (lines 148-149), `%.1f` of the width done by hand
  since `format` is not shared by both hosts."
  [width segments]
  (let [tenths (long (Math/round (* 10.0 width)))]
    (str "width " (quot tenths 10) "." (rem tenths 10) "   segments " segments)))

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measured: `:w :h`, `:size
  :pad :row` (the text's), `:status-y` and `:hint-y`, `:buttons` (a map of
  `[x y w h]`)
  and the curve's place, `:ox :oy :k`, which maps the original's coordinates to
  the screen."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (double (+ back-y back-h))
        side (min w h)
        size (max 16 (int (* 0.03 side)))
        pad (max 8 (int (* 0.5 size)))
        row (int (* 1.3 size))
        status-y (+ top pad)
        hint-y (+ status-y row)
        by (+ hint-y row pad)
        bh (* 3 size)
        bw (/ (- w (* 5.0 pad)) 4)
        area-y (+ by bh pad)
        area-h (- h area-y pad)
        k (max 0.0 (min (/ w (double win-w)) (/ area-h (double win-h))))]
    {:w w
     :h h
     :size size
     :pad pad
     :row row
     :status-y status-y
     :hint-y hint-y
     :buttons (into {}
                    (map-indexed (fn [i b] [b [(+ pad (* i (+ bw pad))) by bw (double bh)]]))
                    button-keys)
     :ox (/ (- w (* k win-w)) 2.0)
     :oy (+ area-y (/ (- area-h (* k win-h)) 2.0))
     :k k}))

(defn dimensions
  "`geometry` plus `:lines`, the status line (at its widest) and the hint as
  `{:s :x :y :size}`, at one size cut back until both fit the width, and
  `:labels`, each button's label placed in it. `measure` is `(fn [s size] ->
  px)`."
  [metrics measure]
  (let [{:keys [w pad size status-y hint-y buttons]
         :as geo} (geometry metrics)
        room (- w (* 2 pad))
        fit (fn [s] (max 8 (min size (int (/ (* room 100.0) (measure s 100))))))
        widest (status-line max-width max-segments)
        sz (min (fit widest) (fit hint-line))
        place (fn [[bx by bw bh] s]
                (let [lsz (max 8 (min size (int (/ (* (- bw 4.0) 100.0) (measure s 100)))))]
                  {:s s
                   :x (int (+ bx (* 0.5 (- bw (measure s lsz)))))
                   :y (int (+ by (* 0.5 (- bh lsz))))
                   :size lsz}))]
    (assoc geo
           :lines [{:s widest
                    :x pad
                    :y (int status-y)
                    :size sz}
                   {:s hint-line
                    :x pad
                    :y (int hint-y)
                    :size sz}]
           :labels (into {} (map (fn [b] [b (place (get buttons b) (get labels b))])) button-keys))))

(defn vertices
  "The ribbon for `state` on the screen `geo` describes."
  [state {:keys [ox oy k]}]
  (ribbon (control-points state) (:segments state) (:width state) ox oy k))

(defn held-button
  "`:width-`, `:width+`, `:seg-`, `:seg+` or nil: the button a finger is on."
  [geo input]
  (when (gesture/down? input)
    (let [at (:position (:pointer input))]
      (some (fn [b] (when (gesture/in-rect? (get (:buttons geo) b) at) b)) button-keys))))

(defn advance
  "One frame: the width moves while a width button is held and a segment
  button's press steps the count. The control points move only as the original's
  do, with the frame."
  [state {:keys [metrics pointer]
          :as input}]
  (let [held (held-button (geometry metrics) input)
        pressed (when (= :press (:phase pointer)) held)
        width (case held
                :width+ (min max-width (+ (:width state) width-step))
                :width- (max min-width (- (:width state) width-step))
                (:width state))
        segments (case pressed
                   :seg+ (min max-segments (inc (:segments state)))
                   :seg- (max min-segments (dec (:segments state)))
                   (:segments state))]
    (assoc state
           :frame (inc (:frame state))
           :width width
           :segments segments
           :held held
           :screen (:screen metrics))))

(defn- init [{:keys [metrics]}]
  [{:frame 0
    :width start-width
    :segments start-segments
    :held nil
    :screen (:screen metrics)}
   [[:scene/init :texcurve]]])

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :texcurve]]])

(defn scene []
  {:id :texcurve
   :title "Textured Curve"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
