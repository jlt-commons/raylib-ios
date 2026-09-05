(ns raylib.scenes.fan-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.fan :as fan]))

(def d (fan/dimensions {:screen [1206 2334]}))

(deftest every-spoke-has-a-positive-width
  (testing "a width of zero produces a degenerate quad that draws nothing, which
            in a fan reads as a missing spoke rather than as an error"
    (doseq [t [0.0 3.1 17.7] i (range fan/spokes)]
      (is (pos? (:thick (fan/spoke d t i))) (str "spoke " i)))))

(deftest widths-grow-around-the-fan
  (testing "monotonic, so a spoke that fails to draw is read as a gap at a known
            width rather than as noise"
    (let [ts (map (fn [i] (:thick (fan/spoke d 0.0 i))) (range fan/spokes))]
      (is (apply < ts))
      (is (= fan/spokes (count (distinct ts)))))))

(deftest the-thin-end-is-genuinely-thin-and-the-fat-end-is-the-full-width
  (let [thin (:thick (fan/spoke d 0.0 0))
        fat (:thick (fan/spoke d 0.0 (dec fan/spokes)))]
    (is (< thin 1.0) (str "under a pixel, measured " thin))
    (is (> fat (* 0.9 (:max-thick d))))
    (is (<= fat (:max-thick d)))))

(deftest spokes-are-evenly-spaced-and-turn-together
  (testing "the fan is rigid: turning changes every spoke's angle by the same
            amount, so the pattern rotates rather than deforming"
    (let [ang (fn [t i] (let [{:keys [from to]} (fan/spoke d t i)
                              [x1 y1] from [x2 y2] to]
                          (Math/atan2 (- x2 x1) (- (- y2 y1)))))
          at0 (map (fn [i] (ang 0.0 i)) (range fan/spokes))
          at1 (map (fn [i] (ang 1.0 i)) (range fan/spokes))
          deltas (map (fn [a b] (Math/toDegrees (- b a))) at0 at1)]
      (doseq [dlt deltas]
        (is (< (abs (- fan/turn-rate (mod (+ dlt 360.0) 360.0))) 1e-6))))))

(deftest every-spoke-runs-outward-from-the-hub
  (doseq [i (range fan/spokes)]
    (let [{:keys [from to]} (fan/spoke d 0.0 i)
          dist (fn [[x y]] (Math/sqrt (+ (* (- x (:cx d)) (- x (:cx d)))
                                         (* (- y (:cy d)) (- y (:cy d))))))]
      (is (< (abs (- (:inner d) (dist from))) 1e-6) "starts on the inner circle")
      (is (< (abs (- (:outer d) (dist to))) 1e-6) "ends on the outer one"))))

(deftest the-fan-stays-on-screen
  (doseq [t (range 0.0 20.0 0.37) i (range fan/spokes)]
    (let [{:keys [from to]} (fan/spoke d t i)]
      (doseq [[x y] [from to]]
        (is (<= 0.0 x 1206.0))
        (is (<= 0.0 y 2334.0))))))

(deftest the-hues-cover-the-wheel-without-repeating
  (let [hues (map (fn [i] (:hue (fan/spoke d 0.0 i))) (range fan/spokes))]
    (is (= fan/spokes (count (distinct hues))))
    (is (< (apply max hues) 360.0) "no wrap, so no two spokes share a colour")
    (is (= 0.0 (apply min hues))))
  (testing "and the hue walk itself covers the primaries"
    (is (= [255 0 0] (fan/hsv->rgb 0.0)))
    (is (= [0 255 0] (fan/hsv->rgb 120.0)))
    (is (= [0 0 255] (fan/hsv->rgb 240.0)))))
