(ns net.b12n.raylib-ios.scenes.mousepaint-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.mousepaint :as sc]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def metrics {:screen [1206 2334]})

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- fresh
  ([] (fresh metrics))
  ([m] (first ((:init (sc/scene)) {:metrics m}))))

(defn- step
  "One update with the finger in `phase` at `pos`."
  ([state phase pos] (step state phase pos metrics))
  ([state phase pos m]
   (first ((:update (sc/scene)) state {:metrics m
                                       :pointer {:phase phase
                                                 :position pos}}))))

(defn- centre [{:keys [x y w h]}] [(+ x (quot w 2)) (+ y (quot h 2))])

(defn- button [id m]
  (first (filter #(= id (:id %)) (:buttons (sc/layout m)))))

(defn- in-field [m dx dy]
  (let [{:keys [x y]} (:field (sc/layout m))]
    [(+ x dx) (+ y dy)]))

(deftest the-palette-is-the-originals
  (is (= 23 sc/color-count))
  (is (= 23 (count sc/palette)))
  (is (= 23 (count (:palette (sc/layout metrics)))))
  (is (= [[245 245 245 255] [253 249 0 255] [255 203 0 255] [255 161 0 255]
          [255 109 194 255] [230 41 55 255] [190 33 55 255] [0 228 48 255]
          [0 158 47 255] [0 117 44 255] [102 191 255 255] [0 121 241 255]
          [0 82 172 255] [200 122 255 255] [135 60 190 255] [112 31 126 255]
          [211 176 131 255] [127 106 79 255] [76 63 47 255] [200 200 200 255]
          [130 130 130 255] [80 80 80 255] [0 0 0 255]]
         sc/palette))
  (is (= [20.0 2.0 50.0 5.0] [sc/brush-start sc/brush-min sc/brush-max sc/brush-step])))

(deftest a-drag-emits-strokes-as-the-original-draws-them
  (let [s0 (fresh)
        p0 (in-field metrics 100 200)
        p1 (in-field metrics 140 200)
        s1 (step s0 :press p0)
        s2 (step s1 :down p1)]
    (testing "a press is one circle at the point, in canvas pixels, the brush 20 in swatch 0"
      (is (= 1 (count (:marks s1))))
      (is (= [:stroke 100 200 100 200 20.0 [245 245 245 255]] (first (:marks s1)))))
    (testing "a drag is the segment from the last point to this one"
      (is (= 1 (count (:marks s2))))
      (is (= [:stroke 100 200 140 200 20.0 [245 245 245 255]] (first (:marks s2)))))
    (testing "the circles along a segment are half a radius apart and end on the point"
      (let [pts (sc/stroke-points 100 200 140 200 20.0)]
        (is (= 4 (count pts)))
        (is (= [[110.0 200.0] [120.0 200.0] [130.0 200.0] [140.0 200.0]] pts)))
      (is (= [[5.0 5.0]] (sc/stroke-points 5 5 5 5 20.0)))
      (is (= sc/max-circles (count (sc/stroke-points 0 0 2000 0 2.0))))
      (is (= [2000.0 0.0] (last (sc/stroke-points 0 0 2000 0 2.0)))))
    (testing "the selected colour, and the eraser's swatch 0, colour the stroke"
      (let [sw (nth (:palette (sc/layout metrics)) 5)
            picked (step s0 :press (centre sw))
            drawn (step (step picked :release nil) :press p0)
            erased (-> picked (step :release nil)
                       (step :press (centre (button :eraser metrics)))
                       (step :release nil)
                       (step :press p0))]
        (is (= 5 (:sel picked)))
        (is (= [230 41 55 255] (nth (first (:marks drawn)) 6)))
        (is (= [245 245 245 255] (nth (first (:marks erased)) 6)))))
    (testing "a finger leaving the field stops the stroke, and returning does not resume it"
      (let [out (step s2 :down (centre (first (:palette (sc/layout metrics)))))
            back (step out :down p0)]
        (is (= [] (:marks out)))
        (is (nil? (:last out)))
        (is (= [] (:marks back)))))
    (testing "a release ends the stroke and draws nothing"
      (let [r (step s2 :release p1)]
        (is (= [] (:marks r)))
        (is (nil? (:last r)))))))

(deftest the-buttons-are-the-originals-keys
  (let [s0 (fresh)
        press (fn [s id] (step (step s :press (centre (button id metrics))) :release nil))]
    (testing "the wheel: steps of 5 from 20, held to 2 to 50"
      (is (= 25.0 (:brush (press s0 :bigger))))
      (is (= 15.0 (:brush (press s0 :smaller))))
      (is (= 50.0 (:brush (nth (iterate #(press % :bigger) s0) 10))))
      (is (= 2.0 (:brush (nth (iterate #(press % :smaller) s0) 10))))
      (is (= 2.0 (:brush (press (nth (iterate #(press % :smaller) s0) 3) :smaller))) "5 less 5 is clamped to 2"))
    (testing "the brush radius reaches the stroke"
      (let [s (step (press s0 :bigger) :press (in-field metrics 50 50))]
        (is (= 25.0 (nth (first (:marks s)) 5)))))
    (testing "C: a clear to swatch 0, and no stroke"
      (is (= [[:clear [245 245 245 255]]] (:marks (step s0 :press (centre (button :clear metrics)))))))
    (testing "the right button: the eraser toggles, and picking a swatch turns it off"
      (let [on (press s0 :eraser)]
        (is (true? (:erase? on)))
        (is (false? (:erase? (press on :eraser))))
        (is (false? (:erase? (step on :press (centre (nth (:palette (sc/layout metrics)) 3))))))))
    (testing "the top left, under Back, is inert"
      (let [s (step s0 :press [10 10])]
        (is (= [] (:marks s)))
        (is (= (:brush s0) (:brush s)))))))

(deftest marks-are-consumed-once
  (let [s0 (fresh)
        s1 (step s0 :press (in-field metrics 50 50))
        s2 (step s1 :down (in-field metrics 60 70))
        s3 (step s2 :release nil)
        s4 (step s3 :idle nil)]
    (is (= [[:clear [245 245 245 255]]] (:marks s0)) "the new canvas is cleared to swatch 0")
    (is (= 1 (count (:marks s1))))
    (is (= 1 (count (:marks s2))))
    (is (= [:stroke 50 50 60 70 20.0 [245 245 245 255]] (first (:marks s2))) "only the new segment, not the press")
    (is (= [] (:marks s3)))
    (is (= [] (:marks s4)))))

(deftest a-finger-already-down-leaves-no-mark
  (let [s (step (fresh) :down (in-field metrics 50 50))]
    (is (= [] (:marks s)))
    (is (= [] (:marks (step s :down (in-field metrics 60 60)))))
    (testing "but the same finger lifted and landed again paints"
      (is (= 1 (count (:marks (step (step s :release nil) :press (in-field metrics 60 60)))))))))

(deftest a-turn-clears-the-canvas
  (let [land {:screen [2334 1206]}
        s1 (step (step (fresh) :press (in-field metrics 50 50)) :release nil)
        turned (step s1 :idle nil land)]
    (is (= [[:clear [245 245 245 255]]] (:marks turned)))
    (is (= [2334 1206] (:screen turned)))
    (is (nil? (:last turned)))
    (testing "the field has the new size, and the canvas follows it"
      (is (not= (:field (sc/layout metrics)) (:field (sc/layout land))))
      (is (= 2334 (:w (:field (sc/layout land))))))
    (testing "the turn is cleared once only"
      (is (= [] (:marks (step turned :idle nil land)))))
    (testing "a stroke in progress is dropped by the turn"
      (let [mid (step (fresh) :press (in-field metrics 50 50))
            t (step mid :down (in-field land 60 60) land)]
        (is (= [[:clear [245 245 245 255]]] (:marks t)))))))

(deftest first-frame-draws
  (is (= :mousepaint (:id (sc/scene))))
  (is (= "Mouse Painting" (:title (sc/scene))))
  (doseq [screen screens
          :let [[w h] screen
                m {:screen screen}
                lay (sc/layout m)
                [_ back-y _ back-h] gesture/back-region
                top (+ back-y back-h)]]
    (testing (str screen)
      (is (= 23 (count (:palette lay))))
      (is (= 4 (count (:buttons lay))))
      (testing "the swatches and buttons are on screen, below Back, and overlap nothing"
        (doseq [{:keys [x y]
                 sw :w
                 sh :h} (concat (:palette lay) (:buttons lay))]
          (is (>= x 0))
          (is (<= (+ x sw) w))
          (is (>= y top))
          (is (pos? sh))
          (is (< (+ y sh) (:y (:field lay)))))
        (let [rects (concat (:palette lay) (:buttons lay))]
          (is (= 27 (count rects)))
          (is (every? (fn [[a b]]
                        (or (<= (+ (:x a) (:w a)) (:x b)) (<= (+ (:x b) (:w b)) (:x a))
                            (<= (+ (:y a) (:h a)) (:y b)) (<= (+ (:y b) (:h b)) (:y a))))
                      (for [i (range 27) j (range i) :let [a (nth rects i) b (nth rects j)]] [a b])))))
      (testing "a swatch is a thumb: at least 30 pixels"
        (is (every? #(>= (min (:w %) (:h %)) 30) (:palette lay))))
      (testing "the field is the rest of the screen"
        (let [{:keys [x y]
               fw :w
               fh :h} (:field lay)]
          (is (= [0 w] [x fw]))
          (is (= h (+ y fh)))
          (is (pos? fh)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [lines buttons]} (sc/dimensions {:screen screen} measure)]]
    (testing (str screen)
      (is (= 4 (count lines)) "one label a button")
      (is (= ["SIZE -" "SIZE +" "ERASER" "CLEAR"] (mapv :s lines)))
      (doseq [{:keys [s x y size]} lines]
        (is (<= 0 x))
        (is (<= (+ x (measure s size)) w) s)
        (is (<= (+ y size) h) s))
      (doseq [{{:keys [s x y size]} :label
               bx :x
               by :y
               bw :w
               bh :h} buttons]
        (is (>= x bx) s)
        (is (<= (+ x (measure s size)) (+ bx bw)) s)
        (is (>= y by) s)
        (is (<= (+ y size) (+ by bh)) s)))))
