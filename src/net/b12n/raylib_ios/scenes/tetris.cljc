(ns net.b12n.raylib-ios.scenes.tetris
  "Tetris, dragged and tapped. Ported from raylib-jlt's `tetris`.

  The original moves with LEFT and RIGHT, rotates on UP, soft-drops on DOWN,
  hard-drops on SPACE and restarts on ENTER. Here a horizontal DRAG moves the
  piece, a `:tap` from `net.b12n.raylib-ios.gesture` rotates it, a downward `:swipe` hard
  drops it and a `:tap` restarts after game over, unless it lands in the Back
  region, which belongs to the host. The soft drop is dropped: a held finger is
  already steering, and a second gesture for gravity would fight it.

  The drag keeps its own anchor, because the gesture layer only reports a swipe
  at the release. On `:press` the scene records the finger's x and the piece's
  column. While the finger is down the target column is that column plus the
  finger's travel in cells, rounded, and the piece steps toward it one column at
  a time through `valid?`, so it stops at a wall or a block rather than jumping
  over one. Only the raw `:pointer` on `:press` and `:down` is read, never the
  `:release`, whose position is the last hardware value. A press that starts in
  the Back region starts no drag.

  The same finger then reports a horizontal `:swipe` on release, which is
  IGNORED, since the drag already moved the piece. A swipe up does nothing. A
  tap, whose travel is at most `gesture/slop`, cannot have moved the piece,
  because the slop is under half a cell on the phone in either orientation
  (21.7 px against a 50 px half cell tall and a 27 px one wide, and a test pins
  that), so the rounded travel is zero. A swipe down that drifts a cell or more
  sideways does move the piece first, which follows the finger and is accepted.
  If a piece locks while the finger is still down, the drag re-anchors on the
  next piece from the finger's current x, so the new piece does not jump.

  The rules are the original's: ten columns by twenty rows, the seven pieces and
  their rotations, `valid?`, locking, line clears, the 0, 40, 100, 300, 1200
  score table, ten lines per level and the gravity interval of `max 6 (48 - 4 *
  level)` frames. A blocked rotation is refused and a piece that cannot spawn
  ends the game. The original picks each piece with `get-random-value 0 6`,
  which is uniform, so here the project's LCG supplies its HIGH bits (its low
  bit alternates on every step) and the piece is that value mod 7.

  Gravity is a frame count, as in the original, so the game falls faster on a
  120 Hz display than on a 60 Hz one. The Delta Time scene shows why that
  matters. It is kept here because the point of the port is the original, and a
  frame count needs no pixel scaling.

  The well is the largest 10 by 20 grid that fits below the Back button. On a
  tall phone the score, lines, level and next piece sit in a band above the
  well, and on a wide one they sit to its right.

  The original keeps drawing the falling piece over the stack once the game is
  over. The port hides it, because the piece that could not spawn sits on top of
  locked cells and would be drawn on them, which reads as a glitch more than as
  the cause of the loss.

  The state remembers its `:screen` and a rotation starts a new game, as
  Breakout's does. Cells hold a piece keyword and colours are `[r g b a]`
  vectors, so the namespace stays pure. The draw method packs them."
  (:require [net.b12n.raylib-ios.gesture :as gesture]))

(def cols "Well width in cells. The original's." 10)
(def rows "Well height in cells. The original's." 20)

(def piece-types
  "The seven pieces, in the original's order."
  [:i :o :t :s :z :j :l])

(def pieces
  "Each piece's colour and its rotation states, the original's. A state is four
  `[col row]` cells in a small local grid, and a piece with fewer distinct
  rotations lists only what it has."
  {:i {:colour [102 191 255 255]
       :rots [[[0 1] [1 1] [2 1] [3 1]] [[2 0] [2 1] [2 2] [2 3]]]}
   :o {:colour [255 203 0 255]
       :rots [[[1 0] [2 0] [1 1] [2 1]]]}
   :t {:colour [200 122 255 255]
       :rots [[[1 0] [0 1] [1 1] [2 1]] [[1 0] [1 1] [2 1] [1 2]]
              [[0 1] [1 1] [2 1] [1 2]] [[1 0] [0 1] [1 1] [1 2]]]}
   :s {:colour [0 228 48 255]
       :rots [[[1 0] [2 0] [0 1] [1 1]] [[1 0] [1 1] [2 1] [2 2]]]}
   :z {:colour [230 41 55 255]
       :rots [[[0 0] [1 0] [1 1] [2 1]] [[2 0] [1 1] [2 1] [1 2]]]}
   :j {:colour [0 121 241 255]
       :rots [[[0 0] [0 1] [1 1] [2 1]] [[1 0] [2 0] [1 1] [1 2]]
              [[0 1] [1 1] [2 1] [2 2]] [[1 0] [1 1] [0 2] [1 2]]]}
   :l {:colour [255 161 0 255]
       :rots [[[2 0] [0 1] [1 1] [2 1]] [[1 0] [1 1] [1 2] [2 2]]
              [[0 1] [1 1] [2 1] [0 2]] [[0 0] [1 0] [1 1] [1 2]]]}})

