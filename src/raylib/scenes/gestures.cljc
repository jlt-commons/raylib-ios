(ns raylib.scenes.gestures
  "What raylib's own gesture recogniser reports, named and logged. Ported from
  raylib-jlt's `input_gestures`, which is raylib's `core_input_gestures` example
  (zlib licence).

  The original calls GetGestureDetected each frame, and when the code differs
  from the previous frame's and the finger is inside the test box it pushes the
  gesture's name onto a log of 20, newest at the bottom, drawn down the left with
  alternating rows and the newest in maroon. A maroon circle of radius 30 follows
  the finger while any gesture is set. The log is a rule on the code, not on the
  time: a gesture raylib keeps reporting is one entry, and the previous code
  updates every frame whether or not an entry was made, so a gesture that begins
  outside the box and carries on inside it is not logged either.

  Here `raylib.gallery` hands the code down as `:raylib-gesture`, read by
  `raylib.host/get-gesture-detected`. The recogniser is raylib's own: this scene
  only names what it says, and runs it unchanged. The 10 codes are
  `raylib.h`'s `Gesture` enum, and a code outside them is logged as \"GESTURE\"
  and the number.

  Where the finger is comes from `:pointer`, which the gallery already gives in
  scene coordinates, so GetTouchX is not called. Only a `:press` or `:down`
  position is read. On any other frame the last position seen on one is used, so
  a tap or a swipe that raylib reports at the moment of release lands where the
  finger last was, and the release position (which is stale) is never read. If
  no position has ever been seen, a gesture cannot be placed and is not logged,
  but it still becomes the previous code.

  What differs from the original:
  - Pinch in and pinch out never appear. raylib's SDL platform feeds its
    gesture system one touch point at a time, so a pinch is never two points
    to it. The table keeps both names, the scene says so on screen as one hint
    line, and the catalog row says it too. Tap, double-tap, hold, drag and the
    four swipes can appear.
  - The mouse controls become a finger on the glass, which is all there is.
  - The test box is the field below Back, and the log is stacked above it in
    portrait and runs down its left in landscape. The original's 800 by 450
    window had room for 20 rows beside the box, a phone in portrait does not.
    Text sizes come from `dimensions`, which shrinks them until 20 rows and the
    longest name fit.
  - The original calls SetGesturesEnabled with every flag. That is not bound
    here, since raylib enables all ten by default.

  Colours are `[r g b a]` vectors."
  (:require [raylib.gesture :as gesture]))

(def max-log "How many entries the log keeps. The original's." 20)

(def gesture-names
  "The names by raylib's `Gesture` code. The original's."
  {1 "GESTURE TAP"
   2 "GESTURE DOUBLETAP"
   4 "GESTURE HOLD"
   8 "GESTURE DRAG"
   16 "GESTURE SWIPE RIGHT"
   32 "GESTURE SWIPE LEFT"
   64 "GESTURE SWIPE UP"
   128 "GESTURE SWIPE DOWN"
   256 "GESTURE PINCH IN"
   512 "GESTURE PINCH OUT"})

(def background-colour [245 245 245 255])
(def area-colour [130 130 130 255])
(def area-text-colour [200 200 200 255])
(def hint-colour [230 230 230 255])
(def row-colour [200 200 200 128])
(def log-text-colour [80 80 80 255])
(def newest-colour [190 33 55 255])
(def header-colour [130 130 130 255])
(def circle-colour [190 33 55 255])

(def header-text "DETECTED GESTURES")
(def title-text "GESTURES TEST AREA")
(def hint-text "No pinch here: SDL feeds raylib one finger")

(defn gesture-name
  "The log entry for gesture `code`: its name, or \"GESTURE\" and the number."
  [code]
  (get gesture-names code (str "GESTURE " code)))

(defn- default-measure
  "estimate: 0.6 of the size per character, for raylib's default font. Used
  when the input carries no `:measure`."
  [s size]
  (* 0.6 size (count s)))

(defn- layout
  "The layout at text size `s`, or nil when it does not fit: the log's rect, its
  row height and the box's rect. `widest` is the longest log text at size 100."
  [w h s widest]
  (let [[_ back-y _ back-h] gesture/back-region
        pad (max 6 (int (* 0.5 s)))
        row-h (int (* 1.3 s))
        ftop (+ back-y back-h pad)
        fx pad
        fw (- w (* 2 pad))
        fh (- h ftop pad)
        log-h (+ pad s pad (* max-log row-h) pad)
        portrait? (< w h)]
    (if portrait?
      (let [area-h (- fh log-h pad)]
        (when (and (<= log-h (* 0.6 fh)) (pos? area-h))
          {:pad pad
           :row-h row-h
           :rows-y (+ ftop pad s pad)
           :log [fx ftop fw log-h]
           :area [fx (+ ftop log-h pad) fw area-h]}))
      (let [log-w (+ (* 2 pad) (* s (/ widest 100.0)))
            area-w (- fw log-w pad)]
        (when (and (<= log-h fh) (<= log-w (* 0.5 fw)) (pos? area-w))
          {:pad pad
           :row-h row-h
           :rows-y (+ ftop pad s pad)
           :log [fx ftop log-w log-h]
           :area [(+ fx log-w pad) ftop area-w fh]})))))

