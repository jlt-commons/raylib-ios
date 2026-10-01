(ns raylib.scenes.pacman
  "Pac-Man, steered by swipes. Ported from raylib-jlt's `pacman`, which came
  from Michiel Borkent's example in babashka/ffi (MIT).

  The original steers with the arrow keys or WASD and restarts with ENTER. Here
  a `:swipe` from `raylib.gesture` sets the desired direction, and a `:tap`
  restarts after game over unless it lands in the Back region, which is the
  host's. A swipe is queued, not applied: Pac-Man takes it at the next tile
  centre where that way is open, which is what a held key does in the original,
  so a swipe into a wall waits until the way opens and a swipe a little early
  still corners. A swipe never turns him between centres, because deciding there
  is what lets an entity drift into a wall.

  The rules are the original's: a maze of 19 by 21 tiles with side tunnels, dots
  worth 10 and four power pellets worth 50, four ghosts with their own
  personalities (see `raylib.scenes.pacman.ghosts`) that alternate between
  scatter and chase on a timer and turn blue for seven seconds after a power
  pellet, the 200, 400, 800 and 1600 combo for eating them, three lives, a
  level cleared when the last dot goes (then the next level, with score and lives
  kept), and game over when the last life does. The original's two constants
  `SCATTER-SECONDS` (20) and `CHASE-SECONDS` (7) are named the wrong way round.
  What it does is scatter for 7 s, then chase for 20 s, and that is what
  `scatter-seconds` and `chase-seconds` hold.

  The original calls `get-frame-time` once per frame, so `advance` reads
  `:delta-seconds`, clamped to 0 below and 0.05 above, which is the original's
  cap against a stalled frame stepping an entity through a wall. Everything is
  in tile units and seconds, so nothing scales with the screen except the
  layout: the maze is drawn with the largest square tile that fits the safe
  region below Back and a HUD row, so a rotation re-lays it out and keeps the
  game. The one fix to the original is the level clear. It re-arms the two
  second `LEVEL CLEARED` timer on every frame while the dots are gone, so the
  next level never begins. Here it is set once, and the board freezes while
  `LEVEL CLEARED` shows, so no ghost can take a life from a cleared board.

  The original draws Pac-Man with `sector!`, which isn't bound here. His mouth
  is a fan of triangles (`pac-fan`) the draw method gives to `rl/draw-triangle`.
  The original's `rand-nth` for a frightened ghost is the project's LCG, taking
  its high bits. Colours are `[r g b a]` vectors. The draw method packs them
  with `rl/rgba`."
  (:require [raylib.gesture :as gesture]
            [raylib.scenes.pacman.ghosts :as ghosts]
            [raylib.scenes.pacman.maze :as maze]))

(def pac-speed "Tiles per second. The original's." 5.6)
(def frightened-seconds "How long a power pellet frightens the ghosts. The original's." 7.0)
(def scatter-seconds "How long a scatter lasts. The original's, which it names `CHASE-SECONDS`." 7.0)
(def chase-seconds "How long a chase lasts. The original's, which it names `SCATTER-SECONDS`." 20.0)
(def max-dt "The longest frame the world steps, in seconds. The original's." 0.05)
(def start-lives 3)
(def catch-distance "Manhattan tiles within which a ghost touches Pac-Man. The original's." 0.75)

(def background-colour [6 6 14 255])
(def wall-colour [33 33 222 255])
(def wall-edge-colour [20 20 130 255])
(def pellet-colour [255 224 40 255])
(def label-colour [240 240 245 255])
(def door-colour [255 184 174 255])
(def pupil-colour [20 20 60 255])
(def scared-colour [40 60 230 255])
(def over-colour [255 90 80 255])

(def caught-line "CAUGHT!")
(def cleared-line "LEVEL CLEARED")
(def over-line "GAME OVER")
(def restart-line "TAP TO RESTART")

(defn score-line [n] (str "SCORE " n))
(defn level-line [n] (str "LEVEL " n))

