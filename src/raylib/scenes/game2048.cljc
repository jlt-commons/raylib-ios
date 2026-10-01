(ns raylib.scenes.game2048
  "2048, played by swipe. Ported from raylib-jlt's `game_2048`.

  The original slides with the arrow keys and restarts on SPACE. Here a
  `:swipe` from `raylib.gesture` slides the board, and a `:tap` restarts it once
  the game is stuck or won, unless the tap lands in the Back region, which is
  the host's.

  The rules are the original's. Slide and merge is one pure function over a row,
  reused for all four directions by reversing and transposing the rows. A move
  counts only if it changes the board (`try-move` compares the new board with the
  old one, not the score), and only then does a tile spawn: a 2, nine times in
  ten, otherwise a 4, in a random empty cell. The game is stuck when no direction
  changes the board. The original has no win check, so reaching 2048 is added
  here as the brief asks: a board holding a 2048 is `:won?` and takes no more
  moves until a tap restarts it. Since play stops at the first 2048, no tile
  above 2048 appears in play, but the text sizing handles any digit count.

  The original draws a 800 by 450 board at fixed pixels. Here the board is a
  centred square, `(* 0.9 (min w play-h))`, where `play-h` is the screen below
  the Back region and above the score line. Nothing in the state depends on the
  screen, so a rotation only re-lays out the board and the game goes on.

  The original spawns with `rl/get-random-value`. Here spawning uses the
  project's LCG, taking the high bits, because its low bit alternates.

  Colours are `[r g b a]` vectors, so the namespace stays pure."
  (:require [raylib.gesture :as gesture]))

(def win-tile 2048)

(def background-colour [187 173 160 255])
(def text-colour [80 80 80 255])
(def score-colour [245 245 245 255])

(def tile-colours
  "The original's `tile-colors`. A value past 2048 uses 2048's colour, as the
  original does."
  {0 [205 193 180 255]
   2 [238 228 218 255]
   4 [237 224 200 255]
   8 [242 177 121 255]
   16 [245 149 99 255]
   32 [246 124 95 255]
   64 [246 94 59 255]
   128 [237 207 114 255]
   256 [237 204 97 255]
   512 [237 200 80 255]
   1024 [237 197 63 255]
   2048 [237 194 46 255]})

(defn tile-colour [v] (get tile-colours v (get tile-colours 2048)))

(def over-line "GAME OVER - TAP")
(def won-line "YOU WIN - TAP")

(defn score-line [n] (str "score " n))

;; --- the pure rules ------------------------------------------------------------

(defn- merge-row
  "`[out score]` for a row with its zeros removed: equal neighbours merge once."
  [row]
  (loop [in row
         out []
         score 0]
    (cond
      (empty? in) [out score]
      (= (count in) 1) [(conj out (first in)) score]
      (= (first in) (second in)) (recur (drop 2 in)
                                        (conj out (* 2 (first in)))
                                        (+ score (* 2 (first in))))
      :else (recur (rest in) (conj out (first in)) score))))

(defn slide-row
  "`[row' gain]` for one row slid to the left."
  [row]
  (let [[merged score] (merge-row (vec (remove zero? row)))]
    [(vec (take 4 (concat merged (repeat 0)))) score]))

(defn- rows [board] (mapv vec (partition 4 board)))
(defn- from-rows [rs] (vec (apply concat rs)))
(defn- transpose [rs] (apply mapv vector rs))
(defn- revrows [rs] (mapv (fn [r] (vec (reverse r))) rs))

(defn- slide-rows [rs]
  (let [results (mapv slide-row rs)]
    [(mapv first results) (reduce + (mapv second results))]))

(defn move
  "`[board' gain]`: the 16-cell `board` slid in `dir`, without spawning."
  [board dir]
  (let [rs (rows board)]
    (case dir
      :left (let [[nr gain] (slide-rows rs)]
              [(from-rows nr) gain])
      :right (let [[nr gain] (slide-rows (revrows rs))]
               [(from-rows (revrows nr)) gain])
      :up (let [[nr gain] (slide-rows (transpose rs))]
            [(from-rows (transpose nr)) gain])
      :down (let [[nr gain] (slide-rows (revrows (transpose rs)))]
              [(from-rows (transpose (revrows nr))) gain]))))

