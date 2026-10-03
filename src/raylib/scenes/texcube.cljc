(ns raylib.scenes.texcube
  "Two textured cubes under an orbiting camera, ported from raylib-jlt's
  `textured_cube` (zlib licence, after raylib's rlgl immediate-mode textured
  quads). The projection is in software, by `raylib.soft3d`.

  The original (textured_cube.clj) draws, with rlgl quads from one 64 by 64
  atlas, a cube of 2 by 4 by 2 at (-2, 2, 0) that maps the whole atlas over each
  face (`draw-cube-texture`, lines 41-64), and a cube of 2 by 2 by 2 at (2, 1, 0)
  that maps only the atlas's slice u 0 to 0.5, v 0.5 to 1 over each face
  (`draw-cube-texture-rec`, lines 66-90, whose v runs the other way up), then a
  grid of 10 (lines 118-135). The camera is at (14 cos a, 8, 14 sin a) looking
  at the origin, fovy 45, with `a = frame * 0.01` (lines 111-115). `angle` and
  `camera` are those lines. Nothing is read from the frame time, as in the
  original: the angle grows 0.01 radians an update.

  The atlas (lines 21-29) is four solid quadrants: red (230, 41, 55) top left,
  green (0, 158, 47) top right, blue (0, 121, 241) bottom left and yellow
  (253, 249, 0) bottom right, `atlas-pixel` line for line, with v = 0 on the
  top row. There is no texture here, so each face is drawn as the quads of its
  texels: `face-cells` cuts the slice of the atlas the face shows at the
  quadrant boundary u = 0.5 and v = 0.5, and each piece is a flat quad of the
  colour the texture has at its centre. The six faces' vertex lines (their
  corners and their u and v) are the original's, copied as data in `faces`, so
  the quadrants fall where the original puts them, mirrored or turned as its
  vertices turn them. The whole cube's face is four quads, one a quadrant, and
  the slice cube's face is one, since its slice lies inside the blue
  quadrant. Neighbouring quadrants differ in colour, so no same-colour runs
  exist to merge; the texture is reproduced at full resolution, with no cut.
  A texel is a quadrant here because that is all the atlas holds.

  Every quad keeps its original's vertex order, which is counter-clockwise from
  outside, so each is drawn only when it is wound front on the screen, as
  `raylib.soft3d`'s builders do. A face away from the camera makes no
  triangles. Seen from a point, a cube shows at most 3 faces, so a frame is at
  most 3 * 4 * 2 + 3 * 2 = 30 triangles and the grid's 22 lines.

  Paint order, with no `raylib.soft3d/finish`. The grid goes first: both cubes
  stand on or above its plane and the camera is above it, so it is behind
  everything. Within a cube, back-face omission is enough, because a cube is
  convex and its visible faces never overlap on the screen, and the quads of a
  face are coplanar and disjoint. Between the cubes: the plane x = 0 separates
  them (the whole cube spans x -3 to -1, the slice cube 1 to 3), so the cube on
  the camera's side of it is in front wherever the two overlap. `cube-order`
  paints the other one first. A test shoots rays through the field and checks
  the nearer hit is painted last.

  Controls: the original reads no input, so none is mapped. Its text line
  (line 130, at (10, 10), size 18) sits below Back; the 3D view fills the field
  under it, full width to the bottom, and `raylib.soft3d/fit-camera` widens the
  45 degree fovy in a field narrower than 800x450.

  The state holds only `:frame`. Colours are `[r g b a]` vectors."
  (:require [raylib.soft3d :as s3]))

(def caption-text "Left: whole atlas. Right: one quarter of it (DrawCubeTextureRec).")

