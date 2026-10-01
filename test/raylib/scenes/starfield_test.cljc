(ns raylib.scenes.starfield-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.starfield :as sf]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def start (first ((:init (sf/scene)) {:metrics m})))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (sf/advance state {:metrics metrics
                      :delta-seconds (/ 1.0 60)
                      :pointer {:phase phase
                                :position position}})))

(defn- idle [state] (step state :idle nil))

(defn- near? [a b] (< (abs (- (double a) (double b))) 1e-9))

(deftest dragging-up-speeds-up-and-down-slows
  (let [k (:k (sf/dimensions m))
        pressed (step start :press [600 1200])]
    (testing "a press alone changes nothing"
      (is (near? (:speed start) (:speed pressed))))
    (testing "up by 200 px speeds up by k times that"
      (is (near? (+ (:speed start) (* k 200))
                 (:speed (step pressed :down [600 1000])))))
    (testing "down by 200 px slows by the same"
      (is (near? (- (:speed start) (* k 200))
                 (:speed (step pressed :down [600 1400])))))
    (testing "the speed follows the finger, not the sum of its steps"
      (is (near? (:speed start)
                 (:speed (-> pressed (step :down [600 1000]) (step :down [600 1200]))))))))

(deftest speed-is-clamped
  (let [[_ h] (:screen m)
        pressed (step start :press [600 (- h 10)])]
    (is (near? sf/max-speed (:speed (step pressed :down [600 0]))))
    (is (near? sf/min-speed (:speed (-> start (step :press [600 10]) (step :down [600 (- h 1)])))))
    (testing "a full-height drag spans the original's range"
      (is (near? (- sf/max-speed sf/min-speed) (* (:k (sf/dimensions m)) h))))))

(deftest the-release-swipe-does-not-change-speed-again
  (let [down (-> start (step :press [600 1500]) (step :down [600 1200]) (step :down [600 900]))
        released (step down :release [600 900])]
    (is (not= (:speed start) (:speed down)))
    (is (= :swipe (:type (second (gesture/track (:gesture down)
                                                {:metrics m
                                                 :pointer {:phase :release
                                                           :position [600 900]}})))))
    (is (near? (:speed down) (:speed released)))
    (is (near? (:speed down) (:speed (idle released))))
    (testing "the release position is never read"
      (is (near? (:speed down) (:speed (step down :release [0 0])))))))

(deftest a-press-in-back-starts-no-drag
  (let [s (-> start (step :press [100 50]) (step :down [100 1000]))]
    (is (near? (:speed start) (:speed s)))))

(deftest a-tap-toggles-streaks
  (let [tap (fn [s pos] (-> s (step :press pos) (step :release pos)))
        once (tap start [600 1200])
        twice (tap once [600 1200])]
    (is (true? (:streaks? start)))
    (is (false? (:streaks? once)))
    (is (true? (:streaks? twice)))
    (testing "a tap in Back belongs to the host"
      (is (true? (:streaks? (tap start [100 50])))))
    (testing "a tap, even at nearly the full slop, barely changes the speed"
      (doseq [screen screens
              :let [metrics {:screen screen}
                    slop (gesture/slop metrics)
                    k (:k (sf/dimensions metrics))
                    s (-> start
                          (step metrics :press [600 600])
                          (step metrics :down [600 (- 600 (* 0.99 slop))])
                          (step metrics :release [600 (- 600 (* 0.99 slop))]))]]
        (is (< (* k slop) (* 0.05 (- sf/max-speed sf/min-speed))) screen)
        (is (false? (:streaks? s)) screen)))))

(deftest stars-respawn-at-the-far-plane
  (let [dims (sf/dimensions m)
        a-star {:nx 0.1
                :ny 0.1
                :z 0.001}
        s (-> start (assoc :stars [a-star]) idle)]
    (testing "a star that has passed the camera comes back at z 1"
      (is (= 1 (count (:stars s))))
      (is (= 1.0 (:z (first (:stars s))))))
    (testing "a star that leaves the screen comes back at z 1"
      (let [edge {:nx 0.49
                  :ny 0.0
                  :z 0.9}
            [px _] (sf/project dims (:nx edge) (:ny edge) 0.5)]
        (is (> px (:w dims)))
        (is (= 1.0 (:z (first (:stars (-> start (assoc :stars [(assoc edge :z 0.5)]) idle))))))))
    (testing "a star that stays on screen flies closer by dt * speed"
      (let [z (:z (first (:stars (-> start (assoc :stars [{:nx 0.0
                                                           :ny 0.0
                                                           :z 0.5}]) idle))))]
        (is (near? (- 0.5 (* (/ 1.0 60) sf/start-speed)) z))))
    (testing "a negative delta is clamped to no motion"
      (let [s (sf/advance (assoc start :stars [{:nx 0.0
                                                :ny 0.0
                                                :z 0.5}])
                          {:metrics m
                           :delta-seconds -1.0
                           :pointer {:phase :idle
                                     :position nil}})]
        (is (= 0.5 (:z (first (:stars s)))))))))

(deftest same-seed-same-field
  (let [again (first ((:init (sf/scene)) {:metrics m}))]
    (is (= start again))
    (is (= (:stars (nth (iterate idle start) 120))
           (:stars (nth (iterate idle again) 120))))
    (is (= sf/star-count (count (:stars start))))
    (is (every? #(and (<= -0.5 (:nx %) 0.5) (<= -0.5 (:ny %) 0.5)) (:stars start)))
    (testing "the LCG's high bits vary, so stars are not on a lattice"
      (is (> (count (distinct (map :nx (:stars start)))) 200)))))

(deftest a-rotation-needs-no-reset
  (let [flown (nth (iterate idle start) 30)
        landscape {:screen [2334 1206]}
        turned (step flown landscape :idle nil)
        dims (sf/dimensions landscape)]
    (is (= sf/star-count (count (:stars turned))))
    (is (= (:speed flown) (:speed turned)))
    (doseq [s (:stars turned)
            :let [[px py] (sf/project dims (:nx s) (:ny s) (:z s))]]
      (is (<= 0.0 px (:w dims)))
      (is (<= 0.0 py (:h dims))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [{:keys [w h lines]} (sf/dimensions {:screen screen})]]
    (testing screen
      (is (= 3 (count lines)))
      (doseq [{:keys [s x y size]} lines
              :let [tw (* 0.6 size (count s))]]
        (testing s
          (is (>= x 0))
          (is (<= (+ x tw) w))
          (is (>= y (let [[_ by _ bh] gesture/back-region] (+ by bh))))
          (is (<= (+ y size) h)))))))

(deftest shapes-stay-near-the-screen
  (doseq [screen screens
          :let [dims (sf/dimensions {:screen screen})]
          s (:stars start)
          :let [{:keys [x y r]} (sf/star-shape dims s)]]
    (is (<= (- r) x (+ (:w dims) r)))
    (is (<= (- r) y (+ (:h dims) r)))))
