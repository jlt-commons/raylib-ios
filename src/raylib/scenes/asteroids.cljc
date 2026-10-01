(ns raylib.scenes.asteroids
  "Asteroids with four on-screen buttons. Ported from raylib-jlt's `asteroids`.

  The original rotates with LEFT and RIGHT, thrusts with UP, fires with SPACE
  and restarts with ENTER. Here four buttons sit along the bottom band, in the
  order rotate left, rotate right, thrust and fire. A button is held while ANY
  point in the input's `:touch-points` lies inside its rect, so one thumb can
  hold thrust while the other rotates, which the single `:pointer` could not
  say. A tap on the field outside the buttons and outside `gesture/back-region`
  restarts after game over, so a fire press after dying does not restart.

  The rules are the original's: ROT 0.07 a frame, THRUST 0.15, FRICTION 0.99,
  bullets that live 55 frames, asteroids of three sizes that split in two until
  the smallest is gone, waves of 4 plus one per 500 points, three lives and 90
  frames of blinking invulnerability after a crash. Its fire rule is a SPACE
  PRESS, an edge with no cooldown: holding the key fires once. A held fire
  button does the same here, one bullet per touch-down, and a player mashes it
  with a thumb as they would the key. Bullets expire by their life, so the count
  stays bounded however fast the button is pressed.

  The 800x450 game is scaled into the play field, the area below
  `gesture/back-region` and above the buttons, and it wraps there. Thrust makes
  the ship go where it points, so the speeds cannot be scaled by a different
  factor per axis without bending that, and one factor `:u`, the geometric mean
  of the two axes' scales, applies to every radius, speed and acceleration. Pace
  therefore lands between the original's across and down. A bullet's speed plus
  the ship's top speed stays below the smallest asteroid's diameter at any
  scale, so the point test cannot skip it unless the rock is closing too. As in
  the original, a skip needs a near head-on pass at the rock's own top speed,
  which adds about 25u of closing speed against a 22u diameter (see the test
  that pins the inequality).

  This is frame-locked, like the original: `advance` moves everything a fixed
  distance per call, so the game runs faster on a 120 Hz display than on a 60 Hz
  one. The Delta Time scene shows why that matters. It is kept here because the
  point of the port is the original. A rotation starts a new game, since every
  position is in the old screen's pixels.

  Asteroids come from the project's LCG, taking its high bits. Colours are
  `[r g b a]` vectors. The draw method packs them with `rl/rgba`."
  (:require [raylib.gesture :as gesture]))

(def rot "Radians a held rotate button turns the ship per frame. The original's." 0.07)
(def thrust "Acceleration along the heading per frame at scale 1. The original's." 0.15)
(def friction "Velocity kept each frame. The original's." 0.99)
(def bullet-speed "A bullet's speed relative to the ship at scale 1. The original's." 7.0)
(def bullet-life "Frames a bullet lives. The original's." 55)
(def ship-radius "The ship's size at scale 1. The original's." 14.0)
(def start-lives "Lives in a new game. The original's." 3)
(def invuln-start "Frames of invulnerability in a new game. The original's." 60)
(def invuln-crash "Frames of invulnerability after a crash. The original's." 90)

(def sizes
  "Radius at scale 1 and score for each asteroid size. The original's."
  {3 {:r 40.0
      :score 20}
   2 {:r 22.0
      :score 50}
   1 {:r 11.0
      :score 100}})

(def background-colour [8 8 18 255])
(def asteroid-colour [200 200 200 255])
(def ship-colour [245 245 245 255])
(def ship-base-colour [130 130 130 255])
(def bullet-colour [255 203 0 255])
(def text-colour [245 245 245 255])
(def over-colour [230 41 55 255])
(def button-colour [48 48 84 255])
(def button-held-colour [96 96 168 255])

(def over-line "GAME OVER")
(def restart-line "TAP TO RESTART")

(defn score-line [n] (str "SCORE " n))
(defn lives-line [n] (str "LIVES " n))

(def button-labels
  "Each button's id and the label drawn on it, in left-to-right order."
  [[:left "LEFT"] [:right "RIGHT"] [:thrust "THRUST"] [:fire "FIRE"]])

(defn- text-width
  "estimate: 0.6 of the size per character, for raylib's default font."
  [size line]
  (* 0.6 size (count line)))

