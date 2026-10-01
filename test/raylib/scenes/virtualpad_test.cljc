(ns raylib.scenes.virtualpad-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.virtualpad :as vp]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (vp/dimensions m))
(def start (first ((:init (vp/scene)) {:metrics m})))

(defn- input
  "A frame at 60 Hz with a finger at each of `pts`."
  ([pts] (input m pts))
  ([metrics pts]
   {:metrics metrics
    :delta-seconds (/ 1.0 60)
    :touch-points (vec pts)
    :pointer (if (seq pts)
               {:phase :down
                :position (first pts)}
               {:phase :idle
                :position nil})}))

(defn- adv [state pts] (vp/advance state (input pts)))
(defn- frames [state pts n] (nth (iterate #(adv % pts) state) n))

(defn- seg-pt
  "The centre of the pad's `dir` circle, which sits 44/82 of the radius out."
  [{:keys [pad-x pad-y pad-r]} dir]
  (let [o (* pad-r (/ 44.0 82))]
    (case dir
      :up [pad-x (- pad-y o)]
      :down [pad-x (+ pad-y o)]
      :left [(- pad-x o) pad-y]
      :right [(+ pad-x o) pad-y])))

(defn- a-pt [{:keys [btn-x btn-y]}] [btn-x btn-y])

(defn- near? [a b] (< (abs (- (double a) (double b))) 1e-6))

(defn- mid
  "The square placed mid-field, resting."
  [state]
  (assoc state :px (* 0.5 (:w d)) :py (+ (:ftop d) (* 0.5 (:fh d))) :hop 0.0))

(deftest each-pad-segment-moves-the-square
  (let [s (mid start)
        step (* 190 (/ 1.0 60))]
    (doseq [[dir axis sign] [[:up :py -1] [:down :py 1] [:left :px -1] [:right :px 1]]]
      (testing dir
        (let [s' (adv s [(seg-pt d dir)])
              k (if (= axis :px) (:sx d) (:sy d))]
          (is (near? (+ (axis s) (* sign step k)) (axis s')))
          (is (near? (if (= axis :px) (:py s) (:px s))
                     (if (= axis :px) (:py s') (:px s')))))))))

(deftest the-dead-centre-and-the-outside-do-nothing
  (let [s (mid start)
        {:keys [pad-x pad-y pad-r]} d]
    (is (= s (select-keys (adv s [[pad-x pad-y]]) (keys s))))
    (is (= (:px s) (:px (adv s [[(+ pad-x pad-r 5) pad-y]]))))))

(deftest a-makes-the-square-hop
  (let [s (mid start)
        held (adv s [(a-pt d)])
        released (adv held [])
        hop-rise (fn [st] (- (nth (vp/square-rect d (assoc st :hop 0.0)) 1)
                             (nth (vp/square-rect d st) 1)))]
    (testing "holding A pins the hop at 1, where the arc is at rest"
      (is (= 1.0 (:hop held))))
    (testing "after release the hop decays by 2.4 a second"
      (is (near? (- 1.0 (/ 2.4 60)) (:hop released)))
      (is (zero? (:hop (frames released [] 40)))))
    (testing "mid-decay the square is lifted"
      (is (pos? (hop-rise (assoc s :hop 0.5)))))
    (testing "A does not move the square sideways"
      (is (= (:px s) (:px held))))))

(deftest pad-and-a-together
  (let [s (mid start)
        two (adv s [(seg-pt d :right) (a-pt d)])
        order (adv s [(a-pt d) (seg-pt d :right)])
        both-pad (adv s [(seg-pt d :right) (seg-pt d :up)])]
    (testing "a direction and A both act, whichever finger is first"
      (doseq [r [two order]]
        (is (> (:px r) (:px s)))
        (is (= 1.0 (:hop r)))))
    (testing "two pad fingers combine into a diagonal"
      (is (> (:px both-pad) (:px s)))
      (is (< (:py both-pad) (:py s))))
    (is (= #{:right} (:dirs (vp/held d (input [(seg-pt d :right) (a-pt d)])))))
    (is (:a? (vp/held d (input [(seg-pt d :right) (a-pt d)]))))))

(deftest a-touch-outside-the-controls-does-nothing
  (let [s (mid start)
        far [(* 0.5 (:w d)) (+ (:ftop d) 10)]
        s' (adv s [far])]
    (is (= {:dirs #{}
            :a? false} (vp/held d (input [far]))))
    (is (near? (:px s) (:px s')))
    (is (near? (:py s) (:py s')))
    (is (zero? (:hop s')))))

(deftest controls-avoid-back
  (doseq [screen screens
          :let [dd (vp/dimensions {:screen screen})
                [bx by _ bh] gesture/back-region
                back-bottom (+ by bh)
                {:keys [pad-x pad-y pad-r btn-x btn-y btn-r w h]} dd]]
    (testing screen
      (is (>= (- pad-y pad-r) back-bottom))
      (is (>= (- btn-y btn-r) back-bottom))
      (is (>= (:ftop dd) back-bottom))
      (is (<= 0 (- pad-x pad-r)))
      (is (<= (+ pad-x pad-r) (- btn-x btn-r)))
      (is (<= (+ btn-x btn-r) w))
      (is (<= (+ pad-y pad-r) h))
      (is (<= (+ btn-y btn-r) h))
      (is (< pad-x btn-x))
      (testing "no probe point in the pad or A is in Back"
        (doseq [dir [:up :down :left :right]]
          (is (not (gesture/in-back-region? (seg-pt dd dir)))))
        (is (not (gesture/in-back-region? (a-pt dd)))))
      (is (zero? bx)))))

(deftest the-square-stays-in-the-play-area
  (doseq [screen screens
          :let [dd (vp/dimensions {:screen screen})
                s0 (first ((:init (vp/scene)) {:metrics {:screen screen}}))
                in (fn [pts] (input {:screen screen} pts))
                inside? (fn [st]
                          (let [[x y w h] (vp/square-rect dd st)]
                            (and (>= x 0.0) (>= y (:ftop dd))
                                 (<= (+ x w) (:w dd))
                                 (<= (+ y h) (+ (:ftop dd) (:fh dd))))))]]
    (testing screen
      (is (inside? s0))
      (doseq [dir [:up :down :left :right]
              :let [s (nth (iterate #(vp/advance % (in [(seg-pt dd dir)])) s0) 400)]]
        (testing dir
          (is (inside? s))
          (is (inside? (assoc s :hop 0.5)))
          (is (inside? (assoc s :hop 0.01))))))))

(deftest the-hop-follows-delta-seconds
  (let [s (assoc (mid start) :hop 1.0)
        at (fn [dt] (:hop (vp/advance s (assoc (input []) :delta-seconds dt))))
        mv (fn [dt] (:px (vp/advance (mid start) (assoc (input [(seg-pt d :right)]) :delta-seconds dt))))]
    (is (near? 0.76 (at 0.1)))
    (is (= 1.0 (at -1.0)))
    (is (= (:px (mid start)) (mv -1.0)))
    (is (> (- (mv 0.032) (:px (mid start))) (* 1.9 (- (mv 0.016) (:px (mid start))))))))

(deftest a-rotation-starts-over
  (let [moved (frames (mid start) [(seg-pt d :right)] 10)
        landscape {:screen [2334 1206]}
        first-frame (vp/advance moved (input landscape []))
        ld (vp/dimensions landscape)]
    (is (near? (* 400 (:sx ld)) (:px first-frame)))
    (testing "the first frame on a screen does not reset a game in progress"
      (is (= (:px moved) (:px (adv moved [])))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [{:keys [w h lines]} (vp/dimensions {:screen screen})]]
    (testing screen
      (is (seq lines))
      (doseq [{:keys [s x y size]} lines
              :let [tw (* 0.6 size (count s))]]
        (testing s
          (is (>= x 0))
          (is (<= (+ x tw) w))
          (is (>= y (let [[_ by _ bh] gesture/back-region] (+ by bh))))
          (is (<= (+ y size) h)))))))
