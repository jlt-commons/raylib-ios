(ns raylib.scenes.fbrender-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.fbrender :as sc]
            [raylib.soft3d :as s3]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def metrics {:screen [1206 2334]})

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))
(defn- fresh [] (first ((:init (sc/scene)) {:metrics metrics})))
(defn- tick [state]
  (first ((:update (sc/scene)) state {:metrics metrics})))

(deftest two-halves-and-the-crop
  (testing "the constants the brief names"
    (is (= 128 sc/crop))
    (is (= 45.0 sc/fovy))
    (is (= 3.0 sc/frustum-depth))
    (is (= [400 450] [sc/original-half-w sc/original-half-h])))
  (testing "portrait stacks the halves, each the field's full width and half its height"
    (let [dims (sc/dimensions {:screen [1206 2334]} measure)
          [_ back-y _ back-h] gesture/back-region
          top (+ back-y back-h)
          [o s] (:halves dims)]
      (is (:portrait? dims))
      (is (= [1206 (quot (- 2334 top) 2)] [(nth o 2) (nth o 3)]))
      (is (= (subvec o 2) (subvec s 2)) "both halves are the same size")
      (is (= [0 top] (subvec o 0 2)))
      (is (= [0 (+ top (nth o 3))] (subvec s 0 2)) "the subject view is under the observer")))
  (testing "landscape puts them side by side, each half the field's width and all its height"
    (let [dims (sc/dimensions {:screen [2334 1206]} measure)
          [_ back-y _ back-h] gesture/back-region
          top (+ back-y back-h)
          [o s] (:halves dims)]
      (is (not (:portrait? dims)))
      (is (= [1167 (- 1206 top)] [(nth o 2) (nth o 3)]))
      (is (= (subvec o 2) (subvec s 2)))
      (is (= [0 top] (subvec o 0 2)))
      (is (= [1167 top] (subvec s 0 2)))))
  (testing "the original's 800x450 window is two halves of 400 by its height"
    (let [dims (sc/dimensions {:screen [800 450]} measure)
          [_ back-y _ back-h] gesture/back-region
          [o _] (:halves dims)]
      (is (= [400 (- 450 back-y back-h)] [(nth o 2) (nth o 3)]))))
  (testing "the divider runs between the halves, the length of the field's other side"
    (doseq [screen screens
            :let [dims (sc/dimensions {:screen screen} measure)
                  [x1 y1 x2 y2] (:divider dims)
                  [o s] (:halves dims)]]
      (testing (str screen)
        (if (:portrait? dims)
          (do (is (= [0 (nth s 1) (first screen) (nth s 1)] [x1 y1 x2 y2]))
              (is (= (+ (nth o 1) (nth o 3)) (nth s 1)) "the halves meet at the divider"))
          (do (is (= [(nth s 0) (nth s 1) (nth s 0) (+ (nth s 1) (nth s 3))] [x1 y1 x2 y2]))
              (is (= (+ (nth o 0) (nth o 2)) (nth s 0)) "the halves meet at the divider"))))))
  (testing "the crop square: centred in the subject view, the inset 20 in from its corner"
    (doseq [screen screens
            :let [dims (sc/dimensions {:screen screen} measure)
                  [_ _ hw hh] (first (:halves dims))
                  {:keys [crop-rect inset-rect inset-uv]} dims
                  [cx cy cw ch] crop-rect
                  [ix iy iw ih] inset-rect]]
      (testing (str screen)
        (is (= [128 128] [cw ch]))
        (is (= [(quot (- hw 128) 2) (quot (- hh 128) 2)] [cx cy]))
        (is (= [20 20 128 128] [ix iy iw ih]))
        (testing "the inset samples the same square, flipped for the bottom-up texture"
          (let [[u0 v0 u1 v1] inset-uv]
            (is (near? (/ cx (double hw)) u0))
            (is (near? (/ (+ cx 128) (double hw)) u1))
            (is (near? (- 1.0 (/ cy (double hh))) v0))
            (is (near? (- 1.0 (/ (+ cy 128) (double hh))) v1))
            (is (> v0 v1)))))))
  (testing "against the original's 400x450 half, the crop's uv are the original's"
    (let [u0 (/ (- 400 128) 2.0 400) u1 (/ (+ 400 128) 2.0 400)
          v0 (/ (+ 450 128) 2.0 450) v1 (/ (- 450 128) 2.0 450)
          [ru0 rv0 ru1 rv1] (sc/crop-uv 400 450)]
      (is (near? u0 ru0))
      (is (near? u1 ru1))
      (is (near? v0 rv0))
      (is (near? v1 rv1)))))

