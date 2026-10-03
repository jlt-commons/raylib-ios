(ns raylib.scenes.vpscaling
  "A game drawn at a fixed resolution and scaled into a window under six
  viewport policies. Ported from raylib-jlt's `viewport-scaling`, which is
  raylib's `core_viewport_scaling` example (zlib licence).

  The original renders the game into a render texture of the game's size, in a
  resizable 800 by 450 window, and blits it to a destination rectangle that
  depends on the window and the policy. Two pairs of < > buttons step the game
  resolution (64x64, 256x240, 320x180, 3840x2160) and the policy (KEEP_ASPECT_INTEGER,
  KEEP_HEIGHT_INTEGER, KEEP_WIDTH_INTEGER, KEEP_ASPECT, KEEP_HEIGHT, KEEP_WIDTH). The
  game is a white clear with a LIME circle of radius 20 at the mouse, mapped back
  into game pixels. Six readouts report the window, the game, the policy, the
  scale ratio, the source size and the destination size.

  `rects` is the original's `compute-rects`, line for line: `keep-aspect-rects`
  (integer or not), `keep-height-rects` and `keep-width-rects`, with the original's
  quirks kept. The two height policies are one function and so are the two width
  policies, so the INTEGER variants of those do not snap: only KEEP_ASPECT_INTEGER
  does, with `quot`. `plan` and `game-point` use the same maths the original uses
  for the blit and for the mouse: the destination is `dx dy dw dh`, and a mouse
  at `mx my` in the window is `(mx - dx) * sw / dw` and `(my - dy) * sw / dw`
  (the original's `ratio-x` is used for both axes).

  Here the window is a virtual window inside the field, and its size is the
  window size the maths run on. Its top-left corner is fixed, a square handle in
  its bottom-right corner resizes it (see `advance`), and it is clamped between
  `:min-win` and `:max-win` from `geometry`. It starts at the original's 800 by
  450 shape, at 80 percent of the largest window the field allows in whichever
  direction runs out first, so the handle has room to grow it both ways. The
  render texture becomes a scissor on the destination rectangle and a push,
  translate and scale in the draw method (the destination's corner, then
  destination over source on each axis), which clips to the same edges the
  texture has.

  Controls here, in place of the original's window and mouse:
  - Dragging the handle resizes the window by exactly the finger's travel, from
    wherever on the handle it was grabbed. A touch
    that starts anywhere else in the screen, except on a button or under Back,
    is the mouse: the circle follows the finger while it is down and stays
    where the finger last was after it lifts, like a mouse that is left alone.
    It starts at the middle of the window. A finger that starts elsewhere and
    slides onto the handle does not resize.
  - The four < > buttons are taps (`raylib.gesture`) that began on them. < from
    the first wraps to the last. The default font has no arrow glyphs for the
    host to rely on, so the labels are the ASCII < and >.
  - The original's window readout is the virtual window's size, and every
    readout is one size, cut back until the widest it can be fits the width.

  Differences from the original, all disclosed in the catalog row:
  - The original draws its panel over the window, at (5, 5), with the two button
    pairs inside it. Here the six readouts are above the field and the two
    pairs of buttons flank the game and policy lines, so the readouts are in a
    different order from the original's (the game and policy lines first), and
    the panel's box is dropped.
  - A policy that gives an empty destination (an integer policy for a game
    bigger than the window, where the original's `quot` gives a ratio of 0)
    draws nothing and gives no mouse position, and the scale readout says
    INVALID as the original's does under 0.001. The original would make a
    zero-size render texture, which it cannot.
  - `fmt2` is `f2` below, since the project does not use `format`.

  The state holds numbers, keywords and vectors of numbers only: `:win`, `:res`
  and `:vtype`, `:mouse` in field pixels, `:grab` and its offset, `:screen` and
  the `:gesture`. Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]))

