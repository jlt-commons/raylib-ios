(ns raylib.scenes.pong
  "Pong against a CPU, your paddle on a finger. Ported from raylib-jlt's `pong`.

  The original moves the left paddle with W and S and restarts on ENTER. Here
  the player's paddle follows the finger along the court's cross axis while one
  is on the glass (`:press` or `:down`), clamped to the court, and holds still
  otherwise. Nothing moves on `:release`, whose position is the last hardware
  value. After a win a `:tap` from `raylib.gesture` restarts the game, unless it
  lands in the Back region, which belongs to the host.

  The rules are the original's: the ball bounces off the two side walls, a
  paddle returns it with english of `0.08` per pixel from the paddle's centre,
  the ball never speeds up, the CPU chases the ball at `AISPEED`, a ball past a
  paddle scores for the other side, and the first to 7 wins.

  The court is turned for the screen. The game logic stays in the original's own
  frame, a LONG axis (the original's x, along which the ball travels) and a
  CROSS axis (its y, along which paddles slide), and `orient` is the only place
  that maps it onto pixels. In landscape the long axis is screen x and the
  player is on the left, as in the original. On a tall phone it is screen y, the
  player is at the bottom and the CPU at the top, so the finger's x is the cross
  coordinate. The court starts below `gesture/back-region` either way.

  The 800x450 layout is scaled per axis: everything along the long axis by
  `L / 800`, everything along the cross axis by `C / 450`, so the ball takes
  about as many frames to cross the court as the original's does. The
  ball's speed is not capped: the paddle test is an overlap of ranges wider than
  one frame's step, so nothing tunnels.

  This is frame-locked, like the original: `advance` moves the ball a fixed
  distance per call, so the game runs faster on a 120 Hz display than on a 60 Hz
  one. The Delta Time scene shows why that matters. It is kept here because the
  point of the port is the original.

  The original draws serves with `rl/get-random-value`. Here they come from the
  project's LCG, taking its high bits, so the namespace stays pure.

  Colours are `[r g b a]` vectors. The draw method packs them with `rl/rgba`."
  (:require [raylib.gesture :as gesture]))

(def win-score "First to this many points wins. The original's `WIN`." 7)

(def background-colour [10 18 18 255])
(def dash-colour [130 130 130 255])
(def paddle-colour [245 245 245 255])
(def ball-colour [255 203 0 255])
(def text-colour [245 245 245 255])
(def hint-colour [130 130 130 255])
(def win-colour [255 203 0 255])

(def player-wins-line "YOU WIN! - TAP")
(def cpu-wins-line "CPU WINS! - TAP")
(def you-line "DRAG")
(def cpu-line "CPU")

(defn- text-width
  "estimate: 0.6 of the size per character, for raylib's default font."
  [size line]
  (* 0.6 size (count line)))

(defn dimensions
  "The court and where everything sits, for `metrics`' `:screen`. `:long` and
  `:cross` are the court's two lengths in pixels, `:portrait?` says whether the
  long axis is screen y, and `:top` is where the court starts, at the bottom
  edge of `gesture/back-region`. `:sl` and `:sc` scale the original's long and
  cross measurements. `:aispeed` is the CPU's per-frame cross speed, scaled."
  [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        portrait? (> h w)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        play-h (- h top)
        [long-len cross-len] (if portrait? [play-h w] [w play-h])
        sl (/ long-len 800.0)
        sc (/ cross-len 450.0)
        pw (* 12 sl)
        left-x (* 30 sl)
        score-size (max 20 (int (* 0.05 side)))
        hint-size (max 18 (int (* 0.02 side)))
        msg-size (max 20 (int (* 0.045 side)))
        mid (+ top (* 0.5 play-h))]
    {:w w
     :h h
     :portrait? portrait?
     :top top
     :long long-len
     :cross cross-len
     :sl sl
     :sc sc
     :pw pw
     :ph (* 80 sc)
     :bs (* 12 (min sl sc))
     :left-x left-x
     :right-x (- long-len left-x pw)
     :aispeed (* 4.5 sc)
     :score-size score-size
     ;; Portrait: the CPU's score above the centre line and yours below it, on
     ;; the left. Landscape: the original's, either side of the centre line.
     :cpu-score-x (if portrait? (int (* 0.06 w)) (int (+ (* 0.5 w) (* 50 sl))))
     :cpu-score-y (if portrait? (int (- mid (* 1.3 score-size))) (int (+ top (* 30 sc))))
     :you-score-x (if portrait? (int (* 0.06 w)) (int (- (* 0.5 w) (* 80 sl))))
     :you-score-y (if portrait? (int (+ mid (* 0.3 score-size))) (int (+ top (* 30 sc))))
     :hint-size hint-size
     :you-hint-x (int (* 0.04 w))
     :you-hint-y (int (- h (* 1.5 hint-size)))
     :cpu-hint-x (int (- w (* 0.04 w) (text-width hint-size cpu-line)))
     :cpu-hint-y (if portrait? (int (+ top hint-size)) (int (- h (* 1.5 hint-size))))
     :msg-size msg-size
     :msg-y (int (- mid (* 0.5 msg-size)))}))

