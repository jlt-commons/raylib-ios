(ns raylib.scenes.spheres-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.spheres :as sc]
            [raylib.soft3d :as s3]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(def metrics {:screen [1206 2334]})
(def start (first ((:init (sc/scene)) {:metrics metrics})))

(defn- tick
  ([state] (tick state :idle nil))
  ([state phase position]
   (first ((:update (sc/scene)) state {:metrics metrics
                                       :pointer {:phase phase
                                                 :position position}}))))

(defn- tap
  "A tap at `at`: a press, a release."
  [state at]
  (-> state (tick :press at) (tick :release at)))

(defn- tris [dl] (filterv (fn [it] (= :tri (nth it 0))) dl))
(defn- lines [dl] (filterv (fn [it] (= :line (nth it 0))) dl))

(deftest the-balls-are-the-originals
  (testing "six, in the original's palette, drawn from the LCG seeded 20261002"
    (is (= 6 (count (:balls start))))
    (is (= sc/palette (mapv :colour (:balls start))))
    (is (= [[230 41 55 255] [255 161 0 255] [0 228 48 255] [102 191 255 255] [135 60 190 255] [255 203 0 255]]
           sc/palette)))
  (testing "the first two, read by hand from the LCG's high bits in spawn's order x y z vx vz r"
    ;; The draws are -22 15 -25 -10 -9 5, then -5 14 5 -1 -7 4.
    (is (= {:x -2.2
            :y 1.5
            :z -2.5
            :vx -0.1
            :vy 0.0
            :vz -0.09
            :r 0.5
            :colour [230 41 55 255]}
           (first (:balls start))))
    (is (= {:x -0.5
            :y 1.4
            :z 0.5
            :vx -0.01
            :vy 0.0
            :vz -0.07
            :r 0.4
            :colour [255 161 0 255]}
           (second (:balls start)))))
  (testing "every ball starts inside the ranges the original draws from"
    (is (every? (fn [{:keys [x y z vx vz r vy]}]
                  (and (<= -3.0 x 3.0) (<= 1.0 y 4.0) (<= -3.0 z 3.0) (<= -0.1 vx 0.1)
                       (<= -0.1 vz 0.1) (<= 0.3 r 0.6) (zero? vy)))
                (:balls start))))
  (testing "the box and the constants are the original's"
    (is (= 4.0 sc/bound))
    (is (= 0.01 sc/gravity))
    (is (= 0.9 sc/restitution))))

(deftest balls-fall-and-bounce-like-the-original
  (testing "one frame: gravity into vy, then each axis moves by its velocity"
    (let [b (first (:balls (tick start)))]
      (is (near? -2.3 (:x b)))
      (is (near? 1.49 (:y b)))
      (is (near? -2.59 (:z b)))
      (is (near? -0.01 (:vy b)))
      (is (near? -0.1 (:vx b)))))
  (testing "the floor: past -4 + r the ball is put on it and its vy is reversed and scaled by 0.9"
    (let [b (sc/step {:x 0.0
                      :y -3.4
                      :z 0.0
                      :vx 0.0
                      :vy -0.2
                      :vz 0.0
                      :r 0.5})]
      (is (near? -3.5 (:y b)))
      (is (near? 0.189 (:vy b)) "vy was -0.2 - 0.01 = -0.21, so -(-0.21) * 0.9")))
  (testing "a wall on the far side: p + r above 4 is put at 4 - r, velocity reversed and scaled"
    (let [b (sc/step {:x 3.4
                      :y 0.0
                      :z 0.0
                      :vx 0.2
                      :vy 0.0
                      :vz 0.0
                      :r 0.5})]
      (is (near? 3.5 (:x b)))
      (is (near? -0.18 (:vx b)))))
  (testing "a ball inside the box is not touched but by its velocity"
    (is (= [0.2 0.3] (sc/reflect 0.2 0.3 0.5)) "away from a wall reflect returns p and v as given (line 33's :else)")
    (is (= [0.9 0.1] (sc/reflect 0.9 0.1 0.5)))))

(deftest balls-stay-in-the-box-and-lose-energy
  (let [after (nth (iterate tick start) 1500)]
    (is (every? (fn [{:keys [x y z r]}]
                  (and (<= (+ -4.0 r -1e-9) x (- 4.0 r -1e-9))
                       (<= (+ -4.0 r -1e-9) y (- 4.0 r -1e-9))
                       (<= (+ -4.0 r -1e-9) z (- 4.0 r -1e-9))))
                (:balls after))
        "all six, after 25 s of frames")
    (is (every? (fn [{:keys [y vy r]}] (< (+ (abs vy) (- y (+ -4.0 r))) 1.0)) (:balls after))
        "and they have settled towards the floor")))

