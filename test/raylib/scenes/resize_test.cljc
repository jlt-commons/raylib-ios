(ns raylib.scenes.resize-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.resize :as rz]))

(def m {:screen [1206 2334]})
(def d (rz/dimensions m))
(def start (first ((:init (rz/scene)) {:metrics m})))

(deftest the-handle-sits-on-the-far-corner-and-is-bigger-than-it-looks
  (let [[hx hy hw hh] (rz/handle-box d 500.0 400.0)]
    (is (rz/in-box? [hx hy hw hh] [(+ (:x d) 500.0) (+ (:y d) 400.0)])
        "the corner itself is inside the grab box")
    (testing "and the box is larger than the drawn triangle, because a finger is
              not a cursor: you aim at the triangle and hit this"
      (is (> hw (:handle d)))
      (is (> hh (:handle d))))))

(deftest the-grab-is-sticky
  (testing "once taken, the handle is held until the finger lifts, even when the
            finger outruns the corner. Re-testing containment every frame is the
            obvious version and it drops the rectangle the moment you drag fast."
    (let [[hx hy] (rz/handle-box d (:rw start) (:rh start))
          grabbed (rz/advance start {:metrics m
                                     :pointer {:phase :press :position [(+ hx 10) (+ hy 10)]}})
          far (rz/advance grabbed {:metrics m
                                   :pointer {:phase :down :position [1100 2000]}})]
      (is (:holding? grabbed))
      (is (:holding? far) "still held, far outside the original handle box")
      (is (not= (:rw start) (:rw far))))))

(deftest a-press-away-from-the-handle-grabs-nothing
  (let [s (rz/advance start {:metrics m :pointer {:phase :press :position [100 100]}})]
    (is (not (:holding? s)))
    (is (= (:rw start) (:rw s)) "and the rectangle does not jump to the finger")))

(deftest lifting-releases-the-handle
  (let [[hx hy] (rz/handle-box d (:rw start) (:rh start))
        grabbed (rz/advance start {:metrics m :pointer {:phase :press :position [(+ hx 5) (+ hy 5)]}})
        lifted (rz/advance grabbed {:metrics m :pointer {:phase :release :position nil}})]
    (is (not (:holding? lifted)))))

(deftest the-rectangle-cannot-be-inverted-or-run-off-the-screen
  (testing "dragging past the top-left floors it rather than producing a
            negative extent, which raylib draws as an enormous unsigned one"
    (doseq [pt [[0 0] [-500 -500] [(:x d) (:y d)]]]
      (let [[rw rh] (rz/resize-to d pt)]
        (is (>= rw (:min-w d)))
        (is (>= rh (:min-h d))))))
  (testing "and dragging past the far edge stops inside the screen"
    (doseq [pt [[5000 5000] [1206 2334]]]
      (let [[rw rh] (rz/resize-to d pt)]
        (is (<= (+ (:x d) rw) 1206.0))
        (is (<= (+ (:y d) rh) 2334.0))))))

(deftest resizing-tracks-the-finger-between-the-limits
  (let [pt [700.0 900.0]
        [rw rh] (rz/resize-to d pt)]
    (is (< (abs (- (- 700.0 (:x d)) rw)) 1e-9))
    (is (< (abs (- (- 900.0 (:y d)) rh)) 1e-9))))

(deftest a-full-drag-leaves-a-sane-rectangle
  (let [[hx hy] (rz/handle-box d (:rw start) (:rh start))
        run (reduce (fn [s p] (rz/advance s {:metrics m :pointer {:phase :down :position p}}))
                    (rz/advance start {:metrics m :pointer {:phase :press :position [(+ hx 5) (+ hy 5)]}})
                    [[900 1400] [400 700] [1100 2100] [200 300]])]
    (is (>= (:rw run) (:min-w d)))
    (is (>= (:rh run) (:min-h d)))
    (is (<= (+ (:x d) (:rw run)) 1206.0))
    (is (<= (+ (:y d) (:rh run)) 2334.0))))
