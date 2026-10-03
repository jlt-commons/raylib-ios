(ns raylib.scenes.pixelperfect-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.camera2d :as cam]
            [raylib.gesture :as gesture]
            [raylib.scenes.pixelperfect :as pp]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (pp/geometry m))
(def start (first ((:init (pp/scene)) {:metrics m})))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-6))
(defn- centre [[x y w h]] [(+ x (* 0.5 w)) (+ y (* 0.5 h))])

(defn- step
  ([state phase pos] (step state m phase pos 0.0))
  ([state phase pos dt] (step state m phase pos dt))
  ([state metrics phase pos dt]
   (pp/advance state {:metrics metrics
                      :delta-seconds dt
                      :pointer {:phase phase
                                :position pos}})))

(defn- tap
  "A press, a hold and a release on `pos`."
  [state pos]
  (-> state
      (step :press pos)
      (step :down pos)
      (step :release pos)))

(defn- at-time
  "`start` with the clock at `t` seconds, as `t` seconds of frames would leave it."
  [t]
  (assoc start :t t :rot (* 60.0 t)))

(defn- trunc [x] (double (long x)))

(deftest zoom-is-the-largest-integer-that-fits
  (doseq [screen screens
          :let [g (pp/geometry {:screen screen})
                [_ _ fw fh] (:field g)
                z (:zoom g)]]
    (testing (str screen)
      (is (integer? z))
      (is (pos? z))
      (is (<= (* 160 z) fw) "160 virtual pixels fit across")
      (is (<= (* 90 z) fh) "and 90 down")
      (is (or (> (* 160 (inc z)) fw) (> (* 90 (inc z)) fh)) "one more would not fit")
      (is (= [(* 160 z) (* 90 z)] (drop 2 (:window g))) "the window is the virtual screen at that zoom")))
  (testing "on the phone, 1206 wide is 7 whole virtual pixels across 160"
    (is (= 7 (:zoom d)))))

(deftest smooth-mode-offsets-by-the-remainder
  ;; The original: wx, wy = (long cx), (long cy); sx, sy = (cx - wx) * RATIO,
  ;; the blit under a Camera2D with that target, so it moves by minus s.
  (let [[ox oy] (let [[x y] (:window d)] [x y])
        r (double (:zoom d))]
    (doseq [t [0.0 0.7 1.0 2.5 4.0 5.5]
            :let [cx (- (* (Math/sin t) 50.0) 10.0)
                  cy (* (Math/cos t) 30.0)
                  wx (trunc cx)
                  wy (trunc cy)
                  state (at-time t)
                  c (pp/camera state d)
                  [px py pw] (pp/picture state d)]]
      (testing (str "t = " t)
        (is (= [wx wy] (:target c)) "the world camera sits on the integer part")
        (is (near? (+ ox px (- (* (- cx wx) r))) (first (:offset c))) "x is shifted by minus the remainder times the ratio")
        (is (near? (+ oy py (- (* (- cy wy) r))) (second (:offset c))))
        (is (near? (/ pw 160.0) (:zoom c)) "the zoom is the picture's width over 160")))
    (testing "t = 4 truncates toward zero, so the remainder is negative"
      (let [cx (- (* (Math/sin 4.0) 50.0) 10.0)
            c (pp/camera (at-time 4.0) d)]
        (is (neg? cx))
        (is (= (trunc cx) (first (:target c))))
        (is (neg? (- cx (first (:target c)))))))
    (testing "turning smoothing off removes the offset and nothing else"
      (let [on (at-time 1.0)
            off (assoc on :smooth? false)
            [px py] (pp/picture on d)]
        (is (= [(+ ox px) (+ oy py)] (:offset (pp/camera off d))))
        (is (= (:target (pp/camera on d)) (:target (pp/camera off d))))
        (is (not= (:offset (pp/camera on d)) (:offset (pp/camera off d))))))))

