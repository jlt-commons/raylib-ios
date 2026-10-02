(ns raylib.camera2d-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.camera2d :as cam]))

(def cameras
  [{:offset [0.0 0.0]
    :target [0.0 0.0]
    :rotation 0.0
    :zoom 1.0}
   {:offset [300.0 400.0]
    :target [10.0 -20.0]
    :rotation 0.0
    :zoom 2.5}
   {:offset [300.0 400.0]
    :target [120.0 80.0]
    :rotation 37.0
    :zoom 0.5}
   {:offset [-50.0 75.0]
    :target [5.0 5.0]
    :rotation -123.0
    :zoom 3.0}
   {:offset [600.0 900.0]
    :target [0.0 0.0]
    :rotation 90.0
    :zoom 1.0}])

(def points [[0.0 0.0] [100.0 50.0] [-30.0 220.0] [640.0 -480.0]])

(defn- near? [[ax ay] [bx by]]
  (and (< (Math/abs (- ax bx)) 1e-6) (< (Math/abs (- ay by)) 1e-6)))

(deftest world-to-screen-and-back-is-identity
  (doseq [c cameras p points]
    (is (near? p (cam/screen->world c (cam/world->screen c p))) (pr-str [c p]))
    (is (near? p (cam/world->screen c (cam/screen->world c p))) (pr-str [c p]))))

(deftest matches-raylibs-camera-matrix
  ;; GetCameraMatrix2D: a point moves by -target, scales by zoom, rotates by
  ;; `rotation` (x' = x cos - y sin, y' = x sin + y cos), then moves by offset.
  (testing "the target lands on the offset"
    (is (near? [300.0 400.0]
               (cam/world->screen (nth cameras 2) [120.0 80.0]))))
  (testing "zoom only"
    (is (near? [325.0 362.5]
               (cam/world->screen (nth cameras 1) [20.0 -35.0]))))
  (testing "a quarter turn sends +x to +y"
    (is (near? [500.0 900.0]
               (cam/world->screen (nth cameras 4) [0.0 100.0]))
        "(0,100) rotated 90 degrees is (-100,0)")
    (is (near? [600.0 1000.0]
               (cam/world->screen (nth cameras 4) [100.0 0.0]))))
  (testing "rotation and zoom together"
    ;; (10,0) - target(0,0), x2 = (20,0), rotated 90 = (0,20), plus offset
    (is (near? [100.0 220.0]
               (cam/world->screen {:offset [100.0 200.0]
                                   :target [0.0 0.0]
                                   :rotation 90.0
                                   :zoom 2.0}
                                  [10.0 0.0])))))

(deftest pinch-ignores-point-order
  (let [a [100.0 200.0] b [340.0 120.0]
        p (cam/pinch a b) q (cam/pinch b a)]
    (is (near? (:mid p) (:mid q)))
    (is (< (Math/abs (- (:dist p) (:dist q))) 1e-9))
    (is (< (Math/abs (- (:angle p) (:angle q))) 1e-9))
    (let [prev (cam/pinch [0.0 0.0] [100.0 0.0])
          cur  (cam/pinch [200.0 0.0] [0.0 0.0])
          s    (cam/pinch-step prev cur)]
      (is (< (Math/abs (- 2.0 (:ratio s))) 1e-9))
      (is (< (Math/abs (:twist s)) 1e-9)))))

(deftest twist-wraps-across-pi
  (testing "a line just past horizontal one way to just past it the other"
    (let [prev (cam/pinch [0.0 0.0] [100.0 -1.0])
          cur  (cam/pinch [0.0 0.0] [100.0 1.0])
          tw   (:twist (cam/pinch-step prev cur))]
      (is (< 0.0 tw 2.0))
      (is (< (Math/abs (- tw (* 2.0 (Math/toDegrees (Math/atan2 0.01 1.0))))) 0.02))))
  (testing "and the reverse is the negative"
    (let [prev (cam/pinch [0.0 0.0] [100.0 1.0])
          cur  (cam/pinch [0.0 0.0] [100.0 -1.0])]
      (is (< -2.0 (:twist (cam/pinch-step prev cur)) 0.0))))
  (testing "a quarter turn sits on the (-90, 90] boundary as +90"
    (is (< (Math/abs (- 90.0 (:twist (cam/pinch-step (cam/pinch [0.0 0.0] [100.0 0.0])
                                                     (cam/pinch [0.0 0.0] [0.0 100.0])))))
           1e-9)))
  (testing "angle is in [0, 180)"
    (doseq [q [[100.0 0.0] [-100.0 0.0] [0.0 -100.0] [-50.0 -50.0]]]
      (is (<= 0.0 (:angle (cam/pinch [0.0 0.0] q)) 180.0)))))

