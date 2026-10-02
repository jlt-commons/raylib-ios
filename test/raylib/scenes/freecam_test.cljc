(ns raylib.scenes.freecam-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.freecam :as sc]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def d (sc/geometry m))
(def start (first ((:init (sc/scene)) {:metrics m})))
(def dt (/ 1.0 60.0))
(def slop (gesture/slop m))

;; geometry of the phone screen: Back ends at 120, pad 18, text 36, so the field
;; starts at 120 + 18 + 36 + 18 = 192 and is 2142 tall. The look region is its
;; upper two thirds, so the stick starts at 192 + 1428 = 1620.
(def look-pt [600.0 600.0])
(def look-pt2 [300.0 700.0])
(def stick-pt [600.0 2000.0])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near?
  ([a b] (near? a b 1e-9))
  ([a b eps] (< (abs (double (- a b))) eps)))

(defn- vnear? [a b] (every? true? (map (fn [x y] (near? x y 1e-9)) a b)))

(defn- v- [a b] (mapv - a b))
(defn- v+ [a b] (mapv + a b))
(defn- vs [k a] (mapv #(* k %) a))
(defn- len [[x y z]] (Math/sqrt (+ (* x x) (* y y) (* z z))))
(defn- dot [[a b c] [x y z]] (+ (* a x) (* b y) (* c z)))
(defn- cross [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by)) (- (* az bx) (* ax bz)) (- (* ax by) (* ay bx))])
(defn- unit [a] (vs (/ 1.0 (len a)) a))

(defn- step
  "One frame. `points` are the touch points (the first is the pointer)."
  ([state phase points] (step state phase points dt))
  ([state phase points seconds]
   (sc/advance state {:metrics m
                      :delta-seconds seconds
                      :pointer {:phase phase
                                :position (first points)}
                      :touch-points (vec points)})))

(defn- step-ids
  "`step` with the host's touch ids alongside the points."
  [state phase points ids]
  (sc/advance state {:metrics m
                     :delta-seconds dt
                     :pointer {:phase phase
                               :position (first points)}
                     :touch-points (vec points)
                     :touches {:ids (vec ids)}}))

(defn- at-px [[x y] dx dy] [(+ x dx) (+ y dy)])

(defn- idle [state] (step state :idle []))
(defn- cam [state] (:camera state))
(defn- view [c] (v- (:target c) (:position c)))

(def original-view [-10.0 -10.0 -10.0])
(def home-fwd (unit original-view))
(def home-right (unit [1.0 0.0 -1.0]))
(def home-distance (* 10.0 (Math/sqrt 3.0)))

