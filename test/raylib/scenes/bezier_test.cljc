(ns raylib.scenes.bezier-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.bezier :as bez]))

(def m {:screen [1206 2334]})
(def d (bez/dimensions m))

(deftest the-curve-interpolates-its-ends-and-only-approaches-the-handles
  (let [ctrl [[0.0 0.0] [10.0 100.0] [90.0 100.0] [100.0 0.0]]]
    (is (= [0.0 0.0] (bez/at ctrl 0.0)))
    (is (= [100.0 0.0] (bez/at ctrl 1.0)))
    (testing "and never reaches the handles' height, which is what makes it a
              Bezier rather than a polyline through four points"
      (let [ys (map second (bez/curve ctrl))]
        (is (< (apply max ys) 100.0))
        (is (> (apply max ys) 50.0) "but it does head that way")))))

(deftest the-curve-stays-inside-the-control-polygon
  (testing "the convex hull property. A basis with a wrong weight usually breaks
            this even when the plotted curve still looks like a curve."
    (let [ctrl [[0.0 0.0] [10.0 100.0] [90.0 -40.0] [100.0 0.0]]
          pts (bez/curve ctrl)
          xs (map first ctrl) ys (map second ctrl)]
      (doseq [[x y] pts]
        (is (<= (apply min xs) x (apply max xs)))
        (is (<= (apply min ys) y (apply max ys)))))))

(deftest the-weights-sum-to-one
  (testing "so a curve over four identical points is that point, at every t.
            Compared with a tolerance, not =: the four weights are summed in
            floating point and land on 7.000000000000001 for some t, which is
            the arithmetic being right rather than wrong."
    (doseq [t [0.0 0.13 0.5 0.87 1.0]]
      (let [[x y] (bez/at [[7.0 3.0] [7.0 3.0] [7.0 3.0] [7.0 3.0]] t)]
        (is (< (abs (- 7.0 x)) 1e-9) (str "t=" t))
        (is (< (abs (- 3.0 y)) 1e-9) (str "t=" t))))))

(deftest the-handles-are-derived-and-level-with-their-ends
  (let [[a b c dd] (bez/controls [100.0 200.0] [900.0 600.0])]
    (is (= [100.0 200.0] a))
    (is (= [900.0 600.0] dd))
    (testing "each handle leaves its own end horizontally, so the curve starts
              and finishes level rather than kinking"
      (is (= 200.0 (second b)) "first handle shares the anchor's y")
      (is (= 600.0 (second c)) "second shares the far end's y"))
    (testing "and they pull toward each other"
      (is (> (first b) (first a)))
      (is (< (first c) (first dd))))))

(deftest a-finger-on-the-anchor-does-not-break-it
  (testing "the degenerate case: zero horizontal gap makes both handles collapse
            onto their ends, which must give a point rather than a NaN"
    (let [ctrl (bez/controls [100.0 200.0] [100.0 200.0])
          pts (bez/curve ctrl)]
      (doseq [[x y] pts]
        (is (< (abs (- 100.0 x)) 1e-9))
        (is (< (abs (- 200.0 y)) 1e-9))))))

(deftest it-follows-a-finger-and-drifts-without-one
  (let [base (first ((:init (bez/scene)) {:metrics m}))]
    (let [s (bez/advance base {:metrics m :pointer {:phase :down :position [400 700]}})]
      (is (:touching? s))
      (is (= [400.0 700.0] (:end s))))
    (let [a (bez/advance base {:metrics m :delta-seconds 0.2})
          b (bez/advance a {:metrics m :delta-seconds 0.2})]
      (is (not (:touching? a)))
      (is (not= (:end a) (:end b))))))

(deftest the-drift-stays-on-screen
  (let [run (reductions (fn [s _] (bez/advance s {:metrics m :delta-seconds 0.1}))
                        (first ((:init (bez/scene)) {:metrics m}))
                        (range 400))]
    (doseq [{:keys [end]} run]
      (is (<= 0.0 (first end) 1206.0))
      (is (<= 0.0 (second end) 2334.0)))))
