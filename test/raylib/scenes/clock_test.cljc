(ns raylib.scenes.clock-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.clock :as c]))

(def metrics {:screen [1206 2334]})
(def dims (c/dimensions metrics))

(deftest the-segment-table-is-a-seven-segment-display
  (testing "every numeral, and only the seven real segments"
    (is (= (set (range 10)) (set (keys c/lit-segments))))
    (doseq [[d segs] c/lit-segments]
      (is (every? #{:a :b :c :d :e :f :g} segs) (str "digit " d))))
  (testing "8 lights everything and 1 lights the two on the right, which is what
            makes a seven-segment 1 sit against the right edge rather than centred"
    (is (= 7 (count (c/lit-segments 8))))
    (is (= #{:b :c} (c/lit-segments 1))))
  (testing "no two numerals share a pattern, or the display would be ambiguous"
    (is (= 10 (count (set (vals c/lit-segments)))))))

(deftest digits-split-correctly
  (is (= [[0 0] [0 0] [0 0]] (c/digit-pairs [0 0 0])))
  (is (= [[2 3] [5 9] [5 9]] (c/digit-pairs [23 59 59])))
  (is (= [[0 9] [0 5] [1 2]] (c/digit-pairs [9 5 12])))
  (testing "midnight and one second to it, the two that catch an off-by-one"
    (is (= [[0 0] [0 0] [0 0]] (c/digit-pairs [0 0 0])))
    (is (= [[2 3] [5 9] [5 9]] (c/digit-pairs [23 59 59])))))

(deftest a-digit-is-seven-rectangles-that-do-not-overlap
  (let [rects (c/segment-rects dims 100.0 200.0)]
    (is (= 7 (count rects)))
    (is (= #{:a :b :c :d :e :f :g} (set (keys rects))))
    (testing "each is [x y w h] with positive extent"
      (doseq [[k [_ _ w h]] rects]
        (is (pos? w) (str k)) (is (pos? h) (str k))))
    (testing "the three horizontals are inset from both verticals, which is the
              little corner notch a real display has"
      (let [[ax _ aw _] (:a rects)
            [fx _ fw _] (:f rects)
            [bx _ bw _] (:b rects)]
        (is (> ax fx) "top bar starts right of the top-left vertical")
        (is (< (+ ax aw) (+ bx bw)) "and ends left of the top-right one")
        (is (pos? fw))))))

(deftest the-rows-stack-and-fit
  (let [[w h] (:screen metrics)]
    (testing "three rows, in order, none overlapping"
      (let [ys (mapv #(c/row-origin dims %) (range 3))]
        (is (apply < ys))
        (doseq [i (range 2)]
          (is (>= (- (nth ys (inc i)) (nth ys i)) (:row-h dims))))))
    (testing "and the block stays on screen"
      (is (>= (c/row-origin dims 0) 0))
      (is (<= (+ (c/row-origin dims 2) (:row-h dims)) h)))
    (testing "with both digit columns inside the width"
      (is (>= (:x0 dims) 0))
      (is (<= (+ (:x1 dims) (:digit-w dims)) w)))))
