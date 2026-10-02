(ns raylib.scenes.camerazoom-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.camera2d :as cam]
            [raylib.gesture :as gesture]
            [raylib.scenes.camerazoom :as cz]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (cz/geometry m))
(def start (first ((:init (cz/scene)) {:metrics m})))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-6))
(defn- near-pt? [[ax ay] [bx by]] (and (near? ax bx) (near? ay by)))

(defn- step
  "One frame. `points` are the touch points (the first is the pointer)."
  ([state phase points] (step state m phase points))
  ([state metrics phase points]
   (cz/advance state {:metrics metrics
                      :pointer {:phase phase
                                :position (first points)}
                      :touch-points (vec points)})))

(defn- idle [state] (step state :idle []))
(defn- cam-of [state] (:camera state))
(defn- zoom-of [state] (:zoom (cam-of state)))
(defn- world-at [state p] (cam/screen->world (cam-of state) p))

(def a [400.0 1200.0])
(def b [500.0 1200.0])
(def mid [450.0 1200.0])

(deftest a-drag-pans-by-delta-over-zoom
  (doseq [z [1.0 2.0 0.5 8.0]
          :let [s (assoc-in start [:camera :zoom] z)
                from [600.0 1000.0]
                to [640.0 980.0]
                pressed (step s :press [from])
                moved (step pressed :down [to])
                [tx ty] (:target (cam-of s))
                [nx ny] (:target (cam-of moved))]]
    (testing (str "zoom " z)
      (is (near? 0.0 (- (+ tx 0.0) (first (:target (cam-of pressed))))) "the press moves nothing")
      (is (near? (- tx (/ 40.0 z)) nx))
      (is (near? (- ty (/ -20.0 z)) ny))
      (is (near? z (zoom-of moved)) "panning leaves the zoom alone")
      (is (near-pt? (world-at pressed from) (world-at moved to))
          "the world point under the finger stays under it")))
  (testing "a drag keeps going frame to frame from the last position"
    (let [s (-> start
                (step :press [[600.0 1000.0]])
                (step :down [[610.0 1000.0]])
                (step :down [[630.0 1000.0]]))]
      (is (near? (- (first (:target (cam-of start))) 30.0) (first (:target (cam-of s)))))))
  (testing "a press under Back starts no drag"
    (let [pressed (step start :press [[100.0 60.0]])
          moved (step pressed :down [[300.0 400.0]])]
      (is (nil? (:drag pressed)))
      (is (= (:target (cam-of start)) (:target (cam-of moved))))))
  (testing "a second finger landing ends the drag that frame"
    (let [held (-> start (step :press [[600.0 1000.0]]) (step :down [[620.0 1000.0]]))
          two (step held :down [[700.0 1000.0] [300.0 1500.0]])]
      (is (= (:target (cam-of held)) (:target (cam-of two))))
      (is (nil? (:drag two))))))

(deftest the-release-does-not-pan
  (let [held (-> start
                 (step :press [[600.0 1000.0]])
                 (step :down [[640.0 1000.0]]))
        lifted (step held :release [[0.0 0.0]])]
    (is (not= (:target (cam-of start)) (:target (cam-of held))) "the drag did pan")
    (is (= (:target (cam-of held)) (:target (cam-of lifted))))
    (is (= (:target (cam-of held)) (:target (cam-of (idle lifted)))))
    (is (nil? (:drag (idle lifted))))
    (is (= (:cross held) (:cross lifted)) "and the crosshair stays where the finger last was")))

