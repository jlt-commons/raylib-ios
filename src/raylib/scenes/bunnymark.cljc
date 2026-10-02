(ns raylib.scenes.bunnymark
  "Bunnymark, ported from raylib-jlt's `bunnymark`
  (net/b12n/raylib_jlt/bunnymark.clj, zlib licence), the traditional sprite-count
  benchmark: hold to spawn bunnies that bounce off the edges, and watch the frame
  rate fall as they pile up.

  Each bunny is a tinted square of the original sprite's size. The original draws
  a 32 by 32 texture with `texture!`, whose pixels it builds by hand (lines
  22-37); there is no texture here, so the rabbit's silhouette is dropped and
  only its tint is kept. A count and the sprite's size are what the benchmark
  measures.

  What is the original's:
  - `spawn` (lines 39-52): x and y at the touch point, vx and vy
    `GetRandomValue(-250, 250) / 60`, and each colour channel
    `GetRandomValue(90, 255)`, drawn in the order x y vx vy r g b. The opening
    spawn is `:scattered`, with x in 0 to 768 (`W - SPRITE`) and y in 0 to
    `H - SPRITE`. `GetRandomValue` is the project's LCG seeded with 20261002,
    taking its high bits, so every run opens the same.
  - `step` (lines 54-61): move by the velocity, then flip the velocity of an axis
    whose sprite is outside the window. The position is not clamped, so a bunny
    that lands outside stays there until its next step brings it back.
  - The main loop (lines 63-100): 200 bunnies to start, BATCH of 60 added per
    frame while the button is held and the count is under MAX-BUNNIES of 40000
    (so the count can end up to 59 past it: 200 + 60 k first reaches 40000 at
    40040), then every bunny is stepped, including the new ones.
  - The count text and the fps readout (lines 108-111). The fps is the host's own
    `GetFPS`, read every frame by the gallery as `starfield` and `deltatime` do.

  Controls: holding a finger anywhere outside Back and outside the clear button
  spawns at that point, in place of the left mouse button. A tap on the \"clear\"
  button empties the field, in place of SPACE. The tap is by `raylib.gesture`, so
  it is where the finger started. As in the original, clearing wins over
  spawning in a frame.

  Units. The original's window is 800 by 450. Here the bunnies move in its units
  (the physics above is the original's, unchanged), but the window is 800 wide
  and `(h - top) / u` tall, where `u` is the safe region's width over 800 and
  `top` is the bottom of Back, so a bunny is square on a phone and a portrait
  field is taller than 450 units. A tall field takes longer to cross, because
  the velocities are the original's per frame. The bar with the text sits over
  the top of the field, as the original's header does, and a bunny goes under it.

  The state holds numbers only, in mutable arrays: `:xs :ys :vxs :vys` (doubles)
  and `:rs :gs :bs` (ints) with `:n` bunnies live, a capacity of `capacity`, the
  LCG `:seed` and the `:gesture`. `advance` updates the arrays IN PLACE and
  returns the state with a new `:n`, so a hot loop over forty thousand bunnies
  allocates nothing per bunny; a state is not a value to keep across `advance`.
  `bunny` and `set-bunny!` are the allocating accessors for tests."
  (:require [raylib.gesture :as gesture]))

(def view-w "The original's window width, in its units." 800.0)
(def sprite "The original's SPRITE." 32.0)
(def batch "Bunnies added a frame while held. The original's BATCH." 60)
(def max-bunnies "The original's MAX-BUNNIES." 40000)
(def start-count "The opening spawn. The original's." 200)
(def capacity "Room for the cap and the batch that can cross it." (+ max-bunnies batch))
(def seed "The LCG's start." 20261002)

(def background-colour "RAYWHITE." [245 245 245 255])
(def bar-colour "The original's header: (20, 24, 34, 220)." [20 24 34 220])
(def text-colour "RAYWHITE." [245 245 245 255])
(def hint-colour "LIGHTGRAY." [200 200 200 255])
(def button-colour [130 130 130 255])
(def button-label-colour [245 245 245 255])

(def hint "The original's hint, with a tap for SPACE." "hold to add - tap clear")
(def button-label "clear")

(defn count-line [n] (str n " bunnies"))
(defn fps-line [fps] (str fps " fps"))

(defn geometry
  "The layout for `metrics`' `:screen`, without text: `:w :h`, `:u` (pixels per
  original unit), `:top` (the bottom of Back, where the field begins), `:field-h`
  (the field's height in original units), `:side` (the sprite in pixels), the
  text `:size`, `:pad` and `:row`, `:bar` as `[x y w h]` and `:button` as `[x y w
  h]` inside it."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (double (+ back-y back-h))
        u (/ (double w) view-w)
        side (min w h)
        size (max 16 (int (* 0.03 side)))
        pad (max 8 (int (* 0.5 size)))
        row (int (* 1.3 size))
        bw (* 0.26 w)]
    {:w w
     :h h
     :u u
     :top top
     :field-h (/ (- h top) u)
     :side (int (* u sprite))
     :size size
     :pad pad
     :row row
     :bar [0.0 top (double w) (double (+ (* 3 row) (* 2 pad)))]
     :button [(- w pad bw) (+ top pad) bw (double (+ row size))]}))

