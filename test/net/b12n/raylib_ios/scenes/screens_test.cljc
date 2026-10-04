(ns net.b12n.raylib-ios.scenes.screens-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.screens :as scr]))

(def m {:screen [1206 2334]})
(def screen-sizes [[1206 2334] [2334 1206] [800 450] [450 800]])
(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))

(defn- init-on [screen] (first ((:init (scr/scene)) {:metrics {:screen screen}})))
(def start (init-on [1206 2334]))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (first ((:update (scr/scene)) state {:metrics metrics
                                        :pointer {:phase phase
                                                  :position position}}))))

(defn- idle-frames
  "`n` frames with no finger."
  [state n]
  (nth (iterate #(step % :idle nil) state) n))

(defn- tap
  "Press, hold one frame and release at `pt`, three frames in all."
  [state pt]
  (-> state
      (step :press pt)
      (step :down pt)
      (step :release [1.0 1.0])))

(def below-back [600.0 1200.0])

(deftest a-tap-advances-a-screen
  (is (= :logo (:screen start)))
  (is (= [:title :gameplay :ending]
         (rest (map :screen (take 4 (iterate #(tap % below-back) start))))))
  (testing "nothing happens until the finger lifts"
    (is (= :logo (:screen (step (step start :press below-back) :down below-back))))))

(deftest the-timer-advances-a-screen
  (testing "frame-locked, every 90th frame, like the original"
    (is (= :logo (:screen (idle-frames start 89))))
    (is (= :title (:screen (idle-frames start 90))))
    (is (= :gameplay (:screen (idle-frames start 180))))
    (is (= :ending (:screen (idle-frames start 270)))))
  (testing "the counter is not reset by a tap, as in the original"
    (let [tapped (tap (idle-frames start 10) below-back)]
      (is (= :title (:screen tapped)))
      (is (= :title (:screen (idle-frames tapped 76))) "frame 89 is not yet reached")
      (is (= :gameplay (:screen (idle-frames tapped 77))) "the 90th frame overall advances"))))

(deftest tap-and-timer-on-one-frame-advance-once
  (let [pressed (step (idle-frames start 87) :press below-back)
        down (step pressed :down below-back)
        ;; press on frame 88, down on 89, so the release lands on frame 90
        _ (is (= :logo (:screen down)))
        both (step down :release [1.0 1.0])]
    (is (= 90 (:frame both)))
    (is (= :title (:screen both)) "one step, not two")))

(deftest a-timer-advance-drops-a-touch-in-flight
  (testing "a finger down when the timer fires does not also advance on lift"
    (let [down (-> (idle-frames start 87)
                   (step :press below-back)
                   (step :down below-back))
          fired (step down :down below-back)]
      (is (= :logo (:screen down)))
      (is (= :title (:screen fired)))
      (is (= :title (:screen (step fired :release [1.0 1.0]))))))
  (testing "a touch that starts on the new screen advances it"
    (is (= :gameplay (:screen (tap (idle-frames start 90) below-back)))))
  (testing "a press landing on the timer frame itself is not swallowed"
    (let [fired (step (idle-frames start 89) :press below-back)]
      (is (= 90 (:frame fired)))
      (is (= :title (:screen fired)))
      (is (= :gameplay (:screen (-> fired
                                    (step :down below-back)
                                    (step :release [1.0 1.0]))))
          "the tap steps once more, in addition to the timer's"))))

(deftest ending-goes-where-the-original-goes
  (let [ending (nth (iterate #(tap % below-back) start) 3)]
    (is (= :ending (:screen ending)))
    (is (= :logo (:screen (tap ending below-back))) "back to LOGO, not TITLE"))
  (is (= :logo (:screen (idle-frames start 360)))))

(deftest a-tap-under-back-does-nothing
  (let [pt [100.0 50.0]]
    (is (gesture/in-back-region? pt))
    (is (= :logo (:screen (tap start pt))))
    (testing "a touch that starts below Back and drags into it is a swipe, not a tap"
      (is (= :logo (:screen (-> start
                                (step :press below-back)
                                (step :down pt)
                                (step :release [1.0 1.0]))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screen-sizes
          :let [[w h] screen
                dims (scr/dimensions {:screen screen})]
          s scr/order
          :let [lines (scr/lines s)]]
    (testing (str screen s)
      (doseq [[{:keys [x y size]} [text _]] (map vector (:rows dims) lines)]
        (is (>= x 0) text)
        (is (<= (+ x (* 0.6 size (count text))) w) text)
        (is (>= y back-bottom) text)
        (is (<= (+ y size) h) text)))))

(deftest every-screen-has-a-colour-and-a-label
  (is (= [:logo :title :gameplay :ending] scr/order))
  (is (= 4 (count (set (map scr/background scr/order)))))
  (is (= "LOGO" (first (first (scr/lines :logo)))))
  (is (= "ENDING" (first (first (scr/lines :ending))))))

(deftest the-scene-has-its-id
  (let [sc (scr/scene)]
    (is (= :screens (:id sc)))
    (is (= "Screen Manager" (:title sc)))))
