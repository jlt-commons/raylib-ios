(ns raylib.scenes.pong-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.pong :as p]))

(def m {:screen [1206 2334]})
(def landscape {:screen [2334 1206]})
(def d (p/dimensions m))
(def dl (p/dimensions landscape))
(def start (first ((:init (p/scene)) {:metrics m})))

(defn- step
  ([state phase position] (step state m phase position))
  ([state metrics phase position]
   (p/advance state {:metrics metrics
                     :pointer {:phase phase
                               :position position}})))

(defn- idle [state] (step state :idle nil))

(defn- frames [state n] (nth (iterate idle state) n))

(defn- tap [state pos]
  (-> state (step :press pos) (step :release pos)))

(defn- with-ball
  "`state` with the ball replaced. Court coordinates: `bx` along the long axis."
  [state bx by bvx bvy]
  (assoc state :bx bx :by by :bvx bvx :bvy bvy))

(defn- near? [a b] (< (Math/abs (double (- a b))) 1e-6))

(deftest the-player-paddle-follows-a-finger
  (let [ph (:ph d)
        c (:cross d)]
    (testing "portrait: the paddle's centre sits under the finger's x"
      (is (near? 600 (+ (:ly (step start :press [600 1800])) (/ ph 2)))))
    (testing "it follows a drag"
      (is (near? 900 (+ (:ly (-> start (step :press [600 1800]) (step :down [900 1900]))) (/ ph 2)))))
    (testing "it stops at both walls"
      (is (near? 0 (:ly (step start :press [5 1800]))))
      (is (near? (- c ph) (:ly (step start :press [(- c 5) 1800])))))
    (testing "landscape: the paddle follows the finger's y, measured from the court's top"
      (let [st (first ((:init (p/scene)) {:metrics landscape}))
            after (step st landscape :press [1000 (+ (:top dl) 500)])]
        (is (near? 500 (+ (:ly after) (/ (:ph dl) 2))))))))

(deftest the-paddle-holds-when-the-finger-lifts
  (let [dragged (-> start (step :press [300 1800]) (step :down [310 1800]))
        after (idle dragged)]
    (is (= (:ly dragged) (:ly after)))
    (testing "a nil position never sends it to the corner"
      (is (pos? (:ly after))))
    (testing "and a release does not move it either"
      (is (= (:ly dragged) (:ly (step dragged :release [1100 100])))))))

(deftest the-ball-bounces-off-the-side-walls
  (let [bs (:bs d)
        c (:cross d)]
    (testing "the near wall"
      (let [after (idle (with-ball start 600.0 1.0 0.0 -9.0))]
        (is (= 0.0 (:by after)))
        (is (pos? (:bvy after)))))
    (testing "the far wall"
      (let [after (idle (with-ball start 600.0 (- c bs 1.0) 0.0 9.0))]
        (is (= (- c bs) (:by after)))
        (is (neg? (:bvy after)))))))

(deftest a-paddle-hit-returns-the-ball-with-english
  (let [{:keys [left-x pw ph right-x bs]} d
        ly 400.0
        centre (+ ly (/ ph 2))
        hit (fn [offset] (-> (with-ball (assoc start :ly ly :ry 0.0)
                               (+ left-x pw 1.0) (- (+ centre offset) (/ bs 2)) -12.0 0.0)
                             idle))]
    (testing "the ball turns back toward the CPU and sits on the paddle's face"
      (let [after (hit 0)]
        (is (= 12.0 (:bvx after)))
        (is (= (+ left-x pw) (:bx after)))))
    (testing "the centre adds no cross speed"
      (is (near? 0 (:bvy (hit 0)))))
    (testing "0.08 per pixel from the centre, either way"
      (is (near? 4.0 (:bvy (hit 50))))
      (is (near? -4.0 (:bvy (hit -50)))))
    (testing "a ball past the paddle's end is not returned"
      (is (neg? (:bvx (hit (+ (/ ph 2) 40))))))
    (testing "the CPU's paddle returns it the other way"
      (let [after (idle (with-ball (assoc start :ry 400.0) (- right-x bs 1.0)
                          (+ 400.0 (/ ph 2) -6.0) 12.0 0.0))]
        (is (= -12.0 (:bvx after)))
        (is (= (- right-x bs) (:bx after)))))
    (testing "the ball does not speed up"
      (is (= 12.0 (Math/abs (double (:bvx (hit 30)))))))))

