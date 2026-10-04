(ns raylib.scenes.texproc-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.texproc :as sc]
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

(def mid [600.0 1200.0])

;; raylib-jlt's `rgba` (net/b12n/raylib/color.clj) and the four pixel fns
;; (texture_procedural.clj lines 24-48), copied as the reference. `noise` is fed
;; a value instead of GetRandomValue.
(defn- ref-rgba [r g b a]
  (bit-or (int r) (bit-shift-left (int g) 8)
          (bit-shift-left (int b) 16) (bit-shift-left (int a) 24)))

(defn- ref-checker [x y]
  (if (even? (+ (quot x 16) (quot y 16)))
    (ref-rgba 40 44 52 255)
    (ref-rgba 230 232 238 255)))

(defn- ref-gradient [x y]
  (ref-rgba (int (* 255 (/ x (double 128))))
            (int (* 255 (/ y (double 128))))
            140
            255))

(defn- ref-noise [v] (ref-rgba v v v 255))

(defn- ref-rings [x y]
  (let [c (/ 128 2.0)
        d (Math/sqrt (+ (* (- x c) (- x c)) (* (- y c) (- y c))))
        t (Math/sin (/ d 6.0))
        v (int (* 127 (+ 1.0 t)))]
    (ref-rgba v (int (* 0.4 v)) (- 255 v) 255)))

(defn- low32 [n] (bit-and n 0xFFFFFFFF))

(defn- each-texel [f]
  (for [y (range 128)
        x (range 128)]
    (f x y)))

