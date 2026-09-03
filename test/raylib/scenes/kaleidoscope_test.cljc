(ns raylib.scenes.kaleidoscope-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.kaleidoscope :as k]))

(def metrics {:screen [1206 2622]})

(deftest rotations-cover-every-fold-and-its-mirror
  (let [rots (k/rotations)]
    (is (= (* 2 k/folds) (count rots)))
    (testing "each is [cos sin mirror?] and the cosines are a unit circle"
      (is (every? (fn [[c s m]] (and (number? c) (number? s) (boolean? m))) rots))
      (is (every? (fn [[c s _]] (< (abs (- 1.0 (+ (* c c) (* s s)))) 1e-9)) rots)))))

(deftest place-is-a-rotation-about-the-centre
  (let [dims (k/dimensions metrics)
        {:keys [cx cy]} dims
        identity-rot [1.0 0.0 false]]
    (testing "the zeroth rotation is the identity, offset to the centre"
      (is (= [(+ cx 10.0) (+ cy 20.0)] (k/place dims identity-rot 10.0 20.0))))
    (testing "a mirror flips x and leaves y"
      (is (= [(- cx 10.0) (+ cy 20.0)] (k/place dims [1.0 0.0 true] 10.0 20.0))))
    (testing "rotation preserves distance from the centre"
      (let [d (fn [[x y]] (Math/sqrt (+ (* (- x cx) (- x cx)) (* (- y cy) (- y cy)))))]
        (is (every? (fn [rot] (< (abs (- (d (k/place dims rot 30.0 40.0)) 50.0)) 1e-9))
                    (k/rotations)))))))

(deftest the-trail-is-bounded
  (let [metrics metrics
        grown (reduce (fn [s _] (k/advance s metrics)) {:frame 0 :trail []} (range 500))]
    (is (= k/trail-length (count (:trail grown))))))

(deftest colour-fades-along-the-trail
  (testing "the oldest segment is blue-ish and the newest red-ish"
    (let [[r0 _ b0 _] (k/segment-colour 1 100)
          [r1 _ b1 _] (k/segment-colour 99 100)]
      (is (< r0 r1))
      (is (> b0 b1)))))
