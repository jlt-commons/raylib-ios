(ns net.b12n.raylib-ios.scenes.huewheel-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.huewheel :as hw]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def start (first ((:init (hw/scene)) {:metrics m})))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (hw/advance state {:metrics metrics
                      :pointer {:phase phase
                                :position position}})))

(defn- near? [a b] (< (abs (- (double a) (double b))) 1e-9))

(defn- swipe
  "A whole swipe from `from` to `to`, ending in a release whose position is
  deliberately stale, as on a device."
  [state from to]
  (-> state
      (step :press from)
      (step :down to)
      (step :release [0 0])))

(defn- drag-to
  "The state after pressing at x 600 with `b` and dragging to `to`."
  [b to]
  (-> (assoc start :brightness b)
      (step :press [600 1200])
      (step :down to)))

(defn- tris-after [n from to]
  (:tris (swipe (assoc start :tris n) from to)))

(deftest swipes-double-and-halve-the-count-within-range
  (is (= 32 (:tris start)))
  (testing "up doubles, down halves"
    (is (= 64 (tris-after 32 [600 1600] [600 1200])))
    (is (= 16 (tris-after 32 [600 1200] [600 1600]))))
  (testing "clamped to the original's 3 to 128"
    (is (= 128 (tris-after 128 [600 1600] [600 1200])))
    (is (= 128 (tris-after 100 [600 1600] [600 1200])))
    (is (= 3 (tris-after 3 [600 1200] [600 1600])))
    (is (= 3 (tris-after 4 [600 1200] [600 1600])))
    (is (= 6 (tris-after 3 [600 1600] [600 1200]))))
  (testing "a horizontal swipe leaves the count alone"
    (is (= 32 (tris-after 32 [300 1200] [900 1200]))))
  (testing "a swipe that began in Back changes nothing"
    (is (= 32 (tris-after 32 [200 100] [200 700])))))

(deftest a-drag-sets-brightness
  (let [k (:k (hw/dimensions m))
        pressed (step start :press [600 1200])]
    (testing "a press alone changes nothing"
      (is (near? 1.0 (:brightness pressed))))
    (testing "left by 300 px darkens by k times that"
      (is (near? (- 1.0 (* k 300)) (:brightness (step pressed :down [300 1200])))))
    (testing "it follows the finger, not the sum of its steps"
      (is (near? (- 1.0 (* k 100))
                 (:brightness (-> pressed (step :down [200 1200]) (step :down [500 1200]))))))
    (testing "right from a darker anchor brightens, clamped to 0..1"
      (let [dark (assoc start :brightness 0.5)
            p (step dark :press [600 1200])]
        (is (near? (+ 0.5 (* k 100)) (:brightness (step p :down [700 1200]))))
        (is (near? 1.0 (:brightness (drag-to 0.8 [1200 1200]))))
        (is (near? 0.0 (:brightness (drag-to 0.3 [0 1200]))))))
    (testing "a press in Back starts no drag"
      (let [p (step start :press [200 100])]
        (is (nil? (:drag p)))
        (is (near? 1.0 (:brightness (step p :down [0 100]))))))))

(deftest the-release-swipe-does-not-change-brightness-again
  (let [dragged (-> start
                    (step :press [900 1200])
                    (step :down [300 1200]))
        b (:brightness dragged)
        released (step dragged :release [0 0])]
    (is (< b 1.0) "the drag did move it")
    (testing "the release carries a :left swipe, and it is not applied"
      (let [[_ event] (gesture/track (:gesture dragged)
                                     {:metrics m
                                      :pointer {:phase :release
                                                :position [0 0]}})]
        (is (= [:swipe :left] [(:type event) (:dir event)]))))
    (is (near? b (:brightness released)))
    (is (near? b (:brightness (step released :idle nil))))))

