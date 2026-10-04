(ns net.b12n.raylib-ios.scenes.strip-test
  (:require [clojure.test :refer [deftest is]]
            [net.b12n.raylib-ios.scenes.strip :as strip]))

(deftest band-colours-match-the-original
  (is (= [0 219 223 255] (strip/band-colour 0)))
  (is (= [255 129 0 255] (strip/band-colour 8)))
  (is (= [49 0 0 255] (strip/band-colour 15))))

(deftest the-bands-tile-the-width-exactly
  (let [w 1206
        bs (strip/bands (strip/dimensions {:screen [w 2334]}))]
    (is (= strip/segments (count bs)))
    (is (= 0 (long (first (first bs)))))
    (is (= (double w) (double (second (last bs)))))
    (doseq [[a b] (map vector bs (rest bs))]
      (is (= (second a) (first b))))))

(deftest the-band-stays-on-screen
  (let [[w h] [1206 2334]
        bs (strip/bands (strip/dimensions {:screen [w h]}))]
    (doseq [[x0 x1 top bot _] bs]
      (is (<= 0 x0 x1 w))
      (is (<= 0 top bot h)))
    (let [{:keys [top bot]} (strip/dimensions {:screen [w h]})]
      ;; the middle 20% of the height, to within float rounding
      (is (< (abs (- (* 0.2 h) (- bot top))) 1e-6)))))

(deftest text-lines-fit-the-safe-region
  (let [[w h] [1206 2334]
        {:keys [caption-size caption-x caption-y]} (strip/dimensions {:screen [w h]})
        text "a rainbow strip via rlgl immediate mode"
        ;; estimate: 0.6 of the size per character, for raylib's default font
        width (* 0.6 caption-size (count text))]
    (is (<= 0 caption-y))
    (is (<= (+ caption-y caption-size) h))
    (is (<= (+ caption-x width) w))))
