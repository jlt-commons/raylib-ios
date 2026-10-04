(ns net.b12n.raylib-ios.scenes.minesweeper
  "Minesweeper, played by tap and long press. Ported from raylib-jlt's
  `minesweeper`.

  The original reveals with a left click, flags with a right click and restarts
  on SPACE. Here a `:tap` from `net.b12n.raylib-ios.gesture` reveals the cell under the
  finger's START point, a `:long-press` toggles a flag there, and a `:tap`
  restarts after a mine or a win, unless it lands in the Back region, which is
  the host's. This is the first scene to use the long press. Once one has
  fired, `gesture/track` swallows the release, so flagging a cell never also
  reveals it.

  The rules are the original's: revealing a cell with no mine around it floods
  outward through every such cell and the numbered cells on their edge, a mine
  ends the game, and revealing every cell that is not a mine wins. Two quirks
  of the original are kept on purpose. Its reveal has no flag guard, so a tap
  on a flagged cell opens it (and a flagged mine ends the game); the flag stays
  in the set but is no longer drawn on an open cell. And there is no
  first-tap safety, so the first reveal can be a mine, as it can there.

  The original's board is 16 columns by 12 rows with 30 mines. A phone is
  usually held tall, so the grid is turned with the screen: 12 by 16 in
  portrait and 16 by 12 in landscape, with the same 30 mines. The cell is the
  largest whole number of pixels that fits the board between the Back region
  and a status line at the bottom, and the board is centred. A whole-pixel cell
  means two neighbours share an edge exactly, so a touch there lands in one
  cell only.

  The grid is laid out for one screen, so the state remembers its `:screen` and
  a rotation starts a new game, as Breakout's and Snake's do.

  The original places mines with `rl/get-random-value` and retries on a repeat.
  Here they come from the project's LCG, using the high bits, and the state
  carries the `:seed` so each new game differs.

  Colours are `[r g b a]` vectors, so the namespace stays pure."
  (:require [net.b12n.raylib-ios.gesture :as gesture]))

(def n-mines
  "How many mines a board holds, as in the original."
  30)

(def background-colour [245 245 245 255])
(def grid-colour [80 80 80 255])
(def hidden-colour [130 130 130 255])
(def open-colour [200 200 200 255])
(def number-colour [0 82 172 255])
(def flag-colour [255 161 0 255])
(def mine-colour [0 0 0 255])
(def lose-colour [190 33 55 255])
(def win-colour [0 117 44 255])

(def over-line "BOOM - TAP")
(def won-line "YOU WIN - TAP")

(defn mines-line
  "The status while playing: how many mines are left unflagged."
  [n]
  (str "mines " n))

(defn dimensions
  "The grid and where it sits. A tall screen gets 12 columns by 16 rows and a
  wide one 16 by 12. `:cell` is the largest whole pixel size that fits the
  board in the screen's width and in its height less the status band at the
  bottom AND the Back button's rows at the top. The play area starts at the
  bottom edge of `gesture/back-region`, so no cell sits under Back where the
  player could not see it. `:ox` and `:oy` centre the board in that area. The
  digit's size and offsets are here too, so the draw method does no layout."
  [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        [cols rows] (if (> h w) [12 16] [16 12])
        status-size (max 20 (int (* 0.03 side)))
        band (* 2 status-size)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        play-h (- h top band)
        cell (max 1 (int (min (/ w (double cols)) (/ play-h (double rows)))))
        num-size (max 8 (int (* 0.6 cell)))]
    {:w w
     :h h
     :cols cols
     :rows rows
     :cell cell
     :ox (int (* 0.5 (- w (* cols cell))))
     :oy (+ top (int (* 0.5 (- play-h (* rows cell)))))
     :status-size status-size
     :status-x (int (* 0.04 w))
     :status-y (int (- h (* 1.5 status-size)))
     :num-size num-size
     ;; estimate: 0.6 of the size per character, for raylib's default font.
     :num-dx (int (* 0.5 (- cell (* 0.6 num-size))))
     :num-dy (int (* 0.5 (- cell num-size)))}))

(defn cell-rect
  "`[x y w h]` of the cell at `[c r]`, in whole pixels."
  [{:keys [cell ox oy]} c r]
  [(+ ox (* c cell)) (+ oy (* r cell)) cell cell])

