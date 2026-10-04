(ns net.b12n.raylib-ios.scenes.fpcamera
  "A first-person camera walked round a yard of columns on two thumbs, ported
  from raylib-jlt's `camera_3d_first_person` (zlib licence).

  The original builds 40 columns with `GetRandomValue`: x and z in [-20, 20],
  a height in [2, 12], and each of r, g, b in [60, 255] with alpha 255, in the
  order x, z, h, r, g, b. A column is a box of side 2 by its height by 2
  standing on the ground, drawn by `cube!` (so `net.b12n.raylib-ios.soft3d/cube`'s default
  shading). It draws a grid of 40 first, on a sky of (140, 190, 230), from an
  eye 2 units up through a 60 degree perspective camera. Here `GetRandomValue`
  is the project's LCG seeded with 20261002, taking its high bits
  (`(mod (quot seed' 65536) range)`), so the yard is the same every run. The
  counts, ranges, colours and camera are the original's. The projection is in
  software, by `net.b12n.raylib-ios.soft3d`.

  The original does not call `UpdateCamera`, and neither does this. It keeps
  a yaw and a pitch and hands raylib a camera whose target is the eye plus the
  look direction (camera_3d_first_person.clj lines 40-53): forward
  on the ground is `(cos yaw, sin yaw)`, right is `(-sin yaw, cos yaw)`, and
  the target is `(px + cos pitch * cos yaw, 2 + sin pitch, pz + cos pitch * sin
  yaw)`. Those formulas are written out here over a camera map `{:position
  :target :up :fovy :projection}`. Nothing of rcamera.h is mirrored.

  The original's keys and mouse, and what stands in for each:
  - W, A, S and D move 0.25 on the ground a frame (`SPEED`), forward and right.
    A relative thumb-stick that starts in the lower third of the field replaces
    them. The press point is its centre. Further than `gesture/slop` from it,
    the eye moves that way (up the glass is forward) 0.25 an update, the
    direction a unit vector, so one speed everywhere. The original's keys add,
    which makes a diagonal 1.41 times faster, and that is the one deliberate
    difference. Nothing reads a frame time, like the original: the speed is
    per update, and walking does not leave the ground when the view is tipped,
    because forward is the ground vector `(cos yaw, sin yaw)` whatever the pitch.
  - The mouse position delta looks, 0.004 radians a pixel (`SENS`), yaw then
    pitch (lines 40-41, the pitch held to +-1.4 radians, `pitch-limit`). A drag that
    starts in the upper two thirds of the field replaces it, times
    `800 / field width` so that a drag across the glass turns as far as one
    across the original's 800 pixel window.
  - Both work at once, each by its own finger.

  `net.b12n.raylib-ios.stick` decides whose finger it is. A stick or a look begins only on
  a finger that was not down the frame before, inside its region. It follows
  only that finger, by touch id (by nearness when the host gives none), and
  ends when that finger lifts, so a finger that rests, or that began under
  Back, is never adopted in its place. A rotation of the phone drops both. A
  tap moves nothing.

  Dropped: `fps!`, the on-screen frame counter, as earlier scenes drop it, and
  the mouse itself, which a phone has no cursor for. The original's key text
  \"WASD move - mouse look\" is kept as a caption below Back, its words changed
  to the touch controls, sized in `dimensions` so it fits.

  Faces with a corner behind the near plane are dropped whole, a known limit
  of `net.b12n.raylib-ios.soft3d`, so standing against a column makes its faces vanish
  rather than clip. The grid is drawn under every face (`net.b12n.raylib-ios.soft3d/finish`
  puts all grid lines first), so where the original's depth buffer would show a
  grid line in front of a column's foot, here the column hides it; the grid
  lies on the ground where the columns stand, so only lines under a column's
  footprint are lost. Overlapping columns are ordered whole, face by face, so
  where two columns cross a face can paint over one a depth buffer would put
  in front. Both are limits of the painter, not chased. The original's 60
  degree fovy is kept while the field is at least as wide as 800x450, and
  widened by `net.b12n.raylib-ios.soft3d/fit-camera` in a narrower one.

  The state holds `:px`, `:pz`, `:yaw` and `:pitch` (numbers), the `:camera`
  built from them, the finger tracking (`:look` and `:stick`, each
  with its finger's id), `:n` (the finger count last frame), `:pts` and `:ids`
  (that frame's touch points and ids) and `:screen`. Colours are `[r g b a]`
  vectors."
  (:require [net.b12n.raylib-ios.scenes.freecam :as free]
            [net.b12n.raylib-ios.soft3d :as s3]
            [net.b12n.raylib-ios.stick :as stick]))

(def speed "The original's SPEED: ground units an update for the stick. " 0.25)
(def sensitivity "The original's SENS: radians a pixel of look, in an 800 pixel window." 0.004)
(def pitch-limit "The original's pitch clamp, radians (line 41: `(max -1.4) (min 1.4)`)." 1.4)
(def eye-height "The original's EYE-Y." 2.0)
(def original-width "The original's window width, in pixels." 800.0)
(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))
(def n-columns "The original's N-COLUMNS." 40)
(def seed "The LCG's start." 20261002)

(def sky-colour "The original's (140, 190, 230)." [140 190 230 255])
(def caption-colour "DARKGRAY, as raylib defines it." [80 80 80 255])
(def caption-text "drag low to walk, high to look")

(defn camera-of
  "The camera for a walker at `px`, `pz` looking along `yaw` and `pitch`, as the
  original builds it each frame (lines 50-53): the eye 2 up, the target the eye
  plus `(cos pitch * cos yaw, sin pitch, cos pitch * sin yaw)`, up (0, 1, 0),
  fovy 60, perspective."
  [px pz yaw pitch]
  (let [cp (Math/cos pitch)]
    {:position [px eye-height pz]
     :target [(+ px (* cp (Math/cos yaw)))
              (+ eye-height (Math/sin pitch))
              (+ pz (* cp (Math/sin yaw)))]
     :up [0.0 1.0 0.0]
     :fovy 60.0
     :projection :perspective}))

(def initial-camera "The original's camera at yaw 0, pitch 0, from the origin." (camera-of 0.0 0.0 0.0 0.0))

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
  "The layout for `metrics`' `:screen`: `net.b12n.raylib-ios.soft3d/field` (`:viewport`,
  `:aspect`, `:size`, `:pad`, `:text-y`) plus `:stick-top`, the y where the
  lower third of the field starts. Above it a touch looks, from it down a
  touch walks, as `net.b12n.raylib-ios.scenes.freecam/region` reads it. With `widest`, the
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
  "`state`'s camera fitted to `dims`' field by `net.b12n.raylib-ios.soft3d/fit-camera`."
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

