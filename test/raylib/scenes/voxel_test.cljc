(ns raylib.scenes.voxel-test
  (:require [clojure.set :as set]
            [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.voxel :as sc]
            [raylib.soft3d :as s3]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (sc/geometry m))
(def start (first ((:init (sc/scene)) {:metrics m})))
(def slop (gesture/slop m))

;; Phone geometry: Back ends at 120, pad 18, text 36, so the field starts at
;; 192 and is 2142 tall. A touch above 192 + 1428 = 1620 looks, from there down
;; it walks. The mode button sits in the row under Back, on the right.
(def look-pt [600.0 600.0])
(def look-pt2 [300.0 700.0])
(def stick-pt [600.0 2000.0])
(def button-pt [900.0 160.0])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))
(defn- vnear? [a b] (and (= (count a) (count b)) (every? true? (map near? a b))))
(defn- at-px [[x y] dx dy] [(+ x dx) (+ y dy)])

(defn- step
  ([state phase points] (step state phase points nil))
  ([state phase points ids]
   (sc/advance state (cond-> {:metrics m
                              :delta-seconds (/ 1.0 60.0)
                              :pointer {:phase phase
                                        :position (first points)}
                              :touch-points (vec points)}
                       ids (assoc :touches {:ids (vec ids)})))))

(defn- tap [state p] (-> state (step :press [p]) (step :release [p])))
(defn- eye [state] [(:px state) (:py state) (:pz state)])
(def steered (assoc start :steered? true))

;; --- the original (basic_voxel.clj), re-typed here as the oracle ------------------

(def half 0.5)

(defn- slab [o dd lo hi t0 t1]
  (if (< (Math/abs (double dd)) 1e-9)
    (when (<= lo o hi) [t0 t1])
    (let [a (/ (- lo o) dd)
          b (/ (- hi o) dd)
          t0 (max t0 (min a b))
          t1 (min t1 (max a b))]
      (when (<= t0 t1) [t0 t1]))))

(defn- hit-distance
  "The original's lines 59-65."
  [[ox oy oz] [dx dy dz] [cx cy cz]]
  (when-let [[t0 t1] (slab ox dx (- cx half) (+ cx half) 0.0 1.0e300)]
    (when-let [[t0 t1] (slab oy dy (- cy half) (+ cy half) t0 t1)]
      (when-let [[t0 _] (slab oz dz (- cz half) (+ cz half) t0 t1)]
        (when-not (neg? t0) t0)))))

(defn- pick
  "The original's lines 67-77: the nearest voxel the ray enters, or nil."
  [voxels origin dir]
  (first
   (reduce (fn [[_ best-t :as acc] v]
             (let [t (hit-distance origin dir (mapv double v))]
               (if (and t (or (nil? best-t) (< t best-t)))
                 [v t]
                 acc)))
           [nil nil]
           voxels)))

(defn- entry-face
  "The unit step `[dx dy dz]` out of voxel `v` through the face the ray enters
  by: the axis whose slab starts latest, facing back against the ray."
  [[ox oy oz] [dx dy dz] [cx cy cz]]
  (let [ts (for [[o dd c] [[ox dx cx] [oy dy cy] [oz dz cz]]
                 :let [dd (if (zero? dd) 1.0e-12 dd)]]
             (min (/ (- (- c half) o) dd) (/ (- (+ c half) o) dd)))
        axis (first (apply max-key second (map-indexed vector ts)))
        dirs [dx dy dz]]
    (assoc [0 0 0] axis (if (pos? (nth dirs axis)) -1 1))))

(defn- look-dir
  "The original's line 124: the look direction, which is the ray under the crosshair."
  [{:keys [yaw pitch]}]
  (let [cp (Math/cos pitch)]
    [(* cp (Math/cos yaw)) (Math/sin pitch) (* cp (Math/sin yaw))]))

(defn- exposed-count
  "Faces with an empty neighbour, counted voxel by voxel, direction by direction."
  [world]
  (count (for [[x y z] world
               [a b c] [[1 0 0] [-1 0 0] [0 1 0] [0 -1 0] [0 0 1] [0 0 -1]]
               :when (not (contains? world [(+ x a) (+ y b) (+ z c)]))]
           1)))

