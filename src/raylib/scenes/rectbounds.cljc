(ns raylib.scenes.rectbounds
  "Text wrapped inside a box you resize by dragging its corner handle. Ported
  from raylib-jlt's `rectangle_bounds`, which is raylib's
  `text_rectangle_bounds` example.

  The mouse and the keyboard become two touch controls. The corner handle is
  dragged in place of the mouse, and a `:tap` on the wrap button toggles word or
  character wrap in place of SPACE. The original also fades the border and
  handle while the mouse hovers or drags. A finger has no hover, so the faded
  colours show while the handle is held, which is the part of that state a
  touch screen can have.

  The grab is sticky, as in `resize`: the handle is taken on the press that
  lands in its grab box and kept until the finger lifts, so dragging faster than
  the corner follows does not drop it. Unlike `resize`, which snaps the corner
  to the finger, this follows the original and moves the box by the finger's
  change since the last frame, so the handle does not jump to where the finger
  landed. The box is clamped between a minimum and the room above the controls.

  Wrapping lives in `layout-text`, which is pure. The original measures with
  MeasureText, which a pure namespace cannot call, so the layout takes a
  `measure` function `(fn [s size] -> px)`. The draw method passes the real one
  and the tests pass an estimate of 0.6 of the size per character. Text that
  would spill past the box height is dropped a line at a time, the same check
  the original (and raylib's DrawTextBoxed) makes, in place of a scissor.

  Everything lives in the safe region below Back. The box starts below it and
  the hint and the wrap button sit under the box, so nothing is under Back or
  off the screen. Sizes scale with the shorter side over 450, the height of the
  original's window. Text is laid out in `dimensions`, with widths estimated at
  0.6 of the size per character."
  (:require [clojure.string :as str]
            [raylib.gesture :as gesture]))

(def text
  "The original's text, with its double spaces and its blank line."
  (str "Text cannot escape  this container  ...word wrap also works when "
       "active so here's a long text for testing.\n\n"
       "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do "
       "eiusmod tempor incididunt ut labore et dolore magna aliqua. Nec "
       "ullamcorper sit amet risus nullam eget felis eget."))

(def background-colour [245 245 245 255])
(def border-colour "The original's MAROON." [190 33 55 255])
(def faded-colour "The original's hand-picked lighter maroon, shown while held." [223 160 169 255])
(def text-colour [130 130 130 255])
(def button-colour [130 130 130 255])
(def label-colour [0 0 0 255])
(def hint-colour [130 130 130 255])

(def hint "Drag the corner to resize")

(defn wrap-label
  "The button's label for `wrap`, `:word` or `:char`."
  [wrap]
  (str "Wrap: " (if (= wrap :word) "word" "char")))

(defn dimensions
  "The layout for `metrics`' `:screen`. `:top` is where the region below Back
  starts, `:size` the text size and `:u` the scale of the original's pixels.
  The box sits at `:box-x :box-y`, between `:min-w :min-h` and `:max-w :max-h`.
  `:handle` is the drawn corner square and `:grab` how much bigger the target
  is on each side, because a finger is not a cursor. `:button` is `[x y w h]`.
  `:lines` holds the hint and the widest button label as `{:s :x :y :size}`, so
  a test can check they fit."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        side (min w h)
        u (/ side 450.0)
        size (max 20 (int (* 0.03 side)))
        bw (* 0.5 w)
        bh (* 2.4 size)
        bx (* 0.5 (- w bw))
        by (- h bh (* 0.02 h))
        hint-y (- by (* 1.8 size))
        hs (max 28.0 (* 0.03 w))
        box-x (* 0.05 w)
        box-y (+ top (* 0.02 h))
        widest (apply max-key count (map wrap-label [:word :char]))]
    {:w w
     :h h
     :top top
     :u u
     :size size
     :box-x box-x
     :box-y box-y
     :min-w (* 60.0 u)
     :min-h (* 60.0 u)
     :max-w (- w (* 2.0 box-x))
     :max-h (- hint-y box-y hs)
     :start-h (* 200.0 u)
     :handle hs
     :grab (* 0.5 hs)
     :line-width (max 2.0 (* 1.5 u))
     :button [bx by bw bh]
     :lines [{:s hint
              :x (int (* 0.04 w))
              :y (int hint-y)
              :size size}
             {:s widest
              :x (int bx)
              :y (int (+ by (* 0.5 (- bh size))))
              :size size}]}))

(defn centred-x
  "Where text `s` at `size` starts to be centred in `[x w]`, using `measure`."
  [x w s size measure]
  (+ x (* 0.5 (- w (measure s size)))))

(defn- wrap-chars
  "Character wrap of `line` to `width`: used when word wrap is off, and by
  `wrap-words` to break one word wider than the box."
  [line width size measure]
  (let [n (count line)]
    (if (zero? n)
      [""]
      (loop [i 0 cur "" acc []]
        (if (< i n)
          (let [c (subs line i (inc i))
                trial (str cur c)]
            (if (> (measure trial size) width)
              (recur (inc i) (if (= c " ") "" c) (conj acc cur))
              (recur (inc i) trial acc)))
          (if (= cur "") acc (conj acc cur)))))))

