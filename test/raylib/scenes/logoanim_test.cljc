(ns raylib.scenes.logoanim-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.logoanim :as l]))

(deftest the-machine-runs-in-order-and-loops
  (let [stages (map :stage (take 1200 (iterate l/advance (l/initial-state))))]
    (testing "every stage is reached, in order"
      (is (= [0 1 2 3] (vec (distinct (take-while #(<= % 3) stages))))))
    (testing "and it starts over rather than stopping, since there is no replay key"
      (is (some (fn [[a b]] (and (= 3 a) (= 0 b))) (partition 2 1 stages))))))

(deftest the-bars-grow-to-the-logo-and-stop
  (let [run (take 1200 (iterate l/advance (l/initial-state)))]
    (testing "no bar ever exceeds the logo, which would draw outside the frame"
      (doseq [{:keys [top left bottom right]} run]
        (is (<= top (+ l/logo-units l/bar-step-units)))
        (is (<= left (+ l/logo-units l/bar-step-units)))
        (is (<= bottom (+ l/logo-units l/bar-step-units)))
        (is (<= right (+ l/logo-units l/bar-step-units)))))
    (testing "and the top and left finish before the bottom and right start"
      (let [at-2 (first (filter #(= 2 (:stage %)) run))]
        (is (>= (:top at-2) l/logo-units))
        (is (= l/border-units (:bottom at-2)))))))

(deftest the-word-spells-out-then-fades
  (let [run (take 1200 (iterate l/advance (l/initial-state)))
        stage-3 (filter #(= 3 (:stage %)) run)]
    (testing "letters arrive one at a time and never overrun the word"
      (is (= "" (l/visible-word 0)))
      (is (= "ray" (l/visible-word 3)))
      (is (= "raylib" (l/visible-word 6)))
      (is (= "raylib" (l/visible-word 99)) "asking for more than there is is safe"))
    (testing "alpha only starts falling once the word is complete"
      (doseq [{:keys [letters alpha]} stage-3]
        (when (< letters (count l/word))
          (is (= 1.0 alpha)))))
    (testing "and it fades most of the way down before restarting. No stored
              state ever HAS alpha at or below zero: the frame that would
              produce one returns a fresh initial-state instead, so alpha jumps
              from its last positive value straight back to 1.0. Asserting on
              a zero that never gets stored was this test's first mistake."
      (let [alphas (map :alpha stage-3)]
        (is (< (apply min alphas) (* 2 l/fade-step)) "gets within a step of zero")
        (is (> (count (distinct (filter #(< % 1.0) alphas))) 40)
            "and does it gradually rather than in one jump")))
    (testing "the restart is what ends the fade, and it happens more than once"
      (let [run (take 1200 (iterate l/advance (l/initial-state)))]
        (is (>= (count (filter (fn [[a b]] (and (= 3 (:stage a)) (= 0 (:stage b))))
                               (partition 2 1 run)))
                2))))))

(deftest the-blink-is-half-on
  (testing "fifteen frames on, fifteen off"
    (is (l/blink-on? 0))
    (is (l/blink-on? 14))
    (is (not (l/blink-on? 15)))
    (is (not (l/blink-on? 29)))
    (is (l/blink-on? 30)))
  (testing "so it is lit for half of any whole number of cycles"
    (is (= 30 (count (filter l/blink-on? (range 60)))))))

(deftest the-logo-scales-to-the-screen
  (doseq [screen [[1206 2334] [800 450] [2048 1536]]]
    (let [{:keys [x y side border scale]} (l/dimensions {:screen screen})
          [w h] screen]
      (is (pos? scale))
      (is (>= x 0)) (is (>= y 0))
      (is (<= (+ x side) w)) (is (<= (+ y side) h))
      (testing "and the border keeps its proportion to the logo"
        (is (< (abs (- (/ border side) (/ (double l/border-units) l/logo-units))) 1e-9))))))
