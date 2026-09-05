(ns raylib.scenes.bars-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.bars :as bars]))

(def d (bars/dimensions {:screen [1206 2334]}))

(deftest the-outline-closes-and-has-a-fixed-point-count
  (testing "four quarter arcs of per+1 points each, whatever the roundness. A
            varying count would mean the fan changed shape rather than the
            corners changing radius."
    (let [per (max 1 (quot bars/segments 4))]
      (doseq [[l r] [[0.0 0.0] [1.0 1.0] [0.0 1.0] [0.37 0.62]]]
        (is (= (* 4 (inc per)) (count (bars/outline 0.0 0.0 100.0 40.0 l r)))
            (str "roundness " l " " r))))))

(deftest a-roundness-of-zero-collapses-to-a-right-angle
  (testing "the corner's points all coincide, so the fan's triangles there have
            no area and vanish. That is why one loop draws a square with no
            special case, which is the claim the scene is making."
    (let [pts (bars/outline 100.0 100.0 800.0 200.0 0.0 0.0)
          per (max 1 (quot bars/segments 4))
          corner (take (inc per) pts)]
      (is (= 1 (count (distinct corner))) "every top-left point is the same")
      (is (= [100.0 100.0] (first corner)))
      (testing "and the four corners are the rectangle's own"
        (is (= #{[100.0 100.0] [900.0 100.0] [900.0 300.0] [100.0 300.0]}
               (set pts)))))))

(deftest full-roundness-on-a-short-bar-is-a-lozenge-not-an-overshoot
  (testing "roundness scales against half the SHORTER side, so the arcs meet
            rather than crossing through each other"
    (let [w 800.0 h 200.0
          pts (bars/outline 0.0 0.0 w h 1.0 1.0)]
      (doseq [[x y] pts]
        (is (<= -1e-9 x (+ w 1e-9)))
        (is (<= -1e-9 y (+ h 1e-9))))
      (testing "and the corner radius is exactly half the height"
        (let [ys (map second pts)]
          (is (< (abs (- 0.0 (apply min ys))) 1e-9))
          (is (< (abs (- h (apply max ys))) 1e-9)))))))

(deftest the-outline-never-leaves-its-bar
  (doseq [i (range bars/bar-count)]
    (let [[x y bw bh] (bars/bar-rect d i)
          [l r] (bars/roundness i)]
      (doseq [[px py] (bars/outline x y bw bh l r)]
        (is (<= (- x 1e-9) px (+ x bw 1e-9)) (str "bar " i))
        (is (<= (- y 1e-9) py (+ y bh 1e-9)) (str "bar " i))))))

(deftest the-five-bars-cover-the-interesting-cases
  (let [rs (map bars/roundness (range bars/bar-count))]
    (is (= [0.0 1.0] (first rs)) "square left, round right")
    (is (= [1.0 0.0] (last rs)) "and the reverse")
    (testing "left grows while right shrinks, so every bar differs"
      (is (apply < (map first rs)))
      (is (apply > (map second rs)))
      (is (= bars/bar-count (count (distinct rs)))))
    (testing "and all of them stay in range"
      (doseq [[l r] rs]
        (is (<= 0.0 l 1.0))
        (is (<= 0.0 r 1.0))))))

(deftest the-gradient-runs-end-to-end
  (let [x 100.0 w 800.0]
    (is (= bars/left-colour (bars/shade x w x bars/left-colour bars/right-colour))
        "the left edge is the left colour")
    (is (= bars/right-colour (bars/shade x w (+ x w) bars/left-colour bars/right-colour))
        "and the right edge the right one")
    (testing "clamped, so a point outside the bar does not extrapolate past it"
      (is (= bars/left-colour (bars/shade x w (- x 500.0) bars/left-colour bars/right-colour)))
      (is (= bars/right-colour (bars/shade x w (+ x w 500.0) bars/left-colour bars/right-colour))))
    (testing "and monotonic across the bar in every channel"
      (let [samples (map (fn [k] (bars/shade x w (+ x (* w (/ k 20.0)))
                                             bars/left-colour bars/right-colour))
                         (range 21))]
        (doseq [ch (range 3)]
          (let [vs (map (fn [c] (nth c ch)) samples)]
            (is (or (apply <= vs) (apply >= vs)) (str "channel " ch))))))))

(deftest the-bars-fit-the-screen
  (doseq [i (range bars/bar-count)]
    (let [[x y bw bh] (bars/bar-rect d i)]
      (is (>= x 0.0))
      (is (>= y 0.0))
      (is (<= (+ x bw) 1206.0))
      (is (<= (+ y bh) 2334.0) (str "bar " i " runs off the bottom")))))
