(ns raylib.gesture-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as g]))

(def m {:screen [1206 2334]})

(defn- in [phase position]
  {:pointer {:phase phase
             :position position}
   :metrics m})

(defn- run
  "Feed `frames`, a seq of [phase position], through `track` from `idle`.
  Returns every event emitted, in order, paired with the final gesture."
  [frames]
  (reduce (fn [[gesture events] [phase position]]
            (let [[gesture' event] (g/track gesture (in phase position))]
              [gesture' (cond-> events event (conj event))]))
          [g/idle []]
          frames))

(defn- events [frames] (second (run frames)))

(def holding (repeat 40 [:down [100 100]]))

(deftest thresholds-follow-the-shorter-side
  (is (< (abs (- 21.708 (g/slop m))) 1e-6))
  (is (< (abs (- 96.48 (g/swipe-min m))) 1e-6))
  (is (< (abs (- 21.708 (g/slop {:screen [2334 1206]}))) 1e-6)))

(deftest a-press-and-release-in-place-is-a-tap
  (is (= [{:type :tap
           :at [100 100]}]
         (events [[:press [100 100]] [:down [100 100]] [:release [100 100]]]))))

(deftest a-tap-reports-where-it-started
  (is (= [{:type :tap
           :at [100 100]}]
         (events [[:press [100 100]] [:down [110 105]] [:release nil]]))))

(deftest a-wandering-finger-is-not-a-tap
  (testing "it strays past the slop and comes back to where it started"
    (is (= [] (events [[:press [100 100]] [:down [150 100]]
                       [:down [100 100]] [:release [100 100]]])))))

(deftest each-direction-swipes
  (doseq [[dir to] {:left [0 300]
                    :right [300 300]
                    :up [150 150]
                    :down [150 450]}]
    (is (= [{:type :swipe
             :dir dir
             :from [150 300]}]
           (events [[:press [150 300]] [:down to] [:release nil]]))
        (str dir))))

(deftest the-dominant-axis-wins
  (is (= :right (:dir (first (events [[:press [100 100]] [:down [300 150]]
                                      [:release nil]])))))
  (is (= :up (:dir (first (events [[:press [100 500]] [:down [150 300]]
                                   [:release nil]]))))))

(deftest a-short-drag-is-neither-swipe-nor-tap
  (is (= [] (events [[:press [100 100]] [:down [160 100]] [:release nil]]))))

(deftest a-swipe-ignores-the-release-position
  (testing "the release carries a stale hardware position far the other way"
    (is (= [{:type :swipe
             :dir :right
             :from [100 100]}]
           (events [[:press [100 100]] [:down [300 100]] [:release [0 2000]]])))))

(deftest a-hold-in-place-long-presses-once
  (let [frames (concat [[:press [100 100]]] holding)
        evs (events frames)]
    (is (= [{:type :long-press
             :at [100 100]}] evs))
    (testing "not before the threshold"
      (is (= [] (events (concat [[:press [100 100]]]
                                (repeat (dec g/long-press-frames) [:down [100 100]]))))))))

(deftest a-long-press-then-release-is-not-a-tap
  (is (= [{:type :long-press
           :at [100 100]}]
         (events (concat [[:press [100 100]]] holding [[:release [100 100]]])))))

(deftest a-hold-that-moves-is-not-a-long-press
  (is (= [] (events (concat [[:press [100 100]] [:down [160 100]]]
                            holding)))))

(deftest a-gesture-seen-only-from-down-is-ignored
  (let [frames [[:down [100 100]] [:down [300 100]] [:release [300 100]]]]
    (is (= [] (events frames)))
    (is (= g/idle (first (run frames)))))
  (testing "even a long hold with no press"
    (is (= [] (events (concat holding [[:release nil]]))))))

(deftest a-press-without-a-point-stays-idle
  (is (= g/idle (first (g/track g/idle (in :press nil))))))

(deftest a-release-and-idle-return-to-rest
  (is (= g/idle (first (run [[:press [1 1]] [:release nil]]))))
  (let [[held _] (run [[:press [1 1]]])]
    (is (= [held nil] (g/track held (in :idle nil))))))

(deftest down-means-a-finger-with-a-point
  (is (g/down? (in :press [1 1])))
  (is (g/down? (in :down [1 1])))
  (is (not (g/down? (in :release [1 1]))))
  (is (not (g/down? (in :idle nil))))
  (is (not (g/down? (in :press nil)))))

(deftest in-rect-is-half-open
  (is (g/in-rect? [10 20 30 40] [10 20]))
  (is (g/in-rect? [10 20 30 40] [39 59]))
  (is (not (g/in-rect? [10 20 30 40] [40 30])))
  (is (not (g/in-rect? [10 20 30 40] [20 60])))
  (is (not (g/in-rect? [10 20 30 40] [9 30]))))

(deftest back-region-matches-the-measured-back-button
  (let [[bx by bw bh] [40 40 330 73]]
    (is (g/in-back-region? [bx by]))
    (is (g/in-back-region? [(+ bx bw) (+ by bh)]))
    (is (not (g/in-back-region? [500 60])))))
