(ns raylib.scenes.freecam
  "A free 3D camera on two thumbs, ported from raylib-jlt's `camera_3d_free`,
  which is raylib's `core_3d_camera_free` example (zlib licence).

  The original lets raylib's own `UpdateCamera(CAMERA_FREE)` fly a camera round
  a red cube of side 2 at the origin, drawn with `DrawCube`, `DrawCubeWires` in
  maroon and a grid of 10. The camera starts at (10, 10, 10), looking at the
  origin, up (0, 1, 0), fovy 45. Here that camera is rebuilt in pure Clojure
  over a map `{:position :target :up :fovy :projection}`, and the scene is
  projected in software by `raylib.soft3d`. The rcamera.h functions mirrored
  are `GetCameraForward`, `GetCameraUp`, `GetCameraRight`, `CameraMoveForward`,
  `CameraMoveRight`, `CameraMoveToTarget`, `CameraYaw` and `CameraPitch`, each
  cited at its definition, with raymath.h's `Vector3RotateByAxisAngle`,
  `Vector3Angle` and `Vector3Normalize` under them. CAMERA_FREE fixes their
  flags (`moveInWorldPlane` false, `rotateAroundTarget` false, `lockView`
  true, `rotateUp` false), so they are fixed here too.

  Controls here, with `UpdateCamera`'s order kept (look, then move, then zoom):
  - A drag that starts in the upper two thirds of the field looks around, in
    place of the mouse delta. Each pixel turns the view 0.003 radians
    (`CAMERA_MOUSE_MOVE_SENSITIVITY`), yaw first and then pitch, times
    `800 / field width` so that a drag across the glass turns as far as one
    across the original's 800 pixel window. Pitch stops 0.001 radians short of
    straight up or down, as `lockView` does.
  - A relative thumb-stick that starts in the lower third replaces W, A, S and
    D. The press point is its centre. Further than `gesture/slop` from it, the
    camera moves that way along its view (up the glass is forward) at
    `CAMERA_MOVE_SPEED` 5.4 units a second times `:delta-seconds`, the vector
    normalised, so one speed in every direction. The original's keys add, which
    makes a diagonal faster, and that is the one deliberate difference.
  - Two fingers that are both in the upper two thirds pinch to dolly, in place
    of the wheel's `CameraMoveToTarget(-wheel)`. The camera moves along its
    view by `distance * (1 / ratio - 1)` where `ratio` is how much the fingers'
    distance changed, so spreading them to twice the distance halves the
    camera's distance from the target. It never reaches zero
    (`CameraMoveToTarget` holds 0.001). Two fingers in different regions are
    look and stick, not a pinch, and the stick finger stays the stick if it
    drifts up into the look region. A second finger landing high while a stick
    is held is a second look, never a pinch.
  - The pinch rule is the shared one (`raylib.camera2d/pinch-frame`): it acts
    only while exactly two fingers stay down, and any change of finger count
    only records. A third finger ends everything.
  - A \"reset\" button below Back replaces Z, which in the original only sets
    the camera's target to (0, 0, 0). So does the button: the position and up
    stay where they are. A tap that ends a multi-finger touch is not a press on
    it.

  Dropped, because a phone has nothing to map them to: the cursor lock and
  unlock (`DisableCursor`), the arrow keys that turn the camera, Q and E that
  roll it, Space and Control that lift and lower it, the middle mouse pan, the
  gamepad and the keypad plus and minus. The original never lists most of
  them, but `UpdateCamera` reads them all. The wheel's pan, which the original
  does list, goes with the middle mouse button.

  Faces with a corner behind the near plane are dropped whole, a known limit
  of `raylib.soft3d`. Flying into the cube makes its faces vanish rather than
  clip. Lines (the grid and the wires) are clipped to the near plane.

  The HUD box of the original (a translucent rect, its outline and four lines
  of help text, at (10, 10) of an 800x450 window) is kept, its text changed to
  the touch controls, sized in `dimensions` and drawn in the top left of the
  field. The reset button sits in the row below Back, above the field.
  The original's 45 degree fovy is kept while the field is at least as wide as
  800x450, and widened by `raylib.soft3d/fit-camera` in a narrower one.

  The state holds the `:camera` and the finger tracking: `:look`, `:stick`,
  `:pinch`, `:n` (the finger count last frame), `:gesture` for the button's
  tap and `:screen`. Colours are `[r g b a]` vectors."
  (:require [raylib.camera2d :as cam]
            [raylib.gesture :as gesture]
            [raylib.soft3d :as s3]))

