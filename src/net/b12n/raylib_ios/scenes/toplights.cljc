(ns net.b12n.raylib-ios.scenes.toplights
  "Top Down Lights, ported from raylib-jlt's `top_down_lights`
  (net/b12n/raylib_jlt/top_down_lights.clj, EPL 2.0), which is raylib's
  `shapes_top_down_lights` by Jeffery Myers (zlib). This is an altered version of
  both.

  Nothing here draws light. Every light renders a full-field mask whose ALPHA is
  all that matters, the masks are merged into one, and that one is drawn over the
  ground as black: alpha 0 reads as lit, alpha 1 as dark. Two custom blend
  equations do the merging. GL_MIN keeps the smaller of source and destination,
  so a light's gradient punches its transparent centre into a mask cleared to
  opaque white, and the master mask keeps the brightest light at each pixel.
  GL_MAX keeps the larger, so a shadow quad at alpha 1 cuts itself back out of
  the disc. Both ignore the factors, which is why the same SRC_ALPHA pair goes to
  each.

  Mirrored from top_down_lights.clj:
  - The constants (lines 34-38): `max-lights` 16, `n-boxes` 20 and `tile` 64.
  - The boxes (lines 179-197): two placed by hand, (150, 80) and (500, 350), each
    40 square, then 18 more with x in 0 to W, y in 0 to H and w and h in 10 to
    100, drawn in that order from the project LCG in place of GetRandomValue.
  - The shadows (lines 61-101): `shadow-quad`, `box-shadows`, `overlaps?` and
    `inside?` as written, so each box casts one quad for every edge the light is
    outside of, plus its own footprint, and a light inside a box goes dark.
  - The mask (lines 103-124): clear to WHITE, then the blend with GL_SRC_ALPHA,
    GL_SRC_ALPHA and GL_MIN around the light's gradient (clear at the centre,
    WHITE at the rim), then GL_SRC_ALPHA, GL_SRC_ALPHA and GL_MAX around the
    shadow quads in WHITE. `mask-plan` is that sequence.
  - The master (lines 141-157): clear to BLACK, then GL_MIN around every mask
    drawn full size. `master-plan`.
  - The lights (lines 159-169, 220, 239): light 1 has radius 300 and starts at
    (600, 400), the others have 200, up to 16.
  - The idle light (lines 171-177): until it is dragged, light 1 walks
    (W/2 + 0.34 W sin t, H/2 + 0.30 H sin 2t) with t = 0.012 a frame.
  - The picture (lines 254-309): the checkered ground repeated over the field,
    the master over it (in a 191 alpha tint while the volumes show), a circle of
    radius 10 on every light (YELLOW for light 1, WHITE for the rest), the
    volumes (light 1's shadow quads in DARKPURPLE, the boxes it can reach in
    PURPLE, every box outlined in DARKBLUE) and the three texts.
  - The ground (lines 207-213): 64 square, DARKBROWN where the x and y halves
    agree and DARKGRAY where they do not.

  Inputs. The mouse becomes touch. A finger that lands in the field and travels
  past the tap slop drags light 1, which then stays where it is left. A quick
  touch that does not travel adds a light there (the right button), up to 16.
  A button toggles the shadow volumes (F1).

  Deviations.
  - **The field, not an 800 by 450 window.** Positions are the original's
    fractions of the field, and every length (the boxes, the radii, the tile,
    the markers) is scaled by the smaller of field width over 800 and field
    height over 450, so the world fills the field and the light reaches as far,
    in proportion. Each mask and the master are the size of the field.
  - **The texts.** \"drag to move light #1\", \"tap to add a new light\" (the
    original says right click) and the button's own label in place of
    \"(F1) show shadow volumes\". Their sizes are the original's 10 times the
    scale, raised to 24 so a phone can read them, and spaced to fit.
  - **Dropped.** The FPS counter.
  - **A light standing inside a box** goes dark, as in raylib-jlt's port (the C
    returns early and leaves the last mask up).
  - **A turn** resets the lights, since positions are in field pixels.

  The state holds `:lights` (`{:pos :radius :valid? :shadows}`), `:dirty` (the
  indices whose mask must be redrawn THIS frame and nothing older; the master is
  merged when it is not empty), `:release` (the indices of masks to give back
  this frame), `:boxes`, `:steered?`, `:show?`, `:gesture`,
  `:frame` and the `:screen` it was laid out for. Colours are `[r g b a]`
  vectors."
  (:require [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.texel :as texel]))

