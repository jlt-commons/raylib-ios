(ns net.b12n.raylib-ios.scenes.wireframes-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.wireframes :as sc]
            [net.b12n.raylib-ios.soft3d :as s3]))

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

(defn- line-items [dl] (filterv (fn [it] (= :line (nth it 0))) dl))

(defn- inside? [[vx vy vw vh] dl]
  (every? (fn [it]
            (every? (fn [[x y]] (and (<= (- vx 1e-6) x (+ vx vw 1e-6)) (<= (- vy 1e-6) y (+ vy vh 1e-6))))
                    (partition 2 (subvec it 1 5))))
          (line-items dl)))

(deftest each-shape-has-the-originals-segment-count
  (testing "pyramid 8, octahedron 12, torus 14 x 7 x 2, helix 64"
    (is (= [:pyramid :octahedron :torus :helix] (mapv :id sc/shapes)))
    (is (= [8 12 196 64] (mapv (fn [sh] (count (:segments sh))) sc/shapes)))
    (is (= 280 (reduce + (map (fn [sh] (count (:segments sh))) sc/shapes)))))
  (testing "the shapes stand at -6, -2, 2 and 6 and keep their colours"
    (is (= [-6.0 -2.0 2.0 6.0] (mapv :x sc/shapes)))
    (is (= [[255 120 120 255] [120 200 255 255] [170 255 120 255] [255 220 120 255]]
           (mapv :colour sc/shapes))))
  (testing "the pyramid: a base of 1.3 either side at y -1 and an apex at (0, 1.6, 0)"
    (let [segs (:segments (first sc/shapes))]
      (is (= [[-1.3 -1.0 -1.3] [1.3 -1.0 -1.3]] (subvec (first segs) 0 2)))
      (is (= [[-1.3 -1.0 -1.3] [0.0 1.6 0.0]] (subvec (nth segs 4) 0 2)))))
  (testing "the torus is closed: 14 x 7 points, each reached from two segments"
    (let [segs (:segments (nth sc/shapes 2))
          ends (frequencies (mapcat (fn [[p q]] [(mapv #(Math/round (* 1e6 %)) p) (mapv #(Math/round (* 1e6 %)) q)]) segs))]
      (is (= 98 (count ends)))
      (is (every? #(= 4 %) (vals ends)))))
  (testing "the helix has 3 turns of radius 1.2 from y -1.3 to 1.3"
    (let [segs (:segments (nth sc/shapes 3))
          [_ [lx ly lz]] (last segs)]
      (is (near? -1.3 (second (first (first segs)))))
      (is (near? 1.3 ly))
      (is (near? 1.2 (Math/sqrt (+ (* lx lx) (* lz lz)))))))
  (testing "every shape tumbles 0.9 degrees a frame about (0.4, 1, 0.3), after its translate"
    (doseq [n [0 1 10 400]]
      (is (near? (* 0.9 n) (sc/spin (frames n))) (str "frame " n)))
    (is (= (s3/compose (s3/translate 2.0 0.0 0.0) (s3/rotate-axis 9.0 0.4 1.0 0.3))
           (sc/transform (frames 10) (nth sc/shapes 2))))
    (is (not= (s3/compose (s3/rotate-axis 9.0 0.4 1.0 0.3) (s3/translate 2.0 0.0 0.0))
              (sc/transform (frames 10) (nth sc/shapes 2))))))

(deftest lines-behind-the-camera-clip
  (let [dims (sc/dimensions {:screen [1206 2334]} measure)
        cam (sc/camera dims)
        finite? (fn [dl] (every? (fn [it] (every? (fn [v] (and (== v v) (< (abs v) 1e9))) (subvec it 1 5)))
                                 (line-items dl)))]
    (testing "from the original's camera nothing is clipped away"
      (is (= 280 (count (sc/draw-list cam (frames 0) dims)))))
    (testing "a camera turned away from every shape sees no line"
      (is (empty? (sc/draw-list (assoc cam :target [0.0 3.2 24.0]) (frames 0) dims))))
    (testing "a camera among the shapes keeps what is ahead, clipped, and drops what is behind"
      (let [among (assoc cam :position [0.0 0.3 0.4] :target [0.0 0.3 -10.0] :up [0.0 1.0 0.0])
            dl (sc/draw-list among (frames 0) dims)]
        (is (< 0 (count dl) 280))
        (is (finite? dl) "no line is wrapped through infinity")))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                dims (sc/dimensions metrics measure)
                dl (sc/scene-list (frames 0) dims)
                ls (line-items dl)
                by-colour (frequencies (map (fn [it] (subvec it 5 9)) ls))]]
    (testing (str screen)
      (is (= 280 (count dl) (count ls)) "all 280 edges, and nothing else")
      (is (= {[255 120 120 255] 8
              [120 200 255 255] 12
              [170 255 120 255] 196
              [255 220 120 255] 64}
             by-colour))
      (is (inside? (:viewport dims) dl) "every end lies inside the field")
      (is (every? (fn [n] (inside? (:viewport dims) (sc/scene-list (frames n) dims)))
                  (range 0 400 7))
          "the shapes stay inside the field through a spin")
      (is (some (fn [it] (> (abs (- (nth it 1) (nth it 3))) 1.0)) ls) "and the lines have some length"))))

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
