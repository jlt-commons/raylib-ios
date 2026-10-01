(ns raylib.scenes.huewheel
  "A hue wheel built as an rlgl triangle fan, with a colour on every vertex.
  Ported from raylib-jlt's `rlgl_color_wheel`, which is raylib's
  `shapes_rlgl_color_wheel` example. It is a different example from
  `colorwheel`, which came from `color_wheel` and draws filled sectors: this one
  hands the GPU a few dozen triangles that share the centre and lets it
  interpolate every shade between the rim hues.

  The original takes its controls from the mouse wheel, the arrow keys and
  SPACE. Here a vertical swipe changes the triangle count, a horizontal drag
  sets the centre brightness and a `:tap` toggles fill and wireframe. UP and
  DOWN resized the wheel in the original. That is dropped: the wheel is sized to
  fit the safe region below Back instead, which a phone cannot do by key.

  The count keeps the original's range, 3 to 128, and starts at its 32. The
  mouse wheel stepped it by one a notch. A swipe is a bigger gesture than a
  notch, so it doubles or halves the count, clamped to that range. That is why
  the count can land on 4 or on 3 rather than only on powers of two.

  The brightness drag keeps its own anchor, because the gesture layer only
  reports a swipe at the release. On `:press` the scene records the finger's x
  and the current brightness, and while the finger is down the brightness is the
  anchored one plus `k` times the distance dragged right, clamped to 0..1. `k` is
  one over the width, so a drag across the safe region spans the whole range. The
  same finger reports a `:swipe :left` or `:right` on release, which is IGNORED:
  the brightness already moved while the finger was down, and applying the
  swipe would count the drag twice. Only `:pointer` on `:press` and `:down` is
  read, never the `:release` position.

  A vertical swipe and a horizontal drag come from one finger, so a vertical
  swipe that drifts sideways nudges the brightness. There is no rule that waits
  for the horizontal travel to dominate, because any such gate has to pick a
  value to hold when it closes, and that makes the brightness jump when the
  finger crosses the diagonal. Without it the change is at most `k` times the
  vertical travel, about 8 % of the range for a swipe that just clears the
  threshold at 45 degrees, and a test pins that bound.

  Every wedge is the centre and two rim points, wound by `wound` so the cross
  is negative and survives raylib's back-face culling, as in `rlgltriangle`. A
  culled wedge would leave a gap in the wheel. The wireframe is `draw-line-ex`
  segments: each rim edge and each spoke, in the rim vertex's hue.

  A swipe that starts in `gesture/back-region` and a tap there belong to the
  host and do nothing, and a drag that starts there sets no brightness. Text is
  laid out in `dimensions` below Back, with widths estimated at 0.6 of the size
  per character."
  (:require [raylib.gesture :as gesture]))

(def min-tris "The original's lower bound." 3)
(def max-tris "The original's upper bound." 128)
(def start-tris "The original's starting count." 32)

(defn count-line [n] (str n " triangles"))

(def hint-lines
  "The two control hints, each short enough for a phone held upright."
  ["[SWIPE] triangles  [TAP] wire"
   "[DRAG] centre brightness"])

(defn dimensions
  "The layout for `metrics`' `:screen`: `:w :h`, the wheel's centre `:cx :cy`
  and `:radius`, the line thickness `:thick`, the drag scale `:k` (brightness
  per pixel) and the text. `:lines` lists the three lines, the widest each can
  get, as `{:s :x :y :size}` so a test can check they fit. The first sits below
  `gesture/back-region` and the wheel takes the room under the last."
  [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        ts (max 20 (int (* 0.03 side)))
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h (* 0.5 ts))
        x (int (* 0.04 w))
        row (fn [i s] {:s s
                       :x x
                       :y (int (+ top (* i 1.4 ts)))
                       :size ts})
        lines [(row 0 (first hint-lines))
               (row 1 (second hint-lines))
               (row 2 (count-line max-tris))]
        text-bottom (+ (:y (peek lines)) ts)
        margin (* 0.03 side)
        region-top (+ text-bottom margin)
        region-h (- h margin region-top)]
    {:w w
     :h h
     :cx (* 0.5 w)
     :cy (+ region-top (* 0.5 region-h))
     :radius (max 1.0 (* 0.5 (min (- w (* 2 margin)) region-h)))
     :thick (max 2.0 (* 0.004 side))
     :k (/ 1.0 w)
     :text-size ts
     :lines lines}))

(defn hue->rgb
  "The original's `ColorFromHSV` at saturation and value 1, as `[r g b a]`.
  `deg` is the hue in degrees, wrapped."
  [deg]
  (let [h (/ (mod (double deg) 360.0) 60.0)
        x (int (* 255 (- 1.0 (abs (- (mod h 2.0) 1.0)))))]
    (case (int h)
      0 [255 x 0 255]
      1 [x 255 0 255]
      2 [0 255 x 255]
      3 [0 x 255 255]
      4 [x 0 255 255]
      [255 0 x 255])))

