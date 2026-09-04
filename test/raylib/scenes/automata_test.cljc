(ns raylib.scenes.automata-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.automata :as a]))

(def metrics {:screen [1206 2622]})

(defn- binomial [n k]
  (reduce * 1 (map (fn [i] (/ (- n i) (inc i))) (range k))))

(deftest rule-90-is-the-sierpinski-triangle
  (testing "rule 90 is XOR of the two neighbours, so generation n is row n of
            Pascal's triangle mod two. The expectation is computed from the
            binomials rather than written out, because writing it out is how
            this test was wrong the first time: row 8 is 8 = 2^3, so by Kummer
            only its two ends are odd, and a hand-written row 8 looked much
            busier than that."
    (let [cols 65
          mid  (quot cols 2)]
      (doseq [n [1 2 3 4 5 8]]
        (let [row  (nth (iterate #(a/next-row % 90) (a/seed-row cols)) n)
              live (vec (keep-indexed (fn [i v] (when (= 1 v) (- i mid))) row))
              odd  (vec (for [k (range (inc n)) :when (odd? (binomial n k))]
                          (- (* 2 k) n)))]
          (is (= odd live) (str "generation " n)))))))

(deftest rule-numbers-are-read-as-neighbourhood-bits
  (testing "rule 0 kills everything and rule 255 fills everything"
    (let [row (a/seed-row 21)]
      (is (every? zero? (a/next-row row 0)))
      (is (every? #(= 1 %) (a/next-row row 255)))))
  (testing "rule 30 from one cell spreads by one column each side per
            generation. This measures the SPAN, first live cell to last, not the
            population: rule 30 is chaotic and its live count goes up and down
            (1, 3, 3, 6, 4 for the first five), which is what makes it rule 30."
    (let [cols 41
          span (fn [row]
                 (let [on (keep-indexed (fn [i v] (when (= 1 v) i)) row)]
                   (inc (- (apply max on) (apply min on)))))]
      (is (= [1 3 5 7 9]
             (mapv span (take 5 (iterate #(a/next-row % 30) (a/seed-row cols)))))))))

(deftest the-strip-wraps
  (testing "a live cell at the last column influences the first, since the row
            is a ring rather than a strip with ends"
    (let [row (assoc (vec (repeat 9 0)) 8 1)
          nxt (a/next-row row 30)]
      (is (= 1 (nth nxt 0))))))

(deftest runs-encode-adjacent-cells-into-one-rectangle
  (testing "a run is [start length]"
    (is (= [[0 1]]           (a/runs [1 0 0])))
    (is (= [[1 3]]           (a/runs [0 1 1 1])))
    (is (= [[0 2] [4 1]]     (a/runs [1 1 0 0 1])))
    (is (= []                (a/runs [0 0 0])))
    (is (= [[0 3]]           (a/runs [1 1 1]))))
  (testing "and encodes exactly the live cells, never more or fewer"
    (doseq [rule [30 90 110 150]]
      (let [row (nth (iterate #(a/next-row % rule) (a/seed-row 51)) 12)]
        (is (= (reduce + row) (reduce + (map second (a/runs row))))
            (str "rule " rule))))))

(deftest the-window-is-bounded
  (testing "it scrolls rather than growing, so the draw cost stays flat"
    (let [{:keys [cols rows]} (a/dimensions metrics)
          s0 {:row (a/seed-row cols) :window [(a/runs (a/seed-row cols))]
              :rule-index 0 :generation 0 :t 0}
          run (nth (iterate #(a/advance % metrics) s0)
                   (* a/ticks-per-generation (+ rows 40)))]
      (is (= rows (count (:window run)))))))

(deftest the-rules-cycle-on-their-own
  (testing "there is no keyboard, so a rule change has to come from the clock"
    (let [{:keys [cols]} (a/dimensions metrics)
          s0 {:row (a/seed-row cols) :window [(a/runs (a/seed-row cols))]
              :rule-index 0 :generation 0 :t 0}
          after (nth (iterate #(a/advance % metrics) s0)
                     (* a/ticks-per-generation (inc a/generations-per-rule)))]
      (is (= 1 (:rule-index after)))
      (is (= (second a/rules) (a/rule-of after)))
      (testing "and it restarts from a single cell rather than continuing
                mid-pattern, so the window is back to almost nothing"
        (is (<= (count (:window after)) 2))
        (is (= [[(quot cols 2) 1]] (first (:window after)))
            "the first row of the new rule is the single centre cell")))))
