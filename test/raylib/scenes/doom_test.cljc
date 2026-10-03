(ns raylib.scenes.doom-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.doom :as sc]
            [raylib.scenes.doom.hud :as hud]
            [raylib.scenes.doom.map :as m]
            [raylib.scenes.doom.ray :as ray]))

(def phone {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (sc/geometry phone))
(def start (first ((:init (sc/scene)) {:metrics phone})))
(def slop (gesture/slop phone))
(def cols ray/cols)

;; Phone geometry: Back ends at 120, pad 18, text 36, so the field starts at 192
;; and is 2142 tall. The picture is the original's 900 x 560 at 1206 / 900, so
;; 1206 x 750 at the top of the field. A stick begins in the left half from y =
;; 192 + 1071 = 1263, a turn anywhere in the right half, and FIRE is the circle
;; of radius 120.6 at (1067.4, 2195.4).
(def stick-pt [300.0 1900.0])
(def look-pt [900.0 600.0])
(def fire-pt [1067.0 2195.0])
(def imp-colour* [230 80 60 255])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near?
  ([a b] (near? a b 1e-9))
  ([a b eps] (< (abs (double (- a b))) eps)))
(defn- vnear? [a b] (and (= (count a) (count b)) (every? true? (map near? a b))))
(defn- at-px [[x y] dx dy] [(+ x dx) (+ y dy)])

(defn- step
  ([state phase points] (step state phase points nil))
  ([state phase points ids]
   (sc/advance state (cond-> {:metrics phone
                              :delta-seconds (/ 1.0 60.0)
                              :pointer {:phase phase
                                        :position (first points)}
                              :touch-points (vec points)}
                       ids (assoc :touches {:ids (vec ids)})))))

(defn- pos [state] [(:pos-x state) (:pos-y state)])
(defn- heading [state] (Math/atan2 (:dir-y state) (:dir-x state)))
(defn- facing
  "`start` turned to look along `angle`, as `rotate` turns the heading."
  [angle]
  (sc/rotate start angle))
(defn- placed [x y angle]
  (assoc (facing angle) :pos-x x :pos-y y))

;; --- the original (doom.clj), re-typed here as the oracle ----------------------------

(def o-level
  ["1111111111111111"
   "1..............1"
   "1..2222...44...1"
   "1..2......4....1"
   "1..2..33..4....1"
   "1.....3........1"
   "1.....3...222..1"
   "1..............1"
   "1...44444......1"
   "1.......4...33.1"
   "1.......4......1"
   "1..333..4..22..1"
   "1....3.....2...1"
   "1....3.........1"
   "1..............1"
   "1111111111111111"])

(defn- o-wall-at [x y]
  (if (or (< x 0) (< y 0) (>= x 16) (>= y 16))
    1
    (let [c (nth (nth o-level y) x)]
      (if (= \. c) 0 (- (int c) (int \0))))))

(defn- o-cast-column
  "The original's lines 181-219, with COLS a parameter."
  [i ncols {:keys [pos-x pos-y dir-x dir-y plane-x plane-y]}]
  (let [camera (- (/ (* 2.0 i) ncols) 1.0)
        rdx (+ dir-x (* plane-x camera))
        rdy (+ dir-y (* plane-y camera))
        ddx (if (zero? rdx) 1e30 (Math/abs (/ 1.0 rdx)))
        ddy (if (zero? rdy) 1e30 (Math/abs (/ 1.0 rdy)))
        stepx (if (neg? rdx) -1 1)
        stepy (if (neg? rdy) -1 1)]
    (loop [mx (int pos-x)
           my (int pos-y)
           sdx (if (neg? rdx)
                 (* (- pos-x (int pos-x)) ddx)
                 (* (- (+ (int pos-x) 1.0) pos-x) ddx))
           sdy (if (neg? rdy)
                 (* (- pos-y (int pos-y)) ddy)
                 (* (- (+ (int pos-y) 1.0) pos-y) ddy))
           side 0
           n 0]
      (let [tile (o-wall-at mx my)]
        (if (or (pos? tile) (> n 64))
          {:dist (max 0.0001 (if (zero? side) (- sdx ddx) (- sdy ddy)))
           :tile (max 1 tile)
           :side side}
          (if (< sdx sdy)
            (recur (+ mx stepx) my (+ sdx ddx) sdy 0 (inc n))
            (recur mx (+ my stepy) sdx (+ sdy ddy) 1 (inc n))))))))

(defn- o-shade
  "The original's lines 221-226."
  [dist side]
  (let [f (/ 1.0 (+ 1.0 (* 0.11 dist dist)))
        f (if (zero? side) f (* f 0.72))]
    (max 30 (min 255 (long (* 255 (+ 0.12 (* 0.95 f))))))))

(defn- o-wall-color
  "The original's lines 461-467 as [r g b]."
  [t]
  (case t
    1 [150 70 55]
    2 [120 120 130]
    3 [90 110 150]
    [70 180 110]))

(defn- o-pack [[r g b] s]
  (bit-or (quot (* r s) 255)
          (bit-shift-left (quot (* g s) 255) 8)
          (bit-shift-left (quot (* b s) 255) 16)
          (bit-shift-left 255 24)))

