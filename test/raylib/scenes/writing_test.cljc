(ns raylib.scenes.writing-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [raylib.scenes.writing :as w]))

(def dims (w/dimensions {:screen [1206 2334]}))

(deftest the-message-grows-one-character-at-a-time
  (is (= "" (w/visible 0)))
  (testing "each character takes frames-per-character frames"
    (is (= 1 (count (w/visible w/frames-per-character))))
    (is (= 2 (count (w/visible (* 2 w/frames-per-character))))))
  (testing "and every prefix is a real prefix of the message, never a slice"
    (doseq [t (range 0 400 7)]
      (is (str/starts-with? w/message (w/visible t)) (str "at t=" t)))))

(deftest it-holds-the-finished-message-before-restarting
  (testing "without the pause the sentence completes and restarts on adjacent
            frames, so it is never on screen long enough to read"
    (let [done (filter w/complete? (range 0 (* w/frames-per-character
                                              (+ (count w/message) w/pause-frames))))]
      (is (>= (count done) (* w/frames-per-character (dec w/pause-frames)))
          "the pause should be most of a hundred frames"))))

(deftest it-loops
  (let [period (* w/frames-per-character (+ (count w/message) w/pause-frames))]
    (is (= (w/visible 0) (w/visible period)))
    (is (= (w/visible 30) (w/visible (+ 30 period))))))

(deftest wrapping-preserves-the-words
  (testing "no line exceeds the column count"
    (doseq [line (w/wrap w/message (:columns dims))]
      (is (<= (count line) (:columns dims)) (pr-str line))))
  (testing "and rejoining gives the message back, so nothing is lost or doubled"
    (is (= w/message (str/join " " (w/wrap w/message (:columns dims))))))
  (testing "a word longer than a line still appears rather than vanishing"
    (let [long-word "supercalifragilistic"]
      (is (some #(str/includes? % long-word) (w/wrap (str "a " long-word " b") 8)))))
  (testing "empty text wraps to nothing rather than one empty line"
    (is (= [] (w/wrap "" 20)))))

(deftest the-text-fits-the-screen
  (let [lines (w/wrap w/message (:columns dims))]
    (is (<= (+ (:top dims) (* (count lines) (:line-height dims))) (:h dims))
        "the block stays above the bottom edge")))
