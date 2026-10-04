(ns net.b12n.raylib-ios.scenes.bounce-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.bounce :as b]))

(def m {:screen [1206 2334]})
(def d (b/dimensions m))
(def start (first ((:init (b/scene)) {:metrics m})))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (b/advance state {:metrics metrics
                     :pointer {:phase phase
                               :position position}})))

(defn- idle [state] (step state :idle nil))

(defn- tap
  "A whole tap at `pos`: a press, then a release. The tap event arrives on the
  release frame, from where the finger started."
  [state pos]
  (-> state (step :press pos) (step :release pos)))

(defn- with-ball [x y vx vy]
  (assoc start :pos [x y] :vel [vx vy]))

(deftest the-ball-bounces-off-each-wall
  (let [r (:radius d)
        w (:w d)
        h (:h d)]
    (testing "left wall"
      (let [{[x _] :pos
             [vx _] :vel} (idle (with-ball (+ r 1.0) 900.0 -13.0 0.0))]
        (is (= r x))
        (is (pos? vx))))
    (testing "right wall"
      (let [{[x _] :pos
             [vx _] :vel} (idle (with-ball (- w r 1.0) 900.0 13.0 0.0))]
        (is (= (- w r) x))
        (is (neg? vx))))
    (testing "top wall"
      (let [{[_ y] :pos
             [_ vy] :vel} (idle (with-ball 600.0 (+ r 1.0) 0.0 -10.0))]
        (is (= r y))
        (is (pos? vy))))
    (testing "bottom wall"
      (let [{[_ y] :pos
             [_ vy] :vel} (idle (with-ball 600.0 (- h r 1.0) 0.0 10.0))]
        (is (= (- h r) y))
        (is (neg? vy))))
    (testing "a ball in open space just moves"
      (let [{[x y] :pos
             [vx vy] :vel} (idle (with-ball 600.0 900.0 13.0 10.0))]
        (is (= [613.0 910.0] [x y]))
        (is (= [13.0 10.0] [vx vy]))))))

(deftest a-tap-pauses-and-resumes
  (let [paused (tap start [600 1500])
        resumed (tap paused [600 1500])]
    (is (not (:paused? start)))
    (is (:paused? paused))
    (is (not (:paused? resumed)))
    (testing "a press alone, before the finger lifts, is not yet a tap"
      (is (not (:paused? (step start :press [600 1500])))))))

(deftest a-paused-ball-does-not-move
  (let [paused (assoc start :paused? true)]
    (is (= (:pos paused) (:pos (idle paused))))
    (is (= (:pos paused) (:pos (nth (iterate idle paused) 30))))
    (testing "and an unpaused one does"
      (is (not= (:pos start) (:pos (idle start)))))))

(deftest a-tap-under-back-does-not-pause
  (is (not (:paused? (tap start [100 60]))))
  (is (not (:paused? (tap start [399 119]))))
  (testing "just outside the Back region does pause"
    (is (:paused? (tap start [400 119])))
    (is (:paused? (tap start [100 120])))))

(deftest a-swipe-does-not-pause
  (let [after (-> start (step :press [600 1500]) (step :down [900 1500]) (step :release [900 1500]))]
    (is (not (:paused? after)))))

(deftest the-ball-stays-inside-after-rotation
  (let [landscape {:screen [2334 1206]}
        r-land (:radius (b/dimensions landscape))
        far (with-ball 1100.0 2200.0 13.0 10.0)
        {[x y] :pos
         :as after} (step far landscape :idle nil)]
    (is (<= r-land x (- 2334 r-land)))
    (is (<= r-land y (- 1206 r-land)))
    (is (= [2334 1206] (:screen after)))
    (testing "and back to portrait from the far right"
      (let [right (assoc after :pos [2200.0 1000.0])
            [px py] (:pos (step right m :idle nil))
            r (:radius d)]
        (is (<= r px (- 1206 r)))
        (is (<= r py (- 2334 r)))))
    (testing "the pause and the heading survive"
      (let [p (step (assoc far :paused? true) landscape :idle nil)]
        (is (:paused? p))
        (is (= [13.0 10.0] (:vel p)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen [[1206 2334] [2334 1206]]
          :let [[w h] screen
                dm (b/dimensions {:screen screen})
                {:keys [hint-x hint-y hint-size paused-x paused-y paused-size]} dm
                ;; estimate: 0.6 of the size per character, for raylib's default font.
                hint-w (* 0.6 hint-size (count b/hint-line))
                paused-w (* 0.6 paused-size (count b/paused-line))]]
    (testing (str screen)
      (is (<= 0 hint-x))
      (is (<= (+ hint-x hint-w) w))
      (is (<= 0 hint-y))
      (is (<= (+ hint-y hint-size) h))
      (is (<= (+ paused-x paused-w) w))
      (is (<= 0 paused-y))
      (is (<= (+ paused-y paused-size) h))
      (testing "PAUSED is clear of the Back region"
        (is (or (>= paused-x 400) (>= paused-y 120)))))))

(deftest the-gesture-is-kept-in-state
  (is (= gesture/idle (:gesture start)))
  (is (= [600 1500] (:start (:gesture (step start :press [600 1500]))))))
