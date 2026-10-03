(ns raylib.scenes.texcube-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.texcube :as sc]
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
(defn- lines [dl] (filterv (fn [it] (= :line (nth it 0))) dl))

(defn- cross-y-down [[_ x1 y1 x2 y2 x3 y3]]
  (- (* (- x2 x1) (- y3 y1)) (* (- y2 y1) (- x3 x1))))

;; --- the original's texture and quads, transcribed as the oracle -------------
;; textured_cube.clj lines 21-29 (atlas-pixel) and 48-64, 72-90 (the vertex
;; lines of draw-cube-texture and draw-cube-texture-rec). Each vertex is
;; [u v sx sy sz], where sx sy sz are -1 for xl yb zb and 1 for xr yt zf.

(defn- original-atlas [x y]
  (let [left? (< x 32)
        top? (< y 32)]
    (cond
      (and left? top?) [230 41 55 255]
      (and (not left?) top?) [0 158 47 255]
      (and left? (not top?)) [0 121 241 255]
      :else [253 249 0 255])))

(def ^:private whole
  "draw-cube-texture's six quads in its order: front, back, top, bottom, right, left."
  [[[0 0 -1 -1 1] [1 0 1 -1 1] [1 1 1 1 1] [0 1 -1 1 1]]
   [[1 0 -1 -1 -1] [1 1 -1 1 -1] [0 1 1 1 -1] [0 0 1 -1 -1]]
   [[0 1 -1 1 -1] [0 0 -1 1 1] [1 0 1 1 1] [1 1 1 1 -1]]
   [[1 1 -1 -1 -1] [0 1 1 -1 -1] [0 0 1 -1 1] [1 0 -1 -1 1]]
   [[1 0 1 -1 -1] [1 1 1 1 -1] [0 1 1 1 1] [0 0 1 -1 1]]
   [[0 0 -1 -1 -1] [1 0 -1 -1 1] [1 1 -1 1 1] [0 1 -1 1 -1]]])

(defn- rec
  "draw-cube-texture-rec's quads: the same vertices with u over [su0 su1] and v
  over [sv0 sv1], and its v turned over (its front has (su0, sv1) at the bottom
  left where the whole one has (0, 0))."
  [su0 su1 sv0 sv1]
  (mapv (fn [quad]
          (mapv (fn [[u v & p]]
                  (into [(+ su0 (* u (- su1 su0))) (- sv1 (* v (- sv1 sv0)))] p))
                quad))
        whole))

(def ^:private normals [[0 0 1] [0 0 -1] [0 1 0] [0 -1 0] [1 0 0] [-1 0 0]])

(defn- v- [a b] (mapv - a b))
(defn- dot [a b] (reduce + (map * a b)))
(defn- cross [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by)) (- (* az bx) (* ax bz)) (- (* ax by) (* ay bx))])

(defn- oracle-colour
  "The original's colour at world point `p` of the quad `quad` on a cube
  centred `c` with half sides `h`: u and v are affine over the rectangle, found
  from its first vertex and its two neighbours."
  [quad c h p]
  (let [world (fn [[_ _ sx sy sz]] [(+ (c 0) (* sx (h 0))) (+ (c 1) (* sy (h 1))) (+ (c 2) (* sz (h 2)))])
        [q0 q1 _ q3] quad
        w0 (world q0) e1 (v- (world q1) w0) e3 (v- (world q3) w0)
        d (v- p w0)
        s (/ (dot d e1) (dot e1 e1))
        t (/ (dot d e3) (dot e3 e3))
        u (+ (q0 0) (* s (- (q1 0) (q0 0))) (* t (- (q3 0) (q0 0))))
        v (+ (q0 1) (* s (- (q1 1) (q0 1))) (* t (- (q3 1) (q0 1))))]
    (original-atlas (min 63 (int (* u 64))) (min 63 (int (* v 64))))))

