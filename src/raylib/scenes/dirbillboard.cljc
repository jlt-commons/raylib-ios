(ns raylib.scenes.dirbillboard
  "A walking figure on a camera-facing billboard, whose pose changes as the
  camera goes round it, ported from raylib-jlt's `directional_billboard` (zlib
  licence, after raylib's models_directional_billboard). The projection is in
  software, by `raylib.soft3d`.

  The original (directional_billboard.clj) keeps its camera on a circle of
  radius `RXZ` 2.828 (the square root of 8) at y = 1, looking at (0, 0.5, 0),
  fovy 45 (lines 28 and 125-137). The angle `theta` starts at pi / 4 and gains
  0.5 times the frame time a frame (lines 114 and 120). The sheet is 8 rows, the
  facing direction, by 4 columns, the walk frame. The row is chosen from the
  camera's own angle (lines 127-128): `dir0 = floor(atan2(z, x) / pi * 4 +
  1/4)`, and `dir` is `dir0` or, when that is negative, `8 + dir0`. `direction`
  is that line for line, and a test compares it with a copy of it at about 2400
  angles, and with hand-computed edges. The column advances when a timer passes
  0.5 s (lines 121-124): the timer gains the frame time, and past 0.5 the walk
  frame steps (mod 4) and the timer starts over, dropping the excess. A
  grid of 10 lies under it (line 139) and the figure is a billboard of side 1 at
  (0, 0.5, 0) (lines 140-147). Here the frame time is the update's
  `:delta-seconds`.

  The sheet (lines 49-69) is a 24 pixel cell per pose, drawn by `sheet-pixel`:
  a round head of radius 5 at (12, 8) in value 1, a body at x 9 to 14 and y 13
  to 19 in value 0.7, and two legs at y 20 to 22 in value 0.5, at x `10 + o`
  to `11 + o` and `12 - o` to `13 - o` where the offset `o` of the column is 0,
  2, 0, -2 (line 47), all of hue 45 degrees a row, saturation 0.7, and
  transparent elsewhere. There is no texture here, so each pose is redrawn as
  four flat rectangles, `cell-rects`, in the same cell: the two legs, the body, and
  a head that is the square of the circle's diameter (9 pixels, from 8 to 16 in
  each direction) in place of the circle. The legs and the body are the original's
  pixels exactly, and a test paints them over a cell and compares all 576 pixels
  of all 32 poses with the original's rule. The head's square holds its circle,
  with the corners left over as the one thing that looks different. Each
  rectangle is a part of the billboard (`cell->part`), so the eight rows are eight
  colours and the four columns are four leg positions.

  One mirror. The original's quad takes its right from `cross(world-up,
  forward)`, which is the camera's LEFT, and puts the sheet's u = 0 edge there.
  So the figure on its screen is the sheet left to right reversed, and this scene
  keeps that: `cell->part` flips x, so a leg that is at the left of the cell is at
  the right of the picture, as the original draws it. A test builds the
  original's texel positions and compares them with the corners of each part.

  The rectangles are coplanar and painted legs, body, head, so painter order is
  exact; there is no `raylib.soft3d/finish`. The grid goes first. The camera is 1
  up and the billboard stands on the grid, so the grid is behind the figure
  wherever the two overlap on the screen.

  Controls: the original reads no input, so none is mapped. Its two text lines
  (\"animation: N\" and \"direction frame: N\", at (10, 10) and (10, 40), size 20)
  become one, \"animation: N  direction frame: N\", below Back; the 3D view fills
  the field under it, full width to the bottom, and `raylib.soft3d/fit-camera`
  widens the 45 degree fovy in a field narrower than 800x450.

  The state holds `:theta`, `:anim`, `:timer` and `:frame`. Colours are
  `[r g b a]` vectors."
  (:require [raylib.soft3d :as s3]))

(def background-colour "RAYWHITE." [245 245 245 255])
(def caption-colour "DARKGRAY." [80 80 80 255])
(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

(def cell "The side of one pose in the original's sheet, in pixels." 24)
(def columns "Walk frames." 4)
(def orbit-radius "The camera's distance from the y axis, the original's `RXZ`." 2.8284271247461903)
(def centre "The billboard's centre." [0.0 0.5 0.0])
(def billboard-size "The side of the billboard." 1.0)
(def start-theta "The camera's angle, in radians, at the start." (/ Math/PI 4.0))
(def theta-rate "Radians a second the camera goes round." 0.5)
(def tick-after "Seconds of the timer past which the walk frame steps." 0.5)
(def ^:private leg-offsets [0 2 0 -2])

(defn hsv->colour
  "The original's `hsv->color` (lines 30-45): `[r g b 255]` for hue `h` degrees,
  saturation `s` and value `v`, each channel truncated from 0..255."
  [h s v]
  (let [h' (/ (mod h 360.0) 60.0)
        i (int (Math/floor h'))
        f (- h' i)
        p (* v (- 1.0 s))
        q (* v (- 1.0 (* s f)))
        t (* v (- 1.0 (* s (- 1.0 f))))
        [r g b] (case (mod i 6)
                  0 [v t p]
                  1 [q v p]
                  2 [p v t]
                  3 [p q v]
                  4 [t p v]
                  5 [v p q])]
    [(int (* 255 r)) (int (* 255 g)) (int (* 255 b)) 255]))

(defn direction
  "The sheet row for a camera at angle `theta`: lines 125-128 of the original."
  [theta]
  (let [px (* orbit-radius (Math/cos theta))
        pz (* orbit-radius (Math/sin theta))
        dir0 (Math/floor (+ (* (/ (Math/atan2 pz px) Math/PI) 4.0) 0.25))]
    (int (if (< dir0 0.0) (+ 8.0 dir0) dir0))))

(defn dir "The sheet row `state` shows." [state] (direction (:theta state)))

(defn cell-rects
  "The pose at row `dir`, column `anim`, as `[x0 y0 x1 y1 colour]` rectangles of
  the 24 pixel cell, half-open in pixels with y down, in painting order: the
  second leg, the first, the body and the head. See the ns docstring."
  [dir anim]
  (let [o (nth leg-offsets (mod anim (count leg-offsets)))
        hue (* dir 45.0)]
    [[(- 12 o) 20 (- 14 o) 23 (hsv->colour hue 0.7 0.5)]
     [(+ 10 o) 20 (+ 12 o) 23 (hsv->colour hue 0.7 0.5)]
     [9 13 15 20 (hsv->colour hue 0.7 0.7)]
     [8 4 17 13 (hsv->colour hue 0.7 1.0)]]))

(defn cell->part
  "A cell rectangle `[x0 y0 x1 y1]` (pixels, y down) as the `:part`
  `[fx0 fy0 fx1 fy1]` of `raylib.soft3d/billboard` that the original's quad shows
  it at: x reversed, as the ns docstring says, and y up."
  [[x0 y0 x1 y1]]
  (let [c (double cell)]
    [(/ (- c x1) c) (- 1.0 (/ y1 c)) (/ (- c x0) c) (- 1.0 (/ y0 c))]))

(defn dimensions
  "`raylib.soft3d/field` for `metrics`, sized for the widest caption, the
  `:lines` a test can check, and `:caption`, the line's position and size.
  `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [size pad text-y]
         :as field} (s3/field metrics (measure "animation: 3  direction frame: 7" 100))
        line {:s ""
              :x pad
              :y text-y
              :size size}]
    (assoc field :caption line :lines [line])))

