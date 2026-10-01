(ns raylib.scenes.invaders
  "Space Invaders on one finger. Ported from raylib-jlt's `space_invaders`.

  The original moves the ship with the left and right arrows and fires on a
  SPACE press. Here the ship's centre follows the finger's x while one is on the
  glass (`:press` or `:down`), clamped to the screen, and holds still otherwise.
  Nothing moves on `:release`, whose position is the last hardware value. Firing
  is automatic while a finger is down. After game over or a win a `:tap` from
  `raylib.gesture` restarts, unless it lands in the Back region, which belongs
  to the host.

  The rules are the original's. Its fire rule is a 15-frame cooldown on a SPACE
  press, not one bullet at a time, so a player mashing the key gets a shot every
  16 frames and several bullets fly at once. A held finger fires at that same
  cadence. An 8 by 4 formation marches sideways 1.2 px a frame, and on touching
  a wall margin of 6 it reverses and drops 18 px without moving sideways that
  frame. A bullet is a point tested against each alien's rectangle, a hit removes
  the alien and scores 10, and the game is won when none are left and lost when
  the lowest alien's bottom reaches the ship's row.

  The 800x450 layout is scaled into the play area below `gesture/back-region`:
  horizontal measures by `w / 800` and vertical ones by the play area's height
  over 450, so the formation takes as many frames to cross, and as many drops to
  reach the ship, as the original's does. Alien and ship sizes keep their
  aspect and scale by width alone, so on a tall phone the rows are further
  apart than the aliens are tall. The bullet's speed is capped at half an
  alien's height, so the point test cannot skip over one.

  This is frame-locked, like the original: `advance` moves everything a fixed
  distance per call, so the game runs faster on a 120 Hz display than on a 60 Hz
  one. The Delta Time scene shows why that matters. It is kept here because the
  point of the port is the original.

  Colours are `[r g b a]` vectors. The draw method packs them with `rl/rgba`."
  (:require [raylib.gesture :as gesture]))

(def acols "Alien columns, the original's." 8)
(def arows "Alien rows, the original's." 4)
(def cooldown-frames "Frames after a shot before the next one. The original's." 15)

(def background-colour [0 0 0 255])
(def alien-colour [0 158 47 255])
(def bullet-colour [255 203 0 255])
(def ship-colour [102 191 255 255])
(def text-colour [245 245 245 255])
(def over-colour [230 41 55 255])
(def win-colour [0 228 48 255])

(def over-line "GAME OVER - TAP")
(def win-line "YOU WIN! - TAP")

(defn score-line [n] (str "score " n))

(defn- text-width
  "estimate: 0.6 of the size per character, for raylib's default font."
  [size line]
  (* 0.6 size (count line)))

(defn dimensions
  "The 800x450 layout for `metrics`' `:screen`. `:sx` scales the original's
  horizontal measures, `:sy` its vertical ones into the play area that starts at
  `:top`, the bottom edge of `gesture/back-region`. `:speed` is the formation's
  per-frame march, `:drop` its step down, `:bullet-speed` the bullet's per-frame
  rise (capped at half an alien's height) and `:ship-y` the ship's top."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        sx (/ w 800.0)
        sy (/ (- h top) 450.0)
        alien-h (* 26 sx)]
    {:w w
     :h h
     :top top
     :sx sx
     :sy sy
     :sp (* 60 sx)
     :row-sp (* 45 sy)
     :alien-w (* 40 sx)
     :alien-h alien-h
     :ship-w (* 50 sx)
     :ship-h (* 16 sx)
     :ship-y (+ top (* 420 sy))
     :ax0 (* 40 sx)
     :ay0 (+ top (* 40 sy))
     :speed (* 1.2 sx)
     :drop (* 18 sy)
     :margin (* 6 sx)
     :bullet-speed (min (* 8 sy) (* 0.5 alien-h))
     :bullet-w (* 4 sx)
     :bullet-h (* 12 sx)
     :score-size (max 20 (int (* 20 sx)))
     :score-x (int (* 8 sx))
     :score-y (int (+ top (* 8 sx)))
     :msg-size (max 20 (int (* 28 sx)))
     :msg-y (int (+ top (* 210 sy) (* -14 sx)))}))

(defn msg-x
  "Where `line` starts so it is centred across the screen's width."
  [{:keys [w msg-size]} line]
  (max 0 (int (* 0.5 (- w (text-width msg-size line))))))

(defn alien-rect
  "Screen rect of the alien at column `c`, row `r` while the formation's
  top-left is `[ax ay]`."
  [{:keys [sp row-sp alien-w alien-h]} ax ay [c r]]
  [(+ ax (* c sp)) (+ ay (* r row-sp)) alien-w alien-h])

(defn ship-rect
  "Screen rect of the ship with its left edge at `ship-x`."
  [{:keys [ship-y ship-w ship-h]} ship-x]
  [ship-x ship-y ship-w ship-h])

