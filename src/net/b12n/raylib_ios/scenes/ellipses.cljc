(ns net.b12n.raylib-ios.scenes.ellipses
  "Two ellipses, one of which follows your finger. Both turn red while they
  overlap. A tap on the other ellipse hands the steering to it. Ported from
  raylib-jlt's `ellipse_collision`, itself raylib's `shapes_ellipse_collision`,
  where the mouse steers one ellipse and A and B choose which. It differs from
  `collision`, which is two boxes and an exact intersection rectangle, and from
  `shapes`, whose ellipse is one static fan: here the same fan is reused for two
  moving ones.

  The overlap test is the original's, and it is an approximation. Two ellipses
  have no closed-form test the way two circles do, so `overlap?` walks 64 points
  round one rim and asks whether any lands inside the other, then does the same
  the other way round. The reverse pass catches one ellipse lying wholly inside
  the other. A lens too thin for any sample to land in is still missed, which
  the tests pin down with a measured case. Point-in-ellipse itself is exact: the
  offset divided by each radius, tested against the unit circle, with the rim
  counted as inside.

  The steered ellipse goes to the finger on `:press` and follows it on `:down`,
  and stays where it was on lift, since the `:release` frame carries a stale
  position. A swap is the `:tap` event from `net.b12n.raylib-ios.gesture/track`, judged at
  where the finger STARTED. A touch that starts inside the other ellipse is a
  candidate for a swap, so it never moves the steered one, on the press frame or
  after, and the swap happens when it lifts as a tap. Without that, the steered
  ellipse would leap to the finger during the tap and the swap would hand over
  an ellipse that had just landed on top of the one being picked. The cost is
  that the steered one cannot be dragged from inside the other, including from
  where they overlap. A touch that starts under Back, or that travels too far to
  be a tap, swaps nothing.

  The original's DrawEllipse is rebuilt as `shapes/ellipse-fan` and its outline,
  which is not bound, as `draw-line-ex` segments between the very same rim
  points (`outline`). One factor `f` scales both ellipses, so they keep the
  original's proportions of 120 by 70 and 90 by 140, and the centres are clamped
  to keep each whole ellipse and its stroke inside the screen, below the text
  rows and so below Back."
  (:require [net.b12n.raylib-ios.gesture :as gesture]
            [net.b12n.raylib-ios.scenes.shapes :as shapes]))

(def rim-samples "The original's 64 rim samples per ellipse." 64)

(def a-radii "The original's 120 by 70, before scaling." [120.0 70.0])
(def b-radii "The original's 90 by 140, before scaling." [90.0 140.0])

(def background-colour "The original's RAYWHITE." [245 245 245 255])
(def a-colour "The original's BLUE." [0 121 241 255])
(def b-colour "The original's GREEN." [0 228 48 255])
(def hit-colour "The original's RED." [230 41 55 255])
(def outline-colour "The original's WHITE." [255 255 255 255])
(def hint-colour "The original's DARKGRAY." [80 80 80 255])
(def apart-colour "The original's GRAY." [130 130 130 255])

(defn inside?
  "Whether `[px py]` is in the ellipse at `[cx cy]` with radii `[rx ry]`. Exact,
  and the rim counts as inside."
  [[px py] [cx cy] [rx ry]]
  (let [dx (/ (- (double px) cx) rx)
        dy (/ (- (double py) cy) ry)]
    (<= (+ (* dx dx) (* dy dy)) 1.0)))

(defn- rim-inside?
  "Whether any of the 64 points round the rim of the first ellipse is inside the
  second."
  [[cx cy] [rx ry] other other-r]
  (boolean
   (some (fn [i]
           (let [t (* 2.0 Math/PI (/ (double i) rim-samples))]
             (inside? [(+ cx (* rx (Math/cos t))) (+ cy (* ry (Math/sin t)))]
                      other other-r)))
         (range rim-samples))))

(defn overlap?
  "The original's approximate overlap of ellipses `[ca ra]` and `[cb rb]`: a rim
  sample of either inside the other."
  [ca ra cb rb]
  (or (rim-inside? ca ra cb rb)
      (rim-inside? cb rb ca ra)))

(defn fan
  "The ellipse at `[cx cy]` with radii `r` as `n` wedges, from `shapes`."
  [cx cy [rx ry] n]
  (shapes/ellipse-fan cx cy rx ry n))