(def background-colour "RAYWHITE." [245 245 245 255])
(def caption-colour "DARKGRAY." [80 80 80 255])
(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

(def atlas-size "The atlas's side in texels, the original's `ATLAS`." 64)

(def atlas-colours
  "The quadrants' colours: top left, top right, bottom left, bottom right."
  [[230 41 55 255] [0 158 47 255] [0 121 241 255] [253 249 0 255]])

(defn atlas-pixel
  "The original's `atlas-pixel` (lines 21-29): the colour of texel `x` `y`."
  [x y]
  (let [left? (< x (/ atlas-size 2))
        top? (< y (/ atlas-size 2))]
    (cond
      (and left? top?) (nth atlas-colours 0)
      (and (not left?) top?) (nth atlas-colours 1)
      (and left? (not top?)) (nth atlas-colours 2)
      :else (nth atlas-colours 3))))

(def faces
  "The six quads of `draw-cube-texture`, in its order (front +z, back -z, top
  +y, bottom -y, right +x, left -x), each of four `[sx sy sz u v]`: the vertex
  at xl or xr (-1 or 1), yb or yt and zb or zf, with its texture coordinates.
  Lines 48-63. Each is counter-clockwise seen from outside."
  [[[-1 -1 1 0 0] [1 -1 1 1 0] [1 1 1 1 1] [-1 1 1 0 1]]
   [[-1 -1 -1 1 0] [-1 1 -1 1 1] [1 1 -1 0 1] [1 -1 -1 0 0]]
   [[-1 1 -1 0 1] [-1 1 1 0 0] [1 1 1 1 0] [1 1 -1 1 1]]
   [[-1 -1 -1 1 1] [1 -1 -1 0 1] [1 -1 1 0 0] [-1 -1 1 1 0]]
   [[1 -1 -1 1 0] [1 1 -1 1 1] [1 1 1 0 1] [1 -1 1 0 0]]
   [[-1 -1 -1 0 0] [-1 -1 1 1 0] [-1 1 1 1 1] [-1 1 -1 0 1]]])

(def cubes
  "The two cubes of the original's `-main`, with `:slice` as `[su0 su1 sv0 sv1]`,
  the part of the atlas a face shows (lines 121-135). `:flip-v?` is
  `draw-cube-texture-rec`'s v, which runs the other way up from
  `draw-cube-texture`'s."
  [{:id :whole
    :centre [-2.0 2.0 0.0]
    :size [2.0 4.0 2.0]
    :slice [0.0 1.0 0.0 1.0]
    :flip-v? false}
   {:id :slice
    :centre [2.0 1.0 0.0]
    :size [2.0 2.0 2.0]
    :slice [0.0 0.5 0.5 1.0]
    :flip-v? true}])

(defn- cuts
  "`lo` and `hi`, with the quadrant boundary 0.5 between them when it falls
  strictly inside."
  [lo hi]
  (if (< lo 0.5 hi) [lo 0.5 hi] [lo hi]))

