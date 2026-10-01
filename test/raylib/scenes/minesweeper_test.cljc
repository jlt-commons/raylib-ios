(ns raylib.scenes.minesweeper-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.minesweeper :as s]))

(def m {:screen [1206 2334]})
(def landscape {:screen [2334 1206]})
(def d (s/dimensions m))
(def start (first ((:init (s/scene)) {:metrics m})))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (s/advance state {:metrics metrics
                     :pointer {:phase phase
                               :position position}})))

(defn- centre
  "The pixel at the middle of cell `[c r]`."
  ([cl] (centre d cl))
  ([dm [c r]]
   (let [[x y w h] (s/cell-rect dm c r)]
     [(+ x (quot w 2)) (+ y (quot h 2))])))

(defn- tap [state cl]
  (let [p (centre cl)]
    (-> state (step :press p) (step :release p))))

(defn- long-press
  "Press, hold for `long-press-frames` `:down` frames, then release."
  [state cl]
  (let [p (centre cl)
        held (reduce (fn [st _] (step st :down p))
                     (step state :press p)
                     (range gesture/long-press-frames))]
    (step held :release p)))

(defn- with-mines
  "`start` with exactly `mines` laid, so a test does not depend on the seed."
  [mines]
  (assoc start :mines (set mines)))

(defn- all-cells [{:keys [cols rows]}]
  (for [c (range cols) r (range rows)] [c r]))

