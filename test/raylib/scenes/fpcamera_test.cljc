(ns raylib.scenes.fpcamera-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.fpcamera :as sc]
            [raylib.soft3d :as s3]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (sc/geometry m))
(def start (first ((:init (sc/scene)) {:metrics m})))
(def slop (gesture/slop m))

;; Phone geometry: Back ends at 120, pad 18, text 36, so the field starts at
;; 120 + 18 + 36 + 18 = 192 and is 2142 tall. A touch above 192 + 1428 = 1620
;; looks, from there down it walks.
(def look-pt [600.0 600.0])
(def stick-pt [600.0 2000.0])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))
(defn- near-px? [a b] (< (abs (double (- a b))) 1e-6))
(defn- vnear? [a b] (and (= (count a) (count b)) (every? true? (map near? a b))))

(defn- step
  ([state phase points] (step state phase points nil))
  ([state phase points ids]
   (sc/advance state (cond-> {:metrics m
                              :delta-seconds (/ 1.0 60.0)
                              :pointer {:phase phase
                                        :position (first points)}
                              :touch-points (vec points)}
                       ids (assoc :touches {:ids (vec ids)})))))

(defn- cam [state] (:camera state))
(defn- eye [state] (:position (cam state)))
(defn- view [state] (mapv - (:target (cam state)) (:position (cam state))))
(defn- len [v] (Math/sqrt (reduce + (map * v v))))
(defn- at-px [[x y] dx dy] [(+ x dx) (+ y dy)])

;; The original (camera_3d_first_person.clj): eye y 2, SPEED 0.25 a frame, SENS
;; 0.004, forward (cos yaw, sin yaw) on the ground, so yaw 0 looks down +x.
(def speed 0.25)
(def sens 0.004)
(def scale (/ 800.0 1206.0))

(defn- walked
  "The state after a stick pressed at `stick-pt` and dragged by `dx`, `dy`: one
  update past the dead zone, so one step of the walk."
  [state dx dy]
  (let [pressed (step state :press [stick-pt])]
    (step pressed :down [(at-px stick-pt dx dy)])))

