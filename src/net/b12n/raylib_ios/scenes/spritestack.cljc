(ns net.b12n.raylib-ios.scenes.spritestack
  "Sprite stacking, ported from raylib-jlt's `sprite_stacking`
  (net/b12n/raylib_jlt/sprite_stacking.clj, zlib licence): forty top-down slices
  of a small car, each drawn at the same rotation and a little higher on the
  screen than the one below, which the eye reads as a solid body turning in place.

  The original paints the slices into one 56 by 784 image with `image-draw-
  rectangle!` and `image-draw-circle!` (lines 38-76) and draws each as a
  textured quad. There is no image here. `layer-shapes` is those calls, kept as
  data: `[:rect x y w h colour]` and `[:circle cx cy r colour]` in the slice's
  56 by 28 pixels. Each is drawn directly, rotated with its slice.
  - `slab` (lines 38-47): a rounded rectangle as two rects, one inset by `r` at
    the sides and one at the top and bottom, and four circles of radius `r` on
    its corners. The corner circles are centred on the integer corner, so they
    touch the rects' edges as the raster circles do.
  - `draw-layer` (lines 49-71), with `t = i / 39`: from `t >= 0.88` two black
    11 by 28 wheels at x 8 and 38; from 0.55 the body (2, 1, 52, 26, r 3) in RED;
    from 0.30 the shoulders (5, 2, 46, 24, r 3) in RED; otherwise the cabin (10,
    3, 26, 22, r 3) in MAROON, with a SKYBLUE glass (13, 6, 20, 16, r 2) while
    `t < 0.15`. That is 5 wheel slices, 13 body, 10 shoulders and 12 cabin
    slices, glass on the top 6.
  - The draw loop (lines 112-124): slices from the last row to the first, so row
    0, the roof, lands on top. Slice `i` is centred on x = the middle and
    y = the middle + `i * spacing - spacing * 40 / 2`, scaled 3x (`stack-scale`),
    and turned by the rotation about its own centre.
  - The state (lines 99-111): rotation in degrees, a spin speed of 30 degrees a
    second, and a spacing of 3.2. Each frame the rotation grows by
    `speed * frame-time` after the speed has been changed by the keys.

  Controls here:
  - A horizontal drag replaces the arrow keys and A and D. It is a relative
    stick: the press point is the centre, and while the finger is down further
    than `gesture/slop` to the right of it the speed grows by 0.35 a frame, to
    the left it falls by 0.35, as the keys did (lines 103-109, frame locked, no
    cap). Inside the slop, or only drifting up or down, nothing changes, so a tap
    spins nothing. A press under Back starts no stick.
  - A two-finger pinch replaces the mouse wheel. The original adds 0.1 a notch to
    the spacing, held to 0 to 5. Here the spacing grows by `pinch-gain` times the
    change of the fingers' distance minus one (`net.b12n.raylib-ios.camera2d/pinch-step`, so
    the order of the two never matters), held to the same 0 to 5. It is additive
    so that a spacing of 0 can open again. It acts only while exactly two fingers
    stay down (`net.b12n.raylib-ios.camera2d/pinch-frame`), and two fingers end a drag.

  The slices are scaled by `k`, the smaller of the safe region's width over 800
  and the field's height over 450, and centred on the field. The original's
  texts are kept (lines 125-135) with the controls named for a phone, and the
  caption at the bottom says what the slices are now made of. The state holds
  numbers only."
  (:require [net.b12n.raylib-ios.camera2d :as cam]
            [net.b12n.raylib-ios.gesture :as gesture]))

(def frame-w "The original's FRAME-W." 56)
(def frame-h "The original's FRAME-H." 28)
(def layers "The original's LAYERS." 40)
(def stack-scale "The original's STACK-SCALE." 3.0)
(def speed-step "The original's SPEED-STEP." 0.35)
(def min-spacing "The original's MIN-SPACING." 0.0)
(def max-spacing "The original's MAX-SPACING." 5.0)
(def start-speed "The original's opening spin, degrees a second." 30.0)
(def start-spacing "The original's opening spacing." 3.2)
(def pinch-gain
  "Spacing gained when two fingers double their distance. A guess at what feels
  like the wheel: half the range."
  2.5)

(def view-w "The original's window width." 800.0)
(def view-h "The original's window height." 450.0)

