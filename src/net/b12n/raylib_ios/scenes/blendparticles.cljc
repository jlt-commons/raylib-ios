(ns net.b12n.raylib-ios.scenes.blendparticles
  "Particles Blending, ported from raylib-jlt's `particles_blending`
  (net/b12n/raylib_jlt/particles_blending.clj, zlib licence): a pool of 200
  coloured sparks that fall and fade, drawn with alpha blending or additive
  blending.

  What is the original's:
  - The pool (lines 17-21, 35-44): 200 particles, each drawn once from the LCG as
    r, g, b in 0 to 255 and a size of `GetRandomValue(1, 30) / 20`, in that order.
    A particle keeps its colour and size for life. `GetRandomValue` is the
    project's LCG seeded with 20261002, taking its high bits, so every run opens
    the same.
  - `activate-first-inactive` (lines 46-55): the first inactive particle in the
    pool is activated at the emitter, with alpha 1.0. A full pool activates
    nothing.
  - `update-particle` (lines 57-64): every active particle loses 0.005 of alpha,
    goes inactive when that leaves 0.0 or less, and otherwise falls `GRAVITY / 2`
    of 1.5 units. The spawn comes first, so a new particle also ages that frame.
  - The sprite (lines 84-89): `SPARK-SIZE` of 32 times `size`, drawn centred on
    the particle, tinted `(r, g, b, int(255 * alpha))`.
  - The blend (lines 81, 90) and the clear, DARKGRAY (line 80). The texts (lines
    91-103) are BLACK \"PRESS SPACE to CHANGE BLENDING MODE\" at (150, 20) and,
    at (290, H - 40), BLACK \"ALPHA BLENDING\" or, at (280, H - 40), RAYWHITE
    \"ADDITIVE BLENDING\", all at size 20.

  The sprite is a texture the original makes by hand (`spark-pixel`, lines
  23-33, a white dot whose alpha is `(1 - d / r)` squared) and there is no
  texture here, so each particle is a flat circle of the sprite's size, the
  tint's alpha on its whole disc. A circle blended additively saturates faster
  than the soft dot, and the original's per-pixel falloff is gone; the size, the
  colour and the alpha are the original's.

  Controls: a finger held in the field is the emitter, in place of the mouse, one
  particle a frame at the finger. The original activates one a frame at the mouse
  wherever it is, so a pool is always filling; here nothing is emitted with no
  finger down. A finger belongs to the control it began on: one that began in Back
  or on the button never emits wherever it slides, and one that began in the field
  stops while it is over the button. A tap on the button flips alpha and additive,
  in place of SPACE. The tap is by `net.b12n.raylib-ios.gesture`, so it is where the finger
  started, and the flip is on its release frame.

  Units. The original's window is 800 by 450. The particles move in its units
  (the physics above is unchanged), but the window is 800 wide and `(h - top) /
  u` tall, where `u` is the safe region's width over 800 and `top` is the bottom
  of Back, so a circle is round on a phone and a portrait field is taller than
  450 units. The original's second text sits at `H - 40` and the button sits
  under it, its bottom 20 units above the screen's. The top text is centred
  rather than at x 150, and cut back to fit.

  The state holds numbers in mutable arrays: `:xs :ys :alphas` (doubles), `:acts`
  (1 for active) and the colours `:rs :gs :bs` and `:sizes`, the `:blending`
  (0 alpha, 1 additive), the `:gesture` and the `:screen`.
  `advance` updates the arrays IN PLACE and returns the state, so a state is not
  a value to keep across `advance`. `particle` is the allocating accessor for
  tests. The draw method draws through `call-blended!`, which ends the blend mode
  in a `finally`, so a failed draw cannot leave a later scene blending."
  (:require [net.b12n.raylib-ios.gesture :as gesture]))

(def view-w "The original's window width, in its units." 800.0)
(def max-particles "The original's MAX-PARTICLES." 200)
(def spark-size "The original's SPARK-SIZE." 32.0)
(def gravity "The original's GRAVITY." 3.0)
(def fade "The alpha a particle loses a frame (line 60)." 0.005)
(def seed "The LCG's start." 20261002)

(def background-colour "DARKGRAY." [80 80 80 255])
(def hint-colour "BLACK." [0 0 0 255])
(def alpha-colour "BLACK: the label for alpha blending." [0 0 0 255])
(def additive-colour "RAYWHITE: the label for additive blending." [245 245 245 255])
(def button-colour [130 130 130 255])

(def hint "The original's hint, with a tap on the button for SPACE."
  "HOLD to EMIT, TAP the button to CHANGE BLENDING")
(def alpha-label "The original's." "ALPHA BLENDING")
(def additive-label "The original's." "ADDITIVE BLENDING")

