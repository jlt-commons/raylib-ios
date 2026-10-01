(ns raylib.scenes.outlines-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.outlines :as ol]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def start (first ((:init (ol/scene)) {:metrics m})))
(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (ol/advance state {:metrics metrics
                      :pointer {:phase phase
                                :position position}})))

(defn- near? [a b] (< (abs (- (double a) (double b))) 1e-9))

(defn estimate
  "estimate: 0.6 of the size per character, as in the other scenes' tests."
  [s size]
  (* 0.6 size (count s)))

(defn- taken-over
  "A state past the sweep, holding thickness `t`."
  [t]
  (assoc start :manual? true :thick t))

(deftest a-drag-sets-thickness-within-range
  (let [per-px (:per-px (ol/dimensions m))
        pressed (step (taken-over 5.0) :press [600 1200])]
    (testing "a press alone changes nothing"
      (is (near? 5.0 (:thick pressed))))
    (testing "up raises it by per-px times the distance, as UP did"
      (is (near? (+ 5.0 (* per-px 100)) (:thick (step pressed :down [600 1100])))))
    (testing "down lowers it"
      (is (near? (- 5.0 (* per-px 100)) (:thick (step pressed :down [600 1300])))))
    (testing "it follows the finger, not the sum of its steps"
      (is (near? (+ 5.0 (* per-px 50))
                 (:thick (-> pressed (step :down [600 1000]) (step :down [600 1150]))))))
    (testing "a sideways move changes nothing"
      (is (near? 5.0 (:thick (step pressed :down [100 1200])))))
    (testing "clamped to the original's -30 to 30"
      (is (near? 30.0 (:thick (step pressed :down [600 0]))))
      (is (near? -30.0 (:thick (step pressed :down [600 2300])))))
    (testing "the range is reachable inside the screen: half the height spans it"
      (is (near? 30.0 (:thick (step (step (taken-over -30.0) :press [600 1800]) :down [600 633])))))
    (testing "a press in Back starts no drag"
      (let [p (step (taken-over 5.0) :press [200 100])]
        (is (nil? (:drag p)))
        (is (near? 5.0 (:thick (step p :down [200 0]))))))))