(deftest a-pinch-keeps-its-midpoint-fixed
  (let [before (-> start (step :press [a b]) (step :down [a b]))]
    (testing "spreading about a fixed midpoint triples the zoom, midpoint pinned"
      (let [after (step before :down [[300.0 1200.0] [600.0 1200.0]])]
        (is (near? 3.0 (zoom-of after)))
        (is (near-pt? (world-at before mid) (world-at after mid)))
        (is (near-pt? mid (cam/world->screen (cam-of after) (world-at before mid))))))
    (testing "pulling in halves it, midpoint pinned"
      (let [after (step before :down [[425.0 1200.0] [475.0 1200.0]])]
        (is (near? 0.5 (zoom-of after)))
        (is (near-pt? (world-at before mid) (world-at after mid)))))
    (testing "an off-centre midpoint is pinned as well"
      (let [s (assoc-in start [:camera :zoom] 2.0)
            p [100.0 300.0]
            q [160.0 380.0]
            m1 [130.0 340.0]
            before (-> s (step :press [p q]) (step :down [p q]))
            after (step before :down [[90.0 290.0] [170.0 390.0]])]
        (is (> (zoom-of after) 2.0))
        (is (near-pt? (world-at before m1) (world-at after m1)))))
    (testing "the world point under the midpoint follows it when it drifts"
      (let [moved (step before :down [[(+ 30.0 (first a)) 1230.0] [(+ 30.0 (first b)) 1230.0]])]
        (is (near? 1.0 (zoom-of moved)))
        (is (near-pt? (world-at before mid) (world-at moved [480.0 1230.0])))))
    (testing "the first two-finger frame only records the pinch"
      (let [first-frame (step start :press [a b])]
        (is (= (cam-of start) (cam-of first-frame)))
        (is (some? (:pinch first-frame)))))
    (testing "swapping the two touch points changes nothing"
      (let [swapped (-> start
                        (step :press [a b])
                        (step :down [b a])
                        (step :down [[600.0 1200.0] [300.0 1200.0]]))]
        (is (near? 3.0 (zoom-of swapped)))))
    (testing "the crosshair follows the midpoint"
      (is (near-pt? mid (:cross before))))))

(deftest zoom-clamps
  (let [before (-> start (step :press [a b]) (step :down [a b]))
        big (-> (assoc-in start [:camera :zoom] 8.0) (step :press [a b]) (step :down [a b]))
        out (step big :down [[0.0 1200.0] [1200.0 1200.0]])
        in (step before :down [[449.0 1200.0] [451.0 1200.0]])]
    (is (near? 64.0 (zoom-of out)) "8 times a 12x spread is 96, held to 64")
    (is (near? 0.125 (zoom-of in)) "a 50x pull in is 0.02, held to 0.125")
    (testing "it stays put at the limit and comes back at once"
      (let [top (-> (assoc-in start [:camera :zoom] 64.0)
                    (step :press [a b])
                    (step :down [a b])
                    (step :down [[300.0 1200.0] [600.0 1200.0]]))
            back (step top :down [[375.0 1200.0] [525.0 1200.0]])]
        (is (near? 64.0 (zoom-of top)))
        (is (near? 32.0 (zoom-of back)))))
    (testing "coincident fingers never collapse the zoom"
      (let [s (-> start (step :press [a a]) (step :down [a a]) (step :down [a b]))]
        (is (near? 1.0 (zoom-of s)))))))

(deftest lifting-one-finger-does-not-jump
  (let [before (-> start (step :press [a b]) (step :down [a b]))
        pinched (step before :down [[350.0 1200.0] [550.0 1200.0]])
        one-left (step pinched :down [[550.0 1200.0]])
        later (step one-left :down [[900.0 1500.0]])]
    (is (not= (cam-of before) (cam-of pinched)))
    (testing "the frame one finger lifts changes nothing"
      (is (= (cam-of pinched) (cam-of one-left)))
      (is (nil? (:pinch one-left)))
      (is (nil? (:drag one-left))))
    (testing "the finger left starts no pan, however far it moves"
      (is (= (cam-of pinched) (cam-of later))))
    (testing "the next pair starts a pinch with no jump"
      (let [second-pair (step later :down [[100.0 1200.0] [200.0 1200.0]])
            grown (step second-pair :down [[100.0 1200.0] [300.0 1200.0]])]
        (is (= (cam-of pinched) (cam-of second-pair)))
        (is (near? (* 2.0 (zoom-of pinched)) (zoom-of grown)))))
    (testing "a fresh touch pans again"
      (let [again (-> later (step :release [[900.0 1500.0]]) idle
                      (step :press [[600.0 1000.0]])
                      (step :down [[650.0 1000.0]]))]
        (is (not= (:target (cam-of pinched)) (:target (cam-of again))))))))

