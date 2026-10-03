(ns raylib.scenes.letterbox
  "A fixed 480 by 360 picture scaled into a window of any shape, with black bars
  where it does not fill. Ported from raylib-jlt's `window-letterbox`, a raylib
  example of its own (zlib licence).

  The original draws a 480 by 360 picture into a render texture and blits it
  into the window at the largest scale that fits, centred. `fit` is its `fit`:
  the scale is the smaller of window width over 480 and window height over 360
  (the docstring there says \"multiple\", but it is not rounded), with the
  leftover split into an offset each side, and the blit is at the offset cut to
  whole pixels, `(int (* 480 s))` by `(int (* 360 s))`. The mouse is mapped back
  through the same transform, `(int (/ (- mouse offset) s))`, truncating toward
  zero, and the crosshair is drawn only when that lands on the picture,
  `0 <= mx <= 480` and `0 <= my <= 360`. The picture is a clear colour of (24,
  28, 38), two rows of 12 alternating blue blocks of 40 by 12 along the top and
  bottom, a GOLD circle of radius 26 swinging on `240 + 90 sin t` at y 180, two
  lines of text (18 and 13 points) and the RED crosshair of 10 either way. A
  strip across the window's bottom edge reads the window size, the scale and the
  bars, and a second text says whether R made the window resizable.

  Here the window is a virtual window inside the field. Its top-left corner is
  fixed and a square handle in its bottom-right corner resizes it, clamped
  between `:min-win` and `:max-win` of `geometry`. It starts at the original's
  800 by 450 shape, at 80 percent of what the field allows, so the 4:3 picture
  has bars at the sides from the first frame, as the original's own window was
  chosen to give. The render texture becomes a scissor on the blit rectangle and a
  push, translate and scale in the draw method (to the blit's corner, then blit
  over 480 or 360), which clips to the texture's edges.

  Controls here, in place of the mouse:
  - Dragging the handle resizes the window by the finger's travel, from wherever
    on the handle it was grabbed. A touch that starts anywhere else, under Back
    excepted, is the mouse: the crosshair follows the finger while it is down
    and stays where it was after it lifts, like a mouse left alone, though it is
    not drawn when that point is outside the picture. It starts at the middle of
    the window. A finger that starts elsewhere and slides onto the handle does
    not resize.

  Dropped, and disclosed in the catalog row:
  - R, which toggles the window's resizable flag, and the two window-state
    calls behind it. A phone has no such flag, and the virtual window is always
    resizable, so the second text is the original's resizable branch,
    \"resizable - drag a corner\", in GREEN.
  - The readout strip on the window's bottom edge. A window as narrow as the
    smallest one here cannot hold the original's line, so the readout is two lines
    above the field in the text size the screen allows: the same words, the
    window, the scale and the bars, then the resizable text.
  - `format`, which is `f2` here.

  `:t` is the sum of `:delta-seconds`, the original's `get-time`, unclamped. The
  state holds numbers, keywords and vectors of numbers only: `:win`, `:t`,
  `:mouse` in field pixels, `:grab` and its offset, `:screen` and the `:gesture`.
  Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]))

(def virtual-w "The original's VW." 480)
(def virtual-h "The original's VH." 360)
(def original-window "The original's window, W by H. The starting shape." [800 450])

(def background-colour [40 44 52 255])
(def window-colour "BLACK, the original's clear behind the bars." [0 0 0 255])
(def frame-colour [200 200 200 255])
(def picture-colour [24 28 38 255])
(def block-colours "The original's two blues, alternating." [[0 121 241 255] [102 191 255 255]])
(def circle-colour "GOLD." [255 203 0 255])
(def title-colour "RAYWHITE." [245 245 245 255])
(def note-colour "LIGHTGRAY." [200 200 200 255])
(def cross-colour "RED." [230 41 55 255])
(def readout-colour "LIGHTGRAY, the original's." [200 200 200 255])
(def hint-colour "GREEN, the original's resizable branch." [0 228 48 255])
(def handle-colour [102 191 255 255])
(def handle-core-colour [0 121 241 255])

(def hint "The original's text when the window is resizable." "resizable - drag a corner")

