(ns net.b12n.raylib-ios.scenes.rendertex-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.rendertex :as sc]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def metrics {:screen [1206 2334]})

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- fresh [] (first ((:init (sc/scene)) {:metrics metrics})))

(defn- tick [state dt]
  (first ((:update (sc/scene)) state {:metrics metrics
                                      :delta-seconds dt})))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(deftest the-target-is-the-originals-size
  (is (= [320 240] [sc/rt-w sc/rt-h]))
  (is (= [320 240] [(:w (sc/target-spec)) (:h (sc/target-spec))]))
  (is (false? (:depth? (sc/target-spec))) "the passes are 2D, so no depth buffer"))

(deftest the-copies-follow-the-original
  (testing "the original's table, as [x y scale label]"
    (is (= [[40 90 1.0 "100%"] [400 90 0.6 "60%"] [400 260 0.35 "35%"] [610 260 0.5 "50% tinted"]]
           sc/copies)))
  (testing "each copy is the target at its scale, truncated to a whole pixel as the original does"
    (is (= [[320 240] [192 144] [112 84] [160 120]]
           (mapv (fn [[_ _ s _]] [(int (* sc/rt-w s)) (int (* sc/rt-h s))]) sc/copies))))
  (testing "only the last copy is tinted, (255, 180, 180)"
    (is (= [nil nil nil [255 180 180 255]] (mapv :tint (:copies (sc/dimensions metrics measure)))))
    (is (= [255 180 180 255] sc/tint-colour)))
  (testing "the copies' positions and sizes are the original's, times one scale"
    (doseq [screen screens
            :let [dims (sc/dimensions {:screen screen} measure)
                  k (:scale dims)]]
      (testing (str screen)
        (is (pos? k))
        (is (= 4 (count (:copies dims))))
        (is (seq (:copies dims)))
        (doseq [[[x y s _] c] (map vector sc/copies (:copies dims))]
          (is (near? (+ (:ox dims) (* k x)) (:x c)))
          (is (near? (+ (:oy dims) (* k y)) (:y c)))
          (is (near? (* k (int (* sc/rt-w s))) (:width c)))
          (is (near? (* k (int (* sc/rt-h s))) (:height c)))))))
  (testing "the motion: six balls on the original's Lissajous, (int x), (int y)"
    (let [ref-ball (fn [i t]
                     (let [ph (* i 0.9)]
                       [(int (+ 160.0 (* 110 (Math/sin (+ t ph)))))
                        (int (+ 120.0 (* 70 (Math/cos (* 1.4 (+ t ph))))))]))]
      (doseq [t [0.0 0.5 1.7 12.3 100.0]]
        (is (= (mapv #(ref-ball % t) (range 6)) (mapv (fn [[x y]] [x y]) (sc/balls t))) (str t))))
    (is (= 18 sc/ball-radius))
    (testing "the colour is r = i*60 mod 256, g 200, b = 255 - r"
      (is (= [[0 200 255 255] [60 200 195 255] [120 200 135 255]
              [180 200 75 255] [240 200 15 255] [44 200 211 255]]
             (mapv #(nth % 2) (sc/balls 0.0)))))
    (testing "the state advances by the frame's seconds"
      (is (near? 0.0 (:t (fresh))))
      (is (near? 1.0 (:t (nth (iterate #(tick % 0.25) (fresh)) 4)))))))

(deftest first-frame-draws
  (is (= :rendertex (:id (sc/scene))))
  (is (= "Render Texture" (:title (sc/scene))))
  (is (= "one scene rendered once, drawn back four times" sc/title-line))
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (testing "every copy lies on the screen, below Back"
        (is (seq (:copies dims)))
        (doseq [{:keys [x y width height]} (:copies dims)]
          (is (>= x 0))
          (is (<= (+ x width) w))
          (is (>= y (+ back-y back-h)))
          (is (<= (+ y height) h))))
      (testing "the balls stay inside the target at every moment"
        (doseq [t (range 0.0 60.0 0.37)]
          (is (seq (sc/balls t)))
          (doseq [[x y] (sc/balls t)]
            (is (<= sc/ball-radius x (- sc/rt-w sc/ball-radius)))
            (is (<= sc/ball-radius y (- sc/rt-h sc/ball-radius)))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [lines]} (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (= 5 (count lines)) "the title and four labels")
      (doseq [{:keys [s x y size]} lines]
        (is (<= 0 x))
        (is (<= (+ x (measure s size)) w) s)
        (is (<= (+ y size) h) s)
        (is (>= y (+ back-y back-h)) "below Back")))))