(defn dimensions
  "The layout for `metrics`' `:screen`. `:cell` is the side of a square tile: the
  largest that fits the maze, with half a tile spare each side so a ghost
  wrapping through the tunnel stays on screen, in the width and in the height
  less the HUD row. The play area starts at the bottom edge of
  `gesture/back-region`, and `:ox` and `:oy` centre the maze in it. The HUD row,
  all left-aligned so nothing is measured here, is `:score` and `:level` as
  `{:x :y :size}` and `:lives` as `{:x0 :y :r :step}`, the first of three
  circles. `:msg` and `:msg2` are `{:y :size}`, whose x `centred-x` completes."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        top (+ back-y back-h)
        side (min w h)
        size (max 16 (int (* 0.03 side)))
        pad (max 8 (int (* 0.015 side)))
        hud-y (+ top pad)
        area-top (+ hud-y size pad)
        area-h (- h area-top pad)
        cell (min (/ w (+ maze/width 1.0)) (/ area-h (double maze/height)))
        r (* 0.4 size)
        step (* 2.6 r)
        msg-size (max 20 (int (* 0.05 side)))
        msg-y (int (- (+ area-top (* 0.5 area-h)) msg-size))]
    {:w w
     :h h
     :cell cell
     :ox (* 0.5 (- w (* maze/width cell)))
     :oy (+ area-top (* 0.5 (- area-h (* maze/height cell))))
     :score {:x pad
             :y hud-y
             :size size}
     :level {:x (int (* 0.4 w))
             :y hud-y
             :size size}
     :lives {:x0 (- w pad r (* 2 step))
             :y (+ hud-y (* 0.5 size))
             :r r
             :step step}
     :msg {:y msg-y
           :size msg-size}
     :msg2 {:y (int (+ msg-y (* 1.3 msg-size)))
            :size (max 16 (int (* 0.03 side)))}}))

(defn centred-x
  "Where `line` at `size` starts so it is centred across the screen's width.
  `measure` is `(fn [s size] -> px)`."
  [{:keys [w]} size line measure]
  (max 0 (int (* 0.5 (- w (measure line size))))))

(defn tile-rect
  "`[x y w h]` in whole pixels of tile `[gx gy]`. Edges are rounded down, so two
  tiles that share an edge share it exactly and walls leave no seam."
  [{:keys [cell ox oy]} gx gy]
  (let [x0 (long (Math/floor (+ ox (* gx cell))))
        y0 (long (Math/floor (+ oy (* gy cell))))
        x1 (long (Math/floor (+ ox (* (inc gx) cell))))
        y1 (long (Math/floor (+ oy (* (inc gy) cell))))]
    [x0 y0 (- x1 x0) (- y1 y0)]))

(defn pac-fan
  "Triangles `[cx cy x1 y1 x2 y2]` for Pac-Man at `[cx cy]` with radius `r`,
  facing `[fx fy]`, as `n` wedges of a disc with a mouth cut out. The mouth is
  the original's: half-open `0.42 * (1 + sin mouth)` radians each side of the
  heading, so it opens and shuts as `mouth` advances and is shut at the bottom
  of the sine. The fan sweeps from the mouth's lower lip round to its upper one,
  and `rl/draw-triangle` fixes each wedge's winding."
  [cx cy r fx fy mouth n]
  (let [heading (Math/atan2 (double fy) (double fx))
        open (* 0.42 (+ 1.0 (Math/sin (double mouth))))
        from (+ heading open)
        sweep (- (* 2.0 Math/PI) (* 2.0 open))
        point (fn [i] (let [a (+ from (* sweep (/ i (double n))))]
                        [(+ cx (* r (Math/cos a))) (+ cy (* r (Math/sin a)))]))]
    (mapv (fn [i] (let [[x1 y1] (point i)
                        [x2 y2] (point (inc i))]
                    [cx cy x1 y1 x2 y2]))
          (range n))))

(defn power-blink?
  "Whether the power pellets show: 0.25 s of every 0.4 s, as the original."
  [clock]
  (< (mod clock 0.4) 0.25))

(defn ghost-blink?
  "Whether a frightened ghost shows white: 0.12 s of every 0.25 s."
  [clock]
  (< (mod clock 0.25) 0.12))

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

