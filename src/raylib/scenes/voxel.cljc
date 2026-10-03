(ns raylib.scenes.voxel
  "A block of voxels you walk round and hollow out, on two thumbs. Ported from
  raylib-jlt's `basic_voxel`, which is raylib's `models_basic_voxel` (zlib
  licence). The projection is in software, by `raylib.soft3d`.

  Mirrored from basic_voxel.clj:
  - The world (lines 79-84, 137-141): an 8 x 8 x 8 block of unit voxels at the
    integer positions 0..7, 512 of them, drawn BEIGE with BLACK wires (lines
    154-164) over a grid of 10 (line 153), through a 45 degree perspective
    camera.
  - The camera (lines 92-135): a yaw and a pitch, the look direction
    `(cos pitch * cos yaw, sin pitch, cos pitch * sin yaw)` (line 124), forward
    on the ground `(cos yaw, sin yaw)` and right `(-sin yaw, cos yaw)` (lines
    125-126), SPEED 0.15 an update, SENS 0.004 a pixel, the pitch held to +-1.4
    (lines 114-121), the height never changing. Until you steer it orbits the
    block's centre (3.5, 3.5) at radius 22 and height 14, 0.006 radians an
    update, facing back at the centre (lines 35-44, 95, 113-115, 118, 131-135),
    and the first walk, look or tap hands it over for good (lines 110-112, 186).
  - The pick (lines 59-77, 137-141): the crosshair is the ray, whatever sits
    under it is what a click takes, the nearest voxel the ray enters. Here
    `raylib.soft3d/screen->ray` casts it through the middle of the field, which
    is where the crosshair (a RED dot of radius 4, lines 166-169) is drawn, and
    `ray-box` tests every voxel, nearest first, as the original's brute force
    does.

  Controls. The original's left click removes the voxel under the crosshair and
  nothing else. A tap does that, anywhere in the field: the crosshair picks, the
  tap's position does not. W, A, S and D are a relative thumb-stick that starts
  in the lower third of the field (`raylib.stick`, as `raylib.scenes.fpcamera`),
  the press point its centre, a unit direction at one speed, up the glass
  forward. The mouse position delta is a drag that starts in the upper two
  thirds, times `800 / field width`. Both work at once, each by its own finger.
  A stick or a look begins only on a fresh press and follows only its own
  finger, so a resting finger never steers. A tap is a press and release that
  moved less than `gesture/slop` and never ends a touch of two fingers, so it
  is neither a walk nor a look. One tap action, so a button picks the other
  one:
  - Added, not in the original: placing. The button in the row under Back, at
    the right, flips what a tap does between \"tap: remove\" (the original's
    only rule, and the start) and \"tap: place\". A place puts a voxel on the
    face of the nearest voxel the ray entered, beside it where the ray came in
    (the `ray-box` normal), and does nothing on a miss, on an occupied cell, or
    on a cell that holds the eye. A finger on the button never starts a stick
    or a look.
  - Dropped: `fps!`, and the original's \"left-click a voxel to remove it\"
    line, which the button replaces. The status line (\"N voxels left\") is
    the caption below Back.

  Only exposed faces draw. A face is exposed when the voxel next to it is empty,
  so a full block has 384, and an eye outside it sees 128 of them from two sides
  (256 triangles) or 192 from three (384 triangles). `mesh` finds the exposed
  faces, the corners and edges they share, once, whenever the world changes.
  `scene-list` projects each corner once a frame, draws the faces that look
  toward the eye as `raylib.soft3d/cube` does for its faces (front winding by
  screen sign, a face with a corner behind the near plane dropped whole, mean
  depth for the painter), and draws the wires of those faces once per edge,
  with edges that carry on in a straight line joined into one line (the same
  pixels): 35 lines from two sides and 51 from three, where a line for each
  unit edge was 280 and 408. The order is `raylib.soft3d/finish`'s, far to near, but
  sorted a record a face rather than a triangle (a face's two triangles share a
  depth), so `finish` is not called.

  The cost follows the exposed faces, which grow as the block is hollowed out,
  since each pit shows its walls. Measured under jolt on the laptop (1206x2334,
  update, build and a stand-in for the draw side's code): 0.38 ms from the
  start view, 0.56 ms at the orbit's 45 degree corner where three sides show,
  and 1.3 ms for a block with a third of it eaten away in a scattered pattern.
  The phone's budget is 0.45, so the first holds and the others do not; a cut
  of the world was not made.

  The painter has no depth buffer. The faces go far to near by mean depth
  (`raylib.soft3d/finish`), and the wires after every face, so in a pit hollowed
  out of the block an edge of the far wall can show over the rim in front of
  it, where raylib's depth test would hide it. On the convex block the first
  frame draws, the wires are exact. Faces in a deep pit can also be ordered
  wrongly where depth buffer and mean depth disagree. Both are limits of the
  painter, not chased. The original's 45 degree fovy is kept while the field is
  as wide as 800x450 and widened by `raylib.soft3d/fit-camera` in a narrower
  one.

  The state holds `:world` (a set of `[x y z]` integer voxels), `:mesh`, `:mode`
  (`:remove` or `:place`), the pose (`:px` `:py` `:pz` `:yaw` `:pitch`, with
  `:angle` for the idle orbit and `:steered?`), the `:camera` built from it, the
  finger tracking (`:look` and `:stick` with their ids, `:gesture`, `:n`,
  `:pts`, `:ids`) and `:screen`. Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.scenes.freecam :as free]
            [raylib.soft3d :as s3]
            [raylib.stick :as stick]))

