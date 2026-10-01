(ns raylib.scenes.undoredo
  "A square driven round a grid with a bounded undo history. Ported from
  raylib-jlt's `undo-redo`, which is raylib's `core_undo_redo` example.

  The original drives the square with the arrow keys, recolours it with SPACE
  and steps through the history with CTRL-Z and CTRL-Y. Here a `:swipe` from
  `raylib.gesture` moves the square one cell in place of an arrow, a `:tap` on
  the square or anywhere else in the field recolours it in place of SPACE, and
  two buttons along the bottom, \"undo\" and \"redo\", replace CTRL-Z and CTRL-Y.
  A tap on a button does only the button's job and never also recolours, as the
  original's CTRL stops an arrow or SPACE from acting. A touch under Back is
  ignored, because it belongs to the host.

  The rules are the original's. The grid is 30 by 13 cells and is not turned
  with the screen, because the history strip already needs the width. The
  history holds at most `max-states` (26) states, the original's slot count,
  as a vector and a cursor in place of its ring buffer. Recording past the cap
  drops the oldest state. Recording after an undo drops the states ahead of the
  cursor, so redo is empty after a new action, and undo and redo stop at the
  ends. A colour change counts as an action like a move, and a move into a wall
  is clamped to the grid and so records nothing.

  Timing is frame-locked, as the original is. It compares the square with the
  state at the cursor only every second frame, so `:ticks` counts frames and the
  comparison runs on every `sample-frames`th one. A change made and then undone
  inside one sample is therefore never recorded, as in the original. An undo or
  redo frame adopts the state at the cursor and discards that frame's own
  change, the original's order.

  The original's palette cycles in order, which is kept. The trail is every state
  up to the cursor, so an undo visibly shortens it, and the strip along the
  bottom has one slot per held state with the cursor's filled. The original
  puts the word and the count on one line beside the strip. Here the line
  sits above the strip so the strip can use the whole width.

  Everything lives below `gesture/back-region`. Text is laid out in
  `dimensions`, which takes an injected `measure` `(fn [s size] -> px)`: the
  draw method passes raylib's own text width and the tests pass an estimate.
  Colours are `[r g b a]` vectors, so the namespace stays pure."
  (:require [raylib.gesture :as gesture]))

(def max-states
  "The history's slot count: the original's `MAX-STATES`."
  26)

(def sample-frames
  "Frames between history samples: the original samples every second frame."
  2)

(def cells-x 30)
(def cells-y 13)

(def background-colour [245 245 245 255])
(def caption-colour [80 80 80 255])
(def trail-colour [200 200 200 255])
(def grid-colour [220 220 220 255])
(def strip-fill-colour [80 80 80 255])
(def strip-line-colour [200 200 200 255])
(def label-colour [130 130 130 255])
(def button-colour [200 200 200 255])
(def button-label-colour [80 80 80 255])
(def button-off-colour [228 228 228 255])
(def button-off-label-colour [190 190 190 255])

(def palette
  "The original's five colours, cycled by a tap: RED, BLUE, LIME, ORANGE and
  PURPLE."
  [[230 41 55 255]
   [0 121 241 255]
   [0 158 47 255]
   [255 161 0 255]
   [200 122 255 255]])

(def caption "SWIPE: MOVE - TAP: COLOUR")

(defn history-line
  "The count beside the history strip, `cursor` and `n` as the original shows
  them, the cursor counted from one."
  [cursor n]
  (str "HISTORY " (inc cursor) " / " n))

(def widest-history-line (history-line (dec max-states) max-states))

(def undo-label "undo")
(def redo-label "redo")

(defn geometry
  "The layout for `metrics`' `:screen` that needs no text measure, which is all
  the touch handling uses. `:cell` is the grid's square, `:grid-x` and `:grid-y`
  its corner, so the grid is 30 by 13 cells and sits below Back. The history
  strip's slots are `:slot` apart from `:strip-x`, at `:strip-y`, `:strip-h`
  tall. `:undo` and `:redo` are the buttons as `[x y w h]` along the bottom.
  `:size` is the text size the spacing is built on, the larger of 20 and 0.03 of
  the shorter side."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        size (max 20 (int (* 0.03 (min w h))))
        bh (* 2.4 size)
        by (- h bh (* 0.02 h))
        bw (* 0.4 w)
        gap (* 0.04 w)
        ux (* 0.5 (- w gap (* 2 bw)))
        caption-y (+ top (* 0.01 h))
        grid-y (+ caption-y (* 1.5 size))
        cell (min (/ (* 0.92 w) cells-x) (/ (- by (* 3.2 size) grid-y) cells-y))
        grid-bottom (+ grid-y (* cells-y cell))
        label-y (+ grid-bottom (* 0.5 size))
        slot (/ (* 0.92 w) max-states)]
    {:w w
     :h h
     :top top
     :size size
     :cell cell
     :grid-x (* 0.5 (- w (* cells-x cell)))
     :grid-y grid-y
     :caption-y caption-y
     :label-y label-y
     :slot slot
     :strip-x (* 0.5 (- w (* max-states slot)))
     :strip-y (+ label-y size (* 0.2 size))
     :strip-h size
     :undo [ux by bw bh]
     :redo [(+ ux bw gap) by bw bh]}))

