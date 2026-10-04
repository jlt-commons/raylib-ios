(ns net.b12n.raylib-ios.scenes.vpscaling-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.vpscaling :as vp]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (vp/geometry m))
(def start (first ((:init (vp/scene)) {:metrics m})))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-6))
(defn- centre [[x y w h]] [(+ x (* 0.5 w)) (+ y (* 0.5 h))])

(defn- step
  ([state phase pos] (step state m phase pos))
  ([state metrics phase pos]
   (vp/advance state {:metrics metrics
                      :delta-seconds 0.0
                      :pointer {:phase phase
                                :position pos}})))

(defn- tap [state pos] (-> state (step :press pos) (step :down pos) (step :release pos)))

(defn- drag
  "A press on `from`, a hold on each of `path` and a release."
  [state from path]
  (as-> state s
    (step s :press from)
    (reduce #(step %1 :down %2) s path)
    (step s :release (last path))))

(defn- handle-centre [state dims] (centre (vp/handle state dims)))

(defn- with-game
  "`start` showing game size index `res` under policy `vtype`, in a window of `w` by `h`."
  [res vtype w h]
  (assoc start :res res :vtype vtype :win [w h]))

;; The original's compute-rects (viewport_scaling.clj lines 21-71), run on each
;; window and game size below. Row: [[sw sh gw gh] vtype [src-w src-h dx dy dw dh]],
;; src-h negative as the original's :sh is.
(def original-rects
  [[[800 450 64 64] 0 [64 -64 176 1 448 448]]
   [[800 450 64 64] 1 [113 -64 2 0 794 450]]
   [[800 450 64 64] 2 [64 -36 0 0 800 450]]
   [[800 450 64 64] 3 [64 -64 175 0 450 450]]
   [[800 450 64 64] 4 [113 -64 2 0 794 450]]
   [[800 450 64 64] 5 [64 -36 0 0 800 450]]
   [[800 450 256 240] 0 [256 -240 272 105 256 240]]
   [[800 450 256 240] 1 [426 -240 0 0 798 450]]
   [[800 450 256 240] 2 [256 -144 0 0 800 450]]
   [[800 450 256 240] 3 [256 -240 160 0 480 450]]
   [[800 450 256 240] 4 [426 -240 0 0 798 450]]
   [[800 450 256 240] 5 [256 -144 0 0 800 450]]
   [[800 450 320 180] 0 [320 -180 80 45 640 360]]
   [[800 450 320 180] 1 [320 -180 0 0 800 450]]
   [[800 450 320 180] 2 [320 -180 0 0 800 450]]
   [[800 450 320 180] 3 [320 -180 0 0 800 450]]
   [[800 450 320 180] 4 [320 -180 0 0 800 450]]
   [[800 450 320 180] 5 [320 -180 0 0 800 450]]
   [[501 300 320 180] 0 [320 -180 90 60 320 180]]
   [[501 300 320 180] 1 [300 -180 0 0 500 300]]
   [[501 300 320 180] 2 [320 -191 0 0 501 299]]
   [[501 300 320 180] 3 [320 -180 0 9 501 281]]
   [[501 300 320 180] 4 [300 -180 0 0 500 300]]
   [[501 300 320 180] 5 [320 -191 0 0 501 299]]
   [[300 700 64 64] 0 [64 -64 22 222 256 256]]
   [[300 700 64 64] 1 [27 -64 2 0 295 700]]
   [[300 700 64 64] 2 [64 -149 0 0 300 698]]
   [[300 700 64 64] 3 [64 -64 0 200 300 300]]
   [[300 700 64 64] 4 [27 -64 2 0 295 700]]
   [[300 700 64 64] 5 [64 -149 0 0 300 698]]
   [[300 700 256 240] 0 [256 -240 22 230 256 240]]
   [[300 700 256 240] 1 [102 -240 1 0 297 700]]
   [[300 700 256 240] 2 [256 -597 0 0 300 699]]
   [[300 700 256 240] 3 [256 -240 0 209 300 281]]
   [[300 700 256 240] 4 [102 -240 1 0 297 700]]
   [[300 700 256 240] 5 [256 -597 0 0 300 699]]
   [[700 300 3840 2160] 0 [3840 -2160 350 150 0 0]]
   [[700 300 3840 2160] 1 [5040 -2160 0 0 700 300]]
   [[700 300 3840 2160] 2 [3840 -1645 0 0 700 299]]
   [[700 300 3840 2160] 3 [3840 -2160 83 0 533 300]]
   [[700 300 3840 2160] 4 [5040 -2160 0 0 700 300]]
   [[700 300 3840 2160] 5 [3840 -1645 0 0 700 299]]
   [[333 777 320 180] 0 [320 -180 6 298 320 180]]
   [[333 777 320 180] 1 [77 -180 0 0 332 777]]
   [[333 777 320 180] 2 [320 -746 0 0 333 776]]
   [[333 777 320 180] 3 [320 -180 0 294 333 187]]
   [[333 777 320 180] 4 [77 -180 0 0 332 777]]
   [[333 777 320 180] 5 [320 -746 0 0 333 776]]])

(deftest each-policy-scales-as-the-original
  (testing "the rects match the original's, in all six policies, on eight window and game sizes"
    (is (= 48 (count original-rects)))
    (doseq [[[sw sh gw gh] v [src-w src-h dx dy dw dh]] original-rects
            :let [r (vp/rects v sw sh gw gh)]]
      (testing (str [sw sh gw gh] " policy " v)
        (is (= [(double src-w) (double src-h) (double dx) (double dy) (double dw) (double dh)]
               ((juxt :sw :sh :dx :dy :dw :dh) r)))
        (is (= [0.0 (if (#{0 3} v) (double gh) 0.0)] [(:sx r) (:sy r)])
            "the source starts at 0 and, for the aspect policies, at gh (the flip)"))))
  (testing "hand-computed: 800 by 450 with a 64 by 64 game"
    ;; 0: rr = min(quot 800 64 = 12, quot 450 64 = 7) = 7, so 448 square, dx (800 - 448) / 2 = 176, dy (450 - 448) / 2 = 1.
    (is (= [176.0 1.0 448.0 448.0] ((juxt :dx :dy :dw :dh) (vp/rects 0 800 450 64 64))))
    ;; 3: rr = min(12.5, 7.03125) = 7.03125, so 450 square, dx (800 - 450) / 2 = 175.
    (is (= [175.0 0.0 450.0 450.0] ((juxt :dx :dy :dw :dh) (vp/rects 3 800 450 64 64))))
    ;; 2: rr = 800 / 64 = 12.5, srch = 450 / 12.5 = 36, so the game is 64 by 36 and fills the window.
    (is (= [64.0 -36.0 800.0 450.0] ((juxt :sw :sh :dw :dh) (vp/rects 2 800 450 64 64))))
    ;; 1: rr = 450 / 64 = 7.03125, srcw = int(800 / 7.03125 = 113.78) = 113, dw = int(113 * 7.03125 = 794.53) = 794.
    (is (= [113.0 794.0 2.0] ((juxt :sw :dw :dx) (vp/rects 1 800 450 64 64)))))
  (testing "the integer policies snap and the others do not: 800 by 450 with a 320 by 180 game is x2 and x2.5"
    (is (= [640.0 360.0] ((juxt :dw :dh) (vp/rects 0 800 450 320 180))))
    (is (= [800.0 450.0] ((juxt :dw :dh) (vp/rects 3 800 450 320 180)))))
  (testing "a game bigger than the window gives an empty integer picture, as the original's quot gives rr 0"
    (is (= [0.0 0.0] ((juxt :dw :dh) (vp/rects 0 700 300 3840 2160)))))
  (testing "the plan puts the picture at the window's origin plus dx, dy, scaled by dest over source"
    (doseq [[sw sh gw gh v res] [[800 450 64 64 1 0] [800 450 256 240 3 1] [300 700 64 64 2 0] [501 300 320 180 5 2]]
            :let [s (with-game res v sw sh)
                  r (vp/rects v sw sh gw gh)
                  [ox oy] (:origin d)
                  plan (vp/plan s d)]]
      (testing (str [sw sh gw gh] v)
        (is (= [(+ ox (:dx r)) (+ oy (:dy r)) (:dw r) (:dh r)] (:dest plan)))
        (is (= [(:sw r) (- (:sh r))] (:source plan)))
        (is (near? (/ (:dw r) (:sw r)) (first (:scale plan))))
        (is (near? (/ (:dh r) (- (:sh r))) (second (:scale plan)))))))
  (testing "the readouts are the original's strings"
    (let [text (fn [s] (mapv :s (vp/readouts s (vp/dimensions m measure))))]
      (is (= ["Window Resolution: 800 x 450"
              "Game Resolution: 64 x 64"
              "Type: KEEP_ASPECT_INTEGER"
              "Scale ratio: 7.00 x 7.00"
              "Source size: 64.00 x 64.00"
              "Destination size: 448.00 x 448.00"]
             (text (with-game 0 0 800 450))))
      (is (= ["Window Resolution: 800 x 450"
              "Game Resolution: 64 x 64"
              "Type: KEEP_HEIGHT_INTEGER"
              "Scale ratio: 7.03 x 7.03"
              "Source size: 113.00 x 64.00"
              "Destination size: 794.00 x 450.00"]
             (text (with-game 0 1 800 450)))
          "794 / 113 = 7.0265 and 450 / 64 = 7.03125")
      (is (= ["Window Resolution: 800 x 450"
              "Game Resolution: 256 x 240"
              "Type: KEEP_WIDTH"
              "Scale ratio: 3.13 x 3.13"
              "Source size: 256.00 x 144.00"
              "Destination size: 800.00 x 450.00"]
             (text (with-game 1 5 800 450)))
          "800 / 256 = 3.125 and 450 / 144 = 3.125, which %.2f rounds up")
      (is (= "Scale ratio: INVALID" (:s (nth (vp/readouts (with-game 3 0 700 300) (vp/dimensions m measure)) 3)))
          "a zero-width picture reads INVALID, as the original does under 0.001"))))

(deftest touch-maps-back-through-the-policy
  ;; The original: tmx = (mx - dx) * (sw / dw), tmy = (my - dy) * (sw / dw), mx my
  ;; the mouse in the window.
  (let [[ox oy] (:origin d)
        at (fn [s mx my] (vp/game-point s d [(+ ox mx) (+ oy my)]))]
    (testing "type 0, 800 by 450, 64 by 64 game (dx 176, dy 1, scale 7): the picture's centre is the game's"
      (let [s (with-game 0 0 800 450)
            [x y] (at s 400 225)]
        (is (near? 32.0 x))
        (is (near? 32.0 y))
        (is (every? true? (map near? [0.0 0.0] (at s 176 1))) "the picture's corner is the game's origin")
        (is (every? true? (map near? [64.0 64.0] (at s 624 449))))))
    (testing "type 3, dx 175, dw 450"
      (let [[x y] (at (with-game 0 3 800 450) 400 225)]
        (is (near? 32.0 x))
        (is (near? 32.0 y))))
    (testing "type 2 scales both axes by sw / dw = 64 / 800, the original's ratio-x for y as well"
      (let [[x y] (at (with-game 0 2 800 450) 400 225)]
        (is (near? 32.0 x))
        (is (near? 18.0 y) "225 * 0.08 = 18, the middle of the 64 by 36 game")))
    (testing "type 1 (dx 2, dw 794 over 113): ratio is 113 / 794"
      (let [[x y] (at (with-game 0 1 800 450) 402 225)]
        (is (near? (* 400.0 (/ 113.0 794.0)) x))
        (is (near? (* 225.0 (/ 113.0 794.0)) y))))
    (testing "the 256 by 240 game at 800 by 450 under type 0 is shown 1 to 1, so a touch is a game pixel"
      (let [[x y] (at (with-game 1 0 800 450) (+ 272 100) (+ 105 50))]
        (is (near? 100.0 x))
        (is (near? 50.0 y))))
    (testing "a touch outside the picture maps outside the game, as the original's mouse does"
      (let [[x y] (at (with-game 0 0 800 450) 100 225)]
        (is (neg? x))
        (is (near? 32.0 y))))
    (testing "an empty picture has no mapping"
      (is (nil? (at (with-game 3 0 700 300) 100 100)))))
  (testing "a press in the field puts the circle there, and it stays after the finger lifts"
    (let [[ox oy] (:origin d)
          s (-> (with-game 0 0 800 450)
                (assoc :screen (:screen m))
                (step :press [(+ ox 400) (+ oy 225)])
                (step :down [(+ ox 300) (+ oy 100)])
                (step :release [0 0]))
          [tx ty] (vp/game-point s d (:mouse s))
          plan (vp/plan s d)]
      (is (= [(+ ox 300) (+ oy 100)] (:mouse s)) "the last :down, never the :release position")
      (is (= [(long tx) (long ty)] (:circle plan)) "(int tmx) (int tmy)")
      (is (= [(long (/ (- 300 176) 7.0)) (long (/ (- 100 1) 7.0))] (:circle plan))
          "(300 - 176) / 7 = 17.7 and (100 - 1) / 7 = 14.1, truncated")))
  (testing "a press under Back or on a button does not move the circle"
    (let [before (:mouse start)]
      (is (= before (:mouse (step start :press [100.0 60.0]))))
      (is (= before (:mouse (step start :press (centre (:res-next d))))))))
  (testing "no circle is drawn when the picture is empty"
    (is (nil? (:circle (vp/plan (with-game 3 0 700 300) d))))
    (is (nil? (:dest (vp/plan (with-game 3 0 700 300) d))))))

(deftest the-corner-drag-resizes
  (let [[ox oy] (:origin d)
        [mw mh] (:max-win d)
        [nw nh] (:min-win d)
        s0 (assoc start :win [600 500])
        hc (handle-centre s0 d)
        grown (drag s0 hc [[(+ (first hc) 50) (+ (second hc) 30)] [(+ (first hc) 100) (+ (second hc) 80)]])]
    (testing "the handle is the window's bottom-right corner, inside it"
      (let [[hx hy hw hh] (vp/handle s0 d)]
        (is (near? (+ ox 600) (+ hx hw)))
        (is (near? (+ oy 500) (+ hy hh)))))
    (testing "dragging the handle by dx, dy grows the window by exactly dx, dy and does not jump to the finger"
      (is (= [700 580] (:win grown)))
      (is (= [600 500] (:win (step s0 :press hc))) "the press alone changes nothing"))
    (testing "the top-left corner stays where it was"
      (is (= (take 2 (vp/window s0 d)) (take 2 (vp/window grown d)))))
    (testing "a drag past the field clamps to the field, less a margin each side"
      (let [big (drag s0 hc [[5000.0 5000.0]])]
        (is (= [mw mh] (:win big)))
        (is (<= (+ ox mw) (first (:screen m))) "and inside the screen")))
    (testing "a drag to nothing clamps to the minimum, which still holds the handle"
      (let [small (drag s0 hc [[0.0 0.0]])]
        (is (= [nw nh] (:win small)))
        (is (>= nw (* 2 (:handle-size d))))))
    (testing "the release ends it: a later :down elsewhere does not resize"
      (let [after (-> grown (step :down [(+ ox 10) (+ oy 10)]))]
        (is (= (:win grown) (:win after)))))
    (testing "a press that starts elsewhere and slides onto the handle does not resize"
      (let [slid (-> s0 (step :press [(+ ox 20) (+ oy 20)]) (step :down hc) (step :down [(+ (first hc) 40) (+ (second hc) 40)]))]
        (is (= [600 500] (:win slid)))))
    (testing "the circle does not move while the corner is dragged"
      (is (= (:mouse s0) (:mouse grown))))
    (testing "the picture follows the new size (policy 3, 64 by 64: the largest square that fits)"
      (let [s (assoc grown :vtype 3)
            [_ _ dw dh] (:dest (vp/plan s d))]
        (is (= 580.0 dw))
        (is (= 580.0 dh))))
    (testing "a rotation of the phone ends the drag and starts a fresh window in the new field"
      (let [mid (step s0 :press hc)
            turned (step mid {:screen [2334 1206]} :down [900.0 400.0])]
        (is (= [2334 1206] (:screen turned)))
        (is (= (:win (first ((:init (vp/scene)) {:metrics {:screen [2334 1206]}}))) (:win turned)))))))

(deftest buttons-avoid-back
  (doseq [screen screens
          :let [[w _] screen
                g (vp/geometry {:screen screen})
                [_ back-y _ back-h] gesture/back-region
                [_ fy _ _] (:field g)]]
    (testing (str screen)
      (doseq [k [:res-prev :res-next :type-prev :type-next]
              :let [[bx by bw bh] (k g)]]
        (is (>= by (+ back-y back-h)) (str k " below Back"))
        (is (>= bx 0))
        (is (<= (+ bx bw) w))
        (is (<= (+ by bh) fy) "above the field")
        (is (>= bw (* 2 (:size g))) "big enough for a finger"))
      (let [[px _ pw _] (:res-prev g)
            [nx _ nw _] (:res-next g)]
        (is (<= (+ px pw) nx))
        (is (<= (+ nx nw) w)))
      (let [[_ ry _ rh] (:res-prev g)
            [_ ty _ _] (:type-prev g)]
        (is (<= (+ ry rh) ty) "the two rows do not touch"))))
  (testing "resolutions cycle 64x64, 256x240, 320x180, 3840x2160 and wrap"
    (is (= [[64 64] [256 240] [320 180] [3840 2160]] vp/resolutions))
    (let [nxt (centre (:res-next d))
          prv (centre (:res-prev d))
          once (tap start nxt)
          back (tap start prv)]
      (is (= 0 (:res start)))
      (is (= 1 (:res once)))
      (is (= 3 (:res back)) "< from the first wraps to the last")
      (is (= 0 (:res (reduce (fn [s _] (tap s nxt)) start (range 4)))))
      (is (= 2 (:res (tap once nxt))))
      (is (= (:vtype start) (:vtype once)) "the resolution buttons leave the policy alone")))
  (testing "policies cycle through six and wrap"
    (let [nxt (centre (:type-next d))
          prv (centre (:type-prev d))]
      (is (= 0 (:vtype start)))
      (is (= 1 (:vtype (tap start nxt))))
      (is (= 5 (:vtype (tap start prv))))
      (is (= 0 (:vtype (reduce (fn [s _] (tap s nxt)) start (range 6)))))
      (is (= 0 (:res (tap start nxt))))))
  (testing "a tap elsewhere or under Back changes nothing, and a window drag is not a tap"
    (let [nowhere (tap start [600.0 2200.0])
          back (tap start [100.0 60.0])
          hc (handle-centre start d)
          dragged (drag start hc [[(+ (first hc) 3) (+ (second hc) 3)]])]
      (doseq [s [nowhere back dragged]]
        (is (= [0 0] ((juxt :res :vtype) s))))))
  (testing "a press that slides onto a button and lifts is not a tap on it"
    (let [nxt (centre (:res-next d))
          slid (-> start (step :press [600.0 2200.0]) (step :down nxt) (step :release nxt))]
      (is (= 0 (:res slid)))))
  (testing "the buttons never change the window"
    (is (= (:win start) (:win (tap start (centre (:res-next d))))))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                s (first ((:init (vp/scene)) {:metrics metrics}))
                dims (vp/dimensions metrics measure)
                [_ h] screen
                [wx wy ww wh] (vp/window s dims)
                [fx fy fw fh] (:field dims)
                [mw mh] (:max-win dims)
                plan (vp/plan s dims)
                [dx dy dw dh] (:dest plan)
                [cx cy] (:circle plan)
                rs (vp/readouts s dims)]]
    (testing (str screen)
      (is (= [0 0] ((juxt :res :vtype) s)) "64 by 64, KEEP_ASPECT_INTEGER")
      (is (<= (Math/abs (- (/ ww wh) (/ 800.0 450.0))) 0.02) "the original's 16:9")
      (is (<= ww (* 0.8 mw)) "80% of what the field allows, so the handle has room to grow")
      (is (<= wh (* 0.8 mh)))
      (is (and (>= wx fx) (>= wy fy) (<= (+ wx ww) (+ fx fw)) (<= (+ wy wh) (+ fy fh))) "in the field")
      (is (<= (+ wy wh) h))
      (is (and (pos? dw) (pos? dh)) "there is a picture")
      (is (and (>= dx wx) (>= dy wy) (<= (+ dx dw) (+ wx ww)) (<= (+ dy dh) (+ wy wh))) "inside the window")
      (is (= dw dh) "a square game in a wider window: pillarbox")
      (is (= (:win s) [(long ww) (long wh)]))
      (is (= 64.0 (:sw (vp/rects 0 ww wh 64 64))))
      (is (= [64.0 64.0] (:source plan)))
      (is (and (some? cx) (<= 0 cx 64) (<= 0 cy 64)) "the circle starts inside the game")
      (is (every? #(and (number? (:x %)) (number? (:y %)) (pos? (:size %))) rs))
      (is (= 6 (count rs)))
      (is (= (str "Window Resolution: " (long ww) " x " (long wh)) (:s (first rs))))
      (is (= "Game Resolution: 64 x 64" (:s (second rs))))
      (is (= "Type: KEEP_ASPECT_INTEGER" (:s (nth rs 2))))
      (is (re-find #"^Scale ratio: \d+\.\d\d x \d+\.\d\d$" (:s (nth rs 3))))
      (is (= (str "Destination size: " (long dw) ".00 x " (long dh) ".00") (:s (nth rs 5))))
      (let [[hx hy hw hh] (vp/handle s dims)]
        (is (and (>= hx wx) (>= hy wy) (<= (+ hx hw) (+ wx ww)) (<= (+ hy hh) (+ wy wh)))
            "the handle is inside the window")))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (vp/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [fx fy fw fh] (:field dims)
                [mw mh] (:max-win dims)
                [nw nh] (:min-win dims)]]
    (testing (str screen)
      (testing "every combination of game size and policy, in the smallest, the starting and the largest window"
        (doseq [res (range 4) v (range 6) [ww wh] [[nw nh] (:start-win dims) [mw mh]]
                :let [s (assoc start :res res :vtype v :win [ww wh] :screen screen)
                      rs (vp/readouts s dims)]]
          (is (= 6 (count rs)))
          (doseq [{:keys [s x y size]} rs]
            (is (>= x 0) s)
            (is (<= (+ x (measure s size)) w) s)
            (is (>= y (+ back-y back-h)) s)
            (is (<= (+ y size) fy) s))
          (testing "the game and type lines sit between their arrows"
            (doseq [[line prev nxt] [[(nth rs 1) :res-prev :res-next] [(nth rs 2) :type-prev :type-next]]
                    :let [[px _ pw _] (prev dims)
                          [nx _ _ _] (nxt dims)]]
              (is (>= (:x line) (+ px pw)) (:s line))
              (is (<= (+ (:x line) (measure (:s line) (:size line))) nx) (:s line))))))
      (testing "the arrows sit in their buttons"
        (doseq [[k label] [[:res-prev :res-prev] [:res-next :res-next] [:type-prev :type-prev] [:type-next :type-next]]
                :let [[bx by bw bh] (k dims)
                      {:keys [s x y size]} (get-in dims [:arrows label])]]
          (is (>= x bx))
          (is (<= (+ x (measure s size)) (+ bx bw)))
          (is (>= y by))
          (is (<= (+ y size) (+ by bh)))))
      (testing "the window and its field fit the screen"
        (is (<= (+ fy fh) h))
        (is (<= (+ fx fw) w))
        (is (<= (first (:origin dims)) fw))))))

(deftest keep-width-centres-vertically
  ;; The original on 801 by 450 with the 64 by 64 game, run through its own
  ;; compute-rects: rr 12.515625, srch 35, dy (450 - 438.05) / 2 = 5, dh 438.
  (doseq [v [2 5]
          :let [r (vp/rects v 801 450 64 64)]]
    (is (= [64.0 -35.0 0.0 5.0 801.0 438.0] ((juxt :sw :sh :dx :dy :dw :dh) r)) (str "policy " v))))

(deftest the-invalid-threshold-is-one-thousandth
  ;; Not reachable by dragging (the smallest window is 96 pixels), so the state is
  ;; built directly. 4K game, policy 3: 3 by 2 gives dw 3 and dh 1, ratios 0.00078
  ;; and 0.00046, under 0.001. 8 by 5 gives dw 8 and dh 4, ratios 0.0021 and 0.0019.
  (let [dims (vp/dimensions m measure)
        scale-line (fn [w h] (:s (nth (vp/readouts (with-game 3 3 w h) dims) 3)))]
    (is (= "Scale ratio: INVALID" (scale-line 3 2)))
    (is (= "Scale ratio: 0.00 x 0.00" (scale-line 8 5)))))
