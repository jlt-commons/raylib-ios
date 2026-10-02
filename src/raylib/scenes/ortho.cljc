(ns raylib.scenes.ortho
  "The same 3D scene in perspective and in orthographic projection, ported from
  raylib-jlt's `orthographic_projection`, which is raylib's
  `core_3d_camera_mode` family (zlib licence).

  The original looks from (5, 5, 5) at the origin at three cubes of side 1 in a
  row on a grid of 10: red at (-1.5, 0.5, 0), green at (0, 0.5, 0) and blue at
  (1.5, 0.5, 0), shaded face by face as raylib-jlt's `cube!` does it
  (`raylib.soft3d/cube`'s default). It starts in perspective with fovy 45 and
  SPACE switches to orthographic, where fovy is the view's height, 12.

  Controls here: a tap anywhere outside Back toggles between the two, in place
  of SPACE (`raylib.gesture/track`, so the finger's start decides, never the
  release). A touch that begins under Back toggles nothing. The caption names
  the mode as the original's does, with \"tap\" for \"SPACE\".

  The original's caption sits below Back, with the 3D view in the field under
  it, full width to the bottom. The view is `raylib.soft3d`'s `[x y w h]`
  viewport on that field. The original's fovys are kept while the field is at
  least as wide as 800x450. In a narrower field `raylib.soft3d/fit-camera`
  widens the perspective fovy, or scales the orthographic height, so the
  original's horizontal view still fits. The grid reaches past the field's
  edges, so the draw method clips to the field.

  The state is `:ortho?` and the gesture. The grid depends on the camera and
  layout only, so `grid-list` builds it for one mode and a draw can keep both.
  Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.soft3d :as s3]))

(def captions {:perspective "PERSPECTIVE (tap to toggle)"
               :orthographic "ORTHOGRAPHIC (tap to toggle)"})

(def background-colour [245 245 245 255])
(def caption-colour [80 80 80 255])

(def cubes
  "The three cubes: centre and colour. raylib's RED, GREEN and BLUE."
  [{:at [-1.5 0.5 0.0]
    :colour [230 41 55 255]}
   {:at [0.0 0.5 0.0]
    :colour [0 228 48 255]}
   {:at [1.5 0.5 0.0]
    :colour [0 121 241 255]}])

(def original-aspect "The original's 800x450 window, w/h." (/ 800.0 450.0))

(defn dimensions
  "`raylib.soft3d/field` plus both captions as `{:s :x :y :size}`:
  `:perspective`, `:orthographic`, and `:lines` with both so a test can check
  they fit. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [widest (apply max (map #(measure % 100) (vals captions)))
        {:keys [size pad text-y]
         :as field} (s3/field metrics widest)
        line (fn [s] {:s s
                      :x pad
                      :y text-y
                      :size size})
        persp (line (:perspective captions))
        orth (line (:orthographic captions))]
    (assoc field :perspective persp :orthographic orth :lines [persp orth])))

(defn caption
  "The caption line for `state`'s mode."
  [state dims]
  (if (:ortho? state) (:orthographic dims) (:perspective dims)))

(defn camera
  "The original's camera for `state`: (5, 5, 5) at the origin, perspective with
  fovy 45, or orthographic with fovy 12, fitted to `dims`' field by
  `raylib.soft3d/fit-camera` (for orthographic that scales the 12 world units of
  height)."
  [state dims]
  (let [ortho? (:ortho? state)]
    (s3/fit-camera {:position [5.0 5.0 5.0]
                    :target [0.0 0.0 0.0]
                    :up [0.0 1.0 0.0]
                    :fovy (if ortho? 12.0 45.0)
                    :projection (if ortho? :orthographic :perspective)}
                   original-aspect (:aspect dims))))

(defn grid-list
  "The grid of 10, spacing 1, as an unfinished draw list through `cam` onto
  `dims`' viewport."
  [cam dims]
  (s3/grid [] (s3/view-proj cam (:viewport dims)) 10 1.0))

(defn scene-list
  "The finished draw list for `state`: `base` (the `grid-list` for its camera)
  and the three cubes."
  [base state dims]
  (let [vp (s3/view-proj (camera state dims) (:viewport dims))]
    (s3/finish (reduce (fn [dl {:keys [at colour]}] (s3/cube dl vp nil at 1.0 colour))
                       base cubes))))

(defn advance
  "One frame. Calls `gesture/track` once. A tap whose start is outside Back
  flips `:ortho?`."
  [state input]
  (let [[g event] (gesture/track (:gesture state) input)
        toggle? (and (= :tap (:type event))
                     (not (gesture/in-back-region? (:at event))))]
    (assoc state
           :gesture g
           :ortho? (if toggle? (not (:ortho? state)) (:ortho? state)))))

(defn- init [_]
  [{:ortho? false
    :gesture gesture/idle} [[:scene/init :ortho]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :ortho]]])

(defn scene []
  {:id :ortho
   :title "Orthographic Projection"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
