(ns raylib.scenes.letterbox-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.letterbox :as lb]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (lb/geometry m))
(def start (first ((:init (lb/scene)) {:metrics m})))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-6))
(defn- centre [[x y w h]] [(+ x (* 0.5 w)) (+ y (* 0.5 h))])

(defn- step
  ([state phase pos] (step state phase pos 0.0))
  ([state phase pos dt] (step state m phase pos dt))
  ([state metrics phase pos dt]
   (lb/advance state {:metrics metrics
                      :delta-seconds dt
                      :pointer {:phase phase
                                :position pos}})))

(defn- drag [state from path]
  (as-> state s
    (step s :press from)
    (reduce #(step %1 :down %2) s path)
    (step s :release (last path))))

;; The original's `fit` and blit (window_letterbox.clj lines 26-33 and 97-102),
;; run on each window below. Row: [w h] s ox oy [blit-x blit-y blit-w blit-h].
(def original-fit
  [[[800 450] 1.25 100.0 0.0 [100 0 600 450]]
   [[450 800] 0.9375 0.0 231.25 [0 231 450 337]]
   [[480 360] 1.0 0.0 0.0 [0 0 480 360]]
   [[600 600] 1.25 0.0 75.0 [0 75 600 450]]
   [[960 360] 1.0 240.0 0.0 [240 0 480 360]]
   [[1000 300] 0.8333333333333334 300.0 0.0 [300 0 400 300]]
   [[333 777] 0.69375 0.0 263.625 [0 263 333 249]]])

(deftest bars-match-the-originals-letterbox
  (testing "fit is the original's, on seven windows"
    (doseq [[[w h] s ox oy blit] original-fit
            :let [[fs fox foy] (lb/fit w h)
                  b (lb/blit w h)]]
      (testing (str [w h])
        (is (near? s fs))
        (is (near? ox fox))
        (is (near? oy foy))
        (is (= blit ((juxt :x :y :w :h) b)))
        (is (= [(long ox) (long oy)] ((juxt :bar-x :bar-y) b)) "the bars, as the readout reports them"))))
  (testing "800 by 450 is 4:3 inside 16:9: a bar of 100 each side, none above or below"
    (let [b (lb/blit 800 450)]
      (is (= [100 0] ((juxt :bar-x :bar-y) b)))
      (is (= 800 (+ (:x b) (:w b) (:bar-x b))) "the bars and the picture fill the window across")))
  (testing "450 by 800 is the other way: bars above and below"
    (is (= [0 231] ((juxt :bar-x :bar-y) (lb/blit 450 800)))))
  (testing "the readout is the original's, bars included"
    (let [dims (lb/dimensions m measure)
          text (fn [w h] (mapv :s (lb/readouts (assoc start :win [w h]) dims)))]
      (is (= "window 800x450   scale 1.25   bars 100x0" (first (text 800 450))))
      (is (= "window 450x800   scale 0.94   bars 0x231" (first (text 450 800))) "0.9375 rounds up to 0.94")
      (is (= "window 480x360   scale 1.00   bars 0x0" (first (text 480 360))))
      (is (= "window 1000x300   scale 0.83   bars 300x0" (first (text 1000 300))))
      (is (= "window 333x777   scale 0.69   bars 0x263" (first (text 333 777))))))
  (testing "the plan blits at the window's origin plus the original's int offset, scaled by int size over 480 by 360"
    (let [[ox oy] (:origin d)]
      (doseq [[[w h] _ _ _ [bx by bw bh]] original-fit
              :let [plan (lb/plan (assoc start :win [w h]) d)]]
        (testing (str [w h])
          (is (= [(+ ox bx) (+ oy by) (double bw) (double bh)] (:blit plan)))
          (is (near? (/ bw 480.0) (first (:scale plan))))
          (is (near? (/ bh 360.0) (second (:scale plan)))))))))

(deftest touch-maps-into-the-virtual-scene
  ;; The original: mx = (int (/ (- mouse ox) s)), my likewise, ox oy the offsets.
  (let [[ox oy] (:origin d)
        at (fn [w h x y] (lb/virtual-point (assoc start :win [w h]) d [(+ ox x) (+ oy y)]))]
    (testing "800 by 450 (s 1.25, ox 100): the middle of the window is the middle of the scene"
      (is (= [240 180] (at 800 450 400 225)))
      (is (= [0 0] (at 800 450 100 0)))
      (is (= [480 360] (at 800 450 700 450))))
    (testing "450 by 800 (s 0.9375, oy 231.25)"
      (is (= [240 180] (at 450 800 225 400))))
    (testing "a touch in a bar maps outside the scene, as the original's mouse does"
      (is (= [-40 180] (at 800 450 50 225))))
    (testing "the cast truncates toward zero, so just left of the picture is still pixel 0"
      (is (= [0 0] (at 800 450 99.5 0)))))
  (testing "a press puts the crosshair at the last :down, never the :release position"
    (let [[ox oy] (:origin d)
          s (-> (assoc start :win [800 450])
                (step :press [(+ ox 400) (+ oy 225)])
                (step :down [(+ ox 200) (+ oy 100)])
                (step :release [0 0]))]
      (is (= [(+ ox 200) (+ oy 100)] (:mouse s)))
      (is (= [80 80] (:cross (lb/plan s d))) "(200 - 100) / 1.25 and 100 / 1.25")))
  (testing "the crosshair is drawn only over the picture"
    (let [[ox oy] (:origin d)
          s (assoc start :win [800 450])
          over (assoc s :mouse [(+ ox 400) (+ oy 225)])
          bar (assoc s :mouse [(+ ox 50) (+ oy 225)])]
      (is (= [240 180] (:cross (lb/plan over d))))
      (is (nil? (:cross (lb/plan bar d))))
      (is (= [] (:cross-lines (lb/picture bar nil))))
      (is (= [[230 180 250 180] [240 170 240 190]] (:cross-lines (lb/picture over [240 180]))) "10 either side")))
  (testing "a press under Back or on the handle does not move the crosshair"
    (let [before (:mouse start)
          hc (centre (lb/handle start d))]
      (is (= before (:mouse (step start :press [100.0 60.0]))))
      (is (= before (:mouse (step start :press hc)))))))

(deftest the-corner-drag-resizes
  (let [[ox oy] (:origin d)
        [mw mh] (:max-win d)
        [nw nh] (:min-win d)
        s0 (assoc start :win [600 500])
        hc (centre (lb/handle s0 d))
        grown (drag s0 hc [[(+ (first hc) 50) (+ (second hc) 30)] [(+ (first hc) 100) (+ (second hc) 80)]])]
    (testing "the handle is inside the window's bottom-right corner"
      (let [[hx hy hw hh] (lb/handle s0 d)]
        (is (near? (+ ox 600) (+ hx hw)))
        (is (near? (+ oy 500) (+ hy hh)))))
    (testing "a drag grows the window by the finger's travel and the top-left stays"
      (is (= [700 580] (:win grown)))
      (is (= [600 500] (:win (step s0 :press hc))))
      (is (= (take 2 (lb/window s0 d)) (take 2 (lb/window grown d)))))
    (testing "clamped to the field above and the minimum below"
      (is (= [mw mh] (:win (drag s0 hc [[5000.0 5000.0]]))))
      (is (= [nw nh] (:win (drag s0 hc [[0.0 0.0]]))))
      (is (>= nw (* 2 (:handle-size d)))))
    (testing "the crosshair stays and the picture follows: 700 by 580 is s 1.4583, 480 by 360 at 700 by 525"
      (is (= (:mouse s0) (:mouse grown)))
      (let [[_ _ bw bh] (:blit (lb/plan grown d))]
        (is (= [700.0 525.0] [bw bh]))))
    (testing "a press that slides onto the handle does not resize"
      (let [slid (-> s0 (step :press [(+ ox 20) (+ oy 20)]) (step :down hc) (step :down [(+ (first hc) 40) (+ (second hc) 40)]))]
        (is (= [600 500] (:win slid)))))
    (testing "a rotation starts afresh"
      (let [turned (step (step s0 :press hc) {:screen [2334 1206]} :down [900.0 400.0] 0.0)]
        (is (= (:win (first ((:init (lb/scene)) {:metrics {:screen [2334 1206]}}))) (:win turned)))))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                s (first ((:init (lb/scene)) {:metrics metrics}))
                dims (lb/dimensions metrics measure)
                [wx wy ww wh] (lb/window s dims)
                [fx fy fw fh] (:field dims)
                [mw mh] (:max-win dims)
                plan (lb/plan s dims)
                [bx by bw bh] (:blit plan)
                pic (lb/picture s (:cross plan))
                rs (lb/readouts s dims)]]
    (testing (str screen)
      (is (<= (Math/abs (- (/ ww wh) (/ 800.0 450.0))) 0.02) "the original's 16:9")
      (is (and (<= ww (* 0.8 mw)) (<= wh (* 0.8 mh))))
      (is (and (>= wx fx) (>= wy fy) (<= (+ wx ww) (+ fx fw)) (<= (+ wy wh) (+ fy fh))) "in the field")
      (is (and (>= bx wx) (>= by wy) (<= (+ bx bw) (+ wx ww)) (<= (+ by bh) (+ wy wh))) "the picture is in the window")
      (is (> (- bx wx) 0) "4:3 in 16:9: the bars are there from the first frame")
      (is (= 480 (:w pic)))
      (is (= [(+ 0.0 (/ bw 480.0)) (+ 0.0 (/ bh 360.0))] (:scale plan)))
      (is (= [24 28 38 255] (:clear pic)))
      (is (= 24 (count (:blocks pic))) "12 across the top and 12 across the bottom")
      (is (= [[0 121 241 255] [102 191 255 255]] (map :colour [(nth (:blocks pic) 0) (nth (:blocks pic) 2)])))
      (is (= [{:x 0
               :y 0
               :w 40
               :h 12}
              {:x 0
               :y 348
               :w 40
               :h 12}]
             (map #(select-keys % [:x :y :w :h]) (take 2 (:blocks pic)))))
      (is (= {:x 240
              :y 180
              :radius 26
              :colour [255 203 0 255]}
             (:circle pic)) "the gold circle at t = 0 is in the middle")
      (is (= ["480x360 virtual resolution" "resize the window - this never changes size"] (map :s (:texts pic))))
      (is (= [[20 40 18] [20 68 13]] (map (juxt :x :y :size) (:texts pic))))
      (is (some? (:cross plan)) "the crosshair starts in the middle of the window")
      (is (<= (Math/abs (- 240 (first (:cross plan)))) 1))
      (is (<= (Math/abs (- 180 (second (:cross plan)))) 1))
      (is (= 2 (count (:cross-lines pic))))
      (is (= 2 (count rs)))
      (is (= (str "window " (long ww) "x" (long wh)) (subs (:s (first rs)) 0 (count (str "window " (long ww) "x" (long wh))))))
      (is (= "resizable - drag a corner" (:s (second rs))))
      (is (every? #(and (number? (:x %)) (number? (:y %)) (pos? (:size %))) rs))))
  (testing "the gold circle swings on 90 sin t, with t the sum of the frames"
    (let [s (-> start (step :idle nil 0.5) (step :idle nil (- (/ Math/PI 2) 0.5)))]
      (is (near? (/ Math/PI 2) (:t s)))
      (is (= 330 (:x (:circle (lb/picture s nil)))))
      (is (= 240 (:x (:circle (lb/picture (assoc s :t Math/PI) nil))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w _] screen
                dims (lb/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [_ fy _ _] (:field dims)
                [mw mh] (:max-win dims)
                [nw nh] (:min-win dims)]]
    (testing (str screen)
      (doseq [[ww wh] [[nw nh] (:start-win dims) [mw mh] [mw nh] [nw mh]]
              :let [rs (lb/readouts (assoc start :win [ww wh] :screen screen) dims)]]
        (is (= 2 (count rs)))
        (doseq [{:keys [s x y size]} rs]
          (is (>= x 0) s)
          (is (<= (+ x (measure s size)) w) s)
          (is (>= y (+ back-y back-h)) s)
          (is (<= (+ y size) fy) s)))))
  (testing "the picture's own text fits the 480 by 360 scene"
    (doseq [{:keys [s x size]} (:texts (lb/picture start nil))]
      (is (<= (+ x (measure s size)) 480) s))))