(defn face-cells
  "The quads that make `face` (0 to 5, as `faces`) of `cube` look like the
  original's textured one: a vector of `{:corners [p p p p] :colour [r g b a]}`
  in world space, each corner `[x y z]` in the original's vertex order. See the
  ns docstring."
  [{:keys [centre size slice flip-v?]} face]
  (let [[su0 su1 sv0 sv1] slice
        [cx cy cz] centre
        [hx hy hz] (mapv #(* 0.5 %) size)
        quad (nth faces face)
        ;; the face's corner at local (lu, lv), the 0 or 1 of its u and v
        corner-at (fn [lu lv]
                    (let [[sx sy sz] (some (fn [[_ _ _ u v :as vtx]] (when (and (== u lu) (== v lv)) vtx)) quad)]
                      [(+ cx (* sx hx)) (+ cy (* sy hy)) (+ cz (* sz hz))]))
        p00 (corner-at 0 0) p10 (corner-at 1 0) p11 (corner-at 1 1) p01 (corner-at 0 1)
        ;; world point at local (s, t) over the face's rectangle
        at (fn [s t]
             (mapv (fn [a b c d] (+ (* (- 1.0 s) (- 1.0 t) a) (* s (- 1.0 t) b) (* s t c) (* (- 1.0 s) t d)))
                   p00 p10 p11 p01))
        us (cuts su0 su1)
        vs (cuts sv0 sv1)
        du (- su1 su0)
        dv (- sv1 sv0)
        ;; the local coordinate of an atlas u or v
        lu-of (fn [au] (/ (- au su0) du))
        lv-of (fn [av] (if flip-v? (/ (- sv1 av) dv) (/ (- av sv0) dv)))]
    (vec (for [[a0 a1] (partition 2 1 us)
               [b0 b1] (partition 2 1 vs)
               :let [ac (* 0.5 (+ a0 a1))
                     bc (* 0.5 (+ b0 b1))]]
           {:corners (mapv (fn [[_ _ _ u v]]
                             (let [au (if (zero? u) a0 a1)
                                   av (if (if flip-v? (zero? v) (not (zero? v))) b1 b0)]
                               (at (lu-of au) (lv-of av))))
                           quad)
            :colour (atlas-pixel (min (dec atlas-size) (int (* ac atlas-size)))
                                 (min (dec atlas-size) (int (* bc atlas-size))))}))))

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

(defn angle "The camera's angle in radians: 0.01 a frame (line 111)." [state]
  (* (:frame state) 0.01))

(defn camera
  "The original's camera for `state`, fitted to `dims`' field by
  `raylib.soft3d/fit-camera`."
  [state dims]
  (let [a (angle state)]
    (s3/fit-camera {:position [(* 14.0 (Math/cos a)) 8.0 (* 14.0 (Math/sin a))]
                    :target [0.0 0.0 0.0]
                    :up [0.0 1.0 0.0]
                    :fovy 45.0
                    :projection :perspective}
                   original-aspect (:aspect dims))))

(defn cube-order
  "The ids of `cubes` in paint order, farther first: the cube on the far side
  of the plane x = 0 from the camera, then the one on its side."
  [state]
  (if (pos? (* 14.0 (Math/cos (angle state))))
    [:whole :slice]
    [:slice :whole]))

(defn- front-tri
  "Append triangle p q s, with the mean view depth `depth`, when it is wound
  front on the y-down screen (a negative cross product); drop it otherwise."
  [dl p q s [r g b a] depth]
  (let [x1 (nth p 0) y1 (nth p 1) x2 (nth q 0) y2 (nth q 1) x3 (nth s 0) y3 (nth s 1)]
    (if (neg? (- (* (- x2 x1) (- y3 y1)) (* (- y2 y1) (- x3 x1))))
      (conj dl [:tri x1 y1 x2 y2 x3 y3 r g b a depth])
      dl)))

(def ^:private normals
  "The outward normal of each of `faces`."
  [[0.0 0.0 1.0] [0.0 0.0 -1.0] [0.0 1.0 0.0] [0.0 -1.0 0.0] [1.0 0.0 0.0] [-1.0 0.0 0.0]])

(defn- toward?
  "Whether face `face` of `cube` looks toward `eye`: its outward normal against
  the way from its centre to the eye. This only skips the work for faces that
  could not be drawn; the screen winding decides what is."
  [{:keys [centre size]} face eye]
  (let [n (nth normals face)]
    (pos? (reduce + (map (fn [nk ck hk ek] (* nk (- ek (+ ck (* nk hk 0.5)))))
                         n centre size eye)))))

(defn- add-cube
  "Append the quads of the faces of `cube` that look toward the camera: each
  cell as the triangles 0 1 2 and 0 2 3 of its corners, which keep their
  vertex order, so each is wound front on the screen. A cell with a corner
  behind the near plane is dropped whole. Its depth is the mean of its corners'."
  [dl vp cube]
  (let [eye (:position (:camera vp))]
    (reduce
     (fn [dl face]
       (if (toward? cube face eye)
         (reduce
          (fn [dl {:keys [corners colour]}]
            (let [[p0 p1 p2 p3] (mapv #(s3/project vp %) corners)]
              (if (and p0 p1 p2 p3)
                (let [depth (* 0.25 (+ (nth p0 2) (nth p1 2) (nth p2 2) (nth p3 2)))]
                  (-> dl
                      (front-tri p0 p1 p2 colour depth)
                      (front-tri p0 p2 p3 colour depth)))
                dl)))
          dl
          (face-cells cube face))
         dl))
     dl
     (range 6))))

(defn scene-list
  "The draw list for `state`, in paint order: the grid, then the cube farther
  from the camera and the nearer one. See the ns docstring."
  [state dims]
  (let [vp (s3/view-proj (camera state dims) (:viewport dims))
        by-id (zipmap (map :id cubes) cubes)]
    (reduce (fn [dl id] (add-cube dl vp (by-id id)))
            (s3/grid [] vp 10 1.0)
            (cube-order state))))

(defn- init [_] [{:frame 0} [[:scene/init :texcube]]])
(defn- update-scene [state _] [(update state :frame inc) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :texcube]]])

(defn scene []
  {:id :texcube
   :title "Textured Cube"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