(deftest a-miss-scores-for-the-other-side
  (let [{:keys [left-x pw long]} d
        far-from-paddle (-> start (assoc :ly 0.0) (with-ball (+ left-x pw 1.0) 900.0 -10.0 0.0))
        past (first (filter #(pos? (:rs %)) (iterate idle far-from-paddle)))]
    (testing "the CPU scores when the ball gets past the player"
      (is (= 1 (:rs past)))
      (is (= 0 (:ls past))))
    (testing "and the next serve is at the centre, toward the player"
      (is (= (* 0.5 long) (:bx past)))
      (is (neg? (:bvx past))))
    (testing "the player scores when the ball gets past the CPU"
      (let [after (idle (with-ball start (- long 1.0) 900.0 10.0 0.0))]
        (is (= 1 (:ls after)))
        (is (= 0 (:rs after)))
        (is (pos? (:bvx after)))))))

(deftest seven-wins-and-a-tap-restarts
  (let [long (:long d)
        won (idle (with-ball (assoc start :ls 6) (- long 1.0) 900.0 10.0 0.0))
        lost (idle (with-ball (assoc start :rs 6) 1.0 900.0 -10.0 0.0))]
    (is (= 7 p/win-score))
    (testing "seven for the player ends the game"
      (is (:over? won))
      (is (= 7 (:ls won)))
      (is (= :player (:winner won))))
    (testing "seven for the CPU ends it the other way"
      (is (:over? lost))
      (is (= :cpu (:winner lost))))
    (testing "six does not"
      (is (not (:over? (idle (with-ball (assoc start :ls 5) (- long 1.0) 900.0 10.0 0.0))))))
    (testing "the game then stops moving"
      (is (= won (frames won 20))))
    (testing "a tap outside Back restarts it, and the paddle stays under the finger"
      (let [after (tap won [600 1400])]
        (is (not (:over? after)))
        (is (= [0 0] [(:ls after) (:rs after)]))
        (is (nil? (:winner after)))
        (is (near? 600 (+ (:ly after) (/ (:ph d) 2))))))
    (testing "a press alone does not restart it"
      (is (:over? (step won :press [600 1400]))))
    (testing "a tap while playing does nothing to the score"
      (let [playing (assoc start :ls 3)]
        (is (= 3 (:ls (tap playing [600 1400]))))))
    (testing "a tap under Back does not restart it"
      (is (:over? (tap won [100 60])))
      (is (:over? (tap won [399 119])))
      (is (not (:over? (tap won [400 119]))))
      (is (not (:over? (tap won [100 120])))))))

(deftest the-cpu-tracks-the-ball
  (let [{:keys [aispeed ph cross]} d
        still (fn [by ry] (with-ball (assoc start :ry ry) 900.0 by 0.0 0.0))]
    (testing "it moves at AISPEED toward a ball it is far from"
      (is (near? (+ 600.0 aispeed) (:ry (idle (still 900.0 600.0)))))
      (is (near? (- 600.0 aispeed) (:ry (idle (still 100.0 600.0))))))
    (testing "scaled from the original's 4.5 by the cross axis"
      (is (near? (* 4.5 (/ (:cross d) 450.0)) aispeed)))
    (testing "it lands exactly on a ball it is close to"
      (is (near? (- 600.0 (/ ph 2)) (:ry (idle (still 600.0 (- 600.0 (/ ph 2) 1.0)))))))
    (testing "it stays inside the court"
      (is (near? 0.0 (:ry (idle (still 0.0 0.0)))))
      (is (near? (- cross ph) (:ry (idle (still (- cross 1.0) (- cross ph)))))))))

(deftest the-ball-crosses-the-court-like-the-originals
  (testing "every serve, seen across many points, stays in the scaled range"
    (let [sl (/ (:long d) 800.0)
          sc (/ (:cross d) 450.0)
          serves (->> (iterate (fn [s] (idle (with-ball s (- (:long d) 1.0) 900.0 10.0 0.0)))
                               (assoc start :ls -100))
                      (take 60))]
      (doseq [s serves]
        (is (<= (* 4.0 sl) (Math/abs (double (:bvx s))) (* 6.0 sl)))
        (is (<= (Math/abs (double (:bvy s))) (* 3.0 sc)))))))

(deftest the-court-starts-below-back
  (let [[bx by bw bh] gesture/back-region
        overlaps? (fn [[x y w h]]
                    (and (< x (+ bx bw)) (< bx (+ x w))
                         (< y (+ by bh)) (< by (+ y h))))]
    (doseq [screen [[1206 2334] [2334 1206]]
            :let [dm (p/dimensions {:screen screen})
                  c (:cross dm)
                  ph (:ph dm)]]
      (testing (str screen)
        (is (>= (:top dm) (+ by bh)))
        (doseq [cc [0.0 (- c ph)]
                side [:player :cpu]]
          (is (not (overlaps? (p/paddle-rect dm side cc))) (str side " paddle at " cc)))
        (doseq [bxx [0.0 (* 0.5 (:long dm)) (- (:long dm) (:bs dm))]
                byy [0.0 (- c (:bs dm))]]
          (is (not (overlaps? (p/ball-rect dm {:bx bxx
                                               :by byy})))))
        (doseq [r (p/centre-dashes dm)]
          (is (not (overlaps? r))))
        (testing "no text starts above the court"
          (doseq [k [:cpu-score-y :you-score-y :cpu-hint-y :you-hint-y :msg-y]]
            (is (>= (get dm k) (+ by bh)) (str k))))))))

(deftest the-court-is-turned-for-a-tall-phone
  (testing "portrait: the player is at the bottom, the CPU at the top"
    (let [[_ py _ _] (p/paddle-rect d :player 0.0)
          [_ cy _ _] (p/paddle-rect d :cpu 0.0)]
      (is (> py cy))
      (is (> py (* 0.5 (:h d))))
      (is (< cy (* 0.5 (:h d))))))
  (testing "the cross coordinate is the screen's x"
    (let [[x] (p/paddle-rect d :player 321.0)]
      (is (= 321.0 (double x)))))
  (testing "landscape keeps the original's left and right"
    (let [[px py] (p/paddle-rect dl :player 100.0)
          [cx] (p/paddle-rect dl :cpu 100.0)]
      (is (< px cx))
      (is (= (+ (:top dl) 100.0) (double py))))))

(deftest everything-drawn-fits-the-screen
  (doseq [screen [[1206 2334] [2334 1206] [800 450] [450 800]]
          :let [[w h] screen
                dm (p/dimensions {:screen screen})
                inside? (fn [[x y rw rh]]
                          (and (<= 0 x) (<= 0 y) (<= (+ x rw) (+ w 1e-6)) (<= (+ y rh) (+ h 1e-6))))]]
    (testing (str screen)
      (doseq [cc [0.0 (- (:cross dm) (:ph dm))]
              side [:player :cpu]]
        (is (inside? (p/paddle-rect dm side cc))))
      (doseq [bxx [0.0 (* 0.5 (:long dm)) (:long dm) (- (:long dm) 1.0)]
              byy [0.0 (- (:cross dm) (:bs dm))]]
        (is (inside? (p/ball-rect dm {:bx bxx
                                      :by byy}))))
      (doseq [r (p/centre-dashes dm)]
        (is (inside? r))))))

(deftest rotation-starts-a-new-game
  (let [played (assoc start :ls 4 :rs 2 :ly 700.0 :over? true :winner :player)
        after (step played landscape :idle nil)
        fresh (first ((:init (p/scene)) {:metrics landscape}))]
    (is (= [2334 1206] (:screen after)))
    (is (= [0 0] [(:ls after) (:rs after)]))
    (is (not (:over? after)))
    (is (= (:ly fresh) (:ly after)))
    (is (= (* 0.5 (:long dl)) (:bx after)))
    (testing "and the first frame after init does not reset"
      (let [playing (assoc start :ls 3)
            after (idle playing)]
        (is (= 3 (:ls after)))
        (is (= [1206 2334] (:screen after)))
        (is (near? (+ (:bx playing) (:bvx playing)) (:bx after)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen [[1206 2334] [2334 1206] [800 450] [450 800]]
          :let [[w h] screen
                dm (p/dimensions {:screen screen})
                {:keys [score-size hint-size msg-size msg-y]} dm
                ;; estimate: 0.6 of the size per character, for raylib's default font.
                fits (fn [x y size line]
                       (and (<= 0 x) (<= (+ x (* 0.6 size (count line))) w)
                            (<= 0 y) (<= (+ y size) h)))]]
    (testing (str screen)
      (is (fits (:cpu-score-x dm) (:cpu-score-y dm) score-size "7"))
      (is (fits (:you-score-x dm) (:you-score-y dm) score-size "7"))
      (is (fits (:you-hint-x dm) (:you-hint-y dm) hint-size p/you-line))
      (is (fits (:cpu-hint-x dm) (:cpu-hint-y dm) hint-size p/cpu-line))
      (doseq [line [p/player-wins-line p/cpu-wins-line]]
        (is (fits (p/msg-x dm line) msg-y msg-size line) line)))))

(deftest the-gesture-is-kept-in-state
  (is (= gesture/idle (:gesture start)))
  (is (= [600 1800] (:start (:gesture (step start :press [600 1800])))))
  (testing "a drag that ends as a swipe moves nothing a second time"
    (let [dragged (-> start (step :press [300 1800]) (step :down [900 1800]))
          released (step dragged :release [900 1800])]
      (is (= (:ly dragged) (:ly released))))))

(deftest a-touch-held-through-game-over-does-not-restart
  (let [long (:long d)
        ending (with-ball (assoc start :ls 6) (- long 1.0) 900.0 10.0 0.0)
        ;; The press lands on the very frame the seventh point is scored.
        over (step ending :press [600 1400])]
    (is (:over? over))
    (testing "still down, then lifted: the short tap is not a restart"
      (let [held (step over :down [600 1400])
            lifted (step held :release [600 1400])]
        (is (:over? held))
        (is (:over? lifted))
        (is (= 7 (:ls lifted)))))))

(deftest a-fresh-tap-after-game-over-restarts
  (let [long (:long d)
        over (-> (with-ball (assoc start :ls 6) (- long 1.0) 900.0 10.0 0.0)
                 (step :press [600 1400])
                 (step :release [600 1400]))
        after (tap over [600 1400])]
    (is (:over? over))
    (is (not (:over? after)))
    (is (= [0 0] [(:ls after) (:rs after)]))))
