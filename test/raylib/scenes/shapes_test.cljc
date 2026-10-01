(ns raylib.scenes.shapes-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.shapes :as sh]))

(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def back-bottom (let [[_ y _ h] gesture/back-region] (+ y h)))

(defn estimate
  "estimate: 0.6 of the size per character, as in the other scenes' tests."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (- (double a) (double b))) 1e-6))

(defn- rim
  "The rim vertices of `wedges`, as each wedge sees them: its second and third
  points."
  [wedges]
  (mapcat (fn [[_ _ x2 y2 x3 y3]] [[x2 y2] [x3 y3]]) wedges))

(defn- cross [[x1 y1 x2 y2 x3 y3]]
  (- (* (- x2 x1) (- y3 y1))
     (* (- y2 y1) (- x3 x1))))

(deftest the-ellipse-fan-covers-the-ellipse
  (doseq [[cx cy rx ry n] [[690.0 125.0 60.0 40.0 24] [100.0 300.0 40.0 60.0 7]
                           [0.0 0.0 5.0 5.0 3] [50.0 50.0 200.0 30.0 90]]
          :let [wedges (sh/ellipse-fan cx cy rx ry n)]]
    (testing (str rx "x" ry " in " n)
      (is (= n (count wedges)))
      (testing "every rim vertex lies on the ellipse"
        (doseq [[x y] (rim wedges)]
          (is (near? 1.0 (+ (Math/pow (/ (- x cx) rx) 2) (Math/pow (/ (- y cy) ry) 2))))))
      (testing "every wedge starts at the centre"
        (is (every? (fn [[x y]] (and (near? x cx) (near? y cy))) (map (juxt first second) wedges))))
      (testing "consecutive wedges share an edge, and the last closes on the first"
        (doseq [i (range n)
                :let [[_ _ ax ay _ _] (nth wedges i)
                      [_ _ _ _ bx by] (nth wedges (mod (inc i) n))]]
          (is (and (near? ax bx) (near? ay by)) (str "wedge " i))))
      (testing "the wedges cover 360 degrees, none overlapping"
        (let [angle (fn [[x1 y1 x2 y2 x3 y3]]
                      (let [ux (- x2 x1) uy (- y2 y1)
                            vx (- x3 x1) vy (- y3 y1)]
                        (Math/atan2 (- (* ux vy) (* uy vx)) (+ (* ux vx) (* uy vy)))))
              as (map angle wedges)]
          (is (near? (* 2.0 Math/PI) (abs (reduce + as))))
          (is (or (every? pos? as) (every? neg? as)) "all turn the same way"))))))

(deftest segments-scale-with-the-radius
  (is (= 12 (sh/ellipse-segments 0.4 0.2)) "tiny: the floor")
  (is (<= (sh/ellipse-segments 10 5) (sh/ellipse-segments 100 5) (sh/ellipse-segments 400 5)))
  (is (= (sh/ellipse-segments 60 40) (sh/ellipse-segments 40 60)) "the larger radius decides")
  (is (= 180 (sh/ellipse-segments 100000 5)) "and there is a ceiling")
  (testing "the chord stays within half a pixel of the curve"
    (doseq [r [20.0 60.0 180.0 600.0]
            :let [n (sh/ellipse-segments r r)
                  sagitta (* r (- 1.0 (Math/cos (/ Math/PI n))))]]
      (is (<= sagitta 0.5) (str r)))))

(deftest fan-triangles-survive-culling
  (testing "every wedge is negative-cross natively, so it survives even without draw-triangle's fix"
    (doseq [[rx ry n] [[60.0 40.0 24] [40.0 60.0 7] [5.0 5.0 3] [200.0 30.0 90]]
            w (sh/ellipse-fan 100.0 100.0 rx ry n)]
      (is (neg? (cross w)) (str rx " " ry " " n " " w))))
  (testing "the scene's own fans, at every size"
    (doseq [screen screens
            :let [{:keys [ellipse]} (sh/dimensions {:screen screen})
                  [cx cy rx ry n] ellipse]
            w (sh/ellipse-fan cx cy rx ry n)]
      (is (neg? (cross w)) (str screen)))))

(defn- bounds-of [pts]
  [(apply min (map first pts)) (apply min (map second pts))
   (apply max (map first pts)) (apply max (map second pts))])

(deftest every-shape-fits-below-back
  (doseq [screen screens
          :let [[w h] screen
                {:keys [rect rect-outline circle circle-ring ellipse line triangle thick]}
                (sh/dimensions {:screen screen})
                [rx ry rw rh] rect
                [ox oy ow oh] rect-outline
                [ccx ccy cr] circle
                [rcx rcy rr] circle-ring
                [ecx ecy erx ery en] ellipse
                [lx1 ly1 lx2 ly2] line
                half (* 0.5 thick)
                boxes {:rect [rx ry (+ rx rw) (+ ry rh)]
                       :rect-outline [ox oy (+ ox ow) (+ oy oh)]
                       :circle [(- ccx cr) (- ccy cr) (+ ccx cr) (+ ccy cr)]
                       :circle-ring [(- rcx rr) (- rcy rr) (+ rcx rr) (+ rcy rr)]
                       :ellipse (bounds-of (rim (sh/ellipse-fan ecx ecy erx ery en)))
                       :line [(min lx1 lx2) (- (min ly1 ly2) half)
                              (max lx1 lx2) (+ (max ly1 ly2) half)]
                       :triangle (bounds-of (partition 2 triangle))}]]
    (testing (str screen)
      (is (pos? thick))
      (doseq [[nm [x0 y0 x1 y1]] boxes]
        (is (>= x0 0.0) (str nm))
        (is (>= y0 back-bottom) (str nm))
        (is (<= x1 w) (str nm))
        (is (<= y1 h) (str nm))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                {:keys [title]} (sh/dimensions {:screen screen})
                {:keys [s x y size]} title]]
    (testing (str screen)
      (is (= "scalar shape primitives" s))
      (is (>= y back-bottom) "below Back")
      (is (>= x 0))
      (is (<= (+ x (estimate s size)) w))
      (is (<= (+ y size) h)))))
