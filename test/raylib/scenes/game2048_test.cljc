(ns raylib.scenes.game2048-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.game2048 :as g]))

(def m {:screen [1206 2334]})
(def landscape {:screen [2334 1206]})
(def start (first ((:init (g/scene)) {:metrics m})))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (g/advance state {:metrics metrics
                     :pointer {:phase phase
                               :position position}})))

(def ^:private swipe-ends
  {:up [[600 1500] [600 1200]]
   :down [[600 1500] [600 1800]]
   :left [[600 1500] [300 1500]]
   :right [[600 1500] [900 1500]]})

(defn- swipe [state dir]
  (let [[from to] (swipe-ends dir)]
    (-> state (step :press from) (step :down to) (step :release to))))

(defn- tap [state pos]
  (-> state (step :press pos) (step :release pos)))

(defn- tiles [board] (count (remove zero? board)))

(def ^:private stuck-board
  [2 4 2 4
   4 2 4 2
   2 4 2 4
   4 2 4 2])

(deftest slide-and-merge-match-the-original
  (is (= [[4 4 0 0] 8] (g/slide-row [2 2 2 2])))
  (testing "no double merge"
    (is (= [[4 4 0 0] 4] (g/slide-row [2 2 4 0]))))
  (is (= [[2 4 0 0] 0] (g/slide-row [2 0 4 0])))
  (is (= [[8 0 0 0] 8] (g/slide-row [0 0 4 4])))
  (is (= [[0 0 0 0] 0] (g/slide-row [0 0 0 0]))))

(deftest each-direction-moves-the-board
  (let [board [2 0 0 2
               0 0 0 0
               0 4 0 0
               0 0 0 0]]
    (is (= [4 0 0 0
            0 0 0 0
            4 0 0 0
            0 0 0 0] (first (g/move board :left))))
    (is (= [0 0 0 4
            0 0 0 0
            0 0 0 4
            0 0 0 0] (first (g/move board :right))))
    (is (= [2 4 0 2
            0 0 0 0
            0 0 0 0
            0 0 0 0] (first (g/move board :up))))
    (is (= [0 0 0 0
            0 0 0 0
            0 0 0 0
            2 4 0 2] (first (g/move board :down))))
    (is (= 4 (second (g/move board :left))))))

(deftest a-move-that-changes-nothing-spawns-nothing
  (let [packed (assoc start :board [2 4 8 16
                                    0 0 0 0
                                    0 0 0 0
                                    0 0 0 0])
        after (swipe packed :left)]
    (is (= (:board packed) (:board after)))
    (is (= (:seed packed) (:seed after)))
    (is (= (:score packed) (:score after)))))

