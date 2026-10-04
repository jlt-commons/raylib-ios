(ns raylib.scenes.npatch-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.scenes.npatch :as sc]
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

(defn- screen-of
  "The screen point of canvas point `cx`, `cy` under `geo`."
  [{:keys [scale ox oy]} cx cy]
  [(+ ox (* scale cx)) (+ oy (* scale (- cy sc/canvas-top)))])

(defn- near? [a b] (< (abs (- (double a) (double b))) 1e-9))

;; raylib-jlt's `rgba` (net/b12n/raylib/color.clj), `patch-pixel`
;; (npatch_drawing.clj lines 31-47) and `npatch!` (lines 49-83), copied verbatim
;; as the reference; `npatch!` returns its cells where the original draws them.
(defn- ref-rgba [r g b a]
  (bit-or (int r) (bit-shift-left (int g) 8)
          (bit-shift-left (int b) 16) (bit-shift-left (int a) 24)))

(defn- ref-patch-pixel [x y]
  (let [SRC 64
        BORDER 16
        edge (min x y (- SRC 1 x) (- SRC 1 y))
        corner? (and (< x BORDER) (< y BORDER))
        corner2? (and (>= x (- SRC BORDER)) (< y BORDER))
        corner3? (and (< x BORDER) (>= y (- SRC BORDER)))
        corner4? (and (>= x (- SRC BORDER)) (>= y (- SRC BORDER)))
        stud? (and (or corner? corner2? corner3? corner4?)
                   (< 4 edge 9))]
    (cond
      stud? (ref-rgba 255 220 90 255)
      (< edge 2) (ref-rgba 20 28 48 255)
      (< edge 6) (ref-rgba 90 150 230 255)
      (< edge 10) (ref-rgba 45 80 150 255)
      :else (ref-rgba 235 240 250 255))))

(defn- ref-npatch
  [{:keys [x y width height left right top bottom]
    :or {x 0
         y 0
         width 64
         height 64
         left 16
         right 16
         top 16
         bottom 16}}]
  (let [SRC 64
        xs [x (+ x left) (+ x (- width right))]
        ws [left (max 0 (- width left right)) right]
        ys [y (+ y top) (+ y (- height bottom))]
        hs [top (max 0 (- height top bottom)) bottom]
        us [0.0 (/ (double left) SRC) (/ (double (- SRC right)) SRC) 1.0]
        vs [0.0 (/ (double top) SRC) (/ (double (- SRC bottom)) SRC) 1.0]
        out (atom [])]
    (dotimes [row 3]
      (dotimes [col 3]
        (let [w (nth ws col)
              h (nth hs row)]
          (when (and (pos? w) (pos? h))
            (swap! out conj {:x (nth xs col)
                             :y (nth ys row)
                             :width w
                             :height h
                             :u0 (nth us col)
                             :u1 (nth us (inc col))
                             :v0 (nth vs row)
                             :v1 (nth vs (inc row))})))))
    @out))

