(ns raylib.scenes.ring-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.ring :as ring]))

(def d (ring/dimensions {:screen [1206 2334]}))

(deftest zero-degrees-points-up-and-the-angle-runs-clockwise
  (testing "the same convention draw-ring uses, so the stroked outline lands on
            the filled annulus rather than beside it"
    (let [[x y] (ring/polar 100.0 100.0 50.0 0)]
      (is (< (abs (- 100.0 x)) 1e-9))
      (is (< (abs (- 50.0 y)) 1e-9) "up is a smaller y"))
    (let [[x y] (ring/polar 100.0 100.0 50.0 90)]
      (is (< (abs (- 150.0 x)) 1e-9))
      (is (< (abs (- 100.0 y)) 1e-9)))))

(deftest the-annulus-always-has-a-hole-and-never-inverts
  (testing "inner below outer at every point of the cycle. Inner meeting outer
            would draw nothing, and inner past outer would wind inside out."
    (doseq [t (range 0.0 40.0 0.13)]
      (let [{:keys [inner]} (ring/geometry d t)]
        (is (pos? inner) (str "t=" t))
        (is (< inner (:outer d)) (str "t=" t))
        (is (> inner (* 0.2 (:outer d))) (str "hole stays visible at t=" t))))))

(deftest the-sweep-is-always-a-real-arc
  (testing "never zero-width, which would draw nothing, and never past a full
            turn, which would overlap itself and double-shade the seam"
    (doseq [t (range 0.0 40.0 0.13)]
      (let [{:keys [start end]} (ring/geometry d t)
            span (- end start)]
        (is (> span 100.0) (str "t=" t))
        (is (< span 360.0) (str "t=" t))))))

(deftest the-arc-stroke-follows-the-arc
  (let [{:keys [cx cy outer]} d
        pts (ring/arc-points cx cy outer 0.0 180.0 ring/outline-segments)]
    (is (= (inc ring/outline-segments) (count pts)) "n segments needs n+1 points")
    (testing "and every point sits on the circle it is stroking"
      (doseq [[x y] pts]
        (let [r (Math/sqrt (+ (* (- x cx) (- x cx)) (* (- y cy) (- y cy))))]
          (is (< (abs (- outer r)) 1e-6)))))
    (testing "starting and ending exactly on the arc's own ends"
      (is (= (ring/polar cx cy outer 0.0) (first pts)))
      (is (= (ring/polar cx cy outer 180.0) (last pts))))))

(deftest the-shape-stays-on-screen
  (testing "asserted once, not over a range of t. The arc always lies inside the
            circle of radius `outer` about the centre, and none of those three
            numbers moves with time, so looping would check one constant many
            times and read as coverage it does not have."
    (let [{:keys [cx cy outer]} d]
      (is (>= (- cx outer) 0.0))
      (is (<= (+ cx outer) 1206.0))
      (is (>= (- cy outer) 0.0))
      (is (<= (+ cy outer) 2334.0))))
  (testing "and the animated part really is inside it"
    (doseq [t (range 0.0 20.0 0.31)]
      (let [{:keys [inner]} (ring/geometry d t)]
        (is (< inner (:outer d)) (str "t=" t))))))

(deftest the-cycles-do-not-share-a-period
  (testing "so a still frame is unlikely to catch the shape at an extreme, and
            the animation does not visibly loop"
    (let [g (fn [t] (ring/geometry d t))
          at-zero (g 0.0)
          later (map (fn [t] (g t)) [6.2 12.4 18.6 24.8])]
      (doseq [l later]
        (is (> (abs (- (:inner at-zero) (:inner l))) 1e-6)
            "inner radius has not returned to its start")))))
