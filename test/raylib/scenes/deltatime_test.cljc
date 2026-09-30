(ns raylib.scenes.deltatime-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.deltatime :as dt]))

(defn- input [w dt-seconds]
  {:metrics {:screen [w 100]}
   :delta-seconds dt-seconds})

(defn- run [state w n dt-seconds]
  (nth (iterate (fn [s] (dt/advance s (input w dt-seconds))) state) n))

(def start {:xf 0.0
            :xd 0.0})

(deftest the-per-frame-box-ignores-dt
  (testing "that is the whole point of it: its speed is whatever the frame rate is"
    (let [a (dt/advance start (input 1000 0.0))
          b (dt/advance start (input 1000 0.1))]
      (is (= (:xf a) (:xf b)))
      (is (pos? (:xf a))))))

(deftest the-delta-box-covers-the-same-distance-per-second
  (testing "60 steps at 1/60 and 30 steps at 1/30 are each one second. The screen
            is wide so neither box wraps, since a wrap would hide the comparison."
    (let [w 100000
          at-60 (run start w 60 (/ 1.0 60.0))
          at-30 (run start w 30 (/ 1.0 30.0))]
      (is (< (abs (- (:xd at-60) (:xd at-30))) 1e-6))
      (is (pos? (:xd at-60)))
      (is (< (abs (- (* 2.0 (:xf at-30)) (:xf at-60))) 1e-6)
          "while the per-frame box covers half the distance at half the rate"))))

(deftest both-boxes-wrap-at-the-screen-width
  (let [w 300
        s (run start w 1000 (/ 1.0 60.0))]
    (is (<= 0.0 (:xf s) w))
    (is (< (:xf s) w))
    (is (<= 0.0 (:xd s)))
    (is (< (:xd s) w))))

(deftest a-negative-delta-cannot-move-the-box-backwards
  (let [s (dt/advance {:xf 0.0
                       :xd 50.0} (input 1000 -1.0))]
    (is (= 50.0 (:xd s)))))

(deftest the-boxes-stay-on-screen
  (let [{:keys [box top-y bottom-y]} (dt/dimensions {:screen [1206 2334]})
        h 2334]
    (doseq [y [top-y bottom-y]]
      (is (<= 0 y))
      (is (<= (+ y box) h)))
    (is (< (+ top-y box) bottom-y) "and the lanes do not overlap")))
