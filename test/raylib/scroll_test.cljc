(ns raylib.scroll-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scroll :as sc]))

(def viewport {:width 1206 :height 2334})
(def sizes {:margin 48 :title-size 64 :body-size 34 :line-gap 16})

(deftest a-short-list-does-not-scroll
  (testing "and is not stretched either: two cards on a tall screen stay two
            cards rather than becoming two half-screen slabs"
    (doseq [n [0 1 2 4]]
      (let [c (sc/content-height viewport sizes n 2)]
        (is (= (:height viewport) c) (str n " cards"))
        (is (zero? (sc/max-scroll c (:height viewport))))))))

(deftest a-long-list-grows-past-the-screen
  (let [c (sc/content-height viewport sizes 27 2)]
    (is (> c (:height viewport)))
    (is (pos? (sc/max-scroll c (:height viewport))))
    (testing "and grows monotonically with the number of cards"
      (let [hs (map (fn [n] (sc/content-height viewport sizes n 2)) [10 20 30 40 50])]
        (is (apply <= hs))
        (is (< (first hs) (last hs)))))))

(deftest cards-keep-a-usable-height-however-many-there-are
  (testing "which is the whole point: the pure layout divides the height it is
            given by the row count, so a fixed screen makes cards shrink without
            limit. Giving it a taller screen keeps them readable."
    (let [floor (* sc/min-card-height (min (:width viewport) (:height viewport)))]
      (doseq [n [20 40 80]]
        (let [c (sc/content-height viewport sizes n 2)
              rows (quot (+ n 1) 2)
              gap (max 12 (quot (:margin sizes) 2))
              cards-y (+ (:margin sizes) (:title-size sizes) (* 2 (:line-gap sizes)))
              footer (+ (:margin sizes) (* 2 (:line-gap sizes)) (:body-size sizes))
              per (/ (- c cards-y footer (* (dec rows) gap)) (double rows))]
          (is (>= per (dec floor)) (str n " cards gave " per " per card")))))))

(deftest the-offset-cannot-leave-its-range
  (let [c 5000 v 2334]
    (is (zero? (sc/clamp -900 c v)) "cannot scroll above the top")
    (is (= (sc/max-scroll c v) (sc/clamp 99999 c v)) "or below the bottom")
    (is (= 1000 (sc/clamp 1000 c v)))
    (testing "and a list that fits cannot scroll at all"
      (is (zero? (sc/clamp 500 2000 2334))))))

(deftest the-list-follows-the-finger
  (testing "dragging up moves the content up, so the list tracks the finger
            rather than opposing it"
    (let [d (sc/begin-drag 0 [0 1000])]
      (is (== 300 (sc/scroll-for d [0 700] 5000 2334)) "finger up 300, list up 300")
      (is (zero? (sc/scroll-for d [0 1300] 5000 2334)) "finger down, clamped at the top"))
    (let [d (sc/begin-drag 1000 [0 1000])]
      (is (== 1300 (sc/scroll-for d [0 700] 5000 2334)) "from an existing offset")
      (is (== 700 (sc/scroll-for d [0 1300] 5000 2334))))))

(deftest travel-is-the-furthest-from-the-start-not-the-last-step
  (testing "a finger that wanders away and comes back has still scrolled, and
            must not then count as a tap on whatever it happens to land on"
    (let [d (-> (sc/begin-drag 0 [0 1000])
                (sc/drag-to [0 600])
                (sc/drag-to [0 1000]))]
      (is (= 400.0 (:travel d)))
      (is (not (sc/tap? d 1206))))))

(deftest a-still-finger-is-a-tap-and-a-moving-one-is-not
  (let [shorter 1206
        slop (* sc/tap-slop shorter)]
    (is (sc/tap? (sc/begin-drag 0 [0 500]) shorter) "no movement at all")
    (is (sc/tap? (sc/drag-to (sc/begin-drag 0 [0 500]) [0 (+ 500 (dec slop))]) shorter)
        "a shaky finger inside the slop")
    (is (not (sc/tap? (sc/drag-to (sc/begin-drag 0 [0 500]) [0 (+ 500 slop 5)]) shorter))
        "past the slop is a scroll")
    (testing "and no gesture at all is not a tap"
      (is (not (sc/tap? nil shorter))))))

(deftest the-thumb-appears-only-when-there-is-something-to-scroll
  (is (nil? (sc/thumb 0 2000 2334)) "content shorter than the viewport")
  (is (nil? (sc/thumb 0 2334 2334)) "exactly equal")
  (is (some? (sc/thumb 0 5000 2334))))

(deftest the-thumb-spans-the-track-and-never-vanishes
  (let [v 2334]
    (doseq [c [2500 5000 20000 200000]]
      (let [[y h] (sc/thumb 0 c v)]
        (is (>= h (* 0.06 v)) (str "content " c ": thumb stays grabbable"))
        (is (<= h v))
        (is (zero? y) "at the top of the track when unscrolled"))
      (let [[y h] (sc/thumb (sc/max-scroll c v) c v)]
        (is (< (abs (- v (+ y h))) 1e-6) (str "content " c ": reaches the bottom"))))))

(deftest the-thumb-moves-with-the-scroll
  (let [c 8000 v 2334
        ys (map (fn [s] (first (sc/thumb s c v)))
                (range 0 (inc (sc/max-scroll c v)) 400))]
    (is (apply < ys) "monotonic")))

(deftest a-release-must-not-be-fed-into-the-drag
  (testing "raylib answers GetTouchX and GetTouchY with whatever it last had
            even when no finger is down, so the release frame's position is not
            the touch that just ended. Feeding it to drag-to inflated travel to
            1941 pixels on a motionless tap, and every tap read as a scroll.
            The host accumulates travel on :down only; this pins what would
            happen if it stopped doing so."
    (let [d (sc/begin-drag 0 [316 1941])
          poisoned (sc/drag-to d [0 0])]
      (is (sc/tap? d 1206) "the gesture as it actually was")
      (is (not (sc/tap? poisoned 1206)) "and as a stray release frame makes it look")
      (is (= [316 1941] (sc/tap-point d))))))

(deftest the-offset-is-always-integral
  (testing "it is added to rectangles the pure layout already rounded to ints,
            and a fractional one makes a card's :y a double, which raylib's
            DrawRectangle binding rejects at the FFI boundary. The synthetic
            drag interpolates in doubles, so this is reachable from a swipe."
    (doseq [o [0 1 999.5 1234.7 -3.2 1e9]]
      (let [v (sc/clamp o 5000 2334)]
        (is (integer? v) (str o " gave " v))))
    (is (integer? (sc/scroll-for (sc/begin-drag 0 [0 1000.0]) [0 613.4] 5000 2334)))))
