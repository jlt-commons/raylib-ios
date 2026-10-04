(ns net.b12n.raylib-ios.scenes.pacman-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.pacman :as pm]
            [net.b12n.raylib-ios.scenes.pacman.ghosts :as gh]
            [net.b12n.raylib-ios.scenes.pacman.maze :as mz]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(def start (first ((:init (pm/scene)) {:metrics m})))

(defn- adv
  ([state phase pos] (adv state phase pos 0.0))
  ([state phase pos dt]
   (pm/advance state {:metrics m
                      :delta-seconds dt
                      :pointer {:phase phase
                                :position pos}})))

(defn- idle
  ([state] (idle state 0.0))
  ([state dt] (adv state :idle nil dt)))

(defn- run [state dt n] (nth (iterate #(idle % dt) state) n))

(defn- swipe [state dir]
  (let [[from to] (case dir
                    :up [[600 1500] [600 1200]]
                    :down [[600 1500] [600 1800]]
                    :left [[600 1500] [300 1500]]
                    :right [[600 1500] [900 1500]])]
    (-> state (adv :press from) (adv :down to) (adv :release to))))

(defn- tap [state pos]
  (-> state (adv :press pos) (adv :release pos)))

(defn- near? [a b] (< (Math/abs (double (- a b))) 1e-6))

(defn- run-until
  "Idle frames of `dt` until `(pred state)` or `limit` frames. Answers the state."
  [state dt limit pred]
  (loop [s state n 0]
    (if (or (pred s) (>= n limit))
      s
      (recur (idle s dt) (inc n)))))

(defn- ghost [s nm] (first (filter #(= nm (:name %)) (:ghosts s))))

(defn- put-pac [s tx ty]
  (-> s (assoc-in [:pac :x] (+ tx 0.5)) (assoc-in [:pac :y] (+ ty 0.5))))

(deftest a-swipe-queues-a-turn-taken-at-the-next-open-tile
  (let [queued (swipe start :up)]
    (is (= [0 -1] [(get-in queued [:pac :ndx]) (get-in queued [:pac :ndy])]))
    (testing "Pac-Man keeps going left, because (9, 14) above the start is a wall"
      (is (= [-1 0] [(get-in queued [:pac :dx]) (get-in queued [:pac :dy])])))
    (testing "and turns up at the first tile whose upper neighbour is open, (8, 15)"
      (let [turned (run-until queued 0.05 200 #(= [0 -1] [(get-in % [:pac :dx]) (get-in % [:pac :dy])]))]
        (is (= [0 -1] [(get-in turned [:pac :dx]) (get-in turned [:pac :dy])]))
        (is (= 8 (mz/tile-of (get-in turned [:pac :x]))))))))

(deftest a-swipe-into-a-wall-is-remembered
  (let [queued (swipe start :down)
        later (run queued 0.05 3)]
    (testing "(9, 16) below the start is a wall, so Pac-Man keeps heading left"
      (is (= [-1 0] [(get-in later [:pac :dx]) (get-in later [:pac :dy])])))
    (testing "but the turn is still queued"
      (is (= [0 1] [(get-in later [:pac :ndx]) (get-in later [:pac :ndy])])))
    (testing "and taken at the first tile with an open way down, (5, 15)"
      (let [turned (run-until later 0.05 400 #(= [0 1] [(get-in % [:pac :dx]) (get-in % [:pac :dy])]))]
        (is (= [0 1] [(get-in turned [:pac :dx]) (get-in turned [:pac :dy])]))
        (is (= 5 (mz/tile-of (get-in turned [:pac :x]))))))))

(deftest a-swipe-is-the-only-input-that-steers
  (let [tapped (tap start [600 1500])]
    (is (= [-1 0] [(get-in tapped [:pac :ndx]) (get-in tapped [:pac :ndy])])))
  (testing "a swipe on a stopped Pac-Man at a wall sets him off at the next frame"
    (let [wall (-> start (assoc-in [:pac :x] 1.5) (assoc-in [:pac :y] 1.5)
                   (assoc-in [:pac :dx] 0) (assoc-in [:pac :dy] 0)
                   (assoc-in [:pac :ndx] 0) (assoc-in [:pac :ndy] 0))
          after (run (swipe wall :right) 0.05 4)]
      (is (> (get-in after [:pac :x]) 1.5)))))

(deftest pellets-score-and-clear
  (testing "a dot is 10 and goes from the set"
    (let [s (pm/eat (put-pac start 1 1))]
      (is (= 10 (:score s)))
      (is (not (contains? (:dots s) [1 1])))
      (is (= (dec (count (:dots start))) (count (:dots s))))))
  (testing "a power pellet is 50"
    (is (= 50 (:score (pm/eat (put-pac start 1 2))))))
  (testing "an empty tile scores nothing, and eating is once only"
    (is (= 0 (:score (pm/eat (put-pac start 9 15)))))
    (is (= 10 (:score (pm/eat (pm/eat (put-pac start 1 1)))))))
  (testing "the last dot shows LEVEL CLEARED for two seconds, then the next level keeps score and lives"
    (let [last-dot (-> (put-pac start 1 1)
                       (assoc :dots #{[1 1]} :score 70 :lives 2))
          cleared (pm/tick last-dot 0.05)]
      (is (= "LEVEL CLEARED" (:message cleared)))
      (is (:cleared? cleared))
      (is (= 1 (:level cleared)))
      (testing "and the two seconds run down, not restart each frame"
        (is (< (:message-timer (pm/tick cleared 0.5)) 1.6)))
      (let [next-level (run-until cleared 0.05 100 #(= 2 (:level %)))]
        (is (= 2 (:level next-level)))
        (is (= 80 (:score next-level)))
        (is (= 2 (:lives next-level)))
        (is (= (count (:dots start)) (count (:dots next-level))))
        (is (not (:cleared? next-level)))))))

(deftest the-board-freezes-while-level-cleared-shows
  (let [cleared (pm/tick (-> (put-pac start 1 1) (assoc :dots #{[1 1]} :lives 2)) 0.05)
        pac (:pac cleared)
        hunted (assoc cleared :ghosts
                      (mapv #(assoc % :x (:x pac) :y (:y pac) :frightened 0.0) (:ghosts cleared)))
        later (run hunted 0.05 20)]
    (is (:cleared? hunted))
    (testing "a ghost on Pac-Man costs no life and ends nothing"
      (is (= 2 (:lives later)))
      (is (not (:over? later))))
    (testing "Pac-Man and the ghosts stay where they were"
      (is (= (:pac hunted) (:pac later)))
      (is (= (:ghosts hunted) (:ghosts later))))
    (testing "the clock still runs and the next level starts after two seconds"
      (is (> (:clock later) (:clock hunted)))
      (let [next-level (run-until hunted 0.05 100 #(= 2 (:level %)))]
        (is (= 2 (:level next-level)))
        (is (= 2 (:lives next-level)))))))

(deftest a-power-pellet-frightens-the-ghosts
  (let [s (pm/eat (-> (put-pac start 1 2) (assoc :combo 3)))]
    (is (every? #(= pm/frightened-seconds (:frightened %)) (:ghosts s)))
    (is (= 7.0 pm/frightened-seconds))
    (is (= 0 (:combo s))))
  (testing "a frightened ghost is slower than a hunting one"
    (let [free (-> (gh/initial-ghosts) first (assoc :x 6.0 :y 3.5 :dx 1 :dy 0))
          ctx {:pac (:pac start)
               :ghosts [free]
               :chase? true
               :dt 0.05
               :pick 0}
          hunting (gh/move-ghost free ctx)
          scared (gh/move-ghost (assoc free :frightened 7.0) ctx)]
      (is (near? 4.6 (/ (- (:x hunting) 6.0) 0.05)))
      (is (near? 3.1 (/ (- (:x scared) 6.0) 0.05)))))
  (testing "the fright runs out after seven seconds"
    ;; a ghost still in the house keeps its fright until it is out, as the
    ;; original's does, so release them all first.
    (let [out (fn [s] (update s :ghosts (partial mapv #(assoc % :home-timer 0.0))))
          s (out (pm/eat (put-pac start 1 2)))]
      (is (every? #(zero? (:frightened %)) (:ghosts (run s 0.05 141))))
      (is (every? #(pos? (:frightened %)) (:ghosts (run s 0.05 130))))))
  (testing "a frightened ghost picks any open way but back, and never walks into a wall"
    (let [s (-> (pm/eat (put-pac start 1 2)) (assoc :cleared? false))
          after (run s 0.05 300)]
      (doseq [g (:ghosts after)]
        (is (not (mz/ghost-wall? (mz/tile-of (:x g)) (mz/tile-of (:y g)))))))))

(deftest blinky-targets-pacman
  (let [pac {:x 5.5
             :y 3.5
             :fx 1
             :fy 0}
        g (ghost start "blinky")]
    (is (= [5 3] (gh/target g pac (:ghosts start))))))

(deftest pinky-targets-four-ahead
  (let [g (ghost start "pinky")]
    (is (= [9 3] (gh/target g {:x 5.5
                               :y 3.5
                               :fx 1
                               :fy 0} (:ghosts start))))
    (is (= [5 -1] (gh/target g {:x 5.5
                                :y 3.5
                                :fx 0
                                :fy -1} (:ghosts start))))
    (is (= [1 3] (gh/target g {:x 5.5
                               :y 3.5
                               :fx -1
                               :fy 0} (:ghosts start))))))

(deftest inky-reflects-through-two-ahead
  (let [g (ghost start "inky")
        blinky (assoc (ghost start "blinky") :x 3.5 :y 5.5)
        ghosts (assoc (:ghosts start) 0 blinky)]
    ;; two ahead of (5, 3) heading right is (7, 3); reflecting Blinky's (3, 5)
    ;; through it gives (2*7 - 3, 2*3 - 5).
    (is (= [11 1] (gh/target g {:x 5.5
                                :y 3.5
                                :fx 1
                                :fy 0} ghosts)))))

(deftest clyde-retreats-within-eight
  (let [g (assoc (ghost start "clyde") :x 5.5 :y 3.5)]
    (testing "far away he chases"
      (is (= [15 3] (gh/target (assoc g :x 5.5) {:x 15.5
                                                 :y 3.5
                                                 :fx 1
                                                 :fy 0} (:ghosts start)))))
    (testing "within eight tiles (Manhattan) he breaks for his own corner"
      (is (= [0 20] (gh/target g {:x 8.5
                                  :y 3.5
                                  :fx 1
                                  :fy 0} (:ghosts start))))
      (is (= [0 20] (gh/target g {:x 13.5
                                  :y 3.5
                                  :fx 1
                                  :fy 0} (:ghosts start)))))
    (testing "just over eight he chases again"
      (is (= [14 3] (gh/target g {:x 14.5
                                  :y 3.5
                                  :fx 1
                                  :fy 0} (:ghosts start)))))))

(deftest scatter-chase-timings-match-the-original
  (testing "the original opens in scatter for 7 s, then chases for 20 s, alternating"
    (is (not (:chase? start)))
    (is (= 7.0 (:mode-timer start)))
    (is (= 7.0 pm/scatter-seconds))
    (is (= 20.0 pm/chase-seconds))
    (let [at (fn [s secs] (nth (iterate #(pm/advance-mode % 1.0) s) secs))
          after-6 (at start 6)
          after-7 (at start 7)
          after-27 (at start 27)
          after-34 (at start 34)]
      (is (not (:chase? after-6)))
      (is (:chase? after-7))
      (is (= 20.0 (:mode-timer after-7)))
      (is (:chase? (at start 26)))
      (is (not (:chase? after-27)))
      (is (= 7.0 (:mode-timer after-27)))
      (is (:chase? after-34))))
  (testing "ghosts hunt in chase and head for their corners in scatter"
    (let [g (assoc (ghost start "blinky") :x 9.5 :y 7.5 :dx 0 :dy -1)
          ctx {:pac {:x 1.5
                     :y 7.5
                     :fx -1
                     :fy 0}
               :ghosts [g]
               :dt 0.2
               :pick 0}]
      (is (= -1 (:dx (gh/move-ghost g (assoc ctx :chase? true)))))
      (is (= 1 (:dx (gh/move-ghost g (assoc ctx :chase? false))))))))

(deftest eating-a-frightened-ghost-scores-and-returns-it
  (let [on-pac (fn [s nm] (let [{:keys [x y]} (:pac s)
                                i (.indexOf (mapv :name (:ghosts s)) nm)]
                            (update-in s [:ghosts i] assoc :x x :y y :frightened 5.0)))
        s (-> start (on-pac "blinky") (on-pac "pinky"))
        after (pm/collide s)]
    (testing "200 then 400 within one pellet"
      (is (= 600 (:score after)))
      (is (= 2 (:combo after))))
    (testing "the ghost is back in the house for 1.5 s, no longer frightened"
      (let [b (ghost after "blinky")]
        (is (= [9.5 9.5] [(:x b) (:y b)]))
        (is (= 1.5 (:home-timer b)))
        (is (zero? (:frightened b)))))
    (testing "no life is lost"
      (is (= 3 (:lives after))))
    (testing "the combo reaches 1600"
      (let [three (-> s (on-pac "inky") (on-pac "clyde") pm/collide)]
        (is (= 3000 (:score three)))))))

(deftest a-ghost-collision-costs-a-life
  (let [on-pac (fn [s] (let [{:keys [x y]} (:pac s)]
                         (update-in s [:ghosts 0] assoc :x x :y y)))
        hit (pm/collide (on-pac start))]
    (is (= 2 (:lives hit)))
    (is (= "CAUGHT!" (:message hit)))
    (is (= 1.2 (:message-timer hit)))
    (testing "Pac-Man and the ghosts go back to their starts"
      (is (= (:pac start) (:pac hit)))
      (is (= (:ghosts start) (:ghosts hit))))
    (testing "the last life ends the game"
      (let [over (pm/collide (on-pac (assoc start :lives 1)))]
        (is (:over? over))
        (is (= 0 (:lives over)))
        (is (= "GAME OVER" (:message over)))))
    (testing "a ghost further than 0.75 tiles away (Manhattan) is harmless"
      (let [near (update-in start [:ghosts 0] assoc :x 6.0 :y (get-in start [:pac :y]))]
        (is (= 3 (:lives (pm/collide (update-in near [:ghosts 0] assoc :x 8.0)))))
        (is (= 3 (:lives (pm/collide (-> start (update-in [:ghosts 0] assoc
                                                          :x (+ (get-in start [:pac :x]) 0.8)
                                                          :y (get-in start [:pac :y])))))))))))

(deftest a-touch-held-through-game-over-does-not-restart
  (let [dying (-> start
                  (assoc :lives 1)
                  (update-in [:ghosts 0] assoc :home-timer 0.0
                             :x (get-in start [:pac :x]) :y (get-in start [:pac :y])))
        field [600 1500]]
    (testing "a finger already down when the last life goes is forgotten, so its lift is no tap"
      (let [down (-> start (adv :press field) (assoc :lives 1 :ghosts (:ghosts dying)))
            over (adv down :down field 0.016)
            lifted (adv over :release field)]
        (is (:over? over))
        (is (= gesture/idle (:gesture over)))
        (is (:over? lifted))))
    (testing "but a press landing on that very frame still counts, so its tap restarts"
      (let [over (adv dying :press field 0.016)
            lifted (adv over :release field)]
        (is (:over? over))
        (is (not (:over? lifted)))
        (is (= 3 (:lives lifted)))))
    (testing "a tap in the Back region does not restart"
      (let [over (adv dying :idle nil 0.016)]
        (is (:over? (tap over [100 60])))
        (is (not (:over? (tap over field))))))
    (testing "a swipe after game over is not a restart"
      (is (:over? (swipe (adv dying :idle nil 0.016) :up))))))

(deftest the-maze-fits-below-back-with-square-tiles
  (doseq [screen screens
          :let [d (pm/dimensions {:screen screen})
                [w h] screen
                {:keys [cell ox oy]} d]]
    (testing (str screen)
      (is (= 19 mz/width))
      (is (= 21 mz/height))
      (is (pos? cell))
      (testing "one number is both the tile's width and height"
        (is (number? cell)))
      (testing "the maze is inside the screen with half a tile to spare at each side, so a wrapped sprite stays on screen"
        (is (>= ox (- (* 0.5 cell) 1e-6)))
        (is (<= (+ ox (* mz/width cell)) (+ (- w (* 0.5 cell)) 1e-6)))
        (is (<= (+ oy (* mz/height cell)) (+ h 1e-6))))
      (testing "and below Back and the HUD row"
        (is (>= oy back-bottom))
        (is (>= oy (+ (:y (:score d)) (:size (:score d)))))))))

(deftest tiles-abut-without-seams
  (doseq [screen screens
          :let [d (pm/dimensions {:screen screen})]
          gy [0 10 20]
          gx (range (dec mz/width))
          :let [[x _ w _] (pm/tile-rect d gx gy)
                [x2 _ _ _] (pm/tile-rect d (inc gx) gy)]]
    (is (= (+ x w) x2)))
  (let [d (pm/dimensions m)
        [_ y _ h] (pm/tile-rect d 3 4)
        [_ y2 _ _] (pm/tile-rect d 3 5)]
    (is (= (+ y h) y2))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [dm (pm/dimensions {:screen screen})
                [w h] screen
                fits (fn [x y size line]
                       (and (>= x 0) (>= y back-bottom) (> size 0)
                            (<= (+ x (measure line size)) w)
                            (<= (+ y size) h)))
                {:keys [score level msg msg2 lives]} dm
                big-score (pm/score-line 999999)
                big-level (pm/level-line 99)]]
    (testing (str screen)
      (is (fits (:x score) (:y score) (:size score) big-score))
      (is (fits (:x level) (:y level) (:size level) big-level))
      (testing "the score, level and lives sit in a row without overlapping"
        (is (<= (+ (:x score) (measure big-score (:size score))) (:x level)))
        (is (<= (+ (:x level) (measure big-level (:size level))) (- (:x0 lives) (:r lives)))))
      (testing "the lives fit"
        (is (<= (+ (:x0 lives) (* 2 (:step lives)) (:r lives)) w))
        (is (>= (- (:y lives) (:r lives)) back-bottom)))
      (doseq [line [pm/caught-line pm/cleared-line pm/over-line]]
        (is (fits (pm/centred-x dm (:size msg) line measure) (:y msg) (:size msg) line)))
      (is (fits (pm/centred-x dm (:size msg2) pm/restart-line measure) (:y msg2) (:size msg2) pm/restart-line))
      (is (<= (+ (:y msg) (:size msg)) (:y msg2))))))

(deftest a-rotation-keeps-the-game
  (let [s (run (swipe start :up) 0.05 10)
        turned (pm/advance s {:metrics {:screen [2334 1206]}
                              :delta-seconds 0.0
                              :pointer {:phase :idle}})]
    (is (= (:pac s) (:pac turned)))
    (is (= (:dots s) (:dots turned)))))

(deftest the-frame-time-is-clamped-at-both-ends
  (let [moved (fn [dt] (let [s (adv start :idle nil dt)] (get-in s [:pac :x])))
        x0 (get-in start [:pac :x])]
    (is (= x0 (moved 0.0)))
    (is (= x0 (moved -1.0)))
    (is (= x0 (moved nil)))
    (testing "a stalled frame moves Pac-Man no further than 0.05 s would"
      (is (near? (moved 5.0) (moved 0.05))))
    (is (< (moved 0.05) x0))))

(deftest pac-man-faces-his-heading-and-the-mouth-is-a-triangle-fan
  (let [fan (pm/pac-fan 100.0 200.0 10.0 1 0 (/ Math/PI 2) 8)]
    (testing "mouth open: sin = 1 gives the widest wedge, so the fan is a partial disc"
      (is (= 8 (count fan)))
      (is (every? #(= 6 (count %)) fan))
      (testing "every triangle starts at the centre and its rim points lie on the radius"
        (doseq [[cx cy x1 y1 x2 y2] fan]
          (is (= [100.0 200.0] [cx cy]))
          (is (near? 10.0 (Math/sqrt (+ (* (- x1 100.0) (- x1 100.0)) (* (- y1 200.0) (- y1 200.0))))))
          (is (near? 10.0 (Math/sqrt (+ (* (- x2 100.0) (- x2 100.0)) (* (- y2 200.0) (- y2 200.0))))))))
      (testing "nothing is drawn straight ahead, where the mouth is"
        (is (not-any? (fn [[_ _ x1 y1]] (and (> x1 109.0) (< (Math/abs (- y1 200.0)) 0.5))) fan))
        (is (some (fn [[_ _ x1 _]] (< x1 91.0)) fan))))
    (testing "mouth shut: sin = -1 is a full disc"
      (let [shut (pm/pac-fan 0.0 0.0 10.0 1 0 (* -0.5 Math/PI) 8)
            ys (mapcat (fn [[_ _ _ y1 _ y2]] [y1 y2]) shut)]
        (is (some #(> % 9.0) ys))
        (is (some #(< % -9.0) ys))))))

(deftest the-draw-reads-the-state
  (let [[s fx] ((:update (pm/scene)) start {:metrics m})]
    (is (= [] fx))
    (is (= s (first ((:draw (pm/scene)) s {}))))))

(deftest the-scene-has-its-id
  (let [sc (pm/scene)]
    (is (= :pacman (:id sc)))
    (is (= "Pac-Man" (:title sc)))))

(deftest the-initial-world-is-the-originals
  (is (= [9.5 15.5] [(get-in start [:pac :x]) (get-in start [:pac :y])]))
  (is (= 3 (:lives start)))
  (is (= 1 (:level start)))
  (is (= 0 (:score start)))
  (testing "the original's 21 rows by 19 columns, 4 power pellets, and the dots"
    (is (= 4 (count (filter #(= \o (mz/tile-at (first %) (second %))) (:dots start)))))
    (is (= 0 (count (filter #(contains? #{\# \- \G \P} (mz/tile-at (first %) (second %))) (:dots start))))))
  (testing "Blinky starts outside the door, the others wait in the house at 1.5, 3.5 and 5.5 s"
    (is (= [0.0 1.5 3.5 5.5] (mapv :home-timer (:ghosts start))))
    (is (= ["blinky" "pinky" "inky" "clyde"] (mapv :name (:ghosts start))))))

(deftest ghost-feet-lie-inside-the-body
  (doseq [r [4 11 23.5]
          :let [cx 100.0
                xs (pm/foot-xs cx r)]]
    (is (= 3 (count xs)))
    (is (every? #(<= (- cx r) % (+ cx r)) xs) "every foot centre is within the body")
    (is (< (abs (- (- (second xs) (first xs)) (- (nth xs 2) (second xs)))) 1e-9) "evenly spaced")
    (is (< (abs (- (+ (first xs) (nth xs 2)) (* 2 cx))) 1e-9) "and symmetric about the centre")))
