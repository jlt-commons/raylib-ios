(ns net.b12n.raylib-ios.scenes.starfield
  "Stars flying at the viewer, dragged faster or slower. Ported from raylib-jlt's
  `starfield_effect`, which is raylib's `shapes_starfield_effect` example. It is
  a different scene from `stars`, which is a twinkle: those stars drift and
  pulse in place, while these fly toward the camera under a perspective
  projection and respawn at the far plane.

  The original changes speed on the MOUSE WHEEL and toggles streaks against
  circles on SPACE. Here a vertical DRAG sets the speed and a `:tap` from
  `net.b12n.raylib-ios.gesture` toggles the mode, unless it lands in the Back region, which
  belongs to the host. A drag that starts in Back starts no speed change.

  The drag keeps its own anchor, because the gesture layer only reports a swipe
  at the release. On `:press` the scene records the finger's y and the current
  speed. While the finger is down the speed is the anchored one plus `k` times
  the distance dragged up, clamped to the original's 0.1 to 2.0. `k` is the
  range divided by the safe region's height, so a drag across the full height
  spans the whole range, the way a wheel's several notches would. Only the raw
  `:pointer` on `:press` and `:down` is read, never the `:release`. The same
  finger then reports a `:swipe` on release (it is a vertical drag), which is
  IGNORED, because nothing here reads swipes: the speed only changes while the
  finger is down, so the release cannot apply the drag a second time. A tap
  travels at most `gesture/slop`, which changes the speed by well under 5 % of
  the range in either orientation (a test pins that), so a tap that toggles the
  mode barely touches the speed.

  Motion is delta-time based, as in the original (`get-frame-time`): `z` falls
  by `speed * dt` a second from 1. It reads the input's `:delta-seconds`,
  clamped at 0 the way the Delta Time scene does. The original's frame time is
  unbounded above and so is this one.

  A star is stored in the original's normalized space: `nx` and `ny` are
  fractions of the width and height in [-0.5, 0.5), the original's `x` over `W`
  and `y` over `H`. Projection is `centre + n * size / z`, taken from the
  current `:screen` every frame, so it matches the original at 800 by 450. A
  rotation therefore needs no reset: the same stars simply project onto the new
  screen. A star respawns, at z 1, when z goes negative or its projection leaves
  the screen, as in the original. The original draws a circle of radius
  `1 + 4 z` and a streak from the projection at `z + 1/32` (at least 0.01 and at
  most 1), both one pixel at 450 high, and here both scale by the shorter side
  over 450 so they are visible on a phone.

  Random numbers come from the project's LCG, taking its HIGH bits (the low bit
  alternates on every step). The seed is fixed, so a field replays. Text is
  laid out in `dimensions`, below Back, with widths estimated at 0.6 of the size
  per character."
  (:require [net.b12n.raylib-ios.gesture :as gesture]))

(def star-count "The original's." 350)
(def min-speed "The original's lower clamp." 0.1)
(def max-speed "The original's upper clamp." 2.0)
(def start-speed "The original's 10/9." (/ 10.0 9.0))
(def ^:private start-seed 19840615)

(def background-colour [0 0 0 255])
(def star-colour [245 245 245 255])

(defn speed-line
  "The original's `Speed: n` readout, where n runs 0 to 9."
  [speed]
  (str "[DRAG] Speed: " (int (* 9.0 (/ speed max-speed)))))

(defn mode-line
  "The original's mode readout, with a tap for SPACE."
  [streaks?]
  (str "[TAP] Mode: " (if streaks? "Lines" "Circles")))

(defn fps-line [fps] (str fps " fps"))

