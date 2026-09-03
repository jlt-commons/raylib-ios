(ns poc.raylib.gallery-ui-test
  (:require [clojure.test :refer [deftest is testing]]
            [poc.raylib.diagnostics :as diagnostics]
            [poc.raylib.gallery-ui :as gallery-ui]))

(def scene-ids [:eyes :trail :flappy :controls :touch :gestures])
(def portrait-metrics
  (diagnostics/screen-metrics {:screen-width 1080 :screen-height 2400
                               :render-width 1080 :render-height 2400}))
(def sizes (diagnostics/layout portrait-metrics))

(deftest live-presentation-is-pure-data-test
  (is (= {:revision :baseline
          :title "Jolt + Raylib Gallery"
          :subtitle "Choose a touch-first scene"
          :background [245 245 245 255]
          :accent [0 82 172 255]
          :card [35 92 150 255]}
         (gallery-ui/live-presentation))))

(deftest adaptive-card-layout-test
  (let [portrait (gallery-ui/gallery-layout portrait-metrics scene-ids sizes)
        landscape-metrics (diagnostics/screen-metrics
                           {:screen-width 2400 :screen-height 1080
                            :render-width 2400 :render-height 1080})
        landscape (gallery-ui/gallery-layout landscape-metrics scene-ids
                                             (diagnostics/layout landscape-metrics))]
    (testing "all registered scenes receive a safe, unique card"
      (is (= 6 (count (:cards portrait))))
      (is (= scene-ids (mapv :scene-id (:cards portrait))))
      (is (= 6 (count (set (map (juxt :x :y) (:cards portrait))))))
      (is (every? #(and (pos? (:width %)) (pos? (:height %))) (:cards portrait))))
    (testing "orientation changes the grid without fixed desktop coordinates"
      (is (= 2 (:columns portrait)))
      (is (= 3 (:columns landscape)))
      (is (not= (mapv #(select-keys % [:x :y :width :height]) (:cards portrait))
                (mapv #(select-keys % [:x :y :width :height]) (:cards landscape)))))))

(deftest counter-control-layout-test
  (let [controls (gallery-ui/counter-controls portrait-metrics sizes)]
    (is (= [:decrement :increment :reset] (mapv :action controls)))
    (is (= :increment
           (gallery-ui/hit-test-controls controls
                                         [(+ (:x (second controls)) 2)
                                          (+ (:y (second controls)) 2)])))
    (is (nil? (gallery-ui/hit-test-controls controls [0 0])))))

(deftest hit-testing-is-edge-safe-test
  (let [layout (gallery-ui/gallery-layout portrait-metrics scene-ids sizes)
        first-card (first (:cards layout))
        back (:back layout)
        center (fn [rect] [(+ (:x rect) (quot (:width rect) 2))
                           (+ (:y rect) (quot (:height rect) 2))])]
    (is (= :eyes (gallery-ui/hit-test layout (center first-card) :gallery)))
    (is (= :back (gallery-ui/hit-test layout (center back) :scene)))
    (is (nil? (gallery-ui/hit-test layout [0 0] :gallery)))
    (is (nil? (gallery-ui/hit-test layout [(:x first-card) (dec (:y first-card))]
                                           :gallery)))))
