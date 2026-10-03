(ns raylib.scenes.geoshapes
  "Cubes, a sphere, cylinders, a cone and a capsule, solid and in wire, over a
  grid, ported from raylib-jlt's `geometric_shapes` (zlib licence, after
  raylib's examples/models/models_geometric_shapes.c). The projection is in
  software, by `raylib.soft3d`.

  The original (geometric_shapes.clj) draws, in `-main` (lines 41-110), with a
  fixed camera at (0, 10, 10) looking at the origin, fovy 45 (lines 28-37),
  and in this order: a red cube 2 by 5 by 2 at (-4, 0, 2) with gold wires over it; maroon
  wires of a 3 by 6 by 2 box at (-4, 0, -2); a green sphere of radius 1 at
  (-1, 0, -2); lime sphere wires of radius 2, 16 rings by 16 slices, at
  (1, 0, 2); a sky blue cylinder (top radius 1, bottom 2, height 3, 4 sides)
  at (4, 0, -2) with dark blue wires; brown wires of a cylinder (radius 1,
  height 2, 6 sides) at (4.5, -1, 2); a gold cone (a cylinder with top radius
  0, bottom 1.5, height 3, 8 sides) at (1, 0, -4) with pink wires; a violet
  capsule from (-3, 1.5, -4) to (-4, -1, -4), radius 1.2, 8 slices by 8 rings,
  with purple wires; and `DrawGrid(10, 1)`. The constants in `shapes` are
  those lines.

  The builders are `raylib.soft3d`'s: `cube` with `{:shade :flat}`
  (`draw-cube!` is rmodels.c DrawCube) and `cube-wires`, `sphere`,
  `cylinder` and `cylinder-wires`, `capsule` and `capsule-wires`, which mirror
  DrawCylinder, DrawCylinderWires, DrawCapsule and DrawCapsuleWires.
  DrawSphereWires is mirrored here by `sphere-wire-segments`, as segments for
  `raylib.soft3d/lines`. The solid sphere is `raylib.soft3d/sphere` with
  `{:shade :flat}`, a flat colour as DrawSphere draws it, on that builder's
  latitude and longitude tessellation.

  Paint order is `raylib.soft3d/finish`: the grid, every triangle far to near
  by its mean depth, then every wire. The shapes overlap and the curved ones
  interpenetrate nothing but sit in front of each other, so no order of whole
  shapes is right, which is why this scene sorts triangles.

  There is no depth buffer, so a wire is drawn over every face, where raylib's
  depth test hides what lies behind a solid. Every wire shows through whatever
  stands in front of it: the far edges of the sky blue, gold and violet shapes,
  the maroon box's wires behind the red cube, and the lime sphere's wires
  behind the shapes in front of it. The red cube's own gold wires hide their
  back edges (`{:hide-back? true}`), as the cube is a box.

  TESSELLATION AND THE BUDGET. The counts are the original's, so `original`
  and `tessellation` are equal and a test says so. They make 362 triangles and
  1643 lines (2005 items, 6383 FFI calls to draw): the sphere wires alone are
  864 segments, the capsule wires 664. Built every frame that took 2.5 ms under
  jolt on the laptop (2.85 ms with the draw side), against a budget of 0.45 ms
  on the laptop, the figure that stands in for the phone. Cutting tessellation
  alone cannot reach it, because drawing the original's 2005 items is 0.36 ms by
  itself. The camera never moves and the scene reads nothing, so the list
  depends on the screen alone (a test checks that), and `raylib.gallery` builds
  it once per screen and draws the same list every frame, 0.36 ms with the draw
  side. The first frame and every rotation rebuild it: about 2.5 ms on the
  laptop, a single-frame hitch (about 80 ms on the phone at 33x). The steady
  state ran at 59 to 60 fps on the iPhone 17 Pro.

  The original's text (lines 112-118) is an FPS counter at (10, 10) and
  \"geometric shapes\" at (10, 40), size 10. Here the second is the caption
  below Back, at the project's size, and the FPS counter is dropped. The 3D view
  fills the field under it, and `raylib.soft3d/fit-camera` widens the 45 degree
  fovy in a field narrower than 800x450. The original reads no input and
  neither does this.

  The state holds only `:frame`, which nothing reads. Colours are `[r g b a]`
  vectors."
  (:require [raylib.soft3d :as s3]))

(def caption-text "geometric shapes")

