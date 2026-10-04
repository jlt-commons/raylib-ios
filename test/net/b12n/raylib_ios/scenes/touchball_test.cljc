(ns net.b12n.raylib-ios.scenes.touchball-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.scenes.touchball :as tb]))

(def m {:screen [1206 2334]})
(def start (first ((:init (tb/scene)) {:metrics m})))

(defn- touch [state phase position]
  (tb/advance state {:metrics m
                     :pointer {:phase phase
                               :position position}}))

(deftest the-ball-starts-at-the-centre
  (is (= [603.0 1167.0] (:pos start)))
  (is (not (:touching? start))))

(deftest the-ball-follows-a-finger
  (let [a (touch start :press [200 300])
        b (touch a :down [250 400])]
    (is (= [200.0 300.0] (:pos a)))
    (is (= [250.0 400.0] (:pos b)))
    (is (:touching? b))))

(deftest the-ball-stays-put-when-the-finger-lifts
  (testing "an idle frame with no position, after a drag: nothing jumps to [0 0]"
    (let [dragged (touch start :down [250 400])
          idle (touch dragged :idle nil)]
      (is (= [250.0 400.0] (:pos idle)))
      (is (not (:touching? idle))))))

(deftest a-release-does-not-move-the-ball
  (testing "the release frame carries a stale hardware position"
    (let [dragged (touch start :down [250 400])
          released (touch dragged :release [1100 2200])]
      (is (= [250.0 400.0] (:pos released)))
      (is (not (:touching? released))))))

(deftest colour-tracks-touching
  (is (= tb/darkblue (tb/colour start)))
  (is (= tb/lime (tb/colour (touch start :press [10 10]))))
  (is (= tb/darkblue (tb/colour (touch (touch start :down [10 10]) :idle nil)))))

(deftest text-lines-fit-the-safe-region
  (let [[w h] (:screen m)
        {:keys [caption-size caption-x caption-y]} (tb/dimensions m)
        ;; estimate: 0.6 of the size per character, for raylib's default font
        char-w (* 0.6 caption-size)]
    (is (<= 0 caption-y))
    (is (<= (+ caption-y caption-size) h))
    (is (<= (+ caption-x (* char-w (count tb/caption))) w))))