(defn dimensions
  "`geometry` plus the text: `:caption`, `:history` (the widest history line)
  and `:undo-label` and `:redo-label`, each `{:s :x :y :size}`, and `:lines`
  with all four so a test can check they fit. The text size is cut back from
  `geometry`'s `:size` when the caption or the history line would cover more
  than 0.92 of the width. `measure` is `(fn [s size] -> px)`."
  [metrics measure]
  (let [{:keys [w caption-y label-y strip-x undo redo]
         :as geo} (geometry metrics)
        widest (apply max (map #(measure % 100) [caption widest-history-line]))
        size (max 8 (min (:size geo) (int (/ (* 0.92 w 100.0) widest))))
        line (fn [s x y] {:s s
                          :x (int x)
                          :y (int y)
                          :size size})
        button-line (fn [s [bx by bw bh]]
                      (line s (+ bx (* 0.5 (- bw (measure s size)))) (+ by (* 0.5 (- bh size)))))
        lines {:caption (line caption (* 0.04 w) caption-y)
               :history (line widest-history-line strip-x label-y)
               :undo-label (button-line undo-label undo)
               :redo-label (button-line redo-label redo)}]
    (assoc (merge geo lines)
           :text-size size
           :lines (vec (vals lines)))))

(defn cell-rect
  "`[x y w h]` of the grid cell at `[c r]`, in pixels."
  [{:keys [cell grid-x grid-y]} c r]
  [(+ grid-x (* c cell)) (+ grid-y (* r cell)) cell cell])

(defn slot-rect
  "`[x y w h]` of the strip's slot `i`, narrower than the pitch so slots do not
  touch."
  [{:keys [slot strip-x strip-y strip-h]} i]
  [(+ strip-x (* i slot)) strip-y (max 1.0 (- slot 2.0)) strip-h])

(defn- clamp [v lo hi] (max lo (min hi v)))

(defn- record
  "`[history cursor]` with `state` appended at the cursor. What was ahead of the
  cursor is dropped, and so is the oldest state once `max-states` is passed."
  [history cursor state]
  (let [kept (conj (subvec history 0 (inc cursor)) state)
        kept (if (> (count kept) max-states) (subvec kept 1) kept)]
    [kept (dec (count kept))]))

(def ^:private start-player {:x 10
                             :y 10
                             :color 0})

(def ^:private swipe-steps
  {:up [0 -1]
   :down [0 1]
   :left [-1 0]
   :right [1 0]})

(defn can-undo? [{:keys [cursor]}] (pos? cursor))
(defn can-redo? [{:keys [cursor history]}] (< cursor (dec (count history))))

(defn- fresh [dims]
  {:player start-player
   :history [start-player]
   :cursor 0
   :ticks 0
   :screen [(:w dims) (:h dims)]
   :gesture gesture/idle})

(defn- act
  "What the touch event asks for: `{:undo? :redo? :swipe :colour?}`. A tap on a
  button asks only for its action, and a tap under Back or on neither a button
  nor the field asks for nothing."
  [dims event]
  (let [at (:at event)]
    (case (:type event)
      :swipe {:swipe (swipe-steps (:dir event))}
      :tap (cond
             (gesture/in-back-region? at) {}
             (gesture/in-rect? (:undo dims) at) {:undo? true}
             (gesture/in-rect? (:redo dims) at) {:redo? true}
             :else {:colour? true})
      {})))

(defn advance
  "One frame. Calls `gesture/track` once and stores the result on every path. A
  rotation (a different `:screen`) starts over, since the layout is in pixels.
  Otherwise the order is the original's: apply the move or the recolour to the
  square, count the frame, record the change on every `sample-frames`th frame,
  then let undo or redo move the cursor and adopt the state there."
  [state input]
  (let [dims (geometry (:metrics input))
        [g event] (gesture/track (:gesture state) input)]
    (if (not= [(:w dims) (:h dims)] (:screen state))
      (assoc (fresh dims) :gesture g)
      (let [{:keys [undo? redo? swipe colour?]} (act dims event)
            {:keys [player history cursor ticks]} state
            moved (cond-> player
                    swipe (-> (update :x + (first swipe))
                              (update :y + (second swipe)))
                    colour? (update :color #(mod (inc %) (count palette)))
                    true (-> (update :x clamp 0 (dec cells-x))
                             (update :y clamp 0 (dec cells-y))))
            ticks (inc ticks)
            sample? (>= ticks sample-frames)
            [history cursor] (if (and sample? (not= moved (nth history cursor)))
                               (record history cursor moved)
                               [history cursor])
            cursor (cond
                     (and undo? (pos? cursor)) (dec cursor)
                     (and redo? (< cursor (dec (count history)))) (inc cursor)
                     :else cursor)]
        (assoc state
               :gesture g
               :ticks (if sample? 0 ticks)
               :history history
               :cursor cursor
               :player (if (or undo? redo?) (nth history cursor) moved))))))

(defn- init [{:keys [metrics]}]
  [(fresh (geometry metrics))
   [[:scene/init :undoredo]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :undoredo]]])

(defn scene []
  {:id :undoredo
   :title "Undo Redo"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