(def background-colour "RAYWHITE." [245 245 245 255])
(def text-colour "DARKGRAY." [80 80 80 255])
(def note-colour "GRAY." [130 130 130 255])

(def red [230 41 55 255])
(def maroon [190 33 55 255])
(def skyblue [102 191 255 255])
(def black [0 0 0 255])

(def hint "drag: spin - pinch: spread the layers")
(def note "40 slices of rects and circles: no image files ship here")

(defn- fixed
  "`x` with `digits` places, rounded half up; no `format` on this runtime."
  [x digits]
  (let [p (long (Math/pow 10 digits))
        n (long (Math/floor (+ (* (double x) p) 0.5)))
        a (abs n)
        whole (quot a p)
        frac (str (rem a p))]
    (str (when (neg? n) "-") whole "." (apply str (repeat (- digits (count frac)) "0")) frac)))

(defn spacing-line [spacing] (str "spacing " (fixed spacing 1)))
(defn speed-line [speed] (str "speed " (fixed speed 2)))

(defn- slab
  "The original's `slab!` (lines 38-47) as shapes."
  [x y w h r colour]
  [[:rect (+ x r) y (- w (* 2 r)) h colour]
   [:rect x (+ y r) w (- h (* 2 r)) colour]
   [:circle (+ x r) (+ y r) r colour]
   [:circle (+ x w (- r)) (+ y r) r colour]
   [:circle (+ x r) (+ y h (- r)) r colour]
   [:circle (+ x w (- r)) (+ y h (- r)) r colour]])

(defn- build-layer
  "The original's `draw-layer!` (lines 49-71) for slice `i`."
  [i]
  (let [t (/ (double i) (dec layers))]
    (cond
      (>= t 0.88) [[:rect 8 0 11 28 black]
                   [:rect 38 0 11 28 black]]
      (>= t 0.55) (slab 2 1 52 26 3 red)
      (>= t 0.30) (slab 5 2 46 24 3 red)
      :else (cond-> (slab 10 3 26 22 3 maroon)
              (< t 0.15) (into (slab 13 6 20 16 2 skyblue))))))

(def ^:private slices (mapv build-layer (range layers)))

(defn layer-shapes
  "The shapes of slice `i` (0 is the roof), in the slice's pixels."
  [i]
  (nth slices i))

(defn geometry
  "The layout for `metrics`' `:screen`, without text. `:field` is `[x y w h]`,
  between the readouts under Back and the note at the bottom; `:centre` is its
  middle; `:k` is the original's unit in pixels (the smaller of the width over
  800 and the field's height over 450); `:u` is a slice pixel in screen pixels;
  `:size`, `:pad` and `:row` are the text metrics; `:line-ys` are the y of the
  hint, spacing, speed and note lines."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        size (max 16 (int (* 0.03 (min w h))))
        pad (max 8 (int (* 0.5 size)))
        row (int (* 1.3 size))
        y0 (+ back-y back-h pad)
        note-y (- h pad size)
        top (+ y0 (* 3 row) pad)
        field-h (- note-y pad top)
        k (min (/ (double w) view-w) (/ (double field-h) view-h))]
    {:w w
     :h h
     :size size
     :pad pad
     :row row
     :line-ys [y0 (+ y0 row) (+ y0 row row) note-y]
     :field [0.0 (double top) (double w) (double field-h)]
     :centre [(* 0.5 w) (+ top (* 0.5 field-h))]
     :k k
     :u (* stack-scale k)}))