(def size "The block's edge in voxels. The original's SIZE." 8)
(def speed "The original's SPEED: ground units an update for the stick." 0.15)
(def sensitivity "The original's SENS: radians a pixel of look, in an 800 pixel window." 0.004)
(def pitch-limit "The original's pitch clamp, radians (lines 120: `(max -1.4) (min 1.4)`)." 1.4)
(def centre "The original's CENTRE: the block's middle on each axis." 3.5)
(def orbit-radius "The original's ORBIT-RADIUS." 22.0)
(def orbit-height "The original's ORBIT-HEIGHT, the eye's height for ever." 14.0)
(def orbit-speed "The original's ORBIT-SPEED, radians an update." 0.006)
(def orbit-pitch "The pitch that aims the idle orbit at the centre (line 95)." (Math/atan2 (- centre orbit-height) orbit-radius))
(def original-width "The original's window width, in pixels." 800.0)
(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))
(def eye-margin
  "How near a placed voxel may come to the eye: half a voxel and the near plane."
  0.55)

(def colours
  "raylib's own definitions of the colours the original names."
  {:background [245 245 245 255]
   :voxel [211 176 131 255]
   :wires [0 0 0 255]
   :crosshair [230 41 55 255]
   :caption [80 80 80 255]
   :button [200 200 200 255]
   :button-label [80 80 80 255]})

(def labels {:remove "tap: remove"
             :place "tap: place"})

(defn voxel-text
  "The caption for `n` voxels."
  [n]
  (str n " voxels"))

(defn full-block
  "The original's block: every `[x y z]` in 0..7."
  []
  (set (for [x (range size)
             y (range size)
             z (range size)]
         [x y z])))

;; --- the mesh -----------------------------------------------------------------

(def ^:private face-defs
  "Each of a voxel's faces as `[axis sign corners]`: the corners are bits (x 1, y 2, z 4)
  at the voxel's max, counter-clockwise seen from outside, as `raylib.soft3d`'s
  faces."
  [[2 1 [4 5 7 6]]
   [2 -1 [1 0 2 3]]
   [0 -1 [0 4 6 2]]
   [0 1 [5 1 3 7]]
   [1 1 [6 7 3 2]]
   [1 -1 [0 1 5 4]]])

