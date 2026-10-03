(ns raylib.scenes.bunnymark-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.bunnymark :as sc]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(def metrics {:screen [1206 2334]})

(defn- fresh [] (first ((:init (sc/scene)) {:metrics metrics})))

(defn- tick
  ([state] (tick state :idle nil))
  ([state phase position]
   (first ((:update (sc/scene)) state {:metrics metrics
                                       :pointer {:phase phase
                                                 :position position}}))))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(def geo (sc/geometry metrics))
(def u (/ 1206.0 800.0))

(defn- rects
  "Every rect `emit-bunnies!` draws, as [x y side r g b]."
  [state dims]
  (let [out (atom [])]
    (sc/emit-bunnies! (fn [x y s r g b] (swap! out conj [x y s r g b])) state dims)
    @out))

(defn- reference-step
  "The original's `step` (bunnymark.clj lines 55-62), on a map, with the
  window the scene derives: W = 800 and H = `h` in the original's units."
  [h {:keys [x y vx vy]
      :as b}]
  (let [x (+ x vx)
        y (+ y vy)
        vx (if (or (< x 0) (> (+ x 32) 800)) (- vx) vx)
        vy (if (or (< y 0) (> (+ y 32) h)) (- vy) vy)]
    (assoc b :x x :y y :vx vx :vy vy)))

