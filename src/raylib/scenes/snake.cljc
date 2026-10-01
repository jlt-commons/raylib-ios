(ns raylib.scenes.snake
  "The classic snake, steered by swipes. Ported from raylib-jlt's `snake`.

  The original steers with the arrow keys and restarts on SPACE. Here a
  `:swipe` from `raylib.gesture` turns the snake, and a `:tap` restarts it after
  game over, unless the tap lands in the Back region, which is the host's.

  The rules are the original's: the snake moves one cell every `tick-frames`
  frames, grows by one cell when its head reaches the food, and the game ends
  when the head hits a wall or the snake's own body. A turn that would reverse
  the snake is refused. The original judges that against the direction last
  chosen, which is the one it also moves in, because a key press is a single
  frame. A swipe can arrive twice inside one tick, so the check here is against
  the direction the snake last MOVED in (`:heading`). Otherwise \"up\" then
  \"left\" while heading right would be accepted and the next step would run the
  head into the neck.

  The original's board is 32 columns by 18 rows. A phone is usually held tall,
  so the grid is turned with the screen: 18 by 32 in portrait and 32 by 18 in
  landscape. The cell is the largest square that fits the board above a score
  line, and the board is centred.

  The grid is laid out for one screen, so the state remembers its `:screen` and
  a rotation starts a new game, as Breakout's does.

  The original picks food with `rl/get-random-value` and retries while the cell
  is taken. Here the food comes from the project's LCG and chooses among the
  free cells, so it cannot loop on a crowded board.

  Colours are `[r g b a]` vectors, so the namespace stays pure."
  (:require [raylib.gesture :as gesture]))

(def tick-frames
  "Frames per step. The original's `tick`."
  6)

(def board-colour [30 30 30 255])
(def snake-colour [0 228 48 255])
(def food-colour [230 41 55 255])
(def text-colour [245 245 245 255])

(def over-line "GAME OVER - TAP")

(defn score-line [n] (str "len " n))

(defn dimensions
  "The grid and where it sits. A tall screen gets 18 columns by 32 rows and a
  wide one 32 by 18. `:cell` is the largest square that fits the board in the
  screen's width and in its height less the score band at the bottom. `:ox` and
  `:oy` centre the board in the space above the band."
  [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        [cols rows] (if (> h w) [18 32] [32 18])
        score-size (max 20 (int (* 0.03 side)))
        band (* 2 score-size)
        play-h (- h band)
        cell (min (/ w (double cols)) (/ play-h (double rows)))
        msg-size (max 20 (int (* 0.04 side)))
        ;; estimate: 0.6 of the size per character, for raylib's default font.
        msg-w (* 0.6 msg-size (count over-line))]
    {:w w
     :h h
     :cols cols
     :rows rows
     :cell cell
     :ox (* 0.5 (- w (* cols cell)))
     :oy (* 0.5 (- play-h (* rows cell)))
     :score-size score-size
     :score-x (int (* 0.04 w))
     :score-y (int (- h (* 1.5 score-size)))
     :msg-size msg-size
     :msg-x (max 0 (int (* 0.5 (- w msg-w))))
     :msg-y (int (* 0.5 h))}))

(defn cell-rect
  "`[x y w h]` of the cell at `[c r]`, in pixels."
  [{:keys [cell ox oy]} c r]
  [(+ ox (* c cell)) (+ oy (* r cell)) cell cell])

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn place-food
  "`[food seed']`: a cell not occupied by `snake`, and the next seed. The LCG's
  high bits pick an index among the free cells, because its low bit alternates
  on every step. `food` is nil when the snake fills the board."
  [{:keys [cols rows]} snake seed]
  (let [seed' (next-random seed)
        taken (set snake)
        free (vec (for [c (range cols)
                        r (range rows)
                        :when (not (taken [c r]))]
                    [c r]))]
    [(when (seq free) (nth free (mod (quot seed' 65536) (count free))))
     seed']))

(def ^:private start-seed 20260930)

(defn- new-game [{:keys [w h cols rows]
                  :as dims} seed g]
  (let [cx (quot cols 2)
        cy (quot rows 2)
        snake [[cx cy] [(dec cx) cy] [(- cx 2) cy]]
        [food seed'] (place-food dims snake seed)]
    {:screen [w h]
     :snake snake
     :dir [1 0]
     :heading [1 0]
     :food food
     :dead? false
     :ticks 0
     :seed seed'
     :gesture g}))

(def ^:private swipe-dirs
  {:up [0 -1]
   :down [0 1]
   :left [-1 0]
   :right [1 0]})

(defn- turn
  "`state` with a swipe applied, or unchanged if it would reverse the heading."
  [{:keys [heading]
    :as state} swipe-dir]
  (let [want (swipe-dirs swipe-dir)]
    (if (= want [(- (first heading)) (- (second heading))])
      state
      (assoc state :dir want))))

(defn- step
  "One move of the original's rules."
  [{:keys [cols rows]
    :as dims}
   {:keys [snake dir food seed]
    :as st}]
  (let [[hc hr] (first snake)
        [dc dr] dir
        head [(+ hc dc) (+ hr dr)]
        [nc nr] head
        st (assoc st :heading dir)]
    (cond
      (or (< nc 0) (>= nc cols) (< nr 0) (>= nr rows) (some #{head} snake))
      (assoc st :dead? true)

      (= head food)
      (let [ns (into [head] snake)
            [f seed'] (place-food dims ns seed)]
        (assoc st :snake ns :food f :seed seed'))

      :else (assoc st :snake (into [head] (pop snake))))))

(defn advance
  "One frame. Calls `gesture/track` once and stores the result. A rotation (the
  metrics report a different `:screen` than the state was laid out for) starts
  a new game. Otherwise a swipe turns the snake, a tap restarts a dead one, and
  every `tick-frames` frames the snake moves a cell."
  [state input]
  (let [dims (dimensions (:metrics input))
        [g event] (gesture/track (:gesture state) input)]
    (cond
      (not= [(:w dims) (:h dims)] (:screen state))
      (new-game dims (:seed state) g)

      (:dead? state)
      (if (and (= :tap (:type event))
               (not (gesture/in-back-region? (:at event))))
        (new-game dims (:seed state) g)
        (assoc state :gesture g))

      :else
      (let [st (assoc (if (= :swipe (:type event)) (turn state (:dir event)) state)
                      :gesture g)
            ticks (inc (:ticks st))]
        (if (>= ticks tick-frames)
          (step dims (assoc st :ticks 0))
          (assoc st :ticks ticks))))))

(defn- init [{:keys [metrics]}]
  [(new-game (dimensions metrics) start-seed gesture/idle)
   [[:scene/init :snake]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :snake]]])

(defn scene []
  {:id :snake
   :title "Snake"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