(defn- f2
  "`v`, which is not negative, to two decimals as the original's `%.2f` gives it."
  [v]
  (let [n (long (Math/round (* 100.0 (double v))))
        r (rem n 100)]
    (str (quot n 100) "." (if (< r 10) "0" "") r)))

(defn fit
  "The original's `fit` for a `w` by `h` window: `[s offset-x offset-y]`, the
  largest scale that fits 480 by 360 and the centring offsets, all doubles."
  [w h]
  (let [s (min (/ (double w) virtual-w) (/ (double h) virtual-h))]
    [s (/ (- w (* virtual-w s)) 2.0) (/ (- h (* virtual-h s)) 2.0)]))

(defn blit
  "Where the original blits the picture in a `w` by `h` window: `:x :y :w :h`
  in whole pixels, and `:bar-x` and `:bar-y`, the bars the readout reports,
  which are the offsets cut to whole pixels."
  [w h]
  (let [[s ox oy] (fit w h)]
    {:x (long ox)
     :y (long oy)
     :w (long (* virtual-w s))
     :h (long (* virtual-h s))
     :bar-x (long ox)
     :bar-y (long oy)}))

(defn geometry
  "The layout for `metrics`' `:screen`, with no text. Below Back there are two
  text rows, then `:field` `[x y w h]`, the full width to the bottom. `:origin`
  is the virtual window's fixed top-left corner, a pad into the field, `:max-win`
  the largest window the field leaves room for with a pad around it, `:min-win`
  the smallest, two handles each way, and `:start-win` the original's 800 by 450
  shape at 80 percent of what fits. All three are whole numbers `[w h]`.
  `:handle-size` is the side of the resizing square in the window's bottom-right
  corner. `:size`, `:pad` and `:row` are the text metrics."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        back-bottom (+ back-y back-h)
        size (max 16 (int (* 0.03 (min w h))))
        pad (max 8 (int (* 0.5 size)))
        row (int (* 1.3 size))
        text-y (+ back-bottom pad)
        ftop (+ text-y (* 2 row) pad)
        fh (- h ftop)
        hs (Math/ceil (max 48.0 (* 0.07 (min w h))))
        max-w (long (- w (* 2 pad)))
        max-h (long (- fh (* 2 pad)))
        min-w (min (long (* 2 hs)) max-w)
        min-h (min (long (* 2 hs)) max-h)
        [ow oh] original-window
        k (min (/ (* 0.8 max-w) ow) (/ (* 0.8 max-h) oh))
        start-w (max min-w (min max-w (long (* k ow))))
        start-h (max min-h (min max-h (long (* k oh))))]
    {:w w
     :h h
     :size size
     :pad pad
     :row row
     :text-y text-y
     :field [0.0 (double ftop) (double w) (double fh)]
     :origin [(double pad) (+ ftop pad)]
     :max-win [max-w max-h]
     :min-win [min-w min-h]
     :start-win [start-w start-h]
     :handle-size hs}))

(defn dimensions
  "`geometry` plus the text. `:slots` are the two readouts' places as `{:x :y
  :size}`, in the original's order (the window line, then the resizable text),
  one size, cut back until the widest each can be fits the width. `measure` is
  `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [size pad row text-y w h]
         :as geo} (geometry metrics)
        room (- w (* 2 pad))
        fit-size (fn [s] (max 8 (min size (int (/ (* room 100.0) (measure s 100))))))
        text-size (reduce min size (map fit-size
                                        [(str "window " w "x" h "   scale 99.99   bars " w "x" h)
                                         hint]))
        slot (fn [i] {:x pad
                      :y (+ text-y (* i row))
                      :size text-size})]
    (assoc geo
           :text-size text-size
           :slots [(slot 0) (slot 1)])))

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

(defn virtual-point
  "Where the point `[px py]` (scene pixels) is in the 480 by 360 picture, as the
  original maps the mouse: `(int (/ (- point offset) s))`, truncating toward
  zero, so a point just left of the picture is still pixel 0."
  [state {[ox oy] :origin} [px py]]
  (let [[w h] (:win state)
        [s fox foy] (fit w h)]
    [(long (/ (- (- (double px) ox) fox) s))
     (long (/ (- (- (double py) oy) foy) s))]))

