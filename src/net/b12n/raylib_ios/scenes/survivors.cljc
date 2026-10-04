(ns net.b12n.raylib-ios.scenes.survivors
  "Vampire Survivors, steered with a thumb-stick. Ported from raylib-jlt's
  `vampire_survivors`.

  The original moves with WASD or the arrows and restarts with ENTER. Here the
  hero follows a relative thumb-stick: wherever a finger lands is the stick's
  centre, and while it stays down the vector from that point to the finger is the
  direction. Inside `gesture/slop` of the centre there is no direction, so a tap
  or a trembling finger never moves the hero. Outside it the hero goes that way
  at the original's one fixed speed, the vector normalised so a diagonal is no
  faster, as the original's digital keys give a fixed step. `stick-dir` is that
  rule as a pure function. A press under `gesture/back-region` belongs to the
  host and starts no stick. A tap on the field restarts after game over, with
  idle stored on the frame the hero dies, unless that frame is a press, so a
  finger already down when the hero dies cannot restart on its lift.

  The rules are the original's: waves of two enemies plus one per 600 frames
  every 50 frames, shortening by one per 120 frames to a floor of 12, a 30 frame
  head start, enemies that chase at a fixed speed and die to two bullets, a
  shot every 22 frames less two per level to a floor of 5, bullets that live 90
  frames, a gem for every kill, a pickup radius of 44, levelling at five gems
  per level, 8 damage on contact with 25 frames of grace and 100 HP.

  This is frame-locked, like the original, which never reads a frame time:
  `advance` moves everything a fixed distance per call. The 800x450 game is
  scaled into the play field below `gesture/back-region` by one factor `:u`, the
  geometric mean of the two axes' scales, which applies to every radius and
  speed because the motion is free in two dimensions. Enemies appear on the edge
  of the field, one radius inside it, where the original starts them 20 pixels
  outside, and never within touching distance of the hero. Outside the field
  they would be drawn over the Back button. A rotation starts a new game,
  since every position is in the old screen's pixels.

  The counts are bounded through frame 720 (about 12 s). Bullets live 90 frames
  and a shot takes at least 5, so at most 18 are alive. A gem needs a kill and a
  kill needs an enemy, so enemies and gems together never exceed what the waves
  have spawned, which by frame 720 is 16 waves of at most 3, so 48. Waves grow
  with time, so later the bound is higher.

  Randomness comes from the project's LCG, taking its high bits. Colours are
  `[r g b a]` vectors. The draw method packs them with `rl/rgba`."
  (:require [net.b12n.raylib-ios.gesture :as gesture]))

(def hero-speed "Pixels per frame at scale 1. The original's." 3.2)
(def hero-hp "Hit points in a new game. The original's." 100)
(def hero-r "The hero's radius at scale 1. The original's." 12.0)
(def enemy-r "An enemy's radius at scale 1. The original's." 10.0)
(def enemy-hp "Bullets an enemy takes. The original's." 2)
(def enemy-speed "An enemy's pixels per frame at scale 1. The original's." 1.4)
(def bullet-speed "A bullet's pixels per frame at scale 1. The original's." 6.0)
(def bullet-r "A bullet's radius at scale 1. The original's." 4.0)
(def bullet-life "Frames a bullet lives. The original's." 90)
(def contact-damage "Hit points lost to a touching enemy. The original's." 8)
(def hurt-frames "Frames of grace after a hit. The original's." 25)
(def pickup-r "How close the hero must be to take a gem, at scale 1. The original's." 44.0)
(def gem-size "A gem's side at scale 1. The original's." 6.0)
(def min-cooldown "The fastest the hero fires, in frames between shots. The original's." 5)

(def background-colour [20 18 28 255])
(def gem-colour [0 158 47 255])
(def enemy-colour [230 41 55 255])
(def bullet-colour [255 203 0 255])
(def hero-colour [102 191 255 255])
(def hurt-colour [245 245 245 255])
(def bar-back-colour [80 80 80 255])
(def hp-colour [230 41 55 255])
(def xp-colour [102 191 255 255])
(def text-colour [245 245 245 255])
(def hint-colour [130 130 130 255])
(def stick-colour [245 245 245 90])
(def knob-colour [245 245 245 140])