(deftest a-move-spawns-one-tile
  (let [b [2 0 0 0
           0 0 0 0
           0 0 0 0
           0 0 0 4]
        st (assoc start :board b)
        after (swipe st :right)
        moved (first (g/move b :right))]
    (is (= (inc (tiles moved)) (tiles (:board after))))
    (testing "every existing tile stays, and the new one is a 2 or a 4"
      (is (every? (fn [i] (or (zero? (nth moved i))
                              (= (nth moved i) (nth (:board after) i))))
                  (range 16)))
      (let [added (filter (fn [i] (zero? (nth moved i))) (range 16))
            new-tiles (remove zero? (map #(nth (:board after) %) added))]
        (is (= 1 (count new-tiles)))
        (is (#{2 4} (first new-tiles)))))
    (testing "the score gains the merges"
      (is (= 8 (:score (swipe (assoc st :board [4 4 0 0 0 0 0 0 0 0 0 0 0 0 0 0]) :left)))))))

(deftest reaching-2048-wins
  (let [st (assoc start :board [1024 1024 0 0 0 0 0 0 0 0 0 0 0 0 0 0])
        won (swipe st :left)]
    (is (not (:won? start)))
    (is (:won? won))
    (is (g/finished? won))
    (testing "a won game ignores swipes"
      (is (= (:board won) (:board (swipe won :down)))))
    (testing "and a tap restarts it"
      (let [again (tap won [600 1400])]
        (is (not (:won? again)))
        (is (= 2 (tiles (:board again))))))))

(deftest a-full-board-with-no-merges-is-stuck
  (is (g/stuck? stuck-board))
  (is (not (g/stuck? (assoc stuck-board 15 4))))
  (is (not (g/stuck? (assoc stuck-board 0 0))))
  (is (not (g/stuck? (:board start)))))

(deftest a-tap-restarts-when-stuck
  (let [st (assoc start :board stuck-board :score 99)
        after (tap st [600 1400])]
    (is (g/finished? st))
    (is (= 2 (tiles (:board after))))
    (is (zero? (:score after)))
    (testing "a swipe on a stuck board changes nothing"
      (is (= stuck-board (:board (swipe st :left)))))
    (testing "a tap under Back does not"
      (is (= stuck-board (:board (tap st [100 60])))))
    (testing "a tap while playing does nothing"
      (is (= (:board start) (:board (tap start [600 1400])))))))

(deftest rotation-keeps-the-board
  (let [st (assoc start :board [2 4 8 16 0 0 0 0 0 0 0 0 0 0 0 2] :score 12)
        after (step st landscape :idle nil)]
    (is (= (:board st) (:board after)))
    (is (= 12 (:score after)))
    (is (not= (:board-side (g/dimensions m)) (:board-side (g/dimensions landscape))))))

(deftest the-board-starts-below-back
  (let [[_ by _ bh] gesture/back-region]
    (doseq [screen [[1206 2334] [2334 1206]]
            :let [[w h] screen
                  dm (g/dimensions {:screen screen})
                  {:keys [board-x board-y board-side]} dm]]
      (testing (str screen)
        (is (>= board-y (+ by bh)))
        (is (>= board-y 120))
        (is (<= 0 board-x))
        (is (<= (+ board-x board-side) w))
        (is (<= (+ board-y board-side) h))
        (doseq [c (range 4) r (range 4)
                :let [[x y tw th] (g/tile-rect dm c r)]]
          (is (not (and (< x 400) (< 0 (+ x tw))
                        (< y (+ by bh)) (< by (+ y th))))
              (str "tile " [c r] " overlaps Back")))))))

(deftest the-board-is-the-brief-size
  (let [dm (g/dimensions m)]
    (is (= (* 0.9 (min 1206 (:play-h dm))) (:board-side dm)))))

(deftest tile-text-fits-its-tile
  (doseq [screen [[1206 2334] [2334 1206] [800 450]]
          :let [dm (g/dimensions {:screen screen})]
          v [2 4 16 128 1024 2048 4096 131072]
          :let [[tx ty tw th] (g/tile-rect dm 0 0)
                {:keys [x y size]} (g/tile-text dm tx ty v)
                ;; estimate: 0.6 of the size per character, for raylib's default font.
                text-w (* 0.6 size (count (str v)))]]
    (testing (str screen " " v)
      (is (pos? size))
      (is (<= tx x))
      (is (<= (+ x text-w) (+ tx tw)))
      (is (<= ty y))
      (is (<= (+ y size) (+ ty th))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen [[1206 2334] [2334 1206]]
          :let [[w h] screen
                dm (g/dimensions {:screen screen})
                {:keys [score-x score-y score-size msg-y msg-size]} dm
                ;; estimate: 0.6 of the size per character, for raylib's default font.
                score-w (* 0.6 score-size (count (g/score-line 999999)))]]
    (testing (str screen)
      (is (<= 0 score-x))
      (is (<= (+ score-x score-w) w))
      (is (<= 0 score-y))
      (is (<= (+ score-y score-size) h))
      (doseq [line [g/over-line g/won-line]
              :let [msg-w (* 0.6 msg-size (count line))]]
        (is (<= 0 (g/msg-x dm line)))
        (is (<= (+ (g/msg-x dm line) msg-w) w)))
      (is (<= 0 msg-y))
      (is (<= (+ msg-y msg-size) h))
      (testing "the message does not sit on the score"
        (is (> (g/msg-x dm g/over-line) (+ score-x score-w)))))))

(deftest the-gesture-is-kept-in-state
  (is (= gesture/idle (:gesture start)))
  (is (= [600 1500] (:start (:gesture (step start :press [600 1500]))))))
