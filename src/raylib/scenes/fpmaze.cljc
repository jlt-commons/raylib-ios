(ns raylib.scenes.fpmaze
  "A first-person walk through a grid maze, with a minimap, on two thumbs,
  ported from raylib-jlt's `first_person_maze` (zlib licence).

  The maze is the original's 16 by 16 vector of strings (lines 20-37), which is
  also its collision map. Each `#` is a wall cube of CELL 4 by 3 by 4 at
  `((cx + 0.5) * CELL, 1.5, (cy + 0.5) * CELL)`, tinted as the original does
  (lines 54-67) with a checker, (120, 130, 160) where `cx + cy` is even and
  (95, 105, 135) where it is odd, then shaded face by face by
  `raylib.soft3d/cube`'s default, which is `cube!`'s. A grid of 40 slices of
  CELL is drawn first, on a clear colour of (16, 18, 26), from an eye 1.6 up
  looking one unit along the heading, `fovy` 68 (lines 130-143). The
  projection is in software, by `raylib.soft3d`, so the state holds numbers
  only and the draw method builds the draw list.

  The original builds its own camera and does not call `UpdateCamera`, and
  neither does this. The heading is an angle about the vertical and forward is
  `(sin h, cos h)`, so heading 0 looks down +z (line 119: `target = pos +
  (sinh, cosh)`). Movement is the original's too. A step is `speed = 5 * dt`
  (line 122) with `dx = speed * (fwd * sinh + strafe * cosh)` and `dz = speed *
  (fwd * cosh - strafe * sinh)` (lines 125-126). It is resolved per axis, x
  first, so a diagonal into a wall keeps the component that is free and slides
  (lines 129-131): `blocked?` tests the four corners of a box of RADIUS 0.9
  (lines 45-52), and a blocked move is dropped, not clamped to the wall. The
  start is `(1.5 CELL, 1.5 CELL)` at heading 0. `dt` is the update's
  `:delta-seconds`, as the original's `GetFrameTime`.

  The original's keys, and what stands in for each:
  - W and S walk forward and back, A and D strafe, and LEFT and RIGHT turn at
    2.2 radians a second. A relative thumb-stick that starts in the lower third
    of the field replaces W, S, A and D. The press point is its centre. Further
    than `gesture/slop` from it, the player moves that way (up the glass is
    forward) at `5 * dt`, the direction a unit vector, so one speed everywhere.
    The original's keys add, which makes a diagonal 1.41 times faster, and that
    is a deliberate difference. A drag that starts in the upper two thirds of
    the field replaces LEFT and RIGHT. Only its horizontal motion turns, as the
    original has no pitch. A drag right turns right, as RIGHT does, and it turns
    0.004 radians a pixel, times `800 / field width`. That rate is borrowed from
    `raylib.scenes.fpcamera`, because a key has no pixel rate to port.
  - D adds +x at heading 0. The camera there looks down +z, where +x is the
    left of the glass (probed with `raylib.soft3d/project`: the point one unit
    along +x projects left of centre), and A adds -x, the right. So the
    original's D is a strafe to the left of the glass and its A one to the
    right, and so is the LEFT key's turn, which raises the heading toward +x.
    The stick maps by what shows on the glass: pushed right it strafes right,
    which is the original's A key's effect. A drag follows the same side.
  - Both work at once, each by its own finger.

  `raylib.stick` decides whose finger it is, as in `raylib.scenes.fpcamera`
  (whose finger tracking this copies, since it is private there): a stick or a
  turn begins only on a finger that was not down the frame before, inside its
  region, follows it by touch id, and ends when it lifts. A rotation of the
  phone drops both. A tap moves nothing.

  The minimap is the original's (lines 70-98), in the top right of the 3D field,
  drawn after the 3D draw list. A black panel of alpha 170, one rect for each
  wall in (150, 160, 190), a RED circle of radius 4 at the player's cell
  position and a GOLD line of 12 from it along `(sin h, cos h)`. The original's
  800 pixel window has a cell of 9, a margin of 4 round the panel and a gap of
  1 between wall rects. Here the cell `s` is sized in `dimensions` so the panel
  is at most 0.3 of the field's width and 0.4 of its height, and the 4, the 4,
  the 12 and the 1 scale by `s / 9`.

  Dropped: the title text (the gallery shows the scene's title) and the
  key text, \"W/S walk A/D strafe LEFT/RIGHT turn\", which becomes a caption
  below Back for the touch controls, sized in `dimensions` so it fits.

  Faces with a corner behind the near plane are dropped whole, a known limit of
  `raylib.soft3d`, so standing against a wall makes its face vanish rather than
  clip. The grid is drawn under every face (`raylib.soft3d/finish` puts all
  grid lines first), so a grid line that a depth buffer would show over a wall's
  foot is hidden here. Adjacent walls are ordered whole, face by face, so where
  two cross a face can paint over one a depth buffer would put in front. Both
  are limits of the painter, not chased. The original's 68 degree fovy is kept
  while the field is at least as wide as 800x450, and widened by
  `raylib.soft3d/fit-camera` in a narrower one.

  The state holds `:px`, `:pz` and `:heading` (numbers), the finger tracking
  (`:look` and `:stick`, each with its finger's id), `:n`, `:pts` and `:ids` (the
  finger count, touch points and ids of last frame) and `:screen`. Colours are
  `[r g b a]` vectors."
  (:require [raylib.scenes.freecam :as free]
            [raylib.soft3d :as s3]
            [raylib.stick :as stick]))

(def maze
  "The original's maze (lines 20-37): `#` is a wall."
  ["################"
   "#..............#"
   "#.####.#####.#.#"
   "#.#....#...#.#.#"
   "#.#.####.#.#.#.#"
   "#.#.#....#...#.#"
   "#...#.######.#.#"
   "###.#......#.#.#"
   "#...####.#.#.#.#"
   "#.####...#...#.#"
   "#....#.###.###.#"
   "####.#.#.....#.#"
   "#....#.#.#####.#"
   "#.####...#.....#"
   "#..............#"
   "################"])

(def cell "The original's CELL: a wall's footprint, in world units." 4.0)
(def radius "The original's RADIUS: how close to a wall the player may stand." 0.9)
(def move-speed "The original's `5.0 * dt`: world units a second." 5.0)
(def eye-height "The original's camera y." 1.6)
(def fovy "The original's camera fovy." 68.0)
(def wall-height "The original's cube height." 3.0)
(def sensitivity "Radians a pixel of drag in an 800 pixel window, as fpcamera's SENS." 0.004)
(def original-width "The original's window width, in pixels." 800.0)
(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

(def background-colour "The original's clear colour." [16 18 26 255])
(def caption-colour "GRAY, as raylib defines it." [130 130 130 255])
(def caption-text "drag low to walk, high to turn")
(def wall-even "The checker tint where cx + cy is even." [120 130 160 255])
(def wall-odd "The checker tint where cx + cy is odd." [95 105 135 255])
(def panel-colour "The minimap's panel (line 74)." [0 0 0 170])
(def map-wall-colour "A wall on the minimap (line 85)." [150 160 190 255])
(def marker-colour "RED, as raylib defines it." [230 41 55 255])
(def heading-colour "GOLD, as raylib defines it." [255 203 0 255])

(def ^:private rows (count maze))
(def ^:private cols (count (first maze)))

(defn wall?
  "Is the cell `cx`, `cy` (column, row) a wall? Anything off the map is (lines
  40-43)."
  [cx cy]
  (or (< cx 0) (< cy 0) (>= cx cols) (>= cy rows)
      (= \# (nth (nth maze cy) cx))))

(defn blocked?
  "Is world position `x`, `z` inside a wall, allowing for the player's radius?
  Tests the four corners of the player's box rather than its centre, which
  stops it clipping a corner diagonally (lines 45-56)."
  [x z]
  (boolean
   (some (fn [[dx dz]]
           (wall? (long (Math/floor (/ (+ x dx) cell)))
                  (long (Math/floor (/ (+ z dz) cell)))))
         [[(- radius) (- radius)] [radius (- radius)]
          [(- radius) radius] [radius radius]])))

(defn slide
  "The player's position after a step of `dx`, `dz` from `px`, `pz`, each axis
  on its own, x first, so a diagonal into a wall keeps the free component
  (lines 129-131). A blocked axis stays where it was."
  [px pz dx dz]
  (let [nx (if (blocked? (+ px dx) pz) px (+ px dx))
        nz (if (blocked? nx (+ pz dz)) pz (+ pz dz))]
    [nx nz]))

(defn camera-of
  "The camera for a player at `px`, `pz` with `heading`, as the original builds
  it each frame (lines 135-143): the eye 1.6 up, the target one unit along
  `(sin h, cos h)` at the same height, up (0, 1, 0), fovy 68, perspective."
  [px pz heading]
  {:position [px eye-height pz]
   :target [(+ px (Math/sin heading)) eye-height (+ pz (Math/cos heading))]
   :up [0.0 1.0 0.0]
   :fovy fovy
   :projection :perspective})

(def start-position "The original's start: the middle of cell (1, 1)." (* 1.5 cell))

;; --- layout -------------------------------------------------------------------

(defn geometry
  "`raylib.scenes.freecam/region`'s layout for `metrics`' `:screen`:
  `raylib.soft3d/field` plus `:stick-top`, the y where the lower third of the
  field starts. Above it a touch turns, from it down a touch walks. With
  `widest`, the caption's width at size 100, the size is cut back as `field`
  does."
  ([metrics] (geometry metrics nil))
  ([metrics widest]
   (let [{[_ fy _ fh] :viewport
          :as field} (s3/field metrics widest)]
     (assoc field :stick-top (+ fy (* (/ 2.0 3.0) fh))))))

(defn- minimap-box
  "The minimap's panel for `geo`: `{:cell-px :x :y :w :h}`. The cell is the
  largest that keeps the panel (`cols * s + 8 * s / 9` square) within 0.3 of
  the field's width and 0.4 of its height, and the panel sits in the field's
  top right, `pad` in from each edge."
  [{[fx fy fw fh] :viewport
    :keys [pad]}]
  (let [per-cell (+ cols (/ 8.0 9.0))
        s (/ (min (* 0.3 fw) (* 0.4 fh)) per-cell)
        side (* s per-cell)]
    {:cell-px s
     :x (- (+ fx fw) pad side)
     :y (+ fy pad)
     :w side
     :h (* s (+ rows (/ 8.0 9.0)))}))

(defn dimensions
  "`geometry` plus the caption as `{:s :x :y :size}`, in `:caption`, and in
  `:lines` as well so a test can check it fits, and the minimap's panel `:map`
  `{:x :y :w :h}` with its cell `:cell-px`. The caption's size is cut back, to 8
  at the least, so that it covers no more than 0.92 of the width. `measure` is
  `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [pad text-y size]
         :as geo} (geometry metrics (measure caption-text 100))
        line {:s caption-text
              :x pad
              :y text-y
              :size size}
        {:keys [cell-px]
         :as box} (minimap-box geo)]
    (assoc geo
           :caption line
           :lines [line]
           :cell-px cell-px
           :map (select-keys box [:x :y :w :h]))))

;; --- the picture --------------------------------------------------------------

(def walls
  "Every wall as `{:x :z :colour}`, the cube's centre on the ground plane and its
  checker tint (lines 54-67), in the original's row-then-column order."
  (vec (for [cy (range rows)
             cx (range cols)
             :when (wall? cx cy)]
         {:x (* (+ cx 0.5) cell)
          :z (* (+ cy 0.5) cell)
          :colour (if (even? (+ cx cy)) wall-even wall-odd)})))

(defn camera
  "`state`'s camera fitted to `dims`' field by `raylib.soft3d/fit-camera`."
  [state dims]
  (s3/fit-camera (camera-of (:px state) (:pz state) (:heading state))
                 original-aspect (:aspect dims)))

(def ^:private wall-reach
  "The farthest a point of a wall can be from its centre on the ground: half the
  diagonal of a CELL square."
  (* (Math/sqrt 2.0) 0.5 cell))

(defn visible-walls
  "The walls that can reach the glass from `state`'s camera in `dims`' field,
  from `walls`. A wall is left out only when the circle of `wall-reach` round
  its centre lies wholly behind the eye or wholly outside the left or right
  plane of the view (half-angle `atan (tan (fovy / 2) * aspect)`), so none of
  its faces that the screen could show is lost, and what `raylib.soft3d/cube`
  would project off the sides of the field is not built. The height is not
  tested: the eye is inside the wall's height range."
  [state dims]
  (let [{:keys [px pz heading]} state
        tan-half (* (Math/tan (Math/toRadians (* 0.5 (:fovy (camera state dims))))) (:aspect dims))
        fx (Math/sin heading)
        fz (Math/cos heading)]
    (filterv (fn [{:keys [x z]}]
               (let [dx (- x px)
                     dz (- z pz)
                     along (+ (* dx fx) (* dz fz))
                     across (abs (- (* dx fz) (* dz fx)))]
                 (and (> (+ along wall-reach) 0.0)
                      (<= (- across wall-reach) (* (+ along wall-reach) tan-half)))))
             walls)))

(defn scene-list
  "The finished draw list for `state`: the grid of 40 slices of CELL, then each
  of `visible-walls` as `cube!` draws it, size CELL by 3 by CELL with its centre
  1.5 up."
  [state dims]
  (let [vp (s3/view-proj (camera state dims) (:viewport dims))]
    (s3/finish
     (reduce (fn [dl {:keys [x z colour]}]
               (s3/cube dl vp nil [x (/ wall-height 2.0) z] [cell wall-height cell] colour))
             (s3/grid [] vp 40 cell)
             (visible-walls state dims)))))

;; --- the minimap --------------------------------------------------------------

(defn minimap-static
  "The minimap's panel and walls as `[:rect x y w h colour]` items, for `dims`:
  the panel at `(ox - 4, oy - 4)` and `cols * s + 8` square, then a rect `s - 1`
  square for each wall at `(ox + cx * s, oy + cy * s)` (lines 74-87), every
  length scaled by `s / 9`."
  [dims]
  (let [{:keys [x y w h]} (:map dims)
        s (:cell-px dims)
        k (/ s 9.0)
        ox (+ x (* 4 k))
        oy (+ y (* 4 k))]
    (into [[:rect x y w h panel-colour]]
          (for [cy (range rows)
                cx (range cols)
                :when (wall? cx cy)]
            [:rect (+ ox (* cx s)) (+ oy (* cy s)) (- s k) (- s k) map-wall-colour]))))

(defn minimap-player
  "The minimap's player for `state` and `dims`: a `[:circle x y r colour]` of
  radius 4 scaled, at `(ox + s * px / CELL, oy + s * pz / CELL)` truncated, and
  a `[:line x1 y1 x2 y2 colour]` from it to the unrounded position plus `12 *
  (sin h, cos h)` scaled, truncated (lines 88-98)."
  [state dims]
  (let [{:keys [x y]} (:map dims)
        s (:cell-px dims)
        k (/ s 9.0)
        mx (+ x (* 4 k) (* s (/ (:px state) cell)))
        my (+ y (* 4 k) (* s (/ (:pz state) cell)))
        h (:heading state)]
    [[:circle (long mx) (long my) (* 4 k) marker-colour]
     [:line (long mx) (long my)
      (long (+ mx (* 12 k (Math/sin h)))) (long (+ my (* 12 k (Math/cos h))))
      heading-colour]]))

(defn minimap
  "The whole minimap for `state`: `minimap-static` then `minimap-player`."
  [dims state]
  (into (minimap-static dims) (minimap-player state dims)))

;; --- fingers --------------------------------------------------------------------

(defn- d2 [[ax ay] [bx by]]
  (let [dx (- (double ax) (double bx))
        dy (- (double ay) (double by))]
    (+ (* dx dx) (* dy dy))))

(defn- follow-both
  "`look` and `stick` (either may be nil) each moved to its own finger by
  `raylib.stick/follow`, as `[look' stick']`. Without ids both could claim the
  one nearest point. Then the owner that moved less keeps it and the other
  follows from what is left. As in `raylib.scenes.fpcamera`."
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
  "`{:look :stick}` with a turn or a stick begun on each fresh finger (a finger
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

(defn- turn-by
  "The heading after a drag of `dx` pixels: LEFT adds and RIGHT subtracts (lines
  113-115), so a drag right subtracts, at `sensitivity` a pixel times `800 /
  field width`."
  [heading dims dx]
  (- heading (* sensitivity (/ original-width (nth (:viewport dims) 2)) dx)))

(defn- walk
  "The original's step (lines 117-131) along the stick's unit direction
  (`raylib.scenes.freecam/stick-dir`): `fwd` is up the glass, `strafe` is
  the original's D, which is the left of the glass, so the glass's right is
  `-1`. Then `slide` resolves it."
  [{:keys [px pz heading]
    :as state} stick metrics dt]
  (if-let [[ux uy] (free/stick-dir stick metrics)]
    (let [fwd (- uy)
          strafe (- ux)
          speed (* move-speed dt)
          sinh (Math/sin heading)
          cosh (Math/cos heading)
          [nx nz] (slide px pz
                         (* speed (+ (* fwd sinh) (* strafe cosh)))
                         (* speed (- (* fwd cosh) (* strafe sinh))))]
      (assoc state :px nx :pz nz))
    state))

(defn advance
  "One frame. Fingers are sorted into a turn and a stick by `raylib.stick`, then
  the player is turned and walked in the original's order. A release frame with
  fewer than two points lifts everything, and its position is never read. A
  rotation of the phone drops the tracking, whose pixels are the old screen's."
  [state input]
  (let [metrics (:metrics input)
        dims (geometry metrics)
        dt (double (or (:delta-seconds input) 0.0))
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
        dx (when (and look (:look state))
             (- (double (first (:at look))) (double (first (:at (:look state))))))
        turned (cond-> state
                 dx (update :heading turn-by dims dx))
        moved (cond-> turned
                stick' (walk stick' metrics dt))]
    (assoc state
           :screen screen
           :n (count points)
           :pts points
           :ids ids
           :look look'
           :stick stick'
           :px (:px moved)
           :pz (:pz moved)
           :heading (:heading moved))))

(defn- init [{:keys [metrics]}]
  [{:px start-position
    :pz start-position
    :heading 0.0
    :screen (:screen metrics)
    :n 0
    :pts []
    :ids nil
    :look nil
    :stick nil}
   [[:scene/init :fpmaze]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :fpmaze]]])

(defn scene []
  {:id :fpmaze
   :title "First-Person Maze"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
