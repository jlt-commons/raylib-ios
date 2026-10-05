(ns net.b12n.raylib-ios.soft3d
  "raylib's 3D camera and a handful of its 3D primitives, projected in software
  into a 2D draw list that `net.b12n.raylib-ios.host/draw-3d!` emits.

  The phone draws 2D through rlgl, so a 3D scene here is projected by hand.
  Every formula mirrors raylib 6.0's C, cited per function:

  - A camera is `{:position [x y z] :target [x y z] :up [x y z] :fovy deg
    :projection :perspective|:orthographic}`, raylib's Camera3D.
  - `view-proj` builds MatrixLookAt and then MatrixPerspective or MatrixOrtho
    with BeginMode3D's rules: near RL_CULL_DISTANCE_NEAR 0.05, far
    RL_CULL_DISTANCE_FAR 4000, perspective `top = near * tan(fovy/2)`,
    orthographic `top = fovy/2`, and `right = top * aspect` for both. The
    aspect is the viewport's own, as GetWorldToScreenEx takes it.
  - A transform is a 4x4 matrix as 16 doubles in row-major order, acting on
    column vectors. `compose` multiplies left to right, so the rightmost
    transform applies to a point first. That is rlgl's call order:
    rlTranslatef then rlRotatef is `(compose (translate ...) (rotate-axis ...))`.

  `field` is the layout every 3D scene shares: a caption line below Back and
  the 3D field under it, with the field's `:viewport` and `:aspect`.
  `fit-camera` adapts a camera made for an original window to a field of another
  aspect, so a tall phone field still shows the original's width.

  Building a frame: start from `[]`, thread it through the builders (`cube`,
  `cube-wires`, `grid`, `lines`, `sphere`, `plane`, `billboard`, `cylinder`,
  `cylinder-wires`, `capsule`, `capsule-wires`), then `finish` it into the draw
  list. `finish` is the general order, a sort of every triangle far to near. A
  scene may paint in an order it can show is right for its own geometry and skip
  it, as Waving Cubes, Point Cloud, 3D Split Screen, Bouncing Spheres, Billboard
  Rendering, Directional Billboard, Textured Cube and Basic Voxel do. Every
  builder projects as it goes, so a back face, or a face behind the near plane,
  never becomes an item, and a line behind it is clipped to it instead.

  The draw list is a vector of flat items:

  - `[:tri x1 y1 x2 y2 x3 y3 r g b a depth]`
  - `[:line x1 y1 x2 y2 r g b a layer]`

  The trailing `depth` (mean view depth of the face) and `layer` (`:under` or
  `:over`) are the sort keys `finish` reads. `net.b12n.raylib-ios.host/draw-3d!` ignores
  them, so `finish` hands the items on without copying them.

  Visibility is decided the way the helitorus scene in raylib-ios-demo decides
  it, by screen-space sign. Each cube face is wound counter-clockwise seen from
  outside, so in this y-down screen space a face toward the camera has a
  NEGATIVE cross product of its first two edges. That is the winding rlgl
  keeps (`net.b12n.raylib-ios.host/draw-triangle`). A triangle with a cross product of zero
  or more is a back face or edge-on and is never added, so every `:tri` in a
  draw list has the front winding. rlgl culls at its later batch flush, so the
  winding has to be right when the vertices leave, not toggled around them.

  Nothing behind the near plane is wrapped through infinity: a face with any
  corner behind it is dropped, and a line is clipped to it in clip space.

  There is no depth buffer. `finish` paints in layers: the grid and `:under`
  lines, then faces far to near by mean depth, then wires and `:over` lines.
  So wires draw over every face, including a cube's hidden back edges, where
  raylib's depth test would hide them, unless `cube-wires` is given
  `{:hide-back? true}`, which leaves those edges out. A cube's faces take
  raylib-jlt's `cube!` shades by face, or one flat colour with
  `{:shade :flat}`, which is rmodels.c `DrawCube`."
  (:require [net.b12n.raylib-ios.gesture :as gesture]))

;; --- vectors and matrices --------------------------------------------------

(def ^:private near 0.05)    ; rlgl.h RL_CULL_DISTANCE_NEAR
(def ^:private far 4000.0)   ; rlgl.h RL_CULL_DISTANCE_FAR

(defn- normalize
  "raymath's Vector3Normalize as MatrixLookAt inlines it: a zero length is
  treated as 1, so a zero vector stays zero."
  [[x y z]]
  (let [x (double x) y (double y) z (double z)
        l (Math/sqrt (+ (* x x) (* y y) (* z z)))
        il (/ 1.0 (if (zero? l) 1.0 l))]
    [(* x il) (* y il) (* z il)]))

(defn- cross3 [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by)) (- (* az bx) (* ax bz)) (- (* ax by) (* ay bx))])

(defn- dot3 [[ax ay az] [bx by bz]]
  (+ (* ax bx) (* ay by) (* az bz)))

(defn- mul
  "The row-major product a * b of two 4x4 matrices."
  [a b]
  (loop [k 0 out (transient [])]
    (if (< k 16)
      (let [r (* 4 (quot k 4)) c (rem k 4)]
        (recur (inc k)
               (conj! out (+ (* (nth a r) (nth b c))
                             (* (nth a (+ r 1)) (nth b (+ c 4)))
                             (* (nth a (+ r 2)) (nth b (+ c 8)))
                             (* (nth a (+ r 3)) (nth b (+ c 12)))))))
      (persistent! out))))

(defn- look-at
  "raymath.h MatrixLookAt(eye, target, up): rows vx, vy, vz, where vz is
  normalize(eye - target), vx normalize(up x vz) and vy vz x vx, each row
  closed by minus its dot with the eye."
  [eye target up]
  (let [eye (mapv double eye)
        vz (normalize (mapv - eye target))
        vx (normalize (cross3 (mapv double up) vz))
        vy (cross3 vz vx)
        row (fn [[a b c]] [a b c (- (dot3 [a b c] eye))])]
    (-> [] (into (row vx)) (into (row vy)) (into (row vz)) (into [0.0 0.0 0.0 1.0]))))

(defn- perspective
  "raymath.h MatrixPerspective, the frustum BeginMode3D sets with rlFrustum:
  top = near * tan(fovy/2), right = top * aspect."
  [fovy aspect]
  (let [top (* near (Math/tan (* 0.5 (Math/toRadians (double fovy)))))
        right (* top aspect)
        span (- far near)]
    [(/ near right) 0.0 0.0 0.0
     0.0 (/ near top) 0.0 0.0
     0.0 0.0 (- (/ (+ far near) span)) (- (/ (* 2.0 far near) span))
     0.0 0.0 -1.0 0.0]))

(defn- ortho
  "raymath.h MatrixOrtho(-right, right, -top, top, near, far) with
  BeginMode3D's top = fovy/2 and right = top * aspect."
  [fovy aspect]
  (let [top (/ (double fovy) 2.0)
        right (* top aspect)
        span (- far near)]
    [(/ 1.0 right) 0.0 0.0 0.0
     0.0 (/ 1.0 top) 0.0 0.0
     0.0 0.0 (/ -2.0 span) (- (/ (+ far near) span))
     0.0 0.0 0.0 1.0]))

