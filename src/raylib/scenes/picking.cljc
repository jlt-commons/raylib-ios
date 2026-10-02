(ns raylib.scenes.picking
  "Tap a cube to pick it with a ray. Ported from raylib-jlt's `picking_3d`,
  which is raylib's `core_3d_picking` example (zlib licence).

  The original looks at the point (0, 1, 0) through a 45 degree perspective
  camera that circles it at a height of 10 and a radius of 14. It draws a
  `DrawCube` of side 2 at (0, 1, 0) in GRAY with DARKGRAY wires, a grid of 10,
  and, once something is picked, the ray with `DrawRay`. A click casts
  `GetScreenToWorldRay` through the cursor and tests it with
  `GetRayCollisionBox`. A hit turns the cube RED with MAROON wires, adds a
  second set of GREEN wires 0.2 larger and shows \"BOX SELECTED\". A click
  while it is selected lets go instead, without casting, and the ray stays
  drawn. The projection is in software, by `raylib.soft3d`, whose `screen->ray`
  and `ray-box` are those two raylib calls.

  The original's controls, and what stands in for each:
  - A click picks. A tap does, through the same `view` that draws the frame, so
    the ray starts at the camera that was drawn. It picks at the tap's PRESS
    position (`raylib.gesture/track`'s `:at`), never the release. A tap that
    ends a multi-finger touch picks nothing, and neither does a drag, because
    the gesture's slop is what separates them.
  - The camera circles by itself, 0.005 radians an update as the original's
    `orbit-frame` does. It stands still from a press until the finger is up,
    so a tap's ray is cast through the camera the finger saw.
  - Right click captured the pointer and gave the camera to the mouse and W, A,
    S and D. A one-finger drag stands for the mouse-look: it turns the camera
    round the cube, 0.004 radians a pixel (the original's `SENS`) times `800 /
    field width`, so the glass is as far to cross as the original's window.
    Dragging right turns the scene right. Dragging down raises the camera by
    the same distance in world units a pixel as the angle would sweep at the
    radius, held between `min-height` and `max-height`. The orbit keeps the
    radius and aims at (0, 1, 0), so the cube stays in view. The drag starts
    only once the finger is further than `gesture/slop` from its press, so a
    tap does not nudge the camera between press and release. It starts only on
    its own fresh press inside the field, a second finger ends it, and the
    finger that stays never takes it back.
  - Dropped: the cursor lock, its right-click toggle, the hint that names it,
    and W, A, S and D, which walked the free camera. There is no free camera
    here, only the orbit.

  The readout keeps the original's \"BOX SELECTED\" and adds the hit's distance,
  point and normal from `ray-box`, in the top left of the field. A miss says
  \"missed\". The text is in `dimensions`. The hint takes the original's top line
  to the caption row below Back and the bottom hint is dropped with the lock.

  The ray is drawn with `raylib.soft3d/lines` from the camera, 10000 units
  along its direction as `DrawRay` does, clipped at the near plane. From the
  camera that cast it, it is seen end-on: a single point at the tapped pixel,
  which is correct and also invisible until the camera moves on. The lines are
  drawn after every face and there is no depth buffer, so `ray-pieces` cuts the
  stretch inside the cube out of the ray, split at the ray's entry and exit.
  What is left of the ray behind the cube can still show through it from some
  angles.

  A face with a corner behind the near plane is dropped whole. The original's
  fovy is kept while the field is as wide as 800x450 and widened by
  `raylib.soft3d/fit-camera` in a narrower one. The wires hide the edges of
  their own cube that face away (`{:hide-back? true}`), the larger green set as
  well.

  The state holds `:angle` and `:height` (the camera), `:ray`, `:pick` (the
  `ray-box` result, nil when nothing is shown), `:hit?` (selected), `:orbit`
  (the drag's `:start`, `:at` and `:dragging?`, or nil), `:gesture`, `:n` (the
  finger count last frame) and `:screen`. Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.soft3d :as s3]))

(def sensitivity "Radians a pixel of drag. The original's SENS." 0.004)
(def orbit-radius "The camera's distance from the y axis. The original's." 14.0)
(def orbit-height "The camera's starting height. The original's." 10.0)
(def orbit-speed "Radians an update of the idle orbit. The original's." 0.005)
(def min-height "The lowest the camera is dragged." 2.0)
(def max-height "The highest the camera is dragged." 25.0)
(def original-width "The original's window width, in pixels." 800.0)
(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))
(def ray-length "DrawRay's scale." 10000.0)

(def cube-centre "The original's CUBE-Y." [0.0 1.0 0.0])
(def cube-size "The original's CUBE-SIZE." 2.0)
(def cube-lo [-1.0 0.0 -1.0])
(def cube-hi [1.0 2.0 1.0])

(def colours
  "raylib's own definitions of the colours the original names."
  {:background [245 245 245 255]
   :cube [130 130 130 255]
   :wires [80 80 80 255]
   :hit-cube [230 41 55 255]
   :hit-wires [190 33 55 255]
   :selection [0 228 48 255]
   :ray [190 33 55 255]
   :hint [80 80 80 255]
   :readout [80 80 80 255]})

(def hint "tap the box, drag to orbit")
(def selected-text "BOX SELECTED")
(def missed-text "missed")

;; --- layout -----------------------------------------------------------------

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measure: `raylib.soft3d/field`
  plus `:look-scale`, `800 / field width`, which scales the drag."
  [metrics]
  (let [{[_ _ fw] :viewport
         :as field} (s3/field metrics)]
    (assoc field :look-scale (/ original-width fw))))

(def ^:private widest-texts
  "The longest each line of the readout can be, for sizing: the distance is two
  digits at most (the camera is under 30 away), the point and the normal have
  a minus sign on every part."
  [hint selected-text "distance 99.99" "point -9.99 -9.99 -9.99" "normal -1 -1 -1" missed-text])

(defn dimensions
  "`geometry` plus the text. `:caption` is the hint's `{:s :x :y :size}`;
  `:readout-slots` are four `{:x :y :size}` in the field's top left, one per
  line of `readout`; `:lines` is the hint and the four widest readout lines in
  their slots, so a test can check they fit. The size is cut back, to 8 at the
  least, so that the widest line covers no more than 0.92 of the width.
  `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [widest (apply max (map #(measure % 100) widest-texts))
        {:keys [size pad text-y]
         [fx fy fw] :viewport
         :as field} (s3/field metrics widest)
        geo (assoc field :look-scale (/ original-width fw))
        pitch (max (inc size) (int (* 1.4 size)))
        caption {:s hint
                 :x pad
                 :y text-y
                 :size size}
        slots (vec (for [k (range 4)]
                     {:x (+ fx pad)
                      :y (+ fy pad (* k pitch))
                      :size size}))]
    (assoc geo
           :caption caption
           :readout-slots slots
           :lines (into [caption]
                        (map #(assoc %1 :s %2) slots (subvec widest-texts 1 5))))))

;; --- the camera -------------------------------------------------------------

(defn camera-at
  "The original's camera at orbit `angle` and `height`: radius 14 round the y
  axis, aimed at (0, 1, 0), up (0, 1, 0), fovy 45."
  [angle height]
  {:position [(* orbit-radius (Math/cos angle)) (double height) (* orbit-radius (Math/sin angle))]
   :target cube-centre
   :up [0.0 1.0 0.0]
   :fovy 45.0
   :projection :perspective})

(defn camera
  "`state`'s camera fitted to `dims`' field by `raylib.soft3d/fit-camera`."
  [state dims]
  (s3/fit-camera (camera-at (:angle state) (:height state)) original-aspect (:aspect dims)))

(defn view
  "`raylib.soft3d/view-proj` of `state`'s camera over `dims`' field. The draw
  and the pick both read this one value, so the ray goes through the camera that
  was drawn."
  [state dims]
  (s3/view-proj (camera state dims) (:viewport dims)))

;; --- the scene --------------------------------------------------------------

(defn ray-pieces
  "The segments `[[x y z] [x y z]]` of `ray` (`{:position :direction}`) to
  draw: `DrawRay`'s line, 10000 units long, minus the stretch inside the cube.
  There is no depth buffer here, so a line through the opaque cube would show
  across it once the camera has moved. The stretch is cut out with the slab
  test (the same maths `raylib.soft3d/ray-box` uses), leaving the piece before
  the entry and the piece after the exit, or one piece when the ray misses the
  box, or starts inside it. A part of the ray that lies behind the cube can
  still show through it from some angles."
  [{:keys [position direction]}]
  (let [[ox oy oz] (mapv double position)
        [dx dy dz] (mapv double direction)
        at (fn [t] [(+ ox (* dx t)) (+ oy (* dy t)) (+ oz (* dz t))])
        whole [[ox oy oz] (at ray-length)]
        slab (fn [o d lo hi]
               (if (zero? d)
                 (when (< lo o hi) [-1.0e30 1.0e30])
                 (let [t1 (/ (- lo o) d)
                       t2 (/ (- hi o) d)]
                   [(min t1 t2) (max t1 t2)])))
        spans [(slab ox dx -1.0 1.0) (slab oy dy 0.0 2.0) (slab oz dz -1.0 1.0)]]
    (if (some nil? spans)
      [whole]
      (let [t-in (apply max (map first spans))
            t-out (apply min (map second spans))]
        (if (or (>= t-in t-out) (<= t-out 0.0) (>= t-in ray-length))
          [whole]
          (cond-> []
            (> t-in 0.0) (conj [[ox oy oz] (at t-in)])
            (< t-out ray-length) (conj [(at t-out) (peek whole)])))))))

(defn scene-list
  "The finished draw list for `state`: the grid of 10, the cube drawn flat as
  `DrawCube` draws it and its wires (RED and MAROON while selected, with the
  larger GREEN wires), then the pick ray if there is one."
  [state dims]
  (let [vp (view state dims)
        hit? (:hit? state)
        ray (:ray state)]
    (cond-> (-> []
                (s3/grid vp 10 1.0)
                (s3/cube vp nil cube-centre cube-size
                         (if hit? (:hit-cube colours) (:cube colours)) {:shade :flat})
                (s3/cube-wires vp nil cube-centre cube-size
                               (if hit? (:hit-wires colours) (:wires colours)) {:hide-back? true}))
      hit? (s3/cube-wires vp nil cube-centre (+ cube-size 0.2) (:selection colours) {:hide-back? true})
      ray (s3/lines vp nil (mapv (fn [[a b]] [a b (:ray colours)]) (ray-pieces ray)))
      true s3/finish)))

(defn- f2
  "`v` to two decimals, `%.2f` without a minus sign on a zero."
  [v]
  (let [n (long (Math/round (* 100.0 (double v))))
        a (Math/abs n)
        r (rem a 100)]
    (str (if (neg? n) "-" "") (quot a 100) "." (if (< r 10) "0" "") r)))

(defn readout
  "The lines to show for `state`, as `{:s :colour}`: nothing before a pick or
  after one is let go, \"missed\" after a miss, and after a hit \"BOX SELECTED\"
  then its distance, point and normal."
  [{:keys [pick]}]
  (cond
    (nil? pick) []
    (:hit? pick) (let [{:keys [distance point normal]} pick
                       text-colour (:readout colours)]
                   [{:s selected-text
                     :colour (:selection colours)}
                    {:s (str "distance " (f2 distance))
                     :colour text-colour}
                    {:s (str "point " (apply str (interpose " " (map f2 point))))
                     :colour text-colour}
                    {:s (str "normal " (apply str (interpose " " (map #(str (long %)) normal))))
                     :colour text-colour}])
    :else [{:s missed-text
            :colour (:ray colours)}]))

;; --- fingers ----------------------------------------------------------------

(defn- dist [[ax ay] [bx by]]
  (let [dx (- (double ax) (double bx))
        dy (- (double ay) (double by))]
    (Math/sqrt (+ (* dx dx) (* dy dy)))))

(defn- next-orbit
  "The drag after this frame, from the previous one `orbit`, the `points` and
  whether this is a `press?`. Only exactly one finger drags, and a drag begins
  only at a press inside the field: a finger that was already down, or that is
  all that is left of two, never begins one. The drag turns `:dragging?` on
  once the finger is further than the slop from where it began."
  [orbit points press? dims metrics]
  (when (= 1 (count points))
    (let [p (first points)]
      (cond
        press? (when (gesture/in-rect? (:viewport dims) p)
                 {:start p
                  :at p
                  :dragging? false})
        orbit {:start (:start orbit)
               :at p
               :dragging? (or (:dragging? orbit)
                              (> (dist (:start orbit) p) (gesture/slop metrics)))}))))

(defn- drag-delta
  "The pixels the finger moved, when it is dragging, else nil. The frame that
  starts the drag carries all the travel since the press, so the camera follows
  the finger from where it landed. After that it is the frame's own movement."
  [orbit prev]
  (when (and (:dragging? orbit) prev)
    (let [[ax ay] (if (:dragging? prev) (:at prev) (:start prev))]
      [(- (double (first (:at orbit))) (double ax))
       (- (double (second (:at orbit))) (double ay))])))

(defn- pick
  "Cast the ray through the PRESS position `at` of a tap and test the cube.
  A tap while selected lets go and keeps the ray, as the original's latch does."
  [state dims at]
  (if (:hit? state)
    (assoc state :hit? false :pick nil)
    (let [ray (s3/screen->ray (view state dims) at)
          result (s3/ray-box ray cube-lo cube-hi)]
      (assoc state :ray ray :pick result :hit? (boolean (:hit? result))))))

(defn advance
  "One frame. `gesture/track` gives the tap. The camera drifts round by
  `orbit-speed` while no finger is down and the phase is not a press, a hold or
  a release, is turned by a one-finger drag, and the pick of a tap is cast
  through the camera as it now stands. A release frame with fewer than two points
  lifts everything, and its position is never read. A rotation of the phone
  drops the drag and the gesture."
  [state input]
  (let [metrics (:metrics input)
        dims (geometry metrics)
        screen (:screen metrics)
        state (if (not= screen (:screen state))
                (assoc (dissoc state :orbit) :n 0 :gesture gesture/idle)
                state)
        phase (get-in input [:pointer :phase])
        raw (vec (:touch-points input))
        points (if (and (= :release phase) (< (count raw) 2)) [] raw)
        n (count points)
        prev-n (:n state 0)
        orbit (next-orbit (:orbit state) points (= :press phase) dims metrics)
        [dx dy] (drag-delta orbit (:orbit state))
        [g event] (gesture/track (:gesture state) input)
        k (* sensitivity (:look-scale dims))
        busy? (or (pos? n) (contains? #{:press :down :release} phase))
        angle (cond-> (:angle state)
                (not busy?) (+ orbit-speed)
                dx (+ (* dx k)))
        height (cond-> (:height state)
                 dy (-> (+ (* dy k orbit-radius)) (max min-height) (min max-height)))
        state (assoc state
                     :screen screen
                     :gesture g
                     :n n
                     :orbit orbit
                     :angle angle
                     :height height)]
    (if (and (= :tap (:type event))
             (< (max n prev-n) 2)
             (gesture/in-rect? (:viewport dims) (:at event)))
      (pick state dims (:at event))
      state)))

(defn- init [{:keys [metrics]}]
  [{:screen (:screen metrics)
    :angle 0.0
    :height orbit-height
    :ray nil
    :pick nil
    :hit? false
    :orbit nil
    :gesture gesture/idle
    :n 0}
   [[:scene/init :picking]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :picking]]])

(defn scene []
  {:id :picking
   :title "3D Picking"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
