(ns raylib.scenes.fpmaze
  "A first-person walk through a grid maze, with a minimap, on two thumbs,
  ported from raylib-jlt's `first_person_maze` (zlib licence).

  The maze is the original's 16 by 16 vector of strings (lines 22-38), which is
  also its collision map. Each `#` is a wall cube of CELL 4 by 3 by 4 at
  `((cx + 0.5) * CELL, 1.5, (cy + 0.5) * CELL)`, tinted as the original does
  (lines 64-73) with a checker, (120, 130, 160) where `cx + cy` is even and
  (95, 105, 135) where it is odd, then shaded face by face by
  `raylib.soft3d/cube`'s default, which is `cube!`'s. A grid of 40 slices of
  CELL is drawn first, on a clear colour of (16, 18, 26), from an eye 1.6 up
  looking one unit along the heading, `fovy` 68 (lines 133-140). The
  projection is in software, by `raylib.soft3d`, so the state holds numbers
  only and the draw method builds the draw list.

  The original builds its own camera and does not call `UpdateCamera`, and
  neither does this. The heading is an angle about the vertical and forward is
  `(sin h, cos h)`, so heading 0 looks down +z (lines 137-139: `target = pos +
  (sinh, cosh)`). Movement is the original's too. A step is `speed = 5 * dt`
  (line 122) with `dx = speed * (fwd * sinh + strafe * cosh)` and `dz = speed *
  (fwd * cosh - strafe * sinh)` (lines 125-126). It is resolved per axis, x
  first, so a diagonal into a wall keeps the component that is free and slides
  (lines 129-131): `blocked?` tests the four corners of a box of RADIUS 0.9
  (lines 48-57), and a blocked move is dropped, not clamped to the wall. The
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
  (`stick/begin-owners` and `stick/follow-pair` are shared): a stick or a
  turn begins only on a finger that was not down the frame before, inside its
  region, follows it by touch id, and ends when it lifts. A rotation of the
  phone drops both. A tap moves nothing.

  The minimap is the original's (lines 75-103), in the top right of the 3D field,
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

  Walls that cannot show are not built. `frustum-walls` leaves out those
  wholly behind the eye or outside the side planes of the view, and
  `visible-walls` those that rays across the maze grid cannot reach, because
  other walls stand in front (the maze is walls full height around an eye in
  their height range, so that is a question in plan). A face that the cube
  drops for a corner behind the near plane hides nothing, as in the picture.
  The two together take a corridor view from 137 cubes to a few dozen.

  The state holds `:px`, `:pz` and `:heading` (numbers), the finger tracking
  (`:look` and `:stick`, each with its finger's id), `:n`, `:pts` and `:ids` (the
  finger count, touch points and ids of last frame) and `:screen`. Colours are
  `[r g b a]` vectors."
  (:require [raylib.scenes.freecam :as free]
            [raylib.soft3d :as s3]
            [raylib.stick :as stick]))

