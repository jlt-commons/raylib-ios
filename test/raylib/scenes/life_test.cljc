(ns raylib.scenes.life-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.life :as l]))

(def metrics {:screen [1206 2622]})

(deftest the-grid-is-derived-from-the-screen
  (let [{:keys [cell cols rows]} (l/dimensions metrics)]
    (testing "cells are square and the grid fits inside the screen"
      (is (<= (* cols cell) 1206))
      (is (<= (* rows cell) 2622)))
    (testing "and lands near the target, which is what keeps it in budget"
      (is (< 3000 (* cols rows) 5500)))))

(deftest the-classic-patterns-behave
  (let [dims {:cols 20 :rows 20 :cell 10}]
    (testing "a block is a still life"
      (let [block #{[5 5] [5 6] [6 5] [6 6]}]
        (is (= block (l/step dims block)))))
    (testing "a blinker has period two"
      (let [blinker #{[5 4] [5 5] [5 6]}
            once (l/step dims blinker)]
        (is (= #{[4 5] [5 5] [6 5]} once))
        (is (= blinker (l/step dims once)))))
    (testing "a lone cell dies and empty stays empty"
      (is (= #{} (l/step dims #{[3 3]})))
      (is (= #{} (l/step dims #{}))))))

(deftest the-grid-wraps
  (testing "a blinker on the edge wraps rather than falling off, which is what
            makes this a torus and not a board with walls"
    (let [dims {:cols 10 :rows 10 :cell 10}
          edge #{[0 0] [9 0] [1 0]}]
      (is (= edge (l/step dims (l/step dims edge)))))))

(deftest spawning-is-deterministic-and-about-the-right-density
  (let [dims (l/dimensions metrics)
        [a _] (l/spawn dims l/default-seed)
        [b _] (l/spawn dims l/default-seed)
        [c _] (l/spawn dims 999)]
    (is (= a b) "same seed, same soup")
    (is (not= a c) "different seed, different soup")
    (testing "density is near fill-percent, so the opening frame is predictable"
      (let [ratio (/ (double (count a)) (* (:cols dims) (:rows dims)))]
        (is (< 0.22 ratio 0.34) (str "got " ratio))))))

(deftest a-dead-or-stuck-board-restarts
  (testing "empty counts as stalled"
    (is (l/stalled? '(0 5 5 5))))
  (testing "so does six generations at the same population"
    (is (l/stalled? '(12 12 12 12 12 12))))
  (testing "but a changing population does not"
    (is (not (l/stalled? '(12 13 12 14 12 15)))))
  (testing "and neither does a short history, which would fire on startup"
    (is (not (l/stalled? '(12 12 12))))))

(deftest advancing-only-steps-on-a-tick
  (let [dims (l/dimensions metrics)
        [live seed] (l/spawn dims l/default-seed)
        s0 {:live live :seed seed :t 0 :history () :generation 0}
        s1 (l/advance s0 metrics)]
    (testing "the first tick is not a generation, it is one frame of six"
      (is (= 1 (:t s1)))
      (is (= (:live s0) (:live s1))))
    (testing "and the sixth one is"
      (let [s6 (nth (iterate #(l/advance % metrics) s0) l/ticks-per-generation)]
        (is (= l/ticks-per-generation (:t s6)))
        (is (not= (:live s0) (:live s6)))))))
