(ns raylib.scenes.texcurve-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.texcurve :as sc]
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

(defn- near? [a b] (< (abs (- (double a) (double b))) 1e-9))

(def geo (sc/geometry metrics))

(defn- btn [k] (get (:buttons geo) k))

;; --- the road: textured_curve.clj lines 45-62, as a rule for each texel --------

(defn- ref-road
  "What texel (x, y) of the original's road is, worked out from its rectangles
  without drawing them."
  [x y]
  (cond
    (and (<= 29 x 34) (< (mod y 32) 16)) [240 200 60 255]
    (or (<= 4 x 8) (<= 55 x 59)) [235 235 235 255]
    :else [58 58 64 255]))

(deftest the-road-is-the-originals-image
  (let [g (sc/road-grid)
        px (texel/pixel-of g)
        {:keys [w h wrap filter pixel]} (sc/road-spec)]
    (is (= [64 128] [(:w g) (:h g)]))
    (is (= [64 128] [w h]))
    (is (= 8192 (count (:px g))))
    (is (= 8192 (count (for [y (range 128)
                             x (range 64)]
                         (is (= (ref-road x y) (texel/unpack (px x y))) (str [x y]))))))
    (testing "four colours, and the dashes divide the height evenly"
      (is (= 3 (count (set (for [y (range 128) x (range 64)] (px x y))))))
      (is (= [240 200 60 255] (texel/unpack (px 31 0))))
      (is (= [58 58 64 255] (texel/unpack (px 31 16))))
      (is (= [240 200 60 255] (texel/unpack (px 31 96))))
      (is (= [58 58 64 255] (texel/unpack (px 31 127))))
      (is (= (px 31 0) (px 31 32) (px 31 64))))
    (testing "the spec reads the same texels"
      (is (= (px 5 7) (pixel 5 7)))
      (is (= (px 31 100) (pixel 31 100))))
    (is (= :repeat wrap))
    (is (= :linear filter))))

(deftest specs-obey-gles2
  (let [{:keys [w h wrap]} (sc/road-spec)]
    (is (= :repeat wrap))
    (is (zero? (bit-and w (dec w))))
    (is (zero? (bit-and h (dec h))))
    (is (pos? w))
    (is (pos? h))))

;; --- the ribbon: textured_curve.clj lines 76-112, copied as the reference ------

(defn- ref-quads
  "The original's draw-curve! loop with its rl-tex-coord-2f / rl-vertex-2f pairs
  collected as four `[x y u v]` per segment."
  [p0 p1 p2 p3 segments width]
  (let [out (atom [])]
    (loop [i 1
           [px py] p0
           [pnx pny] nil
           prev-v 0.0]
      (when (<= i segments)
        (let [t (/ (double i) segments)
              [cx cy] (sc/bezier-at p0 p1 p2 p3 t)
              dx (- cx px)
              dy (- cy py)
              len (Math/sqrt (+ (* dx dx) (* dy dy)))
              [nx ny] (if (zero? len) [0.0 0.0] [(/ (- dy) len) (/ dx len)])
              [ppx ppy] (if pnx [pnx pny] [nx ny])
              v (+ prev-v (/ len (* 128 2.0)))]
          (swap! out conj
                 [[(- px (* ppx width)) (- py (* ppy width)) 0.0 prev-v]
                  [(+ px (* ppx width)) (+ py (* ppy width)) 1.0 prev-v]
                  [(+ cx (* nx width)) (+ cy (* ny width)) 1.0 v]
                  [(- cx (* nx width)) (- cy (* ny width)) 0.0 v]])
          (recur (inc i) [cx cy] [nx ny] v))))
    @out))

(def curve [[80.0 360.0] [250.0 150.0] [560.0 300.0] [720.0 110.0]])

(defn- triangles
  "A flat `[x y u v ...]` as a vector of triangles of three `[x y u v]`."
  [flat]
  (mapv vec (partition 3 (map vec (partition 4 flat)))))

