(ns raylib.scenes.angles-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.angles :as a]))

(def dims (a/dimensions {:screen [1206 2334]}))
(defn- close? [x y] (< (abs (- x y)) 1e-9))

(deftest the-spokes-divide-the-circle-evenly
  (let [as (a/fixed-angles)]
    (is (= a/spokes (count as)))
    (testing "each is the same step from the last"
      (let [gaps (map - (rest as) as)]
        (is (every? #(close? (first gaps) %) gaps))))
    (testing "and they cover exactly one turn, first at zero"
      (is (close? 0.0 (first as)))
      (is (close? (* 2.0 Math/PI) (+ (last as) (- (second as) (first as))))))))

(deftest every-spoke-reaches-the-rim
  (let [{:keys [cx cy radius]} dims]
    (doseq [a (concat (a/fixed-angles) [0.3 1.7 4.2 -2.0 100.0])]
      (let [[x y] (a/spoke-end dims a)
            d (Math/sqrt (+ (* (- x cx) (- x cx)) (* (- y cy) (- y cy))))]
        (is (close? radius d) (str "angle " a))))))

(deftest screen-y-grows-downward
  (testing "which is why a positive angle sweeps clockwise here. Worth pinning:
            the same maths on graph paper goes the other way, and a scene that
            silently compensated would make every sibling harder to read."
    (let [{:keys [cx cy radius]} dims]
      (let [[x y] (a/spoke-end dims 0.0)]
        (is (close? (+ cx radius) x)) (is (close? cy y)))
      (let [[_ y] (a/spoke-end dims (/ Math/PI 2))]
        (is (> y cy) "a quarter turn goes DOWN the screen, not up")))))

(deftest the-spinner-turns
  (is (not= (a/spoke-end dims 0.0) (a/spoke-end dims a/radians-per-frame)))
  (testing "and a full turn comes back to where it started"
    (let [[x0 y0] (a/spoke-end dims 0.0)
          [x1 y1] (a/spoke-end dims (* 2.0 Math/PI))]
      (is (close? x0 x1)) (is (close? y0 y1)))))