(defn blending "0 for alpha, 1 for additive. The original's `blending`." [state]
  (:blending state))

(defn blend-mode
  "raylib.h's BlendMode for `state`: BLEND_ALPHA is 0 and BLEND_ADDITIVE is 1,
  which is the original's `blending` as it passes it to `begin-blend-mode`."
  [state]
  (:blending state))

(defn label [state] (if (zero? (:blending state)) alpha-label additive-label))

(defn label-colour [state] (if (zero? (:blending state)) alpha-colour additive-colour))

(defn geometry
  "The layout for `metrics`' `:screen`, without text: `:w :h :u` (pixels per
  original unit), `:top` (the bottom of Back, where the field begins), `:field-h`
  (the field's height in original units) and the text `:size`."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (double (+ back-y back-h))
        u (/ (double w) view-w)]
    {:w w
     :h h
     :u u
     :top top
     :field-h (/ (- h top) u)
     :size (max 8 (int (* 20.0 u)))}))

(defn dimensions
  "`geometry` plus the text: `:hint` as `{:s :x :y :size}`, centred at the
  original's y of 20 units, cut back to fit the width; `:button` as `[x y w h]`,
  around the widest label with its bottom 20 units above the screen's; and
  `:label-size` and `:label-y`. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w h u top size]
         :as geo} (geometry metrics)
        pad (max 4 (int (* 0.4 size)))
        room (- w (* 2 pad))
        fit (fn [s] (max 8 (min size (int (/ (* room 100.0) (measure s 100))))))
        hint-size (fit hint)
        label-size (min size (fit additive-label))
        bw (+ (measure additive-label label-size) (* 2 pad))
        bh (+ label-size (* 2 pad))
        bx (* 0.5 (- w bw))
        by (- h (* 20.0 u) bh)]
    (assoc geo
           :hint {:s hint
                  :x (max 0 (int (* 0.5 (- w (measure hint hint-size)))))
                  :y (int (+ top (* 20.0 u)))
                  :size hint-size}
           :button [bx by (double bw) (double bh)]
           :label-size label-size
           :label-y (int (+ by pad)))))

(defn label-x
  "The x to draw `label` at so it is centred on the button."
  [dims label measure]
  (let [[bx _ bw _] (:button dims)]
    (int (+ bx (* 0.5 (- bw (measure label (:label-size dims))))))))

(defn- next-random [s]
  (mod (+ (* 1103515245 (long s)) 12345) 2147483648))

