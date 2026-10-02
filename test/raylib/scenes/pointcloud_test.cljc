(ns raylib.scenes.pointcloud-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.pointcloud :as sc]
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

(deftest the-cloud-is-1500-seeded-points
  (testing "1500 points, as the original"
    (is (= 1500 sc/n-points))
    (is (= 1500 (count sc/points))))
  (testing "the same cloud every time: building it again gives the same points"
    (is (= sc/points (sc/make-points))))
  (testing "the first two points, from the LCG seeded 20261002 and read by hand"
    ;; Each coordinate is (mod (quot seed' 65536) 101) - 50 over 10. The first six
    ;; draws are 23, -27, 40, 0, 46, -31; the colours are (int (+ 128 (* 25 c))).
    (is (= [2.3 -2.7 4.0 [185 60 228 255]] (first sc/points)))
    (is (= [0.0 4.6 -3.1 [128 243 50 255]] (second sc/points))))
  (testing "every coordinate is a tenth in [-5, 5]"
    (is (every? (fn [[x y z]] (every? (fn [c] (and (<= -5.0 c 5.0) (near? c (/ (Math/round (* 10.0 c)) 10.0)))) [x y z]))
                sc/points)))
  (testing "the cloud fills the box: both ends of each axis are close to reached"
    (doseq [axis [0 1 2]
            :let [vs (map #(nth % axis) sc/points)]]
      (is (< (reduce min vs) -4.9))
      (is (> (reduce max vs) 4.9))))
  (testing "colour follows position, (int (+ 128 (* 25 c))) per axis, alpha 255"
    (is (every? (fn [[x y z [r g b a]]]
                  (= [(int (+ 128 (* 25 x))) (int (+ 128 (* 25 y))) (int (+ 128 (* 25 z))) 255] [r g b a]))
                sc/points))))

(deftest the-cloud-turns-0-point-3-degrees-a-frame
  (is (near? 0.0 (sc/angle (frames 0))))
  (is (near? 0.3 (sc/angle (frames 1))))
  (is (near? 90.0 (sc/angle (frames 300))))
  (is (= (s3/rotate-axis 90.0 0.0 1.0 0.0) (sc/transform (frames 300))) "rlRotatef(angle, 0, 1, 0)")
  (is (= 12.0 (nth (:position (sc/camera (sc/dimensions {:screen [800 450]} measure))) 2)))
  (is (= [0.0 0.0 12.0] (:position (sc/camera (sc/dimensions {:screen [800 450]} measure))))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                dims (sc/dimensions metrics measure)
                dl (sc/scene-list (frames 0) dims)
                faces (tris dl)]]
    (testing (str screen)
      (is (= 3000 (count faces)) "every point is in front of the camera: a square of 2 triangles each")
      (is (= (count faces) (count dl)) "no lines")
      (is (every? (fn [[_ _ _ _ _ _ _ r g b a]] (some #{[r g b a]} (map #(nth % 3) sc/points))) faces)
          "each square wears its point's colour")
      (is (= (set (map #(nth % 3) sc/points)) (set (map (fn [it] (subvec it 7 11)) faces)))
          "and every point's colour is on some square")))
  (testing "on the phone every corner stays inside the field through a whole turn"
    (let [dims (sc/dimensions {:screen [1206 2334]} measure)]
      (is (every? (fn [n] (inside? (:viewport dims) (sc/scene-list (frames n) dims)))
                  (range 0 1200 40)))))
  (testing "a square is a few pixels across, not a speck and not a blob"
    (let [dims (sc/dimensions {:screen [1206 2334]} measure)
          sizes (map (fn [[_ x1 _ _ _ x3]] (abs (- x3 x1))) (tris (sc/scene-list (frames 0) dims)))]
      (is (< 0.5 (reduce min sizes)))
      (is (> 20.0 (reduce max sizes))))))

(deftest squares-wind-like-rlgl-keeps
  (let [dims (sc/dimensions {:screen [1206 2334]} measure)]
    (is (= 3000 (count (tris (sc/scene-list (frames 77) dims)))))
    (is (every? (fn [[_ x1 y1 x2 y2 x3 y3]]
                  (neg? (- (* (- x2 x1) (- y3 y1)) (* (- y2 y1) (- x3 x1)))))
                (tris (sc/scene-list (frames 77) dims))))))

(deftest nearer-points-draw-last
  (let [dims (sc/dimensions {:screen [1206 2334]} measure)
        ds (map #(nth % 11) (sc/scene-list (frames 0) dims))]
    (is (= 3000 (count ds)))
    (is (= ds (sort > ds)) "far to near by depth")))

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
