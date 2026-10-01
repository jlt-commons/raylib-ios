(ns raylib.scenes.asteroids-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.asteroids :as ast]))

(def m {:screen [1206 2334]})
(def landscape {:screen [2334 1206]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (ast/dimensions m))
(def init-state (first ((:init (ast/scene)) {:metrics m})))

(defn- rock
  "A still asteroid of `size` at `[x y]`."
  [x y size]
  {:x (double x)
   :y (double y)
   :vx 0.0
   :vy 0.0
   :size size
   :r ((:radii d) size)
   :angle 0.0
   :spin 0.0
   :verts []})

(def far-rock
  "A still size-1 rock in a corner of the field, so the wave never clears."
  (rock 20 (+ (:ftop d) 20) 1))

(def start
  "A calm game: no invulnerability left to wait out and one far rock."
  (assoc init-state :asteroids [far-rock] :invuln 0 :bullets []))

(defn- centre [id]
  (let [[x y w h] (:rect (first (filter #(= id (:id %)) (:buttons d))))]
    [(+ x (/ w 2)) (+ y (/ h 2))]))

(defn- frame
  "An input frame with a finger at each of `pts`. The first is the primary
  pointer, as on the device."
  ([pts] (frame m pts))
  ([metrics pts]
   (cond-> {:metrics metrics
            :touch-points (vec pts)}
     (seq pts) (assoc :pointer {:phase :down
                                :position (first pts)})
     (empty? pts) (assoc :pointer {:phase :idle
                                   :position nil}))))

(defn- adv [state pts] (ast/advance state (frame pts)))
(defn- idle [state] (adv state []))
(defn- frames [state n] (nth (iterate idle state) n))

(defn- tap [state pos]
  (-> state
      (ast/advance {:metrics m
                    :touch-points [pos]
                    :pointer {:phase :press
                              :position pos}})
      (ast/advance {:metrics m
                    :touch-points []
                    :pointer {:phase :release
                              :position pos}})))

(defn- near? [a b] (< (Math/abs (double (- a b))) 1e-6))

(defn- speed [{:keys [vx vy]}] (Math/sqrt (+ (* vx vx) (* vy vy))))

(deftest thrust-and-rotate-together
  (let [thrust-pt (centre :thrust)
        left-pt (centre :left)
        s1 (adv start [thrust-pt])
        s2 (adv s1 [thrust-pt left-pt])
        a0 (:angle (:ship start))]
    (testing "thrust alone does not turn the ship"
      (is (near? a0 (:angle (:ship s1))))
      (is (pos? (speed (:ship s1)))))
    (testing "a second finger on rotate turns it while thrust keeps pushing"
      (is (near? (- a0 ast/rot) (:angle (:ship s2))))
      (is (> (speed (:ship s2)) (speed (:ship s1)))))
    (testing "the push this frame follows the NEW heading"
      (let [a (:angle (:ship s2))
            expect-vx (* ast/friction (+ (:vx (:ship s1)) (* ast/thrust (:u d) (Math/cos a))))
            expect-vy (* ast/friction (+ (:vy (:ship s1)) (* ast/thrust (:u d) (Math/sin a))))]
        (is (near? expect-vx (:vx (:ship s2))))
        (is (near? expect-vy (:vy (:ship s2))))))
    (testing "the primary pointer is the first finger, and the second still counts"
      (is (= #{:thrust :left} (:held s2)))
      (testing "even when the first finger lifted and only the second is left"
        (let [s3 (adv s2 [left-pt])]
          (is (= #{:left} (:held s3))))))
    (testing "all four at once, with two fingers each on a thumb"
      (is (= #{:left :thrust :fire :right}
             (:held (adv start (mapv centre [:left :thrust :right :fire]))))))))

(deftest each-button-does-its-thing
  (let [a0 (:angle (:ship start))]
    (testing "left turns the ship by minus ROT"
      (is (near? (- a0 ast/rot) (:angle (:ship (adv start [(centre :left)]))))))
    (testing "right turns it by plus ROT"
      (is (near? (+ a0 ast/rot) (:angle (:ship (adv start [(centre :right)]))))))
    (testing "both together cancel, as both keys did"
      (is (near? a0 (:angle (:ship (adv start [(centre :left) (centre :right)]))))))
    (testing "thrust adds THRUST scaled, along the heading (up), then friction"
      (let [ship (:ship (adv start [(centre :thrust)]))]
        (is (near? 0.0 (:vx ship)))
        (is (near? (* ast/friction ast/thrust (:u d) -1.0) (:vy ship)))))
    (testing "no finger, no change, apart from the original's friction"
      (let [ship (:ship (idle start))]
        (is (= (:ship start) ship))
        (is (empty? (:bullets (idle start))))))
    (testing "fire is an edge: one bullet on the press, none while it stays down"
      (let [s1 (adv start [(centre :fire)])
            s2 (adv s1 [(centre :fire)])
            s3 (idle s2)
            s4 (adv s3 [(centre :fire)])]
        (is (= 1 (count (:bullets s1))))
        (is (= 1 (count (:bullets s2))) "held, no second bullet")
        (is (= 2 (count (:bullets s4))) "released and pressed again fires again")
        (is (= (dec ast/bullet-life) (:life (first (:bullets s1)))))))
    (testing "the bullet leaves the nose at BSPEED scaled, plus the ship's velocity"
      (let [b (first (:bullets (adv start [(centre :fire)])))]
        (is (near? 0.0 (:vx b)))
        (is (near? (- (:bullet-speed d)) (:vy b)))))))

(deftest a-touch-outside-the-buttons-does-nothing
  (let [mid [600 1000]
        [lx ly lw _] (:rect (first (:buttons d)))
        [_ _ rw _] (:rect (second (:buttons d)))
        gap-pt [(+ lx lw 1.0) (+ ly 10.0)]
        s (adv start [mid])]
    (is (not (gesture/in-rect? (:rect (first (:buttons d))) gap-pt)))
    (is (pos? rw))
    (is (= #{} (:held s)))
    (is (near? (:angle (:ship start)) (:angle (:ship s))))
    (is (empty? (:bullets s)))
    (testing "the gap between two buttons"
      (is (= #{} (:held (adv start [gap-pt])))))
    (testing "the margin below the buttons"
      (is (= #{} (:held (adv start [[(first (centre :fire)) (- (:h d) 1.0)]])))))
    (testing "above the first button, in the field"
      (is (= #{} (:held (adv start [[lx (- ly 5.0)]])))))))

(deftest buttons-avoid-back
  (let [[bx by bw bh] gesture/back-region
        overlaps? (fn [[x y w h]]
                    (and (< x (+ bx bw)) (< bx (+ x w))
                         (< y (+ by bh)) (< by (+ y h))))]
    (doseq [screen screens
            :let [dm (ast/dimensions {:screen screen})
                  [w h] screen]]
      (testing (str screen)
        (is (= 4 (count (:buttons dm))))
        (is (>= (:ftop dm) (+ by bh)) "the field starts below Back")
        (is (pos? (:fh dm)))
        (doseq [{:keys [id rect]} (:buttons dm)
                :let [[x y rw rh] rect]]
          (is (not (overlaps? rect)) (str id))
          (is (and (<= 0 x) (<= (+ x rw) w) (< (+ y rh) h)) (str id " is on screen, clear of the bottom edge"))
          (is (>= y (+ (:ftop dm) (:fh dm))) (str id " is below the field")))
        (testing "no two buttons overlap"
          (doseq [[a b] (partition 2 1 (:buttons dm))
                  :let [[ax _ aw _] (:rect a)
                        [bx' _ _ _] (:rect b)]]
            (is (< (+ ax aw) bx'))))))))

(deftest the-ship-wraps-within-the-field
  (let [{:keys [fx ftop fw fh]} d
        bottom (+ ftop fh)
        btn-top (second (:rect (first (:buttons d))))
        with-ship (fn [x y vx vy]
                    (assoc start :ship {:x x
                                        :y y
                                        :angle 0.0
                                        :vx vx
                                        :vy vy}))]
    (testing "off the right edge it comes back at the left"
      (let [{:keys [x]} (:ship (idle (with-ship (- (+ fx fw) 1.0) 1000.0 10.0 0.0)))]
        (is (< x 20.0))
        (is (>= x fx))))
    (testing "off the left edge it comes back at the right"
      (let [{:keys [x]} (:ship (idle (with-ship 1.0 1000.0 -10.0 0.0)))]
        (is (> x (- (+ fx fw) 20.0)))
        (is (< x (+ fx fw)))))
    (testing "off the top of the field it comes back at the bottom of the field, not the screen"
      (let [{:keys [y]} (:ship (idle (with-ship 600.0 (+ ftop 1.0) 0.0 -10.0)))]
        (is (> y (- bottom 20.0)))
        (is (< y bottom))
        (is (< y btn-top))))
    (testing "off the bottom of the field it comes back under Back, not past the buttons"
      (let [{:keys [y]} (:ship (idle (with-ship 600.0 (- bottom 1.0) 0.0 10.0)))]
        (is (< y (+ ftop 20.0)))
        (is (>= y ftop))))
    (testing "a bullet and an asteroid wrap the same way"
      (let [st (assoc start
                      :bullets [{:x 600.0
                                 :y (+ ftop 1.0)
                                 :vx 0.0
                                 :vy -10.0
                                 :life 30}]
                      :asteroids [(assoc far-rock :x 600.0 :y (- bottom 1.0) :vy 10.0)])
            after (idle st)]
        (is (< (:y (first (:bullets after))) bottom))
        (is (> (:y (first (:bullets after))) (- bottom 20.0)))
        (is (< (:y (first (:asteroids after))) (+ ftop 20.0)))))))

(deftest a-bullet-splits-an-asteroid
  (let [{:keys [ftop]} d
        y (+ ftop 800.0)
        st (assoc start
                  :asteroids [far-rock (rock 600 y 3)]
                  :bullets [{:x 600.0
                             :y y
                             :vx 0.0
                             :vy 0.0
                             :life 30}])
        after (idle st)
        splits (filter #(= 2 (:size %)) (:asteroids after))]
    (is (= 2 (count splits)))
    (is (= 3 (count (:asteroids after))) "the far rock and the two halves")
    (is (every? #(near? ((:radii d) 2) (:r %)) splits))
    (is (every? #(and (near? 600 (:x %)) (near? y (:y %))) splits) "born where it was hit")
    (is (= 20 (:score after)))
    (is (empty? (:bullets after)) "the bullet is spent")
    (testing "a bullet just outside the radius passes by"
      (let [miss (idle (assoc st :bullets [{:x (+ 600.0 (:r (rock 0 0 3)) 1.0)
                                            :y y
                                            :vx 0.0
                                            :vy 0.0
                                            :life 30}]))]
        (is (= 0 (:score miss)))
        (is (= 1 (count (:bullets miss))))))))

(deftest the-smallest-asteroid-disappears
  (let [y (+ (:ftop d) 800.0)
        st (assoc start
                  :asteroids [far-rock (rock 600 y 1)]
                  :bullets [{:x 600.0
                             :y y
                             :vx 0.0
                             :vy 0.0
                             :life 30}])
        after (idle st)]
    (is (= 1 (count (:asteroids after))))
    (is (= 100 (:score after)))
    (is (empty? (:bullets after)))))

(deftest a-bullet-cannot-skip-the-smallest-asteroid
  (testing "a bullet's speed plus the ship's top speed is under the diameter"
    (doseq [screen screens
            :let [dm (ast/dimensions {:screen screen})
                  u (:u dm)
                  ;; The ship's terminal speed: v = friction * (v + thrust * u).
                  top-speed (/ (* ast/friction ast/thrust u) (- 1 ast/friction))
                  fastest (+ (:bullet-speed dm) top-speed)
                  diameter (* 2 ((:radii dm) 1))]]
      (testing (str screen)
        (is (< fastest diameter)))))
  (testing "the numbers, for the report: per-frame speeds at 1206x2334"
    (is (< 17.9 (:bullet-speed d) 18.1))
    (is (< 28.0 ((:radii d) 1) 29.0)))
  (testing "a bullet fired at the ship's top speed still hits a smallest rock"
    (let [{:keys [ftop]} d
          top-speed (/ (* ast/friction ast/thrust (:u d)) (- 1 ast/friction))
          ;; Ship facing right at full speed. The rock sits where the bullet's
          ;; first step lands, so a skip would show as no hit in 3 frames.
          ship {:x 100.0
                :y (+ ftop 800.0)
                :angle 0.0
                :vx top-speed
                :vy 0.0}
          st (assoc start
                    :ship ship
                    :asteroids [far-rock (rock 500 (+ ftop 800.0) 1)]
                    :bullets [{:x 400.0
                               :y (+ ftop 800.0)
                               :vx (+ top-speed (:bullet-speed d))
                               :vy 0.0
                               :life 30}])
          after (frames st 8)]
      (is (= 100 (:score after))))))

(deftest bullets-expire
  (testing "600 frames of a held fire button fire once and the bullet dies"
    (let [run (reductions (fn [s _] (adv s [(centre :fire)])) start (range 600))
          peak (apply max (map (comp count :bullets) run))]
      (is (= 1 peak))
      (is (empty? (:bullets (last run))))
      (is (every? #(pos? (:life %)) (mapcat :bullets run)))))
  (testing "600 frames of mashing the button every other frame stay bounded"
    (let [mash (fn [s i] (if (even? i) (adv s [(centre :fire)]) (idle s)))
          st (assoc start :lives 1000000)
          run (reductions mash st (range 600))
          peak (apply max (map (comp count :bullets) run))]
      (is (<= 20 peak))
      (is (<= peak (inc (quot ast/bullet-life 2))))
      (is (every? #(<= (:life %) ast/bullet-life) (mapcat :bullets run)))
      (is (< (apply max (map (comp count :asteroids) run)) 100)))))

(deftest a-collision-costs-a-life
  (let [{:keys [ship]} start
        hit (assoc start :asteroids [far-rock (rock (:x ship) (:y ship) 3)])
        after (idle hit)]
    (is (= 2 (:lives after)))
    (is (not (:over? after)))
    (is (= ast/invuln-crash (:invuln after)))
    (is (= (:ship (first ((:init (ast/scene)) {:metrics m}))) (:ship after)) "back at the centre, at rest")
    (testing "not while invulnerable"
      (let [safe (idle (assoc hit :invuln 30))]
        (is (= 3 (:lives safe)))
        (is (= 29 (:invuln safe)))))
    (testing "the last life ends the game, and it stays ended"
      (let [dead (idle (assoc hit :lives 1))]
        (is (:over? dead))
        (is (= 0 (:lives dead)))
        (is (= (dissoc dead :gesture :held) (dissoc (frames dead 30) :gesture :held)))))))

(deftest clearing-a-wave-spawns-the-next
  (let [y (+ (:ftop d) 800.0)
        shoot (fn [st score]
                (idle (assoc st
                             :score score
                             :asteroids [(rock 600 y 1)]
                             :bullets [{:x 600.0
                                        :y y
                                        :vx 0.0
                                        :vy 0.0
                                        :life 30}])))
        after (shoot start 0)]
    (testing "four size-3 asteroids, on the field's edge"
      (is (= 4 (count (:asteroids after))))
      (is (every? #(= 3 (:size %)) (:asteroids after)))
      (is (every? (fn [{:keys [x y]}]
                    (and (<= 0 x (:w d))
                         (<= (:ftop d) y (+ (:ftop d) (:fh d)))))
                  (:asteroids after))))
    (testing "one more per 500 points scored before the last shot"
      (is (= 5 (count (:asteroids (shoot start 500)))))
      (is (= 4 (count (:asteroids (shoot start 499))))))))

(deftest a-tap-restarts-after-game-over
  (let [over (assoc start :over? true :lives 0 :score 120)
        playing? (fn [st] (and (not (:over? st)) (= 3 (:lives st)) (= 0 (:score st))))]
    (testing "a tap on the field restarts"
      (is (playing? (tap over [600 1000]))))
    (testing "a press alone does not"
      (is (:over? (ast/advance over {:metrics m
                                     :touch-points [[600 1000]]
                                     :pointer {:phase :press
                                               :position [600 1000]}}))))
    (testing "a tap under Back does not"
      (is (:over? (tap over [100 60])))
      (is (:over? (tap over [399 119])))
      (is (playing? (tap over [400 119])))
      (is (playing? (tap over [100 120]))))
    (testing "a fire press after dying does not restart, nor does any button"
      (doseq [id [:left :right :thrust :fire]]
        (is (:over? (tap over (centre id))) (str id))))
    (testing "a game that is over holds still"
      (is (= (:ship over) (:ship (frames over 5))))
      (is (= (:score over) (:score (adv over [(centre :thrust) (centre :fire)])))))
    (testing "a tap while playing keeps the score"
      (is (= 50 (:score (tap (assoc start :score 50) [600 1000])))))))

(deftest rotation-starts-a-new-game
  (let [played (assoc start :score 90 :lives 1 :over? true
                      :bullets [{:x 5.0
                                 :y 900.0
                                 :vx 0.0
                                 :vy 0.0
                                 :life 9}])
        after (ast/advance played (frame landscape []))
        dl (ast/dimensions landscape)]
    (is (= [2334 1206] (:screen after)))
    (is (= 0 (:score after)))
    (is (= 3 (:lives after)))
    (is (not (:over? after)))
    (is (empty? (:bullets after)))
    (is (= (:x (:ship (first ((:init (ast/scene)) {:metrics landscape}))))
           (:x (:ship after))))
    (is (= (+ (:ftop dl) (/ (:fh dl) 2.0)) (:y (:ship after))))
    (testing "and the first frame after init does not reset"
      (let [playing (assoc init-state :score 30)
            after (idle playing)]
        (is (= 30 (:score after)))
        (is (= [1206 2334] (:screen after)))))))

(deftest a-new-wave-starts-inside-the-field
  (doseq [screen screens
          :let [dm (ast/dimensions {:screen screen})
                st (first ((:init (ast/scene)) {:metrics {:screen screen}}))]]
    (testing (str screen)
      (is (= 4 (count (:asteroids st))))
      (is (every? (fn [{:keys [x y]}]
                    (and (<= 0 x (:w dm))
                         (<= (:ftop dm) y (+ (:ftop dm) (:fh dm)))))
                  (:asteroids st)))
      (is (= 60 (:invuln st))))))

(deftest asteroids-draw-from-the-lcg
  (let [again (first ((:init (ast/scene)) {:metrics m}))]
    (is (= (:asteroids init-state) (:asteroids again)) "the same seed gives the same wave")
    (is (every? #(<= (Math/abs (:vx %)) (* 2.2 (/ 1 3.0) (:u d) 1.0001)) (:asteroids init-state)))
    (testing "the vertices lie between 0.65 and 1.15 of the radius"
      (doseq [a (:asteroids init-state)
              [dx dy] (:verts a)
              :let [r (Math/sqrt (+ (* dx dx) (* dy dy)))]]
        (is (<= (* 0.65 (:r a) 0.9999) r (* 1.15 (:r a) 1.0001)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dm (ast/dimensions {:screen screen})
                {:keys [text-size score-x score-y msg-size msg-y msg2-size msg2-y buttons]} dm
                [_ btn-y _ _] (:rect (first buttons))
                ;; estimate: 0.6 of the size per character, for raylib's default font.
                fits (fn [x y size line]
                       (and (<= 0 x) (<= (+ x (* 0.6 size (count line))) w)
                            (<= 0 y) (<= (+ y size) h)))]]
    (testing (str screen)
      (is (fits score-x score-y text-size (ast/score-line 99999)))
      (is (fits (ast/lives-x dm (ast/lives-line 3)) score-y text-size (ast/lives-line 3)))
      (is (fits (ast/centred-x dm msg-size ast/over-line) msg-y msg-size ast/over-line))
      (is (fits (ast/centred-x dm msg2-size ast/restart-line) msg2-y msg2-size ast/restart-line))
      (is (<= (+ msg2-y msg2-size) btn-y) "the message ends above the buttons")
      (is (>= score-y (+ 0 120)) "the score sits below Back")
      (testing "the score and the lives do not collide"
        (is (<= (+ score-x (* 0.6 text-size (count (ast/score-line 99999))))
                (ast/lives-x dm (ast/lives-line 3)))))
      (doseq [{:keys [label label-x label-y label-size rect]} buttons
              :let [[bx by bw bh] rect]]
        (is (>= label-x bx) label)
        (is (<= (+ label-x (* 0.6 label-size (count label))) (+ bx bw)) label)
        (is (>= label-y by) label)
        (is (<= (+ label-y label-size) (+ by bh)) label)))))

(deftest the-gesture-is-kept-in-state
  (is (= gesture/idle (:gesture start)))
  (is (= [600 1000] (:start (:gesture (ast/advance start {:metrics m
                                                          :touch-points [[600 1000]]
                                                          :pointer {:phase :press
                                                                    :position [600 1000]}}))))))
