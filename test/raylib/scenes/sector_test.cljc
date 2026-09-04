(ns raylib.scenes.sector-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.sector :as sec]))

(def metrics {:screen [1206 2334]})

(deftest the-floor-is-one-segment-per-ninety-degrees
  (testing "raylib's own rule, which is the arithmetic this scene exists to show"
    (is (= 1 (sec/auto-floor 0.0 90.0)))
    (is (= 2 (sec/auto-floor 0.0 91.0)) "just past 90 needs a second")
    (is (= 2 (sec/auto-floor 0.0 180.0)))
    (is (= 3 (sec/auto-floor 0.0 270.0)))
    (is (= 4 (sec/auto-floor 0.0 360.0))))
  (testing "and it depends on the arc's width, not on where it starts"
    (is (= (sec/auto-floor 0.0 180.0) (sec/auto-floor 90.0 270.0)))
    (is (= (sec/auto-floor 0.0 90.0) (sec/auto-floor 300.0 390.0)))))

(deftest a-degenerate-arc-still-asks-for-one-segment
  (testing "rather than zero, which would draw nothing and divide badly"
    (is (= 1 (sec/auto-floor 45.0 45.0)))
    (is (= 1 (sec/auto-floor 90.0 89.0)) "even inverted")))

(deftest below-the-floor-your-number-is-discarded
  (testing "which is the lesson: asking for 2 across 270 degrees does not give
            you 2, it gives you raylib's floor of 3"
    (let [r (sec/resolve-segments 0.0 270.0 2)]
      (is (= 3 (:segments r)))
      (is (= :auto (:mode r)))
      (is (= 3 (:floor r))))))

(deftest at-or-above-the-floor-your-number-is-used
  (doseq [[req expected] [[3 3] [4 4] [36 36]]]
    (let [r (sec/resolve-segments 0.0 270.0 req)]
      (is (= expected (:segments r)))
      (is (= :requested (:mode r)) (str "requested " req))))
  (testing "the boundary is inclusive, so exactly the floor counts as yours"
    (is (= :requested (:mode (sec/resolve-segments 0.0 270.0 3))))
    (is (= :auto (:mode (sec/resolve-segments 0.0 270.0 2))))))

(deftest the-drawn-count-is-never-less-than-one
  (doseq [req [0 -5 1]]
    (is (pos? (:segments (sec/resolve-segments 0.0 45.0 req))) (str "requested " req))))

(deftest the-sweep-stays-inside-its-bounds
  (let [run (reductions (fn [s _] (sec/advance s {:delta-seconds 0.05}))
                        (first ((:init (sec/scene)) {:metrics metrics}))
                        (range 2000))]
    (doseq [{:keys [requested end-angle]} run]
      (is (<= sec/min-shown requested sec/max-segments))
      (is (<= 90.0 end-angle 350.1)))
    (testing "and actually reaches both ends rather than hovering in the middle"
      (is (some (fn [s] (= sec/max-segments (:requested s))) run))
      (is (some (fn [s] (= sec/min-shown (:requested s))) run)))))

(deftest the-scene-visits-both-modes
  (testing "otherwise the readout would never change and the point is lost"
    (let [run (reductions (fn [s _] (sec/advance s {:delta-seconds 0.05}))
                          (first ((:init (sec/scene)) {:metrics metrics}))
                          (range 2000))
          modes (map (fn [{:keys [start-angle end-angle requested]}]
                       (:mode (sec/resolve-segments start-angle end-angle requested)))
                     run)]
      (is (some #{:auto} modes))
      (is (some #{:requested} modes)))))