(def over-line "YOU DIED")
(def restart-line "TAP TO RESTART")
(def hint-line "drag to move, you auto-fire")

(defn lv-line [n] (str "LV " n))
(defn kills-line [n] (str "KILLS " n))
(defn time-line [frames] (str "TIME " (quot frames 60) "s"))
(defn summary-line [frames kills] (str "survived " (quot frames 60) "s - " kills " kills"))

(defn fire-cooldown
  "Frames between shots at `level`: 22 less two per level, never under 5."
  [level]
  (max min-cooldown (- 22 (* 2 level))))

(defn xp-needed "Gems that take the hero from `level` to the next." [level] (* level 5))

(defn dimensions
  "The layout for `metrics`' `:screen`. The play field is `:fx :ftop :fw :fh`:
  the full width, from the bottom of `gesture/back-region` to the bottom. `:u`
  scales radii and speeds (see the namespace docstring) and `:ts`, the smaller
  of the two axes' scales, sizes the text so it fits whichever way the phone is
  held. `:edge` is how far inside the field an enemy appears. `:stick-r` is the
  ring drawn round the stick's centre and `:knob-r` its knob. The HUD, all of it
  left-aligned so nothing is measured here, is the `:hp-bar` and `:xp-bar` rects
  as `[x y w h]`, and `:lv`, `:kills`, `:time` and `:hint` as `{:x :y :size}`.
  The game-over messages are `:msg`, `:msg2` and `:msg3`, whose `:y` and `:size`
  `centred-x` completes."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        fh (- h top)
        sx (/ w 800.0)
        sy (/ fh 450.0)
        u (Math/sqrt (* sx sy))
        ts (min sx sy)
        side (min w h)
        pad (max 8 (int (* 12 ts)))
        size (max 20 (int (* 20 ts)))
        bar-w (* 200 ts)
        hp-h (max 6 (* 16 ts))
        xp-h (max 4 (* 8 ts))
        hp-y (+ top pad)
        xp-y (+ hp-y hp-h (* 6 ts))
        kills-y (+ (max (+ xp-y xp-h) (+ hp-y size)) pad)
        time-y (+ kills-y size (* 4 ts))
        hint-size (max 16 (int (* 16 ts)))
        msg-size (max 20 (int (* 46 ts)))
        msg-y (+ top (* 0.35 fh))
        msg2-size (max 20 (int (* 20 ts)))]
    {:w w
     :h h
     :fx 0.0
     :ftop (double top)
     :fw (double w)
     :fh (double fh)
     :u u
     :ts ts
     :hero-r (* hero-r u)
     :enemy-r (* enemy-r u)
     :edge (* enemy-r u)
     :bullet-r (max 2.0 (* bullet-r u))
     :gem-side (max 3.0 (* gem-size u))
     :pickup-r (* pickup-r u)
     :hero-speed (* hero-speed u)
     :enemy-speed (* enemy-speed u)
     :bullet-speed (* bullet-speed u)
     :stick-r (* 0.08 side)
     :knob-r (* 0.032 side)
     :hp-bar [pad hp-y bar-w hp-h]
     :xp-bar [pad xp-y bar-w xp-h]
     :lv {:x (int (+ pad bar-w (* 8 ts)))
          :y (int hp-y)
          :size size}
     :kills {:x pad
             :y (int kills-y)
             :size size}
     :time {:x pad
            :y (int time-y)
            :size size}
     :hint {:x pad
            :y (int (- h pad hint-size))
            :size hint-size}
     :msg {:y (int msg-y)
           :size msg-size}
     :msg2 {:y (int (+ msg-y (* 1.4 msg-size)))
            :size msg2-size}
     :msg3 {:y (int (+ msg-y (* 1.4 msg-size) (* 1.4 msg2-size)))
            :size msg2-size}}))

