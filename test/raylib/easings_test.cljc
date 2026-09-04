(ns raylib.easings-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.easings :as ez]))

(defn- close? [a b] (< (abs (- a b)) 1e-9))

(deftest every-curve-is-anchored
  (testing "at t=0 a curve is exactly b, and at t=d exactly b+c. Anything else
            means an animation that jumps on its first or last frame."
    (doseq [[nm f] ez/curves]
      (is (close? 0.0 (f 0 0.0 100.0 60)) (str nm " at t=0"))
      (is (close? 100.0 (f 60 0.0 100.0 60)) (str nm " at t=d")))))

(deftest t-is-clamped-rather-than-extrapolated
  (testing "a counter that overruns holds at the end value. The C relies on its
            callers to stop incrementing; clamping makes each function total."
    (doseq [[nm f] ez/curves]
      (is (close? (f 60 0.0 100.0 60) (f 600 0.0 100.0 60)) (str nm " past d"))
      (is (close? (f 0 0.0 100.0 60) (f -50 0.0 100.0 60)) (str nm " before 0"))))
  (testing "and a zero duration reads as finished rather than dividing by zero"
    (doseq [[nm f] ez/curves]
      (is (close? 100.0 (f 0 0.0 100.0 0)) (str nm " with d=0")))))

(deftest c-is-total-change-not-the-end-value
  (testing "which is what lets a negative c shrink rather than grow, and is the
            reason the signature was not normalised to [0,1]"
    (is (close? 50.0 (ez/linear 60 100.0 -50.0 60)))
    (is (close? 100.0 (ez/linear 0 100.0 -50.0 60)))))

(deftest the-monotone-curves-never-go-backwards
  (testing "everything except the four that deliberately overshoot or bounce"
    (let [overshooting #{"elastic-in" "elastic-out" "back-out" "bounce-in" "bounce-out"}]
      (doseq [[nm f] ez/curves :when (not (overshooting nm))]
        (let [vs (map #(f % 0.0 1.0 60) (range 61))]
          (is (apply <= vs) (str nm " is not monotone")))))))

(deftest the-overshooting-curves-actually-overshoot
  (testing "elastic-out and back-out pass their target and settle back, which is
            the entire reason to reach for either"
    (doseq [nm ["elastic-out" "back-out"]]
      (let [f (second (first (filter #(= nm (first %)) ez/curves)))
            vs (map #(f % 0.0 1.0 60) (range 61))]
        (is (> (apply max vs) 1.0) (str nm " never exceeds its target")))))
  (testing "and elastic-in undershoots below its start for the same reason"
    (is (< (apply min (map #(ez/elastic-in % 0.0 1.0 60) (range 61))) 0.0))))

(deftest in-and-out-are-reflections
  (testing "f-in(t) and f-out(t) are mirror images, which is what makes reading
            a pair side by side worth doing"
    (doseq [[in-name out-name] [["quad-in" "quad-out"] ["cubic-in" "cubic-out"]
                                ["sine-in" "sine-out"] ["circ-in" "circ-out"]]]
      (let [fi (second (first (filter #(= in-name (first %)) ez/curves)))
            fo (second (first (filter #(= out-name (first %)) ez/curves)))]
        (doseq [t (range 0 61 5)]
          (is (close? (fi t 0.0 1.0 60) (- 1.0 (fo (- 60 t) 0.0 1.0 60)))
              (str in-name " vs " out-name " at " t)))))))

(deftest bounce-out-lands-four-times
  (testing "four parabolas of decreasing height, which is what a ball does"
    (let [vs (mapv #(ez/bounce-out % 0.0 1.0 100) (range 101))
          peaks (count (filter (fn [[a b c]] (and (> b a) (> b c)))
                               (partition 3 1 vs)))]
      (is (= 3 peaks) (str "expected three interior peaks, got " peaks)))))
