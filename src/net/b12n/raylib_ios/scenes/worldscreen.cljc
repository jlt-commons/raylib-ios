(ns net.b12n.raylib-ios.scenes.worldscreen
  "A 2D label pinned above a 3D cube, ported from raylib-jlt's `world_screen`,
  which is raylib's `core_world_screen` (zlib licence).

  The original orbits a perspective camera round a red cube of side 2 at the
  origin on a grid of 10. The camera is at radius 14.14 and height 10, its angle
  starts at 0.7854 radians and grows by `0.3 * get-frame-time` a frame, it looks
  at the origin with fovy 45. Each frame it projects the world point (0, 2.5, 0)
  with GetWorldToScreen, truncates the result to whole pixels, and draws
  \"Enemy: 100/100\" with its top edge at that y and its centre at that x, found
  from the text's width. Here the angle grows by `0.3 * :delta-seconds`, the
  point goes through `net.b12n.raylib-ios.soft3d/world->screen` with the same view-proj the
  cube is drawn with, and the injected `measure` gives the width.

  There is no input and so no control to map. The original draws two more lines
  of text at the top left, over the 3D view: \"Cube screen position: [x, y]\"
  and \"Text 2D always stays on top of the cube\". The second is the caption
  here, below Back and above the field. The first is drawn at the field's top
  left, over the view, as in the original. The 3D view is `net.b12n.raylib-ios.soft3d`'s
  `[x y w h]` viewport on the field, which runs full width to the bottom. The
  original's 45 degree fovy is kept while the field is at least as wide as
  800x450. In a narrower field `net.b12n.raylib-ios.soft3d/fit-camera` widens it so the
  original's horizontal view still fits. All text uses the field's text size,
  where the original uses 20, so the readout and the label scale with the phone.

  The grid is drawn under every face (`net.b12n.raylib-ios.soft3d/finish` puts all grid
  lines first). The cube is centred on y = 0, so half of it is below the grid,
  and where raylib's depth buffer would show grid lines in front of its lower
  half they are hidden here. Splitting the cube at the grid is follow-up work.

  The camera moves, so the grid is projected afresh each frame: 22 lines. The
  state holds only `:t`. Colours are `[r g b a]` vectors."
  (:require [net.b12n.raylib-ios.soft3d :as s3]))

(def caption-text "Text 2D always stays on top of the cube")
(def label-text "Enemy: 100/100")
(def readout-prefix "Cube screen position: ")

(def background-colour [245 245 245 255])
(def caption-colour [130 130 130 255])
(def label-colour [0 0 0 255])
(def readout-colour [0 228 48 255])
(def cube-colour [230 41 55 255])

(def radius "The orbit's radius. The original's." 14.14)
(def height "The camera's height. The original's." 10.0)
(def start-angle "The orbit's angle at the start, in radians. The original's." 0.7854)
(def orbit-rate "Radians a second. The original's." 0.3)
(def anchor "The world point the label is pinned to. The original's." [0.0 2.5 0.0])

(defn- readout-widest
  "The longest readout there can be, for sizing: both numbers at four digits."
  []
  (str readout-prefix "[0000, 0000]"))

(defn dimensions
  "`net.b12n.raylib-ios.soft3d/field` plus the caption as `{:s :x :y :size}` and the label's
  width at that size, `:label-w`. The text size fits the widest of the caption,
  the label and a readout of four digits each. `measure` is
  `(fn [s size] -> px)`."
  [metrics measure]
  (let [widest (max (measure caption-text 100) (measure label-text 100)
                    (measure (readout-widest) 100))
        {:keys [size pad text-y]
         :as field} (s3/field metrics widest)]
    (assoc field
           :caption {:s caption-text
                     :x pad
                     :y text-y
                     :size size}
           :label-w (measure label-text size))))

(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

(defn camera
  "The camera for `state`: on the circle of radius 14.14 at height 10, at the
  angle `:t`, looking at the origin with fovy 45, fitted to `dims`' field by
  `net.b12n.raylib-ios.soft3d/fit-camera`."
  [state dims]
  (let [t (:t state)]
    (s3/fit-camera {:position [(* radius (Math/cos t)) height (* radius (Math/sin t))]
                    :target [0.0 0.0 0.0]
                    :up [0.0 1.0 0.0]
                    :fovy 45.0
                    :projection :perspective}
                   original-aspect (:aspect dims))))

(defn view
  "The `net.b12n.raylib-ios.soft3d/view-proj` for `state`, which both the draw and the label
  project through."
  [state dims]
  (s3/view-proj (camera state dims) (:viewport dims)))

(defn label
  "The label for `state` as `{:s :x :y :size}`, plus the projected point as
  `:sx` and `:sy`. The point is `anchor` through `net.b12n.raylib-ios.soft3d/world->screen`,
  truncated to whole pixels like the original's `int`. The label's top is `:sy`
  and its centre is `:sx`, found from `dims`' `:label-w`."
  [state dims]
  (let [[sx sy] (s3/world->screen (view state dims) anchor)
        sx (long sx)
        sy (long sy)]
    {:s label-text
     :x (- sx (quot (long (:label-w dims)) 2))
     :y sy
     :size (:size dims)
     :sx sx
     :sy sy}))

(defn readout
  "The \"Cube screen position\" line for `state`, at the field's top left."
  [state dims]
  (let [{:keys [sx sy]} (label state dims)
        [_ fy] (:viewport dims)]
    {:s (str readout-prefix "[" sx ", " sy "]")
     :x (:pad dims)
     :y (+ fy (:pad dims))
     :size (:size dims)}))

(defn lines
  "Every line of text for `state`: the caption, the readout and the label."
  [state dims]
  [(:caption dims) (readout state dims) (label state dims)])

(defn scene-list
  "The finished draw list for `state`: the cube and the grid of 10."
  [state dims]
  (let [vp (view state dims)]
    (-> []
        (s3/grid vp 10 1.0)
        (s3/cube vp nil [0.0 0.0 0.0] 2.0 cube-colour)
        s3/finish)))

(defn- init [_] [{:t start-angle} [[:scene/init :worldscreen]]])

(defn- update-scene [state input]
  (let [dt (max 0.0 (double (or (:delta-seconds input) 0.0)))]
    [(update state :t + (* orbit-rate dt)) []]))

(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :worldscreen]]])

(defn scene []
  {:id :worldscreen
   :title "World to Screen"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
