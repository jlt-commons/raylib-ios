(ns raylib.scenes.yawpitchroll
  "A plane built from boxes, flown with the three aircraft rotations. Ported from
  raylib-jlt's `yaw_pitch_roll` (zlib licence).

  The original looks from (7, 5, 10) at the origin through a 45 degree
  perspective camera. It draws a grid of 12 sunk to y = -3, then a plane of five
  boxes inside rlPushMatrix/rlPopMatrix, turned by `rlRotatef(yaw, 0, 1, 0)`,
  `rlRotatef(pitch, 1, 0, 0)` and `rlRotatef(roll, 0, 0, 1)`, in that order.
  rlgl applies the last call to a vertex first, so a vertex rolls about z, then
  pitches about x, then yaws about y. `plane-transform` is
  `raylib.soft3d/compose` of the three in the same order, and the test pins it
  with a vertex worked out apart from soft3d. The boxes (`raylib.soft3d/cube`'s
  default is raylib-jlt's `cube!`), their sizes, their colours and the grid are
  the original's. The projection is in software, by `raylib.soft3d`.

  The original's keys, and what stands in for each:
  - A and D yaw by +1.1 and -1.1 degrees a frame, W and S pitch by +0.9 and
    -0.9, Q and E roll by -1.3 and +1.3, each held to +-90. Q is tested before E,
    as the original's `cond` tests it, so both together roll -1.3.
  - A relative thumb-stick replaces A, D, W and S. The press point is its
    centre, and a finger that lands in the 3D area starts it. An axis is on when
    the finger is further than `gesture/slop` from the centre along it: left is
    A, right is D, up the glass is W and down is S. Each axis is its own dead
    zone and runs at its own rate, so a diagonal turns both, as two keys
    pressed together do. `raylib.stick` decides whose finger it is: a stick
    starts only on a finger that was not down the frame before, so a finger
    that was already down when the scene opened, or one that began under Back
    or on a button, never does, even when another finger lands. It follows
    only that finger, by touch id (by nearness when the host gives none), and
    ends when it lifts, and no resting finger takes it over. A finger that
    slides from the stick onto a button ends the stick.
  - Two held buttons replace Q and E. They are read from `:touch-points`, so a
    thumb on the stick and a thumb on a button act together. \"roll left\" is
    the original's E (+1.3, the left wing goes down) and \"roll right\" is Q
    (-1.3). They are named by what the plane does, which puts them the other
    way round from the keys' places.
  - Nothing else in the original reads input.

  An axis that is not being driven eases back as the original does, to 0.94 of
  its value a frame, and to 0 under 0.15 degrees. Nothing reads a frame time,
  like the original: every rate is per update.

  The original's gauges are kept: a label, a bar that grows out of the middle
  (full at 45 degrees), a tick at the middle and the angle in degrees, over a
  dark panel, and the line \"let go and each axis eases back to level\". The
  panel sits at the bottom of the screen with the two buttons inside it, so the
  3D area is the field between the title and the panel. The original's
  `%6.1f` is `value-text`, which does not pad. Its three gauges stand side by
  side in the panel, not stacked, so that the 3D area stays tall, and the
  original's `yaw   A / D` labels are just `yaw`, `pitch` and `roll`. All text is
  sized in `dimensions`, and the camera's fovy is the original's while the 3D
  area is as wide as 800x450 and widened by `raylib.soft3d/fit-camera` in a
  narrower one. A face with a corner behind the near plane is dropped whole.

  The state holds `:yaw`, `:pitch` and `:roll` in degrees, `:stick` (the
  centre and the finger, or nil), `:held` (the set of button ids held, for the
  draw), `:n` (the finger count last frame), `:pts` and `:ids` (the touch
  points and touch ids of that frame, for telling a new finger from one
  already down) and `:screen`. Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.soft3d :as s3]
            [raylib.stick :as stick]))