(defn centred-x
  "Where `line` at `size` starts so it is centred across the screen's width.
  `measure` is `(fn [s size] -> px)`."
  [{:keys [w]} size line measure]
  (max 0 (int (* 0.5 (- w (measure line size))))))

;; --- the LCG -------------------------------------------------------------

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- roll
  "`[v seed']`: an int in [0, n) from the LCG's high bits. The low bit
  alternates on every step, so `(quot seed' 65536)` is what gets used."
  [seed n]
  (let [seed' (next-random seed)]
    [(mod (quot seed' 65536) n) seed']))

(def ^:private start-seed 20261001)

;; --- the world -----------------------------------------------------------

(defn- clamp [v lo hi] (max lo (min hi v)))

(defn- close?
  "Whether `a` and `b` (each with `:x` and `:y`) are within `r` of each other."
  [a b r]
  (let [dx (- (:x a) (:x b))
        dy (- (:y a) (:y b))]
    (< (+ (* dx dx) (* dy dy)) (* r r))))

(defn- nearest
  "The enemy closest to `[x y]`, or nil when there are none."
  [x y enemies]
  (when (seq enemies)
    (apply min-key (fn [e] (let [dx (- (:x e) x)
                                 dy (- (:y e) y)]
                             (+ (* dx dx) (* dy dy))))
           enemies)))

(defn- toward
  "Velocity `[vx vy]` of magnitude `speed` from `[fx fy]` toward `[tx ty]`. Closer
  than a pixel it points a little short of full speed, as the original does."
  [[fx fy] [tx ty] speed]
  (let [dx (- tx fx)
        dy (- ty fy)
        d (max 1.0 (Math/sqrt (+ (* dx dx) (* dy dy))))]
    [(* speed (/ dx d)) (* speed (/ dy d))]))

(defn- edge-point
  "`[[x y] seed']`: a random point on the field's edge, one radius inside it."
  [{:keys [fx ftop fw fh edge]} seed]
  (let [[side s1] (roll seed 4)
        [along s2] (roll s1 (inc (int (- (if (< side 2) fh fw) (* 2 edge)))))]
    [(case (int side)
       0 [(+ fx edge) (+ ftop edge along)]
       1 [(- (+ fx fw) edge) (+ ftop edge along)]
       2 [(+ fx edge along) (+ ftop edge)]
       [(+ fx edge along) (- (+ ftop fh) edge)])
     s2]))

(def ^:private spawn-tries
  "Positions rolled for one enemy before the opposite edge is used instead."
  4)

(defn- spawn-wave
  "`[enemies seed']`: `n` enemies, each on a random edge of the field and a
  random point along it, one radius inside so the whole circle is in the field.
  A point within `hero-r + enemy-r + enemy-speed` of the hero `[hx hy]` is
  rolled again, so an enemy can't touch the hero on the frame it appears. After
  `spawn-tries` rolls the last point is mirrored through the field's centre,
  which is on the opposite edge and far from a hero that was near it."
  [{:keys [fx ftop fw fh hero-r enemy-r enemy-speed]
    :as dims}
   [hx hy] seed n]
  (let [near (+ hero-r enemy-r enemy-speed)
        clear? (fn [[x y]] (not (close? {:x x
                                         :y y} {:x hx
                                                :y hy} near)))]
    (loop [i 0 s seed out []]
      (if (= i n)
        [out s]
        (let [[[x y] s'] (loop [tries 1 s s]
                           (let [[p s'] (edge-point dims s)]
                             (cond
                               (clear? p) [p s']
                               (>= tries spawn-tries) [[(- (+ fx fx fw) (first p))
                                                        (- (+ ftop ftop fh) (second p))] s']
                               :else (recur (inc tries) s'))))]
          (recur (inc i) s' (conj out {:x (double x)
                                       :y (double y)
                                       :hp enemy-hp})))))))

(defn- new-game [dims seed g]
  {:screen [(:w dims) (:h dims)]
   :hero {:x (+ (:fx dims) (/ (:fw dims) 2.0))
          :y (+ (:ftop dims) (/ (:fh dims) 2.0))
          :hp hero-hp
          :level 1
          :xp 0
          :hurt-cd 0}
   :enemies []
   :bullets []
   :gems []
   :fire-cd 0
   :spawn-cd 30
   :time 0
   :kills 0
   :over? false
   :seed seed
   :gesture g
   :stick nil})

(defn- resolve-hits
  "Folds bullets into enemies: a bullet within range takes 1 HP, a dead enemy
  drops a gem and counts a kill. Returns `{:enemies :bullets :gems :kills}`."
  [{:keys [enemy-r bullet-r]} enemies bullets gems kills]
  (loop [es (seq enemies) bs (vec bullets) out-e [] out-g (vec gems) k kills]
    (if (empty? es)
      {:enemies out-e
       :bullets bs
       :gems out-g
       :kills k}
      (let [e (first es)
            hit (first (keep-indexed (fn [i b] (when (close? e b (+ enemy-r bullet-r)) i)) bs))]
        (if hit
          (let [bs' (into (subvec bs 0 hit) (subvec bs (inc hit)))
                e' (update e :hp dec)]
            (if (<= (:hp e') 0)
              (recur (next es) bs' out-e (conj out-g {:x (:x e)
                                                      :y (:y e)}) (inc k))
              (recur (next es) bs' (conj out-e e') out-g k)))
          (recur (next es) bs (conj out-e e) out-g k))))))

(defn- step
  "One frame of the original's rules. `dir` is the stick's unit vector or nil."
  [{:keys [fx ftop fw fh hero-r hero-speed enemy-speed bullet-speed enemy-r pickup-r]
    :as dims}
   {:keys [hero seed]
    :as st}
   dir]
  (let [[dx dy] (or dir [0.0 0.0])
        hx (clamp (+ (:x hero) (* hero-speed dx)) (+ fx hero-r) (- (+ fx fw) hero-r))
        hy (clamp (+ (:y hero) (* hero-speed dy)) (+ ftop hero-r) (- (+ ftop fh) hero-r))
        ;; a wave when the timer runs out, bigger and faster as time passes
        spawn-cd (dec (:spawn-cd st))
        [enemies0 spawn-cd seed']
        (if (<= spawn-cd 0)
          (let [[wave s] (spawn-wave dims [hx hy] seed (+ 2 (quot (:time st) 600)))]
            [(into (:enemies st) wave) (max 12 (- 50 (quot (:time st) 120))) s])
          [(:enemies st) spawn-cd seed])
        enemies1 (mapv (fn [e]
                         (let [[vx vy] (toward [(:x e) (:y e)] [hx hy] enemy-speed)]
                           (assoc e :x (+ (:x e) vx) :y (+ (:y e) vy))))
                       enemies0)
        fire-cd (dec (:fire-cd st))
        target (nearest hx hy enemies1)
        [bullets0 fire-cd] (if (and (<= fire-cd 0) target)
                             (let [[vx vy] (toward [hx hy] [(:x target) (:y target)] bullet-speed)]
                               [(conj (:bullets st) {:x hx
                                                     :y hy
                                                     :vx vx
                                                     :vy vy
                                                     :life bullet-life})
                                (fire-cooldown (:level hero))])
                             [(:bullets st) (max 0 fire-cd)])
        bullets1 (->> bullets0
                      (map (fn [b]
                             (assoc b
                                    :x (+ (:x b) (:vx b))
                                    :y (+ (:y b) (:vy b))
                                    :life (dec (:life b)))))
                      (filterv (fn [b] (pos? (:life b)))))
        {:keys [enemies bullets gems kills]} (resolve-hits dims enemies1 bullets1 (:gems st) (:kills st))
        [gems' got] (reduce (fn [[keep n] g]
                              (if (close? {:x hx
                                           :y hy} g pickup-r)
                                [keep (inc n)]
                                [(conj keep g) n]))
                            [[] 0] gems)
        xp0 (+ (:xp hero) got)
        need (xp-needed (:level hero))
        [level xp] (if (>= xp0 need) [(inc (:level hero)) (- xp0 need)] [(:level hero) xp0])
        hurt-cd0 (max 0 (dec (:hurt-cd hero)))
        touch? (some (fn [e] (close? {:x hx
                                      :y hy} e (+ hero-r enemy-r))) enemies)
        [hp hurt-cd] (if (and touch? (zero? hurt-cd0))
                       [(- (:hp hero) contact-damage) hurt-frames]
                       [(:hp hero) hurt-cd0])]
    (assoc st
           :hero {:x hx
                  :y hy
                  :hp hp
                  :level level
                  :xp xp
                  :hurt-cd hurt-cd}
           :enemies enemies
           :bullets bullets
           :gems gems'
           :fire-cd fire-cd
           :spawn-cd spawn-cd
           :time (inc (:time st))
           :kills kills
           :over? (<= hp 0)
           :seed seed')))

;; --- the thumb-stick -----------------------------------------------------

(defn stick-dir
  "The unit vector `[dx dy]` the thumb-stick points, or nil. The stick's centre
  is where the touch began, kept in `(:stick state)`. While the finger is down
  and further than `gesture/slop` from it, the direction is the normalised
  vector from the centre to the finger, whatever its length, so the speed is one
  value in every direction. A press frame, a finger inside the dead zone, no
  centre (a touch that began under Back, or before this scene opened) and a
  lifted finger all give nil. `metrics` is the input's `:metrics`."
  [state input metrics]
  (let [centre (get-in state [:stick :centre])
        {:keys [phase position]} (:pointer input)]
    (when (and centre (gesture/down? input) (not= :press phase))
      (let [vx (- (double (first position)) (double (first centre)))
            vy (- (double (second position)) (double (second centre)))
            len (Math/sqrt (+ (* vx vx) (* vy vy)))]
        (when (> len (gesture/slop metrics))
          [(/ vx len) (/ vy len)])))))

(defn- next-stick
  "The stick after this frame: a press below Back starts one at the press point,
  a held finger keeps its centre and follows, and anything else ends it."
  [state input]
  (let [{:keys [phase position]} (:pointer input)]
    (case phase
      :press (when (and position (not (gesture/in-back-region? position)))
               {:centre position
                :finger position})
      :down (when-let [centre (get-in state [:stick :centre])]
              (when position
                {:centre centre
                 :finger position}))
      nil)))

(defn knob
  "Where the stick's knob is drawn for `stick`: the finger, held to the ring."
  [{:keys [stick-r]} {:keys [centre finger]}]
  (let [[cx cy] centre
        vx (- (double (first finger)) (double cx))
        vy (- (double (second finger)) (double cy))
        len (Math/sqrt (+ (* vx vx) (* vy vy)))
        k (if (> len stick-r) (/ stick-r len) 1.0)]
    [(+ cx (* vx k)) (+ cy (* vy k))]))

(defn advance
  "One frame. Calls `gesture/track` once and stores the result on every path. A
  rotation starts a new game. A live game moves the hero by the stick and steps,
  and on the frame the hero dies stores `gesture/idle` unless the frame is a
  press, so a touch already down cannot restart on its lift while a press on
  that very frame still counts. A finished game waits for a tap on the field,
  outside Back."
  [state input]
  (let [metrics (:metrics input)
        dims (dimensions metrics)
        [g event] (gesture/track (:gesture state) input)]
    (cond
      (not= [(:w dims) (:h dims)] (:screen state))
      (new-game dims (:seed state) g)

      (:over? state)
      (if (and (= :tap (:type event))
               (not (gesture/in-back-region? (:at event))))
        (new-game dims (:seed state) g)
        (assoc state :gesture g))

      :else
      (let [after (assoc (step dims state (stick-dir state input metrics))
                         :gesture g
                         :stick (next-stick state input))]
        (if (:over? after)
          (assoc after
                 :gesture (if (= :press (get-in input [:pointer :phase])) g gesture/idle)
                 :stick nil)
          after)))))

(defn- init [{:keys [metrics]}]
  [(new-game (dimensions metrics) start-seed gesture/idle)
   [[:scene/init :survivors]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :survivors]]])

(defn scene []
  {:id :survivors
   :title "Vampire Survivors"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