(defn- centre-of [corners] (mapv #(/ % 4.0) (apply mapv + corners)))

(defn- area [[a b _ d]] (let [c (cross (v- b a) (v- d a))] (Math/sqrt (dot c c))))

(def ^:private cube-specs
  "id -> [centre half-sides quads] from the original's -main (lines 118-135)."
  {:whole [[-2.0 2.0 0.0] [1.0 2.0 1.0] whole]
   :slice [[2.0 1.0 0.0] [1.0 1.0 1.0] (rec 0.0 0.5 0.5 1.0)]})

(deftest each-face-shows-its-pattern
  (testing "the cubes are the original's: 2x4x2 at (-2, 2, 0) and 2x2x2 at (2, 1, 0)"
    (is (= [:whole :slice] (mapv :id sc/cubes)))
    (doseq [{:keys [id centre size]} sc/cubes]
      (is (= (subvec (cube-specs id) 0 1) [centre]) (str id))
      (is (= (mapv #(* 2.0 %) (nth (cube-specs id) 1)) (mapv double size)) (str id))))
  (doseq [{:keys [id]
           :as cube} sc/cubes
          :let [[c h quads] (cube-specs id)]
          face (range 6)
          :let [cells (sc/face-cells cube face)]]
    (testing (str id " face " face)
      (is (= (if (= id :whole) 4 1) (count cells))
          "the whole atlas is four quadrants a face, the slice sits in one")
      (is (every? #(= 4 (count (:corners %))) cells))
      (is (every? (fn [{:keys [corners colour]}]
                    (= (oracle-colour (nth quads face) c h (centre-of corners)) colour))
                  cells)
          "each cell has the colour the original's texture gives it at that spot of that face")
      (is (every? (fn [{:keys [corners]}]
                    (every? (fn [p] (every? (fn [[k hk]] (<= (- (c k) hk 1e-9) (p k) (+ (c k) hk 1e-9)))
                                            (map-indexed vector h)))
                            corners))
                  cells)
          "inside the cube's box")
      (is (near? (area (let [vs (nth quads face)
                             w (fn [[_ _ sx sy sz]] [(+ (c 0) (* sx (h 0))) (+ (c 1) (* sy (h 1))) (+ (c 2) (* sz (h 2)))])]
                         (mapv w vs)))
                 (reduce + (map (comp area :corners) cells)))
          "the cells tile the face: their areas add up to it")))
  (testing "the left cube's faces carry all four of the atlas's colours once"
    (doseq [face (range 6)]
      (is (= (set [[230 41 55 255] [0 158 47 255] [0 121 241 255] [253 249 0 255]])
             (set (map :colour (sc/face-cells (first sc/cubes) face))))
          (str "face " face))))
  (testing "every face of the slice cube is the blue bottom-left quadrant"
    (doseq [face (range 6)]
      (is (= [[0 121 241 255]] (mapv :colour (sc/face-cells (second sc/cubes) face)))
          (str "face " face)))))

(deftest every-sub-quad-is-wound-front-from-outside
  (doseq [{:keys [id]
           :as cube} sc/cubes
          face (range 6)
          {:keys [corners]} (sc/face-cells cube face)
          :let [[a b c] corners
                n (cross (v- b a) (v- c b))]]
    (is (pos? (dot n (normals face))) (str id " face " face " goes counter-clockwise from outside")))
  (testing "on screen only whole faces turn up, with the winding rlgl keeps"
    (doseq [n (range 0 700 3)
            :let [s (frames n)
                  dims (sc/dimensions {:screen [1206 2334]} measure)
                  dl (sc/scene-list s dims)
                  cam (sc/camera s dims)
                  eye (:position cam)
                  per-cube (fn [{:keys [centre size]
                                 :as cube}]
                             (reduce + (for [face (range 6)
                                             :let [nv (normals face)
                                                   fc (mapv + centre (map * nv (map #(* 0.5 %) size)))
                                                   facing? (pos? (dot nv (v- eye fc)))]
                                             :when facing?]
                                         (* 2 (count (sc/face-cells cube face))))))
                  expected (reduce + (map per-cube sc/cubes))]]
      (is (every? #(neg? (cross-y-down %)) (tris dl)) (str "frame " n))
      (is (= expected (count (tris dl))) (str "frame " n ": the faces toward the camera, all their cells")))))

(deftest the-cube-turns-at-the-originals-rate
  (testing "the camera goes round 0.01 radians a frame (angle = frame * 0.01, line 111)"
    (is (near? 0.0 (sc/angle (frames 0))))
    (is (near? 0.01 (sc/angle (frames 1))))
    (is (near? 1.0 (sc/angle (frames 100))))
    (is (near? 6.28 (sc/angle (frames 628)))))
  (testing "it sits at (14 cos a, 8, 14 sin a) looking at the origin, fovy 45 (lines 113-115)"
    (let [dims (sc/dimensions {:screen [2334 1206]} measure)
          {:keys [position target up fovy projection]} (sc/camera (frames 100) dims)]
      (is (near? (* 14.0 (Math/cos 1.0)) (position 0)))
      (is (near? 8.0 (position 1)))
      (is (near? (* 14.0 (Math/sin 1.0)) (position 2)))
      (is (= [0.0 0.0 0.0] (mapv double target)))
      (is (= [0.0 1.0 0.0] (mapv double up)))
      (is (near? 45.0 fovy))
      (is (= :perspective projection))))
  (testing "the cubes stay put and the view changes"
    (let [dims (sc/dimensions {:screen [1206 2334]} measure)]
      (is (not= (sc/scene-list (frames 0) dims) (sc/scene-list (frames 1) dims)))
      (is (= (sc/scene-list (frames 3) dims) (sc/scene-list (frames 3) dims))))))

(deftest the-farther-cube-is-painted-first
  (let [dims (sc/dimensions {:screen [1206 2334]} measure)
        [vx vy vw vh] (:viewport dims)
        overlaps (atom 0)]
    (doseq [n (range 0 720 5)
            :let [s (frames n)
                  vp (s3/view-proj (sc/camera s dims) (:viewport dims))
                  order (sc/cube-order s)
                  rank (zipmap order (range))]
            i (range 40) j (range 40)
            :let [ray (s3/screen->ray vp [(+ vx (* vw (/ (+ i 0.5) 40))) (+ vy (* vh (/ (+ j 0.5) 40)))])
                  hits (for [{:keys [id centre size]} sc/cubes
                             :let [h (map #(* 0.5 %) size)
                                   r (s3/ray-box ray (mapv - centre h) (mapv + centre h))]
                             :when (:hit? r)]
                         [id (:distance r)])]
            :when (= 2 (count hits))]
      (swap! overlaps inc)
      (let [nearer (first (apply min-key second hits))]
        (is (= 1 (rank nearer)) (str "frame " n " ray " i "," j " hits " hits " but order is " order))))
    (is (pos? @overlaps) "some views have one cube in front of the other"))
  (testing "the order is a plain function of the camera's side of the plane between them"
    (is (= [:whole :slice] (sc/cube-order (frames 100))) "camera at x 7.6: the slice cube is nearer, painted last")
    (is (= [:slice :whole] (sc/cube-order (frames 400))) "camera at x -9.2: the whole one is nearer")))

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                dims (sc/dimensions metrics measure)
                [vx vy vw vh] (:viewport dims)
                dl (sc/scene-list (frames 0) dims)
                ts (tris dl)
                colours (set (map (fn [it] (subvec it 7 11)) ts))]]
    (testing (str screen)
      (is (= 20 (count ts))
          "from (14, 8, 0): the whole cube's +x and top faces (4 cells each, 2 triangles a cell) and the slice cube's (1 cell each)")
      (is (= 22 (count (lines dl))) "the grid of 10")
      (is (= #{[230 41 55 255] [0 158 47 255] [0 121 241 255] [253 249 0 255]} colours)
          "the atlas's four colours, all there")
      (is (every? (fn [[_ & more]]
                    (every? (fn [[x y]] (and (<= vx x (+ vx vw)) (<= vy y (+ vy vh))))
                            (partition 2 (take 6 more))))
                  ts)
          "every corner is inside the field")
      (is (every? (fn [n] (let [d (tris (sc/scene-list (frames n) dims))]
                            (and (<= (count d) 30) (every? (fn [[_ & more]]
                                                             (every? (fn [[x y]] (and (<= (- vx 1e-6) x (+ vx vw 1e-6))
                                                                                      (<= (- vy 1e-6) y (+ vy vh 1e-6))))
                                                                     (partition 2 (take 6 more))))
                                                           d))))
                  (range 0 640 16))
          "through a whole orbit: inside the field and never more than 30 triangles"))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [_ fy _ fh] (:viewport dims)]]
    (testing (str screen)
      (is (= 1 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (= "Left: whole atlas. Right: one quarter of it (DrawCubeTextureRec)." s))
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) fy) "the caption sits above the field"))
      (is (>= fy (+ back-y back-h)) "the field is below Back")
      (is (near? h (+ fy fh)) "and runs to the bottom"))))
