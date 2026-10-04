(ns raylib.scenes.spriteanim-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.spriteanim :as sc]
            [raylib.texel :as texel]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def metrics {:screen [1206 2334]})

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- fresh [] (first ((:init (sc/scene)) {:metrics metrics})))

(defn- tick
  ([state] (tick state :idle nil))
  ([state phase position]
   (first ((:update (sc/scene)) state {:metrics metrics
                                       :pointer {:phase phase
                                                 :position position}}))))

(defn- centre [[x y w h]] [(+ x (/ w 2.0)) (+ y (/ h 2.0))])

(defn- press [state rect] (tick state :press (centre rect)))

;; sprite_animation.clj lines 37-89, written out as the list of ImageDrawCircle
;; calls the original makes, in its order, then applied. `(int ...)` truncates
;; as the original's does.
(defn- ref-calls []
  (let [thick (fn [x1 y1 x2 y2 r c]
                (for [i (range 15)
                      :let [f (/ (double i) 14)]]
                  [(int (+ x1 (* f (- x2 x1)))) (int (+ y1 (* f (- y2 y1)))) r c]))]
    (vec
     (mapcat
      (fn [i]
        (let [x0 (* i 96)
              phase (* 2.0 Math/PI (/ (double i) 6))
              swing (Math/sin phase)
              bob (int (* 3.0 (Math/abs (Math/cos phase))))
              cx (+ x0 48)
              hip (+ 72 bob)
              sh (+ 46 bob)]
          (concat
           (thick cx hip (+ cx (int (* 18 swing))) 106 6 [45 60 95 255])
           (thick cx hip (- cx (int (* 18 swing))) 106 6 [60 80 125 255])
           (thick cx (+ 38 bob) cx hip 9 [230 41 55 255])
           (thick (- cx 8) sh (- cx 8 (int (* 20 swing))) (+ 76 bob) 5 [120 25 35 255])
           (thick (+ cx 8) sh (+ cx 8 (int (* 20 swing))) (+ 76 bob) 5 [245 130 120 255])
           [[cx (+ 22 bob) 15 [235 195 150 255]]
            [(+ cx 6) (+ 19 bob) 3 [0 0 0 255]]])))
      (range 6)))))

(deftest the-strip-is-the-originals-image
  (let [calls (ref-calls)
        expected (reduce (fn [g [x y r c]] (texel/draw-circle g x y r c))
                         (texel/grid 576 120 [0 0 0 0])
                         calls)
        got (sc/strip-grid)
        px (texel/pixel-of got)
        {:keys [w h pixel]} (sc/strip-spec)]
    (testing "the originals's circle count: 6 poses of 5 limbs of 15, a head and an eye"
      (is (= (* 6 (+ (* 5 15) 2)) (count calls))))
    (is (= [576 120] [(:w got) (:h got)]))
    (is (= [576 120] [w h]))
    (is (= (:px expected) (:px got)) "texel for texel the replay of the originals's calls")
    (testing "spot checks that do not go through the replay"
      (is (zero? (px 0 0)) "the corner is transparent")
      (is (zero? (px 575 119)))
      (is (= [235 195 150 255] (texel/unpack (px 48 25))) "frame 0 head, bob 3: centre (48, 25)")
      (is (= [0 0 0 255] (texel/unpack (px 54 22))) "frame 0 eye at (cx+6, 19+3)")
      (is (= [235 195 150 255] (texel/unpack (px (+ 96 48 -10) 25))) "frame 1 head sits in frame 1"))
    (testing "every frame has a walker in it, and none spills into its neighbour"
      (doseq [i (range 6)
              :let [inside (for [y (range 120)
                                 x (range (* i 96) (* (inc i) 96))
                                 :when (pos? (px x y))]
                             [x y])]]
        (is (< 500 (count inside)) (str "frame " i))))
    (testing "the original's poses pair up, which is its own quirk and kept"
      ;; sin and |cos| repeat: frames 0 and 3 are both (swing 0, bob 3), frames 1
      ;; and 2 both (swing 0.866, bob 1), frames 4 and 5 both (swing -0.866,
      ;; bob 1). So six frames show three distinct poses.
      (let [frame (fn [i] (for [y (range 120)
                                x (range 96)]
                            (px (+ x (* i 96)) y)))]
        (is (= (frame 0) (frame 3)))
        (is (= (frame 1) (frame 2)))
        (is (= (frame 4) (frame 5)))
        (is (= 3 (count (set (map frame (range 6))))))))
    (testing "the spec reads the same texels"
      (is (= (px 48 25) (pixel 48 25)))
      (is (= (px 300 60) (pixel 300 60))))))

