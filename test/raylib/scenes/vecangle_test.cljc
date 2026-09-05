(ns raylib.scenes.vecangle-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.vecangle :as va]))

(def d (va/dimensions {:screen [1206 2334]}))

(deftest bearings-run-clockwise-from-up
  (testing "screen space, where y grows downward. atan2(x, -y), not the
            textbook atan2(y, x), which would measure from the wrong axis in
            the wrong direction and produce angles that look plausible."
    (is (< (abs (va/bearing [0.0 -10.0])) 1e-9) "up is 0")
    (is (< (abs (- 90.0 (va/bearing [10.0 0.0]))) 1e-9) "right is 90")
    (is (< (abs (- 180.0 (abs (va/bearing [0.0 10.0])))) 1e-9) "down is 180")
    (is (< (abs (- -90.0 (va/bearing [-10.0 0.0]))) 1e-9) "left is -90"))
  (testing "and the textbook form really would disagree"
    (let [textbook (fn [[x y]] (Math/toDegrees (Math/atan2 (double y) (double x))))]
      (is (not= (Math/round (va/bearing [10.0 0.0]))
                (Math/round (textbook [10.0 0.0])))))))

(deftest the-turn-is-signed-and-takes-the-short-way
  (testing "which is the subject of the scene: an unsigned angle is a distance,
            a signed one is a rotation"
    (is (< (abs (- 20.0 (va/signed-between 350.0 10.0))) 1e-9) "wraps forward")
    (is (< (abs (- -20.0 (va/signed-between 10.0 350.0))) 1e-9) "and backward")
    (is (< (abs (- 90.0 (va/signed-between 0.0 90.0))) 1e-9))
    (is (< (abs (- -90.0 (va/signed-between 90.0 0.0))) 1e-9)))
  (testing "never outside (-180, 180]"
    (doseq [a (range -720 720 17) b (range -720 720 23)]
      (let [t (va/signed-between (double a) (double b))]
        (is (< -180.0 t) (str a "->" b))
        (is (<= t 180.0) (str a "->" b)))))
  (testing "and the half turn resolves one way rather than flickering"
    (is (< (abs (- 180.0 (va/signed-between 0.0 180.0))) 1e-9))))

(deftest reversing-the-turn-negates-it
  (doseq [[a b] [[0.0 37.0] [100.0 20.0] [-30.0 200.0] [359.0 1.0]]]
    (let [f (va/signed-between a b)
          r (va/signed-between b a)]
      (is (or (< (abs (+ f r)) 1e-9)
              (and (< (abs (- 180.0 f)) 1e-9) (< (abs (- 180.0 r)) 1e-9)))
          (str a "->" b " gave " f " and " r)))))

(deftest vector-a-is-fixed-and-b-turns
  (let [{a0 :a b0 :b} (va/vectors d 0.0)
        {a1 :a b1 :b} (va/vectors d 1.5)]
    (is (= a0 a1) "A does not move")
    (is (not= b0 b1) "B does")
    (testing "and both keep their length, so only the angle is changing"
      (doseq [[x y] [a0 b0 b1]]
        (is (< (abs (- (:length d) (Math/sqrt (+ (* x x) (* y y))))) 1e-6))))))

(deftest b-sweeps-a-full-turn-and-the-readout-covers-both-signs
  (let [turns (map (fn [t]
                     (let [{:keys [a b]} (va/vectors d t)]
                       (va/signed-between (va/bearing a) (va/bearing b))))
                   (range 0.0 (/ 360.0 va/turn-rate) 0.02))]
    (is (some pos? turns) "clockwise")
    (is (some neg? turns) "anticlockwise")
    (is (some (fn [x] (< (abs x) 2.0)) turns) "and passes through zero")))

(deftest the-arms-stay-on-screen
  (doseq [t (range 0.0 20.0 0.11)]
    (let [{:keys [a b]} (va/vectors d t)]
      (doseq [[x y] [a b]]
        (is (<= 0.0 (+ (:cx d) x) 1206.0) (str "t=" t))
        (is (<= 0.0 (+ (:cy d) y) 2334.0) (str "t=" t))))))
