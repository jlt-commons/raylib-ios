(ns raylib.scenes.ellipses-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.ellipses :as el]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))

(defn- init-on [screen] (first ((:init (el/scene)) {:metrics {:screen screen}})))
(def start (init-on [1206 2334]))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (first ((:update (el/scene)) state {:metrics metrics
                                       :pointer {:phase phase
                                                 :position position}}))))

(defn- near? [a b] (< (abs (- (double a) (double b))) 1e-9))

(defn estimate
  "estimate: 0.6 of the size per character, as in the other scenes' tests."
  [s size]
  (* 0.6 size (count s)))

(defn- cross [[x1 y1 x2 y2 x3 y3]]
  (- (* (- x2 x1) (- y3 y1))
     (* (- y2 y1) (- x3 x1))))

(defn- tap
  "Press, hold two frames and release at `pt`."
  [state pt]
  (-> state
      (step :press pt)
      (step :down pt)
      (step :down pt)
      (step :release [1.0 1.0])))

;; Two 120x70 ellipses whose rims cross in a lens too thin for any of the 64
;; samples per rim to land in. Found by scanning offsets at a fixed angle with a
;; dense sampling and the original's 64, so it is a measured case, not a guess.
(def sliver-centre [194.25 82.12])

(defn- dense-overlap?
  "A reference with 8192 samples per rim, which stands in for the exact answer."
  [[ax ay] [arx ary] [bx by] [brx bry]]
  (letfn [(rim-in? [[cx cy] [rx ry] oc orr]
            (some (fn [i]
                    (let [t (* 2.0 Math/PI (/ (double i) 8192))]
                      (el/inside? [(+ cx (* rx (Math/cos t))) (+ cy (* ry (Math/sin t)))] oc orr)))
                  (range 8192)))]
    (boolean (or (rim-in? [ax ay] [arx ary] [bx by] [brx bry])
                 (rim-in? [bx by] [brx bry] [ax ay] [arx ary])))))

(defn- radii-of [dims k] (get-in dims [k :r]))

(deftest the-steered-ellipse-follows-a-finger
  (let [dims (el/dimensions m)
        [rx ry] (radii-of dims :a)
        pt [(* 0.5 (:w dims)) 700.0]
        pressed (step start :press pt)]
    (testing "A is steered first, and a press moves it"
      (is (= :a (:steer start)))
      (is (= :a (:steer pressed)))
      (is (= (mapv double pt) (:a pressed))))
    (testing "it follows while the finger is down"
      (let [moved (step pressed :down [700.0 900.0])]
        (is (= [700.0 900.0] (:a moved)))
        (is (= (:b start) (:b moved)) "the other stays put")))
    (testing "the other ellipse is the one that follows after a swap"
      (let [swapped (assoc start :steer :b)
            moved (step (step swapped :press [600.0 1700.0]) :down [610.0 1710.0])]
        (is (= [610.0 1710.0] (:b moved)))
        (is (= (:a start) (:a moved)))))
    (testing "a finger near an edge is clamped so the whole ellipse stays on screen"
      (let [{:keys [w h top]} dims
            edge (step (step start :press [5.0 (+ top 5.0)]) :down [5.0 (+ top 5.0)])
            [ex ey] (:a edge)]
        (is (>= (- ex rx) 0.0))
        (is (>= (- ey ry) top))
        (let [far (step (step start :press [w h]) :down [w h])
              [fx fy] (:a far)]
          (is (<= (+ fx rx) w))
          (is (<= (+ fy ry) h)))))))

