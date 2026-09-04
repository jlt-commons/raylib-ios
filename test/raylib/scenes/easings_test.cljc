(ns raylib.scenes.easings-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.easings :as ez]
            [raylib.scenes.easings :as e]))

(def metrics {:screen [1206 2334]})
(def dims (e/dimensions metrics))

(deftest the-grid-holds-every-curve-and-fits
  (testing "enough cells for all fifteen"
    (is (>= (* (:cols dims) (:rows dims)) (count ez/curves))))
  (testing "and the last one is on screen"
    (let [[x y] (e/cell-origin dims (dec (count ez/curves)))]
      (is (<= (+ x (:cell-w dims)) 1206))
      (is (<= (+ y (:cell-h dims)) 2334))))
  (testing "cells read across then down, not down then across"
    (let [[x0 y0] (e/cell-origin dims 0)
          [x1 y1] (e/cell-origin dims 1)
          [x3 y3] (e/cell-origin dims (:cols dims))]
      (is (> x1 x0)) (is (= y1 y0) "the second cell is beside the first")
      (is (= x3 x0)) (is (> y3 y0) "and the fourth is below it"))))

(deftest the-clock-runs-then-pauses
  (testing "it reaches exactly 1.0 and holds there before restarting, so the
            finished state is readable rather than flashing past"
    (is (= 0.0 (e/progress 0)))
    (is (= 1.0 (e/progress e/cycle-frames)))
    (is (= 1.0 (e/progress (+ e/cycle-frames 10))))
    (is (= 1.0 (e/progress (+ e/cycle-frames e/pause-frames -1)))))
  (testing "and then starts over"
    (is (= 0.0 (e/progress (+ e/cycle-frames e/pause-frames)))))
  (testing "never outside [0,1] at any point in several cycles"
    (doseq [t (range 0 1200)]
      (is (<= 0.0 (e/progress t) 1.0)))))

(deftest a-plot-spans-its-cell
  (let [pts (e/plot dims ez/linear 100.0 200.0)
        xs (map first pts)]
    (is (= e/samples (count pts)))
    (is (< (abs (- 100.0 (apply min xs))) 1e-9) "starts at the cell's left edge")
    (is (< (abs (- (+ 100.0 (:cell-w dims)) (apply max xs))) 1e-9) "ends at its right")))

(deftest the-dot-follows-the-plotted-curve
  (testing "the dot sits on the drawn line at any progress, which is the claim
            the scene makes and would be easy to get subtly wrong.

            Compared against the two plot samples that BRACKET the dot rather
            than a nearest one: the plot has 28 samples so its x values land on
            i/27, and a dot at p=0.25 sits between the samples at 6/27 and 7/27
            rather than on either. An earlier version of this test rounded to
            the nearest index and failed for every curve, which was the test
            being wrong and not the scene."
    (doseq [p [0.0 0.1 0.25 0.5 0.75 0.9 1.0]
            [nm f] ez/curves]
      (let [[dx dy] (e/dot dims f 0.0 0.0 p)
            pts (e/plot dims f 0.0 0.0)
            lo (min (dec e/samples) (int (Math/floor (* p (dec e/samples)))))
            hi (min (dec e/samples) (inc lo))
            [ax ay] (nth pts lo)
            [bx by] (nth pts hi)]
        (is (<= (- ax 1e-6) dx (+ bx 1e-6)) (str nm " x at " p " is between its samples"))
        (is (<= (- (min ay by) (* 0.06 (:cell-h dims)))
                dy
                (+ (max ay by) (* 0.06 (:cell-h dims))))
            (str nm " y at " p " is near the segment"))))))

(deftest overshoot-leaves-the-plot-band
  (testing "elastic-out goes above its cell's top, which is why the plot is
            inset rather than filling the cell"
    (let [curve (fn [nm] (second (first (filter #(= nm (first %)) ez/curves))))
          pts (e/plot dims (curve "elastic-out") 0.0 0.0)
          floor (- (:cell-h dims) (:inset-y dims))
          top (:label-h dims)]
      (is (< (apply min (map second pts)) top)
          "elastic-out should rise above the plot's own top edge")
      (testing "and a curve that does not overshoot stays inside the band, which
                is what makes the inset a deliberate choice rather than slack
                everything happens to need"
        (let [ys (map second (e/plot dims (curve "linear") 0.0 0.0))]
          (is (>= (apply max ys) (- floor 1e-6)) "starts on the floor")
          (is (>= (apply min ys) (- top 1e-6)) "without leaving the top"))))))
