(ns raylib.scenes.dashed-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.dashed :as d]))

(def metrics {:screen [1206 2334]})
(def dims (d/dimensions metrics))

(defn- seg-length [[x1 y1 x2 y2]]
  (Math/sqrt (+ (* (- x2 x1) (- x2 x1)) (* (- y2 y1) (- y2 y1)))))

(deftest every-dash-is-the-same-length
  (testing "which is the property that makes it read as a dashed line rather
            than a line with a pattern that stretches. It comes from walking
            the unit vector rather than interpolating between the endpoints."
    (doseq [target [[1200.0 2300.0] [10.0 10.0] [603.0 2000.0] [900.0 300.0]]]
      (doseq [seg (d/dashes dims target)]
        (is (< (abs (- (:dash dims) (seg-length seg))) 1e-9)
            (str "target " target))))))

(deftest dashes-lie-on-the-line-to-the-target
  (let [{:keys [cx cy]} dims
        target [1100.0 2000.0]
        segs (d/dashes dims target)]
    (is (seq segs))
    (testing "every endpoint is collinear with the hub and the target"
      (let [dx (- (first target) cx) dy (- (second target) cy)]
        (doseq [[x1 y1 x2 y2] segs]
          (doseq [[px py] [[x1 y1] [x2 y2]]]
            ;; cross product of (target - hub) and (point - hub) is zero
            (is (< (abs (- (* dx (- py cy)) (* dy (- px cx)))) 1e-6))))))))

(deftest they-stop-short-of-the-target-rather-than-overshooting
  (let [{:keys [cx cy]} dims
        target [1100.0 2000.0]
        segs (d/dashes dims target)
        far (apply max (map (fn [[_ _ x2 y2]]
                              (Math/sqrt (+ (* (- x2 cx) (- x2 cx)) (* (- y2 cy) (- y2 cy)))))
                            segs))
        len (Math/sqrt (+ (* (- (first target) cx) (- (first target) cx))
                          (* (- (second target) cy) (- (second target) cy))))]
    (is (<= far (+ len (:dash dims))))))

(deftest a-target-on-the-hub-draws-nothing
  (testing "rather than dividing by a zero length"
    (is (= [] (d/dashes dims [(:cx dims) (:cy dims)])))
    (is (= [] (d/dashes dims [(+ (:cx dims) 1e-9) (:cy dims)])))))

(deftest it-follows-a-finger-and-drifts-without-one
  (let [base {:t 0 :target [0.0 0.0] :touching? false}]
    (let [s (d/advance base {:metrics metrics :pointer {:phase :down :position [300 700]}})]
      (is (:touching? s))
      (is (= [300.0 700.0] (:target s))))
    (let [a (d/advance base {:metrics metrics :pointer {:phase :idle :position nil}})
          b (d/advance a {:metrics metrics :pointer {:phase :idle :position nil}})]
      (is (not (:touching? a)))
      (is (not= (:target a) (:target b))))))
