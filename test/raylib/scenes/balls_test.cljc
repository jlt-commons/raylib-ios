(ns raylib.scenes.balls-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.balls :as b]))

(def metrics {:screen [1206 2334]})
(def dims (b/dimensions metrics))

(deftest spawning-is-deterministic-and-inside-the-box
  (let [[balls _] (b/spawn dims b/default-seed)
        [again _] (b/spawn dims b/default-seed)
        [other _] (b/spawn dims 999)]
    (is (= b/ball-count (count balls)))
    (is (= balls again) "same seed, same set")
    (is (not= balls other) "different seed, different set")
    (testing "every ball starts fully inside its walls, not straddling one"
      (doseq [{:keys [x y r]} balls]
        (is (<= r x (- (:w dims) r)))
        (is (>= y r))))
    (testing "and they start in the upper part, so there is somewhere to fall"
      (is (every? #(< (:y %) (* 0.4 (:h dims))) balls)))))

(deftest nothing-escapes-the-box
  (testing "including at speed, which is what the position clamp is for: a ball
            that lands fast enough tunnels through a naive bounce and never
            comes back"
    (let [[balls _] (b/spawn dims b/default-seed)
          run (nth (iterate (fn [bs] (mapv #(b/step dims %) bs)) balls) 600)]
      (doseq [{:keys [x y r]} run]
        (is (<= (- r 1e-6) x (+ 1e-6 (- (:w dims) r))))
        (is (<= (+ y r) (+ 1e-6 (:h dims))))))))

(deftest gravity-pulls-down-and-bounces-decay
  (testing "a ball in free space accelerates downward"
    (let [ball {:x 500.0 :y 100.0 :vx 0.0 :vy 0.0 :r 20.0}
          one (b/step dims ball)
          two (b/step dims one)]
      (is (> (:vy one) 0.0))
      (is (> (:vy two) (:vy one)) "and keeps accelerating")))
  (testing "and a bounce returns less than it arrived with, which is what
            restitution below one means and why the scene ever settles"
    (let [falling {:x 500.0 :y (- (:h dims) 21.0) :vx 0.0 :vy 12.0 :r 20.0}
          after (b/step dims falling)]
      (is (neg? (:vy after)) "it is going up now")
      (is (< (abs (:vy after)) 12.0) "but slower than it came down"))))

(deftest a-settled-board-restarts
  (testing "there is no respawn key, so the scene watches its own energy"
    (let [dead (mapv (fn [i] {:x (+ 100.0 (* i 50)) :y (- (:h dims) 20.0)
                              :vx 0.0 :vy 0.0 :r 20.0 :colour [0 0 0]})
                     (range b/ball-count))
          state {:balls dead :seed 5 :settled 0}
          after (b/advance state metrics)]
      (is (< (b/energy dims dead) (* 0.02 (:h dims))) "this board is settled")
      (is (not= dead (:balls after)) "so it respawned"))))

(deftest energy-falls-as-the-balls-settle
  (let [[balls _] (b/spawn dims b/default-seed)
        after (nth (iterate (fn [bs] (mapv #(b/step dims %) bs)) balls) 500)]
    (is (< (b/energy dims after) (b/energy dims balls)))))