(defn bullet-rect
  "Screen rect of a bullet, centred on its x. The hit test uses the point
  `[x y]`, which is the rect's top centre."
  [{:keys [bullet-w bullet-h]} {:keys [x y]}]
  [(- x (* 0.5 bullet-w)) y bullet-w bullet-h])

(defn- hit-alien
  [dims aliens bx by ax ay]
  (some (fn [cell]
          (let [[px py aw ah] (alien-rect dims ax ay cell)]
            (when (and (>= bx px) (<= bx (+ px aw))
                       (>= by py) (<= by (+ py ah)))
              cell)))
        aliens))

(defn- new-game [{:keys [w h ship-w ax0 ay0]} g]
  {:screen [w h]
   :ship-x (* 0.5 (- w ship-w))
   :bullets []
   :aliens (set (for [c (range acols) r (range arows)] [c r]))
   :ax ax0
   :ay ay0
   :adir 1.0
   :cooldown 0
   :over? false
   :won? false
   :score 0
   :gesture g})

(defn- march
  "The formation's sideways step, or a reversal and a drop when the step would
  cross a wall margin, as the original's."
  [{:keys [sp alien-w w speed margin drop]}
   {:keys [aliens ax ay adir]
    :as st}]
  (let [cs (map first aliens)
        minc (reduce min cs)
        maxc (reduce max cs)
        nax (+ ax (* adir speed))]
    (if (or (< (+ nax (* minc sp)) margin)
            (> (+ nax (* maxc sp) alien-w) (- w margin)))
      (assoc st :adir (- adir) :ay (+ ay drop))
      (assoc st :ax nax))))

(defn- step
  "One frame of the original's rules, with the ship already placed. `fire?` says
  a finger is down."
  [{:keys [ship-y ship-w bullet-speed top]
    :as dims}
   {:keys [ship-x bullets aliens ax ay cooldown score]
    :as st}
   fire?]
  (let [shoot? (and fire? (zero? cooldown))
        bullets (cond-> (mapv (fn [b] (update b :y - bullet-speed)) bullets)
                  shoot? (conj {:x (+ ship-x (/ ship-w 2.0))
                                :y ship-y}))
        ;; Gone once above the play area, so a held finger cannot grow the list.
        bullets (filterv (fn [b] (>= (:y b) top)) bullets)
        cooldown (cond shoot? cooldown-frames (pos? cooldown) (dec cooldown) :else 0)
        result (reduce (fn [acc b]
                         (if-let [a (hit-alien dims (:aliens acc) (:x b) (:y b) ax ay)]
                           (-> acc (update :aliens disj a) (update :score + 10))
                           (update acc :bullets conj b)))
                       {:aliens aliens
                        :bullets []
                        :score score}
                       bullets)
        st (assoc st :bullets (:bullets result) :aliens (:aliens result)
                  :cooldown cooldown :score (:score result))
        st (if (seq (:aliens st)) (march dims st) st)
        lowest (if (seq (:aliens st)) (reduce max (map second (:aliens st))) -1)
        [_ low-y _ low-h] (when (>= lowest 0) (alien-rect dims (:ax st) (:ay st) [0 lowest]))
        reached? (and (>= lowest 0) (>= (+ low-y low-h) ship-y))]
    (cond (empty? (:aliens st)) (assoc st :won? true)
          reached? (assoc st :over? true)
          :else st)))

(defn advance
  "One frame. Calls `gesture/track` once and stores the result on every path. A
  rotation (the metrics report a different `:screen` than the state was laid
  out for) starts a new game, because every position is in the old screen's
  pixels. Otherwise the ship goes under the finger, and a finished game waits
  for a tap outside Back."
  [state input]
  (let [dims (dimensions (:metrics input))
        [g event] (gesture/track (:gesture state) input)
        point (get-in input [:pointer :position])]
    (if (not= [(:w dims) (:h dims)] (:screen state))
      (new-game dims g)
      (let [down? (gesture/down? input)
            state (assoc state :gesture g)
            max-x (- (:w dims) (:ship-w dims))
            ship-x (if down?
                     (double (max 0 (min max-x (- (nth point 0) (/ (:ship-w dims) 2.0)))))
                     (:ship-x state))
            state (assoc state :ship-x ship-x)]
        (if (or (:over? state) (:won? state))
          (if (and (= :tap (:type event))
                   (not (gesture/in-back-region? (:at event))))
            (assoc (new-game dims g) :ship-x ship-x)
            state)
          (step dims state down?))))))

(defn- init [{:keys [metrics]}]
  [(new-game (dimensions metrics) gesture/idle)
   [[:scene/init :invaders]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :invaders]]])

(defn scene []
  {:id :invaders
   :title "Space Invaders"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
