(ns raylib.scenes.lorenz-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.lorenz :as l]))

(def metrics {:screen [1206 2622]})

(deftest the-trail-is-a-fixed-length-window
  (let [[points seed] (l/warm l/default-seed)]
    (testing "warming up fills the trail exactly"
      (is (= @l/trail-length (count points))))
    (testing "and advancing keeps it there, so the per-frame cost stays flat"
      (is (= @l/trail-length (count (l/advance points))))
      (is (= @l/trail-length (count (l/advance (l/advance points))))))
    (testing "the seed is carried on for a reproducible replay"
      (is (integer? seed)))))

(deftest warming-is-deterministic
  (testing "the same seed gives the same trail, since randomness is the LCG"
    (is (= (first (l/warm 42)) (first (l/warm 42)))))
  (testing "and different seeds do not"
    (is (not= (first (l/warm 42)) (first (l/warm 43))))))

(deftest the-trajectory-stays-on-the-attractor
  (let [[points _] (l/warm l/default-seed)]
    (testing "nothing diverges or goes NaN, which checks the step size holds up.\n            (== c c) is false only for NaN, and unlike Double/isNaN it reads\n            the same under every .cljc host"
      (is (every? (fn [p] (every? (fn [c] (and (== c c) (< (abs c) 100.0))) p))
                  points)))
    (testing "z stays positive, as the attractor sits well above the xy plane"
      (is (every? (fn [[_ _ z]] (pos? z)) points)))
    (testing "and the opening window shows both wings, not half a butterfly"
      (is (l/spans-both-lobes? points)))))

(deftest the-trail-keeps-showing-the-butterfly-as-it-runs
  (testing "a 450-point window is short enough to sit inside one lobe, so this
            asserts what a viewer sees over time rather than at one instant"
    (let [[points _] (l/warm l/default-seed)
          spans (loop [pts points frame 0 n 0]
                  (if (= frame 600)
                    n
                    (let [pts (l/advance pts)]
                      (recur pts (inc frame) (if (l/spans-both-lobes? pts) (inc n) n)))))]
      ;; measured at 499 of 600 before warm was taught to open on both wings
      (is (> spans 400) (str "only " spans " of 600 frames showed both lobes")))))

(deftest advancing-moves-the-head-and-keeps-the-tail
  (let [[points _] (l/warm l/default-seed)
        advanced (l/advance points)]
    (testing "the head is new"
      (is (not= (peek points) (peek advanced))))
    (testing "and the overlap is the old trail, shifted by the step count"
      (is (= (subvec points l/steps-per-frame)
             (subvec advanced 0 (- (count points) l/steps-per-frame)))))))

(deftest projection-drops-points-behind-the-camera
  (let [cam (l/camera metrics 0.0)]
    (testing "a point in front projects"
      (is (some? (l/project cam [0.0 0.0 25.0]))))
    (testing "one far behind the camera does not, rather than wrapping"
      (is (nil? (l/project cam [0.0 200.0 25.0]))))))

(deftest the-camera-orbits
  (testing "a different time gives a different projection of the same point"
    (let [p [8.0 8.0 27.0]]
      (is (not= (l/project (l/camera metrics 0.0) p)
                (l/project (l/camera metrics 1.0) p))))))

(deftest trail-colour-ramps-without-going-out-of-range
  (testing "every channel stays a byte across the whole trail"
    (doseq [age [0.0 0.25 0.5 0.75 1.0]]
      (is (every? (fn [c] (<= 0 c 255)) (l/trail-colour age)))))
  (testing "the tail and the head differ, so age is visible"
    (is (not= (l/trail-colour 0.0) (l/trail-colour 1.0)))))
