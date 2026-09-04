(ns raylib.scenes.analog-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.analog :as a]))

(def d (a/dimensions {:screen [1206 2334]}))

(deftest twelve-oclock-points-straight-up
  (testing "zero degrees is up and the angle grows clockwise, which is the
            convention every other circular scene here uses too"
    (let [[x y] (a/polar 100.0 100.0 50.0 0)]
      (is (< (abs (- 100.0 x)) 1e-9))
      (is (< (abs (- 50.0 y)) 1e-9) "up is a SMALLER y"))
    (let [[x y] (a/polar 100.0 100.0 50.0 90)]
      (is (< (abs (- 150.0 x)) 1e-9) "three o'clock is to the right")
      (is (< (abs (- 100.0 y)) 1e-9)))
    (let [[_ y] (a/polar 100.0 100.0 50.0 180)]
      (is (< (abs (- 150.0 y)) 1e-9) "six o'clock is down"))))

(deftest each-hand-carries-the-one-below-it
  (testing "half past twelve puts the hour hand halfway to one, not still on
            twelve. Without this the hour hand is wrong for 59 minutes an hour."
    (let [{:keys [hour]} (a/hand-angles [12 30 0] 0.0)]
      (is (< (abs (- 15.0 hour)) 1e-6) "half of the 30-degree hour step")))
  (testing "and the minute hand carries the seconds"
    (let [{:keys [minute]} (a/hand-angles [0 10 30] 0.0)]
      (is (< (abs (- (+ 60.0 3.0) minute)) 1e-6))))
  (testing "and the second hand sweeps rather than ticking"
    (let [a0 (:second (a/hand-angles [0 0 5] 0.0))
          a1 (:second (a/hand-angles [0 0 5] 0.5))]
      (is (< a0 a1))
      (is (< (abs (- 3.0 (- a1 a0))) 1e-9) "half a second is half of six degrees"))))

(deftest noon-and-midnight-both-read-as-twelve
  (doseq [h [0 12 24]]
    (let [{:keys [hour]} (a/hand-angles [h 0 0] 0.0)]
      (is (< (abs (mod hour 360.0)) 1e-9) (str "hour " h)))))

(deftest a-full-hour-takes-the-hour-hand-exactly-one-step
  (let [a0 (:hour (a/hand-angles [3 0 0] 0.0))
        a1 (:hour (a/hand-angles [4 0 0] 0.0))]
    (is (< (abs (- 30.0 (- a1 a0))) 1e-9))))

(deftest there-are-sixty-ticks-and-twelve-are-long
  (let [ts (a/ticks d)]
    (is (= 60 (count ts)))
    (is (= 12 (count (filter (fn [t] (nth t 4)) ts))))
    (testing "and every tick sits inside the bezel"
      (doseq [[x0 y0 x1 y1 _] ts]
        (doseq [[x y] [[x0 y0] [x1 y1]]]
          (let [dist (Math/sqrt (+ (* (- x (:cx d)) (- x (:cx d)))
                                   (* (- y (:cy d)) (- y (:cy d)))))]
            (is (< dist (:r d)))))))))

(defn- tick [state sec dt] (a/advance state {:local-time [1 2 sec] :delta-seconds dt}))

(deftest the-fraction-is-phased-to-the-wall-clock-not-free-running
  (testing "a change of second snaps it to zero. Free-running, it wraps whenever
            its own accumulation says to, which is out of phase with the clock
            it subdivides: measured on device the hand went .95 then .02 while
            the second was still 12, jumping backward 5.6 degrees mid-second."
    (let [s (-> {:frac 0.0 :sec nil}
                (tick 12 0.0) (tick 12 0.4) (tick 12 0.4) (tick 12 0.4))]
      (is (= 12 (:sec s)))
      (is (< (abs (- 0.999 (:frac s))) 1e-9) "clamped, not past the tick")
      (let [s2 (tick s 13 0.4)]
        (is (= 13 (:sec s2)))
        (is (zero? (:frac s2)) "and the new second starts from zero")))))

(deftest the-hand-never-goes-backwards-within-a-second
  (testing "which is the property the eye is actually watching"
    (let [run (reductions (fn [st [sec dt]] (tick st sec dt))
                          {:frac 0.0 :sec nil}
                          (for [sec (range 5) _ (range 6)] [sec 0.17]))
          angles (map (fn [st] (:second (a/hand-angles [0 0 (or (:sec st) 0)] (:frac st)))) run)]
      (doseq [[a b] (map vector angles (rest angles))]
        (is (<= a b) "monotonic across ticks and within them")))))

(deftest a-slow-frame-cannot-push-the-hand-past-the-tick
  (let [s (-> {:frac 0.0 :sec nil} (tick 5 0.0) (tick 5 10.0))]
    (is (< (:frac s) 1.0))))
