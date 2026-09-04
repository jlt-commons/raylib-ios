(ns raylib.scenes.clockgrid-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.clockgrid :as cg]))

(def metrics {:screen [1206 2334]})
(def d (cg/dimensions metrics))

(deftest the-digit-table-is-complete-and-well-formed
  (is (= 10 (count cg/digit-angles)) "one entry per digit")
  (doseq [[n cells] (map-indexed vector cg/digit-angles)]
    (is (= cg/cells (count cells)) (str "digit " n " has 4x6 cells"))
    (doseq [pair cells]
      (is (= 2 (count pair)) (str "digit " n ": every cell is a hand pair"))
      (is (every? number? pair)))))

(deftest every-cell-uses-one-of-the-seven-shapes
  (testing "a transcription slip from the C table would show up as a pair that
            is not one of the seven, since only these seven can meet their
            neighbours to form a continuous stroke"
    (let [known #{cg/TL cg/TR cg/BR cg/BL cg/HH cg/VV cg/ZZ}]
      (doseq [[n cells] (map-indexed vector cg/digit-angles)]
        (doseq [pair cells]
          (is (contains? known pair) (str "digit " n " has an unknown pair " pair)))))))

(deftest digits-are-visually-distinct
  (testing "two digits sharing a cell layout would render identically, which is
            the failure a transcription typo actually produces"
    (is (= 10 (count (distinct cg/digit-angles))))))

(deftest one-and-seven-are-the-sparse-ones
  (testing "a sanity check against the shapes rather than the table: 1 and 7 are
            mostly blank, and 8 has no blanks at all"
    (let [blanks (fn [n] (count (filter #{cg/ZZ} (nth cg/digit-angles n))))]
      (is (zero? (blanks 8)) "8 fills every cell")
      (is (zero? (blanks 0)))
      (is (pos? (blanks 1)))
      (is (pos? (blanks 7)))
      (is (> (blanks 7) (blanks 3))))))

(deftest the-time-splits-into-six-digits
  (is (= [0 9 0 5 0 3] (cg/digits-of [9 5 3])))
  (is (= [2 3 5 9 5 9] (cg/digits-of [23 59 59])))
  (is (= [0 0 0 0 0 0] (cg/digits-of [0 0 0])))
  (testing "and every digit indexes the table"
    (doseq [h (range 24) m [0 7 30 59] s [0 59]]
      (doseq [dg (cg/digits-of [h m s])]
        (is (<= 0 dg 9))))))

(deftest hands-sweep-forward-rather-than-winding-back
  (testing "a source ahead of its target has a full turn taken off it, so the
            hand goes on round instead of reversing"
    (is (= -90.0 (cg/shortest-from 270.0 0.0)))
    (is (= 0.0 (cg/shortest-from 0.0 90.0)) "already behind, left alone")
    (is (= 90.0 (cg/shortest-from 90.0 90.0)) "equal is not ahead"))
  (testing "so the interpolated path never runs backwards"
    (let [src (cg/shortest-from 270.0 0.0)
          path (map (fn [t] (cg/lerp src 0.0 t)) (range 0.0 1.01 0.1))]
      (is (apply <= path)))))

(deftest a-tick-restarts-the-sweep-and-a-quiet-frame-continues-it
  (let [s0 (first ((:init (cg/scene)) {:metrics metrics}))
        s1 (cg/advance s0 {:local-time [1 2 3] :delta-seconds 0.0})
        s2 (cg/advance s1 {:local-time [1 2 3] :delta-seconds 0.2})
        s3 (cg/advance s2 {:local-time [1 2 4] :delta-seconds 0.2})]
    (is (= 3 (:sec s1)))
    (is (zero? (:timer s1)) "the tick resets the clock")
    (is (< (abs (- 0.2 (:timer s2))) 1e-9) "a quiet frame accumulates")
    (is (= 4 (:sec s3)))
    (is (zero? (:timer s3)) "and the next tick resets it again")))

(deftest the-sweep-completes-and-then-holds
  (testing "t is clamped at 1, so the hands settle rather than overshooting the
            digit they were heading for"
    (let [s (reduce (fn [st _] (cg/advance st {:local-time [1 2 3] :delta-seconds 0.2}))
                    (cg/advance (first ((:init (cg/scene)) {:metrics metrics}))
                                {:local-time [1 2 3] :delta-seconds 0.0})
                    (range 20))]
      (is (= (:dst s) (:current s)) "arrived exactly on target"))))

(deftest the-grid-keeps-its-shape-through-every-second
  (let [run (reductions (fn [st sec] (cg/advance st {:local-time [12 34 sec] :delta-seconds 0.1}))
                        (first ((:init (cg/scene)) {:metrics metrics}))
                        (range 60))]
    (doseq [s run]
      (is (= 6 (count (:current s))))
      (doseq [g (:current s)]
        (is (= cg/cells (count g)))
        (doseq [pair g] (is (= 2 (count pair))))))))

(deftest the-layout-fits-the-screen
  (testing "sized from the height, because eighteen cells stacked bind before
            eight across do. Sizing from the width overflowed the bottom by 173."
    (let [right (+ (:x0 d) (:pair-w d) (* cg/cols (:step d)))
          bottom (+ (:y0 d) (* 2 (:row-step d)) (* cg/rows (:step d)))]
      (is (pos? (:x0 d)))
      (is (pos? (:y0 d)))
      (is (< right 1206.0) (str "right edge " right))
      (is (< bottom 2334.0) (str "bottom edge " bottom)))))