(defn- invert
  "raymath.h MatrixInvert, term for term. Its formula is index-for-index, so
  it serves a row-major array as well as raylib's column-major one."
  [m]
  (let [[a00 a01 a02 a03 a10 a11 a12 a13 a20 a21 a22 a23 a30 a31 a32 a33] m
        b00 (- (* a00 a11) (* a01 a10)) b01 (- (* a00 a12) (* a02 a10))
        b02 (- (* a00 a13) (* a03 a10)) b03 (- (* a01 a12) (* a02 a11))
        b04 (- (* a01 a13) (* a03 a11)) b05 (- (* a02 a13) (* a03 a12))
        b06 (- (* a20 a31) (* a21 a30)) b07 (- (* a20 a32) (* a22 a30))
        b08 (- (* a20 a33) (* a23 a30)) b09 (- (* a21 a32) (* a22 a31))
        b10 (- (* a21 a33) (* a23 a31)) b11 (- (* a22 a33) (* a23 a32))
        inv (/ 1.0 (+ (* b00 b11) (- (* b01 b10)) (* b02 b09) (* b03 b08)
                      (- (* b04 b07)) (* b05 b06)))]
    (mapv (fn [v] (* v inv))
          [(+ (* a11 b11) (- (* a12 b10)) (* a13 b09))
           (+ (- (* a01 b11)) (* a02 b10) (- (* a03 b09)))
           (+ (* a31 b05) (- (* a32 b04)) (* a33 b03))
           (+ (- (* a21 b05)) (* a22 b04) (- (* a23 b03)))
           (+ (- (* a10 b11)) (* a12 b08) (- (* a13 b07)))
           (+ (* a00 b11) (- (* a02 b08)) (* a03 b07))
           (+ (- (* a30 b05)) (* a32 b02) (- (* a33 b01)))
           (+ (* a20 b05) (- (* a22 b02)) (* a23 b01))
           (+ (* a10 b10) (- (* a11 b08)) (* a13 b06))
           (+ (- (* a00 b10)) (* a01 b08) (- (* a03 b06)))
           (+ (* a30 b04) (- (* a31 b02)) (* a33 b00))
           (+ (- (* a20 b04)) (* a21 b02) (- (* a23 b00)))
           (+ (- (* a10 b09)) (* a11 b07) (- (* a12 b06)))
           (+ (* a00 b09) (- (* a01 b07)) (* a02 b06))
           (+ (- (* a30 b03)) (* a31 b01) (- (* a32 b00)))
           (+ (* a20 b03) (- (* a21 b01)) (* a22 b00))])))

;; --- transforms -------------------------------------------------------------

(defn translate
  "`(translate x y z)`: rlgl.h rlTranslatef's matrix, as a row-major 4x4."
  [x y z]
  [1.0 0.0 0.0 (double x)
   0.0 1.0 0.0 (double y)
   0.0 0.0 1.0 (double z)
   0.0 0.0 0.0 1.0])

(defn rotate-axis
  "`(rotate-axis deg x y z)`: rlgl.h rlRotatef(angle, x, y, z), a turn of `deg`
  degrees about the axis (x y z), right-handed. The axis is normalised unless
  its squared length is 0 or 1, as rlRotatef does."
  [deg x y z]
  (let [x (double x) y (double y) z (double z)
        l2 (+ (* x x) (* y y) (* z z))
        il (if (or (== l2 1.0) (zero? l2)) 1.0 (/ 1.0 (Math/sqrt l2)))
        x (* x il) y (* y il) z (* z il)
        a (Math/toRadians (double deg))
        s (Math/sin a)
        c (Math/cos a)
        t (- 1.0 c)]
    ;; rlRotatef's m0 m4 m8 / m1 m5 m9 / m2 m6 m10 are these rows
    [(+ (* x x t) c) (- (* x y t) (* z s)) (+ (* x z t) (* y s)) 0.0
     (+ (* y x t) (* z s)) (+ (* y y t) c) (- (* y z t) (* x s)) 0.0
     (- (* z x t) (* y s)) (+ (* z y t) (* x s)) (+ (* z z t) c) 0.0
     0.0 0.0 0.0 1.0]))

(defn compose
  "`(compose a b & more)`: the product a * b * ..., so the LAST transform applies
  to a point first. Writing the calls in rlgl order gives rlgl's result:
  rlTranslatef then rlRotatef is `(compose (translate ...) (rotate-axis ...))`."
  ([a] a)
  ([a b] (mul a b))
  ([a b & more] (reduce mul (mul a b) more)))

;; --- the camera -------------------------------------------------------------

(defn- det3
  "The determinant of the 3x3 matrix with rows (a b c), (d e f), (g h i)."
  [a b c d e f g h i]
  (- (+ (* a (- (* e i) (* f h))) (* c (- (* d h) (* e g))))
     (* b (- (* d i) (* f g)))))

(defn- eye
  "The camera in the space clip matrix `m` maps from, as the homogeneous point
  `[ex ey ez ew]` with E . v = -det(m's x row, y row, w row, v): the point
  every clip row but z sends to 0. That is the eye of a perspective camera and
  the direction back toward the camera, at infinity, of an orthographic one.
  E . plane is the determinant `facing?` takes, so it is positive when E is on
  a counter-clockwise face's outer side."
  [m]
  (let [[m0 m1 m2 m3 m4 m5 m6 m7 _ _ _ _ m12 m13 m14 m15] m]
    [(det3 m1 m2 m3 m5 m6 m7 m13 m14 m15)
     (- (det3 m0 m2 m3 m4 m6 m7 m12 m14 m15))
     (det3 m0 m1 m3 m4 m5 m7 m12 m13 m15)
     (- (det3 m0 m1 m2 m4 m5 m6 m12 m13 m14))]))

(defn view-proj
  "`(view-proj camera viewport)`: everything a frame needs to project through
  `camera` onto `viewport`, which is `[w h]` at the screen's origin or
  `[x y w h]` anywhere on it.

  `:m` is the clip matrix P * V (rcore.c BeginMode3D: MatrixLookAt, then
  MatrixPerspective or MatrixOrtho, aspect w/h, near 0.05, far 4000) and `:d`
  the view's depth row, the distance in front of the camera along its look.
  The camera and viewport ride along for `screen->ray`. `:eye` is the camera
  as `cube` reads it, computed once a frame from `:m`, which `:eye-of` holds,
  so `cube` can tell when a caller has put another `:m` in."
  [camera viewport]
  (let [[x y w h] (if (= 2 (count viewport)) (into [0 0] viewport) viewport)
        {:keys [position target up fovy projection]} camera
        aspect (/ (double w) (double h))
        v (look-at position target up)
        p (if (= projection :orthographic) (ortho fovy aspect) (perspective fovy aspect))
        m (mul p v)]
    {:m m
     :eye (eye m)
     :eye-of m
     :d [(- (nth v 8)) (- (nth v 9)) (- (nth v 10)) (- (nth v 11))]
     :x (double x)
     :y (double y)
     :w (double w)
     :h (double h)
     :camera camera}))

(defn- row4
  "Row `r` of matrix `m` dotted with the point (x y z 1)."
  [m r x y z]
  (let [i (* 4 r)]
    (+ (* (nth m i) x) (* (nth m (+ i 1)) y) (* (nth m (+ i 2)) z) (nth m (+ i 3)))))

(defn- depth-of [d x y z]
  (+ (* (nth d 0) x) (* (nth d 1) y) (* (nth d 2) z) (nth d 3)))

(defn- project*
  "Project (x y z) through clip matrix `m` and depth row `d` to [sx sy depth],
  or nil when it lies behind the near plane (clip z + w < 0)."
  [m d ox oy w h x y z]
  (let [cw (row4 m 3 x y z)]
    (when (>= (+ (row4 m 2 x y z) cw) 0.0)
      [(+ ox (* 0.5 w (+ 1.0 (/ (row4 m 0 x y z) cw))))
       (+ oy (* 0.5 h (- 1.0 (/ (row4 m 1 x y z) cw))))
       (depth-of d x y z)])))

(defn project
  "`(project vp [x y z])`: the world point's screen position and view depth as
  `[sx sy depth]`, or nil when it is behind the near plane. The screen position
  is GetWorldToScreenEx's."
  [{:keys [m d x y w h]} [px py pz]]
  (project* m d x y w h (double px) (double py) (double pz)))

(defn world->screen
  "`(world->screen vp [x y z])`: rcore.c GetWorldToScreenEx, as `[sx sy]`. Like
  raylib it divides by w whatever side of the camera the point is on; use
  `project` to know."
  [{:keys [m x y w h]} [px py pz]]
  (let [px (double px) py (double py) pz (double pz)
        cw (row4 m 3 px py pz)]
    [(+ x (* 0.5 w (+ 1.0 (/ (row4 m 0 px py pz) cw))))
     (+ y (* 0.5 h (- 1.0 (/ (row4 m 1 px py pz) cw))))]))

(defn- unproject
  "raymath.h Vector3Unproject with the inverse already taken."
  [inv nx ny nz]
  (let [w (row4 inv 3 nx ny nz)]
    [(/ (row4 inv 0 nx ny nz) w) (/ (row4 inv 1 nx ny nz) w) (/ (row4 inv 2 nx ny nz) w)]))

(defn screen->ray
  "`(screen->ray vp [sx sy])`: rcore.c GetScreenToWorldRayEx, as
  `{:position [x y z] :direction [x y z]}`. The direction runs from the
  unprojected ndc z 0 to z 1 and is normalised. The position is the camera's
  for a perspective camera and the unprojected point at ndc z -1 for an
  orthographic one."
  [{:keys [m x y w h camera]} [sx sy]]
  (let [nx (- (/ (* 2.0 (- sx x)) w) 1.0)
        ny (- 1.0 (/ (* 2.0 (- sy y)) h))
        inv (invert m)
        n0 (unproject inv nx ny 0.0)
        n1 (unproject inv nx ny 1.0)]
    {:position (if (= :orthographic (:projection camera))
                 (unproject inv nx ny -1.0)
                 (mapv double (:position camera)))
     :direction (normalize (mapv - n1 n0))}))

(defn- fmin
  "C's fmin: a NaN loses to the other argument."
  [a b]
  (cond (< b a) b (== a a) a :else b))

(defn- fmax [a b]
  (cond (> b a) b (== a a) a :else b))

(defn- trunc
  "C's (int) cast toward zero, kept as a double."
  [v]
  (if (neg? v) (- (Math/floor (- v))) (Math/floor v)))

(defn ray-box
  "`(ray-box ray [min-x min-y min-z] [max-x max-y max-z])`: rmodels.c
  GetRayCollisionBox, as `{:hit? :distance :point :normal}`. `ray` is
  `{:position :direction}`, as `screen->ray` gives. From inside the box raylib
  reverses the ray, then negates the distance and the normal, and so does this."
  [{:keys [position direction]} lo hi]
  (let [[px py pz] (mapv double position)
        [lx ly lz] (mapv double lo)
        [hx hy hz] (mapv double hi)
        inside? (and (> px lx) (< px hx) (> py ly) (< py hy) (> pz lz) (< pz hz))
        [dx dy dz] (if inside? (mapv (fn [v] (- (double v))) direction) (mapv double direction))
        t8 (/ 1.0 dx) t9 (/ 1.0 dy) t10 (/ 1.0 dz)
        t0 (* (- lx px) t8) t1 (* (- hx px) t8)
        t2 (* (- ly py) t9) t3 (* (- hy py) t9)
        t4 (* (- lz pz) t10) t5 (* (- hz pz) t10)
        t6 (fmax (fmax (fmin t0 t1) (fmin t2 t3)) (fmin t4 t5))
        t7 (fmin (fmin (fmax t0 t1) (fmax t2 t3)) (fmax t4 t5))
        point [(+ px (* dx t6)) (+ py (* dy t6)) (+ pz (* dz t6))]
        ;; centre to hit point, scaled to the unit cube with raylib's 2.01,
        ;; truncated toward zero and normalised
        normal (normalize (mapv (fn [p l hi*] (trunc (/ (* 2.01 (- p (* 0.5 (+ l hi*)))) (- hi* l))))
                                point [lx ly lz] [hx hy hz]))]
    {:hit? (not (or (< t7 0.0) (> t6 t7)))
     :distance (if inside? (- t6) t6)
     :point point
     :normal (if inside? (mapv - normal) normal)}))

;; --- builders ---------------------------------------------------------------

(defn- frame
  "The clip matrix and depth row of `vp` with transform `xf` applied first."
  [vp xf]
  (if xf
    (let [d (:d vp)]
      [(mul (:m vp) xf)
       (loop [c 0 out (transient [])]
         (if (< c 4)
           (recur (inc c) (conj! out (+ (* (nth d 0) (nth xf c)) (* (nth d 1) (nth xf (+ c 4)))
                                        (* (nth d 2) (nth xf (+ c 8))) (* (nth d 3) (nth xf (+ c 12))))))
           (persistent! out)))])
    [(:m vp) (:d vp)]))

(defn- sizes [size]
  (if (number? size)
    (let [s (double size)] [s s s])
    (mapv double size)))

(def ^:private faces
  "models.clj cube!'s six quads as corner indices (bit 0 x, bit 1 y, bit 2 z
  at its max) and shades, in its order. Each is counter-clockwise from outside."
  [[4 5 7 6 1.0]     ; front  +z
   [1 0 2 3 0.5]     ; back   -z
   [0 4 6 2 0.7]     ; left   -x
   [5 1 3 7 0.85]    ; right  +x
   [6 7 3 2 1.0]     ; top    +y
   [0 1 5 4 0.4]])   ; bottom -y

