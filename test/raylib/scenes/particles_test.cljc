(ns raylib.scenes.particles-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.particles :as p]))

(def m {:screen [1206 2334]})
(def d (p/dimensions m))
(def start (first ((:init (p/scene)) {:metrics m})))

(defn- step [state phase position]
  (p/advance state {:metrics m
                    :pointer {:phase phase
                              :position position}}))

(defn- hold
  "`n` frames with a finger down at `pt`, the first as a press."
  [state n pt]
  (reduce (fn [s i] (step s (if (zero? i) :press :down) pt)) state (range n)))

(def mid [600 1200])

(defn- one
  "A state holding a single particle of `type` at mid-screen, as the emitter
  would have made it."
  [type]
  (let [[particle _] (p/emit-particle d type 600 1200 1)]
    (assoc start :type type :particles [particle])))

(deftest particles-emit-only-while-touching
  (is (empty? (:particles (step start :idle nil))))
  (is (= p/per-frame (count (:particles (step start :press mid)))))
  (is (= (* 2 p/per-frame) (count (:particles (hold start 2 mid)))))
  (testing "they come out at the finger"
    (let [[px py] [(:x (first (:particles (step start :press mid))))
                   (:y (first (:particles (step start :press mid))))]]
      (is (= [600.0 1200.0] [px py])))))

(deftest each-type-moves-the-way-the-original-does
  (testing "water falls: y rises faster than gravity-free drift alone"
    (let [a (one :water)
          b (-> a (step :idle nil) (step :idle nil) (step :idle nil))
          pa (first (:particles a))
          pb (first (:particles b))]
      (is (> (:y pb) (:y pa)))
      (is (> (:vy pb) (:vy pa)))))
  (testing "smoke rises and its alpha falls"
    (let [a (one :smoke)
          b (reduce (fn [s _] (step s :idle nil)) a (range 20))
          pa (first (:particles a))
          pb (first (:particles b))]
      (is (< (:vy pb) (:vy pa)))
      (is (> (:radius pb) (:radius pa)))
      (is (< (nth (p/colour pb) 3) (nth (p/colour pa) 3)))))
  (testing "fire shrinks and runs from yellow toward red"
    (let [a (one :fire)
          b (reduce (fn [s _] (step s :idle nil)) a (range 20))
          pa (first (:particles a))
          pb (first (:particles b))]
      (is (< (:radius pb) (:radius pa)))
      (is (< (nth (p/colour pb) 1) (nth (p/colour pa) 1))))))

(deftest the-info-box-cycles-the-type
  (let [[bx by bw bh] (:info-rect d)
        inside [(+ bx (* 0.5 bw)) (+ by (* 0.5 bh))]
        t1 (step start :press inside)
        t2 (step t1 :press inside)
        t3 (step t2 :press inside)]
    (is (= [:water :smoke :fire :water] (mapv :type [start t1 t2 t3])))
    (testing "a press in the box does not also emit, and holding does not cycle"
      (is (empty? (:particles t1)))
      (is (= :smoke (:type (step t1 :down inside)))))
    (testing "a press elsewhere leaves the type alone"
      (is (= :water (:type (step start :press mid)))))))

(deftest live-particles-are-capped
  (testing "the default cap holds over 600 frames of a finger held down"
    (is (<= (count (:particles (hold start 600 mid))) p/max-particles)))
  (testing "a cap below the steady state is reached exactly and never passed"
    ;; On this tall screen water leaves in about 190 live particles, so the
    ;; default cap of 600 never bites by itself. Lowering it does.
    (with-redefs [p/max-particles 100]
      (let [s (hold start 600 mid)]
        (is (= 100 (count (:particles s))))))))

(deftest dead-particles-are-removed
  (testing "smoke past 1.8 s"
    (let [[particle _] (p/emit-particle d :smoke 600 1200 1)
          old (assoc start :particles [(assoc particle :age 1.79)])]
      (is (empty? (:particles (step old :idle nil))))))
  (testing "fire that has shrunk away"
    (let [[particle _] (p/emit-particle d :fire 600 1200 1)
          tiny (assoc start :particles [(assoc particle :radius 0.1)])]
      (is (empty? (:particles (step tiny :idle nil))))))
  (testing "anything that has left the screen"
    (let [[particle _] (p/emit-particle d :water 600 1200 1)
          gone (assoc start :particles [(assoc particle :x -500.0 :vx 0.0)])]
      (is (empty? (:particles (step gone :idle nil)))))))

(deftest a-release-emits-nothing
  (let [held (hold start 3 mid)
        released (step held :release [1100 2200])
        before (:particles held)]
    (is (= (count before) (count (:particles released))))
    (is (= (:type held) (:type released)))
    (testing "an idle frame with no position after a drag does not throw or emit"
      (is (= (count before) (count (:particles (step held :idle nil))))))
    (testing "a release inside the info box does not cycle the type"
      (let [[bx by] (:info-rect d)]
        (is (= :water (:type (step start :release [(+ bx 10) (+ by 10)]))))))))

(deftest same-seed-same-particles
  (let [run (fn [] (hold start 30 mid))]
    (is (= (run) (run)))
    (testing "a different seed gives different particles"
      (is (not= (:particles (run))
                (:particles (hold (assoc start :seed 7) 30 mid)))))
    (testing "the high bits vary, so speeds are not stuck on one value"
      (is (> (count (distinct (map :vx (:particles (run))))) 10)))))

(deftest buttons-avoid-the-back-target
  (testing "Back is at the top-left; estimate: [0 0 400 120] covers it"
    (let [[x y w h] (:info-rect d)]
      (is (or (>= x 400) (<= (+ x w) 0) (>= y 120) (<= (+ y h) 0))))))

(deftest text-lines-fit-the-safe-region
  (let [[w h] (:screen m)
        {:keys [text-size line1-x line1-y line2-x line2-y info-rect]} d
        [bx by bw bh] info-rect
        ;; estimate: 0.6 of the size per character, for raylib's default font.
        ;; The longest the second line gets is a full cap of particles.
        char-w (* 0.6 text-size)
        line2 (p/type-line {:type :water
                            :particles (vec (range p/max-particles))})]
    (doseq [[x y text] [[line1-x line1-y p/info-line] [line2-x line2-y line2]]]
      (is (<= 0 y))
      (is (<= (+ y text-size) h))
      (is (<= (+ x (* char-w (count text))) w))
      (testing "and inside the box"
        (is (<= (+ x (* char-w (count text))) (+ bx bw)))
        (is (<= by y))
        (is (<= (+ y text-size) (+ by bh)))))
    (is (<= (+ bx bw) w))))