(def move-speed "CAMERA_MOVE_SPEED, units a second. rcamera.h's." 5.4)
(def mouse-sensitivity "CAMERA_MOUSE_MOVE_SENSITIVITY, radians a pixel. rcamera.h's." 0.003)
(def pitch-margin "The numerical margin lockView keeps from straight up or down." 0.001)
(def original-width "The original's window width, in pixels." 800.0)

(def background-colour "RAYWHITE, as raylib defines it." [245 245 245 255])
(def cube-colour "RED, as raylib defines it." [230 41 55 255])
(def wires-colour "MAROON, as raylib defines it." [190 33 55 255])
(def hud-fill-colour "The original's (102, 191, 255, 128)." [102 191 255 128])
(def hud-edge-colour "BLUE, as raylib defines it." [0 121 241 255])
(def hud-title-colour "BLACK" [0 0 0 255])
(def hud-text-colour "DARKGRAY, as raylib defines it." [80 80 80 255])
(def button-colour [200 200 200 255])
(def button-label-colour [80 80 80 255])

(def hud-title "Free camera, touch controls:")
(def hud-texts ["- Drag high to look around"
                "- Drag low to move"
                "- Pinch high to move in and out"])
(def reset-label "reset")

(def initial-camera
  "The original's camera: (10, 10, 10) looking at the origin, up (0, 1, 0),
  fovy 45, perspective."
  {:position [10.0 10.0 10.0]
   :target [0.0 0.0 0.0]
   :up [0.0 1.0 0.0]
   :fovy 45.0
   :projection :perspective})

;; --- raymath.h and rcamera.h, as pure functions over 3-vectors and the camera --

(defn- v- [[ax ay az] [bx by bz]] [(- ax bx) (- ay by) (- az bz)])
(defn- v+ [[ax ay az] [bx by bz]] [(+ ax bx) (+ ay by) (+ az bz)])
(defn- v* [[x y z] k] [(* x k) (* y k) (* z k)])
(defn- cross [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by)) (- (* az bx) (* ax bz)) (- (* ax by) (* ay bx))])
(defn- dot [[ax ay az] [bx by bz]] (+ (* ax bx) (* ay by) (* az bz)))
(defn- length [[x y z]] (Math/sqrt (+ (* x x) (* y y) (* z z))))

(defn- normalize
  "raymath.h Vector3Normalize: a zero-length vector stays zero."
  [v]
  (let [l (length v)]
    (if (zero? l) v (v* v (/ 1.0 l)))))

(defn- angle-between
  "raymath.h Vector3Angle: atan2 of the cross product's length and the dot."
  [a b]
  (Math/atan2 (length (cross a b)) (dot a b)))

(defn rotate-by-axis-angle
  "raymath.h Vector3RotateByAxisAngle: `v` turned by `angle` radians about
  `axis`, by the Euler-Rodrigues formula as the C writes it."
  [v axis angle]
  (let [axis (normalize axis)
        half (/ angle 2.0)
        w (v* axis (Math/sin half))
        a (Math/cos half)
        wv (cross w v)
        wwv (cross w wv)]
    (v+ (v+ v (v* wv (* 2.0 a))) (v* wwv 2.0))))

(defn camera-forward
  "rcamera.h GetCameraForward: the unit vector from position to target."
  [{:keys [position target]}]
  (normalize (v- target position)))

(defn camera-up
  "rcamera.h GetCameraUp: the unit up vector."
  [{:keys [up]}]
  (normalize up))

(defn camera-right
  "rcamera.h GetCameraRight: the unit vector forward x up."
  [c]
  (normalize (cross (camera-forward c) (camera-up c))))

(defn camera-move-forward
  "rcamera.h CameraMoveForward with `moveInWorldPlane` false: position and
  target both move `distance` along the forward vector."
  [c distance]
  (let [d (v* (camera-forward c) distance)]
    (assoc c :position (v+ (:position c) d) :target (v+ (:target c) d))))

