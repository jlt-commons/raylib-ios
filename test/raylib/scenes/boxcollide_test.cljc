(ns raylib.scenes.boxcollide-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.boxcollide :as sc]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def start (first ((:init (sc/scene)) {:metrics m})))
(def slop (gesture/slop m))
(def stick-pt [600.0 1200.0])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(defn- step
  "One frame. `points` are the touch points (the first is the pointer)."
  [state phase points]
  (sc/advance state {:metrics m
                     :delta-seconds (/ 1.0 60.0)
                     :pointer {:phase phase
                               :position (first points)}
                     :touch-points (vec points)}))

(defn- step-ids
  "`step` with the host's touch ids alongside the points."
  [state phase points ids]
  (sc/advance state {:metrics m
                     :delta-seconds (/ 1.0 60.0)
                     :pointer {:phase phase
                               :position (first points)}
                     :touch-points (vec points)
                     :touches {:ids (vec ids)}}))

(defn- at [[x y] dx dy] [(+ x dx) (+ y dy)])

(defn- pushed
  "`state` with a stick pressed at `stick-pt` and dragged by `[dx dy]`, one
  frame after the press."
  [state dx dy]
  (-> state
      (step :press [stick-pt])
      (step :down [(at stick-pt dx dy)])))

(defn- pos [s] [(:px s) (:pz s)])

;; --- the stick -----------------------------------------------------------------

;; The original adds SPEED 0.18 to px for D and subtracts it for A, and adds it
;; to pz for S and subtracts it for W, each key on its own. Here the stick's two
;; axes are normalised, so a diagonal also moves 0.18 in all.
(deftest the-stick-moves-the-player-at-the-originals-speed
  (testing "the player starts at the origin, as the original's (0, 0)"
    (is (= [0.0 0.0] (pos start))))
  (testing "up the glass is W: z - 0.18 (forward)"
    (is (= [0.0 -0.18] (pos (pushed start 0.0 -100.0)))))
  (testing "down is S: z + 0.18"
    (is (= [0.0 0.18] (pos (pushed start 0.0 100.0)))))
  (testing "left is A: x - 0.18"
    (is (= [-0.18 0.0] (pos (pushed start -100.0 0.0)))))
  (testing "right is D: x + 0.18"
    (is (= [0.18 0.0] (pos (pushed start 100.0 0.0)))))
  (testing "a diagonal moves at 0.18 too, not 0.18 on both axes (nudge, splitscreen and freecam)"
    (let [[x z] (pos (pushed start 100.0 -100.0))
          each (/ 0.18 (Math/sqrt 2.0))]
      (is (near? each x))
      (is (near? (- each) z))
      (is (near? 0.18 (Math/sqrt (+ (* x x) (* z z)))))))
  (testing "the press itself moves nothing"
    (is (= [0.0 0.0] (pos (step start :press [stick-pt])))))
  (testing "inside the dead zone an axis stays put"
    (is (= [0.0 0.0] (pos (pushed start (* 0.5 slop) (* 0.5 slop)))))
    (is (= [0.0 -0.18] (pos (pushed start (* 0.5 slop) (* -2.0 slop))))))
  (testing "each held frame adds 0.18, however far the finger is dragged"
    (let [held (step (pushed start 100.0 0.0) :down [(at stick-pt 250.0 0.0)])]
      (is (near? 0.36 (:px held)))))
  (testing "a finger that began before the scene opened is no stick"
    (is (= [0.0 0.0] (pos (step start :down [(at stick-pt 100.0 0.0)])))))
  (testing "a press under Back starts no stick"
    (is (= [0.0 0.0] (pos (-> start (step :press [[100.0 60.0]]) (step :down [[300.0 60.0]]))))))
  (testing "a press above the 3D area starts no stick"
    (let [[_ fy _ _] (:viewport (sc/geometry m))]
      (is (= [0.0 0.0] (pos (-> start
                                (step :press [[600.0 (- fy 5.0)]])
                                (step :down [[800.0 (- fy 5.0)]])))))))
  (testing "a release is not read"
    (let [held (pushed start 100.0 0.0)
          lifted (step held :release [[0.0 0.0]])]
      (is (nil? (:stick lifted)))
      (is (= (pos held) (pos lifted)))))
  (testing "a finger sliding on stops being the stick when it lifts, and the player stays"
    (let [held (pushed start 100.0 0.0)]
      (is (= (pos held) (pos (step held :idle [])))))))

