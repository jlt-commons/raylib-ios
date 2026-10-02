(ns raylib.soft3d-test
  "Hand-computed cases from raylib 6.0's C: rcore.c BeginMode3D,
  GetWorldToScreenEx and GetScreenToWorldRayEx, raymath.h MatrixLookAt,
  MatrixPerspective, MatrixOrtho and rlgl.h rlRotatef, rmodels.c DrawCubeWires,
  DrawGrid and GetRayCollisionBox, and raylib-jlt's models.clj cube! shades."
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.soft3d :as s3]))

(defn- close?
  ([a b] (close? a b 1e-6))
  ([a b eps]
   (and (= (count a) (count b))
        (every? true? (map (fn [x y] (< (Math/abs (- (double x) (double y))) eps)) a b)))))

(defn- cam [position target fovy projection]
  {:position position
   :target target
   :up [0.0 1.0 0.0]
   :fovy fovy
   :projection projection})

(defn- tris [dl] (filterv (fn [it] (= :tri (nth it 0))) dl))
(defn- lines-of [dl] (filterv (fn [it] (= :line (nth it 0))) dl))

(defn- cross
  "The y-down screen cross product of a :tri's first two edges, the sign
  raylib.host/draw-triangle tests."
  [[_ x1 y1 x2 y2 x3 y3]]
  (- (* (- x2 x1) (- y3 y1)) (* (- y2 y1) (- x3 x1))))

(defn- colour-of [it] (subvec it 7 11))

(def ^:private top-down
  "Looking straight down at the origin from y = 10, orthographic, top 5. With
  up [0 0 -1], MatrixLookAt gives right = +x and screen-up = -z, so on a
  100 by 100 viewport world (x, 0, z) lands at (50 + 10x, 50 + 10z)."
  {:position [0.0 10.0 0.0]
   :target [0.0 0.0 0.0]
   :up [0.0 0.0 -1.0]
   :fovy 10.0
   :projection :orthographic})

(deftest perspective-matches-raylib
  (testing "fovy 90 from z = 10 on 200 by 100: MatrixPerspective m0 = 1/aspect = 0.5, m5 = 1"
    (let [vp (s3/view-proj (cam [0 0 10] [0 0 0] 90.0 :perspective) [200 100])]
      ;; view (1 1 -10), clip x 0.5 y 1 w 10, ndc (0.05 0.1)
      (is (close? [105.0 45.0 10.0] (s3/project vp [1 1 0])))
      (is (close? [105.0 45.0] (s3/world->screen vp [1 1 0])))))
  (testing "MatrixLookAt from +x: right is -z, so +z lands left of centre"
    (let [vp (s3/view-proj (cam [5 0 0] [0 0 0] 90.0 :perspective) [100 100])]
      ;; view (-1 0 -5), ndc x -0.2
      (is (close? [40.0 50.0 5.0] (s3/project vp [0 0 1])))))
  (testing "fovy 45 uses tan(22.5 deg)"
    (let [vp (s3/view-proj (cam [0 0 10] [0 0 0] 45.0 :perspective) [100 100])
          ndc (/ 1.0 (* 10.0 (Math/tan (/ Math/PI 8.0))))]
      (is (close? [(* 50.0 (+ 1.0 ndc)) 50.0 10.0] (s3/project vp [1 0 0])))))
  (testing "RL_CULL_DISTANCE_NEAR is 0.05: a point 0.051 in front is kept, 0.04 is not"
    (let [vp (s3/view-proj (cam [0 0 10] [0 0 0] 90.0 :perspective) [100 100])]
      (is (some? (s3/project vp [0 0 9.949])))
      (is (nil? (s3/project vp [0 0 9.96])))
      (is (nil? (s3/project vp [0 0 11])))))
  (testing "a viewport [x y w h] offsets the screen"
    (let [vp (s3/view-proj (cam [0 0 10] [0 0 0] 90.0 :perspective) [30 40 200 100])]
      (is (close? [135.0 85.0 10.0] (s3/project vp [1 1 0])))
      (is (close? [135.0 85.0] (s3/world->screen vp [1 1 0]))))))