(def max-lights "The original's MAX-LIGHTS." 16)
(def n-boxes "The original's N-BOXES." 20)
(def tile "The original's TILE." 64)
(def window-w "The original's W." 800)
(def window-h "The original's H." 450)
(def seed "The LCG's start." 20261002)

(def first-light-radius "Light 1's radius." 300)
(def other-light-radius "The radius of a light added later." 200)
(def marker-radius "The circle drawn on a light." 10)

(def white [255 255 255 255])
(def black [0 0 0 255])
(def clear-white "The gradient's clear centre." [255 255 255 0])
(def dark-brown "DARKBROWN." [76 63 47 255])
(def dark-gray "DARKGRAY." [80 80 80 255])
(def yellow "YELLOW." [253 249 0 255])
(def dark-purple "DARKPURPLE." [112 31 126 255])
(def purple "PURPLE." [200 122 255 255])
(def dark-blue "DARKBLUE." [0 82 172 255])
(def help-colour "DARKGREEN, the help texts." [0 117 44 255])
(def volumes-tint "The master's tint while the volumes show: alpha 191." [255 255 255 191])
(def button-colour "LIGHTGRAY." [200 200 200 255])
(def button-on-colour "GRAY." [130 130 130 255])
(def button-label-colour "DARKGRAY." [80 80 80 255])
(def button-on-label-colour "RAYWHITE." [245 245 245 255])

(def show-label "The button when the volumes are hidden." "show shadow volumes")
(def hide-label "The button when the volumes show." "hide shadow volumes")
(def help-lines "The two help texts, in the original's order." ["drag to move light #1" "tap to add a new light"])

(defn ground-pixel
  "The ground's texel `(x, y)` of its `tile` square, as `[r g b a]`: DARKBROWN
  where the x and y halves agree, DARKGRAY where they do not."
  [x y]
  (if (= (< x (quot tile 2)) (< y (quot tile 2))) dark-brown dark-gray))

(defn ground-spec
  "The ground's texture spec: one `tile` square, repeating (64 is a power of two),
  unfiltered. A static spec, so build it once."
  []
  {:w tile
   :h tile
   :wrap :repeat
   :pixel (fn [x y] (texel/pack (ground-pixel x y)))})

;; --- the LCG --------------------------------------------------------------------

(defn- next-random [s]
  (mod (+ (* 1103515245 (long s)) 12345) 2147483648))