(defn- initial-pac
  "`:dx :dy` is the heading, `:ndx :ndy` the queued turn and `:fx :fy` the last
  non-zero heading, which is where the mouth points while he is stopped."
  []
  (let [[sx sy] (maze/centre-of maze/pac-start)]
    {:x sx
     :y sy
     :dx -1
     :dy 0
     :ndx -1
     :ndy 0
     :fx -1
     :fy 0
     :mouth 0.0}))

(defn new-game
  "A fresh world. With `previous`, score and lives carry over and the level
  advances, which is the between-levels reset."
  ([seed g] (new-game seed g nil))
  ([seed g previous]
   {:pac (initial-pac)
    :ghosts (ghosts/initial-ghosts)
    :dots (maze/initial-dots)
    :score (if previous (:score previous) 0)
    :lives (if previous (:lives previous) start-lives)
    :level (if previous (inc (:level previous)) 1)
    :mode-timer scatter-seconds
    :chase? false
    :combo 0
    :message nil
    :message-timer 0.0
    :cleared? false
    :over? false
    :clock 0.0
    :seed seed
    :gesture g}))

(defn move-pac
  "Pac-Man after `dt` seconds. The queued turn wins at a tile centre whenever
  that way is open, otherwise he keeps his heading, otherwise he stops."
  [pac dt]
  (let [{:keys [ndx ndy]} pac
        decide (fn [tx ty dx dy]
                 (cond
                   (and (or (not= ndx dx) (not= ndy dy))
                        (not (maze/wall? (+ tx ndx) (+ ty ndy))))
                   [ndx ndy]
                   (not (maze/wall? (+ tx dx) (+ ty dy))) [dx dy]
                   :else [0 0]))
        moved (maze/step-entity pac {:speed pac-speed
                                     :dt dt
                                     :decide decide
                                     :walls? maze/wall?})
        moving? (not (and (zero? (:dx moved)) (zero? (:dy moved))))]
    (merge pac moved
           {:fx (if moving? (:dx moved) (:fx pac))
            :fy (if moving? (:dy moved) (:fy pac))
            :mouth (+ (:mouth pac) (* dt (if moving? 9.0 0.0)))})))

