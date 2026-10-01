(ns raylib.scenes.inlinestyle
  "Colours set inside the string itself. Ported from raylib-jlt's
  `inline_styling`.

  A tiny markup changes the style mid-line: `[cRRGGBBAA]` sets the foreground,
  `[bRRGGBBAA]` paints a background behind the text that follows, and `[r]`
  puts both back. A tag touches only its own slot, so a foreground tag leaves
  the background as it was. The tag's alpha is multiplied by the base colour's
  alpha, rounded down, so one fade on the base fades everything the markup set.

  The grammar is positional, as in the original. A tag is `[r]`, or a `c` or `b`
  and exactly eight hex digits in brackets, in either case of hex. Anything else
  is text, including a stray `[`, so a malformed tag prints as it was typed
  instead of vanishing. Capital `C`, `B` and `R` are not tags.

  The original has no input, so this reads none. It rolls a new colour for the
  word CREATIVE every twenty frames and builds that line's markup from it, which
  is why the colour is a hex string in a tag and not a vector. The roll is frame
  locked as the original's is (it counts frames and never asks for a delta
  time), and it takes three channels from the project LCG's high bits where the
  original calls GetRandomValue. The first frame rolls too, as the original's
  frame 0 does, so the starting red is never drawn.

  The original does not wrap. The port scales the original's sizes and positions
  by one factor `u`, the smaller of the width over its 800 pixels and the room
  below Back over its 250 pixels of content, so the lines keep their
  proportions and fit the safe region. Runs are laid out left to right, each starting where the one
  before it ends, and that needs the width of text, which a pure namespace cannot
  ask raylib for. `layout` takes a `measure` function `(fn [s size] -> px)`. The
  draw method passes the real one and the tests pass an estimate of 0.6 of the
  size per character. Text is laid out in `dimensions`."
  (:require [raylib.gesture :as gesture]))

(def background-colour "The original's RAYWHITE." [245 245 245 255])
(def box-colour "The original's GREEN, round the CREATIVE line." [0 228 48 255])

(def ^:private black [0 0 0 255])
(def ^:private faded "The base of the alpha line, 100 of 255." [0 0 0 100])
(def ^:private gray [130 130 130 255])
(def ^:private initial-tint "The original's RED, before frame 0 replaces it." [230 41 55 255])

(def default-seed 2026)
(def retint-every "Frames between new CREATIVE colours, as in the original." 20)

;; --- the parser --------------------------------------------------------------

(def ^:private hex-digit
  (zipmap "0123456789abcdefABCDEF" (concat (range 16) (range 10 16))))

(defn- byte-at
  "The two hex digits at `i` as 0-255, or nil when either is not a digit."
  [s i]
  (let [hi (hex-digit (nth s i))
        lo (hex-digit (nth s (inc i)))]
    (when (and hi lo) (+ (* 16 hi) lo))))

