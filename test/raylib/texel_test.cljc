(ns raylib.texel-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.texel :as tx]))

(def ^:private on [255 0 0 255])
(def ^:private off [0 0 0 0])

(defn- lit
  "The set of [x y] whose texel differs from the grid's blank fill."
  [g]
  (let [blank (tx/pack off)
        px    (:px g)
        w     (:w g)]
    (set (for [i (range (count px))
               :when (not= blank (nth px i))]
           [(mod i w) (quot i w)]))))

(defn- blank [w h] (tx/grid w h off))

(deftest pack-is-rgba8-little-endian
  (is (= 0x04030201 (tx/pack [1 2 3 4])))
  (is (= 0xFFFFFFFF (tx/pack [255 255 255 255])))
  (doseq [c [[0 0 0 0] [1 2 3 4] [255 0 0 255] [12 200 99 128] [255 255 255 255]]]
    (is (= c (tx/unpack (tx/pack c))))))

(deftest grid-shape
  (let [g (tx/grid 3 2 [9 8 7 6])]
    (is (= 3 (:w g)))
    (is (= 2 (:h g)))
    (is (= 6 (count (:px g))))
    (is (every? #(= (tx/pack [9 8 7 6]) %) (:px g)))))

(deftest draw-pixel-clips
  (let [g (blank 3 3)]
    (is (= #{[1 2]} (lit (tx/draw-pixel g 1 2 on))))
    (is (= #{} (lit (tx/draw-pixel g -1 0 on))))
    (is (= #{} (lit (tx/draw-pixel g 3 0 on))))
    (is (= #{} (lit (tx/draw-pixel g 0 3 on))))))

;; Hand-worked from ImageDrawLine (rtextures.c:3491). The loop runs
;; i = 0 .. endVal exclusive, so the far endpoint is never lit.
(deftest draw-line-matches-imagedrawline
  (let [g (blank 8 8)]
    (testing "horizontal (0,0)->(4,0): 4 texels, endpoint excluded"
      (is (= #{[0 0] [1 0] [2 0] [3 0]}
             (lit (tx/draw-line g 0 0 4 0 on)))))
    (testing "vertical (1,0)->(1,3): shortLen/longLen swap, yLonger"
      (is (= #{[1 0] [1 1] [1 2]}
             (lit (tx/draw-line g 1 0 1 3 on)))))
    (testing "shallow (0,0)->(5,2): decInc = (2<<16)/5 = 26214,
              j>>16 over i=0..4 = 0 0 0 1 1"
      (is (= #{[0 0] [1 0] [2 0] [3 1] [4 1]}
             (lit (tx/draw-line g 0 0 5 2 on)))))
    (testing "steep (0,0)->(2,5): swapped, decInc 26214 over i=0..4, x = 0 0 0 1 1"
      (is (= #{[0 0] [0 1] [0 2] [1 3] [1 4]}
             (lit (tx/draw-line g 0 0 2 5 on)))))
    (testing "leftward (4,0)->(0,0): i = 0 -1 -2 -3, so x = 4 3 2 1"
      (is (= #{[4 0] [3 0] [2 0] [1 0]}
             (lit (tx/draw-line g 4 0 0 0 on)))))
    (testing "up-left (5,2)->(0,0): decInc = trunc(-131072/5) = -26214,
              j>>16 (arithmetic) over i=0..-4 = 0 -1 -1 -2 -2"
      (is (= #{[5 2] [4 1] [3 1] [2 0] [1 0]}
             (lit (tx/draw-line g 5 2 0 0 on)))))
    (testing "a zero-length line lights nothing (endVal is 0)"
      (is (= #{} (lit (tx/draw-line g 2 2 2 2 on)))))
    (testing "clips at the edge without throwing"
      (is (= #{[0 0] [1 0]}
             (lit (tx/draw-line (blank 2 2) -2 0 4 0 on)))))))

;; ImageDrawCircleV (rtextures.c:3635) calls ImageDrawCircle (3611), which
;; FILLS in 6.0, using ImageDrawRectangle(x - x, y, 2x, 1) spans. A span of
;; width 2x covers cx-x .. cx+x-1, so the disc is one texel short on the right.
(deftest draw-circle-matches-imagedrawcirclev
  (let [g (blank 9 9)
        at (fn [cx cy rows]
             (set (for [[dy xs] rows, dx xs] [(+ cx dx) (+ cy dy)])))]
    (testing "radius 0: every span has width 0, which still lights its first texel"
      (is (= #{[4 4]} (lit (tx/draw-circle g 4 4 0 on)))))
    (testing "radius 1: (0,1), (0,-1) from the width-0 spans, and the row
              dy=0 from the width-2 spans, dx -1..0"
      (is (= (at 4 4 {-1 [0],
                      0 [-1 0],
                      1 [0]})
             (lit (tx/draw-circle g 4 4 1 on)))))
    (testing "radius 3, worked through d = 3-2r = -3, 7, 17: rows dy -3..3"
      (is (= (at 4 4 {-3 [-1 0]
                      -2 (range -2 2)
                      -1 (range -3 3)
                      0  (range -3 3)
                      1  (range -3 3)
                      2  (range -2 2)
                      3  [-1 0]})
             (lit (tx/draw-circle g 4 4 3 on)))))))

;; Per-row [min max] of dx, hand-worked from the ImageDrawCircle walk (the
;; spans are contiguous). Radius 6 exercises both branches of the d update more
;; than r=3 does. Radius 21 is the smallest where a changed `+ 10` constant in
;; the y-decrement branch moves the outline (checked against r=1..20).
(def ^:private r6
  {-6 [-1 0]
   -5 [-3 2]
   -4 [-4 3]
   -3 [-5 4]
   -2 [-5 4]
   -1 [-6 5]
   0 [-6 5]
   1 [-6 5]
   2 [-5 4]
   3 [-5 4]
   4 [-4 3]
   5 [-3 2]
   6 [-1 0]})

(def ^:private r21
  (into {}
        (map vector (range -21 22)
             [[-3 2] [-6 5] [-8 7] [-10 9] [-11 10] [-12 11] [-13 12] [-14 13]
              [-15 14] [-16 15] [-17 16] [-18 17] [-18 17] [-19 18] [-19 18]
              [-20 19] [-20 19] [-20 19] [-21 20] [-21 20] [-21 20] [-21 20]
              [-21 20] [-21 20] [-21 20] [-20 19] [-20 19] [-20 19] [-19 18]
              [-19 18] [-18 17] [-18 17] [-17 16] [-16 15] [-15 14] [-14 13]
              [-13 12] [-12 11] [-11 10] [-10 9] [-8 7] [-6 5] [-3 2]])))

(defn- disc [cx cy rows]
  (set (for [[dy [lo hi]] rows, dx (range lo (inc hi))] [(+ cx dx) (+ cy dy)])))

(deftest draw-circle-larger-radii
  (is (= (disc 8 8 r6) (lit (tx/draw-circle (blank 17 17) 8 8 6 on))))
  (is (= (disc 24 24 r21) (lit (tx/draw-circle (blank 49 49) 24 24 21 on)))))

;; ImageDrawRectangleRec (rtextures.c:3687). A rect clipped to width 0 still
;; draws its first texel, as the C does.
(deftest draw-rect-clips-at-the-edges
  (let [g (blank 4 4)]
    (testing "inside"
      (is (= #{[1 1] [2 1] [1 2] [2 2]}
             (lit (tx/draw-rect g 1 1 2 2 on)))))
    (testing "half outside the left and top: x=-2,w=4 becomes x=0,w=2"
      (is (= #{[0 0] [1 0] [0 1] [1 1]}
             (lit (tx/draw-rect g -2 -2 4 4 on)))))
    (testing "half outside the right and bottom: w clamps to 4-2"
      (is (= #{[2 2] [3 2] [2 3] [3 3]}
             (lit (tx/draw-rect g 2 2 4 4 on)))))
    (testing "wholly outside lights nothing"
      (is (= #{} (lit (tx/draw-rect g 4 0 2 2 on))))
      (is (= #{} (lit (tx/draw-rect g -3 0 3 2 on))))
      (is (= #{} (lit (tx/draw-rect g 0 -2 2 2 on)))))
    ;; rtextures.c:3708 draws the first texel, then 3719-3722 doubles it along
    ;; the row to (int)rec.width texels regardless of height, and 3726-3729
    ;; copies that row down for y = 1 .. height-1 (width texels each).
    (testing "zero height still lights a width-wide row: (0,2,3,0) -> x 0..2"
      (is (= #{[0 2] [1 2] [2 2]} (lit (tx/draw-rect g 0 2 3 0 on)))))
    (testing "zero height clamped by the edge: (1,3,4,0), w 4 -> 3, row y=3"
      (is (= #{[1 3] [2 3] [3 3]} (lit (tx/draw-rect g 1 3 4 0 on)))))
    (testing "zero width lights the first texel only: row loop needs w>1 and
              the row copy moves 0 bytes"
      (is (= #{[1 1]} (lit (tx/draw-rect g 1 1 0 3 on)))))
    (testing "the early return still fires: x+w <= 0 or y+h <= 0 lights nothing"
      (is (= #{} (lit (tx/draw-rect g 0 1 0 2 on))))
      (is (= #{} (lit (tx/draw-rect g 1 0 2 0 on)))))))

(deftest pixel-of-reads-the-grid
  (let [g (-> (blank 3 2)
              (tx/draw-pixel 2 1 [1 2 3 4])
              (tx/draw-pixel 0 0 [5 6 7 8]))
        f (tx/pixel-of g)]
    (is (= (tx/pack [1 2 3 4]) (f 2 1)))
    (is (= (tx/pack [5 6 7 8]) (f 0 0)))
    (is (= (tx/pack off) (f 1 0)))))

(deftest pack4-is-pack-without-the-vector
  (is (= 0x04030201 (tx/pack4 1 2 3 4)))
  (doseq [c [[0 0 0 0] [255 255 255 255] [1 2 3 4] [255 0 128 7]]]
    (is (= (tx/pack c) (apply tx/pack4 c)))))

(deftest transient-twins-paint-what-the-persistent-fns-paint
  (let [g0 (tx/grid 40 30 [1 2 3 4])
        red [255 0 0 255]
        blue [0 0 255 255]
        persistent (-> g0
                       (tx/draw-pixel 3 4 red)
                       (tx/draw-pixel -1 4 red)
                       (tx/draw-line 2 2 30 20 blue)
                       (tx/draw-line 35 3 5 25 red)
                       (tx/draw-rect -3 -2 12 9 blue)
                       (tx/draw-rect 30 20 40 40 red)
                       (tx/draw-circle 20 15 9 blue)
                       (tx/draw-circle 2 2 6 red))
        transient (-> (tx/transient-grid g0)
                      (tx/draw-pixel! 3 4 red)
                      (tx/draw-pixel! -1 4 red)
                      (tx/draw-line! 2 2 30 20 blue)
                      (tx/draw-line! 35 3 5 25 red)
                      (tx/draw-rect! -3 -2 12 9 blue)
                      (tx/draw-rect! 30 20 40 40 red)
                      (tx/draw-circle! 20 15 9 blue)
                      (tx/draw-circle! 2 2 6 red)
                      tx/persistent-grid)]
    (is (= persistent transient))
    (is (not= (:px g0) (:px persistent)) "the sequence did paint something")
    (testing "the persistent wrappers leave their argument alone"
      (is (= (tx/grid 40 30 [1 2 3 4]) g0)))))
