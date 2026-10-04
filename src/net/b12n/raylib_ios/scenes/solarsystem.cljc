(ns net.b12n.raylib-ios.scenes.solarsystem
  "A sun, an Earth and a Moon through nested transforms, ported from
  raylib-jlt's `rlgl_solar_system` (zlib licence).

  The original looks from (16, 16, 16) at the origin through a 45 degree
  perspective camera and draws three cubes: a gold one of side 3 at the origin,
  a blue one of side 1.4 and a light gray one of side 0.7. With the angles
  `earth-orbit = (mod (* 0.5 frame) 360)`, `earth-spin = (mod frame 360)` and
  `moon-orbit = (mod (* 2 frame) 360)` in degrees, it calls

      rlPushMatrix; rlRotatef(earth-orbit, 0, 1, 0); rlTranslatef(9, 0, 0)
        rlPushMatrix; rlRotatef(earth-spin, 0, 1, 0); <the Earth>; rlPopMatrix
        rlRotatef(moon-orbit, 0, 1, 0); rlTranslatef(2.6, 0, 0); <the Moon>
      rlPopMatrix

  rlgl applies the last call first, so each is `net.b12n.raylib-ios.soft3d/compose` of the
  same calls in the same order. The Earth is the orbit, the translate and the
  spin. The Moon is the orbit and translate of the Earth, then its own orbit
  and translate, without the Earth's spin. The Sun has no transform. Cubes take
  raylib-jlt's `cube!` shades (`net.b12n.raylib-ios.soft3d/cube`'s default). Nothing reads a
  frame time, like the original: the angles advance by frame.

  There is no input and so no control to map. The original's caption is kept.
  It sits below Back, with the 3D view in the field under it, full width to the
  bottom, clipped to it by the draw method. The original's fovy is kept while
  the field is at least as wide as 800x450, and in a narrower field
  `net.b12n.raylib-ios.soft3d/fit-camera` widens it so the original's horizontal view still
  fits. The original draws no grid, so there are no lines. A frame is at most
  three cubes, 18 triangles when none is turned and a few more as they turn;
  the Earth passing behind the Sun is ordered by mean depth, with no depth
  buffer.

  The state holds only `:frame`. Colours are `[r g b a]` vectors."
  (:require [net.b12n.raylib-ios.soft3d :as s3]))

(def caption-text "rlgl matrix stack: Earth orbits Sun, Moon orbits Earth")

(def background-colour [10 10 24 255])
(def caption-colour "RAYWHITE, as raylib defines it." [245 245 245 255])

(def sun-colour "GOLD, as raylib defines it." [255 203 0 255])
(def earth-colour "BLUE, as raylib defines it." [0 121 241 255])
(def moon-colour "LIGHTGRAY, as raylib defines it." [200 200 200 255])

(def sun-size 3.0)
(def earth-size 1.4)
(def moon-size 0.7)

(def ^:private earth-distance 9.0)
(def ^:private moon-distance 2.6)

(defn dimensions
  "`net.b12n.raylib-ios.soft3d/field` plus the caption as `{:s :x :y :size}`, in `:lines` as
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
  "The original's camera, (16, 16, 16) looking at the origin with fovy 45,
  fitted to `dims`' field by `net.b12n.raylib-ios.soft3d/fit-camera`."
  [dims]
  (s3/fit-camera {:position [16.0 16.0 16.0]
                  :target [0.0 0.0 0.0]
                  :up [0.0 1.0 0.0]
                  :fovy 45.0
                  :projection :perspective}
                 original-aspect (:aspect dims)))

(defn earth-orbit "The Earth's turn about the Sun in degrees: half a degree a frame, mod 360."
  [state] (double (mod (* 0.5 (:frame state)) 360)))

(defn earth-spin "The Earth's turn in place in degrees: one a frame, mod 360."
  [state] (double (mod (* 1.0 (:frame state)) 360)))

(defn moon-orbit "The Moon's turn about the Earth in degrees: two a frame, mod 360."
  [state] (double (mod (* 2.0 (:frame state)) 360)))

(defn earth-transform
  "The Earth's transform: rlRotatef(earth-orbit), rlTranslatef(9, 0, 0), then
  rlRotatef(earth-spin), all about the y axis, composed in the original's order."
  [state]
  (s3/compose (s3/rotate-axis (earth-orbit state) 0.0 1.0 0.0)
              (s3/translate earth-distance 0.0 0.0)
              (s3/rotate-axis (earth-spin state) 0.0 1.0 0.0)))

(defn moon-transform
  "The Moon's transform: the Earth's orbit and translate, without its spin, then
  rlRotatef(moon-orbit) and rlTranslatef(2.6, 0, 0)."
  [state]
  (s3/compose (s3/rotate-axis (earth-orbit state) 0.0 1.0 0.0)
              (s3/translate earth-distance 0.0 0.0)
              (s3/rotate-axis (moon-orbit state) 0.0 1.0 0.0)
              (s3/translate moon-distance 0.0 0.0)))

(defn scene-list
  "The finished draw list for `state`: the Sun, the Earth and the Moon."
  [state dims]
  (let [vp (s3/view-proj (camera dims) (:viewport dims))
        origin [0.0 0.0 0.0]]
    (s3/finish
     (-> []
         (s3/cube vp nil origin sun-size sun-colour)
         (s3/cube vp (earth-transform state) origin earth-size earth-colour)
         (s3/cube vp (moon-transform state) origin moon-size moon-colour)))))

(defn- init [_] [{:frame 0} [[:scene/init :solarsystem]]])
(defn- update-scene [state _] [(update state :frame inc) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :solarsystem]]])

(defn scene []
  {:id :solarsystem
   :title "Solar System"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