(defn- wrap-words
  "Greedy word wrap of `line` to `width`. A word wider than `width` falls back
  to a character break, so text cannot escape the box sideways either way."
  [line width size measure]
  (if (= line "")
    [""]
    (let [words (str/split line #" ")
          nw (count words)]
      (loop [wi 0 cur "" acc []]
        (if (< wi nw)
          (let [wd (nth words wi)
                trial (if (= cur "") wd (str cur " " wd))]
            (if (> (measure trial size) width)
              (if (= cur "")
                (let [pieces (wrap-chars wd width size measure)]
                  (recur (inc wi) (peek pieces) (into acc (pop pieces))))
                (recur wi "" (conj acc cur)))
              (recur (inc wi) trial acc)))
          (if (= cur "")
            (if (= acc []) [""] acc)
            (conj acc cur)))))))

(defn layout-text
  "Wrap `s` inside a `box-w` by `box-h` box and return the lines that fit, as a
  vector of `[line y]` with `y` measured down from the box's top edge. `measure`
  is `(fn [s size] -> px)`. `wrap` is `:word` (the default) or `:char`.

  The text is inset by 0.2 of the size on every side, and a line is kept only
  while its bottom edge, 1.2 sizes below its top, is within the box height:
  the original's `24 <= height` check at size 20. Lines are 1.5 sizes apart."
  ([s box-w box-h size measure]
   (layout-text s box-w box-h size measure :word))
  ([s box-w box-h size measure wrap]
   (let [pad (* 0.2 size)
         width (- box-w (* 2.0 pad))
         lh (* 1.5 size)
         wrapper (if (= wrap :word) wrap-words wrap-chars)
         lines (into [] (mapcat #(wrapper % width size measure)) (str/split-lines s))]
     (into []
           (comp (map-indexed (fn [i line] [line (+ pad (* i lh))]))
                 (take-while (fn [[_ y]] (<= (+ y size) box-h))))
           lines))))

(defn handle-rect
  "The drawn corner square as `[x y w h]`, inside the box's far corner with the
  original's small gap."
  [{:keys [box-x box-y handle size]} bw bh]
  (let [gap (* 0.15 size)]
    [(- (+ box-x bw) handle gap) (- (+ box-y bh) handle gap) handle handle]))

(defn grab-rect
  "The handle's grab target as `[x y w h]`: the drawn square grown by `:grab` on
  every side."
  [{:keys [grab]
    :as dims} bw bh]
  (let [[x y w h] (handle-rect dims bw bh)]
    [(- x grab) (- y grab) (+ w (* 2.0 grab)) (+ h (* 2.0 grab))]))

(defn- clamp [lo hi v] (max lo (min hi v)))

(defn- fresh
  "The state at rest for `dims`: the box full width, wrap by word."
  [dims wrap]
  {:box-w (:max-w dims)
   :box-h (min (:max-h dims) (:start-h dims))
   :holding? false
   :last nil
   :wrap wrap
   :screen [(:w dims) (:h dims)]
   :gesture gesture/idle})

(defn advance
  "One frame. Calls `gesture/track` once and stores the result on every path. A
  rotation (a different `:screen`) starts over, since the box is in pixels. The
  handle is taken on a `:press` inside its grab box and kept while a finger is
  down, moving the box by the finger's travel since the last frame. A `:tap`
  inside the button toggles the wrap. The `:release` position is never read."
  [state input]
  (let [dims (dimensions (:metrics input))]
    (if (not= [(:w dims) (:h dims)] (:screen state))
      (assoc (fresh dims (:wrap state)) :gesture (first (gesture/track (:gesture state) input)))
      (let [[g event] (gesture/track (:gesture state) input)
            phase (get-in input [:pointer :phase])
            point (get-in input [:pointer :position])
            down? (gesture/down? input)
            holding? (cond
                       (not down?) false
                       (:holding? state) true
                       (= :press phase) (gesture/in-rect? (grab-rect dims (:box-w state) (:box-h state)) point)
                       :else false)
            [lx ly] (:last state)
            [px py] point
            moving? (and holding? (:holding? state) lx)
            box-w (if moving?
                    (clamp (:min-w dims) (:max-w dims) (+ (:box-w state) (- px lx)))
                    (:box-w state))
            box-h (if moving?
                    (clamp (:min-h dims) (:max-h dims) (+ (:box-h state) (- py ly)))
                    (:box-h state))
            toggle? (and (= :tap (:type event))
                         (gesture/in-rect? (:button dims) (:at event)))]
        (assoc state
               :gesture g
               :holding? holding?
               :last (when (and holding? point) [px py])
               :box-w box-w
               :box-h box-h
               :wrap (if toggle?
                       (if (= :word (:wrap state)) :char :word)
                       (:wrap state)))))))

(defn- init [{:keys [metrics]}]
  [(fresh (dimensions metrics) :word) [[:scene/init :rectbounds]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :rectbounds]]])

(defn scene []
  {:id :rectbounds
   :title "Rectangle Bounds"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
