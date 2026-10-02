(ns raylib.scenes.wavecubes
  "A grid of columns whose heights ripple like water, ported from raylib-jlt's
  `waving_cubes` (zlib licence), with a smaller grid.

  The original draws 14 by 14 columns, 196, at every `(ix, iz)` with spacing
  1.5, centred on the origin: `x = ix * 1.5 - half` and the same for z, where
  half is `0.5 * (n - 1) * 1.5`. Its time is `t = 0.06 * frame`. The height is
  `0.6 + 2.4 * (1 + sin(0.6 ix + 0.6 iz + t))`, so 0.6 to 5.4, and the column
  is a box of size `[1, height, 1]` centred at half that height. The colour is
  three sines, `(int (+ 128 (* 127 (sin ...))))` of `0.35 ix + t`,
  `0.35 iz + t + 2` and `0.35 (ix + iz) + t + 4`, with alpha 255. The camera
  orbits at `a = 0.012 * frame` at `(1.3 span cos a, 0.9 span, 1.3 span sin a)`
  looking at `(0, 1.5, 0)` through a 45 degree perspective camera, where span
  is `1.5 n`, 21 in the original. The wave, the colours, the spacing, the
  heights and the camera's proportions are the original's, and so is the
  shading (`raylib.soft3d/cube`'s shades are raylib-jlt's `cube!`). Nothing reads
  a frame time, like the original: both the wave and the orbit advance by frame.

  The grid here is 9 by 9, 81 columns, where the original has 14 by 14, 196.
  The first version of this port, with all 196 columns through
  `raylib.soft3d/cube` and `finish`, ran at 15 fps on an iPhone 17 Pro, 67 ms a
  frame. This version paints by axis order with its own box emitter. Span follows the grid, so the camera comes in to
  frame 9 columns as the original framed 14, and the wave keeps its ripple per
  column but shows fewer ripples across.

  There is no input and so no control to map. The original's on-screen fps
  counter (`fps!`) is dropped, as earlier scenes drop theirs; the caption is
  the original's text with this grid's count. It sits below Back, with the 3D
  view in the field under it, full width to the bottom, clipped to it by the
  draw method. The original's fovy is kept while the field is at least as wide
  as 800x450, and in a narrower field `raylib.soft3d/fit-camera` widens it so
  the original's horizontal view still fits.

  A frame has no grid and no line, and 468 to 486 triangles: from the camera a
  column shows its top and two sides. `scene-list` paints the columns far to
  near by ordering each axis of the grid farthest first (`axis-order`), which a
  grid of boxes needs no sort of triangles for, and builds the faces itself; see
  `scene-list`. With no depth buffer a tall column near the camera over a short
  one behind it can leave a little residue where their edges meet.

  The state holds only `:frame`. Colours are `[r g b a]` vectors."
  (:require [raylib.soft3d :as s3]))

(def original-n "Columns along each side in the original." 14)

(def n "Columns along each side here." 9)
(def spacing "Distance between column centres. The original's." 1.5)

(def caption-text (str "waving cubes - " (* n n) " columns"))

(def background-colour [18 18 32 255])
(def caption-colour "RAYWHITE, as raylib defines it." [245 245 245 255])

(def ^:private half (* 0.5 (dec n) spacing))
(def ^:private span (* spacing n))

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

