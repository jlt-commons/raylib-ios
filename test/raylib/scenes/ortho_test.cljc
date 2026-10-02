(ns raylib.scenes.ortho-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.ortho :as o]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(def start (first ((:init (o/scene)) {:metrics m})))

(defn- step
  ([state phase pos] (step state phase pos m))
  ([state phase pos metrics]
   (first ((:update (o/scene)) state {:metrics metrics
                                      :pointer {:phase phase
                                                :position pos}
                                      :touch-points (if pos [pos] [])}))))

(defn- tap
  "A press and a release at `pos`."
  [state pos]
  (-> state (step :press pos) (step :down pos) (step :release pos) (step :idle nil)))

(defn- tris [dl] (filterv (fn [it] (= :tri (nth it 0))) dl))

(defn- inside? [[vx vy vw vh] dl]
  (every? (fn [[_ & more]]
            (every? (fn [[x y]] (and (<= (- vx 1e-6) x (+ vx vw 1e-6)) (<= (- vy 1e-6) y (+ vy vh 1e-6))))
                    (partition 2 (take 6 more))))
          (tris dl)))

(def land (o/dimensions {:screen [2334 1206]} measure))

(deftest a-tap-toggles-the-projection
  (is (= :perspective (:projection (o/camera start land))) "it starts in perspective, as the original")
  (let [s1 (tap start [600.0 1200.0])
        s2 (tap s1 [100.0 2000.0])
        s3 (tap s2 [600.0 1200.0])]
    (is (= :orthographic (:projection (o/camera s1 land))))
    (is (= :perspective (:projection (o/camera s2 land))))
    (is (= :orthographic (:projection (o/camera s3 land))))
    (is (= 12.0 (:fovy (o/camera s1 land))) "orthographic fovy is the view height, 12")
    (is (= 45.0 (:fovy (o/camera s2 land))))
    (is (= [5.0 5.0 5.0] (:position (o/camera s1 land))))
    (is (= [0.0 0.0 0.0] (:target (o/camera s1 land)))))
  (testing "the caption follows the mode"
    (let [dims (o/dimensions m measure)]
      (is (= "PERSPECTIVE (tap to toggle)" (:s (o/caption start dims))))
      (is (= "ORTHOGRAPHIC (tap to toggle)" (:s (o/caption (tap start [600.0 1200.0]) dims)))))))

(deftest a-touch-under-back-does-not
  (let [pt [100.0 60.0]]
    (is (gesture/in-back-region? pt))
    (is (= :perspective (:projection (o/camera (tap start pt) land))))
    (is (= :orthographic (:projection (o/camera (tap (tap start [600.0 1200.0]) pt) land)))
        "nor does it toggle back")))

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                dims (o/dimensions metrics measure)]
          ortho? [false true]
          :let [s (assoc start :ortho? ortho?)
                base (o/grid-list (o/camera s dims) dims)
                dl (o/scene-list base s dims)
                cubes (tris dl)]]
    (testing (str screen " " (if ortho? "orthographic" "perspective"))
      (is (= 18 (count cubes)) "three cubes, three faces each, two triangles each, seen from (5,5,5)")
      (is (> (count (remove (fn [it] (= :tri (nth it 0))) dl)) 20) "the grid's lines are there")
      (is (inside? (:viewport dims) dl) "the cubes' corners all lie inside the field"))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (o/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [_ fy _ fh] (:viewport dims)]]
    (testing (str screen)
      (is (= 2 (count (:lines dims))) "one caption per mode")
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) fy) "the caption sits above the field"))
      (is (>= fy (+ back-y back-h)) "the field is below Back")
      (is (near? h (+ fy fh)) "and runs to the bottom"))))
