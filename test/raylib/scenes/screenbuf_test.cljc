(ns raylib.scenes.screenbuf-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.screenbuf :as sc]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def metrics {:screen [1206 2334]})

(defn- fresh [] (first ((:init (sc/scene)) {:metrics metrics})))

(defn- tick [state]
  (first ((:update (sc/scene)) state {:metrics metrics
                                      :pointer {:phase :idle
                                                :position nil}})))

(defn- low32 [n] (bit-and n 0xFFFFFFFF))

;; raylib-jlt's `rgba` (net/b12n/raylib/color.clj) and the original's
;; `hsv->color` and PALETTE (screen_buffer.clj lines 28-53), copied as the
;; reference.
(defn- ref-rgba [r g b a]
  (bit-or (int r) (bit-shift-left (int g) 8)
          (bit-shift-left (int b) 16) (bit-shift-left (int a) 24)))

(defn- ref-hsv->color [h s v]
  (let [h' (/ (mod h 360.0) 60.0)
        i (int (Math/floor h'))
        f (- h' i)
        p (* v (- 1.0 s))
        q (* v (- 1.0 (* s f)))
        t (* v (- 1.0 (* s (- 1.0 f))))
        [r g b] (case (mod i 6)
                  0 [v t p]
                  1 [q v p]
                  2 [p v t]
                  3 [p q v]
                  4 [t p v]
                  5 [v p q])]
    (ref-rgba (int (* 255 r)) (int (* 255 g)) (int (* 255 b)) 255)))

(def ^:private ref-palette
  (mapv (fn [i]
          (let [t (/ i (double 255))
                hue (* t t)]
            (ref-hsv->color (+ 250.0 (* 150.0 hue)) t t)))
        (range 256)))

;; The original's `grow-roots`, `seed-and-clear` and `rise` (lines 55-96),
;; retyped in its own shape (mapv, reduce and nested loops over a persistent
;; vector) with GetRandomValue replaced by `rv`, which draws from the same LCG
;; the scene uses. One whole step.
(defn- ref-step [{:keys [buf roots seed]}]
  (let [seed (atom seed)
        rv (fn [lo hi]
             (let [s (mod (+ (* 1103515245 (long @seed)) 12345) 2147483648)]
               (reset! seed s)
               (+ lo (mod (quot s 65536) (inc (- hi lo))))))
        roots (mapv (fn [x v] (if (>= x 2) (min 255 (+ v (rv 0 2))) v))
                    (range 200) roots)
        base (* 111 200)
        buf (as-> buf b
              (reduce (fn [b x] (assoc b (+ x base) (nth roots x))) b (range 200))
              (reduce (fn [b x] (assoc b x 0)) b (range 200)))
        buf (loop [y 1 b buf]
              (if (< y 112)
                (recur (inc y)
                       (loop [x 0 b b]
                         (if (>= x 200)
                           b
                           (let [i (+ x (* y 200))
                                 ci (nth b i)]
                             (if (zero? ci)
                               (recur (inc x) b)
                               (let [b (assoc b i 0)
                                     mv (dec (rv 0 2))
                                     nx (+ x mv)]
                                 (if (and (> nx 0) (< nx 200))
                                   (let [ia (+ (- i 200) mv)
                                         d (rv 0 3)
                                         nc (- ci (min d ci))]
                                     (recur (inc x) (assoc b ia nc)))
                                   (recur (inc x) b))))))))
                b))]
    {:buf buf
     :roots roots
     :seed @seed}))

(defn- sweep
  "One of the original's steps, as `period` frames of the scene."
  [state]
  (nth (iterate tick state) sc/period))

(deftest the-palette-is-the-originals
  (is (= 256 (count sc/palette) (count sc/palette-texels)))
  (is (= ref-palette (mapv low32 sc/palette-texels)))
  (is (= [0 0 0 255] (first sc/palette)) "index 0 is black")
  (is (= [255 170 0 255] (last sc/palette)) "index 255: hue 400 is 40 degrees, full s and v")
  (is (every? #(= 255 (nth % 3)) sc/palette))
  (testing "the ramp brightens: the high indices are brighter than the low"
    (let [lum (fn [[r g b]] (+ r g b))]
      (is (< (lum (nth sc/palette 20)) (lum (nth sc/palette 128)) (lum (nth sc/palette 255)))))))

(deftest the-fire-steps-as-the-original
  (let [s0 (fresh)
        r0 {:buf (:buf s0)
            :roots (:roots s0)
            :seed (:seed s0)}
        ref (take 41 (iterate ref-step r0))
        mine (take 41 (iterate sweep s0))]
    (is (= 2026 (:seed s0)))
    (testing "after 1, 2, 5, 20 and 40 steps"
      (doseq [n [1 2 5 20 40]
              :let [a (nth ref n)
                    b (nth mine n)]]
        (testing (str n " steps")
          (is (= (:roots a) (:roots b)))
          (is (= (:buf a) (:buf b)))
          (is (= (:seed a) (:seed b))))))
    (testing "the fire is alight by then, so the comparison means something"
      (let [b (nth mine 40)]
        (is (< 100 (count (remove zero? (:buf b)))))
        (is (< 1 (count (set (:buf b)))))
        (is (every? #(<= 0 % 255) (:buf b)))
        (is (every? #(<= 0 % 255) (:roots b)))))
    (testing "a different seed is a different fire"
      (let [other (assoc (fresh) :seed 7)]
        (is (not= (:buf (nth mine 20)) (:buf (nth (iterate sweep other) 20))))))))

(deftest the-roots-grow-as-the-original-says
  (let [s (nth (iterate sweep (fresh)) 3)]
    (is (= [0 0] (take 2 (:roots s))) "columns 0 and 1 never grow")
    (is (every? #(<= 0 % 6) (drop 2 (:roots s))) "0 to 2 a step")
    (is (some pos? (drop 2 (:roots s))))))

(deftest the-bands-tile-the-grid-once-a-sweep
  (is (= 3 sc/band-rows))
  (is (= 38 sc/period) "112 rows in bands of 3, the last one short")
  (let [bands (map sc/band (range sc/period))]
    (is (= (range 112) (mapcat (fn [[y0 y1]] (range y0 y1)) bands)))
    (is (= [111 112] (last bands)))))

(deftest the-upload-follows-the-step-and-the-row-above
  (let [ups (map sc/upload-rows (range sc/period))]
    (testing "no frame refills more than the band and the row above it"
      (is (every? (fn [[_ n]] (<= n (inc sc/band-rows))) ups)))
    (testing "the first frame has no row above"
      (is (= [0 sc/band-rows] (first ups))))
    (testing "every later frame starts one row above its band"
      (is (= [(dec sc/band-rows) (inc sc/band-rows)] (second ups))))
    (testing "every row is refilled at least once a sweep, and the cover is gap-free"
      (is (= (set (range 112)) (set (mapcat (fn [[y0 n]] (range y0 (+ y0 n))) ups)))))
    (testing "the schedule repeats each sweep: advance steps the same band a period later"
      (let [states (vec (take (+ 3 (* 2 sc/period)) (rest (iterate tick (fresh)))))
            stepped (mapv :stepped states)]
        (is (= (+ 3 (* 2 sc/period)) (count states)))
        (is (every? #(< -1 % sc/period) stepped) "every band is in range, so upload-rows' bound holds")
        (is (every? (fn [f] (= (stepped f) (stepped (+ f sc/period))))
                    (range (+ 3 sc/period))))
        (is (= (set (range sc/period)) (set (take sc/period stepped))) "a sweep steps every band once")))
    (testing "never past the grid"
      (is (every? (fn [[y0 n]] (and (<= 0 y0) (<= (+ y0 n) 112))) ups)))))

;; The gallery draws after the update, from the state `advance` returned, and
;; uploads `upload-rows` of the band that state says it stepped. A mock texture
;; takes those rows from the buffer as `texture/band!` does. At the end of each
;; sweep every band has stepped and been uploaded after its own step, so the
;; texture must be the buffer's palette image.
(deftest the-rows-uploaded-are-the-rows-stepped
  (let [tex (volatile! (vec (repeat (* 200 112) 0)))
        run (fn [s]
              (let [s (tick s)
                    [y0 n] (sc/upload-rows (:stepped s))
                    px (:pixel (sc/spec (:buf s)))]
                (doseq [y (range y0 (+ y0 n))
                        x (range 200)]
                  (vswap! tex assoc (+ x (* y 200)) (px x y)))
                s))
        image (fn [state] (mapv #(nth sc/palette-texels %) (:buf state)))]
    (doseq [sweeps [2 3]
            :let [s (nth (iterate run (fresh)) (* sweeps sc/period))
                  img (image s)
                  bad (distinct (for [y (range 112) x (range 200)
                                      :when (not= (nth img (+ x (* y 200))) (nth @tex (+ x (* y 200))))]
                                  y))]]
      (testing (str "end of sweep " sweeps)
        (is (< 30 (count (remove zero? (:buf s)))) "something is lit")
        (is (empty? bad) (str "rows that differ: " (vec (take 12 bad))))))))

(deftest the-spec-reads-the-buffer-through-the-palette
  (let [s (nth (iterate tick (fresh)) 200)
        {:keys [w h pixel wrap filter]} (sc/spec (:buf s))]
    (is (= [200 112] [w h]))
    (is (nil? wrap))
    (is (nil? filter))
    (is (every? true? (for [y (range 112)
                            x (range 200)]
                        (= (low32 (ref-palette (nth (:buf s) (+ x (* y 200)))))
                           (low32 (pixel x y))))))))

(deftest first-frame-draws
  (is (= :screenbuf (:id (sc/scene))))
  (is (= "Screen Buffer" (:title (sc/scene))))
  (is (= 1 (count (filter #{[:scene/init :screenbuf]} (second ((:init (sc/scene)) {:metrics metrics}))))))
  (doseq [screen screens
          :let [[w h] screen
                {:keys [x y width height]} (sc/geometry {:screen screen})]]
    (testing (str screen)
      (is (>= x 0))
      (is (<= (+ x width) w))
      (is (>= y 120) "below Back")
      (is (<= (+ y height) h))
      (testing "the grid's own shape, to within a pixel"
        (is (< (abs (- (/ width height) (/ 200 112.0))) 0.05)))
      (testing "as big as the free area allows"
        (is (or (>= width (- w 2)) (>= (+ height 120) (- h 2)))))
      (testing "centred across"
        (is (<= (abs (- x (- w x width))) 1))))))