(def maze
  "The original's maze (lines 22-38): `#` is a wall."
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
(def panel-colour "The minimap's panel (lines 80-84)." [0 0 0 170])
(def map-wall-colour "A wall on the minimap (lines 88-93)." [150 160 190 255])
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
  stops it clipping a corner diagonally (lines 48-57)."
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
  it each frame (lines 133-140): the eye 1.6 up, the target one unit along
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
  "Every wall as `{:x :z :cell :colour}`, the cube's centre on the ground plane,
  its index `cx + cy * 16` and its checker tint (lines 64-73), in the
  original's row-then-column order."
  (vec (for [cy (range rows)
             cx (range cols)
             :when (wall? cx cy)]
         {:x (* (+ cx 0.5) cell)
          :z (* (+ cy 0.5) cell)
          :cell (+ cx (* cy cols))
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

;; An eye inside the maze is inside the walls' height range (1.6 in 0 to 3), so
;; what hides what is decided in plan, by rays across the grid.

(defn frustum-walls
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

(def ^:private corners
  "Every lattice point `[x z open?]` that is a corner of a wall cell, with
  `open?` true when an open cell touches it too. The first wall a ray hits can
  change only as the ray passes a corner. A corner with open ground at it always
  counts. One that only walls touch counts only near the eye (`solid-reach`),
  where a face can be one the cube drops and a ray goes on through the wall."
  (vec (for [j (range (inc rows))
             i (range (inc cols))
             :let [touching (map (fn [[a b]] (wall? (+ i a) (+ j b))) [[0 0] [-1 0] [0 -1] [-1 -1]])]
             :when (some true? touching)]
         [(* i cell) (* j cell) (boolean (some false? touching))])))

(def ^:private solid-reach
  "How far from the eye a corner touched only by walls is still an event. A
  ray that passes through a wall whose face the cube drops goes on into the
  walls behind it, and their corners then matter, but only within a couple of
  cells of that face. Measured, not derived: at 12 the sweep over 35,712 poses
  found 151 walls cut that showed (all on a 3000x450 field), at 24 and at 100
  none, and 24 builds the same walls as 100."
  24.0)

(def ^:private near-margin
  "A wall face with a corner less than this far along the view is one
  `raylib.soft3d/cube` drops whole (its near plane is 0.05), so a ray goes on
  through it. The margin is wider than the plane, which only keeps more walls."
  0.2)

(defn- march
  "Walk the ray from `px`, `pz` along `dx`, `dz` across the grid (Amanatides
  and Woo) and `conj` onto `hits` the index of every wall it enters, stopping
  at the first whose entering face is not one the cube would drop (`fx`, `fz` is
  the view direction, `along` is measured on it), or at the edge of the map."
  [hits px pz dx dz fx fz]
  (let [inf 1.0e30
        adx (abs dx)
        adz (abs dz)
        sx (if (pos? dx) 1 -1)
        sz (if (pos? dz) 1 -1)
        cx0 (long (Math/floor (/ px cell)))
        cz0 (long (Math/floor (/ pz cell)))
        tdx (if (< adx 1.0e-12) inf (/ cell adx))
        tdz (if (< adz 1.0e-12) inf (/ cell adz))
        tx0 (if (< adx 1.0e-12) inf (/ (if (pos? sx) (- (* (inc cx0) cell) px) (- px (* cx0 cell))) adx))
        tz0 (if (< adz 1.0e-12) inf (/ (if (pos? sz) (- (* (inc cz0) cell) pz) (- pz (* cz0 cell))) adz))]
    (loop [cx cx0 cz cz0 tx tx0 tz tz0 hits hits]
      (let [step-x? (< tx tz)
            ncx (if step-x? (+ cx sx) cx)
            ncz (if step-x? cz (+ cz sz))]
        (if (or (< ncx 0) (< ncz 0) (>= ncx cols) (>= ncz rows))
          hits
          (let [tx' (if step-x? (+ tx tdx) tx)
                tz' (if step-x? tz (+ tz tdz))]
            (if (wall? ncx ncz)
              (let [hits' (conj hits (+ ncx (* ncz cols)))
                    ;; the entering face's two corners, as the cube sees them
                    a0 (if step-x?
                         (let [xf (* (if (pos? sx) ncx (inc ncx)) cell)]
                           (min (+ (* (- xf px) fx) (* (- (* ncz cell) pz) fz))
                                (+ (* (- xf px) fx) (* (- (* (inc ncz) cell) pz) fz))))
                         (let [zf (* (if (pos? sz) ncz (inc ncz)) cell)]
                           (min (+ (* (- (* ncx cell) px) fx) (* (- zf pz) fz))
                                (+ (* (- (* (inc ncx) cell) px) fx) (* (- zf pz) fz)))))]
                (if (>= a0 near-margin)
                  hits'
                  (recur ncx ncz tx' tz' hits')))
              (recur ncx ncz tx' tz' hits))))))))

(defn visible-walls
  "The walls from `frustum-walls` that a ray can reach, as `walls` lists them.
  Walls are full height around an eye inside their height range, so one wall
  hides another exactly where it hides it in plan. From the eye, the first
  wall a ray hits changes only as the ray passes a wall corner, so a ray at the
  middle of every gap between the corners' angles (and the view's two edges,
  widened by 0.01 radians) meets every wall that shows. `march` follows each
  ray across the grid. A face that `raylib.soft3d/cube` would drop for a corner
  behind the near plane hides nothing, as in the picture itself. What is left
  out is wholly behind other walls."
  [state dims]
  (let [{:keys [px pz heading]} state
        fx (Math/sin heading)
        fz (Math/cos heading)
        rx fz
        rz (- fx)
        tan-half (* (Math/tan (Math/toRadians (* 0.5 (:fovy (camera state dims))))) (:aspect dims))
        edge (+ (Math/atan tan-half) 0.01)
        phis (into [(- edge) edge]
                   (keep (fn [[x z open?]]
                           (let [dx (- x px)
                                 dz (- z pz)
                                 along (+ (* dx fx) (* dz fz))]
                             (when (and (> along 1.0e-9)
                                        (or open? (< (+ (* dx dx) (* dz dz)) (* solid-reach solid-reach))))
                               (let [phi (Math/atan2 (+ (* dx rx) (* dz rz)) along)]
                                 (when (< (abs phi) edge) phi))))))
                   corners)
        sorted (vec (sort phis))
        hits (loop [i 1 hits #{}]
               (if (< i (count sorted))
                 (let [a (nth sorted (dec i))
                       b (nth sorted i)]
                   (if (< (- b a) 1.0e-9)
                     (recur (inc i) hits)
                     (let [phi (* 0.5 (+ a b))
                           c (Math/cos phi)
                           sn (Math/sin phi)]
                       (recur (inc i)
                              (march hits px pz (+ (* fx c) (* rx sn)) (+ (* fz c) (* rz sn)) fx fz)))))
                 hits))]
    (filterv #(contains? hits (:cell %)) (frustum-walls state dims))))

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
  square for each wall at `(ox + cx * s, oy + cy * s)` (lines 80-93), every
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
  (sin h, cos h)` scaled, truncated (lines 94-103)."
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

(defn- turn-by
  "The heading after a drag of `dx` pixels: LEFT adds and RIGHT subtracts (lines
  118-119), so a drag right subtracts, at `sensitivity` a pixel times `800 /
  field width`."
  [heading dims dx]
  (- heading (* sensitivity (/ original-width (nth (:viewport dims) 2)) dx)))

(defn- walk
  "The original's step (lines 120-131) along the stick's unit direction
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
        [look stick] (stick/follow-pair (:look state) (:stick state) frame)
        fresh (stick/fresh frame state)
        {look' :look
         stick' :stick} (stick/begin-owners {:look look
                                             :stick stick}
                                            #(free/region dims %) fresh)
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