(def score-table "Points for 0 to 4 lines at once. The original's." [0 40 100 300 1200])

(def background-colour [18 18 28 255])
(def well-colour [128 128 128 255])
(def text-colour [245 245 245 255])
(def label-colour [130 130 130 255])
(def over-colour [230 41 55 255])

(def over-line "GAME OVER - TAP")
(def next-line "next")

(defn score-line [n] (str "score " n))
(defn lines-line [n] (str "lines " n))
(defn level-line [n] (str "level " n))

(defn interval
  "Frames between gravity steps at `level`. The original's."
  [level]
  (max 6 (- 48 (* 4 level))))

(defn piece-cells
  "The four `[col row]` well cells `piece` covers."
  [{:keys [type rot x y]}]
  (let [rs (:rots (pieces type))
        state (nth rs (mod rot (count rs)))]
    (map (fn [[cx cy]] [(+ x cx) (+ y cy)]) state)))

(defn valid?
  "True when every cell of `piece` is in the well and on an empty cell."
  [board piece]
  (every? (fn [[c r]]
            (and (>= c 0) (< c cols) (>= r 0) (< r rows) (nil? (get-in board [r c]))))
          (piece-cells piece)))

(defn dimensions
  "Where everything sits. `:cell` is the largest square that fits the well in
  the screen's width and in its height below Back, and below the panel band on a
  tall screen. The well starts at or below the bottom edge of
  `gesture/back-region`. On a tall screen the band of `:text-size` rows holds
  the three counters at the left and the next piece at the right. On a wide one
  the same things stack to the right of the well."
  [metrics]
  (let [[w h] (:screen metrics)
        tall? (> h w)
        side (min w h)
        ts (max 20 (int (* 0.03 side)))
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        band (if tall? (* 6 ts) 0)
        play-h (- h top band)
        cell (min (/ w (double cols)) (/ play-h (double rows)))
        wx (* 0.5 (- w (* cols cell)))
        wy (+ top band (* 0.5 (- play-h (* rows cell))))
        pc (* 0.5 cell)
        line-gap (* 1.4 ts)
        msg-size (max 20 (int (* 0.04 side)))
        ;; estimate: 0.6 of the size per character, for raylib's default font.
        msg-w (* 0.6 msg-size (count over-line))
        tx (if tall? (int (* 0.04 w)) (int (+ wx (* cols cell) cell)))
        ty (if tall? (int (+ top (* 0.3 ts))) (int wy))
        nx (if tall? (int (- w (* 4 pc) (* 0.04 w))) tx)
        ny (if tall? ty (int (+ ty (* 4.5 ts))))]
    {:w w
     :h h
     :cell cell
     :wx wx
     :wy wy
     :text-size ts
     :score-x tx
     :score-y ty
     :lines-y (int (+ ty line-gap))
     :level-y (int (+ ty (* 2 line-gap)))
     :next-x nx
     :next-y ny
     :pc pc
     :preview-x nx
     :preview-y (+ ny (* 1.3 ts))
     :msg-size msg-size
     :msg-x (max 0 (int (+ wx (* 0.5 (- (* cols cell) msg-w)))))
     :msg-y (int (+ wy (* 0.5 rows cell) (* -0.5 msg-size)))}))

(defn cell-rect
  "`[x y w h]` of the well cell at `[c r]`, in pixels, one pixel short each way
  so the cells show a grid."
  [{:keys [cell wx wy]} c r]
  [(+ wx (* c cell)) (+ wy (* r cell)) (dec cell) (dec cell)])