(def yaw-rate "Degrees a frame for A and D. The original's." 1.1)
(def pitch-rate "Degrees a frame for W and S. The original's." 0.9)
(def roll-rate "Degrees a frame for Q and E. The original's." 1.3)
(def angle-limit "The most any angle reaches either way. The original's." 90.0)
(def ease-factor "What an undriven angle keeps each frame. The original's." 0.94)
(def ease-floor "Under this many degrees an undriven angle snaps to 0." 0.15)
(def full-bar "The angle at which a gauge's bar is full. The original's." 45.0)

(def background-colour "The original's (18, 22, 34)." [18 22 34 255])
(def title-colour "RAYWHITE, as raylib defines it." [245 245 245 255])
(def panel-colour "The original's gauge ground." [10 12 20 220])
(def label-colour "GRAY" [130 130 130 255])
(def hint-colour "GRAY" [130 130 130 255])
(def track-colour "The original's (0, 0, 0, 20)." [0 0 0 20])
(def fill-colour "SKYBLUE" [102 191 255 255])
(def tick-colour "DARKGRAY" [80 80 80 255])
(def value-colour "LIGHTGRAY" [200 200 200 255])
(def button-colour [52 58 78 255])
(def button-held-colour "BLUE" [0 121 241 255])
(def button-label-colour [245 245 245 255])

(def title "yaw, pitch and roll")
(def hint "let go and each axis eases back to level")
(def gauge-labels ["yaw" "pitch" "roll"])
(def button-labels
  "Each button's id and the label drawn on it, left to right."
  [[:roll-left "roll left"] [:roll-right "roll right"]])

(def worst-value "The widest a readout gets." "-90.0 deg")

(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

;; --- layout -----------------------------------------------------------------

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measure.

  - `:panel` `[x y w h]`, the dark ground of the gauges and buttons, the full
    width from its top to the bottom;
  - `:buttons`, the two `{:id :rect}` along the bottom, inside the panel;
  - `:viewport` `[x y w h]` and `:aspect`, the 3D area, which is
    `raylib.soft3d/field`'s field cut off at the panel;
  - `:hint-y` and `:gauge-y`, where the hint line and the gauges start, and
    `:size`, `:pad` and `:text-y` from `raylib.soft3d/field`."
  [metrics]
  (let [[w h] (:screen metrics)
        {:keys [size pad]
         [_ fy] :viewport
         :as field} (s3/field metrics)
        side (min w h)
        gap (* 0.02 side)
        bh (* 0.14 side)
        by (- h gap bh)
        bw (/ (- w (* 3 gap)) 2.0)
        panel-y (- by gap (* 2.3 size) (* 1.4 size) pad)
        hint-y (+ panel-y pad)
        vh (- panel-y fy)]
    (assoc field
           :panel [0.0 (double panel-y) (double w) (double (- h panel-y))]
           :buttons (vec (map-indexed (fn [i [id _]]
                                        {:id id
                                         :rect [(+ gap (* i (+ bw gap))) by bw bh]})
                                      button-labels))
           :hint-y (double hint-y)
           :gauge-y (+ hint-y (* 1.4 size))
           :viewport [0.0 (double fy) (double w) (double vh)]
           :aspect (/ (double w) vh))))

