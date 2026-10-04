(ns net.b12n.raylib-ios.scenes.invaders-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.invaders :as inv]))

(def m {:screen [1206 2334]})
(def landscape {:screen [2334 1206]})
(def d (inv/dimensions m))
(def dl (inv/dimensions landscape))
(def start (first ((:init (inv/scene)) {:metrics m})))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (inv/advance state {:metrics metrics
                       :pointer {:phase phase
                                 :position position}})))

(defn- idle [state] (step state :idle nil))

(defn- frames [state n] (nth (iterate idle state) n))

(defn- tap [state pos]
  (-> state (step :press pos) (step :release pos)))

(defn- near? [a b] (< (Math/abs (double (- a b))) 1e-6))

(defn- only
  "`state` with just the aliens `cells` left."
  [state cells]
  (assoc state :aliens (set cells)))

(deftest the-ship-follows-a-finger
  (let [sw (:ship-w d)
        centre (fn [st] (+ (:ship-x st) (/ sw 2)))]
    (testing "the ship's centre sits under the finger's x"
      (is (near? 600 (centre (step start :press [600 1800])))))
    (testing "it follows a drag"
      (is (near? 900 (centre (-> start (step :press [600 1800]) (step :down [900 1900]))))))
    (testing "it stops at both screen edges"
      (is (near? 0 (:ship-x (step start :press [5 1800]))))
      (is (near? (- 1206 sw) (:ship-x (step start :press [1200 1800])))))
    (testing "it holds when the finger lifts, and a release does not move it"
      (let [dragged (step start :press [300 1800])]
        (is (= (:ship-x dragged) (:ship-x (idle dragged))))
        (is (= (:ship-x dragged) (:ship-x (step dragged :release [1100 100]))))))))

