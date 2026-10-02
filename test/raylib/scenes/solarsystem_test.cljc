(ns raylib.scenes.solarsystem-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.solarsystem :as sc]
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

(defn- origin
  "Where transform `m` puts the point (0, 0, 0): its translation column."
  [m]
  [(nth m 3) (nth m 7) (nth m 11)])

(deftest the-three-angles-run-at-their-own-rates
  (testing "the Earth orbits half a degree a frame, spins one and the Moon orbits two"
    (is (near? 45.0 (sc/earth-orbit (frames 90))))
    (is (near? 90.0 (sc/earth-spin (frames 90))))
    (is (near? 180.0 (sc/moon-orbit (frames 90)))))
  (testing "each wraps at 360, as the original's mod"
    (is (near? 0.0 (sc/earth-orbit (frames 720))))
    (is (near? 0.0 (sc/earth-spin (frames 360))))
    (is (near? 0.0 (sc/moon-orbit (frames 180))))
    (is (near? 0.5 (sc/earth-orbit (frames 721))))
    (is (near? 2.0 (sc/earth-spin (frames 362))))
    (is (near? 4.0 (sc/moon-orbit (frames 182))))))

(deftest the-hierarchy-nests-in-rlgl-order
  (testing "the Earth: rotate by its orbit, translate 9, then spin in place"
    (is (= (s3/compose (s3/rotate-axis 45.0 0.0 1.0 0.0) (s3/translate 9.0 0.0 0.0) (s3/rotate-axis 90.0 0.0 1.0 0.0))
           (sc/earth-transform (frames 90))))
    (let [[x y z] (origin (sc/earth-transform (frames 90)))]
      ;; by hand: R_y(45) takes (9, 0, 0) to (9 cos 45, 0, -9 sin 45)
      (is (near? (* 9.0 (Math/cos (Math/toRadians 45.0))) x))
      (is (near? 0.0 y))
      (is (near? (- (* 9.0 (Math/sin (Math/toRadians 45.0)))) z))))
  (testing "the Moon: the Earth's orbit and translate, then its own orbit and translate 2.6, but not the Earth's spin"
    (is (= (s3/compose (s3/rotate-axis 45.0 0.0 1.0 0.0) (s3/translate 9.0 0.0 0.0)
                       (s3/rotate-axis 180.0 0.0 1.0 0.0) (s3/translate 2.6 0.0 0.0))
           (sc/moon-transform (frames 90))))
    (let [[x y z] (origin (sc/moon-transform (frames 90)))
          ;; the Earth's centre plus 2.6 out along the moon-orbit turned 180 on top of the earth-orbit 45 = 225
          ex (* 9.0 (Math/cos (Math/toRadians 45.0)))
          ez (- (* 9.0 (Math/sin (Math/toRadians 45.0))))]
      (is (near? (+ ex (* 2.6 (Math/cos (Math/toRadians 225.0)))) x))
      (is (near? 0.0 y))
      (is (near? (+ ez (- (* 2.6 (Math/sin (Math/toRadians 225.0))))) z))))
  (testing "the Moon is 2.6 from the Earth at every frame, and the Earth 9 from the Sun"
    (doseq [n [0 1 33 200 719]
            :let [e (origin (sc/earth-transform (frames n)))
                  m (origin (sc/moon-transform (frames n)))
                  dist (fn [a b] (Math/sqrt (reduce + (map (fn [p q] (* (- p q) (- p q))) a b))))]]
      (is (near? 9.0 (dist e [0.0 0.0 0.0])) (str "frame " n))
      (is (near? 2.6 (dist m e)) (str "frame " n))))
  (testing "the order matters: translate then rotate would swing the Earth round its own spot"
    (is (not= (s3/compose (s3/translate 9.0 0.0 0.0) (s3/rotate-axis 45.0 0.0 1.0 0.0) (s3/rotate-axis 90.0 0.0 1.0 0.0))
              (sc/earth-transform (frames 90))))))

