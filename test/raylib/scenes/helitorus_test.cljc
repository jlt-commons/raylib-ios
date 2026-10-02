(ns raylib.scenes.helitorus-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.helitorus :as h]))

(def portrait {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def dt (/ 1.0 60))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(def start (first ((:init (h/scene)) {:metrics portrait})))
(def geo (h/geometry portrait))

(defn- step
  "One frame at 60 fps with `points` as the touch points."
  ([state points] (step state portrait points))
  ([state metrics points]
   (h/advance state {:metrics metrics
                     :touch-points (vec points)
                     :delta-seconds dt})))

(defn- idle [state] (step state []))

(defn- button-centre [g id]
  (let [[x y w hh] (:rect (first (filter #(= id (:id %)) (:buttons g))))]
    [(+ x (* 0.5 w)) (+ y (* 0.5 hh))]))

(defn- field-centre [g]
  (let [[fx fy fw fh] (:field g)]
    [(+ fx (* 0.5 fw)) (+ fy (* 0.5 fh))]))

(defn- at [[x y] dx dy] [(+ x dx) (+ y dy)])

(defn- computed
  "Fresh buffers after one `compute!` of `state` laid out for `metrics`."
  [state metrics]
  (let [bufs (h/make-buffers)]
    (h/compute! bufs (h/params state (h/geometry metrics)))
    bufs))

(defn- snapshot [bufs]
  (mapv #(vec (get bufs %)) [:sx :sy :shade :ring-z :order]))

(deftest compute-is-deterministic
  (let [a (computed start portrait)
        b (computed start portrait)
        blank (h/make-buffers)]
    (is (= (snapshot a) (snapshot b)) "the same parameters give the same arrays")
    (is (not= (snapshot a) (snapshot blank)) "and compute! wrote something")
    (is (some pos? (take (* (:nu start) h/nv) (vec (:shade a)))) "some vertices are lit")
    (testing "recomputing into the same buffers changes nothing"
      (let [before (snapshot a)]
        (h/compute! a (h/params start geo))
        (is (= before (snapshot a)))))
    (testing "another clock gives other points"
      (let [c (computed (assoc start :clock 1.0) portrait)]
        (is (not= (vec (:shade a)) (vec (:shade c))))))
    (testing "the shades index the palette"
      (is (every? #(<= 0 % 63) (take (* (:nu start) h/nv) (vec (:shade a))))))))

(deftest projected-points-stay-near-the-field
  (doseq [screen screens
          rot-y [0.0 1.0 2.0 3.0 4.0 5.0]
          rot-x [-1.45 0.0 0.55 1.45]
          twists [3 14 24]
          nu [60 260 900]
          :let [metrics {:screen screen}
                g (h/geometry metrics)
                [fx fy fw fh] (:field g)
                state (assoc start :rot-y rot-y :rot-x rot-x :twists twists :nu nu)
                bufs (computed state metrics)
                n (* nu h/nv)
                xs (take n (vec (:sx bufs)))
                ys (take n (vec (:sy bufs)))]]
    (testing (str screen " " rot-y " " rot-x " " twists " " nu)
      (is (= n (count xs)))
      (is (every? #(<= fx % (+ fx fw)) xs))
      (is (every? #(<= fy % (+ fy fh)) ys)))))

(deftest rings-sort-far-to-near
  (doseq [nu [60 260 900]
          rot-y [0.0 2.5]
          :let [state (assoc start :nu nu :rot-y rot-y)
                bufs (computed state portrait)
                order (take nu (vec (:order bufs)))
                zs (mapv #(aget (:ring-z bufs) %) order)]]
    (testing (str nu " rings at " rot-y)
      (is (= (range nu) (sort order)) "the order is a permutation of the rings")
      (is (apply >= zs) "far rings first")
      (is (> (first zs) (last zs)) "the rings do differ in depth")))
  (testing "a turned surface resorts the order it kept"
    (let [bufs (computed start portrait)
          before (vec (:order bufs))]
      (h/compute! bufs (h/params (assoc start :rot-y 2.0) geo))
      (is (not= before (vec (:order bufs))))
      (let [zs (mapv #(aget (:ring-z bufs) %) (take (:nu start) (vec (:order bufs))))]
        (is (apply >= zs)))))
  (testing "changing the ring count rebuilds the order"
    (let [bufs (computed start portrait)]
      (h/compute! bufs (h/params (assoc start :nu 100) geo))
      (is (= (range 100) (sort (take 100 (vec (:order bufs)))))))))

(deftest a-drag-turns-and-coasts
  (let [scale (:scale geo)
        p (field-centre geo)
        s0 (assoc start :vel-x 0.0 :vel-y 0.4)
        pressed (step s0 [p])
        moved (step pressed [(at p 30.0 20.0)])
        decay (Math/pow 0.94 (/ dt 0.016))]
    (testing "the press only anchors the drag"
      (is (near? (:rot-y s0) (- (:rot-y pressed) (* 0.4 dt))) "it turns by the remembered velocity alone")
      (is (= (:vel-y s0) (:vel-y pressed)))
      (is (some? (:drag pressed))))
    (testing "a moving finger turns by the original's 0.008 a pixel, per unit of scale"
      (is (near? (+ (:rot-y pressed) (/ (* 30.0 0.008) scale) (* (:vel-y moved) dt))
                 (:rot-y moved)))
      (is (near? (+ (:rot-x pressed) (/ (* 20.0 0.008) scale) (* (:vel-x moved) dt))
                 (:rot-x moved)))
      (testing "and remembers the motion as a velocity"
        (is (near? (/ (* 30.0 0.35) scale) (:vel-y moved)))
        (is (near? (/ (* 20.0 0.35) scale) (:vel-x moved)))))
    (testing "lifting the finger coasts: the velocity decays to the idle turn"
      (let [c1 (idle moved)
            c2 (idle c1)]
        (is (nil? (:drag c1)))
        (is (near? (+ (* (:vel-y moved) decay) (* 0.16 (- 1.0 decay))) (:vel-y c1)))
        (is (near? (* (:vel-x moved) decay) (:vel-x c1)))
        (is (> (:rot-y c1) (:rot-y moved)) "it keeps turning")
        (is (< (abs (- (:vel-y c2) 0.16)) (abs (- (:vel-y c1) 0.16))) "toward the idle turn")
        (let [late (nth (iterate idle moved) 600)]
          (is (< (abs (- (:vel-y late) 0.16)) 1e-6))
          (is (< (abs (:vel-x late)) 1e-6)))))
    (testing "rot-x stays within the limit"
      (let [down (-> pressed (step [(at p 0.0 5000.0)]))
            up (-> pressed (step [(at p 0.0 -5000.0)]))]
        (is (near? 1.45 (:rot-x down)))
        (is (near? -1.45 (:rot-x up)))
        (is (<= -1.45 (:rot-x (nth (iterate idle down) 200)) 1.45))))
    (testing "a finger on a button or under Back turns nothing"
      (let [b (button-centre geo :detail-plus)
            on-button (step (step s0 [b]) [(at b 40.0 0.0)])
            under-back (step (step s0 [[100.0 60.0]]) [[300.0 100.0]])]
        (is (nil? (:drag on-button)))
        (is (nil? (:drag under-back)))
        (is (near? (:rot-y (idle (idle s0))) (:rot-y under-back))
            "only the remembered spin turns it")))
    (testing "a second field finger ends the drag"
      (let [two (step moved [(at p 30.0 20.0) (at p 100.0 100.0)])]
        (is (nil? (:drag two)))))
    (testing "the release position is never read"
      (let [lifted (h/advance moved {:metrics portrait
                                     :pointer {:phase :release
                                               :position [0.0 0.0]}
                                     :touch-points []
                                     :delta-seconds dt})]
        (is (= (:vel-y lifted) (:vel-y (idle moved))))
        (is (= (:rot-y lifted) (:rot-y (idle moved))))))
    (testing "a rotation of the phone drops the drag"
      (let [land {:screen [2334 1206]}
            q (field-centre (h/geometry land))
            turned (step moved land [q])]
        (is (= [2334 1206] (:screen turned)))
        (is (near? (* dt (:vel-y moved)) (- (:rot-y turned) (:rot-y moved)))
            "the new screen's finger only anchors, so nothing jumps")))))

(deftest windings-and-detail-clamp
  (let [g geo
        wm (button-centre g :windings-minus)
        wp (button-centre g :windings-plus)
        dm (button-centre g :detail-minus)
        dp (button-centre g :detail-plus)]
    (testing "windings change once a press"
      (let [s1 (step start [wp])
            s2 (step s1 [wp])
            s3 (step (idle s2) [wp])]
        (is (= (inc (:twists start)) (:twists s1)))
        (is (= (:twists s1) (:twists s2)) "holding does not repeat")
        (is (= (+ 2 (:twists start)) (:twists s3)) "a fresh press does")
        (is (= (dec (:twists start)) (:twists (step start [wm]))))))
    (testing "windings clamp at 3 and 24"
      (is (= 24 (:twists (step (assoc start :twists 24) [wp]))))
      (is (= 3 (:twists (step (assoc start :twists 3) [wm]))))
      (is (= 24 (:twists (step (assoc start :twists 23) [wp]))))
      (is (= 3 (:twists (step (assoc start :twists 4) [wm])))))
    (testing "detail moves by 4 every frame it is held"
      (let [frames (take 4 (iterate #(step % [dp]) start))]
        (is (= [260 264 268 272] (mapv :nu frames))))
      (is (= [260 256 252 248] (mapv :nu (take 4 (iterate #(step % [dm]) start)))))
      (is (= 260 (:nu (step start [dp dm]))) "both held cancel out"))
    (testing "detail clamps at 60 and 900"
      (is (= 900 (:nu (step (assoc start :nu 898) [dp]))))
      (is (= 900 (:nu (step (assoc start :nu 900) [dp]))))
      (is (= 60 (:nu (step (assoc start :nu 62) [dm]))))
      (is (= 60 (:nu (step (assoc start :nu 60) [dm])))))
    (testing "a button held with a finger in the field turns the field too"
      (let [p (field-centre g)
            s (-> start (step [p dp]) (step [(at p 30.0 0.0) dp]))]
        (is (= 268 (:nu s)))
        (is (some? (:drag s)))))
    (testing "the start is the original's"
      (is (= 260 (:nu start)))
      (is (= 14 (:twists start)))
      (is (= 250.0 (:zoom start))))))

(defn- pair [c half] [(at c (- half) 0.0) (at c half 0.0)])

(deftest a-pinch-zooms-within-limits
  (let [c (field-centre geo)
        before (-> start (step (pair c 50.0)) (step (pair c 50.0)))]
    (testing "the first two-finger frame only records the pinch"
      (let [first-frame (step start (pair c 50.0))]
        (is (= (:zoom start) (:zoom first-frame)))
        (is (some? (:pinch first-frame)))))
    (testing "spreading doubles the zoom, pulling in halves it"
      (is (near? 250.0 (:zoom before)))
      (is (near? 500.0 (:zoom (step before (pair c 100.0)))))
      (is (near? 125.0 (:zoom (step before (pair c 25.0))))))
    (testing "the zoom is held to 110 and 520"
      (is (near? 520.0 (:zoom (step before (pair c 400.0)))))
      (is (near? 110.0 (:zoom (step before (pair c 2.0)))))
      (let [top (step before (pair c 400.0))]
        (is (near? 260.0 (:zoom (step top (pair c 200.0)))) "and it comes back at once")))
    (testing "swapping the two points changes nothing"
      (let [swapped (step before (reverse (pair c 100.0)))]
        (is (near? 500.0 (:zoom swapped)))))
    (testing "a pinch turns nothing"
      (let [after (step before (pair c 100.0))]
        (is (nil? (:drag after)))
        (is (near? (* dt (:vel-y after)) (- (:rot-y after) (:rot-y before))) "it only coasts")))
    (testing "coincident fingers never collapse the zoom"
      (let [s (-> start (step [c c]) (step [c c]) (step (pair c 50.0)))]
        (is (near? 250.0 (:zoom s)))))
    (testing "lifting one finger does not jump"
      (let [pinched (step before (pair c 100.0))
            one (step pinched [(at c 100.0 0.0)])
            later (step one [(at c 300.0 200.0)])
            fresh (step later (pair c 60.0))
            grown (step fresh (pair c 120.0))]
        (is (= (:zoom pinched) (:zoom one)))
        (is (nil? (:pinch one)))
        (is (= (:zoom pinched) (:zoom later)))
        (is (= (:zoom pinched) (:zoom fresh)))
        (is (near? (min 520.0 (* 2.0 (:zoom pinched))) (:zoom grown)))))
    (testing "a finger on a button is no part of the pinch"
      (let [b (button-centre geo :windings-plus)
            s (-> start (step [(at c -50.0 0.0) b]) (step [(at c -90.0 0.0) b]))]
        (is (= (:zoom start) (:zoom s)))))
    (testing "three field fingers pause the pinch"
      (let [three (step before [(at c -100.0 0.0) (at c 100.0 0.0) (at c 0.0 300.0)])]
        (is (= (:zoom before) (:zoom three)))
        (is (nil? (:pinch three)))))
    (testing "a rotation of the phone drops the pinch"
      (let [land {:screen [2334 1206]}
            q (field-centre (h/geometry land))
            turned (step before land (pair q 100.0))]
        (is (= (:zoom before) (:zoom turned)))
        (is (some? (:pinch turned)) "and a new pinch begins on the new screen")
        (is (near? (* 2.0 (:zoom before))
                   (:zoom (step turned land (pair q 200.0))))
            "which zooms from the next frame")))))

(deftest buttons-avoid-back
  (doseq [screen screens
          :let [[w hgt] screen
                g (h/geometry {:screen screen})
                [_ back-y _ back-h] gesture/back-region
                [fx fy fw fh] (:field g)
                rects (map :rect (:buttons g))]]
    (testing (str screen)
      (is (= [:windings-minus :windings-plus :detail-minus :detail-plus]
             (mapv :id (:buttons g))))
      (doseq [[x y bw bh] rects]
        (is (>= y (+ back-y back-h)))
        (is (>= x 0))
        (is (<= (+ x bw) w))
        (is (<= (+ y bh) hgt))
        (is (>= y (+ fy fh)) "below the field"))
      (is (>= fy (+ back-y back-h)) "the field is below Back")
      (is (= [0.0 (double w)] [fx fw]))
      (is (pos? fh))
      (doseq [[[x1 _ w1 _] [x2 _ _ _]] (partition 2 1 rects)]
        (is (<= (+ x1 w1) x2) "the buttons do not overlap"))
      (testing "the centre and the scale come from the field"
        (is (near? (+ fy (* 0.5 fh)) (second (:centre g))))
        (is (near? (* 0.5 w) (first (:centre g))))
        (is (near? (min (/ w 1000.0) (/ fh 560.0)) (:scale g)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w _] screen
                dims (h/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [_ fy _ _] (:field dims)]]
    (testing (str screen)
      (is (= 3 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) fy) s))
      (testing "the lines do not overlap"
        (doseq [[a b] (partition 2 1 (:lines dims))]
          (is (<= (+ (:y a) (:size a)) (:y b)))))
      (testing "the longest HUD the scene can print is the one measured"
        (let [worst (h/hud-line 9999.0 999.0 999.0)]
          (is (= worst (:s (first (:lines dims)))))
          (is (<= (count (h/hud-line 60.0 3.2 4.5)) (count worst)))))
      (testing "every label fits inside its button"
        (doseq [{:keys [rect label label-x label-y label-size]} (:buttons dims)
                :let [[bx by bw bh] rect]]
          (is (>= label-x bx) label)
          (is (<= (+ label-x (measure label label-size)) (+ bx bw)) label)
          (is (>= label-y by) label)
          (is (<= (+ label-y label-size) (+ by bh)) label))))))

(deftest the-hud-needs-no-format
  (is (= "fps 60 | compute 3.2 ms | draw 4.5 ms" (h/hud-line 60.0 3.2 4.5)))
  (is (= "fps 0 | compute 0.0 ms | draw 0.0 ms" (h/hud-line 0.0 0.0 0.0)))
  (is (= "fps 58 | compute 12.0 ms | draw 0.1 ms" (h/hud-line 58.4 11.96 0.06)))
  (is (= "windings 14 | detail 260" (h/status-line start))))

(deftest first-frame-draws
  (testing "the state after init alone has everything a draw reads"
    (doseq [screen screens
            :let [metrics {:screen screen}
                  s (first ((:init (h/scene)) {:metrics metrics}))
                  g (h/geometry metrics)
                  [fx fy fw fh] (:field g)
                  bufs (h/make-buffers)
                  p (h/params s g)]]
      (testing (str screen)
        (is (<= 110.0 (:zoom s) 520.0))
        (is (<= 60 (:nu s) h/max-nu))
        (is (<= 3 (:twists s) 24))
        (is (every? number? (map s [:rot-x :rot-y :vel-x :vel-y :clock])))
        (is (= 4 (count (:buttons g))))
        (testing "compute! fills the buffers the draw reads"
          (h/compute! bufs p)
          (let [n (* (:nu s) h/nv)
                xs (take n (vec (:sx bufs)))
                ys (take n (vec (:sy bufs)))]
            (is (pos? (count xs)))
            (is (every? #(<= fx % (+ fx fw)) xs))
            (is (every? #(<= fy % (+ fy fh)) ys))
            (is (= (range (:nu s)) (sort (take (:nu s) (vec (:order bufs))))))
            (is (some #(not= % (first xs)) xs) "the points are spread, not stacked")))
        (testing "about half of the quads face the camera"
          (let [sx (:sx bufs)
                sy (:sy bufs)
                nu (:nu s)
                facing (count
                        (for [i (range nu)
                              j (range h/nv)
                              :let [a (+ (* i h/nv) j)
                                    b (+ (* i h/nv) (mod (inc j) h/nv))
                                    c (+ (* (mod (inc i) nu) h/nv) (mod (inc j) h/nv))]
                              :when (pos? (- (* (- (aget sx b) (aget sx a)) (- (aget sy c) (aget sy a)))
                                             (* (- (aget sy b) (aget sy a)) (- (aget sx c) (aget sx a)))))]
                          a))
                total (* nu h/nv)]
            (is (< (* 0.25 total) facing (* 0.75 total)))))))))
