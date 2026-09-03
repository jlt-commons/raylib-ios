(ns poc.raylib.following-eyes-test
  (:require [clojure.test :refer [deftest is]]
            [poc.raylib.following-eyes :as eyes]))

(def portrait {:screen [1080 2400]})
(def landscape {:screen [2400 1080]})

(deftest pupils-stay-inside-eyes-test
  (doseq [metrics [portrait landscape]
          eye [(:left (eyes/layout metrics)) (:right (eyes/layout metrics))]
          target [[0 0] [99999 99999] [540 1200]]]
    (let [{:keys [eye-radius pupil-radius]} (eyes/layout metrics)
          position (eyes/pupil eye eye-radius pupil-radius target)
          dx (- (first position) (first eye))
          dy (- (second position) (second eye))]
      (is (<= (Math/sqrt (+ (* dx dx) (* dy dy)))
              (+ 0.000001 (- eye-radius pupil-radius)))))))

(deftest active-pointer-updates-and-release-retains-position-test
  (let [initial {:target [540.0 1200.0] :phase :idle}
        pressed (eyes/update-state initial {:pointer {:phase :press :position [10 20]}})
        held (eyes/update-state pressed {:pointer {:phase :down :position [30 40]}})
        released (eyes/update-state held {:pointer {:phase :release :position [30 40]}})]
    (is (= [10 20] (:target pressed)))
    (is (= [30 40] (:target held)))
    (is (= [30 40] (:target released)))))

(deftest scene-layout-is-adaptive-test
  (let [p (eyes/layout portrait) l (eyes/layout landscape)]
    (is (not= (:left p) (:left l)))
    (is (> (:eye-radius p) 0.0))
    (is (= [540.0 1200.0] (:neutral p)))))