(def resolutions
  "The original's RESOLUTIONS."
  [[64 64] [256 240] [320 180] [3840 2160]])

(def type-names
  "The original's TYPE-NAMES, in the order `rects` takes the policy."
  ["KEEP_ASPECT_INTEGER" "KEEP_HEIGHT_INTEGER" "KEEP_WIDTH_INTEGER"
   "KEEP_ASPECT" "KEEP_HEIGHT" "KEEP_WIDTH"])

(def original-window "The original's window, W by H. The starting shape." [800 450])
(def ball-radius "The circle's radius in game pixels. The original's." 20.0)

(def background-colour [40 44 52 255])
(def frame-colour [200 200 200 255])
(def window-colour "BLACK, the original's clear." [0 0 0 255])
(def game-colour "WHITE, the render texture's clear." [255 255 255 255])
(def ball-colour "LIME." [0 158 47 255])
(def text-colour [245 245 245 255])
(def button-colour "SKYBLUE, the original's." [102 191 255 255])
(def button-label-colour "BLACK." [0 0 0 255])
(def handle-colour [102 191 255 255])
(def handle-core-colour [0 121 241 255])

(defn- f2
  "`v`, which is not negative, to two decimals as the original's `%.2f` gives it."
  [v]
  (let [n (long (Math/round (* 100.0 (double v))))
        r (rem n 100)]
    (str (quot n 100) "." (if (< r 10) "0" "") r)))

(defn- keep-aspect-rects
  "The original's `keep-aspect-rects`: letterbox both axes; `integer?` snaps the
  scale ratio to a whole number."
  [integer? sw sh gw gh]
  (let [rr (if integer?
             (double (min (quot sw gw) (quot sh gh)))
             (min (/ (double sw) gw) (/ (double sh) gh)))]
    {:sx 0.0
     :sy (double gh)
     :sw (double gw)
     :sh (- (double gh))
     :dx (double (long (* (- sw (* gw rr)) 0.5)))
     :dy (double (long (* (- sh (* gh rr)) 0.5)))
     :dw (double (long (* gw rr)))
     :dh (double (long (* gh rr)))}))

(defn- keep-height-rects
  "The original's `keep-height-rects`: fill the window height, widen the game."
  [sw sh _gw gh]
  (let [rr (/ (double sh) gh)
        srcw (double (long (/ (double sw) rr)))]
    {:sx 0.0
     :sy 0.0
     :sw srcw
     :sh (- (double gh))
     :dx (double (long (* (- sw (* srcw rr)) 0.5)))
     :dy (double (long (* (- sh (* gh rr)) 0.5)))
     :dw (double (long (* srcw rr)))
     :dh (double (long (* gh rr)))}))

(defn- keep-width-rects
  "The original's `keep-width-rects`: fill the window width, deepen the game."
  [sw sh gw _gh]
  (let [rr (/ (double sw) gw)
        srch (double (long (/ (double sh) rr)))]
    {:sx 0.0
     :sy 0.0
     :sw (double gw)
     :sh (- srch)
     :dx (double (long (* (- sw (* gw rr)) 0.5)))
     :dy (double (long (* (- sh (* srch rr)) 0.5)))
     :dw (double (long (* gw rr)))
     :dh (double (long (* srch rr)))}))

(defn rects
  "The original's `compute-rects`: the source rectangle `:sx :sy :sw :sh` (`:sh`
  negative, the render texture's flip) and the destination `:dx :dy :dw :dh` of a
  `gw` by `gh` game in a `sw` by `sh` window under policy `vtype` (0 to 5), all
  as doubles."
  [vtype sw sh gw gh]
  (case (long vtype)
    0 (keep-aspect-rects true sw sh gw gh)
    1 (keep-height-rects sw sh gw gh)
    2 (keep-width-rects sw sh gw gh)
    3 (keep-aspect-rects false sw sh gw gh)
    4 (keep-height-rects sw sh gw gh)
    (keep-width-rects sw sh gw gh)))

