(ns poc.raylib.diagnostics-test
  (:require [clojure.test :refer [deftest is testing]]
            [poc.raylib.diagnostics :as diagnostics]))

(def portrait-raw
  {:screen-width 1080 :screen-height 2400
   :render-width 1080 :render-height 2400
   :touch-count 0 :touch-ids []
   :pointer-x 0 :pointer-y 0
   :pressed? false :down? false :released? false :back? false})

(deftest live-screen-metrics-and-layout-test
  (testing "portrait and landscape derive from live dimensions"
    (is (= {:screen [1080 2400] :render [1080 2400]
            :dpi-scale [1 1] :orientation :portrait}
           (diagnostics/screen-metrics portrait-raw)))
    (is (= :landscape
           (:orientation (diagnostics/screen-metrics
                          (assoc portrait-raw
                                 :screen-width 2400 :screen-height 1080)))))
    (is (not= (diagnostics/layout (diagnostics/screen-metrics portrait-raw))
              (diagnostics/layout
               (diagnostics/screen-metrics
                (assoc portrait-raw :screen-width 720 :screen-height 1280)))))))

(deftest pointer-edge-and-touch-boundary-test
  (let [press (diagnostics/normalize-input
               (assoc portrait-raw
                      :touch-count 2 :touch-ids [7 11]
                      :pointer-x 120 :pointer-y 340 :pressed? true :down? true))
        down (diagnostics/normalize-input
              (assoc portrait-raw
                     :touch-count 1 :touch-ids [7]
                     :pointer-x 150 :pointer-y 390 :down? true))
        release (diagnostics/normalize-input
                 (assoc portrait-raw
                        :pointer-x 150 :pointer-y 390 :released? true))]
    (is (= :press (get-in press [:pointer :phase])))
    (is (= {:count 2 :ids [7 11] :point-0 [120 340]
            :available-coordinates :point-0
            :all-coordinates-available? false}
           (:touches press)))
    (is (= :down (get-in down [:pointer :phase])))
    (is (= :release (get-in release [:pointer :phase])))
    (is (false? (get-in press [:touches :all-coordinates-available?])))
    (let [keyboard (diagnostics/normalize-input
                    (assoc portrait-raw :gesture-code 8
                           :keyboard-activate? true :keyboard-back? true))]
      (is (= {:code 8} (:gesture keyboard)))
      (is (get-in keyboard [:keyboard :activate?]))
      (is (:back? keyboard)))))

(deftest deterministic-diagnostic-transition-test
  (let [press (diagnostics/normalize-input
               (assoc portrait-raw :pointer-x 10 :pointer-y 20
                      :pressed? true :down? true))
        down (diagnostics/normalize-input
              (assoc portrait-raw :pointer-x 20 :pointer-y 30 :down? true))
        release (diagnostics/normalize-input
                 (assoc portrait-raw :pointer-x 20 :pointer-y 30
                        :released? true))
        s1 (diagnostics/step diagnostics/initial-state press)
        s2 (diagnostics/step s1 down)
        s3 (diagnostics/step s2 release)
        closed (diagnostics/step s3 (assoc release :back? true))]
    (is (= 1 (:tap-count s1)))
    (is (= 2 (:hold-frames s2)))
    (is (= 1 (:drag-samples s2)))
    (is (= 0 (:hold-frames s3)))
    (is (:close-requested? closed))))
