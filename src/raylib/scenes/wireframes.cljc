(ns raylib.scenes.wireframes
  "Four wireframe solids tumbling side by side, ported from raylib-jlt's
  `wireframe_shapes` (zlib licence).

  The original looks from (0, 3.2, 12) at the origin through a 45 degree
  perspective camera. Each shape is a list of edges in its own space, drawn as
  RL_LINES inside rlPushMatrix/rlPopMatrix after `rlTranslatef(x, 0, 0)` and
  `rlRotatef(spin, 0.4, 1, 0.3)`, where spin is the frame number times 0.9
  degrees. Here that is `raylib.soft3d/compose` of `translate` and
  `rotate-axis` in the same order, so as in rlgl the turn applies first. The
  shapes are, at x = -6, -2, 2 and 6:

  - a pyramid of 8 edges, a base of 1.3 either side at y = -1 and an apex at
    (0, 1.6, 0), in light red;
  - an octahedron of 12 edges, points 1.5 out on each axis, in light blue;
  - a torus of 196 edges, major radius 1.2 and minor 0.42, 14 steps around the
    ring and 7 around the tube, in light green;
  - a helix of 64 edges, 3 turns of radius 1.2 from y = -1.3 to 1.3, in
    yellow.

  That is 280 line items a frame, all `raylib.soft3d/lines`. Every shape's
  edges are built once, at load, as `[p q colour]` segments in model space, so a
  frame only composes four transforms and projects them.

  There is no input and so no control to map, and nothing reads a frame time,
  like the original: the spin grows 0.9 degrees an update. The original's
  dark background and its caption (text 18, raywhite, here sized by
  `raylib.soft3d/field`) are kept, the caption below Back with the 3D field
  under it. The original's 45 degree fovy is kept while the field is at least
  as wide as 800x450. In a narrower field `raylib.soft3d/fit-camera` widens it
  so the original's horizontal view still fits. A line behind the near plane
  is clipped by `raylib.soft3d/lines`, never wrapped through infinity.

  The state holds only `:frame`. Colours are `[r g b a]` vectors."
  (:require [raylib.soft3d :as s3]))

(def caption-text "Wireframe shapes: pyramid, octahedron, torus, helix")

(def background-colour [12 12 20 255])
(def caption-colour "RAYWHITE, as raylib defines it." [245 245 245 255])

(def spin-rate "Degrees a frame. The original's." 0.9)

(def ^:private tau 6.283185307179586)

(defn- ring-pairs
  "Edges joining each of the four points `ring` to the next, round."
  [ring]
  (mapv (fn [i] [(nth ring i) (nth ring (mod (inc i) 4))]) (range 4)))

(def ^:private pyramid-edges
  (let [b [[-1.3 -1.0 -1.3] [1.3 -1.0 -1.3] [1.3 -1.0 1.3] [-1.3 -1.0 1.3]]
        apex [0.0 1.6 0.0]]
    (into (ring-pairs b) (map (fn [c] [c apex]) b))))

(def ^:private octahedron-edges
  (let [top [0.0 1.5 0.0] bot [0.0 -1.5 0.0]
        ring [[1.5 0.0 0.0] [0.0 0.0 1.5] [-1.5 0.0 0.0] [0.0 0.0 -1.5]]]
    (-> (mapv (fn [v] [top v]) ring)
        (into (map (fn [v] [bot v]) ring))
        (into (ring-pairs ring)))))

(def ^:private torus-edges
  (let [rr 1.2 r 0.42 nu 14 nv 7
        pt (fn [ui vi]
             (let [u (* tau (/ ui (double nu))) v (* tau (/ vi (double nv)))]
               [(* (+ rr (* r (Math/cos v))) (Math/cos u))
                (* r (Math/sin v))
                (* (+ rr (* r (Math/cos v))) (Math/sin u))]))]
    (vec (for [ui (range nu) vi (range nv)
               e [[(pt ui vi) (pt (mod (inc ui) nu) vi)]
                  [(pt ui vi) (pt ui (mod (inc vi) nv))]]]
           e))))

(def ^:private helix-edges
  (let [n 64 turns 3
        pt (fn [i]
             (let [t (* turns tau (/ i (double n)))]
               [(* 1.2 (Math/cos t)) (- (* 2.6 (/ i (double n))) 1.3) (* 1.2 (Math/sin t))]))]
    (mapv (fn [i] [(pt i) (pt (inc i))]) (range n))))

(defn- shape [id x edges colour]
  {:id id
   :x x
   :colour colour
   :segments (mapv (fn [[p q]] [p q colour]) edges)})

(def shapes
  "The four shapes in the original's order: `:id`, `:x`, `:colour` and
  `:segments`, `[p q colour]` in model space."
  [(shape :pyramid -6.0 pyramid-edges [255 120 120 255])
   (shape :octahedron -2.0 octahedron-edges [120 200 255 255])
   (shape :torus 2.0 torus-edges [170 255 120 255])
   (shape :helix 6.0 helix-edges [255 220 120 255])])

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
  "The original's camera, (0, 3.2, 12) looking at the origin with fovy 45,
  fitted to `dims`' field by `raylib.soft3d/fit-camera`."
  [dims]
  (s3/fit-camera {:position [0.0 3.2 12.0]
                  :target [0.0 0.0 0.0]
                  :up [0.0 1.0 0.0]
                  :fovy 45.0
                  :projection :perspective}
                 original-aspect (:aspect dims)))

(defn spin "The tumble in degrees for `state`: 0.9 a frame." [state]
  (* (:frame state) spin-rate))

(defn transform
  "`shape`'s transform for `state`: rlTranslatef(x, 0, 0) then
  rlRotatef(spin, 0.4, 1, 0.3), composed in the order the original calls them."
  [state shape]
  (s3/compose (s3/translate (:x shape) 0.0 0.0)
              (s3/rotate-axis (spin state) 0.4 1.0 0.3)))

(defn draw-list
  "The finished draw list for `state` seen through `cam`: each shape's
  segments under its transform, as `raylib.soft3d/lines`."
  [cam state dims]
  (let [vp (s3/view-proj cam (:viewport dims))]
    (s3/finish
     (reduce (fn [dl sh] (s3/lines dl vp (transform state sh) (:segments sh)))
             []
             shapes))))

(defn scene-list
  "`draw-list` through the original's own `camera`."
  [state dims]
  (draw-list (camera dims) state dims))

(defn- init [_] [{:frame 0} [[:scene/init :wireframes]]])
(defn- update-scene [state _] [(update state :frame inc) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :wireframes]]])

(defn scene []
  {:id :wireframes
   :title "Wireframe Shapes"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
