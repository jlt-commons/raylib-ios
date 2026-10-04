(ns raylib.scenes.texpoly-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.texpoly :as sc]
            [raylib.texel :as texel]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def metrics {:screen [1206 2334]})

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- fresh [] (first ((:init (sc/scene)) {:metrics metrics})))

(defn- tick [state]
  (first ((:update (sc/scene)) state {:metrics metrics
                                      :pointer {:phase :idle
                                                :position nil}})))

(defn- near? [a b] (< (abs (- (double a) (double b))) 1e-9))

;; raylib-jlt's `rgba` (net/b12n/raylib/color.clj) and polygon_drawing.clj's
;; TEXCOORDS, POINTS, `hsv->color`, `wheel-pixel` and `rotate` (lines 27-64) and
;; the fan (lines 87-99), copied verbatim as the reference.
(defn- ref-rgba [r g b a]
  (bit-or (int r) (bit-shift-left (int g) 8)
          (bit-shift-left (int b) 16) (bit-shift-left (int a) 24)))

(def ref-texcoords
  [[0.75 0.0] [0.25 0.0] [0.0 0.5] [0.0 0.75] [0.25 1.0]
   [0.375 0.875] [0.625 0.875] [0.75 1.0] [1.0 0.75] [1.0 0.5] [0.75 0.0]])

(def ref-points
  (mapv (fn [[u v]] [(* (- u 0.5) 256.0) (* (- v 0.5) 256.0)]) ref-texcoords))

