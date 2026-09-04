(ns raylib.scenes.sequence-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.sequence :as s]))

(deftest every-height-appears-exactly-once
  (testing "which is the whole point of the example: LoadRandomSequence upstream
            is a permutation, not sampling with replacement, and bars drawn from
            independent draws would have duplicates and gaps"
    (let [bs (s/bars 24)]
      (is (= 24 (count bs)))
      (is (= (set (range 24)) (set (map :rank bs))))
      (is (= 24 (count (distinct (map :fraction bs))))))))

(deftest shuffling-reorders-without-changing-the-set
  (let [bs (s/bars 24)
        [once seed] (s/shuffle-with bs s/default-seed)
        [twice _] (s/shuffle-with once seed)]
    (testing "the set of heights survives every shuffle"
      (is (= (set (map :rank bs)) (set (map :rank once))))
      (is (= (set (map :rank bs)) (set (map :rank twice)))))
    (testing "but the order changes, or nothing would be visible"
      (is (not= (map :rank bs) (map :rank once)))
      (is (not= (map :rank once) (map :rank twice))))
    (testing "and the count never drifts"
      (is (= (count bs) (count once) (count twice))))))

(deftest the-shuffle-is-reproducible
  (let [bs (s/bars 24)]
    (is (= (first (s/shuffle-with bs 12345))
           (first (s/shuffle-with bs 12345)))
        "same seed, same permutation")
    (is (not= (first (s/shuffle-with bs 1))
              (first (s/shuffle-with bs 2)))
        "different seeds differ")))

(deftest the-shuffle-is-not-obviously-biased
  (testing "Fisher-Yates walking downward and swapping with an index at or
            BELOW the current one gives every permutation equal probability.
            The off-by-one that picks from the whole vector does not, and looks
            fine. This checks the weaker property that every element reaches
            every position across many seeds, which the biased version would
            still pass, but a broken swap would not."
    (let [n 8
          positions (reduce (fn [acc seed]
                              (let [[sh _] (s/shuffle-with (s/bars n) seed)]
                                (reduce (fn [a [pos bar]] (update a (:rank bar) (fnil conj #{}) pos))
                                        acc (map-indexed vector sh))))
                            {} (range 300))]
      (doseq [rank (range n)]
        (is (= n (count (get positions rank)))
            (str "rank " rank " never reached every position"))))))

(deftest advancing-shuffles-on-schedule
  (let [start {:bars (s/bars 24) :seed 1 :t 0 :shuffles 0}
        just-before (nth (iterate s/advance start) (dec s/frames-per-shuffle))
        just-after (nth (iterate s/advance start) s/frames-per-shuffle)]
    (is (= 0 (:shuffles just-before)))
    (is (= 1 (:shuffles just-after)))
    (is (= (:bars start) (:bars just-before)) "unchanged until the tick")
    (is (not= (:bars start) (:bars just-after)) "and reordered on it")))
