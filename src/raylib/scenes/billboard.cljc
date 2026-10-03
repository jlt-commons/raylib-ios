(ns raylib.scenes.billboard
  "Two camera-facing billboards over a grid, one of them spinning, with the
  camera going round them, ported from raylib-jlt's `billboard_rendering` (zlib
  licence, after raylib's models_billboard_rendering). The projection is in
  software, by `raylib.soft3d`, whose `billboard` builds each quad from the same
  corner maths as rmodels.c's DrawBillboardPro.

  The original (billboard_rendering.clj) keeps its camera at `ORBIT-R` 7.07
  from the y axis and 4 up, looking at (0, 2, 0), fovy 45, up (0, 1, 0) (lines
  24 and 105-106, and `with-camera-3d` at 130-133). The angle starts at 0.8 and
  gains 0.5 times the frame time a frame (line 103), and the spin gains 0.4
  degrees a frame with no frame time (line 104). A static billboard of side 2 sits
  at (0, 2, 0) and a spinning one at (1, 2, 1) (lines 25-26 and 115-122), over a
  `DrawGrid(10, 1)` (line 110). The farther of the two, by the squared distance of
  its centre from the camera, is drawn first (lines 107-108 and 123-125). Here the
  frame time is the update's `:delta-seconds`.

  The corners are the original's: `draw-billboard` (lines 61-88) builds
  `center +/- right h +/- up h` with `right` from the camera's forward vector and
  `up` from `right`, which are the camera's own right and up, and the spin is a
  Rodrigues turn of both about the forward vector (lines 47-54 and 69-71).
  `raylib.soft3d/billboard-corners` is that quad. Its turn is about the quad's
  normal, `right` x `up`, which points at the camera where the original's
  forward points away, so the same spin is the other sign there (`rotation`).
  A test compares the corners with a copy of the original's `draw-billboard` at
  every sampled frame. One real difference: the original's `right` is
  `cross(world-up, forward)`, which is the camera's LEFT, so its quad is a
  mirror of the image on the texture. raylib's DrawBillboardPro, which the
  corners follow, is not mirrored. The atlas here is symmetric, so nothing shows.

  The atlas (lines 28-36) is a 64 by 64 texture: yellow (253, 249, 0) inside a
  radius of 0.35 of half the width, red (230, 41, 55) inside 0.7, blue (0, 121,
  241) elsewhere, corners included. There is no texture here, so it is redrawn as
  three flat squares of the circles' diameters, blue the whole billboard, red 0.7
  of its side and yellow 0.35 of it (`ring-parts`), painted in that order as
  `raylib.soft3d/billboard`'s `:part`s. The squares are larger than the circles
  they replace, by the corners, and they visibly turn with the spin where the
  original's circles show no turning at all (only its blue square's edge does).
  Discs built of strips through `:part` measured 0.62 ms a frame on the laptop,
  over the 0.45 ms budget, so they are not used.

  Each billboard's parts are coplanar and painted back to front, and the two
  billboards are on parallel planes, both perpendicular to the view axis, so
  painting them by depth along that axis, farther first, is exact and there is
  no `raylib.soft3d/finish`: the grid goes first, then the farther billboard, then
  the nearer. This is not the original's rule, which sorts by the distance of the
  centre from the camera and is wrong for about 5.7 degrees of each lap
  (see `paint-order`); the original's depth buffer hides that. The grid is always
  behind them, because the camera is above the grid and above the billboards.

  Controls: the original reads no input (it has no key), so none is mapped. The
  one text line (line 135) sits below Back, not at (10, 40), with the 3D view in
  the field under it, full width to the bottom, and `raylib.soft3d/fit-camera`
  widens the 45 degree fovy in a field narrower than 800x450, so the horizontal
  view still fits.

  The state holds `:angle`, `:spin` and `:frame`. Colours are `[r g b a]` vectors."
  (:require [raylib.soft3d :as s3]))

(def caption-text "The camera orbits; the right billboard also spins")