(defn camera-move-right
  "rcamera.h CameraMoveRight with `moveInWorldPlane` false: position and target
  both move `distance` along the right vector."
  [c distance]
  (let [d (v* (camera-right c) distance)]
    (assoc c :position (v+ (:position c) d) :target (v+ (:target c) d))))

(defn camera-move-to-target
  "rcamera.h CameraMoveToTarget: the distance to the target plus `delta`, held
  above 0.001, with the position moved along the forward vector to it."
  [c delta]
  (let [distance (+ (length (v- (:position c) (:target c))) delta)
        distance (if (<= distance 0.0) 0.001 distance)]
    (assoc c :position (v+ (:target c) (v* (camera-forward c) (- distance))))))

(defn camera-yaw
  "rcamera.h CameraYaw with `rotateAroundTarget` false: the target turns about
  the camera's up vector by `angle` radians, the position stays."
  [c angle]
  (let [view (rotate-by-axis-angle (v- (:target c) (:position c)) (camera-up c) angle)]
    (assoc c :target (v+ (:position c) view))))

(defn camera-pitch
  "rcamera.h CameraPitch with `lockView` true, `rotateAroundTarget` false and
  `rotateUp` false: the angle is held so the view stops 0.001 radians short of
  straight up or down, then the target turns about the right vector by it. The
  up vector is not rotated."
  [c angle]
  (let [up (camera-up c)
        view (v- (:target c) (:position c))
        max-up (- (angle-between up view) pitch-margin)
        angle (if (> angle max-up) max-up angle)
        max-down (+ (* -1.0 (angle-between (v* up -1.0) view)) pitch-margin)
        angle (if (< angle max-down) max-down angle)
        turned (rotate-by-axis-angle view (camera-right c) angle)]
    (assoc c :target (v+ (:position c) turned))))

;; --- layout -----------------------------------------------------------------

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measure. It is
  `raylib.soft3d/field` (`:viewport`, `:aspect`, `:size`, `:pad`, `:text-y`)
  plus

  - `:stick-top`, the y where the lower third of the field starts. Above it a
    touch looks, from it down a touch moves;
  - `:reset`, the button `[x y w h]` in the row below Back, above the field;
  - `:look-scale`, `800 / field width`, which scales the look sensitivity."
  [metrics]
  (let [{:keys [size pad text-y]
         [_ fy fw fh] :viewport
         :as field} (s3/field metrics)]
    (assoc field
           :stick-top (+ fy (* (/ 2.0 3.0) fh))
           :reset [(double pad) (double text-y) (* 0.3 fw) (+ size (* 0.5 pad))]
           :look-scale (/ original-width fw))))

(defn region
  "`:look` for a point in the field above `:stick-top`, `:stick` for one in the
  field from it down, and nil for one outside the field."
  [{:keys [viewport stick-top]} pt]
  (when (gesture/in-rect? viewport pt)
    (if (< (double (second pt)) stick-top) :look :stick)))

