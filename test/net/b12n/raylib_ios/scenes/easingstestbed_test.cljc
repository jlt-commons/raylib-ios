(ns net.b12n.raylib-ios.scenes.easingstestbed-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.easings :as ez]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.easingstestbed :as tb]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def start (first ((:init (tb/scene)) {:metrics m})))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (tb/advance state {:metrics metrics
                      :pointer {:phase phase
                                :position position}})))

(defn- idle [state] (step state :idle nil))
(defn- frames [state n] (nth (iterate idle state) n))
(defn- tap [state at] (-> state (step :press at) (step :release at)))

(defn- swipe
  "A horizontal swipe from `[x y]`, `dx` pixels long."
  [state [x y] dx]
  (-> state (step :press [x y]) (step :down [(+ x (/ dx 2)) y])
      (step :down [(+ x dx) y]) (step :release [(+ x dx) y])))

(defn- near? [a b] (< (abs (- (double a) (double b))) 1e-6))

(deftest swipes-cycle-curves-both-ways-and-wrap
  (let [n (count tb/curve-names)
        right #(swipe % [300 1200] 400)
        left #(swipe % [900 1200] -400)]
    (is (= 15 n))
    (is (= 0 (:idx start)))
    (is (= 1 (:idx (right start))))
    (is (= 2 (:idx (right (right start)))))
    (is (= (dec n) (:idx (left start))) "left from the first wraps to the last")
    (is (= 0 (:idx (right (left start)))))
    (is (= 0 (:idx (nth (iterate right start) n))) "right from the last wraps to the first")
    (testing "the curve names are the original's, sorted"
      (is (= (sort (map first ez/curves)) tb/curve-names))
      (is (= "back-out" (first tb/curve-names))))
    (testing "a swipe restarts the run"
      (is (near? 0.0 (:counter (right (frames start 50))))))
    (testing "a vertical swipe changes nothing"
      (let [s (-> start (step :press [600 1200]) (step :down [600 1500])
                  (step :release [600 1500]))]
        (is (= 0 (:idx s)))))
    (testing "a swipe that starts in Back belongs to the host"
      (is (= 0 (:idx (swipe start [100 50] 400)))))))

(deftest a-tap-replays
  (let [run (frames start 80)
        again (tap run [600 1200])]
    (is (near? 80.0 (:counter run)))
    (is (near? 0.0 (:counter again)))
    (is (= (:idx run) (:idx again)))
    (is (= (:plot? run) (:plot? again)))
    (testing "a tap in Back belongs to the host"
      (is (= 84.0 (:counter (idle (idle (step (step run :press [100 50]) :release [100 50])))))
          "the tap was ignored, the counter only counted frames"))
    (testing "the run loops after a pause at 1.6 times its length"
      (let [at (fn [k] (:counter (frames start k)))]
        (is (near? tb/duration (at 120)))
        (is (near? 192.0 (at 192)))
        (is (near? 0.0 (at 193)))))))

(deftest a-long-press-toggles-the-plot
  (let [held (reduce (fn [s _] (step s :down [600 1200]))
                     (step start :press [600 1200])
                     (range (+ gesture/long-press-frames 3)))
        released (step held :release [600 1200])]
    (is (true? (:plot? start)))
    (is (false? (:plot? held)))
    (testing "toggles once, not every frame it is held"
      (is (false? (:plot? (step held :down [600 1200])))))
    (testing "lifting the finger afterwards does not replay or toggle again"
      (is (false? (:plot? released)))
      (is (= (:counter (idle held)) (:counter released))))
    (testing "a second long press brings it back"
      (let [again (reduce (fn [s _] (step s :down [600 1200]))
                          (step released :press [600 1200])
                          (range (+ gesture/long-press-frames 3)))]
        (is (true? (:plot? again)))))
    (testing "a long press in Back belongs to the host"
      (let [back (reduce (fn [s _] (step s :down [100 50]))
                         (step start :press [100 50])
                         (range (+ gesture/long-press-frames 3)))]
        (is (true? (:plot? back)))))))

(deftest the-plot-samples-the-current-curve
  (doseq [screen screens
          [nm f] (map (fn [n] [n (get (into {} ez/curves) n)]) tb/curve-names)
          :let [d (tb/dimensions {:screen screen})
                pts (tb/plot-points d f)
                {:keys [plot-x plot-w plot-y plot-h]} d
                bottom (+ plot-y plot-h)]]
    (testing [screen nm]
      (is (= (inc tb/plot-samples) (count pts)))
      (is (near? plot-x (first (first pts))))
      (is (near? (+ plot-x plot-w) (first (last pts))))
      (testing "y is the value across the band, 1 at the top"
        (is (near? bottom (second (first pts))))
        (let [[x y] (nth pts (quot tb/plot-samples 2))
              frac (/ (- x plot-x) plot-w)
              v (f (* frac tb/duration) 0.0 1.0 tb/duration)]
          (is (near? (- bottom (* v plot-h)) y)))
        (is (near? (- bottom plot-h) (second (last pts))))))))

(deftest everything-drawn-stays-inside-the-safe-region
  (doseq [screen screens
          nm tb/curve-names
          :let [d (tb/dimensions {:screen screen})
                f (get (into {} ez/curves) nm)
                [_ by _ bh] gesture/back-region
                top (+ by bh)]
          [x y] (concat (tb/plot-points d f)
                        (map #(tb/ball d f %) (range 0 193 4)))]
    (is (<= (- (+ top 0) 1e-6) y (+ (:h d) 1e-6)) (pr-str [screen nm y]))
    (is (<= -1e-6 x (+ (:w d) 1e-6)) (pr-str [screen nm x])))
  (doseq [screen screens
          nm tb/curve-names
          :let [d (tb/dimensions {:screen screen})
                f (get (into {} ez/curves) nm)
                r (:ball-r d)]
          counter (range 0 193 4)
          :let [[x y] (tb/ball d f counter)]]
    (is (<= r x (- (:w d) r)) (pr-str [screen nm counter x]))
    (is (<= r y (- (:h d) r)) (pr-str [screen nm counter y]))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [{:keys [w h lines plot-y]} (tb/dimensions {:screen screen})]]
    (testing screen
      (is (= 3 (count lines)))
      (doseq [{:keys [s x y size]} lines
              :let [tw (* 0.6 size (count s))]]
        (testing s
          (is (>= x 0))
          (is (<= (+ x tw) w))
          (is (>= y (let [[_ by _ bh] gesture/back-region] (+ by bh))))
          (is (<= (+ y size) h))))
      (testing "the plot starts below the text"
        (let [{:keys [y size]} (last lines)]
          (is (> plot-y (+ y size))))))))