(deftest orthographic-matches-raylib
  (testing "top = fovy/2 = 5, right = top * aspect = 10 on 200 by 100"
    (let [vp (s3/view-proj (cam [0 0 10] [0 0 0] 10.0 :orthographic) [200 100])]
      (is (close? [150.0 25.0 7.0] (s3/project vp [5 2.5 3])))
      (is (close? [150.0 25.0] (s3/world->screen vp [5 2.5 3])))))
  (testing "the top-down camera"
    (let [vp (s3/view-proj top-down [100 100])]
      (is (close? [70.0 20.0 10.0] (s3/project vp [2 0 -3])))))
  (testing "the near plane still applies"
    (let [vp (s3/view-proj (cam [0 0 10] [0 0 0] 10.0 :orthographic) [200 100])]
      (is (nil? (s3/project vp [0 0 9.96]))))))

(deftest world-to-screen-then-ray-passes-through-the-point
  (doseq [projection [:perspective :orthographic]
          viewport [[390 600] [10 50 390 600]]
          p [[0.3 0.7 -0.2] [1.0 0.0 1.0] [-2.0 1.0 0.5]]]
    (let [camera (cam [4.0 3.0 6.0] [0.0 0.5 0.0] 45.0 projection)
          vp (s3/view-proj camera viewport)
          {:keys [position direction]} (s3/screen->ray vp (s3/world->screen vp p))
          d (mapv - p position)
          [dx dy dz] d
          [ux uy uz] direction
          off [(- (* dy uz) (* dz uy)) (- (* dz ux) (* dx uz)) (- (* dx uy) (* dy ux))]]
      (testing (str projection " " viewport " " p)
        (is (close? [1.0] [(Math/sqrt (reduce + (map * direction direction)))]))
        (is (close? [0.0 0.0 0.0] off 1e-6))
        (is (pos? (reduce + (map * d direction))))
        (when (= projection :perspective)
          (is (= [4.0 3.0 6.0] (mapv double position))))))))

(deftest ray-box-hits-and-misses-as-raylib
  (let [lo [-1.0 -1.0 -1.0] hi [1.0 1.0 1.0]]
    (testing "straight down +z onto the -z face"
      (let [{:keys [hit? distance point normal]}
            (s3/ray-box {:position [0 0 -5]
                         :direction [0 0 1]} lo hi)]
        (is (true? hit?))
        (is (close? [4.0] [distance]))
        (is (close? [0.0 0.0 -1.0] point))
        (is (close? [0.0 0.0 -1.0] normal))))
    (testing "down -y onto the top face, off centre"
      (let [{:keys [hit? distance point normal]}
            (s3/ray-box {:position [0.5 5 0.25]
                         :direction [0 -1 0]} lo hi)]
        (is (true? hit?))
        (is (close? [4.0] [distance]))
        (is (close? [0.5 1.0 0.25] point))
        (is (close? [0.0 1.0 0.0] normal))))
    (testing "beside the box"
      (is (false? (:hit? (s3/ray-box {:position [3 0 -5]
                                      :direction [0 0 1]} lo hi)))))
    (testing "pointing away"
      (is (false? (:hit? (s3/ray-box {:position [0 0 -5]
                                      :direction [0 0 -1]} lo hi)))))
    (testing "from inside, raylib reverses the ray: distance 1, point (1 0 0), normal (-1 0 0)"
      (let [{:keys [hit? distance point normal]}
            (s3/ray-box {:position [0 0 0]
                         :direction [1 0 0]} lo hi)]
        (is (true? hit?))
        (is (close? [1.0] [distance]))
        (is (close? [1.0 0.0 0.0] point))
        (is (close? [-1.0 0.0 0.0] normal))))))

(def ^:private around
  "Cameras on every axis and in every octant, both projections."
  (for [projection [:perspective :orthographic]
        position [[0 0 10] [0 0 -10] [10 0 0] [-10 0 0] [0.1 10 0] [0.1 -10 0]
                  [6 6 6] [-6 6 6] [6 -6 6] [-6 -6 6]
                  [6 6 -6] [-6 6 -6] [6 -6 -6] [-6 -6 -6]]]
    (cam position [0 0 0] 45.0 projection)))

(deftest every-tri-has-front-winding
  (doseq [camera around
          xf [nil (s3/rotate-axis 30.0 1 0 0)
              (s3/compose (s3/translate 0.5 0 0) (s3/rotate-axis 70.0 0.3 1 0))]]
    (let [vp (s3/view-proj camera [390 600])
          ts (tris (s3/finish (s3/cube [] vp xf [0 0 0] 2.0 [200 100 50 255])))]
      (testing (str camera)
        (is (seq ts))
        (is (every? neg? (map cross ts)))))))