(defn dimensions
  "`geometry` plus the text. `:hud` is the box `[x y w h]` in the field's top
  left, `:hud-lines` its four lines as `{:s :x :y :size}`, `:reset-label` the
  button's label, and `:lines` all five so a test can check they fit. The HUD's
  text size is cut back from the field's, to 8 at the least, so the box covers
  no more than 0.92 of the width. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [size pad viewport]
         [rx ry rw rh] :reset
         :as geo} (geometry metrics)
        [fx fy fw] viewport
        inner pad
        widest-title (measure hud-title 100)
        widest-text (apply max (map #(measure % 100) hud-texts))
        ;; width per unit of text size: the title, or the indent (one size)
        ;; plus the widest item
        per-size (max (/ widest-title 100.0) (+ 1.0 (/ widest-text 100.0)))
        hud-size (max 8 (min size (int (/ (- (* 0.92 fw) pad (* 2 inner)) per-size))))
        pitch (max (inc hud-size) (int (* 1.4 hud-size)))
        indent hud-size
        hx (+ fx pad)
        hy (+ fy pad)
        tx (+ hx inner)
        ty (+ hy inner)
        title {:s hud-title
               :x tx
               :y ty
               :size hud-size}
        items (vec (map-indexed (fn [k s] {:s s
                                           :x (+ tx indent)
                                           :y (+ ty (* (inc k) pitch))
                                           :size hud-size})
                                hud-texts))
        hw (+ (* 2 inner)
              (Math/ceil (max (measure hud-title hud-size)
                              (+ indent (apply max (map #(measure % hud-size) hud-texts))))))
        hh (+ (* 2 inner) (* (count hud-texts) pitch) hud-size)
        label {:s reset-label
               :x (int (+ rx (* 0.5 (- rw (measure reset-label size)))))
               :y (int (+ ry (* 0.5 (- rh size))))
               :size size}
        hud-lines (into [title] items)]
    (assoc geo
           :hud [(double hx) (double hy) (double hw) (double hh)]
           :hud-lines hud-lines
           :reset-label label
           :lines (conj hud-lines label))))

;; --- the camera and the scene -------------------------------------------------

(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

(defn camera
  "`state`'s camera fitted to `dims`' field by `raylib.soft3d/fit-camera`."
  [state dims]
  (s3/fit-camera (:camera state) original-aspect (:aspect dims)))

(defn scene-list
  "The finished draw list for `state`: the grid of 10, the cube drawn flat as
  `DrawCube` draws it, and its maroon wires, which leave out the edges the
  opaque cube hides."
  [state dims]
  (let [vp (s3/view-proj (camera state dims) (:viewport dims))]
    (-> []
        (s3/grid vp 10 1.0)
        (s3/cube vp nil [0.0 0.0 0.0] 2.0 cube-colour {:shade :flat})
        (s3/cube-wires vp nil [0.0 0.0 0.0] 2.0 wires-colour {:hide-back? true})
        s3/finish)))

;; --- fingers -------------------------------------------------------------------

(defn- d2 [[ax ay] [bx by]]
  (let [dx (- ax bx) dy (- ay by)] (+ (* dx dx) (* dy dy))))

(defn- begin
  "The tracking a new finger at `p` starts: a look, a stick, or nothing."
  [dims p]
  (case (region dims p)
    :look {:look {:at p}}
    :stick {:stick {:centre p
                    :at p}}
    {}))

(defn- begin-both
  "Two new fingers `p` and `q`: both in the look region pinch (recorded, not
  yet acting), otherwise each starts what its region says, the first winning
  when both want the same."
  [dims p q]
  (if (and (= :look (region dims p)) (= :look (region dims q)))
    {:pinching :start}
    (merge-with (fn [a _] a) (begin dims p) (begin dims q))))

(defn- follow-look [look p]
  {:look {:at p}
   :look-delta [(- (double (first p)) (double (first (:at look))))
                (- (double (second p)) (double (second (:at look))))]})

(defn- follow-stick [stick p]
  {:stick (assoc stick :at p)})

(defn- track
  "What the fingers do this frame, from the previous `state` and the current
  `points`: `:look` and `:stick` (the tracking to keep), `:look-delta` (pixels
  the look finger moved, only when it was already looking), and `:pinching`
  (`:start` or `:continue`). Identity is by nearness to where each finger was,
  because the platform promises no order. New fingers only begin on a press, or
  when the count rises or falls, so a finger that was ignored (under Back, on the
  button, or from before the scene) never begins by drifting into the field."
  [state points dims press?]
  (let [n (count points)
        prev-n (:n state 0)
        look (:look state)
        stick (:stick state)]
    (cond
      (or (zero? n) (> n 2)) {}

      (= n 1)
      (let [p (first points)]
        (cond press? (begin dims p)
              (and look stick) (if (< (d2 p (:at stick)) (d2 p (:at look)))
                                 (follow-stick stick p)
                                 (follow-look look p))
              stick (follow-stick stick p)
              look (follow-look look p)
              :else {}))

      :else
      (let [p (nth points 0)
            q (nth points 1)]
        (cond
          (and (= prev-n 2) (:pinch state)) {:pinching :continue}

          (and look stick)
          (let [straight (+ (d2 p (:at look)) (d2 q (:at stick)))
                swapped (+ (d2 q (:at look)) (d2 p (:at stick)))
                [lp sp] (if (<= straight swapped) [p q] [q p])]
            (merge (follow-look look lp) (follow-stick stick sp)))

          (or look stick)
          (let [t (or look stick)
                i (if (<= (d2 p (:at t)) (d2 q (:at t))) 0 1)
                mine (nth points i)
                other (nth points (- 1 i))
                kept (if look (follow-look look mine) (follow-stick stick mine))
                r (region dims other)]
            (cond (= prev-n 2) kept
                  (and look (= :look r)) {:pinching :start}
                  (and look (= :stick r)) (merge kept (begin dims other))
                  (and stick (= :look r)) (merge kept (begin dims other))
                  :else kept))

          (and (not= prev-n 2) (or press? (pos? prev-n))) (begin-both dims p q)

          :else {})))))

(defn- look-by
  "UpdateCamera's mouse look: yaw `-dx` then pitch `-dy`, each pixel 0.003
  radians, scaled by the field."
  [c dims [dx dy]]
  (let [k (* mouse-sensitivity (:look-scale dims))]
    (cond-> c
      (not (zero? dx)) (camera-yaw (* -1.0 dx k))
      (not (zero? dy)) (camera-pitch (* -1.0 dy k)))))

(defn stick-dir
  "The unit vector `[dx dy]` of `stick` on the glass, or nil inside the dead
  zone `gesture/slop`."
  [stick metrics]
  (when stick
    (let [vx (- (double (first (:at stick))) (double (first (:centre stick))))
          vy (- (double (second (:at stick))) (double (second (:centre stick))))
          l (Math/sqrt (+ (* vx vx) (* vy vy)))]
      (when (> l (gesture/slop metrics))
        [(/ vx l) (/ vy l)]))))

(defn- walk
  "UpdateCamera's WASD: forward, then right, `move-speed * dt` along the stick."
  [c stick metrics dt]
  (let [speed (* move-speed dt)]
    (if-let [[ux uy] (and (pos? speed) (stick-dir stick metrics))]
      (-> c
          (camera-move-forward (* -1.0 uy speed))
          (camera-move-right (* ux speed)))
      c)))

(defn- dolly
  "A pinch's `ratio` as CameraMoveToTarget: a change of `distance * (1 / ratio -
  1)`, so fingers twice as far apart halve the camera's distance."
  [c ratio]
  (if (= 1.0 ratio)
    c
    (let [distance (length (v- (:position c) (:target c)))]
      (camera-move-to-target c (* distance (- (/ 1.0 ratio) 1.0))))))

(defn advance
  "One frame. Calls `gesture/track` once, for the reset button's tap. The
  fingers are sorted into look, stick and pinch by `track`, then the camera is
  looked, walked and dollied in `UpdateCamera`'s order. A release frame with
  fewer than two points lifts everything, and its position is never read. A
  rotation of the phone drops the tracking, whose pixels are the old screen's.
  A tap on reset sets the target to the origin, unless it ended a multi-finger
  touch."
  [state input]
  (let [metrics (:metrics input)
        dims (geometry metrics)
        screen (:screen metrics)
        state (if (not= screen (:screen state))
                (assoc (dissoc state :look :stick :pinch) :n 0)
                state)
        phase (get-in input [:pointer :phase])
        raw (vec (:touch-points input))
        points (if (and (= :release phase) (< (count raw) 2)) [] raw)
        n (count points)
        prev-n (:n state 0)
        t (track state points dims (= :press phase))
        {now :pinch
         step :step} (when (:pinching t)
                       (cam/pinch-frame (when (= :continue (:pinching t)) (:pinch state)) points))
        [g event] (gesture/track (:gesture state) input)
        reset? (and (= :tap (:type event))
                    (< (max n prev-n) 2)
                    (gesture/in-rect? (:reset dims) (:at event)))
        dt (max 0.0 (double (or (:delta-seconds input) 0.0)))
        c (cond-> (:camera state)
            (:look-delta t) (look-by dims (:look-delta t))
            (:stick t) (walk (:stick t) metrics dt)
            step (dolly (:ratio step))
            reset? (assoc :target [0.0 0.0 0.0]))]
    (assoc state
           :screen screen
           :gesture g
           :n n
           :look (:look t)
           :stick (:stick t)
           :pinch now
           :camera c)))

(defn- init [{:keys [metrics]}]
  [{:camera initial-camera
    :screen (:screen metrics)
    :gesture gesture/idle
    :n 0
    :look nil
    :stick nil
    :pinch nil}
   [[:scene/init :freecam]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :freecam]]])

(defn scene []
  {:id :freecam
   :title "3D Free Camera"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