(deftest pin-keeps-the-world-point-under-the-screen-point
  (doseq [c cameras
          s [[10.0 20.0] [500.0 700.0]]]
    (let [w  (cam/screen->world c s)
          c' (cam/pin c s)]
      (is (near? s (cam/world->screen c' w)) "the world point stays under the screen point")
      (is (near? w (:target c')))
      (is (near? s (:offset c')))
      (is (= (:zoom c) (:zoom c')))
      (is (= (:rotation c) (:rotation c'))))
    ;; zooming after the pin leaves the pinned point where it was
    (let [c' (assoc (cam/pin c [500.0 700.0]) :zoom 7.0)]
      (is (near? [500.0 700.0] (cam/world->screen c' (:target c')))))))

(deftest a-zero-distance-pinch-keeps-the-ratio-at-one
  (let [apart (cam/pinch [0.0 0.0] [100.0 0.0])
        same  (cam/pinch [50.0 50.0] [50.0 50.0])]
    (is (= 1.0 (:ratio (cam/pinch-step apart same))) "current distance 0")
    (is (= 1.0 (:ratio (cam/pinch-step same apart))) "previous distance 0")
    (is (= 1.0 (:ratio (cam/pinch-step same same))) "both 0")))

(deftest a-pinch-acts-only-while-the-finger-count-stays-at-two
  (let [a [400.0 1200.0]
        b [500.0 1200.0]
        c [900.0 1500.0]
        frame (fn [prev pts] (cam/pinch-frame prev pts))
        steps (fn [& frames]
                ;; the step of each frame, the pinch carried frame to frame
                (loop [prev nil
                       [pts & more] frames
                       out []]
                  (if (nil? pts)
                    out
                    (let [{:keys [pinch step]} (frame prev pts)]
                      (recur pinch more (conj out step))))))]
    (testing "the first frame of two fingers only records"
      (let [f (frame nil [a b])]
        (is (some? (:pinch f)))
        (is (nil? (:step f)))))
    (testing "two fingers on two frames step, and the step is order-free"
      (let [s (second (steps [a b] [[350.0 1200.0] [550.0 1200.0]]))
            swapped (second (steps [a b] [[550.0 1200.0] [350.0 1200.0]]))]
        (is (< (abs (- 2.0 (:ratio s))) 1e-9))
        (is (< (abs (- (:ratio s) (:ratio swapped))) 1e-9))))
    (testing "three or more points record nothing and step nothing"
      (is (= {:pinch nil
              :step nil} (frame nil [a b c])))
      (is (= {:pinch nil
              :step nil} (frame (cam/pinch a b) [a b c]))))
    (testing "a reorder of three points moves nothing"
      (is (every? nil? (steps [a b c] [c a b] [b c a] [c b a]))))
    (testing "one of three lifting moves nothing"
      (let [[three lifted steady] (steps [a b c] [b c] [b c])]
        (is (nil? three))
        (is (nil? lifted) "the frame it lifts only records the pair that is left")
        (is (= 1.0 (:ratio steady)) "and a steady pair after that steps by nothing")))
    (testing "two, three, two moves nothing"
      (is (every? nil? (steps [a b] [a b c] [a b]))))
    (testing "fewer than two points end the pinch"
      (is (= {:pinch nil
              :step nil} (frame (cam/pinch a b) [a])))
      (is (= {:pinch nil
              :step nil} (frame (cam/pinch a b) []))))))
