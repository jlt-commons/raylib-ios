(ns raylib.scenes.split3d-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.split3d :as sc]
            [raylib.soft3d :as s3]))

(def portrait {:screen [1206 2334]})
(def landscape {:screen [2334 1206]})
(def squarish {:screen [1200 1100]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (sc/dimensions portrait (fn [s size] (* 0.6 size (count s)))))
(def start (first ((:init (sc/scene)) {:metrics portrait})))
(def slop (gesture/slop portrait))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))
(defn- centre [[x y w h]] [(+ x (* 0.5 w)) (+ y (* 0.5 h))])
(defn- plus [[x y] dx dy] [(+ x dx) (+ y dy)])

(defn- step
  ([state phase points] (step state phase points nil portrait))
  ([state phase points ids] (step state phase points ids portrait))
  ([state phase points ids metrics]
   (sc/advance state (cond-> {:metrics metrics
                              :delta-seconds (/ 1.0 60.0)
                              :pointer {:phase phase
                                        :position (first points)}
                              :touch-points (vec points)}
                       ids (assoc :touches {:ids (vec ids)})))))

(def c1 (centre (first (:halves d))))
(def c2 (centre (second (:halves d))))
;; The original (camera_3d_split_screen.clj): 10 * GetFrameTime() a frame, both start at -3.
(def stride (/ 10.0 60.0))

(defn- drag
  "A stick pressed at `from` and then dragged by `dx`, `dy`: one update past the dead zone."
  [state from dx dy]
  (-> state (step :press [from]) (step :down [(plus from dx dy)])))

(deftest the-worlds-are-the-originals
  (is (= [-3.0 -3.0] [(:z1 start) (:x2 start)]))
  (is (= 10.0 sc/speed))
  (testing "121 trees, 4 apart, -20 to 20"
    (is (= 121 (count sc/trees)))
    (is (= [-20.0 -20.0] (first sc/trees)))
    (is (= [20.0 20.0] (last sc/trees))))
  (testing "player one looks down +z from (0, 1, z1), player two down +x from (x2, 3, 0), fovy 45"
    (is (= {:position [0.0 1.0 -3.0]
            :target [0.0 1.0 0.0]
            :up [0.0 1.0 0.0]
            :fovy 45.0
            :projection :perspective}
           (sc/player-camera 0 -3.0)))
    (is (= {:position [-3.0 3.0 0.0]
            :target [0.0 3.0 0.0]
            :up [0.0 1.0 0.0]
            :fovy 45.0
            :projection :perspective}
           (sc/player-camera 1 -3.0)))))

(deftest each-half-steers-its-own-player
  (testing "a drag up in half one is W: player one goes forward 10 * dt along z, player two stays"
    (let [a (drag start c1 0 -300)]
      (is (near? (+ -3.0 stride) (:z1 a)))
      (is (= -3.0 (:x2 a)))))
  (testing "a drag down in half one is S"
    (is (near? (- -3.0 stride) (:z1 (drag start c1 0 300)))))
  (testing "a drag up in half two is UP: player two goes +x, player one stays"
    (let [b (drag start c2 0 -300)]
      (is (near? (+ -3.0 stride) (:x2 b)))
      (is (= -3.0 (:z1 b)))))
  (testing "a drag down in half two is DOWN"
    (is (near? (- -3.0 stride) (:x2 (drag start c2 0 300)))))
  (testing "the same speed whatever the distance, and sideways moves nothing"
    (is (near? (:z1 (drag start c1 0 (- (* 2 slop)))) (:z1 (drag start c1 0 -300))))
    (is (= [-3.0 -3.0] ((juxt :z1 :x2) (drag start c1 300 0)))))
  (testing "inside the dead zone nothing moves"
    (is (= -3.0 (:z1 (drag start c1 0 (- (* 0.5 slop))))))))

