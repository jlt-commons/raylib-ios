(ns raylib.stick-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.stick :as stick]))

(def m {:screen [1206 2334]})
(def slop (gesture/slop m))
(def a [600.0 1200.0])
(def b [300.0 1700.0])
(def button [[100.0 1000.0] [200.0 1100.0]])

(defn- in-button? [[x y]]
  (let [[[x0 y0] [x1 y1]] button]
    (and (<= x0 x x1) (<= y0 y y1))))

(defn- frame
  [phase points ids]
  {:points points
   :ids ids
   :metrics m
   :press? (= :press phase)
   :free? (complement in-button?)
   :start? (fn [[_ y]] (> y 300.0))})

(defn- step
  "One frame of the tracker, with `world` = `{:stick :prev}`."
  [{:keys [stick prev]} phase points ids]
  (let [f (frame phase points ids)]
    {:stick (stick/next-stick stick prev f)
     :prev {:pts points
            :ids ids
            :n (count points)}}))

(def start {:stick nil
            :prev {:pts []
                   :ids nil
                   :n 0}})

(defn- go [world & frames]
  (reduce (fn [w [phase points ids]] (step w phase points ids)) world frames))

(defn- at [[x y] dx dy] [(+ x dx) (+ y dy)])

(deftest ids-of-needs-one-id-per-point
  (is (= [7 9] (stick/ids-of {:touches {:ids [7 9]}} [a b])))
  (is (nil? (stick/ids-of {:touches {:ids [7]}} [a b])))
  (is (nil? (stick/ids-of {} [a])))
  (is (nil? (stick/ids-of {:touches {:ids []}} []))))

(deftest a-fresh-press-starts-it
  (testing "with ids"
    (let [w (go start [:press [a] [4]])]
      (is (= {:centre a
              :at a
              :id 4}
             (:stick w)))))
  (testing "without ids"
    (let [w (go start [:press [a] nil])]
      (is (= a (get-in w [:stick :centre])))
      (is (nil? (get-in w [:stick :id])))))
  (testing "a press outside the start area or on a button starts nothing"
    (is (nil? (:stick (go start [:press [[600.0 100.0]] [4]]))))
    (is (nil? (:stick (go start [:press [[150.0 1050.0]] [4]]))))))

(deftest a-resting-finger-never-becomes-the-stick
  (doseq [[label ids] [["with ids" [[4] [4 5] [4 5]]] ["without ids" [nil nil nil]]]]
    (testing label
      (let [[i1 i2 i3] ids
            rest-then-land (go start [:down [a] i1] [:press [a b] i2])
            moved (step rest-then-land :down [(at a 200.0 0.0) b] i3)]
        (is (= b (get-in rest-then-land [:stick :centre])) "only the new finger starts it")
        (is (= b (get-in moved [:stick :at])))))
    (testing (str label ", a finger down when the scene opened")
      (is (nil? (:stick (go start [:down [a] (when (= label "with ids") [4])])))))))

(deftest its-own-finger-lifting-ends-it-and-nothing-takes-over
  (testing "with ids"
    (let [held (go start [:press [a] [4]] [:press [a b] [4 5]])
          lifted (step held :down [b] [5])
          later (go lifted [:down [(at b 5.0 0.0)] [5]])]
      (is (some? (:stick held)))
      (is (nil? (:stick lifted)))
      (is (nil? (:stick later)))))
  (testing "without ids, the other finger is out of reach"
    (let [held (go start [:press [a] nil] [:press [a b] nil])
          lifted (step held :down [b] nil)]
      (is (some? (:stick held)))
      (is (nil? (:stick lifted)) "b is 590 px from a, further than 0.3 of 1206"))))

(deftest a-one-frame-reversal-keeps-it
  (let [pushed (go start [:press [a] [4]] [:down [(at a 100.0 0.0)] [4]])]
    (testing "with ids, 200 px"
      (let [s (step pushed :down [(at a -100.0 0.0)] [4])]
        (is (= (at a -100.0 0.0) (get-in s [:stick :at])))))
    (testing "with ids, however far"
      (let [s (step pushed :down [(at a -500.0 0.0)] [4])]
        (is (= (at a -500.0 0.0) (get-in s [:stick :at])))))
    (testing "without ids, 200 px"
      (let [w (go start [:press [a] nil] [:down [(at a 100.0 0.0)] nil])
            s (step w :down [(at a -100.0 0.0)] nil)]
        (is (= (at a -100.0 0.0) (get-in s [:stick :at])))))))

