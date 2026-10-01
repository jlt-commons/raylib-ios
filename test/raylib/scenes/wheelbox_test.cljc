(ns raylib.scenes.wheelbox-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.wheelbox :as wb]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (wb/dimensions m))
(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))
(def start (first ((:init (wb/scene)) {:metrics m})))

(defn- adv
  ([state phase pos] (adv state m phase pos))
  ([state metrics phase pos]
   (wb/advance state {:metrics metrics
                      :pointer {:phase phase
                                :position pos}})))

(defn- idle [state] (adv state :idle nil))
(defn- near? [a b] (< (abs (double (- a b))) 1e-6))

(deftest a-drag-moves-the-box
  (let [pressed (adv start :press [600.0 1200.0])]
    (testing "a press alone changes nothing"
      (is (near? (:y start) (:y pressed))))
    (testing "up by 200 px moves the box up by 200"
      (is (near? (- (:y start) 200) (:y (adv pressed :down [600.0 1000.0])))))
    (testing "down by 200 px moves it down by 200"
      (is (near? (+ (:y start) 200) (:y (adv pressed :down [600.0 1400.0])))))
    (testing "dragging out and back to the start puts the box back where it began"
      (is (near? (:y start)
                 (:y (-> pressed (adv :down [600.0 1000.0]) (adv :down [600.0 1200.0]))))))
    (testing "sideways travel does nothing"
      (is (near? (:y start) (:y (adv pressed :down [100.0 1200.0])))))
    (testing "a second drag continues from where the first left the box"
      (let [dropped (-> pressed (adv :down [600.0 1100.0]) (adv :release [600.0 1100.0]) idle)
            again (-> dropped (adv :press [300.0 1500.0]) (adv :down [300.0 1450.0]))]
        (is (near? (- (:y start) 100) (:y dropped)))
        (is (near? (- (:y start) 150) (:y again)))))
    (testing "a press in Back anchors nothing"
      (is (near? (:y start) (:y (-> start (adv :press [100.0 50.0]) (adv :down [100.0 1000.0]))))))
    (testing "x never changes"
      (is (near? (:box-x d) (/ (- 1206 (:side d)) 2.0))))))

(deftest the-release-swipe-does-not-move-it-again
  (let [down (-> start (adv :press [600.0 1500.0]) (adv :down [600.0 1200.0]) (adv :down [600.0 900.0]))
        released (adv down :release [600.0 900.0])]
    (is (not= (:y start) (:y down)))
    (testing "the gesture layer does report this drag as a swipe on release"
      (let [pointer (fn [g phase pos] (first (gesture/track g {:metrics m
                                                               :pointer {:phase phase
                                                                         :position pos}})))
            g (-> gesture/idle
                  (pointer :press [600.0 1500.0])
                  (pointer :down [600.0 1200.0])
                  (pointer :down [600.0 900.0]))]
        (is (= :swipe (:type (second (gesture/track g {:metrics m
                                                       :pointer {:phase :release
                                                                 :position [600.0 900.0]}})))))))
    (is (near? (:y down) (:y released)))
    (is (near? (:y down) (:y (idle released))))
    (testing "the release position is never read"
      (is (near? (:y down) (:y (adv down :release [0.0 0.0])))))))

(deftest the-box-stays-inside
  (let [{:keys [min-y max-y]} d
        pressed (adv start :press [600.0 1200.0])]
    (is (near? min-y (:y (adv pressed :down [600.0 -5000.0]))))
    (is (near? max-y (:y (adv pressed :down [600.0 9000.0]))))
    (is (>= min-y back-bottom) "never under Back")
    (is (<= (+ max-y (:side d)) 2334) "never past the bottom")
    (testing "a drag back from the wall is measured from the finger, not the wall"
      (let [pinned (adv pressed :down [600.0 -5000.0])]
        (is (near? (:y start) (:y (adv pinned :down [600.0 1200.0]))))))
    (testing "a rotation pulls the box back in"
      (let [far (assoc start :y 2000.0)
            landscape {:screen [2334 1206]}
            {:keys [max-y side]} (wb/dimensions landscape)
            y (:y (adv far landscape :idle nil))]
        (is (<= (+ y side) 1206.0))
        (is (near? max-y y))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [caption side fw fh]} (wb/dimensions {:screen screen})
                {:keys [s x y size]} caption]]
    (testing (str screen)
      (is (<= 0 x))
      (is (<= (+ x (* 0.6 size (count s))) w))
      (is (>= y back-bottom))
      (is (<= (+ y size) h))
      (is (<= side (min fw fh)) "the box fits the field"))))
