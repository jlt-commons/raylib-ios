(ns net.b12n.raylib-ios.scenes.camera2d-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.camera2d :as cam]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.camera2d :as c2d]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (c2d/geometry m))
(def start (first ((:init (c2d/scene)) {:metrics m})))
(def slop (gesture/slop m))
(def below-back [600.0 1200.0])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-6))

(defn- step
  "One frame. `points` are the touch points (the first is the pointer)."
  ([state phase points] (step state m phase points))
  ([state metrics phase points]
   (c2d/advance state {:metrics metrics
                       :pointer {:phase phase
                                 :position (first points)}
                       :touch-points (vec points)})))

(defn- idle [state] (step state :idle []))
(defn- centre [[x y w h]] [(+ x (* 0.5 w)) (+ y (* 0.5 h))])

(defn- pinch-out
  "Press two fingers `a` and `b` apart, then hold them at `a2` and `b2`."
  [state a b a2 b2]
  (-> state
      (step :press [a b])
      (step :down [a b])
      (step :down [a2 b2])))

(deftest the-stick-moves-the-player-at-four-units
  (let [[cx cy] below-back
        pressed (step start :press [below-back])
        right (step pressed :down [[(+ cx 300) cy]])
        left (step pressed :down [[(- cx 300) cy]])]
    (testing "the press itself moves nothing"
      (is (= (:px start) (:px pressed))))
    (testing "right and left are 4 world units a frame, whatever the zoom"
      (is (near? (+ (:px start) 4.0) (:px right)))
      (is (near? (- (:px start) 4.0) (:px left)))
      (is (near? (+ (:px start) 4.0)
                 (:px (step (assoc pressed :zoom 3.0) :down [[(+ cx 300) cy]])))))
    (testing "how far the finger is does not change the speed"
      (is (= (:px (step pressed :down [[(+ cx 100) cy]]))
             (:px (step pressed :down [[(+ cx 500) cy]])))))
    (testing "the stick is on x only: drifting up or down moves nothing"
      (is (= (:px start) (:px (step pressed :down [[cx (+ cy 400)]]))))
      (is (near? (+ (:px start) 4.0) (:px (step pressed :down [[(+ cx 300) (+ cy 400)]])))))
    (testing "it keeps going while held, then stops on release without reading it"
      (let [held (-> pressed
                     (step :down [[(+ cx 300) cy]])
                     (step :down [[(+ cx 300) cy]]))
            lifted (step held :release [[0.0 0.0]])]
        (is (near? (+ (:px start) 8.0) (:px held)))
        (is (= (:px held) (:px lifted)))
        (is (= (:px held) (:px (idle lifted))))
        (is (nil? (:stick (idle lifted))))))
    (testing "the camera follows the player on x and holds y at 200"
      (let [c (c2d/camera right d)]
        (is (= [(:px right) 200.0] (:target c)))))))

(deftest a-tap-does-not-move-it
  (let [tap (-> start
                (step :press [below-back])
                (step :down [below-back])
                (step :release [below-back]))
        shaky (-> start
                  (step :press [below-back])
                  (step :down [[(+ (first below-back) slop) (second below-back)]]))]
    (is (= (:px start) (:px tap)))
    (is (= (:px start) (:px shaky)) "exactly the slop out is still the dead zone")
    (testing "a press under Back starts no stick"
      (let [pressed (step start :press [[100.0 60.0]])]
        (is (nil? (:stick pressed)))
        (is (= (:px start) (:px (step pressed :down [[500.0 60.0]]))))))
    (testing "no stick, no walk: a finger that began before the scene opened"
      (is (= (:px start) (:px (step start :down [[900.0 1200.0]])))))))

