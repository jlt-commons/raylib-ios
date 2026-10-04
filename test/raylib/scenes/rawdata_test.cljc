(ns raylib.scenes.rawdata-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.rawdata :as sc]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def metrics {:screen [1206 2334]})

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- fresh [] (first ((:init (sc/scene)) {:metrics metrics})))

(defn- tick [state]
  (first ((:update (sc/scene)) state {:metrics metrics
                                      :pointer {:phase :idle
                                                :position nil}})))

;; raylib-jlt's `rgba` (net/b12n/raylib/color.clj), ORANGE and GOLD, and the
;; original's `checker` and `channels` (raw_data.clj lines 40-56), copied as the
;; reference.
(defn- ref-rgba [r g b a]
  (bit-or (int r) (bit-shift-left (int g) 8)
          (bit-shift-left (int b) 16) (bit-shift-left (int a) 24)))

(def ^:private CHECK 32)
(def ^:private LIVE 128)

(defn- ref-checker [x y]
  (if (even? (+ (quot x CHECK) (quot y CHECK)))
    (ref-rgba 255 161 0 255)
    (ref-rgba 255 203 0 255)))

(defn- ref-channels [t x y]
  (let [fx (/ (double x) LIVE)
        fy (/ (double y) LIVE)
        r (int (* 255 (Math/abs (Math/sin (+ (* fx 6.0) t)))))
        g (int (* 255 (Math/abs (Math/sin (+ (* fy 6.0) (* t 0.7))))))
        b (int (* 255 (Math/abs (Math/cos (+ (* (+ fx fy) 4.0) (* t 1.3))))))]
    (ref-rgba r g b 255)))

(defn- low32 [n] (bit-and n 0xFFFFFFFF))

(defn- each-texel [n f]
  (for [y (range n)
        x (range n)]
    (f x y)))

(defn- drawn-checker
  "The texel the GPU would sample for the original's texel `a`, `b` of its 256 by
  256 panel, through `quad`'s texcoords over the 64 by 64 repeating texture:
  nearest filtering at the texel's centre, with REPEAT wrapping."
  [{:keys [w h pixel]} {:keys [u1 v1]} a b]
  (let [u (* u1 (/ (+ a 0.5) 256.0))
        v (* v1 (/ (+ b 0.5) 256.0))
        frac (fn [n] (- n (Math/floor n)))]
    (pixel (int (Math/floor (* (frac u) w))) (int (Math/floor (* (frac v) h))))))

(deftest the-checker-is-the-originals
  (is (= 32 sc/check))
  (let [{:keys [w h wrap filter pixel]
         :as spec} (sc/checker-spec)
        geo (sc/dimensions {:screen [1206 2334]} (fn [s size] (* 0.6 size (count s))))
        q (sc/quad geo :checker)]
    (is (= [64 64] [w h]) "one period, 4096 texels in place of 65536")
    (is (= :repeat wrap) "GLES2 repeats only a power of two, and 64 is one")
    (is (nil? filter))
    (is (= [4.0 4.0] [(:u1 q) (:v1 q)]))
    (is (every? nil? [(:u0 q) (:v0 q)]) "absent, so quad!'s default of 0")
    (testing "every texel of the original's 256 by 256 panel is the colour drawn there"
      (is (every? true? (each-texel 256 #(= (low32 (ref-checker % %2))
                                            (low32 (drawn-checker spec q % %2)))))))
    (testing "two colours, squares 32 across"
      (is (= 2 (count (set (each-texel 64 pixel)))))
      (is (= (pixel 0 0) (pixel 31 31)))
      (is (not= (pixel 0 0) (pixel 32 0)))
      (is (not= (pixel 0 0) (pixel 0 32)))
      (is (= (pixel 0 0) (pixel 32 32)))))
  (testing "the live panel is not repeated"
    (let [q (sc/quad (sc/dimensions {:screen [1206 2334]} (fn [s size] (* 0.6 size (count s)))) :live)]
      (is (= [1.0 1.0] [(:u1 q) (:v1 q)]))))
  (testing "the colour fn agrees with the spec"
    (is (= [255 161 0 255] (sc/checker-colour 0 0)))
    (is (= [255 203 0 255] (sc/checker-colour 32 0)))))

