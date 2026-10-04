(ns raylib.scenes.mousepaint
  "Mouse Painting, ported from raylib-jlt's `mouse_painting`
  (net/b12n/raylib_jlt/mouse_painting.clj, EPL 2.0), which is raylib's
  `textures_mouse_painting` (zlib). This is an altered version of both.

  A paint program on a render target that keeps its paint between frames. The
  canvas is drawn into only when a finger paints, and drawn back every frame.

  Mirrored from mouse_painting.clj:
  - The palette (lines 15-24): `palette`, the 23 raylib named colours in the
    original's order, and `color-count` 23. Swatch 0 is RAYWHITE and is both the
    canvas's background and the eraser's colour.
  - The brush (line 54, 62): it starts at 20 and moves in steps of 5 between 2
    and 50, clamped, as the wheel does.
  - Painting (lines 79-84): a filled circle of the brush's radius in the
    selected colour at the pointer, only below the panel. Erasing (lines 63-67)
    paints with swatch 0 instead, and the preview of an eraser is a GRAY circle
    outline (lines 99-107).
  - The clear (lines 76-77): C fills the canvas with swatch 0.
  - The panel (lines 109-141): a strip of swatches with a black outline round
    the selected one, over a LIGHTGRAY rule.

  Inputs. The mouse becomes touch. A finger that lands in the field paints, and
  keeps painting while it stays down and inside the field; a finger that lands
  anywhere else, or slides in from outside, never paints, so a touch already
  down when the scene opens leaves no mark. Four buttons replace the keys and
  the wheel: SIZE - and SIZE + (the wheel), ERASER (the right button, as a
  toggle) and CLEAR (C). A tap on a swatch picks it and also turns the eraser
  off. Each fires on a press and none lies under Back.

  Deviations.
  - **Segments.** The original draws one circle at the pointer a frame, and a
    mouse moves a few pixels in one. A finger moves far more, so a stroke is
    the segment from the last point to this one, drawn as circles spaced half a
    radius apart (`stroke-points`), at most `max-circles` of them. The first
    point of a touch is a single circle, as in the original.
  - **Dropped.** The SAVE button and the S key (a screenshot), its banner, and
    the swatch hover highlight, which a finger has no use for.
  - **Palette.** The swatches wrap to two rows when 23 would be under 70 pixels
    wide, so a thumb can hit them.
  - **A turn clears the canvas.** The canvas is the size of the field, so on a
    rotation it is made again, and made again it is empty: the state emits a
    clear, and the picture is lost. The original's window never turns.
  - **Layout.** The field is the safe region below the panel and buttons, to the
    edges, where the original's canvas is the whole window under its panel.

  The state holds `:marks`, what to draw into the canvas THIS frame and nothing
  older: `[:stroke x0 y0 x1 y1 radius colour]` or `[:clear colour]`, in the
  canvas's own pixels. Each update starts it empty again. Colours are
  `[r g b a]` vectors, so the namespace stays pure."
  (:require [raylib.gesture :as gesture]))

(def color-count "The original's COLOR-COUNT." 23)

(def palette
  "The original's PALETTE as `[r g b a]`: RAYWHITE, YELLOW, GOLD, ORANGE, PINK,
  RED, MAROON, GREEN, LIME, DARKGREEN, SKYBLUE, BLUE, DARKBLUE, PURPLE, VIOLET,
  DARKPURPLE, BEIGE, BROWN, DARKBROWN, LIGHTGRAY, GRAY, DARKGRAY and BLACK."
  (mapv (fn [[r g b]] [r g b 255])
        [[245 245 245] [253 249 0] [255 203 0] [255 161 0] [255 109 194]
         [230 41 55] [190 33 55] [0 228 48] [0 158 47] [0 117 44]
         [102 191 255] [0 121 241] [0 82 172] [200 122 255] [135 60 190]
         [112 31 126] [211 176 131] [127 106 79] [76 63 47]
         [200 200 200] [130 130 130] [80 80 80] [0 0 0]]))

