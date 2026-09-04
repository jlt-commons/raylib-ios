(ns raylib.scenes.collision-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.collision :as c]))

(def metrics {:screen [1206 2334]})
(def dims (c/dimensions metrics))

(deftest intersection-is-the-overlap-or-nothing
  (is (= [5 5 5 5] (c/intersection [0 0 10 10] [5 5 10 10])))
  (is (= [0 0 10 10] (c/intersection [0 0 10 10] [0 0 10 10])) "identical boxes")
  (is (= [2 2 4 4] (c/intersection [0 0 10 10] [2 2 4 4])) "one inside the other")
  (testing "no overlap gives nil, not a zero-sized box, so a caller cannot draw
            a degenerate rectangle and read it as contact"
    (is (nil? (c/intersection [0 0 10 10] [20 20 5 5])))
    (is (nil? (c/intersection [0 0 10 10] [10 0 5 5])) "edges touching is not overlap")
    (is (nil? (c/intersection [0 0 10 10] [0 10 5 5])))))

(deftest intersection-is-symmetric
  (doseq [[a b] [[[0 0 10 10] [5 5 10 10]]
                 [[3 4 20 6] [1 1 9 9]]
                 [[0 0 1 1] [50 50 1 1]]]]
    (is (= (c/intersection a b) (c/intersection b a)))))

(deftest the-slider-bounces-and-stays-on-screen
  (let [{:keys [w slider-w]} dims
        run (reductions (fn [s _] (c/advance s {:metrics metrics}))
                        {:x 0.0 :vx c/slide-speed :t 0 :target [0.0 0.0] :touching? false}
                        (range 2000))]
    (testing "it never leaves the screen"
      (doseq [{:keys [x]} run]
        (is (<= 0.0 x (- w slider-w)))))
    (testing "and it turns round rather than sticking to an edge"
      (is (some #(neg? (:vx %)) run))
      (is (some #(pos? (:vx %)) run)))))

(deftest the-follower-tracks-a-finger-and-drifts-without-one
  (let [base {:x 0.0 :vx 1.0 :t 0 :target [0.0 0.0] :touching? false}]
    (testing "a press puts it on the finger"
      (let [s (c/advance base {:metrics metrics
                               :pointer {:phase :press :position [400 900]}})]
        (is (:touching? s))
        (is (= [400.0 900.0] (:target s)))))
    (testing "and with nothing touching it moves on its own, so the scene is
              alive in a gallery nobody is holding"
      (let [a (c/advance base {:metrics metrics :pointer {:phase :idle :position nil}})
            b (c/advance a {:metrics metrics :pointer {:phase :idle :position nil}})]
        (is (not (:touching? a)))
        (is (not= (:target a) (:target b)))))))

(deftest the-follower-stays-fully-on-screen
  (testing "even when the finger is at a corner, which is where an unclamped
            box would hang half off"
    (let [{:keys [w h finger-w finger-h]} dims]
      (doseq [pt [[0.0 0.0] [w h] [-50.0 -50.0] [(* 2 w) (* 2 h)]]]
        (let [[bx by bw bh] (c/finger-box dims pt)]
          (is (<= 0.0 bx (- w finger-w)))
          (is (<= 0.0 by (- h finger-h)))
          (is (= finger-w bw)) (is (= finger-h bh)))))))