(defn- o-visible-imps
  "The original's lines 273-286."
  [{:keys [pos-x pos-y dir-x dir-y plane-x plane-y imps]}]
  (let [inv-det (/ 1.0 (- (* plane-x dir-y) (* dir-x plane-y)))]
    (->> imps
         (filter :alive)
         (map (fn [{:keys [x y]}]
                (let [sx (- x pos-x)
                      sy (- y pos-y)]
                  {:tx (* inv-det (- (* dir-y sx) (* dir-x sy)))
                   :ty (* inv-det (+ (* (- plane-y) sx) (* plane-x sy)))})))
         (filter #(> (:ty %) 0.25))
         (sort-by :ty >))))

(defn- o-rotate [s a]
  (let [c (Math/cos a)
        sn (Math/sin a)
        {:keys [dir-x dir-y plane-x plane-y]} s]
    (assoc s
           :dir-x (- (* dir-x c) (* dir-y sn))
           :dir-y (+ (* dir-x sn) (* dir-y c))
           :plane-x (- (* plane-x c) (* plane-y sn))
           :plane-y (+ (* plane-x sn) (* plane-y c)))))

(defn- o-move
  "The original's lines 342-351."
  [s {:keys [forward strafe dt]}]
  (let [{:keys [pos-x pos-y dir-x dir-y plane-x plane-y]} s
        sp (* 3.4 dt)
        nx (+ pos-x (* sp (+ (* forward dir-x) (* strafe plane-x))))
        ny (+ pos-y (* sp (+ (* forward dir-y) (* strafe plane-y))))]
    (cond-> s
      (zero? (o-wall-at (int nx) (int pos-y))) (assoc :pos-x nx)
      (zero? (o-wall-at (int pos-x) (int ny))) (assoc :pos-y ny))))

;; --- the constants -----------------------------------------------------------------

(deftest the-originals-constants-are-pinned
  (testing "the sixteen rows, the grid and the six imps"
    (is (= o-level m/level))
    (is (= [16 16] [m/map-w m/map-h]))
    (is (= [[8.5 2.5] [12.5 6.5] [5.5 12.5] [11.5 11.5] [13.5 3.5] [4.5 8.5]] m/imp-spawns))
    (is (= (for [row o-level c row] (if (= \. c) 0 (- (int c) (int \0)))) m/cells)))
  (testing "the speeds, the cone, the flash and the camera"
    (is (= 3.4 m/move-speed))
    (is (= 0.9 m/imp-speed))
    (is (= 0.9 m/bite-range))
    (is (= 0.985 m/fire-cone))
    (is (= 0.06 m/flash-time))
    (is (= 0.66 m/plane-length))
    (is (= 0.0022 sc/mouse-sensitivity))
    (is (= 0.05 sc/dt-cap))
    (is (= [900 560] [sc/window-w sc/window-h]))
    (is (= 12 ray/strips)))
  (testing "the start"
    (is (= [2.5 7.5 1.0 0.0 0.0 0.66] ((juxt :pos-x :pos-y :dir-x :dir-y :plane-x :plane-y) start)))
    (is (= [100 0 0 0.0] ((juxt :health :kills :shots :flash) start)))
    (is (= 6 (count (:imps start))))
    (is (every? :alive (:imps start)))
    (is (= (mapv (fn [[x y]] [x y]) m/imp-spawns) (mapv (juxt :x :y) (:imps start)))))
  (testing "the shade is the original's, and wall styles are its minimap's colours"
    (doseq [dist [0.0001 0.5 1.0 2.5 7.0 12.5 22.0]
            side [0 1]]
      (is (= (o-shade dist side) (m/shade dist side)) (str dist " " side)))
    (is (= 255 (m/shade 0.0001 0)))
    (is (= 31 (m/shade 40.0 1)) "recomputed from the original's formula: 255 * (0.12 + 0.95 * 0.00407) is 31.6")
    (is (= 30 (m/shade 100.0 1)) "the floor")
    (is (= [[150 70 55] [120 120 130] [90 110 150] [70 180 110]]
           (mapv #(get m/wall-colours %) [1 2 3 4])))
    (is (= (o-pack [150 70 55] 43) (m/lit [150 70 55] 43)))))

;; --- the ray caster ------------------------------------------------------------------

(deftest dda-hits-the-originals-walls
  (testing "by hand, from the start (2.5, 7.5): the middle column looks straight along its heading"
    (let [mid (quot cols 2)]
      (testing "+x: the far border, 12.5 away, a vertical face of style 1"
        (let [{:keys [dist tile side]} (ray/cast-column mid start)]
          (is (near? 12.5 dist))
          (is (= [1 0] [tile side]))))
      (testing "+y: the bottom border 7.5 away, a horizontal face"
        (let [{:keys [dist tile side]} (ray/cast-column mid (placed 2.5 7.5 (/ Math/PI 2)))]
          (is (near? 7.5 dist 1e-6))
          (is (= [1 1] [tile side]))))
      (testing "-x: the left border at x = 1, 1.5 away"
        (let [{:keys [dist tile side]} (ray/cast-column mid (placed 2.5 7.5 Math/PI))]
          (is (near? 1.5 dist 1e-6))
          (is (= [1 0] [tile side]))))
      (testing "a wall inside the map: from (3.5, 7.5) facing -y, column 3 is open through row 5 and row 4 is a style 2, 2.5 away"
        (let [{:keys [dist tile side]} (ray/cast-column mid (placed 3.5 7.5 (- (/ Math/PI 2))))]
          (is (= 2 tile))
          (is (= 1 side))
          (is (near? 2.5 dist 1e-6))))))
  (testing "every column from many poses is the original's, distance, style and side"
    (let [xs [1.5 3.2 5.5 7.1 9.5 11.8 13.5 14.4]
          ys [1.5 3.4 6.5 8.5 10.5 12.3 14.5]
          angles (map #(* % (/ (* 2 Math/PI) 7)) (range 7))
          mismatches (atom [])]
      (doseq [x xs
              y ys
              :when (zero? (o-wall-at (int x) (int y)))
              a angles
              :let [s (placed x y a)]
              i (range cols)
              :let [mine (ray/cast-column i s)
                    theirs (o-cast-column i cols s)]]
        (when-not (and (near? (:dist theirs) (:dist mine))
                       (= (:tile theirs) (:tile mine))
                       (= (:side theirs) (:side mine)))
          (swap! mismatches conj [x y a i mine theirs])))
      (is (empty? (take 3 @mismatches)))))
  (testing "the border is all wall, which is what lets the caster skip the original's bounds test and 64 step limit"
    (is (every? pos? (concat (take 16 m/cells) (take-last 16 m/cells)
                             (map #(nth m/cells (* 16 %)) (range 16))
                             (map #(nth m/cells (+ 15 (* 16 %))) (range 16))))))
  (testing "a ray exactly along an axis (a zero component) terminates on the wall"
    (doseq [a [0.0 (/ Math/PI 2) Math/PI (- (/ Math/PI 2))]
            :let [s (placed 2.5 7.5 a)
                  mine (ray/cast-column (quot cols 2) s)
                  theirs (o-cast-column (quot cols 2) cols s)]]
      (is (near? (:dist theirs) (:dist mine) 1e-6) (str a))
      (is (< (:dist mine) 20.0)))))

;; --- movement --------------------------------------------------------------------------

(deftest movement-collides-as-the-original
  (let [dt (/ 1.0 60.0)]
    (testing "free ground: forward goes 3.4 * dt along the heading, strafe 0.66 of that along the plane"
      (let [fwd (sc/move start {:forward 1
                                :strafe 0
                                :dt dt})
            str* (sc/move start {:forward 0
                                 :strafe 1
                                 :dt dt})]
        (is (vnear? [(+ 2.5 (* 3.4 dt)) 7.5] (pos fwd)))
        (is (vnear? [2.5 (+ 7.5 (* 3.4 dt 0.66))] (pos str*)))))
    (testing "a wall stops the axis it is on: the border at x = 15"
      (let [s (placed 14.98 7.5 0.0)
            moved (sc/move s {:forward 1
                              :strafe 0
                              :dt dt})]
        (is (= 14.98 (:pos-x moved)))
        (is (= 7.5 (:pos-y moved)))))
    (testing "and the other axis slides along it"
      (let [s (placed 14.98 7.5 (/ Math/PI 4))
            moved (sc/move s {:forward 1
                              :strafe 0
                              :dt dt})]
        (is (= 14.98 (:pos-x moved)) "x is blocked")
        (is (near? (+ 7.5 (* 3.4 dt (Math/sin (/ Math/PI 4)))) (:pos-y moved)) "y slides")))
    (testing "the test is a point, not a body: the player may stand against the wall's face"
      (let [s (placed 14.999 7.5 (/ Math/PI 2))
            moved (sc/move s {:forward 1
                              :strafe 0
                              :dt dt})]
        (is (= 14.999 (:pos-x moved)))
        (is (> (:pos-y moved) 7.5))))
    (testing "walls inside the map block too: the 2s of row 2 at x = 3"
      (let [s (placed 3.5 3.5 (- (/ Math/PI 2)))
            moved (sc/move s {:forward 1
                              :strafe 0
                              :dt 0.3})]
        ;; 3.5 - 3.4 * 0.3 = 2.48 is in row 2, a wall at column 3
        (is (= 3.5 (:pos-y moved)))))
    (testing "any step from many poses is the original's"
      (let [bad (atom [])]
        (doseq [x [1.2 3.5 8.8 14.7]
                y [1.3 4.6 9.9 14.6]
                a [0.0 1.0 2.0 3.5 5.0]
                [f s] [[1 0] [-1 0] [0 1] [0 -1] [1 1] [-0.7 0.7]]
                :let [st (placed x y a)
                      mine (sc/move st {:forward f
                                        :strafe s
                                        :dt 0.04})
                      theirs (o-move st {:forward f
                                         :strafe s
                                         :dt 0.04})]]
          (when-not (vnear? (pos theirs) (pos mine)) (swap! bad conj [x y a f s])))
        (is (empty? (take 3 @bad)))))
    (testing "the step holds dt to 0.05, so a stall cannot teleport the player"
      (let [moved (sc/step start {:forward 1
                                  :strafe 0
                                  :dt 5.0})]
        (is (vnear? [(+ 2.5 (* 3.4 0.05)) 7.5] (pos moved)))))
    (testing "rotate is the original's"
      (let [s (sc/rotate start 0.7)
            t (o-rotate start 0.7)]
        (is (vnear? ((juxt :dir-x :dir-y :plane-x :plane-y) t) ((juxt :dir-x :dir-y :plane-x :plane-y) s)))))))

;; --- shooting and the imps ---------------------------------------------------------------

(defn- with-imps [s & xys]
  (assoc s :imps (mapv (fn [[x y alive]] {:x x
                                          :y y
                                          :alive (if (nil? alive) true alive)}) xys)))

(deftest fire-acts-as-the-original
  (testing "a shot counts, flashes for 0.06 and misses an empty room"
    (let [s (sc/shoot (with-imps start))]
      (is (= 1 (:shots s)))
      (is (= 0.06 (:flash s)))
      (is (= 0 (:kills s)))))
  (testing "an imp straight ahead dies and counts"
    (let [s (sc/shoot (with-imps start [6.5 7.5]))]
      (is (= [false] (mapv :alive (:imps s))))
      (is (= 1 (:kills s)))))
  (testing "only the nearest imp in the cone"
    (let [s (sc/shoot (with-imps start [9.5 7.5] [5.5 7.5] [12.5 7.5]))]
      (is (= [true false true] (mapv :alive (:imps s))))
      (is (= 1 (:kills s)))))
  (testing "the cone is dot > 0.985"
    (let [at (fn [c dist] (let [a (Math/acos c)] [(+ 2.5 (* dist (Math/cos a))) (+ 7.5 (* dist (Math/sin a)))]))]
      (is (= 1 (:kills (sc/shoot (with-imps start (at 0.99 4.0))))) "0.99 hits")
      (is (= 1 (:kills (sc/shoot (with-imps start (at 0.9851 4.0))))) "just inside")
      (is (= 0 (:kills (sc/shoot (with-imps start (at 0.984 4.0))))) "just outside")
      (is (= 0 (:kills (sc/shoot (with-imps start (at 0.98 4.0))))) "0.98 misses")))
  (testing "a dead imp is ignored and one behind the player is never hit"
    (is (= 0 (:kills (sc/shoot (with-imps start [6.5 7.5 false])))))
    (is (= 0 (:kills (sc/shoot (with-imps start [1.5 7.5]))))))
  (testing "a shot ignores walls, as the original's does"
    (let [s (sc/shoot (assoc (with-imps start [10.5 4.5]) :pos-x 2.5 :pos-y 7.5))]
      (is (= 0 (:kills s))))
    (let [s (sc/shoot (with-imps (placed 2.5 5.5 0.0) [7.5 5.5]))]
      (is (= 1 (:kills s)) "through the pillar column at x = 6")))
  (testing "the step runs the original's order: the shot's flash is already counting down"
    (let [s (sc/step start {:fire? true
                            :dt (/ 1.0 60.0)})]
      (is (= 1 (:shots s)))
      (is (near? (- 0.06 (/ 1.0 60.0)) (:flash s)))
      (let [later (reduce (fn [s _] (sc/step s {:dt (/ 1.0 60.0)})) s (range 10))]
        (is (= 0.0 (:flash later))))))
  (testing "a finger that lands on the FIRE button fires once, however long it stays"
    (let [one (step start :press [fire-pt])
          held (nth (iterate #(step % :down [fire-pt]) one) 8)
          off (step held :release [fire-pt])]
      (is (= 1 (:shots one)))
      (is (= 1 (:shots held)))
      (is (= 1 (:shots off)))
      (is (= 2 (:shots (step (step off :press [fire-pt]) :down [fire-pt]))) "a second landing is a second shot")))
  (testing "a tap on the button fires, a tap elsewhere does not"
    (is (= 1 (:shots (-> start (step :press [fire-pt]) (step :release [fire-pt])))))
    (is (= 0 (:shots (-> start (step :press [stick-pt]) (step :release [stick-pt])))))
    (is (= 0 (:shots (-> start (step :press [look-pt]) (step :release [look-pt]))))))
  (testing "it fires while the other thumb walks, and the fire finger never walks or turns"
    (let [walking (-> start (step :press [stick-pt] [4]) (step :down [(at-px stick-pt 0.0 -200.0)] [4]))
          fired (step walking :press [(at-px stick-pt 0.0 -200.0) fire-pt] [4 5])
          later (step fired :down [(at-px stick-pt 0.0 -200.0) (at-px fire-pt 0.0 -300.0)] [4 5])]
      (is (= 1 (:shots fired)))
      (is (some? (:stick fired)))
      (is (nil? (:look fired)))
      (is (= 1 (:shots later)))
      (is (= (heading fired) (heading later)) "the fire finger sliding off turned nothing")))
  (testing "a kill by a tap on the button"
    (let [s (-> (with-imps start [6.5 7.5]) (step :press [fire-pt]))]
      (is (= [1 1] [(:kills s) (:shots s)])))))

(deftest the-imps-walk-and-bite-as-the-original
  (let [dt (/ 1.0 60.0)]
    (testing "an imp walks straight at the player at 0.9 * dt"
      (let [s (sc/advance-imps (with-imps (placed 2.5 7.5 0.0) [6.5 7.5]) dt)
            {:keys [x y]} (first (:imps s))]
        (is (near? (- 6.5 (* 0.9 dt)) x))
        (is (near? 7.5 y))))
    (testing "within 0.9 it bites instead, a point a frame each, and does not move"
      (let [s (sc/advance-imps (with-imps (placed 2.5 7.5 0.0) [3.0 7.5] [2.5 8.0] [9.5 3.5]) dt)]
        (is (= 98 (:health s)))
        (is (= [3.0 7.5] ((juxt :x :y) (first (:imps s)))))
        (is (= [2.5 8.0] ((juxt :x :y) (second (:imps s)))))))
    (testing "an imp does not walk into a wall: row 2's style-2 block over (4, 3)"
      (let [s (sc/advance-imps (with-imps (placed 4.5 1.5 0.0) [4.5 3.5]) 1.0)]
        (is (= [4.5 3.5] ((juxt :x :y) (first (:imps s)))))))
    (testing "health stops at 0 and then the imps freeze"
      (let [s (assoc (with-imps (placed 2.5 7.5 0.0) [3.0 7.5] [3.1 7.5] [9.5 3.5]) :health 1)
            dead (sc/step s {:dt dt})
            later (sc/step dead {:dt dt})]
        (is (= 0 (:health dead)))
        (is (= (:imps dead) (:imps later)))))
    (testing "the fps is a frame count over the elapsed time, reported once more than 0.4 s has passed"
      (let [a (sc/track-fps start 0.3)
            b (sc/track-fps a 0.2)]
        (is (= 0.0 (:fps a)))
        (is (= 1 (:fps-frames a)))
        (is (near? (/ 2 0.5) (:fps b)))
        (is (= [0 0.0] ((juxt :fps-frames :fps-elapsed) b)))))))

;; --- fingers -----------------------------------------------------------------------------

(deftest the-stick-moves-and-the-drag-turns
  (let [dt (/ 1.0 60.0)
        sp (* 3.4 dt)]
    (testing "up the glass is forward: along the heading, and the press itself walks nowhere"
      (let [pressed (step start :press [stick-pt])
            one (step pressed :down [(at-px stick-pt 0.0 -200.0)])
            two (step one :down [(at-px stick-pt 0.0 -200.0)])]
        (is (= (pos start) (pos pressed)))
        (is (vnear? [(+ 2.5 sp) 7.5] (pos one)))
        (is (vnear? [(+ 2.5 (* 2 sp)) 7.5] (pos two)))))
    (testing "right is the strafe, along the plane at 0.66"
      (let [s (-> start (step :press [stick-pt]) (step :down [(at-px stick-pt 200.0 0.0)]))]
        (is (vnear? [2.5 (+ 7.5 (* sp 0.66))] (pos s)))))
    (testing "down is backwards, and a diagonal is a unit vector"
      (let [back (-> start (step :press [stick-pt]) (step :down [(at-px stick-pt 0.0 200.0)]))
            diag (-> start (step :press [stick-pt]) (step :down [(at-px stick-pt 150.0 -150.0)]))
            u (Math/sqrt 0.5)]
        (is (vnear? [(- 2.5 sp) 7.5] (pos back)))
        (is (vnear? [(+ 2.5 (* sp u)) (+ 7.5 (* sp u 0.66))] (pos diag)))))
    (testing "it follows the heading, so it walks where the view looks"
      (let [s (-> (facing (/ Math/PI 2))
                  (step :press [stick-pt]) (step :down [(at-px stick-pt 0.0 -200.0)]))]
        (is (vnear? [2.5 (+ 7.5 sp)] (pos s)))))
    (testing "inside the dead zone nothing moves"
      (is (= (pos start) (pos (-> start (step :press [stick-pt]) (step :down [(at-px stick-pt (* 0.5 slop) 0.0)]))))))
    (testing "walls stop it"
      (let [s (-> (placed 14.99 7.5 0.0)
                  (step :press [stick-pt]) (step :down [(at-px stick-pt 0.0 -200.0)]))]
        (is (= 14.99 (:pos-x s))))))
  (testing "a drag in the right half turns, 0.0022 radians a pixel in a 900 pixel window"
    (let [scale (/ 900.0 1206.0)
          pressed (step start :press [look-pt])
          dragged (step pressed :down [(at-px look-pt 100.0 40.0)])
          left (step pressed :down [(at-px look-pt -100.0 0.0)])]
      (is (= (heading start) (heading pressed)) "the press turns nothing")
      (is (near? (* 0.0022 100.0 scale) (heading dragged)) "a drag right turns right")
      (is (near? (* -0.0022 100.0 scale) (heading left)))
      (is (vnear? (pos start) (pos dragged)) "turning does not walk")
      (testing "the plane turns with the heading, as the original's rotate does"
        (let [t (o-rotate start (* 0.0022 100.0 scale))]
          (is (vnear? ((juxt :dir-x :dir-y :plane-x :plane-y) t)
                      ((juxt :dir-x :dir-y :plane-x :plane-y) dragged)))))
      (testing "turning is relative: a second frame of the same finger adds its own movement"
        (let [more (step dragged :down [(at-px look-pt 160.0 40.0)])]
          (is (near? (* 0.0022 160.0 scale) (heading more)))))))
  (testing "both thumbs work at once, each by its own finger"
    (doseq [[label ids] [["with ids" [[4] [4 5] [4 5]]] ["without ids" [nil nil nil]]]
            :let [[i1 i2 i3] ids]]
      (testing label
        (let [s1 (step start :press [stick-pt] i1)
              s2 (step s1 :down [(at-px stick-pt 0.0 -200.0)] i1)
              s3 (step s2 :press [(at-px stick-pt 0.0 -200.0) look-pt] i2)
              s4 (step s3 :down [(at-px stick-pt 0.0 -200.0) (at-px look-pt 100.0 0.0)] i3)]
          (is (some? (:stick s3)))
          (is (some? (:look s3)))
          (is (not= (heading s3) (heading s4)) "the look finger turned the view")
          (is (not= (pos s3) (pos s4)) "while the stick finger walked")))))
  (testing "the regions: the left half from the middle down is the stick, the right half the turn"
    (is (= :stick (sc/region d [300.0 1900.0])))
    (is (= :look (sc/region d [900.0 300.0])))
    (is (= :look (sc/region d [900.0 1900.0])))
    (is (nil? (sc/region d [300.0 600.0])) "left, above the stick's top")
    (is (nil? (sc/region d fire-pt)) "on the button")
    (is (nil? (sc/region d [100.0 60.0])) "under Back")))

(deftest a-resting-finger-never-steers
  (doseq [[label ids] [["with ids" [[4] [4 5] [5] [5]]] ["without ids" [nil nil nil nil]]]
          :let [[i1 i2 i3 i4] ids]]
    (testing (str "the stick, " label)
      (let [held (-> start (step :press [stick-pt] i1)
                     (step :down [(at-px stick-pt 0.0 -200.0)] i1)
                     (step :press [(at-px stick-pt 0.0 -200.0) (at-px stick-pt 400.0 0.0)] i2))
            lifted (step held :down [(at-px stick-pt 400.0 0.0)] i3)
            later (nth (iterate #(step % :down [(at-px stick-pt 400.0 0.0)] i4) lifted) 6)]
        (is (some? (:stick held)) "the first finger holds the stick")
        (is (nil? (:stick lifted)) "the second does not inherit it")
        (is (= (pos lifted) (pos later)) "the player stays put")))
    (testing (str "the turn, " label)
      (let [held (-> start (step :press [look-pt] i1)
                     (step :down [(at-px look-pt 80.0 0.0)] i1)
                     (step :press [(at-px look-pt 80.0 0.0) (at-px look-pt -300.0 100.0)] i2))
            lifted (step held :down [(at-px look-pt -300.0 100.0)] i3)
            later (nth (iterate #(step % :down [(at-px look-pt -300.0 100.0)] i4) lifted) 6)]
        (is (some? (:look held)))
        (is (nil? (:look lifted)) "the second finger does not inherit the turn")
        (is (= (heading lifted) (heading later)) "the view stays put")))
    (testing (str "a finger already down at the start, " label)
      (let [s (nth (iterate #(step % :down [stick-pt] i1) start) 3)
            moved (step s :down [(at-px stick-pt 0.0 -200.0)] i1)
            r (nth (iterate #(step % :down [look-pt] i1) start) 3)
            turned (step r :down [(at-px look-pt 200.0 0.0)] i1)]
        (is (nil? (:stick s)))
        (is (= (pos start) (pos moved)))
        (is (nil? (:look r)))
        (is (= (heading start) (heading turned)))))
    (testing (str "a finger under Back or on the button never starts anything, " label)
      (doseq [p [[100.0 60.0] fire-pt]]
        (let [s (-> start (step :press [p] i1) (step :down [(at-px p -100.0 -300.0)] i1))]
          (is (nil? (:look s)))
          (is (nil? (:stick s)))
          (is (= (pos start) (pos s)))
          (is (= (heading start) (heading s)))))))
  (testing "a rotation of the phone drops the fingers"
    (let [held (-> start (step :press [look-pt]) (step :down [look-pt]))
          turned (sc/advance held {:metrics {:screen [2334 1206]}
                                   :pointer {:phase :down
                                             :position [1700.0 400.0]}
                                   :touch-points [[1700.0 400.0]]})]
      (is (some? (:look held)))
      (is (nil? (:look turned))))))

;; --- the picture -------------------------------------------------------------------------

(defn- wall-rects
  "The wall rects of the buffer after ceiling and floor and before any sprite or
  flash, for a state without imps."
  [s dims]
  (let [n (sc/build! (assoc s :imps [] :flash 0.0) dims)]
    (subvec (ray/rects n) 2)))

(defn- picture-of
  "The per-column picture of the original's walls in the view `[vx vy vw vh]`,
  as the rect each column is: `[x0 x1 top bot colour]`, with the column start the
  rounded pixel."
  [s [vx vy vw vh]]
  (let [x-of (fn [i] (+ vx (long (+ 0.5 (* i (/ (double vw) cols))))))
        half (+ vy (/ vh 2.0))]
    (vec (for [i (range cols)
               :let [{:keys [dist tile side]} (o-cast-column i cols s)
                     line (/ vh dist)]]
           [(x-of i) (if (= i (dec cols)) (+ vx vw) (x-of (inc i)))
            (long (max (double vy) (- half (/ line 2.0))))
            (long (min (double (+ vy vh)) (+ half (/ line 2.0))))
            (o-pack (o-wall-color tile) (o-shade dist side))]))))

(deftest first-frame-draws
  (let [dims (sc/dimensions phone measure)
        [vx vy vw vh] (:view dims)
        n (sc/build! start dims)
        rs (ray/rects n)
        [ceiling floor] rs
        walls (wall-rects start dims)]
    (testing "the picture is the original's 900 x 560 at the field's width, at its top"
      (is (= [0 192 1206 750] (:view dims)))
      (is (near? (/ 1206.0 900.0) (:k dims))))
    (testing "the ceiling and the floor halve the view, in the original's colours"
      (is (= [0 192 1206 375 (m/pack 28 26 32 255)] ceiling))
      (is (= [0 567 1206 375 (m/pack 48 42 38 255)] floor)))
    (testing "every rect is inside the view"
      (is (every? (fn [[x y w h]] (and (>= x vx) (>= y vy) (<= (+ x w) (+ vx vw)) (<= (+ y h) (+ vy vh)) (pos? w) (pos? h)))
                  rs)))
    (testing "the walls are the original's picture, pixel column by pixel column"
      (is (<= 3 (count walls) cols))
      (let [expected (into {} (mapcat (fn [[x0 x1 top bot colour]] (for [px (range x0 x1)] [px [top bot colour]]))
                                      (picture-of (assoc start :imps []) (:view dims))))
            got (into {} (mapcat (fn [[x y w h colour]] (for [px (range x (+ x w))] [px [y (+ y h) colour]])) walls))]
        (is (= vw (count expected)))
        (is (= vw (reduce + (map #(nth % 2) walls))) "every pixel column is covered by exactly one wall rect")
        (is (= vw (count got)))
        (is (empty? (take 3 (remove (fn [[px e]] (= e (get got px))) expected)))
            "the same top, bottom and lit colour at every pixel column")))
    (testing "straight ahead: a wall 12.5 away is 750 / 12.5 = 60 px tall and 43 over 255 lit"
      (let [mid-x (+ vx (quot vw 2))
            hit (first (filter (fn [[x _ w]] (<= x mid-x (+ x w -1))) walls))]
        (is (= [537 60 (m/pack 25 11 9 255)] (let [[_ y _ h c] hit] [y h c])))))
    (testing "the wall pass is one pass over the columns, merged where the rect is the same"
      (is (< (count walls) cols))
      (let [facing-wall (wall-rects (placed 2.7 7.5 Math/PI) dims)]
        (is (= 1 (count facing-wall)) "a wall square on to the eye is one rect")))
    (testing "the buffer holds the z-buffer's distances, the original's"
      (ray/build! start 0 0 1206 750)
      (doseq [i [0 10 (quot cols 2) (dec cols)]]
        (is (near? (:dist (o-cast-column i cols start)) (ray/zbuffer i)))))
    (testing "the muzzle flash is the last rect, only after a shot"
      (is (not= (m/pack 255 220 140 40) (last (last rs))) "no flash at the start")
      (let [fired (sc/shoot start)
            fn* (sc/build! fired dims)
            last-rect (last (ray/rects fn*))]
        (is (= [0 192 1206 750 (m/pack 255 220 140 40)] last-rect))))
    (testing "the HUD is the original's five texts at its positions, scaled"
      (is (= ["HEALTH 100" "KILLS 0" "IMPS 6" "SHOTS 0" (str "0 fps  " cols " cols")] (mapv :s (hud/hud-lines start dims))))
      (is (= [(+ 0 (long (* 1.34 16))) (+ 0 (long (* 1.34 190)))]
             (mapv :x (take 2 (hud/hud-lines start dims)))))
      (is (= [[235 70 60 255]] (map :colour (take 1 (hud/hud-lines (assoc start :health 39) dims))))))
    (testing "the minimap is the panel and a rect for every wall cell, in its style's colour"
      (let [static (hud/minimap-static dims)
            walls* (count (filter #(not= \. %) (apply str o-level)))]
        (is (= (inc walls*) (count static)))
        (is (= [(long (* 1.34 8)) (+ 192 (long (* 1.34 8)))] (take 2 (first static))))
        (is (= (long (* 1.34 6)) (nth (second static) 2)) "a cell is s - 1 = 6 scaled")))
    (testing "the minimap's living imps, the player and the heading line"
      (let [dyn (hud/minimap-dynamic start dims)]
        (is (= 8 (count dyn)))
        (is (= 6 (count (filter #(= imp-colour* (last %)) dyn)))))
      (is (= 4 (count (hud/minimap-dynamic (sc/shoot (with-imps start [6.5 7.5] [9.5 2.5] [4.5 4.5])) dims)))
          "two living imps, the player and the line, the shot one gone"))
    (testing "the crosshair is four short lines round the middle"
      (is (= 4 (count (hud/crosshair dims)))))
    (testing "YOU DIED only at 0 health"
      (is (nil? (hud/died start dims)))
      (is (= "YOU DIED" (:s (hud/died (assoc start :health 0) dims)))))))

(deftest sprites-are-cut-into-twelve-strips-and-tested-against-the-walls
  (let [dims (sc/dimensions phone measure)
        imps-of (fn [s]
                  (let [walls (count (wall-rects s dims))
                        n (sc/build! s dims)]
                    (subvec (ray/rects n) (+ 2 walls))))
        of-colour (fn [rs colour] (filter #(= colour (nth % 4)) rs))]
    (testing "an imp in the open, 3 cells ahead, shows the strips the texel reaches: 8 of body, 4 of head, 2 of eyes"
      (let [s (with-imps start [5.5 7.5])
            {:keys [ty]} (first (o-visible-imps s))
            sh (m/shade ty 0)
            rs (imps-of s)
            bodies (of-colour rs (nth m/imp-packed sh))]
        (is (near? 3.0 ty))
        (is (= 8 (count bodies)) "strips whose middle is within 20.5 texels of the centre: 2 to 9")
        (is (= 4 (count (of-colour rs (nth m/imp-packed (+ 256 sh))))) "within 12.2: 4 to 7")
        (is (= 2 (count (of-colour rs (nth m/imp-packed (+ 512 sh))))) "within 3 of an eye: strips 4 and 7")
        (testing "560 / 3 tall, centred on the horizon: the body runs from tile row 13.6 to the bottom"
          (is (= 495 (apply min (map #(nth % 1) bodies))))
          (is (= 692 (apply max (map #(+ (nth % 1) (nth % 3)) bodies)))))))
    (testing "an imp behind the pillar at x = 6 loses the strips whose middle column a wall is nearer than"
      (let [s (with-imps start [8.5 6.5])
            {:keys [tx ty]} (first (o-visible-imps s))
            screen-x (* (/ cols 2.0) (+ 1.0 (/ tx ty)))
            size (/ cols ty)
            passes (vec (for [k (range 12)
                              :let [a (+ (- screen-x (/ size 2.0)) (* size (/ (double k) 12)))
                                    b (+ (- screen-x (/ size 2.0)) (* size (/ (+ k 1.0) 12)))
                                    mid (long (/ (+ a b) 2.0))]]
                          (and (>= mid 0) (< mid cols) (< ty (:dist (o-cast-column mid cols s))))))
            expected-bodies (count (filter true? (map-indexed (fn [k ok] (and ok (<= 2 k 9))) passes)))
            bodies (of-colour (imps-of s) (nth m/imp-packed (m/shade ty 0)))]
        (is (< (count (filter true? passes)) 12) "some strips are hidden")
        (is (pos? (count (filter true? passes))) "and some are not")
        (is (= expected-bodies (count bodies)))))
    (testing "an imp wholly behind a wall draws nothing"
      (is (empty? (imps-of (with-imps (placed 2.5 8.5 0.0) [9.5 8.5])))))
    (testing "an imp behind the player draws nothing, nor one nearer than 0.25"
      (is (empty? (imps-of (with-imps start [1.5 7.5]))))
      (is (empty? (imps-of (with-imps start [2.7 7.5])))))
    (testing "furthest first: the nearer imp is drawn over the other"
      (let [rs (imps-of (with-imps start [9.5 7.5] [4.5 7.5]))
            index-of (fn [colour] (keep-indexed (fn [i r] (when (= colour (nth r 4)) i)) rs))
            far (index-of (nth m/imp-packed (m/shade 7.0 0)))
            near (index-of (nth m/imp-packed (m/shade 2.0 0)))]
        (is (seq far))
        (is (seq near))
        (is (< (apply max far) (apply min near)))))
    (testing "a dead imp is not drawn"
      (is (empty? (imps-of (with-imps start [5.5 7.5 false])))))
    (testing "the body, head and eyes are the texel's colours lit by the shade"
      (is (= (m/lit [112 52 40] 100) (nth m/imp-packed 100)))
      (is (= (m/lit [150 74 52] 100) (nth m/imp-packed 356)))
      (is (= (m/lit [255 226 92] 100) (nth m/imp-packed 612))))))

(deftest the-rect-buffer-is-walked-in-order
  (let [dims (sc/dimensions phone measure)
        n (sc/build! (with-imps start [5.5 7.5]) dims)
        seen (atom [])]
    (sc/draw-rects! n (fn [x y w h c] (swap! seen conj [x y w h c])))
    (is (= (ray/rects n) @seen))
    (is (every? integer? (apply concat @seen)))))

;; --- text --------------------------------------------------------------------------------

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [fx fy fw fh] (:viewport dims)
                {:keys [cx cy r]} (:fire dims)]]
    (testing (str screen)
      (is (= 7 (count (:lines dims))) "the caption, the label and the five HUD texts")
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= size 8) s)
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y 0) s)
        (is (<= (+ y size) h) s))
      (testing "the caption sits between Back and the field"
        (let [{:keys [s x y size]} (:caption dims)]
          (is (>= y (+ back-y back-h)) s)
          (is (<= (+ y size) fy) s)
          (is (<= (+ x (measure s size)) w) s)))
      (testing "the HUD sits inside the picture"
        (let [[vx vy vw vh] (:view dims)]
          (doseq [{:keys [s x y size]} (:hud dims)]
            (is (>= x vx) s)
            (is (>= y vy) s)
            (is (<= (+ x (measure s size)) (+ vx vw)) s)
            (is (<= (+ y size) (+ vy vh)) s))))
      (testing "the button is inside the field and its label inside the button"
        (is (>= (- cx r) fx))
        (is (<= (+ cx r) (+ fx fw)))
        (is (>= (- cy r) fy))
        (is (<= (+ cy r) (+ fy fh)))
        (let [{:keys [s x y size]} (:fire-label dims)]
          (is (>= x (- cx r)) s)
          (is (<= (+ x (measure s size)) (+ cx r)) s)
          (is (>= y (- cy r)) s)
          (is (<= (+ y size) (+ cy r)) s)))
      (testing "the picture fits the field"
        (let [[vx vy vw vh] (:view dims)]
          (is (>= vx fx))
          (is (<= (+ vx vw) (+ fx fw)))
          (is (>= vy fy))
          (is (<= (+ vy vh) (+ fy fh)))))
      (testing "the stick may begin inside the field, away from the button"
        (is (< (+ fy (* 0.5 fh)) (+ fy fh)))
        (is (not (sc/on-fire? dims [(+ fx 10.0) (+ fy fh -10.0)])))))))

(deftest the-held-stick-draws-a-ring-and-a-knob-within-it
  (let [dims (sc/dimensions phone measure)
        s (-> start (step :press [stick-pt]) (step :down [(at-px stick-pt 0.0 -300.0)]))
        shape (sc/stick-shape s dims)]
    (is (nil? (sc/stick-shape start dims)))
    (is (= [300.0 1900.0] (:centre shape)))
    (is (near? (:stick-r dims) (:r shape)))
    (is (vnear? [300.0 (- 1900.0 (:stick-r dims))] (:knob shape)) "held to the ring")))

(deftest the-scene-contract
  (let [sc* (sc/scene)]
    (is (= :doom (:id sc*)))
    (is (= "Doom-like Raycaster" (:title sc*)))
    (is (= [:scene/init :doom] (first (second ((:init sc*) {:metrics phone})))))
    (let [[st fx] ((:draw sc*) start {})]
      (is (= start st))
      (is (= [] fx)))))