(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

(defn camera
  "The original's orbiting camera for `state`, fitted to `dims`' field by
  `raylib.soft3d/fit-camera`: at radius `span * 1.3`, height `span * 0.9`,
  turned `0.012 * frame` radians, looking at (0, 1.5, 0) with fovy 45."
  [state dims]
  (let [a (* 0.012 (:frame state))]
    (s3/fit-camera {:position [(* span 1.3 (Math/cos a)) (* span 0.9) (* span 1.3 (Math/sin a))]
                    :target [0.0 1.5 0.0]
                    :up [0.0 1.0 0.0]
                    :fovy 45.0
                    :projection :perspective}
                   original-aspect (:aspect dims))))

(defn column
  "The column at grid cell `(ix, iz)` for `state`: `:pos`, `:size` and `:colour`
  as the original passes them to `cube!`."
  [state ix iz]
  (let [t (* 0.06 (:frame state))
        wave (Math/sin (+ (* 0.6 ix) (* 0.6 iz) t))
        hgt (+ 0.6 (* 2.4 (+ 1.0 wave)))
        shade (fn [phase] (int (+ 128 (* 127 (Math/sin phase)))))]
    {:pos [(- (* ix spacing) half) (/ hgt 2.0) (- (* iz spacing) half)]
     :size [1.0 hgt 1.0]
     :colour [(shade (+ (* 0.35 ix) t))
              (shade (+ (* 0.35 iz) t 2.0))
              (shade (+ (* 0.35 (+ ix iz)) t 4.0))
              255]}))

(defn columns
  "All `n * n` columns for `state`, `ix` outer and `iz` inner, as the original
  loops."
  [state]
  (vec (for [ix (range n) iz (range n)] (column state ix iz))))

(defn axis-order
  "The cell indices 0 to n-1 ordered farthest first from the world coordinate
  `c` along one axis, by distance of the cell's centre."
  [c]
  (vec (sort-by (fn [i] (- (abs (- (- (* i spacing) half) c)))) (range n))))

(def ^:private near "rlgl.h RL_CULL_DISTANCE_NEAR, as `raylib.soft3d` has it." 0.05)

(defn- put!
  "Store the clip-space corner (X Y W) with view depth D in slot `i` of `buf` as
  screen x, screen y and depth, or mark the slot behind the near plane with
  depth -1. A clip z + w of at least 0 is exactly a depth of at least `near`."
  [^doubles buf i ox oy hw hh X Y W D]
  (let [j (* 3 i)]
    (if (>= D near)
      (do (aset buf j (+ ox (* hw (+ 1.0 (/ X W)))))
          (aset buf (+ j 1) (- (+ oy hh) (* hh (/ Y W))))
          (aset buf (+ j 2) D))
      (aset buf (+ j 2) -1.0))))

(defn- face
  "Append the quad on corner slots a b c e of `buf`, shaded by `f` as
  raylib-jlt's `cube!` shades it, as two triangles a b c and a c e. Each is added
  only when its y-down cross product is negative, the winding rlgl keeps, and
  neither when a corner is behind the near plane."
  [dl ^doubles buf a b c e f [cr cg cb]]
  (let [ja (* 3 a) jb (* 3 b) jc (* 3 c) je (* 3 e)
        da (aget buf (+ ja 2)) db (aget buf (+ jb 2))
        dc (aget buf (+ jc 2)) de (aget buf (+ je 2))]
    (if (and (pos? da) (pos? db) (pos? dc) (pos? de))
      (let [xa (aget buf ja) ya (aget buf (+ ja 1))
            xb (aget buf jb) yb (aget buf (+ jb 1))
            xc (aget buf jc) yc (aget buf (+ jc 1))
            xe (aget buf je) ye (aget buf (+ je 1))
            r (int (* f cr)) g (int (* f cg)) bl (int (* f cb))
            depth (* 0.25 (+ da db dc de))
            dl (if (neg? (- (* (- xb xa) (- yc ya)) (* (- yb ya) (- xc xa))))
                 (conj dl [:tri xa ya xb yb xc yc r g bl 255 depth])
                 dl)]
        (if (neg? (- (* (- xc xa) (- ye ya)) (* (- yc ya) (- xe xa))))
          (conj dl [:tri xa ya xc yc xe ye r g bl 255 depth])
          dl))
      dl)))

(defn scene-list
  "The draw list for `state`: every column as a box, painted far to near. The
  columns stand on a grid and never overlap in plan, so taking the x indices
  and the z indices each farthest first from the camera and looping x outside
  z paints a column after everything behind it. That replaces
  `raylib.soft3d/finish`, whose comparator sort of every triangle cost a third
  of the frame.

  The boxes are built here and not by `raylib.soft3d/cube`, to cut the
  per-frame allocation. Each face is the same quad, shade and winding as
  `cube`'s. A box's corners are the clip coordinates of its first corner plus
  multiples of the clip columns of its edges, so each costs adds in place of a
  matrix product, and the screen position and depth of the corners go into one
  scratch array. A face is considered only when the camera is on its outside
  of the box's plane, which is what the screen-sign test of `cube` decides for a
  convex box seen from outside; the triangles still pass the screen-sign test."
  [state dims]
  (let [cam (camera state dims)
        vp (s3/view-proj cam (:viewport dims))
        {:keys [m d]
         ox :x
         oy :y
         w :w
         h :h} vp
        hw (* 0.5 w) hh (* 0.5 h)
        [m0 m1 m2 m3 m4 m5 m6 m7 _ _ _ _ m12 m13 m14 m15] m
        [d0 d1 d2 d3] d
        [camx camy camz] (:position cam)
        buf (double-array 24)]
    (reduce
     (fn [dl ix]
       (reduce
        (fn [dl iz]
          (let [{[px py pz] :pos
                 [_ sy _] :size
                 colour :colour} (column state ix iz)
                x0 (- px 0.5) x1 (+ px 0.5) y0 (- py (* 0.5 sy)) y1 (+ py (* 0.5 sy))
                z0 (- pz 0.5) z1 (+ pz 0.5)
                bX (+ (* m0 x0) (* m1 y0) (* m2 z0) m3)
                bY (+ (* m4 x0) (* m5 y0) (* m6 z0) m7)
                bW (+ (* m12 x0) (* m13 y0) (* m14 z0) m15)
                bD (+ (* d0 x0) (* d1 y0) (* d2 z0) d3)
                hX (* m1 sy) hY (* m5 sy) hW (* m13 sy) hD (* d1 sy)
                corner! (fn [i fx fy fz]
                          (put! buf i ox oy hw hh
                                (+ bX (* fx m0) (* fy hX) (* fz m2))
                                (+ bY (* fx m4) (* fy hY) (* fz m6))
                                (+ bW (* fx m12) (* fy hW) (* fz m14))
                                (+ bD (* fx d0) (* fy hD) (* fz d2))))
                +z? (> camz z1) -z? (< camz z0)
                -x? (< camx x0) +x? (> camx x1)
                +y? (> camy y1) -y? (< camy y0)]
            (when (or -z? -x? -y?) (corner! 0 0.0 0.0 0.0))
            (when (or -z? +x? -y?) (corner! 1 1.0 0.0 0.0))
            (when (or -z? -x? +y?) (corner! 2 0.0 1.0 0.0))
            (when (or -z? +x? +y?) (corner! 3 1.0 1.0 0.0))
            (when (or +z? -x? -y?) (corner! 4 0.0 0.0 1.0))
            (when (or +z? +x? -y?) (corner! 5 1.0 0.0 1.0))
            (when (or +z? -x? +y?) (corner! 6 0.0 1.0 1.0))
            (when (or +z? +x? +y?) (corner! 7 1.0 1.0 1.0))
            (cond-> dl
              +z? (face buf 4 5 7 6 1.0 colour)
              -z? (face buf 1 0 2 3 0.5 colour)
              -x? (face buf 0 4 6 2 0.7 colour)
              +x? (face buf 5 1 3 7 0.85 colour)
              +y? (face buf 6 7 3 2 1.0 colour)
              -y? (face buf 0 1 5 4 0.4 colour))))
        dl (axis-order camz)))
     []
     (axis-order camx))))

(defn- init [_] [{:frame 0} [[:scene/init :wavecubes]]])
(defn- update-scene [state _] [(update state :frame inc) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :wavecubes]]])

(defn scene []
  {:id :wavecubes
   :title "Waving Cubes"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
