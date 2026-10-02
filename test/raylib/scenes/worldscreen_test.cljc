(ns raylib.scenes.worldscreen-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.worldscreen :as ws]
            [raylib.soft3d :as s3]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(defn- stepped
  "The state after `n` updates of `dt` seconds each."
  [n dt]
  (let [{:keys [init update]} (ws/scene)
        m {:screen [1206 2334]}]
    (nth (iterate (fn [s] (first (update s {:metrics m
                                            :delta-seconds dt
                                            :pointer {:phase :idle}})))
                  (first (init {:metrics m})))
         n)))

(defn- tris [dl] (filterv (fn [it] (= :tri (nth it 0))) dl))

(defn- inside? [[vx vy vw vh] dl]
  (every? (fn [[_ & more]]
            (every? (fn [[x y]] (and (<= (- vx 1e-6) x (+ vx vw 1e-6)) (<= (- vy 1e-6) y (+ vy vh 1e-6))))
                    (partition 2 (take 6 more))))
          (tris dl)))

(deftest the-orbit-follows-delta-seconds
  (is (near? 0.7854 (:t (stepped 0 0.016))))
  (is (near? (+ 0.7854 (* 0.3 0.5)) (:t (stepped 1 0.5))))
  (is (near? (+ 0.7854 (* 0.3 1.0)) (:t (stepped 60 (/ 1.0 60)))))
  (is (near? 0.7854 (:t (stepped 5 0.0))) "a frame of no time does not move it")
  (is (near? 0.7854 (:t (stepped 5 -1.0))) "and a negative one does not run it backwards"))

(deftest the-label-sits-at-the-projected-point
  (doseq [screen screens
          n [0 40 200]
          :let [dims (ws/dimensions {:screen screen} measure)
                state (stepped n 0.1)
                vp (ws/view state dims)
                [px py] (s3/world->screen vp [0.0 2.5 0.0])
                [_ cy] (s3/world->screen vp [0.0 0.0 0.0])
                lab (ws/label state dims)]]
    (testing (str screen " after " n " frames")
      (is (= (long px) (:sx lab)) "its x is the world point's, truncated")
      (is (= (long py) (:sy lab)))
      (is (= (:sy lab) (:y lab)) "its top edge is at the projected y")
      (is (< py cy) "the point is above the cube's centre")
      (is (= (str "Cube screen position: [" (:sx lab) ", " (:sy lab) "]")
             (:s (ws/readout state dims))) "and the readout says the same"))))

(deftest the-label-is-centred-by-measure
  (doseq [m [measure
             (fn [s size] (* 0.45 size (count s)))
             (fn [s size] (+ 3 (* 0.5 size (count s))))]
          screen screens
          :let [dims (ws/dimensions {:screen screen} m)
                lab (ws/label (stepped 12 0.1) dims)
                w (m "Enemy: 100/100" (:size dims))]]
    (testing (str screen)
      (is (= w (:label-w dims)) "the width is measured at the label's own size")
      (is (<= (abs (- (+ (:x lab) (/ w 2.0)) (:sx lab))) 1.0) "half the width either side of the point"))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [dims (ws/dimensions {:screen screen} measure)
                state (stepped 0 0.016)
                dl (ws/scene-list state dims)
                faces (tris dl)]]
    (testing (str screen)
      (is (<= 2 (count faces) 6) "one to three faces of the cube")
      (is (every? (fn [it] (and (> (nth it 7) (nth it 8)) (> (nth it 7) (nth it 9)) (= 255 (nth it 10)))) faces)
          "every face is a shade of red")
      (is (= 22 (count (remove (fn [it] (= :tri (nth it 0))) dl))) "the grid of 10 is 22 lines")
      (is (inside? (:viewport dims) dl) "every corner lies inside the field")
      (is (every? (fn [n] (let [s (stepped n 0.1)
                                d (ws/scene-list s dims)]
                            (and (inside? (:viewport dims) d)
                                 (let [{:keys [x y size sx sy]} (ws/label s dims)
                                       [vx vy vw vh] (:viewport dims)]
                                   (and (<= vx sx (+ vx vw)) (<= vy sy (+ vy vh))
                                        (>= x 0) (<= (+ x (:label-w dims)) (first screen))
                                        (<= (+ y size) (second screen)))))))
                  (range 0 400 10))
          "the cube and its label stay in the field through an orbit"))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          n [0 100 300]
          :let [[w h] screen
                dims (ws/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [_ fy _ fh] (:viewport dims)
                ls (ws/lines (stepped n 0.1) dims)]]
    (testing (str screen)
      (is (= 3 (count ls)))
      (doseq [{:keys [s x y size]} ls]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) h) s))
      (is (<= (+ (:y (first ls)) (:size (first ls))) fy) "the caption sits above the field")
      (is (>= (:y (second ls)) fy) "and the readout inside it")
      (is (>= fy (+ back-y back-h)) "the field is below Back")
      (is (near? h (+ fy fh)) "and runs to the bottom"))))
