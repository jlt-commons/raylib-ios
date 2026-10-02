(ns raylib.scenes.spincubes-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.spincubes :as sc]
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
  (let [{:keys [init update]} (sc/scene)
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

(deftest each-cube-spins-at-its-own-rate
  (testing "every cube turns 2 degrees a frame, 30 degrees ahead of the one before"
    (doseq [n [0 1 10 90]
            i (range 5)]
      (is (near? (+ (* 2.0 n) (* 30.0 i)) (sc/angle (frames n) i)) (str "frame " n " cube " i)))
    (is (near? 30.0 (- (sc/angle (frames 0) 1) (sc/angle (frames 0) 0))))
    (is (near? 120.0 (- (sc/angle (frames 7) 4) (sc/angle (frames 7) 0)))))
  (testing "the cubes stand at -4, -2, 0, 2 and 4"
    (is (= [-4.0 -2.0 0.0 2.0 4.0] (mapv sc/cube-x (range 5)))))
  (testing "the transform is rlTranslatef, then rlRotatef about (0.3, 1, 0), in that order"
    (is (= (s3/compose (s3/translate 2.0 0.5 0.0) (s3/rotate-axis 124.0 0.3 1.0 0.0))
           (sc/transform (frames 17) 3)))
    (is (not= (s3/compose (s3/rotate-axis 124.0 0.3 1.0 0.0) (s3/translate 2.0 0.5 0.0))
              (sc/transform (frames 17) 3)) "and the other order is a different place"))
  (testing "two cubes at the same frame are turned differently"
    (is (not= (sc/transform (frames 5) 0) (sc/transform (frames 5) 1)))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                dims (sc/dimensions metrics measure)
                base (sc/grid-list (sc/camera dims) dims)
                dl (sc/scene-list base (frames 0) dims)
                faces (tris dl)]]
    (testing (str screen)
      (is (<= 10 (count faces) 30) "two to three faces of each of five cubes")
      (is (every? (fn [[r g b]]
                    (some (fn [shade] (some (fn [it] (= [(int (* shade r)) (int (* shade g)) (int (* shade b))]
                                                        (subvec it 7 10)))
                                            faces))
                          [1.0 0.85 0.7 0.5 0.4]))
                  sc/palette)
          "every cube's colour is on a face, shaded as cube! shades it")
      (is (= (count base) (count (remove (fn [it] (= :tri (nth it 0))) dl))) "and the grid's 26 lines are all there")
      (is (= 26 (count base)))
      (is (inside? (:viewport dims) dl) "every corner lies inside the field")
      (is (every? (fn [n] (inside? (:viewport dims) (sc/scene-list base (frames n) dims)))
                  (range 0 360 5))
          "the cubes stay inside the field through a spin")
      (is (= (count base) (count (sc/grid-list (sc/camera dims) dims))) "the grid is the same from frame to frame"))))

(deftest the-palette-runs-left-to-right
  ;; Cube i stands at x = 2i - 4 and wears palette colour i, so the faces of
  ;; each colour sit further right than the last colour's. A palette shifted by
  ;; one cube would break the order.
  (let [dims (sc/dimensions {:screen [1206 2334]} measure)
        base (sc/grid-list (sc/camera dims) dims)
        faces (tris (sc/scene-list base (frames 0) dims))
        shades [1.0 0.85 0.7 0.5 0.4]
        colour-of (fn [[r g b]]
                    (set (map (fn [sh] [(int (* sh r)) (int (* sh g)) (int (* sh b))]) shades)))
        mean-x (fn [colour]
                 (let [mine (filter #(contains? (colour-of colour) (subvec % 7 10)) faces)]
                   (/ (reduce + (map (fn [it] (/ (+ (nth it 1) (nth it 3) (nth it 5)) 3.0)) mine))
                      (count mine))))
        xs (mapv mean-x sc/palette)]
    (is (= 5 (count xs)))
    (is (apply < xs) "red is leftmost and violet rightmost")
    (is (every? #(< (nth xs %) (nth xs (inc %))) (range 4)))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
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
