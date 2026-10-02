(ns raylib.scenes.fpmaze-test
  (:require [clojure.test :refer [deftest is testing]]
            [raylib.gesture :as gesture]
            [raylib.scenes.fpmaze :as sc]
            [raylib.soft3d :as s3]))

(def m {:screen [1206 2334]})
(def screens [[1206 2334] [2334 1206] [800 450] [450 800]])
(def start (first ((:init (sc/scene)) {:metrics m})))
(def slop (gesture/slop m))

;; Phone geometry as fpcamera's: the field starts at 192 and is 2142 tall. A
;; touch above 192 + 1428 = 1620 turns, from there down it walks.
(def look-pt [600.0 600.0])
(def stick-pt [600.0 2000.0])

(defn- measure
  "estimate: 0.6 of the size per character, for raylib's default font."
  [s size]
  (* 0.6 size (count s)))

(defn- near? [a b] (< (abs (double (- a b))) 1e-9))
(defn- vnear? [a b] (and (= (count a) (count b)) (every? true? (map near? a b))))
(defn- at-px [[x y] dx dy] [(+ x dx) (+ y dy)])

(defn- step
  ([state phase points] (step state phase points nil 1/60))
  ([state phase points ids] (step state phase points ids 1/60))
  ([state phase points ids dt]
   (sc/advance state (cond-> {:metrics m
                              :delta-seconds dt
                              :pointer {:phase phase
                                        :position (first points)}
                              :touch-points (vec points)}
                       ids (assoc :touches {:ids (vec ids)})))))

(defn- walked
  "The state after a stick pressed at `stick-pt` and dragged by `dx`, `dy`: one
  update past the dead zone, so one step of the walk."
  [state dx dy]
  (let [pressed (step state :press [stick-pt])]
    (step pressed :down [(at-px stick-pt dx dy)])))

(defn- pos [state] [(:px state) (:pz state)])

