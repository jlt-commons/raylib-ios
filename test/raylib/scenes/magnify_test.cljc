(ns raylib.scenes.magnify-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.perlin :as perlin]
            [raylib.scenes.magnify :as sc]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def metrics {:screen [1206 2334]})

;; A screen whose field is exactly the original's 800 by 450: Back takes the top
;; 120, so sx = sy = k = 1 and every number is the original's own.
(def original-metrics {:screen [800 570]})

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(defn- fresh
  ([] (fresh metrics))
  ([m] (first ((:init (sc/scene)) {:metrics m}))))

(defn- step
  ([state phase pos] (step state phase pos metrics))
  ([state phase pos m]
   (first ((:update (sc/scene)) state {:metrics m
                                       :pointer {:phase phase
                                                 :position pos}}))))

(defn- idle-step
  ([state] (idle-step state metrics))
  ([state m] (first ((:update (sc/scene)) state {:metrics m
                                                 :pointer {:phase :idle
                                                           :position nil}}))))

(defn- in-field [m dx dy]
  (let [{:keys [x y]} (:field (sc/layout m))]
    [(+ x dx) (+ y dy)]))

(deftest the-lens-is-the-originals
  (testing "LENS 220, ZOOM 3.0, SEGMENTS 64 and the 800 by 450 window"
    (is (= 220 sc/lens))
    (is (= 3.0 sc/zoom))
    (is (= 64 sc/segments))
    (is (= [800 450] [sc/window-w sc/window-h])))
  (testing "the five hidden markers"
    (is (= [[170 150] [560 120] [300 330] [660 300] [430 210]] sc/hidden-spots)))
  (testing "the ring: 4 thick on the rim of a 220 lens, a full circle in 64 wedges"
    (is (= [300 400 106.0 110.0 0 360 64] (sc/ring 300 400))))
  (testing "the half-lens is 110"
    (is (= 110.0 (:half (sc/layout metrics)))))
  (testing "the disc is 64 wedges of three vertices, each x y u v"
    (let [v (sc/disc-verts 300.0 400.0 110.0 sc/segments)]
      (is (= (* 64 3 4) (count v)))
      (testing "each wedge is the centre at uv (0.5, 0.5), then the next rim point, then this one"
        (let [[cx cy cu cv x1 y1 u1 v1 x0 y0 u0 v0] (take 12 v)
              a1 (/ (* 2.0 Math/PI) 64)]
          (is (= [300.0 400.0 0.5 0.5] [cx cy cu cv]))
          (is (near? (+ 300.0 (* 110.0 (Math/cos a1))) x1))
          (is (near? (+ 400.0 (* 110.0 (Math/sin a1))) y1))
          (is (near? (+ 0.5 (* 0.5 (Math/cos a1))) u1))
          (testing "v is flipped, because a render target is stored bottom-up"
            (is (near? (- 1.0 (+ 0.5 (* 0.5 (Math/sin a1)))) v1)))
          (is (near? 410.0 x0))
          (is (near? 400.0 y0))
          (is (near? 1.0 u0))
          (is (near? 0.5 v0))))
      (testing "every rim point is 110 from the centre and its texcoord is on the unit circle"
        (doseq [i (range 192)
                :when (not= 0 (mod i 3))
                :let [o (* 4 i)
                      [x y u w] (subvec v o (+ o 4))]]
          (is (near? 110.0 (Math/sqrt (+ (* (- x 300.0) (- x 300.0)) (* (- y 400.0) (- y 400.0))))))
          (is (near? 0.25 (+ (* (- u 0.5) (- u 0.5)) (* (- w 0.5) (- w 0.5))))))))))

(deftest the-backdrop-is-the-perlin-image
  (testing "the call the original makes: 800 by 450, offsets 0, scale 6.0"
    (is (= {:w 800
            :h 450
            :offset-x 0
            :offset-y 0
            :scale 6.0} sc/backdrop-spec)))
  (testing "sampled texels are the pure model's, through the spec's own parameters"
    (doseq [[x y] [[0 0] [1 0] [400 225] [799 449] [123 77] [640 300]]]
      (is (= (perlin/perlin-grey 800 450 0 0 6.0 x y) (sc/backdrop-texel x y)))))
  (testing "and they are the bytes GenImagePerlinNoise answered when measured from the C"
    ;; Measured 2026-10-04 on raylib 6.0 (libraylib.dylib), R byte of each texel.
    (is (= {[0 0] 127
            [1 0] 123
            [400 225] 88
            [799 449] 138
            [123 77] 74
            [640 300] 71}
           (into {} (map (fn [[x y]] [[x y] (sc/backdrop-texel x y)]))
                 [[0 0] [1 0] [400 225] [799 449] [123 77] [640 300]])))))