(deftest specs-obey-gles2
  (let [{:keys [w h wrap filter pixel]} (sc/strip-spec)]
    (is (= :clamp wrap) "576 is not a power of two, so no REPEAT")
    (is (= :nearest filter))
    (is (not (zero? (bit-and w (dec w)))))
    (is (every? #(<= 0 (pixel % 5) 0xFFFFFFFF) [0 100 575]))
    (is (= h 120))))

(deftest the-frame-advances-at-the-originals-rate
  (let [s (fresh)
        geo (sc/geometry metrics)
        steps (fn [state n] (nth (iterate tick state) n))]
    (is (= {:counter 0
            :current 0
            :speed 8} (select-keys s [:counter :current :speed])))
    (testing "speed 8: (quot 60 8) is 7, so the frame steps every 7th update"
      (is (= 0 (:current (steps s 6))))
      (is (= 1 (:current (steps s 7))))
      (is (= 0 (:counter (steps s 7))))
      (is (= 2 (:current (steps s 14)))))
    (testing "the frame wraps after six"
      (is (= 0 (:current (steps s 42))))
      (is (= 5 (:current (steps s 35)))))
    (testing "FASTER and SLOWER move the speed one a press"
      (is (= 9 (:speed (press s (:faster geo)))))
      (is (= 7 (:speed (press s (:slower geo)))))
      (is (= 10 (:speed (press (press s (:faster geo)) (:faster geo))))))
    (testing "only the press frame counts, as a key's first frame does"
      (is (= 8 (:speed (tick s :down (centre (:faster geo))))))
      (is (= 8 (:speed (tick s :release (centre (:faster geo))))))
      (is (= 8 (:speed (tick s :press [5.0 1500.0])))))
    (testing "the speed is held to 1..15"
      (is (= 15 (:speed (nth (iterate #(press % (:faster geo)) s) 30))))
      (is (= 1 (:speed (nth (iterate #(press % (:slower geo)) s) 30)))))
    (testing "MAX-SPEED 15: (quot 60 15) is 4, every 4th update"
      (let [f (assoc s :speed 15)]
        (is (= 0 (:current (steps f 3))))
        (is (= 1 (:current (steps f 4))))
        (is (= 3 (:current (steps f 12))))))
    (testing "MIN-SPEED 1: (quot 60 1) is 60"
      (let [f (assoc s :speed 1)]
        (is (= 0 (:current (steps f 59))))
        (is (= 1 (:current (steps f 60))))))
    (testing "a press that changes the speed also ticks the counter, as the original does"
      (is (= 1 (:counter (press s (:faster geo))))))))

(deftest first-frame-draws
  (let [s (fresh)]
    (is (= :spriteanim (:id (sc/scene))))
    (is (= "Sprite Animation" (:title (sc/scene))))
    (doseq [screen screens
            :let [[w h] screen
                  dims (sc/dimensions {:screen screen} measure)
                  [sx sy sw sh] (:strip dims)
                  [fx fy fw fh] (:frame dims)
                  inside? (fn [[x y rw rh]] (and (<= 0 x) (<= 0 y) (<= (+ x rw) w) (<= (+ y rh) h)))]]
      (testing (str screen)
        (testing "the strip keeps its 576:120 shape and sits below Back"
          (is (< (abs (- (/ sw sh) (/ 576.0 120))) 1e-9))
          (is (>= sy 120))
          (is (inside? (:strip dims))))
        (testing "the frame box is one sixth of the strip"
          (let [[bx by bw bh] (sc/frame-box dims 3)]
            (is (< (abs (- bw (/ sw 6))) 1e-9))
            (is (< (abs (- (+ sx (* 3 (/ sw 6))) bx)) 1e-9))
            (is (= [sy sh] [by bh]))))
        (testing "the fifteen boxes are in a row inside the screen"
          (is (= 15 (count (:cells dims))))
          (is (every? inside? (:cells dims))))
        (testing "the buttons are finger-sized, apart and below the boxes"
          (let [[ux uy uw uh] (:slower dims)
                [vx vy vw vh] (:faster dims)
                [_ cy _ ch] (first (:cells dims))]
            (is (inside? (:slower dims)))
            (is (inside? (:faster dims)))
            (is (<= (+ ux uw) vx))
            (is (>= uy (+ cy ch)))
            (is (>= (min uh vh) 40))
            (is (= uy vy))
            (is (= uw vw) "the two buttons are the same width")))
        (testing "the larger frame is 128:160, below the buttons, inside the screen"
          (is (< (abs (- (/ fw fh) 0.8)) 1e-9))
          (is (inside? (:frame dims)))
          (is (>= fy (+ (nth (:faster dims) 1) (nth (:faster dims) 3))))
          (is (>= fh 60) "big enough to see"))
        (testing "the texcoords pick a sixth of the strip"
          (doseq [i (range 6)
                  :let [q (sc/quad (assoc s :current i) dims)]]
            (is (< (abs (- (:u0 q) (/ i 6.0))) 1e-12))
            (is (< (abs (- (:u1 q) (/ (inc i) 6.0))) 1e-12))
            (is (< (abs (- (- (:u1 q) (:u0 q)) (/ 1.0 6))) 1e-12))
            (is (= [fx fy fw fh] ((juxt :x :y :width :height) q)))))
        (testing "the whole strip is one quad over its rect"
          (let [q (sc/strip-quad dims)]
            (is (= [sx sy sw sh] ((juxt :x :y :width :height) q)))
            (is (nil? (:u0 q)) "default texcoords, the whole texture")))
        (testing "the outline of a rect is four bars inside it"
          (is (= 4 (count (sc/outline-rects (:strip dims) 2))))
          (is (every? inside? (sc/outline-rects (:strip dims) 2))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [lines labels]
                 :as dims} (sc/dimensions {:screen screen} measure)]]
    (testing (str screen)
      (is (= 3 (count lines)))
      (doseq [{:keys [s x y size]} lines]
        (testing s
          (is (<= 0 x))
          (is (<= (+ x (measure s size)) w))
          (is (<= (+ y size) h))
          (is (>= y 120) "below Back")))
      (testing "the lines do not overlap the strip, the boxes or the buttons"
        (let [[note speed hint] lines
              [_ sy _ sh] (:strip dims)
              [_ cy] (first (:cells dims))]
          (is (<= (+ (:y note) (:size note)) sy))
          (is (<= (+ sy sh) (:y speed)))
          (is (<= (+ (:y speed) (:size speed)) cy))
          (is (<= (+ (:y hint) (:size hint)) (nth (:slower dims) 1)))))
      (testing "the widest speed line is covered"
        (let [size (:size (second lines))]
          (doseq [speed [1 8 15]]
            (is (<= (measure (sc/speed-line speed) size) (- w 16))))))
      (testing "each label sits inside its button"
        (doseq [k [:slower :faster]
                :let [{:keys [s x y size]} (k labels)
                      [bx by bw bh] (k dims)]]
          (is (>= x bx))
          (is (<= (+ x (measure s size)) (+ bx bw)))
          (is (>= y by))
          (is (<= (+ y size) (+ by bh))))))))