(deftest a-pinch-zooms-within-the-clamp
  (let [a [400.0 1200.0]
        b [500.0 1200.0]
        out (pinch-out start a b [300.0 1200.0] [600.0 1200.0])]
    (testing "fingers three times as far apart triple the zoom"
      (is (near? 3.0 (:zoom out))))
    (testing "twice as far is twice the zoom, and pulling in halves it"
      (is (near? 2.0 (:zoom (pinch-out start a b [350.0 1200.0] [550.0 1200.0]))))
      (is (near? 0.5 (:zoom (pinch-out start a b [425.0 1200.0] [475.0 1200.0])))))
    (testing "it never leaves 0.25 to 3.0"
      (is (near? 3.0 (:zoom (pinch-out start a b [0.0 1200.0] [1200.0 1200.0]))))
      (is (near? 0.25 (:zoom (pinch-out start a b [449.0 1200.0] [451.0 1200.0])))))
    (testing "the first two-finger frame only records the pinch"
      (let [first-frame (step start :press [a b])]
        (is (near? 1.0 (:zoom first-frame)))
        (is (some? (:pinch first-frame)))))
    (testing "swapping the two touch points changes nothing"
      (let [swapped (-> start
                        (step :press [a b])
                        (step :down [b a])
                        (step :down [[600.0 1200.0] [300.0 1200.0]]))]
        (is (near? 3.0 (:zoom swapped)))
        (is (near? 0.0 (:rot swapped)))))
    (testing "the base zoom is untouched by the user's"
      (is (near? (* 3.0 (:base-zoom d)) (:zoom (c2d/camera out d)))))))

(deftest a-twist-rotates
  (let [a [500.0 1200.0]
        b [700.0 1200.0]
        ;; b swings to straight below a: a quarter turn, clockwise on screen
        turned (pinch-out start a b a [500.0 1400.0])
        back (pinch-out start a b a [(+ 500.0 (* 200 (Math/cos (Math/toRadians -30.0))))
                                     (+ 1200.0 (* 200 (Math/sin (Math/toRadians -30.0))))])]
    (is (near? 90.0 (:rot turned)))
    (is (near? 1.0 (:zoom turned)) "a pure twist leaves the zoom alone")
    (is (near? -30.0 (:rot back)))
    (testing "the twist accumulates across frames"
      (let [two (-> turned (step :down [a [500.0 1400.0]]))]
        (is (near? 90.0 (:rot two)))))))

(deftest reset-restores-zoom-and-rotation
  (let [[rx ry rw rh] (:reset d)
        on [(+ rx (* 0.5 rw)) (+ ry (* 0.5 rh))]
        messy (assoc start :zoom 2.2 :rot 41.0 :px 777.0)
        tapped (-> messy
                   (step :press [on])
                   (step :down [on])
                   (step :release [on]))]
    (is (near? 1.0 (:zoom tapped)))
    (is (near? 0.0 (:rot tapped)))
    (is (= 777.0 (:px tapped)) "the player stays where it is")
    (testing "a press on the button starts no stick"
      (let [pressed (step messy :press [on])]
        (is (nil? (:stick pressed)))
        (is (= 777.0 (:px (step pressed :down [[(+ (first on) 300) (second on)]]))))))
    (testing "a tap elsewhere does not reset"
      (let [elsewhere (-> messy
                          (step :press [below-back])
                          (step :down [below-back])
                          (step :release [below-back]))]
        (is (near? 2.2 (:zoom elsewhere)))
        (is (near? 41.0 (:rot elsewhere)))))
    (testing "a tap that ends a pinch does not reset"
      (let [pinched (-> messy
                        (step :press [on [300.0 1500.0]])
                        (step :down [on [300.0 1500.0]])
                        (step :release [on]))]
        (is (near? 2.2 (:zoom pinched)))))))

(deftest a-second-finger-ends-the-stick
  (let [[cx cy] below-back
        far [(+ cx 300) cy]
        walking (-> start (step :press [below-back]) (step :down [far]))
        two (step walking :down [far [300.0 1800.0]])
        one-left (step two :down [far])
        lifted (step one-left :down [far])]
    (is (near? (+ (:px start) 4.0) (:px walking)))
    (testing "the frame the second finger lands, the player stops"
      (is (= (:px walking) (:px two)))
      (is (nil? (:stick two))))
    (testing "while two are down, only the pinch acts"
      (let [held (step two :down [far [300.0 1800.0]])]
        (is (= (:px walking) (:px held)))))
    (testing "when one lifts the finger left starts no new stick"
      (is (= (:px walking) (:px one-left)))
      (is (= (:px walking) (:px lifted)))
      (is (nil? (:pinch one-left)) "and the pinch ends, so the next one does not jump"))
    (testing "a fresh touch can walk again"
      (let [again (-> lifted (step :release [far]) idle
                      (step :press [below-back]) (step :down [far]))]
        (is (near? (+ (:px walking) 4.0) (:px again)))))
    (testing "a new pair of fingers starts a new pinch with no jump"
      (let [a [400.0 1200.0]
            b [500.0 1200.0]
            second-pair (-> one-left (step :down [a b]))]
        (is (near? (:zoom walking) (:zoom second-pair)))))))