(defn msg-x
  "Where `line` starts so it is centred across the screen's width."
  [{:keys [w msg-size]} line]
  (max 0 (int (* 0.5 (- w (text-width msg-size line))))))

(defn orient
  "The screen rect `[x y w h]` of a box at long coordinate `l` and cross
  coordinate `c`, `el` long and `ec` cross in extent, all in court pixels. The
  court's long axis runs up the screen on a tall phone with coordinate 0 at the
  bottom, which puts the player's paddle there, and along the screen's width on
  a wide one with 0 at the left."
  [{:keys [portrait? top]
    :as dims} l c el ec]
  (if portrait?
    [c (+ top (- (:long dims) l el)) ec el]
    [l (+ top c) el ec]))

(defn paddle-rect
  "Screen rect of a paddle, `:player` or `:cpu`, whose cross position is `c`."
  [{:keys [left-x right-x pw ph]
    :as dims} side c]
  (orient dims (if (= side :player) left-x right-x) c pw ph))

(defn ball-rect
  "Screen rect of the ball. The ball can pass the CPU's end by up to its own
  size before it counts as a point, so its long extent is clipped to the court
  and nothing is drawn off the screen."
  [{:keys [bs]
    :as dims} {:keys [bx by]}]
  (orient dims bx by (max 0.0 (min bs (- (:long dims) bx))) bs))

(defn centre-dashes
  "Screen rects of the dashed centre line. The original draws 4x16 dashes every
  30 pixels down the middle."
  [{:keys [sl sc]
    :as dims}]
  (let [l (- (* 0.5 (:long dims)) (* 2 sl))]
    ;; Counted in whole dashes, not a float `range`, which can land a last dash
    ;; on the court's far edge by rounding.
    (for [i (range (quot 450 30))]
      (orient dims l (* i 30 sc) (* 4 sl) (* 16 sc)))))

(defn- fabs [n] (if (neg? n) (- n) n))