(deftest overlap-matches-the-originals-test
  (let [a-r [120.0 70.0]
        b-r [120.0 70.0]]
    (testing "point-in-ellipse is the unit-circle test after dividing out each radius"
      (is (el/inside? [120.0 0.0] [0.0 0.0] a-r) "on the rim counts as inside")
      (is (el/inside? [0.0 0.0] [0.0 0.0] a-r))
      (is (not (el/inside? [0.0 71.0] [0.0 0.0] a-r)))
      (is (not (el/inside? [121.0 0.0] [0.0 0.0] a-r)))
      (testing "a point that a box test would accept but the ellipse rejects"
        (is (not (el/inside? [100.0 60.0] [0.0 0.0] a-r)))))
    (testing "clearly overlapping"
      (is (el/overlap? [0.0 0.0] a-r [100.0 20.0] b-r))
      (is (el/overlap? [0.0 0.0] a-r [0.0 0.0] b-r) "the same place"))
    (testing "one wholly inside the other, so neither rim crosses"
      (is (el/overlap? [0.0 0.0] [200.0 150.0] [10.0 10.0] [30.0 20.0]))
      (is (el/overlap? [10.0 10.0] [30.0 20.0] [0.0 0.0] [200.0 150.0]) "and the reverse"))
    (testing "clearly apart"
      (is (not (el/overlap? [0.0 0.0] a-r [500.0 0.0] b-r)))
      (is (not (el/overlap? [0.0 0.0] a-r [0.0 300.0] b-r))))
    (testing "near the boundary, along x: A's rim sample at angle 0 is (120 0), inside B iff |120 - d| <= 120, so d = 240 touches"
      (is (el/overlap? [0.0 0.0] a-r [239.0 0.0] b-r))
      (is (el/overlap? [0.0 0.0] a-r [240.0 0.0] b-r) "touching counts, as <= 1 does")
      (is (not (el/overlap? [0.0 0.0] a-r [241.0 0.0] b-r))))
    (testing "near the boundary, along y: the sample at a quarter turn is (0 70), so d = 140 touches"
      (is (el/overlap? [0.0 0.0] a-r [0.0 139.0] b-r))
      (is (not (el/overlap? [0.0 0.0] a-r [0.0 141.0] b-r))))
    (testing "it is symmetric"
      (doseq [d [[100.0 20.0] [239.0 0.0] [241.0 0.0] [500.0 0.0]]]
        (is (= (el/overlap? [0.0 0.0] a-r d b-r) (el/overlap? d b-r [0.0 0.0] a-r)))))
    (testing "it is the original's sampling, so a sliver between samples is missed"
      (is (not (el/overlap? [0.0 0.0] a-r sliver-centre a-r))
          "the two rims cross, but no one of 64 samples lands inside the other")
      (is (dense-overlap? [0.0 0.0] a-r sliver-centre a-r)))
    (testing "the scene stores the verdict for its colours"
      (let [overlapping (assoc start :a (:b start))]
        (is (false? (:hit? start)))
        (is (true? (:hit? (step overlapping :idle nil))))))))

(deftest a-tap-on-the-other-ellipse-swaps-control
  (let [{:keys [a b]} start
        in-b (mapv double b)
        in-a (mapv double a)]
    (testing "a tap inside the other ellipse swaps A to B"
      (let [after (tap start in-b)]
        (is (= :b (:steer after)))))
    (testing "and back again"
      (let [after (-> start (tap in-b) (tap in-a))]
        (is (= :a (:steer after)))))
    (testing "the swap tap does not move the ellipse that was steered, on any frame of the touch"
      (let [pressed (step start :press in-b)
            held (step pressed :down in-b)]
        (is (= a (:a pressed)))
        (is (= a (:a held)))
        (is (= a (:a (tap start in-b))))))
    (testing "a tap inside the steered one swaps nothing"
      (is (= :a (:steer (tap start in-a)))))
    (testing "a tap on empty ground swaps nothing, and moves the steered one there"
      (let [empty-pt [30.0 1250.0]
            after (tap start empty-pt)]
        (is (= :a (:steer after)))
        (is (not (el/inside? empty-pt b (radii-of (el/dimensions m) :b))))
        (is (not= a (:a after)))))
    (testing "a tap under Back is ignored, even when the other ellipse reaches it"
      (let [under-back [200.0 100.0]
            reaching (assoc start :b [200.0 100.0] :steer :a)
            after (tap reaching under-back)]
        (is (= :a (:steer after)))
        (is (= (:a reaching) (:a after)) "and it does not drag the steered one up there")))
    (testing "a press under Back, away from the other ellipse, moves nothing and swaps nothing"
      (let [under-back [200.0 100.0]
            dims (el/dimensions m)]
        (is (gesture/in-back-region? under-back))
        (is (not (el/inside? under-back b (radii-of dims :b))))
        (let [pressed (step start :press under-back)
              held (step pressed :down [210.0 105.0])]
          (is (= a (:a pressed)))
          (is (= a (:a held)))
          (is (= :a (:steer pressed) (:steer held))))))
    (testing "a touch that starts inside the other and travels is a swipe, not a tap, so it swaps nothing"
      (let [end [(+ (first in-b) 300.0) (second in-b)]
            after (-> start
                      (step :press in-b)
                      (step :down [(+ (first in-b) 150.0) (second in-b)])
                      (step :down end)
                      (step :release [1.0 1.0]))]
        (is (= :a (:steer after)))
        (is (= a (:a after)))))))