(defn dimensions
  "`geometry` plus the text. `:lines` are the count, the fps and the hint as `{:s
  :x :y :size}`: the first two share one size, cut back until the widest each can
  get fits left of the button, and the hint is cut back to fit the width. The
  button gains `:label`, `:label-x`, `:label-y` and `:label-size`. `measure` is
  `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w pad row size top]
         [bx by bw bh] :button
         :as geo} (geometry metrics)
        fit (fn [s room] (max 8 (min size (int (/ (* room 100.0) (measure s 100))))))
        left (- bx (* 2 pad))
        size1 (min (fit (count-line (+ max-bunnies batch)) left) (fit (fps-line 999) left))
        size2 (fit hint (- w (* 2 pad)))
        y1 (int (+ top pad))
        label-size (max 8 (min size (int bh) (int (/ (* 0.8 bw 100.0) (measure button-label 100)))))]
    (assoc geo
           :lines [{:s (count-line 0)
                    :x pad
                    :y y1
                    :size size1}
                   {:s (fps-line 0)
                    :x pad
                    :y (+ y1 row)
                    :size size1}
                   {:s hint
                    :x pad
                    :y (+ y1 row row)
                    :size size2}]
           :label button-label
           :label-size label-size
           :label-x (int (+ bx (* 0.5 (- bw (measure button-label label-size)))))
           :label-y (int (+ by (* 0.5 (- bh label-size)))))))

(defn- next-random [s]
  (mod (+ (* 1103515245 (long s)) 12345) 2147483648))

(defn random-value
  "`[v seed']`: an int in [lo, hi] from the LCG's high bits."
  [s lo hi]
  (let [s' (next-random s)]
    [(+ lo (mod (quot s' 65536) (inc (- hi lo)))) s']))

;; kondo reads this .cljc as ClojureScript too, which has no aset-double.
#_{:clj-kondo/ignore [:unresolved-symbol]}
(defn set-bunny!
  "Write bunny `i` from a map of `:x :y :vx :vy :r :g :b`. For tests."
  [state i {:keys [x y vx vy r g b]}]
  (aset-double (:xs state) i (double x))
  (aset-double (:ys state) i (double y))
  (aset-double (:vxs state) i (double vx))
  (aset-double (:vys state) i (double vy))
  (aset-int (:rs state) i (int r))
  (aset-int (:gs state) i (int g))
  (aset-int (:bs state) i (int b)))

(defn bunny
  "Bunny `i` as a map of `:x :y :vx :vy :r :g :b`. Allocates; for tests."
  [state i]
  {:x (aget (:xs state) i)
   :y (aget (:ys state) i)
   :vx (aget (:vxs state) i)
   :vy (aget (:vys state) i)
   :r (aget (:rs state) i)
   :g (aget (:gs state) i)
   :b (aget (:bs state) i)})

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defn- spawn!
  "Write `cnt` bunnies at index `from` on, at `[mx my]` or, when `mx` is nil,
  scattered over the window as the original's opening is. Draws in the
  original's order x y vx vy r g b. Returns the LCG seed afterwards."
  [state from cnt mx my field-h]
  (let [#?@(:jolt [^double/1 xs (:xs state) ^double/1 ys (:ys state)
                   ^double/1 vxs (:vxs state) ^double/1 vys (:vys state)
                   ^int/1 rs (:rs state) ^int/1 gs (:gs state) ^int/1 bs (:bs state)]
            :default [^"[D" xs (:xs state) ^"[D" ys (:ys state)
                      ^"[D" vxs (:vxs state) ^"[D" vys (:vys state)
                      ^"[I" rs (:rs state) ^"[I" gs (:gs state) ^"[I" bs (:bs state)])
        max-x (int (- view-w sprite))
        max-y (int (- field-h sprite))]
    (loop [i from
           s (long (:seed state))]
      (if (< i (+ from cnt))
        (let [[x s] (if mx [mx s] (random-value s 0 max-x))
              [y s] (if mx [my s] (random-value s 0 max-y))
              [vx s] (random-value s -250 250)
              [vy s] (random-value s -250 250)
              [r s] (random-value s 90 255)
              [g s] (random-value s 90 255)
              [b s] (random-value s 90 255)]
          (aset-double xs i (double x))
          (aset-double ys i (double y))
          (aset-double vxs i (/ vx 60.0))
          (aset-double vys i (/ vy 60.0))
          (aset-int rs i (int r))
          (aset-int gs i (int g))
          (aset-int bs i (int b))
          (recur (inc i) s))
        s))))

#_{:clj-kondo/ignore [:unresolved-symbol]}
(defn- step!
  "The original's `step` for the first `n` bunnies, in place."
  [state n field-h]
  (let [#?@(:jolt [^double/1 xs (:xs state) ^double/1 ys (:ys state)
                   ^double/1 vxs (:vxs state) ^double/1 vys (:vys state)]
            :default [^"[D" xs (:xs state) ^"[D" ys (:ys state)
                      ^"[D" vxs (:vxs state) ^"[D" vys (:vys state)])
        field-h (double field-h)]
    (loop [i 0]
      (when (< i n)
        (let [vx (aget vxs i)
              vy (aget vys i)
              x (+ (aget xs i) vx)
              y (+ (aget ys i) vy)]
          (aset-double xs i x)
          (aset-double ys i y)
          (when (or (< x 0.0) (> (+ x sprite) view-w))
            (aset-double vxs i (- vx)))
          (when (or (< y 0.0) (> (+ y sprite) field-h))
            (aset-double vys i (- vy))))
        (recur (inc i))))))