(deftest a-tap-reveals-a-cell
  (let [mine-free (with-mines [[0 0]])
        after (tap mine-free [5 5])]
    (is (contains? (:revealed after) [5 5]))
    (is (not (:over? after)))
    (testing "a tap just left of the board does not reveal anything"
      (let [p [(dec (:ox d)) (:oy d)]]
        (is (empty? (:revealed (step (step mine-free :press p) :release p))))))
    (testing "a number cell reveals only itself"
      (let [num (tap (with-mines [[6 6]]) [5 5])]
        (is (= #{[5 5]} (:revealed num)))))))

(deftest revealing-a-zero-floods-its-neighbours
  (let [after (tap (with-mines [[0 0]]) [8 8])
        {:keys [revealed]} after]
    (is (contains? revealed [8 8]))
    (is (contains? revealed [9 9]))
    (testing "up to the number cells beside the mine, and not the mine"
      (is (contains? revealed [1 1]))
      (is (contains? revealed [1 0]))
      (is (not (contains? revealed [0 0]))))
    (testing "every cell but the mine is open, since one mine leaves one region"
      (is (= (dec (* (:cols d) (:rows d))) (count revealed)))))
  (testing "a flood stops at a number"
    (let [after (tap (with-mines [[5 5]]) [0 0])]
      (is (contains? (:revealed after) [4 4]))
      (is (not (contains? (:revealed after) [5 5]))))))

(deftest a-long-press-flags-and-unflags
  (let [flagged (long-press start [3 4])]
    (is (= #{[3 4]} (:flagged flagged)))
    (is (= #{} (:flagged (long-press flagged [3 4]))))
    (testing "a second cell is flagged alongside"
      (is (= #{[3 4] [7 7]} (:flagged (long-press flagged [7 7])))))))

(deftest a-long-press-never-reveals
  (let [cl [3 4]
        p (centre cl)
        mid (reduce (fn [st _] (step st :down p))
                    (step (with-mines [[0 0]]) :press p)
                    (range gesture/long-press-frames))]
    (testing "the flag lands while the finger is still down"
      (is (= #{cl} (:flagged mid)))
      (is (empty? (:revealed mid))))
    (testing "and the release that follows reveals nothing"
      (let [released (step mid :release p)]
        (is (empty? (:revealed released)))
        (is (= #{cl} (:flagged released)))
        (is (not (:over? released))))))
  (testing "even on a mine"
    (let [after (long-press (with-mines [[3 4]]) [3 4])]
      (is (not (:over? after)))
      (is (empty? (:revealed after))))))

(deftest a-tap-on-a-flagged-cell-reveals-it
  ;; The original's reveal has no flag guard, so a left click on a flagged cell
  ;; opens it, and the flag stays in the set but is no longer drawn.
  (let [st (long-press (with-mines [[0 0]]) [5 5])
        after (tap st [5 5])]
    (is (contains? (:revealed after) [5 5]))
    (is (contains? (:flagged after) [5 5])))
  (testing "and on a flagged mine it ends the game"
    (is (:over? (tap (long-press (with-mines [[5 5]]) [5 5]) [5 5])))))

(deftest revealing-a-mine-ends-the-game
  (let [after (tap (with-mines [[5 5] [9 9]]) [5 5])]
    (is (:over? after))
    (is (not (:won? after)))
    (is (not (contains? (:revealed after) [5 5])))
    (testing "a long-press on another cell is ignored once the game is over"
      (let [later (long-press after [8 8])]
        (is (:over? later))
        (is (= (:revealed after) (:revealed later)))
        (is (= (:flagged after) (:flagged later)))))))

(deftest revealing-every-safe-cell-wins
  (let [mines (take s/n-mines (all-cells d))
        safe (remove (set mines) (all-cells d))
        st (with-mines mines)
        ;; Reveal every safe cell directly, leaving the last one for the tap.
        almost (assoc st :revealed (set (butlast safe)))
        last-cell (last safe)]
    (is (= s/n-mines (count mines)))
    (is (not (:won? almost)))
    (let [after (tap almost last-cell)]
      (is (:won? after))
      (is (not (:over? after))))
    (testing "flagging does not win, and a tap on a mine-free board floods to a win"
      (is (:won? (tap (with-mines []) [0 0]))))))

(deftest mines-are-placed-from-the-seed
  (let [a (s/place-mines d 20260930)
        [mines seed'] a]
    (is (= s/n-mines (count mines)))
    (is (every? (fn [[c r]] (and (< -1 c (:cols d)) (< -1 r (:rows d)))) mines))
    (testing "the same seed gives the same mines and the next seed"
      (is (= a (s/place-mines d 20260930))))
    (testing "another seed gives another layout"
      (is (not= mines (first (s/place-mines d 1)))))
    (testing "the seed advances"
      (is (not= 20260930 seed')))
    (testing "the opening state is built from the opening seed"
      (is (= mines (:mines start))))
    (testing "mines are spread over the board, not stacked in one corner"
      (is (> (count (distinct (map first mines))) 4))
      (is (> (count (distinct (map second mines))) 4)))))

(deftest a-tap-restarts-after-game-over
  (let [dead (assoc (with-mines [[5 5]]) :over? true :revealed #{[1 1]} :flagged #{[2 2]})
        after (tap dead [9 9])]
    (is (not (:over? after)))
    (is (empty? (:revealed after)))
    (is (empty? (:flagged after)))
    (is (not= (:mines dead) (:mines after)))
    (testing "a win restarts too"
      (is (not (:won? (tap (assoc start :won? true) [9 9])))))
    (testing "a tap while playing does not restart"
      (let [playing (assoc (with-mines [[0 0]]) :revealed #{[1 1]})]
        (is (contains? (:revealed (tap playing [0 5])) [1 1]))
        (is (= (:mines playing) (:mines (tap playing [0 5]))))))
    (testing "a long press or a lone press does not restart"
      (is (:over? (long-press dead [9 9])))
      (is (:over? (step dead :press (centre [9 9])))))
    (testing "a tap under Back does not restart"
      (let [[bx by bw bh] gesture/back-region]
        (is (:over? (-> dead (step :press [bx by]) (step :release [bx by]))))
        (is (:over? (-> dead
                        (step :press [(dec (+ bx bw)) (dec (+ by bh))])
                        (step :release [(dec (+ bx bw)) (dec (+ by bh))]))))))))

(deftest rotation-starts-a-new-game
  (let [played (assoc (tap (with-mines [[0 0]]) [8 8]) :over? true)
        after (step played landscape :idle nil)
        dl (s/dimensions landscape)]
    (is (= [2334 1206] (:screen after)))
    (is (not (:over? after)))
    (is (empty? (:revealed after)))
    (is (= s/n-mines (count (:mines after))))
    (is (= [16 12] [(:cols dl) (:rows dl)]))
    (testing "the mines are on the new grid"
      (is (every? (fn [[c r]] (and (< -1 c (:cols dl)) (< -1 r (:rows dl)))) (:mines after))))
    (testing "and the new game plays at the new size"
      (let [p (centre dl [15 11])
            tapped (-> after (step landscape :press p) (step landscape :release p))]
        (is (contains? (:revealed tapped) [15 11]))))))

(deftest the-grid-is-turned-to-fit
  (is (= [12 16] [(:cols d) (:rows d)]))
  (is (= [16 12] [(:cols (s/dimensions landscape)) (:rows (s/dimensions landscape))]))
  (is (= 30 s/n-mines)))

(deftest a-point-on-a-shared-edge-lands-in-one-cell
  (let [owners (fn [p] (filter (fn [[c r]]
                                 (gesture/in-rect? (s/cell-rect d c r) p))
                               (all-cells d)))
        [x y cw ch] (s/cell-rect d 3 4)]
    (is (= [[3 4]] (owners [x y])))
    (is (= [[4 5]] (owners [(+ x cw) (+ y ch)])))
    (is (= [[4 4]] (owners [(+ x cw) y])))))

(deftest the-board-fits-the-safe-region
  (doseq [screen [[1206 2334] [2334 1206] [800 450] [450 800]]
          :let [[w h] screen
                dm (s/dimensions {:screen screen})
                {:keys [cols rows cell ox oy status-y status-size]} dm]]
    (testing (str screen)
      (is (pos? cell))
      (is (<= 0 ox))
      (is (<= 0 oy))
      (is (<= (+ ox (* cols cell)) w))
      (is (<= (+ oy (* rows cell)) h))
      (testing "the board ends above the status line"
        (is (<= (+ oy (* rows cell)) status-y)))
      (testing "every cell is inside the screen"
        (doseq [c [0 (dec cols)] r [0 (dec rows)]
                :let [[x y cw ch] (s/cell-rect dm c r)]]
          (is (<= 0 x))
          (is (<= 0 y))
          (is (<= (+ x cw) w))
          (is (<= (+ y ch) h))))
      (testing "the board is centred across the width"
        (is (<= (Math/abs (double (- ox (- w ox (* cols cell))))) 1.0)))
      (is (<= (+ status-y status-size) h)))))

(deftest the-board-starts-below-back
  (let [[bx by bw bh] gesture/back-region]
    (doseq [screen [[1206 2334] [2334 1206]]
            :let [dm (s/dimensions {:screen screen})
                  {:keys [cols rows oy]} dm]]
      (testing (str screen)
        (is (>= oy (+ by bh)))
        (doseq [c (range cols) r (range rows)
                :let [[x y cw ch] (s/cell-rect dm c r)]]
          (is (not (and (< x (+ bx bw)) (< bx (+ x cw))
                        (< y (+ by bh)) (< by (+ y ch))))
              (str "cell " [c r] " overlaps Back")))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen [[1206 2334] [2334 1206]]
          :let [[w h] screen
                [_ by _ bh] gesture/back-region
                dm (s/dimensions {:screen screen})
                {:keys [status-x status-y status-size oy rows cell num-size num-dx num-dy]} dm]]
    (testing (str screen)
      (doseq [line [(s/mines-line s/n-mines) s/over-line s/won-line]
              ;; estimate: 0.6 of the size per character, for raylib's default font.
              :let [line-w (* 0.6 status-size (count line))]]
        (testing line
          (is (<= 0 status-x))
          (is (<= (+ status-x line-w) w))
          (is (<= 0 status-y))
          (is (<= (+ status-y status-size) h))
          (testing "below the board, so clear of Back"
            (is (>= status-y (+ oy (* rows cell))))
            (is (>= status-y (+ by bh))))))
      (testing "a digit sits inside its cell"
        (is (<= 0 num-dx))
        (is (<= 0 num-dy))
        (is (<= (+ num-dx (* 0.6 num-size)) cell))
        (is (<= (+ num-dy num-size) cell))))))

(deftest the-gesture-is-kept-in-state
  (is (= gesture/idle (:gesture start)))
  (is (= [600 1500] (:start (:gesture (step start :press [600 1500]))))))