(defn- tri
  "Append triangle p q s when its y-down cross product is negative, the winding
  rlgl keeps; drop it otherwise."
  [dl p q s r g b a depth]
  (let [x1 (nth p 0) y1 (nth p 1) x2 (nth q 0) y2 (nth q 1) x3 (nth s 0) y3 (nth s 1)]
    (if (neg? (- (* (- x2 x1) (- y3 y1)) (* (- y2 y1) (- x3 x1))))
      (conj dl [:tri x1 y1 x2 y2 x3 y3 r g b a depth])
      dl)))

(defn- box-face
  "Append the quad on corner slots a b c e of `buf` (`cube`'s scratch array,
  four doubles a corner: screen x, screen y, view depth, and 1.0 when the
  corner is in front of the near plane) as `tri`'s two triangles a b c and
  a c e, coloured r g b al with the quad's mean depth. Each goes in only when
  its y-down cross product is negative, and neither when a corner is behind."
  [dl ^doubles buf a b c e r g bl al]
  (let [ja (* 4 a) jb (* 4 b) jc (* 4 c) je (* 4 e)]
    (if (and (== 1.0 (aget buf (+ ja 3))) (== 1.0 (aget buf (+ jb 3)))
             (== 1.0 (aget buf (+ jc 3))) (== 1.0 (aget buf (+ je 3))))
      (let [xa (aget buf ja) ya (aget buf (+ ja 1))
            xb (aget buf jb) yb (aget buf (+ jb 1))
            xc (aget buf jc) yc (aget buf (+ jc 1))
            xe (aget buf je) ye (aget buf (+ je 1))
            depth (* 0.25 (+ (aget buf (+ ja 2)) (aget buf (+ jb 2))
                             (aget buf (+ jc 2)) (aget buf (+ je 2))))
            dl (if (neg? (- (* (- xb xa) (- yc ya)) (* (- yb ya) (- xc xa))))
                 (conj dl [:tri xa ya xb yb xc yc r g bl al depth])
                 dl)]
        (if (neg? (- (* (- xc xa) (- ye ya)) (* (- yc ya) (- xe xa))))
          (conj dl [:tri xa ya xc yc xe ye r g bl al depth])
          dl))
      dl)))