(deftest a-tap-does-not-move-it
  (let [tapped (-> start
                   (step :press [stick-pt])
                   (step :release [stick-pt]))]
    (is (= [0.0 0.0] (pos tapped)))
    (is (nil? (:stick tapped))))
  (testing "a wobble inside the slop is a tap too"
    (let [tapped (-> start
                     (step :press [stick-pt])
                     (step :down [(at stick-pt (* 0.9 slop) (* -0.9 slop))])
                     (step :release [(at stick-pt (* 0.9 slop) 0.0)]))]
      (is (= [0.0 0.0] (pos tapped))))))

(deftest a-rotation-drops-the-stick
  (let [held (pushed start 100.0 0.0)
        turned (sc/advance held {:metrics {:screen [2334 1206]}
                                 :delta-seconds (/ 1.0 60.0)
                                 :pointer {:phase :down
                                           :position [900.0 400.0]}
                                 :touch-points [[900.0 400.0]]})]
    (is (some? (:stick held)))
    (is (nil? (:stick turned)))
    (is (= (pos held) (pos turned)) "the held finger moves nothing")))

;; --- the collision tests, worked from the original's numbers ----------------------

;; The original's boxes {x z s}: (4 0 2) (-4 3 2.6) (0 -5 3) (1.6 0 2) (-6 -3 2.2),
;; the player's size 2. A box overlaps when |px - x| < 1 + s/2 and
;; |pz - z| < 1 + s/2, strictly, on x and z only. So the reach of each box is
;; 2, 2.3, 2.5, 2 and 2.1.
(deftest the-aabb-test-matches-the-original
  (testing "the five boxes are the original's, in its order"
    (is (= [[4.0 0.0 2.0] [-4.0 3.0 2.6] [0.0 -5.0 3.0] [1.6 0.0 2.0] [-6.0 -3.0 2.2]]
           (mapv (juxt :x :z :s) sc/boxes)))
    (is (= 2.0 sc/player-size)))
  (testing "the spawn touches only the fourth box: |0 - 1.6| = 1.6 < 2, |0 - 0| < 2"
    (is (= #{3} (sc/hits 0.0 0.0))))
  (testing "box 3 (0, -5, 3) reaches 2.5: 2.4 is in"
    (is (= #{2} (sc/hits 0.0 -2.6))))
  (testing "and exactly 2.5 is out, the test is strict"
    (is (= #{} (sc/hits 0.0 -2.5))))
  (testing "the x axis is strict too: box 0 (4, 0) reaches 2, so |2 - 4| = 2 is out"
    (is (not (contains? (sc/hits 2.0 0.0) 0)))
    (is (contains? (sc/hits 2.001 0.0) 0) "and a hair inside is in")
    (is (not (contains? (sc/hits 6.0 0.0) 0)) "from the far side as well")
    (is (contains? (sc/hits 5.999 0.0) 0)))
  (testing "overlapping on x alone is no hit: box 5 at (-6, -3) reaches 2.1"
    (is (= #{4} (sc/hits -4.0 -3.0)) "|-4 + 6| = 2 < 2.1 and z the same")
    (is (= #{} (sc/hits -4.0 -0.5)) "x overlaps but |-0.5 + 3| = 2.5 is not under 2.1"))
  (testing "overlapping on z alone is no hit: box 1 at (4, 0) reaches 2"
    (is (= #{} (sc/hits 6.5 0.0)) "|6.5 - 4| = 2.5 is out on x while z overlaps"))
  (testing "two boxes at once: (2.6, 0) is inside box 1 (1.4 < 2) and box 4 (1.0 < 2)"
    (is (= #{0 3} (sc/hits 2.6 0.0))))
  (testing "box 2 at (-4, 3) reaches 2.3: (-4, 5.2) is in, (-4, 5.4) is out"
    (is (= #{1} (sc/hits -4.0 5.2)))
    (is (= #{} (sc/hits -4.0 5.4))))
  (testing "far away hits nothing"
    (is (= #{} (sc/hits 30.0 30.0)))))

(deftest overlap-turns-the-player-red-as-the-original
  (testing "the original colours a box red when it is hit and gray otherwise, the player lime"
    (is (= [230 41 55 255] sc/hit-colour))
    (is (= [130 130 130 255] sc/box-colour))
    (is (= [0 228 48 255] sc/player-colour)))
  (testing "the caption says COLLISION! while any box is hit, and the original's hint otherwise"
    (is (= "COLLISION!" (sc/caption-text start)) "the spawn is already touching a box")
    (is (= "COLLISION!" (sc/caption-text (assoc start :px 0.0 :pz -2.6))))
    (is (= sc/hint (sc/caption-text (assoc start :px 30.0 :pz 30.0))))
    (is (= sc/hit-caption-colour (sc/caption-colour start)))
    (is (= sc/hint-colour (sc/caption-colour (assoc start :px 30.0 :pz 30.0)))))
  (testing "walking away from the spawn clears the hit, and the stick drives it there"
    (let [moved (nth (iterate #(step % :down [(at stick-pt -100.0 0.0)])
                              (pushed start -100.0 0.0))
                     20)]
      (is (near? -3.78 (:px moved)) "21 frames of -0.18")
      (is (= #{} (sc/hits (:px moved) (:pz moved))) "(-3.78, 0) is clear of every box")
      (is (= sc/hint (sc/caption-text moved)))))
  (testing "the scene list draws a hit box in the hit colour and a clear one in gray"
    (let [dims (sc/dimensions m measure)
          dl (sc/scene-list (sc/grid-list (sc/camera dims) dims) start dims)
          cols (set (map #(subvec % 7 11) (filter #(= :tri (nth % 0)) dl)))
          shades (fn [[r g b a]] [(int (* 1.0 r)) (int (* 1.0 g)) (int (* 1.0 b)) a])]
      (is (contains? cols (shades sc/hit-colour)) "the fourth box's top face at full shade")
      (is (contains? cols (shades sc/box-colour)) "the clear boxes' top faces")
      (is (contains? cols (shades sc/player-colour)) "the player's top face"))))

;; --- whose finger is the stick ----------------------------------------------------

(def other-pt [300.0 1700.0])

(deftest a-stick-starts-only-on-a-fresh-press
  (let [resting (step start :down [stick-pt])]
    (testing "a finger already down is not adopted when a second one lands, in either order"
      (doseq [pts [[stick-pt other-pt] [other-pt stick-pt]]]
        (let [s (step resting :press pts)]
          (is (= other-pt (get-in s [:stick :centre])) "the stick is at the new finger")
          (is (= [0.0 0.0] (pos s))))))
    (testing "the resting finger moving on moves nothing, the new one steers"
      (let [s (-> resting
                  (step :press [stick-pt other-pt])
                  (step :down [(at stick-pt 200.0 0.0) other-pt]))]
        (is (= [0.0 0.0] (pos s)) "the old finger moved right, no motion")))
    (testing "the new finger steers"
      (let [s (-> resting
                  (step :press [stick-pt other-pt])
                  (step :down [stick-pt (at other-pt 100.0 0.0)]))]
        (is (= [0.18 0.0] (pos s)))))
    (testing "a second finger landing under Back starts no stick and adopts nobody"
      (let [s (-> resting
                  (step :press [stick-pt [100.0 60.0]])
                  (step :down [(at stick-pt 200.0 0.0) [100.0 60.0]]))]
        (is (nil? (:stick s)))
        (is (= [0.0 0.0] (pos s)))))))

(deftest the-stick-belongs-to-its-own-finger
  (let [a (pushed start 100.0 0.0)]
    (testing "a second finger landing leaves the stick on its first finger"
      (let [s (step a :press [(at stick-pt 100.0 0.0) other-pt])]
        (is (= stick-pt (get-in s [:stick :centre])))
        (is (= (at stick-pt 100.0 0.0) (get-in s [:stick :at])))
        (is (near? 0.36 (:px s)))))
    (testing "when its finger lifts while another is down the stick ends and nothing moves"
      (let [both (step a :press [(at stick-pt 100.0 0.0) other-pt])
            s (step both :down [other-pt])]
        (is (nil? (:stick s)))
        (is (= (pos both) (pos s)))
        (is (= (pos both) (pos (nth (iterate #(step % :down [other-pt]) s) 5))))))
    (testing "the other finger lifting leaves the stick going"
      (let [both (step a :press [(at stick-pt 100.0 0.0) other-pt])
            s (step both :down [(at stick-pt 100.0 0.0)])]
        (is (some? (:stick s)))
        (is (near? 0.54 (:px s)))))))

;; --- the first frame ----------------------------------------------------------------

(deftest first-frame-draws
  (testing "the state after init alone has everything a draw reads"
    (is (= [0.0 0.0] (pos start)))
    (is (= [1206 2334] (:screen start))))
  (doseq [screen screens
          :let [metrics {:screen screen}
                s (first ((:init (sc/scene)) {:metrics metrics}))
                dims (sc/dimensions metrics measure)
                cam (sc/camera dims)
                grid (sc/grid-list cam dims)
                dl (sc/scene-list grid s dims)
                tris (filterv #(= :tri (nth % 0)) dl)
                lines (filterv #(= :line (nth % 0)) dl)
                [fx fy fw fh] (:viewport dims)]]
    (testing (str screen)
      (testing "the camera is the original's: (11, 12, 11) at (0, 0.5, 0), fovy 45 or wider"
        (is (= [11.0 12.0 11.0] (:position cam)))
        (is (= [0.0 0.5 0.0] (:target cam)))
        (is (>= (:fovy cam) 45.0)))
      (testing "the grid of 20 is 21 lines each way"
        (is (= 42 (count lines))))
      (testing "six boxes show three faces each from the front right, two triangles a face"
        (is (= 36 (count tris))))
      (testing "everything is inside the 3D area"
        (doseq [t tris
                [x y] [[(nth t 1) (nth t 2)] [(nth t 3) (nth t 4)] [(nth t 5) (nth t 6)]]]
          (is (<= fx x (+ fx fw)))
          (is (<= fy y (+ fy fh)))))
      (testing "the player is the lime box nearest the camera, drawn after the farther ones"
        (is (some (fn [t] (= [0 228 48 255] (subvec t 7 11))) tris)))
      (testing "the spawn's hit box is red"
        (is (some (fn [t] (= [230 41 55 255] (subvec t 7 11))) tris))))))

;; --- text ----------------------------------------------------------------------------

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [_ fy _ _] (:viewport dims)]]
    (testing (str screen)
      (is (= 2 (count (:lines dims))) "the hint and the collision caption")
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= size 8) s)
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) fy) "above the 3D area")
        (is (<= (+ y size) h) s))
      (is (= #{"COLLISION!" sc/hint} (set (map :s (:lines dims)))))
      (is (= (:caption dims) (dissoc (first (:lines dims)) :s)) "one place and size for both"))))

(deftest a-hard-reversal-keeps-the-stick
  ;; The reviewer's probe: held right 100 px, then 200 px back in one frame.
  (doseq [[label stp ids] [["with ids" step-ids [4]]
                           ["without ids" (fn [s ph pts _] (step s ph pts)) nil]]]
    (testing label
      (let [right (-> start (stp :press [stick-pt] ids) (stp :down [(at stick-pt 100.0 0.0)] ids))
            left (stp right :down [(at stick-pt -100.0 0.0)] ids)
            more (stp left :down [(at stick-pt -100.0 0.0)] ids)]
        (is (some? (:stick left)) "the thumb is still down")
        (is (< (:px left) (:px right)))
        (is (< (:px more) (:px left)) "and it keeps steering")))))