(defn- roll
  "An int in [lo, hi] from the high bits of the LCG state `s'`."
  [s' lo hi]
  (+ lo (mod (quot s' 65536) (inc (- hi lo)))))

(defn particle
  "Particle `i` as a map of `:x :y :r :g :b :alpha :size :active?`, in the
  original's shape. Allocates; for tests."
  [state i]
  {:x (aget (:xs state) i)
   :y (aget (:ys state) i)
   :r (aget (:rs state) i)
   :g (aget (:gs state) i)
   :b (aget (:bs state) i)
   :alpha (aget (:alphas state) i)
   :size (aget (:sizes state) i)
   :active? (= 1 (aget (:acts state) i))})

(defn active-count [state]
  (loop [i 0 n 0]
    (if (< i max-particles)
      (recur (inc i) (if (= 1 (aget (:acts state) i)) (inc n) n))
      n)))

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defn- activate!
  "The original's `activate-first-inactive`: the first inactive particle becomes
  active at `[x y]` with alpha 1.0. A full pool changes nothing."
  [state x y]
  (let [#?@(:jolt [^double/1 xs (:xs state) ^double/1 ys (:ys state) ^double/1 alphas (:alphas state)
                   ^int/1 acts (:acts state)]
            :default [^"[D" xs (:xs state) ^"[D" ys (:ys state) ^"[D" alphas (:alphas state)
                      ^"[I" acts (:acts state)])]
    (loop [i 0]
      (when (< i max-particles)
        (if (= 1 (aget acts i))
          (recur (inc i))
          (do (aset-int acts i 1)
              (aset-double alphas i 1.0)
              (aset-double xs i (double x))
              (aset-double ys i (double y))))))))

(defn- age!
  "The original's `update-particle` for the whole pool, in place."
  [state]
  (let [#?@(:jolt [^double/1 ys (:ys state) ^double/1 alphas (:alphas state) ^int/1 acts (:acts state)]
            :default [^"[D" ys (:ys state) ^"[D" alphas (:alphas state) ^"[I" acts (:acts state)])
        fall (/ gravity 2.0)]
    (loop [i 0]
      (when (< i max-particles)
        (when (= 1 (aget acts i))
          (let [alpha (- (aget alphas i) fade)]
            (if (<= alpha 0.0)
              (aset-int acts i 0)
              (do (aset-double ys i (+ (aget ys i) fall))
                  (aset-double alphas i alpha)))))
        (recur (inc i))))))

(defn emit-particles!
  "Call `(circle! cx cy radius r g b a)` for each active particle, in pixels, in
  pool order: centred on the particle, of diameter `spark-size` times its size,
  `a` being `int(255 * alpha)`. `dims` is `geometry`'s or `dimensions'`."
  [circle! state {:keys [u top]}]
  (let [#?@(:jolt [^double/1 xs (:xs state) ^double/1 ys (:ys state) ^double/1 alphas (:alphas state)
                   ^double/1 sizes (:sizes state) ^int/1 acts (:acts state)
                   ^int/1 rs (:rs state) ^int/1 gs (:gs state) ^int/1 bs (:bs state)]
            :default [^"[D" xs (:xs state) ^"[D" ys (:ys state) ^"[D" alphas (:alphas state)
                      ^"[D" sizes (:sizes state) ^"[I" acts (:acts state)
                      ^"[I" rs (:rs state) ^"[I" gs (:gs state) ^"[I" bs (:bs state)])
        u (double u)
        top (double top)
        half (* 0.5 spark-size u)]
    (loop [i 0]
      (when (< i max-particles)
        (when (= 1 (aget acts i))
          (circle! (* u (aget xs i)) (+ top (* u (aget ys i))) (* half (aget sizes i))
                   (aget rs i) (aget gs i) (aget bs i) (int (* 255 (aget alphas i)))))
        (recur (inc i))))))

(defn call-blended!
  "Run `(body!)` with `mode` begun, calling `(end!)` afterwards on every path,
  a throw included. `begin!` is inside the `try` too, so a begin that fails
  half way still ends: ending the default mode is harmless."
  [begin! end! mode body!]
  (try
    (begin! mode)
    (body!)
    (finally
      (end!))))

(defn- fallback-measure
  "estimate: 0.6 of the size per character, for a caller whose input has no
  `:measure`, which the gallery's always does."
  [s size]
  (* 0.6 size (count s)))

(defn advance
  "One frame, the original's loop. A tap that began on the button flips the
  blending. A finger down in the field, over neither Back nor the button and
  not begun on either, activates the first inactive particle at that point.
  Then every particle ages. The button is laid out with the input's `:measure`
  and kept in the state for its screen. When the metrics report a different
  `:screen` the gesture is reset, since its start was in the old screen's
  pixels."
  [state {:keys [metrics pointer]
          :as input}]
  (let [{:keys [u top h w]} (geometry metrics)
        turned? (not= (:screen state) (:screen metrics))
        {:keys [button]} (if turned?
                           (dimensions metrics (or (:measure input) fallback-measure))
                           (:dims state))
        [g event] (gesture/track (if turned? gesture/idle (:gesture state)) input)
        pos (:position pointer)
        start (:start g)
        field? (fn [[px py]] (and (>= px 0) (< px w) (>= py top) (< py h)))
        toggle? (and (= :tap (:type event)) (gesture/in-rect? button (:at event)))
        emit? (and (gesture/down? input)
                   start
                   (field? pos)
                   (not (gesture/in-rect? button pos))
                   (not (gesture/in-back-region? start))
                   (not (gesture/in-rect? button start)))]
    (when emit?
      (activate! state (/ (double (first pos)) u) (/ (- (double (second pos)) top) u)))
    (age! state)
    (assoc state
           :blending (if toggle? (- 1 (:blending state)) (:blending state))
           :gesture g
           :dims (if turned? {:button button} (:dims state))
           :screen (:screen metrics))))

(defn- init [{:keys [metrics measure]}]
  (let [state {:dims {:button (:button (dimensions metrics (or measure fallback-measure)))}
               :xs (double-array max-particles)
               :ys (double-array max-particles)
               :alphas (double-array max-particles 1.0)
               :sizes (double-array max-particles)
               :acts (int-array max-particles)
               :rs (int-array max-particles)
               :gs (int-array max-particles)
               :bs (int-array max-particles)
               :blending 0
               :screen (:screen metrics)
               :gesture gesture/idle}]
    (loop [i 0
           s (long seed)]
      (if (< i max-particles)
        (let [s1 (next-random s)
              s2 (next-random s1)
              s3 (next-random s2)
              s4 (next-random s3)]
          (aset-int (:rs state) i (int (roll s1 0 255)))
          (aset-int (:gs state) i (int (roll s2 0 255)))
          (aset-int (:bs state) i (int (roll s3 0 255)))
          (aset-double (:sizes state) i (/ (roll s4 1 30) 20.0))
          (recur (inc i) s4))
        [state [[:scene/init :blendparticles]]]))))

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :blendparticles]]])

(defn scene []
  {:id :blendparticles
   :title "Particles Blending"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