(defn cube
  "`(cube dl vp xf [x y z] size [r g b a])` or `(cube ... {:shade :flat})`:
  an axis-aligned box centred on the point, under transform `xf` (nil for
  none). `size` is a number or `[sx sy sz]`.

  By default it is raylib-jlt models.clj `cube!`: each face shaded as cube!
  shades it, front +z 1.0, back -z 0.5, left -x 0.7, right +x 0.85, top +y 1.0
  and bottom -y 0.4, by `(int (* shade c))` on r, g and b, with alpha 255.
  With `{:shade :flat}` it is rmodels.c `DrawCube`, which sets one
  rlColor4ub(r, g, b, a) for every face: the colour unchanged, alpha
  included. Use it where the original calls `draw-cube!`.

  A face goes in as cube!'s two triangles, in cube!'s face order, each only
  when it faces the camera, and not at all when a corner is behind the near
  plane.

  Faces are chosen before anything is projected. `eye` gives the camera in
  the box's own space from the clip matrix with `xf` applied, so `xf` is
  undone without `invert`, and with no `xf` it is `view-proj`'s `:eye`. A face
  is a candidate when that point lies on its outer side, by more than a margin
  of 1e-9 of the point's size, so an edge-on face is left to the screen-sign
  test. A mirroring `xf` flips the point's sign and the verdicts with it. Only
  the corners of candidate faces are projected, each once, into one scratch
  array, by the same sums `project` makes, so every item is the one projecting
  all eight corners and testing all six faces would give."
  ([dl vp xf pos size colour] (cube dl vp xf pos size colour {}))
  ([dl vp xf [cx cy cz] size [cr cg cb ca] {:keys [shade]}]
   (let [[m d] (frame vp xf)
         {ox :x
          oy :y
          w :w
          h :h} vp
         n? (number? size)
         sx (double (if n? size (nth size 0)))
         sy (if n? sx (double (nth size 1)))
         sz (if n? sx (double (nth size 2)))
         x0 (- cx (/ sx 2.0)) x1 (+ cx (/ sx 2.0))
         y0 (- cy (/ sy 2.0)) y1 (+ cy (/ sy 2.0))
         z0 (- cz (/ sz 2.0)) z1 (+ cz (/ sz 2.0))
         [m0 m1 m2 m3 m4 m5 m6 m7 m8 m9 m10 m11 m12 m13 m14 m15] m
         [d0 d1 d2 d3] d
         [ex ey ez ew] (if (and (nil? xf) (identical? m (:eye-of vp))) (:eye vp) (eye m))
         ;; the margin: 1e-9 of E's size, the box's extent bounding |v|
         tol (- (* 1.0e-9 (+ (abs ex) (abs ey) (abs ez)
                             (* (abs ew) (+ (abs cx) (abs cy) (abs cz) sx sy sz)))))
         +z? (> (- ez (* z1 ew)) tol) -z? (> (- (* z0 ew) ez) tol)
         -x? (> (- (* x0 ew) ex) tol) +x? (> (- ex (* x1 ew)) tol)
         +y? (> (- ey (* y1 ew)) tol) -y? (> (- (* y0 ew) ey) tol)
         hw (* 0.5 w) hh (* 0.5 h)
         buf (double-array 32)
         corner! (fn [i x y z]
                   (let [j (* 4 i)
                         cw (+ (* m12 x) (* m13 y) (* m14 z) m15)]
                     (when (>= (+ (* m8 x) (* m9 y) (* m10 z) m11 cw) 0.0)
                       (aset buf j (+ ox (* hw (+ 1.0 (/ (+ (* m0 x) (* m1 y) (* m2 z) m3) cw)))))
                       (aset buf (+ j 1) (+ oy (* hh (- 1.0 (/ (+ (* m4 x) (* m5 y) (* m6 z) m7) cw)))))
                       (aset buf (+ j 2) (+ (* d0 x) (* d1 y) (* d2 z) d3))
                       (aset buf (+ j 3) 1.0))))
         flat? (= shade :flat)
         face (fn [dl a b c e f]
                (if flat?
                  (box-face dl buf a b c e cr cg cb ca)
                  (box-face dl buf a b c e (int (* f cr)) (int (* f cg)) (int (* f cb)) 255)))]
     (when (or -z? -x? -y?) (corner! 0 x0 y0 z0))
     (when (or -z? +x? -y?) (corner! 1 x1 y0 z0))
     (when (or -z? -x? +y?) (corner! 2 x0 y1 z0))
     (when (or -z? +x? +y?) (corner! 3 x1 y1 z0))
     (when (or +z? -x? -y?) (corner! 4 x0 y0 z1))
     (when (or +z? +x? -y?) (corner! 5 x1 y0 z1))
     (when (or +z? -x? +y?) (corner! 6 x0 y1 z1))
     (when (or +z? +x? +y?) (corner! 7 x1 y1 z1))
     ;; `faces`' quads and shades, in its order
     (cond-> dl
       +z? (face 4 5 7 6 1.0)
       -z? (face 1 0 2 3 0.5)
       -x? (face 0 4 6 2 0.7)
       +x? (face 5 1 3 7 0.85)
       +y? (face 6 7 3 2 1.0)
       -y? (face 0 1 5 4 0.4)))))

(defn- seg-clip
  "Append a segment given in clip space, (x1 y1 z1 w1)-(x2 y2 z2 w2), clipped
  to the near plane, where clip space is linear. d = z + w is the signed
  distance from the plane. Both ends with d < 0 drop the segment; one end with
  d < 0 slides toward the other to where d is 0, a fraction d1 / (d1 - d2) of
  the way."
  [dl ox oy w h cx1 cy1 cz1 w1 cx2 cy2 cz2 w2 colour layer]
  (let [d1 (+ cz1 w1) d2 (+ cz2 w2)]
    (if (and (neg? d1) (neg? d2))
      dl
      (let [t1 (if (neg? d1) (/ d1 (- d1 d2)) 0.0)
            t2 (if (neg? d2) (/ d2 (- d2 d1)) 0.0)
            ax (+ cx1 (* t1 (- cx2 cx1))) ay (+ cy1 (* t1 (- cy2 cy1))) aw (+ w1 (* t1 (- w2 w1)))
            bx (+ cx2 (* t2 (- cx1 cx2))) by (+ cy2 (* t2 (- cy1 cy2))) bw (+ w2 (* t2 (- w1 w2)))]
        (conj dl [:line
                  (+ ox (* 0.5 w (+ 1.0 (/ ax aw)))) (+ oy (* 0.5 h (- 1.0 (/ ay aw))))
                  (+ ox (* 0.5 w (+ 1.0 (/ bx bw)))) (+ oy (* 0.5 h (- 1.0 (/ by bw))))
                  (nth colour 0) (nth colour 1) (nth colour 2) (nth colour 3)
                  layer])))))

(defn- seg
  "Append the world segment (x1 y1 z1)-(x2 y2 z2) through clip matrix `m`."
  [dl m ox oy w h x1 y1 z1 x2 y2 z2 colour layer]
  (seg-clip dl ox oy w h
            (row4 m 0 x1 y1 z1) (row4 m 1 x1 y1 z1) (row4 m 2 x1 y1 z1) (row4 m 3 x1 y1 z1)
            (row4 m 0 x2 y2 z2) (row4 m 1 x2 y2 z2) (row4 m 2 x2 y2 z2) (row4 m 3 x2 y2 z2)
            colour layer))

(defn lines
  "`(lines dl vp xf segments)` or `(lines dl vp xf segments layer)`: world
  segments `[[x1 y1 z1] [x2 y2 z2] [r g b a]]` under transform `xf` (nil for
  none), each clipped to the near plane. `layer` is `:over` (the default),
  drawn after every face, or `:under`, drawn with the grid before them."
  ([dl vp xf segments] (lines dl vp xf segments :over))
  ([dl vp xf segments layer]
   (let [[m] (frame vp xf)
         {ox :x
          oy :y
          w :w
          h :h} vp]
     (reduce (fn [dl [[x1 y1 z1] [x2 y2 z2] colour]]
               (seg dl m ox oy w h (double x1) (double y1) (double z1)
                    (double x2) (double y2) (double z2) colour layer))
             dl segments))))

(defn- clip4
  "Point (x y z) in clip space as [x y z w]."
  [m x y z]
  [(row4 m 0 x y z) (row4 m 1 x y z) (row4 m 2 x y z) (row4 m 3 x y z)])

(def ^:private wire-edges
  "rmodels.c DrawCubeWires' twelve edges in its order, as corner indices (bit 0
  x, bit 1 y, bit 2 z at its max): the front face, the back face, the top
  joins, the bottom joins."
  [4 5  5 7  7 6  6 4
   0 1  1 3  3 2  2 0
   6 2  7 3
   4 0  5 1])

(def ^:private wire-faces
  "The two faces (indices into `faces`) that meet at each `wire-edges` edge."
  [0 5  0 3  0 4  0 2
   1 5  1 3  1 4  1 2
   2 4  3 4
   2 5  3 5])

(defn- facing?
  "Whether the quad of `faces` entry `f` faces the camera, from the clip-space
  corners `c`. The determinant of three corners' (x y w) is the 2D ndc cross
  product times w1 w2 w3, so it is positive for a counter-clockwise (front)
  face whichever side of the near plane its corners lie, and no projection
  through infinity is needed."
  [c f]
  (let [p (nth c (nth f 0)) q (nth c (nth f 1)) r (nth c (nth f 2))
        px (nth p 0) py (nth p 1) pw (nth p 3)
        qx (nth q 0) qy (nth q 1) qw (nth q 3)
        rx (nth r 0) ry (nth r 1) rw (nth r 3)]
    (pos? (+ (* px (- (* qy rw) (* qw ry)))
             (- (* py (- (* qx rw) (* qw rx))))
             (* pw (- (* qx ry) (* qy rx)))))))