(defn dimensions
  "The layout for `metrics`' `:screen`. `measure` is `(fn [s size] -> px)`.

  The text size starts at 3% of the shorter side (at least 14) and shrinks to the
  largest that fits 20 rows and the longest text, down to 8. `:log` is `{:rect
  [x y w h] :size :row-h :rows-y :text-x}`, where `:rows-y` is the top of row 0
  and `:text-x` the left of every row's text. `:area` is the test box's `[x y w
  h]`, `:hint` its hint and `:hint-size` the size of that and the box's title,
  cut back to fit the box. `:circle-radius` is the finger circle's. `:lines` has
  the texts a test can check for fit: the log's header, the box's title and hint,
  and the longest name in the first and last rows."
  [metrics measure]
  (let [[w h] (:screen metrics)
        widest-name (apply max (map #(measure % 100) (vals gesture-names)))
        widest (max widest-name (measure header-text 100))
        desired (max 14 (int (* 0.03 (min w h))))
        [s lay] (or (some (fn [s] (when-let [l (layout w h s widest)] [s l]))
                          (range desired 7 -1))
                    [8 (layout w h 8 widest)])
        {:keys [pad row-h rows-y log area]} lay
        [lx ly _ _] log
        [ax ay aw _] area
        longest (apply max-key #(measure % 100) (vals gesture-names))
        text-x (+ lx pad)
        text-w (max 1 (- aw (* 2 pad)))
        longest-area (max (measure title-text 100) (measure hint-text 100))
        ts (max 6 (min s (int (/ (* text-w 100) (max 1 longest-area)))))
        row-text-y (fn [i] (+ rows-y (* i row-h) (quot (- row-h s) 2)))
        title-y (+ ay pad)
        hint-y (+ title-y ts (quot pad 2))
        lines [{:s header-text
                :x text-x
                :y (+ ly pad)
                :size s}
               {:s title-text
                :x (+ ax pad)
                :y title-y
                :size ts}
               {:s hint-text
                :x (+ ax pad)
                :y hint-y
                :size ts}
               {:s longest
                :x text-x
                :y (row-text-y 0)
                :size s}
               {:s longest
                :x text-x
                :y (row-text-y (dec max-log))
                :size s}]]
    {:w w
     :h h
     :portrait? (< w h)
     :log {:rect log
           :size s
           :row-h row-h
           :rows-y rows-y
           :text-x text-x
           :header-y (+ ly pad)
           :text-dy (quot (- row-h s) 2)}
     :area area
     :hint hint-text
     :hint-size ts
     :title {:s title-text
             :x (+ ax pad)
             :y title-y
             :size ts}
     :hint-line {:s hint-text
                 :x (+ ax pad)
                 :y hint-y
                 :size ts}
     :circle-radius (* 0.05 (min w h))
     :lines lines}))

(defn advance
  "One frame. The code is `:raylib-gesture`. A code that differs from the
  previous frame's, is not zero and has a position inside the box (half-open) is
  pushed onto the log, which keeps the newest `max-log`. The previous code is
  then the current one, logged or not. The position is `:pointer`'s on a
  `:press` or `:down`, and otherwise the last one seen; the release position is
  never read. The `dimensions` take about two dozen `measure` calls, so the
  state keeps them in `:dims` for the `:dims-screen` they were built for and
  `advance` rebuilds them only when the screen changes."
  [state input]
  (let [{:keys [phase position]} (:pointer input)
        screen (:screen (:metrics input))
        dims (if (and (:dims state) (= screen (:dims-screen state)))
               (:dims state)
               (dimensions (:metrics input) (or (:measure input) default-measure)))
        at (if (and position (contains? #{:press :down} phase))
             position
             (:at state))
        code (or (:raylib-gesture input) 0)
        new? (and (not= code 0)
                  (not= code (:last-gesture state))
                  at
                  (gesture/in-rect? (:area dims) at))]
    (assoc state
           :log (if new?
                  (vec (take-last max-log (conj (:log state) (gesture-name code))))
                  (:log state))
           :last-gesture code
           :gesture code
           :at at
           :dims dims
           :dims-screen screen)))

(defn- init [_]
  [{:log []
    :last-gesture 0
    :gesture 0
    :at nil}
   [[:scene/init :gestures]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :gestures]]])

(defn scene []
  {:id :gestures
   :title "Input Gestures"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