(defn- same-cells? [a b]
  (and (= (count a) (count b))
       (every? true? (map (fn [p q] (every? #(== (% p) (% q)) [:x :y :width :height :u0 :u1 :v0 :v1]))
                          a b))))

(defn- low32 [n] (bit-and n 0xFFFFFFFF))

(deftest the-source-is-the-originals
  (let [{:keys [w h pixel]} (sc/patch-spec texel/pack)]
    (is (= [64 64] [w h]))
    (is (= 4096 (count (for [y (range h)
                             x (range w)]
                         (is (= (low32 (ref-patch-pixel x y)) (low32 (pixel x y))) (str [x y]))))))
    (testing "five colours, the studs among them"
      (is (= 5 (count (set (for [y (range h)
                                 x (range w)]
                             (pixel x y))))))
      (is (= (low32 (ref-rgba 255 220 90 255)) (pixel 6 6))))
    (testing "the byte order is raylib-jlt's rgba"
      (is (= (low32 (ref-rgba 90 150 230 255)) (texel/pack [90 150 230 255]))))))

(deftest specs-obey-gles2
  (let [{:keys [w h wrap filter]} (sc/patch-spec texel/pack)]
    (testing "a stretched edge cell must not wrap the far side in: clamp"
      (is (= :clamp wrap)))
    (is (= :nearest filter))
    (is (zero? (bit-and w (dec w))))
    (is (zero? (bit-and h (dec h))))))

(deftest nine-patches-carve-the-originals-uvs
  (testing "the cells are the original's arithmetic, for the three panels"
    (doseq [[w h] [[40.0 40.0] [100.5 77.25] [360.0 250.0] [64 64] [33.0 200.0]]]
      (is (same-cells? (ref-npatch {:x 380
                                    :y 150
                                    :width w
                                    :height h})
                       (sc/cells {:x 380
                                  :y 150
                                  :width w
                                  :height h}))
          (str "nine " [w h]))
      (is (same-cells? (ref-npatch {:x 60
                                    :y 100
                                    :width w
                                    :height 48
                                    :top 0
                                    :bottom 0})
                       (sc/cells {:x 60
                                  :y 100
                                  :width w
                                  :height 48
                                  :top 0
                                  :bottom 0}))
          (str "horizontal " w))
      (is (same-cells? (ref-npatch {:x 60
                                    :y 150
                                    :width 48
                                    :height h
                                    :left 0
                                    :right 0})
                       (sc/cells {:x 60
                                  :y 150
                                  :width 48
                                  :height h
                                  :left 0
                                  :right 0}))
          (str "vertical " h))))
  (testing "the defaults are the original's"
    (is (same-cells? (ref-npatch {}) (sc/cells {}))))
  (testing "a nine-patch is nine cells, a three-patch is three"
    (is (= 9 (count (sc/cells {:width 100
                               :height 100}))))
    (is (= 3 (count (sc/cells {:width 100
                               :height 48
                               :top 0
                               :bottom 0}))))
    (is (= 3 (count (sc/cells {:width 48
                               :height 100
                               :left 0
                               :right 0})))))
  (testing "a nine-patch's uvs split the source at 16 and 48 texels"
    (let [cs (sc/cells {:x 0
                        :y 0
                        :width 100
                        :height 100})]
      (is (= [0.0 0.25 0.75 1.0] [(:u0 (nth cs 0)) (:u1 (nth cs 0)) (:u1 (nth cs 1)) (:u1 (nth cs 2))]))
      (is (= [0.0 0.25 0.75 1.0] [(:v0 (nth cs 0)) (:v1 (nth cs 0)) (:v1 (nth cs 3)) (:v1 (nth cs 6))]))))
  (testing "the corners keep the source's size, the middle takes the slack"
    (let [cs (sc/cells {:x 10
                        :y 20
                        :width 100
                        :height 80})
          [tl t tr l c _ bl _ br] cs]
      (doseq [corner [tl tr bl br]]
        (is (= [16 16] [(:width corner) (:height corner)])))
      (is (= [68 16] [(:width t) (:height t)]))
      (is (= [16 48] [(:width l) (:height l)]))
      (is (= [68 48] [(:width c) (:height c)]))
      (is (= [10 20] [(:x tl) (:y tl)]))
      (is (= [94 84] [(:x br) (:y br)])))))

(deftest the-pointer-sizes-the-panels
  (testing "the original's clamps"
    (letfn [(sizes= [expected actual] (every? (fn [k] (== (k expected) (k actual))) (keys expected)))]
      (is (sizes= {:nine-w 40
                   :nine-h 40
                   :horiz-w 40
                   :vert-h 40} (sc/sizes 0 0)))
      (is (sizes= {:nine-w 360
                   :nine-h 250
                   :horiz-w 280
                   :vert-h 250} (sc/sizes 5000 5000)))
      (is (sizes= {:nine-w 120
                   :nine-h 150
                   :horiz-w 280
                   :vert-h 150} (sc/sizes 500 300)))))
  (testing "until a finger lands the pointer breathes, from the original's sums"
    (let [s (assoc (fresh) :frame 50)
          t (* 50 0.02)
          [px py] (sc/pointer s)]
      (is (near? (+ 420 (* 180 (Math/sin t))) px))
      (is (near? (+ 260 (* 120 (Math/sin (* 1.3 t)))) py)))
    (is (not= (sc/pointer (assoc (fresh) :frame 0)) (sc/pointer (assoc (fresh) :frame 60)))))
  (testing "a drag is the pointer, in the original's own units"
    (let [geo (sc/geometry metrics)
          at (screen-of geo 500 300)
          s (tick (fresh) :press at)
          [px py] (sc/pointer s)]
      (is (near? 500 px))
      (is (near? 300 py))
      (let [nine (take 9 (sc/panel-cells s))
            br (last nine)]
        (is (near? 120 (- (+ (:x br) (:width br)) 380)))
        (is (near? 150 (- (+ (:y br) (:height br)) 150))))
      (testing "it follows the finger while it is down"
        (let [s2 (tick s :down (screen-of geo 600 200))]
          (is (near? 600 (first (sc/pointer s2))))
          (is (near? 200 (second (sc/pointer s2))))))
      (testing "it stays where the finger left, and stops breathing"
        (let [s2 (tick s :release (screen-of geo 700 400))
              s3 (nth (iterate tick s2) 30)]
          (is (= [500.0 300.0] (mapv double (sc/pointer s3))))))))
  (testing "a drag to the far corner holds each panel to its largest"
    (let [geo (sc/geometry metrics)
          s (tick (fresh) :press (screen-of geo 5000 5000))
          cs (sc/panel-cells s)]
      (is (every? (fn [{:keys [x y width height]}] (and (<= (+ x width) 740.001) (<= (+ y height) 400.001))) cs)))))

(deftest first-frame-draws
  (let [s (fresh)]
    (is (= :npatch (:id (sc/scene))))
    (is (= "Npatch Drawing" (:title (sc/scene))))
    (doseq [screen screens
            :let [[w h] screen
                  dims (sc/dimensions {:screen screen} measure)
                  scale (:scale dims)]]
      (testing (str screen)
        (testing "fifteen quads: nine, three and three"
          (is (= 15 (count (sc/quads s dims)))))
        (doseq [[label state] [["idle" s]
                               ["smallest" (assoc s :touch [0 0])]
                               ["largest" (assoc s :touch [5000 5000])]]]
          (testing label
            (let [qs (conj (sc/quads state dims) (sc/source-quad dims))]
              (testing "every quad is on the screen, under Back and the text"
                (doseq [{:keys [x y width height]} qs]
                  (is (>= x 0))
                  (is (>= y (+ (:y2 dims) (:size dims))))
                  (is (<= (+ x width) (+ w 1e-6)))
                  (is (<= (+ y height) (+ h 1e-6)))))
              (testing "the corners are the source's 16 texels at the scale"
                (let [nine (take 9 qs)]
                  (doseq [i [0 2 6 8]]
                    (is (near? (* 16 scale) (:width (nth nine i))))
                    (is (near? (* 16 scale) (:height (nth nine i)))))))
              (testing "neighbouring cells meet"
                (let [[a b c] (take 3 qs)]
                  (is (near? (+ (:x a) (:width a)) (:x b)))
                  (is (near? (+ (:x b) (:width b)) (:x c)))))
              (testing "the source is drawn at its own size, scaled"
                (let [q (sc/source-quad dims)]
                  (is (near? (* 64 scale) (:width q)))
                  (is (near? (* 64 scale) (:height q)))
                  (is (= [0.0 0.0 1.0 1.0] [(:u0 q) (:v0 q) (:u1 q) (:v1 q)])))))))))
    (testing "the hint changes once a finger has been down"
      (is (= "breathing on its own - drag to size them" (sc/hint s)))
      (is (= "drag to size them" (sc/hint (assoc s :touch [1 1])))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [lines labels pad size y2]} (sc/dimensions {:screen screen} measure)]]
    (testing (str screen)
      (is (= 2 (count lines)))
      (is (= 4 (count labels)))
      (doseq [{:keys [s x y]
               sz :size} (concat lines labels)]
        (testing s
          (is (<= 0 x))
          (is (<= (+ x (measure s sz)) w))
          (is (<= (+ y sz) h))
          (is (>= y 120) "below Back")))
      (testing "the hint's longer form is covered"
        (let [sz (:size (first lines))]
          (doseq [s [sc/title-line sc/idle-hint sc/touch-hint]]
            (is (<= (+ pad (measure s sz)) w)))))
      (testing "the labels sit below the hint"
        (doseq [{:keys [y]} labels]
          (is (>= y (- (+ y2 (:size (second lines))) 1)) "clear of the hint")))
      (is (pos? size)))))