(deftest the-bodies-are-the-originals
  (is (= [255 203 0 255] sc/sun-colour) "GOLD")
  (is (= [0 121 241 255] sc/earth-colour) "BLUE")
  (is (= [200 200 200 255] sc/moon-colour) "LIGHTGRAY")
  (is (= [3.0 1.4 0.7] [sc/sun-size sc/earth-size sc/moon-size])))

(deftest each-body-is-drawn-where-its-transform-puts-it
  ;; At frame 90 the Earth orbit is 45 degrees and the Moon's 180, so by hand the
  ;; Earth stands at (9 cos 45, 0, -9 sin 45) and the Moon 2.6 from it along
  ;; the direction at 45 + 180 = 225 degrees.
  (let [dims (sc/dimensions {:screen [1206 2334]} measure)
        vp (s3/view-proj (sc/camera dims) (:viewport dims))
        rad (fn [d] (Math/toRadians d))
        earth [(* 9.0 (Math/cos (rad 45.0))) 0.0 (- (* 9.0 (Math/sin (rad 45.0))))]
        moon [(+ (nth earth 0) (* 2.6 (Math/cos (rad 225.0)))) 0.0
              (+ (nth earth 2) (- (* 2.6 (Math/sin (rad 225.0)))))]
        faces (tris (sc/scene-list (frames 90) dims))
        shades (fn [[r g b]] (set (map (fn [f] [(int (* f r)) (int (* f g)) (int (* f b))]) [1.0 0.85 0.7 0.5 0.4])))
        centre-of (fn [colour]
                    (let [mine (filter (fn [it] (contains? (shades colour) (subvec it 7 10))) faces)
                          pts (mapcat (fn [it] (partition 2 (subvec it 1 7))) mine)]
                      [(/ (reduce + (map first pts)) (count pts)) (/ (reduce + (map second pts)) (count pts))]))
        dist (fn [[ax ay] [bx by]] (Math/sqrt (+ (* (- ax bx) (- ax bx)) (* (- ay by) (- ay by)))))
        pe (vec (take 2 (s3/project vp earth)))
        pm (vec (take 2 (s3/project vp moon)))
        gap (dist pe pm)]
    (is (< 20.0 gap) "the Earth and the Moon are apart on the screen")
    (is (< (dist (centre-of sc/earth-colour) pe) (* 0.4 gap)) "the Earth's faces cluster on the Earth")
    (is (< (dist (centre-of sc/moon-colour) pm) (* 0.4 gap)) "the Moon's faces cluster on the Moon, not on the Earth")
    (is (< (dist (centre-of sc/sun-colour) (vec (take 2 (s3/project vp [0.0 0.0 0.0])))) (* 0.4 gap)) "and the Sun's on the Sun")))

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                dims (sc/dimensions metrics measure)
                dl (sc/scene-list (frames 0) dims)
                faces (tris dl)
                shades (fn [[r g b]] (set (map (fn [f] [(int (* f r)) (int (* f g)) (int (* f b)) 255]) [1.0 0.85 0.7 0.5 0.4])))
                of (fn [colour] (count (filter (fn [it] (contains? (shades colour) (subvec it 7 11))) faces)))]]
    (testing (str screen)
      (is (= 18 (count faces)) "from (16, 16, 16) each unturned cube shows its +x, +y and +z faces: 3 faces of 2 triangles, 3 cubes")
      (is (= (count faces) (count dl)) "no lines")
      (is (= [6 6 6] [(of sc/sun-colour) (of sc/earth-colour) (of sc/moon-colour)]) "6 triangles of each body's colour")))
  (testing "on the phone every corner stays inside the field through a whole year of the Moon's"
    (let [dims (sc/dimensions {:screen [1206 2334]} measure)]
      (is (every? (fn [n] (inside? (:viewport dims) (sc/scene-list (frames n) dims)))
                  (range 0 720 10))))))

(deftest the-camera-is-the-originals
  (let [c (sc/camera (sc/dimensions {:screen [800 450]} measure))]
    (is (= [16.0 16.0 16.0] (:position c)))
    (is (= [0.0 0.0 0.0] (:target c)))
    (is (= 45.0 (:fovy c)))))

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
