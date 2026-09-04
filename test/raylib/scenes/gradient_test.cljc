(ns raylib.scenes.gradient-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.gradient :as gr]))

(def d (gr/dimensions {:screen [1206 2334]}))

(deftest the-hue-walk-covers-the-wheel
  (testing "six sectors, and the primaries land where they should"
    (is (= [255 0 0] (gr/hsv->rgb 0.0)) "red")
    (is (= [255 255 0] (gr/hsv->rgb 60.0)) "yellow")
    (is (= [0 255 0] (gr/hsv->rgb 120.0)) "green")
    (is (= [0 255 255] (gr/hsv->rgb 180.0)) "cyan")
    (is (= [0 0 255] (gr/hsv->rgb 240.0)) "blue")
    (is (= [255 0 255] (gr/hsv->rgb 300.0)) "magenta"))
  (testing "and it wraps rather than running off the end"
    (is (= (gr/hsv->rgb 0.0) (gr/hsv->rgb 360.0)))
    (is (= (gr/hsv->rgb 30.0) (gr/hsv->rgb 390.0)))
    (is (= (gr/hsv->rgb 350.0) (gr/hsv->rgb -10.0)) "negative too")))

(deftest every-hue-is-in-range
  (doseq [deg (range -400 760 7)]
    (let [[r g b] (gr/hsv->rgb (double deg))]
      (doseq [c [r g b]]
        (is (<= 0 c 255) (str "hue " deg " gave " [r g b]))))))

(deftest the-static-bands-are-the-three-raylib-calls
  (testing "a vertical gradient is the four-corner case with the top pair
            equal, which is the claim the whole scene rests on"
    (let [[tl tr br bl] (gr/corners 0 0.0)]
      (is (= tl tr) "top pair equal makes it vertical")
      (is (= br bl) "and the bottom pair")
      (is (not= tl br))))
  (testing "horizontal is the same idea sideways"
    (let [[tl tr br bl] (gr/corners 1 0.0)]
      (is (= tl bl) "left pair equal makes it horizontal")
      (is (= tr br))
      (is (not= tl tr))))
  (testing "and the third has four genuinely different corners"
    (is (= 4 (count (distinct (gr/corners 2 0.0)))))))

(deftest the-turning-band-actually-turns
  (let [a (gr/corners 3 0.0)
        b (gr/corners 3 1.0)]
    (is (not= a b))
    (testing "and keeps its corners a quarter turn apart the whole way round"
      (doseq [t [0.0 0.7 3.3 11.9]]
        (let [[c0 c1 c2 c3] (gr/corners 3 t)]
          (is (< (abs (- 90.0 (- c1 c0))) 1e-9))
          (is (< (abs (- 90.0 (- c2 c1))) 1e-9))
          (is (< (abs (- 90.0 (- c3 c2))) 1e-9)))))))

(deftest the-bands-tile-the-screen-without-overlapping
  (let [rects (map (fn [i] (gr/band-rect d i)) (range gr/bands))]
    (doseq [[x y w h] rects]
      (is (>= x 0.0))
      (is (>= y 0.0))
      (is (<= (+ x w) 1206.0))
      (is (<= (+ y h) 2334.0))
      (is (pos? w))
      (is (pos? h)))
    (testing "and each sits below the one before it"
      (doseq [[[_ y1 _ h1] [_ y2 _ _]] (partition 2 1 rects)]
        (is (<= (+ y1 h1) y2))))))

(deftest time-only-moves-forward
  (let [run (reductions (fn [s _] (gr/advance s {:delta-seconds 0.1})) {:t 0.0} (range 50))]
    (is (apply <= (map :t run)))
    (testing "and a negative delta cannot wind it back"
      (is (= 5.0 (:t (gr/advance {:t 5.0} {:delta-seconds -3.0})))))))
