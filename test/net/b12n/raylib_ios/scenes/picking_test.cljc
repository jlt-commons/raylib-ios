(ns net.b12n.raylib-ios.scenes.picking-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.picking :as sc]
            [net.b12n.raylib-ios.soft3d :as s3]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def dims (sc/dimensions m (fn [s size] (* 0.6 size (count s)))))
(def start (first ((:init (sc/scene)) {:metrics m})))
(def dt (/ 1.0 60.0))
(def slop (gesture/slop m))
(def vx (nth (:viewport dims) 0))
(def vy (nth (:viewport dims) 1))
(def vw (nth (:viewport dims) 2))
(def vh (nth (:viewport dims) 3))

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b eps] (< (abs (double (- a b))) eps))
(defn- vnear? [a b eps] (every? true? (map #(near? %1 %2 eps) a b)))

(defn- step
  "One frame. `points` are the touch points; the pointer is the first, or
  `pointer` when given (a release's position is garbage on a device)."
  ([state phase points] (step state phase points (first points)))
  ([state phase points pointer]
   (sc/advance state {:metrics m
                      :delta-seconds dt
                      :pointer {:phase phase
                                :position pointer}
                      :touch-points (vec points)})))

(defn- tap
  "A tap pressed at `p`, held a frame, and released with the position `away`."
  ([state p] (tap state p [5.0 5.0]))
  ([state p away]
   (-> state
       (step :press [p])
       (step :down [p])
       (step :release [] away))))

(defn- dist3 [a b] (Math/sqrt (reduce + (map #(let [d (- %1 %2)] (* d d)) a b))))

(defn- idle [state] (step state :idle [] nil))

(defn- screen-of [state pt] (s3/world->screen (sc/view state dims) pt))
(defn- centre-of [state] (screen-of state [0.0 1.0 0.0]))

(def cube-lo [-1.0 0.0 -1.0])
(def cube-hi [1.0 2.0 1.0])

(deftest first-frame-draws
  (let [dl (sc/scene-list start dims)
        tris (filterv #(= :tri (nth % 0)) dl)
        lines (filterv #(= :line (nth % 0)) dl)
        cam (sc/camera start dims)
        wide (sc/dimensions {:screen [2334 1206]} measure)]
    (testing "the camera is the original's first orbit frame: (14, 10, 0) looking at (0, 1, 0), fovy 45"
      (is (vnear? [14.0 10.0 0.0] (:position cam) 1e-9))
      (is (= [0.0 1.0 0.0] (:target cam)))
      (is (= 45.0 (:fovy (sc/camera-at 0.0 10.0))))
      (is (= 45.0 (:fovy (sc/camera start wide))) "kept in a field as wide as 800x450")
      (is (> (:fovy cam) 45.0) "widened in the narrow phone field")
      (is (= :perspective (:projection cam))))
    (testing "from there two faces of the cube show, two triangles each, drawn flat GRAY"
      (is (= 4 (count tris)))
      (is (every? (fn [t] (= [130 130 130 255] (subvec t 7 11))) tris)))
    (testing "22 lines of grid of 10 and the 7 DARKGRAY wires of the edges that show"
      (is (= 29 (count lines)))
      (is (= 7 (count (filter #(= [80 80 80 255] (subvec % 5 9)) lines))))
      (is (= 22 (count (filter #(#{[127 127 127 255] [191 191 191 255]} (subvec % 5 9)) lines)))))
    (testing "nothing is picked and no ray is drawn yet"
      (is (nil? (:ray start)))
      (is (false? (:hit? start)))
      (is (= [] (sc/readout start))))
    (testing "the cube sits at the middle of the field"
      (let [[cx cy] (centre-of start)]
        (is (near? cx (+ vx (* 0.5 vw)) 1e-6))
        (is (< vy cy (+ vy vh)))))))

(deftest a-tap-on-the-cube-hits
  (let [c (centre-of start)
        s (tap start c [3.0 9.0])
        pick (:pick s)]
    (testing "the press position picks; the release position (garbage here) is never read"
      (is (true? (:hit? s)))
      (is (true? (:hit? pick)))
      (is (pos? (:distance pick))))
    (testing "the ray is kept, from the camera"
      (is (vnear? (:position (sc/camera start dims)) (:position (:ray s)) 1e-9)))
    (testing "the readout says so, with the distance, the point and the normal"
      (let [lines (sc/readout s)]
        (is (= 4 (count lines)))
        (is (= "BOX SELECTED" (:s (first lines))))
        (is (str/starts-with? (:s (nth lines 1)) "distance "))
        (is (str/starts-with? (:s (nth lines 2)) "point "))
        (is (str/starts-with? (:s (nth lines 3)) "normal "))))
    (testing "the tap does not move the camera"
      (is (= (:angle start) (:angle s)))
      (is (= (:height start) (:height s))))
    (testing "a tap while selected lets go, as the original's latch does, and keeps the ray"
      (let [s2 (tap s c)]
        (is (false? (:hit? s2)))
        (is (some? (:ray s2)))
        (is (= [] (sc/readout s2)))))
    (testing "a press beside the cube released on it is a miss: the press decides"
      (let [s3 (tap start [(+ vx 20.0) (+ vy 20.0)] c)]
        (is (false? (:hit? s3)))
        (is (some? (:ray s3)))))))

(deftest a-tap-beside-it-misses
  (doseq [p [[(+ vx 20.0) (+ vy 20.0)]
             [(+ vx vw -20.0) (+ vy vh -20.0)]
             [(+ vx (* 0.5 vw)) (+ vy 20.0)]]
          :let [s (tap start p)]]
    (testing (str p)
      (is (false? (:hit? s)))
      (is (false? (:hit? (:pick s))))
      (is (some? (:ray s)) "a miss still leaves its ray")
      (is (= ["missed"] (map :s (sc/readout s)))))))

(deftest the-hit-point-lies-on-the-box
  (let [[cx cy] (centre-of start)]
    (doseq [[dx dy] [[0.0 0.0] [30.0 0.0] [-30.0 10.0] [0.0 -40.0] [50.0 40.0]]
            :let [p [(+ cx dx) (+ cy dy)]
                  s (tap start p)
                  {:keys [point normal distance]} (:pick s)
                  {:keys [position direction]} (:ray s)
                  [px py pz] point]]
      (testing (str p)
        (is (true? (:hit? s)))
        (is (every? true? (map #(<= (- %1 1e-6) %2 (+ %3 1e-6)) cube-lo point cube-hi)) "inside the box")
        (is (some true? [(near? (abs px) 1.0 1e-6) (near? py 0.0 1e-6) (near? py 2.0 1e-6) (near? (abs pz) 1.0 1e-6)])
            "on a face")
        (is (= 1.0 (reduce + (map abs normal))) "a unit axis")
        (is (vnear? point (mapv + position (map #(* distance %) direction)) 1e-6))
        (is (vnear? p (screen-of start point) 1e-6) "projects back to the tapped pixel")))))

(deftest a-drag-orbits-and-does-not-pick
  (let [k (* sc/sensitivity (:look-scale (sc/geometry m)))
        c (centre-of start)
        run (fn [state pts]
              (let [s (step state :press [(first pts)])
                    s (reduce #(step %1 :down [%2]) s (rest pts))]
                (step s :release [] [1.0 1.0])))
        right (mapv (fn [i] [(+ (first c) (* 20.0 i)) (second c)]) (range 11))
        down (mapv (fn [i] [(first c) (+ (second c) (* 20.0 i))]) (range 11))
        sr (run start right)
        sd (run start down)]
    (testing "a drag to the right turns the camera round by 200 pixels' worth, and picks nothing"
      (is (near? (+ (:angle start) (* 200.0 k)) (:angle sr) 1e-9))
      (is (= (:height start) (:height sr)))
      (is (nil? (:ray sr)))
      (is (false? (:hit? sr))))
    (testing "a drag down raises the camera, within bounds, and picks nothing"
      (is (> (:height sd) (:height start)))
      (is (nil? (:ray sd)))
      (is (<= sc/min-height (:height sd) sc/max-height)))
    (testing "the camera keeps its radius and its target, so the cube stays in view"
      (doseq [s [sr sd]
              :let [cam (sc/camera s dims)
                    [px _ pz] (:position cam)
                    [sx sy] (centre-of s)]]
        (is (near? sc/orbit-radius (Math/sqrt (+ (* px px) (* pz pz))) 1e-9))
        (is (= [0.0 1.0 0.0] (:target cam)))
        (is (< vx sx (+ vx vw)))
        (is (< vy sy (+ vy vh)))))
    (testing "the height stops at its bounds"
      (let [far (mapv (fn [i] [(first c) (+ (second c) (* 100.0 i))]) (range 20))
            up (mapv (fn [i] [(first c) (- (second c) (* 100.0 i))]) (range 20))]
        (is (= sc/max-height (:height (run start far))))
        (is (= sc/min-height (:height (run start up))))))
    (testing "a wobble inside the slop is a tap: it picks and does not move the camera"
      (let [s (-> start
                  (step :press [c])
                  (step :down [[(+ (first c) (* 0.5 slop)) (second c)]])
                  (step :release [] [0.0 0.0]))]
        (is (true? (:hit? s)))
        (is (= (:angle start) (:angle s)))))))

(deftest the-camera-drifts-while-idle-and-stops-for-a-finger
  (testing "an idle update turns the camera 0.005 radians, as the original's orbit-frame does"
    (is (near? 0.005 (:angle (idle start)) 1e-12))
    (is (near? 0.025 (:angle (nth (iterate idle start) 5)) 1e-12)))
  (testing "the height is the original's 10 and the radius 14 on every frame"
    (let [cam (sc/camera (nth (iterate idle start) 100) dims)
          [px py pz] (:position cam)]
      (is (= 10.0 py))
      (is (near? 14.0 (Math/sqrt (+ (* px px) (* pz pz))) 1e-9))))
  (testing "it does not drift while a finger is down, nor on the press or the release"
    (let [c (centre-of start)
          s1 (step start :press [c])
          s2 (step s1 :down [c])
          s3 (step s2 :release [] c)]
      (is (= (:angle start) (:angle s1) (:angle s2) (:angle s3)))
      (is (near? 0.005 (:angle (idle s3)) 1e-12)))))

(deftest more-than-one-finger-neither-orbits-nor-picks
  (let [c (centre-of start)
        a c
        b [(+ (first c) 300.0) (+ (second c) 300.0)]
        b2 [(+ (first b) 200.0) (second b)]
        a2 [(- (first a) 200.0) (second a)]]
    (testing "a second finger ends the orbit, and the first does not take it back when the second lifts"
      (let [s (-> start
                  (step :press [a])
                  (step :down [a2])
                  (step :press [a2 b])
                  (step :down [a2 b2])
                  (step :down [a2]))
            moved (:angle s)]
        (is (nil? (:ray s)))
        (is (= (:angle (step s :down [[(- (first a2) 100.0) (second a2)]])) moved)
            "the lone survivor was already down, so it does not orbit")))
    (testing "two fingers that land together neither orbit nor pick"
      (let [s (-> start
                  (step :press [a b])
                  (step :down [a2 b2])
                  (step :release [] nil))]
        (is (nil? (:ray s)))
        (is (= (:angle start) (:angle s)))))
    (testing "a second finger tapping while the first rests does not pick"
      (let [s (-> start
                  (step :press [a])
                  (step :press [a c])
                  (step :down [a c])
                  (step :release [] c))]
        (is (nil? (:ray s)))))
    (testing "a finger already down when the scene opens never orbits"
      (let [s (-> start
                  (step :down [c])
                  (step :down [[(+ (first c) 400.0) (second c)]]))]
        (is (= (:angle start) (:angle s)))))))

(deftest a-rotation-drops-the-tracking
  (let [c (centre-of start)
        s (-> start (step :press [c]))
        turned (sc/advance s {:metrics {:screen [2334 1206]}
                              :delta-seconds dt
                              :pointer {:phase :down
                                        :position [100.0 100.0]}
                              :touch-points [[100.0 100.0]]})]
    (is (= [2334 1206] (:screen turned)))
    (is (= (:angle start) (:angle turned)))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                d (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (seq (:lines d)))
      (doseq [{:keys [s x y size]} (:lines d)]
        (is (>= size 8) s)
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) h) s))
      (testing "the readout of a real pick fits the field's width at its slots' size"
        (let [lines (sc/readout (-> start
                                    (assoc :pick {:hit? true
                                                  :distance 28.74
                                                  :point [-1.0 2.0 -1.0]
                                                  :normal [-1.0 0.0 0.0]})
                                    (assoc :hit? true)))
              [_ fy2 _ fh] (:viewport d)]
          (is (= 4 (count lines)))
          (is (= 4 (count (:readout-slots d))))
          (doseq [[{:keys [s]} {:keys [x y size]}] (map vector lines (:readout-slots d))]
            (is (<= (+ x (measure s size)) w) s)
            (is (>= y fy2) s)
            (is (<= (+ y size) (+ fy2 fh)) s)))))))

;; --- the hit state is drawn ---------------------------------------------------

(def red [230 41 55 255])
(def maroon [190 33 55 255])
(def green [0 228 48 255])
(def gray [130 130 130 255])

(defn- colour-of [item] (if (= :tri (nth item 0)) (subvec item 7 11) (subvec item 5 9)))
(defn- of-colour [dl kind c] (filterv #(and (= kind (nth % 0)) (= c (colour-of %))) dl))
(defn- drifted
  "`state` a few updates on, so the camera is no longer where the ray was cast."
  [state]
  (nth (iterate idle state) 60))

(deftest a-hit-is-drawn-with-its-ray-and-its-green-wires
  (let [hit (drifted (tap start (centre-of start)))
        no-ray (assoc hit :ray nil)
        dl (sc/scene-list hit dims)
        base (sc/scene-list no-ray dims)]
    (is (true? (:hit? hit)))
    (testing "the cube is RED, flat, and its wires MAROON"
      (is (pos? (count (of-colour dl :tri red))))
      (is (zero? (count (of-colour dl :tri gray))))
      (is (pos? (count (of-colour base :line maroon)))))
    (testing "the larger wires are GREEN, and there are none before a hit"
      (is (pos? (count (of-colour dl :line green))))
      (is (zero? (count (of-colour (sc/scene-list (drifted start) dims) :line green)))))
    (testing "the ray adds MAROON lines on top of the wires"
      (is (> (count (of-colour dl :line maroon)) (count (of-colour base :line maroon)))))
    (testing "before any pick there is no ray, so nothing beyond the wires"
      (is (zero? (count (of-colour (sc/scene-list (drifted start) dims) :line maroon)))))))

(deftest a-miss-is-drawn-with-its-ray-and-no-green
  (let [missed (drifted (tap start [(+ vx 20.0) (+ vy 20.0)]))
        dl (sc/scene-list missed dims)
        base (sc/scene-list (assoc missed :ray nil) dims)]
    (is (false? (:hit? missed)))
    (is (pos? (count (of-colour dl :tri gray))) "the cube stays GRAY")
    (is (zero? (count (of-colour dl :tri red))))
    (is (zero? (count (of-colour dl :line green))))
    (is (> (count (of-colour dl :line maroon)) (count (of-colour base :line maroon))) "the ray is drawn")))

(defn- inside? [[x y z]]
  (let [e 1e-6]
    (and (< (+ -1.0 e) x (- 1.0 e)) (< (+ 0.0 e) y (- 2.0 e)) (< (+ -1.0 e) z (- 1.0 e)))))

(deftest the-ray-is-split-so-nothing-is-drawn-inside-the-box
  (let [s (tap start (centre-of start))
        ray (:ray s)
        pieces (sc/ray-pieces ray)
        {:keys [distance point]} (:pick s)
        along (fn [[a b] t] (mapv #(+ %1 (* t (- %2 %1))) a b))]
    (testing "a ray through the box is two pieces, split at the entry and the exit"
      (is (= 2 (count pieces)))
      (is (vnear? (:position ray) (ffirst pieces) 1e-9))
      (is (vnear? point (second (first pieces)) 1e-6) "the first piece ends where the pick hit")
      (is (vnear? (mapv + (:position ray) (map #(* sc/ray-length %) (:direction ray)))
                  (second (second pieces)) 1e-6) "the second runs on to the ray's end"))
    (testing "no point of either piece is strictly inside the box"
      (doseq [pc pieces
              t (range 0.0 1.0001 0.01)]
        (is (not (inside? (along pc t))) (str pc " " t))))
    (testing "the exit is further than the entry"
      (is (> (dist3 (second (first pieces)) (first (second pieces))) 1.0))
      (is (near? distance (dist3 (:position ray) (second (first pieces))) 1e-6)))
    (testing "a ray that misses is one whole piece"
      (let [miss (:ray (tap start [(+ vx 20.0) (+ vy 20.0)]))]
        (is (= 1 (count (sc/ray-pieces miss))))))
    (testing "the drawn list holds no maroon line point inside the box in world terms: the piece ends are on the surface"
      (is (every? (fn [[a b]] (and (not (inside? a)) (not (inside? b)))) pieces)))))

(deftest the-readout-text-is-pinned
  (let [s (assoc start :hit? true :pick {:hit? true
                                         :distance 12.34
                                         :point [-1.0 2.0 -0.004]
                                         :normal [-1.0 0.0 0.0]})]
    (is (= ["BOX SELECTED" "distance 12.34" "point -1.00 2.00 0.00" "normal -1 0 0"]
           (map :s (sc/readout s))))
    (is (= "point 0.05 -0.05 10.00"
           (:s (nth (sc/readout (assoc-in s [:pick :point] [0.05 -0.05 10.0])) 2))))
    (is (= [[0 228 48 255] [80 80 80 255] [80 80 80 255] [80 80 80 255]]
           (map :colour (sc/readout s))))
    (is (= [{:s "missed"
             :colour [190 33 55 255]}]
           (sc/readout (assoc start :pick {:hit? false}))))))

(deftest a-drag-keeps-the-selection
  (let [c (centre-of start)
        picked (tap start c)
        pts (mapv (fn [i] [(+ (first c) (* 30.0 i)) (+ (second c) 400.0)]) (range 8))
        s (-> picked
              (step :press [(first pts)])
              (as-> s (reduce #(step %1 :down [%2]) s (rest pts)))
              (step :release [] [0.0 0.0]))]
    (is (true? (:hit? picked)))
    (is (true? (:hit? s)))
    (is (= (:ray picked) (:ray s)))
    (is (not= (:angle picked) (:angle s)))))
