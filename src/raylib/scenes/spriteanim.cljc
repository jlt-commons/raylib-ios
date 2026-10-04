(ns raylib.scenes.spriteanim
  "Sprite Animation, ported from raylib-jlt's `sprite_animation`
  (net/b12n/raylib_jlt/sprite_animation.clj, EPL 2.0), which is raylib's
  `textures_sprite_animation`: one strip of six frames goes to the GPU once, and
  a source rectangle walks along it. Only the texcoords change.
  The raylib C example it follows is zlib licensed, and this is an altered
  version of that too.

  Mirrored from sprite_animation.clj:
  - The strip (lines 37-89): 576 by 120 (lines 26-29), six frames of 96 by 120,
    transparent, with a stick walker painted into each by `ImageDrawCircle`.
    `strip-grid` replays the original's calls through `raylib.texel`: `thick-line`
    (lines 37-48) is fifteen circles along a limb, `draw-pose` (lines 50-78) is
    the legs, torso, arms, head and eye of pose `i`. The calls are the original's
    in the original's order, so overdraw resolves the same way, and the circle
    is `texel/draw-circle`, which keeps raylib 6.0's quirk of coming out a texel
    short on the right and bottom.
  - The rate (lines 31-32, 105-112): the speed runs 1 to 15 and starts at 8. A
    counter reaching `(quot 60 speed)` moves to the next frame and resets.
  - The texcoords (lines 113-114): frame `i` samples `i/6` to `(i+1)/6`.
  - The text and boxes (lines 139-176): the strip's note, \"FRAME SPEED:\" with
    the rate, fifteen boxes of which `speed` are filled RED, outlined MAROON, and
    the strip outlined LIME with the current frame boxed RED.

  Deviations. LEFT and RIGHT are two buttons, SLOWER and FASTER, and a press is
  the key's first frame. The layout is stacked and scaled to the screen, not an
  800 by 450 window: the strip is as wide as fits (its height never over a fifth
  of the screen), then the speed line, the boxes and the buttons, with the
  current frame drawn larger at the bottom, at the original's 128 by 160
  proportion. The window-title text is dropped, since the gallery shows the
  title. The hint reads \"Tap SLOWER or FASTER to change the rate\".

  Entry cost. The first open after launch pauses for about 1.0 s on the phone
  (the largest single frame, measured), while the texture's pixels are computed
  and uploaded. Opening it again takes about one frame, because the filled
  texture buffer is kept.

  The state holds `:counter`, `:current`, `:speed` and `:screen`. Colours are
  `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]
            [raylib.texel :as texel]))

(def frames "The original's FRAMES." 6)
(def frame-w "The original's FRAME-W." 96)
(def frame-h "The original's FRAME-H." 120)
(def sheet-w "The original's SHEET-W." (* frame-w frames))
(def min-speed "The original's MIN-SPEED." 1)
(def max-speed "The original's MAX-SPEED." 15)
(def start-speed "Where the speed starts (line 100)." 8)

(def background-colour "RAYWHITE." [245 245 245 255])
(def strip-outline-colour "LIME." [0 158 47 255])
(def frame-outline-colour "RED." [230 41 55 255])
(def box-fill-colour "RED." [230 41 55 255])
(def box-outline-colour "MAROON." [190 33 55 255])
(def note-colour "GRAY." [130 130 130 255])
(def speed-colour "DARKGRAY." [80 80 80 255])
(def hint-colour "GRAY." [130 130 130 255])
(def button-colour "A button at rest." [200 200 200 255])
(def button-label-colour "DARKGRAY." [80 80 80 255])

(def note-line "The original's note (line 139)." "one texture, six frames: only :u0/:u1 change")
(def hint-line "The original's hint, with buttons for the keys." "Tap SLOWER or FASTER to change the rate")
(def labels {:slower "SLOWER"
             :faster "FASTER"})

;; ---------------------------------------------------------------- the strip

(def ^:private transparent [0 0 0 0])
(def ^:private red "RED." [230 41 55 255])
(def ^:private black "BLACK." [0 0 0 255])

(defn- thick-line
  "The original's `thick-line!` (lines 37-48), over a transient grid: fifteen
  circles of radius `r` along (x1, y1) to (x2, y2), each centre truncated as
  `(int ...)` does."
  [g x1 y1 x2 y2 r c]
  (reduce (fn [g i]
            (let [f (/ (double i) 14)]
              (texel/draw-circle! g
                                  (int (+ x1 (* f (- x2 x1))))
                                  (int (+ y1 (* f (- y2 y1))))
                                  r c)))
          g
          (range 15)))

(defn- draw-pose
  "The original's `draw-pose!` (lines 50-78): pose `i` painted into its frame of
  a transient grid."
  [g i]
  (let [x0 (* i frame-w)
        phase (* 2.0 Math/PI (/ (double i) frames))
        swing (Math/sin phase)
        bob (int (* 3.0 (abs (Math/cos phase))))
        cx (+ x0 48)
        hip (+ 72 bob)
        shoulder (+ 46 bob)]
    (-> g
        (thick-line cx hip (+ cx (int (* 18 swing))) 106 6 [45 60 95 255])
        (thick-line cx hip (- cx (int (* 18 swing))) 106 6 [60 80 125 255])
        (thick-line cx (+ 38 bob) cx hip 9 red)
        (thick-line (- cx 8) shoulder (- cx 8 (int (* 20 swing))) (+ 76 bob) 5 [120 25 35 255])
        (thick-line (+ cx 8) shoulder (+ cx 8 (int (* 20 swing))) (+ 76 bob) 5 [245 130 120 255])
        (texel/draw-circle! cx (+ 22 bob) 15 [235 195 150 255])
        (texel/draw-circle! (+ cx 6) (+ 19 bob) 3 black))))

(defn strip-grid
  "The original's `build-strip!` (lines 80-89) as a `raylib.texel` grid: 576 by
  120, transparent, six poses. Slow, so build it once, not per frame. It paints
  into a transient grid."
  []
  (texel/persistent-grid
   (reduce draw-pose (texel/transient-grid (texel/grid sheet-w frame-h transparent)) (range frames))))

(defn strip-spec
  "The texture for `raylib.texture/id!`: the strip, clamped (576 is not a power
  of two, so GLES2 will not repeat it), unfiltered. The grid is built here, so
  call this once per scene open and keep the result."
  []
  (let [pixel (texel/pixel-of (strip-grid))]
    {:w sheet-w
     :h frame-h
     :wrap :clamp
     :filter :nearest
     :pixel pixel}))

;; ----------------------------------------------------------------- the layout

(defn speed-line [speed] (str "FRAME SPEED: " speed " fps"))

(defn geometry
  "The layout for `metrics`' `:screen`, with no text measured: `:w :h`, `:size
  :pad :row` (the text's), `:k` (the strip's scale), `:strip` (`[x y w h]`),
  `:cells` (the fifteen boxes), `:slower` and `:faster` (the buttons) and
  `:frame` (the larger current frame), all as `[x y w h]`, plus `:note-y`,
  `:speed-y` and `:hint-y`, the text lines' tops."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (double (+ back-y back-h))
        side (min w h)
        size (max 16 (int (* 0.03 side)))
        pad (max 8 (int (* 0.5 size)))
        row (int (* 1.3 size))
        k (min (/ (- w (* 2.0 pad)) sheet-w) (/ (* 0.2 h) frame-h))
        sw (* k sheet-w)
        sh (* k frame-h)
        note-y (+ top pad)
        sy (+ note-y row)
        speed-y (+ sy sh pad)
        cells-y (+ speed-y row)
        cw (/ (- w (* 2.0 pad)) max-speed)
        hint-y (+ cells-y row pad)
        by (+ hint-y row pad)
        bh (* 3 size)
        bw (/ (- w (* 3.0 pad)) 2.0)
        fy (+ by bh pad)
        avail (- h fy pad)
        fh0 (min avail (* 0.4 h))
        fw0 (* 0.8 fh0)
        fw (min fw0 (- w (* 2.0 pad)))
        fh (/ fw 0.8)]
    {:w w
     :h h
     :size size
     :pad pad
     :row row
     :k k
     :note-y note-y
     :speed-y speed-y
     :hint-y hint-y
     :strip [(/ (- w sw) 2.0) sy sw sh]
     :cells (mapv (fn [i] [(+ pad (* i cw)) cells-y (- cw 2.0) (double row)])
                  (range max-speed))
     :slower [(double pad) by bw (double bh)]
     :faster [(+ pad bw pad) by bw (double bh)]
     :frame [(/ (- w fw) 2.0) fy fw fh]}))

(defn dimensions
  "`geometry` plus `:lines`, the note, the speed line (at its widest) and the
  hint as `{:s :x :y :size}`, at one size cut back until all fit the width, and
  `:labels`, each button's label placed in it. `measure` is `(fn [s size] ->
  px)`."
  [metrics measure]
  (let [{:keys [w pad size note-y speed-y hint-y slower faster]
         :as geo} (geometry metrics)
        room (- w (* 2 pad))
        fit (fn [s] (max 8 (min size (int (/ (* room 100.0) (measure s 100))))))
        sz (min (fit note-line) (fit (speed-line max-speed)) (fit hint-line))
        place (fn [[bx by bw bh] s]
                {:s s
                 :x (int (+ bx (* 0.5 (- bw (measure s size)))))
                 :y (int (+ by (* 0.5 (- bh size))))
                 :size size})]
    (assoc geo
           :lines [{:s note-line
                    :x pad
                    :y (int note-y)
                    :size sz}
                   {:s (speed-line max-speed)
                    :x pad
                    :y (int speed-y)
                    :size sz}
                   {:s hint-line
                    :x pad
                    :y (int hint-y)
                    :size sz}]
           :labels {:slower (place slower (:slower labels))
                    :faster (place faster (:faster labels))})))

(defn outline-rects
  "The outline of `[x y w h]` as four rectangles, `t` pixels thick."
  [[x y w h] t]
  [[x y w t] [x (- (+ y h) t) w t] [x y t h] [(- (+ x w) t) y t h]])

(defn frame-box
  "The box on the strip around frame `current`, as `[x y w h]`."
  [{:keys [strip k]} current]
  (let [[sx sy _ sh] strip]
    [(+ sx (* current frame-w k)) sy (* frame-w k) sh]))

(defn quad
  "The larger current frame as `raylib.texture/quad!` takes it: the source
  rectangle is the sixth of the strip that `state`'s `:current` picks."
  [state {:keys [frame]}]
  (let [[x y w h] frame
        i (:current state)]
    {:x x
     :y y
     :width w
     :height h
     :u0 (/ (double i) frames)
     :u1 (/ (double (inc i)) frames)}))

(defn strip-quad
  "The whole strip as `raylib.texture/quad!` takes it."
  [{:keys [strip]}]
  (let [[x y w h] strip]
    {:x x
     :y y
     :width w
     :height h}))

;; ------------------------------------------------------------------- the state

(defn pressed-button
  "`:slower`, `:faster` or nil: the button a finger lands on this frame. Only the
  `:press` frame counts, as a key's first frame does."
  [geo input]
  (let [{:keys [phase position]} (:pointer input)]
    (when (and (= :press phase) position)
      (cond
        (gesture/in-rect? (:slower geo) position) :slower
        (gesture/in-rect? (:faster geo) position) :faster))))

(defn advance
  "One frame of the original's loop (lines 105-112): the speed moves on a press,
  then the counter ticks and the frame steps when it reaches `(quot 60 speed)`."
  [state {:keys [metrics]
          :as input}]
  (let [pressed (pressed-button (geometry metrics) input)
        speed (cond-> (:speed state)
                (= :faster pressed) inc
                (= :slower pressed) dec)
        speed (-> speed (max min-speed) (min max-speed))
        counter (inc (:counter state))
        tick? (>= counter (quot 60 speed))]
    (assoc state
           :speed speed
           :counter (if tick? 0 counter)
           :current (if tick? (mod (inc (:current state)) frames) (:current state))
           :screen (:screen metrics))))

(defn- init [{:keys [metrics]}]
  [{:counter 0
    :current 0
    :speed start-speed
    :screen (:screen metrics)}
   [[:scene/init :spriteanim]]])

(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :spriteanim]]])

(defn scene []
  {:id :spriteanim
   :title "Sprite Animation"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