(deftest a-vertical-swipe-nudges-brightness-by-at-most-k-times-its-travel
  (let [k (:k (hw/dimensions m))
        up (swipe start [600 1600] [700 1200])]
    (is (= 64 (:tris up)))
    (is (<= (- 1.0 (:brightness (-> start
                                    (step :press [600 1600])
                                    (step :down [500 1200]))))
            (* k 400)))))

(deftest a-tap-toggles-wireframe
  (let [tap (fn [s p] (-> s (step :press p) (step :down p) (step :release [0 0])))
        a (tap start [600 1200])
        b (tap a [600 1200])]
    (is (false? (:lines? start)))
    (is (true? (:lines? a)))
    (is (false? (:lines? b)))
    (is (true? (:lines? (tap start [900 1500]))))
    (testing "a tap in Back does nothing"
      (is (false? (:lines? (tap start [200 100])))))
    (testing "and a tap changes neither the count nor the brightness"
      (is (= 32 (:tris a)))
      (is (near? 1.0 (:brightness a))))))

(deftest fan-triangles-wind-negative
  (doseq [screen screens
          n [3 4 5 8 32 64 128]
          b [0.0 1.0]
          :let [d (hw/dimensions {:screen screen})
                tris (hw/fan d n b)]]
    (is (= n (count tris)) (str screen " " n))
    (doseq [t tris
            :let [[a bb c] (map :pos t)]]
      (is (neg? (hw/cross a bb c)) (str screen " " n)))))

(deftest every-wedge-keeps-its-centre-and-colours
  (let [d (hw/dimensions m)
        tris (hw/fan d 8 0.5)]
    (doseq [t tris]
      (is (= 1 (count (filter #(= [(:cx d) (:cy d)] (:pos %)) t))))
      (is (= [127 127 127 255]
             (:color (first (filter #(= [(:cx d) (:cy d)] (:pos %)) t))))))))

(deftest rim-hues-span-the-circle
  (let [d (hw/dimensions m)
        colours (mapv #(:color (hw/rim d 6 %)) (range 7))]
    (is (= [[255 0 0 255] [255 255 0 255] [0 255 0 255]
            [0 255 255 255] [0 0 255 255] [255 0 255 255]]
           (subvec colours 0 6)))
    (testing "vertex n is vertex 0, so the last wedge closes the circle"
      (is (= (hw/rim d 6 0) (hw/rim d 6 6)))
      (is (= (hw/rim d 128 0) (hw/rim d 128 128))))
    (testing "all 128 hues are distinct and no wedge sits at a colour step"
      (is (= 128 (count (distinct (map #(:color (hw/rim d 128 %)) (range 128)))))))
    (testing "every rim point is the radius away from the centre"
      (doseq [i (range 32)
              :let [[x y] (:pos (hw/rim d 32 i))]]
        (is (near? (:radius d)
                   (Math/sqrt (+ (Math/pow (- x (:cx d)) 2)
                                 (Math/pow (- y (:cy d)) 2)))))))))

(deftest wireframe-has-two-segments-per-wedge
  (let [d (hw/dimensions m)]
    (is (= 64 (count (hw/wire-segments d 32))))))

(defn- estimate
  "estimate: 0.6 of the size per character, as in the other scenes' tests."
  [s size]
  (* 0.6 size (count s)))

(deftest text-lines-fit-the-safe-region
  (let [[_ back-y _ back-h] gesture/back-region]
    (doseq [screen screens
            :let [{:keys [w h lines cx cy radius]} (hw/dimensions {:screen screen})]]
      (testing (str screen)
        (is (>= (:y (first lines)) (+ back-y back-h)) "below Back")
        (doseq [{:keys [s x y size]} lines]
          (is (<= (+ x (estimate s size)) w) s)
          (is (<= (+ y size) h) s))
        (testing "and the wheel sits under the text and inside the screen"
          (is (pos? radius))
          (is (>= (- cy radius) (+ (:y (peek lines)) (:size (peek lines)))))
          (is (>= (- cx radius) 0))
          (is (<= (+ cx radius) w))
          (is (<= (+ cy radius) h)))))))