(deftest the-observer-and-subject-cameras
  (let [orbit-ref (fn [radius height speed n]
                    (let [a (* n speed)]
                      [(* radius (Math/cos a)) height (* radius (Math/sin a))]))]
    (doseq [n [0 1 60 500 4000]]
      (testing (str "frame " n)
        (is (every? true? (map near? (orbit-ref 5.0 2.0 0.012 n) (sc/subject-position n))))
        (is (every? true? (map near? (orbit-ref 14.0 10.0 0.004 n) (sc/observer-position n))))
        (doseq [pos [(sc/subject-position n) (sc/observer-position n)]
                :let [cam (sc/camera pos)]]
          (is (= [0.0 0.0 0.0] (:target cam)))
          (is (= [0.0 1.0 0.0] (:up cam)))
          (is (= 45.0 (:fovy cam)))
          (is (= :perspective (:projection cam)))))))
  (testing "the state counts frames, the original's `frame`"
    (is (= 0 (:frame (fresh))))
    (is (= 25 (:frame (nth (iterate tick (fresh)) 25))))
    (is (= (sc/subject-position 25) (:position (sc/subject-camera (nth (iterate tick (fresh)) 25))))))
  (testing "the frustum is built from the subject camera's own numbers"
    (let [pos (sc/subject-position 0)
          aspect (/ 400.0 450.0)
          corners (sc/frustum-corners pos [0.0 0.0 0.0] aspect)
          sub (fn [[ax ay az] [bx by bz]] [(- ax bx) (- ay by) (- az bz)])
          len (fn [[x y z]] (Math/sqrt (+ (* x x) (* y y) (* z z))))
          centre (mapv (fn [i] (/ (reduce + (map #(nth % i) corners)) 4.0)) (range 3))]
      (is (= 4 (count corners)))
      (testing "the centre of the far plane is FRUSTUM-DEPTH along the look"
        (is (near? 3.0 (len (sub centre pos)))))
      (testing "the far plane's half-height is depth * tan(fovy/2)"
        (let [[a _ _ d] corners]
          (is (near? (* 2.0 3.0 (Math/tan (Math/toRadians 22.5))) (len (sub a d))))))
      (testing "and its width is aspect times that"
        (let [[a b] corners
              [_ _ _ d] corners]
          (is (near? (* aspect (len (sub a d))) (len (sub a b))))))))
  (testing "the observer draws the subject's prism, eight green segments"
    (let [segs (sc/prism-segments (sc/subject-position 0) 1.0)]
      (is (= 8 (count segs)))
      (is (every? #(= [0 228 48 255] (nth % 2)) segs)))))

(deftest first-frame-draws
  (is (= :fbrender (:id (sc/scene))))
  (is (= "Framebuffer Rendering" (:title (sc/scene))))
  (doseq [screen screens
          :let [dims (sc/dimensions {:screen screen} measure)
                [_ _ hw hh] (first (:halves dims))
                ;; The grid runs past the view by design (the target's viewport
                ;; clips it), so only the cube's faces and wires and the prism
                ;; must lie inside.
                inside? (fn [dl]
                          (every? (fn [it]
                                    (let [pts (if (= :tri (nth it 0))
                                                (partition 2 (subvec it 1 7))
                                                (partition 2 (subvec it 1 5)))]
                                      (every? (fn [[x y]] (and (<= -1 x (+ hw 1)) (<= -1 y (+ hh 1)))) pts)))
                                  (remove (fn [it] (and (= :line (nth it 0))
                                                        (not (#{[255 109 194 255] [0 228 48 255]} (subvec it 5 9)))))
                                          dl)))]]
    (testing (str screen)
      (let [obs (sc/observer-list (fresh) dims)
            sub (sc/subject-list (fresh) dims)
            kinds (fn [dl k] (count (filter #(= k (nth % 0)) dl)))]
        (is (seq obs))
        (is (seq sub))
        (testing "the observer: the grid's 22 lines, the cube's faces and wires, the prism's 8 lines"
          (is (<= 2 (kinds obs :tri) 6))
          (is (<= 8 (kinds obs :line) (+ 22 8 12))))
        (testing "the subject: the grid, the cube and the wires, no prism"
          (is (<= 2 (kinds sub :tri) 6))
          (is (<= 1 (kinds sub :line) (+ 22 12))))
        (testing "both stay inside their own target through an orbit"
          (doseq [n (range 0 1600 97)
                  :let [st (assoc (fresh) :frame n)]]
            (is (inside? (sc/observer-list st dims)))
            (is (inside? (sc/subject-list st dims)))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                halves (:halves dims)]]
    (testing (str screen)
      (doseq [[half ls n] (map vector halves [(:observer-lines dims) (:subject-lines dims)] [2 1])
              :let [[hx hy hw hh] half]]
        (is (= n (count ls)) "the observer has a note and a label, the subject a label")
        (doseq [{:keys [s x y size]} ls]
          (is (<= 0 x))
          (is (<= (+ x (measure s size)) hw) s)
          (is (<= (+ y size) hh) s)
          (is (<= (+ hx x (measure s size)) w) s)
          (is (<= (+ hy y size) h) s)
          (is (>= (+ hy y) (+ back-y back-h)) "below Back"))))))

(deftest the-world-is-the-originals
  (testing "a gold cube of side 2 under pink wires, on a grid of 10 spaced 1"
    (let [dims (sc/dimensions metrics measure)
          dl (sc/subject-list (fresh) dims)
          tris (filter #(= :tri (nth % 0)) dl)]
      (is (every? #(= [255 203 0 255] (subvec % 7 11)) tris))
      (is (<= 11 (count (filter #(and (= :line (nth % 0)) (not= [255 109 194 255] (subvec % 5 9))) dl)) 22)
          "the grid is 11 lines each way, fewer only where the camera drops one wholly behind it")
      (is (seq (filter #(and (= :line (nth % 0)) (= [255 109 194 255] (subvec % 5 9))) dl))
          "the pink wires"))))

(def green [0 228 48 255])

(defn- green-lines [dl] (filter #(and (= :line (nth % 0)) (= green (subvec % 5 9))) dl))

(deftest the-views-are-wired-to-their-cameras
  (let [dims (sc/dimensions metrics measure)
        [_ _ hw hh] (first (:halves dims))
        vp-of (fn [cam] (s3/view-proj cam [0 0 hw hh]))
        at (fn [n] (assoc (fresh) :frame n))]
    (doseq [n [0 37 800 2500]
            :let [st (at n)
                  svp (vp-of (sc/subject-camera st))
                  ovp (vp-of (sc/observer-camera st))
                  obs (sc/observer-list st dims)
                  sub (sc/subject-list st dims)
                  prism (sc/prism-for st dims)]]
      (testing (str "frame " n)
        (testing "the subject view is the world seen by the subject camera, with no prism"
          (is (seq sub))
          (is (= (s3/finish (sc/world [] svp)) sub))
          (is (empty? (green-lines sub))))
        (testing "the observer view is the world seen by the observer camera plus the prism"
          (is (= (s3/finish (sc/world [] ovp)) (remove (set (green-lines obs)) obs)))
          (is (not= (s3/finish (sc/world [] svp)) (remove (set (green-lines obs)) obs))
              "and not by the subject camera"))
        (testing "the prism is there: eight green segments"
          (is (= 8 (count (green-lines obs))))
          (is (= 8 (count prism))))
        (testing "and they are the prism for this frame, projected by the observer camera"
          (let [pt (fn [p] (let [[x y] (s3/project ovp p)] [(Math/round (* 1000.0 x)) (Math/round (* 1000.0 y))]))
                ends (fn [[x1 y1 x2 y2]] #{[(Math/round (* 1000.0 x1)) (Math/round (* 1000.0 y1))]
                                           [(Math/round (* 1000.0 x2)) (Math/round (* 1000.0 y2))]})]
            (is (= (set (map (fn [[a b _]] #{(pt a) (pt b)}) prism))
                   (set (map #(ends (subvec % 1 5)) (green-lines obs)))))))
        (testing "the prism's far rectangle fills the subject view's own target"
          (let [[_ _ _ _ :as far] (map second (take 4 prism))
                pts (map #(s3/project svp %) far)]
            (is (every? some? pts))
            (is (every? true? (map (fn [[x y] [ex ey]] (and (near? x ex) (< (abs (- y ey)) 1e-6)))
                                   pts [[0 0] [hw 0] [hw hh] [0 hh]])))))
        (testing "all four edges leave the subject camera's position"
          (is (every? #(= (sc/subject-position n) (first %)) (take 4 prism))))))))

(deftest the-prism-hides-behind-the-cube-by-midpoint
  (let [dims (sc/dimensions metrics measure)
        layers (fn [n] (let [st (assoc (fresh) :frame n)]
                         (sc/prism-layers (sc/prism-for st dims) (sc/observer-position n))))
        frames (range 0 3000 7)
        mixed (first (filter #(let [l (layers %)] (and (seq (:under l)) (seq (:over l)))) frames))
        all-over (first (filter #(empty? (:under (layers %))) frames))
        all-under (first (filter #(empty? (:over (layers %))) frames))
        d2 (fn [[ax ay az] [bx by bz]] (+ (* (- ax bx) (- ax bx)) (* (- ay by) (- ay by)) (* (- az bz) (- az bz))))
        mid (fn [[a b _]] (mapv #(* 0.5 (+ %1 %2)) a b))]
    (is (some? mixed) "some frame has segments both behind and in front of the cube")
    (is (some? all-over) "some frame has the whole prism in front")
    (is (some? all-under) "some frame has the whole prism behind")
    (doseq [n [mixed all-over all-under]
            :when n
            :let [{:keys [under over]} (layers n)
                  o (sc/observer-position n)
                  limit (d2 o [0.0 0.0 0.0])
                  obs (sc/observer-list (assoc (fresh) :frame n) dims)
                  layer-of (fn [k] (count (filter #(= k (nth % 9)) (green-lines obs))))]]
      (testing (str "frame " n)
        (is (= 8 (+ (count under) (count over))))
        (is (every? #(< (d2 o (mid %)) limit) over) "over: nearer the observer than the cube")
        (is (every? #(>= (d2 o (mid %)) limit) under) "under: not nearer")
        (is (= (count under) (layer-of :under)) "the under ones are painted under the faces")
        (is (= (count over) (layer-of :over)))))
    (testing "the under lines come before every face in the finished list, the over ones after"
      (let [obs (sc/observer-list (assoc (fresh) :frame mixed) dims)
            idx (fn [pred] (keep-indexed (fn [i it] (when (pred it) i)) obs))
            tri-i (idx #(= :tri (nth % 0)))
            under-i (idx #(and (= :line (nth % 0)) (= green (subvec % 5 9)) (= :under (nth % 9))))
            over-i (idx #(and (= :line (nth % 0)) (= green (subvec % 5 9)) (= :over (nth % 9))))]
        (is (seq tri-i))
        (is (< (apply max under-i) (apply min tri-i)))
        (is (> (apply min over-i) (apply max tri-i)))))))
