(ns raylib.scenes.rlgltriangle-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.rlgltriangle :as t]))

(def m {:screen [1206 2334]})
(def d (t/dimensions m))
(def start (first ((:init (t/scene)) {:metrics m})))

(defn- touch [state phase position]
  (t/advance state {:metrics m
                    :pointer {:phase phase
                              :position position}}))

(defn- centre [rect-key id]
  (let [[x y w h] (:rect (first (filter #(= id (:id %)) (:buttons d))))]
    (assert (= :rect rect-key))
    [(+ x (* 0.5 w)) (+ y (* 0.5 h))]))

(defn- pos [state i] (get-in state [:corners i :pos]))

(deftest wound-has-a-negative-cross-product-for-both-orders
  (let [[a b c] (:start-corners d)
        cr (fn [cs] (let [[p q r] (map :pos cs)] (t/cross p q r)))]
    (is (neg? (cr (t/wound [a b c]))))
    (is (neg? (cr (t/wound [a c b]))))))

(deftest wound-carries-colours-with-their-corners
  (let [[a b c] (:start-corners d)]
    (doseq [order [[a b c] [a c b]]
            corner (t/wound order)]
      (is (contains? (set order) corner) "same corner, same colour")
      (is (= (:color corner)
             (:color (first (filter #(= (:pos corner) (:pos %)) order))))))
    (is (= (set (map :color [a b c])) (set (map :color (t/wound [a c b])))))))

(deftest a-corner-follows-a-drag
  (let [[x y] (pos start 1)
        a (touch start :press [(+ x 10) (+ y 10)])
        b (touch a :down [300 400])]
    (is (= 1 (:dragging a)))
    (is (= [300.0 400.0] (pos b 1)))
    (is (= (pos start 0) (pos b 0)))))

(deftest the-grab-is-sticky
  (let [[x y] (pos start 2)
        a (touch start :press [x y])
        far (touch a :down [100 2000])]
    (is (= 2 (:dragging far)) "held far outside the grab radius")
    (is (= [100.0 2000.0] (pos far 2)))
    (testing "and a finger that lands away from every corner grabs nothing"
      (let [miss (touch start :press [50 1500])
            moved (touch miss :down [300 1500])]
        (is (nil? (:dragging moved)))
        (is (= (:corners start) (:corners moved)))))))

(deftest outline-button-toggles
  (let [p (centre :rect :outline)
        a (touch start :press p)
        b (touch (touch a :idle nil) :press p)]
    (is (:lines? a))
    (is (not (:lines? b)))
    (is (nil? (:dragging a)))))

(deftest reset-button-restores-corners
  (let [[x y] (pos start 0)
        dragged (touch (touch start :press [x y]) :down [100 100])
        reset (touch dragged :press (centre :rect :reset))]
    (is (not= (:corners start) (:corners dragged)))
    (is (= (:start-corners d) (:corners reset)))))

(deftest a-release-does-not-move-a-corner
  (let [[x y] (pos start 0)
        dragged (touch (touch start :press [x y]) :down [100 100])
        released (touch dragged :release [1100 2200])
        idle (touch dragged :idle nil)]
    (is (= (:corners dragged) (:corners released)))
    (is (nil? (:dragging released)))
    (is (= (:corners dragged) (:corners idle)) "and an idle frame with no point")))

(deftest buttons-avoid-the-back-target
  (testing "Back is at the top-left; estimate: [0 0 400 120] covers it"
    (doseq [{[x y w h] :rect
             label :label} (:buttons d)]
      (is (or (>= x 400) (<= (+ x w) 0) (>= y 120) (<= (+ y h) 0)) label))))

(deftest corners-stay-on-screen
  (let [[x y] (pos start 0)
        grabbed (touch start :press [x y])]
    (doseq [p [[-500 -500] [5000 5000] [-1 3000]]]
      (let [[cx cy] (pos (touch grabbed :down p) 0)]
        (is (<= 0 cx 1206))
        (is (<= 0 cy 2334))))))

(deftest text-lines-fit-the-safe-region
  (let [[w h] (:screen m)
        {:keys [label-size buttons]} d
        ;; estimate: 0.6 of the size per character, for raylib's default font
        char-w (* 0.6 label-size)]
    (doseq [{:keys [label label-x label-y rect]} buttons
            :let [[bx by bw bh] rect]]
      (is (<= 0 label-y) label)
      (is (<= (+ label-y label-size) h) label)
      (is (<= (+ label-x (* char-w (count label))) w) label)
      (is (<= (+ by bh) h) label)
      (is (<= (+ bx bw) w) label)
      (is (<= (+ label-x (* char-w (count label))) (+ bx bw)) "inside its own button"))))
