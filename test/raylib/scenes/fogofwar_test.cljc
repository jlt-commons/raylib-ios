(ns raylib.scenes.fogofwar-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.fogofwar :as fog]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def start (first ((:init (fog/scene)) {:metrics m})))
(def slop (gesture/slop m))
(def stick-pt [600.0 1200.0])
(def other-pt [300.0 1700.0])
(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(defn- step
  "One frame. `points` are the touch points (the first is the pointer)."
  ([state phase points] (step state m phase points))
  ([state metrics phase points]
   (fog/advance state {:metrics metrics
                       :delta-seconds (/ 1.0 60.0)
                       :pointer {:phase phase
                                 :position (first points)}
                       :touch-points (vec points)})))

(defn- step-ids
  [state phase points ids]
  (fog/advance state {:metrics m
                      :delta-seconds (/ 1.0 60.0)
                      :pointer {:phase phase
                                :position (first points)}
                      :touch-points (vec points)
                      :touches {:ids (vec ids)}}))

(defn- at [[x y] dx dy] [(+ x dx) (+ y dy)])
(defn- pos [s] [(:px s) (:py s)])

(defn- pushed
  "`state` with a stick pressed at `stick-pt` and dragged by `[dx dy]`, one
  frame after the press."
  [state dx dy]
  (-> state
      (step :press [stick-pt])
      (step :down [(at stick-pt dx dy)])))

(defn- lit-tiles
  "The `[x y]` of every tile at 1 (lit) in `fog`."
  [fog]
  (set (for [y (range fog/tiles-y)
             x (range fog/tiles-x)
             :when (= 1 (nth fog (+ x (* y fog/tiles-x))))]
         [x y])))

(defn- tile-state [fog x y] (nth fog (+ x (* y fog/tiles-x))))

;; --- the original's numbers ----------------------------------------------------

(deftest the-originals-constants
  (is (= [25 15 32 16 2 5] [fog/tiles-x fog/tiles-y fog/tile fog/player fog/visibility fog/speed])))

(deftest tile-of-is-the-originals
  (testing "(int (/ (+ x 16) 32)): a tile changes half a tile in"
    (is (= [0 0] (fog/tile-of 0.0 0.0)))
    (is (= [0 0] (fog/tile-of 15.9 15.9)))
    (is (= [1 1] (fog/tile-of 16.0 16.0)))
    (is (= [12 7] (fog/tile-of 392.0 232.0)))
    (is (= [25 15] (fog/tile-of 784.0 464.0))
        "the formula gives 25 and 15 at the far corner, off the map, and light-around clips")))

;; --- the fog -------------------------------------------------------------------

;; The original's light-around runs x and y over (range (- t 2) (+ t 2)), which is
;; t-2 .. t+1: four tiles a side, one more behind than ahead.
(deftest visibility-reveals-within-the-originals-radius
  (testing "the first frame stands at the patrol's frame 0, (392, 232), tile [12 7]"
    (is (= [392.0 232.0] (pos start)))
    (is (= (set (for [x (range 10 14)
                      y (range 5 9)]
                  [x y]))
           (lit-tiles (:fog start)))
        "x 10..13 and y 5..8, sixteen tiles")
    (is (= 16 (count (lit-tiles (:fog start))))))
  (testing "every other tile is still unexplored"
    (is (= (- (* 25 15) 16) (count (filter zero? (:fog start))))))
  (testing "light-around clips at the map edge"
    (is (= #{[0 0] [1 0] [0 1] [1 1]}
           (lit-tiles (fog/light-around (vec (repeat 375 0)) 0 0))))
    (is (= #{[23 13] [24 13] [23 14] [24 14] [22 13] [22 14] [23 12] [24 12] [22 12]}
           (lit-tiles (fog/light-around (vec (repeat 375 0)) 24 14)))
        "x 22..25 and y 12..15 cut to the 25 by 15 map")))

(deftest explored-tiles-stay-dimmed
  (testing "age-fog drops every lit tile to remembered and leaves the rest"
    (is (= [0 2 2 0 2] (fog/age-fog [0 1 2 0 1]))))
  (testing "walking away leaves the tiles behind at 2, and only the new ones lit"
    (let [steered (pushed start 100.0 0.0)
          moved (nth (iterate #(step % :down [(at stick-pt 100.0 0.0)]) steered) 60)
          fg (:fog moved)]
      (is (> (:px moved) 600.0) "it walked a good way right")
      (is (= 2 (tile-state fg 10 5)) "a tile lit at the start is remembered, not dark again")
      (is (= 2 (tile-state fg 10 8)))
      (is (zero? (tile-state fg 0 0)) "never visited stays dark")
      (is (= 16 (count (lit-tiles fg))) "and exactly the 4 by 4 around the player is lit")
      (is (= 1 (tile-state fg (first (fog/tile-of (:px moved) (:py moved)))
                           (second (fog/tile-of (:px moved) (:py moved))))))
      (is (= 0 (count (filter #{3} fg))) "no other state is ever stored")))
  (testing "alphas: unexplored 255 (black), remembered 204, lit 0"
    (is (= [255 204 0] (mapv fog/tile-alpha [0 2 1])))))

(deftest corner-alpha-is-the-average-of-four-tiles
  (let [fg (-> (vec (repeat 375 0))
               (assoc 0 1 1 2))]
    (testing "an interior corner is the mean of the four tiles meeting there"
      (is (= 255 (fog/corner-alpha (vec (repeat 375 0)) 5 5)))
      (is (= 0 (fog/corner-alpha (vec (repeat 375 1)) 5 5)))
      (let [f (-> (vec (repeat 375 0))
                  (assoc (+ 4 (* 4 25)) 1) ; tile [4 4], top-left of corner [5 5]
                  (assoc (+ 5 (* 4 25)) 2) ; tile [5 4]
                  (assoc (+ 4 (* 5 25)) 0) ; tile [4 5]
                  (assoc (+ 5 (* 5 25)) 2))] ; tile [5 5]
        (is (= 166 (fog/corner-alpha f 5 5)) "(0 + 204 + 255 + 204) / 4 = 165.75")))
    (testing "the map's edge repeats its own tiles, as CLAMP wrap does for the texture"
      (is (= 0 (fog/corner-alpha fg 0 0)) "the corner is tile [0 0] alone, and it is lit")
      (is (= 102 (fog/corner-alpha fg 1 0))
          "the top edge between [0 0] lit and [1 0] remembered: (0 + 204) / 2")
      (is (= 255 (fog/corner-alpha fg 25 15)) "the far corner is tile [24 14] alone")
      (is (= 255 (fog/corner-alpha fg 25 7)) "the right edge is [24 6] and [24 7] twice each"))
    (testing "26 by 16 corners, each 0, 204 or the mean of those"
      (let [alphas (fog/corner-alphas (:fog start))]
        (is (= (* 26 16) (count alphas)))
        (is (every? #(<= 0 % 255) alphas))
        (is (= 9 (count (filter zero? alphas))) "the 3 by 3 corners inside the lit 4 by 4")))
    (testing "tile-corners reads the tile's four corners, clockwise from the top-left"
      (let [alphas (fog/corner-alphas (:fog start))
            idx (fn [cx cy] (nth alphas (+ cx (* cy 26))))]
        (is (= [(idx 3 2) (idx 4 2) (idx 4 3) (idx 3 3)] (fog/tile-corners alphas 3 2)))))))

;; --- the stick -----------------------------------------------------------------

(deftest the-stick-moves-the-player
  (testing "until a stick is dragged the player patrols, the original's loop"
    (let [s (step start :idle [])]
      (is (false? (:steered? s)))
      (is (= (fog/patrol 1) (pos s)))))
  (testing "a drag right adds the original's SPEED 5 to x, from where the patrol left it"
    (let [before (step start :press [stick-pt])
          [x y] (pos before)
          after (step before :down [(at stick-pt 100.0 0.0)])]
      (is (true? (:steered? after)))
      (is (near? (+ x 5.0) (:px after)))
      (is (near? y (:py after)))))
  (testing "left, up and down are the other three keys"
    (let [[x y] (pos (step start :press [stick-pt]))]
      (is (near? (- x 5.0) (:px (pushed start -100.0 0.0))))
      (is (near? (- y 5.0) (:py (pushed start 0.0 -100.0))))
      (is (near? (+ y 5.0) (:py (pushed start 0.0 100.0))))))
  (testing "a diagonal moves at 5 too, not 5 on both axes"
    (let [[x y] (pos (step start :press [stick-pt]))
          s (pushed start 100.0 100.0)
          dx (- (:px s) x)
          dy (- (:py s) y)]
      (is (near? (/ 5.0 (Math/sqrt 2.0)) dx))
      (is (near? dx dy))
      (is (near? 5.0 (Math/sqrt (+ (* dx dx) (* dy dy)))))))
  (testing "once steered the patrol never takes over again"
    (let [held (pushed start 100.0 0.0)
          lifted (step held :release [])
          later (nth (iterate #(step % :idle []) lifted) 50)]
      (is (= (pos held) (pos lifted)))
      (is (= (pos held) (pos later)))))
  (testing "the map holds the player: x and y stay in 0 .. 784 and 0 .. 464"
    (let [far (fn [dx dy]
                (nth (iterate #(step % :down [(at stick-pt dx dy)]) (pushed start dx dy)) 400))]
      (is (= [784.0 (:py (far 100.0 0.0))] [(:px (far 100.0 0.0)) (:py (far 100.0 0.0))]))
      (is (= 0.0 (:px (far -100.0 0.0))))
      (is (= 0.0 (:py (far 0.0 -100.0))))
      (is (= 464.0 (:py (far 0.0 100.0))))))
  (testing "dead zone: inside the slop an axis stays put"
    (let [s (pushed start (* 0.5 slop) (* 0.5 slop))]
      (is (false? (:steered? s)))
      (is (= (fog/patrol 2) (pos s)) "still the patrol's second frame")))
  (testing "a press under Back starts no stick"
    (let [s (-> start (step :press [[100.0 60.0]]) (step :down [[300.0 60.0]]))]
      (is (false? (:steered? s)))))
  (testing "a rotation drops the stick"
    (let [held (pushed start 100.0 0.0)
          turned (step held {:screen [2334 1206]} :down [[900.0 400.0]])]
      (is (some? (:stick held)))
      (is (nil? (:stick turned)))
      (is (= (pos held) (pos turned)) "the held finger moves nothing"))))

(deftest a-resting-finger-never-steers
  (let [resting (step start :down [stick-pt])]
    (testing "a finger already down when the scene opened is no stick, however far it moves"
      (is (nil? (:stick resting)))
      (let [dragged (step resting :down [(at stick-pt 200.0 0.0)])]
        (is (false? (:steered? dragged)))
        (is (= (fog/patrol 1) (pos resting)))
        (is (= (fog/patrol 2) (pos dragged)) "the patrol carries on")))
    (testing "when a second finger lands the resting one is not adopted, in either order"
      (doseq [pts [[stick-pt other-pt] [other-pt stick-pt]]]
        (let [s (step resting :press pts)]
          (is (= other-pt (get-in s [:stick :centre])) "the stick is at the new finger")
          (is (false? (:steered? s))))))
    (testing "the resting finger moving on moves nothing, the new one steers"
      (let [s (-> resting
                  (step :press [stick-pt other-pt])
                  (step :down [(at stick-pt 200.0 0.0) other-pt]))]
        (is (false? (:steered? s)) "the old finger moved right, the stick's finger stayed")
        (let [s2 (step s :down [(at stick-pt 200.0 0.0) (at other-pt -100.0 0.0)])]
          (is (true? (:steered? s2)))
          (is (near? (- (:px s) 5.0) (:px s2)) "left, by the new finger"))))
    (testing "by touch id: the stick follows its own finger and the resting one steers nothing"
      (let [s (-> start
                  (step-ids :down [stick-pt] [7])
                  (step-ids :press [stick-pt other-pt] [7 9])
                  (step-ids :down [(at stick-pt 200.0 0.0) other-pt] [7 9]))]
        (is (= 9 (get-in s [:stick :id])))
        (is (false? (:steered? s)))))
    (testing "a tap moves nothing"
      (let [tapped (-> start (step :press [stick-pt]) (step :release [stick-pt]))]
        (is (false? (:steered? tapped)))
        (is (nil? (:stick tapped)))))))

;; --- the first frame -----------------------------------------------------------

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                s (first ((:init (fog/scene)) {:metrics metrics}))
                [w h] screen
                dims (fog/dimensions metrics measure)
                {:keys [ox oy scale map-w map-h]} dims
                alphas (fog/corner-alphas (:fog s))]]
    (testing (str screen)
      (is (= 375 (count (:fog s))))
      (is (= 375 (count (:tiles s))))
      (is (every? #{0 1} (:tiles s)) "two tile shades, as GetRandomValue 0 1")
      (is (and (some zero? (:tiles s)) (some #{1} (:tiles s))))
      (is (= 16 (count (lit-tiles (:fog s)))))
      (is (= (* 26 16) (count alphas)))
      (is (= 9 (count (filter zero? alphas))))
      (is (= 255 (first alphas)) "the unexplored corner is black")
      (is (every? #(<= 0 % 255) alphas))
      (testing "the map is the original's 800 by 480 scaled, inside the screen and below Back"
        (is (near? (* 800 scale) map-w))
        (is (near? (* 480 scale) map-h))
        (is (pos? scale))
        (is (<= 0 ox))
        (is (<= (+ ox map-w) (+ w 1e-6)))
        (is (>= oy back-bottom))
        (is (<= (+ oy map-h) (+ h 1e-6))))
      (testing "the player stands on the map"
        (let [px (+ ox (* scale (:px s)))
              py (+ oy (* scale (:py s)))]
          (is (and (>= px ox) (<= (+ px (* scale 16)) (+ ox map-w 1e-6))))
          (is (and (>= py oy) (<= (+ py (* scale 16)) (+ oy map-h 1e-6)))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [lines ox map-w]} (fog/dimensions {:screen screen} measure)]]
    (testing (str screen)
      (is (= 3 (count lines)) "the tile line at its widest and both hints")
      (doseq [{:keys [s x y size]} lines]
        (testing s
          (is (<= 0 x))
          (is (>= x ox))
          (is (<= (+ x (measure s size)) (+ ox map-w)) "inside the map")
          (is (<= (+ x (measure s size)) w))
          (is (>= y back-bottom))
          (is (<= (+ y size) h))
          (is (>= size 8)))))))
