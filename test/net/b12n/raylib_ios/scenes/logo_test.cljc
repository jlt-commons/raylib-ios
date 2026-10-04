(ns net.b12n.raylib-ios.scenes.logo-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.logo :as logo]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(defn estimate
  "estimate: 0.6 of the size per character, as in the other scenes' tests."
  [s size]
  (* 0.6 size (count s)))

(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))

(deftest the-border-is-square-and-centred
  (doseq [screen screens
          :let [[w h] screen
                dims (logo/dimensions {:screen screen})
                {:keys [outer inner]} (logo/layout dims estimate)
                [ox oy ow oh] outer
                [ix iy iw ih] inner
                b (:border dims)]]
    (testing (str screen)
      (is (= ow oh) "the outer square is square")
      (is (= iw ih) "and so is the inner")
      (is (< (Math/abs (- (+ ox (* 0.5 ow)) (* 0.5 w))) 1e-6) "centred across the screen")
      (is (< (Math/abs (- (+ oy (* 0.5 oh)) (+ back-bottom (* 0.5 (- h back-bottom))))) 1e-6)
          "and down the region below Back")
      (is (= [(+ ox b) (+ oy b)] [ix iy]) "the border is even on the top and left")
      (is (< (Math/abs (- (- (+ ox ow) (+ ix iw)) b)) 1e-6) "and on the right")
      (is (< (Math/abs (- (- (+ oy oh) (+ iy ih)) b)) 1e-6) "and the bottom")
      (is (< (Math/abs (- (/ b ow) (/ 16.0 256.0))) 1e-6) "the original's proportions"))))

(deftest the-label-sits-in-the-corner-by-measure
  (let [dims (logo/dimensions {:screen [1206 2334]})
        {:keys [outer inner label]} (logo/layout dims estimate)
        [ox oy ow oh] outer
        [ix iy iw ih] inner
        {:keys [s x y size]} label
        right (+ x (estimate s size))]
    (is (= "raylib" s))
    (is (< (Math/abs (- (- (+ ix iw) right) (:gap dims))) 1e-6) "the gap to the inner right edge")
    (is (< (Math/abs (- (- (+ iy ih) (+ y size)) (:gap dims))) 1e-6) "and the inner bottom edge")
    (is (and (>= x ix) (>= y iy)) "inside the inner square")
    (is (and (<= right (+ ox ow)) (<= (+ y size) (+ oy oh))))))

(deftest a-different-measure-moves-the-label
  (let [dims (logo/dimensions {:screen [1206 2334]})
        wide (fn [s size] (* 1.2 size (count s)))
        a (:label (logo/layout dims estimate))
        b (:label (logo/layout dims wide))]
    (is (< (:x b) (:x a)) "a wider label starts further left")
    (is (= (:y a) (:y b)) "and sits on the same baseline")
    (is (= (+ (:x a) (estimate "raylib" (:size a))) (+ (:x b) (wide "raylib" (:size b))))
        "with its right edge in the same place")))

(deftest fits-below-back
  (doseq [screen screens
          :let [[w h] screen
                dims (logo/dimensions {:screen screen})
                [ox oy ow oh] (:outer (logo/layout dims estimate))]]
    (testing (str screen)
      (is (>= oy back-bottom) "starts below Back")
      (is (>= ox 0))
      (is (<= (+ ox ow) w))
      (is (<= (+ oy oh) h)))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (logo/dimensions {:screen screen})
                {:keys [s x y size]} (:label (logo/layout dims estimate))]]
    (testing (str screen)
      (is (>= x 0) s)
      (is (<= (+ x (estimate s size)) w) s)
      (is (>= y back-bottom))
      (is (<= (+ y size) h) s))))

(deftest the-scene-follows-the-screen
  (let [sc (logo/scene)
        [state _] ((:init sc) {:metrics {:screen [800 450]}})
        [next _] ((:update sc) state {:metrics {:screen [450 800]}})]
    (is (= [450 800] (:screen next)))
    (is (= "Still Logo" (:title sc)))
    (is (= :logo (:id sc)))))
