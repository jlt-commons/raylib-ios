(ns raylib.scenes.snake-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.snake :as s]))

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

(defn- idle [state] (step state :idle nil))

(defn- frames [state n] (nth (iterate idle state) n))

(defn- tick
  "Run one whole tick, which is `s/tick-frames` idle frames."
  [state]
  (frames state s/tick-frames))

(def ^:private swipe-ends
  {:up [[600 1500] [600 1200]]
   :down [[600 1500] [600 1800]]
   :left [[600 1500] [300 1500]]
   :right [[600 1500] [900 1500]]})

(defn- swipe
  "A whole swipe in `dir`: press, one drag frame, release. The event arrives on
  the release frame."
  [state dir]
  (let [[from to] (swipe-ends dir)]
    (-> state (step :press from) (step :down to) (step :release to))))

(defn- tap [state pos]
  (-> state (step :press pos) (step :release pos)))

(defn- head [state] (first (:snake state)))

(deftest a-swipe-turns-the-snake
  (let [up (swipe start :up)]
    (is (= [0 -1] (:dir up)))
    (testing "and the next tick moves the head up"
      (let [[c r] (head start)]
        (is (= [c (dec r)] (head (tick up))))))
    (testing "each direction"
      (is (= [0 1] (:dir (swipe start :down))))
      (is (= [1 0] (:dir (swipe (swipe start :up) :right)))))
    (testing "a swipe to the left works once the snake is heading up"
      (is (= [-1 0] (:dir (swipe (tick (swipe start :up)) :left)))))))

(deftest a-reverse-swipe-is-ignored
  (is (= [1 0] (:dir (swipe start :left))))
  (testing "against the way the snake last MOVED, not the way it is queued to"
    ;; Heading right, a swipe up then a swipe left inside one tick: left would
    ;; reverse the heading, so up stands. Judged against the queued direction
    ;; alone it would be accepted and the next step would run into the neck.
    (let [after (-> start (swipe :up) (swipe :left))]
      (is (= [0 -1] (:dir after)))
      (is (not (:dead? (tick after)))))))

(deftest the-snake-advances-every-tick
  (let [[c r] (head start)
        five (frames start (dec s/tick-frames))]
    (is (= 6 s/tick-frames))
    (is (= (:snake start) (:snake five)))
    (is (= [(inc c) r] (head (idle five))))
    (testing "a second tick moves it one more cell"
      (is (= [(+ c 2) r] (head (tick (tick start))))))
    (testing "the tail follows, so the length is unchanged"
      (is (= 3 (count (:snake (tick start))))))))