(def brush-start "The original's starting brush radius." 20.0)
(def brush-min "The original's smallest brush radius." 2.0)
(def brush-max "The original's largest brush radius." 50.0)
(def brush-step "One wheel notch, here one button press." 5.0)

(def max-circles
  "The most circles one segment is drawn with. A segment longer than
  `max-circles` half-radii spaces them further apart instead."
  256)

(def background-colour "RAYWHITE: the page, and palette swatch 0." [245 245 245 255])
(def rule-colour "LIGHTGRAY, the original's rule under the panel." [200 200 200 255])
(def outline-colour "BLACK, round the selected swatch." [0 0 0 255])
(def preview-colour "GRAY, the eraser's preview." [130 130 130 255])
(def button-colour "LIGHTGRAY." [200 200 200 255])
(def button-on-colour "GRAY, a button that is switched on." [130 130 130 255])
(def button-label-colour "DARKGRAY." [80 80 80 255])
(def button-on-label-colour "RAYWHITE." [245 245 245 255])

(def button-labels
  "The four buttons, left to right."
  [[:smaller "SIZE -"] [:bigger "SIZE +"] [:eraser "ERASER"] [:clear "CLEAR"]])

(defn clamp-brush
  "A brush radius held to the original's 2 to 50."
  [r]
  (double (max brush-min (min brush-max r))))

(defn layout
  "Where everything sits for `metrics`' `:screen`, with no text measure, which
  is all the touch handling needs: `:palette` (one `{:i :x :y :w :h}` a swatch),
  `:buttons` (`{:id :x :y :w :h}`) and `:field` (`{:x :y :w :h}`, the canvas), in
  whole pixels below `gesture/back-region`."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        margin (int (* 0.02 w))
        gap (max 2 (int (* 0.004 w)))
        cols (if (>= (/ (- w (* 2 margin)) (double color-count)) 70.0) color-count 12)
        rows (quot (+ color-count (dec cols)) cols)
        cell (int (min 110 (quot (- w (* 2 margin)) cols)))
        px (quot (- w (* cols cell)) 2)
        py (+ top gap)
        swatches (mapv (fn [i]
                         {:i i
                          :x (+ px (* cell (rem i cols)))
                          :y (+ py (* cell (quot i cols)))
                          :w (- cell gap)
                          :h (- cell gap)})
                       (range color-count))
        by (+ py (* rows cell) gap)
        bh (max 40 (int (* 0.9 cell)))
        bw (quot (- w (* 2 margin) (* 3 gap)) 4)
        buttons (mapv (fn [k [id _]]
                        {:id id
                         :x (+ margin (* k (+ bw gap)))
                         :y by
                         :w bw
                         :h bh})
                      (range) button-labels)
        fy (+ by bh gap)]
    {:palette swatches
     :buttons buttons
     :field {:x 0
             :y fy
             :w (int w)
             :h (max 1 (int (- h fy)))}}))

