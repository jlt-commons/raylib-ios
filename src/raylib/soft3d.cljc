(ns raylib.soft3d
  "raylib's 3D camera and a handful of its 3D primitives, projected in software
  into a 2D draw list that `raylib.host/draw-3d!` emits.

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
  `cube-wires`, `grid`, `lines`), then `finish` it into the draw list. Every
  builder projects as it goes, so a back face, or a face behind the near plane,
  never becomes an item, and a line behind it is clipped to it instead.

  The draw list is a vector of flat items:

  - `[:tri x1 y1 x2 y2 x3 y3 r g b a depth]`
  - `[:line x1 y1 x2 y2 r g b a layer]`

  The trailing `depth` (mean view depth of the face) and `layer` (`:under` or
  `:over`) are the sort keys `finish` reads. `raylib.host/draw-3d!` ignores
  them, so `finish` hands the items on without copying them.

  Visibility is decided the way batch 8's `raylib.scenes.helitorus` decides
  it, by screen-space sign. Each cube face is wound counter-clockwise seen from
  outside, so in this y-down screen space a face toward the camera has a
  NEGATIVE cross product of its first two edges. That is the winding rlgl
  keeps (`raylib.host/draw-triangle`). A triangle with a cross product of zero
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
  (:require [raylib.gesture :as gesture]))

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

(defn view-proj
  "`(view-proj camera viewport)`: everything a frame needs to project through
  `camera` onto `viewport`, which is `[w h]` at the screen's origin or
  `[x y w h]` anywhere on it.

  `:m` is the clip matrix P * V (rcore.c BeginMode3D: MatrixLookAt, then
  MatrixPerspective or MatrixOrtho, aspect w/h, near 0.05, far 4000) and `:d`
  the view's depth row, the distance in front of the camera along its look.
  The camera and viewport ride along for `screen->ray`."
  [camera viewport]
  (let [[x y w h] (if (= 2 (count viewport)) (into [0 0] viewport) viewport)
        {:keys [position target up fovy projection]} camera
        aspect (/ (double w) (double h))
        v (look-at position target up)
        p (if (= projection :orthographic) (ortho fovy aspect) (perspective fovy aspect))]
    {:m (mul p v)
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

  A face goes in as cube!'s two triangles, each only when it faces the camera,
  and not at all when a corner is behind the near plane."
  ([dl vp xf pos size colour] (cube dl vp xf pos size colour {}))
  ([dl vp xf [cx cy cz] size [cr cg cb ca] {:keys [shade]}]
   (let [[m d] (frame vp xf)
         {ox :x
          oy :y
          w :w
          h :h} vp
         [sx sy sz] (sizes size)
         x0 (- cx (/ sx 2.0)) x1 (+ cx (/ sx 2.0))
         y0 (- cy (/ sy 2.0)) y1 (+ cy (/ sy 2.0))
         z0 (- cz (/ sz 2.0)) z1 (+ cz (/ sz 2.0))
         c [(project* m d ox oy w h x0 y0 z0) (project* m d ox oy w h x1 y0 z0)
            (project* m d ox oy w h x0 y1 z0) (project* m d ox oy w h x1 y1 z0)
            (project* m d ox oy w h x0 y0 z1) (project* m d ox oy w h x1 y0 z1)
            (project* m d ox oy w h x0 y1 z1) (project* m d ox oy w h x1 y1 z1)]]
     (loop [i 0 dl dl]
       (if (< i 6)
         (let [f (nth faces i)
               a (nth c (nth f 0)) b (nth c (nth f 1))
               e (nth c (nth f 2)) g (nth c (nth f 3))]
           (if (and a b e g)
             (let [flat? (= shade :flat)
                   f (nth f 4)
                   r (if flat? cr (int (* f cr)))
                   gg (if flat? cg (int (* f cg)))
                   bb (if flat? cb (int (* f cb)))
                   aa (if flat? ca 255)
                   depth (* 0.25 (+ (nth a 2) (nth b 2) (nth e 2) (nth g 2)))]
               (recur (inc i) (-> dl (tri a b e r gg bb aa depth) (tri a e g r gg bb aa depth))))
             (recur (inc i) dl)))
         dl)))))

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
  from `raylib.gesture/back-region`, never a literal. Returns

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