(defn stuck?
  "True when no direction changes `board`."
  [board]
  (every? (fn [dir] (= board (first (move board dir)))) [:left :right :up :down]))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn spawn
  "`[board' seed']`: a 2 or a 4 in a random empty cell. The board is unchanged
  when it has no empty cell."
  [board seed]
  (let [empties (filterv (fn [i] (zero? (nth board i))) (range 16))
        s1 (next-random seed)
        s2 (next-random s1)]
    (if (empty? empties)
      [board seed]
      [(assoc board
              (nth empties (mod (quot s1 65536) (count empties)))
              (if (< (mod (quot s2 65536) 10) 9) 2 4))
       s2])))

(defn try-move
  "`state` after sliding in `dir`. A move that leaves the board as it was changes
  nothing at all: no spawn, no score, no seed use."
  [{:keys [board score seed]
    :as state} dir]
  (let [[nb gain] (move board dir)]
    (if (= nb board)
      state
      (let [[sb seed'] (spawn nb seed)]
        (assoc state
               :board sb
               :score (+ score gain)
               :seed seed'
               :won? (boolean (some #(>= % win-tile) sb)))))))

(defn finished?
  "Whether play has stopped: won, or stuck."
  [{:keys [board won?]}]
  (boolean (or won? (stuck? board))))

(def ^:private start-seed 20260930)

(defn- new-game [seed g]
  (let [[b1 s1] (spawn (vec (repeat 16 0)) seed)
        [b2 s2] (spawn b1 s1)]
    {:board b2
     :score 0
     :won? false
     :seed s2
     :gesture g}))

;; --- layout --------------------------------------------------------------------

(defn dimensions
  "Where everything sits. The play area runs from the bottom edge of
  `gesture/back-region` to the score band at the bottom of the screen, so no
  tile can sit under Back. The board is a square of `(* 0.9 (min w play-h))`
  centred in that area. Text sizes and positions are here too, so the tests can
  check them."
  [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        score-size (max 20 (int (* 0.03 side)))
        band (* 2 score-size)
        play-h (- h top band)
        board-side (* 0.9 (min w play-h))
        cell (/ board-side 4.0)]
    {:w w
     :h h
     :play-h play-h
     :board-side board-side
     :board-x (* 0.5 (- w board-side))
     :board-y (+ top (* 0.5 (- play-h board-side)))
     :cell cell
     :gap (* 0.06 cell)
     :score-size score-size
     :score-x (int (* 0.04 w))
     :score-y (int (- h (* 1.5 score-size)))
     :msg-size score-size
     :msg-y (int (- h (* 1.5 score-size)))
     :msg-right (int (* 0.96 w))}))

(defn msg-x
  "Left edge of `line`, which sits against the right margin of the score band."
  [{:keys [msg-size msg-right]} line]
  ;; estimate: 0.6 of the size per character, for raylib's default font.
  (max 0 (int (- msg-right (* 0.6 msg-size (count line))))))

(defn tile-rect
  "`[x y w h]` of the tile at column `c`, row `r`, inset by half a gap on each
  side of its cell."
  [{:keys [board-x board-y cell gap]} c r]
  (let [t (- cell gap)]
    [(+ board-x (* c cell) (* 0.5 gap)) (+ board-y (* r cell) (* 0.5 gap)) t t]))

(defn tile-text
  "`{:x :y :size}` for the number `v` centred in the tile whose top-left is
  `[tx ty]`. The size is half the tile for short numbers, shrunk so the text
  stays within 90 percent of the tile width at 0.6 of the size per character."
  [{:keys [cell gap]} tx ty v]
  (let [t (- cell gap)
        n (count (str v))
        size (max 1 (int (min (* 0.5 t) (/ (* 0.9 t) (* 0.6 n)))))
        tw (* 0.6 size n)]
    {:size size
     :x (int (+ tx (* 0.5 (- t tw))))
     :y (int (+ ty (* 0.5 (- t size))))}))

;; --- the scene -----------------------------------------------------------------

(defn advance
  "One frame. Calls `gesture/track` once and stores the result. While playing, a
  swipe slides the board. Once finished, a tap outside Back restarts."
  [state input]
  (let [[g event] (gesture/track (:gesture state) input)
        st (assoc state :gesture g)]
    (if (finished? st)
      (if (and (= :tap (:type event))
               (not (gesture/in-back-region? (:at event))))
        (new-game (:seed st) g)
        st)
      (if (= :swipe (:type event))
        (try-move st (:dir event))
        st))))

(defn- init [_]
  [(new-game start-seed gesture/idle)
   [[:scene/init :game2048]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :game2048]]])

(defn scene []
  {:id :game2048
   :title "2048"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
