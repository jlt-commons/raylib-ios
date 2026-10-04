(ns net.b12n.raylib-ios.camera2d
  "A pure 2D camera, the same transform as raylib's Camera2D.

  A camera is `{:offset [x y] :target [x y] :rotation deg :zoom z}`. A world
  point moves by minus the target, scales by the zoom, rotates by `rotation`
  degrees (x' = x cos - y sin, y' = x sin + y cos, which is raymath's
  MatrixRotateZ) and then moves by the offset. That is `GetCameraMatrix2D` in
  raylib 6.0's rcore.c, so `world->screen` and `screen->world` are
  GetWorldToScreen2D and GetScreenToWorld2D, rotation included. The host draws
  with the same matrix through `net.b12n.raylib-ios.host/with-camera-2d`.

  Every point is an `[x y]` pair and every function takes the camera or the
  points first-to-last in the order its docstring gives. Angles in a camera
  and in `pinch` and `pinch-step` are degrees.")

(defn- rotate [deg [x y]]
  (let [a (Math/toRadians deg)
        c (Math/cos a)
        s (Math/sin a)]
    [(- (* x c) (* y s)) (+ (* x s) (* y c))]))

(defn world->screen
  "The screen position of world point `[x y]` under `camera`."
  [{[ox oy] :offset
    [tx ty] :target
    :keys [rotation zoom]} [x y]]
  (let [[rx ry] (rotate rotation [(* zoom (- x tx)) (* zoom (- y ty))])]
    [(+ ox rx) (+ oy ry)]))

(defn screen->world
  "The world point under screen position `[x y]`, the inverse of
  `world->screen`. The camera's zoom must be positive (it is divided by)."
  [{[ox oy] :offset
    [tx ty] :target
    :keys [rotation zoom]} [x y]]
  (let [[rx ry] (rotate (- rotation) [(- x ox) (- y oy)])]
    [(+ tx (/ rx zoom)) (+ ty (/ ry zoom))]))

(defn pin
  "`camera` re-anchored so that screen point `[sx sy]` is also its target: the
  world point under it is read off first, then the offset becomes the screen
  point and the target becomes that world point. Changing the zoom or rotation
  afterwards then turns the picture about `[sx sy]`, which is the original
  mouse-zoom's three steps."
  [camera screen-point]
  (assoc camera
         :target (screen->world camera screen-point)
         :offset (vec screen-point)))

(defn pinch
  "Two touch points `p` and `q` as `{:mid [x y] :dist d :angle a}`. `:angle` is
  the direction of the line through them in degrees, taken mod 180 into
  [0, 180), so swapping the points changes nothing."
  [[px py] [qx qy]]
  (let [dx (- qx px)
        dy (- qy py)
        deg (Math/toDegrees (Math/atan2 dy dx))
        angle (- deg (* 180.0 (Math/floor (/ deg 180.0))))]
    {:mid [(/ (+ px qx) 2.0) (/ (+ py qy) 2.0)]
     :dist (Math/sqrt (+ (* dx dx) (* dy dy)))
     :angle (if (>= angle 180.0) 0.0 angle)}))

(defn pinch-step
  "What happened between pinch `prev` and pinch `cur` (both from `pinch`):
  `{:ratio r :twist deg :mid m}`. `:ratio` is the change of distance (1.0 when
  either distance is 0, so coincident fingers never collapse a zoom), `:twist`
  the change of angle in degrees wrapped into (-90, 90], and `:mid` is `cur`'s
  midpoint."
  [prev cur]
  (let [d (- (:angle cur) (:angle prev))
        t (- d (* 180.0 (Math/floor (/ (+ d 90.0) 180.0))))
        twist (if (= t -90.0) 90.0 t)]
    {:ratio (if (and (pos? (:dist prev)) (pos? (:dist cur)))
              (/ (:dist cur) (:dist prev))
              1.0)
     :twist twist
     :mid (:mid cur)}))

(defn pinch-frame
  "The one rule every pinching scene follows. `prev` is the `:pinch` the scene
  stored last frame (nil when there was none) and `points` the touch points now.
  Returns `{:pinch now :step step}`: `:pinch` is `pinch` of the two points when
  there are exactly two, else nil, and `:step` is `pinch-step` from `prev` to it
  when both exist, else nil. The scene stores `:pinch` and applies `:step`.

  So a pinch acts only while the finger count stays at two. Any change of count
  (a third finger landing, one of three lifting, a platform reorder of three
  points) leaves `:pinch` nil or freshly recorded, never a step, so the view
  moves only when two fingers that were already down move. Two fingers are
  order-free (`pinch`), but three or more are not, because the platform lists
  them in no promised order and there is no telling which two were the pair."
  [prev points]
  (let [pts (vec points)
        now (when (= 2 (count pts)) (pinch (nth pts 0) (nth pts 1)))]
    {:pinch now
     :step (when (and now prev) (pinch-step prev now))}))
