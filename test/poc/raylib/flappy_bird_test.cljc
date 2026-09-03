(ns poc.raylib.flappy-bird-test
  (:require [clojure.test :refer [deftest is testing]]
            [poc.raylib.flappy-bird :as flappy]))

(def metrics {:screen [1080 2400]})

(defn input
  ([dt] (input dt :idle))
  ([dt phase]
   {:metrics metrics
    :delta-seconds dt
    :pointer {:phase phase}
    :keyboard {:activate? false}}))

(defn advance [state partitions]
  (reduce (fn [current dt] (flappy/step current (input dt))) state partitions))

(deftest deterministic-seeded-fixture-test
  (let [game (flappy/new-game metrics 42)
        dims (flappy/dimensions metrics)]
    (is (= game (flappy/new-game metrics 42)))
    (is (not= (mapv :gap (:pipes game))
              (mapv :gap (:pipes (flappy/new-game metrics 43)))))
    (is (< (:x (first (:pipes game))) (:width dims)))
    (is (> (+ (:x (first (:pipes game))) (:pipe-width dims)) 0.0))))

(deftest equivalent-elapsed-time-fixtures-test
  (let [initial (flappy/new-game metrics 9)
        thirty (advance initial (repeat 3 (/ 1.0 30.0)))
        sixty (advance initial (repeat 6 (/ 1.0 60.0)))
        variable (advance initial [0.02 0.03 0.02 0.03])]
    (testing "constant acceleration and scrolling remain stable across partitions"
      (doseq [state [thirty sixty variable]]
        (is (false? (:over? state))))
      (is (< (Math/abs (- (:y thirty) (:y sixty))) 0.00001))
      (is (< (Math/abs (- (:y thirty) (:y variable))) 0.00001))
      (is (< (Math/abs (- (get-in thirty [:pipes 0 :x])
                          (get-in sixty [:pipes 0 :x])))
             0.00001)))))

(deftest press-edge-and-restart-test
  (let [initial (flappy/new-game metrics 7)
        pressed (flappy/step initial (input (/ 1.0 60.0) :press))
        held (flappy/step pressed (input (/ 1.0 60.0) :down))
        over (assoc pressed :over? true)
        restarted (flappy/step over (input (/ 1.0 60.0) :press))]
    (testing "only an edge starts a flap; a held contact follows normal gravity"
      (is (= (:flap-velocity (flappy/dimensions metrics))
             (- (:vy pressed) (* (:gravity (flappy/dimensions metrics)) (/ 1.0 60.0)))))
      (is (> (:vy held) (:vy pressed))))
    (testing "a game-over press deterministically restarts once"
      (is (false? (:over? restarted)))
      (is (= 0 (:score restarted)))
      (is (= (:pipes (flappy/new-game metrics (:seed over))) (:pipes restarted))))))

(deftest collision-score-and-delta-clamp-test
  (let [dims (flappy/dimensions metrics)
        base (flappy/new-game metrics 3)
        scored-state (assoc base :pipes [{:x (- (:bird-x dims) (:pipe-width dims) 1.0)
                                         :gap 900.0 :scored? false}])
        scored (flappy/step scored-state (input (/ 1.0 60.0)))
        collided (flappy/step (assoc base :y 3.0 :vy -50.0)
                              (input (/ 1.0 60.0)))
        capped (flappy/step base (input 9.0))]
    (is (= 1 (:score scored)))
    (is (true? (:over? collided)))
    (is (= flappy/max-delta-seconds (:elapsed capped)))
    (is (< (:elapsed capped) 1.0))))
