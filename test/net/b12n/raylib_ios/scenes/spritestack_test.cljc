(ns net.b12n.raylib-ios.scenes.spritestack-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.spritestack :as sc]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def dt (/ 1.0 60.0))
(def below-back [600.0 1200.0])
(def slop (gesture/slop m))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(defn- fresh ([] (fresh m)) ([metrics] (first ((:init (sc/scene)) {:metrics metrics}))))

(defn- step
  "One frame at 60 fps. `points` are the touch points (the first is the pointer)."
  ([state phase points] (step state m phase points))
  ([state metrics phase points]
   (sc/advance state {:metrics metrics
                      :delta-seconds dt
                      :pointer {:phase phase
                                :position (first points)}
                      :touch-points (vec points)})))

(defn- idle [state] (step state :idle []))

(defn- pinch-out
  "Press two fingers `a` and `b`, then hold them at `a2` and `b2`."
  [state a b a2 b2]
  (-> state
      (step :press [a b])
      (step :down [a b])
      (step :down [a2 b2])))

(deftest a-drag-rotates
  (let [s0 (fresh)
        [cx cy] below-back
        pressed (step s0 :press [below-back])
        right (step pressed :down [[(+ cx 300) cy]])
        left (step pressed :down [[(- cx 300) cy]])]
    (testing "the original starts spinning at 30 degrees a second, with no input"
      (is (= 30.0 (:speed s0)))
      (is (near? (* 30.0 dt) (:rotation (idle s0)))))
    (testing "the press frame changes no speed"
      (is (= 30.0 (:speed pressed))))
    (testing "a drag to the right adds 0.35 to the speed a frame, as the right arrow did"
      (is (near? 30.35 (:speed right)))
      (is (near? (+ (:rotation pressed) (* 30.35 dt)) (:rotation right))
          "rotation grows by the new speed times the frame time"))
    (testing "a drag to the left takes 0.35 off, as the left arrow did"
      (is (near? 29.65 (:speed left))))
    (testing "held for ten frames the speed has moved ten steps"
      (let [held (nth (iterate #(step % :down [[(+ cx 300) cy]]) pressed) 10)]
        (is (near? 33.5 (:speed held)))))
    (testing "how far the finger is does not change the step"
      (is (= (:speed (step pressed :down [[(+ cx 100) cy]]))
             (:speed (step pressed :down [[(+ cx 500) cy]])))))
    (testing "drifting up or down, or a tap, changes no speed"
      (is (= 30.0 (:speed (step pressed :down [[cx (+ cy 400)]]))))
      (is (= 30.0 (:speed (step pressed :down [[(+ cx (* 0.5 slop)) cy]])))))
    (testing "lifting stops the changes, without reading the release position"
      (let [lifted (step right :release [[0.0 0.0]])]
        (is (near? 30.35 (:speed lifted)))
        (is (= (:speed lifted) (:speed (idle lifted))))
        (is (nil? (:stick (idle lifted))))))
    (testing "a press under Back starts no stick"
      (let [on-back (step (fresh) :press [[100.0 60.0]])]
        (is (nil? (:stick on-back)))))
    (testing "two fingers are a pinch, never a drag"
      (let [two (step (step s0 :press [below-back [900.0 1200.0]])
                      :down [[(+ cx 300) cy] [900.0 1200.0]])]
        (is (= 30.0 (:speed two)))))))

(deftest a-pinch-spreads-within-the-originals-limits
  (let [s0 (fresh)
        a [500.0 1200.0]
        b [700.0 1200.0]
        out (pinch-out s0 a b [450.0 1200.0] [750.0 1200.0])
        in (pinch-out s0 a b [550.0 1200.0] [650.0 1200.0])]
    (testing "it starts at the original's 3.2"
      (is (= 3.2 (:spacing s0))))
    (testing "the first frame of a pinch only records, so nothing jumps"
      (is (= 3.2 (:spacing (step s0 :press [a b])))))
    (testing "spreading the fingers spreads the layers, and pinching in closes them"
      (is (> (:spacing out) 3.2))
      (is (< (:spacing in) 3.2)))
    (testing "the change follows the ratio of the distances"
      (is (near? (:spacing out) (+ 3.2 (* sc/pinch-gain (- 1.5 1.0)))))
      (is (near? (:spacing in) (+ 3.2 (* sc/pinch-gain (- 0.5 1.0))))))
    (testing "swapping the two points changes nothing"
      (is (near? (:spacing out)
                 (:spacing (-> s0
                               (step :press [a b])
                               (step :down [b a])
                               (step :down [[750.0 1200.0] [450.0 1200.0]]))))))
    (testing "it stops at the original's 0.0 and 5.0"
      (let [wide (loop [s (step (step s0 :press [a b]) :down [a b])
                        k 0
                        d 200.0]
                   (if (= k 30)
                     s
                     (recur (step s :down [[(- 600.0 (* 0.5 (* d 2.0))) 1200.0]
                                           [(+ 600.0 (* 0.5 (* d 2.0))) 1200.0]])
                            (inc k)
                            (* d 2.0))))
            shut (loop [s (step (step s0 :press [[0.0 1200.0] [1200.0 1200.0]])
                                :down [[0.0 1200.0] [1200.0 1200.0]])
                        k 0
                        d 1200.0]
                   (if (= k 30)
                     s
                     (recur (step s :down [[(- 600.0 (* 0.5 d)) 1200.0]
                                           [(+ 600.0 (* 0.5 d)) 1200.0]])
                            (inc k)
                            (* d 0.5))))]
        (is (= 5.0 (:spacing wide)))
        (is (= 0.0 (:spacing shut)))))
    (testing "it acts only while exactly two fingers are down"
      (let [paired (-> s0 (step :press [a b]) (step :down [a b]))
            third (step paired :down [[400.0 1200.0] [800.0 1200.0] [600.0 900.0]])
            one (step paired :down [[400.0 1200.0]])]
        (is (= 3.2 (:spacing third)))
        (is (= 3.2 (:spacing one)))
        (is (nil? (:pinch third)))))))

(defn- stack
  "What `emit-stack!` does for `state`: [layer-calls shape-calls] where a layer
  call is [i x y rotation] and a shape is [:rect x y w h colour] or
  [:circle x y r colour], in order, each tagged with its layer."
  [state metrics]
  (let [out (atom [])
        cur (atom nil)]
    (sc/emit-stack! (fn [i x y rot f]
                      (reset! cur i)
                      (swap! out conj [:layer i x y rot])
                      (f))
                    (fn [x y w h colour] (swap! out conj [:rect @cur x y w h colour]))
                    (fn [x y r colour] (swap! out conj [:circle @cur x y r colour]))
                    state
                    (sc/geometry metrics))
    @out))

(deftest layers-draw-bottom-to-top
  (let [s (assoc (fresh) :rotation 33.0)
        calls (stack s m)
        layer-calls (filter #(= :layer (first %)) calls)
        geo (sc/geometry m)]
    (testing "all forty slices are drawn, last row first, so row 0 (the roof) is on top"
      (is (= (range 39 -1 -1) (map second layer-calls))))
    (testing "each slice's shapes follow its own layer call"
      (let [owners (map second (remove #(= :layer (first %)) calls))]
        (is (= owners (sort-by - owners)) "the owner never goes back up")))
    (testing "the first drawn is the lowest on the screen and each next one is higher"
      (let [ys (map #(nth % 3) layer-calls)]
        (is (apply > ys))
        (is (near? (* 3.2 (:k geo)) (- (nth ys 0) (nth ys 1))))))
    (testing "the stack is centred on the field, as the original's on the window"
      (let [ys (map #(nth % 3) layer-calls)]
        (is (near? (nth (:centre geo) 1)
                   (+ (nth ys 39) (* 0.5 (* 3.2 (:k geo)) 40))))
        (is (every? #(near? (first (:centre geo)) (nth % 2)) layer-calls))))
    (testing "every slice turns by the one rotation"
      (is (every? #(= 33.0 (nth % 4)) layer-calls)))
    (testing "the wheels are the lowest slices and the cabin glass the highest"
      (let [colours (fn [i] (set (map last (filter #(and (not= :layer (first %)) (= i (second %))) calls))))]
        (is (= #{[0 0 0 255]} (colours 39)))
        (is (= #{[0 0 0 255]} (colours 35)))
        (is (= #{[230 41 55 255]} (colours 34)))
        (is (= #{[230 41 55 255]} (colours 12)))
        (is (= #{[190 33 55 255]} (colours 11)))
        (is (= #{[190 33 55 255] [102 191 255 255]} (colours 5)))
        (is (= #{[190 33 55 255] [102 191 255 255]} (colours 0)))
        (is (= #{[190 33 55 255]} (colours 6)))))
    (testing "with no spacing the slices pile on one point"
      (let [flat (stack (assoc s :spacing 0.0) m)
            ys (map #(nth % 3) (filter #(= :layer (first %)) flat))]
        (is (apply = ys))))))

(deftest slices-are-the-originals
  (let [shapes (map sc/layer-shapes (range 40))
        bbox (fn [ss]
               (let [xs (mapcat (fn [s] (case (first s)
                                          :rect [(nth s 1) (+ (nth s 1) (nth s 3))]
                                          :circle [(- (nth s 1) (nth s 3)) (+ (nth s 1) (nth s 3))]))
                                ss)
                     ys (mapcat (fn [s] (case (first s)
                                          :rect [(nth s 2) (+ (nth s 2) (nth s 4))]
                                          :circle [(- (nth s 2) (nth s 3)) (+ (nth s 2) (nth s 3))]))
                                ss)]
                 [(apply min xs) (apply min ys) (apply max xs) (apply max ys)]))
        band (fn [i] (let [t (/ (double i) 39)]
                       (cond (>= t 0.88) :wheels (>= t 0.55) :body (>= t 0.30) :shoulders :else :cabin)))]
    (is (= 40 (count shapes)))
    (testing "wheels: two black 11 by 28 rects at x 8 and 38"
      (doseq [i (range 40) :when (= :wheels (band i))]
        (is (= [[:rect 8 0 11 28 [0 0 0 255]] [:rect 38 0 11 28 [0 0 0 255]]]
               (nth shapes i)))))
    (testing "a slab is a cross of two rects and four corner circles, and spans its box"
      (doseq [i (range 40)
              :let [ss (nth shapes i)]
              :when (not= :wheels (band i))]
        (let [slabs (if (< i 6) 2 1)]
          (is (= (* 2 slabs) (count (filter #(= :rect (first %)) ss))) (str i))
          (is (= (* 4 slabs) (count (filter #(= :circle (first %)) ss))) (str i)))))
    (testing "the body, shoulders and cabin boxes (x y w h from the original's slab calls)"
      (is (= [2 1 54 27] (bbox (nth shapes 22))))
      (is (= [5 2 51 26] (bbox (nth shapes 12))))
      (is (= [10 3 36 25] (bbox (nth shapes 6)))))
    (testing "the glass is the last slab of the top six slices, 20 by 16 with corner radius 2"
      (is (= [13 6 33 22] (bbox (drop 6 (nth shapes 5)))))
      (is (= 6 (count (drop 6 (nth shapes 0)))))
      (is (= 6 (count (nth shapes 6))) "slice 6 has no glass"))
    (testing "count of shapes over the forty slices"
      (is (= (+ (* 5 2) (* 41 6)) (reduce + (map count shapes)))))))

(deftest first-frame-draws
  (testing "the state after init alone has everything a draw reads"
    (doseq [screen screens
            :let [metrics {:screen screen}
                  s (fresh metrics)
                  geo (sc/geometry metrics)
                  calls (stack s metrics)
                  shapes (remove #(= :layer (first %)) calls)
                  dims (sc/dimensions metrics measure)]]
      (testing (str screen)
        (is (every? number? [(:rotation s) (:speed s) (:spacing s)]))
        (is (= 256 (count shapes)) "5 wheel slices of 2 rects, then 41 slabs of 6 shapes")
        (is (= 40 (count (filter #(= :layer (first %)) calls))))
        (testing "every shape stays inside the field whatever the rotation"
          (let [reach (* (:k geo) 3.0 (Math/sqrt (+ (* 28.0 28.0) (* 14.0 14.0))))
                [fx fy fw fh] (:field geo)]
            (doseq [[_ _ x y] (filter #(= :layer (first %)) calls)]
              (is (<= fx (- x reach)) screen)
              (is (<= (+ x reach) (+ fx fw)) screen)
              (is (<= fy (- y reach)) screen)
              (is (<= (+ y reach) (+ fy fh)) screen))))
        (testing "shape coordinates are local to the slice centre and within its half size"
          (doseq [[kind _ x y a b] shapes
                  :let [half-x (+ 1.0 (* 3.0 (:k geo) 28.0))
                        half-y (+ 1.0 (* 3.0 (:k geo) 14.0))
                        far-x (if (= kind :rect) (+ x a) x)
                        far-y (if (= kind :rect) (+ y b) y)]]
            (is (<= (- half-x) x far-x half-x) (str [kind x y]))
            (is (<= (- half-y) y far-y half-y) (str [kind x y]))))
        (testing "the readouts have their text"
          (is (= "spacing 3.2" (sc/spacing-line 3.2)))
          (is (= "speed 30.00" (sc/speed-line 30.0)))
          (is (= 4 (count (:lines dims)))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [_ fy _ fh] (:field (sc/geometry {:screen screen}))]]
    (testing (str screen)
      (is (= 4 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) h) s))
      (testing "the field is between the readouts and the note"
        (let [ys (map :y (:lines dims))]
          (is (> fy (nth ys 2)))
          (is (< (+ fy fh) (nth ys 3))))))))