(defn dimensions
  "The layout for `metrics`' `:screen`. The play field is `:fx :ftop :fw :fh`:
  the full width, from the bottom of `gesture/back-region` down to a gap above
  the buttons. `:u` is the one scale factor for radii, speeds and thrust (see
  the namespace docstring), `:radii` the scaled asteroid radii by size and
  `:buttons` the four rects, each with its label and the label's position."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        side (min w h)
        gap (* 0.02 side)
        btn-h (* 0.16 side)
        btn-y (- h gap btn-h)
        btn-w (/ (- w (* 5 gap)) 4.0)
        fh (- btn-y gap top)
        sx (/ w 800.0)
        sy (/ fh 450.0)
        u (Math/sqrt (* sx sy))
        label-size (max 20 (int (* 0.034 side)))
        msg-size (max 20 (int (* 50 sx)))]
    {:w w
     :h h
     :fx 0.0
     :ftop (double top)
     :fw (double w)
     :fh (double fh)
     :u u
     :ship-r (* ship-radius u)
     :bullet-speed (* bullet-speed u)
     :bullet-r (max 2.0 (* 2.0 u))
     :radii (into {} (map (fn [[k v]] [k (* (:r v) u)])) sizes)
     :thick (max 2.0 (* 0.003 side))
     :buttons (vec (map-indexed
                    (fn [i [id label]]
                      (let [x (+ gap (* i (+ btn-w gap)))]
                        {:id id
                         :rect [x btn-y btn-w btn-h]
                         :label label
                         :label-size label-size
                         :label-x (int (+ x (* 0.5 (- btn-w (text-width label-size label)))))
                         :label-y (int (+ btn-y (* 0.5 (- btn-h label-size))))}))
                    button-labels))
     :text-size (max 20 (int (* 22 sx)))
     :score-x (int (* 12 sx))
     :score-y (int (+ top (* 12 sx)))
     :msg-size msg-size
     :msg-y (int (+ top (* 0.35 fh)))
     :msg2-size (max 20 (int (* 20 sx)))
     :msg2-y (int (+ top (* 0.35 fh) (* 1.4 msg-size)))}))

(defn centred-x
  "Where `line` at `size` starts so it is centred across the screen's width."
  [{:keys [w]} size line]
  (max 0 (int (* 0.5 (- w (text-width size line))))))

(defn lives-x
  "Where the lives line starts so it ends one score-margin from the right edge."
  [{:keys [w score-x text-size]} line]
  (max 0 (int (- w score-x (text-width text-size line)))))

;; --- the LCG -------------------------------------------------------------

(defn- next-random [seed]
  (mod (+ (* 1103515245 (long seed)) 12345) 2147483648))