(def background-colour "RAYWHITE." [245 245 245 255])
(def caption-colour "GRAY." [130 130 130 255])
(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

(def orbit-radius "The camera's distance from the y axis, the original's `ORBIT-R`." 7.07)
(def static-pos "The flat-on billboard's centre, the original's `STATIC-POS`." [0.0 2.0 0.0])
(def spin-pos "The spinning billboard's centre, the original's `SPIN-POS`." [1.0 2.0 1.0])
(def billboard-size "The side of each billboard." 2.0)
(def start-angle "The camera's angle, in radians, at the start." 0.8)
(def angle-rate "Radians a second the camera goes round." 0.5)
(def spin-rate "Degrees a frame the spinning billboard turns." 0.4)

(def ^:private blue [0 121 241 255])
(def ^:private red [230 41 55 255])
(def ^:private yellow [253 249 0 255])

(def ^:private ring
  "The atlas redrawn: `[colour part]` for the blue square, the red and the
  yellow, in painting order. A part is `raylib.soft3d/billboard`'s
  `[fx0 fy0 fx1 fy1]`, from the quad's bottom-left."
  [[blue [0.0 0.0 1.0 1.0]]
   [red [0.15 0.15 0.85 0.85]]
   [yellow [0.325 0.325 0.675 0.675]]])

(defn ring-parts "The ring as `[colour part]` pairs in painting order." [] ring)

(defn dimensions
  "`raylib.soft3d/field` plus the caption as `{:s :x :y :size}`, in `:lines` as
  well so a test can check it fits. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [size pad text-y]
         :as field} (s3/field metrics (measure caption-text 100))
        line {:s caption-text
              :x pad
              :y text-y
              :size size}]
    (assoc field :caption line :lines [line])))

(defn camera
  "The original's camera for `state`: on the orbit at `:angle`, 4 up, looking at
  (0, 2, 0) with fovy 45, fitted to `dims`' field by `raylib.soft3d/fit-camera`."
  [state dims]
  (let [a (:angle state)]
    (s3/fit-camera {:position [(* orbit-radius (Math/cos a)) 4.0 (* orbit-radius (Math/sin a))]
                    :target [0.0 2.0 0.0]
                    :up [0.0 1.0 0.0]
                    :fovy 45.0
                    :projection :perspective}
                   original-aspect (:aspect dims))))

(defn rotation
  "The `:rotation` `raylib.soft3d/billboard` takes for the spinning billboard at
  `state`'s spin: the original turns about the forward vector, which points away
  from the camera, and `billboard` turns about the normal toward it."
  [state]
  (- (double (:spin state))))

(defn paint-order
  "`[:static :spin]` or `[:spin :static]`: the farther first. The billboards are
  planes perpendicular to the view axis `target - eye`, so how far one is
  is its centre's depth along that axis, `(c - eye) . (target - eye)`. The
  original orders by the squared distance of the centre from the eye (lines
  107-125), which a depth buffer makes harmless there, and which disagrees with
  the depth for about 5.7 degrees of each lap, where the spinning billboard is
  nearer along the axis but farther away. Painting by that would cover it."
  [[ex ey ez] [tx ty tz]]
  (let [fx (- tx ex) fy (- ty ey) fz (- tz ez)
        depth (fn [[x y z]] (+ (* (- x ex) fx) (* (- y ey) fy) (* (- z ez) fz)))]
    (if (> (depth static-pos) (depth spin-pos)) [:static :spin] [:spin :static])))

(defn- emit-ring [dl vp pos opts]
  (reduce (fn [dl [colour part]]
            (s3/billboard dl vp pos billboard-size colour (assoc opts :part part)))
          dl ring))

(defn scene-list
  "The draw list for `state`: the grid, then the farther billboard and the nearer,
  each as its three squares. Not sorted by `raylib.soft3d/finish`; see the ns
  docstring."
  [state dims]
  (let [cam (camera state dims)
        vp (s3/view-proj cam (:viewport dims))]
    (reduce (fn [dl which]
              (if (= which :static)
                (emit-ring dl vp static-pos {})
                (emit-ring dl vp spin-pos {:rotation (rotation state)})))
            (s3/grid [] vp 10 1.0)
            (paint-order (:position cam) (:target cam)))))

(defn advance
  "One frame: the camera goes round by 0.5 radians a second of the update's
  `:delta-seconds`, and the spin gains 0.4 degrees."
  [state input]
  (let [dt (max 0.0 (double (or (:delta-seconds input) 0.0)))]
    (assoc state
           :angle (+ (:angle state) (* angle-rate dt))
           :spin (+ (:spin state) spin-rate)
           :frame (inc (:frame state)))))

(defn- init [_]
  [{:angle start-angle
    :spin 0.0
    :frame 0}
   [[:scene/init :billboard]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :billboard]]])

(defn scene []
  {:id :billboard
   :title "Billboard Rendering"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