(deftest both-thumbs-at-once
  (let [a (step start :press [c1 c2] [7 9])
        b (step a :down [(plus c1 0 -300) (plus c2 0 300)] [7 9])]
    (testing "two fresh fingers start a stick each"
      (is (every? some? (:sticks a))))
    (testing "each moves its own player, one forward and one back"
      (is (near? (+ -3.0 stride) (:z1 b)))
      (is (near? (- -3.0 stride) (:x2 b))))
    (testing "the finger order in :touch-points does not matter when ids are given"
      (let [b' (step a :down [(plus c2 0 300) (plus c1 0 -300)] [9 7])]
        (is (near? (:z1 b) (:z1 b')))
        (is (near? (:x2 b) (:x2 b')))))
    (testing "lifting one thumb stops only its player"
      (let [c (step b :down [(plus c2 0 300)] [9])]
        (is (= (:z1 b) (:z1 c)))
        (is (near? (- -3.0 (* 2 stride)) (:x2 c)))))))

(deftest a-resting-finger-never-steers
  (testing "a finger down before the scene had a press never starts a stick"
    (let [a (-> start (step :down [c1] [5]) (step :down [(plus c1 0 -300)] [5]))]
      (is (= -3.0 (:z1 a)))))
  (testing "a finger resting in half one is not adopted when the stick's own thumb lifts"
    (let [a (step start :press [c1] [1])
          a (step a :down [c1 (plus c1 100 100)] [1 2])
          a (step a :down [(plus c1 0 -300) (plus c1 100 100)] [1 2])
          z (:z1 a)
          lifted (step a :down [(plus c1 100 -400)] [2])]
      (is (some? (first (:sticks a))))
      (is (nil? (first (:sticks lifted))))
      (is (= z (:z1 lifted)))
      (is (= z (:z1 (step lifted :down [(plus c1 100 -500)] [2]))))))
  (testing "a stick follows its own id, never the other finger, even one that is nearer"
    (let [a (step start :press [c1] [1])
          a (step a :down [c1 (plus c1 0 -400)] [1 2])
          b (step a :down [c1 (plus c1 0 -405)] [1 2])]
      (is (= (:z1 a) (:z1 b)))))
  (testing "a finger that slides across the divider ends its stick and the other half's does not adopt it"
    (let [a (step start :press [c1] [1])
          a (step a :down [c2] [1])]
      (is (nil? (first (:sticks a))))
      (is (nil? (second (:sticks a))))
      (is (= -3.0 (:x2 (step a :down [(plus c2 0 -300)] [1]))))))
  (testing "a touch under Back never starts a stick"
    (let [a (step start :press [[100.0 50.0]] [1])]
      (is (= [nil nil] (:sticks a)))))
  (testing "a tap moves nothing, and the release position is never read"
    (let [a (-> start (step :press [c1]) (step :release [(plus c1 0 -900)]))]
      (is (= -3.0 (:z1 a)))
      (is (nil? (first (:sticks a))))))
  (testing "a rotation drops both sticks"
    (let [a (-> start (step :press [c1 c2] [1 2]) (step :down [c1 c2] [1 2]))
          r (step a :down [c1 c2] [1 2] landscape)]
      (is (= [nil nil] (:sticks r)))
      (is (= [-3.0 -3.0] [(:z1 r) (:x2 r)])))))

(deftest portrait-stacks-landscape-sides
  (doseq [[w h] screens
          :let [m {:screen [w h]}
                g (sc/geometry m)
                [fx fy fw fh] (:viewport g)
                [[ax ay aw ah] [bx by bw bh]] (:halves g)
                [dx dy dw dh] (:divider g)]]
    (testing (str w "x" h)
      (is (= (< fw fh) (:portrait? g)))
      (if (:portrait? g)
        (do (is (near? ay fy))
            (is (near? ax bx))
            (is (near? aw fw))
            (is (near? (+ ay ah) dy))
            (is (near? dh 4.0))
            (is (near? (+ dy dh) by))
            (is (near? (+ by bh) (+ fy fh))))
        (do (is (near? ax fx))
            (is (near? ay by))
            (is (near? ah fh))
            (is (near? (+ ax aw) dx))
            (is (near? (+ dx dw) bx))
            (is (near? (+ bx bw) (+ fx fw)))))
      (testing "the halves are equal and the field is below Back"
        (is (near? aw bw))
        (is (near? ah bh))
        (is (>= fy (+ (nth gesture/back-region 1) (nth gesture/back-region 3))))))))

(deftest first-frame-draws
  (doseq [m [portrait landscape squarish]
          :let [dims (sc/dimensions m measure)]
          i [0 1]
          :let [cam (sc/camera start dims i)
                [hx hy hw hh] (nth (:halves dims) i)
                dl (sc/scene-list start dims i)
                tris (filterv #(= :tri (nth % 0)) dl)
                vp (s3/view-proj cam (nth (:halves dims) i))
                inside? (fn [[x y]] (and (<= (- hx 1e-6) x (+ hx hw 1e-6)) (<= (- hy 1e-6) y (+ hy hh 1e-6))))]]
    (testing (str (:screen m) " half " i)
      (testing "the camera is the original's, fitted: eye, target, up, and a fovy that is 45 or wider"
        (let [orig (sc/player-camera i -3.0)]
          (is (= (:position orig) (:position cam)))
          (is (= (:target orig) (:target cam)))
          (is (= (:up orig) (:up cam)))
          (is (<= 45.0 (:fovy cam)))
          (is (= (:fovy (s3/fit-camera orig (/ 400.0 450.0) (/ hw hh))) (:fovy cam)))))
      (testing "only triangles, all finite, some on the glass"
        (is (= (count dl) (count tris)))
        (is (every? (fn [[_ & more]] (every? #(and (number? %) (< (abs (double %)) 1e6)) (take 6 more))) tris))
        (is (some (fn [[_ & more]] (some inside? (partition 2 (take 6 more)))) tris)))
      (testing "the plane is first and in the original's beige; the cubes follow in their colours"
        (is (= sc/plane-colour (subvec (first tris) 7 11)))
        (is (= 2 (count (take-while #(= sc/plane-colour (subvec % 7 11)) tris)))))
      (testing "the plane's near edge is below the bottom of the glass, so cutting it loses nothing"
        (let [pl (take 2 tris)
              ys (mapcat (fn [[_ _ y0 _ y1 _ y2]] [y0 y1 y2]) pl)]
          (is (>= (apply max ys) (+ hy hh)))))
      (testing "the tree count: the cube list has the visible trees' posts and leaves and 2 players"
        (let [order (sc/cube-order start dims i)
              vis (sc/visible-trees i cam (/ hw hh))]
          (is (= (+ 2 (* 2 (count vis))) (count order)))
          (is (< (count vis) 121))
          (is (pos? (count vis)))))
      (testing "both players' cubes are in the list, and the other player's is seen"
        (let [order (sc/cube-order start dims i)
              colours (set (map #(nth % 2) order))]
          (is (contains? colours (nth sc/player-colours 0)))
          (is (contains? colours (nth sc/player-colours 1))))
        (is (some #(= (nth sc/player-colours (- 1 i)) (subvec % 7 11)) tris)))
      (is (some? vp)))))

(deftest a-narrow-half-widens-the-fovy
  ;; 1200x1100 splits side by side into halves of about 598x908, narrower than
  ;; the original's 400x450 (0.889): hfov = 2 atan(tan(22.5) * 400/450), then
  ;; fovy' = 2 atan(tan(hfov/2) / aspect). Worked here, not by fit-camera.
  (let [dims (sc/dimensions squarish measure)
        [_ _ hw hh] (first (:halves dims))
        aspect (/ hw hh)
        want (Math/toDegrees (* 2.0 (Math/atan (/ (* (/ 400.0 450.0) (Math/tan (Math/toRadians 22.5))) aspect))))]
    (is (< aspect (/ 400.0 450.0)))
    (is (> want 45.0))
    (doseq [i [0 1]]
      (is (< (abs (- want (:fovy (sc/camera start dims i)))) 1e-9)))))

(deftest paint-order-is-far-to-near
  (doseq [i [0 1]
          st [start (assoc start :z1 7.0 :x2 -11.0)]
          :let [dims (sc/dimensions portrait measure)
                cam (sc/camera st dims i)
                [ex ey ez] (:position cam)
                order (sc/cube-order st dims i)
                dist (mapv (fn [[[x y z]]] (+ (* (- x ex) (- x ex)) (* (- y ey) (- y ey)) (* (- z ez) (- z ez)))) order)]]
    (testing (str "half " i)
      (is (> (count order) 10))
      (is (apply >= dist))
      (testing "the scene list is the plane, then each cube's own triangles in that order"
        (let [vp (s3/view-proj cam (nth (:halves dims) i))
              expected (reduce (fn [dl [pos size colour]] (s3/cube dl vp nil pos size colour {:shade :flat}))
                               (vec (take 2 (sc/scene-list st dims i)))
                               order)]
          (is (= expected (sc/scene-list st dims i))))))))

(deftest same-triangles-as-finish
  (doseq [i [0 1]
          st [start (assoc start :z1 7.0 :x2 -11.0) (assoc start :z1 -19.0 :x2 15.0)]
          :let [dims (sc/dimensions landscape measure)
                dl (sc/scene-list st dims i)
                finished (s3/finish dl)]]
    (testing (str "half " i)
      (is (pos? (count dl)))
      (is (= (count dl) (count finished)))
      (is (= (frequencies dl) (frequencies finished))))))

(deftest text-lines-fit-the-safe-region
  (doseq [[w h] screens
          :let [m {:screen [w h]}
                dims (sc/dimensions m measure)]
          {:keys [s x y size rect]} (:lines dims)
          :let [[rx ry rw rh] rect]]
    (testing (str w "x" h " " s)
      (is (>= size 8))
      (is (>= x rx))
      (is (<= (+ x (measure s size)) (+ rx (* 0.92 rw) 1e-6)))
      (is (>= y ry))
      (is (<= (+ y size) (+ ry rh)))
      (is (>= y (+ (nth gesture/back-region 1) (nth gesture/back-region 3))))
      (is (<= (+ rx rw) w))
      (is (<= (+ ry rh) h)))))