(defn cube-wires
  "`(cube-wires dl vp xf [x y z] size [r g b a])` or
  `(cube-wires ... {:hide-back? true})`: rmodels.c DrawCubeWires, the box's
  twelve edges in its order (front face, back face, then the top and bottom
  joins), under transform `xf` (nil for none). `size` is a number or
  `[width height length]`. They go in the `:over` layer, after every face.

  With no depth buffer, the default draws all twelve, which is what raylib
  shows for wires drawn alone. `{:hide-back? true}` drops each edge whose two
  faces both face away from the camera, which is what raylib's depth test
  hides when an opaque box fills the wires. A scene passes it when it draws
  a solid `cube` of the same box under the wires, as DrawCube followed by
  DrawCubeWires does, and leaves it off for wires with nothing inside."
  ([dl vp xf pos size colour] (cube-wires dl vp xf pos size colour {}))
  ([dl vp xf [cx cy cz] size colour {:keys [hide-back?]}]
   (let [[m] (frame vp xf)
         {ox :x
          oy :y
          w :w
          h :h} vp
         [sx sy sz] (sizes size)
         x0 (- cx (/ sx 2.0)) x1 (+ cx (/ sx 2.0))
         y0 (- cy (/ sy 2.0)) y1 (+ cy (/ sy 2.0))
         z0 (- cz (/ sz 2.0)) z1 (+ cz (/ sz 2.0))
         c [(clip4 m x0 y0 z0) (clip4 m x1 y0 z0) (clip4 m x0 y1 z0) (clip4 m x1 y1 z0)
            (clip4 m x0 y0 z1) (clip4 m x1 y0 z1) (clip4 m x0 y1 z1) (clip4 m x1 y1 z1)]
         front (when hide-back? (mapv (fn [f] (facing? c f)) faces))]
     (loop [i 0 dl dl]
       (if (< i 24)
         (if (and front
                  (not (nth front (nth wire-faces i)))
                  (not (nth front (nth wire-faces (inc i)))))
           (recur (+ i 2) dl)
           (let [p (nth c (nth wire-edges i)) q (nth c (nth wire-edges (inc i)))]
             (recur (+ i 2)
                    (seg-clip dl ox oy w h (nth p 0) (nth p 1) (nth p 2) (nth p 3)
                              (nth q 0) (nth q 1) (nth q 2) (nth q 3) colour :over))))
         dl)))))

(def ^:private grid-centre [127 127 127 255])   ; rlColor3f(0.5, ...): (unsigned char)(0.5*255)
(def ^:private grid-rest [191 191 191 255])     ; rlColor3f(0.75, ...): (unsigned char)(0.75*255)

(defn grid
  "`(grid dl vp slices spacing)`: rmodels.c DrawGrid on the y = 0 plane. For i
  from -slices/2 to slices/2 (integer halves), a line along z at x = i*spacing
  and one along x at z = i*spacing, in that order; the i = 0 pair is
  rlColor3f(0.5) and the rest rlColor3f(0.75). They go in the `:under` layer."
  [dl vp slices spacing]
  (let [{m :m
         ox :x
         oy :y
         w :w
         h :h} vp
        half (quot (long slices) 2)
        s (double spacing)
        e (* half s)]
    (loop [i (- half) dl dl]
      (if (<= i half)
        (let [colour (if (zero? i) grid-centre grid-rest)
              v (* i s)]
          (recur (inc i)
                 (-> dl
                     (seg m ox oy w h v 0.0 (- e) v 0.0 e colour :under)
                     (seg m ox oy w h (- e) 0.0 v e 0.0 v colour :under))))
        dl))))

(defn- sphere-row
  "The projected vertices of one latitude ring of `sphere`: slices + 1 of them,
  the last on the first's longitude so every quad reads j and j + 1."
  [m d ox oy w h cx cy cz radius lat coss sins slices]
  (let [y (+ cy (* radius (Math/sin lat)))
        rr (* radius (Math/cos lat))]
    (loop [j 0 out (transient [])]
      (if (<= j slices)
        (recur (inc j) (conj! out (project* m d ox oy w h
                                            (+ cx (* rr (nth coss j))) y
                                            (+ cz (* rr (nth sins j))))))
        (persistent! out)))))

