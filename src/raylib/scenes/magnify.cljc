(ns raylib.scenes.magnify
  "Magnifying Glass, ported from raylib-jlt's `magnifying_glass`
  (net/b12n/raylib_jlt/magnifying_glass.clj, EPL 2.0), which is raylib's
  `textures_magnifying_glass` (zlib). This is an altered version of both.

  A round lens follows your finger and shows the scene at 3x. Five markers are
  drawn only into the lens, so there are things in the scene that cannot be
  found without moving the glass over them. The scene is drawn twice: once at
  its own size to the screen, and once magnified into a small render target
  whose middle is the point under the finger. The target is then drawn back as a
  disc, a fan of textured triangles, under a black ring.

  Mirrored from magnifying_glass.clj:
  - The constants (lines 31-35): `lens` 220, `zoom` 3.0 and `segments` 64, with
    the original's 800 by 450 as `window-w` and `window-h`.
  - The hidden markers (lines 38-39): `hidden-spots`, five of them, drawn only on
    the magnified pass, each a LIME circle of radius 11 with a DARKGREEN one of
    radius 6 on it.
  - The scene (lines 41-72): the Perlin backdrop stretched over the window, then
    seven squares of side 56 at x = 60 + 100 i, y = 90, coloured
    (40 + 25 i, 90, 210 - 20 i), each with a circle of radius 22 at
    (x + 28, 300) coloured (230, 60 + 20 i, 70).
  - The lens (lines 74-97, 126-150): the disc of `segments` wedges whose texcoords
    come off the unit circle, with v flipped because a render target is stored
    bottom-up, and a BLACK ring 4 thick on its rim. The magnified pass moves the
    scene so the point under the finger lands in the middle of the target:
    ox = half - x * zoom.
  - The texts (lines 151-157): \"five markers are drawn only inside the lens\"
    once the lens has been moved, \"move the pointer to take over the lens\"
    before, in size 18 BLACK at (20, 16).
  - The idle lens (lines 117-125): until the pointer moves, the lens walks its own
    path, (W/2 + 250 sin t, H/2 + 120 sin 1.7 t) with t = 0.02 a frame.

  The backdrop is raylib's own Perlin image, 800 by 450, offsets 0, scale 6.0,
  made in C by `raylib.texture/perlin-texture!`. `backdrop-texel` is the same
  image one texel at a time through `raylib.perlin`, which the tests check
  against bytes measured from the C.

  Deviations.
  - **A finger is the pointer.** The lens follows a finger that landed in the
    field and stays where it was left when the finger lifts. A finger that
    lands in the Back region, or slides in from outside the field, does not
    take it. The lens centre is held to the field, so the disc never covers
    the Back button, which means a marker within a lens radius of the field's
    edge is reached with the lens at that edge instead of centred on it.
  - **The field, not an 800 by 450 window.** The scene is the safe region below
    Back. The original's coordinates are laid over it as fractions: x by
    field width over 800 and y by field height over 450, and every length (the
    squares, the circles, the markers, the text) by the smaller of the two, so
    the scene fills the field and the shapes stay round. The backdrop is
    stretched the same way. It is 800 by 450 texels whatever the screen, so on
    a large screen it is enlarged and drawn smooth (linear) where the original's
    is not.
  - **The lens stays 220.** It is the original's size in target pixels, so on
    the phone it is a small disc, and a finger covers it.
  - **The idle path** is the original's, laid over the field the same way.

  The state holds `:frame`, `:lens` (the centre in field pixels), `:moved?` and
  `:held?` (a finger that took the lens is still down). Colours are `[r g b a]`
  vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.perlin :as perlin]))

(def window-w "The original's W." 800)
(def window-h "The original's H." 450)
(def lens "The original's LENS, in target pixels." 220)
(def zoom "The original's ZOOM." 3.0)
(def segments "The original's SEGMENTS." 64)

(def hidden-spots
  "The original's markers that show only in the lens, in its 800 by 450 window."
  [[170 150] [560 120] [300 330] [660 300] [430 210]])

(def backdrop-spec
  "The arguments of GenImagePerlinNoise the original calls: its window size,
  offsets 0 and 0, and scale 6.0."
  {:w window-w
   :h window-h
   :offset-x 0
   :offset-y 0
   :scale 6.0})

