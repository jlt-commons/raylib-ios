(ns raylib.scenes.rotcube-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.rotcube :as rc]
            [raylib.soft3d :as s3]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(defn- frames
  "The state after `n` updates with nothing touching the screen."
  [n]
  (let [{:keys [init update]} (rc/scene)
        m {:screen [1206 2334]}]
    (nth (iterate (fn [s] (first (update s {:metrics m
                                            :pointer {:phase :idle}})))
                  (first (init {:metrics m})))
         n)))

(defn- tris [dl] (filterv (fn [it] (= :tri (nth it 0))) dl))

(defn- inside? [[vx vy vw vh] dl]
  (every? (fn [[_ & more]]
            (every? (fn [[x y]] (and (<= (- vx 1e-6) x (+ vx vw 1e-6)) (<= (- vy 1e-6) y (+ vy vh 1e-6))))
                    (partition 2 (take 6 more))))
          (tris dl)))

(deftest the-cube-turns-at-the-originals-rates
  (testing "the angle is one degree a frame about x and 0.7 of it about y"
    (is (near? 0.0 (rc/angle-x (frames 0))))
    (is (near? 10.0 (rc/angle-x (frames 10))))
    (is (near? 7.0 (rc/angle-y (frames 10))))
    (is (near? 90.0 (rc/angle-x (frames 90))))
    (is (near? 63.0 (rc/angle-y (frames 90)))))
  (testing "the transform is rlRotatef x, then rlRotatef y, composed in that order"
    (is (= (s3/compose (s3/rotate-axis 90.0 1.0 0.0 0.0) (s3/rotate-axis 63.0 0.0 1.0 0.0))
           (rc/transform (frames 90))))
    (is (not= (s3/compose (s3/rotate-axis 63.0 0.0 1.0 0.0) (s3/rotate-axis 90.0 1.0 0.0 0.0))
              (rc/transform (frames 90))) "and the other order is a different turn"))
  (testing "a whole turn of x brings the cube's x rotation back"
    (is (near? 360.0 (rc/angle-x (frames 360))))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                s (frames 0)
                dims (rc/dimensions metrics measure)
                [vx vy vw vh] (:viewport dims)
                base (rc/grid-list (rc/camera dims) dims)
                dl (rc/scene-list base s dims)
                cube (tris dl)]]
    (testing (str screen)
      (is (= 6 (count cube)) "three faces, two triangles each, seen from (4,4,4)")
      (is (pos? (count (remove (fn [it] (= :tri (nth it 0))) dl))) "the grid's lines are there")
      (is (every? (fn [[_ & more]]
                    (every? (fn [[x y]] (and (<= vx x (+ vx vw)) (<= vy y (+ vy vh))))
                            (partition 2 (take 6 more))))
                  cube)
          "the cube's corners all lie inside the field")
      (is (every? (fn [n] (inside? (:viewport dims) (rc/scene-list base (frames n) dims)))
                  (range 0 720 7))
          "the cube stays inside the field through a full spin")
      (is (= (count base) (count (rc/grid-list (rc/camera dims) dims))) "the grid is the same from frame to frame"))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (rc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [_ fy _ fh] (:viewport dims)]]
    (testing (str screen)
      (is (= 1 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) fy) "the caption sits above the field"))
      (is (>= fy (+ back-y back-h)) "the field is below Back")
      (is (near? h (+ fy fh)) "and runs to the bottom"))))