(deftest a-release-does-not-move-it
  (let [pt [700.0 700.0]
        held (step (step start :press pt) :down pt)
        released (step held :release [3.0 3.0])]
    (is (= (:a held) (:a released)))
    (is (= pt (:a released)))
    (testing "and neither does an idle frame, or a stray :down with no press before it"
      (is (= (:a released) (:a (step released :idle nil))))
      (is (= (:a start) (:a (step start :down [900.0 700.0])))))))

(deftest fan-triangles-survive-culling
  (doseq [screen screens
          :let [dims (el/dimensions {:screen screen})]
          k [:a :b]
          :let [{:keys [r n]} (get dims k)
                [cx cy] (get (init-on screen) k)]
          w (el/fan cx cy r n)]
    (is (neg? (cross w)) (str screen " " k))))

(deftest outline-uses-the-same-rim-as-the-fill
  (doseq [screen screens
          :let [dims (el/dimensions {:screen screen})
                {:keys [r n]} (:a dims)
                [cx cy] (:a (init-on screen))
                fan (el/fan cx cy r n)
                lines (el/outline cx cy r n)]]
    (is (= n (count lines)))
    (is (= (map (fn [[_ _ x2 y2 x3 y3]] [x3 y3 x2 y2]) fan)
           (map (fn [[x1 y1 x2 y2]] [x1 y1 x2 y2]) lines))
        "each line runs along one wedge's rim edge")
    (testing "and the loop closes"
      (let [[x1 y1] (first lines)
            [_ _ x2 y2] (last lines)]
        (is (and (near? x1 x2) (near? y1 y2)))))))

(deftest stays-below-back
  (doseq [screen screens
          :let [[w h] screen
                dims (el/dimensions {:screen screen})
                half (* 0.5 (:thick dims))]]
    (testing (str screen)
      (is (>= (:top dims) back-bottom))
      (is (pos? (:f dims)))
      (testing "at the start, apart, so the colours begin unflushed"
        (is (false? (:hit? (init-on screen)))))
      (testing "wherever the finger goes, every rim point and its stroke stay inside the screen below Back"
        (doseq [pt [[0.0 0.0] [(double w) (double h)] [0.0 (double h)] [(double w) 0.0]
                    [(* 0.5 w) (:top dims)] [-100.0 -100.0] [(* 3.0 w) (* 3.0 h)]]
                steer [:a :b]
                :let [s (-> (init-on screen) (assoc :steer steer)
                            (step {:screen screen} :press (if (gesture/in-back-region? pt) [(* 0.5 w) (* 0.5 h)] pt))
                            (step {:screen screen} :down pt))]
                k [:a :b]
                :let [{:keys [r n]} (get dims k)
                      [cx cy] (get s k)]
                [x1 y1 x2 y2] (el/outline cx cy r n)
                [x y] [[x1 y1] [x2 y2]]]
          (is (>= (- x half) -1e-6) (str k pt))
          (is (<= (+ x half) (+ w 1e-6)) (str k pt))
          (is (>= (- y half) (- back-bottom 1e-6)) (str k pt))
          (is (<= (+ y half) (+ h 1e-6)) (str k pt)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [rows]} (el/dimensions {:screen screen})]
          steer [:a :b]
          hit? [true false]
          :let [lines (el/lines steer hit?)]]
    (testing (str screen " " steer " " hit?)
      (is (= (count rows) (count lines)))
      (doseq [[{:keys [x y size]} [s _]] (map vector rows lines)]
        (is (>= y back-bottom) "below Back")
        (is (>= x 0))
        (is (<= (+ x (estimate s size)) w))
        (is (<= (+ y size) h))))
    (testing "the rows are on top of each other, not overlapping"
      (is (apply < (map :y rows)))
      (is (every? (fn [[r1 r2]] (<= (+ (:y r1) (:size r1)) (:y r2))) (partition 2 1 rows))))
    (testing "the ellipses start below the last row"
      (is (>= (:top (el/dimensions {:screen screen}))
              (let [r (last rows)] (+ (:y r) (:size r))))))))