(defn caption
  "The caption as `{:s :x :y :size}` for `state`: the walk frame and the row."
  [state dims]
  (assoc (:caption dims) :s (str "animation: " (:anim state) "  direction frame: " (dir state))))

(defn camera
  "The original's camera for `state`: on the circle at `:theta`, 1 up, looking at
  (0, 0.5, 0) with fovy 45, fitted to `dims`' field by `raylib.soft3d/fit-camera`."
  [state dims]
  (let [t (:theta state)]
    (s3/fit-camera {:position [(* orbit-radius (Math/cos t)) 1.0 (* orbit-radius (Math/sin t))]
                    :target [0.0 0.5 0.0]
                    :up [0.0 1.0 0.0]
                    :fovy 45.0
                    :projection :perspective}
                   original-aspect (:aspect dims))))

(defn scene-list
  "The draw list for `state`: the grid, then the pose's four rectangles."
  [state dims]
  (let [vp (s3/view-proj (camera state dims) (:viewport dims))]
    (reduce (fn [dl [x0 y0 x1 y1 colour]]
              (s3/billboard dl vp centre billboard-size colour {:part (cell->part [x0 y0 x1 y1])}))
            (s3/grid [] vp 10 1.0)
            (cell-rects (dir state) (:anim state)))))

(defn advance
  "One frame: the camera goes round 0.5 radians a second of the update's
  `:delta-seconds`, and the walk timer gains it, stepping the walk frame once it
  is past 0.5 s."
  [state input]
  (let [dt (max 0.0 (double (or (:delta-seconds input) 0.0)))
        t2 (+ (:timer state) dt)
        tick? (> t2 tick-after)]
    (assoc state
           :theta (+ (:theta state) (* theta-rate dt))
           :anim (if tick? (mod (inc (:anim state)) columns) (:anim state))
           :timer (if tick? 0.0 t2)
           :frame (inc (:frame state)))))

(defn- init [_]
  [{:theta start-theta
    :anim 0
    :timer 0.0
    :frame 0}
   [[:scene/init :dirbillboard]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :dirbillboard]]])

(defn scene []
  {:id :dirbillboard
   :title "Directional Billboard"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