(defn- ref-hsv->color [h]
  (let [h' (/ (mod h 360.0) 60.0)
        i (int (Math/floor h'))
        f (- h' i)
        q (- 1.0 f)
        [r g b] (cond
                  (= i 0) [1.0 f 0.0]
                  (= i 1) [q 1.0 0.0]
                  (= i 2) [0.0 1.0 f]
                  (= i 3) [0.0 q 1.0]
                  (= i 4) [f 0.0 1.0]
                  :else [1.0 0.0 q])]
    (ref-rgba (int (* 255 r)) (int (* 255 g)) (int (* 255 b)) 255)))

(defn- ref-wheel-pixel [x y]
  (let [cx (/ 256 2.0)
        cy (/ 256 2.0)
        dx (- x cx)
        dy (- y cy)]
    (ref-hsv->color (Math/toDegrees (Math/atan2 dy dx)))))

(defn- ref-rotate [x y rad]
  [(- (* x (Math/cos rad)) (* y (Math/sin rad)))
   (+ (* x (Math/sin rad)) (* y (Math/cos rad)))])

(defn- ref-fan
  "The original's loop body, with its rl-tex-coord-2f / rl-vertex-2f pairs
  collected as `[x y u v]`."
  [angle]
  (let [rad (* angle 0.0174532925)
        out (atom [])]
    (dotimes [i 10]
      (let [[p0x p0y] (nth ref-points i)
            [p1x p1y] (nth ref-points (inc i))
            [t0u t0v] (nth ref-texcoords i)
            [t1u t1v] (nth ref-texcoords (inc i))
            [r0x r0y] (ref-rotate p0x p0y rad)
            [r1x r1y] (ref-rotate p1x p1y rad)]
        (swap! out into [400.0 225.0 0.5 0.5
                         (+ r0x 400.0) (+ r0y 225.0) t0u t0v
                         (+ r1x 400.0) (+ r1y 225.0) t1u t1v])))
    @out))

(defn- low32 [n] (bit-and n 0xFFFFFFFF))

(defn- cross
  "The signed area (twice) of the triangle at flat offset `o` of `verts`."
  [verts o]
  (let [g (fn [i k] (double (nth verts (+ o (* 4 i) k))))]
    (- (* (- (g 1 0) (g 0 0)) (- (g 2 1) (g 0 1)))
       (* (- (g 1 1) (g 0 1)) (- (g 2 0) (g 0 0))))))

(deftest the-wheel-is-the-originals
  (let [{:keys [w h pixel]} (sc/wheel-spec texel/pack)]
    (is (= [256 256] [w h]))
    (is (= 65536 (count (for [y (range h)
                              x (range w)]
                          (is (= (low32 (ref-wheel-pixel x y)) (low32 (pixel x y))) (str [x y]))))))
    (testing "the hue runs the whole way round"
      (is (< 200 (count (set (for [y (range 0 h 4)
                                   x (range 0 w 4)]
                               (pixel x y)))))))
    (testing "the byte order is raylib-jlt's rgba"
      (is (= (low32 (ref-rgba 255 128 0 255)) (texel/pack [255 128 0 255]))))))

(deftest specs-obey-gles2
  (let [{:keys [w h wrap filter]} (sc/wheel-spec texel/pack)]
    (testing "every uv is in 0..1, so the wheel clamps, and 256 could repeat anyway"
      (is (= :clamp wrap))
      (is (every? #(<= 0.0 % 1.0) (mapcat identity sc/texcoords))))
    (is (= :nearest filter))
    (is (zero? (bit-and w (dec w))))
    (is (zero? (bit-and h (dec h))))))

(deftest the-fan-is-the-originals
  (testing "the points and uvs are the original's"
    (is (= ref-texcoords sc/texcoords))
    (is (= ref-points sc/points)))
  (testing "at the original's scale and middle, every vertex and uv matches"
    (doseq [angle [0.0 1.0 37.0 90.0 359.0 725.0]]
      (let [mine (sc/fan angle 400.0 225.0 1.0)
            theirs (ref-fan angle)]
        (is (= 120 (count mine)) "ten triangles of three vertices of x y u v")
        (is (every? true? (map near? mine theirs)) (str angle " degrees")))))
  (testing "the middle is uv (0.5, 0.5) and each triangle closes on the next"
    (let [v (sc/fan 0.0 400.0 225.0 1.0)]
      (doseq [t (range 10)
              :let [o (* t 12)]]
        (is (= [0.5 0.5] [(nth v (+ o 2)) (nth v (+ o 3))]))
        (when (< t 9)
          (is (= (subvec v (+ o 8) (+ o 12)) (subvec v (+ o 16) (+ o 20)))
              "the third vertex is the next triangle's second"))))
    (let [v (sc/fan 0.0 400.0 225.0 1.0)]
      (is (= (subvec v 6 8) (subvec v 118 120))
          "the loop closes on the first point: the last uv is the first's")))
  (testing "every triangle is non-degenerate and wound one way at any angle, so
            the fan is a proper fan; triangles! then fixes the winding itself"
    (doseq [angle [0.0 1.0 45.0 200.0 359.0]
            :let [v (sc/fan angle 400.0 225.0 1.0)
                  crosses (map #(cross v (* % 12)) (range 10))]]
      (is (every? #(> (abs %) 1.0) crosses) (str angle))
      (is (or (every? pos? crosses) (every? neg? crosses)) (str angle))))
  (testing "the scale and the middle move the vertices, not the uvs"
    (let [a (sc/fan 30.0 0.0 0.0 1.0)
          b (sc/fan 30.0 10.0 20.0 0.5)]
      (is (every? true? (for [t (range 30)
                              :let [o (* t 4)]]
                          (and (near? (+ 10.0 (* 0.5 (nth a o))) (nth b o))
                               (near? (+ 20.0 (* 0.5 (nth a (+ o 1)))) (nth b (+ o 1)))
                               (= (subvec a (+ o 2) (+ o 4)) (subvec b (+ o 2) (+ o 4))))))))))

(deftest the-angle-follows-the-original
  (let [s (fresh)]
    (is (= 0.0 (:angle s)))
    (testing "one degree a frame, with no wrap"
      (is (= 1.0 (:angle (tick s))))
      (is (= 10.0 (:angle (nth (iterate tick s) 10))))
      (is (= 400.0 (:angle (nth (iterate tick s) 400)))))))

(deftest first-frame-draws
  (let [s (tick (fresh))]
    (is (= :texpoly (:id (sc/scene))))
    (is (= "Polygon Drawing" (:title (sc/scene))))
    (doseq [screen screens
            :let [[w h] screen
                  dims (sc/dimensions {:screen screen} measure)
                  [{:keys [y size]}] (:lines dims)]]
      (testing (str screen)
        (doseq [angle [0.0 1.0 45.0 90.0 135.0 200.0 300.0]
                :let [v (sc/vertices (assoc s :angle angle) dims)
                      xs (take-nth 4 v)
                      ys (take-nth 4 (rest v))]]
          (testing (str angle " degrees: the whole fan stays on the screen, under the caption")
            (is (= 120 (count v)))
            (is (>= (apply min xs) 0))
            (is (<= (apply max xs) w))
            (is (>= (apply min ys) (+ y size)))
            (is (<= (apply max ys) h))))
        (testing "the middle is the fan's middle"
          (let [v (sc/vertices s dims)]
            (is (= [(:cx dims) (:cy dims)] [(nth v 0) (nth v 1)]))))))
    (testing "at the original's scale the first drawn frame is the original's frame 1"
      (is (every? true? (map near? (sc/vertices s {:cx 400.0
                                                   :cy 225.0
                                                   :k 1.0}) (ref-fan 1.0)))))
    (testing "the scale puts the farthest point at 45 percent of the shorter free side"
      (let [{:keys [k]} (sc/geometry metrics)]
        (is (< 0 k))
        (is (< (abs (- 143.108 sc/reach)) 1e-3)
            "the farthest point, measured")))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [lines]} (sc/dimensions {:screen screen} measure)]]
    (testing (str screen)
      (is (= 1 (count lines)))
      (doseq [{:keys [s x y size]} lines]
        (testing s
          (is (<= 0 x))
          (is (<= (+ x (measure s size)) w))
          (is (<= (+ y size) h))
          (is (>= y 120) "below Back"))))))
