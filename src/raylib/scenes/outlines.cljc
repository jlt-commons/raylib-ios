(ns raylib.scenes.outlines
  "Three outlines driven by one thickness: a rectangle, a rounded rectangle and a
  ring, so the same setting can be compared across them. Ported from
  raylib-jlt's `outlines_thickness`, itself raylib's
  `shapes_outlines_thickness` minus its raygui slider. It differs from
  `rounded`, which animates a filled shape's corner radius, and from `ring`,
  which animates an annulus's sweep: here the radius is fixed and the subject is
  the stroke width, including below zero.

  The thickness goes negative, and the original's docstring says what that does.
  raylib guards the plain rectangle with `thick > 0`, so a negative thickness
  draws nothing there. The rounded one collapses to a hairline, with no outward
  band. Only the ring is built from two radii rather than a thickness, so only
  the ring grows outward, and it does. This port reproduces each: `rect-lines`
  returns nothing at or below zero, `rounded-outline` falls back to `hairline`
  and `circle-ring` swaps its radii. One difference is a choice. raylib's
  hairline is whatever its line width is, and here it is one pixel.

  The range is the original's -30 to 30 and the start is its 5. Units are the
  original's pixels at 800 by 450, and `k` scales them, so a shape is 220 units
  wide and a thickness of 30 is the same fraction of it as on the desktop. The
  original sweeps the value on its own until UP or DOWN is pressed. That is kept,
  frame-locked as there: the thickness follows 30 sin(0.02 frame) until the first
  touch outside Back, which then takes over and holds the value it found.

  A vertical drag replaces UP and DOWN. On `:press` the scene records the
  finger's y and the current thickness, and while the finger is down the
  thickness is the anchored one plus `per-px` times the distance dragged up,
  clamped to the range. `per-px` is the range over half the height, so a drag
  across half the screen spans it. The release carries a `:swipe` that is
  IGNORED, because the thickness already moved while the finger was down.
  `raylib.gesture/track` is therefore not called: nothing here consumes a tap or
  a swipe. A tap still moves the value by at most `per-px` times the slop, which
  a test pins. Only `:pointer` on `:press` and `:down` is read, never the
  `:release` position, and a press in `gesture/back-region` belongs to the host.

  Bands grow inward from the shape's own edge, as raylib's do, so a rectangle's
  outline stays inside its rectangle. Each straight side spans the whole side,
  which leaves nothing for a corner to notch: the corner square is covered by
  both lines that meet there. The ring's inner radius is the radius less the
  thickness, so a positive thickness is inward. The rounded outline is four
  straight lines plus a quarter ring at each corner, and the corners come from
  `raylib.scenes.rounded/parts`.

  Shapes sit in three cells, side by side when the screen is wider than tall and
  stacked otherwise, each cell holding a square 280 units across so the ring at
  its most outward still fits. Text sits below Back in `dimensions`, with widths
  estimated at 0.6 of the size per character in the tests."
  (:require [raylib.gesture :as gesture]
            [raylib.scenes.rounded :as rounded]))

(def min-thick "The original's lower bound, in units." -30.0)
(def max-thick "The original's upper bound, in units." 30.0)
(def start-thick "The original's starting value." 5.0)
(def hairline "What a collapsed outline is, in pixels." 1.0)
(def roundness "The original's ROUNDNESS." 0.2)
(def corner-segments "The original's SEGMENTS, per corner." 9)
(def ring-segments "The original's 64 for the ring." 64)
(def sweep-rate "The original's per-frame step of the sweep's phase." 0.02)

(def background-colour "The original's RAYWHITE." [245 245 245 255])
(def fill-colour "The original's LIGHTGRAY." [200 200 200 255])
(def outline-colour "The original's BLUE." [0 121 241 255])
(def label-colour "The original's BLACK." [0 0 0 255])
(def thickness-colour "The original's MAROON." [190 33 55 255])
(def hint-colour "The original's GRAY." [130 130 130 255])

(def labels
  "What each shape is, named by what it draws here rather than by the raylib
  call the original used."
  ["rect outline" "rounded outline" "ring"])

(def notes
  "The original's closing remark, in two lines that fit a narrow phone."
  ["below 0 the rect outline is gone"
   "rounded: hairline; ring grows out"])

(defn hint-line
  "The control hint: the sweep invites a touch and a touch is told what it does."
  [manual?]
  (if manual?
    "drag up or down to change it"
    "sweeping: drag to take over"))

(defn- tenths
  "`v` to one decimal place as a string. There is no `format` in a `.cljc`."
  [v]
  (let [n (long (Math/floor (+ (* 10.0 (abs (double v))) 0.5)))]
    (str (if (and (neg? v) (pos? n)) "-" "") (quot n 10) "." (rem n 10))))

(defn thickness-line
  "The readout. Only the ring grows outward below zero, and the line says so."
  [t]
  (str "thickness " (tenths t)
       (if (neg? t) " (ring grows outward)" " (grows inward)")))

