(ns raylib.scenes.fpcamera
  "A first-person camera walked round a yard of columns on two thumbs, ported
  from raylib-jlt's `camera_3d_first_person` (zlib licence).

  The original builds 40 columns with `GetRandomValue`: x and z in [-20, 20],
  a height in [2, 12], and each of r, g, b in [60, 255] with alpha 255, in the
  order x, z, h, r, g, b. A column is a box of side 2 by its height by 2
  standing on the ground, drawn by `cube!` (so `raylib.soft3d/cube`'s default
  shading). It draws a grid of 40 first, on a sky of (140, 190, 230), from an
  eye 2 units up through a 60 degree perspective camera. Here `GetRandomValue`
  is the project's LCG seeded with 20261002, taking its high bits
  (`(mod (quot seed' 65536) range)`), so the yard is the same every run. The
  counts, ranges, colours and camera are the original's. The projection is in
  software, by `raylib.soft3d`.

  The original does not call `UpdateCamera`. It keeps a yaw and a pitch, and
  hands raylib a camera whose target is the eye plus the look direction. That
  is raylib's `CAMERA_FIRST_PERSON`, rebuilt here in pure Clojure over a map
  `{:position :target :up :fovy :projection}` from rcamera.h. The functions
  mirrored are `GetCameraForward`, `GetCameraUp` and `GetCameraRight`,
  `CameraYaw` and `CameraPitch` (both with the flags CAMERA_FIRST_PERSON sets:
  `rotateAroundTarget` false, `lockView` true, `rotateUp` false), and
  `CameraMoveForward` and `CameraMoveRight` (with `moveInWorldPlane` true).
  All but the two moves are `raylib.scenes.freecam`'s, which already mirrors
  them cited at their definitions, along with raymath.h's
  `Vector3RotateByAxisAngle`, `Vector3Angle` and `Vector3Normalize`. The two
  moves are redone here because freecam's have `moveInWorldPlane` false: with
  the up vector (0, 1, 0), which never changes, the vector is flattened to
  y = 0 and normalised before it is scaled.

  The original's keys and mouse, and what stands in for each:
  - W, A, S and D move 0.25 on the ground a frame (`SPEED`), forward and right.
    A relative thumb-stick that starts in the lower third of the field replaces
    them. The press point is its centre. Further than `gesture/slop` from it,
    the eye moves that way (up the glass is forward) 0.25 an update, the
    direction a unit vector, so one speed everywhere. The original's keys add,
    which makes a diagonal 1.41 times faster, and that is the one deliberate
    difference. Nothing reads a frame time, like the original: the speed is
    per update, and walking does not leave the ground when the view is tipped.
  - The mouse position delta looks, 0.004 radians a pixel (`SENS`), yaw then
    pitch. A drag that starts in the upper two thirds of the field replaces it,
    times `800 / field width` so that a drag across the glass turns as far as
    one across the original's 800 pixel window.
  - Both work at once, each by its own finger.

  `raylib.stick` decides whose finger it is. A stick or a look begins only on
  a finger that was not down the frame before, inside its region. It follows
  only that finger, by touch id (by nearness when the host gives none), and
  ends when that finger lifts, so a finger that rests, or that began under
  Back, is never adopted in its place. A rotation of the phone drops both. A
  tap moves nothing.

  Two differences in the look, from CAMERA_FIRST_PERSON being what it is. The
  original clamps its pitch to +-1.4 radians. `CameraPitch` with `lockView`
  holds the view 0.001 radians short of straight up or down instead (about
  1.570 radians), so a drag can tip the view further than the original lets
  it. And a pitched view keeps its eye at height 2, as the original does,
  because `moveInWorldPlane` flattens the walk.

  Dropped: `fps!`, the on-screen frame counter, as earlier scenes drop it, and
  the mouse itself, which a phone has no cursor for. The original's key text
  \"WASD move - mouse look\" is kept as a caption below Back, its words changed
  to the touch controls, sized in `dimensions` so it fits.

  Faces with a corner behind the near plane are dropped whole, a known limit
  of `raylib.soft3d`, so standing against a column makes its faces vanish
  rather than clip. The grid is drawn under every face (`raylib.soft3d/finish`
  puts all grid lines first), so where the original's depth buffer would show a
  grid line in front of a column's foot, here the column hides it; the grid
  lies on the ground where the columns stand, so only lines under a column's
  footprint are lost. Overlapping columns are ordered whole, face by face, so
  where two columns cross a face can paint over one a depth buffer would put
  in front. Both are limits of the painter, not chased. The original's 60
  degree fovy is kept while the field is at least as wide as 800x450, and
  widened by `raylib.soft3d/fit-camera` in a narrower one.

  The state holds the `:camera`, the finger tracking (`:look` and `:stick`, each
  with its finger's id), `:n` (the finger count last frame), `:pts` and `:ids`
  (that frame's touch points and ids) and `:screen`. Colours are `[r g b a]`
  vectors."
  (:require [raylib.scenes.freecam :as free]
            [raylib.soft3d :as s3]
            [raylib.stick :as stick]))

(def speed "The original's SPEED: ground units an update for the stick. " 0.25)
(def sensitivity "The original's SENS: radians a pixel of look, in an 800 pixel window." 0.004)
(def eye-height "The original's EYE-Y." 2.0)
(def original-width "The original's window width, in pixels." 800.0)
(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))
(def n-columns "The original's N-COLUMNS." 40)
(def seed "The LCG's start." 20261002)

(def sky-colour "The original's (140, 190, 230)." [140 190 230 255])
(def caption-colour "DARKGRAY, as raylib defines it." [80 80 80 255])
(def caption-text "drag low to walk, high to look")

(def initial-camera
  "The original's camera at yaw 0, pitch 0: the eye 2 up at the origin, its
  target one unit along +x (forward is (cos yaw, sin yaw) on the ground), up
  (0, 1, 0), fovy 60."
  {:position [0.0 eye-height 0.0]
   :target [1.0 eye-height 0.0]
   :up [0.0 1.0 0.0]
   :fovy 60.0
   :projection :perspective})

;; --- the yard -----------------------------------------------------------------

(defn- next-random [s]
  (mod (+ (* 1103515245 (long s)) 12345) 2147483648))

(defn- random-value
  "`[v seed']`: an int in [lo, hi] from the LCG's high bits, as
  `GetRandomValue(lo, hi)`. The low bit alternates, so `(quot seed' 65536)` is
  what gets used."
  [s lo hi]
  (let [s' (next-random s)]
    [(+ lo (mod (quot s' 65536) (inc (- hi lo)))) s']))

(defn make-columns
  "The original's 40 columns as `{:x :z :h :colour}`, drawn from the LCG seeded
  with `seed` in the original's order: x, z, h, then r, g, b of each column."
  []
  (loop [i 0 s seed out []]
    (if (< i n-columns)
      (let [[x s1] (random-value s -20 20)
            [z s2] (random-value s1 -20 20)
            [h s3] (random-value s2 2 12)
            [r s4] (random-value s3 60 255)
            [g s5] (random-value s4 60 255)
            [b s6] (random-value s5 60 255)]
        (recur (inc i) s6 (conj out {:x (double x)
                                     :z (double z)
                                     :h (double h)
                                     :colour [r g b 255]})))
      out)))

(def columns "The yard, made once." (make-columns))

;; --- layout -------------------------------------------------------------------

(defn geometry
  "The layout for `metrics`' `:screen`: `raylib.soft3d/field` (`:viewport`,
  `:aspect`, `:size`, `:pad`, `:text-y`) plus `:stick-top`, the y where the
  lower third of the field starts. Above it a touch looks, from it down a
  touch walks, as `raylib.scenes.freecam/region` reads it. With `widest`, the
  caption's width at size 100, the size is cut back as `field` does."
  ([metrics] (geometry metrics nil))
  ([metrics widest]
   (let [{[_ fy _ fh] :viewport
          :as field} (s3/field metrics widest)]
     (assoc field :stick-top (+ fy (* (/ 2.0 3.0) fh))))))

(defn dimensions
  "`geometry` plus the caption as `{:s :x :y :size}`, in `:caption`, and in
  `:lines` as well so a test can check it fits. The size is cut back, to 8 at
  the least, so that the caption covers no more than 0.92 of the width.
  `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [pad text-y size]
         :as geo} (geometry metrics (measure caption-text 100))
        line {:s caption-text
              :x pad
              :y text-y
              :size size}]
    (assoc geo :caption line :lines [line])))

;; --- the picture --------------------------------------------------------------

(defn camera
  "`state`'s camera fitted to `dims`' field by `raylib.soft3d/fit-camera`."
  [state dims]
  (s3/fit-camera (:camera state) original-aspect (:aspect dims)))

(defn scene-list
  "The finished draw list for `state`: the grid of 40, then each column as
  `cube!` draws it, size 2 by its height by 2 and standing on the ground."
  [state dims]
  (let [vp (s3/view-proj (camera state dims) (:viewport dims))]
    (s3/finish
     (reduce (fn [dl {:keys [x z h colour]}]
               (s3/cube dl vp nil [x (/ h 2.0) z] [2.0 h 2.0] colour))
             (s3/grid [] vp 40 1.0)
             columns))))

;; --- rcamera.h: the two moves with moveInWorldPlane true ------------------------

(defn- flat-unit
  "The vector with y set to 0 and normalised: rcamera.h's `moveInWorldPlane`
  branch for an up vector along y (the `else` of its `up.z`/`up.x` tests)."
  [[x _ z]]
  (let [l (Math/sqrt (+ (* x x) (* z z)))]
    (if (zero? l) [0.0 0.0 0.0] [(/ x l) 0.0 (/ z l)])))

(defn- shift
  [c [dx dy dz]]
  (let [[px py pz] (:position c)
        [tx ty tz] (:target c)]
    (assoc c
           :position [(+ px dx) (+ py dy) (+ pz dz)]
           :target [(+ tx dx) (+ ty dy) (+ tz dz)])))

(defn move-forward
  "rcamera.h CameraMoveForward with `moveInWorldPlane` true: position and target
  both move `distance` along the forward vector flattened onto the ground."
  [c distance]
  (let [[x y z] (flat-unit (free/camera-forward c))]
    (shift c [(* x distance) (* y distance) (* z distance)])))

(defn move-right
  "rcamera.h CameraMoveRight with `moveInWorldPlane` true: position and target
  both move `distance` along the right vector flattened onto the ground."
  [c distance]
  (let [[x y z] (flat-unit (free/camera-right c))]
    (shift c [(* x distance) (* y distance) (* z distance)])))

;; --- fingers --------------------------------------------------------------------

(defn- d2 [[ax ay] [bx by]]
  (let [dx (- (double ax) (double bx))
        dy (- (double ay) (double by))]
    (+ (* dx dx) (* dy dy))))

(defn- follow-both
  "`look` and `stick` (either may be nil) each moved to its own finger by
  `raylib.stick/follow`, as `[look' stick']`. Without ids both could claim the
  one nearest point. Then the owner that moved less keeps it and the other
  follows from what is left."
  [look stick frame]
  (let [l (stick/follow look frame)
        s (stick/follow stick frame)]
    (if (and l s (= (:at l) (:at s)))
      (let [without (fn [at] (update frame :points #(filterv (fn [q] (not= q at)) %)))]
        (if (<= (d2 (:at l) (:at look)) (d2 (:at s) (:at stick)))
          [l (stick/follow stick (without (:at l)))]
          [(stick/follow look (without (:at s))) s]))
      [l s])))

(defn- begin
  "`{:look :stick}` with a look or a stick begun on each fresh finger (a finger
  that was not down last frame) in its region, when it has none. A finger that
  is already an owner's is skipped, and one outside the field (under Back) is
  nothing."
  [{:keys [look stick]
    :as owners} dims fresh]
  (reduce (fn [acc {:keys [at id]}]
            (if (or (= at (:at look)) (= at (:at stick)))
              acc
              (case (free/region dims at)
                :look (if (:look acc) acc (assoc acc :look {:at at
                                                            :id id}))
                :stick (if (:stick acc) acc (assoc acc :stick {:centre at
                                                               :at at
                                                               :id id}))
                acc)))
          owners
          fresh))

(defn- look-by
  "The mouse look: yaw by `dx` pixels then pitch by `dy`, each 0.004 radians
  (`sensitivity`) times `800 / field width`. A drag right turns right and a drag
  up looks up, as the original's `yaw + SENS * dx` and `pitch - SENS * dy`;
  `CameraYaw` turns the other way round the up axis, so the angle is negated."
  [c dims [dx dy]]
  (let [k (* sensitivity (/ original-width (nth (:viewport dims) 2)))]
    (cond-> c
      (not (zero? dx)) (free/camera-yaw (* -1.0 dx k))
      (not (zero? dy)) (free/camera-pitch (* -1.0 dy k)))))

(defn- walk
  "The original's WASD as `UpdateCamera` does it: forward, then right, `speed`
  along the stick's unit direction (`raylib.scenes.freecam/stick-dir`)."
  [c stick metrics]
  (if-let [[ux uy] (free/stick-dir stick metrics)]
    (-> c
        (move-forward (* -1.0 uy speed))
        (move-right (* ux speed)))
    c))

(defn advance
  "One frame. Fingers are sorted into a look and a stick by `raylib.stick`, then
  the camera is looked and walked in the original's order. A release frame with
  fewer than two points lifts everything, and its position is never read. A
  rotation of the phone drops the tracking, whose pixels are the old screen's."
  [state input]
  (let [metrics (:metrics input)
        dims (geometry metrics)
        screen (:screen metrics)
        state (if (not= screen (:screen state))
                (assoc (dissoc state :look :stick) :n 0 :pts [] :ids nil)
                state)
        phase (get-in input [:pointer :phase])
        raw (vec (:touch-points input))
        points (if (and (= :release phase) (< (count raw) 2)) [] raw)
        ids (stick/ids-of input points)
        frame {:points points
               :ids ids
               :metrics metrics
               :press? (= :press phase)
               :free? (constantly true)}
        [look stick] (follow-both (:look state) (:stick state) frame)
        fresh (stick/fresh frame state)
        {look' :look
         stick' :stick} (begin {:look look
                                :stick stick} dims fresh)
        delta (when (and look (:look state))
                [(- (double (first (:at look))) (double (first (:at (:look state)))))
                 (- (double (second (:at look))) (double (second (:at (:look state)))))])
        c (cond-> (:camera state)
            delta (look-by dims delta)
            stick' (walk stick' metrics))]
    (assoc state
           :screen screen
           :n (count points)
           :pts points
           :ids ids
           :look look'
           :stick stick'
           :camera c)))

(defn- init [{:keys [metrics]}]
  [{:camera initial-camera
    :screen (:screen metrics)
    :n 0
    :pts []
    :ids nil
    :look nil
    :stick nil}
   [[:scene/init :fpcamera]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :fpcamera]]])

(defn scene []
  {:id :fpcamera
   :title "First-Person Camera"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