(defn outline
  "The ellipse's stroke as `n` segments `[x1 y1 x2 y2]`, one along each wedge's
  rim edge, so the line and the fill share every rim point and the loop closes."
  [cx cy r n]
  (mapv (fn [[_ _ x2 y2 x3 y3]] [x3 y3 x2 y2]) (fan cx cy r n)))

(defn lines
  "The text rows as `[string colour]`, for the steered ellipse `steer` and
  whether the pair overlaps, `hit?`."
  [steer hit?]
  [["tap the other ellipse to steer it" hint-colour]
   [(if (= steer :a) "steering the blue one" "steering the green one") hint-colour]
   [(if hit? "OVERLAPPING" "apart") (if hit? hit-colour apart-colour)]])

(defn dimensions
  "The layout for `metrics`' `:screen`: `:w :h`, the scale `:f`, the stroke width
  `:thick`, `:top`, the y below which the ellipses live, the three text `:rows`
  as `{:x :y :size}`, and `:a` and `:b` as `{:r [rx ry] :n wedges}`. The rows
  start below `gesture/back-region` and the ellipses below the rows. `f` is the
  smaller of what fits the two widest ellipses across the screen and what fits
  the tallest one in the room under the rows."
  [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        ts (max 20 (int (* 0.028 side)))
        [_ back-y _ back-h] gesture/back-region
        row0 (+ back-y back-h (* 0.5 ts))
        step (* 1.3 ts)
        rows (mapv (fn [i] {:x (int (* 0.04 w))
                            :y (int (+ row0 (* i step)))
                            :size ts})
                   (range 3))
        margin (* 0.02 side)
        top (+ (:y (peek rows)) ts margin)
        f (min (/ w 300.0) (/ (- h margin top) 520.0))
        ra (mapv #(* f %) a-radii)
        rb (mapv #(* f %) b-radii)]
    {:w w
     :h h
     :f f
     :thick (max 2.0 (* 0.004 side))
     :top top
     :rows rows
     :a {:r ra
         :n (apply shapes/ellipse-segments ra)}
     :b {:r rb
         :n (apply shapes/ellipse-segments rb)}}))

(defn- clamp-centre
  "`[x y]` pulled in so the ellipse of radii `[rx ry]` and its stroke stay
  inside the screen and below `:top`."
  [{:keys [w h top thick]} [rx ry] [x y]]
  (let [half (* 0.5 thick)
        lim (fn [lo hi v] (max lo (min hi (double v))))]
    [(lim (+ rx half) (- w rx half) x)
     (lim (+ top ry half) (- h ry half) y)]))

(defn- start-centres
  "A above B, centred, a quarter and three quarters of the way down the room
  under the rows."
  [{:keys [w h top]
    :as dims}]
  (let [room (- h top)]
    {:a (clamp-centre dims (:r (:a dims)) [(* 0.5 w) (+ top (* 0.25 room))])
     :b (clamp-centre dims (:r (:b dims)) [(* 0.5 w) (+ top (* 0.75 room))])}))

(def other {:a :b
            :b :a})

(defn advance
  "One frame. The steered ellipse goes to the finger while it is down, unless the
  touch started under Back or inside the other ellipse. A tap that started
  inside the other one swaps which is steered."
  [state input]
  (let [dims (dimensions (:metrics input))
        [g' event] (gesture/track (:g state) input)
        steer (:steer state)
        away (other steer)
        start (:start g')
        position (get-in input [:pointer :position])
        picking? (and start (inside? start (get state away) (:r (get dims away))))
        follow? (and (gesture/down? input)
                     start
                     (not (gesture/in-back-region? start))
                     (not picking?))
        swap? (and (= :tap (:type event))
                   (not (gesture/in-back-region? (:at event)))
                   (inside? (:at event) (get state away) (:r (get dims away))))
        steered (if follow?
                  (mapv double position)
                  (get state steer))
        state' (assoc state steer steered)
        a (clamp-centre dims (:r (:a dims)) (:a state'))
        b (clamp-centre dims (:r (:b dims)) (:b state'))]
    (assoc state'
           :g g'
           :steer (if swap? away steer)
           :a a
           :b b
           :hit? (overlap? a (:r (:a dims)) b (:r (:b dims))))))

(defn- init [{:keys [metrics]}]
  (let [dims (dimensions metrics)
        {:keys [a b]} (start-centres dims)]
    [{:g gesture/idle
      :steer :a
      :a a
      :b b
      :hit? (overlap? a (:r (:a dims)) b (:r (:b dims)))}
     [[:scene/init :ellipses]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :ellipses]]])

(defn scene []
  {:id :ellipses
   :title "Ellipse Collision"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