(deftest a-tap-outside-back-respawns
  (let [moved (nth (iterate tick start) 30)
        spawned-again (first (sc/spawn (:seed start)))]
    (testing "a tap in the field replaces all six with the next six the LCG gives, not the first six"
      (let [t (tap moved [600.0 1200.0])]
        (is (= spawned-again (:balls t)))
        (is (not= (:balls start) (:balls t)))
        (is (every? zero? (map :vy (:balls t))))))
    (testing "and the frame it happens on does not step them (the original's if)"
      (is (= spawned-again (:balls (tap moved [600.0 1200.0])))))
    (testing "a tap in Back's region leaves the balls to the host"
      (let [t (tap moved [100.0 60.0])]
        (is (= (:balls (tick (tick moved))) (:balls t)) "two frames of physics, no respawn")))
    (testing "a drag is not a tap"
      (let [t (-> moved (tick :press [600.0 1200.0]) (tick :down [900.0 1200.0]) (tick :release [900.0 1200.0]))]
        (is (not= spawned-again (:balls t)))))
    (testing "without a touch the balls step"
      (is (= (mapv sc/step (:balls moved)) (:balls (tick moved)))))))

(deftest first-frame-draws
  (testing "the camera is the original's, (10, 8, 10) at the origin, fovy 45, in an 800x450 field"
    (let [cam (sc/camera (sc/dimensions {:screen [800 450]} measure))]
      (is (= [10.0 8.0 10.0] (:position cam)))
      (is (= [0.0 0.0 0.0] (:target cam)))
      (is (= [0.0 1.0 0.0] (:up cam)))
      (is (near? 45.0 (:fovy cam)))
      (is (= :perspective (:projection cam)))))
  (testing "a field narrower than the original's widens the fovy instead"
    (is (< 45.0 (:fovy (sc/camera (sc/dimensions {:screen [1206 2334]} measure))))))
  (doseq [screen screens
          :let [dims (sc/dimensions {:screen screen} measure)
                dl (sc/scene-list start dims)
                faces (tris dl)
                [vx vy vw vh] (:viewport dims)]]
    (testing (str screen)
      (is (= 22 (count (lines dl))) "DrawGrid(10, 1) is 11 lines each way")
      (is (every? #(= :line (nth % 0)) (take 22 dl)) "the grid goes first, under the balls")
      (is (every? #(= :tri (nth % 0)) (drop 22 dl)))
      (is (< 100 (count faces) (* 6 2 sc/rings sc/slices)) "front-facing triangles of six balls")
      (is (every? (fn [[_ & more]]
                    (every? (fn [[x y]] (and (<= (- vx 1e-6) x (+ vx vw 1e-6)) (<= (- vy 1e-6) y (+ vy vh 1e-6))))
                            (partition 2 (take 6 more))))
                  faces)
          "everything projected stays inside the field")))
  (testing "every ball is drawn: each palette colour shows in some triangle, shaded by its band"
    (let [dims (sc/dimensions {:screen [1206 2334]} measure)
          faces (tris (sc/scene-list start dims))
          shades (fn [[cr cg cb]]
                   (set (for [i (range sc/rings)
                              :let [y0 (Math/sin (- (* Math/PI (/ (double i) sc/rings)) (/ Math/PI 2.0)))
                                    y1 (Math/sin (- (* Math/PI (/ (double (inc i)) sc/rings)) (/ Math/PI 2.0)))
                                    f (+ 0.45 (* 0.55 (/ (+ y0 y1 2.0) 4.0)))]]
                          [(int (* f cr)) (int (* f cg)) (int (* f cb)) 255])))
          seen (set (map (fn [it] (subvec it 7 11)) faces))]
      (doseq [c sc/palette]
        (is (some seen (shades c)) (str c)))))
  (testing "balls that touch no other are painted far to near by their centres' distance from the eye"
    (let [dims (sc/dimensions {:screen [1206 2334]} measure)
          eye (:position (sc/camera dims))
          groups (sc/paint-groups (:balls start) eye)
          d2 (fn [{:keys [x y z]}] (let [[ex ey ez] eye] (+ (* (- x ex) (- x ex)) (* (- y ey) (- y ey)) (* (- z ez) (- z ez)))))]
      (is (= 6 (count (apply concat groups))))
      (is (every? #(= 1 (count %)) groups) "the first six are disjoint")
      (is (apply >= (map #(d2 (first %)) groups)))))
  (testing "triangles wind the way rlgl keeps"
    (let [dims (sc/dimensions {:screen [1206 2334]} measure)]
      (is (every? (fn [[_ x1 y1 x2 y2 x3 y3]]
                    (neg? (- (* (- x2 x1) (- y3 y1)) (* (- y2 y1) (- x3 x1)))))
                  (tris (sc/scene-list start dims)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [_ fy _ fh] (:viewport dims)]]
    (testing (str screen)
      (is (= 1 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) fy) "the caption sits above the field"))
      (is (>= fy (+ back-y back-h)) "the field is below Back")
      (is (near? h (+ fy fh)) "and runs to the bottom"))))

(deftest tessellation-and-frames-are-pinned
  (is (= [6 8] [sc/rings sc/slices]) "the lowered tessellation the budget and the disclosure rest on")
  (is (= [10 14] [sc/original-rings sc/original-slices]))
  (let [dims (sc/dimensions metrics measure)]
    (is (= 232 (count (tris (sc/scene-list start dims)))) "measured at the start on 1206x2334"))
  (is (= 0 (:frame start)))
  (is (= 7 (:frame (nth (iterate tick start) 7)))))

(deftest reflect-is-strict-at-the-walls
  (testing "touching a wall exactly is not a bounce (the original's < and >)"
    (is (= [-3.5 -0.2] (sc/reflect -3.5 -0.2 0.5)))
    (is (= [3.5 0.2] (sc/reflect 3.5 0.2 0.5))))
  (testing "a hair past it is"
    (is (= [-3.5 0.18] (mapv #(/ (Math/round (* 1e6 %)) 1e6) (sc/reflect -3.5000001 -0.2 0.5))))))

(deftest every-tap-deals-new-balls
  (let [s1 (first (sc/spawn (:seed start)))
        seed1 (second (sc/spawn (:seed start)))
        [s2 seed2] (sc/spawn seed1)
        s3b (first (sc/spawn seed2))
        t1 (tap start [600.0 1200.0])
        t2 (tap t1 [600.0 1200.0])
        t3 (tap t2 [600.0 1200.0])]
    (is (= s1 (:balls t1)))
    (is (= s2 (:balls t2)) "the second tap goes on from where the first stopped")
    (is (= s3b (:balls t3)) "and the third")
    (is (= 4 (count (distinct [(:balls start) (:balls t1) (:balls t2) (:balls t3)]))) "start is not dealt again")
    (is (apply distinct? [(:balls t1) (:balls t2) (:balls t3)]))))

(def ^:private ball {:vx 0.0
                     :vy 0.0
                     :vz 0.0})

(deftest balls-that-intersect-are-sorted-together
  (let [dims (sc/dimensions {:screen [1206 2334]} measure)
        cam (sc/camera dims)
        eye (:position cam)
        vp (s3/view-proj cam (:viewport dims))
        a (assoc ball :x 0.0 :y 0.0 :z 0.0 :r 0.6 :colour [230 41 55 255])
        b (assoc ball :x 0.5 :y 0.2 :z 0.5 :r 0.4 :colour [0 228 48 255])
        c (assoc ball :x 3.0 :y 0.0 :z -3.0 :r 0.4 :colour [0 121 241 255])
        far (assoc ball :x -3.0 :y 0.0 :z -3.0 :r 0.4 :colour [255 203 0 255])
        whole (fn [balls]
                (reduce (fn [dl {:keys [x y z r colour]}]
                          (s3/sphere dl vp nil [x y z] r colour {:rings sc/rings
                                                                 :slices sc/slices}))
                        [] balls))
        body (fn [balls] (vec (drop 22 (sc/scene-list {:balls balls} dims))))
        depths (fn [dl] (mapv #(nth % 11) dl))]
    (testing "groups: touching balls, transitively, and no others"
      (is (= [2] (mapv count (sc/paint-groups [a b] eye))))
      (is (= [1 1] (mapv count (sc/paint-groups [a c] eye))))
      (let [m (assoc ball :x 0.9 :y 0.0 :z 0.0 :r 0.4 :colour [0 0 0 255])
            e (assoc ball :x -0.9 :y 0.0 :z 0.0 :r 0.4 :colour [0 0 0 255])]
        (is (= [3] (mapv count (sc/paint-groups [e a m] eye))) "e and m each touch a, not each other")))
    (testing "the pair is a case where the nearer centre's whole ball paints over a farther triangle"
      (is (not (apply >= (depths (whole [a b]))))
          "ball by ball, b (nearer) after a, some of a's triangles are nearer than some of b's"))
    (testing "an intersecting pair comes out sorted by triangle depth, with the same triangles"
      (let [dl (body [a b])]
        (is (apply >= (depths dl)))
        (is (= (frequencies (whole [a b])) (frequencies dl)))))
    (testing "disjoint balls stay whole, far to near"
      (let [dl (body [c far])
            d2 (fn [{:keys [x y z]}] (let [[ex ey ez] eye] (+ (* (- x ex) (- x ex)) (* (- y ey) (- y ey)) (* (- z ez) (- z ez)))))
            order (sort-by (comp - d2) [c far])]
        (is (= (whole order) dl))))
    (testing "a group is placed among the other balls by its centroid, far to near"
      (let [dl (body [far a b c])
            ab (body [a b])]
        (is (= (count dl) (+ (count ab) (count (whole [far])) (count (whole [c])))))
        (is (= ab (vec (take-last (count ab) dl)))
            "from the eye (10, 8, 10): far (402), then c (282), then the group (about 252)")
        (is (= (whole [far]) (vec (take (count (whole [far])) dl))))))))
