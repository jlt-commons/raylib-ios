(ns raylib.scenes.easingsbox-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.easings :as ez]
            [raylib.gesture :as gesture]
            [raylib.scenes.easingsbox :as eb]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def start (first ((:init (eb/scene)) {:metrics m})))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (eb/advance state {:metrics metrics
                      :pointer {:phase phase
                                :position position}})))

(defn- idle [state] (step state :idle nil))
(defn- frames [state n] (nth (iterate idle state) n))
(defn- tap [state at] (-> state (step :press at) (step :release at)))

(defn- near? [a b] (< (abs (- (double a) (double b))) 1e-6))

(deftest each-stage-uses-its-curve-and-hands-off
  (let [d (eb/dimensions m)
        u (:u d)
        half (* 50.0 u)
        y0 (+ (:top d) half)
        cy (:cy d)
        sh #(eb/shape d %1 %2)]
    (testing "drop: elastic-out on y"
      (is (near? (ez/elastic-out 30.0 y0 (- cy y0) 120.0) (:cy (sh :drop 30.0))))
      (is (near? 100.0 (/ (:h (sh :drop 30.0)) u))))
    (testing "flatten: bounce-out on width and height"
      (is (near? (ez/bounce-out 50.0 (* 100.0 u) (- (:bar d) (* 100.0 u)) 120.0)
                 (:w (sh :flatten 50.0))))
      (is (near? (ez/bounce-out 50.0 (* 100.0 u) (* -90.0 u) 120.0)
                 (:h (sh :flatten 50.0)))))
    (testing "spin: quad-out on the rotation"
      (is (near? (ez/quad-out 90.0 0.0 270.0 240.0) (:rot (sh :spin 90.0)))))
    (testing "grow: circ-out on both sides"
      (is (near? (ez/circ-out 40.0 (* 10.0 u) (- (:fill-h d) (* 10.0 u)) 120.0)
                 (:h (sh :grow 40.0))))
      (is (near? (ez/circ-out 40.0 (:bar d) (- (:fill-w d) (:bar d)) 120.0)
                 (:w (sh :grow 40.0)))))
    (testing "fade: sine-out on the alpha"
      (is (near? (ez/sine-out 60.0 1.0 -1.0 160.0) (:alpha (sh :fade 60.0)))))
    (testing "each stage ends where the next begins"
      (doseq [[a b] [[:drop :flatten] [:flatten :spin] [:spin :grow] [:grow :fade] [:fade :done]]
              :let [end (sh a (eb/stage-length a))
                    begin (sh b 0.0)]]
        (testing (str a b)
          (doseq [k [:cx :cy :w :h :rot :alpha]]
            (is (near? (k end) (k begin)) (str k))))))
    (testing "the stage counter advances stage by stage, frame-locked"
      (let [at (fn [n] (:stage (frames start n)))]
        (is (= :drop (at 119)))
        (is (= :flatten (at 120)))
        (is (= :spin (at 240)))
        (is (= :grow (at 480)))
        (is (= :fade (at 600)))
        (is (= :done (at 760)))))))

(deftest the-quad-corners-match-the-rotation
  (testing "no rotation: the corners of the rectangle, top-left first, clockwise"
    (is (= [[0.0 0.0] [4.0 0.0] [4.0 2.0] [0.0 2.0]]
           (mapv (fn [[x y]] [(double x) (double y)]) (eb/quad-corners 2 1 4 2 0)))))
  (testing "90 degrees turns clockwise on screen, y growing downward"
    (let [[[ax ay] [bx by] [cx cy] [dx dy]] (eb/quad-corners 0 0 4 2 90)]
      (is (near? 1 ax))
      (is (near? -2 ay))
      (is (near? 1 bx))
      (is (near? 2 by))
      (is (near? -1 cx))
      (is (near? 2 cy))
      (is (near? -1 dx))
      (is (near? -2 dy))))
  (testing "270 degrees of a bar is the bar stood on end"
    (let [cs (eb/quad-corners 10 10 8 2 270)]
      (is (near? 9 (apply min (map first cs))))
      (is (near? 11 (apply max (map first cs))))
      (is (near? 6 (apply min (map second cs))))
      (is (near? 14 (apply max (map second cs))))))
  (testing "the centre and the side lengths survive any angle"
    (doseq [deg [0 17 90 135 270 359]
            :let [cs (eb/quad-corners 5 7 6 2 deg)
                  dist (fn [[ax ay] [bx by]] (Math/hypot (- ax bx) (- ay by)))]]
      (is (near? 5 (/ (reduce + (map first cs)) 4)))
      (is (near? 7 (/ (reduce + (map second cs)) 4)))
      (is (near? 6 (dist (nth cs 0) (nth cs 1))))
      (is (near? 2 (dist (nth cs 1) (nth cs 2)))))))

(deftest a-tap-restarts
  (let [late (frames start 300)
        again (tap late [600 1200])]
    (is (= :spin (:stage late)))
    (is (= :drop (:stage again)))
    (is (near? 0.0 (:counter again)))
    (testing "a tap in Back belongs to the host"
      (is (= (:stage late) (:stage (tap late [100 50])))))
    (testing "a swipe does not restart"
      (is (= (:stage late)
             (:stage (-> late (step :press [600 1200]) (step :down [600 1500])
                         (step :release [600 1500]))))))
    (testing "a tap restarts from the finished state too"
      (is (= :drop (:stage (tap (frames start 2000) [600 1200])))))))

(deftest it-finishes-and-holds
  (let [done (frames start 760)
        later (frames done 500)]
    (is (= :done (:stage done)))
    (is (= :done (:stage later)))
    (is (= (eb/shape (eb/dimensions m) :done (:counter done))
           (eb/shape (eb/dimensions m) :done (:counter later))))
    (is (near? 0.0 (:alpha (eb/shape (eb/dimensions m) :done 0.0))))
    (testing "finishing alone starts nothing"
      (is (= :done (:stage (idle later)))))))

(deftest the-box-stays-inside-the-safe-region
  (doseq [screen screens
          :let [metrics {:screen screen}
                d (eb/dimensions metrics)
                [_ by _ bh] gesture/back-region
                s (first ((:init (eb/scene)) {:metrics metrics}))]
          n (range 0 780 3)
          :let [{:keys [stage counter]} (frames s n)
                corners (eb/quad-corners-of (eb/shape d stage counter))]
          [x y] corners]
    (is (<= -1e-6 x (+ (:w d) 1e-6)) (pr-str [screen n x]))
    (is (<= (- (+ by bh) 1e-6) y (+ (:h d) 1e-6)) (pr-str [screen n y]))))

(deftest the-final-box-fills-the-region-below-back
  (doseq [screen screens
          :let [d (eb/dimensions {:screen screen})
                cs (eb/quad-corners-of (eb/shape d :fade 0.0))
                [_ by _ bh] gesture/back-region]]
    (testing screen
      (is (near? 0 (apply min (map first cs))))
      (is (near? (:w d) (apply max (map first cs))))
      (is (near? (+ by bh) (apply min (map second cs))))
      (is (near? (:h d) (apply max (map second cs)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [{:keys [w h lines]} (eb/dimensions {:screen screen})]]
    (testing screen
      (is (= 1 (count lines)))
      (doseq [{:keys [s x y size]} lines
              :let [tw (* 0.6 size (count s))]]
        (testing s
          (is (>= x 0))
          (is (<= (+ x tw) w))
          (is (>= y (let [[_ by _ bh] gesture/back-region] (+ by bh))))
          (is (<= (+ y size) h)))))))