(deftest back-faces-are-dropped
  (testing "head on, only the front face: two triangles, shade 1.0"
    (let [vp (s3/view-proj (cam [0 0 10] [0 0 0] 45.0 :perspective) [390 600])
          ts (tris (s3/finish (s3/cube [] vp nil [0 0 0] 2.0 [200 100 50 255])))]
      (is (= 2 (count ts)))
      (is (= #{[200 100 50 255]} (set (map colour-of ts))))))
  (testing "from an octant, three faces: six triangles, right top front"
    (let [vp (s3/view-proj (cam [6 6 6] [0 0 0] 45.0 :perspective) [390 600])
          ts (tris (s3/finish (s3/cube [] vp nil [0 0 0] 2.0 [200 100 50 255])))]
      (is (= 6 (count ts)))
      (is (= {[200 100 50 255] 4
              [170 85 42 255] 2}
             (frequencies (map colour-of ts)))))))

(deftest faces-sort-far-to-near
  (let [vp (s3/view-proj (cam [3 4 10] [0 0 0] 45.0 :perspective) [390 600])
        dl (-> []
               (s3/cube-wires vp nil [0 0 0] 2.0 [0 0 0 255])
               (s3/cube vp nil [0 0 0] 2.0 [255 0 0 255])
               (s3/cube vp nil [0 0 -6] 2.0 [0 0 255 255])
               (s3/grid vp 10 1.0))
        out (s3/finish dl)
        kinds (mapv (fn [it] (nth it 0)) out)
        ts (tris out)
        reds (fn [it] (pos? (nth it 7)))]
    (testing "grid lines, then faces, then the wires"
      (is (= (concat (repeat 22 :line) (repeat (count ts) :tri) (repeat 12 :line)) kinds)))
    (testing "the far blue cube's faces all come before the near red one's"
      (is (= 12 (count ts)))
      (is (= (concat (repeat 6 false) (repeat 6 true)) (map reds ts))))
    (testing "mean depth never increases"
      (is (apply >= (map (fn [it] (nth it 11)) ts))))))

(deftest behind-the-camera-is-dropped-or-clipped
  (let [vp (s3/view-proj (cam [0 0 10] [0 0 0] 90.0 :perspective) [100 100])]
    (testing "a cube behind the camera draws nothing"
      (is (empty? (s3/finish (s3/cube [] vp nil [0 0 20] 2.0 [255 0 0 255])))))
    (testing "a cube round the camera keeps no face with a corner behind the near plane"
      (let [ts (tris (s3/finish (s3/cube [] vp nil [0 0 9] 3.0 [255 0 0 255])))]
        (is (every? neg? (map cross ts)))
        (is (every? (fn [it] (every? (fn [v] (< (Math/abs (double v)) 1e6)) (subvec it 1 7))) ts))))
    (testing "a line through the near plane is clipped to it, at view depth 0.05"
      ;; [1 0 0] is ndc 0.1 -> 55; the near-plane point [1 0 9.95] is ndc 20 -> 1050
      (let [ls (lines-of (s3/finish (s3/lines [] vp nil [[[1 0 0] [1 0 20] [9 9 9 255]]])))]
        (is (= 1 (count ls)))
        (is (close? [55.0 50.0 1050.0 50.0] (subvec (first ls) 1 5) 1e-3))
        (is (= [9 9 9 255] (subvec (first ls) 5 9)))))
    (testing "the same line given the other way round"
      (let [ls (lines-of (s3/finish (s3/lines [] vp nil [[[1 0 20] [1 0 0] [9 9 9 255]]])))]
        (is (close? [1050.0 50.0 55.0 50.0] (subvec (first ls) 1 5) 1e-3))))
    (testing "a line wholly behind is dropped"
      (is (empty? (s3/finish (s3/lines [] vp nil [[[1 0 15] [1 0 20] [9 9 9 255]]])))))))

(deftest cube-shades-match-models-clj
  ;; models.clj shade-color: (int (* f c)) on r g b, alpha 255
  (doseq [[position up shade] [[[0 0 10] [0 1 0] [200 100 50 255]]   ; front +z 1.0
                               [[0 0 -10] [0 1 0] [100 50 25 255]]   ; back -z 0.5
                               [[-10 0 0] [0 1 0] [140 70 35 255]]   ; left -x 0.7
                               [[10 0 0] [0 1 0] [170 85 42 255]]    ; right +x 0.85
                               [[0 10 0] [0 0 -1] [200 100 50 255]]  ; top +y 1.0
                               [[0 -10 0] [0 0 1] [80 40 20 255]]]]  ; bottom -y 0.4
    (let [vp (s3/view-proj {:position position
                            :target [0 0 0]
                            :up up
                            :fovy 45.0
                            :projection :perspective} [390 600])
          ts (tris (s3/finish (s3/cube [] vp nil [0 0 0] [2 2 2] [200 100 50 128])))]
      (testing (str position)
        (is (= 2 (count ts)))
        (is (= #{shade} (set (map colour-of ts))))))))

(deftest grid-matches-drawgrid
  (let [ls (lines-of (s3/finish (s3/grid [] (s3/view-proj top-down [100 100]) 10 1.0)))
        ends (map (fn [it] (mapv (fn [v] (Math/round (double v))) (subvec it 1 5))) ls)]
    (testing "slices 10: i from -5 to 5, two lines each"
      (is (= 22 (count ls))))
    (testing "the i = 0 pair is rlColor3f(0.5) = 127, the rest rlColor3f(0.75) = 191"
      (is (= {[127 127 127 255] 2
              [191 191 191 255] 20}
             (frequencies (map (fn [it] (subvec it 5 9)) ls))))
      (is (= [[50 0 50 100] [0 50 100 50]]
             (map (fn [it] (mapv (fn [v] (Math/round (double v))) (subvec it 1 5)))
                  (filter (fn [it] (= 127 (nth it 5))) ls)))))
    (testing "DrawGrid's vertex order: (i*s, 0, -h*s)->(i*s, 0, h*s), then (-h*s, 0, i*s)->(h*s, 0, i*s)"
      (is (= [[0 0 0 100] [0 0 100 0] [10 0 10 100] [0 10 100 10]] (take 4 ends))))))

(deftest cube-wires-matches-drawcubewires
  ;; Through top-down, the twelve edges of a 2 x 4 x 6 box at (1 0 0): x 0..2,
  ;; z -3..3 -> screen x 50..70, y 20..80. Only the x and z of each end show.
  (let [ls (lines-of (s3/finish (s3/cube-wires [] (s3/view-proj top-down [100 100]) nil
                                               [1 0 0] [2 4 6] [1 2 3 4])))
        ends (mapv (fn [it] (mapv (fn [v] (Math/round (double v))) (subvec it 1 5))) ls)]
    (is (= 12 (count ls)))
    (is (= #{[1 2 3 4]} (set (map (fn [it] (subvec it 5 9)) ls))))
    (is (= [[50 80 70 80] [70 80 70 80] [70 80 50 80] [50 80 50 80]
            [50 20 70 20] [70 20 70 20] [70 20 50 20] [50 20 50 20]
            [50 80 50 20] [70 80 70 20] [50 80 50 20] [70 80 70 20]]
           ends))))

(deftest transforms-compose-as-rlgl
  ;; rlRotatef(90, 0 1 0) takes (1 0 0) to (0 0 -1); rlTranslatef(2 0 0) then
  ;; rlRotatef applies the rotation first, so (1 0 0) ends at (2 0 -1).
  (let [vp (s3/view-proj top-down [100 100])
        end (fn [xf] (let [[it] (lines-of (s3/finish (s3/lines [] vp xf [[[0 0 0] [1 0 0] [0 0 0 255]]])))]
                       (subvec it 3 5)))]
    (is (close? [60.0 50.0] (end nil)))
    (is (close? [50.0 40.0] (end (s3/rotate-axis 90.0 0 1 0))))
    (is (close? [70.0 40.0] (end (s3/compose (s3/translate 2 0 0) (s3/rotate-axis 90.0 0 1 0)))))
    (is (close? [60.0 40.0] (end (s3/compose (s3/rotate-axis 90.0 0 1 0) (s3/translate 0 0 1)))))
    (testing "rotate-axis normalises its axis, as rlRotatef"
      (is (close? [50.0 40.0] (end (s3/rotate-axis 90.0 0 5 0)))))))
