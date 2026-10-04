(ns net.b12n.raylib-ios.scenes.wavecubes-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.wavecubes :as sc]
            [net.b12n.raylib-ios.soft3d :as s3]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(defn- near-vec? [a b] (and (= (count a) (count b)) (every? true? (map near? a b))))

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

(deftest the-wave-ripples-with-position-and-time
  (testing "frame 0, the corner column: sin 0 = 0 so the height is 0.6 + 2.4, the colour from sin 0, sin 2 and sin 4"
    ;; by hand: (int (+ 128 (* 127 (sin 0)))) = 128, (int (+ 128 (* 127 (sin 2.0)))) = 243,
    ;; (int (+ 128 (* 127 (sin 4.0)))) = 31 (the sine is negative, so the int truncates 31.88)
    (let [c (sc/column (frames 0) 0 0)]
      (is (near-vec? [-6.0 1.5 -6.0] (:pos c)))
      (is (near-vec? [1.0 3.0 1.0] (:size c)))
      (is (= [128 243 31 255] (:colour c)))))
  (testing "frame 50 is t = 3.0; column (3, 5)"
    ;; x = 3*1.5 - 6 = -1.5, z = 5*1.5 - 6 = 1.5 on the 9 by 9 grid
    ;; wave = sin(0.6*3 + 0.6*5 + 3.0) = sin 7.8 = 0.998543..., height 0.6 + 2.4 * 1.998543... = 5.3965...
    (let [c (sc/column (frames 50) 3 5)
          h 5.396504028899051]
      (is (near-vec? [-1.5 (/ h 2.0) 1.5] (:pos c)))
      (is (near-vec? [1.0 h 1.0] (:size c)))
      (is (= [27 185 81 255] (:colour c)))))
  (testing "9 by 9 columns, 81 (the original has 14 by 14, 196)"
    (is (= 81 (count (sc/columns (frames 0)))))
    (is (= 9 sc/n))
    (is (= 14 sc/original-n))
    (is (= 1.5 sc/spacing)))
  (testing "heights stay between 0.6 and 5.4 for all time"
    (let [hs (for [f (range 0 700 7) c (sc/columns (frames f))] (nth (:size c) 1))]
      (is (<= 0.6 (reduce min hs)))
      (is (>= 5.4 (reduce max hs)))
      (is (< (reduce min hs) 0.65) "and it gets close to the floor")
      (is (> (reduce max hs) 5.35) "and to the ceiling")))
  (testing "the wave travels: the next column over, 10 frames later, stands as this one did"
    (is (near? (nth (:size (sc/column (frames 0) 4 6)) 1)
               (nth (:size (sc/column (frames 10) 3 6)) 1)))
    (is (not (near? (nth (:size (sc/column (frames 0) 4 6)) 1)
                    (nth (:size (sc/column (frames 0) 3 6)) 1))))
    (is (pos? (nth (:size (sc/column (frames 0) 4 6)) 1)))))

(deftest the-camera-orbits-slowly
  (testing "frame 0: span 13.5 for 9 columns of 1.5, so radius 17.55 on x, height 12.15, looking at (0, 1.5, 0)"
    (let [c (sc/camera (frames 0) (sc/dimensions {:screen [800 450]} measure))]
      (is (near-vec? [17.55 12.15 0.0] (:position c)))
      (is (near-vec? [0.0 1.5 0.0] (:target c)))
      (is (= 45.0 (:fovy c)) "the original's fovy is kept in a field as wide as the window")))
  (testing "frame 100 is 1.2 radians round"
    (let [c (sc/camera (frames 100) (sc/dimensions {:screen [800 450]} measure))]
      (is (near-vec? [6.359378591065623 12.15 16.357285958724823] (:position c)) "17.55 cos 1.2, 17.55 sin 1.2")))
  (testing "it turns 0.012 radians a frame"
    (is (not= (:position (sc/camera (frames 1) (sc/dimensions {:screen [800 450]} measure)))
              (:position (sc/camera (frames 0) (sc/dimensions {:screen [800 450]} measure)))))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                dims (sc/dimensions metrics measure)
                dl (sc/scene-list (frames 0) dims)
                faces (tris dl)]]
    (testing (str screen)
      (is (<= 460 (count faces) 486) "from the camera above every column shows its top and two sides, 6 triangles each, or rarely one side: 81 columns")
      (is (= (count faces) (count dl)) "no lines")
      (is (every? (fn [it] (= 255 (nth it 10))) faces) "faces are opaque, as cube! makes them")))
  (testing "on the phone every corner of every face stays inside the field, through a whole orbit"
    (let [dims (sc/dimensions {:screen [1206 2334]} measure)]
      (is (every? (fn [n] (inside? (:viewport dims) (sc/scene-list (frames n) dims)))
                  (range 0 530 20))))))

(deftest the-columns-match-soft3d-cube-and-wind-like-rlgl-keeps
  (doseq [screen [[1206 2334] [800 450]]
          f [0 77 250 400]
          :let [dims (sc/dimensions {:screen screen} measure)
                state (frames f)
                vp (s3/view-proj (sc/camera state dims) (:viewport dims))
                reference (reduce (fn [dl {:keys [pos size colour]}] (s3/cube dl vp nil pos size colour))
                                  [] (sc/columns state))
                mine (sc/scene-list state dims)
                key (fn [it] (mapv (fn [v] (if (number? v) (Math/round (* 1000.0 v)) v)) it))]]
    (testing (str screen " frame " f)
      (is (= (count reference) (count mine)))
      (is (= (set (map key reference)) (set (map key mine))) "the same triangles net.b12n.raylib-ios.soft3d/cube makes, shaded and wound the same")
      (is (every? (fn [[_ x1 y1 x2 y2 x3 y3]]
                    (neg? (- (* (- x2 x1) (- y3 y1)) (* (- y2 y1) (- x3 x1)))))
                  mine)))))

(deftest the-columns-are-soft3d-cubes-in-axis-order
  (doseq [screen [[1206 2334] [800 450]]
          f [0 77 250 400]
          :let [dims (sc/dimensions {:screen screen} measure)
                state (frames f)
                cam (sc/camera state dims)
                vp (s3/view-proj cam (:viewport dims))
                [camx _ camz] (:position cam)
                want (reduce (fn [dl ix]
                               (reduce (fn [dl iz]
                                         (let [{:keys [pos size colour]} (sc/column state ix iz)]
                                           (s3/cube dl vp nil pos size colour)))
                                       dl (sc/axis-order camz)))
                             [] (sc/axis-order camx))]]
    (testing (str screen " frame " f)
      (is (= want (sc/scene-list state dims)) "item for item: net.b12n.raylib-ios.soft3d/cube draws every column, x outside z, each farthest first"))))

(deftest columns-paint-far-to-near
  (testing "an axis is taken farthest first from the camera"
    (is (= [0 8 1 7 2 6 3 5 4] (sc/axis-order 0.0)) "from the middle the two ends tie, then work in")
    (is (= (range 9) (sort (sc/axis-order 17.55))) "every cell once")
    (is (= [0 1 2 3 4 5 6 7 8] (sc/axis-order 17.55)) "from far +x the smallest x is farthest")
    (is (= [8 7 6 5 4 3 2 1 0] (sc/axis-order -17.55))))
  (testing "the first column drawn is a far one and the last a near one"
    (let [dims (sc/dimensions {:screen [1206 2334]} measure)
          dl (sc/scene-list (frames 0) dims)
          depth (fn [it] (nth it 11))]
      (is (> (depth (first dl)) (depth (last dl)))))))

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
