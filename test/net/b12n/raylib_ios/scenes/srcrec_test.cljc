(ns net.b12n.raylib-ios.scenes.srcrec-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.scenes.srcrec :as sc]
            [net.b12n.raylib-ios.texel :as texel]))

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

;; raylib-jlt's `rgba` (net/b12n/raylib/color.clj) and `sheet-pixel`
;; (srcrec_dstrec.clj lines 32-42), copied verbatim as the reference.
(defn- ref-rgba [r g b a]
  (bit-or (int r) (bit-shift-left (int g) 8)
          (bit-shift-left (int b) 16) (bit-shift-left (int a) 24)))

(def ref-colours
  [(ref-rgba 230 41 55 255) (ref-rgba 255 161 0 255) (ref-rgba 255 203 0 255)
   (ref-rgba 0 228 48 255) (ref-rgba 102 191 255 255) (ref-rgba 135 60 190 255)])

(defn- ref-sheet-pixel [x y]
  (let [frame (quot x 64)
        lx (- x (* frame 64))
        cx (/ 64 2.0)
        cy (/ 64 2.0)
        d (Math/sqrt (+ (Math/pow (- lx cx) 2) (Math/pow (- y cy) 2)))
        bg (nth ref-colours frame)]
    (if (< d (* 64 0.32)) (ref-rgba 245 245 245 255) bg)))

(defn- low32 [n] (bit-and n 0xFFFFFFFF))

(deftest the-sheet-is-the-originals
  (let [{:keys [w h pixel]} (sc/sheet-spec)]
    (is (= [384 64] [w h]))
    (is (= 24576 (count (for [y (range h)
                              x (range w)]
                          (is (= (low32 (ref-sheet-pixel x y)) (low32 (pixel x y))) (str [x y]))))))
    (testing "each frame has its own ground colour, and the disc is RAYWHITE"
      (is (= (mapv low32 ref-colours) (mapv (fn [f] (pixel (+ (* f 64) 2) 2)) (range 6))))
      (is (= 7 (count (set (for [y (range h)
                                 x (range w)]
                             (pixel x y)))))))
    (testing "the byte order is raylib-jlt's rgba"
      (is (= (low32 (ref-rgba 230 41 55 255)) (texel/pack [230 41 55 255]))))))

(deftest specs-obey-gles2
  (let [{:keys [w h wrap filter]} (sc/sheet-spec)]
    (testing "384 is not a power of two, so the sheet cannot repeat and must clamp"
      (is (not (zero? (bit-and w (dec w)))))
      (is (= :clamp wrap)))
    (is (= :nearest filter))
    (is (= 64 h))))

(deftest rotation-and-frame-follow-the-original
  (let [s (fresh)]
    (is (= 0.0 (:rotation s)))
    (testing "the rotation grows 1.0 degree a frame, clockwise, with no wrap"
      (is (= 10.0 (:rotation (nth (iterate tick s) 10))))
      (is (= 400.0 (:rotation (nth (iterate tick s) 400)))))
    (testing "FRAME-SHOWN 3 is the fourth frame: u from 192/384 to 256/384, v the whole height"
      (let [q (sc/quad s (sc/geometry metrics))]
        (is (= 3 sc/frame-shown))
        (is (= 0.5 (:u0 q)))
        (is (< (abs (- (/ 2.0 3.0) (:u1 q))) 1e-12))
        (is (= [0.0 1.0] [(:v0 q) (:v1 q)]))))
    (doseq [screen screens
            :let [[w h] screen
                  geo (sc/geometry {:screen screen})
                  q (sc/quad (nth (iterate tick s) 37) geo)]]
      (testing (str screen)
        (testing "the destination is a square at the middle, turning about its own middle"
          (is (= [(/ w 2.0) (/ h 2.0)] [(:x q) (:y q)]))
          (is (= (:width q) (:height q)))
          (is (= [(/ (:width q) 2.0) (/ (:height q) 2.0)] [(:origin-x q) (:origin-y q)])))
        (is (= 37.0 (:rotation q)))))))

(deftest first-frame-draws
  (is (= :srcrec (:id (sc/scene))))
  (is (= "Srcrec Dstrec" (:title (sc/scene))))
  (is (= "srcrec picks the frame, dstrec scales it, origin spins it" sc/text-line))
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                q (sc/quad (fresh) dims)
                reach (* (:side dims) 0.5 (Math/sqrt 2.0))]]
    (testing (str screen)
      (testing "the spinning square stays on the screen at every angle"
        (is (>= (- (:x q) reach) 0))
        (is (<= (+ (:x q) reach) w))
        (is (>= (- (:y q) reach) 0))
        (is (<= (+ (:y q) reach) h)))
      (testing "the crosshair crosses at the middle"
        (let [[vx1 vy1 vx2 vy2] (:vline dims)
              [hx1 hy1 hx2 hy2] (:hline dims)]
          (is (= (int (:cx dims)) vx1 vx2))
          (is (= (int (:cy dims)) hy1 hy2))
          (is (= [0 w] [hx1 hx2]))
          (is (= h vy2))
          (is (>= vy1 120) "the vertical line starts below Back"))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [lines]} (sc/dimensions {:screen screen} measure)]]
    (testing (str screen)
      (is (= 1 (count lines)))
      (doseq [{:keys [s x y size]} lines]
        (is (<= 0 x))
        (is (<= (+ x (measure s size)) w))
        (is (<= (+ y size) h))
        (is (>= y 120) "below Back")))))
