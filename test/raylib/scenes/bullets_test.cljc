(ns raylib.scenes.bullets-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.bullets :as b]))

(def metrics {:screen [1206 2334]})
(def dims (b/dimensions metrics))

(deftest the-emitter-fires-evenly-spaced-arms
  (let [shots (b/emit dims 0.0)]
    (is (= b/arms (count shots)))
    (testing "all from the centre"
      (doseq [{:keys [x y]} shots]
        (is (= (:cx dims) x)) (is (= (:cy dims) y))))
    (testing "at the same speed, in different directions"
      (doseq [{:keys [vx vy]} shots]
        (is (< (abs (- b/speed (Math/sqrt (+ (* vx vx) (* vy vy))))) 1e-9)))
      (is (= b/arms (count (distinct (map (juxt :vx :vy) shots))))))
    (testing "and 120 degrees apart, which is what makes it three arms"
      (let [angles (sort (map #(Math/atan2 (:vy %) (:vx %)) shots))
            gaps (map - (rest angles) angles)]
        (doseq [g gaps]
          (is (< (abs (- g (/ (* 2.0 Math/PI) b/arms))) 1e-9)))))))

(deftest bullets-fly-straight
  (testing "each keeps the velocity it left with, so the spiral is an illusion
            made of straight lines rather than curved paths"
    (let [start {:angle 0.0 :bullets [{:x 100.0 :y 100.0 :vx 3.0 :vy -2.0}]}
          after (nth (iterate #(b/advance % metrics) start) 5)
          tracked (first (filter #(and (= 3.0 (:vx %)) (= -2.0 (:vy %))) (:bullets after)))]
      (is (some? tracked))
      (is (< (abs (- (+ 100.0 (* 5 3.0)) (:x tracked))) 1e-9))
      (is (< (abs (- (- 100.0 (* 5 2.0)) (:y tracked))) 1e-9)))))

(deftest the-count-is-bounded
  (testing "bullets leaving the region are dropped, so the cost in the tenth
            minute is the cost in the first. Without this it grows by three a
            frame forever."
    (let [counts (mapv (fn [n] (count (:bullets (nth (iterate #(b/advance % metrics)
                                                              {:angle 0.0 :bullets []}) n))))
                       [600 900 1200 1800 2400])
          lo (apply min counts)
          hi (apply max counts)]
      ;; The count does not settle to a single number: three bullets are added
      ;; every frame and however many crossed the edge that frame are removed,
      ;; so it oscillates in a narrow band. Measured at 349 to 352 over four
      ;; times the fill period, at the shipped speed of 8.0.
      ;;
      ;; An earlier version of this asserted the sequence was non-increasing
      ;; after subtracting 60 from each, which is not a tolerance: subtracting
      ;; a constant from every element leaves the comparison exactly as it was.
      (is (< (- hi lo) 30) (str "band was " lo " to " hi))
      (is (< hi 500) (str "settled around " hi))
      (is (> lo 200) "and it is not decaying to nothing"))))

(deftest nothing-lingers-outside-the-region
  (let [run (nth (iterate #(b/advance % metrics) {:angle 0.0 :bullets []}) 700)]
    (is (every? #(b/in-flight? dims %) (:bullets run)))))
