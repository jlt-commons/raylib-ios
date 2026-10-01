(ns raylib.scenes.virtualpad
  "An on-screen D-pad and an A button that makes a square move and hop. Ported
  from raylib-jlt's `input_virtual_controls`.

  The original already is the control scheme a touch device needs, so the port
  keeps its hit testing: a press counts on the pad when it is outside the dead
  centre (22 of 82) and inside the ring, and the angle from the centre picks the
  quadrant, each direction owning the 90 degrees around its own axis. A is a
  plain circle test. The one change is the pointer. The original reads the
  mouse, so one control at a time. Here every point in the input's
  `:touch-points` is tested against the pad and against A, so a thumb on the pad
  and another on A both act, which the single `:pointer` could not say. The
  keyboard path (arrow keys, SPACE) is dropped, because a phone has none.

  The pad sits at the bottom left and A at the bottom right, both clear of
  `gesture/back-region`. The controls are circles, so they share one scale `c`
  taken from the shorter side. The square moves in the play field above them,
  where the original's 800 by 250 area is scaled per axis, `sx` across and `sy`
  down, so the square covers the same share of the field and its position range
  is the original's. Speed and the hop's lift use one factor `u`, the geometric
  mean of the two, because the square moves freely under a D-pad and a per-axis
  speed would make up several times faster than sideways on a tall phone.

  Motion is delta-time based, as in the original (`get-frame-time`): 190 units a
  second, and a hop that decays at 2.4 a second from 1. It reads the input's
  `:delta-seconds`, clamped at 0 the way the Delta Time scene does. Holding A
  keeps the hop at 1, where the sine arc is at rest, as the original does, and
  the square lifts and settles after the finger leaves. The original lets a hop
  rise past the top of its window, which would put the square under the title
  and Back here, so the drawn square is kept inside the field instead.

  A rotation starts the scene over, since the square's position is in the old
  screen's pixels. Colours are `[r g b a]` vectors, packed by the draw method
  with `rl/rgba`. Text widths are an estimate of 0.6 of the size per character."
  (:require [raylib.gesture :as gesture]))

(def background-colour [245 245 245 255])
(def title-colour [80 80 80 255])
(def hint-colour [130 130 130 255])
(def square-colour [190 33 55 255])
(def ring-colour [200 200 200 255])
(def segment-colour [0 0 0 25])
(def segment-active-colour [0 121 241 255])
(def arrow-colour [130 130 130 255])
(def arrow-active-colour [245 245 245 255])
(def a-colour [190 33 55 60])
(def a-active-colour [190 33 55 255])
(def a-text-colour [190 33 55 255])
(def a-text-active-colour [245 245 245 255])

(def title-line "virtual controls")
(def hint-line "touch the pad and A together")
(def jump-line "jump")

(def speed "The square's speed in the original's units a second." 190.0)
(def hop-decay "How much of the hop is lost each second once A is let go." 2.4)

(def ^:private segments
  ;; [direction dx dy centre-angle-degrees], as the original's.
  [[:up 0 -1 270] [:right 1 0 0] [:down 0 1 90] [:left -1 0 180]])

(defn- text-width
  "estimate: 0.6 of the size per character, for raylib's default font."
  [size line]
  (* 0.6 size (count line)))

(defn dimensions
  "The layout for `metrics`' `:screen`. Controls first: `:pad-x :pad-y :pad-r`
  and `:btn-x :btn-y :btn-r` are circles, `:segs` the pad's four small circles
  each with its arrow glyph's size and position, `:a-glyph` the A's, `:thick`
  the ring's width and `:c` the controls' scale. Then the play field `:ftop :fh`
  (the full width, below the text and above the pad), its per-axis scales
  `:sx :sy`, `:u` (their geometric mean, the one factor for speed and lift),
  `:half` the square's half side and `:lift` the hop's height.
  `:lines` lists the text drawn outside the controls, each as
  `{:s :x :y :size}`, so a test can check that it fits."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        side (min w h)
        margin (* 0.04 side)
        pad-r (* 0.18 side)
        c (/ pad-r 82.0)
        pad-x (+ margin pad-r)
        pad-y (- h margin pad-r)
        btn-r (* 46.0 c)
        btn-x (- w margin btn-r)
        gap (max 6.0 (* 0.015 side))
        title-size (max 20 (int (* 0.045 side)))
        hint-size (max 20 (int (* 0.03 side)))
        title-y (+ back-y back-h gap)
        hint-y (+ title-y title-size gap)
        ftop (+ hint-y hint-size gap)
        fh (- pad-y pad-r gap ftop)
        sx (/ w 800.0)
        sy (/ fh 250.0)
        u (Math/sqrt (* sx sy))
        glyph-size (max 20 (int (* 20 c)))
        jump-size (max 20 (int (* 14 c)))
        a-size (max 20 (int (* 26 c)))
        centred (fn [cx cy size line]
                  [(int (- cx (* 0.5 (text-width size line))))
                   (int (- cy (* 0.5 size)))])]
    {:w w
     :h h
     :c c
     :pad-x pad-x
     :pad-y pad-y
     :pad-r pad-r
     :btn-x btn-x
     :btn-y pad-y
     :btn-r btn-r
     :thick (max 2.0 (* 0.003 side))
     :segs (mapv (fn [[dir dx dy _]]
                   (let [cx (+ pad-x (* dx 44.0 c))
                         cy (+ pad-y (* dy 44.0 c))
                         glyph (case dir :up "^" :down "v" :left "<" :right ">")
                         [gx gy] (centred cx cy glyph-size glyph)]
                     {:dir dir
                      :cx cx
                      :cy cy
                      :r (* 26.0 c)
                      :glyph glyph
                      :gx gx
                      :gy gy
                      :gsize glyph-size}))
                 segments)
     :a-glyph (let [[x y] (centred btn-x pad-y a-size "A")]
                {:x x
                 :y y
                 :size a-size})
     :ftop ftop
     :fh fh
     :sx sx
     :sy sy
     :u u
     :half (* 22.0 (min sx sy))
     :lift (* 60.0 u)
     :lines [{:s title-line
              :x (int margin)
              :y (int title-y)
              :size title-size}
             {:s hint-line
              :x (int margin)
              :y (int hint-y)
              :size hint-size}
             {:s jump-line
              :x (int (- btn-x (* 0.5 (text-width jump-size jump-line))))
              :y (int (+ pad-y btn-r (* 10.0 c)))
              :size jump-size}]}))