(defn sphere
  "`(sphere dl vp xf [x y z] radius [r g b a])` or `(sphere ... {:rings 12
  :slices 16})`: a latitude and longitude sphere centred on the point, under
  transform `xf` (nil for none). It is raylib-jlt models.clj `sphere!` (the
  stand-in for rmodels.c DrawSphereEx, which has no depth sorting to mirror).

  Ring i spans latitude -pi/2 + pi*i/rings to -pi/2 + pi*(i+1)/rings and slice
  j spans longitude 2*pi*j/slices to 2*pi*(j+1)/slices. Each quad is
  `[p00 p10 p11 p01]` (p<ring edge><longitude>: p0 is the lower latitude) and
  goes in as sphere!'s two triangles, (p00 p10 p11) and (p00 p11 p01). The band is
  shaded by `0.45 + 0.55 * (y0 + y1 + 2) / 4`, y0 and y1 being the sines of its
  two latitudes, by `(int (* f c))` on r, g and b with alpha 255, as sphere!'s
  `shade-color`.

  With `{:shade :flat}` every band takes the colour unchanged, alpha
  included, as rmodels.c DrawSphere sets one rlColor4ub. Use it where the
  original calls `draw-sphere!`.

  Each triangle goes in only when it faces the camera, and a quad with a corner
  behind the near plane is dropped, as `cube` does. A quad touching a pole has
  two coincident corners, so one of its triangles is a sliver. It is usually
  culled, but a view from straight above can emit two of them, each under a
  millionth of a pixel."
  ([dl vp xf pos radius colour] (sphere dl vp xf pos radius colour {}))
  ([dl vp xf [cx cy cz] radius [cr cg cb ca] {:keys [rings slices shade]
                                              :or {rings 12
                                                   slices 16}}]
   (let [[m d] (frame vp xf)
         {ox :x
          oy :y
          w :w
          h :h} vp
         cx (double cx) cy (double cy) cz (double cz) radius (double radius)
         two-pi (* 2.0 Math/PI)
         lon (fn [j] (* two-pi (/ (double j) slices)))
         coss (mapv (fn [j] (Math/cos (lon j))) (range (inc slices)))
         sins (mapv (fn [j] (Math/sin (lon j))) (range (inc slices)))
         lat (fn [i] (- (* Math/PI (/ (double i) rings)) (/ Math/PI 2.0)))
         row (fn [i] (sphere-row m d ox oy w h cx cy cz radius (lat i) coss sins slices))]
     (loop [i 0 below (row 0) dl dl]
       (if (< i rings)
         (let [above (row (inc i))
               y0 (Math/sin (lat i)) y1 (Math/sin (lat (inc i)))
               f (+ 0.45 (* 0.55 (/ (+ y0 y1 2.0) 4.0)))
               flat? (= shade :flat)
               r (if flat? cr (int (* f cr))) g (if flat? cg (int (* f cg))) b (if flat? cb (int (* f cb)))
               al (if flat? (or ca 255) 255)]
           (recur (inc i) above
                  (loop [j 0 dl dl]
                    (if (< j slices)
                      (let [p00 (nth below j) p01 (nth below (inc j))
                            p10 (nth above j) p11 (nth above (inc j))]
                        (recur (inc j)
                               (if (and p00 p01 p10 p11)
                                 (let [depth (* 0.25 (+ (nth p00 2) (nth p01 2) (nth p10 2) (nth p11 2)))]
                                   (-> dl
                                       (tri p00 p10 p11 r g b al depth)
                                       (tri p00 p11 p01 r g b al depth)))
                                 dl)))
                      dl))))
         dl)))))

(defn plane
  "`(plane dl vp xf [x y z] [sx sz] [r g b a])`: rmodels.c DrawPlane, the unit
  quad on the XZ plane scaled to `sx` by `sz` and centred on the point, under
  transform `xf` (nil for none). Its normal is +y and its corners are
  (-.5 0 -.5), (-.5 0 .5), (.5 0 .5), (.5 0 -.5) of that, as DrawPlane's
  rlVertex3f calls, drawn as the triangles (1 2 3) and (1 3 4). `size` may be
  one number for a square. The colour is flat, alpha kept, as rlColor4ub.

  It is one-sided like raylib's: each triangle goes in only when it faces the
  camera, so a plane seen from below draws nothing. A corner behind the near
  plane drops it whole."
  [dl vp xf [cx cy cz] size [cr cg cb ca]]
  (let [[m d] (frame vp xf)
        {ox :x
         oy :y
         w :w
         h :h} vp
        [sx sz] (if (number? size) [size size] size)
        hx (/ (double sx) 2.0) hz (/ (double sz) 2.0)
        cx (double cx) cy (double cy) cz (double cz)
        p1 (project* m d ox oy w h (- cx hx) cy (- cz hz))
        p2 (project* m d ox oy w h (- cx hx) cy (+ cz hz))
        p3 (project* m d ox oy w h (+ cx hx) cy (+ cz hz))
        p4 (project* m d ox oy w h (+ cx hx) cy (- cz hz))]
    (if (and p1 p2 p3 p4)
      (let [depth (* 0.25 (+ (nth p1 2) (nth p2 2) (nth p3 2) (nth p4 2)))]
        (-> dl
            (tri p1 p2 p3 cr cg cb ca depth)
            (tri p1 p3 p4 cr cg cb ca depth)))
      dl)))

(defn- rotate-about
  "raymath.h Vector3RotateByAxisAngle: `v` turned `rad` radians about the unit
  vector `k`, right-handed (Rodrigues)."
  [v k rad]
  (let [c (Math/cos rad) s (Math/sin rad) kv (dot3 k v)
        kxv (cross3 k v)]
    (mapv (fn [vi xi ki] (+ (* vi c) (* xi s) (* ki kv (- 1.0 c)))) v kxv k)))

(defn billboard-corners
  "`(billboard-corners camera [x y z] size opts)`: the four world corners of a
  camera-facing quad, bottom-left, bottom-right, top-right, top-left, in
  rmodels.c DrawBillboardPro's order (its points 0 to 3), as three-vectors of
  doubles. `size` is a number or `[sx sy]`, both positive.

  `right` is the first row of MatrixLookAt(camera.position, camera.target,
  camera.up), which is where Pro reads m0, m4 and m8, scaled by `sx`. `up` is
  the camera's own up, row two of the same matrix, scaled by `sy`, so the quad
  squarely faces the camera. DrawBillboardRec passes the world up (0, 1, 0)
  there instead, which stands the quad on its feet; `{:up [0 1 0]}` does that.

  Options, all optional:
  - `:up` the up vector, normalised or not, before it is scaled by `sy`;
  - `:origin` `[ox oy]`, Pro's origin in world units from the quad's
    bottom-left, along `right` and `up`; the default is half of `size`, which
    DrawBillboardRec passes, so the position is the centre;
  - `:rotation` degrees about the quad's own normal, `right` x `up`, which points
    at the camera, turning about the origin as seen from it, as Pro does;
  - `:part` `[fx0 fy0 fx1 fy1]`, a rectangle of the quad in fractions from its
    bottom-left corner (the whole quad is `[0 0 1 1]`). It is the one thing here
    that raylib has no equivalent for: raylib's `source` rectangle picks a part of
    the TEXTURE for the whole quad, where this picks a part of the QUAD, so a
    figure can be built of flat sub-quads that turn and move together."
  [camera [px py pz] size {:keys [up origin rotation part]}]
  (let [[sx sy] (if (number? size) [size size] size)
        sx (double sx) sy (double sy)
        v (look-at (:position camera) (:target camera) (:up camera))
        right0 [(nth v 0) (nth v 1) (nth v 2)]
        up0 (if up (mapv double up) [(nth v 4) (nth v 5) (nth v 6)])
        scale (fn [[x y z] k] [(* x k) (* y k) (* z k)])
        add (fn [a b] (mapv + a b))
        right (scale right0 sx)
        upv (scale up0 sy)
        [ox oy] (or origin [(* 0.5 sx) (* 0.5 sy)])
        origin3 (add (scale (normalize right) (double ox)) (scale (normalize upv) (double oy)))
        turn (when (and rotation (not (zero? rotation)))
               [(normalize (cross3 right upv)) (Math/toRadians (double rotation))])
        [fx0 fy0 fx1 fy1] (or part [0.0 0.0 1.0 1.0])
        at (fn [fx fy]
             (let [p (mapv - (add (scale right (double fx)) (scale upv (double fy))) origin3)
                   p (if turn (rotate-about p (nth turn 0) (nth turn 1)) p)]
               (add p [(double px) (double py) (double pz)])))]
    [(at fx0 fy0) (at fx1 fy0) (at fx1 fy1) (at fx0 fy1)]))

(defn billboard
  "`(billboard dl vp [x y z] size [r g b a])` or `(billboard ... opts)`: a flat
  coloured quad that faces the camera, at the point, as the two triangles
  rmodels.c DrawBillboardPro draws for a texture, here with one colour in place
  of the texture. The corners, `size` and `opts` are `billboard-corners`'. The
  triangles are the quad's corners 0 1 2 and 0 2 3, counter-clockwise from the
  camera, so both keep rlgl's front winding and a quad that has turned edge on
  draws nothing. A corner behind the near plane drops it whole. The colour is
  flat, because an item carries one colour, so there is no vertex-coloured
  variant; a gradient is more quads. There is no `xf`: a billboard faces the
  camera, so only its position could move; pass the moved point."
  ([dl vp pos size colour] (billboard dl vp pos size colour nil))
  ([dl vp pos size [r g b a] opts]
   (let [[c0 c1 c2 c3] (billboard-corners (:camera vp) pos size (or opts {}))
         p0 (project vp c0) p1 (project vp c1) p2 (project vp c2) p3 (project vp c3)]
     (if (and p0 p1 p2 p3)
       (let [depth (* 0.25 (+ (nth p0 2) (nth p1 2) (nth p2 2) (nth p3 2)))]
         (-> dl
             (tri p0 p1 p2 r g b a depth)
             (tri p0 p2 p3 r g b a depth)))
       dl))))

;; --- cylinders and capsules -------------------------------------------------

(def ^:private deg2rad (/ Math/PI 180.0))

(defn- tri3
  "Append triangle p q s, three projected `[sx sy depth]` points (any nil means
  a corner behind the near plane and drops it), at their mean depth, when it
  faces the camera."
  [dl p q s r g b a]
  (if (and p q s)
    (tri dl p q s r g b a (/ (+ (nth p 2) (nth q 2) (nth s 2)) 3.0))
    dl))

(defn- ring-trig
  "`[sins coss]` of i * (360 / n) degrees for i from 0 to n, rmodels.c's
  `sinf(DEG2RAD*i*angleStep)` and `cosf(...)`. Index n repeats index 0."
  [n]
  (let [step (/ 360.0 n)
        angles (mapv (fn [i] (* deg2rad i step)) (range (inc n)))]
    [(mapv (fn [a] (Math/sin a)) angles) (mapv (fn [a] (Math/cos a)) angles)]))

(defn- cyl-ring
  "`pt` of the n + 1 points (px + sin * rad, y, pz + cos * rad)."
  [pt px y pz rad sins coss n]
  (mapv (fn [i] (pt (+ px (* (nth sins i) rad)) y (+ pz (* (nth coss i) rad)))) (range (inc n))))

(defn cylinder
  "`(cylinder dl vp xf [x y z] radius-top radius-bottom height [r g b a])` or
  `(cylinder ... {:slices 16})`: rmodels.c DrawCylinder, the bottom ring at the
  point and the top `height` above it, under transform `xf` (nil for none).
  `:slices` is the C's `sides`, below 3 taken as 3. Vertex k of a ring is
  `(sin, cos)` of `k * 360 / sides` degrees times its radius, on x and z.

  With a top radius above 0 it is the C's body (two triangles a side, bottom
  left / bottom right / top right and top left / bottom left / top right),
  then the top cap (the centre and ring k, k + 1), then the base; with 0 or
  less it is the C's cone: the apex (0, height, 0) and bottom ring k, k + 1
  instead of the body and cap. The base is the bottom centre, ring k + 1, ring
  k, so it faces down. The colour is flat, alpha kept, as rlColor4ub.

  Each triangle goes in only when it faces the camera, and one with a corner
  behind the near plane is dropped whole, as `cube` does. There is no depth
  buffer: `finish` orders the triangles."
  ([dl vp xf pos r-top r-bottom height colour] (cylinder dl vp xf pos r-top r-bottom height colour {}))
  ([dl vp xf [px py pz] r-top r-bottom height [cr cg cb ca] {:keys [slices]
                                                             :or {slices 16}}]
   (let [[m d] (frame vp xf)
         {ox :x
          oy :y
          w :w
          h :h} vp
         n (max 3 (long slices))
         [sins coss] (ring-trig n)
         px (double px) py (double py) pz (double pz)
         rt (double r-top) rb (double r-bottom) top-y (+ py (double height))
         pt (fn [x y z] (project* m d ox oy w h x y z))
         bot (cyl-ring pt px py pz rb sins coss n)
         c0 (pt px py pz)
         c1 (pt px top-y pz)
         each (fn [dl f] (loop [i 0 dl dl] (if (< i n) (recur (inc i) (f dl i)) dl)))]
     (as-> dl dl
       (if (> rt 0.0)
         (let [top (cyl-ring pt px top-y pz rt sins coss n)]
           (-> dl
               (each (fn [dl i]
                       (let [bl (nth bot i) br (nth bot (inc i))
                             tl (nth top i) tr (nth top (inc i))]
                         (-> dl
                             (tri3 bl br tr cr cg cb ca)
                             (tri3 tl bl tr cr cg cb ca)))))
               (each (fn [dl i] (tri3 dl c1 (nth top i) (nth top (inc i)) cr cg cb ca)))))
         (each dl (fn [dl i] (tri3 dl c1 (nth bot i) (nth bot (inc i)) cr cg cb ca))))
       (each dl (fn [dl i] (tri3 dl c0 (nth bot (inc i)) (nth bot i) cr cg cb ca)))))))

(defn- seg4
  "Append the segment between clip-space points p and q (each `[x y z w]`),
  clipped to the near plane, in the `:over` layer."
  [dl ox oy w h p q colour]
  (seg-clip dl ox oy w h (nth p 0) (nth p 1) (nth p 2) (nth p 3)
            (nth q 0) (nth q 1) (nth q 2) (nth q 3) colour :over))

(defn cylinder-wires
  "`(cylinder-wires dl vp xf [x y z] radius-top radius-bottom height [r g b a])`
  or with `{:slices 16}`: rmodels.c DrawCylinderWires. Four segments a side, in
  the C's order: bottom ring k to k + 1, bottom k + 1 up to top k + 1, top ring
  k + 1 to k, top k down to bottom k. A zero top radius keeps the C's
  collapsed top edges. Each is clipped to the near plane and goes in the
  `:over` layer, after every face, so back edges show through a solid one."
  ([dl vp xf pos r-top r-bottom height colour] (cylinder-wires dl vp xf pos r-top r-bottom height colour {}))
  ([dl vp xf [px py pz] r-top r-bottom height colour {:keys [slices]
                                                      :or {slices 16}}]
   (let [[m] (frame vp xf)
         {ox :x
          oy :y
          w :w
          h :h} vp
         n (max 3 (long slices))
         [sins coss] (ring-trig n)
         px (double px) py (double py) pz (double pz)
         pt (fn [x y z] (clip4 m x y z))
         bot (cyl-ring pt px py pz (double r-bottom) sins coss n)
         top (cyl-ring pt px (+ py (double height)) pz (double r-top) sins coss n)]
     (loop [i 0 dl dl]
       (if (< i n)
         (let [b0 (nth bot i) b1 (nth bot (inc i)) t0 (nth top i) t1 (nth top (inc i))]
           (recur (inc i)
                  (-> dl
                      (seg4 ox oy w h b0 b1 colour)
                      (seg4 ox oy w h b1 t1 colour)
                      (seg4 ox oy w h t1 t0 colour)
                      (seg4 ox oy w h t0 b0 colour))))
         dl)))))

(defn- perpendicular
  "raymath.h Vector3Perpendicular: v crossed with the cardinal axis along its
  smallest component (x on a tie, then y, then z only when strictly smaller)."
  [[x y z :as v]]
  (let [ax (abs (double x)) ay (abs (double y)) az (abs (double z))
        [mn axis] (if (< ay ax) [ay [0.0 1.0 0.0]] [ax [1.0 0.0 0.0]])]
    (cross3 v (if (< az mn) [0.0 0.0 1.0] axis))))

(defn- capsule-basis
  "`[b0 b1 b2 sphere?]` as rmodels.c DrawCapsule builds them: b0 the unit
  direction (0 1 0 when the ends coincide, the C's sphere case), b1 the unit
  perpendicular and b2 the unit b1 x direction."
  [[sx sy sz] [ex ey ez]]
  (let [dx (- (double ex) (double sx)) dy (- (double ey) (double sy)) dz (- (double ez) (double sz))
        sphere? (and (zero? dx) (zero? dy) (zero? dz))
        dir (if sphere? [0.0 1.0 0.0] [dx dy dz])
        b1 (normalize (perpendicular dir))]
    [(normalize dir) b1 (normalize (cross3 b1 dir)) sphere?]))

(defn- cap-grid
  "The (rings + 1) by (slices + 1) points `pt` of one hemisphere cap about
  `centre`: row i, column j is c + r * (sin(ri) b0 + sin(sj) cos(ri) b1 + cos(sj) cos(ri) b2),
  with ri = i * (pi/2) / rings and sj = j * 2pi / slices."
  [pt [cx cy cz] [b0x b0y b0z] [b1x b1y b1z] [b2x b2y b2z] radius rings slices]
  (let [sa (/ (* 2.0 Math/PI) slices)
        ra (/ (* Math/PI 0.5) rings)
        sj (mapv (fn [j] (Math/sin (* sa j))) (range (inc slices)))
        cj (mapv (fn [j] (Math/cos (* sa j))) (range (inc slices)))]
    (mapv (fn [i]
            (let [sr (Math/sin (* ra i)) cr (Math/cos (* ra i))]
              (mapv (fn [j]
                      (let [rs (* (nth sj j) cr) rc (* (nth cj j) cr)]
                        (pt (+ cx (* (+ (* sr b0x) (* rs b1x) (* rc b2x)) radius))
                            (+ cy (* (+ (* sr b0y) (* rs b1y) (* rc b2y)) radius))
                            (+ cz (* (+ (* sr b0z) (* rs b1z) (* rc b2z)) radius)))))
                    (range (inc slices)))))
          (range (inc rings)))))

(defn- mid-ring
  "The slices + 1 points `pt` of the capsule's middle ring about `centre`:
  c + sin(sj) r b1 + cos(sj) r b2."
  [pt [cx cy cz] [b1x b1y b1z] [b2x b2y b2z] radius slices]
  (let [sa (/ (* 2.0 Math/PI) slices)]
    (mapv (fn [j]
            (let [rs (* (Math/sin (* sa j)) radius) rc (* (Math/cos (* sa j)) radius)]
              (pt (+ cx (* rs b1x) (* rc b2x)) (+ cy (* rs b1y) (* rc b2y)) (+ cz (* rs b1z) (* rc b2z)))))
          (range (inc slices)))))

(defn- capsule-parts
  "What both capsule builders walk: `[grid-end grid-start mid-start mid-end sphere?]`
  for `pt`, the caps first (the end's, then the start's with b0 negated)."
  [pt start end radius rings slices]
  (let [[b0 b1 b2 sphere?] (capsule-basis start end)
        radius (double radius)
        start (mapv double start) end (mapv double end)]
    [(cap-grid pt end b0 b1 b2 radius rings slices)
     (cap-grid pt start (mapv - b0) b1 b2 radius rings slices)
     (mid-ring pt start b1 b2 radius slices)
     (mid-ring pt end b1 b2 radius slices)
     sphere?]))

(defn capsule
  "`(capsule dl vp xf start end radius [r g b a])` or `(capsule ... {:slices 8
  :rings 8})`: rmodels.c DrawCapsule, two hemispheres centred on `start` and
  `end` and the tube between them, under transform `xf` (nil for none).
  `:slices` below 3 is taken as 3.

  The basis is the C's: b0 the unit direction, b1 the unit
  Vector3Perpendicular and b2 the unit b1 x direction (start = end is the C's
  sphere case, direction (0 1 0), with no tube). Each cap has `rings` rings
  from its equator to its pole and `slices` slices, and a cell with corners w1
  (ring i, slice j), w2 (ring i, slice j + 1), w3 (ring i + 1, slice j) and w4
  is w1 w2 w3 and w2 w4 w3 on the end cap, w1 w3 w2 and w2 w3 w4 on the start
  cap (its b0 is negated), so both face outward. The tube is w1 w2 w3 and w2
  w4 w3 a slice, w1 w2 on `start` and w3 w4 on `end`. The colour is flat.

  Each triangle goes in only when it faces the camera, and one with a corner
  behind the near plane is dropped whole. `finish` orders the triangles."
  ([dl vp xf start end radius colour] (capsule dl vp xf start end radius colour {}))
  ([dl vp xf start end radius [cr cg cb ca] {:keys [slices rings]
                                             :or {slices 8
                                                  rings 8}}]
   (let [[m d] (frame vp xf)
         {ox :x
          oy :y
          w :w
          h :h} vp
         slices (max 3 (long slices)) rings (long rings)
         pt (fn [x y z] (project* m d ox oy w h x y z))
         [g-end g-start m-start m-end sphere?] (capsule-parts pt start end radius rings slices)
         cap (fn [dl grid end?]
               (loop [i 0 dl dl]
                 (if (< i rings)
                   (let [lo (nth grid i) hi (nth grid (inc i))]
                     (recur (inc i)
                            (loop [j 0 dl dl]
                              (if (< j slices)
                                (let [w1 (nth lo j) w2 (nth lo (inc j)) w3 (nth hi j) w4 (nth hi (inc j))]
                                  (recur (inc j)
                                         (if end?
                                           (-> dl
                                               (tri3 w1 w2 w3 cr cg cb ca)
                                               (tri3 w2 w4 w3 cr cg cb ca))
                                           (-> dl
                                               (tri3 w1 w3 w2 cr cg cb ca)
                                               (tri3 w2 w3 w4 cr cg cb ca)))))
                                dl))))
                   dl)))
         dl (-> dl (cap g-end true) (cap g-start false))]
     (if sphere?
       dl
       (loop [j 0 dl dl]
         (if (< j slices)
           (let [w1 (nth m-start j) w2 (nth m-start (inc j)) w3 (nth m-end j) w4 (nth m-end (inc j))]
             (recur (inc j)
                    (-> dl
                        (tri3 w1 w2 w3 cr cg cb ca)
                        (tri3 w2 w4 w3 cr cg cb ca))))
           dl))))))

(defn capsule-wires
  "`(capsule-wires dl vp xf start end radius [r g b a])` or with `{:slices 8
  :rings 8}`: rmodels.c DrawCapsuleWires, on the same grid as `capsule`. A cap
  cell is five segments, w1 w2, w2 w3, w1 w3, w2 w4 and w3 w4, caps first (end,
  then start), then the tube's three a slice: w1 w3, w2 w4 and w2 w3. Each is
  clipped to the near plane and goes in the `:over` layer, so back edges show
  through a solid one."
  ([dl vp xf start end radius colour] (capsule-wires dl vp xf start end radius colour {}))
  ([dl vp xf start end radius colour {:keys [slices rings]
                                      :or {slices 8
                                           rings 8}}]
   (let [[m] (frame vp xf)
         {ox :x
          oy :y
          w :w
          h :h} vp
         slices (max 3 (long slices)) rings (long rings)
         pt (fn [x y z] (clip4 m x y z))
         [g-end g-start m-start m-end sphere?] (capsule-parts pt start end radius rings slices)
         s (fn [dl p q] (seg4 dl ox oy w h p q colour))
         cap (fn [dl grid]
               (loop [i 0 dl dl]
                 (if (< i rings)
                   (let [lo (nth grid i) hi (nth grid (inc i))]
                     (recur (inc i)
                            (loop [j 0 dl dl]
                              (if (< j slices)
                                (let [w1 (nth lo j) w2 (nth lo (inc j)) w3 (nth hi j) w4 (nth hi (inc j))]
                                  (recur (inc j)
                                         (-> dl
                                             (s w1 w2) (s w2 w3) (s w1 w3) (s w2 w4) (s w3 w4))))
                                dl))))
                   dl)))
         dl (-> dl (cap g-end) (cap g-start))]
     (if sphere?
       dl
       (loop [j 0 dl dl]
         (if (< j slices)
           (let [w1 (nth m-start j) w2 (nth m-start (inc j)) w3 (nth m-end j) w4 (nth m-end (inc j))]
             (recur (inc j)
                    (-> dl (s w1 w3) (s w2 w4) (s w2 w3))))
           dl))))))

;; --- finishing --------------------------------------------------------------

(defn finish
  "`(finish dl)`: the draw list in paint order. `:under` lines first, in the
  order they were built; then every `:tri` far to near by its mean view depth;
  then `:over` lines in build order. Items are not copied, only reordered. The
  builders have already dropped back faces and clipped lines, so every `:tri`
  here has the negative y-down cross product rlgl keeps."
  [dl]
  (let [n (count dl)]
    (loop [i 0 under (transient []) tris (transient []) over (transient [])]
      (if (< i n)
        (let [it (nth dl i)]
          (cond (= :tri (nth it 0)) (recur (inc i) under (conj! tris it) over)
                (= :under (nth it 9)) (recur (inc i) (conj! under it) tris over)
                :else (recur (inc i) under tris (conj! over it))))
        (let [sorted (sort (fn [a b] (compare (nth b 11) (nth a 11))) (persistent! tris))]
          (persistent! (reduce conj! (reduce conj! under sorted) (persistent! over))))))))

(defn fit-camera
  "`(fit-camera camera orig-aspect field-aspect)`: `camera` with its `:fovy`
  adjusted so the view it had in an original window of aspect `orig-aspect`
  (w/h) fits a field of aspect `field-aspect`. fovy is vertical, so a field
  narrower than the original would show less across. When the field is at
  least as wide as the original the camera is returned as it was. Otherwise the
  original's horizontal extent is kept:

  - perspective: hfov = 2 atan(tan(fovy/2) * orig-aspect), then the new
    fovy = 2 atan(tan(hfov/2) / field-aspect), in degrees;
  - orthographic: fovy is a height in world units, so it is scaled by
    orig-aspect / field-aspect.

  Everything the original showed across stays visible, and a taller field
  shows more above and below."
  [camera orig-aspect field-aspect]
  (if (>= field-aspect orig-aspect)
    camera
    (let [ratio (/ (double orig-aspect) (double field-aspect))
          fovy (double (:fovy camera))]
      (assoc camera :fovy
             (if (= (:projection camera) :orthographic)
               (* fovy ratio)
               (Math/toDegrees
                (* 2.0 (Math/atan (* ratio (Math/tan (* 0.5 (Math/toRadians fovy))))))))))))

(defn field
  "`(field metrics)` or `(field metrics widest)`: the layout every 3D scene
  shares, for `metrics`' `:screen` `[w h]`. A caption line sits below Back and
  the 3D field fills the rest, full width to the bottom. Back's bottom is read
  from `net.b12n.raylib-ios.gesture/back-region`, never a literal. Returns

  - `:viewport` `[x y w h]`, the field in scene pixels, to hand to `view-proj`
    and to scissor the draw to;
  - `:aspect` `w/h` of the field, for `fit-camera`;
  - `:size`, `:pad` and `:text-y`, the caption's text size, the gap around it
    and its y, so the caption sits between Back and the field.

  The size is the larger of 16 and 0.03 of the shorter side. With `widest`,
  the width of the widest caption measured at size 100 (`(measure s 100)`), it
  is cut back, to 8 at the least, so that caption covers no more than 0.92 of
  the width. A scene builds its own `{:s :x :y :size}` lines from these."
  ([metrics] (field metrics nil))
  ([metrics widest]
   (let [[w h] (:screen metrics)
         [_ back-y _ back-h] gesture/back-region
         base (max 16 (int (* 0.03 (min w h))))
         size (if widest (max 8 (min base (int (/ (* 0.92 w 100.0) widest)))) base)
         pad (max 8 (int (* 0.5 base)))
         text-y (+ back-y back-h pad)
         ftop (+ text-y base pad)]
     {:size size
      :pad pad
      :text-y text-y
      :aspect (/ (double w) (- h ftop))
      :viewport [0.0 (double ftop) (double w) (double (- h ftop))]})))
