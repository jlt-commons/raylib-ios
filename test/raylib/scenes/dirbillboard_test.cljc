(ns raylib.scenes.dirbillboard-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.dirbillboard :as sc]
            [raylib.soft3d :as s3]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(def metrics {:screen [1206 2334]})

(defn- tick [state dt]
  (first ((:update (sc/scene)) state {:metrics metrics
                                      :pointer {:phase :idle}
                                      :delta-seconds dt})))

(defn- start [] (first ((:init (sc/scene)) {:metrics metrics})))

(defn- tris [dl] (filterv (fn [it] (= :tri (nth it 0))) dl))

;; --- the original's row choice, directional_billboard.clj 125-128, as an oracle

(defn- original-dir [theta]
  (let [px (* 2.8284271247461903 (Math/cos theta))
        pz (* 2.8284271247461903 (Math/sin theta))
        dir0 (Math/floor (+ (* (/ (Math/atan2 pz px) Math/PI) 4.0) 0.25))]
    (int (if (< dir0 0.0) (+ 8.0 dir0) dir0))))

(deftest the-pose-follows-the-view-angle-as-the-original
  (testing "read by hand: the row is floor(atan2(z, x) / pi * 4 + 1/4), plus 8 when it is negative"
    (is (= 0 (sc/direction 0.0)) "0 gives 0.25")
    (is (= 1 (sc/direction (/ Math/PI 4.0))) "the start: 1.25")
    (is (= 0 (sc/direction -0.1)) "-0.127 + 0.25 = 0.123")
    (is (= 7 (sc/direction -0.3)) "-0.382 + 0.25 = -0.132 is -1, then 7")
    (is (= 0 (sc/direction (+ (- (/ Math/PI 16.0)) 0.01))) "row 0 starts at -pi / 16: 0.013")
    (is (= 7 (sc/direction (- (- (/ Math/PI 16.0)) 0.01))) "and just below it, -0.0125 is -1, then 7")
    (is (= 0 (sc/direction (- (* 3 (/ Math/PI 16.0)) 0.001))) "row 0 ends at 3 pi / 16: 0.9987")
    (is (= 1 (sc/direction (+ (* 3 (/ Math/PI 16.0)) 0.001))) "and row 1 starts there: 1.0013")
    (is (= 4 (sc/direction Math/PI)) "atan2 gives pi: 4.25")
    (is (= 4 (sc/direction (+ (- Math/PI) 0.01))) "just past -pi: -4 + 8")
    (is (= 1 (sc/direction (+ (* 2 Math/PI) (/ Math/PI 4.0)))) "theta grows without bound; atan2 wraps it"))
  (testing "it is the original's row at every sampled angle, and always one of eight"
    (doseq [k (range -400 2000)
            :let [theta (* k 0.0173)
                  d (sc/direction theta)]]
      (is (= (original-dir theta) d) (str theta))
      (is (<= 0 d 7) (str theta))))
  (testing "all eight rows turn up in one orbit, in order as the camera goes round"
    (let [rows (mapv sc/direction (map #(* % (/ Math/PI 16.0)) (range 0 32)))]
      (is (= (set (range 8)) (set rows)))
      (is (= [0 0 0] (subvec rows 0 3)) "0.25, 0.5 and 0.75 are all below 1")
      (is (= [1 2 3] (mapv rows [4 8 12])) "pi / 4, pi / 2 and 3 pi / 4 give 1.25, 2.25 and 3.25")))
  (testing "the state's row is that of its theta"
    (let [s (start)]
      (is (= 1 (sc/dir s)))
      (is (= (original-dir (:theta (tick s 1.0))) (sc/dir (tick s 1.0)))))))

(deftest the-orbit-and-the-walk-tick-as-the-original
  (testing "theta starts at pi / 4 and goes 0.5 radians a second"
    (is (near? (/ Math/PI 4.0) (:theta (start))))
    (is (near? (+ (/ Math/PI 4.0) 0.5) (:theta (tick (start) 1.0))))
    (is (near? (/ Math/PI 4.0) (:theta (tick (start) 0.0)))))
  (testing "the walk frame advances when the timer passes 0.5 s, not at it, and wraps at 4"
    (let [s0 (start)
          s1 (tick s0 0.25)
          s2 (tick s1 0.25)
          s3 (tick s2 0.01)]
      (is (= 0 (:anim s1)))
      (is (near? 0.25 (:timer s1)))
      (is (= 0 (:anim s2)) "0.5 is not more than 0.5")
      (is (= 1 (:anim s3)))
      (is (near? 0.0 (:timer s3)) "and the timer starts over, losing the 0.01 as the original does"))
    (is (= [0 1 2 3 0 1]
           (mapv :anim (reductions (fn [s _] (tick s 0.6)) (start) (range 5)))))))

;; --- the sheet cell: directional_billboard.clj 49-69 as an oracle

(defn- original-kind
  "What the original's `sheet-pixel` paints at cell pixel (lx, ly) of column
  `col`: :head, :body, :leg or nil for transparent."
  [col lx ly]
  (let [offset (nth [0 2 0 -2] (mod col 4))
        head-dx (- lx 12) head-dy (- ly 8)
        head-r (Math/sqrt (+ (* head-dx head-dx) (* head-dy head-dy)))]
    (cond
      (< head-r 5) :head
      (and (<= 9 lx) (< lx 15) (<= 13 ly) (< ly 20)) :body
      (and (<= (+ 10 offset) lx) (< lx (+ 12 offset)) (<= 20 ly) (< ly 23)) :leg
      (and (<= (- 12 offset) lx) (< lx (- 14 offset)) (<= 20 ly) (< ly 23)) :leg
      :else nil)))

(deftest the-hue-is-the-originals
  (testing "hsv->color at saturation 0.7, read by hand: h' = hue / 60, p = v 0.3"
    (is (= [255 76 76 255] (sc/hsv->colour 0.0 0.7 1.0)) "hue 0: (v, t, p) = (1, 0.3, 0.3)")
    (is (= [76 255 255 255] (sc/hsv->colour 180.0 0.7 1.0)) "hue 180: (p, q, v) = (0.3, 1, 1)")
    (is (= [255 76 210 255] (sc/hsv->colour 315.0 0.7 1.0)) "hue 315: (v, p, q) = (1, 0.3, 0.825)")
    (is (= [255 210 76 255] (sc/hsv->colour 45.0 0.7 1.0)) "hue 45: (v, t, p) = (1, 0.825, 0.3)")))

(deftest the-figure-is-the-originals-sheet-cell
  (testing "head, body and legs are tinted as `sheet-pixel`: hue 45 a row, value 1, 0.7 and 0.5"
    (let [rects (sc/cell-rects 1 0)
          colours (mapv last rects)]
      (is (= 8 (count rects)) "two legs, the body and a head of five rows of rectangles")
      (is (= [255 210 76 255] (last colours)) "the head, painted last")
      (is (= [255 210 76 255] (nth colours 3)))
      (is (= [178 147 53 255] (nth colours 2)) "the body: hue 45, v 0.7")
      (is (= [127 105 38 255] (first colours)) "a leg: hue 45, v 0.5")
      (is (= 5 (count (filter #{[255 210 76 255]} colours))) "the head is five rectangles")))
  (testing "painted in order on a 24 by 24 cell, the rectangles are the original's cell, for every row and column"
    (doseq [dir (range 8) anim (range 4)
            :let [rects (sc/cell-rects dir anim)
                  paint (fn [lx ly]
                          (reduce (fn [acc [x0 y0 x1 y1 colour]]
                                    (if (and (<= x0 lx) (< lx x1) (<= y0 ly) (< ly y1)) colour acc))
                                  nil rects))
                  head (sc/hsv->colour (* dir 45.0) 0.7 1.0)
                  body (sc/hsv->colour (* dir 45.0) 0.7 0.7)
                  leg (sc/hsv->colour (* dir 45.0) 0.7 0.5)]]
      (doseq [lx (range 24) ly (range 24)
              :let [kind (original-kind anim lx ly)
                    got (paint lx ly)]]
        (case kind
          :head (is (= head got) (str dir "," anim " head " lx "," ly))
          :body (is (= body got) (str dir "," anim " body " lx "," ly))
          :leg (is (= leg got) (str dir "," anim " leg " lx "," ly))
          nil (is (nil? got) (str dir "," anim " clear " lx "," ly " got " got)))))))

(deftest the-figure-faces-as-the-originals-quad-does
  (testing "a cell rectangle lands where the original's texel mapping puts it
            (tl, tr, br, bl take u, v = (0,0) (1,0) (1,1) (0,1)), which mirrors the sheet left to right"
    (doseq [theta [0.3 1.0 2.5 4.0]
            [lx0 ly0 lx1 ly1] [[0 0 24 24] [8 4 17 13] [9 13 15 20] [10 20 12 23] [12 20 14 23]]
            :let [s (assoc (start) :theta theta)
                  dims (sc/dimensions metrics measure)
                  cam (sc/camera s dims)
                  {:keys [position target]} cam
                  forward (let [d (mapv - target position) l (Math/sqrt (reduce + (map * d d)))] (mapv #(/ % l) d))
                  cross (fn [[ax ay az] [bx by bz]]
                          [(- (* ay bz) (* az by)) (- (* az bx) (* ax bz)) (- (* ax by) (* ay bx))])
                  norm (fn [v] (let [l (Math/sqrt (reduce + (map * v v)))] (mapv #(/ % l) v)))
                  right (norm (cross [0.0 1.0 0.0] forward))
                  up (cross forward right)
                  centre [0.0 0.5 0.0]
                  ;; the original: tl = c - right h + up h has (u, v) = (0, 0), tr (1, 0), bl (0, 1),
                  ;; so with h = 0.5 a texel (u, v) is c + right (u - 0.5) + up (0.5 - v)
                  texel (fn [lx ly]
                          (let [u (/ lx 24.0) v (/ ly 24.0)]
                            (mapv (fn [c r q] (+ c (* (- u 0.5) r 1.0) (* (- 0.5 v) q 1.0)))
                                  centre right up)))
                  want [(texel lx0 ly1) (texel lx1 ly1) (texel lx1 ly0) (texel lx0 ly0)]
                  got (s3/billboard-corners cam centre 1.0 {:part (sc/cell->part [lx0 ly0 lx1 ly1])})]]
      (is (every? (fn [p] (some (fn [q] (every? true? (map near? p q))) want)) got)
          (str theta " " [lx0 ly0 lx1 ly1])))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [m {:screen screen}
                s (start)
                dims (sc/dimensions m measure)
                [vx vy vw vh] (:viewport dims)
                dl (sc/scene-list s dims)
                ts (tris dl)]]
    (testing (str screen)
      (is (= 22 (count (remove (fn [it] (= :tri (nth it 0))) dl))) "the grid of 10")
      (is (= 16 (count ts)) "eight rectangles, two triangles each")
      (is (= (concat (repeat 4 [127 105 38 255]) (repeat 2 [178 147 53 255]) (repeat 10 [255 210 76 255]))
             (mapv #(subvec % 7 11) ts))
          "row 1 at the start: two legs, the body, the head's five rectangles")
      (is (every? (fn [[_ & more]]
                    (every? (fn [[x y]] (and (<= vx x (+ vx vw)) (<= vy y (+ vy vh))))
                            (partition 2 (take 6 more))))
                  ts)
          "every corner is inside the field")
      (is (every? (fn [n]
                    (let [s (assoc (start) :theta (* n 0.21) :anim (mod n 4))
                          t (tris (sc/scene-list s dims))]
                      (= 16 (count t))))
                  (range 0 90))
          "the figure is whole from every side and in every walk frame")))
  (testing "the caption reads the walk frame and the row"
    (let [dims (sc/dimensions metrics measure)]
      (is (= "animation: 0  direction frame: 1" (:s (sc/caption (start) dims))))
      (is (= "animation: 3  direction frame: 7"
             (:s (sc/caption (assoc (start) :anim 3 :theta -0.3) dims)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [_ fy _ fh] (:viewport dims)
                lines (mapv #(sc/caption (assoc (start) :anim % :theta (* % 0.7)) dims) (range 4))]]
    (testing (str screen)
      (doseq [{:keys [s x y size]} (conj lines (sc/caption (assoc (start) :anim 3 :theta -0.3) dims))]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) fy) "the caption sits above the field"))
      (is (>= fy (+ back-y back-h)) "the field is below Back")
      (is (near? h (+ fy fh)) "and runs to the bottom"))))