(def background-colour "RAYWHITE." [245 245 245 255])
(def ring-colour "BLACK." [0 0 0 255])
(def text-colour "BLACK." [0 0 0 255])
(def marker-colour "LIME." [0 158 47 255])
(def marker-core-colour "DARKGREEN." [0 117 44 255])
(def ring-width "The ring's thickness, the original's 4." 4)

(def moved-text "The original's text once the lens has moved."
  "five markers are drawn only inside the lens")
(def idle-text "The original's text before it has."
  "move the pointer to take over the lens")

(defn backdrop-texel
  "The grey byte of texel (`x`, `y`) of the backdrop, from the pure model. The C
  image's R, G and B are all this and its alpha is 255."
  [x y]
  (let [{:keys [w h offset-x offset-y scale]} backdrop-spec]
    (perlin/perlin-grey w h offset-x offset-y scale x y)))

;; --- layout -------------------------------------------------------------------

(defn layout
  "Where everything sits for `metrics`' `:screen`: the `:field` (`{:x :y :w :h}`,
  the safe region below Back), `:sx` and `:sy` (the field over the original's
  window, per axis), `:k` (the smaller, for lengths) and `:half` (half the lens)."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        fw (int w)
        fh (max 1 (int (- h top)))
        sx (/ fw (double window-w))
        sy (/ fh (double window-h))]
    {:field {:x 0
             :y top
             :w fw
             :h fh}
     :sx sx
     :sy sy
     :k (min sx sy)
     :half (/ lens 2.0)}))

