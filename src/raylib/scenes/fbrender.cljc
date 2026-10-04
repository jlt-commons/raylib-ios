(ns raylib.scenes.fbrender
  "Framebuffer Rendering, ported from raylib-jlt's `framebuffer_rendering`
  (net/b12n/raylib_jlt/framebuffer_rendering.clj, EPL 2.0), which is raylib's
  `textures_framebuffer_rendering`. The raylib C example it follows is zlib
  licensed, and this is an altered version of that too.

  The same scene twice, each rendered into its own render target before
  anything reaches the screen. The observer view watches the cube AND the other
  camera, drawn as its view frustum. The subject view is what that camera
  actually sees. The green square in the middle of the subject view is copied
  out again as an inset in its corner, which is the same texture sampled through
  a narrower source rectangle, not a third pass. The projection is in software,
  by `raylib.soft3d`, drawn into each target as a 2D draw list.

  Mirrored from framebuffer_rendering.clj:
  - The constants (lines 31-36): `crop` 128, `fovy` 45.0 and `frustum-depth`
    3.0, kept as they are. The original's `HALF-W` 400 and its height 450 are
    `original-half-w` and `original-half-h`; here each half is as large as its
    viewport (see Deviations).
  - The world (lines 102-114): a gold cube of side 2 at the origin, PINK wires
    on it, a grid of 10 spaced 1, drawn flat as `DrawCube` colours it.
  - The cameras (lines 61-68, 133-134): a subject camera orbiting the origin at
    radius 5, height 2, `0.012` radians a frame, and an observer at radius 14,
    height 10, `0.004`, both looking at the origin with fovy 45.
  - The frustum (lines 70-100): the subject camera's own position, target and
    field of view give the four far-plane corners at `frustum-depth`, and the
    prism is four edges from the apex to them and the four edges of the far
    rectangle, in GREEN. The aspect is the subject view's own.
  - The observer's pass (lines 151-165): a clear to RAYWHITE, the world and the
    prism, the text \"observer view\" in size 20 BLACK near the bottom and \"both
    cameras orbit on their own\" in size 10 DARKGRAY at the top.
  - The subject's pass (lines 166-180): a clear to RAYWHITE, the world, a GREEN
    outline of `crop` square in the middle, and \"subject view\" in size 20.
  - The screen (lines 181-217): the observer, then the subject, drawn with
    `:v0 1.0 :v1 0.0`; the inset, a `crop` square at 20 in from the subject
    view's corner sampling the middle `crop` of the subject target (still
    flipped), with a GREEN outline; and a DARKGRAY line between the views.

  Deviations. Each half's target is the size of its viewport, not 400 by 450: the
  field below Back is cut in two, stacked in portrait and side by side in
  landscape as 3D Split Screen does, so a half is about the screen's width in
  portrait. The `crop` and the inset's 20 stay in target pixels, so on a large
  half they are small. The text sizes are the original's times the half's scale
  (the smaller of its width over 400 and height over 450), cut back until they
  fit. The frame counter is the original's `frame`, one a frame. The cameras
  are not fitted to the half's aspect (they keep the original's 45), and the
  prism uses the half's aspect. The prism is drawn over the cube instead of
  being depth tested against it, because the painter has no depth buffer. The
  original's `fps!` is dropped.

  The state holds `:frame`. Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.soft3d :as s3]))

(def crop "The original's CROP." 128)
(def fovy "The original's FOVY." 45.0)
(def frustum-depth "The original's FRUSTUM-DEPTH." 3.0)
(def original-half-w "The original's HALF-W." 400)
(def original-half-h "The original's window height, which its views fill." 450)
(def inset-margin "Where the inset sits, in from the subject view's corner." 20)

(def background-colour "RAYWHITE, the clear of both targets." [245 245 245 255])
(def screen-colour "BLACK, the clear of the screen." [0 0 0 255])
(def cube-colour "GOLD." [255 203 0 255])
(def wire-colour "PINK." [255 109 194 255])
(def prism-colour "GREEN." [0 228 48 255])
(def crop-colour "GREEN." [0 228 48 255])
(def label-colour "BLACK." [0 0 0 255])
(def note-colour "DARKGRAY." [80 80 80 255])
(def divider-colour "DARKGRAY." [80 80 80 255])

(def observer-label "The original's text." "observer view")
(def subject-label "The original's text." "subject view")
(def note-line "The original's text." "both cameras orbit on their own")

(def origin [0.0 0.0 0.0])

;; --- vectors, the original's v-, v+, v*, cross and normalize -------------------

(defn- v- [[ax ay az] [bx by bz]] [(- ax bx) (- ay by) (- az bz)])
(defn- v+ [[ax ay az] [bx by bz]] [(+ ax bx) (+ ay by) (+ az bz)])
(defn- v* [[x y z] k] [(* x k) (* y k) (* z k)])

(defn- cross [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by))
   (- (* az bx) (* ax bz))
   (- (* ax by) (* ay bx))])

(defn- normalize [[x y z :as v]]
  (let [len (Math/sqrt (+ (* x x) (* y y) (* z z)))]
    (if (zero? len) v (v* v (/ 1.0 len)))))

;; --- the cameras ----------------------------------------------------------------

(defn- orbit
  "A position circling `target` at `radius` and `height`, `speed` radians a
  frame, at frame `n`. The original's `orbit`."
  [[tx ty tz] radius height speed n]
  (let [a (* n speed)]
    [(+ tx (* radius (Math/cos a)))
     (+ ty height)
     (+ tz (* radius (Math/sin a)))]))

(defn subject-position
  "Where the subject camera is at frame `n`."
  [n]
  (orbit origin 5.0 2.0 0.012 n))