(deftest the-pinch-follows-the-shared-rule-for-the-finger-count
  ;; `net.b12n.raylib-ios.camera2d/pinch-frame`: a pinch acts only while the count stays at
  ;; two, and a change of count only records.
  (let [a [400.0 1200.0]
        b [500.0 1200.0]
        c [900.0 1500.0]
        view (juxt :zoom :rot)
        pinched (-> start (step :press [a b]) (step :down [a b])
                    (step :down [[350.0 1200.0] [550.0 1200.0]]))
        held (view pinched)]
    (is (not= (view start) held))
    (testing "a reorder of three points moves nothing"
      (let [three (step pinched :down [a b c])]
        (is (= held (view three)))
        (is (= held (view (step three :down [c a b]))))
        (is (= held (view (step three :down [b c a]))))))
    (testing "one of three lifting moves nothing"
      (let [three (step pinched :down [a b c])
            two (step three :down [b c])]
        (is (= held (view two)))
        (is (= held (view (step two :down [b c]))))))
    (testing "two, three, two moves nothing"
      (let [back (-> pinched (step :down [a b c]) (step :down [a b]))]
        (is (= held (view back)))))))

(deftest a-rotation-of-the-phone-drops-the-stick-and-the-pinch
  (let [held (-> start (step :press [below-back]) (step :down [[900.0 1200.0]]))
        land {:screen [2334 1206]}
        turned (step held land :down [[900.0 700.0]])]
    (is (some? (:stick held)))
    (is (nil? (:stick turned)))
    (is (= (:px held) (:px turned)))
    (is (= [2334 1206] (:screen turned)))))

(deftest the-view-fits-below-back
  (doseq [screen screens
          :let [[w h] screen
                g (c2d/geometry {:screen screen})
                [_ back-y _ back-h] gesture/back-region
                [fx fy fw fh] (:field g)
                [rx ry rw rh] (:reset g)]]
    (testing (str screen)
      (is (>= ry (+ back-y back-h)) "the button is below Back")
      (is (<= (+ ry rh) fy) "and above the field")
      (is (>= rx 0))
      (is (<= (+ rx rw) w))
      (is (>= fy (+ back-y back-h)))
      (is (= [0.0 (double w)] [fx fw]))
      (is (near? h (+ fy fh)) "the field runs to the bottom")
      (is (<= (* (:base-zoom g) 800.0) (+ fw 1e-6)) "the 800x450 view fits across")
      (is (<= (* (:base-zoom g) 450.0) (+ fh 1e-6)) "and down")
      (is (or (near? (* (:base-zoom g) 800.0) fw)
              (near? (* (:base-zoom g) 450.0) fh)) "touching one axis")
      (is (= (centre (:field g)) (:offset g)) "the offset is the field's centre"))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (c2d/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (= 2 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) h) s))
      (testing "the label fits its button"
        (let [[_ _ rw rh] (:reset dims)
              {:keys [s size]} (:reset-label dims)]
          (is (<= (measure s size) rw))
          (is (<= size rh))))
      (testing "the hint sits above the field"
        (let [{:keys [y size]} (:hint dims)
              [_ fy _ _] (:field dims)]
          (is (<= (+ y size) fy)))))))

(deftest the-skyline-is-the-originals
  (is (= 30 (count c2d/buildings)))
  (is (= {:x 0
          :w 107
          :h 125
          :color [100 80 90 255]} (first c2d/buildings)))
  (is (every? (fn [{:keys [color]}] (every? #(<= 0 % 255) color)) c2d/buildings)))

(deftest first-frame-draws
  (testing "the state after init alone has everything a draw reads"
    (doseq [screen screens
            :let [metrics {:screen screen}
                  s (first ((:init (c2d/scene)) {:metrics metrics}))
                  dims (c2d/dimensions metrics measure)
                  c (c2d/camera s dims)]]
      (is (every? number? [(:px s) (:zoom s) (:rot s)]) (str screen))
      (is (every? number? (concat (:offset c) (:target c) [(:rotation c) (:zoom c)])))
      (is (pos? (:zoom c)))
      (is (= (:zoom c) (* (:base-zoom dims) (:zoom s))) "the camera's zoom is the base zoom times the user's")
      (is (= (:offset dims) (:offset c)))
      (is (some? (cam/world->screen c [(:px s) 200.0]))))))