(defn mesh
  "The exposed faces of `world`, with the shared corners and edges they need:

  - `:faces`, `[axis sign plane i0 i1 i2 i3 e0 e1 e2 e3]` for each face with an
    empty voxel beyond it: the axis (0 x, 1 y, 2 z), the outward sign, the
    plane's coordinate, four node indices counter-clockwise seen from outside,
    and the four edges between them (`i0`-`i1`, `i1`-`i2`, `i2`-`i3`,
    `i3`-`i0`) as indices into `:edges`;
  - `:groups`, `[axis sign [face ...]]` for each of the six directions that has
    a face, the faces ordered by `sign * plane`, so the ones an eye is outside of
    are a prefix of the list and the rest need not be looked at;
  - `:nodes`, a double array of `x y z` for each node, `:n` of them;
  - `:edges`, `[node-a node-b]` for each edge once, `node-a` the lower end, with
    `:edge-next` and `:edge-prev`, for each edge the edge that carries on from it
    in a straight line at either end, or -1. A frame joins the drawn edges that
    follow one another into one line."
  [world]
  (if (empty? world)
    {:faces []
     :groups []
     :nodes (double-array 0)
     :n 0
     :edges []
     :edge-next []
     :edge-prev []}
    (let [world (set world)
          [x0 y0 z0] (reduce (fn [[a b c] [x y z]] [(min a x) (min b y) (min c z)]) (first world) world)
          [x1 y1 z1] (reduce (fn [[a b c] [x y z]] [(max a x) (max b y) (max c z)]) (first world) world)
          nx (+ 2 (- x1 x0))
          ny (+ 2 (- y1 y0))
          nz (+ 2 (- z1 z0))
          n (* nx ny nz)
          idx (fn [a b c] (+ (* (+ (* a ny) b) nz) c))
          nodes (double-array (* 3 n))
          _ (dotimes [a nx]
              (dotimes [b ny]
                (dotimes [c nz]
                  (let [j (* 3 (idx a b c))]
                    (aset nodes j (- (+ x0 a) 0.5))
                    (aset nodes (+ j 1) (- (+ y0 b) 0.5))
                    (aset nodes (+ j 2) (- (+ z0 c) 0.5))))))
          raw (vec (for [[x y z :as v] world
                         [axis sgn corners] face-defs
                         :when (not (contains? world (update v axis + sgn)))]
                     [axis sgn (+ (nth v axis) (* 0.5 sgn))
                      (mapv (fn [c]
                              (idx (+ (- x x0) (bit-and c 1))
                                   (+ (- y y0) (bit-and (bit-shift-right c 1) 1))
                                   (+ (- z z0) (bit-and (bit-shift-right c 2) 1))))
                            corners)]))
          edge-pair (fn [a b] [(min a b) (max a b)])
          edge-ids (reduce (fn [acc [_ _ _ [i0 i1 i2 i3]]]
                             (reduce (fn [acc k] (if (contains? acc k) acc (assoc acc k (count acc))))
                                     acc
                                     [(edge-pair i0 i1) (edge-pair i1 i2) (edge-pair i2 i3) (edge-pair i3 i0)]))
                           {}
                           raw)
          faces (mapv (fn [[axis sgn plane [i0 i1 i2 i3]]]
                        (into [axis sgn plane i0 i1 i2 i3]
                              (map #(get edge-ids %) [(edge-pair i0 i1) (edge-pair i1 i2) (edge-pair i2 i3) (edge-pair i3 i0)])))
                      raw)
          edges (mapv key (sort-by val edge-ids))
          groups (vec (for [[axis sgn] [[0 1] [0 -1] [1 1] [1 -1] [2 1] [2 -1]]
                            :let [fs (filterv #(and (= axis (nth (nth faces %) 0)) (= sgn (nth (nth faces %) 1)))
                                              (range (count faces)))]
                            :when (seq fs)]
                        [axis sgn (vec (sort-by (fn [f] (* sgn (nth (nth faces f) 2))) fs))]))]
      {:faces faces
       :groups groups
       :nodes nodes
       :n n
       :edges edges
       :edge-next (mapv (fn [[a b]] (get edge-ids [b (+ b (- b a))] -1)) edges)
       :edge-prev (mapv (fn [[a b]] (get edge-ids [(- a (- b a)) a] -1)) edges)})))

;; --- layout -------------------------------------------------------------------

(defn geometry
  "The layout for `metrics`' `:screen`: `raylib.soft3d/field` (`:viewport`,
  `:aspect`, `:size`, `:pad`, `:text-y`) plus

  - `:stick-top`, the y where the lower third of the field starts. Above it a
    touch looks, from it down a touch walks, as `raylib.scenes.freecam/region`
    reads it;
  - `:button`, `[x y w h]` in the row below Back, at the right, above the field;
  - `:crosshair`, the middle of the field, which is also the pick's ray;
  - `:look-scale`, `800 / field width`, which scales the look."
  [metrics]
  (let [{:keys [pad text-y]
         base :size
         [fx fy fw fh] :viewport
         :as field} (s3/field metrics)
        bw (* 0.4 fw)]
    (assoc field
           :stick-top (+ fy (* (/ 2.0 3.0) fh))
           :button [(double (- fw pad bw)) (double text-y) bw (+ base (* 0.5 pad))]
           :crosshair [(+ fx (* 0.5 fw)) (+ fy (* 0.5 fh))]
           :look-scale (/ original-width fw))))

(defn dimensions
  "`geometry` plus the text: `:caption` and `:label` as `{:s :x :y :size}` (the
  caption at its widest, \"9999 voxels\", and the \"tap: remove\" label, the
  wider of the two labels), `:labels` for each mode's label placed in the
  button, and `:lines` the caption and the label so a test can check they fit.
  The size is cut back, to 8 at the least, so that the caption covers no more
  than half the width and the label no more than 0.36 of it. `measure` is
  `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [pad text-y]
         base :size
         [fx _ fw] :viewport
         [bx by bw bh] :button
         :as geo} (geometry metrics)
        widest-caption (measure (voxel-text 9999) 100)
        widest-label (apply max (map #(measure % 100) (vals labels)))
        widest (max (/ widest-caption (/ 0.5 0.92)) (/ widest-label (/ 0.36 0.92)))
        sz (max 8 (min base (int (/ (* 0.92 fw 100.0) widest))))
        place (fn [mode]
                (let [s (get labels mode)]
                  {:s s
                   :x (int (+ bx (* 0.5 (- bw (measure s sz)))))
                   :y (int (+ by (* 0.5 (- bh sz))))
                   :size sz}))
        caption {:s (voxel-text 9999)
                 :x (+ fx pad)
                 :y text-y
                 :size sz}
        label (place :remove)]
    (assoc geo
           :caption caption
           :label label
           :labels {:remove label
                    :place (place :place)}
           :lines [caption label])))

;; --- the camera and the picture -----------------------------------------------

(defn camera-of
  "The camera for the pose `{:px :py :pz :yaw :pitch}`, as the original builds it
  each frame (lines 144-151): the target the eye plus the look direction, up
  (0, 1, 0), fovy 45, perspective."
  [{:keys [px py pz yaw pitch]}]
  (let [cp (Math/cos pitch)]
    {:position [px py pz]
     :target [(+ px (* cp (Math/cos yaw))) (+ py (Math/sin pitch)) (+ pz (* cp (Math/sin yaw)))]
     :up [0.0 1.0 0.0]
     :fovy 45.0
     :projection :perspective}))

(defn camera
  "`state`'s camera fitted to `dims`' field by `raylib.soft3d/fit-camera`."
  [state dims]
  (s3/fit-camera (:camera state) original-aspect (:aspect dims)))

(defn scene-list
  "The draw list for `state`, in paint order: the grid of 10, the exposed faces
  that look toward the eye as flat BEIGE quads far to near, and their BLACK
  wires, each edge once and runs of straight edges as one line. Each node is
  projected once, at most. A face with a corner behind the near plane is
  dropped whole."
  [state dims]
  (let [vp (s3/view-proj (camera state dims) (:viewport dims))
        {:keys [faces groups edges edge-next edge-prev nodes n]} (:mesh state)
        [m0 m1 m2 m3 m4 m5 m6 m7 m8 m9 m10 m11 m12 m13 m14 m15] (:m vp)
        [d0 d1 d2 d3] (:d vp)
        ox (:x vp) oy (:y vp)
        hw (* 0.5 (:w vp)) hh (* 0.5 (:h vp))
        [ex ey ez] (:position (:camera vp))
        [vr vg vb va] (:voxel colours)
        [wr wg wb wa] (:wires colours)
        sx (double-array n) sy (double-array n) sd (double-array n)
        seen (double-array n)
        done (double-array (count edges))
        project! (fn [i]
                   (let [s (aget seen i)]
                     (if (zero? s)
                       (let [j (* 3 i)
                             x (aget nodes j) y (aget nodes (+ j 1)) z (aget nodes (+ j 2))
                             cw (+ (* m12 x) (* m13 y) (* m14 z) m15)]
                         (if (>= (+ (* m8 x) (* m9 y) (* m10 z) m11 cw) 0.0)
                           (do (aset sx i (+ ox (* hw (+ 1.0 (/ (+ (* m0 x) (* m1 y) (* m2 z) m3) cw)))))
                               (aset sy i (+ oy (* hh (- 1.0 (/ (+ (* m4 x) (* m5 y) (* m6 z) m7) cw)))))
                               (aset sd i (+ (* d0 x) (* d1 y) (* d2 z) d3))
                               (aset seen i 1.0)
                               true)
                           (do (aset seen i 2.0)
                               false)))
                       (== s 1.0))))
        ;; pass 1: a record `[depth tri-1 tri-2 e0 e1 e2 e3]` for each face that
        ;; faces the eye and is wholly in front of the near plane
        face! (fn [recs f]
                (let [[_ _ _ i0 i1 i2 i3 e0 e1 e2 e3] (nth faces f)]
                  (if (and (project! i0) (project! i1) (project! i2) (project! i3))
                    (let [xa (aget sx i0) ya (aget sy i0)
                          xb (aget sx i1) yb (aget sy i1)
                          xc (aget sx i2) yc (aget sy i2)
                          xe (aget sx i3) ye (aget sy i3)
                          depth (* 0.25 (+ (aget sd i0) (aget sd i1) (aget sd i2) (aget sd i3)))
                          t1? (neg? (- (* (- xb xa) (- yc ya)) (* (- yb ya) (- xc xa))))
                          t2? (neg? (- (* (- xc xa) (- ye ya)) (* (- yc ya) (- xe xa))))]
                      (if (or t1? t2?)
                        (conj! recs [depth
                                     (when t1? [:tri xa ya xb yb xc yc vr vg vb va depth])
                                     (when t2? [:tri xa ya xc yc xe ye vr vg vb va depth])
                                     e0 e1 e2 e3])
                        recs))
                    recs)))
        recs (reduce (fn [recs [axis sgn fs]]
                       (let [e (case (long axis) 0 ex 1 ey ez)
                             nfs (count fs)]
                         (loop [k 0 recs recs]
                           (if (< k nfs)
                             (let [f (nth fs k)]
                               (if (> (* sgn (- e (nth (nth faces f) 2))) 1.0e-9)
                                 (recur (inc k) (face! recs f))
                                 recs))
                             recs))))
                     (transient [])
                     groups)
        ;; far to near by mean depth, the order `raylib.soft3d/finish` gives the
        ;; triangles (its sort is stable, and so is this one, and a face's two
        ;; triangles share one depth), on a record a face instead of a triangle
        sorted (vec (sort (fn [a b] (compare (nth b 0) (nth a 0))) (persistent! recs)))
        ;; pass 2: the grid under, then the faces in order, noting each edge of
        ;; them once
        note (fn [drawn e]
               (if (zero? (aget done e))
                 (do (aset done e 1.0)
                     (conj! drawn e))
                 drawn))
        nsorted (count sorted)
        [tris drawn] (loop [k 0 tris (transient (s3/grid [] vp 10 1.0)) drawn (transient [])]
                       (if (< k nsorted)
                         (let [[_ t1 t2 e0 e1 e2 e3] (nth sorted k)]
                           (recur (inc k)
                                  (cond-> tris
                                    t1 (conj! t1)
                                    t2 (conj! t2))
                                  (-> drawn (note e0) (note e1) (note e2) (note e3))))
                         [tris (persistent! drawn)]))
        ;; pass 3: the wires over. Drawn edges that carry on one another in a
        ;; straight line are one line, from the first one's start to the last
        ;; one's end: the same pixels, a tenth of the items.
        lines (reduce (fn [lines e]
                        (let [p (nth edge-prev e)]
                          (if (and (>= p 0) (== 1.0 (aget done p)))
                            lines
                            (let [a (nth (nth edges e) 0)
                                  last-b (loop [e e]
                                           (let [nx (nth edge-next e)]
                                             (if (and (>= nx 0) (== 1.0 (aget done nx)))
                                               (recur nx)
                                               (nth (nth edges e) 1))))]
                              (conj! lines [:line (aget sx a) (aget sy a) (aget sx last-b) (aget sy last-b) wr wg wb wa :over])))))
                      (transient [])
                      drawn)]
    (persistent! (reduce conj! tris (persistent! lines)))))

;; --- the pick -----------------------------------------------------------------

(defn pick
  "The nearest voxel of `world` the `ray` (`{:position :direction}`, as
  `raylib.soft3d/screen->ray` gives) enters, as `{:voxel :step}`, or nil. `:step`
  is the unit `[dx dy dz]` out of the voxel through the face the ray came in by,
  the `ray-box` normal's largest axis. A voxel the eye is inside, or behind the
  eye, is not a hit, as the original's `hit-distance` (lines 59-65)."
  [world ray]
  (let [best (reduce (fn [best [x y z :as v]]
                       (let [r (s3/ray-box ray [(- x 0.5) (- y 0.5) (- z 0.5)] [(+ x 0.5) (+ y 0.5) (+ z 0.5)])]
                         (if (and (:hit? r) (>= (:distance r) 0.0)
                                  (or (nil? best) (< (:distance r) (:distance (second best)))))
                           [v r]
                           best)))
                     nil
                     world)]
    (when best
      (let [[v r] best
            [nx ny nz] (:normal r)
            a (map abs [nx ny nz])
            axis (first (apply max-key second (map-indexed vector a)))]
        {:voxel v
         :step (assoc [0 0 0] axis (if (neg? (nth [nx ny nz] axis)) -1 1))}))))

;; --- fingers ------------------------------------------------------------------

(defn- look-by
  "The original's mouse look (lines 116-121) for a drag of `dx`, `dy` pixels:
  `yaw + SENS * dx` and `(max -1.4) (min 1.4)` of `pitch - SENS * dy`, each
  pixel scaled by `800 / field width`."
  [{:keys [yaw pitch]
    :as state} dims [dx dy]]
  (let [k (* sensitivity (:look-scale dims))]
    (assoc state
           :yaw (+ yaw (* k dx))
           :pitch (-> (- pitch (* k dy)) (max (- pitch-limit)) (min pitch-limit)))))

(defn- walk
  "The original's WASD (lines 125-135) along the stick's unit direction `[ux uy]`
  (`raylib.scenes.freecam/stick-dir`): `f` forward (up the glass) and `r` right,
  `dx = f * cos yaw + r * -sin yaw` and `dz = f * sin yaw + r * cos yaw`, scaled
  by `speed`. The height does not change."
  [{:keys [px pz yaw]
    :as state} [ux uy]]
  (let [f (- uy) r ux
        fwx (Math/cos yaw) fwz (Math/sin yaw)]
    (assoc state
           :px (+ px (* speed (+ (* f fwx) (* r (- fwz)))))
           :pz (+ pz (* speed (+ (* f fwz) (* r fwx)))))))

(defn- orbit-pose
  "The idle orbit at `angle` (lines 114, 118, 133-135): the eye on the circle
  round the block's centre, facing back at it."
  [angle]
  {:angle angle
   :px (+ centre (* orbit-radius (Math/cos angle)))
   :py orbit-height
   :pz (+ centre (* orbit-radius (Math/sin angle)))
   :yaw (+ angle Math/PI)
   :pitch orbit-pitch})

(defn- act
  "A tap in the field, at the crosshair: the ray goes through the middle of the
  field of the camera as it now stands, and the nearest voxel it enters is
  removed, or in `:place` mode a voxel is put beside it on the face the ray came
  in by. A miss, an occupied cell or a cell holding the eye changes nothing."
  [{:keys [world mode]
    :as state} dims]
  (let [vp (s3/view-proj (camera state dims) (:viewport dims))
        ray (s3/screen->ray vp (:crosshair dims))]
    (if-let [{:keys [voxel step]} (pick world ray)]
      (let [added (mapv + voxel step)
            [ex ey ez] (:position ray)
            world' (case mode
                     :remove (disj world voxel)
                     (if (or (contains? world added)
                             (let [[ax ay az] added]
                               (and (< (abs (- ex ax)) eye-margin) (< (abs (- ey ay)) eye-margin) (< (abs (- ez az)) eye-margin))))
                       world
                       (conj world added)))]
        (if (= world world')
          state
          (assoc state :world world' :mesh (mesh world'))))
      state)))

(defn advance
  "One frame. Fingers are sorted into a look and a stick by `raylib.stick`, then
  the camera is looked and walked in the original's order, or goes on orbiting
  while nothing has taken it over. `gesture/track` gives the tap: on the button
  it flips the mode, in the field it acts at the crosshair, through the camera
  as this frame left it, as the original picks after it moves (lines 136-141).
  A release frame with fewer than two points lifts everything, and its position
  is never read. A rotation of the phone drops the tracking, whose pixels are
  the old screen's. A walk past the dead zone, a look that moved, or a tap in
  the field hands the camera over for good, the frame after it first moves it
  (the original's `took-over?` and `steered?`, lines 110-113, 186)."
  [state input]
  (let [metrics (:metrics input)
        dims (geometry metrics)
        screen (:screen metrics)
        state (if (not= screen (:screen state))
                (assoc (dissoc state :look :stick) :n 0 :pts [] :ids nil :gesture gesture/idle)
                state)
        phase (get-in input [:pointer :phase])
        raw (vec (:touch-points input))
        points (if (and (= :release phase) (< (count raw) 2)) [] raw)
        n (count points)
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
        looked? (boolean (and delta (not (every? zero? delta))))
        dir (when stick' (free/stick-dir stick' metrics))
        [g event] (gesture/track (:gesture state) input)
        tap? (and (= :tap (:type event))
                  (< (max n (:n state 0)) 2))
        button? (and tap? (gesture/in-rect? (:button dims) (:at event)))
        field? (and tap? (gesture/in-rect? (:viewport dims) (:at event)))
        steered? (:steered? state)
        pose (if steered?
               (cond-> state
                 looked? (look-by dims delta)
                 dir (walk dir))
               (orbit-pose (+ (:angle state) orbit-speed)))
        moved (-> state
                  (assoc :px (:px pose)
                         :py (:py pose)
                         :pz (:pz pose)
                         :yaw (:yaw pose)
                         :pitch (:pitch pose)
                         :angle (:angle pose))
                  (as-> s (assoc s :camera (camera-of s))))
        moved (cond-> moved
                button? (assoc :mode (if (= :remove (:mode state)) :place :remove))
                field? (act dims))]
    (assoc moved
           :screen screen
           :gesture g
           :n n
           :pts points
           :ids ids
           :look look'
           :stick stick'
           :steered? (boolean (or steered? dir looked? field?)))))

(defn- init [{:keys [metrics]}]
  (let [world (full-block)
        pose (orbit-pose 0.0)]
    [(assoc pose
            :world world
            :mesh (mesh world)
            :mode :remove
            :steered? false
            :camera (camera-of pose)
            :screen (:screen metrics)
            :gesture gesture/idle
            :n 0
            :pts []
            :ids nil
            :look nil
            :stick nil)
     [[:scene/init :voxel]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :voxel]]])

(defn scene []
  {:id :voxel
   :title "Basic Voxel"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
