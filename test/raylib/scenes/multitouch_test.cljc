(ns raylib.scenes.multitouch-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.multitouch :as mt]))

(defn- frame [pts ids] {:touch-points pts :touches {:ids ids :count (count pts)}})
(def empty-state (first ((:init (mt/scene)) {:metrics {:screen [1206 2334]}})))

(deftest every-point-gets-drawn-not-just-the-first
  (testing "which is the whole reason this scene exists: the desktop original
            could read coordinates for point 0 only"
    (let [s (mt/advance empty-state (frame [[10.0 20.0] [30.0 40.0] [50.0 60.0]] [0 1 2]))]
      (is (= 3 (count (:live s))))
      (is (= {0 [10.0 20.0] 1 [30.0 40.0] 2 [50.0 60.0]} (:live s))))))

(deftest trails-follow-the-id-not-the-index
  (testing "raylib does not promise ids are 0..n-1, and when a middle finger
            lifts the survivors shift down an index. Keyed by index, the two
            remaining fingers would swap trails on that lift."
    (let [s (-> empty-state
                (mt/advance (frame [[1.0 1.0] [2.0 2.0] [3.0 3.0]] [7 8 9]))
                (mt/advance (frame [[1.5 1.5] [2.5 2.5] [3.5 3.5]] [7 8 9]))
                ;; id 8 lifts; 9 is now at index 1, where 8 used to be
                (mt/advance (frame [[1.9 1.9] [3.9 3.9]] [7 9])))]
      (is (= #{7 9} (set (keys (:trails s)))) "the lifted id is dropped")
      (is (= [[3.0 3.0] [3.5 3.5] [3.9 3.9]] (get-in s [:trails 9]))
          "id 9 kept its own history rather than inheriting id 8's")
      (is (= [[1.0 1.0] [1.5 1.5] [1.9 1.9]] (get-in s [:trails 7]))))))

(deftest lifting-every-finger-clears-the-trails
  (let [s (-> empty-state
              (mt/advance (frame [[1.0 1.0]] [3]))
              (mt/advance (frame [] [])))]
    (is (= {} (:trails s)))
    (is (= {} (:live s)))
    (testing "but the high-water mark is kept, since it is the interesting number"
      (is (= 1 (:peak s))))))

(deftest the-peak-is-the-most-at-once-not-the-most-ever-seen
  (let [s (reduce mt/advance empty-state
                  [(frame [[1.0 1.0] [2.0 2.0]] [0 1])
                   (frame [[1.0 1.0] [2.0 2.0] [3.0 3.0] [4.0 4.0] [5.0 5.0]] [0 1 2 3 4])
                   (frame [[1.0 1.0]] [0])])]
    (is (= 5 (:peak s)))
    (is (= 1 (count (:live s))))))

(deftest a-trail-is-bounded
  (testing "so a finger held down for ten minutes costs what one held for two
            seconds costs"
    (let [s (reduce (fn [acc i] (mt/advance acc (frame [[(double i) 0.0]] [0])))
                    empty-state (range 400))]
      (is (= mt/trail-length (count (get-in s [:trails 0]))))
      (is (= [399.0 0.0] (last (get-in s [:trails 0]))) "and keeps the newest"))))

(deftest ids-fall-back-to-the-index-when-none-are-reported
  (testing "the synthetic tap path produces a point with no id list"
    (let [s (mt/advance empty-state {:touch-points [[9.0 9.0]] :touches {}})]
      (is (= {0 [9.0 9.0]} (:live s))))))

;; Ids real fingers produced on an iPhone 17 Pro, from three separate runs.
;; Every one is a multiple of 8, because iOS derives them from object pointers.
;;
;; The strides differ between runs and the third run sits in a different address
;; range entirely, because the app had relaunched. So neither the spacing nor the
;; magnitude can be relied on. The one invariant across all three is the
;; alignment, which is exactly what broke the original scheme.
(def real-ids [809313472 809313920 809314368 809317952])
(def real-ids-2 [809133248 809134144 809136832 809137728])
(def real-ids-3 [163292352 163292800 163294592])

(deftest real-device-ids-get-four-different-colours
  (testing "the bug this replaced: (mod id 8) sent all four to slot 0, because
            pointer-derived ids are 8-byte aligned, so every finger drew in the
            same blue. Shifting does not save it either, since they step by 448,
            itself a multiple of 8."
    (doseq [ids [real-ids real-ids-2 real-ids-3]]
      (is (every? (fn [id] (zero? (mod id 8))) ids) "the property that broke it")
      (is (= 1 (count (distinct (map (fn [id] (mod id 8)) ids))))
          "and under the old scheme all four collided on one slot")
      (let [slots (mt/assign-colours {} ids)
            n (count ids)]
        (is (= n (count (distinct (vals slots)))))
        (is (= n (count (distinct (map (fn [id] (mt/colour-for (get slots id))) ids)))))))))

(deftest slots-are-exact-not-probabilistic
  (testing "no hash can do this. Four fingers in eight slots collide 59% of the
            time even with a perfectly uniform hash, since P(all distinct) is
            8/8 x 7/8 x 6/8 x 5/8 = 41%. A murmur3 finalizer measured exactly
            that here, so it was already ideal and still wrong most of the time."
    (doseq [n (range 1 (inc (count mt/palette)))]
      (let [slots (mt/assign-colours {} (range 1000 (+ 1000 n)))]
        (is (= n (count (distinct (vals slots)))) (str n " simultaneous touches"))))))

(deftest a-finger-keeps-its-slot-while-it-is-down
  (let [a (mt/assign-colours {} [10 20 30])
        b (mt/assign-colours a [10 20 30])
        c (mt/assign-colours b [10 30])]
    (is (= a b) "an unchanged set of fingers does not reshuffle")
    (is (= (get a 10) (get c 10)) "and a survivor keeps its own slot")
    (is (= (get a 30) (get c 30)))))

(deftest a-lifted-finger-frees-its-slot-for-the-next-one
  (let [a (mt/assign-colours {} [10 20 30])
        freed (get a 20)
        b (mt/assign-colours a [10 30])
        c (mt/assign-colours b [10 30 40])]
    (is (not (contains? b 20)) "the lifted id is dropped")
    (is (= freed (get c 40)) "and the new finger takes the slot it left")
    (is (= 3 (count (distinct (vals c)))))))

(deftest more-fingers-than-colours-does-not-throw
  (testing "no phone reports this many, but the function should not explode"
    (let [slots (mt/assign-colours {} (range 20))]
      (is (= 20 (count slots)))
      (is (every? some? (map (fn [id] (mt/colour-for (get slots id))) (range 20)))))))
