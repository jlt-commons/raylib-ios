(ns raylib.scenes.unitcircle-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.unitcircle :as u]))

(def metrics {:screen [1206 2334]})
(def dims (u/dimensions metrics))

(defn- close? [a b] (< (abs (- a b)) 1e-6))

(deftest the-point-goes-round-the-circle
  (let [{:keys [cx cy radius]} dims]
    (testing "the four quarters, remembering screen y grows downward"
      (let [[x y] (u/point-at dims 0.0)]
        (is (close? (+ cx radius) x)) (is (close? cy y)))
      (let [[x y] (u/point-at dims (/ Math/PI 2))]
        (is (close? cx x)) (is (close? (- cy radius) y) "sine is UP, so y decreases"))
      (let [[x y] (u/point-at dims Math/PI)]
        (is (close? (- cx radius) x)) (is (close? cy y)))
      (let [[x y] (u/point-at dims (* 1.5 Math/PI))]
        (is (close? cx x)) (is (close? (+ cy radius) y))))
    (testing "and never leaves it"
      (doseq [a (range 0 6.3 0.1)]
        (let [[x y] (u/point-at dims a)
              d (Math/sqrt (+ (* (- x cx) (- x cx)) (* (- y cy) (- y cy))))]
          (is (close? radius d)))))))

(deftest the-trace-is-a-bounded-ring
  (let [run (nth (iterate u/advance {:angle 0.0 :trace []}) (* 3 u/trace-length))]
    (is (= u/trace-length (count (:trace run))))
    (testing "it holds sine and cosine of the angles actually visited, not a
              recomputation, so the wave is a record rather than a plot"
      (let [a (:angle run)
            [s c] (last (:trace run))]
        (is (close? (Math/sin a) s))
        (is (close? (Math/cos a) c))))))

(deftest the-trace-fills-before-it-scrolls
  (testing "a short run has a short trace rather than a padded one"
    (let [run (nth (iterate u/advance {:angle 0.0 :trace []}) 10)]
      (is (= 10 (count (:trace run)))))))

(deftest waves-span-the-width-and-stay-in-their-band
  (let [run (nth (iterate u/advance {:angle 0.0 :trace []}) (* 2 u/trace-length))
        {:keys [w trace-top trace-height amplitude]} dims]
    (doseq [[nm pick centre] [["sine" first 0.28] ["cosine" second 0.72]]]
      (let [pts (u/wave-points dims (:trace run) pick centre)
            xs (map first pts)
            ys (map second pts)
            mid (+ trace-top (* centre trace-height))]
        (testing (str nm " spans the screen left to right")
          (is (close? 0.0 (apply min xs)))
          (is (< (- w (apply max xs)) (/ (double w) u/trace-length))))
        (testing (str nm " stays within its amplitude of its own centre line")
          (is (>= (apply min ys) (- mid amplitude 1e-6)))
          (is (<= (apply max ys) (+ mid amplitude 1e-6))))))))

(deftest the-two-waves-are-a-quarter-turn-apart
  (testing "cosine leads sine by pi/2, which is the whole point of showing both"
    (let [run (nth (iterate u/advance {:angle 0.0 :trace []}) (* 2 u/trace-length))
          [s c] (last (:trace run))
          a (:angle run)]
      (is (close? s (Math/sin a)) "the first channel really is sine")
      (is (close? c (Math/cos a)) "and the second really is cosine")
      (is (close? c (Math/sin (+ a (/ Math/PI 2)))) "which is sine, a quarter turn on")
      (testing "so the identity holds for every sample, not just the last"
        (doseq [[sv cv] (take 40 (:trace run))]
          (is (close? 1.0 (+ (* sv sv) (* cv cv)))))))))