(deftest holding-fires-at-the-originals-rate
  (testing "a cooldown of 15 means one shot every 16 frames while held"
    (is (= 15 inv/cooldown-frames))
    (let [run (reductions (fn [s _] (step s :down [600 1800]))
                          (step start :press [600 1800])
                          (range 48))
          ;; A bullet is born at the ship's row, and every other bullet has moved up.
          fired (keep-indexed (fn [i s] (when (some #(= (:ship-y d) (:y %)) (:bullets s)) i)) run)]
      (is (= [0 16 32 48] fired))))
  (testing "no finger, no shot"
    (is (empty? (:bullets (frames start 40)))))
  (testing "the first bullet leaves from the ship's centre and rises"
    (let [s1 (step start :press [600 1800])
          s2 (step s1 :down [600 1800])
          [b] (:bullets s1)]
      (is (near? 600 (:x b)))
      (is (near? (:ship-y d) (:y b)))
      (is (near? (- (:ship-y d) (:bullet-speed d)) (:y (first (:bullets s2))))))))

(deftest a-bullet-hit-removes-an-alien
  (let [[ax ay aw ah] (inv/alien-rect d (:ax start) (:ay start) [3 2])
        st (-> start
               (assoc :bullets [{:x (+ ax (/ aw 2))
                                 :y (+ ay ah (:bullet-speed d) -1.0)}])
               (assoc :cooldown 9))
        after (idle st)]
    (is (not (contains? (:aliens after) [3 2])))
    (is (= 31 (count (:aliens after))))
    (is (= 10 (:score after)))
    (is (empty? (:bullets after)) "the bullet is spent")
    (testing "a bullet beside it passes by"
      (let [miss (idle (assoc st :bullets [{:x (- ax 3.0)
                                            :y (+ ay ah (:bullet-speed d) -1.0)}]))]
        (is (= 32 (count (:aliens miss))))
        (is (= 1 (count (:bullets miss))))))))

(deftest the-formation-marches-and-drops-at-an-edge
  (let [{:keys [speed drop margin sp alien-w w]} d
        s1 (idle start)]
    (testing "it steps sideways at the original's 1.2 scaled by width"
      (is (near? (* 1.2 (/ 1206 800.0)) speed))
      (is (near? (+ (:ax start) speed) (:ax s1)))
      (is (= (:ay start) (:ay s1))))
    (testing "at the right wall it reverses and drops, without moving sideways"
      (let [edge (assoc start :ax (- w margin alien-w (* 7 sp) (* 0.5 speed)))
            after (idle edge)]
        (is (= -1.0 (:adir after)))
        (is (near? (+ (:ay start) drop) (:ay after)))
        (is (= (:ax edge) (:ax after)))))
    (testing "then walks back the other way"
      (let [after (frames (assoc start :ax (- w margin alien-w (* 7 sp) (* 0.5 speed))) 2)]
        (is (< (:ax after) (- w margin alien-w (* 7 sp))))))
    (testing "at the left wall it reverses and drops"
      (let [edge (assoc start :ax (+ margin (* 0.5 speed)) :adir -1.0)
            after (idle edge)]
        (is (= 1.0 (:adir after)))
        (is (near? (+ (:ay start) drop) (:ay after)))))
    (testing "the edge is the outermost surviving column"
      (let [edge (-> start (only [[0 0] [1 0]]) (assoc :ax (- w margin alien-w (* 7 sp) (* 0.5 speed))))
            after (idle edge)]
        (is (= 1.0 (:adir after)) "column 7 is gone, so no reversal yet")
        (is (> (:ax after) (:ax edge)))))
    (testing "a full sweep takes the original's frame count"
      (let [;; the original starts at x 40 and turns when the right edge passes 800 - 6
            sweep (- 800 6 40 (+ (* 7 60) 40))
            n (/ sweep 1.2)
            ;; the same frame count, measured here from the start to the first drop
            first-drop (first (keep-indexed (fn [i s] (when (not= (:ay s) (:ay start)) i))
                                            (take 20000 (iterate idle start))))]
        (is (<= (Math/abs (- n first-drop)) 2))))))

(deftest aliens-reaching-the-ship-end-the-game
  (let [{:keys [ship-y alien-h row-sp]} d
        ;; Bottom row's bottom edge just above, then just at the ship's row.
        ay-above (- ship-y alien-h (* 3 row-sp) 1.0)
        ay-at (- ship-y alien-h (* 3 row-sp))]
    (testing "one pixel short of the ship's row is still a game"
      (is (not (:over? (idle (assoc start :ay ay-above))))))
    (testing "touching the ship's row ends it"
      (is (:over? (idle (assoc start :ay ay-at)))))
    (testing "the lowest surviving alien is the one that counts"
      (let [near (only start [[0 1]])]
        (is (not (:over? (idle (assoc near :ay ay-at)))))))
    (testing "a drop that lands on the row ends it"
      (let [edge (assoc start :ay (- ay-above (- (:drop d) 2.0))
                        :ax (- (:w d) (:margin d) (:alien-w d) (* 7 (:sp d)) (* 0.5 (:speed d))))
            after (idle edge)]
        (is (:over? after))))
    (testing "the game then stops moving"
      (let [over (idle (assoc start :ay ay-at))]
        (is (= over (frames over 20)))))
    (testing "the formation is still on the screen when it ends"
      (let [over (first (filter :over? (take 20000 (iterate idle start))))
            [_ y _ h] (inv/alien-rect d (:ax over) (:ay over) [0 3])]
        (is (<= (+ y h) 2334))))))

(deftest clearing-the-formation-wins
  (let [{:keys [alien-h]} d
        [ax ay aw ah] (inv/alien-rect d (:ax start) (:ay start) [4 1])
        st (-> start (only [[4 1]])
               (assoc :bullets [{:x (+ ax (/ aw 2))
                                 :y (+ ay ah (:bullet-speed d) -1.0)}])
               (assoc :cooldown 5))
        won (idle st)]
    (is (pos? alien-h))
    (is (:won? won))
    (is (empty? (:aliens won)))
    (is (= 10 (:score won)))
    (testing "and it stops there"
      (is (= won (frames won 20))))
    (testing "a press alone does not restart it"
      (is (:won? (step won :press [600 1400]))))))

(deftest bullets-are-removed-off-screen
  (testing "a bullet above the play area is dropped"
    (let [st (assoc (only start []) :bullets [{:x 600.0
                                               :y (+ (:top d) 1.0)}] :won? false)
          st (assoc st :aliens #{[0 0]})
          after (idle st)]
      (is (empty? (:bullets after)))))
  (testing "600 frames of held fire into an empty sky stay bounded"
    (let [st (assoc start :aliens #{[0 3]} :ax 700.0 :adir 1.0)
          ;; keep the formation out of the way: one alien near the right wall that
          ;; never reaches the ship in 600 frames
          run (reductions (fn [s _] (step s :down [100 1800]))
                          (step st :press [100 1800])
                          (range 600))
          peak (apply max (map (comp count :bullets) run))
          ;; a bullet lives about (ship-y - top) / speed frames and one is fired every 16
          bound (inc (long (Math/ceil (/ (/ (- (:ship-y d) (:top d)) (:bullet-speed d)) 16))))]
      (is (pos? peak))
      (is (<= peak bound))
      (is (every? #(>= (:y %) (:top d)) (mapcat :bullets run))))))

(deftest a-tap-restarts
  (let [over (assoc start :over? true :score 120 :aliens #{[2 2]} :ax 300.0 :ay 900.0)
        won (assoc start :won? true :score 320 :aliens #{})]
    (doseq [[label dead] [["game over" over] ["a win" won]]]
      (testing label
        (let [after (tap dead [600 1400])]
          (is (not (:over? after)))
          (is (not (:won? after)))
          (is (= 0 (:score after)))
          (is (= 32 (count (:aliens after))))
          (is (= (:ay start) (:ay after)))
          (is (near? 600 (+ (:ship-x after) (/ (:ship-w d) 2)))))
        (testing "a press alone does not restart it"
          (is (or (:over? (step dead :press [600 1400])) (:won? (step dead :press [600 1400])))))
        (testing "a tap under Back does not"
          (is (or (:over? (tap dead [100 60])) (:won? (tap dead [100 60]))))
          (is (or (:over? (tap dead [399 119])) (:won? (tap dead [399 119]))))
          (is (not (or (:over? (tap dead [400 119])) (:won? (tap dead [400 119])))))
          (is (not (or (:over? (tap dead [100 120])) (:won? (tap dead [100 120]))))))))
    (testing "a tap while playing does not reset the score"
      (is (= 50 (:score (tap (assoc start :score 50) [600 1400])))))))

(deftest the-formation-starts-below-back
  (let [[bx by bw bh] gesture/back-region
        overlaps? (fn [[x y w h]]
                    (and (< x (+ bx bw)) (< bx (+ x w))
                         (< y (+ by bh)) (< by (+ y h))))]
    (doseq [screen [[1206 2334] [2334 1206] [800 450] [450 800]]
            :let [dm (inv/dimensions {:screen screen})
                  st (first ((:init (inv/scene)) {:metrics {:screen screen}}))]]
      (testing (str screen)
        (is (>= (:top dm) (+ by bh)))
        (is (>= (:ay st) (+ by bh)))
        (doseq [cell (:aliens st)]
          (is (not (overlaps? (inv/alien-rect dm (:ax st) (:ay st) cell))) (str cell)))
        (is (not (overlaps? (inv/ship-rect dm 0.0))))
        (is (not (overlaps? (inv/bullet-rect dm {:x 0.0
                                                 :y (:top dm)}))))
        (doseq [k [:score-y :msg-y]]
          (is (>= (get dm k) (+ by bh)) (str k)))))))

(deftest everything-drawn-fits-the-screen
  (doseq [screen [[1206 2334] [2334 1206] [800 450] [450 800]]
          :let [[w h] screen
                dm (inv/dimensions {:screen screen})
                inside? (fn [[x y rw rh]]
                          (and (<= 0 x) (<= 0 y) (<= (+ x rw) (+ w 1e-6)) (<= (+ y rh) (+ h 1e-6))))
                st (first ((:init (inv/scene)) {:metrics {:screen screen}}))]]
    (testing (str screen)
      (is (inside? (inv/ship-rect dm 0.0)))
      (is (inside? (inv/ship-rect dm (- w (:ship-w dm)))))
      (is (inside? (inv/bullet-rect dm {:x (* 0.5 w)
                                        :y (:ship-y dm)})))
      (doseq [cell (:aliens st)]
        (is (inside? (inv/alien-rect dm (:ax st) (:ay st) cell))))
      (testing "even the formation that has just reached the ship"
        (let [step* (fn [s] (inv/advance s {:metrics {:screen screen}
                                            :pointer {:phase :idle
                                                      :position nil}}))
              end (first (filter :over? (take 20000 (iterate step* st))))]
          (is (some? end))
          (doseq [cell (:aliens end)]
            (is (inside? (inv/alien-rect dm (:ax end) (:ay end) cell)))))))))

(deftest rotation-starts-a-new-game
  (let [played (assoc start :score 90 :ay 700.0 :over? true :bullets [{:x 5.0
                                                                       :y 900.0}])
        after (step played landscape :idle nil)
        fresh (first ((:init (inv/scene)) {:metrics landscape}))]
    (is (= [2334 1206] (:screen after)))
    (is (= 0 (:score after)))
    (is (not (:over? after)))
    (is (empty? (:bullets after)))
    (is (= (:ay fresh) (:ay after)))
    (is (= (:ship-x fresh) (:ship-x after)))
    (testing "and the first frame after init does not reset"
      (let [playing (assoc start :score 30)
            after (idle playing)]
        (is (= 30 (:score after)))
        (is (= [1206 2334] (:screen after)))
        (is (near? (+ (:ax playing) (:speed d)) (:ax after)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen [[1206 2334] [2334 1206] [800 450] [450 800]]
          :let [[w h] screen
                dm (inv/dimensions {:screen screen})
                {:keys [score-size msg-size msg-y]} dm
                ;; estimate: 0.6 of the size per character, for raylib's default font.
                fits (fn [x y size line]
                       (and (<= 0 x) (<= (+ x (* 0.6 size (count line))) w)
                            (<= 0 y) (<= (+ y size) h)))]]
    (testing (str screen)
      (is (fits (:score-x dm) (:score-y dm) score-size (inv/score-line 9999)))
      (doseq [line [inv/over-line inv/win-line]]
        (is (fits (inv/msg-x dm line) msg-y msg-size line) line)))))

(deftest the-gesture-is-kept-in-state
  (is (= gesture/idle (:gesture start)))
  (is (= [600 1800] (:start (:gesture (step start :press [600 1800])))))
  (testing "a drag that ends as a swipe moves nothing a second time"
    (let [dragged (-> start (step :press [300 1800]) (step :down [900 1800]))
          released (step dragged :release [900 1800])]
      (is (= (:ship-x dragged) (:ship-x released))))))

(deftest the-bullet-speed-is-capped-below-half-an-alien
  (doseq [screen [[1206 2334] [2334 1206] [800 450] [450 800]]
          :let [dm (inv/dimensions {:screen screen})]]
    (testing (str screen)
      (is (<= (:bullet-speed dm) (* 0.5 (:alien-h dm)))))))

(deftest a-fast-bullet-cannot-skip-an-alien
  ;; Uncapped, the bullet moves 8 * sy = 39.36 px a frame on the phone, more than
  ;; an alien's height of 39.2. Starting 0.1 px below the alien, one uncapped step
  ;; would land 0.07 px above it and the point test would never see it.
  (let [[px py pw ph] (inv/alien-rect d (:ax start) (:ay start) [0 0])
        st (-> start
               (only [[0 0] [7 3]])
               (assoc :bullets [{:x (+ px (/ pw 2))
                                 :y (+ py ph 0.1)}]))
        after (nth (iterate idle st) 5)]
    (is (not (contains? (:aliens after) [0 0])))
    (is (= 10 (:score after)))))

(deftest a-touch-held-through-game-over-does-not-restart
  (let [{:keys [alien-h]} d
        [ax ay aw ah] (inv/alien-rect d (:ax start) (:ay start) [4 1])
        winning (-> start (only [[4 1]])
                    (assoc :bullets [{:x (+ ax (/ aw 2))
                                      :y (+ ay ah (:bullet-speed d) -1.0)}])
                    (assoc :cooldown 5))
        ;; The frame just before the formation reaches the ship.
        before-loss (last (take-while (complement :over?) (take 20000 (iterate idle start))))]
    (is (pos? alien-h))
    (testing "a win on the frame the finger lands"
      (let [won (step winning :press [600 1400])
            lifted (-> won (step :down [600 1400]) (step :release [600 1400]))]
        (is (:won? won))
        (is (:won? lifted))
        (is (= 10 (:score lifted)))))
    (testing "a loss on the frame the finger lands"
      (let [over (step before-loss :press [600 1400])
            lifted (-> over (step :down [600 1400]) (step :release [600 1400]))]
        (is (:over? over))
        (is (:over? lifted))
        (is (= (:score over) (:score lifted)))))))

(deftest a-fresh-tap-after-game-over-restarts
  (let [{:keys [alien-h]} d
        [ax ay aw ah] (inv/alien-rect d (:ax start) (:ay start) [4 1])
        won (-> start (only [[4 1]])
                (assoc :bullets [{:x (+ ax (/ aw 2))
                                  :y (+ ay ah (:bullet-speed d) -1.0)}])
                (assoc :cooldown 5)
                (step :press [600 1400])
                (step :release [600 1400]))
        after (tap won [600 1400])]
    (is (pos? alien-h))
    (is (:won? won))
    (is (not (:won? after)))
    (is (= 0 (:score after)))
    (is (= 32 (count (:aliens after))))))