(defn emit-bunnies!
  "Call `(draw-rect! x y side r g b)` for each live bunny, in pixels, in index
  order. `dims` is `geometry`'s or `dimensions`'. Nothing is allocated per bunny."
  [draw-rect! state {:keys [u top side]}]
  (let [#?@(:jolt [^double/1 xs (:xs state) ^double/1 ys (:ys state)
                   ^int/1 rs (:rs state) ^int/1 gs (:gs state) ^int/1 bs (:bs state)]
            :default [^"[D" xs (:xs state) ^"[D" ys (:ys state)
                      ^"[I" rs (:rs state) ^"[I" gs (:gs state) ^"[I" bs (:bs state)])
        n (long (:n state))
        u (double u)
        top (double top)]
    (loop [i 0]
      (when (< i n)
        (draw-rect! (int (* u (aget xs i))) (int (+ top (* u (aget ys i)))) side
                    (aget rs i) (aget gs i) (aget bs i))
        (recur (inc i))))))

(defn advance
  "One frame, the original's loop. A tap that began on the clear button empties
  the field; otherwise a finger down outside Back and the button adds a batch
  when the count is under the cap. Then every bunny steps."
  [state {:keys [metrics pointer]
          :as input}]
  (let [{:keys [u top field-h]
         [bx by bw bh] :button} (geometry metrics)
        [g event] (gesture/track (:gesture state) input)
        pos (:position pointer)
        in-button? (and pos (gesture/in-rect? [bx by bw bh] pos))
        clear? (and (= :tap (:type event)) (gesture/in-rect? [bx by bw bh] (:at event)))
        n (:n state)
        add? (and (gesture/down? input)
                  (not (gesture/in-back-region? pos))
                  (not in-button?)
                  (< n max-bunnies))
        [n s] (cond
                clear? [0 (:seed state)]
                add? [(+ n batch)
                      (spawn! state n batch (/ (double (first pos)) u) (/ (- (double (second pos)) top) u) field-h)]
                :else [n (:seed state)])]
    (step! state n field-h)
    (assoc state :n n :seed s :gesture g)))

(defn- init [{:keys [metrics]}]
  (let [{:keys [field-h]} (geometry metrics)
        state {:n 0
               :xs (double-array capacity)
               :ys (double-array capacity)
               :vxs (double-array capacity)
               :vys (double-array capacity)
               :rs (int-array capacity)
               :gs (int-array capacity)
               :bs (int-array capacity)
               :seed seed
               :gesture gesture/idle}
        s (spawn! state 0 start-count nil nil field-h)]
    [(assoc state :n start-count :seed s) [[:scene/init :bunnymark]]]))

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :bunnymark]]])

(defn scene []
  {:id :bunnymark
   :title "Bunnymark"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