(defn cell-at
  "The `[c r]` under pixel `pt`, or nil off the board. Uses the same half-open
  rectangles as `gesture/in-rect?`, so a point on a shared edge is in exactly
  one cell."
  [{:keys [cols rows]
    :as dims} pt]
  (first (for [c (range cols)
               r (range rows)
               :when (gesture/in-rect? (cell-rect dims c r) pt)]
           [c r])))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn place-mines
  "`[mines seed']`: `n-mines` distinct cells and the next seed. Each mine takes
  two LCG steps, one for the column and one for the row, from the high bits,
  because the low bits alternate. A repeat is skipped, as the original does."
  [{:keys [cols rows]} seed]
  (loop [mines #{}
         seed seed]
    (if (>= (count mines) n-mines)
      [mines seed]
      (let [s1 (next-random seed)
            s2 (next-random s1)]
        (recur (conj mines [(mod (quot s1 65536) cols) (mod (quot s2 65536) rows)])
               s2)))))

(def ^:private start-seed 20260930)

(defn- new-game [{:keys [w h]
                  :as dims} seed g]
  (let [[mines seed'] (place-mines dims seed)]
    {:screen [w h]
     :mines mines
     :revealed #{}
     :flagged #{}
     :over? false
     :won? false
     :seed seed'
     :gesture g}))

(defn- neighbors
  [{:keys [cols rows]} [c r]]
  (for [dc [-1 0 1]
        dr [-1 0 1]
        :let [nc (+ c dc)
              nr (+ r dr)]
        :when (and (not (and (zero? dc) (zero? dr)))
                   (< -1 nc cols)
                   (< -1 nr rows))]
    [nc nr]))

(defn mine-count
  "How many of `cell`'s neighbours are mines."
  [dims mines cell]
  (count (filter mines (neighbors dims cell))))

(defn- reveal
  "The original's reveal: an open cell is left alone, a mine ends the game, and
  anything else opens, flooding outward through zero cells."
  [dims
   {:keys [mines revealed]
    :as st} cell]
  (cond
    (revealed cell) st
    (contains? mines cell) (assoc st :over? true)
    :else
    (loop [stack [cell]
           rev revealed]
      (if (empty? stack)
        (assoc st :revealed rev)
        (let [c (peek stack)
              stack (pop stack)]
          (if (rev c)
            (recur stack rev)
            (let [rev (conj rev c)]
              (if (zero? (mine-count dims mines c))
                (recur (into stack (remove rev (neighbors dims c))) rev)
                (recur stack rev)))))))))

(defn- toggle-flag [{:keys [flagged]
                     :as st} cell]
  (assoc st :flagged (if (flagged cell) (disj flagged cell) (conj flagged cell))))

(defn- won? [dims {:keys [mines revealed]}]
  (= (count revealed) (- (* (:cols dims) (:rows dims)) (count mines))))

(defn finished?
  "Whether the game is over, by a mine or a win."
  [{:keys [over? won?]}]
  (boolean (or over? won?)))

(defn flags-left
  "Mines minus flags on cells still closed. A flag under an open cell is not
  drawn, so it is not counted."
  [{:keys [mines flagged revealed]}]
  (- (count mines) (count (remove revealed flagged))))

(defn advance
  "One frame. Calls `gesture/track` once and stores the result. A rotation (the
  metrics report a different `:screen` than the state was laid out for) starts
  a new game. Otherwise a tap reveals, a long press flags, and a tap restarts a
  finished game unless it lands in the Back region."
  [state input]
  (let [dims (dimensions (:metrics input))
        [g event] (gesture/track (:gesture state) input)
        state (assoc state :gesture g)
        at (:at event)]
    (cond
      (not= [(:w dims) (:h dims)] (:screen state))
      (new-game dims (:seed state) g)

      (finished? state)
      (if (and (= :tap (:type event)) (not (gesture/in-back-region? at)))
        (new-game dims (:seed state) g)
        state)

      (= :tap (:type event))
      (if-let [cl (cell-at dims at)]
        (let [st (reveal dims state cl)]
          (if (and (not (:over? st)) (won? dims st))
            (assoc st :won? true)
            st))
        state)

      (= :long-press (:type event))
      (if-let [cl (cell-at dims at)]
        (toggle-flag state cl)
        state)

      :else state)))

(defn- init [{:keys [metrics]}]
  [(new-game (dimensions metrics) start-seed gesture/idle)
   [[:scene/init :minesweeper]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :minesweeper]]])

(defn scene []
  {:id :minesweeper
   :title "Minesweeper"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