(def background-colour "RAYWHITE." [245 245 245 255])
(def caption-colour "GRAY." [130 130 130 255])
(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

(def red [230 41 55 255])
(def gold [255 203 0 255])
(def maroon [190 33 55 255])
(def green [0 228 48 255])
(def lime [0 158 47 255])
(def skyblue [102 191 255 255])
(def darkblue [0 82 172 255])
(def brown [127 106 79 255])
(def pink [255 109 194 255])
(def violet [135 60 190 255])
(def purple [200 122 255 255])

(def original
  "The original's tessellation: sphere rings, slices; sphere wires rings,
  slices; capsule slices, rings. The cylinders' sides are 4, 6 and 8 in both."
  {:sphere [16 16]
   :sphere-wires [16 16]
   :capsule [8 8]})

(def tessellation
  "What this scene draws, in the same shape as `original`."
  {:sphere [16 16]
   :sphere-wires [16 16]
   :capsule [8 8]})

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
  "The original's camera, (0, 10, 10) looking at the origin with fovy 45,
  fitted to `dims`' field by `raylib.soft3d/fit-camera`."
  [dims]
  (s3/fit-camera {:position [0.0 10.0 10.0]
                  :target [0.0 0.0 0.0]
                  :up [0.0 1.0 0.0]
                  :fovy 45.0
                  :projection :perspective}
                 original-aspect (:aspect dims)))

(defn sphere-wire-segments
  "rmodels.c DrawSphereWires as world segments `[[x1 y1 z1] [x2 y2 z2] colour]`
  for `raylib.soft3d/lines`: for ring i from 0 to rings + 1 and slice j, three
  segments, in the C's order, of the (rings + 2) latitude steps of
  `270 + 180/(rings + 1) * i` degrees and the longitudes `360 * j / slices`
  degrees, scaled by `radius` about `[cx cy cz]`."
  [[cx cy cz] radius rings slices colour]
  (let [rad (/ Math/PI 180.0)
        step (/ 180.0 (inc rings))
        v (fn [i j]
            (let [lat (* rad (+ 270.0 (* step i)))
                  lon (* rad (/ (* 360.0 j) slices))]
              [(+ cx (* radius (Math/cos lat) (Math/sin lon)))
               (+ cy (* radius (Math/sin lat)))
               (+ cz (* radius (Math/cos lat) (Math/cos lon)))]))]
    (vec (for [i (range (+ rings 2))
               j (range slices)
               :let [a (v i j) b (v (inc i) (inc j)) c (v (inc i) j)]
               seg [[a b colour] [b c colour] [c a colour]]]
           seg))))

(defn scene-list
  "The draw list for `state`, in paint order: the grid, the triangles far to
  near, then the wires. See the ns docstring."
  [_state dims]
  (let [vp (s3/view-proj (camera dims) (:viewport dims))
        [sr ss] (:sphere tessellation)
        [wr ws] (:sphere-wires tessellation)
        [cs cr] (:capsule tessellation)
        flat {:shade :flat}]
    (-> []
        (s3/grid vp 10 1.0)
        (s3/cube vp nil [-4.0 0.0 2.0] [2.0 5.0 2.0] red flat)
        (s3/cube-wires vp nil [-4.0 0.0 2.0] [2.0 5.0 2.0] gold {:hide-back? true})
        (s3/cube-wires vp nil [-4.0 0.0 -2.0] [3.0 6.0 2.0] maroon)
        (s3/sphere vp nil [-1.0 0.0 -2.0] 1.0 green {:rings sr
                                                     :slices ss
                                                     :shade :flat})
        (s3/lines vp nil (sphere-wire-segments [1.0 0.0 2.0] 2.0 wr ws lime))
        (s3/cylinder vp nil [4.0 0.0 -2.0] 1.0 2.0 3.0 skyblue {:slices 4})
        (s3/cylinder-wires vp nil [4.0 0.0 -2.0] 1.0 2.0 3.0 darkblue {:slices 4})
        (s3/cylinder-wires vp nil [4.5 -1.0 2.0] 1.0 1.0 2.0 brown {:slices 6})
        (s3/cylinder vp nil [1.0 0.0 -4.0] 0.0 1.5 3.0 gold {:slices 8})
        (s3/cylinder-wires vp nil [1.0 0.0 -4.0] 0.0 1.5 3.0 pink {:slices 8})
        (s3/capsule vp nil [-3.0 1.5 -4.0] [-4.0 -1.0 -4.0] 1.2 violet {:slices cs
                                                                        :rings cr})
        (s3/capsule-wires vp nil [-3.0 1.5 -4.0] [-4.0 -1.0 -4.0] 1.2 purple {:slices cs
                                                                              :rings cr})
        (s3/finish))))

(defn- init [_] [{:frame 0} [[:scene/init :geoshapes]]])
(defn- update-scene [state _] [(update state :frame inc) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :geoshapes]]])

(defn scene []
  {:id :geoshapes
   :title "Geometric Shapes"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
