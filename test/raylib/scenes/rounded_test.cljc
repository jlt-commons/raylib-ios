(ns raylib.scenes.rounded-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.rounded :as rnd]))

(def d (rnd/dimensions {:screen [1206 2334]}))

(deftest at-zero-radius-the-cross-is-exactly-the-rectangle
  (testing "the degenerate end that a decomposition gets wrong. With no corner
            disks to hide behind, both arms of the cross must cover the whole
            shape on their own."
    (let [{:keys [rects corners]} (rnd/parts d 0.0)]
      (is (empty? corners) "no disks at zero")
      (doseq [[x y w h] rects]
        (is (< (abs (- (:x d) x)) 1e-9))
        (is (< (abs (- (:y d) y)) 1e-9))
        (is (< (abs (- (:rect-w d) w)) 1e-9))
        (is (< (abs (- (:rect-h d) h)) 1e-9))))))

(deftest at-full-radius-the-arms-still-meet
  (testing "the other degenerate end: radius is half the shorter side, so one
            arm collapses to zero width there and the other must still span it"
    (let [r (:max-radius d)
          {:keys [rects corners]} (rnd/parts d r)]
      (is (= 4 (count corners)))
      (doseq [[_ _ w h] rects]
        (is (>= w 0.0) "no negative width")
        (is (>= h 0.0) "no negative height"))
      (testing "and the shorter dimension is exactly consumed"
        (let [shorter (min (:rect-w d) (:rect-h d))]
          (is (< (abs (- shorter (* 2 r))) 1e-9)))))))

(deftest the-cross-never-inverts
  (testing "a radius past half the shorter side would give negative extents,
            which raylib draws as nothing rather than as an error"
    (doseq [t (range 0.0 12.0 0.05)]
      (let [r (rnd/radius-at d t)
            {:keys [rects]} (rnd/parts d r)]
        (doseq [[_ _ w h] rects]
          (is (>= w -1e-9) (str "t=" t))
          (is (>= h -1e-9) (str "t=" t)))))))

(deftest the-corners-sit-inside-the-rectangle
  (let [r (* 0.5 (:max-radius d))
        {:keys [corners]} (rnd/parts d r)
        x1 (+ (:x d) (:rect-w d)) y1 (+ (:y d) (:rect-h d))]
    (is (= 4 (count corners)))
    (doseq [[cx cy _ _] corners]
      (is (<= (+ (:x d) r) cx (- x1 r)))
      (is (<= (+ (:y d) r) cy (- y1 r))))
    (testing "and each covers a distinct quarter turn"
      (is (= 4 (count (distinct (map (fn [c] [(nth c 2) (nth c 3)]) corners)))))
      (doseq [[_ _ start end] corners]
        (is (< (abs (- 90.0 (- end start))) 1e-9) "a quarter, not more")))))

(deftest the-radius-visits-both-ends-of-its-range
  (let [rs (map (fn [t] (rnd/radius-at d t)) (range 0.0 (* 3 rnd/cycle-seconds) 0.01))]
    (is (< (apply min rs) 1e-6) "reaches square")
    (is (> (apply max rs) (- (:max-radius d) 1e-6)) "reaches fully round")
    (doseq [r rs]
      (is (<= -1e-9 r (+ (:max-radius d) 1e-9))))))

(deftest the-rectangle-fits-the-screen
  (is (>= (:x d) 0.0))
  (is (>= (:y d) 0.0))
  (is (<= (+ (:x d) (:rect-w d)) 1206.0))
  (is (<= (+ (:y d) (:rect-h d)) 2334.0)))