(defn dimensions
  "`layout` plus the text: `:lines` is `{:idle :moved}`, each `{:s :x :y :size}`
  in field pixels, size 18 times `k` cut back until it fits. `measure` is
  `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [field k]
         :as lay} (layout metrics)
        pad (* 20.0 k)
        fit (fn [s]
              (let [fit-size (int (/ (* (- (:w field) (* 2 pad)) 100.0) (max 1 (measure s 100))))]
                {:s s
                 :x (int pad)
                 :y (int (* 16.0 k))
                 :size (max 8 (min (int (* 18.0 k)) fit-size))}))]
    (assoc lay :lines {:idle (fit idle-text)
                       :moved (fit moved-text)})))

(defn clamp-lens
  "The lens centre `[x y]` held so the whole disc is inside the field."
  [{:keys [field half]} [x y]]
  (let [lo-x half
        hi-x (max half (- (:w field) half))
        lo-y half
        hi-y (max half (- (:h field) half))]
    [(double (max lo-x (min hi-x x)))
     (double (max lo-y (min hi-y y)))]))

(defn idle-position
  "Where the lens is on frame `n` before a finger has taken it: the original's
  walk, (W/2 + 250 sin t, H/2 + 120 sin 1.7 t) with t = 0.02 n, laid over the
  field."
  [{:keys [field sx sy]
    :as lay} n]
  (let [t (* n 0.02)]
    (clamp-lens lay [(+ (/ (:w field) 2.0) (* 250.0 sx (Math/sin t)))
                     (+ (/ (:h field) 2.0) (* 120.0 sy (Math/sin (* t 1.7))))])))

;; --- the picture ----------------------------------------------------------------

(defn lens-offset
  "The origin of the magnified pass for a lens at `[x y]` in the field: the point
  under the lens lands in the middle of the target, ox = half - x * zoom."
  [{:keys [half]} [x y]]
  [(- half (* x zoom)) (- half (* y zoom))])

(defn world-plan
  "The scene drawn with its origin at (`ox`, `oy`) and magnified `z` times:
  `{:backdrop {:x :y :width :height} :items [...]}` where an item is
  `[:rect x y w h colour]` or `[:circle x y r colour]`, in drawing order.
  `reveal?` adds the hidden markers. The original's `draw-scene!`."
  [{:keys [field sx sy k]} ox oy z reveal?]
  (let [px (fn [x] (+ ox (* x sx z)))
        py (fn [y] (+ oy (* y sy z)))
        len (fn [v] (* v k z))]
    {:backdrop {:x ox
                :y oy
                :width (* (:w field) z)
                :height (* (:h field) z)}
     :items (into (vec (mapcat (fn [i]
                                 (let [x (+ 60 (* i 100))]
                                   [[:rect (px x) (py 90) (len 56) (len 56)
                                     [(+ 40 (* i 25)) 90 (- 210 (* i 20)) 255]]
                                    [:circle (px (+ x 28)) (py 300) (len 22)
                                     [230 (+ 60 (* i 20)) 70 255]]]))
                               (range 7)))
                  (when reveal?
                    (mapcat (fn [[x y]]
                              [[:circle (px x) (py y) (len 11) marker-colour]
                               [:circle (px x) (py y) (len 6) marker-core-colour]])
                            hidden-spots)))}))

(def ^:private rim-table
  "The unit circle for `n` wedges: `n` + 1 entries of `[cos sin u v]` for the rim
  points at i = 0 to n, where u and v come off the unit circle and v is flipped
  (1 - v) because a render target is stored bottom-up. It depends on `n` alone,
  and building it was most of a frame (0.10 ms of 0.25 under laptop jolt)."
  (memoize
   (fn [n]
     (mapv (fn [i]
             (let [a (* 2.0 Math/PI (/ (double i) n))
                   c (Math/cos a)
                   s (Math/sin a)]
               [c s (+ 0.5 (* 0.5 c)) (- 1.0 (+ 0.5 (* 0.5 s)))]))
           (range (inc n))))))

(defn disc-verts
  "The lens as a flat `[x y u v ...]`, three vertices to a wedge, `n` wedges
  around (`cx`, `cy`) at `r`: the centre, then the rim at the next angle, then
  the rim at this one, as the original lists them. u and v come off the unit
  circle, and v is flipped (1 - v) because a render target is stored bottom-up."
  [cx cy r n]
  (let [table (rim-table n)
        cx (double cx)
        cy (double cy)]
    (loop [i 0
           out (transient [])]
      (if (< i n)
        (let [[c0 s0 u0 v0] (nth table i)
              [c1 s1 u1 v1] (nth table (inc i))]
          (recur (inc i)
                 (-> out
                     (conj! cx) (conj! cy) (conj! 0.5) (conj! 0.5)
                     (conj! (+ cx (* r c1))) (conj! (+ cy (* r s1))) (conj! u1) (conj! v1)
                     (conj! (+ cx (* r c0))) (conj! (+ cy (* r s0))) (conj! u0) (conj! v0))))
        (persistent! out)))))

(defn ring
  "The ring on the lens's rim as `raylib.host/draw-ring` takes it: `[cx cy inner
  outer start end segments]`. 4 thick, a full circle in `segments` wedges."
  [cx cy]
  [cx cy (- (/ lens 2.0) ring-width) (/ lens 2.0) 0 360 segments])

;; --- the scene ------------------------------------------------------------------

(defn- fresh [input]
  (let [lay (layout (:metrics input))]
    {:frame 0
     :lens (idle-position lay 0)
     :moved? false
     :held? false}))

(defn advance
  "One frame. A press that lands in the field takes the lens, and it follows that
  finger while it stays down. Until then the lens walks its idle path. The
  centre is held to the field every frame, so a turn pulls it back in."
  [state input]
  (let [lay (layout (:metrics input))
        {:keys [field]} lay
        {:keys [phase position]} (:pointer input)
        local (fn [[px py]] [(- px (:x field)) (- py (:y field))])
        in-field? (fn [p] (and (gesture/in-rect? [(:x field) (:y field) (:w field) (:h field)] p)
                               (not (gesture/in-back-region? p))))
        state (update state :frame inc)
        took (cond
               (not (gesture/down? input)) (assoc state :held? false)
               (and (= :press phase) (in-field? position)) (assoc state :held? true :moved? true)
               (= :press phase) (assoc state :held? false)
               :else state)
        lens-pos (cond
                   (and (:held? took) (gesture/down? input)) (local position)
                   (:moved? took) (:lens took)
                   :else (idle-position lay (:frame took)))]
    (assoc took :lens (clamp-lens lay lens-pos))))

(defn- init [input] [(fresh input) [[:scene/init :magnify]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :magnify]]])

(defn scene []
  {:id :magnify
   :title "Magnifying Glass"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