(deftest the-live-panel-is-the-originals-arithmetic
  (doseq [frame [0 100 777]
          :let [t (* frame 0.03)
                {:keys [w h pixel]} (sc/live-spec frame)]]
    (testing (str "frame " frame)
      (is (= t (sc/live-time frame)))
      (is (= [128 128] [w h]))
      (is (every? true? (each-texel 128 #(= (low32 (ref-channels t % %2)) (low32 (pixel % %2))))))
      (testing "the colour fn packs to the same texel"
        (is (every? true? (each-texel 128 #(let [[r g b a] (sc/live-colour t % %2)]
                                             (= (low32 (ref-rgba r g b a)) (low32 (pixel % %2)))))))))))

(deftest the-live-panel-moves-with-time
  (let [a (:pixel (sc/live-spec 0))
        b (:pixel (sc/live-spec 100))]
    (is (not= (a 10 10) (b 10 10)))
    (is (< 1000 (count (set (each-texel 128 a)))) "a spread of colours, not a flat fill")))

(deftest the-band-refreshes-every-row-once-a-period
  (is (= 3 sc/band-rows))
  (is (= 43 sc/period) "128 rows in bands of 3, the last one short")
  (let [bands (map sc/band (range sc/period))]
    (testing "no frame refills more than the band, and only the last is shorter"
      (is (every? (fn [[_ n]] (<= 1 n sc/band-rows)) bands))
      (is (every? (fn [[_ n]] (= sc/band-rows n)) (butlast bands)))
      (is (= [126 2] (last bands))))
    (testing "the bands tile the panel with no gap and no overlap"
      (is (= (range 128) (mapcat (fn [[y0 n]] (range y0 (+ y0 n))) bands))))
    (testing "and start again at the top"
      (is (= (sc/band 0) (sc/band sc/period)))
      (is (= (sc/band 5) (sc/band (+ 5 (* 3 sc/period))))))
    (testing "the first band is the top rows"
      (is (= [0 sc/band-rows] (sc/band 0))))))

;; The draw runs after the update, so it sees the state of the frame just
;; stepped. A mock texture takes each frame's band the way `texture/band!` does
;; and must end up holding, for every row, the pixels of the last frame whose
;; band covered it.
(deftest the-bands-uploaded-leave-each-row-as-its-own-frame-drew-it
  (let [frames (* 2 sc/period)
        tex (volatile! {})
        last-frame (volatile! {})]
    (reduce (fn [s _]
              (let [s (tick s)
                    [y0 n] (sc/band (:frame s))
                    {:keys [pixel]} (sc/live-spec (:frame s))]
                (doseq [y (range y0 (+ y0 n))]
                  (vswap! tex assoc y (mapv #(pixel % y) (range 128)))
                  (vswap! last-frame assoc y (:frame s)))
                s))
            (fresh) (range frames))
    (is (= 128 (count @tex)) "every row was refreshed")
    (is (= (:frame (nth (iterate tick (fresh)) frames)) (apply max (vals @last-frame))))
    (is (every? (fn [y]
                  (let [{:keys [pixel]} (sc/live-spec (@last-frame y))]
                    (= (@tex y) (mapv #(pixel % y) (range 128)))))
                (range 128)))
    (testing "no row is older than one sweep"
      (is (every? #(<= (- frames %) sc/period) (vals @last-frame))))))

(deftest the-frame-counter-advances
  (let [s (fresh)]
    (is (= 0 (:frame s)))
    (is (= 1 (:frame (tick s))))
    (is (= 30 (:frame (nth (iterate tick s) 30))))
    (is (= [1206 2334] (:screen s)))))

(deftest first-frame-draws
  (is (= :rawdata (:id (sc/scene))))
  (is (= "Raw Data" (:title (sc/scene))))
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                rects (map #(:rect (get (:panels dims) %)) sc/panel-keys)]]
    (testing (str screen)
      (testing "two square panels, equal, inside the screen and below Back"
        (is (= 2 (count rects)))
        (is (= 1 (count (set (map (fn [[_ _ pw ph]] [pw ph]) rects)))))
        (doseq [[x y pw ph] rects]
          (is (= pw ph))
          (is (>= x 0))
          (is (<= (+ x pw) w))
          (is (>= y 120))
          (is (<= (+ y ph) h))))
      (testing "panels do not overlap"
        (let [[[ax ay aw ah] [bx by bw bh]] rects]
          (is (or (<= (+ ax aw) bx) (<= (+ bx bw) ax)
                  (<= (+ ay ah) by) (<= (+ by bh) ay)))))
      (testing "the panels are big enough to read"
        (is (>= (:cell dims) (* 0.2 (min w h)))))
      (testing "the quad is the rect"
        (let [q (sc/quad dims :live)]
          (is (= (:rect (get (:panels dims) :live)) [(:x q) (:y q) (:width q) (:height q)]))))
      (testing "the outline is four rects touching the panel's edges"
        (let [[x y pw ph] (:rect (get (:panels dims) :checker))
              rs (sc/outline-rects dims :checker)]
          (is (= 4 (count rs)))
          (doseq [[rx ry rw rh] rs]
            (is (>= rx x))
            (is (>= ry y))
            (is (<= (+ rx rw) (+ x pw)))
            (is (<= (+ ry rh) (+ y ph)))))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [lines labels panels]} (sc/dimensions {:screen screen} measure)
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
        (let [[head sub caption] lines
              top (apply min (map (fn [[_ y]] y) rects))
              bottom (apply max (map (fn [[_ y _ ph]] (+ y ph)) rects))]
          (is (<= (+ (:y sub) (:size sub)) top))
          (is (< (+ (:y head) (:size head)) (:y sub)))
          (is (<= bottom (:y caption)))))
      (testing "each name sits over its panel and inside the screen"
        (doseq [k sc/panel-keys
                :let [{:keys [s x y size]} (get labels k)
                      [px py] (:rect (get panels k))]]
          (testing s
            (is (>= x 0))
            (is (= px x))
            (is (<= (+ x (measure s size)) w))
            (is (<= (+ y size) py))
            (is (>= y 120))))))))