(deftest v-accumulates-by-arc-length
  (doseq [[segments width] [[24 40.0] [3 6.0] [48 80.0] [10 25.5]]]
    (testing (str segments " segments, width " width)
      (let [flat (sc/ribbon curve segments width 0.0 0.0 1.0)
            tris (triangles flat)
            quads (ref-quads (curve 0) (curve 1) (curve 2) (curve 3) segments width)]
        (is (= (* 2 segments) (count tris)))
        (is (= (* 24 segments) (count flat)))
        (testing "two triangles a quad, A B C then A C D, at the original's corners"
          (doseq [[[a b c d] [t1 t2]] (map vector quads (partition 2 tris))]
            (is (every? true? (map (fn [p q] (every? true? (map near? p q))) [a b c] t1)))
            (is (every? true? (map (fn [p q] (every? true? (map near? p q))) [a c d] t2)))))
        (testing "v at the far edge of segment i is the sum of the lengths so far over 256"
          (let [pts (map #(sc/bezier-at (curve 0) (curve 1) (curve 2) (curve 3) (/ (double %) segments))
                         (range (inc segments)))
                lens (map (fn [[ax ay] [bx by]] (Math/sqrt (+ (* (- bx ax) (- bx ax)) (* (- by ay) (- by ay)))))
                          pts (rest pts))
                sums (reductions + (map #(/ % 256.0) lens))]
            (doseq [[tri-pair s] (map vector (partition 2 tris) sums)
                    :let [[_ _ c] (first tri-pair)]]
              (is (near? s (nth c 3))))))
        (testing "not by index: a long segment takes more v than a short one"
          (let [vs (map (fn [[_ _ c]] (nth c 3)) (map first (partition 2 tris)))
                steps (map - vs (cons 0.0 vs))]
            (is (< (apply min steps) (apply max steps)))
            (when (>= segments 10)
              (is (> (/ (apply max steps) (apply min steps)) 1.05)))))))))

(deftest the-ribbon-is-front-wound
  (testing "each triangle turns the way raylib.texture/triangles! leaves alone"
    ;; texture/triangles! swaps a pair when the cross product is positive, so a
    ;; triangle that is already non-positive goes out as it came in. The test
    ;; is on the scene's own output, so the swap is never what makes it pass.
    (doseq [frame [1 90 400]
            :let [pts (vec (sc/control-points {:frame frame
                                               :pins {}}))]
            [segments width] [[24 40.0] [3 80.0] [48 6.0]]
            tri (triangles (sc/ribbon pts segments width 100.0 200.0 1.7))
            :let [[[x1 y1] [x2 y2] [x3 y3]] tri
                  cross (- (* (- x2 x1) (- y3 y1)) (* (- y2 y1) (- x3 x1)))]]
      (is (<= cross 0.0) (str frame " " segments " " width))))
  (testing "a screen scale moves positions and leaves texcoords"
    (let [a (sc/ribbon curve 8 30.0 0.0 0.0 1.0)
          b (sc/ribbon curve 8 30.0 10.0 20.0 2.0)]
      (is (every? true? (map near? (take-nth 4 (drop 2 a)) (take-nth 4 (drop 2 b)))))
      (is (every? true? (map near? (take-nth 4 (drop 3 a)) (take-nth 4 (drop 3 b)))))
      (is (near? (+ 10.0 (* 2.0 (first a))) (first b)))
      (is (near? (+ 20.0 (* 2.0 (second a))) (second b))))))

;; --- the state ------------------------------------------------------------

(deftest the-controls-follow-the-original
  (let [s (fresh)]
    (is (= [40.0 24] ((juxt :width :segments) s)))
    (testing "WIDTH + held moves 0.8 a frame, to 80"
      (is (near? 48.0 (:width (nth (iterate #(tick % :down (centre (btn :width+))) s) 10))))
      (is (near? 80.0 (:width (nth (iterate #(tick % :down (centre (btn :width+))) s) 100)))))
    (testing "WIDTH - held lowers it, to 6"
      (is (near? 32.0 (:width (nth (iterate #(tick % :down (centre (btn :width-))) s) 10))))
      (is (near? 6.0 (:width (nth (iterate #(tick % :down (centre (btn :width-))) s) 100)))))
    (testing "SEG + and SEG - step once a press, to 3..48"
      (is (= 25 (:segments (tick s :press (centre (btn :seg+))))))
      (is (= 23 (:segments (tick s :press (centre (btn :seg-))))))
      (is (= 24 (:segments (tick s :down (centre (btn :seg+)))))
          "a finger held on it does not repeat")
      (is (= 48 (:segments (nth (iterate #(tick % :press (centre (btn :seg+))) s) 60))))
      (is (= 3 (:segments (nth (iterate #(tick % :press (centre (btn :seg-))) s) 60)))))
    (testing "the frame counts up, which is the flex's t"
      (is (= 1 (:frame (tick s))))
      (is (= 7 (:frame (nth (iterate tick s) 7)))))
    (testing "the middle points swing and the ends do not"
      (let [a (sc/control-points s)
            b (sc/control-points (nth (iterate tick s) 50))]
        (is (= (first a) (first b)))
        (is (= (last a) (last b)))
        (is (not= (nth a 1) (nth b 1)))
        (is (not= (nth a 2) (nth b 2)))
        (is (= [80.0 360.0] (first a)))
        (is (= [720.0 110.0] (last a)))))
    (testing "the flex is the original's at frame 100: t = 1.5"
      (let [[_ p1 p2] (sc/flex-points 100)
            t 1.5]
        (is (near? (+ 220.0 (* 120.0 (Math/sin t))) (first p1)))
        (is (near? (+ 120.0 (* 60.0 (Math/cos (* t 1.3)))) (second p1)))
        (is (near? (+ 560.0 (* 120.0 (Math/sin (* t 0.8)))) (first p2)))
        (is (near? (+ 330.0 (* 70.0 (Math/sin (* t 1.1)))) (second p2)))))
    (testing "the status line is the original's %.1f"
      (is (= "width 40.0   segments 24" (sc/status-line 40.0 24)))
      (is (= "width 6.0   segments 3" (sc/status-line 6.0 3)))
      (is (= "width 80.0   segments 48" (sc/status-line 80.0 48)))
      (is (= "width 40.8   segments 24" (sc/status-line 40.8 24))))))

(deftest the-points-move-only-as-the-originals-do
  (let [s (fresh)
        geo (sc/geometry metrics)
        ;; every pointer a finger could make, on and off the curve and the buttons
        pointers (concat
                  (for [phase [:press :down :release :idle]
                        at [nil [5.0 5.0] [600.0 1200.0] [(:ox geo) (:oy geo)]
                            (centre (btn :width+)) (centre (btn :seg-)) [-5000.0 9999.0]]]
                    {:phase phase
                     :position at})
                  (for [i (range 40)]
                    {:phase (if (zero? i) :press :down)
                     :position [(+ 100.0 (* 25 i)) (+ 900.0 (* 10 i))]}))]
    (testing "on every frame the points are the original's flex at that frame"
      (let [states (reductions (fn [st p]
                                 (first ((:update (sc/scene)) st {:metrics metrics
                                                                  :pointer p})))
                               s
                               (take 200 (cycle pointers)))]
        (is (= 201 (count states)))
        (doseq [st states]
          (is (= (sc/flex-points (:frame st)) (sc/control-points st)) (str "frame " (:frame st))))
        (is (= (range 201) (map :frame states)))))
    (testing "a finger dragged straight over a point moves nothing"
      (let [[px py] [(+ (:ox geo) (* (:k geo) 250.0)) (+ (:oy geo) (* (:k geo) 200.0))]
            st (reduce (fn [st [phase at]] (tick st phase at))
                       s
                       [[:press [px py]] [:down [(+ px 80.0) (+ py 40.0)]] [:release [0.0 0.0]]])]
        (is (= (sc/flex-points 3) (sc/control-points st)))))
    (testing "the state carries no drag"
      (is (not-any? #{:pins :drag} (keys s))))))

(deftest first-frame-draws
  (let [s (fresh)]
    (is (= :texcurve (:id (sc/scene))))
    (is (= "Textured Curve" (:title (sc/scene))))
    (doseq [screen screens
            :let [[w h] screen
                  dims (sc/dimensions {:screen screen} measure)
                  verts (sc/vertices s dims)
                  inside? (fn [[x y rw rh]] (and (<= 0 x) (<= 0 y) (<= (+ x rw) w) (<= (+ y rh) h)))]]
      (testing (str screen)
        (testing "the ribbon is 24 segments of two triangles, in groups of three vertices"
          (is (= (* 24 24) (count verts)))
          (is (zero? (mod (count verts) 12))))
        (testing "the curve's box is inside the screen and below the buttons"
          (let [{:keys [ox oy k]} dims
                btn-bottom (apply max (map (fn [[_ [_ y _ bh]]] (+ y bh)) (:buttons dims)))]
            (is (pos? k))
            (is (>= oy btn-bottom))
            (is (<= 0 ox))
            (is (<= (+ ox (* k 800)) (+ w 1e-9)))
            (is (<= (+ oy (* k 450)) h))))
        (testing "the four buttons are finger-sized, in a row and apart"
          (let [rs (map #(get (:buttons dims) %) sc/button-keys)]
            (is (every? inside? rs))
            (is (every? #(>= (nth % 3) 40) rs))
            (is (apply = (map second rs)))
            (is (every? true? (map (fn [[x _ bw _] [nx]] (<= (+ x bw) nx)) rs (rest rs))))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [lines labels buttons pad]} (sc/dimensions {:screen screen} measure)]]
    (testing (str screen)
      (is (= 2 (count lines)))
      (doseq [{:keys [s x y size]} lines]
        (testing s
          (is (<= 0 x))
          (is (<= (+ x (measure s size)) w))
          (is (<= (+ y size) h))
          (is (>= y 120) "below Back")))
      (testing "the widest status is covered"
        (let [size (:size (first lines))]
          (doseq [[wd sg] [[6.0 3] [40.0 24] [80.0 48]]]
            (is (<= (+ pad (measure (sc/status-line wd sg) size)) w)))))
      (testing "the lines are above the buttons"
        (let [[_ by] (get buttons :width-)]
          (is (<= (+ (:y (last lines)) (:size (last lines))) by))))
      (testing "each label sits inside its button"
        (doseq [k sc/button-keys
                :let [{:keys [s x y size]} (get labels k)
                      [bx by bw bh] (get buttons k)]]
          (is (>= x bx))
          (is (<= (+ x (measure s size)) (+ bx bw)))
          (is (>= y by))
          (is (<= (+ y size) (+ by bh))))))))

(deftest the-transient-road-equals-the-persistent-replay
  (let [white [235 235 235 255]
        yellow [240 200 60 255]
        persistent (as-> (texel/grid 64 128 [58 58 64 255]) g
                     (texel/draw-rect g 4 0 5 128 white)
                     (texel/draw-rect g (- 64 9) 0 5 128 white)
                     (reduce (fn [g i] (texel/draw-rect g (- (quot 64 2) 3) (* i 32) 6 16 yellow))
                             g
                             (range 4)))]
    (is (= persistent (sc/road-grid)))))
