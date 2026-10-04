(ns net.b12n.raylib-ios.scenes.hello-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.hello :as hello]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(deftest the-text-is-centred-by-measure
  (let [narrow (fn [s size] (+ 7 (* 0.4 size (count s))))
        wide (fn [s size] (+ 7 (* 0.55 size (count s))))]
    (doseq [screen [[1206 2334] [2334 1206] [800 450]]
            m [narrow wide]
            :let [{:keys [w text]} (hello/dimensions {:screen screen} m)
                  {:keys [s x size]} text
                  tw (m s size)]]
      (testing (str screen)
        (is (<= (abs (- (- x 0.0) (- w x tw))) 2.0)
            "the left and right margins agree to a pixel or two")))
    (testing "a wider font pushes the start left"
      (is (< (get-in (hello/dimensions {:screen [1206 2334]} wide) [:text :x])
             (get-in (hello/dimensions {:screen [1206 2334]} narrow) [:text :x]))))
    (testing "the original's 800x450 puts it a little left of the original's 190 px, which assumed a wider font"
      (let [{:keys [x size y]} (:text (hello/dimensions {:screen [800 450]} measure))]
        (is (<= 20 size))
        (is (<= 0 x 200))
        (is (>= y (let [[_ by _ bh] gesture/back-region] (+ by bh))))))))

(deftest the-line-spans-most-of-the-width-in-dark-grey
  (doseq [screen screens
          :let [[w _] screen
                {:keys [text]} (hello/dimensions {:screen screen} measure)
                span (/ (measure (:s text) (:size text)) w)]]
    (testing (str screen)
      (is (<= 0.8 span 0.9) "about 85 percent, never past it")))
  (is (= [80 80 80 255] hello/text-colour)))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [text]} (hello/dimensions {:screen screen} measure)
                {:keys [s x y size]} text]]
    (testing (str screen)
      (is (= hello/line s))
      (is (<= 0 x))
      (is (<= (+ x (measure s size)) w))
      (is (>= y (let [[_ by _ bh] gesture/back-region] (+ by bh))))
      (is (<= (+ y size) h)))))