(deftest the-scene-is-the-originals-laid-over-the-field
  (let [lay (sc/layout original-metrics)
        plan (sc/world-plan lay 0 0 1.0 false)
        items (:items plan)]
    (testing "an 800 by 450 field is the original's own numbers"
      (is (= {:x 0
              :y 120
              :w 800
              :h 450} (:field lay)))
      (is (= [1.0 1.0 1.0] [(:sx lay) (:sy lay) (:k lay)])))
    (testing "the backdrop is the field"
      (is (= {:x 0
              :y 0
              :width 800.0
              :height 450.0} (:backdrop plan))))
    (testing "seven squares of 56 at x = 60 + 100 i, y = 90, each with a circle of 22 at (x + 28, 300)"
      (is (= 14 (count items)))
      (doseq [i (range 7)
              :let [x (+ 60 (* i 100))
                    [rect circle] (subvec items (* 2 i) (+ 2 (* 2 i)))]]
        (is (= [:rect (double x) 90.0 56.0 56.0 [(+ 40 (* i 25)) 90 (- 210 (* i 20)) 255]]
               (mapv #(if (number? %) (double %) %) rect)))
        (is (= [:circle (double (+ x 28)) 300.0 22.0 [230 (+ 60 (* i 20)) 70 255]]
               (mapv #(if (number? %) (double %) %) circle)))))
    (testing "the hidden markers add 10 circles, a LIME one of 11 and a DARKGREEN one of 6 on each"
      (let [revealed (:items (sc/world-plan lay 0 0 1.0 true))
            extra (subvec revealed 14)]
        (is (= 24 (count revealed)))
        (is (= items (subvec revealed 0 14)))
        (is (= [[:circle 170.0 150.0 11.0 [0 158 47 255]]
                [:circle 170.0 150.0 6.0 [0 117 44 255]]]
               (mapv (fn [it] (mapv #(if (number? %) (double %) %) it)) (subvec extra 0 2))))))
    (testing "magnified 3x about a point, the point lands in the middle of the lens"
      (let [[ox oy] (sc/lens-offset lay [300.0 200.0])
            plan3 (sc/world-plan lay ox oy 3.0 true)]
        (is (= [-790.0 -490.0] [ox oy]))
        (is (= [2400.0 1350.0] [(:width (:backdrop plan3)) (:height (:backdrop plan3))]))
        (is (= 110.0 (+ (:x (:backdrop plan3)) (* 300.0 3.0))))
        (is (= 110.0 (+ (:y (:backdrop plan3)) (* 200.0 3.0))))
        (testing "every length is three times as long"
          (let [[_ _ _ w h] (first (:items plan3))]
            (is (= [168.0 168.0] [w h]))))))))

(deftest the-field-fills-the-screen
  (doseq [screen screens
          :let [lay (sc/layout {:screen screen})
                {:keys [field sx sy k]} lay
                plan (sc/world-plan lay (:x field) (:y field) 1.0 false)]]
    (testing (str screen)
      (is (= (:w field) (int (first screen))))
      (is (= (+ 120 (:h field)) (second screen)))
      (is (near? k (min sx sy)))
      (testing "everything lies inside the field"
        (doseq [item (:items plan)
                :let [[kind a b c d] item
                      [x0 y0 x1 y1] (if (= :rect kind)
                                      [a b (+ a c) (+ b d)]
                                      [(- a c) (- b c) (+ a c) (+ b c)])]]
          (is (<= (:x field) x0 x1 (+ (:x field) (:w field))) (str item))
          (is (<= (:y field) y0 y1 (+ (:y field) (:h field))) (str item)))))))

(deftest a-finger-takes-the-lens
  (let [s0 (fresh)
        lay (sc/layout metrics)]
    (testing "before a touch the lens walks the idle path, laid over the field"
      (is (not (:moved? s0)))
      (let [s (nth (iterate idle-step s0) 100)
            [x y] (:lens s)]
        (is (= (sc/idle-position lay 100) (:lens s)))
        (is (not= (:lens s0) (:lens s)))
        (is (<= 110.0 x (- (get-in lay [:field :w]) 110.0)))
        (is (<= 110.0 y (- (get-in lay [:field :h]) 110.0)))))
    (testing "the idle path is the original's: W/2 + 250 sin t, H/2 + 120 sin 1.7 t, t = 0.02 a frame"
      (let [l1 (sc/layout original-metrics)
            t (* 100 0.02)
            [x y] (sc/idle-position l1 100)]
        (is (near? (+ 400.0 (* 250.0 (Math/sin t))) x))
        (is (near? (+ 225.0 (* 120.0 (Math/sin (* t 1.7)))) y))))
    (testing "a press in the field takes the lens to the finger, in field pixels"
      (let [s (step s0 :press (in-field metrics 500 900))]
        (is (:moved? s))
        (is (= [500.0 900.0] (:lens s)))))
    (testing "it follows the finger while it stays down"
      (let [s (-> s0 (step :press (in-field metrics 500 900)) (step :down (in-field metrics 520 950)))]
        (is (= [520.0 950.0] (:lens s)))))
    (testing "and stays where the finger left it, however long after"
      (let [s (-> s0 (step :press (in-field metrics 500 900)) (step :down (in-field metrics 520 950))
                  (step :release (in-field metrics 520 950)))
            later (nth (iterate idle-step s) 50)]
        (is (= [520.0 950.0] (:lens s)))
        (is (= [520.0 950.0] (:lens later)))
        (is (:moved? later))))
    (testing "the centre is held a half-lens inside the field, so the disc never leaves it"
      (let [{:keys [w h]} (:field lay)
            s (step s0 :press (in-field metrics 0 0))
            s2 (step s0 :press (in-field metrics (dec w) (dec h)))]
        (is (= [110.0 110.0] (:lens s)))
        (is (= [(- w 110.0) (- h 110.0)] (:lens s2)))))
    (testing "a finger that lands in the Back region does not take it"
      (let [s (step s0 :press [100 60])]
        (is (not (:moved? s)))
        (is (not (:held? s)))))
    (testing "a finger that slides in from outside the field does not take it"
      (let [s (-> s0 (step :press [100 60]) (step :down (in-field metrics 500 900)))]
        (is (not (:moved? s)))))
    (testing "a turn pulls the lens back inside the new field"
      (let [s (step s0 :press (in-field metrics 1100 2000))
            m2 {:screen [800 450]}
            t (idle-step s m2)
            lay2 (sc/layout m2)]
        (is (<= 110.0 (first (:lens t)) (- (get-in lay2 [:field :w]) 110.0)))
        (is (<= 110.0 (second (:lens t)) (max 110.0 (- (get-in lay2 [:field :h]) 110.0))))))))

(deftest first-frame-draws
  (is (= :magnify (:id (sc/scene))))
  (is (= "Magnifying Glass" (:title (sc/scene))))
  (doseq [screen screens
          :let [m {:screen screen}
                lay (sc/layout m)
                {:keys [field half]} lay
                s (idle-step (fresh m) m)
                [lx ly] (:lens s)
                v (sc/disc-verts (+ (:x field) lx) (+ (:y field) ly) half sc/segments)]]
    (testing (str screen)
      (testing "the lens disc lies inside the field"
        (doseq [i (range (quot (count v) 4))
                :let [x (nth v (* 4 i))
                      y (nth v (+ 1 (* 4 i)))]]
          (is (<= (- (:x field) 1e-6) x (+ (:x field) (:w field) 1e-6)))
          (is (<= (- (:y field) 1e-6) y (+ (:y field) (:h field) 1e-6)))))
      (testing "the magnified pass draws 14 shapes and 10 markers, the screen's 14"
        (let [[ox oy] (sc/lens-offset lay (:lens s))]
          (is (= 24 (count (:items (sc/world-plan lay ox oy sc/zoom true)))))
          (is (= 14 (count (:items (sc/world-plan lay (:x field) (:y field) 1.0 false))))))))))

(deftest text-lines-fit-the-field
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                {:keys [field lines]} dims
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (= #{:idle :moved} (set (keys lines))))
      (is (= "move the pointer to take over the lens" (:s (:idle lines))))
      (is (= "five markers are drawn only inside the lens" (:s (:moved lines))))
      (doseq [{:keys [s x y size]} (vals lines)]
        (is (<= 0 x))
        (is (<= (+ x (measure s size)) w) s)
        (is (<= (+ (:y field) y size) h) s)
        (is (>= (+ (:y field) y) (+ back-y back-h)) "below Back")))))