(defn geometry
  "The layout for `metrics`' `:screen`, with no text. Below Back there is a row of
  two square buttons for the game resolution (`:res-prev` and `:res-next`, each
  `[x y w h]`) and one for the policy (`:type-prev`, `:type-next`), then four text
  rows, then `:field` `[x y w h]`, the full width to the bottom. `:origin` is the
  virtual window's fixed top-left corner, a pad into the field, `:max-win` the
  largest window the field leaves room for with a pad around it, `:min-win` the
  smallest, two handles each way, and `:start-win` the original's 800 by 450 shape at
  80 percent of what fits. All three are whole numbers `[w h]`. `:handle-size` is
  the side of the resizing square in the window's bottom-right corner. `:size`,
  `:pad` and `:row` are the text metrics."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        back-bottom (+ back-y back-h)
        size (max 16 (int (* 0.03 (min w h))))
        pad (max 8 (int (* 0.5 size)))
        row (int (* 1.3 size))
        bh (* 2.4 size)
        res-y (+ back-bottom pad)
        type-y (+ res-y bh pad)
        text-y (+ type-y bh pad)
        ftop (+ text-y (* 4 row) pad)
        fh (- h ftop)
        hs (Math/ceil (max 48.0 (* 0.07 (min w h))))
        max-w (long (- w (* 2 pad)))
        max-h (long (- fh (* 2 pad)))
        min-w (min (long (* 2 hs)) max-w)
        min-h (min (long (* 2 hs)) max-h)
        [ow oh] original-window
        k (min (/ (* 0.8 max-w) ow) (/ (* 0.8 max-h) oh))
        start-w (max min-w (min max-w (long (* k ow))))
        start-h (max min-h (min max-h (long (* k oh))))
        bx-next (- w pad bh)]
    {:w w
     :h h
     :size size
     :pad pad
     :row row
     :bh bh
     :text-y text-y
     :res-prev [(double pad) (double res-y) bh bh]
     :res-next [(double bx-next) (double res-y) bh bh]
     :type-prev [(double pad) (double type-y) bh bh]
     :type-next [(double bx-next) (double type-y) bh bh]
     :field [0.0 (double ftop) (double w) (double fh)]
     :origin [(double pad) (+ ftop pad)]
     :max-win [max-w max-h]
     :min-win [min-w min-h]
     :start-win [start-w start-h]
     :handle-size hs}))

