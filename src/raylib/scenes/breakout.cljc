(ns raylib.scenes.breakout
  "Breakout: slide the paddle under the ball and clear every brick. Ported from
  raylib-jlt's `breakout`.

  The original's paddle follows the mouse and SPACE starts a new game. Here the
  paddle's centre follows the finger's x while one is on the glass (`:press` or
  `:down`), clamped so the paddle stays inside the screen, and holds its place
  otherwise. Nothing moves on `:release`, whose position is the last hardware
  value and on a device is not the touch that just ended. After game over or a
  win, a `:press` anywhere outside the top-left Back region starts a new game.

  The rules are the original's: the walls and ceiling bounce, a brick is removed
  and `vy` flipped on contact, the paddle bounces the ball up and adds sideways
  speed by `0.08` of how far from the paddle's centre it landed, and there are
  three lives. The 800x450 layout is scaled to the safe region: horizontal speed
  by `w / 800`, vertical speed by `h / 450`, the brick band to start at 15% of
  the height.

  This is frame-locked, like the original: `advance` moves the ball a fixed
  distance per call, so the game runs faster on a 120 Hz display than on a 60 Hz
  one. The Delta Time scene shows why that matters and what the alternative
  looks like. It is kept here because the point of the port is the original.

  Colours are `[r g b a]` vectors, so the namespace stays pure. The draw method
  packs them with `rl/rgba`.")

(def lives-start 3)

(def back-region
  "`[x y w h]` in safe-area coordinates covering the host's Back button. A press
  inside it is Back, not a restart. estimate: Back is 330x73 at offset (40, 40)
  on the device this was measured on."
  [0 0 400 120])

(def row-colors
  "RED, ORANGE, GOLD, GREEN, SKYBLUE and VIOLET, top row first."
  [[230 41 55 255] [255 161 0 255] [255 203 0 255]
   [0 228 48 255] [102 191 255 255] [135 60 190 255]])

(def ball-colour [190 33 55 255])
(def paddle-colour [80 80 80 255])
(def text-colour [80 80 80 255])
(def over-colour [190 33 55 255])
(def won-colour [0 117 44 255])

(def over-line "GAME OVER - TAP")
(def won-line "YOU WIN! - TAP")

(defn lives-line [n] (str "lives " n))

(def ^:private cols 10)
(def ^:private rows 6)

(defn dimensions
  "The 800x450 layout scaled into the safe region. `:speed` is the ball's
  horizontal per-frame speed, scaled by width. `:vspeed` is the vertical
  per-frame speed, scaled by height and capped at a quarter of a brick height so
  the ball never skips a brick row."
  [metrics]
  (let [[w h] (:screen metrics)
        s (/ w 800.0)
        brick-h (max (* 0.03 h) (* 24 s))]
    {:w w
     :h h
     :scale s
     :cols cols
     :rows rows
     :brick-w (/ w (double cols))
     :brick-h brick-h
     :top (* 0.15 h)
     :paddle-w (* 100 s)
     :paddle-h (* 14 s)
     :paddle-y (* 0.92 h)
     :ball-r (* 8 s)
     :speed (* 3 s)
     :vspeed (min (* 3 (/ h 450.0)) (* 0.25 brick-h))
     :ball-x (* 0.5 w)
     :ball-y (* (/ 300.0 450.0) h)
     ;; The top right, so Back at the top left stays clear of it.
     :lives-size (max 20 (int (* 20 s)))
     :lives-x (int (* 0.6 w))
     :lives-y (int (* 0.05 h))
     :msg-size (max 20 (int (* 28 s)))
     :msg-x (int (* 0.3 w))
     :msg-y (int (* 0.47 h))}))

(defn all-bricks
  "Every `[col row]` of a `cols` by `rows` wall."
  [cols rows]
  (set (for [c (range cols) r (range rows)] [c r])))

(defn brick-rect
  "`[x y w h]` of the brick at `[c r]`, a pixel inside its cell on each side as
  in the original, so a gap shows between neighbours."
  [{:keys [brick-w brick-h top]} c r]
  [(+ 1.0 (* c brick-w))
   (+ 1.0 top (* r brick-h))
   (- brick-w 2.0)
   (- brick-h 2.0)])

(defn brick-at
  "The `[col row]` of the cell holding `[x y]`, or nil outside the brick band."
  [{:keys [brick-w brick-h top cols rows]} x y]
  (let [dy (- y top)]
    (when (and (>= dy 0) (>= x 0))
      (let [c (int (Math/floor (/ x brick-w)))
            r (int (Math/floor (/ dy brick-h)))]
        (when (and (< r rows) (< c cols)) [c r])))))

(defn- fabs [n] (if (neg? n) (- n) n))

(defn- new-ball [{:keys [ball-x ball-y speed vspeed]}]
  {:x ball-x
   :y ball-y
   :vx speed
   :vy (- vspeed)})

(defn- new-game [{:keys [w h]
                  :as dims} paddle-x]
  {:screen [w h]
   :bricks (all-bricks cols rows)
   :ball (new-ball dims)
   :paddle-x paddle-x
   :lives lives-start
   :over? false
   :won? false})

(defn- step
  "One frame of the original's rules, with the paddle's left edge at `paddle-x`."
  [{:keys [w h ball-r paddle-w paddle-h paddle-y scale]
    :as dims}
   {:keys [ball bricks lives]
    :as st} paddle-x]
  (let [{:keys [x y vx vy]} ball
        nx (+ x vx)
        ny (+ y vy)
        [nx vx] (cond (< nx ball-r) [ball-r (- vx)]
                      (> nx (- w ball-r)) [(- w ball-r) (- vx)]
                      :else [nx vx])
        [ny vy] (if (< ny ball-r) [ball-r (- vy)] [ny vy])
        on-paddle? (and (> vy 0)
                        (>= (+ ny ball-r) paddle-y)
                        (<= (+ ny ball-r) (+ paddle-y paddle-h (* 8 scale)))
                        (>= nx paddle-x)
                        (<= nx (+ paddle-x paddle-w)))
        vx (if on-paddle? (+ vx (* 0.08 (- nx (+ paddle-x (/ paddle-w 2.0))))) vx)
        vy (if on-paddle? (- (fabs vy)) vy)
        hit (brick-at dims nx ny)
        hit? (and hit (contains? bricks hit))
        bricks (if hit? (disj bricks hit) bricks)
        vy (if hit? (- vy) vy)
        lost? (> ny (+ h (* 20 scale)))]
    (cond
      (empty? bricks) (assoc st :bricks bricks :won? true)
      lost? (if (<= lives 1)
              (assoc st :over? true :lives 0)
              (assoc st :lives (dec lives) :ball (new-ball dims)))
      :else (assoc st :bricks bricks :ball {:x nx
                                            :y ny
                                            :vx vx
                                            :vy vy}))))

(defn- in-rect? [[bx by bw bh] [px py]]
  (and (>= px bx) (<= px (+ bx bw))
       (>= py by) (<= py (+ by bh))))

(defn- centred-paddle-x [{:keys [w paddle-w]}]
  (* 0.5 (- w paddle-w)))

(defn advance
  "One frame. The state remembers the `:screen` it was laid out for, and when the
  metrics report a different one (the phone rotated) this starts a new game for
  the new screen, because the bricks, paddle and ball positions are all in the
  old screen's pixels and a ball left mid-air could fall off the new bottom and
  cost a life. The paddle is clamped to the screen on every frame."
  [state input]
  (let [dims (dimensions (:metrics input))
        phase (get-in input [:pointer :phase])
        point (get-in input [:pointer :position])
        down? (boolean (and point (#{:press :down} phase)))
        max-x (- (:w dims) (:paddle-w dims))
        clamp-x (fn [x] (double (max 0 (min max-x x))))]
    (if (not= [(:w dims) (:h dims)] (:screen state))
      (new-game dims (centred-paddle-x dims))
      (let [state (if down?
                    (assoc state :paddle-x (clamp-x (- (nth point 0) (/ (:paddle-w dims) 2.0))))
                    (update state :paddle-x clamp-x))]
        (cond
          (or (:over? state) (:won? state))
          (if (and down? (= :press phase) (not (in-rect? back-region point)))
            (new-game dims (:paddle-x state))
            state)

          :else (step dims state (:paddle-x state)))))))

(defn- init [{:keys [metrics]}]
  (let [dims (dimensions metrics)]
    [(new-game dims (centred-paddle-x dims))
     [[:scene/init :breakout]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :breakout]]])

(defn scene []
  {:id :breakout
   :title "Breakout"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
