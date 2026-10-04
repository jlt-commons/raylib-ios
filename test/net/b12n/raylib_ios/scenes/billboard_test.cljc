(ns net.b12n.raylib-ios.scenes.billboard-test
  (:require [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.billboard :as sc]
            [net.b12n.raylib-ios.soft3d :as s3]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))

(def metrics {:screen [1206 2334]})

(defn- tick [state dt]
  (first ((:update (sc/scene)) state {:metrics metrics
                                      :pointer {:phase :idle}
                                      :delta-seconds dt})))

(defn- frames
  "The state after `n` updates of `dt` seconds each."
  [n dt]
  (nth (iterate #(tick % dt) (first ((:init (sc/scene)) {:metrics metrics}))) n))

(defn- tris [dl] (filterv (fn [it] (= :tri (nth it 0))) dl))
(defn- lines [dl] (filterv (fn [it] (= :line (nth it 0))) dl))

;; --- the original's maths, billboard_rendering.clj 61-88, copied as an oracle

(defn- vsub [[ax ay az] [bx by bz]] [(- ax bx) (- ay by) (- az bz)])
(defn- vadd [[ax ay az] [bx by bz]] [(+ ax bx) (+ ay by) (+ az bz)])
(defn- vscale [[x y z] s] [(* x s) (* y s) (* z s)])
(defn- vcross [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by)) (- (* az bx) (* ax bz)) (- (* ax by) (* ay bx))])
(defn- vdot [[ax ay az] [bx by bz]] (+ (* ax bx) (* ay by) (* az bz)))
(defn- vnorm [v] (let [l (Math/sqrt (vdot v v))] (if (zero? l) v (vscale v (/ 1.0 l)))))
(defn- vrotate-axis [v k deg]
  (let [rad (Math/toRadians deg)
        c (Math/cos rad) s (Math/sin rad)
        kdotv (vdot k v)]
    (vadd (vadd (vscale v c) (vscale (vcross k v) s))
          (vscale k (* kdotv (- 1.0 c))))))

(defn- original-corners
  "The four corners the original's `draw-billboard` builds: tl tr br bl."
  [center cam-pos cam-target size spin-deg]
  (let [forward (vnorm (vsub cam-target cam-pos))
        right0 (vnorm (vcross [0.0 1.0 0.0] forward))
        up0 (vcross forward right0)
        [right up] (if (zero? spin-deg)
                     [right0 up0]
                     [(vrotate-axis right0 forward spin-deg) (vrotate-axis up0 forward spin-deg)])
        h (/ size 2.0)]
    [(vadd center (vadd (vscale right (- h)) (vscale up h)))
     (vadd center (vadd (vscale right h) (vscale up h)))
     (vadd center (vadd (vscale right h) (vscale up (- h))))
     (vadd center (vadd (vscale right (- h)) (vscale up (- h))))]))

(defn- same-corners?
  "Whether two lists of four points are the same set, to 1e-9."
  [a b]
  (and (= 4 (count a) (count b))
       (every? (fn [p] (some (fn [q] (every? true? (map near? p q))) b)) a)
       (every? (fn [q] (some (fn [p] (every? true? (map near? p q))) a)) b)))

(deftest the-camera-orbits-and-the-spin-turns-as-the-original
  (testing "the camera starts at angle 0.8 and goes round 0.5 radians a second; the spin is
            0.4 degrees a frame, whatever the frame time (the original's `spin` and `angle`)"
    (let [s0 (frames 0 0.1)
          s10 (frames 10 0.1)]
      (is (near? 0.8 (:angle s0)))
      (is (near? 0.0 (:spin s0)))
      (is (near? 1.3 (:angle s10)))
      (is (near? 4.0 (:spin s10)))
      (is (near? 4.0 (:spin (frames 10 1.0))) "the spin ignores the frame time")
      (is (near? (+ 0.8 (* 0.5 2.0)) (:angle (frames 1 2.0))))
      (is (near? 0.8 (:angle (frames 5 0.0))) "a frame that took no time does not move the camera")))
  (testing "the camera sits at (7.07 cos a, 4, 7.07 sin a) and looks at (0, 2, 0)"
    (doseq [n [0 7 40]
            :let [s (frames n (/ 1.0 60.0))
                  a (:angle s)
                  dims (sc/dimensions metrics measure)
                  cam (sc/camera s dims)]]
      (is (every? true? (map near? [(* 7.07 (Math/cos a)) 4.0 (* 7.07 (Math/sin a))] (:position cam))))
      (is (= [0.0 2.0 0.0] (:target cam)))
      (is (= [0.0 1.0 0.0] (:up cam))))))

(deftest the-billboards-are-the-originals
  (testing "two billboards of side 2, at (0, 2, 0) and (1, 2, 1)"
    (is (= [0.0 2.0 0.0] sc/static-pos))
    (is (= [1.0 2.0 1.0] sc/spin-pos))
    (is (= 2.0 sc/billboard-size)))
  (testing "the corners are the original's, for the static one and the spinning one, all round the orbit"
    (doseq [n (range 0 400 37)
            :let [s (frames n 0.05)
                  dims (sc/dimensions metrics measure)
                  cam (sc/camera s dims)
                  {:keys [position target]} cam]]
      (is (same-corners? (original-corners sc/static-pos position target 2.0 0.0)
                         (s3/billboard-corners cam sc/static-pos 2.0 {}))
          (str "static, frame " n))
      (is (same-corners? (original-corners sc/spin-pos position target 2.0 (:spin s))
                         (s3/billboard-corners cam sc/spin-pos 2.0 {:rotation (sc/rotation s)}))
          (str "spinning, frame " n " at " (:spin s) " degrees"))))
  (testing "the original rotates about the forward vector, which points away from the camera, so
            the port's rotation, about the quad's normal toward it, is the other sign"
    (is (near? -30.0 (sc/rotation {:spin 30.0})))))

(deftest the-ring-is-the-originals-atlas-redrawn
  (let [c 32.0
        atlas-class (fn [x y]
                      (let [r (Math/sqrt (+ (Math/pow (- x c) 2) (Math/pow (- y c) 2)))]
                        (cond (< r (* c 0.35)) :yellow (< r (* c 0.7)) :red :else :blue)))
        by-colour {[253 249 0 255] :yellow
                   [230 41 55 255] :red
                   [0 121 241 255] :blue}
        parts (sc/ring-parts)]
    (testing "three squares, blue then red then yellow, in the original's three colours"
      (is (= [:blue :red :yellow] (mapv (comp by-colour first) parts))))
    (testing "painted in that order, each is as wide as the original's ring of that colour"
      (let [[[_ blue] [_ red] [_ yellow]] parts]
        (is (= [0.0 0.0 1.0 1.0] blue))
        (is (every? true? (map near? [0.15 0.15 0.85 0.85] red)) "r < 0.7 * 32 = 22.4 pixels: 0.35 of 64 each side of the middle")
        (is (every? true? (map near? [0.325 0.325 0.675 0.675] yellow)) "r < 0.35 * 32 = 11.2 pixels")))
    (testing "every pixel of the original's red and yellow lies inside its square"
      (let [inside? (fn [[fx0 fy0 fx1 fy1] x y] (and (<= fx0 (/ x 64.0) fx1) (<= fy0 (/ y 64.0) fy1)))
            part-of (fn [k] (second (first (filter (fn [[col _]] (= k (by-colour col))) parts))))]
        (doseq [x (range 64) y (range 64)
                :let [k (atlas-class x y)]]
          (when (#{:yellow} k) (is (inside? (part-of :yellow) x y) (str x "," y)))
          (when (#{:yellow :red} k) (is (inside? (part-of :red) x y) (str x "," y))))))))

(defn- axis-order
  "Independent of the scene: the billboards are planes perpendicular to the view
  axis `target - eye`, so the one with the greater depth along it is farther and
  goes first."
  [eye target]
  (let [f (vsub target eye)
        depth (fn [c] (vdot (vsub c eye) f))]
    (if (> (depth sc/static-pos) (depth sc/spin-pos)) [:static :spin] [:spin :static])))

(defn- euclid-order [eye]
  (let [d (fn [p] (vdot (vsub eye p) (vsub eye p)))]
    (if (> (d sc/static-pos) (d sc/spin-pos)) [:static :spin] [:spin :static])))

(defn- painted
  "The list `scene-list` should give when the billboards go in `order`."
  [s dims order]
  (let [cam (sc/camera s dims)
        vp (s3/view-proj cam (:viewport dims))
        ring (fn [dl pos opts]
               (reduce (fn [dl [colour part]] (s3/billboard dl vp pos 2.0 colour (assoc opts :part part)))
                       dl (sc/ring-parts)))
        at {:static [sc/static-pos {}]
            :spin [sc/spin-pos {:rotation (sc/rotation s)}]}]
    (reduce (fn [dl k] (ring dl (first (at k)) (second (at k))))
            (s3/grid [] vp 10 1.0)
            order)))

(deftest paint-by-depth-along-the-view-axis
  (let [dims (sc/dimensions metrics measure)
        at-angle (fn [deg] (assoc (frames 0 0.0) :angle (Math/toRadians deg)))]
    (testing "the planes are perpendicular to the view axis, so depth along it decides, farther first"
      (doseq [deg (range 0 360 0.1)
              :let [s (at-angle deg)
                    cam (sc/camera s dims)]]
        (is (= (axis-order (:position cam) (:target cam))
               (sc/paint-order (:position cam) (:target cam)))
            (str deg))))
    (testing "scene-list paints in that order"
      (doseq [deg [0 60 129.3 130.0 200 320.7 333]
              :let [s (at-angle deg)
                    cam (sc/camera s dims)]]
        (is (= (painted s dims (axis-order (:position cam) (:target cam)))
               (sc/scene-list s dims))
            (str deg))))
    (testing "the centre-distance order the original uses disagrees inside a window of each lap
              (0 < cos a + sin a < 1/7.07, about 5.7 degrees), so there the two rules differ"
      (let [s (at-angle 129.3)
            cam (sc/camera s dims)]
        (is (not= (euclid-order (:position cam))
                  (axis-order (:position cam) (:target cam))))
        (is (= (axis-order (:position cam) (:target cam))
               (sc/paint-order (:position cam) (:target cam))))))))

(deftest first-frame-draws
  (doseq [screen screens
          :let [m {:screen screen}
                s (first ((:init (sc/scene)) {:metrics m}))
                dims (sc/dimensions m measure)
                [vx vy vw vh] (:viewport dims)
                dl (sc/scene-list s dims)
                ts (tris dl)]]
    (testing (str screen)
      (is (= 12 (count ts)) "two billboards of three squares, two triangles each")
      (is (= 22 (count (lines dl))) "the grid of 10: 11 lines each way")
      (is (= (tris dl) (vec (drop 22 dl))) "the grid first, then the figures, unsorted")
      (is (= [[0 121 241 255] [0 121 241 255] [230 41 55 255] [230 41 55 255] [253 249 0 255] [253 249 0 255]]
             (mapv #(subvec % 7 11) (subvec ts 0 6)))
          "blue, red, yellow, two triangles each")
      (is (= (mapv #(subvec % 7 11) (subvec ts 0 6)) (mapv #(subvec % 7 11) (subvec ts 6 12))))
      (is (every? (fn [[_ & more]]
                    (every? (fn [[x y]] (and (<= vx x (+ vx vw)) (<= vy y (+ vy vh))))
                            (partition 2 (take 6 more))))
                  ts)
          "every corner is inside the field")
      (is (every? (fn [n]
                    (let [dl (sc/scene-list (frames n 0.05) dims)]
                      (and (= 12 (count (tris dl)))
                           (every? (fn [[_ & more]]
                                     (every? (fn [[x y]] (and (<= vx x (+ vx vw)) (<= vy y (+ vy vh))))
                                             (partition 2 (take 6 more))))
                                   (tris dl)))))
                  (range 0 700 29))
          "both stay in view, whole, through an orbit"))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [_ fy _ fh] (:viewport dims)]]
    (testing (str screen)
      (is (= 1 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) fy) "the caption sits above the field"))
      (is (>= fy (+ back-y back-h)) "the field is below Back")
      (is (near? h (+ fy fh)) "and runs to the bottom"))))
