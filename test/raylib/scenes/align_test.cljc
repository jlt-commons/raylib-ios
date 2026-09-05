(ns raylib.scenes.align-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.align :as al]))

;; A stub measurer of fixed-width glyphs. The point is to pin the arithmetic
;; against something that is not raylib, so the test says what alignment means
;; rather than what this font happens to do.
(defn- measure [word] (* 20 (count word)))

(deftest left-alignment-puts-the-word-at-the-edge
  (doseq [tw [0 100 400 900]]
    (is (zero? (al/offset :left 800 tw)) (str "width " tw))))

(deftest centre-leaves-equal-slack-on-both-sides
  (is (= 200.0 (al/offset :centre 800 400)))
  (is (= 350.0 (al/offset :centre 800 100)))
  (testing "and a word exactly filling the box has nowhere to go"
    (is (zero? (al/offset :centre 800 800)))))

(deftest right-alignment-ends-the-word-at-the-far-edge
  (doseq [tw [0 100 400 800]]
    (let [o (al/offset :right 800 tw)]
      (is (< (abs (- 800 (+ o tw))) 1e-9) (str "width " tw " ends at the edge")))))

(deftest an-overlong-word-starts-at-the-left-in-every-alignment
  (testing "clamped, because an unclamped centre or right starts the word OFF
            the left edge and runs it into the middle, which reads as a layout
            bug rather than as an overflow"
    (doseq [a al/alignments]
      (is (zero? (al/offset a 400 900)) (str a " with a word wider than the box")))))

(deftest an-empty-word-is-not-a-special-case
  (doseq [a al/alignments]
    (let [o (al/offset a 800 0)]
      (is (<= 0.0 o 800.0) (str a)))))

(deftest the-three-offsets-are-ordered
  (testing "left before centre before right, for any word that fits"
    (doseq [word ["a" "raylib" "Clojure"]]
      (let [tw (measure word)
            [l c r] (map (fn [a] (al/offset a 800 tw)) al/alignments)]
        (is (<= l c r) (str word " measured " tw))))))

(deftest the-cycle-visits-every-alignment-and-every-word
  (let [seen (map al/current (range 0.0 (* al/hold-seconds
                                           (count al/alignments)
                                           (count al/words))
                                    0.2))]
    (is (= (set al/alignments) (set (map :alignment seen))))
    (is (= (set al/words) (set (map :word seen))))
    (testing "and it holds each state long enough to read"
      (let [runs (partition-by identity (map (juxt :alignment :word) seen))]
        (doseq [r runs]
          (is (> (count r) 3) "a state that flickers past is no demonstration"))))))

(deftest a-state-holds-for-its-full-time
  (is (= (al/current 0.0) (al/current (* 0.9 al/hold-seconds))))
  (is (not= (al/current 0.0) (al/current (* 1.1 al/hold-seconds)))))