(defn- roll
  "An int in [lo, hi] from the high bits of the LCG state `s'`."
  [s' lo hi]
  (+ lo (mod (quot s' 65536) (inc (- hi lo)))))

(defn random-boxes
  "The 18 random boxes in the original's 800 by 450 window, `{:x :y :w :h}`, each
  drawn x, y, w, h in that order from the LCG at `seed`."
  []
  (loop [i 0
         s seed
         out []]
    (if (< i (- n-boxes 2))
      (let [sx (next-random s)
            sy (next-random sx)
            sw (next-random sy)
            sh (next-random sw)]
        (recur (inc i) sh (conj out {:x (roll sx 0 window-w)
                                     :y (roll sy 0 window-h)
                                     :w (roll sw 10 100)
                                     :h (roll sh 10 100)})))
      out)))

;; --- layout -------------------------------------------------------------------

(defn layout
  "Where everything sits for `metrics`' `:screen`: the `:field` (`{:x :y :w :h}`,
  the safe region below Back, which is also the size of every mask), `:sx` and
  `:sy` (field over the original's window, per axis), `:k` (the smaller, for
  lengths) and `:button` (`{:x :y :w :h}` in field pixels)."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        fw (int w)
        fh (max 1 (int (- h top)))
        sx (/ fw (double window-w))
        sy (/ fh (double window-h))
        k (min sx sy)
        margin (int (* 10 k))
        bh (max 72 (int (* 32 k)))
        bw (min (- fw (* 2 margin)) (max 300 (int (* 240 k))))]
    {:field {:x 0
             :y top
             :w fw
             :h fh}
     :sx sx
     :sy sy
     :k k
     :button {:x margin
              :y (max 0 (- fh bh margin))
              :w bw
              :h bh}}))

(defn dimensions
  "`layout` plus the text, in field pixels: `:help` (two `{:s :x :y :size}`) and
  `:labels` (`{:show :hide}`, each a `{:s :x :y :size}` centred in the button).
  Sizes are 10 times `k`, raised to 24 and cut back to fit. `measure` is
  `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [k field button]
         :as lay} (layout metrics)
        pad (max 4 (int (* 10 k)))
        fit (fn [size s width]
              (max 8 (min size (int (/ (* width 100.0) (max 1 (measure s 100)))))))
        size (fit (max 24 (int (* 10 k))) (apply max-key count help-lines) (- (:w field) (* 2 pad)))
        step (max (int (* 20 k)) (+ size 4))
        help (vec (map-indexed (fn [i s] {:s s
                                          :x pad
                                          :y (+ pad (* i step))
                                          :size size})
                               help-lines))
        label (fn [s]
                (let [sz (fit (int (* 0.4 (:h button))) s (- (:w button) 16))
                      tw (measure s sz)]
                  {:s s
                   :x (int (+ (:x button) (/ (- (:w button) tw) 2.0)))
                   :y (int (+ (:y button) (/ (- (:h button) sz) 2.0)))
                   :size sz}))]
    (assoc lay
           :help help
           :labels {:show (label show-label)
                    :hide (label hide-label)})))

(defn tile-size
  "The ground's tile as drawn, in field pixels."
  [{:keys [k]}]
  (* tile k))

(defn boxes
  "The world's boxes laid over the field: two placed by hand, then the 18 from the
  LCG, with positions the original's fractions of the field and sizes scaled by
  `k`. `{:x :y :w :h}` in field pixels."
  [{:keys [sx sy k]}]
  (mapv (fn [{:keys [x y w h]}]
          {:x (* x sx)
           :y (* y sy)
           :w (* w k)
           :h (* h k)})
        (into [{:x 150
                :y 80
                :w 40
                :h 40}
               {:x 500
                :y 350
                :w 40
                :h 40}]
              (random-boxes))))

;; --- the shadows, the original's lines 61-101 -----------------------------------------

(defn- shadow-quad
  "The shadow volume one edge casts: the edge itself, plus both endpoints pushed
  directly away from the light by twice its radius so the volume always outruns
  the lit disc."
  [[lx ly] radius [sx sy] [ex ey]]
  (let [ext (* 2.0 radius)
        away (fn [px py]
               (let [dx (- px lx)
                     dy (- py ly)
                     len (Math/sqrt (+ (* dx dx) (* dy dy)))
                     len (if (zero? len) 1.0 len)]
                 [(+ px (* ext (/ dx len)))
                  (+ py (* ext (/ dy len)))]))]
    [[sx sy] [ex ey] (away ex ey) (away sx sy)]))

(defn box-shadows
  "Every shadow volume one box casts for a light at `pos`: one per edge the light
  is on the outside of, plus the box's own footprint. Each is four corners."
  [[lx ly :as pos] radius {:keys [x y w h]}]
  (let [tl [x y]
        tr [(+ x w) y]
        br [(+ x w) (+ y h)]
        bl [x (+ y h)]
        edges (cond-> []
                (> ly y) (conj [tl tr])
                (< lx (+ x w)) (conj [tr br])
                (< ly (+ y h)) (conj [br bl])
                (> lx x) (conj [bl tl]))]
    (conj (mapv (fn [[sp ep]] (shadow-quad pos radius sp ep)) edges)
          [tl bl br tr])))

(defn overlaps?
  "CheckCollisionRecs between a box and the light's bounding square."
  [{:keys [x y w h]} [lx ly] radius]
  (and (< (- lx radius) (+ x w)) (> (+ lx radius) x)
       (< (- ly radius) (+ y h)) (> (+ ly radius) y)))

(defn inside?
  "CheckCollisionPointRec: the light is standing in the box, so nothing escapes."
  [{:keys [x y w h]} [lx ly]]
  (and (>= lx x) (<= lx (+ x w)) (>= ly y) (<= ly (+ y h))))

(defn refresh
  "`light` with its shadow volumes recomputed against `boxes`. A light inside a
  box is not `:valid?` and casts none, so its mask is all dark."
  [{:keys [pos radius]
    :as light} boxes]
  (let [blocked? (boolean (some (fn [b] (inside? b pos)) boxes))]
    (assoc light
           :valid? (not blocked?)
           :shadows (if blocked?
                      []
                      (into [] (comp (filter (fn [b] (overlaps? b pos radius)))
                                     (mapcat (fn [b] (box-shadows pos radius b))))
                            boxes)))))

(defn make-light
  "A light at (`x`, `y`) with `radius`, not yet refreshed."
  [x y radius]
  {:pos [(double x) (double y)]
   :radius (double radius)
   :valid? false
   :shadows []})

(defn idle-position
  "Where light 1 is on frame `n` while nobody has dragged it: the original's
  walk laid over the field."
  [{:keys [field]} n]
  (let [t (* n 0.012)]
    [(+ (/ (:w field) 2.0) (* 0.34 (:w field) (Math/sin t)))
     (+ (/ (:h field) 2.0) (* 0.30 (:h field) (Math/sin (* 2.0 t))))]))

;; --- the passes -------------------------------------------------------------------

(defn mask-plan
  "One light's mask pass, in order, as the original draws it:
  `[[:clear colour] [:blend :min items] [:blend :max items]]`. The blend factors
  are GL_SRC_ALPHA twice and the equation named. Items are
  `[:gradient x y radius inner outer]` or `[:quad corners colour]`. A light that
  is not `:valid?` draws no gradient."
  [{:keys [pos radius valid? shadows]}]
  [[:clear white]
   [:blend :min (if valid?
                  [[:gradient (first pos) (second pos) radius clear-white white]]
                  [])]
   [:blend :max (mapv (fn [q] [:quad q white]) shadows)]])

(defn master-plan
  "The merge of `n` masks, as the original draws it:
  `[[:clear colour] [:blend :min [[:mask i] ...]]]`, each mask drawn full size."
  [n]
  [[:clear black]
   [:blend :min (mapv (fn [i] [:mask i]) (range n))]])

(defn quad-triangles
  "A four-corner quad as the two triangles the original's DrawTriangleFan makes,
  `[[a b c] [a c d]]`. Winding is left to the caller's draw."
  [[a b c d]]
  [[a b c] [a c d]])

(defn volume-plan
  "What the shadow volumes add to the picture while they show, for light 1:
  `{:quads [corners ...] :reached [box ...] :outlined [box ...]}`. The quads are
  light 1's shadow volumes (DARKPURPLE), `:reached` the boxes inside its bounding
  square (PURPLE) and `:outlined` every box (DARKBLUE)."
  [{:keys [lights boxes]}]
  (let [lead (first lights)]
    {:quads (:shadows lead)
     :reached (filterv (fn [b] (overlaps? b (:pos lead) (:radius lead))) boxes)
     :outlined boxes}))

;; --- the scene ------------------------------------------------------------------

(defn- fresh [input]
  (let [lay (layout (:metrics input))
        k (:k lay)
        bxs (boxes lay)
        light (refresh (make-light (* 600 (:sx lay)) (* 400 (:sy lay)) (* first-light-radius k)) bxs)]
    {:screen (get-in input [:metrics :screen])
     :frame 0
     :boxes bxs
     :lights [light]
     :dirty [0]
     :release []
     :steered? false
     :show? false
     :gesture gesture/idle}))

(defn- local
  "A touch point in field pixels."
  [lay [px py]]
  [(- px (get-in lay [:field :x])) (- py (get-in lay [:field :y]))])

(defn- clamp-point
  [{:keys [field]} [x y]]
  [(double (max 0.0 (min (double (:w field)) x)))
   (double (max 0.0 (min (double (:h field)) y)))])

(defn- in-field?
  "Whether the touch point `p` begins a touch the scene owns: in the field, not
  under Back and not on the button."
  [lay p]
  (let [{:keys [x y w h]} (:field lay)
        b (:button lay)
        [lx ly] (local lay p)]
    (and (gesture/in-rect? [x y w h] p)
         (not (gesture/in-back-region? p))
         (not (gesture/in-rect? [(:x b) (:y b) (:w b) (:h b)] [lx ly])))))

(defn- on-button? [lay p]
  (let [b (:button lay)]
    (gesture/in-rect? [(:x b) (:y b) (:w b) (:h b)] (local lay p))))

(defn advance
  "One frame. A touch that travels past the slop drags light 1 to the finger; until
  it has, light 1 walks. A tap in the field adds a light while there are fewer
  than `max-lights`, and a tap on the button toggles the volumes. Every light
  that moved or is new is refreshed and listed in `:dirty`, which holds only this
  frame. A turn starts the scene again, and lists in `:release` the masks of the
  lights it dropped (that frame only), which the draw shrinks to a pixel so their
  field-sized targets do not sit on the GPU until the scene is left."
  [state input]
  (let [screen (get-in input [:metrics :screen])]
    (if (not= screen (:screen state))
      (assoc (fresh input) :release (vec (range 1 (count (:lights state)))))
      (let [lay (layout (:metrics input))
            [g ev] (gesture/track (:gesture state) input)
            {:keys [position]} (:pointer input)
            dragging? (boolean (and (gesture/down? input)
                                    (:start g)
                                    (in-field? lay (:start g))
                                    (> (:travel g) (gesture/slop (:metrics input)))))
            steered? (or (:steered? state) dragging?)
            lead-pos (cond
                       dragging? (clamp-point lay (local lay position))
                       (not steered?) (idle-position lay (:frame state))
                       :else nil)
            tap (when (= :tap (:type ev)) (:at ev))
            tap-pos (when tap (clamp-point lay (local lay tap)))
            add? (and tap (in-field? lay tap) (< (count (:lights state)) max-lights))
            toggle? (and tap (on-button? lay tap))
            lights (cond-> (:lights state)
                     lead-pos (assoc-in [0 :pos] lead-pos)
                     add? (conj (make-light (first tap-pos) (second tap-pos)
                                            (* other-light-radius (:k lay)))))
            dirty (cond-> []
                    lead-pos (conj 0)
                    add? (conj (dec (count lights))))
            lights (reduce (fn [ls i] (update ls i refresh (:boxes state))) lights dirty)]
        (assoc state
               :frame (inc (:frame state))
               :gesture g
               :steered? steered?
               :lights lights
               :dirty dirty
               :release []
               :show? (if toggle? (not (:show? state)) (:show? state)))))))

(defn- init [input] [(fresh input) [[:scene/init :toplights]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :toplights]]])

(defn scene []
  {:id :toplights
   :title "Top Down Lights"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
