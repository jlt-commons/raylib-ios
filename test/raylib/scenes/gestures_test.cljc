(ns raylib.scenes.gestures-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.gestures :as g]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def portrait {:screen [1206 2334]})

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- centre [[x y w h]] [(+ x (* 0.5 w)) (+ y (* 0.5 h))])

(def dims (g/dimensions portrait measure))
(def in-area (centre (:area dims)))
(def in-log (centre (get-in dims [:log :rect])))
(def start (first ((:init (g/scene)) {:metrics portrait})))

(defn- step
  "One frame: `code` is raylib's gesture, the finger is at `pos` in `phase`."
  ([state code pos] (step state code pos :down))
  ([state code pos phase]
   (g/advance state {:metrics portrait
                     :raylib-gesture code
                     :pointer {:phase phase
                               :position pos}})))

(deftest the-gesture-codes-are-raylibs
  (is (= {1 "GESTURE TAP"
          2 "GESTURE DOUBLETAP"
          4 "GESTURE HOLD"
          8 "GESTURE DRAG"
          16 "GESTURE SWIPE RIGHT"
          32 "GESTURE SWIPE LEFT"
          64 "GESTURE SWIPE UP"
          128 "GESTURE SWIPE DOWN"
          256 "GESTURE PINCH IN"
          512 "GESTURE PINCH OUT"}
         g/gesture-names))
  (is (= 20 g/max-log)))

(deftest a-new-gesture-in-the-area-is-logged
  (let [s (step start 1 in-area :press)]
    (is (= ["GESTURE TAP"] (:log s)))
    (is (= 1 (:last-gesture s)))
    (is (= in-area (:at s)))))

(deftest a-repeated-gesture-is-logged-once
  (let [held (reduce (fn [s _] (step s 4 in-area)) start (range 6))]
    (is (= ["GESTURE HOLD"] (:log held)))
    (testing "the gesture ending and starting again is a new entry"
      (let [s (-> held (step 0 in-area) (step 4 in-area))]
        (is (= ["GESTURE HOLD" "GESTURE HOLD"] (:log s)))))
    (testing "a different gesture straight after is a new entry"
      (is (= ["GESTURE HOLD" "GESTURE DRAG"] (:log (step held 8 in-area)))))
    (testing "no gesture is never logged"
      (is (= [] (:log (step start 0 in-area)))))))

(deftest outside-the-area-is-ignored
  (let [s (step start 1 in-log)]
    (is (= [] (:log s)))
    (testing "but it still counts as the last gesture, as in the original"
      (is (= 1 (:last-gesture s)))
      (is (= [] (:log (step s 1 in-area)))))
    (testing "the area is half-open and the left and top edges are in"
      (let [[ax ay aw ah] (:area dims)]
        (is (= 1 (count (:log (step start 1 [ax ay])))))
        (is (= [] (:log (step start 1 [(+ ax aw) ay]))))
        (is (= [] (:log (step start 1 [ax (+ ay ah)]))))))))

(deftest a-gesture-with-no-pointer-position-uses-the-last-one
  (testing "a swipe reported on release lands where the finger last was"
    (let [s (-> start (step 8 in-area) (step 0 in-area)
                (step 32 nil :release))]
      (is (= ["GESTURE DRAG" "GESTURE SWIPE LEFT"] (:log s)))
      (is (= in-area (:at s)))))
  (testing "the release position itself is never read"
    (let [s (-> start (step 0 in-area) (step 16 in-log :release))]
      (is (= ["GESTURE SWIPE RIGHT"] (:log s)))
      (is (= in-area (:at s))))
    (let [s (-> start (step 0 in-log) (step 16 in-area :release))]
      (is (= [] (:log s)))))
  (testing "with no position ever seen the gesture is ignored"
    (let [s (step start 1 nil :idle)]
      (is (= [] (:log s)))
      (is (nil? (:at s)))
      (is (= 1 (:last-gesture s))))))

(deftest the-log-keeps-twenty
  (let [codes (take 50 (cycle [1 2 4 8 16 32 64 128]))
        s (reduce (fn [s code] (-> s (step code in-area) (step 0 in-area)))
                  start codes)]
    (is (= 20 (count (:log s))))
    (is (= (mapv g/gesture-name (take-last 20 codes)) (:log s)) "the newest 20, oldest first")))

(deftest unknown-codes-get-a-name
  (is (= "GESTURE 1024" (g/gesture-name 1024)))
  (is (= "GESTURE 3" (g/gesture-name 3)))
  (is (= ["GESTURE 1024"] (:log (step start 1024 in-area))))
  (is (= "GESTURE TAP" (g/gesture-name 1))))

(deftest the-layout-is-below-back-and-the-pieces-do-not-overlap
  (doseq [screen screens
          :let [[w h] screen
                d (g/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [lx ly lw lh] (get-in d [:log :rect])
                [ax ay _ _] (:area d)]]
    (testing (str screen)
      (is (= (< w h) (:portrait? d)))
      (doseq [[x y rw rh] [(get-in d [:log :rect]) (:area d)]]
        (is (>= x 0)) (is (>= y (+ back-y back-h)))
        (is (<= (+ x rw) w)) (is (<= (+ y rh) h)))
      (is (or (<= (+ lx lw) ax) (<= (+ ly lh) ay)) "log and area are apart")
      (testing "twenty rows fit the log"
        (let [{:keys [rows-y row-h]} (:log d)]
          (is (<= (+ rows-y (* g/max-log row-h)) (+ ly lh)))
          (is (>= rows-y ly)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                d (g/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (<= 4 (count (:lines d))))
      (doseq [{:keys [s x y size]} (:lines d)]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) h) s)))
    (testing "every name fits its log row, and the hint fits the area"
      (let [[lx _ lw _] (get-in d [:log :rect])
            [ax _ aw _] (:area d)]
        (doseq [n (vals g/gesture-names)]
          (is (<= (+ (get-in d [:log :text-x]) (measure n (:size (:log d)))) (+ lx lw)) n))
        (is (<= (+ ax (measure (:hint d) (:hint-size d))) (+ ax aw)))))))

(deftest the-hint-says-pinch-is-unavailable
  (is (re-find #"(?i)pinch" (:hint dims))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                s (first ((:init (g/scene)) {:metrics metrics}))
                d (g/dimensions metrics measure)]]
    (testing (str screen)
      (is (= [] (:log s)))
      (is (= 0 (:last-gesture s)))
      (is (nil? (:at s)))
      (is (every? number? (concat (:area d) (get-in d [:log :rect])
                                  [(:circle-radius d) (:hint-size d)
                                   (get-in d [:log :size]) (get-in d [:log :row-h])])))
      (is (pos? (:circle-radius d)))
      (is (string? (:hint d))))))