(defn- rgba-at
  "RRGGBBAA at `i` as `[r g b a]`, or nil unless all eight are hex digits."
  [s i]
  (let [bytes (mapv #(byte-at s (+ i (* 2 %))) (range 4))]
    (when (every? some? bytes) bytes)))

(defn- tag-at
  "The tag starting at `i`, as `[length kind rgba]`, or nil when the bracket there
  is just a bracket. Every index is checked against the length first, so a tag
  cut off by the end of the string is text."
  [s i]
  (let [n (count s)
        c (when (< (inc i) n) (nth s (inc i)))]
    (when (= \[ (nth s i))
      (cond
        (and (= \r c) (< (+ i 2) n) (= \] (nth s (+ i 2))))
        [3 :reset nil]

        (and (contains? #{\c \b} c) (<= (+ i 11) n) (= \] (nth s (+ i 10))))
        (when-let [rgba (rgba-at s (+ i 2))]
          [11 (if (= \c c) :fg :bg) rgba])))))

(defn- scaled
  "`rgba` with its alpha multiplied by `base-alpha`, rounded down."
  [[r g b a] base-alpha]
  [r g b (quot (* a base-alpha) 255)])

(defn parse
  "Split `markup` into runs `{:text :fg :bg}`. `base` is the `[r g b a]` an
  untagged run is drawn in, and the alpha every tag is scaled by. `:bg` is nil
  where nothing is painted. Tags produce no runs of their own, so two in a row
  or one at either end leave no empty run."
  [markup base]
  (let [alpha (nth base 3)
        n (count markup)
        run (fn [from to fg bg] {:text (subs markup from to)
                                 :fg (or fg base)
                                 :bg bg})]
    (loop [i 0
           start 0
           fg nil
           bg nil
           runs []]
      (if (>= i n)
        (cond-> runs (> n start) (conj (run start n fg bg)))
        (if-let [[len kind rgba] (tag-at markup i)]
          (recur (+ i len)
                 (+ i len)
                 (case kind :reset nil :fg (scaled rgba alpha) fg)
                 (case kind :reset nil :bg (scaled rgba alpha) bg)
                 (cond-> runs (> i start) (conj (run start i fg bg))))
          (recur (inc i) start fg bg runs))))))

;; --- the colour roll ---------------------------------------------------------

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- roll-channel
  "A value in 0-255 and the next seed, from the high bits. The low bit of this
  LCG alternates on every step, and `mod 256` would keep it."
  [seed]
  (let [seed' (next-random seed)]
    [(mod (quot seed' 65536) 256) seed']))

(defn roll-tint
  "A fully opaque `[r g b 255]` and the next seed."
  [seed]
  (let [[r s1] (roll-channel seed)
        [g s2] (roll-channel s1)
        [b s3] (roll-channel s2)]
    [[r g b 255] s3]))

(defn- hex2 [v]
  (str (nth "0123456789abcdef" (quot v 16)) (nth "0123456789abcdef" (mod v 16))))

(defn creative-markup
  "The original's last styled line with `tint`'s colour in the tag."
  [[r g b]]
  (str "Let's be [c" (hex2 r) (hex2 g) (hex2 b) "FF]CREATIVE[r] !!!"))

;; --- layout ------------------------------------------------------------------

(def ^:private rows
  "The original's lines in its 800 by 450 pixels, `:y` as drawn. A `:plain?` line
  is drawn as it is, because the original draws its legend without parsing it,
  and a `:creative?` line takes its markup from the current tint."
  [{:s "This changes the [cFF0000FF]foreground color[r] of provided text!!!"
    :size 20
    :y 80
    :base black}
   {:s "This changes the [bFF00FFFF]background color[r] of provided text!!!"
    :size 20
    :y 120
    :base black}
   {:s "This changes the [c00ff00ff][bff0000ff]foreground and background colors[r]!!!"
    :size 20
    :y 160
    :base black}
   {:s (str "This changes the [c00ff00ff]alpha[r] relative "
            "[cffffffff][b000000ff]from source[r] [cff000088]color[r]!!!")
    :size 20
    :y 200
    :base faded}
   {:creative? true
    :size 40
    :y 240
    :base black}
   {:s "markup: [cRRGGBBAA] foreground - [bRRGGBBAA] background - [r] reset"
    :size 10
    :y 320
    :base gray
    :plain? true}])

(def ^:private original-width 800.0)
(def ^:private first-y 80)
(def ^:private original-extent "Top of the first line to the bottom of the last." 250.0)
(def ^:private left-units 100.0)

(defn dimensions
  "The layout for `metrics`' `:screen`. `:u` is the scale of the original's
  pixels, and `:lines` the rows as `{:s :size :x :y :base :plain? :creative?}`
  with `:s` nil on the creative one, whose text depends on the tint. Everything
  sits below Back."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        y0 (+ back-y back-h (* 0.02 h))
        u (min (/ w original-width) (/ (- h y0 (* 0.02 h)) original-extent))]
    {:w w
     :h h
     :u u
     :thick (max 2 (int (+ 0.5 (* 2 u))))
     :lines (mapv (fn [{:keys [size y]
                        :as row}]
                    (assoc row
                           :size (max 1 (int (+ 0.5 (* u size))))
                           :x (int (* left-units u))
                           :y (int (+ y0 (* u (- y first-y))))))
                  rows)}))

(defn- place
  "`runs` with an `:x` and a measured `:w`, each starting where the one before
  it ends."
  [runs x size measure]
  (first (reduce (fn [[placed dx] {:keys [text]
                                   :as run}]
                   (let [rw (measure text size)]
                     [(conj placed (assoc run :x dx :w rw)) (+ dx rw)]))
                 [[] x]
                 runs)))

(defn layout
  "`dims`' lines with their markup parsed for `tint` and every run placed, as
  `{:lines [{:size :x :y :runs [{:text :fg :bg :x :w}]}] :box [x y w h] :thick t}`.
  `measure` is `(fn [s size] -> px)`, and `:thick` is the line width for the box,
  passed on from `dims`. `:box` surrounds the creative line as the
  original's green rectangle does, and is as wide as its runs together."
  [{:keys [lines thick]} measure tint]
  (let [laid (mapv (fn [{:keys [s size x base plain? creative?]
                         :as line}]
                     (let [text (if creative? (creative-markup tint) s)
                           runs (if plain?
                                  [{:text text
                                    :fg base
                                    :bg nil}]
                                  (parse text base))]
                       (assoc (select-keys line [:size :x :y :creative?])
                              :runs (place runs x size measure))))
                   lines)
        {:keys [x y size runs]} (first (filter :creative? laid))
        end (let [r (peek runs)] (+ (:x r) (:w r)))]
    {:lines laid
     :box [x y (- end x) size]
     :thick thick}))

;; --- the scene ---------------------------------------------------------------

(defn advance
  "Count a frame. On every `retint-every`th, starting with the first, roll a new
  tint before counting, as the original does at the top of its loop."
  [{:keys [frame seed]
    :as state} _input]
  (let [state' (if (zero? (mod frame retint-every))
                 (let [[tint seed'] (roll-tint seed)]
                   (assoc state :tint tint :seed seed'))
                 state)]
    (assoc state' :frame (inc frame))))

(defn- init [{:keys [metrics]}]
  [{:screen (:screen metrics)
    :frame 0
    :seed default-seed
    :tint initial-tint}
   [[:scene/init :inlinestyle]]])

(defn- update-scene
  "Advance the frame, and follow the screen so a rotation is noticed. The draw
  method lays out from the metrics it is given."
  [state input]
  [(assoc (advance state input) :screen (get-in input [:metrics :screen] (:screen state))) []])

(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :inlinestyle]]])

(defn scene []
  {:id :inlinestyle
   :title "Inline Styling"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
