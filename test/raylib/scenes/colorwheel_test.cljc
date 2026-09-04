(ns raylib.scenes.colorwheel-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.colorwheel :as c]))

(def metrics {:screen [1206 2334]})

(deftest hsv-hits-the-primaries-and-wraps
  (testing "the six corners of the hue hexagon"
    (is (= [255 0 0]   (c/hsv->rgb 0)))
    (is (= [255 255 0] (c/hsv->rgb 60)))
    (is (= [0 255 0]   (c/hsv->rgb 120)))
    (is (= [0 255 255] (c/hsv->rgb 180)))
    (is (= [0 0 255]   (c/hsv->rgb 240)))
    (is (= [255 0 255] (c/hsv->rgb 300))))
  (testing "hue is an angle, so it wraps in both directions"
    (is (= (c/hsv->rgb 0) (c/hsv->rgb 360)))
    (is (= (c/hsv->rgb 30) (c/hsv->rgb 390)))
    (is (= (c/hsv->rgb 120) (c/hsv->rgb -240))))
  (testing "at full saturation and value one channel is always 255 and one 0,
            which is what puts every hue on the cylinder's outer surface"
    (doseq [h (range 0 360 7)]
      (let [rgb (c/hsv->rgb h)]
        (is (some #{255} rgb) (str "hue " h " " rgb))
        (is (some #{0} rgb) (str "hue " h " " rgb))))))

(deftest every-channel-stays-a-byte
  (doseq [h (range -720 1080 13)]
    (is (every? #(<= 0 % 255) (c/hsv->rgb h)) (str "hue " h))))

(deftest slices-close-the-circle
  (let [d (c/dimensions metrics)]
    (testing "slice i ends exactly where slice i+1 begins, so there are no gaps"
      (doseq [i (range (dec c/slices))]
        (let [a (c/slice d 0.0 i)
              b (c/slice d 0.0 (inc i))]
          (is (< (abs (- (nth a 3) (nth b 0))) 1e-9))
          (is (< (abs (- (nth a 4) (nth b 1))) 1e-9)))))
    (testing "and the last one meets the first"
      (let [last-slice (c/slice d 0.0 (dec c/slices))
            first-slice (c/slice d 0.0 0)]
        (is (< (abs (- (nth last-slice 3) (nth first-slice 0))) 1e-9))
        (is (< (abs (- (nth last-slice 4) (nth first-slice 1))) 1e-9))))))

(deftest every-rim-vertex-is-on-the-rim
  (let [{:keys [cx cy radius] :as d} (c/dimensions metrics)]
    (doseq [i (range c/slices)]
      (let [s (c/slice d 0.0 i)
            dist (fn [x y] (Math/sqrt (+ (* (- x cx) (- x cx)) (* (- y cy) (- y cy)))))]
        (is (< (abs (- radius (dist (nth s 0) (nth s 1)))) 1e-9))
        (is (< (abs (- radius (dist (nth s 3) (nth s 4)))) 1e-9))))))

(deftest the-wheel-fits-the-screen
  (let [{:keys [cx cy radius]} (c/dimensions metrics)]
    (is (<= radius (min cx cy)) "the wheel is inside the shorter half-axis")))
