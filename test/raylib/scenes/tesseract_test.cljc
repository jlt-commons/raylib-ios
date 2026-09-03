(ns raylib.scenes.tesseract-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.tesseract :as t]))

(def metrics {:screen [1206 2622]})

(deftest the-figure-is-a-hypercube
  (testing "16 vertices, every coordinate plus or minus one"
    (is (= 16 (count t/verts)))
    (is (= 16 (count (set t/verts))))
    (is (every? (fn [v] (every? (fn [c] (or (== c -1.0) (== c 1.0))) v)) t/verts)))
  (testing "32 edges, which is the 4D cube's own count: 4 * 2^3"
    (is (= 32 (count t/edges))))
  (testing "an edge joins vertices differing in exactly one coordinate"
    (is (every? (fn [[i j]]
                  (= 1 (count (filter false? (map == (nth t/verts i) (nth t/verts j))))))
                t/edges)))
  (testing "every vertex has degree 4, one edge per dimension"
    (let [deg (frequencies (mapcat identity t/edges))]
      (is (= 16 (count deg)))
      (is (= #{4} (set (vals deg)))))))

(deftest rot4-is-a-rotation
  (testing "it preserves length, which is what makes it a rotation and not a shear"
    (let [norm (fn [v] (Math/sqrt (reduce + (map * v v))))]
      (doseq [v t/verts
              a [0.0 0.3 1.1 2.7]]
        (is (< (abs (- (norm v) (norm (t/rot4 v a (* a 0.6))))) 1e-9)))))
  (testing "angle zero is the identity"
    (doseq [v t/verts]
      (is (every? (fn [[p q]] (< (abs (- p q)) 1e-12)) (map vector v (t/rot4 v 0.0 0.0)))))))

(deftest edges-are-coloured-by-which-cube-they-belong-to
  (let [by-colour (frequencies (map (fn [[i j]] (t/edge-colour i j)) t/edges))]
    (testing "12 edges in each cube, since a 3-cube has 12, and 8 joining them"
      (is (= 12 (get by-colour [255 90 90])))
      (is (= 12 (get by-colour [90 170 255])))
      (is (= 8 (get by-colour [110 240 110]))))
    (testing "which is the whole edge set, nothing uncoloured"
      (is (= 32 (reduce + (vals by-colour)))))))

(deftest projection-lands-on-screen-and-moves
  (let [[w h] (:screen metrics)]
    (testing "every vertex projects inside the screen at a few angles"
      (doseq [a [0.0 0.5 1.7 3.9]]
        (doseq [[x y] (t/points metrics a)]
          (is (and (< -1.0 x (double w)) (< -1.0 y (double h)))
              (str "vertex off screen at angle " a ": " [x y])))))
    (testing "rotating actually moves the figure"
      (is (not= (t/points metrics 0.0) (t/points metrics 0.4))))))
