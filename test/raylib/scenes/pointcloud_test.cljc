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

(defn- near-1e-6? [a b] (< (abs (double (- a b))) 1e-6))

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
  (testing "400 points here, 1500 in the original"
    (is (= 400 sc/n-points))
    (is (= 1500 sc/original-points))
    (is (= 400 (count sc/points))))
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
      (is (< (reduce min vs) -4.5))
      (is (> (reduce max vs) 4.5))))
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
      (is (= 800 (count faces)) "every point is in front of the camera: a square of 2 triangles each")
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
    (is (= 800 (count (tris (sc/scene-list (frames 77) dims)))))
    (is (every? (fn [[_ x1 y1 x2 y2 x3 y3]]
                  (neg? (- (* (- x2 x1) (- y3 y1)) (* (- y2 y1) (- x3 x1)))))
                (tris (sc/scene-list (frames 77) dims))))))

(deftest far-points-draw-first-to-within-a-bucket
  (let [dims (sc/dimensions {:screen [1206 2334]} measure)
        ds (map #(nth % 11) (sc/scene-list (frames 0) dims))]
    (is (= 800 (count ds)))
    (is (every? (fn [[a b]] (>= (+ a sc/bucket-width) b)) (partition 2 1 ds))
        "never nearer than the bucket before it by more than a bucket's depth")
    (is (> (first ds) (+ 5.0 (last ds))) "and the run goes from far to near")
    (is (apply = (map #(nth % 11) (take 2 (sc/scene-list (frames 0) dims)))) "a square's two triangles sit together")))

(defn- square-of
  "The two triangles in `dl` of colour `colour` at view depth `depth`, which
  should be one point's square."
  [dl colour depth]
  (filterv (fn [it] (and (= colour (subvec it 7 11)) (< (abs (- depth (nth it 11))) 1e-6))) (tris dl)))

(deftest a-square-is-the-size-of-the-cubes-face
  ;; The first point is (2.3, -2.7, 4.0), the camera is at z = 12, so at frame 0
  ;; the point is at view depth 8. A cube of side 0.06 seen head on at that
  ;; depth covers the screen distance between the point and the point moved
  ;; 0.06 in x, since x is linear in the projection at a fixed depth.
  (let [dims (sc/dimensions {:screen [1206 2334]} measure)
        vp (s3/view-proj (sc/camera dims) (:viewport dims))
        [sx sy] (s3/project vp [2.3 -2.7 4.0])
        [sx2] (s3/project vp [2.36 -2.7 4.0])
        sq (square-of (sc/scene-list (frames 0) dims) [185 60 228 255] 8.0)
        [_ x1 y1 _ y2 x3 _] (first sq)]
    (is (= 2 (count sq)))
    (is (< 1.0 (- sx2 sx) 10.0) "a few pixels")
    (is (near? (- sx2 sx) (- x3 x1)) "as wide as the cube's face")
    (is (near? (- sx2 sx) (- y2 y1)) "and as high")
    (is (near? sx (/ (+ x1 x3) 2.0)) "centred on the point")
    (is (near? sy (/ (+ y1 y2) 2.0)))))

(deftest the-list-uses-the-rotation
  ;; Frame 300 is 90 degrees: rlRotatef about y takes (x, y, z) to
  ;; (x cos + z sin, y, -x sin + z cos) = (z, y, -x). The first point
  ;; (2.3, -2.7, 4.0) goes to (4.0, -2.7, -2.3), at view depth 12 + 2.3.
  (let [dims (sc/dimensions {:screen [1206 2334]} measure)
        vp (s3/view-proj (sc/camera dims) (:viewport dims))
        [sx sy] (s3/project vp [4.0 -2.7 -2.3])
        sq (square-of (sc/scene-list (frames 300) dims) [185 60 228 255] 14.3)
        [_ x1 y1 _ y2 x3 _] (first sq)]
    (is (= 2 (count sq)))
    (is (near-1e-6? sx (/ (+ x1 x3) 2.0)))
    (is (near-1e-6? sy (/ (+ y1 y2) 2.0)))
    (let [[ux uy] (s3/project vp [2.3 -2.7 4.0])]
      (is (> (+ (abs (- ux sx)) (abs (- uy sy))) 3.0) "which is not where the unturned point projects"))))

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