(deftest a-third-finger-cannot-swap-the-pair
  (let [before (-> start (step :press [a b]) (step :down [a b]))
        c-left [100.0 1200.0]
        c-between [450.0 1500.0]]
    (doseq [c [c-left c-between [1000.0 1900.0]]
            order [[a b c] [c a b] [b c a]]]
      (let [after (step before :down order)]
        (is (near? 1.0 (zoom-of after)) (str c order))
        (is (= (cam-of before) (cam-of after)) (str c order))))
    (testing "the pair keeps following its own fingers"
      (let [after (step before :down [c-left [350.0 1200.0] [550.0 1200.0]])]
        (is (near? 2.0 (zoom-of after)))))
    (testing "three fingers on the first frame pick the two leftmost, always"
      (let [one (step start :press [[300.0 1000.0] [100.0 1000.0] [200.0 1000.0]])
            two (step start :press [[200.0 1000.0] [300.0 1000.0] [100.0 1000.0]])]
        (is (= (:pair one) (:pair two)))
        (is (= (set [[100.0 1000.0] [200.0 1000.0]]) (set (:pair one))))))))

(deftest a-rotation-of-the-phone-drops-the-drag-and-the-pinch
  (let [held (-> start (step :press [[600.0 1000.0]]) (step :down [[640.0 1000.0]]))
        land {:screen [2334 1206]}
        turned (step held land :down [[900.0 700.0]])]
    (is (some? (:drag held)))
    (is (nil? (:drag turned)))
    (is (= (:target (cam-of held)) (:target (cam-of turned))))
    (is (= [2334 1206] (:screen turned)))
    (is (near-pt? (:offset (cz/geometry land)) (:offset (cam-of turned)))
        "the camera is recentred on the new field")))

(deftest zoom-label-has-three-decimals
  (is (= "zoom 1.000" (cz/zoom-label 1.0)))
  (is (= "zoom 0.125" (cz/zoom-label 0.125)))
  (is (= "zoom 64.000" (cz/zoom-label 64.0)))
  (is (= "zoom 0.100" (cz/zoom-label 0.1)))
  (is (= "zoom 12.346" (cz/zoom-label 12.3456)))
  (is (= "zoom 0.005" (cz/zoom-label 0.005))))

(deftest the-field-is-below-back
  (doseq [screen screens
          :let [[w h] screen
                g (cz/geometry {:screen screen})
                [_ back-y _ back-h] gesture/back-region
                [fx fy fw fh] (:field g)]]
    (testing (str screen)
      (is (>= fy (+ back-y back-h)))
      (is (= [0.0 (double w)] [fx fw]))
      (is (near? h (+ fy fh)) "the field runs to the bottom")
      (is (near-pt? [(* 0.5 w) (+ fy (* 0.5 fh))] (:offset g))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (cz/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (= 2 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) h) s))
      (testing "the two lines do not overlap"
        (let [{hy :y
               hs :size} (:hint dims)
              {zy :y} (:zoom-line dims)]
          (is (<= (+ hy hs) zy))))
      (testing "the widest zoom line is the one measured"
        (let [{:keys [s]} (:zoom-line dims)]
          (is (>= (count s) (count (cz/zoom-label cz/zoom-max)))))))))

(deftest first-frame-draws
  (testing "the state after init alone has everything a draw reads"
    (doseq [screen screens
            :let [metrics {:screen screen}
                  s (first ((:init (cz/scene)) {:metrics metrics}))
                  dims (cz/dimensions metrics measure)
                  [fx fy fw fh] (:field dims)
                  c (cam-of s)
                  [ox oy] (cam/world->screen c [0.0 0.0])
                  [cx cy] (:cross s)]]
      (testing (str screen)
        (is (pos? (:zoom c)))
        (is (<= cz/zoom-min (:zoom c) cz/zoom-max))
        (is (and (<= fx ox (+ fx fw)) (<= fy oy (+ fy fh))) "the world origin starts in view")
        (is (and (<= fx cx (+ fx fw)) (<= fy cy (+ fy fh))) "and so does the crosshair")
        (is (every? #(<= fy (:y %) (+ fy fh)) (:lines dims)) "the text is over the field")))))
