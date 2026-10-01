(ns raylib.scenes.fontsizes-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.fontsizes :as fs]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(defn estimate
  "estimate: 0.6 of the size per character, as in the other scenes' tests."
  [s size]
  (* 0.6 size (count s)))

(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))

(defn- size-of [dims s] (:size (first (filter #(= s (:s %)) (:lines dims)))))

(deftest sizes-scale-with-the-screen
  (let [small (fs/dimensions {:screen [450 800]})
        large (fs/dimensions {:screen [1206 2334]})]
    (is (< (:u small) (:u large)))
    (is (every? true? (map (fn [a b] (< (:size a) (:size b))) (:lines small) (:lines large)))
        "every line is bigger on the bigger screen")
    (testing "and the sizes keep the original's ratios, to within rounding"
      (doseq [dims [small large]
              :let [s10 (size-of dims "size 10")
                    s20 (size-of dims "size 20")
                    s30 (size-of dims "size 30")
                    s40 (size-of dims "size 40")]]
        (is (<= (Math/abs (- s20 (* 2 s10))) 1))
        (is (<= (Math/abs (- s30 (* 3 s10))) 2))
        (is (<= (Math/abs (- s40 (* 4 s10))) 2))
        (is (< s10 s20 s30 s40))))
    (is (every? integer? (map :size (:lines large))) "ints, for draw-text")))

(deftest centred-lines-use-measure
  (doseq [screen screens
          :let [[w _] screen
                dims (fs/dimensions {:screen screen})
                wide (fn [s size] (* 1.2 size (count s)))
                a (fs/layout dims estimate)
                b (fs/layout dims wide)
                centred (fn [lines] (filter :centred? lines))]]
    (testing (str screen)
      (is (= 2 (count (centred a))))
      (doseq [{:keys [s size x]} (centred a)]
        (is (<= (Math/abs (- x (* 0.5 (- w (estimate s size))))) 1) "centred by the measure"))
      (is (every? true? (map (fn [p q] (< (:x q) (:x p))) (centred a) (centred b)))
          "a wider measure moves a centred line left")
      (is (= (map :x (remove :centred? a)) (map :x (remove :centred? b)))
          "and leaves the left-hand lines alone"))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (fs/dimensions {:screen screen})]]
    (testing (str screen)
      (doseq [{:keys [s x y size]} (fs/layout dims estimate)]
        (is (>= x 0) s)
        (is (<= (+ x (estimate s size)) w) s)
        (is (>= y back-bottom) s)
        (is (<= (+ y size) h) s)))))

(deftest the-scene-follows-the-screen
  (let [sc (fs/scene)
        [state _] ((:init sc) {:metrics {:screen [800 450]}})
        [next _] ((:update sc) state {:metrics {:screen [450 800]}})]
    (is (= [450 800] (:screen next)))
    (is (= "Font Sizes" (:title sc)))
    (is (= :fontsizes (:id sc)))))