(defn- roll
  "`[v seed']`: an int in [0, n) from the LCG's high bits. The low bit
  alternates on every step, so `(quot seed' 65536)` is what gets used."
  [seed n]
  (let [seed' (next-random seed)]
    [(mod (quot seed' 65536) n) seed']))

(defn- rand-unit
  "`[v seed']` with v in [-1, 1), as the original's `rand-unit`."
  [seed]
  (let [[v seed'] (roll seed 201)]
    [(- (/ v 100.0) 1.0) seed']))

(def ^:private start-seed 20260930)

;; --- the world -----------------------------------------------------------

(defn- wrap
  "`v` brought back into [lo, lo + size) by one step, as the original's."
  [v lo size]
  (cond (< v lo) (+ v size)
        (>= v (+ lo size)) (- v size)
        :else v))

(defn- make-asteroid
  "`[asteroid seed']` at `[x y]`, as the original's `make-asteroid`: ten
  vertices between 0.65 and 1.15 of the radius, a velocity of up to 2.2 / size
  and a spin."
  [{:keys [u radii]} seed x y size]
  (let [r (radii size)
        [vx-unit s1] (rand-unit seed)
        [vy-unit s2] (rand-unit s1)
        [spin-unit s3] (rand-unit s2)
        [verts s4] (loop [i 0 s s3 acc []]
                     (if (= i 10)
                       [acc s]
                       (let [[v s'] (roll s 101)
                             a (* 2 Math/PI (/ (double i) 10))
                             rr (* r (+ 0.65 (* 0.5 (/ v 100.0))))]
                         (recur (inc i) s' (conj acc [(* rr (Math/cos a)) (* rr (Math/sin a))])))))]
    [{:x x
      :y y
      :vx (* vx-unit (/ 2.2 size) u)
      :vy (* vy-unit (/ 2.2 size) u)
      :size size
      :r r
      :angle 0.0
      :spin (* 0.02 spin-unit)
      :verts verts}
     s4]))

(defn- spawn-wave
  "`[asteroids seed']`: `n` size-3 asteroids on a random edge of the field."
  [{:keys [fx ftop fw fh]
    :as dims} seed n]
  (loop [i 0 s seed out []]
    (if (= i n)
      [out s]
      (let [[edge s1] (roll s 4)
            [along s2] (roll s1 (inc (int (if (< edge 2) fh fw))))
            [x y] (case (int edge)
                    0 [fx (+ ftop along)]
                    1 [(+ fx fw) (+ ftop along)]
                    2 [(+ fx along) ftop]
                    [(+ fx along) (+ ftop fh)])
            [a s3] (make-asteroid dims s2 (double x) (double y) 3)]
        (recur (inc i) s3 (conj out a))))))

(defn- new-ship [{:keys [fx ftop fw fh]}]
  {:x (+ fx (/ fw 2.0))
   :y (+ ftop (/ fh 2.0))
   :angle (- (/ Math/PI 2))
   :vx 0.0
   :vy 0.0})

(defn- new-game [dims seed g held]
  (let [[asteroids seed'] (spawn-wave dims seed 4)]
    {:screen [(:w dims) (:h dims)]
     :ship (new-ship dims)
     :bullets []
     :asteroids asteroids
     :score 0
     :lives start-lives
     :over? false
     :invuln invuln-start
     :seed seed'
     :held held
     :gesture g}))

(defn- close?
  "Whether `a` and `b` (each with `:x` and `:y`) are within `r` of each other."
  [a b r]
  (let [dx (- (:x a) (:x b))
        dy (- (:y a) (:y b))]
    (< (+ (* dx dx) (* dy dy)) (* r r))))

(defn- resolve-hits
  "Folds bullets into asteroids: a bullet inside an asteroid destroys both,
  scores, and splits an asteroid that is not the smallest into two. Returns
  `{:asteroids :bullets :score :seed}`."
  [dims asteroids bullets seed]
  (loop [as (seq asteroids) bs (vec bullets) out [] gained 0 s seed]
    (if (empty? as)
      {:asteroids out
       :bullets bs
       :score gained
       :seed s}
      (let [a (first as)
            hit (first (keep-indexed (fn [i b] (when (close? a b (:r a)) i)) bs))]
        (if hit
          (let [bs' (into (subvec bs 0 hit) (subvec bs (inc hit)))
                [splits s'] (if (> (:size a) 1)
                              (let [[a1 s1] (make-asteroid dims s (:x a) (:y a) (dec (:size a)))
                                    [a2 s2] (make-asteroid dims s1 (:x a) (:y a) (dec (:size a)))]
                                [[a1 a2] s2])
                              [[] s])]
            (recur (next as) bs' (into out splits) (+ gained (get-in sizes [(:size a) :score])) s'))
          (recur (next as) bs (conj out a) gained s))))))

(defn- step
  "One frame of the original's rules. `held` is the set of button ids down."
  [{:keys [u ship-r bullet-speed fx ftop fw fh]
    :as dims}
   {:keys [ship bullets asteroids score lives invuln seed held]
    :as st}
   now]
  (let [angle (cond-> (:angle ship)
                (now :left) (- rot)
                (now :right) (+ rot))
        thr? (boolean (now :thrust))
        vx (* friction (+ (:vx ship) (if thr? (* thrust u (Math/cos angle)) 0.0)))
        vy (* friction (+ (:vy ship) (if thr? (* thrust u (Math/sin angle)) 0.0)))
        x (wrap (+ (:x ship) vx) fx fw)
        y (wrap (+ (:y ship) vy) ftop fh)
        ship' {:x x
               :y y
               :angle angle
               :vx vx
               :vy vy}
        ;; The original fires on a SPACE press, not while it is down.
        fire? (and (now :fire) (not (contains? held :fire)))
        fired (if fire?
                [{:x x
                  :y y
                  :vx (+ vx (* bullet-speed (Math/cos angle)))
                  :vy (+ vy (* bullet-speed (Math/sin angle)))
                  :life bullet-life}]
                [])
        bullets' (->> (into bullets fired)
                      (map (fn [b]
                             (assoc b
                                    :x (wrap (+ (:x b) (:vx b)) fx fw)
                                    :y (wrap (+ (:y b) (:vy b)) ftop fh)
                                    :life (dec (:life b)))))
                      (filterv (fn [b] (pos? (:life b)))))
        moved (mapv (fn [a]
                      (assoc a
                             :x (wrap (+ (:x a) (:vx a)) fx fw)
                             :y (wrap (+ (:y a) (:vy a)) ftop fh)
                             :angle (+ (:angle a) (:spin a))))
                    asteroids)
        {as' :asteroids
         bs' :bullets
         gained :score
         seed' :seed} (resolve-hits dims moved bullets' seed)
        invuln' (max 0 (dec invuln))
        crash? (and (zero? invuln')
                    (some (fn [a] (close? ship' a (+ ship-r (:r a) (* -4.0 u)))) as'))
        lives' (if crash? (dec lives) lives)
        [next-wave seed''] (if (empty? as')
                             (spawn-wave dims seed' (+ 4 (quot score 500)))
                             [as' seed'])]
    (assoc st
           :ship (if crash? (new-ship dims) ship')
           :bullets bs'
           :asteroids next-wave
           :score (+ score gained)
           :lives lives'
           :over? (boolean (and crash? (<= lives' 0)))
           :invuln (if crash? invuln-crash invuln')
           :seed seed'')))

(defn held-buttons
  "The set of button ids with any of the input's `:touch-points` inside."
  [{:keys [buttons]} input]
  (let [pts (:touch-points input)]
    (into #{}
          (keep (fn [{:keys [id rect]}]
                  (when (some (fn [p] (gesture/in-rect? rect p)) pts) id)))
          buttons)))

(defn- on-button? [{:keys [buttons]} pt]
  (boolean (some (fn [{:keys [rect]}] (gesture/in-rect? rect pt)) buttons)))

(defn advance
  "One frame. Calls `gesture/track` once and stores the result on every path. A
  rotation (the metrics report a different `:screen` than the state was laid
  out for) starts a new game. Otherwise a live game steps with the buttons now
  held, and a finished one waits for a tap on the field, outside Back and the
  buttons."
  [state input]
  (let [dims (dimensions (:metrics input))
        [g event] (gesture/track (:gesture state) input)
        now (held-buttons dims input)]
    (cond
      (not= [(:w dims) (:h dims)] (:screen state))
      (new-game dims (:seed state) g now)

      (:over? state)
      (if (and (= :tap (:type event))
               (not (gesture/in-back-region? (:at event)))
               (not (on-button? dims (:at event))))
        (new-game dims (:seed state) g now)
        (assoc state :gesture g :held now))

      :else
      (let [after (assoc (step dims state now) :gesture g :held now)]
        (if (:over? after)
          ;; The one exception to storing `g'`: on the frame the game ends,
          ;; forget the touch. A short tap still down would otherwise lift
          ;; into a `:tap` and restart the game before its result was seen.
          (assoc after :gesture gesture/idle)
          after)))))

(defn asteroid-points
  "The asteroid's outline vertices in screen coordinates, turned by its angle."
  [{:keys [x y angle verts]}]
  (let [ca (Math/cos angle)
        sa (Math/sin angle)]
    (mapv (fn [[dx dy]]
            [(+ x (- (* dx ca) (* dy sa)))
             (+ y (* dx sa) (* dy ca))])
          verts)))

(defn ship-points
  "The ship's three corners, nose first, then left and right of the tail."
  [{:keys [ship-r]} {:keys [x y angle]}]
  (mapv (fn [da] [(+ x (* ship-r (Math/cos (+ angle da))))
                  (+ y (* ship-r (Math/sin (+ angle da))))])
        [0.0 2.6 -2.6]))

(defn ship-visible?
  "The ship blinks every 5 frames while invulnerable, as the original's."
  [invuln]
  (or (zero? invuln) (even? (quot invuln 5))))

(defn- init [{:keys [metrics]}]
  [(new-game (dimensions metrics) start-seed gesture/idle #{})
   [[:scene/init :asteroids]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :asteroids]]])

(defn scene []
  {:id :asteroids
   :title "Asteroids"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