(deftest eating-grows-the-snake
  (let [[c r] (head start)
        fed (assoc start :food [(inc c) r])
        after (tick fed)]
    (is (= 4 (count (:snake after))))
    (is (= [(inc c) r] (head after)))
    (is (not (some #{(:food after)} (:snake after))))
    (is (not= (:food fed) (:food after)))
    (testing "the old tail stays, because the snake grew"
      (is (= (:snake fed) (vec (rest (:snake after))))))))

(deftest hitting-a-wall-ends-the-game
  (let [{:keys [cols rows]} d]
    (doseq [[label snake dir] [["right" [[(dec cols) 5] [(- cols 2) 5] [(- cols 3) 5]] [1 0]]
                               ["left" [[0 5] [1 5] [2 5]] [-1 0]]
                               ["top" [[5 0] [5 1] [5 2]] [0 -1]]
                               ["bottom" [[5 (dec rows)] [5 (- rows 2)] [5 (- rows 3)]] [0 1]]]
            :let [st (assoc start :snake snake :dir dir :heading dir :food [9 9])
                  after (tick st)]]
      (testing label
        (is (:dead? after))
        (is (= snake (:snake after)))))
    (testing "and one cell short of the wall is still alive"
      (is (not (:dead? (tick (assoc start :snake [[(- cols 2) 5] [(- cols 3) 5] [(- cols 4) 5]]))))))))

(deftest hitting-itself-ends-the-game
  ;; Heading down into [5 6], which the body occupies.
  (let [st (assoc start
                  :snake [[5 5] [6 5] [6 6] [5 6] [4 6]]
                  :dir [0 1] :heading [0 1] :food [9 9])
        after (tick st)]
    (is (:dead? after))
    (is (= (:snake st) (:snake after)))))

(deftest a-dead-snake-stays-still
  (let [dead (tick (assoc start :snake [[(dec (:cols d)) 5] [(- (:cols d) 2) 5] [(- (:cols d) 3) 5]]))]
    (is (:dead? dead))
    (is (= dead (frames dead 20)))))

(deftest a-tap-restarts-after-game-over
  (let [dead (assoc start :dead? true :snake [[1 1] [1 2]] :dir [0 -1] :heading [0 -1])
        after (tap dead [600 1400])]
    (is (not (:dead? after)))
    (is (= (:snake start) (:snake after)))
    (is (= [1 0] (:dir after)))
    (testing "a tap while alive does nothing"
      (let [alive (assoc start :snake [[1 1] [1 2]])]
        (is (= (:snake alive) (:snake (tap alive [600 1400]))))))
    (testing "a press alone, or a swipe, does not restart"
      (is (:dead? (step dead :press [600 1400])))
      (is (:dead? (swipe dead :up))))))

(deftest a-tap-under-back-does-not-restart
  (let [dead (assoc start :dead? true)]
    (is (:dead? (tap dead [100 60])))
    (is (:dead? (tap dead [399 119])))
    (testing "just outside it does restart"
      (is (not (:dead? (tap dead [400 119]))))
      (is (not (:dead? (tap dead [100 120])))))))

(deftest food-never-lands-on-the-snake
  (testing "the one free cell is the only choice"
    (let [{:keys [cols rows]} d
          all (vec (for [c (range cols) r (range rows)] [c r]))
          free [7 11]
          snake (vec (remove #{free} all))]
      (doseq [seed [0 1 12345 2147483647]]
        (is (= free (first (s/place-food d snake seed)))))))
  (testing "across many seeds on an open board"
    (let [snake (:snake start)]
      (loop [seed 1 n 0]
        (when (< n 200)
          (let [[f seed'] (s/place-food d snake seed)]
            (is (not (some #{f} snake)))
            (is (every? true? (map < f [(:cols d) (:rows d)])))
            (recur seed' (inc n)))))))
  (testing "a full board has nowhere to put it"
    (let [{:keys [cols rows]} d]
      (is (nil? (first (s/place-food d (vec (for [c (range cols) r (range rows)] [c r])) 1)))))))

(deftest rotation-starts-a-new-game
  (let [long-snake (assoc start :snake [[12 12] [11 12] [10 12] [9 12] [8 12]]
                          :dir [0 -1] :heading [0 -1] :dead? true)
        after (step long-snake landscape :idle nil)
        fresh (first ((:init (s/scene)) {:metrics landscape}))
        dl (s/dimensions landscape)]
    (is (= [2334 1206] (:screen after)))
    (is (= (:snake fresh) (:snake after)))
    (is (= [1 0] (:dir after)))
    (is (not (:dead? after)))
    (is (= 32 (:cols dl)))
    (testing "the food is on the new grid"
      (let [[fc fr] (:food after)]
        (is (< -1 fc (:cols dl)))
        (is (< -1 fr (:rows dl)))))
    (testing "and the head sits mid-board, not at the old coordinates"
      (is (= [16 9] (head after))))))

(deftest the-grid-is-turned-to-fit
  (is (= [18 32] [(:cols d) (:rows d)]))
  (is (= [32 18] [(:cols (s/dimensions landscape)) (:rows (s/dimensions landscape))])))

(deftest the-board-fits-the-safe-region
  (doseq [screen [[1206 2334] [2334 1206] [800 450] [450 800]]
          :let [[w h] screen
                dm (s/dimensions {:screen screen})
                {:keys [cols rows cell ox oy score-y score-size]} dm]]
    (testing (str screen)
      (is (pos? cell))
      (is (<= 0 ox))
      (is (<= 0 oy))
      (is (<= (+ ox (* cols cell)) w))
      (is (<= (+ oy (* rows cell)) h))
      (testing "the board ends above the score line"
        (is (<= (+ oy (* rows cell)) score-y)))
      (testing "every cell, food and snake alike, is inside the screen"
        (doseq [c [0 (dec cols)] r [0 (dec rows)]
                :let [[x y cw ch] (s/cell-rect dm c r)]]
          (is (<= 0 x))
          (is (<= 0 y))
          (is (<= (+ x cw) w))
          (is (<= (+ y ch) h))))
      (testing "the board is centred across the width"
        (is (<= (Math/abs (double (- ox (- w ox (* cols cell))))) 1.0)))
      (is (<= (+ score-y score-size) h)))))

(deftest the-board-starts-below-back
  (let [[_ by _ bh] gesture/back-region]
    (doseq [screen [[1206 2334] [2334 1206]]
            :let [dm (s/dimensions {:screen screen})
                  {:keys [cols rows oy]} dm]]
      (testing (str screen)
        (is (>= oy (+ by bh)))
        (is (>= oy 120))
        (doseq [c (range cols) r (range rows)
                :let [[x y cw ch] (s/cell-rect dm c r)]]
          (is (not (and (< x (+ 0 400)) (< 0 (+ x cw))
                        (< y (+ by bh)) (< by (+ y ch))))
              (str "cell " [c r] " overlaps Back")))))))

(deftest the-start-is-on-the-board
  (doseq [screen [[1206 2334] [2334 1206]]
          :let [st (first ((:init (s/scene)) {:metrics {:screen screen}}))
                dm (s/dimensions {:screen screen})]]
    (doseq [[c r] (conj (:snake st) (:food st))]
      (is (< -1 c (:cols dm)))
      (is (< -1 r (:rows dm))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen [[1206 2334] [2334 1206]]
          :let [[w h] screen
                dm (s/dimensions {:screen screen})
                {:keys [score-x score-y score-size msg-x msg-y msg-size]} dm
                ;; estimate: 0.6 of the size per character, for raylib's default font.
                score-w (* 0.6 score-size (count (s/score-line 999)))
                msg-w (* 0.6 msg-size (count s/over-line))]]
    (testing (str screen)
      (is (<= 0 score-x))
      (is (<= (+ score-x score-w) w))
      (is (<= 0 score-y))
      (is (<= (+ score-y score-size) h))
      (is (<= 0 msg-x))
      (is (<= (+ msg-x msg-w) w))
      (is (<= 0 msg-y))
      (is (<= (+ msg-y msg-size) h)))))

(deftest the-gesture-is-kept-in-state
  (is (= gesture/idle (:gesture start)))
  (is (= [600 1500] (:start (:gesture (step start :press [600 1500]))))))
