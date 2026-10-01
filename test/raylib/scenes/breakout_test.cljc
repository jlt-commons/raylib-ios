(ns raylib.scenes.breakout-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.breakout :as b]))

(def m {:screen [1206 2334]})
(def d (b/dimensions m))
(def start (first ((:init (b/scene)) {:metrics m})))

(defn- step [state phase position]
  (b/advance state {:metrics m
                    :pointer {:phase phase
                              :position position}}))

(defn- idle [state] (step state :idle nil))

(defn- with-ball
  "`start` with the ball replaced, keeping every brick."
  [x y vx vy]
  (assoc start :ball {:x x
                      :y y
                      :vx vx
                      :vy vy}))

(defn- brick-centre
  "The centre of the brick in column `c`, row `r`."
  [c r]
  [(* (+ c 0.5) (:brick-w d))
   (+ (:top d) (* (+ r 0.5) (:brick-h d)))])

(deftest the-paddle-follows-a-finger-and-clamps
  (let [pw (:paddle-w d)
        w (:w d)]
    (testing "the paddle's centre sits under the finger"
      (is (= 600.0 (+ (:paddle-x (step start :press [600 1500])) (/ pw 2)))))
    (testing "it follows a drag"
      (is (= 900.0 (+ (:paddle-x (-> start (step :press [600 1500]) (step :down [900 1800]))) (/ pw 2)))))
    (testing "it stops at the left wall"
      (is (= 0.0 (:paddle-x (step start :press [5 1500])))))
    (testing "and at the right wall"
      (is (= (- w pw) (:paddle-x (step start :press [(- w 5) 1500])))))))

(deftest the-paddle-holds-with-no-finger
  (let [dragged (-> start (step :press [300 1500]) (step :down [310 1500]))
        after (idle dragged)]
    (is (= (:paddle-x dragged) (:paddle-x after)))
    (testing "a nil position never sends it to the corner"
      (is (pos? (:paddle-x after))))))

(deftest a-release-does-not-move-the-paddle
  (let [dragged (-> start (step :press [300 1500]) (step :down [310 1500]))
        released (step dragged :release [1100 100])]
    (is (= (:paddle-x dragged) (:paddle-x released)))))

(deftest the-ball-bounces-off-walls
  (let [r (:ball-r d)]
    (testing "left wall"
      (let [{:keys [x vx]} (:ball (step (with-ball (+ r 1.0) 1500.0 -4.5 -4.5) :idle nil))]
        (is (= r x))
        (is (pos? vx))))
    (testing "right wall"
      (let [{:keys [x vx]} (:ball (idle (with-ball (- (:w d) r 1.0) 1500.0 4.5 -4.5)))]
        (is (= (- (:w d) r) x))
        (is (neg? vx))))
    (testing "ceiling, which is above the bricks"
      (let [{:keys [y vy]} (:ball (idle (with-ball 600.0 (+ r 1.0) 4.5 -4.5)))]
        (is (= r y))
        (is (pos? vy))))))

(deftest the-paddle-reflects-the-ball-upward
  (let [pw (:paddle-w d)
        py (:paddle-y d)
        r (:ball-r d)
        px 500.0
        on (fn [bx] (-> (with-ball bx (- py r 1.0) 0.0 4.5)
                        (assoc :paddle-x px)
                        idle
                        :ball))]
    (testing "a ball coming down turns up"
      (is (neg? (:vy (on (+ px (/ pw 2)))))))
    (testing "the centre adds no sideways speed"
      (is (< (Math/abs (double (:vx (on (+ px (/ pw 2)))))) 1e-9)))
    (testing "the right end pushes the ball right, the left end left"
      (is (pos? (:vx (on (+ px pw -1)))))
      (is (neg? (:vx (on (+ px 1))))))
    (testing "the push is 0.08 of the offset from the paddle's centre"
      (is (< (Math/abs (- (:vx (on (+ px (/ pw 2) 50))) (* 0.08 50))) 1e-9)))
    (testing "a ball already rising is not pulled back down"
      (is (neg? (:vy (-> (with-ball (+ px 10) (- py r 1.0) 0.0 -4.5)
                         (assoc :paddle-x px)
                         idle
                         :ball)))))
    (testing "a ball beside the paddle misses it"
      (is (pos? (:vy (-> (with-ball (+ px pw 40) (- py r 1.0) 0.0 4.5)
                         (assoc :paddle-x px)
                         idle
                         :ball)))))))

(deftest hitting-a-brick-removes-it-and-flips-vy
  (let [[bx by] (brick-centre 3 5)
        ;; Just under the brick, rising into it.
        s (with-ball bx (+ by (/ (:brick-h d) 2) 1.0) 0.0 -4.5)
        after (idle s)]
    (is (= (dec (count (:bricks s))) (count (:bricks after))))
    (is (not (contains? (:bricks after) [3 5])))
    (is (pos? (:vy (:ball after))))
    (testing "empty space above the bricks removes nothing"
      (let [free (idle (with-ball 600.0 1500.0 0.0 -4.5))]
        (is (= (count (:bricks start)) (count (:bricks free))))
        (is (neg? (:vy (:ball free))))))))