(defn dimensions
  "The layout for `metrics`' `:screen`: `:w :h`, the unit scale `:k` (pixels per
  original pixel), `:per-px` (thickness per pixel of vertical drag), the four
  text `:rows` as `{:x :y :size}`, the three shapes `:rect` and `:rounded` as
  `[x y w h]` and `:ring` as `[cx cy radius]`, and `:labels` as `{:s :x :y
  :size}`. The first row sits below `gesture/back-region`, and the cells take the
  room under the last."
  [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        ts (max 20 (int (* 0.03 side)))
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h (* 0.5 ts))
        x (int (* 0.04 w))
        rows (mapv (fn [i] {:x x
                            :y (int (+ top (* i 1.4 ts)))
                            :size ts})
                   (range 4))
        text-bottom (+ (:y (peek rows)) ts)
        margin (* 0.03 side)
        region-top (+ text-bottom margin)
        region-h (- h margin region-top)
        region-w (- w (* 2 margin))
        wide? (>= w h)
        band (* 1.6 ts)
        cw (if wide? (/ region-w 3.0) region-w)
        ch (if wide? region-h (/ region-h 3.0))
        k (/ (min cw (- ch band)) 280.0)
        cells (mapv (fn [i]
                      (let [ox (if wide? (+ margin (* i cw)) margin)
                            oy (if wide? region-top (+ region-top (* i ch)))]
                        [(+ ox (* 0.5 cw))
                         (+ oy band (* 0.5 (- ch band)))
                         oy]))
                    (range 3))
        [[c0x c0y _] [c1x c1y _] [c2x c2y _]] cells
        u (fn [n] (* n k))]
    {:w w
     :h h
     :k k
     :per-px (/ (- max-thick min-thick) (* 0.5 h))
     :text-size ts
     :rows rows
     :rect [(- c0x (u 110)) (- c0y (u 110)) (u 220) (u 220)]
     :rounded [(- c1x (u 110)) (- c1y (u 110)) (u 220) (u 220)]
     :ring [c2x c2y (u 110)]
     :labels (mapv (fn [s [cx _ oy]]
                     {:s s
                      :x (max x (- cx (u 110)))
                      :y (+ oy (* 0.1 ts))
                      :size ts})
                   labels cells)}))

(defn rect-lines
  "The plain rectangle's outline as four `[x1 y1 x2 y2 thick]` lines, or nothing
  when `t` is zero or less: raylib's `thick > 0` guard. The band grows inward,
  each line centred half a thickness inside its edge and spanning the whole side,
  so the four corner squares are covered twice and never notched. `t` is capped
  at half the shorter side, where the bands meet."
  [[x y w h] t]
  (if-not (pos? t)
    []
    (let [t (min (double t) (* 0.5 (min w h)))
          o (* 0.5 t)
          x1 (+ x w)
          y1 (+ y h)]
      [[x (+ y o) x1 (+ y o) t]
       [(- x1 o) y (- x1 o) y1 t]
       [x1 (- y1 o) x (- y1 o) t]
       [(+ x o) y1 (+ x o) y t]])))

(defn rounded-radius
  "The corner radius of the rounded rectangle `[x y w h]`: raylib's roundness
  times half the shorter side."
  [[_ _ w h]]
  (* roundness 0.5 (min w h)))

(defn rounded-outline
  "The rounded rectangle's outline: `:lines` are the four straight sides and
  `:rings` are the four corners as `[cx cy inner outer start-deg end-deg]`. At
  `t` zero or less both collapse to a `hairline` rather than vanishing or going
  outward, as raylib's do. The corners are the rounded scene's own, and the inner
  radius is clamped at zero once the thickness passes the corner radius."
  [[x y w h :as rect] t]
  (let [t (if (pos? t) (double t) hairline)
        r (rounded-radius rect)
        o (* 0.5 t)
        x1 (+ x w)
        y1 (+ y h)
        {:keys [corners]} (rounded/parts {:x x
                                          :y y
                                          :rect-w w
                                          :rect-h h}
                                         r)]
    {:lines [[(+ x r) (+ y o) (- x1 r) (+ y o) t]
             [(- x1 o) (+ y r) (- x1 o) (- y1 r) t]
             [(- x1 r) (- y1 o) (+ x r) (- y1 o) t]
             [(+ x o) (- y1 r) (+ x o) (+ y r) t]]
     :rings (mapv (fn [[cx cy a0 a1]] [cx cy (max 0.0 (- r t)) r a0 a1]) corners)}))

(defn circle-ring
  "The circle's outline as `[inner outer]` radii, or nil when they are equal.
  The thickness is subtracted from the radius, so a positive one is inward and a
  negative one grows outward, as in the original."
  [_cx _cy radius t]
  (let [a (- radius t)
        inner (min radius a)
        outer (max radius a)]
    (when (> outer inner) [inner outer])))

(defn- clamp [lo hi v] (max lo (min hi v)))

(defn sweep
  "The thickness the untouched scene shows at `frame`: the original's
  `30 sin(0.02 frame)`, frame-locked as there."
  [frame]
  (* max-thick (Math/sin (* frame sweep-rate))))

(defn- anchor
  "The drag anchor for this frame: a fresh one on a `:press` outside Back, the
  existing one while the finger stays down, and none otherwise."
  [{:keys [drag thick]} {:keys [phase position]}]
  (case phase
    :press (when (and position (not (gesture/in-back-region? position)))
             {:y (double (second position))
              :thick thick})
    :down drag
    nil))

(defn advance
  "One frame. A press outside Back takes over from the sweep, a drag sets the
  thickness while the finger is down, and a release leaves it where it was."
  [state input]
  (let [dims (dimensions (:metrics input))
        drag (anchor state (:pointer input))
        manual? (boolean (or (:manual? state) drag))
        thick (cond
                (and drag (gesture/down? input))
                (clamp min-thick max-thick
                       (+ (:thick drag)
                          (* (:per-px dims)
                             (- (:y drag)
                                (double (second (get-in input [:pointer :position])))))))
                manual? (:thick state)
                :else (sweep (:frame state)))]
    (assoc state
           :frame (inc (:frame state))
           :drag drag
           :manual? manual?
           :thick thick)))

(defn- init [_]
  [{:thick start-thick
    :manual? false
    :frame 0
    :drag nil}
   [[:scene/init :outlines]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :outlines]]])

(defn scene []
  {:id :outlines
   :title "Outline Thickness"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
