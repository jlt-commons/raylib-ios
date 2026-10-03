(ns raylib.soft3d-test
  "Hand-computed cases from raylib 6.0's C: rcore.c BeginMode3D,
  GetWorldToScreenEx and GetScreenToWorldRayEx, raymath.h MatrixLookAt,
  MatrixPerspective, MatrixOrtho and rlgl.h rlRotatef, rmodels.c DrawCubeWires,
  DrawGrid and GetRayCollisionBox, and raylib-jlt's models.clj cube! shades."
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
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
    (testing "a cube round the camera draws nothing: its one whole face, -z, faces away"
      (is (empty? (s3/finish (s3/cube [] vp nil [0 0 9] 3.0 [255 0 0 255])))))
    (testing "a face with a corner behind the near plane goes, its front-facing neighbour stays"
      ;; From (3 0 3) looking along (1 0 -1.5), the +z face's far edge x = -1
      ;; is 0.55 behind the camera while the +x face is wholly in front, so
      ;; only +x's two triangles survive, shaded 0.85.
      (let [vp (s3/view-proj {:position [3 0 3]
                              :target [4 0 1.5]
                              :up [0 1 0]
                              :fovy 90.0
                              :projection :perspective} [100 100])
            ts (tris (s3/finish (s3/cube [] vp nil [0 0 0] 2.0 [200 100 50 255])))]
        (is (= 2 (count ts)))
        (is (= #{[170 85 42 255]} (set (map colour-of ts))))
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

(deftest a-flat-cube-is-drawcube
  ;; rmodels.c DrawCube: one rlColor4ub(color.r, color.g, color.b, color.a) for
  ;; every face, no shading, alpha kept
  (doseq [[position up] [[[0 0 10] [0 1 0]] [[0 0 -10] [0 1 0]] [[-10 0 0] [0 1 0]]
                         [[10 0 0] [0 1 0]] [[0 10 0] [0 0 -1]] [[0 -10 0] [0 0 1]]
                         [[6 6 6] [0 1 0]] [[-6 -6 -6] [0 1 0]]]]
    (let [vp (s3/view-proj {:position position
                            :target [0 0 0]
                            :up up
                            :fovy 45.0
                            :projection :perspective} [390 600])
          flat (tris (s3/finish (s3/cube [] vp nil [0 0 0] 2.0 [200 100 50 128] {:shade :flat})))
          shaded (tris (s3/finish (s3/cube [] vp nil [0 0 0] 2.0 [200 100 50 128])))]
      (testing (str position)
        (is (= (count shaded) (count flat)))
        (is (= #{[200 100 50 128]} (set (map colour-of flat))))
        (is (every? neg? (map cross flat)))))))

(defn- all-wire-ends
  "Rounded screen ends of the DrawCubeWires edges of a 2-cube at the origin."
  [vp opts]
  (mapv (fn [it] (mapv (fn [v] (Math/round (double v))) (subvec it 1 5)))
        (lines-of (s3/finish (s3/cube-wires [] vp nil [0 0 0] 2.0 [0 0 0 255] opts)))))

(deftest wires-hide-their-back-edges-on-request
  (doseq [projection [:perspective :orthographic]]
    (let [vp (s3/view-proj (cam [6 6 6] [0 0 0] 45.0 projection) [390 600])
          whole (all-wire-ends vp {})
          shown (all-wire-ends vp {:hide-back? true})]
      (testing (str projection " from a corner, the 3 edges meeting the far corner (-1 -1 -1) go")
        (is (= 12 (count whole)))
        ;; DrawCubeWires order: 0-1 is edge 4, 2-0 edge 7, 4-0 edge 10
        (is (= (vec (keep-indexed (fn [i e] (when-not (#{4 7 10} i) e)) whole)) shown)))))
  (testing "head on, only the front square stays"
    (let [vp (s3/view-proj (cam [0 0 10] [0 0 0] 45.0 :perspective) [390 600])]
      (is (= (subvec (all-wire-ends vp {}) 0 4) (all-wire-ends vp {:hide-back? true})))))
  (testing "the default keeps all twelve, as DrawCubeWires alone"
    (let [vp (s3/view-proj (cam [6 6 6] [0 0 0] 45.0 :perspective) [390 600])]
      (is (= 12 (count (lines-of (s3/cube-wires [] vp nil [0 0 0] 2.0 [0 0 0 255]))))))))

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

;; --- fit-camera -------------------------------------------------------------

(def ^:private orig-aspect (/ 800.0 450.0))

(defn- fit-case
  "A camera on +z looking at the origin from 10, so right is +x and the world
  x at the target depth maps linearly to the screen."
  [fovy projection]
  {:position [0.0 0.0 10.0]
   :target [0.0 0.0 0.0]
   :up [0.0 1.0 0.0]
   :fovy fovy
   :projection projection})

(defn- orig-half-width
  "Half the original view's width at the target depth, in world units."
  [{:keys [fovy projection]}]
  (* orig-aspect
     (if (= projection :orthographic)
       (/ fovy 2.0)
       (* 10.0 (Math/tan (Math/toRadians (/ fovy 2.0)))))))

(deftest fit-camera-keeps-a-wide-field-unchanged
  (doseq [projection [:perspective :orthographic]
          fovy [45.0 12.0]
          field [orig-aspect 2.0 3.5]
          :let [c (fit-case fovy projection)]]
    (is (= c (s3/fit-camera c orig-aspect field)) (str projection " " fovy " " field))))

(deftest fit-camera-preserves-the-originals-horizontal-coverage-in-portrait
  (doseq [projection [:perspective :orthographic]
          fovy [45.0 12.0]
          [w h] [[1206 2142] [450 648] [600 600]]
          :let [c (fit-case fovy projection)
                fitted (s3/fit-camera c orig-aspect (/ (double w) h))
                vp (s3/view-proj fitted [w h])
                hw (orig-half-width c)
                [lx ly] (s3/project vp [(- hw) 0.0 0.0])
                [rx ry] (s3/project vp [hw 0.0 0.0])]]
    (testing (str projection " " fovy " " w "x" h)
      (is (close? [0.0 (/ h 2.0)] [lx ly] 1e-6) "the original's left edge lands on the field's left edge")
      (is (close? [(double w) (/ h 2.0)] [rx ry] 1e-6) "and its right edge on the right")
      (is (> (:fovy fitted) fovy) "the vertical extent grew to hold it")
      (is (= (dissoc c :fovy) (dissoc fitted :fovy)) "nothing else changed"))))

;; --- field --------------------------------------------------------------------

(deftest field-starts-below-back-and-runs-to-the-bottom
  (doseq [[w h :as screen] [[1206 2334] [2334 1206] [800 450] [450 800]]
          :let [[_ back-y _ back-h] gesture/back-region
                back-bottom (+ back-y back-h)
                {:keys [text-y size pad aspect]
                 [vx vy vw vh] :viewport} (s3/field {:screen screen})]]
    (testing (str screen)
      (is (>= text-y back-bottom) "the caption line is below Back")
      (is (>= vy (+ text-y size)) "and the field below the caption")
      (is (> vy back-bottom))
      (is (= [0.0 (double w)] [vx vw]) "full width")
      (is (close? [(double h)] [(+ vy vh)]) "to the bottom")
      (is (close? [(/ vw vh)] [aspect]))
      (is (pos? pad)))))

(deftest field-follows-back-region
  (with-redefs [gesture/back-region [0 0 400 300]]
    (is (>= (:text-y (s3/field {:screen [1206 2334]})) 300) "a taller Back pushes the field down")))

(deftest field-cuts-the-text-size-to-fit
  (let [roomy (s3/field {:screen [450 800]})
        cut (s3/field {:screen [450 800]} 3000.0)]
    (is (= (:size roomy) (:size (s3/field {:screen [450 800]} 10.0))) "a short caption keeps the size")
    (is (< (:size cut) (:size roomy)))
    (is (<= (* 3000.0 (/ (:size cut) 100.0)) (* 0.92 450)) "the widest caption fits 0.92 of the width")))

;; --- sphere and plane -------------------------------------------------------

(def ^:private front-ortho
  "Orthographic from +z, top 5, on 100 by 100: world (x, y, z) lands at
  (50 + 10x, 50 - 10y)."
  {:position [0.0 0.0 10.0]
   :target [0.0 0.0 0.0]
   :up [0.0 1.0 0.0]
   :fovy 10.0
   :projection :orthographic})

(defn- rounded-verts
  "A :tri's three screen vertices, rounded."
  [it]
  (mapv (fn [v] (Math/round (double v))) (subvec it 1 7)))

(deftest sphere-bands-match-models-clj
  ;; models.clj sphere!: brightness = 0.45 + 0.55 * (y0 + y1 + 2) / 4 per band,
  ;; shade-color = (int (* f c)) on r g b, alpha 255. y = sin(lat), lat running
  ;; from -pi/2 to pi/2 over the rings.
  (let [rings 12
        expected (vec (for [i (range rings)
                            :let [y0 (Math/sin (- (* Math/PI (/ (double i) rings)) (/ Math/PI 2.0)))
                                  y1 (Math/sin (- (* Math/PI (/ (double (inc i)) rings)) (/ Math/PI 2.0)))
                                  f (+ 0.45 (* 0.55 (/ (+ y0 y1 2.0) 4.0)))]]
                        [(int (* f 200)) (int (* f 100)) (int (* f 50)) 255]))
        vp (s3/view-proj front-ortho [100 100])
        ts (tris (s3/finish (s3/sphere [] vp nil [0 0 0] 2.0 [200 100 50 77])))]
    (testing "the first and last bands, worked by hand"
      ;; first: y0 = -1, y1 = sin(-75 deg) = -0.9659, f = 0.45 + 0.55 * 0.0341 / 4 = 0.45469
      ;; last: y0 = 0.9659, y1 = 1, f = 0.45 + 0.55 * 3.9659 / 4 = 0.99532
      (is (= [90 45 22 255] (first expected)))
      (is (= [199 99 49 255] (last expected))))
    (testing "every band's shade shows, and nothing else, alpha 255"
      (is (= (set expected) (set (map colour-of ts)))))
    (testing "bands get brighter toward +y"
      (is (apply < (map first expected))))))

(deftest sphere-bands-small-case-by-hand
  ;; rings 2: bands y -1..0 and 0..1, brightness 0.45 + 0.55 * 1/4 = 0.5875 and
  ;; 0.45 + 0.55 * 3/4 = 0.8625. [200 100 50] -> [117 58 29] and [172 86 43].
  (let [vp (s3/view-proj front-ortho [100 100])
        ts (tris (s3/finish (s3/sphere [] vp nil [0 0 0] 2.0 [200 100 50 255] {:rings 2
                                                                               :slices 4})))]
    (is (= #{[117 58 29 255] [172 86 43 255]} (set (map colour-of ts))))))

(deftest sphere-tessellation-matches
  ;; rings 2, slices 4, radius 2 at the origin. Quad (i, j) is [p00 p10 p11 p01],
  ;; p<lat><lon> with lat i/i+1 and lon j/j+1, drawn as (p00 p10 p11) and
  ;; (p00 p11 p01), as quad-3f splits it.
  (testing "from above, the northern band's four quads reach the pole"
    ;; r0 = 1 at the equator, r1 = cos(pi/2) = 0 at the pole (50 50). Lon 0 is
    ;; +x, lon 90 is +z: equator points (70 50), (50 70), (30 50), (50 30).
    (let [vp (s3/view-proj top-down [100 100])
          ts (tris (s3/finish (s3/sphere [] vp nil [0 0 0] 2.0 [255 255 255 255] {:rings 2
                                                                                  :slices 4})))
          vs (set (map rounded-verts ts))]
      (is (contains? vs [70 50 50 50 50 70]))
      (is (contains? vs [50 70 50 50 30 50]))
      (is (contains? vs [30 50 50 50 50 30]))
      (is (contains? vs [50 30 50 50 70 50]))))
  (testing "from the front, the southern band's first quad: p00 (0 -2 0), p10 (2 0 0), p11 (0 0 2)"
    (let [vp (s3/view-proj front-ortho [100 100])
          ts (tris (s3/finish (s3/sphere [] vp nil [0 0 0] 2.0 [255 255 255 255] {:rings 2
                                                                                  :slices 4})))
          vs (set (map rounded-verts ts))]
      (is (contains? vs [50 70 70 50 50 50]))))
  (testing "the centre and radius move it: centre (1 0 2), radius 1, from above"
    (let [vp (s3/view-proj top-down [100 100])
          ts (tris (s3/finish (s3/sphere [] vp nil [1 0 2] 1.0 [255 255 255 255] {:rings 2
                                                                                  :slices 4})))
          vs (set (map rounded-verts ts))]
      ;; equator (2 0 2) -> (70 70), pole (1 1 2) -> (60 70), (1 0 3) -> (60 80)
      (is (contains? vs [70 70 60 70 60 80])))))

(deftest sphere-front-winding
  (doseq [camera around
          xf [nil (s3/rotate-axis 30.0 1 0 0)
              (s3/compose (s3/translate 0.5 0 0) (s3/rotate-axis 70.0 0.3 1 0))]]
    (let [vp (s3/view-proj camera [390 600])
          ts (tris (s3/finish (s3/sphere [] vp xf [0 0 0] 2.0 [200 100 50 255])))
          solid (filterv (fn [it] (> (Math/abs (double (cross it))) 1e-6)) ts)]
      (testing (str camera)
        (is (seq ts))
        (is (every? neg? (map cross solid)))
        (is (every? (fn [it] (= 255 (nth it 10))) ts)))))
  (testing "the defaults are 12 rings and 16 slices: 192 quads, so up to 384 triangles"
    (let [vp (s3/view-proj (cam [0 0 10] [0 0 0] 45.0 :perspective) [390 600])
          all (s3/sphere [] vp nil [0 0 0] 2.0 [200 100 50 255])
          small (s3/sphere [] vp nil [0 0 0] 2.0 [200 100 50 255] {:rings 2
                                                                   :slices 4})]
      (is (< 100 (count all) 385))
      (is (< (count small) 9)))))

(deftest sphere-behind-the-camera-is-dropped
  (let [vp (s3/view-proj (cam [0 0 10] [0 0 0] 90.0 :perspective) [100 100])]
    (is (empty? (s3/sphere [] vp nil [0 0 20] 2.0 [255 0 0 255])))))

(deftest plane-matches-drawplane
  ;; DrawPlane: translate to the centre, scale (sx 1 sz), quad (-.5 -.5),
  ;; (-.5 .5), (.5 .5), (.5 -.5) in x z. Centre (1 0 2), size 4 by 6: x -1..3,
  ;; z -1..5 -> screen x 40..80, y 40..100 from above.
  (let [vp (s3/view-proj top-down [100 100])
        ts (tris (s3/finish (s3/plane [] vp nil [1 0 2] [4 6] [10 20 30 128])))]
    (is (= 2 (count ts)))
    (is (= [[40 40 40 100 80 100] [40 40 80 100 80 40]]
           (mapv rounded-verts (sort-by (fn [it] (nth it 5)) ts))))
    (is (= #{[10 20 30 128]} (set (map colour-of ts))) "flat, alpha kept")
    (is (every? neg? (map cross ts)))))

(deftest plane-seen-from-below-is-dropped
  ;; the normal is +y: from y = -10 it is a back face
  (let [below (s3/view-proj {:position [0.0 -10.0 0.0]
                             :target [0.0 0.0 0.0]
                             :up [0.0 0.0 1.0]
                             :fovy 10.0
                             :projection :orthographic} [100 100])
        above (s3/view-proj top-down [100 100])]
    (is (empty? (s3/plane [] below nil [0 0 0] [4 4] [1 2 3 255])))
    (is (= 2 (count (s3/plane [] above nil [0 0 0] [4 4] [1 2 3 255]))))
    (testing "and a transform that flips it over flips the verdict"
      (is (= 2 (count (s3/plane [] below (s3/rotate-axis 180.0 1 0 0) [0 0 0] [4 4] [1 2 3 255]))))
      (is (empty? (s3/plane [] above (s3/rotate-axis 180.0 1 0 0) [0 0 0] [4 4] [1 2 3 255]))))))

;; --- cube against its earlier body -------------------------------------------

(defn reference-cube
  "`raylib.soft3d/cube` as it was at 926cb1c, before it took the box emitter
  from `raylib.scenes.wavecubes`: project all eight corners, then test both
  triangles of all six faces by screen sign. Kept here, test only, as the
  reference the new body must reproduce item for item. It calls the private
  helpers it called then, which `cube`'s new body does not change."
  ([dl vp xf pos size colour] (reference-cube dl vp xf pos size colour {}))
  ([dl vp xf [cx cy cz] size [cr cg cb ca] {:keys [shade]}]
   (let [frame #'s3/frame
         project* #'s3/project*
         tri #'s3/tri
         faces @#'s3/faces
         [m d] (frame vp xf)
         {ox :x
          oy :y
          w :w
          h :h} vp
         [sx sy sz] (#'s3/sizes size)
         x0 (- cx (/ sx 2.0)) x1 (+ cx (/ sx 2.0))
         y0 (- cy (/ sy 2.0)) y1 (+ cy (/ sy 2.0))
         z0 (- cz (/ sz 2.0)) z1 (+ cz (/ sz 2.0))
         c [(project* m d ox oy w h x0 y0 z0) (project* m d ox oy w h x1 y0 z0)
            (project* m d ox oy w h x0 y1 z0) (project* m d ox oy w h x1 y1 z0)
            (project* m d ox oy w h x0 y0 z1) (project* m d ox oy w h x1 y0 z1)
            (project* m d ox oy w h x0 y1 z1) (project* m d ox oy w h x1 y1 z1)]]
     (loop [i 0 dl dl]
       (if (< i 6)
         (let [f (nth faces i)
               a (nth c (nth f 0)) b (nth c (nth f 1))
               e (nth c (nth f 2)) g (nth c (nth f 3))]
           (if (and a b e g)
             (let [flat? (= shade :flat)
                   f (nth f 4)
                   r (if flat? cr (int (* f cr)))
                   gg (if flat? cg (int (* f cg)))
                   bb (if flat? cb (int (* f cb)))
                   aa (if flat? ca 255)
                   depth (* 0.25 (+ (nth a 2) (nth b 2) (nth e 2) (nth g 2)))]
               (recur (inc i) (-> dl (tri a b e r gg bb aa depth) (tri a e g r gg bb aa depth))))
             (recur (inc i) dl)))
         dl)))))

(def ^:private equivalence-cameras
  "Perspective and orthographic cameras: off every axis, on axes, inside one or
  two of a 2-box's face slabs, exactly on its face planes (y = 1, x = 1,
  z = -1 and corners of them), inside it, and with its +z face across the near
  plane."
  (for [projection [:perspective :orthographic]
        [position target] [[[0 6 10] [0 0 0]] [[7 5 10] [0 0 0]] [[-8 3 -6] [0 0 0]]
                           [[0.3 -9 0.2] [0 0 0]] [[6 6 6] [0 0 0]] [[-6 -6 -6] [0 0 0]]
                           [[0.3 0.4 12] [0 0 0]] [[11 0.5 -0.2] [0 0 0]]
                           [[0 1 5] [0 0 0]] [[1 1 6] [0 0 0]] [[1 -4 -1] [0 0 0]]
                           [[1 1 1] [0 0 0]] [[1 1 5] [1 1 0]]
                           [[0.2 0.1 0.3] [3 0 0]] [[0 0 1.03] [0 0 0]]
                           [[3 0 3] [4 0 1.5]]]]
    {:position position
     :target target
     :up (if (and (zero? (first position)) (zero? (nth position 2))) [0 0 -1] [0 1 0])
     :fovy (if (= projection :orthographic) 12.0 45.0)
     :projection projection}))

(def ^:private equivalence-transforms
  "nil, rlgl-style turns and moves (the shapes rotcube, spincubes, solarsystem
  and yawpitchroll pass), a non-uniform scale and a mirror."
  [nil
   (s3/rotate-axis 33.0 0.3 1 0)
   (s3/compose (s3/translate 1 0.5 0) (s3/rotate-axis 77.0 1 0 0))
   (s3/compose (s3/rotate-axis 120.0 0 1 0) (s3/translate 4 0 0) (s3/rotate-axis 40.0 0 1 0))
   (s3/compose (s3/rotate-axis 20.0 0 1 0) (s3/rotate-axis -15.0 1 0 0) (s3/rotate-axis 25.0 0 0 1))
   [2.0 0.0 0.0 0.0 0.0 0.5 0.0 0.0 0.0 0.0 1.5 0.0 0.0 0.0 0.0 1.0]
   [-1.0 0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0 1.0 0.0 0.0 0.0 0.0 1.0]])

(def ^:private equivalence-boxes
  "Centre, size and colour: a 2-cube as number and as integer vector, odd
  sizes, an integer centre and size, and a box the cameras sit inside."
  [[[0 0 0] 2.0 [200 100 50 128]]
   [[0 0 0] [2 2 2] [255 255 255 255]]
   [[0.5 -0.25 1.5] [0.3 2.7 1.9] [13 200 77 255]]
   [[1 2 -3] 1 [90 91 92 93]]
   [[0.0 0.0 0.0] [0.06 0.06 0.06] [1 2 3 4]]
   [[0 0 0] [30 30 30] [200 100 50 255]]])

(deftest cube-reproduces-its-reference-item-for-item
  (let [n (atom 0)]
    (doseq [camera equivalence-cameras
            viewport [[800 450] [0 300 1206 2034]]
            :let [vp (s3/view-proj camera viewport)]
            xf equivalence-transforms
            [pos size colour] equivalence-boxes
            opts [{} {:shade :flat}]]
      (let [want (reference-cube [:mark] vp xf pos size colour opts)
            got (s3/cube [:mark] vp xf pos size colour opts)]
        (swap! n + (dec (count want)))
        (when-not (= want got)
          (is (= want got) (str "camera " camera " viewport " viewport " xf " xf " box " [pos size colour] " " opts)))))
    (is (< 20000 @n) "the cases draw a lot of triangles, so the equality is not vacuous")))

(deftest cube-reads-the-m-it-is-given
  (testing "a view-proj whose :m a caller replaced, as yawpitchroll's grid does, still matches"
    (doseq [camera (take 8 equivalence-cameras)
            :let [vp0 (s3/view-proj camera [800 450])
                  vp (assoc vp0 :m (s3/compose (:m vp0) (s3/rotate-axis 50.0 0 1 0) (s3/translate 0 -3 1)))]
            [pos size colour] equivalence-boxes]
      (is (= (reference-cube [] vp nil pos size colour) (s3/cube [] vp nil pos size colour))
          (str camera " " [pos size colour])))))

;; --- billboard: rmodels.c DrawBillboardPro, corner by corner ----------------

(defn- billboard-want
  "The two items a billboard with world corners `pts` (bottom-left, bottom-right,
  top-right, top-left, as DrawBillboardPro's points 0 to 3) must give, each
  corner projected by `s3/project` and the quad's depth the mean of its four."
  [vp pts [r g b a]]
  (let [p (mapv #(s3/project vp %) pts)
        depth (/ (reduce + (map #(nth % 2) p)) 4.0)
        xy (fn [i] [(nth (p i) 0) (nth (p i) 1)])]
    [(into [:tri] (concat (xy 0) (xy 1) (xy 2) [r g b a depth]))
     (into [:tri] (concat (xy 0) (xy 2) (xy 3) [r g b a depth]))]))

(defn- items-close? [want got]
  (and (= (count want) (count got))
       (every? true? (map (fn [w g] (and (= (first w) (first g)) (close? (rest w) (rest g) 1e-9))) want got))))

(def ^:private bb-colour [10 20 30 40])

(deftest billboard-corners-face-the-camera
  (testing "looking down -z from (0, 0, 10): MatrixLookAt's right is +x and up +y, so a 2 by 4
            billboard at (1, 2, 3) has the corners (0, 0, 3) (2, 0, 3) (2, 4, 3) (0, 4, 3)"
    (let [camera (cam [0.0 0.0 10.0] [0.0 0.0 0.0] 45.0 :perspective)
          vp (s3/view-proj camera [800 450])
          corners (s3/billboard-corners camera [1.0 2.0 3.0] [2.0 4.0] {})]
      (is (every? true? (map #(close? %1 %2) [[0 0 3] [2 0 3] [2 4 3] [0 4 3]] corners)))
      (is (items-close? (billboard-want vp corners bb-colour)
                        (s3/billboard [] vp [1.0 2.0 3.0] [2.0 4.0] bb-colour)))
      (is (items-close? (billboard-want vp [[0 0 3] [2 0 3] [2 4 3] [0 4 3]] bb-colour)
                        (s3/billboard [] vp [1.0 2.0 3.0] [2.0 4.0] bb-colour)))))
  (testing "from (5, 0, 0) the right is -z: a square of 2 at the origin has the corners
            (0, -1, 1) (0, -1, -1) (0, 1, -1) (0, 1, 1)"
    (let [camera (cam [5.0 0.0 0.0] [0.0 0.0 0.0] 45.0 :perspective)
          vp (s3/view-proj camera [800 450])
          want [[0 -1 1] [0 -1 -1] [0 1 -1] [0 1 1]]]
      (is (every? true? (map #(close? %1 %2) want (s3/billboard-corners camera [0.0 0.0 0.0] 2.0 {}))))
      (is (items-close? (billboard-want vp want bb-colour)
                        (s3/billboard [] vp [0.0 0.0 0.0] 2.0 bb-colour)))))
  (testing "from above, at (0, 6, 6), the camera's up leans back: corners (-1, -s, s) (1, -s, s)
            (1, s, -s) (-1, s, -s) with s = sqrt(1/2); the world up [0 1 0] stands the quad
            upright, as DrawBillboardRec's axis lock does"
    (let [camera (cam [0.0 6.0 6.0] [0.0 0.0 0.0] 45.0 :perspective)
          vp (s3/view-proj camera [800 450])
          s (Math/sqrt 0.5)
          tilted [[-1 (- s) s] [1 (- s) s] [1 s (- s)] [-1 s (- s)]]
          upright [[-1 -1 0] [1 -1 0] [1 1 0] [-1 1 0]]]
      (is (every? true? (map #(close? %1 %2) tilted (s3/billboard-corners camera [0.0 0.0 0.0] 2.0 {}))))
      (is (every? true? (map #(close? %1 %2) upright (s3/billboard-corners camera [0.0 0.0 0.0] 2.0 {:up [0.0 1.0 0.0]}))))
      (is (items-close? (billboard-want vp upright bb-colour)
                        (s3/billboard [] vp [0.0 0.0 0.0] 2.0 bb-colour {:up [0.0 1.0 0.0]})))))
  (testing "origin and rotation: with origin [0 0] the position is the bottom-left corner, and a
            rotation of 90 degrees turns the quad counter-clockwise about it, as seen from the camera"
    (let [camera (cam [0.0 0.0 10.0] [0.0 0.0 0.0] 45.0 :perspective)]
      (is (every? true? (map #(close? %1 %2) [[0 0 0] [2 0 0] [2 2 0] [0 2 0]]
                             (s3/billboard-corners camera [0.0 0.0 0.0] 2.0 {:origin [0.0 0.0]}))))
      (is (every? true? (map #(close? %1 %2) [[0 0 0] [0 2 0] [-2 2 0] [-2 0 0]]
                             (s3/billboard-corners camera [0.0 0.0 0.0] 2.0 {:origin [0.0 0.0]
                                                                             :rotation 90.0}))))
      (is (every? true? (map #(close? %1 %2) [[1 -1 0] [1 1 0] [-1 1 0] [-1 -1 0]]
                             (s3/billboard-corners camera [0.0 0.0 0.0] 2.0 {:rotation 90.0}))))))
  (testing "a part is a sub-rectangle of the quad, in fractions from its bottom-left; it turns with the quad"
    (let [camera (cam [0.0 0.0 10.0] [0.0 0.0 0.0] 45.0 :perspective)]
      (is (every? true? (map #(close? %1 %2) [[-1 -1 0] [0 -1 0] [0 0 0] [-1 0 0]]
                             (s3/billboard-corners camera [0.0 0.0 0.0] 2.0 {:part [0.0 0.0 0.5 0.5]}))))
      (is (every? true? (map #(close? %1 %2) [[1 -1 0] [1 0 0] [0 0 0] [0 -1 0]]
                             (s3/billboard-corners camera [0.0 0.0 0.0] 2.0 {:part [0.0 0.0 0.5 0.5]
                                                                             :rotation 90.0}))))))
  (testing "the corners are coplanar and perpendicular to the view direction, on an orbit"
    (doseq [deg (range 0 360 30)
            :let [a (Math/toRadians deg)
                  eye [(* 8.0 (Math/cos a)) 3.0 (* 8.0 (Math/sin a))]
                  camera (cam eye [0.0 2.0 0.0] 45.0 :perspective)
                  [p0 p1 p2 p3] (s3/billboard-corners camera [1.0 2.0 0.0] [2.0 3.0] {:rotation (* 1.0 deg)})
                  sub (fn [u v] (mapv - u v))
                  dot (fn [u v] (reduce + (map * u v)))
                  look (mapv - eye [0.0 2.0 0.0])]]
      (is (< (Math/abs (dot (sub p1 p0) look)) 1e-9) (str deg))
      (is (< (Math/abs (dot (sub p3 p0) look)) 1e-9) (str deg))
      (is (close? (sub p2 p3) (sub p1 p0)) "a parallelogram")))
  (testing "every quad that is in front of the camera is two front-wound triangles, on an orbit"
    (doseq [deg (range 0 360 15)
            :let [a (Math/toRadians deg)
                  camera (cam [(* 8.0 (Math/cos a)) 3.0 (* 8.0 (Math/sin a))] [0.0 2.0 0.0] 45.0 :perspective)
                  vp (s3/view-proj camera [800 450])
                  dl (s3/billboard [] vp [1.0 2.0 0.0] 2.0 bb-colour {:rotation (* 2.0 deg)})]]
      (is (= 2 (count dl)) (str deg))
      (is (every? #(neg? (cross %)) dl) (str deg))))
  (testing "the item is [:tri x1 y1 x2 y2 x3 y3 r g b a depth]"
    (let [camera (cam [0.0 0.0 10.0] [0.0 0.0 0.0] 45.0 :perspective)
          vp (s3/view-proj camera [800 450])]
      (is (= :mark (first (s3/billboard [:mark] vp [0.0 0.0 0.0] 2.0 bb-colour))) "appends to the list it is given"))
    (let [camera (cam [0.0 0.0 10.0] [0.0 0.0 0.0] 45.0 :perspective)
          vp (s3/view-proj camera [800 450])
          dl (s3/billboard [] vp [0.0 0.0 0.0] 2.0 bb-colour)]
      (is (= 12 (count (first dl))))
      (is (= bb-colour (colour-of (first dl))))
      (is (close? [10.0] [(nth (first dl) 11)] 1e-9) "the depth of a quad 10 in front is 10")
      (is (= (subvec (first dl) 7) (subvec (second dl) 7)) "the two triangles share colour and depth")))
  (testing "a corner behind the near plane drops the quad whole"
    (let [camera (cam [0.0 0.0 10.0] [0.0 0.0 0.0] 45.0 :perspective)
          vp (s3/view-proj camera [800 450])]
      (is (= [] (s3/billboard [] vp [0.0 0.0 10.0] 2.0 bb-colour)) "centred on the eye")
      (is (= [] (s3/billboard [] vp [0.0 0.0 12.0] 2.0 bb-colour)) "behind it")
      (is (= 2 (count (s3/billboard [] vp [0.0 0.0 5.0] 2.0 bb-colour)))))))