(deftest the-fallback-bound-is-0-3-of-the-shorter-side
  (let [reach (* 0.3 1206.0)
        w (go start [:press [a] nil])]
    (is (= 0.3 stick/follow-fraction))
    (testing "just under the bound is followed"
      (let [p (at a (- reach 1.0) 0.0)]
        (is (= p (get-in (step w :down [p] nil) [:stick :at])))))
    (testing "just over it ends the stick"
      (is (nil? (:stick (step w :down [(at a (+ reach 1.0) 0.0)] nil)))))))

(deftest a-finger-sliding-onto-a-button-ends-it
  (let [[[x0 y0] _] button
        on-button [(+ x0 20.0) (+ y0 20.0)]
        start-pt [(+ x0 20.0) (- y0 100.0)]]
    (doseq [ids [[4] nil]]
      (let [w (go start [:press [start-pt] ids])
            slid (step w :down [on-button] ids)
            back (step slid :down [start-pt] ids)]
        (is (some? (:stick w)))
        (is (nil? (:stick slid)))
        (is (nil? (:stick back)) "and it does not start again")))))

(deftest a-second-finger-landing-leaves-the-stick-alone
  (doseq [[ids1 ids2] [[[4] [4 5]] [nil nil]]]
    (let [w (go start [:press [a] ids1] [:down [(at a 100.0 0.0)] ids1])
          both (step w :press [(at a 100.0 0.0) b] ids2)]
      (is (= a (get-in both [:stick :centre])))
      (is (= (at a 100.0 0.0) (get-in both [:stick :at]))))))

(deftest follow-and-fresh-stand-alone
  (let [f (frame :down [a b] [4 5])]
    (is (= {:at b
            :id 5} (stick/follow {:at (at b 50.0 0.0)
                                  :id 5} f)))
    (is (nil? (stick/follow nil f)))
    (is (nil? (stick/follow {:at a
                             :id 6} f)))
    (is (= [{:at b
             :id 5}] (stick/fresh (assoc f :press? false) {:pts [a]
                                                           :ids [4]
                                                           :n 1})))
    (is (nil? (stick/fresh (assoc f :press? false) {:pts []
                                                    :ids nil
                                                    :n 0})))))

(deftest begin-owners-starts-each-region-on-its-own-fresh-finger
  (let [region (fn [[x _]] (cond (< x 400.0) :look (< x 900.0) :stick))
        l [100.0 800.0]
        s [600.0 1200.0]
        f (fn [& pts] (mapv (fn [p] {:at p
                                     :id nil}) pts))]
    (testing "one look and one stick, each from its region"
      (is (= {:look {:at l
                     :id nil}
              :stick {:centre s
                      :at s
                      :id nil}}
             (stick/begin-owners {} region (f l s)))))
    (testing "an existing owner is kept, and its finger is not adopted twice"
      (let [held {:look {:at l
                         :id 1}}]
        (is (= held (stick/begin-owners held region (f l))))
        (is (= (assoc held :stick {:centre s
                                   :at s
                                   :id nil})
               (stick/begin-owners held region (f l s))))))
    (testing "a finger outside every region is nothing, and no fresh finger begins nothing"
      (is (= {} (stick/begin-owners {} region (f [1000.0 800.0]))))
      (is (= {} (stick/begin-owners {} region []))))))

(deftest follow-pair-moves-each-owner-to-its-own-finger
  (let [look {:at a
              :id 4}
        stick {:centre b
               :at b
               :id 5}]
    (testing "with ids, each reads its own point whatever the order"
      (let [f (frame :down [(at b 10.0 0.0) (at a 10.0 0.0)] [5 4])
            [l s] (stick/follow-pair look stick f)]
        (is (= (at a 10.0 0.0) (:at l)))
        (is (= (at b 10.0 0.0) (:at s)))))
    (testing "a lifted owner ends and the other finger is not adopted"
      (let [f (frame :down [b] [5])
            [l s] (stick/follow-pair look stick f)]
        (is (nil? l))
        (is (= b (:at s)))))
    (testing "either owner may be nil"
      (is (= [nil nil] (stick/follow-pair nil nil (frame :down [a] [4])))))
    (testing "without ids, one point wanted by both goes to the owner that moved less"
      (let [look {:at a}
            stick {:centre a
                   :at (at a 100.0 0.0)}
            f (frame :down [(at a 40.0 0.0)] nil)
            [l s] (stick/follow-pair look stick f)]
        (is (= (at a 40.0 0.0) (:at l)))
        (is (nil? s))))))