(defn dimensions
  "`geometry` plus the text: `:lines` are the hint, the spacing and speed
  readouts (at their widest) and the note, each `{:s :x :y :size}`, each cut back
  from `geometry`'s size until it fits the width. `measure` is
  `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w size pad line-ys]
         :as geo} (geometry metrics)
        fit (fn [s] (max 8 (min size (int (/ (* (- w (* 2 pad)) 100.0) (measure s 100))))))
        texts [hint (spacing-line max-spacing) (speed-line -9999.99) note]]
    (assoc geo :lines (mapv (fn [s y] {:s s
                                       :x pad
                                       :y y
                                       :size (fit s)})
                            texts line-ys))))

(defn emit-stack!
  "Draws the stack through three callbacks. For each slice, last row first,
  `(layer! i x y rotation f)` is called with the slice's centre in pixels and the
  rotation in degrees; it must call zero-arg `f` with the transform applied, and
  `f` calls `(rect! x y w h colour)` and `(circle! x y r colour)` in pixels
  relative to that centre. `dims` is from `geometry`."
  [layer! rect! circle! {:keys [rotation spacing]} {:keys [centre k u]}]
  (let [[cx cy] centre
        gap (* spacing k)
        half-w (* 0.5 frame-w)
        half-h (* 0.5 frame-h)
        px (fn [v half] (long (Math/round (* u (- v half)))))]
    (loop [i (dec layers)]
      (when (>= i 0)
        (let [shapes (nth slices i)
              n (count shapes)
              y (+ cy (* i gap) (- (/ (* gap layers) 2.0)))]
          (layer! i cx y rotation
                  (fn []
                    (loop [j 0]
                      (when (< j n)
                        (let [s (nth shapes j)]
                          (if (= :rect (nth s 0))
                            (let [x0 (px (nth s 1) half-w)
                                  y0 (px (nth s 2) half-h)
                                  x1 (px (+ (nth s 1) (nth s 3)) half-w)
                                  y1 (px (+ (nth s 2) (nth s 4)) half-h)]
                              (rect! x0 y0 (- x1 x0) (- y1 y0) (nth s 5)))
                            (circle! (px (nth s 1) half-w) (px (nth s 2) half-h)
                                     (* u (nth s 3)) (nth s 4))))
                        (recur (inc j)))))))
        (recur (dec i))))))

(defn clamp-spacing [s] (max min-spacing (min max-spacing s)))

(defn- stick-dir
  "-1.0, 1.0 or nil: the sign of the drag from `(:stick state)`'s centre once the
  finger is down further than the slop along x. The press frame, a finger
  inside the dead zone, no stick, a lifted finger and two or more fingers give
  nil."
  [state input metrics]
  (let [centre (get-in state [:stick :centre])
        {:keys [phase position]} (:pointer input)]
    (when (and centre
               (< (count (:touch-points input)) 2)
               (gesture/down? input)
               (not= :press phase))
      (let [dx (- (double (first position)) (double (first centre)))]
        (when (> (abs dx) (gesture/slop metrics))
          (if (pos? dx) 1.0 -1.0))))))

(defn- next-stick
  "The stick after this frame: a single press outside Back starts one at the
  press point, a held finger keeps its centre, and anything else ends it."
  [state input]
  (let [{:keys [phase position]} (:pointer input)]
    (when (< (count (:touch-points input)) 2)
      (case phase
        :press (when (and position (not (gesture/in-back-region? position)))
                 {:centre position})
        :down (when-let [centre (get-in state [:stick :centre])]
                (when position {:centre centre}))
        nil))))

(defn advance
  "One frame (lines 99-111). The spin speed moves by `speed-step` for a drag
  right or left, then the rotation grows by the speed times
  `:delta-seconds`. Exactly two fingers, after exactly two the frame before,
  change the spacing by `pinch-gain` times `ratio - 1`, held to 0 to 5. A turn
  of the phone drops the stick and the pinch. The release position is never
  read."
  [state input]
  (let [metrics (:metrics input)
        screen (:screen metrics)
        state (if (not= screen (:screen state)) (dissoc state :stick :pinch) state)
        dt (max 0.0 (double (:delta-seconds input 0.0)))
        {now :pinch
         step :step} (cam/pinch-frame (:pinch state) (:touch-points input))
        dir (stick-dir state input metrics)
        speed (+ (:speed state) (* (or dir 0.0) speed-step))]
    (assoc state
           :screen screen
           :speed speed
           :rotation (+ (:rotation state) (* speed dt))
           :spacing (if step
                      (clamp-spacing (+ (:spacing state) (* pinch-gain (- (:ratio step) 1.0))))
                      (:spacing state))
           :pinch now
           :stick (next-stick state input))))

(defn- init [{:keys [metrics]}]
  [{:rotation 0.0
    :speed start-speed
    :spacing start-spacing
    :screen (:screen metrics)
    :pinch nil
    :stick nil}
   [[:scene/init :spritestack]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :spritestack]]])

(defn scene []
  {:id :spritestack
   :title "Sprite Stacking"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
