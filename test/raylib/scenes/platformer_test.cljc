(ns raylib.scenes.platformer-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.camera2d :as cam]
            [raylib.gesture :as gesture]
            [raylib.scenes.platformer :as pf]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (pf/geometry m))
(def start (first ((:init (pf/scene)) {:metrics m})))
(def dt (/ 1.0 60.0))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-6))

(defn- button-point
  "The centre of the button `id` on `geo`."
  ([id] (button-point d id))
  ([geo id]
   (let [[x y w h] (:rect (first (filter #(= id (:id %)) (:buttons geo))))]
     [(+ x (* 0.5 w)) (+ y (* 0.5 h))])))

(defn- step
  "One frame of `state` with the buttons `ids` held, `seconds` long."
  ([state ids] (step state ids dt))
  ([state ids seconds]
   (pf/advance state {:metrics m
                      :touch-points (mapv button-point ids)
                      :delta-seconds seconds})))

(defn- on-floor [state x]
  (assoc state :player {:x x
                        :y 400.0
                        :speed 0.0
                        :can-jump? true}))

(deftest gravity-and-jump-match-the-original
  (testing "gravity adds 400 a second to the speed and the move uses the old speed"
    (let [one (step start [])
          two (step one [])]
      (is (near? 280.0 (get-in one [:player :y])))
      (is (near? (* 400.0 dt) (get-in one [:player :speed])))
      (is (near? (+ 280.0 (* 400.0 dt dt)) (get-in two [:player :y])))
      (is (false? (get-in two [:player :can-jump?])))))
  (testing "falling from the start lands on the floor at exactly its top"
    (let [landed (first (filter #(get-in % [:player :can-jump?])
                                (take 300 (rest (iterate #(step % []) start)))))]
      (is (some? landed))
      (is (= 400.0 (get-in landed [:player :y])))
      (is (= 0.0 (get-in landed [:player :speed])))))
  (testing "a jump on the floor starts at minus 350 and rises about 153 units"
    (let [s (on-floor start 400.0)
          j (step s [:jump])
          rise (->> (iterate #(step % []) j)
                    (take 120)
                    (map #(- 400.0 (get-in % [:player :y])))
                    (apply max))]
      (is (near? (+ (- 350.0) (* 400.0 dt)) (get-in j [:player :speed])) "speed is -350 plus one step of gravity")
      (is (near? (- 400.0 (* 350.0 dt)) (get-in j [:player :y])))
      (is (false? (get-in j [:player :can-jump?])))
      (is (< (- 400.0 (get-in j [:player :y])) 6.0))
      (is (< 150.0 rise 158.0))))
  (testing "no jump in the air"
    (let [air (assoc start :player {:x 400.0
                                    :y 100.0
                                    :speed 0.0
                                    :can-jump? false})]
      (is (near? (* 400.0 dt) (get-in (step air [:jump]) [:player :speed]))))))

(deftest walk-and-jump-together
  (let [s (on-floor start 500.0)
        both (step s [:right :jump])
        left (step s [:left])
        none (step s [:left :right])]
    (is (near? (+ 500.0 (* 200.0 dt)) (get-in both [:player :x])))
    (is (near? (- 400.0 (* 350.0 dt)) (get-in both [:player :y])))
    (is (near? (- 500.0 (* 200.0 dt)) (get-in left [:player :x])))
    (testing "both arrows cancel"
      (is (near? 500.0 (get-in none [:player :x]))))
    (testing "the held set is read from every touch point"
      (is (= #{:right :jump} (pf/held-buttons d {:touch-points [(button-point :right)
                                                                (button-point :jump)]}))))))

(deftest a-fall-through-a-platform-top-lands
  (testing "a fall that crosses the top edge this frame lands on it"
    (let [s (assoc start :player {:x 500.0
                                  :y 195.0
                                  :speed 300.0
                                  :can-jump? false})
          after (step s [])]
      (is (= 200.0 (get-in after [:player :y])))
      (is (true? (get-in after [:player :can-jump?])))
      (is (= 0.0 (get-in after [:player :speed])))))
  (testing "a 0.1 second stall does not tunnel through a platform or the floor"
    (let [plat (step (assoc start :player {:x 500.0
                                           :y 190.0
                                           :speed 350.0
                                           :can-jump? false})
                     [] 0.1)
          floor (step (assoc start :player {:x 100.0
                                            :y 380.0
                                            :speed 380.0
                                            :can-jump? false})
                      [] 0.1)]
      (is (= 200.0 (get-in plat [:player :y])))
      (is (= 400.0 (get-in floor [:player :y])))))
  (testing "off the platform's end the player keeps falling"
    (let [s (assoc start :player {:x 701.0
                                  :y 195.0
                                  :speed 300.0
                                  :can-jump? false})]
      (is (> (get-in (step s []) [:player :y]) 195.0))
      (is (false? (get-in (step s []) [:player :can-jump?]))))))

(deftest walking-into-a-side-does-not-stop
  (let [s (assoc start :player {:x 299.0
                                :y 205.0
                                :speed 0.0
                                :can-jump? false})
        after (step s [:right] 0.1)]
    (is (near? 319.0 (get-in after [:player :x])))
    (is (false? (get-in after [:player :can-jump?])))
    (is (>= (get-in after [:player :y]) 205.0))))

(deftest each-camera-mode-follows-as-the-original
  (let [[_ _ vw vh] (:field d)
        z (:base-zoom d)
        half [(* 0.5 vw) (* 0.5 vh)]
        cam0 {:offset half
              :target [400.0 280.0]}
        upd (fn [mode player seconds]
              (pf/update-camera mode cam0 player seconds d))]
    (testing "centre targets the player"
      (let [c (upd :centre {:x 123.0
                            :y 456.0} dt)]
        (is (= [123.0 456.0] (:target c)))
        (is (= half (:offset c)))))
    (testing "clamped pins the map's left and right edges to the viewport's"
      (let [left (upd :clamped {:x 0.0
                                :y 280.0} dt)
            right (upd :clamped {:x 1000.0
                                 :y 280.0} dt)
            mid (upd :clamped {:x 500.0
                               :y 280.0} dt)
            at (fn [c x] (first (cam/world->screen (assoc c :rotation 0.0 :zoom z) [x 0.0])))]
        (is (near? 0.0 (at left 0.0)))
        (is (near? vw (at right 1000.0)))
        (is (= (first half) (first (:offset mid))) "away from the side edges x is the centre mode")
        (is (= [500.0 280.0] (:target mid)))))
    (testing "clamped pins the top and bottom edges when the map is taller than the view"
      (let [tall (pf/update-camera :clamped {:offset half
                                             :target [400.0 280.0]}
                                   {:x 400.0
                                    :y 0.0} dt (assoc d :base-zoom 100.0))
            [_ sy] (cam/world->screen (assoc tall :rotation 0.0 :zoom 100.0) [0.0 0.0])]
        (is (near? 0.0 sy))))
    (testing "smooth closes 0.8 of the distance a second, at least 30 a second"
      (let [far (upd :smooth {:x 400.0
                              :y 480.0} 0.5)
            near-ish (upd :smooth {:x 400.0
                                   :y 288.0} 0.5)
            slow (upd :smooth {:x 400.0
                               :y 290.0} 0.5)]
        (is (near? 360.0 (second (:target far))))
        (is (= [400.0 280.0] (:target near-ish)) "within 10 units it holds still")
        (is (= [400.0 280.0] (:target slow)))
        (let [mid (upd :smooth {:x 400.0
                                :y 300.0} 0.5)]
          (is (near? (+ 280.0 15.0) (second (:target mid))) "a floor of 30 a second"))))
    (testing "even-out follows x at once and eases y"
      (let [c (upd :even-out {:x 600.0
                              :y 380.0} 0.1)
            flat (upd :even-out {:x 600.0
                                 :y 280.5} 0.1)]
        (is (near? 600.0 (first (:target c))))
        (is (near? 320.0 (second (:target c))))
        (is (near? 280.5 (second (:target flat))) "within a unit it snaps")))
    (testing "push waits in the middle 60 percent of the viewport, then shoves"
      (let [inside (upd :push {:x 410.0
                               :y 280.0} dt)
            gap (/ (* 0.3 vw) z)
            out (upd :push {:x (+ 400.0 gap)
                            :y 280.0} dt)
            far-x (+ 400.0 (/ (* 0.4 vw) z))
            shoved (upd :push {:x far-x
                               :y 280.0} dt)]
        (is (= [400.0 280.0] (:target inside)))
        (is (near? 400.0 (first (:target out))) "exactly on the edge it holds")
        (is (near? (+ 400.0 (/ (* 0.1 vw) z)) (first (:target shoved))))
        (is (near? 280.0 (second (:target shoved))))))))

(deftest push-camera-moves-on-all-four-edges
  (let [[_ _ vw vh] (:field d)
        z (:base-zoom d)
        cam0 {:offset [(* 0.5 vw) (* 0.5 vh)]
              :target [400.0 280.0]}
        o 30.0
        ;; The player at screen position [sx sy] in the field, in world units.
        world (fn [sx sy] {:x (+ 400.0 (/ (- sx (* 0.5 vw)) z))
                           :y (+ 280.0 (/ (- sy (* 0.5 vh)) z))})
        target (fn [sx sy] (:target (pf/update-camera :push cam0 (world sx sy) dt d)))]
    (testing "left: o pixels past 20 percent moves the target left by o over the zoom"
      (let [[tx ty] (target (- (* 0.2 vw) o) (* 0.5 vh))]
        (is (near? (- 400.0 (/ o z)) tx))
        (is (near? 280.0 ty))))
    (testing "right"
      (let [[tx ty] (target (+ (* 0.8 vw) o) (* 0.5 vh))]
        (is (near? (+ 400.0 (/ o z)) tx))
        (is (near? 280.0 ty))))
    (testing "top"
      (let [[tx ty] (target (* 0.5 vw) (- (* 0.2 vh) o))]
        (is (near? 400.0 tx))
        (is (near? (- 280.0 (/ o z)) ty))))
    (testing "bottom"
      (let [[tx ty] (target (* 0.5 vw) (+ (* 0.8 vh) o))]
        (is (near? 400.0 tx))
        (is (near? (+ 280.0 (/ o z)) ty))))))

(deftest the-camera-button-cycles-five-modes
  (is (= 5 (count pf/modes)))
  (let [press (fn [s] (-> s (step [:camera]) (step [])))
        seen (take 6 (iterate press start))]
    (is (= [0 1 2 3 4 0] (map :mode seen)))
    (testing "holding does not repeat"
      (let [held (-> start (step [:camera]) (step [:camera]) (step [:camera]))]
        (is (= 1 (:mode held)))))
    (testing "a finger sliding on from another button is a press"
      (is (= 1 (:mode (-> start (step [:left]) (step [:left :camera]))))))
    (testing "the camera state follows the mode, so the label can name it"
      (is (= (first (nth pf/modes 2)) (pf/mode-id (:mode (press (press start)))))))))

(deftest reset-acts-on-a-press-edge
  (let [moved (-> (on-floor start 700.0)
                  (assoc :mode 3)
                  (step [:right]))
        reset (step moved [:reset])
        held (step reset [:reset])]
    (is (not= (:player start) (:player moved)))
    (is (= 400.0 (get-in reset [:player :x])))
    (is (= 280.0 (get-in reset [:player :y])))
    (is (= [400.0 280.0] (:target reset)))
    (is (= 3 (:mode reset)) "reset keeps the camera mode")
    (testing "a held reset acts once; the player falls on after the first frame"
      (is (not= 280.0 (get-in (step held []) [:player :y])))
      (is (near? (* 400.0 dt) (get-in held [:player :speed]))))))

(deftest buttons-avoid-back
  (doseq [screen screens
          :let [[w h] screen
                geo (pf/geometry {:screen screen})
                [_ back-y _ back-h] gesture/back-region
                [fx fy fw fh] (:field geo)]]
    (testing (str screen)
      (is (= [:left :right :jump :camera :reset] (map :id (:buttons geo))))
      (doseq [{[x y bw bh] :rect} (:buttons geo)]
        (is (>= x 0))
        (is (<= (+ x bw) w))
        (is (>= y (+ back-y back-h)))
        (is (<= (+ y bh) h))
        (is (not (gesture/in-back-region? [(+ x (* 0.5 bw)) (+ y (* 0.5 bh))]))))
      (testing "the field is above the buttons and below Back"
        (is (>= fy (+ back-y back-h)))
        (is (pos? fh))
        (is (<= (+ fy fh) (apply min (map #(second (:rect %)) (:buttons geo)))))
        (is (= [0.0 (double w)] [fx fw])))
      (testing "the buttons do not overlap"
        (doseq [[a b] (partition 2 1 (:buttons geo))
                :let [[ax _ aw _] (:rect a)
                      [bx _ _ _] (:rect b)]]
          (is (<= (+ ax aw) bx)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (pf/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (= 5 (count (:mode-lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) h) s))
      (testing "the mode line sits above the field"
        (let [{:keys [y size]} (first (:lines dims))
              [_ fy _ _] (:field dims)]
          (is (<= (+ y size) fy))))
      (testing "each label fits its button"
        (doseq [{:keys [label label-size label-x]
                 [bx _ bw bh] :rect} (:buttons dims)]
          (is (>= label-x bx) label)
          (is (<= (+ label-x (measure label label-size)) (+ bx bw)) label)
          (is (<= label-size bh) label))))))

(deftest the-world-is-the-originals
  (is (= 5 (count pf/env-items)))
  (is (= [[300.0 200.0 400.0 10.0 true]
          [250.0 300.0 100.0 10.0 true]
          [650.0 300.0 100.0 10.0 true]]
         (mapv #(subvec (vec (butlast %)) 0 5) (drop 2 pf/env-items)))))

(deftest first-frame-draws
  (testing "the state after init alone has everything a draw reads"
    (doseq [screen screens
            :let [metrics {:screen screen}
                  s (first ((:init (pf/scene)) {:metrics metrics}))
                  dims (pf/dimensions metrics measure)
                  c (pf/camera s dims)
                  [fx fy fw fh] (:field dims)]]
      (is (every? number? ((juxt :x :y :speed) (:player s))) (str screen))
      (is (= 0 (:mode s)))
      (is (set? (:held s)))
      (is (pos? (:zoom c)))
      (is (= [(+ fx (* 0.5 fw)) (+ fy (* 0.5 fh))] (:offset c)))
      (is (= [400.0 280.0] (:target c)))
      (is (some? (nth pf/modes (:mode s))))
      (testing "the player starts on screen, inside the field"
        (let [[sx sy] (cam/world->screen c [400.0 280.0])]
          (is (<= fx sx (+ fx fw)))
          (is (<= fy sy (+ fy fh))))))))
