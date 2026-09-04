(ns raylib.scenes.piechart-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.piechart :as p]))

(def dims (p/dimensions {:screen [1206 2334]}))

(deftest the-wedges-tile-the-circle
  (let [a (p/arcs 0.0)]
    (testing "one per wedge, in order"
      (is (= (count p/wedges) (count a)))
      (is (= (mapv first p/wedges) (mapv :label a))))
    (testing "each starts where the last ended, so there is no seam"
      (doseq [[x y] (partition 2 1 a)]
        (is (< (abs (- (:to x) (:from y))) 1e-9))))
    (testing "and together they are exactly one turn"
      (is (< (abs (- 360.0 (- (:to (last a)) (:from (first a))))) 1e-9)))))

(deftest rotating-moves-every-wedge-by-the-same-amount
  (let [a (p/arcs 0.0)
        b (p/arcs 90.0)]
    (doseq [[x y] (map vector a b)]
      (is (< (abs (- 90.0 (- (:from y) (:from x)))) 1e-9))
      (is (< (abs (- (- (:to x) (:from x)) (- (:to y) (:from y)))) 1e-9)
          "and does not change any wedge's size"))))

(deftest percentages-are-honest
  (testing "they sum to a hundred, which is not automatic once each is rounded"
    (is (= 100 (reduce + (map #(p/percent (second %)) p/wedges)))))
  (testing "and are ordered like the values"
    (is (= (reverse (sort (map second p/wedges))) (map second p/wedges)))))

(deftest segment-count-follows-the-angle
  (let [a (p/arcs 0.0)
        counts (mapv #(count (p/triangles dims %)) a)]
    (testing "a bigger wedge gets more triangles, which is the whole reason this
              is per wedge rather than a fixed number each"
      (is (= counts (reverse (sort counts)))))
    (testing "and even the smallest gets enough to look like an arc"
      (is (>= (apply min counts) 2)))))

(deftest every-arc-vertex-is-on-the-rim
  (let [{:keys [cx cy radius]} dims]
    (doseq [wedge (p/arcs 33.0)
            t (p/triangles dims wedge)]
      (doseq [[x y] [[(nth t 0) (nth t 1)] [(nth t 2) (nth t 3)]]]
        (let [d (Math/sqrt (+ (* (- x cx) (- x cx)) (* (- y cy) (- y cy))))]
          (is (< (abs (- radius d)) 1e-9)))))))