(defn- clamp [v lo hi] (max lo (min hi v)))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- rand-upto
  "`[n seed']` with n in 0..`hi` inclusive. The LCG's high bits, since its low
  bit alternates on every step."
  [seed hi]
  (let [seed' (next-random seed)]
    [(mod (quot seed' 65536) (inc hi)) seed']))

(defn- serve
  "A ball at the court's centre heading in `dir` (1 toward the CPU, -1 toward
  the player), and the next seed. The original's speeds are 4.0..6.0 along the
  long axis and -3.0..3.0 across, here scaled per axis."
  [{:keys [sl sc]
    :as dims} dir seed]
  (let [[a seed1] (rand-upto seed 20)
        [b seed2] (rand-upto seed1 200)]
    [{:bx (* 0.5 (:long dims))
      :by (* 0.5 (:cross dims))
      :bvx (* dir sl (+ 4.0 (/ a 10.0)))
      :bvy (* sc 3.0 (- (/ b 100.0) 1.0))}
     seed2]))

(def ^:private start-seed 20261001)

(defn- new-game [{:keys [w h ph cross]
                  :as dims} seed g]
  (let [[ball seed'] (serve dims 1 seed)]
    (merge {:screen [w h]
            :ly (* 0.5 (- cross ph))
            :ry (* 0.5 (- cross ph))
            :ls 0
            :rs 0
            :over? false
            :winner nil
            :seed seed'
            :gesture g}
           ball)))

(defn- step
  "One frame of the original's rules, with the player's paddle already placed."
  [{:keys [long cross bs pw ph left-x right-x aispeed]
    :as dims}
   {:keys [ly ry bx by bvx bvy ls rs seed]
    :as st}]
  (let [max-c (- cross ph)
        ;; The CPU chases the ball's top edge less half a paddle, at a capped speed.
        target (- by (/ ph 2.0))
        ry (clamp (+ ry (clamp (- target ry) (- aispeed) aispeed)) 0.0 max-c)
        bx0 (+ bx bvx)
        by0 (+ by bvy)
        [by bvy0] (cond (<= by0 0) [0.0 (- bvy)]
                        (>= (+ by0 bs) cross) [(double (- cross bs)) (- bvy)]
                        :else [by0 bvy])
        bcy (+ by (/ bs 2.0))
        hit-l? (and (< bvx 0) (<= bx0 (+ left-x pw)) (>= (+ bx0 bs) left-x)
                    (>= bcy ly) (<= bcy (+ ly ph)))
        hit-r? (and (> bvx 0) (>= (+ bx0 bs) right-x) (<= bx0 (+ right-x pw))
                    (>= bcy ry) (<= bcy (+ ry ph)))
        bx (cond hit-l? (double (+ left-x pw)) hit-r? (double (- right-x bs)) :else bx0)
        bvx (cond hit-l? (fabs bvx) hit-r? (- (fabs bvx)) :else bvx)
        bvy (cond hit-l? (+ bvy0 (* 0.08 (- bcy (+ ly (/ ph 2.0)))))
                  hit-r? (+ bvy0 (* 0.08 (- bcy (+ ry (/ ph 2.0)))))
                  :else bvy0)
        scored (cond (< bx 0) :cpu (> bx long) :player :else nil)
        ls (+ ls (if (= scored :player) 1 0))
        rs (+ rs (if (= scored :cpu) 1 0))
        [ball seed'] (if scored
                       (serve dims (if (= scored :player) 1 -1) seed)
                       [{:bx bx
                         :by by
                         :bvx bvx
                         :bvy bvy}
                        seed])]
    (merge st
           {:ry ry
            :ls ls
            :rs rs
            :seed seed'
            :over? (or (>= ls win-score) (>= rs win-score))
            :winner (cond (>= ls win-score) :player (>= rs win-score) :cpu :else nil)}
           ball)))

(defn advance
  "One frame. Calls `gesture/track` once and stores the result on every path. A
  rotation (the metrics report a different `:screen` than the state was laid
  out for) starts a new game, because every position is in the old court's
  pixels. Otherwise the player's paddle goes under the finger, and a finished
  game waits for a tap outside Back."
  [state input]
  (let [dims (dimensions (:metrics input))
        [g event] (gesture/track (:gesture state) input)
        point (get-in input [:pointer :position])]
    (if (not= [(:w dims) (:h dims)] (:screen state))
      (new-game dims (:seed state) g)
      (let [state (assoc state :gesture g)
            max-c (- (:cross dims) (:ph dims))
            ly (if (gesture/down? input)
                 (let [finger (if (:portrait? dims) (nth point 0) (- (nth point 1) (:top dims)))]
                   (double (clamp (- finger (/ (:ph dims) 2.0)) 0 max-c)))
                 (:ly state))
            state (assoc state :ly ly)]
        (cond
          (:over? state)
          (if (and (= :tap (:type event))
                   (not (gesture/in-back-region? (:at event))))
            (assoc (new-game dims (:seed state) g) :ly ly)
            state)

          :else (step dims state))))))

(defn- init [{:keys [metrics]}]
  [(new-game (dimensions metrics) start-seed gesture/idle)
   [[:scene/init :pong]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :pong]]])

(defn scene []
  {:id :pong
   :title "Pong"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