(defn preview-rects
  "The rects of the next-piece preview for `type`: its first rotation, drawn at
  half the well's cell size."
  [{:keys [pc preview-x preview-y]} type]
  (map (fn [[cx cy]] [(+ preview-x (* cx pc)) (+ preview-y (* cy pc)) (dec pc) (dec pc)])
       (first (:rots (pieces type)))))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn draw-piece
  "`[type seed']`: a piece uniform over the seven, from the LCG's high bits. The
  low bit alternates on every step, so `(quot seed' 65536)` is what gets used."
  [seed]
  (let [seed' (next-random seed)]
    [(nth piece-types (mod (quot seed' 65536) (count piece-types))) seed']))

(def ^:private start-seed 20260930)

(defn- spawn [type] {:type type
                     :rot 0
                     :x 3
                     :y 0})

(defn- new-game [{:keys [w h]} seed g]
  (let [[ty seed'] (draw-piece seed)
        [nx seed''] (draw-piece seed')]
    {:screen [w h]
     :board (vec (repeat rows (vec (repeat cols nil))))
     :piece (spawn ty)
     :next nx
     :score 0
     :lines 0
     :level 0
     :tick 0
     :over? false
     :seed seed''
     :drag nil
     :gesture g}))

(defn- clear-lines
  "Drop the full rows and refill from the top. `[board cleared-count]`."
  [board]
  (let [kept (filterv (fn [row] (some nil? row)) board)
        cleared (- rows (count kept))]
    [(into (vec (repeat cleared (vec (repeat cols nil)))) kept) cleared]))

(defn- lock-and-next
  "Lock `piece`, clear lines, score, and bring up the next piece. The game is
  over if that piece cannot spawn."
  [s piece]
  (let [ty (:type piece)
        board (reduce (fn [b [c r]] (assoc-in b [r c] ty)) (:board s) (piece-cells piece))
        [board' cleared] (clear-lines board)
        lines (+ (:lines s) cleared)
        next-p (spawn (:next s))
        [nx seed'] (draw-piece (:seed s))]
    (assoc s
           :board board'
           :piece next-p
           :next nx
           :seed seed'
           :tick 0
           :lines lines
           :level (quot lines 10)
           :score (+ (:score s) (nth score-table cleared))
           :over? (not (valid? board' next-p)))))

(defn- hard-drop [board piece]
  (loop [p piece]
    (let [pd (update p :y inc)]
      (if (valid? board pd) (recur pd) p))))

(defn- slide
  "Step `piece` toward column `target`, one column at a time, and stop where the
  next step would not be valid."
  [board piece target]
  (loop [p piece]
    (let [x (:x p)
          p' (cond (< x target) (update p :x inc)
                   (> x target) (update p :x dec)
                   :else nil)]
      (if (and p' (valid? board p')) (recur p') p))))

(defn- anchor
  "The drag anchor for this frame: a fresh one on a `:press` outside Back, the
  existing one while the finger stays down, and none otherwise."
  [{:keys [drag piece]} {:keys [phase position]}]
  (case phase
    :press (when (and position (not (gesture/in-back-region? position)))
             {:x (first position)
              :col (:x piece)})
    :down drag
    nil))

(defn- follow
  "`piece` moved to where the finger's travel from `drag`'s anchor puts it."
  [board piece drag cell input]
  (if (and drag (gesture/down? input))
    (let [dx (- (double (first (get-in input [:pointer :position]))) (:x drag))
          cells (long (Math/floor (+ 0.5 (/ dx cell))))]
      (slide board piece (+ (:col drag) cells)))
    piece))

(defn- play
  "One frame of a live game. The drag moves the piece, then a tap rotates it, then
  either a hard drop or gravity settles it, in the original's order."
  [dims state input event]
  (let [board (:board state)
        drag (anchor state (:pointer input))
        p1 (follow board (:piece state) drag (:cell dims) input)
        rotate? (and (= :tap (:type event))
                     (not (gesture/in-back-region? (:at event))))
        p2 (if rotate?
             (let [p (update p1 :rot inc)] (if (valid? board p) p p1))
             p1)
        hard? (and (= :swipe (:type event)) (= :down (:dir event)))
        tick (inc (:tick state))
        falls? (>= tick (interval (:level state)))
        pd (update p2 :y inc)
        st (assoc state :drag drag)
        locked (cond hard? (lock-and-next st (hard-drop board p2))
                     (and falls? (not (valid? board pd))) (lock-and-next st p2))]
    (cond
      locked
      (assoc locked :drag (when (and drag (gesture/down? input))
                            {:x (double (first (get-in input [:pointer :position])))
                             :col (:x (:piece locked))}))

      falls? (assoc st :piece pd :tick 0)

      :else (assoc st :piece p2 :tick tick))))

(defn advance
  "One frame. Calls `gesture/track` once and stores the result on every path. A
  rotation (the metrics report a different `:screen` than the state was laid
  out for) starts a new game. A finished game waits for a tap outside Back.
  Otherwise the drag, a tap or a swipe down and gravity play the frame."
  [state input]
  (let [dims (dimensions (:metrics input))
        [g event] (gesture/track (:gesture state) input)]
    (cond
      (not= [(:w dims) (:h dims)] (:screen state))
      (new-game dims (:seed state) g)

      (:over? state)
      (if (and (= :tap (:type event))
               (not (gesture/in-back-region? (:at event))))
        (new-game dims (:seed state) g)
        (assoc state :gesture g))

      :else
      (let [after (play dims (assoc state :gesture g) input event)]
        (if (:over? after)
          ;; The one exception to storing `g'`: on the frame the game ends,
          ;; forget the touch. A short tap still down would otherwise lift
          ;; into a `:tap` and restart the game before its result was seen.
          (assoc after :gesture gesture/idle)
          after)))))

(defn- init [{:keys [metrics]}]
  [(new-game (dimensions metrics) start-seed gesture/idle)
   [[:scene/init :tetris]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :tetris]]])

(defn scene []
  {:id :tetris
   :title "Tetris"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
