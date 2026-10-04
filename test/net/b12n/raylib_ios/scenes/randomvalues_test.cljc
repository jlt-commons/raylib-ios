(ns net.b12n.raylib-ios.scenes.randomvalues-test
  (:require [clojure.string]
            [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.scenes.randomvalues :as rv]))

(defn- start [] (first ((:init (rv/scene)) {})))

(defn- step [state] (rv/advance state {}))

(deftest a-new-value-every-120-frames
  (let [states (take 241 (iterate step (start)))
        grew (->> (map vector states (rest states))
                  (keep-indexed (fn [i [a b]]
                                  (when (not= (count (:history a)) (count (:history b)))
                                    (inc i))))
                  vec)]
    (testing "rolls happen at frames 120 and 240 and nowhere else"
      (is (= [120 240] grew)))
    (testing "and between rolls the value holds"
      (doseq [[a b] (map vector states (rest states))
              :when (= (count (:history a)) (count (:history b)))]
        (is (= (:value a) (:value b)))))))

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

(defn- rolls [n]
  (take n (map first (rest (iterate (fn [[_ seed]] (rv/roll seed)) [0 rv/default-seed])))))

(deftest rolls-do-not-alternate-parity
  (let [vs (rolls 1000)]
    (is (some (fn [[a b]] (= (even? a) (even? b))) (map vector vs (rest vs))))))

(deftest every-value-is-reachable
  (is (= 100 (count (set (rolls 10000))))))

(deftest text-lines-fit-the-safe-region
  (let [[w h] [1206 2334]
        {:keys [label-size big-size caption-x caption-y value-y recent-x recent-y]}
        (rv/dimensions {:screen [w h]})
        ;; estimate: 0.6 of the size per character, for raylib's default font
        cw (fn [size text] (* 0.6 size (count text)))
        recent (str "recent: " (clojure.string/join " " (repeat 8 "99")))]
    (doseq [[text x y size] [["a new random value every 2 seconds" caption-x caption-y label-size]
                             [recent recent-x recent-y label-size]
                             ["99" 0 value-y big-size]]]
      (is (<= 0 y) text)
      (is (<= (+ y size) h) text)
      (is (<= (+ x (cw size text)) w) text))))