(deftest first-frame-draws
  (let [dims (sc/dimensions m measure)
        dl (sc/scene-list start dims)
        tris (filterv #(= :tri (nth % 0)) dl)
        lines (filterv #(= :line (nth % 0)) dl)
        [vx vy vw vh] (:viewport dims)]
    (testing "the camera is the original's: (10, 10, 10) looking at the origin, fovy 45"
      (is (= [10.0 10.0 10.0] (:position (cam start))))
      (is (= [0.0 0.0 0.0] (:target (cam start))))
      (is (= [0.0 1.0 0.0] (:up (cam start))))
      (is (= 45.0 (:fovy (cam start))))
      (is (= :perspective (:projection (cam start)))))
    (testing "from a corner three faces of the cube show, two triangles each"
      (is (= 6 (count tris))))
    (testing "DrawCube is flat: every face is the original's RED, unshaded"
      (is (every? (fn [t] (= [230 41 55 255] (subvec t 7 11))) tris)))
    (testing "22 lines of grid and the 9 wires that are not hidden behind the cube"
      (is (= 31 (count lines)))
      (is (= 9 (count (filter #(= [190 33 55 255] (subvec % 5 9)) lines))) "MAROON wires")
      (is (= 22 (count (filter #(#{[127 127 127 255] [191 191 191 255]} (subvec % 5 9)) lines)))))
    (testing "the cube sits where the camera looks, at the middle of the field"
      (let [xs (mapcat (fn [t] [(nth t 1) (nth t 3) (nth t 5)]) tris)
            ys (mapcat (fn [t] [(nth t 2) (nth t 4) (nth t 6)]) tris)]
        (is (< (apply min xs) (+ vx (* 0.5 vw)) (apply max xs)))
        (is (< (apply min ys) (+ vy (* 0.5 vh)) (apply max ys)))
        (is (near? (+ vx (* 0.5 vw)) (* 0.5 (+ (apply min xs) (apply max xs))) 40.0))))
    (testing "the HUD is there too"
      (is (= 4 (count (:hud-lines dims)))))))

(deftest a-drag-looks
  (let [[x0 y0] look-pt
        dx 100.0
        dy 40.0
        scale (/ 800.0 1206.0)
        yaw (* -1.0 dx 0.003 scale)
        pitch (* -1.0 dy 0.003 scale)
        pressed (step start :press [look-pt])
        dragged (step pressed :down [[(+ x0 dx) (+ y0 dy)]])
        ;; yaw about +y: x' = x cos + z sin, z' = -x sin + z cos
        [vx vy vz] original-view
        yawed [(+ (* vx (Math/cos yaw)) (* vz (Math/sin yaw))) vy
               (+ (* -1.0 vx (Math/sin yaw)) (* vz (Math/cos yaw)))]
        right (unit (cross yawed [0.0 1.0 0.0]))
        pitched (v+ (vs (Math/cos pitch) yawed) (vs (Math/sin pitch) (cross right yawed)))]
    (testing "the press itself looks nowhere"
      (is (= (cam start) (cam pressed))))
    (testing "a drag turns the view by -0.003 rad a pixel, yaw first and then pitch"
      (is (vnear? (view (cam dragged)) pitched)))
    (testing "it turns about the camera, which stays where it was, and keeps up"
      (is (= [10.0 10.0 10.0] (:position (cam dragged))))
      (is (= [0.0 1.0 0.0] (:up (cam dragged))))
      (is (near? (len original-view) (len (view (cam dragged))))))
    (testing "holding still turns nothing"
      (is (= (cam dragged) (cam (step dragged :down [[(+ x0 dx) (+ y0 dy)]])))))
    (testing "a release is not read"
      (let [lifted (step dragged :release [[0.0 0.0]])]
        (is (= (cam dragged) (cam lifted)))
        (is (= (cam dragged) (cam (idle lifted))))))
    (testing "a drag that begins in the lower region turns nothing"
      (let [low (-> start (step :press [stick-pt]) (step :down [(v+ stick-pt [100.0 0.0])]))]
        (is (= [0.0 1.0 0.0] (:up (cam low))))
        (is (vnear? (view (cam start)) (view (cam low))) "the stick slides it, it does not turn it")))
    (testing "a finger that began before the scene opened looks nowhere"
      (is (= (cam start) (cam (step start :down [look-pt])))))))

(deftest looking-cannot-somersault
  ;; With ids: a 100000 px jump in one frame is beyond the 0.3 bound that
  ;; identity falls back to without them.
  (let [pressed (step-ids start :press [look-pt] [1])
        up (step-ids pressed :down [(v+ look-pt [0.0 -100000.0])] [1])
        down (step-ids pressed :down [(v+ look-pt [0.0 100000.0])] [1])]
    (testing "dragging far up stops 0.001 rad short of straight up"
      (is (near? (Math/cos 0.001) (dot [0.0 1.0 0.0] (unit (view (cam up)))) 1e-9)))
    (testing "and far down stops 0.001 rad short of straight down"
      (is (near? (- (Math/cos 0.001)) (dot [0.0 1.0 0.0] (unit (view (cam down)))) 1e-9)))
    (testing "up is never rotated (rotateUp false)"
      (is (= [0.0 1.0 0.0] (:up (cam up)))))))

(deftest the-stick-moves-along-the-view
  (let [[sx sy] stick-pt
        speed (* 5.4 dt)
        pressed (step start :press [stick-pt])
        fwd (step pressed :down [[sx (- sy 300.0)]])
        back (step pressed :down [[sx (+ sy 300.0)]])
        right (step pressed :down [[(+ sx 300.0) sy]])
        left (step pressed :down [[(- sx 300.0) sy]])
        diag (step pressed :down [[(+ sx 212.0) (- sy 212.0)]])]
    (testing "the press moves nothing"
      (is (= (cam start) (cam pressed))))
    (testing "up the glass is forward along the view, 5.4 units a second"
      (is (vnear? (:position (cam fwd)) (v+ [10.0 10.0 10.0] (vs speed home-fwd))))
      (testing "target and position move together, so the view does not turn"
        (is (vnear? (view (cam fwd)) original-view))))
    (testing "down the glass is backward, and sideways is along the camera's right"
      (is (vnear? (:position (cam back)) (v- [10.0 10.0 10.0] (vs speed home-fwd))))
      (is (vnear? (:position (cam right)) (v+ [10.0 10.0 10.0] (vs speed home-right))))
      (is (vnear? (:position (cam left)) (v- [10.0 10.0 10.0] (vs speed home-right)))))
    (testing "the speed is one value, a diagonal included, and the distance does not change it"
      (is (near? speed (len (v- (:position (cam diag)) [10.0 10.0 10.0])) 1e-3))
      (is (= (cam fwd) (cam (step-ids (step-ids start :press [stick-pt] [1])
                                      :down [[sx (- sy 900.0)]] [1])))
          "900 px is past the fallback bound, so this one carries its id"))
    (testing "it scales with the frame time, and a frame of no time moves nothing"
      (is (vnear? (:position (cam (step pressed :down [[sx (- sy 300.0)]] 0.5)))
                  (v+ [10.0 10.0 10.0] (vs (* 5.4 0.5) home-fwd))))
      (is (= (cam start) (cam (step pressed :down [[sx (- sy 300.0)]] 0.0)))))
    (testing "inside the slop, or lifting, it stops"
      (is (= (cam start) (cam (step pressed :down [[sx (- sy (* 0.99 slop))]]))))
      (is (not= (cam start) (cam (step pressed :down [[sx (- sy (* 1.01 slop))]]))))
      (let [lifted (step fwd :release [[0.0 0.0]])]
        (is (= (cam fwd) (cam lifted)))
        (is (= (cam fwd) (cam (idle lifted))))
        (is (nil? (:stick (idle lifted))))))
    (testing "it keeps going while held"
      (is (vnear? (:position (cam (step fwd :down [[sx (- sy 300.0)]])))
                  (v+ [10.0 10.0 10.0] (vs (* 2.0 speed) home-fwd)))))
    (testing "the stick turns with the view: after a look it goes the way the camera now faces"
      (let [looked (-> start (step :press [look-pt]) (step :down [(v+ look-pt [100.0 0.0])]))
            fwd' (unit (view (cam looked)))
            walked (-> looked (step :press [stick-pt]) (step :down [[sx (- sy 300.0)]]))]
        (is (not (vnear? fwd' home-fwd)))
        (is (vnear? (:position (cam walked)) (v+ (:position (cam looked)) (vs speed fwd'))))))))

(deftest a-pinch-dollies
  (let [a [300.0 600.0]
        b [900.0 600.0]
        pressed (step start :press [a b])
        held (step pressed :down [a b])
        apart (step held :down [[200.0 600.0] [1000.0 600.0]])
        together (step held :down [[450.0 600.0] [750.0 600.0]])]
    (testing "the press frame and a still pinch move nothing"
      (is (= (cam start) (cam pressed)))
      (is (= (cam start) (cam held))))
    (testing "fingers 600 apart going to 800 bring the camera to 3/4 of its distance, target fixed"
      (let [c (cam apart)]
        (is (= [0.0 0.0 0.0] (:target c)))
        (is (near? (* 0.75 home-distance) (len (view c))))
        (is (vnear? (unit (view c)) home-fwd))))
    (testing "fingers coming together push it back to twice the distance"
      (is (near? (* 2.0 home-distance) (len (view (cam together))))))
    (testing "a pinch looks nowhere and walks nowhere"
      (is (= (:target (cam start)) (:target (cam apart))))
      (is (= [0.0 1.0 0.0] (:up (cam apart)))))
    (testing "the order of the two points does not matter"
      (is (= (cam apart) (cam (step held :down [[1000.0 600.0] [200.0 600.0]])))))
    (testing "a third finger moves nothing, nor does one lifting out of three"
      (let [three (step apart :down [[200.0 600.0] [1000.0 600.0] [600.0 900.0]])
            moved (step three :down [[100.0 600.0] [1100.0 600.0] [600.0 900.0]])
            two (step moved :down [[100.0 600.0] [1100.0 600.0]])]
        (is (= (cam apart) (cam three)))
        (is (= (cam apart) (cam moved)))
        (is (= (cam apart) (cam two)) "the count changed, so it only records")
        (is (< (len (view (cam (step two :down [[50.0 600.0] [1150.0 600.0]]))))
               (len (view (cam two)))) "and the next frame pinches on")))
    (testing "one finger lifting leaves the other doing nothing"
      (let [one (step apart :down [[200.0 600.0]])
            moved (step one :down [[500.0 900.0]])]
        (is (= (cam apart) (cam one)))
        (is (= (cam apart) (cam moved)))))
    (testing "the distance never reaches zero: CameraMoveToTarget holds 0.001"
      (is (near? 0.001 (len (view (sc/camera-move-to-target (cam start) -1.0e6))))))))

(deftest a-pinch-needs-both-fingers-in-the-look-region
  (let [stick-top (:stick-top d)
        high [600.0 (- stick-top 1.0)]
        low [600.0 stick-top]]
    (testing "the boundary is the lower third of the field: 192 + 2/3 * 2142"
      (is (near? 1620.0 stick-top))
      (is (= :look (sc/region d high)))
      (is (= :stick (sc/region d low)))
      (is (= :look (sc/region d [0.0 192.0])))
      (is (nil? (sc/region d [0.0 191.0])) "above the field is neither")
      (is (= :stick (sc/region d [1205.0 2333.0])))
      (is (nil? (sc/region d [0.0 2334.0])) "below the screen is neither")
      (is (nil? (sc/region d [1206.0 600.0])) "the right edge is out"))
    (testing "both fingers high is a pinch, they land together or one after the other"
      (let [a [300.0 600.0]
            b [900.0 600.0]
            together (-> start (step :press [a b]) (step :down [[200.0 600.0] [1000.0 600.0]]))
            one-then-two (-> start (step :press [a]) (step :down [a]) (step :down [a b])
                             (step :down [[200.0 600.0] [1000.0 600.0]]))]
        (is (near? (* 0.75 home-distance) (len (view (cam together)))))
        (is (near? (* 0.75 home-distance) (len (view (cam one-then-two)))))))
    (testing "one high and one low is look and stick, never a pinch"
      (let [a [300.0 600.0]
            b [900.0 2000.0]
            two (-> start (step :press [a b]) (step :down [[200.0 600.0] [900.0 2000.0]]))]
        (is (near? home-distance (len (view (cam two)))) "no dolly")
        (is (not (vnear? (unit (view (cam two))) home-fwd)) "the high finger looked")
        (is (vnear? (:position (cam two)) [10.0 10.0 10.0]) "the low finger stayed put, so the stick moved nothing")))
    (testing "both fingers low is no pinch either"
      (let [two (-> start (step :press [[300.0 2000.0] [900.0 2000.0]])
                    (step :down [[200.0 2000.0] [1000.0 2000.0]]))]
        (is (near? home-distance (len (view (cam two)))))))))

(deftest look-and-stick-run-at-once
  (let [a [600.0 600.0]
        b [600.0 2000.0]
        speed (* 5.4 dt)
        one (-> start (step :press [a]) (step :down [a]))
        two (step one :down [a b])
        held (step two :down [a b])
        both (step held :down [[700.0 600.0] [600.0 1700.0]])
        look-only (step (-> start (step :press [a]) (step :down [a])) :down [[700.0 600.0]])
        reversed (step held :down [[600.0 1700.0] [700.0 600.0]])]
    (testing "the second finger landing starts a stick and moves nothing"
      (is (= (cam one) (cam two)))
      (is (= (cam one) (cam held))))
    (testing "next frame the high finger looks as it would alone"
      (is (vnear? (view (cam both)) (view (cam look-only)))))
    (testing "and the low finger walks along the new view in the same frame"
      (is (vnear? (:position (cam both))
                  (v+ [10.0 10.0 10.0] (vs speed (unit (view (cam look-only))))))))
    (testing "which finger is first in the list makes no difference"
      (is (= (cam both) (cam reversed))))
    (testing "the lower finger can drift up into the look region and still be the stick"
      (let [ided (-> start
                     (step-ids :press [a] [1])
                     (step-ids :down [a] [1])
                     (step-ids :down [a b] [1 2])
                     (step-ids :down [a b] [1 2]))
            drift (step-ids ided :down [a [600.0 1000.0]] [1 2])]
        (is (vnear? (:position (cam drift)) (v+ [10.0 10.0 10.0] (vs speed home-fwd))))
        (is (vnear? (view (cam ided)) (view (cam drift))) "the high finger did not move")))
    (testing "lifting the high finger leaves the stick walking, and its position is not read"
      (let [alone (step both :down [[600.0 1700.0]])]
        (is (vnear? (:position (cam alone)) (v+ (:position (cam both))
                                                (vs speed (unit (view (cam both)))))))))
    (testing "lifting the stick finger leaves the look going"
      (let [alone (step both :down [[800.0 600.0]])]
        (is (not (vnear? (view (cam alone)) (view (cam both)))))
        (is (= (:position (cam both)) (:position (cam alone))))))
    (testing "a second finger landing high while the stick is held is a look, not a pinch"
      (let [walking (-> start (step :press [b]) (step :down [[600.0 1700.0]]))
            two (step walking :down [[600.0 1700.0] [300.0 600.0]])
            looked (step two :down [[600.0 1700.0] [400.0 600.0]])]
        (is (near? home-distance (len (view (cam looked)))))
        (is (not (vnear? (unit (view (cam looked))) home-fwd)))))))

(deftest reset-restores-the-originals-camera
  (let [[rx ry rw rh] (:reset d)
        on [(+ rx (* 0.5 rw)) (+ ry (* 0.5 rh))]
        wandered (-> start
                     (step :press [look-pt]) (step :down [(v+ look-pt [200.0 -80.0])])
                     (step :release [look-pt]) idle
                     (step :press [stick-pt]) (step :down [(v+ stick-pt [0.0 -300.0])])
                     (step :release [stick-pt]) idle)
        tapped (-> wandered
                   (step :press [on])
                   (step :down [on])
                   (step :release [on]))]
    (testing "the setup actually moved the camera"
      (is (not= (:target (cam wandered)) [0.0 0.0 0.0]))
      (is (not= (:position (cam wandered)) [10.0 10.0 10.0])))
    (testing "Z in the original sets the target to the origin and nothing else"
      (is (= [0.0 0.0 0.0] (:target (cam tapped))))
      (is (= (:position (cam wandered)) (:position (cam tapped))))
      (is (= (:up (cam wandered)) (:up (cam tapped))))
      (is (= 45.0 (:fovy (cam tapped)))))
    (testing "from the original's own position that is the original's camera"
      (let [t (-> start (step :press [on]) (step :down [on]) (step :release [on]))]
        (is (= (cam start) (cam t)))))
    (testing "a press on the button starts no look and no stick"
      (let [pressed (step wandered :press [on])]
        (is (nil? (:look pressed)))
        (is (nil? (:stick pressed)))
        (is (= (cam wandered) (cam (step pressed :down [(v+ on [300.0 0.0])]))))))
    (testing "a tap elsewhere does not reset"
      (let [elsewhere (-> wandered
                          (step :press [look-pt])
                          (step :down [look-pt])
                          (step :release [look-pt]))]
        (is (= (:target (cam wandered)) (:target (cam elsewhere))))))
    (testing "a tap that ends a pinch does not reset"
      (let [pinched (-> wandered
                        (step :press [on [300.0 1500.0]])
                        (step :down [on [300.0 1500.0]])
                        (step :release [on]))]
        (is (not= [0.0 0.0 0.0] (:target (cam pinched))))))))

(deftest rcamera-matches-the-c
  (let [c {:position [1.0 2.0 3.0]
           :target [1.0 2.0 -1.0]
           :up [0.0 2.0 0.0]
           :fovy 45.0
           :projection :perspective}]
    (testing "GetCameraForward, Up and Right are unit vectors, right = forward x up"
      (is (vnear? [0.0 0.0 -1.0] (sc/camera-forward c)))
      (is (vnear? [0.0 1.0 0.0] (sc/camera-up c)))
      (is (vnear? [1.0 0.0 0.0] (sc/camera-right c))))
    (testing "a zero-length vector normalises to zero, as Vector3Normalize does"
      (is (vnear? [0.0 0.0 0.0] (sc/camera-forward (assoc c :target (:position c))))))
    (testing "Vector3RotateByAxisAngle: x about z by 90 degrees is y"
      (is (vnear? [0.0 1.0 0.0] (sc/rotate-by-axis-angle [1.0 0.0 0.0] [0.0 0.0 5.0] (/ Math/PI 2.0)))))
    (testing "CameraMoveForward and CameraMoveRight carry position and target together"
      (let [f (sc/camera-move-forward c 2.0)
            r (sc/camera-move-right c 3.0)]
        (is (= [1.0 2.0 1.0] (:position f)))
        (is (= [1.0 2.0 -3.0] (:target f)))
        (is (vnear? [4.0 2.0 3.0] (:position r)))
        (is (vnear? [4.0 2.0 -1.0] (:target r)))))
    (testing "CameraMoveToTarget: distance plus delta along the forward, never below 0.001"
      (is (= [1.0 2.0 1.0] (:position (sc/camera-move-to-target c -2.0))))
      (is (vnear? [1.0 2.0 7.0] (:position (sc/camera-move-to-target c 4.0))))
      (is (vnear? [1.0 2.0 -0.999] (:position (sc/camera-move-to-target c -100.0)))))
    (testing "CameraYaw about up, rotating the target about the position"
      (let [y (sc/camera-yaw c (/ Math/PI 2.0))]
        (is (= [1.0 2.0 3.0] (:position y)))
        (is (vnear? [-3.0 2.0 3.0] (:target y)))))
    (testing "CameraPitch about right, locked 0.001 short of vertical"
      (let [p (sc/camera-pitch c (/ Math/PI 4.0))]
        (is (vnear? [1.0 (+ 2.0 (* 4.0 (Math/sin (/ Math/PI 4.0)))) (+ 3.0 (* -4.0 (Math/cos (/ Math/PI 4.0))))]
                    (:target p))))
      (let [p (sc/camera-pitch c 3.0)
            v (unit (v- (:target p) (:position p)))]
        (is (near? (Math/cos 0.001) (v 1))))
      (let [p (sc/camera-pitch c -3.0)
            v (unit (v- (:target p) (:position p)))]
        (is (near? (- (Math/cos 0.001)) (v 1)))))))

(deftest buttons-avoid-back
  (doseq [screen screens
          :let [[w h] screen
                g (sc/geometry {:screen screen})
                [bx by bw bh] gesture/back-region
                [rx ry rw rh] (:reset g)
                [fx fy fw fh] (:viewport g)]]
    (testing (str screen)
      (is (>= ry (+ by bh)) "the button is below Back")
      (is (<= (+ ry rh) fy) "and above the field")
      (is (>= rx 0))
      (is (<= (+ rx rw) w))
      (is (not (and (< rx (+ bx bw)) (< bx (+ rx rw)) (< ry (+ by bh)) (< by (+ ry rh)))))
      (is (>= fy (+ by bh)))
      (is (= [0.0 (double w)] [fx fw]))
      (is (near? h (+ fy fh) 1e-6) "the field runs to the bottom")
      (is (< fy (:stick-top g) (+ fy fh)) "both regions exist")
      (is (near? (:stick-top g) (+ fy (* (/ 2.0 3.0) fh)) 1e-6)))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [_ back-y _ back-h] gesture/back-region
                [hx hy hw hh] (:hud dims)
                [fx fy fw fh] (:viewport dims)]]
    (testing (str screen)
      (is (= 5 (count (:lines dims))) "four HUD lines and the button label")
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= size 8) s)
        (is (>= x 0) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) h) s))
      (testing "the HUD box is in the field, no wider than 0.92 of it, and holds its lines"
        (is (>= hx fx))
        (is (>= hy fy))
        (is (<= (+ hx hw) (+ fx (* 0.92 fw) 1e-6)))
        (is (<= (+ hy hh) (+ fy fh)))
        (doseq [{:keys [s x y size]} (:hud-lines dims)]
          (is (>= x hx) s)
          (is (<= (+ x (measure s size)) (+ hx hw)) s)
          (is (>= y hy) s)
          (is (<= (+ y size) (+ hy hh)) s)))
      (testing "the HUD lines do not overlap one another"
        (let [ys (map :y (:hud-lines dims))]
          (is (every? (fn [[a b sz]] (<= (+ a sz) b))
                      (map vector ys (rest ys) (map :size (:hud-lines dims)))))))
      (testing "the label fits its button"
        (let [[_ _ rw rh] (:reset dims)
              {:keys [s size]} (:reset-label dims)]
          (is (<= (measure s size) rw))
          (is (<= size rh)))))))

(deftest a-rotation-drops-the-fingers
  (let [held (-> start (step :press [look-pt]) (step :down [look-pt]))
        turned (sc/advance held {:metrics {:screen [2334 1206]}
                                 :delta-seconds dt
                                 :pointer {:phase :down
                                           :position [900.0 400.0]}
                                 :touch-points [[900.0 400.0]]})]
    (is (some? (:look held)))
    (is (nil? (:look turned)))
    (is (= (cam held) (cam turned)))))

(deftest a-finger-on-reset-does-not-inherit-a-lifted-stick-or-look
  ;; The reviewer's probes: A steers or looks, B rests on the reset button
  ;; (ignored while two are down), then A lifts. B must not take over.
  (let [[rx ry rw rh] (:reset d)
        b [(+ rx (* 0.5 rw)) (+ ry (* 0.5 rh))]]
    (doseq [[label stp ids] [["with ids" step-ids [[4] [4 5] [5]]]
                             ["without ids" (fn [s ph pts _] (step s ph pts)) [nil nil nil]]]
            :let [[i1 i2 i3] ids]]
      (testing (str "the stick, " label)
        (let [held (-> start (stp :press [stick-pt] i1)
                       (stp :down [(at-px stick-pt 0.0 -200.0)] i1)
                       (stp :press [(at-px stick-pt 0.0 -200.0) b] i2))
              lifted (stp held :down [b] i3)
              later (nth (iterate #(stp % :down [b] i3) lifted) 6)]
          (is (some? (:stick held)))
          (is (nil? (:stick lifted)))
          (is (= (:position (cam lifted)) (:position (cam later))) "the camera stays put")))
      (testing (str "the look, " label)
        (let [held (-> start (stp :press [look-pt] i1)
                       (stp :down [look-pt] i1)
                       (stp :press [look-pt b] i2))
              lifted (stp held :down [b] i3)]
          (is (some? (:look held)))
          (is (nil? (:look lifted)))
          (is (= (:target (cam held)) (:target (cam lifted))) "the view does not snap"))))))