(defn dimensions
  "`layout` plus the text: each button gains a `:label` (`{:s :x :y :size}`,
  centred in it, cut back to fit its width) and `:lines` lists them. `measure`
  is `(fn [s size] -> px)`."
  [metrics measure]
  (let [lay (layout metrics)
        fit (fn [size s width]
              (max 8 (min size (int (/ (* width 100.0) (max 1 (measure s 100)))))))
        buttons (mapv (fn [{:keys [x y w h id]
                            :as b}]
                        (let [s (second (first (filter #(= id (first %)) button-labels)))
                              size (fit (int (* 0.4 h)) s (- w 8))
                              tw (measure s size)]
                          (assoc b :label {:s s
                                           :x (+ x (/ (- w tw) 2.0))
                                           :y (+ y (/ (- h size) 2.0))
                                           :size size})))
                      (:buttons lay))]
    (assoc lay
           :buttons buttons
           :lines (mapv :label buttons))))

(defn stroke-points
  "The circle centres of a stroke from `(x0, y0)` to `(x1, y1)` with a brush of
  `radius`: every half radius along the segment, the last at `(x1, y1)` and the
  first one step from `(x0, y0)`, whose circle the previous frame drew. A
  segment of no length is the single point `(x1, y1)`, which is how a touch
  begins. At most `max-circles` of them."
  [x0 y0 x1 y1 radius]
  (let [dx (- (double x1) (double x0))
        dy (- (double y1) (double y0))
        d (Math/sqrt (+ (* dx dx) (* dy dy)))
        step (max 1.0 (* 0.5 (double radius)))
        n (int (max 1 (min max-circles (Math/ceil (/ d step)))))]
    (mapv (fn [i]
            (let [t (/ (double i) n)]
              [(+ (double x0) (* dx t)) (+ (double y0) (* dy t))]))
          (range 1 (inc n)))))

(defn paint-colour
  "The colour the brush lays down: swatch 0 when erasing, else the selected one."
  [{:keys [sel erase?]}]
  (nth palette (if erase? 0 sel)))

(def ^:private clear-mark [:clear (first palette)])

(defn- fresh [screen]
  {:screen screen
   :sel 0
   :erase? false
   :brush brush-start
   :last nil
   :cursor nil
   :marks [clear-mark]})

(defn- hit [lay point]
  (let [in? (fn [{:keys [x y w h]}] (gesture/in-rect? [x y w h] point))]
    (or (when-let [s (first (filter in? (:palette lay)))] [:swatch (:i s)])
        (when-let [b (first (filter in? (:buttons lay)))] [:button (:id b)])
        (when (in? (:field lay)) [:field]))))

(defn- press-button [state id]
  (case id
    :smaller (update state :brush #(clamp-brush (- % brush-step)))
    :bigger (update state :brush #(clamp-brush (+ % brush-step)))
    :eraser (update state :erase? not)
    :clear (update state :marks conj clear-mark)))

(defn- stroke [state lay [px py] [lx ly]]
  (let [{fx :x
         fy :y} (:field lay)
        x0 (- lx fx)
        y0 (- ly fy)
        x1 (- px fx)
        y1 (- py fy)]
    (-> state
        (assoc :last [px py]
               :cursor [px py])
        (update :marks conj [:stroke x0 y0 x1 y1 (:brush state) (paint-colour state)]))))

(defn advance
  "One frame. `:marks` is emptied first, so it holds only what this frame draws.
  A turn (a new `:screen`) clears the canvas and drops the stroke in progress."
  [state input]
  (let [screen (get-in input [:metrics :screen])
        turned? (not= screen (:screen state))
        state (cond-> (assoc state :marks [] :cursor nil)
                turned? (assoc :screen screen :last nil :marks [clear-mark]))
        lay (layout (:metrics input))
        {:keys [phase position]} (:pointer input)
        down? (gesture/down? input)]
    (cond
      (not down?) (assoc state :last nil)

      (= :press phase)
      (if (gesture/in-back-region? position)
        (assoc state :last nil)
        (let [[kind arg] (hit lay position)]
          (case kind
            :swatch (assoc state :sel arg :erase? false :last nil)
            :button (-> state (press-button arg) (assoc :last nil))
            :field (stroke state lay position position)
            (assoc state :last nil))))

      ;; :down. Only a finger that began in the field carries on, and it stops
      ;; the moment it leaves the field.
      (:last state) (if (= [:field] (hit lay position))
                      (stroke state lay position (:last state))
                      (assoc state :last nil))

      :else state)))

(defn- init [input] [(fresh (get-in input [:metrics :screen])) [[:scene/init :mousepaint]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :mousepaint]]])

(defn scene []
  {:id :mousepaint
   :title "Mouse Painting"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
