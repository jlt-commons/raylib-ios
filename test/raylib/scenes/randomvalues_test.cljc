(ns raylib.scenes.randomvalues-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.randomvalues :as rv]))

(defn- start [] (first ((:init (rv/scene)) {})))

(defn- step [state] (rv/advance state {}))

(deftest a-new-value-every-120-frames
  (let [states (take 241 (iterate step (start)))
        changed (->> (map vector states (rest states))
                     (keep-indexed (fn [i [a b]] (when (not= (:value a) (:value b)) (inc i))))
                     vec)]
    (testing "the value holds between rolls and changes at frames 120 and 240"
      (is (= [120 240] changed)))))

(deftest values-stay-in-0-to-99
  (let [values (take 10000 (map first (rest (iterate (fn [[_ seed]] (rv/roll seed))
                                                     [0 rv/default-seed]))))]
    (is (= 10000 (count values)))
    (is (every? (fn [v] (<= 0 v 99)) values))))

(deftest history-keeps-the-last-eight
  (let [s (nth (iterate step (start)) (* 11 rv/roll-every))]
    (testing "12 rolls in all, counting the one at frame 0"
      (is (= rv/history-length (count (:history s))))
      (is (= (:value s) (last (:history s)))))))

(deftest the-first-roll-is-the-first-history-entry
  (let [s (start)]
    (is (= [(:value s)] (:history s)))))

(deftest same-seed-same-sequence
  (is (= (nth (iterate step (start)) 600)
         (nth (iterate step (start)) 600))))