(defn dimensions
  "`geometry` plus the text. `:slots` are the six readouts' places in the
  original's order (window, game, policy, scale, source, destination) as
  `{:x :y :size}`: the game and policy lines sit between their buttons, and the
  rest in the rows under them. All are one size, cut back until the widest each
  can be fits its room. `:arrows` maps each button key to its `<` or `>` as
  `{:s :x :y :size}`, centred. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [size pad row text-y w h bh]
         [_ ry] :res-prev
         [_ ty] :type-prev
         :as geo} (geometry metrics)
        row-room (- w (* 4 pad) (* 2 bh))
        line-room (- w (* 2 pad))
        fit (fn [s room] (max 8 (min size (int (/ (* room 100.0) (measure s 100))))))
        row-strings (cons "Game Resolution: 3840 x 2160" (map #(str "Type: " %) type-names))
        line-strings [(str "Window Resolution: " w " x " h)
                      "Scale ratio: 99.99 x 99.99"
                      "Scale ratio: INVALID"
                      "Source size: 99999.00 x 99999.00"
                      (str "Destination size: " w ".00 x " h ".00")]
        text-size (reduce min size (concat (map #(fit % row-room) row-strings)
                                           (map #(fit % line-room) line-strings)))
        line-x (+ pad bh pad)
        row-y (fn [by] (int (+ by (* 0.5 (- bh text-size)))))
        line (fn [i] {:x pad
                      :y (+ text-y (* i row))
                      :size text-size})
        arrow (fn [s [bx by]]
                {:s s
                 :x (int (+ bx (* 0.5 (- bh (measure s text-size)))))
                 :y (row-y by)
                 :size text-size})]
    (assoc geo
           :text-size text-size
           :slots [(line 0)
                   {:x line-x
                    :y (row-y ry)
                    :size text-size}
                   {:x line-x
                    :y (row-y ty)
                    :size text-size}
                   (line 1)
                   (line 2)
                   (line 3)]
           :arrows {:res-prev (arrow "<" (:res-prev geo))
                    :res-next (arrow ">" (:res-next geo))
                    :type-prev (arrow "<" (:type-prev geo))
                    :type-next (arrow ">" (:type-next geo))})))

(defn window
  "The virtual window `[x y w h]` of `state`, in scene pixels."
  [state {[ox oy] :origin}]
  (let [[w h] (:win state)]
    [ox oy (double w) (double h)]))

(defn handle
  "The resizing square `[x y w h]`, inside the window's bottom-right corner."
  [state {:keys [handle-size]
          :as dims}]
  (let [[x y w h] (window state dims)]
    [(- (+ x w) handle-size) (- (+ y h) handle-size) handle-size handle-size]))

(defn game-point
  "Where the point `[px py]` (scene pixels) is in game pixels, as the original
  maps the mouse: the point in the window less the destination's corner, times
  source width over destination width, on both axes. nil when the destination is
  empty, which the original cannot show."
  [state {[ox oy] :origin} [px py]]
  (let [[w h] (:win state)
        [gw gh] (nth resolutions (:res state))
        {:keys [sw dx dy dw]} (rects (:vtype state) w h gw gh)]
    (when (pos? dw)
      (let [ratio-x (/ sw dw)]
        [(* (- (- (double px) ox) dx) ratio-x)
         (* (- (- (double py) oy) dy) ratio-x)]))))

(defn plan
  "What to draw for `state`. `:rects` is `rects` for the window and game. `:dest`
  is the destination `[x y w h]` in scene pixels, nil when it is empty, `:source`
  the game's `[w h]`, and `:scale` the factor from game to destination pixels on
  each axis (the original's `scale-x` and `scale-y`). `:circle` is the mouse in
  game pixels, truncated as the original's `(int ...)` does, or nil with no
  picture."
  [state {[ox oy] :origin
          :as dims}]
  (let [[w h] (:win state)
        [gw gh] (nth resolutions (:res state))
        {:keys [dx dy dw dh]
         :as r} (rects (:vtype state) w h gw gh)
        src-w (:sw r)
        src-h (- (:sh r))
        no-picture? (or (<= dw 0.0) (<= dh 0.0) (<= src-w 0.0) (<= src-h 0.0))
        pt (when-not no-picture? (game-point state dims (:mouse state)))]
    {:rects r
     :source [src-w src-h]
     :dest (when-not no-picture? [(+ ox dx) (+ oy dy) dw dh])
     :scale (if no-picture? [0.0 0.0] [(/ dw src-w) (/ dh src-h)])
     :circle (when pt [(long (first pt)) (long (second pt))])}))

(defn readouts
  "The six readouts of `state` as `{:s :x :y :size}`, in the original's order:
  window, game, policy, scale ratio, source size, destination size. The scale
  ratio is INVALID under 0.001 on either axis, as the original's is."
  [state {:keys [slots]
          :as dims}]
  (let [[w h] (:win state)
        [gw gh] (nth resolutions (:res state))
        {[src-w src-h] :source
         [sx sy] :scale
         [_ _ dw dh] :dest} (plan state dims)
        r (rects (:vtype state) w h gw gh)
        strings [(str "Window Resolution: " w " x " h)
                 (str "Game Resolution: " gw " x " gh)
                 (str "Type: " (nth type-names (:vtype state)))
                 (if (or (< (/ (:dw r) (:sw r)) 0.001) (< (/ (:dh r) (- (:sh r))) 0.001))
                   "Scale ratio: INVALID"
                   (str "Scale ratio: " (f2 sx) " x " (f2 sy)))
                 (str "Source size: " (f2 src-w) " x " (f2 src-h))
                 (str "Destination size: " (f2 (or dw (:dw r))) " x " (f2 (or dh (:dh r))))]]
    (mapv (fn [s slot] (assoc slot :s s)) strings slots)))

(defn- on-button?
  "Whether `p` is on one of the four buttons of `dims`."
  [dims p]
  (boolean (some #(gesture/in-rect? (% dims) p) [:res-prev :res-next :type-prev :type-next])))

(defn- clamp [lo hi v] (max lo (min hi v)))

(defn- fresh
  "`state` with the window, the circle, the drag and the gesture back at their
  start for `metrics`. A rotation of the phone does this, since the old window
  was sized for the old field."
  [state metrics]
  (let [dims (geometry metrics)
        [ow oh] (:start-win dims)
        [ox oy] (:origin dims)]
    (assoc state
           :screen (:screen metrics)
           :win [ow oh]
           :mouse [(+ ox (* 0.5 ow)) (+ oy (* 0.5 oh))]
           :grab nil
           :grab-off [0.0 0.0]
           :gesture gesture/idle)))

(defn advance
  "One frame. A tap that began on a button steps the game resolution or the
  policy, wrapping. A press on the handle starts a drag, which keeps the offset
  from the finger to the window's bottom-right corner and sets the window's
  size from each `:down` after, clamped to `:min-win` and `:max-win`. A press
  anywhere else, except on a button or under Back, makes the finger the mouse
  while it stays down. A release or an idle ends either. A rotation of the phone
  starts afresh. The release position is never read."
  [state {:keys [metrics pointer]
          :as input}]
  (let [dims (geometry metrics)
        state (if (= (:screen metrics) (:screen state)) state (fresh state metrics))
        [g event] (gesture/track (:gesture state) input)
        at (when (= :tap (:type event)) (:at event))
        {:keys [phase position]} pointer
        [ox oy] (:origin dims)
        [mw mh] (:max-win dims)
        [nw nh] (:min-win dims)
        hit (fn [k] (and at (gesture/in-rect? (k dims) at)))
        state (cond-> (assoc state :gesture g)
                (hit :res-prev) (update :res #(mod (+ % 3) (count resolutions)))
                (hit :res-next) (update :res #(mod (inc %) (count resolutions)))
                (hit :type-prev) (update :vtype #(mod (+ % 5) (count type-names)))
                (hit :type-next) (update :vtype #(mod (inc %) (count type-names))))
        live? (and position (not (gesture/in-back-region? position)))
        [px py] position]
    (case phase
      :press (cond
               (not live?) (assoc state :grab nil)
               (on-button? dims position) (assoc state :grab nil)
               (gesture/in-rect? (handle state dims) position)
               (let [[wx wy ww wh] (window state dims)]
                 (assoc state
                        :grab :corner
                        :grab-off [(- (double px) (+ wx ww)) (- (double py) (+ wy wh))]))
               :else (assoc state
                            :grab :mouse
                            :mouse [px py]))
      :down (case (:grab state)
              :corner (if position
                        (let [[offx offy] (:grab-off state)]
                          (assoc state :win [(clamp nw mw (long (- px offx ox)))
                                             (clamp nh mh (long (- py offy oy)))]))
                        state)
              :mouse (if live? (assoc state :mouse [px py]) state)
              state)
      (assoc state :grab nil))))

(defn- init [{:keys [metrics]}]
  [(fresh {:res 0
           :vtype 0}
          metrics)
   [[:scene/init :vpscaling]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :vpscaling]]])

(defn scene []
  {:id :vpscaling
   :title "Viewport Scaling"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