(deftest each-panel-is-the-originals
  (testing "checkerboard"
    (let [{:keys [w h pixel]} (sc/checker-spec)]
      (is (= [128 128] [w h]))
      (is (every? true? (each-texel #(= (low32 (ref-checker % %2)) (low32 (pixel % %2))))))
      (is (= 2 (count (set (each-texel pixel)))))))
  (testing "gradient"
    (let [{:keys [pixel]} (sc/gradient-spec)]
      (is (every? true? (each-texel #(= (low32 (ref-gradient % %2)) (low32 (pixel % %2))))))
      (is (= 128 (count (set (map #(bit-and 0xFF (pixel % 0)) (range 128))))))))
  (testing "rings"
    (let [{:keys [pixel]} (sc/rings-spec)]
      (is (every? true? (each-texel #(= (low32 (ref-rings % %2)) (low32 (pixel % %2))))))
      (is (< 20 (count (set (each-texel pixel)))))))
  (testing "noise is the original's grey fed the project LCG's sequence"
    (let [s (fresh)
          {:keys [pixel w h]} (sc/noise-spec s)
          vs (sc/noise-values (:seed s))]
      (is (= [128 128] [w h]))
      (is (= 16384 (count vs)))
      (is (every? #(<= 0 % 255) vs))
      (is (every? true? (each-texel #(= (low32 (ref-noise (nth vs (+ % (* 128 %2)))))
                                        (low32 (pixel % %2))))))
      (testing "the sequence is the LCG's high bits, step by step"
        (let [step (fn [s] (mod (+ (* 1103515245 s) 12345) 2147483648))
              s1 (step (:seed s))
              s2 (step s1)]
          (is (= [(mod (quot s1 65536) 256) (mod (quot s2 65536) 256)] (take 2 vs)))))
      (testing "the same seed replays, and the values spread over the range"
        (is (= vs (sc/noise-values (:seed s))))
        (is (< 200 (count (set vs))))
        (is (< 100 (/ (reduce + vs) 16384.0) 156)))))
  (testing "packing is raylib-jlt's rgba"
    (is (= (low32 (ref-rgba 40 44 52 255)) (texel/pack [40 44 52 255])))))

(deftest a-tap-regenerates-the-noise
  (let [s (fresh)
        t (tick s :press mid)]
    (is (= 0 (:version s)))
    (is (= 1 (:version t)))
    (is (not= (:seed s) (:seed t)))
    (is (= (sc/reseed (:seed s)) (:seed t)))
    (testing "the new picture differs from the old, texel by texel mostly"
      (let [a (sc/noise-values (:seed s))
            b (sc/noise-values (:seed t))]
        (is (< 16000 (count (filter false? (map = a b)))))
        (is (not= (rest a) (butlast b)) "not the old one slid along")))
    (testing "the spec carries the version, so id! rewrites in place"
      (is (= 0 (:version (sc/noise-spec s))))
      (is (= 1 (:version (sc/noise-spec t)))))
    (testing "the other panels carry no version, so they upload once"
      (is (nil? (:version (sc/checker-spec))))
      (is (nil? (:version (sc/gradient-spec))))
      (is (nil? (:version (sc/rings-spec)))))
    (testing "each press bumps it again"
      (is (= 3 (:version (-> s (tick :press mid) (tick :release mid) (tick :press mid)
                             (tick :release mid) (tick :press mid))))))
    (testing "nothing else is a tap"
      (is (= 0 (:version (tick s :down mid))))
      (is (= 0 (:version (tick s :release mid))))
      (is (= 0 (:version (tick s))))
      (is (= 0 (:version (tick s :press nil))))
      (is (= 0 (:version (tick s :press [100.0 50.0]))) "Back is the host's"))))

(deftest a-held-finger-regenerates-once
  (let [s (tick (fresh) :press mid)
        held (nth (iterate #(tick % :down mid) s) 30)
        gone (tick held :release mid)]
    (is (= 1 (:version s)))
    (is (= 1 (:version held)))
    (is (= (:seed s) (:seed held)))
    (is (= 1 (:version gone)))
    (is (= 2 (:version (tick gone :press mid))))))

(deftest specs-obey-gles2
  (doseq [{:keys [w h wrap filter pixel]} [(sc/checker-spec) (sc/gradient-spec)
                                           (sc/noise-spec (fresh)) (sc/rings-spec)]]
    (is (nil? wrap) "clamped, as the original's default")
    (is (nil? filter))
    (is (= 128 w h))
    (is (zero? (bit-and w (dec w))))
    (testing "every texel is an opaque packed colour that fits in 32 bits"
      (is (every? true? (each-texel #(let [p (pixel % %2)]
                                       (and (<= 0 p 0xFFFFFFFF)
                                            (= 255 (bit-and 0xFF (bit-shift-right p 24)))))))))))

(deftest first-frame-draws
  (is (= :texproc (:id (sc/scene))))
  (is (= "Procedural Textures" (:title (sc/scene))))
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                rects (map #(:rect (get (:panels dims) %)) sc/panel-keys)]]
    (testing (str screen)
      (testing "four square panels, equal, inside the screen and below Back"
        (is (= 4 (count rects)))
        (is (= 1 (count (set (map (fn [[_ _ pw ph]] [pw ph]) rects)))))
        (doseq [[x y pw ph] rects]
          (is (= pw ph))
          (is (>= x 0))
          (is (<= (+ x pw) w))
          (is (>= y 120))
          (is (<= (+ y ph) h))))
      (testing "panels do not overlap"
        (doseq [[i [ax ay aw ah]] (map-indexed vector rects)
                [j [bx by bw bh]] (map-indexed vector rects)
                :when (< i j)]
          (is (or (<= (+ ax aw) bx) (<= (+ bx bw) ax)
                  (<= (+ ay ah) by) (<= (+ by bh) ay))
              (str i " " j))))
      (testing "the panels are big enough to read"
        (is (>= (:cell dims) (* 0.2 (min w h)))))
      (testing "the quad is the rect"
        (let [q (sc/quad dims :noise)]
          (is (= (:rect (get (:panels dims) :noise)) [(:x q) (:y q) (:width q) (:height q)]))))
      (testing "the outline is four rects touching the panel's edges"
        (let [[x y pw ph] (:rect (get (:panels dims) :rings))
              rs (sc/outline-rects dims :rings)]
          (is (= 4 (count rs)))
          (doseq [[rx ry rw rh] rs]
            (is (>= rx x))
            (is (>= ry y))
            (is (<= (+ rx rw) (+ x pw)))
            (is (<= (+ ry rh) (+ y ph)))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [lines labels panels]
                 :as dims} (sc/dimensions {:screen screen} measure)
                rects (map #(:rect (get panels %)) sc/panel-keys)]]
    (testing (str screen)
      (is (= 3 (count lines)))
      (doseq [{:keys [s x y size]} lines]
        (testing s
          (is (<= 0 x))
          (is (<= (+ x (measure s size)) w))
          (is (<= (+ y size) h))
          (is (>= y 120) "below Back")))
      (testing "the text clears the panels"
        (let [[head sub hint] lines
              top (apply min (map second rects))
              bottom (apply max (map (fn [[_ y _ ph]] (+ y ph)) rects))]
          (is (<= (+ (:y sub) (:size sub)) top))
          (is (< (+ (:y head) (:size head)) (:y sub)))
          (is (<= (+ bottom (:size hint)) (+ (:y hint) (:size hint))))
          (is (>= (:y hint) (+ bottom (- (:row dims) (:size dims)))))))
      (testing "each name sits under its panel and inside the screen"
        (doseq [k sc/panel-keys
                :let [{:keys [s x y size]} (get labels k)
                      [_ py _ ph] (:rect (get panels k))]]
          (testing s
            (is (>= x 0))
            (is (<= (+ x (measure s size)) w))
            (is (>= y (+ py ph)))
            (is (<= (+ y size) h))))))))
