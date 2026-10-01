(ns raylib.scenes.survivors-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.survivors :as sv]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (sv/dimensions m))
(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(def init-state (first ((:init (sv/scene)) {:metrics m})))

(def calm
  "A game with no enemies and no spawn for ages."
  (assoc init-state :spawn-cd 1000000 :enemies []))

(def hero-at (let [h (:hero init-state)] [(:x h) (:y h)]))

(defn- adv
  ([state phase pos] (adv state m phase pos))
  ([state metrics phase pos]
   (sv/advance state {:metrics metrics
                      :pointer {:phase phase
                                :position pos}})))

(defn- idle [state] (adv state :idle nil))
(defn- frames [state n] (nth (iterate idle state) n))

(defn- tap [state pos]
  (-> state (adv :press pos) (adv :down pos) (adv :release pos)))

(defn- near? [a b] (< (Math/abs (double (- a b))) 1e-6))

(defn- hero-xy [st] [(:x (:hero st)) (:y (:hero st))])

(defn- dist [[ax ay] [bx by]]
  (Math/sqrt (+ (* (- ax bx) (- ax bx)) (* (- ay by) (- ay by)))))

(def slop (gesture/slop m))
(def below-back [600.0 1200.0])

(defn- enemy [x y] {:x (double x)
                    :y (double y)
                    :hp 2})

(defn- on-hero
  "An enemy sitting where the hero stands, with HP enough to outlast the bullets
  the hero fires at it."
  []
  (assoc (enemy (first hero-at) (second hero-at)) :hp 1000))

(deftest the-stick-moves-the-hero-at-one-speed-in-every-direction
  (let [centre [600.0 1500.0]
        pressed (adv calm :press centre)]
    (testing "the press itself moves nothing"
      (is (= hero-at (hero-xy pressed))))
    (doseq [deg (range 0 360 15)
            reach [(* 3 slop) 400.0]
            :let [a (Math/toRadians deg)
                  finger [(+ (first centre) (* reach (Math/cos a)))
                          (+ (second centre) (* reach (Math/sin a)))]
                  moved (adv pressed :down finger)]]
      (testing (str deg " degrees, " reach " px out")
        (is (near? (:hero-speed d) (dist hero-at (hero-xy moved))) "the same pixel step")
        (is (near? (Math/cos a) (/ (- (first (hero-xy moved)) (first hero-at)) (:hero-speed d))))))
    (testing "keeps going while held, at the same step"
      (let [s2 (adv (adv pressed :down [900.0 1500.0]) :down [900.0 1500.0])]
        (is (near? (* 2 (:hero-speed d)) (- (first (hero-xy s2)) (first hero-at))))))
    (testing "a lifted finger stops the hero"
      (let [moved (adv pressed :down [900.0 1500.0])
            lifted (adv moved :release [900.0 1500.0])]
        (is (= (hero-xy moved) (hero-xy lifted)))
        (is (= (hero-xy moved) (hero-xy (idle lifted))))))))

(deftest stick-dir-has-a-dead-zone-and-a-unit-direction
  (let [st (assoc calm :stick {:centre [500.0 1000.0]
                               :finger [500.0 1000.0]})
        dir (fn [finger phase]
              (sv/stick-dir st {:metrics m
                                :pointer {:phase phase
                                          :position finger}} m))]
    (is (nil? (dir [500.0 1000.0] :down)) "on the centre")
    (is (nil? (dir [(+ 500.0 slop) 1000.0] :down)) "exactly the slop out is still inside")
    (is (nil? (dir [(+ 500.0 (* 0.7 slop)) (+ 1000.0 (* 0.7 slop))] :down)) "a diagonal just inside")
    (is (= [1.0 0.0] (dir [(+ 500.0 slop 1) 1000.0] :down)) "just outside")
    (is (= [0.0 -1.0] (dir [500.0 100.0] :down)) "up is negative y")
    (let [[x y] (dir [900.0 1400.0] :down)]
      (is (near? 1.0 (+ (* x x) (* y y))) "a diagonal is a unit vector")
      (is (near? x y)))
    (is (nil? (dir [900.0 1400.0] :press)) "a press frame has no direction")
    (is (nil? (dir [900.0 1400.0] :release)))
    (is (nil? (dir nil :idle)))
    (is (nil? (sv/stick-dir calm {:metrics m
                                  :pointer {:phase :down
                                            :position [900.0 1400.0]}} m))
        "no centre, no direction")))

(deftest a-tap-does-not-move-the-hero
  (is (= hero-at (hero-xy (tap calm below-back))))
  (testing "a slight drag inside the slop does not either"
    (let [[x y] below-back
          s (-> calm
                (adv :press below-back)
                (adv :down [(+ x (* 0.9 slop)) y])
                (adv :down [x (- y (* 0.9 slop))])
                (adv :release below-back))]
      (is (= hero-at (hero-xy s)))))
  (testing "a press under Back starts no stick"
    (let [s (-> calm (adv :press [100.0 50.0]) (adv :down [700.0 600.0]))]
      (is (nil? (:stick s)))
      (is (= hero-at (hero-xy s)))))
  (testing "a finger that was already down when the scene opened does nothing"
    (is (= hero-at (hero-xy (adv calm :down [900.0 1500.0]))))))

(deftest the-hero-stays-in-the-field
  (let [run (reduce (fn [s _] (adv s :down [1.0 100.0]))
                    (adv calm :press [1100.0 2300.0])
                    (range 600))
        {:keys [x y]} (:hero run)]
    (is (>= x (:hero-r d)))
    (is (<= x (- 1206 (:hero-r d))))
    (is (>= y (+ (:ftop d) (:hero-r d))))
    (is (<= y (- 2334 (:hero-r d))))
    (is (not= hero-at (hero-xy run)) "and it did move")))

(deftest the-hero-fires-at-the-nearest-enemy
  (let [[hx hy] hero-at
        near-e (enemy (+ hx 300) hy)
        far-e (enemy hx (- hy 700))
        after (idle (assoc calm :enemies [far-e near-e]))
        [b] (:bullets after)]
    (is (= 1 (count (:bullets after))))
    (is (near? (:bullet-speed d) (Math/sqrt (+ (* (:vx b) (:vx b)) (* (:vy b) (:vy b))))))
    (is (near? (:bullet-speed d) (:vx b)) "toward the near one, on the right")
    (is (near? 0.0 (:vy b)))
    (is (= (sv/fire-cooldown 1) (:fire-cd after)))
    (testing "the nearest is chosen after the enemies have moved"
      (let [swapped (idle (assoc calm :enemies [(enemy hx (- hy 100)) (enemy (+ hx 99) hy)]))]
        (is (pos? (:vx (first (:bullets swapped)))))))
    (testing "no enemy, no shot"
      (is (empty? (:bullets (frames calm 30)))))
    (testing "it waits out the cooldown"
      (let [two (idle after)]
        (is (= 1 (count (:bullets two))))
        (is (= 19 (:fire-cd two)))))))

(deftest a-kill-drops-a-gem-and-a-gem-gives-xp
  (let [[hx hy] hero-at
        ex (+ hx 500)
        ;; one hit left, and a bullet a few pixels from the enemy
        victim (assoc (enemy ex hy) :hp 1)
        bullet {:x (- ex 20.0)
                :y hy
                :vx 0.0
                :vy 0.0
                :life 50}
        killed (idle (assoc calm :enemies [victim] :bullets [bullet] :fire-cd 50))]
    (is (empty? (:enemies killed)))
    (is (empty? (:bullets killed)) "the bullet is spent")
    (is (= 1 (:kills killed)))
    (is (= 1 (count (:gems killed))))
    (let [[g] (:gems killed)]
      (is (< (Math/abs (- (:x g) ex)) (* 2 (:enemy-speed d))) "where it died")
      (is (= 0 (:xp (:hero killed)))))
    (testing "a hit that does not kill drops nothing"
      (let [hurt (idle (assoc calm :enemies [(enemy ex hy)] :bullets [bullet] :fire-cd 50))]
        (is (= 1 (count (:enemies hurt))))
        (is (= 1 (:hp (first (:enemies hurt)))))
        (is (empty? (:gems hurt)))))
    (testing "a gem inside the pickup radius is taken for 1 xp"
      (let [taken (idle (assoc calm :gems [{:x (+ hx (* 0.9 (:pickup-r d)))
                                            :y hy}]))]
        (is (empty? (:gems taken)))
        (is (= 1 (:xp (:hero taken))))))
    (testing "a gem outside it stays"
      (let [left (idle (assoc calm :gems [{:x (+ hx (* 1.1 (:pickup-r d)))
                                           :y hy}]))]
        (is (= 1 (count (:gems left))))
        (is (= 0 (:xp (:hero left))))))))

(deftest levelling-up-speeds-the-fire-rate
  (is (= [20 18 16 14 12 10 8 6 5 5] (mapv sv/fire-cooldown (range 1 11))))
  (let [[hx hy] hero-at
        gems (vec (repeat 5 {:x hx
                             :y hy}))
        up (idle (assoc calm :gems gems))]
    (is (= 2 (:level (:hero up))))
    (is (= 0 (:xp (:hero up))))
    (is (= 10 (sv/xp-needed 2)))
    (testing "four gems are not enough"
      (let [almost (idle (assoc calm :gems (vec (take 4 gems))))]
        (is (= 1 (:level (:hero almost))))
        (is (= 4 (:xp (:hero almost))))))
    (testing "a shot at level 3 waits 16 frames, one at level 1 waits 20"
      (let [e [(enemy (+ hx 400) hy)]
            shots (fn [level]
                    (let [run (reductions (fn [s _] (idle s))
                                          (assoc-in (assoc calm :enemies e) [:hero :level] level)
                                          (range 100))]
                      (keep-indexed (fn [i s] (when (= (sv/fire-cooldown level) (:fire-cd s)) i)) run)))
            gaps (fn [xs] (map - (rest xs) xs))]
        (is (= #{20} (set (gaps (shots 1)))))
        (is (= #{16} (set (gaps (shots 3)))))))))

(deftest contact-costs-hp-and-zero-ends-the-game
  (let [hit (idle (assoc calm :enemies [(on-hero)]))]
    (is (= (- sv/hero-hp sv/contact-damage) (:hp (:hero hit))))
    (is (= sv/hurt-frames (:hurt-cd (:hero hit))))
    (testing "the grace period holds the damage off for 24 more frames"
      (let [later (frames hit 24)]
        (is (= (- sv/hero-hp sv/contact-damage) (:hp (:hero later))))
        (is (= (- sv/hero-hp (* 2 sv/contact-damage)) (:hp (:hero (idle later)))))))
    (testing "an enemy that is not touching costs nothing"
      (let [[hx hy] hero-at
            away (idle (assoc calm :enemies [(enemy (+ hx 300) hy)]))]
        (is (= sv/hero-hp (:hp (:hero away))))))
    (testing "zero HP ends the game"
      (let [dying (assoc-in (assoc calm :enemies [(on-hero)]) [:hero :hp] 8)
            over (idle dying)]
        (is (= 0 (:hp (:hero over))))
        (is (:over? over))
        (is (= (:time over) (:time (idle over))) "a finished game stands still")))
    (testing "9 HP survives the same hit"
      (is (not (:over? (idle (assoc-in (assoc calm :enemies [(on-hero)]) [:hero :hp] 9))))))))

(def dying (assoc-in (assoc calm :enemies [(on-hero)]) [:hero :hp] 8))

(deftest a-touch-held-through-game-over-does-not-restart
  (let [down (-> (assoc-in dying [:hero :hurt-cd] 5)
                 (adv :press below-back)
                 (adv :down below-back))
        _ (is (not (:over? down)))
        over (adv (assoc-in down [:hero :hurt-cd] 0) :down below-back)]
    (is (:over? over))
    (is (= gesture/idle (:gesture over)) "the touch in flight is dropped")
    (is (nil? (:stick over)))
    (is (:over? (adv over :release below-back)) "its lift is not a tap")
    (is (:over? (idle (adv over :release below-back))))))

(deftest a-press-landing-on-the-game-over-frame-is-not-swallowed
  (let [over (adv dying :press below-back)]
    (is (:over? over))
    (is (= below-back (:start (:gesture over))))
    (is (not (:over? (-> over (adv :down below-back) (adv :release below-back))))
        "an ordinary tap that began on the frame the hero died")))

(deftest a-fresh-tap-restarts
  (let [over (idle dying)
        _ (is (:over? (idle over)))
        again (tap (assoc over :kills 7 :time 900) below-back)]
    (is (not (:over? again)))
    (is (= sv/hero-hp (:hp (:hero again))))
    (is (= 0 (:kills again)))
    (is (= 0 (:time again)))
    (is (= hero-at (hero-xy again)))
    (is (empty? (:enemies again)))
    (testing "a tap under Back does not"
      (is (:over? (tap over [100.0 50.0]))))
    (testing "a swipe is not a tap"
      (is (:over? (-> over
                      (adv :press below-back)
                      (adv :down [900.0 1800.0])
                      (adv :release [900.0 1800.0])))))
    (testing "the new game is not stuck in game over"
      (is (not (:over? (frames again 5)))))))

(deftest enemies-appear-on-the-edge-of-the-field
  (let [before (frames init-state 29)
        after (idle before)]
    (is (empty? (:enemies before)))
    (is (= 2 (count (:enemies after))) "the first wave is two enemies on frame 30")
    (doseq [screen screens
            :let [dm (sv/dimensions {:screen screen})
                  st (first ((:init (sv/scene)) {:metrics {:screen screen}}))
                  run (reductions (fn [s _] (adv s {:screen screen} :idle nil))
                                  (assoc-in st [:hero :hp] 1000000)
                                  (range 400))
                  ;; Enemies are appended, so the new ones are the tail of a longer list.
                  fresh (mapcat (fn [prev s]
                                  (let [n (- (count (:enemies s)) (count (:enemies prev)))]
                                    (when (pos? n) (take-last n (:enemies s)))))
                                run (rest run))]]
      (testing (str screen)
        (is (seq fresh))
        (doseq [{:keys [x y]} fresh
                :let [gap (min (- x (:fx dm)) (- (+ (:fx dm) (:fw dm)) x)
                               (- y (:ftop dm)) (- (+ (:ftop dm) (:fh dm)) y))]]
          (is (>= gap (- (:edge dm) 1e-6)) "inside the field, circle and all")
          (is (<= gap (+ (:edge dm) (:enemy-speed dm) 1e-6)) "within a step of an edge"))))))

(deftest enemies-chase-at-a-fixed-speed
  (let [[hx hy] hero-at
        e0 (enemy (+ hx 600) (- hy 800))
        e (first (:enemies (idle (assoc calm :enemies [e0]))))]
    (is (near? (:enemy-speed d) (dist [(:x e0) (:y e0)] [(:x e) (:y e)])))
    (is (< (dist [(:x e) (:y e)] hero-at) (dist [(:x e0) (:y e0)] hero-at)))))

(deftest enemy-bullet-and-gem-counts-stay-bounded
  (let [frames-run 720
        ;; The original's rules: a wave of 2 + (time / 600) every (50 - time / 120)
        ;; frames, the first after 30. By frame 720 that is at most
        ;; 1 + (720 - 30) / 44 = 16 waves of at most 3. A gem needs a kill and a
        ;; kill needs a spawned enemy, so enemies + gems <= 48. A bullet lives 90
        ;; frames and a shot takes at least 5, so at most 18 at once.
        enemy-cap 48
        bullet-cap 18
        start (assoc-in init-state [:hero :hp] 1000000)
        run (reductions (fn [s _] (idle s)) start (range frames-run))]
    (is (= (inc frames-run) (count run)))
    (is (< 3 (apply max (map (comp count :enemies) run))) "enemies really did arrive")
    (is (every? #(<= (+ (count (:enemies %)) (count (:gems %))) enemy-cap) run))
    (is (every? #(<= (count (:bullets %)) bullet-cap) run))
    (is (pos? (:kills (last run))) "and the hero killed some of them")
    (testing "a hero that keeps moving stays inside the same bounds"
      (let [moving (reductions (fn [s i]
                                 (adv s :down [(+ 600.0 (* 300 (Math/cos (/ i 20.0))))
                                               (+ 1500.0 (* 300 (Math/sin (/ i 20.0))))]))
                               (adv start :press [600.0 1500.0])
                               (range frames-run))]
        (is (every? #(<= (+ (count (:enemies %)) (count (:gems %))) enemy-cap) moving))
        (is (every? #(<= (count (:bullets %)) bullet-cap) moving))))))

(deftest rotation-starts-a-new-game
  (let [played (assoc-in (frames init-state 40) [:hero :hp] 50)
        turned (adv played {:screen [2334 1206]} :idle nil)]
    (is (= [2334 1206] (:screen turned)))
    (is (= sv/hero-hp (:hp (:hero turned))))
    (is (empty? (:enemies turned)))))

(deftest the-game-draws-from-the-lcg
  (is (= (frames init-state 200) (frames init-state 200)))
  (is (not= (:enemies (frames init-state 200))
            (:enemies (frames (assoc init-state :seed 1) 200)))))

(deftest the-stick-is-kept-in-state-and-ends-with-the-touch
  (is (nil? (:stick init-state)))
  (let [held (adv (adv calm :press below-back) :down [800.0 1300.0])]
    (is (= {:centre below-back
            :finger [800.0 1300.0]}
           (:stick held)))
    (is (nil? (:stick (adv held :release [800.0 1300.0]))))
    (is (nil? (:stick (idle held)))))
  (testing "the knob is held to the ring"
    (let [[kx ky] (sv/knob d {:centre [500.0 1000.0]
                              :finger [1100.0 1000.0]})]
      (is (near? (+ 500.0 (:stick-r d)) kx))
      (is (near? 1000.0 ky)))
    (is (= [520.0 1000.0] (sv/knob d {:centre [500.0 1000.0]
                                      :finger [520.0 1000.0]})))))

(deftest fits-below-back
  (doseq [screen screens
          :let [[w h] screen
                dm (sv/dimensions {:screen screen})
                {:keys [hp-bar xp-bar lv kills]} dm]]
    (testing (str screen)
      (is (>= (:ftop dm) back-bottom))
      (is (<= (+ (:ftop dm) (:fh dm)) h))
      (is (== w (:fw dm)))
      (is (pos? (:u dm)))
      (testing "the hero and an enemy fit with room to move"
        (is (< (* 2 (:hero-r dm)) (min w (:fh dm))))
        (is (< (* 2 (:edge dm)) (min w (:fh dm)))))
      (testing "the HUD sits below Back and inside the screen"
        (doseq [[x y bw bh] [hp-bar xp-bar]]
          (is (>= y back-bottom))
          (is (<= 0 x))
          (is (<= (+ x bw) w))
          (is (<= (+ y bh) h)))
        (is (>= (:y lv) back-bottom))
        (is (>= (:y kills) back-bottom)))
      (testing "the stick ring fits on the screen"
        (is (< (* 2 (:stick-r dm)) (min w h))))
      (testing "fifty spawn points are inside the field, circle and all"
        (let [[wave _] (#'sv/spawn-wave dm 7 50)]
          (is (= 50 (count wave)))
          (is (every? (fn [{:keys [x y]}]
                        (and (<= (:edge dm) x (- w (:edge dm)))
                             (<= (+ (:ftop dm) (:edge dm)) y (- (+ (:ftop dm) (:fh dm)) (:edge dm)))))
                      wave)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dm (sv/dimensions {:screen screen})
                fits (fn [x y size line]
                       (testing line
                         (is (<= 0 x))
                         (is (<= (+ x (measure line size)) w))
                         (is (>= y back-bottom))
                         (is (<= (+ y size) h))))
                left (fn [k line]
                       (let [{:keys [x y size]} (get dm k)]
                         (fits x y size line)))
                centred (fn [k line]
                          (let [{:keys [y size]} (get dm k)]
                            (fits (sv/centred-x dm size line measure) y size line)))]]
    (testing (str screen)
      (left :lv (sv/lv-line 99))
      (left :kills (sv/kills-line 99999))
      (left :time (sv/time-line (* 60 99999)))
      (left :hint sv/hint-line)
      (centred :msg sv/over-line)
      (centred :msg2 (sv/summary-line (* 60 99999) 99999))
      (centred :msg3 sv/restart-line)
      (testing "the LV text clears the bars, and the rows do not overlap"
        (let [[bx _ bw _] (:hp-bar dm)]
          (is (>= (:x (:lv dm)) (+ bx bw))))
        (is (<= (+ (:y (:kills dm)) (:size (:kills dm))) (:y (:time dm))))
        (is (<= (+ (:y (:time dm)) (:size (:time dm))) (:y (:hint dm))))
        (is (<= (+ (:y (:msg dm)) (:size (:msg dm))) (:y (:msg2 dm))))
        (is (<= (+ (:y (:msg2 dm)) (:size (:msg2 dm))) (:y (:msg3 dm))))
        (is (<= (+ (:y (:msg3 dm)) (:size (:msg3 dm))) (:y (:hint dm))))))))

(deftest the-scene-has-its-id
  (let [sc (sv/scene)]
    (is (= :survivors (:id sc)))
    (is (= "Vampire Survivors" (:title sc)))))
