(ns raylib.scenes.splines-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.splines :as spl]))

(def d (spl/dimensions {:screen [1206 2334]}))

(deftest catmull-rom-passes-through-its-control-points
  (testing "which is the property that distinguishes it from the other two, and
            the reason all three are drawn over the same points"
    (is (< (abs (- 10.0 (spl/catmull-rom 0.0 10.0 20.0 30.0 0.0))) 1e-9) "t=0 is b")
    (is (< (abs (- 20.0 (spl/catmull-rom 0.0 10.0 20.0 30.0 1.0))) 1e-9) "t=1 is c")
    (testing "for arbitrary points, not just evenly spaced ones"
      (is (< (abs (- 7.0 (spl/catmull-rom -3.0 7.0 2.0 40.0 0.0))) 1e-9))
      (is (< (abs (- 2.0 (spl/catmull-rom -3.0 7.0 2.0 40.0 1.0))) 1e-9)))))

(deftest bezier-interpolates-its-ends-and-only-approaches-the-middle
  (is (< (abs (- 0.0 (spl/bezier 0.0 10.0 20.0 30.0 0.0))) 1e-9) "t=0 is a")
  (is (< (abs (- 30.0 (spl/bezier 0.0 10.0 20.0 30.0 1.0))) 1e-9) "t=1 is d")
  (testing "and with all four equal it is that constant, a useful degenerate check"
    (doseq [t [0.0 0.25 0.5 1.0]]
      (is (< (abs (- 5.0 (spl/bezier 5.0 5.0 5.0 5.0 t))) 1e-9)))))

(deftest the-b-spline-touches-none-of-them
  (testing "which is exactly what buys it smoothness, and is visible in the
            scene as the curve that stays furthest from the dots"
    ;; A zigzag, not the evenly spaced 0 10 20 30 an earlier version used. On
    ;; collinear evenly spaced points every basis here reproduces the line
    ;; exactly, so the B-spline DOES pass through them and the test asserted the
    ;; opposite of what it meant. That agreement is real and worth having, which
    ;; is what all-three-reproduce-a-straight-line checks; it just cannot be the
    ;; input for a test about the three differing.
    (let [pts [0.0 30.0 0.0 30.0]]
      (is (> (abs (- 30.0 (apply spl/b-spline (conj pts 0.0)))) 5.0)
          "does not reach b")
      (is (> (abs (- 0.0 (apply spl/b-spline (conj pts 1.0)))) 5.0)
          "does not reach c")
      (testing "while Catmull-Rom, on the same points, hits both exactly"
        (is (< (abs (- 30.0 (apply spl/catmull-rom (conj pts 0.0)))) 1e-9))
        (is (< (abs (- 0.0 (apply spl/catmull-rom (conj pts 1.0)))) 1e-9)))))
  (testing "but it does reproduce a constant, so the weights sum to one"
    (doseq [t [0.0 0.3 0.7 1.0]]
      (is (< (abs (- 5.0 (spl/b-spline 5.0 5.0 5.0 5.0 t))) 1e-9)))))

(deftest all-three-reproduce-a-straight-line
  (testing "an affine check: evenly spaced controls should give evenly spaced
            output for every basis, and a basis with a typo in one weight
            usually fails this even when it looks plausible plotted"
    (doseq [[nm f] spl/kinds]
      (let [mid (f 0.0 1.0 2.0 3.0 0.5)]
        (is (< (abs (- 1.5 mid)) 1e-9) (str nm " at the midpoint of a line"))))))

(deftest the-curve-covers-every-span
  (let [pts (spl/points d 0.0)]
    (doseq [[nm f] spl/kinds]
      (let [c (spl/curve f pts)]
        (is (= (* (dec spl/control-points) (inc spl/samples)) (count c))
            (str nm " samples every span"))
        (is (every? (fn [[x y]] (and (number? x) (number? y))) c))))))

(deftest the-control-points-span-the-screen-and-stay-on-it
  (doseq [t [0.0 1.7 4.2 9.9 30.0]]
    (let [pts (spl/points d t)]
      (is (= spl/control-points (count pts)))
      (doseq [[x y] pts]
        (is (<= 0.0 x 1206.0) (str "t=" t))
        (is (<= 0.0 y 2334.0) (str "t=" t)))))
  (testing "and they are ordered left to right, so the curves read as one shape"
    (let [xs (map first (spl/points d 3.3))]
      (is (apply < xs)))))

(deftest the-points-bob-independently
  (testing "so the curves change shape rather than sliding as a rigid body"
    (let [ys0 (map second (spl/points d 0.0))
          ys1 (map second (spl/points d 1.0))
          deltas (map - ys1 ys0)]
      (is (> (count (distinct (map (fn [x] (Math/round (double x))) deltas))) 1)))))