(deftest columns-are-deterministic
  (let [cols sc/columns
        lcg (fn [s] (mod (+ (* 1103515245 s) 12345) 2147483648))
        rnd (fn [s lo hi] (let [s' (lcg s)] [(+ lo (mod (quot s' 65536) (inc (- hi lo)))) s']))
        [x s1] (rnd 20261002 -20 20)
        [z s2] (rnd s1 -20 20)
        [h s3] (rnd s2 2 12)
        [r s4] (rnd s3 60 255)
        [g s5] (rnd s4 60 255)
        [b _] (rnd s5 60 255)]
    (testing "40 columns, as the original"
      (is (= 40 (count cols))))
    (testing "the first column is the LCG seeded 20261002, in the original's order x z h r g b"
      (is (= [(double x) (double z) (double h) [r g b 255]]
             ((juxt :x :z :h :colour) (first cols)))))
    (testing "every column is inside the original's ranges"
      (is (every? #(<= -20 (:x %) 20) cols))
      (is (every? #(<= -20 (:z %) 20) cols))
      (is (every? #(<= 2 (:h %) 12) cols))
      (is (every? #(every? (fn [c] (<= 60 c 255)) (subvec (:colour %) 0 3)) cols))
      (is (every? #(= 255 (nth (:colour %) 3)) cols)))
    (testing "not all alike"
      (is (> (count (distinct (map :x cols))) 10)))))

(deftest first-frame-draws
  (let [dims (sc/dimensions m measure)
        dl (sc/scene-list start dims)
        tris (filterv #(= :tri (nth % 0)) dl)
        lines (filterv #(= :line (nth % 0)) dl)
        [vx vy vw vh] (:viewport dims)
        inside? (fn [[x y]] (and (<= (- vx 1e-6) x (+ vx vw 1e-6)) (<= (- vy 1e-6) y (+ vy vh 1e-6))))]
    (testing "the camera is the original's: eye (0, 2, 0) looking down +x, up y, fovy 60"
      (is (= [0.0 2.0 0.0] (eye start)))
      (is (= [1.0 2.0 0.0] (:target (cam start))))
      (is (= [0.0 1.0 0.0] (:up (cam start))))
      (is (= 60.0 (:fovy (cam start)))))
    (testing "columns and the grid are both on the glass"
      (is (pos? (count tris)))
      (is (pos? (count lines))))
    (testing "the forward grid line, the z = 0 axis, runs up the middle of the field"
      ;; It lies on the line of sight, so both ends project to the field's
      ;; centre x. The rest of the scene is not inside the field: the grid runs
      ;; to +-20 and a column beside the eye fills more than the glass, and the
      ;; draw scissors both to the field (raylib.gallery/draw-in-field!).
      (let [axis (filterv #(and (= [127 127 127 255] (subvec % 5 9))
                                (near? (nth % 1) (nth % 3)))
                          lines)]
        (is (= 1 (count axis)))
        (is (every? #(near? (+ vx (* 0.5 vw)) (nth % 1)) axis))))
    (testing "every projected coordinate is a finite number, and some of it is on the glass"
      (is (every? (fn [[_ & more]] (every? #(and (number? %) (< (abs (double %)) 1e5)) (take 6 more))) tris))
      (is (some (fn [[_ & more]] (some inside? (partition 2 (take 6 more)))) tris))
      (is (some (fn [[_ x0 y0 x1 y1]] (or (inside? [x0 y0]) (inside? [x1 y1]))) lines)))
    (testing "what scene-list draws is seen through the fitted camera, a grid of 40 and all 40 columns"
      ;; fit-camera for a field narrower than 800x450 keeps the original's
      ;; horizontal view: hfov = 2 atan(tan(fovy/2) * 800/450), then
      ;; fovy' = 2 atan(tan(hfov/2) / field aspect). Worked here from the
      ;; formula, not by calling fit-camera.
      (let [aspect (/ vw vh)
            fovy (Math/toDegrees (* 2.0 (Math/atan (/ (* (/ 800.0 450.0) (Math/tan (Math/toRadians 30.0))) aspect))))
            vp (s3/view-proj (assoc (cam start) :fovy fovy) (:viewport dims))
            end (s3/world->screen vp [20.0 0.0 1.0])
            ;; the same camera at the unfitted 60 degrees, to show it differs
            unfitted (s3/world->screen (s3/view-proj (cam start) (:viewport dims)) [20.0 0.0 1.0])]
        (is (< 100.0 fovy 180.0) "the field is narrow, so the fit widens the original's 60")
        (is (> (abs (- (first end) (first unfitted))) 5.0))
        (testing "the camera used is the fitted one: the grid line z = 1 ends at (20, 0, 1) where it projects"
          (is (some (fn [l] (and (near-px? (nth l 3) (first end)) (near-px? (nth l 4) (second end)))) lines)))
        (testing "the grid reaches +-20, so its slices are 40"
          (is (some (fn [l] (let [[ex ey] (s3/world->screen vp [20.0 0.0 20.0])]
                              (and (near-px? (nth l 3) ex) (near-px? (nth l 4) ey))))
                    lines)))
        (testing "the grid is 41 lines along x and, of the 41 along z, the 20 ahead of the near plane"
          (is (= 61 (count lines))))
        (testing "every column is drawn: the triangles are the sum of each column's own"
          (is (= 40 (count sc/columns)))
          (is (= (count tris)
                 (reduce + (map (fn [{:keys [x z h colour]}]
                                  (count (s3/cube [] vp nil [x (/ h 2.0) z] [2.0 h 2.0] colour)))
                                sc/columns)))))))
    (testing "the sky colour is not a column's"
      (is (not-any? #(= sc/sky-colour (subvec % 7 11)) tris)))))

(deftest the-stick-walks-at-the-originals-speed
  (testing "up the glass is W: 0.25 along +x a frame from the first, nothing in y"
    (let [pressed (step start :press [stick-pt])
          one (step pressed :down [(at-px stick-pt 0.0 -200.0)])
          two (step one :down [(at-px stick-pt 0.0 -200.0)])]
      (is (= (eye start) (eye pressed)) "the press walks nowhere")
      (is (vnear? [speed 2.0 0.0] (eye one)))
      (is (vnear? [(* 2 speed) 2.0 0.0] (eye two)))
      (is (vnear? (mapv + [1.0 0.0 0.0] (eye one)) (mapv + [0.0 0.0 0.0] (:target (cam one)))) "the view moves with it")))
  (testing "right is D, +z when looking down +x; down is S; left is A"
    (is (vnear? [0.0 2.0 speed] (eye (walked start 200.0 0.0))))
    (is (vnear? [(- speed) 2.0 0.0] (eye (walked start 0.0 200.0))))
    (is (vnear? [0.0 2.0 (- speed)] (eye (walked start -200.0 0.0)))))
  (testing "a diagonal is the same speed, where the original's two keys add to 1.41 times"
    (let [e (eye (walked start 150.0 -150.0))
          k (/ speed (Math/sqrt 2.0))]
      (is (near? speed (len [(first e) (nth e 2)])))
      (is (vnear? [k 2.0 k] e))))
  (testing "inside the dead zone nothing moves"
    (is (= (eye start) (eye (walked start (* 0.5 slop) 0.0)))))
  (testing "strafing follows the yaw: right is (-sin yaw, cos yaw), the original's rgx = -fwz, rgz = fwx"
    (doseq [yaw [(/ Math/PI 2.0) 0.6 -1.1]
            :let [st (assoc start :yaw yaw)]]
      (is (vnear? [(* speed (- (Math/sin yaw))) 2.0 (* speed (Math/cos yaw))] (eye (walked st 200.0 0.0))) (str "D at yaw " yaw))
      (is (vnear? [(* speed (Math/sin yaw)) 2.0 (* speed (- (Math/cos yaw)))] (eye (walked st -200.0 0.0))) (str "A at yaw " yaw))
      (is (vnear? [(* speed (Math/cos yaw)) 2.0 (* speed (Math/sin yaw))] (eye (walked st 0.0 -200.0))) (str "W at yaw " yaw))))
  (testing "walking follows the yaw: after a quarter turn up the glass is +z"
    (let [turned (assoc start :yaw (/ Math/PI 2.0))]
      (is (vnear? [0.0 2.0 speed] (eye (walked turned 0.0 -200.0))))))
  (testing "walking stays on the ground when the view is tipped up"
    (let [tipped (assoc start :pitch 0.7)
          e (eye (walked tipped 0.0 -200.0))]
      (is (vnear? [speed 2.0 0.0] e)))))

(deftest a-drag-looks
  (let [[x0 y0] look-pt
        dx 100.0
        dy 40.0
        yaw (* sens dx scale)
        pitch (* -1.0 sens dy scale)
        pressed (step start :press [look-pt])
        dragged (step pressed :down [[(+ x0 dx) (+ y0 dy)]])
        ;; the original: target = eye + (cos p cos yaw, sin p, cos p sin yaw)
        expected [(* (Math/cos pitch) (Math/cos yaw)) (Math/sin pitch) (* (Math/cos pitch) (Math/sin yaw))]]
    (testing "the press looks nowhere"
      (is (= (cam start) (cam pressed))))
    (testing "a drag right and down turns the view right by 0.004 rad a pixel and tips it down, as the original"
      (is (vnear? expected (view dragged)))
      (is (vnear? (eye start) (eye dragged)) "looking does not walk"))
    (testing "a drag left looks left and a drag up looks up"
      (let [l (step (step start :press [look-pt]) :down [(at-px look-pt -100.0 -40.0)])
            v (view l)]
        (is (neg? (nth v 2)))
        (is (pos? (nth v 1)))))
    (testing "one look step changes yaw by SENS * dx and pitch by -SENS * dy, as the original's yaw/pitch lines"
      (is (near? yaw (:yaw dragged)))
      (is (near? pitch (:pitch dragged)))
      (is (near? 0.0 (:yaw start)))
      (is (near? 0.0 (:pitch start))))
    (testing "the pitch is held to +-1.4 radians, the original's (max -1.4) (min 1.4)"
      (let [drag (fn [s k] (step s :down [(at-px look-pt 0.0 k)]))
            up (reduce drag (step start :press [look-pt]) (map #(* -300.0 (inc %)) (range 6)))
            down (reduce drag (step start :press [look-pt]) (map #(* 300.0 (inc %)) (range 6)))]
        (is (near? 1.4 (:pitch up)))
        (is (near? -1.4 (:pitch down)))
        (is (vnear? [(Math/cos 1.4) (Math/sin 1.4) 0.0] (view up)))
        (is (vnear? [(Math/cos 1.4) (- (Math/sin 1.4)) 0.0] (view down)))))))

(deftest a-resting-finger-never-steers
  (doseq [[label ids] [["with ids" [[4] [4 5] [5] [5]]] ["without ids" [nil nil nil nil]]]
          :let [[i1 i2 i3 i4] ids]]
    (testing (str "the stick, " label)
      (let [held (-> start (step :press [stick-pt] i1)
                     (step :down [(at-px stick-pt 0.0 -200.0)] i1)
                     (step :press [(at-px stick-pt 0.0 -200.0) (at-px stick-pt 400.0 0.0)] i2))
            lifted (step held :down [(at-px stick-pt 400.0 0.0)] i3)
            later (nth (iterate #(step % :down [(at-px stick-pt 400.0 0.0)] i4) lifted) 6)]
        (is (some? (:stick held)) "the first finger holds the stick")
        (is (nil? (:stick lifted)) "the second does not inherit it")
        (is (= (eye lifted) (eye later)) "the camera stays put")))
    (testing (str "a finger already down at the start, " label)
      (let [s (nth (iterate #(step % :down [stick-pt] i1) start) 3)
            moved (step s :down [(at-px stick-pt 0.0 -200.0)] i1)]
        (is (nil? (:stick s)))
        (is (= (cam start) (cam moved)))))
    (testing (str "a finger under Back never starts anything, " label)
      (let [s (-> start (step :press [[100.0 60.0]] i1)
                  (step :down [[100.0 400.0]] i1))]
        (is (nil? (:look s)))
        (is (nil? (:stick s)))
        (is (= (cam start) (cam s))))))
  (testing "a tap walks and looks nowhere"
    (let [s (-> start (step :press [stick-pt]) (step :release [stick-pt]))]
      (is (= (cam start) (cam s)))))
  (testing "a rotation of the phone drops the fingers"
    (let [held (-> start (step :press [look-pt]) (step :down [look-pt]))
          turned (sc/advance held {:metrics {:screen [2334 1206]}
                                   :pointer {:phase :down
                                             :position [900.0 400.0]}
                                   :touch-points [[900.0 400.0]]})]
      (is (some? (:look held)))
      (is (nil? (:look turned)))
      (is (= (cam held) (cam turned))))))

(deftest both-thumbs-work-at-once
  (doseq [[label ids] [["with ids" [[4] [4 5] [4 5]]] ["without ids" [nil nil nil]]]
          :let [[i1 i2 i3] ids]]
    (testing label
      (let [s1 (step start :press [stick-pt] i1)
            s2 (step s1 :down [(at-px stick-pt 0.0 -200.0)] i1)
            s3 (step s2 :press [(at-px stick-pt 0.0 -200.0) look-pt] i2)
            s4 (step s3 :down [(at-px stick-pt 0.0 -200.0) (at-px look-pt 100.0 0.0)] i3)]
        (is (some? (:stick s3)))
        (is (some? (:look s3)))
        (is (not= (view s3) (view s4)) "the look finger turned the view")
        (is (not= (eye s3) (eye s4)) "while the stick finger walked")))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (= 1 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= size 8) s)
        (is (>= x 0) s)
        (is (<= (measure s size) (* 0.92 w)) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) (second (:viewport dims))) s)
        (is (<= (+ y size) h) s)))))