(defn plan
  "What to draw for `state`. `:blit` is the picture's rectangle `[x y w h]` in
  scene pixels, `:scale` the factor from picture to blit pixels on each axis, and
  `:cross` the mouse in picture pixels when it is over the picture, else nil.
  `:fit` is the original's `[s ox oy]`."
  [state {[ox oy] :origin
          :as dims}]
  (let [[w h] (:win state)
        {:keys [x y]
         bw :w
         bh :h} (blit w h)
        [mx my :as pt] (virtual-point state dims (:mouse state))]
    {:blit [(+ ox x) (+ oy y) (double bw) (double bh)]
     :scale [(/ (double bw) virtual-w) (/ (double bh) virtual-h)]
     :fit (fit w h)
     :cross (when (and (<= 0 mx virtual-w) (<= 0 my virtual-h)) pt)}))

(defn picture
  "The 480 by 360 picture at `(:t state)` as data: `:clear`, `:blocks` (the 24
  `{:x :y :w :h :colour}`, the top row then the bottom row, as the original's
  loop draws them), `:circle`, `:texts` and `:cross-lines`, four numbers `[x1 y1
  x2 y2]` each for the crosshair at `cross` (`[mx my]`, or nil for none). The
  original's `draw-virtual`."
  [state cross]
  (let [t (double (:t state))
        block (fn [i y]
                {:x (* i 40)
                 :y y
                 :w 40
                 :h 12
                 :colour (nth block-colours (mod i 2))})]
    {:w virtual-w
     :h virtual-h
     :clear picture-colour
     :blocks (vec (mapcat (fn [i] [(block i 0) (block i (- virtual-h 12))]) (range 12)))
     :circle {:x (long (+ (/ virtual-w 2.0) (* 90 (Math/sin t))))
              :y (long (/ virtual-h 2.0))
              :radius 26
              :colour circle-colour}
     :texts [{:s (str virtual-w "x" virtual-h " virtual resolution")
              :x 20
              :y 40
              :size 18
              :colour title-colour}
             {:s "resize the window - this never changes size"
              :x 20
              :y 68
              :size 13
              :colour note-colour}]
     :cross-lines (if-let [[mx my] cross]
                    [[(- mx 10) my (+ mx 10) my] [mx (- my 10) mx (+ my 10)]]
                    [])}))

(defn readouts
  "The two readouts of `state` as `{:s :x :y :size :colour}`: the original's strip
  text, \"window WxH   scale S   bars BXxBY\", and its resizable text."
  [state {:keys [slots]}]
  (let [[w h] (:win state)
        [s] (fit w h)
        {:keys [bar-x bar-y]} (blit w h)]
    [(assoc (first slots)
            :s (str "window " w "x" h "   scale " (f2 s) "   bars " bar-x "x" bar-y)
            :colour readout-colour)
     (assoc (second slots)
            :s hint
            :colour hint-colour)]))

(defn- clamp [lo hi v] (max lo (min hi v)))

(defn- fresh
  "`state` with the window, the crosshair, the drag and the gesture back at their
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
  "One frame. `:t` adds `:delta-seconds`. A press on the handle starts a drag,
  which keeps the offset from the finger to the window's bottom-right corner and
  sets the window's size from each `:down` after, clamped to `:min-win` and
  `:max-win`. A press anywhere else, except under Back, makes the finger the
  mouse while it stays down. A release or an idle ends either. A rotation of the
  phone starts afresh. The release position is never read."
  [state {:keys [metrics pointer delta-seconds]}]
  (let [dims (geometry metrics)
        state (if (= (:screen metrics) (:screen state)) state (fresh state metrics))
        state (assoc state :t (+ (double (:t state)) (double (or delta-seconds 0.0))))
        {:keys [phase position]} pointer
        [ox oy] (:origin dims)
        [mw mh] (:max-win dims)
        [nw nh] (:min-win dims)
        live? (and position (not (gesture/in-back-region? position)))
        [px py] position]
    (case phase
      :press (cond
               (not live?) (assoc state :grab nil)
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
  [(fresh {:t 0.0} metrics)
   [[:scene/init :letterbox]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :letterbox]]])

(defn scene []
  {:id :letterbox
   :title "Window Letterbox"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