;; The original (first_person_maze.clj): CELL 4, RADIUS 0.9, start (1.5 CELL,
;; 1.5 CELL) = (6, 6), heading 0 looking +z, speed 5 * dt, heading turns 2.2 rad/s.
(def step-len (/ 5.0 60.0))
(def n-walls (count (filter #(= \# %) (apply str sc/maze))))

(defn- held
  "A stick pressed at `stick-pt` and then held `n` updates at offset `dx`, `dy`."
  [state n dx dy]
  (let [pressed (step state :press [stick-pt])]
    (nth (iterate #(step % :down [(at-px stick-pt dx dy)]) pressed) n)))

(deftest walls-block-as-the-original
  (testing "the maze is the original's 16 by 16, walled all round"
    (is (= 16 (count sc/maze)))
    (is (every? #(= 16 (count %)) sc/maze))
    (is (= "################" (first sc/maze) (last sc/maze)))
    (is (= 137 n-walls) "counted from first_person_maze.clj's strings"))
  (testing "wall? reads row cy, column cx, and takes anything off the map as a wall"
    (is (true? (sc/wall? 0 0)))
    (is (false? (sc/wall? 1 1)))
    (is (true? (sc/wall? 2 2)))
    (is (false? (sc/wall? 1 2)))
    (is (true? (sc/wall? -1 1)))
    (is (true? (sc/wall? 1 -1)))
    (is (true? (sc/wall? 16 1)))
    (is (true? (sc/wall? 1 16))))
  (testing "blocked? tests the four corners of a box of RADIUS 0.9, not the centre"
    (is (not (sc/blocked? 6.0 6.0)))
    (is (not (sc/blocked? 7.2 6.0)) "the box's corners at x 8.1 are in the open cell (2, 1)")
    (is (sc/blocked? 7.2 7.5) "the centre is open but a corner is in the wall cell (2, 2)")
    (is (sc/blocked? 4.8 6.0) "x - 0.9 = 3.9 is in the wall column 0")
    (is (not (sc/blocked? 4.9 6.0)))
    (is (sc/blocked? 6.0 4.8))
    (is (not (sc/blocked? 6.0 4.9))))
  (testing "slide resolves x and then z, each on its own, as the original"
    (is (= [6.0 4.9] (sc/slide 6.0 4.9 0.0 -0.1)) "straight into the wall: no move")
    (is (vnear? [6.5 4.9] (sc/slide 6.0 4.9 0.5 -0.1)) "diagonally: the free x is kept")
    (is (= [6.0 5.0] (sc/slide 6.0 5.0 0.0 -0.4)) "a blocked move stays put, it is not clamped to the wall")
    (is (vnear? [6.25 6.5] (sc/slide 6.0 6.0 0.25 0.5)) "open ground moves freely"))
  (testing "walking into the wall, held for 100 updates, stops at the radius"
    (let [north (assoc start :heading Math/PI)
          end (held north 100 0.0 -200.0)]
      (is (<= 4.9 (:pz end)))
      (is (< (:pz end) 5.0) "it did get up to the wall")
      (is (near? 6.0 (:px end))))))

(deftest the-stick-moves
  (testing "up the glass is W: 5 * dt along the heading, which is +z at heading 0"
    (let [pressed (step start :press [stick-pt])
          one (step pressed :down [(at-px stick-pt 0.0 -200.0)])]
      (is (= (pos start) (pos pressed)) "the press walks nowhere")
      (is (vnear? [6.0 (+ 6.0 step-len)] (pos one)))))
  (testing "the speed is 5 * dt: twice the frame time, twice the step"
    (let [pressed (step start :press [stick-pt])
          slow (step pressed :down [(at-px stick-pt 0.0 -200.0)] nil 1/30)]
      (is (vnear? [6.0 (+ 6.0 (* 2 step-len))] (pos slow)))))
  (testing "down is S"
    (is (vnear? [6.0 (- 6.0 step-len)] (pos (walked start 0.0 200.0)))))
  (testing "the heading steers it: at a quarter turn forward is +x, as (sin h, cos h)"
    (is (vnear? [(+ 6.0 step-len) 6.0]
                (pos (walked (assoc start :heading (/ Math/PI 2.0)) 0.0 -200.0)))))
  (testing "a stick pushed right strafes to the right of the glass"
    ;; The original's D adds +x at heading 0, but the camera looks down +z, where
    ;; +x is screen-left (measured by projection below), so the stick maps by
    ;; what it shows rather than by key name.
    (let [right (walked start 200.0 0.0)
          [px pz] (pos right)
          fit (fn [state] (sc/camera-of (:px state) (:pz state) (:heading state)))
          vp (s3/view-proj (fit start) [0.0 192.0 1206.0 2142.0])
          [sx0] (s3/project vp [(+ 6.0 1.0) 1.6 8.0])]
      (is (< sx0 603.0) "+x projects to the left half at heading 0")
      (is (vnear? [(- 6.0 step-len) 6.0] [px pz]))
      (is (vnear? [(+ 6.0 step-len) 6.0] (pos (walked start -200.0 0.0))))))
  (testing "a diagonal is the same speed, where the original's two keys add to 1.41 times"
    (let [[px pz] (pos (walked start 150.0 -150.0))
          k (/ step-len (Math/sqrt 2.0))]
      (is (near? step-len (Math/sqrt (+ (* (- px 6.0) (- px 6.0)) (* (- pz 6.0) (- pz 6.0))))))
      (is (vnear? [(- 6.0 k) (+ 6.0 k)] [px pz]))))
  (testing "inside the dead zone nothing moves"
    (is (= (pos start) (pos (walked start (* 0.5 slop) 0.0)))))
  (testing "the camera follows: eye 1.6 up, target one unit along the heading, fovy 68"
    (let [s (walked (assoc start :heading (/ Math/PI 2.0)) 0.0 -200.0)
          c (sc/camera-of (:px s) (:pz s) (:heading s))]
      (is (vnear? [(:px s) 1.6 (:pz s)] (:position c)))
      (is (vnear? [(+ (:px s) 1.0) 1.6 (:pz s)] (:target c)))
      (is (= 68.0 (:fovy c))))))

(deftest a-drag-turns
  (let [scale (/ 800.0 1206.0)
        pressed (step start :press [look-pt])
        right (step pressed :down [(at-px look-pt 100.0 0.0)])
        left (step pressed :down [(at-px look-pt -100.0 0.0)])
        up (step pressed :down [(at-px look-pt 0.0 -100.0)])]
    (testing "the press turns nowhere"
      (is (= 0.0 (:heading pressed))))
    (testing "a drag right is the RIGHT key: the heading falls, 0.004 rad a pixel at 800 wide"
      (is (near? (* -0.004 100.0 scale) (:heading right))))
    (testing "a drag left is the LEFT key: the heading rises by as much"
      (is (near? (* 0.004 100.0 scale) (:heading left))))
    (testing "the original has no pitch, so a vertical drag turns nothing"
      (is (= 0.0 (:heading up))))
    (testing "turning does not walk"
      (is (= (pos start) (pos right))))
    (testing "the turn is at the eye: the view direction is (sin h, cos h)"
      (let [c (sc/camera-of 6.0 6.0 (:heading left))]
        (is (vnear? [(Math/sin (:heading left)) 0.0 (Math/cos (:heading left))]
                    (mapv - (:target c) (:position c))))))))

(deftest a-resting-finger-never-steers
  (doseq [[label ids] [["with ids" [[4] [4 5] [5] [5]]] ["without ids" [nil nil nil nil]]]
          :let [[i1 i2 i3 i4] ids
                s (fn [st ph pts i] (step st ph pts i 1/60))]]
    (testing (str "the stick, " label)
      (let [at (at-px stick-pt 0.0 -200.0)
            other (at-px stick-pt 400.0 0.0)
            held (-> start (s :press [stick-pt] i1) (s :down [at] i1) (s :press [at other] i2))
            lifted (s held :down [other] i3)
            later (nth (iterate #(s % :down [other] i4) lifted) 6)]
        (is (some? (:stick held)))
        (is (nil? (:stick lifted)) "the second finger does not inherit it")
        (is (= (pos lifted) (pos later)) "nothing moves")))
    (testing (str "a finger under Back never starts anything, " label)
      (let [st (-> start (s :press [[100.0 60.0]] i1) (s :down [[100.0 400.0]] i1))]
        (is (nil? (:look st)))
        (is (nil? (:stick st)))
        (is (= (pos start) (pos st)))
        (is (= 0.0 (:heading st))))))
  (testing "a tap walks and turns nowhere"
    (let [st (-> start (step :press [stick-pt]) (step :release [stick-pt]))]
      (is (= (pos start) (pos st)))
      (is (= 0.0 (:heading st)))))
  (testing "both thumbs work at once"
    (let [fwd (at-px stick-pt 0.0 -200.0)
          s3' (-> start (step :press [stick-pt] [4]) (step :down [fwd] [4])
                  (step :press [fwd look-pt] [4 5]))
          s4 (step s3' :down [fwd (at-px look-pt 100.0 0.0)] [4 5])]
      (is (some? (:look s3')))
      (is (not= (:heading s3') (:heading s4)) "the look finger turned")
      (is (not= (pos s3') (pos s4)) "while the stick finger walked"))))

(deftest the-minimap-marks-the-player
  (let [dims (sc/dimensions m measure)
        items (sc/minimap dims start)
        {:keys [x y w h]} (:map dims)
        s (:cell-px dims)
        k (/ s 9.0)
        rects (filterv #(= :rect (first %)) items)
        circle (first (filterv #(= :circle (first %)) items))
        line (first (filterv #(= :line (first %)) items))
        ox (+ x (* 4 k))
        oy (+ y (* 4 k))]
    (testing "a translucent black panel, then one rect per wall"
      (is (= (inc n-walls) (count rects)))
      (is (= [:rect x y w h [0 0 0 170]] (first rects)))
      (is (every? #(= [150 160 190 255] (nth % 5)) (rest rects))))
    (testing "the walls sit on the original's grid of s, a rect s - k wide"
      (let [r (second rects)]
        (is (near? ox (nth r 1)))
        (is (near? oy (nth r 2)))
        (is (near? (- s k) (nth r 3)))))
    (testing "exactly one circle and one line"
      (is (some? circle))
      (is (some? line))
      (is (= 1 (count (filterv #(= :circle (first %)) items))))
      (is (= 1 (count (filterv #(= :line (first %)) items)))))
    (testing "the circle is RED at the player's cell: ox + s * px / CELL, radius 4 scaled"
      (is (= (long (+ ox (* s 1.5))) (nth circle 1)))
      (is (= (long (+ oy (* s 1.5))) (nth circle 2)))
      (is (near? (* 4 k) (nth circle 3)))
      (is (= [230 41 55 255] (nth circle 4))))
    (testing "the line is GOLD, from the circle to the unrounded position plus 12 scaled along (sin h, cos h)"
      (is (= [(nth circle 1) (nth circle 2)] [(nth line 1) (nth line 2)]))
      (is (= (long (+ ox (* s 1.5))) (nth line 3)) "heading 0 points down the map, +z")
      (is (= (long (+ oy (* s 1.5) (* 12 k))) (nth line 4)))
      (is (= [255 203 0 255] (nth line 5))))
    (testing "the marker moves with the player and the line turns with the heading"
      (let [moved (assoc start :px 10.0 :pz 22.0 :heading (/ Math/PI 2.0))
            its (sc/minimap dims moved)
            c (first (filterv #(= :circle (first %)) its))
            l (first (filterv #(= :line (first %)) its))]
        (is (= (long (+ ox (* s 2.5))) (nth c 1)))
        (is (= (long (+ oy (* s 5.5))) (nth c 2)))
        (is (= (long (+ ox (* s 2.5) (* 12 k))) (nth l 3)) "a quarter turn points along +x")
        (is (= (long (+ oy (* s 5.5))) (nth l 4)))))))

(deftest first-frame-draws
  (let [dims (sc/dimensions m measure)
        dl (sc/scene-list start dims)
        tris (filterv #(= :tri (nth % 0)) dl)
        lines (filterv #(= :line (nth % 0)) dl)
        [vx vy vw vh] (:viewport dims)
        inside? (fn [[x y]] (and (<= (- vx 1e-6) x (+ vx vw 1e-6)) (<= (- vy 1e-6) y (+ vy vh 1e-6))))
        colours (set (map #(subvec % 7 10) tris))]
    (testing "the camera is the original's: eye (6, 1.6, 6) looking along +z, fovy 68"
      (let [c (sc/camera-of 6.0 6.0 0.0)]
        (is (= [6.0 1.6 6.0] (:position c)))
        (is (= [6.0 1.6 7.0] (:target c)))
        (is (= 68.0 (:fovy c)))))
    (testing "walls and the grid are on the glass"
      (is (pos? (count tris)))
      (is (pos? (count lines))))
    (testing "the walls are the original's two checker tints, shaded face by face as cube! does"
      (let [valid (set (for [tint [[120 130 160] [95 105 135]]
                             f [1.0 0.5 0.7 0.85 0.4]]
                         (mapv #(int (* f %)) tint)))]
        (is (seq colours))
        (is (every? valid colours))
        (is (>= (count colours) 2) "at least two faces or tints are showing"))
      (is (every? #(= 255 (nth % 10)) tris)))
    (testing "every projected coordinate is finite and some of it is on the glass"
      (is (every? (fn [[_ & more]] (every? #(and (number? %) (< (abs (double %)) 1e5)) (take 6 more))) tris))
      (is (some (fn [[_ & more]] (some inside? (partition 2 (take 6 more)))) tris)))
    (testing "the minimap is wholly inside the field, clear of the caption"
      (let [pts (mapcat (fn [it] (case (first it)
                                   :rect (let [[_ x y w h] it] [[x y] [(+ x w) (+ y h)]])
                                   :circle (let [[_ x y r] it] [[(- x r) (- y r)] [(+ x r) (+ y r)]])
                                   :line [[(nth it 1) (nth it 2)] [(nth it 3) (nth it 4)]]))
                        (sc/minimap dims start))]
        (is (seq pts))
        (is (every? inside? pts))))))

(deftest culling-leaves-the-picture-alone
  (let [dims (sc/dimensions m measure)
        [vx vy vw vh] (:viewport dims)
        on-glass? (fn [it]
                    (let [xs (map #(nth it %) [1 3 5])
                          ys (map #(nth it %) [2 4 6])]
                      (and (<= (apply min xs) (+ vx vw)) (>= (apply max xs) vx)
                           (<= (apply min ys) (+ vy vh)) (>= (apply max ys) vy))))
        uncut (fn [state]
                (let [vp (s3/view-proj (sc/camera state dims) (:viewport dims))]
                  (s3/finish
                   (reduce (fn [dl {:keys [x z colour]}]
                             (s3/cube dl vp nil [x 1.5 z] [4.0 3.0 4.0] colour))
                           (s3/grid [] vp 40 4.0)
                           sc/walls))))
        glass (fn [dl] (set (filter #(and (= :tri (first %)) (on-glass? %)) dl)))
        poses (for [[cx cy] [[1 1] [1 8] [7 5] [13 13] [14 1] [4 11]]
                    k (range 8)]
                (assoc start :px (* (+ cx 0.5) 4.0) :pz (* (+ cy 0.5) 4.0) :heading (* k (/ Math/PI 4))))]
    (testing "the walls it keeps are fewer than all of them from the start"
      (is (< (count (sc/visible-walls start dims)) (count sc/walls)))
      (is (pos? (count (sc/visible-walls start dims)))))
    (testing "every triangle that reaches the glass is still there, from 48 poses"
      (doseq [p poses]
        (is (= (glass (uncut p)) (glass (sc/scene-list p dims)))
            (str (pos p) " " (:heading p)))))
    (testing "turned the other way the player sees different walls"
      (is (not= (set (sc/visible-walls start dims))
                (set (sc/visible-walls (assoc start :heading Math/PI) dims)))))))

(deftest text-lines-fit-the-safe-region
  (doseq [screen screens
          :let [[w h] screen
                dims (sc/dimensions {:screen screen} measure)
                [fx fy fw fh] (:viewport dims)
                {mx :x
                 my :y
                 mw :w
                 mh :h} (:map dims)
                [_ back-y _ back-h] gesture/back-region]]
    (testing (str screen)
      (is (= 1 (count (:lines dims))))
      (doseq [{:keys [s x y size]} (:lines dims)]
        (is (>= size 8) s)
        (is (>= x 0) s)
        (is (<= (measure s size) (* 0.92 w)) s)
        (is (<= (+ x (measure s size)) w) s)
        (is (>= y (+ back-y back-h)) s)
        (is (<= (+ y size) fy) s)
        (is (<= (+ y size) h) s))
      (testing "the minimap panel fits inside the field"
        (is (>= mx fx))
        (is (>= my fy))
        (is (<= (+ mx mw) (+ fx fw)))
        (is (<= (+ my mh) (+ fy fh)))
        (is (<= mw (* 0.35 fw)))
        (is (<= mh (* 0.45 fh)))))))
