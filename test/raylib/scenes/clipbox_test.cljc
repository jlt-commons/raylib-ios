(ns raylib.scenes.clipbox-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.clipbox :as cb]))

(def d (cb/dimensions {:screen [1206 2334]}))
(def safe {:x 0 :y 186 :width 1206 :height 2334})

(deftest intersection-is-the-overlap-or-nothing
  (is (= [5 5 5 5] (cb/intersect [0 0 10 10] [5 5 10 10])))
  (is (= [0 0 10 10] (cb/intersect [0 0 10 10] [0 0 10 10])))
  (is (= [2 2 4 4] (cb/intersect [0 0 10 10] [2 2 4 4])) "one inside the other")
  (testing "nil, not a zero or negative width. raylib takes the width as an int
            and a negative one reads as an enormous unsigned value, so a
            degenerate box would clip to everything rather than to nothing,
            which looks exactly like the scissor being ignored."
    (is (nil? (cb/intersect [0 0 10 10] [20 20 5 5])) "disjoint")
    (is (nil? (cb/intersect [0 0 10 10] [10 0 5 5])) "edge-touching is not overlap")
    (is (nil? (cb/intersect [0 0 10 10] [0 10 5 5])))
    (is (nil? (cb/intersect [0 0 10 10] [-20 0 5 5])) "wholly to the left"))
  (testing "and it is symmetric"
    (doseq [[a b] [[[0 0 10 10] [5 5 10 10]] [[3 4 20 6] [1 1 9 9]]]]
      (is (= (cb/intersect a b) (cb/intersect b a))))))

(deftest the-clip-never-escapes-the-safe-region
  (testing "which is the whole reason clip-rect takes the safe region at all.
            rlgl keeps one scissor rectangle, so a scene's BeginScissorMode
            replaces the host's rather than nesting inside it."
    (doseq [t (range 0.0 60.0 0.17)]
      (when-let [[x y w h] (cb/clip-rect safe (cb/box d t))]
        (is (>= x (:x safe)) (str "t=" t))
        (is (>= y (:y safe)) (str "t=" t))
        (is (<= (+ x w) (+ (:x safe) (:width safe))) (str "t=" t))
        (is (<= (+ y h) (+ (:y safe) (:height safe))) (str "t=" t))
        (is (pos? w)) (is (pos? h))))))

(deftest a-box-outside-the-region-clips-to-nothing
  (testing "rather than to a rectangle with a negative extent"
    (is (nil? (cb/clip-rect {:x 0 :y 0 :width 100 :height 100} [500.0 500.0 50.0 50.0])))
    (is (nil? (cb/clip-rect {:x 0 :y 0 :width 100 :height 100} [-90.0 0.0 50.0 50.0])))))

(deftest the-box-is-offset-into-screen-space-before-clipping
  (testing "the scene draws from 0,0 but the scissor is set in screen
            coordinates, so skipping the offset would clip the wrong place by
            exactly the inset, which is the bug the pointer already had once"
    (let [s {:x 10 :y 200 :width 1000 :height 2000}
          [x y _ _] (cb/clip-rect s [50.0 60.0 100.0 100.0])]
      ;; == not =, because clip-rect returns doubles and (= 60 60.0) is false.
      (is (== 60 x) "10 + 50")
      (is (== 260 y) "200 + 60"))))

(deftest the-box-stays-inside-the-scene
  (doseq [t (range 0.0 60.0 0.11)]
    (let [[x y w h] (cb/box d t)]
      (is (>= x 0.0) (str "t=" t))
      (is (>= y 0.0) (str "t=" t))
      (is (<= (+ x w) (:w d)) (str "t=" t))
      (is (<= (+ y h) (:h d)) (str "t=" t)))))

(deftest the-box-actually-moves-around
  (testing "otherwise the scene shows one static crop and proves nothing"
    (let [xs (map (fn [t] (first (cb/box d t))) (range 0.0 30.0 0.25))
          ys (map (fn [t] (second (cb/box d t))) (range 0.0 30.0 0.25))]
      (is (> (- (apply max xs) (apply min xs)) 100.0))
      (is (> (- (apply max ys) (apply min ys)) 100.0)))))

(deftest the-grid-covers-the-whole-scene
  (let [cs (cb/cells d)]
    (is (seq cs))
    (testing "every cell is on screen and none is degenerate"
      (doseq [[x y size _] cs]
        (is (>= x 0)) (is (>= y 0))
        (is (pos? size))
        (is (< x (:w d))) (is (< y (:h d)))))
    (testing "and it reaches the far corner, so the crop always has something
              to show wherever the box drifts"
      (let [step (+ cb/cell cb/gap)]
        (is (>= (+ (apply max (map first cs)) step) (- (:w d) step)))
        (is (>= (+ (apply max (map second cs)) step) (- (:h d) step)))))))