(defn- bunnies [state] (mapv #(sc/bunny state %) (range (:n state))))

(def below-bar [600 1500])
(def button-centre
  (let [[bx by bw bh] (:button (sc/dimensions metrics measure))]
    [(+ bx (* 0.5 bw)) (+ by (* 0.5 bh))]))

(deftest the-opening-bunnies-are-the-originals
  (let [s (fresh)
        b (sc/bunny s 0)]
    (testing "200 of them, as the original's opening spawn"
      (is (= 200 (:n s)))
      (is (= 200 sc/start-count)))
    (testing "the first, read by hand from the LCG's high bits in the original's order x y vx vy r g b"
      ;; The draws are 688 707 -85 119 144 114 91, with H = 1468.66 so y runs 0 to 1436.
      (is (= {:x 688.0
              :y 707.0
              :r 144
              :g 114
              :b 91} (select-keys b [:x :y :r :g :b])))
      (is (near? (/ -85 60.0) (:vx b)))
      (is (near? (/ 119 60.0) (:vy b))))
    (testing "every one lies in the ranges the original draws from"
      (is (every? (fn [{:keys [x y vx vy r g b]}]
                    (and (<= 0 x 768) (<= 0 y 1436) (<= (/ -250 60.0) vx (/ 250 60.0)) (<= (/ -250 60.0) vy (/ 250 60.0))
                         (<= 90 r 255) (<= 90 g 255) (<= 90 b 255)))
                  (bunnies s))))))

(deftest holding-spawns-at-the-originals-rate
  (let [s0 (fresh)
        [px py] below-bar
        s1 (tick s0 :press [px py])
        ;; The arrays are updated in place, so read them before the next tick.
        b1 (sc/bunny s1 200)
        s2 (tick s1 :down [px py])
        s3 (tick s2 :down [px py])]
    (testing "60 a frame while a finger is down (the original's BATCH)"
      (is (= 60 sc/batch))
      (is (= 260 (:n s1)))
      (is (= 320 (:n s2)))
      (is (= 380 (:n s3))))
    (testing "the new ones start at the finger, in the original's units, then take their first step"
      (is (near? (+ (/ px u) (:vx b1)) (:x b1)))
      (is (near? (+ (/ (- py 120) u) (:vy b1)) (:y b1))))
    (testing "nothing spawns once the finger lifts, or with no finger"
      (is (= 380 (:n (tick s3 :release [px py]))))
      (is (= 200 (:n (tick s0)))))
    (testing "only the field takes a spawn: a hold right of Back and above the field adds nothing, nor does one off the bottom"
      (is (= 200 (:n (tick s0 :press [700 60]))))
      (is (= 200 (:n (tick s0 :press [700 119]))))
      (is (= 260 (:n (tick s0 :press [700 120]))))
      (is (= 200 (:n (tick s0 :press [700 2334])))))
    (testing "a finger that began on the clear button, or in Back, never spawns, wherever it slides"
      (let [[cx cy] (mapv int button-centre)]
        (is (= 200 (:n (-> s0 (tick :press [cx cy]) (tick :down below-bar)))))
        (is (= 200 (:n (-> s0 (tick :press [100 60]) (tick :down below-bar)))))))
    (testing "and one that began in the field stops spawning on the button, resuming off it"
      (let [[cx cy] (mapv int button-centre)
            a (tick s0 :press below-bar)
            b (tick a :down [cx cy])
            c (tick b :down below-bar)]
        (is (= 260 (:n a)))
        (is (= 260 (:n b)))
        (is (= 320 (:n c)))))
    (testing "the host owns Back, and the clear button is not a place to add"
      (is (= 200 (:n (tick s0 :press [100 60]))))
      (is (= 200 (:n (tick s0 :press (mapv int button-centre))))))))

(deftest bunnies-bounce-as-the-original
  (let [h (:field-h geo)]
    (testing "200 bunnies for 400 frames agree with the original's step, every field"
      (loop [s (fresh) ref (bunnies (fresh)) k 0]
        (if (= k 400)
          (do (is (= 200 (count ref)))
              (is (= 200 (:n s))))
          (let [s' (tick s)
                ref' (mapv #(reference-step h %) ref)]
            (when (or (= k 0) (= k 399))
              (is (every? true? (map (fn [a b]
                                       (every? (fn [kk] (near? (kk a) (kk b))) [:x :y :vx :vy]))
                                     (bunnies s') ref'))))
            (recur s' ref' (inc k))))))
    (testing "each wall flips the velocity and leaves the position where it landed"
      (let [s (fresh)
            run (fn [x y vx vy]
                  (sc/set-bunny! s 0 {:x x
                                      :y y
                                      :vx vx
                                      :vy vy
                                      :r 100
                                      :g 100
                                      :b 100})
                  (select-keys (sc/bunny (tick s) 0) [:x :y :vx :vy]))]
        (is (= {:x -2.0
                :y 50.0
                :vx 3.0
                :vy 1.0} (run 1.0 49.0 -3.0 1.0)))
        (is (= {:x 770.0
                :y 50.0
                :vx -5.0
                :vy 1.0} (run 765.0 49.0 5.0 1.0)))
        (is (= {:x 50.0
                :y -1.0
                :vx 1.0
                :vy 2.0} (run 49.0 1.0 1.0 -2.0)))
        (is (= {:x 50.0
                :y (+ (- h 31.0) 2.0)
                :vx 1.0
                :vy -2.0}
               (run 49.0 (- h 31.0) 1.0 2.0)))
        (is (= {:x 10.0
                :y 20.0
                :vx 3.0
                :vy 4.0} (run 7.0 16.0 3.0 4.0)))))))

(deftest the-count-caps-at-the-originals-max
  (is (= 40000 sc/max-bunnies))
  (is (<= 40040 sc/capacity))
  (let [[px py] below-bar
        s (assoc (fresh) :n 39980)
        s1 (tick s :press [px py])
        s2 (tick s1 :down [px py])
        s3 (tick s2 :down [px py])]
    (testing "a batch is added while the count is below 40000, so it can end past it, as in the original"
      (is (= 40040 (:n s1)))
      (is (= 40040 (:n s2))))
    (testing "and at or above 40000 nothing more is added, so 40040 is where a hold from 200 ends"
      (is (= 40040 (:n s3)))
      (is (= 40040 (:n (tick s3 :down [px py]))))
      (is (zero? (mod (- 40040 sc/start-count) sc/batch))))
    (testing "a hold from 39940 stops exactly at 40000, as 39940 + 60 is not below it"
      (let [t (tick (assoc (fresh) :n 39940) :press [px py])]
        (is (= 40000 (:n t)))
        (is (= 40000 (:n (tick t :down [px py]))))))))

(deftest space-replacement-acts-as-the-original
  (let [s (tick (tick (fresh) :press below-bar) :release below-bar)
        [cx cy] (mapv int button-centre)
        cleared (tick (tick s :press [cx cy]) :release [cx cy])]
    (testing "a tap on the clear button empties the field, as SPACE does"
      (is (= 260 (:n s)))
      (is (= 0 (:n cleared))))
    (testing "a tap anywhere else does not: it adds a batch on its press frame and nothing clears"
      (is (= 320 (:n (tick (tick s :press [300 1800]) :release [300 1800])))))
    (testing "it counts from there: a hold afterwards adds a batch to nothing"
      (is (= 60 (:n (tick cleared :press below-bar)))))
    (testing "a drag that starts elsewhere and lifts on the button does not clear, as the tap is where the finger STARTED"
      (is (= 320 (:n (-> s (tick :press [300 1800]) (tick :down [cx cy]) (tick :release [cx cy]))))))))

(deftest first-frame-draws
  (let [s (fresh)
        dims (sc/dimensions metrics measure)
        rs (rects s dims)
        side (int (* u 32))
        [w h] (:screen metrics)]
    (testing "one rect for each of the 200 bunnies, of the sprite's size scaled to the field"
      (is (= 200 (count rs)))
      (is (= 48 side))
      (is (every? #(= side (nth % 2)) rs)))
    (testing "the first is where the hand-read LCG put it, at its own colour"
      (is (= [(int (* u 688)) (int (+ 120 (* u 707))) side 144 114 91] (first rs))))
    (testing "all inside the safe region, below Back"
      (is (every? (fn [[x y sd]] (and (<= 0 x) (<= (+ x sd) w) (<= 120 y) (<= (+ y sd) h))) rs)))
    (testing "tinted in the original's 90 to 255, and not all one colour"
      (is (every? (fn [[_ _ _ r g b]] (and (<= 90 r 255) (<= 90 g 255) (<= 90 b 255))) rs))
      (is (> (count (distinct (map #(subvec % 3) rs))) 100)))
    (testing "the readouts"
      (is (= "200 bunnies" (sc/count-line 200)))
      (is (= "60 fps" (sc/fps-line 60))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [m {:screen screen}
                [w h] screen
                dims (sc/dimensions m measure)
                [_ back-y _ back-h] gesture/back-region
                [bx by bw bh] (:button dims)
                lines (:lines dims)]]
    (testing (str screen)
      (is (= 3 (count lines)))
      (is (every? (fn [{:keys [s x y size]}]
                    (and (>= x 0) (<= (+ x (measure s size)) w)
                         (>= y (+ back-y back-h)) (<= (+ y size) h)))
                  lines))
      (testing "the count and fps lines leave the button clear, at the widest they can get"
        (is (every? (fn [{:keys [x]}] (< x bx)) lines))
        (is (every? (fn [{:keys [s x size]}] (<= (+ x (measure s size)) bx)) (take 2 lines))))
      (testing "the button is inside the safe region, below Back, and holds its label"
        (is (and (>= bx 0) (<= (+ bx bw) w) (>= by (+ back-y back-h)) (<= (+ by bh) h)))
        (let [{:keys [label label-x label-y label-size]} dims]
          (is (= "clear" label))
          (is (and (>= label-x bx) (<= (+ label-x (measure label label-size)) (+ bx bw))))
          (is (and (>= label-y by) (<= (+ label-y label-size) (+ by bh))))))
      (testing "the count line is the widest it ever gets"
        (is (<= (measure (sc/count-line 40060) (:size (first lines))) (- bx (:x (first lines)))))))))

(deftest turning-the-phone-keeps-every-bunny-in-the-field
  (let [land {:screen [2334 1206]}
        {:keys [field-h]} (sc/geometry land)
        s0 (loop [s (fresh) k 0] (if (< k 200) (recur (tick s) (inc k)) s))
        step-in (fn [s m] (first ((:update (sc/scene)) s {:metrics m
                                                          :pointer {:phase :idle
                                                                    :position nil}})))
        below-before (count (filter #(> (:y %) (- field-h 32)) (bunnies s0)))
        s1 (step-in s0 land)]
    (testing "the portrait field is taller, so some bunnies sit below the landscape one before the turn"
      (is (pos? below-before)))
    (testing "the first frame on the new screen puts every bunny inside it"
      (is (= 200 (:n s1)))
      (is (every? (fn [{:keys [y]}] (<= -64 y (+ field-h 32))) (bunnies s1))))
    (testing "and they stay there for a long run"
      (let [s2 (loop [s s1 k 0] (if (< k 2000) (recur (step-in s land) (inc k)) s))]
        (is (every? (fn [{:keys [y]}] (<= -64 y (+ field-h 32))) (bunnies s2)))))
    (testing "the gesture is reset, since its start was in the old screen's pixels"
      (let [held (tick (fresh) :press below-bar)
            turned (step-in held land)]
        (is (not= (:gesture held) gesture/idle))
        (is (nil? (:start (:gesture turned))))))))