(deftest losing-the-ball-costs-a-life
  (let [s (with-ball 600.0 (+ (:h d) 30.0) 0.0 4.5)
        after (idle s)]
    (is (= 2 (:lives after)))
    (is (not (:over? after)))
    (testing "a fresh ball starts at the middle, rising"
      (is (= (* 0.5 (:w d)) (:x (:ball after))))
      (is (neg? (:vy (:ball after)))))
    (testing "the bricks stay as they were"
      (is (= (:bricks s) (:bricks after))))))

(deftest three-losses-end-the-game
  (let [lose (fn [s] (idle (assoc s :ball {:x 600.0
                                           :y (+ (:h d) 30.0)
                                           :vx 0.0
                                           :vy 4.5})))
        s3 (lose (lose (lose start)))]
    (is (= 0 (:lives s3)))
    (is (:over? s3))
    (testing "and the game then stops moving"
      (is (= s3 (idle s3))))))

(deftest clearing-every-brick-wins
  (let [[bx by] (brick-centre 0 0)
        last-one (-> (with-ball bx (+ by (/ (:brick-h d) 2) 1.0) 0.0 -4.5)
                     (assoc :bricks #{[0 0]}))
        won (idle last-one)]
    (is (:won? won))
    (is (not (:over? won)))
    (testing "the win holds on later frames"
      (is (:won? (idle won))))))

(deftest a-press-restarts-after-game-over
  (let [over (assoc start :over? true :lives 0 :bricks #{[1 1]})
        won (assoc start :won? true :bricks #{})
        anywhere [600 1400]]
    (testing "a press anywhere clear of Back starts a new game"
      (doseq [s [over won]
              :let [after (step s :press anywhere)]]
        (is (not (:over? after)))
        (is (not (:won? after)))
        (is (= 3 (:lives after)))
        (is (= (count (:bricks start)) (count (:bricks after))))))
    (testing "holding, releasing or no finger does not restart"
      (is (:over? (step over :down anywhere)))
      (is (:over? (step over :release anywhere)))
      (is (:over? (idle over)))
      (is (:won? (step won :release anywhere))))
    (testing "a press on the Back region does not restart"
      (is (:over? (step over :press [100 60])))
      (is (:won? (step won :press [399 119]))))))

(deftest ball-speed-cannot-tunnel-through-a-brick-row
  (testing "per-frame vertical speed stays well under a brick's height"
    ;; The original steps 3 px a frame against 24-px bricks, so 0.125. Both
    ;; scale with the screen, and the tall phone's bricks scale by height.
    (let [vy (Math/abs (double (:vy (:ball start))))]
      (is (< vy (:brick-h d)))
      (is (< (/ vy (:brick-h d)) 0.125)))))

(deftest geometry-stays-on-screen
  (doseq [screen [[1206 2334] [2334 1206] [800 450]]
          :let [dm (b/dimensions {:screen screen})
                [w h] screen
                {:keys [brick-w brick-h top cols rows paddle-w paddle-h paddle-y ball-r]} dm]]
    (testing (str screen)
      (is (== w (* cols brick-w)))
      (is (<= 0 top))
      (is (<= (+ top (* rows brick-h)) h))
      (is (<= (+ paddle-y paddle-h) h))
      (is (< (+ top (* rows brick-h)) paddle-y))
      (is (<= paddle-w w))
      (is (< ball-r (/ paddle-w 2)))
      (testing "the start ball is between the bricks and the paddle"
        (let [{:keys [x y]} (:ball (first ((:init (b/scene)) {:metrics {:screen screen}})))]
          (is (<= ball-r x (- w ball-r)))
          (is (< (+ top (* rows brick-h)) y paddle-y))))
      (testing "every brick rect is inside the screen"
        (doseq [[c r] (b/all-bricks cols rows)
                :let [[x y bw bh] (b/brick-rect dm c r)]]
          (is (<= 0 x))
          (is (<= 0 y))
          (is (<= (+ x bw) w))
          (is (<= (+ y bh) h)))))))

(deftest a-ball-inside-the-screen-hits-a-brick-in-a-column
  (testing "brick-at maps every brick's centre back to itself"
    (doseq [c (range (:cols d)) r (range (:rows d))
            :let [[x y] (brick-centre c r)]]
      (is (= [c r] (b/brick-at d x y)))))
  (testing "above, below and beside the band is no brick"
    (is (nil? (b/brick-at d 600.0 (- (:top d) 1.0))))
    (is (nil? (b/brick-at d 600.0 (+ (:top d) (* (:rows d) (:brick-h d)) 1.0))))))

(deftest text-lines-fit-the-safe-region
  (let [[w h] (:screen m)
        {:keys [lives-x lives-y lives-size msg-x msg-y msg-size]} d
        ;; estimate: 0.6 of the size per character, for raylib's default font.
        lives-w (* 0.6 lives-size (count (b/lives-line 3)))
        msg-w (* 0.6 msg-size (max (count b/over-line) (count b/won-line)))]
    (is (<= 0 lives-y))
    (is (<= (+ lives-y lives-size) h))
    (is (<= (+ lives-x lives-w) w))
    (is (<= 0 msg-y))
    (is (<= (+ msg-y msg-size) h))
    (is (<= (+ msg-x msg-w) w))
    (testing "the lives line is clear of the Back region at the top left"
      (is (>= lives-x 400)))))

(deftest buttons-avoid-the-back-target
  (testing "the scene has no buttons, and a restart ignores the Back region"
    (let [[bx by bw bh] b/back-region]
      (is (= [0 0 400 120] [bx by bw bh])))))