(deftest pixel-mode-snaps-as-the-original
  (let [dz (fn [t] (:zoom (pp/camera (assoc (at-time t) :smooth? false) d)))
        screen (fn [t p] (cam/world->screen (pp/camera (assoc (at-time t) :smooth? false) d) p))]
    (testing "two instants inside one virtual pixel draw the same picture"
      ;; t = 0.001 and 0.002 both give floor-equal cx and cy
      (is (= (:target (pp/camera (assoc (at-time 0.0001) :smooth? false) d))
             (:target (pp/camera (assoc (at-time 0.0002) :smooth? false) d))))
      (is (= (screen 0.0001 [70.0 35.0]) (screen 0.0002 [70.0 35.0]))))
    (testing "the world moves a whole virtual pixel at a time, which is the zoom on the glass"
      (let [ts (range 0.0 6.3 0.01)
            xs (map #(first (screen % [70.0 35.0])) ts)
            jumps (remove zero? (map - xs (rest xs)))]
        (is (seq jumps))
        (is (every? #(near? (* (Math/round (/ % (dz 0.0))) (dz 0.0)) %) jumps)
            "every jump is a whole number of picture pixels")))
    (testing "a rect's world position is not offset by the remainder"
      (let [c (pp/camera (assoc (at-time 1.0) :smooth? false) d)]
        (is (every? #(= (double (long %)) %) (:target c)))))
    (testing "the three rects are the original's"
      (let [[a b c] (pp/rects {:rot 10.0})]
        (is (= {:x 70.0
                :y 35.0
                :w 20.0
                :h 20.0
                :rotation 10.0
                :color [0 0 0 255]} a))
        (is (= {:x 90.0
                :y 55.0
                :w 30.0
                :h 10.0
                :rotation -10.0
                :color [230 41 55 255]} b))
        (is (= {:x 80.0
                :y 65.0
                :w 15.0
                :h 25.0
                :rotation 55.0
                :color [0 121 241 255]} c))))
    (testing "the spin is 60 degrees a second and the clock is the sum of the frames"
      (let [s (-> start (step :idle nil 0.25) (step :idle nil 0.25))]
        (is (near? 0.5 (:t s)))
        (is (near? 30.0 (:rot s)))
        (is (= (pp/world s) (pp/world (at-time 0.5))))))
    (testing "the opening frame is the original's t = 0: cx -10, cy 30"
      (is (= [-10.0 30.0] (:target (pp/camera start d))))
      (is (near? 0.0 (:fx (pp/world start)))))))

(deftest overscan-reaches-past-the-window-by-one-ratio
  (let [r (double (:zoom d))
        [wx wy ww wh] (:window d)
        on (assoc start :overscan? true)
        [px py pw ph] (pp/picture on d)
        [qx qy qw qh] (pp/picture start d)]
    (testing "on: the blit is -R, -R, W + 2R, H + 2R of the window, as the original's"
      (is (near? (- r) px))
      (is (near? (- r) py))
      (is (near? (+ ww (* 2 r)) pw))
      (is (near? (+ wh (* 2 r)) ph)))
    (testing "off: 4/5 of a 5x window in the original is one pixel of zoom less here, centred"
      (is (near? (* 160 (dec r)) qw))
      (is (near? (* 90 (dec r)) qh))
      (is (near? (* 0.5 (- ww qw)) qx))
      (is (near? (* 0.5 (- wh qh)) qy)))
    (testing "the clip is the picture inside the window"
      (let [[cx cy cw ch] (pp/clip (assoc start :smooth? false) d)]
        (is (near? (+ wx qx) cx))
        (is (near? (+ wy qy) cy))
        (is (near? qw cw))
        (is (near? qh ch)))
      (let [[cx cy cw ch] (pp/clip (assoc on :smooth? false) d)]
        (is (near? wx cx))
        (is (near? wy cy))
        (is (near? ww cw))
        (is (near? wh ch))))))

(deftest buttons-avoid-back
  (doseq [screen screens
          :let [[w _] screen
                g (pp/geometry {:screen screen})
                [_ back-y _ back-h] gesture/back-region
                [sx sy sw sh] (:smooth-button g)
                [ox oy ow oh] (:overscan-button g)
                [_ fy _ _] (:field g)]]
    (testing (str screen)
      (is (>= sy (+ back-y back-h)) "below Back")
      (is (>= oy (+ back-y back-h)))
      (is (>= sx 0))
      (is (<= (+ ox ow) w))
      (is (<= (+ sx sw) ox) "side by side without touching")
      (is (<= (+ sy sh) fy) "above the field")
      (is (<= (+ oy oh) fy))))
  (let [s-on (centre (:smooth-button d))
        o-on (centre (:overscan-button d))
        s1 (tap start s-on)
        o1 (tap start o-on)]
    (testing "a tap on smooth flips smoothing only, and again flips it back"
      (is (true? (:smooth? start)))
      (is (false? (:smooth? s1)))
      (is (= (:overscan? start) (:overscan? s1)))
      (is (true? (:smooth? (tap s1 s-on)))))
    (testing "a tap on overscan flips overscan only"
      (is (false? (:overscan? start)))
      (is (true? (:overscan? o1)))
      (is (= (:smooth? start) (:smooth? o1))))
    (testing "a tap elsewhere or under Back changes nothing"
      (let [nowhere (tap start [600.0 2000.0])
            back (tap start [100.0 60.0])]
        (is (= [true false] ((juxt :smooth? :overscan?) nowhere)))
        (is (= [true false] ((juxt :smooth? :overscan?) back)))))
    (testing "a press that slides onto a button and lifts is not a tap on it"
      (let [slid (-> start (step :press [600.0 2000.0]) (step :down s-on) (step :release s-on))]
        (is (true? (:smooth? slid)))))
    (testing "a rotation of the phone clears the gesture"
      (let [turned (step (step start :press s-on) {:screen [2334 1206]} :down s-on 0.0)]
        (is (= [2334 1206] (:screen turned)))
        (is (true? (:smooth? turned)))))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [metrics {:screen screen}
                s (first ((:init (pp/scene)) {:metrics metrics}))
                dims (pp/dimensions metrics measure)
                [_ h] screen
                [wx wy ww wh] (:window dims)
                [fx fy fw fh] (:field dims)
                c (pp/camera s dims)
                [cx cy cw ch] (pp/clip s dims)
                rs (pp/rects s)]]
    (testing (str screen)
      (is (every? number? [(:t s) (:rot s)]))
      (is (true? (:smooth? s)))
      (is (false? (:overscan? s)))
      (is (every? number? (concat (:offset c) (:target c) [(:rotation c) (:zoom c)])))
      (is (pos? (:zoom c)))
      (is (= [-10.0 30.0] (:target c)) "t = 0")
      (is (= 3 (count rs)))
      (is (= [[0 0 0 255] [230 41 55 255] [0 121 241 255]] (map :color rs)))
      (is (every? #(every? number? (vals (dissoc % :color))) rs))
      (is (and (>= wx fx) (>= wy fy) (<= (+ wx ww) (+ fx fw)) (<= (+ wy wh) (+ fy fh)))
          "the window sits in the field")
      (is (<= 0 wx) "and in the screen")
      (is (<= (+ wy wh) h))
      (is (and (pos? cw) (pos? ch)) "there is a picture to clip to")
      (is (and (>= cx wx) (>= cy wy) (<= (+ cx cw) (+ wx ww)) (<= (+ cy ch) (+ wy wh))))
      (is (= (* 160 (:zoom dims)) ww))
      (is (some? (cam/world->screen c [70.0 35.0])))
      (is (= 4 (count (filter #(re-find #"resolution|Smooth|fps" (:s %)) (:lines dims))))
          "the four readouts"))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (pp/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (= 4 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (concat (:lines dims) [(:smooth-label dims) (:overscan-label dims)])]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) h) s))
      (testing "the widest status and fps fit at the chosen size"
        (let [size (:size (nth (:lines dims) 2))]
          (is (<= (measure (pp/status-line false false) size) (- w 0)))
          (is (<= (measure (pp/fps-line 999) (:size (nth (:lines dims) 3))) w))))
      (testing "the labels fit their buttons"
        (doseq [[btn label] [[:smooth-button :smooth-label] [:overscan-button :overscan-label]]
                :let [[bx by bw bh] (btn dims)
                      {:keys [s x y size]} (label dims)]]
          (is (<= (measure s size) bw))
          (is (<= size bh))
          (is (>= x bx))
          (is (<= (+ x (measure s size)) (+ bx bw)))
          (is (>= y by))
          (is (<= (+ y size) (+ by bh)))))
      (testing "the lines sit above the field"
        (let [[_ fy _ _] (:field dims)]
          (doseq [{:keys [y size]} (:lines dims)]
            (is (<= (+ y size) fy))))))))

(def ^:private zoom-screens
  "A phone (zoom 7) and the original's own zoom of 5."
  [[1206 2334] [800 1600]])

(defn- sweep [] (range 0.0 6.5 0.37))

(deftest the-drawn-world-covers-the-clip
  ;; The world is a 160 by 90 fill under the camera; its screen rectangle must
  ;; contain the scissor, or the clear colour shows through (the original's
  ;; stretched texture always covers its blit).
  (doseq [screen zoom-screens
          smooth? [true false]
          overscan? [true false]
          t (sweep)
          :let [g (pp/geometry {:screen screen})
                state (assoc (at-time t) :smooth? smooth? :overscan? overscan?)
                c (pp/camera state g)
                [tx ty] (:target c)
                [x0 y0] (cam/world->screen c [tx ty])
                [x1 y1] (cam/world->screen c [(+ tx 160.0) (+ ty 90.0)])
                [cx cy cw ch] (pp/clip state g)]]
    (testing (str screen " smooth " smooth? " overscan " overscan? " t " t)
      (is (<= x0 (+ cx 1e-6)))
      (is (<= y0 (+ cy 1e-6)))
      (is (>= x1 (- (+ cx cw) 1e-6)))
      (is (>= y1 (- (+ cy ch) 1e-6)) "the bottom is covered"))))

(deftest the-clip-slides-with-the-remainder
  (doseq [screen zoom-screens
          t [0.7 1.0 2.5 4.0]
          :let [g (pp/geometry {:screen screen})
                r (double (:zoom g))
                [wx wy ww wh] (:window g)
                {:keys [fx fy]} (pp/world (at-time t))
                on (at-time t)
                [px py pw ph] (pp/picture on g)
                [cx cy cw ch] (pp/clip on g)
                [ox oy] (pp/clip (assoc on :smooth? false) g)]]
    (testing (str screen " t " t)
      (is (not (zero? fx)))
      (is (near? (+ wx px (- (* fx r))) cx) "the picture's left edge is slid by minus the remainder")
      (is (near? (+ wy py (- (* fy r))) cy))
      (is (near? pw cw))
      (is (near? ph ch))
      (is (near? (+ wx px) ox) "and not slid with smoothing off")
      (is (near? (+ wy py) oy))
      (is (not (near? cx ox))))
    (testing "overscan clips to the window, slid or not"
      (let [over (assoc on :overscan? true)]
        (is (every? true? (map near? (pp/clip over g) [wx wy ww wh])))))))

(defn- cell-inside?
  "The reference: is the centre of cell `i`, `j` inside rect `{:x :y :w :h
  :rotation}` turned about `x`, `y`."
  [{:keys [x y w h rotation]} i j]
  (let [a (Math/toRadians rotation)
        c (Math/cos a)
        s (Math/sin a)
        dx (- (+ i 0.5) x)
        dy (- (+ j 0.5) y)
        lx (+ (* dx c) (* dy s))
        ly (- (* dy c) (* dx s))]
    (and (<= 0.0 lx) (< lx w) (<= 0.0 ly) (< ly h))))

(deftest rotated-rects-are-rasterised-to-virtual-pixels
  (testing "turned by nothing, a rect is whole rows of whole cells"
    (is (= (mapv (fn [j] [70 j 20]) (range 35 55))
           (pp/runs {:x 70.0
                     :y 35.0
                     :w 20.0
                     :h 20.0
                     :rotation 0.0}))))
  (testing "turned, the cells are exactly those whose centre is inside"
    (doseq [rot [7.0 33.3 98.0 123.4 200.0 -17.0 301.7 -61.0]
            rect (pp/rects {:rot rot})
            :let [runs (pp/runs rect)
                  cells (set (for [[i j n] runs
                                   k (range n)]
                               [(+ i k) j]))
                  want (set (for [i (range 0 200)
                                  j (range -60 200)
                                  :when (cell-inside? rect i j)]
                              [i j]))]]
      (testing (str rot " " (:color rect))
        (is (= want cells))
        (is (= (count cells) (reduce + (map #(nth % 2) runs))) "no cell is drawn twice")
        (is (every? #(and (integer? (first %)) (integer? (second %)) (pos? (nth % 2))) runs)))))
  (testing "the area is about the rect's"
    (doseq [rect (pp/rects {:rot 12.5})
            :let [n (reduce + (map #(nth % 2) (pp/runs rect)))
                  area (* (:w rect) (:h rect))]]
      (is (< (abs (- n area)) (* 0.25 area))))))
