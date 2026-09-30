(ns raylib.scenes.formattext-test
  (:require [clojure.test :refer [deftest is]]
            [raylib.scenes.formattext :as ft]))

(deftest frame-zero-reads-all-zeros
  (is (= ["SCORE: 00000000" "TIME: 00:00"] (ft/readouts 0))))

(deftest one-frame-is-seven-points
  (is (= "SCORE: 00000007" (first (ft/readouts 1)))))

(deftest minutes-carry-past-an-hour
  (is (= "TIME: 61:01" (second (ft/readouts (* 60 3661))))))

(deftest a-long-score-is-not-truncated
  (is (= "SCORE: 140000000" (first (ft/readouts 20000000)))))

(deftest advance-counts-frames
  (is (= {:frame 1} (ft/advance {:frame 0} {})))
  (is (= {:frame 3} (nth (iterate #(ft/advance % {}) {:frame 0}) 3))))

(deftest text-lines-fit-the-safe-region
  (let [[w h] [1206 2334]
        {:keys [size x score-y time-y]} (ft/dimensions {:screen [w h]})
        ;; estimate: 0.6 of the size per character, for raylib's default font
        cw (fn [text] (* 0.6 size (count text)))]
    ;; The longest a score can show here is 8 digits, and a time is at least
    ;; "TIME: 99:59".
    (doseq [[text y] [["SCORE: 99999999" score-y]
                      ["TIME: 99:59" time-y]]]
      (is (<= 0 y) text)
      (is (<= (+ y size) h) text)
      (is (<= (+ x (cw text)) w) text))))

(deftest the-readouts-fit-the-width
  (let [[w _] [1206 2334]
        {:keys [size]} (ft/dimensions {:screen [w 2334]})]
    ;; estimate: 0.6 of the size per glyph, for raylib's default font
    (is (< (* 0.6 size 20) w))))