(defn dimensions
  "`geometry` plus the text. `:title` and `:hint` are `{:s :x :y :size}`. Each
  of `:gauges` is `{:id :label :value-x :value-y :value-size :track :tick}`: the
  label line, where the readout is drawn (its widest form ends at the column's
  right edge), the bar's track `[x y w h]` and the tick `[x y w h]` at its
  middle. `:buttons` gain `:label`. `:lines` is every text line, with the
  readout at its widest, so a test can check they fit. The title and hint are cut
  back, to 8 at the least, so the wider covers no more than 0.92 of the width;
  the gauges and the buttons' labels are cut back to fit their columns and
  buttons. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [pad text-y hint-y gauge-y]
         [_ _ w _] :panel
         :as geo} (geometry metrics)
        widest (max (measure title 100) (measure hint 100))
        tsize (:size (s3/field metrics widest))
        colgap pad
        colw (/ (- w (* 2 pad) (* 2 colgap)) 3.0)
        wide (+ (apply max (map #(measure % 100) gauge-labels))
                50.0
                (measure worst-value 100))
        gsize (max 8 (min tsize (int (/ (* colw 100.0) wide))))
        bar-h (max 4.0 (double (Math/round (* 0.75 gsize))))
        over (max 2.0 (double (Math/round (* 0.2 gsize))))
        tick-w (max 2.0 (double (Math/round (/ gsize 7.0))))
        bar-y (+ gauge-y (* 1.3 gsize))
        gauges (vec (map-indexed
                     (fn [i s]
                       (let [cx (+ pad (* i (+ colw colgap)))]
                         {:id (nth [:yaw :pitch :roll] i)
                          :label {:s s
                                  :x cx
                                  :y gauge-y
                                  :size gsize}
                          :value-x (- (+ cx colw) (measure worst-value gsize))
                          :value-y gauge-y
                          :value-size gsize
                          :track [cx bar-y colw bar-h]
                          :tick [(- (+ cx (* 0.5 colw)) (* 0.5 tick-w))
                                 (- bar-y over)
                                 tick-w
                                 (+ bar-h (* 2 over))]}))
                     gauge-labels))
        [_ _ bw bh] (:rect (first (:buttons geo)))
        wide-label (apply max (map #(measure (second %) 100) button-labels))
        label-size (max 8 (min (max 16 (int (* 0.034 (min w (second (:screen metrics))))))
                               (int bh)
                               (int (/ (* 0.9 bw 100.0) wide-label))))
        buttons (vec (map (fn [{[bx by] :rect
                                :as b} [_ s]]
                            (assoc b :label {:s s
                                             :x (int (+ bx (* 0.5 (- bw (measure s label-size)))))
                                             :y (int (+ by (* 0.5 (- bh label-size))))
                                             :size label-size}))
                          (:buttons geo) button-labels))
        title-line {:s title
                    :x pad
                    :y text-y
                    :size tsize}
        hint-line {:s hint
                   :x pad
                   :y hint-y
                   :size tsize}]
    (assoc geo
           :title title-line
           :hint hint-line
           :gauges gauges
           :buttons buttons
           :lines (-> [title-line hint-line]
                      (into (map :label gauges))
                      (into (map (fn [{:keys [value-x value-y value-size]}]
                                   {:s worst-value
                                    :x value-x
                                    :y value-y
                                    :size value-size})
                                 gauges))
                      (into (map :label buttons))))))

;; --- the plane ------------------------------------------------------------------

(def plane
  "The original's `draw-plane!`: fuselage, nose, wings, tailplane and fin, each
  `[centre size colour]` plus, for a panel, the shade of the end face that
  `scene-list` leaves out (see `hidden-end`), in the original's order.

  `raylib.soft3d` sorts whole faces by mean depth, which cannot order a box
  that passes through another, so the wing (7 wide) and the tailplane (2.6 wide)
  are cut where they meet the fuselage. Each has two outer panels, from 0.01
  inside the fuselage's side (x = +-0.54) to the original's tips (3.5 and 1.3).
  The inboard part of the wing lay wholly inside the fuselage and is gone. The
  tailplane (z 1.55..2.25) overhangs the fuselage's rear (z 2.2), so a centre
  strip of that overhang, 1.1 wide and 0.05 deep, keeps the silhouette. Colours,
  heights and the rest of the depth are the original's."
  [[[0.0 0.0 0.0] [1.1 0.7 4.4] [200 205 215 255]]
   [[0.0 0.0 -2.6] [0.7 0.5 1.2] [160 165 180 255]]
   [[2.02 0.0 0.2] [2.96 0.22 1.3] [0 121 241 255] 0.7]
   [[-2.02 0.0 0.2] [2.96 0.22 1.3] [0 121 241 255] 0.85]
   [[0.92 0.0 1.9] [0.76 0.18 0.7] [0 82 172 255] 0.7]
   [[-0.92 0.0 1.9] [0.76 0.18 0.7] [0 82 172 255] 0.85]
   [[0.0 0.0 2.225] [1.1 0.18 0.05] [0 82 172 255]]
   [[0.0 0.7 2.0] [0.16 1.3 0.7] [230 41 55 255]]])

(defn plane-transform
  "The plane's transform for `state`: rlRotatef yaw about y, pitch about x and
  roll about z, composed in the order the original calls them. So a vertex
  rolls first and yaws last."
  [{:keys [yaw pitch roll]}]
  (s3/compose (s3/rotate-axis yaw 0.0 1.0 0.0)
              (s3/rotate-axis pitch 1.0 0.0 0.0)
              (s3/rotate-axis roll 0.0 0.0 1.0)))

(defn camera
  "The original's camera, (7, 5, 10) looking at the origin with fovy 45, fitted
  to `dims`' 3D area by `raylib.soft3d/fit-camera`."
  [dims]
  (s3/fit-camera {:position [7.0 5.0 10.0]
                  :target [0.0 0.0 0.0]
                  :up [0.0 1.0 0.0]
                  :fovy 45.0
                  :projection :perspective}
                 original-aspect (:aspect dims)))

(defn grid-list
  "The grid of 12, spacing 1, sunk to y = -3 by the original's
  `rlTranslatef(0, -3, 0)`, as an unfinished draw list. The camera never moves,
  so it depends on the layout alone and a draw can keep it."
  [cam dims]
  (let [vp (s3/view-proj cam (:viewport dims))]
    (s3/grid [] (assoc vp :m (s3/compose (:m vp) (s3/translate 0.0 -3.0 0.0))) 12 1.0)))

(defn- hidden-end
  "`dl` without the triangles of the face of shade `shade` that `cube` just
  added after index `from`. A panel's inboard end lies inside the fuselage
  whichever way the plane turns, so it is never seen, but the painter would
  draw it over the fuselage. `cube` has no way to leave a face out, and this
  drops the face by its colour, `(int (* shade c))` on r, g and b."
  [dl from shade [r g b]]
  (let [c [(int (* shade r)) (int (* shade g)) (int (* shade b))]]
    (reduce (fn [out i]
              (let [t (nth dl i)]
                (if (= c (subvec t 7 10)) out (conj out t))))
            (subvec dl 0 from)
            (range from (count dl)))))

(defn scene-list
  "The finished draw list for `state`: `base` (the `grid-list`) and the plane."
  [base state dims]
  (let [vp (s3/view-proj (camera dims) (:viewport dims))
        xf (plane-transform state)]
    (s3/finish
     (reduce (fn [dl [pos size colour shade]]
               (let [from (count dl)
                     out (s3/cube dl vp xf pos size colour)]
                 (if shade (hidden-end out from shade colour) out)))
             base
             plane))))

;; --- the gauges -------------------------------------------------------------------

(defn value-text
  "The readout for `v` degrees: the original's `%6.1f deg` without the padding.
  A value that rounds to zero has no minus sign."
  [v]
  (let [n (long (Math/round (* 10.0 (double v))))
        a (Math/abs n)]
    (str (if (neg? n) "-" "") (quot a 10) "." (rem a 10) " deg")))

(defn gauge-fill
  "The fill `[x y w h]` of gauge `i` in `dims` for `v` degrees. It grows out of
  the middle of the track, to the right for a positive angle and to the left
  for a negative one, full at 45 degrees, as the original's `half` does."
  [dims i v]
  (let [[tx ty tw th] (:track (nth (:gauges dims) i))
        hw (* 0.5 tw)
        half (int (* hw (max -1.0 (min 1.0 (/ (double v) full-bar)))))]
    [(if (neg? half) (+ tx hw half) (+ tx hw))
     ty
     (Math/abs half)
     th]))

;; --- the frame ----------------------------------------------------------------------

(defn- ease-to-zero
  "The original's `ease-to-zero`."
  [a]
  (if (< (Math/abs (double a)) ease-floor) 0.0 (* a ease-factor)))

(defn- held-buttons
  "The set of button ids with any of `points` inside."
  [{:keys [buttons]} points]
  (into #{}
        (keep (fn [{:keys [id rect]}]
                (when (some (fn [p] (gesture/in-rect? rect p)) points) id)))
        buttons))

(defn- free-point?
  "Whether a finger at `p` can be a stick: not under Back, not on a button."
  [{:keys [buttons]} p]
  (and (not (gesture/in-back-region? p))
       (not-any? (fn [{:keys [rect]}] (gesture/in-rect? rect p)) buttons)))

(defn stick-keys
  "Which of the original's four keys `stick` stands for, as a set of `:a`, `:d`,
  `:w` and `:s`, for `metrics`. An axis is on when the finger is further than
  `gesture/slop` from the centre along it."
  [stick metrics]
  (if stick
    (let [slop (gesture/slop metrics)
          dx (- (double (first (:at stick))) (double (first (:centre stick))))
          dy (- (double (second (:at stick))) (double (second (:centre stick))))]
      (cond-> #{}
        (< dx (- slop)) (conj :a)
        (> dx slop) (conj :d)
        (< dy (- slop)) (conj :w)
        (> dy slop) (conj :s)))
    #{}))

(defn advance
  "One frame, as the original's loop body: each angle is driven by its keys or
  eased back. The keys are the stick's (`stick-keys`) and the buttons'. A
  release frame with fewer than two points lifts everything, and its position
  is never read. A rotation of the phone drops the stick, whose pixels are the
  old screen's."
  [state input]
  (let [metrics (:metrics input)
        dims (geometry metrics)
        screen (:screen metrics)
        state (if (not= screen (:screen state))
                (assoc (dissoc state :stick) :n 0 :pts [] :ids nil)
                state)
        phase (get-in input [:pointer :phase])
        raw (vec (:touch-points input))
        points (if (and (= :release phase) (< (count raw) 2)) [] raw)
        ids (stick/ids-of input points)
        stick (stick/next-stick (:stick state) state
                                {:points points
                                 :ids ids
                                 :metrics metrics
                                 :press? (= :press phase)
                                 :free? #(free-point? dims %)
                                 :start? #(gesture/in-rect? (:viewport dims) %)})
        down (stick-keys stick metrics)
        held (held-buttons dims points)
        {:keys [yaw pitch roll]} state]
    (assoc state
           :screen screen
           :n (count points)
           :pts points
           :ids ids
           :stick stick
           :held held
           :yaw (cond (:a down) (min angle-limit (+ yaw yaw-rate))
                      (:d down) (max (- angle-limit) (- yaw yaw-rate))
                      :else (ease-to-zero yaw))
           :pitch (cond (:w down) (min angle-limit (+ pitch pitch-rate))
                        (:s down) (max (- angle-limit) (- pitch pitch-rate))
                        :else (ease-to-zero pitch))
           :roll (cond (held :roll-right) (max (- angle-limit) (- roll roll-rate))
                       (held :roll-left) (min angle-limit (+ roll roll-rate))
                       :else (ease-to-zero roll)))))

(defn- init [{:keys [metrics]}]
  [{:yaw 0.0
    :pitch 0.0
    :roll 0.0
    :stick nil
    :held #{}
    :n 0
    :pts []
    :ids nil
    :screen (:screen metrics)}
   [[:scene/init :yawpitchroll]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :yawpitchroll]]])

(defn scene []
  {:id :yawpitchroll
   :title "Yaw Pitch Roll"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
