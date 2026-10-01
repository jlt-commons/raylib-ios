(ns raylib.scenes.nudge-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.nudge :as nudge]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (nudge/dimensions m))
(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))
(def start (first ((:init (nudge/scene)) {:metrics m})))
(def start-at (:pos start))
(def slop (gesture/slop m))
(def below-back [600.0 1200.0])

(defn- adv
  ([state phase pos] (adv state m phase pos))
  ([state metrics phase pos]
   (nudge/advance state {:metrics metrics
                         :pointer {:phase phase
                                   :position pos}})))

(defn- idle [state] (adv state :idle nil))
(defn- tap [state pos]
  (-> state (adv :press pos) (adv :down pos) (adv :release pos)))
(defn- near? [a b] (< (abs (double (- a b))) 1e-6))
(defn- dist [[ax ay] [bx by]]
  (Math/sqrt (+ (* (- ax bx) (- ax bx)) (* (- ay by) (- ay by)))))

(deftest the-stick-moves-the-ball
  (let [[cx cy] below-back
        pressed (adv start :press below-back)
        speed (:ball-speed d)]
    (testing "the press itself moves nothing"
      (is (= start-at (:pos pressed))))
    (testing "a drag to the right goes right at the original's speed, scaled"
      (let [moved (adv pressed :down [(+ cx 300) cy])
            [x y] (:pos moved)]
        (is (near? (+ (first start-at) speed) x))
        (is (near? (second start-at) y))
        (is (near? (* 2.0 (:u d)) speed) "2 px a frame at scale 1")))
    (testing "every direction is the same speed, a diagonal included"
      (doseq [[dx dy] [[300 0] [0 -300] [-300 0] [0 300] [300 300] [-200 500] [90 10]]
              :let [moved (adv pressed :down [(+ cx dx) (+ cy dy)])]]
        (is (near? speed (dist start-at (:pos moved))) (str [dx dy]))))
    (testing "how far the finger is does not change the speed"
      (is (= (:pos (adv pressed :down [(+ cx 100) cy]))
             (:pos (adv pressed :down [(+ cx 500) cy])))))
    (testing "it keeps going while held, then stops on release"
      (let [held (-> pressed (adv :down [(+ cx 300) cy]) (adv :down [(+ cx 300) cy]))
            lifted (adv held :release [0.0 0.0])]
        (is (near? (+ (first start-at) (* 2 speed)) (first (:pos held))))
        (is (= (:pos held) (:pos lifted)) "the release position is never read")
        (is (= (:pos held) (:pos (idle lifted))))
        (is (nil? (:stick (idle lifted))))))))

(deftest stick-dir-has-a-dead-zone-and-a-unit-direction
  (let [st (assoc start :stick {:centre [500.0 1000.0]
                                :finger [500.0 1000.0]})
        dir (fn [finger phase]
              (nudge/stick-dir st {:metrics m
                                   :pointer {:phase phase
                                             :position finger}} m))]
    (is (nil? (dir [500.0 1000.0] :down)))
    (is (nil? (dir [(+ 500.0 slop) 1000.0] :down)) "exactly the slop out is still inside")
    (is (= [1.0 0.0] (dir [(+ 500.0 slop 1) 1000.0] :down)))
    (is (= [0.0 -1.0] (dir [500.0 100.0] :down)) "up is negative y")
    (let [[x y] (dir [900.0 1400.0] :down)]
      (is (near? 1.0 (+ (* x x) (* y y))))
      (is (near? x y)))
    (is (nil? (dir [900.0 1400.0] :press)))
    (is (nil? (dir [900.0 1400.0] :release)))
    (is (nil? (nudge/stick-dir start {:metrics m
                                      :pointer {:phase :down
                                                :position [900.0 1400.0]}} m))
        "no centre, no direction")))

(deftest a-rotation-drops-the-stick
  (let [held (-> start (adv :press below-back) (adv :down [(+ (first below-back) 300) (second below-back)]))
        land {:screen [2334 1206]}
        turned (adv held land :down [900.0 700.0])]
    (is (some? (:stick held)))
    (testing "a finger held through a rotation has no centre in the new screen"
      (is (nil? (:stick turned)))
      (is (= (:pos (adv held land :idle nil)) (:pos turned)) "it only clamps the ball in"))
    (testing "and it does not move the ball while it stays down"
      (is (= (:pos turned) (:pos (adv turned land :down [1000.0 700.0])))))))

(deftest a-tap-does-not-move-it
  (is (= start-at (:pos (tap start below-back))))
  (testing "a slight drag inside the slop does not either"
    (let [[x y] below-back
          s (-> start
                (adv :press below-back)
                (adv :down [(+ x (* 0.9 slop)) y])
                (adv :down [x (- y (* 0.9 slop))])
                (adv :release below-back))]
      (is (= start-at (:pos s)))))
  (testing "a press under Back starts no stick"
    (let [s (-> start (adv :press [100.0 50.0]) (adv :down [700.0 600.0]))]
      (is (nil? (:stick s)))
      (is (= start-at (:pos s)))))
  (testing "a finger that was already down when the scene opened does nothing"
    (is (= start-at (:pos (adv start :down [900.0 1500.0]))))))

(deftest the-ball-stays-inside
  (let [{:keys [fx ftop fw fh ball-r]} d
        run (fn [from to]
              (reduce (fn [s _] (adv s :down to))
                      (adv start :press from)
                      (range 1500)))]
    (doseq [[from to] [[[1100.0 2300.0] [1.0 100.0]]
                       [[100.0 400.0] [1100.0 2300.0]]
                       [[600.0 1200.0] [1100.0 1200.0]]
                       [[600.0 1200.0] [100.0 1200.0]]
                       [[600.0 1200.0] [600.0 300.0]]
                       [[600.0 1200.0] [600.0 2300.0]]]
            :let [[x y] (:pos (run from to))]]
      (testing (str from " to " to)
        (is (>= x (+ fx ball-r)))
        (is (<= x (- (+ fx fw) ball-r)))
        (is (>= y (+ ftop ball-r)))
        (is (<= y (- (+ ftop fh) ball-r)))
        (is (not= start-at [x y]) "and it did move")))
    (testing "a rotation pulls the ball back in"
      (let [far (assoc start :pos [1100.0 2300.0])
            landscape {:screen [2334 1206]}
            {:keys [ftop fh ball-r]} (nudge/dimensions landscape)
            [_ y] (:pos (adv far landscape :idle nil))]
        (is (<= y (- (+ ftop fh) ball-r)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [caption ball-r fw fh]} (nudge/dimensions {:screen screen})
                {:keys [s x y size]} caption]]
    (testing (str screen)
      (is (<= 0 x))
      (is (<= (+ x (* 0.6 size (count s))) w))
      (is (>= y back-bottom))
      (is (<= (+ y size) h))
      (is (<= (* 2 ball-r) (min fw fh)) "the ball fits the field"))))

(deftest the-field-starts-below-the-caption
  (doseq [screen screens
          :let [dims (nudge/dimensions {:screen screen})
                {:keys [y size]} (:caption dims)
                caption-bottom (+ y size)
                [x top] (nudge/clamp-ball dims [-1e6 -1e6])]]
    (testing (str screen)
      (is (<= caption-bottom (:ftop dims)) "the field's top is at or below the caption")
      (is (>= (- top (:ball-r dims)) caption-bottom) "a ball clamped at the top clears the caption")
      (is (>= (- x (:ball-r dims)) 0.0))
      (is (== (:h dims) (+ (:ftop dims) (:fh dims)))))))
