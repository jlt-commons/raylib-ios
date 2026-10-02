(ns raylib.scenes.camera3d
  "An orbiting 3D camera, ported from raylib-jlt's `camera_3d`, which is
  raylib's `core_3d_camera_mode` family (zlib licence).

  The original circles a perspective camera round a red cube of side 2 standing
  at (0, 1, 0) on a grid of 20. The camera is at radius 12 and height 8, its
  angle grows 0.02 radians a frame from 0, it looks at (0, 1, 0) with fovy 45.
  All of those are the original's. The cube is shaded face by face as
  raylib-jlt's `cube!` does it (`raylib.soft3d/cube`'s default).

  There is no input and so no control to map. Nothing reads a frame time, like
  the original: the orbit advances one step an update. The original's caption is
  kept and sits below Back, with the 3D view in the field under it, full width
  to the bottom. The view is `raylib.soft3d`'s `[x y w h]` viewport on that
  field. The original's fovy is kept while the field is at least as wide as
  800x450. In a narrower field `raylib.soft3d/fit-camera` widens it so the
  original's horizontal view still fits. The grid reaches past the field's edges, so the draw method clips to the field.

  The camera moves, so the grid is projected afresh each frame: 42 lines. The
  state holds only `:frame`. Colours are `[r g b a]` vectors."
  (:require [raylib.soft3d :as s3]))

(def caption-text "an orbiting 3D camera (Camera3D by value + rlgl cube)")

(def background-colour [245 245 245 255])
(def caption-colour [80 80 80 255])
(def cube-colour [230 41 55 255])

(def radius "The orbit's radius. The original's." 12.0)
(def height "The camera's height. The original's." 8.0)
(def step "Radians a frame. The original's." 0.02)

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
  "The camera for `state`: on the circle of radius 12 at height 8, at the angle
  `0.02 * frame`, looking at (0, 1, 0) with fovy 45, fitted to `dims`' field by
  `raylib.soft3d/fit-camera`."
  [state dims]
  (let [a (* step (:frame state))]
    (s3/fit-camera {:position [(* radius (Math/cos a)) height (* radius (Math/sin a))]
                    :target [0.0 1.0 0.0]
                    :up [0.0 1.0 0.0]
                    :fovy 45.0
                    :projection :perspective}
                   original-aspect (:aspect dims))))

(defn scene-list
  "The finished draw list for `state`: the grid of 20 and the cube."
  [state dims]
  (let [vp (s3/view-proj (camera state dims) (:viewport dims))]
    (-> []
        (s3/grid vp 20 1.0)
        (s3/cube vp nil [0.0 1.0 0.0] 2.0 cube-colour)
        s3/finish)))

(defn- init [_] [{:frame 0} [[:scene/init :camera3d]]])
(defn- update-scene [state _] [(update state :frame inc) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :camera3d]]])

(defn scene []
  {:id :camera3d
   :title "3D Camera"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