(defn- tris [dl] (filterv #(= :tri (nth % 0)) dl))
(defn- lines [dl] (filterv #(= :line (nth % 0)) dl))
(defn- cross-y-down [[_ x1 y1 x2 y2 x3 y3]]
  (- (* (- x2 x1) (- y3 y1)) (* (- y2 y1) (- x3 x1))))

(def beige [211 176 131 255])
(def black [0 0 0 255])

;; --- the world ---------------------------------------------------------------------

(deftest the-world-is-the-originals-block
  (testing "8 x 8 x 8 voxels at integer positions 0..7, 512 of them"
    (is (= 512 (count (:world start))))
    (is (= (set (for [x (range 8) y (range 8) z (range 8)] [x y z])) (set (:world start))))
    (is (= (:world start) (sc/full-block))))
  (testing "the camera starts on the original's idle orbit: radius 22 round (3.5, 3.5), height 14, looking back at the centre"
    (is (vnear? [25.5 14.0 3.5] (eye start)))
    (is (near? Math/PI (:yaw start)))
    (is (near? (Math/atan2 (- 3.5 14.0) 22.0) (:pitch start)))
    (is (= :remove (:mode start)))
    (is (not (:steered? start)))))

;; --- the picture ------------------------------------------------------------------

(deftest first-frame-draws
  (let [dims (sc/dimensions m measure)
        dl (sc/scene-list start dims)
        ts (tris dl)
        ls (lines dl)
        [vx vy vw vh] (:viewport dims)
        grid (filterv #(#{[127 127 127 255] [191 191 191 255]} (subvec % 5 9)) ls)
        wires (filterv #(= black (subvec % 5 9)) ls)
        vp (s3/view-proj (s3/fit-camera (:camera start) (/ 800.0 450.0) (:aspect dims)) (:viewport dims))
        centre (s3/world->screen vp [3.5 3.5 3.5])]
    (testing "the block's two near sides are on the glass in the original's BEIGE"
      (is (= 256 (count ts)))
      (is (every? #(= beige (subvec % 7 11)) ts)))
    (testing "the grid of 10 is the original's, 22 lines"
      (is (= 22 (count grid))))
    (testing "the wires are BLACK"
      (is (pos? (count wires))))
    (testing "every triangle has the front winding"
      (is (every? #(neg? (cross-y-down %)) ts)))
    (testing "the idle orbit looks at the block's centre: it projects to the middle of the field"
      (is (< (abs (- (first centre) (+ vx (* 0.5 vw)))) 1e-6))
      (is (< (abs (- (second centre) (+ vy (* 0.5 vh)))) 1e-6)))
    (testing "the crosshair is that same middle"
      (is (= [(+ vx (* 0.5 vw)) (+ vy (* 0.5 vh))] (:crosshair dims))))
    (testing "every coordinate is finite and some are on the glass"
      (is (every? (fn [[_ & more]] (every? #(and (number? %) (< (abs (double %)) 1e5)) (take 6 more))) ts))
      (is (some (fn [t] (and (<= vx (nth t 1) (+ vx vw)) (<= vy (nth t 2) (+ vy vh)))) ts)))))

(deftest only-exposed-faces-draw
  (let [full (:world start)
        mesh (sc/mesh full)]
    (testing "a full block has 6 x 64 exposed faces and none between two voxels"
      (is (= 384 (count (:faces mesh))))
      (is (= (exposed-count full) (count (:faces mesh)))))
    (testing "one voxel alone has its six faces"
      (is (= 6 (count (:faces (sc/mesh #{[0 0 0]}))))))
    (testing "two touching voxels have ten, the shared pair is not there"
      (is (= 10 (count (:faces (sc/mesh #{[0 0 0] [1 0 0]}))))))
    (testing "a hollowed block counts as brute force counts it"
      (doseq [gone [#{[3 3 3]} #{[7 7 7]} #{[0 0 0] [1 0 0] [2 0 0]} #{[3 3 3] [3 3 4] [3 4 3]}]
              :let [w (apply disj full gone)]]
        (is (= (exposed-count w) (count (:faces (sc/mesh w)))) (str gone))))
    (testing "the first frame draws two triangles for each exposed face that faces the eye: two sides of 64 faces"
      (let [dims (sc/dimensions m measure)]
        (is (= (* 2 2 64) (count (tris (sc/scene-list start dims)))))))
    (testing "a voxel taken from the middle adds the faces that look into the hole and face the eye"
      (let [dims (sc/dimensions m measure)
            w (disj full [3 3 3])
            st (assoc start :world w :mesh (sc/mesh w))
            [ex ey ez] (eye st)
            ;; brute force: each exposed face whose plane the eye is outside of
            facing (count (for [[x y z] w
                                [axis sgn] [[0 1] [0 -1] [1 1] [1 -1] [2 1] [2 -1]]
                                :let [v [x y z]]
                                :when (not (contains? w (update v axis + sgn)))
                                :let [plane (+ (nth v axis) (* 0.5 sgn))
                                      e (nth [ex ey ez] axis)]
                                :when (pos? (* sgn (- e plane)))]
                            1))]
        (is (= (* 2 facing) (count (tris (sc/scene-list st dims)))))
        (is (= (+ 256 (* 2 3)) (count (tris (sc/scene-list st dims)))) "the hole's +x, +y and +z faces: the eye's z of 3.5 is past the 2.5 plane of the voxel below")))
    (testing "no triangle is drawn for a face between two voxels: the same count as the exposed faces facing the eye, on a screen rotated"
      (let [dims (sc/dimensions {:screen [2334 1206]} measure)]
        (is (= 256 (count (tris (sc/scene-list start dims)))))))))

;; The unit edges of the faces that face the eye, found by brute force: the
;; segments the wires must cover, whatever lines they are merged into.
(defn- facing-unit-edges [world [ex ey ez]]
  (set (for [v world
             [axis sgn] [[0 1] [0 -1] [1 1] [1 -1] [2 1] [2 -1]]
             :when (not (contains? world (update v axis + sgn)))
             :let [plane (+ (nth v axis) (* 0.5 sgn))]
             :when (pos? (* sgn (- (nth [ex ey ez] axis) plane)))
             :let [[u w] (remove #{axis} [0 1 2])
                   corner (fn [du dw] (-> (mapv #(- % 0.5) v)
                                          (assoc axis plane)
                                          (update u + du)
                                          (update w + dw)))
                   cs [(corner 0 0) (corner 1 0) (corner 1 1) (corner 0 1)]]
             k (range 4)]
         (set [(nth cs k) (nth cs (mod (inc k) 4))]))))

(defn- on-segment? [[ax ay bx by] [px py]]
  (let [cross (- (* (- bx ax) (- py ay)) (* (- by ay) (- px ax)))
        len (Math/sqrt (+ (* (- bx ax) (- bx ax)) (* (- by ay) (- by ay))))
        dot (+ (* (- px ax) (- bx ax)) (* (- py ay) (- by ay)))]
    (and (< (abs (/ cross (max len 1e-9))) 1e-6)
         (<= -1e-6 dot (+ (* len len) 1e-6)))))

(deftest wires-cover-every-edge-of-a-drawn-face-and-nothing-else
  (let [dims (sc/dimensions m measure)
        wires-of (fn [st] (filterv #(= black (subvec % 5 9)) (lines (sc/scene-list st dims))))
        check (fn [st]
                (let [vp (s3/view-proj (s3/fit-camera (:camera st) (/ 800.0 450.0) (:aspect dims)) (:viewport dims))
                      wires (wires-of st)
                      unit (facing-unit-edges (:world st) (eye st))
                      scr (fn [p] (s3/world->screen vp p))]
                  {:wires (count wires)
                   :uncovered (count (remove (fn [e] (let [[p q] (vec e)
                                                           [px py] (scr p)
                                                           [qx qy] (scr q)]
                                                       (some (fn [w] (and (on-segment? (subvec w 1 5) [px py])
                                                                          (on-segment? (subvec w 1 5) [qx qy])))
                                                             wires)))
                                             unit))
                   ;; a wire is whole when the unit edges on it, laid end to end along
                   ;; it (two at different depths may overlap on the glass), leave no gap
                   :stray (count (remove (fn [[_ x1 y1 x2 y2]]
                                           (let [seg [x1 y1 x2 y2]
                                                 len (Math/hypot (- x2 x1) (- y2 y1))
                                                 along (fn [[px py]] (/ (+ (* (- px x1) (- x2 x1)) (* (- py y1) (- y2 y1))) len))
                                                 spans (sort-by first (for [e unit
                                                                            :let [[p q] (vec e)
                                                                                  a (scr p)
                                                                                  b (scr q)]
                                                                            :when (and (on-segment? seg a) (on-segment? seg b))]
                                                                        (vec (sort [(along a) (along b)]))))
                                                 reach (reduce (fn [reach [lo hi]] (if (<= lo (+ reach 1e-3)) (max reach hi) reach)) 0.0 spans)]
                                             (>= reach (- len 1e-3))))
                                         wires))}))
        corner (merge start (#'sc/orbit-pose (/ Math/PI 4.0)))
        corner (assoc corner :camera (sc/camera-of corner))
        ;; a block eaten by a fixed pattern, about a third of it gone
        eaten (let [w (set (remove (fn [[x y z]] (zero? (mod (+ (* 3 x x) (* 5 y) (* 7 z z) (* x z) (* y z)) 3))) (:world start)))]
                (assoc corner :world w :mesh (sc/mesh w)))
        at-angle (fn [st angle]
                   (let [st (merge st (#'sc/orbit-pose angle))]
                     (assoc st :camera (sc/camera-of st))))]
    (testing "the first frame: two sides, 17 + 9 + 9 straight lines, none stray"
      (is (= {:wires 35
              :uncovered 0
              :stray 0} (check start))))
    (testing "three sides at the orbit's corner: 17 lines along each axis"
      (is (= {:wires 51
              :uncovered 0
              :stray 0} (check corner))))
    (testing "a block eaten full of holes still covers every edge of every face it draws, and joins no gap"
      (is (< 250 (count (:world eaten)) 450))
      (doseq [angle [0.0 0.5 (/ Math/PI 4.0) 1.4 2.3 3.5 5.0]
              :let [r (check (at-angle eaten angle))]]
        (is (= 0 (:uncovered r)) (str "angle " angle))
        (is (= 0 (:stray r)) (str "angle " angle))
        (is (> (:wires r) 51) "the holes break the straight runs")))))

(deftest paint-order
  (doseq [[label st] [["the first frame" start]
                      ["a hollowed block" (let [w (apply disj (:world start) [[3 7 3] [4 7 3] [7 3 3]])]
                                            (assoc start :world w :mesh (sc/mesh w)))]]
          :let [dims (sc/dimensions m measure)
                dl (sc/scene-list st dims)
                kinds (map #(if (= :tri (nth % 0)) :tri (nth % 9)) dl)
                depths (map #(nth % 11) (tris dl))]]
    (testing label
      (testing "the grid first, then every face far to near, then the wires"
        (is (= [:under :tri :over] (map first (partition-by identity kinds))))
        (is (= 22 (count (take-while #{:under} kinds)))))
      (testing "far to near by mean depth, the order raylib.soft3d/finish gives"
        (is (apply >= depths))
        (is (= (tris (s3/finish dl)) (tris dl))
            "finish on the finished list leaves the triangles where they are")))))

(deftest a-ray-along-an-axis-still-picks
  ;; A direction with exact zeros divides by zero in the slab test; ray-box
  ;; reads that as an infinity on both runtimes (probed on the JVM and jolt).
  (let [hit (sc/pick (:world start) {:position [-5.0 3.0 3.0]
                                     :direction [1.0 0.0 0.0]})]
    (is (= [0 3 3] (:voxel hit)))
    (is (= [-1 0 0] (:step hit)))))

;; --- the pick ---------------------------------------------------------------------

(deftest a-tap-removes-as-the-original
  (testing "the voxel the crosshair ray enters first goes, from the idle orbit"
    (let [s (tap start stick-pt)
          expected (pick (:world start) (eye s) (look-dir s))]
      (is (some? expected))
      (is (= (disj (:world start) expected) (:world s)))
      (is (= 511 (count (:world s))))
      (is (= 511 (count (:world (sc/advance s {:metrics m
                                               :pointer {:phase :idle}
                                               :touch-points []})))))
      (is (= (exposed-count (:world s)) (count (:faces (:mesh s)))) "the mesh is rebuilt")))
  (testing "the tap's own position does not matter, the crosshair does"
    (let [a (tap start look-pt)
          b (tap start stick-pt)]
      (is (= (:world a) (:world b)))))
  (testing "from a walked and looked pose, still the original's nearest hit"
    (doseq [[px pz yaw pitch] [[3.3 -8.0 (/ Math/PI 2.0) -0.5]
                               [-6.3 -5.3 (/ Math/PI 4.0) -0.45]
                               [20.0 3.0 Math/PI -0.45]
                               [3.5 3.3 0.0 -1.4]]
            :let [st (assoc steered :px px :py 14.0 :pz pz :yaw yaw :pitch pitch)
                  s (tap st look-pt)
                  expected (pick (:world st) (eye s) (look-dir s))]]
      (testing (str [px pz yaw pitch])
        (is (some? expected))
        (is (= (disj (:world st) expected) (:world s))))))
  (testing "a second tap takes the next voxel along the same ray, behind the first"
    (let [st (assoc steered :px 3.5 :py 14.0 :pz 3.3 :yaw 0.0 :pitch -1.4)
          one (tap st look-pt)
          two (tap one look-pt)
          first-gone (first (set/difference (:world st) (:world one)))
          second-gone (first (set/difference (:world one) (:world two)))]
      (is (= 2 (- 512 (count (:world two)))))
      (is (some? first-gone))
      (is (= (pick (:world one) (eye one) (look-dir one)) second-gone))
      (is (< (nth first-gone 1) 8))
      (is (not= first-gone second-gone))))
  (testing "a tap that hits nothing changes nothing"
    (let [st (assoc steered :px 3.5 :py 14.0 :pz 3.3 :yaw 0.0 :pitch 1.0)
          s (tap st look-pt)]
      (is (nil? (pick (:world st) (eye s) (look-dir s))))
      (is (= (:world st) (:world s)))))
  (testing "a tap hands the camera over, as a click does in the original"
    (is (:steered? (tap start look-pt)))))

(deftest a-tap-places-as-the-original
  ;; The original has only the removing click (its mouse-pressed handler, lines
  ;; 137-141, and the C's MOUSE_LEFT_BUTTON branch), so placing is the one
  ;; addition: the button flips the tap to place, on the face the ray entered.
  (let [st (assoc steered :px 3.5 :py 14.0 :pz 3.3 :yaw 0.0 :pitch -1.4)
        placing (tap st button-pt)]
    (testing "the button flips the mode and touches nothing else"
      (is (= :place (:mode placing)))
      (is (= (:world st) (:world placing)))
      (is (= :remove (:mode (tap placing button-pt)))))
    (testing "a tap then adds a voxel on the face the ray entered, outside the nearest voxel"
      (let [s (tap placing look-pt)
            hit (pick (:world st) (eye s) (look-dir s))
            step* (entry-face (eye s) (look-dir s) (mapv double hit))
            added (mapv + hit step*)]
        (is (some? hit))
        (is (= (conj (:world st) added) (:world s)))
        (is (= 513 (count (:world s))))
        (is (= [0 1 0] step*) "from straight above it is the top face")
        (is (= (exposed-count (:world s)) (count (:faces (:mesh s)))))))
    (testing "from the side it is the side face, from the idle orbit's +x side"
      (let [side (assoc placing :px 25.5 :py 5.0 :pz 3.2 :yaw Math/PI :pitch 0.0)
            s (tap side look-pt)
            hit (pick (:world side) (eye s) (look-dir s))
            added (mapv + hit (entry-face (eye s) (look-dir s) (mapv double hit)))]
        (is (= [8 5 3] added))
        (is (= (conj (:world side) added) (:world s)))))
    (testing "the mode stays until the button is tapped again, and remove takes it back out"
      (let [s (-> placing (tap look-pt) (tap button-pt) (tap look-pt))]
        (is (= :remove (:mode s)))
        (is (= 512 (count (:world s))) "the placed voxel is the nearest one, so it is the one removed")))
    (testing "nothing is placed round a miss"
      (let [sky (assoc placing :pitch 1.0)]
        (is (= (:world sky) (:world (tap sky look-pt))))))
    (testing "nothing is placed on the eye"
      (let [near (assoc placing :px 3.2 :py 8.0 :pz 3.3 :yaw 0.0 :pitch -1.4)
            s (tap near look-pt)]
        (is (not (contains? (:world s) [3 8 3])) "the eye at height 8 is inside that cell")
        (is (= (:world near) (:world s)))))))

;; --- fingers ----------------------------------------------------------------------

(deftest the-stick-walks-at-the-originals-speed
  (testing "up the glass is W: 0.15 along the yaw a frame, nothing in y, the original's SPEED"
    (let [st (assoc steered :yaw 0.0)
          pressed (step st :press [stick-pt])
          one (step pressed :down [(at-px stick-pt 0.0 -200.0)])
          two (step one :down [(at-px stick-pt 0.0 -200.0)])]
      (is (= (eye st) (eye pressed)) "the press walks nowhere")
      (is (vnear? (mapv + (eye st) [0.15 0.0 0.0]) (eye one)))
      (is (vnear? (mapv + (eye st) [0.30 0.0 0.0]) (eye two)))))
  (testing "right is D: (-sin yaw, cos yaw), and a diagonal is one speed"
    (doseq [yaw [0.0 0.6 -1.1]
            :let [st (assoc steered :yaw yaw)
                  moved (fn [dx dy] (let [s (-> st (step :press [stick-pt]) (step :down [(at-px stick-pt dx dy)]))]
                                      (mapv - (eye s) (eye st))))]]
      (is (vnear? [(* 0.15 (- (Math/sin yaw))) 0.0 (* 0.15 (Math/cos yaw))] (moved 200.0 0.0)) (str "D at " yaw))
      (is (vnear? [(* 0.15 (Math/cos yaw)) 0.0 (* 0.15 (Math/sin yaw))] (moved 0.0 -200.0)) (str "W at " yaw))
      (let [[x _ z] (moved 150.0 -150.0)]
        (is (near? 0.15 (Math/sqrt (+ (* x x) (* z z)))) (str "diagonal at " yaw)))))
  (testing "inside the dead zone nothing moves, and the height never changes"
    (is (= (eye steered) (eye (-> steered (step :press [stick-pt]) (step :down [(at-px stick-pt (* 0.5 slop) 0.0)])))))
    (is (= 14.0 (:py (-> steered (step :press [stick-pt]) (step :down [(at-px stick-pt 0.0 -300.0)])))))))

(deftest a-drag-looks
  (let [scale (/ 800.0 1206.0)
        [x0 y0] look-pt
        pressed (step steered :press [look-pt])
        dragged (step pressed :down [[(+ x0 100.0) (+ y0 40.0)]])]
    (testing "the press looks nowhere"
      (is (= (:yaw steered) (:yaw pressed)))
      (is (= (:pitch steered) (:pitch pressed))))
    (testing "yaw + SENS * dx and pitch - SENS * dy, the original's lines 116-121, in an 800 pixel window's worth"
      (is (near? (+ (:yaw steered) (* 0.004 100.0 scale)) (:yaw dragged)))
      (is (near? (- (:pitch steered) (* 0.004 40.0 scale)) (:pitch dragged)))
      (is (vnear? (eye steered) (eye dragged)) "looking does not walk"))
    (testing "the pitch is held to +-1.4"
      (let [drag (fn [s k] (step s :down [(at-px look-pt 0.0 k)]))
            up (reduce drag (step steered :press [look-pt]) (map #(* -300.0 (inc %)) (range 6)))
            down (reduce drag (step steered :press [look-pt]) (map #(* 300.0 (inc %)) (range 6)))]
        (is (near? 1.4 (:pitch up)))
        (is (near? -1.4 (:pitch down)))))))

(deftest the-camera-orbits-until-touched
  (let [one (step start :idle [])
        two (step one :idle [])]
    (testing "0.006 radians an update on the original's circle, facing back at the centre"
      (is (near? 0.006 (:angle one)))
      (is (vnear? [(+ 3.5 (* 22.0 (Math/cos 0.006))) 14.0 (+ 3.5 (* 22.0 (Math/sin 0.006)))] (eye one)))
      (is (near? (+ 0.012 Math/PI) (:yaw two)))
      (is (near? (Math/atan2 -10.5 22.0) (:pitch two))))
    (testing "the first walk hands it over for good"
      (let [walking (-> two (step :press [stick-pt]) (step :down [(at-px stick-pt 0.0 -200.0)]) (step :down [(at-px stick-pt 0.0 -200.0)]))
            later (nth (iterate #(step % :idle []) walking) 5)]
        (is (:steered? walking))
        (is (= (eye walking) (eye later)))
        (is (= (:yaw walking) (:yaw later)))))
    (testing "a drag hands it over too"
      (let [s (-> two (step :press [look-pt]) (step :down [(at-px look-pt 80.0 0.0)]) (step :down [(at-px look-pt 160.0 0.0)]))]
        (is (:steered? s))))
    (testing "a finger that only rests does not take it over until it moves"
      (let [rest* (-> two (step :press [look-pt]) (step :down [look-pt]))]
        (is (not (:steered? rest*)))))))

(deftest a-resting-finger-never-steers
  (doseq [[label ids] [["with ids" [[4] [4 5] [5] [5]]] ["without ids" [nil nil nil nil]]]
          :let [[i1 i2 i3 i4] ids]]
    (testing (str "the stick, " label)
      (let [held (-> steered (step :press [stick-pt] i1)
                     (step :down [(at-px stick-pt 0.0 -200.0)] i1)
                     (step :press [(at-px stick-pt 0.0 -200.0) (at-px stick-pt 400.0 0.0)] i2))
            lifted (step held :down [(at-px stick-pt 400.0 0.0)] i3)
            later (nth (iterate #(step % :down [(at-px stick-pt 400.0 0.0)] i4) lifted) 6)]
        (is (some? (:stick held)) "the first finger holds the stick")
        (is (nil? (:stick lifted)) "the second does not inherit it")
        (is (= (eye lifted) (eye later)) "the camera stays put")))
    (testing (str "a finger already down at the start, " label)
      (let [s (nth (iterate #(step % :down [stick-pt] i1) steered) 3)
            moved (step s :down [(at-px stick-pt 0.0 -200.0)] i1)]
        (is (nil? (:stick s)))
        (is (= (eye steered) (eye moved)))
        (is (= (:yaw steered) (:yaw moved)))))
    (testing (str "a finger under Back or on the button never starts anything, " label)
      (doseq [p [[100.0 60.0] button-pt]]
        (let [s (-> steered (step :press [p] i1) (step :down [(at-px p 0.0 300.0)] i1))]
          (is (nil? (:look s)))
          (is (nil? (:stick s)))
          (is (= (eye steered) (eye s)))))))
  (testing "a rotation of the phone drops the fingers"
    (let [held (-> steered (step :press [look-pt]) (step :down [look-pt]))
          turned (sc/advance held {:metrics {:screen [2334 1206]}
                                   :pointer {:phase :down
                                             :position [900.0 400.0]}
                                   :touch-points [[900.0 400.0]]})]
      (is (some? (:look held)))
      (is (nil? (:look turned))))))

(deftest a-tap-is-not-a-walk-or-a-look
  (testing "a tap that changes nothing in the world leaves the pose alone"
    (let [st (assoc steered :px 3.5 :py 14.0 :pz 3.3 :yaw 0.0 :pitch 1.0)
          s (tap st stick-pt)]
      (is (vnear? (eye st) (eye s)))
      (is (= (:yaw st) (:yaw s)))))
  (testing "a drag removes nothing"
    (let [s (-> steered (step :press [look-pt]) (step :down [(at-px look-pt 200.0 0.0)]) (step :release [(at-px look-pt 200.0 0.0)]))]
      (is (= 512 (count (:world s))))))
  (testing "a tap that ends two fingers removes nothing"
    (let [s (-> steered (step :press [stick-pt]) (step :press [stick-pt look-pt]) (step :release [look-pt]))]
      (is (= 512 (count (:world s)))))))

(deftest both-thumbs-work-at-once
  (doseq [[label ids] [["with ids" [[4] [4 5] [4 5]]] ["without ids" [nil nil nil]]]
          :let [[i1 i2 i3] ids]]
    (testing label
      (let [s1 (step steered :press [stick-pt] i1)
            s2 (step s1 :down [(at-px stick-pt 0.0 -200.0)] i1)
            s3 (step s2 :press [(at-px stick-pt 0.0 -200.0) look-pt] i2)
            s4 (step s3 :down [(at-px stick-pt 0.0 -200.0) (at-px look-pt 100.0 0.0)] i3)]
        (is (some? (:stick s3)))
        (is (some? (:look s3)))
        (is (not= (:yaw s3) (:yaw s4)) "the look finger turned the view")
        (is (not= (eye s3) (eye s4)) "while the stick finger walked")))))

;; --- text -------------------------------------------------------------------------

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [bx by bw bh] (:button dims)]]
    (testing (str screen)
      (is (= 2 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= size 8) s)
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) (second (:viewport dims))) s)
        (is (<= (+ y size) h) s))
      (testing "the button holds its label, sits right of the caption and above the field"
        (let [[caption label] (:lines dims)]
          (is (<= (+ (:x caption) (measure (:s caption) (:size caption))) bx))
          (is (>= (:x label) bx))
          (is (<= (+ (:x label) (measure (:s label) (:size label))) (+ bx bw)))
          (is (<= (+ by bh) (second (:viewport dims))))
          (is (<= (+ bx bw) w))
          (is (>= by (+ back-y back-h))))))))
