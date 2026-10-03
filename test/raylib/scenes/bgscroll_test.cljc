(ns raylib.scenes.bgscroll-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.bgscroll :as sc]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def unit {:screen [800 450]})

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- fresh [metrics] (first ((:init (sc/scene)) {:metrics metrics})))

(defn- tick [state metrics]
  (first ((:update (sc/scene)) state {:metrics metrics})))

(defn- run [state metrics n]
  (nth (iterate #(tick % metrics) state) n))

(defn- near? [a b] (< (abs (double (- a b))) 1e-6))

(defn- rects
  "Every rect `emit-layers!` draws for `state`, as [x y w h colour]."
  [state metrics]
  (let [out (atom [])]
    (sc/emit-layers! (fn [x y w h colour] (swap! out conj [x y w h colour]))
                     state
                     (sc/geometry metrics))
    @out))

;; The original's texture, transcribed from background_scrolling.clj lines
;; 22-39 (hash01 and skyline-pixel), as a predicate: is texel (x, y) opaque?
(defn- hash01 [n]
  (let [h (bit-and (* (+ n 12345) 2654435761) 0xffffffff)]
    (/ (double (bit-and h 0xffff)) 65536.0)))

(defn- opaque? [seed horizon-frac col-w x y]
  (let [horizon (int (* 115 (- 1.0 horizon-frac)))]
    (and (>= y horizon)
         (let [col (quot x col-w)
               gap? (< (mod x col-w) (int (* col-w 0.15)))
               span (- 115 horizon)
               top (int (+ horizon (* span 0.6 (hash01 (+ seed col)))))]
           (not (or gap? (< y top)))))))

(def originals
  "The three layers as [key speed colour seed horizon-frac col-w], from lines
  66-74 (textures) and 84-86 (speeds)."
  [[:back 0.1 [20 52 78 255] 1 0.2 16]
   [:mid 0.5 [12 34 50 255] 7 0.35 26]
   [:fore 1.0 [4 12 20 255] 23 0.5 44]])

(deftest each-layer-scrolls-at-its-rate
  (let [s0 (fresh unit)
        s1 (tick s0 unit)
        s100 (run s0 unit 100)]
    (testing "a new scene sits at zero"
      (is (= [0.0 0.0 0.0] [(:back s0) (:mid s0) (:fore s0)])))
    (testing "one frame moves back by 0.1, mid by 0.5 and fore by 1.0, to the left"
      (is (near? -0.1 (:back s1)))
      (is (near? -0.5 (:mid s1)))
      (is (near? -1.0 (:fore s1))))
    (testing "the rates hold over a hundred frames"
      (is (near? -10.0 (:back s100)))
      (is (near? -50.0 (:mid s100)))
      (is (near? -100.0 (:fore s100))))
    (testing "a closer layer is faster"
      (is (< (:fore s100) (:mid s100) (:back s100) 0.0)))
    (testing "the screen size changes nothing: the rates are in the original's units"
      (is (= s100 (run (fresh {:screen [1206 2334]}) {:screen [1206 2334]} 100))))))

(deftest layers-wrap-seamlessly
  (testing "a layer returns to zero when it reaches minus the width of two textures"
    (let [at-799 (assoc (fresh unit) :fore -799.0 :mid -799.5)
          next (tick at-799 unit)]
      (is (= 0.0 (:fore next)) "fore: -799 - 1 = -800 wraps")
      (is (= 0.0 (:mid next)) "mid: -799.5 - 0.5 = -800 wraps")
      (is (near? -799.9 (:back (tick (assoc (fresh unit) :back -799.8) unit)))
          "back at -799.8 steps to -799.9 without wrapping")
      (is (= 0.0 (:back (tick (assoc (fresh unit) :back -799.95) unit))))))
  (testing "no layer is ever beyond the wrap"
    (let [states (take 8100 (iterate #(tick % unit) (fresh unit)))]
      (is (every? #(> (:back %) -800.0) states))
      (is (every? #(> (:fore %) -800.0) states))
      (is (some #(= 0.0 (:fore %)) (rest states)))))
  (testing "the picture at the wrap is the picture at zero"
    (let [at (fn [b] (set (rects (assoc (fresh unit) :back b :mid b :fore b) unit)))]
      (is (= (at 0.0) (at -800.0)))))
  (testing "every column of the screen shows what the original's two textures show"
    (doseq [screen [[800 450]]
            b [0.0 -0.1 -37.9 -399.5 -400.0 -799.9]
            :let [state (assoc (fresh unit) :back b :mid b :fore b)
                  rs (rects state {:screen screen})
                  ib (long b)]
            [_ _ colour seed frac col-w] originals
            :let [mine (filter #(= colour (nth % 4)) rs)]
            :let [covered? (fn [x y] (some (fn [[rx ry rw rh]]
                                             (and (<= rx x) (< x (+ rx rw)) (<= ry y) (< y (+ ry rh))))
                                           mine))]
            sx (range 0 800 11)
            sy (range 224 450 9)]
      (let [u (mod (- sx ib) 800)
            tx (quot u 2)
            ty (quot (- sy 220) 2)
            want (opaque? seed frac col-w tx ty)]
        (is (= want (boolean (covered? (+ sx 0.5) (+ sy 0.5))))
            (str "offset " b " at " [sx sy]))))))

(deftest first-frame-draws
  (testing "the state after init alone has everything a draw reads"
    (doseq [screen screens
            :let [metrics {:screen screen}
                  [w h] screen
                  s (fresh metrics)
                  rs (rects s metrics)
                  geo (sc/geometry metrics)]]
      (testing (str screen)
        (is (every? number? [(:back s) (:mid s) (:fore s)]))
        (is (= 50 (count rs)) "25 back, 16 mid and 9 fore buildings in the first copy")
        (is (= #{[20 52 78 255] [12 34 50 255] [4 12 20 255]} (set (map #(nth % 4) rs))))
        (is (= [25 16 9] (mapv (fn [[_ _ c]] (count (filter #(= c (nth % 4)) rs))) originals)))
        (doseq [[x y rw rh] rs]
          (is (pos? rw))
          (is (pos? rh))
          (is (<= 0 x) "the first copy starts on the screen's left edge")
          (is (= h (+ y rh)) "every building stands on the bottom of the screen"))
        (is (<= (apply max (map (fn [[x _ rw]] (+ x rw)) rs)) w))
        (testing "the farthest layer is drawn first"
          (is (= [20 52 78 255] (nth (first rs) 4)))
          (is (= [4 12 20 255] (nth (last rs) 4))))
        (testing "the skyline sits below the caption"
          (let [dims (sc/dimensions metrics measure)
                cap (first (:lines dims))]
            (is (< (+ (:y cap) (:size cap)) (apply min (map second rs))))))
        (is (= (/ w 800.0) (:u geo)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (= 1 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) h) s)))))