(defn rim
  "`{:pos :color}` of rim vertex `i` of `n`. The index wraps, so vertex `n` is
  vertex 0 exactly, with no float error from a full turn. The angle runs
  clockwise from twelve o'clock, as in the original."
  [{:keys [cx cy radius]} n i]
  (let [f (/ (double (mod i n)) n)
        a (* 2.0 Math/PI f)]
    {:pos [(+ cx (* radius (Math/sin a)))
           (- cy (* radius (Math/cos a)))]
     :color (hue->rgb (* 360.0 f))}))

(defn centre-colour
  "The centre vertex's colour at `brightness`, 0 to 1: a grey."
  [brightness]
  (let [g (int (* 255 brightness))]
    [g g g 255]))

(defn cross
  "Cross product of the first two edges. Negative is the winding that survives
  back-face culling, as in `raylib.scenes.rlgltriangle/cross`."
  [[ax ay] [bx by] [cx cy]]
  (- (* (- bx ax) (- cy ay))
     (* (- by ay) (- cx ax))))

(defn wound
  "The three vertices in the winding that survives culling, each colour carried
  with its vertex."
  [[a b c :as vs]]
  (if (pos? (cross (:pos a) (:pos b) (:pos c)))
    [a c b]
    (vec vs)))

(defn fan
  "The wheel's `n` triangles at `brightness`: each is three `{:pos :color}`
  vertices, the centre and two neighbouring rim points, already wound."
  [{:keys [cx cy]
    :as dims} n brightness]
  (let [centre {:pos [cx cy]
                :color (centre-colour brightness)}]
    (mapv (fn [i] (wound [centre (rim dims n i) (rim dims n (inc i))]))
          (range n))))

(defn wire-segments
  "The wireframe as `[x1 y1 x2 y2 colour]`: for each wedge the rim edge and the
  spoke to the centre, both in the hue of the rim vertex they start from."
  [{:keys [cx cy]
    :as dims} n]
  (into []
        (mapcat (fn [i]
                  (let [{[x0 y0] :pos
                         c :color} (rim dims n i)
                        {[x1 y1] :pos} (rim dims n (inc i))]
                    [[x0 y0 x1 y1 c]
                     [cx cy x0 y0 c]])))
        (range n)))

(defn- clamp [lo hi v] (max lo (min hi v)))

(defn- anchor
  "The drag anchor for this frame: a fresh one on a `:press` outside Back, the
  existing one while the finger stays down, and none otherwise."
  [{:keys [drag brightness]} {:keys [phase position]}]
  (case phase
    :press (when (and position (not (gesture/in-back-region? position)))
             {:x (double (first position))
              :brightness brightness})
    :down drag
    nil))

(defn- swiped-count
  "The triangle count after `event`: doubled by an up swipe, halved by a down
  swipe, clamped to the original's range. Every other event, and any swipe that
  began in Back, leaves it alone."
  [n event]
  (if (and (= :swipe (:type event))
           (not (gesture/in-back-region? (:from event))))
    (case (:dir event)
      :up (clamp min-tris max-tris (* 2 n))
      :down (clamp min-tris max-tris (quot n 2))
      n)
    n))

(defn advance
  "One frame. Calls `gesture/track` once and stores the result on every path. A
  drag sets the brightness while the finger is down, a vertical swipe changes
  the triangle count at the release and a tap outside Back toggles the
  wireframe. A horizontal swipe is never read."
  [state input]
  (let [dims (dimensions (:metrics input))
        [g event] (gesture/track (:gesture state) input)
        drag (anchor state (:pointer input))
        brightness (if (and drag (gesture/down? input))
                     (clamp 0.0 1.0
                            (+ (:brightness drag)
                               (* (:k dims)
                                  (- (double (first (get-in input [:pointer :position])))
                                     (:x drag)))))
                     (:brightness state))
        tapped? (and (= :tap (:type event))
                     (not (gesture/in-back-region? (:at event))))]
    (assoc state
           :gesture g
           :drag drag
           :brightness brightness
           :tris (swiped-count (:tris state) event)
           :lines? (if tapped? (not (:lines? state)) (:lines? state)))))

(defn- init [_]
  [{:tris start-tris
    :brightness 1.0
    :lines? false
    :gesture gesture/idle
    :drag nil}
   [[:scene/init :huewheel]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :huewheel]]])

(defn scene []
  {:id :huewheel
   :title "rlgl Hue Wheel"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
