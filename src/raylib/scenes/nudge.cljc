(ns raylib.scenes.nudge
  "A ball moved with a thumb-stick. Ported from raylib-jlt's `input`, which is
  raylib's `core_input_keys` example.

  The original moves a MAROON circle of radius 50 by 2 pixels a frame along each
  arrow key held, from the middle of an 800x450 window, with a DARKGRAY caption
  at (10, 10). Here the ball follows a relative thumb-stick, as Vampire
  Survivors does: wherever a finger lands is the stick's centre, and while it
  stays down the vector from there to the finger is the direction. Inside
  `gesture/slop` of the centre there is no direction, so a tap or a trembling
  finger never moves the ball. Outside it the ball goes that way at the
  original's 2 pixels a frame, the vector normalised, so a diagonal is no
  faster. The original's digital keys add the two axes, which makes a diagonal
  2.83 pixels a frame, and that is the one deliberate difference. `stick-dir` is
  the rule as a pure function. A press under `gesture/back-region` belongs to
  the host and starts no stick, and a release never moves the ball.

  The original lets the ball leave the window. Here its centre is held a radius
  inside the play field, which is the full width below the caption, so the
  whole ball stays on the glass and can never cover the line. The original
  draws its ball over the text instead. The clamp runs every frame, so a rotation pulls the ball
  back in.

  This is frame-locked, like the original, which never reads a frame time. The
  800x450 game is scaled into the field by one factor `:u`, the geometric mean
  of the two axes' scales, which applies to the radius and the speed because the
  motion is free in two dimensions. The caption's size uses the smaller scale
  so it fits whichever way the phone is held. Colours are `[r g b a]` vectors.
  The draw method packs them with `rl/rgba`."
  (:require [raylib.gesture :as gesture]))

(def ball-speed "Pixels per frame at scale 1. The original's." 2.0)
(def ball-r "The ball's radius at scale 1. The original's." 50.0)

(def background-colour [245 245 245 255])
(def ball-colour [190 33 55 255])
(def caption-colour [80 80 80 255])
(def stick-colour [80 80 80 90])
(def knob-colour [80 80 80 140])

(def caption "drag to move the ball")

(defn dimensions
  "The layout for `metrics`' `:screen`. The play field is `:fx :ftop :fw :fh`:
  the full width, from a gap below the caption to the bottom, so the ball
  never covers the caption. `:u`
  scales the radius and the speed (see the namespace docstring). `:stick-r` is
  the ring drawn round the stick's centre and `:knob-r` its knob. `:caption` is
  `{:s :x :y :size}`, left-aligned below Back, so nothing is measured here."
  [metrics]
  (let [[w h] (:screen metrics)
        [_ back-y _ back-h] gesture/back-region
        back-bottom (+ back-y back-h)
        sx (/ w 800.0)
        ts (min sx (/ (- h back-bottom) 450.0))
        side (min w h)
        pad (max 8 (int (* 10 ts)))
        cap-size (max 20 (int (* 20 ts)))
        cap-y (+ back-bottom pad)
        top (+ cap-y cap-size pad)
        fh (- h top)
        sy (/ fh 450.0)
        u (Math/sqrt (* sx sy))]
    {:w w
     :h h
     :fx 0.0
     :ftop (double top)
     :fw (double w)
     :fh (double fh)
     :u u
     :ball-r (* ball-r u)
     :ball-speed (* ball-speed u)
     :stick-r (* 0.08 side)
     :knob-r (* 0.032 side)
     :caption {:s caption
               :x pad
               :y cap-y
               :size cap-size}}))

(defn- clamp [lo hi v] (max lo (min hi v)))

(defn clamp-ball
  "`[x y]` held so a ball of the field's radius lies wholly inside the play
  field of `dims`."
  [{:keys [fx ftop fw fh ball-r]} [x y]]
  [(clamp (+ fx ball-r) (- (+ fx fw) ball-r) x)
   (clamp (+ ftop ball-r) (- (+ ftop fh) ball-r) y)])

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
  "One frame. The ball moves by the stick, `:ball-speed` pixels along its
  direction, and is then held inside the play field, which also pulls it back in
  after a rotation. A rotation also drops the stick, whose centre is in the old
  screen's pixels, so a finger held through it moves nothing until it lands
  again. The release position is never read."
  [state input]
  (let [dims (dimensions (:metrics input))
        screen (:screen (:metrics input))
        turned? (not= screen (:screen state))
        state (if turned? (dissoc state :stick) state)
        [dx dy] (or (stick-dir state input (:metrics input)) [0.0 0.0])
        [x y] (:pos state)
        speed (:ball-speed dims)]
    (assoc state
           :pos (clamp-ball dims [(+ x (* dx speed)) (+ y (* dy speed))])
           :screen screen
           :stick (next-stick state input))))

(defn- init [{:keys [metrics]}]
  (let [{:keys [fx ftop fw fh]} (dimensions metrics)]
    [{:pos [(+ fx (* 0.5 fw)) (+ ftop (* 0.5 fh))]
      :screen (:screen metrics)
      :stick nil}
     [[:scene/init :nudge]]]))
(defn- update-scene [state input] [(advance state input) []])
(defn- draw [state _] [state []])
(defn- dispose [state] [state [[:scene/dispose :nudge]]])

(defn scene []
  {:id :nudge
   :title "Keyboard Ball"
   :init init
   :update update-scene
   :draw draw
   :dispose dispose})