(defn- pad-dir
  "Which quadrant of the pad `[x y]` is in, or nil. It counts inside the ring
  and outside the dead centre, then the angle picks the quadrant, as the
  original's `pressed-segment`."
  [{:keys [pad-x pad-y pad-r c]} [x y]]
  (let [dx (- (double x) pad-x)
        dy (- (double y) pad-y)
        d (Math/sqrt (+ (* dx dx) (* dy dy)))]
    (when (< (* 22.0 c) d pad-r)
      (let [deg (mod (+ 360.0 (* (Math/atan2 dy dx) (/ 180.0 Math/PI))) 360.0)]
        (first (for [[dir _ _ centre] segments
                     :when (< (abs (- 180.0 (abs (- 180.0 (abs (- deg centre)))))) 45.0)]
                 dir))))))

(defn- on-a?
  "Whether `[x y]` is inside the A button."
  [{:keys [btn-x btn-y btn-r]} [x y]]
  (let [dx (- (double x) btn-x)
        dy (- (double y) btn-y)]
    (< (+ (* dx dx) (* dy dy)) (* btn-r btn-r))))

(defn held
  "What the input's `:touch-points` press: `:dirs`, the set of pad directions
  any finger is in, and `:a?`, whether any finger is on A. Every point is
  tested, so a pad finger and an A finger both count, in either order."
  [dims input]
  (let [pts (:touch-points input)]
    {:dirs (into #{} (keep #(pad-dir dims %)) pts)
     :a? (boolean (some #(on-a? dims %) pts))}))

(defn square-rect
  "The square as `[x y side side]`. The hop lifts it by a sine arc over the
  hop's decay. The original lets that rise past the top of its window, so here
  the drawn square is held inside the play field."
  [{:keys [ftop fh half lift]} {:keys [px py hop]}]
  (let [cy (- py (* lift (Math/sin (* Math/PI hop))))
        cy (max (+ ftop half) (min (- (+ ftop fh) half) cy))]
    [(- px half) (- cy half) (* 2.0 half) (* 2.0 half)]))

(defn- clamp [lo hi v] (max lo (min hi v)))

(defn- fresh
  "A scene at rest for `dims`, the square at the original's (400, 160)."
  [{:keys [w h ftop sx sy half]} held]
  (assoc held
         :screen [w h]
         :px (* 400.0 sx)
         :py (+ ftop (clamp (max (* 30.0 sy) half) (min (* 230.0 sy) (- (* 250.0 sy) half))
                            (* 160.0 sy)))
         :hop 0.0))

(defn advance
  "One frame. A rotation (the metrics report a different `:screen` than the
  state was laid out for) starts over. Otherwise the held directions move the
  square `190 * dt * u` units, the same in every direction, kept inside the
  field, and A pins the hop at 1 while a finger is on it, after which it decays
  by 2.4 a second. `dt` is the input's `:delta-seconds`, clamped at 0."
  [state input]
  (let [dims (dimensions (:metrics input))
        {:keys [dirs a?]
         :as now} (held dims input)
        {:keys [sx sy u half ftop]} dims
        dt (max 0.0 (double (:delta-seconds input 0.0)))]
    (if (not= [(:w dims) (:h dims)] (:screen state))
      (fresh dims now)
      (let [vx (+ (if (dirs :left) -1 0) (if (dirs :right) 1 0))
            vy (+ (if (dirs :up) -1 0) (if (dirs :down) 1 0))
            step (* speed dt)
            y-lo (+ ftop (max (* 30.0 sy) half))
            y-hi (+ ftop (min (* 230.0 sy) (- (* 250.0 sy) half)))]
        (assoc state
               :dirs dirs
               :a? a?
               :px (clamp (* 30.0 sx) (* 770.0 sx) (+ (:px state) (* vx step u)))
               :py (clamp y-lo y-hi (+ (:py state) (* vy step u)))
               :hop (cond a? 1.0
                          (pos? (:hop state)) (max 0.0 (- (:hop state) (* hop-decay dt)))
                          :else 0.0))))))

(defn- init [{:keys [metrics]}]
  [(fresh (dimensions metrics) {:dirs #{}
                                :a? false})
   [[:scene/init :virtualpad]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :virtualpad]]])

(defn scene []
  {:id :virtualpad
   :title "Virtual Controls"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