(defn observer-position
  "Where the observer camera is at frame `n`."
  [n]
  (orbit origin 14.0 10.0 0.004 n))

(defn camera
  "A camera at `pos` looking at the origin, fovy 45, perspective, as
  `raylib.soft3d/view-proj` takes it."
  [pos]
  {:position pos
   :target origin
   :up [0.0 1.0 0.0]
   :fovy fovy
   :projection :perspective})

(defn subject-camera [state] (camera (subject-position (:frame state))))
(defn observer-camera [state] (camera (observer-position (:frame state))))

(defn frustum-corners
  "The four far-plane corners of the camera at `pos` looking at `target`, at
  `frustum-depth`, in the order that walks the rectangle. The original's
  `frustum-corners`."
  [pos target aspect]
  (let [forward (normalize (v- target pos))
        right (normalize (cross forward [0.0 1.0 0.0]))
        up (cross right forward)
        half-h (* frustum-depth (Math/tan (/ (* fovy Math/PI) 360.0)))
        half-w (* half-h aspect)
        centre (v+ pos (v* forward frustum-depth))]
    [(v+ centre (v+ (v* right (- half-w)) (v* up half-h)))
     (v+ centre (v+ (v* right half-w) (v* up half-h)))
     (v+ centre (v+ (v* right half-w) (v* up (- half-h))))
     (v+ centre (v+ (v* right (- half-w)) (v* up (- half-h))))]))

(defn prism-segments
  "The subject camera drawn as what it is: an apex at `pos` and four edges out
  to the corners of what it can see, then the four edges of the far rectangle,
  as `raylib.soft3d/lines` takes them. The original's `draw-prism!`."
  [pos aspect]
  (let [corners (frustum-corners pos origin aspect)]
    (into (mapv (fn [c] [pos c prism-colour]) corners)
          (map (fn [i] [(nth corners i) (nth corners (mod (inc i) 4)) prism-colour]))
          (range 4))))

;; --- layout -------------------------------------------------------------------

(defn crop-uv
  "The inset's source rectangle in a `w` by `h` target, as `[u0 v0 u1 v1]`: the
  middle `crop` square, with v running bottom-up, because GL stores a target that
  way. For 400 by 450 these are the original's."
  [w h]
  (let [x (quot (- w crop) 2)
        y (quot (- h crop) 2)]
    [(/ x (double w))
     (- 1.0 (/ y (double h)))
     (/ (+ x crop) (double w))
     (- 1.0 (/ (+ y crop) (double h)))]))

(defn dimensions
  "The layout for `metrics`' `:screen`: `:portrait?`, `:halves` (the observer's
  and the subject's `[x y w h]`, the sizes of the two targets), `:divider`
  (`[x1 y1 x2 y2]`), `:crop-rect` and `:inset-rect` (`[x y w h]` in the subject
  view's own pixels), `:inset-uv`, and the text of each view in
  `:observer-lines` and `:subject-lines` as `{:s :x :y :size}` in that view's
  pixels. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        fh (- h top)
        portrait? (< w fh)
        hw (if portrait? (int w) (quot (int w) 2))
        hh (if portrait? (quot fh 2) fh)
        o [0 top hw hh]
        s (if portrait? [0 (+ top hh) hw hh] [hw top hw hh])
        divider (if portrait?
                  [0 (nth s 1) w (nth s 1)]
                  [hw top hw (+ top hh)])
        k (min (/ hw (double original-half-w)) (/ hh (double original-half-h)))
        pad (max 4 (int (* 10 k)))
        line (fn [text size y]
               (let [fit (int (/ (* (- hw (* 2 pad)) 100.0) (measure text 100)))]
                 {:s text
                  :x pad
                  :y y
                  :size (max 8 (min size fit))}))
        big (int (* 20 k))
        small (int (* 10 k))
        bottom (- hh (int (* 30 k)))]
    {:portrait? portrait?
     :scale k
     :halves [o s]
     :divider divider
     :crop-rect [(quot (- hw crop) 2) (quot (- hh crop) 2) crop crop]
     :inset-rect [inset-margin inset-margin crop crop]
     :inset-uv (crop-uv hw hh)
     :observer-lines [(line note-line small pad)
                      (line observer-label big bottom)]
     :subject-lines [(line subject-label big bottom)]}))

;; --- the picture ----------------------------------------------------------------

(defn- world
  "The original's `draw-scene!`: the gold cube of side 2 with pink wires on it, and
  the grid."
  [dl vp]
  (-> dl
      (s3/grid vp 10 1.0)
      (s3/cube vp nil origin 2.0 cube-colour {:shade :flat})
      (s3/cube-wires vp nil origin 2.0 wire-colour {:hide-back? true})))

(defn subject-list
  "The finished draw list of the subject view, in the target's own pixels."
  [state dims]
  (let [[_ _ hw hh] (first (:halves dims))
        vp (s3/view-proj (subject-camera state) [0 0 hw hh])]
    (s3/finish (world [] vp))))

(defn observer-list
  "The finished draw list of the observer view: the world and the subject
  camera's prism."
  [state dims]
  (let [[_ _ hw hh] (first (:halves dims))
        vp (s3/view-proj (observer-camera state) [0 0 hw hh])]
    (s3/finish (s3/lines (world [] vp) vp nil
                         (prism-segments (subject-position (:frame state))
                                         (/ (double hw) hh))))))

(defn advance
  "One frame: the counter moves on."
  [state _]
  (update state :frame inc))

(defn- init [_] [{:frame 0} [[:scene/init :fbrender]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :fbrender]]])

(defn scene []
  {:id :fbrender
   :title "Framebuffer Rendering"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
