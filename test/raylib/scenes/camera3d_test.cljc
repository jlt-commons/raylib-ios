(ns raylib.scenes.camera3d-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.camera3d :as c3]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(defn- frames [n]
  (let [{:keys [init update]} (c3/scene)
        m {:screen [1206 2334]}]
    (nth (iterate (fn [s] (first (update s {:metrics m
                                            :pointer {:phase :idle}})))
                  (first (init {:metrics m})))
         n)))

(defn- tris [dl] (filterv (fn [it] (= :tri (nth it 0))) dl))

(deftest the-camera-orbits-at-the-originals-rate
  (testing "frame 0 is at (12, 8, 0) looking at (0, 1, 0), fovy 45, perspective"
    (let [c (c3/camera (frames 0))]
      (is (every? true? (map near? [12.0 8.0 0.0] (:position c))))
      (is (= [0.0 1.0 0.0] (:target c)))
      (is (= [0.0 1.0 0.0] (:up c)))
      (is (= 45.0 (:fovy c)))
      (is (= :perspective (:projection c)))))
  (testing "0.02 radians a frame at radius 12, height fixed at 8"
    (doseq [n [1 10 100 314]
            :let [[x y z] (:position (c3/camera (frames n)))
                  a (* 0.02 n)]]
      (is (near? (* 12.0 (Math/cos a)) x) (str n))
      (is (near? 8.0 y) (str n))
      (is (near? (* 12.0 (Math/sin a)) z) (str n)))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                s (frames 0)
                dims (c3/dimensions metrics measure)
                [vx vy vw vh] (:viewport dims)
                dl (c3/scene-list s dims)
                cube (tris dl)]]
    (testing (str screen)
      (is (= 4 (count cube)) "two faces, +x and +y, two triangles each, seen from (12,8,0)")
      (is (> (count (remove (fn [it] (= :tri (nth it 0))) dl)) 40) "the grid of 20 has 42 lines")
      (is (every? (fn [[_ & more]]
                    (every? (fn [[x y]] (and (<= vx x (+ vx vw)) (<= vy y (+ vy vh))))
                            (partition 2 (take 6 more))))
                  cube)
          "the cube's corners all lie inside the field"))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (c3/dimensions {:screen screen} measure)
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