(deftest a-press-takes-over-from-the-sweep
  (is (false? (:manual? start)))
  (let [a (step start :idle nil)
        b (step a :idle nil)]
    (is (not (near? (:thick a) (:thick b))) "it sweeps untouched")
    (is (<= -30.0 (:thick b) 30.0)))
  (let [swept (nth (iterate #(step % :idle nil) start) 40)
        pressed (step swept :press [600 1200])
        later (nth (iterate #(step % :idle nil) pressed) 20)]
    (is (true? (:manual? pressed)))
    (is (near? (:thick swept) (:thick pressed)) "it holds the value it was at")
    (is (near? (:thick swept) (:thick later)) "and stays there"))
  (testing "a press in Back does not take over"
    (is (false? (:manual? (step start :press [200 100]))))))

(deftest the-release-swipe-does-not-change-it-again
  (let [dragged (-> (taken-over 0.0)
                    (step :press [600 1800])
                    (step :down [600 1200]))
        t (:thick dragged)
        released (step dragged :release [0 0])]
    (is (> t 0.0) "the drag did move it")
    (testing "the release carries an :up swipe, and it is not applied"
      (let [[_ event] (gesture/track {:start [600 1800]
                                      :last [600 1200]
                                      :travel 600
                                      :frames 2
                                      :fired? false}
                                     {:metrics m
                                      :pointer {:phase :release
                                                :position [0 0]}})]
        (is (= [:swipe :up] [(:type event) (:dir event)]))))
    (is (near? t (:thick released)))
    (is (near? t (:thick (step released :idle nil))))
    (is (nil? (:drag released)))))

(deftest a-tap-barely-moves-it
  (doseq [screen screens
          :let [mm {:screen screen}
                [w h] screen
                {:keys [per-px]} (ol/dimensions mm)
                slop (gesture/slop mm)
                s (taken-over 5.0)
                tapped (-> s
                           (step mm :press [(* 0.5 w) (* 0.5 h)])
                           (step mm :down [(* 0.5 w) (+ (* 0.5 h) slop)])
                           (step mm :release [0 0]))]]
    (testing (str screen)
      (is (<= (abs (- (:thick tapped) 5.0)) (+ (* per-px slop) 1e-9)))
      (is (< (abs (- (:thick tapped) 5.0)) 4.0) "under 7 percent of the range at worst")))
  (let [mm m
        slop (gesture/slop mm)
        tapped (-> (taken-over 5.0)
                   (step :press [600 1200])
                   (step :down [600 (+ 1200 slop)]))]
    (is (< (abs (- (:thick tapped) 5.0)) 1.2) "about 2 percent of the range on the phone upright")))

(defn- covers?
  "Whether a horizontal or vertical thick line in `lines` contains `[px py]`."
  [lines [px py]]
  (some (fn [[x1 y1 x2 y2 th]]
          (let [h (* 0.5 th)]
            (if (= y1 y2)
              (and (<= (min x1 x2) px (max x1 x2)) (<= (- y1 h) py (+ y1 h)))
              (and (<= (min y1 y2) py (max y1 y2)) (<= (- x1 h) px (+ x1 h))))))
        lines))

(deftest each-outline-is-built-from-the-thickness
  (let [rect [100.0 200.0 220.0 220.0]
        [x y w h] rect]
    (doseq [t [1.0 5.0 12.5 30.0]]
      (testing (str "rect outline at " t)
        (let [lines (ol/rect-lines rect t)]
          (is (= 4 (count lines)))
          (is (every? #(near? t (nth % 4)) lines) "every line is t wide")
          (testing "the band is inward, inside the rectangle"
            (doseq [[x1 y1 x2 y2 th] lines
                    :let [hh (* 0.5 th)]]
              (is (and (>= (- (min x1 x2) (if (= y1 y2) 0 hh)) (- x 1e-9))
                       (<= (+ (max x1 x2) (if (= y1 y2) 0 hh)) (+ x w 1e-9))
                       (>= (- (min y1 y2) (if (= x1 x2) 0 hh)) (- y 1e-9))
                       (<= (+ (max y1 y2) (if (= x1 x2) 0 hh)) (+ y h 1e-9))))))
          (testing "all four corners are filled, with no notch"
            (doseq [p [[(+ x (* 0.1 t)) (+ y (* 0.1 t))]
                       [(- (+ x w) (* 0.1 t)) (+ y (* 0.1 t))]
                       [(- (+ x w) (* 0.1 t)) (- (+ y h) (* 0.1 t))]
                       [(+ x (* 0.1 t)) (- (+ y h) (* 0.1 t))]
                       [(+ x t -0.01) (+ y t -0.01)]]]
              (is (covers? lines p) (str p))))
          (testing "and the middle is not"
            (is (not (covers? lines [(+ x (* 0.5 w)) (+ y (* 0.5 h))])))))))
    (doseq [t [1.0 5.0 12.5 30.0]]
      (testing (str "rounded outline at " t)
        (let [{:keys [lines rings]} (ol/rounded-outline rect t)
              r (ol/rounded-radius rect)]
          (is (= 4 (count lines)))
          (is (= 4 (count rings)))
          (is (every? #(near? t (nth % 4)) lines))
          (is (every? (fn [[_ _ inner outer a0 a1]]
                        (and (near? outer r)
                             (near? inner (max 0.0 (- r t)))
                             (near? 90.0 (- a1 a0))))
                      rings)
              "each corner is a quarter ring of the same width"))))
    (testing "the ring grows inward for a positive thickness"
      (is (= [80.0 110.0] (ol/circle-ring 0.0 0.0 110.0 30.0)))
      (is (= [105.0 110.0] (ol/circle-ring 0.0 0.0 110.0 5.0))))
    (testing "the dimensions scale the shapes, and the thickness with them"
      (let [d (ol/dimensions m)
            [_ _ rw rh] (:rect d)]
        (is (near? (* 220 (:k d)) rw))
        (is (near? rw rh))))))

(deftest negative-thickness-behaves-as-the-original
  (let [rect [100.0 200.0 220.0 220.0]]
    (testing "the plain rectangle draws nothing, as raylib's `thick > 0` guard does"
      (doseq [t [-30.0 -5.0 -0.001 0.0]]
        (is (empty? (ol/rect-lines rect t)) (str t))))
    (testing "the rounded one collapses to a one pixel hairline: no outward band"
      (doseq [t [-30.0 -5.0 0.0]
              :let [{:keys [lines rings]} (ol/rounded-outline rect t)
                    [x y w h] rect
                    r (ol/rounded-radius rect)]]
        (is (= 4 (count lines)))
        (is (every? #(near? ol/hairline (nth % 4)) lines) (str t))
        (is (every? (fn [[x1 y1 x2 y2 _]]
                      (and (>= (min x1 x2) (- x 1e-9)) (<= (max x1 x2) (+ x w 1e-9))
                           (>= (min y1 y2) (- y 1e-9)) (<= (max y1 y2) (+ y h 1e-9))))
                    lines)
            "everything stays inside the rectangle")
        (is (every? (fn [[_ _ inner outer _ _]]
                      (and (near? outer r) (near? (- outer inner) ol/hairline)))
                    rings))))
    (testing "the ring is the only one that grows outward, by the thickness"
      (is (= [110.0 140.0] (ol/circle-ring 0.0 0.0 110.0 -30.0)))
      (is (= [110.0 115.0] (ol/circle-ring 0.0 0.0 110.0 -5.0))))
    (testing "and at exactly zero it has no width, so it is not drawn"
      (is (nil? (ol/circle-ring 0.0 0.0 110.0 0.0))))
    (testing "the readout names which one grows outward"
      (is (re-find #"outward" (ol/thickness-line -10.0)))
      (is (re-find #"inward" (ol/thickness-line 10.0)))
      (is (re-find #"-30\.0" (ol/thickness-line -30.0))))))

(deftest fits-below-back
  (doseq [screen screens
          :let [[w h] screen
                {:keys [k rect rounded ring]} (ol/dimensions {:screen screen})
                [cx cy r] ring
                outer-max (* (+ 110.0 ol/max-thick) k)
                box (fn [[x y bw bh]] [x y (+ x bw) (+ y bh)])]]
    (testing (str screen)
      (is (pos? k))
      (doseq [[x0 y0 x1 y1] [(box rect) (box rounded)
                             [(- cx outer-max) (- cy outer-max) (+ cx outer-max) (+ cy outer-max)]]]
        (is (>= x0 0.0))
        (is (>= y0 back-bottom))
        (is (<= x1 w))
        (is (<= y1 h)))
      (testing "the thickest outward ring is the widest thing, and it fits"
        (is (near? (* 110.0 k) r)))
      (testing "no two shapes overlap"
        (let [bs [(box rect) (box rounded)
                  [(- cx outer-max) (- cy outer-max) (+ cx outer-max) (+ cy outer-max)]]]
          (doseq [[i a] (map-indexed vector bs)
                  [j b] (map-indexed vector bs)
                  :when (< i j)]
            (is (or (<= (nth a 2) (nth b 0)) (<= (nth b 2) (nth a 0))
                    (<= (nth a 3) (nth b 1)) (<= (nth b 3) (nth a 1)))
                (str i " " j))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [{:keys [w h rows labels]} (ol/dimensions {:screen screen})
                strings [(ol/hint-line false) (ol/hint-line true)
                         (ol/thickness-line -30.0) (ol/thickness-line 30.0)
                         (first ol/notes) (second ol/notes)]]]
    (testing (str screen)
      (is (>= (:y (first rows)) back-bottom) "below Back")
      (doseq [s strings
              {:keys [x size]} rows]
        (is (<= (+ x (estimate s size)) w) s))
      (is (<= (+ (:y (peek rows)) (:size (peek rows))) h))
      (doseq [[i {:keys [s x y size]}] (map-indexed vector labels)]
        (is (>= y back-bottom) s)
        (is (>= x 0) s)
        (is (<= (+ x (estimate s size)) w) (str i " " s))
        (is (<= (+ y size) h) s)))))