(defn eat
  "`s` with the dot or pellet under Pac-Man eaten. A pellet frightens every
  ghost and restarts the combo, which is what makes 200/400/800/1600 possible."
  [s]
  (let [pac (:pac s)
        tx (maze/tile-of (:x pac))
        ty (maze/tile-of (:y pac))]
    (if-not (contains? (:dots s) [tx ty])
      s
      (let [pellet? (= \o (maze/tile-at tx ty))]
        (cond-> (-> s
                    (update :dots disj [tx ty])
                    (update :score + (if pellet? 50 10)))
          pellet? (-> (assoc :combo 0)
                      (update :ghosts
                              (fn [gs] (mapv #(assoc % :frightened frightened-seconds) gs)))))))))

(defn- caught
  [s]
  (let [lives (dec (:lives s))]
    (if (pos? lives)
      (assoc s
             :lives lives
             :message caught-line
             :message-timer 1.2
             :pac (initial-pac)
             :ghosts (ghosts/initial-ghosts))
      (assoc s :lives 0 :over? true :message over-line))))

(defn- eat-ghost
  [s i g]
  (let [combo (inc (:combo s))
        [hx hy] (maze/centre-of (nth maze/house-slots 1))]
    (-> s
        ;; 200, 400, 800, 1600 within one pellet.
        (update :score + (* 200 (bit-shift-left 1 (dec combo))))
        (assoc :combo combo)
        (assoc-in [:ghosts i] (merge g {:x hx
                                        :y hy
                                        :dx 0
                                        :dy -1
                                        :frightened 0.0
                                        :home-timer 1.5})))))

(defn collide
  "`s` after each ghost within `catch-distance` of Pac-Man has either been eaten,
  when frightened, or cost him a life."
  [s]
  (let [pac (:pac s)]
    (reduce (fn [s i]
              (let [g (get-in s [:ghosts i])
                    d (+ (abs (- (:x g) (:x pac)))
                         (abs (- (:y g) (:y pac))))]
                (cond
                  (> d catch-distance) s
                  (pos? (:frightened g)) (eat-ghost s i g)
                  :else (caught s))))
            s
            (range (count (:ghosts s))))))

(defn advance-mode
  "Scatter and chase alternate on a timer: 7 s of scatter, 20 s of chase, and
  round again."
  [s dt]
  (let [t (- (:mode-timer s) dt)]
    (if (pos? t)
      (assoc s :mode-timer t)
      (assoc s
             :chase? (not (:chase? s))
             :mode-timer (if (:chase? s) scatter-seconds chase-seconds)))))

(defn- move-ghosts
  "Every ghost moved. Each draws one number from the LCG per frame, used only if
  it is frightened, and sees the others where they stood at the start of the
  frame."
  [s dt]
  (let [pac (:pac s)
        before (:ghosts s)
        [moved seed] (reduce (fn [[out seed] g]
                               (let [[pick seed'] (roll seed 32768)]
                                 [(conj out (ghosts/move-ghost g {:pac pac
                                                                  :ghosts before
                                                                  :chase? (:chase? s)
                                                                  :dt dt
                                                                  :pick pick}))
                                  seed']))
                             [[] (:seed s)]
                             before)]
    (assoc s :ghosts moved :seed seed)))

(defn tick
  "One step of the world, `dt` seconds long. The `LEVEL CLEARED` message shows
  for its two seconds, set once when the dots run out, and then the next level
  begins. The board is frozen meanwhile, so a ghost cannot cost a life during
  it."
  [s dt]
  (let [s (update s :clock + dt)]
    (cond
      (:over? s) s

      (and (:cleared? s) (zero? (:message-timer s)))
      (new-game (:seed s) (:gesture s) s)

      (:cleared? s)
      (let [s (update s :message-timer #(max 0.0 (- % dt)))]
        (if (zero? (:message-timer s)) (assoc s :message nil) s))

      :else
      (let [s (update s :message-timer #(max 0.0 (- % dt)))
            s (if (zero? (:message-timer s)) (assoc s :message nil) s)
            s (advance-mode s dt)
            s (update s :pac move-pac dt)
            s (eat s)
            s (move-ghosts s dt)
            s (collide s)]
        (if (and (empty? (:dots s)) (not (:cleared? s)))
          (assoc s :message cleared-line :message-timer 2.0 :cleared? true)
          s)))))

(def ^:private swipe-dirs
  {:up [0 -1]
   :down [0 1]
   :left [-1 0]
   :right [1 0]})

(defn- queue-turn
  "`s` with the swipe's direction as Pac-Man's queued turn."
  [s swipe-dir]
  (let [[ndx ndy] (swipe-dirs swipe-dir)]
    (update s :pac assoc :ndx ndx :ndy ndy)))

(defn- frame-time [input]
  (max 0.0 (min max-dt (double (or (:delta-seconds input) 0.0)))))

(defn advance
  "One frame. Calls `gesture/track` once and stores the result on every path. A
  live game queues a swipe's turn and steps the world by the frame time. On the
  frame the last life goes it stores `gesture/idle` unless the frame is a press,
  so a touch already down cannot restart on its lift while a press on that very
  frame still counts. A finished game waits for a tap outside Back."
  [state input]
  (let [[g event] (gesture/track (:gesture state) input)]
    (if (:over? state)
      (if (and (= :tap (:type event))
               (not (gesture/in-back-region? (:at event))))
        (new-game (:seed state) g)
        (assoc state :gesture g))
      (let [queued (if (= :swipe (:type event)) (queue-turn state (:dir event)) state)
            after (assoc (tick queued (frame-time input)) :gesture g)]
        (if (and (:over? after) (not= :press (get-in input [:pointer :phase])))
          (assoc after :gesture gesture/idle)
          after)))))

(defn- init [_]
  [(new-game start-seed gesture/idle)
   [[:scene/init :pacman]]])
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :pacman]]])

(defn scene []
  {:id :pacman
   :title "Pac-Man"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
