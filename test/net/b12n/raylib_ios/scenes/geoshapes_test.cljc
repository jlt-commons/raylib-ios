(ns net.b12n.raylib-ios.scenes.geoshapes-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.geoshapes :as sc]
            [net.b12n.raylib-ios.soft3d :as s3]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(defn- tris [dl] (filterv (fn [it] (= :tri (nth it 0))) dl))
(defn- lines [dl] (filterv (fn [it] (= :line (nth it 0))) dl))

(defn- cross-y-down [[_ x1 y1 x2 y2 x3 y3]]
  (- (* (- x2 x1) (- y3 y1)) (* (- y2 y1) (- x3 x1))))

(defn- inside? [[vx vy vw vh] x y]
  (and (<= (- vx 1e-6) x (+ vx vw 1e-6)) (<= (- vy 1e-6) y (+ vy vh 1e-6))))

(defn- list-for [screen]
  (let [dims (sc/dimensions {:screen screen} measure)]
    [dims (sc/scene-list {:frame 0} dims)]))

(deftest sphere-wires-match-drawspherewires
  (let [segs (sc/sphere-wire-segments [1.0 0.0 2.0] 2.0 16 16 sc/lime)
        close? (fn [a b] (every? true? (map (fn [x y] (< (abs (- x y)) 1e-9)) a b)))]
    (is (= (* 18 16 3) (count segs)) "(rings + 2) rows x slices x 3 segments")
    (testing "the first cell, by hand: i 0 is latitude 270 degrees (the south pole), j 0 longitude 0"
      ;; a = (cos270 sin0, sin270, cos270 cos0) = (0 -1 0)    -> (1 -2 2)
      ;; b = ring 1 (280.588 deg), slice 1 (22.5 deg)
      ;; c = ring 1, slice 0
      (let [[a b c] segs
            lat (Math/toRadians (+ 270.0 (/ 180.0 17.0)))]
        (is (close? (nth a 0) [1.0 -2.0 2.0]))
        (is (close? (nth a 1) [(+ 1.0 (* 2.0 (Math/cos lat) (Math/sin (Math/toRadians 22.5))))
                               (* 2.0 (Math/sin lat))
                               (+ 2.0 (* 2.0 (Math/cos lat) (Math/cos (Math/toRadians 22.5))))]))
        (is (close? (nth b 0) (nth a 1)) "b starts where a ended")
        (is (close? (nth b 1) (nth c 0)))
        (is (close? (nth c 0) [(+ 1.0 (* 2.0 (Math/cos lat) 0.0))
                               (* 2.0 (Math/sin lat))
                               (+ 2.0 (* 2.0 (Math/cos lat)))]))
        (is (close? (nth c 1) (nth a 0)) "c closes the triangle on a")))
    (is (every? #(= sc/lime (nth % 2)) segs))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [[dims dl] (list-for screen)
                ts (tris dl)
                ls (lines dl)
                tri-colours (set (map (fn [it] (subvec it 7 11)) ts))
                line-colours (set (map (fn [it] (subvec it 5 9)) ls))]]
    (testing (str screen)
      (is (= 1643 (count ls))
          "grid 22, gold cube wires 9 (three faces show, hide-back), maroon 12, sphere wires 864, cylinder wires 16 + 24 + 32, capsule wires 664")
      (is (= 362 (count ts)) "measured: 16x16 sphere, 4/8-sided cylinders, 8x8 capsule, 3 cube faces, front-facing only")
      (is (every? (fn [it] (neg? (cross-y-down it))) ts) "every triangle is wound front")
      (is (every? #(contains? tri-colours %) [sc/red sc/skyblue sc/gold sc/violet])
          "the flat colours of the cube, cylinder, cone and capsule are all there, unshaded")
      (is (contains? tri-colours sc/green) "the sphere is flat GREEN, as DrawSphere draws it")
      (is (every? #(contains? line-colours %)
                  [sc/gold sc/maroon sc/lime sc/darkblue sc/brown sc/pink sc/purple])
          "every wire colour is there")
      (is (every? (fn [it] (every? (fn [[x y]] (inside? (:viewport dims) x y)) (partition 2 (subvec it 1 7)))) ts)
          "every triangle corner is inside the field")
      (is (every? (fn [it] (every? (fn [[x y]] (inside? (:viewport dims) x y)) (partition 2 (subvec it 1 5)))) ls)
          "every line end is inside the field"))))

(deftest the-scene-is-the-originals
  ;; geometric_shapes.clj: the camera map (lines 28-37) and the draws in -main
  ;; (lines 41-110), transcribed here with literals and not through sc's constants.
  ;; On a screen at least as wide as 800x450 fit-camera leaves the camera as it is.
  (let [dims (sc/dimensions {:screen [2334 1206]} measure)
        cam {:position [0.0 10.0 10.0]
             :target [0.0 0.0 0.0]
             :up [0.0 1.0 0.0]
             :fovy 45.0
             :projection :perspective}
        vp (s3/view-proj cam (:viewport dims))
        flat {:shade :flat}
        wire-sphere (sc/sphere-wire-segments [1.0 0.0 2.0] 2.0 16 16 [0 158 47 255])
        expected (-> []
                     (s3/grid vp 10 1.0)
                     (s3/cube vp nil [-4.0 0.0 2.0] [2.0 5.0 2.0] [230 41 55 255] flat)
                     (s3/cube-wires vp nil [-4.0 0.0 2.0] [2.0 5.0 2.0] [255 203 0 255] {:hide-back? true})
                     (s3/cube-wires vp nil [-4.0 0.0 -2.0] [3.0 6.0 2.0] [190 33 55 255])
                     (s3/sphere vp nil [-1.0 0.0 -2.0] 1.0 [0 228 48 255] {:rings 16
                                                                           :slices 16
                                                                           :shade :flat})
                     (s3/lines vp nil wire-sphere)
                     (s3/cylinder vp nil [4.0 0.0 -2.0] 1.0 2.0 3.0 [102 191 255 255] {:slices 4})
                     (s3/cylinder-wires vp nil [4.0 0.0 -2.0] 1.0 2.0 3.0 [0 82 172 255] {:slices 4})
                     (s3/cylinder-wires vp nil [4.5 -1.0 2.0] 1.0 1.0 2.0 [127 106 79 255] {:slices 6})
                     (s3/cylinder vp nil [1.0 0.0 -4.0] 0.0 1.5 3.0 [255 203 0 255] {:slices 8})
                     (s3/cylinder-wires vp nil [1.0 0.0 -4.0] 0.0 1.5 3.0 [255 109 194 255] {:slices 8})
                     (s3/capsule vp nil [-3.0 1.5 -4.0] [-4.0 -1.0 -4.0] 1.2 [135 60 190 255] {:slices 8
                                                                                               :rings 8})
                     (s3/capsule-wires vp nil [-3.0 1.5 -4.0] [-4.0 -1.0 -4.0] 1.2 [200 122 255 255] {:slices 8
                                                                                                      :rings 8})
                     (s3/finish))]
    (is (= (:camera vp) (sc/camera dims)) "the camera: (0 10 10) at the origin, up y, fovy 45")
    (is (= expected (sc/scene-list {:frame 0} dims))
        "every shape's position, size, radius, height, sides, colour and tessellation")
    (is (= 362 (count (tris expected))))
    (is (= 1643 (count (lines expected))))))

(deftest the-shapes-are-in-the-originals-tessellation
  (is (= sc/original sc/tessellation)
      "if this fails the scene was cut: say so in the ns docstring and the catalog row"))

(deftest paint-order
  (let [[_ dl] (list-for [1206 2334])
        kinds (mapv (fn [it] (if (= :tri (nth it 0)) :tri (nth it 9))) dl)
        under (take-while #(= :under %) kinds)
        rest* (drop (count under) kinds)
        tri-run (take-while #(= :tri %) rest*)
        tail (drop (count tri-run) rest*)
        depths (map #(nth % 11) (filter #(= :tri (nth % 0)) dl))]
    (is (= 22 (count under)) "the grid first")
    (is (pos? (count tri-run)))
    (is (every? #(= :over %) tail) "then every wire, after every face")
    (is (apply >= depths) "triangles far to near")))

(deftest the-list-depends-on-the-screen-alone
  (testing "the gallery caches the list by screen, so no state may change it"
    (doseq [screen screens
            :let [dims (sc/dimensions {:screen screen} measure)
                  base (sc/scene-list {:frame 0} dims)]]
      (is (= base (sc/scene-list {:frame 999} dims)))
      (is (= base (sc/scene-list nil dims))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [_ fy _ fh] (:viewport dims)]]
    (testing (str screen)
      (is (= 1 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (= "geometric shapes" s))
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) fy) "the caption sits above the field"))
      (is (>= fy (+ back-y back-h)) "the field is below Back")
      (is (near? h (+ fy fh)) "and runs to the bottom"))))