(defn dimensions
  "The layout for `metrics`' `:screen`: `:w :h`, the projection centre `:cx :cy`,
  the scale `:u` of the original's pixels (shorter side over 450), `:k` (speed
  per pixel dragged) and the text. `:lines` lists the three lines, the widest
  each can get, as `{:s :x :y :size}` so a test can check they fit. The first
  sits below `gesture/back-region`."
  [metrics]
  (let [[w h] (:screen metrics)
        side (min w h)
        ts (max 20 (int (* 0.03 side)))
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h (* 0.5 ts))
        x (int (* 0.04 w))
        row (fn [i s] {:s s
                       :x x
                       :y (int (+ top (* i 1.4 ts)))
                       :size ts})]
    {:w w
     :h h
     :cx (* 0.5 w)
     :cy (* 0.5 h)
     :u (/ side 450.0)
     :k (/ (- max-speed min-speed) h)
     :text-size ts
     :lines [(row 0 (speed-line max-speed))
             (row 1 (apply max-key count (map mode-line [true false])))
             (row 2 (fps-line 999))]}))

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- roll
  "`[v seed']`: an int in [0, n) from the LCG's high bits."
  [seed n]
  (let [seed' (next-random seed)]
    [(mod (quot seed' 65536) n) seed']))

(defn- new-star
  "`[star seed']` at the far plane, z 1, anywhere in the original's spread."
  [seed]
  (let [[a s1] (roll seed 1000)
        [b s2] (roll s1 1000)]
    [{:nx (- (/ a 1000.0) 0.5)
      :ny (- (/ b 1000.0) 0.5)
      :z 1.0}
     s2]))

(defn project
  "`[px py]` for normalized `[nx ny]` at depth `z`."
  [{:keys [w h cx cy]} nx ny z]
  [(+ cx (/ (* nx w) z))
   (+ cy (/ (* ny h) z))])

(defn star-shape
  "What to draw for `star`: `:x :y` its projection, `:r` its circle radius and
  `:tx :ty` the streak's start, projected from a slightly deeper z."
  [{:keys [u]
    :as dims} {:keys [nx ny z]}]
  (let [[x y] (project dims nx ny z)
        t (max 0.01 (min 1.0 (+ z (/ 1.0 32.0))))
        [tx ty] (project dims nx ny t)]
    {:x x
     :y y
     :r (* u (+ 1.0 (* 4.0 z)))
     :tx tx
     :ty ty}))

(defn- offscreen? [{:keys [w h]} [px py]]
  (or (< px 0.0) (< py 0.0) (> px w) (> py h)))

(defn- move
  "`[stars seed']` after every star flies `dt * speed` closer, a star that has
  passed the camera or left the screen being replaced by a fresh one."
  [dims stars seed dt speed]
  (reduce (fn [[out sd] s]
            (let [z (- (:z s) (* dt speed))]
              (if (or (< z 0.0) (offscreen? dims (project dims (:nx s) (:ny s) z)))
                (let [[fresh sd'] (new-star sd)] [(conj out fresh) sd'])
                [(conj out (assoc s :z z)) sd])))
          [[] seed]
          stars))

(defn- clamp [lo hi v] (max lo (min hi v)))

(defn- anchor
  "The drag anchor for this frame: a fresh one on a `:press` outside Back, the
  existing one while the finger stays down, and none otherwise."
  [{:keys [drag speed]} {:keys [phase position]}]
  (case phase
    :press (when (and position (not (gesture/in-back-region? position)))
             {:y (double (second position))
              :speed speed})
    :down drag
    nil))

(defn advance
  "One frame. Calls `gesture/track` once and stores the result on every path. A
  drag sets the speed while the finger is down, a tap outside Back toggles
  streaks, and the stars fly for `:delta-seconds`, clamped at 0. A swipe event
  is never read."
  [state input]
  (let [dims (dimensions (:metrics input))
        [g event] (gesture/track (:gesture state) input)
        drag (anchor state (:pointer input))
        speed (if (and drag (gesture/down? input))
                (clamp min-speed max-speed
                       (+ (:speed drag)
                          (* (:k dims) (- (:y drag)
                                          (double (second (get-in input [:pointer :position])))))))
                (:speed state))
        streaks? (if (and (= :tap (:type event))
                          (not (gesture/in-back-region? (:at event))))
                   (not (:streaks? state))
                   (:streaks? state))
        dt (max 0.0 (double (:delta-seconds input 0.0)))
        [stars seed] (move dims (:stars state) (:seed state) dt speed)]
    (assoc state
           :gesture g
           :drag drag
           :speed speed
           :streaks? streaks?
           :stars stars
           :seed seed)))

(defn- init [_]
  (let [[stars seed] (reduce (fn [[out sd] _]
                               (let [[s sd'] (new-star sd)] [(conj out s) sd']))
                             [[] start-seed]
                             (range star-count))]
    [{:stars stars
      :seed seed
      :speed start-speed
      :streaks? true
      :gesture gesture/idle
      :drag nil}
     [[:scene/init :starfield]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :starfield]]])

(defn scene []
  {:id :starfield
   :title "Starfield Effect"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