;; --- fingers --------------------------------------------------------------------

(defn- look-by
  "The original's mouse look (lines 40-41) for a drag of `dx`, `dy` pixels:
  `yaw + SENS * dx` and `(max -1.4) (min 1.4)` of `pitch - SENS * dy`, each
  pixel scaled by `800 / field width`."
  [{:keys [yaw pitch]
    :as state} dims [dx dy]]
  (let [k (* sensitivity (/ original-width (nth (:viewport dims) 2)))]
    (assoc state
           :yaw (+ yaw (* k dx))
           :pitch (-> (- pitch (* k dy)) (max (- pitch-limit)) (min pitch-limit)))))

(defn- walk
  "The original's WASD (lines 42-49) along the stick's unit direction
  (`net.b12n.raylib-ios.scenes.freecam/stick-dir`, which is the same vector arithmetic): `f`
  forward (up the glass) and `r` right, `dx = f * cos yaw + r * -sin yaw` and
  `dz = f * sin yaw + r * cos yaw`, scaled by `speed`."
  [{:keys [px pz yaw]
    :as state} stick metrics]
  (if-let [[ux uy] (free/stick-dir stick metrics)]
    (let [f (- uy) r ux
          fwx (Math/cos yaw) fwz (Math/sin yaw)]
      (assoc state
             :px (+ px (* speed (+ (* f fwx) (* r (- fwz)))))
             :pz (+ pz (* speed (+ (* f fwz) (* r fwx))))))
    state))

(defn advance
  "One frame. Fingers are sorted into a look and a stick by `net.b12n.raylib-ios.stick`, then
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
        [look stick] (stick/follow-pair (:look state) (:stick state) frame)
        fresh (stick/fresh frame state)
        {look' :look
         stick' :stick} (stick/begin-owners {:look look
                                             :stick stick}
                                            #(free/region dims %) fresh)
        delta (when (and look (:look state))
                [(- (double (first (:at look))) (double (first (:at (:look state)))))
                 (- (double (second (:at look))) (double (second (:at (:look state)))))])
        moved (cond-> state
                delta (look-by dims delta)
                stick' (walk stick' metrics))]
    (assoc state
           :screen screen
           :n (count points)
           :pts points
           :ids ids
           :look look'
           :stick stick'
           :px (:px moved)
           :pz (:pz moved)
           :yaw (:yaw moved)
           :pitch (:pitch moved)
           :camera (camera-of (:px moved) (:pz moved) (:yaw moved) (:pitch moved)))))

(defn- init [{:keys [metrics]}]
  [{:px 0.0
    :pz 0.0
    :yaw 0.0
    :pitch 0.0
    :camera initial-camera
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
